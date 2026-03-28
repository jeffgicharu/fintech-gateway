package com.gateway.registry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Dynamic service registry. Services register on startup and send heartbeats.
 * The gateway resolves service URLs at request time instead of relying on
 * hardcoded configuration.
 *
 * Seed services are registered from application.yml on first startup so
 * the gateway works out of the box without manual registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceRegistry {

    private final ServiceRegistryRepository repo;

    @Value("${gateway.services.wallet-api}")
    private String walletApiUrl;

    @Value("${gateway.services.notification-service}")
    private String notificationServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @PostConstruct
    void seedDefaults() {
        registerIfAbsent("wallet-api", "Wallet API", walletApiUrl, "/api-docs");
        registerIfAbsent("notification-service", "Notification Service", notificationServiceUrl, "/api-docs");
    }

    public ServiceInstance register(String serviceId, String name, String baseUrl, String healthEndpoint) {
        ServiceInstance existing = repo.findByServiceId(serviceId).orElse(null);
        if (existing != null) {
            existing.setBaseUrl(baseUrl);
            existing.setHealthEndpoint(healthEndpoint);
            existing.setStatus("UP");
            existing.setLastHeartbeat(Instant.now());
            repo.save(existing);
            log.info("Service updated: {} -> {}", serviceId, baseUrl);
            return existing;
        }

        ServiceInstance instance = ServiceInstance.builder()
                .serviceId(serviceId)
                .name(name)
                .baseUrl(baseUrl)
                .healthEndpoint(healthEndpoint != null ? healthEndpoint : "/actuator/health")
                .status("UP")
                .version("1.0.0")
                .weight(100)
                .registeredAt(Instant.now())
                .lastHeartbeat(Instant.now())
                .build();
        repo.save(instance);
        log.info("Service registered: {} -> {}", serviceId, baseUrl);
        return instance;
    }

    public Optional<String> resolveUrl(String serviceId) {
        return repo.findByServiceId(serviceId)
                .filter(s -> "UP".equals(s.getStatus()))
                .map(ServiceInstance::getBaseUrl);
    }

    public List<ServiceInstance> listAll() {
        return repo.findAll();
    }

    public void deregister(String serviceId) {
        repo.findByServiceId(serviceId).ifPresent(s -> {
            s.setStatus("DOWN");
            repo.save(s);
            log.info("Service deregistered: {}", serviceId);
        });
    }

    public void heartbeat(String serviceId) {
        repo.findByServiceId(serviceId).ifPresent(s -> {
            s.setLastHeartbeat(Instant.now());
            s.setStatus("UP");
            repo.save(s);
        });
    }

    @Scheduled(fixedRate = 30000)
    void healthCheck() {
        for (ServiceInstance service : repo.findAll()) {
            String url = service.getBaseUrl() + service.getHealthEndpoint();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                service.setStatus(resp.statusCode() < 400 ? "UP" : "DOWN");
            } catch (Exception e) {
                service.setStatus("DOWN");
            }
            service.setLastHeartbeat(Instant.now());
            repo.save(service);
        }
    }

    private void registerIfAbsent(String serviceId, String name, String url, String healthEndpoint) {
        if (!repo.existsByServiceId(serviceId)) {
            register(serviceId, name, url, healthEndpoint);
        }
    }
}
