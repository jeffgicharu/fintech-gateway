package com.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple circuit breaker per downstream service.
 * States: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing recovery).
 */
@Component
@Slf4j
public class CircuitBreaker {

    private final int threshold;
    private final long halfOpenAfterMs;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public CircuitBreaker(
            @Value("${gateway.circuit-breaker.threshold:5}") int threshold,
            @Value("${gateway.circuit-breaker.half-open-after-ms:30000}") long halfOpenAfterMs) {
        this.threshold = threshold;
        this.halfOpenAfterMs = halfOpenAfterMs;
    }

    public boolean isOpen(String service) {
        CircuitState state = circuits.get(service);
        if (state == null) return false;

        if (state.failures.get() >= threshold) {
            long elapsed = System.currentTimeMillis() - state.lastFailure;
            if (elapsed > halfOpenAfterMs) {
                log.info("Circuit half-open for {}", service);
                state.failures.set(threshold - 1); // Allow one request through
                return false;
            }
            return true;
        }
        return false;
    }

    public void recordSuccess(String service) {
        CircuitState state = circuits.get(service);
        if (state != null) {
            state.failures.set(0);
        }
    }

    public void recordFailure(String service) {
        CircuitState state = circuits.computeIfAbsent(service, k -> new CircuitState());
        state.failures.incrementAndGet();
        state.lastFailure = System.currentTimeMillis();

        if (state.failures.get() >= threshold) {
            log.warn("Circuit OPEN for {} (failures: {})", service, state.failures.get());
        }
    }

    public String getState(String service) {
        CircuitState state = circuits.get(service);
        if (state == null || state.failures.get() == 0) return "CLOSED";
        if (state.failures.get() >= threshold) {
            long elapsed = System.currentTimeMillis() - state.lastFailure;
            return elapsed > halfOpenAfterMs ? "HALF_OPEN" : "OPEN";
        }
        return "CLOSED";
    }

    private static class CircuitState {
        final AtomicInteger failures = new AtomicInteger(0);
        volatile long lastFailure;
    }
}
