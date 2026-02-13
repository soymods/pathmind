package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.gui.DrawContext;

/**
 * Toggle switch component with red/green rectangular indicator style.
 * Shows two adjacent squares - a red indicator (off) and green indicator (on),
 * with the active state highlighted and the inactive state dimmed.
 */
public class ToggleSwitch {

    private static final int WIDTH = UITheme.TOGGLE_WIDTH;
    private static final int HEIGHT = UITheme.TOGGLE_HEIGHT;
    private static final int INDICATOR_SIZE = HEIGHT - 2;

    private boolean value;
    private final AnimatedValue colorProgress; // For smooth color transition
    private final AnimatedValue hoverProgress; // For hover effect

    private int x, y;
    private boolean wasHovered = false;

    /**
     * Creates a toggle switch with the specified initial state.
     */
    public ToggleSwitch(boolean initialValue) {
        this.value = initialValue;
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
     * Renders the toggle switch as two adjacent red/green rectangular indicators.
     */
    public void render(DrawContext context, int mouseX, int mouseY) {
        // Update animation states
        colorProgress.tick();
        hoverProgress.tick();

        boolean isHovered = contains(mouseX, mouseY);
        if (isHovered != wasHovered) {
            hoverProgress.animateTo(isHovered ? 1f : 0f, UITheme.FAST_ANIM_MS);
            wasHovered = isHovered;
        }

        float colorProg = colorProgress.getValue();
        float hoverProg = hoverProgress.getValue();

        // Background track
        int trackColor = AnimationHelper.lerpColor(UITheme.TOGGLE_TRACK, UITheme.TOGGLE_TRACK_HOVER, hoverProg);
        UIStyleHelper.drawBeveledPanel(context, x, y, WIDTH, HEIGHT, trackColor, UITheme.BORDER_DEFAULT, UITheme.PANEL_INNER_BORDER);

        // Red indicator (left side) - bright when OFF, dim when ON
        int redX = x + 1;
        int redY = y + 1;
        float redAlpha = 1.0f - colorProg; // Full when off, faded when on
        int redColor = AnimationHelper.lerpColor(UITheme.TOGGLE_OFF_DIM, UITheme.TOGGLE_OFF_INDICATOR, redAlpha);
        int redBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, UITheme.TOGGLE_OFF_BORDER, redAlpha);
        if (isHovered && !value) {
            redColor = AnimationHelper.brighten(redColor, 1.15f);
        }
        UIStyleHelper.drawBeveledPanel(context, redX, redY, INDICATOR_SIZE, INDICATOR_SIZE, redColor, redBorder, UITheme.PANEL_INNER_BORDER);

        // Green indicator (right side) - bright when ON, dim when OFF
        int greenX = x + WIDTH - INDICATOR_SIZE - 1;
        int greenY = y + 1;
        float greenAlpha = colorProg; // Full when on, faded when off
        int greenColor = AnimationHelper.lerpColor(UITheme.TOGGLE_ON_DIM, UITheme.TOGGLE_ON_INDICATOR, greenAlpha);
        int greenBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, UITheme.TOGGLE_ON_BORDER, greenAlpha);
        if (isHovered && value) {
            greenColor = AnimationHelper.brighten(greenColor, 1.15f);
        }
        UIStyleHelper.drawBeveledPanel(context, greenX, greenY, INDICATOR_SIZE, INDICATOR_SIZE, greenColor, greenBorder, UITheme.PANEL_INNER_BORDER);
    }

    /**
     * Handles mouse clicks on the toggle switch.
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
        colorProgress.animateTo(value ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
    }

    /**
     * Sets the value without animation.
     */
    public void setValue(boolean newValue) {
        if (this.value != newValue) {
            this.value = newValue;
            colorProgress.setValue(newValue ? 1f : 0f);
        }
    }

    /**
     * Sets the value with animation.
     */
    public void setValueAnimated(boolean newValue) {
        if (this.value != newValue) {
            this.value = newValue;
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
        return colorProgress.isAnimating();
    }
}
