package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Java-native 4-dimensional Mahalanobis multivariate anomaly detection engine for Spectra.
 * <p>
 * Evaluates telemetry observations against a pre-calibrated baseline model using:
 * <ol>
 *   <li>Domain-floor standardization into dimensionless space</li>
 *   <li>Cholesky-based Mahalanobis distance calculation: D_M = sqrt(z^T * Sigma^-1 * z)</li>
 *   <li>Exact signed feature attribution: c_j = z_j * (Sigma^-1 * z)_j</li>
 *   <li>Non-negative DriverShare ranking to identify root-cause drivers</li>
 * </ol>
 * <p>
 * Under strict architectural constraints, this component does not make Python calls,
 * does not recalibrate on individual telemetry requests, and never substitutes missing
 * metrics with zero.
 */
@Component
public class JavaMultivariateAnomalyDetector {

    /**
     * Default empirical percentile used for quantile ranking of training Mahalanobis distances.
     * <p>
     * NOTE: Selecting the 99th percentile (0.99) does NOT guarantee an actual 1% false-positive rate
     * in operational deployment. Finite calibration sample sizes and Ledoit-Wolf shrinkage
     * regularization alter the null distribution from idealized Chi-square(4).
     */
    public static final double DEFAULT_PERCENTILE = 0.99;

    /**
     * Theoretical lower floor for the multivariate anomaly detection threshold.
     * <p>
     * Mathematical basis:
     * 3.644 is the square root of the theoretical 99th percentile of the chi-square distribution
     * with four degrees of freedom: sqrt(chi^2_4(0.99)) = sqrt(13.2767...) ≈ 3.644.
     * <p>
     * IMPORTANT STATISTICAL AND OPERATIONAL QUALIFICATIONS:
     * <ul>
     *   <li>This theoretical threshold is derived under the assumption of an idealized multivariate
     *       standard normal distribution with known, infinite-sample covariance.</li>
     *   <li>In this implementation, covariance is estimated from finite calibration samples
     *       (minimum 10 observations) using Ledoit-Wolf shrinkage regularization towards a scaled
     *       identity target matrix with domain-floor scaling and spectral ridge adjustment.</li>
     *   <li>Consequently, the theoretical threshold does NOT guarantee an actual 1% false-positive rate.
     *       On finite samples and non-Gaussian real-world traffic, the empirical false-positive rate
     *       may diverge from 1.0%.</li>
     *   <li>Independent out-of-sample empirical validation is strictly required on production or
     *       benchmark traffic to measure and verify the actual operational false-positive rate.</li>
     *   <li>This floor serves strictly as an engineered safeguard against in-sample overfitting on small
     *       sample sizes, not as a mathematical guarantee of a 1% false-positive rate.</li>
     * </ul>
     */
    public static final double MIN_CHI_SQUARE_THRESHOLD = 3.644;

    /**
     * Fallback detection threshold when empirical quantile estimation is uncalibrated.
     * Inherits the exact statistical properties and qualifications of {@link #MIN_CHI_SQUARE_THRESHOLD}.
     */
    public static final double DEFAULT_FALLBACK_THRESHOLD = 3.644;

    private final LedoitWolfCovarianceEstimator covarianceEstimator;
    private final CholeskyPrecisionSolver precisionSolver;
    private final MultivariateAttributionEngine attributionEngine;

    public JavaMultivariateAnomalyDetector() {
        this.covarianceEstimator = new LedoitWolfCovarianceEstimator();
        this.precisionSolver = new CholeskyPrecisionSolver();
        this.attributionEngine = new MultivariateAttributionEngine();
    }

    public JavaMultivariateAnomalyDetector(
        LedoitWolfCovarianceEstimator covarianceEstimator,
        CholeskyPrecisionSolver precisionSolver,
        MultivariateAttributionEngine attributionEngine
    ) {
        this.covarianceEstimator = Objects.requireNonNull(covarianceEstimator);
        this.precisionSolver = Objects.requireNonNull(precisionSolver);
        this.attributionEngine = Objects.requireNonNull(attributionEngine);
    }

    /**
     * Evaluates a single multivariate telemetry observation against a pre-calibrated baseline model.
     *
     * @param observation validated feature vector (non-null, finite, within physical bounds)
     * @param model pre-calibrated service baseline model
     * @return structured MultivariateDetectionResult
     */
    public MultivariateDetectionResult detect(MultivariateFeatureVector observation, MultivariateCalibrationModel model) {
        if (observation == null) {
            throw new InvalidFeatureVectorException("Observation feature vector cannot be null");
        }
        if (model == null) {
            throw new IllegalArgumentException("Multivariate calibration model cannot be null");
        }

        double[] rawValues = observation.toArray();
        double[] mean = model.getBaselineMean();
        double[] sigmaFloors = model.getSigmaFloors();
        double[][] choleskyL = model.getCholeskyL();
        double threshold = model.getAnomalyThreshold();

        int p = MultivariateMetricType.DIMENSION;
        double[] z = new double[p];
        for (int j = 0; j < p; j++) {
            z[j] = (rawValues[j] - mean[j]) / sigmaFloors[j];
        }

        // Solve for v = Sigma^-1 * z using Cholesky factor L
        double[] v = precisionSolver.solvePrecisionVector(choleskyL, z);

        // Compute squared Mahalanobis distance D_M^2 = z^T * v
        double squaredDistance = 0.0;
        for (int j = 0; j < p; j++) {
            squaredDistance += z[j] * v[j];
        }

        if (squaredDistance < 0.0) {
            squaredDistance = 0.0; // clamp minute negative precision drift
        }
        double mahalanobisDistance = Math.sqrt(squaredDistance);

        // Compute signed feature contributions and DriverShares
        MultivariateAttributionEngine.AttributionResult attribution =
            attributionEngine.computeAttributions(rawValues, mean, z, v);

        MultivariateMetricType primaryDriver = attribution.primaryDriver();
        double primaryDriverShare = attribution.rankedContributions().isEmpty()
            ? 0.0
            : attribution.rankedContributions().get(0).driverSharePercent();

        if (mahalanobisDistance <= threshold) {
            String normalMsg = String.format(
                "Normal multivariate observation: D_M=%.2f within threshold %.2f (primary driver=%s, share=%.1f%%)",
                mahalanobisDistance, threshold, primaryDriver.getMetricName(), primaryDriverShare);

            return MultivariateDetectionResult.normal(
                mahalanobisDistance,
                squaredDistance,
                threshold,
                attribution.rankedContributions(),
                primaryDriver,
                normalMsg
            );
        }

        String severity = resolveSeverity(mahalanobisDistance, threshold);
        String anomalyMsg = String.format(
            "Multivariate anomaly detected: Mahalanobis distance D_M=%.2f exceeds threshold %.2f (primary driver=%s, share=%.1f%%)",
            mahalanobisDistance, threshold, primaryDriver.getMetricName(), primaryDriverShare);

        return MultivariateDetectionResult.anomaly(
            mahalanobisDistance,
            squaredDistance,
            threshold,
            attribution.rankedContributions(),
            primaryDriver,
            severity,
            anomalyMsg
        );
    }

    /**
     * Calibrates a new MultivariateCalibrationModel from normal operational training observations.
     * <p>
     * <b>Operational Notice on False-Positive Rate:</b>
     * Although the empirical quantile calculation ranks training Mahalanobis distances and enforces
     * the theoretical floor {@link #MIN_CHI_SQUARE_THRESHOLD} (3.644), this does not guarantee an
     * actual 1% false-positive rate in operational deployment due to finite sample size estimation
     * and Ledoit-Wolf covariance shrinkage. Independent out-of-sample validation on production or
     * benchmark traffic is required to measure empirical false-positive behavior.
     *
     * @param serviceId ID of the monitored service
     * @param observations normal training observations (minimum 10 required)
     * @param percentile empirical quantile for anomaly threshold (e.g. 0.99)
     * @return fully compiled and validated MultivariateCalibrationModel
     */
    public MultivariateCalibrationModel calibrate(Long serviceId, List<MultivariateFeatureVector> observations, double percentile) {
        if (observations == null || observations.size() < LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS) {
            int count = (observations != null) ? observations.size() : 0;
            throw new NumericalInstabilityException(
                "Insufficient observations for multivariate calibration: " + count + " provided, minimum " +
                LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS + " required");
        }

        int n = observations.size();
        int p = MultivariateMetricType.DIMENSION;
        double[][] X = new double[n][p];
        for (int i = 0; i < n; i++) {
            X[i] = observations.get(i).toArray();
        }

        // 1. Regularized covariance estimation via Ledoit-Wolf with domain floors & spectral ridge
        LedoitWolfCovarianceEstimator.CovarianceEstimate est = covarianceEstimator.estimate(X);

        // 2. Cholesky factorisation and precision matrix calculation
        CholeskyPrecisionSolver.CholeskyResult chol = precisionSolver.solve(est.finalCovarianceZ());

        // 3. Calculate empirical Mahalanobis distance threshold on training set
        double[] trainingDistances = new double[n];
        for (int i = 0; i < n; i++) {
            double[] z = new double[p];
            for (int j = 0; j < p; j++) {
                z[j] = (X[i][j] - est.mean()[j]) / est.sigmaFloors()[j];
            }
            double d2 = precisionSolver.mahalanobisDistanceSquared(chol.L(), z);
            trainingDistances[i] = Math.sqrt(Math.max(0.0, d2));
        }

        Arrays.sort(trainingDistances);
        double quantile = (percentile > 0.0 && percentile < 1.0) ? percentile : DEFAULT_PERCENTILE;
        int rankIndex = (int) Math.ceil(quantile * n) - 1;
        rankIndex = Math.max(0, Math.min(n - 1, rankIndex));
        double empiricalThreshold = trainingDistances[rankIndex];

        // Ensure threshold is never below theoretical chi-square lower bound
        double finalThreshold = Math.max(empiricalThreshold, MIN_CHI_SQUARE_THRESHOLD);

        return new MultivariateCalibrationModel(
            serviceId,
            est.mean(),
            est.sigmaFloors(),
            est.finalCovarianceZ(),
            chol.precisionMatrix(),
            chol.L(),
            est.lambdaStar(),
            est.epsilonZ(),
            finalThreshold,
            n,
            Instant.now()
        );
    }

    /**
     * Calibrate using default 99th percentile threshold.
     */
    public MultivariateCalibrationModel calibrate(Long serviceId, List<MultivariateFeatureVector> observations) {
        return calibrate(serviceId, observations, DEFAULT_PERCENTILE);
    }

    private String resolveSeverity(double distance, double threshold) {
        double ratio = distance / threshold;
        if (ratio >= 2.0) {
            return "CRITICAL";
        }
        if (ratio >= 1.5) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    public LedoitWolfCovarianceEstimator getCovarianceEstimator() {
        return covarianceEstimator;
    }

    public CholeskyPrecisionSolver getPrecisionSolver() {
        return precisionSolver;
    }

    public MultivariateAttributionEngine getAttributionEngine() {
        return attributionEngine;
    }
}
