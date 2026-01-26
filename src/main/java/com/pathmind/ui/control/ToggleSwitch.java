package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.gui.DrawContext;

/**
 * Animated toggle switch component with smooth on/off transitions.
 * Features a sliding knob that moves between left (off) and right (on) positions,
 * with color transitions between grey (off) and green (on) states.
 */
public class ToggleSwitch {

    private static final int WIDTH = UITheme.TOGGLE_WIDTH;
    private static final int HEIGHT = UITheme.TOGGLE_HEIGHT;
    private static final int KNOB_SIZE = HEIGHT - 4;
    private static final int KNOB_MARGIN = 2;

    private boolean value;
    private final AnimatedValue knobPosition;  // 0.0 = left (off), 1.0 = right (on)
    private final AnimatedValue colorProgress; // For smooth color transition
    private final AnimatedValue hoverProgress; // For hover effect

    private int x, y;
    private boolean wasHovered = false;

    /**
     * Creates a toggle switch with the specified initial state.
     */
    public ToggleSwitch(boolean initialValue) {
        this.value = initialValue;
        this.knobPosition = AnimatedValue.forToggle(initialValue);
        this.colorProgress = AnimatedValue.forToggle(initialValue);
        this.hoverProgress = AnimatedValue.forHover();
    }

    /**
     * Creates a toggle switch in the OFF state.
     */
    public ToggleSwitch() {
        this(false);
    }

    /**
     * Sets the position where the toggle will be rendered.
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Renders the toggle switch.
     *
     * @param context The draw context
     * @param mouseX Current mouse X position
     * @param mouseY Current mouse Y position
     */
    public void render(DrawContext context, int mouseX, int mouseY) {
        // Update animation states
        knobPosition.tick();
        colorProgress.tick();
        hoverProgress.tick();

        boolean isHovered = contains(mouseX, mouseY);
        if (isHovered != wasHovered) {
            hoverProgress.animateTo(isHovered ? 1f : 0f, UITheme.FAST_ANIM_MS);
            wasHovered = isHovered;
        }

        // Calculate colors based on animation progress
        float colorProg = colorProgress.getValue();
        float hoverProg = hoverProgress.getValue();

        // Background track color (interpolate between off and on colors)
        int trackOffColor = AnimationHelper.lerpColor(
            UITheme.TOGGLE_OFF_BG,
            AnimationHelper.brighten(UITheme.TOGGLE_OFF_BG, 1.1f),
            hoverProg
        );
        int trackOnColor = AnimationHelper.lerpColor(
            UITheme.TOGGLE_ON_BG,
            AnimationHelper.brighten(UITheme.TOGGLE_ON_BG, 1.1f),
            hoverProg
        );
        int trackColor = AnimationHelper.lerpColor(trackOffColor, trackOnColor, colorProg);

        // Border color
        int borderOffColor = UITheme.TOGGLE_OFF_BORDER;
        int borderOnColor = UITheme.TOGGLE_ON_BORDER;
        int borderColor = AnimationHelper.lerpColor(borderOffColor, borderOnColor, colorProg);

        // Render track background
        context.fill(x, y, x + WIDTH, y + HEIGHT, trackColor);

        // Render border
        DrawContextBridge.drawBorder(context, x, y, WIDTH, HEIGHT, borderColor);

        // Calculate knob position
        float knobProg = knobPosition.getValue();
        int maxKnobTravel = WIDTH - KNOB_SIZE - (KNOB_MARGIN * 2);
        int knobX = x + KNOB_MARGIN + (int) (maxKnobTravel * knobProg);
        int knobY = y + KNOB_MARGIN;

        // Knob color (slightly brighter on hover)
        int knobColor = AnimationHelper.lerpColor(
            UITheme.TOGGLE_KNOB,
            AnimationHelper.darken(UITheme.TOGGLE_KNOB, 0.9f),
            hoverProg * 0.3f
        );

        // Render knob with subtle shadow effect
        // Shadow (1px offset, darker)
        context.fill(knobX + 1, knobY + 1, knobX + KNOB_SIZE + 1, knobY + KNOB_SIZE + 1,
            AnimationHelper.withAlpha(0xFF000000, 0.2f));
        // Main knob
        context.fill(knobX, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE, knobColor);
    }

    /**
     * Handles mouse clicks on the toggle switch.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return true if the click was handled (toggle was clicked)
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (contains((int) mouseX, (int) mouseY)) {
            toggle();
            return true;
        }
        return false;
    }

    /**
     * Toggles the switch state with animation.
     */
    public void toggle() {
        value = !value;
        knobPosition.animateTo(value ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        colorProgress.animateTo(value ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
    }

    /**
     * Sets the value without animation.
     */
    public void setValue(boolean newValue) {
        if (this.value != newValue) {
            this.value = newValue;
            knobPosition.setValue(newValue ? 1f : 0f);
            colorProgress.setValue(newValue ? 1f : 0f);
        }
    }

    /**
     * Sets the value with animation.
     */
    public void setValueAnimated(boolean newValue) {
        if (this.value != newValue) {
            this.value = newValue;
            knobPosition.animateTo(newValue ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
            colorProgress.animateTo(newValue ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        }
    }

    /**
     * Gets the current value.
     */
    public boolean getValue() {
        return value;
    }

    /**
     * Checks if the given coordinates are within the toggle bounds.
     */
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + WIDTH &&
               mouseY >= y && mouseY <= y + HEIGHT;
    }

    /**
     * Gets the width of the toggle switch.
     */
    public int getWidth() {
        return WIDTH;
    }

    /**
     * Gets the height of the toggle switch.
     */
    public int getHeight() {
        return HEIGHT;
    }

    /**
     * Returns true if currently animating.
     */
    public boolean isAnimating() {
        return knobPosition.isAnimating() || colorProgress.isAnimating();
    }
}
