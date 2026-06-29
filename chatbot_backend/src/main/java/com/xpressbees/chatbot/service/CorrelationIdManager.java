package com.xpressbees.chatbot.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Manages the correlation ID lifecycle in the SLF4J MDC (Mapped Diagnostic Context).
 * The correlation ID is set to the session ID at the start of a workflow execution
 * and cleared when the execution completes, enabling log tracing across all entries
 * produced during that execution.
 */
@Component
public class CorrelationIdManager {

    private static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Places the given session ID into the MDC as the correlation ID.
     *
     * @param sessionId the session identifier to use as correlation ID
     */
    public void set(String sessionId) {
        MDC.put(CORRELATION_ID_KEY, sessionId);
    }

    /**
     * Removes the correlation ID from the MDC.
     * Should be called in finally blocks to prevent MDC leakage across threads.
     */
    public void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }

    /**
     * Retrieves the current correlation ID from the MDC.
     *
     * @return the current correlation ID, or null if not set
     */
    public String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }
}
