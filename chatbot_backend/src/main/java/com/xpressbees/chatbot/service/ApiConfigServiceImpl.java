package com.xpressbees.chatbot.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xpressbees.chatbot.dto.ApiConfigRequest;
import com.xpressbees.chatbot.dto.ApiConfigResponse;
import com.xpressbees.chatbot.dto.ApiHeaderDto;
import com.xpressbees.chatbot.dto.ApiResponseMappingDto;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import com.xpressbees.chatbot.entity.ApiPayload;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import com.xpressbees.chatbot.exception.ApiConfigNotFoundException;
import com.xpressbees.chatbot.exception.DuplicateApiConfigNameException;
import com.xpressbees.chatbot.exception.InvalidMethodException;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import com.xpressbees.chatbot.repository.ApiHeaderRepository;
import com.xpressbees.chatbot.repository.ApiPayloadRepository;
import com.xpressbees.chatbot.repository.ApiResponseMappingRepository;

@Service
@Transactional
public class ApiConfigServiceImpl implements ApiConfigService {

    private static final Set<String> VALID_METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final int MAX_HEADERS = 50;
    private static final int MAX_RESPONSE_MAPPINGS = 50;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RETRY_COUNT = 1;
    private static final Pattern CONTEXT_VARIABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final ApiConfigRepository apiConfigRepository;
    private final ApiHeaderRepository apiHeaderRepository;
    private final ApiPayloadRepository apiPayloadRepository;
    private final ApiResponseMappingRepository apiResponseMappingRepository;

    public ApiConfigServiceImpl(ApiConfigRepository apiConfigRepository,
                                ApiHeaderRepository apiHeaderRepository,
                                ApiPayloadRepository apiPayloadRepository,
                                ApiResponseMappingRepository apiResponseMappingRepository) {
        this.apiConfigRepository = apiConfigRepository;
        this.apiHeaderRepository = apiHeaderRepository;
        this.apiPayloadRepository = apiPayloadRepository;
        this.apiResponseMappingRepository = apiResponseMappingRepository;
    }

    @Override
    public ApiConfigResponse create(ApiConfigRequest request) {
        validateRequiredFields(request);
        String normalizedMethod = normalizeAndValidateMethod(request.getMethod());
        validateNumericRanges(request);
        validateCollectionSizes(request);

        if (request.getResponseMappings() != null && !request.getResponseMappings().isEmpty()) {
            validateResponseMappings(request.getResponseMappings());
        }

        if (apiConfigRepository.existsByName(request.getName())) {
            throw new DuplicateApiConfigNameException(request.getName());
        }

        ApiConfig config = new ApiConfig();
        config.setName(request.getName());
        config.setUrl(request.getUrl());
        config.setMethod(normalizedMethod);
        config.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS);
        config.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : DEFAULT_RETRY_COUNT);
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setClientId(request.getClientId());

        ApiConfig savedConfig = apiConfigRepository.save(config);

        // Persist headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            List<ApiHeader> headers = request.getHeaders().stream()
                    .map(dto -> {
                        ApiHeader header = new ApiHeader();
                        header.setApiConfig(savedConfig);
                        header.setHeaderName(dto.getHeaderName());
                        header.setHeaderValue(dto.getHeaderValue());
                        return header;
                    })
                    .collect(Collectors.toList());
            apiHeaderRepository.saveAll(headers);
            savedConfig.setHeaders(headers);
        }

        // Persist payload
        if (request.getPayloadTemplate() != null) {
            ApiPayload payload = new ApiPayload();
            payload.setApiConfig(savedConfig);
            payload.setPayloadTemplate(castToMap(request.getPayloadTemplate()));
            ApiPayload savedPayload = apiPayloadRepository.save(payload);
            savedConfig.setPayload(savedPayload);
        }

        // Persist response mappings
        if (request.getResponseMappings() != null && !request.getResponseMappings().isEmpty()) {
            List<ApiResponseMapping> mappings = request.getResponseMappings().stream()
                    .map(dto -> {
                        ApiResponseMapping mapping = new ApiResponseMapping();
                        mapping.setApiConfig(savedConfig);
                        mapping.setResponsePath(dto.getResponsePath());
                        mapping.setContextVariableName(dto.getContextVariableName());
                        return mapping;
                    })
                    .collect(Collectors.toList());
            apiResponseMappingRepository.saveAll(mappings);
            savedConfig.setResponseMappings(mappings);
        }

        return mapToResponse(savedConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiConfigResponse getById(Long id) {
        ApiConfig config = apiConfigRepository.findById(id)
                .orElseThrow(() -> new ApiConfigNotFoundException(id));
        return mapToResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiConfigResponse> listAll() {
        return apiConfigRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ApiConfigResponse update(Long id, ApiConfigRequest request) {
        ApiConfig config = apiConfigRepository.findById(id)
                .orElseThrow(() -> new ApiConfigNotFoundException(id));

        validateRequiredFields(request);
        String normalizedMethod = normalizeAndValidateMethod(request.getMethod());
        validateNumericRanges(request);
        validateCollectionSizes(request);

        if (request.getResponseMappings() != null && !request.getResponseMappings().isEmpty()) {
            validateResponseMappings(request.getResponseMappings());
        }

        if (apiConfigRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DuplicateApiConfigNameException(request.getName());
        }

        config.setName(request.getName());
        config.setUrl(request.getUrl());
        config.setMethod(normalizedMethod);
        config.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS);
        config.setRetryCount(request.getRetryCount() != null ? request.getRetryCount() : DEFAULT_RETRY_COUNT);
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setClientId(request.getClientId());

        // Replace-all strategy for headers when present in request
        if (request.getHeaders() != null) {
            apiHeaderRepository.deleteAllByApiConfig(config);
            if (!request.getHeaders().isEmpty()) {
                List<ApiHeader> headers = request.getHeaders().stream()
                        .map(dto -> {
                            ApiHeader header = new ApiHeader();
                            header.setApiConfig(config);
                            header.setHeaderName(dto.getHeaderName());
                            header.setHeaderValue(dto.getHeaderValue());
                            return header;
                        })
                        .collect(Collectors.toList());
                apiHeaderRepository.saveAll(headers);
                config.setHeaders(headers);
            } else {
                config.setHeaders(new ArrayList<>());
            }
        }

        // Replace payload when present in request
        if (request.getPayloadTemplate() != null) {
            apiPayloadRepository.deleteByApiConfig(config);
            ApiPayload payload = new ApiPayload();
            payload.setApiConfig(config);
            payload.setPayloadTemplate(castToMap(request.getPayloadTemplate()));
            ApiPayload savedPayload = apiPayloadRepository.save(payload);
            config.setPayload(savedPayload);
        }

        // Replace-all strategy for response mappings when present in request
        if (request.getResponseMappings() != null) {
            apiResponseMappingRepository.deleteAllByApiConfig(config);
            if (!request.getResponseMappings().isEmpty()) {
                List<ApiResponseMapping> mappings = request.getResponseMappings().stream()
                        .map(dto -> {
                            ApiResponseMapping mapping = new ApiResponseMapping();
                            mapping.setApiConfig(config);
                            mapping.setResponsePath(dto.getResponsePath());
                            mapping.setContextVariableName(dto.getContextVariableName());
                            return mapping;
                        })
                        .collect(Collectors.toList());
                apiResponseMappingRepository.saveAll(mappings);
                config.setResponseMappings(mappings);
            } else {
                config.setResponseMappings(new ArrayList<>());
            }
        }

        ApiConfig savedConfig = apiConfigRepository.save(config);
        return mapToResponse(savedConfig);
    }

    @Override
    public void delete(Long id) {
        ApiConfig config = apiConfigRepository.findById(id)
                .orElseThrow(() -> new ApiConfigNotFoundException(id));
        apiConfigRepository.delete(config);
    }

    // --- Private helper methods ---

    private void validateRequiredFields(ApiConfigRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Field 'name' is required");
        }
        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("Field 'url' is required");
        }
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            throw new IllegalArgumentException("Field 'method' is required");
        }
    }

    private String normalizeAndValidateMethod(String method) {
        String normalized = method.toUpperCase();
        if (!VALID_METHODS.contains(normalized)) {
            throw new InvalidMethodException(method);
        }
        return normalized;
    }

    private void validateNumericRanges(ApiConfigRequest request) {
        if (request.getTimeoutMs() != null) {
            if (request.getTimeoutMs() < 1 || request.getTimeoutMs() > 300000) {
                throw new IllegalArgumentException("timeout_ms must be between 1 and 300000");
            }
        }
        if (request.getRetryCount() != null) {
            if (request.getRetryCount() < 0 || request.getRetryCount() > 10) {
                throw new IllegalArgumentException("retry_count must be between 0 and 10");
            }
        }
    }

    private void validateCollectionSizes(ApiConfigRequest request) {
        if (request.getHeaders() != null && request.getHeaders().size() > MAX_HEADERS) {
            throw new IllegalArgumentException("Maximum of 50 headers allowed");
        }
        if (request.getResponseMappings() != null && request.getResponseMappings().size() > MAX_RESPONSE_MAPPINGS) {
            throw new IllegalArgumentException("Maximum of 50 response mappings allowed");
        }
    }

    private void validateResponseMappings(List<ApiResponseMappingDto> mappings) {
        Set<String> seenNames = new HashSet<>();
        for (ApiResponseMappingDto mapping : mappings) {
            String name = mapping.getContextVariableName();

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Field 'context_variable_name' is required");
            }

            if (name.length() > 255) {
                throw new IllegalArgumentException("context_variable_name must not exceed 255 characters");
            }

            if (!CONTEXT_VARIABLE_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "context_variable_name must start with a letter or underscore and contain only alphanumeric characters and underscores");
            }

            if (!seenNames.add(name)) {
                throw new IllegalArgumentException("Duplicate context_variable_name: '" + name + "'");
            }
        }
    }

    private ApiConfigResponse mapToResponse(ApiConfig config) {
        ApiConfigResponse response = new ApiConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setUrl(config.getUrl());
        response.setMethod(config.getMethod());
        response.setTimeoutMs(config.getTimeoutMs());
        response.setRetryCount(config.getRetryCount());
        response.setUsername(config.getUsername());
        response.setPassword(config.getPassword());
        response.setClientId(config.getClientId());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());

        // Map headers
        if (config.getHeaders() != null) {
            List<ApiHeaderDto> headerDtos = config.getHeaders().stream()
                    .map(h -> {
                        ApiHeaderDto dto = new ApiHeaderDto();
                        dto.setHeaderName(h.getHeaderName());
                        dto.setHeaderValue(h.getHeaderValue());
                        return dto;
                    })
                    .collect(Collectors.toList());
            response.setHeaders(headerDtos);
        } else {
            response.setHeaders(Collections.emptyList());
        }

        // Map payload
        if (config.getPayload() != null) {
            response.setPayloadTemplate(config.getPayload().getPayloadTemplate());
        }

        // Map response mappings
        if (config.getResponseMappings() != null) {
            List<ApiResponseMappingDto> mappingDtos = config.getResponseMappings().stream()
                    .map(m -> {
                        ApiResponseMappingDto dto = new ApiResponseMappingDto();
                        dto.setResponsePath(m.getResponsePath());
                        dto.setContextVariableName(m.getContextVariableName());
                        return dto;
                    })
                    .collect(Collectors.toList());
            response.setResponseMappings(mappingDtos);
        } else {
            response.setResponseMappings(Collections.emptyList());
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object json) {
        if (json instanceof Map) {
            return (Map<String, Object>) json;
        }
        return null;
    }
}
