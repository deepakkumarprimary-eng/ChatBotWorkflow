package com.chatbot.workflow.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for Condition states. Evaluates boolean expressions with comparison
 * operators (==, !=, <, >, <=, >=) and logical operators (AND, OR, NOT).
 * Resolves context variable references from the execution context.
 * Undefined variables are treated as null.
 */
@Component
public class ConditionStateProcessor implements StateProcessor {

    @Override
    public StateType getType() {
        return StateType.CONDITION;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        Map<String, Object> config = state.getConfig();
        if (config == null || !config.containsKey("expression")) {
            return StateProcessorResult.builder()
                    .outcome(StateOutcome.FAILED)
                    .errorMessage("Condition state missing 'expression' in config")
                    .nextTransitionCondition("error")
                    .build();
        }

        String expression = String.valueOf(config.get("expression"));
        if (expression == null || expression.trim().isEmpty()) {
            return StateProcessorResult.builder()
                    .outcome(StateOutcome.FAILED)
                    .errorMessage("Condition expression is empty")
                    .nextTransitionCondition("error")
                    .build();
        }

        try {
            boolean result = evaluate(expression, context);
            return StateProcessorResult.builder()
                    .outcome(StateOutcome.SUCCEEDED)
                    .nextTransitionCondition(result ? "true" : "false")
                    .build();
        } catch (ExpressionParseException e) {
            return StateProcessorResult.builder()
                    .outcome(StateOutcome.FAILED)
                    .errorMessage("Expression evaluation error: " + e.getMessage())
                    .nextTransitionCondition("error")
                    .build();
        }
    }

    /**
     * Evaluates a boolean expression string against the execution context.
     */
    boolean evaluate(String expression, ExecutionContext context) {
        List<Token> tokens = tokenize(expression);
        Parser parser = new Parser(tokens, context);
        Object result = parser.parseExpression();
        if (parser.pos < parser.tokens.size()) {
            throw new ExpressionParseException("Unexpected token: " + parser.tokens.get(parser.pos));
        }
        return toBool(result);
    }

    // ========== Tokenizer ==========

    enum TokenType {
        NUMBER, STRING, IDENTIFIER, OPERATOR, LPAREN, RPAREN, AND, OR, NOT, TRUE, FALSE, NULL_LITERAL, EOF
    }

    static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Token(" + type + ", " + value + ")";
        }
    }

    List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expression.length();

        while (i < len) {
            char c = expression.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
                continue;
            }

            // String literal (double-quoted)
            if (c == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len && expression.charAt(i) != '"') {
                    if (expression.charAt(i) == '\\' && i + 1 < len) {
                        i++;
                        sb.append(expression.charAt(i));
                    } else {
                        sb.append(expression.charAt(i));
                    }
                    i++;
                }
                if (i >= len) {
                    throw new ExpressionParseException("Unterminated string literal");
                }
                i++; // skip closing quote
                tokens.add(new Token(TokenType.STRING, sb.toString()));
                continue;
            }

            // Comparison operators
            if (c == '=' && i + 1 < len && expression.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "=="));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < len && expression.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "!="));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < len && expression.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, "<="));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && expression.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OPERATOR, ">="));
                i += 2;
                continue;
            }
            if (c == '<') {
                tokens.add(new Token(TokenType.OPERATOR, "<"));
                i++;
                continue;
            }
            if (c == '>') {
                tokens.add(new Token(TokenType.OPERATOR, ">"));
                i++;
                continue;
            }

            // Numbers (integers and decimals)
            if (Character.isDigit(c) || (c == '-' && i + 1 < len && Character.isDigit(expression.charAt(i + 1))
                    && (tokens.isEmpty() || tokens.get(tokens.size() - 1).type == TokenType.OPERATOR
                    || tokens.get(tokens.size() - 1).type == TokenType.LPAREN
                    || tokens.get(tokens.size() - 1).type == TokenType.AND
                    || tokens.get(tokens.size() - 1).type == TokenType.OR
                    || tokens.get(tokens.size() - 1).type == TokenType.NOT))) {
                StringBuilder sb = new StringBuilder();
                if (c == '-') {
                    sb.append(c);
                    i++;
                }
                while (i < len && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    sb.append(expression.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, sb.toString()));
                continue;
            }

            // Identifiers and keywords (AND, OR, NOT, true, false, null)
            if (Character.isLetter(c) || c == '_') {
                StringBuilder sb = new StringBuilder();
                while (i < len && (Character.isLetterOrDigit(expression.charAt(i)) || expression.charAt(i) == '_')) {
                    sb.append(expression.charAt(i));
                    i++;
                }
                String word = sb.toString();
                switch (word) {
                    case "AND":
                        tokens.add(new Token(TokenType.AND, "AND"));
                        break;
                    case "OR":
                        tokens.add(new Token(TokenType.OR, "OR"));
                        break;
                    case "NOT":
                        tokens.add(new Token(TokenType.NOT, "NOT"));
                        break;
                    case "true":
                        tokens.add(new Token(TokenType.TRUE, "true"));
                        break;
                    case "false":
                        tokens.add(new Token(TokenType.FALSE, "false"));
                        break;
                    case "null":
                        tokens.add(new Token(TokenType.NULL_LITERAL, "null"));
                        break;
                    default:
                        tokens.add(new Token(TokenType.IDENTIFIER, word));
                        break;
                }
                continue;
            }

            throw new ExpressionParseException("Unexpected character: '" + c + "' at position " + i);
        }

        return tokens;
    }

    // ========== Recursive Descent Parser ==========

    /**
     * Grammar (precedence low to high):
     *   expression  → orExpr
     *   orExpr      → andExpr (OR andExpr)*
     *   andExpr     → notExpr (AND notExpr)*
     *   notExpr     → NOT notExpr | comparison
     *   comparison  → primary (compOp primary)?
     *   primary     → NUMBER | STRING | TRUE | FALSE | NULL | IDENTIFIER | '(' expression ')'
     */
    static class Parser {
        final List<Token> tokens;
        final ExecutionContext context;
        int pos;

        Parser(List<Token> tokens, ExecutionContext context) {
            this.tokens = tokens;
            this.context = context;
            this.pos = 0;
        }

        Token peek() {
            if (pos >= tokens.size()) return new Token(TokenType.EOF, "");
            return tokens.get(pos);
        }

        Token consume() {
            Token t = peek();
            pos++;
            return t;
        }

        Object parseExpression() {
            return parseOr();
        }

        Object parseOr() {
            Object left = parseAnd();
            while (peek().type == TokenType.OR) {
                consume();
                Object right = parseAnd();
                left = toBool(left) || toBool(right);
            }
            return left;
        }

        Object parseAnd() {
            Object left = parseNot();
            while (peek().type == TokenType.AND) {
                consume();
                Object right = parseNot();
                left = toBool(left) && toBool(right);
            }
            return left;
        }

        Object parseNot() {
            if (peek().type == TokenType.NOT) {
                consume();
                Object val = parseNot();
                return !toBool(val);
            }
            return parseComparison();
        }

        Object parseComparison() {
            Object left = parsePrimary();
            if (peek().type == TokenType.OPERATOR) {
                String op = consume().value;
                Object right = parsePrimary();
                return compare(left, op, right);
            }
            return left;
        }

        Object parsePrimary() {
            Token t = peek();
            switch (t.type) {
                case NUMBER:
                    consume();
                    return parseNumber(t.value);
                case STRING:
                    consume();
                    return t.value;
                case TRUE:
                    consume();
                    return Boolean.TRUE;
                case FALSE:
                    consume();
                    return Boolean.FALSE;
                case NULL_LITERAL:
                    consume();
                    return null;
                case IDENTIFIER:
                    consume();
                    return context.getVariable(t.value);
                case LPAREN:
                    consume();
                    Object val = parseExpression();
                    if (peek().type != TokenType.RPAREN) {
                        throw new ExpressionParseException("Expected ')' but found: " + peek());
                    }
                    consume();
                    return val;
                default:
                    throw new ExpressionParseException("Unexpected token: " + t);
            }
        }

        private Number parseNumber(String value) {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return Double.parseDouble(value);
            }
        }

        @SuppressWarnings("unchecked")
        private boolean compare(Object left, String op, Object right) {
            // Handle null comparisons
            if (left == null && right == null) {
                return "==".equals(op) || "<=".equals(op) || ">=".equals(op);
            }
            if (left == null || right == null) {
                if ("==".equals(op)) return false;
                if ("!=".equals(op)) return true;
                // For ordering comparisons with null, result is false
                return false;
            }

            // Both non-null: try numeric comparison
            Double leftNum = toDouble(left);
            Double rightNum = toDouble(right);

            if (leftNum != null && rightNum != null) {
                int cmp = Double.compare(leftNum, rightNum);
                return evalComparison(cmp, op);
            }

            // String comparison
            String leftStr = String.valueOf(left);
            String rightStr = String.valueOf(right);

            if ("==".equals(op)) return leftStr.equals(rightStr);
            if ("!=".equals(op)) return !leftStr.equals(rightStr);

            // For ordering operators on strings, use lexicographic comparison
            int cmp = leftStr.compareTo(rightStr);
            return evalComparison(cmp, op);
        }

        private boolean evalComparison(int cmp, String op) {
            switch (op) {
                case "==": return cmp == 0;
                case "!=": return cmp != 0;
                case "<":  return cmp < 0;
                case ">":  return cmp > 0;
                case "<=": return cmp <= 0;
                case ">=": return cmp >= 0;
                default: throw new ExpressionParseException("Unknown operator: " + op);
            }
        }

        private Double toDouble(Object value) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }

    // ========== Utilities ==========

    private static boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    static class ExpressionParseException extends RuntimeException {
        ExpressionParseException(String message) {
            super(message);
        }
    }
}
