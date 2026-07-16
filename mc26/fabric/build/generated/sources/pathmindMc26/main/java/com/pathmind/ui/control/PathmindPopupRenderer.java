package com.pathmind.ui.control;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

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

    public static void drawTextWithEllipsis(GuiGraphicsExtractor context, Font textRenderer,
                                            String text, int x, int y, int maxWidth, int color) {
        if (context == null || textRenderer == null) {
            return;
        }
        String display = TextRenderUtil.trimWithEllipsis(textRenderer, text, maxWidth);
        context.text(textRenderer, Component.literal(display), x, y, color);
    }

    public static void drawCenteredTextWithEllipsis(GuiGraphicsExtractor context, Font textRenderer,
                                                    String text, int centerX, int y, int maxWidth, int color) {
        if (context == null || textRenderer == null) {
            return;
        }
        String display = TextRenderUtil.trimWithEllipsis(textRenderer, text, maxWidth);
        context.centeredText(textRenderer, Component.literal(display), centerX, y, color);
    }

    public static void drawButton(GuiGraphicsExtractor context, Font textRenderer,
                                  int x, int y, int width, int height,
                                  Component label, ButtonStyle style, float hoverProgress,
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
        context.centeredText(
            textRenderer,
            label,
            x + width / 2,
            y + (height - textRenderer.lineHeight) / 2 + 1,
            animatedColor(animation, palette.textColor())
        );
    }

    public static boolean drawButton(GuiGraphicsExtractor context, Font textRenderer,
                                     PathmindPopupLayout.Rect bounds, int mouseX, int mouseY,
                                     Component label, ButtonStyle style, int accentColor,
                                     PopupAnimationHandler animation) {
        boolean hovered = bounds != null && bounds.contains(mouseX, mouseY);
        if (bounds != null) {
            drawButton(
                context,
                textRenderer,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                label,
                style,
                hovered ? 1f : 0f,
                accentColor,
                animation
            );
        }
        return hovered;
    }

    public static void drawAnimatedActionButton(GuiGraphicsExtractor context, Font textRenderer,
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
        int textX = x + (width - textRenderer.width(label)) / 2;
        int textY = y + (height - textRenderer.lineHeight) / 2;
        context.text(textRenderer, Component.literal(label), textX, textY,
            animatedColor(animation, palette.textColor()));
    }

    public static String buttonHoverKey(ButtonStyle style, Component label, int x, int y, int width, int height) {
        String labelText = label == null ? "" : label.getString();
        return "popup-button:" + style + ":" + labelText + ":" + x + ":" + y + ":" + width + ":" + height;
    }

    public static void drawContainer(GuiGraphicsExtractor context, int x, int y, int width, int height,
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

    public static boolean beginPopup(GuiGraphicsExtractor context, int x, int y, int width, int height,
                                     PopupAnimationHandler animation) {
        drawContainer(context, x, y, width, height, animation);
        return enableScissor(context, x, y, width, height);
    }

    public static void drawTitle(GuiGraphicsExtractor context, Font textRenderer,
                                 Component title, int popupX, int popupY, int popupWidth,
                                 PopupAnimationHandler animation) {
        context.centeredText(
            textRenderer,
            title,
            popupX + popupWidth / 2,
            popupY + 14,
            animatedColor(animation, UITheme.TEXT_PRIMARY)
        );
    }

    public static void drawHeaderBar(GuiGraphicsExtractor context, Font textRenderer,
                                     Component title, int popupX, int popupY, int popupWidth,
                                     PopupAnimationHandler animation) {
        context.text(
            textRenderer,
            title,
            popupX + 12,
            popupY + 10,
            animatedColor(animation, UITheme.TEXT_HEADER)
        );
        context.horizontalLine(
            popupX,
            popupX + popupWidth - 1,
            popupY + 28,
            animatedColor(animation, UITheme.BORDER_SUBTLE)
        );
    }

    public static void drawInputFrame(GuiGraphicsExtractor context, int x, int y, int width, int height,
                                      int borderColor, PopupAnimationHandler animation) {
        UIStyleHelper.drawFieldFrame(context, x, y, width, height, new UIStyleHelper.FieldPalette(
            animatedColor(animation, UITheme.RENAME_INPUT_BG),
            animatedColor(animation, borderColor),
            animatedColor(animation, UITheme.PANEL_INNER_BORDER),
            animatedColor(animation, UITheme.TEXT_PRIMARY),
            animatedColor(animation, UITheme.TEXT_TERTIARY)
        ));
    }

    public static void drawPopupFieldFrame(GuiGraphicsExtractor context, int x, int y, int width, int height,
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

    public static int drawPopupTextFieldRow(GuiGraphicsExtractor context, Font textRenderer, EditBox field,
                                            int mouseX, int mouseY, int x, int y, int width,
                                            String label, int accentColor, PopupAnimationHandler animation) {
        context.text(textRenderer, Component.literal(label == null ? "" : label), x, y,
            animatedColor(animation, UITheme.TEXT_LABEL));
        int fieldY = y + 11;
        boolean hovered = UiHitTest.containsHalfOpen(mouseX, mouseY, x, fieldY, width, 18);
        boolean focused = field != null && field.isFocused();
        drawPopupFieldFrame(context, x, fieldY, width, 18, hovered, focused, accentColor, animation);
        if (field != null) {
            field.setPosition(x + 6, fieldY);
            field.setWidth(width - 12);
            field.setHeight(18);
            field.extractRenderState(context, mouseX, mouseY, 0f);
        }
        return fieldY + 18;
    }

    public static void drawCheckbox(GuiGraphicsExtractor context, int x, int y, boolean checked, boolean hovered,
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

    public static void drawDropdownChevron(GuiGraphicsExtractor context, int x, int y, int color, boolean open) {
        if (open) {
            context.horizontalLine(x, x + 4, y + 2, color);
            context.horizontalLine(x + 1, x + 3, y + 1, color);
            context.horizontalLine(x + 2, x + 2, y, color);
            return;
        }
        context.horizontalLine(x, x + 4, y, color);
        context.horizontalLine(x + 1, x + 3, y + 1, color);
        context.horizontalLine(x + 2, x + 2, y + 2, color);
    }

    public static void drawCloseIcon(GuiGraphicsExtractor context, int x, int y, int color) {
        context.fill(x + 2, y + 2, x + 4, y + 4, color);
        context.fill(x + 7, y + 2, x + 9, y + 4, color);
        context.fill(x + 4, y + 4, x + 7, y + 7, color);
        context.fill(x + 2, y + 7, x + 4, y + 9, color);
        context.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    public static int drawStatusBadge(GuiGraphicsExtractor context, Font textRenderer, int x, int y,
                                      String label, int accentColor, PopupAnimationHandler animation) {
        String text = label == null ? "" : label;
        int width = Math.max(26, textRenderer.width(text) + 10);
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
        context.text(textRenderer, Component.literal(text), x + 5, y + 2,
            animatedColor(animation, UITheme.TEXT_PRIMARY));
        return width;
    }

    public static void drawPopupTextField(GuiGraphicsExtractor context, EditBox field,
                                          int mouseX, int mouseY, float delta,
                                          int x, int y, int width, int height,
                                          int borderColor, PopupAnimationHandler animation,
                                          int editableColor, int uneditableColor,
                                          int verticalPadding) {
        drawInputFrame(context, x, y, width, height, borderColor, animation);
        renderTextField(context, field, mouseX, mouseY, delta, x, y, width, height,
            animatedColor(animation, editableColor), animatedColor(animation, uneditableColor), verticalPadding);
    }

    public static void drawPaletteTextField(GuiGraphicsExtractor context, EditBox field,
                                            int mouseX, int mouseY, float delta,
                                            int x, int y, int width, int height,
                                            UIStyleHelper.FieldPalette palette,
                                            int editableColor, int uneditableColor,
                                            int verticalPadding) {
        UIStyleHelper.drawFieldFrame(context, x, y, width, height, palette);
        renderTextField(context, field, mouseX, mouseY, delta, x, y, width, height,
            editableColor, uneditableColor, verticalPadding);
    }

    public static void renderTextField(GuiGraphicsExtractor context, EditBox field,
                                       int mouseX, int mouseY, float delta,
                                       int x, int y, int width, int height,
                                       int editableColor, int uneditableColor,
                                       int verticalPadding) {
        if (field == null) {
            return;
        }
        field.setVisible(true);
        field.setEditable(true);
        field.setTextColor(editableColor);
        field.setTextColorUneditable(uneditableColor);
        int textFieldHeight = Math.max(10, height - verticalPadding * 2);
        field.setPosition(x + 4, y + verticalPadding);
        field.setWidth(width - 8);
        field.setHeight(textFieldHeight);
        field.extractRenderState(context, mouseX, mouseY, delta);
    }

    public static int animatedColor(PopupAnimationHandler animation, int baseColor) {
        return animation == null ? baseColor : animation.getAnimatedPopupColor(baseColor);
    }

    public static boolean enableScissor(GuiGraphicsExtractor context, int popupX, int popupY, int scaledWidth, int scaledHeight) {
        int width = Math.max(1, scaledWidth);
        int height = Math.max(1, scaledHeight);
        context.enableScissor(popupX, popupY, popupX + width, popupY + height);
        return true;
    }

    public static void enableBodyScissor(GuiGraphicsExtractor context, PathmindPopupLayout.Rect bounds) {
        context.enableScissor(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height());
    }

    public static void drawScrollableBodyChrome(GuiGraphicsExtractor context, PathmindPopupLayout.Rect bodyBounds,
                                                int scrollOffset, int maxScroll, int dividerColor) {
        ScrollbarHelper.renderCutoffDividers(
            context,
            bodyBounds.x(),
            bodyBounds.x() + bodyBounds.width() - 1,
            bodyBounds.y(),
            bodyBounds.y() + bodyBounds.height(),
            scrollOffset,
            maxScroll,
            dividerColor
        );
    }

    public static void disableScissor(GuiGraphicsExtractor context, boolean enabled) {
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
