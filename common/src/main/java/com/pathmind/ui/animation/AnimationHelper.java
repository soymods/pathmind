package com.pathmind.ui.animation;

/**
 * Core animation utilities for smooth UI transitions.
 * Provides easing functions and color interpolation for consistent animations.
 */
public final class AnimationHelper {

    private AnimationHelper() {} // Prevent instantiation

    // ═══════════════════════════════════════════════════════════════════════════
    // EASING FUNCTIONS
    // All functions take a normalized time value t (0.0 to 1.0) and return
    // the eased value (also 0.0 to 1.0)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Linear interpolation - no easing.
     */
    public static float linear(float t) {
        return clamp01(t);
    }

    /**
     * Quadratic ease out - starts fast, decelerates.
     * Good for hover-off and element exits.
     */
    public static float easeOutQuad(float t) {
        t = clamp01(t);
        return t * (2 - t);
    }

    /**
     * Quadratic ease in - starts slow, accelerates.
     */
    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    /**
     * Quadratic ease in-out - smooth acceleration and deceleration.
     * Good for toggle switches and state transitions.
     */
    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    /**
     * Cubic ease out - faster initial movement, smoother stop.
     * Good for hover effects.
     */
    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float t1 = t - 1;
        return t1 * t1 * t1 + 1;
    }

    /**
     * Cubic ease in - very slow start.
     */
    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /**
     * Cubic ease in-out - very smooth transitions.
     */
    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
    }

    /**
     * Sine ease out - natural feeling deceleration.
     */
    public static float easeOutSine(float t) {
        t = clamp01(t);
        return (float) Math.sin(t * Math.PI / 2);
    }

    /**
     * Sine ease in-out - very smooth, natural motion.
     */
    public static float easeInOutSine(float t) {
        t = clamp01(t);
        return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR INTERPOLATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Linearly interpolates between two ARGB colors.
     *
     * @param from Starting color (ARGB format)
     * @param to Target color (ARGB format)
     * @param progress Interpolation progress (0.0 = from, 1.0 = to)
     * @return Interpolated color in ARGB format
     */
    public static int lerpColor(int from, int to, float progress) {
        progress = clamp01(progress);

        int aFrom = (from >> 24) & 0xFF;
        int rFrom = (from >> 16) & 0xFF;
        int gFrom = (from >> 8) & 0xFF;
        int bFrom = from & 0xFF;

        int aTo = (to >> 24) & 0xFF;
        int rTo = (to >> 16) & 0xFF;
        int gTo = (to >> 8) & 0xFF;
        int bTo = to & 0xFF;

        int a = (int) (aFrom + (aTo - aFrom) * progress);
        int r = (int) (rFrom + (rTo - rFrom) * progress);
        int g = (int) (gFrom + (gTo - gFrom) * progress);
        int b = (int) (bFrom + (bTo - bFrom) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Linearly interpolates between two ARGB colors with easing.
     *
     * @param from Starting color
     * @param to Target color
     * @param progress Raw progress (0.0 to 1.0)
     * @param easingFunction Easing function to apply
     * @return Interpolated color
     */
    public static int lerpColorEased(int from, int to, float progress, java.util.function.Function<Float, Float> easingFunction) {
        return lerpColor(from, to, easingFunction.apply(progress));
    }

    /**
     * Brightens a color by a factor.
     *
     * @param color The color to brighten (ARGB format)
     * @param factor Factor > 1.0 brightens, < 1.0 darkens
     * @return Brightened color
     */
    public static int brighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Darkens a color by a factor.
     *
     * @param color The color to darken (ARGB format)
     * @param factor Factor 0.0-1.0 where lower = darker
     * @return Darkened color
     */
    public static int darken(int color, float factor) {
        factor = clamp01(factor);
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Sets the alpha of a color.
     *
     * @param color The base color (ARGB format)
     * @param alpha Alpha value 0-255
     * @return Color with new alpha
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Sets the alpha of a color using a float.
     *
     * @param color The base color (ARGB format)
     * @param alpha Alpha value 0.0-1.0
     * @return Color with new alpha
     */
    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, (int) (clamp01(alpha) * 255));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALUE INTERPOLATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Linearly interpolates between two float values.
     */
    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp01(progress);
    }

    /**
     * Linearly interpolates between two int values.
     */
    public static int lerp(int from, int to, float progress) {
        return from + (int) ((to - from) * clamp01(progress));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Clamps a value between 0 and 1.
     */
    public static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * Clamps a value between min and max.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps an int value between min and max.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
