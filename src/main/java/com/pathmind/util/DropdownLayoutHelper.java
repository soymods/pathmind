package com.pathmind.util;

import com.pathmind.util.DrawContextBridge;

/**
 * Shared dropdown sizing helper that clamps dropdown height to the visible screen.
 */
public final class DropdownLayoutHelper {
    private static final int SCROLLBAR_TRACK_WIDTH = 4;
    private static final int SCROLLBAR_RIGHT_PADDING = 2;
    private static final int SCROLLBAR_HIT_PADDING = 2;

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
        int trackWidth = SCROLLBAR_TRACK_WIDTH;
        int trackX = x + width - trackWidth - SCROLLBAR_RIGHT_PADDING;
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

    public static int getScrollbarHitLeft(int x, int width, int maxScrollOffset) {
        if (maxScrollOffset <= 0) {
            return x + width;
        }
        return x + width - SCROLLBAR_TRACK_WIDTH - SCROLLBAR_RIGHT_PADDING - SCROLLBAR_HIT_PADDING;
    }

    public static boolean isScrollbarHit(double mouseX,
                                         double mouseY,
                                         int x,
                                         int y,
                                         int width,
                                         int height,
                                         int maxScrollOffset) {
        if (maxScrollOffset <= 0) {
            return false;
        }
        int hitLeft = getScrollbarHitLeft(x, width, maxScrollOffset);
        int hitRight = x + width;
        int hitTop = y;
        int hitBottom = y + height;
        return mouseX >= hitLeft && mouseX <= hitRight && mouseY >= hitTop && mouseY <= hitBottom;
    }

    public static int scrollOffsetFromMouseY(double mouseY,
                                             int dropdownY,
                                             int dropdownHeight,
                                             int visibleCount,
                                             int optionCount,
                                             int maxScrollOffset) {
        if (maxScrollOffset <= 0) {
            return 0;
        }
        int trackY = dropdownY + 2;
        int trackHeight = dropdownHeight - 4;
        if (trackHeight <= 0) {
            return 0;
        }
        int safeOptionCount = Math.max(1, optionCount);
        int safeVisibleCount = Math.max(1, Math.min(visibleCount, safeOptionCount));
        int thumbHeight = Math.max(8, (trackHeight * safeVisibleCount) / safeOptionCount);
        thumbHeight = Math.min(thumbHeight, trackHeight);
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        double relative = mouseY - trackY - thumbHeight / 2.0;
        int offset = (int) Math.round(relative / thumbTravel * maxScrollOffset);
        return Math.max(0, Math.min(maxScrollOffset, offset));
    }
}
