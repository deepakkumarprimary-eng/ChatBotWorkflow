package com.xpressbees.chatbot.exception;

public class ApiConfigNotFoundException extends RuntimeException {

    private final Long id;

    public ApiConfigNotFoundException(Long id) {
        super("ApiConfig not found with id: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
