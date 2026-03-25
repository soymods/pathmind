package com.pathmind.validation;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeTraitRegistry;
import com.pathmind.nodes.NodeType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GraphValidator {
    private GraphValidator() {
    }

    public static GraphValidationResult validate(Collection<Node> nodes, Collection<NodeConnection> connections, String activePreset,
                                                 boolean baritoneAvailable, boolean uiUtilsAvailable) {
        List<Node> safeNodes = nodes == null ? List.of() : new ArrayList<>(nodes);
        List<NodeConnection> safeConnections = connections == null ? List.of() : new ArrayList<>(connections);
        List<GraphValidationIssue> issues = new ArrayList<>();

        Map<String, Node> nodeById = new LinkedHashMap<>();
        Map<String, List<NodeConnection>> outgoingById = new HashMap<>();
        Map<String, Integer> inputOccupancy = new HashMap<>();
        List<Node> startNodes = new ArrayList<>();
        Map<String, List<Node>> functionNodesByName = new LinkedHashMap<>();
        List<String> availablePresets = PresetManager.getAvailablePresets();

        for (Node node : safeNodes) {
            if (node == null) {
                continue;
            }
            nodeById.put(node.getId(), node);
            outgoingById.computeIfAbsent(node.getId(), ignored -> new ArrayList<>());
            if (node.getType() == NodeType.START) {
                startNodes.add(node);
            }
            if (node.getType() == NodeType.EVENT_FUNCTION) {
                String name = normalize(getParameterValue(node, "Name"));
                if (!name.isEmpty()) {
                    functionNodesByName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(node);
                }
            }
        }

        for (NodeConnection connection : safeConnections) {
            if (connection == null || connection.getOutputNode() == null || connection.getInputNode() == null) {
                continue;
            }
            outgoingById.computeIfAbsent(connection.getOutputNode().getId(), ignored -> new ArrayList<>()).add(connection);
            String inputKey = connection.getInputNode().getId() + "#" + connection.getInputSocket();
            inputOccupancy.put(inputKey, inputOccupancy.getOrDefault(inputKey, 0) + 1);
        }

        if (startNodes.isEmpty()) {
            issues.add(new GraphValidationIssue(
                GraphValidationSeverity.ERROR,
                "missing_start",
                "Add at least one START node before running the workspace.",
                null
            ));
        }

        Set<String> reachableNodeIds = collectReachableNodeIds(startNodes, outgoingById, functionNodesByName);

        for (Node node : safeNodes) {
            if (node == null) {
                continue;
            }
            NodeType type = node.getType();

            if (type.requiresBaritone() && !baritoneAvailable) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_baritone",
                    type.getDisplayName() + " requires Baritone, but Baritone is not available.", node));
            }
            if (type.requiresUiUtils() && !uiUtilsAvailable) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_ui_utils",
                    type.getDisplayName() + " requires UI Utils, but UI Utils is not available.", node));
            }

            if ((type == NodeType.START || type == NodeType.EVENT_FUNCTION)
                && outgoingById.getOrDefault(node.getId(), List.of()).isEmpty()) {
                issues.add(issue(GraphValidationSeverity.WARNING, "dead_entry",
                    type.getDisplayName() + " does not connect to any executable node.", node));
            }

            if (!isEntryOrAttached(node) && !reachableNodeIds.contains(node.getId())) {
                issues.add(issue(GraphValidationSeverity.WARNING, "unreachable_node",
                    type.getDisplayName() + " is unreachable from any START chain.", node));
            }

            validateInputConnections(node, inputOccupancy, issues);
            validateRequiredParameterSlots(node, issues);
            validateNamedNodes(node, functionNodesByName, availablePresets, activePreset, issues);
        }

        for (Map.Entry<String, List<Node>> entry : functionNodesByName.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            for (Node node : entry.getValue()) {
                issues.add(issue(GraphValidationSeverity.ERROR, "duplicate_event_function",
                    "Function name \"" + displayValue(entry.getKey()) + "\" is defined more than once.", node));
            }
        }

        issues.sort(
            Comparator.comparing((GraphValidationIssue issue) -> issue.getSeverity() == GraphValidationSeverity.ERROR ? 0 : 1)
                .thenComparing(GraphValidationIssue::getMessage, String.CASE_INSENSITIVE_ORDER)
        );
        return new GraphValidationResult(issues);
    }

    private static void validateInputConnections(Node node, Map<String, Integer> inputOccupancy, List<GraphValidationIssue> issues) {
        if (node.getInputSocketCount() <= 0) {
            return;
        }
        for (int socketIndex = 0; socketIndex < node.getInputSocketCount(); socketIndex++) {
            String key = node.getId() + "#" + socketIndex;
            int count = inputOccupancy.getOrDefault(key, 0);
            if (count > 1) {
                issues.add(issue(GraphValidationSeverity.ERROR, "multiple_inputs",
                    node.getType().getDisplayName() + " has multiple connections into the same input.", node));
            }
        }
    }

    private static void validateRequiredParameterSlots(Node node, List<GraphValidationIssue> issues) {
        if (!node.canAcceptParameter()) {
            return;
        }
        int slotCount = node.getParameterSlotCount();
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (!NodeTraitRegistry.isParameterSlotAlwaysRequired(node.getType(), slotIndex)) {
                continue;
            }
            if (node.getAttachedParameter(slotIndex) != null) {
                continue;
            }
            issues.add(issue(GraphValidationSeverity.ERROR, "missing_parameter_slot",
                node.getType().getDisplayName() + " is missing a required parameter input.", node));
        }
    }

    private static void validateNamedNodes(Node node, Map<String, List<Node>> functionNodesByName, List<String> availablePresets,
                                           String activePreset, List<GraphValidationIssue> issues) {
        NodeType type = node.getType();
        if (type == NodeType.EVENT_FUNCTION) {
            String name = normalize(getParameterValue(node, "Name"));
            if (name.isEmpty()) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_event_function_name",
                    "Event Function is missing a function name.", node));
            }
            return;
        }

        if (type == NodeType.EVENT_CALL) {
            String name = normalize(getParameterValue(node, "Name"));
            if (name.isEmpty()) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_event_call_name",
                    "Call Function is missing a function name.", node));
            } else if (!functionNodesByName.containsKey(name)) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_event_target",
                    "Call Function references \"" + displayValue(name) + "\", but no matching Event Function exists.", node));
            }
            return;
        }

        if (type == NodeType.RUN_PRESET) {
            String preset = normalize(getParameterValue(node, "Preset"));
            if (preset.isEmpty()) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_preset_target",
                    "Run Preset is missing a preset name.", node));
            } else if (!containsIgnoreCase(availablePresets, preset)) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_preset",
                    "Run Preset references \"" + displayValue(preset) + "\", but that preset was not found.", node));
            }
            return;
        }

        if (type == NodeType.TEMPLATE) {
            String preset = normalize(getParameterValue(node, "Preset"));
            String targetPreset = preset.isEmpty() ? normalize(activePreset) : preset;
            if (!targetPreset.isEmpty() && !containsIgnoreCase(availablePresets, targetPreset)) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_template_preset",
                    "Template references preset \"" + displayValue(targetPreset) + "\", but that preset was not found.", node));
            }
            if (node.getTemplateGraphData() == null) {
                issues.add(issue(GraphValidationSeverity.WARNING, "missing_template_graph",
                    "Template has no saved internal graph yet.", node));
            }
            return;
        }

        if (type == NodeType.STOP_CHAIN || type == NodeType.START_CHAIN) {
            String target = normalize(getParameterValue(node, "StartNumber"));
            if (target.isEmpty()) {
                issues.add(issue(GraphValidationSeverity.ERROR, "missing_start_target",
                    type.getDisplayName() + " is missing a START target.", node));
            }
        }
    }

    private static Set<String> collectReachableNodeIds(List<Node> startNodes, Map<String, List<NodeConnection>> outgoingById,
                                                       Map<String, List<Node>> functionNodesByName) {
        Set<String> visited = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>(startNodes);
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node == null || !visited.add(node.getId())) {
                continue;
            }
            if (node.getAttachedSensor() != null) {
                queue.addLast(node.getAttachedSensor());
            }
            if (node.getAttachedActionNode() != null) {
                queue.addLast(node.getAttachedActionNode());
            }
            for (Node parameterNode : node.getAttachedParameters().values()) {
                if (parameterNode != null) {
                    queue.addLast(parameterNode);
                }
            }
            for (NodeConnection connection : outgoingById.getOrDefault(node.getId(), List.of())) {
                if (connection != null) {
                    queue.addLast(connection.getInputNode());
                }
            }
            if (node.getType() == NodeType.EVENT_CALL) {
                String functionName = normalize(getParameterValue(node, "Name"));
                if (!functionName.isEmpty()) {
                    for (Node functionNode : functionNodesByName.getOrDefault(functionName, List.of())) {
                        if (functionNode != null) {
                            queue.addLast(functionNode);
                        }
                    }
                }
            }
        }
        return visited;
    }

    private static boolean isEntryOrAttached(Node node) {
        if (node == null) {
            return true;
        }
        return node.getType() == NodeType.START
            || node.getType() == NodeType.EVENT_FUNCTION
            || node.getParentControl() != null
            || node.getParentActionControl() != null
            || node.getParentParameterHost() != null;
    }

    private static String getParameterValue(Node node, String parameterName) {
        if (node == null || parameterName == null) {
            return "";
        }
        NodeParameter parameter = node.getParameter(parameterName);
        return parameter == null ? "" : parameter.getStringValue();
    }

    private static GraphValidationIssue issue(GraphValidationSeverity severity, String code, String message, Node node) {
        return new GraphValidationIssue(severity, code, message, node != null ? node.getId() : null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }
}
