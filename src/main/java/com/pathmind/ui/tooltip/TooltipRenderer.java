package com.pathmind.ui.tooltip;

import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class TooltipRenderer {
    private static final int PADDING = 6;
    private static final int LINE_SPACING = 2;
    private static final int MAX_WIDTH = 220;
    private static final int MARGIN = 8;

    private TooltipRenderer() {
    }

    public static void render(DrawContext context, TextRenderer textRenderer, String text, int mouseX, int mouseY,
                              int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty() || textRenderer == null) {
            return;
        }

        int maxWidth = Math.min(MAX_WIDTH, Math.max(120, screenWidth - MARGIN * 2));
        List<String> lines = wrapText(text, textRenderer, Math.max(1, maxWidth - PADDING * 2));
        if (lines.isEmpty()) {
            return;
        }

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, textRenderer.getWidth(line));
        }
        int textHeight = lines.size() * textRenderer.fontHeight + Math.max(0, (lines.size() - 1) * LINE_SPACING);
        int tooltipWidth = textWidth + PADDING * 2;
        int tooltipHeight = textHeight + PADDING * 2;

        int x = mouseX + 12;
        int y = mouseY + 12;
        if (x + tooltipWidth > screenWidth - MARGIN) {
            x = screenWidth - MARGIN - tooltipWidth;
        }
        if (x < MARGIN) {
            x = MARGIN;
        }
        if (y + tooltipHeight > screenHeight - MARGIN) {
            y = screenHeight - MARGIN - tooltipHeight;
        }
        if (y < MARGIN) {
            y = MARGIN;
        }

        context.fill(x, y, x + tooltipWidth, y + tooltipHeight, UITheme.TOOLTIP_BG);
        DrawContextBridge.drawBorder(context, x, y, tooltipWidth, tooltipHeight, UITheme.TOOLTIP_BORDER);

        int textX = x + PADDING;
        int textY = y + PADDING;
        for (String line : lines) {
            context.drawTextWithShadow(textRenderer, Text.literal(line), textX, textY, UITheme.TEXT_PRIMARY);
            textY += textRenderer.fontHeight + LINE_SPACING;
        }
    }

    private static List<String> wrapText(String text, TextRenderer textRenderer, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (currentLine.length() > 0) {
                String candidate = currentLine + " " + word;
                if (textRenderer.getWidth(candidate) <= maxWidth) {
                    currentLine.append(" ").append(word);
                    continue;
                }
            }

            if (textRenderer.getWidth(word) <= maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                String remaining = word;
                while (!remaining.isEmpty()) {
                    int breakIndex = findBreakIndex(remaining, textRenderer, maxWidth);
                    lines.add(remaining.substring(0, breakIndex));
                    remaining = remaining.substring(breakIndex);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        if (lines.isEmpty()) {
            lines.add(text);
        }

        return lines;
    }

    private static int findBreakIndex(String text, TextRenderer textRenderer, int maxWidth) {
        if (text.isEmpty() || maxWidth <= 0) {
            return Math.max(1, text.length());
        }

        int breakIndex = 1;
        while (breakIndex <= text.length()
            && textRenderer.getWidth(text.substring(0, breakIndex)) <= maxWidth) {
            breakIndex++;
        }

        if (breakIndex > text.length()) {
            return text.length();
        }

        return Math.max(1, breakIndex - 1);
    }
}
