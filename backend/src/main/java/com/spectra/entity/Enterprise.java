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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Multi-tenant Enterprise organization registered on the Spectra platform.
 */
@Entity
@Table(
    name = "enterprises",
    indexes = {
        @Index(name = "idx_enterprises_slug", columnList = "slug", unique = true),
        @Index(name = "idx_enterprises_org_code", columnList = "organization_code", unique = true),
        @Index(name = "idx_enterprises_status", columnList = "connection_status")
    }
)
public class Enterprise {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_DISCONNECTED = "DISCONNECTED";
    public static final String STATUS_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String STATUS_SYNC_DELAYED = "SYNC_DELAYED";
    public static final String STATUS_INTEGRATION_ERROR = "INTEGRATION_ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "organization_code", nullable = false, unique = true, length = 64)
    private String organizationCode;

    @Column(nullable = false, length = 50)
    private String environment = "PRODUCTION";

    @Column(name = "connection_status", nullable = false, length = 40)
    private String connectionStatus = STATUS_PENDING;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_telemetry_at")
    private Instant lastTelemetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Enterprise() {
    }

    @PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getLastTelemetryAt() {
        return lastTelemetryAt;
    }

    public void setLastTelemetryAt(Instant lastTelemetryAt) {
        this.lastTelemetryAt = lastTelemetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
