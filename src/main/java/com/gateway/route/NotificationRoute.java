package com.gateway.route;

import com.gateway.service.GatewayService;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel route for notification service integration.
 * Uses SEDA for async processing and content-based routing by channel.
 */
@Component
@RequiredArgsConstructor
public class NotificationRoute extends RouteBuilder {

    private final GatewayService gatewayService;

    @Override
    public void configure() {

        from("direct:send-notification")
                .routeId("send-notification")
                .log("Routing notification: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String result = gatewayService.proxyRequest(
                            "notification-service", "POST", "/api/notifications",
                            body, "gateway-internal");
                    exchange.getIn().setBody(result);
                });

        from("direct:send-bulk-notification")
                .routeId("send-bulk-notification")
                .log("Routing bulk notification")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String result = gatewayService.proxyRequest(
                            "notification-service", "POST", "/api/notifications/bulk",
                            body, "gateway-internal");
                    exchange.getIn().setBody(result);
                });

        from("direct:notification-stats")
                .routeId("notification-stats")
                .process(exchange -> {
                    String result = gatewayService.proxyRequest(
                            "notification-service", "GET", "/api/notifications/stats",
                            null, "gateway-internal");
                    exchange.getIn().setBody(result);
                });
    }
}
