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
    name = "service_metrics",
    indexes = {
        @Index(name = "idx_service_metrics_baseline", columnList = "service_id, metric_name, unit, recorded_at DESC, id DESC")
    }
)
public class ServiceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false)
    private Double metricValue;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_batch_id")
    private IngestionBatch ingestionBatch;

    @Column(name = "scenario_label", length = 100)
    private String scenarioLabel;

    @Column(name = "ground_truth_label", length = 50)
    private String groundTruthLabel;

    @Column(length = 50)
    private String environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private EnterpriseAgent agent;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    public ServiceMetric() {
    }

    @PrePersist
    public void onPrePersist() {
        this.createdAt = Instant.now();
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MonitoredService getService() {
        return service;
    }

    public void setService(MonitoredService service) {
        this.service = service;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public IngestionBatch getIngestionBatch() {
        return ingestionBatch;
    }

    public void setIngestionBatch(IngestionBatch ingestionBatch) {
        this.ingestionBatch = ingestionBatch;
    }

    public String getScenarioLabel() {
        return scenarioLabel;
    }

    public void setScenarioLabel(String scenarioLabel) {
        this.scenarioLabel = scenarioLabel;
    }

    public String getGroundTruthLabel() {
        return groundTruthLabel;
    }

    public void setGroundTruthLabel(String groundTruthLabel) {
        this.groundTruthLabel = groundTruthLabel;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Enterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(Enterprise enterprise) {
        this.enterprise = enterprise;
    }

    public EnterpriseAgent getAgent() {
        return agent;
    }

    public void setAgent(EnterpriseAgent agent) {
        this.agent = agent;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
