package com.pathmind.ui.control;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Shared popup chrome renderer for Pathmind screens.
 */
public final class PathmindPopupRenderer {
    public enum ButtonStyle {
        DEFAULT,
        PRIMARY,
        ACCENT
    }

    private PathmindPopupRenderer() {
    }

    public static void drawTextWithEllipsis(DrawContext context, TextRenderer textRenderer,
                                            String text, int x, int y, int maxWidth, int color) {
        if (context == null || textRenderer == null) {
            return;
        }
        String display = TextRenderUtil.trimWithEllipsis(textRenderer, text, maxWidth);
        context.drawTextWithShadow(textRenderer, Text.literal(display), x, y, color);
    }

    public static void drawCenteredTextWithEllipsis(DrawContext context, TextRenderer textRenderer,
                                                    String text, int centerX, int y, int maxWidth, int color) {
        if (context == null || textRenderer == null) {
            return;
        }
        String display = TextRenderUtil.trimWithEllipsis(textRenderer, text, maxWidth);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(display), centerX, y, color);
    }

    public static void drawButton(DrawContext context, TextRenderer textRenderer,
                                  int x, int y, int width, int height,
                                  Text label, ButtonStyle style, float hoverProgress,
                                  int accentColor, PopupAnimationHandler animation) {
        if (context == null || textRenderer == null || label == null) {
            return;
        }
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(
            mapButtonStyle(style),
            accentColor,
            hoverProgress,
            false
        );
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            animatedColor(animation, palette.backgroundColor()),
            animatedColor(animation, palette.borderColor()),
            animatedColor(animation, palette.innerBorderColor())
        );
        context.drawCenteredTextWithShadow(
            textRenderer,
            label,
            x + width / 2,
            y + (height - textRenderer.fontHeight) / 2 + 1,
            animatedColor(animation, palette.textColor())
        );
    }

    public static void drawAnimatedActionButton(DrawContext context, TextRenderer textRenderer,
                                                int x, int y, int width, int height,
                                                String label, boolean disabled, float hoverProgress,
                                                int accentColor, PopupAnimationHandler animation) {
        if (context == null || textRenderer == null || label == null) {
            return;
        }
        float easedHover = AnimationHelper.easeOutQuad(Math.max(0f, Math.min(1f, hoverProgress)));
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(
            UIStyleHelper.TextButtonStyle.DEFAULT,
            accentColor,
            easedHover,
            disabled
        );
        if (!disabled && easedHover > 0.001f) {
            int glowColor = animatedColor(animation, AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, 0.22f));
            int alpha = Math.min(84, Math.round(72f * easedHover * (animation == null ? 1f : animation.getPopupAlpha())));
            context.fill(x - 1, y - 1, x + width + 1, y + height + 1, (alpha << 24) | (glowColor & 0x00FFFFFF));
        }
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            y,
            width,
            height,
            animatedColor(animation, palette.backgroundColor()),
            animatedColor(animation, palette.borderColor()),
            animatedColor(animation, palette.innerBorderColor())
        );
        int textX = x + (width - textRenderer.getWidth(label)) / 2;
        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY,
            animatedColor(animation, palette.textColor()));
    }

    public static String buttonHoverKey(ButtonStyle style, Text label, int x, int y, int width, int height) {
        String labelText = label == null ? "" : label.getString();
        return "popup-button:" + style + ":" + labelText + ":" + x + ":" + y + ":" + width + ":" + height;
    }

    public static void drawContainer(DrawContext context, int x, int y, int width, int height,
                                     PopupAnimationHandler animation) {
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            animatedColor(animation, UITheme.BACKGROUND_SECONDARY),
            animatedColor(animation, UITheme.BORDER_DEFAULT),
            animatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );
    }

    public static void drawInputFrame(DrawContext context, int x, int y, int width, int height,
                                      int borderColor, PopupAnimationHandler animation) {
        UIStyleHelper.drawFieldFrame(context, x, y, width, height, new UIStyleHelper.FieldPalette(
            animatedColor(animation, UITheme.RENAME_INPUT_BG),
            animatedColor(animation, borderColor),
            animatedColor(animation, UITheme.PANEL_INNER_BORDER),
            animatedColor(animation, UITheme.TEXT_PRIMARY),
            animatedColor(animation, UITheme.TEXT_TERTIARY)
        ));
    }

    public static void drawPopupFieldFrame(DrawContext context, int x, int y, int width, int height,
                                           boolean hovered, boolean focused, int accentColor,
                                           PopupAnimationHandler animation) {
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            y,
            width,
            height,
            animatedColor(animation, UITheme.BACKGROUND_SECTION),
            animatedColor(animation, focused || hovered ? accentColor : UITheme.BORDER_SUBTLE),
            animatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );
    }

    public static int drawPopupTextFieldRow(DrawContext context, TextRenderer textRenderer, TextFieldWidget field,
                                            int mouseX, int mouseY, int x, int y, int width,
                                            String label, int accentColor, PopupAnimationHandler animation) {
        context.drawTextWithShadow(textRenderer, Text.literal(label == null ? "" : label), x, y,
            animatedColor(animation, UITheme.TEXT_LABEL));
        int fieldY = y + 11;
        boolean hovered = mouseX >= x && mouseY >= fieldY && mouseX < x + width && mouseY < fieldY + 18;
        boolean focused = field != null && field.isFocused();
        drawPopupFieldFrame(context, x, fieldY, width, 18, hovered, focused, accentColor, animation);
        if (field != null) {
            field.setPosition(x + 6, fieldY + 5);
            field.setWidth(width - 12);
            field.render(context, mouseX, mouseY, 0f);
        }
        return fieldY + 18;
    }

    public static void drawCheckbox(DrawContext context, int x, int y, boolean checked, boolean hovered,
                                    int accentColor, PopupAnimationHandler animation) {
        context.fill(x, y, x + 10, y + 10, animatedColor(animation, UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, x, y, 10, 10,
            animatedColor(animation, hovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (!checked) {
            return;
        }
        int checkColor = animatedColor(animation, accentColor);
        context.fill(x + 2, y + 5, x + 3, y + 7, checkColor);
        context.fill(x + 3, y + 6, x + 4, y + 8, checkColor);
        context.fill(x + 4, y + 6, x + 5, y + 7, checkColor);
        context.fill(x + 5, y + 5, x + 6, y + 6, checkColor);
        context.fill(x + 6, y + 4, x + 7, y + 5, checkColor);
        context.fill(x + 7, y + 3, x + 8, y + 4, checkColor);
    }

    public static void drawDropdownChevron(DrawContext context, int x, int y, int color, boolean open) {
        if (open) {
            context.drawHorizontalLine(x, x + 4, y + 2, color);
            context.drawHorizontalLine(x + 1, x + 3, y + 1, color);
            context.drawHorizontalLine(x + 2, x + 2, y, color);
            return;
        }
        context.drawHorizontalLine(x, x + 4, y, color);
        context.drawHorizontalLine(x + 1, x + 3, y + 1, color);
        context.drawHorizontalLine(x + 2, x + 2, y + 2, color);
    }

    public static void drawCloseIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 2, y + 2, x + 4, y + 4, color);
        context.fill(x + 7, y + 2, x + 9, y + 4, color);
        context.fill(x + 4, y + 4, x + 7, y + 7, color);
        context.fill(x + 2, y + 7, x + 4, y + 9, color);
        context.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    public static int drawStatusBadge(DrawContext context, TextRenderer textRenderer, int x, int y,
                                      String label, int accentColor, PopupAnimationHandler animation) {
        String text = label == null ? "" : label;
        int width = Math.max(26, textRenderer.getWidth(text) + 10);
        int height = 12;
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            animatedColor(animation, UITheme.BACKGROUND_SECTION),
            animatedColor(animation, accentColor),
            animatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(textRenderer, Text.literal(text), x + 5, y + 2,
            animatedColor(animation, UITheme.TEXT_PRIMARY));
        return width;
    }

    public static void drawPopupTextField(DrawContext context, TextFieldWidget field,
                                          int mouseX, int mouseY, float delta,
                                          int x, int y, int width, int height,
                                          int borderColor, PopupAnimationHandler animation,
                                          int editableColor, int uneditableColor,
                                          int verticalPadding) {
        drawInputFrame(context, x, y, width, height, borderColor, animation);
        renderTextField(context, field, mouseX, mouseY, delta, x, y, width, height,
            animatedColor(animation, editableColor), animatedColor(animation, uneditableColor), verticalPadding);
    }

    public static void drawPaletteTextField(DrawContext context, TextFieldWidget field,
                                            int mouseX, int mouseY, float delta,
                                            int x, int y, int width, int height,
                                            UIStyleHelper.FieldPalette palette,
                                            int editableColor, int uneditableColor,
                                            int verticalPadding) {
        UIStyleHelper.drawFieldFrame(context, x, y, width, height, palette);
        renderTextField(context, field, mouseX, mouseY, delta, x, y, width, height,
            editableColor, uneditableColor, verticalPadding);
    }

    public static void renderTextField(DrawContext context, TextFieldWidget field,
                                       int mouseX, int mouseY, float delta,
                                       int x, int y, int width, int height,
                                       int editableColor, int uneditableColor,
                                       int verticalPadding) {
        if (field == null) {
            return;
        }
        field.setVisible(true);
        field.setEditable(true);
        field.setEditableColor(editableColor);
        field.setUneditableColor(uneditableColor);
        int textFieldHeight = Math.max(10, height - verticalPadding * 2);
        field.setPosition(x + 4, y + verticalPadding);
        field.setWidth(width - 8);
        field.setHeight(textFieldHeight);
        field.render(context, mouseX, mouseY, delta);
    }

    public static int animatedColor(PopupAnimationHandler animation, int baseColor) {
        return animation == null ? baseColor : animation.getAnimatedPopupColor(baseColor);
    }

    public static boolean enableScissor(DrawContext context, int popupX, int popupY, int scaledWidth, int scaledHeight) {
        int width = Math.max(1, scaledWidth);
        int height = Math.max(1, scaledHeight);
        context.enableScissor(popupX, popupY, popupX + width, popupY + height);
        return true;
    }

    public static void disableScissor(DrawContext context, boolean enabled) {
        if (enabled) {
            context.disableScissor();
        }
    }

    private static UIStyleHelper.TextButtonStyle mapButtonStyle(ButtonStyle style) {
        if (style == null) {
            return UIStyleHelper.TextButtonStyle.DEFAULT;
        }
        return switch (style) {
            case PRIMARY -> UIStyleHelper.TextButtonStyle.PRIMARY;
            case ACCENT -> UIStyleHelper.TextButtonStyle.ACCENT;
            case DEFAULT -> UIStyleHelper.TextButtonStyle.DEFAULT;
        };
    }
}
