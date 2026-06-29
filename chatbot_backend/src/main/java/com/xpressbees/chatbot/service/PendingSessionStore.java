package com.xpressbees.chatbot.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed store for pending chat session IDs.
 * Replaces the in-memory ConcurrentHashMap with Redis, enabling multi-instance
 * deployments and automatic expiration via TTL.
 */
@Slf4j
@Service
public class PendingSessionStore {

    private static final String KEY_PREFIX = "pending-session:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final long ttlMinutes;

    public PendingSessionStore(StringRedisTemplate redisTemplate,
                               @Value("${session.pending.ttl-minutes:5}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Registers a session ID in Redis with TTL.
     * Returns true if stored successfully, false if Redis is unavailable.
     */
    public boolean register(String sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, VALUE, Duration.ofMinutes(ttlMinutes));
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during session register for sessionId={}: {}", sessionId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to register pending session in Redis for sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Atomically consumes (removes) a session ID.
     * Returns true if the session existed and was removed, false otherwise.
     * Uses Redis DEL which returns true if the key existed — this is atomic.
     */
    public boolean consume(String sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            Boolean deleted = redisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during session consume for sessionId={}: {}", sessionId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to consume pending session from Redis for sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }
}
