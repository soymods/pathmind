package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.overlay.BookTextEditorOverlay;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.overlay.NodeParameterOverlay;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.validation.GraphValidationIssue;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidationSeverity;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.VersionSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.OverlayProtection;
import com.pathmind.util.UiUtilsProxy;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The main visual editor screen for Pathmind.
 * This screen provides the interface for creating and editing node-based workflows.
 */
public class PathmindVisualEditorScreen extends Screen {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int TAB_BAR_LEFT_PADDING = 188;
    private static final int TAB_BAR_RIGHT_PADDING = 230;
    private static final int TAB_BAR_TOP = 4;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 4;
    private static final int TAB_MIN_WIDTH = 72;
    private static final int TAB_MAX_WIDTH = 140;
    private static final int PRESET_TAB_TEXT_PADDING = 6;
    private static final int PRESET_TAB_CLOSE_ICON_SIZE = 6;
    private static final int PRESET_TAB_CLOSE_HITBOX_PADDING = 2;
    private static final int PRESET_TAB_CLOSE_GAP = 4;
    private static final int PRESET_TAB_ADD_WIDTH = 20;
    private static final int PRESET_TAB_HARD_MIN_WIDTH = 24;
    private static final int PRESET_TAB_TITLE_GAP = 8;
    private static final int PRESET_BROWSER_BUTTON_SIZE = 16;
    private static final int PRESET_BROWSER_BUTTON_GAP = 3;
    private static final int PRESET_TAB_DRAG_THRESHOLD = 4;
    private static final boolean IS_MAC_OS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("mac");
    
    // Colors now come from UITheme for consistency
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int MARKETPLACE_BUTTON_WIDTH = BOTTOM_BUTTON_SIZE * 3 + BOTTOM_BUTTON_SPACING * 2;
    private static final int PRESET_DROPDOWN_WIDTH = 220;
    private static final int PRESET_DROPDOWN_HEIGHT = 18;
    private static final int PRESET_DROPDOWN_MARGIN = 6;
    private static final int PRESET_OPTION_HEIGHT = 18;
    private static final int PRESET_TEXT_LEFT_PADDING = 6;
    private static final int PRESET_DELETE_ICON_SIZE = 8;
    private static final int PRESET_DELETE_ICON_MARGIN = 6;
    private static final int PRESET_DELETE_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_RENAME_ICON_SIZE = 8;
    private static final int PRESET_RENAME_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_TEXT_ICON_GAP = 4;
    private static final int CREATE_PRESET_POPUP_WIDTH = 320;
    private static final int CREATE_PRESET_POPUP_HEIGHT = 170;
    private static final int PLAY_BUTTON_SIZE = 18;
    private static final int PLAY_BUTTON_MARGIN = 6;
    private static final int STOP_BUTTON_SIZE = 18;
    private static final int CONTROL_BUTTON_GAP = 6;
    private static final int VALIDATION_BUTTON_SIZE = 18;
    private static final int VALIDATION_BUTTON_WIDE_WIDTH = STOP_BUTTON_SIZE + CONTROL_BUTTON_GAP + PLAY_BUTTON_SIZE;
    private static final int VALIDATION_BUTTON_WIDE_HEIGHT = 16;
    private static final int VALIDATION_PANEL_WIDTH = 292;
    private static final int VALIDATION_PANEL_MAX_VISIBLE_ROWS = 8;
    private static final int VALIDATION_PANEL_ROW_HEIGHT = 22;
    private static final int VALIDATION_PANEL_PADDING = 8;
    private static final int VALIDATION_PANEL_TOP_GAP = 6;
    private static final int VALIDATION_PANEL_SECTION_GAP = 4;
    private static final int VALIDATION_PANEL_BOTTOM_PADDING = 2;
    private static final int VALIDATION_PANEL_HEADER_HEIGHT = 34;
    private static final int VALIDATION_PANEL_FOOTER_HEIGHT = 18;
    private static final int ZOOM_BUTTON_SIZE = 14;
    private static final int ZOOM_BUTTON_MARGIN = 6;
    private static final int ZOOM_BUTTON_SPACING = 4;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    private static final int PRESET_DELETE_POPUP_WIDTH = 320;
    private static final int PRESET_DELETE_POPUP_HEIGHT = 160;
    private static final int PRESET_DELETE_SKIP_CHECKBOX_SIZE = 10;
    private static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    private static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    private static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    private static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;
    private static final String UI_UTILS_DOWNLOAD_URL = "https://ui-utils.com";
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
    private static final int CREATE_LIST_RADIUS_MIN = 1;
    private static final int CREATE_LIST_RADIUS_MAX = 512;
    private static final NodeType[] SETTINGS_NODE_TYPES = {
        NodeType.GOTO,
        NodeType.SENSOR_KEY_PRESSED,
        NodeType.CREATE_LIST
    };
    private static final int NODE_DELAY_MIN_MS = 0;
    private static final int NODE_DELAY_MAX_MS = 500;
    private static final int TITLE_INTERACTION_PADDING = 4;
    private static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final int NODE_SEARCH_FIELD_WIDTH = 180;
    private static final int NODE_SEARCH_FIELD_HEIGHT = 22;
    private static final int NODE_SEARCH_DROPDOWN_TOP_GAP = 2;
    private static final int NODE_SEARCH_RESULT_HEIGHT = 18;
    private static final int NODE_SEARCH_MAX_RESULTS = 8;
    private static final int NODE_SEARCH_RESULT_TEXT_PADDING = 6;
    private static final String INFO_POPUP_AUTHOR = "soymods";
    private static final String INFO_POPUP_TARGET_VERSION = VersionSupport.SUPPORTED_RANGE;
    private static final Text TITLE_TEXT = Text.literal("Pathmind");
    private static final Text INFO_POPUP_TITLE_TEXT = Text.literal("Pathmind");

    private NodeGraph nodeGraph;
    private Sidebar sidebar;
    private NodeParameterOverlay parameterOverlay;
    private BookTextEditorOverlay bookTextEditorOverlay;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;

    // Drag and drop state
    private boolean isDraggingFromSidebar = false;
    private NodeType draggingNodeType = null;
    private Node draggingSidebarNode = null;

    // Right-click context menu state
    private static final int CLICK_THRESHOLD = 5;  // pixels
    private static final long CLICK_TIME_THRESHOLD = 250;  // milliseconds
    private int rightClickStartX = -1;
    private int rightClickStartY = -1;
    private long rightClickStartTime = 0;
    private TextFieldWidget nodeSearchField;
    private boolean nodeSearchOpen = false;
    private int nodeSearchFieldX = 0;
    private int nodeSearchFieldY = 0;
    private int nodeSearchWorldX = 0;
    private int nodeSearchWorldY = 0;
    private float nodeSearchScale = 1.0f;
    private final List<NodeSearchResult> nodeSearchResults = new ArrayList<>();
    private int nodeSearchHoverIndex = -1;

    // Workspace dialogs
    private final PopupAnimationHandler clearPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler importExportPopupAnimation = new PopupAnimationHandler();
    private Path lastImportExportPath;
    private String importExportStatus = "";
    private int importExportStatusColor = UITheme.TEXT_SECONDARY;

    private boolean presetDropdownOpen = false;
    private final AnimatedValue presetDropdownAnimation = AnimatedValue.forHover();
    private int presetDropdownScrollOffset = 0;
    private final AnimatedValue titleUnderlineAnimation = AnimatedValue.forHover();
    private List<String> availablePresets = new ArrayList<>();
    private final List<String> presetTabOrder = new ArrayList<>();
    private final Map<String, AnimatedValue> presetTabXAnimations = new HashMap<>();
    private final Map<String, AnimatedValue> presetTabAppearAnimations = new HashMap<>();
    private final AnimatedValue presetTabAddButtonFadeAnimation = new AnimatedValue(1f, AnimationHelper::easeOutCubic);
    private boolean presetTabsInitialized = false;
    private static final long PRESET_TAB_RENAME_DOUBLE_CLICK_MS = 300L;
    private String pendingPresetTabInteractionName = null;
    private int pendingPresetTabPressMouseX = 0;
    private int pendingPresetTabPressMouseY = 0;
    private int pendingPresetTabPressTabLeft = 0;
    private String draggingPresetTabName = null;
    private int draggingPresetTabPointerOffsetX = 0;
    private int draggingPresetTabCurrentX = 0;
    private String animatingPresetDeletionName = null;
    private long animatingPresetDeletionExecuteAtMs = 0L;
    private String activePresetName = "";
    private final PopupAnimationHandler createPresetPopupAnimation = new PopupAnimationHandler();
    private TextFieldWidget createPresetField;
    private String createPresetStatus = "";
    private int createPresetStatusColor = UITheme.TEXT_SECONDARY;
    private final PopupAnimationHandler renamePresetPopupAnimation = new PopupAnimationHandler();
    private TextFieldWidget renamePresetField;
    private TextFieldWidget inlinePresetRenameField;
    private String renamePresetStatus = "";
    private int renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    private String pendingPresetRenameName = "";
    private String inlinePresetRenameName = "";
    private long lastPresetTitleClickTime = 0L;
    private String lastPresetTitleClickName = "";
    private final PopupAnimationHandler infoPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler presetDeletePopupAnimation = new PopupAnimationHandler();
    private String pendingPresetDeletionName = "";
    private final PopupAnimationHandler missingBaritonePopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingUiUtilsPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler settingsPopupAnimation = new PopupAnimationHandler();
    private final AnimatedValue validationPanelAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private boolean validationPanelOpen = false;
    private Settings currentSettings;
    private static final String[] SUPPORTED_LANGUAGES = {"en_us", "es_es", "pt_br", "ru_ru", "de_de", "fr_fr", "pl_pl"};
    private boolean languageDropdownOpen = false;
    private final AnimatedValue languageDropdownAnimation = AnimatedValue.forHover();
    private int languageDropdownX = 0;
    private int languageDropdownY = 0;
    private int languageDropdownWidth = 0;
    private boolean showGrid = true;
    private boolean showWorkspaceTooltips = true;
    private boolean showChatErrors = true;
    private boolean showHudOverlays = true;
    private boolean skipPresetDeleteConfirm = false;
    private int nodeDelayMs = 150;
    private boolean nodeDelayDragging = false;
    private boolean createListRadiusDragging = false;
    private TextFieldWidget nodeDelayField;
    private TextFieldWidget createListRadiusField;
    private boolean settingsNodeListView = true;
    private NodeType settingsNodeTargetType = null;
    private Node settingsNodeTarget = null;
    private int settingsNodeListScrollOffset = 0;
    private int settingsPopupScrollOffset = 0;
    private AccentOption accentOption = AccentOption.SKY;
    private boolean overlayCutoutActive = false;
    private int overlayCutoutX = 0;
    private int overlayCutoutY = 0;
    private int overlayCutoutWidth = 0;
    private int overlayCutoutHeight = 0;
    private Boolean uiUtilsOverlayPrevEnabled = null;
    private final List<WorkspaceTab> workspaceTabs = new ArrayList<>();
    private int activeWorkspaceTabIndex = 0;

    private static final class WorkspaceTab {
        private String label;
        private NodeGraphData graphData;
        private final Integer parentTabIndex;
        private final String hostTemplateNodeId;

        private WorkspaceTab(String label, NodeGraphData graphData, Integer parentTabIndex, String hostTemplateNodeId) {
            this.label = label;
            this.graphData = graphData;
            this.parentTabIndex = parentTabIndex;
            this.hostTemplateNodeId = hostTemplateNodeId;
        }
    }

    private static final class NodeSearchResult {
        private final NodeType nodeType;
        private final String label;
        private final String categoryLabel;
        private final int score;

        private NodeSearchResult(NodeType nodeType, String label, String categoryLabel, int score) {
            this.nodeType = nodeType;
            this.label = label;
            this.categoryLabel = categoryLabel;
            this.score = score;
        }
    }

    private enum AccentOption {
        SKY("Sky", UITheme.ACCENT_SKY),
        MINT("Mint", UITheme.ACCENT_MINT),
        AMBER("Amber", UITheme.ACCENT_AMBER);

        private final String label;
        private final int color;

        AccentOption(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public PathmindVisualEditorScreen() {
        super(Text.translatable("screen.pathmind.visual_editor.title"));
        this.baritoneAvailable = BaritoneDependencyChecker.isBaritoneApiPresent();
        this.uiUtilsAvailable = UiUtilsProxy.isAvailable();
        this.nodeGraph = new NodeGraph();
        this.sidebar = new Sidebar(baritoneAvailable, uiUtilsAvailable);
        refreshAvailablePresets();
        this.nodeGraph.setActivePreset(activePresetName);
        updateImportExportPathFromPreset();

        // Load settings
        this.currentSettings = SettingsManager.load();

        // Apply loaded settings
        this.accentOption = getAccentOptionFromString(currentSettings.accentColor);
        this.showGrid = currentSettings.showGrid == null || currentSettings.showGrid;
        this.showWorkspaceTooltips = currentSettings.showTooltips == null || currentSettings.showTooltips;
        this.showChatErrors = currentSettings.showChatErrors == null || currentSettings.showChatErrors;
        this.showHudOverlays = currentSettings.showHudOverlays == null || currentSettings.showHudOverlays;
        this.skipPresetDeleteConfirm = currentSettings.skipPresetDeleteConfirm != null && currentSettings.skipPresetDeleteConfirm;
        this.nodeDelayMs = currentSettings.nodeDelayMs != null ? currentSettings.nodeDelayMs : 150;
    }

    private AccentOption getAccentOptionFromString(String color) {
        switch (color.toLowerCase()) {
            case "mint": return AccentOption.MINT;
            case "amber": return AccentOption.AMBER;
            default: return AccentOption.SKY;
        }
    }

    private String getAccentOptionString(AccentOption option) {
        switch (option) {
            case MINT: return "mint";
            case AMBER: return "amber";
            default: return "sky";
        }
    }

    @Override
    protected void init() {
        super.init();
        if (uiUtilsOverlayPrevEnabled == null) {
            uiUtilsOverlayPrevEnabled = UiUtilsProxy.setOverlayEnabled(false);
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);

        if (createPresetField == null) {
            createPresetField = new TextFieldWidget(this.textRenderer, 0, 0, 200, 20, Text.translatable("pathmind.field.presetName"));
            createPresetField.setMaxLength(64);
            createPresetField.setDrawsBackground(false);
            createPresetField.setVisible(false);
            createPresetField.setEditable(false);
            createPresetField.setEditableColor(UITheme.TEXT_PRIMARY);
            createPresetField.setUneditableColor(UITheme.TEXT_TERTIARY);
            createPresetField.setChangedListener(value -> clearCreatePresetStatus());
            this.addSelectableChild(createPresetField);
        }

        if (renamePresetField == null) {
            renamePresetField = new TextFieldWidget(this.textRenderer, 0, 0, 200, 20, Text.translatable("pathmind.field.newPresetName"));
            renamePresetField.setMaxLength(64);
            renamePresetField.setDrawsBackground(false);
            renamePresetField.setVisible(false);
            renamePresetField.setEditable(false);
            renamePresetField.setEditableColor(UITheme.TEXT_PRIMARY);
            renamePresetField.setUneditableColor(UITheme.TEXT_TERTIARY);
            renamePresetField.setChangedListener(value -> clearRenamePresetStatus());
            this.addSelectableChild(renamePresetField);
        }
        if (inlinePresetRenameField == null) {
            inlinePresetRenameField = new TextFieldWidget(this.textRenderer, 0, 0, 200, 20, Text.translatable("pathmind.field.newPresetName"));
            inlinePresetRenameField.setMaxLength(64);
            inlinePresetRenameField.setDrawsBackground(false);
            inlinePresetRenameField.setVisible(false);
            inlinePresetRenameField.setEditable(false);
            inlinePresetRenameField.setEditableColor(UITheme.TEXT_PRIMARY);
            inlinePresetRenameField.setUneditableColor(UITheme.TEXT_TERTIARY);
            this.addSelectableChild(inlinePresetRenameField);
        }
        if (nodeDelayField == null) {
            nodeDelayField = new TextFieldWidget(this.textRenderer, 0, 0, 120, 20, Text.literal("Delay"));
            nodeDelayField.setMaxLength(6);
            nodeDelayField.setDrawsBackground(false);
            nodeDelayField.setVisible(false);
            nodeDelayField.setEditable(false);
            nodeDelayField.setEditableColor(UITheme.TEXT_HEADER);
            nodeDelayField.setUneditableColor(UITheme.TEXT_HEADER);
            nodeDelayField.setTextPredicate(value -> value == null || value.isEmpty() || value.chars().allMatch(Character::isDigit));
            nodeDelayField.setChangedListener(value -> {
                Integer parsed = parseDelayFieldValue(value);
                if (parsed != null && parsed != nodeDelayMs) {
                    nodeDelayMs = parsed;
                    currentSettings.nodeDelayMs = nodeDelayMs;
                    SettingsManager.save(currentSettings);
                }
            });
            this.addSelectableChild(nodeDelayField);
        }
        if (createListRadiusField == null) {
            createListRadiusField = new TextFieldWidget(this.textRenderer, 0, 0, 120, 20, Text.literal("Radius"));
            createListRadiusField.setMaxLength(6);
            createListRadiusField.setDrawsBackground(false);
            createListRadiusField.setVisible(false);
            createListRadiusField.setEditable(false);
            createListRadiusField.setEditableColor(UITheme.TEXT_HEADER);
            createListRadiusField.setUneditableColor(UITheme.TEXT_HEADER);
            createListRadiusField.setTextPredicate(value -> value == null || value.isEmpty() || value.chars().allMatch(Character::isDigit));
            createListRadiusField.setChangedListener(value -> {
                Node targetNode = getEffectiveSettingsTargetNode();
                Integer parsed = parseCreateListRadiusFieldValue(value);
                if (parsed != null && targetNode != null && parsed != getCreateListSettingsRadius(targetNode)) {
                    setCreateListSettingsRadius(targetNode, parsed);
                }
            });
            this.addSelectableChild(createListRadiusField);
        }
        if (nodeSearchField == null) {
            nodeSearchField = new TextFieldWidget(this.textRenderer, 0, 0, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT, Text.literal("Search nodes"));
            nodeSearchField.setMaxLength(64);
            nodeSearchField.setDrawsBackground(false);
            nodeSearchField.setVisible(false);
            nodeSearchField.setEditable(false);
            nodeSearchField.setEditableColor(UITheme.TEXT_PRIMARY);
            nodeSearchField.setUneditableColor(UITheme.TEXT_TERTIARY);
            nodeSearchField.setHeight(Math.max(10, NODE_SEARCH_FIELD_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2));
            nodeSearchField.setChangedListener(value -> updateNodeSearchMatch());
            this.addSelectableChild(nodeSearchField);
        }

        updateImportExportPathFromPreset();

        // Try to load saved node graph first
        if (nodeGraph.hasSavedGraph()) {
            if (nodeGraph.load()) {
                nodeGraph.restoreSessionViewportState();
                resetWorkspaceTabsFromCurrentGraph();
                refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
                return; // Don't initialize default nodes if we loaded a saved graph
            }
        }
        
        // Initialize node graph with proper centering based on screen dimensions
        nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        nodeGraph.restoreSessionViewportState();
        resetWorkspaceTabsFromCurrentGraph();
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
    }

    @Override
    public void tick() {
        super.tick();
        ExecutionManager.getInstance().setWorkspaceGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        OverlayProtection.setPathmindRendering(true);
        try {
        recoverStaleLeftMouseDrag(mouseX, mouseY);
        resetOverlayCutout();
        context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);

        boolean titleHovered = isTitleHovered(mouseX, mouseY);
        boolean titleActive = titleHovered || infoPopupAnimation.isVisible();
        titleUnderlineAnimation.animateTo(titleActive ? 1f : 0f, UITheme.HOVER_ANIM_MS);
        titleUnderlineAnimation.tick();
        GraphValidationResult validationResult = nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);

        // Update mouse hover for socket highlighting
        nodeGraph.updateMouseHover(mouseX, mouseY);
        nodeGraph.setSidebarWidth(sidebar.getWidth());
        nodeGraph.setExecutionEnabled(shouldShowExecutionControls());
        
        // Render node graph (stationary nodes only)
        renderNodeGraph(context, mouseX, mouseY, delta, false);

        // Start a new GUI layer so UI chrome always sits above workspace nodes.
        DrawContextBridge.startNewRootLayer(context);

        // Workspace utilities should sit beneath the sidebar when it expands
        renderWorkspaceButtons(context, mouseX, mouseY);

        // Always render sidebar after node graph/buttons so expanded categories sit on top
        boolean sidebarInteractionsEnabled = !isPopupObscuringWorkspace();
        boolean allowSidebarTooltips = showWorkspaceTooltips && !nodeGraph.isAnyNodeBeingDragged();
        sidebar.render(
            context,
            this.textRenderer,
            mouseX,
            mouseY,
            TITLE_BAR_HEIGHT,
            this.height - TITLE_BAR_HEIGHT,
            sidebarInteractionsEnabled,
            allowSidebarTooltips
        );

        // Render title bar above the workspace so nodes never overlap it.
        UIStyleHelper.drawBeveledPanel(
            context,
            0,
            0,
            this.width,
            TITLE_BAR_HEIGHT,
            UITheme.BACKGROUND_SECTION,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );
        drawTitle(context, mouseX, mouseY, titleUnderlineAnimation.getValue());
        renderWorkspaceTabs(context, mouseX, mouseY);
        renderPresetDropdown(context, mouseX, mouseY, isPopupObscuringWorkspace());

        // Tick all popup animations early so the scrim uses current values
        clearPopupAnimation.tick();
        importExportPopupAnimation.tick();
        createPresetPopupAnimation.tick();
        renamePresetPopupAnimation.tick();
        presetDeletePopupAnimation.tick();
        infoPopupAnimation.tick();
        missingBaritonePopupAnimation.tick();
        missingUiUtilsPopupAnimation.tick();
        settingsPopupAnimation.tick();
        validationPanelAnimation.animateTo(validationPanelOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        validationPanelAnimation.tick();

        boolean controlsDisabled = isPopupObscuringWorkspace();
        if (controlsDisabled && validationPanelOpen) {
            validationPanelOpen = false;
        }
        renderZoomControls(context, mouseX, mouseY, controlsDisabled);

        if (shouldShowExecutionControls()) {
            renderStopButton(context, mouseX, mouseY, controlsDisabled);
            renderPlayButton(context, mouseX, mouseY, controlsDisabled);
        }
        renderValidationPanel(context, mouseX, mouseY, validationResult);
        renderValidationButton(context, mouseX, mouseY, controlsDisabled, validationResult);
        renderSettingsButton(context, mouseX, mouseY, controlsDisabled);

        if (controlsDisabled) {
            DrawContextBridge.startNewRootLayer(context);
        }

        Object popupMatrices = context.getMatrices();
        boolean popupDepthPushed = isPopupObscuringWorkspace();
        if (popupDepthPushed) {
            MatrixStackBridge.push(popupMatrices);
            MatrixStackBridge.translateZ(popupMatrices, 450.0f);
        }
        try {
            if (!isScreenPopupVisible()) {
                setOverlayCutoutForNodeOverlay();
            }
            renderPopupScrimOverlay(context);
            if (isPopupObscuringWorkspace()) {
                DrawContextBridge.startNewRootLayer(context);
            }

            // Render parameter overlay if visible
            if (parameterOverlay != null && parameterOverlay.isVisible()) {
                parameterOverlay.render(context, this.textRenderer, mouseX, mouseY, delta);
            }

            // Render book text editor overlay if visible
            if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
                bookTextEditorOverlay.render(context, this.textRenderer, mouseX, mouseY, delta);
            }

            if (clearPopupAnimation.isVisible()) {
                renderClearConfirmationPopup(context, mouseX, mouseY);
            }

            if (importExportPopupAnimation.isVisible()) {
                renderImportExportPopup(context, mouseX, mouseY, delta);
            }

            if (createPresetPopupAnimation.isVisible()) {
                renderCreatePresetPopup(context, mouseX, mouseY, delta);
            }

            if (renamePresetPopupAnimation.isVisible()) {
                renderRenamePresetPopup(context, mouseX, mouseY, delta);
            }

            if (presetDeletePopupAnimation.isVisible()) {
                renderPresetDeletePopup(context, mouseX, mouseY);
            }

            if (infoPopupAnimation.isVisible()) {
                renderInfoPopup(context, mouseX, mouseY);
            }
            if (missingBaritonePopupAnimation.isVisible()) {
                renderMissingBaritonePopup(context, mouseX, mouseY);
            }
            if (missingUiUtilsPopupAnimation.isVisible()) {
                renderMissingUiUtilsPopup(context, mouseX, mouseY);
            }
            if (settingsPopupAnimation.isVisible()) {
                renderSettingsPopup(context, mouseX, mouseY);
            }

            // Render language dropdown options on top of scrim overlay
            if (settingsPopupAnimation.isVisible()) {
                RenderStateBridge.setShaderColor(1f, 1f, 1f, settingsPopupAnimation.getPopupAlpha());
                drawLanguageDropdownOptions(context, languageDropdownX, languageDropdownY, languageDropdownWidth, mouseX, mouseY);
                RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
            }
        } finally {
            if (popupDepthPushed) {
                DrawContextBridge.flush(context);
                MatrixStackBridge.pop(popupMatrices);
            }
        }

        // Render context menu on top of everything
        nodeGraph.updateNodeContextMenuHover(mouseX, mouseY);
        nodeGraph.renderNodeContextMenu(context, this.textRenderer);
        nodeGraph.updateContextMenuHover(mouseX, mouseY);
        nodeGraph.renderContextMenu(context, this.textRenderer, mouseX, mouseY);
        renderNodeSearchField(context, mouseX, mouseY, delta);
        DrawContextBridge.startNewRootLayer(context);
        renderDraggedWorkspaceLayer(context, mouseX, mouseY, delta);
        if (isDraggingFromSidebar && (draggingNodeType != null || draggingSidebarNode != null)) {
            renderDraggingNode(context, mouseX, mouseY);
        }
        DrawContextBridge.startNewRootLayer(context);
        NodeErrorNotificationOverlay.getInstance().render(context, this.textRenderer, this.width, this.height);
        } finally {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    private boolean isPopupObscuringWorkspace() {
        boolean overlayVisible = parameterOverlay != null && parameterOverlay.isVisible();
        boolean bookOverlayVisible = bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible();
        return overlayVisible
                || bookOverlayVisible
                || clearPopupAnimation.isVisible()
                || importExportPopupAnimation.isVisible()
                || createPresetPopupAnimation.isVisible()
                || renamePresetPopupAnimation.isVisible()
                || presetDeletePopupAnimation.isVisible()
                || infoPopupAnimation.isVisible()
                || missingBaritonePopupAnimation.isVisible()
                || missingUiUtilsPopupAnimation.isVisible()
                || settingsPopupAnimation.isVisible();
    }
    
    private boolean shouldShowExecutionControls() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null;
    }

    private boolean handleNodeDoubleClickExecution(Node clickedNode) {
        if (clickedNode == null || this.client == null || this.client.player == null || this.client.world == null) {
            return false;
        }

        boolean started = ExecutionManager.getInstance().executeFromNode(
            clickedNode,
            nodeGraph.getNodes(),
            nodeGraph.getConnections(),
            activePresetName
        );
        if (!started) {
            return false;
        }

        presetDropdownOpen = false;
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        this.client.setScreen(null);
        return true;
    }

    private void recoverStaleLeftMouseDrag(int mouseX, int mouseY) {
        MinecraftClient client = this.client != null ? this.client : MinecraftClient.getInstance();
        if (InputCompatibilityBridge.isMouseButtonPressed(client, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            return;
        }

        boolean recoveringWorkspaceDrag = false;
        Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
        if (selectedNodes != null) {
            for (Node selected : selectedNodes) {
                if (selected != null && selected.isDragging()) {
                    recoveringWorkspaceDrag = true;
                    break;
                }
            }
        }

        boolean staleState = isDraggingFromSidebar
            || nodeGraph.isSelectionBoxActive()
            || nodeGraph.isAnyNodeBeingDragged()
            || recoveringWorkspaceDrag;
        if (!staleState) {
            return;
        }

        if (nodeGraph.isSelectionBoxActive()) {
            nodeGraph.completeSelectionBox();
        }

        if (isDraggingFromSidebar) {
            if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                int worldMouseX = nodeGraph.screenToWorldX(mouseX);
                int worldMouseY = nodeGraph.screenToWorldY(mouseY);
                Node newNode = draggingSidebarNode != null
                    ? nodeGraph.handleSidebarDrop(draggingSidebarNode, worldMouseX, worldMouseY)
                    : nodeGraph.handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
                if (newNode != null) {
                    nodeGraph.selectNode(newNode);
                }
            }
            isDraggingFromSidebar = false;
            draggingNodeType = null;
            draggingSidebarNode = null;
            nodeGraph.resetDropTargets();
            return;
        }
        nodeGraph.forceClearTransientDragState();
    }

    private void resetOverlayCutout() {
        overlayCutoutActive = false;
    }

    private void setOverlayCutout(int x, int y, int width, int height) {
        overlayCutoutActive = true;
        overlayCutoutX = x;
        overlayCutoutY = y;
        overlayCutoutWidth = width;
        overlayCutoutHeight = height;
    }

    private boolean isScreenPopupVisible() {
        return clearPopupAnimation.isVisible()
            || importExportPopupAnimation.isVisible()
            || createPresetPopupAnimation.isVisible()
            || renamePresetPopupAnimation.isVisible()
            || presetDeletePopupAnimation.isVisible()
            || infoPopupAnimation.isVisible()
            || missingBaritonePopupAnimation.isVisible()
            || missingUiUtilsPopupAnimation.isVisible()
            || settingsPopupAnimation.isVisible();
    }

    private int getActivePopupOverlayColor() {
        if (clearPopupAnimation.isVisible()) {
            return clearPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (importExportPopupAnimation.isVisible()) {
            return importExportPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (createPresetPopupAnimation.isVisible()) {
            return createPresetPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (renamePresetPopupAnimation.isVisible()) {
            return renamePresetPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (presetDeletePopupAnimation.isVisible()) {
            return presetDeletePopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (infoPopupAnimation.isVisible()) {
            return infoPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return missingBaritonePopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return missingUiUtilsPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (settingsPopupAnimation.isVisible()) {
            return settingsPopupAnimation.getAnimatedBackgroundColor(UITheme.OVERLAY_BACKGROUND);
        }
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            return parameterOverlay.getScrimColor();
        }
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            return bookTextEditorOverlay.getScrimColor();
        }
        return UITheme.OVERLAY_BACKGROUND;
    }

    private void renderPopupScrimOverlay(DrawContext context) {
        if (!isPopupObscuringWorkspace()) {
            return;
        }
        int color = getActivePopupOverlayColor();
        if (!overlayCutoutActive || overlayCutoutWidth <= 0 || overlayCutoutHeight <= 0) {
            DrawContextBridge.fillOverlay(context, 0, 0, this.width, this.height, color);
            return;
        }
        int cutoutRight = overlayCutoutX + overlayCutoutWidth;
        int cutoutBottom = overlayCutoutY + overlayCutoutHeight;
        if (overlayCutoutY > 0) {
            DrawContextBridge.fillOverlay(context, 0, 0, this.width, overlayCutoutY, color);
        }
        if (overlayCutoutX > 0) {
            DrawContextBridge.fillOverlay(context, 0, overlayCutoutY, overlayCutoutX, cutoutBottom, color);
        }
        if (cutoutRight < this.width) {
            DrawContextBridge.fillOverlay(context, cutoutRight, overlayCutoutY, this.width, cutoutBottom, color);
        }
        if (cutoutBottom < this.height) {
            DrawContextBridge.fillOverlay(context, 0, cutoutBottom, this.width, this.height, color);
        }
    }

    private void setOverlayCutoutForNodeOverlay() {
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            int[] bounds = parameterOverlay.getScaledPopupBounds();
            if (bounds[2] > 0 && bounds[3] > 0) {
                setOverlayCutout(bounds[0], bounds[1], bounds[2], bounds[3]);
            }
            return;
        }
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            int[] bounds = bookTextEditorOverlay.getScaledPopupBounds();
            if (bounds[2] > 0 && bounds[3] > 0) {
                setOverlayCutout(bounds[0], bounds[1], bounds[2], bounds[3]);
            }
        }
    }

    private boolean shouldBlockBaritoneNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.requiresBaritone()) {
            return false;
        }
        if (baritoneAvailable) {
            return false;
        }
        missingBaritonePopupAnimation.show();
        return true;
    }

    private void refreshMissingBaritonePopup() {
        if (!baritoneAvailable && nodeGraph.containsBaritoneNodes()) { missingBaritonePopupAnimation.show(); } else { missingBaritonePopupAnimation.hide(); }
    }

    private boolean shouldBlockUiUtilsNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.requiresUiUtils()) {
            return false;
        }
        if (uiUtilsAvailable) {
            return false;
        }
        missingUiUtilsPopupAnimation.show();
        return true;
    }

    private void refreshMissingUiUtilsPopup() {
        if (!uiUtilsAvailable && nodeGraph.containsUiUtilsNodes()) { missingUiUtilsPopupAnimation.show(); } else { missingUiUtilsPopupAnimation.hide(); }
    }
    
    private void renderDraggingNode(DrawContext context, int mouseX, int mouseY) {
        if (draggingNodeType == null && draggingSidebarNode == null) return;

        float scale = nodeGraph.getZoomScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        // Create a temporary node for rendering
        Node tempNode = draggingSidebarNode != null ? draggingSidebarNode : new Node(draggingNodeType, 0, 0);
        tempNode.setDragging(true);

        int width = tempNode.getWidth();
        int height = tempNode.getHeight();

        var matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, scale, scale);

        // Convert screen space mouse to scaled space so preview matches workspace zoom.
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);
        int x = scaledMouseX - width / 2;
        int y = scaledMouseY - height / 2;

        // Update temp node position for rendering
        tempNode.setPosition(x, y);

        // Render the node with a slight transparency
        int alpha = 0x80;
        NodeType renderType = tempNode.getType();
        int nodeColor = (renderType.getColor() & 0x00FFFFFF) | alpha;

        // Node background with transparency
        context.fill(x, y, x + width, y + height, UITheme.DRAG_PREVIEW_BG);
        // Draw grey outline for dragging state
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, UITheme.DRAG_PREVIEW_BORDER);

        // Node header
        if (renderType != NodeType.START && renderType != NodeType.EVENT_FUNCTION) {
            context.fill(x + 1, y + 1, x + width - 1, y + 14, nodeColor);
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(renderType == NodeType.TEMPLATE ? tempNode.getTemplateName() : renderType.getDisplayName()),
                x + 4,
                y + 4,
                UITheme.TEXT_HEADER
            );
        }

        MatrixStackBridge.pop(matrices);
    }

    private void renderZoomControls(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonY = getZoomButtonY();
        NodeGraph.ZoomLevel level = nodeGraph.getZoomLevel();
        boolean minusActive = level != NodeGraph.ZoomLevel.FOCUSED;
        boolean plusActive = level == NodeGraph.ZoomLevel.FOCUSED;
        drawZoomButton(context, getZoomMinusButtonX(), buttonY, mouseX, mouseY, disabled, true, minusActive);
        drawZoomButton(context, getZoomPlusButtonX(), buttonY, mouseX, mouseY, disabled, false, plusActive);
    }

    private void drawZoomButton(DrawContext context, int x, int y, int mouseX, int mouseY, boolean disabled, boolean isMinus, boolean active) {
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
        String hoverKey = isMinus ? "zoom-minus-button" : "zoom-plus-button";
        drawToolbarButtonFrame(context, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE, hovered, active, disabled, hoverKey);

        int iconColor = UITheme.TEXT_PRIMARY;
        if (disabled) {
            iconColor = UITheme.DROPDOWN_ACTION_DISABLED;
        } else if (hovered) {
            iconColor = getAccentColor();
        }

        Text iconText = Text.literal(isMinus ? "-" : "+");
        int iconWidth = this.textRenderer.getWidth(iconText);
        int iconX = x + (ZOOM_BUTTON_SIZE - iconWidth) / 2 + 1;
        int iconY = y + (ZOOM_BUTTON_SIZE - this.textRenderer.fontHeight) / 2 + 2;
        context.drawTextWithShadow(this.textRenderer, iconText, iconX, iconY, iconColor);
    }
    
    private void renderNodeGraph(DrawContext context, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        if (!onlyDragged) {
            // Node graph background
            context.fill(Sidebar.getCollapsedWidth(), TITLE_BAR_HEIGHT, this.width, this.height, UITheme.BACKGROUND_PRIMARY);
            
            // Render grid pattern for better visual organization
            if (showGrid) {
                renderGrid(context);
            }
        }
        
        // Render nodes
        nodeGraph.render(context, this.textRenderer, mouseX, mouseY, delta, onlyDragged);
    }

    private void renderDraggedWorkspaceLayer(DrawContext context, int mouseX, int mouseY, float delta) {
        renderNodeGraph(context, mouseX, mouseY, delta, true);
        nodeGraph.renderSelectionBox(context);
    }
    
    private void renderGrid(DrawContext context) {
        int gridSize = 20;
        int startX = Sidebar.getCollapsedWidth();
        int startY = TITLE_BAR_HEIGHT;
        int endX = this.width;
        int endY = this.height;

        int leftWorld = nodeGraph.screenToWorldX(startX);
        int rightWorld = nodeGraph.screenToWorldX(endX);
        if (rightWorld < leftWorld) {
            int swap = leftWorld;
            leftWorld = rightWorld;
            rightWorld = swap;
        }

        int topWorld = nodeGraph.screenToWorldY(startY);
        int bottomWorld = nodeGraph.screenToWorldY(endY);
        if (bottomWorld < topWorld) {
            int swap = topWorld;
            topWorld = bottomWorld;
            bottomWorld = swap;
        }

        int firstVertical = leftWorld - Math.floorMod(leftWorld, gridSize);
        for (int worldX = firstVertical; worldX <= rightWorld + gridSize; worldX += gridSize) {
            int screenX = nodeGraph.worldToScreenX(worldX);
            if (screenX < startX || screenX > endX) {
                continue;
            }
            context.drawVerticalLine(screenX, startY, endY, UITheme.GRID_LINE);
        }

        int firstHorizontal = topWorld - Math.floorMod(topWorld, gridSize);
        for (int worldY = firstHorizontal; worldY <= bottomWorld + gridSize; worldY += gridSize) {
            int screenY = nodeGraph.worldToScreenY(worldY);
            if (screenY < startY || screenY > endY) {
                continue;
            }
            context.drawHorizontalLine(startX, endX, screenY, UITheme.GRID_LINE);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (missingBaritonePopupAnimation.isVisible()) {
            return handleMissingBaritonePopupClick(mouseX, mouseY, button);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
        }
        if (settingsPopupAnimation.isVisible()) {
            if (handleSettingsPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            if (handleInfoPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null && createPresetField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (handleCreatePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (handleRenamePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            stopInlinePresetRename(true);
        }

        if (presetDeletePopupAnimation.isVisible()) {
            if (handlePresetDeletePopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            if (handleClearPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            if (handleImportExportPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleMouseClick(mouseX, mouseY, button);
            return true;
        }

        if (button == 0 && presetDropdownOpen) {
            if (isPresetBrowserButtonClicked((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                return true;
            }
            if (handlePresetDropdownSelection(mouseX, mouseY)) {
                return true;
            }
            presetDropdownOpen = false;
            return true;
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (handleValidationPanelClick((int) mouseX, (int) mouseY)) {
                return true;
            }
        } else if (button == 0) {
            validationPanelOpen = false;
        }

        if (!isPopupObscuringWorkspace() && button == 0 && shouldShowExecutionControls()) {
            if (isPointInPlayButton((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                startExecutingAllGraphs();
                return true;
            }
            if (isPointInStopButton((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                stopExecutingAllGraphs();
                return true;
            }
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (isPointInZoomMinus((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                nodeGraph.zoomOut(getWorkspaceCenterX(), getWorkspaceCenterY());
                return true;
            }
            if (isPointInZoomPlus((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                nodeGraph.zoomIn(getWorkspaceCenterX(), getWorkspaceCenterY());
                return true;
            }
            if (isValidationButtonClicked((int) mouseX, (int) mouseY, button)) {
                validationPanelOpen = !validationPanelOpen;
                return true;
            }
            if (isSettingsButtonClicked((int) mouseX, (int) mouseY, button)) {
                openSettingsPopup();
                return true;
            }
            if (isMarketplaceButtonClicked((int) mouseX, (int) mouseY, button)) {
                if (this.client != null) {
                    this.client.setScreen(new PathmindMarketplaceScreen(this));
                }
                return true;
            }
            if (isPublishButtonClicked((int) mouseX, (int) mouseY, button)) {
                openPublishPresetFlow();
                return true;
            }
        }

        if (button == 0) {
            if (isPresetBrowserButtonClicked((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = !presetDropdownOpen;
                return true;
            }
            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                openInfoPopup();
                return true;
            }
        }

        if (button == 0 && handleWorkspaceTabClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (nodeSearchOpen) {
            if (button == 0 && nodeSearchField != null && isPointInNodeSearchField((int) mouseX, (int) mouseY)) {
                int transformedMouseX = toNodeSearchSpaceX((int) mouseX);
                int transformedMouseY = toNodeSearchSpaceY((int) mouseY);
                nodeSearchField.mouseClicked(transformedMouseX, transformedMouseY, button);
                return true;
            }
            if (button == 0) {
                int resultIndex = getNodeSearchResultIndexAt((int) mouseX, (int) mouseY);
                if (resultIndex >= 0 && resultIndex < nodeSearchResults.size()) {
                    selectNodeSearchResult(nodeSearchResults.get(resultIndex));
                    return true;
                }
            }
            if (!isPointInNodeSearchBounds((int) mouseX, (int) mouseY)) {
                closeNodeSearch();
            }
            return true;
        }

        if (nodeGraph.isNodeContextMenuOpen()) {
            return nodeGraph.handleNodeContextMenuClick((int) mouseX, (int) mouseY);
        }

        // Handle parameter overlay clicks first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 0 && nodeGraph.handleBooleanLiteralDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (nodeGraph.handleParameterDropdownClick(mouseX, mouseY)) {
            return true;
        }
        if (nodeGraph.handleRandomRoundingDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (nodeGraph.handleModeDropdownClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && nodeGraph.handleAmountSignDropdownClick(null, (int)mouseX, (int)mouseY)) {
            return true;
        }
        if (button == 0 && nodeGraph.handleOperatorToggleClick(textRenderer, (int)mouseX, (int)mouseY)) {
            return true;
        }

        // Check if clicking home button
        if (isHomeButtonClicked((int)mouseX, (int)mouseY, button)) {
            nodeGraph.resetCamera();
            return true;
        }

        if (isClearButtonClicked((int)mouseX, (int)mouseY, button)) {
            openClearPopup();
            return true;
        }

        if (isImportExportButtonClicked((int)mouseX, (int)mouseY, button)) {
            openImportExportPopup();
            return true;
        }

        // Check if clicking in sidebar to add nodes
        if (mouseX < sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            if (sidebar.mouseClicked(mouseX, mouseY, button)) {
                // Check if we should start dragging a node from sidebar
                if (sidebar.isHoveringNode()) {
                    NodeType hoveredType = sidebar.getHoveredNodeType();
                    if (!sidebar.isHoveringCustomNode() && shouldBlockBaritoneNode(hoveredType)) {
                        return true;
                    }
                    if (!sidebar.isHoveringCustomNode() && shouldBlockUiUtilsNode(hoveredType)) {
                        return true;
                    }
                    isDraggingFromSidebar = true;
                    draggingNodeType = hoveredType;
                    draggingSidebarNode = sidebar.createNodeFromSidebar(0, 0);
                    nodeGraph.resetDropTargets();
                    nodeGraph.closeContextMenu();
                }
                return true;
            }
        }
        
        // Check if clicking on nodes in the graph area
        if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            // Check if context menu is open and handle click
            if (nodeGraph.isContextMenuOpen()) {
                ContextMenuSelection selection = nodeGraph.handleContextMenuClick((int)mouseX, (int)mouseY);
                if (selection != null && selection.shouldOpenSearch()) {
                    nodeGraph.closeContextMenu();
                    openNodeSearch((int) mouseX, (int) mouseY);
                } else if (selection != null && selection.getNodeType() != null) {
                    // Create node at the stored right-click position
                    nodeGraph.addNodeFromContextMenu(selection.getNodeType());
                    nodeGraph.closeContextMenu();
                }
                // Menu handled the click (either selected node or closed)
                return true;
            }

            // Handle right-click - track position for context menu
            if (button == 1) {
                rightClickStartX = (int)mouseX;
                rightClickStartY = (int)mouseY;
                rightClickStartTime = System.currentTimeMillis();
                return true;
            }

            // Handle middle-click for panning
            if (button == 2) {
                nodeGraph.startPanning((int)mouseX, (int)mouseY);
                return true;
            }

            if (button == 0 && nodeGraph.handleStartButtonClick((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                if (nodeGraph.didLastStartButtonTriggerExecution()) {
                    dismissParameterOverlay();
                    isDraggingFromSidebar = false;
                    draggingNodeType = null;
                    draggingSidebarNode = null;
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                }
                return true;
            }
            
            return handleNodeGraphClick(mouseX, mouseY, button);
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    
    private boolean handleNodeGraphClick(double mouseX, double mouseY, int button) {
        int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
        int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
        // FIRST check if clicking on ANY socket (before checking node body)
        for (Node node : nodeGraph.getNodes()) {
            if (!node.shouldRenderSockets()) {
                continue;
            }
            // Check input sockets
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    if (button == 0) { // Left click - start dragging connection from input
                        nodeGraph.stopCoordinateEditing(true);
                        nodeGraph.stopAmountEditing(true);
                        nodeGraph.stopStopTargetEditing(true);
                        nodeGraph.stopVariableEditing(true);
                        nodeGraph.stopMessageEditing(true);
                        nodeGraph.stopParameterEditing(true);
                        nodeGraph.stopEventNameEditing(true);
                        nodeGraph.startDraggingConnection(node, i, false, (int)mouseX, (int)mouseY);
                        return true;
                    }
                }
            }

            // Check output sockets
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    if (button == 0) { // Left click - start dragging connection from output
                        nodeGraph.stopCoordinateEditing(true);
                        nodeGraph.stopAmountEditing(true);
                        nodeGraph.stopStopTargetEditing(true);
                        nodeGraph.stopVariableEditing(true);
                        nodeGraph.stopMessageEditing(true);
                        nodeGraph.stopParameterEditing(true);
                        nodeGraph.stopEventNameEditing(true);
                        nodeGraph.startDraggingConnection(node, i, true, (int)mouseX, (int)mouseY);
                        return true;
                    }
                }
            }
        }
        
        // THEN check if clicking on node body
        if (button == 0 && nodeGraph.handleStopTargetFieldClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (button == 0 && nodeGraph.handleVariableFieldClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        Node clickedNode = nodeGraph.getNodeAt((int)mouseX, (int)mouseY);
        
        if (clickedNode != null) {
            // Node body clicked (not socket)
            if (button == 0) { // Left click - select node or start dragging
                if (clickedNode.getType() == NodeType.TEMPLATE
                    && nodeGraph.isPointInsideTemplateEditButton(clickedNode, (int) mouseX, (int) mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    openTemplateWorkspaceTab(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleSchematicDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }
                if (nodeGraph.handleRunPresetDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleMessageButtonClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                int coordinateAxis = nodeGraph.getCoordinateFieldAxisAt(clickedNode, (int)mouseX, (int)mouseY);
                if (coordinateAxis != -1) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startCoordinateEditing(clickedNode, coordinateAxis);
                    return true;
                }

                if (nodeGraph.isPointInsideStopTargetField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startStopTargetEditing(clickedNode);
                    return true;
                }

                if (nodeGraph.isPointInsideVariableField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startVariableEditing(clickedNode);
                    return true;
                }

                if (nodeGraph.handleRandomRoundingToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleRandomRoundingDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleAmountToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (button == 0 && nodeGraph.handleAmountSignDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleDirectionModeTabClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanModeTabClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleModeFieldClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleMessageScopeToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanLiteralDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                if (nodeGraph.isPointInsideAmountField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startAmountEditing(clickedNode);
                    return true;
                }

                int messageIndex = nodeGraph.getMessageFieldIndexAt(clickedNode, (int)mouseX, (int)mouseY);
                if (messageIndex != -1) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startMessageEditing(clickedNode, messageIndex);
                    return true;
                }

                int parameterIndex = nodeGraph.getParameterFieldIndexAt(clickedNode, (int)mouseX, (int)mouseY);
                if (parameterIndex != -1) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startParameterEditing(clickedNode, parameterIndex);
                    return true;
                }

                if (nodeGraph.handleEventNameFieldClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    return true;
                }

                nodeGraph.stopAmountEditing(true);
                nodeGraph.stopCoordinateEditing(true);
                nodeGraph.stopStopTargetEditing(true);
                nodeGraph.stopVariableEditing(true);
                nodeGraph.stopMessageEditing(true);
                nodeGraph.stopParameterEditing(true);
                nodeGraph.stopEventNameEditing(true);

                // Check if clicking on Edit Text button for WRITE_BOOK nodes
                if (clickedNode.hasBookTextInput() && nodeGraph.isPointInsideBookTextButton(clickedNode, (int)mouseX, (int)mouseY)) {
                    openBookTextEditor(clickedNode);
                    return true;
                }

                if (clickedNode.isParameterNode() && nodeGraph.isPointInsidePopupEditButton(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    openParameterOverlay(clickedNode);
                    return true;
                }

                boolean doubleClick = nodeGraph.handleNodeClick(clickedNode, (int)mouseX, (int)mouseY);
                if (doubleClick && handleNodeDoubleClickExecution(clickedNode)) {
                    return true;
                }

                // Check for double-click to open parameter editor
                boolean shouldOpenOverlay = clickedNode.getType() == NodeType.PARAM_INVENTORY_SLOT
                    || clickedNode.getType() == NodeType.PARAM_KEY
                    || clickedNode.getType() == NodeType.PARAM_VILLAGER_TRADE;
                if (shouldOpenOverlay && doubleClick) {
                    openParameterOverlay(clickedNode);
                    return true;
                }
                
                if (InputCompatibilityBridge.hasControlDown()) {
                    // Control-click: toggle node in selection
                    nodeGraph.toggleNodeInSelection(clickedNode);
                } else if (InputCompatibilityBridge.hasShiftDown()) {
                    // Shift-click: add node to selection without removing existing nodes
                    if (!nodeGraph.isNodeSelected(clickedNode)) {
                        nodeGraph.toggleNodeInSelection(clickedNode);
                    }
                } else {
                    // Normal click: select only this node or focus if already selected
                    if (!nodeGraph.isNodeSelected(clickedNode)) {
                        nodeGraph.selectNode(clickedNode);
                    } else {
                        nodeGraph.focusSelectedNode(clickedNode);
                    }
                    nodeGraph.startDragging(clickedNode, (int)mouseX, (int)mouseY);
                }
                return true;
            }
        } else {
            if (button == 0 && nodeGraph.handleRunPresetDropdownClick(null, (int)mouseX, (int)mouseY)) {
                return true;
            }
            if (button == 0 && nodeGraph.handleSchematicDropdownClick(null, (int)mouseX, (int)mouseY)) {
                return true;
            }
            // Check if clicking on a connection to delete it
            var connection = nodeGraph.getConnectionAt((int)mouseX, (int)mouseY);
            if (connection != null && button == 1) {
                nodeGraph.getConnections().remove(connection);
                return true;
            }
            
            // Clicked on empty space - deselect and stop dragging
            nodeGraph.selectNode(null);
            nodeGraph.stopDraggingConnection();
                if (button == 0) {
                    nodeGraph.stopCoordinateEditing(true);
                    nodeGraph.stopAmountEditing(true);
                    nodeGraph.stopStopTargetEditing(true);
                    nodeGraph.stopVariableEditing(true);
                    nodeGraph.stopMessageEditing(true);
                    nodeGraph.stopParameterEditing(true);
                    nodeGraph.stopEventNameEditing(true);
                    nodeGraph.beginSelectionBox((int) mouseX, (int) mouseY);
                }
                return true;
        }

        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            if (nodeDelayDragging) {
                int[] bounds = getSettingsPopupBounds();
                updateNodeDelayFromMouse((int) mouseX, bounds[0], bounds[2]);
            }
            if (createListRadiusDragging) {
                int[] bounds = getSettingsPopupBounds();
                updateCreateListRadiusFromMouse(getEffectiveSettingsTargetNode(), (int) mouseX, bounds[0], bounds[2]);
            }
            return true;
        }
        if (createPresetPopupAnimation.isVisible()) {
            return true;
        }

        if (isInlinePresetRenameActive()) {
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.updateSelectionBox((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && pendingPresetTabInteractionName != null) {
            updatePendingPresetTabInteraction((int) mouseX, (int) mouseY);
            if (draggingPresetTabName != null) {
                return true;
            }
        }
        if (draggingPresetTabName != null) {
            updatePresetTabDrag((int) mouseX);
            return true;
        }

        if (button == 1 && rightClickStartX != -1 && !nodeGraph.isPanning()) {
            int dragDeltaX = Math.abs((int) mouseX - rightClickStartX);
            int dragDeltaY = Math.abs((int) mouseY - rightClickStartY);
            if (dragDeltaX > CLICK_THRESHOLD || dragDeltaY > CLICK_THRESHOLD) {
                nodeGraph.startPanning(rightClickStartX, rightClickStartY);
                rightClickStartX = -1;
                rightClickStartY = -1;
            }
        }

        // Handle dragging from sidebar
        if (isDraggingFromSidebar && button == 0) {
            if ((draggingNodeType != null || draggingSidebarNode != null) && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                if (draggingSidebarNode != null) {
                    nodeGraph.previewSidebarDrag(draggingSidebarNode, worldMouseX, worldMouseY);
                } else {
                    nodeGraph.previewSidebarDrag(draggingNodeType, worldMouseX, worldMouseY);
                }
            } else {
                nodeGraph.resetDropTargets();
            }
            return true; // Continue dragging
        }
        
        // Handle node dragging and connection dragging
        if (button == 0) {
            nodeGraph.updateDrag((int)mouseX, (int)mouseY);
            updateSelectionDeletionPreviewState();
            return true;
        }
        
        // Handle panning with right-click or middle-click
        if ((button == 1 || button == 2) && nodeGraph.isPanning()) {
            nodeGraph.updatePanning((int)mouseX, (int)mouseY);
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            nodeDelayDragging = false;
            createListRadiusDragging = false;
            if (nodeDelayField != null) {
                nodeDelayField.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null) {
                createPresetField.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null) {
                renamePresetField.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.completeSelectionBox();
            return true;
        }

        if (draggingPresetTabName != null) {
            endPresetTabDrag();
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null) {
                inlinePresetRenameField.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (button == 0 && pendingPresetTabInteractionName != null) {
            String presetName = pendingPresetTabInteractionName;
            clearPendingPresetTabInteraction();
            if (!presetName.equals(activePresetName)) {
                switchPreset(presetName);
            }
            return true;
        }

        if (button == 0) {
            // Handle dropping node from sidebar
            if (isDraggingFromSidebar) {
                if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                    int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                    int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                    Node newNode = draggingSidebarNode != null
                        ? nodeGraph.handleSidebarDrop(draggingSidebarNode, worldMouseX, worldMouseY)
                        : nodeGraph.handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
                    if (newNode != null) {
                        nodeGraph.selectNode(newNode);
                    }
                }
                // Reset drag state
                isDraggingFromSidebar = false;
                draggingNodeType = null;
                draggingSidebarNode = null;
                nodeGraph.resetDropTargets();
            } else {
                // Check if dragging node into sidebar for deletion (only if actually dragging)
                Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
                if (selectedNodes != null && !selectedNodes.isEmpty()) {
                    List<Node> snapshot = new ArrayList<>(selectedNodes);
                    boolean selectionDragged = false;
                    Node draggedNode = null;
                    boolean selectionOverSidebar = false;
                    for (Node selected : snapshot) {
                        if (selected == null) {
                            continue;
                        }
                        if (selected.isDragging()) {
                            selectionDragged = true;
                            if (draggedNode == null) {
                                draggedNode = selected;
                            }
                        }
                    }
                    if (selectionDragged) {
                        if (snapshot.size() > 1) {
                            selectionOverSidebar = nodeGraph.isSelectionOverSidebar(sidebar.getWidth());
                        } else if (draggedNode != null) {
                            selectionOverSidebar = nodeGraph.isNodeOverSidebar(draggedNode, sidebar.getWidth());
                        }
                    }
                    if (selectionDragged && selectionOverSidebar) {
                        nodeGraph.deleteSelectedNode();
                    }
                } else if (nodeGraph.getSelectedNode() != null && nodeGraph.getSelectedNode().isDragging()) {
                    nodeGraph.deleteNodeIfInSidebar(nodeGraph.getSelectedNode(), (int)mouseX, sidebar.getWidth());
                }
                
                nodeGraph.stopDragging();
                nodeGraph.stopDraggingConnection();
            }
        } else if (button == 1) {
            // Right-click released - check if it's a click or a drag
            if (rightClickStartX != -1) {
                int deltaX = Math.abs((int)mouseX - rightClickStartX);
                int deltaY = Math.abs((int)mouseY - rightClickStartY);
                long deltaTime = System.currentTimeMillis() - rightClickStartTime;

                boolean isClick = deltaX <= CLICK_THRESHOLD &&
                                  deltaY <= CLICK_THRESHOLD &&
                                  deltaTime <= CLICK_TIME_THRESHOLD;

                if (isClick && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                    Node clickedNode = nodeGraph.getNodeAt(rightClickStartX, rightClickStartY);
                    if (clickedNode != null) {
                        nodeGraph.selectNode(clickedNode);
                        nodeGraph.showNodeContextMenu(rightClickStartX, rightClickStartY, clickedNode, width, height);
                    } else {
                        // Show context menu at the right-click position
                        nodeGraph.showContextMenu(rightClickStartX, rightClickStartY, sidebar, width, height);
                    }
                }

                rightClickStartX = -1;
                rightClickStartY = -1;
            }

            // Stop panning
            nodeGraph.stopPanning();
        } else if (button == 2) {
            // Stop panning on middle-click release
            nodeGraph.stopPanning();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nodeSearchOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeNodeSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                NodeSearchResult selected = getSelectedNodeSearchResult();
                if (selected != null) {
                    selectNodeSearchResult(selected);
                } else {
                    closeNodeSearch();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                moveNodeSearchSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                moveNodeSearchSelection(1);
                return true;
            }
            if (nodeSearchField != null && nodeSearchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                missingBaritonePopupAnimation.hide();
            }
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                missingUiUtilsPopupAnimation.hide();
            }
            return true;
        }
        if (nodeGraph.isModeDropdownOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeGraph.closeModeDropdown();
            }
            return true;
        }
        if (nodeGraph.isContextMenuOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeGraph.closeContextMenu();
            }
            return true;
        }
        if (settingsPopupAnimation.isVisible() && nodeDelayField != null && nodeDelayField.isFocused()) {
            if (nodeDelayField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (settingsPopupAnimation.isVisible() && createListRadiusField != null && createListRadiusField.isFocused()) {
            if (createListRadiusField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (settingsPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeSettingsPopup();
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeInfoPopup();
                return true;
            }
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null && createPresetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeCreatePresetPopup();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptCreatePreset();
                return true;
            }

            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRenamePresetPopup();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptRenamePreset();
                return true;
            }

            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopInlinePresetRename(false);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                stopInlinePresetRename(true);
                return true;
            }

            if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            return true;
        }

        if (presetDeletePopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePresetDeletePopup();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmPresetDeletion();
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearPopupAnimation.hide();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmClearWorkspace();
                return true;
            }
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeImportExportPopup();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptImport();
                return true;
            }

            return true;
        }

        if (presetDropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presetDropdownOpen = false;
            return true;
        }

        // Handle parameter overlay key presses first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Handle book text editor overlay key presses
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleKeyInput(keyCode, scanCode, modifiers);
            return true;
        }

        if (nodeGraph.handleStopTargetKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleVariableKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleEventNameKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleParameterKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleMessageKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleAmountKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleCoordinateKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (handleNodeGraphShortcuts(keyCode, modifiers)) {
            return true;
        }

        // Close screen with Escape key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        
        // Delete selected node with Delete key
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (nodeGraph.deleteSelectedNode()) {
                return true;
            }
        }
        
        // Don't handle the opening keybind - let it be ignored
        // This prevents the screen from closing when the same key is pressed
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nodeSearchOpen) {
            if (nodeSearchField != null && nodeSearchField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            if (nodeDelayField != null && nodeDelayField.isFocused() && nodeDelayField.charTyped(chr, modifiers)) {
                return true;
            }
            if (createListRadiusField != null && createListRadiusField.isFocused() && createListRadiusField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null && createPresetField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        // Handle parameter overlay character typing first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.charTyped(chr, modifiers)) {
                return true;
            }
        }

        // Handle book text editor overlay character typing
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleCharInput(chr);
            return true;
        }

        if (nodeGraph.handleStopTargetCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleVariableCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleEventNameCharTyped(chr, modifiers)) {
            return true;
        }

        if (nodeGraph.handleParameterCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleMessageCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleAmountCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleCoordinateCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsPopupAnimation.isVisible()) {
            int popupX = getSettingsPopupX();
            int popupY = getSettingsPopupY();
            int popupHeight = getSettingsPopupHeight();
            int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
            int contentX = popupX + 20;
            int contentPopupY = popupY - settingsPopupScrollOffset;
            if (settingsNodeListView) {
                int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
                int[] listBounds = getSettingsNodeListBounds(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsBodyY);
                if (isPointInRect((int) mouseX, (int) mouseY, listBounds[0], listBounds[1], listBounds[2], listBounds[3])) {
                    List<NodeType> settingsNodes = getSettingsNodeTypes();
                    int visibleRows = Math.max(1, listBounds[3] / SETTINGS_NODE_LIST_ROW_HEIGHT);
                    int maxScroll = Math.max(0, settingsNodes.size() - visibleRows);
                    if (maxScroll > 0 && verticalAmount != 0.0) {
                        settingsNodeListScrollOffset = MathHelper.clamp(
                            settingsNodeListScrollOffset - (int) Math.signum(verticalAmount),
                            0,
                            maxScroll
                        );
                        return true;
                    }
                }
            }
            if (isPointInRect((int) mouseX, (int) mouseY, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3]) && verticalAmount != 0.0) {
                int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
                if (maxScroll > 0) {
                    settingsPopupScrollOffset = MathHelper.clamp(
                        settingsPopupScrollOffset - (int) Math.signum(verticalAmount) * 16,
                        0,
                        maxScroll
                    );
                }
                return true;
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null && createPresetField.mouseScrolled(mouseX, mouseY, 0.0, verticalAmount)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.mouseScrolled(mouseX, mouseY, 0.0, verticalAmount)) {
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleMouseScroll(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (presetDropdownOpen) {
            int dropdownX = getPresetDropdownX();
            int optionStartY = getPresetDropdownY();
            DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
            int dropdownHeight = layout.height;
            if (layout.maxScrollOffset > 0
                && isPointInRect((int) mouseX, (int) mouseY, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, dropdownHeight)) {
                int delta = (int) Math.signum(verticalAmount);
                if (delta != 0) {
                    presetDropdownScrollOffset = MathHelper.clamp(presetDropdownScrollOffset - delta, 0, layout.maxScrollOffset);
                }
            }
            return true;
        }

        if (nodeGraph.handleSchematicDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleRunPresetDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }

        if (nodeGraph.handleParameterDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleRandomRoundingDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleModeDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }

        if (nodeGraph.handleContextMenuScroll((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }

        if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT && verticalAmount != 0.0) {
            nodeGraph.zoomByScroll(verticalAmount, getWorkspaceCenterX(), getWorkspaceCenterY());
            return true;
        }

        // Handle sidebar scrolling
        if (mouseX >= 0 && mouseX <= sidebar.getWidth()) {
            if (sidebar.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private void resetWorkspaceTabsFromCurrentGraph() {
        workspaceTabs.clear();
        workspaceTabs.add(new WorkspaceTab("Main", nodeGraph.exportGraphDataSnapshot(), null, null));
        activeWorkspaceTabIndex = 0;
    }

    private void renderWorkspaceTabs(DrawContext context, int mouseX, int mouseY) {
        tickQueuedPresetDeletionAnimation();
        if (!isPopupObscuringWorkspace() && pendingPresetTabInteractionName != null && draggingPresetTabName == null) {
            updatePendingPresetTabInteraction(mouseX, mouseY);
        }
        if (!isPopupObscuringWorkspace() && draggingPresetTabName != null) {
            updatePresetTabDrag(mouseX);
        }
        List<String> tabs = getRenderedPresetTabs();
        if (tabs.isEmpty()) {
            return;
        }

        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        int[] tabWidths = computePresetTabWidths(tabs, rightLimit - x, PRESET_TAB_ADD_WIDTH);
        int[] tabXs = computePresetTabXs(tabWidths, x);
        int dragIndex = draggingPresetTabName == null ? -1 : tabs.indexOf(draggingPresetTabName);

        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            if (i == dragIndex) {
                continue;
            }
            String label = tabs.get(i);
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                continue;
            }
            int drawX = getAnimatedPresetTabX(label, tabXs[i]);
            drawPresetTab(context, mouseX, mouseY, label, drawX, y, tabWidth, false);
        }

        if (dragIndex >= 0 && dragIndex < tabs.size() && dragIndex < tabWidths.length) {
            String label = tabs.get(dragIndex);
            int tabWidth = tabWidths[dragIndex];
            if (tabWidth > 0) {
                int drawX = draggingPresetTabCurrentX == 0 ? tabXs[dragIndex] : draggingPresetTabCurrentX;
                drawPresetTab(context, mouseX, mouseY, label, drawX, y, tabWidth, true);
            }
        }

        x = getPresetTabStartX();
        for (int width : tabWidths) {
            if (width > 0) {
                x += width + TAB_GAP;
            }
        }
        int addTabX = Math.max(getPresetTabStartX(), x - TAB_GAP);
        presetTabAddButtonFadeAnimation.animateTo(draggingPresetTabName != null ? 0f : 1f, 120, AnimationHelper::easeOutCubic);
        presetTabAddButtonFadeAnimation.tick();
        float plusAlpha = MathHelper.clamp(presetTabAddButtonFadeAnimation.getValue(), 0f, 1f);
        if (addTabX + PRESET_TAB_ADD_WIDTH <= rightLimit) {
            boolean hovered = isPointInRect(mouseX, mouseY, addTabX, y, PRESET_TAB_ADD_WIDTH, TAB_HEIGHT);
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("+"),
                addTabX + PRESET_TAB_ADD_WIDTH / 2,
                y + (TAB_HEIGHT - this.textRenderer.fontHeight) / 2 + 1,
                applyAlpha(hovered ? getAccentColor() : 0xFFB8B8B8, plusAlpha)
            );
        }
        renderInlinePresetRenameField(context, mouseX, mouseY, tabs, tabWidths, tabXs, y, dragIndex);
    }

    private boolean handleWorkspaceTabClick(int mouseX, int mouseY) {
        List<String> tabs = getRenderedPresetTabs();
        if (tabs.isEmpty()) {
            return false;
        }
        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        int[] tabWidths = computePresetTabWidths(tabs, rightLimit - x, PRESET_TAB_ADD_WIDTH);
        int[] tabXs = computePresetTabXs(tabWidths, x);
        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            String label = tabs.get(i);
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                continue;
            }
            if (label.equals(animatingPresetDeletionName)) {
                continue;
            }
            x = tabXs[i];
            if (isPointInRect(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT)) {
                if (!isPresetDeleteDisabled(label)) {
                    int closeLeft = x + tabWidth - PRESET_TAB_TEXT_PADDING - PRESET_TAB_CLOSE_ICON_SIZE;
                    int closeTop = y + (TAB_HEIGHT - PRESET_TAB_CLOSE_ICON_SIZE) / 2;
                    int closeHitboxSize = PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2;
                    if (isPointInRect(
                        mouseX,
                        mouseY,
                        closeLeft - PRESET_TAB_CLOSE_HITBOX_PADDING,
                        closeTop - PRESET_TAB_CLOSE_HITBOX_PADDING,
                        closeHitboxSize,
                        closeHitboxSize
                    )) {
                        openPresetDeletePopup(label);
                        return true;
                    }
                }
                if (!isPresetDeleteDisabled(label) && shouldStartInlinePresetRename(label)) {
                    clearPendingPresetTabInteraction();
                    startInlinePresetRename(label);
                    return true;
                }
                if (!label.equals(activePresetName)) {
                    pendingPresetTabInteractionName = label;
                    pendingPresetTabPressMouseX = mouseX;
                    pendingPresetTabPressMouseY = mouseY;
                    pendingPresetTabPressTabLeft = x;
                } else if (!isPresetDeleteDisabled(label)) {
                    pendingPresetTabInteractionName = label;
                    pendingPresetTabPressMouseX = mouseX;
                    pendingPresetTabPressMouseY = mouseY;
                    pendingPresetTabPressTabLeft = x;
                }
                return true;
            }
        }
        x = getPresetTabStartX();
        for (int width : tabWidths) {
            if (width > 0) {
                x += width + TAB_GAP;
            }
        }
        int addTabX = Math.max(getPresetTabStartX(), x - TAB_GAP);
        if (addTabX + PRESET_TAB_ADD_WIDTH <= rightLimit && isPointInRect(mouseX, mouseY, addTabX, y, PRESET_TAB_ADD_WIDTH, TAB_HEIGHT)) {
            openCreatePresetPopup();
            return true;
        }
        return false;
    }

    private int getPresetTabRightLimit() {
        return Math.max(getPresetTabStartX(), getPresetBrowserButtonX() - 1);
    }

    private void clearPendingPresetTabInteraction() {
        pendingPresetTabInteractionName = null;
        pendingPresetTabPressMouseX = 0;
        pendingPresetTabPressMouseY = 0;
        pendingPresetTabPressTabLeft = 0;
    }

    private void updatePendingPresetTabInteraction(int mouseX, int mouseY) {
        if (pendingPresetTabInteractionName == null || draggingPresetTabName != null) {
            return;
        }
        int dx = Math.abs(mouseX - pendingPresetTabPressMouseX);
        int dy = Math.abs(mouseY - pendingPresetTabPressMouseY);
        if (dx < PRESET_TAB_DRAG_THRESHOLD && dy < PRESET_TAB_DRAG_THRESHOLD) {
            return;
        }
        String presetName = pendingPresetTabInteractionName;
        int tabLeft = pendingPresetTabPressTabLeft;
        clearPendingPresetTabInteraction();
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        beginPresetTabDrag(presetName, mouseX, tabLeft);
    }

    private void beginPresetTabDrag(String presetName, int mouseX, int tabLeft) {
        draggingPresetTabName = presetName;
        draggingPresetTabPointerOffsetX = mouseX - tabLeft;
        draggingPresetTabCurrentX = tabLeft;
    }

    private void normalizePresetTabOrder() {
        String defaultPresetName = PresetManager.getDefaultPresetName();
        if (defaultPresetName == null || defaultPresetName.isEmpty()) {
            return;
        }
        if (presetTabOrder.remove(defaultPresetName)) {
            presetTabOrder.add(0, defaultPresetName);
        }
    }

    private void updatePresetTabDrag(int mouseX) {
        if (draggingPresetTabName == null) {
            return;
        }
        List<String> tabs = getRenderedPresetTabs();
        int currentIndex = tabs.indexOf(draggingPresetTabName);
        if (currentIndex < 0) {
            endPresetTabDrag();
            return;
        }
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        if (currentIndex >= widths.length) {
            return;
        }
        int draggedWidth = widths[currentIndex];
        draggingPresetTabCurrentX = mouseX - draggingPresetTabPointerOffsetX;
        int dragCenter = draggingPresetTabCurrentX + draggedWidth / 2;
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (i == currentIndex) {
                continue;
            }
            int center = xs[i] + widths[i] / 2;
            if (dragCenter > center) {
                targetIndex++;
            }
        }
        int orderIndex = presetTabOrder.indexOf(draggingPresetTabName);
        if (orderIndex < 0) {
            return;
        }
        int clampedTarget = MathHelper.clamp(targetIndex, 1, presetTabOrder.size() - 1);
        if (clampedTarget != orderIndex) {
            presetTabOrder.remove(orderIndex);
            presetTabOrder.add(clampedTarget, draggingPresetTabName);
            normalizePresetTabOrder();
        }
    }

    private void endPresetTabDrag() {
        if (draggingPresetTabName != null && draggingPresetTabCurrentX > 0) {
            presetTabXAnimations
                .computeIfAbsent(draggingPresetTabName, key -> new AnimatedValue(draggingPresetTabCurrentX))
                .setValue(draggingPresetTabCurrentX);
        }
        draggingPresetTabName = null;
        draggingPresetTabPointerOffsetX = 0;
        draggingPresetTabCurrentX = 0;
        clearPendingPresetTabInteraction();
    }

    private int[] computePresetTabXs(int[] widths, int startX) {
        int[] xs = new int[widths.length];
        int x = startX;
        for (int i = 0; i < widths.length; i++) {
            xs[i] = x;
            if (widths[i] > 0) {
                x += widths[i] + TAB_GAP;
            }
        }
        return xs;
    }

    private void drawPresetTab(DrawContext context, int mouseX, int mouseY, String label, int x, int y, int tabWidth, boolean dragging) {
        boolean active = label.equals(activePresetName);
        boolean hovered = isPointInRect(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT);
        int fill = active ? 0xFF3A3A3A : 0xFF2A2A2A;
        int border = active ? getAccentColor() : UITheme.BORDER_DEFAULT;
        if (!active && hovered) {
            fill = 0xFF343434;
            border = UITheme.BORDER_HIGHLIGHT;
        }
        if (dragging) {
            fill = 0xFF3C3C3C;
        }

        float appear = dragging ? 1f : getPresetTabAppearProgress(label);
        int fillColor = applyAlpha(fill, appear);
        int borderColor = applyAlpha(border, appear);
        int textColor = applyAlpha(active ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY, appear);
        if (hovered && !active) {
            textColor = applyAlpha(UITheme.TEXT_PRIMARY, appear);
        }

        context.fill(x, y, x + tabWidth, y + TAB_HEIGHT, fillColor);
        DrawContextBridge.drawBorderInLayer(context, x, y, tabWidth, TAB_HEIGHT, borderColor);
        boolean deletable = !isPresetDeleteDisabled(label);
        int closeSpace = deletable ? (PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2) : 0;
        int textMaxWidth = Math.max(4, tabWidth - PRESET_TAB_TEXT_PADDING * 2 - closeSpace);
        if (!label.equals(inlinePresetRenameName)) {
            String drawLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, label, textMaxWidth);
            context.drawText(this.textRenderer, Text.literal(drawLabel), x + PRESET_TAB_TEXT_PADDING, y + (TAB_HEIGHT - this.textRenderer.fontHeight) / 2 + 1, textColor, false);
        }

        if (deletable) {
            int closeLeft = x + tabWidth - PRESET_TAB_TEXT_PADDING - PRESET_TAB_CLOSE_ICON_SIZE;
            int closeTop = y + (TAB_HEIGHT - PRESET_TAB_CLOSE_ICON_SIZE) / 2;
            int closeHitboxSize = PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2;
            boolean closeHovered = isPointInRect(
                mouseX, mouseY,
                closeLeft - PRESET_TAB_CLOSE_HITBOX_PADDING,
                closeTop - PRESET_TAB_CLOSE_HITBOX_PADDING,
                closeHitboxSize, closeHitboxSize
            );
            int closeColor = closeHovered ? UITheme.STATE_ERROR : 0xFF9A9A9A;
            drawCloseXIcon(context, closeLeft, closeTop, PRESET_TAB_CLOSE_ICON_SIZE, applyAlpha(closeColor, appear));
        }
    }

    private List<String> getRenderedPresetTabs() {
        List<String> tabs = new ArrayList<>();
        for (String name : presetTabOrder) {
            if (availablePresets.contains(name)) {
                tabs.add(name);
            }
        }
        for (String name : availablePresets) {
            if (!tabs.contains(name)) {
                tabs.add(name);
            }
        }
        String defaultPresetName = PresetManager.getDefaultPresetName();
        if (defaultPresetName != null && tabs.remove(defaultPresetName)) {
            tabs.add(0, defaultPresetName);
        }
        return tabs;
    }

    private boolean isInlinePresetRenameActive() {
        return inlinePresetRenameField != null
            && inlinePresetRenameField.isVisible()
            && inlinePresetRenameName != null
            && !inlinePresetRenameName.isEmpty();
    }

    private boolean shouldStartInlinePresetRename(String presetName) {
        long now = System.currentTimeMillis();
        boolean doubleClick = presetName != null && presetName.equals(lastPresetTitleClickName)
            && now - lastPresetTitleClickTime <= PRESET_TAB_RENAME_DOUBLE_CLICK_MS;
        lastPresetTitleClickName = presetName;
        lastPresetTitleClickTime = now;
        return doubleClick;
    }

    private int getPresetTabTextMaxWidth(String label, int tabWidth) {
        boolean deletable = !isPresetDeleteDisabled(label);
        int closeSpace = deletable ? (PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2) : 0;
        return Math.max(4, tabWidth - PRESET_TAB_TEXT_PADDING * 2 - closeSpace);
    }

    private int[] getPresetTabTitleBounds(String label, int x, int y, int tabWidth) {
        int textMaxWidth = getPresetTabTextMaxWidth(label, tabWidth);
        String drawLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, label, textMaxWidth);
        int textX = x + PRESET_TAB_TEXT_PADDING;
        int textY = y + (TAB_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        int textWidth = Math.max(4, this.textRenderer.getWidth(drawLabel));
        return new int[]{textX, textY - 1, Math.min(textWidth, textMaxWidth), this.textRenderer.fontHeight + 2};
    }

    private boolean isPointInPresetTabTitle(int mouseX, int mouseY, String label, int x, int y, int tabWidth) {
        int[] bounds = getPresetTabTitleBounds(label, x, y, tabWidth);
        return isPointInRect(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private void startInlinePresetRename(String presetName) {
        if (presetName == null || presetName.isEmpty() || isPresetDeleteDisabled(presetName) || inlinePresetRenameField == null) {
            return;
        }
        closeCreatePresetPopup();
        closeRenamePresetPopup();
        clearPendingPresetTabInteraction();
        endPresetTabDrag();
        inlinePresetRenameName = presetName;
        inlinePresetRenameField.setText(presetName);
        inlinePresetRenameField.setVisible(true);
        inlinePresetRenameField.setEditable(true);
        inlinePresetRenameField.setFocused(true);
    }

    private boolean renamePresetInternal(String currentName, String desiredName) {
        if (currentName == null || currentName.trim().isEmpty()) {
            return false;
        }
        if (desiredName == null || desiredName.trim().isEmpty()) {
            return false;
        }

        boolean renamingActive = currentName.equalsIgnoreCase(activePresetName);
        if (renamingActive) {
            nodeGraph.save();
        }

        Optional<String> renamedPreset = PresetManager.renamePreset(currentName, desiredName);
        if (renamedPreset.isEmpty()) {
            return false;
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        presetDropdownOpen = false;
        if (renamingActive) {
            updateImportExportPathFromPreset();
        }
        return true;
    }

    private void stopInlinePresetRename(boolean commit) {
        if (!isInlinePresetRenameActive()) {
            return;
        }
        boolean renamed = false;
        if (commit && inlinePresetRenameField != null) {
            renamed = renamePresetInternal(inlinePresetRenameName, inlinePresetRenameField.getText());
        }
        if (commit && !renamed) {
            inlinePresetRenameField.setFocused(true);
            return;
        }
        inlinePresetRenameName = "";
        if (inlinePresetRenameField != null) {
            inlinePresetRenameField.setFocused(false);
            inlinePresetRenameField.setVisible(false);
            inlinePresetRenameField.setEditable(false);
        }
    }

    private void renderInlinePresetRenameField(DrawContext context, int mouseX, int mouseY, List<String> tabs, int[] tabWidths, int[] tabXs, int y, int dragIndex) {
        if (!isInlinePresetRenameActive() || inlinePresetRenameField == null) {
            return;
        }
        for (int i = 0; i < tabs.size() && i < tabWidths.length; i++) {
            if (i == dragIndex) {
                continue;
            }
            String label = tabs.get(i);
            if (!label.equals(inlinePresetRenameName)) {
                continue;
            }
            int tabWidth = tabWidths[i];
            if (tabWidth <= 0) {
                break;
            }
            int drawX = getAnimatedPresetTabX(label, tabXs[i]);
            int[] titleBounds = getPresetTabTitleBounds(label, drawX, y, tabWidth);
            int fieldX = titleBounds[0];
            int fieldWidth = getPresetTabTextMaxWidth(label, tabWidth);
            int fieldHeight = Math.max(this.textRenderer.fontHeight + 2, titleBounds[3]);
            int fieldY = titleBounds[1] + 1;
            inlinePresetRenameField.setVisible(true);
            inlinePresetRenameField.setEditable(true);
            inlinePresetRenameField.setEditableColor(UITheme.TEXT_PRIMARY);
            inlinePresetRenameField.setUneditableColor(UITheme.TEXT_TERTIARY);
            inlinePresetRenameField.setPosition(fieldX, fieldY);
            inlinePresetRenameField.setWidth(fieldWidth);
            inlinePresetRenameField.setHeight(fieldHeight);
            inlinePresetRenameField.render(context, mouseX, mouseY, 0f);
            return;
        }
        stopInlinePresetRename(false);
    }

    private void tickQueuedPresetDeletionAnimation() {
        if (animatingPresetDeletionName == null) {
            return;
        }
        if (System.currentTimeMillis() < animatingPresetDeletionExecuteAtMs) {
            return;
        }
        String presetName = animatingPresetDeletionName;
        animatingPresetDeletionName = null;
        animatingPresetDeletionExecuteAtMs = 0L;
        attemptDeletePresetImmediate(presetName);
    }

    private int getAnimatedPresetTabX(String presetName, int targetX) {
        AnimatedValue animation = presetTabXAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(targetX));
        if (!animation.isAnimating() && Math.abs(animation.getValue() - targetX) < 0.5f) {
            animation.setValue(targetX);
            return targetX;
        }
        animation.animateTo(targetX, 120, AnimationHelper::easeOutCubic);
        animation.tick();
        return Math.round(animation.getValue());
    }

    private float getPresetTabAppearProgress(String presetName) {
        AnimatedValue animation = presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f));
        animation.tick();
        return MathHelper.clamp(animation.getValue(), 0f, 1f);
    }

    private int applyAlpha(int color, float alpha) {
        int targetAlpha = (color >>> 24) & 0xFF;
        int appliedAlpha = MathHelper.clamp(Math.round(targetAlpha * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
        return (color & 0x00FFFFFF) | (appliedAlpha << 24);
    }

    private int getPresetTabStartX() {
        return 6;
    }

    private int[] computePresetTabWidths(int availableWidth, int createTabWidth) {
        return computePresetTabWidths(availablePresets, availableWidth, createTabWidth);
    }

    private int[] computePresetTabWidths(List<String> tabNames, int availableWidth, int createTabWidth) {
        int presetCount = tabNames != null ? tabNames.size() : 0;
        if (presetCount <= 0) {
            return new int[0];
        }

        int available = Math.max(0, availableWidth);
        int gapCount = presetCount; // presets + create tab => presetCount gaps
        int gapSpace = TAB_GAP * gapCount;
        int createWidth = createTabWidth;
        int widthForPresets = Math.max(0, available - gapSpace - createWidth);
        if (widthForPresets <= 0) {
            return new int[presetCount];
        }

        int[] preferred = new int[presetCount];
        int preferredTotal = 0;
        for (int i = 0; i < presetCount; i++) {
            String label = tabNames.get(i);
            boolean deletable = !isPresetDeleteDisabled(label);
            int closeSpace = deletable ? (PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2) : 0;
            int width = this.textRenderer.getWidth(label) + PRESET_TAB_TEXT_PADDING * 2 + closeSpace;
            width = MathHelper.clamp(width, TAB_MIN_WIDTH, TAB_MAX_WIDTH);
            preferred[i] = width;
            preferredTotal += width;
        }

        if (preferredTotal <= widthForPresets) {
            return preferred;
        }

        int minWidth = Math.min(TAB_MIN_WIDTH, Math.max(PRESET_TAB_HARD_MIN_WIDTH, widthForPresets / presetCount));
        int minTotal = minWidth * presetCount;
        int[] result = new int[presetCount];
        if (widthForPresets <= minTotal) {
            int base = Math.max(PRESET_TAB_HARD_MIN_WIDTH, widthForPresets / presetCount);
            int remainder = Math.max(0, widthForPresets - base * presetCount);
            for (int i = 0; i < presetCount; i++) {
                result[i] = base + (i < remainder ? 1 : 0);
            }
            return result;
        }

        int reducibleTotal = 0;
        for (int width : preferred) {
            reducibleTotal += Math.max(0, width - minWidth);
        }
        int reductionNeeded = preferredTotal - widthForPresets;
        int assigned = 0;
        for (int i = 0; i < presetCount; i++) {
            int reducible = Math.max(0, preferred[i] - minWidth);
            int reduction = reducibleTotal > 0 ? (reductionNeeded * reducible) / reducibleTotal : 0;
            result[i] = preferred[i] - reduction;
            if (result[i] < minWidth) {
                result[i] = minWidth;
            }
            assigned += result[i];
        }

        int diff = widthForPresets - assigned;
        for (int i = 0; diff != 0 && i < presetCount; i++) {
            if (diff > 0) {
                result[i]++;
                diff--;
            } else if (result[i] > minWidth) {
                result[i]--;
                diff++;
            }
        }
        return result;
    }

    private void openTemplateWorkspaceTab(Node templateNode) {
        if (templateNode == null || templateNode.getType() != NodeType.TEMPLATE) {
            return;
        }
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();

        int currentTab = activeWorkspaceTabIndex;
        String nodeId = templateNode.getId();
        for (int i = 0; i < workspaceTabs.size(); i++) {
            WorkspaceTab existing = workspaceTabs.get(i);
            if (existing.parentTabIndex != null && existing.parentTabIndex == currentTab
                && nodeId.equals(existing.hostTemplateNodeId)) {
                switchToWorkspaceTab(i);
                return;
            }
        }

        NodeGraphData source = templateNode.getTemplateGraphData();
        if (source == null || source.getNodes() == null || source.getNodes().isEmpty()) {
            source = createDefaultTemplateGraphData();
            templateNode.setTemplateGraphData(source);
            nodeGraph.markWorkspaceDirty();
        }
        String label = templateNode.getTemplateName();
        WorkspaceTab newTab = new WorkspaceTab(label, source, currentTab, nodeId);
        workspaceTabs.add(newTab);
        switchToWorkspaceTab(workspaceTabs.size() - 1);
    }

    private void switchToWorkspaceTab(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= workspaceTabs.size() || targetIndex == activeWorkspaceTabIndex) {
            return;
        }
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();

        WorkspaceTab target = workspaceTabs.get(targetIndex);
        if (target == null) {
            return;
        }
        NodeGraphData data = target.graphData != null ? target.graphData : createDefaultTemplateGraphData();
        nodeGraph.applyGraphDataSnapshot(data, false);
        activeWorkspaceTabIndex = targetIndex;
    }

    private void persistActiveWorkspaceToTabs() {
        if (workspaceTabs.isEmpty() || activeWorkspaceTabIndex < 0 || activeWorkspaceTabIndex >= workspaceTabs.size()) {
            return;
        }
        WorkspaceTab tab = workspaceTabs.get(activeWorkspaceTabIndex);
        tab.graphData = nodeGraph.exportGraphDataSnapshot();
        if (tab.parentTabIndex != null && tab.parentTabIndex >= 0 && tab.parentTabIndex < workspaceTabs.size()) {
            WorkspaceTab parent = workspaceTabs.get(tab.parentTabIndex);
            if (parent != null && parent.graphData != null && parent.graphData.getNodes() != null) {
                for (NodeGraphData.NodeData nodeData : parent.graphData.getNodes()) {
                    if (nodeData != null && tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.equals(nodeData.getId())) {
                        nodeData.setTemplateGraph(tab.graphData);
                        nodeData.setTemplateName(tab.label);
                        break;
                    }
                }
            }
        } else {
            tab.label = "Main";
        }
    }

    private void syncAllTemplateTabsIntoParents() {
        if (workspaceTabs.isEmpty()) {
            return;
        }
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (i == activeWorkspaceTabIndex) {
                continue;
            }
            WorkspaceTab tab = workspaceTabs.get(i);
            if (tab == null || tab.parentTabIndex == null || tab.graphData == null) {
                continue;
            }
            if (tab.parentTabIndex < 0 || tab.parentTabIndex >= workspaceTabs.size()) {
                continue;
            }
            WorkspaceTab parent = workspaceTabs.get(tab.parentTabIndex);
            if (parent == null || parent.graphData == null || parent.graphData.getNodes() == null) {
                continue;
            }
            for (NodeGraphData.NodeData nodeData : parent.graphData.getNodes()) {
                if (nodeData != null && tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.equals(nodeData.getId())) {
                    nodeData.setTemplateGraph(tab.graphData);
                    nodeData.setTemplateName(tab.label);
                    break;
                }
            }
        }
    }

    private void restoreRootWorkspaceIfNeeded() {
        if (workspaceTabs.isEmpty()) {
            return;
        }
        WorkspaceTab root = workspaceTabs.get(0);
        if (root == null || root.graphData == null) {
            return;
        }
        nodeGraph.applyGraphDataSnapshot(root.graphData, false);
        activeWorkspaceTabIndex = 0;
    }

    private NodeGraphData createDefaultTemplateGraphData() {
        NodeGraphData data = new NodeGraphData();
        NodeGraphData.NodeData start = new NodeGraphData.NodeData();
        start.setId(java.util.UUID.randomUUID().toString());
        start.setType(NodeType.START);
        start.setX(220);
        start.setY(160);
        start.setStartNodeNumber(1);
        data.getNodes().add(start);
        return data;
    }

    private boolean hasSavedOnClose = false;

    private void autoSaveWorkspace() {
        if (hasSavedOnClose) {
            return;
        }

        hasSavedOnClose = true;

        nodeGraph.stopCoordinateEditing(true);
        nodeGraph.stopAmountEditing(true);
        nodeGraph.stopStopTargetEditing(true);
        nodeGraph.stopVariableEditing(true);
        nodeGraph.stopMessageEditing(true);
        nodeGraph.stopParameterEditing(true);
        nodeGraph.stopParameterEditing(true);
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        restoreRootWorkspaceIfNeeded();

        nodeGraph.save();

        PresetManager.setActivePreset(activePresetName);
    }

    @Override
    public void close() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        super.close();
    }

    @Override
    public void removed() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        if (uiUtilsOverlayPrevEnabled != null) {
            UiUtilsProxy.setOverlayEnabled(uiUtilsOverlayPrevEnabled);
            uiUtilsOverlayPrevEnabled = null;
        }
        super.removed();
    }

    private void renderClearConfirmationPopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, clearPopupAnimation.getPopupAlpha());

        int popupWidth = 280;
        int popupHeight = 150;
        int[] bounds = clearPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, clearPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.popup.clearWorkspace.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        drawPopupTextWithEllipsis(
            context,
            Text.translatable("pathmind.popup.clearWorkspace.message").getString(),
            popupX + 20,
            popupY + 48,
            scaledWidth - 40,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.TEXT_SECONDARY)
        );

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int confirmX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean confirmHovered = isPointInRect(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Text.translatable("pathmind.button.cancel"), PopupButtonStyle.DEFAULT, clearPopupAnimation);
        drawPopupButton(context, confirmX, buttonY, buttonWidth, buttonHeight, confirmHovered,
            Text.translatable("pathmind.button.clear"), PopupButtonStyle.PRIMARY, clearPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderImportExportPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, importExportPopupAnimation.getPopupAlpha());

        int popupWidth = 360;
        int popupHeight = 210;
        int[] bounds = importExportPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, importExportPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.popup.importExport.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int infoY = popupY + 44;
        String importInfo = Text.translatable("pathmind.popup.importExport.importInfo").getString();
        drawPopupTextWithEllipsis(context, importInfo, popupX + 20, infoY, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        String exportInfo = Text.translatable("pathmind.popup.importExport.exportInfo").getString();
        drawPopupTextWithEllipsis(context, exportInfo, popupX + 20, infoY + 14, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        Path defaultPath = NodeGraphPersistence.getDefaultSavePath();
        if (defaultPath != null) {
            String defaultLabel = Text.translatable("pathmind.popup.importExport.defaultSave", defaultPath.toString()).getString();
            drawPopupTextWithEllipsis(context, defaultLabel, popupX + 20, infoY + 30, scaledWidth - 40,
                getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_TERTIARY));
        }

        if (!importExportStatus.isEmpty()) {
            int textAreaWidth = scaledWidth - 40;
            drawPopupTextWithEllipsis(context, importExportStatus, popupX + 20, popupY + scaledHeight - 56, textAreaWidth,
                getPopupAnimatedColor(importExportPopupAnimation, importExportStatusColor));
        }

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int importX = popupX + 20;
        int exportX = importX + buttonWidth + 8;
        int cancelX = popupX + scaledWidth - buttonWidth - 20;

        boolean importHovered = isPointInRect(mouseX, mouseY, importX, buttonY, buttonWidth, buttonHeight);
        boolean exportHovered = isPointInRect(mouseX, mouseY, exportX, buttonY, buttonWidth, buttonHeight);
        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, importX, buttonY, buttonWidth, buttonHeight, importHovered,
            Text.translatable("pathmind.button.import"), PopupButtonStyle.PRIMARY, importExportPopupAnimation);
        drawPopupButton(context, exportX, buttonY, buttonWidth, buttonHeight, exportHovered,
            Text.translatable("pathmind.button.export"), PopupButtonStyle.PRIMARY, importExportPopupAnimation);
        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Text.translatable("pathmind.button.close"), PopupButtonStyle.DEFAULT, importExportPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderInfoPopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, infoPopupAnimation.getPopupAlpha());

        int popupWidth = INFO_POPUP_WIDTH;
        int popupHeight = INFO_POPUP_HEIGHT;
        int[] bounds = infoPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, infoPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            INFO_POPUP_TITLE_TEXT,
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int textStartY = popupY + 42;
        int lineSpacing = 12;
        int centerX = popupX + scaledWidth / 2;

        String authorLine = "Created by: " + INFO_POPUP_AUTHOR;
        String targetLine = "Built for Minecraft: " + INFO_POPUP_TARGET_VERSION;
        String currentLine = "Running Minecraft: " + getCurrentMinecraftVersion();
        String buildLine = "Current Build: " + getModVersion();
        String loaderLine = "Fabric Loader: " + getFabricLoaderVersion();

        int maxCenteredWidth = scaledWidth - 40;
        drawPopupCenteredTextWithEllipsis(context, authorLine, centerX, textStartY, maxCenteredWidth, getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        drawPopupCenteredTextWithEllipsis(context, targetLine, centerX, textStartY + lineSpacing, maxCenteredWidth, getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        drawPopupCenteredTextWithEllipsis(context, currentLine, centerX, textStartY + lineSpacing * 2, maxCenteredWidth, getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        drawPopupCenteredTextWithEllipsis(context, buildLine, centerX, textStartY + lineSpacing * 3, maxCenteredWidth, getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        drawPopupCenteredTextWithEllipsis(context, loaderLine, centerX, textStartY + lineSpacing * 4, maxCenteredWidth, getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = popupX + (scaledWidth - buttonWidth) / 2;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        boolean closeHovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(
            context,
            buttonX,
            buttonY,
            buttonWidth,
            buttonHeight,
            closeHovered,
            Text.translatable("pathmind.button.close"),
            PopupButtonStyle.DEFAULT,
            infoPopupAnimation
        );
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderMissingBaritonePopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, missingBaritonePopupAnimation.getPopupAlpha());

        int popupWidth = MISSING_BARITONE_POPUP_WIDTH;
        int popupHeight = MISSING_BARITONE_POPUP_HEIGHT;
        int[] bounds = missingBaritonePopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, missingBaritonePopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        int centerX = popupX + scaledWidth / 2;
        int messageY = popupY + 16;
        int maxCenteredWidth = scaledWidth - 40;
        drawPopupCenteredTextWithEllipsis(context, Text.translatable("pathmind.popup.missingBaritone.title").getString(), centerX, messageY, maxCenteredWidth, getPopupAnimatedColor(missingBaritonePopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, Text.translatable("pathmind.popup.missingBaritone.message").getString(), centerX, messageY + 16, maxCenteredWidth, getPopupAnimatedColor(missingBaritonePopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, BaritoneDependencyChecker.DOWNLOAD_URL, centerX, messageY + 30, maxCenteredWidth, getPopupAnimatedColor(missingBaritonePopupAnimation, UITheme.LINK_COLOR));

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = popupY + scaledHeight - buttonHeight - 10;
        int totalButtonsWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonsStartX = popupX + (scaledWidth - totalButtonsWidth) / 2;
        int openX = buttonsStartX;
        int copyX = openX + buttonWidth + buttonGap;
        int closeX = copyX + buttonWidth + buttonGap;

        boolean openHovered = isPointInRect(mouseX, mouseY, openX, buttonY, buttonWidth, buttonHeight);
        boolean copyHovered = isPointInRect(mouseX, mouseY, copyX, buttonY, buttonWidth, buttonHeight);
        boolean closeHovered = isPointInRect(mouseX, mouseY, closeX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Text.translatable("pathmind.button.openLink"), PopupButtonStyle.PRIMARY, missingBaritonePopupAnimation);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Text.translatable("pathmind.button.copyLink"), PopupButtonStyle.PRIMARY, missingBaritonePopupAnimation);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.translatable("pathmind.button.close"), PopupButtonStyle.DEFAULT, missingBaritonePopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderMissingUiUtilsPopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, missingUiUtilsPopupAnimation.getPopupAlpha());

        int popupWidth = MISSING_UI_UTILS_POPUP_WIDTH;
        int popupHeight = MISSING_UI_UTILS_POPUP_HEIGHT;
        int[] bounds = missingUiUtilsPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, missingUiUtilsPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        int centerX = popupX + scaledWidth / 2;
        int messageY = popupY + 16;
        int maxCenteredWidth = scaledWidth - 40;
        drawPopupCenteredTextWithEllipsis(context, Text.translatable("pathmind.popup.missingUiUtils.title").getString(), centerX, messageY, maxCenteredWidth, getPopupAnimatedColor(missingUiUtilsPopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, Text.translatable("pathmind.popup.missingUiUtils.message").getString(), centerX, messageY + 16, maxCenteredWidth, getPopupAnimatedColor(missingUiUtilsPopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, UI_UTILS_DOWNLOAD_URL, centerX, messageY + 30, maxCenteredWidth, getPopupAnimatedColor(missingUiUtilsPopupAnimation, UITheme.LINK_COLOR));

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = popupY + scaledHeight - buttonHeight - 10;
        int totalButtonsWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonsStartX = popupX + (scaledWidth - totalButtonsWidth) / 2;
        int openX = buttonsStartX;
        int copyX = openX + buttonWidth + buttonGap;
        int closeX = copyX + buttonWidth + buttonGap;

        boolean openHovered = isPointInRect(mouseX, mouseY, openX, buttonY, buttonWidth, buttonHeight);
        boolean copyHovered = isPointInRect(mouseX, mouseY, copyX, buttonY, buttonWidth, buttonHeight);
        boolean closeHovered = isPointInRect(mouseX, mouseY, closeX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Text.translatable("pathmind.button.openLink"), PopupButtonStyle.PRIMARY, missingUiUtilsPopupAnimation);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Text.translatable("pathmind.button.copyLink"), PopupButtonStyle.PRIMARY, missingUiUtilsPopupAnimation);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.translatable("pathmind.button.close"), PopupButtonStyle.DEFAULT, missingUiUtilsPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawTitle(DrawContext context, int mouseX, int mouseY, float underlineProgress) {
        int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
        int textX = getTitleTextX();
        int centerX = textX + textWidth / 2;
        int textY = getTitleTextY();
        context.drawCenteredTextWithShadow(this.textRenderer, TITLE_TEXT, centerX, textY, UITheme.TEXT_PRIMARY);

        if (underlineProgress > 0.001f) {
            int underlineWidth = Math.round(textWidth * underlineProgress);
            if (underlineWidth > 0) {
                int underlineStartX = centerX - underlineWidth / 2;
                int underlineY = textY + this.textRenderer.fontHeight;
                context.fill(underlineStartX, underlineY, underlineStartX + underlineWidth, underlineY + 1, UITheme.TEXT_PRIMARY);
            }
        }

        renderPresetBrowserButton(context, mouseX, mouseY);
    }

    private void renderPresetBrowserButton(DrawContext context, int mouseX, int mouseY) {
        int buttonX = getPresetBrowserButtonX();
        int buttonY = getPresetBrowserButtonY();
        boolean hovered = isPresetBrowserButtonHovered(mouseX, mouseY);
        boolean active = presetDropdownOpen;
        int iconColor = (hovered || active) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int lineLeft = buttonX + 4;
        int lineRight = buttonX + PRESET_BROWSER_BUTTON_SIZE - 6;
        int dotLeft = buttonX + 3;
        for (int i = 0; i < 3; i++) {
            int y = buttonY + 4 + i * 4;
            context.fill(dotLeft, y, dotLeft + 1, y + 1, iconColor);
            context.fill(lineLeft, y, lineRight, y + 1, iconColor);
        }
    }

    private void drawPopupTextWithEllipsis(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String display = trimWithEllipsis(this.textRenderer, text, maxWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(display), x, y, color);
    }

    private void drawPopupCenteredTextWithEllipsis(DrawContext context, String text, int centerX, int y, int maxWidth, int color) {
        String display = trimWithEllipsis(this.textRenderer, text, maxWidth);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(display), centerX, y, color);
    }

    private String trimWithEllipsis(TextRenderer renderer, String text, int availableWidth) {
        return TextRenderUtil.trimWithEllipsis(renderer, text, availableWidth);
    }

    private boolean enablePopupScissor(DrawContext context, int popupX, int popupY, int scaledWidth, int scaledHeight) {
        int width = Math.max(1, scaledWidth);
        int height = Math.max(1, scaledHeight);
        context.enableScissor(popupX, popupY, popupX + width, popupY + height);
        return true;
    }

    private void disablePopupScissor(DrawContext context, boolean enabled) {
        if (enabled) {
            DrawContextBridge.flush(context);
            DrawContextBridge.flush(context);
            context.disableScissor();
        }
    }

    private boolean handleClearPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = 280;
        int popupHeight = 150;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 10;
        int cancelX = popupX + 20;
        int confirmX = popupX + popupWidth - buttonWidth - 20;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, confirmX, buttonY, buttonWidth, buttonHeight)) {
            confirmClearWorkspace();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            clearPopupAnimation.hide();
            return true;
        }

        return true;
    }

    private boolean handleImportExportPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = 360;
        int popupHeight = 210;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int importX = popupX + 20;
        int exportX = importX + buttonWidth + 8;
        int cancelX = popupX + popupWidth - buttonWidth - 20;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, importX, buttonY, buttonWidth, buttonHeight)) {
            attemptImport();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, exportX, buttonY, buttonWidth, buttonHeight)) {
            attemptExport();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeImportExportPopup();
            return true;
        }

        return true;
    }

    private boolean handleMissingBaritonePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = MISSING_BARITONE_POPUP_WIDTH;
        int popupHeight = MISSING_BARITONE_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = popupY + popupHeight - buttonHeight - 10;
        int totalButtonsWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonsStartX = popupX + (popupWidth - totalButtonsWidth) / 2;
        int openX = buttonsStartX;
        int copyX = openX + buttonWidth + buttonGap;
        int closeX = copyX + buttonWidth + buttonGap;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, openX, buttonY, buttonWidth, buttonHeight)) {
            openBaritoneDownloadLink();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, copyX, buttonY, buttonWidth, buttonHeight)) {
            copyBaritoneDownloadLink();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, closeX, buttonY, buttonWidth, buttonHeight)) {
            missingBaritonePopupAnimation.hide();
            return true;
        }

        return true;
    }

    private boolean handleMissingUiUtilsPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = MISSING_UI_UTILS_POPUP_WIDTH;
        int popupHeight = MISSING_UI_UTILS_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = popupY + popupHeight - buttonHeight - 10;
        int totalButtonsWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonsStartX = popupX + (popupWidth - totalButtonsWidth) / 2;
        int openX = buttonsStartX;
        int copyX = openX + buttonWidth + buttonGap;
        int closeX = copyX + buttonWidth + buttonGap;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, openX, buttonY, buttonWidth, buttonHeight)) {
            openUiUtilsDownloadLink();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, copyX, buttonY, buttonWidth, buttonHeight)) {
            copyUiUtilsDownloadLink();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, closeX, buttonY, buttonWidth, buttonHeight)) {
            missingUiUtilsPopupAnimation.hide();
            return true;
        }

        return true;
    }

    private void openBaritoneDownloadLink() {
        Util.getOperatingSystem().open(BaritoneDependencyChecker.DOWNLOAD_URL);
    }

    private void copyBaritoneDownloadLink() {
        if (this.client != null && this.client.keyboard != null) {
            this.client.keyboard.setClipboard(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    private void openUiUtilsDownloadLink() {
        Util.getOperatingSystem().open(UI_UTILS_DOWNLOAD_URL);
    }

    private void copyUiUtilsDownloadLink() {
        if (this.client != null && this.client.keyboard != null) {
            this.client.keyboard.setClipboard(UI_UTILS_DOWNLOAD_URL);
        }
    }

    private boolean handleInfoPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = INFO_POPUP_WIDTH;
        int popupHeight = INFO_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = popupX + (popupWidth - buttonWidth) / 2;
        int buttonY = popupY + popupHeight - buttonHeight - 16;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeInfoPopup();
            return true;
        }

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            closeInfoPopup();
            return true;
        }

        return true;
    }

    private void drawPopupButton(DrawContext context, int x, int y, int width, int height, boolean hovered, Text label, boolean primary) {
        PopupButtonStyle style = primary ? PopupButtonStyle.PRIMARY : PopupButtonStyle.DEFAULT;
        drawPopupButton(context, x, y, width, height, hovered, label, style);
    }

    private void drawPopupButton(DrawContext context, int x, int y, int width, int height, boolean hovered, Text label, PopupButtonStyle style) {
        drawPopupButton(context, x, y, width, height, hovered, label, style, null);
    }

    private void drawPopupButton(DrawContext context, int x, int y, int width, int height, boolean hovered,
                                 Text label, PopupButtonStyle style, PopupAnimationHandler animation) {
        UIStyleHelper.TextButtonPalette palette = UIStyleHelper.getTextButtonPalette(mapPopupButtonStyle(style), getAccentColor(), hovered, false);
        int adjustedBg = getPopupAnimatedColor(animation, palette.backgroundColor());
        int adjustedBorder = getPopupAnimatedColor(animation, palette.borderColor());
        int adjustedInnerBorder = getPopupAnimatedColor(animation, palette.innerBorderColor());
        int adjustedText = getPopupAnimatedColor(animation, palette.textColor());
        UIStyleHelper.drawBeveledPanel(context, x, y, width, height, adjustedBg, adjustedBorder, adjustedInnerBorder);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            label,
            x + width / 2,
            y + (height - this.textRenderer.fontHeight) / 2 + 1,
            adjustedText
        );
    }

    private UIStyleHelper.TextButtonStyle mapPopupButtonStyle(PopupButtonStyle style) {
        return switch (style) {
            case PRIMARY -> UIStyleHelper.TextButtonStyle.PRIMARY;
            case ACCENT -> UIStyleHelper.TextButtonStyle.ACCENT;
            case DEFAULT -> UIStyleHelper.TextButtonStyle.DEFAULT;
        };
    }

    private void drawPopupContainer(DrawContext context, int x, int y, int width, int height, PopupAnimationHandler animation) {
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            getPopupAnimatedColor(animation, UITheme.BACKGROUND_SECONDARY),
            getPopupAnimatedColor(animation, UITheme.BORDER_DEFAULT),
            getPopupAnimatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );
    }

    private void drawPopupInputFrame(DrawContext context, int x, int y, int width, int height, int borderColor, PopupAnimationHandler animation) {
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            height,
            getPopupAnimatedColor(animation, UITheme.RENAME_INPUT_BG),
            getPopupAnimatedColor(animation, borderColor),
            getPopupAnimatedColor(animation, UITheme.PANEL_INNER_BORDER)
        );
    }

    private int getPopupAnimatedColor(PopupAnimationHandler animation, int baseColor) {
        return animation == null ? baseColor : animation.getAnimatedPopupColor(baseColor);
    }

    private enum PopupButtonStyle {
        DEFAULT,
        PRIMARY,
        ACCENT
    }

    private void openInfoPopup() {
        dismissParameterOverlay();
        clearPopupAnimation.hide();
        importExportPopupAnimation.hide();
        if (createPresetPopupAnimation.isVisible()) {
            closeCreatePresetPopup();
        }
        presetDropdownOpen = false;
        infoPopupAnimation.show();
    }

    private void closeInfoPopup() {
        infoPopupAnimation.hide();
    }

    private void openClearPopup() {
        dismissParameterOverlay();
        closeImportExportPopup();
        if (createPresetPopupAnimation.isVisible()) {
            closeCreatePresetPopup();
        }
        closeInfoPopup();
        closeSettingsPopup();
        presetDropdownOpen = false;
        clearPopupAnimation.show();
    }

    private void confirmClearWorkspace() {
        nodeGraph.clearWorkspace();
        clearPopupAnimation.hide();
    }

    private void openImportExportPopup() {
        dismissParameterOverlay();
        clearPopupAnimation.hide();
        if (createPresetPopupAnimation.isVisible()) {
            closeCreatePresetPopup();
        }
        closeInfoPopup();
        closeSettingsPopup();
        presetDropdownOpen = false;
        importExportPopupAnimation.show();
        clearImportExportStatus();
        if (lastImportExportPath == null) {
            lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
        }
    }

    private void closeImportExportPopup() {
        importExportPopupAnimation.hide();
    }

    private void attemptImport() {
        String defaultPath = lastImportExportPath != null
                ? lastImportExportPath.toString()
                : Optional.ofNullable(NodeGraphPersistence.getDefaultSavePath())
                    .map(Path::toString)
                    .orElse("");

        String selection;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            selection = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Workspace",
                    defaultPath,
                    filters,
                    filters != null ? "JSON Files" : null,
                    false
            );
        }

        if (selection == null) {
            setImportExportStatus("Import cancelled.", UITheme.TEXT_SECONDARY);
            return;
        }

        try {
            Path path = Paths.get(selection.trim());
            lastImportExportPath = path;
            Path fileName = path.getFileName();
            String fileLabel = fileName != null ? fileName.toString() : path.toString();
            Optional<String> importedPreset = PresetManager.importPresetFromFile(path);
            if (importedPreset.isPresent()) {
                switchPreset(importedPreset.get());
                movePresetTabToEnd(importedPreset.get());
                refreshMissingBaritonePopup();
                refreshMissingUiUtilsPopup();
                updateImportExportPathFromPreset();
                setImportExportStatus(
                        "Imported workspace \"" + fileLabel + "\" as preset \"" + importedPreset.get() + "\".",
                        UITheme.STATE_SUCCESS
                );
            } else {
                setImportExportStatus("Failed to import workspace from " + fileLabel + ".", UITheme.STATE_ERROR);
            }
        } catch (InvalidPathException ex) {
            setImportExportStatus("Invalid file path.", UITheme.STATE_ERROR);
        }
    }

    private void attemptExport() {
        Path defaultSavePath = Optional.ofNullable(lastImportExportPath)
                .orElseGet(NodeGraphPersistence::getDefaultSavePath);
        String defaultPathString = defaultSavePath != null ? defaultSavePath.toString() : "workspace.json";

        String selection;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            selection = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Workspace",
                    defaultPathString,
                    filters,
                    filters != null ? "JSON Files" : null
            );
        }

        if (selection == null) {
            setImportExportStatus("Export cancelled.", UITheme.TEXT_SECONDARY);
            return;
        }

        try {
            Path path = Paths.get(selection.trim());
            boolean success = nodeGraph.exportToPath(path);
            if (success) {
                lastImportExportPath = path;
                Path fileName = path.getFileName();
                setImportExportStatus("Exported workspace to " + (fileName != null ? fileName.toString() : path.toString()), UITheme.STATE_SUCCESS);
            } else {
                setImportExportStatus("Failed to export workspace.", UITheme.STATE_ERROR);
            }
        } catch (InvalidPathException ex) {
            setImportExportStatus("Invalid file path.", UITheme.STATE_ERROR);
        }
    }

    private PointerBuffer createJsonFilterPatterns(MemoryStack stack) {
        PointerBuffer filters = stack.mallocPointer(2);
        filters.put(stack.UTF8("*.json"));
        filters.put(stack.UTF8("*.JSON"));
        filters.flip();
        return filters;
    }

    private void setImportExportStatus(String message, int color) {
        importExportStatus = message != null ? message : "";
        importExportStatusColor = color;
    }

    private void clearImportExportStatus() {
        importExportStatus = "";
        importExportStatusColor = UITheme.TEXT_SECONDARY;
    }

    private boolean handleNodeGraphShortcuts(int keyCode, int modifiers) {
        if (!isShortcutModifierDown(modifiers)) {
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_C:
                nodeGraph.copySelectedNodeToClipboard();
                return true;
            case GLFW.GLFW_KEY_V:
                nodeGraph.pasteClipboardNode();
                return true;
            case GLFW.GLFW_KEY_D:
                nodeGraph.duplicateSelectedNode();
                return true;
            case GLFW.GLFW_KEY_Z:
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    nodeGraph.redo();
                } else {
                    nodeGraph.undo();
                }
                return true;
            default:
                return false;
        }
    }

    private boolean isShortcutModifierDown(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private void dismissParameterOverlay() {
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.close();
        }
    }

    private void openParameterOverlay(Node node) {
        if (node == null) {
            return;
        }
        if (node.getType() != NodeType.PARAM_INVENTORY_SLOT
            && node.getType() != NodeType.PARAM_KEY
            && node.getType() != NodeType.PARAM_VILLAGER_TRADE) {
            return;
        }
        nodeGraph.stopCoordinateEditing(true);
        nodeGraph.stopAmountEditing(true);
        nodeGraph.stopStopTargetEditing(true);
        nodeGraph.stopVariableEditing(true);
        nodeGraph.stopMessageEditing(true);
        final NodeParameterOverlay[] overlayRef = new NodeParameterOverlay[1];
        overlayRef[0] = new NodeParameterOverlay(
            node,
            this.width,
            this.height,
            TITLE_BAR_HEIGHT,
            () -> {
                if (parameterOverlay == overlayRef[0]) {
                    parameterOverlay = null;
                }
            }, // Clear reference on close
            nodeGraph::notifyNodeParametersChanged
        );
        parameterOverlay = overlayRef[0];
        parameterOverlay.init();
        parameterOverlay.show();
    }

    private void openBookTextEditor(Node node) {
        dismissParameterOverlay();
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.hide();
        }
        final BookTextEditorOverlay[] overlayRef = new BookTextEditorOverlay[1];
        overlayRef[0] = new BookTextEditorOverlay(
            node,
            this.width,
            this.height,
            () -> {
                if (bookTextEditorOverlay == overlayRef[0]) {
                    bookTextEditorOverlay = null;
                }
            },
            nodeGraph::notifyNodeParametersChanged
        );
        bookTextEditorOverlay = overlayRef[0];
        bookTextEditorOverlay.init();
        bookTextEditorOverlay.show();
    }

    private void renderPlayButton(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getPlayButtonX();
        int buttonY = getPlayButtonY();
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean active = executing;

        drawToolbarButtonFrame(context, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, hovered, active, disabled, "play-button");

        int bgColor = executing ? UITheme.TOOLBAR_EXECUTE_BG : UITheme.TOOLBAR_BG;
        if (hovered) {
            bgColor = executing ? UITheme.TOOLBAR_EXECUTE_HOVER : UITheme.TOOLBAR_BG_ACTIVE;
        } else if (disabled && !executing) {
            bgColor = UITheme.TOOLBAR_BG_DISABLED;
        }
        context.fill(buttonX + 1, buttonY + 1, buttonX + PLAY_BUTTON_SIZE - 1, buttonY + PLAY_BUTTON_SIZE - 1, bgColor);
        if (executing) {
            DrawContextBridge.drawBorder(context, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, UITheme.STATE_SUCCESS);
        }

        int iconColor = executing ? UITheme.STATE_SUCCESS : UITheme.TOOLBAR_EXECUTE_ICON;
        if (hovered) {
            iconColor = UITheme.TOOLBAR_EXECUTE_ICON_HOVER;
        } else if (disabled && !executing) {
            iconColor = UITheme.TOOLBAR_EXECUTE_ICON_DISABLED;
        }
        drawPlayIcon(context, buttonX, buttonY, iconColor);
    }

    private void drawPlayIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int triangleSize = Math.max(5, Math.min(PLAY_BUTTON_SIZE - 12, 7));
        int startX = buttonX + (PLAY_BUTTON_SIZE - triangleSize) / 2;
        int startY = buttonY + (PLAY_BUTTON_SIZE - triangleSize) / 2;

        for (int row = 0; row < triangleSize; row++) {
            int lineY = startY + row;
            int lineStartX = Math.max(startX + row / 2, buttonX + 2);
            int lineEndX = Math.min(startX + triangleSize - 1, buttonX + PLAY_BUTTON_SIZE - 3);
            if (lineStartX <= lineEndX && lineY >= buttonY + 2 && lineY <= buttonY + PLAY_BUTTON_SIZE - 3) {
                context.drawHorizontalLine(lineStartX, lineEndX, lineY, color);
            }
        }
    }

    private void renderStopButton(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getStopButtonX();
        int buttonY = getStopButtonY();
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean active = executing;

        drawToolbarButtonFrame(context, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE, hovered, active, disabled, "stop-button");

        int bgColor = executing ? UITheme.TOOLBAR_STOP_BG : UITheme.TOOLBAR_BG;
        if (hovered) {
            bgColor = executing ? UITheme.TOOLBAR_STOP_HOVER : UITheme.TOOLBAR_BG_ACTIVE;
        } else if (disabled && !executing) {
            bgColor = UITheme.TOOLBAR_BG_DISABLED;
        }
        context.fill(buttonX + 1, buttonY + 1, buttonX + STOP_BUTTON_SIZE - 1, buttonY + STOP_BUTTON_SIZE - 1, bgColor);
        if (executing) {
            int borderColor = hovered ? UITheme.TOOLBAR_STOP_BORDER_HOVER : UITheme.TOOLBAR_STOP_BORDER;
            DrawContextBridge.drawBorder(context, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE, borderColor);
        }

        int iconColor = executing ? UITheme.TOOLBAR_STOP_ICON : UITheme.TOOLBAR_STOP_ICON_INACTIVE;
        if (hovered) {
            iconColor = executing ? UITheme.TOOLBAR_STOP_ICON_HOVER : UITheme.STATE_ERROR;
        } else if (disabled && !executing) {
            iconColor = UITheme.TOOLBAR_STOP_ICON_DISABLED;
        }
        drawStopIcon(context, buttonX, buttonY, iconColor);
    }

    private void drawStopIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int squareSize = Math.max(6, STOP_BUTTON_SIZE - 10);
        int left = buttonX + (STOP_BUTTON_SIZE - squareSize) / 2;
        int top = buttonY + (STOP_BUTTON_SIZE - squareSize) / 2;
        context.fill(left, top, left + squareSize, top + squareSize, color);
    }

    private void renderPresetDropdown(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int dropdownX = getPresetDropdownX();
        int dropdownY = getPresetDropdownY();

        if (disabled && presetDropdownOpen) {
            presetDropdownOpen = false;
        }

        // Update dropdown animation
        presetDropdownAnimation.animateTo(presetDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        presetDropdownAnimation.tick();
        float animProgress = AnimationHelper.easeOutQuad(presetDropdownAnimation.getValue());

        // Don't render options if animation is fully closed
        if (animProgress <= 0.001f) {
            return;
        }

        int optionStartY = dropdownY;
        int optionCount = availablePresets.size() + 1;
        DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
        presetDropdownScrollOffset = MathHelper.clamp(presetDropdownScrollOffset, 0, layout.maxScrollOffset);
        int fullOptionsHeight = layout.height;
        int animatedHeight = (int) (fullOptionsHeight * animProgress);

        // Use scissor to clip the dropdown content during animation
        context.enableScissor(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + animatedHeight);

        UIStyleHelper.drawBeveledPanel(
            context,
            dropdownX,
            optionStartY,
            PRESET_DROPDOWN_WIDTH,
            fullOptionsHeight,
            UITheme.BACKGROUND_SECONDARY,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );

        int startIndex = presetDropdownScrollOffset;
        int endIndex = Math.min(optionCount, startIndex + layout.visibleCount);
        for (int index = startIndex; index < endIndex; index++) {
            int optionY = optionStartY + (index - startIndex) * PRESET_OPTION_HEIGHT;
            if (index < availablePresets.size()) {
                String preset = availablePresets.get(index);
                boolean optionHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                int optionColor = optionHovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
                context.fill(dropdownX + 1, optionY + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 1, optionY + PRESET_OPTION_HEIGHT, optionColor);
                int textColor = preset.equals(activePresetName) ? getAccentColor() : UITheme.TEXT_PRIMARY;
                int textX = dropdownX + PRESET_TEXT_LEFT_PADDING;
                int iconSpace = PRESET_DELETE_ICON_SIZE
                        + PRESET_DELETE_ICON_MARGIN
                        + PRESET_TEXT_ICON_GAP
                        + PRESET_RENAME_ICON_SIZE
                        + PRESET_TEXT_ICON_GAP;
                int textMaxWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING - iconSpace;
                String presetLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, preset, textMaxWidth);
                context.drawTextWithShadow(this.textRenderer, Text.literal(presetLabel), textX, optionY + 5, textColor);

                boolean renameDisabled = isPresetRenameDisabled(preset);
                int renameLeft = getPresetRenameIconLeft(dropdownX);
                int renameTop = getPresetRenameIconTop(optionY);
                boolean renameHovered = animProgress >= 1f && !renameDisabled && isPointInPresetRenameIcon(mouseX, mouseY, optionY, dropdownX);
                if (renameHovered) {
                    context.fill(renameLeft - PRESET_RENAME_ICON_HITBOX_PADDING,
                            renameTop - PRESET_RENAME_ICON_HITBOX_PADDING,
                            renameLeft + PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING,
                            renameTop + PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING,
                            UITheme.ICON_HITBOX_HOVER_BG);
                }

                int renameColor;
                if (renameDisabled) {
                    renameColor = UITheme.DROPDOWN_ACTION_DISABLED;
                } else if (renameHovered) {
                    renameColor = getAccentColor();
                } else {
                    renameColor = UITheme.TEXT_SECONDARY;
                }
                drawPencilIcon(context, renameLeft, renameTop, renameColor);

                boolean deleteDisabled = isPresetDeleteDisabled(preset);
                int deleteLeft = getPresetDeleteIconLeft(dropdownX);
                int deleteTop = getPresetDeleteIconTop(optionY);
                boolean deleteHovered = animProgress >= 1f && !deleteDisabled && isPointInPresetDeleteIcon(mouseX, mouseY, optionY, dropdownX);
                if (deleteHovered) {
                    context.fill(deleteLeft - PRESET_DELETE_ICON_HITBOX_PADDING,
                            deleteTop - PRESET_DELETE_ICON_HITBOX_PADDING,
                            deleteLeft + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                            deleteTop + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                            UITheme.ICON_HITBOX_HOVER_BG);
                }

                int deleteColor;
                if (deleteDisabled) {
                    deleteColor = UITheme.DROPDOWN_ACTION_DISABLED;
                } else if (deleteHovered) {
                    deleteColor = getAccentColor();
                } else {
                    deleteColor = UITheme.TEXT_SECONDARY;
                }
                drawTrashIcon(context, deleteLeft, deleteTop, deleteColor);
            } else {
                context.drawHorizontalLine(dropdownX + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 2, optionY, UITheme.BORDER_SUBTLE);
                boolean createHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                int createColor = createHovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
                context.fill(dropdownX + 1, optionY + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 1, optionY + PRESET_OPTION_HEIGHT, createColor);
                int createTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
                String createLabel = TextRenderUtil.trimWithEllipsis(this.textRenderer, "+ Create new preset", createTextWidth);
                context.drawTextWithShadow(this.textRenderer, Text.literal(createLabel), dropdownX + PRESET_TEXT_LEFT_PADDING, optionY + 5, getAccentColor());
            }
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            dropdownX,
            optionStartY,
            PRESET_DROPDOWN_WIDTH,
            fullOptionsHeight,
            optionCount,
            layout.visibleCount,
            presetDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            dropdownX,
            optionStartY,
            PRESET_DROPDOWN_WIDTH,
            fullOptionsHeight,
            UITheme.BORDER_DEFAULT
        );

        DrawContextBridge.flush(context);
        DrawContextBridge.flush(context);
        context.disableScissor();
    }

    private int getPresetDropdownX() {
        int preferredX = getPresetBrowserButtonX() + PRESET_BROWSER_BUTTON_SIZE - PRESET_DROPDOWN_WIDTH;
        return MathHelper.clamp(preferredX, PRESET_DROPDOWN_MARGIN, this.width - PRESET_DROPDOWN_WIDTH - PRESET_DROPDOWN_MARGIN);
    }

    private int getPresetDropdownY() {
        return getPresetBrowserButtonY() + PRESET_BROWSER_BUTTON_SIZE + 2;
    }

    private int getPlayButtonX() {
        return this.width - PLAY_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    private int getPlayButtonY() {
        return TITLE_BAR_HEIGHT + PLAY_BUTTON_MARGIN;
    }

    private int getValidationButtonX() {
        if (shouldShowExecutionControls()) {
            return getPlayButtonX();
        }
        return this.width - VALIDATION_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    private int getValidationButtonY() {
        if (shouldShowExecutionControls()) {
            return getPlayButtonY() + PLAY_BUTTON_SIZE + CONTROL_BUTTON_GAP;
        }
        return TITLE_BAR_HEIGHT + PLAY_BUTTON_MARGIN;
    }

    private int getZoomPlusButtonX() {
        return this.width - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int getZoomMinusButtonX() {
        return getZoomPlusButtonX() - ZOOM_BUTTON_SIZE - ZOOM_BUTTON_SPACING;
    }

    private int getZoomButtonY() {
        return this.height - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int getWorkspaceCenterX() {
        int workspaceLeft = Sidebar.getCollapsedWidth();
        return workspaceLeft + (this.width - workspaceLeft) / 2;
    }

    private int getWorkspaceCenterY() {
        int workspaceTop = TITLE_BAR_HEIGHT;
        return workspaceTop + (this.height - workspaceTop) / 2;
    }

    private int getStopButtonX() {
        return getPlayButtonX() - CONTROL_BUTTON_GAP - STOP_BUTTON_SIZE;
    }

    private int getStopButtonY() {
        return getPlayButtonY();
    }

    private DropdownLayoutHelper.Layout getPresetDropdownLayout(int optionStartY) {
        int optionCount = availablePresets.size() + 1;
        int visibleCount = Math.min(optionCount, 10);
        return DropdownLayoutHelper.calculate(optionCount, PRESET_OPTION_HEIGHT, visibleCount, optionStartY, this.height);
    }

    private int getPresetDropdownOptionsHeight() {
        int optionStartY = getPresetDropdownY();
        return getPresetDropdownLayout(optionStartY).height;
    }

    private int getPresetDeleteIconLeft(int dropdownX) {
        return dropdownX + PRESET_DROPDOWN_WIDTH - PRESET_DELETE_ICON_MARGIN - PRESET_DELETE_ICON_SIZE;
    }

    private int getPresetDeleteIconTop(int optionTop) {
        return optionTop + (PRESET_OPTION_HEIGHT - PRESET_DELETE_ICON_SIZE) / 2;
    }

    private int getPresetRenameIconLeft(int dropdownX) {
        return getPresetDeleteIconLeft(dropdownX) - PRESET_TEXT_ICON_GAP - PRESET_RENAME_ICON_SIZE;
    }

    private int getPresetRenameIconTop(int optionTop) {
        return optionTop + (PRESET_OPTION_HEIGHT - PRESET_RENAME_ICON_SIZE) / 2;
    }

    private boolean isPointInPresetDeleteIcon(int mouseX, int mouseY, int optionTop, int dropdownX) {
        int iconLeft = getPresetDeleteIconLeft(dropdownX);
        int iconTop = getPresetDeleteIconTop(optionTop);
        int hitboxSize = PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING * 2;
        return isPointInRect(mouseX, mouseY, iconLeft - PRESET_DELETE_ICON_HITBOX_PADDING, iconTop - PRESET_DELETE_ICON_HITBOX_PADDING, hitboxSize, hitboxSize);
    }

    private boolean isPointInPresetRenameIcon(int mouseX, int mouseY, int optionTop, int dropdownX) {
        int iconLeft = getPresetRenameIconLeft(dropdownX);
        int iconTop = getPresetRenameIconTop(optionTop);
        int hitboxSize = PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING * 2;
        return isPointInRect(mouseX, mouseY, iconLeft - PRESET_RENAME_ICON_HITBOX_PADDING, iconTop - PRESET_RENAME_ICON_HITBOX_PADDING, hitboxSize, hitboxSize);
    }

    private boolean isPresetDeleteDisabled(String presetName) {
        if (presetName == null) {
            return true;
        }
        return presetName.equalsIgnoreCase(PresetManager.getDefaultPresetName());
    }

    private boolean isPresetRenameDisabled(String presetName) {
        return isPresetDeleteDisabled(presetName);
    }

    private boolean handlePresetDropdownSelection(double mouseX, double mouseY) {
        int dropdownX = getPresetDropdownX();
        int optionStartY = getPresetDropdownY();
        DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
        presetDropdownScrollOffset = MathHelper.clamp(presetDropdownScrollOffset, 0, layout.maxScrollOffset);
        int optionsHeight = layout.height;
        if (!isPointInRect((int) mouseX, (int) mouseY, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, optionsHeight)) {
            return false;
        }

        int relativeY = (int) mouseY - optionStartY;
        int index = presetDropdownScrollOffset + (relativeY / PRESET_OPTION_HEIGHT);
        if (index < availablePresets.size()) {
            if (index >= 0) {
                String selectedPreset = availablePresets.get(index);
                int optionTop = optionStartY + (index - presetDropdownScrollOffset) * PRESET_OPTION_HEIGHT;
                if (isPointInPresetRenameIcon((int) mouseX, (int) mouseY, optionTop, dropdownX)) {
                    if (!isPresetRenameDisabled(selectedPreset)) {
                        openRenamePresetPopup(selectedPreset);
                    }
                    return true;
                }
                if (isPointInPresetDeleteIcon((int) mouseX, (int) mouseY, optionTop, dropdownX)) {
                    if (!isPresetDeleteDisabled(selectedPreset)) {
                        openPresetDeletePopup(selectedPreset);
                    }
                    return true;
                }
                presetDropdownOpen = false;
                if (!selectedPreset.equals(activePresetName)) {
                    switchPreset(selectedPreset);
                }
                return true;
            }
        } else if (index == availablePresets.size()) {
            presetDropdownOpen = false;
            openCreatePresetPopup();
            return true;
        }

        presetDropdownOpen = false;
        return true;
    }

    private void openCreatePresetPopup() {
        presetDropdownOpen = false;
        clearCreatePresetStatus();
        closeInfoPopup();
        stopInlinePresetRename(false);
        closeRenamePresetPopup();
        createPresetPopupAnimation.show();
        if (createPresetField != null) {
            createPresetField.setText("");
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            createPresetField.setFocused(true);
        }
    }

    private void closeCreatePresetPopup() {
        createPresetPopupAnimation.hide();
        clearCreatePresetStatus();
        if (createPresetField != null) {
            createPresetField.setFocused(false);
            createPresetField.setVisible(false);
            createPresetField.setEditable(false);
        }
    }

    private void openRenamePresetPopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        presetDropdownOpen = false;
        clearRenamePresetStatus();
        closeInfoPopup();
        stopInlinePresetRename(false);
        closeCreatePresetPopup();
        pendingPresetRenameName = presetName;
        renamePresetPopupAnimation.show();
        if (renamePresetField != null) {
            renamePresetField.setText(presetName);
            renamePresetField.setVisible(true);
            renamePresetField.setEditable(true);
            renamePresetField.setFocused(true);
        }
    }

    private void closeRenamePresetPopup() {
        renamePresetPopupAnimation.hide();
        pendingPresetRenameName = "";
        clearRenamePresetStatus();
        if (renamePresetField != null) {
            renamePresetField.setFocused(false);
            renamePresetField.setVisible(false);
            renamePresetField.setEditable(false);
        }
    }

    private void openNodeSearch(int anchorX, int anchorY) {
        int minX = sidebar.getWidth() + 8;
        int maxX = this.width - NODE_SEARCH_FIELD_WIDTH - 8;
        nodeSearchFieldX = MathHelper.clamp(anchorX, minX, Math.max(minX, maxX));
        nodeSearchFieldY = MathHelper.clamp(anchorY, TITLE_BAR_HEIGHT + 8,
            Math.max(TITLE_BAR_HEIGHT + 8, this.height - NODE_SEARCH_FIELD_HEIGHT - 8));
        nodeSearchWorldX = nodeGraph.screenToWorldX(nodeSearchFieldX);
        nodeSearchWorldY = nodeGraph.screenToWorldY(nodeSearchFieldY);
        nodeSearchScale = Math.max(0.05f, nodeGraph.getZoomScale());
        nodeSearchOpen = true;
        if (nodeSearchField != null) {
            nodeSearchField.setX(nodeSearchFieldX);
            nodeSearchField.setY(nodeSearchFieldY);
            nodeSearchField.setText("");
            nodeSearchField.setVisible(true);
            nodeSearchField.setEditable(true);
            nodeSearchField.setFocused(true);
            nodeSearchField.setSuggestion(null);
        }
        nodeSearchResults.clear();
        nodeSearchHoverIndex = -1;
    }

    private void closeNodeSearch() {
        nodeSearchOpen = false;
        nodeSearchResults.clear();
        nodeSearchHoverIndex = -1;
        if (nodeSearchField != null) {
            nodeSearchField.setFocused(false);
            nodeSearchField.setVisible(false);
            nodeSearchField.setEditable(false);
            nodeSearchField.setSuggestion(null);
        }
    }

    private void updateNodeSearchMatch() {
        if (!nodeSearchOpen || nodeSearchField == null) {
            return;
        }
        String query = nodeSearchField.getText();
        nodeSearchField.setSuggestion(null);
        refreshNodeSearchResults(query);
    }

    private void renderNodeSearchField(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!nodeSearchOpen || nodeSearchField == null) {
            return;
        }

        updateNodeSearchLayout();
        int transformedMouseX = toNodeSearchSpaceX(mouseX);
        int transformedMouseY = toNodeSearchSpaceY(mouseY);
        var matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, nodeSearchFieldX, nodeSearchFieldY);
        MatrixStackBridge.scale(matrices, nodeSearchScale, nodeSearchScale);
        MatrixStackBridge.translate(matrices, -nodeSearchFieldX, -nodeSearchFieldY);

        context.fill(nodeSearchFieldX, nodeSearchFieldY, nodeSearchFieldX + NODE_SEARCH_FIELD_WIDTH,
            nodeSearchFieldY + NODE_SEARCH_FIELD_HEIGHT, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, nodeSearchFieldX, nodeSearchFieldY, NODE_SEARCH_FIELD_WIDTH,
            NODE_SEARCH_FIELD_HEIGHT, UITheme.BORDER_SUBTLE);
        int iconX = nodeSearchFieldX + 6;
        int iconY = nodeSearchFieldY + (NODE_SEARCH_FIELD_HEIGHT - 9) / 2;
        drawNodeSearchIcon(context, iconX, iconY, UITheme.TEXT_PRIMARY);
        nodeSearchField.setPosition(nodeSearchFieldX + 20, nodeSearchFieldY + 6);
        nodeSearchField.setWidth(NODE_SEARCH_FIELD_WIDTH - 26);
        nodeSearchField.setHeight(NODE_SEARCH_FIELD_HEIGHT);
        nodeSearchField.render(context, transformedMouseX, transformedMouseY, delta);

        renderNodeSearchDropdown(context, transformedMouseX, transformedMouseY);
        MatrixStackBridge.pop(matrices);
    }

    private void refreshNodeSearchResults(String query) {
        nodeSearchResults.clear();
        nodeSearchHoverIndex = -1;
        if (query == null) {
            return;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return;
        }

        for (NodeType nodeType : NodeType.values()) {
            if (nodeType == null || !nodeType.isDraggableFromSidebar()) {
                continue;
            }
            int score = scoreNodeSearchCandidate(nodeType, normalizedQuery);
            if (score <= 0) {
                continue;
            }
            nodeSearchResults.add(new NodeSearchResult(
                nodeType,
                nodeType.getDisplayName(),
                nodeType.getCategory().name().replace('_', ' '),
                score
            ));
        }

        nodeSearchResults.sort((left, right) -> {
            int scoreCompare = Integer.compare(right.score, left.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.label.compareToIgnoreCase(right.label);
        });
        if (nodeSearchResults.size() > NODE_SEARCH_MAX_RESULTS) {
            nodeSearchResults.subList(NODE_SEARCH_MAX_RESULTS, nodeSearchResults.size()).clear();
        }
        if (!nodeSearchResults.isEmpty()) {
            nodeSearchHoverIndex = 0;
        }
    }

    private int scoreNodeSearchCandidate(NodeType nodeType, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.getDisplayName(), query));
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.getDescription(), query) - 40);
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.getCategory().name().replace('_', ' '), query) - 80);
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.name(), query) - 100);
        return bestScore;
    }

    private int scoreSearchCandidate(String candidate, String query) {
        if (candidate == null || query == null) {
            return 0;
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidate.isEmpty() || query.isEmpty()) {
            return 0;
        }
        if (normalizedCandidate.equals(query)) {
            return 1000;
        }
        if (normalizedCandidate.startsWith(query)) {
            return 800 - Math.max(0, normalizedCandidate.length() - query.length());
        }
        int containsIndex = normalizedCandidate.indexOf(query);
        if (containsIndex >= 0) {
            return 650 - containsIndex * 6;
        }
        int fuzzyScore = fuzzySearchScore(normalizedCandidate, query);
        return fuzzyScore > 0 ? 300 + fuzzyScore : 0;
    }

    private int fuzzySearchScore(String candidate, String query) {
        int score = 0;
        int streak = 0;
        int queryIndex = 0;
        for (int i = 0; i < candidate.length() && queryIndex < query.length(); i++) {
            if (candidate.charAt(i) == query.charAt(queryIndex)) {
                score += 8 + streak * 4;
                streak++;
                queryIndex++;
            } else {
                streak = 0;
            }
        }
        if (queryIndex != query.length()) {
            return 0;
        }
        return Math.max(1, score - Math.max(0, candidate.length() - query.length()));
    }

    private void renderNodeSearchDropdown(DrawContext context, int mouseX, int mouseY) {
        if (nodeSearchResults.isEmpty()) {
            return;
        }
        int listX = nodeSearchFieldX;
        int listY = nodeSearchFieldY + NODE_SEARCH_FIELD_HEIGHT + NODE_SEARCH_DROPDOWN_TOP_GAP;
        int listWidth = NODE_SEARCH_FIELD_WIDTH;
        int listHeight = nodeSearchResults.size() * NODE_SEARCH_RESULT_HEIGHT;
        context.fill(listX, listY, listX + listWidth, listY + listHeight, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, listX, listY, listWidth, listHeight, UITheme.BORDER_DEFAULT);

        int hoveredIndex = getNodeSearchResultIndexAt(mouseX, mouseY);
        if (hoveredIndex >= 0) {
            nodeSearchHoverIndex = hoveredIndex;
        }

        for (int i = 0; i < nodeSearchResults.size(); i++) {
            NodeSearchResult result = nodeSearchResults.get(i);
            int rowTop = listY + i * NODE_SEARCH_RESULT_HEIGHT;
            boolean selected = i == nodeSearchHoverIndex;
            if (selected) {
                context.fill(listX + 1, rowTop, listX + listWidth - 1, rowTop + NODE_SEARCH_RESULT_HEIGHT, UITheme.DROPDOWN_OPTION_HOVER);
            }
            int textY = rowTop + Math.max(0, (NODE_SEARCH_RESULT_HEIGHT - this.textRenderer.fontHeight) / 2);
            String label = trimToWidth(result.label, listWidth - (NODE_SEARCH_RESULT_TEXT_PADDING * 2) - 42);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), listX + NODE_SEARCH_RESULT_TEXT_PADDING, textY, UITheme.TEXT_PRIMARY);
            String category = trimToWidth(result.categoryLabel, 36);
            int categoryWidth = this.textRenderer.getWidth(category);
            context.drawTextWithShadow(this.textRenderer, Text.literal(category),
                listX + listWidth - NODE_SEARCH_RESULT_TEXT_PADDING - categoryWidth, textY, UITheme.TEXT_TERTIARY);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty() || this.textRenderer == null || maxWidth <= 0) {
            return value == null ? "" : value;
        }
        if (this.textRenderer.getWidth(value) <= maxWidth) {
            return value;
        }
        return this.textRenderer.trimToWidth(value, Math.max(0, maxWidth - this.textRenderer.getWidth("..."))) + "...";
    }

    private boolean isPointInNodeSearchField(int mouseX, int mouseY) {
        int transformedMouseX = toNodeSearchSpaceX(mouseX);
        int transformedMouseY = toNodeSearchSpaceY(mouseY);
        return isPointInRect(transformedMouseX, transformedMouseY, nodeSearchFieldX, nodeSearchFieldY, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT);
    }

    private boolean isPointInNodeSearchBounds(int mouseX, int mouseY) {
        int transformedMouseX = toNodeSearchSpaceX(mouseX);
        int transformedMouseY = toNodeSearchSpaceY(mouseY);
        int totalHeight = NODE_SEARCH_FIELD_HEIGHT + getNodeSearchDropdownHeight() + (nodeSearchResults.isEmpty() ? 0 : NODE_SEARCH_DROPDOWN_TOP_GAP);
        return isPointInRect(transformedMouseX, transformedMouseY, nodeSearchFieldX, nodeSearchFieldY, NODE_SEARCH_FIELD_WIDTH, totalHeight);
    }

    private int getNodeSearchDropdownHeight() {
        return nodeSearchResults.isEmpty() ? 0 : nodeSearchResults.size() * NODE_SEARCH_RESULT_HEIGHT;
    }

    private int getNodeSearchResultIndexAt(int mouseX, int mouseY) {
        if (nodeSearchResults.isEmpty()) {
            return -1;
        }
        int transformedMouseX = toNodeSearchSpaceX(mouseX);
        int transformedMouseY = toNodeSearchSpaceY(mouseY);
        int listX = nodeSearchFieldX;
        int listY = nodeSearchFieldY + NODE_SEARCH_FIELD_HEIGHT + NODE_SEARCH_DROPDOWN_TOP_GAP;
        int listHeight = getNodeSearchDropdownHeight();
        if (!isPointInRect(transformedMouseX, transformedMouseY, listX, listY, NODE_SEARCH_FIELD_WIDTH, listHeight)) {
            return -1;
        }
        int index = (transformedMouseY - listY) / NODE_SEARCH_RESULT_HEIGHT;
        return index >= 0 && index < nodeSearchResults.size() ? index : -1;
    }

    private void updateNodeSearchLayout() {
        nodeSearchScale = Math.max(0.05f, nodeGraph.getZoomScale());
        int minX = sidebar.getWidth() + 8;
        int minY = TITLE_BAR_HEIGHT + 8;
        int scaledWidth = Math.max(1, Math.round(NODE_SEARCH_FIELD_WIDTH * nodeSearchScale));
        int scaledHeight = Math.max(1, Math.round(NODE_SEARCH_FIELD_HEIGHT * nodeSearchScale));
        int maxX = Math.max(minX, this.width - scaledWidth - 8);
        int maxY = Math.max(minY, this.height - scaledHeight - 8);
        nodeSearchFieldX = MathHelper.clamp(nodeGraph.worldToScreenX(nodeSearchWorldX), minX, maxX);
        nodeSearchFieldY = MathHelper.clamp(nodeGraph.worldToScreenY(nodeSearchWorldY), minY, maxY);
    }

    private int toNodeSearchSpaceX(int mouseX) {
        if (nodeSearchScale == 0.0f) {
            return mouseX;
        }
        return Math.round(nodeSearchFieldX + (mouseX - nodeSearchFieldX) / nodeSearchScale);
    }

    private int toNodeSearchSpaceY(int mouseY) {
        if (nodeSearchScale == 0.0f) {
            return mouseY;
        }
        return Math.round(nodeSearchFieldY + (mouseY - nodeSearchFieldY) / nodeSearchScale);
    }

    private NodeSearchResult getSelectedNodeSearchResult() {
        if (nodeSearchResults.isEmpty()) {
            return null;
        }
        if (nodeSearchHoverIndex < 0 || nodeSearchHoverIndex >= nodeSearchResults.size()) {
            return nodeSearchResults.get(0);
        }
        return nodeSearchResults.get(nodeSearchHoverIndex);
    }

    private void moveNodeSearchSelection(int direction) {
        if (nodeSearchResults.isEmpty()) {
            nodeSearchHoverIndex = -1;
            return;
        }
        if (nodeSearchHoverIndex < 0 || nodeSearchHoverIndex >= nodeSearchResults.size()) {
            nodeSearchHoverIndex = 0;
            return;
        }
        nodeSearchHoverIndex = MathHelper.clamp(nodeSearchHoverIndex + direction, 0, nodeSearchResults.size() - 1);
    }

    private void selectNodeSearchResult(NodeSearchResult result) {
        if (result == null || result.nodeType == null) {
            return;
        }
        if (shouldBlockBaritoneNode(result.nodeType) || shouldBlockUiUtilsNode(result.nodeType)) {
            return;
        }
        nodeGraph.addNodeFromContextMenu(result.nodeType);
        closeNodeSearch();
    }

    private void drawNodeSearchIcon(DrawContext context, int x, int y, int color) {
        context.drawHorizontalLine(x + 1, x + 3, y, color);
        context.drawHorizontalLine(x, x + 4, y + 1, color);
        context.drawVerticalLine(x, y + 2, y + 4, color);
        context.drawVerticalLine(x + 4, y + 2, y + 4, color);
        context.drawHorizontalLine(x + 1, x + 3, y + 5, color);
        context.drawHorizontalLine(x + 5, x + 6, y + 5, color);
        context.drawHorizontalLine(x + 6, x + 7, y + 6, color);
        context.drawHorizontalLine(x + 7, x + 8, y + 7, color);
    }

    private boolean handleCreatePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int popupX = (this.width - CREATE_PRESET_POPUP_WIDTH) / 2;
        int popupY = (this.height - CREATE_PRESET_POPUP_HEIGHT) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + CREATE_PRESET_POPUP_HEIGHT - buttonHeight - 16;
        int cancelX = popupX + 20;
        int createX = popupX + CREATE_PRESET_POPUP_WIDTH - buttonWidth - 20;

        if (isPointInRect((int) mouseX, (int) mouseY, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeCreatePresetPopup();
            return true;
        }

        if (isPointInRect((int) mouseX, (int) mouseY, createX, buttonY, buttonWidth, buttonHeight)) {
            attemptCreatePreset();
            return true;
        }

        return false;
    }

    private boolean handleRenamePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int popupX = (this.width - CREATE_PRESET_POPUP_WIDTH) / 2;
        int popupY = (this.height - CREATE_PRESET_POPUP_HEIGHT) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + CREATE_PRESET_POPUP_HEIGHT - buttonHeight - 16;
        int cancelX = popupX + 20;
        int renameX = popupX + CREATE_PRESET_POPUP_WIDTH - buttonWidth - 20;

        if (isPointInRect((int) mouseX, (int) mouseY, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeRenamePresetPopup();
            return true;
        }

        if (isPointInRect((int) mouseX, (int) mouseY, renameX, buttonY, buttonWidth, buttonHeight)) {
            attemptRenamePreset();
            return true;
        }

        return false;
    }

    private boolean handlePresetDeletePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = PRESET_DELETE_POPUP_WIDTH;
        int popupHeight = PRESET_DELETE_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int deleteX = popupX + popupWidth - buttonWidth - 20;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int checkboxX = popupX + 20;
        int checkboxY = popupY + 86;
        int checkboxHitboxSize = PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4;

        if (isPointInRect(mouseXi, mouseYi, deleteX, buttonY, buttonWidth, buttonHeight)) {
            confirmPresetDeletion();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closePresetDeletePopup();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, checkboxX - 2, checkboxY - 2, checkboxHitboxSize, checkboxHitboxSize)) {
            setSkipPresetDeleteConfirm(!skipPresetDeleteConfirm);
            return true;
        }

        return true;
    }

    private void renderCreatePresetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, createPresetPopupAnimation.getPopupAlpha());

        int popupWidth = CREATE_PRESET_POPUP_WIDTH;
        int popupHeight = CREATE_PRESET_POPUP_HEIGHT;
        int[] bounds = createPresetPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, createPresetPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.popup.createPreset.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(createPresetPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        drawPopupTextWithEllipsis(
            context,
            Text.translatable("pathmind.popup.createPreset.message").getString(),
            popupX + 20,
            popupY + 44,
            scaledWidth - 40,
            getPopupAnimatedColor(createPresetPopupAnimation, UITheme.TEXT_SECONDARY)
        );

        int fieldX = popupX + 20;
        int fieldY = popupY + 70;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;

        boolean fieldHovered = isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        boolean focused = createPresetField != null && createPresetField.isFocused();
        int borderColor;
        if (focused) {
            borderColor = getAccentColor();
        } else if (fieldHovered) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
        } else {
            borderColor = UITheme.RENAME_INPUT_BORDER;
        }
        drawPopupInputFrame(
            context,
            fieldX,
            fieldY,
            fieldWidth,
            fieldHeight,
            borderColor,
            createPresetPopupAnimation
        );

        if (createPresetField != null) {
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            int textColor = getPopupAnimatedColor(createPresetPopupAnimation, UITheme.TEXT_PRIMARY);
            int textDisabledColor = getPopupAnimatedColor(createPresetPopupAnimation, UITheme.TEXT_TERTIARY);
            createPresetField.setEditableColor(textColor);
            createPresetField.setUneditableColor(textDisabledColor);
            int textFieldHeight = Math.max(10, fieldHeight - TEXT_FIELD_VERTICAL_PADDING * 2);
            createPresetField.setPosition(fieldX + 4, fieldY + TEXT_FIELD_VERTICAL_PADDING);
            createPresetField.setWidth(fieldWidth - 8);
            createPresetField.setHeight(textFieldHeight);
            createPresetField.render(context, mouseX, mouseY, delta);
        }

        if (!createPresetStatus.isEmpty()) {
            drawPopupTextWithEllipsis(
                context,
                createPresetStatus,
                fieldX,
                fieldY + fieldHeight + 8,
                fieldWidth,
                getPopupAnimatedColor(createPresetPopupAnimation, createPresetStatusColor)
            );
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int createX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean createHovered = isPointInRect(mouseX, mouseY, createX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(
            context,
            cancelX,
            buttonY,
            buttonWidth,
            buttonHeight,
            cancelHovered,
            Text.translatable("pathmind.button.cancel"),
            PopupButtonStyle.DEFAULT,
            createPresetPopupAnimation
        );
        drawPopupButton(
            context,
            createX,
            buttonY,
            buttonWidth,
            buttonHeight,
            createHovered,
            Text.translatable("pathmind.button.create"),
            PopupButtonStyle.PRIMARY,
            createPresetPopupAnimation
        );
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderRenamePresetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, renamePresetPopupAnimation.getPopupAlpha());

        int popupWidth = CREATE_PRESET_POPUP_WIDTH;
        int popupHeight = CREATE_PRESET_POPUP_HEIGHT;
        int[] bounds = renamePresetPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, renamePresetPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("pathmind.popup.renamePreset.title"),
                popupX + scaledWidth / 2,
                popupY + 14,
                UITheme.TEXT_PRIMARY
        );

        String presetLabel = pendingPresetRenameName == null || pendingPresetRenameName.isEmpty()
                ? Text.translatable("pathmind.popup.preset.fallbackSelected").getString()
                : Text.translatable("pathmind.popup.preset.label", pendingPresetRenameName).getString();
        drawPopupTextWithEllipsis(context, Text.translatable("pathmind.popup.renamePreset.message").getString(), popupX + 20, popupY + 44, scaledWidth - 40, UITheme.TEXT_SECONDARY);
        drawPopupTextWithEllipsis(context, presetLabel, popupX + 20, popupY + 58, scaledWidth - 40, UITheme.TEXT_SECONDARY);

        int fieldX = popupX + 20;
        int fieldY = popupY + 80;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;

        boolean fieldHovered = isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        boolean focused = renamePresetField != null && renamePresetField.isFocused();
        int borderColor;
        if (focused) {
            borderColor = getAccentColor();
        } else if (fieldHovered) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
        } else {
            borderColor = UITheme.RENAME_INPUT_BORDER;
        }
        drawPopupInputFrame(context, fieldX, fieldY, fieldWidth, fieldHeight, borderColor, renamePresetPopupAnimation);

        if (renamePresetField != null) {
            renamePresetField.setVisible(true);
            renamePresetField.setEditable(true);
            int textFieldHeight = Math.max(10, fieldHeight - TEXT_FIELD_VERTICAL_PADDING * 2);
            renamePresetField.setPosition(fieldX + 4, fieldY + TEXT_FIELD_VERTICAL_PADDING);
            renamePresetField.setWidth(fieldWidth - 8);
            renamePresetField.setHeight(textFieldHeight);
            renamePresetField.render(context, mouseX, mouseY, delta);
        }

        if (!renamePresetStatus.isEmpty()) {
            drawPopupTextWithEllipsis(context, renamePresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth, renamePresetStatusColor);
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int renameX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean renameHovered = isPointInRect(mouseX, mouseY, renameX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.translatable("pathmind.button.cancel"), false);
        drawPopupButton(context, renameX, buttonY, buttonWidth, buttonHeight, renameHovered, Text.translatable("pathmind.button.rename"), true);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderPresetDeletePopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, presetDeletePopupAnimation.getPopupAlpha());

        int popupWidth = PRESET_DELETE_POPUP_WIDTH;
        int popupHeight = PRESET_DELETE_POPUP_HEIGHT;
        int[] bounds = presetDeletePopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, presetDeletePopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.popup.deletePreset.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_PRIMARY)
        );

        String presetLabel = (pendingPresetDeletionName != null && !pendingPresetDeletionName.isEmpty())
                ? pendingPresetDeletionName
                : Text.translatable("pathmind.popup.preset.fallbackCurrent").getString();
        String warningLine = Text.translatable("pathmind.popup.deletePreset.message").getString();
        String presetLine = Text.translatable("pathmind.popup.preset.label", presetLabel).getString();
        drawPopupTextWithEllipsis(context, warningLine, popupX + 20, popupY + 48, scaledWidth - 40,
            getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));
        drawPopupTextWithEllipsis(context, presetLine, popupX + 20, popupY + 64, scaledWidth - 40,
            getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        int checkboxX = popupX + 20;
        int checkboxY = popupY + 86;
        boolean checkboxHovered = isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4);
        context.fill(checkboxX, checkboxY, checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE, checkboxY + PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, checkboxX, checkboxY, PRESET_DELETE_SKIP_CHECKBOX_SIZE, PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            getPopupAnimatedColor(presetDeletePopupAnimation, checkboxHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (skipPresetDeleteConfirm) {
            int checkColor = getPopupAnimatedColor(presetDeletePopupAnimation, getAccentColor());
            context.fill(checkboxX + 2, checkboxY + 5, checkboxX + 3, checkboxY + 7, checkColor);
            context.fill(checkboxX + 3, checkboxY + 6, checkboxX + 4, checkboxY + 8, checkColor);
            context.fill(checkboxX + 4, checkboxY + 6, checkboxX + 5, checkboxY + 7, checkColor);
            context.fill(checkboxX + 5, checkboxY + 5, checkboxX + 6, checkboxY + 6, checkColor);
            context.fill(checkboxX + 6, checkboxY + 4, checkboxX + 7, checkboxY + 5, checkColor);
            context.fill(checkboxX + 7, checkboxY + 3, checkboxX + 8, checkboxY + 4, checkColor);
        }
        drawPopupTextWithEllipsis(context, "Don't show again", checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE + 8, checkboxY + 1, scaledWidth - 68,
            getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int deleteX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean deleteHovered = isPointInRect(mouseX, mouseY, deleteX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Text.translatable("pathmind.button.cancel"), PopupButtonStyle.DEFAULT, presetDeletePopupAnimation);
        drawPopupButton(context, deleteX, buttonY, buttonWidth, buttonHeight, deleteHovered,
            Text.translatable("pathmind.button.delete"), PopupButtonStyle.PRIMARY, presetDeletePopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void attemptCreatePreset() {
        if (createPresetField == null) {
            return;
        }

        String desiredName = createPresetField.getText();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setCreatePresetStatus("Enter a preset name.", UITheme.STATE_ERROR);
            return;
        }

        Optional<String> createdPreset = PresetManager.createPreset(desiredName);
        if (createdPreset.isEmpty()) {
            setCreatePresetStatus("Preset name already exists or is invalid.", UITheme.STATE_ERROR);
            return;
        }

        switchPreset(createdPreset.get());
        closeCreatePresetPopup();
    }

    private void attemptRenamePreset() {
        if (renamePresetField == null) {
            return;
        }

        if (pendingPresetRenameName == null || pendingPresetRenameName.trim().isEmpty()) {
            setRenamePresetStatus("Select a preset to rename.", UITheme.STATE_ERROR);
            return;
        }

        String desiredName = renamePresetField.getText();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setRenamePresetStatus("Enter a preset name.", UITheme.STATE_ERROR);
            return;
        }

        if (!renamePresetInternal(pendingPresetRenameName, desiredName)) {
            setRenamePresetStatus("Preset name already exists or is invalid.", UITheme.STATE_ERROR);
            return;
        }

        closeRenamePresetPopup();
    }

    private void openPresetDeletePopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (skipPresetDeleteConfirm) {
            attemptDeletePreset(presetName);
            return;
        }
        pendingPresetDeletionName = presetName;
        presetDeletePopupAnimation.show();
        presetDropdownOpen = false;
    }

    private void closePresetDeletePopup() {
        presetDeletePopupAnimation.hide();
        pendingPresetDeletionName = "";
    }

    private void confirmPresetDeletion() {
        String presetName = pendingPresetDeletionName;
        closePresetDeletePopup();
        if (presetName != null && !presetName.isEmpty()) {
            attemptDeletePreset(presetName);
        }
    }

    private void setSkipPresetDeleteConfirm(boolean skip) {
        this.skipPresetDeleteConfirm = skip;
        if (currentSettings != null) {
            currentSettings.skipPresetDeleteConfirm = skip;
            SettingsManager.save(currentSettings);
        }
    }

    private void attemptDeletePreset(String presetName) {
        queueAnimatedPresetDeletion(presetName);
    }

    private void queueAnimatedPresetDeletion(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        if (presetName.equals(animatingPresetDeletionName)) {
            return;
        }
        if (draggingPresetTabName != null && draggingPresetTabName.equals(presetName)) {
            endPresetTabDrag();
        }
        AnimatedValue appear = presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f));
        appear.animateTo(0f, 140, AnimationHelper::easeOutCubic);
        animatingPresetDeletionName = presetName;
        animatingPresetDeletionExecuteAtMs = System.currentTimeMillis() + 140L;
    }

    private void attemptDeletePresetImmediate(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        boolean deletingActive = presetName.equals(activePresetName);
        String defaultPreset = PresetManager.getDefaultPresetName();
        String fallbackPreset = availablePresets.stream()
                .filter(name -> !name.equalsIgnoreCase(presetName))
                .findFirst()
                .orElse(defaultPreset);

        if (!PresetManager.deletePreset(presetName)) {
            return;
        }

        presetDropdownOpen = false;
        closeCreatePresetPopup();
        closeRenamePresetPopup();

        if (deletingActive) {
            PresetManager.setActivePreset(fallbackPreset);
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);

        if (deletingActive) {
            dismissParameterOverlay();
            isDraggingFromSidebar = false;
            draggingNodeType = null;
            draggingSidebarNode = null;
            clearPopupAnimation.hide();
            clearImportExportStatus();

            if (!nodeGraph.load()) {
                nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
            }
            refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
            nodeGraph.restoreSessionViewportState();
            updateImportExportPathFromPreset();
        }
    }

    private void setCreatePresetStatus(String message, int color) {
        createPresetStatus = message != null ? message : "";
        createPresetStatusColor = color;
    }

    private void clearCreatePresetStatus() {
        createPresetStatus = "";
        createPresetStatusColor = UITheme.TEXT_SECONDARY;
    }

    private void setRenamePresetStatus(String message, int color) {
        renamePresetStatus = message != null ? message : "";
        renamePresetStatusColor = color;
    }

    private void clearRenamePresetStatus() {
        renamePresetStatus = "";
        renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    }

    private void updateSelectionDeletionPreviewState() {
        Set<Node> selectedNodes = nodeGraph.getSelectedNodes();
        boolean preview = false;
        if (selectedNodes != null && !selectedNodes.isEmpty()) {
            boolean hasDragging = false;
            for (Node node : selectedNodes) {
                if (node != null && node.isDragging()) {
                    hasDragging = true;
                    break;
                }
            }
            if (hasDragging) {
                if (selectedNodes.size() > 1) {
                    preview = nodeGraph.isSelectionOverSidebar(sidebar.getWidth());
                } else {
                    for (Node node : selectedNodes) {
                        if (node != null && node.isDragging() && nodeGraph.isNodeOverSidebar(node, sidebar.getWidth())) {
                            preview = true;
                            break;
                        }
                    }
                }
            }
        }
        nodeGraph.setSelectionDeletionPreviewActive(preview);
    }

    private void refreshAvailablePresets() {
        stopInlinePresetRename(false);
        availablePresets = new ArrayList<>(PresetManager.getAvailablePresets());
        activePresetName = PresetManager.getActivePreset();
        syncPresetTabOrderWithAvailable();
    }

    private void movePresetTabToEnd(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }

        if (presetTabOrder.remove(presetName)) {
            presetTabOrder.add(presetName);
        }
    }

    private void syncPresetTabOrderWithAvailable() {
        HashSet<String> availableSet = new HashSet<>(availablePresets);
        HashSet<String> previousSet = new HashSet<>(presetTabOrder);

        presetTabOrder.removeIf(name -> !availableSet.contains(name));
        for (String preset : availablePresets) {
            if (!presetTabOrder.contains(preset)) {
                presetTabOrder.add(preset);
                AnimatedValue appear = presetTabAppearAnimations.computeIfAbsent(preset, key -> new AnimatedValue(1f));
                if (presetTabsInitialized && !previousSet.contains(preset)) {
                    appear.setValue(0f);
                    appear.animateTo(1f, 180, AnimationHelper::easeOutCubic);
                } else {
                    appear.setValue(1f);
                }
            }
        }

        normalizePresetTabOrder();

        presetTabXAnimations.entrySet().removeIf(entry -> !availableSet.contains(entry.getKey()));
        presetTabAppearAnimations.entrySet().removeIf(entry -> !availableSet.contains(entry.getKey()));
        presetTabsInitialized = true;
    }

    private void updateImportExportPathFromPreset() {
        lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
    }

    private void switchPreset(String presetName) {
        stopInlinePresetRename(false);
        nodeGraph.save();
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        if (importExportPopupAnimation.isVisible()) {
            closeImportExportPopup();
        }
        if (createPresetPopupAnimation.isVisible()) {
            closeCreatePresetPopup();
        }
        if (renamePresetPopupAnimation.isVisible()) {
            closeRenamePresetPopup();
        }
        clearPopupAnimation.hide();
        closeSettingsPopup();
        presetDropdownOpen = false;
        clearImportExportStatus();

        if (!nodeGraph.load()) {
            nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }
        resetWorkspaceTabsFromCurrentGraph();
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
        nodeGraph.restoreSessionViewportState();
        updateImportExportPathFromPreset();
    }

    private void openPublishPresetFlow() {
        if (this.client == null) {
            return;
        }
        nodeGraph.save();
        PresetManager.setActivePreset(activePresetName);
        Optional<String> linkedPresetId = PresetManager.getMarketplaceLinkedPresetId(activePresetName);
        MarketplaceAuthManager.AuthSession cachedSession = MarketplaceAuthManager.getCachedSession().orElse(null);
        if (cachedSession != null && linkedPresetId.isPresent()) {
            MarketplaceAuthManager.ensureValidSession().whenComplete((session, sessionThrowable) -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    if (sessionThrowable != null || session == null) {
                        this.client.setScreen(new PathmindMarketplaceScreen(this, true, activePresetName));
                        return;
                    }
                    MarketplaceService.fetchPresetById(session.getAccessToken(), linkedPresetId.get())
                        .whenComplete((preset, throwable) -> {
                            if (this.client == null) {
                                return;
                            }
                            this.client.execute(() -> {
                                if (throwable == null && preset != null) {
                                    this.client.setScreen(new PathmindMarketplaceScreen(this, false, null, preset));
                                } else {
                                    this.client.setScreen(new PathmindMarketplaceScreen(this, true, activePresetName));
                                }
                            });
                        });
                });
            });
            return;
        }
        this.client.setScreen(new PathmindMarketplaceScreen(this, true, activePresetName));
    }

    void reopenPublishPresetPopup(String presetName) {
        if (this.client == null) {
            return;
        }
        nodeGraph.save();
        PresetManager.setActivePreset(activePresetName);
        this.client.setScreen(new PathmindMarketplaceScreen(this, true, presetName));
    }

    private void renderWorkspaceButtons(DrawContext context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int workspaceButtonY = getWorkspaceButtonY();
        int bottomButtonY = getSettingsButtonY();
        boolean marketplaceHovered = renderMarketplaceButton(context, mouseX, mouseY, workspaceButtonY);
        boolean publishHovered = renderPublishButton(context, mouseX, mouseY, workspaceButtonY);
        boolean importHovered = renderImportExportButton(context, mouseX, mouseY, bottomButtonY);
        boolean clearHovered = renderClearButton(context, mouseX, mouseY, bottomButtonY);
        boolean homeHovered = renderHomeButton(context, mouseX, mouseY, bottomButtonY);

        if (showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            if (publishHovered) {
                drawWorkspaceTooltip(context, "Publish preset", mouseX, mouseY);
            } else if (marketplaceHovered) {
                drawWorkspaceTooltip(context, "Marketplace", mouseX, mouseY);
            } else if (homeHovered) {
                drawWorkspaceTooltip(context, "Reset view", mouseX, mouseY);
            } else if (clearHovered) {
                drawWorkspaceTooltip(context, "Clear workspace", mouseX, mouseY);
            } else if (importHovered) {
                drawWorkspaceTooltip(context, "Import / Export", mouseX, mouseY);
            }
        }
    }

    private boolean renderMarketplaceButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getMarketplaceButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
        drawToolbarButtonFrame(context, buttonX, buttonY, MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE,
            hovered, false, false, "workspace-marketplace");
        int textColor = hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
        String label = "Marketplace";
        int textX = buttonX + (MARKETPLACE_BUTTON_WIDTH - this.textRenderer.getWidth(label)) / 2;
        int textY = buttonY + (BOTTOM_BUTTON_SIZE - this.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY, textColor);
        return hovered;
    }

    private boolean renderPublishButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getPublishButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("workspace-publish", hovered);
        boolean synced = isCurrentPresetPublishedAndSynced();
        if (synced) {
            int background = AnimationHelper.lerpColor(UITheme.TOOLBAR_BG, UITheme.TOOLBAR_BG_HOVER, hoverProgress);
            int border = getAnimatedBorderColor("workspace-publish", hovered, UITheme.BORDER_DEFAULT, getAccentColor());
            int iconColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, getAccentColor(), hoverProgress);
            UIStyleHelper.drawToolbarButtonFrame(context, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE,
                background, border, UITheme.PANEL_INNER_BORDER);
            drawPublishArrowIcon(context, buttonX, buttonY, iconColor);
            return hovered;
        }
        int idleBackground = mixColor(UITheme.BACKGROUND_SECTION, UITheme.ACCENT_SKY, 0.62f);
        int hoverBackground = mixColor(UITheme.BACKGROUND_SECTION, UITheme.ACCENT_SKY, 0.78f);
        int background = AnimationHelper.lerpColor(idleBackground, hoverBackground, hoverProgress);
        int border = AnimationHelper.lerpColor(UITheme.ACCENT_SKY, UITheme.TEXT_EDITING_LABEL, hoverProgress);
        int iconColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, hoverProgress);
        UIStyleHelper.drawToolbarButtonFrame(context, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE,
            background, border, UITheme.PANEL_INNER_BORDER);
        drawPublishArrowIcon(context, buttonX, buttonY, iconColor);
        return hovered;
    }

    private boolean isCurrentPresetPublishedAndSynced() {
        if (activePresetName == null || activePresetName.isBlank()) {
            return false;
        }
        return PresetManager.getMarketplaceLinkedPresetId(activePresetName).isPresent()
            && !PresetManager.hasMarketplaceLinkedPresetChanges(activePresetName);
    }

    private void drawPublishArrowIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int top = buttonY + 4;
        int bottom = buttonY + BOTTOM_BUTTON_SIZE - 4;
        context.drawVerticalLine(centerX, top + 2, bottom, color);
        context.drawHorizontalLine(centerX - 3, centerX + 3, top + 2, color);
        context.drawHorizontalLine(centerX - 2, centerX + 2, top + 1, color);
        context.drawHorizontalLine(centerX - 1, centerX + 1, top, color);
    }

    private boolean renderHomeButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getHomeButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, false, false, "workspace-home");
        int iconColor = hovered ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int centerY = buttonY + BOTTOM_BUTTON_SIZE / 2;

        context.drawHorizontalLine(centerX - 4, centerX + 2, centerY, iconColor);
        context.drawVerticalLine(centerX - 4, centerY - 4, centerY + 2, iconColor);
        context.drawHorizontalLine(centerX - 2, centerX, centerY - 2, iconColor);
        context.drawHorizontalLine(centerX - 3, centerX - 1, centerY - 1, iconColor);
        context.drawVerticalLine(centerX - 2, centerY - 2, centerY, iconColor);
        context.drawVerticalLine(centerX - 3, centerY - 3, centerY - 1, iconColor);
        return hovered;
    }

    private boolean renderClearButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getClearButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, clearPopupAnimation.isVisible(), false, "workspace-clear");
        int iconColor = (hovered || clearPopupAnimation.isVisible()) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int top = buttonY + 4;
        int bottom = buttonY + BOTTOM_BUTTON_SIZE - 4;

        context.drawHorizontalLine(centerX - 5, centerX + 4, top, iconColor);
        context.drawVerticalLine(centerX - 5, top, top + 2, iconColor);
        context.drawVerticalLine(centerX + 4, top, top + 2, iconColor);
        context.drawHorizontalLine(centerX - 4, centerX + 3, top + 2, iconColor);
        context.drawVerticalLine(centerX - 3, top + 2, bottom, iconColor);
        context.drawVerticalLine(centerX + 2, top + 2, bottom, iconColor);
        context.drawHorizontalLine(centerX - 3, centerX + 2, bottom, iconColor);
        return hovered;
    }

    private boolean renderImportExportButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getImportExportButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, importExportPopupAnimation.isVisible(), false, "workspace-import-export");
        int iconColor = (hovered || importExportPopupAnimation.isVisible()) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int centerY = buttonY + BOTTOM_BUTTON_SIZE / 2;

        // Up arrow
        context.drawVerticalLine(centerX - 4, centerY - 5, centerY, iconColor);
        context.drawHorizontalLine(centerX - 6, centerX - 2, centerY - 5, iconColor);
        context.drawHorizontalLine(centerX - 5, centerX - 3, centerY - 4, iconColor);

        // Down arrow
        context.drawVerticalLine(centerX + 3, centerY, centerY + 5, iconColor);
        context.drawHorizontalLine(centerX + 1, centerX + 5, centerY + 5, iconColor);
        context.drawHorizontalLine(centerX + 2, centerX + 4, centerY + 4, iconColor);

        // Connector line
        context.drawHorizontalLine(centerX - 4, centerX + 3, centerY, iconColor);
        return hovered;
    }

    private void drawWorkspaceTooltip(DrawContext context, String text, int mouseX, int mouseY) {
        TooltipRenderer.render(context, this.textRenderer, text, mouseX, mouseY, this.width, this.height);
    }

    private void renderValidationButton(DrawContext context, int mouseX, int mouseY, boolean disabled,
                                        GraphValidationResult validationResult) {
        int buttonX = getValidationButtonX();
        int buttonY = getValidationButtonY();
        boolean active = validationPanelOpen;
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
        if (!active) {
            hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, false, disabled, "validation-button");
        }

        int statusColor = UITheme.TEXT_PRIMARY;
        if (validationResult != null) {
            if (validationResult.hasErrors()) {
                statusColor = UITheme.STATE_ERROR;
            } else if (validationResult.hasWarnings()) {
                statusColor = UITheme.ACCENT_AMBER;
            }
        }
        if (disabled) {
            statusColor = UITheme.DROPDOWN_ACTION_DISABLED;
        } else if (hovered || active) {
            statusColor = validationResult != null && validationResult.hasIssues() ? statusColor : getAccentColor();
        }

        if (!disabled && validationResult != null && validationResult.hasIssues() && !validationPanelOpen) {
            int severityBorder = validationResult.hasErrors() ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
            DrawContextBridge.drawBorder(context, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE, severityBorder);
        }

        if (validationResult != null && validationResult.hasIssues()) {
            drawValidationAlertIcon(context, buttonX, buttonY, statusColor);
            drawValidationCountBadge(context, validationResult, buttonX, buttonY, disabled);
        } else {
            drawValidationConsoleIcon(context, buttonX, buttonY, statusColor);
        }

    }

    private void renderValidationPanel(DrawContext context, int mouseX, int mouseY, GraphValidationResult validationResult) {
        float progress = validationPanelAnimation.getValue();
        if (progress <= 0.001f || validationResult == null) {
            return;
        }

        int[] bounds = getValidationPanelBounds(validationResult, progress);
        int panelX = bounds[0];
        int panelY = bounds[1];
        int panelWidth = bounds[2];
        int panelHeight = bounds[3];
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        int outlineColor = validationResult.hasErrors() ? UITheme.STATE_ERROR
            : validationResult.hasWarnings() ? UITheme.ACCENT_AMBER
            : UITheme.BORDER_DEFAULT;
        UIStyleHelper.drawBeveledPanel(
            context,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            UITheme.BACKGROUND_SECTION,
            outlineColor,
            UITheme.PANEL_INNER_BORDER
        );
        context.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight);

        int textColor = validationResult.hasErrors() ? UITheme.STATE_ERROR
            : validationResult.hasWarnings() ? UITheme.ACCENT_AMBER
            : UITheme.TEXT_PRIMARY;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Validation"), panelX + VALIDATION_PANEL_PADDING,
            panelY + 8, textColor);

        String summary = validationResult.getErrorCount() + " error" + (validationResult.getErrorCount() == 1 ? "" : "s")
            + "  " + validationResult.getWarningCount() + " warning" + (validationResult.getWarningCount() == 1 ? "" : "s");
        context.drawTextWithShadow(this.textRenderer, Text.literal(summary), panelX + VALIDATION_PANEL_PADDING,
            panelY + 19, UITheme.TEXT_SECONDARY);

        List<GraphValidationIssue> visibleIssues = getVisibleValidationIssues(validationResult);
        int contentTop = panelY + VALIDATION_PANEL_HEADER_HEIGHT;
        for (int index = 0; index < visibleIssues.size(); index++) {
            GraphValidationIssue issue = visibleIssues.get(index);
            int rowY = contentTop + index * VALIDATION_PANEL_ROW_HEIGHT;
            boolean clickable = issue != null && issue.hasNodeTarget();
            boolean hovered = clickable && isPointInRect(mouseX, mouseY, panelX + 1, rowY, panelWidth - 2, VALIDATION_PANEL_ROW_HEIGHT);
            int rowBg = hovered ? UITheme.TOOLBAR_BG_HOVER : UITheme.BACKGROUND_SECONDARY;
            context.fill(panelX + 1, rowY, panelX + panelWidth - 1, rowY + VALIDATION_PANEL_ROW_HEIGHT, rowBg);
            context.drawHorizontalLine(panelX + 1, panelX + panelWidth - 2, rowY, UITheme.BORDER_SUBTLE);

            int severityColor = issue.getSeverity() == GraphValidationSeverity.ERROR ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
            int dotTop = rowY + 7;
            context.fill(panelX + 8, dotTop, panelX + 12, dotTop + 4, severityColor);

            String prefix = issue.getSeverity() == GraphValidationSeverity.ERROR ? "Error" : "Warning";
            String message = TextRenderUtil.trimWithEllipsis(this.textRenderer,
                prefix + ": " + issue.getMessage(), panelWidth - 34);
            context.drawTextWithShadow(this.textRenderer, Text.literal(message), panelX + 18, rowY + 7,
                hovered ? UITheme.TEXT_PRIMARY : UITheme.TEXT_HEADER);
        }

        int hiddenCount = validationResult.getIssues().size() - visibleIssues.size();
        if (hiddenCount > 0) {
            int footerY = panelY + panelHeight - VALIDATION_PANEL_FOOTER_HEIGHT;
            context.drawHorizontalLine(panelX + 1, panelX + panelWidth - 2, footerY, UITheme.BORDER_SUBTLE);
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(hiddenCount + " more issue" + (hiddenCount == 1 ? "" : "s")),
                panelX + VALIDATION_PANEL_PADDING,
                footerY + 5,
                UITheme.TEXT_SECONDARY
            );
        }
        DrawContextBridge.flush(context);
        DrawContextBridge.flush(context);
        context.disableScissor();
    }

    private boolean handleValidationPanelClick(int mouseX, int mouseY) {
        if (isValidationButtonClicked(mouseX, mouseY, 0)) {
            return false;
        }
        GraphValidationResult validationResult = nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);
        if (!validationPanelOpen) {
            return false;
        }
        int[] bounds = getValidationPanelBounds(validationResult, 1f);
        if (!isPointInRect(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) {
            validationPanelOpen = false;
            return false;
        }

        List<GraphValidationIssue> visibleIssues = getVisibleValidationIssues(validationResult);
        int contentTop = bounds[1] + VALIDATION_PANEL_HEADER_HEIGHT;
        for (int index = 0; index < visibleIssues.size(); index++) {
            GraphValidationIssue issue = visibleIssues.get(index);
            int rowY = contentTop + index * VALIDATION_PANEL_ROW_HEIGHT;
            if (!isPointInRect(mouseX, mouseY, bounds[0] + 1, rowY, bounds[2] - 2, VALIDATION_PANEL_ROW_HEIGHT)) {
                continue;
            }
            if (issue != null && issue.hasNodeTarget()) {
                nodeGraph.focusNodeById(issue.getNodeId(), this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
            }
            return true;
        }
        return true;
    }

    private List<GraphValidationIssue> getVisibleValidationIssues(GraphValidationResult validationResult) {
        if (validationResult == null || !validationResult.hasIssues()) {
            return List.of();
        }
        int limit = Math.min(VALIDATION_PANEL_MAX_VISIBLE_ROWS, validationResult.getIssues().size());
        return validationResult.getIssues().subList(0, limit);
    }

    private int[] getValidationPanelBounds(GraphValidationResult validationResult, float progress) {
        List<GraphValidationIssue> visibleIssues = getVisibleValidationIssues(validationResult);
        int rowCount = visibleIssues.size();
        int footerHeight = validationResult != null && validationResult.getIssues().size() > rowCount
            ? VALIDATION_PANEL_FOOTER_HEIGHT : 0;
        int fullWidth = VALIDATION_PANEL_WIDTH;
        int fullHeight = VALIDATION_PANEL_HEADER_HEIGHT
            + rowCount * VALIDATION_PANEL_ROW_HEIGHT
            + footerHeight
            + VALIDATION_PANEL_BOTTOM_PADDING;
        int width = Math.max(1, Math.round(fullWidth * progress));
        int height = Math.max(1, Math.round(fullHeight * progress));
        int x = getValidationButtonX() + VALIDATION_BUTTON_SIZE - width;
        int y = getValidationButtonY();
        return new int[]{x, y, width, height};
    }

    private void drawValidationConsoleIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int left = buttonX + 4;
        int top = buttonY + 4;
        context.fill(left, top, left + 10, top + 1, color);
        context.fill(left, top + 8, left + 10, top + 9, color);
        context.fill(left, top, left + 1, top + 9, color);
        context.fill(left + 9, top, left + 10, top + 9, color);
        context.fill(left + 2, top + 2, left + 5, top + 3, color);
        context.fill(left + 2, top + 4, left + 7, top + 5, color);
        context.fill(left + 2, top + 6, left + 6, top + 7, color);
    }

    private void drawValidationAlertIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int stemX = buttonX + VALIDATION_BUTTON_SIZE / 2 - 1;
        int top = buttonY + 4;
        context.fill(stemX, top, stemX + 2, top + 6, color);
        context.fill(stemX, top + 8, stemX + 2, top + 10, color);
    }

    private void drawValidationCountBadge(DrawContext context, GraphValidationResult validationResult, int buttonX, int buttonY,
                                          boolean disabled) {
        int count = validationResult.getErrorCount() > 0 ? validationResult.getErrorCount() : validationResult.getWarningCount();
        int badgeColor = validationResult.getErrorCount() > 0 ? UITheme.STATE_ERROR : UITheme.ACCENT_AMBER;
        if (disabled) {
            badgeColor = UITheme.DROPDOWN_ACTION_DISABLED;
        }
        String text = count > 9 ? "9+" : String.valueOf(count);
        int textWidth = this.textRenderer.getWidth(text);
        int badgeSize = Math.max(9, textWidth + 4);
        int badgeX = buttonX + VALIDATION_BUTTON_SIZE - badgeSize + 1;
        int badgeY = buttonY - 2;
        context.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, badgeColor);
        DrawContextBridge.drawBorder(context, badgeX, badgeY, badgeSize, badgeSize, UITheme.BORDER_HIGHLIGHT);
        context.drawTextWithShadow(this.textRenderer, Text.literal(text), badgeX + (badgeSize - textWidth) / 2, badgeY + 1,
            UITheme.TEXT_PRIMARY);
    }

    private float getHoverProgress(Object key, boolean hovered) {
        return HoverAnimator.getProgress(key, hovered);
    }

    private int getAnimatedBorderColor(Object key, boolean hovered, int normalColor, int hoverColor) {
        float hoverProgress = getHoverProgress(key, hovered);
        return AnimationHelper.lerpColor(normalColor, hoverColor, hoverProgress);
    }

    private void renderSettingsButton(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getSettingsButtonX();
        int buttonY = getSettingsButtonY();
        boolean active = settingsPopupAnimation.isVisible();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, active, disabled, "settings-button");

        int iconColor = disabled ? UITheme.DROPDOWN_ACTION_DISABLED
                : (hovered || active) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        drawSettingsIcon(context, buttonX, buttonY, iconColor);

        if (hovered && showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            drawWorkspaceTooltip(context, "Settings", mouseX, mouseY);
        }
    }

    private void drawSettingsIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int centerY = buttonY + BOTTOM_BUTTON_SIZE / 2;
        int tooth = 2;
        context.fill(centerX - 1, centerY - 6, centerX + 1, centerY - 4, color);
        context.fill(centerX - 1, centerY + 4, centerX + 1, centerY + 6, color);
        context.fill(centerX - 6, centerY - 1, centerX - 4, centerY + 1, color);
        context.fill(centerX + 4, centerY - 1, centerX + 6, centerY + 1, color);

        context.fill(centerX - 4, centerY - 4, centerX + 4, centerY + 4, color);
        context.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, UITheme.GRID_ORIGIN);
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
    }

    private void renderSettingsPopup(DrawContext context, int mouseX, int mouseY) {
        float popupAlpha = settingsPopupAnimation.getPopupAlpha();

        int popupWidth = SETTINGS_POPUP_WIDTH;
        int popupHeight = getSettingsPopupHeight();
        int[] bounds = settingsPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];

        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);
        drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, settingsPopupAnimation);
        boolean popupScissor = enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.settings.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, scaledWidth, scaledHeight);
        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, scaledWidth, scaledHeight);
        settingsPopupScrollOffset = MathHelper.clamp(settingsPopupScrollOffset, 0, maxScroll);
        int contentPopupY = popupY - settingsPopupScrollOffset;
        context.enableScissor(bodyBounds[0], bodyBounds[1], bodyBounds[0] + bodyBounds[2], bodyBounds[1] + bodyBounds[3]);
        int contentX = popupX + 20;

        // Language section
        int languageLabelY = contentPopupY + 44;
        drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.language").getString(), contentX, languageLabelY, scaledWidth - 40,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        // Language dropdown button
        int languageButtonY = languageLabelY + 12;
        int languageButtonWidth = scaledWidth - 40;

        // Store dropdown position for rendering later
        this.languageDropdownX = contentX;
        this.languageDropdownY = languageButtonY;
        this.languageDropdownWidth = languageButtonWidth;

        String currentLang = this.client.getLanguageManager().getLanguage();
        String langDisplayName = getLanguageDisplayName(currentLang);
        boolean languageHovered = mouseX >= contentX && mouseX <= contentX + languageButtonWidth && mouseY >= languageButtonY && mouseY <= languageButtonY + 20;
        drawLanguageDropdown(context, contentX, languageButtonY, languageButtonWidth, langDisplayName, languageHovered);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, popupAlpha);

        // Adjust following sections downward by 50 pixels
        int accentLabelY = languageButtonY + 50;
        drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.accent").getString(), contentX, accentLabelY, scaledWidth - 40,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            boolean hovered = isPointInRect(mouseX, mouseY, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT);
            boolean selected = accentOption == option;
            drawAccentOption(context, optionX, accentOptionsY, option, hovered, selected);
            optionIndex++;
        }

        int sectionDividerX = popupX + 16;
        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, sectionDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int settingDividerY = sectionDividerY + 22;
        int gridRowCenterY = (sectionDividerY + settingDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, gridRowCenterY, Text.translatable("pathmind.settings.showGrid").getString(), showGrid, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, settingDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int footerDividerY = settingDividerY + 22;
        int tooltipRowCenterY = (settingDividerY + footerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, tooltipRowCenterY, Text.translatable("pathmind.settings.showTooltips").getString(), showWorkspaceTooltips, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, footerDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, chatRowCenterY, Text.translatable("pathmind.settings.showChatErrors").getString(), showChatErrors, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, chatDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, overlayRowCenterY, Text.translatable("pathmind.settings.showHudOverlays").getString(), showHudOverlays, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, overlayDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int delayDividerY = overlayDividerY + 26;
        int delayRowCenterY = (overlayDividerY + delayDividerY) / 2;
        renderNodeDelayRow(context, mouseX, mouseY, contentX, delayRowCenterY, nodeDelayMs, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS, popupX, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, delayDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int nodeSettingsLabelY = delayDividerY + 12;
        int nodeSettingsBodyY = nodeSettingsLabelY + 14;
        if (createListRadiusField != null) {
            createListRadiusField.setVisible(false);
        }
        int nodeSettingsHeaderX = contentX;
        if (!settingsNodeListView && getEffectiveSettingsTargetType() != null) {
            int backX = contentX;
            int backY = nodeSettingsLabelY - 3;
            boolean backHovered = isPointInRect(mouseX, mouseY, backX, backY, SETTINGS_BACK_BUTTON_WIDTH, SETTINGS_BACK_BUTTON_HEIGHT);
            drawPopupButton(context, backX, backY, SETTINGS_BACK_BUTTON_WIDTH, SETTINGS_BACK_BUTTON_HEIGHT, backHovered,
                Text.literal("< Back"), PopupButtonStyle.DEFAULT, settingsPopupAnimation);
            nodeSettingsHeaderX = backX + SETTINGS_BACK_BUTTON_WIDTH + 8;
            nodeSettingsBodyY = Math.max(nodeSettingsBodyY, backY + SETTINGS_BACK_BUTTON_HEIGHT + 6);
        }
        drawPopupTextWithEllipsis(context, "Node Settings", nodeSettingsHeaderX, nodeSettingsLabelY, scaledWidth - 40 - (nodeSettingsHeaderX - contentX),
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY);

        if (settingsNodeListView) {
            renderSettingsNodeList(context, mouseX, mouseY, popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        } else {
            NodeType targetType = getEffectiveSettingsTargetType();
            if (targetType == null) {
                renderSettingsNodeList(context, mouseX, mouseY, popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
            } else if (targetType == NodeType.GOTO) {
                drawPopupTextWithEllipsis(context, "Editing: " + targetType.getDisplayName(), contentX, nodeSettingsContentY, scaledWidth - 40,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_TERTIARY));

                int gotoBreakDividerY = nodeSettingsContentY + 28;
                int gotoBreakRowCenterY = (nodeSettingsContentY + 10 + gotoBreakDividerY) / 2;
                renderToggleRow(context, mouseX, mouseY, contentX, gotoBreakRowCenterY,
                    "Allow Baritone to break blocks while executing", currentSettings.gotoAllowBreakWhileExecuting != null && currentSettings.gotoAllowBreakWhileExecuting, popupX, scaledWidth);
                context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, gotoBreakDividerY,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

                int gotoPlaceDividerY = gotoBreakDividerY + 22;
                int gotoPlaceRowCenterY = (gotoBreakDividerY + gotoPlaceDividerY) / 2;
                renderToggleRow(context, mouseX, mouseY, contentX, gotoPlaceRowCenterY,
                    "Allow Baritone to place blocks while executing", currentSettings.gotoAllowPlaceWhileExecuting != null && currentSettings.gotoAllowPlaceWhileExecuting, popupX, scaledWidth);
                context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, gotoPlaceDividerY,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));
            } else if (targetType == NodeType.SENSOR_KEY_PRESSED) {
                drawPopupTextWithEllipsis(context, "Editing: " + targetType.getDisplayName(), contentX, nodeSettingsContentY, scaledWidth - 40,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_TERTIARY));

                int keyPressedDividerY = nodeSettingsContentY + 28;
                int keyPressedRowCenterY = (nodeSettingsContentY + 10 + keyPressedDividerY) / 2;
                renderToggleRow(context, mouseX, mouseY, contentX, keyPressedRowCenterY,
                    "Activate while GUIs are open", currentSettings.keyPressedActivatesInGuis == null || currentSettings.keyPressedActivatesInGuis, popupX, scaledWidth);
                context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, keyPressedDividerY,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));
            } else if (targetType == NodeType.CREATE_LIST) {
                Node targetNode = getEffectiveSettingsTargetNode();
                boolean useRadius = isCreateListCustomRadiusEnabled(targetNode);
                int radius = getCreateListSettingsRadius(targetNode);
                drawPopupTextWithEllipsis(context, "Editing: " + targetType.getDisplayName(), contentX, nodeSettingsContentY, scaledWidth - 40,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_TERTIARY));

                int createListToggleDividerY = nodeSettingsContentY + 28;
                int createListToggleRowCenterY = (nodeSettingsContentY + 10 + createListToggleDividerY) / 2;
                renderToggleRow(context, mouseX, mouseY, contentX, createListToggleRowCenterY,
                    "Use custom radius instead of render distance", useRadius, popupX, scaledWidth);
                context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, createListToggleDividerY,
                    getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

                if (useRadius) {
                    int createListRadiusDividerY = createListToggleDividerY + 26;
                    int createListRadiusRowCenterY = (createListToggleDividerY + createListRadiusDividerY) / 2;
                    renderCreateListRadiusRow(context, mouseX, mouseY, contentX, createListRadiusRowCenterY,
                        radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX, popupX, scaledWidth);
                    context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, createListRadiusDividerY,
                        getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));
                }
            }
        }

        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        int clearCacheRowCenterY = getSettingsClearCacheRowCenterY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16,
            getSettingsClearCacheDividerY(popupX, popupY, scaledWidth, scaledHeight, contentX, nodeSettingsContentY),
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));
        boolean clearCacheHovered = isPointInRect(mouseX, mouseY, clearCacheButtonBounds[0], clearCacheButtonBounds[1],
            clearCacheButtonBounds[2], clearCacheButtonBounds[3]);
        drawPopupTextWithEllipsis(context, "Clear cache", contentX, clearCacheRowCenterY - this.textRenderer.fontHeight / 2,
            scaledWidth - 40 - clearCacheButtonBounds[2] - 12, getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupButton(context, clearCacheButtonBounds[0], clearCacheButtonBounds[1], clearCacheButtonBounds[2], clearCacheButtonBounds[3],
            clearCacheHovered, Text.literal("Clear"), PopupButtonStyle.DEFAULT, settingsPopupAnimation);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = popupX + scaledWidth - buttonWidth - 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        DrawContextBridge.flush(context);
        DrawContextBridge.flush(context);
        context.disableScissor();
        renderSettingsPopupScrollbar(context, popupX, popupY, scaledWidth, scaledHeight, maxScroll);
        boolean closeHovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        drawPopupButton(context, buttonX, buttonY, buttonWidth, buttonHeight, closeHovered,
            Text.translatable("pathmind.button.close"), PopupButtonStyle.ACCENT, settingsPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawAccentOption(DrawContext context, int x, int y, AccentOption option, boolean hovered, boolean selected) {
        int bgColor = selected ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        if (hovered) {
            bgColor = selected ? UITheme.BORDER_FOCUS : UITheme.BORDER_SECTION;
        }
        int borderColor = selected ? getAccentColor() : UITheme.BORDER_SUBTLE;
        if (hovered) {
            borderColor = getAccentColor();
        }
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            SETTINGS_OPTION_WIDTH,
            SETTINGS_OPTION_HEIGHT,
            getPopupAnimatedColor(settingsPopupAnimation, bgColor),
            getPopupAnimatedColor(settingsPopupAnimation, borderColor),
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.PANEL_INNER_BORDER)
        );

        int swatchSize = 8;
        int swatchX = x + 4;
        int swatchY = y + (SETTINGS_OPTION_HEIGHT - swatchSize) / 2;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize,
            getPopupAnimatedColor(settingsPopupAnimation, option.color));

        int labelX = swatchX + swatchSize + 4;
        int labelY = y + (SETTINGS_OPTION_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(this.textRenderer, Text.literal(option.label), labelX, labelY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_PRIMARY));
    }

    private void renderToggleRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label, boolean active, int popupX, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int toggleX = popupX + scaledWidth - SETTINGS_TOGGLE_WIDTH - 20;
        int toggleY = centerY - SETTINGS_TOGGLE_HEIGHT / 2;
        int maxLabelWidth = Math.max(0, toggleX - labelX - 8);
        drawPopupTextWithEllipsis(context, label, labelX, labelY, maxLabelWidth,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        boolean hovered = isPointInRect(mouseX, mouseY, toggleX, toggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT);
        PopupButtonStyle style = active ? PopupButtonStyle.PRIMARY : PopupButtonStyle.DEFAULT;
        String toggleLabel = active ? Text.translatable("pathmind.settings.on").getString() : Text.translatable("pathmind.settings.off").getString();
        drawPopupButton(context, toggleX, toggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT, hovered,
            Text.literal(toggleLabel), style, settingsPopupAnimation);
    }

    private void renderSliderRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label,
                                 int value, int min, int max, int popupX, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;
        String valueText = value + "ms";
        int valueTextWidth = this.textRenderer.getWidth(valueText);
        int valueBoxWidth = Math.max(36, valueTextWidth + 10);
        int valueBoxX = sliderX - valueBoxWidth - 8;
        int valueBoxY = centerY - SETTINGS_SLIDER_HEIGHT / 2;
        int valueBoxHeight = SETTINGS_SLIDER_HEIGHT;
        int maxLabelWidth = Math.max(0, valueBoxX - labelX - 8);
        drawPopupTextWithEllipsis(context, label, labelX, labelY, maxLabelWidth,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        int valueBoxBg = settingsPopupAnimation.getAnimatedPopupColor(UITheme.DROPDOWN_OPTION_BG);
        int valueBoxBorder = settingsPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_SUBTLE);
        context.fill(valueBoxX, valueBoxY, valueBoxX + valueBoxWidth, valueBoxY + valueBoxHeight, valueBoxBg);
        DrawContextBridge.drawBorder(context, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, valueBoxBorder);
        int valueTextX = valueBoxX + Math.max(4, (valueBoxWidth - valueTextWidth) / 2);
        int valueTextY = valueBoxY + (valueBoxHeight - this.textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(this.textRenderer, Text.literal(valueText), valueTextX, valueTextY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_HEADER));

        int sliderRight = sliderX + SETTINGS_SLIDER_WIDTH;
        boolean hovered = isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        int trackColor = hovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        int trackBorder = UITheme.BORDER_SUBTLE;
        trackColor = settingsPopupAnimation.getAnimatedPopupColor(trackColor);
        trackBorder = settingsPopupAnimation.getAnimatedPopupColor(trackBorder);
        context.fill(sliderX, sliderY, sliderRight, sliderY + SETTINGS_SLIDER_HEIGHT, trackColor);
        DrawContextBridge.drawBorder(context, sliderX, sliderY, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT, trackBorder);

        int clamped = MathHelper.clamp(value, min, max);
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (SETTINGS_SLIDER_WIDTH - SETTINGS_SLIDER_HANDLE_WIDTH));
        int handleY = centerY - SETTINGS_SLIDER_HANDLE_HEIGHT / 2;
        int handleColor = settingsPopupAnimation.getAnimatedPopupColor(getAccentColor());
        int handleBorder = (hovered || nodeDelayDragging) ? getAccentColor() : UITheme.BORDER_SUBTLE;
        handleBorder = getPopupAnimatedColor(settingsPopupAnimation, handleBorder);
        context.fill(handleX, handleY, handleX + SETTINGS_SLIDER_HANDLE_WIDTH, handleY + SETTINGS_SLIDER_HANDLE_HEIGHT, handleColor);
        DrawContextBridge.drawBorder(context, handleX, handleY, SETTINGS_SLIDER_HANDLE_WIDTH, SETTINGS_SLIDER_HANDLE_HEIGHT,
            handleBorder);
    }

    private void renderNodeDelayRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY,
                                    int value, int min, int max, int popupX, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;

        String valueText = Integer.toString(value);
        int[] valueBox = getNodeDelayFieldBounds(popupX, scaledWidth, centerY, valueText);
        int valueBoxX = valueBox[0];
        int valueBoxY = valueBox[1];
        int valueBoxWidth = valueBox[2];
        int valueBoxHeight = valueBox[3];
        int maxLabelWidth = Math.max(0, valueBoxX - labelX - 8);
        drawPopupTextWithEllipsis(context, Text.translatable("pathmind.settings.nodeDelay").getString(), labelX, labelY, maxLabelWidth,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        boolean fieldHovered = isPointInRect(mouseX, mouseY, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight);
        boolean focused = nodeDelayField != null && nodeDelayField.isFocused();
        boolean activeField = focused;
        int valueBoxBg = activeField ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        if (fieldHovered) {
            valueBoxBg = activeField ? UITheme.BORDER_FOCUS : UITheme.BORDER_SECTION;
        }
        int valueBoxBorder = activeField ? getAccentColor() : UITheme.BORDER_SUBTLE;
        if (fieldHovered) {
            valueBoxBorder = getAccentColor();
        }
        valueBoxBg = settingsPopupAnimation.getAnimatedPopupColor(valueBoxBg);
        valueBoxBorder = settingsPopupAnimation.getAnimatedPopupColor(valueBoxBorder);
        context.fill(valueBoxX, valueBoxY, valueBoxX + valueBoxWidth, valueBoxY + valueBoxHeight, valueBoxBg);
        DrawContextBridge.drawBorder(context, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, valueBoxBorder);

        if (nodeDelayField != null) {
            if (!focused) {
                if (!valueText.equals(nodeDelayField.getText())) {
                    nodeDelayField.setText(valueText);
                }
            }
            nodeDelayField.setVisible(true);
            nodeDelayField.setEditable(true);
            nodeDelayField.setEditableColor(getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_HEADER));
            nodeDelayField.setUneditableColor(getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_HEADER));
            int textFieldHeight = Math.max(10, valueBoxHeight - TEXT_FIELD_VERTICAL_PADDING * 2);
            nodeDelayField.setPosition(valueBoxX + 4, valueBoxY + TEXT_FIELD_VERTICAL_PADDING);
            nodeDelayField.setWidth(valueBoxWidth - 8);
            nodeDelayField.setHeight(textFieldHeight);
            nodeDelayField.render(context, mouseX, mouseY, 0f);
        }

        int unitX = valueBoxX + valueBoxWidth + 6;
        int unitY = valueBoxY + (valueBoxHeight - this.textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(this.textRenderer, Text.literal("ms"), unitX, unitY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        int sliderRight = sliderX + SETTINGS_SLIDER_WIDTH;
        boolean hovered = isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        int trackColor = hovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        int trackBorder = UITheme.BORDER_SUBTLE;
        trackColor = settingsPopupAnimation.getAnimatedPopupColor(trackColor);
        trackBorder = settingsPopupAnimation.getAnimatedPopupColor(trackBorder);
        context.fill(sliderX, sliderY, sliderRight, sliderY + SETTINGS_SLIDER_HEIGHT, trackColor);
        DrawContextBridge.drawBorder(context, sliderX, sliderY, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT, trackBorder);

        int clamped = MathHelper.clamp(value, min, max);
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (SETTINGS_SLIDER_WIDTH - SETTINGS_SLIDER_HANDLE_WIDTH));
        int handleY = centerY - SETTINGS_SLIDER_HANDLE_HEIGHT / 2;
        int handleColor = settingsPopupAnimation.getAnimatedPopupColor(getAccentColor());
        int handleBorder = (hovered || nodeDelayDragging) ? getAccentColor() : UITheme.BORDER_SUBTLE;
        handleBorder = getPopupAnimatedColor(settingsPopupAnimation, handleBorder);
        context.fill(handleX, handleY, handleX + SETTINGS_SLIDER_HANDLE_WIDTH, handleY + SETTINGS_SLIDER_HANDLE_HEIGHT, handleColor);
        DrawContextBridge.drawBorder(context, handleX, handleY, SETTINGS_SLIDER_HANDLE_WIDTH, SETTINGS_SLIDER_HANDLE_HEIGHT,
            handleBorder);
    }

    private void renderCreateListRadiusRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY,
                                           int value, int min, int max, int popupX, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;
        String valueText = Integer.toString(value);
        int[] valueBox = getCreateListRadiusFieldBounds(popupX, scaledWidth, centerY, valueText);
        int valueBoxX = valueBox[0];
        int valueBoxY = valueBox[1];
        int valueBoxWidth = valueBox[2];
        int valueBoxHeight = valueBox[3];
        int maxLabelWidth = Math.max(0, valueBoxX - labelX - 8);
        drawPopupTextWithEllipsis(context, "Radius", labelX, labelY, maxLabelWidth,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));
        boolean fieldHovered = isPointInRect(mouseX, mouseY, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight);
        boolean focused = createListRadiusField != null && createListRadiusField.isFocused();
        boolean activeField = focused;
        int valueBoxBg = activeField ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        if (fieldHovered) {
            valueBoxBg = activeField ? UITheme.BORDER_FOCUS : UITheme.BORDER_SECTION;
        }
        int valueBoxBorder = activeField ? getAccentColor() : UITheme.BORDER_SUBTLE;
        if (fieldHovered) {
            valueBoxBorder = getAccentColor();
        }
        valueBoxBg = settingsPopupAnimation.getAnimatedPopupColor(valueBoxBg);
        valueBoxBorder = settingsPopupAnimation.getAnimatedPopupColor(valueBoxBorder);
        context.fill(valueBoxX, valueBoxY, valueBoxX + valueBoxWidth, valueBoxY + valueBoxHeight, valueBoxBg);
        DrawContextBridge.drawBorder(context, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, valueBoxBorder);
        if (createListRadiusField != null) {
            if (!focused && !valueText.equals(createListRadiusField.getText())) {
                createListRadiusField.setText(valueText);
            }
            createListRadiusField.setVisible(true);
            createListRadiusField.setEditable(true);
            createListRadiusField.setEditableColor(getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_HEADER));
            createListRadiusField.setUneditableColor(getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_HEADER));
            int textFieldHeight = Math.max(10, valueBoxHeight - TEXT_FIELD_VERTICAL_PADDING * 2);
            createListRadiusField.setPosition(valueBoxX + 4, valueBoxY + TEXT_FIELD_VERTICAL_PADDING);
            createListRadiusField.setWidth(valueBoxWidth - 8);
            createListRadiusField.setHeight(textFieldHeight);
            createListRadiusField.render(context, mouseX, mouseY, 0f);
        }
        int unitX = valueBoxX + valueBoxWidth + 6;
        int unitY = valueBoxY + (valueBoxHeight - this.textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(this.textRenderer, Text.literal("blocks"), unitX, unitY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));
        int sliderRight = sliderX + SETTINGS_SLIDER_WIDTH;
        boolean hovered = isPointInRect(mouseX, mouseY, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8);
        int trackColor = hovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
        int trackBorder = UITheme.BORDER_SUBTLE;
        trackColor = settingsPopupAnimation.getAnimatedPopupColor(trackColor);
        trackBorder = settingsPopupAnimation.getAnimatedPopupColor(trackBorder);
        context.fill(sliderX, sliderY, sliderRight, sliderY + SETTINGS_SLIDER_HEIGHT, trackColor);
        DrawContextBridge.drawBorder(context, sliderX, sliderY, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT, trackBorder);
        int clamped = MathHelper.clamp(value, min, max);
        float t = max == min ? 0f : (clamped - min) / (float) (max - min);
        int handleX = sliderX + Math.round(t * (SETTINGS_SLIDER_WIDTH - SETTINGS_SLIDER_HANDLE_WIDTH));
        int handleY = centerY - SETTINGS_SLIDER_HANDLE_HEIGHT / 2;
        int handleColor = settingsPopupAnimation.getAnimatedPopupColor(getAccentColor());
        int handleBorder = (hovered || createListRadiusDragging) ? getAccentColor() : UITheme.BORDER_SUBTLE;
        handleBorder = getPopupAnimatedColor(settingsPopupAnimation, handleBorder);
        context.fill(handleX, handleY, handleX + SETTINGS_SLIDER_HANDLE_WIDTH, handleY + SETTINGS_SLIDER_HANDLE_HEIGHT, handleColor);
        DrawContextBridge.drawBorder(context, handleX, handleY, SETTINGS_SLIDER_HANDLE_WIDTH, SETTINGS_SLIDER_HANDLE_HEIGHT, handleBorder);
    }

    private int[] getNodeDelayFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = this.textRenderer.getWidth(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = this.textRenderer.getWidth("ms");
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    private int[] getCreateListRadiusFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        int sliderX = popupX + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        String text = valueText == null ? "" : valueText;
        int textWidth = this.textRenderer.getWidth(text);
        int boxWidth = Math.max(32, textWidth + 8);
        int boxHeight = 16;
        int unitGap = 6;
        int unitWidth = this.textRenderer.getWidth("blocks");
        int boxX = sliderX - boxWidth - unitGap - unitWidth - 4;
        int boxY = centerY - boxHeight / 2;
        return new int[]{boxX, boxY, boxWidth, boxHeight};
    }

    private void updateNodeDelayFromMouse(int mouseX, int popupX, int popupWidth) {
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = MathHelper.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
        float t = SETTINGS_SLIDER_WIDTH <= 0 ? 0f : localX / (float) SETTINGS_SLIDER_WIDTH;
        int value = NODE_DELAY_MIN_MS + Math.round(t * (NODE_DELAY_MAX_MS - NODE_DELAY_MIN_MS));
        if (value != nodeDelayMs) {
            nodeDelayMs = value;
            currentSettings.nodeDelayMs = nodeDelayMs;
            SettingsManager.save(currentSettings);
        }
    }

    private void updateCreateListRadiusFromMouse(Node node, int mouseX, int popupX, int popupWidth) {
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
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

    private Integer parseDelayFieldValue(String value) {
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

    private Integer parseCreateListRadiusFieldValue(String value) {
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

    private boolean supportsNodeSettings(Node node) {
        return node != null && supportsNodeSettings(node.getType());
    }

    private boolean supportsNodeSettings(NodeType type) {
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

    private boolean hasEditedNodeSettings(NodeType type) {
        if (!supportsNodeSettings(type) || currentSettings == null) {
            return false;
        }
        return switch (type) {
            case GOTO -> Boolean.TRUE.equals(currentSettings.gotoAllowBreakWhileExecuting)
                || Boolean.TRUE.equals(currentSettings.gotoAllowPlaceWhileExecuting);
            case SENSOR_KEY_PRESSED -> currentSettings.keyPressedActivatesInGuis != null
                && !currentSettings.keyPressedActivatesInGuis;
            case CREATE_LIST -> {
                boolean edited = false;
                if (nodeGraph != null) {
                    for (Node node : nodeGraph.getNodes()) {
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

    private boolean isCreateListCustomRadiusEnabled(Node node) {
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return Boolean.TRUE.equals(SettingsManager.getCurrent().createListUseCustomRadius);
        }
        node.ensureCreateListRadiusParameters();
        return node.getParameter("UseRadius") != null && node.getParameter("UseRadius").getBoolValue();
    }

    private int getCreateListSettingsRadius(Node node) {
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

    private void setCreateListCustomRadiusEnabled(Node node, boolean enabled) {
        Settings settings = SettingsManager.getCurrent();
        settings.createListUseCustomRadius = enabled;
        SettingsManager.save(settings);
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        node.ensureCreateListRadiusParameters();
        node.setParameterValueAndPropagate("UseRadius", Boolean.toString(enabled));
        if (nodeGraph != null) {
            nodeGraph.notifyNodeParametersChanged(node);
        }
    }

    private void setCreateListSettingsRadius(Node node, int radius) {
        int clamped = MathHelper.clamp(radius, CREATE_LIST_RADIUS_MIN, CREATE_LIST_RADIUS_MAX);
        Settings settings = SettingsManager.getCurrent();
        settings.createListRadius = clamped;
        SettingsManager.save(settings);
        if (node == null || node.getType() != NodeType.CREATE_LIST) {
            return;
        }
        node.ensureCreateListRadiusParameters();
        node.setParameterValueAndPropagate("Radius", Integer.toString(clamped));
        if (nodeGraph != null) {
            nodeGraph.notifyNodeParametersChanged(node);
        }
    }

    private List<NodeType> getSettingsNodeTypes() {
        List<NodeType> result = new ArrayList<>();
        for (NodeType type : SETTINGS_NODE_TYPES) {
            result.add(type);
        }
        return result;
    }

    private NodeType getEffectiveSettingsTargetType() {
        if (supportsNodeSettings(settingsNodeTargetType)) {
            return settingsNodeTargetType;
        }
        if (supportsNodeSettings(settingsNodeTarget)) {
            return settingsNodeTarget.getType();
        }
        return null;
    }

    private Node findFirstNodeWithSettingsType(NodeType type) {
        if (!supportsNodeSettings(type) || nodeGraph == null) {
            return null;
        }
        for (Node node : nodeGraph.getNodes()) {
            if (node != null && node.getType() == type) {
                return node;
            }
        }
        return null;
    }

    private boolean hasNodeWithSettingsType(NodeType type) {
        return findFirstNodeWithSettingsType(type) != null;
    }

    private Node getEffectiveSettingsTargetNode() {
        NodeType targetType = getEffectiveSettingsTargetType();
        if (targetType == null) {
            return null;
        }
        if (supportsNodeSettings(settingsNodeTarget) && settingsNodeTarget.getType() == targetType) {
            return settingsNodeTarget;
        }
        return findFirstNodeWithSettingsType(targetType);
    }

    private int getSettingsNodeSectionContentBottom(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
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

    private int[] getSettingsClearCacheButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        int dividerY = getSettingsClearCacheDividerY(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int buttonY = dividerY + 8;
        int buttonX = popupX + popupWidth - SETTINGS_SECTION_BUTTON_WIDTH - 20;
        return new int[]{buttonX, buttonY, SETTINGS_SECTION_BUTTON_WIDTH, SETTINGS_SECTION_BUTTON_HEIGHT};
    }

    private int getSettingsClearCacheRowCenterY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY)[1]
            + SETTINGS_SECTION_BUTTON_HEIGHT / 2;
    }

    private int getSettingsClearCacheDividerY(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return getSettingsNodeSectionContentBottom(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY) + 10;
    }

    private int getSettingsClearCacheSectionHeight() {
        return 38;
    }

    private int getSettingsNodeSectionContentY(int bodyY) {
        return bodyY;
    }

    private void clearSettingsCache() {
        boolean cleared = Node.clearRecipeCache(this.client);
        NodeErrorNotificationOverlay overlay = NodeErrorNotificationOverlay.getInstance();
        if (cleared) {
            overlay.show("Cache cleared.", UITheme.STATE_SUCCESS);
        } else {
            overlay.show("No cache file found.", UITheme.STATE_ERROR);
        }
    }

    private int getSettingsNodeSectionBodyY(int popupY) {
        int languageLabelY = popupY + 44;
        int languageButtonY = languageLabelY + 12;
        int accentLabelY = languageButtonY + 50;
        int accentOptionsY = accentLabelY + 12;
        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        int settingDividerY = sectionDividerY + 22;
        int footerDividerY = settingDividerY + 22;
        int chatDividerY = footerDividerY + 22;
        int overlayDividerY = chatDividerY + 22;
        int delayDividerY = overlayDividerY + 26;
        int nodeSettingsLabelY = delayDividerY + 12;
        int bodyY = nodeSettingsLabelY + 14;
        if (!settingsNodeListView && getEffectiveSettingsTargetType() != null) {
            int backY = nodeSettingsLabelY - 3;
            bodyY = Math.max(bodyY, backY + SETTINGS_BACK_BUTTON_HEIGHT + 6);
        }
        return bodyY;
    }

    private int[] getSettingsNodeListBounds(int popupX, int popupY, int scaledWidth, int scaledHeight, int contentX, int bodyY) {
        int listX = contentX;
        int listY = bodyY + SETTINGS_NODE_LIST_GAP;
        int listWidth = scaledWidth - 40;
        int buttonY = popupY + scaledHeight - 20 - 16;
        int minListHeight = SETTINGS_NODE_LIST_ROW_HEIGHT * 4;
        int availableHeight = buttonY - 8 - listY - getSettingsClearCacheSectionHeight();
        int listHeight = Math.max(minListHeight, availableHeight);
        return new int[]{listX, listY, listWidth, listHeight};
    }

    private void renderSettingsNodeList(DrawContext context, int mouseX, int mouseY, int popupX, int popupY, int scaledWidth, int scaledHeight, int contentX, int bodyY) {
        List<NodeType> settingsNodes = getSettingsNodeTypes();
        int[] listBounds = getSettingsNodeListBounds(popupX, popupY, scaledWidth, scaledHeight, contentX, bodyY);
        int listX = listBounds[0];
        int listY = listBounds[1];
        int listWidth = listBounds[2];
        int listHeight = listBounds[3];
        if (settingsNodes.isEmpty()) {
            drawPopupTextWithEllipsis(context, "No adjustable node settings are available in this workspace yet.", contentX, bodyY, scaledWidth - 40,
                getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_TERTIARY));
            return;
        }

        int visibleRows = Math.max(1, listHeight / SETTINGS_NODE_LIST_ROW_HEIGHT);
        int maxScroll = Math.max(0, settingsNodes.size() - visibleRows);
        settingsNodeListScrollOffset = MathHelper.clamp(settingsNodeListScrollOffset, 0, maxScroll);

        UIStyleHelper.drawBeveledPanel(
            context,
            listX,
            listY,
            listWidth,
            listHeight,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BACKGROUND_SECONDARY),
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE),
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.PANEL_INNER_BORDER)
        );

        context.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        int startIndex = settingsNodeListScrollOffset;
        int endIndex = Math.min(settingsNodes.size(), startIndex + visibleRows + 1);
        for (int i = startIndex; i < endIndex; i++) {
            NodeType type = settingsNodes.get(i);
            int rowY = listY + (i - startIndex) * SETTINGS_NODE_LIST_ROW_HEIGHT;
            boolean hovered = isPointInRect(mouseX, mouseY, listX, rowY, listWidth, SETTINGS_NODE_LIST_ROW_HEIGHT);
            boolean editing = getEffectiveSettingsTargetType() == type && !settingsNodeListView;
            int rowBg = editing ? UITheme.DROPDOWN_OPTION_HOVER : hovered ? UITheme.BORDER_SECTION : UITheme.DROPDOWN_OPTION_BG;
            int rowBorder = editing ? getAccentColor() : UITheme.BORDER_SUBTLE;
            context.fill(listX + 1, rowY + 1, listX + listWidth - 1, rowY + SETTINGS_NODE_LIST_ROW_HEIGHT - 1,
                getPopupAnimatedColor(settingsPopupAnimation, rowBg));
            DrawContextBridge.drawBorder(context, listX, rowY, listWidth, SETTINGS_NODE_LIST_ROW_HEIGHT,
                getPopupAnimatedColor(settingsPopupAnimation, rowBorder));

            String label = type.getDisplayName();
            String status = editing ? "Editing" : hasEditedNodeSettings(type) ? "Edited" : "";
            int statusWidth = status.isEmpty() ? 0 : this.textRenderer.getWidth(status);
            int maxLabelWidth = Math.max(0, listWidth - 12 - statusWidth - (status.isEmpty() ? 0 : 8));
            drawPopupTextWithEllipsis(context, label, listX + 6, rowY + 6, maxLabelWidth,
                getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_PRIMARY));
            if (!status.isEmpty()) {
                int statusColor = editing ? getAccentColor() : getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_TERTIARY);
                context.drawTextWithShadow(this.textRenderer, Text.literal(status),
                    listX + listWidth - statusWidth - 6, rowY + 6, statusColor);
            }
        }
        DrawContextBridge.flush(context);
        DrawContextBridge.flush(context);
        context.disableScissor();
    }

    private int[] getSettingsPopupBodyBounds(int popupX, int popupY, int popupWidth, int popupHeight) {
        int bodyX = popupX + 1;
        int bodyTop = popupY + 40;
        int buttonY = popupY + popupHeight - 20 - 16;
        int bodyBottom = buttonY - 8;
        return new int[]{bodyX, bodyTop, Math.max(1, popupWidth - 2), Math.max(1, bodyBottom - bodyTop)};
    }

    private int getSettingsPopupMaxScroll(int popupX, int popupY, int popupWidth, int popupHeight) {
        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
        int bodyBottom = bodyBounds[1] + bodyBounds[3];
        int contentX = popupX + 20;
        int nodeSettingsBodyY = getSettingsNodeSectionBodyY(popupY);
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY);
        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int contentBottom = clearCacheButtonBounds[1] + clearCacheButtonBounds[3];
        return Math.max(0, contentBottom - bodyBottom + 8);
    }

    private void renderSettingsPopupScrollbar(DrawContext context, int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }

        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
        int trackLeft = popupX + popupWidth - 12;
        int trackRight = trackLeft + 4;
        int trackTop = bodyBounds[1];
        int trackBottom = bodyBounds[1] + bodyBounds[3];
        int trackHeight = Math.max(1, trackBottom - trackTop);

        context.fill(trackLeft, trackTop, trackRight, trackBottom,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BACKGROUND_SIDEBAR));
        DrawContextBridge.drawBorder(context, trackLeft, trackTop, 4, trackHeight,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_DEFAULT));

        int visibleHeight = Math.max(1, bodyBounds[3]);
        int totalHeight = visibleHeight + maxScroll;
        int thumbHeight = Math.max(20, (visibleHeight * trackHeight) / Math.max(1, totalHeight));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int maxThumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbOffset = maxScroll == 0 ? 0 : Math.round((float) settingsPopupScrollOffset / (float) maxScroll * maxThumbTravel);
        int thumbTop = trackTop + thumbOffset;

        context.fill(trackLeft + 1, thumbTop, trackRight - 1, thumbTop + thumbHeight,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_DEFAULT));
    }

    private boolean renderButtonBackground(DrawContext context, int buttonX, int buttonY, int mouseX, int mouseY,
                                           boolean active, boolean disabled, Object hoverKey) {
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        drawToolbarButtonFrame(context, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE, hovered, active, disabled, hoverKey);
        return hovered;
    }

    private void drawToolbarButtonFrame(DrawContext context, int x, int y, int width, int height,
                                        boolean hovered, boolean active, boolean disabled, Object hoverKey) {
        int bgColor;
        if (disabled) {
            bgColor = UITheme.TOOLBAR_BG_DISABLED;
        } else if (hovered) {
            bgColor = active ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG_HOVER;
        } else {
            bgColor = active ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG;
        }
        int borderColor = disabled
            ? UITheme.BORDER_SUBTLE
            : getAnimatedBorderColor(hoverKey, hovered || active, UITheme.BORDER_DEFAULT, getAccentColor());
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, width, height, bgColor, borderColor, UITheme.PANEL_INNER_BORDER);
    }

    private int getWorkspaceButtonY() {
        return TITLE_BAR_HEIGHT + BOTTOM_BUTTON_MARGIN;
    }

    private int getSidebarVisibleWidth() {
        return sidebar != null ? sidebar.getWidth() : Sidebar.getCollapsedWidth();
    }

    private int getMarketplaceButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN + BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING;
    }

    private int getPublishButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN;
    }

    private int getHomeButtonX() {
        return getImportExportButtonX() + (BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING) * 2;
    }

    private int getClearButtonX() {
        return getImportExportButtonX() + BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING;
    }

    private int getImportExportButtonX() {
        return getSettingsButtonX() + BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING;
    }

    private int getSettingsButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN;
    }

    private int getSettingsButtonY() {
        return this.height - BOTTOM_BUTTON_MARGIN - BOTTOM_BUTTON_SIZE;
    }

    private boolean isHomeButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getHomeButtonX();
        int buttonY = getSettingsButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isClearButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getClearButtonX();
        int buttonY = getSettingsButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isImportExportButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getImportExportButtonX();
        int buttonY = getSettingsButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isMarketplaceButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getMarketplaceButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
    }

    private boolean isPublishButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getPublishButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isSettingsButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getSettingsButtonX();
        int buttonY = getSettingsButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isValidationButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getValidationButtonX();
        int buttonY = getValidationButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
    }

    private boolean isPointInPlayButton(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getPlayButtonX(), getPlayButtonY(), PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private boolean isPointInStopButton(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getStopButtonX(), getStopButtonY(), STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
    }

    private boolean isPointInZoomMinus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomMinusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private boolean isPointInZoomPlus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomPlusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private int getSettingsPopupX() {
        return (this.width - SETTINGS_POPUP_WIDTH) / 2;
    }

    private int getSettingsPopupHeight() {
        return Math.min(SETTINGS_POPUP_HEIGHT, Math.max(140, this.height - 24));
    }

    private int getSettingsPopupY() {
        return (this.height - getSettingsPopupHeight()) / 2;
    }

    private int[] getSettingsPopupBounds() {
        return settingsPopupAnimation.getScaledPopupBounds(this.width, this.height, SETTINGS_POPUP_WIDTH, getSettingsPopupHeight());
    }

    private int getAccentColor() {
        return accentOption != null ? accentOption.color : UITheme.ACCENT_DEFAULT;
    }

    private int mixColor(int color, int target, float ratio) {
        int a = (int) (((color >>> 24) & 0xFF) * (1.0f - ratio) + ((target >>> 24) & 0xFF) * ratio);
        int r = (int) (((color >>> 16) & 0xFF) * (1.0f - ratio) + ((target >>> 16) & 0xFF) * ratio);
        int g = (int) (((color >>> 8) & 0xFF) * (1.0f - ratio) + ((target >>> 8) & 0xFF) * ratio);
        int b = (int) ((color & 0xFF) * (1.0f - ratio) + (target & 0xFF) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void openSettingsPopup() {
        dismissParameterOverlay();
        closeInfoPopup();
        clearPopupAnimation.hide();
        importExportPopupAnimation.hide();
        presetDropdownOpen = false;
        languageDropdownOpen = false;
        languageDropdownAnimation.setValue(0f);
        Node selectedNode = nodeGraph != null ? nodeGraph.getSelectedNode() : null;
        if (supportsNodeSettings(selectedNode)) {
            settingsNodeListView = false;
            settingsNodeTargetType = selectedNode.getType();
            settingsNodeTarget = selectedNode;
        } else {
            settingsNodeListView = true;
            settingsNodeTargetType = null;
            settingsNodeTarget = null;
        }
        settingsNodeListScrollOffset = 0;
        settingsPopupScrollOffset = 0;
        settingsPopupAnimation.show();
    }

    private void closeSettingsPopup() {
        languageDropdownOpen = false;
        nodeDelayDragging = false;
        createListRadiusDragging = false;
        if (createListRadiusField != null) {
            createListRadiusField.setFocused(false);
            createListRadiusField.setVisible(false);
        }
        settingsNodeListView = true;
        settingsNodeTargetType = null;
        settingsNodeTarget = null;
        settingsNodeListScrollOffset = 0;
        settingsPopupScrollOffset = 0;
        settingsPopupAnimation.hide();
    }

    private boolean handleSettingsPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupX = getSettingsPopupX();
        int popupY = getSettingsPopupY();
        int popupHeight = getSettingsPopupHeight();
        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int contentPopupY = popupY - settingsPopupScrollOffset;
        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
        boolean bodyHovered = isPointInRect(mouseXi, mouseYi, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3]);

        if (!settingsNodeListView && getEffectiveSettingsTargetType() != null) {
            int nodeSettingsLabelY = getSettingsNodeSectionBodyY(contentPopupY) - 14;
            int backX = popupX + 20;
            int backY = nodeSettingsLabelY - 3;
            if (isPointInRect(mouseXi, mouseYi, backX, backY, SETTINGS_BACK_BUTTON_WIDTH, SETTINGS_BACK_BUTTON_HEIGHT)) {
                settingsNodeListView = true;
                settingsNodeTargetType = null;
                settingsNodeTarget = null;
                return true;
            }
        }

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight)) {
            closeSettingsPopup();
            return true;
        }

        int contentX = popupX + 20;

        // Language dropdown click
        int languageLabelY = contentPopupY + 44;
        int languageButtonY = languageLabelY + 12;
        int languageButtonWidth = SETTINGS_POPUP_WIDTH - 40;

        if (bodyHovered && mouseXi >= contentX && mouseXi <= contentX + languageButtonWidth && mouseYi >= languageButtonY && mouseYi <= languageButtonY + 20) {
            languageDropdownOpen = !languageDropdownOpen;
            return true;
        }

        // Language dropdown options click
        if (languageDropdownOpen) {
            int dropdownY = languageButtonY + 22;
            for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
                if (bodyHovered && mouseXi >= contentX && mouseXi <= contentX + languageButtonWidth &&
                    mouseYi >= dropdownY + (i * 20) && mouseYi <= dropdownY + (i * 20) + 20) {
                    onLanguageSelected(SUPPORTED_LANGUAGES[i]);
                    return true;
                }
            }
        }

        // Adjust accentOptionsY to match renderSettingsPopup
        int accentLabelY = languageButtonY + 50;
        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            if (bodyHovered && isPointInRect(mouseXi, mouseYi, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT)) {
                accentOption = option;
                currentSettings.accentColor = getAccentOptionString(accentOption);
                SettingsManager.save(currentSettings);
                return true;
            }
            optionIndex++;
        }

        int sectionDividerY = accentOptionsY + SETTINGS_OPTION_HEIGHT + 10;
        int settingDividerY = sectionDividerY + 22;
        int gridRowCenterY = (sectionDividerY + settingDividerY) / 2;
        int gridToggleX = popupX + SETTINGS_POPUP_WIDTH - SETTINGS_TOGGLE_WIDTH - 20;
        int gridToggleY = gridRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, gridToggleX, gridToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showGrid = !showGrid;
            currentSettings.showGrid = showGrid;
            SettingsManager.save(currentSettings);
            return true;
        }

        int footerDividerY = settingDividerY + 22;
        int tooltipRowCenterY = (settingDividerY + footerDividerY) / 2;
        int tooltipToggleX = gridToggleX;
        int tooltipToggleY = tooltipRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, tooltipToggleX, tooltipToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showWorkspaceTooltips = !showWorkspaceTooltips;
            currentSettings.showTooltips = showWorkspaceTooltips;
            SettingsManager.save(currentSettings);
            return true;
        }

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        int chatToggleX = gridToggleX;
        int chatToggleY = chatRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, chatToggleX, chatToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showChatErrors = !showChatErrors;
            currentSettings.showChatErrors = showChatErrors;
            SettingsManager.save(currentSettings);
            return true;
        }

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        int overlayToggleX = gridToggleX;
        int overlayToggleY = overlayRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, overlayToggleX, overlayToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showHudOverlays = !showHudOverlays;
            currentSettings.showHudOverlays = showHudOverlays;
            SettingsManager.save(currentSettings);
            return true;
        }

        int delayDividerY = overlayDividerY + 26;
        int delayRowCenterY = (overlayDividerY + delayDividerY) / 2;
        int sliderX = popupX + SETTINGS_POPUP_WIDTH - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = delayRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
        String delayText = nodeDelayField != null ? nodeDelayField.getText() : Integer.toString(nodeDelayMs);
        int[] valueBox = getNodeDelayFieldBounds(popupX, SETTINGS_POPUP_WIDTH, delayRowCenterY, delayText);
        int valueBoxX = valueBox[0];
        int valueBoxY = valueBox[1];
        int valueBoxWidth = valueBox[2];
        int valueBoxHeight = valueBox[3];
        if (nodeDelayField != null) {
            if (bodyHovered && isPointInRect(mouseXi, mouseYi, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight)) {
                nodeDelayField.setEditable(true);
                nodeDelayField.setFocused(true);
                return true;
            } else if (nodeDelayField.isFocused()) {
                nodeDelayField.setFocused(false);
            }
        }
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
            nodeDelayDragging = true;
            updateNodeDelayFromMouse(mouseXi, popupX, SETTINGS_POPUP_WIDTH);
            return true;
        }

        int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY);
        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(
            popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsContentY);
        if (isPointInRect(mouseXi, mouseYi, clearCacheButtonBounds[0], clearCacheButtonBounds[1],
            clearCacheButtonBounds[2], clearCacheButtonBounds[3])) {
            clearSettingsCache();
            return true;
        }

        if (bodyHovered && settingsNodeListView) {
            List<NodeType> settingsNodes = getSettingsNodeTypes();
            int[] listBounds = getSettingsNodeListBounds(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsContentY);
            int visibleRows = Math.max(1, listBounds[3] / SETTINGS_NODE_LIST_ROW_HEIGHT);
            int maxScroll = Math.max(0, settingsNodes.size() - visibleRows);
            settingsNodeListScrollOffset = MathHelper.clamp(settingsNodeListScrollOffset, 0, maxScroll);
            int startIndex = settingsNodeListScrollOffset;
            int endIndex = Math.min(settingsNodes.size(), startIndex + visibleRows + 1);
            for (int i = startIndex; i < endIndex; i++) {
                int rowY = listBounds[1] + (i - startIndex) * SETTINGS_NODE_LIST_ROW_HEIGHT;
                if (isPointInRect(mouseXi, mouseYi, listBounds[0], rowY, listBounds[2], SETTINGS_NODE_LIST_ROW_HEIGHT)) {
                    NodeType targetType = settingsNodes.get(i);
                    Node targetNode = findFirstNodeWithSettingsType(targetType);
                    settingsNodeTargetType = targetType;
                    settingsNodeTarget = targetNode;
                    settingsNodeListView = false;
                    if (nodeGraph != null && targetNode != null) {
                        nodeGraph.selectNode(targetNode);
                    }
                    return true;
                }
            }
        }

        NodeType selectedType = getEffectiveSettingsTargetType();
        if (bodyHovered && !settingsNodeListView && selectedType == NodeType.GOTO) {
            int gotoBreakDividerY = nodeSettingsContentY + 28;
            int gotoBreakRowCenterY = (nodeSettingsContentY + 10 + gotoBreakDividerY) / 2;
            int gotoToggleX = gridToggleX;
            int gotoBreakToggleY = gotoBreakRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (isPointInRect(mouseXi, mouseYi, gotoToggleX, gotoBreakToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                currentSettings.gotoAllowBreakWhileExecuting = !Boolean.TRUE.equals(currentSettings.gotoAllowBreakWhileExecuting);
                SettingsManager.save(currentSettings);
                return true;
            }

            int gotoPlaceDividerY = gotoBreakDividerY + 22;
            int gotoPlaceRowCenterY = (gotoBreakDividerY + gotoPlaceDividerY) / 2;
            int gotoPlaceToggleY = gotoPlaceRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (isPointInRect(mouseXi, mouseYi, gotoToggleX, gotoPlaceToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                currentSettings.gotoAllowPlaceWhileExecuting = !Boolean.TRUE.equals(currentSettings.gotoAllowPlaceWhileExecuting);
                SettingsManager.save(currentSettings);
                return true;
            }
        } else if (bodyHovered && !settingsNodeListView && selectedType == NodeType.SENSOR_KEY_PRESSED) {
            int keyPressedDividerY = nodeSettingsContentY + 28;
            int keyPressedRowCenterY = (nodeSettingsContentY + 10 + keyPressedDividerY) / 2;
            int keyPressedToggleX = gridToggleX;
            int keyPressedToggleY = keyPressedRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (isPointInRect(mouseXi, mouseYi, keyPressedToggleX, keyPressedToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                currentSettings.keyPressedActivatesInGuis = !(currentSettings.keyPressedActivatesInGuis == null
                    || currentSettings.keyPressedActivatesInGuis);
                SettingsManager.save(currentSettings);
                return true;
            }
        } else if (bodyHovered && !settingsNodeListView && selectedType == NodeType.CREATE_LIST) {
            Node targetNode = getEffectiveSettingsTargetNode();
            int createListToggleDividerY = nodeSettingsContentY + 28;
            int createListToggleRowCenterY = (nodeSettingsContentY + 10 + createListToggleDividerY) / 2;
            int createListToggleX = gridToggleX;
            int createListToggleY = createListToggleRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
            if (isPointInRect(mouseXi, mouseYi, createListToggleX, createListToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
                setCreateListCustomRadiusEnabled(targetNode, !isCreateListCustomRadiusEnabled(targetNode));
                return true;
            }

            if (isCreateListCustomRadiusEnabled(targetNode)) {
                int createListRadiusDividerY = createListToggleDividerY + 26;
                int createListRadiusRowCenterY = (createListToggleDividerY + createListRadiusDividerY) / 2;
                int createListSliderX = popupX + SETTINGS_POPUP_WIDTH - SETTINGS_SLIDER_WIDTH - 20;
                int createListSliderY = createListRadiusRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
                String radiusText = createListRadiusField != null ? createListRadiusField.getText() : Integer.toString(getCreateListSettingsRadius(targetNode));
                int[] radiusValueBox = getCreateListRadiusFieldBounds(popupX, SETTINGS_POPUP_WIDTH, createListRadiusRowCenterY, radiusText);
                if (createListRadiusField != null) {
                    if (bodyHovered && isPointInRect(mouseXi, mouseYi, radiusValueBox[0], radiusValueBox[1], radiusValueBox[2], radiusValueBox[3])) {
                        createListRadiusField.setEditable(true);
                        createListRadiusField.setFocused(true);
                        return true;
                    } else if (createListRadiusField.isFocused()) {
                        createListRadiusField.setFocused(false);
                    }
                }
                if (isPointInRect(mouseXi, mouseYi, createListSliderX, createListSliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
                    createListRadiusDragging = true;
                    updateCreateListRadiusFromMouse(targetNode, mouseXi, popupX, SETTINGS_POPUP_WIDTH);
                    return true;
                }
            }
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = popupX + SETTINGS_POPUP_WIDTH - buttonWidth - 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        if (isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeSettingsPopup();
            return true;
        }

        return true;
    }

    private void startExecutingAllGraphs() {
        GraphValidationResult validationResult = nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);
        if (validationResult.hasErrors()) {
            validationPanelOpen = true;
            return;
        }
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        nodeGraph.save();
        ExecutionManager.getInstance().executeGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void stopExecutingAllGraphs() {
        ExecutionManager.getInstance().requestStopAll();
    }

    private void drawLanguageDropdown(DrawContext context, int x, int y, int width, String currentLang, boolean hovered) {
        // Update dropdown animation
        languageDropdownAnimation.animateTo(languageDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        languageDropdownAnimation.tick();

        int bgColor = hovered
            ? (languageDropdownOpen ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG_HOVER)
            : (languageDropdownOpen ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG);
        bgColor = settingsPopupAnimation.getAnimatedPopupColor(bgColor);

        int borderColor = getAnimatedBorderColor("settings-language-dropdown", hovered || languageDropdownOpen, UITheme.BORDER_DEFAULT, getAccentColor());
        borderColor = settingsPopupAnimation.getAnimatedPopupColor(borderColor);
        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            y,
            width,
            20,
            bgColor,
            borderColor,
            settingsPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        // Draw dropdown text
        int labelColor = (hovered || languageDropdownOpen) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        labelColor = settingsPopupAnimation.getAnimatedPopupColor(labelColor);
        context.drawTextWithShadow(this.textRenderer, Text.literal(currentLang), x + 4, y + 6, labelColor);

        int arrowCenterX = x + width - 10;
        int arrowCenterY = y + 10;
        UIStyleHelper.drawChevron(context, arrowCenterX, arrowCenterY, languageDropdownOpen, labelColor);
    }

    private void drawLanguageDropdownOptions(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
        // Get animation progress
        float animProgress = AnimationHelper.easeOutQuad(languageDropdownAnimation.getValue());

        // Don't render options if animation is fully closed
        if (animProgress <= 0.001f) {
            return;
        }

        Object matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 550.0f);

        int dropdownY = y + 22;
        int fullOptionsHeight = SUPPORTED_LANGUAGES.length * 20;
        int animatedHeight = (int) (fullOptionsHeight * animProgress);

        // Use scissor to clip the dropdown content during animation
        context.enableScissor(x, dropdownY, x + width, dropdownY + animatedHeight);

        UIStyleHelper.drawBeveledPanel(
            context,
            x,
            dropdownY,
            width,
            fullOptionsHeight,
            settingsPopupAnimation.getAnimatedPopupColor(UITheme.BACKGROUND_SECONDARY),
            settingsPopupAnimation.getAnimatedPopupColor(UITheme.BORDER_DEFAULT),
            settingsPopupAnimation.getAnimatedPopupColor(UITheme.PANEL_INNER_BORDER)
        );

        // Draw each language option
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
            String lang = SUPPORTED_LANGUAGES[i];
            String langName = getLanguageDisplayName(lang);
            int optionY = dropdownY + (i * 20);

            // Only allow hover detection when animation is complete
            boolean optionHovered = animProgress >= 1f && mouseX >= x && mouseX <= x + width && mouseY >= optionY && mouseY <= optionY + 20;
            int optionBg = optionHovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
            optionBg = settingsPopupAnimation.getAnimatedPopupColor(optionBg);
            context.fill(x + 1, optionY + 1, x + width - 1, optionY + 20, optionBg);

            // Highlight current language with accent color
            String currentLang = this.client.getLanguageManager().getLanguage();
            int textColor = lang.equals(currentLang) ? getAccentColor() : UITheme.TEXT_PRIMARY;
            textColor = settingsPopupAnimation.getAnimatedPopupColor(textColor);
            context.drawTextWithShadow(this.textRenderer, Text.literal(langName), x + 4, optionY + 6, textColor);
        }

        DrawContextBridge.flush(context);
        DrawContextBridge.flush(context);
        context.disableScissor();
        MatrixStackBridge.pop(matrices);
    }

    private String getLanguageDisplayName(String languageCode) {
        return Text.translatable("pathmind.language." + languageCode).getString();
    }

    private void onLanguageSelected(String languageCode) {
        // Save to settings first
        currentSettings.language = languageCode;
        SettingsManager.save(currentSettings);

        // Update Minecraft's language and reload resources
        this.client.options.language = languageCode;
        this.client.getLanguageManager().setLanguage(languageCode);
        this.client.options.write();
        this.client.reloadResources();

        // Reload the screen to update all text
        this.client.setScreen(null);
        this.client.setScreen(new PathmindVisualEditorScreen());
    }

    private void drawPencilIcon(DrawContext context, int x, int y, int color) {
        int size = PRESET_RENAME_ICON_SIZE;
        for (int offset = 0; offset < size - 2; offset++) {
            int startX = x + offset;
            int startY = y + size - 3 - offset;
            context.fill(startX, startY, startX + 1, startY + 2, color);
        }

        int tipColor = (color & 0x00FFFFFF) | 0x66000000;
        context.fill(x + size - 3, y, x + size - 1, y + 2, tipColor);

        int eraserColor = (color & 0x00FFFFFF) | 0x88000000;
        context.fill(x, y + size - 1, x + 2, y + size, eraserColor);
    }

    private void drawTrashIcon(DrawContext context, int x, int y, int color) {
        int handleWidth = Math.max(2, PRESET_DELETE_ICON_SIZE / 2);
        int handleLeft = x + (PRESET_DELETE_ICON_SIZE - handleWidth) / 2;
        context.fill(handleLeft, y, handleLeft + handleWidth, y + 1, color);

        context.fill(x, y + 1, x + PRESET_DELETE_ICON_SIZE, y + 3, color);
        context.fill(x + 1, y + 3, x + PRESET_DELETE_ICON_SIZE - 1, y + PRESET_DELETE_ICON_SIZE, color);

        int slatColor = (color & 0x00FFFFFF) | 0x66000000;
        context.fill(x + 2, y + 4, x + 3, y + PRESET_DELETE_ICON_SIZE - 1, slatColor);
        context.fill(x + PRESET_DELETE_ICON_SIZE - 3, y + 4, x + PRESET_DELETE_ICON_SIZE - 2, y + PRESET_DELETE_ICON_SIZE - 1, slatColor);
    }

    private void drawCloseXIcon(DrawContext context, int x, int y, int size, int color) {
        int span = Math.max(4, size);
        for (int i = 0; i < span; i++) {
            context.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            context.fill(x + (span - 1 - i), y + i, x + (span - i), y + i + 1, color);
        }
    }

    private boolean isTitleClicked(int mouseX, int mouseY) {
        return isTitleHovered(mouseX, mouseY);
    }

    private boolean isTitleHovered(int mouseX, int mouseY) {
        int textHeight = this.textRenderer.fontHeight;
        int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
        int textX = getTitleTextX();
        int textY = getTitleTextY() - 1;
        int hitboxX = textX - TITLE_INTERACTION_PADDING;
        int hitboxY = textY - TITLE_INTERACTION_PADDING;
        int hitboxWidth = textWidth + TITLE_INTERACTION_PADDING * 2;
        int hitboxHeight = textHeight + TITLE_INTERACTION_PADDING * 2;
        return isPointInRect(mouseX, mouseY, hitboxX, hitboxY, hitboxWidth, hitboxHeight);
    }

    private int getTitleTextX() {
        return this.width - 8 - this.textRenderer.getWidth(TITLE_TEXT);
    }

    private int getTitleTextY() {
        return (TITLE_BAR_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
    }

    private int getPresetBrowserButtonX() {
        return getTitleTextX() - PRESET_BROWSER_BUTTON_GAP - PRESET_BROWSER_BUTTON_SIZE;
    }

    private int getPresetBrowserButtonY() {
        return (TITLE_BAR_HEIGHT - PRESET_BROWSER_BUTTON_SIZE) / 2;
    }

    private boolean isPresetBrowserButtonHovered(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getPresetBrowserButtonX(), getPresetBrowserButtonY(),
            PRESET_BROWSER_BUTTON_SIZE, PRESET_BROWSER_BUTTON_SIZE);
    }

    private boolean isPresetBrowserButtonClicked(int mouseX, int mouseY) {
        return isPresetBrowserButtonHovered(mouseX, mouseY);
    }

    private String getModVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(PathmindMod.MOD_ID);
        return container.map(value -> value.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
    }

    private String getFabricLoaderVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("fabricloader");
        return container.map(value -> value.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
    }

    private String getCurrentMinecraftVersion() {
        return this.client != null ? this.client.getGameVersion() : "Unknown";
    }

    private boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

}
