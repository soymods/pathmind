package com.pathmind.execution;

import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionManagerValidationTest {

    private ExecutionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = ExecutionManager.getInstance();
        manager.requestStopAll();
        setCachedSettingsForTests();
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

    @Test
    void repeatCountResolvesGlobalRuntimeVariableNames() throws Exception {
        Node repeat = new Node(NodeType.CONTROL_REPEAT, 0, 0);
        repeat.getParameter("Count").setStringValue("~length");

        ExecutionManager.RuntimeVariable variable = new ExecutionManager.RuntimeVariable(
            NodeType.PARAM_AMOUNT,
            Map.of("Amount", "10", "amount", "10")
        );
        manager.setRuntimeVariableForAnyActiveChain("length", variable);

        Method getIntParameter = Node.class.getDeclaredMethod("getIntParameter", String.class, int.class);
        getIntParameter.setAccessible(true);

        int resolved = (int) getIntParameter.invoke(repeat, "Count", 1);
        assertTrue(resolved == 10);
    }

    @Test
    void joinAllBarrierWaitsForBothInputsAndResets() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        Node joinAll = new Node(NodeType.CONTROL_JOIN_ALL, 100, 0);

        Class<?> controllerClass = Arrays.stream(ExecutionManager.class.getDeclaredClasses())
            .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
            .findFirst()
            .orElseThrow();
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
        constructor.setAccessible(true);
        Object controller = constructor.newInstance(start, 1);

        Method markJoinAllArrival = ExecutionManager.class.getDeclaredMethod(
            "markJoinAllArrival", Node.class, controllerClass, int.class);
        markJoinAllArrival.setAccessible(true);

        assertTrue(!(boolean) markJoinAllArrival.invoke(manager, joinAll, controller, 0));
        assertTrue(!(boolean) markJoinAllArrival.invoke(manager, joinAll, controller, 0));
        assertTrue((boolean) markJoinAllArrival.invoke(manager, joinAll, controller, 1));
        assertTrue(!(boolean) markJoinAllArrival.invoke(manager, joinAll, controller, 1));
        assertTrue((boolean) markJoinAllArrival.invoke(manager, joinAll, controller, 0));
    }

    private void setCachedSettingsForTests() throws Exception {
        SettingsManager.Settings settings = new SettingsManager.Settings();
        settings.nodeDelayMs = 0;

        Field cachedSettingsField = SettingsManager.class.getDeclaredField("cachedSettings");
        cachedSettingsField.setAccessible(true);
        cachedSettingsField.set(null, settings);
    }
}
