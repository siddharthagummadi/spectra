package com.spectra.service.multivariate;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.repository.AnomalyEventRepository;

/**
 * Transaction-isolated persister bean for multivariate anomaly events.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Isolated transaction boundary with {@code Propagation.REQUIRES_NEW}</li>
 *   <li>Deterministic deduplication based on (service_id, detector_type, detected_at)</li>
 *   <li>Concurrent duplicate insert suppression without triggering {@code UnexpectedRollbackException}</li>
 *   <li>Strict rethrow of unrelated integrity violations (foreign keys, NOT NULL, length)</li>
 *   <li>Clear separation of statistical feature attribution from causal root-cause analysis</li>
 * </ul>
 */
@Service
public class MultivariateAnomalyEventPersister {

    private static final Logger log = LoggerFactory.getLogger(MultivariateAnomalyEventPersister.class);

    public static final String DETECTOR_TYPE = "MULTIVARIATE_MAHALANOBIS";
    public static final String METRIC_UNIT = "D_M";
    public static final String ANOMALY_DIRECTION = "POSITIVE_SPIKE";
    public static final String INITIAL_STATUS = "DETECTED";

    // Theoretical distribution of D_M for p=4 multivariate normal:
    // D_M = sqrt(z^T * Sigma^-1 * z) follows a Chi distribution with 4 degrees of freedom:
    // E[D_M] = sqrt(2) * Gamma(2.5) / Gamma(2) = 0.75 * sqrt(2*pi) = 1.8800 (exact: 1.8799712...)
    public static final double BASELINE_MEAN_DM = 1.8800;
    // Var(D_M) = E[D_M^2] - (E[D_M])^2 = 4 - 9*pi/8 = 0.465708... -> StdDev = sqrt(0.465708) = 0.6824
    public static final double BASELINE_STD_DEV_DM = 0.6824;

    public static final String MULTIVARIATE_DEDUP_CONSTRAINT = "uk_anomaly_events_multivariate_dedup";
    public static final String DEDUP_KEY_CONSTRAINT = "uk_anomaly_events_dedup_key";

    private final AnomalyEventRepository anomalyEventRepository;
    private final ConcurrentMap<String, Object> inFlightKeys = new ConcurrentHashMap<>();

    @Autowired
    public MultivariateAnomalyEventPersister(AnomalyEventRepository anomalyEventRepository) {
        this.anomalyEventRepository = Objects.requireNonNull(anomalyEventRepository, "anomalyEventRepository cannot be null");
    }

    /**
     * Persists a multivariate anomaly event within an isolated {@code REQUIRES_NEW} transaction.
     *
     * @param service monitored service
     * @param vector validated 4D observation vector
     * @param detection multivariate detection result
     * @param model calibrated model used for detection
     * @return persisted AnomalyEvent, or Optional.empty() if suppressed as an idempotent duplicate
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnomalyEvent> persistMultivariateAnomaly(
            MonitoredService service,
            MultivariateFeatureVector vector,
            MultivariateDetectionResult detection,
            MultivariateCalibrationModel model) {

        if (service == null || service.getId() == null) {
            throw new IllegalArgumentException("Service and service ID cannot be null");
        }
        if (vector == null) {
            throw new IllegalArgumentException("Vector cannot be null");
        }
        if (detection == null) {
            throw new IllegalArgumentException("Detection result cannot be null");
        }
        if (model == null) {
            throw new IllegalArgumentException("Calibration model cannot be null");
        }

        Long serviceId = service.getId();
        Instant detectedAt = vector.getRecordedAt();
        String dedupKey = buildDeduplicationKey(serviceId, DETECTOR_TYPE, detectedAt);

        // 1. In-flight synchronization: synchronize on the deduplication key to coordinate concurrent threads
        Object lock = inFlightKeys.computeIfAbsent(dedupKey, k -> new Object());
        synchronized (lock) {
            try {
                // 2. Fast repository pre-check: avoid insert if already persisted
                if (anomalyEventRepository.existsByServiceIdAndMetricNameAndDetectedAt(serviceId, DETECTOR_TYPE, detectedAt)) {
                    log.debug("Multivariate anomaly already persisted for dedup key: {}", dedupKey);
                    return Optional.empty();
                }

                // 3. Assemble AnomalyEvent entity with multivariate parameters
                AnomalyEvent event = new AnomalyEvent();
                event.setService(service);
                event.setMetric(null); // Explicitly null: 4D multivariate event does not map to a single scalar metric
                event.setMetricName(DETECTOR_TYPE);
                event.setUnit(METRIC_UNIT);
                event.setObservedValue(detection.mahalanobisDistance());
                // Assign mathematically sound baseline mean and std dev of D_M under the 4D Chi distribution
                event.setBaselineMean(BASELINE_MEAN_DM);
                event.setBaselineStdDev(BASELINE_STD_DEV_DM);
                // Standardized distance score: s = (D_M - E[D_M]) / sigma(D_M).
                // Note: This is a standardized descriptive distance score under the Chi(4) null distribution,
                // NOT a Gaussian standard normal z-score, since D_M has positive support [0, inf) and skewness.
                event.setAnomalyScore((detection.mahalanobisDistance() - BASELINE_MEAN_DM) / BASELINE_STD_DEV_DM);
                event.setSeverity(detection.severity() != null ? detection.severity() : "MEDIUM");
                event.setDirection(ANOMALY_DIRECTION);
                event.setStatus(INITIAL_STATUS);
                event.setDetectedAt(detectedAt);
                event.setMessage(buildAttributionMessage(vector, detection, model));

                // 4. Flush immediately inside the transaction to catch constraint violations
                AnomalyEvent saved = anomalyEventRepository.saveAndFlush(event);
                log.info("Persisted multivariate anomaly event ID {} for service {} (D_M={:.2f}, threshold={:.2f})",
                    saved.getId(), serviceId, detection.mahalanobisDistance(), detection.threshold());
                return Optional.of(saved);

            } catch (DataIntegrityViolationException ex) {
                // 5. Inspect constraint failure: suppress if exact multivariate duplicate; rethrow otherwise
                if (isMultivariateDuplicateViolation(ex, serviceId, detectedAt)) {
                    log.warn("Concurrent duplicate multivariate anomaly suppressed for service {} at {} ({})",
                        serviceId, detectedAt, ex.getMessage());
                    return Optional.empty();
                }
                log.error("Integrity violation during multivariate anomaly persistence for service {}: {}",
                    serviceId, ex.getMessage());
                throw ex;
            } finally {
                inFlightKeys.remove(dedupKey);
            }
        }
    }

    /**
     * Builds a deterministic deduplication key for multivariate events.
     */
    public static String buildDeduplicationKey(Long serviceId, String detectorType, Instant detectedAt) {
        return String.format("MVT:%d:%s:%s", serviceId, detectorType, detectedAt);
    }

    /**
     * Determines whether a DataIntegrityViolationException is caused by a duplicate multivariate event.
     * Traverses the full exception cause chain to inspect SQLState and constraint names without querying
     * the Hibernate session (which is broken after a flush failure).
     */
    public boolean isMultivariateDuplicateViolation(DataIntegrityViolationException ex, Long serviceId, Instant detectedAt) {
        if (ex == null) {
            return false;
        }

        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                // 23505 = unique_violation in PostgreSQL and H2
                if ("23505".equals(sqlState)) {
                    String sqlMsg = sqlEx.getMessage();
                    if (sqlMsg != null) {
                        String lowerSql = sqlMsg.toLowerCase();
                        if (lowerSql.contains(MULTIVARIATE_DEDUP_CONSTRAINT) ||
                            lowerSql.contains(DEDUP_KEY_CONSTRAINT)) {
                            return true;
                        }
                    }
                }
            }

            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains(MULTIVARIATE_DEDUP_CONSTRAINT) || lower.contains(DEDUP_KEY_CONSTRAINT)) {
                    return true;
                }
            }

            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Formats feature attribution driver shares into a clear, non-causal summary message.
     */
    public static String buildAttributionMessage(
            MultivariateFeatureVector vector,
            MultivariateDetectionResult detection,
            MultivariateCalibrationModel model) {

        List<FeatureContribution> drivers = detection.featureContributions();
        StringBuilder driverSummary = new StringBuilder();
        if (drivers != null && !drivers.isEmpty()) {
            for (int i = 0; i < Math.min(drivers.size(), 3); i++) {
                FeatureContribution fc = drivers.get(i);
                if (i > 0) driverSummary.append(", ");
                driverSummary.append(String.format("%s (%.1f%%)", fc.metricType().getMetricName(), fc.driverSharePercent()));
            }
        } else {
            driverSummary.append("none");
        }

        String msg = String.format(
            "[MULTIVARIATE_MAHALANOBIS] D_M=%.2f > threshold=%.2f. Drivers: %s (non-causal correlation).",
            detection.mahalanobisDistance(),
            detection.threshold(),
            driverSummary
        );

        // Enforce maximum column length of 255 characters
        if (msg.length() > 255) {
            return msg.substring(0, 252) + "...";
        }
        return msg;
    }
}
