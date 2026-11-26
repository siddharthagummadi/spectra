package com.spectra.service.multivariate;

import java.time.Instant;
import java.util.Optional;

/**
 * Immutable snapshot of a service's baseline calibration state and model.
 */
public record CalibrationState(
    Long serviceId,
    CalibrationStatus status,
    MultivariateCalibrationModel model,
    int observationCount,
    Instant lastCalibratedAt,
    String statusMessage
) {
    public boolean isReady() {
        return status == CalibrationStatus.READY && model != null;
    }

    public Optional<MultivariateCalibrationModel> getModel() {
        return Optional.ofNullable(model);
    }

    public static CalibrationState uninitialized(Long serviceId) {
        return new CalibrationState(
            serviceId,
            CalibrationStatus.UNINITIALIZED,
            null,
            0,
            null,
            "Service baseline uninitialized"
        );
    }

    public static CalibrationState collecting(Long serviceId, int count) {
        return new CalibrationState(
            serviceId,
            CalibrationStatus.COLLECTING,
            null,
            count,
            null,
            String.format("Collecting normal observations: %d found, minimum %d required",
                count, LedoitWolfCovarianceEstimator.MIN_OBSERVATIONS)
        );
    }

    public static CalibrationState ready(Long serviceId, MultivariateCalibrationModel model, int count) {
        return new CalibrationState(
            serviceId,
            CalibrationStatus.READY,
            model,
            count,
            model.getCalibratedAt(),
            String.format("Calibrated baseline ready with %d observations (threshold=%.2f, lambda*=%.4f)",
                count, model.getAnomalyThreshold(), model.getLambdaStar())
        );
    }

    public static CalibrationState failed(Long serviceId, int count, String reason) {
        return failed(serviceId, null, count, reason);
    }

    public static CalibrationState failed(Long serviceId, MultivariateCalibrationModel preservedModel, int count, String reason) {
        return new CalibrationState(
            serviceId,
            CalibrationStatus.FAILED,
            preservedModel,
            count,
            preservedModel != null ? preservedModel.getCalibratedAt() : null,
            "Calibration failed: " + reason + (preservedModel != null ? " (preserved previous valid model)" : "")
        );
    }

    public static CalibrationState stale(Long serviceId, MultivariateCalibrationModel model, int count, String reason) {
        return new CalibrationState(
            serviceId,
            CalibrationStatus.STALE,
            model,
            count,
            model != null ? model.getCalibratedAt() : null,
            "Baseline model is stale: " + reason
        );
    }
}
