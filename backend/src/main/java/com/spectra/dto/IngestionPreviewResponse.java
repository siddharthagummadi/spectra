package com.spectra.dto;

import java.util.List;

public class IngestionPreviewResponse {

    private String filename;
    private String batchKey;
    private int totalRecords;
    private int validRecords;
    private int invalidRecords;
    private int duplicateRecords;
    private boolean isDuplicateBatch;
    private String duplicateWarning;
    private List<TelemetryObservationDto> previewRows;
    private List<String> validationErrors;

    public IngestionPreviewResponse() {
    }

    public static class TelemetryObservationDto {
        private Long serviceId;
        private String serviceName;
        private String metricName;
        private Double metricValue;
        private String unit;
        private String timestamp;
        private String environment;
        private String scenarioLabel;
        private String groundTruthLabel;
        private boolean isDuplicate;

        public TelemetryObservationDto() {
        }

        public Long getServiceId() { return serviceId; }
        public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public Double getMetricValue() { return metricValue; }
        public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }

        public String getScenarioLabel() { return scenarioLabel; }
        public void setScenarioLabel(String scenarioLabel) { this.scenarioLabel = scenarioLabel; }

        public String getGroundTruthLabel() { return groundTruthLabel; }
        public void setGroundTruthLabel(String groundTruthLabel) { this.groundTruthLabel = groundTruthLabel; }

        public boolean isDuplicate() { return isDuplicate; }
        public void setDuplicate(boolean duplicate) { isDuplicate = duplicate; }
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getValidRecords() { return validRecords; }
    public void setValidRecords(int validRecords) { this.validRecords = validRecords; }

    public int getInvalidRecords() { return invalidRecords; }
    public void setInvalidRecords(int invalidRecords) { this.invalidRecords = invalidRecords; }

    public int getDuplicateRecords() { return duplicateRecords; }
    public void setDuplicateRecords(int duplicateRecords) { this.duplicateRecords = duplicateRecords; }

    public boolean isDuplicateBatch() { return isDuplicateBatch; }
    public void setDuplicateBatch(boolean duplicateBatch) { isDuplicateBatch = duplicateBatch; }

    public String getDuplicateWarning() { return duplicateWarning; }
    public void setDuplicateWarning(String duplicateWarning) { this.duplicateWarning = duplicateWarning; }

    public List<TelemetryObservationDto> getPreviewRows() { return previewRows; }
    public void setPreviewRows(List<TelemetryObservationDto> previewRows) { this.previewRows = previewRows; }

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
}
