package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.ServiceMetric;

public record MetricResponse(
        Long id,
        Long serviceId,
        String serviceName,
        String metricName,
        Double metricValue,
        String unit,
        Instant recordedAt,
        Instant createdAt) {

    public static MetricResponse from(ServiceMetric metric) {
        return new MetricResponse(
                metric.getId(),
                metric.getService().getId(),
                metric.getService().getName(),
                metric.getMetricName(),
                metric.getMetricValue(),
                metric.getUnit(),
                metric.getRecordedAt(),
                metric.getCreatedAt());
    }
}
