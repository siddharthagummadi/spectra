package com.spectra.dto;

import java.util.List;

public record AgentTelemetryBatchResponse(
        String status,
        String clientBatchId,
        boolean duplicateBatch,
        int receivedCount,
        int acceptedCount,
        int rejectedCount,
        String enterpriseStatus,
        List<String> errors
) {
}
