package com.pathmind.execution;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionManagerValidationTest {

    private ExecutionManager manager;

    @BeforeEach
    void setUp() {
        manager = ExecutionManager.getInstance();
        manager.requestStopAll();
    }

    @AfterEach
    void tearDown() {
        manager.requestStopAll();
    }

    @Test
    void requestStartForStartNumberAllowsInvalidWorkspaceGraph() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        manager.setWorkspaceGraph(List.of(start, runPreset), List.of(connection));

        assertTrue(manager.requestStartForStartNumber(1));
    }

    @Test
    void executeBranchAllowsInvalidGraphBeforeRunning() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        assertTrue(manager.executeBranch(start, List.of(start, runPreset), List.of(connection), PresetManager.getDefaultPresetName()));
    }

    @Test
    void executeExternalBranchAllowsInvalidPresetGraphBeforeRunning() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        assertTrue(manager.executeExternalBranch(start, List.of(start, runPreset), List.of(connection), "BrokenPreset"));
    }

    @Test
    void executeGraphAllowsInvalidWorkspaceBeforeStartingExecution() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        assertDoesNotThrow(() -> manager.executeGraph(List.of(start, runPreset), List.of(connection)));
    }
}
