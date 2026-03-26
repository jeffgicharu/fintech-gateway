package com.gateway.service;

import com.gateway.model.RequestLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface RequestLogRepository extends MongoRepository<RequestLog, String> {

    List<RequestLog> findByServiceOrderByTimestampDesc(String service);

    List<RequestLog> findByStatusOrderByTimestampDesc(int status);

    long countByService(String service);

    long countByStatusGreaterThanEqual(int status);

    long countByStatusLessThan(int status);

    long countByTimestampAfter(Instant since);
}
