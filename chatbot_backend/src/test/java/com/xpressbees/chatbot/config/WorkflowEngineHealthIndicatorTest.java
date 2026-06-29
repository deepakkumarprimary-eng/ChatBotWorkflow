package com.xpressbees.chatbot.config;

import com.xpressbees.chatbot.service.WorkflowExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WorkflowEngineHealthIndicator.
 * Validates: Requirements 1.5
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineHealthIndicatorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Reports UP when WorkflowExecutionService bean is present")
    void reportsUpWhenBeanIsPresent() {
        WorkflowEngineHealthIndicator indicator = new WorkflowEngineHealthIndicator(applicationContext);
        WorkflowExecutionService mockService = mock(WorkflowExecutionService.class);

        when(applicationContext.getBean(WorkflowExecutionService.class)).thenReturn(mockService);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("Reports DOWN when WorkflowExecutionService bean is missing")
    void reportsDownWhenBeanIsMissing() {
        WorkflowEngineHealthIndicator indicator = new WorkflowEngineHealthIndicator(applicationContext);

        when(applicationContext.getBean(WorkflowExecutionService.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                        WorkflowExecutionService.class));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
