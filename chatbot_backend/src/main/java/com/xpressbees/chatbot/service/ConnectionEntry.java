package com.xpressbees.chatbot.service;

import java.time.Instant;

/**
 * Represents an active WebSocket connection in the in-memory registry.
 *
 * @param stompSessionId       the STOMP protocol session ID
 * @param applicationSessionId the application-level chat session ID
 * @param connectedAt          timestamp when the connection was established
 * @param lastActivityAt       timestamp of the last application-level message activity
 */
public record ConnectionEntry(
    String stompSessionId,
    String applicationSessionId,
    Instant connectedAt,
    Instant lastActivityAt
) {}
