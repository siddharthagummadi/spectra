package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedoitWolfCovarianceEstimatorTest {

    private LedoitWolfCovarianceEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new LedoitWolfCovarianceEstimator();
    }

    @Test
    void estimate_InsufficientSamples_ThrowsNumericalInstabilityException() {
        double[][] X = new double[9][4]; // 9 samples < 10 required
        assertThrows(
            NumericalInstabilityException.class,
            () -> estimator.estimate(X)
        );
    }

    @Test
    void estimate_NonFiniteValue_ThrowsInvalidFeatureVectorException() {
        double[][] X = new double[10][4];
        X[3][2] = Double.NaN;
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> estimator.estimate(X)
        );
    }

    @Test
    void estimate_ConstantFeatureVectors_RegularizedCleanly() {
        int n = 50;
        int p = 4;
        double[][] X = new double[n][p];
        for (int i = 0; i < n; i++) {
            X[i][0] = 45.0;
            X[i][1] = 60.0;
            X[i][2] = 120.0;
            X[i][3] = 0.5;
        }

        LedoitWolfCovarianceEstimator.CovarianceEstimate est = estimator.estimate(X);

        assertEquals(0.0, est.lambdaStar(), 1e-9);
        assertEquals(1e-6, est.epsilonZ(), 1e-9);

        // In constant data, all sample covariances are 0.
        // Diagonal floors (1.0) and epsilon (1e-6) make final matrix exactly 1.000001 * I
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                if (j == k) {
                    assertEquals(1.000001, est.finalCovarianceZ()[j][k], 1e-6);
                } else {
                    assertEquals(0.0, est.finalCovarianceZ()[j][k], 1e-9);
                }
            }
        }
    }

    @Test
    void estimate_CollinearFeatures_PositiveDefiniteRegularization() {
        int n = 50;
        int p = 4;
        double[][] X = new double[n][p];
        for (int i = 0; i < n; i++) {
            double v = 10.0 + (40.0 * i) / (n - 1);
            X[i][0] = v;
            X[i][1] = 2.0 * v;
            X[i][2] = 0.5 * v;
            X[i][3] = 0.01 * v;
        }

        LedoitWolfCovarianceEstimator.CovarianceEstimate est = estimator.estimate(X);

        assertTrue(est.lambdaStar() > 0.0, "Lambda* should be positive for collinear data");
        assertTrue(est.epsilonZ() > 0.0, "Epsilon ridge should be strictly positive");

        // Verify diagonal elements are >= 1.0 (enforcing domain noise floor)
        for (int j = 0; j < p; j++) {
            assertTrue(est.finalCovarianceZ()[j][j] >= 1.0, "Diagonal variance floor violated at index " + j);
        }
    }

    @Test
    void estimate_NearSingularFeatures_StrictlyPositiveDefinite() {
        int n = 50;
        int p = 4;
        double[][] X = new double[n][p];
        for (int i = 0; i < n; i++) {
            X[i][0] = 50.0 + i * 0.1;
            X[i][1] = 50.0 + i * 0.1 + 1e-7;
            X[i][2] = 100.0 + i * 0.2;
            X[i][3] = 1.0 + i * 0.001;
        }

        LedoitWolfCovarianceEstimator.CovarianceEstimate est = estimator.estimate(X);

        assertNotNull(est.finalCovarianceZ());
        assertTrue(est.epsilonZ() >= 1e-6);

        // Check positive pivots in Cholesky
        CholeskyPrecisionSolver solver = new CholeskyPrecisionSolver();
        CholeskyPrecisionSolver.CholeskyResult chol = solver.solve(est.finalCovarianceZ());
        assertNotNull(chol.L());
        for (int j = 0; j < p; j++) {
            assertTrue(chol.L()[j][j] > 0.0, "Cholesky diagonal pivot must be strictly positive");
        }
    }

    @Test
    void estimate_OutlierContaminatedData_StableEstimation() {
        int n = 50;
        int p = 4;
        double[][] X = new double[n][p];
        for (int i = 0; i < 45; i++) {
            X[i][0] = 50.0 + (i % 5);
            X[i][1] = 60.0 + (i % 7);
            X[i][2] = 150.0 + (i % 11);
            X[i][3] = 1.0 + (i % 3) * 0.1;
        }
        // 5 extreme outliers
        for (int i = 45; i < 50; i++) {
            X[i][0] = 95.0;
            X[i][1] = 98.0;
            X[i][2] = 2000.0;
            X[i][3] = 25.0;
        }

        LedoitWolfCovarianceEstimator.CovarianceEstimate est = estimator.estimate(X);

        assertTrue(est.lambdaStar() >= 0.0 && est.lambdaStar() <= 1.0);
        assertTrue(est.epsilonZ() > 0.0);

        CholeskyPrecisionSolver solver = new CholeskyPrecisionSolver();
        CholeskyPrecisionSolver.CholeskyResult chol = solver.solve(est.finalCovarianceZ());
        assertNotNull(chol.precisionMatrix());
    }
}
