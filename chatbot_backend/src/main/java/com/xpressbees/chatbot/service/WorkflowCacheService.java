package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class WorkflowCacheService {

    private static final String KEY_PREFIX = "workflow:";

    private final WorkflowRepository workflowRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;

    public WorkflowCacheService(WorkflowRepository workflowRepository,
                                StringRedisTemplate redisTemplate,
                                @Value("${cache.workflow.ttl-minutes:10}") long ttlMinutes) {
        this.workflowRepository = workflowRepository;
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = ttlMinutes;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Returns the Workflow for the given ID.
     * Checks Redis first; on miss, loads from PostgreSQL and populates cache.
     * On Redis failure, falls back to PostgreSQL silently.
     */
    public Optional<Workflow> findById(Long workflowId) {
        String key = KEY_PREFIX + workflowId;

        // Try Redis first
        String json = getFromRedis(key);

        if (json != null) {
            // Cache hit — deserialize
            try {
                Workflow workflow = objectMapper.readValue(json, Workflow.class);
                return Optional.of(workflow);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached workflow for key '{}'. Evicting corrupt entry and reloading from PostgreSQL.", key, e);
                evictFromRedis(key);
                // Fall through to PostgreSQL load
            }
        }

        // Cache miss or deserialization failure — load from PostgreSQL
        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);

        // Populate cache on successful load
        workflowOpt.ifPresent(workflow -> storeInRedis(key, workflow));

        return workflowOpt;
    }

    /**
     * Evicts the cached workflow entry for the given ID.
     * Called on update/delete operations.
     */
    public void evict(Long workflowId) {
        String key = KEY_PREFIX + workflowId;
        evictFromRedis(key);
    }

    private String getFromRedis(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key '{}'. Falling back to PostgreSQL.", key, e);
            return null;
        }
    }

    private void storeInRedis(String key, Workflow workflow) {
        try {
            String json = objectMapper.writeValueAsString(workflow);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize workflow for key '{}'. Skipping cache store.", key, e);
        } catch (Exception e) {
            log.warn("Redis write failed for key '{}'. Returning entity without caching.", key, e);
        }
    }

    private void evictFromRedis(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis eviction failed for key '{}'. Entry will expire via TTL.", key, e);
        }
    }
}
