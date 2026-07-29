package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;

/** Owns node numbering, insertion, removal, and cascade-deletion lifecycle. */
final class NodeLifecycleController {

    interface Host {
        List<Node> nodes();
        List<NodeConnection> connections();
        String activeRoutineWorkspaceId();
        int nextStartNodeNumber();
        void setNextStartNodeNumber(int value);
        void pushUndoState();
        void markWorkspaceDirty();
        void stopEditorsForRemovedNode(Node node);
        void closeRunPresetDropdownForRemovedNode(Node node);
        void clearDropTargetsForRemovedNode(Node node);
        void addConnectionReplacingConflicts(
            Node outputNode, Node inputNode, int outputSocket, int inputSocket);
        void onNodeRemoved(Node node);
        void invalidateHierarchyCache();
        void invalidateRenderCaches();
        int cameraX();
        boolean isNodeOverSidebar(Node node, int sidebarWidth, int screenX, int screenWidth);
        void pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes();
        void clearSelection();
    }

    private final Host host;

    NodeLifecycleController(Host host) {
        this.host = host;
    }

    void assignNewStartNodeNumber(Node node) {
        if (node == null || node.getType() != NodeType.START) {
            return;
        }
        int nextStartNodeNumber = host.nextStartNodeNumber();
        if (nextStartNodeNumber <= 0) {
            nextStartNodeNumber = 1;
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Node existing : host.nodes()) {
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
        host.setNextStartNodeNumber(nextStartNodeNumber);
    }

    void normalizeStartNodeNumbers() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        int max = 0;
        List<Node> startNodes = new ArrayList<>();
        for (Node node : host.nodes()) {
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
        host.setNextStartNodeNumber(next);
    }

    void addNode(Node node) {
        String activeRoutineWorkspaceId = host.activeRoutineWorkspaceId();
        if (node != null && node.getType() == NodeType.ROUTINE_INPUT
            && (!activeRoutineWorkspaceId.equals(node.getRoutineId()) || activeRoutineWorkspaceId.isBlank())) {
            return;
        }
        if (node != null && node.getType() == NodeType.START && node.getStartNodeNumber() <= 0) {
            assignNewStartNodeNumber(node);
        }
        host.nodes().add(node);
        host.invalidateHierarchyCache();
    }

    void removeNode(Node node) {
        if (node == null || node.isProtectedRoutineEntry()) {
            return;
        }
        host.pushUndoState();
        removeNodeInternal(node, true, true);
        host.markWorkspaceDirty();
    }

    void deleteNodeIfInSidebar(Node node, int mouseX, int sidebarWidth) {
        // Use the same logic as the grey-out function - more than halfway over the sidebar
        // Calculate the node's screen position (same as in renderNode)
        int nodeScreenX = node.getX() - host.cameraX();
        if (host.isNodeOverSidebar(node, sidebarWidth, nodeScreenX, node.getWidth())) {
            if (shouldCascadeDelete(node)) {
                removeNodeCascade(node);
            } else {
                removeNode(node);
            }
        }
    }

    boolean deleteSelectedNode() {
        host.pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes = host.selectedNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        List<Node> targets = selectedNodes.stream().filter(node -> !node.isProtectedRoutineEntry()).toList();
        if (targets.isEmpty()) {
            return false;
        }
        host.pushUndoState();
        for (Node node : targets) {
            removeNodeCascade(node, false);
        }
        host.clearSelection();
        host.markWorkspaceDirty();
        return true;
    }

    void removeNodeInternal(Node node, boolean autoReconnect, boolean repositionDetachments) {
        if (node == null) {
            return;
        }

        host.stopEditorsForRemovedNode(node);
        host.closeRunPresetDropdownForRemovedNode(node);

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

        host.clearDropTargetsForRemovedNode(node);

        if (autoReconnect) {
            List<NodeConnection> inputConnections = new ArrayList<>();
            List<NodeConnection> outputConnections = new ArrayList<>();

            for (NodeConnection conn : host.connections()) {
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

                        host.addConnectionReplacingConflicts(
                            inputSource, outputTarget, inputSocket, outputSocket);
                    }
                }
            }
        }

        boolean removedStartNode = node.getType() == NodeType.START;
        host.connections().removeIf(conn ->
            conn.getOutputNode().equals(node) || conn.getInputNode().equals(node));
        host.nodes().remove(node);
        if (removedStartNode) {
            // Reuse freed START numbers on the next START node creation.
            host.setNextStartNodeNumber(1);
        }

        host.onNodeRemoved(node);
        host.invalidateRenderCaches();
    }

    private void removeNodeCascade(Node node) {
        removeNodeCascade(node, true);
    }

    void removeNodeCascade(Node node, boolean captureUndo) {
        if (node == null || node.isProtectedRoutineEntry()) {
            return;
        }
        if (node == null) {
            return;
        }
        if (captureUndo) {
            host.pushUndoState();
        }
        List<Node> removalOrder = new ArrayList<>();
        collectNodesForCascade(node, removalOrder, new HashSet<>());
        for (Node toRemove : removalOrder) {
            boolean shouldReconnect = toRemove == node;
            removeNodeInternal(toRemove, shouldReconnect, false);
        }
        host.markWorkspaceDirty();
    }

    void collectNodesForCascade(Node node, List<Node> order, Set<Node> visited) {
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

    boolean shouldCascadeDelete(Node node) {
        if (node == null) {
            return false;
        }
        return node.hasAttachedSensor() || node.hasAttachedActionNode() || node.hasAttachedParameter();
    }
}
