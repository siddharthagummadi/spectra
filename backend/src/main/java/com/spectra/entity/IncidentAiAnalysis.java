package com.spectra.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Persistent record of an AI-assisted operational intelligence analysis
 * associated with a specific operational incident.
 */
@Entity
@Table(name = "incident_ai_analyses")
public class IncidentAiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(length = 255)
    private String primaryRootCause;

    @Column(length = 100)
    private String failureCategory;

    @Column
    private Double confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String primaryEvidence;

    @Column(columnDefinition = "TEXT")
    private String rankedCausesJson;

    @Column(columnDefinition = "TEXT")
    private String contributingFactorsJson;

    @Column(columnDefinition = "TEXT")
    private String recommendedRunbooksJson;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false, length = 64)
    private String executionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public IncidentAiAnalysis() {
    }

    @PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.version == null) {
            this.version = 1;
        }
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

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrimaryRootCause() {
        return primaryRootCause;
    }

    public void setPrimaryRootCause(String primaryRootCause) {
        this.primaryRootCause = primaryRootCause;
    }

    public String getFailureCategory() {
        return failureCategory;
    }

    public void setFailureCategory(String failureCategory) {
        this.failureCategory = failureCategory;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getPrimaryEvidence() {
        return primaryEvidence;
    }

    public void setPrimaryEvidence(String primaryEvidence) {
        this.primaryEvidence = primaryEvidence;
    }

    public String getRankedCausesJson() {
        return rankedCausesJson;
    }

    public void setRankedCausesJson(String rankedCausesJson) {
        this.rankedCausesJson = rankedCausesJson;
    }

    public String getContributingFactorsJson() {
        return contributingFactorsJson;
    }

    public void setContributingFactorsJson(String contributingFactorsJson) {
        this.contributingFactorsJson = contributingFactorsJson;
    }

    public String getRecommendedRunbooksJson() {
        return recommendedRunbooksJson;
    }

    public void setRecommendedRunbooksJson(String recommendedRunbooksJson) {
        this.recommendedRunbooksJson = recommendedRunbooksJson;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
