package com.gateway.registry;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a registered downstream service. Services register themselves
 * on startup and the gateway routes to them dynamically instead of using
 * hardcoded URLs.
 */
@Document(collection = "service_registry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceInstance {

    @Id
    private String id;

    @Indexed(unique = true)
    private String serviceId;

    private String name;

    private String baseUrl;

    private String healthEndpoint;

    private String status;

    private String version;

    private int weight;

    private Instant registeredAt;

    private Instant lastHeartbeat;
}
