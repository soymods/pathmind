package com.pathmind.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeTraitRegistry;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeValueTrait;
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

    public static boolean saveNodeGraphDataForPreset(String presetName, NodeGraphData data) {
        if (presetName == null || presetName.isBlank() || data == null) {
            return false;
        }
        cachePresetGraph(presetName, data);
        return writeNodeGraphDataToPath(data, PresetManager.getPresetPath(presetName));
    }

    public static boolean saveNodeGraphDataToPath(NodeGraphData data, Path savePath) {
        if (data == null || savePath == null) {
            return false;
        }
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
                return null;
            }

            try (Reader reader = Files.newBufferedReader(savePath)) {
                return GSON.fromJson(reader, NodeGraphData.class);
            }

        } catch (Exception e) {
            System.err.println("Failed to load node graph: " + e.getMessage());
            return null;
        }
    }

    public static boolean normalizeNodeGraphToPath(Path sourcePath, Path targetPath) {
        if (sourcePath == null || targetPath == null) {
            return false;
        }
        NodeGraphData data = loadNodeGraphFromPath(sourcePath);
        if (data == null) {
            return false;
        }
        List<Node> nodes = convertToNodes(data);
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            if (node != null) {
                nodeMap.put(node.getId(), node);
            }
        }
        List<NodeConnection> connections = convertToConnections(data, nodeMap);
        return saveNodeGraphToPath(nodes, connections, targetPath);
    }

    public static ParameterType parseParameterType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        try {
            return ParameterType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown parameter type: " + rawType + ", skipping...");
            return null;
        }
    }

    public static void restoreParameters(Node node, List<NodeGraphData.ParameterData> parameterData) {
        if (node == null) {
            return;
        }

        Map<String, NodeGraphData.ParameterData> savedById = new HashMap<>();
        Map<String, NodeGraphData.ParameterData> savedByName = new HashMap<>();
        if (parameterData != null) {
            for (NodeGraphData.ParameterData paramData : parameterData) {
                if (paramData == null || paramData.getName() == null || paramData.getName().isBlank()) {
                    continue;
                }
                String serializedId = normalizeParameterId(paramData.getId(), paramData.getName());
                if (!serializedId.isEmpty()) {
                    savedById.put(serializedId, paramData);
                }
                savedByName.put(paramData.getName(), paramData);
            }
        }

        if (savedById.isEmpty() && savedByName.isEmpty()) {
            return;
        }

        for (NodeParameter param : node.getParameters()) {
            NodeGraphData.ParameterData saved = savedById.get(param.getId());
            if (saved == null) {
                saved = savedByName.get(param.getName());
            }
            if (saved == null) {
                continue;
            }
            if (saved.getValue() != null) {
                param.setStringValue(saved.getValue());
            }
            if (saved.getUserEdited() != null) {
                param.setUserEdited(saved.getUserEdited());
            }
        }

        for (NodeGraphData.ParameterData saved : parameterData == null ? List.<NodeGraphData.ParameterData>of() : parameterData) {
            if (saved == null || saved.getName() == null || saved.getName().isBlank()) {
                continue;
            }
            String serializedId = normalizeParameterId(saved.getId(), saved.getName());
            boolean alreadyPresent = false;
            for (NodeParameter param : node.getParameters()) {
                if (serializedId.equals(param.getId()) || saved.getName().equals(param.getName())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (alreadyPresent) {
                continue;
            }
            ParameterType paramType = parseParameterType(saved.getType());
            if (paramType == null) {
                continue;
            }
            NodeParameter param = new NodeParameter(serializedId, saved.getName(), paramType, saved.getValue());
            if (saved.getUserEdited() != null) {
                param.setUserEdited(saved.getUserEdited());
            }
            node.getParameters().add(param);
        }
        node.repairSerializedParameters();
    }

    private static String normalizeParameterId(String id, String name) {
        return NodeParameter.createDefaultId(id != null && !id.isBlank() ? id : name);
    }

    /**
     * Convert loaded data back to Node objects
     */
    public static List<Node> convertToNodes(NodeGraphData data) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> nodeMap = new HashMap<>();

        // Create nodes
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData == null || nodeData.getType() == null) {
                System.err.println("Skipping unsupported node entry during conversion.");
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

            restoreParameters(node, nodeData.getParameters());
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
            if (node.isStickyNote()) {
                node.setStickyNoteText(nodeData.getStickyNoteText());
                Integer stickyNoteWidth = nodeData.getStickyNoteWidth();
                Integer stickyNoteHeight = nodeData.getStickyNoteHeight();
                if (stickyNoteWidth != null || stickyNoteHeight != null) {
                    node.setStickyNoteSize(
                        stickyNoteWidth != null ? stickyNoteWidth : node.getWidth(),
                        stickyNoteHeight != null ? stickyNoteHeight : node.getHeight()
                    );
                }
            }
            if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
                node.setTemplateName(nodeData.getTemplateName());
                node.setTemplateVersion(nodeData.getTemplateVersion() != null ? nodeData.getTemplateVersion() : 0);
                node.setCustomNodeInstance(Boolean.TRUE.equals(nodeData.getCustomNodeInstance()));
                node.setTemplateGraphData(nodeData.getTemplateGraph());
            }
            if (node.getType() == NodeType.GOTO || node.getType() == NodeType.TRAVEL) {
                node.setGotoAllowBreakWhileExecuting(Boolean.TRUE.equals(nodeData.getGotoAllowBreakWhileExecuting()));
                node.setGotoAllowPlaceWhileExecuting(Boolean.TRUE.equals(nodeData.getGotoAllowPlaceWhileExecuting()));
            }
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                Boolean storedValue = nodeData.getKeyPressedActivatesInGuis();
                node.setKeyPressedActivatesInGuis(storedValue == null || storedValue);
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

        absorbLegacyWaitDurationAttachments(data, nodes, nodeMap);

        if (requiresLegacyAttachmentRecovery(data)) {
            recoverMissingNestedAttachments(nodes);
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

    private static void absorbLegacyWaitDurationAttachments(NodeGraphData data, List<Node> nodes, Map<String, Node> nodeMap) {
        if (data == null) {
            return;
        }
        List<Node> legacyDurationNodes = new ArrayList<>();
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData == null || nodeData.getType() != NodeType.WAIT) {
                continue;
            }

            String durationNodeId = null;
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null) {
                for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
                    if (attachment != null && attachment.getSlotIndex() == 0) {
                        durationNodeId = attachment.getParameterNodeId();
                        break;
                    }
                }
            }
            if (durationNodeId == null || durationNodeId.isBlank()) {
                durationNodeId = nodeData.getAttachedParameterId();
            }
            if (durationNodeId == null || durationNodeId.isBlank()) {
                continue;
            }

            Node node = nodeMap.get(nodeData.getId());
            Node attached = nodeMap.get(durationNodeId);
            NodeGraphData.NodeData attachedNodeData = null;
            for (NodeGraphData.NodeData candidate : data.getNodes()) {
                if (candidate != null && durationNodeId.equals(candidate.getId())) {
                    attachedNodeData = candidate;
                    break;
                }
            }
            if (node == null) {
                continue;
            }
            if (attached == null || attached.getType() != NodeType.PARAM_DURATION) {
                if (attachedNodeData == null || attachedNodeData.getType() != NodeType.PARAM_DURATION) {
                    continue;
                }
            }

            String value = null;
            if (attachedNodeData != null && attachedNodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData parameterData : attachedNodeData.getParameters()) {
                    if (parameterData != null && "Duration".equals(parameterData.getName())) {
                        value = parameterData.getValue();
                        break;
                    }
                }
            }
            if (value == null && attached != null) {
                NodeParameter legacyDuration = attached.getParameter("Duration");
                value = legacyDuration != null ? legacyDuration.getStringValue() : null;
            }
            if (value != null) {
                node.setParameterValueAndPropagate("Duration", value);
            }

            com.pathmind.nodes.NodeMode legacyMode = attachedNodeData != null ? attachedNodeData.getMode() : null;
            if (legacyMode == null && attached != null) {
                legacyMode = attached.getMode();
            }
            if (legacyMode != null) {
                node.setMode(legacyMode);
                if (value != null) {
                    node.setParameterValueAndPropagate("Duration", value);
                }
            }

            if (attached == null) {
                continue;
            }
            legacyDurationNodes.add(attached);
        }

        if (legacyDurationNodes.isEmpty()) {
            return;
        }

        nodes.removeAll(legacyDurationNodes);
        for (Node legacyDurationNode : legacyDurationNodes) {
            nodeMap.remove(legacyDurationNode.getId());
        }
    }

    private static boolean requiresLegacyAttachmentRecovery(NodeGraphData data) {
        if (data == null || data.getNodes() == null || data.getNodes().isEmpty()) {
            return false;
        }
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData == null) {
                continue;
            }
            if (nodeData.getAttachedSensorId() != null
                || nodeData.getParentControlId() != null
                || nodeData.getAttachedActionId() != null
                || nodeData.getParentActionControlId() != null
                || nodeData.getAttachedParameterId() != null
                || nodeData.getParentParameterHostId() != null) {
                return false;
            }
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void recoverMissingNestedAttachments(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        recoverMissingSensorAttachments(nodes);
        recoverMissingActionAttachments(nodes);
        recoverMissingParameterAttachments(nodes);
    }

    private static void recoverMissingSensorAttachments(List<Node> nodes) {
        for (Node child : nodes) {
            if (child == null || !child.isSensorNode() || child.isAttachedToControl()
                || child.isAttachedToActionControl() || child.getParentParameterHost() != null) {
                continue;
            }
            Node bestHost = findBestSensorHost(nodes, child);
            if (bestHost != null) {
                bestHost.attachSensor(child);
            }
        }
    }

    private static void recoverMissingActionAttachments(List<Node> nodes) {
        for (Node child : nodes) {
            if (child == null || child.isSensorNode() || child.isAttachedToActionControl()
                || child.isAttachedToControl() || child.getParentParameterHost() != null) {
                continue;
            }
            Node bestHost = findBestActionHost(nodes, child);
            if (bestHost != null) {
                bestHost.attachActionNode(child);
            }
        }
    }

    private static void recoverMissingParameterAttachments(List<Node> nodes) {
        for (Node child : nodes) {
            if (child == null || child.getParentParameterHost() != null
                || child.isAttachedToControl() || child.isAttachedToActionControl()
                || (!child.isParameterNode() && !child.isSensorNode())) {
                continue;
            }
            ParameterAttachmentCandidate best = findBestParameterHost(nodes, child);
            if (best != null) {
                best.host.attachParameter(child, best.slotIndex);
            }
        }
    }

    private static Node findBestSensorHost(List<Node> nodes, Node child) {
        Node bestHost = null;
        double bestScore = Double.MAX_VALUE;
        int anchorX = child.getX() + Math.min(4, Math.max(0, child.getWidth() - 1));
        int anchorY = child.getY() + Math.min(4, Math.max(0, child.getHeight() - 1));
        for (Node host : nodes) {
            if (host == null || host == child || !host.hasSensorSlot() || host.getAttachedSensor() != null) {
                continue;
            }
            if (!host.isPointInsideSensorSlot(anchorX, anchorY)) {
                continue;
            }
            double score = squaredDistanceToSlotCenter(
                anchorX,
                anchorY,
                host.getSensorSlotLeft(),
                host.getSensorSlotTop(),
                host.getSensorSlotWidth(),
                host.getSensorSlotHeight()
            );
            if (score < bestScore) {
                bestScore = score;
                bestHost = host;
            }
        }
        return bestHost;
    }

    private static Node findBestActionHost(List<Node> nodes, Node child) {
        Node bestHost = null;
        double bestScore = Double.MAX_VALUE;
        int anchorX = child.getX() + Math.min(4, Math.max(0, child.getWidth() - 1));
        int anchorY = child.getY() + Math.min(4, Math.max(0, child.getHeight() - 1));
        for (Node host : nodes) {
            if (host == null || host == child || !host.hasActionSlot() || host.getAttachedActionNode() != null
                || !host.canAcceptActionNode(child)) {
                continue;
            }
            if (!host.isPointInsideActionSlot(anchorX, anchorY)) {
                continue;
            }
            double score = squaredDistanceToSlotCenter(
                anchorX,
                anchorY,
                host.getActionSlotLeft(),
                host.getActionSlotTop(),
                host.getActionSlotWidth(),
                host.getActionSlotHeight()
            );
            if (score < bestScore) {
                bestScore = score;
                bestHost = host;
            }
        }
        return bestHost;
    }

    private static ParameterAttachmentCandidate findBestParameterHost(List<Node> nodes, Node child) {
        ParameterAttachmentCandidate best = null;
        double bestScore = Double.MAX_VALUE;
        int anchorX = child.getX() + Math.min(4, Math.max(0, child.getWidth() - 1));
        int anchorY = child.getY() + Math.min(4, Math.max(0, child.getHeight() - 1));
        for (Node host : nodes) {
            if (host == null || host == child || !host.hasParameterSlot()) {
                continue;
            }
            int slotCount = host.getParameterSlotCount();
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                if (host.getAttachedParameter(slotIndex) != null || !host.canAcceptParameterNode(child, slotIndex)) {
                    continue;
                }
                int slotLeft = host.getParameterSlotLeft(slotIndex);
                int slotTop = host.getParameterSlotTop(slotIndex);
                int slotWidth = host.getParameterSlotWidth(slotIndex);
                int slotHeight = host.getParameterSlotHeight(slotIndex);
                if (!isPointInsideRect(anchorX, anchorY, slotLeft, slotTop, slotWidth, slotHeight)) {
                    continue;
                }
                double score = squaredDistanceToSlotCenter(anchorX, anchorY, slotLeft, slotTop, slotWidth, slotHeight);
                if (score < bestScore) {
                    bestScore = score;
                    best = new ParameterAttachmentCandidate(host, slotIndex);
                }
            }
        }
        return best;
    }

    private static boolean isPointInsideRect(int pointX, int pointY, int left, int top, int width, int height) {
        return pointX >= left && pointX <= left + width
            && pointY >= top && pointY <= top + height;
    }

    private static double squaredDistanceToSlotCenter(int pointX, int pointY, int left, int top, int width, int height) {
        double centerX = left + (width / 2.0);
        double centerY = top + (height / 2.0);
        double dx = pointX - centerX;
        double dy = pointY - centerY;
        return dx * dx + dy * dy;
    }

    private record ParameterAttachmentCandidate(Node host, int slotIndex) {
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
                paramData.setId(param.getId());
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
            if (node.getType() == NodeType.GOTO || node.getType() == NodeType.TRAVEL) {
                nodeData.setGotoAllowBreakWhileExecuting(node.isGotoAllowBreakWhileExecuting());
                nodeData.setGotoAllowPlaceWhileExecuting(node.isGotoAllowPlaceWhileExecuting());
            } else {
                nodeData.setGotoAllowBreakWhileExecuting(null);
                nodeData.setGotoAllowPlaceWhileExecuting(null);
            }
            if (node.getType() == NodeType.SENSOR_KEY_PRESSED) {
                nodeData.setKeyPressedActivatesInGuis(node.isKeyPressedActivatesInGuis());
            } else {
                nodeData.setKeyPressedActivatesInGuis(null);
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

    public static NodeGraphData.CustomNodeDefinition resolveCustomNodeDefinition(String presetName, List<Node> nodes,
                                                                                 List<NodeConnection> connections) {
        return buildCustomNodeDefinition(presetName, nodes == null ? List.of() : nodes,
            connections == null ? List.of() : connections, null);
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
        Map<String, NodeGraphData.CustomNodePort> initializedInputs = discoverInitializedCustomNodeInputs(nodes);
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
            NodeGraphData.CustomNodePort inferred = initializedInputs.get(normalized);
            if (inferred == null) {
                inferred = inferCustomNodeInputFromUsage(node, name.trim());
            }
            if (inferred == null) {
                inferred = new NodeGraphData.CustomNodePort(name.trim(), NodeType.PARAM_MESSAGE.name(), "");
            }
            mergeDiscoveredInput(inputs, normalized, inferred);
        }
        return new ArrayList<>(inputs.values());
    }

    private static Map<String, NodeGraphData.CustomNodePort> discoverInitializedCustomNodeInputs(List<Node> nodes) {
        Map<String, NodeGraphData.CustomNodePort> discovered = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.SET_VARIABLE) {
                continue;
            }
            Node variableNode = node.getAttachedParameter(0);
            Node valueNode = node.getAttachedParameter(1);
            if (variableNode == null || variableNode.getType() != NodeType.VARIABLE || valueNode == null || valueNode.getType() == NodeType.VARIABLE) {
                continue;
            }
            NodeParameter variableParam = variableNode.getParameter("Variable");
            String variableName = variableParam != null ? variableParam.getStringValue() : null;
            if (variableName == null || variableName.isBlank()) {
                continue;
            }
            NodeGraphData.CustomNodePort port = createCustomNodePortFromValueNode(variableName.trim(), valueNode);
            if (port != null) {
                discovered.put(variableName.trim().toLowerCase(Locale.ROOT), port);
            }
        }
        return discovered;
    }

    private static void mergeDiscoveredInput(Map<String, NodeGraphData.CustomNodePort> inputs, String normalizedKey,
                                             NodeGraphData.CustomNodePort candidate) {
        if (normalizedKey == null || normalizedKey.isBlank() || candidate == null) {
            return;
        }
        NodeGraphData.CustomNodePort existing = inputs.get(normalizedKey);
        if (existing == null) {
            inputs.put(normalizedKey, candidate);
            return;
        }
        if ((existing.getType() == null || existing.getType().isBlank())
            || NodeType.PARAM_MESSAGE.name().equals(existing.getType())) {
            existing.setType(candidate.getType());
        }
        if ((existing.getDefaultValue() == null || existing.getDefaultValue().isBlank())
            && candidate.getDefaultValue() != null && !candidate.getDefaultValue().isBlank()) {
            existing.setDefaultValue(candidate.getDefaultValue());
        }
    }

    private static NodeGraphData.CustomNodePort inferCustomNodeInputFromUsage(Node variableNode, String variableName) {
        if (variableNode == null || variableName == null || variableName.isBlank()) {
            return null;
        }
        Node host = variableNode.getParentParameterHost();
        if (host == null) {
            return new NodeGraphData.CustomNodePort(variableName, NodeType.PARAM_MESSAGE.name(), "");
        }
        int slotIndex = 0;
        for (Map.Entry<Integer, Node> entry : host.getAttachedParameters().entrySet()) {
            if (entry.getValue() == variableNode) {
                slotIndex = entry.getKey();
                break;
            }
        }
        NodeType inferredType = pickRepresentativeNodeType(NodeTraitRegistry.getAcceptedTraits(host.getType(), slotIndex));
        if (inferredType == null) {
            inferredType = NodeType.PARAM_MESSAGE;
        }
        return new NodeGraphData.CustomNodePort(variableName, inferredType.name(), "");
    }

    private static NodeGraphData.CustomNodePort createCustomNodePortFromValueNode(String variableName, Node valueNode) {
        if (variableName == null || variableName.isBlank() || valueNode == null || valueNode.getType() == null) {
            return null;
        }
        NodeType valueType = valueNode.getType();
        if (!isSupportedPresetInputNodeType(valueType)) {
            valueType = pickRepresentativeNodeType(NodeTraitRegistry.getProvidedTraits(valueType));
        }
        if (valueType == null) {
            valueType = NodeType.PARAM_MESSAGE;
        }
        String defaultValue = serializeCustomNodeInputDefaultValue(valueNode);
        return new NodeGraphData.CustomNodePort(variableName, valueType.name(), defaultValue);
    }

    private static String serializeCustomNodeInputDefaultValue(Node valueNode) {
        if (valueNode == null || valueNode.getType() == null) {
            return "";
        }
        return switch (valueNode.getType()) {
            case PARAM_COORDINATE -> joinCoordinateValues(valueNode);
            case PARAM_BLOCK -> getNodeParameterValue(valueNode, "Block");
            case PARAM_ITEM -> getNodeParameterValue(valueNode, "Item");
            case PARAM_VILLAGER_TRADE -> getNodeParameterValue(valueNode, "Item");
            case PARAM_ENTITY -> getNodeParameterValue(valueNode, "Entity");
            case PARAM_PLAYER -> getNodeParameterValue(valueNode, "Player");
            case PARAM_MESSAGE -> getNodeParameterValue(valueNode, "Text");
            case PARAM_WAYPOINT -> getNodeParameterValue(valueNode, "Waypoint");
            case PARAM_SCHEMATIC -> getNodeParameterValue(valueNode, "Schematic");
            case PARAM_INVENTORY_SLOT -> getNodeParameterValue(valueNode, "Slot");
            case PARAM_DURATION -> getNodeParameterValue(valueNode, "Duration");
            case PARAM_AMOUNT -> getNodeParameterValue(valueNode, "Amount");
            case PARAM_BOOLEAN -> {
                NodeParameter toggle = valueNode.getParameter("Toggle");
                yield toggle == null ? "false" : toggle.getStringValue();
            }
            case PARAM_HAND -> getNodeParameterValue(valueNode, "Hand");
            case PARAM_GUI -> getNodeParameterValue(valueNode, "GUI");
            case PARAM_KEY -> getNodeParameterValue(valueNode, "Key");
            case PARAM_MOUSE_BUTTON -> getNodeParameterValue(valueNode, "MouseButton");
            case PARAM_RANGE -> getNodeParameterValue(valueNode, "Range");
            case PARAM_DISTANCE -> getNodeParameterValue(valueNode, "Distance");
            case PARAM_DIRECTION -> getNodeParameterValue(valueNode, "Direction");
            case PARAM_BLOCK_FACE -> getNodeParameterValue(valueNode, "Face");
            case PARAM_ROTATION -> getNodeParameterValue(valueNode, "Yaw") + "," + getNodeParameterValue(valueNode, "Pitch");
            default -> "";
        };
    }

    private static String joinCoordinateValues(Node valueNode) {
        return getNodeParameterValue(valueNode, "X") + "," + getNodeParameterValue(valueNode, "Y") + "," + getNodeParameterValue(valueNode, "Z");
    }

    private static String getNodeParameterValue(Node node, String parameterName) {
        NodeParameter parameter = node == null ? null : node.getParameter(parameterName);
        return parameter == null || parameter.getStringValue() == null ? "" : parameter.getStringValue();
    }

    private static NodeType pickRepresentativeNodeType(java.util.Set<NodeValueTrait> traits) {
        if (traits == null || traits.isEmpty()) {
            return NodeType.PARAM_MESSAGE;
        }
        if (traits.contains(NodeValueTrait.DURATION)) {
            return NodeType.PARAM_DURATION;
        }
        if (traits.contains(NodeValueTrait.RANGE)) {
            return NodeType.PARAM_RANGE;
        }
        if (traits.contains(NodeValueTrait.DISTANCE)) {
            return NodeType.PARAM_DISTANCE;
        }
        if (traits.contains(NodeValueTrait.BOOLEAN)) {
            return NodeType.PARAM_BOOLEAN;
        }
        if (traits.contains(NodeValueTrait.BLOCK)) {
            return NodeType.PARAM_BLOCK;
        }
        if (traits.contains(NodeValueTrait.ITEM)) {
            return NodeType.PARAM_ITEM;
        }
        if (traits.contains(NodeValueTrait.COORDINATE)) {
            return NodeType.PARAM_COORDINATE;
        }
        if (traits.contains(NodeValueTrait.PLAYER)) {
            return NodeType.PARAM_PLAYER;
        }
        if (traits.contains(NodeValueTrait.ENTITY)) {
            return NodeType.PARAM_ENTITY;
        }
        if (traits.contains(NodeValueTrait.INVENTORY_SLOT)) {
            return NodeType.PARAM_INVENTORY_SLOT;
        }
        if (traits.contains(NodeValueTrait.KEY)) {
            return NodeType.PARAM_KEY;
        }
        if (traits.contains(NodeValueTrait.MOUSE_BUTTON)) {
            return NodeType.PARAM_MOUSE_BUTTON;
        }
        if (traits.contains(NodeValueTrait.HAND)) {
            return NodeType.PARAM_HAND;
        }
        if (traits.contains(NodeValueTrait.GUI)) {
            return NodeType.PARAM_GUI;
        }
        if (traits.contains(NodeValueTrait.WAYPOINT)) {
            return NodeType.PARAM_WAYPOINT;
        }
        if (traits.contains(NodeValueTrait.SCHEMATIC)) {
            return NodeType.PARAM_SCHEMATIC;
        }
        if (traits.contains(NodeValueTrait.VILLAGER_TRADE)) {
            return NodeType.PARAM_VILLAGER_TRADE;
        }
        if (traits.contains(NodeValueTrait.ROTATION)) {
            return NodeType.PARAM_ROTATION;
        }
        if (traits.contains(NodeValueTrait.DIRECTION)) {
            return NodeType.PARAM_DIRECTION;
        }
        if (traits.contains(NodeValueTrait.NUMBER)) {
            return NodeType.PARAM_AMOUNT;
        }
        return NodeType.PARAM_MESSAGE;
    }

    private static boolean isSupportedPresetInputNodeType(NodeType type) {
        return type == NodeType.PARAM_COORDINATE
            || type == NodeType.PARAM_BLOCK
            || type == NodeType.PARAM_ITEM
            || type == NodeType.PARAM_VILLAGER_TRADE
            || type == NodeType.PARAM_ENTITY
            || type == NodeType.PARAM_PLAYER
            || type == NodeType.PARAM_MESSAGE
            || type == NodeType.PARAM_WAYPOINT
            || type == NodeType.PARAM_SCHEMATIC
            || type == NodeType.PARAM_INVENTORY_SLOT
            || type == NodeType.PARAM_DURATION
            || type == NodeType.PARAM_AMOUNT
            || type == NodeType.PARAM_BOOLEAN
            || type == NodeType.PARAM_HAND
            || type == NodeType.PARAM_GUI
            || type == NodeType.PARAM_KEY
            || type == NodeType.PARAM_MOUSE_BUTTON
            || type == NodeType.PARAM_RANGE
            || type == NodeType.PARAM_DISTANCE
            || type == NodeType.PARAM_DIRECTION
            || type == NodeType.PARAM_BLOCK_FACE
            || type == NodeType.PARAM_ROTATION;
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
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save node graph: " + e.getMessage());
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
