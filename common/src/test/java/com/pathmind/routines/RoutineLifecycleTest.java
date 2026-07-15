package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutineLifecycleTest {
    @Test
    void duplicateGetsIndependentRoutineAndInputIdsAndRetargetsSelfCalls() {
        NodeGraphData.RoutineDefinitionData source = RoutineBuilderModel.createRoutine("Walk");
        NodeGraphData.RoutineInputData input = new RoutineBuilderModel(source).addInput("steps", RoutineValueKind.NUMBER);
        Node selfCall = Node.createRoutineCall(source, 300, 200);
        Node entry = Node.createRoutineEntry(source.getId(), source.getName(), 0, 0);
        source.setGraph(NodeGraphPersistence.createGraphData(List.of(entry, selfCall),
            List.of(new NodeConnection(entry, selfCall, 0, 0))));

        NodeGraphData.RoutineDefinitionData copy = RoutineLifecycle.duplicate(source, List.of(source));

        assertEquals("Walk copy", copy.getName());
        assertNotEquals(source.getId(), copy.getId());
        assertNotEquals(input.getId(), copy.getInputs().get(0).getId());
        assertTrue(copy.getGraph().getNodes().stream().anyMatch(node -> node.getType() == com.pathmind.nodes.NodeType.ROUTINE_CALL
            && copy.getId().equals(node.getRoutineId())));
        assertEquals(1, copy.getGraph().getConnections().size());
        assertTrue(copy.getGraph().getNodes().stream().anyMatch(node ->
            node.getId().equals(copy.getGraph().getConnections().get(0).getOutputNodeId())));
    }

    @Test
    void deleteRemovesDefinitionAndAllMainAndNestedUsages() {
        NodeGraphData root = new NodeGraphData();
        NodeGraphData.RoutineDefinitionData target = RoutineBuilderModel.createRoutine("Target");
        NodeGraphData.RoutineDefinitionData owner = RoutineBuilderModel.createRoutine("Owner");
        owner.setGraph(NodeGraphPersistence.createGraphData(List.of(Node.createRoutineCall(target, 0, 0)), List.of()));
        Node start = new Node(com.pathmind.nodes.NodeType.START, 0, 0);
        Node mainCall = Node.createRoutineCall(target, 100, 0);
        NodeGraphData mainGraph = NodeGraphPersistence.createGraphData(
            List.of(start, mainCall), List.of(new NodeConnection(start, mainCall, 0, 0)));
        root.setNodes(mainGraph.getNodes());
        root.setConnections(mainGraph.getConnections());
        root.getRoutines().add(target);
        root.getRoutines().add(owner);

        assertTrue(RoutineLifecycle.delete(root, target.getId()));
        assertTrue(root.getRoutines().stream().noneMatch(routine -> target.getId().equals(routine.getId())));
        assertTrue(root.getNodes().stream().noneMatch(node -> target.getId().equals(node.getRoutineId())));
        assertTrue(root.getConnections().isEmpty());
        assertTrue(owner.getGraph().getNodes().stream().noneMatch(node -> target.getId().equals(node.getRoutineId())));
    }
}
