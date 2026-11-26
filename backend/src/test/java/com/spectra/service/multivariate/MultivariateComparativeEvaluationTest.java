package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spectra.dto.DetectionResult;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.service.StatisticalAnomalyDetector;

/**
 * End-to-End Comparative Evaluation Test Suite for Spectra Stage 2.3.1 - Task 5.
 * <p>
 * Executes comparative evaluation between:
 * <ul>
 *   <li><b>Approach A:</b> Existing Univariate Statistical Detector (StatisticalAnomalyDetector, rolling 30-sample window, 3.0-sigma threshold, domain variance floors)</li>
 *   <li><b>Approach B:</b> Java Multivariate Mahalanobis Anomaly Detector (JavaMultivariateAnomalyDetector, 4D domain-floor Mahalanobis distance, Ledoit-Wolf shrinkage, 3.644 floor)</li>
 * </ul>
 * Across 6 operational scenarios:
 * <ol>
 *   <li>Normal operating behaviour (Steady-state traffic)</li>
 *   <li>Sudden CPU spike</li>
 *   <li>Gradual memory leak</li>
 *   <li>Response-time degradation</li>
 *   <li>Error-rate increase</li>
 *   <li>Correlated anomalies involving multiple metrics (Sub-threshold joint shifts & broken correlation)</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
public class MultivariateComparativeEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(MultivariateComparativeEvaluationTest.class);

    @Autowired
    private JavaMultivariateAnomalyDetector multivariateDetector;

    @Autowired
    private StatisticalAnomalyDetector univariateDetector;

    @Autowired
    private MultivariateAnomalyEventPersister eventPersister;

    @Autowired
    private AnomalyEventRepository anomalyEventRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MultivariateBaselineManager baselineManager;
    private MonitoredService evalService;

    public record LabeledObservation(
        long sequenceId,
        MultivariateFeatureVector vector,
        String scenarioName,
        boolean groundTruthAnomaly
    ) {}

    public static class ConfusionMatrix {
        public int tp = 0;
        public int fp = 0;
        public int tn = 0;
        public int fn = 0;

        public void record(boolean groundTruth, boolean predicted) {
            if (groundTruth && predicted) tp++;
            else if (!groundTruth && predicted) fp++;
            else if (!groundTruth && !predicted) tn++;
            else if (groundTruth && !predicted) fn++;
        }

        public double accuracy() {
            int total = tp + fp + tn + fn;
            return total == 0 ? 0.0 : (double) (tp + tn) / total;
        }

        public double precision() {
            int denom = tp + fp;
            return denom == 0 ? 1.0 : (double) tp / denom;
        }

        public double recall() {
            int denom = tp + fn;
            return denom == 0 ? 1.0 : (double) tp / denom;
        }

        public double f1Score() {
            double p = precision();
            double r = recall();
            return (p + r == 0.0) ? 0.0 : 2.0 * (p * r) / (p + r);
        }

        public double falsePositiveRate() {
            int denom = fp + tn;
            return denom == 0 ? 0.0 : (double) fp / denom;
        }

        public double detectionRate() {
            return recall();
        }

        public double specificity() {
            return 1.0 - falsePositiveRate();
        }
    }

    @BeforeEach
    void setUp() {
        baselineManager = new MultivariateBaselineManager(multivariateDetector, 300, Duration.ofHours(24));

        evalService = new MonitoredService();
        evalService.setName("Eval-Payment-Service-" + System.nanoTime());
        evalService.setDescription("Service for Task 5 Evaluation");
        evalService.setEndpointUrl("http://localhost:8089");
        evalService.setEnvironment("TEST");
        evalService.setActive(true);
        evalService = serviceRepository.save(evalService);
    }

    /**
     * Generates a statistically rigorous, reproducible 1,200-sample benchmark dataset
     * covering all 6 mandated operational scenarios with known ground truth labels.
     */
    private List<LabeledObservation> generateComparativeDataset(Instant baseTime) {
        List<LabeledObservation> dataset = new ArrayList<>();
        Random rng = new Random(42); // Deterministic seed for exact reproducibility

        long seq = 0;
        Instant curTime = baseTime;

        // Normal operating parameters:
        // CPU: mean=40.0%, std=3.0%
        // Memory: mean=50.0%, std=2.5%
        // Response time: mean=100.0ms, std=8.0ms (correlated with CPU: rho=0.70)
        // Error rate: mean=0.20%, std=0.04%

        // Phase 1: Initial Steady-State Normal Traffic (400 observations)
        for (int i = 0; i < 400; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2); // rho = 0.70
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_1_STEADY_STATE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 2: Sudden CPU Spike (50 observations)
        for (int i = 0; i < 50; i++) {
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 88.0 + rng.nextDouble() * 10.0; // Extreme spike: 88-98%
            double rt = 105.0 + 8.0 * u2;
            double mem = 52.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_2_SUDDEN_CPU_SPIKE", true));
            curTime = curTime.plusSeconds(15);
        }

        // Return-to-normal interlude (100 observations)
        for (int i = 0; i < 100; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "INTERLUDE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 3: Gradual Memory Leak (80 observations)
        // Observations 0-20: slight drift (50 -> 65%, ground truth false/transitional)
        // Observations 20-80: severe memory leak (68 -> 96%, ground truth true)
        for (int i = 0; i < 80; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double leakProgress = (double) i / 80.0;
            double mem = 50.0 + leakProgress * 45.0 + rng.nextGaussian() * 1.0;
            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            boolean isAnomaly = i >= 20; // past 62% is abnormal
            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_3_GRADUAL_MEMORY_LEAK", isAnomaly));
            curTime = curTime.plusSeconds(15);
        }

        // Return-to-normal interlude (100 observations)
        for (int i = 0; i < 100; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "INTERLUDE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 4: Response-Time Degradation (60 observations)
        for (int i = 0; i < 60; i++) {
            double u1 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 38.0 + 3.0 * u1;
            double rt = 850.0 + rng.nextDouble() * 1800.0; // severe latency: 850ms - 2650ms
            double mem = 51.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_4_RESPONSE_TIME_DEGRADATION", true));
            curTime = curTime.plusSeconds(15);
        }

        // Return-to-normal interlude (100 observations)
        for (int i = 0; i < 100; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "INTERLUDE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 5: Error-Rate Surge (50 observations)
        for (int i = 0; i < 50; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();

            double cpu = 41.0 + 3.0 * u1;
            double rt = 105.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 51.0 + 2.5 * u3;
            double err = 6.0 + rng.nextDouble() * 18.0; // Error rate 6% - 24%

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_5_ERROR_RATE_SURGE", true));
            curTime = curTime.plusSeconds(15);
        }

        // Return-to-normal interlude (100 observations)
        for (int i = 0; i < 100; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "INTERLUDE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 6A: Correlated Sub-Threshold Joint Drift (50 observations)
        // Each individual metric is elevated by 2.4 to 2.5 sigma (below univariate 3.0 cutoff)
        // But the joint 4D quadratic distance D_M strongly breaches the threshold (D_M ~ 4.46 > 3.644).
        for (int i = 0; i < 50; i++) {
            double cpu = 48.0 + rng.nextGaussian() * 0.15; // Z = +2.67 sigma (normal 40.0, std 3.0) < 3.0
            double mem = 56.8 + rng.nextGaussian() * 0.15; // Z = +2.72 sigma (normal 50.0, std 2.5) < 3.0
            double rt = 121.5 + rng.nextGaussian() * 0.40;  // Z = +2.69 sigma (normal 100.0, std 8.0) < 3.0
            double err = 0.31 + rng.nextGaussian() * 0.005; // Z = +1.10 sigma on floor (normal 0.20, floor 0.1) < 3.0

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_6A_SUB_THRESHOLD_JOINT_DRIFT", true));
            curTime = curTime.plusSeconds(15);
        }

        // Phase 6B: Broken Correlation Anomaly (50 observations)
        // CPU is uncharacteristically low (33.1%, -2.3 sigma) while Response Time is elevated (118.4ms, +2.3 sigma)
        // Neither breaches 3.0 sigma individually, but joint correlation is violated (D_M ~ 5.94 > 3.644)!
        for (int i = 0; i < 50; i++) {
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 33.1 + rng.nextGaussian() * 0.15;  // -2.30 sigma
            double rt = 118.4 + rng.nextGaussian() * 0.40;   // +2.30 sigma (violates rho=0.7 positive correlation)
            double mem = 50.0 + 1.0 * u3;
            double err = Math.max(0.01, 0.20 + 0.02 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "SCENARIO_6B_BROKEN_CORRELATION", true));
            curTime = curTime.plusSeconds(15);
        }

        // Return-to-normal final interlude (60 observations)
        for (int i = 0; i < 60; i++) {
            double u1 = rng.nextGaussian();
            double u2 = rng.nextGaussian();
            double u3 = rng.nextGaussian();
            double u4 = rng.nextGaussian();

            double cpu = 40.0 + 3.0 * u1;
            double rt = 100.0 + 8.0 * (0.70 * u1 + 0.714 * u2);
            double mem = 50.0 + 2.5 * u3;
            double err = Math.max(0.01, 0.20 + 0.04 * u4);

            MultivariateFeatureVector vec = MultivariateFeatureVector.of(cpu, mem, rt, err, curTime);
            dataset.add(new LabeledObservation(seq++, vec, "INTERLUDE_NORMAL", false));
            curTime = curTime.plusSeconds(15);
        }

        return dataset;
    }

    @Test
    @DisplayName("Task 5.1: Comparative Experiment - Univariate vs Multivariate Anomaly Detection across 6 Scenarios")
    void testComparativeEvaluation_AcrossSixScenarios() {
        Instant startTime = Instant.now().minus(Duration.ofHours(5));
        List<LabeledObservation> dataset = generateComparativeDataset(startTime);
        assertEquals(1200, dataset.size(), "Dataset must contain exactly 1,200 aligned observations");

        // 1. Initial Warm-Up Phase: First 20 normal observations calibrate the multivariate baseline
        List<MultivariateFeatureVector> warmupVectors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            warmupVectors.add(dataset.get(i).vector());
        }
        MultivariateCalibrationModel calModel = multivariateDetector.calibrate(evalService.getId(), warmupVectors);
        assertNotNull(calModel);
        assertTrue(calModel.getAnomalyThreshold() >= JavaMultivariateAnomalyDetector.MIN_CHI_SQUARE_THRESHOLD);

        // 2. Set up calibrated baseline history buffers for the Univariate Detector (clean baseline evaluation)
        List<Double> cleanCpuHistory = new ArrayList<>();
        List<Double> cleanMemHistory = new ArrayList<>();
        List<Double> cleanRtHistory = new ArrayList<>();
        List<Double> cleanErrHistory = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            cleanCpuHistory.add(dataset.get(i).vector().getCpuUsage());
            cleanMemHistory.add(dataset.get(i).vector().getMemoryUsage());
            cleanRtHistory.add(dataset.get(i).vector().getResponseTime());
            cleanErrHistory.add(dataset.get(i).vector().getErrorRate());
        }

        // Confusion matrices
        ConfusionMatrix cmUnivariate = new ConfusionMatrix();
        ConfusionMatrix cmMultivariate = new ConfusionMatrix();

        // Scenario-wise stats
        Map<String, ConfusionMatrix> scenarioCmUnivariate = new HashMap<>();
        Map<String, ConfusionMatrix> scenarioCmMultivariate = new HashMap<>();

        int totalObservations = dataset.size() - 20; // 1,180 evaluated post-warmup

        for (int i = 20; i < dataset.size(); i++) {
            LabeledObservation obs = dataset.get(i);
            MultivariateFeatureVector vec = obs.vector();
            boolean groundTruth = obs.groundTruthAnomaly();
            String scenario = obs.scenarioName();

            scenarioCmUnivariate.computeIfAbsent(scenario, k -> new ConfusionMatrix());
            scenarioCmMultivariate.computeIfAbsent(scenario, k -> new ConfusionMatrix());

            // --- A. Evaluate Univariate Detector against calibrated baseline ---
            DetectionResult cpuRes = univariateDetector.detect("CPU_USAGE", vec.getCpuUsage(), "PERCENT", cleanCpuHistory);
            DetectionResult memRes = univariateDetector.detect("MEMORY_USAGE", vec.getMemoryUsage(), "PERCENT", cleanMemHistory);
            DetectionResult rtRes = univariateDetector.detect("RESPONSE_TIME", vec.getResponseTime(), "MILLISECONDS", cleanRtHistory);
            DetectionResult errRes = univariateDetector.detect("ERROR_RATE", vec.getErrorRate(), "PERCENT", cleanErrHistory);

            boolean uniPredicted = cpuRes.isAnomaly() || memRes.isAnomaly() || rtRes.isAnomaly() || errRes.isAnomaly();
            cmUnivariate.record(groundTruth, uniPredicted);
            scenarioCmUnivariate.get(scenario).record(groundTruth, uniPredicted);

            // --- B. Evaluate Multivariate Mahalanobis Detector against calibrated model ---
            MultivariateDetectionResult mvtRes = multivariateDetector.detect(vec, calModel);
            boolean mvtPredicted = mvtRes.isAnomaly();
            cmMultivariate.record(groundTruth, mvtPredicted);
            scenarioCmMultivariate.get(scenario).record(groundTruth, mvtPredicted);
        }

        // Output and assert metrics
        log.info("==========================================================================================");
        log.info("          SPECTRA ANOMALY DETECTION COMPARATIVE BENCHMARK (1,180 OBSERVATIONS)            ");
        log.info("==========================================================================================");
        log.info("Metric                    | Univariate (Z-Score/MAD)     | Multivariate (Mahalanobis 4D) ");
        log.info("--------------------------+------------------------------+--------------------------------");
        log.info(String.format("Accuracy                  | %26.2f%% | %28.2f%%", cmUnivariate.accuracy() * 100, cmMultivariate.accuracy() * 100));
        log.info(String.format("Precision                 | %26.2f%% | %28.2f%%", cmUnivariate.precision() * 100, cmMultivariate.precision() * 100));
        log.info(String.format("Recall                    | %26.2f%% | %28.2f%%", cmUnivariate.recall() * 100, cmMultivariate.recall() * 100));
        log.info(String.format("F1-Score                  | %26.4f  | %28.4f ", cmUnivariate.f1Score(), cmMultivariate.f1Score()));
        log.info(String.format("Specificity               | %26.2f%% | %28.2f%%", cmUnivariate.specificity() * 100, cmMultivariate.specificity() * 100));
        log.info(String.format("False Positive Rate (FPR) | %26.2f%% | %28.2f%%", cmUnivariate.falsePositiveRate() * 100, cmMultivariate.falsePositiveRate() * 100));
        log.info(String.format("True Positives (TP)       | %28d | %30d", cmUnivariate.tp, cmMultivariate.tp));
        log.info(String.format("False Positives (FP)      | %28d | %30d", cmUnivariate.fp, cmMultivariate.fp));
        log.info(String.format("True Negatives (TN)       | %28d | %30d", cmUnivariate.tn, cmMultivariate.tn));
        log.info(String.format("False Negatives (FN)      | %28d | %30d", cmUnivariate.fn, cmMultivariate.fn));
        log.info("==========================================================================================");

        log.info("------------------------------------------------------------------------------------------");
        log.info("                         SCENARIO-WISE DETECTION BREAKDOWN                                ");
        log.info("------------------------------------------------------------------------------------------");
        for (String scen : scenarioCmUnivariate.keySet()) {
            ConfusionMatrix u = scenarioCmUnivariate.get(scen);
            ConfusionMatrix m = scenarioCmMultivariate.get(scen);
            int total = u.tp + u.fp + u.tn + u.fn;
            int groundTruthAnomalies = u.tp + u.fn;
            log.info(String.format("Scenario: %-36s (N=%4d, Anomalies=%3d)", scen, total, groundTruthAnomalies));
            if (groundTruthAnomalies > 0) {
                log.info(String.format("   Univariate Recall   : %5.1f%% (Detected %d/%d, FN=%d)",
                    u.recall() * 100, u.tp, groundTruthAnomalies, u.fn));
                log.info(String.format("   Multivariate Recall : %5.1f%% (Detected %d/%d, FN=%d)",
                    m.recall() * 100, m.tp, groundTruthAnomalies, m.fn));
            } else {
                log.info(String.format("   Univariate FP       : %d (FPR=%5.2f%%)", u.fp, u.falsePositiveRate() * 100));
                log.info(String.format("   Multivariate FP     : %d (FPR=%5.2f%%)", m.fp, m.falsePositiveRate() * 100));
            }
        }
        log.info("==========================================================================================");

        // Core Mathematical Verifications:
        // 1. Both detectors achieve very high recall on obvious single-metric spikes (Scenarios 2, 4, 5)
        ConfusionMatrix uCpu = scenarioCmUnivariate.get("SCENARIO_2_SUDDEN_CPU_SPIKE");
        ConfusionMatrix mCpu = scenarioCmMultivariate.get("SCENARIO_2_SUDDEN_CPU_SPIKE");
        assertTrue(uCpu.recall() >= 0.95, "Univariate should easily detect sudden CPU spikes");
        assertTrue(mCpu.recall() >= 0.95, "Multivariate should easily detect sudden CPU spikes");

        ConfusionMatrix uRt = scenarioCmUnivariate.get("SCENARIO_4_RESPONSE_TIME_DEGRADATION");
        ConfusionMatrix mRt = scenarioCmMultivariate.get("SCENARIO_4_RESPONSE_TIME_DEGRADATION");
        assertTrue(uRt.recall() >= 0.95, "Univariate should easily detect extreme latency degradation");
        assertTrue(mRt.recall() >= 0.95, "Multivariate should easily detect extreme latency degradation");

        // 2. Correlated anomalies (Scenario 6A and 6B):
        // Univariate FAILS (recall near 0%) because each individual dimension is below 3.0 sigma.
        // Multivariate SUCCEEDS (recall > 90%) because joint covariance distance D_M captures the coupled deviation.
        ConfusionMatrix uSub = scenarioCmUnivariate.get("SCENARIO_6A_SUB_THRESHOLD_JOINT_DRIFT");
        ConfusionMatrix mSub = scenarioCmMultivariate.get("SCENARIO_6A_SUB_THRESHOLD_JOINT_DRIFT");
        assertTrue(uSub.recall() < 0.20, "Univariate detector MUST fail on sub-threshold joint drift (Z < 3.0 individually)");
        assertTrue(mSub.recall() > 0.85, "Multivariate detector MUST detect sub-threshold joint drift via quadratic form D_M");

        ConfusionMatrix uBroken = scenarioCmUnivariate.get("SCENARIO_6B_BROKEN_CORRELATION");
        ConfusionMatrix mBroken = scenarioCmMultivariate.get("SCENARIO_6B_BROKEN_CORRELATION");
        assertTrue(uBroken.recall() < 0.20, "Univariate detector MUST fail on broken correlation (neither metric exceeds 3.0 sigma)");
        assertTrue(mBroken.recall() > 0.85, "Multivariate detector MUST detect broken correlation via off-diagonal precision coupling");

        // 3. Overall F1-Score: Multivariate is decisively superior
        assertTrue(cmMultivariate.f1Score() > cmUnivariate.f1Score(),
            "Multivariate F1 score must exceed Univariate F1 score due to capturing multi-metric correlations");

        // 4. Low false alarm rate on normal steady-state
        ConfusionMatrix mNormal = scenarioCmUnivariate.get("SCENARIO_1_STEADY_STATE_NORMAL");
        assertTrue(mNormal.falsePositiveRate() < 0.05, "False positive rate on normal steady-state must be under 5%");
    }

    @Test
    @DisplayName("Task 5.2: Complete End-to-End Workflow Verification - Ingestion to Persistence and Deduplication")
    void testEndToEndWorkflow_IngestionToPersistenceAndDeduplication() {
        Long serviceId = evalService.getId();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minus(Duration.ofMinutes(40));

        // 1. Initial State: UNINITIALIZED
        CalibrationState state0 = baselineManager.getCalibrationState(serviceId);
        assertEquals(CalibrationStatus.UNINITIALIZED, state0.status());

        // 2. Telemetry Ingestion & Warm-Up: Feed 9 clean observations -> state COLLECTING
        for (int i = 0; i < 9; i++) {
            MultivariateFeatureVector vec = MultivariateFeatureVector.of(
                40.0 + (i % 3), 50.0 + (i % 2), 100.0 + (i % 4) * 2.0, 0.2, now.plusSeconds(i * 15L)
            );
            CalibrationState s = baselineManager.recordWarmupObservation(serviceId, vec);
            assertEquals(CalibrationStatus.COLLECTING, s.status());
            assertEquals(i + 1, baselineManager.getWarmupObservationCount(serviceId));
        }

        // 3. Outlier Screening during Warm-Up:
        // Inject an extreme dirty outlier (CPU=95%, MEM=90%, RT=3500ms, ERR=15%)
        MultivariateFeatureVector dirtyOutlier = MultivariateFeatureVector.of(
            95.0, 90.0, 3500.0, 15.0, now.plusSeconds(9 * 15L)
        );
        CalibrationState stateAfterDirty = baselineManager.recordWarmupObservation(serviceId, dirtyOutlier);

        // Invariant: Dirty sample is screened and purged; state remains COLLECTING with 9 clean samples
        assertEquals(CalibrationStatus.COLLECTING, stateAfterDirty.status());
        assertFalse(stateAfterDirty.isReady());
        assertEquals(9, baselineManager.getWarmupObservationCount(serviceId));
        assertEquals(0, baselineManager.getConfirmedObservationCount(serviceId));

        // 4. Ingest 10th CLEAN observation -> Calibration succeeds -> READY!
        MultivariateFeatureVector clean10 = MultivariateFeatureVector.of(
            41.0, 51.0, 102.0, 0.21, now.plusSeconds(10 * 15L)
        );
        CalibrationState stateReady = baselineManager.recordWarmupObservation(serviceId, clean10);
        assertEquals(CalibrationStatus.READY, stateReady.status());
        assertTrue(stateReady.isReady());
        assertNotNull(stateReady.model());
        assertEquals(10, baselineManager.getConfirmedObservationCount(serviceId));
        assertEquals(0, baselineManager.getWarmupObservationCount(serviceId));
        assertTrue(stateReady.model().getAnomalyThreshold() >= JavaMultivariateAnomalyDetector.MIN_CHI_SQUARE_THRESHOLD);

        MultivariateCalibrationModel model = stateReady.model();

        // 5. Normal Observation Evaluation:
        // Feed normal observation -> classified NORMAL -> NO anomaly event persisted -> added to confirmed buffer
        MultivariateFeatureVector normalVec = MultivariateFeatureVector.of(
            40.5, 50.5, 101.0, 0.20, now.plusSeconds(11 * 15L)
        );
        MultivariateDetectionResult normalResult = multivariateDetector.detect(normalVec, model);
        assertFalse(normalResult.isAnomaly(), "Normal vector must not trigger anomaly");
        assertTrue(normalResult.mahalanobisDistance() <= normalResult.threshold());

        baselineManager.recordObservation(serviceId, normalVec, normalResult.isAnomaly());
        assertEquals(11, baselineManager.getConfirmedObservationCount(serviceId));

        // 6. Anomaly Observation Evaluation:
        // Feed severe anomaly vector -> classified ANOMALY
        Instant anomalyTime = now.plusSeconds(12 * 15L);
        MultivariateFeatureVector anomalyVec = MultivariateFeatureVector.of(
            92.0, 85.0, 2400.0, 12.0, anomalyTime
        );
        MultivariateDetectionResult anomalyResult = multivariateDetector.detect(anomalyVec, model);
        assertTrue(anomalyResult.isAnomaly(), "Abnormal vector must be detected as anomaly");
        assertTrue(anomalyResult.mahalanobisDistance() > anomalyResult.threshold());
        assertEquals(4, anomalyResult.featureContributions().size());

        // Baseline Protection Invariant: Anomalies are rejected from baseline buffer
        baselineManager.recordObservation(serviceId, anomalyVec, anomalyResult.isAnomaly());
        assertEquals(11, baselineManager.getConfirmedObservationCount(serviceId),
            "Confirmed baseline count must remain strictly 11 (anomaly was excluded)");

        // 7. Event Generation & Isolated Transaction Persistence:
        Optional<AnomalyEvent> persistedEvent = eventPersister.persistMultivariateAnomaly(
            evalService, anomalyVec, anomalyResult, model
        );
        assertTrue(persistedEvent.isPresent(), "AnomalyEvent must be persisted");
        AnomalyEvent event = persistedEvent.get();
        assertNotNull(event.getId());
        assertEquals("MULTIVARIATE_MAHALANOBIS", event.getMetricName());
        assertEquals(anomalyResult.mahalanobisDistance(), event.getObservedValue());
        assertEquals("D_M", event.getUnit());
        assertEquals("POSITIVE_SPIKE", event.getDirection());
        assertEquals("DETECTED", event.getStatus());
        assertTrue(event.getMessage().contains("[MULTIVARIATE_MAHALANOBIS]"));

        // 8. Deduplication / Idempotency Suppression:
        // Concurrent or repeated evaluation of the exact same event at identical timestamp
        Optional<AnomalyEvent> duplicateAttempt = eventPersister.persistMultivariateAnomaly(
            evalService, anomalyVec, anomalyResult, model
        );
        assertFalse(duplicateAttempt.isPresent(),
            "Duplicate multivariate anomaly at same timestamp must be suppressed cleanly (Optional.empty)");

        // Total events in repository for this timestamp must be strictly 1
        List<AnomalyEvent> events = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
            serviceId, org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertEquals(1, events.size(), "Only exactly 1 anomaly event should exist in the repository");
    }

    @Test
    @DisplayName("Task 5.3: Comprehensive End-to-End Evaluation of All Three Anomaly Detection Approaches across 4 Operational Scenarios")
    void testTask5_ComprehensiveThreeDetectorComparison() {
        Instant startTime = Instant.now().minus(Duration.ofHours(5));
        List<LabeledObservation> dataset = generateComparativeDataset(startTime);
        assertEquals(1200, dataset.size(), "Benchmark dataset must contain exactly 1,200 aligned observations");

        // 1. Warm-up Phase: First 20 normal observations calibrate the multivariate model
        List<MultivariateFeatureVector> warmupVectors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            warmupVectors.add(dataset.get(i).vector());
        }
        MultivariateCalibrationModel calModel = multivariateDetector.calibrate(evalService.getId(), warmupVectors);
        assertNotNull(calModel);
        assertTrue(calModel.getAnomalyThreshold() >= JavaMultivariateAnomalyDetector.MIN_CHI_SQUARE_THRESHOLD);

        // 2. Baseline History Buffers for Univariate Detectors
        // Clean baseline (Variant 2B: frozen 20 clean observations)
        List<Double> cleanCpuHistory = new ArrayList<>();
        List<Double> cleanMemHistory = new ArrayList<>();
        List<Double> cleanRtHistory = new ArrayList<>();
        List<Double> cleanErrHistory = new ArrayList<>();

        // Online rolling window (Variant 2A: sliding 30 observations updating dynamically)
        List<Double> rollingCpuHistory = new ArrayList<>();
        List<Double> rollingMemHistory = new ArrayList<>();
        List<Double> rollingRtHistory = new ArrayList<>();
        List<Double> rollingErrHistory = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            double c = dataset.get(i).vector().getCpuUsage();
            double m = dataset.get(i).vector().getMemoryUsage();
            double r = dataset.get(i).vector().getResponseTime();
            double e = dataset.get(i).vector().getErrorRate();

            cleanCpuHistory.add(c);
            cleanMemHistory.add(m);
            cleanRtHistory.add(r);
            cleanErrHistory.add(e);

            rollingCpuHistory.add(c);
            rollingMemHistory.add(m);
            rollingRtHistory.add(r);
            rollingErrHistory.add(e);
        }

        // Confusion Matrices for Overall Evaluation
        ConfusionMatrix cmStatic = new ConfusionMatrix();
        ConfusionMatrix cmStaticAlert = new ConfusionMatrix();
        ConfusionMatrix cmRollingZScore = new ConfusionMatrix();
        ConfusionMatrix cmCleanZScore = new ConfusionMatrix();
        ConfusionMatrix cmMultivariate = new ConfusionMatrix();

        // High-level Scenario Confusion Matrices (Scenarios A, B, C, D)
        Map<String, ConfusionMatrix> scenA_Static = new HashMap<>();
        Map<String, ConfusionMatrix> scenB_Rolling = new HashMap<>();
        Map<String, ConfusionMatrix> scenC_Clean = new HashMap<>();
        Map<String, ConfusionMatrix> scenD_Multivariate = new HashMap<>();

        String[] scenarioCategories = new String[] {
            "Scenario A - Normal Operations",
            "Scenario B - Sudden Anomalies",
            "Scenario C - Gradual Anomalies",
            "Scenario D - Correlated Anomalies"
        };
        for (String cat : scenarioCategories) {
            scenA_Static.put(cat, new ConfusionMatrix());
            scenB_Rolling.put(cat, new ConfusionMatrix());
            scenC_Clean.put(cat, new ConfusionMatrix());
            scenD_Multivariate.put(cat, new ConfusionMatrix());
        }

        // Detailed Per-Phase Confusion Matrices
        Map<String, ConfusionMatrix> phaseStatic = new HashMap<>();
        Map<String, ConfusionMatrix> phaseRolling = new HashMap<>();
        Map<String, ConfusionMatrix> phaseClean = new HashMap<>();
        Map<String, ConfusionMatrix> phaseMultivariate = new HashMap<>();

        // Evaluate all 1,180 post-warmup observations
        for (int i = 20; i < dataset.size(); i++) {
            LabeledObservation obs = dataset.get(i);
            MultivariateFeatureVector vec = obs.vector();
            boolean groundTruth = obs.groundTruthAnomaly();
            String rawPhase = obs.scenarioName();

            phaseStatic.computeIfAbsent(rawPhase, k -> new ConfusionMatrix());
            phaseRolling.computeIfAbsent(rawPhase, k -> new ConfusionMatrix());
            phaseClean.computeIfAbsent(rawPhase, k -> new ConfusionMatrix());
            phaseMultivariate.computeIfAbsent(rawPhase, k -> new ConfusionMatrix());

            // Map raw phases to the 4 mandated operational scenarios
            String category;
            if (rawPhase.equals("SCENARIO_1_STEADY_STATE_NORMAL") || rawPhase.equals("INTERLUDE_NORMAL") ||
                (rawPhase.equals("SCENARIO_3_GRADUAL_MEMORY_LEAK") && !groundTruth)) {
                category = "Scenario A - Normal Operations";
            } else if (rawPhase.equals("SCENARIO_2_SUDDEN_CPU_SPIKE") ||
                       rawPhase.equals("SCENARIO_4_RESPONSE_TIME_DEGRADATION") ||
                       rawPhase.equals("SCENARIO_5_ERROR_RATE_SURGE")) {
                category = "Scenario B - Sudden Anomalies";
            } else if (rawPhase.equals("SCENARIO_3_GRADUAL_MEMORY_LEAK") && groundTruth) {
                category = "Scenario C - Gradual Anomalies";
            } else {
                category = "Scenario D - Correlated Anomalies";
            }

            // --- 1. Approach 1: Static Threshold Detection ---
            // EvaluationService operational thresholds: CPU>80%, MEM>80%, RT>200ms, ERR>3.0%
            boolean staticPred = vec.getCpuUsage() > 80.0 ||
                                 vec.getMemoryUsage() > 80.0 ||
                                 vec.getResponseTime() > 200.0 ||
                                 vec.getErrorRate() > 3.0;

            // AlertService alert rule thresholds: CPU>80%, MEM>85%, RT>1000ms, ERR>5.0%
            boolean staticAlertPred = vec.getCpuUsage() > 80.0 ||
                                      vec.getMemoryUsage() > 85.0 ||
                                      vec.getResponseTime() > 1000.0 ||
                                      vec.getErrorRate() > 5.0;

            cmStatic.record(groundTruth, staticPred);
            cmStaticAlert.record(groundTruth, staticAlertPred);
            scenA_Static.get(category).record(groundTruth, staticPred);
            phaseStatic.get(rawPhase).record(groundTruth, staticPred);

            // --- 2. Approach 2A: Online Rolling Z-Score (Sliding 30-sample Window) ---
            DetectionResult rCpu = univariateDetector.detect("CPU_USAGE", vec.getCpuUsage(), "PERCENT", rollingCpuHistory);
            DetectionResult rMem = univariateDetector.detect("MEMORY_USAGE", vec.getMemoryUsage(), "PERCENT", rollingMemHistory);
            DetectionResult rRt = univariateDetector.detect("RESPONSE_TIME", vec.getResponseTime(), "MILLISECONDS", rollingRtHistory);
            DetectionResult rErr = univariateDetector.detect("ERROR_RATE", vec.getErrorRate(), "PERCENT", rollingErrHistory);
            boolean rollingPred = rCpu.isAnomaly() || rMem.isAnomaly() || rRt.isAnomaly() || rErr.isAnomaly();

            cmRollingZScore.record(groundTruth, rollingPred);
            scenB_Rolling.get(category).record(groundTruth, rollingPred);
            phaseRolling.get(rawPhase).record(groundTruth, rollingPred);

            // Update rolling history buffer (sliding window of max 30)
            rollingCpuHistory.add(vec.getCpuUsage());
            if (rollingCpuHistory.size() > 30) rollingCpuHistory.remove(0);
            rollingMemHistory.add(vec.getMemoryUsage());
            if (rollingMemHistory.size() > 30) rollingMemHistory.remove(0);
            rollingRtHistory.add(vec.getResponseTime());
            if (rollingRtHistory.size() > 30) rollingRtHistory.remove(0);
            rollingErrHistory.add(vec.getErrorRate());
            if (rollingErrHistory.size() > 30) rollingErrHistory.remove(0);

            // --- 2. Approach 2B: Clean Calibrated Z-Score (Frozen Clean Baseline) ---
            DetectionResult cCpu = univariateDetector.detect("CPU_USAGE", vec.getCpuUsage(), "PERCENT", cleanCpuHistory);
            DetectionResult cMem = univariateDetector.detect("MEMORY_USAGE", vec.getMemoryUsage(), "PERCENT", cleanMemHistory);
            DetectionResult cRt = univariateDetector.detect("RESPONSE_TIME", vec.getResponseTime(), "MILLISECONDS", cleanRtHistory);
            DetectionResult cErr = univariateDetector.detect("ERROR_RATE", vec.getErrorRate(), "PERCENT", cleanErrHistory);
            boolean cleanPred = cCpu.isAnomaly() || cMem.isAnomaly() || cRt.isAnomaly() || cErr.isAnomaly();

            cmCleanZScore.record(groundTruth, cleanPred);
            scenC_Clean.get(category).record(groundTruth, cleanPred);
            phaseClean.get(rawPhase).record(groundTruth, cleanPred);

            // --- 3. Approach 3: Multivariate Mahalanobis Anomaly Detection (4D) ---
            MultivariateDetectionResult mvtRes = multivariateDetector.detect(vec, calModel);
            boolean mvtPred = mvtRes.isAnomaly();

            cmMultivariate.record(groundTruth, mvtPred);
            scenD_Multivariate.get(category).record(groundTruth, mvtPred);
            phaseMultivariate.get(rawPhase).record(groundTruth, mvtPred);
        }

        // Print Comprehensive Evaluation Output
        log.info("====================================================================================================================");
        log.info("                SPECTRA TASK 5: COMPREHENSIVE END-TO-END ANOMALY DETECTION BENCHMARK (1,180 EVALUATIONS)            ");
        log.info("====================================================================================================================");
        log.info(String.format("%-26s | %-16s | %-16s | %-16s | %-16s",
            "Evaluation Metric", "Static Threshold", "Rolling Z-Score", "Clean Univariate", "Multivariate (4D)"));
        log.info("---------------------------+------------------+------------------+------------------+-----------------");
        log.info(String.format("%-26s | %15.2f%% | %15.2f%% | %15.2f%% | %15.2f%%",
            "Accuracy", cmStatic.accuracy() * 100, cmRollingZScore.accuracy() * 100, cmCleanZScore.accuracy() * 100, cmMultivariate.accuracy() * 100));
        log.info(String.format("%-26s | %15.2f%% | %15.2f%% | %15.2f%% | %15.2f%%",
            "Precision", cmStatic.precision() * 100, cmRollingZScore.precision() * 100, cmCleanZScore.precision() * 100, cmMultivariate.precision() * 100));
        log.info(String.format("%-26s | %15.2f%% | %15.2f%% | %15.2f%% | %15.2f%%",
            "Recall (Detection Rate)", cmStatic.recall() * 100, cmRollingZScore.recall() * 100, cmCleanZScore.recall() * 100, cmMultivariate.recall() * 100));
        log.info(String.format("%-26s | %16.4f | %16.4f | %16.4f | %16.4f",
            "F1-Score", cmStatic.f1Score(), cmRollingZScore.f1Score(), cmCleanZScore.f1Score(), cmMultivariate.f1Score()));
        log.info(String.format("%-26s | %15.2f%% | %15.2f%% | %15.2f%% | %15.2f%%",
            "Specificity", cmStatic.specificity() * 100, cmRollingZScore.specificity() * 100, cmCleanZScore.specificity() * 100, cmMultivariate.specificity() * 100));
        log.info(String.format("%-26s | %15.2f%% | %15.2f%% | %15.2f%% | %15.2f%%",
            "False Positive Rate (FPR)", cmStatic.falsePositiveRate() * 100, cmRollingZScore.falsePositiveRate() * 100, cmCleanZScore.falsePositiveRate() * 100, cmMultivariate.falsePositiveRate() * 100));
        log.info(String.format("%-26s | %16d | %16d | %16d | %16d",
            "True Positives (TP)", cmStatic.tp, cmRollingZScore.tp, cmCleanZScore.tp, cmMultivariate.tp));
        log.info(String.format("%-26s | %16d | %16d | %16d | %16d",
            "False Positives (FP)", cmStatic.fp, cmRollingZScore.fp, cmCleanZScore.fp, cmMultivariate.fp));
        log.info(String.format("%-26s | %16d | %16d | %16d | %16d",
            "True Negatives (TN)", cmStatic.tn, cmRollingZScore.tn, cmCleanZScore.tn, cmMultivariate.tn));
        log.info(String.format("%-26s | %16d | %16d | %16d | %16d",
            "False Negatives (FN)", cmStatic.fn, cmRollingZScore.fn, cmCleanZScore.fn, cmMultivariate.fn));
        log.info("====================================================================================================================");

        log.info("--------------------------------------------------------------------------------------------------------------------");
        log.info("                          SCENARIO-BASED COMPARISON (MANDATED OPERATIONAL SCENARIOS A - D)                          ");
        log.info("--------------------------------------------------------------------------------------------------------------------");
        for (String cat : scenarioCategories) {
            ConfusionMatrix st = scenA_Static.get(cat);
            ConfusionMatrix rz = scenB_Rolling.get(cat);
            ConfusionMatrix cz = scenC_Clean.get(cat);
            ConfusionMatrix mv = scenD_Multivariate.get(cat);
            int total = st.tp + st.fp + st.tn + st.fn;
            int anom = st.tp + st.fn;

            log.info(String.format(">> %-35s [Total=%4d, Ground-Truth Anomalies=%3d]", cat, total, anom));
            if (anom > 0) {
                log.info(String.format("   Static Threshold Recall : %6.2f%% (Detected %3d/%3d, FN=%3d, Precision=%6.2f%%, F1=%6.4f)",
                    st.recall() * 100, st.tp, anom, st.fn, st.precision() * 100, st.f1Score()));
                log.info(String.format("   Rolling Z-Score Recall  : %6.2f%% (Detected %3d/%3d, FN=%3d, Precision=%6.2f%%, F1=%6.4f)",
                    rz.recall() * 100, rz.tp, anom, rz.fn, rz.precision() * 100, rz.f1Score()));
                log.info(String.format("   Clean Univariate Recall : %6.2f%% (Detected %3d/%3d, FN=%3d, Precision=%6.2f%%, F1=%6.4f)",
                    cz.recall() * 100, cz.tp, anom, cz.fn, cz.precision() * 100, cz.f1Score()));
                log.info(String.format("   Multivariate Recall     : %6.2f%% (Detected %3d/%3d, FN=%3d, Precision=%6.2f%%, F1=%6.4f)",
                    mv.recall() * 100, mv.tp, anom, mv.fn, mv.precision() * 100, mv.f1Score()));
            } else {
                log.info(String.format("   Static Threshold FP     : %3d (FPR=%5.2f%%)", st.fp, st.falsePositiveRate() * 100));
                log.info(String.format("   Rolling Z-Score FP      : %3d (FPR=%5.2f%%)", rz.fp, rz.falsePositiveRate() * 100));
                log.info(String.format("   Clean Univariate FP     : %3d (FPR=%5.2f%%)", cz.fp, cz.falsePositiveRate() * 100));
                log.info(String.format("   Multivariate FP         : %3d (FPR=%5.2f%%)", mv.fp, mv.falsePositiveRate() * 100));
            }
        }
        log.info("====================================================================================================================");

        // Assertions verifying performance dynamics
        // 1. Multivariate achieves the highest F1-Score of all approaches
        assertTrue(cmMultivariate.f1Score() > cmStatic.f1Score(), "Multivariate F1 must exceed Static Threshold F1");
        assertTrue(cmMultivariate.f1Score() > cmRollingZScore.f1Score(), "Multivariate F1 must exceed Rolling Z-Score F1");
        assertTrue(cmMultivariate.f1Score() > cmCleanZScore.f1Score(), "Multivariate F1 must exceed Clean Univariate F1");
        assertTrue(cmMultivariate.f1Score() >= 0.98, "Multivariate F1 must achieve >= 0.98");

        // 2. Scenario D (Correlated Anomalies): Multivariate achieves >95% recall; individual detectors fail (<20% recall)
        ConfusionMatrix mvScenD = scenD_Multivariate.get("Scenario D - Correlated Anomalies");
        ConfusionMatrix stScenD = scenA_Static.get("Scenario D - Correlated Anomalies");
        ConfusionMatrix rzScenD = scenB_Rolling.get("Scenario D - Correlated Anomalies");
        ConfusionMatrix czScenD = scenC_Clean.get("Scenario D - Correlated Anomalies");

        assertTrue(mvScenD.recall() >= 0.95, "Multivariate must detect >= 95% of correlated anomalies");
        assertTrue(stScenD.recall() == 0.0, "Static threshold must detect 0% of sub-threshold correlated anomalies");
        assertTrue(rzScenD.recall() <= 0.10, "Rolling Z-score must detect <= 10% of sub-threshold correlated anomalies");
        assertTrue(czScenD.recall() == 0.0, "Clean univariate must detect 0% of sub-threshold correlated anomalies");

        // 3. Scenario C (Gradual Anomalies): Rolling window absorbs the leak, causing lower recall than frozen or multivariate
        ConfusionMatrix mvScenC = scenD_Multivariate.get("Scenario C - Gradual Anomalies");
        ConfusionMatrix rzScenC = scenB_Rolling.get("Scenario C - Gradual Anomalies");
        ConfusionMatrix stScenC = scenA_Static.get("Scenario C - Gradual Anomalies");

        assertTrue(mvScenC.recall() >= 0.95, "Multivariate must detect gradual memory leak without baseline absorption");
        assertTrue(rzScenC.recall() < mvScenC.recall(), "Rolling Z-score must suffer baseline absorption on gradual leak");
        assertTrue(stScenC.recall() < mvScenC.recall(), "Static threshold must miss early stages of gradual leak (before 80%)");

        // 4. Scenario A (Normal Operations): All detectors have low FPR (< 5%)
        ConfusionMatrix mvScenA = scenD_Multivariate.get("Scenario A - Normal Operations");
        ConfusionMatrix stScenA = scenA_Static.get("Scenario A - Normal Operations");
        ConfusionMatrix rzScenA = scenB_Rolling.get("Scenario A - Normal Operations");

        assertTrue(mvScenA.falsePositiveRate() < 0.02, "Multivariate FPR must be < 2%");
        assertTrue(stScenA.falsePositiveRate() < 0.02, "Static Threshold FPR must be < 2%");
        assertTrue(rzScenA.falsePositiveRate() < 0.05, "Rolling Z-Score FPR must be < 5%");
    }
}
