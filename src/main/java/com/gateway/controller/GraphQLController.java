package com.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.model.RequestLog;
import com.gateway.model.ServiceHealth;
import com.gateway.registry.ServiceInstance;
import com.gateway.registry.ServiceRegistry;
import com.gateway.service.GatewayService;
import com.gateway.service.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class GraphQLController {

    private final RequestLogRepository logRepository;
    private final GatewayService gatewayService;
    private final ServiceRegistry serviceRegistry;
    private final ObjectMapper objectMapper;

    @QueryMapping
    public RequestLog requestLog(@Argument String id) {
        return logRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<RequestLog> requestLogs(@Argument String service,
                                         @Argument String status,
                                         @Argument Integer limit) {
        int max = limit != null ? limit : 20;
        if (service != null) {
            return logRepository.findByServiceOrderByTimestampDesc(service).stream()
                    .limit(max).collect(Collectors.toList());
        }
        return logRepository.findAll(
                PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent();
    }

    @QueryMapping
    public List<ServiceHealth> serviceHealth() {
        return gatewayService.checkHealth();
    }

    @QueryMapping
    public Map<String, Object> gatewayStats() {
        return gatewayService.getStats();
    }

    @QueryMapping
    public List<ServiceInstance> registeredServices() {
        return serviceRegistry.listAll();
    }

    @MutationMapping
    public Map<String, Object> sendMoney(@Argument Map<String, Object> input) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("recipientPhone", input.get("recipientPhone"));
            payload.put("amount", input.get("amount"));
            payload.put("pin", input.get("pin"));
            payload.put("idempotencyKey", "gql-" + UUID.randomUUID().toString().substring(0, 8));

            String body = objectMapper.writeValueAsString(payload);
            String result = gatewayService.proxyRequest(
                    "wallet-api", "POST", "/api/wallet/transfer", body, "graphql-client");

            return Map.of("success", true, "message", "Transfer initiated", "reference", "GQL-TXN");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @MutationMapping
    public Map<String, Object> sendNotification(@Argument Map<String, Object> input) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("channel", input.getOrDefault("channel", "SMS"));
            payload.put("recipient", input.getOrDefault("recipient", ""));
            if (input.containsKey("templateId")) payload.put("templateId", input.get("templateId"));
            if (input.containsKey("body")) payload.put("body", input.get("body"));
            payload.put("idempotencyKey", "gql-" + UUID.randomUUID().toString().substring(0, 8));

            String body = objectMapper.writeValueAsString(payload);
            String result = gatewayService.proxyRequest(
                    "notification-service", "POST", "/api/notifications", body, "graphql-client");

            return Map.of("success", true, "message", "Notification queued");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
