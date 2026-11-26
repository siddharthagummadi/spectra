package com.spectra.service.multivariate;

/**
 * Pure mathematical Cholesky decomposition solver for positive-definite covariance matrices.
 * <p>
 * Provides robust numerical factorisation:
 * <pre>
 *   Sigma = L * L^T
 * </pre>
 * Calculates the exact precision matrix Sigma^-1 = (L^-1)^T * L^-1 and evaluates
 * Mahalanobis distances via stable forward/back triangular substitution.
 * <p>
 * If the matrix fails the positive-definiteness condition (pivot <= 0.0), throws
 * {@link NumericalInstabilityException} with diagnostic details rather than silently producing NaN.
 */
public final class CholeskyPrecisionSolver {

    public record CholeskyResult(
        double[][] L,
        double[][] Linv,
        double[][] precisionMatrix
    ) {
        public CholeskyResult {
            L = cloneMatrix(L);
            Linv = cloneMatrix(Linv);
            precisionMatrix = cloneMatrix(precisionMatrix);
        }

        private static double[][] cloneMatrix(double[][] m) {
            double[][] copy = new double[m.length][];
            for (int i = 0; i < m.length; i++) {
                copy[i] = m[i].clone();
            }
            return copy;
        }
    }

    /**
     * Solves Cholesky decomposition and computes precision matrix.
     */
    public CholeskyResult solve(double[][] sigma) {
        if (sigma == null || sigma.length != MultivariateMetricType.DIMENSION) {
            throw new IllegalArgumentException("Covariance matrix must be 4x4");
        }
        int p = sigma.length;

        // 1. Cholesky factorization: L * L^T = Sigma
        double[][] L = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0.0;
                for (int k = 0; k < j; k++) {
                    sum += L[i][k] * L[j][k];
                }
                if (i == j) {
                    double val = sigma[i][i] - sum;
                    if (val <= 0.0 || Double.isNaN(val) || Double.isInfinite(val)) {
                        throw new NumericalInstabilityException(
                            String.format("Matrix is not positive definite at row %d: diagonal pivot is %.6e <= 0", i, val));
                    }
                    L[i][j] = Math.sqrt(val);
                } else {
                    if (L[j][j] == 0.0) {
                        throw new NumericalInstabilityException(
                            String.format("Division by zero in Cholesky decomposition at row %d, col %d", i, j));
                    }
                    L[i][j] = (sigma[i][j] - sum) / L[j][j];
                }
            }
        }

        // 2. Invert lower triangular matrix L: L * Linv = I
        double[][] Linv = new double[p][p];
        for (int i = 0; i < p; i++) {
            Linv[i][i] = 1.0 / L[i][i];
            for (int j = 0; j < i; j++) {
                double sum = 0.0;
                for (int k = j; k < i; k++) {
                    sum += L[i][k] * Linv[k][j];
                }
                Linv[i][j] = -sum / L[i][i];
            }
        }

        // 3. Precision matrix: Sigma^-1 = (Linv)^T * Linv
        double[][] precisionMatrix = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                double sum = 0.0;
                for (int k = 0; k < p; k++) {
                    sum += Linv[k][i] * Linv[k][j];
                }
                precisionMatrix[i][j] = sum;
            }
        }

        return new CholeskyResult(L, Linv, precisionMatrix);
    }

    /**
     * Solves Sigma * v = z for v using triangular substitution:
     * 1. Solve L * y = z (forward substitution)
     * 2. Solve L^T * v = y (back substitution)
     */
    public double[] solvePrecisionVector(double[][] L, double[] z) {
        int p = L.length;
        double[] y = new double[p];

        // Forward solve: L * y = z
        for (int i = 0; i < p; i++) {
            double sum = 0.0;
            for (int k = 0; k < i; k++) {
                sum += L[i][k] * y[k];
            }
            y[i] = (z[i] - sum) / L[i][i];
        }

        // Back solve: L^T * v = y
        double[] v = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int k = i + 1; k < p; k++) {
                sum += L[k][i] * v[k];
            }
            v[i] = (y[i] - sum) / L[i][i];
        }

        return v;
    }

    /**
     * Computes squared Mahalanobis distance D_M^2 = y^T * y where L * y = z.
     */
    public double mahalanobisDistanceSquared(double[][] L, double[] z) {
        int p = L.length;
        double sumSq = 0.0;
        double[] y = new double[p];
        for (int i = 0; i < p; i++) {
            double sum = 0.0;
            for (int k = 0; k < i; k++) {
                sum += L[i][k] * y[k];
            }
            y[i] = (z[i] - sum) / L[i][i];
            sumSq += y[i] * y[i];
        }
        return sumSq;
    }
}
