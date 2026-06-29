package com.xpressbees.chatbot.config;

import com.xpressbees.chatbot.service.ConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor that enforces the maximum concurrent WebSocket connection limit.
 * On STOMP CONNECT, registers the session in the ConnectionRegistry.
 * On STOMP DISCONNECT, unregisters the session from the ConnectionRegistry.
 */
@Component
public class ConnectionLimitInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLimitInterceptor.class);

    private final ConnectionRegistry connectionRegistry;

    public ConnectionLimitInterceptor(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Returns the current number of active WebSocket connections.
     *
     * @return the active connection count
     */
    public int getActiveConnectionCount() {
        return connectionRegistry.getActiveCount();
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String stompSessionId = accessor.getSessionId();
            boolean registered = connectionRegistry.register(stompSessionId, null);

            if (!registered) {
                log.warn("Connection rejected: maximum connections reached. STOMP session ID: {}", stompSessionId);
                throw new MessageDeliveryException("Maximum connections reached");
            }

            log.debug("Connection registered. STOMP session ID: {}", stompSessionId);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            String stompSessionId = accessor.getSessionId();
            connectionRegistry.unregister(stompSessionId);
            log.debug("Connection unregistered on DISCONNECT. STOMP session ID: {}", stompSessionId);
        }

        return message;
    }
}
