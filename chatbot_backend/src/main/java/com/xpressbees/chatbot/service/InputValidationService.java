package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ValidationResult;
import java.util.Map;

public interface InputValidationService {
    ValidationResult validate(String input, Map<String, Object> validationConfig);
}
