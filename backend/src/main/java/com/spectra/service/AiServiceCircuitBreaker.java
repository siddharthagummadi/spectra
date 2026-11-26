package com.spectra.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe circuit breaker protecting the Spring Boot backend against
 * cascading latency or resource exhaustion when the Python AI microservice
 * is degraded, unavailable, or experiencing network partitions.
 */
public class AiServiceCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long resetTimeoutMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0L);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public AiServiceCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.resetTimeoutMs = Math.max(100L, resetTimeoutMs);
    }

    public synchronized boolean allowExecution() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }

        if (currentState == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTimestamp.get() >= resetTimeoutMs) {
                state.set(State.HALF_OPEN);
                return true; // Trial execution allowed
            }
            return false; // Circuit is OPEN; short-circuit execution
        }

        // HALF_OPEN: allow trial request through
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    public synchronized void recordFailure() {
        lastFailureTimestamp.set(System.currentTimeMillis());
        int current = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN || current >= failureThreshold) {
            state.set(State.OPEN);
        }
    }

    public State getState() {
        if (state.get() == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTimestamp.get() >= resetTimeoutMs) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
        }
        return state.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getResetTimeoutMs() {
        return resetTimeoutMs;
    }

    public synchronized void reset() {
        consecutiveFailures.set(0);
        lastFailureTimestamp.set(0L);
        state.set(State.CLOSED);
    }
}
