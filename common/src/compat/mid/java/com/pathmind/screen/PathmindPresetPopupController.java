package com.pathmind.screen;

import static com.pathmind.screen.PathmindVisualEditorScreen.*;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.RenderStateBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class PathmindPresetPopupController {
    private final PathmindVisualEditorScreen screen;

    PathmindPresetPopupController(PathmindVisualEditorScreen screen) {
        this.screen = screen;
    }

    boolean handleCreatePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = screen.createPresetPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(bounds[0], bounds[2], bounds[1], CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);

        if (buttonRow.left().contains((int) mouseX, (int) mouseY)) {
            screen.closeCreatePresetPopup();
            return true;
        }

        if (buttonRow.right().contains((int) mouseX, (int) mouseY)) {
            screen.attemptCreatePreset();
            return true;
        }

        return false;
    }

    boolean handleRenamePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = screen.renamePresetPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(bounds[0], bounds[2], bounds[1], CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);

        if (buttonRow.left().contains((int) mouseX, (int) mouseY)) {
            screen.closeRenamePresetPopup();
            return true;
        }

        if (buttonRow.right().contains((int) mouseX, (int) mouseY)) {
            screen.attemptRenamePreset();
            return true;
        }

        return false;
    }

    boolean handlePresetDeletePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int[] bounds = screen.presetDeletePopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, popupY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int checkboxX = popupX + 20;
        int checkboxY = popupY + 86;
        int checkboxHitboxSize = PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4;

        if (buttonRow.right().contains(mouseXi, mouseYi)) {
            screen.confirmPresetDeletion();
            return true;
        }

        if (buttonRow.left().contains(mouseXi, mouseYi)) {
            screen.closePresetDeletePopup();
            return true;
        }

        if (screen.isPointInRect(mouseXi, mouseYi, checkboxX - 2, checkboxY - 2, checkboxHitboxSize, checkboxHitboxSize)) {
            screen.setSkipPresetDeleteConfirm(!screen.skipPresetDeleteConfirm);
            return true;
        }

        return true;
    }

    void renderCreatePresetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.createPresetPopupAnimation.getPopupAlpha());

        int[] bounds = screen.createPresetPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        screen.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, screen.createPresetPopupAnimation);
        boolean popupScissor = screen.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            screen.textRenderer(),
            Text.translatable("pathmind.popup.createPreset.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            screen.getPopupAnimatedColor(screen.createPresetPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.popup.createPreset.message").getString(), popupX + 20, popupY + 44, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.createPresetPopupAnimation, UITheme.TEXT_SECONDARY));

        int fieldX = popupX + 20;
        int fieldY = popupY + 70;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, screen.createPresetField, fieldX, fieldY, fieldWidth, fieldHeight, screen.createPresetPopupAnimation);

        if (!screen.createPresetStatus.isEmpty()) {
            screen.drawPopupTextWithEllipsis(context, screen.createPresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth,
                screen.getPopupAnimatedColor(screen.createPresetPopupAnimation, screen.createPresetStatusColor));
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, popupY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Text.translatable("pathmind.button.cancel"),
            Text.translatable("pathmind.button.create"),
            screen.createPresetPopupAnimation);
        screen.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderRenamePresetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.renamePresetPopupAnimation.getPopupAlpha());

        int[] bounds = screen.renamePresetPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        screen.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, screen.renamePresetPopupAnimation);
        boolean popupScissor = screen.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            screen.textRenderer(),
            Text.translatable("pathmind.popup.renamePreset.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        String presetLabel = screen.pendingPresetRenameName == null || screen.pendingPresetRenameName.isEmpty()
            ? Text.translatable("pathmind.popup.preset.fallbackSelected").getString()
            : Text.translatable("pathmind.popup.preset.label", screen.pendingPresetRenameName).getString();
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.popup.renamePreset.message").getString(), popupX + 20, popupY + 44, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, presetLabel, popupX + 20, popupY + 58, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));

        int fieldX = popupX + 20;
        int fieldY = popupY + 80;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, screen.renamePresetField, fieldX, fieldY, fieldWidth, fieldHeight, screen.renamePresetPopupAnimation);

        if (!screen.renamePresetStatus.isEmpty()) {
            screen.drawPopupTextWithEllipsis(context, screen.renamePresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth,
                screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, screen.renamePresetStatusColor));
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, popupY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Text.translatable("pathmind.button.cancel"),
            Text.translatable("pathmind.button.rename"),
            screen.renamePresetPopupAnimation);
        screen.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderPresetDeletePopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.presetDeletePopupAnimation.getPopupAlpha());

        int[] bounds = screen.presetDeletePopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        screen.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, screen.presetDeletePopupAnimation);
        boolean popupScissor = screen.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            screen.textRenderer(),
            Text.translatable("pathmind.popup.deletePreset.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_PRIMARY)
        );

        String presetLabel = screen.pendingPresetDeletionName != null && !screen.pendingPresetDeletionName.isEmpty()
            ? screen.pendingPresetDeletionName
            : Text.translatable("pathmind.popup.preset.fallbackCurrent").getString();
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.popup.deletePreset.message").getString(), popupX + 20, popupY + 48, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.popup.preset.label", presetLabel).getString(), popupX + 20, popupY + 64, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        int checkboxX = popupX + 20;
        int checkboxY = popupY + 86;
        boolean checkboxHovered = screen.isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4);
        context.fill(checkboxX, checkboxY, checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE, checkboxY + PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, checkboxX, checkboxY, PRESET_DELETE_SKIP_CHECKBOX_SIZE, PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, checkboxHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (screen.skipPresetDeleteConfirm) {
            int checkColor = screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, screen.getAccentColor());
            context.fill(checkboxX + 2, checkboxY + 5, checkboxX + 3, checkboxY + 7, checkColor);
            context.fill(checkboxX + 3, checkboxY + 6, checkboxX + 4, checkboxY + 8, checkColor);
            context.fill(checkboxX + 4, checkboxY + 6, checkboxX + 5, checkboxY + 7, checkColor);
            context.fill(checkboxX + 5, checkboxY + 5, checkboxX + 6, checkboxY + 6, checkColor);
            context.fill(checkboxX + 6, checkboxY + 4, checkboxX + 7, checkboxY + 5, checkColor);
            context.fill(checkboxX + 7, checkboxY + 3, checkboxX + 8, checkboxY + 4, checkColor);
        }
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.presetDelete.dontShowAgain").getString(), checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE + 8, checkboxY + 1, scaledWidth - 68,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, popupY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Text.translatable("pathmind.button.cancel"),
            Text.translatable("pathmind.button.delete"),
            screen.presetDeletePopupAnimation);
        screen.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderPresetTextField(DrawContext context, int mouseX, int mouseY, float delta, TextFieldWidget field,
                                       int fieldX, int fieldY, int fieldWidth, int fieldHeight, PopupAnimationHandler animation) {
        boolean fieldHovered = screen.isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        boolean focused = field != null && field.isFocused();
        int borderColor = focused ? screen.getAccentColor() : fieldHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.RENAME_INPUT_BORDER;
        PathmindPopupRenderer.drawPopupTextField(
            context,
            field,
            mouseX,
            mouseY,
            delta,
            fieldX,
            fieldY,
            fieldWidth,
            fieldHeight,
            borderColor,
            animation,
            UITheme.TEXT_PRIMARY,
            UITheme.TEXT_TERTIARY,
            TEXT_FIELD_VERTICAL_PADDING
        );
    }

    private void renderButtonRow(DrawContext context, int mouseX, int mouseY, PathmindPopupLayout.ButtonRow buttonRow,
                                 Text leftLabel, Text rightLabel, PopupAnimationHandler animation) {
        PathmindPopupLayout.Rect leftButton = buttonRow.left();
        PathmindPopupLayout.Rect rightButton = buttonRow.right();
        screen.drawPopupButton(context, leftButton.x(), leftButton.y(), leftButton.width(), leftButton.height(), leftButton.contains(mouseX, mouseY),
            leftLabel, PathmindPopupRenderer.ButtonStyle.DEFAULT, animation);
        screen.drawPopupButton(context, rightButton.x(), rightButton.y(), rightButton.width(), rightButton.height(), rightButton.contains(mouseX, mouseY),
            rightLabel, PathmindPopupRenderer.ButtonStyle.PRIMARY, animation);
    }
}
