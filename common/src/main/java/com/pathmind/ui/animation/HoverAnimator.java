package com.pathmind.ui.animation;

import com.pathmind.ui.theme.UITheme;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks hover animation progress for UI elements keyed by object.
 */
public final class HoverAnimator {
    private static final Map<Object, AnimatedValue> VALUES = new WeakHashMap<>();

    private HoverAnimator() {}

    public static float getProgress(Object key, boolean hovered) {
        return getProgress(key, hovered, UITheme.HOVER_ANIM_MS);
    }

    public static float getProgress(Object key, boolean hovered, int durationMs) {
        AnimatedValue value = VALUES.computeIfAbsent(key, k -> AnimatedValue.forHover());
        value.tick();
        float target = hovered ? 1f : 0f;
        if (Math.abs(value.getTargetValue() - target) > 0.001f) {
            value.animateTo(target, durationMs);
        }
        return value.getValue();
    }
}
