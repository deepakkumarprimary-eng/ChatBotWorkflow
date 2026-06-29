package com.xpressbees.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * A rejection policy that executes the task on the caller thread (back-pressure)
 * and logs a WARN when thread pool saturation occurs.
 */
public class LoggingCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallerRunsPolicy.class);

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.warn("WebSocket thread pool saturated! active={}, queueSize={}, executing on caller thread",
                executor.getActiveCount(), executor.getQueue().size());
        super.rejectedExecution(r, executor);
    }
}
