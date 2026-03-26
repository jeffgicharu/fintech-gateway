package com.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding window rate limiter per client IP.
 */
@Component
public class RateLimiter {

    private final int maxRequests;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${gateway.rate-limit.requests-per-minute:60}") int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public boolean tryAcquire(String clientId) {
        WindowCounter counter = counters.computeIfAbsent(clientId, k -> new WindowCounter());
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        if (counter.windowStart < windowStart) {
            counter.reset(now);
        }

        return counter.count.incrementAndGet() <= maxRequests;
    }

    public int getRemaining(String clientId) {
        WindowCounter counter = counters.get(clientId);
        if (counter == null) return maxRequests;
        return Math.max(0, maxRequests - counter.count.get());
    }

    private static class WindowCounter {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        void reset(long now) {
            windowStart = now;
            count.set(0);
        }
    }
}
