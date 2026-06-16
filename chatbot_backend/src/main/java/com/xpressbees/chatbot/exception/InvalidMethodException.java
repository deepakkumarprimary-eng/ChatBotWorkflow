package com.xpressbees.chatbot.exception;

public class InvalidMethodException extends RuntimeException {

    private final String method;

    public InvalidMethodException(String method) {
        super("method must be one of: GET, POST, PUT, DELETE");
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
