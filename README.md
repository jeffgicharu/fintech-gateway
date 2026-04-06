# Fintech Gateway

In financial services, your mobile app talks REST and JSON. But the core banking system on the other end? It still speaks SOAP and XML. And somewhere between the two, you need authentication, rate limiting, circuit breakers, and a way to figure out why a request failed at 3am.

This project sits in that middle layer. It routes requests to downstream microservices using **Apache Camel**, secures them with **JWT authentication and role-based access**, exposes a **GraphQL** API alongside REST, translates between **REST and SOAP** using proper DOM XML parsing, transforms messages as they pass between services, and logs every request to **MongoDB**.

## What It Does

- **Authenticates API clients** with JWT tokens. Clients register, receive an API key and secret, exchange them for a short-lived JWT, and use that to access gateway endpoints. Roles control what each client can do.
- **Discovers services dynamically** through a built-in registry backed by MongoDB. Services register on startup and the gateway resolves URLs at request time instead of relying on hardcoded config. A background health check runs every 30 seconds.
- **Routes requests through Camel** using real integration patterns: message enrichment (injects correlation IDs and timestamps), wire tap (logs without blocking the request), SEDA (async handoff to notification pipeline after transfers), and dead letter channel (captures failed messages).
- **Transforms messages** between services. The gateway masks sensitive fields (PINs, passwords) before logging, maps field names between different service schemas, and converts transfer results into notification payloads automatically.
- **Translates SOAP/XML** using DOM parsing with namespace support, entity escaping, and XML validation. Not string concatenation.
- **Rate limits** per client with a sliding window counter. Remaining quota is returned in response headers.
- **Breaks circuits** per downstream service. After 5 consecutive failures, the circuit opens and the gateway stops sending requests until the service recovers.
- **Logs everything** to MongoDB for debugging and compliance.

## How Authentication Works

1. Register a client: `POST /api/auth/register` with a name and client ID. You get back an API key and secret.
2. Get a token: `POST /api/auth/token` with the API key and secret. You get a JWT valid for 1 hour.
3. Use the token: pass `Authorization: Bearer <token>` on every request to `/api/gateway/*`.

Admin endpoints (`/api/admin/*`) require the ADMIN role. Public endpoints (Swagger, SOAP, GraphQL playground) don't require auth.

## Camel Integration Patterns

Apache Camel is an integration framework. Instead of writing HTTP client boilerplate, you define routes that describe how messages flow. This project uses six patterns:

| Route | Pattern | What it does |
|---|---|---|
| wallet-deposit | Enrichment + Wire Tap | Injects gateway metadata into the request body, proxies to wallet API, wire-taps a copy to the audit log |
| wallet-transfer | Enrichment + SEDA + Wire Tap | Enriches, proxies, wire-taps to audit, then drops the result into a SEDA queue for async notification |
| post-transfer-notification | SEDA Consumer | Picks up transfer results from the queue, transforms them into notification payloads, and dispatches without blocking |
| audit-log | Wire Tap Sink | Receives tapped messages, masks sensitive fields, and logs for compliance |
| dead-letter | Dead Letter Channel | Captures messages that failed after all retries |

The SEDA pattern is the important one. After a transfer completes, the notification goes into an in-memory queue and gets processed separately. If the notification service is down, the transfer still succeeds and the customer still gets their money.

## Quick Start

```bash
mvn spring-boot:run
```

- Swagger UI: [http://localhost:8383/swagger-ui.html](http://localhost:8383/swagger-ui.html)
- GraphiQL: [http://localhost:8383/graphiql](http://localhost:8383/graphiql)

No external databases needed. Uses in-memory MongoDB.

## Try It Out

```bash
# 1. Register an API client
curl -X POST http://localhost:8383/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"clientId":"my-app","name":"My Mobile App"}'

# 2. Get a JWT token (use the apiKey and secret from step 1)
curl -X POST http://localhost:8383/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"gw_...","secret":"..."}'

# 3. Use the token to access gateway endpoints
curl http://localhost:8383/api/gateway/stats \
  -H "Authorization: Bearer <token>"

# 4. Send a SOAP transaction (public, no auth needed)
curl -X POST http://localhost:8383/soap/transaction \
  -H "Content-Type: text/xml" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Body>
      <SourceAccount>+254700000001</SourceAccount>
      <DestinationAccount>+254700000002</DestinationAccount>
      <Amount>5000</Amount>
      <Currency>KES</Currency>
    </soap:Body>
  </soap:Envelope>'
```

## GraphQL

Open [http://localhost:8383/graphiql](http://localhost:8383/graphiql) and try:

```graphql
{
  registeredServices {
    serviceId
    name
    baseUrl
    status
  }
  serviceHealth {
    name
    status
    responseTimeMs
  }
  gatewayStats {
    totalRequests
    successCount
    avgResponseTimeMs
  }
}
```

## API Reference

### Authentication (public)

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/auth/register` | Register API client, get key + secret |
| POST | `/api/auth/token` | Exchange key + secret for JWT |

### Gateway (requires JWT)

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/gateway/proxy/{service}/**` | Forward request to downstream service |
| GET | `/api/gateway/health` | Service health + circuit breaker states |
| GET | `/api/gateway/stats` | Request volume and latency stats |
| GET | `/api/gateway/logs` | Recent traffic from MongoDB |

### Service Registry (requires ADMIN role)

| Method | Endpoint | What it does |
|---|---|---|
| GET | `/api/admin/registry` | List all registered services |
| POST | `/api/admin/registry` | Register or update a service |
| POST | `/api/admin/registry/{id}/heartbeat` | Send heartbeat |
| DELETE | `/api/admin/registry/{id}` | Deregister a service |

### SOAP (public)

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/soap/transaction` | Process SOAP XML transaction |
| POST | `/soap/convert/rest-to-soap` | Convert JSON to SOAP envelope |

## Project Structure

```
src/main/java/com/gateway/
├── GatewayApplication.java
├── config/               CorsConfig, SecurityConfig, ExceptionHandler, SchedulingConfig
├── controller/           AuthController, GatewayController, GraphQLController, RegistryController, SoapController
├── model/                ApiClient, RequestLog, ServiceHealth, SoapTransactionRequest
├── registry/             ServiceRegistry, ServiceInstance, ServiceRegistryRepository
├── route/                WalletRoute (enrichment, wire tap, SEDA), NotificationRoute
├── security/             JwtProvider, JwtAuthFilter, ApiClientRepository
├── service/              GatewayService, CircuitBreaker, RateLimiter, RequestLogRepository
├── soap/                 SoapAdapter (DOM parsing, XML validation, escaping)
└── transform/            MessageTransformer (enrichment, masking, field mapping, format conversion)
```

## Built With

Spring Boot 3.2, Apache Camel 4.4, Spring Security + JWT, Spring GraphQL, MongoDB, ISO 20022, Java 17, Docker, Kubernetes + Helm, GitHub Actions CI, Prometheus metrics.

## Tests

```bash
mvn test   # 25 tests
```

**Unit tests (11):** rate limiter allow/block, circuit breaker states, SOAP XML parsing with DOM, success/error response generation, REST-to-SOAP conversion, MongoDB storage and queries.

**Integration tests (14):** API client registration, duplicate rejection, JWT token exchange, invalid credentials, protected endpoint rejection without token, protected endpoint access with valid token, SOAP endpoint with valid/invalid/malformed XML, REST-to-SOAP conversion, message enrichment, sensitive field masking, field mapping, transfer-to-notification transformation.

## Performance Testing

JMeter test plans are in `src/test/jmeter/`. Simulates **150 concurrent users** hitting the gateway for 90 seconds:

- SOAP transaction processing with randomized account numbers and amounts (assert 200 status + ResultCode in response body, under 1s)
- Health check endpoint (assert under 200ms)
- GraphQL query for service health and gateway stats (assert under 1s)

Tests the gateway's ability to handle concurrent SOAP XML parsing, protocol translation, and GraphQL query resolution under load.

Run with: `jmeter -n -t src/test/jmeter/fintech-gateway-load-test.jmx -l results.jtl`

## Code Quality

- **JaCoCo** code coverage with threshold enforcement and CI artifact upload
- **SonarCloud** static analysis integrated into CI pipeline
- **OWASP Dependency Check** for vulnerability scanning in dependencies

## License

MIT
