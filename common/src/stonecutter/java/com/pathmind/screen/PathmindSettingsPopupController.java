package com.pathmind.screen;

// Canonical settings UI shared by every supported Stonecutter target.

import com.pathmind.data.OnboardingPresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.PathmindSettingsRowRenderer;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.PathmindI18n;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.ScrollbarHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
//? if MC_1_21_8 {
/*// Legacy screen input callbacks use primitive parameters.*/
//?} else {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

final class PathmindSettingsPopupController {
    private static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }

    interface Host {
        Minecraft client();
        Font font();
        int screenWidth();
        int screenHeight();
        int boundedPopupWidth(int requestedWidth);
        NodeGraph nodeGraph();
        void addWidget(EditBox field);
        void setOverlayCutout(int x, int y, int width, int height);
        void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y, int maxWidth, int color);
        float hoverProgress(Object key, boolean hovered);
        boolean isPointInRect(int pointX, int pointY, int x, int y, int width, int height);
        void refreshAvailablePresets();
        void replayFirstRunTutorial();
        void reopenForLanguageChange();
    }

    private static final int SETTINGS_POPUP_WIDTH = 360;
    private static final int SETTINGS_POPUP_HEIGHT = 408;
    private static final int SETTINGS_OPTION_WIDTH = 90;
    private static final int SETTINGS_OPTION_HEIGHT = 16;
    private static final int SETTINGS_OPTION_GAP = 6;
    private static final int SETTINGS_TOGGLE_WIDTH = 60;
    private static final int SETTINGS_TOGGLE_HEIGHT = 16;
    private static final int SETTINGS_SLIDER_WIDTH = 160;
    private static final int SETTINGS_SLIDER_HEIGHT = 6;
    private static final int SETTINGS_SLIDER_HANDLE_WIDTH = 8;
    private static final int SETTINGS_SLIDER_HANDLE_HEIGHT = 12;
    private static final int SETTINGS_NODE_LIST_ROW_HEIGHT = 20;
    private static final int SETTINGS_NODE_LIST_GAP = 6;
    private static final int SETTINGS_BACK_BUTTON_WIDTH = 52;
    private static final int SETTINGS_BACK_BUTTON_HEIGHT = 18;
    private static final int SETTINGS_SECTION_BUTTON_WIDTH = 56;
    private static final int SETTINGS_SECTION_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_NODE_TYPE_BUTTON_HEIGHT = 28;
    private static final int SETTINGS_NODE_TYPE_BUTTON_GAP = 6;
    private static final int SETTINGS_NODE_TYPE_SECTION_GAP = 10;
    private static final int SETTINGS_NODE_TYPE_SELECTOR_MAX_HEIGHT = 102;
    private static final int SETTINGS_NODE_TYPE_SEARCH_HEIGHT = 22;
    private static final int SETTINGS_NODE_TYPE_EMPTY_HEIGHT = 24;
    private static final long SETTINGS_SCROLL_GESTURE_TIMEOUT_MS = 180L;
    private static final int CREATE_LIST_RADIUS_MIN = 1;
    private static final int CREATE_LIST_RADIUS_MAX = 512;
    private static final NodeType[] SETTINGS_NODE_TYPES = {
        NodeType.GOTO,
        NodeType.SENSOR_KEY_PRESSED,
        NodeType.CREATE_LIST
    };
    private static final int NODE_DELAY_MIN_MS = 1;
    private static final int NODE_DELAY_MAX_MS = 500;
    private static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final int NODE_SEARCH_FIELD_WIDTH = 180;
    private static final String[] SUPPORTED_LANGUAGES = {"en_us", "es_es", "pt_br", "ru_ru", "de_de", "fr_fr", "pl_pl"};

    private final Host host;
    private final PopupAnimationHandler animation = new PopupAnimationHandler();
    private final Settings settings;
    private AccentOption accentOption;
    private boolean showGrid;
    private boolean renderConnectionsOnTop;
    private boolean showWorkspaceTooltips;
    private boolean showChatErrors;
    private boolean showHudOverlays;
    private boolean skipPresetDeleteConfirm;
    private int nodeDelayMs;
    private boolean nodeDelayDragging;
    private boolean createListRadiusDragging;
    private EditBox nodeDelayField;
    private EditBox createListRadiusField;
    private EditBox settingsNodeSearchField;
    private boolean settingsNodeListView = true;
    private NodeType settingsNodeTargetType;
    private Node settingsNodeTarget;
    private int settingsNodeListScrollOffset;
    private int settingsNodeSelectorScrollOffset;
    private int settingsPopupScrollOffset;
    private long settingsLastScrollEventMs;
    private int settingsLastScrollConsumer;
    private boolean settingsNodeSelectorScrollDragging;
    private int settingsNodeSelectorScrollDragOffset;
    private boolean settingsPopupScrollDragging;
    private int settingsPopupScrollDragOffset;

    PathmindSettingsPopupController(Host host) {
        this.host = host;
        this.settings = SettingsManager.load();
        this.accentOption = getAccentOptionFromString(settings.accentColor);
        this.showGrid = settings.showGrid == null || settings.showGrid;
        this.renderConnectionsOnTop = settings.renderConnectionsOnTop != null && settings.renderConnectionsOnTop;
        this.showWorkspaceTooltips = settings.showTooltips == null || settings.showTooltips;
        this.showChatErrors = settings.showChatErrors == null || settings.showChatErrors;
        this.showHudOverlays = settings.showHudOverlays == null || settings.showHudOverlays;
        this.skipPresetDeleteConfirm = settings.skipPresetDeleteConfirm != null && settings.skipPresetDeleteConfirm;
        this.nodeDelayMs = Mth.clamp(
            settings.nodeDelayMs != null ? settings.nodeDelayMs : 150,
            NODE_DELAY_MIN_MS,
            NODE_DELAY_MAX_MS
        );
        settings.nodeDelayMs = nodeDelayMs;
    }

    private enum AccentOption {
        SKY("Sky", UITheme.ACCENT_SKY),
        MINT("Mint", UITheme.ACCENT_MINT),
        AMBER("Amber", UITheme.ACCENT_AMBER);

        final String label;
        final int color;

        AccentOption(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    PopupAnimationHandler animation() {
        return animation;
    }

    Settings settings() {
        return settings;
    }

    int accentColor() {
        return accentOption != null ? accentOption.color : UITheme.ACCENT_DEFAULT;
    }

    boolean showGrid() {
        return showGrid;
    }

    boolean renderConnectionsOnTop() {
        return renderConnectionsOnTop;
    }

    boolean showWorkspaceTooltips() {
        return showWorkspaceTooltips;
    }

    boolean showChatErrors() {
        return showChatErrors;
    }

    boolean showHudOverlays() {
        return showHudOverlays;
    }

    boolean skipPresetDeleteConfirm() {
        return skipPresetDeleteConfirm;
    }

    void setSkipPresetDeleteConfirm(boolean skip) {
        skipPresetDeleteConfirm = skip;
        settings.skipPresetDeleteConfirm = skip;
        SettingsManager.save(settings);
    }

    int getPopupX() {
        return (host.screenWidth() - getPopupWidth()) / 2;
    }

    int getPopupWidth() {
        return host.boundedPopupWidth(SETTINGS_POPUP_WIDTH);
    }

    int getPopupHeight() {
        return Math.min(SETTINGS_POPUP_HEIGHT, Math.max(140, host.screenHeight() - 24));
    }

    int getPopupY() {
        return (host.screenHeight() - getPopupHeight()) / 2;
    }

    void initializeFields() {
        if (nodeDelayField == null) {
            nodeDelayField = PathmindTextField.createInactive(host.font(), 0, 0, 120, 20, Component.translatable("pathmind.field.delay"), 6);
            nodeDelayField.setTextColor(UITheme.TEXT_HEADER);
            nodeDelayField.setTextColorUneditable(UITheme.TEXT_HEADER);
            ((PathmindTextField) nodeDelayField).setPathmindFilter(value -> value == null || value.isEmpty() || value.chars().allMatch(Character::isDigit));
            nodeDelayField.setResponder(value -> {
                Integer parsed = parseDelayFieldValue(value);
                if (parsed != null && parsed != nodeDelayMs) {
                    nodeDelayMs = parsed;
                    settings.nodeDelayMs = nodeDelayMs;
                    SettingsManager.save(settings);
                }
            });
            host.addWidget(nodeDelayField);
        }
        if (createListRadiusField == null) {
            createListRadiusField = PathmindTextField.createInactive(host.font(), 0, 0, 120, 20, Component.translatable("pathmind.field.radius"), 6);
            createListRadiusField.setTextColor(UITheme.TEXT_HEADER);
            createListRadiusField.setTextColorUneditable(UITheme.TEXT_HEADER);
            ((PathmindTextField) createListRadiusField).setPathmindFilter(value -> value == null || value.isEmpty() || value.chars().allMatch(Character::isDigit));
            createListRadiusField.setResponder(value -> {
                Node targetNode = getEffectiveSettingsTargetNode();
                Integer parsed = parseCreateListRadiusFieldValue(value);
                if (parsed != null && (targetNode == null || targetNode.getType() == NodeType.CREATE_LIST)
                    && parsed != getCreateListSettingsRadius(targetNode)) {
                    setCreateListSettingsRadius(targetNode, parsed);
                }
            });
            host.addWidget(createListRadiusField);
        }
        if (settingsNodeSearchField == null) {
            settingsNodeSearchField = PathmindTextField.createInactive(host.font(), 0, 0, NODE_SEARCH_FIELD_WIDTH, SETTINGS_NODE_TYPE_SEARCH_HEIGHT, Component.translatable("pathmind.search.nodeSettings"), 64);
            settingsNodeSearchField.setSuggestion(tr("pathmind.search.nodeSettings"));
            settingsNodeSearchField.setHeight(Math.max(10, SETTINGS_NODE_TYPE_SEARCH_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2));
            settingsNodeSearchField.setResponder(value -> settingsNodeSelectorScrollOffset = 0);
            host.addWidget(settingsNodeSearchField);
        }
    }

    void open() {
        resetLanguageDropdown();
        Node selectedNode = host.nodeGraph() != null ? host.nodeGraph().getSelectedNode() : null;
        if (supportsNodeSettings(selectedNode)) {
            settingsNodeTargetType = selectedNode.getType();
            settingsNodeTarget = selectedNode;
        } else {
            settingsNodeListView = false;
            settingsNodeTargetType = SETTINGS_NODE_TYPES[0];
            settingsNodeTarget = findFirstNodeWithSettingsType(settingsNodeTargetType);
        }
        settingsNodeListScrollOffset = 0;
        settingsNodeSelectorScrollOffset = 0;
        settingsPopupScrollOffset = 0;
        settingsLastScrollEventMs = 0L;
        settingsLastScrollConsumer = 0;
        settingsNodeSelectorScrollDragging = false;
        settingsNodeSelectorScrollDragOffset = 0;
        if (settingsNodeSearchField != null) {
            settingsNodeSearchField.setValue("");
            settingsNodeSearchField.setFocused(false);
            settingsNodeSearchField.setVisible(true);
            settingsNodeSearchField.setEditable(true);
            settingsNodeSearchField.setSuggestion(tr("pathmind.search.nodeSettings"));
        }
        animation.show();
    }

    void close() {
        closeLanguageDropdown();
        nodeDelayDragging = false;
        createListRadiusDragging = false;
        settingsNodeSelectorScrollDragging = false;
        settingsNodeSelectorScrollDragOffset = 0;
        settingsPopupScrollDragging = false;
        settingsPopupScrollDragOffset = 0;
        if (createListRadiusField != null) {
            PathmindTextField.deactivate(createListRadiusField);
        }
        settingsNodeListView = false;
        settingsNodeTargetType = null;
        settingsNodeTarget = null;
        settingsNodeListScrollOffset = 0;
        settingsNodeSelectorScrollOffset = 0;
        settingsPopupScrollOffset = 0;
        settingsLastScrollEventMs = 0L;
        settingsLastScrollConsumer = 0;
        if (settingsNodeSearchField != null) {
            settingsNodeSearchField.setValue("");
            PathmindTextField.deactivate(settingsNodeSearchField);
            settingsNodeSearchField.setSuggestion(tr("pathmind.search.nodeSettings"));
        }
        animation.hide();
    }

    private AccentOption getAccentOptionFromString(String color) {
        return switch (color.toLowerCase()) {
            case "mint" -> AccentOption.MINT;
            case "amber" -> AccentOption.AMBER;
            default -> AccentOption.SKY;
        };
    }

    private String getAccentOptionString(AccentOption option) {
        return switch (option) {
            case MINT -> "mint";
            case AMBER -> "amber";
            default -> "sky";
        };
    }

    private boolean languageDropdownOpen = false;
    private final AnimatedValue languageDropdownAnimation = AnimatedValue.forHover();
    int languageDropdownX = 0;
    int languageDropdownY = 0;
    int languageDropdownWidth = 0;
    int languageDropdownClipX = 0;
    int languageDropdownClipY = 0;
    int languageDropdownClipWidth = 0;
    int languageDropdownClipHeight = 0;

    /** Language options draw above the popup scrim, so the screen renders them last. */
    void renderLanguageDropdownOptions(GuiGraphics context, int mouseX, int mouseY) {
        drawLanguageDropdownOptions(context, languageDropdownX, languageDropdownY, languageDropdownWidth, mouseX, mouseY);
    }

    boolean isLanguageDropdownOpen() {
        return languageDropdownOpen;
    }

    void toggleLanguageDropdown() {
        languageDropdownOpen = !languageDropdownOpen;
    }

    void closeLanguageDropdown() {
        languageDropdownOpen = false;
    }

    /** Collapses the dropdown without animating, for a freshly opened popup. */
    void resetLanguageDropdown() {
        languageDropdownOpen = false;
        languageDropdownAnimation.setValue(0f);
    }

    int supportedLanguageCount() {
        return SUPPORTED_LANGUAGES.length;
    }

    void selectLanguage(int index) {
        onLanguageSelected(SUPPORTED_LANGUAGES[index]);
    }

    private void drawLanguageDropdown(GuiGraphics context, int x, int y, int width, String currentLang, boolean hovered) {
        DropdownLayoutHelper.updateOpenAnimation(languageDropdownAnimation, languageDropdownOpen);

        float hoverProgress = languageDropdownOpen ? 1f : host.hoverProgress("settings-language-dropdown-bg", hovered);
        UIStyleHelper.FieldPalette fieldPalette = UIStyleHelper.getDropdownFieldPalette(accentColor(), hoverProgress, languageDropdownOpen, false);
        UIStyleHelper.drawFieldFrame(
            context,
            x,
            y,
            width,
            20,
            new UIStyleHelper.FieldPalette(
                animation.getAnimatedPopupColor(fieldPalette.backgroundColor()),
                animation.getAnimatedPopupColor(fieldPalette.borderColor()),
                animation.getAnimatedPopupColor(fieldPalette.innerBorderColor()),
                animation.getAnimatedPopupColor(fieldPalette.textColor()),
                animation.getAnimatedPopupColor(fieldPalette.placeholderColor())
            )
        );

        int labelColor = animation.getAnimatedPopupColor(fieldPalette.textColor());
        context.drawString(host.font(), Component.literal(currentLang), x + 4, y + 6, labelColor);

        int arrowCenterX = x + width - 10;
        int arrowCenterY = y + 10;
        UIStyleHelper.drawChevron(context, arrowCenterX, arrowCenterY, languageDropdownOpen, labelColor);
    }

    private void drawLanguageDropdownOptions(GuiGraphics context, int x, int y, int width, int mouseX, int mouseY) {
        // Get animation progress
        float animProgress = languageDropdownAnimation.getValue();

        // Don't render options if animation is fully closed
        if (animProgress <= 0.001f) {
            return;
        }

        Object matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 550.0f);

        int dropdownY = y + 22;
        int fullOptionsHeight = SUPPORTED_LANGUAGES.length * 20;
        int scissorLeft = Math.max(x, languageDropdownClipX);
        int scissorTop = Math.max(dropdownY, languageDropdownClipY);
        int scissorRight = Math.min(x + width, languageDropdownClipX + languageDropdownClipWidth);
        int scissorBottom = Math.min(
            DropdownLayoutHelper.getRevealBottom(dropdownY, fullOptionsHeight, animProgress, 1),
            languageDropdownClipY + languageDropdownClipHeight
        );

        if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) {
            MatrixStackBridge.pop(matrices);
            return;
        }

        context.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);

        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(accentColor(), animProgress, languageDropdownOpen, false);
        UIStyleHelper.drawScrollContainer(
            context,
            x,
            dropdownY,
            width,
            fullOptionsHeight,
            new UIStyleHelper.ScrollContainerPalette(
                animation.getAnimatedPopupColor(containerPalette.backgroundColor()),
                animation.getAnimatedPopupColor(containerPalette.borderColor()),
                animation.getAnimatedPopupColor(containerPalette.innerBorderColor()),
                animation.getAnimatedPopupColor(containerPalette.trackColor()),
                animation.getAnimatedPopupColor(containerPalette.thumbColor())
            )
        );

        // Draw each language option
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
            String lang = SUPPORTED_LANGUAGES[i];
            String langName = getLanguageDisplayName(lang);
            int optionY = dropdownY + (i * 20);

            boolean optionHovered = animProgress >= 1f && mouseX >= x && mouseX <= x + width && mouseY >= optionY && mouseY <= optionY + 20;
            String currentLang = host.client().getLanguageManager().getSelected();
            boolean selected = lang.equals(currentLang);
            UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(accentColor(), optionHovered ? 1f : 0f, selected, false);
            UIStyleHelper.drawDropdownRow(
                context,
                x + 1,
                optionY + 1,
                width - 2,
                19,
                new UIStyleHelper.DropdownRowPalette(
                    animation.getAnimatedPopupColor(rowPalette.backgroundColor()),
                    animation.getAnimatedPopupColor(rowPalette.borderColor()),
                    animation.getAnimatedPopupColor(rowPalette.textColor())
                )
            );

            int textColor = animation.getAnimatedPopupColor(selected ? accentColor() : rowPalette.textColor());
            context.drawString(host.font(), Component.literal(langName), x + 4, optionY + 6, textColor);
        }

        context.disableScissor();
        MatrixStackBridge.pop(matrices);
    }

    private String getLanguageDisplayName(String languageCode) {
        return Component.translatable("pathmind.language." + languageCode).getString();
    }

    private void onLanguageSelected(String languageCode) {
        // Save to settings first
        settings.language = languageCode;
        SettingsManager.save(settings);

        // Update Minecraft's language and reload resources
        host.client().options.languageCode = languageCode;
        host.client().getLanguageManager().setSelected(languageCode);
        host.client().options.save();
        host.client().reloadResourcePacks();

        // Reload the screen to update all text
        host.reopenForLanguageChange();
    }

    //? if MC_1_21_8 {
    /*boolean mouseClicked(double mouseX, double mouseY, int button) {
        *///?} else {
    boolean mouseClicked(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (button != 0) {
            return true;
        }

        int popupX = getPopupX();
        int popupY = getPopupY();
        int popupWidth = getPopupWidth();
        int popupHeight = getPopupHeight();
        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int contentPopupY = popupY - settingsPopupScrollOffset;
        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
        boolean bodyHovered = host.isPointInRect(mouseXi, mouseYi, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3]);

        if (!host.isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            close();
            return true;
        }

        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
        ScrollbarHelper.Metrics scrollMetrics = getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll);
        if (maxScroll > 0
            && host.isPointInRect(mouseXi, mouseYi, scrollMetrics.trackLeft() - 3, scrollMetrics.trackTop(), scrollMetrics.trackWidth() + 6, scrollMetrics.viewportHeight())) {
            settingsPopupScrollDragging = true;
            settingsPopupScrollDragOffset = mouseYi - scrollMetrics.thumbTop();
            return true;
        }

        int contentX = popupX + 20;
        int languageLabelY = contentPopupY + 44;
        int languageButtonY = languageLabelY + 12;
        int languageButtonWidth = popupWidth - 40;

        if (bodyHovered && mouseXi >= contentX && mouseXi <= contentX + languageButtonWidth && mouseYi >= languageButtonY && mouseYi <= languageButtonY + 20) {
            toggleLanguageDropdown();
            return true;
        }

        if (isLanguageDropdownOpen()) {
            int dropdownY = languageButtonY + 22;
            for (int i = 0; i < supportedLanguageCount(); i++) {
                if (bodyHovered && mouseXi >= contentX && mouseXi <= contentX + languageButtonWidth
                    && mouseYi >= dropdownY + (i * 20) && mouseYi <= dropdownY + (i * 20) + 20) {
                    selectLanguage(i);
                    return true;
                }
            }
        }

        int accentLabelY = languageButtonY + 50;
        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT)) {
                accentOption = option;
                settings.accentColor = getAccentOptionString(accentOption);
                SettingsManager.save(settings);
                return true;
            }
            optionIndex++;
        }

        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        int settingDividerY = sectionDividerY + 22;
        int gridRowCenterY = (sectionDividerY + settingDividerY) / 2;
        int gridToggleX = popupX + popupWidth - SETTINGS_TOGGLE_WIDTH - 20;
        int gridToggleY = gridRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, gridToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showGrid = !showGrid;
            settings.showGrid = showGrid;
            SettingsManager.save(settings);
            return true;
        }

        int lowDetailDividerY = settingDividerY + 22;
        int lowDetailRowCenterY = (settingDividerY + lowDetailDividerY) / 2;
        int lowDetailToggleY = lowDetailRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, lowDetailToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            settings.lowDetailMode = !Boolean.TRUE.equals(settings.lowDetailMode);
            SettingsManager.save(settings);
            return true;
        }

        int footerDividerY = lowDetailDividerY + 22;
        int tooltipRowCenterY = (lowDetailDividerY + footerDividerY) / 2;
        int tooltipToggleY = tooltipRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, tooltipToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            renderConnectionsOnTop = !renderConnectionsOnTop;
            settings.renderConnectionsOnTop = renderConnectionsOnTop;
            SettingsManager.save(settings);
            return true;
        }

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        int chatToggleY = chatRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, chatToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showWorkspaceTooltips = !showWorkspaceTooltips;
            settings.showTooltips = showWorkspaceTooltips;
            SettingsManager.save(settings);
            return true;
        }

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        int overlayToggleY = overlayRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, overlayToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showChatErrors = !showChatErrors;
            settings.showChatErrors = showChatErrors;
            SettingsManager.save(settings);
            return true;
        }

        int hudDividerY = overlayDividerY + 22;
        int hudRowCenterY = (overlayDividerY + hudDividerY) / 2;
        int hudToggleY = hudRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, hudToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showHudOverlays = !showHudOverlays;
            settings.showHudOverlays = showHudOverlays;
            SettingsManager.save(settings);
            return true;
        }

        int profilerDividerY = hudDividerY + 22;
        int profilerRowCenterY = (hudDividerY + profilerDividerY) / 2;
        int profilerToggleY = profilerRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, gridToggleX, profilerToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            settings.showProfilerOverlay = !Boolean.TRUE.equals(settings.showProfilerOverlay);
            SettingsManager.save(settings);
            return true;
        }

        int delayDividerY = profilerDividerY + 26;
        int delayRowCenterY = (profilerDividerY + delayDividerY) / 2;
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = delayRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
        String delayText = nodeDelayField != null ? nodeDelayField.getValue() : Integer.toString(nodeDelayMs);
        int[] valueBox = getNodeDelayFieldBounds(popupX, popupWidth, delayRowCenterY, delayText);
        if (nodeDelayField != null) {
            if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, valueBox[0], valueBox[1], valueBox[2], valueBox[3])) {
                nodeDelayField.setEditable(true);
                nodeDelayField.setFocused(true);
                //? if MC_1_21_8 {
                /*nodeDelayField.mouseClicked(mouseX, mouseY, button);*/
                //?} else {
                nodeDelayField.mouseClicked(click, inBounds);
                //?}
                return true;
            } else if (nodeDelayField.isFocused()) {
                nodeDelayField.setFocused(false);
            }
        }
        if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
            nodeDelayDragging = true;
            updateNodeDelayFromMouse(mouseXi, popupX, popupWidth);
            return true;
        }

        int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
        int selectorWidth = popupWidth - 40;
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY, selectorWidth);
        int[] selectorViewportBounds = getSettingsNodeTypeSelectorBounds(contentX, nodeSettingsBodyY, selectorWidth);
        int[] selectorSearchBounds = getSettingsNodeTypeSearchFieldBounds(contentX, nodeSettingsBodyY, selectorWidth);
        int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorWidth);
        ScrollbarHelper.Metrics selectorScrollMetrics = getSettingsNodeTypeSelectorScrollMetrics(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll);
        if (maxSelectorScroll > 0
            && host.isPointInRect(mouseXi, mouseYi, selectorScrollMetrics.trackLeft() - 3, selectorScrollMetrics.trackTop(),
            selectorScrollMetrics.trackWidth() + 6, selectorScrollMetrics.viewportHeight())) {
            settingsNodeSelectorScrollDragging = true;
            settingsNodeSelectorScrollDragOffset = mouseYi - selectorScrollMetrics.thumbTop();
            return true;
        }
        if (settingsNodeSearchField != null) {
            if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, selectorSearchBounds[0], selectorSearchBounds[1], selectorSearchBounds[2], selectorSearchBounds[3])) {
                settingsNodeSearchField.setEditable(true);
                settingsNodeSearchField.setFocused(true);
                //? if MC_1_21_8 {
                /*settingsNodeSearchField.mouseClicked(mouseX, mouseY, button);*/
                //?} else {
                settingsNodeSearchField.mouseClicked(click, inBounds);
                //?}
                return true;
            } else if (settingsNodeSearchField.isFocused()) {
                settingsNodeSearchField.setFocused(false);
            }
        }
        List<NodeType> filteredTypes = getFilteredSettingsNodeTypes();
        for (int i = 0; i < filteredTypes.size(); i++) {
            int[] selectorBounds = getSettingsNodeTypeButtonBounds(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll, i);
            if (bodyHovered
                && host.isPointInRect(mouseXi, mouseYi, selectorViewportBounds[0], selectorViewportBounds[1], selectorViewportBounds[2], selectorViewportBounds[3])
                && host.isPointInRect(mouseXi, mouseYi, selectorBounds[0], selectorBounds[1], selectorBounds[2], selectorBounds[3])) {
                NodeType targetType = filteredTypes.get(i);
                settingsNodeTargetType = targetType;
                settingsNodeTarget = findFirstNodeWithSettingsType(targetType);
                if (host.nodeGraph() != null && settingsNodeTarget != null) {
                    host.nodeGraph().selectNode(settingsNodeTarget);
                }
                return true;
            }
        }
        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int[] cacheRecipesButtonBounds = getSettingsCacheRecipesButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        if (host.isPointInRect(mouseXi, mouseYi, cacheRecipesButtonBounds[0], cacheRecipesButtonBounds[1],
            cacheRecipesButtonBounds[2], cacheRecipesButtonBounds[3])) {
            cacheSettingsRecipes();
            return true;
        }
        if (host.isPointInRect(mouseXi, mouseYi, clearCacheButtonBounds[0], clearCacheButtonBounds[1],
            clearCacheButtonBounds[2], clearCacheButtonBounds[3])) {
            clearSettingsCache();
            return true;
        }
        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        if (host.isPointInRect(mouseXi, mouseYi, restoreExamplesButtonBounds[0], restoreExamplesButtonBounds[1],
            restoreExamplesButtonBounds[2], restoreExamplesButtonBounds[3])) {
            restoreExamplePresets();
            return true;
        }
        int[] replayTutorialButtonBounds = getSettingsReplayTutorialButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        if (host.isPointInRect(mouseXi, mouseYi, replayTutorialButtonBounds[0], replayTutorialButtonBounds[1],
            replayTutorialButtonBounds[2], replayTutorialButtonBounds[3])) {
            host.replayFirstRunTutorial();
            return true;
        }

        NodeType selectedType = getEffectiveSettingsTargetType();
        if (bodyHovered && selectedType == NodeType.GOTO) {
            int gotoBreakDividerY = nodeSettingsContentY + 28;
            int gotoBreakRowCenterY = (nodeSettingsContentY + 10 + gotoBreakDividerY) / 2;
            int gotoBreakToggleY = gotoBreakRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (host.isPointInRect(mouseXi, mouseYi, gridToggleX, gotoBreakToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                settings.gotoAllowBreakWhileExecuting = !Boolean.TRUE.equals(settings.gotoAllowBreakWhileExecuting);
                SettingsManager.save(settings);
                return true;
            }

            int gotoPlaceDividerY = gotoBreakDividerY + 22;
            int gotoPlaceRowCenterY = (gotoBreakDividerY + gotoPlaceDividerY) / 2;
            int gotoPlaceToggleY = gotoPlaceRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (host.isPointInRect(mouseXi, mouseYi, gridToggleX, gotoPlaceToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                settings.gotoAllowPlaceWhileExecuting = !Boolean.TRUE.equals(settings.gotoAllowPlaceWhileExecuting);
                SettingsManager.save(settings);
                return true;
            }
        } else if (bodyHovered && selectedType == NodeType.SENSOR_KEY_PRESSED) {
            int keyPressedDividerY = nodeSettingsContentY + 28;
            int keyPressedRowCenterY = (nodeSettingsContentY + 10 + keyPressedDividerY) / 2;
            int keyPressedToggleY = keyPressedRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (host.isPointInRect(mouseXi, mouseYi, gridToggleX, keyPressedToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                settings.keyPressedActivatesInGuis = !(settings.keyPressedActivatesInGuis == null
                    || settings.keyPressedActivatesInGuis);
                SettingsManager.save(settings);
                return true;
            }
        } else if (bodyHovered && selectedType == NodeType.CREATE_LIST) {
            Node targetNode = getEffectiveSettingsTargetNode();
            int createListToggleDividerY = nodeSettingsContentY + 28;
            int createListToggleRowCenterY = (nodeSettingsContentY + 10 + createListToggleDividerY) / 2;
            int createListToggleY = createListToggleRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (host.isPointInRect(mouseXi, mouseYi, gridToggleX, createListToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                setCreateListCustomRadiusEnabled(targetNode, !isCreateListCustomRadiusEnabled(targetNode));
                return true;
            }

            if (isCreateListCustomRadiusEnabled(targetNode)) {
                int createListRadiusDividerY = createListToggleDividerY + 26;
                int createListRadiusRowCenterY = (createListToggleDividerY + createListRadiusDividerY) / 2;
                int createListSliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
                int createListSliderY = createListRadiusRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
                String radiusText = createListRadiusField != null ? createListRadiusField.getValue() : Integer.toString(getCreateListSettingsRadius(targetNode));
                int[] radiusValueBox = getCreateListRadiusFieldBounds(popupX, popupWidth, createListRadiusRowCenterY, radiusText);
                if (createListRadiusField != null) {
                    if (bodyHovered && host.isPointInRect(mouseXi, mouseYi, radiusValueBox[0], radiusValueBox[1], radiusValueBox[2], radiusValueBox[3])) {
                        createListRadiusField.setEditable(true);
                        createListRadiusField.setFocused(true);
                        //? if MC_1_21_8 {
                        /*createListRadiusField.mouseClicked(mouseX, mouseY, button);*/
                        //?} else {
                        createListRadiusField.mouseClicked(click, inBounds);
                        //?}
                        return true;
                    } else if (createListRadiusField.isFocused()) {
                        createListRadiusField.setFocused(false);
                    }
                }
                if (host.isPointInRect(mouseXi, mouseYi, createListSliderX, createListSliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
                    createListRadiusDragging = true;
                    updateCreateListRadiusFromMouse(targetNode, mouseXi, popupX, popupWidth);
                    return true;
                }
            }
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = popupX + popupWidth - buttonWidth - 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        if (host.isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            close();
            return true;
        }

        return true;
    }

    private void updateScrollDrag(double mouseY) {
        if (settingsNodeSelectorScrollDragging) {
            int popupX = getPopupX();
            int contentPopupY = getPopupY() - settingsPopupScrollOffset;
            int popupWidth = getPopupWidth();
            int contentX = popupX + 20;
            int selectorWidth = popupWidth - 40;
            int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
            int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorWidth);
            ScrollbarHelper.Metrics selectorScrollMetrics =
                getSettingsNodeTypeSelectorScrollMetrics(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll);
            settingsNodeSelectorScrollOffset =
                ScrollbarHelper.scrollFromThumb(selectorScrollMetrics, (int) mouseY - settingsNodeSelectorScrollDragOffset);
        }
        if (settingsPopupScrollDragging) {
            int popupX = getPopupX();
            int popupY = getPopupY();
            int popupWidth = getPopupWidth();
            int popupHeight = getPopupHeight();
            int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
            ScrollbarHelper.Metrics scrollMetrics =
                getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll);
            settingsPopupScrollOffset =
                ScrollbarHelper.scrollFromThumb(scrollMetrics, (int) mouseY - settingsPopupScrollDragOffset);
        }
    }

    void mouseDragged(double mouseX, double mouseY) {
        if (settingsNodeSelectorScrollDragging || settingsPopupScrollDragging) {
            updateScrollDrag(mouseY);
        }
        if (nodeDelayDragging) {
            updateNodeDelayFromMouse((int) mouseX, getPopupX(), getPopupWidth());
        }
        if (createListRadiusDragging) {
            updateCreateListRadiusFromMouse(getEffectiveSettingsTargetNode(), (int) mouseX, getPopupX(), getPopupWidth());
        }
    }

    //? if MC_1_21_8 {
    /*void mouseReleased(double mouseX, double mouseY, int button) {
        *///?} else {
    void mouseReleased(MouseButtonEvent click) {
        //?}
        nodeDelayDragging = false;
        createListRadiusDragging = false;
        settingsNodeSelectorScrollDragging = false;
        settingsPopupScrollDragging = false;
        if (nodeDelayField != null) {
            //? if MC_1_21_8 {
            /*nodeDelayField.mouseReleased(mouseX, mouseY, button);*/
            //?} else {
            nodeDelayField.mouseReleased(click);
            //?}
        }
    }

    //? if MC_1_21_8 {
    /*boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        *///?} else {
    boolean keyPressed(KeyEvent input) {
        int keyCode = input.key();
        //?}
        if (nodeDelayField != null && nodeDelayField.isFocused()) {
            //? if MC_1_21_8 {
            /*if (nodeDelayField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (nodeDelayField.keyPressed(input)) {
                //?}
                return true;
            }
        }
        if (settingsNodeSearchField != null && settingsNodeSearchField.isFocused()) {
            //? if MC_1_21_8 {
            /*if (settingsNodeSearchField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (settingsNodeSearchField.keyPressed(input)) {
                //?}
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
        }
        if (createListRadiusField != null && createListRadiusField.isFocused()) {
            //? if MC_1_21_8 {
            /*if (createListRadiusField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (createListRadiusField.keyPressed(input)) {
                //?}
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            close();
        }
        return true;
    }

    //? if MC_1_21_8 {
    /*boolean charTyped(char chr, int modifiers) {
        *///?} else {
    boolean charTyped(CharacterEvent input) {
        //?}
        //? if MC_1_21_8 {
        /*if (settingsNodeSearchField != null && settingsNodeSearchField.isFocused() && settingsNodeSearchField.charTyped(chr, modifiers)) {
            *///?} else {
        if (settingsNodeSearchField != null && settingsNodeSearchField.isFocused() && settingsNodeSearchField.charTyped(input)) {
            //?}
            return true;
        }
        //? if MC_1_21_8 {
        /*if (nodeDelayField != null && nodeDelayField.isFocused() && nodeDelayField.charTyped(chr, modifiers)) {
            *///?} else {
        if (nodeDelayField != null && nodeDelayField.isFocused() && nodeDelayField.charTyped(input)) {
            //?}
            return true;
        }
        //? if MC_1_21_8 {
        /*if (createListRadiusField != null && createListRadiusField.isFocused() && createListRadiusField.charTyped(chr, modifiers)) {
            *///?} else {
        if (createListRadiusField != null && createListRadiusField.isFocused() && createListRadiusField.charTyped(input)) {
            //?}
            return true;
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        int popupX = getPopupX();
        int popupY = getPopupY();
        int popupWidth = getPopupWidth();
        int popupHeight = getPopupHeight();
        int contentPopupY = popupY - settingsPopupScrollOffset;
        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
        int[] selectorBounds = getSettingsNodeTypeSelectorBounds(
            popupX + 20, getSettingsNodeSectionBodyY(contentPopupY), popupWidth - 40);
        long now = System.currentTimeMillis();
        boolean continueOuterScroll = now - settingsLastScrollEventMs <= SETTINGS_SCROLL_GESTURE_TIMEOUT_MS
            && settingsLastScrollConsumer == 2;
        if (host.isPointInRect((int) mouseX, (int) mouseY, selectorBounds[0], selectorBounds[1], selectorBounds[2], selectorBounds[3])
            && verticalAmount != 0.0) {
            int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorBounds[2]);
            if (maxSelectorScroll > 0 && !continueOuterScroll) {
                int nextSelectorScroll =
                    ScrollbarHelper.applyWheel(settingsNodeSelectorScrollOffset, verticalAmount, 16, maxSelectorScroll);
                if (nextSelectorScroll != settingsNodeSelectorScrollOffset) {
                    settingsNodeSelectorScrollOffset = nextSelectorScroll;
                    settingsLastScrollEventMs = now;
                    settingsLastScrollConsumer = 1;
                    return true;
                }
            }
            if (!continueOuterScroll) {
                return true;
            }
        }
        if (host.isPointInRect((int) mouseX, (int) mouseY, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3])
            && verticalAmount != 0.0) {
            int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
            if (maxScroll > 0) {
                int nextPopupScroll =
                    ScrollbarHelper.applyWheel(settingsPopupScrollOffset, verticalAmount, 16, maxScroll);
                if (nextPopupScroll != settingsPopupScrollOffset) {
                    settingsPopupScrollOffset = nextPopupScroll;
                    settingsLastScrollEventMs = now;
                    settingsLastScrollConsumer = 2;
                }
            }
        }
        return true;
    }


    void renderSettingsPopup(GuiGraphics context, int mouseX, int mouseY) {
        float popupAlpha = animation.getPopupAlpha();

        int popupWidth = getPopupWidth();
        int popupHeight = getPopupHeight();
        int[] bounds = animation.getScaledPopupBounds(host.screenWidth(), host.screenHeight(), popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);
        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, animation);

        PathmindPopupRenderer.drawTitle(
            context,
            host.font(),
            Component.translatable("pathmind.settings.title"),
            popupX,
            popupY,
            scaledWidth,
            animation
        );

        PathmindPopupLayout.Rect bodyBounds = getSettingsPopupBodyRect(popupX, popupY, scaledWidth, scaledHeight);
        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, scaledWidth, scaledHeight);
        settingsPopupScrollOffset = Mth.clamp(settingsPopupScrollOffset, 0, maxScroll);
        int contentPopupY = popupY - settingsPopupScrollOffset;
        PathmindPopupRenderer.enableBodyScissor(context, bodyBounds);
        int contentX = popupX + 20;

        // Language section
        int languageLabelY = contentPopupY + 44;
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.language").getString(), contentX, languageLabelY, scaledWidth - 40,
            animation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));

        // Language dropdown button
        int languageButtonY = languageLabelY + 12;
        int languageButtonWidth = scaledWidth - 40;

        // Store dropdown position for rendering later
        languageDropdownX = contentX;
        languageDropdownY = languageButtonY;
        languageDropdownWidth = languageButtonWidth;
        languageDropdownClipX = bodyBounds.x();
        languageDropdownClipY = bodyBounds.y();
        languageDropdownClipWidth = bodyBounds.width();
        languageDropdownClipHeight = bodyBounds.height();

        String currentLang = host.client().getLanguageManager().getSelected();
        String langDisplayName = getLanguageDisplayName(currentLang);
        boolean languageHovered = mouseX >= contentX && mouseX <= contentX + languageButtonWidth && mouseY >= languageButtonY && mouseY <= languageButtonY + 20;
        drawLanguageDropdown(context, contentX, languageButtonY, languageButtonWidth, langDisplayName, languageHovered);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Adjust following sections downward by 50 pixels
        int accentLabelY = languageButtonY + 50;
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.accent").getString(), contentX, accentLabelY, scaledWidth - 40,
            animation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));

        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            boolean hovered = host.isPointInRect(mouseX, mouseY, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT);
            boolean selected = accentOption == option;
            drawAccentOption(context, optionX, accentOptionsY, option, hovered, selected);
            optionIndex++;
        }

        int sectionDividerX = popupX + 16;
        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, sectionDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int settingDividerY = sectionDividerY + 22;
        int gridRowCenterY = (sectionDividerY + settingDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, gridRowCenterY, Component.translatable("pathmind.settings.showGrid").getString(), showGrid, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, settingDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int lowDetailDividerY = settingDividerY + 22;
        int lowDetailRowCenterY = (settingDividerY + lowDetailDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, lowDetailRowCenterY, Component.translatable("pathmind.settings.lowDetailMode").getString(),
            Boolean.TRUE.equals(settings.lowDetailMode), popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, lowDetailDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int footerDividerY = lowDetailDividerY + 22;
        int tooltipRowCenterY = (lowDetailDividerY + footerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, tooltipRowCenterY, Component.translatable("pathmind.settings.renderConnectionsOnTop").getString(), renderConnectionsOnTop, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, footerDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, chatRowCenterY, Component.translatable("pathmind.settings.showTooltips").getString(), showWorkspaceTooltips, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, chatDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, overlayRowCenterY, Component.translatable("pathmind.settings.showChatErrors").getString(), showChatErrors, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, overlayDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int hudDividerY = overlayDividerY + 22;
        int hudRowCenterY = (overlayDividerY + hudDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, hudRowCenterY, Component.translatable("pathmind.settings.showHudOverlays").getString(), showHudOverlays, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, hudDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int profilerDividerY = hudDividerY + 22;
        int profilerRowCenterY = (hudDividerY + profilerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, profilerRowCenterY, Component.translatable("pathmind.settings.showProfilerOverlay").getString(),
            settings != null && Boolean.TRUE.equals(settings.showProfilerOverlay), popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, profilerDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int delayDividerY = profilerDividerY + 26;
        int delayRowCenterY = (profilerDividerY + delayDividerY) / 2;
        renderNodeDelayRow(context, mouseX, mouseY, contentX, delayRowCenterY, nodeDelayMs, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS, popupX, scaledWidth);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16, delayDividerY,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

        int nodeSettingsLabelY = getSettingsNodeSectionLabelY(contentPopupY);
        int nodeSettingsBodyY = nodeSettingsLabelY + 14;
        if (createListRadiusField != null) {
            createListRadiusField.setVisible(false);
        }
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings").getString(), contentX, nodeSettingsLabelY, scaledWidth - 40,
            animation.getAnimatedPopupColor(UITheme.TEXT_SECONDARY));
        int selectorWidth = scaledWidth - 40;
        renderSettingsNodeTypeSelector(context, mouseX, mouseY, contentX, nodeSettingsBodyY, selectorWidth);
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY, selectorWidth);

        NodeType targetType = getEffectiveSettingsTargetType();
        if (targetType == null) {
            host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings.none").getString(), contentX, nodeSettingsContentY,
                scaledWidth - 40, animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));
        } else if (targetType == NodeType.GOTO) {
            host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));

            int gotoBreakDividerY = nodeSettingsContentY + 28;
            int gotoBreakRowCenterY = (nodeSettingsContentY + 10 + gotoBreakDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, gotoBreakRowCenterY,
                Component.translatable("pathmind.settings.gotoAllowBreak").getString(), settings.gotoAllowBreakWhileExecuting != null && settings.gotoAllowBreakWhileExecuting, popupX, scaledWidth);
            context.hLine(sectionDividerX, popupX + scaledWidth - 16, gotoBreakDividerY,
                animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

            int gotoPlaceDividerY = gotoBreakDividerY + 22;
            int gotoPlaceRowCenterY = (gotoBreakDividerY + gotoPlaceDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, gotoPlaceRowCenterY,
                Component.translatable("pathmind.settings.gotoAllowPlace").getString(), settings.gotoAllowPlaceWhileExecuting != null && settings.gotoAllowPlaceWhileExecuting, popupX, scaledWidth);
            context.hLine(sectionDividerX, popupX + scaledWidth - 16, gotoPlaceDividerY,
                animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
        } else if (targetType == NodeType.SENSOR_KEY_PRESSED) {
            host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));

            int keyPressedDividerY = nodeSettingsContentY + 28;
            int keyPressedRowCenterY = (nodeSettingsContentY + 10 + keyPressedDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, keyPressedRowCenterY,
                Component.translatable("pathmind.settings.keyPressedActivatesInGuis").getString(), settings.keyPressedActivatesInGuis == null || settings.keyPressedActivatesInGuis, popupX, scaledWidth);
            context.hLine(sectionDividerX, popupX + scaledWidth - 16, keyPressedDividerY,
                animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
        } else if (targetType == NodeType.CREATE_LIST) {
            Node targetNode = getEffectiveSettingsTargetNode();
            boolean useRadius = isCreateListCustomRadiusEnabled(targetNode);
            int radius = getCreateListSettingsRadius(targetNode);
            host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings.editing", targetType.getDisplayName()).getString(), contentX, nodeSettingsContentY, scaledWidth - 40,
                animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));

            int createListToggleDividerY = nodeSettingsContentY + 28;
            int createListToggleRowCenterY = (nodeSettingsContentY + 10 + createListToggleDividerY) / 2;
            renderToggleRow(context, mouseX, mouseY, contentX, createListToggleRowCenterY,
                Component.translatable("pathmind.settings.createListUseCustomRadius").getString(), useRadius, popupX, scaledWidth);
            context.hLine(sectionDividerX, popupX + scaledWidth - 16, createListToggleDividerY,
                animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));

            if (useRadius) {
                int createListRadiusDividerY = createListToggleDividerY + 26;
                int createListRadiusRowCenterY = (createListToggleDividerY + createListRadiusDividerY) / 2;
                renderCreateListRadiusRow(context, mouseX, mouseY, contentX, createListRadiusRowCenterY,
                    radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX, popupX, scaledWidth);
                context.hLine(sectionDividerX, popupX + scaledWidth - 16, createListRadiusDividerY,
                    animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
            }
        }

        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int[] cacheRecipesButtonBounds = getSettingsCacheRecipesButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int clearCacheRowCenterY = getSettingsClearCacheRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsClearCacheDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.recipeCache").getString(), contentX, clearCacheRowCenterY - host.font().lineHeight / 2,
            scaledWidth - 40 - clearCacheButtonBounds[2] - cacheRecipesButtonBounds[2] - 18, animation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY));
        PathmindPopupRenderer.drawButton(
            context,
            host.font(),
            PathmindPopupLayout.rect(cacheRecipesButtonBounds[0], cacheRecipesButtonBounds[1], cacheRecipesButtonBounds[2], cacheRecipesButtonBounds[3]),
            mouseX,
            mouseY,
            Component.translatable("pathmind.settings.cacheRecipes"),
            PathmindPopupRenderer.ButtonStyle.PRIMARY,
            accentColor(),
            animation
        );
        PathmindPopupRenderer.drawButton(
            context,
            host.font(),
            PathmindPopupLayout.rect(clearCacheButtonBounds[0], clearCacheButtonBounds[1], clearCacheButtonBounds[2], clearCacheButtonBounds[3]),
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.clear"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            accentColor(),
            animation
        );

        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int restoreExamplesRowCenterY = getSettingsRestoreExamplesRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsRestoreExamplesDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.examplePresets").getString(), contentX,
            restoreExamplesRowCenterY - host.font().lineHeight / 2,
            scaledWidth - 40 - restoreExamplesButtonBounds[2] - 12, animation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY));
        PathmindPopupRenderer.drawButton(
            context,
            host.font(),
            PathmindPopupLayout.rect(restoreExamplesButtonBounds[0], restoreExamplesButtonBounds[1], restoreExamplesButtonBounds[2], restoreExamplesButtonBounds[3]),
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.restore"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            accentColor(),
            animation
        );

        int[] replayTutorialButtonBounds = getSettingsReplayTutorialButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int replayTutorialRowCenterY = getSettingsReplayTutorialRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.hLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsReplayTutorialDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.tutorial").getString(), contentX,
            replayTutorialRowCenterY - host.font().lineHeight / 2,
            scaledWidth - 40 - replayTutorialButtonBounds[2] - 12, animation.getAnimatedPopupColor(UITheme.TEXT_PRIMARY));
        PathmindPopupRenderer.drawButton(
            context,
            host.font(),
            PathmindPopupLayout.rect(replayTutorialButtonBounds[0], replayTutorialButtonBounds[1], replayTutorialButtonBounds[2], replayTutorialButtonBounds[3]),
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.replay"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            accentColor(),
            animation
        );

        PathmindPopupLayout.Rect closeButton = PathmindPopupLayout.settingsCloseButton(popupX, popupY, scaledWidth, scaledHeight, 90, 20);
        context.disableScissor();
        PathmindPopupRenderer.drawScrollableBodyChrome(
            context,
            bodyBounds,
            settingsPopupScrollOffset,
            maxScroll,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE)
        );
        renderSettingsPopupScrollbar(context, popupX, popupY, scaledWidth, scaledHeight, maxScroll);
        PathmindPopupRenderer.drawButton(
            context,
            host.font(),
            closeButton,
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.close"),
            PathmindPopupRenderer.ButtonStyle.ACCENT,
            accentColor(),
            animation
        );
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void drawAccentOption(GuiGraphics context, int x, int y, AccentOption option, boolean hovered, boolean selected) {
        float hoverProgress = selected ? 1f : host.hoverProgress("settings-accent-option:" + option.name(), hovered);
        PathmindSettingsRowRenderer.renderAccentOption(
            context,
            host.font(),
            x,
            y,
            SETTINGS_OPTION_WIDTH,
            SETTINGS_OPTION_HEIGHT,
            option.label,
            option.color,
            selected,
            hoverProgress,
            accentColor(),
            animation
        );
    }

    void renderToggleRow(GuiGraphics context, int mouseX, int mouseY, int labelX, int centerY, String label, boolean active, int popupX, int scaledWidth) {
        PathmindSettingsRowRenderer.renderToggleRow(
            context,
            host.font(),
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
            accentColor(),
            animation,
            Component.translatable("pathmind.settings.on").getString(),
            Component.translatable("pathmind.settings.off").getString()
        );
    }

    void renderSliderRow(GuiGraphics context, int mouseX, int mouseY, int labelX, int centerY, String label,
                                 int value, int min, int max, int popupX, int scaledWidth) {
        PathmindSettingsRowRenderer.renderSliderRow(
            context,
            host.font(),
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
            accentColor(),
            animation,
            tr("pathmind.unit.millisecondsShort"),
            nodeDelayDragging
        );
    }

    void renderNodeDelayRow(GuiGraphics context, int mouseX, int mouseY, int labelX, int centerY,
                                    int value, int min, int max, int popupX, int scaledWidth) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;

        String valueText = Integer.toString(value);
        int[] valueBox = getNodeDelayFieldBounds(popupX, scaledWidth, centerY, valueText);
        boolean fieldHovered = host.isPointInRect(mouseX, mouseY, valueBox[0], valueBox[1], valueBox[2], valueBox[3]);
        boolean focused = nodeDelayField != null && nodeDelayField.isFocused();
        float fieldHoverProgress = focused ? 1f : host.hoverProgress("settings-node-delay-field", fieldHovered);
        PathmindSettingsRowRenderer.renderNumericField(
            context,
            host.font(),
            nodeDelayField,
            mouseX,
            mouseY,
            labelX,
            centerY,
            Component.translatable("pathmind.settings.nodeDelay").getString(),
            valueBox[0],
            valueBox[1],
            valueBox[2],
            valueBox[3],
            valueText,
            Component.translatable("pathmind.unit.millisecondsShort"),
            accentColor(),
            animation,
            fieldHoverProgress,
            focused,
            TEXT_FIELD_VERTICAL_PADDING
        );

        boolean hovered = host.isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        float sliderHoverProgress = nodeDelayDragging ? 1f : host.hoverProgress("settings-node-delay-slider", hovered);
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
            accentColor(),
            animation,
            sliderHoverProgress
        );
    }

    void renderCreateListRadiusRow(GuiGraphics context, int mouseX, int mouseY, int labelX, int centerY,
                                           int value, int min, int max, int popupX, int scaledWidth) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;

        String valueText = Integer.toString(value);
        int[] valueBox = getCreateListRadiusFieldBounds(popupX, scaledWidth, centerY, valueText);
        boolean fieldHovered = host.isPointInRect(mouseX, mouseY, valueBox[0], valueBox[1], valueBox[2], valueBox[3]);
        boolean focused = createListRadiusField != null && createListRadiusField.isFocused();
        float fieldHoverProgress = focused ? 1f : host.hoverProgress("settings-create-list-radius-field", fieldHovered);
        PathmindSettingsRowRenderer.renderNumericField(
            context,
            host.font(),
            createListRadiusField,
            mouseX,
            mouseY,
            labelX,
            centerY,
            Component.translatable("pathmind.field.radius").getString(),
            valueBox[0],
            valueBox[1],
            valueBox[2],
            valueBox[3],
            valueText,
            Component.translatable("pathmind.unit.blocks"),
            accentColor(),
            animation,
            fieldHoverProgress,
            focused,
            TEXT_FIELD_VERTICAL_PADDING
        );

        boolean hovered = host.isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        float sliderHoverProgress = createListRadiusDragging ? 1f : host.hoverProgress("settings-create-list-radius-slider", hovered);
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
            accentColor(),
            animation,
            sliderHoverProgress
        );
    }

    int[] getNodeDelayFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = host.font().width(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = host.font().width(tr("pathmind.unit.millisecondsShort"));
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    int[] getCreateListRadiusFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = host.font().width(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = host.font().width(tr("pathmind.unit.blocks"));
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    void updateNodeDelayFromMouse(int mouseX, int popupX, int popupWidth) {
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = Mth.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
        float t = SETTINGS_SLIDER_WIDTH <= 0 ? 0f : localX / (float) SETTINGS_SLIDER_WIDTH;
        int value = NODE_DELAY_MIN_MS + Math.round(t * (NODE_DELAY_MAX_MS - NODE_DELAY_MIN_MS));
        if (value != nodeDelayMs) {
            nodeDelayMs = value;
            settings.nodeDelayMs = nodeDelayMs;
            SettingsManager.save(settings);
        }
    }

    void updateCreateListRadiusFromMouse(Node node, int mouseX, int popupX, int popupWidth) {
        if (node != null && node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = Mth.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
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
            return Mth.clamp(parsed, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS);
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
            return Mth.clamp(parsed, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
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
        if (!supportsNodeSettings(type) || settings == null) {
            return false;
        }
        return switch (type) {
            case GOTO -> Boolean.TRUE.equals(settings.gotoAllowBreakWhileExecuting)
                || Boolean.TRUE.equals(settings.gotoAllowPlaceWhileExecuting);
            case SENSOR_KEY_PRESSED -> settings.keyPressedActivatesInGuis != null
                && !settings.keyPressedActivatesInGuis;
            case CREATE_LIST -> {
                boolean edited = false;
                if (host.nodeGraph() != null) {
                    for (Node node : host.nodeGraph().getNodes()) {
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
            return Mth.clamp(configured == null ? 64 : configured, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
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
        return Mth.clamp((int) Math.round(value), CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
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
        if (host.nodeGraph() != null) {
            host.nodeGraph().notifyNodeParametersChanged(node);
        }
    }

    void setCreateListSettingsRadius(Node node, int radius) {
        int clamped = Mth.clamp(radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
        Settings settings = SettingsManager.getCurrent();
        settings.createListRadius = clamped;
        SettingsManager.save(settings);
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        node.ensureCreateListRadiusParameters();
        node.setParameterValueAndPropagate("Radius", Integer.toString(clamped));
        if (host.nodeGraph() != null) {
            host.nodeGraph().notifyNodeParametersChanged(node);
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
        if (supportsNodeSettings(settingsNodeTargetType)) {
            return settingsNodeTargetType;
        }
        if (supportsNodeSettings(settingsNodeTarget)) {
            return settingsNodeTarget.getType();
        }
        return null;
    }

    Node findFirstNodeWithSettingsType(NodeType type) {
        if (!supportsNodeSettings(type) || host.nodeGraph() == null) {
            return null;
        }
        for (Node node : host.nodeGraph().getNodes()) {
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
        if (supportsNodeSettings(settingsNodeTarget) && settingsNodeTarget.getType() == targetType) {
            return settingsNodeTarget;
        }
        return findFirstNodeWithSettingsType(targetType);
    }

    int getSettingsNodeSectionContentBottom(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        NodeType targetType = getEffectiveSettingsTargetType();
        if (settingsNodeListView || targetType == null) {
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

    int[] getSettingsCacheRecipesButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int[] clearBounds = getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int buttonWidth = SETTINGS_SECTION_BUTTON_WIDTH + 36;
        return new int[]{clearBounds[0] - buttonWidth - 6, clearBounds[1], buttonWidth, SETTINGS_SECTION_BUTTON_HEIGHT};
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
        boolean cleared = Node.clearRecipeCache(host.client());
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (cleared) {
            overlay.show(Component.translatable("pathmind.settings.cacheCleared").getString(), UITheme.STATE_SUCCESS);
        } else {
            overlay.show(Component.translatable("pathmind.settings.cacheNotFound").getString(), UITheme.STATE_ERROR);
        }
    }

    void cacheSettingsRecipes() {
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (host.client() == null || host.client().getSingleplayerServer() == null) {
            overlay.show(Component.translatable("pathmind.settings.cacheRequiresSingleplayer").getString(), UITheme.STATE_ERROR);
            return;
        }
        if (Node.requestRecipeCacheWarmup(host.client())) {
            overlay.show(Component.translatable("pathmind.settings.cacheStarted").getString(), UITheme.ACCENT_SKY);
        } else {
            overlay.show(Component.translatable("pathmind.settings.cacheStartFailed").getString(), UITheme.STATE_ERROR);
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

    int[] getSettingsReplayTutorialButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int dividerY = getSettingsReplayTutorialDividerY(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int buttonY = dividerY + 8;
        int buttonX = popupX + popupWidth - SETTINGS_SECTION_BUTTON_WIDTH - 20;
        return new int[]{buttonX, buttonY, SETTINGS_SECTION_BUTTON_WIDTH, SETTINGS_SECTION_BUTTON_HEIGHT};
    }

    int getSettingsReplayTutorialRowCenterY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsReplayTutorialButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY)[1]
            + SETTINGS_SECTION_BUTTON_HEIGHT / 2;
    }

    int getSettingsReplayTutorialDividerY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        return restoreExamplesButtonBounds[1] + restoreExamplesButtonBounds[3] + 10;
    }

    void restoreExamplePresets() {
        OnboardingPresetManager.RestoreResult result = OnboardingPresetManager.restoreExamplePresets();
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (result.success()) {
            host.refreshAvailablePresets();
            overlay.show(Component.translatable("pathmind.settings.examplePresetsRestored").getString(), UITheme.STATE_SUCCESS);
        } else {
            overlay.show(Component.translatable("pathmind.settings.examplePresetsRestoreFailed").getString(), UITheme.STATE_ERROR);
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
            settingsNodeSelectorScrollOffset,
            20
        );
    }

    int[] getSettingsNodeTypeButtonBounds(int contentX, int bodyY, int contentWidth, int maxScroll, int index) {
        int y = getSettingsNodeTypeListY(bodyY) + index * (SETTINGS_NODE_TYPE_BUTTON_HEIGHT + SETTINGS_NODE_TYPE_BUTTON_GAP) - settingsNodeSelectorScrollOffset;
        int rowX = contentX + 2;
        int rowWidth = Math.max(0, contentWidth - 2 - (maxScroll > 0 ? UITheme.SCROLLBAR_WIDTH : 0));
        return new int[]{rowX, y, rowWidth, SETTINGS_NODE_TYPE_BUTTON_HEIGHT};
    }

    void renderSettingsNodeTypeSelector(GuiGraphics context, int mouseX, int mouseY, int contentX, int bodyY, int contentWidth) {
        int[] selectorBounds = getSettingsNodeTypeSelectorBounds(contentX, bodyY, contentWidth);
        int[] searchBounds = getSettingsNodeTypeSearchFieldBounds(contentX, bodyY, contentWidth);
        boolean searchHovered = host.isPointInRect(mouseX, mouseY, searchBounds[0], searchBounds[1], searchBounds[2], searchBounds[3]);
        boolean searchFocused = settingsNodeSearchField != null && settingsNodeSearchField.isFocused();
        float searchHoverProgress = searchFocused ? 1f : host.hoverProgress("settings-node-search-box", searchHovered);
        UIStyleHelper.FieldPalette searchPalette = UIStyleHelper.getSearchFieldPalette(accentColor(), searchHoverProgress, searchFocused, false);
        UIStyleHelper.ScrollContainerPalette selectorPalette = UIStyleHelper.getScrollContainerPalette(accentColor(), 0f, true, false);
        int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(contentWidth);
        settingsNodeSelectorScrollOffset = ScrollbarHelper.clampScroll(settingsNodeSelectorScrollOffset, maxSelectorScroll);
        UIStyleHelper.drawScrollContainer(
            context,
            selectorBounds[0],
            selectorBounds[1],
            selectorBounds[2],
            selectorBounds[3],
            new UIStyleHelper.ScrollContainerPalette(
                animation.getAnimatedPopupColor(selectorPalette.backgroundColor()),
                animation.getAnimatedPopupColor(selectorPalette.borderColor()),
                animation.getAnimatedPopupColor(selectorPalette.innerBorderColor()),
                animation.getAnimatedPopupColor(selectorPalette.trackColor()),
                animation.getAnimatedPopupColor(selectorPalette.thumbColor())
            )
        );
        UIStyleHelper.drawFieldFrame(
            context,
            searchBounds[0],
            searchBounds[1],
            searchBounds[2],
            searchBounds[3],
            new UIStyleHelper.FieldPalette(
                animation.getAnimatedPopupColor(searchPalette.backgroundColor()),
                animation.getAnimatedPopupColor(searchPalette.borderColor()),
                animation.getAnimatedPopupColor(searchPalette.innerBorderColor()),
                animation.getAnimatedPopupColor(searchPalette.textColor()),
                animation.getAnimatedPopupColor(searchPalette.placeholderColor())
            )
        );
        if (settingsNodeSearchField != null) {
            int textFieldHeight = Math.max(10, SETTINGS_NODE_TYPE_SEARCH_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2);
            settingsNodeSearchField.setVisible(true);
            settingsNodeSearchField.setEditable(true);
            settingsNodeSearchField.setSuggestion(!searchFocused && settingsNodeSearchField.getValue().isEmpty() ? tr("pathmind.search.nodeSettings") : null);
            settingsNodeSearchField.setPosition(searchBounds[0] + 8, searchBounds[1] + TEXT_FIELD_VERTICAL_PADDING);
            settingsNodeSearchField.setWidth(Math.max(0, searchBounds[2] - 16));
            settingsNodeSearchField.setHeight(textFieldHeight);
            settingsNodeSearchField.render(context, mouseX, mouseY, 0.0f);
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
            settingsNodeSelectorScrollOffset,
            maxSelectorScroll,
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE)
        );
        ScrollbarHelper.renderSettingsStyle(
            context,
            selectorScrollMetrics,
            animation.getAnimatedPopupColor(selectorPalette.trackColor()),
            animation.getAnimatedPopupColor(selectorPalette.borderColor()),
            animation.getAnimatedPopupColor(selectorPalette.thumbColor())
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
            boolean hovered = host.isPointInRect(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
            boolean selected = type == selectedType;
            float hoverProgress = selected ? 1f : host.hoverProgress("settings-node-selector:" + type.name(), hovered);
            PathmindSettingsRowRenderer.renderDescriptionListRow(
                context,
                host.font(),
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3],
                type.getDisplayName(),
                getSettingsNodeTypeDescription(type),
                hovered,
                selected,
                hoverProgress,
                accentColor(),
                animation
            );
        }
        if (filteredTypes.isEmpty()) {
            context.drawString(host.font(), Component.translatable("pathmind.settings.nodeSettings.noMatches"),
                contentX + 8, listTop + 8, animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));
        }
        context.disableScissor();
    }

    List<NodeType> getFilteredSettingsNodeTypes() {
        List<NodeType> filteredTypes = new ArrayList<>();
        String query = settingsNodeSearchField != null ? settingsNodeSearchField.getValue() : "";
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
            case GOTO -> Component.translatable("pathmind.settings.nodeSettings.description.goto").getString();
            case SENSOR_KEY_PRESSED -> Component.translatable("pathmind.settings.nodeSettings.description.keyPressed").getString();
            case CREATE_LIST -> Component.translatable("pathmind.settings.nodeSettings.description.createList").getString();
            default -> Component.translatable("pathmind.settings.nodeSettings.description.default").getString();
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

    void renderSettingsNodeList(GuiGraphics context, int mouseX, int mouseY, int popupX, int popupY, int scaledWidth, int scaledHeight, int contentX, int bodyY) {
        List<NodeType> settingsNodes = getSettingsNodeTypes();
        int[] listBounds = getSettingsNodeListBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, bodyY);
        int listX = listBounds[0];
        int listY = listBounds[1];
        int listWidth = listBounds[2];
        int listHeight = listBounds[3];
        if (settingsNodes.isEmpty()) {
            host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.settings.nodeSettings.none").getString(), contentX, bodyY, scaledWidth - 40,
                animation.getAnimatedPopupColor(UITheme.TEXT_TERTIARY));
            return;
        }

        int visibleRows = Math.max(1, listHeight / SETTINGS_NODE_LIST_ROW_HEIGHT);
        int maxScroll = Math.max(0, settingsNodes.size() - visibleRows);
        settingsNodeListScrollOffset = Mth.clamp(settingsNodeListScrollOffset, 0, maxScroll);

        UIStyleHelper.drawBeveledPanel(
            context,
            listX,
            listY,
            listWidth,
            listHeight,
            animation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            animation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE),
            animation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        context.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        int startIndex = settingsNodeListScrollOffset;
        int endIndex = Math.min(settingsNodes.size(), startIndex + visibleRows + 1);
        for (int i = startIndex; i < endIndex; i++) {
            NodeType type = settingsNodes.get(i);
            int rowY = listY + (i - startIndex) * SETTINGS_NODE_LIST_ROW_HEIGHT;
            boolean hovered = host.isPointInRect(mouseX, mouseY, listX, rowY, listWidth, SETTINGS_NODE_LIST_ROW_HEIGHT);
            boolean editing = getEffectiveSettingsTargetType() == type && !settingsNodeListView;
            String status = editing ? Component.translatable("pathmind.settings.nodeSettings.status.editing").getString() : hasEditedNodeSettings(type) ? Component.translatable("pathmind.settings.nodeSettings.status.edited").getString() : "";
            PathmindSettingsRowRenderer.renderStatusListRow(
                context,
                host.font(),
                listX,
                rowY,
                listWidth,
                SETTINGS_NODE_LIST_ROW_HEIGHT,
                type.getDisplayName(),
                status,
                hovered,
                editing,
                accentColor(),
                animation
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
        int[] replayTutorialButtonBounds = getSettingsReplayTutorialButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int contentBottom = replayTutorialButtonBounds[1] + replayTutorialButtonBounds[3];
        return Math.max(0, contentBottom - bodyBottom + 24);
    }

    void renderSettingsPopupScrollbar(GuiGraphics context, int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        ScrollbarHelper.renderSettingsStyle(
            context,
            getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll),
            animation.getAnimatedPopupColor(UITheme.BACKGROUND_SIDEBAR),
            animation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            animation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT)
        );
    }

    ScrollbarHelper.Metrics getSettingsPopupScrollMetrics(int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        PathmindPopupLayout.Rect bodyBounds = getSettingsPopupBodyRect(popupX, popupY, popupWidth, popupHeight);
        return ScrollbarHelper.metrics(popupX + popupWidth - 12, bodyBounds.y(), 4, Math.max(1, bodyBounds.height()), maxScroll, settingsPopupScrollOffset, 20);
    }
}
