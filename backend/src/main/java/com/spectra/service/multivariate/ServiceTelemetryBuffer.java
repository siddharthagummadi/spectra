package com.spectra.service.multivariate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe, per-service alignment buffer for asynchronous scalar telemetry metrics.
 * <p>
 * Enforces:
 * <ul>
 *   <li>30-second allowed-lateness event-time watermark (T_max - 30s)</li>
 *   <li>Per-feature operational freshness boundaries (180s for CPU/RT/ERR, 300s for MEM)</li>
 *   <li>Duplicate arrival suppression (identical timestamp and value)</li>
 *   <li>Zero-fill prohibition: never substitutes missing metrics with zero</li>
 *   <li>Evaluates only when at least one relevant metric has updated</li>
 * </ul>
 */
public class ServiceTelemetryBuffer {

    public static final Duration ALLOWED_WATERMARK_LATENESS = Duration.ofSeconds(30);

    private final Long serviceId;
    private ScalarMetricReading latestCpu;
    private ScalarMetricReading latestMemory;
    private ScalarMetricReading latestResponseTime;
    private ScalarMetricReading latestErrorRate;

    private Instant maxObservedEventTime;
    private Instant lastEvaluatedEventTime;

    public ServiceTelemetryBuffer(Long serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId cannot be null");
        }
        this.serviceId = serviceId;
    }

    /**
     * Ingests a single scalar metric reading and attempts to align a complete 4D feature vector.
     * Synchronized to guarantee atomic updates per service under concurrent ingestion.
     *
     * @param reading scalar metric observation
     * @return alignment result detailing whether a complete vector was produced or why it was deferred/dropped
     */
    public synchronized TelemetryAlignmentResult ingest(ScalarMetricReading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("reading cannot be null");
        }
        if (!serviceId.equals(reading.serviceId())) {
            throw new IllegalArgumentException(
                "Service mismatch: buffer is for service " + serviceId + ", reading is for " + reading.serviceId());
        }

        Instant eventTime = reading.recordedAt();

        // 1. Duplicate suppression check: identical timestamp and value for the same dimension
        ScalarMetricReading existing = getSlot(reading.metricType());
        if (existing != null && existing.recordedAt().equals(eventTime) && Double.compare(existing.value(), reading.value()) == 0) {
            return TelemetryAlignmentResult.duplicateSuppressed(
                serviceId,
                String.format("Duplicate reading suppressed for %s at %s with value %.2f",
                    reading.metricType().getMetricName(), eventTime, reading.value())
            );
        }

        // 2. Compute watermark based on currently observed max event time (T_max - 30s)
        Instant currentWatermark = getWatermark();

        // 3. Late-arrival check for already-finalized windows:
        // A window at T_window is finalized when the watermark has advanced past it (T_window <= watermark)
        // and it has already been evaluated (T_window <= lastEvaluatedEventTime).
        if (currentWatermark != null && lastEvaluatedEventTime != null) {
            if (eventTime.isBefore(currentWatermark) && !eventTime.isAfter(lastEvaluatedEventTime)) {
                Instant candidateTarget = candidateAlignmentTime(reading);
                if (!candidateTarget.isAfter(lastEvaluatedEventTime)) {
                    return TelemetryAlignmentResult.lateDropped(
                        serviceId,
                        String.format("Late telemetry for %s at %s dropped: older than finalized window %s (watermark: %s)",
                            reading.metricType().getMetricName(), eventTime, lastEvaluatedEventTime, currentWatermark)
                    );
                }
            }
        }

        // 4. Out-of-order check: if existing reading for this slot is strictly newer, do not overwrite
        if (existing != null && eventTime.isBefore(existing.recordedAt())) {
            if (currentWatermark != null && eventTime.isBefore(currentWatermark)) {
                return TelemetryAlignmentResult.lateDropped(
                    serviceId,
                    String.format("Late telemetry for %s at %s dropped: older than slot (%s) and watermark (%s)",
                        reading.metricType().getMetricName(), eventTime, existing.recordedAt(), currentWatermark)
                );
            }
            return TelemetryAlignmentResult.noUpdate(
                serviceId,
                String.format("Older out-of-order reading for %s (%s < %s) retained existing newer reading",
                    reading.metricType().getMetricName(), eventTime, existing.recordedAt())
            );
        }

        // 5. Update maximum observed event time if this reading is newer
        Instant newMax = (maxObservedEventTime == null || eventTime.isAfter(maxObservedEventTime))
            ? eventTime : maxObservedEventTime;

        // 6. Check freshness of this incoming reading relative to candidate reference time newMax
        Duration incomingAge = Duration.between(eventTime, newMax);
        if (incomingAge.compareTo(reading.metricType().getFreshnessLimit()) > 0) {
            return TelemetryAlignmentResult.staleDropped(
                serviceId,
                String.format("Reading for %s is stale: age %ds exceeds live freshness limit of %ds (reference time: %s)",
                    reading.metricType().getMetricName(), incomingAge.toSeconds(),
                    reading.metricType().getFreshnessLimit().toSeconds(), newMax)
            );
        }

        // Accept reading into dimension slot
        setSlot(reading);
        maxObservedEventTime = newMax;

        // 7. Check for missing dimensions (Zero-fill strictly prohibited)
        List<String> missing = getMissingDimensions();
        if (!missing.isEmpty()) {
            return TelemetryAlignmentResult.incomplete(
                serviceId,
                "Incomplete feature vector: waiting for dimensions " + missing + ". Zero-fill is strictly prohibited."
            );
        }

        // 8. Calculate candidate alignment time T = max(t_cpu, t_mem, t_rt, t_err)
        Instant alignmentTime = calculateAlignmentTime();

        // 9. Watermark finalization check on the alignment window:
        // An alignment window is finalized if its timestamp is <= lastEvaluatedEventTime and <= watermark
        Instant watermark = getWatermark();
        if (lastEvaluatedEventTime != null && watermark != null) {
            if (!alignmentTime.isAfter(lastEvaluatedEventTime) && !alignmentTime.isAfter(watermark)) {
                return TelemetryAlignmentResult.lateDropped(
                    serviceId,
                    String.format("Alignment window at %s dropped: already finalized at %s (watermark: %s)",
                        alignmentTime, lastEvaluatedEventTime, watermark)
                );
            }
        }

        // 10. Check freshness for each dimension relative to alignment time T
        for (MultivariateMetricType type : MultivariateMetricType.values()) {
            ScalarMetricReading slot = getSlot(type);
            Duration age = Duration.between(slot.recordedAt(), alignmentTime);
            if (age.isNegative()) {
                age = Duration.ZERO; // allow minute sub-millisecond clock jitter
            }
            if (age.compareTo(type.getFreshnessLimit()) > 0) {
                // Dimension is stale
                return TelemetryAlignmentResult.staleDropped(
                    serviceId,
                    String.format("Dimension %s is stale: age %ds exceeds live freshness limit of %ds (alignment time: %s)",
                        type.getMetricName(), age.toSeconds(), type.getFreshnessLimit().toSeconds(), alignmentTime)
                );
            }
        }

        // 11. Ensure at least one relevant metric has updated since last evaluation
        if (lastEvaluatedEventTime != null && !alignmentTime.isAfter(lastEvaluatedEventTime)) {
            return TelemetryAlignmentResult.noUpdate(
                serviceId,
                "No metric update advanced the alignment timestamp beyond " + lastEvaluatedEventTime
            );
        }

        // 12. Assemble validated 4D vector
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(
            latestCpu.value(),
            latestMemory.value(),
            latestResponseTime.value(),
            latestErrorRate.value(),
            alignmentTime
        );

        lastEvaluatedEventTime = alignmentTime;
        return TelemetryAlignmentResult.aligned(serviceId, vector);
    }

    private ScalarMetricReading getSlot(MultivariateMetricType type) {
        return switch (type) {
            case CPU_USAGE -> latestCpu;
            case MEMORY_USAGE -> latestMemory;
            case RESPONSE_TIME -> latestResponseTime;
            case ERROR_RATE -> latestErrorRate;
        };
    }

    private void setSlot(ScalarMetricReading reading) {
        switch (reading.metricType()) {
            case CPU_USAGE -> latestCpu = reading;
            case MEMORY_USAGE -> latestMemory = reading;
            case RESPONSE_TIME -> latestResponseTime = reading;
            case ERROR_RATE -> latestErrorRate = reading;
        }
    }

    private List<String> getMissingDimensions() {
        List<String> missing = new ArrayList<>();
        if (latestCpu == null) missing.add("CPU_USAGE");
        if (latestMemory == null) missing.add("MEMORY_USAGE");
        if (latestResponseTime == null) missing.add("RESPONSE_TIME");
        if (latestErrorRate == null) missing.add("ERROR_RATE");
        return missing;
    }

    private Instant calculateAlignmentTime() {
        Instant t = latestCpu.recordedAt();
        if (latestMemory.recordedAt().isAfter(t)) t = latestMemory.recordedAt();
        if (latestResponseTime.recordedAt().isAfter(t)) t = latestResponseTime.recordedAt();
        if (latestErrorRate.recordedAt().isAfter(t)) t = latestErrorRate.recordedAt();
        return t;
    }

    private Instant candidateAlignmentTime(ScalarMetricReading reading) {
        Instant t = reading.recordedAt();
        for (MultivariateMetricType type : MultivariateMetricType.values()) {
            if (type != reading.metricType()) {
                ScalarMetricReading slot = getSlot(type);
                if (slot != null && slot.recordedAt().isAfter(t)) {
                    t = slot.recordedAt();
                }
            }
        }
        return t;
    }

    public synchronized Long getServiceId() {
        return serviceId;
    }

    public synchronized Instant getMaxObservedEventTime() {
        return maxObservedEventTime;
    }

    public synchronized Instant getWatermark() {
        return maxObservedEventTime != null
            ? maxObservedEventTime.minus(ALLOWED_WATERMARK_LATENESS)
            : null;
    }

    public synchronized Instant getLastEvaluatedEventTime() {
        return lastEvaluatedEventTime;
    }

    public synchronized void reset() {
        latestCpu = null;
        latestMemory = null;
        latestResponseTime = null;
        latestErrorRate = null;
        maxObservedEventTime = null;
        lastEvaluatedEventTime = null;
    }
}
