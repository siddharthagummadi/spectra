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
 * Rotatable, revocable per-agent authentication credential isolated from user credentials.
 */
@Entity
@Table(
    name = "agent_credentials",
    indexes = {
        @Index(name = "idx_agent_credentials_key_id", columnList = "key_id", unique = true),
        @Index(name = "idx_agent_credentials_agent_status", columnList = "agent_id, status")
    }
)
public class AgentCredential {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ROTATING = "ROTATING";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private EnterpriseAgent agent;

    @Column(name = "key_id", nullable = false, unique = true, length = 64)
    private String keyId;

    @Column(name = "secret_hash", nullable = false, length = 128)
    private String secretHash;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public AgentCredential() {
    }

    @PrePersist
    public void onPrePersist() {
        if (this.issuedAt == null) {
            this.issuedAt = Instant.now();
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

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
