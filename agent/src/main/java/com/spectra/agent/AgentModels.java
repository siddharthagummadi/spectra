package com.spectra.agent;

import java.time.Instant;
import java.util.List;

public final class AgentModels {

    private AgentModels() {}

    public record TelemetrySample(
            String serviceName,
            String environment,
            String endpointUrl,
            String metricName,
            double metricValue,
            String unit,
            Instant recordedAt
    ) {}

    public record TelemetryBatch(
            String clientBatchId,
            List<TelemetrySample> samples
    ) {}

    public record EnrollRequest(
            String enrollmentToken,
            String agentCode,
            String hostname,
            String osInfo,
            String agentVersion
    ) {}

    public record EnrollResponse(
            Long agentId,
            Long enterpriseId,
            String enterpriseName,
            String enterpriseOrganizationCode,
            String agentCode,
            String keyId,
            String agentCredential,
            int collectionIntervalSec,
            int heartbeatIntervalSec
    ) {}

    public record HeartbeatRequest(
            String agentVersion,
            String status,
            int localBufferCount,
            long collectionFailures,
            long transmissionFailures,
            Instant lastSuccessfulSyncAt,
            String message
    ) {}
}
