package com.spectra.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ExternalTelemetryRequest {

    private Long serviceId;

    @Size(max = 100, message = "Service name must not exceed 100 characters")
    private String serviceName;

    @NotBlank(message = "Metric name is required")
    @Size(max = 100, message = "Metric name must not exceed 100 characters")
    private String metricName;

    @NotNull(message = "Metric value is required")
    private Double metricValue;

    @Size(max = 20, message = "Unit must not exceed 20 characters")
    private String unit;

    private Instant recordedAt;

    public ExternalTelemetryRequest() {
    }

    public ExternalTelemetryRequest(Long serviceId, String serviceName, String metricName, Double metricValue, String unit, Instant recordedAt) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.unit = unit;
        this.recordedAt = recordedAt;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
