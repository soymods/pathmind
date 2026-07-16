package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineValueKind;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGraphTest {

    @Test
    void autosaveDelegatesToOwningWorkspace() {
        NodeGraph graph = new NodeGraph();
        AtomicInteger saves = new AtomicInteger();
        graph.setWorkspaceSaveHandler(() -> {
            saves.incrementAndGet();
            return true;
        });
        graph.setActiveRoutineWorkspaceId("routine-id");

        graph.markWorkspaceDirty();

        assertEquals(1, saves.get());
        assertFalse(graph.isWorkspaceDirty());
    }

    @Test
    void applyingRootSnapshotKeepsNewRoutineOnImmediateResave() {
        NodeGraph graph = new NodeGraph();
        NodeGraphData root = new NodeGraphData();
        root.getRoutines().add(RoutineBuilderModel.createRoutine("Mine"));

        assertTrue(graph.applyGraphDataSnapshot(root, false));
        NodeGraphData savedAgain = graph.exportGraphDataSnapshot();

        assertEquals(1, savedAgain.getRoutines().size());
        assertEquals("Mine", savedAgain.getRoutines().get(0).getName());
    }

    @Test
    void liveRoutineInputLabelSyncsIntoSidebarMetadata() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Mine");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("block", RoutineValueKind.BLOCK);
        Node reporter = builder.createInputReporter(input.getId(), 20, 20);
        routine.getGraph().getNodes().addAll(com.pathmind.data.NodeGraphPersistence
            .createGraphData(java.util.List.of(reporter), java.util.List.of()).getNodes());

        NodeGraph graph = new NodeGraph();
        graph.setActiveRoutineWorkspaceId(routine.getId());
        assertTrue(graph.applyGraphDataSnapshot(routine.getGraph(), false));
        graph.setActiveRoutineWorkspaceId(routine.getId());
        Node liveReporter = graph.getNodes().stream().filter(node -> node.getType() == NodeType.ROUTINE_INPUT).findFirst().orElseThrow();
        liveReporter.getParameter("Label").setStringValue("target block");

        graph.syncRoutineDefinitionMetadata(routine);

        assertEquals("target block", routine.getInputs().get(0).getLabel());
    }

    @Test
    void routineEntryAlwaysUsesTheSameMinimalPresentation() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Simple");
        NodeGraph graph = new NodeGraph();
        graph.setActiveRoutineWorkspaceId(routine.getId());
        graph.setRoutineValidationContext(java.util.List.of(routine));

        assertTrue(graph.applyGraphDataSnapshot(routine.getGraph(), false));
        Node entry = graph.getNodes().stream()
            .filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();

        assertTrue(entry.usesMinimalNodePresentation());
        assertEquals(32, entry.getHeight());

        new RoutineBuilderModel(routine).addInput("value", RoutineValueKind.TEXT);
        graph.setRoutineValidationContext(java.util.List.of(routine));
        assertTrue(entry.usesMinimalNodePresentation());
        assertEquals(32, entry.getHeight());
    }

    @Test
    void sidebarDropPrefersDeepestHoveredParameterHost() {
        NodeGraph graph = new NodeGraph();

        Node parent = new Node(NodeType.OPERATOR_EQUALS, 100, 100);
        Node child = new Node(NodeType.OPERATOR_LESS, 0, 0);
        Node existing = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertTrue(parent.attachParameter(child, 0));
        assertTrue(child.attachParameter(existing, 0));

        graph.addNode(parent);
        graph.addNode(child);
        graph.addNode(existing);

        Node dropped = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        int hoverX = child.getX() + 8;
        int hoverY = child.getY() + 8;

        graph.handleSidebarDrop(dropped, hoverX, hoverY);

        assertSame(child, dropped.getParentParameterHost());
        assertSame(existing, child.getAttachedParameter(0));
        assertSame(dropped, child.getAttachedParameter(1));
        assertSame(child, parent.getAttachedParameter(0));
        assertSame(child, existing.getParentParameterHost());
    }

    @Test
    void initializeClearsStaleSelectionButKeepsClipboardPasteUsable() {
        NodeGraph graph = new NodeGraph();

        Node copied = new Node(NodeType.PARAM_AMOUNT, 100, 100);
        graph.addNode(copied);
        graph.selectNode(copied);

        assertTrue(graph.copySelectedNodeToClipboard());

        graph.initializeWithScreenDimensions(800, 600, 180, 40);

        assertFalse(copied.isSelected());
        assertTrue(graph.getSelectedNodes().isEmpty());

        Node pasted = graph.pasteClipboardNode();

        assertNotNull(pasted);
        assertNotSame(copied, pasted);
        assertEquals(NodeType.PARAM_AMOUNT, pasted.getType());
        assertEquals(1, graph.getSelectedNodes().size());
        assertSame(pasted, graph.getSelectedNode());
    }

    @Test
    void focusingInteractiveControlOnSelectedNodePreservesMultiSelection() {
        NodeGraph graph = new NodeGraph();
        Node first = new Node(NodeType.STICKY_NOTE, 100, 100);
        Node second = new Node(NodeType.MESSAGE, 260, 100);
        graph.addNode(first);
        graph.addNode(second);
        graph.selectNodes(java.util.List.of(first, second));

        graph.focusSelectedNode(first);

        assertEquals(2, graph.getSelectedNodes().size());
        assertTrue(graph.isNodeSelected(first));
        assertTrue(graph.isNodeSelected(second));
        assertSame(first, graph.getSelectedNode());
    }

    @Test
    void cutSelectedNodeCopiesThenDeletesAndCanPaste() {
        NodeGraph graph = new NodeGraph();

        Node cut = new Node(NodeType.PARAM_AMOUNT, 100, 100);
        graph.addNode(cut);
        graph.selectNode(cut);

        assertTrue(graph.cutSelectedNodeToClipboard());
        assertFalse(graph.getNodes().contains(cut));
        assertTrue(graph.getSelectedNodes().isEmpty());

        Node pasted = graph.pasteClipboardNode();

        assertNotNull(pasted);
        assertNotSame(cut, pasted);
        assertEquals(NodeType.PARAM_AMOUNT, pasted.getType());
        assertEquals(1, graph.getNodes().size());
        assertSame(pasted, graph.getSelectedNode());
    }

    @Test
    void draggedPassIncludesConnectionsTouchingDraggedNodes() {
        NodeGraph graph = new NodeGraph();
        Node source = new Node(NodeType.START, 100, 100);
        Node target = new Node(NodeType.MESSAGE, 240, 100);
        Node unrelated = new Node(NodeType.MESSAGE, 380, 100);

        NodeConnection activeConnection = new NodeConnection(source, target, 0, 0);
        NodeConnection unrelatedConnection = new NodeConnection(source, unrelated, 0, 0);

        assertFalse(graph.shouldRenderConnectionInDraggedPass(activeConnection));

        target.setDragging(true);

        assertTrue(graph.shouldRenderConnectionInDraggedPass(activeConnection));
        assertFalse(graph.shouldRenderConnectionInDraggedPass(unrelatedConnection));
    }
}
