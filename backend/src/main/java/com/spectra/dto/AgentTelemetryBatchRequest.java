package com.spectra.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class AgentTelemetryBatchRequest {

    @NotBlank(message = "clientBatchId is required for idempotency")
    private String clientBatchId;

    @NotEmpty(message = "samples list cannot be empty")
    @Valid
    private List<SampleDto> samples;

    public String getClientBatchId() {
        return clientBatchId;
    }

    public void setClientBatchId(String clientBatchId) {
        this.clientBatchId = clientBatchId;
    }

    public List<SampleDto> getSamples() {
        return samples;
    }

    public void setSamples(List<SampleDto> samples) {
        this.samples = samples;
    }

    public static class SampleDto {
        @NotBlank(message = "serviceName is required")
        private String serviceName;
        private String environment = "PRODUCTION";
        private String endpointUrl;
        @NotBlank(message = "metricName is required")
        private String metricName;
        private Double metricValue;
        private String unit;
        private Instant recordedAt;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
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
}
