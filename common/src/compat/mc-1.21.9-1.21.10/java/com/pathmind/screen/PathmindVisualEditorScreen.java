package com.pathmind.screen;

import com.pathmind.PathmindCommon;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.data.WorkspaceFileAccess;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindSettingsRowRenderer;
import com.pathmind.ui.control.PathmindValidationPanelRenderer;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.control.PathmindRoutineUi;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.control.UiHitTest;
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
import com.pathmind.util.PathmindI18n;
import com.pathmind.validation.GraphValidationIssue;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.VersionSupport;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.OverlayProtection;
import com.pathmind.util.UiUtilsProxy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The main visual editor screen for Pathmind.
 * This screen provides the interface for creating and editing node-based workflows.
 */
public class PathmindVisualEditorScreen extends Screen {
    static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }

    private static final int TITLE_BAR_HEIGHT = 20;
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
    private static final int PRESET_MENU_BUTTON_SIZE = 18;
    private static final int PRESET_TAB_TITLE_GAP = 0;
    private static final int PRESET_TAB_DRAG_THRESHOLD = 4;
    private static final int PRESET_CONTEXT_MENU_WIDTH = 132;
    private static final int PRESET_CONTEXT_MENU_ITEM_HEIGHT = 18;
    private static final int PRESET_CONTEXT_MENU_SEPARATOR_HEIGHT = 5;
    private static final String[] PRESET_GROUP_COLOR_KEYS = {"sky", "mint", "amber", "rose", "violet"};
    private static final int[] PRESET_GROUP_COLORS = {0xFF38BDF8, 0xFF34D399, 0xFFF59E0B, 0xFFFB7185, 0xFFA78BFA};
    private static final String PRESET_GROUP_TAB_PREFIX = "__pathmind_preset_group__:";
    private static final String ROUTINE_WORKSPACE_PREFIX = "__pathmind_routine__:";
    private static final String LIBRARY_ROUTINE_WORKSPACE_PREFIX = "__pathmind_library_routine__:";
    private static final int PRESET_GROUP_TAB_WIDTH = 10;
    private static final boolean IS_MAC_OS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("mac");
    
    // Colors now come from UITheme for consistency
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int MARKETPLACE_BUTTON_WIDTH = BOTTOM_BUTTON_SIZE * 3 + BOTTOM_BUTTON_SPACING * 2;
    private static final int PRESET_DROPDOWN_WIDTH = 220;
    private static final int PRESET_DROPDOWN_MARGIN = 6;
    private static final int PRESET_OPTION_HEIGHT = 18;
    private static final int PRESET_TEXT_LEFT_PADDING = 6;
    private static final int PRESET_DELETE_ICON_SIZE = 8;
    private static final int PRESET_DELETE_ICON_MARGIN = 6;
    private static final int PRESET_DELETE_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_RENAME_ICON_SIZE = 8;
    private static final int PRESET_RENAME_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_TEXT_ICON_GAP = 4;
    static final int CREATE_PRESET_POPUP_WIDTH = 320;
    static final int CREATE_PRESET_POPUP_HEIGHT = 170;
    private static final int PLAY_BUTTON_SIZE = 18;
    private static final int PLAY_BUTTON_MARGIN = 6;
    private static final int STOP_BUTTON_SIZE = 18;
    private static final int CONTROL_BUTTON_GAP = 6;
    private static final int VALIDATION_BUTTON_SIZE = 18;
    private static final int VALIDATION_PANEL_WIDTH = 292;
    private static final int VALIDATION_PANEL_MAX_VISIBLE_ROWS = 8;
    private static final int VALIDATION_PANEL_ROW_HEIGHT = 22;
    private static final int VALIDATION_PANEL_PADDING = 8;
    private static final int VALIDATION_PANEL_BOTTOM_PADDING = 2;
    private static final int VALIDATION_PANEL_HEADER_HEIGHT = 34;
    private static final int VALIDATION_PANEL_FOOTER_HEIGHT = 18;
    private static final int ZOOM_BUTTON_SIZE = 14;
    private static final int ZOOM_BUTTON_MARGIN = 6;
    private static final int ZOOM_BUTTON_SPACING = 4;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    static final int PRESET_DELETE_POPUP_WIDTH = 320;
    static final int PRESET_DELETE_POPUP_HEIGHT = 160;
    static final int PRESET_DELETE_SKIP_CHECKBOX_SIZE = 10;
    private static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    private static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    private static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    private static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;
    private static final String UI_UTILS_DOWNLOAD_URL = "https://ui-utils.com";
    private static final int SETTINGS_POPUP_WIDTH = 360;
    private static final int SETTINGS_POPUP_HEIGHT = 408;
    static final int SETTINGS_OPTION_WIDTH = 90;
    static final int SETTINGS_OPTION_HEIGHT = 16;
    static final int SETTINGS_OPTION_GAP = 6;
    static final int SETTINGS_TOGGLE_WIDTH = 60;
    static final int SETTINGS_TOGGLE_HEIGHT = 16;
    static final int SETTINGS_SLIDER_WIDTH = 160;
    static final int SETTINGS_SLIDER_HEIGHT = 6;
    static final int SETTINGS_SLIDER_HANDLE_WIDTH = 8;
    static final int SETTINGS_SLIDER_HANDLE_HEIGHT = 12;
    static final int SETTINGS_NODE_LIST_ROW_HEIGHT = 20;
    static final int SETTINGS_NODE_LIST_GAP = 6;
    private static final int SETTINGS_BACK_BUTTON_WIDTH = 52;
    private static final int SETTINGS_BACK_BUTTON_HEIGHT = 18;
    static final int SETTINGS_SECTION_BUTTON_WIDTH = 56;
    static final int SETTINGS_SECTION_BUTTON_HEIGHT = 20;
    static final int SETTINGS_NODE_TYPE_BUTTON_HEIGHT = 28;
    static final int SETTINGS_NODE_TYPE_BUTTON_GAP = 6;
    static final int SETTINGS_NODE_TYPE_SECTION_GAP = 10;
    static final int SETTINGS_NODE_TYPE_SELECTOR_MAX_HEIGHT = 102;
    static final int SETTINGS_NODE_TYPE_SEARCH_HEIGHT = 22;
    private static final int SETTINGS_NODE_TYPE_SEARCH_PADDING = 8;
    private static final int SETTINGS_NODE_TYPE_SEARCH_LIST_GAP = 8;
    static final int SETTINGS_NODE_TYPE_EMPTY_HEIGHT = 24;
    private static final long SETTINGS_SCROLL_GESTURE_TIMEOUT_MS = 180L;
    static final int CREATE_LIST_RADIUS_MIN = 1;
    static final int CREATE_LIST_RADIUS_MAX = 512;
    static final NodeType[] SETTINGS_NODE_TYPES = {
        NodeType.GOTO,
        NodeType.SENSOR_KEY_PRESSED,
        NodeType.CREATE_LIST
    };
    static final int NODE_DELAY_MIN_MS = 1;
    static final int NODE_DELAY_MAX_MS = 500;
    static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final int NODE_SEARCH_FIELD_WIDTH = 180;
    private static final int NODE_SEARCH_FIELD_HEIGHT = 22;
    private static final int NODE_SEARCH_DROPDOWN_TOP_GAP = 2;
    private static final int NODE_SEARCH_RESULT_HEIGHT = 18;
    private static final int NODE_SEARCH_MAX_RESULTS = 8;
    private static final int NODE_SEARCH_RESULT_TEXT_PADDING = 6;
    private static final String INFO_POPUP_AUTHOR = "soymods";
    private static final String INFO_POPUP_TARGET_VERSION = VersionSupport.SUPPORTED_RANGE;
    private static final Component TITLE_TEXT = Component.literal("Pathmind");
    private static final Component INFO_POPUP_TITLE_TEXT = Component.literal("Pathmind");

    NodeGraph nodeGraph;
    private Sidebar sidebar;
    private NodeParameterOverlay parameterOverlay;
    private BookTextEditorOverlay bookTextEditorOverlay;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;

    // Drag and drop state
    private boolean isDraggingFromSidebar = false;
    private boolean sidebarDragActivated = false;
    private int sidebarDragStartX = -1;
    private int sidebarDragStartY = -1;
    private NodeType draggingNodeType = null;
    private Node draggingSidebarNode = null;
    private boolean draggingFromRoutineLibrary = false;

    // Right-click context menu state
    private static final int CLICK_THRESHOLD = 5;  // pixels
    private static final long CLICK_TIME_THRESHOLD = 250;  // milliseconds
    private int rightClickStartX = -1;
    private int rightClickStartY = -1;
    private long rightClickStartTime = 0;
    private boolean cuttingConnections = false;
    private EditBox nodeSearchField;
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
    private boolean importExportBusy = false;

    private boolean presetDropdownOpen = false;
    private final AnimatedValue presetDropdownAnimation = AnimatedValue.forHover();
    private int presetDropdownScrollOffset = 0;
    private final DropdownLayoutHelper.SmoothScrollState presetDropdownSmoothScroll = new DropdownLayoutHelper.SmoothScrollState();
    private final AnimatedValue titleUnderlineAnimation = AnimatedValue.forHover();
    private final AnimatedValue routineWorkspaceAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
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
    private String pendingPresetDropdownDragName = null;
    private int pendingPresetDropdownPressMouseX = 0;
    private int pendingPresetDropdownPressMouseY = 0;
    private String draggingPresetDropdownName = null;
    private int draggingPresetDropdownCurrentX = 0;
    private int draggingPresetDropdownCurrentY = 0;
    private boolean presetContextMenuOpen = false;
    private int presetContextMenuX = 0;
    private int presetContextMenuY = 0;
    private String presetContextMenuPresetName = "";
    private String presetContextMenuGroupKey = "";
    private String animatingPresetDeletionName = null;
    private long animatingPresetDeletionExecuteAtMs = 0L;
    private String activePresetName = "";
    final PopupAnimationHandler createPresetPopupAnimation = new PopupAnimationHandler();
    EditBox createPresetField;
    String createPresetStatus = "";
    int createPresetStatusColor = UITheme.TEXT_SECONDARY;
    boolean createRoutineNaming = false;
    String pendingRoutineRenameId = "";
    String pendingLibraryRoutineRenameId = "";
    final PopupAnimationHandler renamePresetPopupAnimation = new PopupAnimationHandler();
    EditBox renamePresetField;
    private EditBox inlinePresetRenameField;
    String renamePresetStatus = "";
    int renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    String pendingPresetRenameName = "";
    private String inlinePresetRenameName = "";
    private long lastPresetTitleClickTime = 0L;
    private String lastPresetTitleClickName = "";
    private final PopupAnimationHandler infoPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler presetDeletePopupAnimation = new PopupAnimationHandler();
    String pendingPresetDeletionName = "";
    private final PopupAnimationHandler missingBaritonePopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingUiUtilsPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler settingsPopupAnimation = new PopupAnimationHandler();
    private final PathmindSettingsPopupController settingsPopupController = new PathmindSettingsPopupController(this);
    private final PathmindPresetPopupController presetPopupController = new PathmindPresetPopupController(this);
    private final AnimatedValue validationPanelAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private boolean validationPanelOpen = false;
    Settings currentSettings;
    private static final String[] SUPPORTED_LANGUAGES = {"en_us", "es_es", "pt_br", "ru_ru", "de_de", "fr_fr", "pl_pl"};
    private boolean languageDropdownOpen = false;
    private final AnimatedValue languageDropdownAnimation = AnimatedValue.forHover();
    int languageDropdownX = 0;
    int languageDropdownY = 0;
    int languageDropdownWidth = 0;
    int languageDropdownClipX = 0;
    int languageDropdownClipY = 0;
    int languageDropdownClipWidth = 0;
    int languageDropdownClipHeight = 0;
    boolean showGrid = true;
    boolean showWorkspaceTooltips = true;
    boolean showChatErrors = true;
    boolean showHudOverlays = true;
    boolean skipPresetDeleteConfirm = false;
    int nodeDelayMs = 150;
    boolean nodeDelayDragging = false;
    boolean createListRadiusDragging = false;
    EditBox nodeDelayField;
    EditBox createListRadiusField;
    EditBox settingsNodeSearchField;
    boolean settingsNodeListView = true;
    NodeType settingsNodeTargetType = null;
    Node settingsNodeTarget = null;
    int settingsNodeListScrollOffset = 0;
    int settingsNodeSelectorScrollOffset = 0;
    int settingsPopupScrollOffset = 0;
    private long settingsLastScrollEventMs = 0L;
    private int settingsLastScrollConsumer = 0;
    boolean settingsNodeSelectorScrollDragging = false;
    int settingsNodeSelectorScrollDragOffset = 0;
    boolean settingsPopupScrollDragging = false;
    int settingsPopupScrollDragOffset = 0;
    AccentOption accentOption = AccentOption.SKY;
    private boolean overlayCutoutActive = false;
    private int overlayCutoutX = 0;
    private int overlayCutoutY = 0;
    private int overlayCutoutWidth = 0;
    private int overlayCutoutHeight = 0;
    private Boolean uiUtilsOverlayPrevEnabled = null;
    private final List<WorkspaceTab> workspaceTabs = new ArrayList<>();
    private int activeWorkspaceTabIndex = 0;
    private boolean systemCursorHidden = false;

    private static final class WorkspaceTab {
        private String label;
        private NodeGraphData graphData;
        private final Integer parentTabIndex;
        private final String hostTemplateNodeId;
        private final NodeGraphData.RoutineDefinitionData libraryRoutineDefinition;

        private WorkspaceTab(String label, NodeGraphData graphData, Integer parentTabIndex, String hostTemplateNodeId) {
            this(label, graphData, parentTabIndex, hostTemplateNodeId, null);
        }

        private WorkspaceTab(String label, NodeGraphData graphData, Integer parentTabIndex, String hostTemplateNodeId,
                             NodeGraphData.RoutineDefinitionData libraryRoutineDefinition) {
            this.label = label;
            this.graphData = graphData;
            this.parentTabIndex = parentTabIndex;
            this.hostTemplateNodeId = hostTemplateNodeId;
            this.libraryRoutineDefinition = libraryRoutineDefinition;
        }
    }

    private static final class NodeSearchResult {
        private final NodeType nodeType;
        final String label;
        private final String categoryLabel;
        private final int score;
        private final NodeGraphData.RoutineDefinitionData routine;

        private NodeSearchResult(NodeType nodeType, String label, String categoryLabel, int score) {
            this(nodeType, label, categoryLabel, score, null);
        }

        private NodeSearchResult(NodeType nodeType, String label, String categoryLabel, int score,
                                 NodeGraphData.RoutineDefinitionData routine) {
            this.nodeType = nodeType;
            this.label = label;
            this.categoryLabel = categoryLabel;
            this.score = score;
            this.routine = routine;
        }
    }

    enum AccentOption {
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

    public PathmindVisualEditorScreen() {
        super(Component.translatable("screen.pathmind.visual_editor.title"));
        this.baritoneAvailable = BaritoneDependencyChecker.isBaritoneApiPresent();
        this.uiUtilsAvailable = UiUtilsProxy.isAvailable();
        this.nodeGraph = new NodeGraph();
        this.nodeGraph.setWorkspaceSaveHandler(this::saveRootPresetWorkspace);
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
        this.nodeDelayMs = Mth.clamp(
            currentSettings.nodeDelayMs != null ? currentSettings.nodeDelayMs : 150,
            NODE_DELAY_MIN_MS,
            NODE_DELAY_MAX_MS
        );
        currentSettings.nodeDelayMs = this.nodeDelayMs;
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
        ensureCustomCursorHidden();
        if (uiUtilsOverlayPrevEnabled == null) {
            uiUtilsOverlayPrevEnabled = UiUtilsProxy.setOverlayEnabled(false);
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);

        if (createPresetField == null) {
            createPresetField = PathmindTextField.createInactive(this.font, 0, 0, 200, 20, Component.translatable("pathmind.field.presetName"), 64);
            createPresetField.setResponder(value -> clearCreatePresetStatus());
            this.addWidget(createPresetField);
        }

        if (renamePresetField == null) {
            renamePresetField = PathmindTextField.createInactive(this.font, 0, 0, 200, 20, Component.translatable("pathmind.field.newPresetName"), 64);
            renamePresetField.setResponder(value -> clearRenamePresetStatus());
            this.addWidget(renamePresetField);
        }
        if (inlinePresetRenameField == null) {
            inlinePresetRenameField = PathmindTextField.createInactive(this.font, 0, 0, 200, 20, Component.translatable("pathmind.field.newPresetName"), 64);
            this.addWidget(inlinePresetRenameField);
        }
        if (nodeDelayField == null) {
            nodeDelayField = PathmindTextField.createInactive(this.font, 0, 0, 120, 20, Component.translatable("pathmind.field.delay"), 6);
            nodeDelayField.setTextColor(UITheme.TEXT_HEADER);
            nodeDelayField.setTextColorUneditable(UITheme.TEXT_HEADER);
            ((PathmindTextField) nodeDelayField).setPathmindFilter(value -> value == null || value.isEmpty() || value.chars().allMatch(Character::isDigit));
            nodeDelayField.setResponder(value -> {
                Integer parsed = parseDelayFieldValue(value);
                if (parsed != null && parsed != nodeDelayMs) {
                    nodeDelayMs = parsed;
                    currentSettings.nodeDelayMs = nodeDelayMs;
                    SettingsManager.save(currentSettings);
                }
            });
            this.addWidget(nodeDelayField);
        }
        if (createListRadiusField == null) {
            createListRadiusField = PathmindTextField.createInactive(this.font, 0, 0, 120, 20, Component.translatable("pathmind.field.radius"), 6);
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
            this.addWidget(createListRadiusField);
        }
        if (nodeSearchField == null) {
            nodeSearchField = PathmindTextField.createInactive(this.font, 0, 0, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT, Component.translatable("pathmind.search.nodes"), 64);
            nodeSearchField.setHeight(Math.max(10, NODE_SEARCH_FIELD_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2));
            nodeSearchField.setResponder(value -> updateNodeSearchMatch());
            this.addWidget(nodeSearchField);
        }
        if (settingsNodeSearchField == null) {
            settingsNodeSearchField = PathmindTextField.createInactive(this.font, 0, 0, NODE_SEARCH_FIELD_WIDTH, SETTINGS_NODE_TYPE_SEARCH_HEIGHT, Component.translatable("pathmind.search.nodeSettings"), 64);
            settingsNodeSearchField.setSuggestion(tr("pathmind.search.nodeSettings"));
            settingsNodeSearchField.setHeight(Math.max(10, SETTINGS_NODE_TYPE_SEARCH_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2));
            settingsNodeSearchField.setResponder(value -> settingsNodeSelectorScrollOffset = 0);
            this.addWidget(settingsNodeSearchField);
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
        ExecutionManager.getInstance().setWorkspaceGraph(
            nodeGraph.getNodes(), nodeGraph.getConnections(), nodeGraph.getRoutineDefinitions());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        OverlayProtection.setPathmindRendering(true);
        try {
        recoverStaleLeftMouseDrag(mouseX, mouseY);
        resetOverlayCutout();
        context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);

        boolean titleHovered = isTitleHovered(mouseX, mouseY);
        boolean titleActive = titleHovered || presetDropdownOpen;
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
        refreshRoutineSidebarContext();
        sidebar.setRoutineDragState(isDraggingFromSidebar && sidebarDragActivated
            && draggingSidebarNode != null && draggingSidebarNode.getType() == NodeType.ROUTINE_CALL,
            draggingFromRoutineLibrary);
        sidebar.render(
            context,
            this.font,
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
        int chromeMouseX = controlsDisabled ? Integer.MIN_VALUE : mouseX;
        int chromeMouseY = controlsDisabled ? Integer.MIN_VALUE : mouseY;
        if (controlsDisabled && validationPanelOpen) {
            validationPanelOpen = false;
        }
        renderZoomControls(context, chromeMouseX, chromeMouseY, false);

        if (shouldShowExecutionControls()) {
            renderRoutineWorkspaceExitButton(context, chromeMouseX, chromeMouseY);
            renderStopButton(context, chromeMouseX, chromeMouseY, false);
            renderPlayButton(context, chromeMouseX, chromeMouseY, false);
        }
        renderValidationPanel(context, mouseX, mouseY, validationResult);
        renderValidationButton(context, chromeMouseX, chromeMouseY, false, validationResult);
        renderSettingsButton(context, chromeMouseX, chromeMouseY, false);
        renderPresetDropdown(context, mouseX, mouseY, controlsDisabled);

        if (controlsDisabled) {
            DrawContextBridge.startNewRootLayer(context);
        }

        // Render parameter overlay if visible
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.render(context, this.font, mouseX, mouseY, delta);
        }

        // Render book text editor overlay if visible
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.render(context, this.font, mouseX, mouseY, delta);
        }

        if (clearPopupAnimation.isVisible()) {
            renderClearConfirmationPopup(context, mouseX, mouseY);
        }

        if (importExportPopupAnimation.isVisible()) {
            renderImportExportPopup(context, mouseX, mouseY, delta);
        }

        if (createPresetPopupAnimation.isVisible()) {
            presetPopupController.renderCreatePresetPopup(context, mouseX, mouseY, delta);
        }

        if (renamePresetPopupAnimation.isVisible()) {
            presetPopupController.renderRenamePresetPopup(context, mouseX, mouseY, delta);
        }

        if (presetDeletePopupAnimation.isVisible()) {
            presetPopupController.renderPresetDeletePopup(context, mouseX, mouseY);
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
            settingsPopupController.renderSettingsPopup(context, mouseX, mouseY);
        }

        if (!isScreenPopupVisible()) {
            setOverlayCutoutForNodeOverlay();
        }

        renderPopupScrimOverlay(context);

        // Render language dropdown options on top of scrim overlay
        if (settingsPopupAnimation.isVisible()) {
            RenderStateBridge.setShaderColor(1f, 1f, 1f, settingsPopupAnimation.getPopupAlpha());
            drawLanguageDropdownOptions(context, languageDropdownX, languageDropdownY, languageDropdownWidth, mouseX, mouseY);
        }

        // Render context menu on top of everything
        if (presetContextMenuOpen) {
            nodeGraph.closeNodeContextMenu();
            nodeGraph.closeContextMenu();
        } else {
            nodeGraph.updateNodeContextMenuHover(mouseX, mouseY);
            nodeGraph.renderNodeContextMenu(context, this.font);
            nodeGraph.renderStartModeDropdown(context, this.font, mouseX, mouseY);
            nodeGraph.updateContextMenuHover(mouseX, mouseY);
            nodeGraph.renderContextMenu(context, this.font, mouseX, mouseY);
        }
        renderPresetContextMenu(context, mouseX, mouseY);
        renderNodeSearchField(context, mouseX, mouseY, delta);
        DrawContextBridge.startNewRootLayer(context);
        renderDraggedWorkspaceLayer(context, mouseX, mouseY, delta);
        if (isDraggingFromSidebar && sidebarDragActivated && (draggingNodeType != null || draggingSidebarNode != null)) {
            renderDraggingNode(context, mouseX, mouseY);
        }
        renderDraggedPresetDropdownTab(context, mouseX, mouseY);
        DrawContextBridge.startNewRootLayer(context);
        NodeErrorNotificationOverlay.getInstance().render(context, this.font, this.width, this.height);
        if (currentSettings != null && Boolean.TRUE.equals(currentSettings.showProfilerOverlay)) {
            DrawContextBridge.startNewRootLayer(context);
            nodeGraph.renderProfilerOverlay(context, this.font);
        }
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            DrawContextBridge.startNewRootLayer(context);
            nodeGraph.renderScreenCoordinateCaptureOverlay(context, this.font, mouseX, mouseY);
        }
        DrawContextBridge.startNewRootLayer(context);
        renderCustomCursor(context, mouseX, mouseY);
        } finally {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    private void ensureCustomCursorHidden() {
        if (systemCursorHidden) {
            return;
        }
        PathmindCursor.hideSystemCursor(this.minecraft != null ? this.minecraft : Minecraft.getInstance());
        systemCursorHidden = true;
    }

    private void restoreSystemCursor() {
        if (!systemCursorHidden) {
            return;
        }
        PathmindCursor.showSystemCursor(this.minecraft != null ? this.minecraft : Minecraft.getInstance());
        systemCursorHidden = false;
    }

    private void renderCustomCursor(GuiGraphics context, int mouseX, int mouseY) {
        NodeGraph.StickyNoteResizeCorner resizeCorner = getHoveredStickyNoteResizeCorner(mouseX, mouseY);
        PathmindCursor.render(context, resolveCursorTexture(mouseX, mouseY, resizeCorner), mouseX, mouseY);
    }

    private Identifier resolveCursorTexture(int mouseX, int mouseY) {
        return resolveCursorTexture(mouseX, mouseY, getHoveredStickyNoteResizeCorner(mouseX, mouseY));
    }

    private Identifier resolveCursorTexture(int mouseX, int mouseY, NodeGraph.StickyNoteResizeCorner resizeCorner) {
        if (nodeGraph.isConnectionCutActive()) {
            return PathmindCursor.CUT_TEXTURE;
        }
        if ((isDraggingFromSidebar && sidebarDragActivated)
            || nodeGraph.isAnyNodeBeingDragged()
            || nodeGraph.isPanning()) {
            return PathmindCursor.GRABBING_TEXTURE;
        }

        boolean overWorkspace = mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT;
        if (overWorkspace && InputCompatibilityBridge.hasControlDown()) {
            return PathmindCursor.CUT_TEXTURE;
        }
        if (sidebar.isHoveringNode()) {
            NodeType hoveredType = sidebar.getHoveredNodeType();
            if (shouldBlockBaritoneNode(hoveredType) || shouldBlockUiUtilsNode(hoveredType)) {
                return PathmindCursor.DISABLED_TEXTURE;
            }
            return PathmindCursor.GRAB_TEXTURE;
        }
        Node hoveredNode = overWorkspace ? nodeGraph.getNodeAt(mouseX, mouseY) : null;
        if (resizeCorner != null) {
            return switch (resizeCorner) {
                case TOP_LEFT -> PathmindCursor.SCALE_TOP_LEFT_TEXTURE;
                case TOP_RIGHT -> PathmindCursor.SCALE_TOP_RIGHT_TEXTURE;
                case BOTTOM_LEFT -> PathmindCursor.SCALE_BOTTOM_LEFT_TEXTURE;
                case BOTTOM_RIGHT -> PathmindCursor.SCALE_TEXTURE;
            };
        }
        if (hoveredNode != null && nodeGraph.isPointInsideInteractiveNodeControl(hoveredNode, mouseX, mouseY)) {
            return PathmindCursor.DEFAULT_TEXTURE;
        }
        if (overWorkspace && (hoveredNode != null
            || nodeGraph.getConnectionAt(mouseX, mouseY) != null)) {
            return PathmindCursor.GRAB_TEXTURE;
        }
        if (!overWorkspace) {
            return PathmindCursor.DEFAULT_TEXTURE;
        }

        return PathmindCursor.DEFAULT_TEXTURE;
    }

    private NodeGraph.StickyNoteResizeCorner getHoveredStickyNoteResizeCorner(int mouseX, int mouseY) {
        boolean overWorkspace = mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT;
        if (!overWorkspace) {
            return null;
        }
        Node hoveredNode = nodeGraph.getNodeAt(mouseX, mouseY);
        return hoveredNode == null ? null : nodeGraph.getStickyNoteResizeCornerAt(hoveredNode, mouseX, mouseY);
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
        return true;
    }

    private boolean handleNodeDoubleClickExecution(Node clickedNode) {
        if (clickedNode == null || this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
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
        this.minecraft.setScreen(null);
        return true;
    }

    private void recoverStaleLeftMouseDrag(int mouseX, int mouseY) {
        Minecraft client = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
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
            if (sidebarDragActivated && saveDraggedRoutineToLibrary(mouseX, mouseY)) {
                // Saved to the reusable routine catalogue.
            } else if (sidebarDragActivated && importDraggedLibraryRoutineToList(mouseX, mouseY)) {
                // Imported into this preset's routine list.
            } else if (sidebarDragActivated && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                Node newNode = dropDraggedSidebarNodeIntoWorkspace(mouseX, mouseY);
                if (newNode != null) {
                    nodeGraph.selectNode(newNode);
                }
            }
            isDraggingFromSidebar = false;
            sidebarDragActivated = false;
            draggingNodeType = null;
            draggingSidebarNode = null;
            draggingFromRoutineLibrary = false;
            nodeGraph.resetDropTargets();
            return;
        }
        nodeGraph.forceClearTransientDragState();
    }

    private void resetOverlayCutout() {
        overlayCutoutActive = false;
    }

    void setOverlayCutout(int x, int y, int width, int height) {
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

    private void renderPopupScrimOverlay(GuiGraphics context) {
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
    
    private void renderDraggingNode(GuiGraphics context, int mouseX, int mouseY) {
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
        int worldMouseX = nodeGraph.screenToWorldX(mouseX);
        int worldMouseY = nodeGraph.screenToWorldY(mouseY);
        int[] previewPosition = nodeGraph.getSidebarDragPreviewPosition(tempNode, worldMouseX, worldMouseY);
        int screenNodeX = nodeGraph.worldToScreenX(previewPosition[0]);
        int screenNodeY = nodeGraph.worldToScreenY(previewPosition[1]);

        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, scale, scale);

        int x = Math.round(screenNodeX / scale);
        int y = Math.round(screenNodeY / scale);

        // Update temp node position for rendering
        tempNode.setPosition(x, y);

        // Render the node with a slight transparency
        int alpha = 0x80;
        NodeType renderType = tempNode.getType();
        int nodeColor = (tempNode.getColor() & 0x00FFFFFF) | alpha;

        // Node background with transparency
        context.fill(x, y, x + width, y + height, UITheme.DRAG_PREVIEW_BG);
        // Draw grey outline for dragging state
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, UITheme.DRAG_PREVIEW_BORDER);

        // Node header
        if (renderType != NodeType.START && renderType != NodeType.EVENT_FUNCTION) {
            context.fill(x + 1, y + 1, x + width - 1, y + 14, nodeColor);
            context.drawString(
                this.font,
                Component.literal(renderType == NodeType.TEMPLATE ? tempNode.getTemplateName() : renderType.getDisplayName()),
                x + 4,
                y + 4,
                UITheme.TEXT_HEADER
            );
        }

        MatrixStackBridge.pop(matrices);
    }

    private void renderZoomControls(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonY = getZoomButtonY();
        NodeGraph.ZoomLevel level = nodeGraph.getZoomLevel();
        boolean minusActive = level != NodeGraph.ZoomLevel.FOCUSED;
        boolean plusActive = level == NodeGraph.ZoomLevel.FOCUSED;
        drawZoomButton(context, getZoomMinusButtonX(), buttonY, mouseX, mouseY, disabled, true, minusActive);
        drawZoomButton(context, getZoomPlusButtonX(), buttonY, mouseX, mouseY, disabled, false, plusActive);
    }

    private void drawZoomButton(GuiGraphics context, int x, int y, int mouseX, int mouseY, boolean disabled, boolean isMinus, boolean active) {
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
        String hoverKey = isMinus ? "zoom-minus-button" : "zoom-plus-button";
        float hoverProgress = getHoverProgress(hoverKey, hovered || active);
        PathmindWorkspaceChrome.drawToolbarButtonFrame(context, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE, hovered, active, disabled, hoverProgress, getAccentColor());

        int iconColor = UITheme.TEXT_PRIMARY;
        if (disabled) {
            iconColor = UITheme.DROPDOWN_ACTION_DISABLED;
        } else if (hovered) {
            iconColor = getAccentColor();
        }

        Component iconText = Component.literal(isMinus ? "-" : "+");
        int iconWidth = this.font.width(iconText);
        int iconX = x + (ZOOM_BUTTON_SIZE - iconWidth) / 2 + 1;
        int iconY = y + (ZOOM_BUTTON_SIZE - this.font.lineHeight) / 2 + 2;
        context.drawString(this.font, iconText, iconX, iconY, iconColor);
    }
    
    private void renderNodeGraph(GuiGraphics context, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        if (!onlyDragged) {
            // Node graph background
            routineWorkspaceAnimation.animateTo(getActiveRoutineWorkspace() == null ? 0f : 1f, UITheme.TRANSITION_ANIM_MS);
            routineWorkspaceAnimation.tick();
            int workspaceBackground = PathmindRoutineUi.workspaceBackground(
                UITheme.BACKGROUND_PRIMARY, NodeCategory.ROUTINES.getColor(), routineWorkspaceAnimation.getValue());
            context.fill(Sidebar.getCollapsedWidth(), TITLE_BAR_HEIGHT, this.width, this.height, workspaceBackground);
            
            // Render grid pattern for better visual organization
            if (showGrid) {
                renderGrid(context);
            }
        }

        nodeGraph.updateScreenCoordinateCapturePreview(mouseX, mouseY);
        
        // Render nodes
        nodeGraph.render(context, this.font, mouseX, mouseY, delta, onlyDragged);
    }

    private void renderDraggedWorkspaceLayer(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (nodeGraph.isAnyNodeBeingDragged()) {
            renderNodeGraph(context, mouseX, mouseY, delta, true);
        }
        nodeGraph.renderSelectionBox(context);
    }
    
    private void renderGrid(GuiGraphics context) {
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
            context.vLine(screenX, startY, endY, UITheme.GRID_LINE);
        }

        int firstHorizontal = topWorld - Math.floorMod(topWorld, gridSize);
        for (int worldY = firstHorizontal; worldY <= bottomWorld + gridSize; worldY += gridSize) {
            int screenY = nodeGraph.worldToScreenY(worldY);
            if (screenY < startY || screenY > endY) {
                continue;
            }
            context.hLine(startX, endX, screenY, UITheme.GRID_LINE);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            if (button == 0) {
                return nodeGraph.commitScreenCoordinateCapture((int) mouseX, (int) mouseY);
            }
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return handleMissingBaritonePopupClick(mouseX, mouseY, button);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
        }
        if (settingsPopupAnimation.isVisible()) {
            if (handleSettingsPopupClick(click, inBounds)) {
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
            if (createPresetField != null && createPresetField.mouseClicked(click, inBounds)) {
                return true;
            }
            if (presetPopupController.handleCreatePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.mouseClicked(click, inBounds)) {
                return true;
            }
            if (presetPopupController.handleRenamePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(click, inBounds)) {
                return true;
            }
            stopInlinePresetRename(true);
        }

        if (presetDeletePopupAnimation.isVisible()) {
            if (presetPopupController.handlePresetDeletePopupClick(mouseX, mouseY, button)) {
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

        if (presetContextMenuOpen) {
            if (button == 0 && handlePresetContextMenuClick((int) mouseX, (int) mouseY)) {
                return true;
            }
            presetContextMenuOpen = false;
            return true;
        }

        if (button == 1 && isPointInPresetTabBarContextZone((int) mouseX, (int) mouseY)) {
            openPresetContextMenu((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetDropdownOpen) {
            if (handlePresetDropdownMouseDown(mouseX, mouseY)) {
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

        if (!isPopupObscuringWorkspace() && button == 0
            && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT
            && handleStartNodeClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (!isPopupObscuringWorkspace() && button == 0 && shouldShowExecutionControls()) {
            if (isPointInRoutineExitButton((int) mouseX, (int) mouseY)) {
                switchToWorkspaceTab(0);
                return true;
            }
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
                if (!validationPanelOpen) {
                }
                return true;
            }
            if (isSettingsButtonClicked((int) mouseX, (int) mouseY, button)) {
                openSettingsPopup();
                return true;
            }
            if (isMarketplaceButtonClicked((int) mouseX, (int) mouseY, button)) {
                saveRootPresetWorkspace();
                PresetManager.setActivePreset(activePresetName);
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new PathmindMarketplaceScreen(this));
                }
                return true;
            }
            if (isPublishButtonClicked((int) mouseX, (int) mouseY, button)) {
                openPublishPresetFlow();
                return true;
            }
        }

        if (button == 0) {
            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = !presetDropdownOpen;
                return true;
            }
        }

        if (button == 0 && handleWorkspaceTabClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (nodeSearchOpen) {
            if (button == 0 && nodeSearchField != null && isPointInNodeSearchField((int) mouseX, (int) mouseY)) {
                nodeSearchField.setFocused(true);
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

        if (button == 0 && nodeGraph.handleStartModeDropdownClick((int) mouseX, (int) mouseY)) {
            return true;
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
        if (button == 0 && nodeGraph.handleOperatorToggleClick(font, (int)mouseX, (int)mouseY)) {
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
                int routineInputAction = sidebar.consumeRoutineInputAction();
                String routineInputActionId = sidebar.consumeRoutineInputActionId();
                if (routineInputAction != 0 && routineInputActionId != null) {
                    editActiveRoutineInput(routineInputActionId, routineInputAction);
                    return true;
                }
                int routineAction = sidebar.consumeRoutineAction();
                String routineActionId = sidebar.consumeRoutineActionId();
                if (routineAction != 0 && routineActionId != null) {
                    handleRoutineAction(routineActionId, routineAction);
                    return true;
                }
                int libraryAction = sidebar.consumeLibraryAction();
                String libraryActionId = sidebar.consumeLibraryActionId();
                if (libraryAction != 0 && libraryActionId != null) {
                    handleRoutineLibraryAction(libraryActionId, libraryAction);
                    return true;
                }
                if (sidebar.consumeCreateRoutineRequested()) {
                    openCreateRoutinePopup();
                    return true;
                }
                if (sidebar.consumeAddRoutineInputRequested()) {
                    addInputToActiveRoutine();
                    return true;
                }
                // Check if we should start dragging a node from sidebar
                if (sidebar.isHoveringNode()) {
                    NodeType hoveredType = sidebar.getHoveredNodeType();
                    if (shouldBlockBaritoneNode(hoveredType)) {
                        return true;
                    }
                    if (shouldBlockUiUtilsNode(hoveredType)) {
                        return true;
                    }
                    isDraggingFromSidebar = true;
                    sidebarDragActivated = false;
                    sidebarDragStartX = (int) mouseX;
                    sidebarDragStartY = (int) mouseY;
                    draggingNodeType = hoveredType;
                    draggingSidebarNode = sidebar.createNodeFromSidebar(0, 0);
                    draggingFromRoutineLibrary = sidebar.isHoveringLibraryRoutine();
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
                } else if (selection != null && selection.shouldCreateStickyNote()) {
                    nodeGraph.addNodeFromContextMenu(NodeType.STICKY_NOTE);
                    nodeGraph.closeContextMenu();
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
                if (InputCompatibilityBridge.hasControlDown()) {
                    cuttingConnections = true;
                    nodeGraph.startConnectionCut((int) mouseX, (int) mouseY);
                    return true;
                }
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
                handleStartNodeLaunchAfterClick();
                return true;
            }
            
            return handleNodeGraphClick(mouseX, mouseY, button);
        }
        
        return super.mouseClicked(click, inBounds);
    }
    
    
    private boolean handleNodeGraphClick(double mouseX, double mouseY, int button) {
        if (button == 0 && nodeGraph.isScreenCoordinateCaptureActive()) {
            return nodeGraph.commitScreenCoordinateCapture((int) mouseX, (int) mouseY);
        }
        if (button == 0) {
            List<Node> graphNodes = nodeGraph.getNodes();
            for (int i = graphNodes.size() - 1; i >= 0; i--) {
                Node candidate = graphNodes.get(i);
                if (candidate != null
                    && nodeGraph.isPointInsideScreenCoordinatePickerButton(candidate, (int) mouseX, (int) mouseY)) {
                    return nodeGraph.handleScreenCoordinatePickerClick(candidate, (int) mouseX, (int) mouseY);
                }
            }
        }
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
                    nodeGraph.focusSelectedNode(clickedNode);
                    openTemplateWorkspaceTab(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleRuntimeScopeButtonClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleSchematicDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }
                if (clickedNode.getType() == NodeType.RUN_PRESET
                    && nodeGraph.isPointInsideRunPresetOpenButton(clickedNode, (int) mouseX, (int) mouseY)) {
                    String targetPreset = nodeGraph.getSelectedPresetNameForNode(clickedNode);
                    if (targetPreset != null && !targetPreset.isBlank()
                        && PresetManager.getAvailablePresets().stream().anyMatch(name -> name.equalsIgnoreCase(targetPreset))) {
                        switchPreset(PresetManager.getAvailablePresets().stream()
                            .filter(name -> name.equalsIgnoreCase(targetPreset)).findFirst().orElse(targetPreset));
                    }
                    return true;
                }
                if (nodeGraph.handleRunPresetDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleBooleanOperatorButtonClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleMessageButtonClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    return true;
                }

                if (nodeGraph.handleScreenCoordinatePickerClick(clickedNode, (int) mouseX, (int) mouseY)) {
                    return true;
                }

                int coordinateAxis = nodeGraph.getCoordinateFieldAxisAt(clickedNode, (int)mouseX, (int)mouseY);
                if (coordinateAxis != -1) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startCoordinateEditing(clickedNode, coordinateAxis);
                    return true;
                }

                if (nodeGraph.isPointInsideStopTargetField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startStopTargetEditing(clickedNode);
                    return true;
                }

                if (nodeGraph.isPointInsideVariableField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startVariableEditing(clickedNode);
                    return true;
                }

                if (nodeGraph.handleRandomRoundingToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleRandomRoundingDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleAmountToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (button == 0 && nodeGraph.handleAmountSignDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleDirectionModeTabClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanModeTabClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleMessageScopeToggleClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleBooleanLiteralDropdownClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.handleModeFieldClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.isPointInsideAmountField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startAmountEditing(clickedNode);
                    return true;
                }

                int messageIndex = nodeGraph.getMessageFieldIndexAt(clickedNode, (int)mouseX, (int)mouseY);
                if (messageIndex != -1) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startMessageEditing(clickedNode, messageIndex);
                    return true;
                }

                int parameterIndex = nodeGraph.getParameterFieldIndexAt(clickedNode, (int)mouseX, (int)mouseY);
                if (parameterIndex != -1) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startParameterEditing(clickedNode, parameterIndex);
                    return true;
                }

                if (nodeGraph.handleEventNameFieldClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
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
                    nodeGraph.focusSelectedNode(clickedNode);
                    openParameterOverlay(clickedNode);
                    return true;
                }

                boolean doubleClick = nodeGraph.handleNodeClick(clickedNode, (int)mouseX, (int)mouseY);
                if (doubleClick && clickedNode.getType() == NodeType.ROUTINE_CALL && !clickedNode.getRoutineId().isBlank()) {
                    openRoutineWorkspaceTab(clickedNode.getRoutineId());
                    return true;
                }
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
                    nodeGraph.focusSelectedNode(clickedNode);
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
                nodeGraph.removeConnection(connection);
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
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            if (settingsNodeSelectorScrollDragging) {
                int popupX = getSettingsPopupX();
                int popupY = getSettingsPopupY();
                int contentPopupY = popupY - settingsPopupScrollOffset;
                int contentX = popupX + 20;
                int selectorWidth = SETTINGS_POPUP_WIDTH - 40;
                int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
                int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorWidth);
                ScrollbarHelper.Metrics selectorScrollMetrics = getSettingsNodeTypeSelectorScrollMetrics(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll);
                settingsNodeSelectorScrollOffset = ScrollbarHelper.scrollFromThumb(selectorScrollMetrics, (int) mouseY - settingsNodeSelectorScrollDragOffset);
            }
            if (settingsPopupScrollDragging) {
                int popupX = getSettingsPopupX();
                int popupY = getSettingsPopupY();
                int popupHeight = getSettingsPopupHeight();
                int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
                ScrollbarHelper.Metrics scrollMetrics = getSettingsPopupScrollMetrics(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, maxScroll);
                settingsPopupScrollOffset = ScrollbarHelper.scrollFromThumb(scrollMetrics, (int) mouseY - settingsPopupScrollDragOffset);
            }
            if (nodeDelayDragging) {
                updateNodeDelayFromMouse((int) mouseX, getSettingsPopupX(), SETTINGS_POPUP_WIDTH);
            }
            if (createListRadiusDragging) {
                updateCreateListRadiusFromMouse(getEffectiveSettingsTargetNode(), (int) mouseX, getSettingsPopupX(), SETTINGS_POPUP_WIDTH);
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
        if (button == 0 && pendingPresetDropdownDragName != null) {
            updatePendingPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && draggingPresetDropdownName != null) {
            updatePresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (draggingPresetTabName != null) {
            updatePresetTabDrag((int) mouseX);
            return true;
        }

        if (button == 1 && cuttingConnections) {
            nodeGraph.updateConnectionCut(nodeGraph.screenToWorldX((int) mouseX), nodeGraph.screenToWorldY((int) mouseY));
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
            sidebarDragActivated = sidebarDragActivated
                || Math.abs((int) mouseX - sidebarDragStartX) > CLICK_THRESHOLD
                || Math.abs((int) mouseY - sidebarDragStartY) > CLICK_THRESHOLD;
            if (!sidebarDragActivated) return true;
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
        
        return super.mouseDragged(click, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            nodeDelayDragging = false;
            createListRadiusDragging = false;
            settingsNodeSelectorScrollDragging = false;
            settingsPopupScrollDragging = false;
            if (nodeDelayField != null) {
                nodeDelayField.mouseReleased(click);
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null) {
                createPresetField.mouseReleased(click);
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null) {
                renamePresetField.mouseReleased(click);
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

        if (button == 0 && draggingPresetDropdownName != null) {
            finishPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && pendingPresetDropdownDragName != null) {
            String presetName = pendingPresetDropdownDragName;
            clearPresetDropdownDragState();
            presetDropdownOpen = false;
            if (!presetName.equals(activePresetName)) {
                switchPreset(presetName);
            }
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null) {
                inlinePresetRenameField.mouseReleased(click);
            }
            return true;
        }

        if (button == 0 && pendingPresetTabInteractionName != null) {
            String presetName = pendingPresetTabInteractionName;
            clearPendingPresetTabInteraction();
            if (isPresetGroupTab(presetName)) {
                togglePresetGroupExpanded(getPresetGroupKeyFromTab(presetName));
                return true;
            }
            if (!presetName.equals(activePresetName)) {
                switchPreset(presetName);
            }
            return true;
        }

        if (button == 0) {
            // Handle dropping node from sidebar
            if (isDraggingFromSidebar) {
                if (sidebarDragActivated && saveDraggedRoutineToLibrary(mouseX, mouseY)) {
                    // Saved to the reusable routine catalogue.
                } else if (sidebarDragActivated && importDraggedLibraryRoutineToList(mouseX, mouseY)) {
                    // Imported into this preset's routine list.
                } else if (sidebarDragActivated && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                    Node newNode = dropDraggedSidebarNodeIntoWorkspace((int) mouseX, (int) mouseY);
                    if (newNode != null) {
                        nodeGraph.selectNode(newNode);
                    }
                } else if (!sidebarDragActivated && draggingSidebarNode != null
                    && draggingSidebarNode.getType() == NodeType.ROUTINE_CALL
                    && !draggingSidebarNode.getRoutineId().isBlank()) {
                    if (draggingFromRoutineLibrary) openLibraryRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
                    else openRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
                }
                // Reset drag state
                isDraggingFromSidebar = false;
                sidebarDragActivated = false;
                draggingNodeType = null;
                draggingSidebarNode = null;
                draggingFromRoutineLibrary = false;
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
            if (cuttingConnections) {
                nodeGraph.stopConnectionCut();
                cuttingConnections = false;
                return true;
            }
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
                    nodeGraph.focusSelectedNode(clickedNode);
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
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
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
            if (nodeSearchField != null && nodeSearchField.keyPressed(input)) {
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
            if (nodeDelayField.keyPressed(input)) {
                return true;
            }
        }
        if (settingsPopupAnimation.isVisible() && settingsNodeSearchField != null && settingsNodeSearchField.isFocused()) {
            if (settingsNodeSearchField.keyPressed(input)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
        }
        if (settingsPopupAnimation.isVisible() && createListRadiusField != null && createListRadiusField.isFocused()) {
            if (createListRadiusField.keyPressed(input)) {
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
            if (createPresetField != null && createPresetField.keyPressed(input)) {
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
            if (renamePresetField != null && renamePresetField.keyPressed(input)) {
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

            if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(input)) {
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

            if (!importExportBusy && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
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

        if (nodeGraph.isScreenCoordinateCaptureActive() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            nodeGraph.cancelScreenCoordinateCapture();
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

        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            && canStartInlinePresetRename(activePresetName)) {
            startInlinePresetRename(activePresetName);
            return true;
        }

        if (handleNodeGraphShortcuts(keyCode, modifiers)) {
            return true;
        }

        // Close screen with Escape key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        
        // Delete selected node with Delete/Backspace key
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (nodeGraph.deleteSelectedNode()) {
                return true;
            }
        }
        
        // Don't handle the opening keybind - let it be ignored
        // This prevents the screen from closing when the same key is pressed
        
        return super.keyPressed(input);
    }
    
    @Override
    public boolean charTyped(CharacterEvent input) {
        int modifiers = input.modifiers();
        char chr = (char) input.codepoint();
        if (nodeSearchOpen) {
            if (nodeSearchField != null && nodeSearchField.charTyped(input)) {
                return true;
            }
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            if (settingsNodeSearchField != null && settingsNodeSearchField.isFocused() && settingsNodeSearchField.charTyped(input)) {
                return true;
            }
            if (nodeDelayField != null && nodeDelayField.isFocused() && nodeDelayField.charTyped(input)) {
                return true;
            }
            if (createListRadiusField != null && createListRadiusField.isFocused() && createListRadiusField.charTyped(input)) {
                return true;
            }
            return true;
        }
        if (validationPanelOpen) {
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null && createPresetField.charTyped(input)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.charTyped(input)) {
                return true;
            }
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(input)) {
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

        if (nodeGraph.handleStopTargetCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleVariableCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleEventNameCharTyped(chr, modifiers)) {
            return true;
        }

        if (nodeGraph.handleParameterCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleMessageCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleAmountCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleCoordinateCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        return super.charTyped(input);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            int popupX = getSettingsPopupX();
            int popupY = getSettingsPopupY();
            int popupHeight = getSettingsPopupHeight();
            int contentPopupY = popupY - settingsPopupScrollOffset;
            int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
            int[] selectorBounds = getSettingsNodeTypeSelectorBounds(popupX + 20, getSettingsNodeSectionBodyY(contentPopupY), SETTINGS_POPUP_WIDTH - 40);
            long now = System.currentTimeMillis();
            boolean continueOuterScroll = now - settingsLastScrollEventMs <= SETTINGS_SCROLL_GESTURE_TIMEOUT_MS
                && settingsLastScrollConsumer == 2;
            if (isPointInRect((int) mouseX, (int) mouseY, selectorBounds[0], selectorBounds[1], selectorBounds[2], selectorBounds[3]) && verticalAmount != 0.0) {
                int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorBounds[2]);
                if (maxSelectorScroll > 0 && !continueOuterScroll) {
                    int nextSelectorScroll = ScrollbarHelper.applyWheel(settingsNodeSelectorScrollOffset, verticalAmount, 16, maxSelectorScroll);
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
            if (isPointInRect((int) mouseX, (int) mouseY, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3]) && verticalAmount != 0.0) {
                int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
                if (maxScroll > 0) {
                    int nextPopupScroll = ScrollbarHelper.applyWheel(settingsPopupScrollOffset, verticalAmount, 16, maxScroll);
                    if (nextPopupScroll != settingsPopupScrollOffset) {
                        settingsPopupScrollOffset = nextPopupScroll;
                        settingsLastScrollEventMs = now;
                        settingsLastScrollConsumer = 2;
                    }
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
                    presetDropdownScrollOffset = Mth.clamp(presetDropdownScrollOffset - delta, 0, layout.maxScrollOffset);
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

    private void renderWorkspaceTabs(GuiGraphics context, int mouseX, int mouseY) {
        tickQueuedPresetDeletionAnimation();
        if (!isPopupObscuringWorkspace() && pendingPresetTabInteractionName != null && draggingPresetTabName == null) {
            updatePendingPresetTabInteraction(mouseX, mouseY);
        }
        if (!isPopupObscuringWorkspace() && draggingPresetTabName != null) {
            updatePresetTabDrag(mouseX);
        }
        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - x);
        if (tabs.isEmpty()) {
            return;
        }

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
        float plusAlpha = Mth.clamp(presetTabAddButtonFadeAnimation.getValue(), 0f, 1f);
        if (addTabX + PRESET_TAB_ADD_WIDTH <= rightLimit) {
            boolean hovered = isPointInRect(mouseX, mouseY, addTabX, y, PRESET_TAB_ADD_WIDTH, TAB_HEIGHT);
            context.drawCenteredString(
                this.font,
                Component.literal("+"),
                addTabX + PRESET_TAB_ADD_WIDTH / 2,
                y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1,
                AnimationHelper.multiplyAlpha(hovered ? getAccentColor() : UITheme.ICON_MUTED_BRIGHT, plusAlpha)
            );
        }
        renderInlinePresetRenameField(context, mouseX, mouseY, tabs, tabWidths, tabXs, y, dragIndex);
    }

    private boolean handleWorkspaceTabClick(int mouseX, int mouseY) {
        int x = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - x);
        if (tabs.isEmpty()) {
            return false;
        }
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
                if (isPresetGroupTab(label)) {
                    pendingPresetTabInteractionName = label;
                    pendingPresetTabPressMouseX = mouseX;
                    pendingPresetTabPressMouseY = mouseY;
                    pendingPresetTabPressTabLeft = x;
                    return true;
                }
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
        return Math.max(getPresetTabStartX(), getTitleTextX() - PRESET_TAB_TITLE_GAP);
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
        List<String> tabs = getRenderedPresetTabsForWidth(getPresetTabRightLimit() - getPresetTabStartX());
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
        if (isPresetGroupTab(draggingPresetTabName)) {
            updatePresetGroupDragOrder(tabs, widths, xs, currentIndex, dragCenter);
            return;
        }
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
        int clampedTarget = Mth.clamp(targetIndex, 1, presetTabOrder.size() - 1);
        if (clampedTarget != orderIndex) {
            presetTabOrder.remove(orderIndex);
            presetTabOrder.add(clampedTarget, draggingPresetTabName);
            normalizePresetTabOrder();
        }
    }

    private void updatePresetGroupDragOrder(List<String> tabs, int[] widths, int[] xs, int currentIndex, int dragCenter) {
        if (currentSettings == null || currentSettings.presetGroupOrder == null) {
            return;
        }
        String groupKey = getPresetGroupKeyFromTab(draggingPresetTabName);
        int orderIndex = currentSettings.presetGroupOrder.indexOf(groupKey);
        if (orderIndex < 0) {
            return;
        }
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (i == currentIndex || !isPresetGroupTab(tabs.get(i))) {
                continue;
            }
            int center = xs[i] + widths[i] / 2;
            if (dragCenter > center) {
                targetIndex++;
            }
        }
        int clampedTarget = Mth.clamp(targetIndex, 0, currentSettings.presetGroupOrder.size() - 1);
        if (clampedTarget != orderIndex) {
            currentSettings.presetGroupOrder.remove(orderIndex);
            currentSettings.presetGroupOrder.add(clampedTarget, groupKey);
            SettingsManager.save(currentSettings);
        }
    }

    private void endPresetTabDrag() {
        if (draggingPresetTabName != null && draggingPresetTabCurrentX > 0) {
            int dropX = draggingPresetTabCurrentX + Math.max(1, PRESET_GROUP_TAB_WIDTH / 2);
            String groupKey = getPresetGroupAt(dropX, TAB_BAR_TOP + TAB_HEIGHT / 2);
            if (!groupKey.isEmpty() && !isPresetGroupTab(draggingPresetTabName)) {
                setPresetGroupColor(draggingPresetTabName, groupKey);
            } else if (!isPresetGroupTab(draggingPresetTabName) && !getPresetGroupKey(draggingPresetTabName).isEmpty() && !isPointInPresetGroupSpan(dropX, TAB_BAR_TOP + TAB_HEIGHT / 2, getPresetGroupKey(draggingPresetTabName))) {
                setPresetGroupColor(draggingPresetTabName, null);
            }
        }
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

    private boolean isPointInPresetTabBarContextZone(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int rightLimit = getPresetOverflowTabRight();
        return isPointInRect(mouseX, mouseY, startX, TAB_BAR_TOP - 4, Math.max(0, rightLimit - startX), TAB_HEIGHT + 8);
    }

    private void openPresetContextMenu(int mouseX, int mouseY) {
        String target = getPresetTabAt(mouseX, mouseY);
        String groupTarget = getPresetGroupAt(mouseX, mouseY);
        presetContextMenuPresetName = target;
        presetContextMenuGroupKey = groupTarget;
        presetContextMenuX = Mth.clamp(mouseX, 4, Math.max(4, this.width - PRESET_CONTEXT_MENU_WIDTH - 4));
        presetContextMenuY = Mth.clamp(mouseY, 4, Math.max(4, this.height - getPresetContextMenuHeight() - 4));
        presetContextMenuOpen = true;
        presetDropdownOpen = false;
        nodeGraph.closeContextMenu();
        nodeGraph.closeNodeContextMenu();
    }

    private String getPresetTabAt(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (isPointInRect(mouseX, mouseY, xs[i], y, widths[i], TAB_HEIGHT)) {
                String tabName = tabs.get(i);
                return isPresetGroupTab(tabName) ? null : tabName;
            }
        }
        return null;
    }

    private String getPresetGroupAt(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            if (isPointInRect(mouseX, mouseY, xs[i], y, widths[i], TAB_HEIGHT) && isPresetGroupTab(tabs.get(i))) {
                return getPresetGroupKeyFromTab(tabs.get(i));
            }
        }
        return "";
    }

    private boolean isPointInPresetGroupSpan(int mouseX, int mouseY, String groupKey) {
        if (!isValidPresetGroupColorKey(groupKey)) {
            return false;
        }
        int startX = getPresetTabStartX();
        int y = TAB_BAR_TOP;
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        int left = -1;
        int right = -1;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            String tab = tabs.get(i);
            boolean inGroup = isPresetGroupTab(tab)
                ? groupKey.equals(getPresetGroupKeyFromTab(tab))
                : groupKey.equals(getPresetGroupKey(tab));
            if (inGroup) {
                if (left < 0) {
                    left = xs[i];
                }
                right = xs[i] + widths[i];
            } else if (left >= 0) {
                break;
            }
        }
        return left >= 0 && isPointInRect(mouseX, mouseY, left, y - 4, Math.max(0, right - left), TAB_HEIGHT + 8);
    }

    private int getPresetContextMenuHeight() {
        if (presetContextMenuGroupKey != null && !presetContextMenuGroupKey.isEmpty()) {
            return PRESET_CONTEXT_MENU_ITEM_HEIGHT * (PRESET_GROUP_COLOR_KEYS.length + 3)
                + PRESET_CONTEXT_MENU_SEPARATOR_HEIGHT;
        }
        if (presetContextMenuPresetName == null) {
            return PRESET_CONTEXT_MENU_ITEM_HEIGHT * 2;
        }
        return PRESET_CONTEXT_MENU_ITEM_HEIGHT * (getPresetGroupKey(presetContextMenuPresetName).isEmpty() ? 4 : 5);
    }

    private void renderPresetContextMenu(GuiGraphics context, int mouseX, int mouseY) {
        if (!presetContextMenuOpen) {
            return;
        }
        Object matrices = context.pose();
        MatrixStackBridge.push(matrices);
        try {
            MatrixStackBridge.translateZ(matrices, 600.0f);
            int height = getPresetContextMenuHeight();
            context.fill(presetContextMenuX, presetContextMenuY, presetContextMenuX + PRESET_CONTEXT_MENU_WIDTH, presetContextMenuY + height, UITheme.BACKGROUND_SECONDARY);
            DrawContextBridge.drawBorderInLayer(context, presetContextMenuX, presetContextMenuY, PRESET_CONTEXT_MENU_WIDTH, height, UITheme.BORDER_DEFAULT);
            int y = presetContextMenuY;
            y = drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.createPreset").getString(), 0, false);
            y = drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.createGroup").getString(), 0, getNextPresetGroupColorKey().isEmpty());
            if (presetContextMenuGroupKey != null && !presetContextMenuGroupKey.isEmpty()) {
                y = drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.deleteGroup").getString(), 0, false);
                y = drawPresetContextSeparator(context, y);
                for (int i = 0; i < PRESET_GROUP_COLOR_KEYS.length; i++) {
                    y = drawPresetContextMenuItem(context, mouseX, mouseY, y, getPresetGroupColorLabel(PRESET_GROUP_COLOR_KEYS[i]), PRESET_GROUP_COLORS[i], PRESET_GROUP_COLOR_KEYS[i].equals(presetContextMenuGroupKey));
                }
                return;
            }
            if (presetContextMenuPresetName == null) {
                return;
            }
            y = drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.renamePreset").getString(), 0, isPresetRenameDisabled(presetContextMenuPresetName));
            y = drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.deletePreset").getString(), 0, isPresetDeleteDisabled(presetContextMenuPresetName));
            if (!getPresetGroupKey(presetContextMenuPresetName).isEmpty()) {
                drawPresetContextMenuItem(context, mouseX, mouseY, y, Component.translatable("pathmind.context.ungroup").getString(), getPresetGroupColor(presetContextMenuPresetName), false);
            }
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    private int drawPresetContextSeparator(GuiGraphics context, int y) {
        int lineY = y + PRESET_CONTEXT_MENU_SEPARATOR_HEIGHT / 2;
        context.hLine(presetContextMenuX + 5, presetContextMenuX + PRESET_CONTEXT_MENU_WIDTH - 6, lineY, UITheme.BORDER_SUBTLE);
        return y + PRESET_CONTEXT_MENU_SEPARATOR_HEIGHT;
    }

    private int drawPresetContextMenuItem(GuiGraphics context, int mouseX, int mouseY, int y, String label, int swatchColor, boolean disabled) {
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, presetContextMenuX, y, PRESET_CONTEXT_MENU_WIDTH, PRESET_CONTEXT_MENU_ITEM_HEIGHT);
        if (hovered) {
            context.fill(presetContextMenuX + 1, y + 1, presetContextMenuX + PRESET_CONTEXT_MENU_WIDTH - 1, y + PRESET_CONTEXT_MENU_ITEM_HEIGHT, UITheme.BUTTON_DEFAULT_HOVER);
        }
        int textColor = disabled ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = presetContextMenuX + 7;
        if (swatchColor != 0) {
            context.fill(textX, y + 6, textX + 7, y + 13, swatchColor);
            textX += 12;
        }
        context.drawString(this.font, Component.literal(label), textX, y + 5, textColor);
        return y + PRESET_CONTEXT_MENU_ITEM_HEIGHT;
    }

    private boolean handlePresetContextMenuClick(int mouseX, int mouseY) {
        if (!isPointInRect(mouseX, mouseY, presetContextMenuX, presetContextMenuY, PRESET_CONTEXT_MENU_WIDTH, getPresetContextMenuHeight())) {
            presetContextMenuOpen = false;
            return true;
        }
        int relativeY = mouseY - presetContextMenuY;
        if (relativeY < PRESET_CONTEXT_MENU_ITEM_HEIGHT * 2) {
            int action = relativeY / PRESET_CONTEXT_MENU_ITEM_HEIGHT;
            presetContextMenuOpen = false;
            if (action == 0) {
                openCreatePresetPopup();
            } else if (action == 1) {
                createPresetGroup();
            }
            return true;
        }
        if (presetContextMenuGroupKey != null && !presetContextMenuGroupKey.isEmpty()) {
            if (relativeY < PRESET_CONTEXT_MENU_ITEM_HEIGHT * 3) {
                int action = relativeY / PRESET_CONTEXT_MENU_ITEM_HEIGHT;
                presetContextMenuOpen = false;
                if (action == 2) {
                    deletePresetGroup(presetContextMenuGroupKey);
                }
                return true;
            }
            relativeY -= PRESET_CONTEXT_MENU_ITEM_HEIGHT * 3 + PRESET_CONTEXT_MENU_SEPARATOR_HEIGHT;
            if (relativeY >= 0 && relativeY < PRESET_CONTEXT_MENU_ITEM_HEIGHT * PRESET_GROUP_COLOR_KEYS.length) {
                int action = relativeY / PRESET_CONTEXT_MENU_ITEM_HEIGHT;
                presetContextMenuOpen = false;
                recolorPresetGroup(presetContextMenuGroupKey, PRESET_GROUP_COLOR_KEYS[action]);
            }
            return true;
        }
        if (presetContextMenuPresetName == null) {
            return true;
        }
        relativeY -= PRESET_CONTEXT_MENU_ITEM_HEIGHT * 2;
        int presetActionCount = getPresetGroupKey(presetContextMenuPresetName).isEmpty() ? 2 : 3;
        if (relativeY < PRESET_CONTEXT_MENU_ITEM_HEIGHT * presetActionCount) {
            int action = relativeY / PRESET_CONTEXT_MENU_ITEM_HEIGHT;
            presetContextMenuOpen = false;
            if (action == 0 && !isPresetRenameDisabled(presetContextMenuPresetName)) {
                openRenamePresetPopup(presetContextMenuPresetName);
            } else if (action == 1 && !isPresetDeleteDisabled(presetContextMenuPresetName)) {
                openPresetDeletePopup(presetContextMenuPresetName);
            } else if (action == 2) {
                setPresetGroupColor(presetContextMenuPresetName, null);
            }
            return true;
        }
        return true;
    }

    private String getPresetGroupColorLabel(String key) {
        if ("sky".equals(key)) return "Sky";
        if ("mint".equals(key)) return "Mint";
        if ("amber".equals(key)) return "Amber";
        if ("rose".equals(key)) return "Rose";
        if ("violet".equals(key)) return "Violet";
        return key;
    }

    private String getNextPresetGroupColorKey() {
        if (currentSettings == null) {
            return "";
        }
        if (currentSettings.presetGroupOrder == null) {
            currentSettings.presetGroupOrder = new ArrayList<>();
        }
        for (String key : PRESET_GROUP_COLOR_KEYS) {
            if (!currentSettings.presetGroupOrder.contains(key)) {
                return key;
            }
        }
        return "";
    }

    private void createPresetGroup() {
        String key = getNextPresetGroupColorKey();
        if (key.isEmpty() || currentSettings == null) {
            return;
        }
        if (currentSettings.presetGroupOrder == null) {
            currentSettings.presetGroupOrder = new ArrayList<>();
        }
        if (currentSettings.presetGroupsExpanded == null) {
            currentSettings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        currentSettings.presetGroupOrder.add(key);
        currentSettings.presetGroupsExpanded.put(key, true);
        SettingsManager.save(currentSettings);
    }

    private void deletePresetGroup(String groupKey) {
        if (!isValidPresetGroupColorKey(groupKey) || currentSettings == null) {
            return;
        }
        if (currentSettings.presetGroupOrder != null) {
            currentSettings.presetGroupOrder.remove(groupKey);
        }
        if (currentSettings.presetGroupsExpanded != null) {
            currentSettings.presetGroupsExpanded.remove(groupKey);
        }
        if (currentSettings.presetGroupColors != null) {
            currentSettings.presetGroupColors.entrySet().removeIf(entry -> groupKey.equals(entry.getValue()));
        }
        SettingsManager.save(currentSettings);
    }

    private void recolorPresetGroup(String oldKey, String newKey) {
        if (!isValidPresetGroupColorKey(oldKey) || !isValidPresetGroupColorKey(newKey) || currentSettings == null || oldKey.equals(newKey)) {
            return;
        }
        if (currentSettings.presetGroupOrder == null) {
            currentSettings.presetGroupOrder = new ArrayList<>();
        }
        if (currentSettings.presetGroupOrder.contains(newKey)) {
            return;
        }
        int index = currentSettings.presetGroupOrder.indexOf(oldKey);
        if (index >= 0) {
            currentSettings.presetGroupOrder.set(index, newKey);
        }
        if (currentSettings.presetGroupsExpanded != null) {
            Boolean expanded = currentSettings.presetGroupsExpanded.remove(oldKey);
            currentSettings.presetGroupsExpanded.put(newKey, expanded == null || expanded);
        }
        if (currentSettings.presetGroupColors != null) {
            for (Map.Entry<String, String> entry : currentSettings.presetGroupColors.entrySet()) {
                if (oldKey.equals(entry.getValue())) {
                    entry.setValue(newKey);
                }
            }
        }
        SettingsManager.save(currentSettings);
    }

    private void setPresetGroupColor(String presetName, String colorKey) {
        if (presetName == null || presetName.isEmpty() || currentSettings == null) {
            return;
        }
        if (currentSettings.presetGroupColors == null) {
            currentSettings.presetGroupColors = new LinkedHashMap<>();
        }
        if (currentSettings.presetGroupsExpanded == null) {
            currentSettings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        if (colorKey == null || colorKey.isEmpty()) {
            currentSettings.presetGroupColors.remove(presetName);
        } else {
            if (currentSettings.presetGroupOrder == null) {
                currentSettings.presetGroupOrder = new ArrayList<>();
            }
            if (!currentSettings.presetGroupOrder.contains(colorKey)) {
                currentSettings.presetGroupOrder.add(colorKey);
            }
            currentSettings.presetGroupColors.put(presetName, colorKey);
            currentSettings.presetGroupsExpanded.putIfAbsent(colorKey, true);
        }
        SettingsManager.save(currentSettings);
    }

    private boolean isPresetGroupTab(String tabName) {
        return tabName != null && tabName.startsWith(PRESET_GROUP_TAB_PREFIX);
    }

    private String getPresetGroupTabName(String colorKey) {
        return PRESET_GROUP_TAB_PREFIX + colorKey;
    }

    private String getPresetGroupKeyFromTab(String tabName) {
        if (!isPresetGroupTab(tabName)) {
            return "";
        }
        return tabName.substring(PRESET_GROUP_TAB_PREFIX.length());
    }

    private String getPresetGroupKey(String presetName) {
        if (presetName == null || currentSettings == null || currentSettings.presetGroupColors == null) {
            return "";
        }
        String key = currentSettings.presetGroupColors.get(presetName);
        return isValidPresetGroupColorKey(key) ? key : "";
    }

    private boolean isValidPresetGroupColorKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (String candidate : PRESET_GROUP_COLOR_KEYS) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPresetGroupExpanded(String colorKey) {
        if (!isValidPresetGroupColorKey(colorKey)) {
            return true;
        }
        if (currentSettings == null || currentSettings.presetGroupsExpanded == null) {
            return true;
        }
        Boolean expanded = currentSettings.presetGroupsExpanded.get(colorKey);
        return expanded == null || expanded;
    }

    private void togglePresetGroupExpanded(String colorKey) {
        if (!isValidPresetGroupColorKey(colorKey) || currentSettings == null) {
            return;
        }
        setPresetGroupExpanded(colorKey, !isPresetGroupExpanded(colorKey));
    }

    private void setPresetGroupExpanded(String colorKey, boolean expanded) {
        if (!isValidPresetGroupColorKey(colorKey) || currentSettings == null) {
            return;
        }
        if (currentSettings.presetGroupsExpanded == null) {
            currentSettings.presetGroupsExpanded = new LinkedHashMap<>();
        }
        currentSettings.presetGroupsExpanded.put(colorKey, expanded);
        if (expanded) {
            for (String presetName : availablePresets) {
                if (colorKey.equals(getPresetGroupKey(presetName))) {
                    AnimatedValue appear = presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f));
                    appear.setValue(0f);
                    appear.animateTo(1f, 180, AnimationHelper::easeOutCubic);
                }
            }
        }
        SettingsManager.save(currentSettings);
    }

    private int getPresetGroupColorByKey(String key) {
        for (int i = 0; i < PRESET_GROUP_COLOR_KEYS.length; i++) {
            if (PRESET_GROUP_COLOR_KEYS[i].equals(key)) {
                return PRESET_GROUP_COLORS[i];
            }
        }
        return 0;
    }

    private int getPresetGroupColor(String presetName) {
        if (isPresetGroupTab(presetName)) {
            return getPresetGroupColorByKey(getPresetGroupKeyFromTab(presetName));
        }
        return getPresetGroupColorByKey(getPresetGroupKey(presetName));
    }

    private void clearPresetDropdownDragState() {
        pendingPresetDropdownDragName = null;
        pendingPresetDropdownPressMouseX = 0;
        pendingPresetDropdownPressMouseY = 0;
        draggingPresetDropdownName = null;
        draggingPresetDropdownCurrentX = 0;
        draggingPresetDropdownCurrentY = 0;
    }

    private void updatePendingPresetDropdownDrag(int mouseX, int mouseY) {
        if (pendingPresetDropdownDragName == null || draggingPresetDropdownName != null) {
            return;
        }
        int dx = Math.abs(mouseX - pendingPresetDropdownPressMouseX);
        int dy = Math.abs(mouseY - pendingPresetDropdownPressMouseY);
        if (dx < PRESET_TAB_DRAG_THRESHOLD && dy < PRESET_TAB_DRAG_THRESHOLD) {
            return;
        }
        draggingPresetDropdownName = pendingPresetDropdownDragName;
        draggingPresetDropdownCurrentX = mouseX;
        draggingPresetDropdownCurrentY = mouseY;
        pendingPresetDropdownDragName = null;
    }

    private void updatePresetDropdownDrag(int mouseX, int mouseY) {
        draggingPresetDropdownCurrentX = mouseX;
        draggingPresetDropdownCurrentY = mouseY;
    }

    private void finishPresetDropdownDrag(int mouseX, int mouseY) {
        String presetName = draggingPresetDropdownName;
        if (presetName == null || presetName.isEmpty()) {
            clearPresetDropdownDragState();
            return;
        }
        if (isPointInPresetTabBarDropZone(mouseX, mouseY)) {
            String groupKey = getPresetGroupAt(mouseX, mouseY);
            if (!groupKey.isEmpty()) {
                setPresetGroupColor(presetName, groupKey);
            } else {
                insertPresetTabAtDropPosition(presetName, mouseX);
            }
            presetDropdownOpen = false;
        }
        clearPresetDropdownDragState();
    }

    private boolean isPointInPresetTabBarDropZone(int mouseX, int mouseY) {
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        return isPointInRect(mouseX, mouseY, startX, TAB_BAR_TOP - 4, Math.max(0, rightLimit - startX), TAB_HEIGHT + 8);
    }

    private void insertPresetTabAtDropPosition(String presetName, int mouseX) {
        if (presetName == null || presetName.isEmpty() || !availablePresets.contains(presetName)) {
            return;
        }
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        int targetIndex = getPresetTabDropIndex(mouseX);
        presetTabOrder.remove(presetName);
        int clampedTarget = Mth.clamp(targetIndex, 1, presetTabOrder.size());
        presetTabOrder.add(clampedTarget, presetName);
        normalizePresetTabOrder();
        presetTabAppearAnimations.computeIfAbsent(presetName, key -> new AnimatedValue(1f)).setValue(1f);
    }

    private int getPresetTabDropIndex(int mouseX) {
        int startX = getPresetTabStartX();
        int rightLimit = getPresetTabRightLimit();
        List<String> tabs = getRenderedPresetTabsForWidth(rightLimit - startX);
        int[] widths = computePresetTabWidths(tabs, rightLimit - startX, PRESET_TAB_ADD_WIDTH);
        int[] xs = computePresetTabXs(widths, startX);
        int targetIndex = 0;
        for (int i = 0; i < tabs.size() && i < widths.length; i++) {
            String tabName = tabs.get(i);
            if (tabName != null && (tabName.equals(draggingPresetDropdownName) || isPresetGroupTab(tabName))) {
                continue;
            }
            if (mouseX > xs[i] + widths[i] / 2) {
                targetIndex++;
            }
        }
        return targetIndex;
    }

    private void renderDraggedPresetDropdownTab(GuiGraphics context, int mouseX, int mouseY) {
        if (draggingPresetDropdownName == null) {
            return;
        }
        int width = Mth.clamp(
            this.font.width(draggingPresetDropdownName) + PRESET_TAB_TEXT_PADDING * 2,
            TAB_MIN_WIDTH,
            TAB_MAX_WIDTH
        );
        int x = draggingPresetDropdownCurrentX - width / 2;
        int y = draggingPresetDropdownCurrentY - TAB_HEIGHT / 2;
        drawPresetTab(context, mouseX, mouseY, draggingPresetDropdownName, x, y, width, true);
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

    private void drawPresetTab(GuiGraphics context, int mouseX, int mouseY, String label, int x, int y, int tabWidth, boolean dragging) {
        if (isPresetGroupTab(label)) {
            drawPresetGroupTab(context, mouseX, mouseY, label, x, y, tabWidth, dragging);
            return;
        }
        String displayLabel = getPresetTabDisplayLabel(label);
        boolean active = label.equals(activePresetName);
        boolean hovered = isPointInRect(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT);
        int groupColor = getPresetGroupColor(label);
        int fill = active ? UITheme.BUTTON_ACTIVE_BG : UITheme.BUTTON_DEFAULT_BG;
        int border = groupColor != 0 ? groupColor : (active ? getAccentColor() : UITheme.BORDER_DEFAULT);
        if (!active && hovered) {
            fill = UITheme.BUTTON_DEFAULT_HOVER;
            border = groupColor != 0 ? groupColor : UITheme.BORDER_HIGHLIGHT;
        }
        if (dragging) {
            fill = UITheme.TOOLBAR_BG_ACTIVE;
        }

        float appear = dragging ? 1f : getPresetTabAppearProgress(label);
        int fillColor = AnimationHelper.multiplyAlpha(fill, appear);
        int borderColor = AnimationHelper.multiplyAlpha(border, appear);
        int textColor = AnimationHelper.multiplyAlpha(active ? UITheme.TEXT_PRIMARY : UITheme.TEXT_SECONDARY, appear);
        if (hovered && !active) {
            textColor = AnimationHelper.multiplyAlpha(UITheme.TEXT_PRIMARY, appear);
        }

        context.fill(x, y, x + tabWidth, y + TAB_HEIGHT, fillColor);
        if (groupColor != 0) {
            context.fill(x + 1, y + 1, x + tabWidth - 1, y + 3, AnimationHelper.multiplyAlpha(groupColor, appear));
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, tabWidth, TAB_HEIGHT, borderColor);
        boolean deletable = !isPresetDeleteDisabled(label);
        int closeSpace = deletable ? (PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2) : 0;
        int textMaxWidth = Math.max(4, tabWidth - PRESET_TAB_TEXT_PADDING * 2 - closeSpace);
        if (!label.equals(inlinePresetRenameName)) {
            String drawLabel = TextRenderUtil.trimWithEllipsis(this.font, displayLabel, textMaxWidth);
            context.drawString(this.font, Component.literal(drawLabel), x + PRESET_TAB_TEXT_PADDING, y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1, textColor, false);
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
            int closeColor = closeHovered ? UITheme.STATE_ERROR : UITheme.ICON_MUTED;
            drawCloseXIcon(context, closeLeft, closeTop, PRESET_TAB_CLOSE_ICON_SIZE, AnimationHelper.multiplyAlpha(closeColor, appear));
        }
    }

    private void drawPresetGroupTab(GuiGraphics context, int mouseX, int mouseY, String label, int x, int y, int tabWidth, boolean dragging) {
        String groupKey = getPresetGroupKeyFromTab(label);
        int groupColor = getPresetGroupColorByKey(groupKey);
        boolean hovered = isPointInRect(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT);
        float appear = dragging ? 1f : getPresetTabAppearProgress(label);
        boolean expanded = isPresetGroupExpanded(groupKey);
        int squareSize = 8;
        int squareX = x + (tabWidth - squareSize) / 2;
        int squareY = y + (TAB_HEIGHT - squareSize) / 2;
        context.fill(squareX + 1, squareY + 1, squareX + squareSize + 1, squareY + squareSize + 1, AnimationHelper.multiplyAlpha(UITheme.BACKGROUND_SECONDARY, appear * 0.75f));
        if (expanded) {
            context.fill(squareX + 1, squareY + 1, squareX + squareSize - 1, squareY + squareSize - 1, AnimationHelper.multiplyAlpha(UITheme.BACKGROUND_SECONDARY, appear));
            context.fill(squareX + 3, squareY + 3, squareX + squareSize - 3, squareY + squareSize - 3, AnimationHelper.multiplyAlpha(groupColor, appear));
        } else {
            context.fill(squareX + 1, squareY + 1, squareX + squareSize - 1, squareY + squareSize - 1, AnimationHelper.multiplyAlpha(groupColor, appear));
        }
        DrawContextBridge.drawBorderInLayer(context, squareX, squareY, squareSize, squareSize, AnimationHelper.multiplyAlpha(groupColor, appear));
    }

    private String getPresetTabDisplayLabel(String label) {
        if (isPresetGroupTab(label)) {
            return "";
        }
        return label;
    }

    private List<String> getRenderedPresetTabs() {
        List<String> tabs = new ArrayList<>();
        HashSet<String> groupedPresets = new HashSet<>();
        if (currentSettings != null && currentSettings.presetGroupOrder != null) {
            for (String groupKey : currentSettings.presetGroupOrder) {
                if (!isValidPresetGroupColorKey(groupKey)) {
                    continue;
                }
                tabs.add(getPresetGroupTabName(groupKey));
                if (isPresetGroupExpanded(groupKey)) {
                    for (String name : presetTabOrder) {
                        if (availablePresets.contains(name) && groupKey.equals(getPresetGroupKey(name))) {
                            tabs.add(name);
                            groupedPresets.add(name);
                        }
                    }
                }
            }
        }
        for (String name : presetTabOrder) {
            if (availablePresets.contains(name) && !groupedPresets.contains(name) && getPresetGroupKey(name).isEmpty()) {
                tabs.add(name);
            }
        }
        for (String name : availablePresets) {
            if (!tabs.contains(name) && getPresetGroupKey(name).isEmpty()) {
                tabs.add(name);
            }
        }
        String defaultPresetName = PresetManager.getDefaultPresetName();
        if (defaultPresetName != null && tabs.remove(defaultPresetName)) {
            tabs.add(0, defaultPresetName);
        }
        return tabs;
    }

    private List<String> getRenderedPresetTabsForWidth(int availableWidth) {
        List<String> allTabs = getRenderedPresetTabs();
        if (allTabs.isEmpty() || doPresetTabsFit(allTabs, availableWidth, PRESET_TAB_ADD_WIDTH)) {
            return allTabs;
        }

        List<String> visibleTabs = new ArrayList<>();
        for (String name : allTabs) {
            List<String> candidate = new ArrayList<>(visibleTabs);
            candidate.add(name);
            if (!doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                break;
            }
            visibleTabs.add(name);
        }

        if (activePresetName != null && allTabs.contains(activePresetName) && !visibleTabs.contains(activePresetName)) {
            while (!visibleTabs.isEmpty()) {
                List<String> candidate = new ArrayList<>(visibleTabs);
                candidate.add(activePresetName);
                if (doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                    visibleTabs.add(activePresetName);
                    break;
                }
                visibleTabs.remove(visibleTabs.size() - 1);
            }
            if (visibleTabs.isEmpty()) {
                List<String> candidate = new ArrayList<>();
                candidate.add(activePresetName);
                if (doPresetTabsFit(candidate, availableWidth, PRESET_TAB_ADD_WIDTH)) {
                    visibleTabs.add(activePresetName);
                }
            }
        }
        return visibleTabs;
    }

    private boolean isInlinePresetRenameActive() {
        return inlinePresetRenameField != null
            && inlinePresetRenameField.isVisible()
            && inlinePresetRenameName != null
            && !inlinePresetRenameName.isEmpty();
    }

    private boolean canStartInlinePresetRename(String presetName) {
        return presetName != null
            && !presetName.isEmpty()
            && !isPresetDeleteDisabled(presetName)
            && inlinePresetRenameField != null;
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
        String drawLabel = TextRenderUtil.trimWithEllipsis(this.font, getPresetTabDisplayLabel(label), textMaxWidth);
        int textX = x + PRESET_TAB_TEXT_PADDING;
        int textY = y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1;
        int textWidth = Math.max(4, this.font.width(drawLabel));
        return new int[]{textX, textY - 1, Math.min(textWidth, textMaxWidth), this.font.lineHeight + 2};
    }

    private void startInlinePresetRename(String presetName) {
        if (!canStartInlinePresetRename(presetName)) {
            return;
        }
        closeCreatePresetPopup();
        closeRenamePresetPopup();
        clearPendingPresetTabInteraction();
        endPresetTabDrag();
        inlinePresetRenameName = presetName;
        inlinePresetRenameField.setValue(presetName);
        inlinePresetRenameField.setVisible(true);
        inlinePresetRenameField.setEditable(true);
        inlinePresetRenameField.setFocused(true);
        inlinePresetRenameField.moveCursorToStart(false);
        inlinePresetRenameField.setHighlightPos(presetName.length());
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
            saveRootPresetWorkspace();
        }

        Optional<String> renamedPreset = PresetManager.renamePreset(currentName, desiredName);
        if (renamedPreset.isEmpty()) {
            return false;
        }
        if (currentSettings != null && currentSettings.presetGroupColors != null && currentSettings.presetGroupColors.containsKey(currentName)) {
            String groupKey = currentSettings.presetGroupColors.remove(currentName);
            currentSettings.presetGroupColors.put(renamedPreset.get(), groupKey);
            SettingsManager.save(currentSettings);
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
            renamed = renamePresetInternal(inlinePresetRenameName, inlinePresetRenameField.getValue());
        }
        if (commit && !renamed) {
            inlinePresetRenameField.setFocused(true);
            return;
        }
        inlinePresetRenameName = "";
        if (inlinePresetRenameField != null) {
            PathmindTextField.deactivate(inlinePresetRenameField);
        }
    }

    private void renderInlinePresetRenameField(GuiGraphics context, int mouseX, int mouseY, List<String> tabs, int[] tabWidths, int[] tabXs, int y, int dragIndex) {
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
            int fieldHeight = Math.max(this.font.lineHeight + 2, titleBounds[3]);
            int fieldY = titleBounds[1];
            int frameX = Math.max(drawX + 2, fieldX - 3);
            int frameY = y + 2;
            int frameWidth = Math.min(fieldWidth + 6, drawX + tabWidth - 2 - frameX);
            int frameHeight = TAB_HEIGHT - 4;
            context.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, UITheme.BACKGROUND_SECONDARY);
            DrawContextBridge.drawBorderInLayer(context, frameX, frameY, frameWidth, frameHeight, getAccentColor());
            inlinePresetRenameField.setVisible(true);
            inlinePresetRenameField.setEditable(true);
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
        return Mth.clamp(animation.getValue(), 0f, 1f);
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
            int width;
            if (isPresetGroupTab(label)) {
                width = PRESET_GROUP_TAB_WIDTH;
            } else {
                boolean deletable = !isPresetDeleteDisabled(label);
                int closeSpace = deletable ? (PRESET_TAB_CLOSE_GAP + PRESET_TAB_CLOSE_ICON_SIZE + PRESET_TAB_CLOSE_HITBOX_PADDING * 2) : 0;
                width = this.font.width(label) + PRESET_TAB_TEXT_PADDING * 2 + closeSpace;
                width = Mth.clamp(width, TAB_MIN_WIDTH, TAB_MAX_WIDTH);
            }
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

    private boolean doPresetTabsFit(List<String> tabNames, int availableWidth, int createTabWidth) {
        if (tabNames == null || tabNames.isEmpty()) {
            return false;
        }
        int total = createTabWidth;
        for (String label : tabNames) {
            total += getPresetTabMinimumVisibleWidth(label) + TAB_GAP;
        }
        return total <= Math.max(0, availableWidth);
    }

    private int getPresetTabMinimumVisibleWidth(String label) {
        if (isPresetGroupTab(label)) {
            return PRESET_GROUP_TAB_WIDTH;
        }
        return PRESET_TAB_HARD_MIN_WIDTH;
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

    private void refreshRoutineSidebarContext() {
        if (workspaceTabs.isEmpty() || workspaceTabs.get(0).graphData == null) {
            sidebar.setRoutineContext(List.of(), "");
            return;
        }
        String activeId = "";
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        activeId = getRoutineWorkspaceId(active);
        NodeGraphData.RoutineDefinitionData activeRoutine = getActiveRoutineWorkspace();
        if (!activeId.isBlank() && (nodeGraph.isEditingParameterField() || nodeGraph.isEditingEventNameField())) {
            if (activeRoutine != null) nodeGraph.syncRoutineDefinitionMetadata(activeRoutine);
        }
        NodeGraphData rootData = workspaceTabs.get(0).graphData;
        nodeGraph.setRoutineValidationContext(isLibraryRoutineWorkspace(active) ? getActiveRoutineRegistry() : rootData.getRoutines());
        sidebar.setRoutineContext(rootData.getRoutines(), activeId, activeRoutine);
    }

    private NodeGraphData.RoutineDefinitionData getActiveRoutineWorkspace() {
        if (workspaceTabs.isEmpty() || activeWorkspaceTabIndex < 0 || activeWorkspaceTabIndex >= workspaceTabs.size()) return null;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (isLibraryRoutineWorkspace(active)) return active.libraryRoutineDefinition;
        if (active.hostTemplateNodeId == null || !active.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) return null;
        String routineId = active.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
        return workspaceTabs.get(0).graphData.getRoutines().stream()
            .filter(routine -> routineId.equals(routine.getId())).findFirst().orElse(null);
    }

    private boolean isLibraryRoutineWorkspace(WorkspaceTab tab) {
        return tab != null && tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.startsWith(LIBRARY_ROUTINE_WORKSPACE_PREFIX);
    }

    private String getRoutineWorkspaceId(WorkspaceTab tab) {
        if (tab == null || tab.hostTemplateNodeId == null) return "";
        if (tab.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) return tab.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
        if (tab.hostTemplateNodeId.startsWith(LIBRARY_ROUTINE_WORKSPACE_PREFIX)) return tab.hostTemplateNodeId.substring(LIBRARY_ROUTINE_WORKSPACE_PREFIX.length());
        return "";
    }

    private List<NodeGraphData.RoutineDefinitionData> getActiveRoutineRegistry() {
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (!isLibraryRoutineWorkspace(active)) return workspaceTabs.get(0).graphData.getRoutines();
        NodeGraphData.RoutineDefinitionData edited = active.libraryRoutineDefinition;
        List<NodeGraphData.RoutineDefinitionData> registry = new ArrayList<>(com.pathmind.routines.RoutineLibraryManager.list());
        registry.removeIf(routine -> routine != null && edited != null && edited.getId().equals(routine.getId()));
        if (edited != null) registry.add(edited);
        return registry;
    }

    private void renderRoutineWorkspaceExitButton(GuiGraphics context, int mouseX, int mouseY) {
        if (getActiveRoutineWorkspace() == null) return;
        int x = getRoutineExitButtonX();
        int y = getRoutineExitButtonY();
        boolean hovered = PathmindWorkspaceChrome.contains(mouseX, mouseY, x, y, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
        PathmindRoutineUi.renderReturnButton(context, x, y, PLAY_BUTTON_SIZE, mouseX, mouseY,
            getHoverProgress("routine-return-button", hovered),
            PathmindRoutineUi.subtleRoutineAccent(NodeCategory.ROUTINES.getColor()));
    }

    private void createRoutineFromSidebar(String name) {
        persistActiveWorkspaceToTabs();
        WorkspaceTab root = workspaceTabs.get(0);
        NodeGraphData.RoutineDefinitionData routine = com.pathmind.routines.RoutineBuilderModel.createRoutine(name);
        root.graphData.getRoutines().add(routine);
        if (activeWorkspaceTabIndex == 0) {
            // Keep NodeGraph's in-memory registry aligned before openRoutineWorkspaceTab
            // persists the main tab again.
            nodeGraph.applyGraphDataSnapshot(root.graphData, false);
        }
        openRoutineWorkspaceTab(routine.getId());
    }

    private void addInputToActiveRoutine() {
        if (workspaceTabs.isEmpty()) return;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (getRoutineWorkspaceId(active).isBlank()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData.RoutineDefinitionData routine = getActiveRoutineWorkspace();
        if (routine == null) return;
        com.pathmind.routines.RoutineBuilderModel builder = new com.pathmind.routines.RoutineBuilderModel(routine);
        int number = routine.getInputs().size() + 1;
        NodeGraphData.RoutineInputData input = builder.addInput(number == 1 ? "input" : "input " + number, com.pathmind.routines.RoutineValueKind.TEXT);
        Node reporter = builder.createInputReporter(input.getId(), 420, 140 + (number - 1) * 80);
        if (reporter != null) nodeGraph.addNode(reporter);
        active.graphData = routine.getGraph();
        nodeGraph.markWorkspaceDirty();
    }

    private void editActiveRoutineInput(String inputId, int action) {
        if (workspaceTabs.isEmpty()) return;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (getRoutineWorkspaceId(active).isBlank()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData.RoutineDefinitionData routine = getActiveRoutineWorkspace();
        if (routine == null) return;
        com.pathmind.routines.RoutineBuilderModel builder = new com.pathmind.routines.RoutineBuilderModel(routine);
        if (action == 2) {
            builder.removeInput(inputId);
            for (Node node : new ArrayList<>(nodeGraph.getNodes())) {
                if (node.getType() == NodeType.ROUTINE_INPUT && inputId.equals(node.getRoutineInputId())) nodeGraph.removeNode(node);
            }
        } else {
            builder.moveInput(inputId, action);
        }
        nodeGraph.markWorkspaceDirty();
    }

    private void handleRoutineAction(String routineId, int action) {
        if (workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        NodeGraphData.RoutineDefinitionData routine = root.getRoutines().stream()
            .filter(candidate -> candidate != null && routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        if (action == 7) {
            openRenameRoutinePopup(routine);
            return;
        }
        if (action != 4 || !com.pathmind.routines.RoutineLifecycle.delete(root, routineId)) return;
        String hostId = ROUTINE_WORKSPACE_PREFIX + routineId;
        workspaceTabs.removeIf(tab -> hostId.equals(tab.hostTemplateNodeId));
        switchToRootAfterRoutineChange(root);
    }

    private void handleRoutineLibraryAction(String libraryRoutineId, int action) {
        if (action == 7) {
            com.pathmind.routines.RoutineLibraryManager.list().stream()
                .filter(routine -> routine != null && libraryRoutineId.equals(routine.getId()))
                .findFirst().ifPresent(this::openRenameLibraryRoutinePopup);
            return;
        }
        if (action != 3) return;
        String hostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + libraryRoutineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (!hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) continue;
            if (i == activeWorkspaceTabIndex) switchToWorkspaceTab(0);
            else if (i < activeWorkspaceTabIndex) activeWorkspaceTabIndex--;
            workspaceTabs.remove(i);
            break;
        }
        com.pathmind.routines.RoutineLibraryManager.delete(libraryRoutineId);
        refreshRoutineSidebarContext();
    }

    private boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY) {
        if (draggingFromRoutineLibrary || draggingSidebarNode == null
            || draggingSidebarNode.getType() != NodeType.ROUTINE_CALL
            || !sidebar.isRoutineLibraryDropTarget(mouseX, mouseY)) return false;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        root.getRoutines().stream()
            .filter(routine -> routine != null && draggingSidebarNode.getRoutineId().equals(routine.getId()))
            .findFirst().ifPresent(routine -> com.pathmind.routines.RoutineLibraryManager.share(routine, root.getRoutines()));
        refreshRoutineSidebarContext();
        return true;
    }

    private Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY) {
        int worldMouseX = nodeGraph.screenToWorldX(mouseX);
        int worldMouseY = nodeGraph.screenToWorldY(mouseY);
        if (!draggingFromRoutineLibrary) {
            return draggingSidebarNode != null
                ? nodeGraph.handleSidebarDrop(draggingSidebarNode, worldMouseX, worldMouseY)
                : nodeGraph.handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
        }
        NodeGraphData.RoutineDefinitionData imported = ensureDraggedLibraryRoutineImported();
        if (imported == null) return null;
        return nodeGraph.handleSidebarDrop(Node.createRoutineCall(imported, 0, 0), worldMouseX, worldMouseY);
    }

    private boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY) {
        if (!draggingFromRoutineLibrary || !sidebar.isRoutineListDropTarget(mouseX, mouseY)) return false;
        ensureDraggedLibraryRoutineImported();
        return true;
    }

    private NodeGraphData.RoutineDefinitionData ensureDraggedLibraryRoutineImported() {
        if (draggingSidebarNode == null || draggingSidebarNode.getRoutineId().isBlank() || workspaceTabs.isEmpty()) return null;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        String libraryRoutineId = draggingSidebarNode.getRoutineId();
        NodeGraphData.RoutineDefinitionData imported = root.getRoutines().stream()
            .filter(routine -> routine != null && (libraryRoutineId.equals(routine.getId())
                || libraryRoutineId.equals(routine.getLibraryRoutineId())))
            .findFirst().orElse(null);
        if (imported == null) {
            com.pathmind.routines.RoutineLibraryManager.ImportResult result =
                com.pathmind.routines.RoutineLibraryManager.importInto(root, libraryRoutineId);
            if (!result.added() || result.routine() == null) return null;
            imported = result.routine();
            if (activeWorkspaceTabIndex == 0) nodeGraph.applyGraphDataSnapshot(root, false);
            else nodeGraph.setRoutineValidationContext(root.getRoutines());
        }
        refreshRoutineSidebarContext();
        return imported;
    }

    private void switchToRootAfterRoutineChange(NodeGraphData root) {
        nodeGraph.setActiveRoutineWorkspaceId("");
        nodeGraph.applyGraphDataSnapshot(root, false);
        activeWorkspaceTabIndex = 0;
        nodeGraph.markWorkspaceDirty();
    }

    private void openRoutineWorkspaceTab(String routineId) {
        if (routineId == null || workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        WorkspaceTab root = workspaceTabs.get(0);
        NodeGraphData.RoutineDefinitionData routine = root.graphData.getRoutines().stream()
            .filter(candidate -> routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        new com.pathmind.routines.RoutineBuilderModel(routine).ensureDefinitionGraph();
        String hostId = ROUTINE_WORKSPACE_PREFIX + routineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) {
                switchToWorkspaceTab(i);
                return;
            }
        }
        workspaceTabs.add(new WorkspaceTab(routine.getName(), routine.getGraph(), 0, hostId));
        switchToWorkspaceTab(workspaceTabs.size() - 1);
    }

    private void openLibraryRoutineWorkspaceTab(String routineId) {
        if (routineId == null || workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        String hostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + routineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) { switchToWorkspaceTab(i); return; }
        }
        NodeGraphData.RoutineDefinitionData routine = com.pathmind.routines.RoutineLibraryManager.list().stream()
            .filter(candidate -> candidate != null && routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        new com.pathmind.routines.RoutineBuilderModel(routine).ensureDefinitionGraph();
        workspaceTabs.add(new WorkspaceTab(routine.getName(), routine.getGraph(), null, hostId, routine));
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
        String routineWorkspaceId = getRoutineWorkspaceId(target);
        activeWorkspaceTabIndex = targetIndex;
        nodeGraph.setActiveRoutineWorkspaceId(routineWorkspaceId);
        nodeGraph.setRoutineValidationContext(isLibraryRoutineWorkspace(target)
            ? getActiveRoutineRegistry() : workspaceTabs.get(0).graphData.getRoutines());
        NodeGraphData data = target.graphData != null ? target.graphData : createDefaultTemplateGraphData();
        nodeGraph.applyGraphDataSnapshot(data, false);
    }

    private void persistActiveWorkspaceToTabs() {
        if (workspaceTabs.isEmpty() || activeWorkspaceTabIndex < 0 || activeWorkspaceTabIndex >= workspaceTabs.size()) {
            return;
        }
        WorkspaceTab tab = workspaceTabs.get(activeWorkspaceTabIndex);
        tab.graphData = nodeGraph.exportGraphDataSnapshot();
        if (isLibraryRoutineWorkspace(tab) && tab.libraryRoutineDefinition != null) {
            com.pathmind.routines.RoutineWorkspaceSupport.syncMetadata(tab.libraryRoutineDefinition, tab.graphData);
            tab.libraryRoutineDefinition.setGraph(tab.graphData);
            tab.label = tab.libraryRoutineDefinition.getName();
            com.pathmind.routines.RoutineLibraryManager.save(tab.libraryRoutineDefinition);
            return;
        }
        if (tab.parentTabIndex != null && tab.parentTabIndex >= 0 && tab.parentTabIndex < workspaceTabs.size()) {
            WorkspaceTab parent = workspaceTabs.get(tab.parentTabIndex);
            if (tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) {
                String routineId = tab.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
                for (NodeGraphData.RoutineDefinitionData routine : parent.graphData.getRoutines()) {
                    if (routineId.equals(routine.getId())) {
                        for (NodeGraphData.NodeData nodeData : tab.graphData.getNodes()) {
                            if (nodeData != null && nodeData.getType() == NodeType.ROUTINE_ENTRY) {
                                for (NodeGraphData.ParameterData parameter : nodeData.getParameters()) {
                                    if (parameter != null && "Name".equals(parameter.getName()) && parameter.getValue() != null && !parameter.getValue().isBlank()) {
                                        routine.setName(parameter.getValue().trim());
                                    }
                                }
                            }
                            if (nodeData != null && nodeData.getType() == NodeType.ROUTINE_INPUT
                                && routineId.equals(nodeData.getRoutineId())) {
                                NodeGraphData.RoutineInputData input = routine.getInputs().stream()
                                    .filter(candidate -> candidate.getId().equals(nodeData.getRoutineInputId())).findFirst().orElse(null);
                                if (input != null) {
                                    for (NodeGraphData.ParameterData parameter : nodeData.getParameters()) {
                                        if (parameter == null || parameter.getValue() == null) continue;
                                        if ("Label".equals(parameter.getName()) && !parameter.getValue().isBlank()) input.setLabel(parameter.getValue().trim());
                                        if ("valuekind".equals(parameter.getId())) input.setValueKind(com.pathmind.routines.RoutineValueKind.fromSerialized(parameter.getValue()).name());
                                        if ("Default".equals(parameter.getName())) input.setDefaultValue(parameter.getValue());
                                        if ("Required".equals(parameter.getName())) input.setRequired(Boolean.parseBoolean(parameter.getValue()));
                                    }
                                }
                            }
                        }
                        routine.setGraph(tab.graphData);
                        tab.label = routine.getName();
                        return;
                    }
                }
            }
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

    private NodeGraphData snapshotRootPresetWorkspace() {
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        if (!workspaceTabs.isEmpty() && workspaceTabs.get(0).graphData != null) {
            return workspaceTabs.get(0).graphData;
        }
        return nodeGraph.exportGraphDataSnapshot();
    }

    private boolean saveRootPresetWorkspace() {
        return NodeGraphPersistence.saveNodeGraphDataForPreset(activePresetName, snapshotRootPresetWorkspace());
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
        nodeGraph.setActiveRoutineWorkspaceId("");
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
        nodeGraph.stopStickyNoteEditing(true);
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        restoreRootWorkspaceIfNeeded();

        saveRootPresetWorkspace();

        PresetManager.setActivePreset(activePresetName);
    }

    @Override
    public void onClose() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        restoreSystemCursor();
        super.onClose();
    }

    @Override
    public void removed() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        if (uiUtilsOverlayPrevEnabled != null) {
            UiUtilsProxy.setOverlayEnabled(uiUtilsOverlayPrevEnabled);
            uiUtilsOverlayPrevEnabled = null;
        }
        restoreSystemCursor();
        super.removed();
    }

    private void renderClearConfirmationPopup(GuiGraphics context, int mouseX, int mouseY) {
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

        context.drawCenteredString(
            this.font,
            Component.translatable("pathmind.popup.clearWorkspace.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        drawPopupTextWithEllipsis(
            context,
            Component.translatable("pathmind.popup.clearWorkspace.message").getString(),
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
            Component.translatable("pathmind.button.cancel"), PathmindPopupRenderer.ButtonStyle.DEFAULT, clearPopupAnimation);
        drawPopupButton(context, confirmX, buttonY, buttonWidth, buttonHeight, confirmHovered,
            Component.translatable("pathmind.button.clear"), PathmindPopupRenderer.ButtonStyle.PRIMARY, clearPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderImportExportPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
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

        context.drawCenteredString(
            this.font,
            Component.translatable("pathmind.popup.importExport.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int infoY = popupY + 44;
        String importInfo = Component.translatable("pathmind.popup.importExport.importInfo").getString();
        drawPopupTextWithEllipsis(context, importInfo, popupX + 20, infoY, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        String exportInfo = Component.translatable("pathmind.popup.importExport.exportInfo").getString();
        drawPopupTextWithEllipsis(context, exportInfo, popupX + 20, infoY + 14, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        Path defaultPath = NodeGraphPersistence.getDefaultSavePath();
        if (defaultPath != null) {
            String defaultLabel = Component.translatable("pathmind.popup.importExport.defaultSave", defaultPath.toString()).getString();
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

        boolean importHovered = !importExportBusy && isPointInRect(mouseX, mouseY, importX, buttonY, buttonWidth, buttonHeight);
        boolean exportHovered = !importExportBusy && isPointInRect(mouseX, mouseY, exportX, buttonY, buttonWidth, buttonHeight);
        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, importX, buttonY, buttonWidth, buttonHeight, importHovered,
            Component.translatable("pathmind.button.import"), PathmindPopupRenderer.ButtonStyle.PRIMARY, importExportPopupAnimation);
        drawPopupButton(context, exportX, buttonY, buttonWidth, buttonHeight, exportHovered,
            Component.translatable("pathmind.button.export"), PathmindPopupRenderer.ButtonStyle.PRIMARY, importExportPopupAnimation);
        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT, importExportPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderInfoPopup(GuiGraphics context, int mouseX, int mouseY) {
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

        context.drawCenteredString(
            this.font,
            INFO_POPUP_TITLE_TEXT,
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(infoPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int textStartY = popupY + 42;
        int lineSpacing = 12;
        int centerX = popupX + scaledWidth / 2;

        String authorLine = Component.translatable("pathmind.popup.info.createdBy", INFO_POPUP_AUTHOR).getString();
        String targetLine = Component.translatable("pathmind.popup.info.builtForMinecraft", INFO_POPUP_TARGET_VERSION).getString();
        String currentLine = Component.translatable("pathmind.popup.info.runningMinecraft", getCurrentMinecraftVersion()).getString();
        String buildLine = Component.translatable("pathmind.popup.info.currentBuild", getModVersion()).getString();
        String loaderLine = LoaderMetadata.getLoaderName() + ": " + getLoaderVersion();

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
            Component.translatable("pathmind.button.close"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            infoPopupAnimation
        );
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderMissingBaritonePopup(GuiGraphics context, int mouseX, int mouseY) {
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
        drawPopupCenteredTextWithEllipsis(context, Component.translatable("pathmind.popup.missingBaritone.title").getString(), centerX, messageY, maxCenteredWidth, getPopupAnimatedColor(missingBaritonePopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, Component.translatable("pathmind.popup.missingBaritone.message").getString(), centerX, messageY + 16, maxCenteredWidth, getPopupAnimatedColor(missingBaritonePopupAnimation, UITheme.TEXT_PRIMARY));
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

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Component.translatable("pathmind.button.openLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, missingBaritonePopupAnimation);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Component.translatable("pathmind.button.copyLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, missingBaritonePopupAnimation);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT, missingBaritonePopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderMissingUiUtilsPopup(GuiGraphics context, int mouseX, int mouseY) {
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
        drawPopupCenteredTextWithEllipsis(context, Component.translatable("pathmind.popup.missingUiUtils.title").getString(), centerX, messageY, maxCenteredWidth, getPopupAnimatedColor(missingUiUtilsPopupAnimation, UITheme.TEXT_PRIMARY));
        drawPopupCenteredTextWithEllipsis(context, Component.translatable("pathmind.popup.missingUiUtils.message").getString(), centerX, messageY + 16, maxCenteredWidth, getPopupAnimatedColor(missingUiUtilsPopupAnimation, UITheme.TEXT_PRIMARY));
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

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Component.translatable("pathmind.button.openLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, missingUiUtilsPopupAnimation);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Component.translatable("pathmind.button.copyLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, missingUiUtilsPopupAnimation);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT, missingUiUtilsPopupAnimation);
        disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawTitle(GuiGraphics context, int mouseX, int mouseY, float underlineProgress) {
        drawTitleMenuButton(context, mouseX, mouseY, underlineProgress);
    }

    private void drawTitleMenuButton(GuiGraphics context, int mouseX, int mouseY, float hoverProgress) {
        int x = getTitleTextX();
        int y = getTitleTextY();
        boolean hovered = isTitleHovered(mouseX, mouseY);
        int iconColor = (hovered || presetDropdownOpen) ? getAccentColor() : UITheme.ICON_MUTED_BRIGHT;
        int centerX = x + PRESET_MENU_BUTTON_SIZE / 2;
        int centerY = y + PRESET_MENU_BUTTON_SIZE / 2;
        int lineHalfWidth = 4;
        int alphaColor = AnimationHelper.multiplyAlpha(iconColor, Mth.clamp(0.75f + hoverProgress * 0.25f, 0f, 1f));
        for (int i = -1; i <= 1; i++) {
            int lineY = centerY + i * 3;
            context.hLine(centerX - lineHalfWidth, centerX + lineHalfWidth, lineY, alphaColor);
        }
    }

    void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y, int maxWidth, int color) {
        PathmindPopupRenderer.drawTextWithEllipsis(context, this.font, text, x, y, maxWidth, color);
    }

    private void drawPopupCenteredTextWithEllipsis(GuiGraphics context, String text, int centerX, int y, int maxWidth, int color) {
        PathmindPopupRenderer.drawCenteredTextWithEllipsis(context, this.font, text, centerX, y, maxWidth, color);
    }

    boolean enablePopupScissor(GuiGraphics context, int popupX, int popupY, int scaledWidth, int scaledHeight) {
        return PathmindPopupRenderer.enableScissor(context, popupX, popupY, scaledWidth, scaledHeight);
    }

    void disablePopupScissor(GuiGraphics context, boolean enabled) {
        PathmindPopupRenderer.disableScissor(context, enabled);
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

        if (!importExportBusy && isPointInRect(mouseXi, mouseYi, importX, buttonY, buttonWidth, buttonHeight)) {
            attemptImport();
            return true;
        }

        if (!importExportBusy && isPointInRect(mouseXi, mouseYi, exportX, buttonY, buttonWidth, buttonHeight)) {
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
        Util.getPlatform().openUri(BaritoneDependencyChecker.DOWNLOAD_URL);
    }

    private void copyBaritoneDownloadLink() {
        if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
            this.minecraft.keyboardHandler.setClipboard(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    private void openUiUtilsDownloadLink() {
        Util.getPlatform().openUri(UI_UTILS_DOWNLOAD_URL);
    }

    private void copyUiUtilsDownloadLink() {
        if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
            this.minecraft.keyboardHandler.setClipboard(UI_UTILS_DOWNLOAD_URL);
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

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered, Component label, boolean primary) {
        PathmindPopupRenderer.ButtonStyle style = primary ? PathmindPopupRenderer.ButtonStyle.PRIMARY : PathmindPopupRenderer.ButtonStyle.DEFAULT;
        drawPopupButton(context, x, y, width, height, hovered, label, style);
    }

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered, Component label, PathmindPopupRenderer.ButtonStyle style) {
        drawPopupButton(context, x, y, width, height, hovered, label, style, null);
    }

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered,
                                 Component label, PathmindPopupRenderer.ButtonStyle style, PopupAnimationHandler animation) {
        float hoverProgress = getHoverProgress(PathmindPopupRenderer.buttonHoverKey(style, label, x, y, width, height), hovered);
        PathmindPopupRenderer.drawButton(
            context,
            this.font,
            x,
            y,
            width,
            height,
            label,
            style,
            hoverProgress,
            getAccentColor(),
            animation
        );
    }

    void drawPopupContainer(GuiGraphics context, int x, int y, int width, int height, PopupAnimationHandler animation) {
        PathmindPopupRenderer.drawContainer(context, x, y, width, height, animation);
    }

    int getPopupAnimatedColor(PopupAnimationHandler animation, int baseColor) {
        return PathmindPopupRenderer.animatedColor(animation, baseColor);
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
        importExportBusy = false;
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
        importExportBusy = true;
        setImportExportStatus(Component.translatable("pathmind.status.waitingForImportFile").getString(), UITheme.TEXT_SECONDARY);
        WorkspaceFileAccess.supplyAsync(() -> openWorkspaceImportDialog(defaultPath))
            .whenComplete((selection, throwable) -> runOnClientThread(() -> {
                if (throwable != null) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.failedOpenImportDialog").getString(), UITheme.STATE_ERROR);
                    return;
                }
                if (selection == null) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.importCancelled").getString(), UITheme.TEXT_SECONDARY);
                    return;
                }
                try {
                    Path path = Paths.get(selection.trim());
                    beginImportFromPath(path);
                } catch (InvalidPathException ex) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
                }
            }));
    }

    private void beginImportFromPath(Path path) {
        try {
            lastImportExportPath = path;
            Path fileName = path.getFileName();
            String fileLabel = fileName != null ? fileName.toString() : path.toString();
            String currentPresetName = activePresetName;
            NodeGraphData currentPresetSnapshot = snapshotRootPresetWorkspace();
            setImportExportStatus(Component.translatable("pathmind.status.importingWorkspace").getString(), UITheme.TEXT_SECONDARY);
            WorkspaceFileAccess.supplyAsync(() -> {
                if (currentPresetSnapshot != null && currentPresetName != null && !currentPresetName.isBlank()) {
                    NodeGraphPersistence.saveNodeGraphDataForPreset(currentPresetName, currentPresetSnapshot);
                }
                Optional<String> importedPreset = PresetManager.importPresetFromFile(path);
                if (importedPreset.isEmpty()) {
                    return ImportOperationResult.failed(fileLabel);
                }
                NodeGraphData importedData = NodeGraphPersistence.loadNodeGraphForPreset(importedPreset.get());
                return ImportOperationResult.succeeded(fileLabel, importedPreset.get(), importedData);
            }).whenComplete((result, throwable) -> runOnClientThread(() -> {
                importExportBusy = false;
                if (throwable != null || result == null || !result.success) {
                    setImportExportStatus(Component.translatable("pathmind.status.failedImportWorkspaceFrom", fileLabel).getString(), UITheme.STATE_ERROR);
                    return;
                }
                applyImportedPreset(result.presetName, result.importedData);
                setImportExportStatus(
                    Component.translatable("pathmind.status.importedWorkspaceAsPreset", result.fileLabel, result.presetName).getString(),
                    UITheme.STATE_SUCCESS
                );
            }));
        } catch (InvalidPathException ex) {
            setImportExportStatus(Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
        }
    }

    private void attemptExport() {
        Path defaultSavePath = Optional.ofNullable(lastImportExportPath)
                .orElseGet(NodeGraphPersistence::getDefaultSavePath);
        String defaultPathString = defaultSavePath != null ? defaultSavePath.toString() : "workspace.json";
        importExportBusy = true;
        setImportExportStatus(Component.translatable("pathmind.status.waitingForExportPath").getString(), UITheme.TEXT_SECONDARY);
        WorkspaceFileAccess.supplyAsync(() -> openWorkspaceExportDialog(defaultPathString))
            .whenComplete((selection, throwable) -> runOnClientThread(() -> {
                if (throwable != null) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.failedOpenExportDialog").getString(), UITheme.STATE_ERROR);
                    return;
                }
                if (selection == null) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.exportCancelled").getString(), UITheme.TEXT_SECONDARY);
                    return;
                }
                try {
                    Path path = Paths.get(selection.trim());
                    beginExportToPath(path);
                } catch (InvalidPathException ex) {
                    importExportBusy = false;
                    setImportExportStatus(Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
                }
            }));
    }

    private void beginExportToPath(Path path) {
        try {
            NodeGraphData snapshot = snapshotRootPresetWorkspace();
            setImportExportStatus(Component.translatable("pathmind.status.exportingWorkspace").getString(), UITheme.TEXT_SECONDARY);
            WorkspaceFileAccess.supplyExportAsync(() -> NodeGraphPersistence.saveNodeGraphDataToPath(snapshot, path))
                .whenComplete((success, throwable) -> runOnClientThread(() -> {
                    importExportBusy = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        setImportExportStatus(Component.translatable("pathmind.status.failedExportWorkspace").getString(), UITheme.STATE_ERROR);
                        return;
                    }
                    lastImportExportPath = path;
                    Path fileName = path.getFileName();
                    setImportExportStatus(Component.translatable("pathmind.status.exportedWorkspaceTo", fileName != null ? fileName.toString() : path.toString()).getString(), UITheme.STATE_SUCCESS);
                }));
        } catch (InvalidPathException ex) {
            setImportExportStatus(Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
        }
    }

    private String openWorkspaceImportDialog(String defaultPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            return TinyFileDialogs.tinyfd_openFileDialog(
                Component.translatable("pathmind.dialog.importWorkspace").getString(),
                defaultPath,
                filters,
                filters != null ? Component.translatable("pathmind.dialog.jsonFiles").getString() : null,
                false
            );
        }
    }

    private String openWorkspaceExportDialog(String defaultPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            return TinyFileDialogs.tinyfd_saveFileDialog(
                Component.translatable("pathmind.dialog.exportWorkspace").getString(),
                defaultPath,
                filters,
                filters != null ? Component.translatable("pathmind.dialog.jsonFiles").getString() : null
            );
        }
    }

    private void applyImportedPreset(String presetName, NodeGraphData importedData) {
        if (presetName == null || presetName.isBlank()) {
            return;
        }
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        movePresetTabToEnd(presetName);
        nodeGraph.setActivePreset(activePresetName);
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        clearPopupAnimation.hide();
        closeSettingsPopup();
        presetDropdownOpen = false;

        if (!nodeGraph.applyGraphDataSnapshot(importedData, false)) {
            nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }
        resetWorkspaceTabsFromCurrentGraph();
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
        nodeGraph.restoreSessionViewportState();
        updateImportExportPathFromPreset();
    }

    private void runOnClientThread(Runnable task) {
        Minecraft minecraftClient = this.minecraft;
        if (minecraftClient == null || task == null) {
            return;
        }
        minecraftClient.execute(() -> {
            if (this.minecraft == null) {
                return;
            }
            task.run();
        });
    }

    private static final class ImportOperationResult {
        private final boolean success;
        private final String fileLabel;
        private final String presetName;
        private final NodeGraphData importedData;

        private ImportOperationResult(boolean success, String fileLabel, String presetName, NodeGraphData importedData) {
            this.success = success;
            this.fileLabel = fileLabel;
            this.presetName = presetName;
            this.importedData = importedData;
        }

        private static ImportOperationResult succeeded(String fileLabel, String presetName, NodeGraphData importedData) {
            return new ImportOperationResult(true, fileLabel, presetName, importedData);
        }

        private static ImportOperationResult failed(String fileLabel) {
            return new ImportOperationResult(false, fileLabel, null, null);
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
            case GLFW.GLFW_KEY_X:
                return nodeGraph.cutSelectedNodeToClipboard();
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

    private void renderPlayButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getPlayButtonX();
        int buttonY = getPlayButtonY();
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("play-button", hovered || executing);
        PathmindWorkspaceChrome.renderPlayButton(
            context,
            buttonX,
            buttonY,
            PLAY_BUTTON_SIZE,
            mouseX,
            mouseY,
            disabled,
            executing,
            hoverProgress,
            getAccentColor()
        );
    }

    private void renderStopButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getStopButtonX();
        int buttonY = getStopButtonY();
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("stop-button", hovered || executing);
        PathmindWorkspaceChrome.renderStopButton(
            context,
            buttonX,
            buttonY,
            STOP_BUTTON_SIZE,
            mouseX,
            mouseY,
            disabled,
            executing,
            hoverProgress,
            getAccentColor()
        );
    }

    private void renderPresetDropdown(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int dropdownX = getPresetDropdownX();
        int dropdownY = getPresetDropdownY();

        if (disabled && presetDropdownOpen) {
            presetDropdownOpen = false;
        }

        float animProgress = DropdownLayoutHelper.updateOpenAnimation(presetDropdownAnimation, presetDropdownOpen);

        // Don't render options if animation is fully closed
        if (animProgress <= 0.001f) {
            return;
        }

        int optionStartY = dropdownY;
        int optionCount = availablePresets.size() + 1;
        DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
        presetDropdownScrollOffset = Mth.clamp(presetDropdownScrollOffset, 0, layout.maxScrollOffset);
        int fullOptionsHeight = layout.height;
        int animatedHeight = DropdownLayoutHelper.getRevealHeight(fullOptionsHeight, animProgress);

        context.enableScissor(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + animatedHeight);

        UIStyleHelper.drawScrollContainer(context, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, fullOptionsHeight,
            UIStyleHelper.getScrollContainerPalette(getAccentColor(), animProgress, true, false));

        float smoothScrollOffset = DropdownLayoutHelper.updateSmoothScroll(presetDropdownSmoothScroll, presetDropdownScrollOffset, layout.maxScrollOffset);
        DropdownLayoutHelper.ScrollWindow scrollWindow = DropdownLayoutHelper.getSmoothScrollWindow(
            smoothScrollOffset,
            layout.visibleCount,
            optionCount,
            PRESET_OPTION_HEIGHT
        );
        for (int index = scrollWindow.firstIndex; index < scrollWindow.endIndex; index++) {
            int optionY = optionStartY + (index - scrollWindow.firstIndex) * PRESET_OPTION_HEIGHT + scrollWindow.pixelOffset;
            if (index < availablePresets.size()) {
                String preset = availablePresets.get(index);
                boolean optionHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(getAccentColor(), optionHovered ? 1f : 0f, preset.equals(activePresetName), false);
                UIStyleHelper.drawDropdownRow(context, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1, rowPalette);
                int textColor = preset.equals(activePresetName) ? getAccentColor() : UITheme.TEXT_PRIMARY;
                int textX = dropdownX + PRESET_TEXT_LEFT_PADDING;
                int iconSpace = PRESET_DELETE_ICON_SIZE
                        + PRESET_DELETE_ICON_MARGIN
                        + PRESET_TEXT_ICON_GAP
                        + PRESET_RENAME_ICON_SIZE
                        + PRESET_TEXT_ICON_GAP;
                int textMaxWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING - iconSpace;
                String presetLabel = TextRenderUtil.trimWithEllipsis(this.font, preset, textMaxWidth);
                context.drawString(this.font, Component.literal(presetLabel), textX, optionY + 5, textColor);

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
                context.hLine(dropdownX + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 2, optionY, UITheme.BORDER_SUBTLE);
                boolean createHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                UIStyleHelper.DropdownRowPalette createPalette = UIStyleHelper.getDropdownRowPalette(getAccentColor(), createHovered ? 1f : 0f, false, false);
                UIStyleHelper.drawDropdownRow(context, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1, createPalette);
                int createTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
                String createLabel = TextRenderUtil.trimWithEllipsis(this.font, Component.translatable("pathmind.preset.createNew").getString(), createTextWidth);
                context.drawString(this.font, Component.literal(createLabel), dropdownX + PRESET_TEXT_LEFT_PADDING, optionY + 5, getAccentColor());
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
            Math.round(smoothScrollOffset),
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

        context.disableScissor();
    }

    private int getPresetDropdownX() {
        int preferredX = getPresetOverflowTabRight() - PRESET_DROPDOWN_WIDTH;
        return Mth.clamp(preferredX, PRESET_DROPDOWN_MARGIN, this.width - PRESET_DROPDOWN_WIDTH - PRESET_DROPDOWN_MARGIN);
    }

    private int getPresetDropdownY() {
        return TAB_BAR_TOP + TAB_HEIGHT + 2;
    }

    private int getPresetOverflowTabRight() {
        return getTitleTextX() + PRESET_MENU_BUTTON_SIZE;
    }

    private int getPlayButtonX() {
        return PathmindWorkspaceChrome.playButtonX(this.width, PLAY_BUTTON_SIZE, PLAY_BUTTON_MARGIN);
    }

    private int getPlayButtonY() {
        return PathmindWorkspaceChrome.playButtonY(TITLE_BAR_HEIGHT, PLAY_BUTTON_MARGIN);
    }

    private int getValidationButtonX() {
        if (shouldShowExecutionControls()) {
            return getPlayButtonX();
        }
        return this.width - VALIDATION_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    private int getValidationButtonY() {
        if (getActiveRoutineWorkspace() != null) {
            return getPlayButtonY() + PLAY_BUTTON_SIZE + CONTROL_BUTTON_GAP;
        }
        if (shouldShowExecutionControls()) {
            return getPlayButtonY() + PLAY_BUTTON_SIZE + CONTROL_BUTTON_GAP;
        }
        return PathmindWorkspaceChrome.playButtonY(TITLE_BAR_HEIGHT, PLAY_BUTTON_MARGIN);
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
        return PathmindWorkspaceChrome.stopButtonX(getPlayButtonX(), CONTROL_BUTTON_GAP, STOP_BUTTON_SIZE);
    }

    private int getStopButtonY() {
        return getPlayButtonY();
    }

    private int getRoutineExitButtonX() {
        return getStopButtonX() - CONTROL_BUTTON_GAP - PLAY_BUTTON_SIZE;
    }

    private int getRoutineExitButtonY() {
        return getPlayButtonY();
    }

    private DropdownLayoutHelper.Layout getPresetDropdownLayout(int optionStartY) {
        int optionCount = availablePresets.size() + 1;
        int visibleCount = Math.min(optionCount, 10);
        return DropdownLayoutHelper.calculate(optionCount, PRESET_OPTION_HEIGHT, visibleCount, optionStartY, this.height);
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

    private boolean handlePresetDropdownMouseDown(double mouseX, double mouseY) {
        int dropdownX = getPresetDropdownX();
        int optionStartY = getPresetDropdownY();
        DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
        presetDropdownScrollOffset = Mth.clamp(presetDropdownScrollOffset, 0, layout.maxScrollOffset);
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
                pendingPresetDropdownDragName = selectedPreset;
                pendingPresetDropdownPressMouseX = (int) mouseX;
                pendingPresetDropdownPressMouseY = (int) mouseY;
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
        createRoutineNaming = false;
        presetDropdownOpen = false;
        clearCreatePresetStatus();
        closeInfoPopup();
        stopInlinePresetRename(false);
        closeRenamePresetPopup();
        createPresetPopupAnimation.show();
        if (createPresetField != null) {
            createPresetField.setValue("");
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            createPresetField.setFocused(true);
        }
    }

    private void openCreateRoutinePopup() {
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = "";
    }

    int getCreateNamingPopupHeight() {
        return createRoutineNaming ? 148 : CREATE_PRESET_POPUP_HEIGHT;
    }

    private void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return;
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = routine.getId();
        pendingLibraryRoutineRenameId = "";
        if (createPresetField != null) {
            createPresetField.setValue(routine.getName() == null ? "" : routine.getName());
            createPresetField.setFocused(true);
        }
    }

    private void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return;
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = routine.getId();
        if (createPresetField != null) {
            createPresetField.setValue(routine.getName() == null ? "" : routine.getName());
            createPresetField.setFocused(true);
        }
    }

    void closeCreatePresetPopup() {
        createRoutineNaming = false;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = "";
        createPresetPopupAnimation.hide();
        clearCreatePresetStatus();
        if (createPresetField != null) {
            PathmindTextField.deactivate(createPresetField);
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
            renamePresetField.setValue(presetName);
            renamePresetField.setVisible(true);
            renamePresetField.setEditable(true);
            renamePresetField.setFocused(true);
        }
    }

    void closeRenamePresetPopup() {
        renamePresetPopupAnimation.hide();
        pendingPresetRenameName = "";
        clearRenamePresetStatus();
        if (renamePresetField != null) {
            PathmindTextField.deactivate(renamePresetField);
        }
    }

    private void openNodeSearch(int anchorX, int anchorY) {
        int minX = sidebar.getWidth() + 8;
        int maxX = this.width - NODE_SEARCH_FIELD_WIDTH - 8;
        nodeSearchFieldX = Mth.clamp(anchorX, minX, Math.max(minX, maxX));
        nodeSearchFieldY = Mth.clamp(anchorY, TITLE_BAR_HEIGHT + 8,
            Math.max(TITLE_BAR_HEIGHT + 8, this.height - NODE_SEARCH_FIELD_HEIGHT - 8));
        nodeSearchWorldX = nodeGraph.screenToWorldX(nodeSearchFieldX);
        nodeSearchWorldY = nodeGraph.screenToWorldY(nodeSearchFieldY);
        nodeSearchScale = Math.max(0.05f, nodeGraph.getZoomScale());
        nodeSearchOpen = true;
        if (nodeSearchField != null) {
            nodeSearchField.setX(nodeSearchFieldX);
            nodeSearchField.setY(nodeSearchFieldY);
            nodeSearchField.setValue("");
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
            PathmindTextField.deactivate(nodeSearchField);
            nodeSearchField.setSuggestion(null);
        }
    }

    private void updateNodeSearchMatch() {
        if (!nodeSearchOpen || nodeSearchField == null) {
            return;
        }
        String query = nodeSearchField.getValue();
        nodeSearchField.setSuggestion(null);
        refreshNodeSearchResults(query);
    }

    private void renderNodeSearchField(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!nodeSearchOpen || nodeSearchField == null) {
            return;
        }

        updateNodeSearchLayout();
        int transformedMouseX = toNodeSearchSpaceX(mouseX);
        int transformedMouseY = toNodeSearchSpaceY(mouseY);
        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, nodeSearchFieldX, nodeSearchFieldY);
        MatrixStackBridge.scale(matrices, nodeSearchScale, nodeSearchScale);
        MatrixStackBridge.translate(matrices, -nodeSearchFieldX, -nodeSearchFieldY);

        boolean focused = nodeSearchField.isFocused();
        UIStyleHelper.FieldPalette searchPalette = UIStyleHelper.getSearchFieldPalette(getAccentColor(), focused ? 1f : 0f, focused, false);
        UIStyleHelper.drawFieldFrame(context, nodeSearchFieldX, nodeSearchFieldY, NODE_SEARCH_FIELD_WIDTH, NODE_SEARCH_FIELD_HEIGHT, searchPalette);
        int iconX = nodeSearchFieldX + 6;
        int iconY = nodeSearchFieldY + (NODE_SEARCH_FIELD_HEIGHT - 9) / 2;
        drawNodeSearchIcon(context, iconX, iconY, searchPalette.textColor());
        int textFieldHeight = Math.max(10, NODE_SEARCH_FIELD_HEIGHT - TEXT_FIELD_VERTICAL_PADDING * 2);
        nodeSearchField.setPosition(nodeSearchFieldX + 20, nodeSearchFieldY + TEXT_FIELD_VERTICAL_PADDING);
        nodeSearchField.setWidth(NODE_SEARCH_FIELD_WIDTH - 26);
        nodeSearchField.setHeight(textFieldHeight);
        nodeSearchField.render(context, transformedMouseX, transformedMouseY, delta);

        renderNodeSearchDropdown(context, mouseX, mouseY);
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
            if (!sidebar.isNodeAvailable(nodeType)) {
                continue;
            }
            int score = scoreNodeSearchCandidate(nodeType, normalizedQuery);
            if (score <= 0) {
                continue;
            }
            nodeSearchResults.add(new NodeSearchResult(
                nodeType,
                nodeType.getDisplayName(),
                getNodeSearchCategoryLabel(nodeType),
                score
            ));
        }
        if (!workspaceTabs.isEmpty() && workspaceTabs.get(0).graphData != null) {
            for (NodeGraphData.RoutineDefinitionData routine : workspaceTabs.get(0).graphData.getRoutines()) {
                if (routine == null) continue;
                int score = scoreSearchCandidate(routine.getName(), normalizedQuery);
                if (score > 0) nodeSearchResults.add(new NodeSearchResult(NodeType.ROUTINE_CALL, routine.getName(),
                    NodeCategory.ROUTINES.getDisplayName(), score + 20, routine));
            }
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

    private String getNodeSearchCategoryLabel(NodeType nodeType) {
        NodeCatalog.NodePlacement placement = NodeCatalog.sidebarPlacement(nodeType, baritoneAvailable, uiUtilsAvailable);
        NodeCategory category = placement != null ? placement.displayCategory() : NodeCatalog.category(nodeType);
        return category.getDisplayName();
    }

    private int scoreNodeSearchCandidate(NodeType nodeType, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.getDisplayName(), query));
        bestScore = Math.max(bestScore, scoreSearchCandidate(nodeType.getDescription(), query) - 40);
        bestScore = Math.max(bestScore, scoreSearchCandidate(getNodeSearchCategoryLabel(nodeType), query) - 80);
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

    private void renderNodeSearchDropdown(GuiGraphics context, int mouseX, int mouseY) {
        if (nodeSearchResults.isEmpty()) {
            return;
        }
        int listX = nodeSearchFieldX;
        int listY = nodeSearchFieldY + NODE_SEARCH_FIELD_HEIGHT + NODE_SEARCH_DROPDOWN_TOP_GAP;
        int listWidth = NODE_SEARCH_FIELD_WIDTH;
        int listHeight = nodeSearchResults.size() * NODE_SEARCH_RESULT_HEIGHT;
        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(getAccentColor(), 1f, true, false);
        UIStyleHelper.drawScrollContainer(context, listX, listY, listWidth, listHeight, containerPalette);

        int hoveredIndex = getNodeSearchResultIndexAt(mouseX, mouseY);
        if (hoveredIndex >= 0) {
            nodeSearchHoverIndex = hoveredIndex;
        }

        for (int i = 0; i < nodeSearchResults.size(); i++) {
            NodeSearchResult result = nodeSearchResults.get(i);
            int rowTop = listY + i * NODE_SEARCH_RESULT_HEIGHT;
            boolean selected = i == nodeSearchHoverIndex;
            UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(getAccentColor(), selected ? 1f : 0f, selected, false);
            if (selected) {
                UIStyleHelper.drawDropdownRow(context, listX + 1, rowTop, listWidth - 2, NODE_SEARCH_RESULT_HEIGHT, rowPalette);
            }
            int textY = rowTop + Math.max(0, (NODE_SEARCH_RESULT_HEIGHT - this.font.lineHeight) / 2);
            String label = trimToWidth(result.label, listWidth - (NODE_SEARCH_RESULT_TEXT_PADDING * 2) - 42);
            context.drawString(this.font, Component.literal(label), listX + NODE_SEARCH_RESULT_TEXT_PADDING, textY, selected ? rowPalette.textColor() : UITheme.TEXT_PRIMARY);
            String category = trimToWidth(result.categoryLabel, 36);
            int categoryWidth = this.font.width(category);
            context.drawString(this.font, Component.literal(category),
                listX + listWidth - NODE_SEARCH_RESULT_TEXT_PADDING - categoryWidth, textY, UITheme.TEXT_TERTIARY);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        return TextRenderUtil.trimWithEllipsis(this.font, value, maxWidth);
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
        nodeSearchFieldX = Mth.clamp(nodeGraph.worldToScreenX(nodeSearchWorldX), minX, maxX);
        nodeSearchFieldY = Mth.clamp(nodeGraph.worldToScreenY(nodeSearchWorldY), minY, maxY);
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
        nodeSearchHoverIndex = Mth.clamp(nodeSearchHoverIndex + direction, 0, nodeSearchResults.size() - 1);
    }

    private void selectNodeSearchResult(NodeSearchResult result) {
        if (result == null || result.nodeType == null) {
            return;
        }
        if (shouldBlockBaritoneNode(result.nodeType) || shouldBlockUiUtilsNode(result.nodeType)) {
            return;
        }
        if (result.routine != null) nodeGraph.addRoutineFromContextMenu(result.routine);
        else nodeGraph.addNodeFromContextMenu(result.nodeType);
        closeNodeSearch();
    }

    private void drawNodeSearchIcon(GuiGraphics context, int x, int y, int color) {
        PathmindIconRenderer.drawSearch(context, x, y, color);
    }

    void attemptCreatePreset() {
        if (createPresetField == null) {
            return;
        }

        String desiredName = createPresetField.getValue();
        if (createRoutineNaming) {
            String routineName = desiredName == null ? "" : desiredName.trim();
            if (routineName.isEmpty()) {
                setCreatePresetStatus(Component.translatable("pathmind.status.enterRoutineName").getString(), UITheme.STATE_ERROR);
                return;
            }
            if (!pendingLibraryRoutineRenameId.isBlank()) {
                if (!com.pathmind.routines.RoutineLibraryManager.rename(pendingLibraryRoutineRenameId, routineName)) {
                    setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                    return;
                }
                String libraryHostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + pendingLibraryRoutineRenameId;
                for (WorkspaceTab tab : workspaceTabs) {
                    if (!libraryHostId.equals(tab.hostTemplateNodeId) || tab.libraryRoutineDefinition == null) continue;
                    new com.pathmind.routines.RoutineBuilderModel(tab.libraryRoutineDefinition).renameRoutine(routineName);
                    tab.graphData = tab.libraryRoutineDefinition.getGraph();
                    tab.label = routineName;
                }
                refreshRoutineSidebarContext();
                closeCreatePresetPopup();
                return;
            }
            boolean duplicate = !workspaceTabs.isEmpty() && workspaceTabs.get(0).graphData.getRoutines().stream()
                .anyMatch(routine -> routine != null && routine.getName() != null
                    && !routine.getId().equals(pendingRoutineRenameId)
                    && routineName.equalsIgnoreCase(routine.getName().trim()));
            if (duplicate) {
                setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                return;
            }
            if (!pendingRoutineRenameId.isBlank()) {
                NodeGraphData root = workspaceTabs.get(0).graphData;
                NodeGraphData.RoutineDefinitionData routine = root.getRoutines().stream()
                    .filter(candidate -> candidate != null && pendingRoutineRenameId.equals(candidate.getId()))
                    .findFirst().orElse(null);
                if (routine != null) {
                    new com.pathmind.routines.RoutineBuilderModel(routine).renameRoutine(routineName);
                    switchToRootAfterRoutineChange(root);
                }
            } else {
                createRoutineFromSidebar(routineName);
            }
            closeCreatePresetPopup();
            return;
        }
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setCreatePresetStatus(Component.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            return;
        }

        Optional<String> createdPreset = PresetManager.createPreset(desiredName);
        if (createdPreset.isEmpty()) {
            setCreatePresetStatus(Component.translatable("pathmind.status.presetNameExistsOrInvalid").getString(), UITheme.STATE_ERROR);
            return;
        }

        switchPreset(createdPreset.get());
        closeCreatePresetPopup();
    }

    void attemptRenamePreset() {
        if (renamePresetField == null) {
            return;
        }

        if (pendingPresetRenameName == null || pendingPresetRenameName.trim().isEmpty()) {
            setRenamePresetStatus(Component.translatable("pathmind.status.selectPresetToRename").getString(), UITheme.STATE_ERROR);
            return;
        }

        String desiredName = renamePresetField.getValue();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setRenamePresetStatus(Component.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            return;
        }

        if (!renamePresetInternal(pendingPresetRenameName, desiredName)) {
            setRenamePresetStatus(Component.translatable("pathmind.status.presetNameExistsOrInvalid").getString(), UITheme.STATE_ERROR);
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

    void closePresetDeletePopup() {
        presetDeletePopupAnimation.hide();
        pendingPresetDeletionName = "";
    }

    void confirmPresetDeletion() {
        String presetName = pendingPresetDeletionName;
        closePresetDeletePopup();
        if (presetName != null && !presetName.isEmpty()) {
            attemptDeletePreset(presetName);
        }
    }

    void setSkipPresetDeleteConfirm(boolean skip) {
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
        if (currentSettings != null && currentSettings.presetGroupColors != null) {
            currentSettings.presetGroupColors.remove(presetName);
            SettingsManager.save(currentSettings);
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

    void refreshAvailablePresets() {
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
        nodeGraph.stopEventNameEditing(true);
        nodeGraph.stopParameterEditing(true);
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        restoreRootWorkspaceIfNeeded();
        saveRootPresetWorkspace();
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
        if (this.minecraft == null) {
            return;
        }
        saveRootPresetWorkspace();
        PresetManager.setActivePreset(activePresetName);
        Optional<String> linkedPresetId = PresetManager.getMarketplaceLinkedPresetId(activePresetName);
        MarketplaceAuthManager.AuthSession cachedSession = MarketplaceAuthManager.getCachedSession().orElse(null);
        if (cachedSession != null && linkedPresetId.isPresent()) {
            PathmindMarketplaceFlowController.resolveLinkedPreset(this.minecraft, cachedSession, linkedPresetId, result -> {
                if (result.status() == PathmindMarketplaceFlowController.LinkedPresetStatus.FOUND) {
                    this.minecraft.setScreen(new PathmindMarketplaceScreen(this, false, null, result.preset()));
                } else {
                    this.minecraft.setScreen(new PathmindMarketplaceScreen(this, true, activePresetName));
                }
            });
            return;
        }
        this.minecraft.setScreen(new PathmindMarketplaceScreen(this, true, activePresetName));
    }

    void reopenPublishPresetPopup(String presetName) {
        if (this.minecraft == null) {
            return;
        }
        saveRootPresetWorkspace();
        PresetManager.setActivePreset(activePresetName);
        this.minecraft.setScreen(new PathmindMarketplaceScreen(this, true, presetName));
    }

    private void renderWorkspaceButtons(GuiGraphics context, int mouseX, int mouseY) {
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
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.publishPreset").getString(), mouseX, mouseY, this.width, this.height);
            } else if (marketplaceHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.title").getString(), mouseX, mouseY, this.width, this.height);
            } else if (homeHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.tooltip.resetView").getString(), mouseX, mouseY, this.width, this.height);
            } else if (clearHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.tooltip.clearWorkspace").getString(), mouseX, mouseY, this.width, this.height);
            } else if (importHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.popup.importExport.title").getString(), mouseX, mouseY, this.width, this.height);
            }
        }
    }

    private boolean renderMarketplaceButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getMarketplaceButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("workspace-marketplace", hovered);
        return PathmindWorkspaceChrome.renderMarketplaceButton(
            context,
            this.font,
            buttonX,
            buttonY,
            MARKETPLACE_BUTTON_WIDTH,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            hoverProgress,
            getAccentColor(),
            Component.translatable("pathmind.marketplace.title").getString()
        );
    }


    private boolean renderPublishButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getPublishButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("workspace-publish", hovered);
        return PathmindWorkspaceChrome.renderPublishButton(
            context,
            buttonX,
            buttonY,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            hoverProgress,
            getAccentColor(),
            isCurrentPresetPublishedAndSynced()
        );
    }


    private boolean isCurrentPresetPublishedAndSynced() {
        if (activePresetName == null || activePresetName.isBlank()) {
            return false;
        }
        return PresetManager.getMarketplaceLinkedPresetId(activePresetName).isPresent()
            && !PresetManager.hasMarketplaceLinkedPresetChanges(activePresetName);
    }

    private boolean renderHomeButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getHomeButtonX(), buttonY, mouseX, mouseY,
            false, false, "workspace-home", PathmindWorkspaceChrome::drawHomeIcon);
    }

    private boolean renderClearButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getClearButtonX(), buttonY, mouseX, mouseY,
            clearPopupAnimation.isVisible(), false, "workspace-clear", PathmindWorkspaceChrome::drawClearIcon);
    }

    private boolean renderImportExportButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getImportExportButtonX(), buttonY, mouseX, mouseY,
            importExportPopupAnimation.isVisible(), false, "workspace-import-export", PathmindWorkspaceChrome::drawImportExportIcon);
    }

    private void renderValidationButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled,
                                        GraphValidationResult validationResult) {
        int buttonX = getValidationButtonX();
        int buttonY = getValidationButtonY();
        boolean active = validationPanelOpen;
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("validation-button", hovered || active);
        PathmindValidationPanelRenderer.renderValidationButton(
            context,
            this.font,
            buttonX,
            buttonY,
            VALIDATION_BUTTON_SIZE,
            mouseX,
            mouseY,
            active,
            disabled,
            hoverProgress,
            validationPanelAnimation.getValue(),
            getAccentColor(),
            validationResult
        );
    }

    private void renderValidationPanel(GuiGraphics context, int mouseX, int mouseY, GraphValidationResult validationResult) {
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

        context.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight);
        int issueTop = PathmindValidationPanelRenderer.renderPanelAndIssues(
            context,
            this.font,
            mouseX,
            mouseY,
            validationResult,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            VALIDATION_PANEL_PADDING,
            VALIDATION_PANEL_HEADER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_ROW_HEIGHT,
            this::getValidationIssueHoverProgress
        );
        PathmindValidationPanelRenderer.renderFooter(
            context,
            this.font,
            validationResult,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            VALIDATION_PANEL_PADDING,
            VALIDATION_PANEL_FOOTER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS
        );
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

        PathmindValidationPanelRenderer.ClickedIssue clickedIssue = PathmindValidationPanelRenderer.findClickedIssue(
            validationResult,
            this.font,
            mouseX,
            mouseY,
            bounds[0],
            bounds[1],
            bounds[2],
            VALIDATION_PANEL_HEADER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_ROW_HEIGHT
        );
        if (clickedIssue.clicked()) {
            GraphValidationIssue issue = clickedIssue.issue();
            if (issue != null && issue.hasRoutineTarget()
                && !issue.getRoutineId().equals(nodeGraph.getActiveRoutineWorkspaceId())) {
                openRoutineWorkspaceTab(issue.getRoutineId());
            }
            if (issue != null && issue.hasNodeTarget()) {
                nodeGraph.focusNodeById(issue.getNodeId(), this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
            }
            return true;
        }
        return true;
    }

    private int[] getValidationPanelBounds(GraphValidationResult validationResult, float progress) {
        return PathmindValidationPanelRenderer.getPanelBounds(
            validationResult,
            this.font,
            getValidationButtonX() + VALIDATION_BUTTON_SIZE,
            getValidationButtonY(),
            progress,
            VALIDATION_PANEL_WIDTH,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_HEADER_HEIGHT,
            0,
            VALIDATION_PANEL_FOOTER_HEIGHT,
            VALIDATION_PANEL_BOTTOM_PADDING,
            VALIDATION_PANEL_ROW_HEIGHT
        );
    }

    private float getValidationIssueHoverProgress(GraphValidationIssue issue, int index, boolean hovered) {
        String issueKey = issue == null ? "unknown-" + index : issue.getCode() + ":" + issue.getNodeId() + ":" + index;
        return getHoverProgress("validation-issue-row:" + issueKey, hovered);
    }

    float getHoverProgress(Object key, boolean hovered) {
        return HoverAnimator.getProgress(key, hovered);
    }


    private void renderSettingsButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        boolean hovered = renderWorkspaceIconButton(context, getSettingsButtonX(), getSettingsButtonY(), mouseX, mouseY,
            settingsPopupAnimation.isVisible(), disabled, "settings-button", PathmindWorkspaceChrome::drawSettingsIcon);

        if (hovered && showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            TooltipRenderer.render(context, this.font, Component.translatable("pathmind.settings.title").getString(), mouseX, mouseY, this.width, this.height);
        }
    }

    private boolean renderWorkspaceIconButton(GuiGraphics context, int buttonX, int buttonY, int mouseX, int mouseY,
                                              boolean active, boolean disabled, Object hoverKey,
                                              PathmindWorkspaceChrome.IconPainter iconPainter) {
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress(hoverKey, hovered || active);
        return PathmindWorkspaceChrome.renderIconButton(
            context,
            buttonX,
            buttonY,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            active,
            disabled,
            hoverProgress,
            getAccentColor(),
            iconPainter
        );
    }

    private int getWorkspaceButtonY() {
        return PathmindWorkspaceChrome.topButtonY(TITLE_BAR_HEIGHT, BOTTOM_BUTTON_MARGIN);
    }


    private int getSidebarVisibleWidth() {
        return sidebar != null ? sidebar.getWidth() : Sidebar.getCollapsedWidth();
    }

    private int getMarketplaceButtonX() {
        return PathmindWorkspaceChrome.marketplaceButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }


    private int getPublishButtonX() {
        return PathmindWorkspaceChrome.publishButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN);
    }


    private int getHomeButtonX() {
        return PathmindWorkspaceChrome.homeButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }


    private int getClearButtonX() {
        return PathmindWorkspaceChrome.clearButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }


    private int getImportExportButtonX() {
        return PathmindWorkspaceChrome.importExportButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }


    private int getSettingsButtonX() {
        return PathmindWorkspaceChrome.settingsButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN);
    }


    private int getSettingsButtonY() {
        return PathmindWorkspaceChrome.bottomButtonY(this.height, BOTTOM_BUTTON_MARGIN, BOTTOM_BUTTON_SIZE);
    }


    private boolean isHomeButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getHomeButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isClearButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getClearButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isImportExportButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getImportExportButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isMarketplaceButtonClicked(int mouseX, int mouseY, int button) {
        return PathmindWorkspaceChrome.primaryClickInBounds(mouseX, mouseY, button, getMarketplaceButtonX(), getWorkspaceButtonY(), MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
    }

    private boolean isPublishButtonClicked(int mouseX, int mouseY, int button) {
        return PathmindWorkspaceChrome.primaryClickInBounds(mouseX, mouseY, button, getPublishButtonX(), getWorkspaceButtonY(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isSettingsButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getSettingsButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isValidationButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getValidationButtonX();
        int buttonY = getValidationButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
    }

    private boolean isPointInPlayButton(int mouseX, int mouseY) {
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, getPlayButtonX(), getPlayButtonY(), PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private boolean isPointInStopButton(int mouseX, int mouseY) {
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, getStopButtonX(), getStopButtonY(), STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
    }

    private boolean isPointInRoutineExitButton(int mouseX, int mouseY) {
        return getActiveRoutineWorkspace() != null && PathmindWorkspaceChrome.contains(
            mouseX, mouseY, getRoutineExitButtonX(), getRoutineExitButtonY(), PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private boolean isPointInZoomMinus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomMinusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private boolean isPointInZoomPlus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomPlusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    int getSettingsPopupX() {
        return (this.width - SETTINGS_POPUP_WIDTH) / 2;
    }

    int getSettingsPopupWidth() {
        return SETTINGS_POPUP_WIDTH;
    }

    int getSettingsPopupHeight() {
        return Math.min(SETTINGS_POPUP_HEIGHT, Math.max(140, this.height - 24));
    }

    int getSettingsPopupY() {
        return (this.height - getSettingsPopupHeight()) / 2;
    }


    int screenWidth() {
        return this.width;
    }

    int screenHeight() {
        return this.height;
    }

    Font textRenderer() {
        return this.font;
    }

    Minecraft client() {
        return this.minecraft;
    }

    int[] getSettingsPopupBodyBounds(int popupX, int popupY, int popupWidth, int popupHeight) {
        return settingsPopupController.getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
    }

    int getSettingsPopupMaxScroll(int popupX, int popupY, int popupWidth, int popupHeight) {
        return settingsPopupController.getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
    }

    ScrollbarHelper.Metrics getSettingsPopupScrollMetrics(int popupX, int popupY, int popupWidth, int popupHeight, int maxScroll) {
        return settingsPopupController.getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll);
    }

    int getSettingsNodeSectionBodyY(int popupY) {
        return settingsPopupController.getSettingsNodeSectionBodyY(popupY);
    }

    int getSettingsNodeSectionContentY(int bodyY, int contentWidth) {
        return settingsPopupController.getSettingsNodeSectionContentY(bodyY, contentWidth);
    }

    int[] getSettingsNodeTypeSelectorBounds(int contentX, int bodyY, int contentWidth) {
        return settingsPopupController.getSettingsNodeTypeSelectorBounds(contentX, bodyY, contentWidth);
    }

    int[] getSettingsNodeTypeSearchFieldBounds(int contentX, int bodyY, int contentWidth) {
        return settingsPopupController.getSettingsNodeTypeSearchFieldBounds(contentX, bodyY, contentWidth);
    }

    int getSettingsNodeTypeSelectorMaxScroll(int contentWidth) {
        return settingsPopupController.getSettingsNodeTypeSelectorMaxScroll(contentWidth);
    }

    ScrollbarHelper.Metrics getSettingsNodeTypeSelectorScrollMetrics(int contentX, int bodyY, int contentWidth, int maxScroll) {
        return settingsPopupController.getSettingsNodeTypeSelectorScrollMetrics(contentX, bodyY, contentWidth, maxScroll);
    }

    int[] getSettingsNodeTypeButtonBounds(int contentX, int bodyY, int contentWidth, int maxScroll, int index) {
        return settingsPopupController.getSettingsNodeTypeButtonBounds(contentX, bodyY, contentWidth, maxScroll, index);
    }

    List<NodeType> getFilteredSettingsNodeTypes() {
        return settingsPopupController.getFilteredSettingsNodeTypes();
    }

    int[] getNodeDelayFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        return settingsPopupController.getNodeDelayFieldBounds(popupX, scaledWidth, centerY, valueText);
    }

    int[] getCreateListRadiusFieldBounds(int popupX, int scaledWidth, int centerY, String valueText) {
        return settingsPopupController.getCreateListRadiusFieldBounds(popupX, scaledWidth, centerY, valueText);
    }

    Integer parseDelayFieldValue(String value) {
        return settingsPopupController.parseDelayFieldValue(value);
    }

    Integer parseCreateListRadiusFieldValue(String value) {
        return settingsPopupController.parseCreateListRadiusFieldValue(value);
    }

    void updateNodeDelayFromMouse(int mouseX, int popupX, int popupWidth) {
        settingsPopupController.updateNodeDelayFromMouse(mouseX, popupX, popupWidth);
    }

    void updateCreateListRadiusFromMouse(Node node, int mouseX, int popupX, int popupWidth) {
        settingsPopupController.updateCreateListRadiusFromMouse(node, mouseX, popupX, popupWidth);
    }

    boolean supportsNodeSettings(Node node) {
        return settingsPopupController.supportsNodeSettings(node);
    }

    Node findFirstNodeWithSettingsType(NodeType type) {
        return settingsPopupController.findFirstNodeWithSettingsType(type);
    }

    NodeType getEffectiveSettingsTargetType() {
        return settingsPopupController.getEffectiveSettingsTargetType();
    }

    Node getEffectiveSettingsTargetNode() {
        return settingsPopupController.getEffectiveSettingsTargetNode();
    }

    boolean isCreateListCustomRadiusEnabled(Node node) {
        return settingsPopupController.isCreateListCustomRadiusEnabled(node);
    }

    int getCreateListSettingsRadius(Node node) {
        return settingsPopupController.getCreateListSettingsRadius(node);
    }

    void setCreateListCustomRadiusEnabled(Node node, boolean enabled) {
        settingsPopupController.setCreateListCustomRadiusEnabled(node, enabled);
    }

    void setCreateListSettingsRadius(Node node, int radius) {
        settingsPopupController.setCreateListSettingsRadius(node, radius);
    }

    int[] getSettingsClearCacheButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return settingsPopupController.getSettingsClearCacheButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
    }

    int[] getSettingsCacheRecipesButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return settingsPopupController.getSettingsCacheRecipesButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
    }

    void cacheSettingsRecipes() {
        settingsPopupController.cacheSettingsRecipes();
    }

    void clearSettingsCache() {
        settingsPopupController.clearSettingsCache();
    }

    int[] getSettingsRestoreExamplesButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return settingsPopupController.getSettingsRestoreExamplesButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
    }

    void restoreExamplePresets() {
        settingsPopupController.restoreExamplePresets();
    }

    int getAccentColor() {
        return accentOption != null ? accentOption.color : UITheme.ACCENT_DEFAULT;
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
        settingsPopupAnimation.show();
    }

    private void closeSettingsPopup() {
        languageDropdownOpen = false;
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
        settingsPopupAnimation.hide();
    }

    private boolean handleSettingsPopupClick(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
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

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight)) {
            closeSettingsPopup();
            return true;
        }

        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight);
        ScrollbarHelper.Metrics scrollMetrics = getSettingsPopupScrollMetrics(popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, maxScroll);
        if (maxScroll > 0
            && isPointInRect(mouseXi, mouseYi, scrollMetrics.trackLeft() - 3, scrollMetrics.trackTop(), scrollMetrics.trackWidth() + 6, scrollMetrics.viewportHeight())) {
            settingsPopupScrollDragging = true;
            settingsPopupScrollDragOffset = mouseYi - scrollMetrics.thumbTop();
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

        int lowDetailDividerY = settingDividerY + 22;
        int lowDetailRowCenterY = (settingDividerY + lowDetailDividerY) / 2;
        int lowDetailToggleX = gridToggleX;
        int lowDetailToggleY = lowDetailRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, lowDetailToggleX, lowDetailToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            currentSettings.lowDetailMode = !Boolean.TRUE.equals(currentSettings.lowDetailMode);
            SettingsManager.save(currentSettings);
            return true;
        }

        int footerDividerY = lowDetailDividerY + 22;
        int tooltipRowCenterY = (lowDetailDividerY + footerDividerY) / 2;
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

        int profilerDividerY = overlayDividerY + 22;
        int profilerRowCenterY = (overlayDividerY + profilerDividerY) / 2;
        int profilerToggleX = gridToggleX;
        int profilerToggleY = profilerRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, profilerToggleX, profilerToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            currentSettings.showProfilerOverlay = !Boolean.TRUE.equals(currentSettings.showProfilerOverlay);
            SettingsManager.save(currentSettings);
            return true;
        }

        int delayDividerY = profilerDividerY + 26;
        int delayRowCenterY = (profilerDividerY + delayDividerY) / 2;
        int sliderX = popupX + SETTINGS_POPUP_WIDTH - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = delayRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
        String delayText = nodeDelayField != null ? nodeDelayField.getValue() : Integer.toString(nodeDelayMs);
        int[] valueBox = getNodeDelayFieldBounds(popupX, SETTINGS_POPUP_WIDTH, delayRowCenterY, delayText);
        int valueBoxX = valueBox[0];
        int valueBoxY = valueBox[1];
        int valueBoxWidth = valueBox[2];
        int valueBoxHeight = valueBox[3];
        if (nodeDelayField != null) {
            if (bodyHovered && isPointInRect(mouseXi, mouseYi, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight)) {
                nodeDelayField.setEditable(true);
                nodeDelayField.setFocused(true);
                nodeDelayField.mouseClicked(click, inBounds);
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
        int selectorWidth = SETTINGS_POPUP_WIDTH - 40;
        int nodeSettingsContentY = getSettingsNodeSectionContentY(nodeSettingsBodyY, selectorWidth);
        int[] selectorViewportBounds = getSettingsNodeTypeSelectorBounds(contentX, nodeSettingsBodyY, selectorWidth);
        int[] selectorSearchBounds = getSettingsNodeTypeSearchFieldBounds(contentX, nodeSettingsBodyY, selectorWidth);
        int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorWidth);
        ScrollbarHelper.Metrics selectorScrollMetrics = getSettingsNodeTypeSelectorScrollMetrics(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll);
        if (maxSelectorScroll > 0
            && isPointInRect(mouseXi, mouseYi, selectorScrollMetrics.trackLeft() - 3, selectorScrollMetrics.trackTop(),
            selectorScrollMetrics.trackWidth() + 6, selectorScrollMetrics.viewportHeight())) {
            settingsNodeSelectorScrollDragging = true;
            settingsNodeSelectorScrollDragOffset = mouseYi - selectorScrollMetrics.thumbTop();
            return true;
        }
        if (settingsNodeSearchField != null) {
            if (bodyHovered && isPointInRect(mouseXi, mouseYi, selectorSearchBounds[0], selectorSearchBounds[1], selectorSearchBounds[2], selectorSearchBounds[3])) {
                settingsNodeSearchField.setEditable(true);
                settingsNodeSearchField.setFocused(true);
                settingsNodeSearchField.mouseClicked(click, inBounds);
                return true;
            } else if (settingsNodeSearchField.isFocused()) {
                settingsNodeSearchField.setFocused(false);
            }
        }
        List<NodeType> filteredTypes = getFilteredSettingsNodeTypes();
        for (int i = 0; i < filteredTypes.size(); i++) {
            int[] selectorBounds = getSettingsNodeTypeButtonBounds(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll, i);
            if (bodyHovered
                && isPointInRect(mouseXi, mouseYi, selectorViewportBounds[0], selectorViewportBounds[1], selectorViewportBounds[2], selectorViewportBounds[3])
                && isPointInRect(mouseXi, mouseYi, selectorBounds[0], selectorBounds[1], selectorBounds[2], selectorBounds[3])) {
                NodeType targetType = filteredTypes.get(i);
                settingsNodeTargetType = targetType;
                settingsNodeTarget = findFirstNodeWithSettingsType(targetType);
                if (nodeGraph != null && settingsNodeTarget != null) {
                    nodeGraph.selectNode(settingsNodeTarget);
                }
                return true;
            }
        }
        int[] clearCacheButtonBounds = getSettingsClearCacheButtonBounds(
            popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsContentY);
        int[] cacheRecipesButtonBounds = getSettingsCacheRecipesButtonBounds(
            popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsContentY);
        if (isPointInRect(mouseXi, mouseYi, cacheRecipesButtonBounds[0], cacheRecipesButtonBounds[1],
            cacheRecipesButtonBounds[2], cacheRecipesButtonBounds[3])) {
            cacheSettingsRecipes();
            return true;
        }
        if (isPointInRect(mouseXi, mouseYi, clearCacheButtonBounds[0], clearCacheButtonBounds[1],
            clearCacheButtonBounds[2], clearCacheButtonBounds[3])) {
            clearSettingsCache();
            return true;
        }
        int[] restoreExamplesButtonBounds = getSettingsRestoreExamplesButtonBounds(
            popupX, popupY, SETTINGS_POPUP_WIDTH, popupHeight, contentX, nodeSettingsContentY);
        if (isPointInRect(mouseXi, mouseYi, restoreExamplesButtonBounds[0], restoreExamplesButtonBounds[1],
            restoreExamplesButtonBounds[2], restoreExamplesButtonBounds[3])) {
            restoreExamplePresets();
            return true;
        }

        NodeType selectedType = getEffectiveSettingsTargetType();
        if (bodyHovered && selectedType == NodeType.GOTO) {
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
        } else if (bodyHovered && selectedType == NodeType.SENSOR_KEY_PRESSED) {
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
        } else if (bodyHovered && selectedType == NodeType.CREATE_LIST) {
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
                String radiusText = createListRadiusField != null ? createListRadiusField.getValue() : Integer.toString(getCreateListSettingsRadius(targetNode));
                int[] radiusValueBox = getCreateListRadiusFieldBounds(popupX, SETTINGS_POPUP_WIDTH, createListRadiusRowCenterY, radiusText);
                if (createListRadiusField != null) {
                    if (bodyHovered && isPointInRect(mouseXi, mouseYi, radiusValueBox[0], radiusValueBox[1], radiusValueBox[2], radiusValueBox[3])) {
                        createListRadiusField.setEditable(true);
                        createListRadiusField.setFocused(true);
                        createListRadiusField.mouseClicked(click, inBounds);
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
        validationPanelOpen = false;
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        draggingSidebarNode = null;
        NodeGraphData.RoutineDefinitionData activeRoutine = getActiveRoutineWorkspace();
        saveRootPresetWorkspace();
        if (activeRoutine != null) {
            ExecutionManager.getInstance().executeRoutine(
                activeRoutine, getActiveRoutineRegistry(), activePresetName);
        } else {
            ExecutionManager.getInstance().executeGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.setScreen(null);
        }
    }

    private boolean handleStartNodeClick(int mouseX, int mouseY) {
        if (!nodeGraph.handleStartButtonClick(mouseX, mouseY)) {
            return false;
        }
        handleStartNodeLaunchAfterClick();
        return true;
    }

    private void handleStartNodeLaunchAfterClick() {
        presetDropdownOpen = false;
        if (nodeGraph.didLastStartButtonTriggerExecution()) {
            dismissParameterOverlay();
            isDraggingFromSidebar = false;
            draggingNodeType = null;
            draggingSidebarNode = null;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.setScreen(null);
            }
        }
    }

    private void stopExecutingAllGraphs() {
        ExecutionManager.getInstance().requestStopAll();
    }

    void drawLanguageDropdown(GuiGraphics context, int x, int y, int width, String currentLang, boolean hovered) {
        DropdownLayoutHelper.updateOpenAnimation(languageDropdownAnimation, languageDropdownOpen);

        float hoverProgress = languageDropdownOpen ? 1f : getHoverProgress("settings-language-dropdown-bg", hovered);
        UIStyleHelper.FieldPalette fieldPalette = UIStyleHelper.getDropdownFieldPalette(getAccentColor(), hoverProgress, languageDropdownOpen, false);
        UIStyleHelper.drawFieldFrame(
            context,
            x,
            y,
            width,
            20,
            new UIStyleHelper.FieldPalette(
                settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.backgroundColor()),
                settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.borderColor()),
                settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.innerBorderColor()),
                settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.textColor()),
                settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.placeholderColor())
            )
        );

        int labelColor = settingsPopupAnimation.getAnimatedPopupColor(fieldPalette.textColor());
        context.drawString(this.font, Component.literal(currentLang), x + 4, y + 6, labelColor);

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
            DropdownLayoutHelper.getRevealBottom(dropdownY, fullOptionsHeight, animProgress, 0),
            languageDropdownClipY + languageDropdownClipHeight
        );

        if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) {
            MatrixStackBridge.pop(matrices);
            return;
        }

        context.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);

        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(getAccentColor(), animProgress, languageDropdownOpen, false);
        UIStyleHelper.drawScrollContainer(
            context,
            x,
            dropdownY,
            width,
            fullOptionsHeight,
            new UIStyleHelper.ScrollContainerPalette(
                settingsPopupAnimation.getAnimatedPopupColor(containerPalette.backgroundColor()),
                settingsPopupAnimation.getAnimatedPopupColor(containerPalette.borderColor()),
                settingsPopupAnimation.getAnimatedPopupColor(containerPalette.innerBorderColor()),
                settingsPopupAnimation.getAnimatedPopupColor(containerPalette.trackColor()),
                settingsPopupAnimation.getAnimatedPopupColor(containerPalette.thumbColor())
            )
        );

        // Draw each language option
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
            String lang = SUPPORTED_LANGUAGES[i];
            String langName = getLanguageDisplayName(lang);
            int optionY = dropdownY + (i * 20);

            boolean optionHovered = animProgress >= 1f && mouseX >= x && mouseX <= x + width && mouseY >= optionY && mouseY <= optionY + 20;
            String currentLang = this.minecraft.getLanguageManager().getSelected();
            boolean selected = lang.equals(currentLang);
            UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(getAccentColor(), optionHovered ? 1f : 0f, selected, false);
            UIStyleHelper.drawDropdownRow(
                context,
                x + 1,
                optionY + 1,
                width - 2,
                19,
                new UIStyleHelper.DropdownRowPalette(
                    settingsPopupAnimation.getAnimatedPopupColor(rowPalette.backgroundColor()),
                    settingsPopupAnimation.getAnimatedPopupColor(rowPalette.borderColor()),
                    settingsPopupAnimation.getAnimatedPopupColor(rowPalette.textColor())
                )
            );

            int textColor = settingsPopupAnimation.getAnimatedPopupColor(selected ? getAccentColor() : rowPalette.textColor());
            context.drawString(this.font, Component.literal(langName), x + 4, optionY + 6, textColor);
        }

        context.disableScissor();
        MatrixStackBridge.pop(matrices);
    }

    String getLanguageDisplayName(String languageCode) {
        return Component.translatable("pathmind.language." + languageCode).getString();
    }

    private void onLanguageSelected(String languageCode) {
        // Save to settings first
        currentSettings.language = languageCode;
        SettingsManager.save(currentSettings);

        // Update Minecraft's language and reload resources
        this.minecraft.options.languageCode = languageCode;
        this.minecraft.getLanguageManager().setSelected(languageCode);
        this.minecraft.options.save();
        this.minecraft.reloadResourcePacks();

        // Reload the screen to update all text
        this.minecraft.setScreen(null);
        this.minecraft.setScreen(new PathmindVisualEditorScreen());
    }

    private void drawPencilIcon(GuiGraphics context, int x, int y, int color) {
        PathmindIconRenderer.drawPencil(context, x, y, PRESET_RENAME_ICON_SIZE, color);
    }

    private void drawTrashIcon(GuiGraphics context, int x, int y, int color) {
        PathmindIconRenderer.drawTrash(context, x, y, PRESET_DELETE_ICON_SIZE, color);
    }

    private void drawCloseXIcon(GuiGraphics context, int x, int y, int size, int color) {
        PathmindIconRenderer.drawCloseX(context, x, y, size, color);
    }

    private boolean isTitleClicked(int mouseX, int mouseY) {
        return isTitleHovered(mouseX, mouseY);
    }

    private boolean isTitleHovered(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getTitleTextX(), getTitleTextY(), PRESET_MENU_BUTTON_SIZE, PRESET_MENU_BUTTON_SIZE);
    }

    private int getTitleTextX() {
        return this.width - 8 - PRESET_MENU_BUTTON_SIZE;
    }

    private int getTitleTextY() {
        return (TITLE_BAR_HEIGHT - PRESET_MENU_BUTTON_SIZE) / 2;
    }

    private String getModVersion() {
        return LoaderMetadata.getModVersion(PathmindCommon.MOD_ID);
    }

    private String getLoaderVersion() {
        return LoaderMetadata.getLoaderVersion();
    }

    private String getCurrentMinecraftVersion() {
        return this.minecraft != null ? this.minecraft.getLaunchedVersion() : "Unknown";
    }

    boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }

}
