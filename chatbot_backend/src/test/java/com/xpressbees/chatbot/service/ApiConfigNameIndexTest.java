package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import net.jqwik.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Feature: redis-caching-and-performance, Property 4: ApiConfig name-to-ID index consistency

/**
 * Property-based test for ApiConfig name-to-ID index consistency.
 *
 * Validates: Requirements 2.6
 *
 * For any ApiConfig with a unique name, after the name-to-ID index is populated,
 * looking up by name SHALL return the same ApiConfig entity as looking up directly by ID.
 */
class ApiConfigNameIndexTest {

    private Map<String, String> redisStore;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ApiConfigRepository apiConfigRepository;
    private ApiConfigCacheService cacheService;
    private ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    private void setupMocks() {
        redisStore = new ConcurrentHashMap<>();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        apiConfigRepository = mock(ApiConfigRepository.class);
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
        cacheService = new ApiConfigCacheService(apiConfigRepository, redisTemplate, 10L);
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 4: ApiConfig name-to-ID index consistency")
    void lookupByNameReturnsSameEntityAsLookupById(
            @ForAll("positiveIds") Long id,
            @ForAll("alphanumericNames") String name) throws JsonProcessingException {
        // **Validates: Requirements 2.6**

        setupMocks();

        // Arrange: create an ApiConfig with the given id and name
        ApiConfig apiConfig = createApiConfig(id, name);

        // Mock repository to return this entity for both findByName and findById
        when(apiConfigRepository.findByName(name)).thenReturn(Optional.of(apiConfig));
        when(apiConfigRepository.findById(id)).thenReturn(Optional.of(apiConfig));

        // Step 1: Call findByName — this loads from repo and populates both caches
        Optional<ApiConfig> byName = cacheService.findByName(name);
        assert byName.isPresent() : "findByName should return the ApiConfig";

        // Verify the name-to-ID index was populated
        String nameKey = "apiconfig:name:" + name;
        assert redisStore.containsKey(nameKey) :
                "Name-to-ID index should be populated after findByName. name=" + name;

        // Verify the ID-based cache was populated
        String idKey = "apiconfig:" + id;
        assert redisStore.containsKey(idKey) :
                "ID-based cache should be populated after findByName. id=" + id;

        // Step 2: Call findById — this should return from cache
        Optional<ApiConfig> byId = cacheService.findById(id);
        assert byId.isPresent() : "findById should return the ApiConfig";

        // Step 3: Assert both return the same entity (matching fields)
        ApiConfig resultByName = byName.get();
        ApiConfig resultById = byId.get();

        assert Objects.equals(resultByName.getId(), resultById.getId()) :
                "IDs should match. byName.id=" + resultByName.getId() + ", byId.id=" + resultById.getId();
        assert Objects.equals(resultByName.getName(), resultById.getName()) :
                "Names should match. byName.name=" + resultByName.getName() + ", byId.name=" + resultById.getName();
        assert Objects.equals(resultByName.getUrl(), resultById.getUrl()) :
                "URLs should match";
        assert Objects.equals(resultByName.getMethod(), resultById.getMethod()) :
                "Methods should match";
        assert Objects.equals(resultByName.getTimeoutMs(), resultById.getTimeoutMs()) :
                "Timeout values should match";
        assert Objects.equals(resultByName.getRetryCount(), resultById.getRetryCount()) :
                "Retry counts should match";
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 4: ApiConfig name-to-ID index consistency")
    void nameIndexResolvedIdDelegatesToIdCache(
            @ForAll("positiveIds") Long id,
            @ForAll("alphanumericNames") String name) throws JsonProcessingException {
        // **Validates: Requirements 2.6**
        // After the name-to-ID index is populated, a subsequent findByName call should
        // use the index to resolve the ID and then delegate to the ID-based cache,
        // without querying the repository again for by-name lookup.

        setupMocks();

        // Arrange: create an ApiConfig
        ApiConfig apiConfig = createApiConfig(id, name);
        when(apiConfigRepository.findByName(name)).thenReturn(Optional.of(apiConfig));
        when(apiConfigRepository.findById(id)).thenReturn(Optional.of(apiConfig));

        // Step 1: Populate both caches via findByName
        cacheService.findByName(name);

        // Clear invocations so we can verify no additional repo calls
        clearInvocations(apiConfigRepository);

        // Step 2: Call findByName again — should use name index → findById cache path
        Optional<ApiConfig> secondByName = cacheService.findByName(name);
        assert secondByName.isPresent() : "Second findByName should return the ApiConfig from cache";

        // Verify the repository was NOT called again (everything served from cache)
        verify(apiConfigRepository, never()).findByName(anyString());
        verify(apiConfigRepository, never()).findById(any());

        // Step 3: Call findById — should also be served from cache
        Optional<ApiConfig> byId = cacheService.findById(id);
        assert byId.isPresent() : "findById should return the ApiConfig from cache";

        // Verify consistency: both return same data
        assert Objects.equals(secondByName.get().getId(), byId.get().getId()) :
                "Cached results should have same ID";
        assert Objects.equals(secondByName.get().getName(), byId.get().getName()) :
                "Cached results should have same name";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<Long> positiveIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    @Provide
    Arbitrary<String> alphanumericNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ApiConfig createApiConfig(Long id, String name) {
        ApiConfig config = new ApiConfig();
        config.setId(id);
        config.setName(name);
        config.setUrl("https://api.example.com/" + name);
        config.setMethod("GET");
        config.setTimeoutMs(5000);
        config.setRetryCount(1);
        config.setHeaders(new ArrayList<>());
        config.setPayload(null);
        config.setResponseMappings(new ArrayList<>());
        config.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        config.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        return config;
    }
}
