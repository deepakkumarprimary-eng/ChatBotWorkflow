package com.xpressbees.chatbot.config;

import com.xpressbees.chatbot.service.WorkflowExecutionService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngineHealthIndicator implements HealthIndicator {

    private final ApplicationContext applicationContext;

    public WorkflowEngineHealthIndicator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Health health() {
        try {
            applicationContext.getBean(WorkflowExecutionService.class);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
