package com.xpressbees.chatbot.exception;

public class DuplicateApiConfigNameException extends RuntimeException {

    private final String name;

    public DuplicateApiConfigNameException(String name) {
        super("ApiConfig with name '" + name + "' already exists");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
