package com.spectra.dto;

public record AgentEnrollResponse(
        Long agentId,
        Long enterpriseId,
        String enterpriseName,
        String enterpriseOrganizationCode,
        String agentCode,
        String keyId,
        String agentCredential,
        int collectionIntervalSec,
        int heartbeatIntervalSec
) {
}
