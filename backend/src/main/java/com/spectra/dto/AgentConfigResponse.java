package com.spectra.dto;

import java.util.List;

public record AgentConfigResponse(
        Long agentId,
        Long enterpriseId,
        String agentCode,
        String agentStatus,
        String enterpriseStatus,
        int collectionIntervalSec,
        int heartbeatIntervalSec,
        List<String> supportedMetrics,
        boolean active
) {
}
