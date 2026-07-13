package com.pathmind.screen;

import static com.pathmind.screen.PathmindVisualEditorScreen.*;

import com.pathmind.data.OnboardingPresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.PathmindSettingsRowRenderer;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.ScrollbarHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PathmindSettingsPopupController {
    final PathmindVisualEditorScreen screen;

    PathmindSettingsPopupController(PathmindVisualEditorScreen screen) {
        this.screen = screen;
    }

    void renderSettingsPopup(DrawContext context, int mouseX, int mouseY) {
        float popupAlpha = screen.settingsPopupAnimation.getPopupAlpha();

        int popupWidth = screen.getSettingsPopupWidth();
        int popupHeight = screen.getSettingsPopupHeight();
        int[] bounds = screen.settingsPopupAnimation.getScaledPopupBounds(screen.screenWidth(), screen.screenHeight(), popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        screen.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);
        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, screen.settingsPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            screen.textRenderer(),
            Text.translatable("pathmind.settings.title"),
            popupX,
            popupY,
            scaledWidth,
            screen.settingsPopupAnimation
        );

        PathmindPopupLayout.Rect bodyBounds = getSettingsPopupBodyRect(popupX, popupY, scaledWidth, scaledHeight);
        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, scaledWidth, scaledHeight);
        screen.settingsPopupScrollOffset = MathHelper.clamp(screen.settingsPopupScrollOffset, 0, maxScroll);
        int contentPopupY = popupY - screen.settingsPopupScrollOffset;
        PathmindPopupRenderer.enableBodyScissor(context, bodyBounds);
        int contentX = popupX + 20;

        // Language section
        int languageLabelY = contentPopupY + 44;
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.language").getString(), contentX, languageLabelY, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        // Language dropdown button
        int languageButtonY = languageLabelY + 12;
        int languageButtonWidth = scaledWidth - 40;

        // Store dropdown position for rendering later
        screen.languageDropdownX = contentX;
        screen.languageDropdownY = languageButtonY;
        screen.languageDropdownWidth = languageButtonWidth;
        screen.languageDropdownClipX = bodyBounds.x();
        screen.languageDropdownClipY = bodyBounds.y();
        screen.languageDropdownClipWidth = bodyBounds.width();
        screen.languageDropdownClipHeight = bodyBounds.height();

        String currentLang = screen.client().getLanguageManager().getLanguage();
        String langDisplayName = screen.getLanguageDisplayName(currentLang);
        boolean languageHovered = mouseX >= contentX && mouseX <= contentX + languageButtonWidth && mouseY >= languageButtonY && mouseY <= languageButtonY + 20;
        screen.drawLanguageDropdown(context, contentX, languageButtonY, languageButtonWidth, langDisplayName, languageHovered);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Adjust following sections downward by 50 pixels
        int accentLabelY = languageButtonY + 50;
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.accent").getString(), contentX, accentLabelY, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            boolean hovered = screen.isPointInRect(mouseX, mouseY, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT);
            boolean selected = screen.accentOption == option;
            drawAccentOption(context, optionX, accentOptionsY, option, hovered, selected);
            optionIndex++;
        }

        int sectionDividerX = popupX + 16;
        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, sectionDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int settingDividerY = sectionDividerY + 22;
        int gridRowCenterY = (sectionDividerY + settingDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, gridRowCenterY, Text.translatable("pathmind.settings.screen.showGrid").getString(), screen.showGrid, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, settingDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int lowDetailDividerY = settingDividerY + 22;
        int lowDetailRowCenterY = (settingDividerY + lowDetailDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, lowDetailRowCenterY, Text.translatable("pathmind.settings.lowDetailMode").getString(),
            Boolean.TRUE.equals(screen.currentSettings.lowDetailMode), popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, lowDetailDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int footerDividerY = lowDetailDividerY + 22;
        int tooltipRowCenterY = (lowDetailDividerY + footerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, tooltipRowCenterY, Text.translatable("pathmind.settings.showTooltips").getString(), screen.showWorkspaceTooltips, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, footerDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, chatRowCenterY, Text.translatable("pathmind.settings.screen.showChatErrors").getString(), screen.showChatErrors, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, chatDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, overlayRowCenterY, Text.translatable("pathmind.settings.screen.showHudOverlays").getString(), screen.showHudOverlays, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, overlayDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int profilerDividerY = overlayDividerY + 22;
        int profilerRowCenterY = (overlayDividerY + profilerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, profilerRowCenterY, Text.translatable("pathmind.settings.showProfilerOverlay").getString(),
            screen.currentSettings != null && Boolean.TRUE.equals(screen.currentSettings.showProfilerOverlay), popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, profilerDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int delayDividerY = profilerDividerY + 26;
        int delayRowCenterY = (profilerDividerY + delayDividerY) / 2;
        renderNodeDelayRow(context, mouseX, mouseY, contentX, delayRowCenterY, screen.nodeDelayMs, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, delayDividerY,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int nodeSettingsLabelY = getSettingsNodeSectionLabelY(contentPopupY);
        int nodeSettingsBodyY = nodeSettingsLabelY + 14;
        if (screen.createListRadiusField != null) {
            screen.createListRadiusField.setVisible(false);
        }
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings").getString(), contentX, nodeSettingsLabelY, scaledWidth - 40,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_SECONDARY));
        int selectorWidth = scaledWidth - 40;
        renderSettingsNodeTypeSelector(context, mouseX, mouseY, contentX, nodeSettingsBodyY, selectorWidth);
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY, selectorWidth);

        NodeType targetType = getEffectiveSettingsTargetType();
        if (targetType == null) {
            screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings.none").getString(), contentX, nodeSettingsContentY,
                scaledWidth - 40, screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));
        } else if (targetType == NodeType.GOTO) {
            screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));

            int gotoBreakDividerY = nodeSettingsContentY + 28;
            int gotoBreakRowCenterY = (nodeSettingsContentY + 10 + gotoBreakDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, gotoBreakRowCenterY,
                Text.translatable("pathmind.settings.gotoAllowBreak").getString(), screen.currentSettings.gotoAllowBreakWhileExecuting != null && screen.currentSettings.gotoAllowBreakWhileExecuting, popupX, scaledWidth);
            context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, gotoBreakDividerY,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

            int gotoPlaceDividerY = gotoBreakDividerY + 22;
            int gotoPlaceRowCenterY = (gotoBreakDividerY + gotoPlaceDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, gotoPlaceRowCenterY,
                Text.translatable("pathmind.settings.gotoAllowPlace").getString(), screen.currentSettings.gotoAllowPlaceWhileExecuting != null && screen.currentSettings.gotoAllowPlaceWhileExecuting, popupX, scaledWidth);
            context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, gotoPlaceDividerY,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));
        } else if (targetType == NodeType.SENSOR_KEY_PRESSED) {
            screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));

            int keyPressedDividerY = nodeSettingsContentY + 28;
            int keyPressedRowCenterY = (nodeSettingsContentY + 10 + keyPressedDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, keyPressedRowCenterY,
                Text.translatable("pathmind.settings.keyPressedActivatesInGuis").getString(), screen.currentSettings.keyPressedActivatesInGuis == null || screen.currentSettings.keyPressedActivatesInGuis, popupX, scaledWidth);
            context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, keyPressedDividerY,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));
        } else if (targetType == NodeType.CREATE_LIST) {
            Node targetNode = getEffectiveSettingsTargetNode();
            boolean useRadius = isCreateListCustomRadiusEnabled(targetNode);
            int radius = getCreateListSettingsRadius(targetNode);
            screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));

            int createListToggleDividerY = nodeSettingsContentY + 28;
            int createListToggleRowCenterY = (nodeSettingsContentY + 10 + createListToggleDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, createListToggleRowCenterY,
                Text.translatable("pathmind.settings.createListUseCustomRadius").getString(), useRadius, popupX, scaledWidth);
            context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, createListToggleDividerY,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));

            if (useRadius) {
                int createListRadiusDividerY = createListToggleDividerY + 26;
                int createListRadiusRowCenterY = (createListToggleDividerY + createListRadiusDividerY) / 2;
                renderCreateListRadiusRow(context, mouseX, mouseY, contentX, createListRadiusRowCenterY,
                    radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX, popupX, scaledWidth);
                context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, createListRadiusDividerY,
                    screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));
            }
        }

        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int clearCacheRowCenterY = getSettingsClearCacheRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsClearCacheDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.clearCache").getString(), contentX, clearCacheRowCenterY - screen.textRenderer().fontHeight / 2,
            scaledWidth - 40 - clearCacheButtonBounds[2] - 12, screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_PRIMARY));
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            PathmindPopupLayout.rect(clearCacheButtonBounds[0], clearCacheButtonBounds[1], clearCacheButtonBounds[2], clearCacheButtonBounds[3]),
            mouseX,
            mouseY,
            Text.translatable("pathmind.button.clear"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            screen.getAccentColor(),
            screen.settingsPopupAnimation
        );

        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int restoreExamplesRowCenterY = getSettingsRestoreExamplesRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsRestoreExamplesDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE));
        screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.examplePresets").getString(), contentX,
            restoreExamplesRowCenterY - screen.textRenderer().fontHeight / 2,
            scaledWidth - 40 - restoreExamplesButtonBounds[2] - 12, screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_PRIMARY));
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            PathmindPopupLayout.rect(restoreExamplesButtonBounds[0], restoreExamplesButtonBounds[1], restoreExamplesButtonBounds[2], restoreExamplesButtonBounds[3]),
            mouseX,
            mouseY,
            Text.translatable("pathmind.button.restore"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            screen.getAccentColor(),
            screen.settingsPopupAnimation
        );

        PathmindPopupLayout.Rect closeButton = PathmindPopupLayout.settingsCloseButton(popupX, popupY, scaledWidth, scaledHeight, 90, 20);
        context.disableScissor();
        PathmindPopupRenderer.drawScrollableBodyChrome(
            context,
            bodyBounds,
            screen.settingsPopupScrollOffset,
            maxScroll,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE)
        );
        renderSettingsPopupScrollbar(context, popupX, popupY, scaledWidth, scaledHeight, maxScroll);
        PathmindPopupRenderer.drawButton(
            context,
            screen.textRenderer(),
            closeButton,
            mouseX,
            mouseY,
            Text.translatable("pathmind.button.close"),
            PathmindPopupRenderer.ButtonStyle.ACCENT,
            screen.getAccentColor(),
            screen.settingsPopupAnimation
        );
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void drawAccentOption(DrawContext context, int x, int y, AccentOption option, boolean hovered, boolean selected) {
        float hoverProgress = selected ? 1f : screen.getHoverProgress("settings-accent-option:" + option.name(), hovered);
        PathmindSettingsRowRenderer.renderAccentOption(
            context,
            screen.textRenderer(),
            x,
            y,
            SETTINGS_OPTION_WIDTH,
            SETTINGS_OPTION_HEIGHT,
            option.label,
            option.color,
            selected,
            hoverProgress,
            screen.getAccentColor(),
            screen.settingsPopupAnimation
        );
    }

    void renderToggleRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label, boolean active, int popupX, int scaledWidth) {
        PathmindSettingsRowRenderer.renderToggleRow(
            context,
            screen.textRenderer(),
            mouseX,
            mouseY,
            labelX,
            centerY,
            label,
            active,
            popupX,
            scaledWidth,
            SETTINGS_TOGGLE_WIDTH,
            SETTINGS_TOGGLE_HEIGHT,
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            Text.translatable("pathmind.settings.on").getString(),
            Text.translatable("pathmind.settings.off").getString()
        );
    }

    void renderSliderRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label,
                                 int value, int min, int max, int popupX, int scaledWidth) {
        PathmindSettingsRowRenderer.renderSliderRow(
            context,
            screen.textRenderer(),
            mouseX,
            mouseY,
            labelX,
            centerY,
            label,
            value,
            min,
            max,
            popupX,
            scaledWidth,
            SETTINGS_SLIDER_WIDTH,
            SETTINGS_SLIDER_HEIGHT,
            SETTINGS_SLIDER_HANDLE_WIDTH,
            SETTINGS_SLIDER_HANDLE_HEIGHT,
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            PathmindVisualEditorScreen.tr("pathmind.unit.millisecondsShort"),
            screen.nodeDelayDragging
        );
    }

    void renderNodeDelayRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY,
                                    int value, int min, int max, int popupX, int scaledWidth) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;

        String valueText = Integer.toString(value);
        int[] valueBox = getNodeDelayFieldBounds(popupX, scaledWidth, centerY, valueText);
        boolean fieldHovered = screen.isPointInRect(mouseX, mouseY, valueBox[0], valueBox[1], valueBox[2], valueBox[3]);
        boolean focused = screen.nodeDelayField != null && screen.nodeDelayField.isFocused();
        float fieldHoverProgress = focused ? 1f : screen.getHoverProgress("settings-node-delay-field", fieldHovered);
        PathmindSettingsRowRenderer.renderNumericField(
            context,
            screen.textRenderer(),
            screen.nodeDelayField,
            mouseX,
            mouseY,
            labelX,
            centerY,
            Text.translatable("pathmind.settings.nodeDelay").getString(),
            valueBox[0],
            valueBox[1],
            valueBox[2],
            valueBox[3],
            valueText,
            Text.translatable("pathmind.unit.millisecondsShort"),
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            fieldHoverProgress,
            focused,
            TEXT_FIELD_VERTICAL_PADDING
        );

        boolean hovered = screen.isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        float sliderHoverProgress = screen.nodeDelayDragging ? 1f : screen.getHoverProgress("settings-node-delay-slider", hovered);
        PathmindSettingsRowRenderer.renderNumericSlider(
            context,
            centerY,
            sliderX,
            sliderY,
            SETTINGS_SLIDER_WIDTH,
            SETTINGS_SLIDER_HEIGHT,
            SETTINGS_SLIDER_HANDLE_WIDTH,
            SETTINGS_SLIDER_HANDLE_HEIGHT,
            value,
            min,
            max,
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            sliderHoverProgress
        );
    }

    void renderCreateListRadiusRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY,
                                           int value, int min, int max, int popupX, int scaledWidth) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;

        String valueText = Integer.toString(value);
        int[] valueBox = getCreateListRadiusFieldBounds(popupX, scaledWidth, centerY, valueText);
        boolean fieldHovered = screen.isPointInRect(mouseX, mouseY, valueBox[0], valueBox[1], valueBox[2], valueBox[3]);
        boolean focused = screen.createListRadiusField != null && screen.createListRadiusField.isFocused();
        float fieldHoverProgress = focused ? 1f : screen.getHoverProgress("settings-create-list-radius-field", fieldHovered);
        PathmindSettingsRowRenderer.renderNumericField(
            context,
            screen.textRenderer(),
            screen.createListRadiusField,
            mouseX,
            mouseY,
            labelX,
            centerY,
            Text.translatable("pathmind.field.radius").getString(),
            valueBox[0],
            valueBox[1],
            valueBox[2],
            valueBox[3],
            valueText,
            Text.translatable("pathmind.unit.blocks"),
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            fieldHoverProgress,
            focused,
            TEXT_FIELD_VERTICAL_PADDING
        );

        boolean hovered = screen.isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        float sliderHoverProgress = screen.createListRadiusDragging ? 1f : screen.getHoverProgress("settings-create-list-radius-slider", hovered);
        PathmindSettingsRowRenderer.renderNumericSlider(
            context,
            centerY,
            sliderX,
            sliderY,
            SETTINGS_SLIDER_WIDTH,
            SETTINGS_SLIDER_HEIGHT,
            SETTINGS_SLIDER_HANDLE_WIDTH,
            SETTINGS_SLIDER_HANDLE_HEIGHT,
            value,
            min,
            max,
            screen.getAccentColor(),
            screen.settingsPopupAnimation,
            sliderHoverProgress
        );
    }

    int[] getNodeDelayFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = screen.textRenderer().getWidth(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = screen.textRenderer().getWidth(PathmindVisualEditorScreen.tr("pathmind.unit.millisecondsShort"));
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    int[] getCreateListRadiusFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = screen.textRenderer().getWidth(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = screen.textRenderer().getWidth(PathmindVisualEditorScreen.tr("pathmind.unit.blocks"));
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    void updateNodeDelayFromMouse(int mouseX, int popupX, int popupWidth) {
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = MathHelper.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
        float t = SETTINGS_SLIDER_WIDTH <= 0 ? 0f : localX / (float) SETTINGS_SLIDER_WIDTH;
        int value = NODE_DELAY_MIN_MS + Math.round(t * (NODE_DELAY_MAX_MS - NODE_DELAY_MIN_MS));
        if (value != screen.nodeDelayMs) {
            screen.nodeDelayMs = value;
            screen.currentSettings.nodeDelayMs = screen.nodeDelayMs;
            SettingsManager.save(screen.currentSettings);
        }
    }

    void updateCreateListRadiusFromMouse(Node node, int mouseX, int popupX, int popupWidth) {
        if (node != null && node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = MathHelper.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
        float t = SETTINGS_SLIDER_WIDTH <= 0 ? 0f : localX / (float) SETTINGS_SLIDER_WIDTH;
        int value = CREATE_LIST_RADIUS_MIN + Math.round(t * (CREATE_LIST_RADIUS_MAX - CREATE_LIST_RADIUS_MIN));
        if (value != getCreateListSettingsRadius(node)) {
            setCreateListSettingsRadius(node, value);
        }
    }

    Integer parseDelayFieldValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(digits);
            return MathHelper.clamp(parsed, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Integer parseCreateListRadiusFieldValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(digits);
            return MathHelper.clamp(parsed, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    boolean supportsNodeSettings(Node node) {
        return node != null && supportsNodeSettings(node.getType());
    }

    boolean supportsNodeSettings(NodeType type) {
        if (type == null) {
            return false;
        }
        for (NodeType candidate : SETTINGS_NODE_TYPES) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    boolean hasEditedNodeSettings(NodeType type) {
        if (!supportsNodeSettings(type) || screen.currentSettings == null) {
            return false;
        }
        return switch (type) {
            case GOTO -> Boolean.TRUE.equals(screen.currentSettings.gotoAllowBreakWhileExecuting)
                || Boolean.TRUE.equals(screen.currentSettings.gotoAllowPlaceWhileExecuting);
            case SENSOR_KEY_PRESSED -> screen.currentSettings.keyPressedActivatesInGuis != null
                && !screen.currentSettings.keyPressedActivatesInGuis;
            case CREATE_LIST -> {
                boolean edited = false;
                if (screen.nodeGraph != null) {
                    for (Node node : screen.nodeGraph.getNodes()) {
                        if (node != null && node.getType() == NodeType.CREATE_LIST) {
                            node.ensureCreateListRadiusParameters();
                            if (node.getParameter("UseRadius") != null && node.getParameter("UseRadius").getBoolValue()) {
                                edited = true;
                                break;
                            }
                        }
                    }
                }
                yield edited;
            }
            default -> false;
        };
    }

    boolean isCreateListCustomRadiusEnabled(Node node) {
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return Boolean.TRUE.equals(SettingsManager.getCurrent().createListUseCustomRadius);
        }
        node.ensureCreateListRadiusParameters();
        return node.getParameter("UseRadius") != null && node.getParameter("UseRadius").getBoolValue();
    }

    int getCreateListSettingsRadius(Node node) {
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            Integer configured = SettingsManager.getCurrent().createListRadius;
            return MathHelper.clamp(configured == null ? 64 : configured, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
        }
        node.ensureCreateListRadiusParameters();
        double value = 64.0;
        if (node.getParameter("Radius") != null) {
            try {
                value = Double.parseDouble(node.getParameter("Radius").getStringValue().trim());
            } catch (Exception ignored) {
                value = 64.0;
            }
        }
        return MathHelper.clamp((int) Math.round(value), CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
    }

    void setCreateListCustomRadiusEnabled(Node node, boolean enabled) {
        Settings settings = SettingsManager.getCurrent();
        settings.createListUseCustomRadius = enabled;
        SettingsManager.save(settings);
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        node.ensureCreateListRadiusParameters();
        node.setParameterValueAndPropagate("UseRadius", Boolean.toString(enabled));
        if (screen.nodeGraph != null) {
            screen.nodeGraph.notifyNodeParametersChanged(node);
        }
    }

    void setCreateListSettingsRadius(Node node, int radius) {
        int clamped = MathHelper.clamp(radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
        Settings settings = SettingsManager.getCurrent();
        settings.createListRadius = clamped;
        SettingsManager.save(settings);
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        node.ensureCreateListRadiusParameters();
        node.setParameterValueAndPropagate("Radius", Integer.toString(clamped));
        if (screen.nodeGraph != null) {
            screen.nodeGraph.notifyNodeParametersChanged(node);
        }
    }

    List<NodeType> getSettingsNodeTypes() {
        List<NodeType> result = new ArrayList<>();
        for (NodeType type : SETTINGS_NODE_TYPES) {
            result.add(type);
        }
        return result;
    }

    NodeType getEffectiveSettingsTargetType() {
        if (supportsNodeSettings(screen.settingsNodeTargetType)) {
            return screen.settingsNodeTargetType;
        }
        if (supportsNodeSettings(screen.settingsNodeTarget)) {
            return screen.settingsNodeTarget.getType();
        }
        return null;
    }

    Node findFirstNodeWithSettingsType(NodeType type) {
        if (!supportsNodeSettings(type) || screen.nodeGraph == null) {
            return null;
        }
        for (Node node : screen.nodeGraph.getNodes()) {
            if (node != null && node.getType() == type) {
                return node;
            }
        }
        return null;
    }

    boolean hasNodeWithSettingsType(NodeType type) {
        return findFirstNodeWithSettingsType(type) != null;
    }

    Node getEffectiveSettingsTargetNode() {
        NodeType targetType = getEffectiveSettingsTargetType();
        if (targetType == null) {
            return null;
        }
        if (supportsNodeSettings(screen.settingsNodeTarget) && screen.settingsNodeTarget.getType() == targetType) {
            return screen.settingsNodeTarget;
        }
        return findFirstNodeWithSettingsType(targetType);
    }

    int getSettingsNodeSectionContentBottom(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        NodeType targetType = getEffectiveSettingsTargetType();
        if (screen.settingsNodeListView || targetType == null) {
            int[] listBounds = getSettingsNodeListBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
            return listBounds[1] + listBounds[3];
        } else if (targetType == NodeType.GOTO) {
            int gotoBreakDividerY = nodeSettingsContentY + 28;
            return gotoBreakDividerY + 22;
        } else if (targetType == NodeType.CREATE_LIST) {
            Node targetNode = getEffectiveSettingsTargetNode();
            boolean useRadius = isCreateListCustomRadiusEnabled(targetNode);
            int createListToggleDividerY = nodeSettingsContentY + 28;
            if (useRadius) {
                return createListToggleDividerY + 26;
            }
            return createListToggleDividerY;
        } else {
            return nodeSettingsContentY + 28;
        }
    }

    int[] getSettingsClearCacheButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int dividerY = getSettingsClearCacheDividerY(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int buttonY = dividerY + 8;
        int buttonX = popupX + popupWidth - SETTINGS_SECTION_BUTTON_WIDTH - 20;
        return new int[]{buttonX, buttonY, SETTINGS_SECTION_BUTTON_WIDTH, SETTINGS_SECTION_BUTTON_HEIGHT};
    }

    int getSettingsClearCacheRowCenterY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY)[1]
            + SETTINGS_SECTION_BUTTON_HEIGHT / 2;
    }

    int getSettingsClearCacheDividerY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsNodeSectionContentBottom(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY) + 10;
    }

    int getSettingsClearCacheSectionHeight() {
        return 76;
    }

    int getSettingsNodeSectionContentY(int bodyY, int contentWidth) {
        return bodyY + getSettingsNodeTypeSelectorViewportHeight(contentWidth) + SETTINGS_NODE_TYPE_SECTION_GAP;
    }

    void clearSettingsCache() {
        boolean cleared = Node.clearRecipeCache(screen.client());
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (cleared) {
            overlay.show(Text.translatable("pathmind.settings.cacheCleared").getString(), UITheme.STATE_SUCCESS);
        } else {
            overlay.show(Text.translatable("pathmind.settings.cacheNotFound").getString(), UITheme.STATE_ERROR);
        }
    }

    int[] getSettingsRestoreExamplesButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int dividerY = getSettingsRestoreExamplesDividerY(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int buttonY = dividerY + 8;
        int buttonX = popupX + popupWidth - SETTINGS_SECTION_BUTTON_WIDTH - 20;
        return new int[]{buttonX, buttonY, SETTINGS_SECTION_BUTTON_WIDTH, SETTINGS_SECTION_BUTTON_HEIGHT};
    }

    int getSettingsRestoreExamplesRowCenterY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsRestoreExamplesButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY)[1]
            + SETTINGS_SECTION_BUTTON_HEIGHT / 2;
    }

    int getSettingsRestoreExamplesDividerY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        return clearCacheButtonBounds[1] + clearCacheButtonBounds[3] + 10;
    }

    void restoreExamplePresets() {
        OnboardingPresetManager.RestoreResult result = OnboardingPresetManager.restoreExamplePresets();
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (result.success()) {
            screen.refreshAvailablePresets();
            overlay.show(Text.translatable("pathmind.settings.examplePresetsRestored").getString(), UITheme.STATE_SUCCESS);
        } else {
            overlay.show(Text.translatable("pathmind.settings.examplePresetsRestoreFailed").getString(), UITheme.STATE_ERROR);
        }
    }

    int getSettingsNodeSectionLabelY(int popupY) {
        return PathmindPopupLayout.settingsNodeSectionLabelY(popupY, SETTINGS_OPTION_HEIGHT);
    }

    int getSettingsNodeSectionBodyY(int popupY) {
        return PathmindPopupLayout.settingsNodeSectionBodyY(popupY, SETTINGS_OPTION_HEIGHT);
    }

    int getSettingsNodeTypeSelectorViewportHeight(int contentWidth) {
        return Math.min(SETTINGS_NODE_TYPE_SEARCH_HEIGHT + getSettingsNodeTypeListViewportHeight(contentWidth), SETTINGS_NODE_TYPE_SELECTOR_MAX_HEIGHT);
    }

    int getSettingsNodeTypeSelectorMaxScroll(int contentWidth) {
        return Math.max(0, getSettingsNodeTypeListContentHeight() - getSettingsNodeTypeListViewportHeight(contentWidth));
    }

    int[] getSettingsNodeTypeSelectorBounds(int contentX, int bodyY, int contentWidth) {
        return new int[]{contentX, bodyY, contentWidth, getSettingsNodeTypeSelectorViewportHeight(contentWidth)};
    }

    int[] getSettingsNodeTypeSearchFieldBounds(int contentX, int bodyY, int contentWidth) {
        return new int[]{
            contentX,
            bodyY,
            contentWidth,
            SETTINGS_NODE_TYPE_SEARCH_HEIGHT
        };
    }

    int getSettingsNodeTypeListY(int bodyY) {
        return bodyY + SETTINGS_NODE_TYPE_SEARCH_HEIGHT;
    }

    int getSettingsNodeTypeListContentHeight() {
        List<NodeType> filteredTypes = getFilteredSettingsNodeTypes();
        if (filteredTypes.isEmpty()) {
            return 0;
        }
        return filteredTypes.size() * SETTINGS_NODE_TYPE_BUTTON_HEIGHT
            + Math.max(0, filteredTypes.size() - 1) * SETTINGS_NODE_TYPE_BUTTON_GAP;
    }

    int getSettingsNodeTypeListViewportHeight(int contentWidth) {
        int maxListViewportHeight = Math.max(0, SETTINGS_NODE_TYPE_SELECTOR_MAX_HEIGHT - SETTINGS_NODE_TYPE_SEARCH_HEIGHT);
        int minListViewportHeight = Math.min(maxListViewportHeight, SETTINGS_NODE_TYPE_EMPTY_HEIGHT);
        int listContentHeight = getSettingsNodeTypeListContentHeight();
        if (listContentHeight <= 0) {
            return minListViewportHeight;
        }
        return Math.min(listContentHeight, maxListViewportHeight);
    }

    ScrollbarHelper.Metrics getSettingsNodeTypeSelectorScrollMetrics(int contentX, int bodyY, int contentWidth, int maxScroll) {
        int listY = getSettingsNodeTypeListY(bodyY);
        int listHeight = Math.max(1, getSettingsNodeTypeSelectorViewportHeight(contentWidth) - SETTINGS_NODE_TYPE_SEARCH_HEIGHT);
        return ScrollbarHelper.metrics(
            contentX + contentWidth - UITheme.SCROLLBAR_WIDTH,
            listY,
            UITheme.SCROLLBAR_WIDTH,
            listHeight,
            maxScroll,
            screen.settingsNodeSelectorScrollOffset,
            20
        );
    }

    int[] getSettingsNodeTypeButtonBounds(int contentX, int bodyY, int contentWidth, int maxScroll, int index) {
        int y = getSettingsNodeTypeListY(bodyY) + index * (SETTINGS_NODE_TYPE_BUTTON_HEIGHT + SETTINGS_NODE_TYPE_BUTTON_GAP) - screen.settingsNodeSelectorScrollOffset;
        int rowX = contentX + 2;
        int rowWidth = Math.max(0, contentWidth - 2 - (maxScroll > 0 ? UITheme.SCROLLBAR_WIDTH : 0));
        return new int[]{rowX, y, rowWidth, SETTINGS_NODE_TYPE_BUTTON_HEIGHT};
    }

    void renderSettingsNodeTypeSelector(DrawContext context, int mouseX, int mouseY, int contentX, int bodyY, int contentWidth) {
        int[] selectorBounds = getSettingsNodeTypeSelectorBounds(contentX, bodyY, contentWidth);
        int[] searchBounds = getSettingsNodeTypeSearchFieldBounds(contentX, bodyY, contentWidth);
        boolean searchHovered = screen.isPointInRect(mouseX, mouseY, searchBounds[0], searchBounds[1], searchBounds[2], searchBounds[3]);
        boolean searchFocused = screen.settingsNodeSearchField != null && screen.settingsNodeSearchField.isFocused();
        float searchHoverProgress = searchFocused ? 1f : screen.getHoverProgress("settings-node-search-box", searchHovered);
        UIStyleHelper.FieldPalette searchPalette = UIStyleHelper.getSearchFieldPalette(screen.getAccentColor(), searchHoverProgress, searchFocused, false);
        UIStyleHelper.ScrollContainerPalette selectorPalette = UIStyleHelper.getScrollContainerPalette(screen.getAccentColor(), 0f, true, false);
        int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(contentWidth);
        screen.settingsNodeSelectorScrollOffset = ScrollbarHelper.clampScroll(screen.settingsNodeSelectorScrollOffset, maxSelectorScroll);
        UIStyleHelper.drawScrollContainer(
            context,
            selectorBounds[0],
            selectorBounds[1],
            selectorBounds[2],
            selectorBounds[3],
            new UIStyleHelper.ScrollContainerPalette(
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.backgroundColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.borderColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.innerBorderColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.trackColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.thumbColor())
            )
        );
        UIStyleHelper.drawFieldFrame(
            context,
            searchBounds[0],
            searchBounds[1],
            searchBounds[2],
            searchBounds[3],
            new UIStyleHelper.FieldPalette(
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, searchPalette.backgroundColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, searchPalette.borderColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, searchPalette.innerBorderColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, searchPalette.textColor()),
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, searchPalette.placeholderColor())
            )
        );
        if (screen.settingsNodeSearchField != null) {
            int textFieldHeight = Math.max(10, SETTINGS_NODE_TYPE_SEARCH_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2);
            screen.settingsNodeSearchField.setVisible(true);
            screen.settingsNodeSearchField.setEditable(true);
            screen.settingsNodeSearchField.setSuggestion(!searchFocused && screen.settingsNodeSearchField.getText().isEmpty() ? PathmindVisualEditorScreen.tr("pathmind.search.nodeSettings") : null);
            screen.settingsNodeSearchField.setPosition(searchBounds[0] + 8, searchBounds[1] + TEXT_FIELD_VERTICAL_PADDING);
            screen.settingsNodeSearchField.setWidth(Math.max(0, searchBounds[2] - 16));
            screen.settingsNodeSearchField.setHeight(textFieldHeight);
            screen.settingsNodeSearchField.render(context, mouseX, mouseY, 0.0f);
        }

        ScrollbarHelper.Metrics selectorScrollMetrics = getSettingsNodeTypeSelectorScrollMetrics(contentX, bodyY, contentWidth, maxSelectorScroll);
        int listTop = searchBounds[1] + searchBounds[3];
        int listHeight = Math.max(0, selectorBounds[3] - searchBounds[3]);
        int listBottom = listTop + listHeight;
        int listContentRight = maxSelectorScroll > 0 ? selectorScrollMetrics.trackLeft() : selectorBounds[0] + selectorBounds[2];
        ScrollbarHelper.renderCutoffDividers(
            context,
            contentX,
            listContentRight - 1,
            listTop,
            listBottom,
            screen.settingsNodeSelectorScrollOffset,
            maxSelectorScroll,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE)
        );
        ScrollbarHelper.renderSettingsStyle(
            context,
            selectorScrollMetrics,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.trackColor()),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.borderColor()),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, selectorPalette.thumbColor())
        );

        int listClipBottom = Math.max(listTop, listBottom - 1);
        context.enableScissor(selectorBounds[0] + 1, listTop, selectorBounds[0] + selectorBounds[2] - 1, listClipBottom);
        NodeType selectedType = getEffectiveSettingsTargetType();
        List<NodeType> filteredTypes = getFilteredSettingsNodeTypes();
        for (int i = 0; i < filteredTypes.size(); i++) {
            NodeType type = filteredTypes.get(i);
            int[] bounds = getSettingsNodeTypeButtonBounds(contentX, bodyY, contentWidth, maxSelectorScroll, i);
            if (bounds[1] + bounds[3] < listTop || bounds[1] >= listClipBottom) {
                continue;
            }
            boolean hovered = screen.isPointInRect(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
            boolean selected = type == selectedType;
            float hoverProgress = selected ? 1f : screen.getHoverProgress("settings-node-selector:" + type.name(), hovered);
            PathmindSettingsRowRenderer.renderDescriptionListRow(
                context,
                screen.textRenderer(),
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3],
                type.getDisplayName(),
                getSettingsNodeTypeDescription(type),
                hovered,
                selected,
                hoverProgress,
                screen.getAccentColor(),
                screen.settingsPopupAnimation
            );
        }
        if (filteredTypes.isEmpty()) {
            context.drawTextWithShadow(screen.textRenderer(), Text.translatable("pathmind.settings.nodeSettings.noMatches"),
                contentX + 8, listTop + 8, screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));
        }
        context.disableScissor();
    }

    List<NodeType> getFilteredSettingsNodeTypes() {
        List<NodeType> filteredTypes = new ArrayList<>();
        String query = screen.settingsNodeSearchField != null ? screen.settingsNodeSearchField.getText() : "";
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (NodeType type : SETTINGS_NODE_TYPES) {
            if (normalizedQuery.isEmpty()) {
                filteredTypes.add(type);
                continue;
            }
            String displayName = type.getDisplayName().toLowerCase(Locale.ROOT);
            String description = getSettingsNodeTypeDescription(type).toLowerCase(Locale.ROOT);
            if (displayName.contains(normalizedQuery) || description.contains(normalizedQuery)) {
                filteredTypes.add(type);
            }
        }
        return filteredTypes;
    }

    String getSettingsNodeTypeDescription(NodeType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case GOTO -> Text.translatable("pathmind.settings.nodeSettings.description.goto").getString();
            case SENSOR_KEY_PRESSED -> Text.translatable("pathmind.settings.nodeSettings.description.keyPressed").getString();
            case CREATE_LIST -> Text.translatable("pathmind.settings.nodeSettings.description.createList").getString();
            default -> Text.translatable("pathmind.settings.nodeSettings.description.default").getString();
        };
    }

    int[] getSettingsNodeListBounds(int popupX, int popupY, int scaledWidth, int scaledHeight, int contentX, int bodyY) {
        int listX = contentX;
        int listY = bodyY + SETTINGS_NODE_LIST_GAP;
        int listWidth = scaledWidth - 40;
        int buttonY = popupY + scaledHeight - 20 - 16;
        int minListHeight = SETTINGS_NODE_LIST_ROW_HEIGHT * 4;
        int availableHeight = buttonY - 8 - listY - getSettingsClearCacheSectionHeight();
        int listHeight = Math.max(minListHeight, availableHeight);
        return new int[]{listX, listY, listWidth, listHeight};
    }

    void renderSettingsNodeList(DrawContext context, int mouseX, int mouseY, int popupX, int popupY, int scaledWidth, int scaledHeight, int contentX, int bodyY) {
        List<NodeType> settingsNodes = getSettingsNodeTypes();
        int[] listBounds = getSettingsNodeListBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, bodyY);
        int listX = listBounds[0];
        int listY = listBounds[1];
        int listWidth = listBounds[2];
        int listHeight = listBounds[3];
        if (settingsNodes.isEmpty()) {
            screen.drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeSettings.none").getString(), contentX, bodyY, scaledWidth - 40,
                screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.TEXT_TERTIARY));
            return;
        }

        int visibleRows = Math.max(1, listHeight / SETTINGS_NODE_LIST_ROW_HEIGHT);
        int maxScroll = Math.max(0, settingsNodes.size() - visibleRows);
        screen.settingsNodeListScrollOffset = MathHelper.clamp(screen.settingsNodeListScrollOffset, 0, maxScroll);

        UIStyleHelper.drawBeveledPanel(
            context,
            listX,
            listY,
            listWidth,
            listHeight,
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BACKGROUND_SECONDARY),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_SUBTLE),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.PANEL_INNER_BORDER)
        );

        context.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        int startIndex = screen.settingsNodeListScrollOffset;
        int endIndex = Math.min(settingsNodes.size(), startIndex + visibleRows + 1);
        for (int i = startIndex; i < endIndex; i++) {
            NodeType type = settingsNodes.get(i);
            int rowY = listY + (i - startIndex) * SETTINGS_NODE_LIST_ROW_HEIGHT;
            boolean hovered = screen.isPointInRect(mouseX, mouseY, listX, rowY, listWidth, SETTINGS_NODE_LIST_ROW_HEIGHT);
            boolean editing = getEffectiveSettingsTargetType() == type && !screen.settingsNodeListView;
            String status = editing ? Text.translatable("pathmind.settings.nodeSettings.status.editing").getString() : hasEditedNodeSettings(type) ? Text.translatable("pathmind.settings.nodeSettings.status.edited").getString() : "";
            PathmindSettingsRowRenderer.renderStatusListRow(
                context,
                screen.textRenderer(),
                listX,
                rowY,
                listWidth,
                SETTINGS_NODE_LIST_ROW_HEIGHT,
                type.getDisplayName(),
                status,
                hovered,
                editing,
                screen.getAccentColor(),
                screen.settingsPopupAnimation
            );
        }
        context.disableScissor();
    }

    PathmindPopupLayout.Rect getSettingsPopupBodyRect(int popupX, int popupY, int popupWidth, int popupHeight) {
        return PathmindPopupLayout.settingsBody(popupX, popupY, popupWidth, popupHeight);
    }

    int[] getSettingsPopupBodyBounds(int popupX, int popupY, int popupWidth, int popupHeight) {
        PathmindPopupLayout.Rect body = getSettingsPopupBodyRect(popupX, popupY, popupWidth, popupHeight);
        return new int[]{body.x(), body.y(), body.width(), body.height()};
    }

    int getSettingsPopupMaxScroll(int popupX, int popupY, int popupWidth, int popupHeight) {
        PathmindPopupLayout.Rect bodyBounds = getSettingsPopupBodyRect(popupX, popupY, popupWidth, popupHeight);
        int bodyBottom = bodyBounds.y() + bodyBounds.height();
        int contentX = popupX + 20;
        int nodeSettingsBodyY = getSettingsNodeSectionBodyY(popupY);
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY, popupWidth - 40);
        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int contentBottom = restoreExamplesButtonBounds[1] + restoreExamplesButtonBounds[3];
        return Math.max(0, contentBottom - bodyBottom + 24);
    }

    void renderSettingsPopupScrollbar(DrawContext context, int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        ScrollbarHelper.renderSettingsStyle(
            context,
            getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BACKGROUND_SIDEBAR),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_DEFAULT),
            screen.getPopupAnimatedColor(screen.settingsPopupAnimation, UITheme.BORDER_DEFAULT)
        );
    }

    ScrollbarHelper.Metrics getSettingsPopupScrollMetrics(int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        PathmindPopupLayout.Rect bodyBounds = getSettingsPopupBodyRect(popupX, popupY, popupWidth, popupHeight);
        return ScrollbarHelper.metrics(popupX + popupWidth - 12, bodyBounds.y(), 4, Math.max(1, bodyBounds.height()), maxScroll, screen.settingsPopupScrollOffset, 20);
    }
}
