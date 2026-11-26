package com.spectra.service.multivariate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service managing baseline observation buffers and the calibration lifecycle
 * for multivariate anomaly detection.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Exclusion of detected anomalies from baseline training buffers</li>
 *   <li>Requirement of at least 10 valid observations before calibration</li>
 *   <li>Bounded in-memory observation ring-buffers (max 300 observations per service)</li>
 *   <li>Explicit calibration readiness, failure, and staleness state management</li>
 *   <li>Safe recovery and bootstrap capability from historical telemetry</li>
 * </ul>
 */
@Service
public class MultivariateBaselineManager {

    private static final Logger log = LoggerFactory.getLogger(MultivariateBaselineManager.class);

    public static final int DEFAULT_MAX_BUFFER_SIZE = 300;
    public static final Duration DEFAULT_MAX_MODEL_AGE = Duration.ofHours(24);
    public static final int RECALIBRATION_THRESHOLD_NEW_SAMPLES = 30;
    public static final int MAX_WARMUP_BUFFER_SIZE = 50;
    public static final int DEFAULT_STRIPE_COUNT = 128;

    public record WarmupScreeningResult(
        List<MultivariateFeatureVector> cleanObservations,
        List<MultivariateFeatureVector> outliers
    ) {}

    private final JavaMultivariateAnomalyDetector detector;
    private final int maxBufferSize;
    private final Duration maxModelAge;
    private final Object[] stripeLocks;

    private final ConcurrentMap<Long, List<MultivariateFeatureVector>> serviceBuffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<MultivariateFeatureVector>> serviceWarmupBuffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, java.util.Set<Instant>> warmupTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, java.util.Set<Instant>> confirmedTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CalibrationState> serviceStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Integer> samplesAtLastCalibration = new ConcurrentHashMap<>();

    @Autowired
    public MultivariateBaselineManager(JavaMultivariateAnomalyDetector detector) {
        this(detector, DEFAULT_MAX_BUFFER_SIZE, DEFAULT_MAX_MODEL_AGE, DEFAULT_STRIPE_COUNT);
    }

    public MultivariateBaselineManager(JavaMultivariateAnomalyDetector detector, int maxBufferSize, Duration maxModelAge) {
        this(detector, maxBufferSize, maxModelAge, DEFAULT_STRIPE_COUNT);
    }

    public MultivariateBaselineManager(JavaMultivariateAnomalyDetector detector, int maxBufferSize, Duration maxModelAge, int stripeCount) {
        this.detector = Objects.requireNonNull(detector, "detector cannot be null");
        this.maxBufferSize = Math.max(10, maxBufferSize);
        this.maxModelAge = maxModelAge != null ? maxModelAge : DEFAULT_MAX_MODEL_AGE;
        int count = stripeCount > 0 ? stripeCount : DEFAULT_STRIPE_COUNT;
        this.stripeLocks = new Object[count];
        for (int i = 0; i < count; i++) {
            this.stripeLocks[i] = new Object();
        }
    }

    /**
     * Resolves the fixed stripe lock for the given service ID.
     * Uses Math.floorMod to guarantee non-negative indexing across all hash codes.
     * Stripe locks are fixed at initialization and never removed or recreated.
     *
     * @param serviceId service identifier
     * @return initialized stripe lock object
     */
    Object getLock(Long serviceId) {
        if (serviceId == null) {
            return stripeLocks[0];
        }
        int index = Math.floorMod(serviceId.hashCode(), stripeLocks.length);
        return stripeLocks[index];
    }

    int getStripeCount() {
        return stripeLocks.length;
    }

    /**
     * Records an observation during the initial baseline warm-up phase (before READY state).
     * Keeps warm-up observations strictly separate from confirmed normal observations.
     * Deduplicates identical logical observations by timestamp.
     *
     * @param serviceId service identifier
     * @param vector validated 4D observation vector
     * @return updated CalibrationState
     */
    public CalibrationState recordWarmupObservation(Long serviceId, MultivariateFeatureVector vector) {
        if (serviceId == null || vector == null) {
            return CalibrationState.uninitialized(serviceId);
        }

        // 1. Eligibility check: finite, physically valid, and fresh
        if (!isValidBaselineObservation(vector)) {
            log.warn("Excluding invalid, stale, or out-of-bounds observation from warm-up buffer for service {}: {}",
                serviceId, vector);
            return getCalibrationState(serviceId);
        }

        synchronized (getLock(serviceId)) {
            // 2. State check: if already READY, do not accumulate in warm-up
            CalibrationState currentState = serviceStates.get(serviceId);
            if (currentState != null && currentState.isReady()) {
                log.debug("Service {} is already READY; skipping warm-up accumulation", serviceId);
                return currentState;
            }

            // 3. Deduplication check: do not count repeated logical observations twice
            java.util.Set<Instant> seen = warmupTimestamps.computeIfAbsent(
                serviceId, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())
            );
            if (!seen.add(vector.getRecordedAt())) {
                log.debug("Duplicate warm-up observation at {} for service {} ignored", vector.getRecordedAt(), serviceId);
                return getCalibrationState(serviceId);
            }

            // 4. Staging in dedicated warm-up buffer with bounded capacity
            List<MultivariateFeatureVector> warmupList = serviceWarmupBuffers.computeIfAbsent(
                serviceId, k -> Collections.synchronizedList(new ArrayList<>())
            );
            synchronized (warmupList) {
                if (warmupList.size() >= MAX_WARMUP_BUFFER_SIZE) {
                    MultivariateFeatureVector evicted = warmupList.remove(0);
                    if (evicted != null && evicted.getRecordedAt() != null) {
                        seen.remove(evicted.getRecordedAt());
                    }
                }
                warmupList.add(vector);
            }
            int count = warmupList.size();

            // 5. Check sample threshold (minimum 10 required)
            if (count < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
                CalibrationState collectingState = CalibrationState.collecting(serviceId, count);
                serviceStates.put(serviceId, collectingState);
                log.info("Service {} warm-up observation recorded ({}/{} required)",
                    serviceId, count, LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS);
                return collectingState;
            }

            // 6. Minimum sample threshold reached: attempt initial calibration with outlier screening
            return calibrateInitialWarmup(serviceId);
        }
    }

    /**
     * Executes initial calibration from accumulated warm-up samples.
     * Screens candidate samples for outliers using robust statistics (Median/MAD and multivariate distance)
     * before transferring to confirmed normal baseline buffers.
     * Transitions to READY only upon successful numerical fitting on clean observations.
     */
    public CalibrationState calibrateInitialWarmup(Long serviceId) {
        if (serviceId == null) {
            return CalibrationState.uninitialized(null);
        }

        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> warmupList = serviceWarmupBuffers.get(serviceId);
            int count = (warmupList != null) ? warmupList.size() : 0;

            if (count < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
                CalibrationState collectingState = CalibrationState.collecting(serviceId, count);
                serviceStates.put(serviceId, collectingState);
                return collectingState;
            }

            List<MultivariateFeatureVector> warmupSnapshot;
            synchronized (warmupList) {
                warmupSnapshot = new ArrayList<>(warmupList);
            }

            // 1. Robust outlier screening: prevent contaminated or abnormal telemetry from becoming baseline samples
            WarmupScreeningResult screening = screenWarmupObservations(serviceId, warmupSnapshot);
            if (!screening.outliers().isEmpty()) {
                log.warn("Service {}: Screened and purged {} outlier observation(s) from initial warm-up buffer",
                    serviceId, screening.outliers().size());

                java.util.Set<Instant> seen = warmupTimestamps.get(serviceId);
                synchronized (warmupList) {
                    warmupList.removeAll(screening.outliers());
                    if (seen != null) {
                        for (MultivariateFeatureVector outlier : screening.outliers()) {
                            if (outlier.getRecordedAt() != null) {
                                seen.remove(outlier.getRecordedAt());
                            }
                        }
                    }
                }

                int cleanCount = warmupList.size();
                if (cleanCount < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
                    CalibrationState collectingState = CalibrationState.collecting(serviceId, cleanCount);
                    serviceStates.put(serviceId, collectingState);
                    log.info("Service {}: Retaining {} clean warm-up observation(s) after outlier purge; awaiting clean samples to reach {}",
                        serviceId, cleanCount, LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS);
                    return collectingState;
                }

                warmupSnapshot = new ArrayList<>(screening.cleanObservations());
                count = warmupSnapshot.size();
            }

            CalibrationState previousState = serviceStates.get(serviceId);
            MultivariateCalibrationModel previousModel = (previousState != null) ? previousState.model() : null;

            try {
                MultivariateCalibrationModel model = detector.calibrate(serviceId, warmupSnapshot);

                // Transfer ONLY validated, clean samples to confirmed buffer
                List<MultivariateFeatureVector> confirmedBuffer = serviceBuffers.computeIfAbsent(
                    serviceId, k -> Collections.synchronizedList(new ArrayList<>())
                );
                synchronized (confirmedBuffer) {
                    confirmedBuffer.clear();
                    confirmedBuffer.addAll(warmupSnapshot);
                }

                // Transfer timestamps to confirmed set
                java.util.Set<Instant> confirmedSet = confirmedTimestamps.computeIfAbsent(
                    serviceId, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())
                );
                java.util.Set<Instant> warmSet = warmupTimestamps.get(serviceId);
                if (warmSet != null) {
                    confirmedSet.addAll(warmSet);
                }

                // Clear warm-up staging collections
                serviceWarmupBuffers.remove(serviceId);
                warmupTimestamps.remove(serviceId);

                CalibrationState readyState = CalibrationState.ready(serviceId, model, count);
                serviceStates.put(serviceId, readyState);
                samplesAtLastCalibration.put(serviceId, count);
                log.info("Initial baseline calibration succeeded for service {}: READY with {} observations (threshold={:.2f})",
                    serviceId, count, model.getAnomalyThreshold());
                return readyState;

            } catch (Exception ex) {
                log.error("Initial baseline calibration failed for service {}: {}", serviceId, ex.getMessage(), ex);
                CalibrationState failedState = CalibrationState.failed(serviceId, previousModel, count, ex.getMessage());
                serviceStates.put(serviceId, failedState);
                return failedState;
            }
        }
    }

    /**
     * Robust outlier screening for initial warm-up observations.
     * Uses two-stage screening:
     * 1. Coordinate-wise robust median & MAD (modified Z-score > 3.5 cutoff)
     * 2. Candidate multivariate Mahalanobis distance check against candidate model
     *
     * @param serviceId service identifier
     * @param candidates candidate warm-up observations (minimum 10 required)
     * @return WarmupScreeningResult partitioning clean observations from outliers
     */
    public WarmupScreeningResult screenWarmupObservations(Long serviceId, List<MultivariateFeatureVector> candidates) {
        if (candidates == null || candidates.size() < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
            return new WarmupScreeningResult(candidates != null ? candidates : List.of(), List.of());
        }

        int n = candidates.size();
        int p = MultivariateMetricType.DIMENSION;
        List<MultivariateFeatureVector> cleanList = new ArrayList<>(candidates);
        List<MultivariateFeatureVector> outliers = new ArrayList<>();

        // Coordinate-wise robust screening using Median and Median Absolute Deviation (MAD)
        // Modified Z-score: M_j = |x_j - median_j| / max(1.4826 * MAD_j, sigmaFloor_j)
        // Observations with M_j > 3.5 on any dimension are rejected as marginal outliers.
        MultivariateMetricType[] types = MultivariateMetricType.values();
        double[] medians = new double[p];
        double[] scales = new double[p];

        for (int j = 0; j < p; j++) {
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) {
                vals[i] = candidates.get(i).toArray()[j];
            }
            java.util.Arrays.sort(vals);
            double med = (n % 2 == 1) ? vals[n / 2] : 0.5 * (vals[(n / 2) - 1] + vals[n / 2]);
            medians[j] = med;

            double[] dev = new double[n];
            for (int i = 0; i < n; i++) {
                dev[i] = Math.abs(vals[i] - med);
            }
            java.util.Arrays.sort(dev);
            double mad = (n % 2 == 1) ? dev[n / 2] : 0.5 * (dev[(n / 2) - 1] + dev[n / 2]);
            double normalConsistentScale = 1.4826 * mad;
            scales[j] = Math.max(normalConsistentScale, types[j].getSigmaFloor());
        }

        java.util.Iterator<MultivariateFeatureVector> it = cleanList.iterator();
        while (it.hasNext()) {
            MultivariateFeatureVector vec = it.next();
            double[] arr = vec.toArray();
            boolean isOutlier = false;
            for (int j = 0; j < p; j++) {
                double modifiedZ = Math.abs(arr[j] - medians[j]) / scales[j];
                if (modifiedZ > 3.5) {
                    isOutlier = true;
                    log.warn("Warm-up candidate at {} for service {} flagged as marginal outlier on {}: val={}, median={}, scale={}, modifiedZ={:.2f}",
                        vec.getRecordedAt(), serviceId, types[j].getMetricName(), arr[j], medians[j], scales[j], modifiedZ);
                    break;
                }
            }
            if (isOutlier) {
                outliers.add(vec);
                it.remove();
            }
        }

        return new WarmupScreeningResult(cleanList, outliers);
    }

    /**
     * Records a confirmed normal observation for an already calibrated (READY) service.
     * Detected anomalies are strictly excluded by the orchestrator before calling this method.
     */
    public void recordConfirmedObservation(Long serviceId, MultivariateFeatureVector vector) {
        if (serviceId == null || vector == null) {
            return;
        }

        if (!isValidBaselineObservation(vector)) {
            log.warn("Excluding invalid or stale observation from confirmed baseline for service {}: {}",
                serviceId, vector);
            return;
        }

        synchronized (getLock(serviceId)) {
            java.util.Set<Instant> seen = confirmedTimestamps.computeIfAbsent(
                serviceId, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())
            );
            if (!seen.add(vector.getRecordedAt())) {
                log.debug("Duplicate confirmed observation at {} for service {} ignored", vector.getRecordedAt(), serviceId);
                return;
            }

            List<MultivariateFeatureVector> buffer = serviceBuffers.computeIfAbsent(
                serviceId, k -> Collections.synchronizedList(new ArrayList<>())
            );

            synchronized (buffer) {
                if (buffer.size() >= maxBufferSize) {
                    MultivariateFeatureVector evicted = buffer.remove(0);
                    if (evicted != null && evicted.getRecordedAt() != null) {
                        seen.remove(evicted.getRecordedAt());
                    }
                }
                buffer.add(vector);
            }

            if (shouldRecalibrate(serviceId)) {
                calibrate(serviceId);
            }
        }
    }

    /**
     * General observation recorder maintaining backward compatibility.
     * Routes uncalibrated services to the warm-up pipeline, and calibrated services
     * to the confirmed pipeline. Anomalies are strictly rejected.
     */
    public void recordObservation(Long serviceId, MultivariateFeatureVector vector, boolean isAnomaly) {
        if (serviceId == null || vector == null) {
            return;
        }

        // Strict invariant: Anomalous observations must NEVER contaminate the normal baseline
        if (isAnomaly) {
            log.debug("Excluding anomalous observation from baseline buffer for service {}", serviceId);
            return;
        }

        synchronized (getLock(serviceId)) {
            CalibrationState state = getCalibrationState(serviceId);
            if (!state.isReady()) {
                recordWarmupObservation(serviceId, vector);
            } else {
                recordConfirmedObservation(serviceId, vector);
            }
        }
    }

    /**
     * Fits or refreshes the baseline calibration model for a service.
     *
     * @param serviceId service identifier
     * @return updated CalibrationState
     */
    public CalibrationState calibrate(Long serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException("serviceId cannot be null");
        }

        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> buffer = serviceBuffers.get(serviceId);
            int count = (buffer != null) ? buffer.size() : 0;

            if (count < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
                CalibrationState state = CalibrationState.collecting(serviceId, count);
                serviceStates.put(serviceId, state);
                return state;
            }

            List<MultivariateFeatureVector> trainingData;
            synchronized (buffer) {
                trainingData = new ArrayList<>(buffer);
            }

            CalibrationState previousState = serviceStates.get(serviceId);
            MultivariateCalibrationModel previousModel = (previousState != null) ? previousState.model() : null;

            try {
                MultivariateCalibrationModel model = detector.calibrate(serviceId, trainingData);
                CalibrationState readyState = CalibrationState.ready(serviceId, model, count);
                serviceStates.put(serviceId, readyState);
                samplesAtLastCalibration.put(serviceId, count);
                log.info("Baseline calibration succeeded for service {}: count={}, threshold={}",
                    serviceId, count, model.getAnomalyThreshold());
                return readyState;
            } catch (Exception ex) {
                log.error("Baseline calibration failed for service {}: {}", serviceId, ex.getMessage(), ex);
                // Invariant: Failed recalibration preserves the last valid calibration model
                CalibrationState failedState = CalibrationState.failed(serviceId, previousModel, count, ex.getMessage());
                serviceStates.put(serviceId, failedState);
                return failedState;
            }
        }
    }

    /**
     * Checks if recalibration is warranted based on model age or accumulated new samples.
     */
    public boolean shouldRecalibrate(Long serviceId) {
        if (serviceId == null) {
            return false;
        }

        synchronized (getLock(serviceId)) {
            CalibrationState state = serviceStates.get(serviceId);
            if (state == null || !state.isReady()) {
                return false;
            }

            // Recalibrate if model is older than maximum configured age
            Instant lastCal = state.lastCalibratedAt();
            if (lastCal != null && Instant.now().isAfter(lastCal.plus(maxModelAge))) {
                return true;
            }

            // Recalibrate if sufficient new normal samples have been collected
            List<MultivariateFeatureVector> buffer = serviceBuffers.get(serviceId);
            int currentCount = (buffer != null) ? buffer.size() : 0;
            int lastCount = samplesAtLastCalibration.getOrDefault(serviceId, 0);

            return (currentCount - lastCount) >= RECALIBRATION_THRESHOLD_NEW_SAMPLES;
        }
    }

    /**
     * Returns the active calibrated model if available.
     * Guarantees stale or failed models cannot be used silently.
     */
    public Optional<MultivariateCalibrationModel> getCalibratedModel(Long serviceId) {
        CalibrationState state = getCalibrationState(serviceId);
        if (!state.isReady()) {
            return Optional.empty();
        }
        return state.getModel();
    }

    /**
     * Retrieves the current calibration state, updating to STALE if expired.
     */
    public CalibrationState getCalibrationState(Long serviceId) {
        if (serviceId == null) {
            return CalibrationState.uninitialized(null);
        }

        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> buffer = serviceBuffers.get(serviceId);
            int currentCount = (buffer != null) ? buffer.size() : 0;

            CalibrationState state = serviceStates.get(serviceId);
            if (state == null) {
                List<MultivariateFeatureVector> warmup = serviceWarmupBuffers.get(serviceId);
                int warmupCount = (warmup != null) ? warmup.size() : 0;
                if (warmupCount > 0) {
                    CalibrationState collectingState = CalibrationState.collecting(serviceId, warmupCount);
                    serviceStates.put(serviceId, collectingState);
                    return collectingState;
                }
                return currentCount > 0 ? CalibrationState.collecting(serviceId, currentCount) : CalibrationState.uninitialized(serviceId);
            }

            if (state.isReady()) {
                if (state.lastCalibratedAt() != null && !Instant.now().isBefore(state.lastCalibratedAt().plus(maxModelAge))) {
                    CalibrationState staleState = CalibrationState.stale(
                        serviceId,
                        state.model(),
                        currentCount,
                        "Model age exceeds " + maxModelAge.toHours() + " hours"
                    );
                    serviceStates.put(serviceId, staleState);
                    return staleState;
                }
                if (state.observationCount() != currentCount) {
                    CalibrationState updatedState = CalibrationState.ready(serviceId, state.model(), currentCount);
                    serviceStates.put(serviceId, updatedState);
                    return updatedState;
                }
            }

            return state;
        }
    }

    /**
     * Bootstraps the baseline buffer from historical observations (e.g. at startup or recovery).
     *
     * @param serviceId service identifier
     * @param historicalObservations list of normal historical observations
     * @return count of successfully ingested observations
     */
    public int bootstrapFromHistory(Long serviceId, List<MultivariateFeatureVector> historicalObservations) {
        if (serviceId == null || historicalObservations == null || historicalObservations.isEmpty()) {
            return 0;
        }

        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> buffer = serviceBuffers.computeIfAbsent(
                serviceId,
                k -> Collections.synchronizedList(new ArrayList<>())
            );

            int added = 0;
            synchronized (buffer) {
                for (MultivariateFeatureVector vec : historicalObservations) {
                    if (isValidBaselineObservation(vec)) {
                        if (buffer.size() >= maxBufferSize) {
                            buffer.remove(0);
                        }
                        buffer.add(vec);
                        added++;
                    } else {
                        log.warn("Skipping invalid or stale observation during historical bootstrap for service {}: {}", serviceId, vec);
                    }
                }
            }

            if (buffer.size() >= LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
                calibrate(serviceId);
            } else {
                serviceStates.put(serviceId, CalibrationState.collecting(serviceId, buffer.size()));
            }

            return added;
        }
    }

    /**
     * Validates that an observation is finite, physically valid, and not stale relative to maxModelAge.
     */
    public boolean isValidBaselineObservation(MultivariateFeatureVector vector) {
        if (vector == null) {
            return false;
        }
        double[] vals = vector.toArray();
        for (double v : vals) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return false;
            }
        }
        if (vector.getCpuUsage() < MultivariateMetricType.CPU_USAGE.getMinPhysicalValue() ||
            vector.getCpuUsage() > MultivariateMetricType.CPU_USAGE.getMaxPhysicalValue()) {
            return false;
        }
        if (vector.getMemoryUsage() < MultivariateMetricType.MEMORY_USAGE.getMinPhysicalValue() ||
            vector.getMemoryUsage() > MultivariateMetricType.MEMORY_USAGE.getMaxPhysicalValue()) {
            return false;
        }
        if (vector.getResponseTime() < MultivariateMetricType.RESPONSE_TIME.getMinPhysicalValue() ||
            vector.getResponseTime() > MultivariateMetricType.RESPONSE_TIME.getMaxPhysicalValue()) {
            return false;
        }
        if (vector.getErrorRate() < MultivariateMetricType.ERROR_RATE.getMinPhysicalValue() ||
            vector.getErrorRate() > MultivariateMetricType.ERROR_RATE.getMaxPhysicalValue()) {
            return false;
        }

        if (vector.getRecordedAt() == null) {
            return false;
        }
        Duration allowedAge = (maxModelAge.isZero() || maxModelAge.isNegative())
            ? DEFAULT_MAX_MODEL_AGE : maxModelAge;
        Instant cutoff = Instant.now().minus(allowedAge);
        if (vector.getRecordedAt().isBefore(cutoff)) {
            return false;
        }
        if (vector.getRecordedAt().isAfter(Instant.now().plus(MultivariateFeatureVector.MAX_FUTURE_DRIFT))) {
            return false;
        }
        return true;
    }

    /**
     * Resets calibration state and buffer for a service.
     * Synchronizes on the service's designated stripe lock before clearing state.
     * The stripe lock is never removed or recreated, preventing split-brain concurrency.
     */
    public void resetService(Long serviceId) {
        if (serviceId == null) {
            return;
        }
        synchronized (getLock(serviceId)) {
            serviceBuffers.remove(serviceId);
            serviceWarmupBuffers.remove(serviceId);
            warmupTimestamps.remove(serviceId);
            confirmedTimestamps.remove(serviceId);
            serviceStates.remove(serviceId);
            samplesAtLastCalibration.remove(serviceId);
        }
    }

    /**
     * Clears all service buffers and calibration models.
     * Safely coordinates with all active operations across all services by
     * acquiring all stripe locks in deterministic ascending order (0 to N-1).
     * Prevents lock-ordering deadlocks and guarantees no thread modifies shared state
     * during the reset.
     */
    public void resetAll() {
        executeWithAllLocks(0, () -> {
            serviceBuffers.clear();
            serviceWarmupBuffers.clear();
            warmupTimestamps.clear();
            confirmedTimestamps.clear();
            serviceStates.clear();
            samplesAtLastCalibration.clear();
        });
    }

    private void executeWithAllLocks(int stripeIndex, Runnable action) {
        if (stripeIndex >= stripeLocks.length) {
            action.run();
            return;
        }
        synchronized (stripeLocks[stripeIndex]) {
            executeWithAllLocks(stripeIndex + 1, action);
        }
    }

    public int getObservationCount(Long serviceId) {
        if (serviceId == null) {
            return 0;
        }
        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> buffer = serviceBuffers.get(serviceId);
            if (buffer != null && !buffer.isEmpty()) {
                return buffer.size();
            }
            List<MultivariateFeatureVector> warmup = serviceWarmupBuffers.get(serviceId);
            return (warmup != null) ? warmup.size() : 0;
        }
    }

    public int getConfirmedObservationCount(Long serviceId) {
        if (serviceId == null) {
            return 0;
        }
        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> buffer = serviceBuffers.get(serviceId);
            return (buffer != null) ? buffer.size() : 0;
        }
    }

    public int getWarmupObservationCount(Long serviceId) {
        if (serviceId == null) {
            return 0;
        }
        synchronized (getLock(serviceId)) {
            List<MultivariateFeatureVector> warmup = serviceWarmupBuffers.get(serviceId);
            return (warmup != null) ? warmup.size() : 0;
        }
    }
}
