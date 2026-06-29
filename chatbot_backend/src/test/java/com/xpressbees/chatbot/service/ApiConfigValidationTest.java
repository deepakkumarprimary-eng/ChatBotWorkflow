package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ApiConfigRequest;
import com.xpressbees.chatbot.dto.ApiHeaderDto;
import com.xpressbees.chatbot.dto.ApiResponseMappingDto;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.exception.InvalidMethodException;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import com.xpressbees.chatbot.repository.ApiHeaderRepository;
import com.xpressbees.chatbot.repository.ApiPayloadRepository;
import com.xpressbees.chatbot.repository.ApiResponseMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ApiConfigServiceImpl validation logic.
 * Uses Mockito to mock repositories, isolating the validation behavior in the create method.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9
 */
@ExtendWith(MockitoExtension.class)
class ApiConfigValidationTest {

    @Mock
    private ApiConfigRepository apiConfigRepository;

    @Mock
    private ApiHeaderRepository apiHeaderRepository;

    @Mock
    private ApiPayloadRepository apiPayloadRepository;

    @Mock
    private ApiResponseMappingRepository apiResponseMappingRepository;

    private ApiConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApiConfigServiceImpl(
                apiConfigRepository,
                apiHeaderRepository,
                apiPayloadRepository,
                apiResponseMappingRepository
        );
    }

    private ApiConfigRequest validRequest() {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("test-config");
        request.setUrl("https://api.example.com/endpoint");
        request.setMethod("POST");
        request.setTimeoutMs(5000);
        request.setRetryCount(1);
        return request;
    }

    private void stubRepositoryForSuccess() {
        when(apiConfigRepository.existsByName(any())).thenReturn(false);
        when(apiConfigRepository.save(any(ApiConfig.class))).thenAnswer(invocation -> {
            ApiConfig config = invocation.getArgument(0);
            config.setId(1L);
            return config;
        });
    }

    // --- Method normalization tests (Requirement 5.1) ---

    @ParameterizedTest(name = "method \"{0}\" should be normalized to uppercase")
    @ValueSource(strings = {"post", "Post", "POST", "pOsT", "get", "Get", "GET", "gEt",
            "put", "Put", "PUT", "pUt", "delete", "Delete", "DELETE", "dElEtE"})
    @DisplayName("Valid methods are normalized to uppercase")
    void validMethodsAreNormalizedToUppercase(String method) {
        ApiConfigRequest request = validRequest();
        request.setMethod(method);
        stubRepositoryForSuccess();

        var response = service.create(request);

        assertThat(response.getMethod()).isEqualTo(method.toUpperCase());
    }

    // --- Invalid method tests (Requirement 5.2) ---

    @ParameterizedTest(name = "method \"{0}\" should throw InvalidMethodException")
    @ValueSource(strings = {"PATCH", "OPTIONS", "FOO", "HEAD", "CONNECT", "TRACE", "abc123"})
    @DisplayName("Invalid methods throw InvalidMethodException")
    void invalidMethodsThrowInvalidMethodException(String method) {
        ApiConfigRequest request = validRequest();
        request.setMethod(method);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidMethodException.class);
    }

    // --- Timeout range validation (Requirements 5.3) ---

    @Test
    @DisplayName("timeoutMs < 1 throws IllegalArgumentException")
    void timeoutMsLessThanOneThrows() {
        ApiConfigRequest request = validRequest();
        request.setTimeoutMs(0);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout_ms");
    }

    @Test
    @DisplayName("timeoutMs = -1 throws IllegalArgumentException")
    void timeoutMsNegativeThrows() {
        ApiConfigRequest request = validRequest();
        request.setTimeoutMs(-1);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout_ms");
    }

    @Test
    @DisplayName("timeoutMs > 300000 throws IllegalArgumentException")
    void timeoutMsExceedsMaxThrows() {
        ApiConfigRequest request = validRequest();
        request.setTimeoutMs(300001);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout_ms");
    }

    @Test
    @DisplayName("timeoutMs at boundaries (1 and 300000) are accepted")
    void timeoutMsBoundariesAccepted() {
        stubRepositoryForSuccess();

        ApiConfigRequest request1 = validRequest();
        request1.setTimeoutMs(1);
        var response1 = service.create(request1);
        assertThat(response1.getTimeoutMs()).isEqualTo(1);

        ApiConfigRequest request2 = validRequest();
        request2.setTimeoutMs(300000);
        var response2 = service.create(request2);
        assertThat(response2.getTimeoutMs()).isEqualTo(300000);
    }

    // --- Retry count range validation (Requirement 5.4) ---

    @Test
    @DisplayName("retryCount < 0 throws IllegalArgumentException")
    void retryCountNegativeThrows() {
        ApiConfigRequest request = validRequest();
        request.setRetryCount(-1);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry_count");
    }

    @Test
    @DisplayName("retryCount > 10 throws IllegalArgumentException")
    void retryCountExceedsMaxThrows() {
        ApiConfigRequest request = validRequest();
        request.setRetryCount(11);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry_count");
    }

    @Test
    @DisplayName("retryCount at boundaries (0 and 10) are accepted")
    void retryCountBoundariesAccepted() {
        stubRepositoryForSuccess();

        ApiConfigRequest request0 = validRequest();
        request0.setRetryCount(0);
        var response0 = service.create(request0);
        assertThat(response0.getRetryCount()).isEqualTo(0);

        ApiConfigRequest request10 = validRequest();
        request10.setRetryCount(10);
        var response10 = service.create(request10);
        assertThat(response10.getRetryCount()).isEqualTo(10);
    }

    // --- Headers collection size validation (Requirement 5.5) ---

    @Test
    @DisplayName("headers list > 50 entries throws IllegalArgumentException")
    void headersExceedMaxSizeThrows() {
        ApiConfigRequest request = validRequest();
        List<ApiHeaderDto> headers = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ApiHeaderDto header = new ApiHeaderDto();
            header.setHeaderName("Header-" + i);
            header.setHeaderValue("Value-" + i);
            headers.add(header);
        }
        request.setHeaders(headers);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50 headers");
    }

    @Test
    @DisplayName("headers list with exactly 50 entries is accepted")
    void headersAtMaxSizeAccepted() {
        stubRepositoryForSuccess();
        when(apiHeaderRepository.saveAll(any())).thenReturn(List.of());

        ApiConfigRequest request = validRequest();
        List<ApiHeaderDto> headers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ApiHeaderDto header = new ApiHeaderDto();
            header.setHeaderName("Header-" + i);
            header.setHeaderValue("Value-" + i);
            headers.add(header);
        }
        request.setHeaders(headers);

        var response = service.create(request);
        assertThat(response).isNotNull();
    }

    // --- Response mappings collection size validation (Requirement 5.6) ---

    @Test
    @DisplayName("response mappings list > 50 entries throws IllegalArgumentException")
    void responseMappingsExceedMaxSizeThrows() {
        ApiConfigRequest request = validRequest();
        List<ApiResponseMappingDto> mappings = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ApiResponseMappingDto mapping = new ApiResponseMappingDto();
            mapping.setResponsePath("$.data[" + i + "]");
            mapping.setContextVariableName("var_" + i);
            mappings.add(mapping);
        }
        request.setResponseMappings(mappings);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50 response mappings");
    }

    @Test
    @DisplayName("response mappings list with exactly 50 entries is accepted")
    void responseMappingsAtMaxSizeAccepted() {
        stubRepositoryForSuccess();
        when(apiResponseMappingRepository.saveAll(any())).thenReturn(List.of());

        ApiConfigRequest request = validRequest();
        List<ApiResponseMappingDto> mappings = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ApiResponseMappingDto mapping = new ApiResponseMappingDto();
            mapping.setResponsePath("$.data[" + i + "]");
            mapping.setContextVariableName("var_" + i);
            mappings.add(mapping);
        }
        request.setResponseMappings(mappings);

        var response = service.create(request);
        assertThat(response).isNotNull();
    }

    // --- Invalid context_variable_name pattern (Requirement 5.7) ---

    @ParameterizedTest(name = "variable name \"{0}\" should throw IllegalArgumentException")
    @ValueSource(strings = {"123abc", "my-var", "my.var", "my var", "@name", "var!"})
    @DisplayName("Invalid context_variable_name pattern throws IllegalArgumentException")
    void invalidContextVariableNamePatternThrows(String invalidName) {
        ApiConfigRequest request = validRequest();
        ApiResponseMappingDto mapping = new ApiResponseMappingDto();
        mapping.setResponsePath("$.data");
        mapping.setContextVariableName(invalidName);
        request.setResponseMappings(List.of(mapping));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "variable name \"{0}\" should be accepted")
    @ValueSource(strings = {"validName", "_private", "camelCase", "snake_case", "A", "_", "a1b2c3"})
    @DisplayName("Valid context_variable_name patterns are accepted")
    void validContextVariableNamePatternsAccepted(String validName) {
        stubRepositoryForSuccess();
        when(apiResponseMappingRepository.saveAll(any())).thenReturn(List.of());

        ApiConfigRequest request = validRequest();
        ApiResponseMappingDto mapping = new ApiResponseMappingDto();
        mapping.setResponsePath("$.data");
        mapping.setContextVariableName(validName);
        request.setResponseMappings(List.of(mapping));

        var response = service.create(request);
        assertThat(response).isNotNull();
    }

    // --- Duplicate context_variable_name (Requirement 5.8) ---

    @Test
    @DisplayName("Duplicate context_variable_name throws IllegalArgumentException")
    void duplicateContextVariableNameThrows() {
        ApiConfigRequest request = validRequest();
        ApiResponseMappingDto mapping1 = new ApiResponseMappingDto();
        mapping1.setResponsePath("$.data.field1");
        mapping1.setContextVariableName("duplicate_name");

        ApiResponseMappingDto mapping2 = new ApiResponseMappingDto();
        mapping2.setResponsePath("$.data.field2");
        mapping2.setContextVariableName("duplicate_name");

        request.setResponseMappings(List.of(mapping1, mapping2));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    // --- context_variable_name > 255 chars (Requirement 5.9) ---

    @Test
    @DisplayName("context_variable_name > 255 chars throws IllegalArgumentException")
    void contextVariableNameExceedsMaxLengthThrows() {
        ApiConfigRequest request = validRequest();
        String longName = "a".repeat(256);
        ApiResponseMappingDto mapping = new ApiResponseMappingDto();
        mapping.setResponsePath("$.data");
        mapping.setContextVariableName(longName);
        request.setResponseMappings(List.of(mapping));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    @DisplayName("context_variable_name with exactly 255 chars is accepted")
    void contextVariableNameAtMaxLengthAccepted() {
        stubRepositoryForSuccess();
        when(apiResponseMappingRepository.saveAll(any())).thenReturn(List.of());

        ApiConfigRequest request = validRequest();
        String maxName = "a".repeat(255);
        ApiResponseMappingDto mapping = new ApiResponseMappingDto();
        mapping.setResponsePath("$.data");
        mapping.setContextVariableName(maxName);
        request.setResponseMappings(List.of(mapping));

        var response = service.create(request);
        assertThat(response).isNotNull();
    }
}
