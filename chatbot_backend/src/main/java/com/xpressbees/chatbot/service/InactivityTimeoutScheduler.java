package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically scans for inactive WebSocket sessions and closes them.
 * Sessions that have exceeded the configured inactivity timeout receive a STOMP ERROR frame
 * before being unregistered, unless they have been inactive for significantly longer than
 * the timeout (2x), in which case the error frame is skipped.
 */
@Service
public class InactivityTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(InactivityTimeoutScheduler.class);

    private final ConnectionRegistry connectionRegistry;
    private final WebSocketResilienceProperties properties;
    private final SimpMessagingTemplate messagingTemplate;

    public InactivityTimeoutScheduler(ConnectionRegistry connectionRegistry,
                                      WebSocketResilienceProperties properties,
                                      SimpMessagingTemplate messagingTemplate) {
        this.connectionRegistry = connectionRegistry;
        this.properties = properties;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelay = 60000)
    public void evictIdleSessions() {
        Duration timeout = Duration.ofMinutes(properties.getInactivityTimeoutMinutes());
        List<String> inactiveSessions = connectionRegistry.getInactiveSessions(timeout);

        for (String stompSessionId : inactiveSessions) {
            ConnectionEntry entry = connectionRegistry.getEntry(stompSessionId);
            if (entry == null) {
                continue;
            }

            String applicationSessionId = entry.applicationSessionId();
            Duration inactiveDuration = Duration.between(entry.lastActivityAt(), Instant.now());
            Duration doubleTimeout = timeout.multipliedBy(2);

            if (inactiveDuration.compareTo(doubleTimeout) <= 0) {
                // Session inactive within reasonable range — send error frame before closing
                try {
                    if (applicationSessionId != null) {
                        messagingTemplate.convertAndSend(
                                "/topic/chat/" + applicationSessionId,
                                "Session timed out due to inactivity"
                        );
                    }
                } catch (Exception e) {
                    log.warn("Failed to send timeout error frame for session {}: {}",
                            stompSessionId, e.getMessage());
                }
            }
            // else: session has been inactive > 2x timeout — skip error frame

            log.info("Closing inactive session: stompSessionId={}, applicationSessionId={}, inactiveDuration={}",
                    stompSessionId, applicationSessionId, inactiveDuration);

            connectionRegistry.unregister(stompSessionId);
        }
    }
}
