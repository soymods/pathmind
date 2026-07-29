package com.pathmind.screen;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.util.InputCompatibilityBridge;

import java.util.List;

/**
 * Routes a workspace click to whatever the cursor actually landed on: a
 * coordinate picker, a connection socket, one of the many inline controls on a
 * node body, or empty canvas.
 *
 * <p>The ordering here is the interaction contract. Sockets are tested before
 * node bodies so a socket on top of a node still starts a connection, and the
 * inline controls are tested before the node-drag fallback so clicking a
 * control never drags the node.
 */
final class PathmindNodeInteractionController {

    interface Host {
        NodeGraph nodeGraph();
        void openTemplateWorkspaceTab(Node node);
        void openRoutineWorkspaceTab(String routineId);
        void switchPreset(String presetName);
        void openBookTextEditor(Node node);
        void openParameterOverlay(Node node);
        boolean executeFromNodeOnDoubleClick(Node node);
    }

    private final Host host;

    PathmindNodeInteractionController(Host host) {
        this.host = host;
    }

    /**
     * Handles a click that landed inside the workspace area.
     *
     * @return {@code true} when the click was consumed
     */
    boolean handleClick(double mouseX, double mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();
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

        // FIRST check if clicking on ANY socket (before checking node body)
        if (handleSocketClick(mouseX, mouseY, button)) {
            return true;
        }

        // THEN check if clicking on node body
        if (button == 0 && nodeGraph.handleStopTargetFieldClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (button == 0 && nodeGraph.handleVariableFieldClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        Node clickedNode = nodeGraph.getNodeAt((int) mouseX, (int) mouseY);

        if (clickedNode != null) {
            // Node body clicked (not socket)
            if (button == 0) { // Left click - select node or start dragging
                return handleNodeBodyClick(clickedNode, mouseX, mouseY);
            }
            return false;
        }

        return handleEmptySpaceClick(mouseX, mouseY, button);
    }

    /** Starts a connection drag when a left click landed on any node's socket. */
    private boolean handleSocketClick(double mouseX, double mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();
        int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
        int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
        for (Node node : nodeGraph.getNodes()) {
            if (!node.shouldRenderSockets()) {
                continue;
            }
            // Check input sockets
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    if (button == 0) { // Left click - start dragging connection from input
                        stopInlineFieldEditing(false);
                        nodeGraph.startDraggingConnection(node, i, false, (int) mouseX, (int) mouseY);
                        return true;
                    }
                }
            }

            // Check output sockets
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    if (button == 0) { // Left click - start dragging connection from output
                        stopInlineFieldEditing(false);
                        nodeGraph.startDraggingConnection(node, i, true, (int) mouseX, (int) mouseY);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Routes a left click on a node body. Inline controls are tested first, in
     * declaration order; anything that falls through selects or drags the node.
     */
    private boolean handleNodeBodyClick(Node clickedNode, double mouseX, double mouseY) {
        NodeGraph nodeGraph = host.nodeGraph();
        int clickX = (int) mouseX;
        int clickY = (int) mouseY;

        if (clickedNode.getType() == NodeType.TEMPLATE
            && nodeGraph.isPointInsideTemplateEditButton(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            host.openTemplateWorkspaceTab(clickedNode);
            return true;
        }

        if (nodeGraph.handleBooleanToggleClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleRuntimeScopeButtonClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleSchematicDropdownClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (clickedNode.getType() == NodeType.RUN_PRESET
            && nodeGraph.isPointInsideRunPresetOpenButton(clickedNode, clickX, clickY)) {
            openRunPresetTarget(nodeGraph.getSelectedPresetNameForNode(clickedNode));
            return true;
        }

        if (nodeGraph.handleRunPresetDropdownClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleBooleanOperatorButtonClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleMessageButtonClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleScreenCoordinatePickerClick(clickedNode, clickX, clickY)) {
            return true;
        }

        if (nodeGraph.handleStickyNoteResizeHandleClick(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            return true;
        }

        if (nodeGraph.isPointInsideStickyNoteTextArea(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startStickyNoteEditing(clickedNode);
            return true;
        }

        int coordinateAxis = nodeGraph.getCoordinateFieldAxisAt(clickedNode, clickX, clickY);
        if (coordinateAxis != -1) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startCoordinateEditing(clickedNode, coordinateAxis);
            return true;
        }

        if (nodeGraph.isPointInsideStopTargetField(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startStopTargetEditing(clickedNode);
            return true;
        }

        if (nodeGraph.isPointInsideVariableField(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startVariableEditing(clickedNode);
            return true;
        }

        if (nodeGraph.handleRandomRoundingToggleClick(clickedNode, clickX, clickY)
            || nodeGraph.handleRandomRoundingDropdownClick(clickedNode, clickX, clickY)
            || nodeGraph.handleAmountToggleClick(clickedNode, clickX, clickY)
            || nodeGraph.handleDirectionModeTabClick(clickedNode, clickX, clickY)
            || nodeGraph.handleBooleanModeTabClick(clickedNode, clickX, clickY)
            || nodeGraph.handleMessageScopeToggleClick(clickedNode, clickX, clickY)
            || nodeGraph.handleBooleanLiteralDropdownClick(clickedNode, clickX, clickY)
            || nodeGraph.handleModeFieldClick(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            return true;
        }

        if (nodeGraph.isPointInsideAmountField(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startAmountEditing(clickedNode);
            return true;
        }

        int messageIndex = nodeGraph.getMessageFieldIndexAt(clickedNode, clickX, clickY);
        if (messageIndex != -1) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startMessageEditing(clickedNode, messageIndex);
            return true;
        }

        int parameterIndex = nodeGraph.getParameterFieldIndexAt(clickedNode, clickX, clickY);
        if (parameterIndex != -1) {
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startParameterEditing(clickedNode, parameterIndex);
            return true;
        }

        if (nodeGraph.handleEventNameFieldClick(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            return true;
        }

        stopInlineFieldEditing(true);

        // Check if clicking on Edit Text button for WRITE_BOOK nodes
        if (clickedNode.hasBookTextInput() && nodeGraph.isPointInsideBookTextButton(clickedNode, clickX, clickY)) {
            host.openBookTextEditor(clickedNode);
            return true;
        }

        if (clickedNode.isParameterNode() && nodeGraph.isPointInsidePopupEditButton(clickedNode, clickX, clickY)) {
            nodeGraph.focusSelectedNode(clickedNode);
            host.openParameterOverlay(clickedNode);
            return true;
        }

        boolean doubleClick = nodeGraph.handleNodeClick(clickedNode, clickX, clickY);
        if (doubleClick && clickedNode.getType() == NodeType.ROUTINE_CALL && !clickedNode.getRoutineId().isBlank()) {
            host.openRoutineWorkspaceTab(clickedNode.getRoutineId());
            return true;
        }
        if (doubleClick && host.executeFromNodeOnDoubleClick(clickedNode)) {
            return true;
        }

        // Check for double-click to open parameter editor
        boolean shouldOpenOverlay = clickedNode.getType() == NodeType.PARAM_INVENTORY_SLOT
            || clickedNode.getType() == NodeType.PARAM_KEY
            || clickedNode.getType() == NodeType.PARAM_VILLAGER_TRADE;
        if (shouldOpenOverlay && doubleClick) {
            host.openParameterOverlay(clickedNode);
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
            // Normal click: focus this node and begin dragging it
            nodeGraph.focusSelectedNode(clickedNode);
            nodeGraph.startDragging(clickedNode, clickX, clickY);
        }
        return true;
    }

    /** Switches to the preset a RUN_PRESET node points at, matching case-insensitively. */
    private void openRunPresetTarget(String targetPreset) {
        if (targetPreset == null || targetPreset.isBlank()) {
            return;
        }
        PresetManager.getAvailablePresets().stream()
            .filter(name -> name.equalsIgnoreCase(targetPreset))
            .findFirst()
            .ifPresent(host::switchPreset);
    }

    /** Handles a click on empty canvas: deselect, delete a connection, or start a marquee. */
    private boolean handleEmptySpaceClick(double mouseX, double mouseY, int button) {
        NodeGraph nodeGraph = host.nodeGraph();
        if (button == 0 && nodeGraph.handleRunPresetDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (button == 0 && nodeGraph.handleSchematicDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }

        // Check if clicking on a connection to delete it
        var connection = nodeGraph.getConnectionAt((int) mouseX, (int) mouseY);
        if (connection != null && button == 1) {
            nodeGraph.removeConnection(connection);
            return true;
        }

        // Clicked on empty space - deselect and stop dragging
        nodeGraph.selectNode(null);
        nodeGraph.stopDraggingConnection();
        if (button == 0) {
            stopInlineFieldEditing(true);
            nodeGraph.beginSelectionBox((int) mouseX, (int) mouseY);
        }
        return true;
    }

    /**
     * Commits and closes every open inline editor. Each editor is independent,
     * so the order below carries no meaning.
     */
    private void stopInlineFieldEditing(boolean includeStickyNote) {
        NodeGraph nodeGraph = host.nodeGraph();
        nodeGraph.stopCoordinateEditing(true);
        nodeGraph.stopAmountEditing(true);
        nodeGraph.stopStopTargetEditing(true);
        nodeGraph.stopVariableEditing(true);
        nodeGraph.stopMessageEditing(true);
        nodeGraph.stopParameterEditing(true);
        nodeGraph.stopEventNameEditing(true);
        if (includeStickyNote) {
            nodeGraph.stopStickyNoteEditing(true);
        }
    }
}
