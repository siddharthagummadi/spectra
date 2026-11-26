package com.spectra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateIntegrationStatusRequest {

    @NotBlank(message = "Status is required")
    private String status; // APPROVED, REJECTED, ACTIVE, CONFIGURED

    @Size(max = 500)
    private String reason;

    public UpdateIntegrationStatusRequest() {
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
