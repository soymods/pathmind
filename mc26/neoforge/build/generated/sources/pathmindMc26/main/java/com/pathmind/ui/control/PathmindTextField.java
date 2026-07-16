package com.pathmind.ui.control;

import com.pathmind.mixin.TextFieldWidgetAccessor;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import java.lang.reflect.Field;
import java.util.function.Predicate;

public class PathmindTextField extends EditBox {
    private static final int SELECTION_COLOR = 0x664F86C6;
    private static final Field TEXT_SHADOW_FIELD = findTextShadowField();

    private final Font pathmindTextRenderer;
    private Predicate<String> pathmindFilter = value -> true;

    public PathmindTextField(Font textRenderer, int x, int y, int width, int height, Component text) {
        super(textRenderer, x, y, width, height, text);
        this.pathmindTextRenderer = textRenderer;
        applyPathmindDefaults();
    }

    /** Creates a standard Pathmind field with a configured maximum length. */
    public static PathmindTextField create(Font textRenderer, int x, int y, int width, int height,
                                           Component text, int maxLength) {
        PathmindTextField field = new PathmindTextField(textRenderer, x, y, width, height, text);
        field.setMaxLength(maxLength);
        return field;
    }

    /**
     * Creates a standard field that starts hidden and non-editable, as popup
     * and transient editor fields do before they are opened.
     */
    public static PathmindTextField createInactive(Font textRenderer, int x, int y, int width, int height,
                                                   Component text, int maxLength) {
        PathmindTextField field = create(textRenderer, x, y, width, height, text, maxLength);
        deactivate(field);
        return field;
    }

    /** Hides a transient field and clears its interactive state. */
    public static void deactivate(EditBox field) {
        if (field == null) {
            return;
        }
        field.setFocused(false);
        field.setVisible(false);
        field.setEditable(false);
    }

    /** Stable input filter retained across the pre-26 and 26.x EditBox APIs. */
    public void setPathmindFilter(Predicate<String> filter) {
        this.pathmindFilter = filter != null ? filter : value -> true;
    }

    @Override
    public void setValue(String value) {
        if (pathmindFilter == null || pathmindFilter.test(value)) {
            super.setValue(value);
        }
    }

    @Override
    public void insertText(String text) {
        TextFieldWidgetAccessor accessor = (TextFieldWidgetAccessor) this;
        String current = getValue();
        int selectionStart = Math.min(accessor.pathmind$getSelectionStart(), accessor.pathmind$getSelectionEnd());
        int selectionEnd = Math.max(accessor.pathmind$getSelectionStart(), accessor.pathmind$getSelectionEnd());
        String candidate = current.substring(0, selectionStart) + text + current.substring(selectionEnd);
        if (pathmindFilter == null || pathmindFilter.test(candidate)) {
            super.insertText(text);
        }
    }

    private void applyPathmindDefaults() {
        this.setBordered(false);
        this.setTextColor(UITheme.TEXT_PRIMARY);
        this.setTextColorUneditable(UITheme.TEXT_TERTIARY);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!this.isVisible()) {
            return;
        }

        TextFieldWidgetAccessor accessor = (TextFieldWidgetAccessor) this;
        int innerX = getInnerX();
        int innerWidth = Math.max(0, this.getInnerWidth());
        int textY = this.getY() + Math.max(0, (this.getHeight() - this.pathmindTextRenderer.lineHeight) / 2);
        int textColor = accessor.pathmind$isEditable() ? accessor.pathmind$getEditableColor() : accessor.pathmind$getUneditableColor();
        String text = this.getValue();
        int firstCharacterIndex = Mth.clamp(accessor.pathmind$getFirstCharacterIndex(), 0, text.length());
        String visibleText = this.pathmindTextRenderer.plainSubstrByWidth(text.substring(firstCharacterIndex), innerWidth);

        renderSelection(context, accessor, text, firstCharacterIndex, innerX, innerWidth, textY);
        renderText(context, accessor, innerX, textY, innerWidth, textColor, visibleText);
        renderSuggestion(context, accessor, textY, textColor, text, visibleText, firstCharacterIndex, innerX, innerWidth);
        renderCaret(context, text, firstCharacterIndex, innerX, innerWidth, textY);
    }

    private void renderSelection(GuiGraphicsExtractor context, TextFieldWidgetAccessor accessor, String text,
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
        left = Mth.clamp(left, fieldLeft, fieldRight);
        right = Mth.clamp(right, fieldLeft, fieldRight);
        if (right > left) {
            context.fill(left, textY - 1, right, textY + this.pathmindTextRenderer.lineHeight + 1, SELECTION_COLOR);
        }
    }

    private void renderText(GuiGraphicsExtractor context, TextFieldWidgetAccessor accessor, int innerX, int textY, int innerWidth,
                            int textColor, String visibleText) {
        if (!visibleText.isEmpty()) {
            if (usesTextShadow()) {
                context.text(this.pathmindTextRenderer, visibleText, innerX, textY, textColor);
            } else {
                context.text(this.pathmindTextRenderer, visibleText, innerX, textY, textColor, false);
            }
            return;
        }
        Component placeholder = accessor.pathmind$getPlaceholder();
        if (!this.isFocused() && placeholder != null) {
            String placeholderText = this.pathmindTextRenderer.plainSubstrByWidth(placeholder.getString(), innerWidth);
            context.text(this.pathmindTextRenderer, placeholderText, innerX, textY, textColor, false);
        }
    }

    private void renderSuggestion(GuiGraphicsExtractor context, TextFieldWidgetAccessor accessor, int textY, int textColor,
                                  String text, String visibleText, int firstCharacterIndex, int innerX, int innerWidth) {
        String suggestion = accessor.pathmind$getSuggestion();
        if (suggestion == null || suggestion.isEmpty() || this.getCursorPosition() != text.length()) {
            return;
        }
        int suggestionX = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, this.getCursorPosition());
        int fieldRight = innerX + innerWidth;
        if (suggestionX >= fieldRight) {
            return;
        }
        int remainingWidth = fieldRight - suggestionX;
        if (remainingWidth <= 0) {
            return;
        }
        String visibleSuggestion = this.pathmindTextRenderer.plainSubstrByWidth(suggestion, remainingWidth);
        if (visibleSuggestion.isEmpty()) {
            return;
        }
        context.text(this.pathmindTextRenderer, visibleSuggestion, suggestionX, textY, (textColor & 0x00FFFFFF) | 0x77000000, false);
    }

    private void renderCaret(GuiGraphicsExtractor context, String text, int firstCharacterIndex, int innerX, int innerWidth, int textY) {
        if (!this.isFocused() || ((Util.getMillis() / 300L) & 1L) != 0L) {
            return;
        }
        int caretX = getVisibleCharacterX(text, firstCharacterIndex, innerX, innerWidth, this.getCursorPosition());
        int fieldLeft = innerX;
        int fieldRight = innerX + innerWidth;
        if (caretX < fieldLeft || caretX > fieldRight) {
            return;
        }
        UIStyleHelper.drawTextCaret(context, caretX, textY, textY + this.pathmindTextRenderer.lineHeight, fieldRight, 0xFFFFFFFF);
    }

    private int getInnerX() {
        return this.getX() + (this.isBordered() ? 4 : 0);
    }

    private int getVisibleCharacterX(String text, int firstCharacterIndex, int innerX, int innerWidth, int characterIndex) {
        int clampedIndex = Mth.clamp(characterIndex, 0, text.length());
        if (clampedIndex <= firstCharacterIndex) {
            return innerX;
        }
        String textBeforeCharacter = text.substring(firstCharacterIndex, clampedIndex);
        String visiblePrefix = this.pathmindTextRenderer.plainSubstrByWidth(textBeforeCharacter, innerWidth);
        return innerX + this.pathmindTextRenderer.width(visiblePrefix);
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
            Field field = EditBox.class.getDeclaredField("textShadow");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }
}
