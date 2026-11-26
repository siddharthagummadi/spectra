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
import jakarta.persistence.UniqueConstraint;

/**
 * Standalone monitoring agent deployed inside an Enterprise infrastructure.
 */
@Entity
@Table(
    name = "enterprise_agents",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_enterprise_agents_code", columnNames = {"enterprise_id", "agent_code"})
    },
    indexes = {
        @Index(name = "idx_enterprise_agents_enterprise_status", columnList = "enterprise_id, status"),
        @Index(name = "idx_enterprise_agents_heartbeat", columnList = "last_heartbeat_at DESC")
    }
)
public class EnterpriseAgent {

    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_SYNC_DELAYED = "SYNC_DELAYED";
    public static final String STATUS_DISCONNECTED = "DISCONNECTED";
    public static final String STATUS_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String STATUS_INTEGRATION_ERROR = "INTEGRATION_ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private Enterprise enterprise;

    @Column(name = "agent_code", nullable = false, length = 100)
    private String agentCode;

    @Column(nullable = false, length = 150)
    private String hostname;

    @Column(name = "os_info", length = 150)
    private String osInfo;

    @Column(name = "agent_version", nullable = false, length = 40)
    private String agentVersion = "1.0.0";

    @Column(nullable = false, length = 40)
    private String status = STATUS_ONLINE;

    @Column(name = "collection_interval_sec", nullable = false)
    private int collectionIntervalSec = 30;

    @Column(name = "heartbeat_interval_sec", nullable = false)
    private int heartbeatIntervalSec = 30;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_successful_telemetry_at")
    private Instant lastSuccessfulTelemetryAt;

    @Column(name = "local_buffer_count", nullable = false)
    private int localBufferCount = 0;

    @Column(name = "collection_failures", nullable = false)
    private long collectionFailures = 0L;

    @Column(name = "transmission_failures", nullable = false)
    private long transmissionFailures = 0L;

    @Column(name = "last_sync_error", length = 500)
    private String lastSyncError;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EnterpriseAgent() {
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

    public Enterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(Enterprise enterprise) {
        this.enterprise = enterprise;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsInfo() {
        return osInfo;
    }

    public void setOsInfo(String osInfo) {
        this.osInfo = osInfo;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCollectionIntervalSec() {
        return collectionIntervalSec;
    }

    public void setCollectionIntervalSec(int collectionIntervalSec) {
        this.collectionIntervalSec = collectionIntervalSec;
    }

    public int getHeartbeatIntervalSec() {
        return heartbeatIntervalSec;
    }

    public void setHeartbeatIntervalSec(int heartbeatIntervalSec) {
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Instant getLastSuccessfulTelemetryAt() {
        return lastSuccessfulTelemetryAt;
    }

    public void setLastSuccessfulTelemetryAt(Instant lastSuccessfulTelemetryAt) {
        this.lastSuccessfulTelemetryAt = lastSuccessfulTelemetryAt;
    }

    public int getLocalBufferCount() {
        return localBufferCount;
    }

    public void setLocalBufferCount(int localBufferCount) {
        this.localBufferCount = localBufferCount;
    }

    public long getCollectionFailures() {
        return collectionFailures;
    }

    public void setCollectionFailures(long collectionFailures) {
        this.collectionFailures = collectionFailures;
    }

    public long getTransmissionFailures() {
        return transmissionFailures;
    }

    public void setTransmissionFailures(long transmissionFailures) {
        this.transmissionFailures = transmissionFailures;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public void setLastSyncError(String lastSyncError) {
        this.lastSyncError = lastSyncError;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
