package com.spectra.service.multivariate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a 4-dimensional telemetry feature vector for a service.
 * <p>
 * Enforces strict physical bounds, finite numeric checks, non-null guarantees,
 * and staleness detection. Missing metrics are NEVER silently replaced with zero.
 */
public final class MultivariateFeatureVector {

    public static final Duration DEFAULT_MAX_STALENESS = Duration.ofMinutes(15);
    public static final Duration MAX_FUTURE_DRIFT = Duration.ofSeconds(10);

    private final double cpuUsage;
    private final double memoryUsage;
    private final double responseTime;
    private final double errorRate;
    private final Instant recordedAt;

    private MultivariateFeatureVector(double cpuUsage, double memoryUsage, double responseTime,
                                     double errorRate, Instant recordedAt) {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.responseTime = responseTime;
        this.errorRate = errorRate;
        this.recordedAt = recordedAt;
    }

    /**
     * Factory method creating and validating a feature vector at the specified timestamp.
     */
    public static MultivariateFeatureVector of(Double cpuUsage, Double memoryUsage, Double responseTime,
                                               Double errorRate, Instant recordedAt) {
        validateMetric("CPU_USAGE", cpuUsage, MultivariateMetricType.CPU_USAGE);
        validateMetric("MEMORY_USAGE", memoryUsage, MultivariateMetricType.MEMORY_USAGE);
        validateMetric("RESPONSE_TIME", responseTime, MultivariateMetricType.RESPONSE_TIME);
        validateMetric("ERROR_RATE", errorRate, MultivariateMetricType.ERROR_RATE);

        if (recordedAt == null) {
            throw new InvalidFeatureVectorException("Feature vector recordedAt timestamp cannot be null");
        }

        return new MultivariateFeatureVector(cpuUsage, memoryUsage, responseTime, errorRate, recordedAt);
    }

    /**
     * Factory method with current timestamp.
     */
    public static MultivariateFeatureVector of(Double cpuUsage, Double memoryUsage, Double responseTime,
                                               Double errorRate) {
        return of(cpuUsage, memoryUsage, responseTime, errorRate, Instant.now());
    }

    /**
     * Factory method from double array [cpu, mem, rt, err].
     */
    public static MultivariateFeatureVector fromArray(double[] values, Instant recordedAt) {
        if (values == null) {
            throw new InvalidFeatureVectorException("Feature vector values array cannot be null");
        }
        if (values.length != MultivariateMetricType.DIMENSION) {
            throw new InvalidFeatureVectorException(
                "Invalid feature vector dimension: " + values.length +
                ". Exactly " + MultivariateMetricType.DIMENSION + " features required (CPU, MEM, RT, ERR)");
        }
        return of(values[0], values[1], values[2], values[3], recordedAt != null ? recordedAt : Instant.now());
    }

    private static void validateMetric(String name, Double value, MultivariateMetricType type) {
        if (value == null) {
            throw new InvalidFeatureVectorException(
                "Incomplete feature vector: missing metric '" + name + "'. Silently substituting zero is prohibited.");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new InvalidFeatureVectorException(
                "Invalid numeric value for '" + name + "': " + value + ". Must be a finite number.");
        }
        if (value < type.getMinPhysicalValue()) {
            throw new InvalidFeatureVectorException(
                "Physical bound violation for '" + name + "': " + value +
                " is below minimum allowed " + type.getMinPhysicalValue() + " " + type.getUnit());
        }
        if (value > type.getMaxPhysicalValue()) {
            throw new InvalidFeatureVectorException(
                "Physical bound violation for '" + name + "': " + value +
                " exceeds maximum allowed " + type.getMaxPhysicalValue() + " " + type.getUnit());
        }
    }

    /**
     * Validates that this feature vector is not stale relative to a reference time.
     */
    public void validateStaleness(Instant referenceTime, Duration maxStaleness) {
        Instant ref = (referenceTime != null) ? referenceTime : Instant.now();
        Duration staleness = (maxStaleness != null) ? maxStaleness : DEFAULT_MAX_STALENESS;

        if (recordedAt.isAfter(ref.plus(MAX_FUTURE_DRIFT))) {
            throw new InvalidFeatureVectorException(
                "Feature vector timestamp is in the future: " + recordedAt + " (reference time: " + ref + ")");
        }
        if (recordedAt.isBefore(ref.minus(staleness))) {
            throw new InvalidFeatureVectorException(
                "Stale feature vector: recorded at " + recordedAt + ", which exceeds maximum staleness window of " +
                staleness.toMinutes() + " minutes (reference time: " + ref + ")");
        }
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public double get(MultivariateMetricType type) {
        return switch (type) {
            case CPU_USAGE -> cpuUsage;
            case MEMORY_USAGE -> memoryUsage;
            case RESPONSE_TIME -> responseTime;
            case ERROR_RATE -> errorRate;
        };
    }

    public double[] toArray() {
        return new double[] { cpuUsage, memoryUsage, responseTime, errorRate };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultivariateFeatureVector that = (MultivariateFeatureVector) o;
        return Double.compare(that.cpuUsage, cpuUsage) == 0 &&
               Double.compare(that.memoryUsage, memoryUsage) == 0 &&
               Double.compare(that.responseTime, responseTime) == 0 &&
               Double.compare(that.errorRate, errorRate) == 0 &&
               Objects.equals(recordedAt, that.recordedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpuUsage, memoryUsage, responseTime, errorRate, recordedAt);
    }

    @Override
    public String toString() {
        return String.format("MultivariateFeatureVector[CPU=%.2f%%, MEM=%.2f%%, RT=%.2fms, ERR=%.2f%%, at=%s]",
            cpuUsage, memoryUsage, responseTime, errorRate, recordedAt);
    }
}
