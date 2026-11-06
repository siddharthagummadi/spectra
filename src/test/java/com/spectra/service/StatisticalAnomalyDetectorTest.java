package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectra.dto.DetectionResult;

class StatisticalAnomalyDetectorTest {

    private StatisticalAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new StatisticalAnomalyDetector();
    }

    private List<Double> createBaseline(int count, double value) {
        List<Double> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(value);
        }
        return list;
    }

    @Test
    void detect_InsufficientHistory_ReturnsSkipped() {
        // Less than 10 observations
        List<Double> history9 = createBaseline(9, 50.0);
        DetectionResult result9 = detector.detect(55.0, "PERCENT", history9);
        assertFalse(result9.isAnomaly());
        assertTrue(result9.message().contains("Insufficient"));

        // Empty history
        DetectionResult result0 = detector.detect(55.0, "PERCENT", Collections.emptyList());
        assertFalse(result0.isAnomaly());
        assertTrue(result0.message().contains("Insufficient"));

        // Null history
        DetectionResult resultNull = detector.detect(55.0, "PERCENT", null);
        assertFalse(resultNull.isAnomaly());
        assertTrue(resultNull.message().contains("Insufficient"));
    }

    @Test
    void detect_MinimumHistory10_EvaluatedSuccessfully() {
        // Exactly 10 observations with mean=50, stddev=2.0
        // Values: 48, 52, 48, 52, 48, 52, 48, 52, 48, 52 (mean = 50.0, variance = 40/9 ≈ 4.444, s ≈ 2.108)
        List<Double> history10 = List.of(48.0, 52.0, 48.0, 52.0, 48.0, 52.0, 48.0, 52.0, 48.0, 52.0);
        DetectionResult normalResult = detector.detect(51.0, "PERCENT", history10);
        assertFalse(normalResult.isAnomaly());
        assertEquals(50.0, normalResult.baselineMean(), 0.001);
    }

    @Test
    void detect_NormalObservation_WithinExpectedBaseline() {
        // Baseline with mean = 100.0, standard deviation ≈ 10.0
        // N = 10: 90, 110, 95, 105, 90, 110, 95, 105, 100, 100
        List<Double> history = List.of(90.0, 110.0, 95.0, 105.0, 90.0, 110.0, 95.0, 105.0, 100.0, 100.0);
        DetectionResult result = detector.detect(115.0, "MILLISECONDS", history);
        assertFalse(result.isAnomaly());
        assertTrue(Math.abs(result.anomalyScore()) < 3.0);
    }

    @Test
    void detect_PositiveSpike_MediumSeverity() {
        // 10 identical values of 50.0 with PERCENT unit -> varianceFloor = 1.0, mean = 50.0
        List<Double> history = createBaseline(10, 50.0);
        // Value = 53.5 -> Z = (53.5 - 50.0) / 1.0 = 3.5 (Medium: 3.0 <= |Z| < 4.0)
        DetectionResult result = detector.detect(53.5, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(3.5, result.anomalyScore(), 0.001);
        assertEquals("MEDIUM", result.severity());
        assertEquals("POSITIVE_SPIKE", result.direction());
        assertEquals(50.0, result.baselineMean(), 0.001);
        assertEquals(1.0, result.baselineStdDev(), 0.001);
    }

    @Test
    void detect_PositiveSpike_HighSeverity() {
        // Mean = 50.0, effective std dev = 1.0
        List<Double> history = createBaseline(10, 50.0);
        // Value = 54.5 -> Z = 4.5 (High: 4.0 <= |Z| < 5.0)
        DetectionResult result = detector.detect(54.5, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(4.5, result.anomalyScore(), 0.001);
        assertEquals("HIGH", result.severity());
        assertEquals("POSITIVE_SPIKE", result.direction());
    }

    @Test
    void detect_PositiveSpike_CriticalSeverity() {
        // Mean = 50.0, effective std dev = 1.0
        List<Double> history = createBaseline(10, 50.0);
        // Value = 56.0 -> Z = 6.0 (Critical: |Z| >= 5.0)
        DetectionResult result = detector.detect(56.0, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(6.0, result.anomalyScore(), 0.001);
        assertEquals("CRITICAL", result.severity());
        assertEquals("POSITIVE_SPIKE", result.direction());
    }

    @Test
    void detect_NegativeDrop_MediumSeverity() {
        // Mean = 50.0, effective std dev = 1.0
        List<Double> history = createBaseline(10, 50.0);
        // Value = 46.5 -> Z = (46.5 - 50.0) / 1.0 = -3.5 (Medium: 3.0 <= |Z| < 4.0)
        DetectionResult result = detector.detect(46.5, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(-3.5, result.anomalyScore(), 0.001);
        assertEquals("MEDIUM", result.severity());
        assertEquals("NEGATIVE_DROP", result.direction());
    }

    @Test
    void detect_NegativeDrop_HighSeverity() {
        List<Double> history = createBaseline(10, 50.0);
        // Value = 45.5 -> Z = -4.5 (High: 4.0 <= |Z| < 5.0)
        DetectionResult result = detector.detect(45.5, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(-4.5, result.anomalyScore(), 0.001);
        assertEquals("HIGH", result.severity());
        assertEquals("NEGATIVE_DROP", result.direction());
    }

    @Test
    void detect_NegativeDrop_CriticalSeverity() {
        List<Double> history = createBaseline(10, 50.0);
        // Value = 44.0 -> Z = -6.0 (Critical: |Z| >= 5.0)
        DetectionResult result = detector.detect(44.0, "PERCENT", history);

        assertTrue(result.isAnomaly());
        assertEquals(-6.0, result.anomalyScore(), 0.001);
        assertEquals("CRITICAL", result.severity());
        assertEquals("NEGATIVE_DROP", result.direction());
    }

    @Test
    void detect_ZeroVariance_AvoidsDivisionByZeroUsingVarianceFloor() {
        List<Double> history = createBaseline(15, 100.0); // sample stddev = 0.0
        // Unit MILLISECONDS -> floor = 5.0
        DetectionResult result = detector.detect(100.0, "MILLISECONDS", history);
        assertFalse(result.isAnomaly());
        assertEquals(0.0, result.anomalyScore(), 0.001);
        assertEquals(5.0, result.baselineStdDev(), 0.001); // effective stddev is floor
    }

    @Test
    void detect_NearZeroVariance_FloorPreventsNoiseExplosion() {
        // History with tiny fluctuations (mean = 100.0, tiny stddev ≈ 0.0001)
        List<Double> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            history.add(100.0 + (i % 2 == 0 ? 0.0001 : -0.0001));
        }
        // Current value with slight delta of 0.01 (should NOT trigger anomaly due to 5.0ms floor)
        DetectionResult result = detector.detect(100.01, "MILLISECONDS", history);
        assertFalse(result.isAnomaly());
        assertTrue(result.baselineStdDev() >= 5.0);
    }

    @Test
    void detect_VarianceFloorPolicies_ByUnit() {
        assertEquals(1.0, detector.resolveVarianceFloor("PERCENT"));
        assertEquals(1.0, detector.resolveVarianceFloor("percent"));
        assertEquals(5.0, detector.resolveVarianceFloor("MILLISECONDS"));
        assertEquals(5.0, detector.resolveVarianceFloor("milliseconds"));
        assertEquals(10.0, detector.resolveVarianceFloor("MEGABYTES"));
        assertEquals(10.0, detector.resolveVarianceFloor("megabytes"));
        assertEquals(1.0, detector.resolveVarianceFloor("UNKNOWN"));
        assertEquals(1.0, detector.resolveVarianceFloor(null));
    }

    @Test
    void detect_VarianceFloorPolicies_MetricAndUnitAware() {
        // ERROR_RATE gets 0.1 percentage point floor strictly when unit is PERCENT
        assertEquals(0.1, detector.resolveVarianceFloor("ERROR_RATE", "PERCENT"), 0.001);
        assertEquals(0.1, detector.resolveVarianceFloor("error_rate", "percent"), 0.001);
        assertEquals(1.0, detector.resolveVarianceFloor("ERROR_RATE", "UNKNOWN_UNIT"), 0.001);
        assertEquals(1.0, detector.resolveVarianceFloor("ERROR_RATE", null), 0.001);

        // CPU_USAGE with PERCENT gets standard 1.0 floor
        assertEquals(1.0, detector.resolveVarianceFloor("CPU_USAGE", "PERCENT"), 0.001);

        // MEMORY_USAGE with PERCENT gets 1.0 floor, with MEGABYTES gets 10.0 floor
        assertEquals(1.0, detector.resolveVarianceFloor("MEMORY_USAGE", "PERCENT"), 0.001);
        assertEquals(10.0, detector.resolveVarianceFloor("MEMORY_USAGE", "MEGABYTES"), 0.001);

        // RESPONSE_TIME with MILLISECONDS gets 5.0 floor
        assertEquals(5.0, detector.resolveVarianceFloor("RESPONSE_TIME", "MILLISECONDS"), 0.001);

        // Fallbacks
        assertEquals(1.0, detector.resolveVarianceFloor("UNKNOWN_METRIC", "UNKNOWN_UNIT"), 0.001);
        assertEquals(1.0, detector.resolveVarianceFloor(null, null), 0.001);
    }

    @Test
    void detect_ErrorRate_FractionalBaseline_NoFalsePositiveOnMinorFluctuations() {
        // Flat baseline of 0.05% error rate
        List<Double> history = createBaseline(10, 0.05);

        // Fluctuation to 0.08% is only delta = +0.03%, well below 3 * 0.1 = 0.30%
        DetectionResult result = detector.detect("ERROR_RATE", 0.08, "PERCENT", history);
        assertFalse(result.isAnomaly(), "0.08% against 0.05% baseline should not be flagged as anomaly");
        assertEquals(0.1, result.baselineStdDev(), 0.001);
        assertEquals(0.3, result.anomalyScore(), 0.001);
    }

    @Test
    void detect_ErrorRate_FractionalBaseline_DetectsRealSpikes() {
        // Flat baseline of 0.05% error rate
        List<Double> history = createBaseline(10, 0.05);

        // 1. Medium spike: 0.40% -> delta = +0.35% -> Z = 3.5
        DetectionResult mediumResult = detector.detect("ERROR_RATE", 0.40, "PERCENT", history);
        assertTrue(mediumResult.isAnomaly());
        assertEquals("MEDIUM", mediumResult.severity());
        assertEquals("POSITIVE_SPIKE", mediumResult.direction());
        assertEquals(3.5, mediumResult.anomalyScore(), 0.001);

        // 2. Critical spike: 0.60% -> delta = +0.55% -> Z = 5.5
        DetectionResult critResult = detector.detect("ERROR_RATE", 0.60, "PERCENT", history);
        assertTrue(critResult.isAnomaly());
        assertEquals("CRITICAL", critResult.severity());
        assertEquals(5.5, critResult.anomalyScore(), 0.001);
    }

    @Test
    void detect_ResponseTime_MillisecondsVarianceFloorBehavior() {
        // Flat baseline of 120.0ms
        List<Double> history = createBaseline(10, 120.0);

        // Delta of +4ms is below floor of 5ms (Z = 0.8) -> normal
        DetectionResult normalResult = detector.detect("RESPONSE_TIME", 124.0, "MILLISECONDS", history);
        assertFalse(normalResult.isAnomaly());
        assertEquals(5.0, normalResult.baselineStdDev(), 0.001);

        // Delta of +16ms -> Z = 16.0 / 5.0 = 3.2 -> MEDIUM anomaly
        DetectionResult spikeResult = detector.detect("RESPONSE_TIME", 136.0, "MILLISECONDS", history);
        assertTrue(spikeResult.isAnomaly());
        assertEquals("MEDIUM", spikeResult.severity());
        assertEquals(3.2, spikeResult.anomalyScore(), 0.001);
    }

    @Test
    void detect_MemoryUsage_MegabytesVarianceFloorBehavior() {
        // Flat baseline of 512.0 MB
        List<Double> history = createBaseline(10, 512.0);

        // Delta of +5MB is below 10MB floor -> normal
        DetectionResult normalResult = detector.detect("MEMORY_USAGE", 517.0, "MEGABYTES", history);
        assertFalse(normalResult.isAnomaly());
        assertEquals(10.0, normalResult.baselineStdDev(), 0.001);

        // Delta of +35MB -> Z = 35.0 / 10.0 = 3.5 -> MEDIUM anomaly
        DetectionResult spikeResult = detector.detect("MEMORY_USAGE", 547.0, "MEGABYTES", history);
        assertTrue(spikeResult.isAnomaly());
        assertEquals("MEDIUM", spikeResult.severity());
        assertEquals(3.5, spikeResult.anomalyScore(), 0.001);
    }

    @Test
    void detect_WindowSize_BoundedToMostRecent30() {
        // Create 40 values: first 30 are 100.0, older 10 are 0.0
        // Because window limits to 30, baseline should only include the first 30 (mean = 100.0)
        List<Double> history40 = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            history40.add(100.0);
        }
        for (int i = 0; i < 10; i++) {
            history40.add(0.0);
        }

        DetectionResult result = detector.detect(100.0, "PERCENT", history40);
        assertNotNull(result);
        assertEquals(100.0, result.baselineMean(), 0.001);
    }

    @Test
    void detect_InvalidCurrentValues_ThrowsException() {
        List<Double> history = createBaseline(10, 50.0);

        assertThrows(IllegalArgumentException.class, () -> detector.detect(null, "PERCENT", history));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(Double.NaN, "PERCENT", history));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(Double.POSITIVE_INFINITY, "PERCENT", history));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(Double.NEGATIVE_INFINITY, "PERCENT", history));
    }

    @Test
    void detect_HistoryWithInvalidValues_FiltersInvalidGracefully() {
        List<Double> historyWithNans = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            historyWithNans.add(50.0);
        }
        historyWithNans.add(Double.NaN); // 9 valid + 1 NaN = 9 valid -> insufficient!
        DetectionResult result = detector.detect(55.0, "PERCENT", historyWithNans);
        assertFalse(result.isAnomaly());
        assertTrue(result.message().contains("Insufficient"));
    }
}
