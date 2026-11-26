package com.spectra.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "enterprise_integrations",
    indexes = {
        @Index(name = "idx_enterprise_integrations_status", columnList = "status"),
        @Index(name = "idx_enterprise_integrations_user", columnList = "requested_by_id, created_at DESC")
    }
)
public class EnterpriseIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enterprise_name", nullable = false, length = 150)
    private String enterpriseName;

    @Column(name = "organization_name", nullable = false, length = 150)
    private String organizationName;

    @Column(nullable = false, length = 50)
    private String environment; // PRODUCTION, STAGING, DEVELOPMENT

    @Column(name = "integration_type", nullable = false, length = 50)
    private String integrationType; // DATADOG, PROMETHEUS, NEW_RELIC, CLOUDWATCH, CUSTOM_WEBHOOK, REST_API

    @Column(name = "api_endpoint", nullable = false, length = 500)
    private String apiEndpoint;

    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "connection_purpose", length = 1000)
    private String connectionPurpose;

    @Column(nullable = false, length = 30)
    private String status = "PENDING_APPROVAL"; // PENDING_APPROVAL, APPROVED, REJECTED, ACTIVE, CONFIGURED

    @Column(name = "encrypted_credentials", length = 500)
    private String encryptedCredentials;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public EnterpriseIntegration() {
    }

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getIntegrationType() {
        return integrationType;
    }

    public void setIntegrationType(String integrationType) {
        this.integrationType = integrationType;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getConnectionPurpose() {
        return connectionPurpose;
    }

    public void setConnectionPurpose(String connectionPurpose) {
        this.connectionPurpose = connectionPurpose;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEncryptedCredentials() {
        return encryptedCredentials;
    }

    public void setEncryptedCredentials(String encryptedCredentials) {
        this.encryptedCredentials = encryptedCredentials;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(User requestedBy) {
        this.requestedBy = requestedBy;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Enterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(Enterprise enterprise) {
        this.enterprise = enterprise;
    }
}
