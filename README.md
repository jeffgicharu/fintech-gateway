# Fintech Gateway

In financial services, your mobile app talks REST and JSON. But the core banking system on the other end? It still speaks SOAP and XML. And somewhere between the two, you need rate limiting, circuit breakers, and a way to figure out why a request failed at 3am.

This project sits in that middle layer. It routes requests to downstream microservices using **Apache Camel**, exposes a **GraphQL** API for flexible analytics queries, translates between **REST and SOAP** for legacy system integration, and logs every request to **MongoDB** so nothing disappears into a black hole.

## What It Does

- **Routes requests** to downstream services (wallet-api, notification-service) through Camel integration routes
- **Translates protocols**: accepts SOAP/XML from legacy banking systems and converts to REST/JSON (and vice versa)
- **Exposes GraphQL** alongside REST. Same data, but clients can query exactly what they need
- **Rate limits** per client with a sliding window counter, remaining quota returned in response headers
- **Circuit breaker** per downstream service. Stops hammering a service that's already down, tests recovery automatically
- **Logs everything** to MongoDB: service, method, path, status, duration, request body, response body

## How the Camel Routes Work

Apache Camel is an integration framework. Instead of writing HTTP client code directly, you define routes that describe how messages flow between systems. This project has five routes:

- **wallet-balance**: proxies balance queries to the wallet API
- **wallet-transfer**: processes a transfer, then fires an async notification via SEDA (Camel's in-memory queue)
- **post-transfer-notification**: picks up the async message and triggers a notification
- **send-notification**: proxies notification dispatch
- **dead-letter**: catches messages that failed after all retries and logs them for investigation

The SEDA pattern is worth calling out. After a transfer completes, the notification doesn't block the response. It goes into an in-memory queue and gets processed separately. If the notification service is down, the transfer still succeeds.

## Quick Start

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8383/swagger-ui.html
# GraphiQL:   http://localhost:8383/graphiql
```

No external databases needed. Uses in-memory MongoDB.

## SOAP Example

This is how a legacy banking system would send a transaction:

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

The response comes back as a SOAP envelope. You can also convert a JSON request to SOAP format using `/soap/convert/rest-to-soap`.

## GraphQL

Open [http://localhost:8383/graphiql](http://localhost:8383/graphiql) and try:

```graphql
{
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

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/gateway/proxy/{service}/**` | Forward request to a downstream service |
| GET | `/api/gateway/health` | Health of all services + circuit breaker states |
| GET | `/api/gateway/stats` | Request volume and latency stats |
| GET | `/api/gateway/logs` | Recent requests from MongoDB |
| POST | `/soap/transaction` | Process a SOAP XML transaction |
| POST | `/soap/convert/rest-to-soap` | Convert JSON to SOAP envelope |

## Built With

Spring Boot 3.2, Apache Camel 4.4, Spring GraphQL, MongoDB (mongo-java-server for dev), Java 17, Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 11 tests
```

Covers rate limiter (allow and block), circuit breaker states (closed, open, reset on success), SOAP XML parsing, success and error response generation, REST-to-SOAP conversion, MongoDB storage, and service-based queries.

## License

MIT
