package com.pathmind.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Shared scrollbar metrics/rendering helpers for Pathmind UI.
 */
public final class ScrollbarHelper {
    private ScrollbarHelper() {
    }

    public static Metrics metrics(int trackLeft, int trackTop, int trackWidth, int viewportHeight,
                                  int maxScroll, int scrollOffset, int minThumbHeight) {
        int clampedViewportHeight = Math.max(1, viewportHeight);
        int clampedMaxScroll = Math.max(0, maxScroll);
        int clampedOffset = clampScroll(scrollOffset, clampedMaxScroll);
        int totalHeight = clampedViewportHeight + clampedMaxScroll;
        int thumbHeight = Math.max(minThumbHeight, (clampedViewportHeight * clampedViewportHeight) / Math.max(1, totalHeight));
        thumbHeight = Math.min(clampedViewportHeight, thumbHeight);
        int maxThumbTravel = Math.max(0, clampedViewportHeight - thumbHeight);
        int thumbOffset = clampedMaxScroll == 0 ? 0 : Math.round((float) clampedOffset / (float) clampedMaxScroll * maxThumbTravel);
        int thumbTop = trackTop + thumbOffset;
        return new Metrics(trackLeft, trackTop, trackWidth, clampedViewportHeight, clampedMaxScroll, clampedOffset, thumbTop, thumbHeight);
    }

    public static int clampScroll(int scrollOffset, int maxScroll) {
        return Math.max(0, Math.min(scrollOffset, Math.max(0, maxScroll)));
    }

    public static int applyWheel(int scrollOffset, double verticalAmount, int step, int maxScroll) {
        if (verticalAmount == 0.0) {
            return clampScroll(scrollOffset, maxScroll);
        }
        int delta = (int) Math.round(verticalAmount * step);
        if (delta == 0) {
            delta = (int) Math.signum(verticalAmount);
        }
        int next = scrollOffset - delta;
        return clampScroll(next, maxScroll);
    }

    public static int scrollFromThumb(Metrics metrics, int thumbTop) {
        if (metrics.maxScroll() <= 0) {
            return 0;
        }
        int minThumbTop = metrics.trackTop();
        int maxThumbTop = metrics.trackTop() + Math.max(0, metrics.viewportHeight() - metrics.thumbHeight());
        int clampedThumbTop = Math.max(minThumbTop, Math.min(maxThumbTop, thumbTop));
        float progress = maxThumbTop <= minThumbTop ? 0f : (clampedThumbTop - minThumbTop) / (float) (maxThumbTop - minThumbTop);
        return clampScroll(Math.round(progress * metrics.maxScroll()), metrics.maxScroll());
    }

    public static void renderSettingsStyle(DrawContext context, Metrics metrics, int trackColor, int borderColor, int thumbColor) {
        if (metrics.maxScroll() <= 0) {
            return;
        }
        context.fill(metrics.trackLeft(), metrics.trackTop(), metrics.trackRight(), metrics.trackBottom(), trackColor);
        DrawContextBridge.drawBorder(context, metrics.trackLeft(), metrics.trackTop(), metrics.trackWidth(), metrics.viewportHeight(), borderColor);
        context.fill(metrics.trackLeft() + 1, metrics.thumbTop(), metrics.trackRight() - 1, metrics.thumbTop() + metrics.thumbHeight(), thumbColor);
    }

    public static void renderCutoffDividers(DrawContext context, int left, int right, int top, int bottom,
                                            int scrollOffset, int maxScroll, int color) {
        if (maxScroll <= 0) {
            return;
        }
        if (scrollOffset > 0) {
            context.drawHorizontalLine(left, right, top, color);
        }
        if (scrollOffset < maxScroll) {
            context.drawHorizontalLine(left, right, bottom - 1, color);
        }
    }

    public record Metrics(
        int trackLeft,
        int trackTop,
        int trackWidth,
        int viewportHeight,
        int maxScroll,
        int scrollOffset,
        int thumbTop,
        int thumbHeight
    ) {
        public int trackRight() {
            return trackLeft + trackWidth;
        }

        public int trackBottom() {
            return trackTop + viewportHeight;
        }
    }
}
