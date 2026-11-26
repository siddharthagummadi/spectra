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

/**
 * Periodic liveness and health telemetry reported by an EnterpriseAgent.
 */
@Entity
@Table(
    name = "agent_heartbeats",
    indexes = {
        @Index(name = "idx_agent_heartbeats_agent_received", columnList = "agent_id, received_at DESC"),
        @Index(name = "idx_agent_heartbeats_enterprise_received", columnList = "enterprise_id, received_at DESC")
    }
)
public class AgentHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private EnterpriseAgent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private Enterprise enterprise;

    @Column(name = "agent_version", nullable = false, length = 40)
    private String agentVersion;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "local_buffer_count", nullable = false)
    private int localBufferCount;

    @Column(name = "collection_failures", nullable = false)
    private long collectionFailures;

    @Column(name = "transmission_failures", nullable = false)
    private long transmissionFailures;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(length = 500)
    private String message;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public AgentHeartbeat() {
    }

    @PrePersist
    public void onPrePersist() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EnterpriseAgent getAgent() {
        return agent;
    }

    public void setAgent(EnterpriseAgent agent) {
        this.agent = agent;
    }

    public Enterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(Enterprise enterprise) {
        this.enterprise = enterprise;
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

    public Instant getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) {
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
