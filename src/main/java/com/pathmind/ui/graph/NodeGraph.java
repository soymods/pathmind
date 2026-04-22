package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.menu.ContextMenuRenderer;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.TextRenderUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.Registries;

import java.nio.file.Files;
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
import java.util.stream.Stream;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;

/**
 * Manages the node graph for the Pathmind visual editor.
 * Handles node rendering, connections, and interactions.
 */
public class NodeGraph {
    private static final int CONNECTION_DOT_SPACING = 12;
    private static final int CONNECTION_DOT_LENGTH = 4;
    private static final int CONNECTION_ANIMATION_STEP_MS = 50;
    private static final int DUPLICATE_OFFSET_X = 32;
    private static final int DUPLICATE_OFFSET_Y = 24;
    private static final int SELECTION_BOX_MIN_DRAG = 3;
    private static final int CONNECTION_CLICK_THRESHOLD = 4;
    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int GRID_SNAP_SIZE = 20;
    private static final int TEMPLATE_PREVIEW_MARGIN = 6;
    private static final int TEMPLATE_PREVIEW_TOP = 42;
    private static final int TEMPLATE_PREVIEW_BOTTOM_MARGIN = 6;
    private static final int VIEWPORT_CULL_MARGIN = 64;
    private static final int STICKY_NOTE_MAX_CHARS = 4096;

    private final List<Node> nodes;
    private final List<NodeConnection> connections;
    private Node selectedNode;
    private final LinkedHashSet<Node> selectedNodes;
    private Node draggingNode;
    private int draggingNodeStartX;
    private int draggingNodeStartY;
    private boolean draggingNodeDetached;
    private Node resizingStickyNote;
    private StickyNoteResizeCorner stickyNoteResizeCorner;
    private int stickyNoteResizeStartX;
    private int stickyNoteResizeStartY;
    private int stickyNoteResizeStartWidth;
    private int stickyNoteResizeStartHeight;
    private NodeGraphData pendingDragUndoSnapshot = null;
    private boolean dragOperationChanged = false;
    
    // Camera/viewport for infinite scrolling
    private int cameraX = 0;
    private int cameraY = 0;
    private boolean isPanning = false;
    private int panStartX, panStartY;
    private int panStartCameraX, panStartCameraY;
    
    // Connection dragging state
    private boolean isDraggingConnection = false;
    private Node connectionSourceNode;
    private int connectionSourceSocket;
    private boolean isOutputSocket; // true if dragging from output, false if from input
    private int connectionDragX, connectionDragY;
    private int connectionDragStartX, connectionDragStartY;
    private boolean connectionDragMoved = false;
    private Node hoveredNode = null;
    private int hoveredSocket = -1;
    private boolean hoveredSocketIsInput = false;
    
    // Store the original connection that was disconnected
    private NodeConnection disconnectedConnection = null;
    
    // Socket hover state
    private Node hoveredSocketNode = null;
    private int hoveredSocketIndex = -1;

    // Start button hover state
    private boolean hoveringStartButton = false;
    private Node hoveredStartNode = null;
    private boolean lastStartButtonTriggeredExecution = false;

    private Node sensorDropTarget = null;
    private Node actionDropTarget = null;
    private Node parameterDropTarget = null;
    private Integer parameterDropSlotIndex = null;
    private final Map<Node, AnimatedValue> messageScopeAnimations = new WeakHashMap<>();
    private final Map<Node, AnimatedValue> booleanToggleAnimations = new WeakHashMap<>();
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

    private String activePreset;
    private final Set<Node> cascadeDeletionPreviewNodes;

    private static final long COORDINATE_CARET_BLINK_INTERVAL_MS = 500;
    private static final int PARAMETER_INPUT_HEIGHT = 16;
    private static final int PARAMETER_INPUT_GAP = 4;
    private static final int DIRECTION_MODE_TAB_HEIGHT = 18;

    private Node coordinateEditingNode = null;
    private int coordinateEditingAxis = -1;
    private String coordinateEditBuffer = "";
    private String coordinateEditOriginalValue = "";
    private long coordinateCaretLastToggleTime = 0L;
    private boolean coordinateCaretVisible = true;
    private Node amountEditingNode = null;
    private String amountEditBuffer = "";
    private String amountEditOriginalValue = "";
    private long amountCaretLastToggleTime = 0L;
    private boolean amountCaretVisible = true;
    private int coordinateCaretPosition = 0;
    private int coordinateSelectionStart = -1;
    private int coordinateSelectionEnd = -1;
    private int coordinateSelectionAnchor = -1;
    private Node screenCoordinateCaptureNode = null;
    private int screenCoordinatePreviewX = 0;
    private int screenCoordinatePreviewY = 0;
    private int amountCaretPosition = 0;
    private int amountSelectionStart = -1;
    private int amountSelectionEnd = -1;
    private int amountSelectionAnchor = -1;
    private Node messageEditingNode = null;
    private int messageEditingIndex = -1;
    private String messageEditBuffer = "";
    private String messageEditOriginalValue = "";
    private long messageCaretLastToggleTime = 0L;
    private boolean messageCaretVisible = true;
    private int messageCaretPosition = 0;
    private int messageSelectionStart = -1;
    private int messageSelectionEnd = -1;
    private int messageSelectionAnchor = -1;
    private Node stickyNoteEditingNode = null;
    private String stickyNoteEditBuffer = "";
    private String stickyNoteEditOriginalValue = "";
    private long stickyNoteCaretLastToggleTime = 0L;
    private boolean stickyNoteCaretVisible = true;
    private int stickyNoteCaretPosition = 0;
    private Node eventNameEditingNode = null;
    private String eventNameEditBuffer = "";
    private String eventNameEditOriginalValue = "";
    private long eventNameCaretLastToggleTime = 0L;
    private boolean eventNameCaretVisible = true;
    private int eventNameCaretPosition = 0;
    private int eventNameSelectionStart = -1;
    private int eventNameSelectionEnd = -1;
    private int eventNameSelectionAnchor = -1;
    private Node parameterEditingNode = null;
    private int parameterEditingIndex = -1;
    private String parameterEditBuffer = "";
    private String parameterEditOriginalValue = "";
    private long parameterCaretLastToggleTime = 0L;
    private boolean parameterCaretVisible = true;
    private int parameterCaretPosition = 0;
    private int parameterSelectionStart = -1;
    private int parameterSelectionEnd = -1;
    private int parameterSelectionAnchor = -1;
    private Node stopTargetEditingNode = null;
    private String stopTargetEditBuffer = "";
    private String stopTargetEditOriginalValue = "";
    private long stopTargetCaretLastToggleTime = 0L;
    private boolean stopTargetCaretVisible = true;
    private int stopTargetCaretPosition = 0;
    private int stopTargetSelectionStart = -1;
    private int stopTargetSelectionEnd = -1;
    private int stopTargetSelectionAnchor = -1;
    private Node variableEditingNode = null;
    private String variableEditBuffer = "";
    private String variableEditOriginalValue = "";
    private long variableCaretLastToggleTime = 0L;
    private boolean variableCaretVisible = true;
    private int variableCaretPosition = 0;
    private int variableSelectionStart = -1;
    private int variableSelectionEnd = -1;
    private int variableSelectionAnchor = -1;
    private Node schematicDropdownNode = null;
    private boolean schematicDropdownOpen = false;
    private final AnimatedValue schematicDropdownAnimation = AnimatedValue.forHover();
    private java.util.List<String> schematicDropdownOptions = new java.util.ArrayList<>();
    private int schematicDropdownScrollOffset = 0;
    private int schematicDropdownHoverIndex = -1;
    private Node runPresetDropdownNode = null;
    private boolean runPresetDropdownOpen = false;
    private final AnimatedValue runPresetDropdownAnimation = AnimatedValue.forHover();
    private java.util.List<String> runPresetDropdownOptions = new java.util.ArrayList<>();
    private int runPresetDropdownScrollOffset = 0;
    private int runPresetDropdownHoverIndex = -1;
    private static final int SCHEMATIC_DROPDOWN_MAX_ROWS = 8;
    private static final int SCHEMATIC_DROPDOWN_ROW_HEIGHT = 16;
    private static final int RANDOM_ROUNDING_DROPDOWN_MAX_ROWS = 4;
    private Node parameterDropdownNode = null;
    private int parameterDropdownIndex = -1;
    private boolean parameterDropdownOpen = false;
    private final AnimatedValue parameterDropdownAnimation = AnimatedValue.forHover();
    private int parameterDropdownHoverIndex = -1;
    private int parameterDropdownScrollOffset = 0;
    private int parameterDropdownFieldX = 0;
    private int parameterDropdownFieldY = 0;
    private int parameterDropdownFieldWidth = 0;
    private int parameterDropdownFieldHeight = 0;
    private String parameterDropdownQuery = "";
    private final java.util.List<ParameterDropdownOption> parameterDropdownOptions = new java.util.ArrayList<>();
    private Node parameterDropdownSuppressedNode = null;
    private int parameterDropdownSuppressedIndex = -1;
    private String parameterDropdownSuppressedQuery = "";
    private Node randomRoundingDropdownNode = null;
    private boolean randomRoundingDropdownOpen = false;
    private final AnimatedValue randomRoundingDropdownAnimation = AnimatedValue.forHover();
    private int randomRoundingDropdownHoverIndex = -1;
    private int randomRoundingDropdownScrollOffset = 0;
    private Node modeDropdownNode = null;
    private boolean modeDropdownOpen = false;
    private final AnimatedValue modeDropdownAnimation = AnimatedValue.forHover();
    private int modeDropdownHoverIndex = -1;
    private int modeDropdownScrollOffset = 0;
    private int modeDropdownFieldX = 0;
    private int modeDropdownFieldY = 0;
    private int modeDropdownFieldWidth = 0;
    private int modeDropdownFieldHeight = 0;
    private final java.util.List<ModeDropdownOption> modeDropdownOptions = new java.util.ArrayList<>();
    private Node amountSignDropdownNode = null;
    private boolean amountSignDropdownOpen = false;
    private final AnimatedValue amountSignDropdownAnimation = AnimatedValue.forHover();
    private int amountSignDropdownHoverIndex = -1;
    private int amountSignDropdownScrollOffset = 0;
    private static final int AMOUNT_SIGN_DROPDOWN_MAX_ROWS = 5;
    private static final int PARAMETER_DROPDOWN_MAX_ROWS = 8;
    private static final int PARAMETER_DROPDOWN_ROW_HEIGHT = 16;
    private static final int DROPDOWN_SIDE_PADDING = 6;
    private static final int DROPDOWN_SCROLLBAR_ALLOWANCE = 8;
    private static final int PARAMETER_DROPDOWN_ICON_ALLOWANCE = 24;
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
    private int selectionBoxStartX = 0;
    private int selectionBoxStartY = 0;
    private int selectionBoxCurrentX = 0;
    private int selectionBoxCurrentY = 0;
    private boolean multiDragActive = false;
    private final Map<Node, DragStartInfo> multiDragStartPositions = new HashMap<>();
    private boolean selectionDeletionPreviewActive = false;

    private enum StickyNoteResizeCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

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

    private enum NodeStyle {
        STANDARD,
        MINIMAL
    }

    private static final class ClipboardSnapshot {
        private final NodeGraphData data;
        private final List<String> selectionIds;
        private final int anchorX;
        private final int anchorY;

        private ClipboardSnapshot(NodeGraphData data, List<String> selectionIds, int anchorX, int anchorY) {
            this.data = data;
            this.selectionIds = selectionIds;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    private static final class SelectionBounds {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private SelectionBounds(int minX, int minY, int maxX, int maxY) {
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
        float clampedScale = MathHelper.clamp(newScale, minScale, maxScale);
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

    private void assignNewStartNodeNumber(Node node) {
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
        if (node != null && node.getType() == NodeType.START && node.getStartNodeNumber() <= 0) {
            assignNewStartNodeNumber(node);
        }
        nodes.add(node);
    }

    public void removeNode(Node node) {
        if (node == null) {
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

        if (coordinateEditingNode == node) {
            stopCoordinateEditing(false);
        }

        if (amountEditingNode == node) {
            stopAmountEditing(false);
        }
        if (stopTargetEditingNode == node) {
            stopStopTargetEditing(false);
        }
        if (variableEditingNode == node) {
            stopVariableEditing(false);
        }
        if (messageEditingNode == node) {
            stopMessageEditing(false);
        }

        if (runPresetDropdownNode == node) {
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

                        connections.add(new NodeConnection(inputSource, outputTarget, inputSocket, outputSocket));
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
    }

    public Node getNodeAt(int x, int y) {
        int worldX = screenToWorldX(x);
        int worldY = screenToWorldY(y);
        return getNodeAtWorld(worldX, worldY);
    }

    private Node getNodeAtWorld(int worldX, int worldY) {
        Set<Node> processedRoots = new HashSet<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            Node root = getRootNode(node);
            if (root == null || processedRoots.contains(root)) {
                continue;
            }
            processedRoots.add(root);
            Node hit = findNodeInHierarchyAt(root, worldX, worldY);
            if (hit != null) {
                return hit;
            }
        }
        return null;
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

        if (node.containsPoint(worldX, worldY)) {
            return node;
        }

        return null;
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

    private SelectionBounds calculateBounds(Collection<Node> nodesToMeasure) {
        if (nodesToMeasure == null || nodesToMeasure.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Node node : nodesToMeasure) {
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + node.getWidth());
            maxY = Math.max(maxY, node.getY() + node.getHeight());
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new SelectionBounds(minX, minY, maxX, maxY);
    }

    private SelectionBounds calculateHierarchyBounds(Node root) {
        if (root == null) {
            return null;
        }
        List<Node> hierarchyNodes = new ArrayList<>();
        collectHierarchyNodes(root, hierarchyNodes, new HashSet<>());
        return calculateBounds(hierarchyNodes);
    }

    private void collectHierarchyNodes(Node node, List<Node> collected, Set<Node> visited) {
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getScaledWidth() / Math.max(0.0001f, getZoomScale()));
    }

    private int getViewportWorldHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0;
        }
        return Math.round(client.getWindow().getScaledHeight() / Math.max(0.0001f, getZoomScale()));
    }

    private boolean intersectsViewport(SelectionBounds bounds) {
        if (bounds == null) {
            return false;
        }
        int viewportLeft = cameraX - VIEWPORT_CULL_MARGIN;
        int viewportTop = cameraY - VIEWPORT_CULL_MARGIN;
        int viewportRight = cameraX + getViewportWorldWidth() + VIEWPORT_CULL_MARGIN;
        int viewportBottom = cameraY + getViewportWorldHeight() + VIEWPORT_CULL_MARGIN;
        return bounds.maxX >= viewportLeft
            && bounds.minX <= viewportRight
            && bounds.maxY >= viewportTop
            && bounds.minY <= viewportBottom;
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

    public void beginSelectionBox(int screenX, int screenY) {
        selectionBoxActive = true;
        selectionBoxStartX = screenX;
        selectionBoxStartY = screenY;
        selectionBoxCurrentX = screenX;
        selectionBoxCurrentY = screenY;
    }

    public void updateSelectionBox(int screenX, int screenY) {
        if (!selectionBoxActive) {
            return;
        }
        selectionBoxCurrentX = screenX;
        selectionBoxCurrentY = screenY;
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
        int deltaX = Math.abs(selectionBoxCurrentX - selectionBoxStartX);
        int deltaY = Math.abs(selectionBoxCurrentY - selectionBoxStartY);
        return deltaX >= SELECTION_BOX_MIN_DRAG || deltaY >= SELECTION_BOX_MIN_DRAG;
    }

    private void applySelectionBoxSelection() {
        int left = Math.min(selectionBoxStartX, selectionBoxCurrentX);
        int right = Math.max(selectionBoxStartX, selectionBoxCurrentX);
        int top = Math.min(selectionBoxStartY, selectionBoxCurrentY);
        int bottom = Math.max(selectionBoxStartY, selectionBoxCurrentY);
        int worldLeft = screenToWorldX(left);
        int worldRight = screenToWorldX(right);
        int worldTop = screenToWorldY(top);
        int worldBottom = screenToWorldY(bottom);

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

    private void bringNodeToFront(Node node) {
        if (node == null) {
            return;
        }
        Node root = getRootNode(node);
        List<Node> hierarchy = new ArrayList<>();
        collectHierarchy(root, hierarchy, new HashSet<>());
        // Remove all hierarchy nodes from current ordering
        for (Node member : hierarchy) {
            nodes.remove(member);
        }
        // Append in hierarchy order so they render above others
        nodes.addAll(hierarchy);
    }

    private Node getRootNode(Node node) {
        Node current = node;
        Node parent;
        while ((parent = getParentForNode(current)) != null) {
            current = parent;
        }
        return current;
    }

    private void collectHierarchy(Node node, List<Node> result, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        result.add(node);

        Node actionChild = node.getAttachedActionNode();
        collectHierarchy(actionChild, result, visited);

        Node sensorChild = node.getAttachedSensor();
        collectHierarchy(sensorChild, result, visited);

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            Collections.sort(keys);
            for (Integer key : keys) {
                collectHierarchy(parameterMap.get(key), result, visited);
            }
        }
    }

    public Node getSelectedNode() {
        return selectedNode;
    }

    public boolean copySelectedNodeToClipboard() {
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

    public Node duplicateSelectedNode() {
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
        if (selectedNodes.isEmpty()) {
            return false;
        }
        List<Node> targets = new ArrayList<>(selectedNodes);
        pushUndoState();
        for (Node node : targets) {
            removeNodeCascade(node, false);
        }
        clearSelection();
        markWorkspaceDirty();
        return true;
    }

    private ClipboardSnapshot createClipboardSnapshot(Collection<Node> selection) {
        if (selection == null || selection.isEmpty()) {
            return null;
        }
        List<Node> hierarchy = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        for (Node node : selection) {
            collectHierarchy(node, hierarchy, visited);
        }
        if (hierarchy.isEmpty()) {
            return null;
        }
        Set<Node> subset = new LinkedHashSet<>(hierarchy);
        List<NodeConnection> subsetConnections = new ArrayList<>();
        for (NodeConnection connection : connections) {
            if (subset.contains(connection.getOutputNode()) && subset.contains(connection.getInputNode())) {
                subsetConnections.add(connection);
            }
        }
        NodeGraphData data = buildGraphData(hierarchy, subsetConnections, subset);
        if (data == null) {
            return null;
        }
        SelectionBounds bounds = calculateBounds(subset);
        int anchorX = bounds != null ? bounds.minX : 0;
        int anchorY = bounds != null ? bounds.minY : 0;
        List<String> selectionIds = new ArrayList<>();
        for (Node node : selection) {
            if (node != null) {
                selectionIds.add(node.getId());
            }
        }
        return new ClipboardSnapshot(data, selectionIds, anchorX, anchorY);
    }

    private Node instantiateClipboardSnapshot(ClipboardSnapshot snapshot, int targetAnchorX, int targetAnchorY) {
        if (snapshot == null || snapshot.data == null) {
            return null;
        }
        int offsetX = targetAnchorX - snapshot.anchorX;
        int offsetY = targetAnchorY - snapshot.anchorY;
        Map<String, Node> idToNode = new HashMap<>();

        for (NodeGraphData.NodeData nodeData : snapshot.data.getNodes()) {
            if (nodeData.getType() == null) {
                continue;
            }
            Node newNode = new Node(nodeData.getType(), nodeData.getX() + offsetX, nodeData.getY() + offsetY);
            if (nodeData.getType() == NodeType.START) {
                assignNewStartNodeNumber(newNode);
            }
            if (nodeData.getMode() != null) {
                newNode.setMode(nodeData.getMode());
            }
            List<NodeGraphData.ParameterData> params = nodeData.getParameters();
            if (params != null) {
                newNode.getParameters().clear();
                for (NodeGraphData.ParameterData paramData : params) {
                    ParameterType parameterType = ParameterType.STRING;
                    String typeName = paramData.getType();
                    if (typeName != null) {
                        try {
                            parameterType = ParameterType.valueOf(typeName);
                        } catch (IllegalArgumentException ignored) {
                            parameterType = ParameterType.STRING;
                        }
                    }
                    String value = paramData.getValue() == null ? "" : paramData.getValue();
                    NodeParameter parameter = new NodeParameter(paramData.getName(), parameterType, value);
                    if (paramData.getUserEdited() != null) {
                        parameter.setUserEdited(paramData.getUserEdited());
                    }
                    newNode.getParameters().add(parameter);
                }
                if ((newNode.getType() == NodeType.STOP_CHAIN || newNode.getType() == NodeType.START_CHAIN)
                    && newNode.getParameter("StartNumber") == null) {
                    newNode.getParameters().add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
                }
                if ((newNode.getType() == NodeType.RUN_PRESET || newNode.getType() == NodeType.TEMPLATE || newNode.getType() == NodeType.CUSTOM_NODE)
                    && newNode.getParameter("Preset") == null) {
                    newNode.getParameters().add(new NodeParameter("Preset", ParameterType.STRING, ""));
                }
            }
            newNode.ensureVillagerTradeNumberParameter();
            if (nodeData.getType() == NodeType.MESSAGE && nodeData.getMessageLines() != null) {
                newNode.setMessageLines(nodeData.getMessageLines());
                newNode.setMessageClientSide(Boolean.TRUE.equals(nodeData.getMessageClientSide()));
            }
            if (newNode.hasBookTextInput() && nodeData.getBookText() != null) {
                newNode.setBookText(nodeData.getBookText());
            }
            if (newNode.isStickyNote()) {
                newNode.setStickyNoteText(nodeData.getStickyNoteText());
                Integer stickyNoteWidth = nodeData.getStickyNoteWidth();
                Integer stickyNoteHeight = nodeData.getStickyNoteHeight();
                if (stickyNoteWidth != null || stickyNoteHeight != null) {
                    newNode.setStickyNoteSize(
                        stickyNoteWidth != null ? stickyNoteWidth : newNode.getWidth(),
                        stickyNoteHeight != null ? stickyNoteHeight : newNode.getHeight()
                    );
                }
            }
            if (newNode.getType() == NodeType.SENSOR_KEY_PRESSED) {
                Boolean storedValue = nodeData.getKeyPressedActivatesInGuis();
                newNode.setKeyPressedActivatesInGuis(storedValue == null || storedValue);
            }
            if (newNode.hasBooleanToggle()) {
                Boolean storedToggle = nodeData.getBooleanToggleValue();
                newNode.setBooleanToggleValue(storedToggle == null || storedToggle);
            }
            newNode.recalculateDimensions();
            nodes.add(newNode);
            idToNode.put(nodeData.getId(), newNode);
        }

        for (NodeGraphData.NodeData nodeData : snapshot.data.getNodes()) {
            Node parent = idToNode.get(nodeData.getId());
            if (parent == null) {
                continue;
            }
            String sensorId = nodeData.getAttachedSensorId();
            if (sensorId != null) {
                Node sensor = idToNode.get(sensorId);
                if (sensor != null) {
                    parent.attachSensor(sensor);
                }
            }
            String actionId = nodeData.getAttachedActionId();
            if (actionId != null) {
                Node action = idToNode.get(actionId);
                if (action != null) {
                    parent.attachActionNode(action);
                }
            }

            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                List<NodeGraphData.ParameterAttachmentData> ordered = new ArrayList<>(attachments);
                ordered.sort(java.util.Comparator.comparingInt(NodeGraphData.ParameterAttachmentData::getSlotIndex));
                for (NodeGraphData.ParameterAttachmentData attachment : ordered) {
                    Node parameterNode = idToNode.get(attachment.getParameterNodeId());
                    if (parameterNode != null) {
                        parent.attachParameter(parameterNode, attachment.getSlotIndex());
                    }
                }
            }
        }

        if (snapshot.data.getConnections() != null) {
            for (NodeGraphData.ConnectionData connData : snapshot.data.getConnections()) {
                Node outputNode = idToNode.get(connData.getOutputNodeId());
                Node inputNode = idToNode.get(connData.getInputNodeId());
                if (outputNode == null || inputNode == null || outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                connections.add(new NodeConnection(outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket()));
            }
        }

        List<Node> clonesForSelection = new ArrayList<>();
        if (snapshot.selectionIds != null) {
            for (String originalId : snapshot.selectionIds) {
                Node clone = idToNode.get(originalId);
                if (clone != null) {
                    clonesForSelection.add(clone);
                }
            }
        }

        Node primaryClone = null;
        if (!clonesForSelection.isEmpty()) {
            primaryClone = clonesForSelection.get(0);
            selectNodes(clonesForSelection);
            for (Node clone : clonesForSelection) {
                bringNodeToFront(clone);
            }
        } else if (!idToNode.isEmpty()) {
            primaryClone = idToNode.values().iterator().next();
            selectNode(primaryClone);
            bringNodeToFront(primaryClone);
        }

        markWorkspaceDirty();
        return primaryClone;
    }

    private NodeGraphData buildGraphData(Collection<Node> nodeCollection, Collection<NodeConnection> connectionCollection, Set<Node> allowedNodes) {
        NodeGraphData data = new NodeGraphData();
        if (nodeCollection == null) {
            return data;
        }
        Set<Node> allowed = allowedNodes != null ? allowedNodes : new LinkedHashSet<>(nodeCollection);
        for (Node node : nodeCollection) {
            if (node == null) {
                continue;
            }
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());
            nodeData.setStartNodeNumber(node.getStartNodeNumber());
            if (node.hasMessageInputFields()) {
                nodeData.setMessageLines(new ArrayList<>(node.getMessageLines()));
                nodeData.setMessageClientSide(node.isMessageClientSide());
            } else {
                nodeData.setMessageLines(null);
                nodeData.setMessageClientSide(null);
            }
            if (node.hasBookTextInput()) {
                nodeData.setBookText(node.getBookText());
            } else {
                nodeData.setBookText(null);
            }
            if (node.isStickyNote()) {
                nodeData.setStickyNoteText(node.getStickyNoteText());
                nodeData.setStickyNoteWidth(node.getStickyNoteWidthOverride());
                nodeData.setStickyNoteHeight(node.getStickyNoteHeightOverride());
            } else {
                nodeData.setStickyNoteText(null);
                nodeData.setStickyNoteWidth(null);
                nodeData.setStickyNoteHeight(null);
            }
            if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
                nodeData.setTemplateName(node.getTemplateName());
                nodeData.setTemplateVersion(node.getTemplateVersion());
                nodeData.setCustomNodeInstance(node.isCustomNodeInstance());
                nodeData.setTemplateGraph(node.getTemplateGraphData());
            } else {
                nodeData.setTemplateName(null);
                nodeData.setTemplateVersion(null);
                nodeData.setCustomNodeInstance(null);
                nodeData.setTemplateGraph(null);
            }
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                nodeData.setKeyPressedActivatesInGuis(node.isKeyPressedActivatesInGuis());
            } else {
                nodeData.setKeyPressedActivatesInGuis(null);
            }

            List<NodeGraphData.ParameterData> paramDataList = new ArrayList<>();
            for (NodeParameter param : node.getParameters()) {
                NodeGraphData.ParameterData paramData = new NodeGraphData.ParameterData();
                paramData.setName(param.getName());
                paramData.setValue(param.getStringValue());
                paramData.setType(param.getType().name());
                paramData.setUserEdited(param.isUserEdited());
                paramDataList.add(paramData);
            }
            nodeData.setParameters(paramDataList);

            Node attachedSensor = node.getAttachedSensor();
            nodeData.setAttachedSensorId(attachedSensor != null && allowed.contains(attachedSensor) ? attachedSensor.getId() : null);

            Node parentControl = node.getParentControl();
            nodeData.setParentControlId(parentControl != null && allowed.contains(parentControl) ? parentControl.getId() : null);

            Node attachedAction = node.getAttachedActionNode();
            nodeData.setAttachedActionId(attachedAction != null && allowed.contains(attachedAction) ? attachedAction.getId() : null);

            Node parentActionControl = node.getParentActionControl();
            nodeData.setParentActionControlId(parentActionControl != null && allowed.contains(parentActionControl) ? parentActionControl.getId() : null);

            List<NodeGraphData.ParameterAttachmentData> attachmentData = new ArrayList<>();
            Map<Integer, Node> attachedParameters = node.getAttachedParameters();
            if (attachedParameters != null && !attachedParameters.isEmpty()) {
                List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
                Collections.sort(slotIndices);
                for (Integer slotIndex : slotIndices) {
                    Node parameterNode = attachedParameters.get(slotIndex);
                    if (parameterNode != null && allowed.contains(parameterNode)) {
                        attachmentData.add(new NodeGraphData.ParameterAttachmentData(slotIndex, parameterNode.getId()));
                    }
                }
            }
            nodeData.setParameterAttachments(attachmentData);
            if (!attachmentData.isEmpty()) {
                nodeData.setAttachedParameterId(attachmentData.get(0).getParameterNodeId());
            } else {
                nodeData.setAttachedParameterId(null);
            }

            Node parentParameterHost = node.getParentParameterHost();
            nodeData.setParentParameterHostId(parentParameterHost != null && allowed.contains(parentParameterHost) ? parentParameterHost.getId() : null);
            if (node.hasBooleanToggle()) {
                nodeData.setBooleanToggleValue(node.getBooleanToggleValue());
            } else {
                nodeData.setBooleanToggleValue(null);
            }

            data.getNodes().add(nodeData);
        }

        if (connectionCollection != null) {
            for (NodeConnection connection : connectionCollection) {
                if (connection == null) {
                    continue;
                }
                Node outputNode = connection.getOutputNode();
                Node inputNode = connection.getInputNode();
                if (!allowed.contains(outputNode) || !allowed.contains(inputNode)) {
                    continue;
                }
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                NodeGraphData.ConnectionData connData = new NodeGraphData.ConnectionData(
                    outputNode.getId(),
                    inputNode.getId(),
                    connection.getOutputSocket(),
                    connection.getInputSocket()
                );
                data.getConnections().add(connData);
            }
        }

        return data;
    }

    private void pushUndoState() {
        if (suppressUndoCapture) {
            return;
        }
        NodeGraphData snapshot = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        pushUndoSnapshot(snapshot);
    }

    private void pushUndoSnapshot(NodeGraphData snapshot) {
        if (snapshot == null || suppressUndoCapture) {
            return;
        }
        undoStack.push(snapshot);
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void restoreFromSnapshot(NodeGraphData data) {
        if (data == null) {
            return;
        }
        suppressUndoCapture = true;
        applyLoadedData(data);
        suppressUndoCapture = false;
        markWorkspaceDirty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        NodeGraphData currentState = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        NodeGraphData previousState = undoStack.pop();
        if (currentState != null) {
            redoStack.push(currentState);
        }
        restoreFromSnapshot(previousState);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        NodeGraphData currentState = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        NodeGraphData nextState = redoStack.pop();
        if (currentState != null) {
            undoStack.push(currentState);
        }
        restoreFromSnapshot(nextState);
        return true;
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
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopStickyNoteEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);
        stopMessageEditing(true);
        isDraggingConnection = true;
        connectionSourceNode = node;
        connectionSourceSocket = socketIndex;
        isOutputSocket = isOutput;
        connectionDragX = screenToWorldX(mouseX);
        connectionDragY = screenToWorldY(mouseY);
        connectionDragStartX = connectionDragX;
        connectionDragStartY = connectionDragY;
        connectionDragMoved = false;
        hoveredNode = null;
        hoveredSocket = -1;
        hoveredSocketIsInput = false;
        
        // Find and disconnect existing connection from this socket
        disconnectedConnection = null;
        if (isOutput) {
            // Dragging from output socket - find connection that starts from this socket
            for (NodeConnection conn : connections) {
                if (conn.getOutputNode().equals(node) && conn.getOutputSocket() == socketIndex) {
                    disconnectedConnection = conn;
                    connections.remove(conn);
                    break;
                }
            }
        } else {
            // Dragging from input socket - find connection that ends at this socket
            for (NodeConnection conn : connections) {
                if (conn.getInputNode().equals(node) && conn.getInputSocket() == socketIndex) {
                    disconnectedConnection = conn;
                    connections.remove(conn);
                    break;
                }
            }
        }
        
    }

    public void updateDrag(int mouseX, int mouseY) {
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        if (resizingStickyNote != null && stickyNoteResizeCorner != null) {
            updateStickyNoteResize(worldMouseX, worldMouseY);
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
                draggingNode.setSocketsHidden(false);
                resetDropTargets();
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

                    boolean hideSockets = false;
                    resetDropTargets();
                    boolean parameterCandidate = draggingNode.getType() == NodeType.SENSOR_POSITION_OF
                        || draggingNode.getType() == NodeType.SENSOR_DISTANCE_BETWEEN
                        || draggingNode.getType() == NodeType.SENSOR_TARGETED_BLOCK_FACE
                        || draggingNode.getType() == NodeType.SENSOR_TARGETED_BLOCK
                        || draggingNode.getType() == NodeType.SENSOR_TARGETED_ENTITY
                        || draggingNode.getType() == NodeType.SENSOR_LOOK_DIRECTION
                        || draggingNode.isParameterNode()
                        || draggingNode.isSensorNode();
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
                    draggingNode.setSocketsHidden(hideSockets);
                }
            }
        }
        if (isDraggingConnection) {
            connectionDragX = worldMouseX;
            connectionDragY = worldMouseY;
            if (!connectionDragMoved) {
                int deltaX = Math.abs(connectionDragX - connectionDragStartX);
                int deltaY = Math.abs(connectionDragY - connectionDragStartY);
                if (deltaX >= CONNECTION_CLICK_THRESHOLD || deltaY >= CONNECTION_CLICK_THRESHOLD) {
                    connectionDragMoved = true;
                }
            }

            // Check for socket snapping
            hoveredNode = null;
            hoveredSocket = -1;

            for (Node node : nodes) {
                if (node == connectionSourceNode) continue;
                if (!node.shouldRenderSockets()) continue;

                // Check input sockets if dragging from output
                if (isOutputSocket) {
                    for (int i = 0; i < node.getInputSocketCount(); i++) {
                        if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                            hoveredNode = node;
                            hoveredSocket = i;
                            hoveredSocketIsInput = true;
                            break;
                        }
                    }
                } else {
                    // Check output sockets if dragging from input
                    for (int i = 0; i < node.getOutputSocketCount(); i++) {
                        if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                            hoveredNode = node;
                            hoveredSocket = i;
                            hoveredSocketIsInput = false;
                            break;
                        }
                    }
                }
                
                if (hoveredNode != null) break;
            }
        }
    }

    public void previewSidebarDrag(NodeType nodeType, int worldMouseX, int worldMouseY) {
        previewSidebarDrag(nodeType != null ? new Node(nodeType, worldMouseX, worldMouseY) : null, worldMouseX, worldMouseY);
    }

    public void previewSidebarDrag(Node candidate, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        if (candidate == null) {
            return;
        }
        NodeType nodeType = candidate.getType();
        boolean parameterCandidate = nodeType == NodeType.SENSOR_POSITION_OF
            || nodeType == NodeType.SENSOR_DISTANCE_BETWEEN
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK_FACE
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK
            || nodeType == NodeType.SENSOR_TARGETED_ENTITY
            || nodeType == NodeType.SENSOR_LOOK_DIRECTION
            || Node.isParameterType(nodeType)
            || Node.isSensorType(nodeType);
        if (parameterCandidate && trySetParameterDropTarget(candidate, worldMouseX, worldMouseY, false)) {
            return;
        }
        if (Node.isSensorType(nodeType) && trySetSensorDropTarget(null, worldMouseX, worldMouseY)) {
            return;
        }
        if (Node.isParameterType(nodeType)) {
            return;
        } else {
            for (Node node : nodes) {
                if (!node.canAcceptActionNode()) {
                    continue;
                }
                if (!node.canAcceptActionNode(candidate)) {
                    continue;
                }
                if (node.isPointInsideActionSlot(worldMouseX, worldMouseY)) {
                    actionDropTarget = node;
                    break;
                }
            }
        }
    }

    private boolean trySetParameterDropTarget(Node candidate, int worldMouseX, int worldMouseY, boolean excludeCandidateNode) {
        Node hoveredNode = getNodeAtWorld(worldMouseX, worldMouseY);
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
        }
        for (Node node : nodes) {
            if (excludeCandidateNode && node == candidate) {
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
            if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                sensorDropTarget = node;
                return true;
            }
        }
        return false;
    }

    public Node handleSidebarDrop(NodeType nodeType, int worldMouseX, int worldMouseY) {
        return handleSidebarDrop(nodeType != null ? new Node(nodeType, 0, 0) : null, worldMouseX, worldMouseY);
    }

    public Node handleSidebarDrop(Node newNode, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        if (newNode == null) {
            return null;
        }
        NodeType nodeType = newNode.getType();
        if (nodeType == NodeType.START) {
            assignNewStartNodeNumber(newNode);
        }

        boolean parameterCandidate = nodeType == NodeType.SENSOR_POSITION_OF
            || nodeType == NodeType.SENSOR_DISTANCE_BETWEEN
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK_FACE
            || nodeType == NodeType.SENSOR_TARGETED_BLOCK
            || nodeType == NodeType.SENSOR_TARGETED_ENTITY
            || nodeType == NodeType.SENSOR_LOOK_DIRECTION
            || Node.isParameterType(nodeType)
            || Node.isSensorType(nodeType);
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

        positionNewNode(newNode, worldMouseX, worldMouseY);
        nodes.add(newNode);
        markWorkspaceDirty();
        return newNode;
    }
    
    public void updateMouseHover(int mouseX, int mouseY) {
        // Reset hover state
        hoveredSocketNode = null;
        hoveredSocketIndex = -1;
        hoveringStartButton = false;
        hoveredStartNode = null;

        // Check for start button hover
        for (Node node : nodes) {
            if (node.getType() == NodeType.START && isMouseOverStartButton(node, mouseX, mouseY)) {
                hoveringStartButton = true;
                hoveredStartNode = node;
                break;
            }
        }
        
        // Don't check for socket hover if we're currently dragging a connection
        if (isDraggingConnection) {
            return;
        }

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        // Check for socket hover
        for (Node node : nodes) {
            if (!node.shouldRenderSockets()) {
                continue;
            }
            // Check input sockets
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    hoveredSocketNode = node;
                    hoveredSocketIndex = i;
                    hoveredSocketIsInput = true;
                    return;
                }
            }

            // Check output sockets
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    hoveredSocketNode = node;
                    hoveredSocketIndex = i;
                    hoveredSocketIsInput = false;
                    return;
                }
            }
        }
    }

    public void stopDragging() {
        Node rootToPromote = null;
        if (resizingStickyNote != null) {
            rootToPromote = resizingStickyNote;
            resizingStickyNote = null;
            stickyNoteResizeCorner = null;
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
                rootToPromote = getRootNode(node);
            }
        }
        if (rootToPromote != null) {
            bringNodeToFront(rootToPromote);
        }
        draggingNode = null;
        draggingNodeDetached = false;
        resetDropTargets();

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
        resizingStickyNote = null;
        stickyNoteResizeCorner = null;
        pendingDragUndoSnapshot = null;
        dragOperationChanged = false;
        if (multiDragActive) {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
        isDraggingConnection = false;
        connectionSourceNode = null;
        connectionSourceSocket = -1;
        hoveredNode = null;
        hoveredSocket = -1;
        disconnectedConnection = null;
        connectionDragMoved = false;
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

        if ((draggingNode.isParameterNode()
            || draggingNode.isSensorNode()
            || draggingNode.getType() == NodeType.SENSOR_POSITION_OF
            || draggingNode.getType() == NodeType.SENSOR_DISTANCE_BETWEEN
            || draggingNode.getType() == NodeType.SENSOR_TARGETED_BLOCK_FACE
            || draggingNode.getType() == NodeType.SENSOR_TARGETED_BLOCK
            || draggingNode.getType() == NodeType.SENSOR_TARGETED_ENTITY
            || draggingNode.getType() == NodeType.SENSOR_LOOK_DIRECTION)
            && draggingNode.getParentParameterHost() != null) {
            Node parent = draggingNode.getParentParameterHost();
            if (parent != null) {
                parent.detachParameter(draggingNode.getParentParameterSlotIndex());
            }
        }

        draggingNodeDetached = true;
        dragOperationChanged = true;
    }
    
    public void stopDraggingConnection() {
        boolean connectionChanged = false;
        if (isDraggingConnection && connectionSourceNode != null) {
            // Try to create connection if hovering over valid socket
            if (hoveredNode != null && hoveredSocket != -1) {
                if (isOutputSocket && hoveredSocketIsInput) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    // Remove any existing incoming connection to the target socket
                    connections.removeIf(conn ->
                        conn.getInputNode() == hoveredNode && conn.getInputSocket() == hoveredSocket
                    );

                    // Ensure only one outgoing connection per source socket
                    connections.removeIf(conn ->
                        conn.getOutputNode() == connectionSourceNode && conn.getOutputSocket() == connectionSourceSocket
                    );

                    // Connect output to input
                    NodeConnection newConnection = new NodeConnection(connectionSourceNode, hoveredNode, connectionSourceSocket, hoveredSocket);
                    connections.add(newConnection);
                    connectionChanged = true;
                } else if (!isOutputSocket && !hoveredSocketIsInput) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    // Remove any existing outgoing connection from the target socket
                    connections.removeIf(conn -> 
                        conn.getOutputNode() == hoveredNode && conn.getOutputSocket() == hoveredSocket
                    );
                    
                    // Connect input to output (reverse connection)
                    NodeConnection newConnection = new NodeConnection(hoveredNode, connectionSourceNode, hoveredSocket, connectionSourceSocket);
                    connections.add(newConnection);
                    connectionChanged = true;
                } else {
                    // Invalid connection - restore original
                    restoreDisconnectedConnection();
                }
            } else {
                // No valid target - decide whether to keep the removal
                if (disconnectedConnection != null && isOutputSocket && !connectionDragMoved) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    connectionChanged = true;
                } else {
                    restoreDisconnectedConnection();
                }
            }
        }

        if (connectionChanged) {
            markWorkspaceDirty();
        }
        
        isDraggingConnection = false;
        connectionSourceNode = null;
        connectionSourceSocket = -1;
        hoveredNode = null;
        hoveredSocket = -1;
        disconnectedConnection = null;
        connectionDragMoved = false;
    }

    private void restoreDisconnectedConnection() {
        if (disconnectedConnection != null) {
            connections.add(disconnectedConnection);
        }
    }

    private void captureUndoStateForConnectionChange(NodeConnection previousConnection) {
        if (suppressUndoCapture) {
            return;
        }
        boolean temporarilyAdded = false;
        if (previousConnection != null && !connections.contains(previousConnection)) {
            connections.add(previousConnection);
            temporarilyAdded = true;
        }
        pushUndoState();
        if (temporarilyAdded) {
            connections.remove(previousConnection);
        }
    }
    
    public boolean isInSidebar(int mouseX, int sidebarWidth) {
        return mouseX < sidebarWidth;
    }
    
    public boolean isAnyNodeBeingDragged() {
        return draggingNode != null || isDraggingConnection;
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
    public void renderContextMenu(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(contextMenuWorldX);
            int anchorScreenY = worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(getZoomScale());
            contextMenu.render(context, textRenderer, mouseX, mouseY);
        }
    }

    public void renderNodeContextMenu(DrawContext context, TextRenderer textRenderer) {
        if (nodeContextMenu != null && nodeContextMenu.isOpen()) {
            int anchorScreenX = worldToScreenX(nodeContextMenuWorldX);
            int anchorScreenY = worldToScreenY(nodeContextMenuWorldY);
            nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            nodeContextMenu.setScale(getZoomScale());
            nodeContextMenu.render(context, textRenderer);
        }
    }

    /**
     * Adds a node of the specified type at the given world coordinates.
     */
    public Node addNodeAtPosition(NodeType type, int worldX, int worldY) {
        Node node = new Node(type, 0, 0);
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
        if (isDraggingConnection && connectionSourceNode != null) {
            if (!targetNode.shouldRenderSockets()) {
                return false;
            }
            // Validate connection (output can only connect to input)
            if (isInput && connectionSourceNode != targetNode) {
                // Create new connection
                connections.add(new NodeConnection(connectionSourceNode, targetNode, connectionSourceSocket, targetSocket));
                stopDraggingConnection();
                return true;
            }
        }
        return false;
    }
    
    public NodeConnection getConnectionAt(int mouseX, int mouseY) {
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        for (NodeConnection connection : connections) {
            // Simple check - could be improved with better line collision detection
            Node outputNode = connection.getOutputNode();
            Node inputNode = connection.getInputNode();

            int outputX = outputNode.getSocketX(false);
            int outputY = outputNode.getSocketY(connection.getOutputSocket(), false);
            int inputX = inputNode.getSocketX(true);
            int inputY = inputNode.getSocketY(connection.getInputSocket(), true);

            // Check if mouse is near the connection line (simplified)
            if (Math.abs(worldY - (outputY + inputY) / 2) < 10) {
                int minX = Math.min(outputX, inputX);
                int maxX = Math.max(outputX, inputX);
                if (worldX >= minX && worldX <= maxX) {
                    return connection;
                }
            }
        }
        return null;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        var matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, getZoomScale(), getZoomScale());
        if (onlyDragged) {
            // Keep active drags above border/line layers while they are in motion.
            MatrixStackBridge.translateZ(matrices, 250.0f);
        }

        if (!onlyDragged) {
            updateCascadeDeletionPreview();
        }
        boolean renderConnectionsOnTop = shouldRenderConnectionsOnTop();
        if (!renderConnectionsOnTop) {
            renderConnections(context, onlyDragged);
        }

        Set<Node> processedRoots = new HashSet<>();
        Set<Node> renderedNodes = new HashSet<>();

        for (Node node : nodes) {
            Node root = getRootNode(node);
            if (root == null || processedRoots.contains(root)) {
                continue;
            }
            processedRoots.add(root);
            if (!intersectsViewport(calculateHierarchyBounds(root))) {
                markHierarchyRendered(root, renderedNodes);
                continue;
            }
            renderHierarchy(root, context, textRenderer, mouseX, mouseY, delta, onlyDragged, false, renderedNodes);
        }

        if (!onlyDragged) {
            renderParameterDropdownList(context, textRenderer, mouseX, mouseY);
            renderRandomRoundingDropdownList(context, textRenderer, mouseX, mouseY);
            renderModeDropdownList(context, textRenderer, mouseX, mouseY);
            renderAmountSignDropdownList(context, textRenderer, mouseX, mouseY);
        }

        if (renderConnectionsOnTop) {
            renderConnections(context, onlyDragged);
        }

        MatrixStackBridge.pop(matrices);

    }

    private boolean shouldRenderConnectionsOnTop() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        return settings != null && Boolean.TRUE.equals(settings.renderConnectionsOnTop);
    }

    public void renderScreenCoordinateCaptureOverlay(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (context == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int clampedX = MathHelper.clamp(mouseX, 0, Math.max(0, width - 1));
        int clampedY = MathHelper.clamp(mouseY, 0, Math.max(0, height - 1));

        int lineColor = 0xE6FFFFFF;
        int glowColor = 0x33FFFFFF;
        int panelFill = 0xCC111111;
        int panelBorder = 0xE6FFFFFF;
        int accentFill = 0xE6FFFFFF;
        int textColor = UITheme.TEXT_PRIMARY;

        context.fill(clampedX - 1, 0, clampedX + 1, height, glowColor);
        context.fill(0, clampedY - 1, width, clampedY + 1, glowColor);
        context.drawVerticalLine(clampedX, 0, height - 1, lineColor);
        context.drawHorizontalLine(0, width - 1, clampedY, lineColor);

        int crossRadius = 6;
        context.fill(clampedX - crossRadius, clampedY - 1, clampedX + crossRadius + 1, clampedY + 1, accentFill);
        context.fill(clampedX - 1, clampedY - crossRadius, clampedX + 1, clampedY + crossRadius + 1, accentFill);
        context.fill(clampedX - 2, clampedY - 2, clampedX + 3, clampedY + 3, 0xFF101010);
        context.fill(clampedX - 1, clampedY - 1, clampedX + 2, clampedY + 2, accentFill);

        if (textRenderer != null) {
            String label = "Pick Mode  " + clampedX + ", " + clampedY;
            int textWidth = textRenderer.getWidth(label);
            int boxWidth = textWidth + 12;
            int boxHeight = 18;
            int boxX = MathHelper.clamp(clampedX + 12, 4, Math.max(4, width - boxWidth - 4));
            int boxY = clampedY > height - 28 ? clampedY - 24 : clampedY + 12;
            context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, panelFill);
            DrawContextBridge.drawBorderInLayer(context, boxX, boxY, boxWidth, boxHeight, panelBorder);
            drawNodeText(context, textRenderer, Text.literal(label), boxX + 6, boxY + 5, textColor);
        }
    }

    public void renderSelectionBox(DrawContext context) {
        if (!selectionBoxActive || !hasSelectionBoxDrag()) {
            return;
        }
        int left = Math.min(selectionBoxStartX, selectionBoxCurrentX);
        int right = Math.max(selectionBoxStartX, selectionBoxCurrentX);
        int top = Math.min(selectionBoxStartY, selectionBoxCurrentY);
        int bottom = Math.max(selectionBoxStartY, selectionBoxCurrentY);
        if (left == right || top == bottom) {
            return;
        }
        int fillColor = UITheme.NODE_SELECTION_FILL;
        int borderColor = UITheme.NODE_SELECTION_BORDER;
        context.fill(left, top, right, bottom, fillColor);
        DrawContextBridge.drawBorderInLayer(context, left, top, right - left, bottom - top, borderColor);
    }

    private void renderHierarchy(Node node, DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta, boolean onlyDragged, boolean ancestorActive, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }

        boolean ownActive = isHierarchyDragging(node);
        boolean hierarchyActive = ancestorActive || ownActive;
        if ((onlyDragged && !hierarchyActive) || (!onlyDragged && hierarchyActive)) {
            markHierarchyRendered(node, renderedNodes);
            return;
        }

        renderNode(context, textRenderer, node, mouseX, mouseY, delta);
        renderedNodes.add(node);

        Node actionChild = node.getAttachedActionNode();
        renderHierarchy(actionChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Node sensorChild = node.getAttachedSensor();
        renderHierarchy(sensorChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            Collections.sort(keys);
            for (Integer key : keys) {
                renderHierarchy(parameterMap.get(key), context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);
            }
        }
    }

    private void markHierarchyRendered(Node node, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }
        renderedNodes.add(node);
        markHierarchyRendered(node.getAttachedActionNode(), renderedNodes);
        markHierarchyRendered(node.getAttachedSensor(), renderedNodes);
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                markHierarchyRendered(parameter, renderedNodes);
            }
        }
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

    private boolean isHierarchyDragging(Node node) {
        return isHierarchyDragging(node, new HashSet<>());
    }

    private boolean isHierarchyDragging(Node node, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return false;
        }
        visited.add(node);
        if (node.isDragging()) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedActionNode(), visited)) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedSensor(), visited)) {
            return true;
        }
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                if (isHierarchyDragging(parameter, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private NodeStyle getNodeStyle(Node node) {
        if (node == null) {
            return NodeStyle.STANDARD;
        }
        return node.usesMinimalNodePresentation() ? NodeStyle.MINIMAL : NodeStyle.STANDARD;
    }

    private boolean rendersInlineParameters(Node node) {
        if (node == null) {
            return false;
        }
        if (node.shouldRenderInlineParameters()) {
            return true;
        }
        return node.isParameterNode()
            && node.getType() != NodeType.CREATE_LIST
            && node.getType() != NodeType.LIST_LENGTH
            && node.getType() != NodeType.OPERATOR_MOD
            && node.getType() != NodeType.PARAM_DURATION
            && node.getType() != NodeType.SENSOR_POSITION_OF
            && node.getType() != NodeType.SENSOR_DISTANCE_BETWEEN
            && node.getType() != NodeType.SENSOR_SLOT_ITEM_COUNT;
    }

    private void renderNode(DrawContext context, TextRenderer textRenderer, Node node, int mouseX, int mouseY, float delta) {
        int x = node.getX() - cameraX;
        int y = node.getY() - cameraY;
        int width = node.getWidth();
        int height = node.getHeight();

        // Check if node is being dragged over sidebar (grey-out effect)
        // Use screen coordinates (with camera offset) for this check
        boolean isOverSidebar = isNodeOverSidebarForRender(node, x, width);

        if (node.isStickyNote()) {
            renderStickyNote(context, textRenderer, node, x, y, width, height, isOverSidebar);
            return;
        }

        NodeStyle nodeStyle = getNodeStyle(node);
        boolean simpleStyle = nodeStyle == NodeStyle.MINIMAL;
        boolean isStopControl = node.isStopControlNode();

        // Node background
        int bgColor = node.isSelected() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECONDARY;
        if (isOverSidebar) {
            bgColor = UITheme.NODE_DIMMED_BG; // Grey when over sidebar for deletion
        }
        context.fill(x, y, x + width, y + height, bgColor);
        if (simpleStyle) {
            int tabColor;
            if (isStopControl) {
                tabColor = isOverSidebar ? toGrayscale(UITheme.NODE_STOP_BG, 0.7f) : UITheme.NODE_STOP_BG;
            } else {
                int baseColor = node.getType().getColor();
                tabColor = isOverSidebar ? toGrayscale(baseColor, 0.7f) : baseColor;
            }
            int tabRight = Math.min(x + MINIMAL_NODE_TAB_WIDTH, x + width - 1);
            if (tabRight > x + 1) {
                context.fill(x + 1, y + 1, tabRight, y + height - 1, tabColor);
            }
        }
        
        // Node border - use light blue for selection, grey for dragging, darker node type color for START/events, node type color otherwise
        int borderColor;
        if (node.isDragging()) {
            borderColor = UITheme.BORDER_DRAGGING; // Medium grey outline when dragging
        } else if (node.isSelected()) {
            borderColor = getSelectedNodeAccentColor();
        } else if (node.getType() == NodeType.START) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_START_BORDER, 0.75f) : UITheme.NODE_START_BORDER; // Darker green for START
        } else if (node.getType() == NodeType.EVENT_FUNCTION) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_BORDER, 0.75f) : UITheme.NODE_EVENT_BORDER; // Darker pink for event functions
        } else if (node.getType() == NodeType.EVENT_CALL) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_CALL_BG, 0.75f) : UITheme.NODE_EVENT_CALL_BG;
        } else if (node.getType() == NodeType.VARIABLE) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_BORDER, 0.75f) : UITheme.NODE_VARIABLE_BORDER; // Darker orange for variables
        } else if (node.getType() == NodeType.OPERATOR_EQUALS
            || node.getType() == NodeType.OPERATOR_NOT
            || node.getType() == NodeType.OPERATOR_BOOLEAN_OR
            || node.getType() == NodeType.OPERATOR_BOOLEAN_AND
            || node.getType() == NodeType.OPERATOR_BOOLEAN_XOR) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_BORDER, 0.75f) : UITheme.NODE_OPERATOR_BORDER; // Darker green for operators
        } else if (isStopControl) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_STOP_BORDER, 0.75f) : UITheme.NODE_STOP_BORDER;
        } else if (simpleStyle) {
            int baseColor = node.getType().getColor();
            borderColor = isOverSidebar ? toGrayscale(baseColor, 0.6f) : adjustColorBrightness(baseColor, 1.1f);
        } else {
            borderColor = node.getType().getColor(); // Regular node type color
        }
        if (isOverSidebar && node.getType() != NodeType.START && !node.isDragging()) {
            borderColor = UITheme.BORDER_SUBTLE; // Darker grey border when over sidebar (for regular nodes)
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);

        // Node header (only for non-START/event function nodes)
        if (simpleStyle) {
            boolean isOperator = node.getType() == NodeType.OPERATOR_EQUALS
                || node.getType() == NodeType.OPERATOR_NOT
                || node.getType() == NodeType.OPERATOR_BOOLEAN_OR
                || node.getType() == NodeType.OPERATOR_BOOLEAN_AND
                || node.getType() == NodeType.OPERATOR_BOOLEAN_XOR;
            String label = node.getType().getDisplayName().toUpperCase(Locale.ROOT);
            boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
            int titleColor = (isStopControl || isActivateNode)
                ? UITheme.TEXT_PRIMARY
                : (isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY);
            int textX;
            int textY;
            if (node.hasStopTargetInputField()) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                textX = contentLeft + 4;
                textY = y + 4;
            } else if (!isOperator) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                int contentWidth = Math.max(0, width - MINIMAL_NODE_TAB_WIDTH);
                String displayLabel = trimTextToWidth(label, textRenderer, Math.max(0, contentWidth - 8));
                int textWidth = textRenderer.getWidth(displayLabel);
                textX = contentLeft + Math.max(4, (contentWidth - textWidth) / 2);
                textY = y + (height - textRenderer.fontHeight) / 2;
                label = displayLabel;
            } else {
                textX = 0;
                textY = 0;
            }
            if (!isOperator && !isComparisonOperator(node)) {
                drawNodeText(context, textRenderer, label, textX, textY, titleColor);
            }
        } else if (node.getType() != NodeType.START
            && node.getType() != NodeType.EVENT_FUNCTION
            && node.getType() != NodeType.VARIABLE
            && node.getType() != NodeType.TEMPLATE
            && node.getType() != NodeType.CUSTOM_NODE
            && node.getType() != NodeType.OPERATOR_EQUALS
            && node.getType() != NodeType.OPERATOR_NOT
            && node.getType() != NodeType.OPERATOR_BOOLEAN_OR
            && node.getType() != NodeType.OPERATOR_BOOLEAN_AND
            && node.getType() != NodeType.OPERATOR_BOOLEAN_XOR) {
            int headerColor = node.getType().getColor() & UITheme.NODE_HEADER_ALPHA_MASK;
            if (isOverSidebar) {
                headerColor = UITheme.NODE_HEADER_DIMMED; // Grey header when over sidebar
            }
            context.fill(x + 1, y + 1, x + width - 1, y + 14, headerColor);
            
            // Node title
            int titleColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY; // Grey text when over sidebar
            drawNodeText(
                context,
                textRenderer,
                node.getDisplayName(),
                x + 4,
                y + 4,
                titleColor
            );
        }

        if (node.shouldRenderSockets()) {
            // Render input sockets
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                boolean isHovered = (hoveredSocketNode == node && hoveredSocketIndex == i && hoveredSocketIsInput);
                boolean isActive = isSocketActive(node, i, true);
                int socketColor = isHovered ? getSelectedNodeAccentColor() : node.getType().getColor();
                if (!isActive && !isHovered) {
                    socketColor = darkenColor(socketColor, 0.7f); // Darker when unused
                }
                if (isOverSidebar) {
                    socketColor = UITheme.BORDER_HIGHLIGHT; // Grey sockets when over sidebar
                }
                renderSocket(context, node.getSocketX(true) - cameraX, node.getSocketY(i, true) - cameraY, true, socketColor);
            }

            // Render output sockets
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                boolean isHovered = (hoveredSocketNode == node && hoveredSocketIndex == i && !hoveredSocketIsInput);
                boolean isActive = isSocketActive(node, i, false);
                int socketColor = isHovered ? getSelectedNodeAccentColor() : node.getOutputSocketColor(i);
                if (!isActive && !isHovered) {
                    socketColor = darkenColor(socketColor, 0.7f); // Darker when unused
                }
                if (isOverSidebar) {
                    socketColor = UITheme.BORDER_HIGHLIGHT; // Grey sockets when over sidebar
                }
                renderSocket(context, node.getSocketX(false) - cameraX, node.getSocketY(i, false) - cameraY, false, socketColor);
            }
        }

        // Render node content based on type
        if (node.getType() == NodeType.START) {
            // START node - green square with play button
            int greenColor = isOverSidebar ? toGrayscale(UITheme.NODE_START_BG, 0.7f) : UITheme.NODE_START_BG; // Darker green when over sidebar
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, greenColor);
            
            // Draw play button (triangle pointing right) - with hover effect
            int playColor;
            if (hoveringStartButton) {
                playColor = isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY; // Darker when hovered
            } else {
                playColor = isOverSidebar ? UITheme.TEXT_PRIMARY : UITheme.TEXT_PRIMARY; // Normal white
            }
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            
            // Play triangle (pointing right) - bigger and cleaner
            int triangleSize = 10; // Bigger triangle
            int offset = 1; // Slight right offset for centering
            
            // Draw triangle using a cleaner algorithm
            for (int i = 0; i < triangleSize; i++) {
                int lineWidth = i + 1; // Each line gets progressively wider
                int startX = centerX - triangleSize/2 + offset;
                int lineY = centerY - triangleSize/2 + i;
                
                if (lineY >= y + 2 && lineY <= y + height - 3) {
                    context.drawHorizontalLine(startX, startX + lineWidth, lineY, playColor);
                }
            }

            renderStartNodeNumber(context, textRenderer, node, x, y, isOverSidebar);
            
        } else if (node.getType() == NodeType.EVENT_FUNCTION) {
            int baseColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_BG, 0.7f) : UITheme.NODE_EVENT_BG;
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

            int titleColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TITLE, 0.9f) : UITheme.NODE_EVENT_TITLE;
            drawNodeText(
                context,
                textRenderer,
                Text.literal("Function"),
                x + 6,
                y + 4,
                titleColor
            );

            int boxLeft = node.getEventNameFieldLeft() - cameraX;
            int boxTop = node.getEventNameFieldTop() - cameraY;
            int boxWidth = node.getEventNameFieldWidth();
            int boxHeight = node.getEventNameFieldHeight();
            int boxRight = boxLeft + boxWidth;
            int boxBottom = boxTop + boxHeight;
            int inputBackground = isOverSidebar ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_INPUT;
            context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
            int inputBorder = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_INPUT_BORDER, 0.8f) : UITheme.BORDER_SUBTLE;
            DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

            boolean editingEventName = isEditingEventNameField() && eventNameEditingNode == node;
            if (editingEventName) {
                updateEventNameCaretBlink();
            }

            NodeParameter nameParam = node.getParameter("Name");
            String value = editingEventName
                ? eventNameEditBuffer
                : (nameParam != null ? nameParam.getStringValue() : "");
            if (value == null) {
                value = "";
            }
            String display;
            boolean showPlaceholder = !editingEventName && value.isEmpty();
            if (showPlaceholder) {
                display = "enter name";
            } else {
                display = value;
            }
            int eventNameVariableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
            Set<String> eventNameVariableNames = collectRuntimeVariableNames(node);
            InlineVariableRender eventNameRenderData = null;
            boolean highlightPlainEventName = false;
            if (!eventNameVariableNames.isEmpty() && value.indexOf('~') >= 0) {
                InlineVariableRender candidate = buildInlineVariableRender(value, eventNameVariableNames, isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT, eventNameVariableHighlightColor);
                if (editingEventName) {
                    eventNameRenderData = candidate;
                    display = eventNameRenderData.displayText;
                } else if (textRenderer.getWidth(candidate.displayText) <= boxRight - boxLeft - 8) {
                    eventNameRenderData = candidate;
                    display = eventNameRenderData.displayText;
                } else if (isSingleKnownInlineVariableReference(value, eventNameVariableNames)) {
                    display = trimTextToWidth(candidate.displayText, textRenderer, boxRight - boxLeft - 8);
                    highlightPlainEventName = true;
                }
            }
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT;
            if (showPlaceholder) {
                textColor = UITheme.TEXT_TERTIARY;
            }
            if (highlightPlainEventName) {
                textColor = eventNameVariableHighlightColor;
            }
            int textX = boxLeft + 4;
            if (editingEventName && hasEventNameSelection()) {
                int start = eventNameSelectionStart;
                int end = eventNameSelectionEnd;
                if (eventNameRenderData != null) {
                    start = eventNameRenderData.toDisplayIndex(start);
                    end = eventNameRenderData.toDisplayIndex(end);
                }
                start = MathHelper.clamp(start, 0, display.length());
                end = MathHelper.clamp(end, 0, display.length());
                if (start != end) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (!editingEventName) {
                if (eventNameRenderData != null && shouldRenderNodeText()) {
                    eventNameRenderData.draw(context, textRenderer, textX, textY);
                } else {
                    renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
                }
            } else {
                if (eventNameRenderData != null && shouldRenderNodeText()) {
                    eventNameRenderData.draw(context, textRenderer, textX, textY);
                } else {
                    drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
                }
            }

            if (editingEventName && eventNameCaretVisible) {
                int caretIndex = eventNameCaretPosition;
                if (eventNameRenderData != null) {
                    caretIndex = eventNameRenderData.toDisplayIndex(caretIndex);
                }
                caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, boxRight - 2);
                int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, boxBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
            }
            renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else if (node.getType() == NodeType.VARIABLE) {
            int baseColor = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_BG, 0.7f) : UITheme.NODE_VARIABLE_BG;
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

            int titleColor = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_TITLE, 0.9f) : UITheme.NODE_VARIABLE_TITLE;
            drawNodeText(
                context,
                textRenderer,
                Text.literal("Variable"),
                x + 6,
                y + 4,
                titleColor
            );

            int boxLeft = x + 6;
            int boxRight = x + width - 6;
            int boxHeight = 16;
            int boxTop = y + height / 2 - boxHeight / 2 + 4;
            int boxBottom = boxTop + boxHeight;
            boolean editingThis = isEditingParameterField()
                && parameterEditingNode == node
                && parameterEditingIndex == 0;
            if (editingThis) {
                updateParameterCaretBlink();
            }
            int inputBackground = isOverSidebar ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_INPUT;
            int inputBorder = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_INPUT_BORDER, 0.8f) : UITheme.BORDER_SUBTLE;
            if (editingThis) {
                inputBorder = getSelectedNodeAccentColor();
            }
            context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
            DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

            NodeParameter nameParam = node.getParameter("Variable");
            String value = editingThis
                ? parameterEditBuffer
                : (nameParam != null ? nameParam.getStringValue() : "");
            if (value == null) {
                value = "";
            }
            String display;
            if (!editingThis && value.isEmpty()) {
                display = "enter variable";
            } else {
                display = value;
            }
            display = editingThis
                ? display
                : trimTextToWidth(display, textRenderer, boxRight - boxLeft - 8);
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = editingThis
                ? UITheme.TEXT_EDITING
                : (isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_TEXT, 0.85f) : UITheme.NODE_VARIABLE_TEXT);
            if (editingThis && hasParameterSelection()) {
                int start = MathHelper.clamp(parameterSelectionStart, 0, display.length());
                int end = MathHelper.clamp(parameterSelectionEnd, 0, display.length());
                if (start != end) {
                    int selectionStartX = boxLeft + 4 + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = boxLeft + 4 + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            drawNodeText(
                context,
                textRenderer,
                Text.literal(display),
                boxLeft + 4,
                textY,
                textColor
            );
            if (editingThis && parameterCaretVisible) {
                int caretIndex = MathHelper.clamp(parameterCaretPosition, 0, display.length());
                int caretX = boxLeft + 4 + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, boxRight - 2);
                int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, boxBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
            }
        } else if (!simpleStyle && isComparisonOperator(node)) {
            int baseColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_BG, 0.7f) : UITheme.NODE_OPERATOR_BG;
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

            int titleColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_TITLE, 0.9f) : UITheme.NODE_OPERATOR_TITLE;
            if (titleColor != 0) {
                // Intentionally skip title text for operator nodes to keep the symbol clean.
            }

            renderParameterSlot(context, textRenderer, node, isOverSidebar, 0);
            renderParameterSlot(context, textRenderer, node, isOverSidebar, 1);

            int leftSlotX = node.getParameterSlotLeft(0) - cameraX;
            int rightSlotX = node.getParameterSlotLeft(1) - cameraX;
            int leftSlotWidth = node.getParameterSlotWidth(0);
            int leftSlotHeight = node.getParameterSlotHeight(0);
            int rightSlotHeight = node.getParameterSlotHeight(1);
            int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;
            String operatorText = getOperatorSymbol(node, false);
            int operatorWidth = textRenderer.getWidth(operatorText);
            int operatorX = gapCenterX - operatorWidth / 2;
            int leftSlotTop = node.getParameterSlotTop(0) - cameraY;
            int rightSlotTop = node.getParameterSlotTop(1) - cameraY;
            int leftCenterY = leftSlotTop + leftSlotHeight / 2;
            int rightCenterY = rightSlotTop + rightSlotHeight / 2;
            int operatorCenterY = (leftCenterY + rightCenterY) / 2;
            int operatorY = operatorCenterY - textRenderer.fontHeight / 2;
            int operatorColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_SYMBOL, 0.85f) : UITheme.NODE_OPERATOR_SYMBOL;
            if (node.getType() == NodeType.OPERATOR_GREATER || node.getType() == NodeType.OPERATOR_LESS) {
                int buttonPaddingX = 3;
                int buttonPaddingY = 4;
                int maxSymbolWidth = textRenderer.getWidth(">=");
                int buttonWidth = maxSymbolWidth + buttonPaddingX * 2;
                int buttonHeight = textRenderer.fontHeight + buttonPaddingY * 2;
                int buttonLeft = gapCenterX - buttonWidth / 2;
                int buttonTop = operatorY - buttonPaddingY;
                int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
                int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
                DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);
                operatorX = buttonLeft + (buttonWidth - operatorWidth) / 2;
            }
            drawNodeText(
                context,
                textRenderer,
                Text.literal(operatorText),
                operatorX,
                operatorY,
                operatorColor
            );
        } else if (node.getType() == NodeType.EVENT_CALL) {
            int baseColor = isOverSidebar ? toGrayscale(node.getType().getColor(), 0.7f) : node.getType().getColor();
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

            int titleColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TITLE, 0.9f) : UITheme.NODE_EVENT_TITLE;
            drawNodeText(
                context,
                textRenderer,
                Text.literal("Call Function"),
                x + 6,
                y + 4,
                titleColor
            );

            int boxLeft = node.getEventNameFieldLeft() - cameraX;
            int boxTop = node.getEventNameFieldTop() - cameraY;
            int boxWidth = node.getEventNameFieldWidth();
            int boxHeight = node.getEventNameFieldHeight();
            int boxRight = boxLeft + boxWidth;
            int boxBottom = boxTop + boxHeight;
            int inputBackground = isOverSidebar ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_INPUT;
            context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
            int inputBorder = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_CALL_INPUT_BORDER, 0.8f) : UITheme.BORDER_SUBTLE;
            DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

            boolean editingEventName = isEditingEventNameField() && eventNameEditingNode == node;
            if (editingEventName) {
                updateEventNameCaretBlink();
            }

            NodeParameter nameParam = node.getParameter("Name");
            String value = editingEventName
                ? eventNameEditBuffer
                : (nameParam != null ? nameParam.getStringValue() : "");
            if (value == null) {
                value = "";
            }
            String display;
            boolean showPlaceholder = !editingEventName && value.isEmpty();
            if (showPlaceholder) {
                display = "enter name";
            } else {
                display = value;
            }
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT;
            if (showPlaceholder) {
                textColor = UITheme.TEXT_TERTIARY;
            }
            int textX = boxLeft + 4;
            if (editingEventName && hasEventNameSelection()) {
                int start = MathHelper.clamp(eventNameSelectionStart, 0, display.length());
                int end = MathHelper.clamp(eventNameSelectionEnd, 0, display.length());
                if (start != end) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (!editingEventName) {
                renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
            } else {
                drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
            }

            if (editingEventName && eventNameCaretVisible) {
                int caretIndex = MathHelper.clamp(eventNameCaretPosition, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, boxRight - 2);
                int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, boxBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
            }
            renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else if (node.getType() == NodeType.TEMPLATE) {
            renderTemplateNodeContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else if (node.getType() == NodeType.CUSTOM_NODE) {
            renderCustomNodeContent(context, textRenderer, node, isOverSidebar);
        } else {
            if (rendersInlineParameters(node)) {
                if (shouldShowParameters(node)) {
                    int paramBgColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR; // Grey when over sidebar
                    context.fill(x + 3, y + 16, x + width - 3, y + height - 3, paramBgColor);

                    // Render parameters
                    int paramY = y + 18;
                    List<NodeParameter> parameters = node.getParameters();
                    if (isEditingParameterField() && parameterEditingNode == node) {
                        updateParameterCaretBlink();
                    }

                    if (node.supportsModeSelection()) {
                        int fieldLeft = getParameterFieldLeft(node) - cameraX;
                        int fieldTop = paramY;
                        int fieldWidth = getParameterFieldWidth(node);
                        int fieldHeight = getParameterFieldHeight();
                        int fieldRight = fieldLeft + fieldWidth;
                        int worldMouseX = screenToWorldX(mouseX);
                        int worldMouseY = screenToWorldY(mouseY);

                        int fieldBackground = isOverSidebar
                            ? UITheme.BACKGROUND_SECONDARY
                            : UITheme.BACKGROUND_SIDEBAR;
                        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
                        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                        int activeFieldBorder = getSelectedNodeAccentColor();
                        boolean hovered = !isOverSidebar
                            && worldMouseX >= getParameterFieldLeft(node)
                            && worldMouseX <= getParameterFieldLeft(node) + fieldWidth
                            && worldMouseY >= fieldTop + cameraY
                            && worldMouseY <= fieldTop + cameraY + fieldHeight;
                        float progress = getTextFieldHighlightProgress(node.getId() + "#modeInline", hovered, false);
                        int backgroundColor = isOverSidebar
                            ? fieldBackground
                            : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
                        int modeFieldBorderColor = isOverSidebar
                            ? fieldBorder
                            : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);

                        context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, backgroundColor);
                        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, modeFieldBorderColor);

                        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
                        int valueColor = isOverSidebar ? UITheme.TEXT_TERTIARY
                            : AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, progress);
                        String labelText = "Mode:";
                        int labelX = fieldLeft + 4;
                        int labelY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;
                        drawNodeText(context, textRenderer, Text.literal(labelText), labelX, labelY, labelColor);

                        String modeValue = node.getMode() != null ? node.getMode().getDisplayName() : "Select Mode";
                        int valueStartX = labelX + textRenderer.getWidth(labelText) + 6;
                        int maxValueWidth = Math.max(0, fieldRight - valueStartX - 4);
                        String displayValue = trimTextToWidth(modeValue, textRenderer, maxValueWidth);
                        int valueY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;
                        drawNodeText(context, textRenderer, Text.literal(displayValue), valueStartX, valueY, valueColor);

                        paramY += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
                    }

                    if (isCombinedDirectionNode(node)) {
                        renderDirectionModeTabs(context, textRenderer, node, isOverSidebar, paramY, mouseX, mouseY);
                        paramY += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
                    }

                    if (isCombinedBooleanNode(node)) {
                        renderBooleanModeTabs(context, textRenderer, node, isOverSidebar, paramY, mouseX, mouseY);
                        paramY += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
                    }

                    for (int i = 0; i < parameters.size(); i++) {
                        NodeParameter param = parameters.get(i);
                        String displayLabel = node.getParameterLabel(param);
                        if (displayLabel == null || displayLabel.isEmpty()) {
                            continue;
                        }

                        boolean editingThis = isEditingParameterField()
                            && parameterEditingNode == node
                            && parameterEditingIndex == i;

                        int fieldLeft = getParameterFieldLeft(node) - cameraX;
                        int fieldTop = paramY;
                        int fieldWidth = getParameterFieldWidth(node);
                        int fieldHeight = getParameterFieldHeight();
                        int fieldRight = fieldLeft + fieldWidth;
                        int worldMouseX = screenToWorldX(mouseX);
                        int worldMouseY = screenToWorldY(mouseY);

                        int fieldBackground = isOverSidebar
                            ? UITheme.BACKGROUND_SECONDARY
                            : UITheme.BACKGROUND_SIDEBAR;
                        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
                        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                        int activeFieldBorder = getSelectedNodeAccentColor();

                        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
                        int valueColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

                        int maxLabelWidth = Math.max(0, fieldWidth - 40);
                        String labelText = getParameterLabelText(node, param, textRenderer, maxLabelWidth);
                        int labelX = fieldLeft + 4;
                        int labelY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;
                        if (!labelText.isEmpty()) {
                            drawNodeText(context, textRenderer, Text.literal(labelText), labelX, labelY, labelColor);
                        }

                        int valueStartX = getParameterValueStartX(node, param, textRenderer) - cameraX;
                        int maxValueWidth = Math.max(0, fieldRight - valueStartX - 4);
                        String value = editingThis ? parameterEditBuffer : param.getStringValue();
                        if (value == null) {
                            value = "";
                        }
                        boolean isPlayerParam = isPlayerParameter(node, param);
                        boolean isMessageParam = isMessageParameter(node, param);
                        boolean isSeedParam = isSeedParameter(node, param);
                        boolean isGuiParam = isGuiParameter(node, param);
                        boolean isMouseButtonParam = isMouseButtonParameter(node, param);
                        boolean isAmountParam = isAmountParameter(node, param);
                        boolean isAttributeDetectionDropdownParam = isAttributeDetectionDropdownParameter(node, i);
                        boolean showPlayerPlaceholder = false;
                        boolean showMessagePlaceholder = false;
                        boolean showSeedPlaceholder = false;
                        boolean showBlockItemPlaceholder = false;
                        boolean showFabricEventPlaceholder = false;
                        boolean showDirectionPlaceholder = false;
                        boolean showBlockFacePlaceholder = false;
                        boolean showGuiPlaceholder = false;
                        boolean showMouseButtonPlaceholder = false;
                        boolean showAmountPlaceholder = false;
                        boolean showTradePlaceholder = false;
                        if (isPlayerParam) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && "Self".equalsIgnoreCase(value));
                            showPlayerPlaceholder = showPlaceholder;
                        }
                        if (isMessageParam) {
                            showMessagePlaceholder = false;
                        }
                        if (isSeedParam) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value));
                            showSeedPlaceholder = showPlaceholder;
                        }
                        if (isGuiParam) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty() || "Any".equalsIgnoreCase(value)
                                : value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value));
                            showGuiPlaceholder = showPlaceholder;
                        }
                        if (isMouseButtonParam) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && isDefaultMouseButtonValue(value));
                            showMouseButtonPlaceholder = showPlaceholder;
                        }
                        if (isAmountParam) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && "0".equalsIgnoreCase(value));
                            showAmountPlaceholder = showPlaceholder;
                        }
                        if (isTradeInlinePlaceholder(node, param, editingThis)) {
                            showTradePlaceholder = true;
                        }
                        if (isBlockItemParameter(node, i)) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty() || isAnyBlockItemValue(value)
                                : value.isEmpty() || (!param.isUserEdited() && isAnyBlockItemValue(value));
                            showBlockItemPlaceholder = showPlaceholder;
                        }
                        if (isFabricEventSensorParameter(node, i)) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty() || "Any".equalsIgnoreCase(value)
                                : value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value));
                            showFabricEventPlaceholder = showPlaceholder;
                        }
                        if (isDirectionParameter(node, i)) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && "north".equalsIgnoreCase(value));
                            showDirectionPlaceholder = showPlaceholder;
                        }
                        if (isBlockFaceParameter(node, i)) {
                            boolean showPlaceholder = editingThis
                                ? value.isEmpty()
                                : value.isEmpty() || (!param.isUserEdited() && "north".equalsIgnoreCase(value));
                            showBlockFacePlaceholder = showPlaceholder;
                        }
                        if (!editingThis
                            && node.getType() == NodeType.PARAM_VILLAGER_TRADE
                            && ("Item".equalsIgnoreCase(param.getName()) || "Trade".equalsIgnoreCase(param.getName()))) {
                            value = formatVillagerTradeValue(value);
                        }
                        if (!editingThis && (value.isEmpty() || isAnyBlockItemValue(value)) && isBlockItemParameter(node, i)) {
                            value = (isBlockStateParameter(node, i) || isEntityStateParameter(node, i))
                                ? "Any State"
                                : "Any";
                        }
                        if (!editingThis && isFabricEventSensorParameter(node, i)
                            && (value.isEmpty() || "Any".equalsIgnoreCase(value))) {
                            value = "Any";
                        }
                        if (!editingThis && isMouseButtonParam) {
                            value = formatMouseButtonValue(value);
                        }
                        if (!editingThis && isAttributeDetectionDropdownParam) {
                            value = formatAttributeDetectionInlineValue(node, param, value);
                        }
                        if (!editingThis && isBooleanLiteralParameter(node, i) && value.isEmpty()) {
                            value = "True";
                        }
                        if (!editingThis && isBooleanLiteralParameter(node, i) && !value.isEmpty()) {
                            value = Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
                        }
                        if (!editingThis && isBlockFaceParameter(node, i) && !value.isEmpty()) {
                            value = Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
                        }
                        if (showPlayerPlaceholder || showMessagePlaceholder || showSeedPlaceholder
                            || showBlockItemPlaceholder || showFabricEventPlaceholder
                            || showGuiPlaceholder || showMouseButtonPlaceholder || showAmountPlaceholder
                            || showDirectionPlaceholder || showBlockFacePlaceholder || showTradePlaceholder) {
                            if (isBlockStateParameter(node, i) || isEntityStateParameter(node, i)) {
                                value = "Any State";
                            } else if (showPlayerPlaceholder) {
                                value = "Self";
                            } else if (showMouseButtonPlaceholder) {
                                value = "Left";
                            } else if (showTradePlaceholder) {
                                value = "1";
                            } else if (showAmountPlaceholder) {
                                value = "0";
                            } else if (showBlockFacePlaceholder) {
                                value = "North";
                            } else if (showDirectionPlaceholder) {
                                value = "North";
                            } else {
                                value = "Any";
                            }
                            valueColor = UITheme.TEXT_TERTIARY;
                        }
                        boolean inlineDropdown = isBooleanLiteralParameter(node, i) || isAttributeDetectionDropdownParam;
                        boolean inlineDropdownOpen = inlineDropdown
                            && parameterDropdownOpen
                            && parameterDropdownNode == node
                            && parameterDropdownIndex == i;
                        boolean hovered = !isOverSidebar
                            && worldMouseX >= fieldLeft + cameraX
                            && worldMouseX <= fieldLeft + cameraX + fieldWidth
                            && worldMouseY >= fieldTop + cameraY
                            && worldMouseY <= fieldTop + cameraY + fieldHeight;
                        float progress = getTextFieldHighlightProgress(
                            node.getId() + "#param:" + i,
                            hovered,
                            editingThis || inlineDropdownOpen
                        );
                        int backgroundColor = isOverSidebar
                            ? fieldBackground
                            : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
                        int parameterFieldBorderColor = isOverSidebar
                            ? fieldBorder
                            : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
                        if (inlineDropdownOpen && !isOverSidebar) {
                            parameterFieldBorderColor = getSelectedNodeAccentColor();
                        }

                        context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, backgroundColor);
                        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, parameterFieldBorderColor);

                        labelColor = isOverSidebar ? labelColor
                            : AnimationHelper.lerpColor(labelColor, UITheme.TEXT_HEADER, progress * 0.6f);
                        valueColor = isOverSidebar ? valueColor
                            : AnimationHelper.lerpColor(valueColor, UITheme.TEXT_HEADER, progress);

                        String arrow = inlineDropdown ? (inlineDropdownOpen ? "v" : "^") : "";
                        int arrowWidth = inlineDropdown ? textRenderer.getWidth(arrow) : 0;
                        if (inlineDropdown) {
                            maxValueWidth = Math.max(0, maxValueWidth - arrowWidth - 8);
                        }
                        String displayValue = editingThis
                            ? value
                            : trimTextToWidth(value, textRenderer, maxValueWidth);
                        int paramVariableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
                        Set<String> paramVariableNames = collectRuntimeVariableNames(node);
                        InlineVariableRender paramRenderData = null;
                        if (!paramVariableNames.isEmpty() && value != null && value.indexOf('~') >= 0) {
                            InlineVariableRender candidate = buildInlineVariableRender(value, paramVariableNames, valueColor, paramVariableHighlightColor);
                            if (editingThis) {
                                paramRenderData = candidate;
                                displayValue = paramRenderData.displayText;
                            } else if (textRenderer.getWidth(candidate.displayText) <= maxValueWidth) {
                                paramRenderData = candidate;
                                displayValue = paramRenderData.displayText;
                            } else if (isSingleKnownInlineVariableReference(value, paramVariableNames)) {
                                displayValue = trimTextToWidth(candidate.displayText, textRenderer, maxValueWidth);
                                valueColor = paramVariableHighlightColor;
                            }
                        }
                        int valueY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;

                        if (editingThis && hasParameterSelection()) {
                            int start = parameterSelectionStart;
                            int end = parameterSelectionEnd;
                            if (paramRenderData != null) {
                                start = paramRenderData.toDisplayIndex(start);
                                end = paramRenderData.toDisplayIndex(end);
                            }
                            start = MathHelper.clamp(start, 0, displayValue.length());
                            end = MathHelper.clamp(end, 0, displayValue.length());
                            if (start != end) {
                                int selectionStartX = valueStartX + textRenderer.getWidth(displayValue.substring(0, start));
                                int selectionEndX = valueStartX + textRenderer.getWidth(displayValue.substring(0, end));
                                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldTop + fieldHeight - 2, UITheme.TEXT_SELECTION_BG);
                            }
                        }

                        if (paramRenderData != null && shouldRenderNodeText()) {
                            paramRenderData.draw(context, textRenderer, valueStartX, valueY);
                        } else {
                            drawNodeText(context, textRenderer, Text.literal(displayValue), valueStartX, valueY, valueColor);
                        }

                        if (inlineDropdown) {
                            int arrowX = fieldRight - arrowWidth - 4;
                            drawNodeText(context, textRenderer, Text.literal(arrow), arrowX, valueY, valueColor);
                        }

                        if (editingThis && parameterCaretVisible) {
                            int caretIndex = parameterCaretPosition;
                            if (paramRenderData != null) {
                                caretIndex = paramRenderData.toDisplayIndex(caretIndex);
                            }
                            caretIndex = MathHelper.clamp(caretIndex, 0, displayValue.length());
                            int caretX = valueStartX + textRenderer.getWidth(displayValue.substring(0, caretIndex));
                            caretX = Math.min(caretX, fieldRight - 2);
                            int caretBaseline = Math.min(valueY + textRenderer.fontHeight - 1, fieldTop + fieldHeight - 2);
                            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, fieldRight - 2, UITheme.CARET_COLOR);
                        }

                        if (editingThis && (isBlockItemParameter(node, i)
                            || isMouseButtonParameter(node, param)
                            || isGuiParameter(node, param)
                            || isDirectionParameter(node, i)
                            || isAttributeDetectionDropdownParameter(node, i)
                            || isBlockFaceParameter(node, i)
                            || isFabricEventSensorParameter(node, i))) {
                            updateParameterDropdown(node, i, textRenderer, fieldLeft, fieldTop, fieldWidth, fieldHeight);
                        }

                        paramY += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
                    }
                    if (node.hasRandomRoundingField()) {
                        renderRandomRoundingField(context, textRenderer, node, isOverSidebar);
                    }
                    if (node.hasParameterSlot()) {
                        int slotCount = node.getParameterSlotCount();
                        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                            renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
                        }
                    }
                    if (node.hasAmountInputField()) {
                        renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    if (node.hasPopupEditButton()) {
                        renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                }
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
                        renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
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
                    renderMessageScopeToggle(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    renderMessageButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasBookTextInput()) {
                    renderBookTextInput(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    renderSchematicDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (isPresetSelectorNode(node)) {
                    renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
            }

            if (node.hasBooleanToggle()) {
                renderBooleanToggleButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            }
            if (node.hasSensorSlot()) {
                renderSensorSlot(context, textRenderer, node, isOverSidebar);
            }
            if (node.hasActionSlot()) {
                renderActionSlot(context, textRenderer, node, isOverSidebar);
            }
        }
    }

    private int getParameterFieldLeft(Node node) {
        return node.getX() + 5;
    }

    private boolean isCombinedDirectionNode(Node node) {
        return node != null && node.getType() == NodeType.PARAM_DIRECTION;
    }

    private boolean isCombinedBooleanNode(Node node) {
        return node != null && node.getType() == NodeType.PARAM_BOOLEAN;
    }

    private int getDirectionModeTabTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    private int getBooleanModeTabTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    private int getInlineParameterFieldsTop(Node node) {
        int top = node.getY() + 18;
        if (node != null && node.supportsModeSelection()) {
            top += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        if (isCombinedDirectionNode(node)) {
            top += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
        }
        if (isCombinedBooleanNode(node)) {
            top += DIRECTION_MODE_TAB_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return top;
    }

    private int getParameterFieldWidth(Node node) {
        return Math.max(20, node.getWidth() - 10);
    }

    private int getParameterFieldHeight() {
        return PARAMETER_INPUT_HEIGHT;
    }

    private void renderStickyNote(DrawContext context, TextRenderer textRenderer, Node node, int x, int y, int width, int height,
                                  boolean isOverSidebar) {
        int paperColor = isOverSidebar ? UITheme.NODE_DIMMED_BG : 0xFFF3E28A;
        int headerColor = isOverSidebar ? UITheme.NODE_HEADER_DIMMED : 0xFFE2C65A;
        int borderColor = node.isSelected() ? getSelectedNodeAccentColor() : (isOverSidebar ? UITheme.BORDER_SUBTLE : 0xFFC2A748);
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : 0xFF3F3420;

        context.fill(x, y, x + width, y + height, paperColor);
        context.fill(x + 1, y + 1, x + width - 1, y + node.getStickyNoteHeaderHeight(), headerColor);
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);

        int bodyLeft = node.getStickyNoteBodyLeft() - cameraX;
        int bodyTop = node.getStickyNoteBodyTop() - cameraY;
        int maxLines = Math.max(1, node.getStickyNoteBodyHeight() / Math.max(1, textRenderer.fontHeight + 1));
        List<String> lines = getStickyNoteDisplayLines(node, textRenderer, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = bodyTop + i * (textRenderer.fontHeight + 1);
            if (lineY + textRenderer.fontHeight > y + height - 4) {
                break;
            }
            drawNodeText(context, textRenderer, lines.get(i), bodyLeft, lineY, textColor);
        }

        if (stickyNoteEditingNode == node && stickyNoteCaretVisible) {
            updateStickyNoteCaretBlink();
            int caretLine = 0;
            int caretColumn = 0;
            int remaining = MathHelper.clamp(stickyNoteCaretPosition, 0, stickyNoteEditBuffer.length());
            String[] rawLines = stickyNoteEditBuffer.split("\\n", -1);
            for (int i = 0; i < rawLines.length; i++) {
                String rawLine = rawLines[i] == null ? "" : rawLines[i];
                if (remaining <= rawLine.length()) {
                    caretLine = i;
                    caretColumn = remaining;
                    break;
                }
                remaining -= rawLine.length() + 1;
                caretLine = i;
                caretColumn = rawLine.length();
            }
            int caretY = bodyTop + caretLine * (textRenderer.fontHeight + 1);
            int caretX = bodyLeft + textRenderer.getWidth(rawLines[Math.min(caretLine, rawLines.length - 1)].substring(0, Math.min(caretColumn, rawLines[Math.min(caretLine, rawLines.length - 1)].length())));
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretY + textRenderer.fontHeight - 1, x + width - 6, 0xFF000000);
        }

        if (node.isSelected()) {
            renderStickyNoteResizeHandle(context, node, StickyNoteResizeCorner.TOP_LEFT, borderColor);
            renderStickyNoteResizeHandle(context, node, StickyNoteResizeCorner.TOP_RIGHT, borderColor);
            renderStickyNoteResizeHandle(context, node, StickyNoteResizeCorner.BOTTOM_LEFT, borderColor);
            renderStickyNoteResizeHandle(context, node, StickyNoteResizeCorner.BOTTOM_RIGHT, borderColor);
        }
    }

    private List<String> getStickyNoteDisplayLines(Node node, TextRenderer textRenderer, int maxLines) {
        String source = stickyNoteEditingNode == node ? stickyNoteEditBuffer : node.getStickyNoteText();
        List<String> lines = new ArrayList<>();
        int maxWidth = Math.max(1, node.getStickyNoteBodyWidth());
        String[] rawLines = (source == null ? "" : source).split("\\n", -1);
        for (String rawLine : rawLines) {
            String safeLine = rawLine == null ? "" : rawLine;
            lines.add(trimTextToWidth(safeLine, textRenderer, maxWidth));
            if (lines.size() >= maxLines) {
                return lines;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private void renderStickyNoteResizeHandle(DrawContext context, Node node, StickyNoteResizeCorner corner, int color) {
        int size = node.getStickyNoteResizeHandleSize();
        int left = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> node.getX() - cameraX - size / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> node.getX() + node.getWidth() - cameraX - size / 2;
        };
        int top = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> node.getY() - cameraY - size / 2;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> node.getY() + node.getHeight() - cameraY - size / 2;
        };
        context.fill(left, top, left + size, top + size, UITheme.TEXT_PRIMARY);
        DrawContextBridge.drawBorderInLayer(context, left, top, size, size, color);
    }

    private void renderModeField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
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

    private void renderDropdownSelectorField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
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
        boolean open = modeDropdownOpen && modeDropdownNode == node;

        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#selector:" + fieldLeft + ":" + fieldTop, hovered || open);
        int fieldBackground = isOverSidebar
            ? UITheme.BACKGROUND_SECONDARY
            : AnimationHelper.lerpColor(UITheme.BACKGROUND_SIDEBAR, UITheme.BACKGROUND_TERTIARY, hoverProgress);
        int fieldBorder = isOverSidebar
            ? UITheme.BORDER_SUBTLE
            : AnimationHelper.lerpColor(UITheme.BORDER_DEFAULT, UITheme.BORDER_HIGHLIGHT, hoverProgress);
        int textColor = isOverSidebar
            ? UITheme.TEXT_TERTIARY
            : AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, hoverProgress);
        int labelColor = includeValue && !isOverSidebar && !(hovered || open)
            ? UITheme.NODE_LABEL_COLOR
            : textColor;

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldTop + fieldHeight, fieldBackground);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, fieldBorder);

        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;
        int textX = fieldLeft + 4;
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;

        if (includeValue) {
            drawNodeText(context, textRenderer, Text.literal(label), textX, textY, labelColor);
            int valueStartX = textX + textRenderer.getWidth(label) + 6;
            int maxValueWidth = Math.max(0, chevronCenterX - 5 - valueStartX);
            String displayValue = trimTextToWidth(value != null ? value : "", textRenderer, maxValueWidth);
            drawNodeText(context, textRenderer, Text.literal(displayValue), valueStartX, textY, textColor);
        } else {
            int maxLabelWidth = Math.max(0, fieldWidth - 20);
            String displayLabel = trimTextToWidth(label != null ? label : "", textRenderer, maxLabelWidth);
            drawNodeText(context, textRenderer, Text.literal(displayLabel), textX, textY, textColor);
        }

        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, textColor);
    }

    private float getAnimatedHoverProgress(Object key, boolean highlighted) {
        return HoverAnimator.getProgress(key, highlighted, UITheme.HOVER_ANIM_MS);
    }

    private float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active) {
        return active ? 1f : getAnimatedHoverProgress(key, hovered);
    }

    private void renderDirectionModeTabs(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                         int fieldTop, int mouseX, int mouseY) {
        if (!isCombinedDirectionNode(node)) {
            return;
        }
        int fieldLeft = getParameterFieldLeft(node) - cameraX;
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = DIRECTION_MODE_TAB_HEIGHT;
        int splitX = fieldLeft + fieldWidth / 2;
        int accentColor = getSelectedNodeAccentColor();
        int inactiveBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int inactiveBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeText = UITheme.TEXT_EDITING;
        int inactiveText = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        boolean exactMode = node.isDirectionModeExact();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int worldLeft = getParameterFieldLeft(node);
        int worldTop = fieldTop + cameraY;
        int halfWidth = Math.max(1, fieldWidth / 2);
        boolean hoverExact = !isOverSidebar
            && worldMouseX >= worldLeft
            && worldMouseX < worldLeft + halfWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;
        boolean hoverCardinal = !isOverSidebar
            && worldMouseX >= worldLeft + halfWidth
            && worldMouseX <= worldLeft + fieldWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;

        int exactLeft = fieldLeft;
        int exactWidth = Math.max(1, halfWidth);
        int cardinalLeft = splitX;
        int cardinalWidth = Math.max(1, fieldLeft + fieldWidth - splitX);

        int exactBackground = exactMode ? activeBackground : inactiveBackground;
        int cardinalBackground = exactMode ? inactiveBackground : activeBackground;
        int exactBorder = exactMode ? accentColor : inactiveBorder;
        int cardinalBorder = exactMode ? inactiveBorder : accentColor;
        if (hoverExact && !exactMode) {
            exactBackground = UITheme.BACKGROUND_TERTIARY;
        }
        if (hoverCardinal && exactMode) {
            cardinalBackground = UITheme.BACKGROUND_TERTIARY;
        }

        context.fill(exactLeft, fieldTop, exactLeft + exactWidth, fieldTop + fieldHeight, exactBackground);
        DrawContextBridge.drawBorderInLayer(context, exactLeft, fieldTop, exactWidth, fieldHeight, exactBorder);
        context.fill(cardinalLeft, fieldTop, cardinalLeft + cardinalWidth, fieldTop + fieldHeight, cardinalBackground);
        DrawContextBridge.drawBorderInLayer(context, cardinalLeft, fieldTop, cardinalWidth, fieldHeight, cardinalBorder);

        int exactLabelX = exactLeft + Math.max(0, (exactWidth - textRenderer.getWidth("Exact")) / 2);
        int cardinalLabelX = cardinalLeft + Math.max(0, (cardinalWidth - textRenderer.getWidth("Cardinal")) / 2);
        int labelY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        drawNodeText(context, textRenderer, Text.literal("Exact"), exactLabelX, labelY, exactMode ? activeText : inactiveText);
        drawNodeText(context, textRenderer, Text.literal("Cardinal"), cardinalLabelX, labelY, exactMode ? inactiveText : activeText);
    }

    private void renderBooleanModeTabs(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                       int fieldTop, int mouseX, int mouseY) {
        if (!isCombinedBooleanNode(node)) {
            return;
        }
        int fieldLeft = getParameterFieldLeft(node) - cameraX;
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = DIRECTION_MODE_TAB_HEIGHT;
        int splitX = fieldLeft + fieldWidth / 2;
        int accentColor = getSelectedNodeAccentColor();
        int inactiveBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int inactiveBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeText = UITheme.TEXT_EDITING;
        int inactiveText = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        boolean literalMode = node.isBooleanModeLiteral();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int worldLeft = getParameterFieldLeft(node);
        int worldTop = fieldTop + cameraY;
        int halfWidth = Math.max(1, fieldWidth / 2);
        boolean hoverLiteral = !isOverSidebar
            && worldMouseX >= worldLeft
            && worldMouseX < worldLeft + halfWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;
        boolean hoverVariable = !isOverSidebar
            && worldMouseX >= worldLeft + halfWidth
            && worldMouseX <= worldLeft + fieldWidth
            && worldMouseY >= worldTop
            && worldMouseY <= worldTop + fieldHeight;

        int literalLeft = fieldLeft;
        int literalWidth = Math.max(1, halfWidth);
        int variableLeft = splitX;
        int variableWidth = Math.max(1, fieldLeft + fieldWidth - splitX);

        int literalBackground = literalMode ? activeBackground : inactiveBackground;
        int variableBackground = literalMode ? inactiveBackground : activeBackground;
        int literalBorder = literalMode ? accentColor : inactiveBorder;
        int variableBorder = literalMode ? inactiveBorder : accentColor;
        if (hoverLiteral && !literalMode) {
            literalBackground = UITheme.BACKGROUND_TERTIARY;
        }
        if (hoverVariable && literalMode) {
            variableBackground = UITheme.BACKGROUND_TERTIARY;
        }

        context.fill(literalLeft, fieldTop, literalLeft + literalWidth, fieldTop + fieldHeight, literalBackground);
        DrawContextBridge.drawBorderInLayer(context, literalLeft, fieldTop, literalWidth, fieldHeight, literalBorder);
        context.fill(variableLeft, fieldTop, variableLeft + variableWidth, fieldTop + fieldHeight, variableBackground);
        DrawContextBridge.drawBorderInLayer(context, variableLeft, fieldTop, variableWidth, fieldHeight, variableBorder);

        int literalLabelX = literalLeft + Math.max(0, (literalWidth - textRenderer.getWidth("Literal")) / 2);
        int variableLabelX = variableLeft + Math.max(0, (variableWidth - textRenderer.getWidth("Variable")) / 2);
        int labelY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        drawNodeText(context, textRenderer, Text.literal("Literal"), literalLabelX, labelY, literalMode ? activeText : inactiveText);
        drawNodeText(context, textRenderer, Text.literal("Variable"), variableLabelX, labelY, literalMode ? inactiveText : activeText);
    }

    private String getParameterLabelText(Node node, NodeParameter parameter, TextRenderer textRenderer, int maxWidth) {
        String displayName = node.getParameterDisplayName(parameter);
        if (displayName == null || displayName.isEmpty()) {
            return "";
        }
        String label = displayName + ":";
        if (textRenderer == null || maxWidth <= 0) {
            return label;
        }
        return trimTextToWidth(label, textRenderer, maxWidth);
    }

    private boolean shouldLeftAlignParameterValue(Node node) {
        if (node == null) {
            return false;
        }
        return node.getType() == NodeType.PARAM_BLOCK
            || node.getType() == NodeType.PARAM_INVENTORY_SLOT;
    }

    private int getParameterValueStartX(Node node, NodeParameter parameter, TextRenderer textRenderer) {
        int fieldLeft = getParameterFieldLeft(node);
        if (shouldLeftAlignParameterValue(node)) {
            return fieldLeft + 4;
        }
        int fieldWidth = getParameterFieldWidth(node);
        int maxLabelWidth = Math.max(0, fieldWidth - 40);
        String label = getParameterLabelText(node, parameter, textRenderer, maxLabelWidth);
        int labelWidth = textRenderer != null ? textRenderer.getWidth(label) : 0;
        return fieldLeft + 4 + labelWidth + 4;
    }

    private String formatVillagerTradeValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }
        if (!rawValue.contains("|") || !rawValue.contains("@")) {
            return rawValue;
        }
        String[] parts = rawValue.split("\\|");
        if (parts.length < 1) {
            return rawValue;
        }
        TradeKeyPart first = parseTradeKeyPart(parts[0]);
        TradeKeyPart second = parts.length > 1 ? parseTradeKeyPart(parts[1]) : null;
        TradeKeyPart sell = parts.length > 2 ? parseTradeKeyPart(parts[2]) : null;
        if (sell == null || sell.name == null || sell.name.isEmpty()) {
            return rawValue;
        }
        StringBuilder builder = new StringBuilder();
        if (first != null && first.isValid()) {
            builder.append(first.format());
        }
        if (second != null && second.isValid()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(second.format());
        }
        if (builder.length() > 0) {
            builder.append(" -> ");
        }
        builder.append(sell.format());
        return builder.toString();
    }

    private TradeKeyPart parseTradeKeyPart(String part) {
        if (part == null || part.isEmpty() || "none@0".equals(part)) {
            return TradeKeyPart.empty();
        }
        int atIndex = part.indexOf('@');
        if (atIndex <= 0) {
            return TradeKeyPart.empty();
        }
        String itemId = part.substring(0, atIndex);
        String countRaw = part.substring(atIndex + 1);
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countRaw));
        } catch (NumberFormatException ignored) {
            count = 1;
        }
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return TradeKeyPart.empty();
        }
        Item item = Registries.ITEM.get(identifier);
        return new TradeKeyPart(item.getName().getString(), count);
    }

    private static final class TradeKeyPart {
        private static final TradeKeyPart EMPTY = new TradeKeyPart("", 0);
        private final String name;
        private final int count;

        private TradeKeyPart(String name, int count) {
            this.name = name;
            this.count = count;
        }

        private static TradeKeyPart empty() {
            return EMPTY;
        }

        private boolean isValid() {
            return name != null && !name.isEmpty() && count > 0;
        }

        private String format() {
            if (count > 1) {
                return count + "x " + name;
            }
            return name;
        }
    }

    private void renderStartNodeNumber(DrawContext context, TextRenderer textRenderer, Node node, int x, int y, boolean isOverSidebar) {
        int number = node.getStartNodeNumber();
        if (number <= 0) {
            return;
        }

        String label = String.valueOf(number);
        int textColor = isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY;
        drawNodeText(context, textRenderer, label, x + 4, y + 4, textColor);
    }

    private void renderBooleanToggleButton(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (node == null || node.getType() == NodeType.PARAM_BOOLEAN) {
            return;
        }
        int buttonLeft = node.getBooleanToggleLeft() - cameraX;
        int buttonTop = node.getBooleanToggleTop() - cameraY;
        int buttonWidth = node.getBooleanToggleWidth();
        int buttonHeight = node.getBooleanToggleHeight();

        boolean hovered = isPointInsideBooleanToggle(node, mouseX, mouseY);
        int borderColor;
        int fillColor;
        int textColor;
        if (isOverSidebar) {
            borderColor = UITheme.BORDER_HIGHLIGHT;
            fillColor = UITheme.BACKGROUND_SECONDARY;
            textColor = UITheme.TEXT_TERTIARY;
        } else {
            float progress = getNodeToggleProgress(booleanToggleAnimations, node, node.getBooleanToggleValue());
            int accentColor = getSelectedNodeAccentColor();
            int onBorder = adjustColorBrightness(accentColor, 1.12f);
            int onFill = adjustColorBrightness(accentColor, 0.22f);
            borderColor = AnimationHelper.lerpColor(UITheme.TOGGLE_OFF_BORDER, onBorder, progress);
            fillColor = AnimationHelper.lerpColor(UITheme.BOOL_TOGGLE_OFF_FILL, onFill, progress);
            if (hovered) {
                fillColor = adjustColorBrightness(fillColor, 1.12f);
                borderColor = adjustColorBrightness(borderColor, 1.05f);
            }
            textColor = UITheme.TEXT_PRIMARY;
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, fillColor);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, borderColor);

        String label = node.getBooleanToggleValue() ? "TRUE" : "FALSE";
        int textWidth = textRenderer.getWidth(label);
        int textX = buttonLeft + Math.max(2, (buttonWidth - textWidth) / 2);
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2 + 1;
        drawNodeText(context, textRenderer, Text.literal(label), textX, textY, textColor);
    }

    private void renderSensorSlot(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int slotX = node.getSensorSlotLeft() - cameraX;
        int slotY = node.getSensorSlotTop() - cameraY;
        int slotWidth = node.getSensorSlotWidth();
        int slotHeight = node.getSensorSlotHeight();
        boolean useLogicSlotTitle = usesLogicSensorSlotTitle(node);

        int backgroundColor = node.hasAttachedSensor() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = UITheme.NODE_INPUT_BG_DIMMED;
        }

        int borderColor = node.hasAttachedSensor() ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (sensorDropTarget == node) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_BLUE;
            borderColor = getSelectedNodeAccentColor();
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (useLogicSlotTitle) {
            String titleDisplay = trimTextToWidth(getLogicSensorSlotTitle(node), textRenderer, slotWidth - 4);
            int titleY = slotY - textRenderer.fontHeight - 2;
            int titleColor = sensorDropTarget == node ? getSelectedNodeAccentColor() : (isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY);
            drawNodeText(context, textRenderer, Text.literal(titleDisplay), slotX + 2, titleY, titleColor);
        }

        if (!node.hasAttachedSensor()) {
            if (useLogicSlotTitle) {
                return;
            }
            String placeholder = "Drag a sensor here";
            String display = trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.getWidth(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.fontHeight) / 2;
            int textColor = sensorDropTarget == node ? getSelectedNodeAccentColor() : UITheme.TEXT_TERTIARY;
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
        }
    }

    private void renderActionSlot(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int slotX = node.getActionSlotLeft() - cameraX;
        int slotY = node.getActionSlotTop() - cameraY;
        int slotWidth = node.getActionSlotWidth();
        int slotHeight = node.getActionSlotHeight();
        boolean useLogicSlotTitle = usesLogicActionSlotTitle(node);

        int backgroundColor = node.hasAttachedActionNode() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = UITheme.NODE_INPUT_BG_DIMMED;
        }

        int borderColor = node.hasAttachedActionNode() ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (actionDropTarget == node) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_GREEN;
            borderColor = UITheme.DROP_ACCENT_GREEN;
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (useLogicSlotTitle) {
            String title = getLogicActionSlotTitle(node);
            int titleColor = actionDropTarget == node ? UITheme.DROP_ACCENT_GREEN : (isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY);
            int titleY = slotY - textRenderer.fontHeight - 2;
            String titleDisplay = trimTextToWidth(title, textRenderer, slotWidth - 4);
            drawNodeText(context, textRenderer, Text.literal(titleDisplay), slotX + 2, titleY, titleColor);
        }

        if (!node.hasAttachedActionNode()) {
            if (useLogicSlotTitle) {
                return;
            }
            String placeholder = "Drag a node here";
            String display = trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.getWidth(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.fontHeight) / 2;
            int textColor = actionDropTarget == node ? UITheme.DROP_ACCENT_GREEN : UITheme.TEXT_TERTIARY;
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
        }
    }

    private boolean usesLogicSensorSlotTitle(Node node) {
        if (node == null) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.CONTROL_IF
            || type == NodeType.CONTROL_IF_ELSE
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_WAIT_UNTIL;
    }

    private boolean usesLogicActionSlotTitle(Node node) {
        if (node == null) {
            return false;
        }
        NodeType type = node.getType();
        return type == NodeType.CONTROL_REPEAT
            || type == NodeType.CONTROL_REPEAT_UNTIL
            || type == NodeType.CONTROL_FOREVER;
    }

    private String getLogicSensorSlotTitle(Node node) {
        return "Condition";
    }

    private String getLogicActionSlotTitle(Node node) {
        if (node != null && node.getType() == NodeType.CONTROL_REPEAT) {
            return "Repeat Body";
        }
        return "Loop Body";
    }

    private boolean isPointInsideBooleanToggle(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasBooleanToggle() || node.getType() == NodeType.PARAM_BOOLEAN) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int buttonLeft = node.getBooleanToggleLeft();
        int buttonTop = node.getBooleanToggleTop();
        int buttonWidth = node.getBooleanToggleWidth();
        int buttonHeight = node.getBooleanToggleHeight();
        return worldMouseX >= buttonLeft && worldMouseX <= buttonLeft + buttonWidth &&
               worldMouseY >= buttonTop && worldMouseY <= buttonTop + buttonHeight;
    }

    public boolean handleBooleanToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideBooleanToggle(node, mouseX, mouseY)) {
            return false;
        }
        node.toggleBooleanToggleValue();
        getNodeToggleAnimation(booleanToggleAnimations, node, node.getBooleanToggleValue())
            .animateTo(node.getBooleanToggleValue() ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        notifyNodeParametersChanged(node);
        return true;
    }

    public boolean handleBooleanModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findBooleanModeNodeAt(worldX, worldY);
        if (!isCombinedBooleanNode(node)) {
            return false;
        }
        int fieldLeft = getParameterFieldLeft(node);
        int fieldTop = getBooleanModeTabTop(node);
        int fieldWidth = getParameterFieldWidth(node);
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

    public boolean handleMessageButtonClick(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasMessageInputFields()) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int size = node.getMessageButtonSize();
        int top = node.getMessageButtonTop();
        int addLeft = node.getMessageAddButtonLeft();
        int removeLeft = node.getMessageRemoveButtonLeft();
        boolean handled = false;

        boolean overAdd = worldMouseX >= addLeft && worldMouseX <= addLeft + size
            && worldMouseY >= top && worldMouseY <= top + size;
        boolean overRemove = worldMouseX >= removeLeft && worldMouseX <= removeLeft + size
            && worldMouseY >= top && worldMouseY <= top + size;

        if (overAdd) {
            stopMessageEditing(true);
            node.addMessageLine("");
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
            startMessageEditing(node, node.getMessageFieldCount() - 1);
            handled = true;
        } else if (overRemove && node.getMessageFieldCount() > 1) {
            int targetIndex = (messageEditingNode == node && messageEditingIndex >= 0)
                ? messageEditingIndex
                : node.getMessageFieldCount() - 1;
            stopMessageEditing(true);
            int removeIndex = Math.min(node.getMessageFieldCount() - 1, targetIndex);
            node.removeMessageLine(removeIndex);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
            int nextIndex = Math.max(0, Math.min(removeIndex, node.getMessageFieldCount() - 1));
            startMessageEditing(node, nextIndex);
            handled = true;
        }

        return handled;
    }

    private boolean isPointInsideMessageScopeToggle(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasMessageInputFields()) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int left = node.getMessageScopeToggleLeft();
        int top = node.getMessageScopeToggleTop();
        int width = node.getMessageScopeToggleWidth();
        int height = node.getMessageScopeToggleHeight();
        return worldMouseX >= left && worldMouseX <= left + width
            && worldMouseY >= top && worldMouseY <= top + height;
    }

    public boolean handleMessageScopeToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideMessageScopeToggle(node, mouseX, mouseY)) {
            return false;
        }
        stopMessageEditing(true);
        node.toggleMessageClientSide();
        AnimatedValue animation = getMessageScopeAnimation(node);
        animation.animateTo(node.isMessageClientSide() ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        notifyNodeParametersChanged(node);
        return true;
    }

    private AnimatedValue getMessageScopeAnimation(Node node) {
        boolean clientSide = node != null && node.isMessageClientSide();
        return messageScopeAnimations.computeIfAbsent(node, key -> AnimatedValue.forToggle(clientSide));
    }

    private AnimatedValue getNodeToggleAnimation(Map<Node, AnimatedValue> animations, Node node, boolean enabled) {
        return animations.computeIfAbsent(node, key -> AnimatedValue.forToggle(enabled));
    }

    private float getNodeToggleProgress(Map<Node, AnimatedValue> animations, Node node, boolean enabled) {
        AnimatedValue animation = getNodeToggleAnimation(animations, node, enabled);
        animation.animateTo(enabled ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        animation.tick();
        return AnimationHelper.easeInOutCubic(animation.getValue());
    }

    private void renderNodeSliderToggle(DrawContext context, int toggleLeft, int toggleTop, int toggleWidth, int toggleHeight,
                                        float progress, boolean hovered, boolean isOverSidebar) {
        int accentColor = getSelectedNodeAccentColor();
        int onBorder = adjustColorBrightness(accentColor, 1.12f);
        int onBg = adjustColorBrightness(accentColor, 0.28f);
        int toggleBorder = isOverSidebar
            ? UITheme.BORDER_HIGHLIGHT
            : AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, onBorder, progress);
        int toggleBg = isOverSidebar
            ? UITheme.BACKGROUND_SECONDARY
            : AnimationHelper.lerpColor(UITheme.BACKGROUND_TERTIARY, onBg, progress);
        if (hovered && !isOverSidebar) {
            toggleBg = adjustColorBrightness(toggleBg, 1.08f);
            toggleBorder = adjustColorBrightness(toggleBorder, 1.04f);
        }
        context.fill(toggleLeft, toggleTop, toggleLeft + toggleWidth, toggleTop + toggleHeight, toggleBg);
        DrawContextBridge.drawBorderInLayer(context, toggleLeft, toggleTop, toggleWidth, toggleHeight, toggleBorder);
        int knobWidth = toggleHeight - 2;
        int knobTravel = Math.max(0, toggleWidth - knobWidth - 2);
        int knobLeft = toggleLeft + 1 + Math.round(knobTravel * progress);
        context.fill(knobLeft, toggleTop + 1, knobLeft + knobWidth, toggleTop + toggleHeight - 1, UITheme.TOGGLE_KNOB);
    }

    private int adjustColorBrightness(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = MathHelper.clamp((int) (r * factor), 0, 255);
        g = MathHelper.clamp((int) (g * factor), 0, 255);
        b = MathHelper.clamp((int) (b * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getSelectedNodeAccentColor() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        if (settings == null || settings.accentColor == null) {
            return UITheme.ACCENT_DEFAULT;
        }
        switch (settings.accentColor.toLowerCase(Locale.ROOT)) {
            case "sky":
                return UITheme.ACCENT_SKY;
            case "mint":
                return UITheme.ACCENT_MINT;
            case "amber":
                return UITheme.ACCENT_AMBER;
            default:
                return UITheme.ACCENT_DEFAULT;
        }
    }

    private int toGrayscale(int color, float brightnessFactor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int gray = MathHelper.clamp((int) ((0.299f * r + 0.587f * g + 0.114f * b) * brightnessFactor), 0, 255);
        return (a << 24) | (gray << 16) | (gray << 8) | gray;
    }

    private void renderParameterSlot(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int slotIndex) {
        int slotX = node.getParameterSlotLeft(slotIndex) - cameraX;
        int slotY = node.getParameterSlotTop(slotIndex) - cameraY;
        int slotWidth = node.getParameterSlotWidth(slotIndex);
        int slotHeight = node.getParameterSlotHeight(slotIndex);

        Node parameterNode = node.getAttachedParameter(slotIndex);
        boolean occupied = parameterNode != null;
        boolean isDropTarget = parameterDropTarget == node && parameterDropSlotIndex != null && parameterDropSlotIndex == slotIndex;

        int backgroundColor = occupied ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = occupied ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_PRIMARY;
        }

        int borderColor = occupied ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (isDropTarget) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_BLUE;
            borderColor = getSelectedNodeAccentColor();
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        String headerText = node.getParameterSlotLabel(slotIndex);
        int headerColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int headerY = slotY - textRenderer.fontHeight - 2;
        if (headerY > node.getY() - cameraY + 14) {
            drawNodeText(context, textRenderer, Text.literal(headerText), slotX + 2, headerY, headerColor);
        }

        if (!occupied && isDropTarget) {
            // Provide a minimal visual cue when dragging to an empty slot without adding text.
            DrawContextBridge.drawBorderInLayer(context, slotX + 2, slotY + 2, slotWidth - 4, slotHeight - 4, getSelectedNodeAccentColor());
        }

        if (node.usesMinimalNodePresentation()
            && isComparisonOperator(node)
            && slotIndex == 0) {
            int leftSlotX = node.getParameterSlotLeft(0) - cameraX;
            int rightSlotX = node.getParameterSlotLeft(1) - cameraX;
            int leftSlotWidth = node.getParameterSlotWidth(0);
            int leftSlotHeight = node.getParameterSlotHeight(0);
            int rightSlotHeight = node.getParameterSlotHeight(1);
            int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;
            String operatorText = getOperatorSymbol(node, true);
            int operatorWidth = textRenderer.getWidth(operatorText);
            int operatorX = gapCenterX - operatorWidth / 2;
            int leftSlotTop = node.getParameterSlotTop(0) - cameraY;
            int rightSlotTop = node.getParameterSlotTop(1) - cameraY;
            int leftCenterY = leftSlotTop + leftSlotHeight / 2;
            int rightCenterY = rightSlotTop + rightSlotHeight / 2;
            int operatorCenterY = (leftCenterY + rightCenterY) / 2;
            int operatorY = operatorCenterY - textRenderer.fontHeight / 2;
            int operatorColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_SYMBOL, 0.85f) : UITheme.NODE_OPERATOR_SYMBOL;
            if (node.getType() == NodeType.OPERATOR_GREATER || node.getType() == NodeType.OPERATOR_LESS) {
                int buttonPaddingX = 3;
                int buttonPaddingY = 4;
                int maxSymbolWidth = textRenderer.getWidth(">=");
                int buttonWidth = maxSymbolWidth + buttonPaddingX * 2;
                int buttonHeight = textRenderer.fontHeight + buttonPaddingY * 2;
                int buttonLeft = gapCenterX - buttonWidth / 2;
                int buttonTop = operatorY - buttonPaddingY;
                int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
                int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
                DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);
                operatorX = buttonLeft + (buttonWidth - operatorWidth) / 2;
            }
            drawNodeText(
                context,
                textRenderer,
                Text.literal(operatorText),
                operatorX,
                operatorY,
                operatorColor
            );
        }
    }

    private boolean isComparisonOperator(Node node) {
        if (node == null || node.getType() == null) {
            return false;
        }
        switch (node.getType()) {
            case OPERATOR_EQUALS:
            case OPERATOR_NOT:
            case OPERATOR_BOOLEAN_OR:
            case OPERATOR_BOOLEAN_AND:
            case OPERATOR_BOOLEAN_XOR:
            case OPERATOR_GREATER:
            case OPERATOR_LESS:
                return true;
            default:
                return false;
        }
    }

    private boolean isOperatorInclusive(Node node) {
        if (node == null) {
            return false;
        }
        NodeParameter param = node.getParameter("Inclusive");
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BOOLEAN) {
            return param.getBoolValue();
        }
        String value = param.getStringValue();
        return value != null && Boolean.parseBoolean(value.trim());
    }

    private String getOperatorSymbol(Node node, boolean minimalStyle) {
        if (node == null || node.getType() == null) {
            return "";
        }
        switch (node.getType()) {
            case OPERATOR_EQUALS:
                return "=";
            case OPERATOR_NOT:
                return minimalStyle ? "=/" : "!=";
            case OPERATOR_BOOLEAN_OR:
                return "OR";
            case OPERATOR_BOOLEAN_AND:
                return "AND";
            case OPERATOR_BOOLEAN_XOR:
                return "XOR";
            case OPERATOR_GREATER:
                return isOperatorInclusive(node) ? ">=" : ">";
            case OPERATOR_LESS:
                return isOperatorInclusive(node) ? "<=" : "<";
            default:
                return "";
        }
    }

    private boolean isOperatorToggleHit(Node node, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!isComparisonOperator(node)) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int leftSlotX = node.getParameterSlotLeft(0);
        int rightSlotX = node.getParameterSlotLeft(1);
        int leftSlotWidth = node.getParameterSlotWidth(0);
        int leftSlotHeight = node.getParameterSlotHeight(0);
        int rightSlotHeight = node.getParameterSlotHeight(1);
        int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;

        int leftSlotTop = node.getParameterSlotTop(0);
        int rightSlotTop = node.getParameterSlotTop(1);
        int leftCenterY = leftSlotTop + leftSlotHeight / 2;
        int rightCenterY = rightSlotTop + rightSlotHeight / 2;
        int operatorCenterY = (leftCenterY + rightCenterY) / 2;

        String operatorText = getOperatorSymbol(node, node.usesMinimalNodePresentation());
        int textWidth = textRenderer.getWidth(operatorText);
        int textHeight = textRenderer.fontHeight;
        int padding = 4;
        int hitLeft = gapCenterX - textWidth / 2 - padding;
        int hitRight = gapCenterX + textWidth / 2 + padding;
        int hitTop = operatorCenterY - textHeight / 2 - padding;
        int hitBottom = operatorCenterY + textHeight / 2 + padding;

        return worldMouseX >= hitLeft && worldMouseX <= hitRight && worldMouseY >= hitTop && worldMouseY <= hitBottom;
    }

    public boolean handleOperatorToggleClick(TextRenderer textRenderer, int mouseX, int mouseY) {
        if (textRenderer == null) {
            return false;
        }
        Node node = getNodeAt(mouseX, mouseY);
        if (node == null || node.getType() == null) {
            return false;
        }
        if (node.getType() != NodeType.OPERATOR_GREATER && node.getType() != NodeType.OPERATOR_LESS) {
            return false;
        }
        if (!isOperatorToggleHit(node, textRenderer, mouseX, mouseY)) {
            return false;
        }
        NodeParameter param = node.getParameter("Inclusive");
        if (param == null) {
            return false;
        }
        boolean next = !isOperatorInclusive(node);
        param.setStringValueFromUser(Boolean.toString(next));
        node.recalculateDimensions();
        return true;
    }

    private String[] getCoordinateAxes(Node node) {
        if (node == null) {
            return new String[0];
        }
        return node.getCoordinateFieldAxes();
    }

    private void renderScreenCoordinatePickerButton(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
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
            buttonBorder = getSelectedNodeAccentColor();
        } else if (hovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = isScreenCoordinateCaptureActiveFor(node) ? "Click To Set" : "Pick";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + Math.max(0, (buttonWidth - textRenderer.getWidth(buttonLabel)) / 2);
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(buttonLabel), textX, textY, textColor);
    }

    private void renderCoordinateInputFields(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                             int mouseX, int mouseY) {
        String[] axes = getCoordinateAxes(node);
        int baseLabelColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = getSelectedNodeAccentColor();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;

        if (isEditingCoordinateField() && coordinateEditingNode == node) {
            updateCoordinateCaretBlink();
        }

        int labelTop = node.getCoordinateFieldLabelTop() - cameraY;
        int labelHeight = node.getCoordinateFieldLabelHeight();
        int inputTop = node.getCoordinateFieldInputTop() - cameraY;
        int fieldHeight = node.getCoordinateFieldHeight();
        int fieldWidth = node.getCoordinateFieldWidth();
        int spacing = node.getCoordinateFieldSpacing();
        int startX = node.getCoordinateFieldStartX() - cameraX;
        boolean captureActive = isScreenCoordinateCaptureActiveFor(node);
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        for (int i = 0; i < axes.length; i++) {
            int fieldX = startX + i * (fieldWidth + spacing);

            boolean editingAxis = isEditingCoordinateField()
                && coordinateEditingNode == node
                && coordinateEditingAxis == i;

            String axisLabel = axes[i];
            int labelWidth = textRenderer.getWidth(axisLabel);
            int labelX = fieldX + Math.max(0, (fieldWidth - labelWidth) / 2);
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
            int labelColor = editingAxis ? UITheme.TEXT_EDITING_LABEL : baseLabelColor;
            drawNodeText(context, textRenderer, Text.literal(axisLabel), labelX, labelY, labelColor);

            int inputBottom = inputTop + fieldHeight;
            boolean hovered = !isOverSidebar
                && worldMouseX >= fieldX + cameraX
                && worldMouseX <= fieldX + cameraX + fieldWidth
                && worldMouseY >= inputTop + cameraY
                && worldMouseY <= inputTop + cameraY + fieldHeight;
            float progress = getTextFieldHighlightProgress(node.getId() + "#coord:" + i, hovered, editingAxis);
            int backgroundColor = isOverSidebar ? (editingAxis ? activeFieldBackground : fieldBackground)
                : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
            int borderColor = isOverSidebar ? (editingAxis ? activeFieldBorder : fieldBorder)
                : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
            int valueColor = isOverSidebar ? (editingAxis ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress);
            if (captureActive && !editingAxis) {
                borderColor = getSelectedNodeAccentColor();
                valueColor = UITheme.TEXT_TERTIARY;
            }

            context.fill(fieldX, inputTop, fieldX + fieldWidth, inputBottom, backgroundColor);
            DrawContextBridge.drawBorderInLayer(context, fieldX, inputTop, fieldWidth, fieldHeight, borderColor);

            String value;
            if (editingAxis) {
                value = coordinateEditBuffer;
            } else if (captureActive) {
                value = Integer.toString(i == 0 ? screenCoordinatePreviewX : screenCoordinatePreviewY);
            } else {
                NodeParameter parameter = node.getParameter(axisLabel);
                value = parameter != null ? parameter.getStringValue() : "";
            }
            if (value == null) {
                value = "";
            }

            String display = editingAxis
                ? value
                : trimTextToWidth(value, textRenderer, fieldWidth - 6);
            int variableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
            Set<String> coordVariableNames = collectRuntimeVariableNames(node);
            InlineVariableRender coordRenderData = null;
            if (!coordVariableNames.isEmpty() && value.indexOf('~') >= 0) {
                InlineVariableRender candidate = buildInlineVariableRender(value, coordVariableNames, valueColor, variableHighlightColor);
                if (editingAxis) {
                    coordRenderData = candidate;
                    display = coordRenderData.displayText;
                } else if (textRenderer.getWidth(candidate.displayText) <= fieldWidth - 6) {
                    coordRenderData = candidate;
                    display = coordRenderData.displayText;
                } else if (isSingleKnownInlineVariableReference(value, coordVariableNames)) {
                    display = trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                    valueColor = variableHighlightColor;
                }
            }

            int textX = fieldX + 3;
            int textY = inputTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
            if (editingAxis && hasCoordinateSelection()) {
                int start = coordinateSelectionStart;
                int end = coordinateSelectionEnd;
                if (coordRenderData != null) {
                    start = coordRenderData.toDisplayIndex(start);
                    end = coordRenderData.toDisplayIndex(end);
                }
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, inputTop + 2, selectionEndX, inputBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (coordRenderData != null && shouldRenderNodeText()) {
                coordRenderData.draw(context, textRenderer, textX, textY);
            } else {
                drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);
            }

            if (editingAxis && coordinateCaretVisible) {
                int caretIndex = coordinateCaretPosition;
                if (coordRenderData != null) {
                    caretIndex = coordRenderData.toDisplayIndex(caretIndex);
                }
                caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldX + fieldWidth - 2);
                int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, inputBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, fieldX + fieldWidth - 2, UITheme.CARET_COLOR);
            }
        }

        if (node.hasScreenCoordinatePickerButton()) {
            renderScreenCoordinatePickerButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    }

    private void renderAmountInputField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                        int mouseX, int mouseY) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = getSelectedNodeAccentColor();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;
        boolean amountEnabled = node.isAmountInputEnabled();

        boolean editing = isEditingAmountField() && amountEditingNode == node;
        if (editing) {
            updateAmountCaretBlink();
        }

        int labelTop = node.getAmountFieldLabelTop() - cameraY;
        int labelHeight = node.getAmountFieldLabelHeight();
        int fieldTop = node.getAmountFieldInputTop() - cameraY;
        int fieldHeight = node.getAmountFieldHeight();
        int fieldLeft = node.getAmountFieldLeft() - cameraX;
        int fieldWidth = node.getAmountFieldWidth();
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        if ((node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) && node.supportsModeSelection()) {
            int dropdownLeft = fieldLeft;
            int dropdownTop = labelTop;
            int dropdownWidth = fieldWidth;
            int dropdownHeight = labelHeight;
            String unitLabel = node.getAmountFieldLabel();
            renderDropdownSelectorField(
                context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                dropdownLeft, dropdownTop, dropdownWidth, dropdownHeight,
                unitLabel, false, null
            );
        } else {
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
            drawNodeText(context, textRenderer, Text.literal(node.getAmountFieldLabel()), fieldLeft + 2, labelY, baseLabelColor);
        }

        int fieldBottom = fieldTop + fieldHeight;
        int disabledBg = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BUTTON_DEFAULT_BG;
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getAmountFieldLeft()
            && worldMouseX <= node.getAmountFieldLeft() + fieldWidth
            && worldMouseY >= node.getAmountFieldInputTop()
            && worldMouseY <= node.getAmountFieldInputTop() + fieldHeight;
        float progress = amountEnabled
            ? getTextFieldHighlightProgress(node.getId() + "#amount", hovered, editing)
            : 0f;
        int backgroundColor = amountEnabled
            ? (isOverSidebar ? (editing ? activeFieldBackground : fieldBackground)
                : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress))
            : disabledBg;
        int borderColor = amountEnabled
            ? (isOverSidebar ? (editing ? activeFieldBorder : fieldBorder)
                : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress))
            : fieldBorder;
        int valueColor = amountEnabled
            ? (isOverSidebar ? ((editing && amountEnabled) ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress))
            : UITheme.TEXT_SECONDARY;

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value;
        if (editing && amountEnabled) {
            value = amountEditBuffer;
        } else {
            String amountKey = node.getAmountParameterKey();
            NodeParameter amountParam = node.getParameter(amountKey);
            value = amountParam != null ? amountParam.getStringValue() : "";
            if (node.getType() == NodeType.MOVE_ITEM && isMoveItemAllAmountValue(value)) {
                value = "";
            }
        }

        boolean showPlaceholder = amountEnabled && value.isEmpty();
        String display = editing
            ? value
            : trimTextToWidth(value, textRenderer, fieldWidth - 6);
        if (showPlaceholder) {
            if (node.getType() == NodeType.MOVE_ITEM) {
                display = "All";
            } else if (node.getType() == NodeType.TRADE
                || node.getType() == NodeType.SENSOR_VILLAGER_TRADE
                || node.getType() == NodeType.SENSOR_IN_STOCK) {
                display = "1";
            } else {
                display = "0";
            }
            valueColor = UITheme.TEXT_TERTIARY;
        }
            int variableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
        Set<String> amountVariableNames = collectRuntimeVariableNames(node);
        InlineVariableRender amountRenderData = null;
        if (amountEnabled && !showPlaceholder && !amountVariableNames.isEmpty() && value != null && value.indexOf('~') >= 0) {
            InlineVariableRender candidate = buildInlineVariableRender(value, amountVariableNames, valueColor, variableHighlightColor);
            if (editing && amountEnabled) {
                amountRenderData = candidate;
                display = amountRenderData.displayText;
            } else if (textRenderer.getWidth(candidate.displayText) <= fieldWidth - 6) {
                amountRenderData = candidate;
                display = amountRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, amountVariableNames)) {
                display = trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && amountEnabled && hasAmountSelection()) {
            int start = amountSelectionStart;
            int end = amountSelectionEnd;
            if (amountRenderData != null) {
                start = amountRenderData.toDisplayIndex(start);
                end = amountRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (amountRenderData != null && shouldRenderNodeText()) {
            amountRenderData.draw(context, textRenderer, textX, textY);
        } else {
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);
        }

        if (editing && amountEnabled && amountCaretVisible) {
            int caretIndex = amountCaretPosition;
            if (amountRenderData != null) {
                caretIndex = amountRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, fieldBottom - 2);
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, fieldLeft + fieldWidth - 2, UITheme.CARET_COLOR);
        }

        if (node.hasAmountToggle()) {
            int toggleLeft = node.getAmountToggleLeft() - cameraX;
            int toggleTop = node.getAmountToggleTop() - cameraY;
            int toggleWidth = node.getAmountToggleWidth();
            int toggleHeight = node.getAmountToggleHeight();
            renderNodeSliderToggle(context, toggleLeft, toggleTop, toggleWidth, toggleHeight,
                getNodeToggleProgress(amountToggleAnimations, node, amountEnabled), false, isOverSidebar);
        }

        if (node.hasAmountSignToggle()) {
            int toggleLeft = node.getAmountSignToggleLeft() - cameraX;
            int toggleTop = node.getAmountSignToggleTop() - cameraY;
            int toggleWidth = node.getAmountSignToggleWidth();
            int toggleHeight = node.getAmountSignToggleHeight();
            boolean open = amountSignDropdownOpen && amountSignDropdownNode == node;
            String operation = node.getAmountOperation();

            int signBorderColor;
            int signFillColor;
            int signTextColor;
            if (isOverSidebar) {
                signBorderColor = UITheme.BORDER_HIGHLIGHT;
                signFillColor = UITheme.BACKGROUND_SECONDARY;
                signTextColor = UITheme.TEXT_TERTIARY;
            } else {
                signBorderColor = open ? getSelectedNodeAccentColor() : UITheme.BORDER_DEFAULT;
                signFillColor = open ? UITheme.NODE_INPUT_BG_ACTIVE : UITheme.BACKGROUND_TERTIARY;
                signTextColor = UITheme.TEXT_PRIMARY;
            }

            context.fill(toggleLeft, toggleTop, toggleLeft + toggleWidth, toggleTop + toggleHeight, signFillColor);
            DrawContextBridge.drawBorderInLayer(context, toggleLeft, toggleTop, toggleWidth, toggleHeight, signBorderColor);

            String label = operation == null || operation.isEmpty() ? "+" : operation;
            String arrow = open ? "v" : "^";
            int labelWidth = textRenderer.getWidth(label);
            int arrowWidth = textRenderer.getWidth(arrow);
            int signTextX = toggleLeft + 3;
            int arrowX = toggleLeft + toggleWidth - arrowWidth - 3;
            int signTextY = toggleTop + (toggleHeight - textRenderer.fontHeight) / 2 + 1;
            drawNodeText(context, textRenderer, Text.literal(label), signTextX, signTextY, signTextColor);
            drawNodeText(context, textRenderer, Text.literal(arrow), arrowX, signTextY, signTextColor);
        }
    }

    private void renderRandomRoundingField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = getSelectedNodeAccentColor();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        boolean enabled = node.isRandomRoundingEnabled();
        boolean open = randomRoundingDropdownOpen && randomRoundingDropdownNode == node;

        int labelTop = node.getRandomRoundingFieldLabelTop() - cameraY;
        int labelHeight = node.getRandomRoundingFieldLabelHeight();
        int fieldTop = node.getRandomRoundingFieldInputTop() - cameraY;
        int fieldHeight = node.getRandomRoundingFieldHeight();
        int fieldLeft = node.getRandomRoundingFieldLeft() - cameraX;
        int fieldWidth = node.getRandomRoundingFieldWidth();
        int fieldRight = fieldLeft + fieldWidth;

        int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
        drawNodeText(context, textRenderer, Text.literal("Rounding"), fieldLeft + 2, labelY, baseLabelColor);

        int fieldBottom = fieldTop + fieldHeight;
        int disabledBg = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BUTTON_DEFAULT_BG;
        int backgroundColor = enabled ? (open ? activeFieldBackground : fieldBackground) : disabledBg;
        int borderColor = enabled && open ? activeFieldBorder : fieldBorder;
        int valueColor = enabled ? textColor : UITheme.TEXT_SECONDARY;

        context.fill(fieldLeft, fieldTop, fieldRight, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value = node.getRandomRoundingModeDisplay();
        String arrow = open ? "v" : "^";
        int arrowWidth = textRenderer.getWidth(arrow);
        int arrowX = fieldRight - arrowWidth - 4;
        int valueStartX = fieldLeft + 4;
        int maxValueWidth = Math.max(0, arrowX - valueStartX - 4);
        String display = trimTextToWidth(value, textRenderer, maxValueWidth);
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        drawNodeText(context, textRenderer, Text.literal(display), valueStartX, textY, valueColor);
        drawNodeText(context, textRenderer, Text.literal(arrow), arrowX, textY, valueColor);

        if (node.hasRandomRoundingToggle()) {
            int toggleLeft = node.getRandomRoundingToggleLeft() - cameraX;
            int toggleTop = node.getRandomRoundingToggleTop() - cameraY;
            int toggleWidth = node.getRandomRoundingToggleWidth();
            int toggleHeight = node.getRandomRoundingToggleHeight();
            renderNodeSliderToggle(context, toggleLeft, toggleTop, toggleWidth, toggleHeight,
                getNodeToggleProgress(randomRoundingToggleAnimations, node, enabled), false, isOverSidebar);
        }
    }

    private void renderRandomRoundingDropdownList(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(randomRoundingDropdownAnimation, randomRoundingDropdownOpen);
        if (randomRoundingDropdownNode == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearRandomRoundingDropdownState();
            return;
        }
        Node node = randomRoundingDropdownNode;
        List<ParameterDropdownOption> options = getRandomRoundingDropdownOptions();
        int optionCount = Math.max(1, options.size());
        float zoom = getZoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);

        int rowHeight = SCHEMATIC_DROPDOWN_ROW_HEIGHT;
        int dropdownWidth = getRandomRoundingDropdownWidth(node);
        int listTop = node.getRandomRoundingFieldInputTop() + node.getRandomRoundingFieldHeight() + 2 - cameraY;
        int listLeft = node.getRandomRoundingFieldLeft() - cameraX;
        int listRight = listLeft + dropdownWidth;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            rowHeight,
            RANDOM_ROUNDING_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, UITheme.BORDER_HIGHLIGHT);

        randomRoundingDropdownScrollOffset = MathHelper.clamp(randomRoundingDropdownScrollOffset, 0, layout.maxScrollOffset);
        randomRoundingDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && transformedMouseX >= listLeft && transformedMouseX <= listRight
            && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / rowHeight;
            if (row >= 0 && row < layout.visibleCount) {
                randomRoundingDropdownHoverIndex = randomRoundingDropdownScrollOffset + row;
            }
        }

        int visibleCount = layout.visibleCount;
        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = randomRoundingDropdownScrollOffset + row;
            String optionLabel = options.isEmpty() ? "No options" : options.get(optionIndex).label();
            int rowTop = listTop + row * rowHeight;
            int rowBottom = rowTop + rowHeight;
            boolean hovered = options.isEmpty() ? row == 0 && randomRoundingDropdownHoverIndex >= 0 : optionIndex == randomRoundingDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, UITheme.BACKGROUND_TERTIARY);
            }
            int textPadding = 5;
        int maxTextWidth = dropdownWidth - (textPadding * 2) - DROPDOWN_SCROLLBAR_ALLOWANCE;
            String rowText = trimTextToWidth(optionLabel, textRenderer, Math.max(0, maxTextWidth));
            int textOffsetY = 4;
            drawNodeText(context, textRenderer, Text.literal(rowText), listLeft + textPadding, rowTop + textOffsetY, UITheme.TEXT_PRIMARY);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            randomRoundingDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    private void renderMessageInputFields(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = getSelectedNodeAccentColor();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;
        int variableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();

        boolean editing = isEditingMessageField() && messageEditingNode == node;
        if (editing) {
            updateMessageCaretBlink();
        }

        Set<String> runtimeVariableNames = collectRuntimeVariableNames(node);
        int fieldCount = node.getMessageFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            int labelTop = node.getMessageFieldLabelTop(i) - cameraY;
            int labelHeight = node.getMessageFieldLabelHeight();
            int fieldTop = node.getMessageFieldInputTop(i) - cameraY;
            int fieldHeight = node.getMessageFieldHeight();
            int fieldLeft = node.getMessageFieldLeft() - cameraX;
            int fieldWidth = node.getMessageFieldWidth();
            int worldMouseX = screenToWorldX(mouseX);
            int worldMouseY = screenToWorldY(mouseY);

            boolean editingThis = editing && messageEditingIndex == i;
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
            String label = fieldCount > 1 ? "Message " + (i + 1) : "Message";
            drawNodeText(context, textRenderer, Text.literal(label), fieldLeft + 2, labelY, baseLabelColor);

            int fieldBottom = fieldTop + fieldHeight;
            boolean hovered = !isOverSidebar
                && worldMouseX >= node.getMessageFieldLeft()
                && worldMouseX <= node.getMessageFieldLeft() + fieldWidth
                && worldMouseY >= node.getMessageFieldInputTop(i)
                && worldMouseY <= node.getMessageFieldInputTop(i) + fieldHeight;
            float progress = getTextFieldHighlightProgress(node.getId() + "#message:" + i, hovered, editingThis);
            int backgroundColor = isOverSidebar ? (editingThis ? activeFieldBackground : fieldBackground)
                : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
            int borderColor = isOverSidebar ? (editingThis ? activeFieldBorder : fieldBorder)
                : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
            int valueColor = isOverSidebar ? (editingThis ? activeTextColor : textColor)
                : AnimationHelper.lerpColor(textColor, activeTextColor, progress);

            context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
            DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

            String rawValue = editingThis ? messageEditBuffer : node.getMessageLine(i);
            if (rawValue == null) {
                rawValue = "";
            }
            String display = editingThis
                ? rawValue
                : trimTextToWidth(rawValue, textRenderer, fieldWidth - 6);
            InlineVariableRender renderData = null;
            if (!runtimeVariableNames.isEmpty() && rawValue.indexOf('~') >= 0) {
                InlineVariableRender candidate = buildInlineVariableRender(rawValue, runtimeVariableNames, valueColor, variableHighlightColor);
                if (editingThis) {
                    renderData = candidate;
                    display = renderData.displayText;
                } else if (textRenderer.getWidth(candidate.displayText) <= fieldWidth - 6) {
                    renderData = candidate;
                    display = renderData.displayText;
                } else if (isSingleKnownInlineVariableReference(rawValue, runtimeVariableNames)) {
                    display = trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                    valueColor = variableHighlightColor;
                }
            }

            int textX = fieldLeft + 3;
            int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
            if (editingThis && hasMessageSelection()) {
                int start = messageSelectionStart;
                int end = messageSelectionEnd;
                if (renderData != null) {
                    start = renderData.toDisplayIndex(start);
                    end = renderData.toDisplayIndex(end);
                }
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            if (renderData != null) {
                if (shouldRenderNodeText()) {
                    renderData.draw(context, textRenderer, textX, textY);
                }
            } else {
                drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);
            }

            if (editingThis && messageCaretVisible) {
                int caretIndex = messageCaretPosition;
                if (renderData != null) {
                    caretIndex = renderData.toDisplayIndex(caretIndex);
                }
                caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
                int caretBaseline = Math.min(textY + textRenderer.fontHeight - 1, fieldBottom - 2);
                UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, fieldLeft + fieldWidth - 2, UITheme.CARET_COLOR);
            }
        }
    }

    private void renderEventNamePreview(DrawContext context, TextRenderer textRenderer, String value, int x, int y,
                                        int baseColor, int maxWidth) {
        if (value == null || value.isEmpty()) {
            drawNodeText(context, textRenderer, Text.literal("enter name"), x, y, baseColor);
            return;
        }
        if (textRenderer.getWidth(value) <= maxWidth) {
            drawNodeText(context, textRenderer, Text.literal(value), x, y, baseColor);
            return;
        }

        String trimmed = trimTextToWidth(value, textRenderer, maxWidth);
        drawNodeText(context, textRenderer, Text.literal(trimmed), x, y, baseColor);
        int trimmedWidth = textRenderer.getWidth(trimmed);

        String tail = "..";
        int tailWidth = textRenderer.getWidth(tail);
        if (trimmedWidth + tailWidth + 4 >= maxWidth) {
            return;
        }

        String suffix = value.substring(Math.max(0, value.length() - 4));
        String tailText = tail + suffix;
        int tailTextWidth = textRenderer.getWidth(tailText);
        if (trimmedWidth + tailTextWidth + 4 > maxWidth) {
            return;
        }
        int tailX = x + maxWidth - tailTextWidth;
        int hintColor = toGrayscale(baseColor, 0.85f);
        drawNodeText(context, textRenderer, Text.literal(tailText), tailX, y, hintColor);
    }

    private InlineVariableRender buildInlineVariableRender(String rawText, Set<String> variableNames, int baseColor, int highlightColor) {
        if (rawText == null || rawText.isEmpty() || variableNames == null || variableNames.isEmpty()) {
            return new InlineVariableRender(rawText == null ? "" : rawText, Collections.emptyList(), new int[0]);
        }
        List<InlineTextSegment> segments = new ArrayList<>();
        List<Integer> removedPositions = new ArrayList<>();
        StringBuilder displayBuilder = new StringBuilder();
        int operatorColor = UITheme.DROP_ACCENT_GREEN;
        int cursor = 0;
        while (cursor < rawText.length()) {
            int tildeIndex = rawText.indexOf('~', cursor);
            if (tildeIndex < 0) {
                appendStyledPlainSegments(rawText.substring(cursor), baseColor, operatorColor, segments, displayBuilder);
                break;
            }
            if (tildeIndex > cursor) {
                appendStyledPlainSegments(rawText.substring(cursor, tildeIndex), baseColor, operatorColor, segments, displayBuilder);
            }
            VariableReferenceMatch match = findInlineVariableReference(rawText, tildeIndex, variableNames);
            if (match != null) {
                removedPositions.add(tildeIndex);
                segments.add(new InlineTextSegment(match.name, highlightColor));
                displayBuilder.append(match.name);
                cursor = match.endIndex;
                continue;
            }
            segments.add(new InlineTextSegment("~", baseColor));
            displayBuilder.append("~");
            cursor = tildeIndex + 1;
        }
        int[] removed = new int[removedPositions.size()];
        for (int i = 0; i < removedPositions.size(); i++) {
            removed[i] = removedPositions.get(i);
        }
        return new InlineVariableRender(displayBuilder.toString(), segments, removed);
    }

    private boolean isInlineVariableChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    private boolean isInlineArithmeticOperator(char character) {
        return character == '+' || character == '-' || character == '*' || character == '/' || character == '^';
    }

    private void appendStyledPlainSegments(String text, int baseColor, int operatorColor,
                                           List<InlineTextSegment> segments, StringBuilder displayBuilder) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (!isInlineArithmeticOperator(current)) {
                continue;
            }
            if (i > start) {
                String plain = text.substring(start, i);
                segments.add(new InlineTextSegment(plain, baseColor));
                displayBuilder.append(plain);
            }
            String operator = Character.toString(current);
            segments.add(new InlineTextSegment(operator, operatorColor));
            displayBuilder.append(operator);
            start = i + 1;
        }
        if (start < text.length()) {
            String tail = text.substring(start);
            segments.add(new InlineTextSegment(tail, baseColor));
            displayBuilder.append(tail);
        }
    }

    private VariableReferenceMatch findInlineVariableReference(String rawText, int tildeIndex, Set<String> variableNames) {
        if (rawText == null || tildeIndex < 0 || tildeIndex >= rawText.length() || rawText.charAt(tildeIndex) != '~'
            || variableNames == null || variableNames.isEmpty()) {
            return null;
        }
        int nameStart = tildeIndex + 1;
        if (nameStart >= rawText.length()) {
            return null;
        }
        VariableReferenceMatch bestMatch = null;
        for (String variableName : variableNames) {
            if (variableName == null || variableName.isEmpty()) {
                continue;
            }
            if (!rawText.regionMatches(nameStart, variableName, 0, variableName.length())) {
                continue;
            }
            int endIndex = nameStart + variableName.length();
            if (endIndex < rawText.length()) {
                char boundary = rawText.charAt(endIndex);
                if (!Character.isWhitespace(boundary) && !isInlineArithmeticOperator(boundary)) {
                    continue;
                }
            }
            if (bestMatch == null || variableName.length() > bestMatch.name.length()) {
                bestMatch = new VariableReferenceMatch(variableName, endIndex);
            }
        }
        return bestMatch;
    }

    private boolean isSingleKnownInlineVariableReference(String rawText, Set<String> variableNames) {
        if (rawText == null || variableNames == null || variableNames.isEmpty()) {
            return false;
        }
        String trimmed = rawText.trim();
        if (!trimmed.equals(rawText) || !trimmed.startsWith("~")) {
            return false;
        }
        VariableReferenceMatch match = findInlineVariableReference(trimmed, 0, variableNames);
        return match != null && match.endIndex == trimmed.length();
    }

    /** Returns true if value is empty or a valid arithmetic expression using numbers and/or known ~variable references. */
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
        if (value == null) {
            return false;
        }
        NumericExpressionValidator validator = new NumericExpressionValidator(value, variableNames, allowDecimal);
        if (!validator.parse()) {
            return false;
        }
        if (requireCoordinateValid && !validator.isCoordinateSafeIntegerExpression()) {
            return false;
        }
        return true;
    }

    private Set<String> collectRuntimeVariableNames(Node node) {
        Set<String> names = new HashSet<>();
        for (Node graphNode : nodes) {
            if (graphNode == null || graphNode.getType() != NodeType.VARIABLE) {
                continue;
            }
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
        ExecutionManager manager = ExecutionManager.getInstance();
        List<ExecutionManager.RuntimeVariableEntry> entries = manager.getRuntimeVariableEntries();
        if (!entries.isEmpty()) {
            Node startNode = node != null ? node.getOwningStartNode() : null;
            String startId = startNode != null ? startNode.getId() : null;
            for (ExecutionManager.RuntimeVariableEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                if (startId != null && !startId.equals(entry.getStartNodeId())) {
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
        return names;
    }

    private static final class InlineVariableRender {
        private final String displayText;
        private final List<InlineTextSegment> segments;
        private final int[] removedTildePositions;

        private InlineVariableRender(String displayText, List<InlineTextSegment> segments, int[] removedTildePositions) {
            this.displayText = displayText == null ? "" : displayText;
            this.segments = segments == null ? Collections.emptyList() : segments;
            this.removedTildePositions = removedTildePositions == null ? new int[0] : removedTildePositions;
        }

        private int toDisplayIndex(int rawIndex) {
            if (rawIndex <= 0) {
                return 0;
            }
            int removed = 0;
            for (int pos : removedTildePositions) {
                if (pos < rawIndex) {
                    removed++;
                } else {
                    break;
                }
            }
            return rawIndex - removed;
        }

        private void draw(DrawContext context, TextRenderer renderer, int x, int y) {
            int cursorX = x;
            for (InlineTextSegment segment : segments) {
                if (segment == null || segment.text == null || segment.text.isEmpty()) {
                    continue;
                }
                context.drawText(renderer, Text.literal(segment.text), cursorX, y, segment.color, false);
                cursorX += renderer.getWidth(segment.text);
            }
        }
    }

    private static final class InlineTextSegment {
        private final String text;
        private final int color;

        private InlineTextSegment(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }

    private static final class VariableReferenceMatch {
        private final String name;
        private final int endIndex;

        private VariableReferenceMatch(String name, int endIndex) {
            this.name = name;
            this.endIndex = endIndex;
        }
    }

    private static final class NumericExpressionValidator {
        private final String input;
        private final Set<String> variableNames;
        private final boolean allowDecimal;
        private int index;
        private boolean usesDecimal;

        private NumericExpressionValidator(String input, Set<String> variableNames, boolean allowDecimal) {
            this.input = input == null ? "" : input;
            this.variableNames = variableNames == null ? Collections.emptySet() : variableNames;
            this.allowDecimal = allowDecimal;
        }

        private boolean parse() {
            skipWhitespace();
            if (!parseExpression()) {
                return false;
            }
            skipWhitespace();
            return index == input.length();
        }

        private boolean isCoordinateSafeIntegerExpression() {
            return !usesDecimal;
        }

        private boolean parseExpression() {
            if (!parseTerm()) {
                return false;
            }
            while (true) {
                skipWhitespace();
                if (!consume('+') && !consume('-')) {
                    return true;
                }
                skipWhitespace();
                if (!parseTerm()) {
                    return false;
                }
            }
        }

        private boolean parseTerm() {
            if (!parsePower()) {
                return false;
            }
            while (true) {
                skipWhitespace();
                if (!consume('*') && !consume('/')) {
                    return true;
                }
                skipWhitespace();
                if (!parsePower()) {
                    return false;
                }
            }
        }

        private boolean parsePower() {
            if (!parseFactor()) {
                return false;
            }
            skipWhitespace();
            if (!consume('^')) {
                return true;
            }
            skipWhitespace();
            return parsePower();
        }

        private boolean parseFactor() {
            skipWhitespace();
            if (consume('+')) {
                skipWhitespace();
                return parseFactor();
            }
            if (peekIsNegativeNumberStart()) {
                index++;
                skipWhitespace();
                return parseNumber(true);
            }
            if (peek() == '~') {
                VariableReferenceMatch match = matchVariableAt(index);
                if (match == null) {
                    return false;
                }
                index = match.endIndex;
                return true;
            }
            return parseNumber(false);
        }

        private boolean parseNumber(boolean negative) {
            int start = index;
            boolean sawDigit = false;
            boolean sawDecimal = false;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isDigit(current)) {
                    sawDigit = true;
                    index++;
                    continue;
                }
                if (current == '.') {
                    if (!allowDecimal || sawDecimal) {
                        break;
                    }
                    sawDecimal = true;
                    usesDecimal = true;
                    index++;
                    continue;
                }
                break;
            }
            if (!sawDigit) {
                index = start;
                return false;
            }
            if (negative) {
                usesDecimal |= sawDecimal;
            }
            return true;
        }

        private VariableReferenceMatch matchVariableAt(int tildeIndex) {
            if (tildeIndex < 0 || tildeIndex >= input.length() || input.charAt(tildeIndex) != '~') {
                return null;
            }
            int nameStart = tildeIndex + 1;
            VariableReferenceMatch bestMatch = null;
            for (String variableName : variableNames) {
                if (variableName == null || variableName.isEmpty()) {
                    continue;
                }
                if (!input.regionMatches(nameStart, variableName, 0, variableName.length())) {
                    continue;
                }
                int endIndex = nameStart + variableName.length();
                if (endIndex < input.length()) {
                    char boundary = input.charAt(endIndex);
                    if (!Character.isWhitespace(boundary) && !isOperator(boundary)) {
                        continue;
                    }
                }
                if (bestMatch == null || variableName.length() > bestMatch.name.length()) {
                    bestMatch = new VariableReferenceMatch(variableName, endIndex);
                }
            }
            return bestMatch;
        }

        private boolean peekIsNegativeNumberStart() {
            if (peek() != '-') {
                return false;
            }
            int lookahead = index + 1;
            while (lookahead < input.length() && Character.isWhitespace(input.charAt(lookahead))) {
                lookahead++;
            }
            if (lookahead >= input.length()) {
                return false;
            }
            char next = input.charAt(lookahead);
            return Character.isDigit(next) || (allowDecimal && next == '.');
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (peek() != expected) {
                return false;
            }
            index++;
            return true;
        }

        private char peek() {
            return index < input.length() ? input.charAt(index) : '\0';
        }

        private boolean isOperator(char character) {
            return character == '+' || character == '-' || character == '*' || character == '/' || character == '^';
        }
    }

    private void updateMessageFieldContentWidth(TextRenderer textRenderer) {
        if (!isEditingMessageField() || messageEditingNode == null || textRenderer == null) {
            return;
        }
        int maxWidth = 0;
        int fieldCount = messageEditingNode.getMessageFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            String line = i == messageEditingIndex ? messageEditBuffer : messageEditingNode.getMessageLine(i);
            if (line == null) {
                line = "";
            }
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
        }
        messageEditingNode.setMessageFieldTextWidth(maxWidth);
        messageEditingNode.recalculateDimensions();
    }

    private void updateCoordinateFieldContentWidth(TextRenderer textRenderer) {
        if (!isEditingCoordinateField() || coordinateEditingNode == null || textRenderer == null) {
            return;
        }
        int maxWidth = 0;
        String[] axes = getCoordinateAxes(coordinateEditingNode);
        for (int i = 0; i < axes.length; i++) {
            NodeParameter parameter = getCoordinateParameter(coordinateEditingNode, i);
            String value = i == coordinateEditingAxis ? coordinateEditBuffer : (parameter != null ? parameter.getStringValue() : "");
            if (value == null) {
                value = "";
            }
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(value));
        }
        coordinateEditingNode.setCoordinateFieldTextWidth(maxWidth);
        coordinateEditingNode.recalculateDimensions();
    }

    private void updateAmountFieldContentWidth(TextRenderer textRenderer) {
        if (!isEditingAmountField() || amountEditingNode == null || textRenderer == null) {
            return;
        }
        String value = amountEditBuffer == null ? "" : amountEditBuffer;
        amountEditingNode.setAmountFieldTextWidth(textRenderer.getWidth(value));
        amountEditingNode.recalculateDimensions();
    }

    private void updateStopTargetFieldContentWidth(TextRenderer textRenderer) {
        if (!isEditingStopTargetField() || stopTargetEditingNode == null || textRenderer == null) {
            return;
        }
        String value = stopTargetEditBuffer == null ? "" : stopTargetEditBuffer;
        stopTargetEditingNode.setStopTargetFieldTextWidth(textRenderer.getWidth(value));
        stopTargetEditingNode.recalculateDimensions();
    }

    private void updateVariableFieldContentWidth(TextRenderer textRenderer) {
        if (!isEditingVariableField() || variableEditingNode == null || textRenderer == null) {
            return;
        }
        String value = variableEditBuffer == null ? "" : variableEditBuffer;
        variableEditingNode.setVariableFieldTextWidth(textRenderer.getWidth(value));
        variableEditingNode.recalculateDimensions();
    }

    private void updateParameterFieldContentWidth(Node node, TextRenderer textRenderer, int editingIndex, String editingValue) {
        if (node == null || !rendersInlineParameters(node) || textRenderer == null) {
            return;
        }
        int requiredFieldWidth = 0;
        if (node.supportsModeSelection()) {
            String modeLabel = node.getModeDisplayLabel();
            if (modeLabel != null && !modeLabel.isEmpty()) {
                requiredFieldWidth = Math.max(requiredFieldWidth, textRenderer.getWidth(modeLabel));
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
                value = formatVillagerTradeValue(value);
            }
            int labelWidth = textRenderer.getWidth(label);
            int valueWidth = textRenderer.getWidth(value);
            int fieldWidth = labelWidth + valueWidth + 12;
            requiredFieldWidth = Math.max(requiredFieldWidth, fieldWidth);
        }
        node.setParameterFieldWidthOverride(requiredFieldWidth);
        node.recalculateDimensions();
    }

    private void renderMessageButtons(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int size = node.getMessageButtonSize();
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int worldTop = node.getMessageButtonTop();
        int worldAddLeft = node.getMessageAddButtonLeft();
        int worldRemoveLeft = node.getMessageRemoveButtonLeft();
        int top = worldTop - cameraY;
        int addLeft = worldAddLeft - cameraX;
        int removeLeft = worldRemoveLeft - cameraX;

        boolean canRemove = node.getMessageFieldCount() > 1;
        boolean addHovered = worldMouseX >= worldAddLeft && worldMouseX <= worldAddLeft + size
            && worldMouseY >= worldTop && worldMouseY <= worldTop + size;
        boolean removeHovered = worldMouseX >= worldRemoveLeft && worldMouseX <= worldRemoveLeft + size
            && worldMouseY >= worldTop && worldMouseY <= worldTop + size && canRemove;

        int baseFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_PRIMARY;
        int baseBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;

        // Add button
        int addFill = addHovered ? adjustColorBrightness(baseFill, 1.15f) : baseFill;
        int addBorder = addHovered ? getSelectedNodeAccentColor() : baseBorder;
        context.fill(addLeft, top, addLeft + size, top + size, addFill);
        DrawContextBridge.drawBorderInLayer(context, addLeft, top, size, size, addBorder);
        int addTextX = addLeft + (size - textRenderer.getWidth("+")) / 2;
        int addTextY = top + (size - textRenderer.fontHeight) / 2 + 1;
        drawNodeText(context, textRenderer, Text.literal("+"), addTextX, addTextY, UITheme.TEXT_PRIMARY);

        // Remove button
        int removeFill = canRemove
            ? (removeHovered ? adjustColorBrightness(baseFill, 1.15f) : baseFill)
            : UITheme.BACKGROUND_PRIMARY;
        int removeBorder = canRemove
            ? (removeHovered ? UITheme.BORDER_DANGER : baseBorder)
            : UITheme.BORDER_DEFAULT;
        context.fill(removeLeft, top, removeLeft + size, top + size, removeFill);
        DrawContextBridge.drawBorderInLayer(context, removeLeft, top, size, size, removeBorder);
        int removeTextX = removeLeft + (size - textRenderer.getWidth("-")) / 2;
        int removeTextY = top + (size - textRenderer.fontHeight) / 2 + 1;
        int removeTextColor = canRemove ? UITheme.TEXT_PRIMARY : UITheme.NODE_LABEL_DIMMED;
        drawNodeText(context, textRenderer, Text.literal("-"), removeTextX, removeTextY, removeTextColor);
    }

    private void renderMessageScopeToggle(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int borderColor = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int accentColor = getSelectedNodeAccentColor();
        int activeBackground = isOverSidebar ? adjustColorBrightness(accentColor, 0.72f) : adjustColorBrightness(accentColor, 0.84f);
        int activeBorderColor = accentColor;
        int activeTextColor = UITheme.TEXT_EDITING;
        int inactiveTextColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int optionGap = 4;

        int labelLeft = node.getMessageScopeToggleLeft() - cameraX;
        int labelTop = node.getMessageScopeLabelTop() - cameraY;
        int labelY = labelTop + Math.max(0, (node.getMessageScopeLabelHeight() - textRenderer.fontHeight) / 2);
        drawNodeText(context, textRenderer, Text.literal("Visibility"), labelLeft + 2, labelY, labelColor);

        int left = node.getMessageScopeToggleLeft() - cameraX;
        int top = node.getMessageScopeToggleTop() - cameraY;
        int width = node.getMessageScopeToggleWidth();
        int height = node.getMessageScopeToggleHeight();
        int segmentWidth = Math.max(1, (width - optionGap) / 2);
        boolean hovered = !isOverSidebar && isPointInsideMessageScopeToggle(node, mouseX, mouseY);
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int worldLeft = node.getMessageScopeToggleLeft();
        int worldTop = node.getMessageScopeToggleTop();
        AnimatedValue animation = getMessageScopeAnimation(node);
        float target = node.isMessageClientSide() ? 1f : 0f;
        if (Math.abs(animation.getTargetValue() - target) > 0.001f) {
            animation.setValue(target);
        }
        animation.tick();
        float progress = animation.getValue();

        int globalLeft = left;
        int clientLeft = left + segmentWidth + optionGap;
        int globalWorldLeft = worldLeft;
        int clientWorldLeft = worldLeft + segmentWidth + optionGap;
        boolean globalHovered = hovered
            && worldMouseX >= globalWorldLeft && worldMouseX <= globalWorldLeft + segmentWidth
            && worldMouseY >= worldTop && worldMouseY <= worldTop + height;
        boolean clientHovered = hovered
            && worldMouseX >= clientWorldLeft && worldMouseX <= clientWorldLeft + segmentWidth
            && worldMouseY >= worldTop && worldMouseY <= worldTop + height;

        int globalFill = AnimationHelper.lerpColor(activeBackground, fieldBackground, progress);
        int globalBorder = AnimationHelper.lerpColor(activeBorderColor, borderColor, progress);
        int clientFill = AnimationHelper.lerpColor(fieldBackground, activeBackground, progress);
        int clientBorder = AnimationHelper.lerpColor(borderColor, activeBorderColor, progress);
        if (globalHovered) {
            globalFill = adjustColorBrightness(globalFill, 1.08f);
            globalBorder = activeBorderColor;
        }
        if (clientHovered) {
            clientFill = adjustColorBrightness(clientFill, 1.08f);
            clientBorder = activeBorderColor;
        }

        context.fill(globalLeft, top, globalLeft + segmentWidth, top + height, globalFill);
        DrawContextBridge.drawBorderInLayer(context, globalLeft, top, segmentWidth, height, globalBorder);
        context.fill(clientLeft, top, clientLeft + segmentWidth, top + height, clientFill);
        DrawContextBridge.drawBorderInLayer(context, clientLeft, top, segmentWidth, height, clientBorder);

        String globalLabel = "Global";
        String clientLabel = "Client Side";
        int globalX = globalLeft + Math.max(0, (segmentWidth - textRenderer.getWidth(globalLabel)) / 2);
        int clientX = clientLeft + Math.max(0, (segmentWidth - textRenderer.getWidth(clientLabel)) / 2);
        int textY = top + (height - textRenderer.fontHeight) / 2 + 1;
        int globalTextColor = AnimationHelper.lerpColor(activeTextColor, inactiveTextColor, progress);
        int clientTextColor = AnimationHelper.lerpColor(inactiveTextColor, activeTextColor, progress);

        drawNodeText(context, textRenderer, Text.literal(globalLabel), globalX, textY, globalTextColor);
        drawNodeText(context, textRenderer, Text.literal(clientLabel), clientX, textY, clientTextColor);
    }

    private void renderBookTextInput(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        // Render "Edit Text" button
        int buttonLeft = node.getBookTextButtonLeft() - cameraX;
        int buttonTop = node.getBookTextButtonTop() - cameraY;
        int buttonWidth = node.getBookTextButtonWidth();
        int buttonHeight = node.getBookTextButtonHeight();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean buttonHovered = !isOverSidebar &&
            worldMouseX >= node.getBookTextButtonLeft() && worldMouseX <= node.getBookTextButtonLeft() + buttonWidth &&
            worldMouseY >= node.getBookTextButtonTop() && worldMouseY <= node.getBookTextButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (buttonHovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = "Edit Text";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.getWidth(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(buttonLabel), textX, textY, textColor);

        if (node.hasBookTextPageInput()) {
            int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
            int labelTop = node.getBookTextPageLabelTop() - cameraY;
            drawNodeText(context, textRenderer, Text.literal("Page #:"), buttonLeft, labelTop, labelColor);

            int fieldTop = node.getBookTextPageFieldTop() - cameraY;
            int fieldHeight = node.getBookTextPageFieldHeight();

            NodeParameter pageParam = node.getParameter("Page");
            String pageValue = pageParam != null ? pageParam.getDisplayValue() : "1";
            if (pageValue == null) {
                pageValue = "";
            }
            int pageTextColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            drawNodeText(context, textRenderer, Text.literal(pageValue), buttonLeft + 4, fieldTop + (fieldHeight - textRenderer.fontHeight) / 2, pageTextColor);
        }
    }

    private void renderPopupEditButton(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (node == null || !node.hasPopupEditButton()) {
            return;
        }
        int buttonLeft = node.getPopupEditButtonLeft() - cameraX;
        int buttonTop = node.getPopupEditButtonTop() - cameraY;
        int buttonWidth = node.getPopupEditButtonWidth();
        int buttonHeight = node.getPopupEditButtonHeight();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean buttonHovered = !isOverSidebar &&
            worldMouseX >= node.getPopupEditButtonLeft() && worldMouseX <= node.getPopupEditButtonLeft() + buttonWidth &&
            worldMouseY >= node.getPopupEditButtonTop() && worldMouseY <= node.getPopupEditButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (buttonHovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = "Edit";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.getWidth(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(buttonLabel), textX, textY, textColor);
    }

    private void renderTemplateNodeContent(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        int x = node.getX() - cameraX;
        int y = node.getY() - cameraY;
        int width = node.getWidth();

        int headerColor = node.getType().getColor() & UITheme.NODE_HEADER_ALPHA_MASK;
        if (isOverSidebar) {
            headerColor = UITheme.NODE_HEADER_DIMMED;
        }
        context.fill(x + 1, y + 1, x + width - 1, y + 14, headerColor);

        int bodyColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
        context.fill(x + 1, y + 15, x + width - 1, y + node.getHeight() - 1, bodyColor);

        NodeGraphData.CustomNodeDefinition definition = getTemplateDefinition(node);
        String headerName = definition != null && definition.getName() != null && !definition.getName().isBlank()
            ? definition.getName().trim()
            : "Custom Node";
        String versionLabel = definition != null && definition.getVersion() != null && definition.getVersion() > 0
            ? " v" + definition.getVersion()
            : "";
        boolean lockedCustomNode = node.isCustomNodeInstance();
        String badge = lockedCustomNode ? "LOCKED" : "LINK";
        int badgeWidth = textRenderer.getWidth(badge) + 8;
        int badgeLeft = x + width - badgeWidth - 6;
        int badgeTop = y + 2;
        int badgeFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : adjustColorBrightness(node.getType().getColor(), 0.78f);
        int badgeBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : adjustColorBrightness(node.getType().getColor(), 1.18f);
        context.fill(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + 11, badgeFill);
        DrawContextBridge.drawBorderInLayer(context, badgeLeft, badgeTop, badgeWidth, 11, badgeBorder);
        drawNodeText(context, textRenderer, badge, badgeLeft + 4, badgeTop + 2, isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);

        String name = trimTextToWidth(headerName + versionLabel, textRenderer, Math.max(0, width - badgeWidth - 20));
        drawNodeText(context, textRenderer, name, x + 6, y + 4, isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);

        if (lockedCustomNode) {
            renderLockedCustomNodeBinding(context, textRenderer, node, isOverSidebar);
        } else {
            renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }

        int previewLeft = getTemplatePreviewLeft(node) - cameraX;
        int previewTop = getTemplatePreviewTop(node) - cameraY;
        int previewWidth = getTemplatePreviewWidth(node);
        int previewHeight = getTemplatePreviewHeight(node);
        context.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight,
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, previewLeft, previewTop, previewWidth, previewHeight,
            isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT);

        renderTemplatePreviewGraph(context, textRenderer, node, previewLeft, previewTop, previewWidth, previewHeight, isOverSidebar);
        if (!lockedCustomNode) {
            renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    }

    private void renderCustomNodeContent(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int x = node.getX() - cameraX;
        int y = node.getY() - cameraY;
        int width = node.getWidth();
        int height = node.getHeight();
        int accent = node.getType().getColor();

        int headerColor = isOverSidebar ? UITheme.NODE_HEADER_DIMMED : (accent & UITheme.NODE_HEADER_ALPHA_MASK);
        int bodyColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : adjustColorBrightness(accent, 0.2f);
        int panelColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY;
        int chipFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : adjustColorBrightness(accent, 0.55f);
        int chipBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : adjustColorBrightness(accent, 1.15f);
        int primaryText = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int secondaryText = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;

        context.fill(x + 1, y + 1, x + width - 1, y + 18, headerColor);
        context.fill(x + 1, y + 19, x + width - 1, y + height - 1, panelColor);

        NodeGraphData.CustomNodeDefinition definition = getTemplateDefinition(node);
        String title = definition != null && definition.getName() != null && !definition.getName().isBlank()
            ? definition.getName().trim()
            : node.getTemplateName();
        String version = definition != null && definition.getVersion() != null && definition.getVersion() > 0
            ? "v" + definition.getVersion()
            : "";
        drawNodeText(context, textRenderer, trimTextToWidth(title, textRenderer, width - 48), x + 6, y + 4, primaryText);
        if (!version.isEmpty()) {
            int versionWidth = textRenderer.getWidth(version) + 8;
            int versionLeft = x + width - versionWidth - 6;
            context.fill(versionLeft, y + 3, versionLeft + versionWidth, y + 14, chipFill);
            DrawContextBridge.drawBorderInLayer(context, versionLeft, y + 3, versionWidth, 11, chipBorder);
            drawNodeText(context, textRenderer, version, versionLeft + 4, y + 5, primaryText);
        }

        int bindingLeft = x + 6;
        int bindingTop = y + 24;
        int bindingWidth = width - 12;
        context.fill(bindingLeft, bindingTop, bindingLeft + bindingWidth, bindingTop + 18, bodyColor);
        DrawContextBridge.drawBorderInLayer(context, bindingLeft, bindingTop, bindingWidth, 18, chipBorder);
        drawNodeText(context, textRenderer, "Preset", bindingLeft + 4, bindingTop + 5, secondaryText);
        String presetName = trimTextToWidth(getSelectedPresetName(node), textRenderer, Math.max(24, bindingWidth - 48));
        drawNodeText(context, textRenderer, presetName, bindingLeft + 42, bindingTop + 5, primaryText);

        int previewLeft = getTemplatePreviewLeft(node) - cameraX;
        int previewTop = y + 48;
        int previewWidth = getTemplatePreviewWidth(node);
        int previewHeight = Math.max(18, y + height - TEMPLATE_PREVIEW_BOTTOM_MARGIN - previewTop);
        context.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight,
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, previewLeft, previewTop, previewWidth, previewHeight,
            isOverSidebar ? UITheme.BORDER_SUBTLE : chipBorder);
        renderTemplatePreviewGraph(context, textRenderer, node, previewLeft, previewTop, previewWidth, previewHeight, isOverSidebar);
    }

    private void renderLockedCustomNodeBinding(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int fieldTop = node.getStopTargetFieldInputTop() - cameraY;
        int fieldLeft = node.getStopTargetFieldLeft() - cameraX;
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldBottom = fieldTop + fieldHeight;

        int labelY = fieldTop - textRenderer.fontHeight - 2;
        if (labelY >= node.getY() - cameraY + 14) {
            drawNodeText(context, textRenderer, Text.literal("Custom Node"), fieldLeft, labelY,
                isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR);
        }

        int fill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : adjustColorBrightness(node.getType().getColor(), 0.55f);
        int border = isOverSidebar ? UITheme.BORDER_SUBTLE : adjustColorBrightness(node.getType().getColor(), 1.12f);
        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, fill);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, border);

        String presetLine = trimTextToWidth(getSelectedPresetName(node), textRenderer, Math.max(0, fieldWidth - 34));
        drawNodeText(context, textRenderer, Text.literal(presetLine), fieldLeft + 4, fieldTop + 4,
            isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);

        String lockGlyph = "[]";
        int lockX = fieldLeft + fieldWidth - textRenderer.getWidth(lockGlyph) - 5;
        drawNodeText(context, textRenderer, Text.literal(lockGlyph), lockX, fieldTop + 4,
            isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY);
    }

    private void renderTemplatePreviewGraph(DrawContext context, TextRenderer textRenderer, Node node,
                                            int previewLeft, int previewTop, int previewWidth, int previewHeight,
                                            boolean isOverSidebar) {
        NodeGraphData templateData = getPresetPreviewGraphData(node);
        NodeGraphData.CustomNodeDefinition definition = templateData != null ? NodeGraphPersistence.resolveCustomNodeDefinition(getSelectedPresetName(node), templateData) : null;
        if (definition != null) {
            renderTemplateDefinitionSummary(context, textRenderer, node, definition, previewLeft, previewTop, previewWidth, previewHeight, isOverSidebar);
            return;
        }
        if (templateData == null || templateData.getNodes() == null || templateData.getNodes().isEmpty()) {
            return;
        }
        List<NodeGraphData.NodeData> previewNodes = templateData.getNodes();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeGraphData.NodeData nd : previewNodes) {
            if (nd == null) {
                continue;
            }
            minX = Math.min(minX, nd.getX());
            minY = Math.min(minY, nd.getY());
            maxX = Math.max(maxX, nd.getX());
            maxY = Math.max(maxY, nd.getY());
        }
        if (minX == Integer.MAX_VALUE) {
            return;
        }

        float padding = 6f;
        float spanX = Math.max(1f, (maxX - minX) + 20f);
        float spanY = Math.max(1f, (maxY - minY) + 20f);
        float sx = Math.max(0.1f, (previewWidth - padding * 2f) / spanX);
        float sy = Math.max(0.1f, (previewHeight - padding * 2f) / spanY);
        float scale = Math.min(sx, sy);
        float offsetX = previewLeft + (previewWidth - spanX * scale) / 2f + 10f * scale;
        float offsetY = previewTop + (previewHeight - spanY * scale) / 2f + 10f * scale;

        Map<String, float[]> centers = new HashMap<>();
        for (int i = 0; i < previewNodes.size(); i++) {
            NodeGraphData.NodeData nd = previewNodes.get(i);
            if (nd == null) {
                continue;
            }
            float cx = offsetX + (nd.getX() - minX) * scale;
            float cy = offsetY + (nd.getY() - minY) * scale;
            String nodeId = nd.getId();
            if (nodeId == null || nodeId.isBlank()) {
                nodeId = "__preview_node_" + i;
            }
            centers.put(nodeId, new float[]{cx, cy});
        }

        int lineColor = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT;
        if (templateData.getConnections() != null) {
            for (NodeGraphData.ConnectionData cd : templateData.getConnections()) {
                if (cd == null) {
                    continue;
                }
                float[] a = centers.get(cd.getOutputNodeId());
                float[] b = centers.get(cd.getInputNodeId());
                if (a == null || b == null) {
                    continue;
                }
                int x1 = Math.round(a[0]);
                int y1 = Math.round(a[1]);
                int x2 = Math.round(b[0]);
                int y2 = Math.round(b[1]);
                int mx = (x1 + x2) / 2;
                context.drawHorizontalLine(Math.min(x1, mx), Math.max(x1, mx), y1, lineColor);
                context.drawVerticalLine(mx, Math.min(y1, y2), Math.max(y1, y2), lineColor);
                context.drawHorizontalLine(Math.min(mx, x2), Math.max(mx, x2), y2, lineColor);
            }
        }

        for (int i = 0; i < previewNodes.size(); i++) {
            NodeGraphData.NodeData nd = previewNodes.get(i);
            if (nd == null) {
                continue;
            }
            String nodeId = nd.getId();
            if (nodeId == null || nodeId.isBlank()) {
                nodeId = "__preview_node_" + i;
            }
            float[] c = centers.get(nodeId);
            if (c == null) {
                continue;
            }
            int cx = Math.round(c[0]);
            int cy = Math.round(c[1]);
            int color = nd.getType() == NodeType.START ? UITheme.NODE_START_BG : (nd.getType() != null ? nd.getType().getColor() : UITheme.TEXT_TERTIARY);
            if (isOverSidebar) {
                color = UITheme.BORDER_SUBTLE;
            }
            context.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        }
    }

    private void renderTemplateDefinitionSummary(DrawContext context, TextRenderer textRenderer, Node node,
                                                 NodeGraphData.CustomNodeDefinition definition, int previewLeft,
                                                 int previewTop, int previewWidth, int previewHeight, boolean isOverSidebar) {
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int mutedColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        int warningColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.ACCENT_AMBER;
        int textX = previewLeft + 5;
        int lineY = previewTop + 4;
        int lineStep = textRenderer.fontHeight + 2;

        String presetLine = "Preset: " + trimTextToWidth(getSelectedPresetName(node), textRenderer, Math.max(0, previewWidth - 10 - textRenderer.getWidth("Preset: ")));
        drawNodeText(context, textRenderer, presetLine, textX, lineY, mutedColor);
        lineY += lineStep;

        int instanceVersion = node.getTemplateVersion();
        int definitionVersion = definition.getVersion() != null ? definition.getVersion() : 0;
        String versionLine = "Version: " + (definitionVersion > 0 ? ("v" + definitionVersion) : "unversioned");
        if (instanceVersion > 0 && definitionVersion > instanceVersion) {
            versionLine += " (instance v" + instanceVersion + ")";
            drawNodeText(context, textRenderer, trimTextToWidth(versionLine, textRenderer, previewWidth - 10), textX, lineY, warningColor);
        } else {
            drawNodeText(context, textRenderer, trimTextToWidth(versionLine, textRenderer, previewWidth - 10), textX, lineY, textColor);
        }
        lineY += lineStep;

        drawNodeText(context, textRenderer, "Inputs", textX, lineY, mutedColor);
        lineY += lineStep;
        lineY = renderPortList(context, textRenderer, definition.getInputs(), textX, lineY, previewWidth, previewTop + previewHeight, textColor, mutedColor);
        if (lineY + lineStep < previewTop + previewHeight - 2) {
            drawNodeText(context, textRenderer, "Outputs", textX, lineY, mutedColor);
            lineY += lineStep;
            renderPortList(context, textRenderer, definition.getOutputs(), textX, lineY, previewWidth, previewTop + previewHeight, textColor, mutedColor);
        }
    }

    private int renderPortList(DrawContext context, TextRenderer textRenderer, List<NodeGraphData.CustomNodePort> ports,
                               int textX, int lineY, int previewWidth, int previewBottom, int textColor, int mutedColor) {
        if (ports == null || ports.isEmpty()) {
            drawNodeText(context, textRenderer, "none", textX, lineY, mutedColor);
            return lineY + textRenderer.fontHeight + 2;
        }
        for (NodeGraphData.CustomNodePort port : ports) {
            if (lineY + textRenderer.fontHeight > previewBottom - 2) {
                break;
            }
            String label = buildPortSummary(port);
            drawNodeText(context, textRenderer, trimTextToWidth(label, textRenderer, previewWidth - 10), textX, lineY, textColor);
            lineY += textRenderer.fontHeight + 2;
        }
        return lineY;
    }

    private String buildPortSummary(NodeGraphData.CustomNodePort port) {
        if (port == null) {
            return "";
        }
        String name = port.getName() == null || port.getName().isBlank() ? "unnamed" : port.getName().trim();
        String type = port.getType() == null || port.getType().isBlank() ? "" : " [" + port.getType().trim() + "]";
        String defaultValue = port.getDefaultValue() == null || port.getDefaultValue().isBlank() ? "" : " = " + port.getDefaultValue().trim();
        return name + type + defaultValue;
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
        if (node == null || (node.getType() != NodeType.TEMPLATE && node.getType() != NodeType.CUSTOM_NODE)) {
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

    private int getTemplatePreviewLeft(Node node) {
        return node.getX() + TEMPLATE_PREVIEW_MARGIN;
    }

    private int getTemplatePreviewTop(Node node) {
        return node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 4;
    }

    private int getTemplatePreviewWidth(Node node) {
        return Math.max(20, node.getWidth() - TEMPLATE_PREVIEW_MARGIN * 2);
    }

    private int getTemplatePreviewHeight(Node node) {
        return Math.max(18, node.getY() + node.getHeight() - TEMPLATE_PREVIEW_BOTTOM_MARGIN - getTemplatePreviewTop(node));
    }

    public boolean isPointInsideBookTextButton(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasBookTextInput()) {
            return false;
        }
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        int buttonLeft = node.getBookTextButtonLeft();
        int buttonTop = node.getBookTextButtonTop();
        int buttonWidth = node.getBookTextButtonWidth();
        int buttonHeight = node.getBookTextButtonHeight();

        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth &&
               worldY >= buttonTop && worldY <= buttonTop + buttonHeight;
    }

    public boolean isPointInsidePopupEditButton(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasPopupEditButton()) {
            return false;
        }
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        int buttonLeft = node.getPopupEditButtonLeft();
        int buttonTop = node.getPopupEditButtonTop();
        int buttonWidth = node.getPopupEditButtonWidth();
        int buttonHeight = node.getPopupEditButtonHeight();

        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth &&
               worldY >= buttonTop && worldY <= buttonTop + buttonHeight;
    }

    public boolean isPointInsideTemplateEditButton(Node node, int mouseX, int mouseY) {
        return false;
    }

    private void renderStopTargetInputField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                            int mouseX, int mouseY) {
        boolean isRunPresetNode = isPresetSelectorNode(node);
        if (isRunPresetNode) {
            renderPresetSelectorField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            return;
        }
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
        int fieldBorder = isActivateNode
            ? (isOverSidebar ? UITheme.BORDER_FOCUS : UITheme.BORDER_HIGHLIGHT)
            : (isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT);
        int activeFieldBorder = isActivateNode ? UITheme.TEXT_TERTIARY : UITheme.BORDER_HIGHLIGHT;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_LABEL;
        int activeTextColor = UITheme.TEXT_LABEL;
        int caretColor = UITheme.TEXT_LABEL;
        boolean presetDropdownOpenForNode = isRunPresetNode && runPresetDropdownOpen && runPresetDropdownNode == node;
        if (isRunPresetNode) {
            fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
            activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
            fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
            activeFieldBorder = getSelectedNodeAccentColor();
            textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            activeTextColor = UITheme.TEXT_PRIMARY;
            caretColor = UITheme.TEXT_PRIMARY;
        }

        boolean editing = isEditingStopTargetField() && stopTargetEditingNode == node;
        if (editing) {
            updateStopTargetCaretBlink();
        }

        int fieldTop = node.getStopTargetFieldInputTop() - cameraY;
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldLeft = node.getStopTargetFieldLeft() - cameraX;
        int fieldWidth = node.getStopTargetFieldWidth();

        int fieldBottom = fieldTop + fieldHeight;
        boolean activeVisual = isRunPresetNode ? presetDropdownOpenForNode : editing;
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getStopTargetFieldLeft()
            && worldMouseX <= node.getStopTargetFieldLeft() + fieldWidth
            && worldMouseY >= node.getStopTargetFieldInputTop()
            && worldMouseY <= node.getStopTargetFieldInputTop() + fieldHeight;
        float progress = getTextFieldHighlightProgress(node.getId() + "#stopTarget", hovered, activeVisual);
        int backgroundColor = isOverSidebar ? (activeVisual ? activeFieldBackground : fieldBackground)
            : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
        int borderColor = isOverSidebar ? (activeVisual ? activeFieldBorder : fieldBorder)
            : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
        int valueColor = isOverSidebar ? (editing ? activeTextColor : textColor)
            : AnimationHelper.lerpColor(textColor, activeTextColor, progress);

        if (isRunPresetNode) {
            int labelY = fieldTop - textRenderer.fontHeight - 2;
            if (labelY >= node.getY() - cameraY + 14) {
                drawNodeText(context, textRenderer, Text.literal("Preset"), fieldLeft, labelY, baseLabelColor);
            }
        }

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value;
        if (editing) {
            value = stopTargetEditBuffer;
        } else {
            NodeParameter targetParam = node.getParameter(getStopTargetParameterKey(node));
            value = targetParam != null ? targetParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display;
        if (!editing && value.isEmpty()) {
            display = getStopTargetPlaceholder(node);
            valueColor = UITheme.TEXT_TERTIARY;
        } else {
            display = value;
        }

        int reservedRightPadding = isRunPresetNode ? 16 : 6;
        display = editing
            ? display
            : trimTextToWidth(display, textRenderer, fieldWidth - reservedRightPadding);
        int variableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
        Set<String> stopTargetVariableNames = collectRuntimeVariableNames(node);
        InlineVariableRender stopTargetRenderData = null;
        if (!stopTargetVariableNames.isEmpty() && value.indexOf('~') >= 0) {
            InlineVariableRender candidate = buildInlineVariableRender(value, stopTargetVariableNames, valueColor, variableHighlightColor);
            if (editing) {
                stopTargetRenderData = candidate;
                display = stopTargetRenderData.displayText;
            } else if (textRenderer.getWidth(candidate.displayText) <= fieldWidth - 6) {
                stopTargetRenderData = candidate;
                display = stopTargetRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, stopTargetVariableNames)) {
                display = trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && hasStopTargetSelection()) {
            int start = stopTargetSelectionStart;
            int end = stopTargetSelectionEnd;
            if (stopTargetRenderData != null) {
                start = stopTargetRenderData.toDisplayIndex(start);
                end = stopTargetRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                int selectionColor = isRunPresetNode ? UITheme.TEXT_SELECTION_BG : UITheme.TEXT_SELECTION_DANGER_BG;
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, selectionColor);
            }
        }
        if (stopTargetRenderData != null && shouldRenderNodeText()) {
            stopTargetRenderData.draw(context, textRenderer, textX, textY);
        } else {
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);
        }

        if (editing && stopTargetCaretVisible) {
            int caretIndex = stopTargetCaretPosition;
            if (stopTargetRenderData != null) {
                caretIndex = stopTargetRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }

        if (isRunPresetNode) {
            int arrowX = fieldLeft + fieldWidth - 10;
            int arrowY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
            String arrow = presetDropdownOpenForNode ? ">" : "v";
            int arrowColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
            drawNodeText(context, textRenderer, Text.literal(arrow), arrowX, arrowY, arrowColor);
        }
    }

    private void renderPresetSelectorField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                           int mouseX, int mouseY) {
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int caretColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.CARET_COLOR;

        boolean editing = isEditingStopTargetField() && stopTargetEditingNode == node;
        if (editing) {
            updateStopTargetCaretBlink();
        }
        boolean open = runPresetDropdownOpen && runPresetDropdownNode == node;

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
        int animatedFieldBackground = isOverSidebar
            ? fieldBackground
            : AnimationHelper.lerpColor(fieldBackground, UITheme.BACKGROUND_TERTIARY, hoverProgress);
        int animatedFieldBorder = isOverSidebar
            ? fieldBorder
            : AnimationHelper.lerpColor(fieldBorder, UITheme.BORDER_HIGHLIGHT, hoverProgress);
        int animatedTextColor = isOverSidebar
            ? textColor
            : AnimationHelper.lerpColor(textColor, UITheme.TEXT_HEADER, hoverProgress);

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, animatedFieldBackground);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, animatedFieldBorder);

        String inlineLabel = "Preset:";
        int labelX = fieldLeft + 4;
        int labelY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(inlineLabel), labelX, labelY, labelColor);
        int valueTextX = labelX + textRenderer.getWidth(inlineLabel) + 6;
        int maxValueWidth = Math.max(0, fieldLeft + fieldWidth - valueTextX - 4);

        String value;
        if (editing) {
            value = stopTargetEditBuffer;
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

        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && hasStopTargetSelection()) {
            int start = MathHelper.clamp(stopTargetSelectionStart, 0, display.length());
            int end = MathHelper.clamp(stopTargetSelectionEnd, 0, display.length());
            if (start != end) {
                int selectionStartX = valueTextX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = valueTextX + textRenderer.getWidth(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        drawNodeText(context, textRenderer, Text.literal(display), valueTextX, textY, valueDrawColor);

        if (editing && stopTargetCaretVisible) {
            int caretIndex = MathHelper.clamp(stopTargetCaretPosition, 0, display.length());
            int caretX = valueTextX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    private void renderVariableInputField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT;
        int activeFieldBorder = getSelectedNodeAccentColor();
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_LABEL;
        int activeTextColor = UITheme.TEXT_LABEL;
        int caretColor = UITheme.TEXT_LABEL;

        boolean editing = isEditingVariableField() && variableEditingNode == node;
        if (editing) {
            updateVariableCaretBlink();
        }

        int fieldTop = node.getVariableFieldInputTop() - cameraY;
        int fieldHeight = node.getVariableFieldHeight();
        int fieldLeft = node.getVariableFieldLeft() - cameraX;
        int fieldWidth = node.getVariableFieldWidth();
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        int fieldBottom = fieldTop + fieldHeight;
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getVariableFieldLeft()
            && worldMouseX <= node.getVariableFieldLeft() + fieldWidth
            && worldMouseY >= node.getVariableFieldInputTop()
            && worldMouseY <= node.getVariableFieldInputTop() + fieldHeight;
        float progress = getTextFieldHighlightProgress(node.getId() + "#variable", hovered, editing);
        int backgroundColor = isOverSidebar ? (editing ? activeFieldBackground : fieldBackground)
            : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
        int borderColor = isOverSidebar ? (editing ? activeFieldBorder : fieldBorder)
            : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
        int valueColor = isOverSidebar ? (editing ? activeTextColor : textColor)
            : AnimationHelper.lerpColor(textColor, activeTextColor, progress);

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value;
        if (editing) {
            value = variableEditBuffer;
        } else {
            String keyName = node.getVariableFieldParameterKey();
            NodeParameter variableParam = node.getParameter(keyName);
            value = variableParam != null ? variableParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display;
        if (!editing && value.isEmpty()) {
            String keyName = node.getVariableFieldParameterKey();
            display = "List".equalsIgnoreCase(keyName) ? "list" : "variable";
            valueColor = UITheme.TEXT_TERTIARY;
        } else {
            display = value;
        }

        display = editing
            ? display
            : trimTextToWidth(display, textRenderer, fieldWidth - 6);
        int variableHighlightColor = isOverSidebar ? toGrayscale(getSelectedNodeAccentColor(), 0.85f) : getSelectedNodeAccentColor();
        Set<String> variableFieldVariableNames = collectRuntimeVariableNames(node);
        InlineVariableRender variableFieldRenderData = null;
        if (!variableFieldVariableNames.isEmpty() && value.indexOf('~') >= 0) {
            InlineVariableRender candidate = buildInlineVariableRender(value, variableFieldVariableNames, valueColor, variableHighlightColor);
            if (editing) {
                variableFieldRenderData = candidate;
                display = variableFieldRenderData.displayText;
            } else if (textRenderer.getWidth(candidate.displayText) <= fieldWidth - 6) {
                variableFieldRenderData = candidate;
                display = variableFieldRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, variableFieldVariableNames)) {
                display = trimTextToWidth(candidate.displayText, textRenderer, fieldWidth - 6);
                valueColor = variableHighlightColor;
            }
        }

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && hasVariableSelection()) {
            int start = variableSelectionStart;
            int end = variableSelectionEnd;
            if (variableFieldRenderData != null) {
                start = variableFieldRenderData.toDisplayIndex(start);
                end = variableFieldRenderData.toDisplayIndex(end);
            }
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (variableFieldRenderData != null && shouldRenderNodeText()) {
            variableFieldRenderData.draw(context, textRenderer, textX, textY);
        } else {
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);
        }

        if (editing && variableCaretVisible) {
            int caretIndex = variableCaretPosition;
            if (variableFieldRenderData != null) {
                caretIndex = variableFieldRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = MathHelper.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }
    }

    private void renderSchematicDropdownField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar,
                                              int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = UITheme.SCHEMATIC_ACTIVE_BORDER;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        boolean open = schematicDropdownOpen && schematicDropdownNode == node;

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

        drawNodeText(context, textRenderer, Text.literal("Schematic"), fieldLeft, labelTop + (labelHeight - textRenderer.fontHeight) / 2, labelColor);

        int fieldBottom = fieldTop + fieldHeight;
        int backgroundColor = isOverSidebar
            ? fieldBackground
            : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, hoverProgress);
        int borderColor = isOverSidebar
            ? fieldBorder
            : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, hoverProgress);
        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

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
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        int animatedTextColor = isOverSidebar
            ? textColor
            : AnimationHelper.lerpColor(textColor, UITheme.TEXT_HEADER, hoverProgress);
        drawNodeText(context, textRenderer, Text.literal(display), textX, textY, (value.equals("schematic") ? textColor : animatedTextColor));

        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    private void renderSchematicDropdownList(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(schematicDropdownAnimation, schematicDropdownOpen);
        if (schematicDropdownNode != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearSchematicDropdownState();
            return;
        }

        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        List<String> options = schematicDropdownOptions;
        int optionCount = options.isEmpty() ? 1 : options.size();
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        schematicDropdownScrollOffset = MathHelper.clamp(schematicDropdownScrollOffset, 0, layout.maxScrollOffset);

        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int dropdownWidth = getSchematicDropdownWidth(node);
        int listLeft = node.getSchematicFieldLeft() - cameraX;
        int listRight = listLeft + dropdownWidth;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, fieldBackground);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, fieldBorder);

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        schematicDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && worldMouseX >= node.getSchematicFieldLeft()
            && worldMouseX <= node.getSchematicFieldLeft() + dropdownWidth
            && worldMouseY >= node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2
            && worldMouseY <= node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 + listHeight) {
            int row = (worldMouseY - (node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2)) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            if (row >= 0 && row < visibleCount) {
                schematicDropdownHoverIndex = schematicDropdownScrollOffset + row;
            }
        }

        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = schematicDropdownScrollOffset + row;
            String optionLabel = options.isEmpty() ? "No schematics found" : options.get(optionIndex);
            int rowTop = listTop + row * SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            int rowBottom = rowTop + SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            boolean hovered = options.isEmpty() ? row == 0 && schematicDropdownHoverIndex >= 0 : optionIndex == schematicDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, UITheme.DROP_ROW_HIGHLIGHT);
            }
            String rowText = trimTextToWidth(optionLabel, textRenderer, dropdownWidth - 6 - DROPDOWN_SCROLLBAR_ALLOWANCE);
            drawNodeText(context, textRenderer, Text.literal(rowText), listLeft + 3, rowTop + 4, textColor);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            schematicDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    private void renderRunPresetDropdownList(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(runPresetDropdownAnimation, runPresetDropdownOpen);
        if (runPresetDropdownNode != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearRunPresetDropdownState();
            return;
        }

        int listBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int listBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int rowHoverFill = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_TERTIARY;

        List<String> options = runPresetDropdownOptions;
        int optionCount = options.isEmpty() ? 1 : options.size();
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        runPresetDropdownScrollOffset = MathHelper.clamp(runPresetDropdownScrollOffset, 0, layout.maxScrollOffset);

        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int dropdownWidth = getRunPresetDropdownWidth(node);
        int listLeft = node.getStopTargetFieldLeft() - cameraX;
        int listRight = listLeft + dropdownWidth;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, listBackground);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, listBorder);

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        runPresetDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && worldMouseX >= node.getStopTargetFieldLeft()
            && worldMouseX <= node.getStopTargetFieldLeft() + dropdownWidth
            && worldMouseY >= node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2
            && worldMouseY <= node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 + listHeight) {
            int row = (worldMouseY - (node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2)) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            if (row >= 0 && row < visibleCount) {
                runPresetDropdownHoverIndex = runPresetDropdownScrollOffset + row;
            }
        }

        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = runPresetDropdownScrollOffset + row;
            String optionLabel = options.isEmpty() ? "No presets found" : options.get(optionIndex);
            int rowTop = listTop + row * SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            int rowBottom = rowTop + SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            boolean hovered = options.isEmpty() ? row == 0 && runPresetDropdownHoverIndex >= 0 : optionIndex == runPresetDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, rowHoverFill);
            }
            String rowText = trimTextToWidth(optionLabel, textRenderer, dropdownWidth - 6 - DROPDOWN_SCROLLBAR_ALLOWANCE);
            drawNodeText(context, textRenderer, Text.literal(rowText), listLeft + 3, rowTop + 4, textColor);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            runPresetDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    private void renderAmountSignDropdownList(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(amountSignDropdownAnimation, amountSignDropdownOpen);
        if (amountSignDropdownNode == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearAmountSignDropdownState();
            return;
        }

        Node node = amountSignDropdownNode;
        boolean isOverSidebar = node.getX() < sidebarWidthForRendering;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        java.util.List<String> options = getAmountSignDropdownOptions();
        int optionCount = options.size();
        int listTop = getAmountSignDropdownListTop(node);
        DropdownLayoutHelper.Layout layout = getAmountSignDropdownLayout(node);
        int visibleCount = layout.visibleCount;
        amountSignDropdownScrollOffset = MathHelper.clamp(amountSignDropdownScrollOffset, 0, layout.maxScrollOffset);

        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int dropdownWidth = getAmountSignDropdownWidth(node);
        int listLeft = node.getAmountSignToggleLeft() - cameraX;
        int listRight = listLeft + dropdownWidth;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, fieldBackground);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, fieldBorder);

        float zoom = Math.max(0.01f, getZoomScale());
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);
        amountSignDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && transformedMouseX >= listLeft
            && transformedMouseX <= listRight
            && transformedMouseY >= listTop
            && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            if (row >= 0 && row < visibleCount) {
                amountSignDropdownHoverIndex = amountSignDropdownScrollOffset + row;
            }
        }

        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = amountSignDropdownScrollOffset + row;
            String optionLabel = options.get(optionIndex);
            int rowTop = listTop + row * SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            int rowBottom = rowTop + SCHEMATIC_DROPDOWN_ROW_HEIGHT;
            boolean hovered = optionIndex == amountSignDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, UITheme.DROP_ROW_HIGHLIGHT);
            }
            int textX = listLeft + Math.max(DROPDOWN_SIDE_PADDING, (dropdownWidth - textRenderer.getWidth(optionLabel)) / 2);
            drawNodeText(context, textRenderer, Text.literal(optionLabel), textX, rowTop + 4, textColor);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            amountSignDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    public boolean isEditingCoordinateField() {
        return coordinateEditingNode != null && coordinateEditingAxis >= 0;
    }

    public boolean isScreenCoordinateCaptureActive() {
        return screenCoordinateCaptureNode != null;
    }

    public boolean isScreenCoordinateCaptureActiveFor(Node node) {
        return node != null && node == screenCoordinateCaptureNode;
    }

    public void startScreenCoordinateCapture(Node node) {
        if (node == null || !node.hasScreenCoordinatePickerButton()) {
            cancelScreenCoordinateCapture();
            return;
        }
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        stopStickyNoteEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);
        screenCoordinateCaptureNode = node;
        NodeParameter xParam = node.getParameter("X");
        NodeParameter yParam = node.getParameter("Y");
        screenCoordinatePreviewX = xParam != null ? xParam.getIntValue() : 0;
        screenCoordinatePreviewY = yParam != null ? yParam.getIntValue() : 0;
    }

    public void cancelScreenCoordinateCapture() {
        screenCoordinateCaptureNode = null;
    }

    public void updateScreenCoordinateCapturePreview(int screenX, int screenY) {
        if (!isScreenCoordinateCaptureActive()) {
            return;
        }
        screenCoordinatePreviewX = Math.max(0, screenX);
        screenCoordinatePreviewY = Math.max(0, screenY);
    }

    public boolean commitScreenCoordinateCapture(int screenX, int screenY) {
        if (!isScreenCoordinateCaptureActive()) {
            return false;
        }
        Node node = screenCoordinateCaptureNode;
        screenCoordinateCaptureNode = null;
        if (node == null) {
            return false;
        }
        node.setParameterValueAndPropagate("X", Integer.toString(Math.max(0, screenX)));
        node.setParameterValueAndPropagate("Y", Integer.toString(Math.max(0, screenY)));
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
        return true;
    }

    private void updateCoordinateCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - coordinateCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            coordinateCaretVisible = !coordinateCaretVisible;
            coordinateCaretLastToggleTime = now;
        }
    }

    private void resetCoordinateCaretBlink() {
        coordinateCaretVisible = true;
        coordinateCaretLastToggleTime = System.currentTimeMillis();
    }

    public int getCoordinateFieldAxisAt(Node node, int screenX, int screenY) {
        if (node == null || !node.hasCoordinateInputFields()) {
            return -1;
        }
        if (isScreenCoordinateCaptureActiveFor(node)) {
            return -1;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int inputTop = node.getCoordinateFieldInputTop();
        int inputBottom = inputTop + node.getCoordinateFieldHeight();
        if (worldY < inputTop || worldY > inputBottom) {
            return -1;
        }

        int startX = node.getCoordinateFieldStartX();
        int fieldWidth = node.getCoordinateFieldWidth();
        int spacing = node.getCoordinateFieldSpacing();
        String[] axes = getCoordinateAxes(node);

        for (int i = 0; i < axes.length; i++) {
            int fieldX = startX + i * (fieldWidth + spacing);
            if (worldX >= fieldX && worldX <= fieldX + fieldWidth) {
                return i;
            }
        }
        return -1;
    }

    public boolean isPointInsideScreenCoordinatePickerButton(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasScreenCoordinatePickerButton()) {
            return false;
        }
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        int buttonLeft = node.getScreenCoordinatePickerButtonLeft();
        int buttonTop = node.getScreenCoordinatePickerButtonTop();
        int buttonWidth = node.getScreenCoordinatePickerButtonWidth();
        int buttonHeight = node.getScreenCoordinatePickerButtonHeight();
        return worldX >= buttonLeft && worldX <= buttonLeft + buttonWidth
            && worldY >= buttonTop && worldY <= buttonTop + buttonHeight;
    }

    public boolean handleScreenCoordinatePickerClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY)) {
            return false;
        }
        selectNode(node);
        startScreenCoordinateCapture(node);
        return true;
    }

    public void startCoordinateEditing(Node node, int axisIndex) {
        String[] axes = getCoordinateAxes(node);
        if (node == null || !node.hasCoordinateInputFields() || axisIndex < 0
            || axisIndex >= axes.length) {
            stopCoordinateEditing(false);
            return;
        }

        cancelScreenCoordinateCapture();

        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeRandomRoundingDropdown();
        closeAmountSignDropdown();
        closeAmountSignDropdown();
        closeAmountSignDropdown();
        closeAmountSignDropdown();
        closeAmountSignDropdown();
        closeAmountSignDropdown();
        closeRandomRoundingDropdown();
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);

        if (isEditingCoordinateField()) {
            if (coordinateEditingNode == node && coordinateEditingAxis == axisIndex) {
                return;
            }
            boolean changed = applyCoordinateEdit();
            if (changed) {
                notifyNodeParametersChanged(coordinateEditingNode);
            }
        }

        coordinateEditingNode = node;
        coordinateEditingAxis = axisIndex;

        NodeParameter parameter = getCoordinateParameter(node, axisIndex);
        coordinateEditBuffer = parameter != null ? parameter.getStringValue() : "";
        coordinateEditOriginalValue = coordinateEditBuffer;
        resetCoordinateCaretBlink();
        coordinateCaretPosition = coordinateEditBuffer.length();
        coordinateSelectionAnchor = -1;
        coordinateSelectionStart = -1;
        coordinateSelectionEnd = -1;
        updateCoordinateFieldContentWidth(getClientTextRenderer());
    }

    public void stopCoordinateEditing(boolean commit) {
        if (!isEditingCoordinateField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyCoordinateEdit();
        } else {
            revertCoordinateEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(coordinateEditingNode);
        }

        coordinateEditingNode = null;
        coordinateEditingAxis = -1;
        coordinateEditBuffer = "";
        coordinateEditOriginalValue = "";
        coordinateCaretVisible = true;
        coordinateCaretPosition = 0;
        coordinateSelectionAnchor = -1;
        coordinateSelectionStart = -1;
        coordinateSelectionEnd = -1;
    }

    private boolean applyCoordinateEdit() {
        if (!isEditingCoordinateField()) {
            return false;
        }
        String value = coordinateEditBuffer == null ? "" : coordinateEditBuffer.trim();
        if (!value.isEmpty() && !"-".equals(value) && !isNumericOrVariableReference(value, coordinateEditingNode, false, true)) {
            coordinateEditingNode.sendNodeErrorMessageToPlayer("Please enter a number, arithmetic expression, or variable (~variable_name).");
            return false;
        }
        if (value.isEmpty() || "-".equals(value)) {
            value = "0";
        }
        String axisName = getCoordinateAxes(coordinateEditingNode)[coordinateEditingAxis];
        NodeParameter parameter = getCoordinateParameter(coordinateEditingNode, coordinateEditingAxis);
        String previous = parameter != null ? parameter.getStringValue() : "";
        coordinateEditingNode.setParameterValueAndPropagate(axisName, value);
        coordinateEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertCoordinateEdit() {
        if (!isEditingCoordinateField()) {
            return;
        }
        String axisName = getCoordinateAxes(coordinateEditingNode)[coordinateEditingAxis];
        coordinateEditingNode.setParameterValueAndPropagate(axisName, coordinateEditOriginalValue);
        coordinateEditingNode.recalculateDimensions();
    }

    private NodeParameter getCoordinateParameter(Node node, int axisIndex) {
        String[] axes = getCoordinateAxes(node);
        if (node == null || axisIndex < 0 || axisIndex >= axes.length) {
            return null;
        }
        return node.getParameter(axes[axisIndex]);
    }

    public boolean handleCoordinateKeyPressed(int keyCode, int modifiers) {
        if (!isEditingCoordinateField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteCoordinateSelection()) {
                    return true;
                }
                if (controlHeld && coordinateCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(coordinateEditBuffer, coordinateCaretPosition);
                    coordinateEditBuffer = coordinateEditBuffer.substring(0, deleteToPos)
                        + coordinateEditBuffer.substring(coordinateCaretPosition);
                    setCoordinateCaretPosition(deleteToPos);
                    updateCoordinateFieldContentWidth(getClientTextRenderer());
                } else if (coordinateCaretPosition > 0 && !coordinateEditBuffer.isEmpty()) {
                    coordinateEditBuffer = coordinateEditBuffer.substring(0, coordinateCaretPosition - 1)
                        + coordinateEditBuffer.substring(coordinateCaretPosition);
                    setCoordinateCaretPosition(coordinateCaretPosition - 1);
                    updateCoordinateFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteCoordinateSelection()) {
                    return true;
                }
                if (coordinateCaretPosition < coordinateEditBuffer.length()) {
                    coordinateEditBuffer = coordinateEditBuffer.substring(0, coordinateCaretPosition)
                        + coordinateEditBuffer.substring(coordinateCaretPosition + 1);
                    setCoordinateCaretPosition(coordinateCaretPosition);
                    updateCoordinateFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveCoordinateCaretTo(coordinateCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveCoordinateCaretTo(coordinateCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveCoordinateCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveCoordinateCaretTo(coordinateEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopCoordinateEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopCoordinateEditing(true);
                return true;
            case GLFW.GLFW_KEY_TAB:
                Node node = coordinateEditingNode;
                int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                int axisCount = getCoordinateAxes(node).length;
                int nextAxis = (coordinateEditingAxis + direction + axisCount) % axisCount;
                startCoordinateEditing(node, nextAxis);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllCoordinateText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyCoordinateSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutCoordinateSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        smartPasteCoordinates(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    private boolean isTextShortcutDown(int modifiers) {
        return InputCompatibilityBridge.hasControlDown()
            || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    public boolean handleCoordinateCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingCoordinateField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertCoordinateText(String.valueOf(chr), textRenderer);
    }

    public boolean isEditingAmountField() {
        return amountEditingNode != null;
    }

    private void updateAmountCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - amountCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            amountCaretVisible = !amountCaretVisible;
            amountCaretLastToggleTime = now;
        }
    }

    private void resetAmountCaretBlink() {
        amountCaretVisible = true;
        amountCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startAmountEditing(Node node) {
        if (node == null || !node.hasAmountInputField() || !node.isAmountInputEnabled()) {
            stopAmountEditing(false);
            return;
        }

        closeSchematicDropdown();
        closeRunPresetDropdown();
        if (isEditingAmountField()) {
            if (amountEditingNode == node) {
                return;
            }
            boolean changed = applyAmountEdit();
            if (changed) {
                notifyNodeParametersChanged(amountEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        stopStickyNoteEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);

        amountEditingNode = node;
        String amountKey = node.getAmountParameterKey();
        NodeParameter amountParam = node.getParameter(amountKey);
        amountEditBuffer = amountParam != null ? amountParam.getStringValue() : "";
        if (node.getType() == NodeType.MOVE_ITEM && isMoveItemAllAmountValue(amountEditBuffer)) {
            amountEditBuffer = "";
        }
        amountEditOriginalValue = amountEditBuffer;
        resetAmountCaretBlink();
        amountCaretPosition = amountEditBuffer.length();
        amountSelectionAnchor = -1;
        amountSelectionStart = -1;
        amountSelectionEnd = -1;
        updateAmountFieldContentWidth(getClientTextRenderer());
    }

    public void stopAmountEditing(boolean commit) {
        if (!isEditingAmountField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyAmountEdit();
        } else {
            revertAmountEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(amountEditingNode);
        }

        amountEditingNode = null;
        amountEditBuffer = "";
        amountEditOriginalValue = "";
        amountCaretVisible = true;
        amountCaretPosition = 0;
        amountSelectionAnchor = -1;
        amountSelectionStart = -1;
        amountSelectionEnd = -1;
    }

    private boolean applyAmountEdit() {
        if (!isEditingAmountField()) {
            return false;
        }

        String value = amountEditBuffer == null ? "" : amountEditBuffer.trim();
        if ((amountEditingNode.getType() == NodeType.PARAM_DURATION
            || amountEditingNode.getType() == NodeType.USE
            || amountEditingNode.getType() == NodeType.SWING) && !value.startsWith("~")) {
            // Accept locale decimal input like "1,5" for duration-style fields.
            value = value.replace(',', '.');
        }
        if (amountEditingNode.getType() == NodeType.MOVE_ITEM && isMoveItemAllAmountValue(value)) {
            value = "0";
        }
        if (amountEditingNode.getType() != NodeType.PARAM_DURATION
            && amountEditingNode.getType() != NodeType.USE
            && amountEditingNode.getType() != NodeType.SWING
            && !value.isEmpty()
            && !isNumericOrVariableReference(value, amountEditingNode, true, false)) {
            amountEditingNode.sendNodeErrorMessageToPlayer("Please enter a number, arithmetic expression, or variable (~variable_name).");
            return false;
        }
        if (value.isEmpty()) {
            if (amountEditingNode.getType() == NodeType.MOVE_ITEM) {
                value = "0";
            } else if (amountEditingNode.getType() == NodeType.TRADE
                || amountEditingNode.getType() == NodeType.SENSOR_VILLAGER_TRADE
                || amountEditingNode.getType() == NodeType.SENSOR_IN_STOCK) {
                value = "1";
            } else if (amountEditingNode.getType() == NodeType.PARAM_DURATION
                || amountEditingNode.getType() == NodeType.USE
                || amountEditingNode.getType() == NodeType.SWING) {
                value = "";
            } else {
                value = amountEditOriginalValue != null && !amountEditOriginalValue.isEmpty()
                    ? amountEditOriginalValue
                    : "0";
            }
        }

        String amountKey = amountEditingNode.getAmountParameterKey();
        NodeParameter amountParam = amountEditingNode.getParameter(amountKey);
        String previous = amountParam != null ? amountParam.getStringValue() : "";
        amountEditingNode.setParameterValueAndPropagate(amountKey, value);
        amountEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertAmountEdit() {
        if (!isEditingAmountField()) {
            return;
        }
        String amountKey = amountEditingNode.getAmountParameterKey();
        amountEditingNode.setParameterValueAndPropagate(amountKey, amountEditOriginalValue);
        amountEditingNode.recalculateDimensions();
    }

    public boolean handleAmountKeyPressed(int keyCode, int modifiers) {
        if (!isEditingAmountField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteAmountSelection()) {
                    return true;
                }
                if (controlHeld && amountCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(amountEditBuffer, amountCaretPosition);
                    amountEditBuffer = amountEditBuffer.substring(0, deleteToPos)
                        + amountEditBuffer.substring(amountCaretPosition);
                    setAmountCaretPosition(deleteToPos);
                    updateAmountFieldContentWidth(getClientTextRenderer());
                } else if (amountCaretPosition > 0 && !amountEditBuffer.isEmpty()) {
                    amountEditBuffer = amountEditBuffer.substring(0, amountCaretPosition - 1)
                        + amountEditBuffer.substring(amountCaretPosition);
                    setAmountCaretPosition(amountCaretPosition - 1);
                    updateAmountFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteAmountSelection()) {
                    return true;
                }
                if (amountCaretPosition < amountEditBuffer.length()) {
                    amountEditBuffer = amountEditBuffer.substring(0, amountCaretPosition)
                        + amountEditBuffer.substring(amountCaretPosition + 1);
                    setAmountCaretPosition(amountCaretPosition);
                    updateAmountFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveAmountCaretTo(amountCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveAmountCaretTo(amountCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveAmountCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveAmountCaretTo(amountEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopAmountEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopAmountEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllAmountText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyAmountSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutAmountSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertAmountText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleAmountCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingAmountField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertAmountText(String.valueOf(chr), textRenderer);
    }

    public boolean isEditingStopTargetField() {
        return stopTargetEditingNode != null;
    }

    private void updateStopTargetCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - stopTargetCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            stopTargetCaretVisible = !stopTargetCaretVisible;
            stopTargetCaretLastToggleTime = now;
        }
    }

    private void resetStopTargetCaretBlink() {
        stopTargetCaretVisible = true;
        stopTargetCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startStopTargetEditing(Node node) {
        if (node == null || !node.hasStopTargetInputField()) {
            stopStopTargetEditing(false);
            return;
        }

        closeSchematicDropdown();
        closeRunPresetDropdown();
        if (isEditingStopTargetField()) {
            if (stopTargetEditingNode == node) {
                return;
            }
            boolean changed = applyStopTargetEdit();
            if (changed) {
                notifyNodeParametersChanged(stopTargetEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopMessageEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);
        stopVariableEditing(true);
        stopStickyNoteEditing(true);

        stopTargetEditingNode = node;
        NodeParameter targetParam = node.getParameter(getStopTargetParameterKey(node));
        stopTargetEditBuffer = targetParam != null ? targetParam.getStringValue() : "";
        stopTargetEditOriginalValue = stopTargetEditBuffer;
        resetStopTargetCaretBlink();
        stopTargetCaretPosition = stopTargetEditBuffer.length();
        stopTargetSelectionAnchor = -1;
        stopTargetSelectionStart = -1;
        stopTargetSelectionEnd = -1;
        updateStopTargetFieldContentWidth(getClientTextRenderer());
    }

    public void stopStopTargetEditing(boolean commit) {
        if (!isEditingStopTargetField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyStopTargetEdit();
        } else {
            revertStopTargetEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(stopTargetEditingNode);
        }

        stopTargetEditingNode = null;
        stopTargetEditBuffer = "";
        stopTargetEditOriginalValue = "";
        stopTargetCaretVisible = true;
        stopTargetCaretPosition = 0;
        stopTargetSelectionAnchor = -1;
        stopTargetSelectionStart = -1;
        stopTargetSelectionEnd = -1;
    }

    public boolean isEditingVariableField() {
        return variableEditingNode != null;
    }

    private void updateVariableCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - variableCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            variableCaretVisible = !variableCaretVisible;
            variableCaretLastToggleTime = now;
        }
    }

    private void resetVariableCaretBlink() {
        variableCaretVisible = true;
        variableCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startVariableEditing(Node node) {
        if (node == null || !node.hasVariableInputField()) {
            stopVariableEditing(false);
            return;
        }

        closeSchematicDropdown();
        closeRunPresetDropdown();
        if (isEditingVariableField()) {
            if (variableEditingNode == node) {
                return;
            }
            boolean changed = applyVariableEdit();
            if (changed) {
                notifyNodeParametersChanged(variableEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopMessageEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);
        stopStickyNoteEditing(true);

        variableEditingNode = node;
        String keyName = node.getVariableFieldParameterKey();
        NodeParameter variableParam = node.getParameter(keyName);
        variableEditBuffer = variableParam != null ? variableParam.getStringValue() : "";
        variableEditOriginalValue = variableEditBuffer;
        resetVariableCaretBlink();
        variableCaretPosition = variableEditBuffer.length();
        variableSelectionAnchor = -1;
        variableSelectionStart = -1;
        variableSelectionEnd = -1;
        updateVariableFieldContentWidth(getClientTextRenderer());
    }

    public void stopVariableEditing(boolean commit) {
        if (!isEditingVariableField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyVariableEdit();
        } else {
            revertVariableEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(variableEditingNode);
        }

        variableEditingNode = null;
        variableEditBuffer = "";
        variableEditOriginalValue = "";
        variableCaretVisible = true;
        variableCaretPosition = 0;
        variableSelectionAnchor = -1;
        variableSelectionStart = -1;
        variableSelectionEnd = -1;
    }

    private boolean applyVariableEdit() {
        if (!isEditingVariableField()) {
            return false;
        }

        String value = variableEditBuffer == null ? "" : variableEditBuffer;
        String keyName = variableEditingNode.getVariableFieldParameterKey();
        NodeParameter variableParam = variableEditingNode.getParameter(keyName);
        String previous = variableParam != null ? variableParam.getStringValue() : "";
        variableEditingNode.setParameterValueAndPropagate(keyName, value);
        variableEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertVariableEdit() {
        if (!isEditingVariableField()) {
            return;
        }
        String keyName = variableEditingNode.getVariableFieldParameterKey();
        variableEditingNode.setParameterValueAndPropagate(keyName, variableEditOriginalValue);
        variableEditingNode.recalculateDimensions();
    }

    public boolean handleVariableKeyPressed(int keyCode, int modifiers) {
        if (!isEditingVariableField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteVariableSelection()) {
                    return true;
                }
                if (controlHeld && variableCaretPosition > 0) {
                    int deleteToPos = findPreviousWordBoundary(variableEditBuffer, variableCaretPosition);
                    variableEditBuffer = variableEditBuffer.substring(0, deleteToPos)
                        + variableEditBuffer.substring(variableCaretPosition);
                    setVariableCaretPosition(deleteToPos);
                    updateVariableFieldContentWidth(getClientTextRenderer());
                } else if (variableCaretPosition > 0 && !variableEditBuffer.isEmpty()) {
                    variableEditBuffer = variableEditBuffer.substring(0, variableCaretPosition - 1)
                        + variableEditBuffer.substring(variableCaretPosition);
                    setVariableCaretPosition(variableCaretPosition - 1);
                    updateVariableFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteVariableSelection()) {
                    return true;
                }
                if (variableCaretPosition < variableEditBuffer.length()) {
                    variableEditBuffer = variableEditBuffer.substring(0, variableCaretPosition)
                        + variableEditBuffer.substring(variableCaretPosition + 1);
                    setVariableCaretPosition(variableCaretPosition);
                    updateVariableFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveVariableCaretTo(variableCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveVariableCaretTo(variableCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveVariableCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveVariableCaretTo(variableEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopVariableEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopVariableEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllVariableText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyVariableSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutVariableSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertVariableText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleVariableCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingVariableField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertVariableText(String.valueOf(chr), textRenderer);
    }

    private boolean applyStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return false;
        }

        String value = stopTargetEditBuffer == null ? "" : stopTargetEditBuffer.trim();
        if (!isPresetSelectorNode(stopTargetEditingNode)
            && !value.isEmpty()
            && !isNumericOrVariableReference(value, stopTargetEditingNode, false, false)) {
            stopTargetEditingNode.sendNodeErrorMessageToPlayer("Please enter a number, arithmetic expression, or variable (~variable_name).");
            return false;
        }
        String keyName = getStopTargetParameterKey(stopTargetEditingNode);
        NodeParameter targetParam = stopTargetEditingNode.getParameter(keyName);
        String previous = targetParam != null ? targetParam.getStringValue() : "";
        stopTargetEditingNode.setParameterValueAndPropagate(keyName, value);
        stopTargetEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return;
        }
        stopTargetEditingNode.setParameterValueAndPropagate(
            getStopTargetParameterKey(stopTargetEditingNode),
            stopTargetEditOriginalValue
        );
        stopTargetEditingNode.recalculateDimensions();
    }

    public boolean handleStopTargetKeyPressed(int keyCode, int modifiers) {
        if (!isEditingStopTargetField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteStopTargetSelection()) {
                    return true;
                }
                if (controlHeld && stopTargetCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(stopTargetEditBuffer, stopTargetCaretPosition);
                    stopTargetEditBuffer = stopTargetEditBuffer.substring(0, deleteToPos)
                        + stopTargetEditBuffer.substring(stopTargetCaretPosition);
                    setStopTargetCaretPosition(deleteToPos);
                    updateStopTargetFieldContentWidth(getClientTextRenderer());
                } else if (stopTargetCaretPosition > 0 && !stopTargetEditBuffer.isEmpty()) {
                    stopTargetEditBuffer = stopTargetEditBuffer.substring(0, stopTargetCaretPosition - 1)
                        + stopTargetEditBuffer.substring(stopTargetCaretPosition);
                    setStopTargetCaretPosition(stopTargetCaretPosition - 1);
                    updateStopTargetFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteStopTargetSelection()) {
                    return true;
                }
                if (stopTargetCaretPosition < stopTargetEditBuffer.length()) {
                    stopTargetEditBuffer = stopTargetEditBuffer.substring(0, stopTargetCaretPosition)
                        + stopTargetEditBuffer.substring(stopTargetCaretPosition + 1);
                    setStopTargetCaretPosition(stopTargetCaretPosition);
                    updateStopTargetFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveStopTargetCaretTo(stopTargetCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveStopTargetCaretTo(stopTargetCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveStopTargetCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveStopTargetCaretTo(stopTargetEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopStopTargetEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopStopTargetEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllStopTargetText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyStopTargetSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutStopTargetSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertStopTargetText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleStopTargetCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingStopTargetField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertStopTargetText(String.valueOf(chr), textRenderer);
    }

    public boolean isEditingStickyNote() {
        return stickyNoteEditingNode != null;
    }

    private void updateStickyNoteCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - stickyNoteCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            stickyNoteCaretVisible = !stickyNoteCaretVisible;
            stickyNoteCaretLastToggleTime = now;
        }
    }

    private void resetStickyNoteCaretBlink() {
        stickyNoteCaretVisible = true;
        stickyNoteCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startStickyNoteEditing(Node node) {
        if (node == null || !node.isStickyNote()) {
            stopStickyNoteEditing(false);
            return;
        }
        if (stickyNoteEditingNode == node) {
            return;
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);
        stopStickyNoteEditing(true);
        stopMessageEditing(true);

        stickyNoteEditingNode = node;
        stickyNoteEditBuffer = node.getStickyNoteText();
        stickyNoteEditOriginalValue = stickyNoteEditBuffer;
        stickyNoteCaretPosition = stickyNoteEditBuffer.length();
        resetStickyNoteCaretBlink();
    }

    public void stopStickyNoteEditing(boolean commit) {
        if (!isEditingStickyNote()) {
            return;
        }
        if (commit) {
            stickyNoteEditingNode.setStickyNoteText(stickyNoteEditBuffer);
            if (!Objects.equals(stickyNoteEditOriginalValue, stickyNoteEditBuffer)) {
                markWorkspaceDirty();
            }
        } else {
            stickyNoteEditingNode.setStickyNoteText(stickyNoteEditOriginalValue);
        }
        stickyNoteEditingNode = null;
        stickyNoteEditBuffer = "";
        stickyNoteEditOriginalValue = "";
        stickyNoteCaretPosition = 0;
        stickyNoteCaretVisible = true;
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
        if (node == null || !node.isStickyNote()) {
            return false;
        }
        StickyNoteResizeCorner corner = getStickyNoteResizeCornerAt(node, screenX, screenY);
        if (corner == null) {
            return false;
        }
        stopStickyNoteEditing(true);
        if (suppressUndoCapture) {
            pendingDragUndoSnapshot = null;
        } else {
            pendingDragUndoSnapshot = buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
        }
        dragOperationChanged = false;
        resizingStickyNote = node;
        stickyNoteResizeCorner = corner;
        stickyNoteResizeStartX = node.getX();
        stickyNoteResizeStartY = node.getY();
        stickyNoteResizeStartWidth = node.getWidth();
        stickyNoteResizeStartHeight = node.getHeight();
        return true;
    }

    private StickyNoteResizeCorner getStickyNoteResizeCornerAt(Node node, int screenX, int screenY) {
        if (node == null || !node.isStickyNote()) {
            return null;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int size = node.getStickyNoteResizeHandleSize();
        int half = size / 2;
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() - half, node.getY() - half, size, size)) {
            return StickyNoteResizeCorner.TOP_LEFT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() + node.getWidth() - half, node.getY() - half, size, size)) {
            return StickyNoteResizeCorner.TOP_RIGHT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() - half, node.getY() + node.getHeight() - half, size, size)) {
            return StickyNoteResizeCorner.BOTTOM_LEFT;
        }
        if (ContextMenuRenderer.isPointInRect(worldX, worldY, node.getX() + node.getWidth() - half, node.getY() + node.getHeight() - half, size, size)) {
            return StickyNoteResizeCorner.BOTTOM_RIGHT;
        }
        return null;
    }

    private void updateStickyNoteResize(int worldMouseX, int worldMouseY) {
        if (resizingStickyNote == null || stickyNoteResizeCorner == null) {
            return;
        }
        int left = stickyNoteResizeStartX;
        int top = stickyNoteResizeStartY;
        int right = stickyNoteResizeStartX + stickyNoteResizeStartWidth;
        int bottom = stickyNoteResizeStartY + stickyNoteResizeStartHeight;

        switch (stickyNoteResizeCorner) {
            case TOP_LEFT -> {
                left = Math.min(worldMouseX, right - 120);
                top = Math.min(worldMouseY, bottom - 84);
            }
            case TOP_RIGHT -> {
                right = Math.max(worldMouseX, left + 120);
                top = Math.min(worldMouseY, bottom - 84);
            }
            case BOTTOM_LEFT -> {
                left = Math.min(worldMouseX, right - 120);
                bottom = Math.max(worldMouseY, top + 84);
            }
            case BOTTOM_RIGHT -> {
                right = Math.max(worldMouseX, left + 120);
                bottom = Math.max(worldMouseY, top + 84);
            }
        }

        int newWidth = right - left;
        int newHeight = bottom - top;
        if (left != resizingStickyNote.getX() || top != resizingStickyNote.getY()
            || newWidth != resizingStickyNote.getWidth() || newHeight != resizingStickyNote.getHeight()) {
            dragOperationChanged = true;
        }
        resizingStickyNote.setPosition(left, top);
        resizingStickyNote.setStickyNoteSize(newWidth, newHeight);
    }

    public boolean handleStickyNoteKeyPressed(int keyCode, int modifiers) {
        if (!isEditingStickyNote()) {
            return false;
        }
        boolean controlHeld = isTextShortcutDown(modifiers);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (stickyNoteCaretPosition > 0 && !stickyNoteEditBuffer.isEmpty()) {
                    stickyNoteEditBuffer = stickyNoteEditBuffer.substring(0, stickyNoteCaretPosition - 1)
                        + stickyNoteEditBuffer.substring(stickyNoteCaretPosition);
                    stickyNoteCaretPosition--;
                    resetStickyNoteCaretBlink();
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (stickyNoteCaretPosition < stickyNoteEditBuffer.length()) {
                    stickyNoteEditBuffer = stickyNoteEditBuffer.substring(0, stickyNoteCaretPosition)
                        + stickyNoteEditBuffer.substring(stickyNoteCaretPosition + 1);
                    resetStickyNoteCaretBlink();
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                stickyNoteCaretPosition = Math.max(0, stickyNoteCaretPosition - 1);
                resetStickyNoteCaretBlink();
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                stickyNoteCaretPosition = Math.min(stickyNoteEditBuffer.length(), stickyNoteCaretPosition + 1);
                resetStickyNoteCaretBlink();
                return true;
            case GLFW.GLFW_KEY_HOME:
                stickyNoteCaretPosition = 0;
                resetStickyNoteCaretBlink();
                return true;
            case GLFW.GLFW_KEY_END:
                stickyNoteCaretPosition = stickyNoteEditBuffer.length();
                resetStickyNoteCaretBlink();
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                return insertStickyNoteText("\n");
            case GLFW.GLFW_KEY_ESCAPE:
                stopStickyNoteEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    stickyNoteCaretPosition = stickyNoteEditBuffer.length();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    setClipboardText(stickyNoteEditBuffer);
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    setClipboardText(stickyNoteEditBuffer);
                    stickyNoteEditBuffer = "";
                    stickyNoteCaretPosition = 0;
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    return insertStickyNoteText(getClipboardText());
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleStickyNoteCharTyped(char chr, int modifiers) {
        if (!isEditingStickyNote()) {
            return false;
        }
        if (chr == '\r') {
            return false;
        }
        return insertStickyNoteText(String.valueOf(chr));
    }

    private boolean insertStickyNoteText(String text) {
        if (!isEditingStickyNote() || text == null || text.isEmpty()) {
            return false;
        }
        int allowed = STICKY_NOTE_MAX_CHARS - stickyNoteEditBuffer.length();
        if (allowed <= 0) {
            return true;
        }
        String safe = text.length() > allowed ? text.substring(0, allowed) : text;
        stickyNoteEditBuffer = stickyNoteEditBuffer.substring(0, stickyNoteCaretPosition)
            + safe
            + stickyNoteEditBuffer.substring(stickyNoteCaretPosition);
        stickyNoteCaretPosition += safe.length();
        resetStickyNoteCaretBlink();
        return true;
    }

    public boolean isEditingMessageField() {
        return messageEditingNode != null && messageEditingIndex >= 0;
    }

    private void updateMessageCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - messageCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            messageCaretVisible = !messageCaretVisible;
            messageCaretLastToggleTime = now;
        }
    }

    private void resetMessageCaretBlink() {
        messageCaretVisible = true;
        messageCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startMessageEditing(Node node, int index) {
        if (node == null || !node.hasMessageInputFields() || index < 0 || index >= node.getMessageFieldCount()) {
            stopMessageEditing(false);
            return;
        }

        closeSchematicDropdown();
        closeRunPresetDropdown();
        if (isEditingMessageField()) {
            if (messageEditingNode == node && messageEditingIndex == index) {
                return;
            }
            boolean changed = applyMessageEdit();
            if (changed) {
                notifyNodeParametersChanged(messageEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);

        messageEditingNode = node;
        messageEditingIndex = index;
        messageEditBuffer = node.getMessageLine(index);
        messageEditOriginalValue = messageEditBuffer;
        resetMessageCaretBlink();
        messageCaretPosition = messageEditBuffer.length();
        messageSelectionAnchor = -1;
        messageSelectionStart = -1;
        messageSelectionEnd = -1;
        updateMessageFieldContentWidth(getClientTextRenderer());
    }

    public void stopMessageEditing(boolean commit) {
        if (!isEditingMessageField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyMessageEdit();
        } else {
            revertMessageEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(messageEditingNode);
        }

        messageEditingNode = null;
        messageEditingIndex = -1;
        messageEditBuffer = "";
        messageEditOriginalValue = "";
        messageCaretVisible = true;
        messageCaretPosition = 0;
        messageSelectionAnchor = -1;
        messageSelectionStart = -1;
        messageSelectionEnd = -1;
    }

    public boolean isEditingEventNameField() {
        return eventNameEditingNode != null;
    }

    private void updateEventNameCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - eventNameCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            eventNameCaretVisible = !eventNameCaretVisible;
            eventNameCaretLastToggleTime = now;
        }
    }

    private void resetEventNameCaretBlink() {
        eventNameCaretVisible = true;
        eventNameCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startEventNameEditing(Node node) {
        if (node == null || (node.getType() != NodeType.EVENT_FUNCTION && node.getType() != NodeType.EVENT_CALL)) {
            stopEventNameEditing(false);
            return;
        }

        closeSchematicDropdown();
        if (isEditingEventNameField()) {
            if (eventNameEditingNode == node) {
                return;
            }
            boolean changed = applyEventNameEdit();
            if (changed) {
                notifyNodeParametersChanged(eventNameEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopMessageEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);
        stopStickyNoteEditing(true);

        eventNameEditingNode = node;
        NodeParameter nameParam = node.getParameter("Name");
        eventNameEditBuffer = nameParam != null ? nameParam.getStringValue() : "";
        eventNameEditOriginalValue = eventNameEditBuffer;
        resetEventNameCaretBlink();
        eventNameCaretPosition = eventNameEditBuffer.length();
        eventNameSelectionAnchor = -1;
        eventNameSelectionStart = -1;
        eventNameSelectionEnd = -1;
    }

    public void stopEventNameEditing(boolean commit) {
        if (!isEditingEventNameField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyEventNameEdit();
        } else {
            revertEventNameEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(eventNameEditingNode);
        }

        eventNameEditingNode = null;
        eventNameEditBuffer = "";
        eventNameEditOriginalValue = "";
        eventNameCaretVisible = true;
        eventNameCaretPosition = 0;
        eventNameSelectionAnchor = -1;
        eventNameSelectionStart = -1;
        eventNameSelectionEnd = -1;
    }

    private boolean applyEventNameEdit() {
        if (!isEditingEventNameField()) {
            return false;
        }
        String value = eventNameEditBuffer == null ? "" : eventNameEditBuffer;
        NodeParameter nameParam = eventNameEditingNode.getParameter("Name");
        String previous = nameParam != null ? nameParam.getStringValue() : "";
        eventNameEditingNode.setParameterValueAndPropagate("Name", value);
        eventNameEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertEventNameEdit() {
        if (!isEditingEventNameField()) {
            return;
        }
        eventNameEditingNode.setParameterValueAndPropagate("Name", eventNameEditOriginalValue);
        eventNameEditingNode.recalculateDimensions();
    }

    public boolean handleEventNameKeyPressed(int keyCode, int modifiers) {
        if (!isEditingEventNameField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteEventNameSelection()) {
                    return true;
                }
                if (controlHeld && eventNameCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(eventNameEditBuffer, eventNameCaretPosition);
                    eventNameEditBuffer = eventNameEditBuffer.substring(0, deleteToPos)
                        + eventNameEditBuffer.substring(eventNameCaretPosition);
                    setEventNameCaretPosition(deleteToPos);
                } else if (eventNameCaretPosition > 0 && !eventNameEditBuffer.isEmpty()) {
                    eventNameEditBuffer = eventNameEditBuffer.substring(0, eventNameCaretPosition - 1)
                        + eventNameEditBuffer.substring(eventNameCaretPosition);
                    setEventNameCaretPosition(eventNameCaretPosition - 1);
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteEventNameSelection()) {
                    return true;
                }
                if (eventNameCaretPosition < eventNameEditBuffer.length()) {
                    eventNameEditBuffer = eventNameEditBuffer.substring(0, eventNameCaretPosition)
                        + eventNameEditBuffer.substring(eventNameCaretPosition + 1);
                    setEventNameCaretPosition(eventNameCaretPosition);
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveEventNameCaretTo(eventNameCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveEventNameCaretTo(eventNameCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveEventNameCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveEventNameCaretTo(eventNameEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopEventNameEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopEventNameEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllEventNameText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyEventNameSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutEventNameSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertEventNameText(getClipboardText());
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleEventNameCharTyped(char chr, int modifiers) {
        if (!isEditingEventNameField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertEventNameText(String.valueOf(chr));
    }

    private boolean applyMessageEdit() {
        if (!isEditingMessageField()) {
            return false;
        }
        String value = messageEditBuffer == null ? "" : messageEditBuffer;
        String previous = messageEditingNode.getMessageLine(messageEditingIndex);
        messageEditingNode.setMessageLine(messageEditingIndex, value);
        messageEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertMessageEdit() {
        if (!isEditingMessageField()) {
            return;
        }
        messageEditingNode.setMessageLine(messageEditingIndex, messageEditOriginalValue);
        messageEditingNode.recalculateDimensions();
    }

    public boolean isEditingParameterField() {
        return parameterEditingNode != null && parameterEditingIndex >= 0;
    }

    private void updateParameterCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - parameterCaretLastToggleTime >= COORDINATE_CARET_BLINK_INTERVAL_MS) {
            parameterCaretVisible = !parameterCaretVisible;
            parameterCaretLastToggleTime = now;
        }
    }

    private void resetParameterCaretBlink() {
        parameterCaretVisible = true;
        parameterCaretLastToggleTime = System.currentTimeMillis();
    }

    public void startParameterEditing(Node node, int index) {
        if (node == null || !rendersInlineParameters(node) || node.hasPopupEditButton()
            || index < 0 || index >= node.getParameters().size()) {
            stopParameterEditing(false);
            return;
        }

        closeModeDropdown();
        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeAmountSignDropdown();
        closeRandomRoundingDropdown();
        if (isEditingParameterField()) {
            if (parameterEditingNode == node && parameterEditingIndex == index) {
                clearParameterDropdownSuppression();
                return;
            }
            boolean changed = applyParameterEdit();
            if (changed) {
                notifyNodeParametersChanged(parameterEditingNode);
            }
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopMessageEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopStickyNoteEditing(true);

        parameterEditingNode = node;
        parameterEditingIndex = index;
        NodeParameter parameter = node.getParameters().get(index);
        String originalValue = parameter != null ? parameter.getStringValue() : "";
        parameterEditBuffer = originalValue;
        if (parameter != null && (isPlayerParameter(node, parameter)
            || isMessageParameter(node, parameter)
            || isSeedParameter(node, parameter)
            || isAmountParameter(node, parameter)
            || isTradeInlineParameter(node, parameter)
            || isMouseButtonParameter(node, parameter)
            || isGuiParameter(node, parameter)
            || isDirectionParameter(node, index)
            || isAttributeDetectionBooleanValueParameter(node, index)
            || isBlockFaceParameter(node, index)
            || isBlockItemParameter(node, index)
            || isFabricEventSensorParameter(node, index))) {
            if (parameterEditBuffer == null || parameterEditBuffer.isEmpty()
                || "Any".equalsIgnoreCase(parameterEditBuffer)
                || "Self".equalsIgnoreCase(parameterEditBuffer)
                || "Any State".equalsIgnoreCase(parameterEditBuffer)
                || "North".equalsIgnoreCase(parameterEditBuffer)
                || "True".equalsIgnoreCase(parameterEditBuffer)
                || isDefaultMouseButtonValue(parameterEditBuffer)
                || "0".equals(parameterEditBuffer)
                || (isTradeInlineParameter(node, parameter) && "1".equals(parameterEditBuffer))) {
                parameterEditBuffer = "";
            }
        }
        parameterEditOriginalValue = originalValue != null ? originalValue : "";
        resetParameterCaretBlink();
        parameterCaretPosition = parameterEditBuffer.length();
        parameterSelectionAnchor = -1;
        parameterSelectionStart = -1;
        parameterSelectionEnd = -1;
        clearParameterDropdownSuppression();
        updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
    }

    public void stopParameterEditing(boolean commit) {
        if (!isEditingParameterField()) {
            return;
        }

        boolean changed = false;
        if (commit) {
            changed = applyParameterEdit();
        } else {
            revertParameterEdit();
        }

        if (commit && changed) {
            notifyNodeParametersChanged(parameterEditingNode);
        }

        updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), -1, null);
        parameterEditingNode = null;
        parameterEditingIndex = -1;
        parameterEditBuffer = "";
        parameterEditOriginalValue = "";
        parameterCaretVisible = true;
        parameterCaretPosition = 0;
        parameterSelectionAnchor = -1;
        parameterSelectionStart = -1;
        parameterSelectionEnd = -1;
        closeParameterDropdown();
        clearParameterDropdownSuppression();
    }

    private boolean applyParameterEdit() {
        if (!isEditingParameterField()) {
            return false;
        }
        if (parameterEditingIndex < 0 || parameterEditingIndex >= parameterEditingNode.getParameters().size()) {
            return false;
        }
        NodeParameter parameter = parameterEditingNode.getParameters().get(parameterEditingIndex);
        String value = parameterEditBuffer == null ? "" : parameterEditBuffer;
        String previous = parameter != null ? parameter.getStringValue() : "";
        String appliedValue = value;
        if (parameter != null) {
            boolean isPlayerParam = isPlayerParameter(parameterEditingNode, parameter);
            boolean isDirectionParam = isDirectionParameter(parameterEditingNode, parameterEditingIndex);
            boolean isAnyLikeParam = isSeedParameter(parameterEditingNode, parameter)
                || isGuiParameter(parameterEditingNode, parameter)
                || isFabricEventSensorParameter(parameterEditingNode, parameterEditingIndex);
            boolean isBlockFaceParam = isBlockFaceParameter(parameterEditingNode, parameterEditingIndex);
            boolean isBooleanLiteralParam = isBooleanLiteralParameter(parameterEditingNode, parameterEditingIndex);
            boolean isAttributeDetectionAttributeParam = isAttributeDetectionAttributeParameter(parameterEditingNode, parameterEditingIndex);
            boolean isAttributeDetectionBooleanValueParam = isAttributeDetectionBooleanValueParameter(parameterEditingNode, parameterEditingIndex);
            boolean isMouseButtonParam = isMouseButtonParameter(parameterEditingNode, parameter);
            boolean isAmountParam = isAmountParameter(parameterEditingNode, parameter);
            boolean isTradeInlineParam = isTradeInlineParameter(parameterEditingNode, parameter);
            boolean isBlockItemParam = isBlockItemParameter(parameterEditingNode, parameterEditingIndex);
            if (isAmountParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    appliedValue = "0";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(value);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
                }
            } else if (isTradeInlineParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty() || "1".equals(trimmed)) {
                    appliedValue = "1";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(value);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
                }
            } else if (isPlayerParam) {
                String trimmed = value.trim();
                boolean isDefaultSelf = trimmed.isEmpty() || "Self".equalsIgnoreCase(trimmed);
                if (isDefaultSelf) {
                    appliedValue = "Self";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(value);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
                }
            } else if (isMouseButtonParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty() || isDefaultMouseButtonValue(trimmed)) {
                    appliedValue = "Left";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(value);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
                }
            } else if (isBlockFaceParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty() || "North".equalsIgnoreCase(trimmed) || "north".equalsIgnoreCase(trimmed)) {
                    appliedValue = "north";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(trimmed.toLowerCase(Locale.ROOT));
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), trimmed.toLowerCase(Locale.ROOT));
                }
            } else if (isDirectionParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    appliedValue = "north";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(trimmed.toLowerCase(Locale.ROOT));
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), trimmed.toLowerCase(Locale.ROOT));
                }
            } else if (isBooleanLiteralParam) {
                String trimmed = value.trim();
                if (trimmed.isEmpty() || "true".equalsIgnoreCase(trimmed)) {
                    appliedValue = "true";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else if ("1".equals(trimmed)) {
                    appliedValue = "true";
                    parameter.setStringValueFromUser(appliedValue);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else if ("0".equals(trimmed)) {
                    appliedValue = "false";
                    parameter.setStringValueFromUser(appliedValue);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    String normalized = trimmed.toLowerCase(Locale.ROOT);
                    parameter.setStringValueFromUser(normalized);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), normalized);
                }
            } else if (isAttributeDetectionAttributeParam) {
                String trimmed = value.trim();
                AttributeDetectionConfig.TargetKind targetKind = getAttributeDetectionTargetKind(parameterEditingNode);
                AttributeDetectionConfig.AttributeOption attribute = AttributeDetectionConfig.getAttribute(trimmed);
                if (attribute == null || (targetKind != null && !attribute.supports(targetKind))) {
                    attribute = AttributeDetectionConfig.getDefaultAttribute(targetKind);
                    appliedValue = attribute.id();
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    appliedValue = attribute.id();
                    parameter.setStringValueFromUser(appliedValue);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                }
            } else if (isAttributeDetectionBooleanValueParam) {
                String trimmed = value.trim();
                String normalized = (trimmed.isEmpty() || "true".equalsIgnoreCase(trimmed) || "1".equals(trimmed))
                    ? "true"
                    : ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed) ? "false" : trimmed.toLowerCase(Locale.ROOT));
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    normalized = "true";
                }
                appliedValue = normalized;
                if ("true".equals(normalized) && trimmed.isEmpty()) {
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                } else {
                    parameter.setStringValueFromUser(appliedValue);
                }
                parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
            } else if (isAnyLikeParam || isBlockItemParam) {
                String trimmed = value.trim();
                boolean isEmptyOrAny = trimmed.isEmpty()
                    || "Any".equalsIgnoreCase(trimmed)
                    || "Any State".equalsIgnoreCase(trimmed);
                if (isEmptyOrAny) {
                    appliedValue = (isBlockItemParam && (isBlockStateParameter(parameterEditingNode, parameterEditingIndex)
                        || isEntityStateParameter(parameterEditingNode, parameterEditingIndex)))
                        ? "Any State"
                        : "Any";
                    parameter.setStringValue(appliedValue);
                    parameter.setUserEdited(false);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), appliedValue);
                } else {
                    parameter.setStringValueFromUser(value);
                    parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
                }
            } else {
                parameter.setStringValueFromUser(value);
                parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
            }
        }
        parameterEditingNode.recalculateDimensions();
        return !Objects.equals(previous, appliedValue);
    }

    private void refreshStateParameterPreview() {
        if (!isEditingParameterField() || parameterEditingNode == null) {
            return;
        }
        if (parameterEditingIndex < 0 || parameterEditingIndex >= parameterEditingNode.getParameters().size()) {
            return;
        }
        if (!isBlockParameter(parameterEditingNode, parameterEditingIndex)
            && !isEntityParameter(parameterEditingNode, parameterEditingIndex)) {
            return;
        }
        NodeParameter parameter = parameterEditingNode.getParameters().get(parameterEditingIndex);
        if (parameter == null) {
            return;
        }
        String value = parameterEditBuffer == null ? "" : parameterEditBuffer;
        parameter.setStringValueFromUser(value);
        parameterEditingNode.recalculateDimensions();
    }

    private boolean isPlayerParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_PLAYER) {
            return false;
        }
        return "Player".equalsIgnoreCase(parameter.getName());
    }

    private boolean isMessageParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_MESSAGE) {
            return false;
        }
        return "Text".equalsIgnoreCase(parameter.getName()) || "Message".equalsIgnoreCase(parameter.getName());
    }

    private boolean isSeedParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.OPERATOR_RANDOM) {
            return false;
        }
        return "Seed".equalsIgnoreCase(parameter.getName());
    }

    private boolean isAmountParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_AMOUNT) {
            return false;
        }
        return "Amount".equalsIgnoreCase(parameter.getName());
    }

    private boolean isTradeInlineParameter(Node node, NodeParameter parameter) {
        if (node == null || parameter == null || node.getType() != NodeType.TRADE) {
            return false;
        }
        return "Count".equalsIgnoreCase(parameter.getName());
    }

    private boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing) {
        if (!isTradeInlineParameter(node, parameter)) {
            return false;
        }
        String value = parameter.getStringValue();
        if (editing && isEditingParameterField() && parameterEditingNode == node && parameterEditingIndex >= 0
            && parameterEditingIndex < node.getParameters().size() && node.getParameters().get(parameterEditingIndex) == parameter) {
            value = parameterEditBuffer;
        }
        return value == null || value.isEmpty() || (!parameter.isUserEdited() && "1".equals(value));
    }

    private boolean isGuiParameter(Node node, NodeParameter parameter) {
        if (node == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_GUI) {
            return false;
        }
        if (parameter != null) {
            return "GUI".equalsIgnoreCase(parameter.getName());
        }
        NodeParameter guiParam = node.getParameter("GUI");
        return guiParam != null;
    }

    private boolean isMouseButtonParameter(Node node, NodeParameter parameter) {
        if (node == null) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_MOUSE_BUTTON) {
            return false;
        }
        if (parameter != null) {
            return "MouseButton".equalsIgnoreCase(parameter.getName());
        }
        NodeParameter mouseButtonParam = node.getParameter("MouseButton");
        return mouseButtonParam != null;
    }

    private boolean isDefaultMouseButtonValue(String value) {
        return value == null
            || value.isEmpty()
            || "GLFW_MOUSE_BUTTON_LEFT".equalsIgnoreCase(value)
            || "LEFT".equalsIgnoreCase(value);
    }

    private String formatMouseButtonValue(String value) {
        if (value == null || value.isEmpty()) {
            return "Left";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "GLFW_MOUSE_BUTTON_LEFT", "LEFT" -> "Left";
            case "GLFW_MOUSE_BUTTON_RIGHT", "RIGHT" -> "Right";
            case "GLFW_MOUSE_BUTTON_MIDDLE", "MIDDLE" -> "Middle";
            case "GLFW_MOUSE_BUTTON_4", "BUTTON_4" -> "Button 4";
            case "GLFW_MOUSE_BUTTON_5", "BUTTON_5" -> "Button 5";
            case "GLFW_MOUSE_BUTTON_6", "BUTTON_6" -> "Button 6";
            case "GLFW_MOUSE_BUTTON_7", "BUTTON_7" -> "Button 7";
            case "GLFW_MOUSE_BUTTON_8", "BUTTON_8" -> "Button 8";
            default -> value;
        };
    }

    private boolean isDirectionParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_DIRECTION) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Direction".equalsIgnoreCase(param.getName());
    }

    private boolean isBooleanLiteralParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_BOOLEAN || !node.isBooleanModeLiteral()) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Toggle".equalsIgnoreCase(param.getName());
    }

    private boolean isAttributeDetectionAttributeParameter(Node node, int index) {
        if (node == null || !node.isAttributeDetectionSensor() || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null && "Attribute".equalsIgnoreCase(param.getName());
    }

    private boolean isAttributeDetectionBooleanValueParameter(Node node, int index) {
        if (node == null || !node.isAttributeDetectionSensor() || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null || !"Value".equalsIgnoreCase(param.getName())) {
            return false;
        }
        AttributeDetectionConfig.AttributeOption attribute =
            AttributeDetectionConfig.getAttribute(node.getParameter("Attribute") != null
                ? node.getParameter("Attribute").getStringValue()
                : "");
        return attribute != null && attribute.valueType() == AttributeDetectionConfig.ValueType.BOOLEAN;
    }

    private boolean isAttributeDetectionDropdownParameter(Node node, int index) {
        return isAttributeDetectionAttributeParameter(node, index)
            || isAttributeDetectionBooleanValueParameter(node, index);
    }

    private boolean isBlockFaceParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.PARAM_BLOCK_FACE) {
            return false;
        }
        if (index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Face".equalsIgnoreCase(param.getName());
    }

    private void revertParameterEdit() {
        if (!isEditingParameterField()) {
            return;
        }
        if (parameterEditingIndex < 0 || parameterEditingIndex >= parameterEditingNode.getParameters().size()) {
            return;
        }
        NodeParameter parameter = parameterEditingNode.getParameters().get(parameterEditingIndex);
        if (parameter != null) {
            parameter.setStringValue(parameterEditOriginalValue);
            parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), parameterEditOriginalValue);
        }
        parameterEditingNode.recalculateDimensions();
    }

    public boolean handleParameterKeyPressed(int keyCode, int modifiers) {
        if (!isEditingParameterField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteParameterSelection()) {
                    return true;
                }
                if (controlHeld && parameterCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(parameterEditBuffer, parameterCaretPosition);
                    parameterEditBuffer = parameterEditBuffer.substring(0, deleteToPos)
                        + parameterEditBuffer.substring(parameterCaretPosition);
                    setParameterCaretPosition(deleteToPos);
                    updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
                    refreshStateParameterPreview();
                    clearParameterDropdownSuppression();
                } else if (parameterCaretPosition > 0 && !parameterEditBuffer.isEmpty()) {
                    parameterEditBuffer = parameterEditBuffer.substring(0, parameterCaretPosition - 1)
                        + parameterEditBuffer.substring(parameterCaretPosition);
                    setParameterCaretPosition(parameterCaretPosition - 1);
                    updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
                    refreshStateParameterPreview();
                    clearParameterDropdownSuppression();
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteParameterSelection()) {
                    return true;
                }
                if (parameterCaretPosition < parameterEditBuffer.length()) {
                    parameterEditBuffer = parameterEditBuffer.substring(0, parameterCaretPosition)
                        + parameterEditBuffer.substring(parameterCaretPosition + 1);
                    setParameterCaretPosition(parameterCaretPosition);
                    updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
                    refreshStateParameterPreview();
                    clearParameterDropdownSuppression();
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveParameterCaretTo(parameterCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveParameterCaretTo(parameterCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveParameterCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveParameterCaretTo(parameterEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopParameterEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopParameterEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllParameterText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyParameterSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutParameterSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertParameterText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleParameterCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingParameterField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertParameterText(String.valueOf(chr), textRenderer);
    }

    public boolean handleMessageKeyPressed(int keyCode, int modifiers) {
        if (!isEditingMessageField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = isTextShortcutDown(modifiers);

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteMessageSelection()) {
                    return true;
                }
                if (controlHeld && messageCaretPosition > 0) {
                    // CTRL+Backspace: delete to previous word boundary
                    int deleteToPos = findPreviousWordBoundary(messageEditBuffer, messageCaretPosition);
                    messageEditBuffer = messageEditBuffer.substring(0, deleteToPos)
                        + messageEditBuffer.substring(messageCaretPosition);
                    setMessageCaretPosition(deleteToPos);
                    updateMessageFieldContentWidth(getClientTextRenderer());
                } else if (messageCaretPosition > 0 && !messageEditBuffer.isEmpty()) {
                    messageEditBuffer = messageEditBuffer.substring(0, messageCaretPosition - 1)
                        + messageEditBuffer.substring(messageCaretPosition);
                    setMessageCaretPosition(messageCaretPosition - 1);
                    updateMessageFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (deleteMessageSelection()) {
                    return true;
                }
                if (messageCaretPosition < messageEditBuffer.length()) {
                    messageEditBuffer = messageEditBuffer.substring(0, messageCaretPosition)
                        + messageEditBuffer.substring(messageCaretPosition + 1);
                    setMessageCaretPosition(messageCaretPosition);
                    updateMessageFieldContentWidth(getClientTextRenderer());
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                moveMessageCaretTo(messageCaretPosition - 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                moveMessageCaretTo(messageCaretPosition + 1, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveMessageCaretTo(0, shiftHeld);
                return true;
            case GLFW.GLFW_KEY_END:
                moveMessageCaretTo(messageEditBuffer.length(), shiftHeld);
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                stopMessageEditing(true);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                stopMessageEditing(true);
                return true;
            case GLFW.GLFW_KEY_A:
                if (controlHeld) {
                    selectAllMessageText();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (controlHeld) {
                    copyMessageSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (controlHeld) {
                    cutMessageSelection();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (controlHeld) {
                    TextRenderer textRenderer = getClientTextRenderer();
                    if (textRenderer != null) {
                        insertMessageText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleMessageCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingMessageField()) {
            return false;
        }
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        return insertMessageText(String.valueOf(chr), textRenderer);
    }

    private boolean hasCoordinateSelection() {
        return coordinateSelectionStart >= 0
            && coordinateSelectionEnd >= 0
            && coordinateSelectionStart != coordinateSelectionEnd;
    }

    private boolean hasAmountSelection() {
        return amountSelectionStart >= 0
            && amountSelectionEnd >= 0
            && amountSelectionStart != amountSelectionEnd;
    }

    private boolean hasStopTargetSelection() {
        return stopTargetSelectionStart >= 0
            && stopTargetSelectionEnd >= 0
            && stopTargetSelectionStart != stopTargetSelectionEnd;
    }

    private boolean hasVariableSelection() {
        return variableSelectionStart >= 0
            && variableSelectionEnd >= 0
            && variableSelectionStart != variableSelectionEnd;
    }

    private boolean hasMessageSelection() {
        return messageSelectionStart >= 0
            && messageSelectionEnd >= 0
            && messageSelectionStart != messageSelectionEnd;
    }

    private boolean hasEventNameSelection() {
        return eventNameSelectionStart >= 0
            && eventNameSelectionEnd >= 0
            && eventNameSelectionStart != eventNameSelectionEnd;
    }

    private boolean hasParameterSelection() {
        return parameterSelectionStart >= 0
            && parameterSelectionEnd >= 0
            && parameterSelectionStart != parameterSelectionEnd;
    }

    private void resetCoordinateSelectionRange() {
        coordinateSelectionStart = -1;
        coordinateSelectionEnd = -1;
    }

    private void resetAmountSelectionRange() {
        amountSelectionStart = -1;
        amountSelectionEnd = -1;
    }

    private void resetStopTargetSelectionRange() {
        stopTargetSelectionStart = -1;
        stopTargetSelectionEnd = -1;
    }

    private void resetVariableSelectionRange() {
        variableSelectionStart = -1;
        variableSelectionEnd = -1;
    }

    private void resetMessageSelectionRange() {
        messageSelectionStart = -1;
        messageSelectionEnd = -1;
    }

    private void resetEventNameSelectionRange() {
        eventNameSelectionStart = -1;
        eventNameSelectionEnd = -1;
    }

    private void resetParameterSelectionRange() {
        parameterSelectionStart = -1;
        parameterSelectionEnd = -1;
    }

    private void setCoordinateCaretPosition(int position) {
        coordinateCaretPosition = MathHelper.clamp(position, 0, coordinateEditBuffer.length());
        coordinateSelectionAnchor = -1;
        resetCoordinateSelectionRange();
        resetCoordinateCaretBlink();
    }

    private void moveCoordinateCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, coordinateEditBuffer.length());
        if (extendSelection) {
            if (coordinateSelectionAnchor == -1) {
                coordinateSelectionAnchor = coordinateCaretPosition;
            }
            int start = Math.min(coordinateSelectionAnchor, position);
            int end = Math.max(coordinateSelectionAnchor, position);
            if (start == end) {
                resetCoordinateSelectionRange();
            } else {
                coordinateSelectionStart = start;
                coordinateSelectionEnd = end;
            }
        } else {
            coordinateSelectionAnchor = -1;
            resetCoordinateSelectionRange();
        }
        coordinateCaretPosition = position;
        resetCoordinateCaretBlink();
    }

    private void setParameterCaretPosition(int position) {
        parameterCaretPosition = MathHelper.clamp(position, 0, parameterEditBuffer.length());
        parameterSelectionAnchor = -1;
        resetParameterSelectionRange();
        resetParameterCaretBlink();
    }

    private void moveParameterCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, parameterEditBuffer.length());
        if (extendSelection) {
            if (parameterSelectionAnchor == -1) {
                parameterSelectionAnchor = parameterCaretPosition;
            }
            int start = Math.min(parameterSelectionAnchor, position);
            int end = Math.max(parameterSelectionAnchor, position);
            if (start == end) {
                resetParameterSelectionRange();
            } else {
                parameterSelectionStart = start;
                parameterSelectionEnd = end;
            }
        } else {
            parameterSelectionAnchor = -1;
            resetParameterSelectionRange();
        }
        parameterCaretPosition = position;
        resetParameterCaretBlink();
        clearParameterDropdownSuppression();
    }

    private boolean deleteCoordinateSelection() {
        if (!hasCoordinateSelection()) {
            return false;
        }
        coordinateEditBuffer = coordinateEditBuffer.substring(0, coordinateSelectionStart)
            + coordinateEditBuffer.substring(coordinateSelectionEnd);
        setCoordinateCaretPosition(coordinateSelectionStart);
        updateCoordinateFieldContentWidth(getClientTextRenderer());
        return true;
    }

    private void selectAllCoordinateText() {
        if (!isEditingCoordinateField()) {
            return;
        }
        coordinateSelectionAnchor = 0;
        if (coordinateEditBuffer.isEmpty()) {
            resetCoordinateSelectionRange();
        } else {
            coordinateSelectionStart = 0;
            coordinateSelectionEnd = coordinateEditBuffer.length();
        }
        coordinateCaretPosition = coordinateEditBuffer.length();
        resetCoordinateCaretBlink();
    }

    private void copyCoordinateSelection() {
        if (!hasCoordinateSelection()) {
            return;
        }
        setClipboardText(coordinateEditBuffer.substring(coordinateSelectionStart, coordinateSelectionEnd));
    }

    private void cutCoordinateSelection() {
        if (!hasCoordinateSelection()) {
            return;
        }
        copyCoordinateSelection();
        deleteCoordinateSelection();
    }

    private void smartPasteCoordinates(String clipboardText, TextRenderer textRenderer) {
        if (!isEditingCoordinateField() || textRenderer == null || clipboardText == null || clipboardText.isEmpty()) {
            return;
        }

        // Try to parse as multi-coordinate format (e.g., "2313 123 -32131" or "100,200,300")
        String[] parts = clipboardText.trim().split("[\\s,]+");
        String[] axes = getCoordinateAxes(coordinateEditingNode);
        if (parts.length == axes.length) {
            String[] parsedCoords = new String[axes.length];
            boolean allValid = true;

            for (int i = 0; i < axes.length; i++) {
                String trimmed = parts[i].trim();
                // Remove any non-digit/minus characters
                StringBuilder cleaned = new StringBuilder();
                for (int j = 0; j < trimmed.length(); j++) {
                    char c = trimmed.charAt(j);
                    if (Character.isDigit(c) || (c == '-' && j == 0)) {
                        cleaned.append(c);
                    }
                }

                String value = cleaned.toString();
                if (value.isEmpty() || !isValidCoordinateValue(value)) {
                    allValid = false;
                    break;
                }
                parsedCoords[i] = value;
            }

            // If we successfully parsed all three coordinates, apply them
            if (allValid) {
                // Save reference to node before stopping editing
                Node node = coordinateEditingNode;
                if (node == null) {
                    return;
                }

                // Stop current coordinate editing
                stopCoordinateEditing(false);

                for (int i = 0; i < axes.length; i++) {
                    node.setParameterValueAndPropagate(axes[i], parsedCoords[i]);
                }
                node.recalculateDimensions();
                notifyNodeParametersChanged(node);
                return;
            }
        }

        // Fall back to single-axis paste
        insertCoordinateText(clipboardText, textRenderer);
    }

    private boolean insertCoordinateText(String text, TextRenderer textRenderer) {
        if (!isEditingCoordinateField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }
        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String working = coordinateEditBuffer;
        int caret = coordinateCaretPosition;
        if (hasCoordinateSelection()) {
            int start = coordinateSelectionStart;
            int end = coordinateSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        coordinateEditBuffer = working.substring(0, caret) + filtered + working.substring(caret);
        setCoordinateCaretPosition(caret + filtered.length());
        updateCoordinateFieldContentWidth(textRenderer);
        return true;
    }

    private int findPreviousWordBoundary(String text, int fromPosition) {
        if (text == null || fromPosition <= 0) {
            return 0;
        }
        int pos = fromPosition - 1;

        // Skip any whitespace immediately before the caret
        while (pos > 0 && Character.isWhitespace(text.charAt(pos))) {
            pos--;
        }

        // If we're on alphanumeric, skip back to start of word
        if (pos >= 0 && Character.isLetterOrDigit(text.charAt(pos))) {
            while (pos > 0 && Character.isLetterOrDigit(text.charAt(pos - 1))) {
                pos--;
            }
        }
        // If we're on non-alphanumeric (like punctuation), skip similar characters
        else if (pos >= 0) {
            char startChar = text.charAt(pos);
            while (pos > 0 && !Character.isLetterOrDigit(text.charAt(pos - 1))
                   && !Character.isWhitespace(text.charAt(pos - 1))) {
                pos--;
            }
        }

        return pos;
    }

    private boolean isValidCoordinateValue(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if ("-".equals(value)) {
            return true;
        }
        int startIndex = 0;
        if (value.charAt(0) == '-') {
            startIndex = 1;
            if (startIndex >= value.length()) {
                return false;
            }
        }
        for (int i = startIndex; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void setAmountCaretPosition(int position) {
        amountCaretPosition = MathHelper.clamp(position, 0, amountEditBuffer.length());
        amountSelectionAnchor = -1;
        resetAmountSelectionRange();
        resetAmountCaretBlink();
    }

    private void moveAmountCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, amountEditBuffer.length());
        if (extendSelection) {
            if (amountSelectionAnchor == -1) {
                amountSelectionAnchor = amountCaretPosition;
            }
            int start = Math.min(amountSelectionAnchor, position);
            int end = Math.max(amountSelectionAnchor, position);
            if (start == end) {
                resetAmountSelectionRange();
            } else {
                amountSelectionStart = start;
                amountSelectionEnd = end;
            }
        } else {
            amountSelectionAnchor = -1;
            resetAmountSelectionRange();
        }
        amountCaretPosition = position;
        resetAmountCaretBlink();
    }

    private void setStopTargetCaretPosition(int position) {
        stopTargetCaretPosition = MathHelper.clamp(position, 0, stopTargetEditBuffer.length());
        stopTargetSelectionAnchor = -1;
        resetStopTargetSelectionRange();
        resetStopTargetCaretBlink();
    }

    private void moveStopTargetCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, stopTargetEditBuffer.length());
        if (extendSelection) {
            if (stopTargetSelectionAnchor == -1) {
                stopTargetSelectionAnchor = stopTargetCaretPosition;
            }
            int start = Math.min(stopTargetSelectionAnchor, position);
            int end = Math.max(stopTargetSelectionAnchor, position);
            if (start == end) {
                resetStopTargetSelectionRange();
            } else {
                stopTargetSelectionStart = start;
                stopTargetSelectionEnd = end;
            }
        } else {
            stopTargetSelectionAnchor = -1;
            resetStopTargetSelectionRange();
        }
        stopTargetCaretPosition = position;
        resetStopTargetCaretBlink();
    }

    private void setVariableCaretPosition(int position) {
        variableCaretPosition = MathHelper.clamp(position, 0, variableEditBuffer.length());
        variableSelectionAnchor = -1;
        resetVariableSelectionRange();
        resetVariableCaretBlink();
    }

    private void moveVariableCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, variableEditBuffer.length());
        if (extendSelection) {
            if (variableSelectionAnchor == -1) {
                variableSelectionAnchor = variableCaretPosition;
            }
            int start = Math.min(variableSelectionAnchor, position);
            int end = Math.max(variableSelectionAnchor, position);
            if (start == end) {
                resetVariableSelectionRange();
            } else {
                variableSelectionStart = start;
                variableSelectionEnd = end;
            }
        } else {
            variableSelectionAnchor = -1;
            resetVariableSelectionRange();
        }
        variableCaretPosition = position;
        resetVariableCaretBlink();
    }

    private void setMessageCaretPosition(int position) {
        messageCaretPosition = MathHelper.clamp(position, 0, messageEditBuffer.length());
        messageSelectionAnchor = -1;
        resetMessageSelectionRange();
        resetMessageCaretBlink();
    }

    private void setEventNameCaretPosition(int position) {
        eventNameCaretPosition = MathHelper.clamp(position, 0, eventNameEditBuffer.length());
        eventNameSelectionAnchor = -1;
        resetEventNameSelectionRange();
        resetEventNameCaretBlink();
    }

    private void moveMessageCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, messageEditBuffer.length());
        if (extendSelection) {
            if (messageSelectionAnchor == -1) {
                messageSelectionAnchor = messageCaretPosition;
            }
            int start = Math.min(messageSelectionAnchor, position);
            int end = Math.max(messageSelectionAnchor, position);
            if (start == end) {
                resetMessageSelectionRange();
            } else {
                messageSelectionStart = start;
                messageSelectionEnd = end;
            }
        } else {
            messageSelectionAnchor = -1;
            resetMessageSelectionRange();
        }
        messageCaretPosition = position;
        resetMessageCaretBlink();
    }

    private void moveEventNameCaretTo(int position, boolean extendSelection) {
        position = MathHelper.clamp(position, 0, eventNameEditBuffer.length());
        if (extendSelection) {
            if (eventNameSelectionAnchor == -1) {
                eventNameSelectionAnchor = eventNameCaretPosition;
            }
            int start = Math.min(eventNameSelectionAnchor, position);
            int end = Math.max(eventNameSelectionAnchor, position);
            if (start == end) {
                resetEventNameSelectionRange();
            } else {
                eventNameSelectionStart = start;
                eventNameSelectionEnd = end;
            }
        } else {
            eventNameSelectionAnchor = -1;
            resetEventNameSelectionRange();
        }
        eventNameCaretPosition = position;
        resetEventNameCaretBlink();
    }

    private boolean deleteAmountSelection() {
        if (!hasAmountSelection()) {
            return false;
        }
        amountEditBuffer = amountEditBuffer.substring(0, amountSelectionStart)
            + amountEditBuffer.substring(amountSelectionEnd);
        setAmountCaretPosition(amountSelectionStart);
        updateAmountFieldContentWidth(getClientTextRenderer());
        return true;
    }

    private boolean deleteStopTargetSelection() {
        if (!hasStopTargetSelection()) {
            return false;
        }
        stopTargetEditBuffer = stopTargetEditBuffer.substring(0, stopTargetSelectionStart)
            + stopTargetEditBuffer.substring(stopTargetSelectionEnd);
        setStopTargetCaretPosition(stopTargetSelectionStart);
        updateStopTargetFieldContentWidth(getClientTextRenderer());
        return true;
    }

    private boolean deleteVariableSelection() {
        if (!hasVariableSelection()) {
            return false;
        }
        variableEditBuffer = variableEditBuffer.substring(0, variableSelectionStart)
            + variableEditBuffer.substring(variableSelectionEnd);
        setVariableCaretPosition(variableSelectionStart);
        updateVariableFieldContentWidth(getClientTextRenderer());
        return true;
    }

    private void selectAllAmountText() {
        if (!isEditingAmountField()) {
            return;
        }
        amountSelectionAnchor = 0;
        if (amountEditBuffer.isEmpty()) {
            resetAmountSelectionRange();
        } else {
            amountSelectionStart = 0;
            amountSelectionEnd = amountEditBuffer.length();
        }
        amountCaretPosition = amountEditBuffer.length();
        resetAmountCaretBlink();
    }

    private void selectAllStopTargetText() {
        if (!isEditingStopTargetField()) {
            return;
        }
        stopTargetSelectionAnchor = 0;
        if (stopTargetEditBuffer.isEmpty()) {
            resetStopTargetSelectionRange();
        } else {
            stopTargetSelectionStart = 0;
            stopTargetSelectionEnd = stopTargetEditBuffer.length();
        }
        stopTargetCaretPosition = stopTargetEditBuffer.length();
        resetStopTargetCaretBlink();
    }

    private void selectAllVariableText() {
        if (!isEditingVariableField()) {
            return;
        }
        variableSelectionAnchor = 0;
        if (variableEditBuffer.isEmpty()) {
            resetVariableSelectionRange();
        } else {
            variableSelectionStart = 0;
            variableSelectionEnd = variableEditBuffer.length();
        }
        variableCaretPosition = variableEditBuffer.length();
        resetVariableCaretBlink();
    }

    private void copyAmountSelection() {
        if (!hasAmountSelection()) {
            return;
        }
        setClipboardText(amountEditBuffer.substring(amountSelectionStart, amountSelectionEnd));
    }

    private void copyStopTargetSelection() {
        if (!hasStopTargetSelection()) {
            return;
        }
        setClipboardText(stopTargetEditBuffer.substring(stopTargetSelectionStart, stopTargetSelectionEnd));
    }

    private void copyVariableSelection() {
        if (!hasVariableSelection()) {
            return;
        }
        setClipboardText(variableEditBuffer.substring(variableSelectionStart, variableSelectionEnd));
    }

    private void cutAmountSelection() {
        if (!hasAmountSelection()) {
            return;
        }
        copyAmountSelection();
        deleteAmountSelection();
    }

    private void cutStopTargetSelection() {
        if (!hasStopTargetSelection()) {
            return;
        }
        copyStopTargetSelection();
        deleteStopTargetSelection();
    }

    private void cutVariableSelection() {
        if (!hasVariableSelection()) {
            return;
        }
        copyVariableSelection();
        deleteVariableSelection();
    }

    private boolean deleteMessageSelection() {
        if (!hasMessageSelection()) {
            return false;
        }
        messageEditBuffer = messageEditBuffer.substring(0, messageSelectionStart)
            + messageEditBuffer.substring(messageSelectionEnd);
        setMessageCaretPosition(messageSelectionStart);
        updateMessageFieldContentWidth(getClientTextRenderer());
        return true;
    }

    private boolean deleteEventNameSelection() {
        if (!hasEventNameSelection()) {
            return false;
        }
        eventNameEditBuffer = eventNameEditBuffer.substring(0, eventNameSelectionStart)
            + eventNameEditBuffer.substring(eventNameSelectionEnd);
        setEventNameCaretPosition(eventNameSelectionStart);
        return true;
    }

    private boolean deleteParameterSelection() {
        if (!hasParameterSelection()) {
            return false;
        }
        parameterEditBuffer = parameterEditBuffer.substring(0, parameterSelectionStart)
            + parameterEditBuffer.substring(parameterSelectionEnd);
        setParameterCaretPosition(parameterSelectionStart);
        updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
        refreshStateParameterPreview();
        clearParameterDropdownSuppression();
        return true;
    }

    private void selectAllMessageText() {
        if (!isEditingMessageField()) {
            return;
        }
        messageSelectionAnchor = 0;
        if (messageEditBuffer.isEmpty()) {
            resetMessageSelectionRange();
        } else {
            messageSelectionStart = 0;
            messageSelectionEnd = messageEditBuffer.length();
        }
        messageCaretPosition = messageEditBuffer.length();
        resetMessageCaretBlink();
    }

    private void selectAllEventNameText() {
        if (!isEditingEventNameField()) {
            return;
        }
        eventNameSelectionAnchor = 0;
        if (eventNameEditBuffer.isEmpty()) {
            resetEventNameSelectionRange();
        } else {
            eventNameSelectionStart = 0;
            eventNameSelectionEnd = eventNameEditBuffer.length();
        }
        eventNameCaretPosition = eventNameEditBuffer.length();
        resetEventNameCaretBlink();
    }

    private void selectAllParameterText() {
        if (!isEditingParameterField()) {
            return;
        }
        parameterSelectionAnchor = 0;
        if (parameterEditBuffer.isEmpty()) {
            resetParameterSelectionRange();
        } else {
            parameterSelectionStart = 0;
            parameterSelectionEnd = parameterEditBuffer.length();
        }
        parameterCaretPosition = parameterEditBuffer.length();
        resetParameterCaretBlink();
    }

    private void copyMessageSelection() {
        if (!hasMessageSelection()) {
            return;
        }
        setClipboardText(messageEditBuffer.substring(messageSelectionStart, messageSelectionEnd));
    }

    private void copyEventNameSelection() {
        if (!hasEventNameSelection()) {
            return;
        }
        setClipboardText(eventNameEditBuffer.substring(eventNameSelectionStart, eventNameSelectionEnd));
    }

    private void copyParameterSelection() {
        if (!hasParameterSelection()) {
            return;
        }
        setClipboardText(parameterEditBuffer.substring(parameterSelectionStart, parameterSelectionEnd));
    }

    private void cutMessageSelection() {
        if (!hasMessageSelection()) {
            return;
        }
        copyMessageSelection();
        deleteMessageSelection();
    }

    private void cutEventNameSelection() {
        if (!hasEventNameSelection()) {
            return;
        }
        copyEventNameSelection();
        deleteEventNameSelection();
    }

    private void cutParameterSelection() {
        if (!hasParameterSelection()) {
            return;
        }
        copyParameterSelection();
        deleteParameterSelection();
    }

    private boolean insertAmountText(String text, TextRenderer textRenderer) {
        if (!isEditingAmountField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }
        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String working = amountEditBuffer;
        int caret = amountCaretPosition;
        if (hasAmountSelection()) {
            int start = amountSelectionStart;
            int end = amountSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        amountEditBuffer = working.substring(0, caret) + filtered + working.substring(caret);
        setAmountCaretPosition(caret + filtered.length());
        updateAmountFieldContentWidth(textRenderer);
        return true;
    }

    private boolean isValidSignedAmountInput(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        int index = 0;
        if (value.charAt(0) == '-') {
            if (value.length() == 1) {
                return true;
            }
            index = 1;
        }
        boolean dotSeen = false;
        boolean digitSeen = false;
        for (; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '.') {
                if (dotSeen) {
                    return false;
                }
                dotSeen = true;
            } else if (c >= '0' && c <= '9') {
                digitSeen = true;
            } else {
                return false;
            }
        }
        return digitSeen || dotSeen;
    }

    private boolean isMoveItemAllAmountValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "0".equals(trimmed)
            || "all".equalsIgnoreCase(trimmed)
            || "any".equalsIgnoreCase(trimmed);
    }

    private boolean insertStopTargetText(String text, TextRenderer textRenderer) {
        if (!isEditingStopTargetField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }
        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String working = stopTargetEditBuffer;
        int caret = stopTargetCaretPosition;
        if (hasStopTargetSelection()) {
            int start = stopTargetSelectionStart;
            int end = stopTargetSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        stopTargetEditBuffer = working.substring(0, caret) + filtered + working.substring(caret);
        setStopTargetCaretPosition(caret + filtered.length());
        updateStopTargetFieldContentWidth(textRenderer);
        return true;
    }

    private boolean insertVariableText(String text, TextRenderer textRenderer) {
        if (!isEditingVariableField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }

        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String originalBuffer = variableEditBuffer;
        int originalCaret = variableCaretPosition;
        int originalSelectionStart = variableSelectionStart;
        int originalSelectionEnd = variableSelectionEnd;
        int originalSelectionAnchor = variableSelectionAnchor;

        String working = variableEditBuffer;
        int caret = variableCaretPosition;

        if (hasVariableSelection()) {
            int start = variableSelectionStart;
            int end = variableSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        boolean inserted = false;
        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            String candidate = working.substring(0, caret) + c + working.substring(caret);
            working = candidate;
            caret++;
            inserted = true;
        }

        if (inserted) {
            variableEditBuffer = working;
            setVariableCaretPosition(caret);
            updateVariableFieldContentWidth(textRenderer);
            return true;
        }

        variableEditBuffer = originalBuffer;
        variableCaretPosition = originalCaret;
        variableSelectionStart = originalSelectionStart;
        variableSelectionEnd = originalSelectionEnd;
        variableSelectionAnchor = originalSelectionAnchor;
        return false;
    }

    private boolean insertEventNameText(String text) {
        if (!isEditingEventNameField() || text == null || text.isEmpty()) {
            return false;
        }
        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String originalBuffer = eventNameEditBuffer;
        int originalCaret = eventNameCaretPosition;
        int originalSelectionStart = eventNameSelectionStart;
        int originalSelectionEnd = eventNameSelectionEnd;
        int originalSelectionAnchor = eventNameSelectionAnchor;

        String working = eventNameEditBuffer;
        int caret = eventNameCaretPosition;

        if (hasEventNameSelection()) {
            int start = eventNameSelectionStart;
            int end = eventNameSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        boolean inserted = false;
        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            working = working.substring(0, caret) + c + working.substring(caret);
            caret++;
            inserted = true;
        }

        if (inserted) {
            eventNameEditBuffer = working;
            setEventNameCaretPosition(caret);
            return true;
        }

        eventNameEditBuffer = originalBuffer;
        eventNameCaretPosition = originalCaret;
        eventNameSelectionStart = originalSelectionStart;
        eventNameSelectionEnd = originalSelectionEnd;
        eventNameSelectionAnchor = originalSelectionAnchor;
        return false;
    }

    private boolean insertMessageText(String text, TextRenderer textRenderer) {
        if (!isEditingMessageField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }

        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String originalBuffer = messageEditBuffer;
        int originalCaret = messageCaretPosition;
        int originalSelectionStart = messageSelectionStart;
        int originalSelectionEnd = messageSelectionEnd;
        int originalSelectionAnchor = messageSelectionAnchor;

        String working = messageEditBuffer;
        int caret = messageCaretPosition;

        if (hasMessageSelection()) {
            int start = messageSelectionStart;
            int end = messageSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        boolean inserted = false;
        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            String candidate = working.substring(0, caret) + c + working.substring(caret);
            working = candidate;
            caret++;
            inserted = true;
        }

        if (inserted) {
            messageEditBuffer = working;
            setMessageCaretPosition(caret);
            updateMessageFieldContentWidth(textRenderer);
            return true;
        }

        messageEditBuffer = originalBuffer;
        messageCaretPosition = originalCaret;
        messageSelectionStart = originalSelectionStart;
        messageSelectionEnd = originalSelectionEnd;
        messageSelectionAnchor = originalSelectionAnchor;
        return false;
    }

    private boolean insertParameterText(String text, TextRenderer textRenderer) {
        if (!isEditingParameterField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }

        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) {
            return false;
        }

        String originalBuffer = parameterEditBuffer;
        int originalCaret = parameterCaretPosition;
        int originalSelectionStart = parameterSelectionStart;
        int originalSelectionEnd = parameterSelectionEnd;
        int originalSelectionAnchor = parameterSelectionAnchor;

        String working = parameterEditBuffer;
        int caret = parameterCaretPosition;

        if (hasParameterSelection()) {
            int start = parameterSelectionStart;
            int end = parameterSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        boolean inserted = false;

        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            String candidate = working.substring(0, caret) + c + working.substring(caret);
            working = candidate;
            caret++;
            inserted = true;
        }

        if (inserted) {
            parameterEditBuffer = working;
            setParameterCaretPosition(caret);
            updateParameterFieldContentWidth(parameterEditingNode, textRenderer, parameterEditingIndex, parameterEditBuffer);
            refreshStateParameterPreview();
            clearParameterDropdownSuppression();
            return true;
        }

        parameterEditBuffer = originalBuffer;
        parameterCaretPosition = originalCaret;
        parameterSelectionStart = originalSelectionStart;
        parameterSelectionEnd = originalSelectionEnd;
        parameterSelectionAnchor = originalSelectionAnchor;
        return false;
    }

    private boolean isListIndexParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.LIST_ITEM || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        return param != null && "Index".equalsIgnoreCase(param.getName());
    }

    private boolean isBlockItemParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BLOCK_TYPE) {
            return true;
        }
        String name = param.getName();
        return isBlockStateParameter(node, index)
            || isEntityStateParameter(node, index)
            || "Block".equalsIgnoreCase(name)
            || "Blocks".equalsIgnoreCase(name)
            || "Item".equalsIgnoreCase(name)
            || "Entity".equalsIgnoreCase(name);
    }

    private boolean isBlockStateParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_BLOCK) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "State".equalsIgnoreCase(param.getName());
    }

    private boolean isEntityStateParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        if (node.getType() != NodeType.PARAM_ENTITY) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "State".equalsIgnoreCase(param.getName());
    }

    private boolean isAnyBlockItemValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "Any".equalsIgnoreCase(trimmed)
            || "Any State".equalsIgnoreCase(trimmed);
    }

    private String getNormalizedBlockIdForStateOptions(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_BLOCK) {
            return "";
        }
        NodeParameter blockParam = node.getParameter("Block");
        if (blockParam == null) {
            return "";
        }
        String raw = blockParam.getStringValue();
        if (raw == null) {
            return "";
        }
        int comma = raw.indexOf(',');
        if (comma >= 0) {
            raw = raw.substring(0, comma);
        }
        String stripped = BlockSelection.stripState(raw);
        if (stripped == null || stripped.isEmpty()) {
            return "";
        }
        String fullId = stripped.contains(":") ? stripped : "minecraft:" + stripped;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    private String getNormalizedEntityIdForStateOptions(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_ENTITY) {
            return "";
        }
        NodeParameter entityParam = node.getParameter("Entity");
        if (entityParam == null) {
            return "";
        }
        String raw = entityParam.getStringValue();
        if (raw == null) {
            return "";
        }
        int comma = raw.indexOf(',');
        if (comma >= 0) {
            raw = raw.substring(0, comma);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String sanitized = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_:\\/.-]", "");
        if (sanitized.isEmpty()) {
            return "";
        }
        String fullId = sanitized.contains(":") ? sanitized : "minecraft:" + sanitized;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return "";
        }
        return id.toString();
    }

    private boolean isBlockParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        if (param.getType() == ParameterType.BLOCK_TYPE) {
            return true;
        }
        String name = param.getName();
        return "Block".equalsIgnoreCase(name) || "Blocks".equalsIgnoreCase(name);
    }

    private boolean isItemParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Item".equalsIgnoreCase(param.getName());
    }

    private boolean isEntityParameter(Node node, int index) {
        if (node == null || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter param = node.getParameters().get(index);
        if (param == null) {
            return false;
        }
        return "Entity".equalsIgnoreCase(param.getName());
    }

    private static int findSegmentStart(String value, int caret) {
        int idx = value.lastIndexOf(',', Math.max(0, caret - 1));
        return idx == -1 ? 0 : idx + 1;
    }

    private static int findSegmentEnd(String value, int caret) {
        int idx = value.indexOf(',', Math.max(0, caret));
        return idx == -1 ? value.length() : idx;
    }

    private static final class ParameterSegment {
        private final int start;
        private final int end;
        private final String leadingWhitespace;
        private final String trimmedSegment;

        private ParameterSegment(int start, int end, String leadingWhitespace, String trimmedSegment) {
            this.start = start;
            this.end = end;
            this.leadingWhitespace = leadingWhitespace;
            this.trimmedSegment = trimmedSegment;
        }
    }

    private record ParameterDropdownOption(String label, String value) {
    }

    private record ModeDropdownOption(String label, com.pathmind.nodes.NodeMode mode) {
    }

    private java.util.List<String> getAmountSignDropdownOptions() {
        return java.util.Arrays.asList("+", "-", "*", "/", "%");
    }

    private List<ParameterDropdownOption> getRandomRoundingDropdownOptions() {
        List<ParameterDropdownOption> options = new ArrayList<>(3);
        options.add(new ParameterDropdownOption("Round", "round"));
        options.add(new ParameterDropdownOption("Floor", "floor"));
        options.add(new ParameterDropdownOption("Ceil", "ceil"));
        return options;
    }

    private ParameterSegment getParameterSegment(String value, int caret) {
        String working = value != null ? value : "";
        int clamped = MathHelper.clamp(caret, 0, working.length());
        int start = findSegmentStart(working, clamped);
        int end = findSegmentEnd(working, clamped);
        String segment = working.substring(start, end);
        int leadingEnd = 0;
        while (leadingEnd < segment.length() && Character.isWhitespace(segment.charAt(leadingEnd))) {
            leadingEnd++;
        }
        String leading = segment.substring(0, leadingEnd);
        String trimmed = segment.substring(leadingEnd);
        return new ParameterSegment(start, end, leading, trimmed);
    }

    private AttributeDetectionConfig.TargetKind getAttributeDetectionTargetKind(Node node) {
        if (node == null || !node.isAttributeDetectionSensor()) {
            return null;
        }
        Node attached = node.getAttachedParameter(0);
        if (attached == null) {
            return null;
        }
        return AttributeDetectionConfig.inferTargetKind(attached.getType());
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
            return "true".equalsIgnoreCase(value) ? "True" : "False";
        }
        return value;
    }

    private List<ParameterDropdownOption> getParameterDropdownOptions(Node node, int index, String query) {
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (isAttributeDetectionAttributeParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            for (AttributeDetectionConfig.AttributeOption option : AttributeDetectionConfig.getAttributesForTarget(getAttributeDetectionTargetKind(node))) {
                result.add(new ParameterDropdownOption(option.label(), option.id()));
            }
            return filterDropdownOptions(result, lowered);
        }
        if (isAttributeDetectionBooleanValueParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("True", "true"));
            result.add(new ParameterDropdownOption("False", "false"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBlockStateParameter(node, index)) {
            return getBlockStateDropdownOptions(node, lowered);
        }
        if (isEntityStateParameter(node, index)) {
            return getEntityStateDropdownOptions(node, lowered);
        }
        if (isFabricEventSensorParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("Any", "Any"));
            for (String eventName : FabricEventTracker.getSupportedEvents()) {
                result.add(new ParameterDropdownOption(eventName, eventName));
            }
            return filterDropdownOptions(result, lowered);
        }

        List<String> source;
        if (node != null && node.getType() == NodeType.PARAM_GUI) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("Any", ""));
            for (GuiSelectionMode mode : GuiSelectionMode.valuesList()) {
                result.add(new ParameterDropdownOption(mode.getDisplayName(), mode.getId()));
            }
            return filterDropdownOptions(result, lowered);
        }
        if (isMouseButtonParameter(node, null)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("Left", "Left"));
            result.add(new ParameterDropdownOption("Right", "Right"));
            result.add(new ParameterDropdownOption("Middle", "Middle"));
            result.add(new ParameterDropdownOption("Button 4", "Button 4"));
            result.add(new ParameterDropdownOption("Button 5", "Button 5"));
            result.add(new ParameterDropdownOption("Button 6", "Button 6"));
            result.add(new ParameterDropdownOption("Button 7", "Button 7"));
            result.add(new ParameterDropdownOption("Button 8", "Button 8"));
            return filterDropdownOptions(result, lowered);
        }
        if (isDirectionParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("North", "north"));
            result.add(new ParameterDropdownOption("South", "south"));
            result.add(new ParameterDropdownOption("East", "east"));
            result.add(new ParameterDropdownOption("West", "west"));
            result.add(new ParameterDropdownOption("Up", "up"));
            result.add(new ParameterDropdownOption("Down", "down"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBooleanLiteralParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("True", "true"));
            result.add(new ParameterDropdownOption("False", "false"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBlockFaceParameter(node, index)) {
            List<ParameterDropdownOption> result = new ArrayList<>();
            result.add(new ParameterDropdownOption("North", "north"));
            result.add(new ParameterDropdownOption("South", "south"));
            result.add(new ParameterDropdownOption("East", "east"));
            result.add(new ParameterDropdownOption("West", "west"));
            result.add(new ParameterDropdownOption("Up", "up"));
            result.add(new ParameterDropdownOption("Down", "down"));
            return filterDropdownOptions(result, lowered);
        }
        if (isBlockParameter(node, index)) {
            source = RegistryStringCache.BLOCK_IDS;
        } else if (isItemParameter(node, index)) {
            source = RegistryStringCache.ITEM_IDS;
        } else if (isEntityParameter(node, index)) {
            source = RegistryStringCache.ENTITY_IDS;
        } else {
            return Collections.emptyList();
        }

        List<ParameterDropdownOption> starts = new ArrayList<>();
        List<ParameterDropdownOption> contains = new ArrayList<>();
        List<ParameterDropdownOption> result = new ArrayList<>(65);
        result.add(new ParameterDropdownOption("Any", ""));
        if (lowered.isEmpty()) {
            int limit = 64;
            int added = 0;
            for (String option : source) {
                result.add(new ParameterDropdownOption(option, option));
                added++;
                if (added >= limit) {
                    break;
                }
            }
            return result;
        }
        for (String option : source) {
            String lower = option.toLowerCase(Locale.ROOT);
            if (!lower.contains(lowered)) {
                continue;
            }
            ParameterDropdownOption entry = new ParameterDropdownOption(option, option);
            if (lower.startsWith(lowered)) {
                starts.add(entry);
            } else {
                contains.add(entry);
            }
            if (starts.size() + contains.size() >= 64) {
                break;
            }
        }
        result.addAll(starts);
        result.addAll(contains);
        return result;
    }

    private List<ParameterDropdownOption> filterDropdownOptions(List<ParameterDropdownOption> options, String lowered) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        if (lowered == null || lowered.isEmpty()) {
            return options;
        }
        List<ParameterDropdownOption> starts = new ArrayList<>();
        List<ParameterDropdownOption> contains = new ArrayList<>();
        for (ParameterDropdownOption option : options) {
            if (option == null || option.label() == null) {
                continue;
            }
            String labelLower = option.label().toLowerCase(Locale.ROOT);
            String valueLower = option.value() == null ? "" : option.value().toLowerCase(Locale.ROOT);
            if (!labelLower.contains(lowered) && !valueLower.contains(lowered)) {
                continue;
            }
            if (labelLower.startsWith(lowered) || valueLower.startsWith(lowered)) {
                starts.add(option);
            } else {
                contains.add(option);
            }
        }
        List<ParameterDropdownOption> result = new ArrayList<>(starts.size() + contains.size());
        result.addAll(starts);
        result.addAll(contains);
        return result;
    }

    private boolean isFabricEventSensorParameter(Node node, int index) {
        if (node == null || node.getType() != NodeType.SENSOR_FABRIC_EVENT || index < 0) {
            return false;
        }
        List<NodeParameter> parameters = node.getParameters();
        if (parameters == null || index >= parameters.size()) {
            return false;
        }
        NodeParameter parameter = parameters.get(index);
        return parameter != null
            && parameter.getType() == ParameterType.STRING
            && "Event".equals(parameter.getName());
    }

    private List<ParameterDropdownOption> getBlockStateDropdownOptions(Node node, String loweredQuery) {
        if (node == null) {
            return Collections.emptyList();
        }
        String blockId = getNormalizedBlockIdForStateOptions(node);
        if (blockId.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockSelection.StateOption> options = BlockSelection.getStateOptions(blockId);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParameterDropdownOption> results = new ArrayList<>();
        boolean includeAnyState = loweredQuery == null || loweredQuery.isEmpty();
        if (includeAnyState) {
            results.add(new ParameterDropdownOption("Any State", ""));
        }
        String lowered = loweredQuery == null ? "" : loweredQuery;
        for (BlockSelection.StateOption option : options) {
            String value = option.value();
            String label = option.displayText();
            if (!lowered.isEmpty()) {
                String valueLower = value.toLowerCase(Locale.ROOT);
                String labelLower = label.toLowerCase(Locale.ROOT);
                if (!valueLower.contains(lowered) && !labelLower.contains(lowered)) {
                    continue;
                }
            }
            results.add(new ParameterDropdownOption(label, value));
            if (results.size() >= 64) {
                break;
            }
        }
        return results;
    }

    private List<ParameterDropdownOption> getEntityStateDropdownOptions(Node node, String loweredQuery) {
        if (node == null) {
            return Collections.emptyList();
        }
        String entityId = getNormalizedEntityIdForStateOptions(node);
        if (entityId.isEmpty()) {
            return Collections.emptyList();
        }
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
            return Collections.emptyList();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.world.World world = client != null ? client.world : null;
        List<EntityStateOptions.StateOption> options = EntityStateOptions.getOptions(Registries.ENTITY_TYPE.get(id), world);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParameterDropdownOption> results = new ArrayList<>();
        boolean includeAnyState = loweredQuery == null || loweredQuery.isEmpty();
        if (includeAnyState) {
            results.add(new ParameterDropdownOption("Any State", ""));
        }
        String lowered = loweredQuery == null ? "" : loweredQuery;
        for (EntityStateOptions.StateOption option : options) {
            String value = option.value();
            String label = option.displayText();
            if (!lowered.isEmpty()) {
                String valueLower = value.toLowerCase(Locale.ROOT);
                String labelLower = label.toLowerCase(Locale.ROOT);
                if (!valueLower.contains(lowered) && !labelLower.contains(lowered)) {
                    continue;
                }
            }
            results.add(new ParameterDropdownOption(label, value));
            if (results.size() >= 64) {
                break;
            }
        }
        return results;
    }

    private void updateParameterDropdown(Node node, int index, TextRenderer textRenderer, int fieldX, int fieldY, int fieldWidth, int fieldHeight) {
        if (!isEditingParameterField() || parameterEditingNode != node || parameterEditingIndex != index) {
            return;
        }
        if (!isBlockItemParameter(node, index)
            && !isGuiParameter(node, null)
            && !isMouseButtonParameter(node, null)
            && !isDirectionParameter(node, index)
            && !isBooleanLiteralParameter(node, index)
            && !isAttributeDetectionDropdownParameter(node, index)
            && !isBlockFaceParameter(node, index)
            && !isFabricEventSensorParameter(node, index)) {
            closeParameterDropdown();
            return;
        }
        ParameterSegment segment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String query = segment.trimmedSegment == null ? "" : segment.trimmedSegment.trim();
        boolean isStateParameter = isBlockStateParameter(node, index);

        if (isParameterDropdownSuppressed(node, index, query)) {
            closeParameterDropdown();
            return;
        }

        List<ParameterDropdownOption> options = getParameterDropdownOptions(node, index, query);
        if (!parameterEditBuffer.trim().isEmpty()) {
            options.removeIf(option -> option.value().isEmpty());
        }
        boolean changed = node != parameterDropdownNode
            || index != parameterDropdownIndex
            || !Objects.equals(parameterDropdownQuery, query);

        if (changed) {
            parameterDropdownScrollOffset = 0;
            parameterDropdownHoverIndex = -1;
        }

        parameterDropdownNode = node;
        parameterDropdownIndex = index;
        parameterDropdownFieldX = fieldX;
        parameterDropdownFieldY = fieldY;
        parameterDropdownFieldWidth = fieldWidth;
        parameterDropdownFieldHeight = fieldHeight;
        parameterDropdownQuery = query;
        parameterDropdownOptions.clear();
        parameterDropdownOptions.addAll(options);
        parameterDropdownOpen = true;
    }

    private void closeParameterDropdown() {
        parameterDropdownOpen = false;
        parameterDropdownHoverIndex = -1;
    }

    private void suppressParameterDropdown(Node node, int index, String query) {
        parameterDropdownSuppressedNode = node;
        parameterDropdownSuppressedIndex = index;
        parameterDropdownSuppressedQuery = query == null ? "" : query;
    }

    private void clearParameterDropdownSuppression() {
        parameterDropdownSuppressedNode = null;
        parameterDropdownSuppressedIndex = -1;
        parameterDropdownSuppressedQuery = "";
    }

    private boolean isParameterDropdownSuppressed(Node node, int index, String query) {
        if (parameterDropdownSuppressedNode == null) {
            return false;
        }
        if (parameterDropdownSuppressedNode != node || parameterDropdownSuppressedIndex != index) {
            return false;
        }
        String normalizedQuery = query == null ? "" : query;
        if (Objects.equals(parameterDropdownSuppressedQuery, normalizedQuery)) {
            return true;
        }
        clearParameterDropdownSuppression();
        return false;
    }

    private boolean applyParameterDropdownSelection(int optionIndex) {
        if (!parameterDropdownOpen || parameterDropdownOptions.isEmpty()) {
            return false;
        }
        if (optionIndex < 0 || optionIndex >= parameterDropdownOptions.size()) {
            return false;
        }
        if (!isEditingParameterField()) {
            if (parameterDropdownNode == null || !isBooleanLiteralParameter(parameterDropdownNode, parameterDropdownIndex)) {
                return false;
            }
            ParameterDropdownOption option = parameterDropdownOptions.get(optionIndex);
            NodeParameter parameter = parameterDropdownNode.getParameters().get(parameterDropdownIndex);
            if (parameter == null || option == null) {
                return false;
            }
            parameter.setStringValueFromUser(option.value());
            parameterDropdownNode.setParameterValueAndPropagate(parameter.getName(), option.value());
            parameterDropdownNode.recalculateDimensions();
            notifyNodeParametersChanged(parameterDropdownNode);
            closeParameterDropdown();
            return true;
        }
        ParameterDropdownOption option = parameterDropdownOptions.get(optionIndex);
        ParameterSegment segment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String prefix = parameterEditBuffer.substring(0, segment.start);
        String suffix = parameterEditBuffer.substring(segment.end);
        boolean keepMouseButtonDefaultPlaceholder = isMouseButtonParameter(parameterEditingNode, null)
            && "Left".equalsIgnoreCase(option.value())
            && (segment.trimmedSegment == null || segment.trimmedSegment.trim().isEmpty());
        boolean keepDirectionDefaultPlaceholder = isDirectionParameter(parameterEditingNode, parameterEditingIndex)
            && "north".equalsIgnoreCase(option.value());
        boolean keepBooleanDefaultPlaceholder = isBooleanLiteralParameter(parameterEditingNode, parameterEditingIndex)
            && "true".equalsIgnoreCase(option.value());
        String replacement = (keepMouseButtonDefaultPlaceholder || keepDirectionDefaultPlaceholder || keepBooleanDefaultPlaceholder)
            ? ""
            : segment.leadingWhitespace + option.value();
        parameterEditBuffer = prefix + replacement + suffix;
        setParameterCaretPosition(prefix.length() + replacement.length());
        updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
        refreshStateParameterPreview();
        boolean changed = applyParameterEdit();
        if (changed) {
            notifyNodeParametersChanged(parameterEditingNode);
        }
        ParameterSegment updatedSegment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String updatedQuery = updatedSegment.trimmedSegment == null ? "" : updatedSegment.trimmedSegment.trim();
        suppressParameterDropdown(parameterEditingNode, parameterEditingIndex, updatedQuery);
        closeParameterDropdown();
        return true;
    }

    private int getParameterDropdownListTop() {
        return parameterDropdownFieldY + parameterDropdownFieldHeight;
    }

    private void openBooleanLiteralDropdown(Node node, int index) {
        if (node == null || !isBooleanLiteralParameter(node, index)) {
            return;
        }
        closeModeDropdown();
        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeRandomRoundingDropdown();
        closeAmountSignDropdown();
        stopParameterEditing(false);
        parameterDropdownNode = node;
        parameterDropdownIndex = index;
        parameterDropdownFieldX = getParameterFieldLeft(node) - cameraX;
        parameterDropdownFieldY = getInlineParameterFieldTop(node, index) - cameraY;
        parameterDropdownFieldWidth = getParameterFieldWidth(node);
        parameterDropdownFieldHeight = getParameterFieldHeight();
        parameterDropdownQuery = "";
        parameterDropdownScrollOffset = 0;
        parameterDropdownHoverIndex = -1;
        parameterDropdownOptions.clear();
        parameterDropdownOptions.addAll(getParameterDropdownOptions(node, index, ""));
        parameterDropdownOpen = true;
    }

    private int getDropdownWidth() {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null) {
            return parameterDropdownFieldWidth;
        }
        int longestLabelWidth = textRenderer.getWidth("No matches");
        for (ParameterDropdownOption option : parameterDropdownOptions) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option.label()));
            }
        }
        return longestLabelWidth + PARAMETER_DROPDOWN_ICON_ALLOWANCE + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getDropdownRowHeight() {
        return PARAMETER_DROPDOWN_ROW_HEIGHT;
    }

    private DropdownLayoutHelper.Layout getParameterDropdownLayout() {
        int optionCount = Math.max(1, parameterDropdownOptions.size());
        int listTop = getParameterDropdownListTop();
        // Convert screen height to transformed space since dropdown is rendered in transformed coordinates
        float zoom = Math.max(0.01f, getZoomScale());
        int transformedScreenHeight = Math.round(MinecraftClient.getInstance().getWindow().getScaledHeight() / zoom);
        int rowHeight = getDropdownRowHeight();
        return DropdownLayoutHelper.calculate(
            optionCount,
            rowHeight,
            PARAMETER_DROPDOWN_MAX_ROWS,
            listTop,
            transformedScreenHeight
        );
    }

    private boolean isPointInsideParameterDropdownList(int screenX, int screenY) {
        if (!parameterDropdownOpen) {
            return false;
        }
        // Transform mouse coordinates from screen space to transformed space
        float zoom = getZoomScale();
        int transformedX = Math.round(screenX / zoom);
        int transformedY = Math.round(screenY / zoom);

        int dropdownWidth = getDropdownWidth();
        DropdownLayoutHelper.Layout layout = getParameterDropdownLayout();
        int listTop = getParameterDropdownListTop();
        int listLeft = parameterDropdownFieldX;
        return transformedX >= listLeft && transformedX <= listLeft + dropdownWidth
            && transformedY >= listTop && transformedY <= listTop + layout.height;
    }

    private int getParameterDropdownIndexAt(int screenX, int screenY) {
        if (!parameterDropdownOpen) {
            return -1;
        }
        // Transform mouse coordinates from screen space to transformed space
        float zoom = getZoomScale();
        int transformedY = Math.round(screenY / zoom);

        int rowHeight = getDropdownRowHeight();
        DropdownLayoutHelper.Layout layout = getParameterDropdownLayout();
        int listTop = getParameterDropdownListTop();
        int row = (transformedY - listTop) / rowHeight;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        int index = parameterDropdownScrollOffset + row;
        if (parameterDropdownOptions.isEmpty()) {
            return -1;
        }
        return index;
    }

    public boolean handleParameterDropdownClick(double screenX, double screenY) {
        if (!parameterDropdownOpen) {
            return false;
        }
        int x = (int) screenX;
        int y = (int) screenY;
        if (isPointInsideParameterDropdownList(x, y)) {
            int index = getParameterDropdownIndexAt(x, y);
            if (index >= 0) {
                applyParameterDropdownSelection(index);
            }
            return true;
        }
        int transformedX = screenToUiX(x);
        int transformedY = screenToUiY(y);
        int fieldLeft = parameterDropdownFieldX;
        int fieldTop = parameterDropdownFieldY;
        if (transformedX >= fieldLeft && transformedX <= fieldLeft + parameterDropdownFieldWidth
            && transformedY >= fieldTop && transformedY <= fieldTop + parameterDropdownFieldHeight) {
            if (!isEditingParameterField()
                && parameterDropdownNode != null
                && isBooleanLiteralParameter(parameterDropdownNode, parameterDropdownIndex)) {
                closeParameterDropdown();
            }
            return true;
        }
        if (isEditingParameterField()) {
            ParameterSegment segment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
            String query = segment.trimmedSegment == null ? "" : segment.trimmedSegment.trim();
            suppressParameterDropdown(parameterEditingNode, parameterEditingIndex, query);
        }
        closeParameterDropdown();
        return false;
    }

    public boolean handleParameterDropdownScroll(double screenX, double screenY, double verticalAmount) {
        if (!parameterDropdownOpen) {
            return false;
        }
        if (!isPointInsideParameterDropdownList((int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = getParameterDropdownLayout();
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        parameterDropdownScrollOffset = MathHelper.clamp(parameterDropdownScrollOffset - delta, 0, layout.maxScrollOffset);
        return true;
    }

    private void renderParameterDropdownList(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(parameterDropdownAnimation, parameterDropdownOpen);
        if (parameterDropdownNode == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearParameterDropdownState();
            return;
        }
        List<ParameterDropdownOption> options = parameterDropdownOptions;
        int optionCount = Math.max(1, options.size());
        // Transform mouse coordinates from screen space to transformed space
        float zoom = getZoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);

        int rowHeight = getDropdownRowHeight();
        int dropdownWidth = getDropdownWidth();
        DropdownLayoutHelper.Layout layout = getParameterDropdownLayout();
        int listTop = getParameterDropdownListTop();
        int listLeft = parameterDropdownFieldX;
        int listRight = listLeft + dropdownWidth;
        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, UITheme.BORDER_HIGHLIGHT);

        parameterDropdownScrollOffset = MathHelper.clamp(parameterDropdownScrollOffset, 0, layout.maxScrollOffset);
        parameterDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && transformedMouseX >= listLeft && transformedMouseX <= listRight
            && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / rowHeight;
            if (row >= 0 && row < layout.visibleCount) {
                parameterDropdownHoverIndex = parameterDropdownScrollOffset + row;
            }
        }

        int visibleCount = layout.visibleCount;
        int iconSize = 16;
        int padding = 4;

        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = parameterDropdownScrollOffset + row;
            String optionLabel = options.isEmpty() ? "No matches" : options.get(optionIndex).label();
            int rowTop = listTop + row * rowHeight;
            int rowBottom = rowTop + rowHeight;
            boolean hovered = options.isEmpty() ? row == 0 && parameterDropdownHoverIndex >= 0 : optionIndex == parameterDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, UITheme.BACKGROUND_TERTIARY);
            }
            int iconX = listLeft + padding;
            int iconY = rowTop + (rowHeight - iconSize) / 2;
            String optionValue = options.isEmpty() ? "" : options.get(optionIndex).value();
            ItemStack icon = resolveParameterDropdownIcon(parameterDropdownNode, parameterDropdownIndex, optionValue);
            if (!icon.isEmpty()) {
                context.drawItem(icon, iconX, iconY);
            }
            int textPadding = 3;
            int textX = listLeft + textPadding;
            if (!icon.isEmpty()) {
                textX = iconX + iconSize + padding;
            }
            int maxTextWidth = dropdownWidth - (textX - listLeft) - textPadding - DROPDOWN_SCROLLBAR_ALLOWANCE;
            String rowText = trimTextToWidth(optionLabel, textRenderer, Math.max(0, maxTextWidth));
            int textOffsetY = 4;
            drawNodeText(context, textRenderer, Text.literal(rowText), textX, rowTop + textOffsetY, UITheme.TEXT_PRIMARY);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            parameterDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    public boolean handleModeDropdownClick(double screenX, double screenY) {
        if (!modeDropdownOpen) {
            return false;
        }
        refreshModeDropdownAnchor();
        int x = (int) screenX;
        int y = (int) screenY;
        if (isPointInsideModeDropdownList(x, y)) {
            int index = getModeDropdownIndexAt(x, y);
            if (index >= 0) {
                applyModeDropdownSelection(index);
            }
            return true;
        }
        int transformedX = screenToUiX(x);
        int transformedY = screenToUiY(y);
        int fieldLeft = modeDropdownFieldX;
        int fieldTop = modeDropdownFieldY;
        if (transformedX >= fieldLeft && transformedX <= fieldLeft + modeDropdownFieldWidth
            && transformedY >= fieldTop && transformedY <= fieldTop + modeDropdownFieldHeight) {
            closeModeDropdown();
            return true;
        }
        closeModeDropdown();
        return false;
    }

    public boolean handleModeDropdownScroll(double screenX, double screenY, double verticalAmount) {
        if (!modeDropdownOpen) {
            return false;
        }
        refreshModeDropdownAnchor();
        if (!isPointInsideModeDropdownList((int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = getModeDropdownLayout();
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        modeDropdownScrollOffset = MathHelper.clamp(modeDropdownScrollOffset - delta, 0, layout.maxScrollOffset);
        return true;
    }

    public boolean handleRandomRoundingDropdownScroll(double screenX, double screenY, double verticalAmount) {
        if (!randomRoundingDropdownOpen || randomRoundingDropdownNode == null) {
            return false;
        }
        if (!isPointInsideRandomRoundingDropdownList((int) screenX, (int) screenY)) {
            return false;
        }
        int listTop = randomRoundingDropdownNode.getRandomRoundingFieldInputTop()
            + randomRoundingDropdownNode.getRandomRoundingFieldHeight() + 2;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            getRandomRoundingDropdownOptions().size(),
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            RANDOM_ROUNDING_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        randomRoundingDropdownScrollOffset = MathHelper.clamp(
            randomRoundingDropdownScrollOffset - delta,
            0,
            layout.maxScrollOffset
        );
        return true;
    }

    public boolean handleModeFieldClick(Node node, int screenX, int screenY) {
        if (node == null || !node.supportsModeSelection()) {
            return false;
        }
        if (!isPointInsideModeField(node, screenX, screenY)) {
            return false;
        }
        if (modeDropdownOpen && modeDropdownNode == node) {
            closeModeDropdown();
            return true;
        }
        stopParameterEditing(true);
        openModeDropdown(node);
        return true;
    }

    public boolean isModeDropdownOpen() {
        return modeDropdownOpen;
    }

    public void closeModeDropdown() {
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
    }

    private void refreshModeDropdownAnchor() {
        if (!modeDropdownOpen || modeDropdownNode == null) {
            return;
        }
        Node node = modeDropdownNode;
        if (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) {
            modeDropdownFieldX = node.getAmountFieldLeft() - cameraX;
            modeDropdownFieldY = node.getAmountFieldLabelTop() - cameraY;
            modeDropdownFieldWidth = node.getAmountFieldWidth();
            modeDropdownFieldHeight = node.getAmountFieldLabelHeight();
        } else if (node.showsModeFieldAboveParameterSlot()) {
            modeDropdownFieldX = node.getModeFieldLeft() - cameraX;
            modeDropdownFieldY = node.getModeFieldTop() - cameraY;
            modeDropdownFieldWidth = node.getModeFieldWidth();
            modeDropdownFieldHeight = node.getModeFieldHeight();
        } else {
            modeDropdownFieldX = getParameterFieldLeft(node) - cameraX;
            modeDropdownFieldY = node.getY() - cameraY + 18;
            modeDropdownFieldWidth = getParameterFieldWidth(node);
            modeDropdownFieldHeight = getParameterFieldHeight();
        }
    }

    private void renderModeDropdownList(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(modeDropdownAnimation, modeDropdownOpen);
        if (modeDropdownNode == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearModeDropdownState();
            return;
        }
        refreshModeDropdownAnchor();
        List<ModeDropdownOption> options = modeDropdownOptions;
        int optionCount = Math.max(1, options.size());
        float zoom = getZoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);

        int rowHeight = getDropdownRowHeight();
        int dropdownWidth = getModeDropdownWidth();
        DropdownLayoutHelper.Layout layout = getModeDropdownLayout();
        int listTop = getModeDropdownListTop();
        int listLeft = modeDropdownFieldX;
        int listRight = listLeft + dropdownWidth;
        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));

        enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        context.fill(listLeft, listTop, listRight, listBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorderInLayer(context, listLeft, listTop, dropdownWidth, listHeight, UITheme.BORDER_HIGHLIGHT);

        modeDropdownScrollOffset = MathHelper.clamp(modeDropdownScrollOffset, 0, layout.maxScrollOffset);
        modeDropdownHoverIndex = -1;
        if (animProgress >= 1f
            && transformedMouseX >= listLeft && transformedMouseX <= listRight
            && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / rowHeight;
            if (row >= 0 && row < layout.visibleCount) {
                modeDropdownHoverIndex = modeDropdownScrollOffset + row;
            }
        }

        int visibleCount = layout.visibleCount;
        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = modeDropdownScrollOffset + row;
            String optionLabel = options.isEmpty() ? "No modes" : options.get(optionIndex).label();
            int rowTop = listTop + row * rowHeight;
            int rowBottom = rowTop + rowHeight;
            boolean hovered = options.isEmpty() ? row == 0 && modeDropdownHoverIndex >= 0 : optionIndex == modeDropdownHoverIndex;
            if (hovered) {
                context.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom - 1, UITheme.BACKGROUND_TERTIARY);
            }
            int textPadding = 5;
            int maxTextWidth = dropdownWidth - (textPadding * 2) - DROPDOWN_SCROLLBAR_ALLOWANCE;
            String rowText = trimTextToWidth(optionLabel, textRenderer, Math.max(0, maxTextWidth));
            int textOffsetY = 4;
            drawNodeText(context, textRenderer, Text.literal(rowText), listLeft + textPadding, rowTop + textOffsetY, UITheme.TEXT_PRIMARY);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            modeDropdownScrollOffset,
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            UITheme.BORDER_DEFAULT
        );
        context.disableScissor();
    }

    private int getModeDropdownListTop() {
        return modeDropdownFieldY + modeDropdownFieldHeight;
    }

    private int getModeDropdownWidth() {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null) {
            return modeDropdownFieldWidth;
        }
        int longestLabelWidth = textRenderer.getWidth("No modes");
        for (ModeDropdownOption option : modeDropdownOptions) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option.label()));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getRandomRoundingDropdownWidth(Node node) {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getRandomRoundingFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.getWidth("No options");
        for (ParameterDropdownOption option : getRandomRoundingDropdownOptions()) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option.label()));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getSchematicDropdownWidth(Node node) {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getSchematicFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.getWidth("No schematics found");
        for (String option : schematicDropdownOptions) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getRunPresetDropdownWidth(Node node) {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getStopTargetFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.getWidth("No presets found");
        for (String option : runPresetDropdownOptions) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private int getAmountSignDropdownWidth(Node node) {
        TextRenderer textRenderer = getClientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getAmountSignToggleWidth() : 0;
        }
        int longestLabelWidth = 0;
        for (String option : getAmountSignDropdownOptions()) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.getWidth(option));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private DropdownLayoutHelper.Layout getModeDropdownLayout() {
        int optionCount = Math.max(1, modeDropdownOptions.size());
        int listTop = getModeDropdownListTop();
        float zoom = Math.max(0.01f, getZoomScale());
        int transformedScreenHeight = Math.round(MinecraftClient.getInstance().getWindow().getScaledHeight() / zoom);
        int rowHeight = getDropdownRowHeight();
        return DropdownLayoutHelper.calculate(
            optionCount,
            rowHeight,
            PARAMETER_DROPDOWN_MAX_ROWS,
            listTop,
            transformedScreenHeight
        );
    }

    private boolean isPointInsideModeDropdownList(int screenX, int screenY) {
        if (!modeDropdownOpen) {
            return false;
        }
        refreshModeDropdownAnchor();
        float zoom = getZoomScale();
        int transformedX = Math.round(screenX / zoom);
        int transformedY = Math.round(screenY / zoom);

        int dropdownWidth = getModeDropdownWidth();
        DropdownLayoutHelper.Layout layout = getModeDropdownLayout();
        int listTop = getModeDropdownListTop();
        int listLeft = modeDropdownFieldX;
        return transformedX >= listLeft && transformedX <= listLeft + dropdownWidth
            && transformedY >= listTop && transformedY <= listTop + layout.height;
    }

    private int getModeDropdownIndexAt(int screenX, int screenY) {
        if (!modeDropdownOpen) {
            return -1;
        }
        refreshModeDropdownAnchor();
        float zoom = getZoomScale();
        int transformedY = Math.round(screenY / zoom);

        int rowHeight = getDropdownRowHeight();
        DropdownLayoutHelper.Layout layout = getModeDropdownLayout();
        int listTop = getModeDropdownListTop();
        int row = (transformedY - listTop) / rowHeight;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        int index = modeDropdownScrollOffset + row;
        if (modeDropdownOptions.isEmpty()) {
            return -1;
        }
        return index;
    }

    private void openModeDropdown(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return;
        }
        closeParameterDropdown();
        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeRandomRoundingDropdown();
        modeDropdownNode = node;
        modeDropdownScrollOffset = 0;
        modeDropdownHoverIndex = -1;
        if (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) {
            modeDropdownFieldX = node.getAmountFieldLeft() - cameraX;
            modeDropdownFieldY = node.getAmountFieldLabelTop() - cameraY;
            modeDropdownFieldWidth = node.getAmountFieldWidth();
            modeDropdownFieldHeight = node.getAmountFieldLabelHeight();
        } else if (node.showsModeFieldAboveParameterSlot()) {
            modeDropdownFieldX = node.getModeFieldLeft() - cameraX;
            modeDropdownFieldY = node.getModeFieldTop() - cameraY;
            modeDropdownFieldWidth = node.getModeFieldWidth();
            modeDropdownFieldHeight = node.getModeFieldHeight();
        } else {
            modeDropdownFieldX = getParameterFieldLeft(node) - cameraX;
            modeDropdownFieldY = node.getY() - cameraY + 18;
            modeDropdownFieldWidth = getParameterFieldWidth(node);
            modeDropdownFieldHeight = getParameterFieldHeight();
        }
        modeDropdownOptions.clear();
        modeDropdownOptions.addAll(getModeDropdownOptions(node));
        modeDropdownOpen = true;
    }

    private List<ModeDropdownOption> getModeDropdownOptions(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return Collections.emptyList();
        }
        com.pathmind.nodes.NodeMode[] modes = com.pathmind.nodes.NodeMode.getModesForNodeType(node.getType());
        if (modes == null || modes.length == 0) {
            return Collections.emptyList();
        }
        List<ModeDropdownOption> options = new ArrayList<>(modes.length);
        for (com.pathmind.nodes.NodeMode mode : modes) {
            if (mode != null) {
                options.add(new ModeDropdownOption(mode.getDisplayName(), mode));
            }
        }
        return options;
    }

    private void applyModeDropdownSelection(int optionIndex) {
        if (!modeDropdownOpen || modeDropdownNode == null || modeDropdownOptions.isEmpty()) {
            return;
        }
        if (optionIndex < 0 || optionIndex >= modeDropdownOptions.size()) {
            return;
        }
        ModeDropdownOption option = modeDropdownOptions.get(optionIndex);
        if (option == null || option.mode() == null) {
            return;
        }
        modeDropdownNode.setMode(option.mode());
        modeDropdownNode.recalculateDimensions();
        notifyNodeParametersChanged(modeDropdownNode);
        closeModeDropdown();
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
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = getParameterFieldHeight();
        int fieldTop = node.getY() + 18;
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private ItemStack resolveParameterDropdownIcon(Node node, int index, String optionValue) {
        if (node == null || optionValue == null || optionValue.isEmpty()
            || isBlockStateParameter(node, index)
            || isEntityStateParameter(node, index)) {
            return ItemStack.EMPTY;
        }
        String fullId = optionValue.contains(":") ? optionValue : "minecraft:" + optionValue;
        Identifier id = Identifier.tryParse(fullId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        if (isBlockParameter(node, index)) {
            var block = Registries.BLOCK.get(id);
            if (block == null) {
                return ItemStack.EMPTY;
            }
            Item item = block.asItem();
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }
        if (isItemParameter(node, index)) {
            Item item = Registries.ITEM.get(id);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }
        if (isEntityParameter(node, index)) {
            var entityType = Registries.ENTITY_TYPE.get(id);
            if (entityType == null) {
                return ItemStack.EMPTY;
            }
            Item spawnEgg = SpawnEggItem.forEntity(entityType);
            if (spawnEgg == null || spawnEgg == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(spawnEgg);
        }
        return ItemStack.EMPTY;
    }

    private static final class RegistryStringCache {
        private static final List<String> BLOCK_IDS = buildBlockIds();
        private static final List<String> ITEM_IDS = buildItemIds();
        private static final List<String> ENTITY_IDS = buildEntityIds();

        private static List<String> buildBlockIds() {
            List<String> options = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                if (id == null) {
                    continue;
                }
                options.add(id.getPath());
            }
            options.sort(String::compareToIgnoreCase);
            return options;
        }

        private static List<String> buildItemIds() {
            List<String> options = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) {
                if (id == null) {
                    continue;
                }
                options.add(id.getPath());
            }
            options.sort(String::compareToIgnoreCase);
            return options;
        }

        private static List<String> buildEntityIds() {
            List<String> options = new ArrayList<>();
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                if (id == null) {
                    continue;
                }
                options.add(id.getPath());
            }
            options.sort(String::compareToIgnoreCase);
            return options;
        }
    }

    private TextRenderer getClientTextRenderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null ? client.textRenderer : null;
    }

    private String getClipboardText() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            return client.keyboard.getClipboard();
        }
        return "";
    }

    private void setClipboardText(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(text == null ? "" : text);
        }
    }

    public boolean isPointInsideAmountField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasAmountInputField()) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getAmountFieldLeft();
        int fieldTop = node.getAmountFieldInputTop();
        int fieldWidth = node.getAmountFieldWidth();
        int fieldHeight = node.getAmountFieldHeight();

        boolean inside = worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
        return inside && node.isAmountInputEnabled();
    }

    private boolean isPointInsideRandomRoundingField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasRandomRoundingField()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getRandomRoundingFieldLeft();
        int fieldTop = node.getRandomRoundingFieldInputTop();
        int fieldWidth = node.getRandomRoundingFieldWidth();
        int fieldHeight = node.getRandomRoundingFieldHeight();
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private boolean isPointInsideRandomRoundingToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasRandomRoundingToggle()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int left = node.getRandomRoundingToggleLeft() - 3;
        int top = node.getRandomRoundingToggleTop() - 3;
        int width = node.getRandomRoundingToggleWidth() + 6;
        int height = node.getRandomRoundingToggleHeight() + 6;
        return worldX >= left && worldX <= left + width
            && worldY >= top && worldY <= top + height;
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

    private boolean isPointInsideAmountSignToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasAmountSignToggle()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int left = node.getAmountSignToggleLeft() - 3;
        int top = node.getAmountSignToggleTop() - 3;
        int width = node.getAmountSignToggleWidth() + 6;
        int height = node.getAmountSignToggleHeight() + 6;
        return worldX >= left && worldX <= left + width
            && worldY >= top && worldY <= top + height;
    }

    private boolean isPointInsideAmountSignDropdownList(int screenX, int screenY) {
        if (!amountSignDropdownOpen || amountSignDropdownNode == null) {
            return false;
        }
        Node node = amountSignDropdownNode;
        float zoom = Math.max(0.01f, getZoomScale());
        int transformedX = Math.round(screenX / zoom);
        int transformedY = Math.round(screenY / zoom);
        int listTop = getAmountSignDropdownListTop(node);
        DropdownLayoutHelper.Layout layout = getAmountSignDropdownLayout(node);
        int listHeight = layout.height;
        int listLeft = node.getAmountSignToggleLeft() - cameraX;
        int listWidth = getAmountSignDropdownWidth(node);
        return transformedX >= listLeft && transformedX <= listLeft + listWidth
            && transformedY >= listTop && transformedY <= listTop + listHeight;
    }

    private int getAmountSignDropdownIndexAt(Node node, int screenX, int screenY) {
        if (node == null) {
            return -1;
        }
        java.util.List<String> options = getAmountSignDropdownOptions();
        if (options.isEmpty()) {
            return -1;
        }
        float zoom = Math.max(0.01f, getZoomScale());
        int transformedY = Math.round(screenY / zoom);
        int listTop = getAmountSignDropdownListTop(node);
        DropdownLayoutHelper.Layout layout = getAmountSignDropdownLayout(node);
        int row = (transformedY - listTop) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        int index = amountSignDropdownScrollOffset + row;
        if (index < 0 || index >= options.size()) {
            return -1;
        }
        return index;
    }

    private int getAmountSignDropdownListTop(Node node) {
        return node.getAmountSignToggleTop() + node.getAmountSignToggleHeight() + 2 - cameraY;
    }

    private DropdownLayoutHelper.Layout getAmountSignDropdownLayout(Node node) {
        int optionCount = Math.max(1, getAmountSignDropdownOptions().size());
        int listTop = getAmountSignDropdownListTop(node);
        float zoom = Math.max(0.01f, getZoomScale());
        int transformedScreenHeight = Math.round(MinecraftClient.getInstance().getWindow().getScaledHeight() / zoom);
        return DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            AMOUNT_SIGN_DROPDOWN_MAX_ROWS,
            listTop,
            transformedScreenHeight
        );
    }

    private boolean isPointInsideRandomRoundingDropdownList(int screenX, int screenY) {
        if (!randomRoundingDropdownOpen || randomRoundingDropdownNode == null) {
            return false;
        }
        Node node = randomRoundingDropdownNode;
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int listTopScreen = node.getRandomRoundingFieldInputTop() + node.getRandomRoundingFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            getRandomRoundingDropdownOptions().size(),
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            RANDOM_ROUNDING_DROPDOWN_MAX_ROWS,
            listTopScreen,
            screenHeight
        );
        int listHeight = layout.height;
        int listLeft = node.getRandomRoundingFieldLeft();
        int listWidth = getRandomRoundingDropdownWidth(node);
        int worldListTop = node.getRandomRoundingFieldInputTop() + node.getRandomRoundingFieldHeight() + 2;
        return worldX >= listLeft && worldX <= listLeft + listWidth
            && worldY >= worldListTop && worldY <= worldListTop + listHeight;
    }

    private int getRandomRoundingDropdownIndexAt(Node node, int screenX, int screenY) {
        if (node == null) {
            return -1;
        }
        List<ParameterDropdownOption> options = getRandomRoundingDropdownOptions();
        if (options.isEmpty()) {
            return -1;
        }
        int worldY = screenToWorldY(screenY);
        int worldListTop = node.getRandomRoundingFieldInputTop() + node.getRandomRoundingFieldHeight() + 2;
        int listTopScreen = node.getRandomRoundingFieldInputTop() + node.getRandomRoundingFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            options.size(),
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            RANDOM_ROUNDING_DROPDOWN_MAX_ROWS,
            listTopScreen,
            screenHeight
        );
        int row = (worldY - worldListTop) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        int index = randomRoundingDropdownScrollOffset + row;
        if (index < 0 || index >= options.size()) {
            return -1;
        }
        return index;
    }

    public boolean handleAmountToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideAmountToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isAmountInputEnabled();
        node.setAmountInputEnabled(newState);
        getNodeToggleAnimation(amountToggleAnimations, node, newState)
            .animateTo(newState ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        if (!newState && isEditingAmountField() && amountEditingNode == node) {
            stopAmountEditing(false);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
        return true;
    }

    public boolean handleRandomRoundingToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideRandomRoundingToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isRandomRoundingEnabled();
        node.setRandomRoundingEnabled(newState);
        getNodeToggleAnimation(randomRoundingToggleAnimations, node, newState)
            .animateTo(newState ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        if (!newState && randomRoundingDropdownOpen && randomRoundingDropdownNode == node) {
            closeRandomRoundingDropdown();
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
        return true;
    }

    public boolean handleRandomRoundingDropdownClick(Node node, int mouseX, int mouseY) {
        if (randomRoundingDropdownOpen) {
            if (node == null || node != randomRoundingDropdownNode) {
                if (isPointInsideRandomRoundingDropdownList(mouseX, mouseY)) {
                    int index = getRandomRoundingDropdownIndexAt(randomRoundingDropdownNode, mouseX, mouseY);
                    if (index >= 0) {
                        List<ParameterDropdownOption> options = getRandomRoundingDropdownOptions();
                        if (index < options.size()) {
                            randomRoundingDropdownNode.setRandomRoundingMode(options.get(index).value());
                            randomRoundingDropdownNode.recalculateDimensions();
                            notifyNodeParametersChanged(randomRoundingDropdownNode);
                        }
                    }
                    closeRandomRoundingDropdown();
                    return true;
                }
                closeRandomRoundingDropdown();
                return false;
            }
            if (isPointInsideRandomRoundingField(node, mouseX, mouseY)) {
                closeRandomRoundingDropdown();
                return true;
            }
            if (isPointInsideRandomRoundingDropdownList(mouseX, mouseY)) {
                int index = getRandomRoundingDropdownIndexAt(node, mouseX, mouseY);
                if (index >= 0) {
                    List<ParameterDropdownOption> options = getRandomRoundingDropdownOptions();
                    if (index < options.size()) {
                        node.setRandomRoundingMode(options.get(index).value());
                        node.recalculateDimensions();
                        notifyNodeParametersChanged(node);
                    }
                }
                closeRandomRoundingDropdown();
                return true;
            }
            closeRandomRoundingDropdown();
            return false;
        }

        if (node == null || !node.hasRandomRoundingField()) {
            return false;
        }
        if (!isPointInsideRandomRoundingField(node, mouseX, mouseY)) {
            return false;
        }
        stopParameterEditing(true);
        openRandomRoundingDropdown(node);
        return true;
    }

    public boolean handleAmountSignDropdownClick(Node node, int mouseX, int mouseY) {
        if (amountSignDropdownOpen) {
            if (node == null && amountSignDropdownNode != null
                && isPointInsideAmountSignToggle(amountSignDropdownNode, mouseX, mouseY)) {
                closeAmountSignDropdown();
                return true;
            }
            if (node == amountSignDropdownNode && isPointInsideAmountSignToggle(node, mouseX, mouseY)) {
                closeAmountSignDropdown();
                return true;
            }
            if (isPointInsideAmountSignDropdownList(mouseX, mouseY)) {
                int selectedIndex = getAmountSignDropdownIndexAt(amountSignDropdownNode, mouseX, mouseY);
                if (amountSignDropdownNode != null && selectedIndex >= 0) {
                    java.util.List<String> options = getAmountSignDropdownOptions();
                    if (selectedIndex < options.size()) {
                        amountSignDropdownNode.setAmountOperation(options.get(selectedIndex));
                        amountSignDropdownNode.recalculateDimensions();
                        notifyNodeParametersChanged(amountSignDropdownNode);
                    }
                }
                closeAmountSignDropdown();
                return true;
            }
            closeAmountSignDropdown();
            return false;
        }

        if (node == null || !isPointInsideAmountSignToggle(node, mouseX, mouseY)) {
            return false;
        }
        openAmountSignDropdown(node);
        return true;
    }

    public boolean isPointInsideStopTargetField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasStopTargetInputField()) {
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
                selectNode(node);
                startStopTargetEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean handleRunPresetDropdownClick(Node clickedNode, int screenX, int screenY) {
        if (runPresetDropdownOpen && runPresetDropdownNode != null) {
            if (isPointInsideRunPresetDropdownList(runPresetDropdownNode, screenX, screenY)) {
                int index = getRunPresetDropdownIndexAt(runPresetDropdownNode, screenX, screenY);
                if (index >= 0 && index < runPresetDropdownOptions.size()) {
                    applyRunPresetSelection(runPresetDropdownNode, runPresetDropdownOptions.get(index));
                }
                closeRunPresetDropdown();
                return true;
            }
            if (isPointInsideRunPresetField(runPresetDropdownNode, screenX, screenY)) {
                closeRunPresetDropdown();
                return true;
            }
            closeRunPresetDropdown();
        }

        if (isPresetSelectorNode(clickedNode)
            && isPointInsideRunPresetField(clickedNode, screenX, screenY)) {
            selectNode(clickedNode);
            startStopTargetEditing(clickedNode);
            openRunPresetDropdown(clickedNode);
            return true;
        }

        return false;
    }

    public boolean isPointInsideVariableField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasVariableInputField()) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getVariableFieldLeft();
        int fieldTop = node.getVariableFieldInputTop();
        int fieldWidth = node.getVariableFieldWidth();
        int fieldHeight = node.getVariableFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean handleVariableFieldClick(int screenX, int screenY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node != null && node.hasVariableInputField() && isPointInsideVariableField(node, screenX, screenY)) {
                selectNode(node);
                startVariableEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean isPointInsideEventNameField(Node node, int screenX, int screenY) {
        if (node == null || (node.getType() != NodeType.EVENT_FUNCTION && node.getType() != NodeType.EVENT_CALL)) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getEventNameFieldLeft();
        int fieldTop = node.getEventNameFieldTop();
        int fieldWidth = node.getEventNameFieldWidth();
        int fieldHeight = node.getEventNameFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean handleEventNameFieldClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideEventNameField(node, mouseX, mouseY)) {
            return false;
        }
        startEventNameEditing(node);
        return true;
    }

    public int getMessageFieldIndexAt(Node node, int screenX, int screenY) {
        if (node == null || !node.hasMessageInputFields()) {
            return -1;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int count = node.getMessageFieldCount();
        int fieldLeft = node.getMessageFieldLeft();
        int fieldWidth = node.getMessageFieldWidth();
        for (int i = 0; i < count; i++) {
            int fieldTop = node.getMessageFieldInputTop(i);
            int fieldHeight = node.getMessageFieldHeight();
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight) {
                return i;
            }
        }
        return -1;
    }

    public int getParameterFieldIndexAt(Node node, int screenX, int screenY) {
        if (node == null || !rendersInlineParameters(node) || node.hasPopupEditButton()) {
            return -1;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        if (node.getType() == NodeType.VARIABLE) {
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
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = getParameterFieldHeight();
        int fieldTop = getInlineParameterFieldsTop(node);

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

    private int getInlineParameterFieldTop(Node node, int index) {
        if (node == null || index < 0) {
            return 0;
        }
        int fieldTop = getInlineParameterFieldsTop(node);
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
        if (parameterDropdownOpen && !isEditingParameterField()
            && parameterDropdownNode != null
            && isBooleanLiteralParameter(parameterDropdownNode, parameterDropdownIndex)) {
            if (isPointInsideBooleanLiteralField(parameterDropdownNode, parameterDropdownIndex, mouseX, mouseY)) {
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
                if (isBooleanLiteralParameter(candidate, index)) {
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
        if (!isBooleanLiteralParameter(node, index)) {
            return false;
        }
        openBooleanLiteralDropdown(node, index);
        return true;
    }

    private boolean isPointInsideBooleanLiteralField(Node node, int index, int screenX, int screenY) {
        if (!isBooleanLiteralParameter(node, index)) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = getParameterFieldLeft(node);
        int fieldTop = getInlineParameterFieldTop(node, index);
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = getParameterFieldHeight();
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean handleDirectionModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findDirectionModeNodeAt(worldX, worldY);
        if (!isCombinedDirectionNode(node)) {
            return false;
        }
        int fieldLeft = getParameterFieldLeft(node);
        int fieldTop = getDirectionModeTabTop(node);
        int fieldWidth = getParameterFieldWidth(node);
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
            if (!isCombinedDirectionNode(candidate)) {
                continue;
            }
            int fieldLeft = getParameterFieldLeft(candidate);
            int fieldTop = getDirectionModeTabTop(candidate);
            int fieldWidth = getParameterFieldWidth(candidate);
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
            if (!isCombinedBooleanNode(candidate)) {
                continue;
            }
            int fieldLeft = getParameterFieldLeft(candidate);
            int fieldTop = getBooleanModeTabTop(candidate);
            int fieldWidth = getParameterFieldWidth(candidate);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    public boolean handleSchematicDropdownClick(Node clickedNode, int screenX, int screenY) {
        if (schematicDropdownOpen && schematicDropdownNode != null) {
            if (isPointInsideSchematicDropdownList(schematicDropdownNode, screenX, screenY)) {
                int index = getSchematicDropdownIndexAt(schematicDropdownNode, screenX, screenY);
                if (index >= 0 && index < schematicDropdownOptions.size()) {
                    applySchematicSelection(schematicDropdownNode, schematicDropdownOptions.get(index));
                }
                closeSchematicDropdown();
                return true;
            }
            if (isPointInsideSchematicField(schematicDropdownNode, screenX, screenY)) {
                closeSchematicDropdown();
                return true;
            }
            closeSchematicDropdown();
        }

        if (clickedNode != null && clickedNode.hasSchematicDropdownField()
            && isPointInsideSchematicField(clickedNode, screenX, screenY)) {
            openSchematicDropdown(clickedNode);
            return true;
        }

        return false;
    }

    public boolean handleSchematicDropdownScroll(double screenX, double screenY, double amount) {
        if (!schematicDropdownOpen || schematicDropdownNode == null || schematicDropdownOptions.isEmpty()) {
            return false;
        }
        if (!isPointInsideSchematicDropdownList(schematicDropdownNode, (int) screenX, (int) screenY)) {
            return false;
        }
        int listTop = schematicDropdownNode.getSchematicFieldInputTop() + schematicDropdownNode.getSchematicFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            schematicDropdownOptions.size(),
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        if (layout.maxScrollOffset <= 0) {
            return true;
        }
        int delta = amount > 0 ? -1 : 1;
        schematicDropdownScrollOffset = MathHelper.clamp(schematicDropdownScrollOffset + delta, 0, layout.maxScrollOffset);
        return true;
    }

    public boolean handleRunPresetDropdownScroll(double screenX, double screenY, double amount) {
        if (!runPresetDropdownOpen || runPresetDropdownNode == null || runPresetDropdownOptions.isEmpty()) {
            return false;
        }
        if (!isPointInsideRunPresetDropdownList(runPresetDropdownNode, (int) screenX, (int) screenY)) {
            return false;
        }
        int listTop = runPresetDropdownNode.getStopTargetFieldInputTop() + runPresetDropdownNode.getStopTargetFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            runPresetDropdownOptions.size(),
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        if (layout.maxScrollOffset <= 0) {
            return true;
        }
        int delta = amount > 0 ? -1 : 1;
        runPresetDropdownScrollOffset = MathHelper.clamp(runPresetDropdownScrollOffset + delta, 0, layout.maxScrollOffset);
        return true;
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

    private boolean isPointInsideSchematicDropdownList(Node node, int screenX, int screenY) {
        if (node == null || !schematicDropdownOpen || schematicDropdownNode != node) {
            return false;
        }
        int optionCount = Math.max(1, schematicDropdownOptions.size());
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int listHeight = layout.height;

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int listLeft = node.getSchematicFieldLeft();
        int worldListTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2;

        int dropdownWidth = getSchematicDropdownWidth(node);
        return worldX >= listLeft && worldX <= listLeft + dropdownWidth
            && worldY >= worldListTop && worldY <= worldListTop + listHeight;
    }

    private boolean isPointInsideRunPresetDropdownList(Node node, int screenX, int screenY) {
        if (!isPresetSelectorNode(node) || !runPresetDropdownOpen || runPresetDropdownNode != node) {
            return false;
        }
        int optionCount = Math.max(1, runPresetDropdownOptions.size());
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int listHeight = layout.height;

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int listLeft = node.getStopTargetFieldLeft();
        int worldListTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2;

        int dropdownWidth = getRunPresetDropdownWidth(node);
        return worldX >= listLeft && worldX <= listLeft + dropdownWidth
            && worldY >= worldListTop && worldY <= worldListTop + listHeight;
    }

    private int getSchematicDropdownIndexAt(Node node, int screenX, int screenY) {
        if (node == null || schematicDropdownOptions.isEmpty()) {
            return -1;
        }
        int worldY = screenToWorldY(screenY);
        int worldListTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2;
        int row = (worldY - worldListTop) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
        if (row < 0) {
            return -1;
        }
        int optionCount = schematicDropdownOptions.size();
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        if (row >= visibleCount) {
            return -1;
        }
        return schematicDropdownScrollOffset + row;
    }

    private int getRunPresetDropdownIndexAt(Node node, int screenX, int screenY) {
        if (node == null || runPresetDropdownOptions.isEmpty()) {
            return -1;
        }
        int worldY = screenToWorldY(screenY);
        int worldListTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2;
        int row = (worldY - worldListTop) / SCHEMATIC_DROPDOWN_ROW_HEIGHT;
        if (row < 0) {
            return -1;
        }
        int optionCount = runPresetDropdownOptions.size();
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - cameraY;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            SCHEMATIC_DROPDOWN_ROW_HEIGHT,
            SCHEMATIC_DROPDOWN_MAX_ROWS,
            listTop,
            screenHeight
        );
        int visibleCount = layout.visibleCount;
        if (row >= visibleCount) {
            return -1;
        }
        return runPresetDropdownScrollOffset + row;
    }

    private void openSchematicDropdown(Node node) {
        schematicDropdownNode = node;
        schematicDropdownOptions = loadSchematicOptions();
        String current = "";
        if (node != null) {
            NodeParameter param = node.getParameter("Schematic");
            current = param != null ? param.getStringValue() : "";
        }
        if (current != null && !current.isEmpty()
            && !schematicDropdownOptions.contains(current)
            && schematicExistsInRoots(current)) {
            schematicDropdownOptions.add(0, current);
        }
        schematicDropdownOpen = true;
        schematicDropdownScrollOffset = 0;
        schematicDropdownHoverIndex = -1;
    }

    private void closeSchematicDropdown() {
        schematicDropdownOpen = false;
        schematicDropdownHoverIndex = -1;
    }

    private void openRunPresetDropdown(Node node) {
        if (!isPresetSelectorNode(node)) {
            return;
        }
        runPresetDropdownNode = node;
        runPresetDropdownOptions = new ArrayList<>(PresetManager.getAvailablePresets());
        String current = "";
        NodeParameter param = node.getParameter(getStopTargetParameterKey(node));
        if (param != null && param.getStringValue() != null) {
            current = param.getStringValue();
        }
        String normalizedCurrent = current != null ? current.trim() : "";
        if (!normalizedCurrent.isEmpty()
            && runPresetDropdownOptions.stream().noneMatch(option -> option.equalsIgnoreCase(normalizedCurrent))) {
            runPresetDropdownOptions.add(0, normalizedCurrent);
        }
        runPresetDropdownOpen = true;
        runPresetDropdownScrollOffset = 0;
        runPresetDropdownHoverIndex = -1;
    }

    private void closeRunPresetDropdown() {
        runPresetDropdownOpen = false;
        runPresetDropdownHoverIndex = -1;
    }

    private void openAmountSignDropdown(Node node) {
        amountSignDropdownNode = node;
        amountSignDropdownOpen = true;
        amountSignDropdownScrollOffset = 0;
        amountSignDropdownHoverIndex = -1;
    }

    private void closeAmountSignDropdown() {
        amountSignDropdownOpen = false;
        amountSignDropdownHoverIndex = -1;
    }

    private void openRandomRoundingDropdown(Node node) {
        randomRoundingDropdownNode = node;
        randomRoundingDropdownOpen = true;
        randomRoundingDropdownScrollOffset = 0;
        randomRoundingDropdownHoverIndex = -1;
    }

    private void closeRandomRoundingDropdown() {
        randomRoundingDropdownOpen = false;
        randomRoundingDropdownHoverIndex = -1;
    }

    private float getDropdownAnimationProgress(AnimatedValue animation, boolean open) {
        animation.animateTo(open ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        animation.tick();
        return AnimationHelper.easeOutQuad(animation.getValue());
    }

    private void enableDropdownScissor(DrawContext context, int x, int y, int width, int height) {
        context.enableScissor(x, y, x + Math.max(1, width), y + Math.max(1, height));
    }

    private void clearSchematicDropdownState() {
        if (schematicDropdownOpen) {
            return;
        }
        schematicDropdownNode = null;
        schematicDropdownHoverIndex = -1;
        schematicDropdownScrollOffset = 0;
    }

    private void clearRunPresetDropdownState() {
        if (runPresetDropdownOpen) {
            return;
        }
        runPresetDropdownNode = null;
        runPresetDropdownOptions = new ArrayList<>();
        runPresetDropdownHoverIndex = -1;
        runPresetDropdownScrollOffset = 0;
    }

    private void clearAmountSignDropdownState() {
        if (amountSignDropdownOpen) {
            return;
        }
        amountSignDropdownNode = null;
        amountSignDropdownHoverIndex = -1;
        amountSignDropdownScrollOffset = 0;
    }

    private void clearRandomRoundingDropdownState() {
        if (randomRoundingDropdownOpen) {
            return;
        }
        randomRoundingDropdownNode = null;
        randomRoundingDropdownHoverIndex = -1;
        randomRoundingDropdownScrollOffset = 0;
    }

    private void clearParameterDropdownState() {
        if (parameterDropdownOpen) {
            return;
        }
        parameterDropdownNode = null;
        parameterDropdownIndex = -1;
        parameterDropdownHoverIndex = -1;
        parameterDropdownScrollOffset = 0;
        parameterDropdownQuery = "";
        parameterDropdownOptions.clear();
    }

    private void clearModeDropdownState() {
        if (modeDropdownOpen) {
            return;
        }
        modeDropdownNode = null;
        modeDropdownHoverIndex = -1;
        modeDropdownScrollOffset = 0;
        modeDropdownOptions.clear();
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
        if (isEditingStopTargetField() && stopTargetEditingNode == node) {
            stopTargetEditBuffer = value;
            stopTargetEditOriginalValue = value;
            stopTargetCaretPosition = stopTargetEditBuffer.length();
            stopTargetSelectionAnchor = -1;
            stopTargetSelectionStart = -1;
            stopTargetSelectionEnd = -1;
            resetStopTargetCaretBlink();
            updateStopTargetFieldContentWidth(getClientTextRenderer());
        }
        if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
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

    private List<String> loadSchematicOptions() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return List.of();
        }

        Path runDir = client.runDirectory.toPath();
        List<Path> roots = new ArrayList<>();
        roots.add(runDir.resolve("schematics"));
        roots.add(runDir.resolve("baritone").resolve("schematics"));
        roots.add(runDir.resolve("litematica").resolve("schematics"));
        roots.addAll(resolveMinecraftSchematicRoots());

        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root, 12)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".schem") || name.endsWith(".schematic") || name.endsWith(".nbt")
                            || name.endsWith(".litematic");
                    })
                    .forEach(path -> {
                        Path relative = root.relativize(path);
                        String normalized = relative.toString().replace(java.io.File.separatorChar, '/');
                        results.add(normalized);
                    });
            } catch (Exception ignored) {
            }
        }

        List<String> options = new ArrayList<>(results);
        Collections.sort(options);
        return options;
    }

    private List<Path> resolveMinecraftSchematicRoots() {
        List<Path> roots = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home == null) {
            return roots;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            Path macRoot = Path.of(home, "Library", "Application Support", "minecraft");
            roots.add(macRoot.resolve("schematics"));
            roots.add(macRoot.resolve("baritone").resolve("schematics"));
            roots.add(macRoot.resolve("litematica").resolve("schematics"));
            Path dotRoot = Path.of(home, ".minecraft");
            roots.add(dotRoot.resolve("schematics"));
            roots.add(dotRoot.resolve("baritone").resolve("schematics"));
            roots.add(dotRoot.resolve("litematica").resolve("schematics"));
        } else if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                Path winRoot = Path.of(appData, ".minecraft");
                roots.add(winRoot.resolve("schematics"));
                roots.add(winRoot.resolve("baritone").resolve("schematics"));
                roots.add(winRoot.resolve("litematica").resolve("schematics"));
            }
        } else {
            Path linuxRoot = Path.of(home, ".minecraft");
            roots.add(linuxRoot.resolve("schematics"));
            roots.add(linuxRoot.resolve("baritone").resolve("schematics"));
            roots.add(linuxRoot.resolve("litematica").resolve("schematics"));
        }
        return roots;
    }

    private boolean schematicExistsInRoots(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return false;
        }
        Path runDir = client.runDirectory.toPath();
        List<Path> roots = new ArrayList<>();
        roots.add(runDir.resolve("schematics"));
        roots.add(runDir.resolve("baritone").resolve("schematics"));
        roots.add(runDir.resolve("litematica").resolve("schematics"));
        roots.addAll(resolveMinecraftSchematicRoots());
        for (Path root : roots) {
            if (Files.isRegularFile(root.resolve(value))) {
                return true;
            }
        }
        return false;
    }

    private void drawNodeText(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color) {
        if (!shouldRenderNodeText()) {
            return;
        }
        context.drawText(renderer, text, x, y, color, false);
    }

    private void drawNodeText(DrawContext context, TextRenderer renderer, String text, int x, int y, int color) {
        drawNodeText(context, renderer, Text.literal(text), x, y, color);
    }

    private String trimTextToWidth(String text, TextRenderer renderer, int maxWidth) {
        if (renderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int safeMaxWidth = Math.max(0, maxWidth);
        return TextRenderUtil.trimWithEllipsis(renderer, text, safeMaxWidth);
    }

    private void renderSocket(DrawContext context, int x, int y, boolean isInput, int color) {
        // Socket circle
        context.fill(x - 3, y - 3, x + 3, y + 3, color);
        DrawContextBridge.drawBorderInLayer(context, x - 3, y - 3, 6, 6, UITheme.BORDER_SOCKET);
        
        // Socket highlight
        context.fill(x - 1, y - 1, x + 1, y + 1, UITheme.TEXT_PRIMARY);
    }

    private boolean isSocketConnected(Node node, int socketIndex, boolean isInput) {
        for (NodeConnection connection : connections) {
            if (isInput) {
                if (connection.getInputNode() == node && connection.getInputSocket() == socketIndex) {
                    return true;
                }
            } else {
                if (connection.getOutputNode() == node && connection.getOutputSocket() == socketIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSocketActive(Node node, int socketIndex, boolean isInput) {
        if (isSocketConnected(node, socketIndex, isInput)) {
            return true;
        }
        if (isDraggingConnection && connectionSourceNode == node && connectionSourceSocket == socketIndex) {
            // Treat the drag source as active so it stays bright while connecting.
            if ((isInput && !isOutputSocket) || (!isInput && isOutputSocket)) {
                return true;
            }
        }
        return false;
    }

    private int darkenColor(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        red = Math.min(255, Math.max(0, Math.round(red * factor)));
        green = Math.min(255, Math.max(0, Math.round(green * factor)));
        blue = Math.min(255, Math.max(0, Math.round(blue * factor)));

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private void renderConnections(DrawContext context, boolean onlyDragged) {
        ExecutionManager manager = ExecutionManager.getInstance();
        boolean animateConnections = manager.isExecuting();
        long animationTimestamp = System.currentTimeMillis();

        if (!onlyDragged) {
            for (NodeConnection connection : connections) {
                Node outputNode = connection.getOutputNode();
                Node inputNode = connection.getInputNode();

                if (!outputNode.shouldRenderSockets() || !inputNode.shouldRenderSockets()) {
                    continue;
                }

                int outputX = outputNode.getSocketX(false) - cameraX;
                int outputY = outputNode.getSocketY(connection.getOutputSocket(), false) - cameraY;
                int inputX = inputNode.getSocketX(true) - cameraX;
                int inputY = inputNode.getSocketY(connection.getInputSocket(), true) - cameraY;
                int viewportWidth = getViewportWorldWidth();
                int viewportHeight = getViewportWorldHeight();
                int minX = Math.min(outputX, inputX) - VIEWPORT_CULL_MARGIN;
                int maxX = Math.max(outputX, inputX) + VIEWPORT_CULL_MARGIN;
                int minY = Math.min(outputY, inputY) - VIEWPORT_CULL_MARGIN;
                int maxY = Math.max(outputY, inputY) + VIEWPORT_CULL_MARGIN;
                if (maxX < 0 || minX > viewportWidth || maxY < 0 || minY > viewportHeight) {
                    continue;
                }

                int color = outputNode.getOutputSocketColor(connection.getOutputSocket());
                if (shouldGrayOutConnection(outputNode, inputNode)) {
                    color = toGrayscale(color, 0.65f);
                }

                // Simple bezier-like curve
                if (animateConnections && manager.shouldAnimateConnection(connection)) {
                    renderAnimatedConnectionCurve(context, outputX, outputY, inputX, inputY,
                            color, animationTimestamp);
                } else {
                    renderConnectionCurve(context, outputX, outputY, inputX, inputY,
                            color);
                }
            }
        }

        // Render dragging connection if active
        if (isDraggingConnection && connectionSourceNode != null) {
            int sourceX = connectionSourceNode.getSocketX(!isOutputSocket) - cameraX;
            int sourceY = connectionSourceNode.getSocketY(connectionSourceSocket, !isOutputSocket) - cameraY;
            int targetX = connectionDragX - cameraX;
            int targetY = connectionDragY - cameraY;

            // Snap to hovered socket if available
            if (hoveredNode != null && hoveredSocket != -1) {
                targetX = hoveredNode.getSocketX(hoveredSocketIsInput) - cameraX;
                targetY = hoveredNode.getSocketY(hoveredSocket, hoveredSocketIsInput) - cameraY;

                if (onlyDragged) {
                    // Highlight the target socket above nodes while dragging.
                    renderSocket(context, targetX, targetY, hoveredSocketIsInput, getSelectedNodeAccentColor());
                }
            }

            if (!onlyDragged) {
                // Render the dragging connection below sockets in the main layer.
                int color = connectionSourceNode.getOutputSocketColor(connectionSourceSocket);
                int sourceScreenX = connectionSourceNode.getX() - cameraX;
                if (isNodeOverSidebarForRender(connectionSourceNode, sourceScreenX, connectionSourceNode.getWidth())) {
                    color = toGrayscale(color, 0.65f);
                }

                if (animateConnections) {
                    renderAnimatedConnectionCurve(context, sourceX, sourceY, targetX, targetY,
                            color, animationTimestamp);
                } else {
                    renderConnectionCurve(context, sourceX, sourceY, targetX, targetY,
                            color);
                }
            }
        }
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

    private boolean shouldGrayOutConnection(Node outputNode, Node inputNode) {
        if (outputNode == null || inputNode == null) {
            return false;
        }
        int outputScreenX = outputNode.getX() - cameraX;
        int inputScreenX = inputNode.getX() - cameraX;
        return isNodeOverSidebarForRender(outputNode, outputScreenX, outputNode.getWidth())
            || isNodeOverSidebarForRender(inputNode, inputScreenX, inputNode.getWidth());
    }

    private boolean isNodeInDraggedHierarchy(Node node) {
        if (node == null) {
            return false;
        }
        if (node.isDragging()) {
            return true;
        }
        Node parent = getParentForNode(node);
        while (parent != null) {
            if (parent.isDragging()) {
                return true;
            }
            parent = getParentForNode(parent);
        }
        return false;
    }

    private void renderAnimatedConnectionCurve(DrawContext context, int x1, int y1, int x2, int y2, int color, long timestamp) {
        int midX = x1 + (x2 - x1) / 2;

        int firstSegmentLength = Math.abs(midX - x1);
        int secondSegmentLength = Math.abs(y2 - y1);

        int animationOffset = (int) ((timestamp / CONNECTION_ANIMATION_STEP_MS) % CONNECTION_DOT_SPACING);

        drawAnimatedSegment(context, x1, y1, midX, y1, true, color, animationOffset, 0);
        drawAnimatedSegment(context, midX, y1, midX, y2, false, color, animationOffset, firstSegmentLength);
        drawAnimatedSegment(context, midX, y2, x2, y2, true, color, animationOffset,
                firstSegmentLength + secondSegmentLength);
    }

    private void drawAnimatedSegment(DrawContext context, int x1, int y1, int x2, int y2, boolean horizontal,
                                     int color, int animationOffset, int distanceOffset) {
        int length = horizontal ? Math.abs(x2 - x1) : Math.abs(y2 - y1);
        if (length == 0) {
            return;
        }

        int direction = horizontal ? Integer.compare(x2, x1) : Integer.compare(y2, y1);
        int start = horizontal ? x1 : y1;
        int staticCoord = horizontal ? y1 : x1;

        int initialOffset = mod(distanceOffset - animationOffset, CONNECTION_DOT_SPACING);
        int stepStart = (CONNECTION_DOT_SPACING - initialOffset) % CONNECTION_DOT_SPACING;

        int position = stepStart;
        while (position > 0) {
            position -= CONNECTION_DOT_SPACING;
        }

        boolean drewSegment = false;

        for (; position <= length; position += CONNECTION_DOT_SPACING) {
            int minDistance = Math.max(position, 0);
            int maxDistance = Math.min(position + CONNECTION_DOT_LENGTH - 1, length);
            if (maxDistance < 0 || minDistance > length || minDistance > maxDistance) {
                continue;
            }

            drewSegment = true;

            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.drawHorizontalLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.drawVerticalLine(staticCoord, minY, maxY, color);
            }
        }

        if (!drewSegment) {
            int fallbackLength = Math.min(CONNECTION_DOT_LENGTH, length);
            int minDistance = Math.max(0, length - fallbackLength);
            int maxDistance = length;
            int startPos = start + minDistance * direction;
            int endPos = start + maxDistance * direction;

            if (horizontal) {
                int minX = Math.min(startPos, endPos);
                int maxX = Math.max(startPos, endPos);
                context.drawHorizontalLine(minX, maxX, staticCoord, color);
            } else {
                int minY = Math.min(startPos, endPos);
                int maxY = Math.max(startPos, endPos);
                context.drawVerticalLine(staticCoord, minY, maxY, color);
            }
        }
    }

    private int mod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private void renderConnectionCurve(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // Draw a simple L-shaped connection line
        int midX = x1 + (x2 - x1) / 2;
        
        // Horizontal line from source to middle
        context.drawHorizontalLine(Math.min(x1, midX), Math.max(x1, midX), y1, color);
        
        // Vertical line from middle to target
        context.drawVerticalLine(midX, Math.min(y1, y2), Math.max(y1, y2), color);
        
        // Horizontal line from middle to target
        context.drawHorizontalLine(Math.min(midX, x2), Math.max(midX, x2), y2, color);
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
        int centerX = startNode.getX() + startNode.getWidth() / 2;
        int centerY = startNode.getY() + startNode.getHeight() / 2;
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        
        // Check if mouse is within the triangle area
        int triangleSize = 10;
        int offset = 1;
        int startX = centerX - triangleSize/2 + offset;
        
        // Simple bounding box check for the triangle
        return worldMouseX >= startX && worldMouseX <= startX + triangleSize &&
               worldMouseY >= centerY - triangleSize/2 && worldMouseY <= centerY + triangleSize/2;
    }
    
    public boolean isHoveringStartButton() {
        return hoveringStartButton;
    }

    public boolean handleStartButtonClick(int mouseX, int mouseY) {
        lastStartButtonTriggeredExecution = false;
        if (!executionEnabled) {
            return false;
        }
        Node startNode = findStartNodeAt(mouseX, mouseY);
        if (startNode == null) {
            return false;
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);

        hoveredStartNode = startNode;

        ExecutionManager manager = ExecutionManager.getInstance();
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
        for (Node node : nodes) {
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
        boolean saved = NodeGraphPersistence.saveNodeGraphForPreset(activePreset, nodes, connections);
        if (saved) {
            workspaceDirty = false;
            invalidateTemplatePreviewCachesForPreset(activePreset);
        }
        return saved;
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
        if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
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
            cachedValidationResult = GraphValidator.validate(nodes, connections, activePreset, baritoneAvailable, uiUtilsAvailable);
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
            if (candidate == null || (candidate.getType() != NodeType.TEMPLATE && candidate.getType() != NodeType.CUSTOM_NODE)) {
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
            if (candidate != null && (candidate.getType() == NodeType.TEMPLATE || candidate.getType() == NodeType.CUSTOM_NODE)) {
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
        nextStartNodeNumber = 1;
        clearSelection();
        draggingNode = null;
        hoveredNode = null;
        hoveredSocketNode = null;
        hoveredSocketIndex = -1;
        hoveredSocket = -1;
        invalidateValidation();
        hoveredSocketIsInput = false;
        hoveringStartButton = false;
        hoveredStartNode = null;
        isDraggingConnection = false;
        connectionSourceNode = null;
        disconnectedConnection = null;
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
        nodes.clear();
        connections.clear();
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
            if ((node.getType() == NodeType.STOP_CHAIN || node.getType() == NodeType.START_CHAIN)
                && node.getParameter("StartNumber") == null) {
                node.getParameters().add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
            }
            if ((node.getType() == NodeType.RUN_PRESET || node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) && node.getParameter("Preset") == null) {
                node.getParameters().add(new NodeParameter("Preset", ParameterType.STRING, ""));
            }
            node.ensureVillagerTradeNumberParameter();
            Integer startNodeNumber = nodeData.getStartNodeNumber();
            if (startNodeNumber != null) {
                node.setStartNodeNumber(startNodeNumber);
            }
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                Boolean storedValue = nodeData.getKeyPressedActivatesInGuis();
                node.setKeyPressedActivatesInGuis(storedValue == null || storedValue);
            }
            if (node.hasBooleanToggle()) {
                Boolean storedToggle = nodeData.getBooleanToggleValue();
                node.setBooleanToggleValue(storedToggle == null || storedToggle);
            }
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
            if (nodeData.getType() == NodeType.MESSAGE && nodeData.getMessageLines() != null) {
                Node messageNode = nodeMap.get(nodeData.getId());
                if (messageNode != null) {
                    messageNode.setMessageLines(nodeData.getMessageLines());
                    messageNode.setMessageClientSide(Boolean.TRUE.equals(nodeData.getMessageClientSide()));
                }
            }
            Node textNode = nodeMap.get(nodeData.getId());
            if (textNode != null && textNode.hasBookTextInput() && nodeData.getBookText() != null) {
                textNode.setBookText(nodeData.getBookText());
            }
            if (textNode != null && (textNode.getType() == NodeType.TEMPLATE || textNode.getType() == NodeType.CUSTOM_NODE)) {
                textNode.setTemplateName(nodeData.getTemplateName());
                textNode.setTemplateVersion(nodeData.getTemplateVersion() != null ? nodeData.getTemplateVersion() : 0);
                textNode.setCustomNodeInstance(Boolean.TRUE.equals(nodeData.getCustomNodeInstance()));
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
                            host.attachParameter(parameter, attachment.getSlotIndex());
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

        // Load connections
        for (NodeGraphData.ConnectionData connData : data.getConnections()) {
            Node outputNode = nodeMap.get(connData.getOutputNodeId());
            Node inputNode = nodeMap.get(connData.getInputNodeId());

            if (outputNode != null && inputNode != null) {
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                NodeConnection connection = new NodeConnection(
                    outputNode,
                    inputNode,
                    connData.getOutputSocket(),
                    connData.getInputSocket()
                );
                connections.add(connection);
            } else {
                System.err.println("Failed to restore connection: missing node(s)");
            }
        }

        sensorDropTarget = null;
        actionDropTarget = null;
        hoveredNode = null;
        hoveredSocketNode = null;
        hoveredSocketIndex = -1;
        hoveredSocket = -1;
        hoveredSocketIsInput = false;
        hoveringStartButton = false;
        hoveredStartNode = null;
        isDraggingConnection = false;
        connectionSourceNode = null;
        disconnectedConnection = null;
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
        return buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
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
}
