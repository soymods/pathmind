package com.pathmind.nodes;

import com.pathmind.data.NodeGraphData;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.routines.RoutineInputDefinition;
import com.pathmind.routines.RoutineValueKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NodeRoutineMetadata {
    private final Node owner;
    private String routineId;
    private String routineInputId;
    private final List<NodeGraphData.RoutineArgumentData> routineArguments;

    NodeRoutineMetadata(Node owner) {
        this.owner = owner;
        this.routineId = "";
        this.routineInputId = "";
        this.routineArguments = new ArrayList<>();
    }

    String getRoutineId() {
        return routineId == null ? "" : routineId;
    }

    String getRoutineInputId() {
        return routineInputId == null ? "" : routineInputId;
    }

    void setRoutineIdentity(String routineId, String inputId) {
        this.routineId = routineId == null ? "" : routineId;
        this.routineInputId = inputId == null ? "" : inputId;
    }

    static Node createRoutineEntry(String routineId, String label, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_ENTRY, x, y);
        node.setRoutineIdentity(routineId, "");
        node.getParameter("Name").setStringValue(label == null ? "Routine" : label);
        node.recalculateDimensions();
        return node;
    }

    static Node createRoutineInput(String routineId, RoutineInputDefinition input, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_INPUT, x, y);
        node.setRoutineIdentity(routineId, input == null ? "" : input.getId());
        if (input != null) {
            node.getParameter("Label").setStringValue(input.getLabel());
            node.getParameter("ValueKind").setStringValue(input.getValueKind().name());
            node.getParameter("Default").setStringValue(input.getDefaultValue());
            node.getParameter("Required").setStringValue(Boolean.toString(input.isRequired()));
        }
        node.recalculateDimensions();
        return node;
    }

    static Node createRoutineCall(String routineId, String name, int x, int y) {
        Node node = new Node(NodeType.ROUTINE_CALL, x, y);
        node.setRoutineIdentity(routineId, "");
        node.getParameter("Name").setStringValue(name == null || name.isBlank() ? "Routine" : name.trim());
        node.recalculateDimensions();
        return node;
    }

    static Node createRoutineCall(NodeGraphData.RoutineDefinitionData routine, int x, int y) {
        Node node = createRoutineCall(routine == null ? "" : routine.getId(), routine == null ? "Routine" : routine.getName(), x, y);
        node.syncRoutineCallDefinition(routine);
        return node;
    }

    void setRoutineArguments(List<NodeGraphData.RoutineArgumentData> arguments) {
        routineArguments.clear();
        if (arguments != null) {
            for (NodeGraphData.RoutineArgumentData argument : arguments) {
                if (argument != null && argument.getInputId() != null && !argument.getInputId().isBlank()) {
                    routineArguments.add(copyRoutineArgument(argument));
                }
            }
        }
        owner.recalculateDimensions();
    }

    List<NodeGraphData.RoutineArgumentData> getRoutineArguments() {
        return routineArguments.stream().map(NodeRoutineMetadata::copyRoutineArgument).toList();
    }

    /** Refreshes the public signature while preserving bindings for inputs that still exist. */
    void syncRoutineCallDefinition(NodeGraphData.RoutineDefinitionData routine) {
        if (owner.getType() != NodeType.ROUTINE_CALL || routine == null || !getRoutineId().equals(routine.getId())) return;
        Map<String, Node> boundArguments = new LinkedHashMap<>();
        for (Map.Entry<Integer, Node> binding : new ArrayList<>(owner.getAttachedParameters().entrySet())) {
            String inputId = getRoutineInputIdForSlot(binding.getKey());
            if (!inputId.isBlank() && binding.getValue() != null) boundArguments.put(inputId, binding.getValue());
            owner.getAttachments().detachParameter(binding.getKey());
        }
        NodeParameter name = owner.getParameter("Name");
        if (name != null) name.setStringValue(routine.getName() == null ? "Routine" : routine.getName());
        ArrayList<NodeGraphData.RoutineInputData> inputs = new ArrayList<>(routine.getInputs());
        inputs.sort(Comparator.comparingInt(input -> input.getOrder() == null ? Integer.MAX_VALUE : input.getOrder()));
        routineArguments.clear();
        for (NodeGraphData.RoutineInputData input : inputs) {
            if (input == null || input.getId() == null || input.getId().isBlank()) continue;
            NodeGraphData.RoutineArgumentData argument = new NodeGraphData.RoutineArgumentData();
            argument.setInputId(input.getId());
            argument.setLabel(input.getLabel());
            argument.setValueKind(input.getValueKind());
            argument.setRequired(input.getRequired());
            argument.setDefaultValue(input.getDefaultValue());
            argument.setOrphaned(false);
            routineArguments.add(argument);
        }
        for (Map.Entry<String, Node> binding : boundArguments.entrySet()) {
            int slot = getRoutineSlotForInputId(binding.getKey());
            if (slot < 0) continue;
            owner.getAttachments().attachParameter(owner, slot, binding.getValue());
            binding.getValue().setSocketsHidden(true);
            binding.getValue().setDragging(false);
        }
        owner.recalculateDimensions();
    }

    String getRoutineInputIdForSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size() ? routineArguments.get(slotIndex).getInputId() : "";
    }

    int getRoutineSlotForInputId(String inputId) {
        if (inputId == null || inputId.isBlank()) return -1;
        for (int i = 0; i < routineArguments.size(); i++) if (inputId.equals(routineArguments.get(i).getInputId())) return i;
        return -1;
    }

    boolean isRoutineArgumentOrphaned(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size()
            && Boolean.TRUE.equals(routineArguments.get(slotIndex).getOrphaned());
    }

    String getRoutineArgumentDefaultValue(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= routineArguments.size()) return "";
        String value = routineArguments.get(slotIndex).getDefaultValue();
        return value == null ? "" : value;
    }

    String getRoutineArgumentValueKind(int slotIndex) {
        return slotIndex >= 0 && slotIndex < routineArguments.size()
            ? RoutineValueKind.fromSerialized(routineArguments.get(slotIndex).getValueKind()).name() : RoutineValueKind.ANY.name();
    }

    EnumSet<NodeValueTrait> getAcceptedTraitsForParameterSlot(int slotIndex) {
        if (owner.getType() == NodeType.ROUTINE_CALL && slotIndex >= 0 && slotIndex < routineArguments.size()) {
            return EnumSet.of(NodeValueTrait.ANY);
        }
        return NodeTraitRegistry.getAcceptedTraits(owner.getType(), slotIndex);
    }

    int getRoutineArgumentCount() {
        return routineArguments.size();
    }

    boolean hasRoutineArguments() {
        return !routineArguments.isEmpty();
    }

    NodeGraphData.RoutineArgumentData getRoutineArgument(int slotIndex) {
        return routineArguments.get(slotIndex);
    }

    ExecutionManager.RuntimeVariable captureAttachedRuntimeValue(int slotIndex, int executionId) {
        Node valueNode = owner.getAttachedParameter(slotIndex);
        if (valueNode == null) return null;
        if (valueNode.getType() == NodeType.VARIABLE) {
            valueNode = owner.resolveVariableValueNode(valueNode, slotIndex, null);
            if (valueNode == null) return null;
        } else if (valueNode.getType() == NodeType.ROUTINE_INPUT) {
            ExecutionManager.RuntimeVariable framed = ExecutionManager.getInstance()
                .getRoutineInputValue(executionId, valueNode.getRoutineInputId());
            if (framed != null) return framed;
        }
        if (valueNode.isSensorNode() && NodeCatalog.isBooleanSensor(valueNode.getType())) {
            String value = Boolean.toString(valueNode.evaluateSensor());
            Map<String, String> values = new HashMap<>();
            values.put("Toggle", value);
            values.put(Node.normalizeParameterKey("Toggle"), value);
            return new ExecutionManager.RuntimeVariable(NodeType.PARAM_BOOLEAN, values);
        }
        NodeType valueType = valueNode.getResolvedValueType();
        if (valueType == null || valueType == NodeType.ROUTINE_INPUT) valueType = valueNode.getType();
        return new ExecutionManager.RuntimeVariable(valueType, valueNode.exportParameterValues());
    }

    private static NodeGraphData.RoutineArgumentData copyRoutineArgument(NodeGraphData.RoutineArgumentData source) {
        NodeGraphData.RoutineArgumentData copy = new NodeGraphData.RoutineArgumentData();
        copy.setInputId(source.getInputId());
        copy.setLabel(source.getLabel());
        copy.setValueKind(source.getValueKind());
        copy.setRequired(source.getRequired());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setOrphaned(source.getOrphaned());
        return copy;
    }
}
