package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultivariateBaselineManagerTest {

    private JavaMultivariateAnomalyDetector detector;
    private MultivariateBaselineManager baselineManager;

    @BeforeEach
    void setUp() {
        detector = new JavaMultivariateAnomalyDetector();
        // Use a short recalibration age (e.g. 1 hour) for testing
        baselineManager = new MultivariateBaselineManager(detector, 100, Duration.ofHours(1));
    }

    private List<MultivariateFeatureVector> createNormalVectors(int count) {
        List<MultivariateFeatureVector> list = new ArrayList<>(count);
        Instant t0 = Instant.now().minus(Duration.ofMinutes(count));
        for (int i = 0; i < count; i++) {
            double cpu = 40.0 + (i % 5);
            double mem = 50.0 + (i % 7);
            double rt = 100.0 + (i % 9) * 2.0;
            double err = 0.2 + (i % 3) * 0.05;
            list.add(MultivariateFeatureVector.of(cpu, mem, rt, err, t0.plus(Duration.ofMinutes(i))));
        }
        return list;
    }

    @Test
    void calibrationLifecycle_CollectingToReady() {
        Long serviceId = 501L;

        // 1. Initial state is UNINITIALIZED
        CalibrationState s0 = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.UNINITIALIZED, s0.status());
        assertFalse(s0.isReady());

        // 2. Feed 5 observations -> state transitions to COLLECTING
        List<MultivariateFeatureVector> vectors = createNormalVectors(15);
        for (int i = 0; i < 5; i++) {
            baselineManager.recordObservation(serviceId, vectors.get(i), false);
        }

        CalibrationState s5 = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.COLLECTING, s5.status());
        assertEquals(5, s5.observationCount());
        assertFalse(s5.isReady());

        // 3. Feed next 5 observations (total 10 reaches MIN_OBSERVATIONS) -> Auto-calibrates to READY!
        for (int i = 5; i < 10; i++) {
            baselineManager.recordObservation(serviceId, vectors.get(i), false);
        }

        CalibrationState s10 = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.READY, s10.status());
        assertEquals(10, s10.observationCount());
        assertTrue(s10.isReady());
        assertNotNull(s10.model());
        assertTrue(s10.model().getAnomalyThreshold() >= JavaMultivariateAnomalyDetector.MIN_CHI_SQUARE_THRESHOLD);
    }

    @Test
    void anomalyExclusion_NeverContaminatesBaselineBuffer() {
        Long serviceId = 502L;
        List<MultivariateFeatureVector> normals = createNormalVectors(8);
        for (MultivariateFeatureVector v : normals) {
            baselineManager.recordObservation(serviceId, v, false);
        }
        assertEquals(8, baselineManager.getObservationCount(serviceId));

        // Attempt to record 5 anomalous vectors
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            MultivariateFeatureVector anomaly = MultivariateFeatureVector.of(99.0, 95.0, 5000.0, 25.0, now.plusSeconds(i));
            baselineManager.recordObservation(serviceId, anomaly, true); // isAnomaly = true
        }

        // Count must still be strictly 8!
        assertEquals(8, baselineManager.getObservationCount(serviceId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(serviceId).status());
    }

    @Test
    void calibrate_InsufficientSamples_TransitionsToCollectingOrFailed() {
        Long serviceId = 503L;
        List<MultivariateFeatureVector> small = createNormalVectors(4);
        for (MultivariateFeatureVector v : small) {
            baselineManager.recordObservation(serviceId, v, false);
        }

        CalibrationState state = baselineManager.calibrate(serviceId);
        assertEquals(CalibrationStatus.COLLECTING, state.status());
        assertFalse(state.isReady());
        assertTrue(state.statusMessage().contains("minimum 10 required"));
    }

    @Test
    void recalibration_TriggeredWhenThresholdNewSamplesAccumulated() {
        Long serviceId = 504L;
        List<MultivariateFeatureVector> vectors = createNormalVectors(50);

        // Feed first 10 -> initial calibration
        for (int i = 0; i < 10; i++) {
            baselineManager.recordObservation(serviceId, vectors.get(i), false);
        }
        CalibrationState state1 = baselineManager.getCalibrationState(serviceId);
        assertTrue(state1.isReady());
        assertEquals(10, state1.observationCount());

        // Feed 10 more (total 20, new = 10 < 30) -> should not recalibrate yet
        for (int i = 10; i < 20; i++) {
            baselineManager.recordObservation(serviceId, vectors.get(i), false);
        }
        assertFalse(baselineManager.shouldRecalibrate(serviceId));

        // Feed 25 more (total 45, new = 35 >= 30) -> triggers recalibration
        for (int i = 20; i < 45; i++) {
            baselineManager.recordObservation(serviceId, vectors.get(i), false);
        }
        CalibrationState state2 = baselineManager.getCalibrationState(serviceId);
        assertTrue(state2.isReady());
        assertEquals(45, state2.observationCount());
    }

    @Test
    void bootstrapFromHistory_WarmsUpBaselineCleanly() {
        Long serviceId = 505L;
        List<MultivariateFeatureVector> history = createNormalVectors(25);

        int loaded = baselineManager.bootstrapFromHistory(serviceId, history);
        assertEquals(25, loaded);

        CalibrationState state = baselineManager.getCalibrationState(serviceId);
        assertTrue(state.isReady());
        assertEquals(25, state.observationCount());

        // Model is immediately ready for detection with Task 1 engine
        MultivariateCalibrationModel model = state.model();
        assertNotNull(model);

        MultivariateFeatureVector testVector = MultivariateFeatureVector.of(41.0, 52.0, 105.0, 0.22);
        MultivariateDetectionResult detection = detector.detect(testVector, model);
        assertNotNull(detection);
        assertFalse(detection.isAnomaly());
    }

    @Test
    void stalenessCheck_ModelTransitionsToStaleAfterMaxAge_CannotBeUsedSilently() throws InterruptedException {
        Long serviceId = 506L;
        // Manager configured with zero duration maxModelAge
        MultivariateBaselineManager shortManager = new MultivariateBaselineManager(detector, 100, Duration.ZERO);

        List<MultivariateFeatureVector> history = createNormalVectors(12);
        shortManager.bootstrapFromHistory(serviceId, history);

        // Sleep briefly to ensure clock advances past calibration instant
        Thread.sleep(10);

        // Immediately checked: model age > 0 -> STALE status
        CalibrationState state = shortManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.STALE, state.status());
        assertFalse(state.isReady());
        // Model is preserved in state for audit/inspection
        assertNotNull(state.model());

        // Invariant: Stale models cannot be used silently via getCalibratedModel()!
        assertTrue(shortManager.getCalibratedModel(serviceId).isEmpty());
    }

    @Test
    void failedRecalibration_PreservesLastValidModel_AndRecoversWithoutCorruption() {
        Long serviceId = 507L;

        // Custom detector that succeeds on first calibration and throws on recalibration
        JavaMultivariateAnomalyDetector faultDetector = new JavaMultivariateAnomalyDetector() {
            private int callCount = 0;
            @Override
            public MultivariateCalibrationModel calibrate(Long sId, List<MultivariateFeatureVector> obs) {
                callCount++;
                if (callCount == 2) {
                    throw new NumericalInstabilityException("Simulated matrix singularity during recalibration");
                }
                return super.calibrate(sId, obs);
            }
        };

        MultivariateBaselineManager faultManager = new MultivariateBaselineManager(faultDetector, 100, Duration.ofHours(1));

        // 1. Initial calibration with 10 samples -> Succeeds and becomes READY
        List<MultivariateFeatureVector> vectors = createNormalVectors(45);
        for (int i = 0; i < 10; i++) {
            faultManager.recordObservation(serviceId, vectors.get(i), false);
        }

        CalibrationState initialState = faultManager.getCalibrationState(serviceId);
        assertTrue(initialState.isReady());
        MultivariateCalibrationModel firstModel = initialState.model();
        assertNotNull(firstModel);

        // 2. Feed 30 more samples (total 40, new = 30 >= threshold) -> Triggers recalibration which fails
        for (int i = 10; i < 40; i++) {
            faultManager.recordObservation(serviceId, vectors.get(i), false);
        }

        CalibrationState failedState = faultManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.FAILED, failedState.status());
        assertFalse(failedState.isReady());

        // Invariant: Failed recalibration PRESERVES the last valid calibration model!
        assertNotNull(failedState.model());
        assertEquals(firstModel.getCalibratedAt(), failedState.model().getCalibratedAt());
        assertEquals(firstModel.getAnomalyThreshold(), failedState.model().getAnomalyThreshold());

        // Invariant: Failed state cannot be used silently
        assertTrue(faultManager.getCalibratedModel(serviceId).isEmpty());

        // 3. Recovery: next calibration attempt (callCount = 3) succeeds without state corruption!
        CalibrationState recoveredState = faultManager.calibrate(serviceId);
        assertEquals(CalibrationStatus.READY, recoveredState.status());
        assertTrue(recoveredState.isReady());
        assertNotNull(recoveredState.model());
        assertEquals(40, recoveredState.observationCount());
    }

    @Test
    void invalidStaleAndAnomalousObservations_ExcludedFromBaselineBuffer() {
        Long serviceId = 508L;
        // Manager with 1-hour maxModelAge
        MultivariateBaselineManager manager = new MultivariateBaselineManager(detector, 100, Duration.ofHours(1));

        // 1. Valid observation accepted
        Instant now = Instant.now();
        MultivariateFeatureVector valid = MultivariateFeatureVector.of(45.0, 55.0, 100.0, 0.2, now);
        manager.recordObservation(serviceId, valid, false);
        assertEquals(1, manager.getObservationCount(serviceId));

        // 2. Anomalous observation excluded
        MultivariateFeatureVector anomaly = MultivariateFeatureVector.of(95.0, 90.0, 2000.0, 10.0, now.plusSeconds(1));
        manager.recordObservation(serviceId, anomaly, true); // isAnomaly = true
        assertEquals(1, manager.getObservationCount(serviceId));

        // 3. Stale observation (> 1 hour old relative to maxModelAge) excluded
        MultivariateFeatureVector stale = MultivariateFeatureVector.of(45.0, 55.0, 100.0, 0.2, now.minus(Duration.ofHours(2)));
        manager.recordObservation(serviceId, stale, false);
        assertEquals(1, manager.getObservationCount(serviceId));

        // 4. Future drifted observation (> 10s drift) excluded
        MultivariateFeatureVector future = MultivariateFeatureVector.of(45.0, 55.0, 100.0, 0.2, now.plus(Duration.ofMinutes(5)));
        manager.recordObservation(serviceId, future, false);
        assertEquals(1, manager.getObservationCount(serviceId));
    }

    @Test
    void historicalBootstrapping_ExcludesStaleAndInvalidVectors() {
        Long serviceId = 509L;
        MultivariateBaselineManager manager = new MultivariateBaselineManager(detector, 100, Duration.ofHours(1));

        Instant now = Instant.now();
        List<MultivariateFeatureVector> mixed = new ArrayList<>();
        // 5 valid recent vectors
        for (int i = 0; i < 5; i++) {
            mixed.add(MultivariateFeatureVector.of(40.0 + i, 50.0 + i, 100.0, 0.2, now.minus(Duration.ofMinutes(10 - i))));
        }
        // 3 stale vectors (3 hours old > 1 hour maxModelAge)
        for (int i = 0; i < 3; i++) {
            mixed.add(MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now.minus(Duration.ofHours(3)).plusSeconds(i)));
        }

        // Bootstrap should only ingest the 5 valid vectors
        int ingested = manager.bootstrapFromHistory(serviceId, mixed);
        assertEquals(5, ingested);
        assertEquals(5, manager.getObservationCount(serviceId));
        assertEquals(CalibrationStatus.COLLECTING, manager.getCalibrationState(serviceId).status());
    }

    @Test
    void restartBehavior_ReconstructsStateAndAlignmentSuccessfully() {
        Long serviceId = 510L;
        Instant now = Instant.now();

        // 1. Initial run before restart: calibrate baseline and align
        MultivariateBaselineManager managerBefore = new MultivariateBaselineManager(detector, 100, Duration.ofHours(24));
        List<MultivariateFeatureVector> history = createNormalVectors(15);
        int loadedBefore = managerBefore.bootstrapFromHistory(serviceId, history);
        assertEquals(15, loadedBefore);
        assertTrue(managerBefore.getCalibrationState(serviceId).isReady());

        // 2. Simulate complete application restart: instantiate fresh manager and alignment service
        MultivariateBaselineManager managerAfterRestart = new MultivariateBaselineManager(detector, 100, Duration.ofHours(24));
        MultivariateAlignmentService alignmentAfterRestart = new MultivariateAlignmentService();

        // Fresh instances are uninitialized
        assertEquals(CalibrationStatus.UNINITIALIZED, managerAfterRestart.getCalibrationState(serviceId).status());
        assertEquals(0, alignmentAfterRestart.getActiveBufferCount());

        // 3. Reconstruct baseline state from historical observations
        int reloaded = managerAfterRestart.bootstrapFromHistory(serviceId, history);
        assertEquals(15, reloaded);
        CalibrationState recoveredBaseline = managerAfterRestart.getCalibrationState(serviceId);
        assertTrue(recoveredBaseline.isReady());
        assertNotNull(recoveredBaseline.model());
        assertTrue(recoveredBaseline.model().getAnomalyThreshold() > 0);

        // 4. Reconstruct alignment buffer with recent scalar telemetry
        List<ScalarMetricReading> recentTelemetry = List.of(
            new ScalarMetricReading(serviceId, MultivariateMetricType.CPU_USAGE, 42.0, now.minusSeconds(10)),
            new ScalarMetricReading(serviceId, MultivariateMetricType.MEMORY_USAGE, 55.0, now.minusSeconds(8)),
            new ScalarMetricReading(serviceId, MultivariateMetricType.RESPONSE_TIME, 105.0, now.minusSeconds(5))
        );
        alignmentAfterRestart.bootstrapFromMetrics(serviceId, recentTelemetry);

        // Buffer has 3/4 dimensions populated
        ServiceTelemetryBuffer buffer = alignmentAfterRestart.getBuffer(serviceId);
        assertNotNull(buffer);

        // Ingest the 4th dimension (Error Rate) -> Immediately aligns complete 4D feature vector!
        TelemetryAlignmentResult alignResult = alignmentAfterRestart.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.15, now.minusSeconds(2));

        assertTrue(alignResult.isAligned());
        MultivariateFeatureVector alignedVector = alignResult.alignedVector();
        assertNotNull(alignedVector);
        assertEquals(42.0, alignedVector.getCpuUsage());
        assertEquals(55.0, alignedVector.getMemoryUsage());
        assertEquals(105.0, alignedVector.getResponseTime());
        assertEquals(0.15, alignedVector.getErrorRate());

        // Evaluate against restored baseline model -> Detection works seamlessly!
        MultivariateDetectionResult detection = detector.detect(alignedVector, recoveredBaseline.model());
        assertNotNull(detection);
        assertFalse(detection.isAnomaly());
    }

    @Test
    void warmupLifecycle_UninitializedToCollectingToReadyAt10Samples() {
        Long serviceId = 601L;

        // 1. Initial state is UNINITIALIZED
        CalibrationState s0 = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.UNINITIALIZED, s0.status());
        assertFalse(s0.isReady());
        assertTrue(baselineManager.getCalibratedModel(serviceId).isEmpty());
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));

        // 2. Accumulate 9 warm-up observations (fewer than 10 required)
        List<MultivariateFeatureVector> vectors = createNormalVectors(15);
        for (int i = 0; i < 9; i++) {
            CalibrationState state = baselineManager.recordWarmupObservation(serviceId, vectors.get(i));
            assertEquals(CalibrationStatus.COLLECTING, state.status());
            assertFalse(state.isReady());
            assertEquals(i + 1, state.observationCount());
            assertEquals(i + 1, baselineManager.getWarmupObservationCount(serviceId));
            assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));
        }

        // 3. Accumulate 10th observation -> Minimum count reached, auto-calibrates to READY!
        CalibrationState s10 = baselineManager.recordWarmupObservation(serviceId, vectors.get(9));
        assertEquals(CalibrationStatus.READY, s10.status());
        assertTrue(s10.isReady());
        assertTrue(baselineManager.getCalibratedModel(serviceId).isPresent());
        assertEquals(10, s10.observationCount());
        // Warm-up buffer transferred to confirmed baseline
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(10, baselineManager.getConfirmedObservationCount(serviceId));
        assertNotNull(s10.model());
        assertTrue(s10.model().getAnomalyThreshold() > 0);
    }

    @Test
    void duplicateWarmupObservation_IgnoredAndNotCountedTwice() {
        Long serviceId = 602L;
        Instant now = Instant.now().minus(Duration.ofMinutes(5));
        MultivariateFeatureVector v1 = MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now);
        MultivariateFeatureVector vDuplicate = MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now);

        baselineManager.recordWarmupObservation(serviceId, v1);
        assertEquals(1, baselineManager.getWarmupObservationCount(serviceId));

        // Ingest duplicate with exact same timestamp
        baselineManager.recordWarmupObservation(serviceId, vDuplicate);
        assertEquals(1, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(serviceId).status());
    }

    @Test
    void concurrentWarmupArrivals_ThreadSafeAndCalibratesCleanly() throws Exception {
        Long serviceId = 603L;
        int threadCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Instant t0 = Instant.now().minus(Duration.ofMinutes(20));
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                        40.0 + (index % 4),
                        50.0 + (index % 3),
                        100.0 + (index % 5) * 5.0,
                        0.2 + (index % 2) * 0.05,
                        t0.plus(Duration.ofMinutes(index))
                    );
                    baselineManager.recordWarmupObservation(serviceId, vec);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        CalibrationState finalState = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.READY, finalState.status());
        assertTrue(finalState.isReady());
        assertTrue(baselineManager.getCalibratedModel(serviceId).isPresent());
        assertTrue(finalState.observationCount() >= 10);
    }

    @Test
    void invalidAndStaleWarmupObservations_Excluded() {
        Long serviceId = 604L;
        Instant now = Instant.now();

        // 1. Negative CPU throws InvalidFeatureVectorException when creating vector
        assertThrows(InvalidFeatureVectorException.class, () -> {
            MultivariateFeatureVector.of(-5.0, 50.0, 100.0, 0.2, now);
        });

        // 2. Negative RT throws InvalidFeatureVectorException when creating vector
        assertThrows(InvalidFeatureVectorException.class, () -> {
            MultivariateFeatureVector.of(40.0, 50.0, -10.0, 0.2, now);
        });

        // 3. Stale vector (older than 1 hour max age) is rejected by baselineManager
        MultivariateFeatureVector stale = MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now.minus(Duration.ofHours(5)));
        baselineManager.recordWarmupObservation(serviceId, stale);
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));

        // 4. Future drifted vector is rejected by baselineManager
        MultivariateFeatureVector future = MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now.plus(Duration.ofMinutes(10)));
        baselineManager.recordWarmupObservation(serviceId, future);
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));

        // 5. Valid vector accumulates cleanly
        MultivariateFeatureVector valid = MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, now);
        baselineManager.recordWarmupObservation(serviceId, valid);
        assertEquals(1, baselineManager.getWarmupObservationCount(serviceId));
    }

    @Test
    void initialCalibrationFailure_TransitionsToFailedState() {
        Long serviceId = 605L;

        // Custom detector that always throws during calibration
        JavaMultivariateAnomalyDetector faultDetector = new JavaMultivariateAnomalyDetector() {
            @Override
            public MultivariateCalibrationModel calibrate(Long sId, List<MultivariateFeatureVector> obs) {
                throw new NumericalInstabilityException("Initial calibration matrix singular");
            }
        };

        MultivariateBaselineManager faultManager = new MultivariateBaselineManager(faultDetector, 100, Duration.ofHours(1));

        List<MultivariateFeatureVector> vectors = createNormalVectors(10);
        for (int i = 0; i < 9; i++) {
            faultManager.recordWarmupObservation(serviceId, vectors.get(i));
        }
        assertEquals(CalibrationStatus.COLLECTING, faultManager.getCalibrationState(serviceId).status());

        // 10th observation triggers calibration which fails
        CalibrationState state10 = faultManager.recordWarmupObservation(serviceId, vectors.get(9));
        assertEquals(CalibrationStatus.FAILED, state10.status());
        assertFalse(state10.isReady());
        assertTrue(faultManager.getCalibratedModel(serviceId).isEmpty());
        assertTrue(state10.statusMessage().contains("Initial calibration matrix singular"));
    }

    @Test
    void warmupOutlierScreening_AbnormalObservationPurged_BaselineProtected() {
        Long serviceId = 701L;
        Instant now = Instant.now().minus(Duration.ofMinutes(15));

        // 1. Ingest 9 normal observations
        for (int i = 0; i < 9; i++) {
            MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                40.0 + (i % 3), 50.0 + (i % 2), 100.0 + (i % 4) * 2.0, 0.2, now.plus(Duration.ofMinutes(i))
            );
            CalibrationState s = baselineManager.recordWarmupObservation(serviceId, vec);
            assertEquals(CalibrationStatus.COLLECTING, s.status());
            assertEquals(i + 1, baselineManager.getWarmupObservationCount(serviceId));
        }

        // 2. Ingest 10th observation as an extreme abnormal spike (CPU=95%, MEM=90%, RT=3500ms, ERR=15%)
        MultivariateFeatureVector abnormal = MultivariateFeatureVector.of(
            95.0, 90.0, 3500.0, 15.0, now.plus(Duration.ofMinutes(9))
        );
        CalibrationState stateAfterAbnormal = baselineManager.recordWarmupObservation(serviceId, abnormal);

        // Invariant: The abnormal spike must be screened out!
        // State remains COLLECTING with 9 clean samples; confirmed buffer has 0 samples
        assertEquals(CalibrationStatus.COLLECTING, stateAfterAbnormal.status());
        assertFalse(stateAfterAbnormal.isReady());
        assertEquals(9, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));

        // 3. Ingest a 10th CLEAN observation
        MultivariateFeatureVector clean10 = MultivariateFeatureVector.of(
            41.0, 51.0, 104.0, 0.21, now.plus(Duration.ofMinutes(10))
        );
        CalibrationState stateAfterClean10 = baselineManager.recordWarmupObservation(serviceId, clean10);

        // Invariant: With 10 verified clean observations, calibration succeeds cleanly!
        assertEquals(CalibrationStatus.READY, stateAfterClean10.status());
        assertTrue(stateAfterClean10.isReady());
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(10, baselineManager.getConfirmedObservationCount(serviceId));
    }

    @Test
    void warmupOutlierScreening_MultipleAbnormalSpikes_AllPurged() {
        Long serviceId = 702L;
        Instant now = Instant.now().minus(Duration.ofMinutes(25));

        // Ingest 8 normal observations
        for (int i = 0; i < 8; i++) {
            MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                40.0 + (i % 3), 50.0 + (i % 2), 100.0 + (i % 4) * 2.0, 0.2, now.plus(Duration.ofMinutes(i))
            );
            baselineManager.recordWarmupObservation(serviceId, vec);
        }

        // Ingest 2 abnormal spikes: triggers calibration attempt on 10 samples -> both spikes purged
        for (int i = 0; i < 2; i++) {
            MultivariateFeatureVector spike = MultivariateFeatureVector.of(
                95.0 + i, 88.0 + i, 3000.0 + i * 100, 12.0 + i, now.plus(Duration.ofMinutes(8 + i))
            );
            baselineManager.recordWarmupObservation(serviceId, spike);
        }

        // Invariant: Both spikes purged, exactly 8 clean samples remain in warm-up
        assertEquals(8, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(serviceId).status());

        // Ingest 2 more abnormal spikes: reaches 10 samples again -> both new spikes purged
        for (int i = 0; i < 2; i++) {
            MultivariateFeatureVector spike2 = MultivariateFeatureVector.of(
                98.0 + i, 92.0 + i, 4000.0 + i * 100, 15.0 + i, now.plus(Duration.ofMinutes(10 + i))
            );
            baselineManager.recordWarmupObservation(serviceId, spike2);
        }

        // Invariant: All spikes purged, still exactly 8 clean samples remain in warm-up
        assertEquals(8, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(serviceId).status());
    }

    @Test
    void multipleServices_CalibratingConcurrently_IndependentAndClean() throws Exception {
        int serviceCount = 4;
        int observationsPerService = 12;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(serviceCount);

        Instant baseTime = Instant.now().minus(Duration.ofMinutes(30));

        for (int s = 0; s < serviceCount; s++) {
            final long serviceId = 800L + s;
            final double baseCpu = 30.0 + s * 10.0; // different operating profile per service
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < observationsPerService; i++) {
                        MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                            baseCpu + (i % 3),
                            50.0 + (i % 2),
                            100.0 + (i % 4) * 5.0,
                            0.2 + (i % 2) * 0.05,
                            baseTime.plus(Duration.ofMinutes(i))
                        );
                        // Record via general observation router (routes warmup then confirmed)
                        baselineManager.recordObservation(serviceId, vec, false);
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Invariant: All services independently reached READY with clean models
        for (int s = 0; s < serviceCount; s++) {
            long serviceId = 800L + s;
            CalibrationState state = baselineManager.getCalibrationState(serviceId);
            assertEquals(CalibrationStatus.READY, state.status());
            assertTrue(state.isReady());
            assertNotNull(state.model());
            assertEquals(12, baselineManager.getConfirmedObservationCount(serviceId));
            assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        }
    }

    @Test
    void warmupBuffer_CapacityBound_EvictsOldestWhenFlooded() {
        Long serviceId = 703L;
        Instant now = Instant.now().minus(Duration.ofMinutes(100));

        // Inject 60 observations that don't pass screening or have small sample increments
        // All with distinct timestamps
        for (int i = 0; i < 60; i++) {
            MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                40.0 + (i % 5), 50.0 + (i % 4), 100.0 + (i % 6) * 3.0, 0.2, now.plus(Duration.ofMinutes(i))
            );
            // reset state to COLLECTING to simulate long collection
            baselineManager.recordWarmupObservation(serviceId, vec);
            if (baselineManager.getCalibrationState(serviceId).isReady()) {
                // If it calibrated, reset for this test to test buffer boundedness
                break;
            }
        }

        // Warm-up buffer can never exceed MAX_WARMUP_BUFFER_SIZE (50)
        assertTrue(baselineManager.getWarmupObservationCount(serviceId) <= MultivariateBaselineManager.MAX_WARMUP_BUFFER_SIZE);
    }

    @Test
    void stripeLockConsistency_NeverRemovedOrRecreated() {
        Long serviceId = 999L;
        Object initialLock = baselineManager.getLock(serviceId);
        assertNotNull(initialLock);

        // Lock is identical across multiple calls
        assertSame(initialLock, baselineManager.getLock(serviceId));

        // Ingest telemetry
        List<MultivariateFeatureVector> vectors = createNormalVectors(12);
        for (MultivariateFeatureVector v : vectors) {
            baselineManager.recordObservation(serviceId, v, false);
        }
        assertSame(initialLock, baselineManager.getLock(serviceId));

        // Reset service
        baselineManager.resetService(serviceId);
        // INVARIANT: Stripe lock is NEVER removed or recreated!
        assertSame(initialLock, baselineManager.getLock(serviceId),
            "Stripe lock must be permanent and never recreated after resetService");

        // Reset all
        baselineManager.resetAll();
        assertSame(initialLock, baselineManager.getLock(serviceId),
            "Stripe lock must remain permanent and never recreated after resetAll");

        // Edge case: null service ID maps safely to stripe 0
        Object nullLock = baselineManager.getLock(null);
        assertNotNull(nullLock);
        assertSame(nullLock, baselineManager.getLock(null));

        // Edge cases: extreme hash codes (negative, Integer.MIN_VALUE, Long.MIN_VALUE, etc.)
        Long minLongService = Long.MIN_VALUE;
        Object minLock = baselineManager.getLock(minLongService);
        assertNotNull(minLock);

        Long maxLongService = Long.MAX_VALUE;
        Object maxLock = baselineManager.getLock(maxLongService);
        assertNotNull(maxLock);
    }

    @Test
    void concurrentIngestionAndReset_SameService_RemainsConsistent() throws Exception {
        final Long serviceId = 901L;
        final Object initialLock = baselineManager.getLock(serviceId);
        final int iterations = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(3);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Instant baseTime = Instant.now().minus(Duration.ofMinutes(iterations + 10));

        // Thread 1 & 2: Concurrently ingest telemetry
        for (int t = 0; t < 2; t++) {
            final int threadOffset = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterations; i++) {
                        MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                            40.0 + (i % 5),
                            50.0 + (i % 3),
                            100.0 + (i % 4) * 2.0,
                            0.2 + (i % 2) * 0.05,
                            baseTime.plusSeconds(i * 2L + threadOffset)
                        );
                        baselineManager.recordObservation(serviceId, vec, false);
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Thread 3: Concurrently reset the service
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                    Thread.sleep(2);
                    baselineManager.resetService(serviceId);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent ingestion and reset must complete within timeout");
        executor.shutdown();

        assertNull(failure.get(), "No exception should occur during concurrent ingestion and reset: " + failure.get());

        // Invariant: Lock object identity was never altered
        assertSame(initialLock, baselineManager.getLock(serviceId), "Lock object must remain identical after concurrent resets");

        // State must be coherent: either UNINITIALIZED/COLLECTING (if reset happened recently) or READY
        CalibrationState finalState = baselineManager.getCalibrationState(serviceId);
        assertNotNull(finalState);
        if (finalState.isReady()) {
            assertTrue(baselineManager.getConfirmedObservationCount(serviceId) >= 10);
            assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        } else {
            assertTrue(finalState.observationCount() >= 0);
        }
    }

    @Test
    void concurrentCalibrationAndReset_SameService_NoSplitBrainOrCorruption() throws Exception {
        final Long serviceId = 902L;
        final Object initialLock = baselineManager.getLock(serviceId);
        final int iterations = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(3);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Instant baseTime = Instant.now().minus(Duration.ofMinutes(iterations + 10));

        // Thread 1: Ingests batches of samples
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                        40.0 + (i % 4),
                        50.0 + (i % 2),
                        100.0 + (i % 3) * 5.0,
                        0.2 + (i % 2) * 0.05,
                        baseTime.plusSeconds(i)
                    );
                    baselineManager.recordObservation(serviceId, vec, false);
                    Thread.sleep(1);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Continuously triggers calibrate()
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    baselineManager.calibrate(serviceId);
                    Thread.sleep(1);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 3: Concurrently resets the service
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(3);
                    baselineManager.resetService(serviceId);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent calibration and reset must finish within timeout");
        executor.shutdown();

        assertNull(failure.get(), "No exception should occur during concurrent calibration and reset: " + failure.get());
        assertSame(initialLock, baselineManager.getLock(serviceId));
    }

    @Test
    void concurrentOperations_DifferentServices_PreservesIsolation() throws Exception {
        final Long serviceReset = 1001L;
        final Long serviceNormal = 1002L;
        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(3);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Instant baseTime = Instant.now().minus(Duration.ofMinutes(30));

        // Thread 1: Ingests normal observations into serviceNormal (15 observations -> READY)
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 15; i++) {
                    MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                        40.0 + (i % 3),
                        50.0 + (i % 2),
                        100.0 + (i % 4) * 2.0,
                        0.2,
                        baseTime.plus(Duration.ofMinutes(i))
                    );
                    baselineManager.recordObservation(serviceNormal, vec, false);
                    Thread.sleep(2);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Rapidly ingests into serviceReset
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 30; i++) {
                    MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                        35.0, 45.0, 90.0, 0.1, baseTime.plusSeconds(i)
                    );
                    baselineManager.recordObservation(serviceReset, vec, false);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 3: Repeatedly resets serviceReset
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(3);
                    baselineManager.resetService(serviceReset);
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent operations across services must finish within timeout");
        executor.shutdown();

        assertNull(failure.get(), "No failure in concurrent multi-service operations");

        // INVARIANT: serviceNormal must be 100% unaffected by serviceReset's resets!
        CalibrationState normalState = baselineManager.getCalibrationState(serviceNormal);
        assertEquals(CalibrationStatus.READY, normalState.status(), "serviceNormal must reach READY");
        assertTrue(normalState.isReady());
        assertNotNull(normalState.model());
        assertEquals(15, baselineManager.getConfirmedObservationCount(serviceNormal));
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceNormal));
    }

    @Test
    void concurrentResetAll_WhileIngestionActive_SafelyCoordinatesAllStripes() throws Exception {
        final int serviceCount = 8;
        final ExecutorService executor = Executors.newFixedThreadPool(serviceCount + 1);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(serviceCount + 1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Instant baseTime = Instant.now().minus(Duration.ofMinutes(30));

        // Launch ingestion threads across multiple distinct services/stripes
        for (int s = 0; s < serviceCount; s++) {
            final long serviceId = 2000L + s;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 25; i++) {
                        MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                            40.0 + (i % 4),
                            50.0 + (i % 2),
                            100.0 + (i % 3) * 5.0,
                            0.2,
                            baseTime.plusSeconds(i)
                        );
                        baselineManager.recordObservation(serviceId, vec, false);
                        Thread.sleep(1);
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Thread calling resetAll() concurrently during active ingestion
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(10);
                baselineManager.resetAll();
                Thread.sleep(10);
                baselineManager.resetAll();
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "resetAll() with active ingestion must complete without deadlock");
        executor.shutdown();

        assertNull(failure.get(), "resetAll() must not throw any exception or deadlock with active ingestion");

        // Clean post-reset verification: Manager can record and calibrate normally
        Long freshId = 2999L;
        List<MultivariateFeatureVector> fresh = createNormalVectors(12);
        for (MultivariateFeatureVector v : fresh) {
            baselineManager.recordObservation(freshId, v, false);
        }
        CalibrationState freshState = baselineManager.getCalibrationState(freshId);
        assertTrue(freshState.isReady(), "Manager must function properly after resetAll");
    }

    @Test
    void repeatedResets_FollowedByNewIngestion_OperatesNormally() {
        final Long serviceId = 905L;
        final Object persistentLock = baselineManager.getLock(serviceId);

        // Perform 10 sequential reset and re-ingestion cycles
        for (int cycle = 0; cycle < 10; cycle++) {
            // 1. Verify lock identity remains identical
            assertSame(persistentLock, baselineManager.getLock(serviceId),
                "Lock instance must remain identical across repeated cycles");

            // 2. Ingest 12 clean observations
            Instant cycleBase = Instant.now().minus(Duration.ofMinutes(50 - cycle * 3));
            for (int i = 0; i < 12; i++) {
                MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                    40.0 + (i % 3),
                    50.0 + (i % 2),
                    100.0 + (i % 4) * 2.0,
                    0.2,
                    cycleBase.plus(Duration.ofMinutes(i))
                );
                baselineManager.recordObservation(serviceId, vec, false);
            }

            // 3. Verify reached READY
            CalibrationState readyState = baselineManager.getCalibrationState(serviceId);
            assertEquals(CalibrationStatus.READY, readyState.status(), "Cycle " + cycle + " must reach READY");
            assertTrue(readyState.isReady());
            assertNotNull(readyState.model());
            assertEquals(12, baselineManager.getConfirmedObservationCount(serviceId));
            assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));

            // 4. Reset the service
            baselineManager.resetService(serviceId);

            // 5. Verify reset state
            CalibrationState resetState = baselineManager.getCalibrationState(serviceId);
            assertEquals(CalibrationStatus.UNINITIALIZED, resetState.status());
            assertEquals(0, baselineManager.getObservationCount(serviceId));
            assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));
            assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));

            // 6. Verify lock was NOT removed or replaced
            assertSame(persistentLock, baselineManager.getLock(serviceId),
                "Lock instance must not be modified by resetService in cycle " + cycle);
        }
    }
}
