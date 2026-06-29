package com.xpressbees.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final String validApiKey;

    public WebSocketAuthInterceptor(@Value("${websocket.api-key}") String validApiKey) {
        if (validApiKey == null || validApiKey.isBlank()) {
            throw new IllegalStateException(
                    "WEBSOCKET_API_KEY environment variable must be set. " +
                    "Configure 'websocket.api-key' property with a non-blank value.");
        }
        this.validApiKey = validApiKey;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String apiKey = extractApiKey(accessor);

            if (apiKey == null || apiKey.isBlank()) {
                throw new MessageDeliveryException(
                        "WebSocket authentication failed: API key is missing. " +
                        "Provide it via 'X-API-Key' header or 'apiKey' query parameter.");
            }

            if (!constantTimeEquals(validApiKey, apiKey)) {
                throw new MessageDeliveryException(
                        "WebSocket authentication failed: Invalid API key.");
            }
        }

        return message;
    }

    private String extractApiKey(StompHeaderAccessor accessor) {
        // Try X-API-Key native header first
        String apiKey = accessor.getFirstNativeHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }

        // Fall back to apiKey query parameter (passed as native header by some clients)
        apiKey = accessor.getFirstNativeHeader("apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }

        return null;
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(),
                provided.getBytes()
        );
    }
}
