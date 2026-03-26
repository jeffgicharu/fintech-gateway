package com.gateway.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "request_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    private String id;

    @Indexed
    private String service;

    private String method;
    private String path;
    private int status;
    private long durationMs;
    private String requestBody;
    private String responseBody;
    private String errorMessage;

    @Indexed
    private String clientIp;

    @Indexed
    private Instant timestamp;
}
