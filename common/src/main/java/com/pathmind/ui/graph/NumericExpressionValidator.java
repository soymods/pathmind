package com.pathmind.ui.graph;

import java.util.Collections;
import java.util.Set;

final class NumericExpressionValidator {

    private final String input;
    private final Set<String> variableNames;
    private final boolean allowDecimal;
    private int index;
    private boolean usesDecimal;

    private NumericExpressionValidator(String input, Set<String> variableNames, boolean allowDecimal) {
        this.input = input == null ? "" : input;
        this.variableNames = variableNames == null ? Collections.emptySet() : variableNames;
        this.allowDecimal = allowDecimal;
    }

    static boolean isValid(String value, Set<String> variableNames, boolean allowDecimal, boolean requireCoordinateValid) {
        if (value == null) {
            return false;
        }
        NumericExpressionValidator validator = new NumericExpressionValidator(value, variableNames, allowDecimal);
        if (!validator.parse()) {
            return false;
        }
        if (requireCoordinateValid && !validator.isCoordinateSafeIntegerExpression()) {
            return false;
        }
        return true;
    }

    private boolean parse() {
        skipWhitespace();
        if (!parseExpression()) {
            return false;
        }
        skipWhitespace();
        return index == input.length();
    }

    private boolean isCoordinateSafeIntegerExpression() {
        return !usesDecimal;
    }

    private boolean parseExpression() {
        if (!parseTerm()) {
            return false;
        }
        while (true) {
            skipWhitespace();
            if (!consume('+') && !consume('-')) {
                return true;
            }
            skipWhitespace();
            if (!parseTerm()) {
                return false;
            }
        }
    }

    private boolean parseTerm() {
        if (!parsePower()) {
            return false;
        }
        while (true) {
            skipWhitespace();
            if (!consume('*') && !consume('/')) {
                return true;
            }
            skipWhitespace();
            if (!parsePower()) {
                return false;
            }
        }
    }

    private boolean parsePower() {
        if (!parseFactor()) {
            return false;
        }
        skipWhitespace();
        if (!consume('^')) {
            return true;
        }
        skipWhitespace();
        return parsePower();
    }

    private boolean parseFactor() {
        skipWhitespace();
        if (consume('+')) {
            skipWhitespace();
            return parseFactor();
        }
        if (peekIsNegativeNumberStart()) {
            index++;
            skipWhitespace();
            return parseNumber(true);
        }
        if (peek() == '$') {
            VariableReferenceMatch match = matchVariableAt(index);
            if (match == null) {
                return false;
            }
            index = match.endIndex;
            return true;
        }
        return parseNumber(false);
    }

    private boolean parseNumber(boolean negative) {
        int start = index;
        boolean sawDigit = false;
        boolean sawDecimal = false;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (Character.isDigit(current)) {
                sawDigit = true;
                index++;
                continue;
            }
            if (current == '.') {
                if (!allowDecimal || sawDecimal) {
                    break;
                }
                sawDecimal = true;
                usesDecimal = true;
                index++;
                continue;
            }
            break;
        }
        if (!sawDigit) {
            index = start;
            return false;
        }
        if (negative) {
            usesDecimal |= sawDecimal;
        }
        return true;
    }

    private VariableReferenceMatch matchVariableAt(int variableIndex) {
        if (variableIndex < 0 || variableIndex >= input.length() || input.charAt(variableIndex) != '$') {
            return null;
        }
        int nameStart = variableIndex + 1;
        VariableReferenceMatch bestMatch = null;
        for (String variableName : variableNames) {
            if (variableName == null || variableName.isEmpty()) {
                continue;
            }
            if (!input.regionMatches(nameStart, variableName, 0, variableName.length())) {
                continue;
            }
            int endIndex = nameStart + variableName.length();
            if (endIndex < input.length()) {
                char boundary = input.charAt(endIndex);
                if (!Character.isWhitespace(boundary) && !isOperator(boundary)) {
                    continue;
                }
            }
            if (bestMatch == null || variableName.length() > bestMatch.name.length()) {
                bestMatch = new VariableReferenceMatch(variableName, endIndex);
            }
        }
        return bestMatch;
    }

    private boolean peekIsNegativeNumberStart() {
        if (peek() != '-') {
            return false;
        }
        int lookahead = index + 1;
        while (lookahead < input.length() && Character.isWhitespace(input.charAt(lookahead))) {
            lookahead++;
        }
        if (lookahead >= input.length()) {
            return false;
        }
        char next = input.charAt(lookahead);
        return Character.isDigit(next) || (allowDecimal && next == '.');
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private boolean consume(char expected) {
        if (peek() != expected) {
            return false;
        }
        index++;
        return true;
    }

    private char peek() {
        return index < input.length() ? input.charAt(index) : '\0';
    }

    private boolean isOperator(char character) {
        return character == '+' || character == '-' || character == '*' || character == '/' || character == '^';
    }

    private static final class VariableReferenceMatch {
        private final String name;
        private final int endIndex;

        private VariableReferenceMatch(String name, int endIndex) {
            this.name = name;
            this.endIndex = endIndex;
        }
    }
}
