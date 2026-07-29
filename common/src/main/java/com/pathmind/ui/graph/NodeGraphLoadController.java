package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.List;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;

/** Reconstructs persisted graphs and clears the live workspace. */
final class NodeGraphLoadController {

    interface Host {
        List<Node> nodes();
        List<NodeConnection> connections();
        void setRoutineRegistry(List<NodeGraphData.RoutineDefinitionData> routines);
        void invalidateRenderCaches();
        void clearSelectionTransientState();
        void normalizeStartNodeNumbers();
        void syncRoutineInvocations();
        void addConnectionReplacingConflicts(
            Node outputNode, Node inputNode, int outputSocket, int inputSocket);
        void resetDropTargets();
        void clearConnectionGraphState();
        void clearStartHoverState();
        void invalidateValidation();
        void restoreSessionViewportState();
        void setNextStartNodeNumber(int value);
    }

    private final Host host;

    NodeGraphLoadController(Host host) {
        this.host = host;
    }

    void clearWorkspace() {
        List<Node> nodes = host.nodes();
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
        host.connections().clear();
        host.invalidateRenderCaches();
        host.setNextStartNodeNumber(1);
        host.clearSelectionTransientState();
        host.invalidateValidation();
        host.clearConnectionGraphState();
        host.clearStartHoverState();
    }

    boolean applyLoadedData(NodeGraphData data) {
        host.setRoutineRegistry(new ArrayList<>(data.getRoutines()));
        List<Node> nodes = host.nodes();
        List<NodeConnection> connections = host.connections();
        nodes.clear();
        connections.clear();
        host.invalidateRenderCaches();
        host.clearSelectionTransientState();

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
            if ((node.getType() == NodeType.RUN_PRESET || node.getType() == NodeType.TEMPLATE)
                && node.getParameter("Preset") == null) {
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

        host.normalizeStartNodeNumbers();

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

        host.syncRoutineInvocations();

        // Load connections
        for (NodeGraphData.ConnectionData connData : data.getConnections()) {
            Node outputNode = nodeMap.get(connData.getOutputNodeId());
            Node inputNode = nodeMap.get(connData.getInputNodeId());

            if (outputNode != null && inputNode != null) {
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                host.addConnectionReplacingConflicts(
                    outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket());
            } else {
                System.err.println("Failed to restore connection: missing node(s)");
            }
        }

        host.resetDropTargets();
        host.clearConnectionGraphState();
        host.clearStartHoverState();
        host.invalidateValidation();
        host.restoreSessionViewportState();

        return true;
    }
}
