package com.spectra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ManualTelemetryRequest {

    private Long serviceId;
    private String serviceName;

    @NotBlank(message = "Metric name is required")
    private String metricName;

    @NotNull(message = "Metric value is required")
    private Double metricValue;

    private String unit;
    private String timestamp;
    private String environment;
    private String scenarioLabel;
    private String groundTruthLabel;

    public ManualTelemetryRequest() {
    }

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getScenarioLabel() { return scenarioLabel; }
    public void setScenarioLabel(String scenarioLabel) { this.scenarioLabel = scenarioLabel; }

    public String getGroundTruthLabel() { return groundTruthLabel; }
    public void setGroundTruthLabel(String groundTruthLabel) { this.groundTruthLabel = groundTruthLabel; }
}
