package com.pathmind.screen;

import com.pathmind.PathmindCommon;
//? if PRE_1_21_11 {
/*// CharacterEvent exposes modifiers directly before 1.21.11.*/
//?} else {
import com.pathmind.compat.CharacterEventModifiers;
//?}
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.nodes.Node;
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
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.OverlayProtection;
import com.pathmind.util.UiUtilsProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The main visual editor screen for Pathmind.
 * This screen provides the interface for creating and editing node-based workflows.
 */
public class PathmindVisualEditorScreen extends Screen {
    static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }

    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int PRESET_MENU_BUTTON_SIZE = 18;
    
    // Colors now come from UITheme for consistency
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int MARKETPLACE_BUTTON_WIDTH = BOTTOM_BUTTON_SIZE * 3 + BOTTOM_BUTTON_SPACING * 2;
    static final int CREATE_PRESET_POPUP_WIDTH = 320;
    static final int CREATE_PRESET_POPUP_HEIGHT = 170;
    static final int PUBLISH_PRESET_POPUP_WIDTH = 380;
    static final int PUBLISH_PRESET_POPUP_HEIGHT = 272;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    static final int PRESET_DELETE_POPUP_WIDTH = 320;
    static final int PRESET_DELETE_POPUP_HEIGHT = 160;
    static final int PRESET_DELETE_SKIP_CHECKBOX_SIZE = 10;
    private static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    private static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    private static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    private static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;
    static final int SETTINGS_POPUP_WIDTH = 360;
    static final int SETTINGS_POPUP_HEIGHT = 408;
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
    static final int SETTINGS_BACK_BUTTON_WIDTH = 52;
    static final int SETTINGS_BACK_BUTTON_HEIGHT = 18;
    static final int SETTINGS_SECTION_BUTTON_WIDTH = 56;
    static final int SETTINGS_SECTION_BUTTON_HEIGHT = 20;
    static final int SETTINGS_NODE_TYPE_BUTTON_HEIGHT = 28;
    static final int SETTINGS_NODE_TYPE_BUTTON_GAP = 6;
    static final int SETTINGS_NODE_TYPE_SECTION_GAP = 10;
    static final int SETTINGS_NODE_TYPE_SELECTOR_MAX_HEIGHT = 102;
    static final int SETTINGS_NODE_TYPE_SEARCH_HEIGHT = 22;
    static final int SETTINGS_NODE_TYPE_SEARCH_PADDING = 8;
    static final int SETTINGS_NODE_TYPE_SEARCH_LIST_GAP = 8;
    static final int SETTINGS_NODE_TYPE_EMPTY_HEIGHT = 24;
    static final long SETTINGS_SCROLL_GESTURE_TIMEOUT_MS = 180L;
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
    private static final Component TITLE_TEXT = Component.literal("Pathmind");

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
    private final PathmindNodeSearchController nodeSearchController =
        new PathmindNodeSearchController(new NodeSearchHost());

    // Workspace dialogs
    private final PopupAnimationHandler clearPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler importExportPopupAnimation = new PopupAnimationHandler();
    private final PathmindWorkspaceDialogController workspaceDialogController;

    private final PathmindPresetDropdownController presetDropdownController =
        new PathmindPresetDropdownController(new PresetDropdownHost());
    private final AnimatedValue titleUnderlineAnimation = AnimatedValue.forHover();
    private final AnimatedValue routineWorkspaceAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private List<String> availablePresets = new ArrayList<>();
    private final PathmindPresetTabController presetTabController =
        new PathmindPresetTabController(new PresetTabHost());
    private final PathmindPresetContextMenuController presetContextMenuController =
        new PathmindPresetContextMenuController(new PresetContextMenuHost());
    private String activePresetName = "";
    final PopupAnimationHandler createPresetPopupAnimation = new PopupAnimationHandler();
    EditBox createPresetField;
    String createPresetStatus = "";
    int createPresetStatusColor = UITheme.TEXT_SECONDARY;
    boolean createRoutineNaming = false;
    String pendingRoutineRenameId = "";
    String pendingLibraryRoutineRenameId = "";
    final PopupAnimationHandler publishPresetPopupAnimation = new PopupAnimationHandler();
    EditBox publishPresetNameField;
    EditBox publishPresetDescriptionField;
    EditBox publishPresetTagsField;
    String publishPresetStatus = "";
    int publishPresetStatusColor = UITheme.TEXT_SECONDARY;
    boolean publishPresetBusy = false;
    MarketplaceAuthManager.AuthSession publishPresetSession = null;
    MarketplacePreset publishPresetEditingPreset = null;
    boolean publishPresetPublic = true;
    final ToggleSwitch publishPresetVisibilityToggle = new ToggleSwitch(true);
    private final Map<PopupAnimationHandler, Integer> popupScrollOffsets = new IdentityHashMap<>();
    final PopupAnimationHandler renamePresetPopupAnimation = new PopupAnimationHandler();
    EditBox renamePresetField;
    private EditBox inlinePresetRenameField;
    String renamePresetStatus = "";
    int renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    String pendingPresetRenameName = "";
    private final PopupAnimationHandler infoPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler presetDeletePopupAnimation = new PopupAnimationHandler();
    String pendingPresetDeletionName = "";
    private final PopupAnimationHandler missingBaritonePopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingUiUtilsPopupAnimation = new PopupAnimationHandler();
    final PopupAnimationHandler settingsPopupAnimation = new PopupAnimationHandler();
    private final PathmindSettingsPopupController settingsPopupController = new PathmindSettingsPopupController(this);
    private final PathmindPresetPopupController presetPopupController = new PathmindPresetPopupController(this);
    private final PathmindModalOverlayController modalOverlayController = new PathmindModalOverlayController(
        new ModalOverlayHost(),
        clearPopupAnimation,
        importExportPopupAnimation,
        createPresetPopupAnimation,
        publishPresetPopupAnimation,
        renamePresetPopupAnimation,
        presetDeletePopupAnimation,
        infoPopupAnimation,
        missingBaritonePopupAnimation,
        missingUiUtilsPopupAnimation,
        settingsPopupAnimation
    );
    private final PathmindFirstRunTutorialController firstRunTutorialController =
        new PathmindFirstRunTutorialController(new FirstRunTutorialHost());
    private final PathmindWorkspaceLifecycleController workspaceLifecycleController =
        new PathmindWorkspaceLifecycleController(new WorkspaceLifecycleHost());
    private final PathmindValidationExecutionController validationExecutionController =
        new PathmindValidationExecutionController(new ValidationExecutionHost());
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
    boolean renderConnectionsOnTop = false;
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
    private Boolean uiUtilsOverlayPrevEnabled = null;
    private final PathmindWorkspaceViewportController workspaceViewportController =
        new PathmindWorkspaceViewportController(new WorkspaceViewportHost());

    private final class NodeSearchHost implements PathmindNodeSearchController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public float zoomScale() {
            return nodeGraph.getZoomScale();
        }

        @Override
        public int screenToWorldX(int screenX) {
            return nodeGraph.screenToWorldX(screenX);
        }

        @Override
        public int screenToWorldY(int screenY) {
            return nodeGraph.screenToWorldY(screenY);
        }

        @Override
        public int worldToScreenX(int worldX) {
            return nodeGraph.worldToScreenX(worldX);
        }

        @Override
        public int worldToScreenY(int worldY) {
            return nodeGraph.worldToScreenY(worldY);
        }

        @Override
        public boolean isNodeAvailable(NodeType nodeType) {
            return sidebar.isNodeAvailable(nodeType);
        }

        @Override
        public boolean baritoneAvailable() {
            return baritoneAvailable;
        }

        @Override
        public boolean uiUtilsAvailable() {
            return uiUtilsAvailable;
        }

        @Override
        public List<NodeGraphData.RoutineDefinitionData> rootRoutines() {
            return workspaceLifecycleController.getRootRoutines();
        }

        @Override
        public boolean shouldBlockBaritoneNode(NodeType nodeType) {
            return PathmindVisualEditorScreen.this.shouldBlockBaritoneNode(nodeType);
        }

        @Override
        public boolean shouldBlockUiUtilsNode(NodeType nodeType) {
            return PathmindVisualEditorScreen.this.shouldBlockUiUtilsNode(nodeType);
        }

        @Override
        public void addNode(NodeType nodeType) {
            nodeGraph.addNodeFromContextMenu(nodeType);
        }

        @Override
        public void addRoutine(NodeGraphData.RoutineDefinitionData routine) {
            nodeGraph.addRoutineFromContextMenu(routine);
        }

        @Override
        public EditBox createSearchField(Runnable responder) {
            EditBox field = PathmindTextField.createInactive(font, 0, 0, 180, 22,
                Component.translatable("pathmind.search.nodes"), 64);
            field.setHeight(Math.max(10, 22 - TEXT_FIELD_VERTICAL_PADDING * 2));
            field.setResponder(value -> responder.run());
            addWidget(field);
            return field;
        }
    }

    private final class ValidationExecutionHost implements PathmindValidationExecutionController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public boolean hasActiveRoutineWorkspace() {
            return getActiveRoutineWorkspace() != null;
        }

        @Override
        public boolean isPopupObscuringWorkspace() {
            return PathmindVisualEditorScreen.this.isPopupObscuringWorkspace();
        }

        @Override
        public boolean showWorkspaceTooltips() {
            return showWorkspaceTooltips;
        }

        @Override
        public GraphValidationResult validationResult() {
            return nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);
        }

        @Override
        public float hoverProgress(Object key, boolean hovered) {
            return getHoverProgress(key, hovered);
        }

        @Override
        public void openRoutineWorkspace(String routineId) {
            openRoutineWorkspaceTab(routineId);
        }

        @Override
        public String activeRoutineWorkspaceId() {
            return nodeGraph.getActiveRoutineWorkspaceId();
        }

        @Override
        public void focusNode(String nodeId) {
            nodeGraph.focusNodeById(nodeId, width, height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void switchToRootWorkspace() {
            switchToWorkspaceTab(0);
        }

        @Override
        public void startExecutingAllGraphs() {
            PathmindVisualEditorScreen.this.startExecutingAllGraphs();
        }

        @Override
        public void stopExecutingAllGraphs() {
            ExecutionManager.getInstance().requestStopAll();
        }
    }

    private final class PresetTabHost implements PathmindPresetTabController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int presetOverflowTabRight() {
            return getPresetOverflowTabRight();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public boolean isPopupObscuringWorkspace() {
            return PathmindVisualEditorScreen.this.isPopupObscuringWorkspace();
        }

        @Override
        public List<String> availablePresets() {
            return availablePresets;
        }

        @Override
        public String activePresetName() {
            return activePresetName;
        }

        @Override
        public Settings settings() {
            return currentSettings;
        }

        @Override
        public EditBox inlinePresetRenameField() {
            return inlinePresetRenameField;
        }

        @Override
        public boolean isPresetDeleteDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetDeleteDisabled(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void closeCreatePresetPopup() {
            PathmindVisualEditorScreen.this.closeCreatePresetPopup();
        }

        @Override
        public void closeRenamePresetPopup() {
            PathmindVisualEditorScreen.this.closeRenamePresetPopup();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void switchPreset(String presetName) {
            PathmindVisualEditorScreen.this.switchPreset(presetName);
        }

        @Override
        public boolean renamePresetInternal(String currentName, String desiredName) {
            return PathmindVisualEditorScreen.this.renamePresetInternal(currentName, desiredName);
        }

        @Override
        public void attemptDeletePresetImmediate(String presetName) {
            PathmindVisualEditorScreen.this.attemptDeletePresetImmediate(presetName);
        }
    }

    private final class PresetDropdownHost implements PathmindPresetDropdownController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public List<String> availablePresets() {
            return availablePresets;
        }

        @Override
        public String activePresetName() {
            return activePresetName;
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public void openRenamePresetPopup(String presetName) {
            PathmindVisualEditorScreen.this.openRenamePresetPopup(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void beginPresetDropdownDrag(String presetName, int mouseX, int mouseY) {
            presetTabController.beginPresetDropdownDrag(presetName, mouseX, mouseY);
        }
    }

    private final class PresetContextMenuHost implements PathmindPresetContextMenuController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public String presetTabAt(int mouseX, int mouseY) {
            return getPresetTabAt(mouseX, mouseY);
        }

        @Override
        public String presetGroupAt(int mouseX, int mouseY) {
            return getPresetGroupAt(mouseX, mouseY);
        }

        @Override
        public String presetGroupKey(String presetName) {
            return getPresetGroupKey(presetName);
        }

        @Override
        public int presetGroupColor(String presetName) {
            return getPresetGroupColor(presetName);
        }

        @Override
        public String presetGroupColorLabel(String key) {
            return getPresetGroupColorLabel(key);
        }

        @Override
        public String nextPresetGroupColorKey() {
            return getNextPresetGroupColorKey();
        }

        @Override
        public boolean isPresetRenameDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetRenameDisabled(presetName);
        }

        @Override
        public boolean isPresetDeleteDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetDeleteDisabled(presetName);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void closeGraphContextMenus() {
            nodeGraph.closeContextMenu();
            nodeGraph.closeNodeContextMenu();
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void createPresetGroup() {
            PathmindVisualEditorScreen.this.createPresetGroup();
        }

        @Override
        public void deletePresetGroup(String groupKey) {
            PathmindVisualEditorScreen.this.deletePresetGroup(groupKey);
        }

        @Override
        public void recolorPresetGroup(String oldKey, String newKey) {
            PathmindVisualEditorScreen.this.recolorPresetGroup(oldKey, newKey);
        }

        @Override
        public void openRenamePresetPopup(String presetName) {
            PathmindVisualEditorScreen.this.openRenamePresetPopup(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void setPresetGroupColor(String presetName, String colorKey) {
            PathmindVisualEditorScreen.this.setPresetGroupColor(presetName, colorKey);
        }
    }

    private final class ModalOverlayHost implements PathmindModalOverlayController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public boolean isParameterOverlayVisible() {
            return parameterOverlay != null && parameterOverlay.isVisible();
        }

        @Override
        public boolean isBookTextEditorVisible() {
            return bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible();
        }

        @Override
        public int parameterOverlayScrimColor() {
            return parameterOverlay != null ? parameterOverlay.getScrimColor() : UITheme.OVERLAY_BACKGROUND;
        }

        @Override
        public int bookTextEditorScrimColor() {
            return bookTextEditorOverlay != null ? bookTextEditorOverlay.getScrimColor() : UITheme.OVERLAY_BACKGROUND;
        }

        @Override
        public int[] parameterOverlayBounds() {
            return parameterOverlay != null ? parameterOverlay.getScaledPopupBounds() : null;
        }

        @Override
        public int[] bookTextEditorBounds() {
            return bookTextEditorOverlay != null ? bookTextEditorOverlay.getScaledPopupBounds() : null;
        }
    }

    private final class FirstRunTutorialHost implements PathmindFirstRunTutorialController.Host {
        @Override
        public boolean isScreenPopupVisible() {
            return PathmindVisualEditorScreen.this.isScreenPopupVisible();
        }

        @Override
        public boolean isScreenCoordinateCaptureActive() {
            return nodeGraph.isScreenCoordinateCaptureActive();
        }

        @Override
        public boolean isParameterOverlayVisible() {
            return parameterOverlay != null && parameterOverlay.isVisible();
        }

        @Override
        public boolean isBookTextEditorVisible() {
            return bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible();
        }

        @Override
        public void switchPreset(String presetName) {
            PathmindVisualEditorScreen.this.switchPreset(presetName);
        }

        @Override
        public void closeSettingsPopup() {
            PathmindVisualEditorScreen.this.closeSettingsPopup();
        }

        @Override
        public void hideSettingsPopupInstantly() {
            settingsPopupAnimation.hideInstant();
        }

        @Override
        public void focusNodeById(String nodeId) {
            nodeGraph.focusNodeById(nodeId, width, height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int presetTabRightLimit() {
            return getPresetTabRightLimit();
        }

        @Override
        public int stopButtonX() {
            return getStopButtonX();
        }

        @Override
        public int stopButtonY() {
            return getStopButtonY();
        }

        @Override
        public int playButtonX() {
            return getPlayButtonX();
        }

        @Override
        public int validationButtonX() {
            return getValidationButtonX();
        }

        @Override
        public int validationButtonY() {
            return getValidationButtonY();
        }

        @Override
        public int publishButtonX() {
            return getPublishButtonX();
        }

        @Override
        public int marketplaceButtonX() {
            return getMarketplaceButtonX();
        }

        @Override
        public int workspaceButtonY() {
            return getWorkspaceButtonY();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public float zoomScale() {
            return nodeGraph.getZoomScale();
        }

        @Override
        public List<Node> nodes() {
            return nodeGraph.getNodes();
        }

        @Override
        public int worldToScreenX(int worldX) {
            return nodeGraph.worldToScreenX(worldX);
        }

        @Override
        public int worldToScreenY(int worldY) {
            return nodeGraph.worldToScreenY(worldY);
        }
    }

    private final class WorkspaceLifecycleHost implements PathmindWorkspaceLifecycleController.Host {
        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public String activePresetName() {
            return activePresetName;
        }

        @Override
        public void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
            PathmindVisualEditorScreen.this.openRenameRoutinePopup(routine);
        }

        @Override
        public void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
            PathmindVisualEditorScreen.this.openRenameLibraryRoutinePopup(routine);
        }

        @Override
        public int routineExitButtonX() {
            return getRoutineExitButtonX();
        }

        @Override
        public int routineExitButtonY() {
            return getRoutineExitButtonY();
        }

        @Override
        public float hoverProgress(String key, boolean hovered) {
            return getHoverProgress(key, hovered);
        }
    }

    private final class WorkspaceDialogHost implements PathmindWorkspaceDialogController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public int[] boundedScaledPopupBounds(PopupAnimationHandler animation, int width, int height) {
            return getBoundedScaledPopupBounds(animation, width, height);
        }

        @Override
        public int boundedPopupContentY(int popupY, PopupAnimationHandler animation, int preferredHeight) {
            return getBoundedPopupContentY(popupY, animation, preferredHeight);
        }

        @Override
        public void setOverlayCutout(int x, int y, int width, int height) {
            PathmindVisualEditorScreen.this.setOverlayCutout(x, y, width, height);
        }

        @Override
        public void drawPopupContainer(GuiGraphics context, int x, int y, int width, int height,
                                       PopupAnimationHandler animation) {
            PathmindVisualEditorScreen.this.drawPopupContainer(context, x, y, width, height, animation);
        }

        @Override
        public boolean enablePopupScissor(GuiGraphics context, int popupX, int popupY, int width, int height) {
            return PathmindVisualEditorScreen.this.enablePopupScissor(context, popupX, popupY, width, height);
        }

        @Override
        public void disablePopupScissor(GuiGraphics context, boolean enabled) {
            PathmindVisualEditorScreen.this.disablePopupScissor(context, enabled);
        }

        @Override
        public int popupAnimatedColor(PopupAnimationHandler animation, int color) {
            return getPopupAnimatedColor(animation, color);
        }

        @Override
        public void drawPopupTextWithEllipsis(
            GuiGraphics context, String text, int x, int y, int maxWidth, int color
        ) {
            PathmindVisualEditorScreen.this.drawPopupTextWithEllipsis(
                context, text, x, y, maxWidth, color);
        }

        @Override
        public void drawPopupCenteredTextWithEllipsis(
            GuiGraphics context, String text, int centerX, int y, int maxWidth, int color
        ) {
            PathmindVisualEditorScreen.this.drawPopupCenteredTextWithEllipsis(
                context, text, centerX, y, maxWidth, color);
        }

        @Override
        public void drawPopupButton(
            GuiGraphics context, int x, int y, int width, int height, boolean hovered,
            Component label, PathmindPopupRenderer.ButtonStyle style, PopupAnimationHandler animation
        ) {
            PathmindVisualEditorScreen.this.drawPopupButton(
                context, x, y, width, height, hovered, label, style, animation);
        }

        @Override
        public void resetBoundedPopupScroll(PopupAnimationHandler animation) {
            PathmindVisualEditorScreen.this.resetBoundedPopupScroll(animation);
        }

        @Override
        public void dismissParameterOverlay() {
            PathmindVisualEditorScreen.this.dismissParameterOverlay();
        }

        @Override
        public void closeCreatePresetPopupIfVisible() {
            if (createPresetPopupAnimation.isVisible()) {
                closeCreatePresetPopup();
            }
        }

        @Override
        public void closeSettingsPopup() {
            PathmindVisualEditorScreen.this.closeSettingsPopup();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void clearWorkspace() {
            nodeGraph.clearWorkspace();
        }

        @Override
        public boolean containsBaritoneNodes() {
            return nodeGraph.containsBaritoneNodes();
        }

        @Override
        public boolean containsUiUtilsNodes() {
            return nodeGraph.containsUiUtilsNodes();
        }

        @Override
        public NodeGraphData snapshotRootPresetWorkspace() {
            return PathmindVisualEditorScreen.this.snapshotRootPresetWorkspace();
        }

        @Override
        public String activePresetName() {
            return activePresetName;
        }

        @Override
        public void applyImportedPreset(String presetName, NodeGraphData importedData) {
            PathmindVisualEditorScreen.this.applyImportedPreset(presetName, importedData);
        }

        @Override
        public void runOnClientThread(Runnable task) {
            PathmindVisualEditorScreen.this.runOnClientThread(task);
        }

        @Override
        public String currentMinecraftVersion() {
            return getCurrentMinecraftVersion();
        }

        @Override
        public String modVersion() {
            return getModVersion();
        }

        @Override
        public String loaderVersion() {
            return getLoaderVersion();
        }

        @Override
        public void copyToClipboard(String value) {
            if (minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(value);
            }
        }
    }

    private final class WorkspaceViewportHost implements PathmindWorkspaceViewportController.Host {
        @Override
        public Font font() {
            return PathmindVisualEditorScreen.this.font;
        }

        @Override
        public int screenWidth() {
            return PathmindVisualEditorScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return PathmindVisualEditorScreen.this.height;
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public Minecraft client() {
            return PathmindVisualEditorScreen.this.minecraft;
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public boolean showGrid() {
            return showGrid;
        }

        @Override
        public boolean isSidebarDragActive() {
            return isDraggingFromSidebar && sidebarDragActivated;
        }

        @Override
        public NodeType draggingNodeType() {
            return draggingNodeType;
        }

        @Override
        public Node draggingSidebarNode() {
            return draggingSidebarNode;
        }

        @Override
        public boolean isNodeDragBlocked(NodeType nodeType) {
            return shouldBlockBaritoneNode(nodeType) || shouldBlockUiUtilsNode(nodeType);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
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
        this.workspaceDialogController = new PathmindWorkspaceDialogController(
            new WorkspaceDialogHost(),
            baritoneAvailable,
            uiUtilsAvailable,
            clearPopupAnimation,
            importExportPopupAnimation,
            infoPopupAnimation,
            missingBaritonePopupAnimation,
            missingUiUtilsPopupAnimation
        );
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
        this.renderConnectionsOnTop = currentSettings.renderConnectionsOnTop != null && currentSettings.renderConnectionsOnTop;
        this.showWorkspaceTooltips = currentSettings.showTooltips == null || currentSettings.showTooltips;
        this.showChatErrors = currentSettings.showChatErrors == null || currentSettings.showChatErrors;
        this.showHudOverlays = currentSettings.showHudOverlays == null || currentSettings.showHudOverlays;
        this.skipPresetDeleteConfirm = currentSettings.skipPresetDeleteConfirm != null && currentSettings.skipPresetDeleteConfirm;
        this.firstRunTutorialController.initialize(Boolean.TRUE.equals(currentSettings.firstRunTutorialCompleted));
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
        workspaceViewportController.ensureSystemCursorHidden();
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
        if (publishPresetNameField == null) {
            publishPresetNameField = PathmindTextField.createInactive(this.font, 0, 0, 240, 20, Component.translatable("pathmind.field.presetName"), 64);
            publishPresetNameField.setResponder(value -> clearPublishPresetStatus());
            this.addWidget(publishPresetNameField);
        }
        if (publishPresetDescriptionField == null) {
            publishPresetDescriptionField = PathmindTextField.createInactive(this.font, 0, 0, 240, 20, Component.translatable("pathmind.field.description"), 180);
            publishPresetDescriptionField.setResponder(value -> clearPublishPresetStatus());
            this.addWidget(publishPresetDescriptionField);
        }
        if (publishPresetTagsField == null) {
            publishPresetTagsField = PathmindTextField.createInactive(this.font, 0, 0, 240, 20, Component.translatable("pathmind.field.tags"), 96);
            publishPresetTagsField.setResponder(value -> clearPublishPresetStatus());
            this.addWidget(publishPresetTagsField);
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
        nodeSearchController.initialize();
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
        boolean titleActive = titleHovered || presetDropdownController.isOpen();
        titleUnderlineAnimation.animateTo(titleActive ? 1f : 0f, UITheme.HOVER_ANIM_MS);
        titleUnderlineAnimation.tick();
        GraphValidationResult validationResult = nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);

        nodeGraph.setSidebarWidth(sidebar.getWidth());
        nodeGraph.setExecutionEnabled(validationExecutionController.shouldShowExecutionControls());
        nodeGraph.updateMouseHover(mouseX, mouseY);
        renderNodeGraph(context, mouseX, mouseY, delta, false);

        // Start a new GUI layer so UI chrome always sits above workspace nodes.
        DrawContextBridge.startNewRootLayer(context);

        renderWorkspaceButtons(context, mouseX, mouseY);
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
        publishPresetPopupAnimation.tick();
        renamePresetPopupAnimation.tick();
        presetDeletePopupAnimation.tick();
        infoPopupAnimation.tick();
        missingBaritonePopupAnimation.tick();
        missingUiUtilsPopupAnimation.tick();
        settingsPopupAnimation.tick();
        validationExecutionController.tick();

        boolean controlsDisabled = isPopupObscuringWorkspace();
        int chromeMouseX = controlsDisabled ? Integer.MIN_VALUE : mouseX;
        int chromeMouseY = controlsDisabled ? Integer.MIN_VALUE : mouseY;
        workspaceViewportController.renderZoomControls(context, chromeMouseX, chromeMouseY, false);

        if (validationExecutionController.shouldShowExecutionControls()) {
            renderRoutineWorkspaceExitButton(context, chromeMouseX, chromeMouseY);
        }
        validationExecutionController.render(context, mouseX, mouseY, controlsDisabled, validationResult);
        renderBottomLeftWorkspaceButtons(context, mouseX, mouseY);
        renderSettingsButton(context, chromeMouseX, chromeMouseY, false);
        validationExecutionController.renderTooltip(context, mouseX, mouseY, controlsDisabled, validationResult);
        renderPresetDropdown(context, mouseX, mouseY, controlsDisabled);

        if (controlsDisabled) {
            DrawContextBridge.startNewRootLayer(context);
        }

        //? if MC_1_21_8 {
        /*Object popupMatrices = context.pose();
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
        *///?}

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

        if (publishPresetPopupAnimation.isVisible()) {
            presetPopupController.renderPublishPresetPopup(context, mouseX, mouseY, delta);
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

        //? if MC_1_21_8 {
        /*// Legacy rendering draws the scrim before popup contents inside the translated pose.*/
        //?} else {
        if (!isScreenPopupVisible()) {
            setOverlayCutoutForNodeOverlay();
        }

        renderPopupScrimOverlay(context);
        //?}

        // Render language dropdown options on top of scrim overlay
        if (settingsPopupAnimation.isVisible()) {
            RenderStateBridge.setShaderColor(1f, 1f, 1f, settingsPopupAnimation.getPopupAlpha());
            drawLanguageDropdownOptions(context, languageDropdownX, languageDropdownY, languageDropdownWidth, mouseX, mouseY);
            //? if MC_1_21_8 {
            /*RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);*/
            //?}
        }

        //? if MC_1_21_8 {
        /*} finally {
            if (popupDepthPushed) {
                DrawContextBridge.flush(context);
                MatrixStackBridge.pop(popupMatrices);
            }
        }*/
        //?}

        // Render context menu on top of everything
        if (presetContextMenuController.isOpen()) {
            nodeGraph.closeNodeContextMenu();
            nodeGraph.closeContextMenu();
        } else {
            nodeGraph.updateNodeContextMenuHover(mouseX, mouseY);
            nodeGraph.renderNodeContextMenu(context, this.font);
            nodeGraph.renderStartModeDropdown(context, this.font, mouseX, mouseY);
            nodeGraph.updateContextMenuHover(mouseX, mouseY);
            nodeGraph.renderContextMenu(context, this.font, mouseX, mouseY);
        }
        presetContextMenuController.render(context, mouseX, mouseY);
        nodeSearchController.render(context, mouseX, mouseY, delta);
        DrawContextBridge.startNewRootLayer(context);
        renderDraggedWorkspaceLayer(context, mouseX, mouseY, delta);
        if (isDraggingFromSidebar && sidebarDragActivated && (draggingNodeType != null || draggingSidebarNode != null)) {
            workspaceViewportController.renderDragPreview(context, mouseX, mouseY);
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
        firstRunTutorialController.maybeShow();
        firstRunTutorialController.render(context, this.font, mouseX, mouseY);
        DrawContextBridge.startNewRootLayer(context);
        workspaceViewportController.renderCursor(context, mouseX, mouseY);
        } finally {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    void replayFirstRunTutorial() {
        firstRunTutorialController.replay();
    }

    private boolean isPopupObscuringWorkspace() {
        return modalOverlayController.isObscuringWorkspace();
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

        presetDropdownController.close();
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
        modalOverlayController.resetCutout();
    }

    void setOverlayCutout(int x, int y, int width, int height) {
        modalOverlayController.setCutout(x, y, width, height);
    }

    private boolean isScreenPopupVisible() {
        return modalOverlayController.isScreenPopupVisible();
    }

    private void renderPopupScrimOverlay(GuiGraphics context) {
        modalOverlayController.renderScrim(context);
    }

    private void setOverlayCutoutForNodeOverlay() {
        modalOverlayController.setCutoutForNodeOverlay();
    }

    private boolean shouldBlockBaritoneNode(NodeType nodeType) {
        return workspaceDialogController.shouldBlockBaritoneNode(nodeType);
    }

    private void refreshMissingBaritonePopup() {
        workspaceDialogController.refreshMissingBaritonePopup();
    }

    private boolean shouldBlockUiUtilsNode(NodeType nodeType) {
        return workspaceDialogController.shouldBlockUiUtilsNode(nodeType);
    }

    private void refreshMissingUiUtilsPopup() {
        workspaceDialogController.refreshMissingUiUtilsPopup();
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
            workspaceViewportController.renderGrid(context);
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
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        *///?} else {
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            if (button == 0) {
                return nodeGraph.commitScreenCoordinateCapture((int) mouseX, (int) mouseY);
            }
            return true;
        }
        if (firstRunTutorialController.isVisible()) {
            return firstRunTutorialController.mouseClicked(mouseX, mouseY, button);
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return handleMissingBaritonePopupClick(mouseX, mouseY, button);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
        }
        if (settingsPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (handleSettingsPopupClick(mouseX, mouseY, button)) {
                *///?} else {
            if (handleSettingsPopupClick(click, inBounds)) {
                //?}
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

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleMouseClicked(mouseX, mouseY, button)) {
            *///?} else {
        if (presetPopupController.handleMouseClicked(click, inBounds)) {
            //?}
            return true;
        }

        if (isInlinePresetRenameActive()) {
            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(mouseX, mouseY, button)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(click, inBounds)) {
                //?}
                return true;
            }
            stopInlinePresetRename(true);
        }

        if (presetDeletePopupAnimation.isVisible()) {
            presetPopupController.handlePresetDeletePopupClick(mouseX, mouseY, button);
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

        if (presetContextMenuController.isOpen()) {
            if (button == 0 && presetContextMenuController.handleClick((int) mouseX, (int) mouseY)) {
                return true;
            }
            presetContextMenuController.close();
            return true;
        }

        if (button == 1 && isPointInPresetTabBarContextZone((int) mouseX, (int) mouseY)) {
            presetContextMenuController.open((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetDropdownController.isOpen()) {
            if (presetDropdownController.handleMouseDown(mouseX, mouseY)) {
                return true;
            }
            presetDropdownController.close();
            return true;
        }

        if (button == 0) {
            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                presetDropdownController.toggle();
                return true;
            }
            if (handleWorkspaceTabClick((int) mouseX, (int) mouseY)) {
                return true;
            }
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (validationExecutionController.handlePanelClick((int) mouseX, (int) mouseY)) {
                return true;
            }
        } else if (button == 0) {
            validationExecutionController.closePanel();
        }

        if (!isPopupObscuringWorkspace() && button == 0
            && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT
            && handleStartNodeClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (!isPopupObscuringWorkspace()
            && validationExecutionController.handleExecutionClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (workspaceViewportController.handleZoomButtonClick((int) mouseX, (int) mouseY)) {
                return true;
            }
            if (validationExecutionController.handleValidationButtonClick((int) mouseX, (int) mouseY, button)) {
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
                saveRootPresetWorkspace();
                PresetManager.setActivePreset(activePresetName);
                openPublishPresetPopup();
                return true;
            }
        }

        if (nodeSearchController.isOpen()) {
            return nodeSearchController.handleClick((int) mouseX, (int) mouseY, button);
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
        }        if (button == 0 && nodeGraph.handleOperatorToggleClick(font, (int)mouseX, (int)mouseY)) {
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
                    nodeSearchController.open((int) mouseX, (int) mouseY);
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
        
        //? if MC_1_21_8 {
        /*return super.mouseClicked(mouseX, mouseY, button);*/
        //?} else {
        return super.mouseClicked(click, inBounds);
        //?}
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

                if (nodeGraph.handleStickyNoteResizeHandleClick(clickedNode, (int) mouseX, (int) mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    return true;
                }

                if (nodeGraph.isPointInsideStickyNoteTextArea(clickedNode, (int) mouseX, (int) mouseY)) {
                    nodeGraph.focusSelectedNode(clickedNode);
                    nodeGraph.startStickyNoteEditing(clickedNode);
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
                nodeGraph.stopStickyNoteEditing(true);
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
                    nodeGraph.stopStickyNoteEditing(true);
                    nodeGraph.stopParameterEditing(true);
                    nodeGraph.stopEventNameEditing(true);
                    nodeGraph.beginSelectionBox((int) mouseX, (int) mouseY);
                }
                return true;
        }

        return false;
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        *///?} else {
    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
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
                int popupWidth = getSettingsPopupWidth();
                int contentPopupY = popupY - settingsPopupScrollOffset;
                int contentX = popupX + 20;
                int selectorWidth = popupWidth - 40;
                int nodeSettingsBodyY = getSettingsNodeSectionBodyY(contentPopupY);
                int maxSelectorScroll = getSettingsNodeTypeSelectorMaxScroll(selectorWidth);
                ScrollbarHelper.Metrics selectorScrollMetrics = getSettingsNodeTypeSelectorScrollMetrics(contentX, nodeSettingsBodyY, selectorWidth, maxSelectorScroll);
                settingsNodeSelectorScrollOffset = ScrollbarHelper.scrollFromThumb(selectorScrollMetrics, (int) mouseY - settingsNodeSelectorScrollDragOffset);
            }
            if (settingsPopupScrollDragging) {
                int popupX = getSettingsPopupX();
                int popupY = getSettingsPopupY();
                int popupWidth = getSettingsPopupWidth();
                int popupHeight = getSettingsPopupHeight();
                int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
                ScrollbarHelper.Metrics scrollMetrics = getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll);
                settingsPopupScrollOffset = ScrollbarHelper.scrollFromThumb(scrollMetrics, (int) mouseY - settingsPopupScrollDragOffset);
            }
            if (nodeDelayDragging) {
                updateNodeDelayFromMouse((int) mouseX, getSettingsPopupX(), getSettingsPopupWidth());
            }
            if (createListRadiusDragging) {
                updateCreateListRadiusFromMouse(getEffectiveSettingsTargetNode(), (int) mouseX, getSettingsPopupX(), getSettingsPopupWidth());
            }
            return true;
        }
        if (createPresetPopupAnimation.isVisible()) {
            return true;
        }

        if (publishPresetPopupAnimation.isVisible()) {
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

        if (sidebar.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.updateSelectionBox((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetTabController.hasPendingPresetTabInteraction()) {
            updatePendingPresetTabInteraction((int) mouseX, (int) mouseY);
            if (presetTabController.isDraggingPresetTab()) {
                return true;
            }
        }
        if (button == 0 && presetTabController.hasPendingPresetDropdownDrag()) {
            updatePendingPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && presetTabController.isDraggingPresetDropdown()) {
            updatePresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (presetTabController.isDraggingPresetTab()) {
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
        
        //? if MC_1_21_8 {
        /*return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);*/
        //?} else {
        return super.mouseDragged(click, deltaX, deltaY);
        //?}
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        *///?} else {
    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            settingsNodeSelectorScrollDragging = false;
            settingsPopupScrollDragging = false;
            nodeDelayDragging = false;
            createListRadiusDragging = false;
            if (nodeDelayField != null) {
                //? if MC_1_21_8 {
                /*nodeDelayField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                nodeDelayField.mouseReleased(click);
                //?}
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleMouseReleased(mouseX, mouseY, button)) {
            *///?} else {
        if (presetPopupController.handleMouseReleased(click)) {
            //?}
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (sidebar.mouseReleased(button)) {
            return true;
        }

        if (button == 0 && nodeGraph.isSelectionBoxActive()) {
            nodeGraph.completeSelectionBox();
            return true;
        }

        if (presetTabController.isDraggingPresetTab()) {
            endPresetTabDrag();
            return true;
        }

        if (button == 0 && presetTabController.isDraggingPresetDropdown()) {
            finishPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetTabController.releasePendingPresetDropdownDrag()) {
            return true;
        }

        if (isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null) {
                //? if MC_1_21_8 {
                /*inlinePresetRenameField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                inlinePresetRenameField.mouseReleased(click);
                //?}
            }
            return true;
        }

        if (button == 0 && presetTabController.releasePendingPresetTabInteraction()) {
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
                    if (draggingFromRoutineLibrary) {
                        openLibraryRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
                    } else {
                        openRoutineWorkspaceTab(draggingSidebarNode.getRoutineId());
                    }
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
        //? if MC_1_21_8 {
        /*return super.mouseReleased(mouseX, mouseY, button);*/
        //?} else {
        return super.mouseReleased(click);
        //?}
    }

    //? if MC_1_21_8 {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        *///?} else {
    @Override
    public boolean keyPressed(KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return firstRunTutorialController.keyPressed(keyCode);
        }
        if (nodeSearchController.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeSearchController.close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                nodeSearchController.selectCurrentOrClose();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                nodeSearchController.moveSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                nodeSearchController.moveSelection(1);
                return true;
            }
            //? if MC_1_21_8 {
            /*if (nodeSearchController.field() != null && nodeSearchController.field().keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (nodeSearchController.field() != null && nodeSearchController.field().keyPressed(input)) {
                //?}
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
            //? if MC_1_21_8 {
            /*if (nodeDelayField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (nodeDelayField.keyPressed(input)) {
                //?}
                return true;
            }
        }
        if (settingsPopupAnimation.isVisible() && settingsNodeSearchField != null && settingsNodeSearchField.isFocused()) {
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
        if (settingsPopupAnimation.isVisible() && createListRadiusField != null && createListRadiusField.isFocused()) {
            //? if MC_1_21_8 {
            /*if (createListRadiusField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (createListRadiusField.keyPressed(input)) {
                //?}
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

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleKeyPressed(keyCode, scanCode, modifiers)) {
            *///?} else {
        if (presetPopupController.handleKeyPressed(input)) {
            //?}
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

            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(input)) {
                //?}
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

            if (!workspaceDialogController.isImportExportBusy()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                attemptImport();
                return true;
            }

            return true;
        }

        if (presetDropdownController.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presetDropdownController.close();
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

        if (nodeGraph.handleStickyNoteKeyPressed(keyCode, modifiers)) {
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
        
        //? if MC_1_21_8 {
        /*return super.keyPressed(keyCode, scanCode, modifiers);*/
        //?} else {
        return super.keyPressed(input);
        //?}
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean charTyped(char chr, int modifiers) {
        *///?} else {
    @Override
    public boolean charTyped(CharacterEvent input) {
        //? if PRE_1_21_11 {
        /*int modifiers = input.modifiers();*/
        //?} else {
        int modifiers = CharacterEventModifiers.get(input, this.minecraft);
        //?}
        char chr = (char) input.codepoint();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (nodeSearchController.isOpen()) {
            //? if MC_1_21_8 {
            /*if (nodeSearchController.field() != null && nodeSearchController.field().charTyped(chr, modifiers)) {
                *///?} else {
            if (nodeSearchController.field() != null && nodeSearchController.field().charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
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
        if (validationExecutionController.isPanelOpen()) {
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleCharTyped(chr, modifiers)) {
            *///?} else {
        if (presetPopupController.handleCharTyped(input)) {
            //?}
            return true;
        }

        if (isInlinePresetRenameActive()) {
            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(chr, modifiers)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(input)) {
                //?}
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

        if (nodeGraph.handleStickyNoteCharTyped(chr, modifiers)) {
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

        //? if MC_1_21_8 {
        /*return super.charTyped(chr, modifiers);*/
        //?} else {
        return super.charTyped(input);
        //?}
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            return true;
        }
        if (settingsPopupAnimation.isVisible()) {
            int popupX = getSettingsPopupX();
            int popupY = getSettingsPopupY();
            int popupWidth = getSettingsPopupWidth();
            int popupHeight = getSettingsPopupHeight();
            int contentPopupY = popupY - settingsPopupScrollOffset;
            int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
            int[] selectorBounds = getSettingsNodeTypeSelectorBounds(popupX + 20, getSettingsNodeSectionBodyY(contentPopupY), popupWidth - 40);
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
                int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
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
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, infoPopupAnimation, INFO_POPUP_WIDTH, INFO_POPUP_HEIGHT);
        }

        if (createPresetPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, createPresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        }

        if (publishPresetPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        }

        if (renamePresetPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, renamePresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        }

        if (clearPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, clearPopupAnimation, 280, 150);
        }

        if (importExportPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, importExportPopupAnimation, 360, 210);
        }

        if (presetDeletePopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, presetDeletePopupAnimation, PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        }

        if (missingBaritonePopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, missingBaritonePopupAnimation, MISSING_BARITONE_POPUP_WIDTH, MISSING_BARITONE_POPUP_HEIGHT);
        }

        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, missingUiUtilsPopupAnimation, MISSING_UI_UTILS_POPUP_WIDTH, MISSING_UI_UTILS_POPUP_HEIGHT);
        }

        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleMouseScroll(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (presetDropdownController.isOpen()) {
            presetDropdownController.handleScroll(mouseX, mouseY, verticalAmount);
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
            workspaceViewportController.zoomByScroll(verticalAmount);
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
        workspaceLifecycleController.resetFromCurrentGraph();
    }

    private void renderWorkspaceTabs(GuiGraphics context, int mouseX, int mouseY) {
        presetTabController.render(context, mouseX, mouseY);
    }

    private boolean handleWorkspaceTabClick(int mouseX, int mouseY) {
        return presetTabController.handleClick(mouseX, mouseY);
    }

    private int getPresetTabRightLimit() {
        return presetTabController.getPresetTabRightLimit();
    }

    private void clearPendingPresetTabInteraction() {
        presetTabController.clearPendingPresetTabInteraction();
    }

    private void updatePendingPresetTabInteraction(int mouseX, int mouseY) {
        presetTabController.updatePendingPresetTabInteraction(mouseX, mouseY);
    }

    private void updatePresetTabDrag(int mouseX) {
        presetTabController.updatePresetTabDrag(mouseX);
    }

    private void endPresetTabDrag() {
        presetTabController.endPresetTabDrag();
    }

    private boolean isPointInPresetTabBarContextZone(int mouseX, int mouseY) {
        return presetTabController.isPointInPresetTabBarContextZone(mouseX, mouseY);
    }

    private String getPresetTabAt(int mouseX, int mouseY) {
        return presetTabController.getPresetTabAt(mouseX, mouseY);
    }

    private String getPresetGroupAt(int mouseX, int mouseY) {
        return presetTabController.getPresetGroupAt(mouseX, mouseY);
    }

    private String getPresetGroupColorLabel(String key) {
        return presetTabController.getPresetGroupColorLabel(key);
    }

    private String getNextPresetGroupColorKey() {
        return presetTabController.getNextPresetGroupColorKey();
    }

    private void createPresetGroup() {
        presetTabController.createPresetGroup();
    }

    private void deletePresetGroup(String groupKey) {
        presetTabController.deletePresetGroup(groupKey);
    }

    private void recolorPresetGroup(String oldKey, String newKey) {
        presetTabController.recolorPresetGroup(oldKey, newKey);
    }

    private void setPresetGroupColor(String presetName, String colorKey) {
        presetTabController.setPresetGroupColor(presetName, colorKey);
    }

    private boolean isPresetGroupTab(String tabName) {
        return presetTabController.isPresetGroupTab(tabName);
    }

    private String getPresetGroupKeyFromTab(String tabName) {
        return presetTabController.getPresetGroupKeyFromTab(tabName);
    }

    private String getPresetGroupKey(String presetName) {
        return presetTabController.getPresetGroupKey(presetName);
    }

    private int getPresetGroupColor(String presetName) {
        return presetTabController.getPresetGroupColor(presetName);
    }

    private void clearPresetDropdownDragState() {
        presetTabController.clearPresetDropdownDragState();
    }

    private void updatePendingPresetDropdownDrag(int mouseX, int mouseY) {
        presetTabController.updatePendingPresetDropdownDrag(mouseX, mouseY);
    }

    private void updatePresetDropdownDrag(int mouseX, int mouseY) {
        presetTabController.updatePresetDropdownDrag(mouseX, mouseY);
    }

    private void finishPresetDropdownDrag(int mouseX, int mouseY) {
        presetTabController.finishPresetDropdownDrag(mouseX, mouseY);
    }

    private void renderDraggedPresetDropdownTab(GuiGraphics context, int mouseX, int mouseY) {
        presetTabController.renderDraggedPresetDropdownTab(context, mouseX, mouseY);
    }

    private boolean isInlinePresetRenameActive() {
        return presetTabController.isInlinePresetRenameActive();
    }

    private boolean canStartInlinePresetRename(String presetName) {
        return presetTabController.canStartInlinePresetRename(presetName);
    }

    private void startInlinePresetRename(String presetName) {
        presetTabController.startInlinePresetRename(presetName);
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
        String renamedKey = renamedPreset.get();
        if (currentSettings != null && currentSettings.presetGroupColors != null && currentSettings.presetGroupColors.containsKey(currentName)) {
            String groupKey = currentSettings.presetGroupColors.remove(currentName);
            currentSettings.presetGroupColors.put(renamedKey, groupKey);
            SettingsManager.save(currentSettings);
        }
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        presetDropdownController.close();
        if (renamingActive) {
            updateImportExportPathFromPreset();
        }
        return true;
    }

    void stopInlinePresetRename(boolean commit) {
        presetTabController.stopInlinePresetRename(commit);
    }

    private int[] computePresetTabWidths(int availableWidth, int createTabWidth) {
        return presetTabController.computePresetTabWidths(availableWidth, createTabWidth);
    }

    private void openTemplateWorkspaceTab(Node templateNode) {
        workspaceLifecycleController.openTemplateWorkspaceTab(templateNode);
    }

    private void refreshRoutineSidebarContext() {
        workspaceLifecycleController.refreshRoutineSidebarContext();
    }

    private NodeGraphData.RoutineDefinitionData getActiveRoutineWorkspace() {
        return workspaceLifecycleController.getActiveRoutineWorkspace();
    }

    private List<NodeGraphData.RoutineDefinitionData> getActiveRoutineRegistry() {
        return workspaceLifecycleController.getActiveRoutineRegistry();
    }

    private void renderRoutineWorkspaceExitButton(GuiGraphics context, int mouseX, int mouseY) {
        workspaceLifecycleController.renderRoutineWorkspaceExitButton(context, mouseX, mouseY);
    }

    private void createRoutineFromSidebar(String name) {
        workspaceLifecycleController.createRoutineFromSidebar(name);
    }

    private void addInputToActiveRoutine() {
        workspaceLifecycleController.addInputToActiveRoutine();
    }

    private void editActiveRoutineInput(String inputId, int action) {
        workspaceLifecycleController.editActiveRoutineInput(inputId, action);
    }

    private void handleRoutineAction(String routineId, int action) {
        workspaceLifecycleController.handleRoutineAction(routineId, action);
    }

    private void handleRoutineLibraryAction(String libraryRoutineId, int action) {
        workspaceLifecycleController.handleRoutineLibraryAction(libraryRoutineId, action);
    }

    private boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY) {
        return workspaceLifecycleController.saveDraggedRoutineToLibrary(
            mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode);
    }

    private Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY) {
        return workspaceLifecycleController.dropDraggedSidebarNodeIntoWorkspace(
            mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode, draggingNodeType);
    }

    private boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY) {
        return workspaceLifecycleController.importDraggedLibraryRoutineToList(
            mouseX, mouseY, draggingFromRoutineLibrary, draggingSidebarNode);
    }

    private void switchToRootAfterRoutineChange(NodeGraphData root) {
        workspaceLifecycleController.switchToRootAfterRoutineChange(root);
    }

    private void openRoutineWorkspaceTab(String routineId) {
        workspaceLifecycleController.openRoutineWorkspaceTab(routineId);
    }

    private void openLibraryRoutineWorkspaceTab(String routineId) {
        workspaceLifecycleController.openLibraryRoutineWorkspaceTab(routineId);
    }

    private void switchToWorkspaceTab(int targetIndex) {
        workspaceLifecycleController.switchToWorkspaceTab(targetIndex);
    }

    boolean saveRootPresetWorkspace() {
        return workspaceLifecycleController.saveRootPresetWorkspace();
    }

    private NodeGraphData snapshotRootPresetWorkspace() {
        return workspaceLifecycleController.snapshotRootPresetWorkspace();
    }

    private void persistActiveWorkspaceToTabs() {
        workspaceLifecycleController.persistActiveWorkspaceToTabs();
    }

    private void syncAllTemplateTabsIntoParents() {
        workspaceLifecycleController.syncAllTemplateTabsIntoParents();
    }

    private void restoreRootWorkspaceIfNeeded() {
        workspaceLifecycleController.restoreRootWorkspaceIfNeeded();
    }

    private void autoSaveWorkspace() {
        workspaceLifecycleController.autoSaveWorkspace();
    }

    @Override
    public void onClose() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        workspaceViewportController.restoreSystemCursor();
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
        workspaceViewportController.restoreSystemCursor();
        super.removed();
    }

    private void renderClearConfirmationPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderClearConfirmationPopup(context, mouseX, mouseY);
    }

    private void renderImportExportPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        workspaceDialogController.renderImportExportPopup(context, mouseX, mouseY, delta);
    }

    private void renderInfoPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderInfoPopup(context, mouseX, mouseY);
    }

    private void renderMissingBaritonePopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderMissingBaritonePopup(context, mouseX, mouseY);
    }

    private void renderMissingUiUtilsPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderMissingUiUtilsPopup(context, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphics context, int mouseX, int mouseY, float underlineProgress) {
        drawTitleMenuButton(context, mouseX, mouseY, underlineProgress);
    }

    private void drawTitleMenuButton(GuiGraphics context, int mouseX, int mouseY, float hoverProgress) {
        int x = getTitleTextX();
        int y = getTitleTextY();
        boolean hovered = isTitleHovered(mouseX, mouseY);
        int iconColor = (hovered || presetDropdownController.isOpen()) ? getAccentColor() : UITheme.ICON_MUTED_BRIGHT;
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

    private int getBoundedPopupWidth(int preferredWidth) {
        return Math.min(preferredWidth, Math.max(1, this.width - 40));
    }

    private int getBoundedPopupHeight(int preferredHeight) {
        return Math.min(preferredHeight, Math.max(1, this.height - 40));
    }

    int[] getBoundedScaledPopupBounds(PopupAnimationHandler animation, int preferredWidth, int preferredHeight) {
        return animation.getScaledPopupBounds(
            this.width,
            this.height,
            getBoundedPopupWidth(preferredWidth),
            getBoundedPopupHeight(preferredHeight)
        );
    }

    private int getBoundedPopupMaxScroll(int preferredHeight) {
        return Math.max(0, preferredHeight - getBoundedPopupHeight(preferredHeight));
    }

    private int getBoundedPopupScrollOffset(PopupAnimationHandler animation, int preferredHeight) {
        int maxScroll = getBoundedPopupMaxScroll(preferredHeight);
        int offset = ScrollbarHelper.clampScroll(popupScrollOffsets.getOrDefault(animation, 0), maxScroll);
        if (offset == 0) {
            popupScrollOffsets.remove(animation);
        } else {
            popupScrollOffsets.put(animation, offset);
        }
        return offset;
    }

    int getBoundedPopupContentY(int popupY, PopupAnimationHandler animation, int preferredHeight) {
        return popupY - getBoundedPopupScrollOffset(animation, preferredHeight);
    }

    void resetBoundedPopupScroll(PopupAnimationHandler animation) {
        popupScrollOffsets.remove(animation);
    }

    private boolean handleBoundedPopupScroll(double mouseX, double mouseY, double verticalAmount,
                                             PopupAnimationHandler animation, int preferredWidth, int preferredHeight) {
        int[] bounds = getBoundedScaledPopupBounds(animation, preferredWidth, preferredHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (verticalAmount != 0.0 && isPointInRect((int) mouseX, (int) mouseY, popupX, popupY, popupWidth, popupHeight)) {
            int maxScroll = getBoundedPopupMaxScroll(preferredHeight);
            int nextOffset = ScrollbarHelper.applyWheel(
                getBoundedPopupScrollOffset(animation, preferredHeight),
                verticalAmount,
                16,
                maxScroll
            );
            if (nextOffset == 0) {
                popupScrollOffsets.remove(animation);
            } else {
                popupScrollOffsets.put(animation, nextOffset);
            }
        }
        return true;
    }

    private boolean handleClearPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleClearPopupClick(mouseX, mouseY, button);
    }

    private boolean handleImportExportPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleImportExportPopupClick(mouseX, mouseY, button);
    }

    private boolean handleMissingBaritonePopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleMissingBaritonePopupClick(mouseX, mouseY, button);
    }

    private boolean handleMissingUiUtilsPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
    }

    private boolean handleInfoPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleInfoPopupClick(mouseX, mouseY, button);
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

    void closeInfoPopup() {
        workspaceDialogController.closeInfoPopup();
    }

    private void openClearPopup() {
        workspaceDialogController.openClearPopup();
    }

    private void confirmClearWorkspace() {
        workspaceDialogController.confirmClearWorkspace();
    }

    private void openImportExportPopup() {
        workspaceDialogController.openImportExportPopup();
    }

    private void closeImportExportPopup() {
        workspaceDialogController.closeImportExportPopup();
    }

    private void attemptImport() {
        workspaceDialogController.attemptImport();
    }

    private void attemptExport() {
        workspaceDialogController.attemptExport();
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
        presetDropdownController.close();

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

    private void clearImportExportStatus() {
        workspaceDialogController.clearImportExportStatus();
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

    private void renderPresetDropdown(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        presetDropdownController.render(context, mouseX, mouseY, disabled);
    }

    private int getPresetOverflowTabRight() {
        return getTitleTextX() + PRESET_MENU_BUTTON_SIZE;
    }

    private int getPlayButtonX() {
        return validationExecutionController.playButtonX();
    }

    private int getPlayButtonY() {
        return validationExecutionController.playButtonY();
    }

    private int getValidationButtonX() {
        return validationExecutionController.validationButtonX();
    }

    private int getValidationButtonY() {
        return validationExecutionController.validationButtonY();
    }

    private int getStopButtonX() {
        return validationExecutionController.stopButtonX();
    }

    private int getStopButtonY() {
        return validationExecutionController.stopButtonY();
    }

    private int getRoutineExitButtonX() {
        return validationExecutionController.routineExitButtonX();
    }

    private int getRoutineExitButtonY() {
        return validationExecutionController.routineExitButtonY();
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

    private void openCreatePresetPopup() {
        createRoutineNaming = false;
        presetDropdownController.close();
        clearCreatePresetStatus();
        closeInfoPopup();
        stopInlinePresetRename(false);
        closeRenamePresetPopup();
        closePublishPresetPopup();
        resetBoundedPopupScroll(createPresetPopupAnimation);
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
        resetBoundedPopupScroll(createPresetPopupAnimation);
        createPresetPopupAnimation.hide();
        clearCreatePresetStatus();
        if (createPresetField != null) {
            PathmindTextField.deactivate(createPresetField);
        }
    }

    private void openPublishPresetPopup() {
        presetPopupController.openPublishPresetPopup();
    }

    void reopenPublishPresetPopup(String presetName) {
        presetPopupController.reopenPublishPresetPopup(presetName);
    }

    void closePublishPresetPopup() {
        presetPopupController.closePublishPresetPopup();
    }

    private void openRenamePresetPopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        presetDropdownController.close();
        clearRenamePresetStatus();
        closeInfoPopup();
        stopInlinePresetRename(false);
        closeCreatePresetPopup();
        pendingPresetRenameName = presetName;
        resetBoundedPopupScroll(renamePresetPopupAnimation);
        renamePresetPopupAnimation.show();
        if (renamePresetField != null) {
            renamePresetField.setValue(presetName);
            renamePresetField.setVisible(true);
            renamePresetField.setEditable(true);
            renamePresetField.setFocused(true);
        }
    }

    void closeRenamePresetPopup() {
        resetBoundedPopupScroll(renamePresetPopupAnimation);
        renamePresetPopupAnimation.hide();
        pendingPresetRenameName = "";
        clearRenamePresetStatus();
        if (renamePresetField != null) {
            PathmindTextField.deactivate(renamePresetField);
        }
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
                if (!workspaceLifecycleController.renameOpenLibraryRoutine(pendingLibraryRoutineRenameId, routineName)) {
                    setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                    return;
                }
                closeCreatePresetPopup();
                return;
            }
            boolean duplicate = workspaceLifecycleController.isDuplicateRoutineName(
                routineName, pendingRoutineRenameId);
            if (duplicate) {
                setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                return;
            }
            if (!pendingRoutineRenameId.isBlank()) {
                workspaceLifecycleController.renameRoutine(pendingRoutineRenameId, routineName);
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

    void attemptPublishPreset() {
        presetPopupController.attemptPublishPreset();
    }

    void startPublishPresetSignIn() {
        presetPopupController.startPublishPresetSignIn();
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
        resetBoundedPopupScroll(presetDeletePopupAnimation);
        presetDeletePopupAnimation.show();
        presetDropdownController.close();
    }

    void closePresetDeletePopup() {
        resetBoundedPopupScroll(presetDeletePopupAnimation);
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
        presetTabController.queueAnimatedPresetDeletion(presetName);
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

        presetDropdownController.close();
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

    void setPublishPresetStatus(String message, int color) {
        publishPresetStatus = message != null ? message : "";
        publishPresetStatusColor = color;
    }

    void clearPublishPresetStatus() {
        publishPresetStatus = "";
        publishPresetStatusColor = UITheme.TEXT_SECONDARY;
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
        presetTabController.movePresetTabToEnd(presetName);
    }

    private void syncPresetTabOrderWithAvailable() {
        presetTabController.refreshAvailablePresets();
    }

    private void updateImportExportPathFromPreset() {
        workspaceDialogController.updateImportExportPathFromPreset();
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
        presetDropdownController.close();
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

    private void renderWorkspaceButtons(GuiGraphics context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int buttonY = getWorkspaceButtonY();
        boolean marketplaceHovered = renderMarketplaceButton(context, mouseX, mouseY, buttonY);
        boolean publishHovered = renderPublishButton(context, mouseX, mouseY, buttonY);

        if (showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            if (publishHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.publishPreset").getString(), mouseX, mouseY, this.width, this.height);
            } else if (marketplaceHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.title").getString(), mouseX, mouseY, this.width, this.height);
            }
        }
    }

    private void renderBottomLeftWorkspaceButtons(GuiGraphics context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int buttonY = getSettingsButtonY();
        boolean importHovered = renderImportExportButton(context, mouseX, mouseY, buttonY);
        boolean clearHovered = renderClearButton(context, mouseX, mouseY, buttonY);
        boolean homeHovered = renderHomeButton(context, mouseX, mouseY, buttonY);

        if (showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            if (homeHovered) {
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

    private int getHomeButtonX() {
        return PathmindWorkspaceChrome.homeButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }

    private int getPublishButtonX() {
        return PathmindWorkspaceChrome.publishButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN);
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

    int getSettingsPopupX() {
        return (this.width - getSettingsPopupWidth()) / 2;
    }

    int getSettingsPopupWidth() {
        return getBoundedPopupWidth(SETTINGS_POPUP_WIDTH);
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

    Minecraft client() {
        return this.minecraft;
    }

    String activePresetName() {
        return activePresetName;
    }

    void closePresetDropdown() {
        presetDropdownController.close();
    }

    Font textRenderer() {
        return this.font;
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

    int[] getSettingsReplayTutorialButtonBounds(int popupX, int popupY, int popupWidth, int popupHeight, int contentX, int nodeSettingsContentY) {
        return settingsPopupController.getSettingsReplayTutorialButtonBounds(popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
    }

    int getAccentColor() {
        return accentOption != null ? accentOption.color : UITheme.ACCENT_DEFAULT;
    }

    private void openSettingsPopup() {
        dismissParameterOverlay();
        closeInfoPopup();
        clearPopupAnimation.hide();
        importExportPopupAnimation.hide();
        presetDropdownController.close();
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

    void closeSettingsPopup() {
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

    //? if MC_1_21_8 {
    /*private boolean handleSettingsPopupClick(double mouseX, double mouseY, int button) {
        *///?} else {
    private boolean handleSettingsPopupClick(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (button != 0) {
            return true;
        }

        int popupX = getSettingsPopupX();
        int popupY = getSettingsPopupY();
        int popupWidth = getSettingsPopupWidth();
        int popupHeight = getSettingsPopupHeight();
        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int contentPopupY = popupY - settingsPopupScrollOffset;
        int[] bodyBounds = getSettingsPopupBodyBounds(popupX, popupY, popupWidth, popupHeight);
        boolean bodyHovered = isPointInRect(mouseXi, mouseYi, bodyBounds[0], bodyBounds[1], bodyBounds[2], bodyBounds[3]);

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            closeSettingsPopup();
            return true;
        }

        int maxScroll = getSettingsPopupMaxScroll(popupX, popupY, popupWidth, popupHeight);
        ScrollbarHelper.Metrics scrollMetrics = getSettingsPopupScrollMetrics(popupX, popupY, popupWidth, popupHeight, maxScroll);
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
        int languageButtonWidth = popupWidth - 40;

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
        int gridToggleX = popupX + popupWidth - SETTINGS_TOGGLE_WIDTH - 20;
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
            renderConnectionsOnTop = !renderConnectionsOnTop;
            currentSettings.renderConnectionsOnTop = renderConnectionsOnTop;
            SettingsManager.save(currentSettings);
            return true;
        }

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        int chatToggleX = gridToggleX;
        int chatToggleY = chatRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, chatToggleX, chatToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showWorkspaceTooltips = !showWorkspaceTooltips;
            currentSettings.showTooltips = showWorkspaceTooltips;
            SettingsManager.save(currentSettings);
            return true;
        }

        int overlayDividerY = chatDividerY + 22;
        int overlayRowCenterY = (chatDividerY + overlayDividerY) / 2;
        int overlayToggleX = gridToggleX;
        int overlayToggleY = overlayRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, overlayToggleX, overlayToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showChatErrors = !showChatErrors;
            currentSettings.showChatErrors = showChatErrors;
            SettingsManager.save(currentSettings);
            return true;
        }

        int hudDividerY = overlayDividerY + 22;
        int hudRowCenterY = (overlayDividerY + hudDividerY) / 2;
        int hudToggleX = gridToggleX;
        int hudToggleY = hudRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, hudToggleX, hudToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showHudOverlays = !showHudOverlays;
            currentSettings.showHudOverlays = showHudOverlays;
            SettingsManager.save(currentSettings);
            return true;
        }

        int profilerDividerY = hudDividerY + 22;
        int profilerRowCenterY = (hudDividerY + profilerDividerY) / 2;
        int profilerToggleX = gridToggleX;
        int profilerToggleY = profilerRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, profilerToggleX, profilerToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            currentSettings.showProfilerOverlay = !Boolean.TRUE.equals(currentSettings.showProfilerOverlay);
            SettingsManager.save(currentSettings);
            return true;
        }

        int delayDividerY = profilerDividerY + 26;
        int delayRowCenterY = (profilerDividerY + delayDividerY) / 2;
        int sliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = delayRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
        String delayText = nodeDelayField != null ? nodeDelayField.getValue() : Integer.toString(nodeDelayMs);
        int[] valueBox = getNodeDelayFieldBounds(popupX, popupWidth, delayRowCenterY, delayText);
        int valueBoxX = valueBox[0];
        int valueBoxY = valueBox[1];
        int valueBoxWidth = valueBox[2];
        int valueBoxHeight = valueBox[3];
        if (nodeDelayField != null) {
            if (bodyHovered && isPointInRect(mouseXi, mouseYi, valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight)) {
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
        if (bodyHovered && isPointInRect(mouseXi, mouseYi, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
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
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        int[] cacheRecipesButtonBounds = getSettingsCacheRecipesButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
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
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        if (isPointInRect(mouseXi, mouseYi, restoreExamplesButtonBounds[0], restoreExamplesButtonBounds[1],
            restoreExamplesButtonBounds[2], restoreExamplesButtonBounds[3])) {
            restoreExamplePresets();
            return true;
        }
        int[] replayTutorialButtonBounds = getSettingsReplayTutorialButtonBounds(
            popupX, popupY, popupWidth, popupHeight, contentX, nodeSettingsContentY);
        if (isPointInRect(mouseXi, mouseYi, replayTutorialButtonBounds[0], replayTutorialButtonBounds[1],
            replayTutorialButtonBounds[2], replayTutorialButtonBounds[3])) {
            replayFirstRunTutorial();
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
                int createListSliderX = popupX + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
                int createListSliderY = createListRadiusRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
                String radiusText = createListRadiusField != null ? createListRadiusField.getValue() : Integer.toString(getCreateListSettingsRadius(targetNode));
                int[] radiusValueBox = getCreateListRadiusFieldBounds(popupX, popupWidth, createListRadiusRowCenterY, radiusText);
                if (createListRadiusField != null) {
                    if (bodyHovered && isPointInRect(mouseXi, mouseYi, radiusValueBox[0], radiusValueBox[1], radiusValueBox[2], radiusValueBox[3])) {
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
                if (isPointInRect(mouseXi, mouseYi, createListSliderX, createListSliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
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
        if (isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeSettingsPopup();
            return true;
        }

        return true;
    }

    private void startExecutingAllGraphs() {
        validationExecutionController.closePanel();
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
        presetDropdownController.close();
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
            DropdownLayoutHelper.getRevealBottom(dropdownY, fullOptionsHeight, animProgress, 1),
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

    String getModVersion() {
        return LoaderMetadata.getModVersion(PathmindCommon.MOD_ID);
    }

    private String getLoaderVersion() {
        return LoaderMetadata.getLoaderVersion();
    }

    String getCurrentMinecraftVersion() {
        return this.minecraft != null ? this.minecraft.getLaunchedVersion() : "Unknown";
    }

    String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }

}
