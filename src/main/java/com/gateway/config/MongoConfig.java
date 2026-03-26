package com.gateway.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.InetSocketAddress;

/**
 * In-memory MongoDB for development and testing.
 * In production, connect to a real MongoDB instance via spring.data.mongodb.uri.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.mongodb.uri", matchIfMissing = true, havingValue = "")
public class MongoConfig {

    private MongoServer server;

    @Bean
    public MongoTemplate mongoTemplate() {
        server = new MongoServer(new MemoryBackend());
        InetSocketAddress address = server.bind();
        MongoClient client = MongoClients.create("mongodb://" + address.getHostString() + ":" + address.getPort());
        return new MongoTemplate(client, "gateway");
    }

    @PreDestroy
    void shutdown() {
        if (server != null) server.shutdown();
    }
}
