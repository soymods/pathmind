package com.pathmind.util;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared dropdown sizing helper that clamps dropdown height to the visible screen.
 */
public final class DropdownLayoutHelper {
    public static final int DEFAULT_ANIMATION_DURATION_MS = 160;
    public static final int DEFAULT_SCROLL_ANIMATION_DURATION_MS = 220;
    private static final int SCROLLBAR_TRACK_WIDTH = 4;
    private static final int SCROLLBAR_RIGHT_PADDING = 2;
    private static final int SCROLLBAR_HIT_PADDING = 2;

    private DropdownLayoutHelper() {
    }

    public static float updateOpenAnimation(AnimatedValue animation, boolean open) {
        return updateOpenAnimation(animation, open, DEFAULT_ANIMATION_DURATION_MS);
    }

    public static float updateOpenAnimation(AnimatedValue animation, boolean open, int durationMs) {
        if (animation == null) {
            return open ? 1f : 0f;
        }
        animation.animateTo(open ? 1f : 0f, durationMs, AnimationHelper::easeInOutCubic);
        animation.tick();
        return clampProgress(animation.getValue());
    }

    public static int getRevealHeight(int fullHeight, float progress) {
        if (fullHeight <= 0) {
            return 0;
        }
        float clamped = clampProgress(progress);
        if (clamped <= 0.001f) {
            return 0;
        }
        return Math.max(1, Math.round(fullHeight * clamped));
    }

    public static int getRevealBottom(int top, int fullHeight, float progress, int bottomPadding) {
        int revealHeight = getRevealHeight(fullHeight, progress);
        if (revealHeight <= 0) {
            return top;
        }
        return top + revealHeight + Math.max(0, bottomPadding);
    }

    public static void enableRevealScissor(GuiGraphics context, int x, int y, int width, int fullHeight, float progress) {
        enableRevealScissor(context, x, y, width, fullHeight, progress, 0);
    }

    public static void enableRevealScissor(GuiGraphics context, int x, int y, int width, int fullHeight, float progress, int bottomPadding) {
        if (context == null) {
            return;
        }
        context.enableScissor(x, y, x + width, getRevealBottom(y, fullHeight, progress, bottomPadding));
    }

    public static float updateSmoothScroll(SmoothScrollState state, int targetOffset, int maxScrollOffset) {
        return updateSmoothScroll(state, targetOffset, maxScrollOffset, DEFAULT_SCROLL_ANIMATION_DURATION_MS);
    }

    public static float updateSmoothScroll(SmoothScrollState state, int targetOffset, int maxScrollOffset, int durationMs) {
        int clampedTarget = Math.max(0, Math.min(targetOffset, Math.max(0, maxScrollOffset)));
        if (state == null) {
            return clampedTarget;
        }
        return state.update(clampedTarget, durationMs);
    }

    public static ScrollWindow getSmoothScrollWindow(float scrollOffset, int visibleCount, int optionCount, int optionHeight) {
        int safeOptionCount = Math.max(0, optionCount);
        int safeVisibleCount = Math.max(1, visibleCount);
        int safeOptionHeight = Math.max(1, optionHeight);
        if (safeOptionCount <= 0) {
            return new ScrollWindow(0, 0, 0);
        }

        float maxOffset = Math.max(0, safeOptionCount - safeVisibleCount);
        float clampedOffset = Math.max(0f, Math.min(scrollOffset, maxOffset));
        int firstIndex = Math.max(0, Math.min((int) Math.floor(clampedOffset), safeOptionCount - 1));
        int endIndex = Math.min(safeOptionCount, firstIndex + safeVisibleCount + 1);
        int pixelOffset = -Math.round((clampedOffset - firstIndex) * safeOptionHeight);
        return new ScrollWindow(firstIndex, endIndex, pixelOffset);
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

    public static final class SmoothScrollState {
        private final AnimatedValue offset = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
        private boolean initialized;

        private float update(int targetOffset, int durationMs) {
            if (!initialized) {
                offset.setValue(targetOffset);
                initialized = true;
                return targetOffset;
            }
            offset.animateTo(targetOffset, durationMs, AnimationHelper::easeOutCubic);
            offset.tick();
            return offset.getValue();
        }

        public void setInstant(int targetOffset) {
            offset.setValue(Math.max(0, targetOffset));
            initialized = true;
        }
    }

    public static final class ScrollWindow {
        public final int firstIndex;
        public final int endIndex;
        public final int pixelOffset;

        private ScrollWindow(int firstIndex, int endIndex, int pixelOffset) {
            this.firstIndex = firstIndex;
            this.endIndex = endIndex;
            this.pixelOffset = pixelOffset;
        }
    }

    public static void drawOutline(net.minecraft.client.gui.GuiGraphics context,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   int color) {
        DrawContextBridge.drawBorder(context, x, y, width, height, color);
    }

    public static void drawScrollBar(net.minecraft.client.gui.GuiGraphics context,
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

    private static float clampProgress(float progress) {
        if (progress < 0f) {
            return 0f;
        }
        if (progress > 1f) {
            return 1f;
        }
        return progress;
    }
}
