package com.spectra.service.multivariate;

/**
 * Lifecycle states of service baseline calibration for multivariate anomaly detection.
 */
public enum CalibrationStatus {
    /**
     * No baseline data or model initialized for this service.
     */
    UNINITIALIZED,

    /**
     * Accumulating normal operational telemetry vectors (count < MIN_OBSERVATIONS).
     */
    COLLECTING,

    /**
     * Successfully calibrated, positive-definite model active for live detection.
     */
    READY,

    /**
     * Calibration failed due to numerical breakdown, insufficient observations, or invalid inputs.
     */
    FAILED,

    /**
     * Calibrated model has exceeded maximum operational age and is scheduled for refresh.
     */
    STALE
}
