package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.overlay.BookTextEditorOverlay;
import com.pathmind.ui.overlay.NodeParameterOverlay;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.UiUtilsDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.VersionSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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
    private static final boolean IS_MAC_OS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("mac");
    
    // Colors now come from UITheme for consistency
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int PRESET_DROPDOWN_WIDTH = 160;
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
    private static final int ZOOM_BUTTON_SIZE = 14;
    private static final int ZOOM_BUTTON_MARGIN = 6;
    private static final int ZOOM_BUTTON_SPACING = 4;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    private static final int PRESET_DELETE_POPUP_WIDTH = 320;
    private static final int PRESET_DELETE_POPUP_HEIGHT = 160;
    private static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    private static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    private static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    private static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;
    private static final String UI_UTILS_DOWNLOAD_URL = "https://ui-utils.com";
    private static final int SETTINGS_POPUP_WIDTH = 360;
    private static final int SETTINGS_POPUP_HEIGHT = 324;
    private static final int SETTINGS_OPTION_WIDTH = 90;
    private static final int SETTINGS_OPTION_HEIGHT = 16;
    private static final int SETTINGS_OPTION_GAP = 6;
    private static final int SETTINGS_TOGGLE_WIDTH = 60;
    private static final int SETTINGS_TOGGLE_HEIGHT = 16;
    private static final int SETTINGS_SLIDER_WIDTH = 160;
    private static final int SETTINGS_SLIDER_HEIGHT = 6;
    private static final int SETTINGS_SLIDER_HANDLE_WIDTH = 8;
    private static final int SETTINGS_SLIDER_HANDLE_HEIGHT = 12;
    private static final int NODE_DELAY_MIN_MS = 0;
    private static final int NODE_DELAY_MAX_MS = 500;
    private static final int TITLE_INTERACTION_PADDING = 4;
    private static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final String INFO_POPUP_AUTHOR = "soyboy";
    private static final String INFO_POPUP_TARGET_VERSION = VersionSupport.SUPPORTED_RANGE;
    private static final Text TITLE_TEXT = Text.translatable("pathmind.title");

    private NodeGraph nodeGraph;
    private Sidebar sidebar;
    private NodeParameterOverlay parameterOverlay;
    private BookTextEditorOverlay bookTextEditorOverlay;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;

    // Drag and drop state
    private boolean isDraggingFromSidebar = false;
    private NodeType draggingNodeType = null;

    // Right-click context menu state
    private static final int CLICK_THRESHOLD = 5;  // pixels
    private static final long CLICK_TIME_THRESHOLD = 250;  // milliseconds
    private int rightClickStartX = -1;
    private int rightClickStartY = -1;
    private long rightClickStartTime = 0;

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
    private String activePresetName = "";
    private final PopupAnimationHandler createPresetPopupAnimation = new PopupAnimationHandler();
    private TextFieldWidget createPresetField;
    private String createPresetStatus = "";
    private int createPresetStatusColor = UITheme.TEXT_SECONDARY;
    private final PopupAnimationHandler renamePresetPopupAnimation = new PopupAnimationHandler();
    private TextFieldWidget renamePresetField;
    private String renamePresetStatus = "";
    private int renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    private String pendingPresetRenameName = "";
    private final PopupAnimationHandler infoPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler presetDeletePopupAnimation = new PopupAnimationHandler();
    private String pendingPresetDeletionName = "";
    private final PopupAnimationHandler missingBaritonePopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingUiUtilsPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler settingsPopupAnimation = new PopupAnimationHandler();
    private Settings currentSettings;
    private static final String[] SUPPORTED_LANGUAGES = {"en_us", "es_es", "pt_br", "ru_ru", "de_de", "fr_fr", "pl_pl"};
    private boolean languageDropdownOpen = false;
    private final AnimatedValue languageDropdownAnimation = AnimatedValue.forHover();
    private boolean showGrid = true;
    private boolean showWorkspaceTooltips = true;
    private boolean showChatErrors = true;
    private int nodeDelayMs = 150;
    private boolean nodeDelayDragging = false;
    private AccentOption accentOption = AccentOption.SKY;
    private boolean overlayCutoutActive = false;
    private int overlayCutoutX = 0;
    private int overlayCutoutY = 0;
    private int overlayCutoutWidth = 0;
    private int overlayCutoutHeight = 0;
    private Boolean uiUtilsOverlayPrevEnabled = null;

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
        this.uiUtilsAvailable = UiUtilsDependencyChecker.isUiUtilsPresent();
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

        updateImportExportPathFromPreset();

        // Try to load saved node graph first
        if (nodeGraph.hasSavedGraph()) {
            System.out.println("Found saved node graph, loading...");
            if (nodeGraph.load()) {
                System.out.println("Successfully loaded saved node graph");
                refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
                return; // Don't initialize default nodes if we loaded a saved graph
            } else {
                System.out.println("Failed to load saved node graph, using default");
            }
        }
        
        // Initialize node graph with proper centering based on screen dimensions
        nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        OverlayProtection.setPathmindRendering(true);
        try {
        resetOverlayCutout();
        // Fill background with dark grey theme
        context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);
        
        boolean titleHovered = isTitleHovered(mouseX, mouseY);
        boolean titleActive = titleHovered || infoPopupAnimation.isVisible();
        titleUnderlineAnimation.animateTo(titleActive ? 1f : 0f, UITheme.HOVER_ANIM_MS);
        titleUnderlineAnimation.tick();

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
        context.fill(0, 0, this.width, TITLE_BAR_HEIGHT, UITheme.BACKGROUND_SECONDARY);
        context.drawHorizontalLine(0, this.width, TITLE_BAR_HEIGHT - 1, UITheme.BORDER_SUBTLE);
        drawTitle(context, titleUnderlineAnimation.getValue());

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

        boolean controlsDisabled = isPopupObscuringWorkspace();
        renderZoomControls(context, mouseX, mouseY, controlsDisabled);

        if (shouldShowExecutionControls()) {
            renderStopButton(context, mouseX, mouseY, controlsDisabled);
            renderPlayButton(context, mouseX, mouseY, controlsDisabled);
        }
        renderPresetDropdown(context, mouseX, mouseY, controlsDisabled);
        renderSettingsButton(context, mouseX, mouseY, controlsDisabled);

        if (controlsDisabled) {
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

        if (!isScreenPopupVisible()) {
            setOverlayCutoutForNodeOverlay();
        }

        renderPopupScrimOverlay(context);
        // Controls are already rendered before overlays so they appear dimmed underneath
        DrawContextBridge.startNewRootLayer(context);
        renderNodeGraph(context, mouseX, mouseY, delta, true);
        nodeGraph.renderSelectionBox(context);
        if (isDraggingFromSidebar && draggingNodeType != null) {
            renderDraggingNode(context, mouseX, mouseY);
        }

        // Render context menu on top of everything
        nodeGraph.updateNodeContextMenuHover(mouseX, mouseY);
        nodeGraph.renderNodeContextMenu(context, this.textRenderer);
        nodeGraph.updateContextMenuHover(mouseX, mouseY);
        nodeGraph.renderContextMenu(context, this.textRenderer, mouseX, mouseY);
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
        if (draggingNodeType == null) return;

        float scale = nodeGraph.getZoomScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        // Create a temporary node for rendering
        Node tempNode = new Node(draggingNodeType, 0, 0);
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
        int nodeColor = (draggingNodeType.getColor() & 0x00FFFFFF) | alpha;

        // Node background with transparency
        context.fill(x, y, x + width, y + height, 0x802A2A2A);
        // Draw grey outline for dragging state
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, 0xFFAAAAAA);

        // Node header
        if (draggingNodeType != NodeType.START && draggingNodeType != NodeType.EVENT_FUNCTION) {
            context.fill(x + 1, y + 1, x + width - 1, y + 14, nodeColor);
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(draggingNodeType.getDisplayName()),
                x + 4,
                y + 4,
                0xFFFFFFFF
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
            context.drawVerticalLine(screenX, startY, endY, 0x40333333);
        }

        int firstHorizontal = topWorld - Math.floorMod(topWorld, gridSize);
        for (int worldY = firstHorizontal; worldY <= bottomWorld + gridSize; worldY += gridSize) {
            int screenY = nodeGraph.worldToScreenY(worldY);
            if (screenY < startY || screenY > endY) {
                continue;
            }
            context.drawHorizontalLine(startX, endX, screenY, 0x40333333);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
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
            if (createPresetField != null && createPresetField.mouseClicked(click, inBounds)) {
                return true;
            }
            if (handleCreatePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null && renamePresetField.mouseClicked(click, inBounds)) {
                return true;
            }
            if (handleRenamePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
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
            if (isSettingsButtonClicked((int) mouseX, (int) mouseY, button)) {
                openSettingsPopup();
                return true;
            }
        }

        if (button == 0) {
            if (isPointInRect((int)mouseX, (int)mouseY, getPresetDropdownX(), getPresetDropdownY(), PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT)) {
                presetDropdownOpen = !presetDropdownOpen;
                if (presetDropdownOpen) {
                    presetDropdownScrollOffset = 0;
                }
                return true;
            }

            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                openInfoPopup();
                return true;
            }

            if (presetDropdownOpen && handlePresetDropdownSelection(mouseX, mouseY)) {
                return true;
            }
        }

        if (presetDropdownOpen && !isPointInRect((int)mouseX, (int)mouseY, getPresetDropdownX(), getPresetDropdownY(), PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT + getPresetDropdownOptionsHeight())) {
            presetDropdownOpen = false;
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

        // Handle book text editor overlay clicks
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            if (bookTextEditorOverlay.handleMouseClick(mouseX, mouseY, button)) {
                return true;
            }
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
                    if (shouldBlockBaritoneNode(hoveredType)) {
                        return true;
                    }
                    if (shouldBlockUiUtilsNode(hoveredType)) {
                        return true;
                    }
                    isDraggingFromSidebar = true;
                    draggingNodeType = hoveredType;
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
                NodeType clickedNode = nodeGraph.handleContextMenuClick((int)mouseX, (int)mouseY);
                if (clickedNode != null) {
                    // Create node at the stored right-click position
                    nodeGraph.addNodeFromContextMenu(clickedNode);
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
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                }
                return true;
            }
            
            return handleNodeGraphClick(mouseX, mouseY, button);
        }
        
        return super.mouseClicked(click, inBounds);
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

                if (nodeGraph.handleModeFieldClick(clickedNode, (int)mouseX, (int)mouseY)) {
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

                // Check for double-click to open parameter editor
                boolean shouldOpenOverlay = clickedNode.getType() == NodeType.PARAM_INVENTORY_SLOT
                    || clickedNode.getType() == NodeType.PARAM_KEY
                    || clickedNode.getType() == NodeType.PARAM_VILLAGER_TRADE;
                if (shouldOpenOverlay &&
                    nodeGraph.handleNodeClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    openParameterOverlay(clickedNode);
                    return true;
                }
                
                if (InputCompatibilityBridge.hasShiftDown()) {
                    // Shift-click: toggle node in selection
                    nodeGraph.toggleNodeInSelection(clickedNode);
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
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
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
            if (nodeDelayDragging) {
                updateNodeDelayFromMouse((int) mouseX, SETTINGS_POPUP_WIDTH);
            }
            return true;
        }
        if (createPresetPopupAnimation.isVisible()) {
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
            if (draggingNodeType != null && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                nodeGraph.previewSidebarDrag(draggingNodeType, worldMouseX, worldMouseY);
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
    public boolean mouseReleased(Click click) {
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

        if (button == 0) {
            // Handle dropping node from sidebar
            if (isDraggingFromSidebar) {
                if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                    int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                    int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                    Node newNode = nodeGraph.handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
                    if (newNode != null) {
                        nodeGraph.selectNode(newNode);
                    }
                }
                // Reset drag state
                isDraggingFromSidebar = false;
                draggingNodeType = null;
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
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
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
        
        return super.keyPressed(input);
    }
    
    @Override
    public boolean charTyped(CharInput input) {
        int modifiers = input.modifiers();
        char chr = (char) input.codepoint();
        if (settingsPopupAnimation.isVisible()) {
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

        return super.charTyped(input);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsPopupAnimation.isVisible()) {
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
            int optionStartY = getPresetDropdownY() + PRESET_DROPDOWN_HEIGHT;
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

        if (nodeGraph.save()) {
            System.out.println("Node graph auto-saved successfully");
        } else {
            System.err.println("Failed to auto-save node graph");
        }

        PresetManager.setActivePreset(activePresetName);
    }

    @Override
    public void close() {
        autoSaveWorkspace();
        super.close();
    }

    @Override
    public void removed() {
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.BACKGROUND_SECONDARY));
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.BORDER_SUBTLE));

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Clear workspace?"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(clearPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        drawPopupTextWithEllipsis(
            context,
            "This will remove all nodes from the workspace.",
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.BACKGROUND_SECONDARY));
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.BORDER_SUBTLE));

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Import / Export Workspace"),
            popupX + scaledWidth / 2,
            popupY + 14,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int infoY = popupY + 44;
        String importInfo = "Click Import to load a saved workspace.";
        drawPopupTextWithEllipsis(context, importInfo, popupX + 20, infoY, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        String exportInfo = "Click Export to choose where to save the current workspace.";
        drawPopupTextWithEllipsis(context, exportInfo, popupX + 20, infoY + 14, scaledWidth - 40,
            getPopupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        Path defaultPath = NodeGraphPersistence.getDefaultSavePath();
        if (defaultPath != null) {
            String defaultLabel = "Default save: " + defaultPath.toString();
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            TITLE_TEXT,
            popupX + scaledWidth / 2,
            popupY + 14,
            UITheme.TEXT_PRIMARY
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
        drawPopupCenteredTextWithEllipsis(context, authorLine, centerX, textStartY, maxCenteredWidth, UITheme.TEXT_SECONDARY);
        drawPopupCenteredTextWithEllipsis(context, targetLine, centerX, textStartY + lineSpacing, maxCenteredWidth, UITheme.TEXT_SECONDARY);
        drawPopupCenteredTextWithEllipsis(context, currentLine, centerX, textStartY + lineSpacing * 2, maxCenteredWidth, UITheme.TEXT_SECONDARY);
        drawPopupCenteredTextWithEllipsis(context, buildLine, centerX, textStartY + lineSpacing * 3, maxCenteredWidth, UITheme.TEXT_SECONDARY);
        drawPopupCenteredTextWithEllipsis(context, loaderLine, centerX, textStartY + lineSpacing * 4, maxCenteredWidth, UITheme.TEXT_SECONDARY);

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = popupX + (scaledWidth - buttonWidth) / 2;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        boolean closeHovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, buttonX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.translatable("pathmind.button.close"), false);
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        int centerX = popupX + scaledWidth / 2;
        int messageY = popupY + 16;
        int maxCenteredWidth = scaledWidth - 40;
        drawPopupCenteredTextWithEllipsis(context, "Baritone nodes need the Baritone API (optional)", centerX, messageY, maxCenteredWidth, UITheme.TEXT_PRIMARY);
        drawPopupCenteredTextWithEllipsis(context, "Install baritone-api to enable these nodes", centerX, messageY + 16, maxCenteredWidth, 0xFFD7D7D7);
        drawPopupCenteredTextWithEllipsis(context, BaritoneDependencyChecker.DOWNLOAD_URL, centerX, messageY + 30, maxCenteredWidth, 0xFF87CEEB);

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

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Text.translatable("pathmind.button.openLink"), PopupButtonStyle.PRIMARY);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Text.translatable("pathmind.button.copyLink"), PopupButtonStyle.PRIMARY);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.translatable("pathmind.button.close"), false);
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        int centerX = popupX + scaledWidth / 2;
        int messageY = popupY + 16;
        int maxCenteredWidth = scaledWidth - 40;
        drawPopupCenteredTextWithEllipsis(context, "UI Utils nodes need the UI-Utils mod (optional)", centerX, messageY, maxCenteredWidth, UITheme.TEXT_PRIMARY);
        drawPopupCenteredTextWithEllipsis(context, "Install UI-Utils to enable these nodes", centerX, messageY + 16, maxCenteredWidth, 0xFFD7D7D7);
        drawPopupCenteredTextWithEllipsis(context, UI_UTILS_DOWNLOAD_URL, centerX, messageY + 30, maxCenteredWidth, 0xFF87CEEB);

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

        drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered, Text.translatable("pathmind.button.openLink"), PopupButtonStyle.PRIMARY);
        drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered, Text.translatable("pathmind.button.copyLink"), PopupButtonStyle.PRIMARY);
        drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.translatable("pathmind.button.close"), false);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawTitle(DrawContext context, float underlineProgress) {
        int centerX = this.width / 2;
        int textY = (TITLE_BAR_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawCenteredTextWithShadow(this.textRenderer, TITLE_TEXT, centerX, textY, UITheme.TEXT_PRIMARY);

        if (underlineProgress > 0.001f) {
            int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
            int underlineWidth = Math.round(textWidth * underlineProgress);
            if (underlineWidth > 0) {
                int underlineStartX = centerX - underlineWidth / 2;
                int underlineY = textY + this.textRenderer.fontHeight;
                context.fill(underlineStartX, underlineY, underlineStartX + underlineWidth, underlineY + 1, UITheme.TEXT_PRIMARY);
            }
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
        int bgColor;
        int borderColor;
        switch (style) {
            case PRIMARY:
                bgColor = hovered
                        ? mixColor(getAccentColor(), 0xFFFFFFFF, 0.18f)
                        : mixColor(getAccentColor(), 0xFF000000, 0.25f);
                borderColor = getAccentColor();
                break;
            case ACCENT:
                bgColor = hovered
                        ? mixColor(getAccentColor(), 0xFFFFFFFF, 0.25f)
                        : getAccentColor();
                borderColor = getAccentColor();
                break;
            default:
                bgColor = hovered ? 0xFF505050 : 0xFF3A3A3A;
                borderColor = hovered ? UITheme.TEXT_TERTIARY : UITheme.BORDER_HIGHLIGHT;
                break;
        }
        int adjustedBg = getPopupAnimatedColor(animation, bgColor);
        int adjustedBorder = getPopupAnimatedColor(animation, borderColor);
        int adjustedText = getPopupAnimatedColor(animation, UITheme.TEXT_PRIMARY);
        context.fill(x, y, x + width, y + height, adjustedBg);
        DrawContextBridge.drawBorder(context, x, y, width, height, adjustedBorder);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            label,
            x + width / 2,
            y + (height - this.textRenderer.fontHeight) / 2 + 1,
            adjustedText
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
            boolean success = nodeGraph.importFromPath(path);
            if (success) {
                refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
                lastImportExportPath = path;
                Path fileName = path.getFileName();
                String fileLabel = fileName != null ? fileName.toString() : path.toString();
                Optional<String> importedPreset = PresetManager.importPresetFromFile(path);
                if (importedPreset.isPresent()) {
                    PresetManager.setActivePreset(importedPreset.get());
                    refreshAvailablePresets();
                    nodeGraph.setActivePreset(activePresetName);
                    updateImportExportPathFromPreset();
                    setImportExportStatus(
                            "Imported workspace \"" + fileLabel + "\" as preset \"" + importedPreset.get() + "\".",
                            UITheme.STATE_SUCCESS
                    );
                } else {
                    setImportExportStatus("Imported workspace from " + fileLabel + " but failed to create preset.", UITheme.STATE_ERROR);
                }
            } else {
                setImportExportStatus("Failed to import workspace from file.", UITheme.STATE_ERROR);
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

        nodeGraph.stopParameterEditing(true);
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

        int bgColor = executing ? 0xFF243224 : 0xFF2A2A2A;
        if (hovered) {
            bgColor = executing ? 0xFF2F4531 : 0xFF353535;
        } else if (disabled && !executing) {
            bgColor = 0xFF242424;
        }
        context.fill(buttonX + 1, buttonY + 1, buttonX + PLAY_BUTTON_SIZE - 1, buttonY + PLAY_BUTTON_SIZE - 1, bgColor);
        if (executing) {
            DrawContextBridge.drawBorder(context, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, UITheme.STATE_SUCCESS);
        }

        int iconColor = executing ? UITheme.STATE_SUCCESS : 0xFF4CAF50;
        if (hovered) {
            iconColor = 0xFF8BE97A;
        } else if (disabled && !executing) {
            iconColor = 0xFF4A7C4A;
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

        int bgColor = executing ? 0xFF8C1B1B : 0xFF2A2A2A;
        if (hovered) {
            bgColor = executing ? 0xFFA02525 : 0xFF353535;
        } else if (disabled && !executing) {
            bgColor = 0xFF242424;
        }
        context.fill(buttonX + 1, buttonY + 1, buttonX + STOP_BUTTON_SIZE - 1, buttonY + STOP_BUTTON_SIZE - 1, bgColor);
        if (executing) {
            int borderColor = hovered ? 0xFFFF6666 : 0xFFFF4C4C;
            DrawContextBridge.drawBorder(context, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE, borderColor);
        }

        int iconColor = executing ? 0xFFFF6F6F : 0xFFFFA6A6;
        if (hovered) {
            iconColor = executing ? 0xFFFF8A8A : UITheme.STATE_ERROR;
        } else if (disabled && !executing) {
            iconColor = 0xFFB35E5E;
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

        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, dropdownX, dropdownY, PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT);
        drawToolbarButtonFrame(
            context,
            dropdownX,
            dropdownY,
            PRESET_DROPDOWN_WIDTH,
            PRESET_DROPDOWN_HEIGHT,
            hovered,
            presetDropdownOpen,
            disabled,
            "preset-dropdown"
        );

        String displayName = activePresetName == null || activePresetName.isEmpty()
                ? PresetManager.getDefaultPresetName()
                : activePresetName;
        int activeTextX = dropdownX + PRESET_TEXT_LEFT_PADDING;
        int activeTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
        String trimmedName = TextRenderUtil.trimWithEllipsis(this.textRenderer, displayName, activeTextWidth);
        int labelColor = (hovered || presetDropdownOpen) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        context.drawTextWithShadow(this.textRenderer, Text.literal(trimmedName), activeTextX, dropdownY + 5, labelColor);

        int arrowCenterX = dropdownX + PRESET_DROPDOWN_WIDTH - 10;
        int arrowCenterY = dropdownY + PRESET_DROPDOWN_HEIGHT / 2;
        if (presetDropdownOpen) {
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY - 2, labelColor);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY - 1, labelColor);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY, labelColor);
        } else {
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY + 1, labelColor);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY, labelColor);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY - 1, labelColor);
        }

        // Don't render options if animation is fully closed
        if (animProgress <= 0.001f) {
            return;
        }

        int optionStartY = dropdownY + PRESET_DROPDOWN_HEIGHT;
        int optionCount = availablePresets.size() + 1;
        DropdownLayoutHelper.Layout layout = getPresetDropdownLayout(optionStartY);
        presetDropdownScrollOffset = MathHelper.clamp(presetDropdownScrollOffset, 0, layout.maxScrollOffset);
        int fullOptionsHeight = layout.height;
        int animatedHeight = (int) (fullOptionsHeight * animProgress);

        // Use scissor to clip the dropdown content during animation
        context.enableScissor(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + animatedHeight);

        context.fill(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + fullOptionsHeight, UITheme.BACKGROUND_SECONDARY);

        int startIndex = presetDropdownScrollOffset;
        int endIndex = Math.min(optionCount, startIndex + layout.visibleCount);
        for (int index = startIndex; index < endIndex; index++) {
            int optionY = optionStartY + (index - startIndex) * PRESET_OPTION_HEIGHT;
            if (index < availablePresets.size()) {
                String preset = availablePresets.get(index);
                boolean optionHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                int optionColor = optionHovered ? 0xFF3F3F3F : 0xFF2B2B2B;
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
                            0x33555555);
                }

                int renameColor;
                if (renameDisabled) {
                    renameColor = 0xFF555555;
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
                            0x33555555);
                }

                int deleteColor;
                if (deleteDisabled) {
                    deleteColor = 0xFF555555;
                } else if (deleteHovered) {
                    deleteColor = getAccentColor();
                } else {
                    deleteColor = UITheme.TEXT_SECONDARY;
                }
                drawTrashIcon(context, deleteLeft, deleteTop, deleteColor);
            } else {
                context.drawHorizontalLine(dropdownX + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 2, optionY, UITheme.BORDER_SUBTLE);
                boolean createHovered = animProgress >= 1f && isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                int createColor = createHovered ? 0xFF3F3F3F : 0xFF2B2B2B;
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


        context.disableScissor();
    }

    private int getPresetDropdownX() {
        if (shouldShowExecutionControls()) {
            return getStopButtonX() - PRESET_DROPDOWN_MARGIN - PRESET_DROPDOWN_WIDTH;
        }
        return this.width - PRESET_DROPDOWN_MARGIN - PRESET_DROPDOWN_WIDTH;
    }

    private int getPresetDropdownY() {
        return TITLE_BAR_HEIGHT + PRESET_DROPDOWN_MARGIN;
    }

    private int getPlayButtonX() {
        return this.width - PLAY_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    private int getPlayButtonY() {
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
        return DropdownLayoutHelper.calculate(optionCount, PRESET_OPTION_HEIGHT, optionCount, optionStartY, this.height);
    }

    private int getPresetDropdownOptionsHeight() {
        int optionStartY = getPresetDropdownY() + PRESET_DROPDOWN_HEIGHT;
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
        int optionStartY = getPresetDropdownY() + PRESET_DROPDOWN_HEIGHT;
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

        if (isPointInRect(mouseXi, mouseYi, deleteX, buttonY, buttonWidth, buttonHeight)) {
            confirmPresetDeletion();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closePresetDeletePopup();
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Create workspace preset"),
            popupX + scaledWidth / 2,
            popupY + 14,
            UITheme.TEXT_PRIMARY
        );

        drawPopupTextWithEllipsis(
            context,
            "Enter a name for the new preset.",
            popupX + 20,
            popupY + 44,
            scaledWidth - 40,
            UITheme.TEXT_SECONDARY
        );

        int fieldX = popupX + 20;
        int fieldY = popupY + 70;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;

        boolean fieldHovered = isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, 0xFF1F1F1F);
        boolean focused = createPresetField != null && createPresetField.isFocused();
        int borderColor;
        if (focused) {
            borderColor = getAccentColor();
        } else if (fieldHovered) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
        } else {
            borderColor = 0xFF000000;
        }
        DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

        if (createPresetField != null) {
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            int textFieldHeight = Math.max(10, fieldHeight - TEXT_FIELD_VERTICAL_PADDING * 2);
            createPresetField.setPosition(fieldX + 4, fieldY + TEXT_FIELD_VERTICAL_PADDING);
            createPresetField.setWidth(fieldWidth - 8);
            createPresetField.setHeight(textFieldHeight);
            createPresetField.render(context, mouseX, mouseY, delta);
        }

        if (!createPresetStatus.isEmpty()) {
            drawPopupTextWithEllipsis(context, createPresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth, createPresetStatusColor);
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int createX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean createHovered = isPointInRect(mouseX, mouseY, createX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.translatable("pathmind.button.cancel"), false);
        drawPopupButton(context, createX, buttonY, buttonWidth, buttonHeight, createHovered, Text.translatable("pathmind.button.create"), true);
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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Rename workspace preset"),
                popupX + scaledWidth / 2,
                popupY + 14,
                UITheme.TEXT_PRIMARY
        );

        String presetLabel = pendingPresetRenameName == null || pendingPresetRenameName.isEmpty()
                ? "the selected preset"
                : "Preset: " + pendingPresetRenameName;
        drawPopupTextWithEllipsis(context, "Enter a new name.", popupX + 20, popupY + 44, scaledWidth - 40, UITheme.TEXT_SECONDARY);
        drawPopupTextWithEllipsis(context, presetLabel, popupX + 20, popupY + 58, scaledWidth - 40, UITheme.TEXT_SECONDARY);

        int fieldX = popupX + 20;
        int fieldY = popupY + 80;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;

        boolean fieldHovered = isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, 0xFF1F1F1F);
        boolean focused = renamePresetField != null && renamePresetField.isFocused();
        int borderColor;
        if (focused) {
            borderColor = getAccentColor();
        } else if (fieldHovered) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
        } else {
            borderColor = 0xFF000000;
        }
        DrawContextBridge.drawBorder(context, fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

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

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Delete preset?"),
            popupX + scaledWidth / 2,
            popupY + 14,
            UITheme.TEXT_PRIMARY
        );

        String presetLabel = (pendingPresetDeletionName != null && !pendingPresetDeletionName.isEmpty())
                ? pendingPresetDeletionName
                : "this preset";
        String warningLine = "This will permanently remove the preset.";
        String presetLine = "Preset: " + presetLabel;
        drawPopupTextWithEllipsis(context, warningLine, popupX + 20, popupY + 48, scaledWidth - 40, UITheme.TEXT_SECONDARY);
        drawPopupTextWithEllipsis(context, presetLine, popupX + 20, popupY + 64, scaledWidth - 40, UITheme.TEXT_SECONDARY);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int deleteX = popupX + scaledWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean deleteHovered = isPointInRect(mouseX, mouseY, deleteX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.translatable("pathmind.button.cancel"), false);
        drawPopupButton(context, deleteX, buttonY, buttonWidth, buttonHeight, deleteHovered, Text.translatable("pathmind.button.delete"), true);
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

        boolean renamingActive = pendingPresetRenameName.equalsIgnoreCase(activePresetName);
        if (renamingActive) {
            nodeGraph.save();
        }

        Optional<String> renamedPreset = PresetManager.renamePreset(pendingPresetRenameName, desiredName);
        if (renamedPreset.isEmpty()) {
            setRenamePresetStatus("Preset name already exists or is invalid.", UITheme.STATE_ERROR);
            return;
        }

        closeRenamePresetPopup();
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        presetDropdownOpen = false;

        if (renamingActive) {
            updateImportExportPathFromPreset();
        }
    }

    private void openPresetDeletePopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
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

    private void attemptDeletePreset(String presetName) {
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
            clearPopupAnimation.hide();
            clearImportExportStatus();

            if (!nodeGraph.load()) {
                nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
            }
            refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
            nodeGraph.resetCamera();
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
        availablePresets = new ArrayList<>(PresetManager.getAvailablePresets());
        activePresetName = PresetManager.getActivePreset();
    }

    private void updateImportExportPathFromPreset() {
        lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
    }

    private void switchPreset(String presetName) {
        nodeGraph.save();
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
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
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
        nodeGraph.resetCamera();
        updateImportExportPathFromPreset();
    }

    private void renderWorkspaceButtons(DrawContext context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int buttonY = getWorkspaceButtonY();
        boolean importHovered = renderImportExportButton(context, mouseX, mouseY, buttonY);
        boolean clearHovered = renderClearButton(context, mouseX, mouseY, buttonY);
        boolean homeHovered = renderHomeButton(context, mouseX, mouseY, buttonY);

        if (showWorkspaceTooltips && !isPopupObscuringWorkspace()) {
            if (homeHovered) {
                drawWorkspaceTooltip(context, "Reset view", mouseX, mouseY);
            } else if (clearHovered) {
                drawWorkspaceTooltip(context, "Clear workspace", mouseX, mouseY);
            } else if (importHovered) {
                drawWorkspaceTooltip(context, "Import / Export", mouseX, mouseY);
            }
        }
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
        context.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFF2A2A2A);
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
    }

    private void renderSettingsPopup(DrawContext context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, settingsPopupAnimation.getPopupAlpha());

        int popupWidth = SETTINGS_POPUP_WIDTH;
        int popupHeight = SETTINGS_POPUP_HEIGHT;
        int[] bounds = settingsPopupAnimation.getScaledPopupBounds(this.width, this.height, popupWidth, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        context.fill(popupX, popupY, popupX + scaledWidth, popupY + scaledHeight, UITheme.BACKGROUND_SECONDARY);
        DrawContextBridge.drawBorder(context, popupX, popupY, scaledWidth, scaledHeight, UITheme.BORDER_SUBTLE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("pathmind.settings.title"),
            popupX + scaledWidth / 2,
            popupY + 14,
            UITheme.TEXT_PRIMARY
        );

        int contentX = popupX + 20;
        int accentLabelY = popupY + 44;
        drawPopupTextWithEllipsis(context, "GUI accent", contentX, accentLabelY, scaledWidth - 40,
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
        renderToggleRow(context, mouseX, mouseY, contentX, gridRowCenterY, "Show grid", showGrid, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, settingDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int footerDividerY = settingDividerY + 22;
        int tooltipRowCenterY = (settingDividerY + footerDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, tooltipRowCenterY, "Show tooltips", showWorkspaceTooltips, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, footerDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        renderToggleRow(context, mouseX, mouseY, contentX, chatRowCenterY,
            Text.translatable("pathmind.settings.showChatErrors").getString(), showChatErrors, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, chatDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int delayDividerY = chatDividerY + 26;
        int delayRowCenterY = (chatDividerY + delayDividerY) / 2;
        String delayLabel = Text.translatable("pathmind.settings.nodeDelay").getString() + " (" + nodeDelayMs + "ms)";
        renderSliderRow(context, mouseX, mouseY, contentX, delayRowCenterY, delayLabel, nodeDelayMs, NODE_DELAY_MIN_MS, NODE_DELAY_MAX_MS, scaledWidth);
        context.drawHorizontalLine(sectionDividerX, popupX + scaledWidth - 16, delayDividerY,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_SUBTLE));

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = popupX + scaledWidth - buttonWidth - 20;
        int buttonY = popupY + scaledHeight - buttonHeight - 16;
        boolean closeHovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        drawPopupButton(context, buttonX, buttonY, buttonWidth, buttonHeight, closeHovered,
            Text.translatable("pathmind.button.close"), PopupButtonStyle.DEFAULT, settingsPopupAnimation);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawAccentOption(DrawContext context, int x, int y, AccentOption option, boolean hovered, boolean selected) {
        int bgColor = selected ? 0xFF3F3F3F : 0xFF2B2B2B;
        if (hovered) {
            bgColor = selected ? 0xFF4A4A4A : 0xFF343434;
        }
        context.fill(x, y, x + SETTINGS_OPTION_WIDTH, y + SETTINGS_OPTION_HEIGHT,
            getPopupAnimatedColor(settingsPopupAnimation, bgColor));
        int borderColor = selected ? getAccentColor() : UITheme.BORDER_SUBTLE;
        if (hovered) {
            borderColor = getAccentColor();
        }
        DrawContextBridge.drawBorder(context, x, y, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT,
            getPopupAnimatedColor(settingsPopupAnimation, borderColor));

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

    private void renderToggleRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label, boolean active, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int toggleX = getSettingsPopupX() + scaledWidth - SETTINGS_TOGGLE_WIDTH - 20;
        int toggleY = centerY - SETTINGS_TOGGLE_HEIGHT / 2;
        int maxLabelWidth = Math.max(0, toggleX - labelX - 8);
        drawPopupTextWithEllipsis(context, label, labelX, labelY, maxLabelWidth,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.TEXT_SECONDARY));

        boolean hovered = isPointInRect(mouseX, mouseY, toggleX, toggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT);
        PopupButtonStyle style = active ? PopupButtonStyle.PRIMARY : PopupButtonStyle.DEFAULT;
        String toggleLabel = active ? "On" : "Off";
        drawPopupButton(context, toggleX, toggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT, hovered,
            Text.literal(toggleLabel), style, settingsPopupAnimation);
    }

    private void renderSliderRow(DrawContext context, int mouseX, int mouseY, int labelX, int centerY, String label,
                                 int value, int min, int max, int scaledWidth) {
        int labelY = centerY - this.textRenderer.fontHeight / 2;
        int sliderX = getSettingsPopupX() + scaledWidth - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = centerY - SETTINGS_SLIDER_HEIGHT / 2;
        int maxLabelWidth = Math.max(0, sliderX - labelX - 8);
        drawPopupTextWithEllipsis(context, label, labelX, labelY, maxLabelWidth,
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

    private void updateNodeDelayFromMouse(int mouseX, int popupWidth) {
        int sliderX = getSettingsPopupX() + popupWidth - SETTINGS_SLIDER_WIDTH - 20;
        int localX = MathHelper.clamp(mouseX - sliderX, 0, SETTINGS_SLIDER_WIDTH);
        float t = SETTINGS_SLIDER_WIDTH <= 0 ? 0f : localX / (float) SETTINGS_SLIDER_WIDTH;
        int value = NODE_DELAY_MIN_MS + Math.round(t * (NODE_DELAY_MAX_MS - NODE_DELAY_MIN_MS));
        if (value != nodeDelayMs) {
            nodeDelayMs = value;
            currentSettings.nodeDelayMs = nodeDelayMs;
            SettingsManager.save(currentSettings);
        }
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
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, bgColor);

        int borderColor = disabled
            ? UITheme.BORDER_SUBTLE
            : getAnimatedBorderColor(hoverKey, hovered || active, UITheme.BORDER_DEFAULT, getAccentColor());
        DrawContextBridge.drawBorder(context, x, y, width, height, borderColor);
    }

    private int getWorkspaceButtonY() {
        return TITLE_BAR_HEIGHT + BOTTOM_BUTTON_MARGIN;
    }

    private int getSidebarVisibleWidth() {
        return sidebar != null ? sidebar.getWidth() : Sidebar.getCollapsedWidth();
    }

    private int getHomeButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN + (BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING) * 2;
    }

    private int getClearButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN + BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING;
    }

    private int getImportExportButtonX() {
        return getSidebarVisibleWidth() + BOTTOM_BUTTON_MARGIN;
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
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isClearButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getClearButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isImportExportButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getImportExportButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isSettingsButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getSettingsButtonX();
        int buttonY = getSettingsButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
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

    private int getSettingsPopupY() {
        return (this.height - SETTINGS_POPUP_HEIGHT) / 2;
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
        settingsPopupAnimation.show();
    }

    private void closeSettingsPopup() {
        nodeDelayDragging = false;
        settingsPopupAnimation.hide();
    }

    private boolean handleSettingsPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupX = getSettingsPopupX();
        int popupY = getSettingsPopupY();
        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, SETTINGS_POPUP_WIDTH, SETTINGS_POPUP_HEIGHT)) {
            closeSettingsPopup();
            return true;
        }

        int contentX = popupX + 20;
        int accentLabelY = popupY + 44;
        int accentOptionsY = accentLabelY + 12;
        int optionIndex = 0;
        for (AccentOption option : AccentOption.values()) {
            int optionX = contentX + optionIndex * (SETTINGS_OPTION_WIDTH + SETTINGS_OPTION_GAP);
            if (isPointInRect(mouseXi, mouseYi, optionX, accentOptionsY, SETTINGS_OPTION_WIDTH, SETTINGS_OPTION_HEIGHT)) {
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
        if (isPointInRect(mouseXi, mouseYi, gridToggleX, gridToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showGrid = !showGrid;
            currentSettings.showGrid = showGrid;
            SettingsManager.save(currentSettings);
            return true;
        }

        int footerDividerY = settingDividerY + 22;
        int tooltipRowCenterY = (settingDividerY + footerDividerY) / 2;
        int tooltipToggleX = gridToggleX;
        int tooltipToggleY = tooltipRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (isPointInRect(mouseXi, mouseYi, tooltipToggleX, tooltipToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showWorkspaceTooltips = !showWorkspaceTooltips;
            currentSettings.showTooltips = showWorkspaceTooltips;
            SettingsManager.save(currentSettings);
            return true;
        }

        int chatDividerY = footerDividerY + 22;
        int chatRowCenterY = (footerDividerY + chatDividerY) / 2;
        int chatToggleX = gridToggleX;
        int chatToggleY = chatRowCenterY - SETTINGS_TOGGLE_HEIGHT / 2;
        if (isPointInRect(mouseXi, mouseYi, chatToggleX, chatToggleY, SETTINGS_TOGGLE_WIDTH, SETTINGS_TOGGLE_HEIGHT)) {
            showChatErrors = !showChatErrors;
            currentSettings.showChatErrors = showChatErrors;
            SettingsManager.save(currentSettings);
            return true;
        }

        int delayDividerY = chatDividerY + 26;
        int delayRowCenterY = (chatDividerY + delayDividerY) / 2;
        int sliderX = popupX + SETTINGS_POPUP_WIDTH - SETTINGS_SLIDER_WIDTH - 20;
        int sliderY = delayRowCenterY - SETTINGS_SLIDER_HEIGHT / 2;
        if (isPointInRect(mouseXi, mouseYi, sliderX, sliderY - 4, SETTINGS_SLIDER_WIDTH, SETTINGS_SLIDER_HEIGHT + 8)) {
            nodeDelayDragging = true;
            updateNodeDelayFromMouse(mouseXi, SETTINGS_POPUP_WIDTH);
            return true;
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = popupX + SETTINGS_POPUP_WIDTH - buttonWidth - 20;
        int buttonY = popupY + SETTINGS_POPUP_HEIGHT - buttonHeight - 16;
        if (isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeSettingsPopup();
            return true;
        }

        return true;
    }

    private void startExecutingAllGraphs() {
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        ExecutionManager.getInstance().executeGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void stopExecutingAllGraphs() {
        ExecutionManager.getInstance().requestStopAll();
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

    private boolean isTitleClicked(int mouseX, int mouseY) {
        return isTitleHovered(mouseX, mouseY);
    }

    private boolean isTitleHovered(int mouseX, int mouseY) {
        int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
        int textHeight = this.textRenderer.fontHeight;
        int textX = this.width / 2 - textWidth / 2;
        int textY = (TITLE_BAR_HEIGHT - textHeight) / 2;
        int hitboxX = textX - TITLE_INTERACTION_PADDING;
        int hitboxY = textY - TITLE_INTERACTION_PADDING;
        int hitboxWidth = textWidth + TITLE_INTERACTION_PADDING * 2;
        int hitboxHeight = textHeight + TITLE_INTERACTION_PADDING * 2;
        return isPointInRect(mouseX, mouseY, hitboxX, hitboxY, hitboxWidth, hitboxHeight);
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

    private void drawLanguageDropdown(DrawContext context, int x, int y, int width, String currentLang, boolean hovered, int mouseX, int mouseY) {
        // Update dropdown animation
        languageDropdownAnimation.animateTo(languageDropdownOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        languageDropdownAnimation.tick();

        int bgColor;
        if (hovered) {
            bgColor = languageDropdownOpen ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG_HOVER;
        } else {
            bgColor = languageDropdownOpen ? UITheme.TOOLBAR_BG_ACTIVE : UITheme.TOOLBAR_BG;
        }
        bgColor = getPopupAnimatedColor(settingsPopupAnimation, bgColor);
        context.fill(x + 1, y + 1, x + width - 1, y + 19, bgColor);

        int borderColor = getAnimatedBorderColor("settings-language-dropdown", hovered || languageDropdownOpen, UITheme.BORDER_DEFAULT, getAccentColor());
        borderColor = getPopupAnimatedColor(settingsPopupAnimation, borderColor);
        DrawContextBridge.drawBorder(context, x, y, width, 20, borderColor);

        // Draw dropdown text
        int labelColor = (hovered || languageDropdownOpen) ? getAccentColor() : UITheme.TEXT_PRIMARY;
        labelColor = getPopupAnimatedColor(settingsPopupAnimation, labelColor);
        context.drawTextWithShadow(this.textRenderer, Text.literal(currentLang), x + 4, y + 6, labelColor);

        // Draw animated arrow
        int arrowCenterX = x + width - 10;
        int arrowCenterY = y + 10;
        if (languageDropdownOpen) {
            // Arrow pointing up
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY - 2, labelColor);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY - 1, labelColor);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY, labelColor);
        } else {
            // Arrow pointing down
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY + 1, labelColor);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY, labelColor);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY - 1, labelColor);
        }
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

        // Draw background for all options
        context.fill(x, dropdownY, x + width, dropdownY + fullOptionsHeight,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BACKGROUND_SECONDARY));

        // Draw each language option
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
            String lang = SUPPORTED_LANGUAGES[i];
            String langName = getLanguageDisplayName(lang);
            int optionY = dropdownY + (i * 20);

            // Only allow hover detection when animation is complete
            boolean optionHovered = animProgress >= 1f && mouseX >= x && mouseX <= x + width && mouseY >= optionY && mouseY <= optionY + 20;
            int optionBg = optionHovered ? UITheme.DROPDOWN_OPTION_HOVER : UITheme.DROPDOWN_OPTION_BG;
            optionBg = getPopupAnimatedColor(settingsPopupAnimation, optionBg);
            context.fill(x + 1, optionY + 1, x + width - 1, optionY + 20, optionBg);

            // Highlight current language with accent color
            String currentLanguage = this.client.getLanguageManager().getLanguage();
            int textColor = lang.equals(currentLanguage) ? getAccentColor() : UITheme.TEXT_PRIMARY;
            textColor = getPopupAnimatedColor(settingsPopupAnimation, textColor);
            context.drawTextWithShadow(this.textRenderer, Text.literal(langName), x + 4, optionY + 6, textColor);
        }

        DropdownLayoutHelper.drawOutline(
            context,
            x,
            dropdownY,
            width,
            fullOptionsHeight,
            getPopupAnimatedColor(settingsPopupAnimation, UITheme.BORDER_DEFAULT)
        );

        // Disable scissor
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

}
