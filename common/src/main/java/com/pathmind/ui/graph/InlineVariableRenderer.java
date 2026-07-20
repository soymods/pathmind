package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.pathmind.ui.theme.UITheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class InlineVariableRenderer {

    private InlineVariableRenderer() {
    }

    static InlineVariableRender buildInlineVariableRender(String rawText, Set<String> variableNames, int baseColor, int highlightColor) {
        return buildInlineVariableRender(rawText, variableNames, baseColor, highlightColor, false);
    }

    static InlineVariableRender buildInlineVariableRender(String rawText, Set<String> variableNames, int baseColor, int highlightColor,
                                                          boolean allowRelativeMarker) {
        if (rawText == null || rawText.isEmpty()) {
            return new InlineVariableRender(rawText == null ? "" : rawText, Collections.emptyList(), new int[0]);
        }
        List<InlineTextSegment> segments = new ArrayList<>();
        List<Integer> removedPositions = new ArrayList<>();
        StringBuilder displayBuilder = new StringBuilder();
        int operatorColor = UITheme.DROP_ACCENT_GREEN;
        int relativeColor = UITheme.TEXT_RELATIVE_MARKER;
        int cursor = 0;
        int plainStart = 0;
        while (cursor < rawText.length()) {
            char current = rawText.charAt(cursor);
            if (current == '$') {
                if (cursor > plainStart) {
                    appendStyledPlainSegment(rawText.substring(plainStart, cursor), baseColor, segments, displayBuilder);
                }
                VariableReferenceMatch match = findInlineVariableReference(rawText, cursor, variableNames);
                if (match != null) {
                    removedPositions.add(cursor);
                    segments.add(new InlineTextSegment(match.name, highlightColor));
                    displayBuilder.append(match.name);
                    cursor = match.endIndex;
                } else {
                    segments.add(new InlineTextSegment("$", baseColor));
                    displayBuilder.append("$");
                    cursor++;
                }
                plainStart = cursor;
                continue;
            }
            if (isInlineArithmeticOperatorAt(rawText, cursor)) {
                if (cursor > plainStart) {
                    appendStyledPlainSegment(rawText.substring(plainStart, cursor), baseColor, segments, displayBuilder);
                }
                segments.add(new InlineTextSegment(Character.toString(current), operatorColor));
                displayBuilder.append(current);
                cursor++;
                plainStart = cursor;
                continue;
            }
            if (allowRelativeMarker && current == '~') {
                if (cursor > plainStart) {
                    appendStyledPlainSegment(rawText.substring(plainStart, cursor), baseColor, segments, displayBuilder);
                }
                segments.add(new InlineTextSegment("~", relativeColor));
                displayBuilder.append('~');
                cursor++;
                plainStart = cursor;
                continue;
            }
            cursor++;
        }
        if (plainStart < rawText.length()) {
            appendStyledPlainSegment(rawText.substring(plainStart), baseColor, segments, displayBuilder);
        }
        int[] removed = new int[removedPositions.size()];
        for (int i = 0; i < removedPositions.size(); i++) {
            removed[i] = removedPositions.get(i);
        }
        return new InlineVariableRender(displayBuilder.toString(), segments, removed);
    }

    static boolean shouldBuildInlineExpressionRender(boolean compactViewportMode, String rawText,
                                                     Set<String> variableNames, boolean allowRelativeMarker) {
        if (compactViewportMode) {
            return false;
        }
        if (rawText == null || rawText.isEmpty()) {
            return false;
        }
        if (containsInlineArithmeticOperator(rawText)) {
            return true;
        }
        if (allowRelativeMarker && rawText.indexOf('~') >= 0) {
            return true;
        }
        return variableNames != null && !variableNames.isEmpty() && rawText.indexOf('$') >= 0;
    }

    private static boolean isInlineVariableChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    private static boolean containsInlineArithmeticOperator(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isInlineArithmeticOperatorAt(text, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInlineArithmeticOperator(char character) {
        return character == '+' || character == '-' || character == '*' || character == '/' || character == '^';
    }

    static boolean isInlineArithmeticOperatorAt(String text, int index) {
        if (text == null || index <= 0 || index >= text.length()) {
            return false;
        }
        char character = text.charAt(index);
        if (character != '+' && character != '-' && character != '*'
            && character != '/' && character != '^') {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void appendStyledPlainSegment(String text, int baseColor, List<InlineTextSegment> segments,
                                                 StringBuilder displayBuilder) {
        if (text == null || text.isEmpty()) {
            return;
        }
        segments.add(new InlineTextSegment(text, baseColor));
        displayBuilder.append(text);
    }

    private static VariableReferenceMatch findInlineVariableReference(String rawText, int variableIndex,
                                                                      Set<String> variableNames) {
        if (rawText == null || variableIndex < 0 || variableIndex >= rawText.length() || rawText.charAt(variableIndex) != '$'
            || variableNames == null || variableNames.isEmpty()) {
            return null;
        }
        int nameStart = variableIndex + 1;
        if (nameStart >= rawText.length()) {
            return null;
        }
        VariableReferenceMatch bestMatch = null;
        for (String variableName : variableNames) {
            if (variableName == null || variableName.isEmpty()) {
                continue;
            }
            if (!rawText.regionMatches(nameStart, variableName, 0, variableName.length())) {
                continue;
            }
            int endIndex = nameStart + variableName.length();
            if (endIndex < rawText.length()) {
                char boundary = rawText.charAt(endIndex);
                if (!Character.isWhitespace(boundary) && !isInlineArithmeticOperator(boundary)) {
                    continue;
                }
            }
            if (bestMatch == null || variableName.length() > bestMatch.name.length()) {
                bestMatch = new VariableReferenceMatch(variableName, endIndex);
            }
        }
        return bestMatch;
    }

    static boolean isSingleKnownInlineVariableReference(String rawText, Set<String> variableNames) {
        if (rawText == null || variableNames == null || variableNames.isEmpty()) {
            return false;
        }
        String trimmed = rawText.trim();
        if (!trimmed.equals(rawText) || !trimmed.startsWith("$")) {
            return false;
        }
        VariableReferenceMatch match = findInlineVariableReference(trimmed, 0, variableNames);
        return match != null && match.endIndex == trimmed.length();
    }

    static final class InlineVariableRender {
        final String displayText;
        private final List<InlineTextSegment> segments;
        private final int[] removedVariablePrefixPositions;

        private InlineVariableRender(String displayText, List<InlineTextSegment> segments, int[] removedVariablePrefixPositions) {
            this.displayText = displayText == null ? "" : displayText;
            this.segments = segments == null ? Collections.emptyList() : segments;
            this.removedVariablePrefixPositions = removedVariablePrefixPositions == null ? new int[0] : removedVariablePrefixPositions;
        }

        int toDisplayIndex(int rawIndex) {
            if (rawIndex <= 0) {
                return 0;
            }
            int removed = 0;
            for (int pos : removedVariablePrefixPositions) {
                if (pos < rawIndex) {
                    removed++;
                } else {
                    break;
                }
            }
            return rawIndex - removed;
        }

        void draw(GuiGraphics context, Font renderer, int x, int y) {
            int cursorX = x;
            for (InlineTextSegment segment : segments) {
                if (segment == null || segment.text == null || segment.text.isEmpty()) {
                    continue;
                }
                context.drawString(renderer, Component.literal(segment.text), cursorX, y, segment.color, false);
                cursorX += renderer.width(segment.text);
            }
        }
    }

    private static final class InlineTextSegment {
        private final String text;
        private final int color;

        private InlineTextSegment(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
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
