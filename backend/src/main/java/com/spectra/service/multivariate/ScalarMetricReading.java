package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a single scalar metric reading ingested for a service.
 * <p>
 * Enforces non-null properties, finite numeric constraints, and physical operational bounds
 * in accordance with canonical 4-dimensional telemetry standards.
 */
public record ScalarMetricReading(
    Long serviceId,
    MultivariateMetricType metricType,
    double value,
    Instant recordedAt
) {
    public ScalarMetricReading {
        Objects.requireNonNull(serviceId, "serviceId cannot be null");
        Objects.requireNonNull(metricType, "metricType cannot be null");
        Objects.requireNonNull(recordedAt, "recordedAt cannot be null");

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new InvalidFeatureVectorException(
                "Invalid numeric value for " + metricType.getMetricName() + ": " + value + ". Must be a finite number.");
        }
        if (value < metricType.getMinPhysicalValue()) {
            throw new InvalidFeatureVectorException(
                "Physical bound violation for " + metricType.getMetricName() + ": " + value +
                " is below minimum " + metricType.getMinPhysicalValue() + " " + metricType.getUnit());
        }
        if (value > metricType.getMaxPhysicalValue()) {
            throw new InvalidFeatureVectorException(
                "Physical bound violation for " + metricType.getMetricName() + ": " + value +
                " exceeds maximum " + metricType.getMaxPhysicalValue() + " " + metricType.getUnit());
        }
    }

    public static ScalarMetricReading of(Long serviceId, String metricName, double value, Instant recordedAt) {
        MultivariateMetricType type = MultivariateMetricType.fromMetricName(metricName);
        return new ScalarMetricReading(serviceId, type, value, recordedAt);
    }
}
