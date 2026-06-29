package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatReconnectRequest;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Controller
public class ReconnectionController {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionController.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ConnectionRegistry connectionRegistry;
    private final BufferedMessageSender bufferedMessageSender;
    private final SimpMessagingTemplate messagingTemplate;

    public ReconnectionController(ChatSessionRepository chatSessionRepository,
                                  ConnectionRegistry connectionRegistry,
                                  BufferedMessageSender bufferedMessageSender,
                                  SimpMessagingTemplate messagingTemplate) {
        this.chatSessionRepository = chatSessionRepository;
        this.connectionRegistry = connectionRegistry;
        this.bufferedMessageSender = bufferedMessageSender;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.reconnect")
    @Transactional
    public void handleReconnect(@Valid ChatReconnectRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = request.getSessionId();
        String stompSessionId = headerAccessor.getSessionId();

        log.info("Reconnection request received for session {} from STOMP session {}", sessionId, stompSessionId);

        Optional<ChatSession> optionalSession = chatSessionRepository.findBySessionId(sessionId);

        if (optionalSession.isEmpty()) {
            log.warn("Reconnection failed: session {} not found", sessionId);
            bufferedMessageSender.sendError(sessionId, "Session not found");
            return;
        }

        ChatSession chatSession = optionalSession.get();
        String status = chatSession.getStatus();

        if ("completed".equals(status)) {
            log.warn("Reconnection failed: session {} has already completed", sessionId);
            bufferedMessageSender.sendError(sessionId, "Session has already completed");
            return;
        }

        if ("active".equals(status)) {
            log.warn("Reconnection failed: session {} is already active on another connection", sessionId);
            bufferedMessageSender.sendError(sessionId, "Session is already active on another connection");
            return;
        }

        if ("disconnected".equals(status)) {
            // Transition status to "active"
            chatSession.setStatus("active");
            chatSessionRepository.save(chatSession);

            // Register new connection
            connectionRegistry.associateApplicationSession(stompSessionId, sessionId);

            // Re-send lastPromptPayload
            Map<String, Object> lastPromptPayload = chatSession.getLastPromptPayload();
            if (lastPromptPayload != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> node = (Map<String, Object>) lastPromptPayload.get("node");
                    ChatResponse chatResponse = new ChatResponse(
                            node,
                            (String) lastPromptPayload.get("response"),
                            sessionId,
                            (Boolean) lastPromptPayload.get("completed")
                    );
                    boolean sent = bufferedMessageSender.send(sessionId, chatResponse);
                    if (!sent) {
                        // Roll back status to "disconnected"
                        chatSession.setStatus("disconnected");
                        chatSessionRepository.save(chatSession);
                        log.error("Reconnection failed: unable to re-send prompt for session {}", sessionId);
                        bufferedMessageSender.sendError(sessionId, "Reconnection failed");
                        return;
                    }
                } catch (Exception e) {
                    // Roll back status to "disconnected"
                    chatSession.setStatus("disconnected");
                    chatSessionRepository.save(chatSession);
                    log.error("Reconnection failed for session {}: {}", sessionId, e.getMessage(), e);
                    bufferedMessageSender.sendError(sessionId, "Reconnection failed");
                    return;
                }
            }

            log.info("Session {} successfully reconnected via STOMP session {}", sessionId, stompSessionId);
        }
    }
}
