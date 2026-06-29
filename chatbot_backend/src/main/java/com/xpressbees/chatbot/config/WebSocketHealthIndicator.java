package com.xpressbees.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator that reports WebSocket connection pool status.
 * Reports DEGRADED when active connections exceed the configured threshold, UP otherwise.
 */
@Component
public class WebSocketHealthIndicator implements HealthIndicator {

    private final ConnectionLimitInterceptor connectionLimitInterceptor;
    private final int degradedThreshold;

    public WebSocketHealthIndicator(
            ConnectionLimitInterceptor connectionLimitInterceptor,
            @Value("${chatbot.health.websocket.degraded-threshold:800}") int degradedThreshold) {
        this.connectionLimitInterceptor = connectionLimitInterceptor;
        this.degradedThreshold = degradedThreshold;
    }

    @Override
    public Health health() {
        int activeConnections = connectionLimitInterceptor.getActiveConnectionCount();
        if (activeConnections > degradedThreshold) {
            return Health.status("DEGRADED")
                    .withDetail("activeConnections", activeConnections)
                    .withDetail("threshold", degradedThreshold)
                    .build();
        }
        return Health.up()
                .withDetail("activeConnections", activeConnections)
                .withDetail("threshold", degradedThreshold)
                .build();
    }
}
