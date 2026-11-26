package com.spectra.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.spectra.dto.DetectionResult;

@Component
public class StatisticalAnomalyDetector {

    public static final int WINDOW_SIZE = 30;
    public static final int MIN_HISTORY = 10;
    public static final double ANOMALY_THRESHOLD = 3.0;

    public DetectionResult detect(String metricName, Double currentValue, String unit, List<Double> historicalValues) {
        if (currentValue == null || currentValue.isNaN() || currentValue.isInfinite()) {
            throw new IllegalArgumentException("Current metric value must be a valid finite number");
        }

        if (historicalValues == null || historicalValues.size() < MIN_HISTORY) {
            int count = (historicalValues != null) ? historicalValues.size() : 0;
            return DetectionResult.skipped("Insufficient historical observations: " + count + " found, minimum " + MIN_HISTORY + " required");
        }

        // Limit to window size (most recent observations up to WINDOW_SIZE)
        List<Double> window = historicalValues.stream()
                .filter(v -> v != null && !v.isNaN() && !v.isInfinite())
                .limit(WINDOW_SIZE)
                .toList();

        if (window.size() < MIN_HISTORY) {
            return DetectionResult.skipped("Insufficient valid historical observations: " + window.size() + " valid, minimum " + MIN_HISTORY + " required");
        }

        int n = window.size();
        double sum = 0.0;
        for (double val : window) {
            sum += val;
        }
        double mean = sum / n;

        double sumSquaredDiffs = 0.0;
        for (double val : window) {
            double diff = val - mean;
            sumSquaredDiffs += diff * diff;
        }

        // Sample standard deviation (Bessel's correction: N - 1)
        double sampleStdDev = Math.sqrt(sumSquaredDiffs / (n - 1));

        // Variance floor policy prevents division by zero and false positives from tiny fluctuations
        double varianceFloor = resolveVarianceFloor(metricName, unit);
        double effectiveStdDev = Math.max(sampleStdDev, varianceFloor);

        double zScore = (currentValue - mean) / effectiveStdDev;
        double absZScore = Math.abs(zScore);

        if (absZScore < ANOMALY_THRESHOLD) {
            return DetectionResult.normal(zScore, mean, effectiveStdDev);
        }

        String severity = resolveSeverity(absZScore);
        String direction = zScore >= 0 ? "POSITIVE_SPIKE" : "NEGATIVE_DROP";

        String message = String.format("Statistical anomaly detected: observed %.2f deviates from baseline mean %.2f (|Z|=%.2f, severity=%s, direction=%s)",
                currentValue, mean, absZScore, severity, direction);

        return DetectionResult.anomaly(zScore, mean, effectiveStdDev, severity, direction, message);
    }

    public DetectionResult detect(Double currentValue, String unit, List<Double> historicalValues) {
        return detect(null, currentValue, unit, historicalValues);
    }

    public double resolveVarianceFloor(String metricName, String unit) {
        String normalizedMetric = metricName != null ? metricName.trim().toUpperCase() : "";
        String normalizedUnit = unit != null ? unit.trim().toUpperCase() : "";

        if ("ERROR_RATE".equals(normalizedMetric) && "PERCENT".equals(normalizedUnit)) {
            // Provisional design choice: 0.1 percentage point floor allows detecting fractional error surges
            // (e.g. jumping from 0.05% to 0.40% yields |Z|=3.5) while suppressing tiny noise below 0.1%.
            return 0.1;
        }

        return switch (normalizedUnit) {
            case "PERCENT" -> 1.0;
            case "MILLISECONDS" -> 5.0;
            case "MEGABYTES" -> 10.0;
            default -> 1.0;
        };
    }

    public double resolveVarianceFloor(String unit) {
        return resolveVarianceFloor(null, unit);
    }

    private String resolveSeverity(double absZScore) {
        if (absZScore >= 5.0) {
            return "CRITICAL";
        }
        if (absZScore >= 4.0) {
            return "HIGH";
        }
        return "MEDIUM";
    }
}
