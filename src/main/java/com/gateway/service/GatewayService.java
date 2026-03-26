package com.gateway.service;

import com.gateway.model.RequestLog;
import com.gateway.model.ServiceHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayService {

    private final RequestLogRepository logRepository;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    @Value("${gateway.services.wallet-api}")
    private String walletApiUrl;

    @Value("${gateway.services.notification-service}")
    private String notificationServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String proxyRequest(String service, String method, String path,
                               String body, String clientIp) {

        String baseUrl = resolveServiceUrl(service);
        if (baseUrl == null) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }

        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new RateLimitException("Rate limit exceeded. Try again later.");
        }

        if (circuitBreaker.isOpen(service)) {
            throw new ServiceUnavailableException("Service " + service + " is temporarily unavailable");
        }

        long start = System.currentTimeMillis();
        String url = baseUrl + path;

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            if ("POST".equalsIgnoreCase(method)) {
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - start;
            circuitBreaker.recordSuccess(service);

            logRequest(service, method, path, response.statusCode(), duration,
                    body, response.body(), null, clientIp);

            return response.body();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            circuitBreaker.recordFailure(service);

            logRequest(service, method, path, 503, duration,
                    body, null, e.getMessage(), clientIp);

            throw new ServiceUnavailableException("Service call failed: " + e.getMessage());
        }
    }

    public List<ServiceHealth> checkHealth() {
        Map<String, String> services = Map.of(
                "wallet-api", walletApiUrl,
                "notification-service", notificationServiceUrl
        );

        return services.entrySet().stream().map(entry -> {
            long start = System.currentTimeMillis();
            String status;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(entry.getValue() + "/api-docs"))
                        .timeout(Duration.ofSeconds(3))
                        .GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                status = resp.statusCode() < 400 ? "UP" : "DOWN";
            } catch (Exception e) {
                status = "DOWN";
            }
            long duration = System.currentTimeMillis() - start;

            return ServiceHealth.builder()
                    .name(entry.getKey())
                    .url(entry.getValue())
                    .status(status)
                    .responseTimeMs(duration)
                    .lastChecked(Instant.now())
                    .build();
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        long total = logRepository.count();
        long success = logRepository.countByStatusLessThan(400);
        long failure = logRepository.countByStatusGreaterThanEqual(400);

        List<RequestLog> recent = logRepository.findAll(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent();
        double avgMs = recent.stream().mapToLong(RequestLog::getDurationMs).average().orElse(0);

        Map<String, Long> byService = new HashMap<>();
        for (String svc : List.of("wallet-api", "notification-service")) {
            byService.put(svc, logRepository.countByService(svc));
        }

        return Map.of(
                "totalRequests", total,
                "successCount", success,
                "failureCount", failure,
                "avgResponseTimeMs", Math.round(avgMs * 100.0) / 100.0,
                "requestsByService", byService
        );
    }

    private String resolveServiceUrl(String service) {
        return switch (service) {
            case "wallet-api" -> walletApiUrl;
            case "notification-service" -> notificationServiceUrl;
            default -> null;
        };
    }

    private void logRequest(String service, String method, String path, int status,
                            long durationMs, String requestBody, String responseBody,
                            String error, String clientIp) {
        RequestLog log = RequestLog.builder()
                .service(service)
                .method(method)
                .path(path)
                .status(status)
                .durationMs(durationMs)
                .requestBody(truncate(requestBody, 2000))
                .responseBody(truncate(responseBody, 2000))
                .errorMessage(error)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .build();
        logRepository.save(log);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) { super(message); }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) { super(message); }
    }
}
