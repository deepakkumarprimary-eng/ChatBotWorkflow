package com.xpressbees.chatbot.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for WebSocketResilienceProperties thread pool validation.
 *
 * <p><b>Feature: websocket-thread-pool</b></p>
 */
class WebSocketThreadPoolValidationPropertyTest {

    private final Validator validator;

    WebSocketThreadPoolValidationPropertyTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    // --- Property 3: Invalid Pool Size Rejection ---

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: inboundPoolCoreSize <= 0 produces constraint violation")
    void invalidInboundCoreSize(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setInboundPoolCoreSize(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("inboundPoolCoreSize"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: inboundPoolMaxSize <= 0 produces constraint violation")
    void invalidInboundMaxSize(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setInboundPoolMaxSize(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("inboundPoolMaxSize"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: inboundPoolQueueCapacity <= 0 produces constraint violation")
    void invalidInboundQueueCapacity(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setInboundPoolQueueCapacity(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("inboundPoolQueueCapacity"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: outboundPoolCoreSize <= 0 produces constraint violation")
    void invalidOutboundCoreSize(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setOutboundPoolCoreSize(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("outboundPoolCoreSize"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: outboundPoolMaxSize <= 0 produces constraint violation")
    void invalidOutboundMaxSize(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setOutboundPoolMaxSize(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("outboundPoolMaxSize"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 3: outboundPoolQueueCapacity <= 0 produces constraint violation")
    void invalidOutboundQueueCapacity(@ForAll @IntRange(min = -1000, max = 0) int invalidValue) {
        WebSocketResilienceProperties props = validProperties();
        props.setOutboundPoolQueueCapacity(invalidValue);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("outboundPoolQueueCapacity"));
    }

    // --- Property 4: Cross-Field Max >= Core Validation ---

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 4: inbound validation fails when maxSize < coreSize")
    void inboundValidationFailsWhenMaxLessThanCore(
            @ForAll @IntRange(min = 2, max = 500) int coreSize,
            @ForAll @IntRange(min = 1, max = 499) int maxSizeDelta) {

        int maxSize = Math.max(1, coreSize - maxSizeDelta); // ensure maxSize >= 1 but < coreSize
        if (maxSize >= coreSize) return; // skip if delta didn't produce max < core

        WebSocketResilienceProperties props = validProperties();
        props.setInboundPoolCoreSize(coreSize);
        props.setInboundPoolMaxSize(maxSize);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getMessage().contains("inbound-pool-max-size must be >= inbound-pool-core-size"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 4: inbound validation passes when maxSize >= coreSize")
    void inboundValidationPassesWhenMaxGreaterOrEqualCore(
            @ForAll @IntRange(min = 1, max = 500) int coreSize,
            @ForAll @IntRange(min = 0, max = 500) int headroom) {

        int maxSize = coreSize + headroom;

        WebSocketResilienceProperties props = validProperties();
        props.setInboundPoolCoreSize(coreSize);
        props.setInboundPoolMaxSize(maxSize);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).noneMatch(v ->
                v.getMessage().contains("inbound-pool-max-size must be >= inbound-pool-core-size"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 4: outbound validation fails when maxSize < coreSize")
    void outboundValidationFailsWhenMaxLessThanCore(
            @ForAll @IntRange(min = 2, max = 500) int coreSize,
            @ForAll @IntRange(min = 1, max = 499) int maxSizeDelta) {

        int maxSize = Math.max(1, coreSize - maxSizeDelta);
        if (maxSize >= coreSize) return;

        WebSocketResilienceProperties props = validProperties();
        props.setOutboundPoolCoreSize(coreSize);
        props.setOutboundPoolMaxSize(maxSize);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getMessage().contains("outbound-pool-max-size must be >= outbound-pool-core-size"));
    }

    @Property(tries = 100)
    @Tag("websocket-thread-pool")
    @Label("Feature: websocket-thread-pool, Property 4: outbound validation passes when maxSize >= coreSize")
    void outboundValidationPassesWhenMaxGreaterOrEqualCore(
            @ForAll @IntRange(min = 1, max = 500) int coreSize,
            @ForAll @IntRange(min = 0, max = 500) int headroom) {

        int maxSize = coreSize + headroom;

        WebSocketResilienceProperties props = validProperties();
        props.setOutboundPoolCoreSize(coreSize);
        props.setOutboundPoolMaxSize(maxSize);

        Set<ConstraintViolation<WebSocketResilienceProperties>> violations = validator.validate(props);

        assertThat(violations).noneMatch(v ->
                v.getMessage().contains("outbound-pool-max-size must be >= outbound-pool-core-size"));
    }

    /**
     * Creates a WebSocketResilienceProperties instance with all valid default values.
     */
    private WebSocketResilienceProperties validProperties() {
        WebSocketResilienceProperties props = new WebSocketResilienceProperties();
        // Defaults are already valid (core=10, max=50, queue=200)
        return props;
    }
}
