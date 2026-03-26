package com.gateway.config;

import com.gateway.service.GatewayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(GatewayService.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(GatewayService.RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(), "status", 429));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(GatewayService.ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(GatewayService.ServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage(), "status", 503));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage(), "status", 400));
    }
}
