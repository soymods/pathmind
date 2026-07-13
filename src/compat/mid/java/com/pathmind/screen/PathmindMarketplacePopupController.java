package com.pathmind.screen;

import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;


final class PathmindMarketplacePopupController {
    private final PathmindMarketplaceScreen screen;

    PathmindMarketplacePopupController(PathmindMarketplaceScreen screen) {
        this.screen = screen;
    }

    void renderAccountPopup(DrawContext context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
        PathmindMarketplaceScreen.AccountPopupLayout popup = screen.getAccountPopupLayout(layout);
        context.fill(0, 0, screen.screenWidth(), screen.screenHeight(), screen.accountPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = screen.accountPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), popup.width(), popup.height());
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0 || screen.authSession == null) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);

        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.marketplace.account"), popupX + 12, popupY + 10,
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int avatarSize = 52;
        int contentX = popupX + 12;
        int contentY = popupY + 42;
        screen.renderAccountAvatar(context, popupX + 14, popupY + 42, avatarSize);
        int textX = contentX + avatarSize + 12;
        int textWidth = popupWidth - (textX - popupX) - 12;
        context.drawTextWithShadow(screen.textRenderer(),
            Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(),
                screen.fallback(screen.authSession.getDisplayName(), screen.fallback(screen.authSession.getEmail(), Text.translatable("pathmind.status.discordUser").getString())), textWidth)),
            textX, contentY + 2, screen.accountPopupAnimation.getAnimatedPopupColor(screen.getAccentColor()));
        contentY += 16;
        contentY = screen.drawWrappedValue(context, textX, contentY + 2, textWidth,
            Text.translatable("pathmind.marketplace.provider", screen.fallback(screen.authSession.getProvider(), "discord")).getString(),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        screen.drawWrappedValue(context, textX, contentY, textWidth,
            Text.translatable("pathmind.marketplace.userId", screen.fallback(screen.authSession.getUserId(), Text.translatable("pathmind.marketplace.unknown").getString())).getString(),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);
        int closeButtonX = popupX + (popup.closeButtonX() - popup.x());
        int signOutButtonX = popupX + (popup.signOutButtonX() - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean closeHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, closeButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean signOutHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, signOutButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.drawAnimatedActionButton(context, closeButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Text.translatable("pathmind.button.close").getString(), closeHovered, false, screen.accountPopupAnimation);
        screen.drawAnimatedActionButton(context, signOutButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Text.translatable("pathmind.button.signOut").getString(), signOutHovered, screen.authBusy, screen.accountPopupAnimation);
        context.disableScissor();
    }

    void renderPublishPopup(DrawContext context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
        PathmindMarketplaceScreen.PublishPopupLayout popup = screen.getPublishPopupLayout(layout);
        context.fill(0, 0, screen.screenWidth(), screen.screenHeight(), screen.publishPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = screen.publishPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), popup.width(), popup.height());
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        String title = screen.editingPreset == null ? Text.translatable("pathmind.marketplace.publishPreset").getString() : Text.translatable("pathmind.marketplace.editMetadata").getString();
        context.drawTextWithShadow(screen.textRenderer(), Text.literal(title), popupX + 12, popupY + 10,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int contentX = popupX + 12;
        int contentWidth = popupWidth - 24;
        int sourceY = popupY + 40;
        String sourceLine = screen.editingPreset == null
            ? Text.translatable("pathmind.marketplace.sourcePresetValue", screen.fallback(screen.publishSourcePresetName, Text.translatable("pathmind.marketplace.unknown").getString())).getString()
            : Text.translatable("pathmind.marketplace.editingListingBy", screen.fallback(screen.editingPreset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString())).getString();
        screen.drawWrappedValue(context, contentX, sourceY, contentWidth, sourceLine,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        int fieldWidth = popupWidth - 24;
        int fieldHeight = 18;
        int labelGap = 11;
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 53, fieldWidth, fieldHeight, Text.translatable("pathmind.field.name").getString(), screen.publishNameField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 92, fieldWidth, fieldHeight, Text.translatable("pathmind.field.description").getString(), screen.publishDescriptionField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 131, fieldWidth, fieldHeight, Text.translatable("pathmind.field.tags").getString(), screen.publishTagsField, labelGap);

        screen.drawWrappedValue(context, contentX, popupY + 166, contentWidth, Text.translatable("pathmind.marketplace.tagsHint").getString(),
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        int visibilityLabelY = popupY + 189;
        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.field.visibility"), contentX, visibilityLabelY,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        PathmindMarketplaceScreen.Rect publishToggle = screen.getPublishPopupVisibilityToggleRect(popupX, popupY, popupWidth);
        screen.publishVisibilityToggle.setValue(screen.publishVisibilityPublic);
        screen.publishVisibilityToggle.setPosition(publishToggle.x(), publishToggle.y());
        screen.publishVisibilityToggle.render(context, mouseX, mouseY, screen.publishPopupAnimation.getPopupAlpha());
        String visibilityHint = screen.publishVisibilityPublic
            ? Text.translatable("pathmind.marketplace.visiblePublic").getString()
            : Text.translatable("pathmind.marketplace.visiblePrivate").getString();
        screen.drawWrappedValue(context, contentX + 78, visibilityLabelY + 2, contentWidth - 78, visibilityHint,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        if (!screen.publishStatusMessage.isEmpty()) {
            context.drawTextWithShadow(screen.textRenderer(),
                Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.publishStatusMessage, contentWidth)),
                contentX,
                popupY + popupHeight - 40,
                screen.publishPopupAnimation.getAnimatedPopupColor(screen.publishStatusColor));
        }

        int cancelButtonX = popupX + (popup.cancelButtonX() - popup.x());
        int authButtonX = popupX + (popup.authButtonX() - popup.x());
        int submitButtonX = popupX + (popup.submitButtonX() - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean cancelHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean authHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean submitHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, submitButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.drawAnimatedActionButton(context, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Text.translatable("pathmind.button.cancel").getString(), cancelHovered, false, screen.publishPopupAnimation);
        screen.drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.getPublishAuthButtonLabel(), authHovered, screen.publishBusy || screen.authSession != null, screen.publishPopupAnimation);
        screen.drawAnimatedActionButton(context, submitButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.publishBusy ? Text.translatable("pathmind.status.working").getString() : (screen.editingPreset == null ? Text.translatable("pathmind.marketplace.publish").getString() : Text.translatable("pathmind.button.save").getString()),
            submitHovered, screen.publishBusy, screen.publishPopupAnimation);
        context.disableScissor();
    }

    void renderConfirmPopup(DrawContext context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
        PathmindMarketplaceScreen.ConfirmAction confirmAction = screen.pendingConfirmAction != null ? screen.pendingConfirmAction : screen.renderConfirmAction;
        if (confirmAction == null) {
            return;
        }
        PathmindMarketplaceScreen.ConfirmPopupLayout popup = screen.getConfirmPopupLayout(layout);
        context.fill(0, 0, screen.screenWidth(), screen.screenHeight(), screen.confirmPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = screen.confirmPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), popup.width(), popup.height());
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        boolean deleteAction = confirmAction == PathmindMarketplaceScreen.ConfirmAction.DELETE;
        String title = deleteAction ? Text.translatable("pathmind.marketplace.deleteUploadedPreset").getString() : Text.translatable("pathmind.marketplace.updateUploadedPreset").getString();
        String lineOne = deleteAction ? Text.translatable("pathmind.marketplace.deleteUploadedConfirm").getString() : Text.translatable("pathmind.marketplace.overwriteUploadedConfirm").getString();
        String lineTwo = deleteAction ? Text.translatable("pathmind.marketplace.deleteUploadedWarning").getString() : Text.translatable("pathmind.marketplace.overwriteUploadedWarning").getString();

        context.drawCenteredTextWithShadow(screen.textRenderer(), Text.literal(title),
            popupX + popupWidth / 2, popupY + 14, screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY));
        int contentX = popupX + 20;
        int contentWidth = popupWidth - 40;
        int cursorY = popupY + 40;
        cursorY = screen.drawWrappedValue(context, contentX, cursorY, contentWidth, lineOne,
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        cursorY = screen.drawWrappedValue(context, contentX, cursorY + 2, contentWidth, lineTwo,
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        boolean skipConfirm = deleteAction ? screen.skipMarketplaceDeleteConfirm : screen.skipMarketplaceUpdateConfirm;
        int checkboxX = popupX + 20;
        int checkboxY = cursorY + 12;
        boolean checkboxHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, 14, 14);
        context.fill(checkboxX, checkboxY, checkboxX + 10, checkboxY + 10,
            screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, checkboxX, checkboxY, 10, 10,
            screen.confirmPopupAnimation.getAnimatedPopupColor(checkboxHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (skipConfirm) {
            int checkColor = screen.confirmPopupAnimation.getAnimatedPopupColor(screen.getAccentColor());
            context.fill(checkboxX + 2, checkboxY + 5, checkboxX + 3, checkboxY + 7, checkColor);
            context.fill(checkboxX + 3, checkboxY + 6, checkboxX + 4, checkboxY + 8, checkColor);
            context.fill(checkboxX + 4, checkboxY + 6, checkboxX + 5, checkboxY + 7, checkColor);
            context.fill(checkboxX + 5, checkboxY + 5, checkboxX + 6, checkboxY + 6, checkColor);
            context.fill(checkboxX + 6, checkboxY + 4, checkboxX + 7, checkboxY + 5, checkColor);
            context.fill(checkboxX + 7, checkboxY + 3, checkboxX + 8, checkboxY + 4, checkColor);
        }
        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.option.dontShowAgain"),
            checkboxX + 18, checkboxY + 1, screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));

        int cancelButtonX = popupX + (popup.cancelButtonX() - popup.x());
        int confirmButtonX = popupX + (popup.confirmButtonX() - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean cancelHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean confirmHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, confirmButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.drawAnimatedActionButton(context, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Text.translatable("pathmind.button.cancel").getString(), cancelHovered, false, screen.confirmPopupAnimation);
        screen.drawAnimatedActionButton(context, confirmButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            deleteAction ? Text.translatable("pathmind.button.delete").getString() : Text.translatable("pathmind.button.update").getString(),
            confirmHovered, false, screen.confirmPopupAnimation);
        context.disableScissor();
    }

    private int drawPublishField(DrawContext context, int mouseX, int mouseY, int x, int y, int width, int height,
                                 String label, TextFieldWidget field, int labelGap) {
        context.drawTextWithShadow(screen.textRenderer(), Text.literal(label), x, y,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        int fieldY = y + labelGap;
        boolean hovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, x, fieldY, width, height);
        boolean focused = field != null && field.isFocused();
        UIStyleHelper.drawToolbarButtonFrame(
            context,
            x,
            fieldY,
            width,
            height,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            screen.publishPopupAnimation.getAnimatedPopupColor(focused || hovered ? screen.getAccentColor() : UITheme.BORDER_SUBTLE),
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        if (field != null) {
            field.setPosition(x + 6, fieldY + 5);
            field.setWidth(width - 12);
            field.render(context, mouseX, mouseY, 0f);
        }
        return fieldY + height;
    }


}
