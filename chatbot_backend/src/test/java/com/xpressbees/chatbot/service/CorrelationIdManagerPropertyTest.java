package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import org.slf4j.MDC;

/**
 * Property 2: MDC correlation ID lifecycle round-trip
 *
 * For any valid session ID, when set() is called the MDC SHALL contain the session ID
 * as the correlation ID, and after clear() is called the MDC SHALL no longer contain
 * the correlation ID.
 *
 * Validates: Requirements 2.2, 2.4
 *
 * Feature: production-readiness, Property 2: MDC correlation ID lifecycle round-trip
 */
class CorrelationIdManagerPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: production-readiness, Property 2: MDC correlation ID lifecycle round-trip")
    void afterSetMdcContainsCorrelationIdAndAfterClearMdcDoesNot(
            @ForAll("sessionIds") String sessionId) {

        // Ensure MDC is clean before test
        MDC.clear();

        CorrelationIdManager manager = new CorrelationIdManager();

        // Act: set the correlation ID
        manager.set(sessionId);

        // Assert: MDC contains the session ID after set()
        String mdcValue = MDC.get("correlationId");
        assert mdcValue != null :
                "MDC should contain correlationId after set(), but got null";
        assert mdcValue.equals(sessionId) :
                "MDC correlationId should equal the session ID. Expected '" + sessionId + "' got '" + mdcValue + "'";

        // Also verify via get() method
        String managerValue = manager.get();
        assert managerValue != null :
                "manager.get() should return the correlationId after set(), but got null";
        assert managerValue.equals(sessionId) :
                "manager.get() should equal the session ID. Expected '" + sessionId + "' got '" + managerValue + "'";

        // Act: clear the correlation ID
        manager.clear();

        // Assert: MDC no longer contains the correlation ID after clear()
        String mdcValueAfterClear = MDC.get("correlationId");
        assert mdcValueAfterClear == null :
                "MDC should not contain correlationId after clear(), but got '" + mdcValueAfterClear + "'";

        // Also verify via get() method
        String managerValueAfterClear = manager.get();
        assert managerValueAfterClear == null :
                "manager.get() should return null after clear(), but got '" + managerValueAfterClear + "'";
    }

    @Provide
    Arbitrary<String> sessionIds() {
        // Generate a variety of session ID formats:
        // - UUID-style IDs
        // - Prefixed IDs (e.g., "sess_abc123")
        // - Alphanumeric strings of varying lengths
        Arbitrary<String> uuidStyle = Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .ofLength(8)
                .flatMap(part1 -> Arbitraries.strings()
                        .withCharRange('a', 'f')
                        .numeric()
                        .ofLength(4)
                        .map(part2 -> part1 + "-" + part2 + "-4xxx-yxxx"));

        Arbitrary<String> prefixed = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "sess_" + s);

        Arbitrary<String> alphanumeric = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(50);

        return Arbitraries.oneOf(uuidStyle, prefixed, alphanumeric);
    }
}
