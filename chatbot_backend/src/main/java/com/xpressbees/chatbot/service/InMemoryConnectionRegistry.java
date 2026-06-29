package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.config.WebSocketResilienceProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ConnectionRegistry} using ConcurrentHashMap
 * for O(1) lookup by STOMP session ID and application session ID.
 */
@Service
public class InMemoryConnectionRegistry implements ConnectionRegistry {

    private final ConcurrentHashMap<String, ConnectionEntry> byStompSessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> byApplicationSessionId = new ConcurrentHashMap<>();
    private final WebSocketResilienceProperties properties;

    public InMemoryConnectionRegistry(WebSocketResilienceProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean register(String stompSessionId, String applicationSessionId) {
        if (getActiveCount() >= properties.getMaxConnections()) {
            return false;
        }

        Instant now = Instant.now();
        ConnectionEntry entry = new ConnectionEntry(stompSessionId, applicationSessionId, now, now);
        byStompSessionId.put(stompSessionId, entry);

        if (applicationSessionId != null) {
            byApplicationSessionId.put(applicationSessionId, stompSessionId);
        }

        return true;
    }

    @Override
    public void unregister(String stompSessionId) {
        ConnectionEntry removed = byStompSessionId.remove(stompSessionId);
        if (removed != null && removed.applicationSessionId() != null) {
            byApplicationSessionId.remove(removed.applicationSessionId());
        }
    }

    @Override
    public int getActiveCount() {
        return byStompSessionId.size();
    }

    @Override
    public String getApplicationSessionId(String stompSessionId) {
        ConnectionEntry entry = byStompSessionId.get(stompSessionId);
        return entry != null ? entry.applicationSessionId() : null;
    }

    @Override
    public String getStompSessionId(String applicationSessionId) {
        return byApplicationSessionId.get(applicationSessionId);
    }

    @Override
    public void recordActivity(String stompSessionId) {
        byStompSessionId.computeIfPresent(stompSessionId, (key, existing) ->
            new ConnectionEntry(
                existing.stompSessionId(),
                existing.applicationSessionId(),
                existing.connectedAt(),
                Instant.now()
            )
        );
    }

    @Override
    public List<String> getInactiveSessions(Duration timeout) {
        Instant cutoff = Instant.now().minus(timeout);
        return byStompSessionId.values().stream()
            .filter(entry -> entry.lastActivityAt().isBefore(cutoff))
            .map(ConnectionEntry::stompSessionId)
            .toList();
    }

    @Override
    public ConnectionEntry getEntry(String stompSessionId) {
        return byStompSessionId.get(stompSessionId);
    }

    @Override
    public void associateApplicationSession(String stompSessionId, String applicationSessionId) {
        byStompSessionId.computeIfPresent(stompSessionId, (key, existing) -> {
            // Remove old reverse mapping if one existed
            if (existing.applicationSessionId() != null) {
                byApplicationSessionId.remove(existing.applicationSessionId());
            }
            // Add new reverse mapping
            if (applicationSessionId != null) {
                byApplicationSessionId.put(applicationSessionId, stompSessionId);
            }
            return new ConnectionEntry(
                existing.stompSessionId(),
                applicationSessionId,
                existing.connectedAt(),
                existing.lastActivityAt()
            );
        });
    }
}
