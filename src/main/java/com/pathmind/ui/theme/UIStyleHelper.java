package com.pathmind.ui.theme;

import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared rendering helpers for Pathmind UI chrome.
 * Keeps panel/button framing logic centralized and avoids hardcoded styling in screens/widgets.
 */
public final class UIStyleHelper {

    private UIStyleHelper() {
    }

    public static void drawPanel(DrawContext context, int x, int y, int width, int height, int backgroundColor, int borderColor) {
        if (context == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, backgroundColor);
        DrawContextBridge.drawBorder(context, x, y, width, height, borderColor);
    }

    public static void drawBeveledPanel(DrawContext context, int x, int y, int width, int height,
                                        int backgroundColor, int outerBorderColor, int innerBorderColor) {
        drawPanel(context, x, y, width, height, backgroundColor, outerBorderColor);
        if (width > 3 && height > 3) {
            DrawContextBridge.drawBorder(context, x + 1, y + 1, width - 2, height - 2, innerBorderColor);
        }
    }

    public static void drawToolbarButtonFrame(DrawContext context, int x, int y, int width, int height,
                                              int backgroundColor, int outerBorderColor, int innerBorderColor) {
        drawBeveledPanel(context, x, y, width, height, backgroundColor, outerBorderColor, innerBorderColor);
    }

    public static void drawChevron(DrawContext context, int centerX, int centerY, boolean expanded, int color) {
        if (expanded) {
            context.drawHorizontalLine(centerX - 3, centerX + 3, centerY - 2, color);
            context.drawHorizontalLine(centerX - 2, centerX + 2, centerY - 1, color);
            context.drawHorizontalLine(centerX - 1, centerX + 1, centerY, color);
            return;
        }
        context.drawHorizontalLine(centerX - 3, centerX + 3, centerY + 1, color);
        context.drawHorizontalLine(centerX - 2, centerX + 2, centerY, color);
        context.drawHorizontalLine(centerX - 1, centerX + 1, centerY - 1, color);
    }

    public static void drawTextCaret(DrawContext context, int caretX, int topY, int bottomY, int color) {
        drawTextCaret(context, caretX, topY, bottomY, caretX + 1, color);
    }

    public static void drawTextCaret(DrawContext context, int caretX, int topY, int bottomY, int maxRight, int color) {
        if (context == null) {
            return;
        }
        int startY = Math.min(topY, bottomY);
        int endY = Math.max(topY, bottomY);
        int endX = Math.min(caretX + 1, maxRight);
        if (endX <= caretX || endY <= startY) {
            return;
        }
        context.fill(caretX, startY, endX, endY, color);
    }

    public static void drawTextCaretAtBaseline(DrawContext context, TextRenderer textRenderer, int caretX, int baselineY, int maxRight, int color) {
        if (textRenderer == null) {
            return;
        }
        int topY = baselineY - textRenderer.fontHeight + 1;
        drawTextCaret(context, caretX, topY, baselineY + 1, maxRight, color);
    }
}
