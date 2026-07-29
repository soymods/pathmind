package com.pathmind.ui.graph;

import java.util.ArrayList;
import java.util.List;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;

/** Owns routine workspace identity, metadata, and validation context. */
final class RoutineWorkspaceController {

    interface Host {
        List<Node> nodes();
        void invalidateValidation();
        String liveRoutineParameterValue(Node node, String parameterName);
    }

    private final Host host;
    private List<NodeGraphData.RoutineDefinitionData> routineRegistry = new ArrayList<>();
    private List<NodeGraphData.RoutineDefinitionData> routineValidationRegistry = List.of();
    private String activeRoutineWorkspaceId = "";

    RoutineWorkspaceController(Host host) {
        this.host = host;
    }

    void setRoutineRegistry(List<NodeGraphData.RoutineDefinitionData> routines) {
        routineRegistry = routines;
    }

    List<NodeGraphData.RoutineDefinitionData> routineRegistry() {
        return routineRegistry;
    }

    List<NodeGraphData.RoutineDefinitionData> validationRoutines() {
        return routineValidationRegistry.isEmpty() ? routineRegistry : routineValidationRegistry;
    }

    void setActiveRoutineWorkspaceId(String routineId) {
        String resolved = routineId == null ? "" : routineId;
        if (!resolved.equals(activeRoutineWorkspaceId)) host.invalidateValidation();
        activeRoutineWorkspaceId = resolved;
    }

    String getActiveRoutineWorkspaceId() {
        return activeRoutineWorkspaceId;
    }

    List<NodeGraphData.RoutineDefinitionData> getRoutineDefinitions() {
        return List.copyOf(routineRegistry);
    }

    void setRoutineValidationContext(List<NodeGraphData.RoutineDefinitionData> routines) {
        List<NodeGraphData.RoutineDefinitionData> resolved =
            routines == null ? List.of() : List.copyOf(routines);
        if (!resolved.equals(routineValidationRegistry)) {
            routineValidationRegistry = resolved;
            host.invalidateValidation();
        }
    }

    /** Mirrors live routine card edits into sidebar metadata, including the current uncommitted text buffer. */
    void syncRoutineDefinitionMetadata(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null || routine.getId() == null
            || !routine.getId().equals(activeRoutineWorkspaceId)) {
            return;
        }
        for (Node node : host.nodes()) {
            if (node == null || !routine.getId().equals(node.getRoutineId())) continue;
            if (node.getType() == NodeType.ROUTINE_ENTRY) {
                String name = host.liveRoutineParameterValue(node, "Name");
                if (!name.isBlank()) routine.setName(name.trim());
                continue;
            }
            if (node.getType() != NodeType.ROUTINE_INPUT || node.getRoutineInputId().isBlank()) continue;
            NodeGraphData.RoutineInputData input = routine.getInputs().stream()
                .filter(candidate -> node.getRoutineInputId().equals(candidate.getId())).findFirst().orElse(null);
            if (input == null) continue;
            String label = host.liveRoutineParameterValue(node, "Label");
            if (!label.isBlank()) input.setLabel(label.trim());
            input.setValueKind(com.pathmind.routines.RoutineValueKind.fromSerialized(
                host.liveRoutineParameterValue(node, "ValueKind")).name());
            input.setDefaultValue(host.liveRoutineParameterValue(node, "Default"));
            input.setRequired(Boolean.parseBoolean(host.liveRoutineParameterValue(node, "Required")));
        }
        syncRoutineInvocations();
    }

    void syncRoutineInvocations() {
        if (activeRoutineWorkspaceId != null && !activeRoutineWorkspaceId.isBlank()) return;
        for (Node node : host.nodes()) {
            if (node == null || node.getType() != NodeType.ROUTINE_CALL) continue;
            routineRegistry.stream()
                .filter(routine -> node.getRoutineId().equals(routine.getId())).findFirst()
                .ifPresent(node::syncRoutineCallDefinition);
        }
    }
}
