package com.pathmind.execution;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.RuntimeValueScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionManagerScopeTest {
    private ExecutionManager manager;
    private Class<?> controllerClass;

    @BeforeEach
    void setUp() throws Exception {
        manager = ExecutionManager.getInstance();
        manager.requestStopAll();
        controllerClass = Arrays.stream(ExecutionManager.class.getDeclaredClasses())
            .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
            .findFirst()
            .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        manager.requestStopAll();
    }

    @Test
    void chainScopedVariablesWithSameNameStayIsolated() throws Exception {
        Node firstStart = registerRootChain(1, 1);
        Node secondStart = registerRootChain(2, 2);

        assertTrue(manager.setRuntimeVariable(firstStart, "target", amount("1"), RuntimeValueScope.CHAIN));
        assertTrue(manager.setRuntimeVariable(secondStart, "target", amount("2"), RuntimeValueScope.CHAIN));

        assertEquals("1", amountValue(manager.getRuntimeVariable(firstStart, "target", RuntimeValueScope.CHAIN)));
        assertEquals("2", amountValue(manager.getRuntimeVariable(secondStart, "target", RuntimeValueScope.CHAIN)));
        assertNull(manager.getGlobalRuntimeVariable("target"));
    }

    @Test
    void globalVariablesAreSharedOnlyThroughGlobalScope() throws Exception {
        Node firstStart = registerRootChain(1, 1);
        Node secondStart = registerRootChain(2, 2);

        assertTrue(manager.setGlobalRuntimeVariable("shared", amount("7")));

        assertEquals("7", amountValue(manager.getRuntimeVariable(firstStart, "shared", RuntimeValueScope.GLOBAL)));
        assertEquals("7", amountValue(manager.getRuntimeVariable(secondStart, "shared", RuntimeValueScope.GLOBAL)));
        assertNull(manager.getRuntimeVariable(firstStart, "shared", RuntimeValueScope.CHAIN));
        assertNull(manager.getRuntimeVariable(secondStart, "shared", RuntimeValueScope.CHAIN));
    }

    @Test
    void explicitChainAndGlobalVariablesMayUseSameName() throws Exception {
        Node start = registerRootChain(1, 1);

        assertTrue(manager.setRuntimeVariable(start, "value", amount("3"), RuntimeValueScope.CHAIN));
        assertTrue(manager.setGlobalRuntimeVariable("value", amount("8")));

        assertEquals("3", amountValue(manager.getRuntimeVariable(start, "value", RuntimeValueScope.CHAIN)));
        assertEquals("8", amountValue(manager.getRuntimeVariable(start, "value", RuntimeValueScope.GLOBAL)));

        List<ExecutionManager.RuntimeVariableEntry> entries = manager.getRuntimeVariableEntries();
        assertTrue(entries.stream().anyMatch(entry -> entry.getScope() == RuntimeValueScope.CHAIN
            && "value".equals(entry.getName())));
        assertTrue(entries.stream().anyMatch(entry -> entry.getScope() == RuntimeValueScope.GLOBAL
            && "value".equals(entry.getName())));
    }

    @Test
    void nestedExecutionReadsAndUpdatesRootChainScope() throws Exception {
        Node parentStart = newStart(1);
        Object parent = newRootController(parentStart, 11);
        registerController(parentStart, parent);

        Node childStart = newStart(2);
        Object child = newChildController(childStart, 12, parent);
        registerController(childStart, child);

        assertTrue(manager.setRuntimeVariable(parentStart, "distance", amount("4"), RuntimeValueScope.CHAIN));
        assertEquals("4", amountValue(manager.getRuntimeVariable(childStart, "distance", RuntimeValueScope.CHAIN)));

        assertTrue(manager.setRuntimeVariable(childStart, "distance", amount("9"), RuntimeValueScope.CHAIN));
        assertEquals("9", amountValue(manager.getRuntimeVariable(parentStart, "distance", RuntimeValueScope.CHAIN)));
    }

    @Test
    void runtimeListsUseTheSameChainAndGlobalRules() throws Exception {
        Node firstStart = registerRootChain(1, 1);
        Node secondStart = registerRootChain(2, 2);
        ExecutionManager.RuntimeList first = new ExecutionManager.RuntimeList(NodeType.PARAM_ITEM, List.of("stone"));
        ExecutionManager.RuntimeList second = new ExecutionManager.RuntimeList(NodeType.PARAM_ITEM, List.of("dirt"));
        ExecutionManager.RuntimeList shared = new ExecutionManager.RuntimeList(NodeType.PARAM_ITEM, List.of("diamond"));

        assertTrue(manager.setRuntimeList(firstStart, "items", first, RuntimeValueScope.CHAIN));
        assertTrue(manager.setRuntimeList(secondStart, "items", second, RuntimeValueScope.CHAIN));
        assertTrue(manager.setGlobalRuntimeList("shared_items", shared));

        assertEquals(List.of("stone"), manager.getRuntimeList(firstStart, "items", RuntimeValueScope.CHAIN).getEntries());
        assertEquals(List.of("dirt"), manager.getRuntimeList(secondStart, "items", RuntimeValueScope.CHAIN).getEntries());
        assertNull(manager.getRuntimeList(firstStart, "items", RuntimeValueScope.GLOBAL));
        assertEquals(List.of("diamond"), manager.getRuntimeList(secondStart, "shared_items", RuntimeValueScope.GLOBAL).getEntries());
    }

    @Test
    void listOperationsInheritScopeFromCreateListDeclaration() throws Exception {
        Node start = newStart(1);
        Node declaration = new Node(NodeType.CREATE_LIST, 0, 0);
        declaration.getParameter("List").setStringValue("targets");
        declaration.setRuntimeValueScope(RuntimeValueScope.GLOBAL);
        Node operation = new Node(NodeType.ADD_TO_LIST, 0, 0);
        operation.getParameter("List").setStringValue("targets");
        registerController(start, newRootController(start, 1, List.of(start, declaration, operation)));

        assertEquals(RuntimeValueScope.GLOBAL,
            manager.resolveRuntimeListScope(start, "targets", RuntimeValueScope.GLOBAL));
        assertTrue(declaration.supportsRuntimeValueScope());
        assertTrue(!operation.supportsRuntimeValueScope());
    }

    @Test
    void unscopedWritesDefaultToGlobal() throws Exception {
        Node firstStart = registerRootChain(1, 1);
        Node secondStart = registerRootChain(2, 2);

        assertTrue(manager.setRuntimeVariable(firstStart, "shared", amount("5")));

        assertEquals("5", amountValue(manager.getRuntimeVariable(firstStart, "shared")));
        assertEquals("5", amountValue(manager.getRuntimeVariable(secondStart, "shared")));
        assertNull(manager.getRuntimeVariable(secondStart, "shared", RuntimeValueScope.CHAIN));
        assertEquals("5", amountValue(manager.getGlobalRuntimeVariable("shared")));
        assertEquals(1, manager.getRuntimeVariableEntries().stream()
            .filter(entry -> "shared".equals(entry.getName()))
            .count());
    }

    @Test
    void stopAllClearsGlobalValuesEvenWhenNoChainIsActive() {
        assertTrue(manager.setGlobalRuntimeVariable("global", amount("1")));
        assertTrue(manager.setGlobalRuntimeList(
            "global_list", new ExecutionManager.RuntimeList(NodeType.PARAM_ITEM, List.of("stone"))));

        manager.requestStopAll();

        assertNull(manager.getGlobalRuntimeVariable("global"));
        assertNull(manager.getGlobalRuntimeList("global_list"));
    }

    private Node registerRootChain(int startNumber, int executionId) throws Exception {
        Node start = newStart(startNumber);
        registerController(start, newRootController(start, executionId));
        return start;
    }

    private Node newStart(int startNumber) {
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(startNumber);
        return start;
    }

    private Object newRootController(Node start, int executionId) throws Exception {
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(start, executionId);
    }

    private Object newRootController(Node start, int executionId, List<Node> graphNodes) throws Exception {
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(
            Node.class, int.class, List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(start, executionId, graphNodes, List.of());
    }

    private Object newChildController(Node start, int executionId, Object parent) throws Exception {
        Constructor<?> constructor = controllerClass.getDeclaredConstructor(
            Node.class, int.class, controllerClass, List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(start, executionId, parent, List.of(), List.of());
    }

    private void registerController(Node start, Object controller) throws Exception {
        Field field = ExecutionManager.class.getDeclaredField("activeChains");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Node, Object> activeChains = (Map<Node, Object>) field.get(manager);
        activeChains.put(start, controller);
    }

    private ExecutionManager.RuntimeVariable amount(String value) {
        return new ExecutionManager.RuntimeVariable(
            NodeType.PARAM_AMOUNT,
            Map.of("Amount", value, "amount", value)
        );
    }

    private String amountValue(ExecutionManager.RuntimeVariable variable) {
        assertNotNull(variable);
        return variable.getValues().get("Amount");
    }
}
