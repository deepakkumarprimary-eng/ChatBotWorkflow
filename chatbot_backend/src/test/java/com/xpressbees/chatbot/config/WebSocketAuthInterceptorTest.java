package com.xpressbees.chatbot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WebSocketAuthInterceptor.
 * Validates: Requirements 5.1, 5.2, 5.6
 */
class WebSocketAuthInterceptorTest {

    private static final String VALID_API_KEY = "test-secret-api-key-12345";

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(VALID_API_KEY);
    }

    @Test
    @DisplayName("Valid API key allows STOMP CONNECT")
    void validApiKeyAllowsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-API-Key", VALID_API_KEY);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("Invalid API key rejects STOMP CONNECT with MessageDeliveryException")
    void invalidApiKeyRejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-API-Key", "wrong-api-key");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    @DisplayName("Missing API key rejects STOMP CONNECT with MessageDeliveryException")
    void missingApiKeyRejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // No X-API-Key header added
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("API key is missing");
    }

    @Test
    @DisplayName("Non-CONNECT frames pass through without authentication")
    void nonConnectFramesPassThrough() {
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Message<?> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(subscribeMessage, null);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(subscribeMessage);

        // Also test SEND command
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<?> sendMessage = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());

        Message<?> sendResult = interceptor.preSend(sendMessage, null);

        assertThat(sendResult).isNotNull();
        assertThat(sendResult).isSameAs(sendMessage);
    }

    @Test
    @DisplayName("Constructor rejects null API key")
    void constructorRejectsNullKey() {
        assertThatThrownBy(() -> new WebSocketAuthInterceptor(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBSOCKET_API_KEY");
    }

    @Test
    @DisplayName("Constructor rejects blank API key")
    void constructorRejectsBlankKey() {
        assertThatThrownBy(() -> new WebSocketAuthInterceptor("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBSOCKET_API_KEY");
    }

    @Test
    @DisplayName("Constant-time comparison prevents timing attacks - different length keys rejected")
    void constantTimeComparisonWithDifferentLengths() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-API-Key", "short");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    @DisplayName("Constant-time comparison prevents timing attacks - similar keys rejected")
    void constantTimeComparisonWithSimilarKeys() {
        // Key that differs only in last character
        String almostValidKey = VALID_API_KEY.substring(0, VALID_API_KEY.length() - 1) + "X";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-API-Key", almostValidKey);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    @DisplayName("API key via apiKey native header also works")
    void apiKeyViaQueryParameterHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("apiKey", VALID_API_KEY);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(message);
    }
}
