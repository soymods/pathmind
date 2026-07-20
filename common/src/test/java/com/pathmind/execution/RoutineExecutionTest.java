package com.pathmind.execution;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.SettingsManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.RuntimeValueScope;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineValueKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutineExecutionTest {
    private final ExecutionManager manager = ExecutionManager.getInstance();
    private Integer originalNodeDelay;

    @BeforeEach
    void speedUpExecution() {
        originalNodeDelay = SettingsManager.getCurrent().nodeDelayMs;
        SettingsManager.getCurrent().nodeDelayMs = 0;
    }

    @AfterEach
    void cleanUp() {
        manager.requestStopAll();
        manager.setWorkspaceGraph(List.of(), List.of(), List.of());
        SettingsManager.getCurrent().nodeDelayMs = originalNodeDelay;
    }

    @Test
    void sequentialInvocationsSnapshotDifferentInputsAndUseDefaults() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Travel");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData destination = builder.addInput("destination", RoutineValueKind.NUMBER);
        NodeGraphData.RoutineInputData range = builder.addInput("range", RoutineValueKind.NUMBER);
        builder.updateInput(range.getId(), "range", RoutineValueKind.NUMBER, true, "5");
        Node first = Node.createRoutineCall(routine, 0, 0);
        Node second = Node.createRoutineCall(routine, 0, 0);
        Node firstValue = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        firstValue.getParameter("Amount").setStringValue("10");
        Node secondValue = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        secondValue.getParameter("Amount").setStringValue("20");
        first.attachParameter(firstValue, first.getRoutineSlotForInputId(destination.getId()));
        second.attachParameter(secondValue, second.getRoutineSlotForInputId(destination.getId()));

        var firstInputs = manager.captureRoutineInputs(first, routine, 1);
        var secondInputs = manager.captureRoutineInputs(second, routine, 2);

        assertEquals("10", firstInputs.get(destination.getId()).getValues().get("Amount"));
        assertEquals("20", secondInputs.get(destination.getId()).getValues().get("Amount"));
        assertEquals("5", firstInputs.get(range.getId()).getValues().get("Amount"));
    }

    @Test
    void callerFutureWaitsForRoutineAndNestedRoutineCompletion() {
        NodeGraphData.RoutineDefinitionData inner = RoutineBuilderModel.createRoutine("Inner");
        NodeGraphData.RoutineDefinitionData outer = RoutineBuilderModel.createRoutine("Outer");
        List<Node> outerNodes = NodeGraphPersistence.convertToNodes(outer.getGraph());
        Node outerEntry = outerNodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node nestedCall = Node.createRoutineCall(inner, 200, 200);
        outerNodes.add(nestedCall);
        outer.setGraph(NodeGraphPersistence.createGraphData(outerNodes,
            List.of(new NodeConnection(outerEntry, nestedCall, 0, 0))));

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(outer, 100, 0);
        List<Node> rootNodes = List.of(start, invocation);
        List<NodeConnection> rootConnections = List.of(new NodeConnection(start, invocation, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, List.of(outer, inner));

        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "RoutineTest");

        assertNotNull(future);
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void standaloneRoutinePreviewUsesRoutineDefaults() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Preview");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("amount", RoutineValueKind.NUMBER);
        builder.updateInput(input.getId(), "amount", RoutineValueKind.NUMBER, true, "12");

        List<Node> nodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(routine.getGraph()));
        Node entry = nodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node set = new Node(NodeType.SET_VARIABLE, 160, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.getParameter("Variable").setStringValue("preview_result");
        variable.setRuntimeValueScope(RuntimeValueScope.GLOBAL);
        Node reporter = builder.createInputReporter(input.getId(), 0, 0);
        assertTrue(set.attachParameter(variable, 0));
        assertTrue(set.attachParameter(reporter, 1));
        nodes.addAll(List.of(set, variable, reporter));
        routine.setGraph(NodeGraphPersistence.createGraphData(
            nodes, List.of(new NodeConnection(entry, set, 0, 0))));

        var future = manager.executeRoutineAndWait(routine, List.of(routine), "RoutinePreviewTest");
        assertNotNull(future);
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));

        assertNotNull(manager.getGlobalRuntimeVariable("preview_result"));
        assertEquals("12", manager.getGlobalRuntimeVariable("preview_result").getValues().get("Amount"));
    }

    @Test
    void routineCalculateCanIncrementMainPresetGlobalVariableAcrossCalls() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Timer");
        List<Node> routineNodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(routine.getGraph()));
        Node entry = routineNodes.stream()
            .filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node calculate = new Node(NodeType.CALCULATE, 200, 0);
        calculate.setMessageLine(0, "A = $variable+1");
        Node update = new Node(NodeType.SET_VARIABLE, 400, 0);
        Node updateTarget = new Node(NodeType.VARIABLE, 0, 0);
        updateTarget.getParameter("Variable").setStringValue("variable");
        updateTarget.setRuntimeValueScope(RuntimeValueScope.GLOBAL);
        Node calculatedValue = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        calculatedValue.getParameter("Amount").setStringValue("$A");
        assertTrue(update.attachParameter(updateTarget, 0));
        assertTrue(update.attachParameter(calculatedValue, 1));
        routineNodes.addAll(List.of(calculate, update, updateTarget, calculatedValue));
        routine.setGraph(NodeGraphPersistence.createGraphData(routineNodes, List.of(
            new NodeConnection(entry, calculate, 0, 0),
            new NodeConnection(calculate, update, 0, 0))));

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node initialize = new Node(NodeType.SET_VARIABLE, 100, 0);
        Node initializeTarget = new Node(NodeType.VARIABLE, 0, 0);
        initializeTarget.getParameter("Variable").setStringValue("variable");
        initializeTarget.setRuntimeValueScope(RuntimeValueScope.GLOBAL);
        Node zero = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        zero.getParameter("Amount").setStringValue("0");
        assertTrue(initialize.attachParameter(initializeTarget, 0));
        assertTrue(initialize.attachParameter(zero, 1));
        Node forever = new Node(NodeType.CONTROL_FOREVER, 300, 0);
        Node invocation = Node.createRoutineCall(routine, 0, 0);
        assertTrue(forever.attachActionNode(invocation));
        List<Node> rootNodes = List.of(start, initialize, initializeTarget, zero, forever, invocation);
        List<NodeConnection> rootConnections = List.of(
            new NodeConnection(start, initialize, 0, 0),
            new NodeConnection(initialize, forever, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, List.of(routine));

        var future = manager.executeExternalBranchAndWait(
            start, rootNodes, rootConnections, "RoutineCalculateTest");

        assertNotNull(future);
        long deadline = System.currentTimeMillis() + 5000L;
        String amount = null;
        while (System.currentTimeMillis() < deadline) {
            ExecutionManager.RuntimeVariable value = manager.getRuntimeVariable(
                start, "variable", RuntimeValueScope.GLOBAL);
            amount = value == null ? null : value.getValues().get("Amount");
            if (amount != null && Double.parseDouble(amount) >= 3.0) {
                break;
            }
            Thread.onSpinWait();
        }
        assertNotNull(amount);
        assertTrue(Double.parseDouble(amount) >= 3.0, "routine loop stopped at " + amount);
        manager.requestStopAll();
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void routineReporterFeedsChainAndGlobalVariablesAndCancellationUnwindsCall() throws Exception {
        NodeGraphData.RoutineDefinitionData helper = RoutineBuilderModel.createRoutine("Yield");
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Store");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("value", RoutineValueKind.NUMBER);
        List<Node> definitionNodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(routine.getGraph()));
        Node entry = definitionNodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node chainSet = new Node(NodeType.SET_VARIABLE, 150, 0);
        Node chainName = new Node(NodeType.VARIABLE, 0, 0);
        chainName.getParameter("Variable").setStringValue("value");
        chainName.setRuntimeValueScope(RuntimeValueScope.CHAIN);
        Node chainReporter = builder.createInputReporter(input.getId(), 0, 0);
        assertTrue(chainSet.attachParameter(chainName, 0));
        assertTrue(chainSet.attachParameter(chainReporter, 1));
        Node globalSet = new Node(NodeType.SET_VARIABLE, 300, 0);
        Node globalName = new Node(NodeType.VARIABLE, 0, 0);
        globalName.getParameter("Variable").setStringValue("shared");
        globalName.setRuntimeValueScope(RuntimeValueScope.GLOBAL);
        Node globalReporter = builder.createInputReporter(input.getId(), 0, 0);
        assertTrue(globalSet.attachParameter(globalName, 0));
        assertTrue(globalSet.attachParameter(globalReporter, 1));
        Node forever = new Node(NodeType.CONTROL_FOREVER, 450, 0);
        Node helperCall = Node.createRoutineCall(helper, 0, 0);
        assertTrue(forever.attachActionNode(helperCall));
        definitionNodes.addAll(List.of(chainSet, chainName, chainReporter, globalSet, globalName, globalReporter, forever, helperCall));
        routine.setGraph(NodeGraphPersistence.createGraphData(definitionNodes, List.of(
            new NodeConnection(entry, chainSet, 0, 0),
            new NodeConnection(chainSet, globalSet, 0, 0),
            new NodeConnection(globalSet, forever, 0, 0)
        )));

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(routine, 100, 0);
        Node supplied = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        supplied.getParameter("Amount").setStringValue("42");
        assertTrue(invocation.attachParameter(supplied, invocation.getRoutineSlotForInputId(input.getId())));
        List<Node> rootNodes = List.of(start, invocation, supplied);
        List<NodeConnection> rootConnections = List.of(new NodeConnection(start, invocation, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, List.of(routine, helper));

        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "RoutineScopeTest");
        assertNotNull(future);
        long deadline = System.currentTimeMillis() + 5000L;
        while ((manager.getRuntimeVariable(start, "value", RuntimeValueScope.CHAIN) == null
            || manager.getRuntimeVariable(start, "shared", RuntimeValueScope.GLOBAL) == null)
            && System.currentTimeMillis() < deadline) Thread.onSpinWait();

        assertEquals("42", manager.getRuntimeVariable(start, "value", RuntimeValueScope.CHAIN).getValues().get("Amount"));
        assertEquals("42", manager.getRuntimeVariable(start, "shared", RuntimeValueScope.GLOBAL).getValues().get("Amount"));
        manager.requestStopAll();
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void runawayRecursionFailsAtDepthLimit() {
        NodeGraphData.RoutineDefinitionData recursive = RoutineBuilderModel.createRoutine("Recursive");
        List<Node> definitionNodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(recursive.getGraph()));
        Node entry = definitionNodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node recursiveCall = Node.createRoutineCall(recursive, 200, 200);
        definitionNodes.add(recursiveCall);
        recursive.setGraph(NodeGraphPersistence.createGraphData(definitionNodes,
            List.of(new NodeConnection(entry, recursiveCall, 0, 0))));

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(recursive, 100, 0);
        List<Node> rootNodes = List.of(start, invocation);
        List<NodeConnection> rootConnections = List.of(new NodeConnection(start, invocation, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, List.of(recursive));

        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "RoutineTest");

        assertNotNull(future);
        assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
    }

    @Test
    void incompatibleRoutineInputValueFailsDuringSubstitution() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Use");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("value", RoutineValueKind.TEXT);
        List<Node> definitionNodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(routine.getGraph()));
        Node entry = definitionNodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
        Node set = new Node(NodeType.SET_VARIABLE, 160, 0);
        Node reporter = builder.createInputReporter(input.getId(), 0, 0);
        assertTrue(set.attachParameter(reporter, 0));
        definitionNodes.addAll(List.of(set, reporter));
        routine.setGraph(NodeGraphPersistence.createGraphData(definitionNodes,
            List.of(new NodeConnection(entry, set, 0, 0))));

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(routine, 100, 0);
        Node block = new Node(NodeType.PARAM_BLOCK, 0, 0);
        assertTrue(invocation.attachParameter(block, invocation.getRoutineSlotForInputId(input.getId())));
        List<Node> rootNodes = List.of(start, invocation, block);
        List<NodeConnection> rootConnections = List.of(new NodeConnection(start, invocation, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, List.of(routine));

        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "RoutineInputErrorTest");

        assertNotNull(future);
        Exception error = assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(error.toString().contains("Routine input")
            || (error.getCause() != null && error.getCause().toString().contains("Routine input")));
    }

    @Test
    void manyDefinitionsAndSequentialInvocationsCompleteWithoutLeakingState() {
        List<NodeGraphData.RoutineDefinitionData> routines = new ArrayList<>();
        List<Node> rootNodes = new ArrayList<>();
        List<NodeConnection> rootConnections = new ArrayList<>();
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        rootNodes.add(start);
        Node previous = start;

        for (int index = 0; index < 64; index++) {
            NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Routine " + index);
            routines.add(routine);
            Node invocation = Node.createRoutineCall(routine, 100 + index * 20, 0);
            rootNodes.add(invocation);
            rootConnections.add(new NodeConnection(previous, invocation, 0, 0));
            previous = invocation;
        }

        manager.setWorkspaceGraph(rootNodes, rootConnections, routines);
        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "RoutineStressTest");

        assertNotNull(future);
        assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
    }

    @Test
    void deeplyNestedCallsCompleteBelowTheSafetyLimit() {
        List<NodeGraphData.RoutineDefinitionData> routines = new ArrayList<>();
        NodeGraphData.RoutineDefinitionData child = null;
        for (int depth = 0; depth < 16; depth++) {
            NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Depth " + depth);
            if (child != null) {
                List<Node> nodes = new ArrayList<>(NodeGraphPersistence.convertToNodes(routine.getGraph()));
                Node entry = nodes.stream().filter(node -> node.getType() == NodeType.ROUTINE_ENTRY).findFirst().orElseThrow();
                Node call = Node.createRoutineCall(child, 200, 0);
                nodes.add(call);
                routine.setGraph(NodeGraphPersistence.createGraphData(nodes,
                    List.of(new NodeConnection(entry, call, 0, 0))));
            }
            routines.add(routine);
            child = routine;
        }

        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(child, 100, 0);
        List<Node> rootNodes = List.of(start, invocation);
        List<NodeConnection> rootConnections = List.of(new NodeConnection(start, invocation, 0, 0));
        manager.setWorkspaceGraph(rootNodes, rootConnections, routines);
        var future = manager.executeExternalBranchAndWait(start, rootNodes, rootConnections, "NestedRoutineStressTest");

        assertNotNull(future);
        assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
    }
}
