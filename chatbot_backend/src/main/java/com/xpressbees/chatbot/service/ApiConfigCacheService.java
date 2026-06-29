package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class ApiConfigCacheService {

    private static final String KEY_PREFIX = "apiconfig:";
    private static final String NAME_KEY_PREFIX = "apiconfig:name:";

    private final ApiConfigRepository apiConfigRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;

    public ApiConfigCacheService(ApiConfigRepository apiConfigRepository,
                                 StringRedisTemplate redisTemplate,
                                 @Value("${cache.apiconfig.ttl-minutes:10}") long ttlMinutes) {
        this.apiConfigRepository = apiConfigRepository;
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Returns the full ApiConfig entity graph by ID.
     * Checks Redis first; on miss, loads from PostgreSQL and populates cache.
     * On Redis failure, falls back to PostgreSQL silently.
     */
    public Optional<ApiConfig> findById(Long apiConfigId) {
        String key = KEY_PREFIX + apiConfigId;

        // Try Redis first
        String json = getFromRedis(key);
        if (json != null) {
            try {
                ApiConfig config = objectMapper.readValue(json, ApiConfig.class);
                return Optional.of(config);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize ApiConfig from cache for key '{}', evicting corrupt entry", key, e);
                evictKey(key);
                // Fall through to reload from PostgreSQL
            }
        }

        // Cache miss or Redis unavailable — load from PostgreSQL
        Optional<ApiConfig> configOpt = apiConfigRepository.findById(apiConfigId);
        configOpt.ifPresent(config -> cacheApiConfig(config));
        return configOpt;
    }

    /**
     * Returns the full ApiConfig entity graph by name.
     * Uses the name-to-ID index to resolve, then delegates to findById.
     */
    public Optional<ApiConfig> findByName(String name) {
        String nameKey = NAME_KEY_PREFIX + name;

        // Try to resolve ID from name index in Redis
        String idStr = getFromRedis(nameKey);
        if (idStr != null) {
            try {
                Long id = Long.parseLong(idStr);
                return findById(id);
            } catch (NumberFormatException e) {
                log.error("Invalid ID value in name index for key '{}': {}", nameKey, idStr, e);
                evictKey(nameKey);
            }
        }

        // Name index miss — load from PostgreSQL
        Optional<ApiConfig> configOpt = apiConfigRepository.findByName(name);
        configOpt.ifPresent(config -> cacheApiConfig(config));
        return configOpt;
    }

    /**
     * Evicts both the ID-based cache entry and the name-to-ID index entry.
     */
    public void evict(Long apiConfigId, String name) {
        String idKey = KEY_PREFIX + apiConfigId;
        String nameKey = NAME_KEY_PREFIX + name;
        evictKey(idKey);
        evictKey(nameKey);
    }

    /**
     * Caches the ApiConfig entity and populates the name-to-ID index.
     */
    private void cacheApiConfig(ApiConfig config) {
        String idKey = KEY_PREFIX + config.getId();
        String nameKey = NAME_KEY_PREFIX + config.getName();

        try {
            String json = objectMapper.writeValueAsString(config);
            setInRedis(idKey, json);
            setInRedis(nameKey, String.valueOf(config.getId()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ApiConfig id={} for caching", config.getId(), e);
        }
    }

    /**
     * Safe Redis GET — returns null on any failure.
     */
    private String getFromRedis(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}', falling back to PostgreSQL", key, e);
            return null;
        }
    }

    /**
     * Safe Redis SET with TTL — logs and ignores failures.
     */
    private void setInRedis(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            log.warn("Redis SET failed for key '{}', entity returned without caching", key, e);
        }
    }

    /**
     * Safe Redis DEL — logs and ignores failures.
     */
    private void evictKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL failed for key '{}', stale data will expire via TTL", key, e);
        }
    }
}
