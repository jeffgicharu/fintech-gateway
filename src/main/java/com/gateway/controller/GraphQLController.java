package com.gateway.controller;

import com.gateway.model.RequestLog;
import com.gateway.model.ServiceHealth;
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

    @QueryMapping
    public RequestLog requestLog(@Argument String id) {
        return logRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<RequestLog> requestLogs(@Argument String service,
                                         @Argument String status,
                                         @Argument Integer limit) {
        int maxResults = limit != null ? limit : 20;

        if (service != null) {
            return logRepository.findByServiceOrderByTimestampDesc(service).stream()
                    .limit(maxResults).collect(Collectors.toList());
        }

        return logRepository.findAll(
                PageRequest.of(0, maxResults, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).getContent();
    }

    @QueryMapping
    public List<ServiceHealth> serviceHealth() {
        return gatewayService.checkHealth();
    }

    @QueryMapping
    public Map<String, Object> gatewayStats() {
        return gatewayService.getStats();
    }

    @MutationMapping
    public Map<String, Object> sendMoney(@Argument Map<String, Object> input) {
        try {
            String body = String.format(
                    "{\"recipientPhone\":\"%s\",\"amount\":%s,\"pin\":\"%s\",\"idempotencyKey\":\"%s\"}",
                    input.get("recipientPhone"),
                    input.get("amount"),
                    input.get("pin"),
                    "gql-" + UUID.randomUUID().toString().substring(0, 8));

            String result = gatewayService.proxyRequest(
                    "wallet-api", "POST", "/api/wallet/transfer",
                    body, "graphql-client");

            return Map.of("success", true, "message", "Transfer initiated", "reference", "GQL-TXN");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @MutationMapping
    public Map<String, Object> sendNotification(@Argument Map<String, Object> input) {
        try {
            String channel = (String) input.getOrDefault("channel", "SMS");
            String recipient = (String) input.getOrDefault("recipient", "");
            String body = (String) input.get("body");
            String templateId = (String) input.get("templateId");

            StringBuilder json = new StringBuilder("{");
            json.append("\"channel\":\"").append(channel).append("\",");
            json.append("\"recipient\":\"").append(recipient).append("\",");
            if (templateId != null) {
                json.append("\"templateId\":\"").append(templateId).append("\",");
            }
            if (body != null) {
                json.append("\"body\":\"").append(body).append("\",");
            }
            json.append("\"idempotencyKey\":\"gql-").append(UUID.randomUUID().toString().substring(0, 8)).append("\"}");

            String result = gatewayService.proxyRequest(
                    "notification-service", "POST", "/api/notifications",
                    json.toString(), "graphql-client");

            return Map.of("success", true, "message", "Notification queued");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
