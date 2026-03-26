package com.gateway.route;

import com.gateway.service.GatewayService;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel route that proxies requests to the Wallet API.
 * Demonstrates Camel's content-based routing, error handling,
 * and integration patterns.
 */
@Component
@RequiredArgsConstructor
public class WalletRoute extends RouteBuilder {

    private final GatewayService gatewayService;

    @Override
    public void configure() {

        // Error handling with dead letter channel
        errorHandler(deadLetterChannel("seda:dead-letter")
                .maximumRedeliveries(2)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // Proxy wallet operations through Camel
        from("direct:wallet-balance")
                .routeId("wallet-balance")
                .log("Checking balance via gateway")
                .process(exchange -> {
                    String token = exchange.getIn().getHeader("Authorization", String.class);
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "GET", "/api/wallet",
                            null, getClientIp(exchange));
                    exchange.getIn().setBody(result);
                });

        from("direct:wallet-deposit")
                .routeId("wallet-deposit")
                .log("Processing deposit via gateway: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "POST", "/api/wallet/deposit",
                            body, getClientIp(exchange));
                    exchange.getIn().setBody(result);
                });

        from("direct:wallet-transfer")
                .routeId("wallet-transfer")
                .log("Processing transfer via gateway")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String result = gatewayService.proxyRequest(
                            "wallet-api", "POST", "/api/wallet/transfer",
                            body, getClientIp(exchange));
                    exchange.getIn().setBody(result);
                })
                .to("seda:post-transfer-notification");

        // Async notification after successful transfer
        from("seda:post-transfer-notification")
                .routeId("post-transfer-notification")
                .log("Triggering post-transfer notification")
                .process(exchange -> {
                    // Extract transfer details and send SMS notification
                    String transferResult = exchange.getIn().getBody(String.class);
                    log.info("Transfer completed, notification pipeline triggered");
                });

        // Dead letter handler
        from("seda:dead-letter")
                .routeId("dead-letter")
                .log("Dead letter received: ${exception.message}")
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    log.error("Failed message sent to dead letter: {}",
                            cause != null ? cause.getMessage() : "unknown");
                });
    }

    private String getClientIp(Exchange exchange) {
        return exchange.getIn().getHeader("X-Forwarded-For", "127.0.0.1", String.class);
    }
}
