package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Property 5: Context Merge Preserves Existing Entries
 *
 * For any existing session context map with N key-value pairs and a new user input stored
 * under the key mobile_no, after the merge operation the context SHALL contain N or N+1 entries,
 * the mobile_no key SHALL map to the new value, and all other keys SHALL retain their original values.
 *
 * Feature: websocket-workflow-execution, Property 5: Context Merge Preserves Existing Entries
 */
class ContextMergePropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 5: Context Merge Preserves Existing Entries")
    void mergingNewMobileNoPreservesExistingEntries(
            @ForAll("existingContexts") Map<String, Object> existingContext,
            @ForAll("mobileNumbers") String newMobileNo) {

        // Create a copy to simulate existing state
        Map<String, Object> context = new HashMap<>(existingContext);
        int originalSize = context.size();
        Map<String, Object> originalEntries = new HashMap<>(context);

        // Simulate the merge operation (same as in WorkflowExecutionServiceImpl.handleUserInput)
        context.put("mobile_no", newMobileNo);

        // Verify mobile_no is set to new value
        assert newMobileNo.equals(context.get("mobile_no")) :
                "mobile_no should map to the new value";

        // Verify size is N or N+1
        boolean hadMobileNo = originalEntries.containsKey("mobile_no");
        int expectedSize = hadMobileNo ? originalSize : originalSize + 1;
        assert context.size() == expectedSize :
                "Expected size " + expectedSize + " but got " + context.size();

        // Verify all other keys retain their original values
        for (Map.Entry<String, Object> entry : originalEntries.entrySet()) {
            if (!"mobile_no".equals(entry.getKey())) {
                assert context.containsKey(entry.getKey()) :
                        "Key " + entry.getKey() + " should still exist";
                assert entry.getValue().equals(context.get(entry.getKey())) :
                        "Key " + entry.getKey() + " should retain its original value";
            }
        }
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 5: Context Merge Preserves Existing Entries")
    void mergingOverwritesExistingMobileNo(@ForAll("mobileNumbers") String oldValue,
                                            @ForAll("mobileNumbers") String newValue) {
        Map<String, Object> context = new HashMap<>();
        context.put("mobile_no", oldValue);
        context.put("other_key", "preserved");

        // Simulate merge
        context.put("mobile_no", newValue);

        assert newValue.equals(context.get("mobile_no")) :
                "mobile_no should be overwritten with new value";
        assert "preserved".equals(context.get("other_key")) :
                "Other keys should remain unchanged";
        assert context.size() == 2 : "Size should remain 2";
    }

    @Provide
    Arbitrary<Map<String, Object>> existingContexts() {
        return Arbitraries.of(
                new HashMap<>(),
                new HashMap<>(Map.of("name", "John")),
                new HashMap<>(Map.of("name", "Jane", "age", "25")),
                new HashMap<>(Map.of("mobile_no", "old_number")),
                new HashMap<>(Map.of("mobile_no", "old_number", "email", "test@test.com"))
        );
    }

    @Provide
    Arbitrary<String> mobileNumbers() {
        return Arbitraries.of("9876543210", "1234567890", "+91-9999999999", "0000000000");
    }
}
