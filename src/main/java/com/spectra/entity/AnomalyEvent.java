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

@Entity
@Table(
    name = "anomaly_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_anomaly_events_metric_id", columnNames = {"metric_id"})
    },
    indexes = {
        @Index(name = "idx_anomaly_events_service_detected", columnList = "service_id, detected_at DESC, id DESC")
    }
)
public class AnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metric_id")
    private ServiceMetric metric;

    @Column(nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private Double observedValue;

    @Column(nullable = false)
    private Double baselineMean;

    @Column(nullable = false)
    private Double baselineStdDev;

    @Column(nullable = false)
    private Double anomalyScore;

    @Column(nullable = false, length = 20)
    private String severity; // MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, length = 20)
    private String direction; // POSITIVE_SPIKE, NEGATIVE_DROP

    @Column(nullable = false, length = 20)
    private String status; // DETECTED, ACKNOWLEDGED

    @Column(nullable = false, length = 255)
    private String message;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column
    private Instant acknowledgedAt;

    @Column(length = 100)
    private String acknowledgedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public AnomalyEvent() {
    }

    @PrePersist
    public void onPrePersist() {
        this.createdAt = Instant.now();
        if (this.detectedAt == null) {
            this.detectedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "DETECTED";
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

    public ServiceMetric getMetric() {
        return metric;
    }

    public void setMetric(ServiceMetric metric) {
        this.metric = metric;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getObservedValue() {
        return observedValue;
    }

    public void setObservedValue(Double observedValue) {
        this.observedValue = observedValue;
    }

    public Double getBaselineMean() {
        return baselineMean;
    }

    public void setBaselineMean(Double baselineMean) {
        this.baselineMean = baselineMean;
    }

    public Double getBaselineStdDev() {
        return baselineStdDev;
    }

    public void setBaselineStdDev(Double baselineStdDev) {
        this.baselineStdDev = baselineStdDev;
    }

    public Double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(Double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
