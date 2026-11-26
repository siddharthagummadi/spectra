package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CholeskyPrecisionSolverTest {

    private CholeskyPrecisionSolver solver;

    @BeforeEach
    void setUp() {
        solver = new CholeskyPrecisionSolver();
    }

    @Test
    void solve_IdentityMatrix_PrecisionIsIdentity() {
        double[][] I = {
            { 1.0, 0.0, 0.0, 0.0 },
            { 0.0, 1.0, 0.0, 0.0 },
            { 0.0, 0.0, 1.0, 0.0 },
            { 0.0, 0.0, 0.0, 1.0 }
        };

        CholeskyPrecisionSolver.CholeskyResult res = solver.solve(I);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(I[i][j], res.L()[i][j], 1e-12);
                assertEquals(I[i][j], res.precisionMatrix()[i][j], 1e-12);
            }
        }
    }

    @Test
    void solve_SymmetricPositiveDefiniteMatrix_InvertsCorrectly() {
        // Known SPD matrix
        double[][] A = {
            { 4.0, 1.0, 0.5, 0.2 },
            { 1.0, 5.0, 1.2, 0.3 },
            { 0.5, 1.2, 3.0, 0.1 },
            { 0.2, 0.3, 0.1, 2.0 }
        };

        CholeskyPrecisionSolver.CholeskyResult res = solver.solve(A);
        double[][] inv = res.precisionMatrix();

        // Check A * A^-1 = I
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double dot = 0.0;
                for (int k = 0; k < 4; k++) {
                    dot += A[i][k] * inv[k][j];
                }
                double expected = (i == j) ? 1.0 : 0.0;
                assertEquals(expected, dot, 1e-10, "A * A^-1 must equal identity at [" + i + "][" + j + "]");
            }
        }
    }

    @Test
    void solvePrecisionVector_MatchesMatrixVectorMultiplication() {
        double[][] A = {
            { 3.0, 0.8, 0.4, 0.1 },
            { 0.8, 4.0, 0.6, 0.2 },
            { 0.4, 0.6, 2.5, 0.3 },
            { 0.1, 0.2, 0.3, 1.8 }
        };

        CholeskyPrecisionSolver.CholeskyResult res = solver.solve(A);
        double[] z = { 1.5, -2.0, 0.75, 3.1 };

        double[] vSolve = solver.solvePrecisionVector(res.L(), z);

        // Compute direct vDirect = A^-1 * z
        double[] vDirect = new double[4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                vDirect[i] += res.precisionMatrix()[i][j] * z[j];
            }
        }

        assertArrayEquals(vDirect, vSolve, 1e-11);

        // Check Mahalanobis distance squared consistency
        double d2Fast = solver.mahalanobisDistanceSquared(res.L(), z);
        double d2Direct = 0.0;
        for (int i = 0; i < 4; i++) {
            d2Direct += z[i] * vDirect[i];
        }
        assertEquals(d2Direct, d2Fast, 1e-11);
    }

    @Test
    void solve_NonPositiveDefiniteMatrix_ThrowsNumericalInstabilityException() {
        // Matrix with negative pivot
        double[][] bad = {
            { 1.0, 2.0, 0.0, 0.0 },
            { 2.0, 1.0, 0.0, 0.0 }, // 1 - 2^2 = -3 < 0
            { 0.0, 0.0, 1.0, 0.0 },
            { 0.0, 0.0, 0.0, 1.0 }
        };

        assertThrows(
            NumericalInstabilityException.class,
            () -> solver.solve(bad)
        );
    }
}
