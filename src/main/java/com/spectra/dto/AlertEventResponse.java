package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.AlertEvent;

public record AlertEventResponse(
        Long id,
        Long ruleId,
        String ruleName,
        Long serviceId,
        String serviceName,
        String metricName,
        String unit,
        Double triggerValue,
        Double thresholdValue,
        String severity,
        String status,
        String message,
        Instant triggeredAt,
        Instant resolvedAt,
        Double resolvedValue,
        String ownerEmail) {

    public static AlertEventResponse from(AlertEvent event) {
        String ownerEmail = (event.getService() != null && event.getService().getUser() != null)
                ? event.getService().getUser().getEmail() : null;
        return new AlertEventResponse(
                event.getId(),
                event.getRule().getId(),
                event.getRule().getRuleName(),
                event.getService().getId(),
                event.getService().getName(),
                event.getMetricName(),
                event.getUnit(),
                event.getTriggerValue(),
                event.getThresholdValue(),
                event.getSeverity(),
                event.getStatus(),
                event.getMessage(),
                event.getTriggeredAt(),
                event.getResolvedAt(),
                event.getResolvedValue(),
                ownerEmail);
    }
}
