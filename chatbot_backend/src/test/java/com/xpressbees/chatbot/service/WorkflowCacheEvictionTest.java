package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Feature: redis-caching-and-performance, Property 2: Workflow cache eviction on mutation

/**
 * Property-based test for Workflow cache eviction on mutation.
 *
 * Validates: Requirements 1.4, 1.5
 *
 * For any workflow ID that is present in the cache, performing an update or delete
 * operation on that workflow SHALL result in the cache no longer containing an entry
 * for that ID (subsequent lookup returns a cache miss).
 */
class WorkflowCacheEvictionTest {

    /**
     * Simulates Redis as an in-memory HashMap so we can verify eviction behavior
     * without needing a real Redis instance. The mock StringRedisTemplate delegates
     * GET/SET/DELETE operations to this store.
     */
    private Map<String, String> redisStore;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private WorkflowRepository workflowRepository;
    private WorkflowCacheService cacheService;
    private ObjectMapper objectMapper;

    private void setupMocks() {
        redisStore = new ConcurrentHashMap<>();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        workflowRepository = mock(WorkflowRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Wire the mock template to return our mock ValueOperations
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Simulate GET: return value from in-memory store
        when(valueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisStore.get(key);
        });

        // Simulate SET: store value in in-memory store
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisStore.put(key, value);
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        // Simulate DELETE: remove from in-memory store
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisStore.remove(key) != null;
        });

        // Create the service under test with 10 min TTL
        cacheService = new WorkflowCacheService(workflowRepository, redisTemplate, 10L);
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 2: Workflow cache eviction on mutation")
    void evictingCachedWorkflowResultsInCacheMiss(
            @ForAll("positiveWorkflowIds") Long workflowId) throws JsonProcessingException {
        // **Validates: Requirements 1.4, 1.5**

        setupMocks();

        // Arrange: create a workflow entity that the repository will return
        Workflow workflow = createWorkflow(workflowId);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        // Step 1: Populate the cache by calling findById (cache miss → loads from repo → stores in Redis)
        Optional<Workflow> firstResult = cacheService.findById(workflowId);
        assert firstResult.isPresent() : "findById should return the workflow";

        // Verify the entry is now in our simulated Redis store
        String expectedKey = "workflow:" + workflowId;
        assert redisStore.containsKey(expectedKey) :
                "Cache should contain the workflow entry after first findById";

        // Step 2: Evict the workflow (simulates what happens on update/delete)
        cacheService.evict(workflowId);

        // Step 3: Verify the entry is no longer in the cache
        assert !redisStore.containsKey(expectedKey) :
                "Cache should NOT contain the workflow entry after eviction. " +
                        "workflowId=" + workflowId;

        // Step 4: Verify that a subsequent findById goes back to PostgreSQL (cache miss)
        // Reset the repository mock invocation count
        Mockito.clearInvocations(workflowRepository);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        Optional<Workflow> secondResult = cacheService.findById(workflowId);
        assert secondResult.isPresent() : "findById should still return the workflow from PostgreSQL";

        // Verify it hit the repository again (proving cache miss)
        verify(workflowRepository, times(1)).findById(workflowId);
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 2: Workflow cache eviction on mutation")
    void evictOnNonCachedIdIsNoOp(
            @ForAll("positiveWorkflowIds") Long workflowId) {
        // **Validates: Requirements 1.4, 1.5**
        // Evicting a workflow that was never cached should not cause errors

        setupMocks();

        String expectedKey = "workflow:" + workflowId;

        // Verify nothing is in the store initially
        assert !redisStore.containsKey(expectedKey) :
                "Store should be empty initially";

        // Evict a workflow that was never cached — should not throw
        cacheService.evict(workflowId);

        // Store should still be empty
        assert !redisStore.containsKey(expectedKey) :
                "Store should still be empty after evicting a non-existent entry";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<Long> positiveWorkflowIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Workflow createWorkflow(Long id) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setName("Test Workflow " + id);
        w.setWorkflowJson(Map.of(
                "nodes", List.of(Map.of("id", "n1", "type", "message", "label", "Start")),
                "transitions", List.of()
        ));
        w.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        w.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        return w;
    }
}
