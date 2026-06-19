package com.xpressbees.chatbot.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConditionEvaluator {

    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty() || context == null) return false;

        // Split on " or " boundaries first (or has lower precedence)
        String[] orGroups = expression.split(" or ");

        for (String orGroup : orGroups) {
            // Split each OR group on " and " boundaries (and has higher precedence)
            String[] andConditions = orGroup.split(" and ");

            boolean allTrue = true;
            for (String condition : andConditions) {
                if (!evaluateSimple(condition.trim(), context)) {
                    allTrue = false;
                    break;
                }
            }

            // Overall expression is true if ANY OR group is fully true
            if (allTrue) {
                return true;
            }
        }

        return false;
    }

    private boolean evaluateSimple(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) return false;

        String[] tokens = expression.trim().split("\\s+");
        if (tokens.length != 3) return false;

        String variable = tokens[0];
        String operator = tokens[1];
        String literal = tokens[2];

        if (!context.containsKey(variable)) return false;

        String contextValue = String.valueOf(context.get(variable));

        return switch (operator) {
            case "==" -> contextValue.equals(literal);
            case "!=" -> !contextValue.equals(literal);
            case "<", ">", "<=", ">=" -> compareNumeric(contextValue, literal, operator);
            default -> false;
        };
    }

    private boolean compareNumeric(String left, String right, String operator) {
        try {
            double leftNum = Double.parseDouble(left);
            double rightNum = Double.parseDouble(right);
            return switch (operator) {
                case "<" -> leftNum < rightNum;
                case ">" -> leftNum > rightNum;
                case "<=" -> leftNum <= rightNum;
                case ">=" -> leftNum >= rightNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
