package com.gateway.controller;

import com.gateway.model.ApiClient;
import com.gateway.security.ApiClientRepository;
import com.gateway.security.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API client registration and token management")
public class AuthController {

    private final ApiClientRepository clientRepo;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    @Operation(summary = "Register a new API client and receive credentials")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (clientRepo.existsByClientId(request.getClientId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Client ID already exists"));
        }

        String apiKey = "gw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String rawSecret = UUID.randomUUID().toString().replace("-", "");

        ApiClient client = ApiClient.builder()
                .clientId(request.getClientId())
                .name(request.getName())
                .apiKey(apiKey)
                .hashedSecret(passwordEncoder.encode(rawSecret))
                .active(true)
                .allowedServices(request.getAllowedServices() != null
                        ? request.getAllowedServices()
                        : List.of("wallet-api", "notification-service"))
                .roles(request.getRoles() != null ? request.getRoles() : List.of("CLIENT"))
                .rateLimitPerMinute(request.getRateLimitPerMinute() > 0
                        ? request.getRateLimitPerMinute() : 60)
                .createdAt(Instant.now())
                .build();
        clientRepo.save(client);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "clientId", client.getClientId(),
                "apiKey", apiKey,
                "secret", rawSecret,
                "message", "Store these credentials securely. The secret cannot be retrieved again."
        ));
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange API key + secret for a JWT token")
    public ResponseEntity<Map<String, Object>> getToken(@RequestBody Map<String, String> credentials) {
        String apiKey = credentials.get("apiKey");
        String secret = credentials.get("secret");

        if (apiKey == null || secret == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey and secret required"));
        }

        ApiClient client = clientRepo.findByApiKey(apiKey).orElse(null);
        if (client == null || !client.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or inactive API key"));
        }

        if (!passwordEncoder.matches(secret, client.getHashedSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid secret"));
        }

        client.setLastUsedAt(Instant.now());
        clientRepo.save(client);

        String token = jwtProvider.generateToken(
                client.getClientId(), client.getRoles(), client.getAllowedServices());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "clientId", client.getClientId(),
                "expiresIn", "3600s"
        ));
    }

    @Data
    public static class RegisterRequest {
        @NotBlank private String clientId;
        @NotBlank private String name;
        private List<String> allowedServices;
        private List<String> roles;
        private int rateLimitPerMinute;
    }
}
