package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;

/**
 * Orchestrator integrating the Java multivariate anomaly detection engine,
 * telemetry alignment, baseline calibration, and event persistence.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Feature flag controlled: {@code spectra.anomaly-detection.multivariate.enabled} (default: false)</li>
 *   <li>Evaluates only complete, valid, fresh 4D vectors</li>
 *   <li>Skips detection if calibration model is absent, collecting, stale, or failed</li>
 *   <li>Non-contamination: excludes detected anomalies from baseline training buffers</li>
 *   <li>Complete isolation from existing univariate detection and alerting flows</li>
 * </ul>
 */
@Service
public class MultivariateAnomalyOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultivariateAnomalyOrchestrator.class);

    private final MultivariateAlignmentService alignmentService;
    private final MultivariateBaselineManager baselineManager;
    private final JavaMultivariateAnomalyDetector detector;
    private final MultivariateAnomalyEventPersister persister;
    private final boolean enabled;

    @Autowired
    public MultivariateAnomalyOrchestrator(
            MultivariateAlignmentService alignmentService,
            MultivariateBaselineManager baselineManager,
            JavaMultivariateAnomalyDetector detector,
            MultivariateAnomalyEventPersister persister,
            @Value("${spectra.anomaly-detection.multivariate.enabled:false}") boolean enabled) {
        this.alignmentService = Objects.requireNonNull(alignmentService, "alignmentService cannot be null");
        this.baselineManager = Objects.requireNonNull(baselineManager, "baselineManager cannot be null");
        this.detector = Objects.requireNonNull(detector, "detector cannot be null");
        this.persister = Objects.requireNonNull(persister, "persister cannot be null");
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Ingests a persisted scalar metric, attempts 4D alignment, evaluates multivariate detection
     * against a calibrated baseline model, and persists an anomaly event if detected.
     *
     * @param metric persisted service metric entity
     * @return persisted AnomalyEvent if an anomaly was detected and saved, or Optional.empty()
     */
    public Optional<AnomalyEvent> processMetric(ServiceMetric metric) {
        if (!enabled) {
            log.trace("Multivariate anomaly detection is disabled by configuration");
            return Optional.empty();
        }
        if (metric == null || metric.getService() == null || metric.getService().getId() == null) {
            return Optional.empty();
        }

        MultivariateMetricType metricType = MultivariateMetricType.fromMetricName(metric.getMetricName());
        if (metricType == null) {
            // Not one of the 4 canonical metrics (CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, ERROR_RATE)
            return Optional.empty();
        }

        Double val = metric.getMetricValue();
        if (val == null || Double.isNaN(val) || Double.isInfinite(val)) {
            log.warn("Invalid numeric telemetry value for service {}: {}", metric.getService().getId(), val);
            return Optional.empty();
        }

        // Validate physical bounds before alignment
        if (val < metricType.getMinPhysicalValue() || val > metricType.getMaxPhysicalValue()) {
            log.warn("Physical bound violation for {} ({} {}): outside [{}, {}]",
                metricType.getMetricName(), val, metricType.getUnit(),
                metricType.getMinPhysicalValue(), metricType.getMaxPhysicalValue());
            return Optional.empty();
        }

        Long serviceId = metric.getService().getId();
        Instant recordedAt = metric.getRecordedAt() != null ? metric.getRecordedAt() : Instant.now();
        ScalarMetricReading reading = new ScalarMetricReading(serviceId, metricType, val, recordedAt);

        return processReading(metric.getService(), reading);
    }

    /**
     * Internal processor accepting a validated ScalarMetricReading.
     */
    public Optional<AnomalyEvent> processReading(MonitoredService service, ScalarMetricReading reading) {
        if (!enabled || service == null || service.getId() == null || reading == null) {
            return Optional.empty();
        }

        Long serviceId = service.getId();

        try {
            // 1. Send scalar reading through telemetry alignment engine
            TelemetryAlignmentResult alignmentResult = alignmentService.ingestAndAlign(reading);

            // 2. Continue only when a complete, valid, fresh 4D vector is formed
            if (!alignmentResult.isAligned()) {
                log.debug("Telemetry alignment result for service {}: status={}, detail={}",
                    serviceId, alignmentResult.status(), alignmentResult.detailMessage());
                return Optional.empty();
            }

            MultivariateFeatureVector vector = alignmentResult.alignedVector();
            if (vector == null) {
                return Optional.empty();
            }

            // 3. Inspect baseline calibration readiness for this service
            CalibrationState state = baselineManager.getCalibrationState(serviceId);
            if (!state.isReady()) {
                // Circular dependency resolved: Service is in initial warm-up phase (UNINITIALIZED, COLLECTING, or FAILED).
                // Safely accumulate the complete, fresh 4D observation in the isolated warm-up buffer.
                log.info("Multivariate detection pending initial calibration for service {}: status={} ({})",
                    serviceId, state.status(), state.statusMessage());
                baselineManager.recordWarmupObservation(serviceId, vector);
                // Invariant: Do not perform detection or classify observations as normal before READY state
                return Optional.empty();
            }

            MultivariateCalibrationModel model = state.model();
            if (model == null) {
                return Optional.empty();
            }

            // 4. Service is READY: Invoke Java multivariate anomaly detector
            MultivariateDetectionResult detection = detector.detect(vector, model);

            // 5. If anomalous, do NOT update baseline; persist multivariate anomaly event through isolated persister
            if (detection.isAnomaly()) {
                log.warn("Multivariate anomaly detected for service {} at {}: D_M={:.2f} > threshold={:.2f}",
                    serviceId, vector.getRecordedAt(), detection.mahalanobisDistance(), detection.threshold());

                return persister.persistMultivariateAnomaly(service, vector, detection, model);
            }

            // 6. Confirmed normal observation: safely update confirmed baseline buffer
            baselineManager.recordConfirmedObservation(serviceId, vector);
            return Optional.empty();

        } catch (Exception ex) {
            log.error("Error during multivariate detection orchestration for service {}: {}",
                serviceId, ex.getMessage(), ex);
            return Optional.empty();
        }
    }
}
