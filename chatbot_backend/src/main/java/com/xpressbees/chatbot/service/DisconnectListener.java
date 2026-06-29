package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Optional;

/**
 * Listens for STOMP SessionDisconnectEvent and updates the associated
 * ChatSession status to "disconnected" when the session was active.
 */
@Service
public class DisconnectListener {

    private static final Logger log = LoggerFactory.getLogger(DisconnectListener.class);

    private final ConnectionRegistry connectionRegistry;
    private final ChatSessionRepository chatSessionRepository;
    private final BufferedMessageSender bufferedMessageSender;

    public DisconnectListener(ConnectionRegistry connectionRegistry,
                              ChatSessionRepository chatSessionRepository,
                              BufferedMessageSender bufferedMessageSender) {
        this.connectionRegistry = connectionRegistry;
        this.chatSessionRepository = chatSessionRepository;
        this.bufferedMessageSender = bufferedMessageSender;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String stompSessionId = event.getSessionId();
        Instant timestamp = Instant.now();

        String applicationSessionId = connectionRegistry.getApplicationSessionId(stompSessionId);

        if (applicationSessionId == null) {
            log.warn("Disconnect event received for STOMP session [{}] but no application session found in registry. Skipping status update.", stompSessionId);
            connectionRegistry.unregister(stompSessionId);
            return;
        }

        log.info("Disconnect detected: applicationSessionId=[{}], stompSessionId=[{}], timestamp=[{}]",
                applicationSessionId, stompSessionId, timestamp);

        try {
            Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(applicationSessionId);
            if (sessionOpt.isPresent()) {
                ChatSession chatSession = sessionOpt.get();
                if ("active".equals(chatSession.getStatus())) {
                    chatSession.setStatus("disconnected");
                    chatSessionRepository.save(chatSession);
                    log.info("ChatSession [{}] status updated to 'disconnected'", applicationSessionId);
                }
            }
        } catch (Exception e) {
            log.error("Database error while updating ChatSession status for applicationSessionId=[{}], stompSessionId=[{}]: {}",
                    applicationSessionId, stompSessionId, e.getMessage(), e);
        }

        connectionRegistry.unregister(stompSessionId);
        bufferedMessageSender.cleanup(applicationSessionId);
    }
}
