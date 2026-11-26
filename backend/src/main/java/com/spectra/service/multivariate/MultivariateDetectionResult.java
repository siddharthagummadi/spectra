package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result record returned by the multivariate anomaly detector.
 */
public record MultivariateDetectionResult(
    boolean isAnomaly,
    double mahalanobisDistance,
    double squaredMahalanobisDistance,
    double threshold,
    List<FeatureContribution> featureContributions,
    MultivariateMetricType primaryDriver,
    String severity,
    String message,
    Instant evaluatedAt
) {
    public MultivariateDetectionResult {
        if (featureContributions != null) {
            featureContributions = Collections.unmodifiableList(featureContributions);
        } else {
            featureContributions = Collections.emptyList();
        }
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }

    public static MultivariateDetectionResult normal(
        double distance,
        double squaredDistance,
        double threshold,
        List<FeatureContribution> contributions,
        MultivariateMetricType primaryDriver,
        String message
    ) {
        return new MultivariateDetectionResult(
            false,
            distance,
            squaredDistance,
            threshold,
            contributions,
            primaryDriver,
            "NONE",
            message != null ? message : "Normal observation within expected multivariate baseline",
            Instant.now()
        );
    }

    public static MultivariateDetectionResult anomaly(
        double distance,
        double squaredDistance,
        double threshold,
        List<FeatureContribution> contributions,
        MultivariateMetricType primaryDriver,
        String severity,
        String message
    ) {
        return new MultivariateDetectionResult(
            true,
            distance,
            squaredDistance,
            threshold,
            contributions,
            primaryDriver,
            severity != null ? severity : "MEDIUM",
            message,
            Instant.now()
        );
    }
}
