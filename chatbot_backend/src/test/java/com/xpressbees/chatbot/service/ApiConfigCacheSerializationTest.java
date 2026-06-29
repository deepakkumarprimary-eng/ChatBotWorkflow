package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import com.xpressbees.chatbot.entity.ApiPayload;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import net.jqwik.api.*;

import java.util.*;

// Feature: redis-caching-and-performance, Property 3: ApiConfig cache serialization round-trip
/**
 * Property-based test for ApiConfig cache serialization round-trip (full entity graph).
 *
 * For any valid ApiConfig entity graph (with arbitrary headers, payload template, and
 * response mappings), serializing the full graph to JSON and deserializing it back
 * SHALL produce an equivalent ApiConfig with all child entities intact and field values matching.
 *
 * Validates: Requirements 2.1, 2.2
 */
class ApiConfigCacheSerializationTest {

    private final ObjectMapper objectMapper;

    ApiConfigCacheSerializationTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 3: ApiConfig cache serialization round-trip")
    void apiConfigSerializationRoundTripPreservesAllFields(
            @ForAll("apiConfigs") ApiConfig original) throws JsonProcessingException {
        // Validates: Requirements 2.1, 2.2

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back to ApiConfig
        ApiConfig deserialized = objectMapper.readValue(json, ApiConfig.class);

        // Assert top-level fields match
        assert Objects.equals(original.getId(), deserialized.getId()) :
                "ID mismatch: expected " + original.getId() + " but got " + deserialized.getId();
        assert Objects.equals(original.getName(), deserialized.getName()) :
                "Name mismatch: expected " + original.getName() + " but got " + deserialized.getName();
        assert Objects.equals(original.getUrl(), deserialized.getUrl()) :
                "URL mismatch: expected " + original.getUrl() + " but got " + deserialized.getUrl();
        assert Objects.equals(original.getMethod(), deserialized.getMethod()) :
                "Method mismatch: expected " + original.getMethod() + " but got " + deserialized.getMethod();
        assert Objects.equals(original.getTimeoutMs(), deserialized.getTimeoutMs()) :
                "timeoutMs mismatch: expected " + original.getTimeoutMs() + " but got " + deserialized.getTimeoutMs();
        assert Objects.equals(original.getRetryCount(), deserialized.getRetryCount()) :
                "retryCount mismatch: expected " + original.getRetryCount() + " but got " + deserialized.getRetryCount();
        assert Objects.equals(original.getUsername(), deserialized.getUsername()) :
                "Username mismatch";
        assert Objects.equals(original.getPassword(), deserialized.getPassword()) :
                "Password mismatch";
        assert Objects.equals(original.getClientId(), deserialized.getClientId()) :
                "ClientId mismatch";

        // Assert headers collection
        assertHeadersMatch(original.getHeaders(), deserialized.getHeaders());

        // Assert payload
        assertPayloadMatches(original.getPayload(), deserialized.getPayload());

        // Assert response mappings
        assertResponseMappingsMatch(original.getResponseMappings(), deserialized.getResponseMappings());
    }

    private void assertHeadersMatch(List<ApiHeader> expected, List<ApiHeader> actual) {
        if (expected == null && actual == null) return;
        assert expected != null && actual != null :
                "Headers null mismatch: expected=" + expected + ", actual=" + actual;
        assert expected.size() == actual.size() :
                "Headers size mismatch: expected " + expected.size() + " but got " + actual.size();

        for (int i = 0; i < expected.size(); i++) {
            ApiHeader exp = expected.get(i);
            ApiHeader act = actual.get(i);
            assert Objects.equals(exp.getId(), act.getId()) :
                    "Header[" + i + "] ID mismatch";
            assert Objects.equals(exp.getHeaderName(), act.getHeaderName()) :
                    "Header[" + i + "] headerName mismatch: expected '" + exp.getHeaderName() + "' got '" + act.getHeaderName() + "'";
            assert Objects.equals(exp.getHeaderValue(), act.getHeaderValue()) :
                    "Header[" + i + "] headerValue mismatch: expected '" + exp.getHeaderValue() + "' got '" + act.getHeaderValue() + "'";
            // Note: apiConfig back-reference is @JsonIgnore, so it will be null after deserialization
        }
    }

    private void assertPayloadMatches(ApiPayload expected, ApiPayload actual) {
        if (expected == null && actual == null) return;
        assert expected != null && actual != null :
                "Payload null mismatch: expected=" + expected + ", actual=" + actual;
        assert Objects.equals(expected.getId(), actual.getId()) :
                "Payload ID mismatch";
        assert Objects.equals(expected.getPayloadTemplate(), actual.getPayloadTemplate()) :
                "Payload payloadTemplate mismatch: expected " + expected.getPayloadTemplate() + " got " + actual.getPayloadTemplate();
        // Note: apiConfig back-reference is @JsonIgnore, so it will be null after deserialization
    }

    private void assertResponseMappingsMatch(List<ApiResponseMapping> expected, List<ApiResponseMapping> actual) {
        if (expected == null && actual == null) return;
        assert expected != null && actual != null :
                "ResponseMappings null mismatch: expected=" + expected + ", actual=" + actual;
        assert expected.size() == actual.size() :
                "ResponseMappings size mismatch: expected " + expected.size() + " but got " + actual.size();

        for (int i = 0; i < expected.size(); i++) {
            ApiResponseMapping exp = expected.get(i);
            ApiResponseMapping act = actual.get(i);
            assert Objects.equals(exp.getId(), act.getId()) :
                    "ResponseMapping[" + i + "] ID mismatch";
            assert Objects.equals(exp.getResponsePath(), act.getResponsePath()) :
                    "ResponseMapping[" + i + "] responsePath mismatch: expected '" + exp.getResponsePath() + "' got '" + act.getResponsePath() + "'";
            assert Objects.equals(exp.getContextVariableName(), act.getContextVariableName()) :
                    "ResponseMapping[" + i + "] contextVariableName mismatch: expected '" + exp.getContextVariableName() + "' got '" + act.getContextVariableName() + "'";
            // Note: apiConfig back-reference is @JsonIgnore, so it will be null after deserialization
        }
    }

    // ========================== Generators ==========================

    @Provide
    Arbitrary<ApiConfig> apiConfigs() {
        // Split into two combine calls since jqwik supports max 8 args
        return Combinators.combine(
                Arbitraries.longs().greaterOrEqual(1),               // id
                alphanumericStrings(1, 50),                          // name
                alphanumericStrings(5, 100),                         // url
                httpMethods(),                                       // method
                Arbitraries.integers().greaterOrEqual(100).lessOrEqual(30000), // timeoutMs
                Arbitraries.integers().between(0, 5)                 // retryCount
        ).flatAs((id, name, url, method, timeoutMs, retryCount) ->
            Combinators.combine(
                    headerLists(),
                    optionalPayload(),
                    responseMappingLists()
            ).as((headers, payload, mappings) -> {
                ApiConfig config = new ApiConfig();
                config.setId(id);
                config.setName(name);
                config.setUrl(url);
                config.setMethod(method);
                config.setTimeoutMs(timeoutMs);
                config.setRetryCount(retryCount);
                config.setHeaders(headers);
                config.setPayload(payload);
                config.setResponseMappings(mappings);
                return config;
            })
        );
    }

    private Arbitrary<String> alphanumericStrings(int minLength, int maxLength) {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(minLength)
                .ofMaxLength(maxLength);
    }

    private Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE");
    }

    private Arbitrary<List<ApiHeader>> headerLists() {
        return headerArbitrary().list().ofMinSize(0).ofMaxSize(10);
    }

    private Arbitrary<ApiHeader> headerArbitrary() {
        return Combinators.combine(
                Arbitraries.longs().greaterOrEqual(1),       // id
                alphanumericStrings(1, 30),                  // headerName
                alphanumericStrings(1, 50)                   // headerValue
        ).as((id, name, value) -> {
            ApiHeader header = new ApiHeader();
            header.setId(id);
            header.setApiConfig(null); // @JsonIgnore back-reference excluded
            header.setHeaderName(name);
            header.setHeaderValue(value);
            return header;
        });
    }

    private Arbitrary<ApiPayload> optionalPayload() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                payloadArbitrary()
        );
    }

    private Arbitrary<ApiPayload> payloadArbitrary() {
        return Combinators.combine(
                Arbitraries.longs().greaterOrEqual(1),       // id
                payloadTemplateMap()                          // payloadTemplate
        ).as((id, template) -> {
            ApiPayload payload = new ApiPayload();
            payload.setId(id);
            payload.setApiConfig(null); // @JsonIgnore back-reference excluded
            payload.setPayloadTemplate(template);
            return payload;
        });
    }

    private Arbitrary<Map<String, Object>> payloadTemplateMap() {
        // Keep values simple (strings, numbers, booleans) to avoid serialization issues
        Arbitrary<Object> simpleValues = Arbitraries.oneOf(
                alphanumericStrings(1, 20).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.of(Boolean.TRUE, Boolean.FALSE).map(b -> (Object) b)
        );

        Arbitrary<String> keys = alphanumericStrings(1, 15);

        return Arbitraries.maps(keys, simpleValues)
                .ofMinSize(0)
                .ofMaxSize(5);
    }

    private Arbitrary<List<ApiResponseMapping>> responseMappingLists() {
        return responseMappingArbitrary().list().ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<ApiResponseMapping> responseMappingArbitrary() {
        return Combinators.combine(
                Arbitraries.longs().greaterOrEqual(1),       // id
                alphanumericStrings(3, 50),                  // responsePath
                alphanumericStrings(3, 30)                   // contextVariableName
        ).as((id, responsePath, contextVariableName) -> {
            ApiResponseMapping mapping = new ApiResponseMapping();
            mapping.setId(id);
            mapping.setApiConfig(null); // @JsonIgnore back-reference excluded
            mapping.setResponsePath(responsePath);
            mapping.setContextVariableName(contextVariableName);
            return mapping;
        });
    }
}
