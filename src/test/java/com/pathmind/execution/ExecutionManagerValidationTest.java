package com.pathmind.execution;

import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.NodeGraphPersistence;
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
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionManagerValidationTest {
    private static final String CUSTOM_NODE_FOREVER_PRESET = "ExecutionManagerCustomNodeForever";

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
        try {
            Files.deleteIfExists(PresetManager.getPresetPath(CUSTOM_NODE_FOREVER_PRESET));
        } catch (Exception ignored) {
        }
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
    void booleanParameterVariableModeResolvesBooleanAndNumericRuntimeVariables() {
        Node booleanVariable = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        booleanVariable.setBooleanModeLiteral(false);
        booleanVariable.getParameter("Variable").setStringValue("flag_numeric");

        Node literalFalse = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        literalFalse.getParameter("Toggle").setStringValue("false");

        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        assertTrue(or.attachParameter(booleanVariable, 0));
        assertTrue(or.attachParameter(literalFalse, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "flag_numeric",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "1", "amount", "1"))
        );
        assertTrue(or.evaluateSensor());

        manager.setRuntimeVariableForAnyActiveChain(
            "flag_numeric",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_BOOLEAN, Map.of("Toggle", "false", "toggle", "false"))
        );
        assertTrue(!or.evaluateSensor());
    }

    @Test
    void equalsResolvesVariableAgainstInlineVariableBackedAmount() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("lhs_compare");

        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        amount.getParameter("Amount").setStringValue("~rhs_compare");

        assertTrue(equals.attachParameter(variable, 0));
        assertTrue(equals.attachParameter(amount, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "lhs_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "5", "amount", "5"))
        );
        manager.setRuntimeVariableForAnyActiveChain(
            "rhs_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "5", "amount", "5"))
        );

        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsResolvesVariableAgainstVariable() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node left = new Node(NodeType.VARIABLE, 0, 0);
        left.getParameter("Variable").setStringValue("left_compare");
        Node right = new Node(NodeType.VARIABLE, 0, 0);
        right.getParameter("Variable").setStringValue("right_compare");

        assertTrue(equals.attachParameter(left, 0));
        assertTrue(equals.attachParameter(right, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "left_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "42", "amount", "42"))
        );
        manager.setRuntimeVariableForAnyActiveChain(
            "right_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "42", "amount", "42"))
        );

        assertTrue(equals.evaluateSensor());
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

    @Test
    void branchLaunchClonesUseUniqueRuntimeIdsForForeverLoops() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node forever = new Node(NodeType.CONTROL_FOREVER, 120, 0);
        Node message = new Node(NodeType.MESSAGE, 220, 0);
        assertTrue(forever.attachActionNode(message));
        NodeConnection connection = new NodeConnection(start, forever, 0, 0);

        Method buildBranchData = ExecutionManager.class.getDeclaredMethod(
            "buildBranchData", Node.class, List.class, List.class);
        buildBranchData.setAccessible(true);
        Object branchData = buildBranchData.invoke(manager, start, List.of(start, forever, message), List.of(connection));
        assertNotNull(branchData);

        Method createBranchLaunchData = ExecutionManager.class.getDeclaredMethod(
            "createBranchLaunchData", branchData.getClass(), int.class);
        createBranchLaunchData.setAccessible(true);
        Object launchData = createBranchLaunchData.invoke(manager, branchData, 1);
        assertNotNull(launchData);

        Field branchDataField = launchData.getClass().getDeclaredField("branchData");
        branchDataField.setAccessible(true);
        Object clonedBranchData = branchDataField.get(launchData);
        assertNotNull(clonedBranchData);

        Field nodesField = clonedBranchData.getClass().getDeclaredField("nodes");
        nodesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Node> clonedNodes = (List<Node>) nodesField.get(clonedBranchData);

        Node clonedStart = clonedNodes.stream()
            .filter(node -> node.getType() == NodeType.START)
            .findFirst()
            .orElseThrow();
        Node clonedForever = clonedNodes.stream()
            .filter(node -> node.getType() == NodeType.CONTROL_FOREVER)
            .findFirst()
            .orElseThrow();
        Node clonedMessage = clonedNodes.stream()
            .filter(node -> node.getType() == NodeType.MESSAGE)
            .findFirst()
            .orElseThrow();

        assertEquals(1, clonedStart.getStartNodeNumber());
        assertNotEquals(start.getId(), clonedStart.getId());
        assertNotEquals(forever.getId(), clonedForever.getId());
        assertNotEquals(message.getId(), clonedMessage.getId());
        assertEquals(clonedMessage, clonedForever.getAttachedActionNode());
    }

    @Test
    void externalBranchLaunchesForPresetBackedCustomNodeGraph() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node forever = new Node(NodeType.CONTROL_FOREVER, 120, 0);
        Node wait = new Node(NodeType.WAIT, 220, 0);
        wait.getParameter("Duration").setStringValue("5.0");
        assertTrue(forever.attachActionNode(wait));
        NodeConnection connection = new NodeConnection(start, forever, 0, 0);

        assertTrue(NodeGraphPersistence.saveNodeGraphForPreset(
            CUSTOM_NODE_FOREVER_PRESET,
            List.of(start, forever, wait),
            List.of(connection)
        ));

        var loadedGraph = NodeGraphPersistence.loadNodeGraphForPreset(CUSTOM_NODE_FOREVER_PRESET);
        List<Node> reloadedNodes = NodeGraphPersistence.convertToNodes(loadedGraph);
        Node reloadedStart = reloadedNodes.stream()
            .filter(node -> node.getType() == NodeType.START)
            .findFirst()
            .orElseThrow();
        Map<String, Node> nodeMap = new java.util.HashMap<>();
        for (Node node : reloadedNodes) {
            nodeMap.put(node.getId(), node);
        }
        List<NodeConnection> reloadedConnections = NodeGraphPersistence.convertToConnections(
            loadedGraph,
            nodeMap
        );

        Field activeChainsField = ExecutionManager.class.getDeclaredField("activeChains");
        activeChainsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Node, Object> activeChains = (Map<Node, Object>) activeChainsField.get(manager);
        int before = activeChains.size();

        CompletableFuture<Void> future = manager.executeExternalBranchAndWait(
            reloadedStart,
            reloadedNodes,
            reloadedConnections,
            CUSTOM_NODE_FOREVER_PRESET
        );

        assertNotNull(future);
        assertEquals(before + 1, activeChains.size());
    }

    @Test
    void externalBranchCanStartWhenAnotherChainUsesSameStartNumber() throws Exception {
        Node activeStart = new Node(NodeType.START, 0, 0);
        activeStart.setStartNodeNumber(1);

        Class<?> controllerClass = Arrays.stream(ExecutionManager.class.getDeclaredClasses())
            .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
            .findFirst()
            .orElseThrow();
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
        constructor.setAccessible(true);
        Object controller = constructor.newInstance(activeStart, 99);

        Field activeChainsField = ExecutionManager.class.getDeclaredField("activeChains");
        activeChainsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Node, Object> activeChains = (Map<Node, Object>) activeChainsField.get(manager);
        activeChains.put(activeStart, controller);

        Node nestedStart = new Node(NodeType.START, 0, 0);
        nestedStart.setStartNodeNumber(1);
        Node forever = new Node(NodeType.CONTROL_FOREVER, 120, 0);
        Node wait = new Node(NodeType.WAIT, 220, 0);
        wait.getParameter("Duration").setStringValue("5.0");
        assertTrue(forever.attachActionNode(wait));
        NodeConnection connection = new NodeConnection(nestedStart, forever, 0, 0);

        CompletableFuture<Void> future = manager.executeExternalBranchAndWait(
            nestedStart,
            List.of(nestedStart, forever, wait),
            List.of(connection),
            "NestedPreset"
        );

        assertNotNull(future);
        assertEquals(2, activeChains.size());
    }

    @Test
    void missingSecondaryOutputDoesNotFallBackToPrimaryOutput() throws Exception {
        Node ifElse = new Node(NodeType.CONTROL_IF_ELSE, 0, 0);
        Node forever = new Node(NodeType.CONTROL_FOREVER, 100, 0);
        Node wait = new Node(NodeType.WAIT, 200, 0);
        wait.getParameter("Duration").setStringValue("5.0");
        assertTrue(forever.attachActionNode(wait));

        Field activeConnectionsField = ExecutionManager.class.getDeclaredField("activeConnections");
        activeConnectionsField.setAccessible(true);
        activeConnectionsField.set(manager, new java.util.ArrayList<>(List.of(
            new NodeConnection(ifElse, forever, 0, 0)
        )));

        Class<?> controllerClass = Arrays.stream(ExecutionManager.class.getDeclaredClasses())
            .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
            .findFirst()
            .orElseThrow();
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
        constructor.setAccessible(true);
        Object controller = constructor.newInstance(ifElse, 1);

        Method continueFromOutputSocket = ExecutionManager.class.getDeclaredMethod(
            "continueFromOutputSocket", Node.class, controllerClass, int.class, Node.class, int.class);
        continueFromOutputSocket.setAccessible(true);

        @SuppressWarnings("unchecked")
        CompletableFuture<Void> future = (CompletableFuture<Void>) continueFromOutputSocket.invoke(
            manager, ifElse, controller, 1, null, 1);

        assertTrue(future.isDone());
    }

    private void setCachedSettingsForTests() throws Exception {
        SettingsManager.Settings settings = new SettingsManager.Settings();
        settings.nodeDelayMs = 0;

        Field cachedSettingsField = SettingsManager.class.getDeclaredField("cachedSettings");
        cachedSettingsField.setAccessible(true);
        cachedSettingsField.set(null, settings);
    }
}
