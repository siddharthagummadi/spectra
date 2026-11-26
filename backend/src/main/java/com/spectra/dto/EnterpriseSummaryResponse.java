package com.spectra.dto;

import java.time.Instant;

public record EnterpriseSummaryResponse(
        Long id,
        String name,
        String slug,
        String organizationCode,
        String environment,
        String connectionStatus,
        String statusReason,
        String contactName,
        String contactEmail,
        Instant lastTelemetryAt,
        Instant createdAt,
        long serviceCount,
        long activeServiceCount,
        long agentCount,
        long onlineAgentCount,
        long activeAlertCount,
        long openIncidentCount,
        long userCount
) {
}
