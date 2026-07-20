package com.pathmind.ui.graph;

import net.minecraft.client.gui.GuiGraphics;

final class ConnectionRenderer {

    private static final int CONNECTION_DOT_SPACING = 12;
    private static final int CONNECTION_DOT_LENGTH = 4;
    private static final int CONNECTION_ANIMATION_STEP_MS = 50;

    private ConnectionRenderer() {
    }

    static void renderAnimatedConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color, long timestamp) {
        int midX = x1 + (x2 - x1) / 2;

        int firstSegmentLength = Math.abs(midX - x1);
        int secondSegmentLength = Math.abs(y2 - y1);

        int animationOffset = (int) ((timestamp / CONNECTION_ANIMATION_STEP_MS) % CONNECTION_DOT_SPACING);

        drawAnimatedSegment(context, x1, y1, midX, y1, true, color, animationOffset, 0);
        drawAnimatedSegment(context, midX, y1, midX, y2, false, color, animationOffset, firstSegmentLength);
        drawAnimatedSegment(context, midX, y2, x2, y2, true, color, animationOffset,
                firstSegmentLength + secondSegmentLength);
    }

    private static void drawAnimatedSegment(GuiGraphics context, int x1, int y1, int x2, int y2, boolean horizontal,
                                            int color, int animationOffset, int distanceOffset) {
        int length = horizontal ? Math.abs(x2 - x1) : Math.abs(y2 - y1);
        if (length == 0) {
            return;
        }

        int direction = horizontal ? Integer.compare(x2, x1) : Integer.compare(y2, y1);
        int start = horizontal ? x1 : y1;
        int staticCoord = horizontal ? y1 : x1;

        int initialOffset = mod(distanceOffset - animationOffset, CONNECTION_DOT_SPACING);
        int stepStart = (CONNECTION_DOT_SPACING - initialOffset) % CONNECTION_DOT_SPACING;

        int position = stepStart;
        while (position > 0) {
            position -= CONNECTION_DOT_SPACING;
        }

        boolean drewSegment = false;

        for (; position <= length; position += CONNECTION_DOT_SPACING) {
            int minDistance = Math.max(position, 0);
            int maxDistance = Math.min(position + CONNECTION_DOT_LENGTH - 1, length);
            if (maxDistance < 0 || minDistance > length || minDistance > maxDistance) {
                continue;
            }

            drewSegment = true;

            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.hLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.vLine(staticCoord, minY, maxY, color);
            }
        }

        if (!drewSegment) {
            int fallbackLength = Math.min(CONNECTION_DOT_LENGTH, length);
            int minDistance = Math.max(0, length - fallbackLength);
            int maxDistance = length;
            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.hLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.vLine(staticCoord, minY, maxY, color);
            }
        }
    }

    private static int mod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    static void renderConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        // Draw a simple L-shaped connection line
        int midX = x1 + (x2 - x1) / 2;

        // Horizontal line from source to middle
        context.hLine(Math.min(x1, midX), Math.max(x1, midX), y1, color);

        // Vertical line from middle to target
        context.vLine(midX, Math.min(y1, y2), Math.max(y1, y2), color);

        // Horizontal line from middle to target
        context.hLine(Math.min(midX, x2), Math.max(midX, x2), y2, color);
    }

    static void drawSegmentWithThickness(GuiGraphics context, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (x1 == x2 && y1 == y2) {
            context.fill(x1 - thickness, y1 - thickness, x1 + thickness + 1, y1 + thickness + 1, color);
            return;
        }

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0.0f : (float) i / (float) steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            context.fill(x - thickness, y - thickness, x + thickness + 1, y + thickness + 1, color);
        }
    }

    static void renderDenseConnectionCurve(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        context.vLine(x2, Math.min(y1, y2), Math.max(y1, y2), color);
    }
}
