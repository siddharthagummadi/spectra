package com.spectra.service.multivariate;

/**
 * Top-level unchecked exception for multivariate anomaly detection errors.
 */
public class MultivariateDetectionException extends RuntimeException {

    public MultivariateDetectionException(String message) {
        super(message);
    }

    public MultivariateDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
