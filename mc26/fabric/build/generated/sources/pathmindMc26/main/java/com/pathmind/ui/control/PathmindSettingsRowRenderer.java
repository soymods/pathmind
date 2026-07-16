package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Shared renderer for compact settings rows in Pathmind screens.
 */
public final class PathmindSettingsRowRenderer {
    private PathmindSettingsRowRenderer() {
    }

    public static void renderToggleRow(GuiGraphicsExtractor context, Font textRenderer,
                                       int mouseX, int mouseY,
                                       int labelX, int centerY, String label,
                                       boolean active, int popupX, int popupWidth,
                                       int toggleWidth, int toggleHeight,
                                       int accentColor, PopupAnimationHandler animation,
                                       String onLabel, String offLabel) {
        int labelY = centerY - textRenderer.lineHeight / 2;
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
            Component.literal(active ? onLabel : offLabel),
            active ? PathmindPopupRenderer.ButtonStyle.PRIMARY : PathmindPopupRenderer.ButtonStyle.DEFAULT,
            hovered ? 1f : 0f,
            accentColor,
            animation
        );
    }

    public static void renderSliderRow(GuiGraphicsExtractor context, Font textRenderer,
                                       int mouseX, int mouseY,
                                       int labelX, int centerY, String label,
                                       int value, int min, int max,
                                       int popupX, int popupWidth,
                                       int sliderWidth, int sliderHeight,
                                       int sliderHandleWidth, int sliderHandleHeight,
                                       int accentColor, PopupAnimationHandler animation,
                                       String unitLabel, boolean handleActive) {
        int labelY = centerY - textRenderer.lineHeight / 2;
        int sliderX = popupX + popupWidth - sliderWidth - 20;
        int sliderY = centerY - sliderHeight / 2;
        String valueText = value + unitLabel;
        int valueTextWidth = textRenderer.width(valueText);
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
        int valueTextY = valueBoxY + (valueBoxHeight - textRenderer.lineHeight) / 2 + 1;
        context.text(
            textRenderer,
            Component.literal(valueText),
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

    public static void renderNumericField(GuiGraphicsExtractor context, Font textRenderer, EditBox field,
                                          int mouseX, int mouseY,
                                          int labelX, int centerY, String label,
                                          int valueBoxX, int valueBoxY, int valueBoxWidth, int valueBoxHeight,
                                          String valueText, Component unitText,
                                          int accentColor, PopupAnimationHandler animation,
                                          float fieldHoverProgress, boolean focused,
                                          int textFieldVerticalPadding) {
        int labelY = centerY - textRenderer.lineHeight / 2;
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
            if (!focused && !valueText.equals(field.getValue())) {
                field.setValue(valueText);
            }
            field.setVisible(true);
            field.setEditable(true);
            int textColor = PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_HEADER);
            field.setTextColor(textColor);
            field.setTextColorUneditable(textColor);
            int textFieldHeight = Math.max(10, valueBoxHeight - textFieldVerticalPadding * 2);
            field.setPosition(valueBoxX + 4, valueBoxY + textFieldVerticalPadding);
            field.setWidth(valueBoxWidth - 8);
            field.setHeight(textFieldHeight);
            field.extractRenderState(context, mouseX, mouseY, 0f);
        }

        int unitX = valueBoxX + valueBoxWidth + 6;
        int unitY = valueBoxY + (valueBoxHeight - textRenderer.lineHeight) / 2 + 1;
        context.text(
            textRenderer,
            unitText,
            unitX,
            unitY,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_SECONDARY)
        );
    }

    public static void renderNumericSlider(GuiGraphicsExtractor context, int centerY,
                                           int sliderX, int sliderY, int sliderWidth, int sliderHeight,
                                           int sliderHandleWidth, int sliderHandleHeight,
                                           int value, int min, int max,
                                           int accentColor, PopupAnimationHandler animation,
                                           float sliderHoverProgress) {
        UIStyleHelper.SliderPalette trackPalette = animatedSliderPalette(
            UIStyleHelper.getSliderPalette(accentColor, sliderHoverProgress, false, false),
            animation
        );
        UIStyleHelper.drawSliderTrack(context, sliderX, sliderY, sliderWidth, sliderHeight, trackPalette);

        int clamped = Math.max(min, Math.min(value, max));
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (sliderWidth - sliderHandleWidth));
        int handleY = centerY - sliderHandleHeight / 2;
        UIStyleHelper.SliderPalette handlePalette = animatedSliderPalette(
            UIStyleHelper.getSliderPalette(accentColor, sliderHoverProgress, true, false),
            animation
        );
        UIStyleHelper.drawSliderHandle(context, handleX, handleY, sliderHandleWidth, sliderHandleHeight, handlePalette);
    }

    public static void renderStatusListRow(GuiGraphicsExtractor context, Font textRenderer,
                                           int x, int y, int width, int height,
                                           String label, String status,
                                           boolean hovered, boolean selected,
                                           int accentColor, PopupAnimationHandler animation) {
        int rowBg = selected ? UITheme.DROPDOWN_OPTION_HOVER : hovered ? UITheme.BORDER_SECTION : UITheme.DROPDOWN_OPTION_BG;
        int rowBorder = selected ? accentColor : UITheme.BORDER_SUBTLE;
        context.fill(
            x + 1,
            y + 1,
            x + width - 1,
            y + height - 1,
            PathmindPopupRenderer.animatedColor(animation, rowBg)
        );
        DrawContextBridge.drawBorder(context, x, y, width, height, PathmindPopupRenderer.animatedColor(animation, rowBorder));

        String safeStatus = status == null ? "" : status;
        int statusWidth = safeStatus.isEmpty() ? 0 : textRenderer.width(safeStatus);
        int maxLabelWidth = Math.max(0, width - 12 - statusWidth - (safeStatus.isEmpty() ? 0 : 8));
        String rowText = TextRenderUtil.trimWithEllipsis(textRenderer, label == null ? "" : label, maxLabelWidth);
        context.text(
            textRenderer,
            Component.literal(rowText),
            x + 6,
            y + (height - textRenderer.lineHeight) / 2 + 1,
            PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_PRIMARY)
        );

        if (!safeStatus.isEmpty()) {
            int statusColor = selected ? accentColor : PathmindPopupRenderer.animatedColor(animation, UITheme.TEXT_TERTIARY);
            context.text(
                textRenderer,
                Component.literal(safeStatus),
                x + width - statusWidth - 6,
                y + (height - textRenderer.lineHeight) / 2 + 1,
                statusColor
            );
        }
    }

    public static void renderAccentOption(GuiGraphicsExtractor context, Font textRenderer,
                                          int x, int y, int width, int height,
                                          String label, int swatchColor,
                                          boolean selected, float hoverProgress,
                                          int accentColor, PopupAnimationHandler animation) {
        int bgColor = AnimationHelper.lerpColor(
            selected ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG,
            selected ? UITheme.BORDER_FOCUS : UITheme.BORDER_SECTION,
            hoverProgress
        );
        int borderColor = AnimationHelper.lerpColor(selected ? accentColor : UITheme.BORDER_SUBTLE, accentColor, hoverProgress);
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            PathmindPopupRenderer.animatedColor(animation, bgColor),
            PathmindPopupRenderer.animatedColor(animation, borderColor),
            PathmindPopupRenderer.animatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );

        int swatchSize = 8;
        int swatchX = x + 4;
        int swatchY = y + (height - swatchSize) / 2;
        context.fill(
            swatchX,
            swatchY,
            swatchX + swatchSize,
            swatchY + swatchSize,
            PathmindPopupRenderer.animatedColor(animation, swatchColor)
        );

        int labelX = swatchX + swatchSize + 4;
        int labelY = y + (height - textRenderer.lineHeight) / 2 + 1;
        int maxLabelWidth = Math.max(0, x + width - labelX - 4);
        String rowText = TextRenderUtil.trimWithEllipsis(textRenderer, label == null ? "" : label, maxLabelWidth);
        context.text(
            textRenderer,
            Component.literal(rowText),
            labelX,
            labelY,
            PathmindPopupRenderer.animatedColor(animation, AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, accentColor, hoverProgress))
        );
    }

    public static void renderDescriptionListRow(GuiGraphicsExtractor context, Font textRenderer,
                                                int x, int y, int width, int height,
                                                String label, String description,
                                                boolean hovered, boolean selected,
                                                float hoverProgress, int accentColor,
                                                PopupAnimationHandler animation) {
        int rowBg = selected ? UITheme.DROPDOWN_OPTION_HOVER : hovered ? UITheme.BACKGROUND_TERTIARY : UITheme.DROPDOWN_OPTION_BG;
        context.fill(x, y, x + width, y + height, PathmindPopupRenderer.animatedColor(animation, rowBg));
        if (selected) {
            DrawContextBridge.drawBorder(context, x, y, width, height, PathmindPopupRenderer.animatedColor(animation, accentColor));
        }

        int labelColor = PathmindPopupRenderer.animatedColor(animation, AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, accentColor, hoverProgress));
        int metaColor = PathmindPopupRenderer.animatedColor(animation, AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, UITheme.TEXT_SECONDARY, hoverProgress));
        int maxTextWidth = Math.max(0, width - 16);
        context.text(
            textRenderer,
            Component.literal(TextRenderUtil.trimWithEllipsis(textRenderer, label == null ? "" : label, maxTextWidth)),
            x + 8,
            y + 6,
            labelColor
        );
        context.text(
            textRenderer,
            Component.literal(TextRenderUtil.trimWithEllipsis(textRenderer, description == null ? "" : description, maxTextWidth)),
            x + 8,
            y + 16,
            metaColor
        );
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
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }
}
