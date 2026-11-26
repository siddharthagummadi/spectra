package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.AlertRule;

public record AlertRuleResponse(
        Long id,
        Long serviceId,
        String serviceName,
        String ruleName,
        String metricName,
        String unit,
        String comparator,
        Double thresholdValue,
        String severity,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String ownerEmail) {

    public static AlertRuleResponse from(AlertRule rule) {
        String ownerEmail = (rule.getService() != null && rule.getService().getUser() != null)
                ? rule.getService().getUser().getEmail() : null;
        return new AlertRuleResponse(
                rule.getId(),
                rule.getService().getId(),
                rule.getService().getName(),
                rule.getRuleName(),
                rule.getMetricName(),
                rule.getUnit(),
                rule.getComparator(),
                rule.getThresholdValue(),
                rule.getSeverity(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                ownerEmail);
    }
}
