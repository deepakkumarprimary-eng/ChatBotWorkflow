package com.xpressbees.chatbot.integration;

import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import com.xpressbees.chatbot.entity.ApiPayload;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import com.xpressbees.chatbot.repository.ApiHeaderRepository;
import com.xpressbees.chatbot.repository.ApiPayloadRepository;
import com.xpressbees.chatbot.repository.ApiResponseMappingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying JPA cascade persist and cascade delete behavior
 * for ApiConfig and its child entities (ApiHeader, ApiPayload, ApiResponseMapping).
 *
 * Requirements: 1.5, 1.6
 */
class ApiConfigCascadeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @Autowired
    private ApiHeaderRepository apiHeaderRepository;

    @Autowired
    private ApiPayloadRepository apiPayloadRepository;

    @Autowired
    private ApiResponseMappingRepository apiResponseMappingRepository;

    @Test
    @Transactional
    void cascadePersist_savesAllChildEntities() {
        // Arrange: build an ApiConfig with headers, payload, and response mappings
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setName("cascade-persist-test");
        apiConfig.setUrl("https://api.example.com/data");
        apiConfig.setMethod("POST");
        apiConfig.setTimeoutMs(5000);
        apiConfig.setRetryCount(2);

        // Add headers
        ApiHeader header1 = new ApiHeader();
        header1.setHeaderName("Content-Type");
        header1.setHeaderValue("application/json");
        header1.setApiConfig(apiConfig);

        ApiHeader header2 = new ApiHeader();
        header2.setHeaderName("Authorization");
        header2.setHeaderValue("Bearer token123");
        header2.setApiConfig(apiConfig);

        apiConfig.setHeaders(List.of(header1, header2));

        // Add payload
        ApiPayload payload = new ApiPayload();
        payload.setPayloadTemplate(Map.of("key", "value", "nested", Map.of("inner", "data")));
        payload.setApiConfig(apiConfig);
        apiConfig.setPayload(payload);

        // Add response mappings
        ApiResponseMapping mapping1 = new ApiResponseMapping();
        mapping1.setResponsePath("$.result.id");
        mapping1.setContextVariableName("result_id");
        mapping1.setApiConfig(apiConfig);

        ApiResponseMapping mapping2 = new ApiResponseMapping();
        mapping2.setResponsePath("$.result.status");
        mapping2.setContextVariableName("result_status");
        mapping2.setApiConfig(apiConfig);

        apiConfig.setResponseMappings(List.of(mapping1, mapping2));

        // Act: save only the parent entity (cascade should persist children)
        ApiConfig saved = apiConfigRepository.saveAndFlush(apiConfig);

        // Assert: parent was persisted with an ID
        assertThat(saved.getId()).isNotNull();

        // Assert: headers were cascade-persisted
        List<ApiHeader> persistedHeaders = apiHeaderRepository.findAll().stream()
                .filter(h -> h.getApiConfig().getId().equals(saved.getId()))
                .toList();
        assertThat(persistedHeaders).hasSize(2);
        assertThat(persistedHeaders).extracting(ApiHeader::getHeaderName)
                .containsExactlyInAnyOrder("Content-Type", "Authorization");
        assertThat(persistedHeaders).extracting(ApiHeader::getHeaderValue)
                .containsExactlyInAnyOrder("application/json", "Bearer token123");

        // Assert: payload was cascade-persisted
        ApiPayload persistedPayload = apiPayloadRepository.findByApiConfig(saved).orElse(null);
        assertThat(persistedPayload).isNotNull();
        assertThat(persistedPayload.getPayloadTemplate()).containsEntry("key", "value");

        // Assert: response mappings were cascade-persisted
        List<ApiResponseMapping> persistedMappings = apiResponseMappingRepository.findAll().stream()
                .filter(m -> m.getApiConfig().getId().equals(saved.getId()))
                .toList();
        assertThat(persistedMappings).hasSize(2);
        assertThat(persistedMappings).extracting(ApiResponseMapping::getContextVariableName)
                .containsExactlyInAnyOrder("result_id", "result_status");
        assertThat(persistedMappings).extracting(ApiResponseMapping::getResponsePath)
                .containsExactlyInAnyOrder("$.result.id", "$.result.status");
    }

    @Test
    void cascadeDelete_removesAllChildEntities() {
        // Arrange: create and persist an ApiConfig with all children
        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setName("cascade-delete-test");
        apiConfig.setUrl("https://api.example.com/remove");
        apiConfig.setMethod("GET");
        apiConfig.setTimeoutMs(3000);
        apiConfig.setRetryCount(1);

        ApiHeader header = new ApiHeader();
        header.setHeaderName("X-Custom");
        header.setHeaderValue("custom-value");
        header.setApiConfig(apiConfig);
        apiConfig.setHeaders(List.of(header));

        ApiPayload payload = new ApiPayload();
        payload.setPayloadTemplate(Map.of("action", "delete"));
        payload.setApiConfig(apiConfig);
        apiConfig.setPayload(payload);

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.status");
        mapping.setContextVariableName("delete_status");
        mapping.setApiConfig(apiConfig);
        apiConfig.setResponseMappings(List.of(mapping));

        ApiConfig saved = apiConfigRepository.saveAndFlush(apiConfig);
        Long savedId = saved.getId();

        // Verify children exist before deletion
        assertThat(apiHeaderRepository.findAll().stream()
                .anyMatch(h -> h.getApiConfig().getId().equals(savedId))).isTrue();
        assertThat(apiPayloadRepository.findByApiConfig(saved)).isPresent();
        assertThat(apiResponseMappingRepository.findAll().stream()
                .anyMatch(m -> m.getApiConfig().getId().equals(savedId))).isTrue();

        // Act: delete the parent entity
        apiConfigRepository.delete(saved);
        apiConfigRepository.flush();

        // Assert: parent is removed
        assertThat(apiConfigRepository.findById(savedId)).isEmpty();

        // Assert: all child headers are removed
        assertThat(apiHeaderRepository.findAll().stream()
                .noneMatch(h -> h.getApiConfig().getId().equals(savedId))).isTrue();

        // Assert: payload is removed
        assertThat(apiPayloadRepository.findAll().stream()
                .noneMatch(p -> p.getApiConfig().getId().equals(savedId))).isTrue();

        // Assert: all response mappings are removed
        assertThat(apiResponseMappingRepository.findAll().stream()
                .noneMatch(m -> m.getApiConfig().getId().equals(savedId))).isTrue();
    }
}
