package com.pathmind.ui.control;

import com.pathmind.mixin.TextFieldWidgetAccessor;
import com.pathmind.ui.theme.UIStyleHelper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;

public class PathmindTextField extends TextFieldWidget {
    private static final int SELECTION_COLOR = 0x664F86C6;
    private static final Field TEXT_SHADOW_FIELD = findTextShadowField();

    private final TextRenderer pathmindTextRenderer;

    public PathmindTextField(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.pathmindTextRenderer = textRenderer;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.isVisible()) {
            return;
        }

        TextFieldWidgetAccessor accessor = (TextFieldWidgetAccessor) this;
        int innerX = getInnerX();
        int innerWidth = Math.max(0, this.getInnerWidth());
        int textY = this.getY() + Math.max(0, (this.getHeight() - this.pathmindTextRenderer.fontHeight) / 2);
        int textColor = accessor.pathmind$isEditable() ? accessor.pathmind$getEditableColor() : accessor.pathmind$getUneditableColor();
        String text = this.getText();
        int firstCharacterIndex = MathHelper.clamp(accessor.pathmind$getFirstCharacterIndex(), 0, text.length());
        String visibleText = this.pathmindTextRenderer.trimToWidth(text.substring(firstCharacterIndex), innerWidth);

        renderSelection(context, accessor, text, firstCharacterIndex, innerX, innerWidth, textY);
        renderText(context, accessor, innerX, textY, innerWidth, textColor, visibleText);
        renderSuggestion(context, accessor, textY, textColor, text, visibleText, firstCharacterIndex, innerX, innerWidth);
        renderCaret(context, text, firstCharacterIndex, innerX, innerWidth, textY);
    }

    private void renderSelection(DrawContext context, TextFieldWidgetAccessor accessor, String text,
                                 int firstCharacterIndex, int innerX, int innerWidth, int textY) {
        int selectionStart = accessor.pathmind$getSelectionStart();
        int selectionEnd = accessor.pathmind$getSelectionEnd();
        if (selectionStart == selectionEnd) {
            return;
        }
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        int left = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, start);
        int right = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, end);
        if (right < left) {
            int swap = left;
            left = right;
            right = swap;
        }
        int fieldLeft = innerX;
        int fieldRight = innerX + innerWidth;
        left = MathHelper.clamp(left, fieldLeft, fieldRight);
        right = MathHelper.clamp(right, fieldLeft, fieldRight);
        if (right > left) {
            context.fill(left, textY - 1, right, textY + this.pathmindTextRenderer.fontHeight + 1, SELECTION_COLOR);
        }
    }

    private void renderText(DrawContext context, TextFieldWidgetAccessor accessor, int innerX, int textY, int innerWidth,
                            int textColor, String visibleText) {
        if (!visibleText.isEmpty()) {
            if (usesTextShadow()) {
                context.drawTextWithShadow(this.pathmindTextRenderer, visibleText, innerX, textY, textColor);
            } else {
                context.drawText(this.pathmindTextRenderer, visibleText, innerX, textY, textColor, false);
            }
            return;
        }
        Text placeholder = accessor.pathmind$getPlaceholder();
        if (!this.isFocused() && placeholder != null) {
            String placeholderText = this.pathmindTextRenderer.trimToWidth(placeholder.getString(), innerWidth);
            context.drawText(this.pathmindTextRenderer, placeholderText, innerX, textY, textColor, false);
        }
    }

    private void renderSuggestion(DrawContext context, TextFieldWidgetAccessor accessor, int textY, int textColor,
                                  String text, String visibleText, int firstCharacterIndex, int innerX, int innerWidth) {
        String suggestion = accessor.pathmind$getSuggestion();
        if (suggestion == null || suggestion.isEmpty() || this.getCursor() != text.length()) {
            return;
        }
        int suggestionX = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, this.getCursor());
        int fieldRight = innerX + innerWidth;
        if (suggestionX >= fieldRight) {
            return;
        }
        int remainingWidth = fieldRight - suggestionX;
        if (remainingWidth <= 0) {
            return;
        }
        String visibleSuggestion = this.pathmindTextRenderer.trimToWidth(suggestion, remainingWidth);
        if (visibleSuggestion.isEmpty()) {
            return;
        }
        context.drawText(this.pathmindTextRenderer, visibleSuggestion, suggestionX, textY, (textColor & 0x00FFFFFF) | 0x77000000, false);
    }

    private void renderCaret(DrawContext context, String text, int firstCharacterIndex, int innerX, int innerWidth, int textY) {
        if (!this.isFocused() || ((Util.getMeasuringTimeMs() / 300L) & 1L) != 0L) {
            return;
        }
        int caretX = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, this.getCursor());
        int fieldLeft = innerX;
        int fieldRight = innerX + innerWidth;
        if (caretX < fieldLeft || caretX > fieldRight) {
            return;
        }
        UIStyleHelper.drawTextCaret(context, caretX, textY, textY + this.pathmindTextRenderer.fontHeight, fieldRight, 0xFFFFFFFF);
    }

    private int getInnerX() {
        return this.getX() + (this.drawsBackground() ? 4 : 0);
    }

    private int getVisibleCharacterX(String text, int firstCharacterIndex, int innerX, int innerWidth, int characterIndex) {
        int clampedIndex = MathHelper.clamp(characterIndex, 0, text.length());
        if (clampedIndex <= firstCharacterIndex) {
            return innerX;
        }
        String textBeforeCharacter = text.substring(firstCharacterIndex, clampedIndex);
        String visiblePrefix = this.pathmindTextRenderer.trimToWidth(textBeforeCharacter, innerWidth);
        return innerX + this.pathmindTextRenderer.getWidth(visiblePrefix);
    }

    private boolean usesTextShadow() {
        if (TEXT_SHADOW_FIELD == null) {
            return false;
        }
        try {
            return TEXT_SHADOW_FIELD.getBoolean(this);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static Field findTextShadowField() {
        try {
            Field field = TextFieldWidget.class.getDeclaredField("textShadow");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }
}
