package com.pathmind.screen;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.control.PathmindDropdownRenderer;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class PathmindMarketplacePopupController {
    private final PathmindMarketplaceScreen screen;

    PathmindMarketplacePopupController(PathmindMarketplaceScreen screen) {
        this.screen = screen;
    }

    void renderPresetPopup(GuiGraphics context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
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

        context.drawString(screen.textRenderer(), Component.translatable("pathmind.marketplace.presetDetails"), popupX + 12, popupY + 10,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
        int popupCloseX = popupX + popupWidth - 18;
        int popupCloseY = popupY + 10;
        boolean popupCloseHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, popupCloseX - 2, popupCloseY - 2, 12, 12);
        PathmindPopupRenderer.drawCloseIcon(context, popupCloseX, popupCloseY,
            screen.presetPopupAnimation.getAnimatedPopupColor(popupCloseHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY));
        context.hLine(popupX, popupX + popupWidth - 1, popupY + 28,
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
            cursorY = screen.drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, Component.translatable("pathmind.field.title").getString(), screen.publishNameField) + 3;
        } else {
            context.drawString(screen.textRenderer(),
                Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.popupPreset.getName(), textWidth)),
                textX, cursorY, screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_HEADER));
            cursorY += 14;
        }
        String popupAuthorLabel = Component.translatable("pathmind.marketplace.byAuthor", TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.fallback(screen.popupPreset.getAuthorName(), Component.translatable("pathmind.marketplace.unknown").getString()), textWidth - 20)).getString();
        screen.popupAuthorHitRect = new PathmindMarketplaceScreen.Rect(textX, cursorY, screen.textRenderer().width(popupAuthorLabel), screen.textRenderer().lineHeight + 1);
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

        int downloadsWidth = Math.max(54, screen.textRenderer().width(screen.popupPreset.getDownloadsCount() + " downloads") + 12);
        int likesWidth = Math.max(42, screen.textRenderer().width(Component.translatable("pathmind.marketplace.likesShort", screen.popupPreset.getLikesCount()).getString()) + 12);
        screen.drawPopupStatPill(context, textX, cursorY, downloadsWidth, Component.translatable("pathmind.marketplace.downloads").getString(), Integer.toString(screen.popupPreset.getDownloadsCount()));
        screen.drawPopupStatPill(context, textX + downloadsWidth + 6, cursorY, likesWidth, Component.translatable("pathmind.marketplace.likes").getString(), Integer.toString(screen.popupPreset.getLikesCount()));
        cursorY += 24;
        PathmindMarketplaceScreen.CompatibilityStatus compatibility = screen.getCompatibilityStatus(screen.popupPreset);
        int badgeY = cursorY;
        int badgeX = textX;
        badgeX += screen.drawStatusBadge(context, badgeX, badgeY, screen.popupPreset.isPublished() ? Component.translatable("pathmind.option.public").getString() : Component.translatable("pathmind.option.private").getString(),
            screen.popupPreset.isPublished() ? screen.getAccentColor() : UITheme.STATE_WARNING, true) + 6;
        badgeX += screen.drawStatusBadge(context, badgeX, badgeY, compatibility.isFullyCompatible() ? Component.translatable("pathmind.marketplace.compatible").getString() : Component.translatable("pathmind.marketplace.versionCheck").getString(),
            compatibility.summaryColor(), true) + 6;
        if (screen.isPresetSavedLocally(screen.popupPreset)) {
            screen.drawStatusBadge(context, badgeX, badgeY, Component.translatable("pathmind.marketplace.saved").getString(), UITheme.MARKETPLACE_SAVE, true);
        }
        cursorY += 22;

        if (screen.popupMetadataEditing) {
            cursorY = screen.drawPopupEditableField(context, mouseX, mouseY, textX, cursorY, textWidth, Component.translatable("pathmind.field.tags").getString(), screen.publishTagsField) + 1;
        } else {
            String tagsLine = PathmindMarketplaceScreen.formatTags(screen.popupPreset.getTags());
            if (!tagsLine.isBlank() && !"untagged".equalsIgnoreCase(tagsLine)) {
                cursorY = screen.drawWrappedValue(
                    context,
                    textX,
                    cursorY,
                    textWidth,
                    Component.translatable("pathmind.marketplace.tagsValue", tagsLine).getString(),
                    screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY),
                    2
                );
            }
        }

        int visibilityY = cursorY + 10;
        String visibilityLabel = screen.popupMetadataEditing
            ? Component.translatable("pathmind.field.visibility").getString()
            : Component.translatable("pathmind.marketplace.visibilityValue", screen.popupPreset.isPublished()
                ? Component.translatable("pathmind.option.public").getString()
                : Component.translatable("pathmind.option.private").getString()).getString();
        int visibilityColor = screen.popupMetadataEditing
            ? UITheme.TEXT_LABEL
            : (screen.popupPreset.isPublished() ? screen.getAccentColor() : UITheme.STATE_WARNING);
        context.drawString(screen.textRenderer(), Component.literal(visibilityLabel), textX, visibilityY,
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
            : Math.max(42, screen.measureWrappedValueHeight(textWidth - 16, screen.fallback(screen.popupPreset.getDescription(), Component.translatable("pathmind.marketplace.noDescription").getString()), 5) + 18);
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
        context.drawString(screen.textRenderer(), Component.translatable("pathmind.marketplace.about"), textX + 8, descriptionTop + 6,
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
                screen.fallback(screen.popupPreset.getDescription(), Component.translatable("pathmind.marketplace.noDescription").getString()),
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
        context.drawString(screen.textRenderer(), Component.translatable("pathmind.marketplace.compatibility"), textX + 8, cursorY + 14,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        context.drawString(screen.textRenderer(), Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(),
                compatibilityStatus.minecraftLine() + "  •  " + compatibilityStatus.pathmindLine(), textWidth - 16)),
            textX + 8, cursorY + 24,
            screen.presetPopupAnimation.getAnimatedPopupColor(compatibilityStatus.minecraftColor()));
        cursorY += compatibilityHeight + 18;

        String sharedLine = Component.translatable("pathmind.marketplace.publishedUpdated", PathmindMarketplaceScreen.formatTimestamp(screen.popupPreset.getCreatedAt()), PathmindMarketplaceScreen.formatTimestamp(screen.popupPreset.getUpdatedAt())).getString();
        cursorY = screen.drawWrappedValue(context, textX, cursorY, textWidth, sharedLine,
            screen.presetPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        if (screen.authSession == null) {
            cursorY = screen.drawWrappedValue(context, textX, cursorY, textWidth,
                Component.translatable("pathmind.marketplace.signInLikeImport").getString(),
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
            context.drawString(screen.textRenderer(),
                Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.popupStatusMessage, textWidth)),
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
                screen.publishBusy ? Component.translatable("pathmind.status.working").getString() : Component.translatable("pathmind.button.update").getString(), updateHovered, !updateEnabled, screen.presetPopupAnimation, screen.popupUpdateHoverAnimation.getValue());
        }
        screen.drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.getPopupAuthButtonLabel(), authHovered, screen.authBusy || screen.publishBusy, screen.presetPopupAnimation);
        if (ownPreset) {
            screen.drawAnimatedActionButton(context, deleteButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
                screen.deleteBusy ? "..." : Component.translatable("pathmind.button.delete").getString(), deleteHovered, screen.deleteBusy || screen.publishBusy, screen.presetPopupAnimation, screen.popupDeleteHoverAnimation.getValue());
        }
        screen.drawAnimatedActionButton(context, downloadButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.importingPreset ? Component.translatable("pathmind.status.downloading").getString() : Component.translatable("pathmind.button.download").getString(), downloadHovered, screen.importingPreset || screen.deleteBusy, screen.presetPopupAnimation);
        context.disableScissor();
    }

    void renderAccountPopup(GuiGraphics context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
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
        PathmindPopupRenderer.beginPopup(context, popupX, popupY, popupWidth, popupHeight, screen.accountPopupAnimation);
        PathmindPopupRenderer.drawHeaderBar(
            context,
            screen.textRenderer(),
            Component.translatable("pathmind.marketplace.account"),
            popupX,
            popupY,
            popupWidth,
            screen.accountPopupAnimation
        );

        int avatarSize = 52;
        int contentX = popupX + 12;
        int contentY = popupY + 42;
        screen.renderAccountAvatar(context, popupX + 14, popupY + 42, avatarSize);
        int textX = contentX + avatarSize + 12;
        int textWidth = popupWidth - (textX - popupX) - 12;
        context.drawString(screen.textRenderer(),
            Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(),
                screen.fallback(screen.authSession.getDisplayName(), screen.fallback(screen.authSession.getEmail(), Component.translatable("pathmind.status.discordUser").getString())), textWidth)),
            textX, contentY + 2, screen.accountPopupAnimation.getAnimatedPopupColor(screen.getAccentColor()));
        contentY += 16;
        contentY = screen.drawWrappedValue(context, textX, contentY + 2, textWidth,
            Component.translatable("pathmind.marketplace.provider", screen.fallback(screen.authSession.getProvider(), "discord")).getString(),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);
        screen.drawWrappedValue(context, textX, contentY, textWidth,
            Component.translatable("pathmind.marketplace.userId", screen.fallback(screen.authSession.getUserId(), Component.translatable("pathmind.marketplace.unknown").getString())).getString(),
            screen.accountPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);
        int closeButtonX = popupX + (popup.closeButtonX() - popup.x());
        int signOutButtonX = popupX + (popup.signOutButtonX() - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean closeHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, closeButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean signOutHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, signOutButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.drawAnimatedActionButton(context, closeButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Component.translatable("pathmind.button.close").getString(), closeHovered, false, screen.accountPopupAnimation);
        screen.drawAnimatedActionButton(context, signOutButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Component.translatable("pathmind.button.signOut").getString(), signOutHovered, screen.authBusy, screen.accountPopupAnimation);
        context.disableScissor();
    }

    void renderPublishPopup(GuiGraphics context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
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
        PathmindPopupRenderer.beginPopup(context, popupX, popupY, popupWidth, popupHeight, screen.publishPopupAnimation);

        Component title = screen.editingPreset == null ? Component.translatable("pathmind.marketplace.publishPreset") : Component.translatable("pathmind.marketplace.editMetadata");
        PathmindPopupRenderer.drawHeaderBar(
            context,
            screen.textRenderer(),
            title,
            popupX,
            popupY,
            popupWidth,
            screen.publishPopupAnimation
        );

        int contentX = popupX + 12;
        int contentWidth = popupWidth - 24;
        int sourceY = popupY + 40;
        String sourceLine = screen.editingPreset == null
            ? Component.translatable("pathmind.marketplace.sourcePresetValue", screen.fallback(screen.publishSourcePresetName, Component.translatable("pathmind.marketplace.unknown").getString())).getString()
            : Component.translatable("pathmind.marketplace.editingListingBy", screen.fallback(screen.editingPreset.getAuthorName(), Component.translatable("pathmind.marketplace.unknown").getString())).getString();
        screen.drawWrappedValue(context, contentX, sourceY, contentWidth, sourceLine,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        int fieldWidth = popupWidth - 24;
        int fieldHeight = 18;
        int labelGap = 11;
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 53, fieldWidth, fieldHeight, Component.translatable("pathmind.field.name").getString(), screen.publishNameField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 92, fieldWidth, fieldHeight, Component.translatable("pathmind.field.description").getString(), screen.publishDescriptionField, labelGap);
        drawPublishField(context, mouseX, mouseY, contentX, popupY + 131, fieldWidth, fieldHeight, Component.translatable("pathmind.field.tags").getString(), screen.publishTagsField, labelGap);

        screen.drawWrappedValue(context, contentX, popupY + 166, contentWidth, Component.translatable("pathmind.marketplace.tagsHint").getString(),
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY), 2);

        int visibilityLabelY = popupY + 189;
        context.drawString(screen.textRenderer(), Component.translatable("pathmind.field.visibility"), contentX, visibilityLabelY,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_LABEL));
        PathmindMarketplaceScreen.Rect publishToggle = screen.getPublishPopupVisibilityToggleRect(popupX, popupY, popupWidth);
        screen.publishVisibilityToggle.setValue(screen.publishVisibilityPublic);
        screen.publishVisibilityToggle.setPosition(publishToggle.x(), publishToggle.y());
        screen.publishVisibilityToggle.render(context, mouseX, mouseY, screen.publishPopupAnimation.getPopupAlpha());
        String visibilityHint = screen.publishVisibilityPublic
            ? Component.translatable("pathmind.marketplace.visiblePublic").getString()
            : Component.translatable("pathmind.marketplace.visiblePrivate").getString();
        screen.drawWrappedValue(context, contentX + 78, visibilityLabelY + 2, contentWidth - 78, visibilityHint,
            screen.publishPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY), 2);

        if (!screen.publishStatusMessage.isEmpty()) {
            context.drawString(screen.textRenderer(),
                Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), screen.publishStatusMessage, contentWidth)),
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
            Component.translatable("pathmind.button.cancel").getString(), cancelHovered, false, screen.publishPopupAnimation);
        screen.drawAnimatedActionButton(context, authButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.getPublishAuthButtonLabel(), authHovered, screen.publishBusy || screen.authSession != null, screen.publishPopupAnimation);
        screen.drawAnimatedActionButton(context, submitButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            screen.publishBusy ? Component.translatable("pathmind.status.working").getString() : (screen.editingPreset == null ? Component.translatable("pathmind.marketplace.publish").getString() : Component.translatable("pathmind.button.save").getString()),
            submitHovered, screen.publishBusy, screen.publishPopupAnimation);
        context.disableScissor();
    }

    void renderConfirmPopup(GuiGraphics context, int mouseX, int mouseY, PathmindMarketplaceScreen.Layout layout) {
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
        String title = deleteAction ? Component.translatable("pathmind.marketplace.deleteUploadedPreset").getString() : Component.translatable("pathmind.marketplace.updateUploadedPreset").getString();
        String lineOne = deleteAction ? Component.translatable("pathmind.marketplace.deleteUploadedConfirm").getString() : Component.translatable("pathmind.marketplace.overwriteUploadedConfirm").getString();
        String lineTwo = deleteAction ? Component.translatable("pathmind.marketplace.deleteUploadedWarning").getString() : Component.translatable("pathmind.marketplace.overwriteUploadedWarning").getString();

        context.drawCenteredString(screen.textRenderer(), Component.literal(title),
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
            context.drawString(screen.textRenderer(), Component.translatable("pathmind.marketplace.sourcePreset"), sourceX, sourceLabelY,
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
            String displayText = displayPresetName.isBlank() ? Component.translatable("pathmind.marketplace.selectLocalPreset").getString() : displayPresetName;
            int textColor = displayPresetName.isBlank() ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            context.drawString(screen.textRenderer(),
                Component.literal(TextRenderUtil.trimWithEllipsis(screen.textRenderer(), displayText, sourceWidth - 24)),
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
        PathmindPopupRenderer.drawCheckbox(context, checkboxX, checkboxY, skipConfirm, checkboxHovered,
            screen.getAccentColor(), screen.confirmPopupAnimation);
        context.drawString(screen.textRenderer(), Component.translatable("pathmind.option.dontShowAgain"),
            checkboxX + 18, checkboxY + 1, screen.confirmPopupAnimation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));

        int cancelButtonX = popupX + (popup.cancelButtonX() - popup.x());
        int confirmButtonX = popupX + (popup.confirmButtonX() - popup.x());
        int buttonY = popupY + (popup.buttonY() - popup.y());
        boolean cancelHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        boolean confirmHovered = PathmindMarketplaceScreen.isPointInRect(mouseX, mouseY, confirmButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight());
        screen.drawAnimatedActionButton(context, cancelButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            Component.translatable("pathmind.button.cancel").getString(), cancelHovered, false, screen.confirmPopupAnimation);
        screen.drawAnimatedActionButton(context, confirmButtonX, buttonY, popup.buttonWidth(), popup.buttonHeight(),
            deleteAction ? Component.translatable("pathmind.button.delete").getString() : Component.translatable("pathmind.button.update").getString(),
            confirmHovered, false, screen.confirmPopupAnimation);
        if (confirmAction == PathmindMarketplaceScreen.ConfirmAction.UPDATE && (screen.updateConfirmSourceDropdownOpen || screen.updateConfirmSourceDropdownAnimation.getValue() > 0.001f)) {
            renderUpdateConfirmSourceDropdown(context, mouseX, mouseY, popupX, popupY, popupWidth);
        }
        context.disableScissor();
    }

    private int drawPublishField(GuiGraphics context, int mouseX, int mouseY, int x, int y, int width, int height,
                                 String label, EditBox field, int labelGap) {
        return PathmindPopupRenderer.drawPopupTextFieldRow(context, screen.textRenderer(), field, mouseX, mouseY,
            x, y, width, label, screen.getAccentColor(), screen.publishPopupAnimation);
    }

    private void renderUpdateConfirmSourceDropdown(GuiGraphics context, int mouseX, int mouseY, int popupX, int popupY, int popupWidth) {
        List<String> presetOptions = screen.getUpdateSourcePresetOptions();
        if (presetOptions.isEmpty()) {
            return;
        }
        float animProgress = AnimationHelper.easeOutQuad(screen.updateConfirmSourceDropdownAnimation.getValue());
        if (animProgress <= 0.001f) {
            return;
        }
        int visibleCount = Math.min(6, presetOptions.size());
        PathmindMarketplaceScreen.Rect bounds = screen.getUpdateConfirmSourceDropdownBounds(popupX, popupY, popupWidth, presetOptions.size());
        PathmindDropdownRenderer.renderTextList(
            context,
            screen.textRenderer(),
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(bounds.x(), bounds.y(), bounds.width())
                .rows(PathmindMarketplaceScreen.SORT_OPTION_HEIGHT, visibleCount, presetOptions.size())
                .scroll(0, 0, 0)
                .animation(animProgress)
                .hoverPoint(mouseX, mouseY)
                .colors(screen.getAccentColor(), UITheme.TEXT_PRIMARY)
                .textLayout(8, 5, false, true)
                .labels("", presetOptions::get)
                .textColors(index -> presetOptions.get(index).equalsIgnoreCase(screen.publishSourcePresetName) ? screen.getAccentColor() : UITheme.TEXT_PRIMARY)
                .chrome(new UIStyleHelper.ScrollContainerPalette(
                    UITheme.BACKGROUND_SECONDARY,
                    UITheme.BORDER_DEFAULT,
                    UITheme.PANEL_INNER_BORDER,
                    UITheme.BORDER_DEFAULT,
                    UITheme.BORDER_DEFAULT
                ), UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT, UITheme.BORDER_DEFAULT)
                .build()
        );
    }

}
