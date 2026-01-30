package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.DropdownLayoutHelper;
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
import java.util.stream.Stream;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;

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

    private final List<Node> nodes;
    private final List<NodeConnection> connections;
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
    
    // Double-click detection
    private long lastClickTime = 0;
    private Node lastClickedNode = null;
    private static final long DOUBLE_CLICK_THRESHOLD = 300; // milliseconds
    private int sidebarWidthForRendering = 180;
    private boolean executionEnabled = true;

    private String activePreset;
    private final Set<Node> cascadeDeletionPreviewNodes;

    private static final long COORDINATE_CARET_BLINK_INTERVAL_MS = 500;
    private static final String[] COORDINATE_AXES = {"X", "Y", "Z"};
    private static final int PARAMETER_INPUT_HEIGHT = 16;
    private static final int PARAMETER_INPUT_GAP = 4;

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
    private Node schematicDropdownNode = null;
    private boolean schematicDropdownOpen = false;
    private java.util.List<String> schematicDropdownOptions = new java.util.ArrayList<>();
    private int schematicDropdownScrollOffset = 0;
    private int schematicDropdownHoverIndex = -1;
    private static final int SCHEMATIC_DROPDOWN_MAX_ROWS = 8;
    private static final int SCHEMATIC_DROPDOWN_ROW_HEIGHT = 16;
    private Node parameterDropdownNode = null;
    private int parameterDropdownIndex = -1;
    private boolean parameterDropdownOpen = false;
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
    private Node modeDropdownNode = null;
    private boolean modeDropdownOpen = false;
    private int modeDropdownHoverIndex = -1;
    private int modeDropdownScrollOffset = 0;
    private int modeDropdownFieldX = 0;
    private int modeDropdownFieldY = 0;
    private int modeDropdownFieldWidth = 0;
    private int modeDropdownFieldHeight = 0;
    private final java.util.List<ModeDropdownOption> modeDropdownOptions = new java.util.ArrayList<>();
    private static final int PARAMETER_DROPDOWN_MAX_ROWS = 8;
    private static final int PARAMETER_DROPDOWN_ROW_HEIGHT = 16;
    private boolean workspaceDirty = false;
    private int nextStartNodeNumber = 1;
    private static final float ZOOM_SCROLL_STEP = 1.12f;
    private static final float ZOOM_EPSILON = 0.0001f;
    private ZoomLevel zoomLevel = ZoomLevel.FOCUSED;
    private float zoomScale = ZoomLevel.FOCUSED.getScale();
    private ClipboardSnapshot clipboardNodeSnapshot = null;
    private final Deque<NodeGraphData> undoStack = new ArrayDeque<>();
    private final Deque<NodeGraphData> redoStack = new ArrayDeque<>();
    private boolean suppressUndoCapture = false;
    private static final int MAX_HISTORY = 50;
    private boolean selectionBoxActive = false;
    private int selectionBoxStartX = 0;
    private int selectionBoxStartY = 0;
    private int selectionBoxCurrentX = 0;
    private int selectionBoxCurrentY = 0;
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
    }

    private void assignNewStartNodeNumber(Node node) {
        if (node == null || node.getType() != NodeType.START) {
            return;
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
        if (messageEditingNode == node) {
            stopMessageEditing(false);
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

        connections.removeIf(conn ->
            conn.getOutputNode().equals(node) || conn.getInputNode().equals(node));
        nodes.remove(node);

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
            }
            if (nodeData.getType() == NodeType.MESSAGE && nodeData.getMessageLines() != null) {
                newNode.setMessageLines(nodeData.getMessageLines());
            }
            if (nodeData.getType() == NodeType.WRITE_BOOK && nodeData.getBookText() != null) {
                newNode.setBookText(nodeData.getBookText());
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
            } else {
                nodeData.setMessageLines(null);
            }
            if (node.hasBookTextInput()) {
                nodeData.setBookText(node.getBookText());
            } else {
                nodeData.setBookText(null);
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
        stopParameterEditing(true);
        stopStopTargetEditing(true);
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
        
        System.out.println("Started dragging connection from " + (isOutput ? "output" : "input") + 
                         " socket " + socketIndex + " of node " + node.getType() + 
                         (disconnectedConnection != null ? " (disconnected existing connection)" : ""));
    }

    public void updateDrag(int mouseX, int mouseY) {
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        if (draggingNode != null) {
            int newX = worldMouseX - draggingNode.getDragOffsetX();
            int newY = worldMouseY - draggingNode.getDragOffsetY();

            if (multiDragActive) {
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
                    draggingNode.setPosition(newX, newY);

                    boolean hideSockets = false;
                    if (draggingNode.isSensorNode()) {
                        resetDropTargets();
                        for (Node node : nodes) {
                            if (!node.canAcceptSensor() || node == draggingNode) {
                                continue;
                            }
                            if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                                sensorDropTarget = node;
                                hideSockets = true;
                                break;
                            }
                        }
                    } else if (draggingNode.isParameterNode()) {
                        resetDropTargets();
                        for (Node node : nodes) {
                            if (!node.canAcceptParameter() || node == draggingNode) {
                                continue;
                            }
                            int slotIndex = node.getParameterSlotIndexAt(worldMouseX, worldMouseY);
                            if (slotIndex >= 0 && node.canAcceptParameterNode(draggingNode, slotIndex)) {
                                parameterDropTarget = node;
                                parameterDropSlotIndex = slotIndex;
                                hideSockets = true;
                                break;
                            }
                        }
                    } else {
                        resetDropTargets();
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
        resetDropTargets();
        if (nodeType == null) {
            return;
        }

        if (Node.isSensorType(nodeType)) {
            for (Node node : nodes) {
                if (!node.canAcceptSensor()) {
                    continue;
                }
                if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                    sensorDropTarget = node;
                    break;
                }
            }
        } else if (Node.isParameterType(nodeType)) {
            Node parameterCandidate = new Node(nodeType, worldMouseX, worldMouseY);
            for (Node node : nodes) {
                if (!node.canAcceptParameter()) {
                    continue;
                }
                int slotIndex = node.getParameterSlotIndexAt(worldMouseX, worldMouseY);
                if (slotIndex >= 0 && node.canAcceptParameterNode(parameterCandidate, slotIndex)) {
                    parameterDropTarget = node;
                    parameterDropSlotIndex = slotIndex;
                    break;
                }
            }
        } else {
            Node candidate = new Node(nodeType, worldMouseX, worldMouseY);
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

    public Node handleSidebarDrop(NodeType nodeType, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        if (nodeType == null) {
            return null;
        }

        Node newNode = new Node(nodeType, 0, 0);
        if (nodeType == NodeType.START) {
            assignNewStartNodeNumber(newNode);
        }

        if (Node.isSensorType(nodeType)) {
            for (Node node : nodes) {
                if (!node.canAcceptSensor()) {
                    continue;
                }
                if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                    nodes.add(newNode);
                    node.attachSensor(newNode);
                    markWorkspaceDirty();
                    return newNode;
                }
            }
        } else if (Node.isParameterType(nodeType)) {
            for (Node node : nodes) {
                if (!node.canAcceptParameter()) {
                    continue;
                }
                int slotIndex = node.getParameterSlotIndexAt(worldMouseX, worldMouseY);
                if (slotIndex >= 0 && node.canAcceptParameterNode(newNode, slotIndex)) {
                    nodes.add(newNode);
                    node.attachParameter(newNode, slotIndex);
                    markWorkspaceDirty();
                    return newNode;
                }
            }
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

        int nodeX = worldMouseX - newNode.getWidth() / 2;
        int nodeY = worldMouseY - newNode.getHeight() / 2;
        newNode.setPosition(nodeX, nodeY);
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
            } else if (node.isSensorNode() && sensorDropTarget != null) {
                Node target = sensorDropTarget;
                node.setDragging(false);
                if (!target.attachSensor(node)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = getRootNode(target);
            } else if (node.isParameterNode() && parameterDropTarget != null && parameterDropSlotIndex != null) {
                Node target = parameterDropTarget;
                int slotIndex = parameterDropSlotIndex;
                node.setDragging(false);
                if (!target.attachParameter(node, slotIndex)) {
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

        if (draggingNode.isParameterNode() && draggingNode.getParentParameterHost() != null) {
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
                    System.out.println("Created new connection from " + connectionSourceNode.getType() + " to " + hoveredNode.getType());
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
                    System.out.println("Created new connection from " + hoveredNode.getType() + " to " + connectionSourceNode.getType());
                } else {
                    // Invalid connection - restore original
                    restoreDisconnectedConnection();
                    System.out.println("Restored original connection (invalid target)");
                }
            } else {
                // No valid target - decide whether to keep the removal
                if (disconnectedConnection != null && isOutputSocket && !connectionDragMoved) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    connectionChanged = true;
                    System.out.println("Disconnected connection from " + connectionSourceNode.getType() + " socket " + connectionSourceSocket);
                } else {
                    restoreDisconnectedConnection();
                    System.out.println("Restored original connection (no target)");
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
    
    // Convert world coordinates to screen coordinates
    public int worldToScreenX(int worldX) {
        return Math.round((worldX - cameraX) * getZoomScale());
    }
    
    public int worldToScreenY(int worldY) {
        return Math.round((worldY - cameraY) * getZoomScale());
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
        if (node.getType().getCategory() == NodeCategory.LOGIC) {
            return true;
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
            // Lift dragged nodes above other GUI layers on matrix-stack renderers.
            MatrixStackBridge.translateZ(matrices, 250.0f);
        }

        if (!onlyDragged) {
            updateCascadeDeletionPreview();
        }
        renderConnections(context, onlyDragged);

        Set<Node> processedRoots = new HashSet<>();
        Set<Node> renderedNodes = new HashSet<>();

        for (Node node : nodes) {
            Node root = getRootNode(node);
            if (root == null || processedRoots.contains(root)) {
                continue;
            }
            processedRoots.add(root);
            renderHierarchy(root, context, textRenderer, mouseX, mouseY, delta, onlyDragged, false, renderedNodes);
        }

        MatrixStackBridge.pop(matrices);
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
        DrawContextBridge.drawBorder(context, left, top, right - left, bottom - top, borderColor);
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

    private void renderNode(DrawContext context, TextRenderer textRenderer, Node node, int mouseX, int mouseY, float delta) {
        int x = node.getX() - cameraX;
        int y = node.getY() - cameraY;
        int width = node.getWidth();
        int height = node.getHeight();

        // Check if node is being dragged over sidebar (grey-out effect)
        // Use screen coordinates (with camera offset) for this check
        boolean isOverSidebar = isNodeOverSidebarForRender(node, x, width);

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
            borderColor = UITheme.ACCENT_DEFAULT; // Light blue selection
        } else if (node.getType() == NodeType.START) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_START_BORDER, 0.75f) : UITheme.NODE_START_BORDER; // Darker green for START
        } else if (node.getType() == NodeType.EVENT_FUNCTION) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_BORDER, 0.75f) : UITheme.NODE_EVENT_BORDER; // Darker pink for event functions
        } else if (node.getType() == NodeType.EVENT_CALL) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_CALL_BG, 0.75f) : UITheme.NODE_EVENT_CALL_BG;
        } else if (node.getType() == NodeType.VARIABLE) {
            borderColor = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_BORDER, 0.75f) : UITheme.NODE_VARIABLE_BORDER; // Darker orange for variables
        } else if (node.getType() == NodeType.OPERATOR_EQUALS || node.getType() == NodeType.OPERATOR_NOT) {
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
        DrawContextBridge.drawBorder(context, x, y, width, height, borderColor);

        // Node header (only for non-START/event function nodes)
        if (simpleStyle) {
            String label = node.getType().getDisplayName().toUpperCase(Locale.ROOT);
            int titleColor = isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY;
            int textX;
            int textY;
            if (node.hasStopTargetInputField()) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                textX = contentLeft + 4;
                textY = y + 4;
            } else {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                int contentWidth = Math.max(0, width - MINIMAL_NODE_TAB_WIDTH);
                String displayLabel = trimTextToWidth(label, textRenderer, Math.max(0, contentWidth - 8));
                int textWidth = textRenderer.getWidth(displayLabel);
                textX = contentLeft + Math.max(4, (contentWidth - textWidth) / 2);
                textY = y + (height - textRenderer.fontHeight) / 2;
                label = displayLabel;
            }
            drawNodeText(context, textRenderer, label, textX, textY, titleColor);
        } else if (node.getType() != NodeType.START
            && node.getType() != NodeType.EVENT_FUNCTION
            && node.getType() != NodeType.VARIABLE
            && node.getType() != NodeType.OPERATOR_EQUALS
            && node.getType() != NodeType.OPERATOR_NOT) {
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
                int socketColor = isHovered ? UITheme.ACCENT_DEFAULT : node.getType().getColor(); // Light blue when hovered
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
                int socketColor = isHovered ? UITheme.ACCENT_DEFAULT : node.getOutputSocketColor(i);
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
            DrawContextBridge.drawBorder(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

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
            if (!editingEventName && value.isEmpty()) {
                display = "enter name";
            } else {
                display = value;
            }
            display = editingEventName
                ? display
                : trimTextToWidth(display, textRenderer, boxRight - boxLeft - 8);
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT;
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
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);

            if (editingEventName && eventNameCaretVisible) {
                int caretIndex = MathHelper.clamp(eventNameCaretPosition, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, boxRight - 2);
                context.fill(caretX, boxTop + 2, caretX + 1, boxBottom - 2, UITheme.CARET_COLOR);
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
            int inputBackground = isOverSidebar ? UITheme.NODE_INPUT_BG_DIMMED : UITheme.BACKGROUND_INPUT;
            context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
            int inputBorder = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_INPUT_BORDER, 0.8f) : UITheme.BORDER_SUBTLE;
            DrawContextBridge.drawBorder(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

            NodeParameter nameParam = node.getParameter("Variable");
            String value = nameParam != null ? nameParam.getDisplayValue() : "";
            String display = value.isEmpty() ? "enter variable" : value;
            display = trimTextToWidth(display, textRenderer, boxRight - boxLeft - 8);
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = isOverSidebar ? toGrayscale(UITheme.NODE_VARIABLE_TEXT, 0.85f) : UITheme.NODE_VARIABLE_TEXT;
            drawNodeText(
                context,
                textRenderer,
                Text.literal(display),
                boxLeft + 4,
                textY,
                textColor
            );
        } else if (node.getType() == NodeType.OPERATOR_EQUALS || node.getType() == NodeType.OPERATOR_NOT) {
            int baseColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_BG, 0.7f) : UITheme.NODE_OPERATOR_BG;
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

            int titleColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_TITLE, 0.9f) : UITheme.NODE_OPERATOR_TITLE;
            drawNodeText(
                context,
                textRenderer,
                Text.literal(node.getType() == NodeType.OPERATOR_EQUALS ? "Equals" : "Not"),
                x + 6,
                y + 4,
                titleColor
            );

            renderParameterSlot(context, textRenderer, node, isOverSidebar, 0);
            renderParameterSlot(context, textRenderer, node, isOverSidebar, 1);

            int leftSlotX = node.getParameterSlotLeft(0) - cameraX;
            int rightSlotX = node.getParameterSlotLeft(1) - cameraX;
            int leftSlotWidth = node.getParameterSlotWidth(0);
            int leftSlotHeight = node.getParameterSlotHeight(0);
            int rightSlotHeight = node.getParameterSlotHeight(1);
            int slotTop = node.getParameterSlotTop(0) - cameraY;
            int maxSlotHeight = Math.max(leftSlotHeight, rightSlotHeight);
            int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;
            String operatorText = node.getType() == NodeType.OPERATOR_EQUALS ? "=" : "!=";
            int operatorWidth = textRenderer.getWidth(operatorText);
            int operatorX = gapCenterX - operatorWidth / 2;
            int operatorY = slotTop + (maxSlotHeight - textRenderer.fontHeight) / 2;
            int operatorColor = isOverSidebar ? toGrayscale(UITheme.NODE_OPERATOR_SYMBOL, 0.85f) : UITheme.NODE_OPERATOR_SYMBOL;
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
            DrawContextBridge.drawBorder(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

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
            if (!editingEventName && value.isEmpty()) {
                display = "enter name";
            } else {
                display = value;
            }
            display = editingEventName
                ? display
                : trimTextToWidth(display, textRenderer, boxRight - boxLeft - 8);
            int textY = boxTop + (boxHeight - textRenderer.fontHeight) / 2 + 1;
            int textColor = isOverSidebar ? toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT;
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
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);

            if (editingEventName && eventNameCaretVisible) {
                int caretIndex = MathHelper.clamp(eventNameCaretPosition, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, boxRight - 2);
                context.fill(caretX, boxTop + 2, caretX + 1, boxBottom - 2, UITheme.CARET_COLOR);
            }
            renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else {
            if (node.isParameterNode() || node.shouldRenderInlineParameters()) {
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

                        int fieldBackground = isOverSidebar
                            ? UITheme.BACKGROUND_SECONDARY
                            : UITheme.BACKGROUND_SIDEBAR;
                        int fieldBorder = isOverSidebar
                            ? UITheme.BORDER_SUBTLE
                            : UITheme.BORDER_HIGHLIGHT;

                        context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, fieldBackground);
                        DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, fieldBorder);

                        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
                        int valueColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
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

                        int fieldBackground = isOverSidebar
                            ? UITheme.BACKGROUND_SECONDARY
                            : UITheme.BACKGROUND_SIDEBAR;
                        int fieldBorder = isOverSidebar
                            ? UITheme.BORDER_SUBTLE
                            : UITheme.BORDER_HIGHLIGHT;

                        context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, fieldBackground);
                        DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, fieldBorder);

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
                        String displayValue = editingThis
                            ? value
                            : trimTextToWidth(value, textRenderer, maxValueWidth);
                        int valueY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2;

                        if (editingThis && hasParameterSelection()) {
                            int start = MathHelper.clamp(parameterSelectionStart, 0, displayValue.length());
                            int end = MathHelper.clamp(parameterSelectionEnd, 0, displayValue.length());
                            if (start != end) {
                                int selectionStartX = valueStartX + textRenderer.getWidth(displayValue.substring(0, start));
                                int selectionEndX = valueStartX + textRenderer.getWidth(displayValue.substring(0, end));
                                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldTop + fieldHeight - 2, UITheme.TEXT_SELECTION_BG);
                            }
                        }

                        drawNodeText(context, textRenderer, Text.literal(displayValue), valueStartX, valueY, valueColor);

                        if (editingThis && parameterCaretVisible) {
                            int caretIndex = MathHelper.clamp(parameterCaretPosition, 0, displayValue.length());
                            int caretX = valueStartX + textRenderer.getWidth(displayValue.substring(0, caretIndex));
                            caretX = Math.min(caretX, fieldRight - 2);
                            context.fill(caretX, fieldTop + 2, caretX + 1, fieldTop + fieldHeight - 2, UITheme.CARET_COLOR);
                        }

                        if (editingThis && isBlockItemParameter(node, i)) {
                            updateParameterDropdown(node, i, textRenderer, fieldLeft, fieldTop, fieldWidth, fieldHeight);
                        }

                        paramY += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
                    }
                    if (node.hasPopupEditButton()) {
                        renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    if (parameterDropdownOpen && parameterDropdownNode == node) {
                        renderParameterDropdownList(context, textRenderer, mouseX, mouseY);
                    }
                    if (modeDropdownOpen && modeDropdownNode == node) {
                        renderModeDropdownList(context, textRenderer, mouseX, mouseY);
                    }
                }
            } else {
                if (node.hasStopTargetInputField()) {
                    renderStopTargetInputField(context, textRenderer, node, isOverSidebar);
                }
                if (node.hasSchematicDropdownField()) {
                    renderSchematicDropdownField(context, textRenderer, node, isOverSidebar);
                }
                if (node.hasParameterSlot()) {
                    int slotCount = node.getParameterSlotCount();
                    for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                        renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
                    }
                    if (node.hasCoordinateInputFields()) {
                        renderCoordinateInputFields(context, textRenderer, node, isOverSidebar);
                    }
                    if (node.hasAmountInputField()) {
                        renderAmountInputField(context, textRenderer, node, isOverSidebar);
                    }
                }
                if (node.hasMessageInputFields()) {
                    renderMessageInputFields(context, textRenderer, node, isOverSidebar);
                    renderMessageButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasBookTextInput()) {
                    renderBookTextInput(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    renderSchematicDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
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

    private int getParameterFieldWidth(Node node) {
        return Math.max(20, node.getWidth() - 10);
    }

    private int getParameterFieldHeight() {
        return PARAMETER_INPUT_HEIGHT;
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

    private int getParameterValueStartX(Node node, NodeParameter parameter, TextRenderer textRenderer) {
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int maxLabelWidth = Math.max(0, fieldWidth - 40);
        String label = getParameterLabelText(node, parameter, textRenderer, maxLabelWidth);
        int labelWidth = textRenderer != null ? textRenderer.getWidth(label) : 0;
        return fieldLeft + 4 + labelWidth + 4;
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
            borderColor = node.getBooleanToggleValue() ? UITheme.TOGGLE_ON_BORDER : UITheme.TOGGLE_OFF_BORDER;
            fillColor = node.getBooleanToggleValue() ? UITheme.BOOL_TOGGLE_ON_FILL : UITheme.BOOL_TOGGLE_OFF_FILL;
            if (hovered) {
                fillColor = adjustColorBrightness(fillColor, 1.15f);
            }
            textColor = UITheme.TEXT_PRIMARY;
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, fillColor);
        DrawContextBridge.drawBorder(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, borderColor);

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

        int backgroundColor = node.hasAttachedSensor() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_PRIMARY;
        if (isOverSidebar) {
            backgroundColor = UITheme.NODE_INPUT_BG_DIMMED;
        }

        int borderColor = node.hasAttachedSensor() ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT;
        if (sensorDropTarget == node) {
            backgroundColor = UITheme.DROP_HIGHLIGHT_BLUE;
            borderColor = UITheme.ACCENT_DEFAULT;
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorder(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (!node.hasAttachedSensor()) {
            String placeholder = "Drag a sensor here";
            String display = trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.getWidth(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.fontHeight) / 2;
            int textColor = sensorDropTarget == node ? UITheme.ACCENT_DEFAULT : UITheme.TEXT_TERTIARY;
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
        }
    }

    private void renderActionSlot(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int slotX = node.getActionSlotLeft() - cameraX;
        int slotY = node.getActionSlotTop() - cameraY;
        int slotWidth = node.getActionSlotWidth();
        int slotHeight = node.getActionSlotHeight();

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
        DrawContextBridge.drawBorder(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        if (!node.hasAttachedActionNode()) {
            String placeholder = "Drag a node here";
            String display = trimTextToWidth(placeholder, textRenderer, slotWidth - 8);
            int textWidth = textRenderer.getWidth(display);
            int textX = slotX + Math.max(4, (slotWidth - textWidth) / 2);
            int textY = slotY + (slotHeight - textRenderer.fontHeight) / 2;
            int textColor = actionDropTarget == node ? UITheme.DROP_ACCENT_GREEN : UITheme.TEXT_TERTIARY;
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);
        }
    }

    private boolean isPointInsideBooleanToggle(Node node, int mouseX, int mouseY) {
        if (node == null || !node.hasBooleanToggle()) {
            return false;
        }
        int buttonLeft = node.getBooleanToggleLeft() - cameraX;
        int buttonTop = node.getBooleanToggleTop() - cameraY;
        int buttonWidth = node.getBooleanToggleWidth();
        int buttonHeight = node.getBooleanToggleHeight();
        return mouseX >= buttonLeft && mouseX <= buttonLeft + buttonWidth &&
               mouseY >= buttonTop && mouseY <= buttonTop + buttonHeight;
    }

    public boolean handleBooleanToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideBooleanToggle(node, mouseX, mouseY)) {
            return false;
        }
        node.toggleBooleanToggleValue();
        notifyNodeParametersChanged(node);
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
            borderColor = UITheme.ACCENT_DEFAULT;
        }

        context.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, backgroundColor);
        DrawContextBridge.drawBorder(context, slotX, slotY, slotWidth, slotHeight, borderColor);

        String headerText = node.getParameterSlotLabel(slotIndex);
        int headerColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int headerY = slotY - textRenderer.fontHeight - 2;
        if (headerY > node.getY() - cameraY + 14) {
            drawNodeText(context, textRenderer, Text.literal(headerText), slotX + 2, headerY, headerColor);
        }

        if (!occupied && isDropTarget) {
            // Provide a minimal visual cue when dragging to an empty slot without adding text.
            DrawContextBridge.drawBorder(context, slotX + 2, slotY + 2, slotWidth - 4, slotHeight - 4, UITheme.ACCENT_DEFAULT);
        }
    }

    private void renderCoordinateInputFields(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_SECONDARY;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = UITheme.ACCENT_DEFAULT;
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

        for (int i = 0; i < COORDINATE_AXES.length; i++) {
            int fieldX = startX + i * (fieldWidth + spacing);

            boolean editingAxis = isEditingCoordinateField()
                && coordinateEditingNode == node
                && coordinateEditingAxis == i;

            String axisLabel = COORDINATE_AXES[i];
            int labelWidth = textRenderer.getWidth(axisLabel);
            int labelX = fieldX + Math.max(0, (fieldWidth - labelWidth) / 2);
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
            int labelColor = editingAxis ? UITheme.TEXT_EDITING_LABEL : baseLabelColor;
            drawNodeText(context, textRenderer, Text.literal(axisLabel), labelX, labelY, labelColor);

            int inputBottom = inputTop + fieldHeight;
            int backgroundColor = editingAxis ? activeFieldBackground : fieldBackground;
            int borderColor = editingAxis ? activeFieldBorder : fieldBorder;
            int valueColor = editingAxis ? activeTextColor : textColor;

            context.fill(fieldX, inputTop, fieldX + fieldWidth, inputBottom, backgroundColor);
            DrawContextBridge.drawBorder(context, fieldX, inputTop, fieldWidth, fieldHeight, borderColor);

            String value;
            if (editingAxis) {
                value = coordinateEditBuffer;
            } else {
                NodeParameter parameter = node.getParameter(axisLabel);
                value = parameter != null ? parameter.getStringValue() : "";
            }

            String display = editingAxis
                ? value
                : trimTextToWidth(value, textRenderer, fieldWidth - 6);

            int textX = fieldX + 3;
            int textY = inputTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
            if (editingAxis && hasCoordinateSelection()) {
                int start = coordinateSelectionStart;
                int end = coordinateSelectionEnd;
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, inputTop + 2, selectionEndX, inputBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);

            if (editingAxis && coordinateCaretVisible) {
                int caretIndex = MathHelper.clamp(coordinateCaretPosition, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldX + fieldWidth - 2);
                context.fill(caretX, inputTop + 2, caretX + 1, inputBottom - 2, UITheme.CARET_COLOR);
            }
        }
    }

    private void renderAmountInputField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = UITheme.ACCENT_DEFAULT;
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

        int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
        drawNodeText(context, textRenderer, Text.literal(node.getAmountFieldLabel()), fieldLeft + 2, labelY, baseLabelColor);

        int fieldBottom = fieldTop + fieldHeight;
        int disabledBg = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BUTTON_DEFAULT_BG;
        int backgroundColor = (editing && amountEnabled) ? activeFieldBackground : (amountEnabled ? fieldBackground : disabledBg);
        int borderColor = (editing && amountEnabled) ? activeFieldBorder : fieldBorder;
        int valueColor = (editing && amountEnabled) ? activeTextColor : (amountEnabled ? textColor : UITheme.TEXT_SECONDARY);

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value;
        if (editing && amountEnabled) {
            value = amountEditBuffer;
        } else {
            NodeParameter amountParam = node.getParameter("Amount");
            value = amountParam != null ? amountParam.getStringValue() : "";
        }

        String display = editing
            ? value
            : trimTextToWidth(value, textRenderer, fieldWidth - 6);

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && amountEnabled && hasAmountSelection()) {
            int start = amountSelectionStart;
            int end = amountSelectionEnd;
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);

        if (editing && amountEnabled && amountCaretVisible) {
            int caretIndex = MathHelper.clamp(amountCaretPosition, 0, display.length());
            int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            context.fill(caretX, fieldTop + 2, caretX + 1, fieldBottom - 2, UITheme.CARET_COLOR);
        }

        if (node.hasAmountToggle()) {
            int toggleLeft = node.getAmountToggleLeft() - cameraX;
            int toggleTop = node.getAmountToggleTop() - cameraY;
            int toggleWidth = node.getAmountToggleWidth();
            int toggleHeight = node.getAmountToggleHeight();
            int toggleBorder = amountEnabled ? UITheme.ACCENT_DEFAULT : UITheme.BORDER_SUBTLE;
            int toggleBg = amountEnabled ? UITheme.AMOUNT_TOGGLE_ON : UITheme.BACKGROUND_TERTIARY;
            context.fill(toggleLeft, toggleTop, toggleLeft + toggleWidth, toggleTop + toggleHeight, toggleBg);
            DrawContextBridge.drawBorder(context, toggleLeft, toggleTop, toggleWidth, toggleHeight, toggleBorder);
            int knobWidth = toggleHeight - 2;
            int knobLeft = amountEnabled ? toggleLeft + toggleWidth - knobWidth - 1 : toggleLeft + 1;
            context.fill(knobLeft, toggleTop + 1, knobLeft + knobWidth, toggleTop + toggleHeight - 1, UITheme.TOGGLE_KNOB);

            // Hit area debug outline (optional visual cue)
            // DrawContextBridge.drawBorder(context, toggleLeft - 2, toggleTop - 2, toggleWidth + 4, toggleHeight + 4, 0x22000000);
        }
    }

    private void renderMessageInputFields(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
        int activeFieldBorder = UITheme.ACCENT_DEFAULT;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_EDITING;

        boolean editing = isEditingMessageField() && messageEditingNode == node;
        if (editing) {
            updateMessageCaretBlink();
        }

        int fieldCount = node.getMessageFieldCount();
        for (int i = 0; i < fieldCount; i++) {
            int labelTop = node.getMessageFieldLabelTop(i) - cameraY;
            int labelHeight = node.getMessageFieldLabelHeight();
            int fieldTop = node.getMessageFieldInputTop(i) - cameraY;
            int fieldHeight = node.getMessageFieldHeight();
            int fieldLeft = node.getMessageFieldLeft() - cameraX;
            int fieldWidth = node.getMessageFieldWidth();

            boolean editingThis = editing && messageEditingIndex == i;
            int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.fontHeight) / 2);
            String label = fieldCount > 1 ? "Message " + (i + 1) : "Message";
            drawNodeText(context, textRenderer, Text.literal(label), fieldLeft + 2, labelY, baseLabelColor);

            int fieldBottom = fieldTop + fieldHeight;
            int backgroundColor = editingThis ? activeFieldBackground : fieldBackground;
            int borderColor = editingThis ? activeFieldBorder : fieldBorder;
            int valueColor = editingThis ? activeTextColor : textColor;

            context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
            DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

            String value = editingThis ? messageEditBuffer : node.getMessageLine(i);
            if (value == null) {
                value = "";
            }
            String display = editingThis
                ? value
                : trimTextToWidth(value, textRenderer, fieldWidth - 6);

            int textX = fieldLeft + 3;
            int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
            if (editingThis && hasMessageSelection()) {
                int start = messageSelectionStart;
                int end = messageSelectionEnd;
                if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                    int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                    int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                    context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
                }
            }
            drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);

            if (editingThis && messageCaretVisible) {
                int caretIndex = MathHelper.clamp(messageCaretPosition, 0, display.length());
                int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
                caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
                context.fill(caretX, fieldTop + 2, caretX + 1, fieldBottom - 2, UITheme.CARET_COLOR);
            }
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
        for (int i = 0; i < COORDINATE_AXES.length; i++) {
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

    private void updateParameterFieldContentWidth(Node node, TextRenderer textRenderer, int editingIndex, String editingValue) {
        if (node == null || !(node.isParameterNode() || node.shouldRenderInlineParameters()) || textRenderer == null) {
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
        int addBorder = addHovered ? UITheme.ACCENT_DEFAULT : baseBorder;
        context.fill(addLeft, top, addLeft + size, top + size, addFill);
        DrawContextBridge.drawBorder(context, addLeft, top, size, size, addBorder);
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
        DrawContextBridge.drawBorder(context, removeLeft, top, size, size, removeBorder);
        int removeTextX = removeLeft + (size - textRenderer.getWidth("-")) / 2;
        int removeTextY = top + (size - textRenderer.fontHeight) / 2 + 1;
        int removeTextColor = canRemove ? UITheme.TEXT_PRIMARY : UITheme.NODE_LABEL_DIMMED;
        drawNodeText(context, textRenderer, Text.literal("-"), removeTextX, removeTextY, removeTextColor);
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
            buttonBorder = UITheme.ACCENT_DEFAULT;
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorder(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = "Edit Text";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.getWidth(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(buttonLabel), textX, textY, textColor);

        // Render Page label and field
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int labelTop = node.getBookTextPageLabelTop() - cameraY;
        drawNodeText(context, textRenderer, Text.literal("Page:"), buttonLeft, labelTop, labelColor);

        int fieldTop = node.getBookTextPageFieldTop() - cameraY;
        int fieldWidth = node.getBookTextPageFieldWidth();
        int fieldHeight = node.getBookTextPageFieldHeight();

        int fieldFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_HIGHLIGHT;

        context.fill(buttonLeft, fieldTop, buttonLeft + fieldWidth, fieldTop + fieldHeight, fieldFill);
        DrawContextBridge.drawBorder(context, buttonLeft, fieldTop, fieldWidth, fieldHeight, fieldBorder);

        // Display the page value
        NodeParameter pageParam = node.getParameter("Page");
        String pageValue = pageParam != null ? pageParam.getDisplayValue() : "1";
        int pageTextColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        drawNodeText(context, textRenderer, Text.literal(pageValue), buttonLeft + 4, fieldTop + (fieldHeight - textRenderer.fontHeight) / 2, pageTextColor);
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
            buttonBorder = UITheme.ACCENT_DEFAULT;
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorder(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = "Edit";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + (buttonWidth - textRenderer.getWidth(buttonLabel)) / 2;
        int textY = buttonTop + (buttonHeight - textRenderer.fontHeight) / 2;
        drawNodeText(context, textRenderer, Text.literal(buttonLabel), textX, textY, textColor);
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

    private void renderStopTargetInputField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int fieldBackground = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
        boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
        int fieldBorder = isActivateNode
            ? (isOverSidebar ? UITheme.BORDER_FOCUS : UITheme.BORDER_HIGHLIGHT)
            : (isOverSidebar ? UITheme.BORDER_DANGER_MUTED : UITheme.BORDER_DANGER_MUTED);
        int activeFieldBorder = isActivateNode ? UITheme.TEXT_TERTIARY : UITheme.BORDER_DANGER;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int activeTextColor = UITheme.TEXT_DANGER_ACTIVE;
        int caretColor = isActivateNode ? UITheme.TEXT_PRIMARY : UITheme.CARET_DANGER;

        boolean editing = isEditingStopTargetField() && stopTargetEditingNode == node;
        if (editing) {
            updateStopTargetCaretBlink();
        }

        int fieldTop = node.getStopTargetFieldInputTop() - cameraY;
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldLeft = node.getStopTargetFieldLeft() - cameraX;
        int fieldWidth = node.getStopTargetFieldWidth();

        int fieldBottom = fieldTop + fieldHeight;
        int backgroundColor = editing ? activeFieldBackground : fieldBackground;
        int borderColor = editing ? activeFieldBorder : fieldBorder;
        int valueColor = editing ? activeTextColor : textColor;

        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

        String value;
        if (editing) {
            value = stopTargetEditBuffer;
        } else {
            NodeParameter targetParam = node.getParameter("StartNumber");
            value = targetParam != null ? targetParam.getStringValue() : "";
        }

        String display;
        if (!editing && (value == null || value.isEmpty())) {
            display = "start #";
            valueColor = isOverSidebar ? UITheme.TEXT_DANGER_MUTED : UITheme.TEXT_DANGER_MUTED;
        } else {
            display = value == null ? "" : value;
        }

        display = editing
            ? display
            : trimTextToWidth(display, textRenderer, fieldWidth - 6);

        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        if (editing && hasStopTargetSelection()) {
            int start = stopTargetSelectionStart;
            int end = stopTargetSelectionEnd;
            if (start >= 0 && end >= 0 && start <= display.length() && end <= display.length()) {
                int selectionStartX = textX + textRenderer.getWidth(display.substring(0, start));
                int selectionEndX = textX + textRenderer.getWidth(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_DANGER_BG);
            }
        }
        drawNodeText(context, textRenderer, Text.literal(display), textX, textY, valueColor);

        if (editing && stopTargetCaretVisible) {
            int caretIndex = MathHelper.clamp(stopTargetCaretPosition, 0, display.length());
            int caretX = textX + textRenderer.getWidth(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            context.fill(caretX, fieldTop + 2, caretX + 1, fieldBottom - 2, caretColor);
        }
    }

    private void renderSchematicDropdownField(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar) {
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

        drawNodeText(context, textRenderer, Text.literal("Schematic"), fieldLeft, labelTop + (labelHeight - textRenderer.fontHeight) / 2, labelColor);

        int fieldBottom = fieldTop + fieldHeight;
        int backgroundColor = open ? activeFieldBackground : fieldBackground;
        int borderColor = open ? activeFieldBorder : fieldBorder;
        context.fill(fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldBottom, backgroundColor);
        DrawContextBridge.drawBorder(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, borderColor);

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
        drawNodeText(context, textRenderer, Text.literal(display), textX, textY, textColor);

        int arrowX = fieldLeft + fieldWidth - 10;
        int arrowY = fieldTop + (fieldHeight - textRenderer.fontHeight) / 2 + 1;
        String arrow = open ? ">" : "v";
        drawNodeText(context, textRenderer, Text.literal(arrow), arrowX, arrowY, UITheme.TEXT_PRIMARY);
    }

    private void renderSchematicDropdownList(DrawContext context, TextRenderer textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        if (!schematicDropdownOpen || schematicDropdownNode != node) {
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
        int listLeft = node.getSchematicFieldLeft() - cameraX;
        int listRight = listLeft + node.getSchematicFieldWidth();

        context.fill(listLeft, listTop, listRight, listBottom, fieldBackground);
        DrawContextBridge.drawBorder(context, listLeft, listTop, node.getSchematicFieldWidth(), listHeight, fieldBorder);

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        schematicDropdownHoverIndex = -1;
        if (worldMouseX >= node.getSchematicFieldLeft()
            && worldMouseX <= node.getSchematicFieldLeft() + node.getSchematicFieldWidth()
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
            String rowText = trimTextToWidth(optionLabel, textRenderer, node.getSchematicFieldWidth() - 6);
            drawNodeText(context, textRenderer, Text.literal(rowText), listLeft + 3, rowTop + 4, textColor);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            node.getSchematicFieldWidth(),
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
            node.getSchematicFieldWidth(),
            listHeight,
            UITheme.BORDER_DEFAULT
        );
    }

    public boolean isEditingCoordinateField() {
        return coordinateEditingNode != null && coordinateEditingAxis >= 0;
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

        for (int i = 0; i < COORDINATE_AXES.length; i++) {
            int fieldX = startX + i * (fieldWidth + spacing);
            if (worldX >= fieldX && worldX <= fieldX + fieldWidth) {
                return i;
            }
        }
        return -1;
    }

    public void startCoordinateEditing(Node node, int axisIndex) {
        if (node == null || !node.hasCoordinateInputFields() || axisIndex < 0
            || axisIndex >= COORDINATE_AXES.length) {
            stopCoordinateEditing(false);
            return;
        }

        closeSchematicDropdown();
        stopAmountEditing(true);
        stopStopTargetEditing(true);
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
        String value = coordinateEditBuffer;
        if (value == null || value.isEmpty() || "-".equals(value)) {
            value = "0";
        }
        String axisName = COORDINATE_AXES[coordinateEditingAxis];
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
        String axisName = COORDINATE_AXES[coordinateEditingAxis];
        coordinateEditingNode.setParameterValueAndPropagate(axisName, coordinateEditOriginalValue);
        coordinateEditingNode.recalculateDimensions();
    }

    private NodeParameter getCoordinateParameter(Node node, int axisIndex) {
        if (node == null || axisIndex < 0 || axisIndex >= COORDINATE_AXES.length) {
            return null;
        }
        return node.getParameter(COORDINATE_AXES[axisIndex]);
    }

    public boolean handleCoordinateKeyPressed(int keyCode, int modifiers) {
        if (!isEditingCoordinateField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteCoordinateSelection()) {
                    return true;
                }
                if (coordinateCaretPosition > 0 && !coordinateEditBuffer.isEmpty()) {
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
                int nextAxis = (coordinateEditingAxis + direction + COORDINATE_AXES.length) % COORDINATE_AXES.length;
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
                        insertCoordinateText(getClipboardText(), textRenderer);
                    }
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean handleCoordinateCharTyped(char chr, int modifiers, TextRenderer textRenderer) {
        if (!isEditingCoordinateField()) {
            return false;
        }

        if ((chr >= '0' && chr <= '9') || chr == '-') {
            return insertCoordinateText(String.valueOf(chr), textRenderer);
        }

        return false;
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
        stopMessageEditing(true);
        stopParameterEditing(true);
        stopEventNameEditing(true);

        amountEditingNode = node;
        NodeParameter amountParam = node.getParameter("Amount");
        amountEditBuffer = amountParam != null ? amountParam.getStringValue() : "";
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

        String value = amountEditBuffer;
        if (value == null || value.isEmpty()) {
            value = amountEditOriginalValue != null && !amountEditOriginalValue.isEmpty()
                ? amountEditOriginalValue
                : "0";
        }

        NodeParameter amountParam = amountEditingNode.getParameter("Amount");
        String previous = amountParam != null ? amountParam.getStringValue() : "";
        amountEditingNode.setParameterValueAndPropagate("Amount", value);
        amountEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertAmountEdit() {
        if (!isEditingAmountField()) {
            return;
        }
        amountEditingNode.setParameterValueAndPropagate("Amount", amountEditOriginalValue);
        amountEditingNode.recalculateDimensions();
    }

    public boolean handleAmountKeyPressed(int keyCode, int modifiers) {
        if (!isEditingAmountField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteAmountSelection()) {
                    return true;
                }
                if (amountCaretPosition > 0 && !amountEditBuffer.isEmpty()) {
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

        if (chr >= '0' && chr <= '9') {
            return insertAmountText(String.valueOf(chr), textRenderer);
        }

        return false;
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

        stopTargetEditingNode = node;
        NodeParameter targetParam = node.getParameter("StartNumber");
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

    private boolean applyStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return false;
        }

        String value = stopTargetEditBuffer == null ? "" : stopTargetEditBuffer;
        NodeParameter targetParam = stopTargetEditingNode.getParameter("StartNumber");
        String previous = targetParam != null ? targetParam.getStringValue() : "";
        stopTargetEditingNode.setParameterValueAndPropagate("StartNumber", value);
        stopTargetEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void revertStopTargetEdit() {
        if (!isEditingStopTargetField()) {
            return;
        }
        stopTargetEditingNode.setParameterValueAndPropagate("StartNumber", stopTargetEditOriginalValue);
        stopTargetEditingNode.recalculateDimensions();
    }

    public boolean handleStopTargetKeyPressed(int keyCode, int modifiers) {
        if (!isEditingStopTargetField()) {
            return false;
        }

        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteStopTargetSelection()) {
                    return true;
                }
                if (stopTargetCaretPosition > 0 && !stopTargetEditBuffer.isEmpty()) {
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

        if (chr >= '0' && chr <= '9') {
            return insertStopTargetText(String.valueOf(chr), textRenderer);
        }

        return false;
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
        stopMessageEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);

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
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteEventNameSelection()) {
                    return true;
                }
                if (eventNameCaretPosition > 0 && !eventNameEditBuffer.isEmpty()) {
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
        if (node == null || !(node.isParameterNode() || node.shouldRenderInlineParameters()) || node.hasPopupEditButton()
            || index < 0 || index >= node.getParameters().size()) {
            stopParameterEditing(false);
            return;
        }

        closeModeDropdown();
        closeSchematicDropdown();
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

        parameterEditingNode = node;
        parameterEditingIndex = index;
        NodeParameter parameter = node.getParameters().get(index);
        parameterEditBuffer = parameter != null ? parameter.getStringValue() : "";
        parameterEditOriginalValue = parameterEditBuffer;
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
        if (parameter != null) {
            parameter.setStringValueFromUser(value);
            parameterEditingNode.setParameterValueAndPropagate(parameter.getName(), value);
        }
        parameterEditingNode.recalculateDimensions();
        return !Objects.equals(previous, value);
    }

    private void refreshBlockStateParameterPreview() {
        if (!isEditingParameterField() || parameterEditingNode == null) {
            return;
        }
        if (parameterEditingNode.getType() != NodeType.PARAM_BLOCK) {
            return;
        }
        if (parameterEditingIndex < 0 || parameterEditingIndex >= parameterEditingNode.getParameters().size()) {
            return;
        }
        if (!isBlockParameter(parameterEditingNode, parameterEditingIndex)) {
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
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteParameterSelection()) {
                    return true;
                }
                if (parameterCaretPosition > 0 && !parameterEditBuffer.isEmpty()) {
                    parameterEditBuffer = parameterEditBuffer.substring(0, parameterCaretPosition - 1)
                        + parameterEditBuffer.substring(parameterCaretPosition);
                    setParameterCaretPosition(parameterCaretPosition - 1);
                    updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
                    refreshBlockStateParameterPreview();
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
                    refreshBlockStateParameterPreview();
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
        boolean controlHeld = InputCompatibilityBridge.hasControlDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (deleteMessageSelection()) {
                    return true;
                }
                if (messageCaretPosition > 0 && !messageEditBuffer.isEmpty()) {
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

    private boolean insertCoordinateText(String text, TextRenderer textRenderer) {
        if (!isEditingCoordinateField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '-') {
                filtered.append(c);
            }
        }
        if (filtered.length() == 0) {
            return false;
        }

        String originalBuffer = coordinateEditBuffer;
        int originalCaret = coordinateCaretPosition;
        int originalSelectionStart = coordinateSelectionStart;
        int originalSelectionEnd = coordinateSelectionEnd;
        int originalSelectionAnchor = coordinateSelectionAnchor;

        String working = coordinateEditBuffer;
        int caret = coordinateCaretPosition;

        if (hasCoordinateSelection()) {
            int start = coordinateSelectionStart;
            int end = coordinateSelectionEnd;
            working = working.substring(0, start) + working.substring(end);
            caret = start;
        }

        boolean inserted = false;
        for (int i = 0; i < filtered.length(); i++) {
            char c = filtered.charAt(i);
            String candidate = working.substring(0, caret) + c + working.substring(caret);
            if (!isValidCoordinateValue(candidate)) {
                continue;
            }
            working = candidate;
            caret++;
            inserted = true;
        }

        if (inserted) {
            coordinateEditBuffer = working;
            setCoordinateCaretPosition(caret);
            updateCoordinateFieldContentWidth(textRenderer);
            return true;
        }

        coordinateEditBuffer = originalBuffer;
        coordinateCaretPosition = originalCaret;
        coordinateSelectionStart = originalSelectionStart;
        coordinateSelectionEnd = originalSelectionEnd;
        coordinateSelectionAnchor = originalSelectionAnchor;
        return false;
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
        refreshBlockStateParameterPreview();
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
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                filtered.append(c);
            }
        }
        if (filtered.length() == 0) {
            return false;
        }

        String originalBuffer = amountEditBuffer;
        int originalCaret = amountCaretPosition;
        int originalSelectionStart = amountSelectionStart;
        int originalSelectionEnd = amountSelectionEnd;
        int originalSelectionAnchor = amountSelectionAnchor;

        String working = amountEditBuffer;
        int caret = amountCaretPosition;

        if (hasAmountSelection()) {
            int start = amountSelectionStart;
            int end = amountSelectionEnd;
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
            amountEditBuffer = working;
            setAmountCaretPosition(caret);
            updateAmountFieldContentWidth(textRenderer);
            return true;
        }

        amountEditBuffer = originalBuffer;
        amountCaretPosition = originalCaret;
        amountSelectionStart = originalSelectionStart;
        amountSelectionEnd = originalSelectionEnd;
        amountSelectionAnchor = originalSelectionAnchor;
        return false;
    }

    private boolean insertStopTargetText(String text, TextRenderer textRenderer) {
        if (!isEditingStopTargetField() || textRenderer == null || text == null || text.isEmpty()) {
            return false;
        }
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                filtered.append(c);
            }
        }
        if (filtered.length() == 0) {
            return false;
        }

        String originalBuffer = stopTargetEditBuffer;
        int originalCaret = stopTargetCaretPosition;
        int originalSelectionStart = stopTargetSelectionStart;
        int originalSelectionEnd = stopTargetSelectionEnd;
        int originalSelectionAnchor = stopTargetSelectionAnchor;

        String working = stopTargetEditBuffer;
        int caret = stopTargetCaretPosition;

        if (hasStopTargetSelection()) {
            int start = stopTargetSelectionStart;
            int end = stopTargetSelectionEnd;
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
            stopTargetEditBuffer = working;
            setStopTargetCaretPosition(caret);
            updateStopTargetFieldContentWidth(textRenderer);
            return true;
        }

        stopTargetEditBuffer = originalBuffer;
        stopTargetCaretPosition = originalCaret;
        stopTargetSelectionStart = originalSelectionStart;
        stopTargetSelectionEnd = originalSelectionEnd;
        stopTargetSelectionAnchor = originalSelectionAnchor;
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
            refreshBlockStateParameterPreview();
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

    private List<ParameterDropdownOption> getParameterDropdownOptions(Node node, int index, String query) {
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (isBlockStateParameter(node, index)) {
            return getBlockStateDropdownOptions(node, lowered);
        }

        List<String> source;
        if (isBlockParameter(node, index)) {
            source = RegistryStringCache.BLOCK_IDS;
        } else if (isItemParameter(node, index)) {
            source = RegistryStringCache.ITEM_IDS;
        } else if (isEntityParameter(node, index)) {
            source = RegistryStringCache.ENTITY_IDS;
        } else {
            return Collections.emptyList();
        }

        if (lowered.isEmpty()) {
            return Collections.emptyList();
        }

        List<ParameterDropdownOption> starts = new ArrayList<>();
        List<ParameterDropdownOption> contains = new ArrayList<>();
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
        List<ParameterDropdownOption> result = new ArrayList<>(Math.min(64, starts.size() + contains.size()));
        result.addAll(starts);
        result.addAll(contains);
        return result;
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

    private void updateParameterDropdown(Node node, int index, TextRenderer textRenderer, int fieldX, int fieldY, int fieldWidth, int fieldHeight) {
        if (!isEditingParameterField() || parameterEditingNode != node || parameterEditingIndex != index) {
            return;
        }
        if (!isBlockItemParameter(node, index)) {
            closeParameterDropdown();
            return;
        }
        ParameterSegment segment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String query = segment.trimmedSegment == null ? "" : segment.trimmedSegment.trim();
        boolean isStateParameter = isBlockStateParameter(node, index);
        if (query.isEmpty() && !isStateParameter) {
            closeParameterDropdown();
            return;
        }

        if (isParameterDropdownSuppressed(node, index, query)) {
            closeParameterDropdown();
            return;
        }

        List<ParameterDropdownOption> options = getParameterDropdownOptions(node, index, query);
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
        parameterDropdownNode = null;
        parameterDropdownIndex = -1;
        parameterDropdownHoverIndex = -1;
        parameterDropdownScrollOffset = 0;
        parameterDropdownQuery = "";
        parameterDropdownOptions.clear();
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
        if (!isEditingParameterField() || !parameterDropdownOpen || parameterDropdownOptions.isEmpty()) {
            return false;
        }
        if (optionIndex < 0 || optionIndex >= parameterDropdownOptions.size()) {
            return false;
        }
        ParameterDropdownOption option = parameterDropdownOptions.get(optionIndex);
        ParameterSegment segment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String prefix = parameterEditBuffer.substring(0, segment.start);
        String suffix = parameterEditBuffer.substring(segment.end);
        String replacement = segment.leadingWhitespace + option.value();
        parameterEditBuffer = prefix + replacement + suffix;
        setParameterCaretPosition(prefix.length() + replacement.length());
        updateParameterFieldContentWidth(parameterEditingNode, getClientTextRenderer(), parameterEditingIndex, parameterEditBuffer);
        refreshBlockStateParameterPreview();
        ParameterSegment updatedSegment = getParameterSegment(parameterEditBuffer, parameterCaretPosition);
        String updatedQuery = updatedSegment.trimmedSegment == null ? "" : updatedSegment.trimmedSegment.trim();
        suppressParameterDropdown(parameterEditingNode, parameterEditingIndex, updatedQuery);
        closeParameterDropdown();
        return true;
    }

    private int getParameterDropdownListTop() {
        return parameterDropdownFieldY + parameterDropdownFieldHeight;
    }

    private int getDropdownWidth() {
        return Math.max(200, Math.round(parameterDropdownFieldWidth * 1.5f));
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
        int fieldLeft = parameterDropdownFieldX;
        int fieldTop = parameterDropdownFieldY;
        if (x >= fieldLeft && x <= fieldLeft + parameterDropdownFieldWidth
            && y >= fieldTop && y <= fieldTop + parameterDropdownFieldHeight) {
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
        if (!parameterDropdownOpen) {
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

        context.fill(listLeft, listTop, listRight, listBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, listLeft, listTop, dropdownWidth, listHeight, UITheme.BORDER_HIGHLIGHT);

        parameterDropdownScrollOffset = MathHelper.clamp(parameterDropdownScrollOffset, 0, layout.maxScrollOffset);
        parameterDropdownHoverIndex = -1;
        if (transformedMouseX >= listLeft && transformedMouseX <= listRight && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
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
            int maxTextWidth = dropdownWidth - (textX - listLeft) - textPadding;
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
    }

    public boolean handleModeDropdownClick(double screenX, double screenY) {
        if (!modeDropdownOpen) {
            return false;
        }
        int x = (int) screenX;
        int y = (int) screenY;
        if (isPointInsideModeDropdownList(x, y)) {
            int index = getModeDropdownIndexAt(x, y);
            if (index >= 0) {
                applyModeDropdownSelection(index);
            }
            return true;
        }
        int fieldLeft = modeDropdownFieldX;
        int fieldTop = modeDropdownFieldY;
        if (x >= fieldLeft && x <= fieldLeft + modeDropdownFieldWidth
            && y >= fieldTop && y <= fieldTop + modeDropdownFieldHeight) {
            return true;
        }
        closeModeDropdown();
        return false;
    }

    public boolean handleModeDropdownScroll(double screenX, double screenY, double verticalAmount) {
        if (!modeDropdownOpen) {
            return false;
        }
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

    public boolean handleModeFieldClick(Node node, int screenX, int screenY) {
        if (node == null || !node.shouldRenderInlineParameters() || !node.supportsModeSelection()) {
            return false;
        }
        if (!isPointInsideModeField(node, screenX, screenY)) {
            return false;
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
        modeDropdownNode = null;
        modeDropdownHoverIndex = -1;
        modeDropdownScrollOffset = 0;
        modeDropdownOptions.clear();
    }

    private void renderModeDropdownList(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!modeDropdownOpen) {
            return;
        }
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

        context.fill(listLeft, listTop, listRight, listBottom, UITheme.BACKGROUND_SIDEBAR);
        DrawContextBridge.drawBorder(context, listLeft, listTop, dropdownWidth, listHeight, UITheme.BORDER_HIGHLIGHT);

        modeDropdownScrollOffset = MathHelper.clamp(modeDropdownScrollOffset, 0, layout.maxScrollOffset);
        modeDropdownHoverIndex = -1;
        if (transformedMouseX >= listLeft && transformedMouseX <= listRight && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
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
            int maxTextWidth = dropdownWidth - (textPadding * 2);
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
    }

    private int getModeDropdownListTop() {
        return modeDropdownFieldY + modeDropdownFieldHeight;
    }

    private int getModeDropdownWidth() {
        return Math.max(200, Math.round(modeDropdownFieldWidth * 1.5f));
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
        modeDropdownNode = node;
        modeDropdownScrollOffset = 0;
        modeDropdownHoverIndex = -1;
        modeDropdownFieldX = getParameterFieldLeft(node) - cameraX;
        modeDropdownFieldY = node.getY() - cameraY + 18;
        modeDropdownFieldWidth = getParameterFieldWidth(node);
        modeDropdownFieldHeight = getParameterFieldHeight();
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
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = getParameterFieldHeight();
        int fieldTop = node.getY() + 18;
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private ItemStack resolveParameterDropdownIcon(Node node, int index, String optionValue) {
        if (node == null || optionValue == null || optionValue.isEmpty() || isBlockStateParameter(node, index)) {
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

    private boolean isPointInsideAmountToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasAmountToggle()) {
            return false;
        }
        int left = node.getAmountToggleLeft() - cameraX - 3;
        int top = node.getAmountToggleTop() - cameraY - 3;
        int width = node.getAmountToggleWidth() + 6;
        int height = node.getAmountToggleHeight() + 6;
        return screenX >= left && screenX <= left + width
            && screenY >= top && screenY <= top + height;
    }

    public boolean handleAmountToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideAmountToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isAmountInputEnabled();
        node.setAmountInputEnabled(newState);
        if (!newState && isEditingAmountField() && amountEditingNode == node) {
            stopAmountEditing(false);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
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
        if (node == null || !(node.isParameterNode() || node.shouldRenderInlineParameters()) || node.hasPopupEditButton()) {
            return -1;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = getParameterFieldLeft(node);
        int fieldWidth = getParameterFieldWidth(node);
        int fieldHeight = getParameterFieldHeight();
        int fieldTop = node.getY() + 18;

        if (node.supportsModeSelection()) {
            fieldTop += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }

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

        return worldX >= listLeft && worldX <= listLeft + node.getSchematicFieldWidth()
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
        schematicDropdownNode = null;
        schematicDropdownHoverIndex = -1;
        schematicDropdownScrollOffset = 0;
    }

    private void applySchematicSelection(Node node, String value) {
        if (node == null || value == null || value.isEmpty()) {
            return;
        }
        node.setParameterValueAndPropagate("Schematic", value);
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
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
        return renderer.trimToWidth(text, safeMaxWidth);
    }

    private void renderSocket(DrawContext context, int x, int y, boolean isInput, int color) {
        // Socket circle
        context.fill(x - 3, y - 3, x + 3, y + 3, color);
        DrawContextBridge.drawBorder(context, x - 3, y - 3, 6, 6, UITheme.BORDER_SOCKET);
        
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

        for (NodeConnection connection : connections) {
            if (onlyDragged != isConnectionInDraggedLayer(connection)) {
                continue;
            }
            Node outputNode = connection.getOutputNode();
            Node inputNode = connection.getInputNode();

            if (!outputNode.shouldRenderSockets() || !inputNode.shouldRenderSockets()) {
                continue;
            }

            int outputX = outputNode.getSocketX(false) - cameraX;
            int outputY = outputNode.getSocketY(connection.getOutputSocket(), false) - cameraY;
            int inputX = inputNode.getSocketX(true) - cameraX;
            int inputY = inputNode.getSocketY(connection.getInputSocket(), true) - cameraY;

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
                    renderSocket(context, targetX, targetY, hoveredSocketIsInput, UITheme.ACCENT_DEFAULT); // Light blue highlight
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
        boolean isOverSidebar = node.isDragging() && isNodeOverSidebar(node, sidebarWidthForRendering, screenX, screenWidth);
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

    private boolean isConnectionInDraggedLayer(NodeConnection connection) {
        if (connection == null) {
            return false;
        }
        return isNodeInDraggedHierarchy(connection.getOutputNode())
            || isNodeInDraggedHierarchy(connection.getInputNode());
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
        if (node.isParameterNode() || node.shouldRenderInlineParameters()) {
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
        for (Node node : nodes) {
            if (!shouldCascadeDelete(node)) {
                continue;
            }
            if (!node.isDragging()) {
                continue;
            }
            int screenX = node.getX() - cameraX;
            if (!isNodeOverSidebar(node, sidebarWidthForRendering, screenX, node.getWidth())) {
                continue;
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
        save();
    }

    public void markWorkspaceClean() {
        workspaceDirty = false;
    }

    public boolean isWorkspaceDirty() {
        return workspaceDirty;
    }

    public void notifyNodeParametersChanged(Node node) {
        if (node == null) {
            return;
        }
        markWorkspaceDirty();
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

            // Restore parameters (overwrite the default parameters with saved ones)
            node.getParameters().clear();
            if (nodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData paramData : nodeData.getParameters()) {
                    ParameterType paramType = ParameterType.valueOf(paramData.getType());
                    NodeParameter param = new NodeParameter(paramData.getName(), paramType, paramData.getValue());
                    if (paramData.getUserEdited() != null) {
                        param.setUserEdited(paramData.getUserEdited());
                    }
                    node.getParameters().add(param);
                }
            }
            if ((node.getType() == NodeType.STOP_CHAIN || node.getType() == NodeType.START_CHAIN)
                && node.getParameter("StartNumber") == null) {
                node.getParameters().add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
            }
            Integer startNodeNumber = nodeData.getStartNodeNumber();
            if (startNodeNumber != null) {
                node.setStartNodeNumber(startNodeNumber);
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
                }
            }
            if (nodeData.getType() == NodeType.WRITE_BOOK && nodeData.getBookText() != null) {
                Node bookNode = nodeMap.get(nodeData.getId());
                if (bookNode != null) {
                    bookNode.setBookText(nodeData.getBookText());
                }
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

        System.out.println("Loaded " + nodes.size() + " nodes and " + connections.size() + " connections");
        return true;
    }
    
    /**
     * Check if there's a saved node graph available
     */
    public boolean hasSavedGraph() {
        return NodeGraphPersistence.hasSavedNodeGraph(activePreset);
    }

    public void setActivePreset(String presetName) {
        this.activePreset = presetName;
    }

    public String getActivePreset() {
        return activePreset;
    }
}
