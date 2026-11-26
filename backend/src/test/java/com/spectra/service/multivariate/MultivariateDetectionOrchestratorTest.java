package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;

class MultivariateDetectionOrchestratorTest {

    private MultivariateAlignmentService alignmentService;
    private MultivariateBaselineManager baselineManager;
    private JavaMultivariateAnomalyDetector detector;
    private MultivariateAnomalyEventPersister persister;
    private MultivariateAnomalyOrchestrator enabledOrchestrator;
    private MultivariateAnomalyOrchestrator disabledOrchestrator;

    private MonitoredService testService;

    @BeforeEach
    void setUp() {
        alignmentService = new MultivariateAlignmentService();
        detector = new JavaMultivariateAnomalyDetector();
        baselineManager = new MultivariateBaselineManager(detector, 100, Duration.ofHours(24));
        persister = mock(MultivariateAnomalyEventPersister.class);

        enabledOrchestrator = new MultivariateAnomalyOrchestrator(
            alignmentService, baselineManager, detector, persister, true);
        disabledOrchestrator = new MultivariateAnomalyOrchestrator(
            alignmentService, baselineManager, detector, persister, false);

        testService = new MonitoredService();
        testService.setId(101L);
        testService.setName("Payment Gateway");
    }

    private ServiceMetric createMetric(Long serviceId, String name, double value, String unit, Instant recordedAt) {
        ServiceMetric m = new ServiceMetric();
        m.setId((long) (Math.random() * 100000));
        MonitoredService s = new MonitoredService();
        s.setId(serviceId != null ? serviceId : 101L);
        s.setName("Service " + s.getId());
        m.setService(s);
        m.setMetricName(name);
        m.setMetricValue(value);
        m.setUnit(unit);
        m.setRecordedAt(recordedAt);
        return m;
    }

    private void warmUpBaseline(Long serviceId, int count) {
        List<MultivariateFeatureVector> history = new ArrayList<>();
        Instant t0 = Instant.now().minus(Duration.ofMinutes(count));
        for (int i = 0; i < count; i++) {
            history.add(MultivariateFeatureVector.of(
                40.0 + (i % 5),
                50.0 + (i % 7),
                100.0 + (i % 9) * 2.0,
                0.2 + (i % 3) * 0.05,
                t0.plus(Duration.ofMinutes(i))
            ));
        }
        baselineManager.bootstrapFromHistory(serviceId, history);
    }

    @Test
    void featureFlag_Disabled_ReturnsEmptyAndDoesNotProcess() {
        ServiceMetric m = createMetric(101L, "CPU_USAGE", 45.0, "PERCENT", Instant.now());
        Optional<AnomalyEvent> result = disabledOrchestrator.processMetric(m);

        assertTrue(result.isEmpty());
        assertEquals(0, alignmentService.getActiveBufferCount());
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void incompleteTelemetry_WaitingForDimensions_ReturnsEmpty() {
        warmUpBaseline(101L, 15);
        Instant now = Instant.now();

        // Ingest only CPU and MEM
        Optional<AnomalyEvent> res1 = enabledOrchestrator.processMetric(
            createMetric(101L, "CPU_USAGE", 42.0, "PERCENT", now));
        Optional<AnomalyEvent> res2 = enabledOrchestrator.processMetric(
            createMetric(101L, "MEMORY_USAGE", 55.0, "PERCENT", now));

        assertTrue(res1.isEmpty());
        assertTrue(res2.isEmpty());
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void missingCalibration_DoesNotInvokeDetection_AndDoesNotContaminateBaseline() {
        // Service 102 has NO baseline calibrated (UNINITIALIZED)
        Long sId = 102L;
        MonitoredService s = new MonitoredService();
        s.setId(sId);

        Instant now = Instant.now();
        // Ingest all 4 dimensions
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 40.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 100.0, "MILLISECONDS", now));
        Optional<AnomalyEvent> result = enabledOrchestrator.processMetric(
            createMetric(sId, "ERROR_RATE", 0.2, "PERCENT", now));

        // Detection skipped because calibration is absent
        assertTrue(result.isEmpty());
        assertEquals(0, baselineManager.getConfirmedObservationCount(sId));
        assertEquals(1, baselineManager.getWarmupObservationCount(sId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(sId).status());
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void staleCalibration_DoesNotInvokeDetection() {
        Long sId = 103L;
        // Manager with zero duration max age -> model is immediately STALE
        MultivariateBaselineManager shortManager = new MultivariateBaselineManager(detector, 100, Duration.ZERO);
        MultivariateAnomalyOrchestrator orchestratorWithShortMgr = new MultivariateAnomalyOrchestrator(
            alignmentService, shortManager, detector, persister, true);

        // Warm up
        List<MultivariateFeatureVector> history = new ArrayList<>();
        Instant t0 = Instant.now().minus(Duration.ofMinutes(15));
        for (int i = 0; i < 15; i++) {
            history.add(MultivariateFeatureVector.of(40.0, 50.0, 100.0, 0.2, t0.plus(Duration.ofMinutes(i))));
        }
        shortManager.bootstrapFromHistory(sId, history);

        // State is STALE
        assertEquals(CalibrationStatus.STALE, shortManager.getCalibrationState(sId).status());

        Instant now = Instant.now();
        alignmentService.ingestAndAlign(sId, "CPU_USAGE", 40.0, now);
        alignmentService.ingestAndAlign(sId, "MEMORY_USAGE", 50.0, now);
        alignmentService.ingestAndAlign(sId, "RESPONSE_TIME", 100.0, now);
        ServiceMetric err = createMetric(sId, "ERROR_RATE", 0.2, "PERCENT", now);

        Optional<AnomalyEvent> result = orchestratorWithShortMgr.processMetric(err);
        assertTrue(result.isEmpty());
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void completeNormalVector_EvaluatedAndAddedToBaseline_NoAnomalyEvent() {
        warmUpBaseline(101L, 15);
        int initialCount = baselineManager.getObservationCount(101L);

        Instant now = Instant.now();
        enabledOrchestrator.processMetric(createMetric(101L, "CPU_USAGE", 42.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(101L, "MEMORY_USAGE", 52.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(101L, "RESPONSE_TIME", 102.0, "MILLISECONDS", now));
        Optional<AnomalyEvent> result = enabledOrchestrator.processMetric(
            createMetric(101L, "ERROR_RATE", 0.22, "PERCENT", now));

        assertTrue(result.isEmpty());
        // Normal observation is added to baseline buffer!
        assertEquals(initialCount + 1, baselineManager.getObservationCount(101L));
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void completeAnomalousVector_InvokesPersister_AndExcludesFromBaseline() {
        warmUpBaseline(101L, 15);
        int initialCount = baselineManager.getObservationCount(101L);

        AnomalyEvent mockPersistedEvent = new AnomalyEvent();
        mockPersistedEvent.setId(999L);
        when(persister.persistMultivariateAnomaly(any(), any(), any(), any()))
            .thenReturn(Optional.of(mockPersistedEvent));

        Instant now = Instant.now();
        // Massive correlated multi-dimensional anomaly (CPU spike + RT spike + ERR spike)
        enabledOrchestrator.processMetric(createMetric(101L, "CPU_USAGE", 98.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(101L, "MEMORY_USAGE", 95.0, "PERCENT", now));
        enabledOrchestrator.processMetric(createMetric(101L, "RESPONSE_TIME", 8500.0, "MILLISECONDS", now));
        Optional<AnomalyEvent> result = enabledOrchestrator.processMetric(
            createMetric(101L, "ERROR_RATE", 35.0, "PERCENT", now));

        assertTrue(result.isPresent());
        assertEquals(999L, result.get().getId());

        // Invariant: Anomaly must NOT be added to baseline buffer!
        assertEquals(initialCount, baselineManager.getObservationCount(101L));
        verify(persister).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void staleTelemetry_DroppedAndNotEvaluated() {
        warmUpBaseline(101L, 15);
        Instant now = Instant.now();

        // Advance buffer with CPU at now
        enabledOrchestrator.processMetric(createMetric(101L, "CPU_USAGE", 45.0, "PERCENT", now));

        // Ingest stale memory reading (400 seconds old > 300s freshness limit)
        Instant staleMemTime = now.minusSeconds(400);
        Optional<AnomalyEvent> res = enabledOrchestrator.processMetric(
            createMetric(101L, "MEMORY_USAGE", 55.0, "PERCENT", staleMemTime));

        assertTrue(res.isEmpty());
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());
    }

    @Test
    void invalidNumericTelemetry_RejectedBeforeAlignment() {
        warmUpBaseline(101L, 15);
        Instant now = Instant.now();

        // NaN value
        Optional<AnomalyEvent> res1 = enabledOrchestrator.processMetric(
            createMetric(101L, "CPU_USAGE", Double.NaN, "PERCENT", now));
        assertTrue(res1.isEmpty());

        // Negative CPU
        Optional<AnomalyEvent> res2 = enabledOrchestrator.processMetric(
            createMetric(101L, "CPU_USAGE", -5.0, "PERCENT", now));
        assertTrue(res2.isEmpty());

        // RT exceeding maximum 60,000 ms
        Optional<AnomalyEvent> res3 = enabledOrchestrator.processMetric(
            createMetric(101L, "RESPONSE_TIME", 75000.0, "MILLISECONDS", now));
        assertTrue(res3.isEmpty());
    }

    @Test
    void attributionMetadata_PreservedInPersisterFormat() {
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(85.0, 70.0, 450.0, 5.0, Instant.now());
        MultivariateCalibrationModel model = new MultivariateCalibrationModel(
            101L,
            new double[] { 40.0, 50.0, 100.0, 0.2 },
            new double[] { 1.0, 1.0, 5.0, 0.1 },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            0.05,
            1e-6,
            3.644,
            20,
            Instant.now()
        );

        MultivariateDetectionResult detection = detector.detect(vector, model);
        assertTrue(detection.isAnomaly());

        String message = MultivariateAnomalyEventPersister.buildAttributionMessage(vector, detection, model);
        assertNotNull(message);
        assertTrue(message.contains("[MULTIVARIATE_MAHALANOBIS]"));
        assertTrue(message.contains("non-causal correlation"));
        assertTrue(message.contains("CPU_USAGE"));
        assertTrue(message.length() <= 255);
    }

    @Test
    void endToEndWarmupLifecycle_NewServiceToCollectingToReadyToAnomalyDetection() {
        Long sId = 104L;
        MonitoredService s = new MonitoredService();
        s.setId(sId);
        s.setName("Warm-up Order Service");

        AnomalyEvent mockPersistedEvent = new AnomalyEvent();
        mockPersistedEvent.setId(999L);
        when(persister.persistMultivariateAnomaly(any(), any(), any(), any()))
            .thenReturn(Optional.of(mockPersistedEvent));

        // 1. Initial state: UNINITIALIZED, no model
        CalibrationState state0 = baselineManager.getCalibrationState(sId);
        assertEquals(CalibrationStatus.UNINITIALIZED, state0.status());
        assertFalse(state0.isReady());

        Instant baseTime = Instant.now().minus(Duration.ofMinutes(20));

        // 2. Feed 9 complete 4D vectors (less than minimum 10 required)
        for (int i = 0; i < 9; i++) {
            Instant t = baseTime.plus(Duration.ofMinutes(i));
            enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 40.0 + (i % 3), "PERCENT", t));
            enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.0 + (i % 2), "PERCENT", t));
            enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 100.0 + (i % 4) * 2.0, "MILLISECONDS", t));
            Optional<AnomalyEvent> res = enabledOrchestrator.processMetric(
                createMetric(sId, "ERROR_RATE", 0.2 + (i % 2) * 0.05, "PERCENT", t));

            assertTrue(res.isEmpty(), "Warm-up phase must not perform anomaly detection");
            assertEquals(0, baselineManager.getConfirmedObservationCount(sId));
            assertEquals(i + 1, baselineManager.getWarmupObservationCount(sId));
            assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(sId).status());
        }

        // Persister must never have been called during warm-up
        verify(persister, never()).persistMultivariateAnomaly(any(), any(), any(), any());

        // 3. Feed 10th complete 4D vector -> Minimum count reached, auto-calibrates to READY!
        Instant t10 = baseTime.plus(Duration.ofMinutes(9));
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 41.0, "PERCENT", t10));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 51.0, "PERCENT", t10));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 104.0, "MILLISECONDS", t10));
        Optional<AnomalyEvent> res10 = enabledOrchestrator.processMetric(
            createMetric(sId, "ERROR_RATE", 0.22, "PERCENT", t10));

        assertTrue(res10.isEmpty(), "10th vector completes calibration, does not flag anomaly");
        CalibrationState stateReady = baselineManager.getCalibrationState(sId);
        assertEquals(CalibrationStatus.READY, stateReady.status());
        assertTrue(stateReady.isReady());
        assertEquals(0, baselineManager.getWarmupObservationCount(sId));
        assertEquals(10, baselineManager.getConfirmedObservationCount(sId));

        // 4. Feed 11th vector (normal) -> Evaluated against calibrated baseline, added to confirmed
        Instant t11 = baseTime.plus(Duration.ofMinutes(10));
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 41.5, "PERCENT", t11));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.5, "PERCENT", t11));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 102.0, "MILLISECONDS", t11));
        Optional<AnomalyEvent> res11 = enabledOrchestrator.processMetric(
            createMetric(sId, "ERROR_RATE", 0.21, "PERCENT", t11));

        assertTrue(res11.isEmpty());
        assertEquals(11, baselineManager.getConfirmedObservationCount(sId));

        // 5. Feed 12th vector (severe anomaly: CPU=95%, MEM=90%, RT=3000ms, ERR=15%)
        Instant t12 = baseTime.plus(Duration.ofMinutes(11));
        Optional<AnomalyEvent> resA = enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 95.0, "PERCENT", t12));
        Optional<AnomalyEvent> resB = enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 90.0, "PERCENT", t12));
        Optional<AnomalyEvent> resC = enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 3000.0, "MILLISECONDS", t12));
        Optional<AnomalyEvent> resD = enabledOrchestrator.processMetric(createMetric(sId, "ERROR_RATE", 15.0, "PERCENT", t12));

        Optional<AnomalyEvent> anomalyRes = resA.or(() -> resB).or(() -> resC).or(() -> resD);

        // Multivariate anomaly is detected and persisted!
        assertTrue(anomalyRes.isPresent());
        assertEquals(999L, anomalyRes.get().getId());
        verify(persister, times(1)).persistMultivariateAnomaly(any(), any(), any(), any());

        // Invariant: Anomalous vector is NOT added to confirmed baseline!
        assertEquals(11, baselineManager.getConfirmedObservationCount(sId));
    }

    @Test
    void duplicateObservationsDuringWarmup_DoNotCountTwiceInOrchestrator() {
        Long sId = 105L;
        Instant t = Instant.now().minus(Duration.ofMinutes(5));

        // Ingest complete 4D vector at timestamp t
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 40.0, "PERCENT", t));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.0, "PERCENT", t));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 100.0, "MILLISECONDS", t));
        enabledOrchestrator.processMetric(createMetric(sId, "ERROR_RATE", 0.2, "PERCENT", t));

        assertEquals(1, baselineManager.getWarmupObservationCount(sId));

        // Ingest duplicate complete 4D vector at exact same timestamp t
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 40.0, "PERCENT", t));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.0, "PERCENT", t));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 100.0, "MILLISECONDS", t));
        enabledOrchestrator.processMetric(createMetric(sId, "ERROR_RATE", 0.2, "PERCENT", t));

        // Invariant: Duplicate must NOT increment sample count
        assertEquals(1, baselineManager.getWarmupObservationCount(sId));
    }

    @Test
    void warmupOutlierObservation_InOrchestrator_ScreenedAndNotPromotedToBaseline() {
        Long sId = 106L;
        Instant baseTime = Instant.now().minus(Duration.ofMinutes(20));

        // 1. Ingest 9 normal 4D vectors through orchestrator
        for (int i = 0; i < 9; i++) {
            Instant t = baseTime.plus(Duration.ofMinutes(i));
            enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 40.0 + (i % 3), "PERCENT", t));
            enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 50.0 + (i % 2), "PERCENT", t));
            enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 100.0 + (i % 4) * 2.0, "MILLISECONDS", t));
            Optional<AnomalyEvent> res = enabledOrchestrator.processMetric(
                createMetric(sId, "ERROR_RATE", 0.2 + (i % 2) * 0.05, "PERCENT", t));

            assertTrue(res.isEmpty());
            assertEquals(i + 1, baselineManager.getWarmupObservationCount(sId));
            assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(sId).status());
        }

        // 2. Ingest 10th observation as an extreme abnormal spike (all 4 dimensions advancing to form complete spike vector)
        Instant tSpike = baseTime.plus(Duration.ofMinutes(9));
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 95.0, "PERCENT", tSpike.plusSeconds(1)));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 90.0, "PERCENT", tSpike.plusSeconds(2)));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 3500.0, "MILLISECONDS", tSpike.plusSeconds(3)));
        Optional<AnomalyEvent> resSpike = enabledOrchestrator.processMetric(
            createMetric(sId, "ERROR_RATE", 15.0, "PERCENT", tSpike.plusSeconds(4)));

        // Invariant: Spurious vector in warm-up must not emit anomaly event before calibration
        assertTrue(resSpike.isEmpty());
        // And must be screened out: warm-up count drops back to 9 clean samples, confirmed count remains 0!
        assertEquals(9, baselineManager.getWarmupObservationCount(sId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(sId));
        assertEquals(CalibrationStatus.COLLECTING, baselineManager.getCalibrationState(sId).status());

        // 3. Ingest clean 10th observation (all 4 dimensions advancing to form complete clean vector)
        Instant tClean10 = baseTime.plus(Duration.ofMinutes(10));
        enabledOrchestrator.processMetric(createMetric(sId, "CPU_USAGE", 41.0, "PERCENT", tClean10.plusSeconds(1)));
        enabledOrchestrator.processMetric(createMetric(sId, "MEMORY_USAGE", 51.0, "PERCENT", tClean10.plusSeconds(2)));
        enabledOrchestrator.processMetric(createMetric(sId, "RESPONSE_TIME", 102.0, "MILLISECONDS", tClean10.plusSeconds(3)));
        Optional<AnomalyEvent> resClean10 = enabledOrchestrator.processMetric(
            createMetric(sId, "ERROR_RATE", 0.22, "PERCENT", tClean10.plusSeconds(4)));

        assertTrue(resClean10.isEmpty());
        // Service now auto-calibrates to READY with the 10 verified clean observations!
        assertEquals(CalibrationStatus.READY, baselineManager.getCalibrationState(sId).status());
        assertEquals(0, baselineManager.getWarmupObservationCount(sId));
        assertEquals(10, baselineManager.getConfirmedObservationCount(sId));
    }
}
