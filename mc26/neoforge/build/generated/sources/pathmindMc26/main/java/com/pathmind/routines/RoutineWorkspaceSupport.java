package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.NodeType;

/** Shared metadata synchronization for preset and library routine workspaces. */
public final class RoutineWorkspaceSupport {
    private RoutineWorkspaceSupport() {
    }

    public static void syncMetadata(NodeGraphData.RoutineDefinitionData routine, NodeGraphData graph) {
        if (routine == null || graph == null) return;
        for (NodeGraphData.NodeData nodeData : graph.getNodes()) {
            if (nodeData != null && nodeData.getType() == NodeType.ROUTINE_ENTRY) {
                for (NodeGraphData.ParameterData parameter : nodeData.getParameters()) {
                    if (parameter != null && "Name".equals(parameter.getName())
                        && parameter.getValue() != null && !parameter.getValue().isBlank()) {
                        routine.setName(parameter.getValue().trim());
                    }
                }
            }
            if (nodeData == null || nodeData.getType() != NodeType.ROUTINE_INPUT
                || !routine.getId().equals(nodeData.getRoutineId())) continue;
            NodeGraphData.RoutineInputData input = routine.getInputs().stream()
                .filter(candidate -> candidate.getId().equals(nodeData.getRoutineInputId())).findFirst().orElse(null);
            if (input == null) continue;
            for (NodeGraphData.ParameterData parameter : nodeData.getParameters()) {
                if (parameter == null || parameter.getValue() == null) continue;
                if ("Label".equals(parameter.getName()) && !parameter.getValue().isBlank()) input.setLabel(parameter.getValue().trim());
                if ("valuekind".equals(parameter.getId())) input.setValueKind(RoutineValueKind.fromSerialized(parameter.getValue()).name());
                if ("Default".equals(parameter.getName())) input.setDefaultValue(parameter.getValue());
                if ("Required".equals(parameter.getName())) input.setRequired(Boolean.parseBoolean(parameter.getValue()));
            }
        }
    }
}
