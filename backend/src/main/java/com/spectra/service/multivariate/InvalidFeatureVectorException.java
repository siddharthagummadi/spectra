package com.spectra.service.multivariate;

/**
 * Thrown when a multivariate feature vector is incomplete, contains non-finite values (NaN/Infinity),
 * violates physical operational bounds, or is stale.
 * <p>
 * In accordance with strict detection invariants, missing metrics are never silently substituted with zero.
 */
public class InvalidFeatureVectorException extends IllegalArgumentException {

    public InvalidFeatureVectorException(String message) {
        super(message);
    }

    public InvalidFeatureVectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
