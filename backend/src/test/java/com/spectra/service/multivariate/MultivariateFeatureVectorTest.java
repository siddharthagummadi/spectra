package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class MultivariateFeatureVectorTest {

    @Test
    void of_ValidValues_ConstructsSuccessfully() {
        Instant now = Instant.now();
        MultivariateFeatureVector vec = MultivariateFeatureVector.of(45.5, 60.2, 125.0, 0.45, now);

        assertEquals(45.5, vec.getCpuUsage());
        assertEquals(60.2, vec.getMemoryUsage());
        assertEquals(125.0, vec.getResponseTime());
        assertEquals(0.45, vec.getErrorRate());
        assertEquals(now, vec.getRecordedAt());

        assertArrayEquals(new double[] { 45.5, 60.2, 125.0, 0.45 }, vec.toArray(), 1e-9);
        assertEquals(45.5, vec.get(MultivariateMetricType.CPU_USAGE));
        assertEquals(60.2, vec.get(MultivariateMetricType.MEMORY_USAGE));
        assertEquals(125.0, vec.get(MultivariateMetricType.RESPONSE_TIME));
        assertEquals(0.45, vec.get(MultivariateMetricType.ERROR_RATE));
    }

    @Test
    void of_NullMetric_ThrowsInvalidFeatureVectorException() {
        // Missing CPU
        InvalidFeatureVectorException ex1 = assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(null, 50.0, 100.0, 1.0)
        );
        assertEquals(true, ex1.getMessage().contains("missing metric 'CPU_USAGE'"));

        // Missing Memory
        InvalidFeatureVectorException ex2 = assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, null, 100.0, 1.0)
        );
        assertEquals(true, ex2.getMessage().contains("missing metric 'MEMORY_USAGE'"));

        // Missing Response Time
        InvalidFeatureVectorException ex3 = assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, null, 1.0)
        );
        assertEquals(true, ex3.getMessage().contains("missing metric 'RESPONSE_TIME'"));

        // Missing Error Rate
        InvalidFeatureVectorException ex4 = assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, 100.0, null)
        );
        assertEquals(true, ex4.getMessage().contains("missing metric 'ERROR_RATE'"));
    }

    @Test
    void of_NonFiniteMetric_ThrowsInvalidFeatureVectorException() {
        // NaN
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(Double.NaN, 50.0, 100.0, 1.0)
        );

        // Positive Infinity
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, Double.POSITIVE_INFINITY, 100.0, 1.0)
        );

        // Negative Infinity
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, Double.NEGATIVE_INFINITY, 1.0)
        );
    }

    @Test
    void of_PhysicalRangeViolations_ThrowsInvalidFeatureVectorException() {
        // CPU negative
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(-0.1, 50.0, 100.0, 1.0)
        );

        // CPU > 100%
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(100.1, 50.0, 100.0, 1.0)
        );

        // Memory negative
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, -1.0, 100.0, 1.0)
        );

        // Memory > 100%
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 100.5, 100.0, 1.0)
        );

        // Response time negative
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, -5.0, 1.0)
        );

        // Response time > 60,000 ms (established canonical limit)
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, 60_001.0, 1.0)
        );

        // Error rate negative
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, 100.0, -0.01)
        );

        // Error rate > 100%
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.of(50.0, 50.0, 100.0, 100.1)
        );
    }

    @Test
    void validateStaleness_FutureOrStale_ThrowsInvalidFeatureVectorException() {
        Instant now = Instant.now();

        // Stale observation (20 minutes old, max 15 minutes)
        MultivariateFeatureVector staleVec = MultivariateFeatureVector.of(
            50.0, 50.0, 100.0, 1.0, now.minus(Duration.ofMinutes(20))
        );
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> staleVec.validateStaleness(now, Duration.ofMinutes(15))
        );

        // Future observation (> 10s into future)
        MultivariateFeatureVector futureVec = MultivariateFeatureVector.of(
            50.0, 50.0, 100.0, 1.0, now.plus(Duration.ofMinutes(2))
        );
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> futureVec.validateStaleness(now, Duration.ofMinutes(15))
        );

        // Fresh observation passes without exception
        MultivariateFeatureVector freshVec = MultivariateFeatureVector.of(
            50.0, 50.0, 100.0, 1.0, now.minus(Duration.ofSeconds(30))
        );
        freshVec.validateStaleness(now, Duration.ofMinutes(15));
    }

    @Test
    void fromArray_DimensionMismatch_ThrowsInvalidFeatureVectorException() {
        // 3 elements instead of 4
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.fromArray(new double[] { 10.0, 20.0, 30.0 }, Instant.now())
        );

        // Null array
        assertThrows(
            InvalidFeatureVectorException.class,
            () -> MultivariateFeatureVector.fromArray(null, Instant.now())
        );
    }
}
