package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.EnterpriseIntegration;

public class EnterpriseIntegrationResponse {

    private Long id;
    private String enterpriseName;
    private String organizationName;
    private String environment;
    private String integrationType;
    private String apiEndpoint;
    private String contactEmail;
    private String contactName;
    private String connectionPurpose;
    private String status;
    private String statusReason;
    private String requestedByEmail;
    private String reviewedByEmail;
    private boolean hasConfiguredCredentials;
    private Instant createdAt;
    private Instant reviewedAt;

    public EnterpriseIntegrationResponse() {
    }

    public static EnterpriseIntegrationResponse from(EnterpriseIntegration entity) {
        EnterpriseIntegrationResponse res = new EnterpriseIntegrationResponse();
        res.setId(entity.getId());
        res.setEnterpriseName(entity.getEnterpriseName());
        res.setOrganizationName(entity.getOrganizationName());
        res.setEnvironment(entity.getEnvironment());
        res.setIntegrationType(entity.getIntegrationType());
        res.setApiEndpoint(entity.getApiEndpoint());
        res.setContactEmail(entity.getContactEmail());
        res.setContactName(entity.getContactName());
        res.setConnectionPurpose(entity.getConnectionPurpose());
        res.setStatus(entity.getStatus());
        res.setStatusReason(entity.getStatusReason());
        if (entity.getRequestedBy() != null) {
            res.setRequestedByEmail(entity.getRequestedBy().getEmail());
        }
        if (entity.getReviewedBy() != null) {
            res.setReviewedByEmail(entity.getReviewedBy().getEmail());
        }
        res.setHasConfiguredCredentials(entity.getEncryptedCredentials() != null && !entity.getEncryptedCredentials().isEmpty());
        res.setCreatedAt(entity.getCreatedAt());
        res.setReviewedAt(entity.getReviewedAt());
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getIntegrationType() { return integrationType; }
    public void setIntegrationType(String integrationType) { this.integrationType = integrationType; }

    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getConnectionPurpose() { return connectionPurpose; }
    public void setConnectionPurpose(String connectionPurpose) { this.connectionPurpose = connectionPurpose; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public String getRequestedByEmail() { return requestedByEmail; }
    public void setRequestedByEmail(String requestedByEmail) { this.requestedByEmail = requestedByEmail; }

    public String getReviewedByEmail() { return reviewedByEmail; }
    public void setReviewedByEmail(String reviewedByEmail) { this.reviewedByEmail = reviewedByEmail; }

    public boolean isHasConfiguredCredentials() { return hasConfiguredCredentials; }
    public void setHasConfiguredCredentials(boolean hasConfiguredCredentials) { this.hasConfiguredCredentials = hasConfiguredCredentials; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
