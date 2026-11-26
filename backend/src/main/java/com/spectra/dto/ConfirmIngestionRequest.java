package com.spectra.dto;

import java.util.List;

public class ConfirmIngestionRequest {

    private String batchKey;
    private String filename;
    private String ingestionSource = "CSV_UPLOAD"; // CSV_UPLOAD, MANUAL_ENTRY
    private List<IngestionPreviewResponse.TelemetryObservationDto> records;

    public ConfirmIngestionRequest() {
    }

    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getIngestionSource() { return ingestionSource; }
    public void setIngestionSource(String ingestionSource) { this.ingestionSource = ingestionSource; }

    public List<IngestionPreviewResponse.TelemetryObservationDto> getRecords() { return records; }
    public void setRecords(List<IngestionPreviewResponse.TelemetryObservationDto> records) { this.records = records; }
}
