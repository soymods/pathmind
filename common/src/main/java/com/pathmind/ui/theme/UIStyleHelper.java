package com.pathmind.ui.theme;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared rendering helpers for Pathmind UI chrome.
 * Keeps panel/button framing logic centralized and avoids hardcoded styling in screens/widgets.
 */
public final class UIStyleHelper {

    public enum TextButtonStyle {
        DEFAULT,
        PRIMARY,
        ACCENT,
        DANGER
    }

    public record TextButtonPalette(int backgroundColor, int borderColor, int innerBorderColor, int textColor) {
    }

    public record FieldPalette(int backgroundColor, int borderColor, int innerBorderColor, int textColor, int placeholderColor) {
    }

    public record DropdownRowPalette(int backgroundColor, int borderColor, int textColor) {
    }

    public record ScrollContainerPalette(int backgroundColor, int borderColor, int innerBorderColor, int trackColor, int thumbColor) {
    }

    public record TogglePalette(int backgroundColor, int borderColor, int knobColor) {
    }

    public record SliderPalette(int trackColor, int trackBorderColor, int handleColor, int handleBorderColor) {
    }

    private UIStyleHelper() {
    }

    public static void drawPanel(DrawContext context, int x, int y, int width, int height, int backgroundColor, int borderColor) {
        if (context == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, backgroundColor);
        DrawContextBridge.drawBorder(context, x, y, width, height, borderColor);
    }

    public static void drawBeveledPanel(DrawContext context, int x, int y, int width, int height,
                                        int backgroundColor, int outerBorderColor, int innerBorderColor) {
        drawPanel(context, x, y, width, height, backgroundColor, outerBorderColor);
        if (width > 3 && height > 3) {
            DrawContextBridge.drawBorder(context, x + 1, y + 1, width - 2, height - 2, innerBorderColor);
        }
    }

    public static void drawToolbarButtonFrame(DrawContext context, int x, int y, int width, int height,
                                              int backgroundColor, int outerBorderColor, int innerBorderColor) {
        drawBeveledPanel(context, x, y, width, height, backgroundColor, outerBorderColor, innerBorderColor);
    }

    public static TextButtonPalette getTextButtonPalette(TextButtonStyle style, int accentColor, boolean hovered, boolean disabled) {
        return getTextButtonPalette(style, accentColor, hovered ? 1f : 0f, disabled);
    }

    public static TextButtonPalette getTextButtonPalette(TextButtonStyle style, int accentColor, float hoverProgress, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        int backgroundColor;
        int borderColor;
        int textColor;
        switch (style) {
            case PRIMARY -> {
                backgroundColor = AnimationHelper.lerpColor(
                    AnimationHelper.lerpColor(accentColor, UITheme.BORDER_SOCKET, 0.25f),
                    AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, 0.18f),
                    easedHover
                );
                borderColor = AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, easedHover * 0.2f);
                textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, easedHover);
            }
            case ACCENT -> {
                backgroundColor = AnimationHelper.lerpColor(
                    accentColor,
                    AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, 0.25f),
                    easedHover
                );
                borderColor = AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, easedHover * 0.18f);
                textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, easedHover);
            }
            case DANGER -> {
                backgroundColor = AnimationHelper.lerpColor(UITheme.BUTTON_DANGER_BG, UITheme.BUTTON_DANGER_HOVER, easedHover);
                borderColor = AnimationHelper.lerpColor(UITheme.BORDER_DANGER_MUTED, UITheme.BORDER_DANGER, easedHover);
                textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, easedHover);
            }
            case DEFAULT -> {
                backgroundColor = AnimationHelper.lerpColor(UITheme.BUTTON_ACTIVE_BG, UITheme.BORDER_HIGHLIGHT, easedHover);
                borderColor = AnimationHelper.lerpColor(UITheme.BORDER_HIGHLIGHT, UITheme.TEXT_TERTIARY, easedHover);
                textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, easedHover);
            }
            default -> {
                backgroundColor = UITheme.BUTTON_ACTIVE_BG;
                borderColor = UITheme.BORDER_HIGHLIGHT;
                textColor = UITheme.TEXT_PRIMARY;
            }
        }

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.7f);
            borderColor = AnimationHelper.darken(borderColor, 0.7f);
            textColor = UITheme.TEXT_TERTIARY;
        }

        int innerBorderColor = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, easedHover * 0.25f);
        return new TextButtonPalette(backgroundColor, borderColor, innerBorderColor, textColor);
    }

    public static FieldPalette getSearchFieldPalette(int accentColor, float hoverProgress, boolean focused, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = focused ? 1f : easedHover;

        int backgroundColor = AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, UITheme.BORDER_SECTION, easedHover * 0.45f);
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, emphasis);
        int innerBorderColor = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, emphasis * 0.18f);
        int textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, emphasis * 0.65f);
        int placeholderColor = AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, UITheme.TEXT_SECONDARY, emphasis * 0.35f);

        if (focused) {
            backgroundColor = AnimationHelper.lerpColor(backgroundColor, UITheme.BORDER_FOCUS, 0.28f);
        }

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.7f);
            borderColor = AnimationHelper.darken(borderColor, 0.7f);
            innerBorderColor = AnimationHelper.darken(innerBorderColor, 0.7f);
            textColor = UITheme.TEXT_TERTIARY;
            placeholderColor = UITheme.TEXT_TERTIARY;
        }

        return new FieldPalette(backgroundColor, borderColor, innerBorderColor, textColor, placeholderColor);
    }

    public static FieldPalette getDropdownFieldPalette(int accentColor, float hoverProgress, boolean open, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = open ? 1f : easedHover;

        int backgroundColor = AnimationHelper.lerpColor(UITheme.BACKGROUND_SIDEBAR, UITheme.BACKGROUND_TERTIARY, emphasis);
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_DEFAULT, accentColor, emphasis * 0.9f);
        int innerBorderColor = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, emphasis * 0.22f);
        int textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, emphasis * 0.8f);
        int placeholderColor = AnimationHelper.lerpColor(UITheme.TEXT_SECONDARY, UITheme.TEXT_TERTIARY, 1f - emphasis * 0.35f);

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.72f);
            borderColor = AnimationHelper.darken(borderColor, 0.72f);
            innerBorderColor = AnimationHelper.darken(innerBorderColor, 0.72f);
            textColor = UITheme.TEXT_TERTIARY;
            placeholderColor = UITheme.TEXT_TERTIARY;
        }

        return new FieldPalette(backgroundColor, borderColor, innerBorderColor, textColor, placeholderColor);
    }

    public static FieldPalette getInputFieldPalette(int accentColor, float hoverProgress, boolean active, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = active ? 1f : easedHover;

        int backgroundColor = AnimationHelper.lerpColor(UITheme.BACKGROUND_SIDEBAR, UITheme.NODE_INPUT_BG_ACTIVE, emphasis);
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_DEFAULT, accentColor, emphasis);
        int innerBorderColor = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, emphasis * 0.24f);
        int textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_EDITING, emphasis * 0.9f);
        int placeholderColor = AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, UITheme.TEXT_SECONDARY, 1f - emphasis * 0.4f);

        if (disabled) {
            backgroundColor = AnimationHelper.lerpColor(UITheme.BUTTON_DEFAULT_BG, backgroundColor, 0.25f);
            borderColor = AnimationHelper.darken(borderColor, 0.72f);
            innerBorderColor = AnimationHelper.darken(innerBorderColor, 0.72f);
            textColor = UITheme.TEXT_SECONDARY;
            placeholderColor = UITheme.TEXT_TERTIARY;
        }

        return new FieldPalette(backgroundColor, borderColor, innerBorderColor, textColor, placeholderColor);
    }

    public static DropdownRowPalette getDropdownRowPalette(int accentColor, float hoverProgress, boolean selected, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = selected ? 1f : easedHover;

        int backgroundColor = AnimationHelper.lerpColor(
            UITheme.DROPDOWN_OPTION_BG,
            selected ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.BACKGROUND_TERTIARY,
            emphasis
        );
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, selected ? accentColor : UITheme.BORDER_SECTION, emphasis * 0.85f);
        int textColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, emphasis * 0.8f);

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.72f);
            borderColor = AnimationHelper.darken(borderColor, 0.72f);
            textColor = UITheme.TEXT_TERTIARY;
        }

        return new DropdownRowPalette(backgroundColor, borderColor, textColor);
    }

    public static ScrollContainerPalette getScrollContainerPalette(int accentColor, float hoverProgress, boolean active, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = active ? 1f : easedHover;

        int backgroundColor = AnimationHelper.lerpColor(UITheme.BACKGROUND_SECONDARY, UITheme.BACKGROUND_TERTIARY, emphasis * 0.18f);
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, emphasis * 0.3f);
        int innerBorderColor = AnimationHelper.lerpColor(UITheme.PANEL_INNER_BORDER, UITheme.BUTTON_HOVER_OUTLINE, emphasis * 0.18f);
        int trackColor = AnimationHelper.lerpColor(UITheme.BORDER_DEFAULT, UITheme.BORDER_SECTION, emphasis * 0.3f);
        int thumbColor = AnimationHelper.lerpColor(UITheme.BORDER_HIGHLIGHT, accentColor, emphasis * 0.35f);

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.72f);
            borderColor = AnimationHelper.darken(borderColor, 0.72f);
            innerBorderColor = AnimationHelper.darken(innerBorderColor, 0.72f);
            trackColor = AnimationHelper.darken(trackColor, 0.72f);
            thumbColor = AnimationHelper.darken(thumbColor, 0.72f);
        }

        return new ScrollContainerPalette(backgroundColor, borderColor, innerBorderColor, trackColor, thumbColor);
    }

    public static TogglePalette getTogglePalette(int accentColor, float progress, boolean hovered, boolean disabled) {
        float easedProgress = AnimationHelper.easeInOutCubic(Math.max(0f, Math.min(1f, progress)));
        int onBorder = AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, 0.12f);
        int onBackground = AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, accentColor, 0.28f);

        int backgroundColor = AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, onBackground, easedProgress);
        int borderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, onBorder, easedProgress);
        if (hovered) {
            backgroundColor = AnimationHelper.lerpColor(backgroundColor, UITheme.TEXT_HEADER, 0.06f);
            borderColor = AnimationHelper.lerpColor(borderColor, UITheme.TEXT_HEADER, 0.08f);
        }

        if (disabled) {
            backgroundColor = AnimationHelper.darken(backgroundColor, 0.72f);
            borderColor = AnimationHelper.darken(borderColor, 0.72f);
        }

        return new TogglePalette(backgroundColor, borderColor, UITheme.TOGGLE_KNOB);
    }

    public static SliderPalette getSliderPalette(int accentColor, float hoverProgress, boolean active, boolean disabled) {
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        float emphasis = active ? 1f : easedHover;
        int trackColor = AnimationHelper.lerpColor(UITheme.DROPDOWN_OPTION_BG, UITheme.DROPDOWN_OPTION_HOVER, emphasis);
        int trackBorderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, emphasis * 0.45f);
        int handleColor = accentColor;
        int handleBorderColor = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, emphasis);

        if (disabled) {
            trackColor = AnimationHelper.darken(trackColor, 0.72f);
            trackBorderColor = AnimationHelper.darken(trackBorderColor, 0.72f);
            handleColor = AnimationHelper.darken(handleColor, 0.72f);
            handleBorderColor = AnimationHelper.darken(handleBorderColor, 0.72f);
        }

        return new SliderPalette(trackColor, trackBorderColor, handleColor, handleBorderColor);
    }

    public static void drawFieldFrame(DrawContext context, int x, int y, int width, int height, FieldPalette palette) {
        if (palette == null) {
            return;
        }
        drawBeveledPanel(context, x, y, width, height, palette.backgroundColor(), palette.borderColor(), palette.innerBorderColor());
    }

    public static void drawDropdownRow(DrawContext context, int x, int y, int width, int height, DropdownRowPalette palette) {
        if (palette == null || context == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, palette.backgroundColor());
        DrawContextBridge.drawBorder(context, x, y, width, height, palette.borderColor());
    }

    public static void drawScrollContainer(DrawContext context, int x, int y, int width, int height, ScrollContainerPalette palette) {
        if (palette == null) {
            return;
        }
        drawBeveledPanel(context, x, y, width, height, palette.backgroundColor(), palette.borderColor(), palette.innerBorderColor());
    }

    public static void drawToggleSwitch(DrawContext context, int x, int y, int width, int height, float progress, TogglePalette palette) {
        if (context == null || palette == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, palette.backgroundColor());
        DrawContextBridge.drawBorder(context, x, y, width, height, palette.borderColor());

        int knobWidth = Math.max(1, height - 2);
        int knobTravel = Math.max(0, width - knobWidth - 2);
        int knobLeft = x + 1 + Math.round(knobTravel * Math.max(0f, Math.min(1f, progress)));
        context.fill(knobLeft, y + 1, knobLeft + knobWidth, y + height - 1, palette.knobColor());
    }

    public static void drawSliderTrack(DrawContext context, int x, int y, int width, int height, SliderPalette palette) {
        if (context == null || palette == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, palette.trackColor());
        DrawContextBridge.drawBorder(context, x, y, width, height, palette.trackBorderColor());
    }

    public static void drawSliderHandle(DrawContext context, int x, int y, int width, int height, SliderPalette palette) {
        if (context == null || palette == null || width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, palette.handleColor());
        DrawContextBridge.drawBorder(context, x, y, width, height, palette.handleBorderColor());
    }

    public static void drawChevron(DrawContext context, int centerX, int centerY, boolean expanded, int color) {
        if (expanded) {
            context.drawHorizontalLine(centerX - 3, centerX + 3, centerY - 2, color);
            context.drawHorizontalLine(centerX - 2, centerX + 2, centerY - 1, color);
            context.drawHorizontalLine(centerX - 1, centerX + 1, centerY, color);
            return;
        }
        context.drawHorizontalLine(centerX - 3, centerX + 3, centerY + 1, color);
        context.drawHorizontalLine(centerX - 2, centerX + 2, centerY, color);
        context.drawHorizontalLine(centerX - 1, centerX + 1, centerY - 1, color);
    }

    public static void drawTextCaret(DrawContext context, int caretX, int topY, int bottomY, int color) {
        drawTextCaret(context, caretX, topY, bottomY, caretX + 1, color);
    }

    public static void drawTextCaret(DrawContext context, int caretX, int topY, int bottomY, int maxRight, int color) {
        if (context == null) {
            return;
        }
        int startY = Math.min(topY, bottomY);
        int endY = Math.max(topY, bottomY);
        int endX = Math.min(caretX + 1, maxRight);
        if (endX <= caretX || endY <= startY) {
            return;
        }
        context.fill(caretX, startY, endX, endY, color);
    }

    public static void drawTextCaretAtBaseline(DrawContext context, TextRenderer textRenderer, int caretX, int baselineY, int maxRight, int color) {
        if (textRenderer == null) {
            return;
        }
        int topY = baselineY - textRenderer.fontHeight + 1;
        drawTextCaret(context, caretX, topY, baselineY + 1, maxRight, color);
    }
}
