package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.InputCompatibilityBridge;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NodeSelectionController {
    interface Host {
        List<Node> nodes();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int worldToScreenX(int worldX);
        int worldToScreenY(int worldY);
        float zoomScale();
        void stopEditorsForNodeDrag();
        boolean isUndoCaptureSuppressed();
        NodeGraphData captureDragUndoSnapshot();
        void pushUndoSnapshot(NodeGraphData snapshot);
        void markWorkspaceDirty();
        void invalidateHierarchyCache();
        boolean isConnectionCutActive();
        void updateConnectionCut(int worldX, int worldY);
        boolean isStickyNoteResizing();
        void updateStickyNoteResize(int worldX, int worldY);
        Node finishStickyNoteResize();
        void cancelStickyNoteResize();
        void setInsertionPreviewConnection(NodeConnection connection);
        NodeConnection findInsertionPreviewConnection(Node node);
        boolean tryInsertDraggedNodeIntoPreviewConnection(Node node);
        boolean insertNodeIntoConnection(Node node, NodeConnection connection);
        void updateConnectionDrag(int worldX, int worldY);
        void forceClearConnectionDragState();
        boolean isDraggingConnection();
        boolean isConnectionCutActiveForStatus();
        Node getNodeAtWorldExcluding(int worldX, int worldY, Node excluded);
        Node getParentForNode(Node node);
        boolean intersectsViewport(Node node);
        void positionNewNode(Node node, int worldMouseX, int worldMouseY);
        String activeRoutineWorkspaceId();
        void assignNewStartNodeNumber(Node node);
        void bringNodeToFront(Node node);
        Node getRootNode(Node node);
        NodeGraph.SelectionBounds calculateBounds(Collection<Node> nodes);
        void removeNodeCascade(Node node, boolean captureUndo);
        boolean shouldCascadeDelete(Node node);
        void collectNodesForCascade(Node node, List<Node> order, Set<Node> visited);
        int cameraX();
        int sidebarWidthForRendering();
    }

    private static final int SELECTION_BOX_MIN_DRAG = 3;
    private static final int GRID_SNAP_SIZE = 20;
    private static final long DOUBLE_CLICK_THRESHOLD = 300;

    private final Host host;
    private Node selectedNode;
    private final LinkedHashSet<Node> selectedNodes = new LinkedHashSet<>();
    private Node draggingNode;
    private int draggingNodeStartX;
    private int draggingNodeStartY;
    private boolean draggingNodeDetached;
    private NodeGraphData pendingDragUndoSnapshot;
    private boolean dragOperationChanged;
    private Node sensorDropTarget;
    private Node actionDropTarget;
    private Node parameterDropTarget;
    private Integer parameterDropSlotIndex;
    private boolean selectionBoxActive;
    private int selectionBoxStartWorldX;
    private int selectionBoxStartWorldY;
    private int selectionBoxCurrentWorldX;
    private int selectionBoxCurrentWorldY;
    private boolean multiDragActive;
    private final Map<Node, DragStartInfo> multiDragStartPositions = new HashMap<>();
    private boolean selectionDeletionPreviewActive;
    private long lastClickTime;
    private Node lastClickedNode;
    private final Set<Node> cascadeDeletionPreviewNodes = new HashSet<>();

    NodeSelectionController(Host host) {
        this.host = host;
    }

    Node getSensorDropTarget() {
        return sensorDropTarget;
    }

    Node getActionDropTarget() {
        return actionDropTarget;
    }

    Node getParameterDropTarget() {
        return parameterDropTarget;
    }

    Integer getParameterDropSlotIndex() {
        return parameterDropSlotIndex;
    }

    Node getDraggingNode() {
        return draggingNode;
    }

    boolean isMultiDragActive() {
        return multiDragActive;
    }

    boolean isSelectionDeletionPreviewActive() {
        return selectionDeletionPreviewActive;
    }

    boolean isCascadeDeletionPreviewNode(Node node) {
        return cascadeDeletionPreviewNodes.contains(node);
    }

    void selectNode(Node node) {
        if (node == null) {
            clearSelection();
            return;
        }
        clearSelection();
        addNodeToSelection(node);
    }

    void selectNodes(Collection<Node> nodesToSelect) {
        clearSelection();
        if (nodesToSelect == null) {
            return;
        }
        for (Node node : nodesToSelect) {
            addNodeToSelection(node);
        }
    }

    Set<Node> getSelectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    void setSelectionDeletionPreviewActive(boolean active) {
        selectionDeletionPreviewActive = active;
    }

    boolean isNodeSelected(Node node) {
        if (node == null) {
            return false;
        }
        return selectedNodes.contains(node);
    }

    void focusSelectedNode(Node node) {
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

    void toggleNodeInSelection(Node node) {
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

    private void addNodeToSelection(Node node) {
        if (node == null) {
            return;
        }
        if (selectedNodes.add(node)) {
            node.setSelected(true);
            selectedNode = node;
        }
    }

    void clearSelection() {
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

    void pruneSelectionToCurrentNodes() {
        if (selectedNodes.isEmpty()) {
            selectedNode = null;
            return;
        }
        Set<Node> liveNodes = new HashSet<>(host.nodes());
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

    void clearDropTargetsForRemovedNode(Node node) {
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
    }

    void onNodeRemoved(Node node) {
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

    void clearTransientState() {
        clearSelection();
        draggingNode = null;
        sensorDropTarget = null;
        actionDropTarget = null;
        parameterDropTarget = null;
        parameterDropSlotIndex = null;
        lastClickedNode = null;
        lastClickTime = 0;
        cascadeDeletionPreviewNodes.clear();
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
    }

    void beginSelectionBox(int screenX, int screenY) {
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        selectionBoxActive = true;
        selectionBoxStartWorldX = worldX;
        selectionBoxStartWorldY = worldY;
        selectionBoxCurrentWorldX = worldX;
        selectionBoxCurrentWorldY = worldY;
    }

    void updateSelectionBox(int screenX, int screenY) {
        if (!selectionBoxActive) {
            return;
        }
        selectionBoxCurrentWorldX = host.screenToWorldX(screenX);
        selectionBoxCurrentWorldY = host.screenToWorldY(screenY);
        if (hasSelectionBoxDrag()) {
            applySelectionBoxSelection();
        }
    }

    void completeSelectionBox() {
        if (!selectionBoxActive) {
            return;
        }
        if (hasSelectionBoxDrag()) {
            applySelectionBoxSelection();
        }
        selectionBoxActive = false;
    }

    boolean isSelectionBoxActive() {
        return selectionBoxActive;
    }

    private boolean hasSelectionBoxDrag() {
        float scale = host.zoomScale();
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
        for (Node node : host.nodes()) {
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

    void renderSelectionBox(GuiGraphics context) {
        if (!selectionBoxActive || !hasSelectionBoxDrag()) {
            return;
        }
        int left = host.worldToScreenX(Math.min(selectionBoxStartWorldX, selectionBoxCurrentWorldX));
        int right = host.worldToScreenX(Math.max(selectionBoxStartWorldX, selectionBoxCurrentWorldX));
        int top = host.worldToScreenY(Math.min(selectionBoxStartWorldY, selectionBoxCurrentWorldY));
        int bottom = host.worldToScreenY(Math.max(selectionBoxStartWorldY, selectionBoxCurrentWorldY));
        if (left == right || top == bottom) {
            return;
        }
        int fillColor = UITheme.NODE_SELECTION_FILL;
        int borderColor = UITheme.NODE_SELECTION_BORDER;
        context.fill(left, top, right, bottom, fillColor);
        DrawContextBridge.drawBorderInLayer(context, left, top, right - left, bottom - top, borderColor);
    }

    void resetDropTargets() {
        sensorDropTarget = null;
        actionDropTarget = null;
        parameterDropTarget = null;
        parameterDropSlotIndex = null;
    }

    Node getSelectedNode() {
        return selectedNode;
    }

    void beginDragOperation() {
        pendingDragUndoSnapshot = host.isUndoCaptureSuppressed() ? null : host.captureDragUndoSnapshot();
        dragOperationChanged = false;
    }

    void markDragOperationChanged() {
        dragOperationChanged = true;
    }

    void startDragging(Node node, int mouseX, int mouseY) {
        host.stopEditorsForNodeDrag();
        resetDropTargets();

        if (node == null) {
            pendingDragUndoSnapshot = null;
            dragOperationChanged = false;
            multiDragActive = false;
            multiDragStartPositions.clear();
            return;
        }

        beginDragOperation();

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

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        node.setDragOffsetX(worldMouseX - node.getX());
        node.setDragOffsetY(worldMouseY - node.getY());
    }

    void updateDrag(int mouseX, int mouseY) {
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);

        if (host.isConnectionCutActive()) {
            host.updateConnectionCut(worldMouseX, worldMouseY);
            return;
        }

        if (host.isStickyNoteResizing()) {
            host.updateStickyNoteResize(worldMouseX, worldMouseY);
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
                host.invalidateHierarchyCache();
                draggingNode.setSocketsHidden(false);
                resetDropTargets();
                host.setInsertionPreviewConnection(null);
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
                    host.invalidateHierarchyCache();

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
                        for (Node node : host.nodes()) {
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
                    host.setInsertionPreviewConnection(!hideSockets ? host.findInsertionPreviewConnection(draggingNode) : null);
                    draggingNode.setSocketsHidden(hideSockets);
                } else {
                    host.setInsertionPreviewConnection(null);
                }
            }
        } else {
            host.setInsertionPreviewConnection(null);
        }
        host.updateConnectionDrag(worldMouseX, worldMouseY);
    }

    void previewSidebarDrag(NodeType nodeType, int worldMouseX, int worldMouseY) {
        previewSidebarDrag(nodeType != null ? Node.createForEditor(nodeType, worldMouseX, worldMouseY) : null, worldMouseX, worldMouseY);
    }

    void previewSidebarDrag(Node candidate, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        host.setInsertionPreviewConnection(null);
        if (candidate == null) {
            return;
        }
        host.positionNewNode(candidate, worldMouseX, worldMouseY);
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
            for (Node node : host.nodes()) {
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
                host.setInsertionPreviewConnection(host.findInsertionPreviewConnection(candidate));
            }
        }
    }

    int[] getSidebarDragPreviewPosition(Node candidate, int worldMouseX, int worldMouseY) {
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
        Node hoveredNode = host.getNodeAtWorldExcluding(worldMouseX, worldMouseY, excludeCandidateNode ? candidate : null);
        for (Node current = hoveredNode; current != null; current = host.getParentForNode(current)) {
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
        List<Node> nodes = host.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (excludeCandidateNode && node == candidate) {
                continue;
            }
            if (!host.intersectsViewport(node)) {
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

    private int findPreferredParameterSlot(Node hostNode, Node candidate, int worldMouseX, int worldMouseY, boolean allowBodyFallback) {
        if (hostNode == null || candidate == null || !hostNode.canAcceptParameter()) {
            return -1;
        }

        int hoveredSlotIndex = hostNode.getParameterSlotIndexAt(worldMouseX, worldMouseY);
        if (hoveredSlotIndex >= 0 && hostNode.canAcceptParameterNode(candidate, hoveredSlotIndex)) {
            return hoveredSlotIndex;
        }

        if (!allowBodyFallback || !hostNode.containsPoint(worldMouseX, worldMouseY)) {
            return -1;
        }

        int slotCount = hostNode.getParameterSlotCount();
        int firstCompatible = -1;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (!hostNode.canAcceptParameterNode(candidate, slotIndex)) {
                continue;
            }
            if (firstCompatible < 0) {
                firstCompatible = slotIndex;
            }
            if (hostNode.getAttachedParameter(slotIndex) == null) {
                return slotIndex;
            }
        }
        return firstCompatible;
    }

    private boolean trySetSensorDropTarget(Node candidateToExclude, int worldMouseX, int worldMouseY) {
        for (Node node : host.nodes()) {
            if (!node.canAcceptSensor() || node == candidateToExclude) {
                continue;
            }
            if (!host.intersectsViewport(node)) {
                continue;
            }
            if (node.isPointInsideSensorSlot(worldMouseX, worldMouseY)) {
                sensorDropTarget = node;
                return true;
            }
        }
        return false;
    }

    Node handleSidebarDrop(NodeType nodeType, int worldMouseX, int worldMouseY) {
        return handleSidebarDrop(nodeType != null ? Node.createForEditor(nodeType, 0, 0) : null, worldMouseX, worldMouseY);
    }

    Node handleSidebarDrop(Node newNode, int worldMouseX, int worldMouseY) {
        resetDropTargets();
        host.setInsertionPreviewConnection(null);
        if (newNode == null) {
            return null;
        }
        if (newNode.getType() == NodeType.ROUTINE_INPUT
            && (host.activeRoutineWorkspaceId().isBlank() || !host.activeRoutineWorkspaceId().equals(newNode.getRoutineId()))) {
            return null;
        }
        host.positionNewNode(newNode, worldMouseX, worldMouseY);
        NodeType nodeType = newNode.getType();
        if (nodeType == NodeType.START) {
            host.assignNewStartNodeNumber(newNode);
        }

        boolean parameterCandidate = Node.isUsableAsParameterType(nodeType);
        if (parameterCandidate
            && trySetParameterDropTarget(newNode, worldMouseX, worldMouseY, false)
            && parameterDropTarget != null
            && parameterDropSlotIndex != null) {
            host.nodes().add(newNode);
            parameterDropTarget.attachParameter(newNode, parameterDropSlotIndex);
            host.markWorkspaceDirty();
            return newNode;
        }
        if (Node.isSensorType(nodeType) && trySetSensorDropTarget(null, worldMouseX, worldMouseY) && sensorDropTarget != null) {
            host.nodes().add(newNode);
            sensorDropTarget.attachSensor(newNode);
            host.markWorkspaceDirty();
            return newNode;
        } else {
            for (Node node : host.nodes()) {
                if (!node.canAcceptActionNode()) {
                    continue;
                }
                if (!host.intersectsViewport(node)) {
                    continue;
                }
                if (!node.canAcceptActionNode(newNode)) {
                    continue;
                }
                if (node.isPointInsideActionSlot(worldMouseX, worldMouseY)) {
                    host.nodes().add(newNode);
                    node.attachActionNode(newNode);
                    host.markWorkspaceDirty();
                    return newNode;
                }
            }
        }

        NodeConnection insertionConnection = host.findInsertionPreviewConnection(newNode);
        host.nodes().add(newNode);
        if (insertionConnection != null) {
            host.insertNodeIntoConnection(newNode, insertionConnection);
        }
        host.markWorkspaceDirty();
        return newNode;
    }

    void stopDragging() {
        Node rootToPromote = null;
        if (host.isStickyNoteResizing()) {
            rootToPromote = host.finishStickyNoteResize();
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
                rootToPromote = host.getRootNode(node);
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
                rootToPromote = host.getRootNode(target);
            } else if (parameterDropTarget != null && parameterDropSlotIndex != null) {
                Node target = parameterDropTarget;
                int slotIndex = parameterDropSlotIndex;
                node.setDragging(false);
                if (!target.attachParameter(node, slotIndex)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = host.getRootNode(target);
            } else if (node.isSensorNode() && sensorDropTarget != null) {
                Node target = sensorDropTarget;
                node.setDragging(false);
                if (!target.attachSensor(node)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = host.getRootNode(target);
            } else if (!node.isSensorNode() && actionDropTarget != null) {
                Node target = actionDropTarget;
                node.setDragging(false);
                if (!target.attachActionNode(node)) {
                    node.setSocketsHidden(false);
                }
                rootToPromote = host.getRootNode(target);
            } else {
                node.setDragging(false);
                node.setSocketsHidden(shouldHideSocketsWhenAttached(node));
                host.tryInsertDraggedNodeIntoPreviewConnection(node);
                rootToPromote = host.getRootNode(node);
            }
        }
        if (rootToPromote != null) {
            host.bringNodeToFront(rootToPromote);
        }
        draggingNode = null;
        draggingNodeDetached = false;
        resetDropTargets();
        host.setInsertionPreviewConnection(null);

        if (dragOperationChanged) {
            host.pushUndoSnapshot(pendingDragUndoSnapshot);
            host.markWorkspaceDirty();
        }
        pendingDragUndoSnapshot = null;
        dragOperationChanged = false;
        if (multiDragActive) {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }
        selectionDeletionPreviewActive = false;
    }

    void forceClearTransientDragState() {
        for (Node node : host.nodes()) {
            if (node == null) {
                continue;
            }
            node.setDragging(false);
            node.setSocketsHidden(shouldHideSocketsWhenAttached(node));
        }
        draggingNode = null;
        draggingNodeDetached = false;
        host.cancelStickyNoteResize();
        pendingDragUndoSnapshot = null;
        dragOperationChanged = false;
        if (multiDragActive) {
            multiDragActive = false;
            multiDragStartPositions.clear();
        }
        selectionDeletionPreviewActive = false;
        selectionBoxActive = false;
        host.forceClearConnectionDragState();
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
        host.invalidateHierarchyCache();
    }

    boolean isAnyNodeBeingDragged() {
        return draggingNode != null || host.isStickyNoteResizing()
            || host.isDraggingConnection() || host.isConnectionCutActiveForStatus();
    }

    boolean isSelectionOverSidebar(int sidebarWidth) {
        if (selectedNodes.isEmpty()) {
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
        NodeGraph.SelectionBounds bounds = host.calculateBounds(draggedNodes);
        if (bounds == null) {
            return false;
        }
        int left = bounds.minX - host.cameraX();
        int right = bounds.maxX - host.cameraX();
        double scaledCenter = (left + (right - left) / 2.0) * host.zoomScale();
        return scaledCenter < sidebarWidth;
    }

    private boolean isNodeOverSidebar(Node node, int sidebarWidth) {
        int screenX = host.worldToScreenX(node.getX());
        double scaledCenter = screenX + (node.getWidth() * host.zoomScale()) / 2.0;
        return scaledCenter < sidebarWidth;
    }

    boolean handleNodeClick(Node clickedNode) {
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

    void updateCascadeDeletionPreview() {
        cascadeDeletionPreviewNodes.clear();
        boolean selectionOverSidebar = false;
        if (multiDragActive && !selectedNodes.isEmpty()) {
            selectionOverSidebar = isSelectionOverSidebar(host.sidebarWidthForRendering());
        }
        for (Node node : host.nodes()) {
            if (!host.shouldCascadeDelete(node)) {
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
                int screenX = node.getX() - host.cameraX();
                double scaledCenter = (screenX + node.getWidth() / 2.0) * host.zoomScale();
                if (scaledCenter >= host.sidebarWidthForRendering()) {
                    continue;
                }
            }
            List<Node> removalOrder = new ArrayList<>();
            host.collectNodesForCascade(node, removalOrder, new HashSet<>());
            cascadeDeletionPreviewNodes.addAll(removalOrder);
        }
    }

    private int snapToGrid(int worldCoord) {
        return Math.round((float) worldCoord / GRID_SNAP_SIZE) * GRID_SNAP_SIZE;
    }

    private static final class DragStartInfo {
        private final int x;
        private final int y;

        private DragStartInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
