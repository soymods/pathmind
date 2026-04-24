package com.pathmind.execution;

import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeMode;
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
    void functionCallsResolveFreshHandlerCloneEachTime() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node call = new Node(NodeType.EVENT_CALL, 100, 0);
        call.getParameter("Name").setStringValue("recurse");
        Node function = new Node(NodeType.EVENT_FUNCTION, 200, 0);
        function.getParameter("Name").setStringValue("recurse");

        NodeConnection startToCall = new NodeConnection(start, call, 0, 0);
        NodeConnection functionToCall = new NodeConnection(function, call, 0, 0);
        manager.setWorkspaceGraph(List.of(start, call, function), List.of(startToCall, functionToCall));

        Method resolver = ExecutionManager.class.getDeclaredMethod("resolveFunctionInvocationHandlers", String.class);
        resolver.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Node> firstHandlers = (List<Node>) resolver.invoke(manager, "recurse");
        @SuppressWarnings("unchecked")
        List<Node> secondHandlers = (List<Node>) resolver.invoke(manager, "recurse");

        assertEquals(1, firstHandlers.size());
        assertEquals(1, secondHandlers.size());
        assertEquals(NodeType.EVENT_FUNCTION, firstHandlers.get(0).getType());
        assertEquals(NodeType.EVENT_FUNCTION, secondHandlers.get(0).getType());
        assertNotEquals(firstHandlers.get(0).getId(), secondHandlers.get(0).getId());
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
    void runtimeVariableInterpolationFormatsStoredRotationValues() throws Exception {
        Node message = new Node(NodeType.MESSAGE, 0, 0);
        manager.setRuntimeVariableForAnyActiveChain(
            "look_dir",
            new ExecutionManager.RuntimeVariable(
                NodeType.PARAM_ROTATION,
                Map.of("Yaw", "90", "yaw", "90", "Pitch", "-30", "pitch", "-30")
            )
        );

        Method resolveRuntimeVariablesInText = Node.class.getDeclaredMethod("resolveRuntimeVariablesInText", String.class);
        resolveRuntimeVariablesInText.setAccessible(true);

        String resolved = (String) resolveRuntimeVariablesInText.invoke(message, "~look_dir");
        assertEquals("90 -30", resolved);
    }

    @Test
    void runtimeVariableInterpolationFormatsLegacyExactDirectionValues() throws Exception {
        Node message = new Node(NodeType.MESSAGE, 0, 0);
        manager.setRuntimeVariableForAnyActiveChain(
            "look_dir",
            new ExecutionManager.RuntimeVariable(
                NodeType.PARAM_DIRECTION,
                Map.of("Mode", "exact", "mode", "exact", "Yaw", "180", "yaw", "180", "Pitch", "15", "pitch", "15")
            )
        );

        Method resolveRuntimeVariablesInText = Node.class.getDeclaredMethod("resolveRuntimeVariablesInText", String.class);
        resolveRuntimeVariablesInText.setAccessible(true);

        String resolved = (String) resolveRuntimeVariablesInText.invoke(message, "~look_dir");
        assertEquals("180 15", resolved);
    }

    @Test
    void lookDirectionSingleAxisModeResolvesToNumericType() {
        Node lookDirection = new Node(NodeType.SENSOR_LOOK_DIRECTION, 0, 0);
        lookDirection.setMode(NodeMode.SENSOR_LOOK_YAW);

        assertEquals(NodeType.PARAM_AMOUNT, lookDirection.getResolvedValueType());
    }

    @Test
    void equalsTreatsRotationAndExactDirectionAsEquivalent() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("look_rotation");

        Node direction = new Node(NodeType.PARAM_DIRECTION, 0, 0);
        direction.getParameter("Mode").setStringValue("exact");
        direction.getParameter("Yaw").setStringValue("90");
        direction.getParameter("Pitch").setStringValue("-30");

        assertTrue(equals.attachParameter(variable, 0));
        assertTrue(equals.attachParameter(direction, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "look_rotation",
            new ExecutionManager.RuntimeVariable(
                NodeType.PARAM_ROTATION,
                Map.of("Yaw", "90", "yaw", "90", "Pitch", "-30", "pitch", "-30")
            )
        );

        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsTreatsStoredCoordinateAndCoordinateNodeAsEquivalent() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("target_pos");

        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue("10");
        coordinate.getParameter("Y").setStringValue("64");
        coordinate.getParameter("Z").setStringValue("-4");

        assertTrue(equals.attachParameter(variable, 0));
        assertTrue(equals.attachParameter(coordinate, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "target_pos",
            new ExecutionManager.RuntimeVariable(
                NodeType.PARAM_COORDINATE,
                Map.of("X", "10", "x", "10", "Y", "64", "y", "64", "Z", "-4", "z", "-4")
            )
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
    void equalsResolvesAmountVariableAgainstStoredListLengthVariable() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node left = new Node(NodeType.VARIABLE, 0, 0);
        left.getParameter("Variable").setStringValue("left_compare");
        Node right = new Node(NodeType.VARIABLE, 0, 0);
        right.getParameter("Variable").setStringValue("right_compare");

        assertTrue(equals.attachParameter(left, 0));
        assertTrue(equals.attachParameter(right, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "left_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_AMOUNT, Map.of("Amount", "1", "amount", "1"))
        );
        manager.setRuntimeVariableForAnyActiveChain(
            "right_compare",
            new ExecutionManager.RuntimeVariable(NodeType.LIST_LENGTH, Map.of("List", "list", "Amount", "1", "amount", "1"))
        );

        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsResolvesVariableAgainstSemanticallyEquivalentItemNode() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("stored_item");
        Node trade = new Node(NodeType.PARAM_VILLAGER_TRADE, 0, 0);
        trade.getParameter("Item").setStringValue("emerald");

        assertTrue(equals.attachParameter(variable, 0));
        assertTrue(equals.attachParameter(trade, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "stored_item",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_ITEM, Map.of("Item", "minecraft:emerald", "item", "minecraft:emerald"))
        );

        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsResolvesSemanticallyEquivalentItemVariablesAcrossNodeShapes() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node left = new Node(NodeType.VARIABLE, 0, 0);
        left.getParameter("Variable").setStringValue("left_item_compare");
        Node right = new Node(NodeType.VARIABLE, 0, 0);
        right.getParameter("Variable").setStringValue("right_item_compare");

        assertTrue(equals.attachParameter(left, 0));
        assertTrue(equals.attachParameter(right, 1));

        manager.setRuntimeVariableForAnyActiveChain(
            "left_item_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_ITEM, Map.of("Item", "minecraft:emerald", "item", "minecraft:emerald"))
        );
        manager.setRuntimeVariableForAnyActiveChain(
            "right_item_compare",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_VILLAGER_TRADE, Map.of("Item", "emerald", "item", "emerald"))
        );

        assertTrue(equals.evaluateSensor());
    }

    @Test
    void setVariableStoresResolvedDistanceSensorValue() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
        setVariable.setOwningStartNode(start);

        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("stored_distance");
        Node distance = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);
        Node first = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        first.getParameter("X").setStringValue("0");
        first.getParameter("Y").setStringValue("64");
        first.getParameter("Z").setStringValue("0");
        Node second = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        second.getParameter("X").setStringValue("3");
        second.getParameter("Y").setStringValue("64");
        second.getParameter("Z").setStringValue("4");

        assertTrue(distance.attachParameter(first, 0));
        assertTrue(distance.attachParameter(second, 1));
        assertTrue(setVariable.attachParameter(variable, 0));
        assertTrue(setVariable.attachParameter(distance, 1));

        Method executeSetVariable = Node.class.getDeclaredMethod("executeSetVariableCommand", CompletableFuture.class);
        executeSetVariable.setAccessible(true);

        CompletableFuture<Void> future = new CompletableFuture<>();
        executeSetVariable.invoke(setVariable, future);
        future.get(1, TimeUnit.SECONDS);

        ExecutionManager.RuntimeVariable stored = manager.getRuntimeVariable(start, "stored_distance");
        assertNotNull(stored);
        assertEquals(NodeType.PARAM_DISTANCE, stored.getType());
        assertEquals("5.0", stored.getValues().get("Distance"));
    }

    @Test
    void setVariableStoresResolvedPositionFromListItem() throws Exception {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
        setVariable.setOwningStartNode(start);

        Class<?> controllerClass = Arrays.stream(ExecutionManager.class.getDeclaredClasses())
            .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
            .findFirst()
            .orElseThrow();
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
        constructor.setAccessible(true);
        Object controller = constructor.newInstance(start, 1);

        Field activeChainsField = ExecutionManager.class.getDeclaredField("activeChains");
        activeChainsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Node, Object> activeChains = (Map<Node, Object>) activeChainsField.get(manager);
        activeChains.put(start, controller);

        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("stored_position");

        Node position = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);
        Node listItem = new Node(NodeType.LIST_ITEM, 0, 0);
        listItem.getParameter("List").setStringValue("list");
        listItem.getParameter("Index").setStringValue("1");

        assertTrue(position.attachParameter(listItem, 0));
        assertTrue(setVariable.attachParameter(variable, 0));
        assertTrue(setVariable.attachParameter(position, 1));

        manager.setRuntimeList(start, "list", new ExecutionManager.RuntimeList(
            NodeType.PARAM_COORDINATE,
            List.of("pm_list:{\"X\":\"10\",\"Y\":\"64\",\"Z\":\"-3\",\"x\":\"10\",\"y\":\"64\",\"z\":\"-3\"}")
        ));

        Method executeSetVariable = Node.class.getDeclaredMethod("executeSetVariableCommand", CompletableFuture.class);
        executeSetVariable.setAccessible(true);

        CompletableFuture<Void> future = new CompletableFuture<>();
        executeSetVariable.invoke(setVariable, future);
        future.get(1, TimeUnit.SECONDS);

        ExecutionManager.RuntimeVariable stored = manager.getRuntimeVariable(start, "stored_position");
        assertNotNull(stored);
        assertEquals(NodeType.PARAM_COORDINATE, stored.getType());
        assertEquals("10", stored.getValues().get("X"));
        assertEquals("64", stored.getValues().get("Y"));
        assertEquals("-3", stored.getValues().get("Z"));
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
    void repeatUntilExitUsesPrimarySocketWhenNodeExposesSingleOutput() throws Exception {
        Node repeatUntil = new Node(NodeType.CONTROL_REPEAT_UNTIL, 0, 0);

        Method exitSocketResolver = ExecutionManager.class.getDeclaredMethod(
            "getRepeatUntilExitOutputSocket", Node.class);
        exitSocketResolver.setAccessible(true);

        assertEquals(1, repeatUntil.getOutputSocketCount());
        assertEquals(0, exitSocketResolver.invoke(manager, repeatUntil));
    }

    @Test
    void repeatUntilExitUsesSecondarySocketWhenNodeExposesMultipleOutputs() throws Exception {
        Node repeatUntil = new Node(NodeType.CONTROL_REPEAT_UNTIL, 0, 0) {
            @Override
            public int getOutputSocketCount() {
                return 2;
            }
        };

        Method exitSocketResolver = ExecutionManager.class.getDeclaredMethod(
            "getRepeatUntilExitOutputSocket", Node.class);
        exitSocketResolver.setAccessible(true);

        assertEquals(1, exitSocketResolver.invoke(manager, repeatUntil));
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
