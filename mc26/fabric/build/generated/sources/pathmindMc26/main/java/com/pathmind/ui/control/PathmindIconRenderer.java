package com.pathmind.ui.control;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pixel-icon renderer shared by Pathmind screens.
 */
public final class PathmindIconRenderer {
    private PathmindIconRenderer() {
    }

    public static void drawPlay(GuiGraphicsExtractor context, int buttonX, int buttonY, int buttonSize, int color) {
        int triangleSize = Math.max(5, Math.min(buttonSize - 12, 7));
        int startX = buttonX + (buttonSize - triangleSize) / 2;
        int startY = buttonY + (buttonSize - triangleSize) / 2;

        for (int row = 0; row < triangleSize; row++) {
            int lineY = startY + row;
            int lineStartX = Math.max(startX + row / 2, buttonX + 2);
            int lineEndX = Math.min(startX + triangleSize - 1, buttonX + buttonSize - 3);
            if (lineStartX <= lineEndX && lineY >= buttonY + 2 && lineY <= buttonY + buttonSize - 3) {
                context.horizontalLine(lineStartX, lineEndX, lineY, color);
            }
        }
    }

    public static void drawStop(GuiGraphicsExtractor context, int buttonX, int buttonY, int buttonSize, int color) {
        int squareSize = Math.max(6, buttonSize - 10);
        int left = buttonX + (buttonSize - squareSize) / 2;
        int top = buttonY + (buttonSize - squareSize) / 2;
        context.fill(left, top, left + squareSize, top + squareSize, color);
    }

    public static void drawSearch(GuiGraphicsExtractor context, int x, int y, int color) {
        context.horizontalLine(x + 1, x + 3, y, color);
        context.horizontalLine(x, x + 4, y + 1, color);
        context.verticalLine(x, y + 2, y + 4, color);
        context.verticalLine(x + 4, y + 2, y + 4, color);
        context.horizontalLine(x + 1, x + 3, y + 5, color);
        context.horizontalLine(x + 5, x + 6, y + 5, color);
        context.horizontalLine(x + 6, x + 7, y + 6, color);
        context.horizontalLine(x + 7, x + 8, y + 7, color);
    }

    public static void drawPublishArrow(GuiGraphicsExtractor context, int buttonX, int buttonY, int buttonSize, int color) {
        int centerX = buttonX + buttonSize / 2;
        int top = buttonY + 4;
        int bottom = buttonY + buttonSize - 4;
        context.verticalLine(centerX, top + 2, bottom, color);
        context.horizontalLine(centerX - 3, centerX + 3, top + 2, color);
        context.horizontalLine(centerX - 2, centerX + 2, top + 1, color);
        context.horizontalLine(centerX - 1, centerX + 1, top, color);
    }

    public static void drawSettings(GuiGraphicsExtractor context, int buttonX, int buttonY, int buttonSize,
                                    int color, int centerCutoutColor) {
        int centerX = buttonX + buttonSize / 2;
        int centerY = buttonY + buttonSize / 2;
        context.fill(centerX - 1, centerY - 6, centerX + 1, centerY - 4, color);
        context.fill(centerX - 1, centerY + 4, centerX + 1, centerY + 6, color);
        context.fill(centerX - 6, centerY - 1, centerX - 4, centerY + 1, color);
        context.fill(centerX + 4, centerY - 1, centerX + 6, centerY + 1, color);

        context.fill(centerX - 4, centerY - 4, centerX + 4, centerY + 4, color);
        context.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, centerCutoutColor);
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
    }

    public static void drawPencil(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        for (int offset = 0; offset < size - 2; offset++) {
            int startX = x + offset;
            int startY = y + size - 3 - offset;
            context.fill(startX, startY, startX + 1, startY + 2, color);
        }

        int tipColor = (color & 0x00FFFFFF) | 0x66000000;
        context.fill(x + size - 3, y, x + size - 1, y + 2, tipColor);

        int eraserColor = (color & 0x00FFFFFF) | 0x88000000;
        context.fill(x, y + size - 1, x + 2, y + size, eraserColor);
    }

    public static void drawTrash(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        int handleWidth = Math.max(2, size / 2);
        int handleLeft = x + (size - handleWidth) / 2;
        context.fill(handleLeft, y, handleLeft + handleWidth, y + 1, color);

        context.fill(x, y + 1, x + size, y + 3, color);
        context.fill(x + 1, y + 3, x + size - 1, y + size, color);

        int slatColor = (color & 0x00FFFFFF) | 0x66000000;
        context.fill(x + 2, y + 4, x + 3, y + size - 1, slatColor);
        context.fill(x + size - 3, y + 4, x + size - 2, y + size - 1, slatColor);
    }

    public static void drawCloseX(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        int span = Math.max(4, size);
        for (int i = 0; i < span; i++) {
            context.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            context.fill(x + (span - 1 - i), y + i, x + (span - i), y + i + 1, color);
        }
    }

    /** Draws a small globe for values shared by every execution chain. */
    public static void drawGlobalScope(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        if (context == null || size < 5) {
            return;
        }
        int right = x + size - 1;
        int bottom = y + size - 1;
        int midX = x + size / 2;
        int midY = y + size / 2;
        context.horizontalLine(x + 2, right - 2, y, color);
        context.horizontalLine(x + 1, right - 1, y + 1, color);
        context.verticalLine(x, y + 2, bottom - 2, color);
        context.verticalLine(right, y + 2, bottom - 2, color);
        context.horizontalLine(x, right, midY, color);
        context.verticalLine(midX, y, bottom, color);
        context.horizontalLine(x + 1, right - 1, bottom - 1, color);
        context.horizontalLine(x + 2, right - 2, bottom, color);
    }

    /** Draws a small home for values local to a single execution chain. */
    public static void drawLocalScope(GuiGraphicsExtractor context, int x, int y, int size, int color) {
        if (context == null || size < 5) {
            return;
        }
        int midX = x + size / 2;
        int right = x + size - 1;
        int bottom = y + size - 1;
        context.fill(midX, y, midX + 1, y + 1, color);
        context.horizontalLine(midX - 1, midX + 1, y + 1, color);
        context.horizontalLine(midX - 2, midX + 2, y + 2, color);
        context.verticalLine(x + 1, y + 3, bottom, color);
        context.verticalLine(right - 1, y + 3, bottom, color);
        context.horizontalLine(x + 1, right - 1, y + 3, color);
        context.horizontalLine(x + 1, right - 1, bottom, color);
        context.verticalLine(midX, bottom - 2, bottom, color);
    }
}
