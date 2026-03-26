package com.pathmind.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles saving and loading node graphs to/from disk.
 */
public class NodeGraphPersistence {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NodeType.class, new NodeTypeAdapter())
            .registerTypeAdapter(com.pathmind.nodes.NodeMode.class, new NodeModeAdapter())
            .create();

    private static final Map<String, String> IN_MEMORY_JSON_CACHE = new ConcurrentHashMap<>();

    /**
     * Save the current node graph to disk
     */
    public static boolean saveNodeGraph(List<Node> nodes, List<NodeConnection> connections) {
        return saveNodeGraphForPreset(PresetManager.getActivePreset(), nodes, connections);
    }

    public static boolean saveNodeGraphForPreset(String presetName, List<Node> nodes, List<NodeConnection> connections) {
        Path savePath = PresetManager.getPresetPath(presetName);
        NodeGraphData existingData = loadNodeGraphFromPath(savePath);
        NodeGraphData data = buildNodeGraphData(presetName, nodes, connections, existingData);
        cachePresetGraph(presetName, data);
        return writeNodeGraphDataToPath(data, savePath);
    }

    public static boolean saveNodeGraphToPath(List<Node> nodes, List<NodeConnection> connections, Path savePath) {
        NodeGraphData data = buildNodeGraphData(null, nodes, connections, null);
        return writeNodeGraphDataToPath(data, savePath);
    }

    /**
     * Load the node graph from disk
     */
    public static NodeGraphData loadNodeGraph() {
        return loadNodeGraphForPreset(PresetManager.getActivePreset());
    }

    public static NodeGraphData loadNodeGraphForPreset(String presetName) {
        Path savePath = PresetManager.getPresetPath(presetName);
        NodeGraphData data = loadNodeGraphFromPath(savePath);
        if (data != null) {
            cachePresetGraph(presetName, data);
            return data;
        }

        String key = cacheKeyForPath(savePath);
        if (key != null) {
            String cached = IN_MEMORY_JSON_CACHE.get(key);
            if (cached != null) {
                try {
                    return GSON.fromJson(cached, NodeGraphData.class);
                } catch (Exception e) {
                    System.err.println("Failed to deserialize cached node graph: " + e.getMessage());
                }
            }
        }

        return null;
    }

    public static NodeGraphData loadNodeGraphFromPath(Path savePath) {
        try {
            if (!Files.exists(savePath)) {
                System.out.println("No saved node graph found at: " + savePath);
                return null;
            }

            try (Reader reader = Files.newBufferedReader(savePath)) {
                NodeGraphData data = GSON.fromJson(reader, NodeGraphData.class);
                System.out.println("Node graph loaded successfully from: " + savePath);
                return data;
            }

        } catch (Exception e) {
            System.err.println("Failed to load node graph: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert loaded data back to Node objects
     */
    public static List<Node> convertToNodes(NodeGraphData data) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> nodeMap = new HashMap<>();

        // Create nodes
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

            // Save parameter values before setting mode (which will reinitialize parameters)
            Map<String, String> savedParamValues = new HashMap<>();
            Map<String, Boolean> savedParamEdited = new HashMap<>();
            if (nodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData paramData : nodeData.getParameters()) {
                    savedParamValues.put(paramData.getName(), paramData.getValue());
                    if (paramData.getUserEdited() != null) {
                        savedParamEdited.put(paramData.getName(), paramData.getUserEdited());
                    }
                }
            }

            // Set the mode if it exists (this will reinitialize parameters)
            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            // Restore saved parameter values (overwrite the default parameters with saved ones)
            if (!savedParamValues.isEmpty()) {
                for (NodeParameter param : node.getParameters()) {
                    String savedValue = savedParamValues.get(param.getName());
                    if (savedValue != null) {
                        param.setStringValue(savedValue);
                    }
                    Boolean edited = savedParamEdited.get(param.getName());
                    if (edited != null) {
                        param.setUserEdited(edited);
                    }
                }
            }
            if (node.hasBooleanToggle()) {
                Boolean storedToggle = nodeData.getBooleanToggleValue();
                if (storedToggle != null) {
                    node.setBooleanToggleValue(storedToggle);
                } else {
                    node.setBooleanToggleValue(true);
                }
            }
            if (node.getType() == NodeType.MESSAGE && nodeData.getMessageLines() != null) {
                node.setMessageLines(nodeData.getMessageLines());
                node.setMessageClientSide(Boolean.TRUE.equals(nodeData.getMessageClientSide()));
            }
            if (node.hasBookTextInput() && nodeData.getBookText() != null) {
                node.setBookText(nodeData.getBookText());
            }
            if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
                node.setTemplateName(nodeData.getTemplateName());
                node.setTemplateVersion(nodeData.getTemplateVersion() != null ? nodeData.getTemplateVersion() : 0);
                node.setCustomNodeInstance(Boolean.TRUE.equals(nodeData.getCustomNodeInstance()));
                node.setTemplateGraphData(nodeData.getTemplateGraph());
            }
            if (node.getType() == NodeType.GOTO) {
                node.setGotoAllowBreakWhileExecuting(Boolean.TRUE.equals(nodeData.getGotoAllowBreakWhileExecuting()));
                node.setGotoAllowPlaceWhileExecuting(Boolean.TRUE.equals(nodeData.getGotoAllowPlaceWhileExecuting()));
            }
            if ((node.getType() == NodeType.STOP_CHAIN || node.getType() == NodeType.START_CHAIN)
                && node.getParameter("StartNumber") == null) {
                node.getParameters().add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
            }
            if (node.getType() == NodeType.RUN_PRESET && node.getParameter("Preset") == null) {
                node.getParameters().add(new NodeParameter("Preset", ParameterType.STRING, ""));
            }
            node.ensureVillagerTradeNumberParameter();
            Integer startNodeNumber = nodeData.getStartNodeNumber();
            if (startNodeNumber != null) {
                node.setStartNodeNumber(startNodeNumber);
            }
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

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

        java.util.Set<Integer> usedStartNumbers = new java.util.HashSet<>();
        int maxStartNumber = 0;
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.START) {
                continue;
            }
            int number = node.getStartNodeNumber();
            if (number > 0 && usedStartNumbers.add(number)) {
                maxStartNumber = Math.max(maxStartNumber, number);
            } else {
                node.setStartNodeNumber(0);
            }
        }
        int nextStartNumber = Math.max(1, maxStartNumber + 1);
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.START) {
                continue;
            }
            if (node.getStartNodeNumber() <= 0) {
                node.setStartNodeNumber(nextStartNumber);
                nextStartNumber++;
            }
        }

        return nodes;
    }

    /**
     * Convert loaded data back to Connection objects
     */
    public static List<NodeConnection> convertToConnections(NodeGraphData data, Map<String, Node> nodeMap) {
        List<NodeConnection> connections = new ArrayList<>();

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

        return connections;
    }

    /**
     * Get the save file path in the Minecraft saves directory
     */
    public static Path getDefaultSavePath() {
        return PresetManager.getPresetPath(PresetManager.getActivePreset());
    }

    /**
     * Check if a saved node graph exists
     */
    public static boolean hasSavedNodeGraph() {
        return hasSavedNodeGraph(PresetManager.getActivePreset());
    }

    public static boolean hasSavedNodeGraph(String presetName) {
        Path path = PresetManager.getPresetPath(presetName);
        if (Files.exists(path)) {
            return true;
        }
        String key = cacheKeyForPath(path);
        return key != null && IN_MEMORY_JSON_CACHE.containsKey(key);
    }

    private static void cachePresetGraph(String presetName, NodeGraphData data) {
        String key = cacheKeyForPreset(presetName);
        if (key != null && data != null) {
            IN_MEMORY_JSON_CACHE.put(key, GSON.toJson(data));
        }
    }

    private static String cacheKeyForPreset(String presetName) {
        return cacheKeyForPath(PresetManager.getPresetPath(presetName));
    }

    private static String cacheKeyForPath(Path path) {
        if (path == null) {
            return null;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static NodeGraphData buildNodeGraphData(String presetName, List<Node> nodes, List<NodeConnection> connections,
                                                    NodeGraphData existingData) {
        NodeGraphData data = new NodeGraphData();
        data.setCustomNodeDefinition(buildCustomNodeDefinition(presetName, nodes, connections, existingData));

        for (Node node : nodes) {
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());

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
            nodeData.setAttachedSensorId(node.getAttachedSensorId());
            nodeData.setParentControlId(node.getParentControlId());
            nodeData.setAttachedActionId(node.getAttachedActionId());
            nodeData.setParentActionControlId(node.getParentActionControlId());
            List<NodeGraphData.ParameterAttachmentData> attachmentData = new ArrayList<>();
            if (!node.getAttachedParameters().isEmpty()) {
                List<Integer> slotIndices = new ArrayList<>(node.getAttachedParameters().keySet());
                java.util.Collections.sort(slotIndices);
                for (Integer slotIndex : slotIndices) {
                    Node parameterNode = node.getAttachedParameter(slotIndex);
                    if (parameterNode != null) {
                        attachmentData.add(new NodeGraphData.ParameterAttachmentData(slotIndex, parameterNode.getId()));
                    }
                }
                if (!attachmentData.isEmpty()) {
                    nodeData.setAttachedParameterId(attachmentData.get(0).getParameterNodeId());
                } else {
                    nodeData.setAttachedParameterId(null);
                }
            } else {
                nodeData.setAttachedParameterId(null);
            }
            nodeData.setParameterAttachments(attachmentData);
            nodeData.setParentParameterHostId(node.getParentParameterHostId());
            if (node.hasBooleanToggle()) {
                nodeData.setBooleanToggleValue(node.getBooleanToggleValue());
            } else {
                nodeData.setBooleanToggleValue(null);
            }
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
            if (node.getType() == NodeType.GOTO) {
                nodeData.setGotoAllowBreakWhileExecuting(node.isGotoAllowBreakWhileExecuting());
                nodeData.setGotoAllowPlaceWhileExecuting(node.isGotoAllowPlaceWhileExecuting());
            } else {
                nodeData.setGotoAllowBreakWhileExecuting(null);
                nodeData.setGotoAllowPlaceWhileExecuting(null);
            }

            data.getNodes().add(nodeData);
        }

        for (NodeConnection connection : connections) {
            if (connection.getOutputNode().isSensorNode() || connection.getInputNode().isSensorNode()) {
                continue;
            }
            NodeGraphData.ConnectionData connData = new NodeGraphData.ConnectionData();
            connData.setOutputNodeId(connection.getOutputNode().getId());
            connData.setInputNodeId(connection.getInputNode().getId());
            connData.setOutputSocket(connection.getOutputSocket());
            connData.setInputSocket(connection.getInputSocket());

            data.getConnections().add(connData);
        }

        return data;
    }

    public static NodeGraphData.CustomNodeDefinition resolveCustomNodeDefinition(String presetName, NodeGraphData data) {
        if (data != null && data.getCustomNodeDefinition() != null) {
            NodeGraphData.CustomNodeDefinition stored = data.getCustomNodeDefinition();
            if (stored.getInputs() == null) {
                stored.setInputs(new ArrayList<>());
            }
            if (stored.getOutputs() == null) {
                stored.setOutputs(new ArrayList<>());
            }
            if (stored.getPresetName() == null || stored.getPresetName().isBlank()) {
                stored.setPresetName(presetName);
            }
            if (stored.getName() == null || stored.getName().isBlank()) {
                stored.setName(stored.getPresetName());
            }
            if (stored.getVersion() == null || stored.getVersion() <= 0) {
                stored.setVersion(1);
            }
            if (stored.getSignature() == null || stored.getSignature().isBlank()) {
                stored.setSignature(buildCustomNodeSignature(data));
            }
            return stored;
        }
        return buildCustomNodeDefinition(presetName, convertToNodesOrEmpty(data), convertToConnectionsOrEmpty(data), null);
    }

    private static List<Node> convertToNodesOrEmpty(NodeGraphData data) {
        if (data == null || data.getNodes() == null || data.getNodes().isEmpty()) {
            return List.of();
        }
        return convertToNodes(data);
    }

    private static List<NodeConnection> convertToConnectionsOrEmpty(NodeGraphData data) {
        if (data == null || data.getNodes() == null || data.getNodes().isEmpty()) {
            return List.of();
        }
        List<Node> rebuiltNodes = convertToNodes(data);
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : rebuiltNodes) {
            nodeMap.put(node.getId(), node);
        }
        return convertToConnections(data, nodeMap);
    }

    private static NodeGraphData.CustomNodeDefinition buildCustomNodeDefinition(String presetName, List<Node> nodes,
                                                                                List<NodeConnection> connections,
                                                                                NodeGraphData existingData) {
        NodeGraphData.CustomNodeDefinition definition = new NodeGraphData.CustomNodeDefinition();
        String resolvedPresetName = (presetName == null || presetName.isBlank())
            ? (existingData != null && existingData.getCustomNodeDefinition() != null
                ? existingData.getCustomNodeDefinition().getPresetName()
                : PresetManager.getActivePreset())
            : presetName.trim();
        definition.setPresetName(resolvedPresetName);
        definition.setName(resolveCustomNodeName(resolvedPresetName, existingData));
        definition.setInputs(discoverCustomNodeInputs(nodes));
        definition.setOutputs(discoverCustomNodeOutputs(nodes));

        String signature = buildCustomNodeSignature(nodes, connections);
        definition.setSignature(signature);

        int previousVersion = 0;
        String previousSignature = null;
        if (existingData != null && existingData.getCustomNodeDefinition() != null) {
            NodeGraphData.CustomNodeDefinition existingDefinition = existingData.getCustomNodeDefinition();
            previousVersion = existingDefinition.getVersion() != null ? existingDefinition.getVersion() : 0;
            previousSignature = existingDefinition.getSignature();
            if ((previousSignature == null || previousSignature.isBlank()) && existingData.getNodes() != null) {
                previousSignature = buildCustomNodeSignature(existingData);
            }
        }
        definition.setVersion(Objects.equals(signature, previousSignature) ? Math.max(1, previousVersion) : Math.max(1, previousVersion + 1));
        return definition;
    }

    private static String resolveCustomNodeName(String presetName, NodeGraphData existingData) {
        if (existingData != null && existingData.getCustomNodeDefinition() != null) {
            String existingName = existingData.getCustomNodeDefinition().getName();
            if (existingName != null && !existingName.isBlank()) {
                return existingName.trim();
            }
        }
        return (presetName == null || presetName.isBlank()) ? "Custom Node" : presetName.trim();
    }

    private static List<NodeGraphData.CustomNodePort> discoverCustomNodeInputs(List<Node> nodes) {
        Map<String, NodeGraphData.CustomNodePort> inputs = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.VARIABLE) {
                continue;
            }
            NodeParameter variableParam = node.getParameter("Variable");
            if (variableParam == null) {
                continue;
            }
            String name = variableParam.getStringValue();
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            inputs.putIfAbsent(normalized, new NodeGraphData.CustomNodePort(name.trim(), "variable", ""));
        }
        return new ArrayList<>(inputs.values());
    }

    private static List<NodeGraphData.CustomNodePort> discoverCustomNodeOutputs(List<Node> nodes) {
        List<NodeGraphData.CustomNodePort> outputs = new ArrayList<>();
        List<Node> starts = new ArrayList<>();
        for (Node node : nodes) {
            if (node != null && node.getType() == NodeType.START) {
                starts.add(node);
            }
        }
        starts.sort(Comparator.comparingInt(Node::getStartNodeNumber));
        for (Node start : starts) {
            int startNumber = start.getStartNodeNumber();
            String label = startNumber > 0 ? "Start " + startNumber : "Start";
            outputs.add(new NodeGraphData.CustomNodePort(label, "flow", ""));
        }
        if (outputs.isEmpty()) {
            outputs.add(new NodeGraphData.CustomNodePort("Flow", "flow", ""));
        }
        return outputs;
    }

    private static String buildCustomNodeSignature(NodeGraphData data) {
        if (data == null) {
            return "";
        }
        StringBuilder raw = new StringBuilder();
        List<NodeGraphData.NodeData> nodeList = data.getNodes() == null ? List.of() : new ArrayList<>(data.getNodes());
        nodeList.sort(Comparator
            .comparing((NodeGraphData.NodeData node) -> node.getType() == null ? "" : node.getType().name())
            .thenComparing(node -> node.getId() == null ? "" : node.getId()));
        for (NodeGraphData.NodeData node : nodeList) {
            raw.append(node.getType() == null ? "" : node.getType().name()).append('|');
            List<NodeGraphData.ParameterData> params = node.getParameters() == null ? List.of() : new ArrayList<>(node.getParameters());
            params.sort(Comparator.comparing(NodeGraphData.ParameterData::getName, Comparator.nullsFirst(String::compareToIgnoreCase)));
            for (NodeGraphData.ParameterData param : params) {
                raw.append(param.getName()).append('=').append(param.getValue()).append(';');
            }
            raw.append('\n');
        }
        List<NodeGraphData.ConnectionData> connectionList = data.getConnections() == null ? List.of() : new ArrayList<>(data.getConnections());
        connectionList.sort(Comparator
            .comparing(NodeGraphData.ConnectionData::getOutputNodeId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(NodeGraphData.ConnectionData::getInputNodeId, Comparator.nullsFirst(String::compareTo))
            .thenComparingInt(NodeGraphData.ConnectionData::getOutputSocket)
            .thenComparingInt(NodeGraphData.ConnectionData::getInputSocket));
        for (NodeGraphData.ConnectionData connection : connectionList) {
            raw.append(connection.getOutputNodeId()).append("->").append(connection.getInputNodeId())
                .append(':').append(connection.getOutputSocket()).append(':').append(connection.getInputSocket()).append('\n');
        }
        return sha256(raw.toString());
    }

    private static String buildCustomNodeSignature(List<Node> nodes, List<NodeConnection> connections) {
        StringBuilder raw = new StringBuilder();
        List<Node> nodeList = new ArrayList<>(nodes);
        nodeList.sort(Comparator
            .comparing((Node node) -> node.getType() == null ? "" : node.getType().name())
            .thenComparing(Node::getId));
        for (Node node : nodeList) {
            raw.append(node.getType() == null ? "" : node.getType().name()).append('|');
            List<NodeParameter> params = new ArrayList<>(node.getParameters());
            params.sort(Comparator.comparing(NodeParameter::getName, Comparator.nullsFirst(String::compareToIgnoreCase)));
            for (NodeParameter param : params) {
                raw.append(param.getName()).append('=').append(param.getStringValue()).append(';');
            }
            raw.append('\n');
        }
        List<NodeConnection> connectionList = new ArrayList<>(connections);
        connectionList.sort(Comparator
            .comparing((NodeConnection connection) -> connection.getOutputNode().getId())
            .thenComparing(connection -> connection.getInputNode().getId())
            .thenComparingInt(NodeConnection::getOutputSocket)
            .thenComparingInt(NodeConnection::getInputSocket));
        for (NodeConnection connection : connectionList) {
            raw.append(connection.getOutputNode().getId()).append("->").append(connection.getInputNode().getId())
                .append(':').append(connection.getOutputSocket()).append(':').append(connection.getInputSocket()).append('\n');
        }
        return sha256(raw.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private static boolean writeNodeGraphDataToPath(NodeGraphData data, Path savePath) {
        try {
            if (savePath.getParent() != null) {
                Files.createDirectories(savePath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(data, writer);
            }
            System.out.println("Node graph saved successfully to: " + savePath);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save node graph: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

/**
 * Custom adapter for NodeType enum serialization
 */
class NodeTypeAdapter extends com.google.gson.TypeAdapter<NodeType> {
    @Override
    public void write(com.google.gson.stream.JsonWriter out, NodeType value) throws java.io.IOException {
        out.value(value.name());
    }

    @Override
    public NodeType read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
        String name = in.nextString();
        try {
            if ("MINE".equals(name)) {
                return NodeType.COLLECT;
            }
            if ("CLOSE_INVENTORY".equals(name)) {
                return NodeType.CLOSE_GUI;
            }
            return NodeType.valueOf(name);
        } catch (IllegalArgumentException e) {
            // Handle unknown node types gracefully
            System.err.println("Unknown node type: " + name + ", skipping...");
            return null;
        }
    }
}

/**
 * Custom adapter for NodeMode enum serialization
 */
class NodeModeAdapter extends com.google.gson.TypeAdapter<com.pathmind.nodes.NodeMode> {
    @Override
    public void write(com.google.gson.stream.JsonWriter out, com.pathmind.nodes.NodeMode value) throws java.io.IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.name());
    }

    @Override
    public com.pathmind.nodes.NodeMode read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String name = in.nextString();
        try {
            if ("MINE_SINGLE".equals(name)) {
                return com.pathmind.nodes.NodeMode.COLLECT_SINGLE;
            }
            if ("MINE_MULTIPLE".equals(name)) {
                return com.pathmind.nodes.NodeMode.COLLECT_MULTIPLE;
            }
            return com.pathmind.nodes.NodeMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown node mode: " + name + ", skipping...");
            return null;
        }
    }
}
