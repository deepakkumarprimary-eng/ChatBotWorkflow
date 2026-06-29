package com.xpressbees.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Listens for Spring context close events to coordinate graceful shutdown.
 * When the context closes, this listener signals the ExecutionTracker to stop
 * accepting new executions and waits for in-progress executions to complete
 * within a configurable timeout period.
 *
 * If active executions do not drain before the timeout, a warning is logged
 * with the count of executions that will be interrupted.
 */
@Component
public class GracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownListener.class);
    private static final long POLL_INTERVAL_MS = 500;

    private final ExecutionTracker executionTracker;
    private final long shutdownTimeoutMs;

    public GracefulShutdownListener(
            ExecutionTracker executionTracker,
            @Value("${chatbot.shutdown.timeout-seconds:30}") int shutdownTimeoutSeconds) {
        this.executionTracker = executionTracker;
        this.shutdownTimeoutMs = shutdownTimeoutSeconds * 1000L;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        executionTracker.beginShutdown();
        log.info("Graceful shutdown initiated, waiting for active executions to complete (timeout: {}s)",
                shutdownTimeoutMs / 1000);

        long deadline = System.currentTimeMillis() + shutdownTimeoutMs;

        while (executionTracker.getActiveCount() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Shutdown wait interrupted");
                break;
            }
        }

        int remaining = executionTracker.getActiveCount();
        if (remaining > 0) {
            log.warn("Force shutdown with {} in-progress executions", remaining);
        } else {
            log.info("All active executions completed, proceeding with shutdown");
        }
    }
}
