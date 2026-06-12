package com.pathmind.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary compatibility shim for migrating legacy ~variable text-box syntax to $variable.
 * Remove this class and its call sites once legacy preset support is no longer needed.
 */
public final class LegacyVariableSyntaxCompat {
    private LegacyVariableSyntaxCompat() {
    }

    public static String normalizeLegacyVariableSyntax(String value) {
        if (value == null || value.isEmpty() || value.indexOf('~') < 0) {
            return value;
        }

        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '~' && isLegacyVariableStart(value, i)) {
                normalized.append('$');
                continue;
            }
            normalized.append(current);
        }
        return normalized.toString();
    }

    public static List<String> normalizeLegacyVariableSyntax(List<String> values) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(normalizeLegacyVariableSyntax(value));
        }
        return normalized;
    }

    private static boolean isLegacyVariableStart(String value, int index) {
        int nextIndex = index + 1;
        if (nextIndex >= value.length() || !isVariableNameStart(value.charAt(nextIndex))) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        return !isVariableNameChar(value.charAt(index - 1));
    }

    private static boolean isVariableNameStart(char character) {
        return Character.isLetter(character) || character == '_';
    }

    private static boolean isVariableNameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }
}
