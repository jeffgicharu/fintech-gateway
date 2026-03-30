package com.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter proxyRequestCounter(MeterRegistry registry) {
        return Counter.builder("gateway.proxy.requests.total")
                .description("Total proxied requests")
                .register(registry);
    }

    @Bean
    public Counter circuitBreakerTripCounter(MeterRegistry registry) {
        return Counter.builder("gateway.circuit_breaker.trips.total")
                .description("Total circuit breaker trips")
                .register(registry);
    }

    @Bean
    public Counter rateLimitRejectCounter(MeterRegistry registry) {
        return Counter.builder("gateway.rate_limit.rejections.total")
                .description("Total rate limit rejections")
                .register(registry);
    }

    @Bean
    public Timer proxyLatencyTimer(MeterRegistry registry) {
        return Timer.builder("gateway.proxy.latency")
                .description("Downstream service call latency")
                .register(registry);
    }
}
