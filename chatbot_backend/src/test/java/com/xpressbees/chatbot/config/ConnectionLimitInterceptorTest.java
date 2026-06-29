package com.xpressbees.chatbot.config;

import com.xpressbees.chatbot.service.ConnectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConnectionLimitInterceptor.
 * Validates: Requirements 3.2, 3.3, 3.4
 */
@ExtendWith(MockitoExtension.class)
class ConnectionLimitInterceptorTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @InjectMocks
    private ConnectionLimitInterceptor interceptor;

    @Test
    @DisplayName("CONNECT is accepted when below connection limit")
    void connectAcceptedWhenBelowLimit() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("test-session");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(connectionRegistry.register("test-session", null)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(message);
        verify(connectionRegistry).register("test-session", null);
    }

    @Test
    @DisplayName("CONNECT is rejected with correct error message when at limit")
    void connectRejectedWhenAtLimit() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("test-session");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(connectionRegistry.register("test-session", null)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Maximum connections reached");
    }

    @Test
    @DisplayName("DISCONNECT correctly unregisters the session")
    void disconnectUnregistersSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("test-session");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(message);
        verify(connectionRegistry).unregister("test-session");
    }
}
