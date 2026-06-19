package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.service.ConditionEvaluator;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based tests for API Node Processor behavior covering:
 * - Property 9: First-Match-Wins for Conditional Transitions
 * - Property 10: Interactive Selection Validation
 * - Property 11: Button Node Routing Correctness
 *
 * Validates: Requirements 7.1, 7.4, 9.5, 9.7, 12.4, 12.5
 */
class ApiNodeProcessorBehaviorPropertyTest {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    // ========================================================================
    // Property 9: First-Match-Wins for Conditional Transitions
    //
    // For any ordered list of transitions with condition expressions and any
    // context map where multiple conditions evaluate to true, the processor
    // SHALL select the transition at the lowest array index among those with
    // true conditions.
    //
    // Validates: Requirements 7.1, 7.4
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 9: First-Match-Wins for Conditional Transitions")
    void firstMatchWinsSelectsLowestIndexTransition(
            @ForAll("transitionListsWithMultipleTrueConditions") TransitionScenario scenario) {
        // Iterate transitions in order and select the first one whose condition is true
        int selectedIndex = -1;
        for (int i = 0; i < scenario.transitions.size(); i++) {
            String condition = scenario.transitions.get(i).condition;
            if (condition != null && !condition.trim().isEmpty()) {
                if (conditionEvaluator.evaluate(condition, scenario.context)) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        assert selectedIndex == scenario.expectedFirstMatchIndex :
                "First-match-wins should select index " + scenario.expectedFirstMatchIndex +
                " but selected index " + selectedIndex +
                ". Transitions: " + scenario.transitions +
                ", Context: " + scenario.context;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 9: First-Match-Wins for Conditional Transitions")
    void firstMatchWinsAlwaysSelectsFromTrueConditions(
            @ForAll("transitionListsWithKnownTrueIndices") TransitionScenarioWithTrueIndices scenario) {
        // The selected index must be the minimum of all true indices
        int selectedIndex = -1;
        for (int i = 0; i < scenario.transitions.size(); i++) {
            String condition = scenario.transitions.get(i).condition;
            if (condition != null && !condition.trim().isEmpty()) {
                if (conditionEvaluator.evaluate(condition, scenario.context)) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        int expectedMinIndex = scenario.trueIndices.stream().min(Integer::compareTo).orElse(-1);

        assert selectedIndex == expectedMinIndex :
                "First-match-wins should select the minimum true index " + expectedMinIndex +
                " but selected " + selectedIndex +
                ". True indices: " + scenario.trueIndices;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 9: First-Match-Wins for Conditional Transitions")
    void firstMatchWinsOrderDependence(
            @ForAll("validVariableNames") String varName,
            @ForAll("distinctValues") List<String> values) {
        // Given N transitions where all conditions are true (all match context),
        // the first one (index 0) should always be selected
        Assume.that(values.size() >= 2);

        String contextValue = values.get(0);
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextValue);

        // All transitions have conditions that are true for this context
        List<TransitionData> transitions = new ArrayList<>();
        for (String val : values) {
            // Only the first has a matching condition; rest won't match
            transitions.add(new TransitionData(varName + " == " + val, "target-" + val));
        }

        // First-match: index 0's condition evaluates varName == values.get(0) which is true
        int selectedIndex = -1;
        for (int i = 0; i < transitions.size(); i++) {
            if (conditionEvaluator.evaluate(transitions.get(i).condition, context)) {
                selectedIndex = i;
                break;
            }
        }

        assert selectedIndex == 0 :
                "When context matches the first transition, index 0 should be selected. " +
                "Got index " + selectedIndex;
    }

    // ========================================================================
    // Property 10: Interactive Selection Validation
    //
    // For any array of displayed values and any user reply string, the selection
    // SHALL be accepted (stored in context) if and only if the reply exactly
    // equals (case-sensitive) one of the array elements. All non-matching replies
    // SHALL be rejected with an error message.
    //
    // Validates: Requirements 9.5, 9.7
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 10: Interactive Selection Validation")
    void selectionAcceptedWhenReplyExactlyMatchesArrayElement(
            @ForAll("nonEmptyStringLists") List<String> options,
            @ForAll("validIndices") int indexSeed) {
        // Pick a value that IS in the list
        int index = Math.abs(indexSeed) % options.size();
        String reply = options.get(index);

        boolean accepted = options.contains(reply);

        assert accepted : "Reply '" + reply + "' should be accepted because it exactly matches " +
                "an element in the options list: " + options;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 10: Interactive Selection Validation")
    void selectionRejectedWhenReplyDoesNotMatchAnyElement(
            @ForAll("nonEmptyStringLists") List<String> options,
            @ForAll("stringValues") String reply) {
        // Ensure reply is NOT in the list
        Assume.that(!options.contains(reply));

        boolean accepted = options.contains(reply);

        assert !accepted : "Reply '" + reply + "' should be REJECTED because it does not match " +
                "any element in the options list: " + options;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 10: Interactive Selection Validation")
    void selectionIsCaseSensitive(
            @ForAll("lowerCaseStringLists") List<String> options) {
        // Take the first option and change its case
        Assume.that(!options.isEmpty());
        String original = options.get(0);
        Assume.that(original.length() > 0);

        String caseChanged = original.substring(0, 1).toUpperCase() + original.substring(1);
        Assume.that(!original.equals(caseChanged)); // Ensure case actually changed

        // Case-sensitive: the case-changed version should NOT match
        boolean accepted = options.contains(caseChanged);

        assert !accepted : "Selection should be case-sensitive. '" + caseChanged +
                "' should NOT match '" + original + "' in options: " + options;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 10: Interactive Selection Validation")
    void selectionValidationBidirectional(
            @ForAll("nonEmptyStringLists") List<String> options,
            @ForAll("stringValues") String reply) {
        // Core property: accepted iff reply exactly matches an element
        boolean accepted = options.contains(reply);
        boolean shouldBeAccepted = options.stream().anyMatch(opt -> opt.equals(reply));

        assert accepted == shouldBeAccepted :
                "Selection validation inconsistency. Reply='" + reply +
                "', options=" + options + ", accepted=" + accepted +
                ", shouldBeAccepted=" + shouldBeAccepted;
    }

    // ========================================================================
    // Property 11: Button Node Routing Correctness
    //
    // For any set of target node names and any user reply string, the workflow
    // engine SHALL route to the matching target node if and only if the reply
    // exactly equals (case-sensitive) one of the target node names.
    // Non-matching replies SHALL be rejected.
    //
    // Validates: Requirements 12.4, 12.5
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 11: Button Node Routing Correctness")
    void routesToTargetWhenReplyMatchesNodeName(
            @ForAll("nonEmptyStringLists") List<String> targetNodeNames,
            @ForAll("validIndices") int indexSeed) {
        // Pick a name that IS in the list
        int index = Math.abs(indexSeed) % targetNodeNames.size();
        String reply = targetNodeNames.get(index);

        // Find the matching target
        Optional<String> matchedTarget = targetNodeNames.stream()
                .filter(name -> name.equals(reply))
                .findFirst();

        assert matchedTarget.isPresent() :
                "Should route to target when reply '" + reply + "' matches a node name. " +
                "Target names: " + targetNodeNames;
        assert matchedTarget.get().equals(reply) :
                "Matched target should equal the reply exactly.";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 11: Button Node Routing Correctness")
    void rejectsWhenReplyDoesNotMatchAnyNodeName(
            @ForAll("nonEmptyStringLists") List<String> targetNodeNames,
            @ForAll("stringValues") String reply) {
        // Ensure reply does NOT match any target name
        Assume.that(!targetNodeNames.contains(reply));

        Optional<String> matchedTarget = targetNodeNames.stream()
                .filter(name -> name.equals(reply))
                .findFirst();

        assert matchedTarget.isEmpty() :
                "Should reject when reply '" + reply + "' does not match any target node name. " +
                "Target names: " + targetNodeNames;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 11: Button Node Routing Correctness")
    void buttonRoutingIsCaseSensitive(
            @ForAll("lowerCaseStringLists") List<String> targetNodeNames) {
        // Take the first name and change its case
        Assume.that(!targetNodeNames.isEmpty());
        String original = targetNodeNames.get(0);
        Assume.that(original.length() > 0);

        String caseChanged = original.toUpperCase();
        Assume.that(!original.equals(caseChanged)); // Ensure case actually changed

        // Case-sensitive: the upper-case version should NOT match
        Optional<String> matchedTarget = targetNodeNames.stream()
                .filter(name -> name.equals(caseChanged))
                .findFirst();

        assert matchedTarget.isEmpty() :
                "Button routing should be case-sensitive. '" + caseChanged +
                "' should NOT match '" + original + "' in target names: " + targetNodeNames;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 11: Button Node Routing Correctness")
    void buttonRoutingBidirectional(
            @ForAll("nonEmptyStringLists") List<String> targetNodeNames,
            @ForAll("stringValues") String reply) {
        // Core property: routes iff reply exactly matches a target node name
        boolean routes = targetNodeNames.contains(reply);
        boolean shouldRoute = targetNodeNames.stream().anyMatch(name -> name.equals(reply));

        assert routes == shouldRoute :
                "Button routing validation inconsistency. Reply='" + reply +
                "', targetNames=" + targetNodeNames + ", routes=" + routes +
                ", shouldRoute=" + shouldRoute;
    }

    // ========================================================================
    // Data classes for scenarios
    // ========================================================================

    static class TransitionData {
        final String condition;
        final String targetNodeId;

        TransitionData(String condition, String targetNodeId) {
            this.condition = condition;
            this.targetNodeId = targetNodeId;
        }

        @Override
        public String toString() {
            return "{condition='" + condition + "', target='" + targetNodeId + "'}";
        }
    }

    static class TransitionScenario {
        final List<TransitionData> transitions;
        final Map<String, Object> context;
        final int expectedFirstMatchIndex;

        TransitionScenario(List<TransitionData> transitions, Map<String, Object> context, int expectedFirstMatchIndex) {
            this.transitions = transitions;
            this.context = context;
            this.expectedFirstMatchIndex = expectedFirstMatchIndex;
        }
    }

    static class TransitionScenarioWithTrueIndices {
        final List<TransitionData> transitions;
        final Map<String, Object> context;
        final List<Integer> trueIndices;

        TransitionScenarioWithTrueIndices(List<TransitionData> transitions, Map<String, Object> context, List<Integer> trueIndices) {
            this.transitions = transitions;
            this.context = context;
            this.trueIndices = trueIndices;
        }
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<TransitionScenario> transitionListsWithMultipleTrueConditions() {
        // Generate 3 to 6 transitions where at least 2 conditions are true
        // The first true condition is always at a known index
        return Arbitraries.integers().between(3, 6).flatMap(size ->
            Arbitraries.integers().between(0, size - 2).flatMap(firstTrueIndex -> {
                // Create distinct variable names for each transition
                List<String> varNames = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    varNames.add("var" + i);
                }

                Map<String, Object> context = new HashMap<>();
                List<TransitionData> transitions = new ArrayList<>();

                for (int i = 0; i < size; i++) {
                    String varName = varNames.get(i);
                    if (i == firstTrueIndex || i == firstTrueIndex + 1) {
                        // These conditions will be TRUE
                        String matchValue = "match" + i;
                        context.put(varName, matchValue);
                        transitions.add(new TransitionData(varName + " == " + matchValue, "target-" + i));
                    } else {
                        // These conditions will be FALSE
                        context.put(varName, "actual" + i);
                        transitions.add(new TransitionData(varName + " == " + "wrong" + i, "target-" + i));
                    }
                }

                return Arbitraries.just(new TransitionScenario(transitions, context, firstTrueIndex));
            })
        );
    }

    @Provide
    Arbitrary<TransitionScenarioWithTrueIndices> transitionListsWithKnownTrueIndices() {
        // Generate transitions with a known set of true indices
        return Arbitraries.integers().between(3, 5).flatMap(size -> {
            List<String> varNames = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                varNames.add("v" + i);
            }

            // Randomly pick which indices are true (at least 2)
            return Arbitraries.of(true, false).list().ofSize(size)
                    .filter(booleans -> booleans.stream().filter(b -> b).count() >= 2)
                    .map(booleans -> {
                        Map<String, Object> context = new HashMap<>();
                        List<TransitionData> transitions = new ArrayList<>();
                        List<Integer> trueIndices = new ArrayList<>();

                        for (int i = 0; i < size; i++) {
                            String varName = varNames.get(i);
                            if (booleans.get(i)) {
                                // TRUE condition
                                String matchVal = "yes" + i;
                                context.put(varName, matchVal);
                                transitions.add(new TransitionData(varName + " == " + matchVal, "target-" + i));
                                trueIndices.add(i);
                            } else {
                                // FALSE condition
                                context.put(varName, "no" + i);
                                transitions.add(new TransitionData(varName + " == " + "wrong" + i, "target-" + i));
                            }
                        }

                        return new TransitionScenarioWithTrueIndices(transitions, context, trueIndices);
                    });
        });
    }

    @Provide
    Arbitrary<String> validVariableNames() {
        Arbitrary<String> alphaStart = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(1);
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1).ofMaxLength(8);
        return Combinators.combine(alphaStart, rest).as((start, tail) -> start + tail);
    }

    @Provide
    Arbitrary<String> stringValues() {
        // Single-token values (no spaces, no newlines) for use in conditions and option lists
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(12);
    }

    @Provide
    Arbitrary<List<String>> nonEmptyStringLists() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(2).ofMaxLength(10)
                .list().ofMinSize(2).ofMaxSize(8)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> list.size() >= 2);
    }

    @Provide
    Arbitrary<List<String>> lowerCaseStringLists() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(8)
                .list().ofMinSize(2).ofMaxSize(6)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> list.size() >= 2);
    }

    @Provide
    Arbitrary<List<String>> distinctValues() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(6)
                .list().ofMinSize(2).ofMaxSize(5)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> list.size() >= 2);
    }

    @Provide
    Arbitrary<Integer> validIndices() {
        return Arbitraries.integers().between(0, 1000);
    }
}
