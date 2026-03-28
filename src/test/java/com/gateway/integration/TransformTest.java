package com.gateway.integration;

import com.gateway.transform.MessageTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransformTest {

    @Autowired private MessageTransformer transformer;

    @Test
    @DisplayName("Enrich request adds gateway metadata")
    void enrichRequest_addsMetadata() {
        String result = transformer.enrichRequest("{\"amount\":5000}", "corr-123", "wallet-api");
        assertTrue(result.contains("_gatewayCorrelationId"));
        assertTrue(result.contains("corr-123"));
        assertTrue(result.contains("_gatewayTimestamp"));
    }

    @Test
    @DisplayName("Mask sensitive fields replaces PIN and password")
    void maskSensitiveFields_masksCorrectly() {
        String input = "{\"amount\":5000,\"pin\":\"1234\",\"password\":\"secret123\"}";
        String masked = transformer.maskSensitiveFields(input);
        assertTrue(masked.contains("****"));
        assertFalse(masked.contains("1234"));
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("5000"));
    }

    @Test
    @DisplayName("Field mapping renames fields correctly")
    void mapFields_renamesFields() {
        String input = "{\"phoneNumber\":\"+254700000001\",\"amount\":5000}";
        String mapped = transformer.mapFields(input, Map.of("phoneNumber", "recipient"));
        assertTrue(mapped.contains("recipient"));
        assertFalse(mapped.contains("phoneNumber"));
    }

    @Test
    @DisplayName("Transform transfer result to notification payload")
    void transferToNotification_producesValidPayload() {
        String transferResult = "{\"data\":{\"reference\":\"TRF-001\",\"amount\":5000,\"receiverPhone\":\"+254700000002\"}}";
        String notification = transformer.transferResultToNotification(transferResult, "+254700000001");
        assertNotNull(notification);
        assertTrue(notification.contains("transaction_success"));
        assertTrue(notification.contains("TRF-001"));
    }
}
