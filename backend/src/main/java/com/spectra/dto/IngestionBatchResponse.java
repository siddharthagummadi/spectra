package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.IngestionBatch;

public class IngestionBatchResponse {

    private Long id;
    private String batchKey;
    private String filename;
    private String uploaderEmail;
    private int totalRecords;
    private int validRecords;
    private int rejectedRecords;
    private String status;
    private String ingestionSource;
    private Instant createdAt;

    public IngestionBatchResponse() {
    }

    public static IngestionBatchResponse from(IngestionBatch batch) {
        IngestionBatchResponse res = new IngestionBatchResponse();
        res.setId(batch.getId());
        res.setBatchKey(batch.getBatchKey());
        res.setFilename(batch.getFilename());
        res.setUploaderEmail(batch.getUploaderEmail());
        res.setTotalRecords(batch.getTotalRecords());
        res.setValidRecords(batch.getValidRecords());
        res.setRejectedRecords(batch.getRejectedRecords());
        res.setStatus(batch.getStatus());
        res.setIngestionSource(batch.getIngestionSource());
        res.setCreatedAt(batch.getCreatedAt());
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getUploaderEmail() { return uploaderEmail; }
    public void setUploaderEmail(String uploaderEmail) { this.uploaderEmail = uploaderEmail; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getValidRecords() { return validRecords; }
    public void setValidRecords(int validRecords) { this.validRecords = validRecords; }

    public int getRejectedRecords() { return rejectedRecords; }
    public void setRejectedRecords(int rejectedRecords) { this.rejectedRecords = rejectedRecords; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIngestionSource() { return ingestionSource; }
    public void setIngestionSource(String ingestionSource) { this.ingestionSource = ingestionSource; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
