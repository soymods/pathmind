package com.pathmind.util;

import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Utility helpers for rendering text within constrained widths.
 */
public final class TextRenderUtil {
    private static final String ELLIPSIS = "...";

    private TextRenderUtil() {
    }

    public static String trimWithEllipsis(TextRenderer renderer, String text, int availableWidth) {
        if (renderer == null || text == null) {
            return "";
        }
        if (availableWidth <= 0) {
            return ELLIPSIS;
        }
        if (renderer.getWidth(text) <= availableWidth) {
            return text;
        }

        int ellipsisWidth = renderer.getWidth(ELLIPSIS);
        if (ellipsisWidth >= availableWidth) {
            return ELLIPSIS;
        }

        int trimmedWidth = Math.max(0, availableWidth - ellipsisWidth);
        return renderer.trimToWidth(text, trimmedWidth) + ELLIPSIS;
    }

    /**
     * Wraps text at whitespace while preserving explicit line breaks. A word is
     * split only when it cannot fit on an otherwise empty line.
     */
    public static List<String> wrapWords(TextRenderer renderer, String text, int availableWidth) {
        if (renderer == null) {
            return List.of();
        }
        return wrapWords(text, availableWidth, renderer::getWidth);
    }

    static List<String> wrapWords(String text, int availableWidth, ToIntFunction<String> widthProvider) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (availableWidth <= 0) {
            lines.add(text);
            return lines;
        }

        String[] paragraphs = text.split("\\n", -1);
        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph.replace("\r", "").trim();
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            wrapParagraph(paragraph, availableWidth, widthProvider, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, int availableWidth,
                                      ToIntFunction<String> widthProvider, List<String> lines) {
        String currentLine = "";
        for (String word : paragraph.split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (widthProvider.applyAsInt(candidate) <= availableWidth) {
                currentLine = candidate;
                continue;
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
                currentLine = "";
            }

            if (widthProvider.applyAsInt(word) <= availableWidth) {
                currentLine = word;
                continue;
            }

            List<String> parts = splitOversizedWord(word, availableWidth, widthProvider);
            for (int i = 0; i < parts.size() - 1; i++) {
                lines.add(parts.get(i));
            }
            currentLine = parts.get(parts.size() - 1);
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
    }

    private static List<String> splitOversizedWord(String word, int availableWidth,
                                                    ToIntFunction<String> widthProvider) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String candidate = currentPart + character;
            if (!currentPart.isEmpty() && widthProvider.applyAsInt(candidate) > availableWidth) {
                parts.add(currentPart.toString());
                currentPart.setLength(0);
            }
            currentPart.append(character);
            if (widthProvider.applyAsInt(currentPart.toString()) > availableWidth) {
                // A single glyph can be wider than the available area. Keep it
                // intact so wrapping always makes progress without corrupting Unicode.
                parts.add(currentPart.toString());
                currentPart.setLength(0);
            }
            offset += Character.charCount(codePoint);
        }
        if (!currentPart.isEmpty()) {
            parts.add(currentPart.toString());
        }
        return parts;
    }
}
