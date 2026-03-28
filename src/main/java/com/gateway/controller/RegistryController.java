package com.gateway.controller;

import com.gateway.registry.ServiceInstance;
import com.gateway.registry.ServiceRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/registry")
@RequiredArgsConstructor
@Tag(name = "Service Registry", description = "Register, discover, and monitor downstream services")
public class RegistryController {

    private final ServiceRegistry registry;

    @GetMapping
    @Operation(summary = "List all registered services")
    public List<ServiceInstance> list() {
        return registry.listAll();
    }

    @PostMapping
    @Operation(summary = "Register or update a service")
    public ResponseEntity<ServiceInstance> register(@RequestBody RegisterServiceRequest request) {
        ServiceInstance instance = registry.register(
                request.getServiceId(), request.getName(),
                request.getBaseUrl(), request.getHealthEndpoint());
        return ResponseEntity.status(HttpStatus.CREATED).body(instance);
    }

    @PostMapping("/{serviceId}/heartbeat")
    @Operation(summary = "Send a heartbeat for a service")
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable String serviceId) {
        registry.heartbeat(serviceId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/{serviceId}")
    @Operation(summary = "Deregister a service")
    public ResponseEntity<Map<String, String>> deregister(@PathVariable String serviceId) {
        registry.deregister(serviceId);
        return ResponseEntity.ok(Map.of("status", "deregistered"));
    }

    @Data
    public static class RegisterServiceRequest {
        private String serviceId;
        private String name;
        private String baseUrl;
        private String healthEndpoint;
    }
}
