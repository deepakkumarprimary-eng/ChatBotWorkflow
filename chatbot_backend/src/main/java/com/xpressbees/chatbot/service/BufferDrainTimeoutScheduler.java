package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Scheduled task that enforces the buffer drain timeout.
 * Periodically checks all paused session buffers and closes connections
 * for sessions that have been paused longer than the configured drain timeout.
 */
@Service
public class BufferDrainTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(BufferDrainTimeoutScheduler.class);

    private final BufferedMessageSenderImpl bufferedMessageSender;
    private final ConnectionRegistry connectionRegistry;
    private final WebSocketResilienceProperties resilienceProperties;

    public BufferDrainTimeoutScheduler(BufferedMessageSenderImpl bufferedMessageSender,
                                       ConnectionRegistry connectionRegistry,
                                       WebSocketResilienceProperties resilienceProperties) {
        this.bufferedMessageSender = bufferedMessageSender;
        this.connectionRegistry = connectionRegistry;
        this.resilienceProperties = resilienceProperties;
    }

    /**
     * Runs every 5 seconds to check for paused sessions that have exceeded
     * the configured buffer drain timeout.
     */
    @Scheduled(fixedDelay = 5000)
    public void enforceBufferDrainTimeout() {
        int drainTimeoutSeconds = resilienceProperties.getBufferDrainTimeoutSeconds();
        Instant now = Instant.now();

        for (Map.Entry<String, SessionSendBuffer> entry : bufferedMessageSender.getBuffers().entrySet()) {
            String sessionId = entry.getKey();
            SessionSendBuffer buffer = entry.getValue();

            if (!buffer.isPaused()) {
                continue;
            }

            Instant pausedSince = buffer.getPausedSince();
            if (pausedSince == null) {
                continue;
            }

            Duration pausedDuration = Duration.between(pausedSince, now);
            if (pausedDuration.getSeconds() >= drainTimeoutSeconds) {
                log.warn("Buffer drain timeout exceeded for session {}: paused for {} seconds (timeout: {} seconds). Closing connection.",
                        sessionId, pausedDuration.getSeconds(), drainTimeoutSeconds);

                // Look up the STOMP session ID from the application session ID
                String stompSessionId = connectionRegistry.getStompSessionId(sessionId);
                if (stompSessionId != null) {
                    connectionRegistry.unregister(stompSessionId);
                }

                // Clean up the buffer
                bufferedMessageSender.cleanup(sessionId);
            }
        }
    }
}
