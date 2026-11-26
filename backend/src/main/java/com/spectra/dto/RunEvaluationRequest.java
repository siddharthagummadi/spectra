package com.spectra.dto;

public class RunEvaluationRequest {

    private String datasetName;
    private Long batchId;
    private String algorithm = "ALL"; // STATIC_THRESHOLD, STATISTICAL_ZSCORE, SPECTRA_AI, ALL
    private String scenarioFilter;

    public RunEvaluationRequest() {
    }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getScenarioFilter() { return scenarioFilter; }
    public void setScenarioFilter(String scenarioFilter) { this.scenarioFilter = scenarioFilter; }
}
