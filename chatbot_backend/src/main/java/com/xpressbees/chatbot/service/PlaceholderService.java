package com.xpressbees.chatbot.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlaceholderService {

    public String resolve(String template, Map<String, Object> context) {
        if (template == null || context == null) {
            return template;
        }
        String result = template;
        if (context.containsKey("mobile_no")) {
            result = result.replace("<mobile_no>", String.valueOf(context.get("mobile_no")));
        }
        return result;
    }
}
