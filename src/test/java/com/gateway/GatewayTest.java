package com.gateway;

import com.gateway.model.RequestLog;
import com.gateway.service.CircuitBreaker;
import com.gateway.service.RateLimiter;
import com.gateway.service.RequestLogRepository;
import com.gateway.soap.SoapAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GatewayTest {

    @Autowired private RateLimiter rateLimiter;
    @Autowired private CircuitBreaker circuitBreaker;
    @Autowired private SoapAdapter soapAdapter;
    @Autowired private RequestLogRepository logRepository;

    @Test
    @DisplayName("Rate limiter should allow requests within limit")
    void rateLimiter_allowsWithinLimit() {
        for (int i = 0; i < 60; i++) {
            assertTrue(rateLimiter.tryAcquire("test-client-1"));
        }
    }

    @Test
    @DisplayName("Rate limiter should block when limit exceeded")
    void rateLimiter_blocksOverLimit() {
        for (int i = 0; i < 60; i++) {
            rateLimiter.tryAcquire("test-client-2");
        }
        assertFalse(rateLimiter.tryAcquire("test-client-2"));
    }

    @Test
    @DisplayName("Circuit breaker starts closed")
    void circuitBreaker_startsClosed() {
        assertFalse(circuitBreaker.isOpen("test-service"));
        assertEquals("CLOSED", circuitBreaker.getState("test-service"));
    }

    @Test
    @DisplayName("Circuit breaker opens after threshold failures")
    void circuitBreaker_opensAfterFailures() {
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure("failing-service");
        }
        assertTrue(circuitBreaker.isOpen("failing-service"));
        assertEquals("OPEN", circuitBreaker.getState("failing-service"));
    }

    @Test
    @DisplayName("Circuit breaker resets on success")
    void circuitBreaker_resetsOnSuccess() {
        circuitBreaker.recordFailure("flaky-service");
        circuitBreaker.recordFailure("flaky-service");
        circuitBreaker.recordSuccess("flaky-service");
        assertEquals("CLOSED", circuitBreaker.getState("flaky-service"));
    }

    @Test
    @DisplayName("SOAP adapter parses XML request")
    void soapAdapter_parsesRequest() {
        String xml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <SourceAccount>+254700000001</SourceAccount>
                    <DestinationAccount>+254700000002</DestinationAccount>
                    <Amount>5000</Amount>
                    <Currency>KES</Currency>
                  </soap:Body>
                </soap:Envelope>
                """;

        var request = soapAdapter.parseRequest(xml);
        assertEquals("+254700000001", request.getSourceAccount());
        assertEquals("+254700000002", request.getDestinationAccount());
        assertEquals("5000", request.getAmount());
        assertEquals("KES", request.getCurrency());
    }

    @Test
    @DisplayName("SOAP adapter builds success response")
    void soapAdapter_buildsSuccessResponse() {
        String response = soapAdapter.buildSuccessResponse("TXN-001", "OK");
        assertTrue(response.contains("ResultCode>0</ResultCode"));
        assertTrue(response.contains("TXN-001"));
        assertTrue(response.contains("soap:Envelope"));
    }

    @Test
    @DisplayName("SOAP adapter builds error response")
    void soapAdapter_buildsErrorResponse() {
        String response = soapAdapter.buildErrorResponse("TXN-002", "INVALID", "Bad request");
        assertTrue(response.contains("faultcode>INVALID</faultcode"));
        assertTrue(response.contains("Bad request"));
    }

    @Test
    @DisplayName("SOAP adapter converts REST to SOAP XML")
    void soapAdapter_restToSoap() {
        String xml = soapAdapter.toSoapRequest("+254700000001", "+254700000002", "1000", "TXN-X");
        assertTrue(xml.contains("+254700000001"));
        assertTrue(xml.contains("+254700000002"));
        assertTrue(xml.contains("1000"));
        assertTrue(xml.contains("TXN-X"));
    }

    @Test
    @DisplayName("MongoDB stores and retrieves request logs")
    void mongodb_storesLogs() {
        RequestLog log = RequestLog.builder()
                .service("test-service")
                .method("GET")
                .path("/api/test")
                .status(200)
                .durationMs(42)
                .timestamp(Instant.now())
                .build();

        RequestLog saved = logRepository.save(log);
        assertNotNull(saved.getId());

        var retrieved = logRepository.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("test-service", retrieved.get().getService());
        assertEquals(42, retrieved.get().getDurationMs());
    }

    @Test
    @DisplayName("MongoDB queries by service")
    void mongodb_queriesByService() {
        logRepository.save(RequestLog.builder()
                .service("wallet-api").method("POST").path("/transfer")
                .status(200).durationMs(100).timestamp(Instant.now()).build());
        logRepository.save(RequestLog.builder()
                .service("wallet-api").method("GET").path("/balance")
                .status(200).durationMs(50).timestamp(Instant.now()).build());

        var logs = logRepository.findByServiceOrderByTimestampDesc("wallet-api");
        assertTrue(logs.size() >= 2);
    }
}
