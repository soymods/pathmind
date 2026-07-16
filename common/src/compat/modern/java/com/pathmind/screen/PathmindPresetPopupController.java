package com.pathmind.screen;

import static com.pathmind.screen.PathmindVisualEditorScreen.*;

import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class PathmindPresetPopupController {
    private final PathmindVisualEditorScreen screen;

    PathmindPresetPopupController(PathmindVisualEditorScreen screen) {
        this.screen = screen;
    }

    boolean handleCreatePresetPopupClick(double mouseX, double mouseY, int button) {
        int popupHeight = screen.getCreateNamingPopupHeight();
        if (button != 0) {
            return false;
        }

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.createPresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.createPresetPopupAnimation, popupHeight);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, popupHeight, 90, 20, 16);

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

    boolean handlePublishPresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.PublishPresetLayout layout = PathmindPopupLayout.publishPreset(
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            contentY,
            PUBLISH_PRESET_POPUP_HEIGHT,
            96,
            20,
            screen.publishPresetVisibilityToggle.getWidth(),
            screen.publishPresetVisibilityToggle.getHeight()
        );

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        if (!screen.isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            screen.closePublishPresetPopup();
            return true;
        }
        if (layout.cancelButton().contains(mouseXi, mouseYi)) {
            screen.closePublishPresetPopup();
            return true;
        }
        if (screen.publishPresetSession == null && layout.signInButton().contains(mouseXi, mouseYi)) {
            screen.startPublishPresetSignIn();
            return true;
        }
        if (layout.publishButton().contains(mouseXi, mouseYi)) {
            screen.attemptPublishPreset();
            return true;
        }
        if (layout.nameField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(screen.publishPresetNameField);
            return true;
        }
        if (layout.descriptionField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(screen.publishPresetDescriptionField);
            return true;
        }
        if (layout.tagsField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(screen.publishPresetTagsField);
            return true;
        }
        if (layout.visibilityToggle().contains(mouseXi, mouseYi)) {
            screen.publishPresetVisibilityToggle.mouseClicked(mouseXi, mouseYi);
            screen.publishPresetPublic = screen.publishPresetVisibilityToggle.getValue();
            return true;
        }
        focusPublishPresetField(null);
        return true;
    }

    boolean handleRenamePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.renamePresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.renamePresetPopupAnimation, CREATE_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);

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

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.presetDeletePopupAnimation, PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.presetDeletePopupAnimation, PRESET_DELETE_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int checkboxX = popupX + 20;
        int checkboxY = contentY + 86;
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

    void renderCreatePresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int popupHeight = screen.getCreateNamingPopupHeight();
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.createPresetPopupAnimation.getPopupAlpha());

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.createPresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.createPresetPopupAnimation, popupHeight);
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, screen.createPresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            screen.textRenderer(),
            Component.translatable(screen.createRoutineNaming ? (screen.pendingRoutineRenameId.isBlank() ? "pathmind.popup.createRoutine.title" : "pathmind.popup.renameRoutine.title") : "pathmind.popup.createPreset.title"),
            popupX,
            contentY,
            scaledWidth,
            screen.createPresetPopupAnimation
        );

        screen.drawPopupTextWithEllipsis(
            context,
            Component.translatable(screen.createRoutineNaming ? (screen.pendingRoutineRenameId.isBlank() ? "pathmind.popup.createRoutine.message" : "pathmind.popup.renameRoutine.message") : "pathmind.popup.createPreset.message").getString(),
            popupX + 20,
            contentY + 44,
            scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.createPresetPopupAnimation, UITheme.TEXT_SECONDARY)
        );

        int fieldX = popupX + 20;
        int fieldY = contentY + 70;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, screen.createPresetField, fieldX, fieldY, fieldWidth, fieldHeight, screen.createPresetPopupAnimation);

        if (!screen.createPresetStatus.isEmpty()) {
            screen.drawPopupTextWithEllipsis(
                context,
                screen.createPresetStatus,
                fieldX,
                fieldY + fieldHeight + 8,
                fieldWidth,
                screen.getPopupAnimatedColor(screen.createPresetPopupAnimation, screen.createPresetStatusColor)
            );
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, popupHeight, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable(screen.createRoutineNaming && !screen.pendingRoutineRenameId.isBlank() ? "pathmind.button.rename" : "pathmind.button.create"),
            screen.createPresetPopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderPublishPresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.publishPresetPopupAnimation.getPopupAlpha());
        syncPublishPresetVisibilityToggleColors();

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_HEIGHT);
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, screen.publishPresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            screen.textRenderer(),
            screen.publishPresetEditingPreset == null ? Component.translatable("pathmind.marketplace.publishPreset") : Component.translatable("pathmind.marketplace.updateUploadedPreset"),
            popupX,
            contentY,
            scaledWidth,
            screen.publishPresetPopupAnimation
        );

        PathmindPopupLayout.PublishPresetLayout layout = PathmindPopupLayout.publishPreset(
            popupX,
            popupY,
            scaledWidth,
            scaledHeight,
            contentY,
            PUBLISH_PRESET_POPUP_HEIGHT,
            96,
            20,
            screen.publishPresetVisibilityToggle.getWidth(),
            screen.publishPresetVisibilityToggle.getHeight()
        );
        int fieldX = layout.fieldX();
        int fieldWidth = layout.fieldWidth();
        int fieldHeight = layout.nameField().height();

        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.name").getString(), fieldX, layout.nameField().y() - 10, fieldWidth,
            screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.description").getString(), fieldX, layout.descriptionField().y() - 10, fieldWidth,
            screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.tags").getString(), fieldX, layout.tagsField().y() - 10, fieldWidth,
            screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));

        renderPublishPresetField(context, mouseX, mouseY, delta, screen.publishPresetNameField, layout.nameField());
        renderPublishPresetField(context, mouseX, mouseY, delta, screen.publishPresetDescriptionField, layout.descriptionField());
        renderPublishPresetField(context, mouseX, mouseY, delta, screen.publishPresetTagsField, layout.tagsField());

        int visibilityY = layout.visibilityRow().y();
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.visibility").getString(), fieldX, visibilityY - 10, fieldWidth,
            screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        renderPublishVisibilityToggle(context, mouseX, mouseY, layout.visibilityRow(), layout.visibilityToggle());

        String accountLabel = screen.publishPresetBusy ? Component.translatable("pathmind.status.working").getString() : screen.publishPresetSession == null
            ? Component.translatable("pathmind.marketplace.signIn").getString()
            : TextRenderUtil.trimWithEllipsis(screen.textRenderer(),
                screen.fallback(screen.publishPresetSession.getDisplayName(), screen.fallback(screen.publishPresetSession.getEmail(), Component.translatable("pathmind.marketplace.signedIn").getString())), 110);
        screen.drawPopupTextWithEllipsis(context, screen.publishPresetPublic
                ? Component.translatable("pathmind.marketplace.visiblePublic").getString()
                : Component.translatable("pathmind.marketplace.visiblePrivate").getString(),
            fieldX, visibilityY + fieldHeight + 8, fieldWidth, screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_TERTIARY));

        if (!screen.publishPresetStatus.isEmpty()) {
            screen.drawPopupTextWithEllipsis(context, screen.publishPresetStatus, fieldX, contentY + 214, fieldWidth,
                screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, screen.publishPresetStatusColor));
        }

        PathmindPopupLayout.Rect cancelButton = layout.cancelButton();
        PathmindPopupLayout.Rect publishButton = layout.publishButton();
        PathmindPopupLayout.Rect signInButton = layout.signInButton();
        int accountTextX = popupX + scaledWidth / 2 - screen.textRenderer().width(accountLabel) / 2;
        int accountTextY = cancelButton.y() + (cancelButton.height() - screen.textRenderer().lineHeight) / 2 + 1;

        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            cancelButton,
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.cancel"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            screen.getAccentColor(),
            screen.publishPresetPopupAnimation
        );
        if (screen.publishPresetSession == null) {
            PathmindPopupRenderer.drawButton(
                context,
                screen.textRenderer(),
                signInButton,
                mouseX,
                mouseY,
                Component.literal(accountLabel),
                PathmindPopupRenderer.ButtonStyle.DEFAULT,
                screen.getAccentColor(),
                screen.publishPresetPopupAnimation
            );
        } else {
            context.drawString(screen.textRenderer(), Component.literal(accountLabel), accountTextX, accountTextY,
                screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        }
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            publishButton,
            mouseX,
            mouseY,
            Component.literal(screen.publishPresetBusy ? Component.translatable("pathmind.status.working").getString() : (screen.publishPresetEditingPreset == null ? Component.translatable("pathmind.marketplace.publish").getString() : Component.translatable("pathmind.button.update").getString())),
            PathmindPopupRenderer.ButtonStyle.PRIMARY,
            screen.getAccentColor(),
            screen.publishPresetPopupAnimation
        );
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderRenamePresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.renamePresetPopupAnimation.getPopupAlpha());

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.renamePresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.renamePresetPopupAnimation, CREATE_PRESET_POPUP_HEIGHT);
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, screen.renamePresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            screen.textRenderer(),
            Component.translatable("pathmind.popup.renamePreset.title"),
            popupX,
            contentY,
            scaledWidth,
            screen.renamePresetPopupAnimation
        );

        String presetLabel = screen.pendingPresetRenameName == null || screen.pendingPresetRenameName.isEmpty()
            ? Component.translatable("pathmind.popup.preset.fallbackSelected").getString()
            : Component.translatable("pathmind.popup.preset.label", screen.pendingPresetRenameName).getString();
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.renamePreset.message").getString(), popupX + 20, contentY + 44, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, presetLabel, popupX + 20, contentY + 58, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));

        int fieldX = popupX + 20;
        int fieldY = contentY + 80;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, screen.renamePresetField, fieldX, fieldY, fieldWidth, fieldHeight, screen.renamePresetPopupAnimation);

        if (!screen.renamePresetStatus.isEmpty()) {
            screen.drawPopupTextWithEllipsis(context, screen.renamePresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth,
                screen.getPopupAnimatedColor(screen.renamePresetPopupAnimation, screen.renamePresetStatusColor));
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable("pathmind.button.rename"),
            screen.renamePresetPopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderPresetDeletePopup(GuiGraphics context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, screen.presetDeletePopupAnimation.getPopupAlpha());

        int[] bounds = screen.getBoundedScaledPopupBounds(screen.presetDeletePopupAnimation, PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = screen.getBoundedPopupContentY(popupY, screen.presetDeletePopupAnimation, PRESET_DELETE_POPUP_HEIGHT);
        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, screen.presetDeletePopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            screen.textRenderer(),
            Component.translatable("pathmind.popup.deletePreset.title"),
            popupX,
            contentY,
            scaledWidth,
            screen.presetDeletePopupAnimation
        );

        String presetLabel = screen.pendingPresetDeletionName != null && !screen.pendingPresetDeletionName.isEmpty()
            ? screen.pendingPresetDeletionName
            : Component.translatable("pathmind.popup.preset.fallbackCurrent").getString();
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.deletePreset.message").getString(), popupX + 20, contentY + 48, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.preset.label", presetLabel).getString(), popupX + 20, contentY + 64, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        int checkboxX = popupX + 20;
        int checkboxY = contentY + 86;
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
        screen.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.presetDelete.dontShowAgain").getString(), checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE + 8, checkboxY + 1, scaledWidth - 68,
            screen.getPopupAnimatedColor(screen.presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable("pathmind.button.delete"),
            screen.presetDeletePopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void focusPublishPresetField(EditBox target) {
        if (screen.publishPresetNameField != null) {
            screen.publishPresetNameField.setFocused(screen.publishPresetNameField == target);
        }
        if (screen.publishPresetDescriptionField != null) {
            screen.publishPresetDescriptionField.setFocused(screen.publishPresetDescriptionField == target);
        }
        if (screen.publishPresetTagsField != null) {
            screen.publishPresetTagsField.setFocused(screen.publishPresetTagsField == target);
        }
    }

    private void renderPresetTextField(GuiGraphics context, int mouseX, int mouseY, float delta, EditBox field,
                                       int fieldX, int fieldY, int fieldWidth, int fieldHeight, com.pathmind.ui.animation.PopupAnimationHandler animation) {
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

    private void renderPublishPresetField(GuiGraphics context, int mouseX, int mouseY, float delta, EditBox field,
                                          PathmindPopupLayout.Rect bounds) {
        renderPresetTextField(context, mouseX, mouseY, delta, field, bounds.x(), bounds.y(), bounds.width(), bounds.height(), screen.publishPresetPopupAnimation);
    }

    private void renderPublishVisibilityToggle(GuiGraphics context, int mouseX, int mouseY, PathmindPopupLayout.Rect row, PathmindPopupLayout.Rect toggle) {
        screen.publishPresetVisibilityToggle.setValue(screen.publishPresetPublic);
        screen.publishPresetVisibilityToggle.setPosition(toggle.x(), toggle.y());
        screen.publishPresetVisibilityToggle.render(context, mouseX, mouseY, screen.publishPresetPopupAnimation.getPopupAlpha());
        String label = screen.publishPresetPublic ? Component.translatable("pathmind.option.public").getString() : Component.translatable("pathmind.option.private").getString();
        int labelColor = screen.publishPresetPublic ? screen.getAccentColor() : UITheme.STATE_WARNING;
        screen.drawPopupTextWithEllipsis(context, label, row.x(), row.y() + 4, row.width() - toggle.width() - 8,
            screen.getPopupAnimatedColor(screen.publishPresetPopupAnimation, labelColor));
    }

    private void syncPublishPresetVisibilityToggleColors() {
        screen.publishPresetVisibilityToggle.setIndicatorColors(UITheme.MARKETPLACE_PRIVATE_VISIBILITY, screen.getAccentColor());
    }

    private void renderButtonRow(GuiGraphics context, int mouseX, int mouseY, PathmindPopupLayout.ButtonRow buttonRow,
                                 Component leftLabel, Component rightLabel, com.pathmind.ui.animation.PopupAnimationHandler animation) {
        PathmindPopupLayout.Rect leftButton = buttonRow.left();
        PathmindPopupLayout.Rect rightButton = buttonRow.right();
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            leftButton,
            mouseX,
            mouseY,
            leftLabel,
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            screen.getAccentColor(),
            animation
        );
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            rightButton,
            mouseX,
            mouseY,
            rightLabel,
            PathmindPopupRenderer.ButtonStyle.PRIMARY,
            screen.getAccentColor(),
            animation
        );
    }
}
