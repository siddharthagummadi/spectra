package com.spectra.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ExternalTelemetryIngestionResponse {

    private String status; // SUCCESS, PARTIAL_SUCCESS, FAILED
    private int totalReceived;
    private int accepted;
    private int rejected;
    private Long serviceId;
    private String serviceName;
    private Instant processedAt;
    private List<String> errors = new ArrayList<>();

    public ExternalTelemetryIngestionResponse() {
        this.processedAt = Instant.now();
    }

    public ExternalTelemetryIngestionResponse(String status, int totalReceived, int accepted, int rejected, Long serviceId, String serviceName) {
        this.status = status;
        this.totalReceived = totalReceived;
        this.accepted = accepted;
        this.rejected = rejected;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.processedAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(int totalReceived) {
        this.totalReceived = totalReceived;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
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

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
