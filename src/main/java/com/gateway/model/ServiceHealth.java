package com.gateway.model;

import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceHealth {
    private String name;
    private String url;
    private String status;
    private long responseTimeMs;
    private Instant lastChecked;
}
