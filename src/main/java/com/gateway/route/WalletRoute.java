package com.gateway.route;

import com.gateway.service.GatewayService;
import com.gateway.transform.MessageTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel routes for wallet service integration.
 * Uses real integration patterns: enrichment, wire tap, SEDA,
 * content-based routing, sensitive field masking, and dead letter channel.
 */
@Component
@RequiredArgsConstructor
public class WalletRoute extends RouteBuilder {

    private final GatewayService gatewayService;
    private final MessageTransformer transformer;

    @Override
    public void configure() {

        errorHandler(deadLetterChannel("seda:dead-letter")
                .maximumRedeliveries(2)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        from("direct:wallet-balance")
                .routeId("wallet-balance")
                .process(exchange -> {
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "GET", "/api/wallet",
                            null, clientIp(exchange));
                    exchange.getIn().setBody(result);
                });

        from("direct:wallet-deposit")
                .routeId("wallet-deposit")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String enriched = transformer.enrichRequest(body, correlationId(exchange), "gateway");
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "POST", "/api/wallet/deposit",
                            enriched, clientIp(exchange));
                    exchange.getIn().setBody(result);
                })
                .wireTap("seda:audit-log");

        from("direct:wallet-transfer")
                .routeId("wallet-transfer")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    log.info("Transfer (masked): {}", transformer.maskSensitiveFields(body));
                    String enriched = transformer.enrichRequest(body, correlationId(exchange), "gateway");
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "POST", "/api/wallet/transfer",
                            enriched, clientIp(exchange));
                    exchange.getIn().setBody(result);
                    exchange.getIn().setHeader("transferResult", result);
                })
                .wireTap("seda:audit-log")
                .to("seda:post-transfer-notification");

        from("seda:post-transfer-notification")
                .routeId("post-transfer-notification")
                .process(exchange -> {
                    String transferResult = exchange.getIn().getHeader("transferResult",
                            exchange.getIn().getBody(String.class), String.class);
                    String payload = transformer.transferResultToNotification(transferResult, "recipient");
                    if (payload != null) {
                        try {
                            gatewayService.proxyRequest("notification-service", "POST",
                                    "/api/notifications", payload, "gateway-internal");
                        } catch (Exception e) {
                            log.warn("Post-transfer notification failed (non-blocking): {}", e.getMessage());
                        }
                    }
                });

        from("seda:audit-log")
                .routeId("audit-log")
                .process(exchange -> {
                    String masked = transformer.maskSensitiveFields(exchange.getIn().getBody(String.class));
                    log.info("AUDIT: {}", masked);
                });

        from("seda:dead-letter")
                .routeId("dead-letter")
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    log.error("Dead letter: {}", cause != null ? cause.getMessage() : "unknown");
                });
    }

    private String clientIp(Exchange ex) {
        return ex.getIn().getHeader("X-Forwarded-For", "127.0.0.1", String.class);
    }

    private String correlationId(Exchange ex) {
        return ex.getIn().getHeader("X-Correlation-ID",
                java.util.UUID.randomUUID().toString().substring(0, 12), String.class);
    }
}
