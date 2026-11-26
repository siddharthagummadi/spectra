package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, thread-safe calibrated baseline model for 4D multivariate Mahalanobis detection.
 * <p>
 * Contains pre-computed baseline parameters, regularized covariance, Cholesky decomposition factor,
 * precision matrix, and anomaly detection threshold for a specific service.
 * <p>
 * Evaluates individual incoming telemetry observations in O(p^2) time without recalibrating.
 */
public final class MultivariateCalibrationModel {

    private final Long serviceId;
    private final double[] baselineMean;
    private final double[] sigmaFloors;
    private final double[][] covariance;
    private final double[][] precisionMatrix;
    private final double[][] choleskyL;
    private final double lambdaStar;
    private final double epsilonZ;
    private final double anomalyThreshold;
    private final int sampleCount;
    private final Instant calibratedAt;

    public MultivariateCalibrationModel(
        Long serviceId,
        double[] baselineMean,
        double[] sigmaFloors,
        double[][] covariance,
        double[][] precisionMatrix,
        double[][] choleskyL,
        double lambdaStar,
        double epsilonZ,
        double anomalyThreshold,
        int sampleCount,
        Instant calibratedAt
    ) {
        this.serviceId = serviceId;
        this.baselineMean = Objects.requireNonNull(baselineMean, "baselineMean cannot be null").clone();
        this.sigmaFloors = Objects.requireNonNull(sigmaFloors, "sigmaFloors cannot be null").clone();
        this.covariance = cloneMatrix(Objects.requireNonNull(covariance, "covariance cannot be null"));
        this.precisionMatrix = cloneMatrix(Objects.requireNonNull(precisionMatrix, "precisionMatrix cannot be null"));
        this.choleskyL = cloneMatrix(Objects.requireNonNull(choleskyL, "choleskyL cannot be null"));
        this.lambdaStar = lambdaStar;
        this.epsilonZ = epsilonZ;
        this.anomalyThreshold = anomalyThreshold;
        this.sampleCount = sampleCount;
        this.calibratedAt = calibratedAt != null ? calibratedAt : Instant.now();
    }

    private static double[][] cloneMatrix(double[][] m) {
        double[][] copy = new double[m.length][];
        for (int i = 0; i < m.length; i++) {
            copy[i] = m[i].clone();
        }
        return copy;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public double[] getBaselineMean() {
        return baselineMean.clone();
    }

    public double[] getSigmaFloors() {
        return sigmaFloors.clone();
    }

    public double[][] getCovariance() {
        return cloneMatrix(covariance);
    }

    public double[][] getPrecisionMatrix() {
        return cloneMatrix(precisionMatrix);
    }

    public double[][] getCholeskyL() {
        return cloneMatrix(choleskyL);
    }

    public double getLambdaStar() {
        return lambdaStar;
    }

    public double getEpsilonZ() {
        return epsilonZ;
    }

    public double getAnomalyThreshold() {
        return anomalyThreshold;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public Instant getCalibratedAt() {
        return calibratedAt;
    }
}
