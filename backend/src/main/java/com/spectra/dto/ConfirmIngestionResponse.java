package com.spectra.dto;

public class ConfirmIngestionResponse {

    private Long batchId;
    private String batchKey;
    private String filename;
    private int totalIngested;
    private int anomaliesDetected;
    private int alertsTriggered;
    private int incidentsCreated;
    private String status;
    private String message;

    public ConfirmIngestionResponse() {
    }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public int getTotalIngested() { return totalIngested; }
    public void setTotalIngested(int totalIngested) { this.totalIngested = totalIngested; }

    public int getAnomaliesDetected() { return anomaliesDetected; }
    public void setAnomaliesDetected(int anomaliesDetected) { this.anomaliesDetected = anomaliesDetected; }

    public int getAlertsTriggered() { return alertsTriggered; }
    public void setAlertsTriggered(int alertsTriggered) { this.alertsTriggered = alertsTriggered; }

    public int getIncidentsCreated() { return incidentsCreated; }
    public void setIncidentsCreated(int incidentsCreated) { this.incidentsCreated = incidentsCreated; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
