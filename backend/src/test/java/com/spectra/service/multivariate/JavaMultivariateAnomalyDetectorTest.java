package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaMultivariateAnomalyDetectorTest {

    private JavaMultivariateAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new JavaMultivariateAnomalyDetector();
    }

    private List<MultivariateFeatureVector> generateBaseline(int count) {
        List<MultivariateFeatureVector> list = new ArrayList<>(count);
        Instant baseTime = Instant.now().minus(Duration.ofMinutes(count));
        for (int i = 0; i < count; i++) {
            // Mean approx: CPU=40%, MEM=60%, RT=120ms, ERR=0.2%
            double cpu = 40.0 + (i % 5) - 2.0;
            double mem = 60.0 + (i % 7) - 3.0;
            double rt = 120.0 + (i % 9) * 2.0 - 8.0;
            double err = 0.20 + (i % 3) * 0.05 - 0.05;
            list.add(MultivariateFeatureVector.of(cpu, mem, rt, err, baseTime.plus(Duration.ofMinutes(i))));
        }
        return list;
    }

    @Test
    void calibrateAndDetect_NormalObservation_WithinExpectedBaseline() {
        List<MultivariateFeatureVector> baseline = generateBaseline(30);
        MultivariateCalibrationModel model = detector.calibrate(1L, baseline);

        assertNotNull(model);
        assertEquals(30, model.getSampleCount());
        assertTrue(model.getAnomalyThreshold() >= JavaMultivariateAnomalyDetector.MIN_CHI_SQUARE_THRESHOLD);

        // An observation very close to baseline mean
        MultivariateFeatureVector normalObs = MultivariateFeatureVector.of(40.5, 60.2, 122.0, 0.22);
        MultivariateDetectionResult result = detector.detect(normalObs, model);

        assertFalse(result.isAnomaly());
        assertEquals("NONE", result.severity());
        assertTrue(result.mahalanobisDistance() <= model.getAnomalyThreshold());
        assertEquals(4, result.featureContributions().size());
    }

    @Test
    void calibrateAndDetect_AnomalySpike_IdentifiedWithPrimaryDriver() {
        List<MultivariateFeatureVector> baseline = generateBaseline(30);
        MultivariateCalibrationModel model = detector.calibrate(1L, baseline);

        // Huge surge in error rate and response time
        // Error rate jumps from 0.2% to 8.5% (over 80 std dev jumps in Z-space!)
        MultivariateFeatureVector anomalousObs = MultivariateFeatureVector.of(42.0, 61.0, 450.0, 8.5);
        MultivariateDetectionResult result = detector.detect(anomalousObs, model);

        assertTrue(result.isAnomaly());
        assertTrue(result.mahalanobisDistance() > model.getAnomalyThreshold());
        assertTrue(result.squaredMahalanobisDistance() > 100.0);

        // Primary driver should be ERROR_RATE due to the massive surge
        assertEquals(MultivariateMetricType.ERROR_RATE, result.primaryDriver());
        assertTrue(result.featureContributions().get(0).driverSharePercent() > 50.0);

        // Severity should be CRITICAL
        assertEquals("CRITICAL", result.severity());
        assertTrue(result.message().contains("Multivariate anomaly detected"));
    }

    @Test
    void detect_MathematicalScoreConsistency_ExactDeterminism() {
        List<MultivariateFeatureVector> baseline = generateBaseline(30);
        MultivariateCalibrationModel model = detector.calibrate(1L, baseline);

        MultivariateFeatureVector obs = MultivariateFeatureVector.of(48.0, 72.0, 210.0, 1.5);

        MultivariateDetectionResult res1 = detector.detect(obs, model);
        MultivariateDetectionResult res2 = detector.detect(obs, model);

        // Must be bitwise and numerically identical
        assertEquals(res1.mahalanobisDistance(), res2.mahalanobisDistance(), 0.0);
        assertEquals(res1.squaredMahalanobisDistance(), res2.squaredMahalanobisDistance(), 0.0);
        assertEquals(res1.primaryDriver(), res2.primaryDriver());

        for (int i = 0; i < 4; i++) {
            assertEquals(
                res1.featureContributions().get(i).signedContribution(),
                res2.featureContributions().get(i).signedContribution(),
                0.0
            );
            assertEquals(
                res1.featureContributions().get(i).driverSharePercent(),
                res2.featureContributions().get(i).driverSharePercent(),
                0.0
            );
        }
    }

    @Test
    void detect_InvalidOrNullInputs_ThrowsExpectedExceptions() {
        List<MultivariateFeatureVector> baseline = generateBaseline(30);
        MultivariateCalibrationModel model = detector.calibrate(1L, baseline);

        // Null vector
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> detector.detect(null, model)
        );

        // Null model
        MultivariateFeatureVector obs = MultivariateFeatureVector.of(40.0, 60.0, 120.0, 0.2);
        assertThrows(
            IllegalArgumentException.class,
            () -> detector.detect(obs, null)
        );

        // Insufficient training samples (< 10)
        List<MultivariateFeatureVector> smallBaseline = generateBaseline(8);
        assertThrows(
            NumericalInstabilityException.class,
            () -> detector.calibrate(1L, smallBaseline)
        );
    }

    @Test
    void verifyPythonReferenceNumericalParity_Constant() {
        verifyParity("Constant", MultivariateReferenceData.DATA_CONSTANT, MultivariateReferenceData.REF_CONSTANT);
    }

    @Test
    void verifyPythonReferenceNumericalParity_Collinear() {
        verifyParity("Collinear", MultivariateReferenceData.DATA_COLLINEAR, MultivariateReferenceData.REF_COLLINEAR);
    }

    @Test
    void verifyPythonReferenceNumericalParity_NearSingular() {
        verifyParity("Near-Singular", MultivariateReferenceData.DATA_NEAR_SINGULAR, MultivariateReferenceData.REF_NEAR_SINGULAR);
    }

    @Test
    void verifyPythonReferenceNumericalParity_OutlierContaminated() {
        verifyParity("Outlier-Contaminated", MultivariateReferenceData.DATA_OUTLIER_CONTAMINATED, MultivariateReferenceData.REF_OUTLIER_CONTAMINATED);
    }

    private void verifyParity(String datasetName, double[][] X, MultivariateReferenceData.ReferenceResult ref) {
        int p = 4;
        CholeskyPrecisionSolver solver = new CholeskyPrecisionSolver();

        LedoitWolfCovarianceEstimator.CovarianceEstimate est =
            detector.getCovarianceEstimator().estimate(X);

        // Compare lambda* against Python NumPy/SciPy reference
        assertEquals(ref.lambdaStar(), est.lambdaStar(), 1e-12,
            "[" + datasetName + "] lambda* parity failure");

        // Compare epsilon_z against Python reference
        assertEquals(ref.epsilonZ(), est.epsilonZ(), 1e-12,
            "[" + datasetName + "] epsilon_z parity failure");

        // Solve Cholesky
        CholeskyPrecisionSolver.CholeskyResult chol = solver.solve(est.finalCovarianceZ());

        // Score for observation 0
        double[] z0 = new double[p];
        for (int j = 0; j < p; j++) {
            z0[j] = (X[0][j] - est.mean()[j]) / est.sigmaFloors()[j];
        }
        double score0 = solver.mahalanobisDistanceSquared(chol.L(), z0);
        assertEquals(ref.score0(), score0, 1e-10,
            "[" + datasetName + "] score0 parity failure");

        // Attributions for observation 0
        double[] v0 = solver.solvePrecisionVector(chol.L(), z0);
        for (int j = 0; j < p; j++) {
            double c0_j = z0[j] * v0[j];
            assertEquals(ref.c0()[j], c0_j, 1e-10,
                "[" + datasetName + "] c0[" + j + "] attribution parity failure");
        }
    }
}
