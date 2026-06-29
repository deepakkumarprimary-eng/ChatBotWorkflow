package com.xpressbees.chatbot.entity;

import com.xpressbees.chatbot.config.ApplicationContextProvider;
import com.xpressbees.chatbot.service.EncryptionService;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * JPA entity listener that transparently encrypts and decrypts
 * the username and password fields on ApiConfig entities.
 *
 * Uses ApplicationContextProvider to obtain the EncryptionService bean,
 * since JPA entity listeners are not Spring-managed.
 */
public class ApiConfigEntityListener {

    @PrePersist
    @PreUpdate
    public void encryptFields(ApiConfig entity) {
        EncryptionService encryptionService = getEncryptionService();

        String username = entity.getUsername();
        if (username != null) {
            entity.setUsername(encryptionService.encrypt(username));
        }

        String password = entity.getPassword();
        if (password != null) {
            entity.setPassword(encryptionService.encrypt(password));
        }
    }

    @PostLoad
    public void decryptFields(ApiConfig entity) {
        EncryptionService encryptionService = getEncryptionService();

        String username = entity.getUsername();
        if (username != null) {
            entity.setUsername(encryptionService.decrypt(username));
        }

        String password = entity.getPassword();
        if (password != null) {
            entity.setPassword(encryptionService.decrypt(password));
        }
    }

    private EncryptionService getEncryptionService() {
        return ApplicationContextProvider.getBean(EncryptionService.class);
    }
}
