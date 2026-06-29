package com.xpressbees.chatbot.service;

import java.time.Duration;
import java.util.List;

/**
 * Registry for tracking active WebSocket connections and enforcing concurrency limits.
 */
public interface ConnectionRegistry {

    /**
     * Attempts to register a new connection.
     *
     * @param stompSessionId       the STOMP session ID for the new connection
     * @param applicationSessionId the application-level session ID (may be null if not yet known)
     * @return true if the connection was registered successfully, false if the maximum limit is reached
     */
    boolean register(String stompSessionId, String applicationSessionId);

    /**
     * Removes a connection from the registry.
     *
     * @param stompSessionId the STOMP session ID to unregister
     */
    void unregister(String stompSessionId);

    /**
     * Returns the current number of active connections.
     *
     * @return the active connection count
     */
    int getActiveCount();

    /**
     * Returns the application session ID mapped to the given STOMP session.
     *
     * @param stompSessionId the STOMP session ID to look up
     * @return the application session ID, or null if not found
     */
    String getApplicationSessionId(String stompSessionId);

    /**
     * Returns the STOMP session ID mapped to the given application session.
     *
     * @param applicationSessionId the application session ID to look up
     * @return the STOMP session ID, or null if not found
     */
    String getStompSessionId(String applicationSessionId);

    /**
     * Records an activity timestamp for the given STOMP session, resetting the inactivity timer.
     *
     * @param stompSessionId the STOMP session ID that had activity
     */
    void recordActivity(String stompSessionId);

    /**
     * Returns all STOMP session IDs that have exceeded the given inactivity timeout.
     *
     * @param timeout the maximum allowed inactivity duration
     * @return a list of STOMP session IDs that have been inactive longer than the timeout
     */
    List<String> getInactiveSessions(Duration timeout);

    /**
     * Returns the ConnectionEntry for the given STOMP session ID, or null if not found.
     *
     * @param stompSessionId the STOMP session ID to look up
     * @return the ConnectionEntry, or null if not registered
     */
    ConnectionEntry getEntry(String stompSessionId);

    /**
     * Associates an application session ID with an existing STOMP session entry.
     * This updates an entry that was registered with a null applicationSessionId on CONNECT.
     *
     * @param stompSessionId        the STOMP session ID of the existing entry
     * @param applicationSessionId  the application session ID to associate
     */
    void associateApplicationSession(String stompSessionId, String applicationSessionId);
}
