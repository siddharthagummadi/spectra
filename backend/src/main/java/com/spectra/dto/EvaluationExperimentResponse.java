package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.EvaluationExperiment;

public class EvaluationExperimentResponse {

    private Long id;
    private String experimentName;
    private String datasetName;
    private Long batchId;
    private String algorithm;
    private int totalSamples;
    private int tp;
    private int tn;
    private int fp;
    private int fn;
    private Double precisionVal;
    private Double recallVal;
    private Double f1Score;
    private Double accuracyVal;
    private Double fprVal;
    private Double specificityVal;
    private String evaluatedBy;
    private String scenarioFilter;
    private Instant createdAt;

    public EvaluationExperimentResponse() {
    }

    public static EvaluationExperimentResponse from(EvaluationExperiment exp) {
        EvaluationExperimentResponse res = new EvaluationExperimentResponse();
        res.setId(exp.getId());
        res.setExperimentName(exp.getExperimentName());
        res.setDatasetName(exp.getDatasetName());
        res.setBatchId(exp.getBatchId());
        res.setAlgorithm(exp.getAlgorithm());
        res.setTotalSamples(exp.getTotalSamples());
        res.setTp(exp.getTp());
        res.setTn(exp.getTn());
        res.setFp(exp.getFp());
        res.setFn(exp.getFn());
        res.setPrecisionVal(exp.getPrecisionVal());
        res.setRecallVal(exp.getRecallVal());
        res.setF1Score(exp.getF1Score());
        res.setAccuracyVal(exp.getAccuracyVal());
        res.setFprVal(exp.getFprVal());
        res.setSpecificityVal(exp.getSpecificityVal());
        res.setEvaluatedBy(exp.getEvaluatedBy());
        res.setScenarioFilter(exp.getScenarioFilter());
        res.setCreatedAt(exp.getCreatedAt());
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public int getTotalSamples() { return totalSamples; }
    public void setTotalSamples(int totalSamples) { this.totalSamples = totalSamples; }

    public int getTp() { return tp; }
    public void setTp(int tp) { this.tp = tp; }

    public int getTn() { return tn; }
    public void setTn(int tn) { this.tn = tn; }

    public int getFp() { return fp; }
    public void setFp(int fp) { this.fp = fp; }

    public int getFn() { return fn; }
    public void setFn(int fn) { this.fn = fn; }

    public Double getPrecisionVal() { return precisionVal; }
    public void setPrecisionVal(Double precisionVal) { this.precisionVal = precisionVal; }

    public Double getRecallVal() { return recallVal; }
    public void setRecallVal(Double recallVal) { this.recallVal = recallVal; }

    public Double getF1Score() { return f1Score; }
    public void setF1Score(Double f1Score) { this.f1Score = f1Score; }

    public Double getAccuracyVal() { return accuracyVal; }
    public void setAccuracyVal(Double accuracyVal) { this.accuracyVal = accuracyVal; }

    public Double getFprVal() { return fprVal; }
    public void setFprVal(Double fprVal) { this.fprVal = fprVal; }

    public Double getSpecificityVal() { return specificityVal; }
    public void setSpecificityVal(Double specificityVal) { this.specificityVal = specificityVal; }

    public String getEvaluatedBy() { return evaluatedBy; }
    public void setEvaluatedBy(String evaluatedBy) { this.evaluatedBy = evaluatedBy; }

    public String getScenarioFilter() { return scenarioFilter; }
    public void setScenarioFilter(String scenarioFilter) { this.scenarioFilter = scenarioFilter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
