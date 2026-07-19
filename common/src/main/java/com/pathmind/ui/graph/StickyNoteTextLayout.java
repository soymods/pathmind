package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;

/**
 * Pure word-wrapping for sticky-note body text: turns a raw string into the list
 * of display lines that fit within a given pixel width, honoring explicit
 * newlines and breaking over-long words. No editor state -- depends only on the
 * text and the font metrics.
 */
final class StickyNoteTextLayout {

    private StickyNoteTextLayout() {
    }

    static List<String> wrapLines(String source, Font textRenderer, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] rawLines = (source == null ? "" : source).split("\\n", -1);
        for (String rawLine : rawLines) {
            appendWrappedLine(lines, rawLine == null ? "" : rawLine, textRenderer, Math.max(1, maxWidth));
            if (lines.size() >= maxLines) {
                return new ArrayList<>(lines.subList(0, maxLines));
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static void appendWrappedLine(List<String> lines, String rawLine, Font textRenderer, int maxWidth) {
        if (rawLine.isEmpty()) {
            lines.add("");
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String word : rawLine.split(" ", -1)) {
            if (word.isEmpty()) {
                appendWhitespace(lines, current, textRenderer, maxWidth);
                continue;
            }

            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textRenderer.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            appendWrappedWord(lines, current, word, textRenderer, maxWidth);
        }
        lines.add(current.toString());
    }

    private static void appendWhitespace(List<String> lines, StringBuilder current, Font textRenderer, int maxWidth) {
        if (current.length() == 0 || textRenderer.width(current + " ") <= maxWidth) {
            current.append(' ');
            return;
        }
        lines.add(current.toString());
        current.setLength(0);
    }

    private static void appendWrappedWord(List<String> lines, StringBuilder current, String word,
                                          Font textRenderer, int maxWidth) {
        for (int index = 0; index < word.length(); index++) {
            String candidate = current.toString() + word.charAt(index);
            if (current.length() > 0 && textRenderer.width(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(word.charAt(index));
        }
    }
}
