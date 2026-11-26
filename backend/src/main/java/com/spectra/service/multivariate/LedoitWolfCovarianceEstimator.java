package com.spectra.service.multivariate;

import java.util.Arrays;
import java.util.List;

/**
 * Pure mathematical implementation of Java-native Ledoit-Wolf covariance shrinkage
 * with domain-floor scaling and positive spectral ridge regularization.
 * <p>
 * Transforms raw metric observations into dimensionless space using canonical domain floors:
 * <pre>
 *   z_j = (x_j - mu_j) / sigma_floor,j
 * </pre>
 * Applies optimal Ledoit-Wolf (2004) analytical shrinkage towards the spherical target F = mu * I,
 * applies a dimensionless unit floor (1.0 in Z-space), and adds an isotropic spectral ridge eps * I.
 * <p>
 * Guarantees that the final covariance matrix Sigma_final is strictly positive definite:
 * lambda_min(Sigma_final) >= eps > 0 for all constant, collinear, or rank-deficient inputs.
 */
public final class LedoitWolfCovarianceEstimator {

    public static final int MIN_OBSERVATIONS = 10;
    public static final double MIN_EPSILON = 1e-6;
    public static final double RELATIVE_RIDGE_FACTOR = 1e-5;

    public record CovarianceEstimate(
        double[] mean,
        double[] sigmaFloors,
        double[][] sampleCovarianceZ,
        double[][] shrunkCovarianceZ,
        double[][] finalCovarianceZ,
        double lambdaStar,
        double epsilonZ,
        double[] diagonalFloorAdjustments
    ) {
        public CovarianceEstimate {
            // Defensive copies for immutability
            mean = mean.clone();
            sigmaFloors = sigmaFloors.clone();
            sampleCovarianceZ = cloneMatrix(sampleCovarianceZ);
            shrunkCovarianceZ = cloneMatrix(shrunkCovarianceZ);
            finalCovarianceZ = cloneMatrix(finalCovarianceZ);
            diagonalFloorAdjustments = diagonalFloorAdjustments.clone();
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
     * Estimates regularized covariance using canonical metric floors.
     */
    public CovarianceEstimate estimate(double[][] X) {
        return estimate(X, MultivariateMetricType.getSigmaFloors());
    }

    /**
     * Estimates regularized covariance from a list of MultivariateFeatureVectors.
     */
    public CovarianceEstimate estimateFromVectors(List<MultivariateFeatureVector> vectors) {
        if (vectors == null || vectors.size() < MIN_OBSERVATIONS) {
            int count = (vectors != null) ? vectors.size() : 0;
            throw new NumericalInstabilityException(
                "Insufficient observations for covariance estimation: " + count + " provided, minimum " +
                MIN_OBSERVATIONS + " required");
        }
        double[][] X = new double[vectors.size()][MultivariateMetricType.DIMENSION];
        for (int i = 0; i < vectors.size(); i++) {
            X[i] = vectors.get(i).toArray();
        }
        return estimate(X);
    }

    /**
     * Estimates regularized covariance for matrix X with specified sigma floors.
     *
     * @param X observation matrix of dimension n x p (n >= 10, p == 4)
     * @param sigmaFloors positive domain standard deviation floors for each dimension
     * @return fully regularized CovarianceEstimate
     */
    public CovarianceEstimate estimate(double[][] X, double[] sigmaFloors) {
        validateInputs(X, sigmaFloors);

        int n = X.length;
        int p = X[0].length;

        // 1. Calculate baseline mean vector in original physical units
        double[] mean = new double[p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                mean[j] += X[i][j];
            }
        }
        for (int j = 0; j < p; j++) {
            mean[j] /= n;
        }

        // 2. Standardize observations to dimensionless Z-space using domain floors:
        //    Z_ij = (X_ij - mean_j) / sigmaFloor_j
        double[][] Z = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                Z[i][j] = (X[i][j] - mean[j]) / sigmaFloors[j];
            }
        }

        // Center Z to mitigate any floating point drift
        double[] zMean = new double[p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                zMean[j] += Z[i][j];
            }
        }
        for (int j = 0; j < p; j++) {
            zMean[j] /= n;
        }

        double[][] Zc = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                Zc[i][j] = Z[i][j] - zMean[j];
            }
        }

        // 3. Compute sample covariance matrix S_z in dimensionless space (using 1/n normalization)
        double[][] S = new double[p][p];
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    sum += Zc[i][j] * Zc[i][k];
                }
                S[j][k] = sum / n;
            }
        }

        // 4. Shrinkage target: F = mu * I, where mu = tr(S) / p
        double trS = 0.0;
        for (int j = 0; j < p; j++) {
            trS += S[j][j];
        }
        double mu = trS / p;

        // Frobenius norm of difference: delta^2 = ||S - F||_F^2
        double delta2 = 0.0;
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                double target = (j == k) ? mu : 0.0;
                double diff = S[j][k] - target;
                delta2 += diff * diff;
            }
        }

        // 5. Analytical Ledoit-Wolf shrinkage intensity lambda*
        double lambdaStar = 0.0;
        if (delta2 > 0.0) {
            double[][] Zc2 = new double[n][p];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    Zc2[i][j] = Zc[i][j] * Zc[i][j];
                }
            }

            double sumSq = 0.0;
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < p; k++) {
                    double dot = 0.0;
                    for (int i = 0; i < n; i++) {
                        dot += Zc2[i][j] * Zc2[i][k];
                    }
                    sumSq += dot;
                }
            }
            sumSq /= (double) (n * n);

            double sumS2 = 0.0;
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < p; k++) {
                    sumS2 += S[j][k] * S[j][k];
                }
            }
            double beta = sumSq - (sumS2 / n);
            lambdaStar = Math.max(0.0, Math.min(1.0, beta / delta2));
        }

        // 6. Compute shrunk matrix: Sigma_LW = (1 - lambda*) * S + lambda* * F
        double[][] sigmaLW = new double[p][p];
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                double target = (j == k) ? mu : 0.0;
                sigmaLW[j][k] = (1.0 - lambdaStar) * S[j][k] + lambdaStar * target;
            }
        }

        // 7. Dimensionless variance floor adjustment (floor is exactly 1.0 in standardized space)
        double[] dFloorAdj = new double[p];
        for (int j = 0; j < p; j++) {
            dFloorAdj[j] = Math.max(0.0, 1.0 - sigmaLW[j][j]);
        }

        // 8. Dimensionless global isotropic ridge: epsilon = max(1e-6, 1e-5 * (tr(Sigma_LW) / p))
        double trLW = 0.0;
        for (int j = 0; j < p; j++) {
            trLW += sigmaLW[j][j];
        }
        double epsilon = Math.max(MIN_EPSILON, RELATIVE_RIDGE_FACTOR * (trLW / p));

        // 9. Assemble final regularized covariance matrix:
        //    Sigma_final = Sigma_LW + diag(dFloorAdj) + epsilon * I
        double[][] sigmaFinal = new double[p][p];
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                sigmaFinal[j][k] = sigmaLW[j][k];
                if (j == k) {
                    sigmaFinal[j][k] += dFloorAdj[j] + epsilon;
                }
            }
        }

        return new CovarianceEstimate(
            mean,
            sigmaFloors,
            S,
            sigmaLW,
            sigmaFinal,
            lambdaStar,
            epsilon,
            dFloorAdj
        );
    }

    private void validateInputs(double[][] X, double[] sigmaFloors) {
        if (X == null || X.length < MIN_OBSERVATIONS) {
            int count = (X != null) ? X.length : 0;
            throw new NumericalInstabilityException(
                "Insufficient observations for covariance estimation: " + count + " provided, minimum " +
                MIN_OBSERVATIONS + " required");
        }
        if (sigmaFloors == null || sigmaFloors.length != MultivariateMetricType.DIMENSION) {
            throw new IllegalArgumentException(
                "Sigma floors array must have length " + MultivariateMetricType.DIMENSION);
        }
        for (int j = 0; j < sigmaFloors.length; j++) {
            if (sigmaFloors[j] <= 0.0 || Double.isNaN(sigmaFloors[j]) || Double.isInfinite(sigmaFloors[j])) {
                throw new IllegalArgumentException("Sigma floor at index " + j + " must be strictly positive and finite: " + sigmaFloors[j]);
            }
        }

        int p = MultivariateMetricType.DIMENSION;
        for (int i = 0; i < X.length; i++) {
            if (X[i] == null || X[i].length != p) {
                int len = (X[i] != null) ? X[i].length : 0;
                throw new InvalidFeatureVectorException(
                    "Observation row " + i + " has dimension " + len + ", expected " + p);
            }
            for (int j = 0; j < p; j++) {
                double val = X[i][j];
                if (Double.isNaN(val) || Double.isInfinite(val)) {
                    throw new InvalidFeatureVectorException(
                        "Non-finite value at observation row " + i + ", feature " + j + ": " + val);
                }
            }
        }
    }
}
