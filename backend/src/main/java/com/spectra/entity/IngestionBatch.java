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
    name = "ingestion_batches",
    indexes = {
        @Index(name = "idx_ingestion_batches_user", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_ingestion_batches_key", columnList = "batch_key", unique = true)
    }
)
public class IngestionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_key", nullable = false, unique = true, length = 64)
    private String batchKey;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "uploader_email", nullable = false, length = 150)
    private String uploaderEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "valid_records", nullable = false)
    private int validRecords;

    @Column(name = "rejected_records", nullable = false)
    private int rejectedRecords;

    @Column(nullable = false, length = 30)
    private String status = "COMPLETED"; // COMPLETED, FAILED, ROLLED_BACK

    @Column(name = "ingestion_source", nullable = false, length = 30)
    private String ingestionSource = "CSV_UPLOAD"; // CSV_UPLOAD, MANUAL_ENTRY, REST_API

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public IngestionBatch() {
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

    public String getBatchKey() {
        return batchKey;
    }

    public void setBatchKey(String batchKey) {
        this.batchKey = batchKey;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUploaderEmail() {
        return uploaderEmail;
    }

    public void setUploaderEmail(String uploaderEmail) {
        this.uploaderEmail = uploaderEmail;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getValidRecords() {
        return validRecords;
    }

    public void setValidRecords(int validRecords) {
        this.validRecords = validRecords;
    }

    public int getRejectedRecords() {
        return rejectedRecords;
    }

    public void setRejectedRecords(int rejectedRecords) {
        this.rejectedRecords = rejectedRecords;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIngestionSource() {
        return ingestionSource;
    }

    public void setIngestionSource(String ingestionSource) {
        this.ingestionSource = ingestionSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Enterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(Enterprise enterprise) {
        this.enterprise = enterprise;
    }
}
