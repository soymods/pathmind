package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Reusable animated button component with multiple style variants.
 * Features smooth hover transitions and consistent styling across the UI.
 */
public class StyledButton {

    /**
     * Button style variants.
     */
    public enum Style {
        /** Default grey button */
        DEFAULT,
        /** Primary accent-colored button */
        PRIMARY,
        /** Uses the current accent color */
        ACCENT,
        /** Red danger/warning button */
        DANGER
    }

    private Style style;
    private String label;
    private int x, y, width, height;
    private final AnimatedValue hoverProgress;
    private boolean wasHovered = false;
    private boolean enabled = true;

    // Optional: custom colors that override style
    private Integer customBgColor = null;
    private Integer customHoverColor = null;
    private Integer customBorderColor = null;
    private int accentColor = UITheme.ACCENT_DEFAULT;

    /**
     * Creates a styled button with the given parameters.
     */
    public StyledButton(String label, int x, int y, int width, int height, Style style) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.style = style;
        this.hoverProgress = AnimatedValue.forHover();
    }

    /**
     * Creates a default styled button.
     */
    public StyledButton(String label, int x, int y, int width, int height) {
        this(label, x, y, width, height, Style.DEFAULT);
    }

    /**
     * Creates a button with default dimensions.
     */
    public StyledButton(String label, Style style) {
        this(label, 0, 0, 80, UITheme.BUTTON_HEIGHT, style);
    }

    /**
     * Sets the button position.
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the button bounds.
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the accent color used for ACCENT and PRIMARY styles.
     */
    public void setAccentColor(int color) {
        this.accentColor = color;
    }

    /**
     * Sets custom colors that override the style defaults.
     */
    public void setCustomColors(int bgColor, int hoverColor, int borderColor) {
        this.customBgColor = bgColor;
        this.customHoverColor = hoverColor;
        this.customBorderColor = borderColor;
    }

    /**
     * Clears custom colors and uses style defaults.
     */
    public void clearCustomColors() {
        this.customBgColor = null;
        this.customHoverColor = null;
        this.customBorderColor = null;
    }

    /**
     * Renders the button.
     */
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        // Update animation
        hoverProgress.tick();

        boolean isHovered = enabled && contains(mouseX, mouseY);
        if (isHovered != wasHovered) {
            hoverProgress.animateTo(isHovered ? 1f : 0f, UITheme.HOVER_ANIM_MS);
            wasHovered = isHovered;
        }

        float hoverProg = hoverProgress.getValue();

        // Calculate colors
        int bgNormal = getBackgroundColor();
        int bgHover = getHoverColor();
        int bgColor = AnimationHelper.lerpColor(bgNormal, bgHover, hoverProg);

        int borderNormal = getBorderColor();
        int borderHover = UITheme.BUTTON_HOVER_OUTLINE;
        int borderColor = AnimationHelper.lerpColor(borderNormal, borderHover, hoverProg);

        // Apply disabled state
        if (!enabled) {
            bgColor = AnimationHelper.darken(bgColor, 0.6f);
            borderColor = AnimationHelper.darken(borderColor, 0.6f);
        }

        int innerBorder = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, hoverProg * 0.4f);
        UIStyleHelper.drawBeveledPanel(context, x, y, width, height, bgColor, borderColor, innerBorder);

        // Render text
        int textColor = enabled ? UITheme.TEXT_PRIMARY : UITheme.TEXT_TERTIARY;
        int textX = x + width / 2;
        int textY = y + (height - textRenderer.fontHeight) / 2 + 1;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), textX, textY, textColor);
    }

    /**
     * Handles mouse clicks.
     *
     * @return true if the button was clicked
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        return enabled && contains((int) mouseX, (int) mouseY);
    }

    /**
     * Checks if coordinates are within button bounds.
     */
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    private int getBackgroundColor() {
        if (customBgColor != null) return customBgColor;

        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_BG;
            case PRIMARY -> UITheme.BUTTON_PRIMARY_BG;
            case ACCENT -> AnimationHelper.darken(accentColor, 0.6f);
            case DANGER -> UITheme.BUTTON_DANGER_BG;
        };
    }

    private int getHoverColor() {
        if (customHoverColor != null) return customHoverColor;

        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_HOVER;
            case PRIMARY -> UITheme.BUTTON_PRIMARY_HOVER;
            case ACCENT -> AnimationHelper.darken(accentColor, 0.5f);
            case DANGER -> UITheme.BUTTON_DANGER_HOVER;
        };
    }

    private int getBorderColor() {
        if (customBorderColor != null) return customBorderColor;

        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_BORDER;
            case PRIMARY -> AnimationHelper.darken(UITheme.BUTTON_PRIMARY_BG, 0.7f);
            case ACCENT -> AnimationHelper.darken(accentColor, 0.4f);
            case DANGER -> AnimationHelper.darken(UITheme.BUTTON_DANGER_BG, 0.7f);
        };
    }

    // Getters and setters

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Static helper to render a button without creating an instance.
     * Useful for simple one-off buttons where state tracking isn't needed.
     */
    public static void renderSimple(DrawContext context, TextRenderer textRenderer,
                                    int x, int y, int width, int height,
                                    String label, Style style, boolean hovered, int accentColor) {
        int bgColor = hovered ? getStaticHoverColor(style, accentColor) : getStaticBgColor(style, accentColor);
        int borderColor = getStaticBorderColor(style, accentColor);
        if (hovered) {
            borderColor = UITheme.BUTTON_HOVER_OUTLINE;
        }

        UIStyleHelper.drawBeveledPanel(context, x, y, width, height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);

        int textX = x + width / 2;
        int textY = y + (height - textRenderer.fontHeight) / 2 + 1;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), textX, textY, UITheme.TEXT_PRIMARY);
    }

    private static int getStaticBgColor(Style style, int accentColor) {
        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_BG;
            case PRIMARY -> UITheme.BUTTON_PRIMARY_BG;
            case ACCENT -> AnimationHelper.darken(accentColor, 0.6f);
            case DANGER -> UITheme.BUTTON_DANGER_BG;
        };
    }

    private static int getStaticHoverColor(Style style, int accentColor) {
        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_HOVER;
            case PRIMARY -> UITheme.BUTTON_PRIMARY_HOVER;
            case ACCENT -> AnimationHelper.darken(accentColor, 0.5f);
            case DANGER -> UITheme.BUTTON_DANGER_HOVER;
        };
    }

    private static int getStaticBorderColor(Style style, int accentColor) {
        return switch (style) {
            case DEFAULT -> UITheme.BUTTON_DEFAULT_BORDER;
            case PRIMARY -> AnimationHelper.darken(UITheme.BUTTON_PRIMARY_BG, 0.7f);
            case ACCENT -> AnimationHelper.darken(accentColor, 0.4f);
            case DANGER -> AnimationHelper.darken(UITheme.BUTTON_DANGER_BG, 0.7f);
        };
    }
}
