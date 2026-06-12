package com.chatbot.workflow.engine;

/**
 * Abstraction for sleeping/waiting. Allows tests to provide a no-op implementation
 * to avoid actual delays during test execution.
 */
public interface Sleeper {

    /**
     * Sleep for the specified number of milliseconds.
     *
     * @param millis the time to sleep in milliseconds
     * @throws InterruptedException if the sleep is interrupted
     */
    void sleep(long millis) throws InterruptedException;
}
