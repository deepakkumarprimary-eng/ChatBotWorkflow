package com.chatbot.workflow.engine;

import org.springframework.stereotype.Component;

/**
 * Default Sleeper implementation that delegates to Thread.sleep().
 */
@Component
public class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
