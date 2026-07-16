package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.NodeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared, UI-independent routine management operations. */
public final class RoutineLifecycle {
    private RoutineLifecycle() {
    }

    public static List<NodeGraphData.RoutineDefinitionData> sorted(List<NodeGraphData.RoutineDefinitionData> routines) {
        ArrayList<NodeGraphData.RoutineDefinitionData> result = new ArrayList<>();
        if (routines != null) result.addAll(routines.stream().filter(java.util.Objects::nonNull).toList());
        result.sort(Comparator.comparing((NodeGraphData.RoutineDefinitionData routine) -> safe(routine.getName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(routine -> safe(routine.getId())));
        return result;
    }

    public static Map<String, Integer> usageCounts(NodeGraphData root) {
        Map<String, Integer> counts = new HashMap<>();
        if (root == null) return counts;
        for (NodeGraphData.RoutineDefinitionData routine : root.getRoutines()) {
            if (routine != null && routine.getId() != null) counts.put(routine.getId(), 0);
        }
        collectUsageCounts(root, counts);
        for (NodeGraphData.RoutineDefinitionData routine : root.getRoutines())
            if (routine != null) collectUsageCounts(routine.getGraph(), counts);
        return counts;
    }

    public static NodeGraphData.RoutineDefinitionData duplicate(NodeGraphData.RoutineDefinitionData source,
                                                                  List<NodeGraphData.RoutineDefinitionData> siblings) {
        if (source == null) return null;
        NodeGraphData.RoutineDefinitionData copy = new NodeGraphData.RoutineDefinitionData();
        String newRoutineId = UUID.randomUUID().toString();
        copy.setId(newRoutineId);
        copy.setName(uniqueCopyName(source.getName(), siblings));
        copy.setInterfaceVersion(1);
        copy.setImplementationRevision(1);

        Map<String, String> inputIds = new LinkedHashMap<>();
        for (NodeGraphData.RoutineInputData original : source.getInputs()) {
            if (original == null) continue;
            NodeGraphData.RoutineInputData input = new NodeGraphData.RoutineInputData();
            String newInputId = UUID.randomUUID().toString();
            inputIds.put(safe(original.getId()), newInputId);
            input.setId(newInputId);
            input.setLabel(original.getLabel());
            input.setValueKind(original.getValueKind());
            input.setAcceptedTraits(new ArrayList<>(original.getAcceptedTraits()));
            input.setRequired(original.getRequired());
            input.setDefaultValue(original.getDefaultValue());
            input.setOrder(original.getOrder());
            copy.getInputs().add(input);
        }

        NodeGraphData graph = source.getGraph() == null ? new NodeGraphData()
            : NodeGraphPersistence.parseNodeGraphData(NodeGraphPersistence.toPrettyJson(source.getGraph()));
        if (graph == null) graph = new NodeGraphData();
        Map<String, String> nodeIds = new HashMap<>();
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node != null) nodeIds.put(safe(node.getId()), UUID.randomUUID().toString());
        }
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null) continue;
            node.setId(nodeIds.get(safe(node.getId())));
            node.setAttachedSensorId(remap(node.getAttachedSensorId(), nodeIds));
            node.setParentControlId(remap(node.getParentControlId(), nodeIds));
            node.setAttachedActionId(remap(node.getAttachedActionId(), nodeIds));
            node.setParentActionControlId(remap(node.getParentActionControlId(), nodeIds));
            node.setAttachedParameterId(remap(node.getAttachedParameterId(), nodeIds));
            node.setParentParameterHostId(remap(node.getParentParameterHostId(), nodeIds));
            node.setRuntimeSourceNodeId(remap(node.getRuntimeSourceNodeId(), nodeIds));
            if (node.getParameterAttachments() != null) {
                for (NodeGraphData.ParameterAttachmentData attachment : node.getParameterAttachments()) {
                    if (attachment != null) attachment.setParameterNodeId(remap(attachment.getParameterNodeId(), nodeIds));
                }
            }
            if (node.getType() == NodeType.ROUTINE_ENTRY) {
                node.setRoutineId(newRoutineId);
                setParameter(node, "Name", copy.getName());
            } else if (node.getType() == NodeType.ROUTINE_INPUT && source.getId().equals(node.getRoutineId())) {
                node.setRoutineId(newRoutineId);
                node.setRoutineInputId(inputIds.getOrDefault(safe(node.getRoutineInputId()), UUID.randomUUID().toString()));
            } else if (node.getType() == NodeType.ROUTINE_CALL && source.getId().equals(node.getRoutineId())) {
                node.setRoutineId(newRoutineId);
                setParameter(node, "Name", copy.getName());
                for (NodeGraphData.RoutineArgumentData argument : node.getRoutineArguments()) {
                    if (argument != null) argument.setInputId(inputIds.getOrDefault(safe(argument.getInputId()), argument.getInputId()));
                }
                if (node.getParameterAttachments() != null) {
                    for (NodeGraphData.ParameterAttachmentData attachment : node.getParameterAttachments()) {
                        if (attachment != null) attachment.setRoutineInputId(
                            inputIds.getOrDefault(safe(attachment.getRoutineInputId()), attachment.getRoutineInputId()));
                    }
                }
            }
        }
        for (NodeGraphData.ConnectionData connection : graph.getConnections()) {
            if (connection == null) continue;
            connection.setOutputNodeId(remap(connection.getOutputNodeId(), nodeIds));
            connection.setInputNodeId(remap(connection.getInputNodeId(), nodeIds));
        }
        copy.setGraph(graph);
        new RoutineBuilderModel(copy).ensureDefinitionGraph();
        return copy;
    }

    /** Deletes a definition and every invocation of it from the preset and nested routine/template graphs. */
    public static boolean delete(NodeGraphData root, String routineId) {
        if (root == null || routineId == null || routineId.isBlank()) return false;
        boolean exists = root.getRoutines().stream()
            .anyMatch(routine -> routine != null && routineId.equals(routine.getId()));
        if (!exists) return false;
        removeRoutineCalls(root, routineId);
        for (NodeGraphData.RoutineDefinitionData routine : root.getRoutines()) {
            if (routine != null) removeRoutineCalls(routine.getGraph(), routineId);
        }
        root.getRoutines().removeIf(routine -> routine != null && routineId.equals(routine.getId()));
        NodeGraphPersistence.sanitizeRoutineDefinitions(root);
        return true;
    }

    private static void removeRoutineCalls(NodeGraphData graph, String routineId) {
        if (graph == null || graph.getNodes() == null) return;
        Set<String> removedIds = new HashSet<>();
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node != null && node.getType() == NodeType.ROUTINE_CALL && routineId.equals(node.getRoutineId())) {
                removedIds.add(node.getId());
            }
            if (node != null && node.getTemplateGraph() != null) removeRoutineCalls(node.getTemplateGraph(), routineId);
        }
        if (removedIds.isEmpty()) return;
        boolean changed;
        do {
            changed = false;
            for (NodeGraphData.NodeData node : graph.getNodes()) {
                if (node == null || removedIds.contains(node.getId())) continue;
                if (removedIds.contains(node.getParentControlId())
                    || removedIds.contains(node.getParentActionControlId())
                    || removedIds.contains(node.getParentParameterHostId())
                    || removedIds.contains(node.getRuntimeSourceNodeId())) {
                    changed |= removedIds.add(node.getId());
                }
            }
        } while (changed);
        graph.getNodes().removeIf(node -> node != null && removedIds.contains(node.getId()));
        if (graph.getConnections() != null) {
            graph.getConnections().removeIf(connection -> connection != null
                && (removedIds.contains(connection.getOutputNodeId()) || removedIds.contains(connection.getInputNodeId())));
        }
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node == null) continue;
            if (removedIds.contains(node.getAttachedSensorId())) node.setAttachedSensorId(null);
            if (removedIds.contains(node.getAttachedActionId())) node.setAttachedActionId(null);
            if (removedIds.contains(node.getAttachedParameterId())) node.setAttachedParameterId(null);
            if (node.getParameterAttachments() != null) {
                node.getParameterAttachments().removeIf(attachment -> attachment != null
                    && removedIds.contains(attachment.getParameterNodeId()));
            }
        }
    }

    private static void collectUsageCounts(NodeGraphData graph, Map<String, Integer> counts) {
        if (graph == null || graph.getNodes() == null) return;
        for (NodeGraphData.NodeData node : graph.getNodes()) {
            if (node != null && node.getType() == NodeType.ROUTINE_CALL && counts.containsKey(node.getRoutineId())) {
                counts.put(node.getRoutineId(), counts.get(node.getRoutineId()) + 1);
            }
        }
    }

    private static String uniqueCopyName(String sourceName, List<NodeGraphData.RoutineDefinitionData> siblings) {
        String base = safe(sourceName).isBlank() ? "Routine copy" : safe(sourceName) + " copy";
        String candidate = base;
        int suffix = 2;
        while (containsName(siblings, candidate)) candidate = base + " " + suffix++;
        return candidate;
    }

    private static boolean containsName(List<NodeGraphData.RoutineDefinitionData> routines, String name) {
        if (routines == null) return false;
        return routines.stream().filter(java.util.Objects::nonNull)
            .anyMatch(routine -> safe(routine.getName()).equalsIgnoreCase(name));
    }

    private static void setParameter(NodeGraphData.NodeData node, String name, String value) {
        if (node.getParameters() == null) return;
        String id = com.pathmind.nodes.NodeParameter.createDefaultId(name);
        node.getParameters().stream().filter(parameter -> parameter != null
            && (name.equals(parameter.getName()) || id.equals(parameter.getId())))
            .findFirst().ifPresent(parameter -> parameter.setValue(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String remap(String value, Map<String, String> ids) {
        return value == null ? null : ids.getOrDefault(value, value);
    }
}
