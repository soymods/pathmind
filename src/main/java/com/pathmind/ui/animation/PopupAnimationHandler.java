package com.pathmind.ui.animation;

/**
 * Handles smooth entrance and exit animations for popup overlays.
 * Provides animated values for background fade-in and popup scale/fade effects.
 */
public class PopupAnimationHandler {

    private static final int ANIMATION_DURATION_MS = 250;

    private final AnimatedValue backgroundAlpha;
    private final AnimatedValue popupScale;
    private boolean isVisible;

    /**
     * Creates a new popup animation handler.
     */
    public PopupAnimationHandler() {
        this.backgroundAlpha = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
        this.popupScale = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
        this.isVisible = false;
    }

    /**
     * Starts the entrance animation.
     * Call this when the popup is shown.
     */
    public void show() {
        isVisible = true;
        backgroundAlpha.animateTo(1f, ANIMATION_DURATION_MS);
        popupScale.animateTo(1f, ANIMATION_DURATION_MS);
    }

    /**
     * Starts the exit animation.
     * Call this when the popup is hidden.
     */
    public void hide() {
        isVisible = false;
        backgroundAlpha.animateTo(0f, ANIMATION_DURATION_MS);
        popupScale.animateTo(0f, ANIMATION_DURATION_MS);
    }

    /**
     * Immediately shows the popup without animation.
     */
    public void showInstant() {
        isVisible = true;
        backgroundAlpha.setValue(1f);
        popupScale.setValue(1f);
    }

    /**
     * Immediately hides the popup without animation.
     */
    public void hideInstant() {
        isVisible = false;
        backgroundAlpha.setValue(0f);
        popupScale.setValue(0f);
    }

    /**
     * Updates the animation state. Call this every frame.
     */
    public void tick() {
        backgroundAlpha.tick();
        popupScale.tick();
    }

    /**
     * Gets the current background alpha multiplier (0.0 to 1.0).
     * Multiply your background color's alpha by this value.
     */
    public float getBackgroundAlpha() {
        return backgroundAlpha.getValue();
    }

    /**
     * Gets the current popup scale (0.0 to 1.0).
     * Interpolates from 0.8 to 1.0 for a smooth scale-in effect.
     */
    public float getPopupScale() {
        float progress = popupScale.getValue();
        return AnimationHelper.lerp(0.8f, 1.0f, progress);
    }

    /**
     * Gets the current popup alpha multiplier (0.0 to 1.0).
     * Use this to fade in popup content.
     */
    public float getPopupAlpha() {
        return popupScale.getValue();
    }

    /**
     * Returns true if the popup is currently visible or animating.
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * Returns true if the animation is currently in progress.
     */
    public boolean isAnimating() {
        return backgroundAlpha.isAnimating() || popupScale.isAnimating();
    }

    /**
     * Returns true if the animation has completed and the popup is fully visible.
     */
    public boolean isFullyVisible() {
        return isVisible && !isAnimating() && backgroundAlpha.isAtTarget();
    }

    /**
     * Returns true if the animation has completed and the popup is fully hidden.
     */
    public boolean isFullyHidden() {
        return !isVisible && !isAnimating() && backgroundAlpha.isAtTarget();
    }

    /**
     * Applies an animated background color with proper alpha.
     * Multiplies the original alpha by the animation progress.
     *
     * @param baseColor The base background color (ARGB format)
     * @return The background color with animated alpha applied
     */
    public int getAnimatedBackgroundColor(int baseColor) {
        float animationProgress = getBackgroundAlpha();

        // Extract the original alpha from the base color (unsigned shift)
        int originalAlpha = (baseColor >>> 24) & 0xFF;

        // Multiply the original alpha by animation progress
        int animatedAlpha = (int) (originalAlpha * animationProgress);

        // Combine the animated alpha with the RGB values
        return (animatedAlpha << 24) | (baseColor & 0x00FFFFFF);
    }

    /**
     * Calculates animated popup position and size centered on screen.
     *
     * @param screenWidth The width of the screen
     * @param screenHeight The height of the screen
     * @param popupWidth The width of the popup
     * @param popupHeight The height of the popup
     * @return Array of [x, y, width, height] with scale applied, centered on screen
     */
    public int[] getScaledPopupBounds(int screenWidth, int screenHeight, int popupWidth, int popupHeight) {
        float scale = getPopupScale();
        int scaledWidth = (int) (popupWidth * scale);
        int scaledHeight = (int) (popupHeight * scale);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int scaledX = centerX - scaledWidth / 2;
        int scaledY = centerY - scaledHeight / 2;
        return new int[]{scaledX, scaledY, scaledWidth, scaledHeight};
    }

    /**
     * Calculates animated popup position with scaling from top-left origin.
     *
     * @param x The X position of the popup (top-left)
     * @param y The Y position of the popup (top-left)
     * @param width The width of the popup
     * @param height The height of the popup
     * @return Array of [x, y, width, height] with scale applied from center
     */
    public int[] getScaledPopupBoundsFromTopLeft(int x, int y, int width, int height) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        return getScaledPopupBounds(centerX, centerY, width, height);
    }
}
