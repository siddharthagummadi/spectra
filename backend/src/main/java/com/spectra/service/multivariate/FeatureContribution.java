package com.spectra.service.multivariate;

/**
 * Immutable record representing the mathematical contribution and DriverShare
 * of an individual metric dimension to the overall multivariate Mahalanobis anomaly score.
 */
public record FeatureContribution(
    MultivariateMetricType metricType,
    double rawValue,
    double baselineMean,
    double standardizedZ,
    double signedContribution,
    double driverSharePercent,
    int rank
) {
    public String getMetricName() {
        return metricType.getMetricName();
    }

    public String getUnit() {
        return metricType.getUnit();
    }
}
