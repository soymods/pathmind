package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Shared renderer for compact settings rows in Pathmind screens.
 */
public final class PathmindSettingsRowRenderer {
    private PathmindSettingsRowRenderer() {
    }

    public static void renderToggleRow(DrawContext context, TextRenderer textRenderer,
                                       int mouseX, int mouseY,
                                       int labelX, int centerY, String label,
                                       boolean active, int popupX, int popupWidth,
                                       int toggleWidth, int toggleHeight,
                                       int accentColor, PopupAnimationHandler animation,
                                       String onLabel, String offLabel) {
        int labelY = centerY - textRenderer.fontHeight / 2;
        int toggleX = popupX + popupWidth - toggleWidth - 20;
        int toggleY = centerY - toggleHeight / 2;
        int maxLabelWidth = Math.max(0, toggleX - labelX - 8);
        PathmindPopupRenderer.drawTextWithEllipsis(
            context,
            textRenderer,
            label,
            labelX,
            labelY,
            maxLabelWidth,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_SECONDARY)
        );

        boolean hovered = isPointInRect(mouseX, mouseY, toggleX, toggleY, toggleWidth, toggleHeight);
        PathmindPopupRenderer.drawButton(
            context,
            textRenderer,
            toggleX,
            toggleY,
            toggleWidth,
            toggleHeight,
            Text.literal(active ? onLabel : offLabel),
            active ? PathmindPopupRenderer.ButtonStyle.PRIMARY : PathmindPopupRenderer.ButtonStyle.DEFAULT,
            hovered ? 1f : 0f,
            accentColor,
            animation
        );
    }

    public static void renderSliderRow(DrawContext context, TextRenderer textRenderer,
                                       int mouseX, int mouseY,
                                       int labelX, int centerY, String label,
                                       int value, int min, int max,
                                       int popupX, int popupWidth,
                                       int sliderWidth, int sliderHeight,
                                       int sliderHandleWidth, int sliderHandleHeight,
                                       int accentColor, PopupAnimationHandler animation,
                                       String unitLabel, boolean handleActive) {
        int labelY = centerY - textRenderer.fontHeight / 2;
        int sliderX = popupX + popupWidth - sliderWidth - 20;
        int sliderY = centerY - sliderHeight / 2;
        String valueText = value + unitLabel;
        int valueTextWidth = textRenderer.getWidth(valueText);
        int valueBoxWidth = Math.max(36, valueTextWidth + 10);
        int valueBoxX = sliderX - valueBoxWidth - 8;
        int valueBoxY = centerY - sliderHeight / 2;
        int valueBoxHeight = sliderHeight;
        int maxLabelWidth = Math.max(0, valueBoxX - labelX - 8);
        PathmindPopupRenderer.drawTextWithEllipsis(
            context,
            textRenderer,
            label,
            labelX,
            labelY,
            maxLabelWidth,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_SECONDARY)
        );

        UIStyleHelper.drawFieldFrame(context, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, new UIStyleHelper.FieldPalette(
            PathmindPopupRenderer.animatedColor(animation, UITheme.DROPDOWN_OPTION_BG),
            PathmindPopupRenderer.animatedColor(animation, UITheme.BORDER_SUBTLE),
            PathmindPopupRenderer.animatedColor(animation, UITheme.PANEL_INNER_BORDER),
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_HEADER),
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_TERTIARY)
        ));
        int valueTextX = valueBoxX + Math.max(4, (valueBoxWidth - valueTextWidth) / 2);
        int valueTextY = valueBoxY + (valueBoxHeight - textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(valueText),
            valueTextX,
            valueTextY,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_HEADER)
        );

        boolean hovered = isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, sliderWidth, sliderHeight + 8);
        UIStyleHelper.SliderPalette sliderPalette = animatedSliderPalette(
            UIStyleHelper.getSliderPalette(accentColor, hovered ? 1f : 0f, false, false),
            animation
        );
        UIStyleHelper.drawSliderTrack(context, sliderX, sliderY, sliderWidth, sliderHeight, sliderPalette);

        int clamped = Math.max(min, Math.min(value, max));
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (sliderWidth - sliderHandleWidth));
        int handleY = centerY - sliderHandleHeight / 2;
        UIStyleHelper.SliderPalette handlePalette = animatedSliderPalette(
            UIStyleHelper.getSliderPalette(accentColor, hovered ? 1f : 0f, handleActive, false),
            animation
        );
        UIStyleHelper.drawSliderHandle(context, handleX, handleY, sliderHandleWidth, sliderHandleHeight, handlePalette);
    }

    public static void renderNumericField(DrawContext context, TextRenderer textRenderer, TextFieldWidget field,
                                          int mouseX, int mouseY,
                                          int labelX, int centerY, String label,
                                          int valueBoxX, int valueBoxY, int valueBoxWidth, int valueBoxHeight,
                                          String valueText, Text unitText,
                                          int accentColor, PopupAnimationHandler animation,
                                          float fieldHoverProgress, boolean focused,
                                          int textFieldVerticalPadding) {
        int labelY = centerY - textRenderer.fontHeight / 2;
        int maxLabelWidth = Math.max(0, valueBoxX - labelX - 8);
        PathmindPopupRenderer.drawTextWithEllipsis(
            context,
            textRenderer,
            label,
            labelX,
            labelY,
            maxLabelWidth,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_SECONDARY)
        );

        int valueBoxBg = AnimationHelper.lerpColor(
            UITheme.DROPDOWN_OPTION_BG,
            focused ? UITheme.BORDER_FOCUS : UITheme.BORDER_SECTION,
            fieldHoverProgress
        );
        int valueBoxBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, fieldHoverProgress);
        valueBoxBg = animation.getAnimatedPopupColor(valueBoxBg);
        valueBoxBorder = animation.getAnimatedPopupColor(valueBoxBorder);
        context.fill(valueBoxX, valueBoxY, valueBoxX + valueBoxWidth, valueBoxY + valueBoxHeight, valueBoxBg);
        DrawContextBridge.drawBorder(context, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, valueBoxBorder);

        if (field != null) {
            if (!focused && !valueText.equals(field.getText())) {
                field.setText(valueText);
            }
            field.setVisible(true);
            field.setEditable(true);
            int textColor = PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_HEADER);
            field.setEditableColor(textColor);
            field.setUneditableColor(textColor);
            int textFieldHeight = Math.max(10, valueBoxHeight - textFieldVerticalPadding * 2);
            field.setPosition(valueBoxX + 4, valueBoxY + textFieldVerticalPadding);
            field.setWidth(valueBoxWidth - 8);
            field.setHeight(textFieldHeight);
            field.render(context, mouseX, mouseY, 0f);
        }

        int unitX = valueBoxX + valueBoxWidth + 6;
        int unitY = valueBoxY + (valueBoxHeight - textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(
            textRenderer,
            unitText,
            unitX,
            unitY,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_SECONDARY)
        );
    }

    public static void renderNumericSlider(DrawContext context, int centerY,
                                           int sliderX, int sliderY, int sliderWidth, int sliderHeight,
                                           int sliderHandleWidth, int sliderHandleHeight,
                                           int value, int min, int max,
                                           int accentColor, PopupAnimationHandler animation,
                                           float sliderHoverProgress) {
        int trackColor = AnimationHelper.lerpColor(UITheme.DROPDOWN_OPTION_BG, UITheme.DROPDOWN_OPTION_HOVER, sliderHoverProgress);
        int trackBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, sliderHoverProgress * 0.45f);
        trackColor = animation.getAnimatedPopupColor(trackColor);
        trackBorder = animation.getAnimatedPopupColor(trackBorder);
        context.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, trackColor);
        DrawContextBridge.drawBorder(context, sliderX, sliderY, sliderWidth, sliderHeight, trackBorder);

        int clamped = Math.max(min, Math.min(value, max));
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (sliderWidth - sliderHandleWidth));
        int handleY = centerY - sliderHandleHeight / 2;
        int handleColor = animation.getAnimatedPopupColor(accentColor);
        int handleBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, sliderHoverProgress);
        handleBorder = PathmindPopupRenderer.animatedColor(animation, handleBorder);
        context.fill(handleX, handleY, handleX + sliderHandleWidth, handleY + sliderHandleHeight, handleColor);
        DrawContextBridge.drawBorder(context, handleX, handleY, sliderHandleWidth, sliderHandleHeight, handleBorder);
    }

    private static UIStyleHelper.SliderPalette animatedSliderPalette(UIStyleHelper.SliderPalette palette,
                                                                     PopupAnimationHandler animation) {
        return new UIStyleHelper.SliderPalette(
            PathmindPopupRenderer.animatedColor(animation, palette.trackColor()),
            PathmindPopupRenderer.animatedColor(animation, palette.trackBorderColor()),
            PathmindPopupRenderer.animatedColor(animation, palette.handleColor()),
            PathmindPopupRenderer.animatedColor(animation, palette.handleBorderColor())
        );
    }

    private static boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
