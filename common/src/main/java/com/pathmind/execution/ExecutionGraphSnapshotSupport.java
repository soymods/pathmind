package com.pathmind.execution;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ExecutionGraphSnapshotSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionManager.class);

    private ExecutionGraphSnapshotSupport() {
    }

    static LoadedGraph buildGraphFromData(NodeGraphData graphData) {
        if (graphData == null || graphData.getNodes() == null) {
            return null;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        List<Node> nodes = new ArrayList<>();

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData == null || nodeData.getType() == null) {
                LOGGER.warn("Skipping unsupported node entry during replay graph load");
                continue;
            }
            Node node = new Node(nodeData.getType(), nodeData.getX(), nodeData.getY());

            try {
                java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(node, nodeData.getId());
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Failed to set node ID during replay: {}", e.getMessage());
            }

            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            NodeGraphPersistence.restoreParameters(node, nodeData.getParameters());
            if (node.supportsRuntimeValueScope()) {
                node.setRuntimeValueScope(nodeData.getRuntimeValueScope());
            }
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedSensorId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node sensor = nodeMap.get(nodeData.getAttachedSensorId());
                if (control != null && sensor != null) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentControlId() != null) {
                Node sensor = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentControlId());
                if (sensor != null && control != null && sensor.isSensorNode()) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedActionId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node child = nodeMap.get(nodeData.getAttachedActionId());
                if (control != null && child != null) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentActionControlId() != null) {
                Node child = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentActionControlId());
                if (child != null && control != null && control.canAcceptActionNode(child)) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
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

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
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

        List<NodeConnection> connections = new ArrayList<>();
        if (graphData.getConnections() != null) {
            for (NodeGraphData.ConnectionData connData : graphData.getConnections()) {
                Node outputNode = nodeMap.get(connData.getOutputNodeId());
                Node inputNode = nodeMap.get(connData.getInputNodeId());
                if (outputNode != null && inputNode != null) {
                    if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                        continue;
                    }
                    connections.add(new NodeConnection(outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket()));
                }
            }
        }

        return new LoadedGraph(nodes, connections, nodeMap);
    }

    static final class LoadedGraph {
        final List<Node> nodes;
        final List<NodeConnection> connections;
        final Map<String, Node> nodeLookup;

        LoadedGraph(List<Node> nodes, List<NodeConnection> connections, Map<String, Node> nodeLookup) {
            this.nodes = nodes;
            this.connections = connections;
            this.nodeLookup = nodeLookup;
        }
    }

    static final class BranchData {
        final List<Node> nodes;
        final List<NodeConnection> connections;

        BranchData(List<Node> nodes, List<NodeConnection> connections) {
            this.nodes = nodes;
            this.connections = connections;
        }
    }

    static NodeConnection getNextConnectedConnection(Node currentNode, List<NodeConnection> connections, int outputSocket) {
        // Use branch-local object identity when traversing to avoid cross-graph collisions when
        // multiple loaded presets contain the same persisted node IDs.
        for (NodeConnection connection : connections) {
            if (connection.getOutputNode() == currentNode) {
                if (connection.getOutputSocket() == outputSocket) {
                    return connection;
                }
            }
        }
        return null;
    }

    static List<NodeConnection> getOutgoingConnections(Node currentNode, List<NodeConnection> connections) {
        if (currentNode == null || connections == null || connections.isEmpty()) {
            return List.of();
        }

        List<NodeConnection> matches = new ArrayList<>();
        for (NodeConnection connection : connections) {
            if (connection.getOutputNode() == currentNode) {
                matches.add(connection);
            }
        }
        matches.sort((left, right) -> Integer.compare(left.getOutputSocket(), right.getOutputSocket()));
        return matches;
    }

    static List<Node> findStartNodes(List<Node> nodes) {
        List<Node> startNodes = new ArrayList<>();
        if (nodes == null) {
            return startNodes;
        }

        for (Node node : nodes) {
            if (node.getType() == NodeType.START) {
                startNodes.add(node);
            }
        }

        return startNodes;
    }

    static Set<Node> collectBranchNodes(Node startNode, List<NodeConnection> connections) {
        LinkedHashSet<Node> visited = new LinkedHashSet<>();
        if (startNode == null) {
            return visited;
        }

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(startNode);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }

            Node attachedSensor = current.getAttachedSensor();
            if (attachedSensor != null) {
                stack.push(attachedSensor);
            }

            Node attachedAction = current.getAttachedActionNode();
            if (attachedAction != null) {
                stack.push(attachedAction);
            }

            Map<Integer, Node> attachedParameters = current.getAttachedParameters();
            if (attachedParameters != null && !attachedParameters.isEmpty()) {
                for (Node parameter : attachedParameters.values()) {
                    if (parameter != null) {
                        stack.push(parameter);
                    }
                }
            }

            for (NodeConnection connection : connections) {
                if (connection.getOutputNode() == current) {
                    stack.push(connection.getInputNode());
                }
            }
        }

        return visited;
    }

    static Node findStartNodeByNumber(List<Node> nodes, int startNodeNumber) {
        if (nodes == null || startNodeNumber <= 0) {
            return null;
        }
        for (Node startNode : nodes) {
            if (startNode != null && startNode.getType() == NodeType.START
                && startNode.getStartNodeNumber() == startNodeNumber) {
                return startNode;
            }
        }
        return null;
    }

    static BranchData buildBranchData(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        if (startNode == null || nodes == null || connections == null) {
            return null;
        }
        Set<Node> branchNodeSet = collectBranchNodes(startNode, connections);

        for (Node node : nodes) {
            if (node != null && node.getType() == NodeType.EVENT_FUNCTION) {
                branchNodeSet.addAll(collectBranchNodes(node, connections));
            }
        }

        List<Node> branchNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (branchNodeSet.contains(node)) {
                branchNodes.add(node);
            }
        }

        List<NodeConnection> branchConnections = new ArrayList<>();
        for (NodeConnection connection : connections) {
            if (branchNodeSet.contains(connection.getOutputNode()) && branchNodeSet.contains(connection.getInputNode())) {
                branchConnections.add(connection);
            }
        }

        return new BranchData(branchNodes, branchConnections);
    }

    static <T> List<T> snapshotList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        synchronized (source) {
            return new ArrayList<>(source);
        }
    }

    static BranchLaunchData createBranchLaunchData(BranchData branchData, int startNodeNumber) {
        BranchData isolatedBranchData = cloneBranchData(branchData);
        if (isolatedBranchData == null || isolatedBranchData.nodes.isEmpty()) {
            return null;
        }

        Node isolatedStartNode = findStartNodeByNumber(isolatedBranchData.nodes, startNodeNumber);
        if (isolatedStartNode == null) {
            return null;
        }

        assignRuntimeNodeIds(isolatedBranchData.nodes);
        return new BranchLaunchData(isolatedBranchData, isolatedStartNode);
    }

    static BranchLaunchData createBranchLaunchData(BranchData branchData, Node rootNode) {
        if (rootNode == null) {
            return null;
        }

        BranchData isolatedBranchData = cloneBranchData(branchData);
        if (isolatedBranchData == null || isolatedBranchData.nodes.isEmpty()) {
            return null;
        }

        Node isolatedRootNode = findNodeById(isolatedBranchData.nodes, rootNode.getId());
        if (isolatedRootNode == null) {
            return null;
        }

        assignRuntimeNodeIds(isolatedBranchData.nodes);
        return new BranchLaunchData(isolatedBranchData, isolatedRootNode);
    }

    static BranchLaunchData createBranchLaunchData(NodeGraphData graphSnapshot, String rootNodeId) {
        if (graphSnapshot == null || rootNodeId == null || rootNodeId.isEmpty()) {
            return null;
        }

        List<Node> clonedNodes = NodeGraphPersistence.convertToNodes(graphSnapshot);
        if (clonedNodes == null || clonedNodes.isEmpty()) {
            return null;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : clonedNodes) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }

        List<NodeConnection> clonedConnections = NodeGraphPersistence.convertToConnections(graphSnapshot, nodeMap);
        Node isolatedRootNode = findNodeById(clonedNodes, rootNodeId);
        if (isolatedRootNode == null) {
            return null;
        }

        assignRuntimeNodeIds(clonedNodes);
        return new BranchLaunchData(new BranchData(clonedNodes, clonedConnections), isolatedRootNode);
    }

    static BranchData cloneBranchData(BranchData branchData) {
        if (branchData == null || branchData.nodes == null || branchData.connections == null) {
            return null;
        }

        NodeGraphData snapshot = createGraphSnapshot(branchData.nodes, branchData.connections);
        List<Node> clonedNodes = NodeGraphPersistence.convertToNodes(snapshot);
        if (clonedNodes == null || clonedNodes.isEmpty()) {
            return null;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : clonedNodes) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }

        List<NodeConnection> clonedConnections = NodeGraphPersistence.convertToConnections(snapshot, nodeMap);
        return new BranchData(clonedNodes, clonedConnections);
    }

    static void assignRuntimeNodeIds(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        try {
            java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
            idField.setAccessible(true);
            for (Node node : nodes) {
                if (node == null) {
                    continue;
                }
                if (node.getRuntimeSourceNodeId() == null || node.getRuntimeSourceNodeId().isBlank()) {
                    node.setRuntimeSourceNodeId(node.getId());
                }
                // Runtime clones must not reuse persisted IDs or nested preset executions with
                // forever loops can collide in active-node/connection tracking.
                idField.set(node, UUID.randomUUID().toString());
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to assign runtime node IDs: {}", e.getMessage());
        }
    }

    static Node findNodeById(List<Node> nodes, String nodeId) {
        if (nodes == null || nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        for (Node node : nodes) {
            if (node != null && nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    static final class BranchLaunchData {
        final BranchData branchData;
        final Node rootNode;

        BranchLaunchData(BranchData branchData, Node rootNode) {
            this.branchData = branchData;
            this.rootNode = rootNode;
        }
    }

    static final class EventHandlerLaunchData {
        final List<Node> branchNodes;
        final List<NodeConnection> branchConnections;
        final Node rootNode;

        EventHandlerLaunchData(BranchLaunchData launchData) {
            this.branchNodes = launchData.branchData.nodes;
            this.branchConnections = launchData.branchData.connections;
            this.rootNode = launchData.rootNode;
        }
    }

    static final class HandlerTemplate {
        final NodeGraphData graphSnapshot;
        final String rootNodeId;

        HandlerTemplate(NodeGraphData graphSnapshot, String rootNodeId) {
            this.graphSnapshot = graphSnapshot;
            this.rootNodeId = rootNodeId;
        }
    }

    static NodeGraphData createGraphSnapshot(List<Node> nodes, List<NodeConnection> connections) {
        NodeGraphData snapshot = new NodeGraphData();

        for (Node node : nodes) {
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());

            List<NodeGraphData.ParameterData> parameterDataList = new ArrayList<>();
            for (NodeParameter parameter : node.getParameters()) {
                NodeGraphData.ParameterData parameterData = new NodeGraphData.ParameterData();
                parameterData.setName(parameter.getName());
                parameterData.setValue(parameter.getStringValue());
                parameterData.setType(parameter.getType().name());
                parameterData.setUserEdited(parameter.isUserEdited());
                parameterDataList.add(parameterData);
            }
            nodeData.setParameters(parameterDataList);
            nodeData.setAttachedSensorId(node.getAttachedSensorId());
            nodeData.setParentControlId(node.getParentControlId());
            nodeData.setAttachedActionId(node.getAttachedActionId());
            nodeData.setParentActionControlId(node.getParentActionControlId());
            nodeData.setStartNodeNumber(node.getStartNodeNumber());
            nodeData.setStartLaunchMode(node.getStartLaunchMode());
            nodeData.setStartScreenTarget(node.getStartScreenTarget());
            nodeData.setRuntimeSourceNodeId(node.getRuntimeSourceNodeId());
            List<NodeGraphData.ParameterAttachmentData> attachmentData = new ArrayList<>();
            Map<Integer, Node> attachedParameters = node.getAttachedParameters();
            if (attachedParameters != null && !attachedParameters.isEmpty()) {
                List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
                Collections.sort(slotIndices);
                for (Integer slotIndex : slotIndices) {
                    Node parameterNode = attachedParameters.get(slotIndex);
                    if (parameterNode != null) {
                        NodeGraphData.ParameterAttachmentData attachment = new NodeGraphData.ParameterAttachmentData(slotIndex, parameterNode.getId());
                        if (node.getType() == NodeType.ROUTINE_CALL) attachment.setRoutineInputId(node.getRoutineInputIdForSlot(slotIndex));
                        attachmentData.add(attachment);
                    }
                }
            }
            nodeData.setParameterAttachments(attachmentData);
            if (!attachmentData.isEmpty()) {
                nodeData.setAttachedParameterId(attachmentData.get(0).getParameterNodeId());
            } else {
                nodeData.setAttachedParameterId(node.getAttachedParameterId());
            }
            nodeData.setParentParameterHostId(node.getParentParameterHostId());
            if (node.hasBooleanToggle()) {
                nodeData.setBooleanToggleValue(node.getBooleanToggleValue());
            }
            if (node.getType() == NodeType.START) {
                nodeData.setStartNodeNumber(node.getStartNodeNumber());
            }
            nodeData.setRuntimeValueScope(node.supportsRuntimeValueScope() ? node.getRuntimeValueScope() : null);
            nodeData.setRoutineId(node.getRoutineId().isBlank() ? null : node.getRoutineId());
            nodeData.setRoutineInputId(node.getRoutineInputId().isBlank() ? null : node.getRoutineInputId());
            nodeData.setRoutineArguments(node.getRoutineArguments());
            if (node.hasMessageInputFields()) {
                nodeData.setMessageLines(new ArrayList<>(node.getMessageLines()));
                nodeData.setMessageClientSide(node.hasMessageScopeToggle() ? node.isMessageClientSide() : null);
            }
            if (node.hasBookTextInput()) {
                nodeData.setBookText(node.getBookText());
            }
            if (node.getType() == NodeType.GOTO || node.getType() == NodeType.TRAVEL) {
                nodeData.setGotoAllowBreakWhileExecuting(node.isGotoAllowBreakWhileExecuting());
                nodeData.setGotoAllowPlaceWhileExecuting(node.isGotoAllowPlaceWhileExecuting());
            }
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                nodeData.setKeyPressedActivatesInGuis(node.isKeyPressedActivatesInGuis());
            }
            if (node.getType() == NodeType.TEMPLATE) {
                nodeData.setTemplateName(node.getTemplateName());
                nodeData.setTemplateVersion(node.getTemplateVersion());
                nodeData.setTemplateGraph(node.getTemplateGraphData());
            }

            snapshot.getNodes().add(nodeData);
        }

        for (NodeConnection connection : filterConnections(connections)) {
            NodeGraphData.ConnectionData connectionData = new NodeGraphData.ConnectionData(
                    connection.getOutputNode().getId(),
                    connection.getInputNode().getId(),
                    connection.getOutputSocket(),
                    connection.getInputSocket()
            );
            snapshot.getConnections().add(connectionData);
        }

        return snapshot;
    }

    static List<NodeConnection> filterConnections(List<NodeConnection> connections) {
        List<NodeConnection> filtered = new ArrayList<>();
        if (connections == null) {
            return filtered;
        }
        for (NodeConnection connection : connections) {
            if (connection == null) {
                continue;
            }
            Node output = connection.getOutputNode();
            Node input = connection.getInputNode();
            if (output == null || input == null) {
                continue;
            }
            if (output.isSensorNode() || input.isSensorNode()) {
                continue;
            }
            if (connection.getOutputSocket() < 0 || connection.getOutputSocket() >= output.getOutputSocketCount()) {
                continue;
            }
            filtered.add(connection);
        }
        return filtered;
    }
}

