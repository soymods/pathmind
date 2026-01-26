package com.pathmind.ui.animation;

import java.util.function.Function;

/**
 * Represents a single animated value with state tracking.
 * Automatically handles smooth transitions between values using easing functions.
 */
public class AnimatedValue {

    private float currentValue;
    private float targetValue;
    private float startValue;
    private long animationStartTime;
    private int durationMs;
    private boolean isAnimating;
    private Function<Float, Float> easingFunction;

    /**
     * Creates an AnimatedValue with initial value 0 and default easing.
     */
    public AnimatedValue() {
        this(0f);
    }

    /**
     * Creates an AnimatedValue with specified initial value and default easing.
     */
    public AnimatedValue(float initialValue) {
        this(initialValue, AnimationHelper::easeOutCubic);
    }

    /**
     * Creates an AnimatedValue with specified initial value and easing function.
     */
    public AnimatedValue(float initialValue, Function<Float, Float> easingFunction) {
        this.currentValue = initialValue;
        this.targetValue = initialValue;
        this.startValue = initialValue;
        this.isAnimating = false;
        this.easingFunction = easingFunction;
    }

    /**
     * Starts animating towards a target value.
     *
     * @param target The target value to animate towards
     * @param durationMs Duration of the animation in milliseconds
     */
    public void animateTo(float target, int durationMs) {
        if (Math.abs(target - this.targetValue) < 0.001f && this.isAnimating) {
            return; // Already animating to this target
        }

        this.startValue = this.currentValue;
        this.targetValue = target;
        this.durationMs = durationMs;
        this.animationStartTime = System.currentTimeMillis();
        this.isAnimating = true;
    }

    /**
     * Starts animating towards a target value with a specific easing function.
     */
    public void animateTo(float target, int durationMs, Function<Float, Float> easing) {
        this.easingFunction = easing;
        animateTo(target, durationMs);
    }

    /**
     * Immediately sets the value without animation.
     */
    public void setValue(float value) {
        this.currentValue = value;
        this.targetValue = value;
        this.startValue = value;
        this.isAnimating = false;
    }

    /**
     * Updates the animation state. Call this every frame/tick.
     * Uses System.currentTimeMillis() internally.
     */
    public void tick() {
        tick(System.currentTimeMillis());
    }

    /**
     * Updates the animation state with a specific timestamp.
     *
     * @param currentTime Current time in milliseconds
     */
    public void tick(long currentTime) {
        if (!isAnimating) {
            return;
        }

        long elapsed = currentTime - animationStartTime;

        if (elapsed >= durationMs) {
            // Animation complete
            currentValue = targetValue;
            isAnimating = false;
        } else {
            // Calculate progress
            float rawProgress = (float) elapsed / durationMs;
            float easedProgress = easingFunction.apply(rawProgress);
            currentValue = AnimationHelper.lerp(startValue, targetValue, easedProgress);
        }
    }

    /**
     * Gets the current animated value.
     * Note: Call tick() before getValue() each frame for accurate results.
     */
    public float getValue() {
        return currentValue;
    }

    /**
     * Gets the current value and updates the animation in one call.
     * Convenience method that combines tick() and getValue().
     */
    public float getValueAndTick() {
        tick();
        return currentValue;
    }

    /**
     * Gets the target value the animation is moving towards.
     */
    public float getTargetValue() {
        return targetValue;
    }

    /**
     * Returns true if currently animating.
     */
    public boolean isAnimating() {
        return isAnimating;
    }

    /**
     * Returns true if the current value is at or very close to the target.
     */
    public boolean isAtTarget() {
        return Math.abs(currentValue - targetValue) < 0.001f;
    }

    /**
     * Cancels the current animation and stays at current value.
     */
    public void cancel() {
        this.targetValue = this.currentValue;
        this.isAnimating = false;
    }

    /**
     * Cancels the animation and jumps to the target value.
     */
    public void complete() {
        this.currentValue = this.targetValue;
        this.isAnimating = false;
    }

    /**
     * Sets the easing function used for future animations.
     */
    public void setEasingFunction(Function<Float, Float> easingFunction) {
        this.easingFunction = easingFunction;
    }

    /**
     * Creates an AnimatedValue for hover states (0 = not hovered, 1 = hovered).
     */
    public static AnimatedValue forHover() {
        return new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    }

    /**
     * Creates an AnimatedValue for toggle states (0 = off, 1 = on).
     */
    public static AnimatedValue forToggle(boolean initialState) {
        return new AnimatedValue(initialState ? 1f : 0f, AnimationHelper::easeInOutQuad);
    }

    /**
     * Creates an AnimatedValue for smooth color transitions.
     */
    public static AnimatedValue forColor() {
        return new AnimatedValue(0f, AnimationHelper::easeOutQuad);
    }
}
