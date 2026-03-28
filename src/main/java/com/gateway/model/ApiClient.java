package com.gateway.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Represents a registered API client (merchant, internal service, or third-party app).
 * Each client gets an API key for authentication and can be scoped to specific services.
 */
@Document(collection = "api_clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiClient {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    private String name;

    @Indexed(unique = true)
    private String apiKey;

    private String hashedSecret;

    private boolean active;

    private List<String> allowedServices;

    private List<String> roles;

    private int rateLimitPerMinute;

    private Instant createdAt;

    private Instant lastUsedAt;
}
