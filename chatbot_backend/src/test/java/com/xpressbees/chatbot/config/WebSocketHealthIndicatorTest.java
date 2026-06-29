package com.xpressbees.chatbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebSocketHealthIndicator.
 * Validates: Requirements 1.2, 1.4
 */
@ExtendWith(MockitoExtension.class)
class WebSocketHealthIndicatorTest {

    @Mock
    private ConnectionLimitInterceptor connectionLimitInterceptor;

    @Test
    @DisplayName("Reports UP with 0 active connections")
    void reportsUpWithZeroConnections() {
        int threshold = 800;
        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(connectionLimitInterceptor, threshold);

        when(connectionLimitInterceptor.getActiveConnectionCount()).thenReturn(0);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeConnections", 0);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }

    @Test
    @DisplayName("Reports UP when active connections are exactly at threshold")
    void reportsUpWhenExactlyAtThreshold() {
        int threshold = 800;
        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(connectionLimitInterceptor, threshold);

        when(connectionLimitInterceptor.getActiveConnectionCount()).thenReturn(800);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeConnections", 800);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }

    @Test
    @DisplayName("Reports DEGRADED when active connections are one above threshold")
    void reportsDegradedWhenOneAboveThreshold() {
        int threshold = 800;
        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(connectionLimitInterceptor, threshold);

        when(connectionLimitInterceptor.getActiveConnectionCount()).thenReturn(801);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("activeConnections", 801);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }
}
