package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.EnterpriseAgent;

public record AgentSummaryResponse(
        Long id,
        Long enterpriseId,
        String enterpriseName,
        String agentCode,
        String hostname,
        String osInfo,
        String agentVersion,
        String status,
        int collectionIntervalSec,
        int heartbeatIntervalSec,
        Instant lastHeartbeatAt,
        Instant lastSuccessfulTelemetryAt,
        int localBufferCount,
        long collectionFailures,
        long transmissionFailures,
        String lastSyncError,
        boolean active
) {
    public static AgentSummaryResponse from(EnterpriseAgent agent) {
        return new AgentSummaryResponse(
                agent.getId(),
                agent.getEnterprise() != null ? agent.getEnterprise().getId() : null,
                agent.getEnterprise() != null ? agent.getEnterprise().getName() : null,
                agent.getAgentCode(),
                agent.getHostname(),
                agent.getOsInfo(),
                agent.getAgentVersion(),
                agent.getStatus(),
                agent.getCollectionIntervalSec(),
                agent.getHeartbeatIntervalSec(),
                agent.getLastHeartbeatAt(),
                agent.getLastSuccessfulTelemetryAt(),
                agent.getLocalBufferCount(),
                agent.getCollectionFailures(),
                agent.getTransmissionFailures(),
                agent.getLastSyncError(),
                agent.isActive()
        );
    }
}
