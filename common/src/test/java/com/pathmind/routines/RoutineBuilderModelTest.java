package com.pathmind.routines;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeValueTrait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutineBuilderModelTest {
    @Test
    void createsOneProtectedEntryAndLiveLabel() {
        RoutineBuilderModel builder = new RoutineBuilderModel(RoutineBuilderModel.createRoutine("Break"));
        NodeGraphData.RoutineInputData block = builder.addInput("block", RoutineValueKind.BLOCK);
        builder.addInput("range", RoutineValueKind.NUMBER);
        builder.ensureDefinitionGraph();

        assertEquals("Break [block] [range]", builder.getPreviewLabel());
        assertEquals(1, builder.getRoutine().getGraph().getNodes().stream()
            .filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).count());
        Node reporter = builder.createInputReporter(block.getId(), 10, 20);
        assertTrue(reporter.getProvidedTraits().contains(NodeValueTrait.BLOCK));
        assertTrue(builder.ownsReporter(reporter));
    }

    @Test
    void routineCallKeepsRoutineIdentityAndVisibleName() {
        Node call = Node.createRoutineCall("routine-id", "Break nearby", 10, 20);

        assertEquals(NodeType.ROUTINE_CALL, call.getType());
        assertEquals("routine-id", call.getRoutineId());
        assertEquals("Break nearby", call.getDisplayName().getString());
        assertFalse(call.isProtectedRoutineEntry());
    }

    @Test
    void editsDefaultsTypesAndOrderWithUndoRedo() {
        RoutineBuilderModel builder = new RoutineBuilderModel(RoutineBuilderModel.createRoutine("Move"));
        NodeGraphData.RoutineInputData target = builder.addInput("target", RoutineValueKind.ENTITY);
        NodeGraphData.RoutineInputData amount = builder.addInput("amount", RoutineValueKind.NUMBER);
        builder.updateInput(amount.getId(), "steps", RoutineValueKind.NUMBER, true, "3");
        builder.moveInput(amount.getId(), -1);

        assertEquals("steps", builder.getInputs().get(0).getLabel());
        assertEquals("3", builder.getInputs().get(0).getDefaultValue());
        assertTrue(builder.getInputs().get(0).getRequired());
        builder.undo();
        assertEquals(target.getId(), builder.getInputs().get(0).getId());
        builder.redo();
        assertEquals(amount.getId(), builder.getInputs().get(0).getId());
    }

    @Test
    void removesForeignAndDeletedReportersFromDefinition() {
        RoutineBuilderModel builder = new RoutineBuilderModel(RoutineBuilderModel.createRoutine("Safe"));
        NodeGraphData.RoutineInputData input = builder.addInput("value", RoutineValueKind.TEXT);
        Node valid = builder.createInputReporter(input.getId(), 0, 0);
        Node foreign = Node.createRoutineInput("another-routine",
            RoutineInputDefinition.create("foreign", RoutineValueKind.TEXT, 0), 0, 0);
        builder.getRoutine().setGraph(com.pathmind.data.NodeGraphPersistence.createGraphData(
            java.util.List.of(Node.createRoutineEntry(builder.getRoutine().getId(), "Safe", 0, 0), valid, foreign), java.util.List.of()));

        builder.ensureDefinitionGraph();
        assertEquals(2, builder.getRoutine().getGraph().getNodes().size());
        builder.removeInput(input.getId());
        assertFalse(builder.getRoutine().getGraph().getNodes().stream().anyMatch(node -> node.getType() == NodeType.ROUTINE_INPUT));
        assertNotNull(builder.getRoutine().getGraph());
    }
}
