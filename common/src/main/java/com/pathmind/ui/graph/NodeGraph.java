package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ParameterTypeClassifier.isAmountParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionAttributeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionBooleanValueParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockFaceParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBooleanLiteralParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isDirectionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isFabricEventSensorParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isGuiParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isHandParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isInlineDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isListIndexParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMessageParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMouseButtonParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerProfessionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeVariantParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isPlayerParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isSeedParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isTradeInlineParameter;
import static com.pathmind.ui.graph.ParameterDropdownOptions.getAttributeDetectionTargetKind;
import static com.pathmind.ui.graph.ParameterDropdownOptions.getParameterDropdownOptions;
import static com.pathmind.ui.graph.ParameterDropdownOptions.resolveParameterDropdownIcon;
import static com.pathmind.ui.graph.SchematicRepository.loadSchematicOptions;
import static com.pathmind.ui.graph.SchematicRepository.schematicExistsInRoots;
import static com.pathmind.ui.graph.InlineVariableRenderer.buildInlineVariableRender;
import static com.pathmind.ui.graph.InlineVariableRenderer.isSingleKnownInlineVariableReference;
import static com.pathmind.ui.graph.ConnectionRenderer.VIEWPORT_CULL_MARGIN;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.nodes.RelativeInputSupport;
import com.pathmind.nodes.RuntimeValueScope;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.nodes.StartScreenTarget;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.control.PathmindDropdownRenderer;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.UiUtilsProxy;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;
import com.pathmind.ui.graph.InlineVariableRenderer.InlineVariableRender;

/**
 * Manages the node graph for the Pathmind visual editor.
 * Handles node rendering, connections, and interactions.
 */
public class NodeGraph {
    private static final int DUPLICATE_OFFSET_X = 32;
    private static final int DUPLICATE_OFFSET_Y = 24;
    private static final int SELECTION_BOX_MIN_DRAG = 3;
    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int GRID_SNAP_SIZE = 20;
    private static final int TEMPLATE_PREVIEW_MARGIN = 6;
    private static final int NODE_HEADER_BUTTON_SIZE = 12;

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
    private static final int TEMPLATE_PREVIEW_TOP = 42;
    private static final int TEMPLATE_PREVIEW_BOTTOM_MARGIN = 6;
    private static final int STICKY_NOTE_MAX_CHARS = 4096;
    private static final int DENSE_VIEW_VISIBLE_NODE_THRESHOLD = 120;
    private static final int COMPACT_VIEW_VISIBLE_NODE_THRESHOLD = 40;
    private static final int PROFILER_OVERLAY_MARGIN = 10;
    private static final int PROFILER_OVERLAY_PADDING = 6;

    private final List<Node> nodes;
    private final List<NodeConnection> connections;
    private final List<Node> cachedRootNodes;
    private final List<Node> cachedVisibleRootNodes;
    private final Map<Node, SelectionBounds> cachedHierarchyBounds;
    private final Map<Node, Integer> cachedHierarchyNodeCounts;
    private final ConnectionController connectionController = new ConnectionController(new ConnectionController.Host() {
        @Override public List<Node> getNodes() { return nodes; }
        @Override public List<NodeConnection> getConnections() { return connections; }
        @Override public Node getNodeAtWorld(int worldX, int worldY) { return NodeGraph.this.getNodeAtWorld(worldX, worldY); }
        @Override public Node getNodeAtWorldExcluding(int worldX, int worldY, Node excludedNode) {
            return NodeGraph.this.getNodeAtWorldExcluding(worldX, worldY, excludedNode);
        }
        @Override public Node getParentForNode(Node node) { return NodeGraph.this.getParentForNode(node); }
        @Override public void stopConnectionEditors() { NodeGraph.this.stopConnectionEditors(); }
        @Override public void pushUndoState() { NodeGraph.this.pushUndoState(); }
        @Override public boolean isUndoCaptureSuppressed() { return suppressUndoCapture; }
        @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
        @Override public void invalidateRenderCaches() { NodeGraph.this.invalidateRenderCaches(); }
        @Override public void markDragOperationChanged() { dragOperationChanged = true; }
    });
    private final ConnectionRenderer connectionRenderer = new ConnectionRenderer(new ConnectionRenderer.Host() {
        @Override public List<NodeConnection> getConnections() { return connections; }
        @Override public List<Node> getVisibleRootsForViewport() { return NodeGraph.this.getVisibleRootsForViewport(); }
        @Override public int getViewportWorldWidth() { return NodeGraph.this.getViewportWorldWidth(); }
        @Override public int getViewportWorldHeight() { return NodeGraph.this.getViewportWorldHeight(); }
        @Override public int getCameraX() { return cameraX; }
        @Override public int getCameraY() { return cameraY; }
        @Override public boolean isDenseViewportMode() { return denseViewportMode; }
        @Override public boolean shouldRenderConnectionsOnTop() { return NodeGraph.this.shouldRenderConnectionsOnTop(); }
        @Override public Node getParentForNode(Node node) { return NodeGraph.this.getParentForNode(node); }
        @Override public boolean shouldConsiderConnectionForViewport(NodeConnection connection, Set<Node> visibleRoots,
                                                                     int viewportWidth, int viewportHeight) {
            return NodeGraph.this.shouldConsiderConnectionForViewport(connection, visibleRoots, viewportWidth, viewportHeight);
        }
        @Override public boolean isNodeOverSidebarForRender(Node node, int screenX, int screenWidth) {
            return NodeGraph.this.isNodeOverSidebarForRender(node, screenX, screenWidth);
        }
        @Override public int toGrayscale(int color, float brightnessFactor) {
            return nodeControls.toGrayscale(color, brightnessFactor);
        }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color) {
            NodeGraph.this.renderSocket(context, x, y, isInput, color);
        }
        @Override public void setProfilerConnectionMs(double profilerConnectionMs) {
            NodeGraph.this.profilerConnectionMs = profilerConnectionMs;
        }
    }, connectionController);
    private Node selectedNode;
    private final LinkedHashSet<Node> selectedNodes;
    private Node draggingNode;
    private int draggingNodeStartX;
    private int draggingNodeStartY;
    private boolean draggingNodeDetached;
    private NodeGraphData pendingDragUndoSnapshot = null;
    private boolean dragOperationChanged = false;
    
    // Camera/viewport for infinite scrolling
    private int cameraX = 0;
    private int cameraY = 0;
    private boolean isPanning = false;
    private double profilerRenderMs = 0.0;
    private double profilerNodeMs = 0.0;
    private double profilerConnectionMs = 0.0;
    private double profilerDropdownMs = 0.0;
    private double profilerHoverMs = 0.0;
    private double profilerHitTestAvgMs = 0.0;
    private double profilerHitTestAvgRoots = 0.0;
    private int profilerVisibleNodes = 0;
    private int profilerDrawnNodes = 0;
    private int profilerVisibleRoots = 0;
    private int profilerDrawnConnections = 0;
    private long profilerHitTestTotalNanos = 0L;
    private long profilerHitTestCallCount = 0L;
    private long profilerHitTestTotalRoots = 0L;
    private int panStartX, panStartY;
    private int panStartCameraX, panStartCameraY;
    
    // Start button hover state
    private boolean hoveringStartButton = false;
    private Node hoveredStartNode = null;
    private boolean lastStartButtonTriggeredExecution = false;
    private final StartModeDropdownController startModeDropdown = new StartModeDropdownController(new StartModeDropdownController.Host() {
        @Override public float getZoomScale() { return NodeGraph.this.getZoomScale(); }
        @Override public int worldToScreenX(int worldX) { return NodeGraph.this.worldToScreenX(worldX); }
        @Override public int worldToScreenY(int worldY) { return NodeGraph.this.worldToScreenY(worldY); }
        @Override public int getScaledScreenWidth() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.getWindow() != null ? client.getWindow().getGuiScaledWidth() : 0;
        }
        @Override public int getScaledScreenHeight() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.getWindow() != null ? client.getWindow().getGuiScaledHeight() : 0;
        }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public Node findStartModeButtonAt(int mouseX, int mouseY) { return NodeGraph.this.findStartModeButtonAt(mouseX, mouseY); }
        @Override public int getStartModeButtonWorldX(Node node) { return NodeGraph.this.getStartModeButtonWorldX(node); }
        @Override public int getStartModeButtonWorldY(Node node) { return NodeGraph.this.getStartModeButtonWorldY(node); }
        @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
        @Override public void closeContextMenu() { NodeGraph.this.closeContextMenu(); }
        @Override public void closeNodeContextMenu() { NodeGraph.this.closeNodeContextMenu(); }
    });

    private Node sensorDropTarget = null;
    private Node actionDropTarget = null;
    private Node parameterDropTarget = null;
    private Integer parameterDropSlotIndex = null;
    private final Map<Node, AnimatedValue> amountToggleAnimations = new WeakHashMap<>();
    private final Map<Node, AnimatedValue> randomRoundingToggleAnimations = new WeakHashMap<>();

    // Context menu state
    private com.pathmind.ui.menu.ContextMenu contextMenu = null;
    private com.pathmind.ui.menu.NodeContextMenu nodeContextMenu = null;
    private int contextMenuWorldX = 0;
    private int contextMenuWorldY = 0;
    private int nodeContextMenuWorldX = 0;
    private int nodeContextMenuWorldY = 0;
    private Node nodeContextMenuTarget = null;

    // Double-click detection
    private long lastClickTime = 0;
    private Node lastClickedNode = null;
    private static final long DOUBLE_CLICK_THRESHOLD = 300; // milliseconds
    private int sidebarWidthForRendering = 180;
    private boolean executionEnabled = true;
    private boolean hierarchyGeometryDirty = true;
    private boolean visibleRootsDirty = true;
    private boolean compactViewportMode = false;
    private boolean denseViewportMode = false;
    private int visibleNodeCountForFrame = 0;
    private final Map<TrimKey, String> trimmedTextCache = new HashMap<>();
    private final Map<String, Set<String>> runtimeVariableNamesFrameCache = new HashMap<>();
    private Set<String> cachedBaseRuntimeVariableNames = null;
    private int cachedVisibleNodeCount = 0;
    private int visibleRootsCameraX = Integer.MIN_VALUE;
    private int visibleRootsCameraY = Integer.MIN_VALUE;
    private int visibleRootsViewportWidth = Integer.MIN_VALUE;
    private int visibleRootsViewportHeight = Integer.MIN_VALUE;

    private String activePreset;
    private java.util.function.BooleanSupplier workspaceSaveHandler;
    private List<NodeGraphData.RoutineDefinitionData> routineRegistry = new ArrayList<>();
    private List<NodeGraphData.RoutineDefinitionData> routineValidationRegistry = List.of();
    private String activeRoutineWorkspaceId = "";
    private final Set<Node> cascadeDeletionPreviewNodes;

    private static final int PARAMETER_INPUT_HEIGHT = 16;
    private static final int PARAMETER_INPUT_GAP = 4;
    private static final int DIRECTION_MODE_TAB_HEIGHT = 18;

    private final InlineFieldController inlineFields = new InlineFieldController(new InlineFieldController.Host() {
        @Override public Font getClientTextRenderer() { return NodeGraph.this.getClientTextRenderer(); }
        @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
        @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
        @Override public void closeRandomRoundingDropdown() { NodeGraph.this.closeRandomRoundingDropdown(); }
        @Override public void stopParameterEditing(boolean commit) { NodeGraph.this.stopParameterEditing(commit); }
        @Override public void stopStickyNoteEditing(boolean commit) { NodeGraph.this.stopStickyNoteEditing(commit); }
        @Override public void cancelScreenCoordinateCapture() { NodeGraph.this.cancelScreenCoordinateCapture(); }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public boolean isScreenCoordinateCaptureActiveFor(Node node) {
            return NodeGraph.this.isScreenCoordinateCaptureActiveFor(node);
        }
        @Override public void notifyNodeParametersChanged(Node node) { NodeGraph.this.notifyNodeParametersChanged(node); }
        @Override public boolean isPresetSelectorNode(Node node) { return NodeGraph.this.isPresetSelectorNode(node); }
        @Override public boolean isNumericOrVariableReference(String value, Node node, boolean allowDecimal, boolean allowNegative) {
            return NodeGraph.this.isNumericOrVariableReference(value, node, allowDecimal, allowNegative);
        }
        @Override public String getStopTargetParameterKey(Node node) { return NodeGraph.this.getStopTargetParameterKey(node); }
        @Override public String getNumberExpressionErrorMessage() {
            return tr("pathmind.error.enterNumberExpressionOrVariable");
        }
        @Override public boolean isTextShortcutDown(int modifiers) { return NodeGraph.this.isTextShortcutDown(modifiers); }
        @Override public String getClipboardText() { return NodeGraph.this.getClipboardText(); }
        @Override public void setClipboardText(String text) { NodeGraph.this.setClipboardText(text); }
        @Override public int findPreviousWordBoundary(String text, int fromPosition) {
            return ParameterTextEditorController.findPreviousWordBoundary(text, fromPosition);
        }
    });
    private final InlineFieldRenderer inlineFieldRenderer = new InlineFieldRenderer(new InlineFieldRenderer.Host() {
        @Override public int cameraX() { return cameraX; }
        @Override public int cameraY() { return cameraY; }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public int selectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public float textFieldHighlightProgress(Object key, boolean hovered, boolean active) {
            return getTextFieldHighlightProgress(key, hovered, active);
        }
        @Override public UIStyleHelper.FieldPalette nodeInputPalette(boolean isOverSidebar, int accentColor,
                                                                     float progress, boolean active, boolean disabled) {
            return getNodeInputPalette(isOverSidebar, accentColor, progress, active, disabled);
        }
        @Override public UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(int backgroundColor, int borderColor,
                                                                               int innerBorderColor, int textColor,
                                                                               int placeholderColor, boolean isOverSidebar) {
            return getLowDetailAwareFieldPalette(backgroundColor, borderColor, innerBorderColor, textColor,
                placeholderColor, isOverSidebar);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text,
                                           int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public Set<String> collectRuntimeVariableNames(Node node) {
            return NodeGraph.this.collectRuntimeVariableNames(node);
        }
        @Override public boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames) {
            return NodeGraph.this.shouldBuildInlineExpressionRender(rawText, variableNames);
        }
        @Override public boolean shouldRenderNodeText() { return NodeGraph.this.shouldRenderNodeText(); }
        @Override public boolean isCompactViewportMode() { return compactViewportMode; }
        @Override public boolean isScreenCoordinateCaptureActiveFor(Node node) {
            return NodeGraph.this.isScreenCoordinateCaptureActiveFor(node);
        }
        @Override public int screenCoordinatePreviewX() { return screenCoordinateCapture.getPreviewX(); }
        @Override public int screenCoordinatePreviewY() { return screenCoordinateCapture.getPreviewY(); }
        @Override public void renderScreenCoordinatePickerButton(GuiGraphics context, Font textRenderer, Node node,
                                                                  boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderScreenCoordinatePickerButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderDropdownSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                                           boolean isOverSidebar, int mouseX, int mouseY,
                                                           int fieldLeft, int fieldTop, int fieldWidth, int fieldHeight,
                                                           String label, boolean includeValue, String value) {
            NodeGraph.this.renderDropdownSelectorField(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                fieldLeft, fieldTop, fieldWidth, fieldHeight, label, includeValue, value);
        }
        @Override public boolean isMoveItemAllAmountValue(String value) {
            return ParameterTextEditorController.isMoveItemAllAmountValue(value);
        }
        @Override public void renderAmountToggle(GuiGraphics context, Node node, boolean amountEnabled,
                                                  boolean isOverSidebar) {
            int toggleLeft = node.getAmountToggleLeft() - cameraX;
            int toggleTop = node.getAmountToggleTop() - cameraY;
            int toggleWidth = node.getAmountToggleWidth();
            int toggleHeight = node.getAmountToggleHeight();
            nodeControls.renderNodeSliderToggle(context, toggleLeft, toggleTop, toggleWidth, toggleHeight,
                nodeControls.getNodeToggleProgress(amountToggleAnimations, node, amountEnabled), false, isOverSidebar);
        }
        @Override public boolean isPresetSelectorNode(Node node) { return NodeGraph.this.isPresetSelectorNode(node); }
        @Override public void renderPresetSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                                         boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderPresetSelectorField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean isRunPresetDropdownOpenFor(Node node) {
            return specializedSelectors.isRunPresetOpen() && specializedSelectors.getRunPresetNode() == node;
        }
        @Override public String getStopTargetParameterKey(Node node) {
            return NodeGraph.this.getStopTargetParameterKey(node);
        }
        @Override public String getStopTargetPlaceholder(Node node) {
            return NodeGraph.this.getStopTargetPlaceholder(node);
        }
    }, inlineFields);
    private final ScreenCoordinateCaptureController screenCoordinateCapture = new ScreenCoordinateCaptureController(new ScreenCoordinateCaptureController.Host() {
        @Override
        public void stopOtherEditors() {
            stopCoordinateEditing(true);
            stopAmountEditing(true);
            stopStopTargetEditing(true);
            stopVariableEditing(true);
            stopMessageEditing(true);
            stopStickyNoteEditing(true);
            stopParameterEditing(true);
            stopEventNameEditing(true);
        }

        @Override
        public void notifyNodeParametersChanged(Node node) {
            NodeGraph.this.notifyNodeParametersChanged(node);
        }

        @Override
        public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
    });
    private final StickyNoteController stickyNoteController = new StickyNoteController(new StickyNoteHost() {
        @Override
        public int cameraX() {
            return cameraX;
        }

        @Override
        public int cameraY() {
            return cameraY;
        }

        @Override
        public int screenToWorldX(int screenX) {
            return NodeGraph.this.screenToWorldX(screenX);
        }

        @Override
        public int screenToWorldY(int screenY) {
            return NodeGraph.this.screenToWorldY(screenY);
        }

        @Override
        public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }

        @Override
        public int selectedNodeAccentColor() {
            return nodeControls.getSelectedNodeAccentColor();
        }

        @Override
        public void stopOtherEditors() {
            stopCoordinateEditing(true);
            stopAmountEditing(true);
            stopStopTargetEditing(true);
            stopVariableEditing(true);
            stopEventNameEditing(true);
            stopParameterEditing(true);
            stopMessageEditing(true);
        }

        @Override
        public void markWorkspaceDirty() {
            NodeGraph.this.markWorkspaceDirty();
        }

        @Override
        public boolean isWorkspaceDirty() {
            return workspaceDirty;
        }

        @Override
        public void setWorkspaceDirty(boolean dirty) {
            workspaceDirty = dirty;
        }

        @Override
        public void invalidateValidation() {
            NodeGraph.this.invalidateValidation();
        }

        @Override
        public void invalidateRenderCaches() {
            NodeGraph.this.invalidateRenderCaches();
        }

        @Override
        public void invalidateHierarchyCache() {
            NodeGraph.this.invalidateHierarchyCache();
        }

        @Override
        public void beginDragOperation() {
            if (suppressUndoCapture) {
                pendingDragUndoSnapshot = null;
            } else {
                pendingDragUndoSnapshot = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
            }
            dragOperationChanged = false;
        }

        @Override
        public void markDragOperationChanged() {
            dragOperationChanged = true;
        }

        @Override
        public boolean save() {
            return NodeGraph.this.save();
        }

        @Override
        public boolean isTextShortcutDown(int modifiers) {
            return NodeGraph.this.isTextShortcutDown(modifiers);
        }

        @Override
        public String getClipboardText() {
            return NodeGraph.this.getClipboardText();
        }

        @Override
        public void setClipboardText(String text) {
            NodeGraph.this.setClipboardText(text);
        }
    });
    private final NodeControlController nodeControls = new NodeControlController(
        new NodeControlController.Host() {
            @Override public int cameraX() { return cameraX; }
            @Override public int cameraY() { return cameraY; }
            @Override public int screenToWorldX(int screenX) {
                return NodeGraph.this.screenToWorldX(screenX);
            }
            @Override public int screenToWorldY(int screenY) {
                return NodeGraph.this.screenToWorldY(screenY);
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public boolean compactViewportMode() { return compactViewportMode; }
            @Override public Node sensorDropTarget() { return sensorDropTarget; }
            @Override public Node actionDropTarget() { return actionDropTarget; }
            @Override public Node parameterDropTarget() { return parameterDropTarget; }
            @Override public Integer parameterDropSlotIndex() { return parameterDropSlotIndex; }
            @Override public Node nodeAt(int screenX, int screenY) {
                return NodeGraph.this.getNodeAt(screenX, screenY);
            }
            @Override public int getStartModeButtonWorldX(Node node) {
                return NodeGraph.this.getStartModeButtonWorldX(node);
            }
            @Override public int getStartModeButtonWorldY(Node node) {
                return NodeGraph.this.getStartModeButtonWorldY(node);
            }
            @Override public boolean isPointInsideStartModeButton(
                Node node, int screenX, int screenY
            ) {
                return NodeGraph.this.isPointInsideStartModeButton(node, screenX, screenY);
            }
            @Override public void pushUndoState() { NodeGraph.this.pushUndoState(); }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void stopMessageEditing(boolean commit) {
                NodeGraph.this.stopMessageEditing(commit);
            }
            @Override public void startMessageEditing(Node node, int index) {
                NodeGraph.this.startMessageEditing(node, index);
            }
            @Override public Node messageEditingNode() {
                return inlineFields.getMessageEditingNode();
            }
            @Override public int messageEditingIndex() {
                return inlineFields.getMessageEditingIndex();
            }
            @Override public String translate(String key) { return tr(key); }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, String text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
        }
    );
    private final NodeRenderer nodeRenderer = new NodeRenderer(new NodeRenderer.Host() {
        @Override public int cameraX() { return cameraX; }
        @Override public int cameraY() { return cameraY; }
        @Override public boolean compactViewportMode() { return compactViewportMode; }
        @Override public boolean intersectsViewport(Node node) { return NodeGraph.this.intersectsViewport(node); }
        @Override public boolean isNodeOverSidebarForRender(Node node, int x, int width) {
            return NodeGraph.this.isNodeOverSidebarForRender(node, x, width);
        }
        @Override public void renderStickyNote(GuiGraphics context, Font textRenderer, Node node, int x, int y,
                                               int width, int height, boolean isOverSidebar) {
            stickyNoteController.render(context, textRenderer, node, x, y, width, height, isOverSidebar);
        }
        @Override public int selectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public int toGrayscale(int color, float brightnessFactor) {
            return nodeControls.toGrayscale(color, brightnessFactor);
        }
        @Override public int adjustColorBrightness(int color, float factor) {
            return nodeControls.adjustColorBrightness(color, factor);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public boolean isComparisonOperator(Node node) { return nodeControls.isComparisonOperator(node); }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public boolean shouldRenderNodeSockets(Node node) { return NodeGraph.this.shouldRenderNodeSockets(node); }
        @Override public Node hoveredSocketNode() { return connectionController.getHoveredSocketNode(); }
        @Override public int hoveredSocketIndex() { return connectionController.getHoveredSocketIndex(); }
        @Override public boolean hoveredSocketInput() { return connectionController.isHoveredSocketInput(); }
        @Override public boolean isSocketActive(Node node, int socketIndex, boolean isInput) {
            return connectionController.isSocketActive(node, socketIndex, isInput);
        }
        @Override public boolean isEditingEventNameField() { return NodeGraph.this.isEditingEventNameField(); }
        @Override public InlineTextEditor eventNameEditor() { return inlineFields.getEventNameEditor(); }
        @Override public Node eventNameEditingNode() { return inlineFields.getEventNameEditingNode(); }
        @Override public void renderEventNamePreview(GuiGraphics context, Font textRenderer, String value,
                                                     int x, int y, int color, int maxWidth) {
            NodeGraph.this.renderEventNamePreview(context, textRenderer, value, x, y, color, maxWidth);
        }
        @Override public void renderPopupEditButton(GuiGraphics context, Font textRenderer, Node node,
                                                    boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public Set<String> collectRuntimeVariableNames(Node node) {
            return NodeGraph.this.collectRuntimeVariableNames(node);
        }
        @Override public boolean shouldRenderNodeText() { return NodeGraph.this.shouldRenderNodeText(); }
        @Override public boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames) {
            return NodeGraph.this.shouldBuildInlineExpressionRender(rawText, variableNames);
        }
        @Override public boolean hoveringStartButton() { return hoveringStartButton; }
        @Override public void renderStartLaunchIcon(GuiGraphics context, StartLaunchMode mode, int centerX,
                                                    int centerY, int color, int nodeTop, int nodeHeight) {
            nodeControls.renderStartLaunchIcon(context, mode, centerX, centerY, color, nodeTop, nodeHeight);
        }
        @Override public void renderStartNodeNumber(GuiGraphics context, Font textRenderer, Node node,
                                                    int x, int y, boolean isOverSidebar) {
            nodeControls.renderStartNodeNumber(context, textRenderer, node, x, y, isOverSidebar);
        }
        @Override public void renderStartModeButton(GuiGraphics context, Node node, int x, int y,
                                                    boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderStartModeButton(context, node, x, y, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean isEditingParameterField() { return NodeGraph.this.isEditingParameterField(); }
        @Override public Node parameterEditingNode() { return parameterEditor.getNode(); }
        @Override public int parameterEditingIndex() { return parameterEditor.getIndex(); }
        @Override public void updateParameterCaretBlink() { parameterEditor.updateCaretBlink(); }
        @Override public String parameterEditBuffer() { return parameterEditor.getBuffer(); }
        @Override public boolean hasParameterSelection() {
            return parameterEditor.getSelectionStart() >= 0
                && parameterEditor.getSelectionEnd() >= 0
                && parameterEditor.getSelectionStart() != parameterEditor.getSelectionEnd();
        }
        @Override public int parameterSelectionStart() { return parameterEditor.getSelectionStart(); }
        @Override public int parameterSelectionEnd() { return parameterEditor.getSelectionEnd(); }
        @Override public boolean parameterCaretVisible() { return parameterEditor.isCaretVisible(); }
        @Override public int parameterCaretPosition() { return parameterEditor.getCaretPosition(); }
        @Override public boolean shouldShowParameters(Node node) { return NodeGraph.this.shouldShowParameters(node); }
        @Override public int parameterInputHeight() { return PARAMETER_INPUT_HEIGHT; }
        @Override public int parameterInputGap() { return PARAMETER_INPUT_GAP; }
        @Override public int directionModeTabHeight() { return DIRECTION_MODE_TAB_HEIGHT; }
        @Override public int getParameterFieldLeft(Node node) { return nodeControls.getParameterFieldLeft(node); }
        @Override public int getParameterFieldWidth(Node node) { return nodeControls.getParameterFieldWidth(node); }
        @Override public int getParameterFieldHeight() { return nodeControls.getParameterFieldHeight(); }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active) {
            return NodeGraph.this.getTextFieldHighlightProgress(key, hovered, active);
        }
        @Override public boolean isCombinedDirectionNode(Node node) {
            return nodeControls.isCombinedDirectionNode(node);
        }
        @Override public void renderDirectionModeTabs(GuiGraphics context, Font textRenderer, Node node,
                                                      boolean isOverSidebar, int fieldTop, int mouseX, int mouseY) {
            nodeControls.renderDirectionModeTabs(context, textRenderer, node, isOverSidebar, fieldTop, mouseX, mouseY);
        }
        @Override public boolean isCombinedBooleanNode(Node node) {
            return nodeControls.isCombinedBooleanNode(node);
        }
        @Override public void renderBooleanModeTabs(GuiGraphics context, Font textRenderer, Node node,
                                                    boolean isOverSidebar, int fieldTop, int mouseX, int mouseY) {
            nodeControls.renderBooleanModeTabs(context, textRenderer, node, isOverSidebar, fieldTop, mouseX, mouseY);
        }
        @Override public String getParameterLabelText(Node node, NodeParameter parameter, Font textRenderer,
                                                      int maxWidth) {
            return nodeControls.getParameterLabelText(node, parameter, textRenderer, maxWidth);
        }
        @Override public int getParameterValueStartX(Node node, NodeParameter parameter, Font textRenderer) {
            return nodeControls.getParameterValueStartX(node, parameter, textRenderer);
        }
        @Override public boolean isDefaultMouseButtonValue(String value) {
            return NodeGraph.this.isDefaultMouseButtonValue(value);
        }
        @Override public boolean isDefaultHandValue(String value) {
            return NodeGraph.this.isDefaultHandValue(value);
        }
        @Override public boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing) {
            return NodeGraph.this.isTradeInlinePlaceholder(node, parameter, editing);
        }
        @Override public boolean isAnyBlockItemValue(String value) {
            return NodeGraph.this.isAnyBlockItemValue(value);
        }
        @Override public String formatVillagerTradeValue(String rawValue) {
            return nodeControls.formatVillagerTradeValue(rawValue);
        }
        @Override public String formatMouseButtonValue(String value) {
            return NodeGraph.this.formatMouseButtonValue(value);
        }
        @Override public String formatHandValue(String value) { return NodeGraph.this.formatHandValue(value); }
        @Override public String formatAttributeDetectionInlineValue(Node node, NodeParameter parameter, String value) {
            return NodeGraph.this.formatAttributeDetectionInlineValue(node, parameter, value);
        }
        @Override public boolean parameterDropdownOpen() { return parameterDropdown.isOpen(); }
        @Override public Node parameterDropdownNode() { return parameterDropdown.getNode(); }
        @Override public int parameterDropdownIndex() { return parameterDropdown.getIndex(); }
        @Override public boolean supportsRelativeInlineParameter(Node node, NodeParameter parameter) {
            return NodeGraph.this.supportsRelativeInlineParameter(node, parameter);
        }
        @Override public boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames,
                                                                   boolean allowRelativeMarker) {
            return NodeGraph.this.shouldBuildInlineExpressionRender(rawText, variableNames, allowRelativeMarker);
        }
        @Override public void updateParameterDropdown(Node node, int index, Font textRenderer, int fieldX,
                                                      int fieldY, int fieldWidth, int fieldHeight) {
            NodeGraph.this.updateParameterDropdown(node, index, textRenderer, fieldX, fieldY, fieldWidth, fieldHeight);
        }
        @Override public void renderRandomRoundingField(GuiGraphics context, Font textRenderer, Node node,
                                                        boolean isOverSidebar) {
            NodeGraph.this.renderRandomRoundingField(context, textRenderer, node, isOverSidebar);
        }
        @Override public void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node,
                                                     boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderParameterSlot(GuiGraphics context, Font textRenderer, Node node,
                                                  boolean isOverSidebar, int slotIndex) {
            nodeControls.renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
        }
        @Override public String getOperatorSymbol(Node node, boolean negated) {
            return nodeControls.getOperatorSymbol(node, negated);
        }
        @Override public void renderNodeContent(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY,
                                                int x, int y, int width, int height, boolean isOverSidebar,
                                                boolean simpleStyle, boolean lowDetail) {
            NodeGraph.this.renderNodeContent(context, textRenderer, node, mouseX, mouseY, x, y, width, height,
                isOverSidebar, simpleStyle, lowDetail);
        }
    });
    private final TemplateNodeRenderer templateNodeRenderer = new TemplateNodeRenderer(new TemplateNodeRenderer.Host() {
        @Override public int cameraX() { return cameraX; }
        @Override public int cameraY() { return cameraY; }
        @Override public int adjustColorBrightness(int color, float factor) {
            return nodeControls.adjustColorBrightness(color, factor);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public NodeGraphData.CustomNodeDefinition getTemplateDefinition(Node node) {
            return NodeGraph.this.getTemplateDefinition(node);
        }
        @Override public String getSelectedPresetName(Node node) { return NodeGraph.this.getSelectedPresetName(node); }
        @Override public NodeGraphData getPresetPreviewGraphData(Node node) {
            return NodeGraph.this.getPresetPreviewGraphData(node);
        }
        @Override public void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node,
                                                         boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                                          boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    });
    private final ParameterTextEditorController parameterEditor = new ParameterTextEditorController(
        new ParameterTextEditorController.Host() {
            @Override public boolean canEditInlineParameterFields(Node node) {
                return NodeGraph.this.canEditInlineParameterFields(node);
            }
            @Override public void closeModeDropdown() { NodeGraph.this.closeModeDropdown(); }
            @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
            @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
            @Override public void closeRandomRoundingDropdown() { NodeGraph.this.closeRandomRoundingDropdown(); }
            @Override public void closeParameterDropdown() { parameterDropdown.close(); }
            @Override public void clearParameterDropdownSuppression() { parameterDropdown.clearSuppression(); }
            @Override public void stopCoordinateEditing(boolean commit) { NodeGraph.this.stopCoordinateEditing(commit); }
            @Override public void stopAmountEditing(boolean commit) { NodeGraph.this.stopAmountEditing(commit); }
            @Override public void stopStopTargetEditing(boolean commit) { NodeGraph.this.stopStopTargetEditing(commit); }
            @Override public void stopMessageEditing(boolean commit) { NodeGraph.this.stopMessageEditing(commit); }
            @Override public void stopVariableEditing(boolean commit) { NodeGraph.this.stopVariableEditing(commit); }
            @Override public void stopEventNameEditing(boolean commit) { NodeGraph.this.stopEventNameEditing(commit); }
            @Override public void stopStickyNoteEditing(boolean commit) { NodeGraph.this.stopStickyNoteEditing(commit); }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void updateParameterFieldContentWidth(
                Node node, Font renderer, int index, String value
            ) {
                NodeGraph.this.updateParameterFieldContentWidth(node, renderer, index, value);
            }
            @Override public Font getClientTextRenderer() { return NodeGraph.this.getClientTextRenderer(); }
            @Override public boolean isTextShortcutDown(int modifiers) {
                return NodeGraph.this.isTextShortcutDown(modifiers);
            }
            @Override public String getClipboardText() { return NodeGraph.this.getClipboardText(); }
            @Override public void setClipboardText(String text) { NodeGraph.this.setClipboardText(text); }
        }
    );
    private final ParameterDropdownController parameterDropdown = new ParameterDropdownController(
        new ParameterDropdownController.Host() {
            @Override public boolean isEditingParameterField() {
                return NodeGraph.this.isEditingParameterField();
            }
            @Override public Node parameterEditingNode() { return parameterEditor.getNode(); }
            @Override public int parameterEditingIndex() { return parameterEditor.getIndex(); }
            @Override public String parameterEditBuffer() { return parameterEditor.getBuffer(); }
            @Override public int parameterCaretPosition() { return parameterEditor.getCaretPosition(); }
            @Override public void replaceParameterEditBuffer(String value, int caretPosition) {
                parameterEditor.replaceBuffer(value, caretPosition);
            }
            @Override public void updateParameterFieldContentWidth(
                Node node, Font textRenderer, int index, String value
            ) {
                NodeGraph.this.updateParameterFieldContentWidth(node, textRenderer, index, value);
            }
            @Override public void refreshStateParameterPreview() {
                parameterEditor.refreshStatePreview();
            }
            @Override public boolean applyParameterEdit() {
                return parameterEditor.apply();
            }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void closeModeDropdown() { NodeGraph.this.closeModeDropdown(); }
            @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
            @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
            @Override public void closeRandomRoundingDropdown() {
                NodeGraph.this.closeRandomRoundingDropdown();
            }
            @Override public void stopParameterEditing(boolean commit) {
                NodeGraph.this.stopParameterEditing(commit);
            }
            @Override public int parameterFieldLeft(Node node) {
                return nodeControls.getParameterFieldLeft(node);
            }
            @Override public int inlineParameterFieldTop(Node node, int index) {
                return getInlineParameterFieldTop(node, index);
            }
            @Override public int parameterFieldWidth(Node node) {
                return nodeControls.getParameterFieldWidth(node);
            }
            @Override public int parameterFieldHeight() { return nodeControls.getParameterFieldHeight(); }
            @Override public int cameraX() { return cameraX; }
            @Override public int cameraY() { return cameraY; }
            @Override public int screenToUiX(int screenX) {
                return NodeGraph.this.screenToUiX(screenX);
            }
            @Override public int screenToUiY(int screenY) {
                return NodeGraph.this.screenToUiY(screenY);
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public Font getClientTextRenderer() {
                return NodeGraph.this.getClientTextRenderer();
            }
            @Override public float dropdownAnimationProgress(AnimatedValue animation, boolean open) {
                return getDropdownAnimationProgress(animation, open);
            }
            @Override public int selectedNodeAccentColor() {
                return nodeControls.getSelectedNodeAccentColor();
            }
            @Override public void enableDropdownScissor(
                GuiGraphics context, int x, int y, int width, int height
            ) {
                NodeGraph.this.enableDropdownScissor(context, x, y, width, height);
            }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
        }
    );
    private final SpecializedSelectorController specializedSelectors = new SpecializedSelectorController(
        new SpecializedSelectorController.Host() {
            @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
            @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
            @Override public int cameraY() { return cameraY; }
            @Override public int guiScaledHeight() {
                return Minecraft.getInstance().getWindow().getGuiScaledHeight();
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public int schematicDropdownWidth(Node node) {
                return getSchematicDropdownWidth(node);
            }
            @Override public int runPresetDropdownWidth(Node node) {
                return getRunPresetDropdownWidth(node);
            }
            @Override public List<String> loadSchematicOptions() {
                return SchematicRepository.loadSchematicOptions();
            }
            @Override public boolean schematicExists(String name) {
                return schematicExistsInRoots(name);
            }
            @Override public boolean isPresetSelectorNode(Node node) {
                return NodeGraph.this.isPresetSelectorNode(node);
            }
            @Override public String stopTargetParameterKey(Node node) {
                return getStopTargetParameterKey(node);
            }
            @Override public void prepareRunPresetOpen(Node node) {
                focusSelectedNode(node);
                stopStopTargetEditing(true);
            }
            @Override public void applySchematicSelection(Node node, String value) {
                NodeGraph.this.applySchematicSelection(node, value);
            }
            @Override public void applyRunPresetSelection(Node node, String value) {
                NodeGraph.this.applyRunPresetSelection(node, value);
            }
        }
    );
    private final AnimatedValue schematicDropdownAnimation = AnimatedValue.forHover();
    private final AnimatedValue runPresetDropdownAnimation = AnimatedValue.forHover();
    private static final int SCHEMATIC_DROPDOWN_MAX_ROWS = 8;
    private static final int SCHEMATIC_DROPDOWN_ROW_HEIGHT = 16;
    private final RandomRoundingController randomRounding = new RandomRoundingController(
        new RandomRoundingController.Host() {
            @Override public int cameraX() { return cameraX; }
            @Override public int cameraY() { return cameraY; }
            @Override public int screenToWorldX(int screenX) {
                return NodeGraph.this.screenToWorldX(screenX);
            }
            @Override public int screenToWorldY(int screenY) {
                return NodeGraph.this.screenToWorldY(screenY);
            }
            @Override public int guiScaledHeight() {
                return Minecraft.getInstance().getWindow().getGuiScaledHeight();
            }
            @Override public Font clientTextRenderer() { return getClientTextRenderer(); }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public boolean compactViewportMode() { return compactViewportMode; }
            @Override public boolean shouldRenderNodeText() {
                return NodeGraph.this.shouldRenderNodeText();
            }
            @Override public int selectedNodeAccentColor() {
                return nodeControls.getSelectedNodeAccentColor();
            }
            @Override public String translate(String key) { return tr(key); }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public UIStyleHelper.FieldPalette nodeInputPalette(
                boolean isOverSidebar, int accentColor, float progress, boolean active, boolean disabled
            ) {
                return getNodeInputPalette(
                    isOverSidebar, accentColor, progress, active, disabled
                );
            }
            @Override public UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(
                int backgroundColor, int borderColor, int innerBorderColor, int textColor,
                int placeholderColor, boolean isOverSidebar
            ) {
                return getLowDetailAwareFieldPalette(
                    backgroundColor, borderColor, innerBorderColor, textColor,
                    placeholderColor, isOverSidebar
                );
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
            @Override public void renderToggle(
                GuiGraphics context, Node node, int left, int top, int width, int height,
                boolean enabled, boolean isOverSidebar
            ) {
                nodeControls.renderNodeSliderToggle(
                    context, left, top, width, height,
                    nodeControls.getNodeToggleProgress(randomRoundingToggleAnimations, node, enabled),
                    false, isOverSidebar
                );
            }
            @Override public float dropdownAnimationProgress(
                AnimatedValue animation, boolean open
            ) {
                return getDropdownAnimationProgress(animation, open);
            }
            @Override public void animateToggle(Node node, boolean enabled) {
                nodeControls.getNodeToggleAnimation(randomRoundingToggleAnimations, node, enabled)
                    .animateTo(
                        enabled ? 1f : 0f,
                        UITheme.TRANSITION_ANIM_MS,
                        AnimationHelper::easeInOutCubic
                    );
            }
            @Override public void stopParameterEditing() {
                NodeGraph.this.stopParameterEditing(true);
            }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
        }
    );
    private final AnimatedValue modeDropdownAnimation = AnimatedValue.forHover();
    private final DropdownController.Host dropdownHost = new DropdownController.Host() {
        @Override public float getZoomScale() { return NodeGraph.this.getZoomScale(); }
        @Override public int screenToUiX(int screenX) { return NodeGraph.this.screenToUiX(screenX); }
        @Override public int screenToUiY(int screenY) { return NodeGraph.this.screenToUiY(screenY); }
        @Override public int getDropdownRowHeight() { return NodeGraph.this.getDropdownRowHeight(); }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public Font getTextRenderer() { return getClientTextRenderer(); }
        @Override public int getGuiScaledHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) { return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth); }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) { NodeGraph.this.drawNodeText(context, renderer, text, x, y, color); }
        @Override public void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height) { NodeGraph.this.enableDropdownScissor(context, x, y, width, height); }
        @Override public float dropdownAnimationProgress(AnimatedValue animation, boolean open) { return getDropdownAnimationProgress(animation, open); }
    };
    private final DropdownController<com.pathmind.nodes.NodeMode> modeDropdown = new DropdownController<>(
        dropdownHost,
        modeDropdownAnimation,
        PARAMETER_DROPDOWN_MAX_ROWS,
        () -> tr("pathmind.dropdown.noModes"),
        this::computeModeDropdownAnchor,
        this::getModeDropdownOptions,
        (node, mode) -> {
            node.setMode(mode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
    );
    private static final int PARAMETER_DROPDOWN_MAX_ROWS = 8;
    private static final int DROPDOWN_SIDE_PADDING = 6;
    private static final int DROPDOWN_SCROLLBAR_ALLOWANCE = 8;
    private boolean workspaceDirty = false;
    private int nextStartNodeNumber = 1;
    private boolean validationDirty = true;
    private GraphValidationResult cachedValidationResult = GraphValidationResult.empty();
    private static final float ZOOM_SCROLL_STEP = 1.12f;
    private static final float ZOOM_EPSILON = 0.0001f;
    private ZoomLevel zoomLevel = ZoomLevel.FOCUSED;
    private float zoomScale = ZoomLevel.FOCUSED.getScale();
    private ClipboardSnapshot clipboardNodeSnapshot = null;
    private final Deque<NodeGraphData> undoStack = new ArrayDeque<>();
    private final Deque<NodeGraphData> redoStack = new ArrayDeque<>();
    private boolean suppressUndoCapture = false;
    private static final int MAX_HISTORY = 50;
    private static final Map<String, SessionViewportState> SESSION_VIEWPORT_STATES = new ConcurrentHashMap<>();
    private boolean selectionBoxActive = false;
    private int selectionBoxStartWorldX = 0;
    private int selectionBoxStartWorldY = 0;
    private int selectionBoxCurrentWorldX = 0;
    private int selectionBoxCurrentWorldY = 0;
    private boolean multiDragActive = false;
    private final Map<Node, DragStartInfo> multiDragStartPositions = new HashMap<>();
    private boolean selectionDeletionPreviewActive = false;


    public enum ZoomLevel {
        FOCUSED(1.0f, true),
        OVERVIEW(0.35f, true),
        DISTANT(0.18f, true);

        private final float scale;
        private final boolean showText;

        ZoomLevel(float scale, boolean showText) {
            this.scale = scale;
            this.showText = showText;
        }

        public float getScale() {
            return scale;
        }

        public boolean shouldShowText() {
            return showText;
        }
    }

    static final class ClipboardSnapshot {
        final NodeGraphData data;
        final List<String> selectionIds;
        final int anchorX;
        final int anchorY;

        ClipboardSnapshot(NodeGraphData data, List<String> selectionIds, int anchorX, int anchorY) {
            this.data = data;
            this.selectionIds = selectionIds;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    static final class SelectionBounds {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;

        SelectionBounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class DragStartInfo {
        private final int x;
        private final int y;

        private DragStartInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class SessionViewportState {
        private final int cameraX;
        private final int cameraY;
        private final ZoomLevel zoomLevel;
        private final float zoomScale;

        private SessionViewportState(int cameraX, int cameraY, ZoomLevel zoomLevel, float zoomScale) {
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.zoomLevel = zoomLevel;
            this.zoomScale = zoomScale;
        }
    }

    public ZoomLevel getZoomLevel() {
        return zoomLevel;
    }

    public boolean isZoomedOut() {
        return zoomScale < (ZoomLevel.FOCUSED.getScale() - ZOOM_EPSILON);
    }

    public void setZoomLevel(ZoomLevel newLevel, int anchorScreenX, int anchorScreenY) {
        if (newLevel == null || newLevel == this.zoomLevel) {
            return;
        }
        int anchorWorldX = screenToWorldX(anchorScreenX);
        int anchorWorldY = screenToWorldY(anchorScreenY);
        this.zoomLevel = newLevel;
        this.zoomScale = newLevel.getScale();
        alignCameraToAnchor(anchorWorldX, anchorWorldY, anchorScreenX, anchorScreenY);
        cacheSessionViewportState();
    }

    private void alignCameraToAnchor(int anchorWorldX, int anchorWorldY, int anchorScreenX, int anchorScreenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        cameraX = Math.round(anchorWorldX - anchorScreenX / scale);
        cameraY = Math.round(anchorWorldY - anchorScreenY / scale);
    }

    public float getZoomScale() {
        return zoomScale;
    }

    private boolean shouldRenderNodeText() {
        return zoomLevel.shouldShowText();
    }

    public boolean canZoomIn() {
        return zoomScale < (ZoomLevel.FOCUSED.getScale() - ZOOM_EPSILON);
    }

    public boolean canZoomOut() {
        return zoomScale > (ZoomLevel.DISTANT.getScale() + ZOOM_EPSILON);
    }

    public void zoomIn(int anchorScreenX, int anchorScreenY) {
        ZoomLevel target = getNextZoomInLevel();
        if (target != null) {
            setZoomLevel(target, anchorScreenX, anchorScreenY);
        }
    }

    public void zoomOut(int anchorScreenX, int anchorScreenY) {
        ZoomLevel target = getNextZoomOutLevel();
        if (target != null) {
            setZoomLevel(target, anchorScreenX, anchorScreenY);
        }
    }

    public boolean isDefaultZoom() {
        return Math.abs(zoomScale - ZoomLevel.FOCUSED.getScale()) <= ZOOM_EPSILON;
    }

    private ZoomLevel getNextZoomInLevel() {
        ZoomLevel target = null;
        for (ZoomLevel level : ZoomLevel.values()) {
            if (level.getScale() > zoomScale + ZOOM_EPSILON) {
                if (target == null || level.getScale() < target.getScale()) {
                    target = level;
                }
            }
        }
        return target;
    }

    private ZoomLevel getNextZoomOutLevel() {
        ZoomLevel target = null;
        for (ZoomLevel level : ZoomLevel.values()) {
            if (level.getScale() < zoomScale - ZOOM_EPSILON) {
                if (target == null || level.getScale() > target.getScale()) {
                    target = level;
                }
            }
        }
        return target;
    }

    public void zoomByScroll(double scrollAmount, int anchorScreenX, int anchorScreenY) {
        if (scrollAmount == 0.0) {
            return;
        }
        float scaleFactor = (float) Math.pow(ZOOM_SCROLL_STEP, scrollAmount);
        setZoomScale(zoomScale * scaleFactor, anchorScreenX, anchorScreenY);
    }

    private void setZoomScale(float newScale, int anchorScreenX, int anchorScreenY) {
        float minScale = ZoomLevel.DISTANT.getScale();
        float maxScale = ZoomLevel.FOCUSED.getScale();
        float clampedScale = Mth.clamp(newScale, minScale, maxScale);
        if (Math.abs(clampedScale - zoomScale) <= ZOOM_EPSILON) {
            return;
        }
        int anchorWorldX = screenToWorldX(anchorScreenX);
        int anchorWorldY = screenToWorldY(anchorScreenY);
        zoomScale = clampedScale;
        updateZoomLevelFromScale();
        alignCameraToAnchor(anchorWorldX, anchorWorldY, anchorScreenX, anchorScreenY);
        cacheSessionViewportState();
    }

    private void updateZoomLevelFromScale() {
        float minScale = ZoomLevel.DISTANT.getScale();
        float maxScale = ZoomLevel.FOCUSED.getScale();
        if (zoomScale >= maxScale - ZOOM_EPSILON) {
            zoomLevel = ZoomLevel.FOCUSED;
        } else if (zoomScale <= minScale + ZOOM_EPSILON) {
            zoomLevel = ZoomLevel.DISTANT;
        } else {
            zoomLevel = ZoomLevel.OVERVIEW;
        }
    }

    public NodeGraph() {
        this.nodes = new ArrayList<>();
        this.connections = new ArrayList<>();
        this.cachedRootNodes = new ArrayList<>();
        this.cachedVisibleRootNodes = new ArrayList<>();
        this.cachedHierarchyBounds = new HashMap<>();
        this.cachedHierarchyNodeCounts = new HashMap<>();
        this.selectedNode = null;
        this.selectedNodes = new LinkedHashSet<>();
        this.draggingNode = null;
        this.draggingNodeStartX = 0;
        this.draggingNodeStartY = 0;
        this.draggingNodeDetached = false;
        this.activePreset = PresetManager.getActivePreset();
        this.cascadeDeletionPreviewNodes = new HashSet<>();

        // Add preset nodes similar to Blender's shader editor
        // Will be initialized with proper centering when screen dimensions are available
    }
    
    public void initializeWithScreenDimensions(int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        // Clear any existing nodes
        nodes.clear();
        connections.clear();
        invalidateRenderCaches();
        clearTransientGraphState();
        nextStartNodeNumber = 1;
        
        // Calculate workspace area
        int workspaceStartX = sidebarWidth;
        int workspaceStartY = titleBarHeight;
        int workspaceWidth = screenWidth - sidebarWidth;
        int workspaceHeight = screenHeight - titleBarHeight;
        
        // Center nodes in the workspace
        int centerX = workspaceStartX + workspaceWidth / 2;
        int centerY = workspaceStartY + workspaceHeight / 2;
        
        // Position the initial start node centered in the workspace.
        Node startNode = new Node(NodeType.START, centerX, centerY - 50);
        assignNewStartNodeNumber(startNode);
        nodes.add(startNode);
        restoreSessionViewportState();
        invalidateValidation();
    }

    void assignNewStartNodeNumber(Node node) {
        if (node == null || node.getType() != NodeType.START) {
            return;
        }
        if (nextStartNodeNumber <= 0) {
            nextStartNodeNumber = 1;
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Node existing : nodes) {
            if (existing != null && existing.getType() == NodeType.START) {
                int number = existing.getStartNodeNumber();
                if (number > 0) {
                    used.add(number);
                }
            }
        }
        while (used.contains(nextStartNodeNumber)) {
            nextStartNodeNumber++;
        }
        node.setStartNodeNumber(nextStartNodeNumber);
        nextStartNodeNumber++;
    }

    private void normalizeStartNodeNumbers() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int max = 0;
        List<Node> startNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (node != null && node.getType() == NodeType.START) {
                startNodes.add(node);
            }
        }
        for (Node node : startNodes) {
            int number = node.getStartNodeNumber();
            if (number > 0 && used.add(number)) {
                max = Math.max(max, number);
            } else {
                node.setStartNodeNumber(0);
            }
        }
        int next = Math.max(1, max + 1);
        for (Node node : startNodes) {
            if (node.getStartNodeNumber() <= 0) {
                node.setStartNodeNumber(next);
                used.add(next);
                next++;
            }
        }
        nextStartNodeNumber = next;
    }


    public void addNode(Node node) {
        if (node != null && node.getType() == NodeType.ROUTINE_INPUT
            && (!activeRoutineWorkspaceId.equals(node.getRoutineId()) || activeRoutineWorkspaceId.isBlank())) {
            return;
        }
        if (node != null && node.getType() == NodeType.START && node.getStartNodeNumber() <= 0) {
            assignNewStartNodeNumber(node);
        }
        nodes.add(node);
        invalidateHierarchyCache();
    }

    public void removeNode(Node node) {
        if (node == null || node.isProtectedRoutineEntry()) {
            return;
        }
        pushUndoState();
        removeNodeInternal(node, true, true);
        markWorkspaceDirty();
    }

    private void removeNodeInternal(Node node, boolean autoReconnect, boolean repositionDetachments) {
        if (node == null) {
            return;
        }

        if (inlineFields.getCoordinateEditingNode() == node) {
            stopCoordinateEditing(false);
        }

        if (inlineFields.getAmountEditingNode() == node) {
            stopAmountEditing(false);
        }
        if (inlineFields.getStopTargetEditingNode() == node) {
            stopStopTargetEditing(false);
        }
        if (inlineFields.getVariableEditingNode() == node) {
            stopVariableEditing(false);
        }
        if (inlineFields.getMessageEditingNode() == node) {
            stopMessageEditing(false);
        }

        if (specializedSelectors.getRunPresetNode() == node) {
            closeRunPresetDropdown();
        }

        if (node.hasAttachedSensor()) {
            Node attached = node.getAttachedSensor();
            node.detachSensor();
            if (repositionDetachments && attached != null) {
                attached.setPosition(node.getX() + node.getWidth() + 12, node.getY());
            }
        }

        if (node.hasAttachedActionNode()) {
            Node attached = node.getAttachedActionNode();
            node.detachActionNode();
            if (repositionDetachments && attached != null) {
                attached.setPosition(node.getX() + node.getWidth() + 12, node.getY());
            }
        }

        if (node.isSensorNode() && node.isAttachedToControl()) {
            Node parent = node.getParentControl();
            if (parent != null) {
                parent.detachSensor();
            }
        }

        if (node.isAttachedToActionControl()) {
            Node parent = node.getParentActionControl();
            if (parent != null) {
                parent.detachActionNode();
            }
        }

        if (node.isParameterNode() && node.getParentParameterHost() != null) {
            Node parent = node.getParentParameterHost();
            int slotIndex = node.getParentParameterSlotIndex();
            if (parent != null) {
                parent.detachParameter(slotIndex);
            }
        }

        if (node.hasAttachedParameter()) {
            List<Integer> parameterSlots = new ArrayList<>(node.getAttachedParameters().keySet());
            for (Integer slotIndex : parameterSlots) {
                node.detachParameter(slotIndex);
            }
        }

        if (sensorDropTarget == node) {
            sensorDropTarget = null;
            actionDropTarget = null;
            parameterDropTarget = null;
            parameterDropSlotIndex = null;
        }

        if (actionDropTarget == node) {
            actionDropTarget = null;
        }

        if (parameterDropTarget == node) {
            parameterDropTarget = null;
            parameterDropSlotIndex = null;
        }

        if (autoReconnect) {
            List<NodeConnection> inputConnections = new ArrayList<>();
            List<NodeConnection> outputConnections = new ArrayList<>();

            for (NodeConnection conn : connections) {
                if (conn.getOutputNode().equals(node)) {
                    outputConnections.add(conn);
                } else if (conn.getInputNode().equals(node)) {
                    inputConnections.add(conn);
                }
            }

            // Avoid reconnecting nodes that previously fanned out to multiple outputs,
            // otherwise the upstream node ends up with duplicate outgoing lines.
            boolean hasMultipleOutputs = outputConnections.size() > 1;
            if (!hasMultipleOutputs) {
                for (NodeConnection inputConn : inputConnections) {
                    Node inputSource = inputConn.getOutputNode();
                    int inputSocket = inputConn.getOutputSocket();

                    for (NodeConnection outputConn : outputConnections) {
                        Node outputTarget = outputConn.getInputNode();
                        int outputSocket = outputConn.getInputSocket();

                        addConnectionReplacingConflicts(inputSource, outputTarget, inputSocket, outputSocket);
                    }
                }
            }
        }

        boolean removedStartNode = node.getType() == NodeType.START;
        connections.removeIf(conn ->
            conn.getOutputNode().equals(node) || conn.getInputNode().equals(node));
        nodes.remove(node);
        if (removedStartNode) {
            // Reuse freed START numbers on the next START node creation.
            nextStartNodeNumber = 1;
        }

        if (selectedNodes.remove(node)) {
            node.setSelected(false);
            if (selectedNode == node) {
                selectedNode = selectedNodes.isEmpty() ? null : selectedNodes.iterator().next();
            }
        }
        if (draggingNode == node) {
            draggingNode = null;
        }
        invalidateRenderCaches();
    }

    public Node getNodeAt(int x, int y) {
        int worldX = screenToWorldX(x);
        int worldY = screenToWorldY(y);
        return getNodeAtWorld(worldX, worldY);
    }

    private Node getNodeAtWorld(int worldX, int worldY) {
        long startNanos = System.nanoTime();
        List<Node> visibleRoots = getVisibleRootsForViewport();
        int rootCount = visibleRoots.size();
        Node hit = null;
        for (int i = visibleRoots.size() - 1; i >= 0; i--) {
            Node root = visibleRoots.get(i);
            hit = findNodeInHierarchyAt(root, worldX, worldY);
            if (hit != null) {
                break;
            }
        }
        long duration = System.nanoTime() - startNanos;
        profilerHitTestTotalNanos += duration;
        profilerHitTestCallCount++;
        profilerHitTestTotalRoots += rootCount;
        profilerHitTestAvgMs = (profilerHitTestTotalNanos / (double) profilerHitTestCallCount) / 1_000_000.0;
        profilerHitTestAvgRoots = profilerHitTestTotalRoots / (double) profilerHitTestCallCount;
        return hit;
    }

    private Node findNodeInHierarchyAt(Node node, int worldX, int worldY) {
        if (node == null) {
            return null;
        }

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            keys.sort(Collections.reverseOrder());
            for (Integer key : keys) {
                Node hit = findNodeInHierarchyAt(parameterMap.get(key), worldX, worldY);
                if (hit != null) {
                    return hit;
                }
            }
        }

        Node sensorChild = node.getAttachedSensor();
        Node hit = findNodeInHierarchyAt(sensorChild, worldX, worldY);
        if (hit != null) {
            return hit;
        }

        Node actionChild = node.getAttachedActionNode();
        hit = findNodeInHierarchyAt(actionChild, worldX, worldY);
        if (hit != null) {
            return hit;
        }

        if (isNodeHitAt(node, worldX, worldY)) {
            return node;
        }

        return null;
    }

    private boolean isNodeHitAt(Node node, int worldX, int worldY) {
        if (node == null) {
            return false;
        }
        if (node.containsPoint(worldX, worldY)) {
            return true;
        }
        return node.isSelected() && stickyNoteController.getResizeCornerAtWorld(node, worldX, worldY) != null;
    }

    public void selectNode(Node node) {
        if (node == null) {
            clearSelection();
            return;
        }
        clearSelection();
        addNodeToSelection(node);
    }

    public void selectNodes(Collection<Node> nodesToSelect) {
        clearSelection();
        if (nodesToSelect == null) {
            return;
        }
        for (Node node : nodesToSelect) {
            addNodeToSelection(node);
        }
    }

    public Set<Node> getSelectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    public void setSelectionDeletionPreviewActive(boolean active) {
        selectionDeletionPreviewActive = active;
    }

    public boolean isNodeSelected(Node node) {
        if (node == null) {
            return false;
        }
        return selectedNodes.contains(node);
    }

    public void focusSelectedNode(Node node) {
        if (node == null) {
            return;
        }
        if (!selectedNodes.contains(node)) {
            selectNode(node);
            return;
        }
        selectedNode = node;
        node.setSelected(true);
    }

    public void toggleNodeInSelection(Node node) {
        if (node == null) {
            return;
        }
        if (selectedNodes.contains(node)) {
            // Remove from selection
            selectedNodes.remove(node);
            node.setSelected(false);
            // Update focused node if we removed it
            if (selectedNode == node) {
                selectedNode = selectedNodes.isEmpty() ? null : selectedNodes.iterator().next();
            }
        } else {
            // Add to selection
            addNodeToSelection(node);
        }
    }

    SelectionBounds calculateBounds(Collection<Node> nodesToMeasure) {
        return NodeGraphHierarchySupport.calculateBounds(nodesToMeasure);
    }

    private SelectionBounds calculateHierarchyBounds(Node root) {
        if (root == null) {
            return null;
        }
        List<Node> hierarchyNodes = new ArrayList<>();
        collectHierarchyNodes(root, hierarchyNodes, new HashSet<>());
        return calculateBounds(hierarchyNodes);
    }

    private void invalidateHierarchyCache() {
        hierarchyGeometryDirty = true;
        visibleRootsDirty = true;
    }

    private void invalidateConnectionIndex() {
        connectionController.invalidateConnectionIndex();
    }

    private void invalidateRenderCaches() {
        invalidateHierarchyCache();
        invalidateConnectionIndex();
        trimmedTextCache.clear();
        nodeControls.clearParameterLayoutCache();
        runtimeVariableNamesFrameCache.clear();
        cachedBaseRuntimeVariableNames = null;
    }

    private void rebuildHierarchyCacheIfNeeded() {
        if (hierarchyGeometryDirty) {
            // The visible-viewport set (cachedVisibleRootNodes/cachedVisibleNodeCount) is derived
            // from the hierarchy cache, so it must be invalidated whenever the roots/bounds are
            // rebuilt. Otherwise swapping the graph at an unchanged camera (e.g. switching presets)
            // leaves the previous graph's visible set cached and drawn even though the node data
            // and cachedRootNodes have already updated.
            visibleRootsDirty = true;
        }
        NodeGraphHierarchySupport.rebuildHierarchyCacheIfNeeded(
            this,
            hierarchyGeometryDirty,
            nodes,
            cachedRootNodes,
            cachedHierarchyBounds,
            cachedHierarchyNodeCounts
        );
        hierarchyGeometryDirty = false;
    }

    private List<Node> getVisibleRootsForViewport() {
        rebuildHierarchyCacheIfNeeded();

        int viewportWidth = getViewportWorldWidth();
        int viewportHeight = getViewportWorldHeight();
        if (!visibleRootsDirty
            && visibleRootsCameraX == cameraX
            && visibleRootsCameraY == cameraY
            && visibleRootsViewportWidth == viewportWidth
            && visibleRootsViewportHeight == viewportHeight) {
            return cachedVisibleRootNodes;
        }

        cachedVisibleRootNodes.clear();
        cachedVisibleNodeCount = 0;
        for (Node root : cachedRootNodes) {
            SelectionBounds bounds = cachedHierarchyBounds.get(root);
            if (!intersectsViewport(bounds, viewportWidth, viewportHeight)) {
                continue;
            }
            cachedVisibleRootNodes.add(root);
            cachedVisibleNodeCount += cachedHierarchyNodeCounts.getOrDefault(root, 0);
        }

        visibleRootsDirty = false;
        visibleRootsCameraX = cameraX;
        visibleRootsCameraY = cameraY;
        visibleRootsViewportWidth = viewportWidth;
        visibleRootsViewportHeight = viewportHeight;
        return cachedVisibleRootNodes;
    }

    void collectHierarchyNodes(Node node, List<Node> collected, Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        collected.add(node);
        collectHierarchyNodes(node.getAttachedActionNode(), collected, visited);
        collectHierarchyNodes(node.getAttachedSensor(), collected, visited);
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                collectHierarchyNodes(parameter, collected, visited);
            }
        }
    }

    private int getViewportWorldWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getGuiScaledWidth() / Math.max(0.0001f, getZoomScale()));
    }

    private int getViewportWorldHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getGuiScaledHeight() / Math.max(0.0001f, getZoomScale()));
    }

    private boolean intersectsViewport(SelectionBounds bounds) {
        return intersectsViewport(bounds, getViewportWorldWidth(), getViewportWorldHeight());
    }

    private boolean intersectsViewport(SelectionBounds bounds, int viewportWidth, int viewportHeight) {
        if (bounds == null) {
            return false;
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return true;
        }
        int viewportLeft = cameraX - VIEWPORT_CULL_MARGIN;
        int viewportTop = cameraY - VIEWPORT_CULL_MARGIN;
        int viewportRight = cameraX + viewportWidth + VIEWPORT_CULL_MARGIN;
        int viewportBottom = cameraY + viewportHeight + VIEWPORT_CULL_MARGIN;
        return bounds.maxX >= viewportLeft
            && bounds.minX <= viewportRight
            && bounds.maxY >= viewportTop
            && bounds.minY <= viewportBottom;
    }

    private boolean intersectsViewport(Node node) {
        if (node == null) {
            return false;
        }
        return intersectsViewport(new SelectionBounds(
            node.getX(),
            node.getY(),
            node.getX() + node.getWidth(),
            node.getY() + node.getHeight()
        ));
    }

    private void addNodeToSelection(Node node) {
        if (node == null) {
            return;
        }
        if (selectedNodes.add(node)) {
            node.setSelected(true);
            selectedNode = node;
        }
    }

    private void clearSelection() {
        if (selectedNodes.isEmpty()) {
            selectedNode = null;
            return;
        }
        for (Node entry : selectedNodes) {
            entry.setSelected(false);
        }
        selectedNodes.clear();
        selectedNode = null;
    }

    private void pruneSelectionToCurrentNodes() {
        if (selectedNodes.isEmpty()) {
            selectedNode = null;
            return;
        }
        Set<Node> liveNodes = new HashSet<>(nodes);
        boolean changed = false;
        java.util.Iterator<Node> iterator = selectedNodes.iterator();
        while (iterator.hasNext()) {
            Node entry = iterator.next();
            if (entry == null || !liveNodes.contains(entry)) {
                if (entry != null) {
                    entry.setSelected(false);
                }
                iterator.remove();
                changed = true;
            }
        }
        if (selectedNode == null || !liveNodes.contains(selectedNode)) {
            selectedNode = selectedNodes.isEmpty() ? null : selectedNodes.iterator().next();
            changed = true;
        }
        if (changed && selectedNode != null) {
            selectedNode.setSelected(true);
        }
    }

    private void clearTransientGraphState() {
        clearSelection();
        draggingNode = null;
        connectionController.clearGraphState();
        hoveringStartButton = false;
        hoveredStartNode = null;
        startModeDropdown.close();
        sensorDropTarget = null;
        actionDropTarget = null;
        parameterDropTarget = null;
        lastClickedNode = null;
        lastClickTime = 0;
        cascadeDeletionPreviewNodes.clear();
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
    }

    public void beginSelectionBox(int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        selectionBoxActive = true;
        selectionBoxStartWorldX = worldX;
        selectionBoxStartWorldY = worldY;
        selectionBoxCurrentWorldX = worldX;
        selectionBoxCurrentWorldY = worldY;
    }

    public void updateSelectionBox(int screenX, int screenY) {
        if (!selectionBoxActive) {
            return;
        }
        selectionBoxCurrentWorldX = screenToWorldX(screenX);
        selectionBoxCurrentWorldY = screenToWorldY(screenY);
        if (hasSelectionBoxDrag()) {
            applySelectionBoxSelection();
        }
    }

    public void completeSelectionBox() {
        if (!selectionBoxActive) {
            return;
        }
        if (hasSelectionBoxDrag()) {
            applySelectionBoxSelection();
        }
        selectionBoxActive = false;
    }

    public boolean isSelectionBoxActive() {
        return selectionBoxActive;
    }

    private boolean hasSelectionBoxDrag() {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        int deltaX = Math.round(Math.abs(selectionBoxCurrentWorldX - selectionBoxStartWorldX) * scale);
        int deltaY = Math.round(Math.abs(selectionBoxCurrentWorldY - selectionBoxStartWorldY) * scale);
        return deltaX >= SELECTION_BOX_MIN_DRAG || deltaY >= SELECTION_BOX_MIN_DRAG;
    }

    private void applySelectionBoxSelection() {
        int worldLeft = Math.min(selectionBoxStartWorldX, selectionBoxCurrentWorldX);
        int worldRight = Math.max(selectionBoxStartWorldX, selectionBoxCurrentWorldX);
        int worldTop = Math.min(selectionBoxStartWorldY, selectionBoxCurrentWorldY);
        int worldBottom = Math.max(selectionBoxStartWorldY, selectionBoxCurrentWorldY);

        List<Node> inside = new ArrayList<>();
        for (Node node : nodes) {
            if (node == null) {
                continue;
            }
            int nodeLeft = node.getX();
            int nodeRight = node.getX() + node.getWidth();
            int nodeTop = node.getY();
            int nodeBottom = node.getY() + node.getHeight();
            boolean intersecting = nodeRight >= worldLeft &&
                    nodeLeft <= worldRight &&
                    nodeBottom >= worldTop &&
                    nodeTop <= worldBottom;
            if (intersecting) {
                inside.add(node);
            }
        }
        selectNodes(inside);
    }

    public void resetDropTargets() {
        sensorDropTarget = null;
        actionDropTarget = null;
        parameterDropTarget = null;
        parameterDropSlotIndex = null;
    }

    void bringNodeToFront(Node node) {
        NodeGraphHierarchySupport.bringNodeToFront(this, node, nodes);
    }

    Node getRootNode(Node node) {
        Node current = node;
        Node parent;
        while ((parent = getParentForNode(current)) != null) {
            current = parent;
        }
        return current;
    }

    void collectHierarchy(Node node, List<Node> result, Set<Node> visited) {
        NodeGraphHierarchySupport.collectHierarchy(node, result, visited);
    }

    public Node getSelectedNode() {
        return selectedNode;
    }

    public boolean copySelectedNodeToClipboard() {
        pruneSelectionToCurrentNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return false;
        }
        clipboardNodeSnapshot = snapshot;
        return true;
    }

    public boolean cutSelectedNodeToClipboard() {
        pruneSelectionToCurrentNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return false;
        }
        clipboardNodeSnapshot = snapshot;
        return deleteSelectedNode();
    }

    public Node duplicateSelectedNode() {
        pruneSelectionToCurrentNodes();
        if (selectedNodes.isEmpty()) {
            return null;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return null;
        }
        clipboardNodeSnapshot = snapshot;
        pushUndoState();
        SelectionBounds bounds = calculateBounds(selectedNodes);
        int anchorX = bounds != null ? bounds.minX : snapshot.anchorX;
        int anchorY = bounds != null ? bounds.minY : snapshot.anchorY;
        return instantiateClipboardSnapshot(snapshot, anchorX + DUPLICATE_OFFSET_X, anchorY + DUPLICATE_OFFSET_Y);
    }

    public Node pasteClipboardNode() {
        pruneSelectionToCurrentNodes();
        if (clipboardNodeSnapshot == null) {
            return null;
        }
        SelectionBounds bounds = calculateBounds(selectedNodes);
        int baseX = bounds != null ? bounds.minX : clipboardNodeSnapshot.anchorX;
        int baseY = bounds != null ? bounds.minY : clipboardNodeSnapshot.anchorY;
        pushUndoState();
        return instantiateClipboardSnapshot(clipboardNodeSnapshot, baseX + DUPLICATE_OFFSET_X, baseY + DUPLICATE_OFFSET_Y);
    }

    public boolean deleteSelectedNode() {
        pruneSelectionToCurrentNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        List<Node> targets = selectedNodes.stream().filter(node -> !node.isProtectedRoutineEntry()).toList();
        if (targets.isEmpty()) {
            return false;
        }
        pushUndoState();
        for (Node node : targets) {
            removeNodeCascade(node, false);
        }
        clearSelection();
        markWorkspaceDirty();
        return true;
    }

    private boolean isNodeEligibleForConnectionInsertion(Node node) {
        return connectionController.isNodeEligibleForConnectionInsertion(node);
    }

    private NodeConnection findInsertionPreviewConnection(Node node) {
        return connectionController.findInsertionPreviewConnection(node);
    }

    private boolean tryInsertDraggedNodeIntoPreviewConnection(Node node) {
        return connectionController.tryInsertDraggedNodeIntoPreviewConnection(node);
    }

    private boolean insertNodeIntoConnection(Node node, NodeConnection connection) {
        return connectionController.insertNodeIntoConnection(node, connection);
    }

    private ClipboardSnapshot createClipboardSnapshot(Collection<Node> selection) {
        return NodeGraphClipboardSupport.createClipboardSnapshot(this, selection, connections);
    }

    private Node instantiateClipboardSnapshot(ClipboardSnapshot snapshot, int targetAnchorX, int targetAnchorY) {
        return NodeGraphClipboardSupport.instantiateClipboardSnapshot(this, snapshot, targetAnchorX, targetAnchorY, nodes, connections);
    }

    NodeGraphData buildGraphData(Collection<Node> nodeCollection, Collection<NodeConnection> connectionCollection, Set<Node> allowedNodes) {
        return NodeGraphClipboardSupport.buildGraphData(nodeCollection, connectionCollection, allowedNodes);
    }

    private void pushUndoState() {
        NodeGraphHistorySupport.pushUndoState(this, nodes, connections, undoStack, redoStack, suppressUndoCapture, MAX_HISTORY);
    }

    private void pushUndoSnapshot(NodeGraphData snapshot) {
        NodeGraphHistorySupport.pushUndoSnapshot(snapshot, undoStack, redoStack, suppressUndoCapture, MAX_HISTORY);
    }

    void restoreFromSnapshot(NodeGraphData data) {
        if (data == null) {
            return;
        }
        suppressUndoCapture = true;
        applyLoadedData(data);
        suppressUndoCapture = false;
        markWorkspaceDirty();
    }

    public boolean undo() {
        return NodeGraphHistorySupport.undo(this, nodes, connections, undoStack, redoStack);
    }

    public boolean redo() {
        return NodeGraphHistorySupport.redo(this, nodes, connections, undoStack, redoStack);
    }

    public void startDragging(Node node, int mouseX, int mouseY) {
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopMessageEditing(true);
        stopStickyNoteEditing(true);
        stopParameterEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        resetDropTargets();

        if (node == null) {
            pendingDragUndoSnapshot = null;
            dragOperationChanged = false;
            multiDragActive = false;
            multiDragStartPositions.clear();
            return;
        }

        if (suppressUndoCapture) {
            pendingDragUndoSnapshot = null;
        } else {
            pendingDragUndoSnapshot = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        }
        dragOperationChanged = false;

        draggingNode = node;
        draggingNodeStartX = node.getX();
        draggingNodeStartY = node.getY();
        draggingNodeDetached = false;
        node.setDragging(true);

        if (selectedNodes.size() > 1 && selectedNodes.contains(node)) {
            multiDragActive = true;
            multiDragStartPositions.clear();
            for (Node selected : selectedNodes) {
                multiDragStartPositions.put(selected, new DragStartInfo(selected.getX(), selected.getY()));
                if (selected != node) {
                    selected.setDragging(true);
                }
            }
        } else {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        node.setDragOffsetX(worldMouseX - node.getX());
        node.setDragOffsetY(worldMouseY - node.getY());
    }
    
    public void startDraggingConnection(Node node, int socketIndex, boolean isOutput, int mouseX, int mouseY) {
        connectionController.startDraggingConnection(node, socketIndex, isOutput, screenToWorldX(mouseX), screenToWorldY(mouseY));
    }

    private void stopConnectionEditors() {
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopStickyNoteEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);
        stopMessageEditing(true);
    }

    public void updateDrag(int mouseX, int mouseY) {
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        if (connectionController.isConnectionCutActive()) {
            updateConnectionCut(worldMouseX, worldMouseY);
            return;
        }

        if (stickyNoteController.isResizing()) {
            stickyNoteController.updateResize(worldMouseX, worldMouseY);
            return;
        }

        if (draggingNode != null) {
            int newX = worldMouseX - draggingNode.getDragOffsetX();
            int newY = worldMouseY - draggingNode.getDragOffsetY();

            if (multiDragActive) {
                // Apply grid snapping if Shift is held
                if (InputCompatibilityBridge.hasShiftDown()) {
                    newX = snapToGrid(newX);
                    newY = snapToGrid(newY);
                }

                int deltaX = newX - draggingNodeStartX;
                int deltaY = newY - draggingNodeStartY;
                if (deltaX != 0 || deltaY != 0) {
                    dragOperationChanged = true;
                }
                draggingNode.setPosition(newX, newY);
                for (Map.Entry<Node, DragStartInfo> entry : multiDragStartPositions.entrySet()) {
                    Node member = entry.getKey();
                    if (member == null || member == draggingNode) {
                        continue;
                    }
                    DragStartInfo start = entry.getValue();
                    if (start == null) {
                        continue;
                    }
                    member.setPosition(start.x + deltaX, start.y + deltaY);
                }
                invalidateHierarchyCache();
                draggingNode.setSocketsHidden(false);
                resetDropTargets();
                connectionController.setInsertionPreviewConnection(null);
            } else {
                if (!draggingNodeDetached) {
                    if (newX != draggingNodeStartX || newY != draggingNodeStartY) {
                        detachDraggingNodeFromParents();
                    }
                }

                if (draggingNodeDetached) {
                    int currentX = draggingNode.getX();
                    int currentY = draggingNode.getY();
                    if (currentX != newX || currentY != newY) {
                        dragOperationChanged = true;
                    }

                    // Apply grid snapping if Shift is held
                    if (InputCompatibilityBridge.hasShiftDown()) {
                        newX = snapToGrid(newX);
                        newY = snapToGrid(newY);
                    }

                    draggingNode.setPosition(newX, newY);
                    invalidateHierarchyCache();

                    boolean hideSockets = false;
                    resetDropTargets();
                    boolean parameterCandidate = Node.isUsableAsParameterType(draggingNode.getType());
                    if (parameterCandidate) {
                        hideSockets = trySetParameterDropTarget(draggingNode, worldMouseX, worldMouseY, true);
                    }
                    if (!hideSockets && draggingNode.isSensorNode()) {
                        hideSockets = trySetSensorDropTarget(draggingNode, worldMouseX, worldMouseY);
                    }
                    if (!hideSockets && !draggingNode.isSensorNode()) {
                        for (Node node : nodes) {
                            if (!node.canAcceptActionNode() || node == draggingNode) {
                                continue;
                            }
                            if (!node.canAcceptActionNode(draggingNode)) {
                                continue;
                            }
                            if (node.isPointInsideActionSlot(worldMouseX, worldMouseY)) {
                                actionDropTarget = node;
                                hideSockets = true;
                                break;
                            }
                        }
                    }
                    connectionController.setInsertionPreviewConnection(!hideSockets ? findInsertionPreviewConnection(draggingNode) : null);
                    draggingNode.setSocketsHidden(hideSockets);
                } else {
                    connectionController.setInsertionPreviewConnection(null);
                }
            }
        } else {
            connectionController.setInsertionPreviewConnection(null);
        }
        connectionController.updateDrag(worldMouseX, worldMouseY, denseViewportMode);
    }

    public void previewSidebarDrag(NodeType nodeType, int worldMouseX, int worldMouseY) {
        previewSidebarDrag(nodeType != null ? Node.createForEditor(nodeType, worldMouseX, worldMouseY) : null, worldMouseX, worldMouseY);
    }

    public void previewSidebarDrag(Node candidate, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        connectionController.setInsertionPreviewConnection(null);
        if (candidate == null) {
            return;
        }
        positionNewNode(candidate, worldMouseX, worldMouseY);
        NodeType nodeType = candidate.getType();
        boolean parameterCandidate = Node.isUsableAsParameterType(nodeType);
        if (parameterCandidate && trySetParameterDropTarget(candidate, worldMouseX, worldMouseY, false)) {
            return;
        }
        if (Node.isSensorType(nodeType) && trySetSensorDropTarget(null, worldMouseX, worldMouseY)) {
            return;
        }
        if (Node.isParameterType(nodeType)) {
            return;
        } else {
            boolean actionTargetFound = false;
            for (Node node : nodes) {
                if (!node.canAcceptActionNode()) {
                    continue;
                }
                if (!node.canAcceptActionNode(candidate)) {
                    continue;
                }
                if (node.isPointInsideActionSlot(worldMouseX, worldMouseY)) {
                    actionDropTarget = node;
                    actionTargetFound = true;
                    break;
                }
            }
            if (!actionTargetFound) {
                connectionController.setInsertionPreviewConnection(findInsertionPreviewConnection(candidate));
            }
        }
    }

    public int[] getSidebarDragPreviewPosition(Node candidate, int worldMouseX, int worldMouseY) {
        if (candidate == null) {
            return new int[]{worldMouseX, worldMouseY};
        }
        int nodeX = worldMouseX - candidate.getWidth() / 2;
        int nodeY = worldMouseY - candidate.getHeight() / 2;
        if (InputCompatibilityBridge.hasShiftDown()) {
            nodeX = snapToGrid(nodeX);
            nodeY = snapToGrid(nodeY);
        }
        return new int[]{nodeX, nodeY};
    }

    private boolean trySetParameterDropTarget(Node candidate, int worldMouseX, int worldMouseY, boolean excludeCandidateNode) {
        Node hoveredNode = getNodeAtWorldExcluding(worldMouseX, worldMouseY, excludeCandidateNode ? candidate : null);
        for (Node current = hoveredNode; current != null; current = getParentForNode(current)) {
            if (excludeCandidateNode && current == candidate) {
                continue;
            }
            int slotIndex = findPreferredParameterSlot(current, candidate, worldMouseX, worldMouseY, true);
            if (slotIndex >= 0) {
                parameterDropTarget = current;
                parameterDropSlotIndex = slotIndex;
                return true;
            }
            if (current == hoveredNode
                && current.getParentParameterHost() != null
                && current.canAcceptParameter()
                && current.containsPoint(worldMouseX, worldMouseY)) {
                // When the cursor is over a nested parameter host, do not let an
                // ancestor host steal the drop and replace the nested node.
                return false;
            }
        }
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (excludeCandidateNode && node == candidate) {
                continue;
            }
            if (!intersectsViewport(node)) {
                continue;
            }
            int slotIndex = findPreferredParameterSlot(node, candidate, worldMouseX, worldMouseY, false);
            if (slotIndex >= 0) {
                parameterDropTarget = node;
                parameterDropSlotIndex = slotIndex;
                return true;
            }
        }
        return false;
    }

    private Node getNodeAtWorldExcluding(int worldX, int worldY, Node excluded) {
        rebuildHierarchyCacheIfNeeded();
        Set<Node> processedRoots = new HashSet<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            Node root = getRootNode(node);
            if (root == null || processedRoots.contains(root)) {
                continue;
            }
            processedRoots.add(root);
            if (!intersectsViewport(cachedHierarchyBounds.get(root))) {
                continue;
            }
            Node hit = findNodeInHierarchyAtExcluding(root, worldX, worldY, excluded);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Node findNodeInHierarchyAtExcluding(Node node, int worldX, int worldY, Node excluded) {
        if (node == null) {
            return null;
        }

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            keys.sort(Collections.reverseOrder());
            for (Integer key : keys) {
                Node hit = findNodeInHierarchyAtExcluding(parameterMap.get(key), worldX, worldY, excluded);
                if (hit != null) {
                    return hit;
                }
            }
        }

        Node sensorChild = node.getAttachedSensor();
        Node hit = findNodeInHierarchyAtExcluding(sensorChild, worldX, worldY, excluded);
        if (hit != null) {
            return hit;
        }

        Node actionChild = node.getAttachedActionNode();
        hit = findNodeInHierarchyAtExcluding(actionChild, worldX, worldY, excluded);
        if (hit != null) {
            return hit;
        }

        if (node != excluded && isNodeHitAt(node, worldX, worldY)) {
            return node;
        }

        return null;
    }

    private int findPreferredParameterSlot(Node host, Node candidate, int worldMouseX, int worldMouseY, boolean allowBodyFallback) {
        if (host == null || candidate == null || !host.canAcceptParameter()) {
            return -1;
        }

        int hoveredSlotIndex = host.getParameterSlotIndexAt(worldMouseX, worldMouseY);
        if (hoveredSlotIndex >= 0 && host.canAcceptParameterNode(candidate, hoveredSlotIndex)) {
            return hoveredSlotIndex;
        }

        if (!allowBodyFallback || !host.containsPoint(worldMouseX, worldMouseY)) {
            return -1;
        }

        int slotCount = host.getParameterSlotCount();
        int firstCompatible = -1;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (!host.canAcceptParameterNode(candidate, slotIndex)) {
                continue;
            }
            if (firstCompatible < 0) {
                firstCompatible = slotIndex;
            }
            if (host.getAttachedParameter(slotIndex) == null) {
                return slotIndex;
            }
        }
        return firstCompatible;
    }

    private boolean trySetSensorDropTarget(Node candidateToExclude, int worldMouseX, int worldMouseY) {
        for (Node node : nodes) {
            if (!node.canAcceptSensor() || node == candidateToExclude) {
                continue;
            }
            if (!intersectsViewport(node)) {
                continue;
            }
            if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                sensorDropTarget = node;
                return true;
            }
        }
        return false;
    }

    public Node handleSidebarDrop(NodeType nodeType, int worldMouseX, int worldMouseY) {
        return handleSidebarDrop(nodeType != null ? Node.createForEditor(nodeType, 0, 0) : null, worldMouseX, worldMouseY);
    }

    public Node handleSidebarDrop(Node newNode, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        connectionController.setInsertionPreviewConnection(null);
        if (newNode == null) {
            return null;
        }
        if (newNode.getType() == NodeType.ROUTINE_INPUT
            && (activeRoutineWorkspaceId.isBlank() || !activeRoutineWorkspaceId.equals(newNode.getRoutineId()))) {
            return null;
        }
        positionNewNode(newNode, worldMouseX, worldMouseY);
        NodeType nodeType = newNode.getType();
        if (nodeType == NodeType.START) {
            assignNewStartNodeNumber(newNode);
        }

        boolean parameterCandidate = Node.isUsableAsParameterType(nodeType);
        if (parameterCandidate
            && trySetParameterDropTarget(newNode, worldMouseX, worldMouseY, false)
            && parameterDropTarget != null
            && parameterDropSlotIndex != null) {
            nodes.add(newNode);
            parameterDropTarget.attachParameter(newNode, parameterDropSlotIndex);
            markWorkspaceDirty();
            return newNode;
        }
        if (Node.isSensorType(nodeType) && trySetSensorDropTarget(null, worldMouseX, worldMouseY) && sensorDropTarget != null) {
            nodes.add(newNode);
            sensorDropTarget.attachSensor(newNode);
            markWorkspaceDirty();
            return newNode;
        } else {
            for (Node node : nodes) {
                if (!node.canAcceptActionNode()) {
                    continue;
                }
                if (!intersectsViewport(node)) {
                    continue;
                }
                if (!node.canAcceptActionNode(newNode)) {
                    continue;
                }
                if (node.isPointInsideActionSlot(worldMouseX, worldMouseY)) {
                    nodes.add(newNode);
                    node.attachActionNode(newNode);
                    markWorkspaceDirty();
                    return newNode;
                }
            }
        }

        NodeConnection insertionConnection = findInsertionPreviewConnection(newNode);
        nodes.add(newNode);
        if (insertionConnection != null) {
            insertNodeIntoConnection(newNode, insertionConnection);
        }
        markWorkspaceDirty();
        return newNode;
    }
    
    public void updateMouseHover(int mouseX, int mouseY) {
        long startNanos = System.nanoTime();
        List<Node> visibleRoots = getVisibleRootsForViewport();
        // Reset hover state
        connectionController.clearSocketHover();
        hoveringStartButton = false;
        hoveredStartNode = null;

        // Check for start button hover
        for (Node root : visibleRoots) {
            if (root.getType() == NodeType.START && isMouseOverStartButton(root, mouseX, mouseY)) {
                hoveringStartButton = true;
                hoveredStartNode = root;
                break;
            }
        }
        
        // Don't check for socket hover if we're currently dragging a connection
        if (connectionController.isDraggingConnection()) {
            profilerHoverMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            return;
        }

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        boolean socketHovered = connectionController.updateMouseHover(worldMouseX, worldMouseY, denseViewportMode, visibleRoots);
        if (socketHovered && denseViewportMode) {
            return;
        }
        profilerHoverMs = (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    public void stopDragging() {
        Node rootToPromote = null;
        if (stickyNoteController.isResizing()) {
            rootToPromote = stickyNoteController.finishResize();
        }
        if (draggingNode != null) {
            Node node = draggingNode;
            if (multiDragActive) {
                for (Node member : multiDragStartPositions.keySet()) {
                    if (member != null) {
                        member.setDragging(false);
                        member.setSocketsHidden(shouldHideSocketsWhenAttached(member));
                    }
                }
                rootToPromote = getRootNode(node);
            } else if ((node.getType() == NodeType.SENSOR_POSITION_OF
                || node.getType() == NodeType.SENSOR_DISTANCE_BETWEEN
                || node.getType() == NodeType.SENSOR_TARGETED_BLOCK_FACE
                || node.getType() == NodeType.SENSOR_TARGETED_BLOCK
                || node.getType() == NodeType.SENSOR_TARGETED_ENTITY
                || node.getType() == NodeType.SENSOR_CURRENT_GUI
                || node.getType() == NodeType.SENSOR_LOOK_DIRECTION)
                && parameterDropTarget != null
                && parameterDropSlotIndex != null) {
                Node target = parameterDropTarget;
                int slotIndex = parameterDropSlotIndex;
                node.setDragging(false);
                if (!target.attachParameter(node, slotIndex)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = getRootNode(target);
            } else if (parameterDropTarget != null && parameterDropSlotIndex != null) {
                Node target = parameterDropTarget;
                int slotIndex = parameterDropSlotIndex;
                node.setDragging(false);
                if (!target.attachParameter(node, slotIndex)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = getRootNode(target);
            } else if (node.isSensorNode() && sensorDropTarget != null) {
                Node target = sensorDropTarget;
                node.setDragging(false);
                if (!target.attachSensor(node)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = getRootNode(target);
            } else if (!node.isSensorNode() && actionDropTarget != null) {
                Node target = actionDropTarget;
                node.setDragging(false);
                if (!target.attachActionNode(node)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = getRootNode(target);
            } else {
                node.setDragging(false);
                node.setSocketsHidden(shouldHideSocketsWhenAttached(node));
                tryInsertDraggedNodeIntoPreviewConnection(node);
                rootToPromote = getRootNode(node);
            }
        }
        if (rootToPromote != null) {
            bringNodeToFront(rootToPromote);
        }
        draggingNode = null;
        draggingNodeDetached = false;
        resetDropTargets();
        connectionController.setInsertionPreviewConnection(null);

        if (dragOperationChanged) {
            pushUndoSnapshot(pendingDragUndoSnapshot);
            markWorkspaceDirty();
        }
        pendingDragUndoSnapshot = null;
        dragOperationChanged = false;
        if (multiDragActive) {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }
        selectionDeletionPreviewActive = false;
    }

    public void forceClearTransientDragState() {
        for (Node node : nodes) {
            if (node == null) {
                continue;
            }
            node.setDragging(false);
            node.setSocketsHidden(shouldHideSocketsWhenAttached(node));
        }
        draggingNode = null;
        draggingNodeDetached = false;
        stickyNoteController.cancelResize();
        pendingDragUndoSnapshot = null;
        dragOperationChanged = false;
        if (multiDragActive) {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
        connectionController.forceClearTransientDragState();
        resetDropTargets();
    }

    private boolean shouldHideSocketsWhenAttached(Node node) {
        if (node == null) {
            return false;
        }
        return node.isAttachedToControl()
            || node.isAttachedToActionControl()
            || node.getParentParameterHost() != null;
    }

    private void detachDraggingNodeFromParents() {
        if (draggingNode == null || draggingNodeDetached) {
            return;
        }

        if (draggingNode.isSensorNode() && draggingNode.isAttachedToControl()) {
            Node parent = draggingNode.getParentControl();
            if (parent != null) {
                parent.detachSensor();
            }
        }

        if (draggingNode.isAttachedToActionControl()) {
            Node parent = draggingNode.getParentActionControl();
            if (parent != null) {
                parent.detachActionNode();
            }
        }

        if (Node.isUsableAsParameterType(draggingNode.getType())
            && draggingNode.getParentParameterHost() != null) {
            Node parent = draggingNode.getParentParameterHost();
            if (parent != null) {
                parent.detachParameter(draggingNode.getParentParameterSlotIndex());
            }
        }

        draggingNodeDetached = true;
        dragOperationChanged = true;
        invalidateHierarchyCache();
    }

    public void stopDraggingConnection() {
        connectionController.stopDraggingConnection();
    }

    private void addConnectionReplacingConflicts(Node outputNode, Node inputNode, int outputSocket, int inputSocket) {
        connectionController.addConnectionReplacingConflicts(outputNode, inputNode, outputSocket, inputSocket);
    }
    
    public boolean isInSidebar(int mouseX, int sidebarWidth) {
        return mouseX < sidebarWidth;
    }
    
    public boolean isAnyNodeBeingDragged() {
        return draggingNode != null || stickyNoteController.isResizing()
            || connectionController.isDraggingConnection() || connectionController.isConnectionCutActive();
    }

    private boolean isLowDetailModeEnabled() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        return settings != null && Boolean.TRUE.equals(settings.lowDetailMode);
    }

    public void startPanning(int mouseX, int mouseY) {
        isPanning = true;
        panStartX = mouseX;
        panStartY = mouseY;
        panStartCameraX = cameraX;
        panStartCameraY = cameraY;
    }

    /**
     * Shows the context menu at the specified screen position.
     */
    public void showContextMenu(int screenX, int screenY, com.pathmind.ui.sidebar.Sidebar sidebar, int screenWidth, int screenHeight) {
        closeNodeContextMenu();
        if (contextMenu == null) {
            contextMenu = new com.pathmind.ui.menu.ContextMenu(sidebar);
        }
        contextMenu.setScale(getZoomScale());
        // Store the world coordinates where nodes should be created
        contextMenuWorldX = screenToWorldX(screenX);
        contextMenuWorldY = screenToWorldY(screenY);
        contextMenu.setAnchorScreen(screenX, screenY);
        contextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    public void showNodeContextMenu(int screenX, int screenY, Node targetNode, int screenWidth, int screenHeight) {
        closeContextMenu();
        if (nodeContextMenu == null) {
            nodeContextMenu = new com.pathmind.ui.menu.NodeContextMenu();
        }
        nodeContextMenuTarget = targetNode;
        nodeContextMenuWorldX = screenToWorldX(screenX);
        nodeContextMenuWorldY = screenToWorldY(screenY);
        nodeContextMenu.setScale(getZoomScale());
        nodeContextMenu.setAnchorScreen(screenX, screenY);
        nodeContextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    /**
     * Closes the context menu if it's open.
     */
    public void closeContextMenu() {
        if (contextMenu != null) {
            contextMenu.close();
        }
    }

    public void closeNodeContextMenu() {
        if (nodeContextMenu != null) {
            nodeContextMenu.close();
        }
        nodeContextMenuTarget = null;
    }

    /**
     * Returns true if the context menu is open.
     */
    public boolean isContextMenuOpen() {
        return contextMenu != null && contextMenu.isOpen();
    }

    public boolean isNodeContextMenuOpen() {
        return nodeContextMenu != null && nodeContextMenu.isOpen();
    }

    public boolean isStartModeDropdownOpen() {
        return startModeDropdown.isOpen();
    }

    /**
     * Updates the context menu hover state.
     */
    public void updateContextMenuHover(int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(contextMenuWorldX);
            int anchorScreenY = worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(getZoomScale());
            contextMenu.updateHover(mouseX, mouseY);
        }
    }

    public void updateNodeContextMenuHover(int mouseX, int mouseY) {
        if (nodeContextMenu != null && nodeContextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(nodeContextMenuWorldX);
            int anchorScreenY = worldToScreenY(nodeContextMenuWorldY);
            nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            nodeContextMenu.setScale(getZoomScale());
            nodeContextMenu.updateHover(mouseX, mouseY);
        }
    }

    public boolean handleStartModeDropdownClick(int mouseX, int mouseY) {
        return startModeDropdown.handleClick(mouseX, mouseY);
    }

    public void closeStartModeDropdown() {
        startModeDropdown.close();
    }

    /**
     * Handles a click on the context menu. Returns the selected NodeType, or null.
     */
    public ContextMenuSelection handleContextMenuClick(int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(contextMenuWorldX);
            int anchorScreenY = worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(getZoomScale());
            return contextMenu.handleClick(mouseX, mouseY);
        }
        return null;
    }

    public boolean handleNodeContextMenuClick(int mouseX, int mouseY) {
        if (nodeContextMenu == null || !nodeContextMenu.isOpen()) {
            return false;
        }
        int anchorScreenX = worldToScreenX(nodeContextMenuWorldX);
        int anchorScreenY = worldToScreenY(nodeContextMenuWorldY);
        nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
        nodeContextMenu.setScale(getZoomScale());
        com.pathmind.ui.menu.NodeContextMenuAction action = nodeContextMenu.handleClick(mouseX, mouseY);
        if (action == null) {
            closeNodeContextMenu();
            return true;
        }

        if (nodeContextMenuTarget != null && !isNodeSelected(nodeContextMenuTarget)) {
            selectNode(nodeContextMenuTarget);
        }

        switch (action) {
            case COPY:
                copySelectedNodeToClipboard();
                break;
            case DUPLICATE:
                duplicateSelectedNode();
                break;
            case PASTE:
                pasteClipboardNode();
                break;
            case DELETE:
                deleteSelectedNode();
                break;
        }
        closeNodeContextMenu();
        return true;
    }

    /**
     * Renders the context menu.
     */
    public void renderContextMenu(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(contextMenuWorldX);
            int anchorScreenY = worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(getZoomScale());
            contextMenu.render(context, textRenderer, mouseX, mouseY);
        }
    }

    public void renderNodeContextMenu(GuiGraphics context, Font textRenderer) {
        if (nodeContextMenu != null && nodeContextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(nodeContextMenuWorldX);
            int anchorScreenY = worldToScreenY(nodeContextMenuWorldY);
            nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            nodeContextMenu.setScale(getZoomScale());
            nodeContextMenu.render(context, textRenderer);
        }
    }

    public void renderStartModeDropdown(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        startModeDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    /**
     * Adds a node of the specified type at the given world coordinates.
     */
    public Node addNodeAtPosition(NodeType type, int worldX, int worldY) {
        Node node = Node.createForEditor(type, 0, 0);
        positionNewNode(node, worldX, worldY);
        addNode(node);
        selectNode(node);
        return node;
    }

    /**
     * Adds a node from the context menu at the stored right-click position.
     */
    public Node addNodeFromContextMenu(NodeType type) {
        return addNodeAtPosition(type, contextMenuWorldX, contextMenuWorldY);
    }

    public Node addRoutineFromContextMenu(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return null;
        Node node = Node.createRoutineCall(routine, contextMenuWorldX, contextMenuWorldY);
        addNode(node);
        selectNode(node);
        return node;
    }

    private void positionNewNode(Node node, int worldMouseX, int worldMouseY) {
        if (node == null) {
            return;
        }
        int nodeX = worldMouseX - node.getWidth() / 2;
        int nodeY = worldMouseY - node.getHeight() / 2;
        if (InputCompatibilityBridge.hasShiftDown()) {
            nodeX = snapToGrid(nodeX);
            nodeY = snapToGrid(nodeY);
        }
        node.setPosition(nodeX, nodeY);
        invalidateHierarchyCache();
    }

    /**
     * Handles scroll events for the context menu.
     * Returns true if the context menu handled the scroll.
     */
    public boolean handleContextMenuScroll(int mouseX, int mouseY, double amount) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(contextMenuWorldX);
            int anchorScreenY = worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(getZoomScale());
            // Check if mouse is over the menu and handle scroll
            if (contextMenu.isMouseOver(mouseX, mouseY)) {
                contextMenu.handleScroll(amount);
                return true;
            }
        }
        return false;
    }

    public void updatePanning(int mouseX, int mouseY) {
        if (isPanning) {
            int deltaX = mouseX - panStartX;
            int deltaY = mouseY - panStartY;
            float scale = getZoomScale();
            if (scale == 0.0f) {
                scale = 1.0f;
            }
            cameraX = panStartCameraX - Math.round(deltaX / scale); // Flip horizontal panning
            cameraY = panStartCameraY - Math.round(deltaY / scale); // Flip vertical panning
            cacheSessionViewportState();
        }
    }
    
    public void stopPanning() {
        isPanning = false;
    }
    
    public boolean isPanning() {
        return isPanning;
    }
    
    public void resetCamera() {
        cameraX = 0;
        cameraY = 0;
        zoomLevel = ZoomLevel.FOCUSED;
        zoomScale = ZoomLevel.FOCUSED.getScale();
        cacheSessionViewportState();
    }

    public void restoreSessionViewportState() {
        SessionViewportState state = SESSION_VIEWPORT_STATES.get(activePreset);
        if (state == null) {
            return;
        }
        cameraX = state.cameraX;
        cameraY = state.cameraY;
        zoomLevel = state.zoomLevel != null ? state.zoomLevel : ZoomLevel.FOCUSED;
        zoomScale = state.zoomScale > 0.0f ? state.zoomScale : zoomLevel.getScale();
        updateZoomLevelFromScale();
    }

    public void persistSessionViewportState() {
        cacheSessionViewportState();
    }

    private void cacheSessionViewportState() {
        if (activePreset == null || activePreset.isEmpty()) {
            return;
        }
        SESSION_VIEWPORT_STATES.put(activePreset, new SessionViewportState(cameraX, cameraY, zoomLevel, zoomScale));
    }

    public boolean focusNodeById(String nodeId, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        for (Node node : nodes) {
            if (node != null && nodeId.equals(node.getId())) {
                focusNode(node, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
                return true;
            }
        }
        return false;
    }

    public void focusNode(Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        if (node == null) {
            return;
        }
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopMessageEditing(true);
        stopParameterEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        clearSelection();
        selectNode(node);

        int workspaceLeft = Math.max(0, sidebarWidth);
        int workspaceTop = Math.max(0, titleBarHeight);
        int workspaceWidth = Math.max(1, screenWidth - workspaceLeft);
        int workspaceHeight = Math.max(1, screenHeight - workspaceTop);
        float scale = getZoomScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        int desiredScreenX = workspaceLeft + workspaceWidth / 2 - Math.round(node.getWidth() * scale / 2f);
        int desiredScreenY = workspaceTop + workspaceHeight / 2 - Math.round(node.getHeight() * scale / 2f);
        cameraX = node.getX() - Math.round((desiredScreenX - workspaceLeft) / scale);
        cameraY = node.getY() - Math.round((desiredScreenY - workspaceTop) / scale);
        cacheSessionViewportState();
    }

    public boolean focusBestMatchingNode(String query, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        Node match = findBestMatchingNode(query);
        if (match == null) {
            return false;
        }
        focusNode(match, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
        return true;
    }

    public String getBestMatchingNodeLabel(String query) {
        Node match = findBestMatchingNode(query);
        if (match == null || match.getType() == null) {
            return null;
        }
        return match.getType().getDisplayName();
    }

    private Node findBestMatchingNode(String query) {
        if (query == null) {
            return null;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        Node bestNode = null;
        int bestScore = 0;
        for (Node node : nodes) {
            if (node == null || node.getType() == null) {
                continue;
            }
            int score = scoreNodeSearch(node, normalizedQuery);
            if (score > bestScore) {
                bestScore = score;
                bestNode = node;
            }
        }
        return bestNode;
    }

    private int scoreNodeSearch(Node node, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreSearchCandidate(node.getType().getDisplayName(), query));
        if (node.getMode() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getMode().getDisplayName(), query) - 20);
        }
        if (node.getId() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getId(), query) - 40);
        }
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

        int fuzzyScore = fuzzySubsequenceScore(normalizedCandidate, query);
        return fuzzyScore > 0 ? 300 + fuzzyScore : 0;
    }

    private int fuzzySubsequenceScore(String candidate, String query) {
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
    
    // Convert screen coordinates to world coordinates
    public int screenToWorldX(int screenX) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return cameraX + Math.round(screenX / scale);
    }
    
    public int screenToWorldY(int screenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return cameraY + Math.round(screenY / scale);
    }

    private int screenToUiX(int screenX) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return Math.round(screenX / scale);
    }

    private int screenToUiY(int screenY) {
        float scale = getZoomScale();
        if (scale == 0.0f) {
            scale = 1.0f;
        }
        return Math.round(screenY / scale);
    }
    
    // Convert world coordinates to screen coordinates
    public int worldToScreenX(int worldX) {
        return Math.round((worldX - cameraX) * getZoomScale());
    }
    
    public int worldToScreenY(int worldY) {
        return Math.round((worldY - cameraY) * getZoomScale());
    }

    /**
     * Snaps a world coordinate to the nearest grid point.
     * @param worldCoord The world coordinate to snap
     * @return The snapped coordinate
     */
    private int snapToGrid(int worldCoord) {
        return Math.round((float) worldCoord / GRID_SNAP_SIZE) * GRID_SNAP_SIZE;
    }

    public void deleteNodeIfInSidebar(Node node, int mouseX, int sidebarWidth) {
        // Use the same logic as the grey-out function - more than halfway over the sidebar
        // Calculate the node's screen position (same as in renderNode)
        int nodeScreenX = node.getX() - cameraX;
        if (isNodeOverSidebar(node, sidebarWidth, nodeScreenX, node.getWidth())) {
            if (shouldCascadeDelete(node)) {
                removeNodeCascade(node);
            } else {
                removeNode(node);
            }
        }
    }

    private void removeNodeCascade(Node node) {
        removeNodeCascade(node, true);
    }

    private void removeNodeCascade(Node node, boolean captureUndo) {
        if (node == null || node.isProtectedRoutineEntry()) {
            return;
        }
        if (node == null) {
            return;
        }
        if (captureUndo) {
            pushUndoState();
        }
        List<Node> removalOrder = new ArrayList<>();
        collectNodesForCascade(node, removalOrder, new HashSet<>());
        for (Node toRemove : removalOrder) {
            boolean shouldReconnect = toRemove == node;
            removeNodeInternal(toRemove, shouldReconnect, false);
        }
        markWorkspaceDirty();
    }

    private void collectNodesForCascade(Node node, List<Node> order, Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }

        if (node.hasAttachedSensor()) {
            collectNodesForCascade(node.getAttachedSensor(), order, visited);
        }
        if (node.hasAttachedActionNode()) {
            collectNodesForCascade(node.getAttachedActionNode(), order, visited);
        }
        if (node.hasAttachedParameter()) {
            for (Node parameter : node.getAttachedParameters().values()) {
                collectNodesForCascade(parameter, order, visited);
            }
        }

        order.add(node);
    }

    private boolean shouldCascadeDelete(Node node) {
        if (node == null) {
            return false;
        }
        return node.hasAttachedSensor() || node.hasAttachedActionNode() || node.hasAttachedParameter();
    }
    
    public boolean isNodeOverSidebar(Node node, int sidebarWidth) {
        if (node == null) {
            return false;
        }
        int screenX = worldToScreenX(node.getX());
        double scaledCenter = screenX + (node.getWidth() * getZoomScale()) / 2.0;
        return scaledCenter < sidebarWidth;
    }
    
    public boolean isNodeOverSidebar(Node node, int sidebarWidth, int screenX, int screenWidth) {
        double scaledCenter = (screenX + screenWidth / 2.0) * getZoomScale();
        return scaledCenter < sidebarWidth;
    }

    public boolean isSelectionOverSidebar(int sidebarWidth) {
        if (selectedNodes == null || selectedNodes.isEmpty()) {
            return false;
        }
        List<Node> draggedNodes = new ArrayList<>();
        for (Node node : selectedNodes) {
            if (node != null && node.isDragging()) {
                draggedNodes.add(node);
            }
        }
        if (draggedNodes.isEmpty()) {
            return false;
        }
        if (draggedNodes.size() == 1) {
            return isNodeOverSidebar(draggedNodes.get(0), sidebarWidth);
        }
        SelectionBounds bounds = calculateBounds(draggedNodes);
        if (bounds == null) {
            return false;
        }
        int left = bounds.minX - cameraX;
        int right = bounds.maxX - cameraX;
        double scaledCenter = (left + (right - left) / 2.0) * getZoomScale();
        return scaledCenter < sidebarWidth;
    }
    
    public boolean tryConnectToSocket(Node targetNode, int targetSocket, boolean isInput) {
        return connectionController.tryConnectToSocket(targetNode, targetSocket, isInput);
    }
    
    public NodeConnection getConnectionAt(int mouseX, int mouseY) {
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        return connectionController.getConnectionAt(worldX, worldY);
    }

    public void startConnectionCut(int mouseX, int mouseY) {
        connectionController.startConnectionCut(screenToWorldX(mouseX), screenToWorldY(mouseY));
    }

    public void updateConnectionCut(int worldX, int worldY) {
        connectionController.updateConnectionCut(worldX, worldY);
    }

    public boolean stopConnectionCut() {
        return connectionController.stopConnectionCut();
    }

    public void cancelConnectionCut() {
        connectionController.cancelConnectionCut();
    }

    public boolean removeConnection(NodeConnection connection) {
        return connectionController.removeConnection(connection);
    }

    public boolean isConnectionCutActive() {
        return connectionController.isConnectionCutActive();
    }

    public boolean hasConnectionCutMoved() {
        return connectionController.hasConnectionCutMoved();
    }

    public void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        long totalStartNanos = !onlyDragged ? System.nanoTime() : 0L;
        flushDeferredStickyNoteSaveIfDue();
        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, getZoomScale(), getZoomScale());
        if (onlyDragged) {
            // Keep active drags above border/line layers while they are in motion.
            MatrixStackBridge.translateZ(matrices, 250.0f);
        }

        if (!onlyDragged) {
            updateCascadeDeletionPreview();
        }
        List<Node> visibleRoots = getVisibleRootsForViewport();
        visibleNodeCountForFrame = cachedVisibleNodeCount;
        if (!onlyDragged) {
            profilerVisibleRoots = visibleRoots.size();
            profilerVisibleNodes = cachedVisibleNodeCount;
        }
        if (!onlyDragged) {
            runtimeVariableNamesFrameCache.clear();
        }
        compactViewportMode = isLowDetailModeEnabled();
        denseViewportMode = false;
        boolean renderConnectionsOnTop = shouldRenderConnectionsOnTop();
        int drawnConnections = 0;
        if (!renderConnectionsOnTop) {
            drawnConnections += connectionRenderer.renderConnections(context, onlyDragged, !onlyDragged);
        }

        Set<Node> renderedNodes = new HashSet<>();
        long nodesStartNanos = !onlyDragged ? System.nanoTime() : 0L;

        for (Node root : visibleRoots) {
            nodeRenderer.renderHierarchy(root, context, textRenderer, mouseX, mouseY, delta, onlyDragged, false, renderedNodes);
        }
        if (!onlyDragged) {
            profilerNodeMs = (System.nanoTime() - nodesStartNanos) / 1_000_000.0;
            profilerDrawnNodes = renderedNodes.size();
        }

        long dropdownStartNanos = !onlyDragged ? System.nanoTime() : 0L;
        if (!onlyDragged) {
            renderParameterDropdownList(context, textRenderer, mouseX, mouseY);
            renderRandomRoundingDropdownList(context, textRenderer, mouseX, mouseY);
            renderModeDropdownList(context, textRenderer, mouseX, mouseY);
            profilerDropdownMs = (System.nanoTime() - dropdownStartNanos) / 1_000_000.0;
        }

        if (renderConnectionsOnTop) {
            drawnConnections += connectionRenderer.renderConnections(context, onlyDragged, !onlyDragged);
        }
        if (!onlyDragged) {
            profilerDrawnConnections = drawnConnections;
            profilerRenderMs = (System.nanoTime() - totalStartNanos) / 1_000_000.0;
        }

        if (!onlyDragged) {
            DrawContextBridge.startNewRootLayer(context);
            nodeControls.renderRuntimeScopeTooltip(context, textRenderer, mouseX, mouseY);
        }
        MatrixStackBridge.pop(matrices);
        compactViewportMode = false;
        denseViewportMode = false;
        visibleNodeCountForFrame = 0;
    }

    private boolean shouldRenderConnectionsOnTop() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        return settings != null && Boolean.TRUE.equals(settings.renderConnectionsOnTop);
    }

    public PerformanceSnapshot getPerformanceSnapshot() {
        return new PerformanceSnapshot(
            profilerRenderMs,
            profilerNodeMs,
            profilerConnectionMs,
            profilerDropdownMs,
            profilerHoverMs,
            profilerHitTestAvgMs,
            profilerHitTestAvgRoots,
            profilerVisibleNodes,
            profilerDrawnNodes,
            profilerVisibleRoots,
            profilerDrawnConnections
        );
    }

    public void renderProfilerOverlay(GuiGraphics context, Font textRenderer) {
        PerformanceSnapshot snapshot = getPerformanceSnapshot();
        List<String> lines = List.of(
            String.format(Locale.ROOT, "render %.2f ms", snapshot.renderMs()),
            String.format(Locale.ROOT, "nodes %.2f ms (%d visible, %d drawn, %d roots)", snapshot.nodeMs(), snapshot.visibleNodes(), snapshot.drawnNodes(), snapshot.visibleRoots()),
            String.format(Locale.ROOT, "connections %.2f ms (%d drawn)", snapshot.connectionMs(), snapshot.drawnConnections()),
            String.format(Locale.ROOT, "dropdowns %.2f ms", snapshot.dropdownMs()),
            String.format(Locale.ROOT, "hover %.2f ms", snapshot.hoverMs()),
            String.format(Locale.ROOT, "hit-test %.2f ms (%.1f roots/call)", snapshot.hitTestAvgMs(), snapshot.hitTestAvgRoots())
        );
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, textRenderer.width(line));
        }
        int lineHeight = textRenderer.lineHeight + 2;
        int overlayWidth = maxWidth + PROFILER_OVERLAY_PADDING * 2;
        int overlayHeight = lines.size() * lineHeight + PROFILER_OVERLAY_PADDING * 2;
        int overlayX = PROFILER_OVERLAY_MARGIN;
        int overlayY = PROFILER_OVERLAY_MARGIN;
        context.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + overlayHeight, 0xD0101010);
        DrawContextBridge.drawBorder(context, overlayX, overlayY, overlayWidth, overlayHeight, 0xFF505050);
        int textY = overlayY + PROFILER_OVERLAY_PADDING;
        for (String line : lines) {
            context.drawString(textRenderer, Component.literal(line), overlayX + PROFILER_OVERLAY_PADDING, textY, 0xFFFFFFFF);
            textY += lineHeight;
        }
    }

    public record PerformanceSnapshot(
        double renderMs,
        double nodeMs,
        double connectionMs,
        double dropdownMs,
        double hoverMs,
        double hitTestAvgMs,
        double hitTestAvgRoots,
        int visibleNodes,
        int drawnNodes,
        int visibleRoots,
        int drawnConnections
    ) {
    }

    public void renderScreenCoordinateCaptureOverlay(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        screenCoordinateCapture.renderOverlay(context, textRenderer, mouseX, mouseY);
    }

    public void renderSelectionBox(GuiGraphics context) {
        if (!selectionBoxActive || !hasSelectionBoxDrag()) {
            return;
        }
        int left = worldToScreenX(Math.min(selectionBoxStartWorldX, selectionBoxCurrentWorldX));
        int right = worldToScreenX(Math.max(selectionBoxStartWorldX, selectionBoxCurrentWorldX));
        int top = worldToScreenY(Math.min(selectionBoxStartWorldY, selectionBoxCurrentWorldY));
        int bottom = worldToScreenY(Math.max(selectionBoxStartWorldY, selectionBoxCurrentWorldY));
        if (left == right || top == bottom) {
            return;
        }
        int fillColor = UITheme.NODE_SELECTION_FILL;
        int borderColor = UITheme.NODE_SELECTION_BORDER;
        context.fill(left, top, right, bottom, fillColor);
        DrawContextBridge.drawBorderInLayer(context, left, top, right - left, bottom - top, borderColor);
    }

    private Node getParentForNode(Node node) {
        if (node == null) {
            return null;
        }
        if (node.isParameterNode()) {
            return node.getParentParameterHost();
        }
        if (node.isSensorNode()) {
            return node.getParentControl();
        }
        if (node.isAttachedToActionControl()) {
            return node.getParentActionControl();
        }
        return null;
    }

    boolean shouldRenderConnectionInDraggedPass(NodeConnection connection) {
        return connectionRenderer.shouldRenderConnectionInDraggedPass(connection);
    }

    private boolean rendersInlineParameters(Node node) {
        if (node == null) {
            return false;
        }
        if (denseViewportMode) {
            return false;
        }
        if (node.shouldRenderInlineParameters()) {
            return true;
        }
        return node.isParameterNode()
            && node.getType() != NodeType.ROUTINE_INPUT
            && node.getType() != NodeType.CREATE_LIST
            && node.getType() != NodeType.LIST_LENGTH
            && node.getType() != NodeType.OPERATOR_MOD
            && node.getType() != NodeType.PARAM_DURATION
            && node.getType() != NodeType.SENSOR_POSITION_OF
            && node.getType() != NodeType.SENSOR_LOOK_DIRECTION
            && node.getType() != NodeType.SENSOR_DISTANCE_BETWEEN
            && node.getType() != NodeType.SENSOR_CURRENT_GUI
            && node.getType() != NodeType.SENSOR_SLOT_ITEM_COUNT;
    }

    private boolean shouldRenderNodeSockets(Node node) {
        if (node == null || !node.shouldRenderSockets()) {
            return false;
        }
        if (!denseViewportMode && !compactViewportMode) {
            return true;
        }
        return node.isSelected()
            || node.isDragging()
            || node == connectionController.getConnectionSourceNode()
            || node == connectionController.getHoveredSocketNode()
            || node == connectionController.getHoveredNode();
    }

    private void renderNodeContent(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY,
                                   int x, int y, int width, int height, boolean isOverSidebar,
                                   boolean simpleStyle, boolean lowDetail) {
        // Render node content based on type
        if (node.getType() == NodeType.START) {
            nodeRenderer.renderStartContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (!simpleStyle
            && (node.getType() == NodeType.EVENT_FUNCTION || node.getType() == NodeType.ROUTINE_ENTRY)) {
            nodeRenderer.renderEventDefinitionContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.VARIABLE || node.getType() == NodeType.ROUTINE_INPUT) {
            nodeRenderer.renderVariableContent(context, textRenderer, node, isOverSidebar,
                x, y, width, height, lowDetail);
        } else if (!simpleStyle && nodeControls.isComparisonOperator(node) && !node.isExpandableBooleanOperator()) {
            nodeRenderer.renderComparisonContent(context, textRenderer, node, isOverSidebar,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.EVENT_CALL) {
            nodeRenderer.renderEventCallContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.TEMPLATE) {
            templateNodeRenderer.render(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else {
            if (rendersInlineParameters(node)) {
                nodeRenderer.renderInlineParameterContent(context, textRenderer, node, isOverSidebar,
                    mouseX, mouseY, x, y, width, height);
            } else {
                if (node.hasStopTargetInputField()) {
                    renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    renderSchematicDropdownField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasVariableInputField()) {
                    renderVariableInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.showsModeFieldAboveParameterSlot()) {
                    renderModeField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasParameterSlot()) {
                    int slotCount = node.getParameterSlotCount();
                    for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                        nodeControls.renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
                    }
                    if (node.hasCoordinateInputFields()) {
                        renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    if (node.hasAmountInputField()) {
                        renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                } else if (node.hasCoordinateInputFields()) {
                    renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                } else if (node.hasAmountInputField()) {
                    renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasMessageInputFields()) {
                    renderMessageInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    if (node.hasMessageScopeToggle()) {
                        nodeControls.renderMessageScopeToggle(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    nodeControls.renderMessageButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.isExpandableBooleanOperator()) {
                    nodeControls.renderBooleanOperatorButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasBookTextInput()) {
                    nodeControls.renderBookTextInput(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    renderSchematicDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (isPresetSelectorNode(node)) {
                    renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
            }

            if (node.hasBooleanToggle()) {
                nodeControls.renderBooleanToggleButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            }
            if (node.hasSensorSlot()) {
                nodeControls.renderSensorSlot(context, textRenderer, node, isOverSidebar);
            }
            if (node.hasActionSlot()) {
                nodeControls.renderActionSlot(context, textRenderer, node, isOverSidebar);
            }
        }
        if (node.supportsRuntimeValueScope()) {
            nodeControls.renderRuntimeScopeButton(context, node, isOverSidebar, mouseX, mouseY);
        }
        if (hasRunPresetSelection(node)) {
            renderRunPresetOpenButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    }

    private boolean hasRunPresetSelection(Node node) {
        return node != null && node.getType() == NodeType.RUN_PRESET
            && !getSelectedPresetName(node).isBlank();
    }

    private int getRunPresetOpenButtonWorldX(Node node) {
        return node.getX() + node.getWidth() - NODE_HEADER_BUTTON_SIZE - 2;
    }

    private int getRunPresetOpenButtonWorldY(Node node) {
        return node.getY() + 2;
    }

    private void renderRunPresetOpenButton(GuiGraphics context, Font textRenderer, Node node,
                                           boolean dimmed, int mouseX, int mouseY) {
        nodeControls.renderNodeHeaderTextButton(context, textRenderer, getRunPresetOpenButtonWorldX(node),
            getRunPresetOpenButtonWorldY(node), NODE_HEADER_BUTTON_SIZE, "↗", dimmed, true,
            nodeControls.getSelectedNodeAccentColor(), mouseX, mouseY);
    }







































    private void renderModeField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                 int mouseX, int mouseY) {
        if (node == null || !node.showsModeFieldAboveParameterSlot()) {
            return;
        }
        int fieldLeft = node.getModeFieldLeft() - cameraX;
        int fieldTop = node.getModeFieldTop() - cameraY;
        int fieldWidth = node.getModeFieldWidth();
        int fieldHeight = node.getModeFieldHeight();
        String labelText = node.getModeFieldLabelText();
        String modeValue = node.getMode() != null ? node.getMode().getDisplayName() : "Select Mode";
        renderDropdownSelectorField(
            context, textRenderer, node, isOverSidebar, mouseX, mouseY,
            fieldLeft, fieldTop, fieldWidth, fieldHeight,
            labelText, true, modeValue
        );
    }

    private void renderDropdownSelectorField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                             int mouseX, int mouseY, int fieldLeft, int fieldTop, int fieldWidth,
                                             int fieldHeight, String label, boolean includeValue, String value) {
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int worldFieldLeft = fieldLeft + cameraX;
        int worldFieldTop = fieldTop + cameraY;
        boolean hovered = !isOverSidebar
            && worldMouseX >= worldFieldLeft
            && worldMouseX <= worldFieldLeft + fieldWidth
            && worldMouseY >= worldFieldTop
            && worldMouseY <= worldFieldTop + fieldHeight;
        boolean open = modeDropdown.isOpen() && modeDropdown.getNode() == node;

        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#selector:" + fieldLeft + ":" + fieldTop, hovered || open);
        int accentColor = isOverSidebar ? nodeControls.toGrayscale(nodeControls.getSelectedNodeAccentColor(), 0.8f) : nodeControls.getSelectedNodeAccentColor();
        UIStyleHelper.FieldPalette palette;
        if (compactViewportMode && !isOverSidebar) {
            palette = new UIStyleHelper.FieldPalette(
                open ? UITheme.BACKGROUND_INPUT : UITheme.BACKGROUND_SECONDARY,
                open ? accentColor : UITheme.BORDER_DEFAULT,
                UITheme.PANEL_INNER_BORDER,
                UITheme.TEXT_PRIMARY,
                UITheme.TEXT_TERTIARY
            );
        } else {
            palette = UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);
        }
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : palette.textColor();
        int labelColor = includeValue && !isOverSidebar && !(hovered || open)
            ? UITheme.NODE_LABEL_COLOR
            : textColor;

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            getLowDetailAwareFieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                textColor,
                palette.placeholderColor(),
                isOverSidebar
            )
        );

        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
        int textX = fieldLeft + 4;
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;

        if (includeValue) {
            drawNodeText(context, textRenderer, Component.literal(label), textX, textY, labelColor);
            int valueStartX = textX + textRenderer.width(label) + 6;
            int maxValueWidth = Math.max(0, chevronCenterX - 5 - valueStartX);
            String displayValue = trimTextToWidth(value != null ? value : "", textRenderer, maxValueWidth);
            drawNodeText(context, textRenderer, Component.literal(displayValue), valueStartX, textY, textColor);
        } else {
            int maxLabelWidth = Math.max(0, fieldWidth - 20);
            String displayLabel = trimTextToWidth(label != null ? label : "", textRenderer, maxLabelWidth);
            drawNodeText(context, textRenderer, Component.literal(displayLabel), textX, textY, textColor);
        }

        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, textColor);
    }

    private float getAnimatedHoverProgress(Object key, boolean highlighted) {
        if (compactViewportMode) {
            return 0f;
        }
        return HoverAnimator.getProgress(key, highlighted, UITheme.HOVER_ANIM_MS);
    }

    private float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active) {
        return active ? 1f : getAnimatedHoverProgress(key, hovered);
    }

    private UIStyleHelper.FieldPalette getNodeInputPalette(boolean isOverSidebar, int accentColor, float progress, boolean active, boolean disabled) {
        if (compactViewportMode && !isOverSidebar) {
            return new UIStyleHelper.FieldPalette(
                active ? UITheme.BACKGROUND_INPUT : UITheme.BACKGROUND_SECONDARY,
                active ? accentColor : UITheme.BORDER_DEFAULT,
                active ? accentColor : UITheme.BORDER_DEFAULT,
                active ? UITheme.TEXT_EDITING : UITheme.TEXT_PRIMARY,
                UITheme.TEXT_TERTIARY
            );
        }
        UIStyleHelper.FieldPalette palette = UIStyleHelper.getInputFieldPalette(accentColor, progress, active, disabled);
        if (!isOverSidebar) {
            return palette;
        }
        return new UIStyleHelper.FieldPalette(
            active ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECONDARY,
            active ? accentColor : UITheme.BORDER_SUBTLE,
            UITheme.PANEL_INNER_BORDER,
            active ? UITheme.TEXT_EDITING : UITheme.TEXT_TERTIARY,
            disabled ? UITheme.TEXT_TERTIARY : palette.placeholderColor()
        );
    }

    private UIStyleHelper.FieldPalette getLowDetailAwareFieldPalette(int backgroundColor, int borderColor, int innerBorderColor,
                                                                     int textColor, int placeholderColor, boolean isOverSidebar) {
        if (compactViewportMode && !isOverSidebar) {
            innerBorderColor = borderColor;
        }
        return new UIStyleHelper.FieldPalette(backgroundColor, borderColor, innerBorderColor, textColor, placeholderColor);
    }













































    public boolean handleBooleanModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findBooleanModeNodeAt(worldX, worldY);
        if (!nodeControls.isCombinedBooleanNode(node)) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = nodeControls.getBooleanModeTabTop(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        if (worldX < fieldLeft || worldX > fieldLeft + fieldWidth
            || worldY < fieldTop || worldY > fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
            return false;
        }

        boolean literalMode = worldX < fieldLeft + fieldWidth / 2;
        if (isEditingParameterField()) {
            stopParameterEditing(true);
        }
        if (node.isBooleanModeLiteral() != literalMode) {
            node.setBooleanModeLiteral(literalMode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
        return true;
    }



































    private String[] getCoordinateAxes(Node node) {
        if (node == null) {
            return new String[0];
        }
        return node.getCoordinateFieldAxes();
    }

    private void renderScreenCoordinatePickerButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                                    int mouseX, int mouseY) {
        if (node == null || !node.hasScreenCoordinatePickerButton()) {
            return;
        }
        int buttonLeft = node.getScreenCoordinatePickerButtonLeft() - cameraX;
        int buttonTop = node.getScreenCoordinatePickerButtonTop() - cameraY;
        int buttonWidth = node.getScreenCoordinatePickerButtonWidth();
        int buttonHeight = node.getScreenCoordinatePickerButtonHeight();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getScreenCoordinatePickerButtonLeft()
            && worldMouseX <= node.getScreenCoordinatePickerButtonLeft() + buttonWidth
            && worldMouseY >= node.getScreenCoordinatePickerButtonTop()
            && worldMouseY <= node.getScreenCoordinatePickerButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (isScreenCoordinateCaptureActiveFor(node)) {
            buttonFill = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = nodeControls.getSelectedNodeAccentColor();
        } else if (hovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = nodeControls.getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = isScreenCoordinateCaptureActiveFor(node) ? "Click To Set" : "Pick";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + Math.max(0, (buttonWidth - textRenderer.width(buttonLabel)) / 2);
        int textY = buttonTop + (buttonHeight - textRenderer.lineHeight) / 2;
        drawNodeText(context, textRenderer, Component.literal(buttonLabel), textX, textY, textColor);
    }

    private void renderCoordinateInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                             int mouseX, int mouseY) {
        inlineFieldRenderer.renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                        int mouseX, int mouseY) {
        inlineFieldRenderer.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderRandomRoundingField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar) {
        randomRounding.renderField(context, textRenderer, node, isOverSidebar);
    }

    private void renderRandomRoundingDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        randomRounding.renderDropdown(context, textRenderer, mouseX, mouseY);
    }

    private void renderMessageInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        inlineFieldRenderer.renderMessageInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderEventNamePreview(GuiGraphics context, Font textRenderer, String value, int x, int y,
                                        int baseColor, int maxWidth) {
        if (value == null || value.isEmpty()) {
            drawNodeText(context, textRenderer, Component.translatable("pathmind.field.enterName"), x, y, baseColor);
            return;
        }
        if (textRenderer.width(value) <= maxWidth) {
            drawNodeText(context, textRenderer, Component.literal(value), x, y, baseColor);
            return;
        }

        String trimmed = trimTextToWidth(value, textRenderer, maxWidth);
        drawNodeText(context, textRenderer, Component.literal(trimmed), x, y, baseColor);
        int trimmedWidth = textRenderer.width(trimmed);

        String tail = "..";
        int tailWidth = textRenderer.width(tail);
        if (trimmedWidth + tailWidth + 4 >= maxWidth) {
            return;
        }

        String suffix = value.substring(Math.max(0, value.length() - 4));
        String tailText = tail + suffix;
        int tailTextWidth = textRenderer.width(tailText);
        if (trimmedWidth + tailTextWidth + 4 > maxWidth) {
            return;
        }
        int tailX = x + maxWidth - tailTextWidth;
        int hintColor = nodeControls.toGrayscale(baseColor, 0.85f);
        drawNodeText(context, textRenderer, Component.literal(tailText), tailX, y, hintColor);
    }

    private boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames) {
        return shouldBuildInlineExpressionRender(rawText, variableNames, false);
    }

    private boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames, boolean allowRelativeMarker) {
        return InlineVariableRenderer.shouldBuildInlineExpressionRender(
            compactViewportMode, rawText, variableNames, allowRelativeMarker);
    }

    static boolean isInlineArithmeticOperatorAt(String text, int index) {
        return InlineVariableRenderer.isInlineArithmeticOperatorAt(text, index);
    }

    private boolean supportsRelativeInlineParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        String parameterName = parameter.getName();
        return RelativeInputSupport.supportsRelativeCoordinate(node, parameterName)
            || RelativeInputSupport.supportsRelativeLook(node, parameterName);
    }

    /** Returns true if value is empty or a valid arithmetic expression using numbers and/or known $variable references. */
    private boolean isNumericOrVariableReference(String value, Node node, boolean allowDecimal, boolean requireCoordinateValid) {
        if (value == null) {
            value = "";
        }
        value = value.trim();
        if (value.isEmpty()) {
            return true;
        }
        if (requireCoordinateValid && "-".equals(value)) {
            return true;
        }
        return isValidNumericExpression(value, collectRuntimeVariableNames(node), allowDecimal, requireCoordinateValid);
    }

    private boolean isValidNumericExpression(String value, Set<String> variableNames, boolean allowDecimal, boolean requireCoordinateValid) {
        return NumericExpressionValidator.isValid(value, variableNames, allowDecimal, requireCoordinateValid);
    }

    private Set<String> collectRuntimeVariableNames(Node node) {
        Node startNode = node != null ? node.getOwningStartNode() : null;
        String startId = startNode != null ? startNode.getId() : "";
        Set<String> cached = runtimeVariableNamesFrameCache.get(startId);
        if (cached != null) {
            return cached;
        }
        Set<String> names = new HashSet<>(getBaseRuntimeVariableNames());
        ExecutionManager manager = ExecutionManager.getInstance();
        List<ExecutionManager.RuntimeVariableEntry> entries = manager.getRuntimeVariableEntries();
        if (!entries.isEmpty()) {
            String effectiveStartId = startNode != null ? startNode.getId() : null;
            for (ExecutionManager.RuntimeVariableEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                if (entry.getScope() != RuntimeValueScope.GLOBAL
                    && effectiveStartId != null && !effectiveStartId.equals(entry.getStartNodeId())) {
                    continue;
                }
                String name = entry.getName();
                if (name != null) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        names.add(trimmed);
                    }
                }
            }
        }
        runtimeVariableNamesFrameCache.put(startId, names);
        return names;
    }

    private Set<String> getBaseRuntimeVariableNames() {
        if (cachedBaseRuntimeVariableNames != null) {
            return cachedBaseRuntimeVariableNames;
        }
        Set<String> names = new HashSet<>();
        for (Node graphNode : nodes) {
            if (graphNode == null) {
                continue;
            }
            if (graphNode.getType() == NodeType.VARIABLE) {
                NodeParameter parameter = graphNode.getParameter("Variable");
                if (parameter == null) {
                    continue;
                }
                String value = parameter.getStringValue();
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        cachedBaseRuntimeVariableNames = names;
        return cachedBaseRuntimeVariableNames;
    }

    private void updateMessageFieldContentWidth(Font textRenderer) {
        inlineFields.updateMessageFieldContentWidth(textRenderer);
    }

    private void updateCoordinateFieldContentWidth(Font textRenderer) {
        inlineFields.updateCoordinateFieldContentWidth(textRenderer);
    }

    private void updateAmountFieldContentWidth(Font textRenderer) {
        inlineFields.updateAmountFieldContentWidth(textRenderer);
    }

    private void updateStopTargetFieldContentWidth(Font textRenderer) {
        inlineFields.updateStopTargetFieldContentWidth(textRenderer);
    }

    private void updateVariableFieldContentWidth(Font textRenderer) {
        inlineFields.updateVariableFieldContentWidth(textRenderer);
    }

    private void updateParameterFieldContentWidth(Node node, Font textRenderer, int editingIndex, String editingValue) {
        if (node == null || !rendersInlineParameters(node) || textRenderer == null) {
            return;
        }
        int requiredFieldWidth = 0;
        if (node.supportsModeSelection()) {
            String modeLabel = node.getModeDisplayLabel();
            if (modeLabel != null && !modeLabel.isEmpty()) {
                requiredFieldWidth = Math.max(requiredFieldWidth, textRenderer.width(modeLabel));
            }
        }
        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter param = parameters.get(i);
            if (param == null) {
                continue;
            }
            String label = node.getParameterDisplayName(param);
            if (label == null) {
                label = "";
            }
            label = label + ":";
            String value = i == editingIndex ? editingValue : param.getStringValue();
            if (value == null) {
                value = "";
            }
            if (node.getType() == NodeType.PARAM_VILLAGER_TRADE
                && ("Item".equalsIgnoreCase(param.getName()) || "Trade".equalsIgnoreCase(param.getName()))) {
                value = nodeControls.formatVillagerTradeValue(value);
            }
            int labelWidth = textRenderer.width(label);
            int valueWidth = textRenderer.width(value);
            int fieldWidth = labelWidth + valueWidth + 12;
            requiredFieldWidth = Math.max(requiredFieldWidth, fieldWidth);
        }
        node.setParameterFieldWidthOverride(requiredFieldWidth);
        node.recalculateDimensions();
    }











    private NodeGraphData.CustomNodeDefinition getTemplateDefinition(Node node) {
        NodeGraphData templateData = getPresetPreviewGraphData(node);
        return templateData == null ? null : NodeGraphPersistence.resolveCustomNodeDefinition(getSelectedPresetName(node), templateData);
    }

    private String getSelectedPresetName(Node node) {
        if (node == null) {
            return "";
        }
        NodeParameter presetParam = node.getParameter("Preset");
        String presetName = presetParam != null ? presetParam.getStringValue() : "";
        if (presetName == null || presetName.isBlank()) {
            return activePreset == null ? "" : activePreset.trim();
        }
        return presetName.trim();
    }

    private NodeGraphData getPresetPreviewGraphData(Node node) {
        if (node == null || node.getType() != NodeType.TEMPLATE) {
            return null;
        }
        String normalized = getSelectedPresetName(node);
        if (normalized.isEmpty()) {
            return node.getTemplateGraphData();
        }
        NodeGraphData cached = node.getTemplateGraphData();
        if (cached != null) {
            return cached;
        }
        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(normalized);
        if (loaded != null) {
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(normalized, loaded);
            if (definition != null) {
                node.setTemplateName(definition.getName());
                node.setTemplateVersion(definition.getVersion() != null ? definition.getVersion() : 0);
            }
            node.setTemplateGraphData(loaded);
            return loaded;
        }
        return cached;
    }





    public boolean isPointInsideTemplateEditButton(Node node, int mouseX, int mouseY) {
        return false;
    }

    private void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                            int mouseX, int mouseY) {
        inlineFieldRenderer.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderPresetSelectorField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                           int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int caretColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.CARET_COLOR;

        boolean editing = isEditingStopTargetField() && inlineFields.getStopTargetEditingNode() == node;
        if (editing) {
            inlineFields.getStopTargetEditor().updateCaretBlink();
        }
        boolean open = specializedSelectors.isRunPresetOpen() && specializedSelectors.getRunPresetNode() == node;

        int fieldTop = node.getStopTargetFieldInputTop() - cameraY;
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldLeft = node.getStopTargetFieldLeft() - cameraX;
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldBottom = fieldTop + fieldHeight;
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getStopTargetFieldLeft()
            && worldMouseX <= node.getStopTargetFieldLeft() + fieldWidth
            && worldMouseY >= node.getStopTargetFieldInputTop()
            && worldMouseY <= node.getStopTargetFieldInputTop() + fieldHeight;
        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#presetSelector", hovered || open);
        int accentColor = isOverSidebar ? nodeControls.toGrayscale(nodeControls.getSelectedNodeAccentColor(), 0.8f) : nodeControls.getSelectedNodeAccentColor();
        UIStyleHelper.FieldPalette palette = UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);
        int animatedTextColor = isOverSidebar ? textColor : palette.textColor();

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            new UIStyleHelper.FieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                animatedTextColor,
                palette.placeholderColor()
            )
        );

        String inlineLabel = "Preset:";
        int labelX = fieldLeft + 4;
        int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
        drawNodeText(context, textRenderer, Component.literal(inlineLabel), labelX, labelY, labelColor);
        int valueTextX = labelX + textRenderer.width(inlineLabel) + 6;
        int maxValueWidth = Math.max(0, fieldLeft + fieldWidth - valueTextX - 16);

        String value;
        if (editing) {
            value = inlineFields.getStopTargetEditor().getBuffer();
        } else {
            NodeParameter targetParam = node.getParameter(getStopTargetParameterKey(node));
            value = targetParam != null ? targetParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display = value;
        int valueDrawColor = animatedTextColor;
        if (!editing && display.isEmpty()) {
            display = "preset";
            valueDrawColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        }
        display = editing ? display : trimTextToWidth(display, textRenderer, maxValueWidth);

        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        if (editing && inlineFields.getStopTargetEditor().hasSelection()) {
            int start = Mth.clamp(inlineFields.getStopTargetEditor().getSelectionStart(), 0, display.length());
            int end = Mth.clamp(inlineFields.getStopTargetEditor().getSelectionEnd(), 0, display.length());
            if (start != end) {
                int selectionStartX = valueTextX + textRenderer.width(display.substring(0, start));
                int selectionEndX = valueTextX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        drawNodeText(context, textRenderer, Component.literal(display), valueTextX, textY, valueDrawColor);

        if (editing && inlineFields.getStopTargetEditor().isCaretVisible()) {
            int caretIndex = Mth.clamp(inlineFields.getStopTargetEditor().getCaretPosition(), 0, display.length());
            int caretX = valueTextX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    private void renderVariableInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        inlineFieldRenderer.renderVariableInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderSchematicDropdownField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                              int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        boolean open = specializedSelectors.isSchematicOpen() && specializedSelectors.getSchematicNode() == node;

        int labelTop = node.getSchematicFieldLabelTop() - cameraY;
        int labelHeight = node.getSchematicFieldLabelHeight();
        int fieldTop = node.getSchematicFieldInputTop() - cameraY;
        int fieldHeight = node.getSchematicFieldHeight();
        int fieldLeft = node.getSchematicFieldLeft() - cameraX;
        int fieldWidth = node.getSchematicFieldWidth();
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getSchematicFieldLeft()
            && worldMouseX <= node.getSchematicFieldLeft() + fieldWidth
            && worldMouseY >= node.getSchematicFieldInputTop()
            && worldMouseY <= node.getSchematicFieldInputTop() + fieldHeight;
        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#schematicSelector", hovered || open);
        int accentColor = isOverSidebar ? nodeControls.toGrayscale(UITheme.SCHEMATIC_ACTIVE_BORDER, 0.8f) : UITheme.SCHEMATIC_ACTIVE_BORDER;
        UIStyleHelper.FieldPalette palette = UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);

        drawNodeText(context, textRenderer, Component.translatable("pathmind.field.schematic"), fieldLeft, labelTop + (labelHeight - textRenderer.lineHeight) / 2, labelColor);

        int fieldBottom = fieldTop + fieldHeight;
        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            new UIStyleHelper.FieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                palette.textColor(),
                palette.placeholderColor()
            )
        );

        NodeParameter schematicParam = node.getParameter("Schematic");
        String value = schematicParam != null ? schematicParam.getDisplayValue() : "";
        if (value != null && !value.isEmpty() && !schematicExistsInRoots(value)) {
            value = "";
        }
        if (value == null || value.isEmpty()) {
            value = "schematic";
            textColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        }

        String display = trimTextToWidth(value, textRenderer, fieldWidth - 16);
        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        int animatedTextColor = isOverSidebar ? textColor : palette.textColor();
        drawNodeText(context, textRenderer, Component.literal(display), textX, textY, (value.equals("schematic") ? textColor : animatedTextColor));

        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    private void renderSchematicDropdownList(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(schematicDropdownAnimation, specializedSelectors.isSchematicOpen());
        if (specializedSelectors.getSchematicNode() != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearSchematicDropdownState();
            return;
        }

        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        List<String> options = specializedSelectors.getSchematicOptions();
        int optionCount = options.isEmpty() ? 1 : options.size();
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - cameraY;
        int screenHeight = Math.round(
            Minecraft.getInstance().getWindow().getGuiScaledHeight()
                / Math.max(0.01f, getZoomScale())
        );
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        specializedSelectors.setSchematicScrollOffset(
            Mth.clamp(specializedSelectors.getSchematicScrollOffset(), 0, layout.maxScrollOffset)
        );

        int dropdownWidth = getSchematicDropdownWidth(node);
        int listLeft = node.getSchematicFieldLeft() - cameraX;
        int accentColor = isOverSidebar ? nodeControls.toGrayscale(UITheme.SCHEMATIC_ACTIVE_BORDER, 0.8f) : UITheme.SCHEMATIC_ACTIVE_BORDER;
        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);
        UIStyleHelper.ScrollContainerPalette adjustedPalette = new UIStyleHelper.ScrollContainerPalette(
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : containerPalette.backgroundColor(),
            isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor(),
            isOverSidebar ? UITheme.PANEL_INNER_BORDER : containerPalette.innerBorderColor(),
            containerPalette.trackColor(),
            containerPalette.thumbColor()
        );

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        specializedSelectors.setSchematicHoverIndex(PathmindDropdownRenderer.renderTextList(
            context,
            textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(listLeft, listTop, dropdownWidth)
                .rows(SCHEMATIC_DROPDOWN_ROW_HEIGHT, visibleCount, options.size())
                .scroll(specializedSelectors.getSchematicScrollOffset(), layout.maxScrollOffset, DROPDOWN_SCROLLBAR_ALLOWANCE)
                .animation(animProgress)
                .hoverPoint(worldMouseX - cameraX, worldMouseY - cameraY)
                .colors(accentColor, textColor)
                .textLayout(3, 4, false, shouldRenderNodeText())
                .labels(tr("pathmind.dropdown.noSchematicsFound"), options::get)
                .chrome(
                    adjustedPalette,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.trackColor(),
                    isOverSidebar ? UITheme.BORDER_HIGHLIGHT : containerPalette.thumbColor(),
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor()
                )
                .build()
        ));
    }

    private void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(runPresetDropdownAnimation, specializedSelectors.isRunPresetOpen());
        if (specializedSelectors.getRunPresetNode() != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearRunPresetDropdownState();
            return;
        }

        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        List<String> options = specializedSelectors.getRunPresetOptions();
        int optionCount = options.isEmpty() ? 1 : options.size();
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - cameraY;
        int screenHeight = Math.round(
            Minecraft.getInstance().getWindow().getGuiScaledHeight()
                / Math.max(0.01f, getZoomScale())
        );
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        specializedSelectors.setRunPresetScrollOffset(
            Mth.clamp(specializedSelectors.getRunPresetScrollOffset(), 0, layout.maxScrollOffset)
        );

        int dropdownWidth = getRunPresetDropdownWidth(node);
        int listLeft = node.getStopTargetFieldLeft() - cameraX;
        int accentColor = isOverSidebar ? nodeControls.toGrayscale(nodeControls.getSelectedNodeAccentColor(), 0.8f) : nodeControls.getSelectedNodeAccentColor();
        UIStyleHelper.ScrollContainerPalette containerPalette = UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);
        UIStyleHelper.ScrollContainerPalette adjustedPalette = new UIStyleHelper.ScrollContainerPalette(
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : containerPalette.backgroundColor(),
            isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor(),
            isOverSidebar ? UITheme.PANEL_INNER_BORDER : containerPalette.innerBorderColor(),
            containerPalette.trackColor(),
            containerPalette.thumbColor()
        );

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        specializedSelectors.setRunPresetHoverIndex(PathmindDropdownRenderer.renderTextList(
            context,
            textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(listLeft, listTop, dropdownWidth)
                .rows(SCHEMATIC_DROPDOWN_ROW_HEIGHT, visibleCount, options.size())
                .scroll(specializedSelectors.getRunPresetScrollOffset(), layout.maxScrollOffset, DROPDOWN_SCROLLBAR_ALLOWANCE)
                .animation(animProgress)
                .hoverPoint(worldMouseX - cameraX, worldMouseY - cameraY)
                .colors(accentColor, textColor)
                .textLayout(3, 4, false, shouldRenderNodeText())
                .labels(tr("pathmind.dropdown.noPresetsFound"), options::get)
                .chrome(
                    adjustedPalette,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.trackColor(),
                    isOverSidebar ? UITheme.BORDER_HIGHLIGHT : containerPalette.thumbColor(),
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor()
                )
                .build()
        ));
    }

    public boolean isEditingCoordinateField() {
        return inlineFields.isEditingCoordinateField();
    }

    public boolean isScreenCoordinateCaptureActive() {
        return screenCoordinateCapture.isActive();
    }

    public boolean isScreenCoordinateCaptureActiveFor(Node node) {
        return screenCoordinateCapture.isActiveFor(node);
    }

    public void startScreenCoordinateCapture(Node node) {
        screenCoordinateCapture.start(node);
    }

    public void cancelScreenCoordinateCapture() {
        screenCoordinateCapture.cancel();
    }

    public void updateScreenCoordinateCapturePreview(int screenX, int screenY) {
        screenCoordinateCapture.updatePreview(screenX, screenY);
    }

    public boolean commitScreenCoordinateCapture(int screenX, int screenY) {
        return screenCoordinateCapture.commit(screenX, screenY);
    }

    public int getCoordinateFieldAxisAt(Node node, int screenX, int screenY) {
        return inlineFields.getCoordinateFieldAxisAt(node, screenX, screenY);
    }

    public boolean isPointInsideScreenCoordinatePickerButton(Node node, int mouseX, int mouseY) {
        return inlineFields.isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY);
    }

    public boolean handleScreenCoordinatePickerClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY)) {
            return false;
        }
        focusSelectedNode(node);
        startScreenCoordinateCapture(node);
        return true;
    }

    public void startCoordinateEditing(Node node, int axisIndex) {
        inlineFields.startCoordinateEditing(node, axisIndex);
    }

    public void stopCoordinateEditing(boolean commit) {
        inlineFields.stopCoordinateEditing(commit);
    }

    public boolean handleCoordinateKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleCoordinateKeyPressed(keyCode, modifiers);
    }

    private boolean isTextShortcutDown(int modifiers) {
        return InputCompatibilityBridge.hasControlDown()
            || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    public boolean handleCoordinateCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleCoordinateCharTyped(chr);
    }

    public boolean isEditingAmountField() {
        return inlineFields.isEditingAmountField();
    }

    public void startAmountEditing(Node node) {
        inlineFields.startAmountEditing(node);
    }

    public void stopAmountEditing(boolean commit) {
        inlineFields.stopAmountEditing(commit);
    }

    public boolean handleAmountKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleAmountKeyPressed(keyCode, modifiers);
    }

    public boolean handleAmountCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleAmountCharTyped(chr);
    }

    public boolean isEditingStopTargetField() {
        return inlineFields.isEditingStopTargetField();
    }

    public void startStopTargetEditing(Node node) {
        inlineFields.startStopTargetEditing(node);
    }

    public void stopStopTargetEditing(boolean commit) {
        inlineFields.stopStopTargetEditing(commit);
    }

    public boolean isEditingVariableField() {
        return inlineFields.isEditingVariableField();
    }

    public void startVariableEditing(Node node) {
        inlineFields.startVariableEditing(node);
    }

    public void stopVariableEditing(boolean commit) {
        inlineFields.stopVariableEditing(commit);
    }

    public boolean handleVariableKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleVariableKeyPressed(keyCode, modifiers);
    }

    public boolean handleVariableCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleVariableCharTyped(chr);
    }

    public boolean handleStopTargetKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleStopTargetKeyPressed(keyCode, modifiers);
    }

    public boolean handleStopTargetCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleStopTargetCharTyped(chr);
    }

    public boolean isEditingStickyNote() {
        return stickyNoteController.isEditing();
    }

    public void startStickyNoteEditing(Node node) {
        stickyNoteController.startEditing(node);
    }

    public void stopStickyNoteEditing(boolean commit) {
        stickyNoteController.stopEditing(commit);
    }

    private void flushDeferredStickyNoteSaveIfDue() {
        stickyNoteController.flushDeferredSaveIfDue();
    }

    public boolean isPointInsideStickyNoteTextArea(Node node, int screenX, int screenY) {
        if (node == null || !node.isStickyNote()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        return worldX >= node.getStickyNoteBodyLeft()
            && worldX <= node.getStickyNoteBodyLeft() + node.getStickyNoteBodyWidth()
            && worldY >= node.getStickyNoteBodyTop()
            && worldY <= node.getStickyNoteBodyTop() + node.getStickyNoteBodyHeight();
    }

    public boolean handleStickyNoteResizeHandleClick(Node node, int screenX, int screenY) {
        return stickyNoteController.handleResizeHandleClick(node, screenX, screenY);
    }

    public boolean isPointInsideStickyNoteResizeHandle(Node node, int screenX, int screenY) {
        return getStickyNoteResizeCornerAt(node, screenX, screenY) != null;
    }

    public StickyNoteResizeCorner getStickyNoteResizeCornerAt(Node node, int screenX, int screenY) {
        return stickyNoteController.getResizeCornerAt(node, screenX, screenY);
    }

    public boolean handleStickyNoteKeyPressed(int keyCode, int modifiers) {
        return stickyNoteController.handleKeyPressed(keyCode, modifiers);
    }

    public boolean handleStickyNoteCharTyped(char chr, int modifiers) {
        return stickyNoteController.handleCharTyped(chr, modifiers);
    }

    public boolean isEditingMessageField() {
        return inlineFields.isEditingMessageField();
    }

    public void startMessageEditing(Node node, int index) {
        inlineFields.startMessageEditing(node, index);
    }

    public void stopMessageEditing(boolean commit) {
        inlineFields.stopMessageEditing(commit);
    }

    public boolean isEditingEventNameField() {
        return inlineFields.isEditingEventNameField();
    }

    public void startEventNameEditing(Node node) {
        inlineFields.startEventNameEditing(node);
    }

    public void stopEventNameEditing(boolean commit) {
        inlineFields.stopEventNameEditing(commit);
    }

    public boolean handleEventNameKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleEventNameKeyPressed(keyCode, modifiers);
    }

    public boolean handleEventNameCharTyped(char chr, int modifiers) {
        return inlineFields.handleEventNameCharTyped(chr);
    }

    public boolean isEditingParameterField() {
        return parameterEditor.isEditing();
    }

    private void updateParameterCaretBlink() {
        parameterEditor.updateCaretBlink();
    }

    public void startParameterEditing(Node node, int index) {
        parameterEditor.start(node, index);
    }

    public void stopParameterEditing(boolean commit) {
        parameterEditor.stop(commit);
    }

    private boolean applyParameterEdit() {
        return parameterEditor.apply();
    }

    private void refreshStateParameterPreview() {
        parameterEditor.refreshStatePreview();
    }

    private boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing) {
        return parameterEditor.isTradeInlinePlaceholder(node, parameter, editing);
    }

    private boolean isDefaultMouseButtonValue(String value) {
        return ParameterTextEditorController.isDefaultMouseButtonValue(value);
    }

    private boolean isDefaultHandValue(String value) {
        return ParameterTextEditorController.isDefaultHandValue(value);
    }

    private String formatMouseButtonValue(String value) {
        return ParameterTextEditorController.formatMouseButtonValue(value);
    }

    private String formatHandValue(String value) {
        return ParameterTextEditorController.formatHandValue(value);
    }

    public boolean handleParameterKeyPressed(int keyCode, int modifiers) {
        return parameterEditor.handleKeyPressed(keyCode, modifiers);
    }

    public boolean handleParameterCharTyped(char chr, int modifiers, Font textRenderer) {
        return parameterEditor.handleCharTyped(chr, textRenderer);
    }

    public boolean handleMessageKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleMessageKeyPressed(keyCode, modifiers);
    }

    public boolean handleMessageCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleMessageCharTyped(chr);
    }

    private boolean isAnyBlockItemValue(String value) {
        return ParameterTextEditorController.isAnyBlockItemValue(value);
    }

    private String formatAttributeDetectionInlineValue(Node node, NodeParameter parameter, String value) {
        if (node == null || parameter == null || !node.isAttributeDetectionSensor()) {
            return value;
        }
        if ("Attribute".equalsIgnoreCase(parameter.getName())) {
            AttributeDetectionConfig.AttributeOption attribute = AttributeDetectionConfig.getAttribute(value);
            return attribute != null ? attribute.label() : value;
        }
        if ("Value".equalsIgnoreCase(parameter.getName()) && isAttributeDetectionBooleanValueParameter(node, node.getParameters().indexOf(parameter))) {
            return "true".equalsIgnoreCase(value) ? tr("pathmind.option.true") : tr("pathmind.option.false");
        }
        return value;
    }

    private void updateParameterDropdown(Node node, int index, Font textRenderer, int fieldX, int fieldY, int fieldWidth, int fieldHeight) {
        parameterDropdown.update(node, index, textRenderer, fieldX, fieldY, fieldWidth, fieldHeight);
    }

    private void closeParameterDropdown() {
        parameterDropdown.close();
    }

    private void clearParameterDropdownSuppression() {
        parameterDropdown.clearSuppression();
    }

    private void openInlineParameterDropdown(Node node, int index) {
        parameterDropdown.openInline(node, index);
    }

    private int getDropdownRowHeight() {
        return SCHEMATIC_DROPDOWN_ROW_HEIGHT;
    }

    public boolean handleParameterDropdownClick(double screenX, double screenY) {
        return parameterDropdown.handleClick(screenX, screenY);
    }

    public boolean handleParameterDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return parameterDropdown.handleScroll(screenX, screenY, verticalAmount);
    }

    private void renderParameterDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        parameterDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    public boolean handleModeDropdownClick(double screenX, double screenY) {
        return modeDropdown.handleClick(screenX, screenY);
    }

    public boolean handleModeDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return modeDropdown.handleScroll(screenX, screenY, verticalAmount);
    }

    public boolean handleRandomRoundingDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return randomRounding.handleScroll(screenX, screenY, verticalAmount);
    }

    public boolean handleModeFieldClick(Node node, int screenX, int screenY) {
        if (node == null || !node.supportsModeSelection()) {
            return false;
        }
        if (!isPointInsideModeField(node, screenX, screenY)) {
            return false;
        }
        if (modeDropdown.isOpen() && modeDropdown.getNode() == node) {
            closeModeDropdown();
            return true;
        }
        stopParameterEditing(true);
        openModeDropdown(node);
        return true;
    }

    public boolean isModeDropdownOpen() {
        return modeDropdown.isOpen();
    }

    public void closeModeDropdown() {
        modeDropdown.close();
    }

    private void renderModeDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        modeDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    private int getSchematicDropdownWidth(Node node) {
        Font textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getSchematicFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.width(tr("pathmind.dropdown.noSchematicsFound"));
        for (String option : specializedSelectors.getSchematicOptions()) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getRunPresetDropdownWidth(Node node) {
        Font textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getStopTargetFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.width(tr("pathmind.dropdown.noPresetsFound"));
        for (String option : specializedSelectors.getRunPresetOptions()) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private void openModeDropdown(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return;
        }
        closeParameterDropdown();
        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeRandomRoundingDropdown();
        modeDropdown.open(node);
    }

    private DropdownController.Rect computeModeDropdownAnchor(Node node) {
        if (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) {
            return new DropdownController.Rect(
                node.getAmountFieldLeft() - cameraX,
                node.getAmountFieldLabelTop() - cameraY,
                node.getAmountFieldWidth(),
                node.getAmountFieldLabelHeight());
        } else if (node.showsModeFieldAboveParameterSlot()) {
            return new DropdownController.Rect(
                node.getModeFieldLeft() - cameraX,
                node.getModeFieldTop() - cameraY,
                node.getModeFieldWidth(),
                node.getModeFieldHeight());
        }
        return new DropdownController.Rect(
            nodeControls.getParameterFieldLeft(node) - cameraX,
            node.getY() - cameraY + 18,
            nodeControls.getParameterFieldWidth(node),
            nodeControls.getParameterFieldHeight());
    }

    private List<DropdownController.Option<com.pathmind.nodes.NodeMode>> getModeDropdownOptions(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return Collections.emptyList();
        }
        com.pathmind.nodes.NodeMode[] modes = com.pathmind.nodes.NodeMode.getModesForNodeType(node.getType());
        if (modes == null || modes.length == 0) {
            return Collections.emptyList();
        }
        List<DropdownController.Option<com.pathmind.nodes.NodeMode>> options = new ArrayList<>(modes.length);
        for (com.pathmind.nodes.NodeMode mode : modes) {
            if (mode != null) {
                options.add(new DropdownController.Option<>(mode.getDisplayName(), mode));
            }
        }
        return options;
    }

    private boolean isPointInsideModeField(Node node, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        if (node != null
            && (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION)
            && node.supportsModeSelection()) {
            int fieldLeft = node.getAmountFieldLeft();
            int fieldWidth = node.getAmountFieldWidth();
            int fieldHeight = node.getAmountFieldLabelHeight();
            int fieldTop = node.getAmountFieldLabelTop();
            return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
        }
        if (node != null && node.showsModeFieldAboveParameterSlot()) {
            int fieldLeft = node.getModeFieldLeft();
            int fieldWidth = node.getModeFieldWidth();
            int fieldHeight = node.getModeFieldHeight();
            int fieldTop = node.getModeFieldTop();
            return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
        }
        if (node == null || !node.shouldRenderInlineParameters()) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        int fieldTop = node.getY() + 18;
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private Font getClientTextRenderer() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.font : null;
    }

    private String getClipboardText() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            return client.keyboardHandler.getClipboard();
        }
        return "";
    }

    private void setClipboardText(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }

    public boolean isPointInsideAmountField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideAmountField(node, screenX, screenY);
    }

    private boolean isPointInsideRandomRoundingField(Node node, int screenX, int screenY) {
        return randomRounding.isPointInsideField(node, screenX, screenY);
    }

    private boolean isPointInsideRandomRoundingToggle(Node node, int screenX, int screenY) {
        return randomRounding.isPointInsideToggle(node, screenX, screenY);
    }

    private boolean isPointInsideAmountToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasAmountToggle()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int left = node.getAmountToggleLeft() - 3;
        int top = node.getAmountToggleTop() - 3;
        int width = node.getAmountToggleWidth() + 6;
        int height = node.getAmountToggleHeight() + 6;
        return worldX >= left && worldX <= left + width
            && worldY >= top && worldY <= top + height;
    }

    public boolean handleAmountToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideAmountToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isAmountInputEnabled();
        node.setAmountInputEnabled(newState);
        nodeControls.getNodeToggleAnimation(amountToggleAnimations, node, newState)
            .animateTo(newState ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        if (!newState && isEditingAmountField() && inlineFields.getAmountEditingNode() == node) {
            stopAmountEditing(false);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
        return true;
    }

    public boolean handleRandomRoundingToggleClick(Node node, int mouseX, int mouseY) {
        return randomRounding.handleToggleClick(node, mouseX, mouseY);
    }

    public boolean handleRandomRoundingDropdownClick(Node node, int mouseX, int mouseY) {
        return randomRounding.handleDropdownClick(node, mouseX, mouseY);
    }

    public boolean isPointInsideStopTargetField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideStopTargetField(node, screenX, screenY);
    }

    private String getStopTargetParameterKey(Node node) {
        if (node == null) {
            return "StartNumber";
        }
        return node.getStopTargetFieldParameterKey();
    }

    private String getStopTargetPlaceholder(Node node) {
        if (isPresetSelectorNode(node)) {
            return "preset";
        }
        return "start #";
    }

    public boolean handleStopTargetFieldClick(int screenX, int screenY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node != null
                && node.hasStopTargetInputField()
                && !isPresetSelectorNode(node)
                && isPointInsideStopTargetField(node, screenX, screenY)) {
                focusSelectedNode(node);
                startStopTargetEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean handleRunPresetDropdownClick(Node clickedNode, int screenX, int screenY) {
        return specializedSelectors.handleRunPresetClick(clickedNode, screenX, screenY);
    }

    public boolean isPointInsideVariableField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideVariableField(node, screenX, screenY);
    }

    public boolean handleVariableFieldClick(int screenX, int screenY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node != null && node.hasVariableInputField() && isPointInsideVariableField(node, screenX, screenY)) {
                focusSelectedNode(node);
                startVariableEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean isPointInsideEventNameField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideEventNameField(node, screenX, screenY);
    }

    public boolean handleEventNameFieldClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideEventNameField(node, mouseX, mouseY)) {
            return false;
        }
        startEventNameEditing(node);
        return true;
    }

    public int getMessageFieldIndexAt(Node node, int screenX, int screenY) {
        return inlineFields.getMessageFieldIndexAt(node, screenX, screenY);
    }

    public int getParameterFieldIndexAt(Node node, int screenX, int screenY) {
        if (node == null || !canEditInlineParameterFields(node)) {
            return -1;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        if (node.getType() == NodeType.VARIABLE || node.getType() == NodeType.ROUTINE_INPUT) {
            int boxHeight = 16;
            int boxLeft = node.getX() + 6;
            int boxRight = node.getX() + node.getWidth() - 6;
            int boxTop = node.getY() + node.getHeight() / 2 - boxHeight / 2 + 4;
            int boxBottom = boxTop + boxHeight;
            if (worldX >= boxLeft && worldX <= boxRight
                && worldY >= boxTop && worldY <= boxBottom) {
                return 0;
            }
            return -1;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        int fieldTop = nodeControls.getInlineParameterFieldsTop(node);

        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter parameter = parameters.get(i);
            if (node.getParameterLabel(parameter).isEmpty()) {
                continue;
            }
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight) {
                return i;
            }
            fieldTop += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return -1;
    }

    private boolean canEditInlineParameterFields(Node node) {
        if (node == null) {
            return false;
        }
        if (node.getType() == NodeType.ROUTINE_INPUT) {
            return true;
        }
        if (!rendersInlineParameters(node)) {
            return false;
        }
        return !node.hasPopupEditButton() || node.getType() == NodeType.PARAM_INVENTORY_SLOT;
    }

    private int getInlineParameterFieldTop(Node node, int index) {
        if (node == null || index < 0) {
            return 0;
        }
        int fieldTop = nodeControls.getInlineParameterFieldsTop(node);
        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter parameter = parameters.get(i);
            if (parameter == null || node.getParameterLabel(parameter).isEmpty()) {
                continue;
            }
            if (i == index) {
                return fieldTop;
            }
            fieldTop += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return fieldTop;
    }

    public boolean handleBooleanLiteralDropdownClick(Node node, int mouseX, int mouseY) {
        if (parameterDropdown.isOpen() && !isEditingParameterField()
            && parameterDropdown.getNode() != null
            && isInlineDropdownParameter(parameterDropdown.getNode(), parameterDropdown.getIndex())) {
            if (isPointInsideInlineDropdownField(
                parameterDropdown.getNode(), parameterDropdown.getIndex(), mouseX, mouseY
            )) {
                closeParameterDropdown();
                return true;
            }
        }
        if (node == null) {
            int worldX = screenToWorldX(mouseX);
            int worldY = screenToWorldY(mouseY);
            for (int i = nodes.size() - 1; i >= 0; i--) {
                Node candidate = nodes.get(i);
                if (candidate == null) {
                    continue;
                }
                int index = getParameterFieldIndexAt(candidate, mouseX, mouseY);
                if (isInlineDropdownParameter(candidate, index)) {
                    node = candidate;
                    break;
                }
                if (candidate.getX() > worldX || candidate.getY() > worldY) {
                    continue;
                }
            }
            if (node == null) {
                return false;
            }
        }
        int index = getParameterFieldIndexAt(node, mouseX, mouseY);
        if (!isInlineDropdownParameter(node, index)) {
            return false;
        }
        openInlineParameterDropdown(node, index);
        return true;
    }

    private boolean isPointInsideInlineDropdownField(Node node, int index, int screenX, int screenY) {
        if (!isInlineDropdownParameter(node, index)) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = getInlineParameterFieldTop(node, index);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean handleDirectionModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findDirectionModeNodeAt(worldX, worldY);
        if (!nodeControls.isCombinedDirectionNode(node)) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = nodeControls.getDirectionModeTabTop(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        if (worldX < fieldLeft || worldX > fieldLeft + fieldWidth
            || worldY < fieldTop || worldY > fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
            return false;
        }

        boolean exactMode = worldX < fieldLeft + fieldWidth / 2;
        if (isEditingParameterField()) {
            stopParameterEditing(true);
        }
        if (node.isDirectionModeExact() != exactMode) {
            node.setDirectionModeExact(exactMode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
        return true;
    }

    private Node findDirectionModeNodeAt(int worldX, int worldY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node candidate = nodes.get(i);
            if (!nodeControls.isCombinedDirectionNode(candidate)) {
                continue;
            }
            int fieldLeft = nodeControls.getParameterFieldLeft(candidate);
            int fieldTop = nodeControls.getDirectionModeTabTop(candidate);
            int fieldWidth = nodeControls.getParameterFieldWidth(candidate);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    private Node findBooleanModeNodeAt(int worldX, int worldY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node candidate = nodes.get(i);
            if (!nodeControls.isCombinedBooleanNode(candidate)) {
                continue;
            }
            int fieldLeft = nodeControls.getParameterFieldLeft(candidate);
            int fieldTop = nodeControls.getBooleanModeTabTop(candidate);
            int fieldWidth = nodeControls.getParameterFieldWidth(candidate);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    public boolean handleSchematicDropdownClick(Node clickedNode, int screenX, int screenY) {
        return specializedSelectors.handleSchematicClick(clickedNode, screenX, screenY);
    }

    public boolean handleSchematicDropdownScroll(double screenX, double screenY, double amount) {
        return specializedSelectors.handleSchematicScroll(screenX, screenY, amount);
    }

    public boolean handleRunPresetDropdownScroll(double screenX, double screenY, double amount) {
        return specializedSelectors.handleRunPresetScroll(screenX, screenY, amount);
    }

    private boolean isPointInsideSchematicField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasSchematicDropdownField()) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getSchematicFieldLeft();
        int fieldTop = node.getSchematicFieldInputTop();
        int fieldWidth = node.getSchematicFieldWidth();
        int fieldHeight = node.getSchematicFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private boolean isPointInsideRunPresetField(Node node, int screenX, int screenY) {
        if (!isPresetSelectorNode(node)) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getStopTargetFieldLeft();
        int fieldTop = node.getStopTargetFieldInputTop();
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldHeight = node.getStopTargetFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private boolean isPointInsideRunPresetToggle(Node node, int screenX, int screenY) {
        if (!isPresetSelectorNode(node)) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getStopTargetFieldLeft();
        int fieldTop = node.getStopTargetFieldInputTop();
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldHeight = node.getStopTargetFieldHeight();
        int toggleLeft = fieldLeft + Math.max(0, fieldWidth - 14);
        return worldX >= toggleLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean isPointInsideRunPresetOpenButton(Node node, int screenX, int screenY) {
        if (!hasRunPresetSelection(node)) return false;
        int left = getRunPresetOpenButtonWorldX(node);
        int top = getRunPresetOpenButtonWorldY(node);
        return nodeControls.isPointInsideNodeHeaderButton(left, top, NODE_HEADER_BUTTON_SIZE, screenX, screenY);
    }

    public String getSelectedPresetNameForNode(Node node) {
        return getSelectedPresetName(node);
    }

    private void closeSchematicDropdown() {
        specializedSelectors.closeSchematic();
    }

    private void closeRunPresetDropdown() {
        specializedSelectors.closeRunPreset();
    }

    private void closeRandomRoundingDropdown() {
        randomRounding.close();
    }

    private float getDropdownAnimationProgress(AnimatedValue animation, boolean open) {
        animation.animateTo(open ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        animation.tick();
        return AnimationHelper.easeOutQuad(animation.getValue());
    }

    private void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height) {
        context.enableScissor(x, y, x + Math.max(1, width), y + Math.max(1, height));
    }

    private void clearSchematicDropdownState() {
        specializedSelectors.clearSchematicState();
    }

    private void clearRunPresetDropdownState() {
        specializedSelectors.clearRunPresetState();
    }

    private void clearParameterDropdownState() {
        parameterDropdown.clearState();
    }

    private void applySchematicSelection(Node node, String value) {
        if (node == null || value == null || value.isEmpty()) {
            return;
        }
        node.setParameterValueAndPropagate("Schematic", value);
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
    }

    private void applyRunPresetSelection(Node node, String value) {
        if (node == null || value == null || value.isEmpty()) {
            return;
        }
        String keyName = getStopTargetParameterKey(node);
        node.setParameterValueAndPropagate(keyName, value);
        if (isEditingStopTargetField() && inlineFields.getStopTargetEditingNode() == node) {
            inlineFields.replaceStopTargetEditValue(value);
            updateStopTargetFieldContentWidth(getClientTextRenderer());
        }
        if (node.getType() == NodeType.TEMPLATE) {
            NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(value);
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(value, loaded);
            node.setTemplateName(definition != null ? definition.getName() : value);
            node.setTemplateVersion(definition != null && definition.getVersion() != null ? definition.getVersion() : 0);
            node.setTemplateGraphData(loaded);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
    }

    private boolean isPresetSelectorNode(Node node) {
        return node != null && (node.getType() == NodeType.RUN_PRESET
            || node.getType() == NodeType.TEMPLATE);
    }

    private void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
        if (!shouldRenderNodeText()) {
            return;
        }
        context.drawString(renderer, text, x, y, color, false);
    }

    private void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
        drawNodeText(context, renderer, Component.literal(text), x, y, color);
    }

    private String trimTextToWidth(String text, Font renderer, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (renderer == null) {
            return text;
        }
        TrimKey cacheKey = new TrimKey(text, maxWidth);
        String cached = trimmedTextCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (renderer.width(text) <= maxWidth) {
            trimmedTextCache.put(cacheKey, text);
            return text;
        }
        int safeMaxWidth = Math.max(0, maxWidth);
        String trimmed = TextRenderUtil.trimWithEllipsis(renderer, text, safeMaxWidth);
        trimmedTextCache.put(cacheKey, trimmed);
        return trimmed;
    }

    private void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color) {
        NodeRenderer.renderSocket(context, x, y, isInput, color);
    }

    private boolean shouldConsiderConnectionForViewport(NodeConnection connection, Set<Node> visibleRoots, int viewportWidth, int viewportHeight) {
        if (connection == null) {
            return false;
        }

        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        Node outputRoot = getRootNode(outputNode);
        Node inputRoot = getRootNode(inputNode);
        if ((outputRoot != null && visibleRoots.contains(outputRoot))
            || (inputRoot != null && visibleRoots.contains(inputRoot))) {
            return true;
        }

        SelectionBounds outputBounds = outputRoot != null ? cachedHierarchyBounds.get(outputRoot) : null;
        SelectionBounds inputBounds = inputRoot != null ? cachedHierarchyBounds.get(inputRoot) : null;
        if (outputBounds == null || inputBounds == null) {
            return true;
        }

        SelectionBounds combinedBounds = new SelectionBounds(
            Math.min(outputBounds.minX, inputBounds.minX),
            Math.min(outputBounds.minY, inputBounds.minY),
            Math.max(outputBounds.maxX, inputBounds.maxX),
            Math.max(outputBounds.maxY, inputBounds.maxY)
        );
        return intersectsViewport(combinedBounds, viewportWidth, viewportHeight);
    }

    private boolean isNodeOverSidebarForRender(Node node, int screenX, int screenWidth) {
        if (node == null) {
            return false;
        }
        boolean isOverSidebar = false;
        if (node.isDragging()) {
            if (multiDragActive && node.isSelected()) {
                isOverSidebar = isSelectionOverSidebar(sidebarWidthForRendering);
            } else {
                isOverSidebar = isNodeOverSidebar(node, sidebarWidthForRendering, screenX, screenWidth);
            }
        }
        if (!isOverSidebar && selectionDeletionPreviewActive && node.isSelected()) {
            isOverSidebar = true;
        }
        if (!isOverSidebar && cascadeDeletionPreviewNodes.contains(node)) {
            isOverSidebar = true;
        }
        return isOverSidebar;
    }

    private record TrimKey(String text, int maxWidth) {
    }



    public boolean isPointInsideRuntimeScopeButton(Node node, int screenX, int screenY) {
        return nodeControls.isPointInsideRuntimeScopeButton(node, screenX, screenY);
    }

    public boolean handleRuntimeScopeButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleRuntimeScopeButtonClick(node, mouseX, mouseY);
    }

    public boolean handleBooleanToggleClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleBooleanToggleClick(node, mouseX, mouseY);
    }

    public boolean handleMessageButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleMessageButtonClick(node, mouseX, mouseY);
    }

    public boolean handleBooleanOperatorButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleBooleanOperatorButtonClick(node, mouseX, mouseY);
    }

    public boolean handleMessageScopeToggleClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleMessageScopeToggleClick(node, mouseX, mouseY);
    }

    public boolean handleOperatorToggleClick(Font textRenderer, int mouseX, int mouseY) {
        return nodeControls.handleOperatorToggleClick(textRenderer, mouseX, mouseY);
    }

    public boolean isPointInsideBookTextButton(Node node, int mouseX, int mouseY) {
        return nodeControls.isPointInsideBookTextButton(node, mouseX, mouseY);
    }

    public boolean isPointInsidePopupEditButton(Node node, int mouseX, int mouseY) {
        return nodeControls.isPointInsidePopupEditButton(node, mouseX, mouseY);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<NodeConnection> getConnections() {
        return connections;
    }
    
    /**
     * Collects the names of all EVENT_FUNCTION nodes currently in the workspace.
     * Returns them in insertion order with duplicates removed.
     */
    public List<String> getFunctionNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }
            NodeParameter nameParam = node.getParameter("Name");
            if (nameParam == null) {
                continue;
            }
            String value = nameParam.getStringValue();
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return new ArrayList<>(names);
    }
    
    public int getCameraX() {
        return cameraX;
    }
    
    public int getCameraY() {
        return cameraY;
    }
    
    public void setSidebarWidth(int sidebarWidth) {
        this.sidebarWidthForRendering = sidebarWidth;
    }
    
    /**
     * Handle node click and detect double-clicks for parameter editing
     * Returns true if a double-click was detected and the popup should open
     */
    public boolean handleNodeClick(Node clickedNode, int mouseX, int mouseY) {
        long currentTime = System.currentTimeMillis();
        boolean isDoubleClick = false;
        
        if (clickedNode == lastClickedNode && 
            (currentTime - lastClickTime) < DOUBLE_CLICK_THRESHOLD) {
            isDoubleClick = true;
        }
        
        lastClickTime = currentTime;
        lastClickedNode = clickedNode;
        
        return isDoubleClick;
    }
    
    private boolean isMouseOverStartButton(Node startNode, int mouseX, int mouseY) {
        if (isPointInsideStartModeButton(startNode, mouseX, mouseY)) {
            return false;
        }
        int centerX = startNode.getX() + startNode.getWidth() / 2;
        int centerY = startNode.getY() + startNode.getHeight() / 2;
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        
        return worldMouseX >= centerX - 11 && worldMouseX <= centerX + 11
            && worldMouseY >= centerY - 11 && worldMouseY <= centerY + 11;
    }

    private int getStartModeButtonWorldX(Node startNode) {
        return startNode.getX() + startNode.getWidth() - 12;
    }

    private int getStartModeButtonWorldY(Node startNode) {
        return startNode.getY() + 4;
    }

    private boolean isPointInsideStartModeButton(Node startNode, int mouseX, int mouseY) {
        if (startNode == null || startNode.getType() != NodeType.START) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int buttonX = getStartModeButtonWorldX(startNode) - 1;
        int buttonY = getStartModeButtonWorldY(startNode) - 1;
        return worldMouseX >= buttonX && worldMouseX <= buttonX + 12
            && worldMouseY >= buttonY && worldMouseY <= buttonY + 9;
    }

    private Node findStartModeButtonAt(int mouseX, int mouseY) {
        rebuildHierarchyCacheIfNeeded();
        for (Node node : nodes) {
            if (!intersectsViewport(node)) {
                continue;
            }
            if (node.getType() == NodeType.START && isPointInsideStartModeButton(node, mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }

    public boolean isHoveringStartButton() {
        return hoveringStartButton;
    }

    public boolean isPointInsideInteractiveNodeControl(Node node, int mouseX, int mouseY) {
        if (node == null) {
            return false;
        }

        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);

        if (node.getType() == NodeType.START
            && (isMouseOverStartButton(node, mouseX, mouseY) || isPointInsideStartModeButton(node, mouseX, mouseY))) {
            return true;
        }
        if (node.getType() == NodeType.TEMPLATE && isPointInsideTemplateEditButton(node, mouseX, mouseY)) {
            return true;
        }
        if (nodeControls.isPointInsideBooleanToggle(node, mouseX, mouseY)
            || isPointInsideSchematicField(node, mouseX, mouseY)
            || isPointInsideRunPresetField(node, mouseX, mouseY)
            || isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY)
            || isPointInsideStickyNoteTextArea(node, mouseX, mouseY)
            || getStickyNoteResizeCornerAt(node, mouseX, mouseY) != null
            || getCoordinateFieldAxisAt(node, mouseX, mouseY) >= 0
            || isPointInsideStopTargetField(node, mouseX, mouseY)
            || isPointInsideVariableField(node, mouseX, mouseY)
            || isPointInsideRandomRoundingToggle(node, mouseX, mouseY)
            || isPointInsideRandomRoundingField(node, mouseX, mouseY)
            || isPointInsideAmountToggle(node, mouseX, mouseY)
            || isPointInsideAmountField(node, mouseX, mouseY)
            || getMessageFieldIndexAt(node, mouseX, mouseY) >= 0
            || getParameterFieldIndexAt(node, mouseX, mouseY) >= 0
            || isPointInsideEventNameField(node, mouseX, mouseY)
            || nodeControls.isPointInsideBookTextButton(node, mouseX, mouseY)
            || nodeControls.isPointInsidePopupEditButton(node, mouseX, mouseY)
            || nodeControls.isPointInsideMessageScopeToggle(node, mouseX, mouseY)) {
            return true;
        }

        if (node.isExpandableBooleanOperator()) {
            int buttonTop = node.getBooleanOperatorButtonTop();
            int addLeft = node.getBooleanOperatorAddButtonLeft();
            int removeLeft = node.getBooleanOperatorRemoveButtonLeft();
            int buttonSize = node.getBooleanOperatorButtonSize();
            if (nodeControls.isPointInsideNodeHeaderButtonWorld(addLeft, buttonTop, buttonSize, worldX, worldY)
                || nodeControls.isPointInsideNodeHeaderButtonWorld(removeLeft, buttonTop, buttonSize, worldX, worldY)) {
                return true;
            }
        }

        if (node.hasMessageInputFields()) {
            int buttonTop = node.getMessageButtonTop();
            int addLeft = node.getMessageAddButtonLeft();
            int removeLeft = node.getMessageRemoveButtonLeft();
            int buttonSize = node.getMessageButtonSize();
            if (nodeControls.isPointInsideNodeHeaderButtonWorld(addLeft, buttonTop, buttonSize, worldX, worldY)
                || nodeControls.isPointInsideNodeHeaderButtonWorld(removeLeft, buttonTop, buttonSize, worldX, worldY)) {
                return true;
            }
        }

        if (nodeControls.isCombinedDirectionNode(node)) {
            int fieldLeft = nodeControls.getParameterFieldLeft(node);
            int fieldTop = nodeControls.getDirectionModeTabTop(node);
            int fieldWidth = nodeControls.getParameterFieldWidth(node);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return true;
            }
        }

        if (nodeControls.isCombinedBooleanNode(node)) {
            int fieldLeft = nodeControls.getParameterFieldLeft(node);
            int fieldTop = nodeControls.getBooleanModeTabTop(node);
            int fieldWidth = nodeControls.getParameterFieldWidth(node);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return true;
            }
        }

        return isInlineDropdownParameter(node, getParameterFieldIndexAt(node, mouseX, mouseY))
            || isPointInsideModeField(node, mouseX, mouseY);
    }

    public boolean handleStartButtonClick(int mouseX, int mouseY) {
        lastStartButtonTriggeredExecution = false;
        Node startNode = findStartNodeAt(mouseX, mouseY);
        if (startNode == null) {
            return false;
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);

        hoveredStartNode = startNode;

        ExecutionManager manager = ExecutionManager.getInstance();
        manager.setWorkspaceGraph(nodes, connections, routineRegistry);
        if (manager.isChainActive(startNode)) {
            return manager.requestStopForStart(startNode);
        }

        boolean started = manager.executeBranch(startNode, nodes, connections, activePreset);
        if (started) {
            lastStartButtonTriggeredExecution = true;
        }
        return started;
    }

    private Node findStartNodeAt(int mouseX, int mouseY) {
        rebuildHierarchyCacheIfNeeded();
        for (Node node : nodes) {
            if (!intersectsViewport(node)) {
                continue;
            }
            if (node.getType() == NodeType.START && isMouseOverStartButton(node, mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }
    
    
    /**
     * Check if a node should show parameters (Start and End nodes don't)
     */
    public boolean shouldShowParameters(Node node) {
        if (node == null) {
            return false;
        }
        if (rendersInlineParameters(node)) {
            return node.hasParameters() || node.supportsModeSelection();
        }
        return false;
    }

    public boolean didLastStartButtonTriggerExecution() {
        return lastStartButtonTriggeredExecution;
    }

    public void setExecutionEnabled(boolean enabled) {
        this.executionEnabled = enabled;
    }

    public boolean containsBaritoneNodes() {
        for (Node node : nodes) {
            if (node != null && node.getType() != null && node.getType().requiresBaritone()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsUiUtilsNodes() {
        for (Node node : nodes) {
            if (node != null && node.getType() != null && node.getType().requiresUiUtils()) {
                return true;
            }
        }
        return false;
    }

    private void updateCascadeDeletionPreview() {
        cascadeDeletionPreviewNodes.clear();
        boolean selectionOverSidebar = false;
        if (multiDragActive && selectedNodes != null && !selectedNodes.isEmpty()) {
            selectionOverSidebar = isSelectionOverSidebar(sidebarWidthForRendering);
        }
        for (Node node : nodes) {
            if (!shouldCascadeDelete(node)) {
                continue;
            }
            if (!node.isDragging()) {
                continue;
            }
            if (multiDragActive) {
                if (!selectionOverSidebar) {
                    continue;
                }
            } else {
                int screenX = node.getX() - cameraX;
                if (!isNodeOverSidebar(node, sidebarWidthForRendering, screenX, node.getWidth())) {
                    continue;
                }
            }
            List<Node> removalOrder = new ArrayList<>();
            collectNodesForCascade(node, removalOrder, new HashSet<>());
            cascadeDeletionPreviewNodes.addAll(removalOrder);
        }
    }
    
    /**
     * Save the current node graph to disk
     */
    public boolean save() {
        stickyNoteController.cancelDeferredSave();
        stickyNoteController.commitPendingEdit();
        boolean saved = workspaceSaveHandler != null
            ? workspaceSaveHandler.getAsBoolean()
            : NodeGraphPersistence.saveNodeGraphForPreset(activePreset, nodes, connections, routineRegistry);
        if (saved) {
            workspaceDirty = false;
            invalidateTemplatePreviewCachesForPreset(activePreset);
        }
        return saved;
    }

    public void setWorkspaceSaveHandler(java.util.function.BooleanSupplier workspaceSaveHandler) {
        this.workspaceSaveHandler = workspaceSaveHandler;
    }

    /**
     * Load a node graph from disk, replacing the current one
     */
    public boolean load() {
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphForPreset(activePreset);
        if (data != null) {
            boolean applied = applyLoadedData(data);
            if (applied) {
                workspaceDirty = false;
                invalidateAllTemplatePreviewCaches();
            }
            return applied;
        }
        return false;
    }

    public boolean importFromPath(Path savePath) {
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        if (data != null) {
            boolean applied = applyLoadedData(data);
            if (applied) {
                markWorkspaceDirty();
            }
            return applied;
        }
        return false;
    }

    public boolean exportToPath(Path savePath) {
        boolean saved = NodeGraphPersistence.saveNodeGraphToPath(nodes, connections, savePath);
        if (saved) {
            workspaceDirty = false;
        }
        return saved;
    }

    public void markWorkspaceDirty() {
        workspaceDirty = true;
        invalidateValidation();
        invalidateRenderCaches();
        save();
    }

    public void markWorkspaceClean() {
        workspaceDirty = false;
        invalidateValidation();
    }

    public boolean isWorkspaceDirty() {
        return workspaceDirty;
    }

    public void notifyNodeParametersChanged(Node node) {
        if (node == null) {
            return;
        }
        if (node.getType() == NodeType.TEMPLATE) {
            NodeParameter presetParam = node.getParameter("Preset");
            String presetName = presetParam != null ? presetParam.getStringValue() : "";
            String normalizedPreset = presetName == null ? "" : presetName.trim();
            if (normalizedPreset.isEmpty()) {
                node.setTemplateGraphData(null);
            } else {
                NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(normalizedPreset);
                NodeGraphData.CustomNodeDefinition definition = loaded != null
                    ? NodeGraphPersistence.resolveCustomNodeDefinition(normalizedPreset, loaded)
                    : null;
                node.setTemplateGraphData(loaded);
                node.setTemplateName(definition != null ? definition.getName() : normalizedPreset);
                node.setTemplateVersion(definition != null && definition.getVersion() != null ? definition.getVersion() : 0);
            }
        }
        markWorkspaceDirty();
    }

    public GraphValidationResult getValidationResult(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        if (validationDirty) {
            List<NodeGraphData.RoutineDefinitionData> validationRoutines = routineValidationRegistry.isEmpty()
                ? routineRegistry : routineValidationRegistry;
            cachedValidationResult = GraphValidator.validate(nodes, connections, activePreset, baritoneAvailable,
                uiUtilsAvailable, validationRoutines, activeRoutineWorkspaceId);
            validationDirty = false;
        }
        return cachedValidationResult;
    }

    private void invalidateValidation() {
        validationDirty = true;
    }

    private void invalidateTemplatePreviewCachesForPreset(String presetName) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        String normalizedPreset = presetName == null ? "" : presetName.trim();
        for (Node candidate : nodes) {
            if (candidate == null || candidate.getType() != NodeType.TEMPLATE) {
                continue;
            }
            NodeParameter presetParam = candidate.getParameter("Preset");
            String selected = presetParam != null ? presetParam.getStringValue() : null;
            String normalizedSelected = selected == null ? "" : selected.trim();
            boolean usesActivePreset = normalizedSelected.isEmpty();
            boolean matchesPreset = !normalizedPreset.isEmpty() && normalizedSelected.equalsIgnoreCase(normalizedPreset);
            if (usesActivePreset || matchesPreset) {
                candidate.setTemplateGraphData(null);
            }
        }
    }

    private void invalidateAllTemplatePreviewCaches() {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (Node candidate : nodes) {
            if (candidate != null && candidate.getType() == NodeType.TEMPLATE) {
                candidate.setTemplateGraphData(null);
            }
        }
    }

    public void clearWorkspace() {
        for (Node node : new ArrayList<>(nodes)) {
            if (node.hasAttachedSensor()) {
                node.detachSensor();
            }
            if (node.hasAttachedActionNode()) {
                node.detachActionNode();
            }
            if (node.hasAttachedParameter()) {
                node.detachParameter();
            }
            if (node.isSensorNode() && node.isAttachedToControl()) {
                Node parent = node.getParentControl();
                if (parent != null) {
                    parent.detachSensor();
                }
            }
            if (node.isAttachedToActionControl()) {
                Node parent = node.getParentActionControl();
                if (parent != null) {
                    parent.detachActionNode();
                }
            }
            if (node.isParameterNode() && node.getParentParameterHost() != null) {
                Node parent = node.getParentParameterHost();
                if (parent != null) {
                    parent.detachParameter();
                }
            }
            node.setDragging(false);
            node.setSelected(false);
        }

        nodes.clear();
        connections.clear();
        invalidateRenderCaches();
        nextStartNodeNumber = 1;
        clearSelection();
        draggingNode = null;
        invalidateValidation();
        connectionController.clearGraphState();
        hoveringStartButton = false;
        hoveredStartNode = null;
        sensorDropTarget = null;
        actionDropTarget = null;
        parameterDropTarget = null;
        lastClickedNode = null;
        lastClickTime = 0;
        cascadeDeletionPreviewNodes.clear();
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
    }

    private boolean applyLoadedData(NodeGraphData data) {
        routineRegistry = new ArrayList<>(data.getRoutines());
        nodes.clear();
        connections.clear();
        invalidateRenderCaches();
        clearSelection();
        draggingNode = null;
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;

        // Load nodes and create node map for connections
        java.util.Map<String, Node> nodeMap = new java.util.HashMap<>();
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData == null || nodeData.getType() == null) {
                System.err.println("Skipping unsupported node entry while loading graph.");
                continue;
            }
            Node node = new Node(nodeData.getType(), nodeData.getX(), nodeData.getY());

            // Set the same ID using reflection
            try {
                java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(node, nodeData.getId());
            } catch (Exception e) {
                System.err.println("Failed to set node ID: " + e.getMessage());
            }

            // Set the mode if it exists (this will reinitialize parameters)
            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            NodeGraphPersistence.restoreParameters(node, nodeData.getParameters());
            node.setRoutineIdentity(nodeData.getRoutineId(), nodeData.getRoutineInputId());
            node.setRoutineArguments(nodeData.getRoutineArguments());
            if ((node.getType() == NodeType.STOP_CHAIN || node.getType() == NodeType.START_CHAIN)
                && node.getParameter("StartNumber") == null) {
                node.getParameters().add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
            }
            if ((node.getType() == NodeType.RUN_PRESET || node.getType() == NodeType.TEMPLATE) && node.getParameter("Preset") == null) {
                node.getParameters().add(new NodeParameter("Preset", ParameterType.STRING, ""));
            }
            node.ensureVillagerTradeNumberParameter();
            if (node.supportsRuntimeValueScope()) {
                node.setRuntimeValueScope(nodeData.getRuntimeValueScope());
            }
            Integer startNodeNumber = nodeData.getStartNodeNumber();
            if (startNodeNumber != null) {
                node.setStartNodeNumber(startNodeNumber);
            }
            node.setStartLaunchMode(nodeData.getStartLaunchMode());
            node.setStartScreenTarget(nodeData.getStartScreenTarget());
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                Boolean storedValue = nodeData.getKeyPressedActivatesInGuis();
                node.setKeyPressedActivatesInGuis(storedValue == null || storedValue);
            }
            if (node.hasBooleanToggle()) {
                Boolean storedToggle = nodeData.getBooleanToggleValue();
                node.setBooleanToggleValue(storedToggle == null || storedToggle);
            }
            node.setBooleanOperatorSlotCount(nodeData.getParameterSlotCount());
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

        normalizeStartNodeNumbers();

        // Restore sensor attachments
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getAttachedSensorId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node sensor = nodeMap.get(nodeData.getAttachedSensorId());
                if (control != null && sensor != null) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getParentControlId() != null) {
                Node sensor = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentControlId());
                if (sensor != null && control != null && sensor.isSensorNode()) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getAttachedActionId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node child = nodeMap.get(nodeData.getAttachedActionId());
                if (control != null && child != null) {
                    control.attachActionNode(child);
                }
            }
            if (nodeData.getMessageLines() != null) {
                Node messageNode = nodeMap.get(nodeData.getId());
                if (messageNode != null && messageNode.hasMessageInputFields()) {
                    messageNode.setMessageLines(nodeData.getMessageLines());
                    if (messageNode.hasMessageScopeToggle()) {
                        messageNode.setMessageClientSide(Boolean.TRUE.equals(nodeData.getMessageClientSide()));
                    }
                }
            }
            Node textNode = nodeMap.get(nodeData.getId());
            if (textNode != null && textNode.hasBookTextInput() && nodeData.getBookText() != null) {
                textNode.setBookText(nodeData.getBookText());
            }
            if (textNode != null && textNode.isStickyNote()) {
                textNode.setStickyNoteText(nodeData.getStickyNoteText());
                Integer stickyNoteWidth = nodeData.getStickyNoteWidth();
                Integer stickyNoteHeight = nodeData.getStickyNoteHeight();
                if (stickyNoteWidth != null || stickyNoteHeight != null) {
                    textNode.setStickyNoteSize(
                        stickyNoteWidth != null ? stickyNoteWidth : textNode.getWidth(),
                        stickyNoteHeight != null ? stickyNoteHeight : textNode.getHeight()
                    );
                }
            }
            if (textNode != null && textNode.getType() == NodeType.TEMPLATE) {
                textNode.setTemplateName(nodeData.getTemplateName());
                textNode.setTemplateVersion(nodeData.getTemplateVersion() != null ? nodeData.getTemplateVersion() : 0);
                textNode.setTemplateGraphData(nodeData.getTemplateGraph());
            }
            if (textNode != null && textNode.getType() == NodeType.SENSOR_KEY_PRESSED) {
                Boolean storedValue = nodeData.getKeyPressedActivatesInGuis();
                textNode.setKeyPressedActivatesInGuis(storedValue == null || storedValue);
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getParentActionControlId() != null) {
                Node child = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentActionControlId());
                if (child != null && control != null && control.canAcceptActionNode(child)) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                Node host = nodeMap.get(nodeData.getId());
                if (host != null) {
                    attachments.sort(java.util.Comparator.comparingInt(NodeGraphData.ParameterAttachmentData::getSlotIndex));
                    for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
                        Node parameter = nodeMap.get(attachment.getParameterNodeId());
                        if (parameter != null) {
                            int slot = host.getType() == NodeType.ROUTINE_CALL && attachment.getRoutineInputId() != null
                                && !attachment.getRoutineInputId().isBlank()
                                ? host.getRoutineSlotForInputId(attachment.getRoutineInputId()) : attachment.getSlotIndex();
                            if (slot >= 0) host.attachParameter(parameter, slot);
                        }
                    }
                }
            } else if (nodeData.getAttachedParameterId() != null) {
                Node host = nodeMap.get(nodeData.getId());
                Node parameter = nodeMap.get(nodeData.getAttachedParameterId());
                if (host != null && parameter != null && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                continue;
            }
            if (nodeData.getParentParameterHostId() != null) {
                Node parameter = nodeMap.get(nodeData.getId());
                Node host = nodeMap.get(nodeData.getParentParameterHostId());
                if (parameter != null && host != null && parameter.isParameterNode()
                    && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        NodeGraphPersistence.recoverMissingNestedAttachments(nodes);

        syncRoutineInvocations();

        // Load connections
        for (NodeGraphData.ConnectionData connData : data.getConnections()) {
            Node outputNode = nodeMap.get(connData.getOutputNodeId());
            Node inputNode = nodeMap.get(connData.getInputNodeId());

            if (outputNode != null && inputNode != null) {
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                addConnectionReplacingConflicts(outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket());
            } else {
                System.err.println("Failed to restore connection: missing node(s)");
            }
        }

        sensorDropTarget = null;
        actionDropTarget = null;
        connectionController.clearGraphState();
        hoveringStartButton = false;
        hoveredStartNode = null;
        lastClickedNode = null;
        lastClickTime = 0;
        cascadeDeletionPreviewNodes.clear();
        invalidateValidation();
        restoreSessionViewportState();

        return true;
    }

    /**
     * Check if there's a saved node graph available
     */
    public boolean hasSavedGraph() {
        return NodeGraphPersistence.hasSavedNodeGraph(activePreset);
    }

    public NodeGraphData exportGraphDataSnapshot() {
        NodeGraphData snapshot = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        snapshot.setRoutines(new ArrayList<>(routineRegistry));
        return snapshot;
    }

    public boolean applyGraphDataSnapshot(NodeGraphData data, boolean markDirty) {
        if (data == null) {
            return false;
        }
        boolean applied = applyLoadedData(data);
        if (applied) {
            if (markDirty) {
                workspaceDirty = true;
            } else {
                workspaceDirty = false;
            }
            invalidateValidation();
        }
        return applied;
    }

    public void setActivePreset(String presetName) {
        String previousPreset = this.activePreset;
        if (Objects.equals(previousPreset, presetName)) {
            restoreSessionViewportState();
            return;
        }
        cacheSessionViewportState();
        this.activePreset = presetName;
        invalidateTemplatePreviewCachesForPreset(previousPreset);
        invalidateTemplatePreviewCachesForPreset(presetName);
        invalidateValidation();
        restoreSessionViewportState();
    }

    public String getActivePreset() {
        return activePreset;
    }

    public void setActiveRoutineWorkspaceId(String routineId) {
        String resolved = routineId == null ? "" : routineId;
        if (!resolved.equals(activeRoutineWorkspaceId)) invalidateValidation();
        activeRoutineWorkspaceId = resolved;
    }

    public String getActiveRoutineWorkspaceId() {
        return activeRoutineWorkspaceId;
    }

    public List<NodeGraphData.RoutineDefinitionData> getRoutineDefinitions() {
        return List.copyOf(routineRegistry);
    }

    public void setRoutineValidationContext(List<NodeGraphData.RoutineDefinitionData> routines) {
        List<NodeGraphData.RoutineDefinitionData> resolved = routines == null ? List.of() : List.copyOf(routines);
        if (!resolved.equals(routineValidationRegistry)) {
            routineValidationRegistry = resolved;
            invalidateValidation();
        }
    }

    /** Mirrors live routine card edits into sidebar metadata, including the current uncommitted text buffer. */
    public void syncRoutineDefinitionMetadata(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null || routine.getId() == null || !routine.getId().equals(activeRoutineWorkspaceId)) {
            return;
        }
        for (Node node : nodes) {
            if (node == null || !routine.getId().equals(node.getRoutineId())) continue;
            if (node.getType() == NodeType.ROUTINE_ENTRY) {
                String name = liveRoutineParameterValue(node, "Name");
                if (!name.isBlank()) routine.setName(name.trim());
                continue;
            }
            if (node.getType() != NodeType.ROUTINE_INPUT || node.getRoutineInputId().isBlank()) continue;
            NodeGraphData.RoutineInputData input = routine.getInputs().stream()
                .filter(candidate -> node.getRoutineInputId().equals(candidate.getId())).findFirst().orElse(null);
            if (input == null) continue;
            String label = liveRoutineParameterValue(node, "Label");
            if (!label.isBlank()) input.setLabel(label.trim());
            input.setValueKind(com.pathmind.routines.RoutineValueKind.fromSerialized(
                liveRoutineParameterValue(node, "ValueKind")).name());
            input.setDefaultValue(liveRoutineParameterValue(node, "Default"));
            input.setRequired(Boolean.parseBoolean(liveRoutineParameterValue(node, "Required")));
        }
        syncRoutineInvocations();
    }

    private void syncRoutineInvocations() {
        if (activeRoutineWorkspaceId != null && !activeRoutineWorkspaceId.isBlank()) return;
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.ROUTINE_CALL) continue;
            routineRegistry.stream().filter(routine -> node.getRoutineId().equals(routine.getId())).findFirst()
                .ifPresent(node::syncRoutineCallDefinition);
        }
    }

    private String liveRoutineParameterValue(Node node, String parameterName) {
        NodeParameter parameter = node.getParameter(parameterName);
        String value = parameter == null ? "" : parameter.getStringValue();
        if (parameterEditor.getNode() == node && parameterEditor.getIndex() >= 0
            && parameterEditor.getIndex() < node.getParameters().size()
            && node.getParameters().get(parameterEditor.getIndex()) == parameter) {
            value = parameterEditor.getBuffer();
        }
        if (inlineFields.getEventNameEditingNode() == node && "Name".equals(parameterName)) {
            value = inlineFields.getEventNameEditor().getBuffer();
        }
        return value == null ? "" : value;
    }
}
