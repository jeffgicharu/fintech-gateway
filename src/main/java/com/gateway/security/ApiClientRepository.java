package com.gateway.security;

import com.gateway.model.ApiClient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ApiClientRepository extends MongoRepository<ApiClient, String> {
    Optional<ApiClient> findByApiKey(String apiKey);
    Optional<ApiClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
