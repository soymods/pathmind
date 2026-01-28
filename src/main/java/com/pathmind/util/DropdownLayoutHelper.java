package com.pathmind.util;

import com.pathmind.util.DrawContextBridge;

/**
 * Shared dropdown sizing helper that clamps dropdown height to the visible screen.
 */
public final class DropdownLayoutHelper {
    private DropdownLayoutHelper() {
    }

    public static Layout calculate(int optionCount, int optionHeight, int maxVisible, int dropdownTop, int screenHeight) {
        int safeOptionCount = Math.max(1, optionCount);
        int maxVisibleSafe = maxVisible > 0 ? maxVisible : safeOptionCount;
        int baseVisible = Math.min(maxVisibleSafe, safeOptionCount);
        int fullHeight = safeOptionCount * optionHeight;

        boolean offscreen = dropdownTop < 0 || dropdownTop + fullHeight > screenHeight;

        int availableHeight = screenHeight - dropdownTop;
        if (dropdownTop < 0) {
            availableHeight = screenHeight + dropdownTop;
        }
        if (availableHeight < optionHeight) {
            availableHeight = optionHeight;
        }
        int maxVisibleByScreen = Math.max(1, availableHeight / optionHeight);

        int visibleCount = offscreen ? Math.min(baseVisible, maxVisibleByScreen) : baseVisible;
        visibleCount = Math.max(1, visibleCount);

        int maxScrollOffset = Math.max(0, safeOptionCount - visibleCount);
        int height = visibleCount * optionHeight;

        return new Layout(visibleCount, maxScrollOffset, height, offscreen);
    }

    public static final class Layout {
        public final int visibleCount;
        public final int maxScrollOffset;
        public final int height;
        public final boolean offscreen;

        private Layout(int visibleCount, int maxScrollOffset, int height, boolean offscreen) {
            this.visibleCount = visibleCount;
            this.maxScrollOffset = maxScrollOffset;
            this.height = height;
            this.offscreen = offscreen;
        }
    }

    public static void drawOutline(net.minecraft.client.gui.DrawContext context,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   int color) {
        DrawContextBridge.drawBorder(context, x, y, width, height, color);
    }

    public static void drawScrollBar(net.minecraft.client.gui.DrawContext context,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     int optionCount,
                                     int visibleCount,
                                     int scrollOffset,
                                     int maxScrollOffset,
                                     int trackColor,
                                     int thumbColor) {
        if (maxScrollOffset <= 0) {
            return;
        }
        int safeOptionCount = Math.max(1, optionCount);
        int safeVisibleCount = Math.max(1, Math.min(visibleCount, safeOptionCount));
        int trackWidth = 4;
        int trackX = x + width - trackWidth - 2;
        int trackY = y + 2;
        int trackHeight = height - 4;
        if (trackHeight <= 0) {
            return;
        }
        int thumbHeight = Math.max(8, (trackHeight * safeVisibleCount) / safeOptionCount);
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        float ratio = maxScrollOffset == 0 ? 0f : (float) scrollOffset / (float) maxScrollOffset;
        int thumbY = trackY + Math.round((trackHeight - thumbHeight) * ratio);

        context.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, trackColor);
        context.fill(trackX, thumbY, trackX + trackWidth, thumbY + thumbHeight, thumbColor);
    }
}
