package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatResponse;

/**
 * Buffered message delivery interface that wraps ChatMessageSender
 * to provide per-session send buffering and back-pressure signaling.
 */
public interface BufferedMessageSender {

    /**
     * Buffers a message for delivery. If the buffer is full, marks the session
     * as paused (back-pressure) and starts drain timeout tracking.
     * Delegates to ChatMessageSender.sendResponse() for actual delivery.
     *
     * @param sessionId the application session ID
     * @param response  the ChatResponse to send
     * @return true if the message was sent successfully, false if the connection
     *         was closed due to drain timeout or delivery failure
     */
    boolean send(String sessionId, ChatResponse response);

    /**
     * Sends an error message immediately, bypassing the buffer.
     *
     * @param sessionId    the application session ID
     * @param errorMessage the error message text
     */
    void sendError(String sessionId, String errorMessage);

    /**
     * Called when a message is acknowledged/delivered to decrement buffer count.
     * If the session was paused and now has space, marks it as resumed.
     *
     * @param sessionId the application session ID
     */
    void acknowledge(String sessionId);

    /**
     * Cleans up buffer resources for a disconnected session.
     *
     * @param sessionId the application session ID
     */
    void cleanup(String sessionId);
}
