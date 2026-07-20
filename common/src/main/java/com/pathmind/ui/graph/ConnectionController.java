package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ConnectionGeometry.connectionDistanceToPoint;
import static com.pathmind.ui.graph.ConnectionGeometry.connectionIntersectsRect;
import static com.pathmind.ui.graph.ConnectionGeometry.doesSegmentIntersectConnection;
import static com.pathmind.ui.graph.ConnectionGeometry.isPointNearConnection;
import static com.pathmind.ui.graph.ConnectionRenderer.drawSegmentWithThickness;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;

final class ConnectionController {
    private static final int CONNECTION_CLICK_THRESHOLD = 4;
    private static final int CONNECTION_CUT_THRESHOLD = 6;
    private static final int CONNECTION_CUT_PREVIEW_COLOR = 0xCCFF6B6B;

    interface Host {
        List<Node> getNodes();
        List<NodeConnection> getConnections();
        Node getNodeAtWorld(int worldX, int worldY);
        Node getNodeAtWorldExcluding(int worldX, int worldY, Node excludedNode);
        Node getParentForNode(Node node);
        void stopConnectionEditors();
        void pushUndoState();
        boolean isUndoCaptureSuppressed();
        void markWorkspaceDirty();
        void invalidateRenderCaches();
        void markDragOperationChanged();
    }

    private final Host host;
    private final Set<SocketKey> connectedInputSockets = new HashSet<>();
    private final Set<SocketKey> connectedOutputSockets = new HashSet<>();
    private boolean connectionIndexDirty = true;

    private boolean draggingConnection = false;
    private Node connectionSourceNode;
    private int connectionSourceSocket;
    private boolean outputSocket;
    private int connectionDragX;
    private int connectionDragY;
    private int connectionDragStartX;
    private int connectionDragStartY;
    private boolean connectionDragMoved = false;
    private Node hoveredNode = null;
    private int hoveredSocket = -1;
    private boolean hoveredSocketIsInput = false;
    private NodeConnection disconnectedConnection = null;
    private NodeConnection insertionPreviewConnection = null;

    private boolean connectionCutActive = false;
    private boolean connectionCutMoved = false;
    private int connectionCutStartWorldX = 0;
    private int connectionCutStartWorldY = 0;
    private int connectionCutCurrentWorldX = 0;
    private int connectionCutCurrentWorldY = 0;
    private final List<CutPoint> connectionCutPath = new ArrayList<>();

    private Node hoveredSocketNode = null;
    private int hoveredSocketIndex = -1;

    ConnectionController(Host host) {
        this.host = host;
    }

    void invalidateConnectionIndex() {
        connectionIndexDirty = true;
    }

    private void rebuildConnectionIndexIfNeeded() {
        if (!connectionIndexDirty) {
            return;
        }

        connectedInputSockets.clear();
        connectedOutputSockets.clear();
        for (NodeConnection connection : host.getConnections()) {
            connectedInputSockets.add(new SocketKey(connection.getInputNode(), connection.getInputSocket()));
            connectedOutputSockets.add(new SocketKey(connection.getOutputNode(), connection.getOutputSocket()));
        }

        connectionIndexDirty = false;
    }

    boolean isSocketConnected(Node node, int socketIndex, boolean isInput) {
        rebuildConnectionIndexIfNeeded();
        SocketKey key = new SocketKey(node, socketIndex);
        return isInput ? connectedInputSockets.contains(key) : connectedOutputSockets.contains(key);
    }

    boolean isSocketActive(Node node, int socketIndex, boolean isInput) {
        if (isSocketConnected(node, socketIndex, isInput)) {
            return true;
        }
        if (draggingConnection && connectionSourceNode == node && connectionSourceSocket == socketIndex) {
            // Treat the drag source as active so it stays bright while connecting.
            if ((isInput && !outputSocket) || (!isInput && outputSocket)) {
                return true;
            }
        }
        return false;
    }

    void startDraggingConnection(Node node, int socketIndex, boolean isOutput, int worldMouseX, int worldMouseY) {
        host.stopConnectionEditors();
        draggingConnection = true;
        connectionSourceNode = node;
        connectionSourceSocket = socketIndex;
        outputSocket = isOutput;
        connectionDragX = worldMouseX;
        connectionDragY = worldMouseY;
        connectionDragStartX = connectionDragX;
        connectionDragStartY = connectionDragY;
        connectionDragMoved = false;
        hoveredNode = null;
        hoveredSocket = -1;
        hoveredSocketIsInput = false;

        // Find and disconnect existing connection from this socket
        disconnectedConnection = null;
        List<NodeConnection> connections = host.getConnections();
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
        if (disconnectedConnection != null) {
            invalidateConnectionIndex();
        }
    }

    void updateDrag(int worldMouseX, int worldMouseY, boolean denseViewportMode) {
        if (!draggingConnection) {
            return;
        }
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

        if (denseViewportMode) {
            Node hoveredCandidate = host.getNodeAtWorldExcluding(worldMouseX, worldMouseY, connectionSourceNode);
            for (Node current = hoveredCandidate; current != null; current = host.getParentForNode(current)) {
                if (updateConnectionSnapForNode(current, worldMouseX, worldMouseY)) {
                    break;
                }
            }
        } else {
            for (Node node : host.getNodes()) {
                if (updateConnectionSnapForNode(node, worldMouseX, worldMouseY)) {
                    break;
                }
            }
        }
    }

    private boolean updateConnectionSnapForNode(Node node, int worldMouseX, int worldMouseY) {
        if (node == null || node == connectionSourceNode || !node.shouldRenderSockets()) {
            return false;
        }

        if (outputSocket) {
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    hoveredNode = node;
                    hoveredSocket = i;
                    hoveredSocketIsInput = true;
                    return true;
                }
            }
        } else {
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    hoveredNode = node;
                    hoveredSocket = i;
                    hoveredSocketIsInput = false;
                    return true;
                }
            }
        }

        return false;
    }

    boolean updateMouseHover(int worldMouseX, int worldMouseY, boolean denseViewportMode, List<Node> visibleRoots) {
        clearSocketHover();

        // Don't check for socket hover if we're currently dragging a connection
        if (draggingConnection) {
            return false;
        }

        // Check for socket hover
        if (denseViewportMode) {
            Node hoveredCandidate = host.getNodeAtWorld(worldMouseX, worldMouseY);
            for (Node current = hoveredCandidate; current != null; current = host.getParentForNode(current)) {
                if (updateHoveredSocketForNode(current, worldMouseX, worldMouseY, false, false)) {
                    return true;
                }
            }
        } else {
            for (int i = visibleRoots.size() - 1; i >= 0; i--) {
                if (updateHoveredSocketInHierarchy(visibleRoots.get(i), worldMouseX, worldMouseY, false, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean updateHoveredSocketInHierarchy(Node node, int worldMouseX, int worldMouseY, boolean inputsOnly, boolean outputsOnly) {
        if (node == null) {
            return false;
        }

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            keys.sort(Collections.reverseOrder());
            for (Integer key : keys) {
                if (updateHoveredSocketInHierarchy(parameterMap.get(key), worldMouseX, worldMouseY, inputsOnly, outputsOnly)) {
                    return true;
                }
            }
        }

        if (updateHoveredSocketInHierarchy(node.getAttachedSensor(), worldMouseX, worldMouseY, inputsOnly, outputsOnly)) {
            return true;
        }

        if (updateHoveredSocketInHierarchy(node.getAttachedActionNode(), worldMouseX, worldMouseY, inputsOnly, outputsOnly)) {
            return true;
        }

        return updateHoveredSocketForNode(node, worldMouseX, worldMouseY, inputsOnly, outputsOnly);
    }

    private boolean updateHoveredSocketForNode(Node node, int worldMouseX, int worldMouseY, boolean inputsOnly, boolean outputsOnly) {
        if (node == null || !node.shouldRenderSockets()) {
            return false;
        }

        if (!outputsOnly) {
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    hoveredSocketNode = node;
                    hoveredSocketIndex = i;
                    hoveredSocketIsInput = true;
                    return true;
                }
            }
        }

        if (!inputsOnly) {
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    hoveredSocketNode = node;
                    hoveredSocketIndex = i;
                    hoveredSocketIsInput = false;
                    return true;
                }
            }
        }

        return false;
    }

    void stopDraggingConnection() {
        boolean connectionChanged = false;
        if (draggingConnection && connectionSourceNode != null) {
            // Try to create connection if hovering over valid socket
            if (hoveredNode != null && hoveredSocket != -1) {
                if (outputSocket && hoveredSocketIsInput) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    addConnectionReplacingConflicts(connectionSourceNode, hoveredNode, connectionSourceSocket, hoveredSocket);
                    connectionChanged = true;
                } else if (!outputSocket && !hoveredSocketIsInput) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    addConnectionReplacingConflicts(hoveredNode, connectionSourceNode, hoveredSocket, connectionSourceSocket);
                    connectionChanged = true;
                } else {
                    // Invalid connection - restore original
                    restoreDisconnectedConnection();
                }
            } else {
                // No valid target - decide whether to keep the removal
                if (disconnectedConnection != null && outputSocket && !connectionDragMoved) {
                    captureUndoStateForConnectionChange(disconnectedConnection);
                    connectionChanged = true;
                } else {
                    restoreDisconnectedConnection();
                }
            }
        }

        if (connectionChanged) {
            host.markWorkspaceDirty();
        }

        draggingConnection = false;
        connectionSourceNode = null;
        connectionSourceSocket = -1;
        hoveredNode = null;
        hoveredSocket = -1;
        disconnectedConnection = null;
        connectionDragMoved = false;
    }

    void addConnectionReplacingConflicts(Node outputNode, Node inputNode, int outputSocket, int inputSocket) {
        if (outputNode == null || inputNode == null || outputNode == inputNode) {
            return;
        }
        List<NodeConnection> connections = host.getConnections();
        connections.removeIf(conn ->
            conn.getInputNode() == inputNode && conn.getInputSocket() == inputSocket
        );
        connections.removeIf(conn ->
            conn.getOutputNode() == outputNode && conn.getOutputSocket() == outputSocket
        );
        connections.add(new NodeConnection(outputNode, inputNode, outputSocket, inputSocket));
    }

    private void restoreDisconnectedConnection() {
        if (disconnectedConnection != null) {
            host.getConnections().add(disconnectedConnection);
            invalidateConnectionIndex();
        }
    }

    private void captureUndoStateForConnectionChange(NodeConnection previousConnection) {
        if (host.isUndoCaptureSuppressed()) {
            return;
        }
        List<NodeConnection> connections = host.getConnections();
        boolean temporarilyAdded = false;
        if (previousConnection != null && !connections.contains(previousConnection)) {
            connections.add(previousConnection);
            temporarilyAdded = true;
        }
        host.pushUndoState();
        if (temporarilyAdded) {
            connections.remove(previousConnection);
        }
    }

    boolean tryConnectToSocket(Node targetNode, int targetSocket, boolean isInput) {
        if (draggingConnection && connectionSourceNode != null) {
            if (!targetNode.shouldRenderSockets()) {
                return false;
            }
            // Validate connection (output can only connect to input)
            if (isInput && connectionSourceNode != targetNode) {
                addConnectionReplacingConflicts(connectionSourceNode, targetNode, connectionSourceSocket, targetSocket);
                stopDraggingConnection();
                return true;
            }
        }
        return false;
    }

    NodeConnection getConnectionAt(int worldX, int worldY) {
        for (NodeConnection connection : host.getConnections()) {
            if (isPointNearConnection(connection, worldX, worldY, CONNECTION_CLICK_THRESHOLD)) {
                return connection;
            }
        }
        return null;
    }

    boolean isNodeEligibleForConnectionInsertion(Node node) {
        return node != null
            && node.shouldRenderSockets()
            && !node.isSensorNode()
            && !node.isParameterNode()
            && node.getInputSocketCount() == 1
            && node.getOutputSocketCount() == 1;
    }

    NodeConnection findInsertionPreviewConnection(Node node) {
        if (!isNodeEligibleForConnectionInsertion(node)) {
            return null;
        }

        NodeConnection bestConnection = null;
        double bestDistance = Double.MAX_VALUE;
        int left = node.getX();
        int top = node.getY();
        int right = left + node.getWidth();
        int bottom = top + node.getHeight();
        double centerX = left + node.getWidth() / 2.0;
        double centerY = top + node.getHeight() / 2.0;

        for (NodeConnection connection : host.getConnections()) {
            if (connection == null
                || connection.getOutputNode() == node
                || connection.getInputNode() == node) {
                continue;
            }
            if (!connectionIntersectsRect(connection, left, top, right, bottom, CONNECTION_CLICK_THRESHOLD)) {
                continue;
            }

            double distance = connectionDistanceToPoint(connection, centerX, centerY);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestConnection = connection;
            }
        }

        return bestConnection;
    }

    boolean tryInsertDraggedNodeIntoPreviewConnection(Node node) {
        if (!isNodeEligibleForConnectionInsertion(node) || insertionPreviewConnection == null) {
            return false;
        }

        NodeConnection connection = insertionPreviewConnection;
        insertionPreviewConnection = null;
        boolean inserted = insertNodeIntoConnection(node, connection);
        if (inserted) {
            host.markDragOperationChanged();
        }
        return inserted;
    }

    boolean insertNodeIntoConnection(Node node, NodeConnection connection) {
        List<NodeConnection> connections = host.getConnections();
        if (node == null || connection == null || !connections.contains(connection)) {
            return false;
        }

        Node source = connection.getOutputNode();
        Node target = connection.getInputNode();
        if (source == null || target == null || source == node || target == node) {
            return false;
        }

        connections.remove(connection);
        addConnectionReplacingConflicts(source, node, connection.getOutputSocket(), 0);
        addConnectionReplacingConflicts(node, target, 0, connection.getInputSocket());
        invalidateConnectionIndex();
        host.invalidateRenderCaches();
        return true;
    }

    void startConnectionCut(int worldX, int worldY) {
        connectionCutActive = true;
        connectionCutMoved = false;
        connectionCutStartWorldX = worldX;
        connectionCutStartWorldY = worldY;
        connectionCutCurrentWorldX = connectionCutStartWorldX;
        connectionCutCurrentWorldY = connectionCutStartWorldY;
        connectionCutPath.clear();
        connectionCutPath.add(new CutPoint(connectionCutStartWorldX, connectionCutStartWorldY));
    }

    void updateConnectionCut(int worldX, int worldY) {
        if (!connectionCutActive) {
            return;
        }
        connectionCutCurrentWorldX = worldX;
        connectionCutCurrentWorldY = worldY;
        if (!connectionCutMoved) {
            int deltaX = Math.abs(connectionCutCurrentWorldX - connectionCutStartWorldX);
            int deltaY = Math.abs(connectionCutCurrentWorldY - connectionCutStartWorldY);
            connectionCutMoved = deltaX > CONNECTION_CUT_THRESHOLD || deltaY > CONNECTION_CUT_THRESHOLD;
        }
        CutPoint lastPoint = connectionCutPath.isEmpty() ? null : connectionCutPath.get(connectionCutPath.size() - 1);
        if (lastPoint == null || lastPoint.x != worldX || lastPoint.y != worldY) {
            connectionCutPath.add(new CutPoint(worldX, worldY));
        }
    }

    boolean stopConnectionCut() {
        boolean removedAny = false;
        if (connectionCutActive && connectionCutMoved) {
            List<NodeConnection> toRemove = new ArrayList<>();
            for (NodeConnection connection : host.getConnections()) {
                if (doesCutPathIntersectConnection(connection)) {
                    toRemove.add(connection);
                }
            }
            if (!toRemove.isEmpty()) {
                host.pushUndoState();
                host.getConnections().removeAll(toRemove);
                invalidateConnectionIndex();
                host.markWorkspaceDirty();
                removedAny = true;
            }
        }

        connectionCutActive = false;
        connectionCutMoved = false;
        connectionCutPath.clear();
        return removedAny;
    }

    void cancelConnectionCut() {
        connectionCutActive = false;
        connectionCutMoved = false;
        connectionCutPath.clear();
    }

    boolean removeConnection(NodeConnection connection) {
        List<NodeConnection> connections = host.getConnections();
        if (connection == null || !connections.contains(connection)) {
            return false;
        }
        host.pushUndoState();
        boolean removed = connections.remove(connection);
        if (removed) {
            invalidateConnectionIndex();
            host.markWorkspaceDirty();
        }
        return removed;
    }

    void renderConnectionCutPreview(GuiGraphics context, int cameraX, int cameraY) {
        if (connectionCutPath.size() < 2) {
            int x = connectionCutCurrentWorldX - cameraX;
            int y = connectionCutCurrentWorldY - cameraY;
            drawSegmentWithThickness(context, x, y, x, y, CONNECTION_CUT_PREVIEW_COLOR, 0);
            return;
        }
        CutPoint previous = connectionCutPath.get(0);
        for (int i = 1; i < connectionCutPath.size(); i++) {
            CutPoint current = connectionCutPath.get(i);
            drawSegmentWithThickness(
                context,
                previous.x - cameraX,
                previous.y - cameraY,
                current.x - cameraX,
                current.y - cameraY,
                CONNECTION_CUT_PREVIEW_COLOR,
                0
            );
            previous = current;
        }
    }

    private boolean doesCutPathIntersectConnection(NodeConnection connection) {
        if (connectionCutPath.size() < 2) {
            return false;
        }
        CutPoint previous = connectionCutPath.get(0);
        for (int i = 1; i < connectionCutPath.size(); i++) {
            CutPoint current = connectionCutPath.get(i);
            if (doesSegmentIntersectConnection(
                connection,
                previous.x,
                previous.y,
                current.x,
                current.y,
                CONNECTION_CUT_THRESHOLD
            )) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    void clearSocketHover() {
        hoveredSocketNode = null;
        hoveredSocketIndex = -1;
    }

    void clearGraphState() {
        hoveredNode = null;
        hoveredSocketNode = null;
        hoveredSocketIndex = -1;
        hoveredSocket = -1;
        hoveredSocketIsInput = false;
        draggingConnection = false;
        connectionSourceNode = null;
        disconnectedConnection = null;
    }

    void forceClearTransientDragState() {
        draggingConnection = false;
        connectionSourceNode = null;
        connectionSourceSocket = -1;
        hoveredNode = null;
        hoveredSocket = -1;
        disconnectedConnection = null;
        connectionDragMoved = false;
        insertionPreviewConnection = null;
    }

    boolean isDraggingConnection() {
        return draggingConnection;
    }

    Node getConnectionSourceNode() {
        return connectionSourceNode;
    }

    int getConnectionSourceSocket() {
        return connectionSourceSocket;
    }

    boolean isOutputSocket() {
        return outputSocket;
    }

    int getConnectionDragX() {
        return connectionDragX;
    }

    int getConnectionDragY() {
        return connectionDragY;
    }

    Node getHoveredNode() {
        return hoveredNode;
    }

    int getHoveredSocket() {
        return hoveredSocket;
    }

    boolean isHoveredSocketInput() {
        return hoveredSocketIsInput;
    }

    NodeConnection getInsertionPreviewConnection() {
        return insertionPreviewConnection;
    }

    void setInsertionPreviewConnection(NodeConnection insertionPreviewConnection) {
        this.insertionPreviewConnection = insertionPreviewConnection;
    }

    Node getHoveredSocketNode() {
        return hoveredSocketNode;
    }

    int getHoveredSocketIndex() {
        return hoveredSocketIndex;
    }

    boolean isConnectionCutActive() {
        return connectionCutActive;
    }

    boolean hasConnectionCutMoved() {
        return connectionCutMoved;
    }

    private static final class SocketKey {
        private final Node node;
        private final int socketIndex;

        private SocketKey(Node node, int socketIndex) {
            this.node = node;
            this.socketIndex = socketIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SocketKey)) {
                return false;
            }
            SocketKey other = (SocketKey) obj;
            return node == other.node && socketIndex == other.socketIndex;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(node) + socketIndex;
        }
    }

    private static final class CutPoint {
        private final int x;
        private final int y;

        private CutPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
