package com.pathmind.execution;

import static com.pathmind.execution.ExecutionGraphSnapshotSupport.*;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.RuntimeValueScope;
import com.pathmind.routines.RoutineValueKind;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.screen.PathmindScreens;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.UiUtilsDependencyChecker;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Manages the execution state of the node graph.
 * Tracks which node is currently active and provides state information for overlays.
 */
public class ExecutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionManager.class);
    private static final Executor CHAIN_COMPLETION_BOUNDARY_EXECUTOR = ForkJoinPool.commonPool();
    private static final int MAX_CHAIN_EXECUTIONS_PER_START = 128;
    private static final int MAX_FUNCTION_CALL_DEPTH = 32;
    private static final int MAX_ROUTINE_CALL_DEPTH = 32;
    public static final String CHAT_MESSAGE_EVENT_NAME = "chat_message";
    public static final String CHAT_SENDER_VARIABLE_NAME = "chat_sender";
    public static final String CHAT_MESSAGE_VARIABLE_NAME = "chat_message";
    private static volatile ExecutionManager instance;
    private final ExecutionSessionState sessionState;
    private NodeGraphData lastExecutedGraph;
    private NodeGraphData lastGlobalGraph;
    private List<Node> activeNodes;
    private List<NodeConnection> activeConnections;
    private List<Node> workspaceNodes;
    private List<NodeConnection> workspaceConnections;
    private volatile List<NodeGraphData.RoutineDefinitionData> workspaceRoutines;
    private final Set<ConnectionKey> activeConnectionLookup;
    private final Map<String, Node> outputNodeLookup;
    private volatile boolean cancelRequested;
    private final Map<Node, ChainController> activeChains;
    private final Map<ChainController, List<NodeGraphData.RoutineDefinitionData>> controllerRoutines;
    private final ExecutionRuntimeValueStore runtimeValues;
    private final Map<ConnectionKey, Node> eventConnectionOwners;
    private final Set<Node> activeEventFunctionNodes;
    private final Map<Integer, Node> activeExecutionNodes;
    private final Map<Integer, RoutineCallFrame> routineCallFrames;
    private boolean lastSnapshotWasGlobal;
    private Integer lastStartNodeNumber;
    private String lastStartPreset;


    private static class ChainController {
        final Node startNode;
        final int rootExecutionId;
        final ChainController parentScope;
        volatile boolean cancelRequested;
        volatile Node pendingRepeatUntilExitControl;
        final AtomicInteger activeExecutions;
        final ExecutionRuntimeValueStore.Scope runtimeValueScope;
        final Map<Node, Set<Integer>> joinBarrierInputs;
        final Map<String, List<HandlerTemplate>> functionHandlerTemplates;
        final Map<Integer, Integer> executionFunctionDepths;
        final List<Node> graphNodes;
        final List<NodeConnection> graphConnections;
        final List<Node> functionSourceNodes;
        final List<NodeConnection> functionSourceConnections;
        final AtomicInteger branchBudgetWarnings;
        final AtomicInteger functionDepthWarnings;

        ChainController(Node startNode, int rootExecutionId) {
            this(startNode, rootExecutionId, null, List.of(), List.of());
        }

        ChainController(Node startNode, int rootExecutionId, List<Node> graphNodes, List<NodeConnection> graphConnections) {
            this(startNode, rootExecutionId, null, graphNodes, graphConnections);
        }

        ChainController(Node startNode, int rootExecutionId, ChainController parentScope,
                        List<Node> graphNodes, List<NodeConnection> graphConnections) {
            this.startNode = startNode;
            this.rootExecutionId = rootExecutionId;
            this.parentScope = parentScope;
            this.cancelRequested = false;
            this.pendingRepeatUntilExitControl = null;
            this.activeExecutions = new AtomicInteger(1);
            this.joinBarrierInputs = new ConcurrentHashMap<>();
            this.functionHandlerTemplates = new ConcurrentHashMap<>();
            this.executionFunctionDepths = new ConcurrentHashMap<>();
            this.executionFunctionDepths.put(rootExecutionId, 0);
            this.graphNodes = Collections.synchronizedList(new ArrayList<>(graphNodes == null ? List.of() : graphNodes));
            this.graphConnections = Collections.synchronizedList(new ArrayList<>(graphConnections == null ? List.of() : graphConnections));
            this.functionSourceNodes = Collections.synchronizedList(new ArrayList<>(graphNodes == null ? List.of() : graphNodes));
            this.functionSourceConnections = Collections.synchronizedList(new ArrayList<>(graphConnections == null ? List.of() : graphConnections));
            this.runtimeValueScope = new ExecutionRuntimeValueStore.Scope(
                startNode,
                parentScope != null ? parentScope.runtimeValueScope : null,
                this.graphNodes
            );
            this.branchBudgetWarnings = new AtomicInteger();
            this.functionDepthWarnings = new AtomicInteger();
        }
    }

    private final class RuntimeValueHost implements ExecutionRuntimeValueStore.Host {
        @Override
        public ExecutionRuntimeValueStore.Scope findScopeForStart(Node startNode) {
            ChainController controller = findChainControllerForStart(startNode);
            return controller != null ? controller.runtimeValueScope : null;
        }

        @Override
        public ExecutionRuntimeValueStore.Scope currentScope() {
            ChainController controller = resolveCurrentChainController();
            return controller != null ? controller.runtimeValueScope : null;
        }

        @Override
        public Collection<ExecutionRuntimeValueStore.Scope> activeScopes() {
            List<ExecutionRuntimeValueStore.Scope> scopes = new ArrayList<>();
            for (ChainController controller : activeChains.values()) {
                if (controller != null) {
                    scopes.add(controller.runtimeValueScope);
                }
            }
            return scopes;
        }

        @Override
        public List<Node> activeNodes() {
            return activeNodes;
        }

        @Override
        public List<Node> workspaceNodes() {
            return workspaceNodes;
        }
    }

    public static final class RuntimeVariable {
        private final NodeType type;
        private final Map<String, String> values;

        public RuntimeVariable(NodeType type, Map<String, String> values) {
            this.type = type;
            this.values = values == null ? Collections.emptyMap() : new HashMap<>(values);
        }

        public NodeType getType() {
            return type;
        }

        public Map<String, String> getValues() {
            return Collections.unmodifiableMap(values);
        }
    }

    public static final class RuntimeList {
        private final NodeType elementType;
        private final List<String> entries;

        public RuntimeList(NodeType elementType, List<String> entries) {
            this.elementType = elementType;
            this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
        }

        public NodeType getElementType() {
            return elementType;
        }

        public synchronized List<String> getEntries() {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public synchronized int size() {
            return entries.size();
        }

        public synchronized boolean isEmpty() {
            return entries.isEmpty();
        }

        public synchronized String getEntry(int index) {
            if (index < 0 || index >= entries.size()) {
                return null;
            }
            return entries.get(index);
        }

        public synchronized void addEntry(String entry) {
            if (entry == null || entry.trim().isEmpty()) {
                return;
            }
            entries.add(entry.trim());
        }

        public synchronized String removeFirstEntry() {
            return entries.isEmpty() ? null : entries.remove(0);
        }

        public synchronized String removeLastEntry() {
            return entries.isEmpty() ? null : entries.remove(entries.size() - 1);
        }

        public synchronized String removeEntry(int index) {
            if (index < 0 || index >= entries.size()) {
                return null;
            }
            return entries.remove(index);
        }
    }

    public static final class RuntimeVariableEntry {
        private final String startNodeId;
        private final String name;
        private final RuntimeVariable variable;
        private final RuntimeValueScope scope;

        public RuntimeVariableEntry(String startNodeId, String name, RuntimeVariable variable) {
            this(startNodeId, name, variable, RuntimeValueScope.CHAIN);
        }

        public RuntimeVariableEntry(String startNodeId, String name, RuntimeVariable variable, RuntimeValueScope scope) {
            this.startNodeId = startNodeId;
            this.name = name;
            this.variable = variable;
            this.scope = RuntimeValueScope.orGlobal(scope);
        }

        public String getStartNodeId() {
            return startNodeId;
        }

        public String getName() {
            return name;
        }

        public RuntimeVariable getVariable() {
            return variable;
        }

        public RuntimeValueScope getScope() {
            return scope;
        }
    }

    public static final class RuntimeListEntry {
        private final String startNodeId;
        private final String name;
        private final RuntimeList list;
        private final RuntimeValueScope scope;

        public RuntimeListEntry(String startNodeId, String name, RuntimeList list) {
            this(startNodeId, name, list, RuntimeValueScope.CHAIN);
        }

        public RuntimeListEntry(String startNodeId, String name, RuntimeList list, RuntimeValueScope scope) {
            this.startNodeId = startNodeId;
            this.name = name;
            this.list = list;
            this.scope = RuntimeValueScope.orGlobal(scope);
        }

        public String getStartNodeId() {
            return startNodeId;
        }

        public String getName() {
            return name;
        }

        public RuntimeList getList() {
            return list;
        }

        public RuntimeValueScope getScope() {
            return scope;
        }
    }

    private static final class ConnectionKey {
        private final String outputNodeId;
        private final int outputSocket;
        private final String inputNodeId;
        private final int inputSocket;

        ConnectionKey(String outputNodeId, int outputSocket, String inputNodeId, int inputSocket) {
            this.outputNodeId = outputNodeId;
            this.outputSocket = outputSocket;
            this.inputNodeId = inputNodeId;
            this.inputSocket = inputSocket;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ConnectionKey)) {
                return false;
            }
            ConnectionKey other = (ConnectionKey) obj;
            return outputSocket == other.outputSocket
                    && inputSocket == other.inputSocket
                    && Objects.equals(outputNodeId, other.outputNodeId)
                    && Objects.equals(inputNodeId, other.inputNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputNodeId, outputSocket, inputNodeId, inputSocket);
        }
    }

    private ExecutionManager() {
        this.sessionState = new ExecutionSessionState();
        this.activeNodes = new ArrayList<>();
        this.activeConnections = new ArrayList<>();
        this.workspaceNodes = new ArrayList<>();
        this.workspaceConnections = new ArrayList<>();
        this.workspaceRoutines = new ArrayList<>();
        this.cancelRequested = false;
        this.activeChains = new ConcurrentHashMap<>();
        this.controllerRoutines = new ConcurrentHashMap<>();
        this.runtimeValues = new ExecutionRuntimeValueStore(new RuntimeValueHost());
        this.lastSnapshotWasGlobal = false;
        this.activeConnectionLookup = ConcurrentHashMap.newKeySet();
        this.outputNodeLookup = new ConcurrentHashMap<>();
        this.eventConnectionOwners = new ConcurrentHashMap<>();
        this.activeEventFunctionNodes = ConcurrentHashMap.newKeySet();
        this.activeExecutionNodes = sessionState.activeExecutionNodes;
        this.routineCallFrames = new ConcurrentHashMap<>();
        this.lastStartNodeNumber = null;
        this.lastStartPreset = null;
    }
    
    public static ExecutionManager getInstance() {
        ExecutionManager result = instance;
        if (result == null) {
            synchronized (ExecutionManager.class) {
                result = instance;
                if (result == null) {
                    instance = result = new ExecutionManager();
                }
            }
        }
        return result;
    }

    public boolean setRuntimeVariable(Node startNode, String name, RuntimeVariable value) {
        return runtimeValues.setRuntimeVariable(startNode, name, value);
    }

    public boolean setRuntimeVariable(Node startNode, String name, RuntimeVariable value, RuntimeValueScope scope) {
        return runtimeValues.setRuntimeVariable(startNode, name, value, scope);
    }

    public RuntimeVariable getRuntimeVariable(Node startNode, String name) {
        return runtimeValues.getRuntimeVariable(startNode, name);
    }

    public RuntimeVariable getRuntimeVariable(Node startNode, String name, RuntimeValueScope scope) {
        return runtimeValues.getRuntimeVariable(startNode, name, scope);
    }

    public boolean setGlobalRuntimeVariable(String name, RuntimeVariable value) {
        return runtimeValues.setGlobalRuntimeVariable(name, value);
    }

    public RuntimeVariable getGlobalRuntimeVariable(String name) {
        return runtimeValues.getGlobalRuntimeVariable(name);
    }

    public boolean setRuntimeVariableForAnyActiveChain(String name, RuntimeVariable value) {
        return runtimeValues.setRuntimeVariableForAnyActiveChain(name, value);
    }

    public RuntimeVariable getRuntimeVariableFromAnyActiveChain(String name) {
        return runtimeValues.getRuntimeVariableFromAnyActiveChain(name);
    }

    public boolean triggerEventFunction(String eventName, Map<String, RuntimeVariable> runtimeVariables) {
        String normalizedEventName = normalizeEventName(eventName);
        if (normalizedEventName.isEmpty()) {
            return false;
        }

        List<EventHandlerLaunchData> handlers = resolveFunctionInvocationHandlers(normalizedEventName, null);
        if (handlers.isEmpty()) {
            return false;
        }

        boolean startFresh = activeChains.isEmpty();
        if (startFresh) {
            this.activeNodes = new ArrayList<>();
            this.activeConnections = new ArrayList<>();
        }

        this.cancelRequested = false;
        List<Node> rootNodes = new ArrayList<>();
        for (EventHandlerLaunchData handler : handlers) {
            if (handler == null || handler.rootNode == null
                || !matchesEventFunctionFilter(handler.rootNode, normalizedEventName, runtimeVariables)) {
                continue;
            }
            mergeActiveGraph(handler.branchNodes, handler.branchConnections);
            rootNodes.add(handler.rootNode);
        }

        if (rootNodes.isEmpty()) {
            return false;
        }

        rebuildConnectionState(this.activeNodes, this.activeConnections);
        if (startFresh) {
            startExecution(rootNodes, false);
        } else {
            this.sessionState.markExecuting();
        }

        for (EventHandlerLaunchData handler : handlers) {
            if (handler == null || handler.rootNode == null
                || !matchesEventFunctionFilter(handler.rootNode, normalizedEventName, runtimeVariables)) {
                continue;
            }

            int executionId = allocateExecutionId();
            ChainController controller = new ChainController(handler.rootNode, executionId,
                handler.branchNodes, handler.branchConnections);
            registerControllerRoutines(controller, workspaceRoutines);
            activeChains.put(handler.rootNode, controller);
            runtimeValues.seedRuntimeVariables(handler.rootNode, runtimeVariables);
            setEventFunctionActive(handler.rootNode, true);

            CompletableFuture<Void> chainFuture = runChain(handler.rootNode, controller, controller.rootExecutionId);
            chainFuture.whenComplete((ignored, throwable) -> {
                setEventFunctionActive(handler.rootNode, false);
                handleChainCompletion(controller, throwable, controller.rootExecutionId);
            });
        }

        return true;
    }

    private boolean matchesEventFunctionFilter(Node rootNode, String eventName, Map<String, RuntimeVariable> runtimeVariables) {
        if (rootNode == null || eventName == null || eventName.isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean setRuntimeList(Node startNode, String name, RuntimeList list) {
        return runtimeValues.setRuntimeList(startNode, name, list);
    }

    public boolean setRuntimeList(Node startNode, String name, RuntimeList list, RuntimeValueScope scope) {
        return runtimeValues.setRuntimeList(startNode, name, list, scope);
    }

    public RuntimeList getRuntimeList(Node startNode, String name) {
        return runtimeValues.getRuntimeList(startNode, name);
    }

    public RuntimeList getRuntimeList(Node startNode, String name, RuntimeValueScope scope) {
        return runtimeValues.getRuntimeList(startNode, name, scope);
    }

    public boolean setGlobalRuntimeList(String name, RuntimeList list) {
        return runtimeValues.setGlobalRuntimeList(name, list);
    }

    public RuntimeList getGlobalRuntimeList(String name) {
        return runtimeValues.getGlobalRuntimeList(name);
    }

    /** Resolves a list operation's scope from the matching Create List declaration. */
    public RuntimeValueScope resolveRuntimeListScope(Node startNode, String name, RuntimeValueScope fallback) {
        return runtimeValues.resolveRuntimeListScope(startNode, name, fallback);
    }


    public List<RuntimeVariableEntry> getRuntimeVariableEntries() {
        return runtimeValues.getRuntimeVariableEntries();
    }

    public List<RuntimeListEntry> getRuntimeListEntries() {
        return runtimeValues.getRuntimeListEntries();
    }


    public Set<String> getKnownRuntimeVariableNames() {
        return runtimeValues.getKnownRuntimeVariableNames();
    }

    private CompletableFuture<Void> runLoopAttachedAction(
        Node controlNode,
        Node actionNode,
        ChainController controller,
        int executionId,
        Node repeatUntilGuard,
        LoopContinuation continuation
    ) {
        CompletableFuture<Void> loopDone = new CompletableFuture<>();
        runLoopAttachedActionIteration(
            controlNode,
            actionNode,
            controller,
            executionId,
            repeatUntilGuard,
            continuation,
            loopDone
        );
        return loopDone.whenComplete((ignored, throwable) -> controlNode.clearLoopRuntimeState());
    }

    private void runLoopAttachedActionIteration(
        Node controlNode,
        Node actionNode,
        ChainController controller,
        int executionId,
        Node repeatUntilGuard,
        LoopContinuation continuation,
        CompletableFuture<Void> loopDone
    ) {
        if (loopDone.isDone()) {
            return;
        }
        if (cancelRequested || controller == null || controller.cancelRequested) {
            loopDone.complete(null);
            return;
        }

        Node guard = continuation.guardForIteration(controlNode, repeatUntilGuard);
        if (!continuation.shouldRunNextIteration(controlNode, controller)) {
            loopDone.complete(null);
            return;
        }

        runChain(actionNode, controller, executionId, guard).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                loopDone.completeExceptionally(throwable);
                return;
            }
            runLoopAttachedActionIteration(
                controlNode,
                actionNode,
                controller,
                executionId,
                repeatUntilGuard,
                continuation,
                loopDone
            );
        });
    }

    private interface LoopContinuation {
        boolean shouldRunNextIteration(Node controlNode, ChainController controller);

        default Node guardForIteration(Node controlNode, Node repeatUntilGuard) {
            return repeatUntilGuard;
        }
    }

    public void executeGraph(List<Node> nodes, List<NodeConnection> connections) {
        executeGraphInternal(nodes, connections, true);
    }

    private boolean executeGraphInternal(List<Node> nodes, List<NodeConnection> connections, boolean markGlobalSnapshot) {
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute graph - missing nodes or connections");
            return false;
        }
        logValidationErrors(nodes, connections, PresetManager.getActivePreset(), "workspace");

        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<Node> startNodes = findStartNodes(nodes);
        if (startNodes.isEmpty()) {
            LOGGER.warn("No START nodes found");
            return false;
        }

        // Track the last started chain by START node number so the keybind can replay it.
        updateLastStartContext(startNodes.get(startNodes.size() - 1), PresetManager.getActivePreset());

        // Ensure Baritone isn't still executing stale goals from a previous session
        cancelAllNavigationCommands();

        List<NodeConnection> filteredConnections = filterConnections(connections);

        NodeGraphData snapshot = createGraphSnapshot(nodes, filteredConnections);
        this.lastExecutedGraph = snapshot;
        if (markGlobalSnapshot) {
            this.lastGlobalGraph = snapshot;
        }
        this.lastSnapshotWasGlobal = markGlobalSnapshot;
        this.activeNodes = new ArrayList<>();
        this.activeConnections = new ArrayList<>();
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        activeChains.clear();
        controllerRoutines.clear();

        List<BranchLaunchData> launchDatas = new ArrayList<>();
        List<Node> isolatedStartNodes = new ArrayList<>();
        for (Node startNode : startNodes) {
            BranchData branchData = buildBranchData(startNode, nodes, filteredConnections);
            BranchLaunchData launchData = createBranchLaunchData(branchData, startNode.getStartNodeNumber());
            if (launchData == null) {
                LOGGER.debug("Skipping START node {} because its branch could not be prepared", startNode.getStartNodeNumber());
                continue;
            }
            mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);
            launchDatas.add(launchData);
            isolatedStartNodes.add(launchData.rootNode);
        }

        if (isolatedStartNodes.isEmpty()) {
            LOGGER.warn("No START branches could be prepared for execution");
            return false;
        }

        startExecution(isolatedStartNodes, markGlobalSnapshot);

        for (BranchLaunchData launchData : launchDatas) {
            Node isolatedStartNode = launchData.rootNode;
            int executionId = allocateExecutionId();
            ChainController controller = new ChainController(isolatedStartNode, executionId,
                launchData.branchData.nodes, launchData.branchData.connections);
            registerControllerRoutines(controller, workspaceRoutines);
            activeChains.put(isolatedStartNode, controller);
            CompletableFuture<Void> chainFuture = runChain(isolatedStartNode, controller, controller.rootExecutionId);
            chainFuture.whenComplete((ignored, throwable) ->
                handleChainCompletion(controller, throwable, controller.rootExecutionId));
        }
        return true;
    }

    public void replayLastGraph() {
        if (playLastStartNodeGraphFromWorkspace()) {
            return;
        }

        if (lastExecutedGraph == null) {
            LOGGER.debug("No previously executed node graph to replay");
            return;
        }

        if (!executeGraphSnapshot(lastExecutedGraph, lastSnapshotWasGlobal)) {
            LOGGER.debug("No nodes available to replay");
        }
    }

    public void playAllGraphs() {
        playAllGraphsWithResult();
    }

    public PlayAllResult playAllGraphsWithResult() {
        boolean graphAvailable = false;
        if (workspaceNodes != null && !workspaceNodes.isEmpty() && workspaceConnections != null) {
            return executeGraphInternal(workspaceNodes, workspaceConnections, true)
                ? PlayAllResult.STARTED
                : PlayAllResult.NO_START_NODE;
        }

        if (lastGlobalGraph != null && executeGraphSnapshot(lastGlobalGraph, true)) {
            return PlayAllResult.STARTED;
        }
        graphAvailable |= lastGlobalGraph != null;

        if (lastExecutedGraph != null && executeGraphSnapshot(lastExecutedGraph, lastSnapshotWasGlobal)) {
            return PlayAllResult.STARTED;
        }
        graphAvailable |= lastExecutedGraph != null;

        LOGGER.debug("No workspace graph available for keybind launch");
        return graphAvailable ? PlayAllResult.NO_START_NODE : PlayAllResult.NO_GRAPH;
    }

    public enum PlayAllResult {
        STARTED,
        NO_GRAPH,
        NO_START_NODE
    }

    public boolean executeBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        return executeBranch(startNode, nodes, connections, PresetManager.getActivePreset());
    }

    public boolean executeFromNode(Node node, List<Node> nodes, List<NodeConnection> connections) {
        return executeFromNode(node, nodes, connections, PresetManager.getActivePreset());
    }

    public boolean executeBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections, String presetName) {
        if (startNode == null || startNode.getType() != NodeType.START) {
            LOGGER.warn("Cannot execute branch - invalid START node");
            return false;
        }
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute branch - missing nodes or connections");
            return false;
        }
        logValidationErrors(nodes, connections, presetName, "START branch");
        if (isChainActive(startNode)) {
            LOGGER.debug("START node already executing, ignoring branch start request");
            return false;
        }

        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<NodeConnection> filteredConnections = filterConnections(connections);
        Set<Node> branchNodeSet = collectBranchNodes(startNode, filteredConnections);

        for (Node node : nodes) {
            if (node.getType() == NodeType.EVENT_FUNCTION) {
                branchNodeSet.addAll(collectBranchNodes(node, filteredConnections));
            }
        }

        List<Node> branchNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (branchNodeSet.contains(node)) {
                branchNodes.add(node);
            }
        }

        List<NodeConnection> branchConnections = new ArrayList<>();
        for (NodeConnection connection : filteredConnections) {
            if (branchNodeSet.contains(connection.getOutputNode()) && branchNodeSet.contains(connection.getInputNode())) {
                branchConnections.add(connection);
            }
        }

        this.lastExecutedGraph = createGraphSnapshot(branchNodes, branchConnections);
        this.lastSnapshotWasGlobal = false;
        BranchLaunchData launchData = createBranchLaunchData(new BranchData(branchNodes, branchConnections), startNode.getStartNodeNumber());
        if (launchData == null) {
            LOGGER.debug("START branch could not be cloned for execution");
            return false;
        }
        this.activeNodes = launchData.branchData.nodes;
        this.activeConnections = launchData.branchData.connections;
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        if (activeChains.isEmpty()) {
            startExecution(Collections.singletonList(launchData.rootNode), false);
        } else {
            this.sessionState.markExecuting();
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId,
            launchData.branchData.nodes, launchData.branchData.connections);
        registerControllerRoutines(controller, workspaceRoutines);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        updateLastStartContext(startNode, presetName);
        return true;
    }

    public boolean executeFromNode(Node node, List<Node> nodes, List<NodeConnection> connections, String presetName) {
        if (node == null) {
            LOGGER.warn("Cannot execute node - missing root node");
            return false;
        }
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute node - missing nodes or connections");
            return false;
        }

        logValidationErrors(nodes, connections, presetName, "node run");
        if (isChainActive(node)) {
            LOGGER.debug("Node is already executing, ignoring node run request");
            return false;
        }

        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<NodeConnection> filteredConnections = filterConnections(connections);
        BranchData branchData = buildBranchData(node, nodes, filteredConnections);
        if (branchData == null || branchData.nodes.isEmpty()) {
            LOGGER.debug("Node {} has no executable branch", node.getId());
            return false;
        }

        this.lastExecutedGraph = createGraphSnapshot(branchData.nodes, branchData.connections);
        this.lastSnapshotWasGlobal = false;
        BranchLaunchData launchData = createBranchLaunchData(branchData, node);
        if (launchData == null) {
            LOGGER.debug("Node branch could not be cloned for execution");
            return false;
        }

        this.cancelRequested = false;
        if (activeChains.isEmpty()) {
            this.activeNodes = launchData.branchData.nodes;
            this.activeConnections = launchData.branchData.connections;
            rebuildConnectionState(this.activeNodes, this.activeConnections);
            startExecution(Collections.singletonList(launchData.rootNode), false);
        } else {
            mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);
            this.sessionState.markExecuting();
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId,
            launchData.branchData.nodes, launchData.branchData.connections);
        registerControllerRoutines(controller, workspaceRoutines);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        return true;
    }

    /**
     * Start a branch from an externally supplied graph (for example a Run Preset node) without
     * replacing the currently active graph state for other running chains.
     */
    public boolean executeExternalBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections, String presetName) {
        return startExternalBranch(startNode, nodes, connections, presetName, workspaceRoutines) != null;
    }

    public boolean executeExternalBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections,
                                         String presetName, List<NodeGraphData.RoutineDefinitionData> routines) {
        return startExternalBranch(startNode, nodes, connections, presetName, routines) != null;
    }

    /** Executes one routine as a standalone preview using the same call path as a routine node. */
    public boolean executeRoutine(NodeGraphData.RoutineDefinitionData routine,
                                  List<NodeGraphData.RoutineDefinitionData> routines,
                                  String presetName) {
        return executeRoutineAndWait(routine, routines, presetName) != null;
    }

    public CompletableFuture<Void> executeRoutineAndWait(NodeGraphData.RoutineDefinitionData routine,
                                                         List<NodeGraphData.RoutineDefinitionData> routines,
                                                         String presetName) {
        if (routine == null || routine.getId() == null || routine.getId().isBlank()) {
            LOGGER.warn("Cannot execute routine preview - missing routine definition");
            return null;
        }
        Node start = new Node(NodeType.START, 0, 0);
        start.setStartNodeNumber(1);
        Node invocation = Node.createRoutineCall(routine, 160, 0);
        return executeExternalBranchAndWait(
            start,
            List.of(start, invocation),
            List.of(new NodeConnection(start, invocation, 0, 0)),
            presetName,
            routines
        );
    }

    public CompletableFuture<Void> executeExternalBranchAndWait(Node startNode, List<Node> nodes,
                                                                List<NodeConnection> connections, String presetName) {
        return startExternalBranch(startNode, nodes, connections, presetName, workspaceRoutines);
    }

    public CompletableFuture<Void> executeExternalBranchAndWait(Node startNode, List<Node> nodes,
                                                                List<NodeConnection> connections, String presetName,
                                                                List<NodeGraphData.RoutineDefinitionData> routines) {
        return startExternalBranch(startNode, nodes, connections, presetName, routines);
    }

    private CompletableFuture<Void> startExternalBranch(Node startNode, List<Node> nodes,
                                                        List<NodeConnection> connections, String presetName,
                                                        List<NodeGraphData.RoutineDefinitionData> routines) {
        if (startNode == null || startNode.getType() != NodeType.START) {
            LOGGER.warn("Cannot execute external branch - invalid START node");
            return null;
        }
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute external branch - missing nodes or connections");
            return null;
        }
        logValidationErrors(nodes, connections, presetName, "preset \"" + presetName + "\"");

        // Preserve this graph for Activate-node lookups within the launched preset chain.
        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<NodeConnection> filteredConnections = filterConnections(connections);
        BranchData branchData = buildBranchData(startNode, nodes, filteredConnections);
        if (branchData == null || branchData.nodes.isEmpty()) {
            LOGGER.debug("External START node has no executable branch");
            return null;
        }

        this.lastExecutedGraph = createGraphSnapshot(branchData.nodes, branchData.connections);
        this.lastSnapshotWasGlobal = false;
        BranchLaunchData launchData = createBranchLaunchData(branchData, startNode.getStartNodeNumber());
        if (launchData == null) {
            LOGGER.debug("External START node branch could not be cloned for execution");
            return null;
        }
        this.cancelRequested = false;
        mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);

        if (!sessionState.isActivelyExecuting() && activeChains.isEmpty()) {
            startExecution(Collections.singletonList(launchData.rootNode), false);
        } else {
            this.sessionState.markExecuting();
        }

        int executionId = allocateExecutionId();
        ChainController parentController = resolveCurrentChainController();
        ChainController controller = new ChainController(launchData.rootNode, executionId, parentController,
            launchData.branchData.nodes, launchData.branchData.connections);
        registerControllerRoutines(controller, routines);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        updateLastStartContext(startNode, presetName);
        return chainFuture;
    }

    private Map<String, String> buildRoutineDefaultValueMap(NodeType nodeType, String rawValue) {
        Map<String, String> values = new HashMap<>();
        String safeValue = rawValue == null ? "" : rawValue.trim();
        switch (nodeType) {
            case PARAM_COORDINATE -> {
                String[] parts = safeValue.isEmpty() ? new String[0] : safeValue.split("\\s*,\\s*|\\s+");
                putRuntimeValue(values, "X", parts.length > 0 ? parts[0] : "0");
                putRuntimeValue(values, "Y", parts.length > 1 ? parts[1] : "64");
                putRuntimeValue(values, "Z", parts.length > 2 ? parts[2] : "0");
            }
            case PARAM_BLOCK -> putRuntimeValue(values, "Block", safeValue);
            case PARAM_ITEM -> putRuntimeValue(values, "Item", safeValue);
            case PARAM_VILLAGER_TRADE -> putRuntimeValue(values, "Item", safeValue);
            case PARAM_ENTITY -> putRuntimeValue(values, "Entity", safeValue);
            case PARAM_PLAYER -> putRuntimeValue(values, "Player", safeValue);
            case PARAM_MESSAGE -> putRuntimeValue(values, "Text", safeValue);
            case PARAM_WAYPOINT -> putRuntimeValue(values, "Waypoint", safeValue);
            case PARAM_SCHEMATIC -> putRuntimeValue(values, "Schematic", safeValue);
            case PARAM_INVENTORY_SLOT -> putRuntimeValue(values, "Slot", safeValue.isEmpty() ? "0" : safeValue);
            case PARAM_DURATION -> putRuntimeValue(values, "Duration", safeValue.isEmpty() ? "0.0" : safeValue);
            case PARAM_AMOUNT -> putRuntimeValue(values, "Amount", safeValue.isEmpty() ? "0.0" : safeValue);
            case PARAM_BOOLEAN -> {
                putRuntimeValue(values, "Mode", "literal");
                putRuntimeValue(values, "Toggle", Boolean.toString(Boolean.parseBoolean(safeValue)));
                putRuntimeValue(values, "Variable", "");
            }
            case PARAM_HAND -> putRuntimeValue(values, "Hand", safeValue.isEmpty() ? "main" : safeValue);
            case PARAM_GUI -> putRuntimeValue(values, "GUI", safeValue);
            case PARAM_KEY -> putRuntimeValue(values, "Key", safeValue);
            case PARAM_MOUSE_BUTTON -> putRuntimeValue(values, "MouseButton", safeValue);
            case PARAM_RANGE -> putRuntimeValue(values, "Range", safeValue.isEmpty() ? "0" : safeValue);
            case PARAM_DISTANCE -> putRuntimeValue(values, "Distance", safeValue.isEmpty() ? "0.0" : safeValue);
            case PARAM_DIRECTION -> putRuntimeValue(values, "Direction", safeValue);
            case PARAM_BLOCK_FACE -> putRuntimeValue(values, "Face", safeValue);
            case PARAM_ROTATION -> {
                String[] parts = safeValue.isEmpty() ? new String[0] : safeValue.split("\\s*,\\s*|\\s+");
                putRuntimeValue(values, "Yaw", parts.length > 0 ? parts[0] : "0.0");
                putRuntimeValue(values, "Pitch", parts.length > 1 ? parts[1] : "0.0");
            }
            default -> putRuntimeValue(values, "Text", safeValue);
        }
        return values;
    }

    private void putRuntimeValue(Map<String, String> values, String key, String value) {
        if (values == null || key == null) {
            return;
        }
        String safeValue = value == null ? "" : value;
        values.put(key, safeValue);
        values.put(normalizeRuntimeValueKey(key), safeValue);
    }

    private String normalizeRuntimeValueKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    
    /**
     * Start execution with the given start node
     */
    private void startExecution(List<Node> startNodes, boolean markGlobal) {
        runtimeValues.clear();
        this.routineCallFrames.clear();
        sessionState.start(startNodes, markGlobal);
        this.cancelRequested = false;
        if (!startNodes.isEmpty()) {
            LOGGER.debug("Started execution with {} start node(s)", startNodes.size());
        } else {
            LOGGER.debug("Started execution without any root nodes");
        }
    }
    
    /**
     * Set the currently active node
     */
    public void setActiveNode(Node node) {
        sessionState.setActiveNode(node);
    }

    private void setActiveNode(Node node, int executionId) {
        sessionState.setActiveNode(node, executionId);
    }
    
    /**
     * Stop execution
     */
    public void stopExecution() {
        LOGGER.debug("Stopping execution");
        sessionState.stop(cancelRequested);
        if (cancelRequested) {
            cancelRequested = false;
        }
        // Keep activeNode for minimum display duration
    }

    /**
     * Request that all executing node chains stop immediately.
     */
    public void requestStopAll() {
        cancelAllNavigationCommands();

        if (!sessionState.isActivelyExecuting() && sessionState.getActiveNode() == null && activeChains.isEmpty()) {
            runtimeValues.clear();
            routineCallFrames.clear();
            return;
        }

        LOGGER.debug("Stop requested for all node trees");
        cancelRequested = true;
        for (ChainController controller : activeChains.values()) {
            controller.cancelRequested = true;
        }
        this.sessionState.reset();
        this.activeNodes.clear();
        this.activeConnections.clear();
        this.activeConnectionLookup.clear();
        this.outputNodeLookup.clear();
        this.eventConnectionOwners.clear();
        this.activeEventFunctionNodes.clear();
        this.activeChains.clear();
        this.controllerRoutines.clear();
        this.runtimeValues.clear();
        this.routineCallFrames.clear();
    }

    private void cancelAllBaritoneCommands() {
        PreciseCompletionTracker.getInstance().cancelAllTasks();

        try {
            Object baritone = BaritoneApiProxy.getPrimaryBaritone();
            if (baritone == null) {
                return;
            }

            Object pathingBehavior = BaritoneApiProxy.getPathingBehavior(baritone);
            if (pathingBehavior != null) {
                BaritoneApiProxy.cancelEverything(pathingBehavior);
            }

            Object goalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
            if (goalProcess != null) {
                BaritoneApiProxy.setGoal(goalProcess, null);
                BaritoneApiProxy.onLostControl(goalProcess);
            }

            Object mineProcess = BaritoneApiProxy.getMineProcess(baritone);
            if (mineProcess != null) {
                BaritoneApiProxy.cancelMine(mineProcess);
            }

            Object exploreProcess = BaritoneApiProxy.getExploreProcess(baritone);
            if (exploreProcess != null && BaritoneApiProxy.isProcessActive(exploreProcess)) {
                BaritoneApiProxy.onLostControl(exploreProcess);
            }

            Object farmProcess = BaritoneApiProxy.getFarmProcess(baritone);
            if (farmProcess != null && BaritoneApiProxy.isProcessActive(farmProcess)) {
                BaritoneApiProxy.onLostControl(farmProcess);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to cancel Baritone processes: {}", e.getMessage());
        }
    }

    private void cancelAllNavigationCommands() {
        cancelAllBaritoneCommands();
        PathmindNavigator.getInstance().stop("execution manager stop");
    }
    
    /**
     * Get the currently active node
     */
    public Node getActiveNode() {
        return sessionState.getActiveNode();
    }

    public boolean isExecutionActiveOnNode(Integer executionId, String nodeId) {
        return sessionState.isExecutionActiveOnNode(executionId, nodeId);
    }

    public long getExecutionNodeDuration(Integer executionId) {
        return sessionState.getExecutionNodeDuration(executionId);
    }

    public Integer getCurrentExecutionId() {
        return sessionState.getCurrentExecutionId();
    }

    private ChainController resolveCurrentChainController() {
        Integer executionId = sessionState.getCurrentExecutionId();
        if (executionId == null || executionId < 0) {
            return null;
        }
        Node activeExecutionNode = sessionState.getActiveExecutionNode(executionId);
        if (activeExecutionNode == null) {
            return null;
        }
        return findChainControllerForStart(activeExecutionNode.getOwningStartNode());
    }

    public boolean isStopRequested() {
        return cancelRequested;
    }

    public void runWithExecutionContext(int executionId, Runnable runnable) {
        sessionState.runWithExecutionContext(executionId, runnable);
    }

    public List<Node> getActiveNodeChainSnapshot() {
        return sessionState.getActiveNodeChainSnapshot();
    }

    public boolean requestStopForStart(Node startNode) {
        if (startNode == null) {
            return false;
        }

        ChainController controller = findChainControllerForStart(startNode);
        if (controller == null) {
            LOGGER.debug("No active chain found for requested START node stop");
            return false;
        }

        cancelAllNavigationCommands();
        controller.cancelRequested = true;
        LOGGER.debug("Stop requested for START node {}", startNode.getId());
        return true;
    }

    public boolean requestStopForStartNumber(int startNodeNumber) {
        if (startNodeNumber <= 0) {
            return false;
        }
        Node match = null;
        for (Node startNode : activeChains.keySet()) {
            if (startNode != null && startNode.getStartNodeNumber() == startNodeNumber) {
                match = startNode;
                break;
            }
        }
        if (match == null) {
            LOGGER.debug("No START node found for number {}", startNodeNumber);
            return false;
        }
        return requestStopForStart(match);
    }

    public boolean requestStartForStartNumber(int startNodeNumber) {
        if (startNodeNumber <= 0) {
            return false;
        }
        if (workspaceNodes == null || workspaceNodes.isEmpty() || workspaceConnections == null) {
            LOGGER.debug("No workspace graph available to start START node {}", startNodeNumber);
            return false;
        }
        logValidationErrors(workspaceNodes, workspaceConnections, PresetManager.getActivePreset(),
            "workspace START #" + startNodeNumber);

        Node match = findWorkspaceStartNode(startNodeNumber);

        if (match == null) {
            LOGGER.debug("No START node found for number {}", startNodeNumber);
            return false;
        }
        if (isStartNumberActive(startNodeNumber)) {
            LOGGER.debug("START node already executing, ignoring start request");
            return false;
        }

        List<NodeConnection> filteredConnections = filterConnections(workspaceConnections);
        BranchData branchData = buildBranchData(match, workspaceNodes, filteredConnections);
        if (branchData == null || branchData.nodes.isEmpty()) {
            LOGGER.debug("START node {} has no executable branch", startNodeNumber);
            return false;
        }

        BranchLaunchData launchData = createBranchLaunchData(branchData, startNodeNumber);
        if (launchData == null) {
            LOGGER.debug("Cloned branch for START node {} is missing its START node", startNodeNumber);
            return false;
        }

        mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);

        if (!sessionState.isActivelyExecuting() && activeChains.isEmpty()) {
            startExecution(Collections.singletonList(launchData.rootNode), sessionState.isGlobalExecutionActive());
        } else {
            this.sessionState.markExecuting();
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId,
            launchData.branchData.nodes, launchData.branchData.connections);
        registerControllerRoutines(controller, workspaceRoutines);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        return true;
    }

    public void setWorkspaceGraph(List<Node> nodes, List<NodeConnection> connections) {
        setWorkspaceGraph(nodes, connections, workspaceRoutines);
    }

    public void setWorkspaceGraph(List<Node> nodes, List<NodeConnection> connections,
                                  List<NodeGraphData.RoutineDefinitionData> routines) {
        if (nodes == null || connections == null) {
            return;
        }
        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);
        this.workspaceRoutines = new ArrayList<>(routines == null ? List.of() : routines);
    }

    public boolean isChainActive(Node startNode) {
        if (startNode == null) {
            return false;
        }
        ChainController controller = findChainControllerForStart(startNode);
        return controller != null && !controller.cancelRequested;
    }

    private ChainController findChainControllerForStart(Node startNode) {
        if (startNode == null) {
            return null;
        }

        ChainController direct = activeChains.get(startNode);
        if (direct != null) {
            return direct;
        }

        int startNumber = startNode.getStartNodeNumber();
        if (startNumber > 0) {
            for (Map.Entry<Node, ChainController> entry : activeChains.entrySet()) {
                Node activeStart = entry.getKey();
                ChainController controller = entry.getValue();
                if (activeStart != null
                    && controller != null
                    && activeStart.getStartNodeNumber() == startNumber) {
                    return controller;
                }
            }
        }

        String startId = startNode.getId();
        if (startId != null && !startId.isEmpty()) {
            for (Map.Entry<Node, ChainController> entry : activeChains.entrySet()) {
                Node activeStart = entry.getKey();
                ChainController controller = entry.getValue();
                if (activeStart != null
                    && controller != null
                    && startId.equals(activeStart.getId())) {
                    return controller;
                }
            }
        }

        return null;
    }

    private String getNodeParameterValue(Node node, String key) {
        if (node == null || key == null || key.isEmpty()) {
            return "";
        }
        NodeParameter parameter = node.getParameter(key);
        String value = parameter != null ? parameter.getStringValue() : null;
        if ((value == null || value.isBlank())) {
            Map<String, String> exported = node.exportParameterValues();
            if (exported != null) {
                value = exported.get(key);
                if (value == null) {
                    value = exported.get(normalizeRuntimeValueKey(key));
                }
            }
        }
        return value == null ? "" : value.trim();
    }

    private String getRuntimeValue(RuntimeVariable variable, String key) {
        if (variable == null || key == null || key.isEmpty()) {
            return "";
        }
        Map<String, String> values = variable.getValues();
        if (values == null || values.isEmpty()) {
            return "";
        }
        String direct = values.get(key);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        String normalized = normalizeRuntimeValueKey(key);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (!normalizeRuntimeValueKey(entry.getKey()).equals(normalized)) {
                continue;
            }
            String candidate = entry.getValue();
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private boolean isStartNumberActive(int startNodeNumber) {
        if (startNodeNumber <= 0) {
            return false;
        }
        for (Map.Entry<Node, ChainController> entry : activeChains.entrySet()) {
            Node startNode = entry.getKey();
            ChainController controller = entry.getValue();
            if (startNode != null
                && controller != null
                && !controller.cancelRequested
                && startNode.getStartNodeNumber() == startNodeNumber) {
                return true;
            }
        }
        return false;
    }

    private int resolvePlayableStartNumber() {
        if (lastStartNodeNumber != null && lastStartNodeNumber > 0) {
            return lastStartNodeNumber;
        }
        if (workspaceNodes == null) {
            return -1;
        }
        for (Node node : workspaceNodes) {
            if (node != null && node.getType() == NodeType.START && node.getStartNodeNumber() > 0) {
                return node.getStartNodeNumber();
            }
        }
        return -1;
    }
    
    /**
     * Check if execution is currently running or should still be displayed
     */
    public boolean isExecuting() {
        return sessionState.isExecuting();
    }

    /**
     * Returns true when the overlay should show completion state messaging.
     */
    public boolean isDisplayingCompletion() {
        return sessionState.isDisplayingCompletion();
    }

    public boolean isGlobalExecutionActive() {
        return sessionState.isGlobalExecutionActive();
    }
    
    /**
     * Get the execution start time
     */
    public long getExecutionStartTime() {
        return sessionState.getExecutionStartTime();
    }
    
    /**
     * Get the current execution duration in milliseconds
     */
    public long getExecutionDuration() {
        return sessionState.getExecutionDuration();
    }

    /**
     * Get the elapsed duration for the currently active node in milliseconds.
     */
    public long getActiveNodeDuration() {
        return sessionState.getActiveNodeDuration();
    }

    /**
     * Update whether Pathmind execution should be paused so node timers and polling can be frozen.
     */
    public void setSingleplayerPaused(boolean paused) {
        sessionState.setSingleplayerPaused(paused);
    }

    public boolean isExecutionPaused() {
        return sessionState.isExecutionPaused();
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller, int executionId) {
        return runChain(currentNode, controller, executionId, null, -1);
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller, int executionId, Node repeatUntilGuard) {
        return runChain(currentNode, controller, executionId, repeatUntilGuard, -1);
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller, int executionId,
                                             Node repeatUntilGuard, int arrivalInputSocket) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }
        if (shouldExitRepeatUntilGuard(currentNode, controller, repeatUntilGuard)) {
            return CompletableFuture.completedFuture(null);
        }

        currentNode.setOwningStartNode(controller.startNode);
        currentNode.setActiveRepeatUntilGuard(repeatUntilGuard);

        return waitForExecutionResume()
            .thenCompose(ignored -> scheduleNodeStartDelay())
            .thenCompose(ignored -> {
                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }
                if (shouldExitRepeatUntilGuard(currentNode, controller, repeatUntilGuard)) {
                    return CompletableFuture.completedFuture(null);
                }

                setActiveNode(currentNode, executionId);

                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }

                return waitForExecutionResume()
                    .thenCompose(pausedIgnored -> {
                        if (shouldExitRepeatUntilGuard(currentNode, controller, repeatUntilGuard)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(pausedIgnored -> executeNodeWithRepeatUntilGuard(
                        currentNode, controller, executionId, repeatUntilGuard))
                    .thenCompose(ignoredFuture -> handleRoutineCallIfNeeded(currentNode, controller, executionId, repeatUntilGuard))
                    .thenCompose(ignoredFuture -> {
                        if (cancelRequested || controller.cancelRequested) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return handleEventCallIfNeeded(currentNode, controller, executionId, repeatUntilGuard);
                    })
                    .thenCompose(ignoredFuture -> waitForExecutionResume())
                    .thenCompose(ignoredFuture -> continueFromNode(currentNode, controller, executionId, repeatUntilGuard, arrivalInputSocket));
            });
    }

    private CompletableFuture<Void> handleRoutineCallIfNeeded(Node invocation, ChainController controller,
                                                               int parentExecutionId, Node repeatUntilGuard) {
        if (cancelRequested || controller == null || controller.cancelRequested
            || invocation == null || invocation.getType() != NodeType.ROUTINE_CALL) {
            return CompletableFuture.completedFuture(null);
        }
        NodeGraphData.RoutineDefinitionData definition = findRoutineDefinition(controller, invocation.getRoutineId());
        if (definition == null || definition.getGraph() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Routine definition is missing for " + invocation.getDisplayName().getString()));
        }

        Map<String, RuntimeVariable> inputs = captureRoutineInputs(invocation, definition, parentExecutionId);
        if (inputs == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Routine " + definition.getName() + " has missing or invalid inputs."));
        }

        int routineExecutionId = allocateExecutionId();
        if (!retainChainExecution(controller, routineExecutionId, parentExecutionId, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Routine call depth exceeded " + MAX_ROUTINE_CALL_DEPTH + ". Check for runaway recursion."));
        }

        RoutineCallFrame frame = new RoutineCallFrame(
            routineExecutionId, parentExecutionId, definition.getId(), inputs);
        routineCallFrames.put(routineExecutionId, frame);
        BranchData routineBranch = createRoutineBranch(controller, definition, frame);
        if (routineBranch == null || routineBranch.nodes.isEmpty()) {
            routineCallFrames.remove(routineExecutionId);
            handleChainCompletion(controller, null, routineExecutionId);
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Routine " + definition.getName() + " has no definition entry."));
        }
        Node entry = routineBranch.nodes.stream()
            .filter(node -> node != null && node.getType() == NodeType.ROUTINE_ENTRY)
            .findFirst().orElse(null);
        if (entry == null) {
            routineCallFrames.remove(routineExecutionId);
            handleChainCompletion(controller, null, routineExecutionId);
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Routine " + definition.getName() + " has no definition entry."));
        }

        mergeControllerGraph(controller, routineBranch.nodes, routineBranch.connections);
        mergeActiveGraph(routineBranch.nodes, routineBranch.connections);
        CompletableFuture<Void> routineFuture = runChain(entry, controller, routineExecutionId, repeatUntilGuard);
        routineFuture.whenComplete((ignored, throwable) -> {
            routineCallFrames.remove(routineExecutionId);
            removeControllerGraph(controller, routineBranch.nodes, routineBranch.connections);
            handleChainCompletion(controller, throwable, routineExecutionId);
        });
        return deferCompletion(routineFuture);
    }

    private NodeGraphData.RoutineDefinitionData findRoutineDefinition(ChainController controller, String routineId) {
        if (routineId == null || routineId.isBlank()) return null;
        List<NodeGraphData.RoutineDefinitionData> routines = controllerRoutines.getOrDefault(
            controller, workspaceRoutines == null ? List.of() : workspaceRoutines);
        for (NodeGraphData.RoutineDefinitionData routine : routines) {
            if (routine != null && routineId.equals(routine.getId())) return routine;
        }
        return null;
    }

    private void registerControllerRoutines(ChainController controller,
                                            List<NodeGraphData.RoutineDefinitionData> routines) {
        if (controller == null) return;
        controllerRoutines.put(controller, List.copyOf(routines == null ? List.of() : routines));
    }

    Map<String, RuntimeVariable> captureRoutineInputs(Node invocation,
                                                      NodeGraphData.RoutineDefinitionData definition,
                                                      int executionId) {
        Map<String, RuntimeVariable> inputs = new HashMap<>();
        for (NodeGraphData.RoutineInputData input : definition.getInputs()) {
            if (input == null || input.getId() == null || input.getId().isBlank()) continue;
            int slot = invocation.getRoutineSlotForInputId(input.getId());
            RuntimeVariable value = slot >= 0 ? invocation.captureAttachedRuntimeValue(slot, executionId) : null;
            if (value == null && input.getDefaultValue() != null && !input.getDefaultValue().isBlank()) {
                value = createRoutineDefaultValue(input);
            }
            if (value == null && Boolean.TRUE.equals(input.getRequired())) return null;
            if (value != null) inputs.put(input.getId(), value);
        }
        return inputs;
    }

    private RuntimeVariable createRoutineDefaultValue(NodeGraphData.RoutineInputData input) {
        RoutineValueKind kind = RoutineValueKind.fromSerialized(input.getValueKind());
        String raw = input.getDefaultValue() == null ? "" : input.getDefaultValue();
        NodeType type = switch (kind) {
            case NUMBER -> NodeType.PARAM_AMOUNT;
            case BOOLEAN -> NodeType.PARAM_BOOLEAN;
            case BLOCK -> NodeType.PARAM_BLOCK;
            case ITEM -> NodeType.PARAM_ITEM;
            case COORDINATE -> NodeType.PARAM_COORDINATE;
            case PLAYER -> NodeType.PARAM_PLAYER;
            case ENTITY -> NodeType.PARAM_ENTITY;
            case ROTATION -> NodeType.PARAM_ROTATION;
            case DIRECTION -> NodeType.PARAM_DIRECTION;
            case DURATION -> NodeType.PARAM_DURATION;
            case INVENTORY_SLOT -> NodeType.PARAM_INVENTORY_SLOT;
            case GUI -> NodeType.PARAM_GUI;
            default -> NodeType.PARAM_MESSAGE;
        };
        return new RuntimeVariable(type, buildRoutineDefaultValueMap(type, raw));
    }

    private BranchData createRoutineBranch(ChainController controller,
                                           NodeGraphData.RoutineDefinitionData definition, RoutineCallFrame frame) {
        List<Node> nodes = NodeGraphPersistence.convertToNodes(definition.getGraph());
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) if (node != null) nodeMap.put(node.getId(), node);
        List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(definition.getGraph(), nodeMap);

        for (Node node : nodes) {
            if (node == null || node.getType() != NodeType.ROUTINE_CALL) continue;
            NodeGraphData.RoutineDefinitionData nested = findRoutineDefinition(controller, node.getRoutineId());
            if (nested != null) node.syncRoutineCallDefinition(nested);
        }

        List<Node> reporters = nodes.stream()
            .filter(node -> node != null && node.getType() == NodeType.ROUTINE_INPUT).toList();
        for (Node reporter : reporters) {
            RuntimeVariable value = frame.inputs().get(reporter.getRoutineInputId());
            if (value == null) continue;
            Node snapshot = reporter.createRuntimeVariableSnapshot(value);
            if (snapshot == null) {
                throw new IllegalStateException("Routine input " + reporter.getDisplayName().getString()
                    + " could not be converted to a runtime value.");
            }
            Node host = reporter.getParentParameterHost();
            int slot = reporter.getParentParameterSlotIndex();
            if (host != null && slot >= 0 && !host.attachParameterStrict(snapshot, slot)) {
                throw new IllegalStateException("Routine input " + reporter.getDisplayName().getString()
                    + " produced " + snapshot.getType().getDisplayName()
                    + ", which cannot be used for " + host.getParameterSlotLabel(slot)
                    + " on " + host.getType().getDisplayName() + ".");
            }
            snapshot.setOwningStartNode(reporter.getOwningStartNode());
            nodes.add(snapshot);
            nodes.remove(reporter);
        }
        return new BranchData(nodes, connections);
    }

    public RuntimeVariable getRoutineInputValue(int executionId, String inputId) {
        return RoutineCallFrame.resolve(routineCallFrames, executionId, inputId);
    }

    private CompletableFuture<Void> scheduleNodeStartDelay() {
        long delayMs = SettingsManager.getNodeDelayMs();
        if (delayMs <= 0L) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> { },
            CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }

    private CompletableFuture<Void> waitForExecutionResume() {
        if (!sessionState.isExecutionPaused()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduleResumeCheck(future);
        return future;
    }

    private void scheduleResumeCheck(CompletableFuture<Void> future) {
        if (future.isDone()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            if (cancelRequested || !sessionState.isExecutionPaused()) {
                future.complete(null);
                return;
            }
            scheduleResumeCheck(future);
        }, CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS));
    }

    private CompletableFuture<Void> handleEventCallIfNeeded(Node node, ChainController controller, int executionId,
                                                            Node repeatUntilGuard) {
        if (cancelRequested || controller.cancelRequested || node.getType() != NodeType.EVENT_CALL) {
            return CompletableFuture.completedFuture(null);
        }

        NodeParameter nameParam = node.getParameter("Name");
        String eventName = normalizeEventName(nameParam != null ? nameParam.getStringValue() : null);
        if (eventName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<EventHandlerLaunchData> handlers = resolveFunctionInvocationHandlers(eventName, controller);
        if (handlers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> handlerFutures = new ArrayList<>();
        for (EventHandlerLaunchData handler : handlers) {
            if (cancelRequested || controller.cancelRequested) {
                break;
            }
            int handlerExecutionId = allocateExecutionId();
            if (!retainChainExecution(controller, handlerExecutionId, executionId, true)) {
                continue;
            }
            CompletableFuture<Void> handlerFuture = runEventHandler(handler, controller, handlerExecutionId, repeatUntilGuard);
            handlerFuture.whenComplete((ignored, throwable) ->
                handleChainCompletion(controller, throwable, handlerExecutionId));
            handlerFutures.add(handlerFuture);
        }

        if (handlerFutures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> handlersComplete = CompletableFuture.allOf(
            handlerFutures.toArray(new CompletableFuture[0]));
        return deferCompletion(handlersComplete);
    }

    public <T> CompletableFuture<T> deferCompletion(CompletableFuture<T> future) {
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }
        return future.handleAsync((result, throwable) -> {
            if (throwable != null) {
                throw throwable instanceof CompletionException completionException
                    ? completionException
                    : new CompletionException(throwable);
            }
            return result;
        }, CHAIN_COMPLETION_BOUNDARY_EXECUTOR);
    }

    private List<EventHandlerLaunchData> resolveFunctionInvocationHandlers(String eventName, ChainController controller) {
        if (eventName == null || eventName.isEmpty()) {
            return List.of();
        }

        if (controller != null) {
            List<HandlerTemplate> templates = controller.functionHandlerTemplates.computeIfAbsent(
                eventName,
                ignored -> buildFunctionHandlerTemplates(eventName, controller)
            );
            if (templates.isEmpty()) {
                return List.of();
            }
            List<EventHandlerLaunchData> handlers = new ArrayList<>();
            for (HandlerTemplate template : templates) {
                BranchLaunchData launchData = createBranchLaunchData(template.graphSnapshot, template.rootNodeId);
                if (launchData == null) {
                    LOGGER.debug("Skipping function handler clone for {}", eventName);
                    continue;
                }
                handlers.add(new EventHandlerLaunchData(launchData));
            }
            return handlers;
        }

        List<Node> sourceNodes = controller != null && controller.functionSourceNodes != null && !controller.functionSourceNodes.isEmpty()
            ? snapshotList(controller.functionSourceNodes)
            : (workspaceNodes != null && !workspaceNodes.isEmpty() ? workspaceNodes : activeNodes);
        List<NodeConnection> sourceConnections = controller != null && controller.functionSourceConnections != null && !controller.functionSourceConnections.isEmpty()
            ? snapshotList(controller.functionSourceConnections)
            : (workspaceConnections != null && !workspaceConnections.isEmpty() ? workspaceConnections : activeConnections);
        if (sourceNodes == null || sourceNodes.isEmpty() || sourceConnections == null) {
            return List.of();
        }

        List<NodeConnection> filteredConnections = filterConnections(sourceConnections);
        List<EventHandlerLaunchData> handlers = new ArrayList<>();
        for (Node candidate : sourceNodes) {
            if (candidate == null || candidate.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }

            NodeParameter candidateParam = candidate.getParameter("Name");
            String candidateName = normalizeEventName(candidateParam != null ? candidateParam.getStringValue() : null);
            if (!candidateName.equals(eventName)) {
                continue;
            }

            BranchData handlerBranch = buildBranchData(candidate, sourceNodes, filteredConnections);
            BranchLaunchData launchData = createBranchLaunchData(handlerBranch, candidate);
            if (launchData == null) {
                LOGGER.debug("Skipping function handler clone for {}", eventName);
                continue;
            }

            handlers.add(new EventHandlerLaunchData(launchData));
        }

        return handlers;
    }

    private List<HandlerTemplate> buildFunctionHandlerTemplates(String eventName, ChainController controller) {
        List<Node> sourceNodes = controller != null && controller.functionSourceNodes != null && !controller.functionSourceNodes.isEmpty()
            ? snapshotList(controller.functionSourceNodes)
            : (workspaceNodes != null && !workspaceNodes.isEmpty() ? workspaceNodes : activeNodes);
        List<NodeConnection> sourceConnections = controller != null && controller.functionSourceConnections != null && !controller.functionSourceConnections.isEmpty()
            ? snapshotList(controller.functionSourceConnections)
            : (workspaceConnections != null && !workspaceConnections.isEmpty() ? workspaceConnections : activeConnections);
        if (sourceNodes == null || sourceNodes.isEmpty() || sourceConnections == null) {
            return List.of();
        }

        List<NodeConnection> filteredConnections = filterConnections(sourceConnections);
        List<HandlerTemplate> templates = new ArrayList<>();
        for (Node candidate : sourceNodes) {
            if (candidate == null || candidate.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }

            NodeParameter candidateParam = candidate.getParameter("Name");
            String candidateName = normalizeEventName(candidateParam != null ? candidateParam.getStringValue() : null);
            if (!candidateName.equals(eventName)) {
                continue;
            }

            BranchData handlerBranch = buildBranchData(candidate, sourceNodes, filteredConnections);
            if (handlerBranch == null || handlerBranch.nodes.isEmpty()) {
                continue;
            }
            templates.add(new HandlerTemplate(createGraphSnapshot(handlerBranch.nodes, handlerBranch.connections), candidate.getId()));
        }
        return templates;
    }

    private List<Node> resolveFunctionInvocationHandlers(String eventName) {
        List<EventHandlerLaunchData> handlerData = resolveFunctionInvocationHandlers(eventName, null);
        if (handlerData.isEmpty()) {
            return List.of();
        }
        List<Node> handlers = new ArrayList<>();
        for (EventHandlerLaunchData handler : handlerData) {
            if (handler != null && handler.rootNode != null) {
                handlers.add(handler.rootNode);
            }
        }
        return handlers;
    }

    private CompletableFuture<Void> continueFromNode(Node currentNode, ChainController controller, int executionId,
                                                     Node repeatUntilGuard, int arrivalInputSocket) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        NodeType currentType = currentNode.getType();
        if (currentType == NodeType.CONTROL_JOIN_ANY) {
            return continueFromOutputSocket(currentNode, controller, executionId, repeatUntilGuard, 0);
        }
        if (currentType == NodeType.CONTROL_JOIN_ALL) {
            if (!markJoinAllArrival(currentNode, controller, arrivalInputSocket)) {
                return CompletableFuture.completedFuture(null);
            }
            return continueFromOutputSocket(currentNode, controller, executionId, repeatUntilGuard, 0);
        }

        int nextSocket = currentNode.consumeNextOutputSocket();

        if (currentNode.hasAttachedActionNode()) {
            Node attachedAction = currentNode.getAttachedActionNode();
            NodeType type = currentType;

            if (attachedAction != null) {
                if (type == NodeType.CONTROL_FOREVER && nextSocket != Node.NO_OUTPUT) {
                    return runLoopAttachedAction(
                        currentNode,
                        attachedAction,
                        controller,
                        executionId,
                        repeatUntilGuard,
                        (controlNode, loopController) -> true
                    );
                }

                if (type == NodeType.CONTROL_REPEAT
                    && currentNode.shouldExecuteRepeatAttachedAction()
                    && nextSocket == 0) {
                    AtomicInteger remaining = new AtomicInteger(currentNode.getRepeatLoopCount());
                    return runLoopAttachedAction(
                        currentNode,
                        attachedAction,
                        controller,
                        executionId,
                        repeatUntilGuard,
                        (controlNode, loopController) -> remaining.getAndDecrement() > 0
                    ).thenCompose(ignored ->
                        continueFromOutputSocket(currentNode, controller, executionId, repeatUntilGuard, 0)
                    );
                }

                if (type == NodeType.CONTROL_REPEAT_UNTIL && nextSocket == 0) {
                    controller.pendingRepeatUntilExitControl = null;
                    return runLoopAttachedAction(
                        currentNode,
                        attachedAction,
                        controller,
                        executionId,
                        repeatUntilGuard,
                        new LoopContinuation() {
                            @Override
                            public boolean shouldRunNextIteration(Node controlNode, ChainController loopController) {
                                return !controlNode.isRepeatUntilConditionMetForPolling();
                            }

                            @Override
                            public Node guardForIteration(Node controlNode, Node outerRepeatUntilGuard) {
                                // The Repeat Until condition guards the body while it is running,
                                // not only between completed iterations. Long-running actions poll
                                // this guard and release their input as soon as it becomes true.
                                return controlNode;
                            }
                        }
                    ).thenCompose(ignored ->
                        continueFromOutputSocket(
                            currentNode,
                            controller,
                            executionId,
                            repeatUntilGuard,
                            getRepeatUntilExitOutputSocket(currentNode)
                        )
                    );
                }
            }
        }

        if (nextSocket == Node.NO_OUTPUT) {
            return CompletableFuture.completedFuture(null);
        }

        if (currentType == NodeType.CONTROL_REPEAT_UNTIL && nextSocket != 0) {
            return continueFromOutputSocket(
                currentNode,
                controller,
                executionId,
                repeatUntilGuard,
                getRepeatUntilExitOutputSocket(currentNode)
            );
        }

        if (currentType == NodeType.CONTROL_FORK) {
            return continueFork(currentNode, controller, executionId, repeatUntilGuard);
        }

        return continueFromOutputSocket(currentNode, controller, executionId, repeatUntilGuard, nextSocket);
    }

    private CompletableFuture<Void> continueFromOutputSocket(Node currentNode, ChainController controller, int executionId,
                                                             Node repeatUntilGuard, int outputSocket) {
        List<NodeConnection> graphConnections = controller != null
            && controller.graphConnections != null
            && !controller.graphConnections.isEmpty()
            ? snapshotList(controller.graphConnections)
            : activeConnections;
        NodeConnection nextConnection = getNextConnectedConnection(currentNode, graphConnections, outputSocket);
        if (nextConnection == null) {
            return CompletableFuture.completedFuture(null);
        }
        return runChain(nextConnection.getInputNode(), controller, executionId, repeatUntilGuard, nextConnection.getInputSocket());
    }

    private CompletableFuture<Void> continueFork(Node currentNode, ChainController controller, int executionId, Node repeatUntilGuard) {
        List<NodeConnection> graphConnections = controller != null
            && controller.graphConnections != null
            && !controller.graphConnections.isEmpty()
            ? snapshotList(controller.graphConnections)
            : activeConnections;
        List<NodeConnection> branchConnections = getOutgoingConnections(currentNode, graphConnections);
        if (branchConnections.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> branchFutures = new ArrayList<>();
        NodeConnection primaryConnection = branchConnections.get(0);
        branchFutures.add(runChain(primaryConnection.getInputNode(), controller, executionId, repeatUntilGuard,
            primaryConnection.getInputSocket()));

        for (int i = 1; i < branchConnections.size(); i++) {
            if (cancelRequested || controller.cancelRequested) {
                break;
            }
            NodeConnection branchConnection = branchConnections.get(i);
            int branchExecutionId = allocateExecutionId();
            if (!retainChainExecution(controller, branchExecutionId, executionId, false)) {
                continue;
            }
            CompletableFuture<Void> branchFuture = runChain(branchConnection.getInputNode(), controller, branchExecutionId,
                repeatUntilGuard, branchConnection.getInputSocket());
            branchFuture.whenComplete((ignored, throwable) ->
                handleChainCompletion(controller, throwable, branchExecutionId));
            branchFutures.add(branchFuture);
        }

        if (branchFutures.size() == 1) {
            return branchFutures.get(0);
        }
        return CompletableFuture.allOf(branchFutures.toArray(new CompletableFuture[0]));
    }

    private boolean markJoinAllArrival(Node node, ChainController controller, int arrivalInputSocket) {
        if (node == null || controller == null || arrivalInputSocket < 0) {
            return false;
        }

        synchronized (controller.joinBarrierInputs) {
            Set<Integer> arrivedInputs = controller.joinBarrierInputs.computeIfAbsent(node, ignored -> new HashSet<>());
            arrivedInputs.add(arrivalInputSocket);
            if (arrivedInputs.size() < node.getInputSocketCount()) {
                return false;
            }
            controller.joinBarrierInputs.remove(node);
            return true;
        }
    }

    private int getRepeatUntilExitOutputSocket(Node node) {
        if (node == null) {
            return 0;
        }
        return node.getOutputSocketCount() > 1 ? 1 : 0;
    }

    private CompletableFuture<Void> runEventHandler(EventHandlerLaunchData handlerData, ChainController controller, int executionId, Node repeatUntilGuard) {
        if (handlerData == null || handlerData.rootNode == null) {
            return CompletableFuture.completedFuture(null);
        }

        Node handler = handlerData.rootNode;
        mergeControllerGraph(controller, handlerData.branchNodes, handlerData.branchConnections);
        setEventFunctionActive(handler, true);
        return runChain(handler, controller, executionId, repeatUntilGuard)
            .whenComplete((ignored, throwable) -> {
                setEventFunctionActive(handler, false);
                removeControllerGraph(controller, handlerData.branchNodes, handlerData.branchConnections);
            });
    }

    private boolean shouldExitRepeatUntilGuard(Node currentNode, ChainController controller, Node repeatUntilGuard) {
        if (currentNode == null || controller == null || repeatUntilGuard == null || currentNode == repeatUntilGuard) {
            return false;
        }
        if (!repeatUntilGuard.isRepeatUntilConditionMetForPolling()) {
            return false;
        }
        controller.pendingRepeatUntilExitControl = repeatUntilGuard;
        return true;
    }

    private CompletableFuture<Void> executeNodeWithRepeatUntilGuard(Node currentNode, ChainController controller,
                                                                    int executionId, Node repeatUntilGuard) {
        CompletableFuture<Void> nodeFuture = currentNode.execute(executionId);
        if (repeatUntilGuard != null && isCancellableNavigationNode(currentNode)) {
            monitorNavigationRepeatUntilGuard(nodeFuture, controller, repeatUntilGuard);
        }
        return nodeFuture;
    }

    private void monitorNavigationRepeatUntilGuard(CompletableFuture<Void> nodeFuture,
                                                   ChainController controller, Node repeatUntilGuard) {
        if (nodeFuture == null || nodeFuture.isDone() || controller == null || repeatUntilGuard == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (nodeFuture.isDone() || cancelRequested || controller.cancelRequested) {
                return;
            }
            if (repeatUntilGuard.isRepeatUntilConditionMetForPolling()) {
                controller.pendingRepeatUntilExitControl = repeatUntilGuard;
                // Complete first so cancellation of a tracked Baritone task is a normal loop exit,
                // not an execution error. The underlying navigation is stopped immediately after.
                nodeFuture.complete(null);
                cancelAllNavigationCommands();
                return;
            }
            monitorNavigationRepeatUntilGuard(nodeFuture, controller, repeatUntilGuard);
        }, CompletableFuture.delayedExecutor(25L, TimeUnit.MILLISECONDS));
    }

    private boolean isCancellableNavigationNode(Node node) {
        if (node == null) {
            return false;
        }
        return switch (node.getType()) {
            case GOTO, TRAVEL, COLLECT, BUILD, EXPLORE, FOLLOW, FARM, PATH, COME, SURFACE, TUNNEL -> true;
            default -> false;
        };
    }

    private void handleChainCompletion(ChainController controller, Throwable throwable, int executionId) {
        if (controller == null) {
            return;
        }

        if (throwable != null && !cancelRequested && !controller.cancelRequested) {
            LOGGER.error("Error during execution", throwable);
        }

        if (executionId >= 0) {
            sessionState.removeExecution(executionId);
            routineCallFrames.remove(executionId);
            controller.executionFunctionDepths.remove(executionId);
        }

        if (!releaseChainExecution(controller)) {
            return;
        }

        activeChains.remove(controller.startNode, controller);
        controllerRoutines.remove(controller);

        if (activeChains.isEmpty() && sessionState.isActivelyExecuting()) {
            stopExecution();
            activeNodes.clear();
            activeConnections.clear();
            activeConnectionLookup.clear();
            outputNodeLookup.clear();
            eventConnectionOwners.clear();
            activeEventFunctionNodes.clear();
            sessionState.clearExecutionNodes();
        }
    }

    private int allocateExecutionId() {
        return sessionState.allocateExecutionId();
    }

    private boolean retainChainExecution(ChainController controller, int executionId, int parentExecutionId, boolean functionCall) {
        if (controller == null) {
            return false;
        }

        int parentDepth = controller.executionFunctionDepths.getOrDefault(parentExecutionId, 0);
        int depth = functionCall ? parentDepth + 1 : parentDepth;
        if (depth > MAX_FUNCTION_CALL_DEPTH) {
            if (controller.functionDepthWarnings.getAndIncrement() < 3) {
                LOGGER.warn("Skipping function call from START node {} because function call depth exceeded {}",
                    controller.startNode != null ? controller.startNode.getStartNodeNumber() : "unknown",
                    MAX_FUNCTION_CALL_DEPTH);
                notifyExecutionBudgetWarning("Pathmind stopped a runaway function or routine call loop. Check this preset for recursion.");
            }
            return false;
        }

        int active = controller.activeExecutions.incrementAndGet();
        if (active > MAX_CHAIN_EXECUTIONS_PER_START) {
            controller.activeExecutions.decrementAndGet();
            if (controller.branchBudgetWarnings.getAndIncrement() < 3) {
                LOGGER.warn("Skipping branch from START node {} because active execution count exceeded {}",
                    controller.startNode != null ? controller.startNode.getStartNodeNumber() : "unknown",
                    MAX_CHAIN_EXECUTIONS_PER_START);
                notifyExecutionBudgetWarning("Pathmind skipped extra forks to prevent lag. Check this preset for runaway branching.");
            }
            return false;
        }

        controller.executionFunctionDepths.put(executionId, depth);
        RoutineCallFrame parentFrame = routineCallFrames.get(parentExecutionId);
        if (!functionCall && parentFrame != null) {
            routineCallFrames.put(executionId, new RoutineCallFrame(
                executionId, parentFrame.parentExecutionId(), parentFrame.routineId(), parentFrame.inputs()));
        }
        return true;
    }

    private void notifyExecutionBudgetWarning(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> NodeErrorNotificationOverlay.getInstance().show(message, UITheme.STATE_WARNING));
        } catch (Throwable ignored) {
            // Unit tests and headless environments may not have a Minecraft client.
        }
    }

    private boolean releaseChainExecution(ChainController controller) {
        if (controller == null) {
            return true;
        }
        return controller.activeExecutions.decrementAndGet() <= 0;
    }

    private BranchData buildBranchData(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        return ExecutionGraphSnapshotSupport.buildBranchData(startNode, nodes, connections);
    }

    private NodeGraphData createGraphSnapshot(List<Node> nodes, List<NodeConnection> connections) {
        return ExecutionGraphSnapshotSupport.createGraphSnapshot(nodes, connections);
    }

    private BranchLaunchData createBranchLaunchData(BranchData branchData, int startNodeNumber) {
        return ExecutionGraphSnapshotSupport.createBranchLaunchData(branchData, startNodeNumber);
    }

    private BranchLaunchData createBranchLaunchData(BranchData branchData, Node rootNode) {
        return ExecutionGraphSnapshotSupport.createBranchLaunchData(branchData, rootNode);
    }

    private BranchLaunchData createBranchLaunchData(NodeGraphData graphSnapshot, String rootNodeId) {
        return ExecutionGraphSnapshotSupport.createBranchLaunchData(graphSnapshot, rootNodeId);
    }

    private boolean executeGraphSnapshot(NodeGraphData graphData, boolean markGlobalSnapshot) {
        LoadedGraph loadedGraph = buildGraphFromData(graphData);
        if (loadedGraph == null || loadedGraph.nodes.isEmpty()) {
            return false;
        }

        return executeGraphInternal(loadedGraph.nodes, loadedGraph.connections, markGlobalSnapshot);
    }

    private void logValidationErrors(List<Node> nodes, List<NodeConnection> connections, String presetName, String context) {
        GraphValidationResult validation = GraphValidator.validate(
            nodes,
            connections,
            presetName,
            BaritoneDependencyChecker.isBaritoneApiPresent(),
            UiUtilsDependencyChecker.isUiUtilsPresent()
        );
        if (!validation.hasErrors()) {
            return;
        }

        String message = "Validation errors detected for " + context + " (" + validation.getErrorCount() + " error"
            + (validation.getErrorCount() == 1 ? "" : "s") + ")";
        LOGGER.debug("{}; continuing execution attempt so runtime errors can surface in the overlay", message);
    }


    private boolean playLastStartNodeGraphFromWorkspace() {
        if (lastStartNodeNumber == null || lastStartPreset == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        boolean editorOpen = client != null && PathmindScreens.isVisualEditorScreen(client.screen);
        if (editorOpen && workspaceNodes != null && !workspaceNodes.isEmpty() && workspaceConnections != null) {
            Node workspaceStart = findStartNodeByNumber(workspaceNodes, lastStartNodeNumber);
            if (workspaceStart != null && workspaceStart.getType() == NodeType.START) {
                return executeBranch(workspaceStart, workspaceNodes, workspaceConnections, lastStartPreset);
            }
        }

        NodeGraphData graphData = NodeGraphPersistence.loadNodeGraphForPreset(lastStartPreset);
        if (graphData == null) {
            LOGGER.debug("Failed to load node graph for preset {}", lastStartPreset);
            return false;
        }

        LoadedGraph loadedGraph = buildGraphFromData(graphData);
        if (loadedGraph == null || loadedGraph.nodes.isEmpty()) {
            return false;
        }

        Node startNode = findStartNodeByNumber(loadedGraph.nodes, lastStartNodeNumber);
        if (startNode == null || startNode.getType() != NodeType.START) {
            LOGGER.debug("Last START node not found in current workspace for number {}", lastStartNodeNumber);
            return false;
        }

        return executeBranch(startNode, loadedGraph.nodes, loadedGraph.connections, lastStartPreset);
    }

    private void updateLastStartContext(Node startNode, String presetName) {
        if (startNode == null) {
            return;
        }
        int startNumber = startNode.getStartNodeNumber();
        if (startNumber <= 0) {
            this.lastStartNodeNumber = null;
            this.lastStartPreset = null;
            return;
        }
        this.lastStartNodeNumber = startNumber;
        this.lastStartPreset = presetName != null ? presetName : PresetManager.getActivePreset();
    }



    public boolean shouldAnimateConnection(NodeConnection connection) {
        if (connection == null || !sessionState.isActivelyExecuting()) {
            return false;
        }

        ConnectionKey key = toKey(connection);
        if (key == null) {
            return false;
        }
        if (!activeConnectionLookup.contains(key)) {
            return false;
        }

        Node currentNode = sessionState.getActiveNode();
        if (currentNode == null) {
            return false;
        }

        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        String activeId = currentNode.getId();
        if (activeId == null) {
            return false;
        }

        return activeId.equals(outputNode.getId()) || activeId.equals(inputNode.getId());
    }

    private String normalizeEventName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }





    private Node findWorkspaceStartNode(int startNodeNumber) {
        if (workspaceNodes == null) {
            return null;
        }
        for (Node startNode : workspaceNodes) {
            if (startNode != null && startNode.getType() == NodeType.START
                && startNode.getStartNodeNumber() == startNodeNumber) {
                return startNode;
            }
        }
        return null;
    }



    private void mergeActiveGraph(List<Node> branchNodes, List<NodeConnection> branchConnections) {
        if (branchNodes == null || branchConnections == null) {
            return;
        }
        if (activeNodes == null || activeNodes.isEmpty()) {
            this.activeNodes = new ArrayList<>(branchNodes);
        } else {
            LinkedHashSet<Node> mergedNodes = new LinkedHashSet<>(activeNodes);
            mergedNodes.addAll(branchNodes);
            this.activeNodes = new ArrayList<>(mergedNodes);
        }

        if (activeConnections == null || activeConnections.isEmpty()) {
            this.activeConnections = new ArrayList<>(branchConnections);
        } else {
            // Keep branch connections by object identity. Different loaded preset runs can reuse the
            // same persisted node IDs, so deduplicating by IDs drops valid connections for later runs.
            LinkedHashSet<NodeConnection> mergedConnections = new LinkedHashSet<>(activeConnections);
            mergedConnections.addAll(branchConnections);
            this.activeConnections = new ArrayList<>(mergedConnections);
        }

        rebuildConnectionState(this.activeNodes, this.activeConnections);
    }

    private void mergeControllerGraph(ChainController controller, List<Node> branchNodes, List<NodeConnection> branchConnections) {
        if (controller == null) {
            return;
        }
        if (branchNodes != null && !branchNodes.isEmpty()) {
            synchronized (controller.graphNodes) {
                LinkedHashSet<Node> mergedNodes = new LinkedHashSet<>(controller.graphNodes);
                mergedNodes.addAll(branchNodes);
                controller.graphNodes.clear();
                controller.graphNodes.addAll(mergedNodes);
            }
        }
        if (branchConnections != null && !branchConnections.isEmpty()) {
            synchronized (controller.graphConnections) {
                LinkedHashSet<NodeConnection> mergedConnections = new LinkedHashSet<>(controller.graphConnections);
                mergedConnections.addAll(branchConnections);
                controller.graphConnections.clear();
                controller.graphConnections.addAll(mergedConnections);
            }
        }
    }

    private void removeControllerGraph(ChainController controller, List<Node> branchNodes, List<NodeConnection> branchConnections) {
        if (controller == null) {
            return;
        }
        if (branchConnections != null && !branchConnections.isEmpty()) {
            synchronized (controller.graphConnections) {
                controller.graphConnections.removeAll(branchConnections);
            }
        }
        if (branchNodes != null && !branchNodes.isEmpty()) {
            synchronized (controller.graphNodes) {
                controller.graphNodes.removeAll(branchNodes);
            }
        }
    }













    private void rebuildConnectionState(List<Node> nodes, List<NodeConnection> connections) {
        activeConnectionLookup.clear();
        outputNodeLookup.clear();
        eventConnectionOwners.clear();
        activeEventFunctionNodes.clear();

        if (connections != null) {
            for (NodeConnection connection : connections) {
                ConnectionKey key = toKey(connection);
                if (key != null) {
                    activeConnectionLookup.add(key);
                }
                // Build output node lookup for O(1) access in getNextConnectedNode
                Node outputNode = connection.getOutputNode();
                if (outputNode != null && outputNode.getId() != null) {
                    String lookupKey = outputNode.getId() + ":" + connection.getOutputSocket();
                    outputNodeLookup.put(lookupKey, connection.getInputNode());
                }
            }
        }

        if (nodes == null || connections == null) {
            return;
        }

        for (Node node : nodes) {
            if (node.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }

            Set<Node> scopeNodes = collectBranchNodes(node, connections);
            if (scopeNodes.isEmpty()) {
                continue;
            }

            for (NodeConnection connection : connections) {
                if (scopeNodes.contains(connection.getOutputNode()) && scopeNodes.contains(connection.getInputNode())) {
                    ConnectionKey key = toKey(connection);
                    if (key != null) {
                        eventConnectionOwners.put(key, node);
                    }
                }
            }
        }
    }

    private void setEventFunctionActive(Node handler, boolean active) {
        if (handler == null || handler.getType() != NodeType.EVENT_FUNCTION) {
            return;
        }

        if (active) {
            activeEventFunctionNodes.add(handler);
        } else {
            activeEventFunctionNodes.remove(handler);
        }
    }

    private ConnectionKey toKey(NodeConnection connection) {
        if (connection == null) {
            return null;
        }

        Node output = connection.getOutputNode();
        Node input = connection.getInputNode();
        if (output == null || input == null) {
            return null;
        }

        return new ConnectionKey(output.getId(), connection.getOutputSocket(), input.getId(), connection.getInputSocket());
    }
}
