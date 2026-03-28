package com.gateway.registry;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRegistryRepository extends MongoRepository<ServiceInstance, String> {
    Optional<ServiceInstance> findByServiceId(String serviceId);
    List<ServiceInstance> findByStatus(String status);
    boolean existsByServiceId(String serviceId);
}
