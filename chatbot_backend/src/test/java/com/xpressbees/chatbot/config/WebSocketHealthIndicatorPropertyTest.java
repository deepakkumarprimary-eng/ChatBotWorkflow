package com.xpressbees.chatbot.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for WebSocketHealthIndicator threshold correctness.
 *
 * <p><b>Validates: Requirements 1.4</b></p>
 *
 * <p>Property: For any non-negative active connection count and any positive degraded threshold,
 * the WebSocketHealthIndicator SHALL report status as DEGRADED when active connections exceed
 * the threshold, and UP when active connections are at or below the threshold.</p>
 */
class WebSocketHealthIndicatorPropertyTest {

    private static final Status DEGRADED_STATUS = new Status("DEGRADED");

    @Property(tries = 200)
    @Tag("production-readiness")
    @Label("WebSocket health indicator reports DEGRADED when connectionCount > threshold")
    void degradedWhenConnectionsExceedThreshold(
            @ForAll @IntRange(min = 1, max = 10000) int threshold,
            @ForAll @IntRange(min = 1, max = 10000) int excess) {

        int connectionCount = threshold + excess; // always > threshold

        ConnectionLimitInterceptor mockInterceptor = mock(ConnectionLimitInterceptor.class);
        when(mockInterceptor.getActiveConnectionCount()).thenReturn(connectionCount);

        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(mockInterceptor, threshold);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(DEGRADED_STATUS);
        assertThat(health.getDetails()).containsEntry("activeConnections", connectionCount);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }

    @Property(tries = 200)
    @Tag("production-readiness")
    @Label("WebSocket health indicator reports UP when connectionCount <= threshold")
    void upWhenConnectionsAtOrBelowThreshold(
            @ForAll @IntRange(min = 0, max = 10000) int connectionCount,
            @ForAll @IntRange(min = 0, max = 10000) int headroom) {

        int threshold = connectionCount + headroom; // always >= connectionCount

        ConnectionLimitInterceptor mockInterceptor = mock(ConnectionLimitInterceptor.class);
        when(mockInterceptor.getActiveConnectionCount()).thenReturn(connectionCount);

        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(mockInterceptor, threshold);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeConnections", connectionCount);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }

    @Property(tries = 100)
    @Tag("production-readiness")
    @Label("WebSocket health indicator reports UP when connectionCount equals threshold exactly")
    void upWhenConnectionsEqualThreshold(
            @ForAll @IntRange(min = 0, max = 10000) int value) {

        int connectionCount = value;
        int threshold = value; // exactly equal

        ConnectionLimitInterceptor mockInterceptor = mock(ConnectionLimitInterceptor.class);
        when(mockInterceptor.getActiveConnectionCount()).thenReturn(connectionCount);

        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(mockInterceptor, threshold);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeConnections", connectionCount);
        assertThat(health.getDetails()).containsEntry("threshold", threshold);
    }
}
