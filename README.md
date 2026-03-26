# Fintech Gateway

An API gateway and integration layer that sits between clients and downstream microservices. Built with **Apache Camel** for message routing, **GraphQL** for flexible querying, and a **SOAP/XML adapter** for legacy banking system integration — with all traffic logged to **MongoDB** for analytics, debugging, and compliance.

In fintech, the gateway is where modern meets legacy. Mobile apps speak REST and GraphQL; core banking systems speak SOAP and XML. This project bridges that gap while adding the resilience patterns (circuit breakers, rate limiting) needed to keep services available at 99.99%.

## Why This Architecture

| Problem | Solution | Implementation |
|---|---|---|
| Client needs REST, core banking needs SOAP | Protocol translation | `SoapAdapter` converts between JSON and SOAP envelopes bidirectionally |
| Mobile app wants flexible queries | GraphQL alongside REST | Same data, two interfaces — REST for transactions, GraphQL for analytics |
| Downstream service goes down | Circuit breaker | Per-service failure tracking with CLOSED → OPEN → HALF_OPEN states |
| Single client overwhelms the API | Rate limiting | Sliding window per-client (60 req/min default) with remaining quota in headers |
| Can't debug failed requests | MongoDB traffic logging | Every request/response logged with service, duration, status, and correlation ID |
| Need real-time integration patterns | Apache Camel routes | Content-based routing, SEDA async messaging, dead letter channel for failures |

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Integration | Apache Camel 4.4 |
| APIs | REST + GraphQL + SOAP/XML |
| Analytics DB | MongoDB (in-memory `mongo-java-server` for dev) |
| Resilience | Custom circuit breaker + rate limiter |

## Architecture

```
Clients
  │
  ├── REST ──────► GatewayController ──► Camel Routes ──► wallet-api
  │                     │                                 notification-service
  ├── GraphQL ──► GraphQLController ──► GatewayService
  │
  └── SOAP/XML ─► SoapController ───► SoapAdapter ───► REST translation
                        │
                        ▼
                 Rate Limiter → Circuit Breaker → Proxy → Log to MongoDB
```

### Camel Routes

| Route | Pattern | Purpose |
|---|---|---|
| `wallet-balance` | Direct | Proxy balance queries |
| `wallet-transfer` | Direct → SEDA | Transfer, then async notification trigger |
| `post-transfer-notification` | SEDA | Fire-and-forget notification after transfer completes |
| `send-notification` | Direct | Proxy notification dispatch |
| `dead-letter` | Dead Letter Channel | Capture and log failed messages after retries |

## API Endpoints

### REST

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/gateway/proxy/{service}/**` | Proxy to downstream service |
| GET | `/api/gateway/health` | Service health + circuit breaker states |
| GET | `/api/gateway/stats` | Request volume and latency analytics |
| GET | `/api/gateway/logs` | Recent traffic from MongoDB |

### SOAP

| Method | Endpoint | Description |
|---|---|---|
| POST | `/soap/transaction` | Process SOAP XML transaction |
| POST | `/soap/convert/rest-to-soap` | Convert JSON request to SOAP envelope |

### GraphQL

Available at `/graphiql` — query request logs, service health, and gateway stats with flexible field selection.

## Running

```bash
mvn spring-boot:run   # http://localhost:8383/swagger-ui.html
```

No external databases needed — uses in-memory MongoDB (`mongo-java-server`).

## Testing

```bash
mvn test   # 11 tests
```

Covers: rate limiter allow/block, circuit breaker states (closed/open/reset), SOAP XML parsing, success/error response generation, REST-to-SOAP conversion, MongoDB storage and querying.

## License

MIT
