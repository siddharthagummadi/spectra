package com.spectra.service.multivariate;

import java.time.Duration;

/**
 * Canonical 4-dimensional metric types for multivariate anomaly detection in Spectra.
 * <p>
 * Defines the canonical dimensional ordering, measurement units, domain-floor standard
 * deviations, live operational freshness limits, and physical operational bounds for enterprise microservices.
 */
public enum MultivariateMetricType {
    CPU_USAGE(0, "CPU_USAGE", "PERCENT", 1.0, 0.0, 100.0, Duration.ofSeconds(180)),
    MEMORY_USAGE(1, "MEMORY_USAGE", "PERCENT", 1.0, 0.0, 100.0, Duration.ofSeconds(300)),
    RESPONSE_TIME(2, "RESPONSE_TIME", "MILLISECONDS", 5.0, 0.0, 60_000.0, Duration.ofSeconds(180)),
    ERROR_RATE(3, "ERROR_RATE", "PERCENT", 0.1, 0.0, 100.0, Duration.ofSeconds(180));

    public static final int DIMENSION = 4;

    private final int index;
    private final String metricName;
    private final String unit;
    private final double sigmaFloor;
    private final double minPhysicalValue;
    private final double maxPhysicalValue;
    private final Duration freshnessLimit;

    MultivariateMetricType(int index, String metricName, String unit, double sigmaFloor,
                           double minPhysicalValue, double maxPhysicalValue, Duration freshnessLimit) {
        this.index = index;
        this.metricName = metricName;
        this.unit = unit;
        this.sigmaFloor = sigmaFloor;
        this.minPhysicalValue = minPhysicalValue;
        this.maxPhysicalValue = maxPhysicalValue;
        this.freshnessLimit = freshnessLimit;
    }

    public int getIndex() {
        return index;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getUnit() {
        return unit;
    }

    public double getSigmaFloor() {
        return sigmaFloor;
    }

    public double getMinPhysicalValue() {
        return minPhysicalValue;
    }

    public double getMaxPhysicalValue() {
        return maxPhysicalValue;
    }

    public Duration getFreshnessLimit() {
        return freshnessLimit;
    }

    public static double[] getSigmaFloors() {
        return new double[] {
            CPU_USAGE.sigmaFloor,
            MEMORY_USAGE.sigmaFloor,
            RESPONSE_TIME.sigmaFloor,
            ERROR_RATE.sigmaFloor
        };
    }

    public static MultivariateMetricType fromIndex(int index) {
        return switch (index) {
            case 0 -> CPU_USAGE;
            case 1 -> MEMORY_USAGE;
            case 2 -> RESPONSE_TIME;
            case 3 -> ERROR_RATE;
            default -> throw new IllegalArgumentException(
                "Invalid metric dimension index: " + index + ". Expected 0..3 for 4D multivariate detection");
        };
    }

    public static MultivariateMetricType fromMetricName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Metric name cannot be null or blank");
        }
        String trimmed = rawName.trim().toUpperCase();
        return switch (trimmed) {
            case "CPU_USAGE" -> CPU_USAGE;
            case "MEMORY_USAGE" -> MEMORY_USAGE;
            case "RESPONSE_TIME" -> RESPONSE_TIME;
            case "ERROR_RATE" -> ERROR_RATE;
            default -> throw new IllegalArgumentException(
                "Unsupported multivariate metric: " + rawName +
                ". Supported canonical metrics are CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, ERROR_RATE");
        };
    }
}
