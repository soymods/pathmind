package com.pathmind.screen;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

final class PathmindMarketplacePopupController {
    private final PathmindMarketplaceScreen screen;

    PathmindMarketplacePopupController(PathmindMarketplaceScreen screen) {
        this.screen = screen;
    }

    void renderPresetPopup(DrawContext context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
        screen.popupAuthorHitRect = null;
        PathmindMarketplaceScreen.PopupLayout popup = screen.getPopupLayout(layout);
        context.fill(0, 0, screen.screenWidth(), screen.screenHeight(), screen.presetPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND));
        int[] bounds = screen.presetPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), popup.width(), popup.height());
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (popupWidth <= 0 || popupHeight <= 0 || screen.popupPreset == null) {
            return;
        }
        context.enableScissor(popupX, popupY, popupX + popupWidth, popupY + popupHeight);
        UIStyleHelper.drawBeveledPanel(
            context,
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.marketplace.presetDetails"), popupX + 12, popupY + 10,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        int popupCloseX = popupX + popupWidth - 18;
        int popupCloseY = popupY + 10;
        boolean popupCloseHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, popupCloseX - 2, popupCloseY - 2, 12, 12);
        drawPopupCloseIcon(context, popupCloseX, popupCloseY,
            screen.presetPopupAnimation.getAnimatedPopupColor(popupCloseHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY));
        context.drawHorizontalLine(popupX, popupX + popupWidth - 1, popupY + 28,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int contentTop = popupY + 40;
        int contentBottom = popupY + popupHeight - 48;
        int contentHeight = Math.max(24, contentBottom - contentTop);
        PathmindMarketplaceScreen.Rect previewRect = screen.getPopupPreviewRect(popupX, popupY, popupWidth, popupHeight, screen.popupScrollOffset);
        int previewX = previewRect.x();
        int previewY = previewRect.y();
        int previewWidth = previewRect.width();
        int previewHeight = previewRect.height();
        int textX = popupX + 12;
        int textWidth = popupWidth - 24;
        int contentHeightTotal = screen.measurePopupContentHeight(textWidth);
        int maxPopupScroll = Math.max(0, contentHeightTotal - contentHeight);
        screen.popupScrollOffset = Math.max(0, Math.min(screen.popupScrollOffset, maxPopupScroll));

        context.enableScissor(popupX + 8, contentTop, popupX + popupWidth - 8, contentBottom);
        UIStyleHelper.drawBeveledPanel(
            context,
            previewX,
            previewY,
            previewWidth,
            previewHeight,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_PRIMARY),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        screen.graphPreviewRenderer.renderSurface(context, previewX, previewY, previewWidth, previewHeight, screen.popupPreset, true, true, screen.popupPreviewPanX, screen.popupPreviewPanY);
        int zoomButtonSize = 14;
        int zoomOutX = previewX + previewWidth - zoomButtonSize * 2 - 8;
        int zoomInX = previewX + previewWidth - zoomButtonSize - 6;
        int zoomButtonY = previewY + 6;
        boolean zoomOutHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, zoomOutX, zoomButtonY, zoomButtonSize, zoomButtonSize);
        boolean zoomInHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, zoomInX, zoomButtonY, zoomButtonSize, zoomButtonSize);
        screen.graphPreviewRenderer.drawMinimalPreviewButton(context, zoomOutX, zoomButtonY, zoomButtonSize, zoomButtonSize, zoomOutHovered);
        screen.graphPreviewRenderer.drawMinimalPreviewButton(context, zoomInX, zoomButtonY, zoomButtonSize, zoomButtonSize, zoomInHovered);
        screen.graphPreviewRenderer.drawPreviewMinusIcon(context, zoomOutX, zoomButtonY, screen.presetPopupAnimation.getAnimatedPopupColor(zoomOutHovered ? screen.getAccentColor() : UITheme.TEXT_PRIMARY));
        screen.graphPreviewRenderer.drawPreviewPlusIcon(context, zoomInX, zoomButtonY, screen.presetPopupAnimation.getAnimatedPopupColor(zoomInHovered ? screen.getAccentColor() : UITheme.TEXT_PRIMARY));

        int popupBookmarkX = popupX + popupWidth - 56;
        int popupHeartX = popupX + popupWidth - 38;
        boolean popupBookmarkHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, popupBookmarkX, popupY + 10, 12, 12);
        boolean popupHeartHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, popupHeartX, popupY + 10, 12, 12);
        screen.drawAnimatedBookmarkIcon(context, popupBookmarkX, popupY + 10, screen.popupPreset, screen.isPresetSavedLocally(screen.popupPreset), true, popupBookmarkHovered);
        screen.drawAnimatedHeartIcon(context, popupHeartX, popupY + 10, screen.popupPreset, screen.isPresetLiked(screen.popupPreset), true, popupHeartHovered);

        int cursorY = previewY + previewHeight + 12;
        if (screen.popupMetadataEditing) {
            cursorY = screen.drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, Text.translatable("pathmind.field.title").getString(), screen.publishNameField) + 3;
        } else {
            context.drawTextWithShadow(screen.textRenderer(),
                Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.popupPreset.getName(), textWidth)),
                textX, cursorY, screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
            cursorY += 14;
        }
        String popupAuthorLabel = Text.translatable("pathmind.marketplace.byAuthor", TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.fallback(screen.popupPreset.getAuthorName(), Text.translatable("pathmind.marketplace.unknown").getString()), textWidth - 20)).getString();
        screen.popupAuthorHitRect = new PathmindMarketplaceScreen.Rect(textX, cursorY, screen.textRenderer().getWidth(popupAuthorLabel), screen.textRenderer().fontHeight + 1);
        boolean popupAuthorHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, screen.popupAuthorHitRect.x(), screen.popupAuthorHitRect.y(), screen.popupAuthorHitRect.width(), screen.popupAuthorHitRect.height());
        screen.renderAuthorLink(
            context,
            "marketplace-author-popup:" + screen.fallback(screen.popupPreset.getId(), "unknown"),
            popupAuthorLabel,
            textX,
            cursorY,
            popupAuthorHovered,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY)
        );
        cursorY += 14;

        int downloadsWidth = Math.max(54, screen.textRenderer().getWidth(screen.popupPreset.getDownloadsCount() + " downloads") + 12);
        int likesWidth = Math.max(42, screen.textRenderer().getWidth(Text.translatable("pathmind.marketplace.likesShort", screen.popupPreset.getLikesCount()).getString()) + 12);
        screen.drawPopupStatPill(context, textX, cursorY, downloadsWidth, Text.translatable("pathmind.marketplace.downloads").getString(), Integer.toString(screen.popupPreset.getDownloadsCount()));
        screen.drawPopupStatPill(context, textX + downloadsWidth + 6, cursorY, likesWidth, Text.translatable("pathmind.marketplace.likes").getString(), Integer.toString(screen.popupPreset.getLikesCount()));
        cursorY += 24;
        PathmindMarketplaceScreen.CompatibilityStatus compatibility = screen.getCompatibilityStatus(screen.popupPreset);
        int badgeY = cursorY;
        int badgeX = textX;
        badgeX += screen.drawStatusBadge(context, badgeX, badgeY, screen.popupPreset.isPublished() ? Text.translatable("pathmind.option.public").getString() : Text.translatable("pathmind.option.private").getString(),
            screen.popupPreset.isPublished() ? screen.getAccentColor() : UITheme.STATE_WARNING, true) + 6;
        badgeX += screen.drawStatusBadge(context, badgeX, badgeY, compatibility.isFullyCompatible() ? Text.translatable("pathmind.marketplace.compatible").getString() : Text.translatable("pathmind.marketplace.versionCheck").getString(),
            compatibility.summaryColor(), true) + 6;
        if (screen.isPresetSavedLocally(screen.popupPreset)) {
            screen.drawStatusBadge(context, badgeX, badgeY, Text.translatable("pathmind.marketplace.saved").getString(), UITheme.MARKETPLACE_SAVE, true);
        }
        cursorY += 22;

        if (screen.popupMetadataEditing) {
            cursorY = screen.drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, Text.translatable("pathmind.field.tags").getString(), screen.publishTagsField) + 1;
        } else {
            String tagsLine = PathmindMarketplaceScreen.formatTags(screen.popupPreset.getTags());
            if (!tagsLine.isBlank() && !"untagged".equalsIgnoreCase(tagsLine)) {
                cursorY = screen.drawWrappedValue(
                    context,
                    textX,
                    cursorY,
                    textWidth,
                    Text.translatable("pathmind.marketplace.tagsValue", tagsLine).getString(),
                    screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY),
                    2
                );
            }
        }

        int visibilityY = cursorY + 10;
        String visibilityLabel = screen.popupMetadataEditing
            ? Text.translatable("pathmind.field.visibility").getString()
            : Text.translatable("pathmind.marketplace.visibilityValue", screen.popupPreset.isPublished()
                ? Text.translatable("pathmind.option.public").getString()
                : Text.translatable("pathmind.option.private").getString()).getString();
        int visibilityColor = screen.popupMetadataEditing
            ? UITheme.TEXT_LABEL
            : (screen.popupPreset.isPublished() ? screen.getAccentColor() : UITheme.STATE_WARNING);
        context.drawTextWithShadow(screen.textRenderer(), Text.literal(visibilityLabel), textX, visibilityY,
            screen.presetPopupAnimation.getAnimatedPopupColor(visibilityColor));
        if (screen.popupMetadataEditing) {
            screen.presetVisibilityToggle.setValue(screen.publishVisibilityPublic);
            screen.presetVisibilityToggle.setPosition(textX + textWidth - screen.presetVisibilityToggle.getWidth(), visibilityY - 2);
            screen.presetVisibilityToggle.render(context, mouseX, mouseY, screen.presetPopupAnimation.getPopupAlpha());
        }
        cursorY = visibilityY + 14;

        int descriptionTop = cursorY + 8;
        int descriptionHeight = screen.popupMetadataEditing
            ? 46
            : Math.max(42, screen.measureWrappedValueHeight(textWidth - 16, screen.fallback(screen.popupPreset.getDescription(), Text.translatable("pathmind.marketplace.noDescription").getString()), 5) + 18);
        UIStyleHelper.drawBeveledPanel(
            context,
            textX,
            descriptionTop,
            textWidth,
            descriptionHeight,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.marketplace.about"), textX + 8, descriptionTop + 6,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        if (screen.popupMetadataEditing) {
            int fieldY = descriptionTop + 18;
            screen.drawPopupFieldFrame(context, mouseX, mouseY, textX + 8, fieldY, textWidth - 16, 18, screen.publishDescriptionField);
            if (screen.publishDescriptionField != null) {
                screen.publishDescriptionField.setPosition(textX + 14, fieldY + 5);
                screen.publishDescriptionField.setWidth(textWidth - 28);
                screen.publishDescriptionField.render(context, mouseX, mouseY, 0f);
            }
            cursorY = descriptionTop + descriptionHeight;
        } else {
            cursorY = screen.drawWrappedValue(context, textX + 8, descriptionTop + 18, textWidth - 16,
                screen.fallback(screen.popupPreset.getDescription(), Text.translatable("pathmind.marketplace.noDescription").getString()),
                screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY), 5);
        }

        PathmindMarketplaceScreen.CompatibilityStatus compatibilityStatus = screen.getCompatibilityStatus(screen.popupPreset);
        int compatibilityHeight = 30;
        UIStyleHelper.drawBeveledPanel(
            context,
            textX,
            cursorY + 8,
            textWidth,
            compatibilityHeight,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );
        context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.marketplace.compatibility"), textX + 8, cursorY + 14,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        context.drawTextWithShadow(screen.textRenderer(), Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(),
                compatibilityStatus.minecraftLine() + "  •  " + compatibilityStatus.pathmindLine(), textWidth - 16)),
            textX + 8, cursorY + 24,
            screen.presetPopupAnimation.getAnimatedPopupColor(compatibilityStatus.minecraftColor()));
        cursorY += compatibilityHeight + 18;

        String sharedLine = Text.translatable("pathmind.marketplace.publishedUpdated", PathmindMarketplaceScreen.formatTimestamp(screen.popupPreset.getCreatedAt()), PathmindMarketplaceScreen.formatTimestamp(screen.popupPreset.getUpdatedAt())).getString();
        cursorY = screen.drawWrappedValue(context, textX, cursorY, textWidth, sharedLine,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        if (screen.authSession == null) {
            cursorY = screen.drawWrappedValue(context, textX, cursorY, textWidth,
                Text.translatable("pathmind.marketplace.signInLikeImport").getString(),
                screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);
        }
        context.disableScissor();

        ScrollbarHelper.renderCutoffDividers(
            context,
            popupX + 8,
            popupX + popupWidth - 9,
            contentTop,
            contentBottom,
            screen.popupScrollOffset,
            maxPopupScroll,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE)
        );

        if (!screen.popupStatusMessage.isEmpty()) {
            context.drawTextWithShadow(screen.textRenderer(),
                Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.popupStatusMessage, textWidth)),
                textX, popupY + popupHeight - 40, screen.presetPopupAnimation.getAnimatedPopupColor(screen.popupStatusColor));
        }

        if (maxPopupScroll > 0) {
            ScrollbarHelper.renderSettingsStyle(
                context,
                screen.getPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight),
                screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SIDEBAR),
                screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
                screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT)
            );
        }

        boolean ownPreset = screen.canManagePreset(screen.popupPreset);
        boolean hasLinkedLocalPreset = screen.findLocalPresetNameForMarketplacePreset(screen.popupPreset).isPresent();
        boolean hasLocalChanges = screen.hasLinkedLocalPresetChanges(screen.popupPreset);
        boolean showUpdateButton = ownPreset && !screen.popupMetadataEditing && hasLinkedLocalPreset;
        int updateButtonX = popupX + 10;
        int authButtonX = popupX + (popup.authButtonX() - popup.x());
        int deleteButtonX = popupX + (popup.deleteButtonX() - popup.x());
        int downloadButtonX = popupX + ((ownPreset ? popup.downloadButtonX() : popup.deleteButtonX()) - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean updateEnabled = hasLocalChanges && !screen.publishBusy && !screen.deleteBusy && !screen.importingPreset;
        boolean updateHovered = showUpdateButton && PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, updateButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean authHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean deleteHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, deleteButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean downloadHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, downloadButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.popupUpdateHoverAnimation.animateTo(showUpdateButton && updateHovered ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        screen.popupDeleteHoverAnimation.animateTo(ownPreset && deleteHovered ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        screen.popupUpdateHoverAnimation.tick();
        screen.popupDeleteHoverAnimation.tick();
        if (showUpdateButton) {
            screen.drawAnimatedActionButton(context, updateButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
                screen.publishBusy ? Text.translatable("pathmind.status.working").getString() : Text.translatable("pathmind.button.update").getString(), updateHovered, !updateEnabled, screen.presetPopupAnimation, screen.popupUpdateHoverAnimation.getValue());
        }
        screen.drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.getPopupAuthButtonLabel(), authHovered, screen.authBusy || screen.publishBusy, screen.presetPopupAnimation);
        if (ownPreset) {
            screen.drawAnimatedActionButton(context, deleteButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
                screen.deleteBusy ? "..." : Text.translatable("pathmind.button.delete").getString(), deleteHovered, screen.deleteBusy || screen.publishBusy, screen.presetPopupAnimation, screen.popupDeleteHoverAnimation.getValue());
        }
        screen.drawAnimatedActionButton(context, downloadButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.importingPreset ? Text.translatable("pathmind.status.downloading").getString() : Text.translatable("pathmind.button.download").getString(), downloadHovered, screen.importingPreset || screen.deleteBusy, screen.presetPopupAnimation);
        context.disableScissor();
    }

    private void drawPopupCloseIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 2, y + 2, x + 4, y + 4, color);
        context.fill(x + 7, y + 2, x + 9, y + 4, color);
        context.fill(x + 4, y + 4, x + 7, y + 7, color);
        context.fill(x + 2, y + 7, x + 4, y + 9, color);
        context.fill(x + 7, y + 7, x + 9, y + 9, color);
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

        if (confirmAction == PathmindMarketplaceScreen.ConfirmAction.UPDATE) {
            int sourceLabelY = cursorY + 8;
            int sourceFieldY = sourceLabelY + 11;
            int sourceX = contentX;
            int sourceWidth = contentWidth;
            context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.marketplace.sourcePreset"), sourceX, sourceLabelY,
                screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
            UIStyleHelper.drawToolbarButtonFrame(
                context,
                sourceX,
                sourceFieldY,
                sourceWidth,
                18,
                screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECTION),
                screen.confirmPopupAnimation.getAnimatedPopupColor(screen.updateConfirmSourceDropdownOpen ? screen.getAccentColor() : UITheme.BORDER_SUBTLE),
                screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
            );
            String displayPresetName = screen.fallback(screen.publishSourcePresetName, "");
            String displayText = displayPresetName.isBlank() ? Text.translatable("pathmind.marketplace.selectLocalPreset").getString() : displayPresetName;
            int textColor = displayPresetName.isBlank() ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            context.drawTextWithShadow(screen.textRenderer(),
                Text.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), displayText, sourceWidth - 24)),
                sourceX + 6,
                sourceFieldY + 5,
                screen.confirmPopupAnimation.getAnimatedPopupColor(textColor));
            screen.drawDropdownChevron(context, sourceX + sourceWidth - 12, sourceFieldY + 6,
                screen.confirmPopupAnimation.getAnimatedPopupColor(screen.updateConfirmSourceDropdownOpen ? screen.getAccentColor() : UITheme.TEXT_SECONDARY),
                screen.updateConfirmSourceDropdownOpen);
            cursorY = sourceFieldY + 18;
        }

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
        if (confirmAction == PathmindMarketplaceScreen.ConfirmAction.UPDATE && (screen.updateConfirmSourceDropdownOpen || screen.updateConfirmSourceDropdownAnimation.getValue() > 0.001f)) {
            renderUpdateConfirmSourceDropdown(context, mouseX, mouseY, popupX, popupY, popupWidth);
        }
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

    private void renderUpdateConfirmSourceDropdown(DrawContext context, int mouseX, int mouseY, int popupX, int popupY, int popupWidth) {
        List<String> presetOptions = screen.getUpdateSourcePresetOptions();
        if (presetOptions.isEmpty()) {
            return;
        }
        float animProgress = AnimationHelper.easeOutQuad(screen.updateConfirmSourceDropdownAnimation.getValue());
        if (animProgress <= 0.001f) {
            return;
        }
        PathmindMarketplaceScreen.Rect bounds = screen.getUpdateConfirmSourceDropdownBounds(popupX, popupY, popupWidth, presetOptions.size());
        int animatedHeight = Math.max(1, (int) (bounds.height() * animProgress));
        context.enableScissor(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + animatedHeight + 1);
        UIStyleHelper.drawBeveledPanel(
            context,
            bounds.x(),
            bounds.y(),
            bounds.width(),
            bounds.height(),
            UITheme.BACKGROUND_SECONDARY,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );
        for (int i = 0; i < presetOptions.size() && i < 6; i++) {
            int optionY = bounds.y() + i * PathmindMarketplaceScreen.SORT_OPTION_HEIGHT;
            boolean hovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, bounds.x() + 1, optionY + 1, bounds.width() - 2, PathmindMarketplaceScreen.SORT_OPTION_HEIGHT - 1);
            int optionColor = hovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
            context.fill(bounds.x() + 1, optionY + 1, bounds.x() + bounds.width() - 1, optionY + PathmindMarketplaceScreen.SORT_OPTION_HEIGHT, optionColor);
            if (i > 0) {
                context.drawHorizontalLine(bounds.x() + 1, bounds.x() + bounds.width() - 2, optionY, UITheme.BORDER_SUBTLE);
            }
            String presetName = presetOptions.get(i);
            int textColor = presetName.equalsIgnoreCase(screen.publishSourcePresetName) ? screen.getAccentColor() : UITheme.TEXT_PRIMARY;
            context.drawTextWithShadow(screen.textRenderer(), Text.literal(presetName), bounds.x() + 8, optionY + 5, textColor);
        }
        context.disableScissor();
    }
}
