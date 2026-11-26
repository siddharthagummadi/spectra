package com.spectra.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "evaluation_experiments",
    indexes = {
        @Index(name = "idx_eval_experiments_algorithm", columnList = "algorithm, created_at DESC"),
        @Index(name = "idx_eval_experiments_created_at", columnList = "created_at DESC")
    }
)
public class EvaluationExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experiment_name", nullable = false, length = 150)
    private String experimentName;

    @Column(name = "dataset_name", nullable = false, length = 150)
    private String datasetName;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(nullable = false, length = 50)
    private String algorithm; // STATIC_THRESHOLD, STATISTICAL_ZSCORE, SPECTRA_AI

    @Column(name = "total_samples", nullable = false)
    private int totalSamples;

    @Column(nullable = false)
    private int tp; // True Positives

    @Column(nullable = false)
    private int tn; // True Negatives

    @Column(nullable = false)
    private int fp; // False Positives

    @Column(nullable = false)
    private int fn; // False Negatives

    @Column(name = "precision_val")
    private Double precisionVal;

    @Column(name = "recall_val")
    private Double recallVal;

    @Column(name = "f1_score")
    private Double f1Score;

    @Column(name = "accuracy_val")
    private Double accuracyVal;

    @Column(name = "fpr_val")
    private Double fprVal;

    @Column(name = "specificity_val")
    private Double specificityVal;

    @Column(name = "evaluated_by", length = 150)
    private String evaluatedBy;

    @Column(name = "scenario_filter", length = 100)
    private String scenarioFilter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EvaluationExperiment() {
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

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public void setTotalSamples(int totalSamples) {
        this.totalSamples = totalSamples;
    }

    public int getTp() {
        return tp;
    }

    public void setTp(int tp) {
        this.tp = tp;
    }

    public int getTn() {
        return tn;
    }

    public void setTn(int tn) {
        this.tn = tn;
    }

    public int getFp() {
        return fp;
    }

    public void setFp(int fp) {
        this.fp = fp;
    }

    public int getFn() {
        return fn;
    }

    public void setFn(int fn) {
        this.fn = fn;
    }

    public Double getPrecisionVal() {
        return precisionVal;
    }

    public void setPrecisionVal(Double precisionVal) {
        this.precisionVal = precisionVal;
    }

    public Double getRecallVal() {
        return recallVal;
    }

    public void setRecallVal(Double recallVal) {
        this.recallVal = recallVal;
    }

    public Double getF1Score() {
        return f1Score;
    }

    public void setF1Score(Double f1Score) {
        this.f1Score = f1Score;
    }

    public Double getAccuracyVal() {
        return accuracyVal;
    }

    public void setAccuracyVal(Double accuracyVal) {
        this.accuracyVal = accuracyVal;
    }

    public Double getFprVal() {
        return fprVal;
    }

    public void setFprVal(Double fprVal) {
        this.fprVal = fprVal;
    }

    public Double getSpecificityVal() {
        return specificityVal;
    }

    public void setSpecificityVal(Double specificityVal) {
        this.specificityVal = specificityVal;
    }

    public String getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(String evaluatedBy) {
        this.evaluatedBy = evaluatedBy;
    }

    public String getScenarioFilter() {
        return scenarioFilter;
    }

    public void setScenarioFilter(String scenarioFilter) {
        this.scenarioFilter = scenarioFilter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
