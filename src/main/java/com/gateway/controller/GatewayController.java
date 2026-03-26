package com.gateway.controller;

import com.gateway.model.RequestLog;
import com.gateway.model.ServiceHealth;
import com.gateway.service.GatewayService;
import com.gateway.service.RequestLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
@Tag(name = "Gateway", description = "API gateway for microservice routing, health checks, and analytics")
public class GatewayController {

    private final GatewayService gatewayService;
    private final RequestLogRepository logRepository;
    private final ProducerTemplate camelProducer;
    private final com.gateway.service.RateLimiter rateLimiter;
    private final com.gateway.service.CircuitBreaker circuitBreaker;

    @PostMapping("/proxy/{service}/**")
    @Operation(summary = "Proxy request to a downstream service via Camel")
    public ResponseEntity<String> proxy(
            @PathVariable String service,
            @RequestBody(required = false) String body,
            @RequestHeader(value = "X-Forwarded-For", defaultValue = "127.0.0.1") String clientIp) {

        String result = gatewayService.proxyRequest(service, "POST", "/api/wallet", body, clientIp);
        return ResponseEntity.ok()
                .header("X-RateLimit-Remaining", String.valueOf(rateLimiter.getRemaining(clientIp)))
                .body(result);
    }

    @GetMapping("/health")
    @Operation(summary = "Check health of all downstream services with circuit breaker state")
    public ResponseEntity<Map<String, Object>> health() {
        var services = gatewayService.checkHealth();
        var cbStates = new java.util.LinkedHashMap<String, String>();
        for (var svc : services) {
            cbStates.put(svc.getName(), circuitBreaker.getState(svc.getName()));
        }
        return ResponseEntity.ok(Map.of("services", services, "circuitBreakers", cbStates));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get gateway request statistics")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(gatewayService.getStats());
    }

    @GetMapping("/logs")
    @Operation(summary = "Get recent request logs from MongoDB")
    public ResponseEntity<List<RequestLog>> logs(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(
                logRepository.findAll(
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))
                ).getContent());
    }

    @GetMapping("/logs/service/{service}")
    @Operation(summary = "Get request logs filtered by service")
    public ResponseEntity<List<RequestLog>> logsByService(@PathVariable String service) {
        return ResponseEntity.ok(logRepository.findByServiceOrderByTimestampDesc(service));
    }
}
