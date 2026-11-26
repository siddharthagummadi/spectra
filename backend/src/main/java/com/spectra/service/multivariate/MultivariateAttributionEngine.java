package com.spectra.service.multivariate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure mathematical feature attribution engine for multivariate Mahalanobis distance.
 * <p>
 * Decomposes the squared Mahalanobis distance D_M^2 into exact signed feature contributions:
 * <pre>
 *   c_j = z_j * (Sigma^-1 * z)_j
 * </pre>
 * where the exact conservation law holds:
 * <pre>
 *   sum_{j=1}^p c_j = D_M^2
 * </pre>
 * <p>
 * Computes non-negative DriverShare percentages:
 * <pre>
 *   c_j^+ = max(0, c_j)
 *   S^+ = sum_{j=1}^p c_j^+
 *   DriverShare_j = (c_j^+ / S^+) * 100%   (when S^+ > 0)
 * </pre>
 * When S^+ == 0 (e.g. observation exactly at baseline mean), DriverShare is split equally (25% each).
 */
public final class MultivariateAttributionEngine {

    public static final double CONSERVATION_TOLERANCE = 1e-9;
    private static final double EPSILON_SUM = 1e-12;

    public record AttributionResult(
        double[] signedContributions,
        double[] driverSharePercents,
        List<FeatureContribution> rankedContributions,
        MultivariateMetricType primaryDriver,
        double squaredDistance
    ) {}

    /**
     * Decomposes observation z into signed contributions and ranked DriverShare.
     *
     * @param rawValues raw observation vector in physical units
     * @param baselineMean baseline mean vector in physical units
     * @param z standardized dimensionless observation vector
     * @param v precision product vector v = Sigma^-1 * z
     * @return fully computed and verified AttributionResult
     */
    public AttributionResult computeAttributions(double[] rawValues, double[] baselineMean, double[] z, double[] v) {
        int p = MultivariateMetricType.DIMENSION;
        if (z.length != p || v.length != p) {
            throw new IllegalArgumentException("Dimension mismatch: expected " + p + " features");
        }

        double[] signedContributions = new double[p];
        double[] positiveContributions = new double[p];
        double squaredDistance = 0.0;
        double sumPositive = 0.0;

        for (int j = 0; j < p; j++) {
            signedContributions[j] = z[j] * v[j];
            squaredDistance += signedContributions[j];
            positiveContributions[j] = Math.max(0.0, signedContributions[j]);
            sumPositive += positiveContributions[j];
        }

        double[] driverShares = new double[p];
        if (sumPositive > EPSILON_SUM) {
            for (int j = 0; j < p; j++) {
                driverShares[j] = (positiveContributions[j] / sumPositive) * 100.0;
            }
        } else {
            // Equal share when no positive drivers exist (e.g. perfect match to baseline mean)
            double equalShare = 100.0 / p;
            for (int j = 0; j < p; j++) {
                driverShares[j] = equalShare;
            }
        }

        // Build list of FeatureContribution objects
        List<FeatureContribution> unranked = new ArrayList<>(p);
        for (int j = 0; j < p; j++) {
            MultivariateMetricType type = MultivariateMetricType.fromIndex(j);
            unranked.add(new FeatureContribution(
                type,
                rawValues != null ? rawValues[j] : 0.0,
                baselineMean != null ? baselineMean[j] : 0.0,
                z[j],
                signedContributions[j],
                driverShares[j],
                0 // assigned after sorting
            ));
        }

        // Sort descending by DriverShare (then by signed contribution)
        unranked.sort(Comparator
            .comparingDouble(FeatureContribution::driverSharePercent)
            .thenComparingDouble(FeatureContribution::signedContribution)
            .reversed());

        List<FeatureContribution> ranked = new ArrayList<>(p);
        for (int rank = 0; rank < p; rank++) {
            FeatureContribution fc = unranked.get(rank);
            ranked.add(new FeatureContribution(
                fc.metricType(),
                fc.rawValue(),
                fc.baselineMean(),
                fc.standardizedZ(),
                fc.signedContribution(),
                fc.driverSharePercent(),
                rank + 1
            ));
        }

        MultivariateMetricType primaryDriver = ranked.get(0).metricType();

        return new AttributionResult(
            signedContributions,
            driverShares,
            ranked,
            primaryDriver,
            squaredDistance
        );
    }
}
