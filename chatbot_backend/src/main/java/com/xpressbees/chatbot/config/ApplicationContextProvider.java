package com.xpressbees.chatbot.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Static holder for the Spring ApplicationContext.
 * Used by JPA entity listeners (which are not Spring-managed beans)
 * to obtain Spring beans such as EncryptionService.
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) {
        applicationContext = context;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (applicationContext == null) {
            throw new IllegalStateException(
                    "ApplicationContext has not been initialized. Ensure ApplicationContextProvider is registered as a Spring bean.");
        }
        return applicationContext.getBean(beanClass);
    }
}
