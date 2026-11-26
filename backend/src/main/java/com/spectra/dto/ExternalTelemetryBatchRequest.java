package com.spectra.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class ExternalTelemetryBatchRequest {

    private Long serviceId;

    @Size(max = 100, message = "Service name must not exceed 100 characters")
    private String serviceName;

    @NotEmpty(message = "Metrics list cannot be empty")
    @Valid
    private List<ObservationDto> metrics = new ArrayList<>();

    public ExternalTelemetryBatchRequest() {
    }

    public ExternalTelemetryBatchRequest(Long serviceId, String serviceName, List<ObservationDto> metrics) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.metrics = metrics != null ? metrics : new ArrayList<>();
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

    public List<ObservationDto> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<ObservationDto> metrics) {
        this.metrics = metrics;
    }

    public static class ObservationDto {
        private String metricName;
        private Double metricValue;
        private Double value; // alias for metricValue
        private String unit;
        private Instant timestamp;
        private Instant recordedAt; // alias for timestamp

        public ObservationDto() {
        }

        public ObservationDto(String metricName, Double metricValue, String unit, Instant timestamp) {
            this.metricName = metricName;
            this.metricValue = metricValue;
            this.value = metricValue;
            this.unit = unit;
            this.timestamp = timestamp;
            this.recordedAt = timestamp;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }

        public Double getMetricValue() {
            return metricValue != null ? metricValue : value;
        }

        public void setMetricValue(Double metricValue) {
            this.metricValue = metricValue;
            if (this.value == null) {
                this.value = metricValue;
            }
        }

        public Double getValue() {
            return value != null ? value : metricValue;
        }

        public void setValue(Double value) {
            this.value = value;
            if (this.metricValue == null) {
                this.metricValue = value;
            }
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public Instant getTimestamp() {
            return timestamp != null ? timestamp : recordedAt;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            if (this.recordedAt == null) {
                this.recordedAt = timestamp;
            }
        }

        public Instant getRecordedAt() {
            return recordedAt != null ? recordedAt : timestamp;
        }

        public void setRecordedAt(Instant recordedAt) {
            this.recordedAt = recordedAt;
            if (this.timestamp == null) {
                this.timestamp = recordedAt;
            }
        }
    }
}
