package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.AnomalyEvent;

public record AnomalyEventResponse(
        Long id,
        Long serviceId,
        String serviceName,
        Long metricId,
        String metricName,
        String unit,
        Double observedValue,
        Double baselineMean,
        Double baselineStdDev,
        Double anomalyScore,
        String severity,
        String direction,
        String status,
        String message,
        Instant detectedAt,
        Instant acknowledgedAt,
        String acknowledgedBy,
        String ownerEmail) {

    public static AnomalyEventResponse from(AnomalyEvent event) {
        String ownerEmail = (event.getService() != null && event.getService().getUser() != null)
                ? event.getService().getUser().getEmail() : null;
        Long metricId = event.getMetric() != null ? event.getMetric().getId() : null;
        return new AnomalyEventResponse(
                event.getId(),
                event.getService().getId(),
                event.getService().getName(),
                metricId,
                event.getMetricName(),
                event.getUnit(),
                event.getObservedValue(),
                event.getBaselineMean(),
                event.getBaselineStdDev(),
                event.getAnomalyScore(),
                event.getSeverity(),
                event.getDirection(),
                event.getStatus(),
                event.getMessage(),
                event.getDetectedAt(),
                event.getAcknowledgedAt(),
                event.getAcknowledgedBy(),
                ownerEmail);
    }
}
