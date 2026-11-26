package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

/**
 * Thread-safe Spring service managing per-service telemetry alignment buffers.
 * <p>
 * Routes incoming scalar metrics to the corresponding service's alignment buffer,
 * evaluates event-time freshness and watermark boundaries, and produces complete 4D feature vectors.
 */
@Service
public class MultivariateAlignmentService {

    private final ConcurrentMap<Long, ServiceTelemetryBuffer> serviceBuffers = new ConcurrentHashMap<>();

    /**
     * Ingests a validated scalar metric reading and attempts 4D vector alignment.
     *
     * @param reading scalar telemetry reading
     * @return alignment result
     */
    public TelemetryAlignmentResult ingestAndAlign(ScalarMetricReading reading) {
        Objects.requireNonNull(reading, "reading cannot be null");
        ServiceTelemetryBuffer buffer = serviceBuffers.computeIfAbsent(
            reading.serviceId(),
            ServiceTelemetryBuffer::new
        );
        return buffer.ingest(reading);
    }

    /**
     * Convenience method to ingest a metric by individual fields.
     */
    public TelemetryAlignmentResult ingestAndAlign(Long serviceId, String metricName, double value, Instant recordedAt) {
        ScalarMetricReading reading = ScalarMetricReading.of(serviceId, metricName, value, recordedAt);
        return ingestAndAlign(reading);
    }

    /**
     * Bootstraps or primes a service's alignment buffer with recent scalar telemetry metrics
     * (e.g. at startup or after recovery).
     *
     * @param serviceId service identifier
     * @param readings list of recent scalar telemetry readings
     * @return count of successfully ingested readings
     */
    public int bootstrapFromMetrics(Long serviceId, java.util.List<ScalarMetricReading> readings) {
        if (serviceId == null || readings == null || readings.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ScalarMetricReading reading : readings) {
            if (reading != null && serviceId.equals(reading.serviceId())) {
                ingestAndAlign(reading);
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the telemetry buffer for a service, if initialized.
     */
    public ServiceTelemetryBuffer getBuffer(Long serviceId) {
        return serviceBuffers.get(serviceId);
    }

    /**
     * Resets the buffer state for a service (e.g. upon service re-registration or test cleanup).
     */
    public void resetService(Long serviceId) {
        if (serviceId != null) {
            ServiceTelemetryBuffer buffer = serviceBuffers.get(serviceId);
            if (buffer != null) {
                buffer.reset();
            }
        }
    }

    /**
     * Clears all buffers (e.g. during application context shutdown or test setup).
     */
    public void resetAll() {
        serviceBuffers.clear();
    }

    /**
     * Returns the number of currently active service alignment buffers.
     */
    public int getActiveBufferCount() {
        return serviceBuffers.size();
    }
}
