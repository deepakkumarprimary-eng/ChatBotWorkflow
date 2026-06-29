package com.xpressbees.chatbot.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit test verifying WebSocketConfig preserves interceptor ordering
 * (auth before connection limit) when configuring the inbound channel.
 *
 * <p>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5</p>
 */
class WebSocketConfigTest {

    @Test
    void configureClientInboundChannelRegistersInterceptorsInCorrectOrder() {
        WebSocketAuthInterceptor authInterceptor = mock(WebSocketAuthInterceptor.class);
        ConnectionLimitInterceptor connInterceptor = mock(ConnectionLimitInterceptor.class);
        WebSocketResilienceProperties props = new WebSocketResilienceProperties();

        WebSocketConfig config = new WebSocketConfig(
                new String[]{"http://localhost:3000"},
                authInterceptor,
                connInterceptor,
                props
        );

        ChannelRegistration registration = mock(ChannelRegistration.class);
        when(registration.interceptors(any(ChannelInterceptor[].class))).thenReturn(registration);

        config.configureClientInboundChannel(registration);

        ArgumentCaptor<ChannelInterceptor[]> captor = ArgumentCaptor.forClass(ChannelInterceptor[].class);
        verify(registration).interceptors(captor.capture());

        ChannelInterceptor[] interceptors = captor.getValue();
        assertThat(interceptors).hasSize(2);
        assertThat(interceptors[0]).isInstanceOf(WebSocketAuthInterceptor.class);
        assertThat(interceptors[1]).isInstanceOf(ConnectionLimitInterceptor.class);
    }
}
