# Fintech Gateway

API gateway and integration layer built with **Apache Camel**, **Spring Boot**, **GraphQL**, and **MongoDB**. Routes requests between microservices (wallet-api, notification-service), provides a SOAP/XML adapter for legacy banking systems, and logs all traffic to MongoDB for analytics.

## Features

- **Apache Camel Routes** - Integration patterns: content-based routing, dead letter channel, SEDA async messaging, error handling with retry
- **GraphQL API** - Query request logs, service health, and stats; mutate via sendMoney and sendNotification
- **SOAP/XML Adapter** - Accepts SOAP envelope requests and translates to REST, and vice versa — for legacy banking system integration
- **MongoDB Analytics** - All gateway traffic is logged to MongoDB with service, status, duration, request/response bodies
- **Rate Limiting** - Sliding window per-client rate limiter (configurable requests/minute)
- **Circuit Breaker** - Per-service circuit breaker with configurable threshold and half-open recovery
- **Service Health Checks** - Monitors downstream services and exposes health status
- **REST + Swagger UI** - Full OpenAPI documentation

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Integration | Apache Camel 4.4 |
| API | REST + GraphQL + SOAP/XML |
| Database | MongoDB (mongo-java-server for dev) |
| Language | Java 17 |
| Build | Maven |
| CI/CD | GitHub Actions |
| Container | Docker |

## Getting Started

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8383` with an in-memory MongoDB (no external database needed).

- Swagger UI: [http://localhost:8383/swagger-ui.html](http://localhost:8383/swagger-ui.html)
- GraphiQL: [http://localhost:8383/graphiql](http://localhost:8383/graphiql)

### With Docker (real MongoDB)

```bash
docker compose up
```

## API Endpoints

### REST Gateway

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/gateway/proxy/{service}/**` | Proxy request to downstream service |
| GET | `/api/gateway/health` | Health check all services |
| GET | `/api/gateway/stats` | Request analytics |
| GET | `/api/gateway/logs` | Recent request logs from MongoDB |
| GET | `/api/gateway/logs/service/{service}` | Logs filtered by service |

### SOAP

| Method | Endpoint | Description |
|---|---|---|
| POST | `/soap/transaction` | Process SOAP XML transaction |
| POST | `/soap/convert/rest-to-soap` | Convert REST JSON to SOAP XML |

### GraphQL

```graphql
# Query gateway stats
query {
  gatewayStats {
    totalRequests
    successCount
    failureCount
    avgResponseTimeMs
  }
}

# Query service health
query {
  serviceHealth {
    name
    status
    responseTimeMs
  }
}

# Query request logs
query {
  requestLogs(service: "wallet-api", limit: 10) {
    method
    path
    status
    durationMs
    timestamp
  }
}
```

### SOAP Example

```bash
curl -X POST http://localhost:8383/soap/transaction \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
  <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Body>
      <SourceAccount>+254700000001</SourceAccount>
      <DestinationAccount>+254700000002</DestinationAccount>
      <Amount>5000</Amount>
      <Currency>KES</Currency>
    </soap:Body>
  </soap:Envelope>'
```

## Architecture

```
Clients
  │
  ├── REST ──────► GatewayController ──► Camel Routes ──► Downstream Services
  ├── GraphQL ──► GraphQLController ──► GatewayService ──► (wallet-api, notification-service)
  └── SOAP/XML ─► SoapController ───► SoapAdapter ────► REST translation
                        │
                        ▼
                  RateLimiter ──► CircuitBreaker
                        │
                        ▼
                  RequestLogRepository (MongoDB)
```

### Apache Camel Routes

| Route | Pattern | Purpose |
|---|---|---|
| `wallet-balance` | Direct | Proxy balance check |
| `wallet-transfer` | Direct → SEDA | Transfer with async notification trigger |
| `post-transfer-notification` | SEDA (async) | Fire-and-forget notification after transfer |
| `send-notification` | Direct | Proxy notification send |
| `dead-letter` | Dead Letter Channel | Handle failed messages after retries |

## Configuration

| Property | Default | Description |
|---|---|---|
| `gateway.services.wallet-api` | http://localhost:8080 | Wallet API URL |
| `gateway.services.notification-service` | http://localhost:8282 | Notification service URL |
| `gateway.rate-limit.requests-per-minute` | 60 | Rate limit per client |
| `gateway.circuit-breaker.threshold` | 5 | Failures before circuit opens |
| `gateway.circuit-breaker.half-open-after-ms` | 30000 | Recovery test interval |

## Running Tests

```bash
mvn test
```

11 tests covering:
- Rate limiter allows within limit and blocks over limit
- Circuit breaker states (closed, open, half-open, reset on success)
- SOAP XML parsing, success/error response generation, REST-to-SOAP conversion
- MongoDB document storage and service-based queries

## License

MIT
