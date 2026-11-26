package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of a telemetry ingestion and vector alignment operation.
 */
public record TelemetryAlignmentResult(
    Long serviceId,
    Status status,
    MultivariateFeatureVector alignedVector,
    String detailMessage,
    Instant evaluatedAt
) {
    public enum Status {
        ALIGNED,
        INCOMPLETE,
        LATE_DROPPED,
        DUPLICATE_SUPPRESSED,
        STALE_DROPPED,
        NO_UPDATE
    }

    public boolean isAligned() {
        return status == Status.ALIGNED && alignedVector != null;
    }

    public Optional<MultivariateFeatureVector> getAlignedVector() {
        return Optional.ofNullable(alignedVector);
    }

    public static TelemetryAlignmentResult aligned(Long serviceId, MultivariateFeatureVector vector) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.ALIGNED,
            vector,
            "Complete fresh 4D vector successfully aligned",
            Instant.now()
        );
    }

    public static TelemetryAlignmentResult incomplete(Long serviceId, String message) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.INCOMPLETE,
            null,
            message,
            Instant.now()
        );
    }

    public static TelemetryAlignmentResult lateDropped(Long serviceId, String message) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.LATE_DROPPED,
            null,
            message,
            Instant.now()
        );
    }

    public static TelemetryAlignmentResult duplicateSuppressed(Long serviceId, String message) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.DUPLICATE_SUPPRESSED,
            null,
            message,
            Instant.now()
        );
    }

    public static TelemetryAlignmentResult staleDropped(Long serviceId, String message) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.STALE_DROPPED,
            null,
            message,
            Instant.now()
        );
    }

    public static TelemetryAlignmentResult noUpdate(Long serviceId, String message) {
        return new TelemetryAlignmentResult(
            serviceId,
            Status.NO_UPDATE,
            null,
            message,
            Instant.now()
        );
    }
}
