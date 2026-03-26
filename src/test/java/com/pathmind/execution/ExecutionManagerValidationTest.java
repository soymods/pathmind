package com.pathmind.execution;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void requestStartForStartNumberRejectsInvalidWorkspaceGraph() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        manager.setWorkspaceGraph(List.of(start, runPreset), List.of(connection));

        assertFalse(manager.requestStartForStartNumber(1));
        assertFalse(manager.isExecuting());
        assertFalse(manager.isGlobalExecutionActive());
    }

    @Test
    void executeBranchRejectsInvalidGraphBeforeRunning() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        assertFalse(manager.executeBranch(start, List.of(start, runPreset), List.of(connection), PresetManager.getDefaultPresetName()));
        assertFalse(manager.isExecuting());
    }

    @Test
    void executeExternalBranchRejectsInvalidPresetGraphBeforeRunning() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        assertFalse(manager.executeExternalBranch(start, List.of(start, runPreset), List.of(connection), "BrokenPreset"));
        assertFalse(manager.isExecuting());
    }

    @Test
    void executeGraphRejectsInvalidWorkspaceBeforeStartingExecution() {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        manager.executeGraph(List.of(start, runPreset), List.of(connection));

        assertFalse(manager.isExecuting());
        assertFalse(manager.isGlobalExecutionActive());
    }
}
