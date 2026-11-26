package com.spectra.service.multivariate;

/**
 * Thrown when covariance estimation, regularization, or matrix inversion encounters
 * a numerical breakdown (e.g. non-positive-definite covariance matrix, Cholesky failure,
 * or non-finite matrix entries).
 * <p>
 * Under Spectra's mathematical failure policy, the detector documents numerical failure
 * rather than silently suppressing errors or returning pseudo-normal observations.
 */
public class NumericalInstabilityException extends RuntimeException {

    public NumericalInstabilityException(String message) {
        super(message);
    }

    public NumericalInstabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
