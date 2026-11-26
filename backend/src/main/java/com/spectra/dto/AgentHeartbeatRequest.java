package com.spectra.dto;

import java.time.Instant;

public class AgentHeartbeatRequest {

    private String agentVersion;
    private String status;
    private int localBufferCount;
    private long collectionFailures;
    private long transmissionFailures;
    private Instant lastSuccessfulSyncAt;
    private String message;

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLocalBufferCount() {
        return localBufferCount;
    }

    public void setLocalBufferCount(int localBufferCount) {
        this.localBufferCount = localBufferCount;
    }

    public long getCollectionFailures() {
        return collectionFailures;
    }

    public void setCollectionFailures(long collectionFailures) {
        this.collectionFailures = collectionFailures;
    }

    public long getTransmissionFailures() {
        return transmissionFailures;
    }

    public void setTransmissionFailures(long transmissionFailures) {
        this.transmissionFailures = transmissionFailures;
    }

    public Instant getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) {
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
