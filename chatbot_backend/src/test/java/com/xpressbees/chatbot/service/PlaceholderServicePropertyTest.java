package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Property 1: Placeholder Substitution Correctness
 *
 * For any node name string containing the <mobile_no> token and for any session context map
 * containing a mobile_no key with a non-null value, the PlaceholderService.resolve() method SHALL
 * return the original string with all occurrences of <mobile_no> replaced by the context value;
 * and if the context does not contain the key, the token SHALL remain unchanged.
 *
 * Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness
 */
class PlaceholderServicePropertyTest {

    private final PlaceholderService placeholderService = new PlaceholderService();

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness")
    void replacesTokenWhenKeyExistsInContext(@ForAll("templatesWithPlaceholder") String template,
                                             @ForAll("mobileNumbers") String mobileNo) {
        Map<String, Object> context = new HashMap<>();
        context.put("mobile_no", mobileNo);

        String result = placeholderService.resolve(template, context);

        assert !result.contains("<mobile_no>") :
                "Result should not contain <mobile_no> when key exists. Got: " + result;
        assert result.contains(mobileNo) :
                "Result should contain the mobile number value. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness")
    void leavesTokenUnchangedWhenKeyAbsentFromContext(@ForAll("templatesWithPlaceholder") String template) {
        Map<String, Object> context = new HashMap<>();
        // No mobile_no key in context

        String result = placeholderService.resolve(template, context);

        assert result.contains("<mobile_no>") :
                "Result should still contain <mobile_no> when key is absent. Got: " + result;
        assert result.equals(template) :
                "Result should equal original template when key is absent";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness")
    void returnsTemplateUnchangedWhenNoPlaceholderPresent(@ForAll("templatesWithoutPlaceholder") String template,
                                                          @ForAll("mobileNumbers") String mobileNo) {
        Map<String, Object> context = new HashMap<>();
        context.put("mobile_no", mobileNo);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Result should equal original template when no placeholder is present";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness")
    void handlesNullTemplateGracefully() {
        Map<String, Object> context = Map.of("mobile_no", "1234567890");

        String result = placeholderService.resolve(null, context);

        assert result == null : "Should return null for null template";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 1: Placeholder Substitution Correctness")
    void handlesNullContextGracefully(@ForAll("templatesWithPlaceholder") String template) {
        String result = placeholderService.resolve(template, null);

        assert result.equals(template) : "Should return original template when context is null";
    }

    @Provide
    Arbitrary<String> templatesWithPlaceholder() {
        return Arbitraries.of(
                "Your number is <mobile_no>",
                "<mobile_no>",
                "Hello <mobile_no>, welcome!",
                "Call <mobile_no> or <mobile_no>",
                "Number: <mobile_no>."
        );
    }

    @Provide
    Arbitrary<String> templatesWithoutPlaceholder() {
        return Arbitraries.of(
                "Hello World",
                "Welcome to the chatbot",
                "Please wait",
                "",
                "No placeholders here"
        );
    }

    @Provide
    Arbitrary<String> mobileNumbers() {
        return Arbitraries.of(
                "9876543210",
                "1234567890",
                "+91-9999999999",
                "0000000000",
                "12345"
        );
    }
}
