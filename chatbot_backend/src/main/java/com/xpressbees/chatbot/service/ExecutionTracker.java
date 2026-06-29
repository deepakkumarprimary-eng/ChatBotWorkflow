package com.xpressbees.chatbot.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Tracks in-progress workflow executions for graceful shutdown support.
 * Uses an AtomicInteger for thread-safe active execution counting and a
 * volatile boolean to signal shutdown state across threads.
 *
 * During normal operation, tryStart() increments the counter and returns true.
 * Once beginShutdown() is called, tryStart() returns false to reject new executions,
 * allowing the GracefulShutdownListener to wait for active executions to drain.
 */
@Component
public class ExecutionTracker {

    private final AtomicInteger activeExecutions = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    /**
     * Attempts to start a new execution. Returns false if the application
     * is shutting down, indicating the request should be rejected.
     * Otherwise increments the active execution counter and returns true.
     *
     * @return true if the execution was accepted, false if shutting down
     */
    public boolean tryStart() {
        if (shuttingDown) {
            return false;
        }
        activeExecutions.incrementAndGet();
        return true;
    }

    /**
     * Marks an execution as complete by decrementing the active execution counter.
     * Must be called in a finally block to ensure the counter stays accurate.
     */
    public void complete() {
        activeExecutions.decrementAndGet();
    }

    /**
     * Returns the current number of active (in-progress) workflow executions.
     *
     * @return the active execution count
     */
    public int getActiveCount() {
        return activeExecutions.get();
    }

    /**
     * Signals the start of application shutdown. After this call, tryStart()
     * will return false for all subsequent invocations.
     */
    public void beginShutdown() {
        shuttingDown = true;
    }

    /**
     * Returns whether the application is in the process of shutting down.
     *
     * @return true if shutdown has been initiated
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }
}
