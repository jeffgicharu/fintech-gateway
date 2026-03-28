package com.gateway.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transforms messages as they pass through the gateway.
 * Handles header injection, field mapping, payload enrichment,
 * and format conversion between services that use different schemas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageTransformer {

    private final ObjectMapper objectMapper;

    /**
     * Enrich a request with gateway metadata before forwarding to a downstream service.
     * Adds correlation ID, timestamp, and source tracking.
     */
    public String enrichRequest(String body, String correlationId, String sourceService) {
        try {
            ObjectNode node = body != null && !body.isBlank()
                    ? (ObjectNode) objectMapper.readTree(body)
                    : objectMapper.createObjectNode();

            node.put("_gatewayCorrelationId", correlationId);
            node.put("_gatewayTimestamp", Instant.now().toString());
            node.put("_sourceService", sourceService);

            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Could not enrich request body: {}", e.getMessage());
            return body;
        }
    }

    /**
     * Transform a wallet transfer response into a notification payload.
     * This is the kind of cross-service field mapping a gateway does constantly.
     */
    public String transferResultToNotification(String transferResponse, String recipientPhone) {
        try {
            JsonNode transfer = objectMapper.readTree(transferResponse);
            JsonNode data = transfer.has("data") ? transfer.get("data") : transfer;

            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("channel", "SMS");
            notification.put("recipient", recipientPhone);
            notification.put("templateId", "transaction_success");
            notification.put("idempotencyKey", "notif-" + UUID.randomUUID().toString().substring(0, 8));

            ObjectNode params = objectMapper.createObjectNode();
            params.put("txnId", data.path("reference").asText("N/A"));
            params.put("amount", data.path("amount").asText("0"));
            params.put("recipient", data.path("receiverPhone").asText(""));
            params.put("balance", "N/A");
            notification.set("templateParams", params);

            return objectMapper.writeValueAsString(notification);
        } catch (Exception e) {
            log.error("Failed to transform transfer result to notification: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Mask sensitive fields in a request body before logging.
     * PINs, passwords, and account numbers are replaced with asterisks.
     */
    public String maskSensitiveFields(String body) {
        if (body == null) return null;
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(body);
            for (String field : new String[]{"pin", "password", "secret", "cvv", "cardNumber"}) {
                if (node.has(field)) {
                    node.put(field, "****");
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return body.replaceAll("\"(pin|password|secret|cvv)\"\\s*:\\s*\"[^\"]*\"",
                    "\"$1\":\"****\"");
        }
    }

    /**
     * Map fields between different service schemas.
     * For example, wallet-api uses "phoneNumber" but notification-service uses "recipient".
     */
    public String mapFields(String body, Map<String, String> fieldMapping) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(body);
            ObjectNode mapped = objectMapper.createObjectNode();

            node.fields().forEachRemaining(entry -> {
                String targetField = fieldMapping.getOrDefault(entry.getKey(), entry.getKey());
                mapped.set(targetField, entry.getValue());
            });

            return objectMapper.writeValueAsString(mapped);
        } catch (Exception e) {
            log.warn("Field mapping failed: {}", e.getMessage());
            return body;
        }
    }
}
