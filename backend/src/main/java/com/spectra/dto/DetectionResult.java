package com.spectra.dto;

public record DetectionResult(
        boolean isAnomaly,
        Double anomalyScore,
        Double baselineMean,
        Double baselineStdDev,
        String severity,
        String direction,
        String message) {

    public static DetectionResult skipped(String reason) {
        return new DetectionResult(false, 0.0, 0.0, 0.0, null, null, reason);
    }

    public static DetectionResult normal(Double score, Double mean, Double stdDev) {
        return new DetectionResult(false, score, mean, stdDev, null, null, "Normal observation within expected baseline");
    }

    public static DetectionResult anomaly(
            Double score,
            Double mean,
            Double stdDev,
            String severity,
            String direction,
            String message) {
        return new DetectionResult(true, score, mean, stdDev, severity, direction, message);
    }
}
