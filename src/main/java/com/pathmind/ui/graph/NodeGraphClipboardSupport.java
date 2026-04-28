package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NodeGraphClipboardSupport {
    private NodeGraphClipboardSupport() {
    }

    static NodeGraph.ClipboardSnapshot createClipboardSnapshot(
        NodeGraph graph,
        Collection<Node> selection,
        Collection<NodeConnection> connections
    ) {
        if (selection == null || selection.isEmpty()) {
            return null;
        }
        List<Node> hierarchy = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        for (Node node : selection) {
            graph.collectHierarchy(node, hierarchy, visited);
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
        NodeGraph.SelectionBounds bounds = graph.calculateBounds(subset);
        int anchorX = bounds != null ? bounds.minX : 0;
        int anchorY = bounds != null ? bounds.minY : 0;
        List<String> selectionIds = new ArrayList<>();
        for (Node node : selection) {
            if (node != null) {
                selectionIds.add(node.getId());
            }
        }
        return new NodeGraph.ClipboardSnapshot(data, selectionIds, anchorX, anchorY);
    }

    static Node instantiateClipboardSnapshot(
        NodeGraph graph,
        NodeGraph.ClipboardSnapshot snapshot,
        int targetAnchorX,
        int targetAnchorY,
        List<Node> nodes,
        List<NodeConnection> connections
    ) {
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
                graph.assignNewStartNodeNumber(newNode);
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
            graph.selectNodes(clonesForSelection);
            for (Node clone : clonesForSelection) {
                graph.bringNodeToFront(clone);
            }
        } else if (!idToNode.isEmpty()) {
            primaryClone = idToNode.values().iterator().next();
            graph.selectNode(primaryClone);
            graph.bringNodeToFront(primaryClone);
        }

        graph.markWorkspaceDirty();
        return primaryClone;
    }

    static NodeGraphData buildGraphData(
        Collection<Node> nodeCollection,
        Collection<NodeConnection> connectionCollection,
        Set<Node> allowedNodes
    ) {
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
}
