package com.pathmind.ui.control;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
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
