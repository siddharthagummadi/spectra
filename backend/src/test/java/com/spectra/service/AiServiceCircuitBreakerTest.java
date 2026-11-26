package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiServiceCircuitBreakerTest {

    private AiServiceCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 3 failures to trip, 200ms reset timeout for fast testing
        circuitBreaker = new AiServiceCircuitBreaker(3, 200L);
    }

    @Test
    @DisplayName("Initial state is CLOSED and allows execution")
    void initialState_IsClosed_AllowsExecution() {
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertTrue(circuitBreaker.allowExecution());
    }

    @Test
    @DisplayName("Success in CLOSED state keeps state CLOSED and failure count 0")
    void recordSuccess_InClosedState_KeepsClosed() {
        circuitBreaker.recordSuccess();
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertTrue(circuitBreaker.allowExecution());
    }

    @Test
    @DisplayName("Failures below threshold keep state CLOSED")
    void failuresBelowThreshold_StayClosed() {
        circuitBreaker.recordFailure();
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowExecution());

        circuitBreaker.recordFailure();
        assertEquals(2, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowExecution());
    }

    @Test
    @DisplayName("Reaching failure threshold transitions state to OPEN and blocks execution")
    void reachingThreshold_TransitionsToOpen_BlocksExecution() {
        circuitBreaker.recordFailure(); // 1
        circuitBreaker.recordFailure(); // 2
        circuitBreaker.recordFailure(); // 3 -> threshold reached!

        assertEquals(3, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowExecution(), "Execution must be blocked when circuit breaker is OPEN");
    }

    @Test
    @DisplayName("Success resets failure count to 0")
    void recordSuccess_ResetsFailureCount() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(2, circuitBreaker.getConsecutiveFailures());

        circuitBreaker.recordSuccess();
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Reset timeout transitions OPEN state to HALF_OPEN and permits trial execution")
    void resetTimeout_TransitionsOpenToHalfOpen_PermitsTrial() throws InterruptedException {
        // Trip circuit breaker
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowExecution());

        // Wait for reset timeout window (200ms)
        Thread.sleep(250);

        // State check after timeout should allow execution and transition to HALF_OPEN
        assertTrue(circuitBreaker.allowExecution(), "Trial execution should be permitted after reset timeout");
        assertEquals(AiServiceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Success in HALF_OPEN recovers circuit to CLOSED state")
    void successInHalfOpen_RecoversToClosed() throws InterruptedException {
        // Trip breaker
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertFalse(circuitBreaker.allowExecution());

        // Wait for timeout
        Thread.sleep(250);

        // Trial execution permitted
        assertTrue(circuitBreaker.allowExecution());
        assertEquals(AiServiceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        // Record successful trial
        circuitBreaker.recordSuccess();
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertTrue(circuitBreaker.allowExecution());
    }

    @Test
    @DisplayName("Failure in HALF_OPEN returns circuit to OPEN state")
    void failureInHalfOpen_ReOpensCircuit() throws InterruptedException {
        // Trip breaker
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        // Wait for timeout
        Thread.sleep(250);

        // Trial execution permitted
        assertTrue(circuitBreaker.allowExecution());
        assertEquals(AiServiceCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        // Trial fails
        circuitBreaker.recordFailure();
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowExecution());
    }

    @Test
    @DisplayName("Manual reset returns circuit to pristine CLOSED state")
    void manualReset_RestoresClosedState() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());

        circuitBreaker.reset();
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertTrue(circuitBreaker.allowExecution());
    }
}
