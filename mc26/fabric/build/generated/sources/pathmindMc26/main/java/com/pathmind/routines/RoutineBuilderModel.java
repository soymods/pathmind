package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Mutable state used by the Scratch-style routine builder UI. */
public final class RoutineBuilderModel {
    private final NodeGraphData.RoutineDefinitionData routine;
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();

    public RoutineBuilderModel(NodeGraphData.RoutineDefinitionData routine) {
        this.routine = routine == null ? createRoutine("Routine") : routine;
        normalizeOrder();
    }

    public static NodeGraphData.RoutineDefinitionData createRoutine(String name) {
        NodeGraphData.RoutineDefinitionData routine = new NodeGraphData.RoutineDefinitionData();
        routine.setId(UUID.randomUUID().toString());
        routine.setName(clean(name, "Routine"));
        routine.setInterfaceVersion(1);
        routine.setImplementationRevision(1);
        NodeGraphData graph = NodeGraphPersistence.createGraphData(
            List.of(Node.createRoutineEntry(routine.getId(), routine.getName(), 220, 160)), List.of());
        routine.setGraph(graph);
        return routine;
    }

    public NodeGraphData.RoutineDefinitionData getRoutine() { return routine; }
    public List<NodeGraphData.RoutineInputData> getInputs() { return List.copyOf(routine.getInputs()); }
    public List<RoutineValueKind> getSupportedKinds() { return List.of(RoutineValueKind.values()); }

    public String getPreviewLabel() {
        StringBuilder label = new StringBuilder(clean(routine.getName(), "Routine"));
        for (NodeGraphData.RoutineInputData input : orderedInputs()) {
            label.append(" [").append(clean(input.getLabel(), "Input")).append(']');
        }
        return label.toString();
    }

    public void renameRoutine(String name) {
        checkpoint();
        routine.setName(clean(name, "Routine"));
        syncDefinitionNodes();
    }

    public NodeGraphData.RoutineInputData addInput(String label, RoutineValueKind kind) {
        checkpoint();
        NodeGraphData.RoutineInputData input = new NodeGraphData.RoutineInputData();
        input.setId(UUID.randomUUID().toString());
        input.setLabel(clean(label, "Input"));
        RoutineValueKind resolved = kind == null ? RoutineValueKind.ANY : kind;
        input.setValueKind(resolved.name());
        input.setAcceptedTraits(resolved.getDefaultTraits().stream().map(Enum::name).toList());
        input.setRequired(false);
        input.setDefaultValue("");
        input.setOrder(routine.getInputs().size());
        routine.getInputs().add(input);
        return input;
    }

    public void removeInput(String inputId) {
        checkpoint();
        routine.getInputs().removeIf(input -> inputId != null && inputId.equals(input.getId()));
        normalizeOrder();
        if (routine.getGraph() != null && routine.getGraph().getNodes() != null) {
            routine.getGraph().getNodes().removeIf(node -> node != null && node.getType() == NodeType.ROUTINE_INPUT
                && inputId.equals(node.getRoutineInputId()));
        }
    }

    public void updateInput(String inputId, String label, RoutineValueKind kind, boolean required, String defaultValue) {
        NodeGraphData.RoutineInputData input = findInput(inputId);
        if (input == null) return;
        checkpoint();
        RoutineValueKind resolved = kind == null ? RoutineValueKind.ANY : kind;
        input.setLabel(clean(label, "Input"));
        input.setValueKind(resolved.name());
        input.setAcceptedTraits(resolved.getDefaultTraits().stream().map(Enum::name).toList());
        input.setRequired(required);
        input.setDefaultValue(defaultValue == null ? "" : defaultValue);
        syncDefinitionNodes();
    }

    public void moveInput(String inputId, int direction) {
        List<NodeGraphData.RoutineInputData> ordered = orderedInputs();
        int current = -1;
        for (int i = 0; i < ordered.size(); i++) if (inputId.equals(ordered.get(i).getId())) current = i;
        int target = Math.max(0, Math.min(ordered.size() - 1, current + Integer.signum(direction)));
        if (current < 0 || current == target) return;
        checkpoint();
        java.util.Collections.swap(ordered, current, target);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setOrder(i);
        routine.setInputs(ordered);
        normalizeOrder();
    }

    public void ensureDefinitionGraph() {
        if (routine.getGraph() == null) routine.setGraph(new NodeGraphData());
        List<NodeGraphData.NodeData> nodes = routine.getGraph().getNodes();
        nodes.removeIf(node -> node != null && node.getType() == NodeType.START);
        nodes.removeIf(node -> node != null && node.getType() == NodeType.ROUTINE_INPUT
            && (!routine.getId().equals(node.getRoutineId()) || findInput(node.getRoutineInputId()) == null));
        NodeGraphData.NodeData entry = nodes.stream().filter(node -> node != null && node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElse(null);
        if (entry == null) {
            NodeGraphData entryGraph = NodeGraphPersistence.createGraphData(
                List.of(Node.createRoutineEntry(routine.getId(), routine.getName(), 220, 160)), List.of());
            nodes.add(0, entryGraph.getNodes().get(0));
        }
        boolean seen = false;
        for (java.util.Iterator<NodeGraphData.NodeData> it = nodes.iterator(); it.hasNext();) {
            NodeGraphData.NodeData node = it.next();
            if (node != null && node.getType() == NodeType.ROUTINE_ENTRY) {
                if (seen) it.remove();
                seen = true;
            }
        }
        syncDefinitionNodes();
    }

    public Node createInputReporter(String inputId, int x, int y) {
        NodeGraphData.RoutineInputData data = findInput(inputId);
        if (data == null) return null;
        RoutineInputDefinition input = new RoutineInputDefinition(data.getId(), data.getLabel(),
            RoutineValueKind.fromSerialized(data.getValueKind()), null,
            Boolean.TRUE.equals(data.getRequired()), data.getDefaultValue(), data.getOrder() == null ? 0 : data.getOrder());
        return Node.createRoutineInput(routine.getId(), input, x, y);
    }

    public boolean ownsReporter(Node node) {
        return node != null && node.getType() == NodeType.ROUTINE_INPUT && routine.getId().equals(node.getRoutineId())
            && findInput(node.getRoutineInputId()) != null;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void undo() { if (!undo.isEmpty()) { redo.push(snapshot()); restore(undo.pop()); } }
    public void redo() { if (!redo.isEmpty()) { undo.push(snapshot()); restore(redo.pop()); } }

    private void checkpoint() { undo.push(snapshot()); redo.clear(); }
    private State snapshot() {
        List<InputState> inputs = orderedInputs().stream().map(InputState::from).toList();
        return new State(routine.getName(), inputs);
    }
    private void restore(State state) {
        routine.setName(state.name());
        ArrayList<NodeGraphData.RoutineInputData> inputs = new ArrayList<>();
        for (InputState input : state.inputs()) inputs.add(input.toData());
        routine.setInputs(inputs);
        syncDefinitionNodes();
    }

    private void syncDefinitionNodes() {
        if (routine.getGraph() == null || routine.getGraph().getNodes() == null) return;
        for (NodeGraphData.NodeData node : routine.getGraph().getNodes()) {
            if (node == null) continue;
            if (node.getType() == NodeType.ROUTINE_ENTRY) setParameter(node, "Name", routine.getName());
            if (node.getType() == NodeType.ROUTINE_INPUT) {
                NodeGraphData.RoutineInputData input = findInput(node.getRoutineInputId());
                if (input != null) {
                    setParameter(node, "Label", input.getLabel());
                    setParameter(node, "ValueKind", input.getValueKind());
                    setParameter(node, "Default", input.getDefaultValue());
                    setParameter(node, "Required", Boolean.toString(Boolean.TRUE.equals(input.getRequired())));
                }
            }
        }
    }

    private NodeGraphData.RoutineInputData findInput(String id) {
        if (id == null) return null;
        return routine.getInputs().stream().filter(input -> id.equals(input.getId())).findFirst().orElse(null);
    }
    private List<NodeGraphData.RoutineInputData> orderedInputs() {
        ArrayList<NodeGraphData.RoutineInputData> ordered = new ArrayList<>(routine.getInputs());
        ordered.sort(Comparator.comparingInt(input -> input.getOrder() == null ? Integer.MAX_VALUE : input.getOrder()));
        return ordered;
    }
    private void normalizeOrder() {
        List<NodeGraphData.RoutineInputData> ordered = orderedInputs();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setOrder(i);
        routine.setInputs(ordered);
    }
    private static String clean(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String parameter(NodeGraphData.NodeData node, String name) {
        if (node.getParameters() == null) return "";
        String id = com.pathmind.nodes.NodeParameter.createDefaultId(name);
        return node.getParameters().stream().filter(p -> p != null && (name.equals(p.getName()) || id.equals(p.getId())))
            .map(NodeGraphData.ParameterData::getValue).findFirst().orElse("");
    }
    private static void setParameter(NodeGraphData.NodeData node, String name, String value) {
        if (node.getParameters() == null) return;
        String id = com.pathmind.nodes.NodeParameter.createDefaultId(name);
        node.getParameters().stream().filter(p -> p != null && (name.equals(p.getName()) || id.equals(p.getId())))
            .findFirst().ifPresent(p -> p.setValue(value));
    }

    private record State(String name, List<InputState> inputs) {}
    private record InputState(String id, String label, String kind, List<String> traits, boolean required, String defaultValue, int order) {
        static InputState from(NodeGraphData.RoutineInputData input) {
            return new InputState(input.getId(), input.getLabel(), input.getValueKind(), List.copyOf(input.getAcceptedTraits()),
                Boolean.TRUE.equals(input.getRequired()), input.getDefaultValue(), input.getOrder() == null ? 0 : input.getOrder());
        }
        NodeGraphData.RoutineInputData toData() {
            NodeGraphData.RoutineInputData input = new NodeGraphData.RoutineInputData();
            input.setId(id); input.setLabel(label); input.setValueKind(kind); input.setAcceptedTraits(new ArrayList<>(traits));
            input.setRequired(required); input.setDefaultValue(defaultValue); input.setOrder(order); return input;
        }
    }
}
