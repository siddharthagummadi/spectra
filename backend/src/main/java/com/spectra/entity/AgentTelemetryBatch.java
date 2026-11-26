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
import jakarta.persistence.UniqueConstraint;

/**
 * Idempotency ledger ensuring retried agent telemetry batches are not processed twice.
 */
@Entity
@Table(
    name = "agent_telemetry_batches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_telemetry_batch_idempotency", columnNames = {"agent_id", "client_batch_id"})
    },
    indexes = {
        @Index(name = "idx_agent_telemetry_batches_enterprise", columnList = "enterprise_id, received_at DESC")
    }
)
public class AgentTelemetryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private EnterpriseAgent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private Enterprise enterprise;

    @Column(name = "client_batch_id", nullable = false, length = 100)
    private String clientBatchId;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public AgentTelemetryBatch() {
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

    public String getClientBatchId() {
        return clientBatchId;
    }

    public void setClientBatchId(String clientBatchId) {
        this.clientBatchId = clientBatchId;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
