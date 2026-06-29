package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import com.xpressbees.chatbot.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of BufferedMessageSender that maintains per-session send buffers
 * and provides back-pressure signaling when buffers reach capacity.
 * <p>
 * Uses the decorator pattern: wraps ChatMessageSender to add buffering without
 * modifying the existing contract.
 */
@Service
public class BufferedMessageSenderImpl implements BufferedMessageSender {

    private static final Logger log = LoggerFactory.getLogger(BufferedMessageSenderImpl.class);

    private final ChatMessageSender chatMessageSender;
    private final WebSocketResilienceProperties resilienceProperties;
    private final ConcurrentHashMap<String, SessionSendBuffer> buffers = new ConcurrentHashMap<>();

    public BufferedMessageSenderImpl(ChatMessageSender chatMessageSender,
                                     WebSocketResilienceProperties resilienceProperties) {
        this.chatMessageSender = chatMessageSender;
        this.resilienceProperties = resilienceProperties;
    }

    @Override
    public boolean send(String sessionId, ChatResponse response) {
        SessionSendBuffer buffer = buffers.computeIfAbsent(sessionId,
                id -> new SessionSendBuffer(resilienceProperties.getSendBufferSize()));

        boolean offered = buffer.offer(response);

        if (!offered) {
            // Buffer is full — mark paused to signal back-pressure
            if (!buffer.isPaused()) {
                buffer.markPaused();
                log.info("Send buffer full for session {}, pausing workflow processing", sessionId);
            }
            return false;
        }

        // If buffer became full after this offer, mark paused
        if (buffer.isFull() && !buffer.isPaused()) {
            buffer.markPaused();
            log.info("Send buffer reached capacity for session {}, pausing workflow processing", sessionId);
        }

        // Delegate to ChatMessageSender for actual delivery
        try {
            chatMessageSender.sendResponse(sessionId, response);
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver message for session {}: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void sendError(String sessionId, String errorMessage) {
        // Bypass buffer — deliver immediately
        chatMessageSender.sendError(sessionId, errorMessage);
    }

    @Override
    public void acknowledge(String sessionId) {
        SessionSendBuffer buffer = buffers.get(sessionId);
        if (buffer == null) {
            return;
        }

        buffer.poll();

        // If was paused and now has space (not full), mark resumed
        if (buffer.isPaused() && !buffer.isFull()) {
            buffer.markResumed();
            log.info("Send buffer drained for session {}, resuming workflow processing", sessionId);
        }
    }

    @Override
    public void cleanup(String sessionId) {
        SessionSendBuffer removed = buffers.remove(sessionId);
        if (removed != null) {
            log.debug("Cleaned up send buffer for session {}", sessionId);
        }
    }

    /**
     * Returns the buffer map for internal use (e.g., drain timeout enforcement).
     * Package-private for access by scheduled tasks.
     */
    ConcurrentHashMap<String, SessionSendBuffer> getBuffers() {
        return buffers;
    }
}
