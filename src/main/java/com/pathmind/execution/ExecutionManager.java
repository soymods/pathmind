package com.pathmind.execution;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.UiUtilsDependencyChecker;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Objects;

/**
 * Manages the execution state of the node graph.
 * Tracks which node is currently active and provides state information for overlays.
 */
public class ExecutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionManager.class);
    private static volatile ExecutionManager instance;
    private Node activeNode;
    private boolean isExecuting;
    private long executionStartTime;
    private long executionEndTime;
    private static final long MINIMUM_DISPLAY_DURATION = 3000; // 3 seconds minimum display
    private NodeGraphData lastExecutedGraph;
    private NodeGraphData lastGlobalGraph;
    private List<Node> activeNodes;
    private List<NodeConnection> activeConnections;
    private List<Node> workspaceNodes;
    private List<NodeConnection> workspaceConnections;
    private final Set<ConnectionKey> activeConnectionLookup;
    private final Map<String, Node> outputNodeLookup;
    private final Set<String> executingEvents;
    private volatile boolean cancelRequested;
    private final Map<Node, ChainController> activeChains;
    private final Map<String, RuntimeVariable> globalRuntimeVariables;
    private final Map<ConnectionKey, Node> eventConnectionOwners;
    private final Set<Node> activeEventFunctionNodes;
    private final Map<Integer, Node> activeExecutionNodes;
    private final Map<Integer, Long> executionNodeStartTimes;
    private final Map<Integer, Long> executionNodePausedDurations;
    private final Map<Integer, Long> executionNodePauseStartTimes;
    private final AtomicInteger nextExecutionId;
    private Integer primaryExecutionId;
    private boolean globalExecutionActive;
    private boolean lastSnapshotWasGlobal;
    private long activeNodeStartTime;
    private long activeNodePausedDuration;
    private long activeNodePauseStartTime;
    private long activeNodeEndTime;
    private boolean singleplayerPaused;
    private Integer lastStartNodeNumber;
    private String lastStartPreset;
    private static final ThreadLocal<Integer> CURRENT_EXECUTION_ID = new ThreadLocal<>();


    private static class ChainController {
        final Node startNode;
        final int rootExecutionId;
        volatile boolean cancelRequested;
        volatile Node pendingRepeatUntilExitControl;
        final AtomicInteger activeExecutions;
        final Map<String, RuntimeVariable> runtimeVariables;
        final Map<String, RuntimeList> runtimeLists;

        ChainController(Node startNode, int rootExecutionId) {
            this.startNode = startNode;
            this.rootExecutionId = rootExecutionId;
            this.cancelRequested = false;
            this.pendingRepeatUntilExitControl = null;
            this.activeExecutions = new AtomicInteger(1);
            this.runtimeVariables = new ConcurrentHashMap<>();
            this.runtimeLists = new ConcurrentHashMap<>();
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

        public List<String> getEntries() {
            return Collections.unmodifiableList(entries);
        }
    }

    public static final class RuntimeVariableEntry {
        private final String startNodeId;
        private final String name;
        private final RuntimeVariable variable;

        public RuntimeVariableEntry(String startNodeId, String name, RuntimeVariable variable) {
            this.startNodeId = startNodeId;
            this.name = name;
            this.variable = variable;
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
        this.activeNode = null;
        this.isExecuting = false;
        this.executionStartTime = 0;
        this.executionEndTime = 0;
        this.activeNodes = new ArrayList<>();
        this.activeConnections = new ArrayList<>();
        this.workspaceNodes = new ArrayList<>();
        this.workspaceConnections = new ArrayList<>();
        this.executingEvents = ConcurrentHashMap.newKeySet();
        this.cancelRequested = false;
        this.activeChains = new ConcurrentHashMap<>();
        this.globalRuntimeVariables = new ConcurrentHashMap<>();
        this.globalExecutionActive = false;
        this.lastSnapshotWasGlobal = false;
        this.activeConnectionLookup = ConcurrentHashMap.newKeySet();
        this.outputNodeLookup = new ConcurrentHashMap<>();
        this.eventConnectionOwners = new ConcurrentHashMap<>();
        this.activeEventFunctionNodes = ConcurrentHashMap.newKeySet();
        this.activeExecutionNodes = new ConcurrentHashMap<>();
        this.executionNodeStartTimes = new ConcurrentHashMap<>();
        this.executionNodePausedDurations = new ConcurrentHashMap<>();
        this.executionNodePauseStartTimes = new ConcurrentHashMap<>();
        this.nextExecutionId = new AtomicInteger(1);
        this.primaryExecutionId = null;
        this.activeNodeStartTime = 0;
        this.activeNodePausedDuration = 0;
        this.activeNodePauseStartTime = 0;
        this.activeNodeEndTime = 0;
        this.singleplayerPaused = false;
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
        if (startNode == null || name == null || name.trim().isEmpty() || value == null) {
            return false;
        }
        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            globalRuntimeVariables.put(name.trim(), value);
            return true;
        }
        controller.runtimeVariables.put(name.trim(), value);
        globalRuntimeVariables.put(name.trim(), value);
        return true;
    }

    public RuntimeVariable getRuntimeVariable(Node startNode, String name) {
        if (startNode == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            return globalRuntimeVariables.get(name.trim());
        }
        RuntimeVariable value = controller.runtimeVariables.get(name.trim());
        return value != null ? value : globalRuntimeVariables.get(name.trim());
    }

    public boolean setRuntimeVariableForAnyActiveChain(String name, RuntimeVariable value) {
        if (name == null || name.trim().isEmpty() || value == null) {
            return false;
        }
        for (ChainController controller : activeChains.values()) {
            controller.runtimeVariables.put(name.trim(), value);
            globalRuntimeVariables.put(name.trim(), value);
            return true;
        }
        globalRuntimeVariables.put(name.trim(), value);
        return false;
    }

    public RuntimeVariable getRuntimeVariableFromAnyActiveChain(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        for (ChainController controller : activeChains.values()) {
            RuntimeVariable value = controller.runtimeVariables.get(name.trim());
            if (value != null) {
                return value;
            }
        }
        return globalRuntimeVariables.get(name.trim());
    }

    public boolean setRuntimeList(Node startNode, String name, RuntimeList list) {
        if (startNode == null || name == null || name.trim().isEmpty() || list == null) {
            return false;
        }
        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            return false;
        }
        controller.runtimeLists.put(name.trim(), list);
        return true;
    }

    public RuntimeList getRuntimeList(Node startNode, String name) {
        if (startNode == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            return null;
        }
        return controller.runtimeLists.get(name.trim());
    }

    public List<RuntimeVariableEntry> getRuntimeVariableEntries() {
        if (activeChains.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuntimeVariableEntry> entries = new ArrayList<>();
        for (ChainController controller : activeChains.values()) {
            if (controller == null || controller.runtimeVariables.isEmpty()) {
                continue;
            }
            String startId = controller.startNode != null ? controller.startNode.getId() : "";
            for (Map.Entry<String, RuntimeVariable> entry : controller.runtimeVariables.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                entries.add(new RuntimeVariableEntry(startId, entry.getKey(), entry.getValue()));
            }
        }
        return entries;
    }

    public void executeGraph(List<Node> nodes, List<NodeConnection> connections) {
        executeGraphInternal(nodes, connections, true);
    }

    private void executeGraphInternal(List<Node> nodes, List<NodeConnection> connections, boolean markGlobalSnapshot) {
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute graph - missing nodes or connections");
            return;
        }
        logValidationErrors(nodes, connections, PresetManager.getActivePreset(), "workspace");

        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<Node> startNodes = findStartNodes(nodes);
        if (startNodes.isEmpty()) {
            LOGGER.warn("No START nodes found");
            return;
        }

        // Track the last started chain by START node number so the keybind can replay it.
        updateLastStartContext(startNodes.get(startNodes.size() - 1), PresetManager.getActivePreset());

        // Ensure Baritone isn't still executing stale goals from a previous session
        cancelAllBaritoneCommands();

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

        List<Node> isolatedStartNodes = new ArrayList<>();
        for (Node startNode : startNodes) {
            BranchData branchData = buildBranchData(startNode, nodes, filteredConnections);
            BranchLaunchData launchData = createBranchLaunchData(branchData, startNode.getStartNodeNumber());
            if (launchData == null) {
                LOGGER.debug("Skipping START node {} because its branch could not be prepared", startNode.getStartNodeNumber());
                continue;
            }
            mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);
            isolatedStartNodes.add(launchData.rootNode);
        }

        if (isolatedStartNodes.isEmpty()) {
            LOGGER.warn("No START branches could be prepared for execution");
            return;
        }

        startExecution(isolatedStartNodes, markGlobalSnapshot);

        for (Node isolatedStartNode : isolatedStartNodes) {
            int executionId = allocateExecutionId();
            ChainController controller = new ChainController(isolatedStartNode, executionId);
            activeChains.put(isolatedStartNode, controller);
            CompletableFuture<Void> chainFuture = runChain(isolatedStartNode, controller, controller.rootExecutionId);
            chainFuture.whenComplete((ignored, throwable) ->
                handleChainCompletion(controller, throwable, controller.rootExecutionId));
        }
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
        if (workspaceNodes == null || workspaceNodes.isEmpty() || workspaceConnections == null) {
            LOGGER.debug("No workspace graph available to start a START node");
            return;
        }

        int startNodeNumber = resolvePlayableStartNumber();
        if (startNodeNumber <= 0) {
            LOGGER.debug("No playable START node available for keybind launch");
            return;
        }

        Node startNode = findWorkspaceStartNode(startNodeNumber);
        if (startNode == null) {
            LOGGER.debug("START node {} no longer exists in the current workspace", startNodeNumber);
            return;
        }

        if (!requestStartForStartNumber(startNodeNumber)) {
            LOGGER.debug("Failed to start last START node {} from current workspace", startNodeNumber);
        }
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
            this.isExecuting = true;
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId);
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
            this.isExecuting = true;
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId);
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
        if (startNode == null || startNode.getType() != NodeType.START) {
            LOGGER.warn("Cannot execute external branch - invalid START node");
            return false;
        }
        if (nodes == null || connections == null) {
            LOGGER.warn("Cannot execute external branch - missing nodes or connections");
            return false;
        }
        logValidationErrors(nodes, connections, presetName, "preset \"" + presetName + "\"");
        if (isChainActive(startNode)) {
            LOGGER.debug("External START node already executing, ignoring branch start request");
            return false;
        }

        // Preserve this graph for Activate-node lookups within the launched preset chain.
        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);

        List<NodeConnection> filteredConnections = filterConnections(connections);
        BranchData branchData = buildBranchData(startNode, nodes, filteredConnections);
        if (branchData == null || branchData.nodes.isEmpty()) {
            LOGGER.debug("External START node has no executable branch");
            return false;
        }

        this.lastExecutedGraph = createGraphSnapshot(branchData.nodes, branchData.connections);
        this.lastSnapshotWasGlobal = false;
        BranchLaunchData launchData = createBranchLaunchData(branchData, startNode.getStartNodeNumber());
        if (launchData == null) {
            LOGGER.debug("External START node branch could not be cloned for execution");
            return false;
        }
        this.cancelRequested = false;
        mergeActiveGraph(launchData.branchData.nodes, launchData.branchData.connections);

        if (!isExecuting && activeChains.isEmpty()) {
            startExecution(Collections.singletonList(launchData.rootNode), false);
        } else {
            this.isExecuting = true;
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        updateLastStartContext(startNode, presetName);
        return true;
    }
    
    /**
     * Start execution with the given start node
     */
    private void startExecution(List<Node> startNodes, boolean markGlobal) {
        globalRuntimeVariables.clear();
        this.activeExecutionNodes.clear();
        this.executionNodeStartTimes.clear();
        this.executionNodePausedDurations.clear();
        this.executionNodePauseStartTimes.clear();
        this.primaryExecutionId = null;
        this.activeNode = startNodes.isEmpty() ? null : startNodes.get(0);
        if (this.activeNode != null) {
            resetActiveNodeTiming();
        } else {
            clearActiveNodeTiming();
        }
        this.isExecuting = true;
        this.globalExecutionActive = markGlobal;
        this.cancelRequested = false;
        this.executionStartTime = System.currentTimeMillis();
        this.executionEndTime = 0;
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
        setActiveNode(node, primaryExecutionId != null ? primaryExecutionId : -1);
    }

    private void setActiveNode(Node node, int executionId) {
        if (executionId >= 0) {
            if (node != null) {
                activeExecutionNodes.put(executionId, node);
                resetExecutionTiming(executionId);
            } else {
                activeExecutionNodes.remove(executionId);
                clearExecutionTiming(executionId);
            }
        }

        if (primaryExecutionId == null && executionId >= 0) {
            primaryExecutionId = executionId;
        }

        if (primaryExecutionId != null && executionId == primaryExecutionId) {
            this.activeNode = node;
            if (node != null) {
                resetActiveNodeTiming();
            } else {
                clearActiveNodeTiming();
            }
            LOGGER.trace("Set active node to {}", node != null ? node.getType() : "null");
        }
    }
    
    /**
     * Stop execution
     */
    public void stopExecution() {
        LOGGER.debug("Stopping execution");
        this.isExecuting = false;
        this.globalExecutionActive = false;
        if (cancelRequested) {
            this.executionEndTime = 0;
            this.executionStartTime = 0;
            this.activeNode = null;
            clearActiveNodeTiming();
            cancelRequested = false;
        } else {
            this.executionEndTime = System.currentTimeMillis();
            this.activeNodeEndTime = this.executionEndTime;
        }
        // Keep activeNode for minimum display duration
    }

    /**
     * Request that all executing node chains stop immediately.
     */
    public void requestStopAll() {
        cancelAllBaritoneCommands();

        if (!isExecuting && activeNode == null && activeChains.isEmpty()) {
            return;
        }

        LOGGER.debug("Stop requested for all node trees");
        cancelRequested = true;
        for (ChainController controller : activeChains.values()) {
            controller.cancelRequested = true;
        }
        this.isExecuting = false;
        this.globalExecutionActive = false;
        this.activeNode = null;
        clearActiveNodeTiming();
        this.executionStartTime = 0;
        this.executionEndTime = 0;
        this.activeNodes.clear();
        this.activeConnections.clear();
        this.activeConnectionLookup.clear();
        this.outputNodeLookup.clear();
        this.executingEvents.clear();
        this.eventConnectionOwners.clear();
        this.activeEventFunctionNodes.clear();
        this.activeExecutionNodes.clear();
        this.executionNodeStartTimes.clear();
        this.executionNodePausedDurations.clear();
        this.executionNodePauseStartTimes.clear();
        this.primaryExecutionId = null;
        this.activeChains.clear();
        this.globalRuntimeVariables.clear();
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
    
    /**
     * Get the currently active node
     */
    public Node getActiveNode() {
        return activeNode;
    }

    public boolean isExecutionActiveOnNode(Integer executionId, String nodeId) {
        if (executionId == null || executionId < 0 || nodeId == null || nodeId.isEmpty()) {
            return false;
        }
        Node active = activeExecutionNodes.get(executionId);
        return active != null && nodeId.equals(active.getId());
    }

    public long getExecutionNodeDuration(Integer executionId) {
        if (executionId == null || executionId < 0) {
            return 0L;
        }

        Long startTime = executionNodeStartTimes.get(executionId);
        if (startTime == null || startTime <= 0L) {
            return 0L;
        }

        long referenceTime = System.currentTimeMillis();
        long pausedTime = executionNodePausedDurations.getOrDefault(executionId, 0L);
        long pauseStart = executionNodePauseStartTimes.getOrDefault(executionId, 0L);
        if (singleplayerPaused && pauseStart > 0L) {
            pausedTime += referenceTime - pauseStart;
        }

        return Math.max(0L, referenceTime - startTime - pausedTime);
    }

    public Integer getCurrentExecutionId() {
        return CURRENT_EXECUTION_ID.get();
    }

    public void runWithExecutionContext(int executionId, Runnable runnable) {
        Integer previous = CURRENT_EXECUTION_ID.get();
        if (executionId >= 0) {
            CURRENT_EXECUTION_ID.set(executionId);
        } else {
            CURRENT_EXECUTION_ID.remove();
        }
        try {
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT_EXECUTION_ID.set(previous);
            } else {
                CURRENT_EXECUTION_ID.remove();
            }
        }
    }

    public List<Node> getActiveNodeChainSnapshot() {
        LinkedHashSet<Node> ordered = new LinkedHashSet<>();
        if (activeNode != null) {
            ordered.add(activeNode);
        }

        if (!activeExecutionNodes.isEmpty()) {
            List<Integer> ids = new ArrayList<>(activeExecutionNodes.keySet());
            Collections.sort(ids);
            for (Integer id : ids) {
                Node node = activeExecutionNodes.get(id);
                if (node != null) {
                    ordered.add(node);
                }
            }
        }

        return new ArrayList<>(ordered);
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

        cancelAllBaritoneCommands();
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

        if (!isExecuting && activeChains.isEmpty()) {
            startExecution(Collections.singletonList(launchData.rootNode), globalExecutionActive);
        } else {
            this.isExecuting = true;
        }

        int executionId = allocateExecutionId();
        ChainController controller = new ChainController(launchData.rootNode, executionId);
        activeChains.put(launchData.rootNode, controller);
        CompletableFuture<Void> chainFuture = runChain(launchData.rootNode, controller, controller.rootExecutionId);
        chainFuture.whenComplete((ignored, throwable) ->
            handleChainCompletion(controller, throwable, controller.rootExecutionId));
        return true;
    }

    public void setWorkspaceGraph(List<Node> nodes, List<NodeConnection> connections) {
        if (nodes == null || connections == null) {
            return;
        }
        this.workspaceNodes = new ArrayList<>(nodes);
        this.workspaceConnections = new ArrayList<>(connections);
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
        if (isExecuting) {
            return true;
        }

        return isCompletionDisplayActive();
    }

    /**
     * Returns true when the overlay should show completion state messaging.
     */
    public boolean isDisplayingCompletion() {
        if (isExecuting) {
            return false;
        }

        return isCompletionDisplayActive();
    }

    private boolean isCompletionDisplayActive() {
        if (executionEndTime > 0 && activeNode != null) {
            long timeSinceEnd = System.currentTimeMillis() - executionEndTime;
            if (timeSinceEnd < MINIMUM_DISPLAY_DURATION) {
                return true;
            } else {
                // Clear the active node after minimum display duration
                this.activeNode = null;
                this.executionEndTime = 0;
                clearActiveNodeTiming();
            }
        }
        return false;
    }

    public boolean isGlobalExecutionActive() {
        return globalExecutionActive;
    }
    
    /**
     * Get the execution start time
     */
    public long getExecutionStartTime() {
        return executionStartTime;
    }
    
    /**
     * Get the current execution duration in milliseconds
     */
    public long getExecutionDuration() {
        if (executionStartTime == 0) {
            return 0;
        }
        
        if (isExecuting) {
            return System.currentTimeMillis() - executionStartTime;
        } else if (executionEndTime > 0) {
            return executionEndTime - executionStartTime;
        }
        
        return 0;
    }

    /**
     * Get the elapsed duration for the currently active node in milliseconds.
     */
    public long getActiveNodeDuration() {
        if (activeNodeStartTime == 0) {
            return 0;
        }

        long referenceTime;
        if (isExecuting) {
            referenceTime = System.currentTimeMillis();
        } else if (activeNodeEndTime > 0) {
            referenceTime = activeNodeEndTime;
        } else {
            referenceTime = System.currentTimeMillis();
        }

        long pausedTime = activeNodePausedDuration;
        if (singleplayerPaused && activeNodePauseStartTime > 0) {
            pausedTime += referenceTime - activeNodePauseStartTime;
        }

        return Math.max(0, referenceTime - activeNodeStartTime - pausedTime);
    }

    /**
     * Update whether the singleplayer game is currently paused so node timers can be frozen.
     */
    public void setSingleplayerPaused(boolean paused) {
        if (this.singleplayerPaused == paused) {
            return;
        }

        this.singleplayerPaused = paused;
        long now = System.currentTimeMillis();
        if (paused) {
            if (activeNode != null) {
                this.activeNodePauseStartTime = now;
            }
            for (Map.Entry<Integer, Node> entry : activeExecutionNodes.entrySet()) {
                Integer executionId = entry.getKey();
                if (executionId == null || entry.getValue() == null) {
                    continue;
                }
                executionNodePauseStartTimes.put(executionId, now);
            }
        } else if (activeNodePauseStartTime > 0) {
            this.activeNodePausedDuration += now - this.activeNodePauseStartTime;
            this.activeNodePauseStartTime = 0;
            for (Map.Entry<Integer, Node> entry : activeExecutionNodes.entrySet()) {
                Integer executionId = entry.getKey();
                if (executionId == null || entry.getValue() == null) {
                    continue;
                }
                long pauseStart = executionNodePauseStartTimes.getOrDefault(executionId, 0L);
                if (pauseStart > 0L) {
                    executionNodePausedDurations.merge(executionId, now - pauseStart, Long::sum);
                    executionNodePauseStartTimes.put(executionId, 0L);
                }
            }
        }
    }

    private void resetActiveNodeTiming() {
        this.activeNodeStartTime = System.currentTimeMillis();
        this.activeNodePausedDuration = 0;
        this.activeNodeEndTime = 0;
        this.activeNodePauseStartTime = singleplayerPaused ? activeNodeStartTime : 0;
    }

    private void clearActiveNodeTiming() {
        this.activeNodeStartTime = 0;
        this.activeNodePausedDuration = 0;
        this.activeNodePauseStartTime = 0;
        this.activeNodeEndTime = 0;
    }

    private void resetExecutionTiming(int executionId) {
        if (executionId < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        executionNodeStartTimes.put(executionId, now);
        executionNodePausedDurations.put(executionId, 0L);
        executionNodePauseStartTimes.put(executionId, singleplayerPaused ? now : 0L);
    }

    private void clearExecutionTiming(int executionId) {
        if (executionId < 0) {
            return;
        }
        executionNodeStartTimes.remove(executionId);
        executionNodePausedDurations.remove(executionId);
        executionNodePauseStartTimes.remove(executionId);
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller, int executionId) {
        return runChain(currentNode, controller, executionId, null);
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller, int executionId, Node repeatUntilGuard) {
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
                    .thenCompose(pausedIgnored -> currentNode.execute(executionId))
                    .thenCompose(ignoredFuture -> {
                        if (cancelRequested || controller.cancelRequested) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return handleEventCallIfNeeded(currentNode, controller, repeatUntilGuard);
                    })
                    .thenCompose(ignoredFuture -> waitForExecutionResume())
                    .thenCompose(ignoredFuture -> continueFromNode(currentNode, controller, executionId, repeatUntilGuard));
            });
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
        if (!singleplayerPaused) {
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
            if (cancelRequested || !singleplayerPaused) {
                future.complete(null);
                return;
            }
            scheduleResumeCheck(future);
        }, CompletableFuture.delayedExecutor(50L, TimeUnit.MILLISECONDS));
    }

    private CompletableFuture<Void> handleEventCallIfNeeded(Node node, ChainController controller, Node repeatUntilGuard) {
        if (cancelRequested || controller.cancelRequested || node.getType() != NodeType.EVENT_CALL) {
            return CompletableFuture.completedFuture(null);
        }

        NodeParameter nameParam = node.getParameter("Name");
        String eventName = normalizeEventName(nameParam != null ? nameParam.getStringValue() : null);
        if (eventName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (!executingEvents.add(eventName)) {
            LOGGER.debug("Skipping recursive event call for {}", eventName);
            return CompletableFuture.completedFuture(null);
        }

        List<Node> handlers = new ArrayList<>();
        for (Node candidate : activeNodes) {
            if (candidate.getType() == NodeType.EVENT_FUNCTION) {
                NodeParameter candidateParam = candidate.getParameter("Name");
                String candidateName = normalizeEventName(candidateParam != null ? candidateParam.getStringValue() : null);
                if (!candidateName.isEmpty() && candidateName.equals(eventName)) {
                    handlers.add(candidate);
                }
            }
        }

        if (handlers.isEmpty()) {
            executingEvents.remove(eventName);
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> handlerFutures = new ArrayList<>();
        for (Node handler : handlers) {
            if (cancelRequested || controller.cancelRequested) {
                break;
            }
            retainChainExecution(controller);
            int handlerExecutionId = allocateExecutionId();
            CompletableFuture<Void> handlerFuture = runEventHandler(handler, controller, handlerExecutionId, repeatUntilGuard);
            handlerFuture.whenComplete((ignored, throwable) ->
                handleChainCompletion(controller, throwable, handlerExecutionId));
            handlerFutures.add(handlerFuture);
        }

        if (handlerFutures.isEmpty()) {
            executingEvents.remove(eventName);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> handlersComplete = CompletableFuture.allOf(
            handlerFutures.toArray(new CompletableFuture[0]));
        return handlersComplete.whenComplete((ignored, throwable) -> executingEvents.remove(eventName));
    }

    private CompletableFuture<Void> continueFromNode(Node currentNode, ChainController controller, int executionId, Node repeatUntilGuard) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        int nextSocket = currentNode.consumeNextOutputSocket();

        if (currentNode.hasAttachedActionNode()) {
            Node attachedAction = currentNode.getAttachedActionNode();
            NodeType type = currentNode.getType();

            if (attachedAction != null) {
                if (type == NodeType.CONTROL_FOREVER && nextSocket != Node.NO_OUTPUT) {
                    return runChain(attachedAction, controller, executionId, repeatUntilGuard)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller, executionId, repeatUntilGuard);
                        });
                }

                if ((type == NodeType.CONTROL_REPEAT || type == NodeType.CONTROL_REPEAT_UNTIL) && nextSocket == 0) {
                    if (type == NodeType.CONTROL_REPEAT_UNTIL) {
                        controller.pendingRepeatUntilExitControl = null;
                    }
                    Node actionGuard = type == NodeType.CONTROL_REPEAT_UNTIL ? currentNode : repeatUntilGuard;
                    return runChain(attachedAction, controller, executionId, actionGuard)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            if (type == NodeType.CONTROL_REPEAT_UNTIL
                                && controller.pendingRepeatUntilExitControl == currentNode) {
                                controller.pendingRepeatUntilExitControl = null;
                                Node exitNode = getNextConnectedNode(currentNode, activeConnections, 1);
                                if (exitNode == null) {
                                    exitNode = getNextConnectedNode(currentNode, activeConnections, 0);
                                }
                                if (exitNode != null) {
                                    return runChain(exitNode, controller, executionId, repeatUntilGuard);
                                }
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller, executionId, repeatUntilGuard);
                        });
                }
            }
        }

        if (nextSocket == Node.NO_OUTPUT) {
            return CompletableFuture.completedFuture(null);
        }

        Node nextNode = getNextConnectedNode(currentNode, activeConnections, nextSocket);
        if (nextNode == null && nextSocket > 0) {
            nextNode = getNextConnectedNode(currentNode, activeConnections, 0);
        }
        if (nextNode != null) {
            return runChain(nextNode, controller, executionId, repeatUntilGuard);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> runEventHandler(Node handler, ChainController controller, int executionId, Node repeatUntilGuard) {
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }

        setEventFunctionActive(handler, true);
        return runChain(handler, controller, executionId, repeatUntilGuard)
            .whenComplete((ignored, throwable) -> setEventFunctionActive(handler, false));
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

    private void handleChainCompletion(ChainController controller, Throwable throwable, int executionId) {
        if (controller == null) {
            return;
        }

        if (throwable != null && !cancelRequested && !controller.cancelRequested) {
            LOGGER.error("Error during execution", throwable);
        }

        if (executionId >= 0) {
            activeExecutionNodes.remove(executionId);
            clearExecutionTiming(executionId);
            if (primaryExecutionId != null && primaryExecutionId == executionId) {
                refreshPrimaryExecution();
            }
        }

        if (!releaseChainExecution(controller)) {
            return;
        }

        activeChains.remove(controller.startNode, controller);

        if (activeChains.isEmpty() && isExecuting) {
            stopExecution();
            activeNodes.clear();
            activeConnections.clear();
            activeConnectionLookup.clear();
            outputNodeLookup.clear();
            executingEvents.clear();
            eventConnectionOwners.clear();
            activeEventFunctionNodes.clear();
            activeExecutionNodes.clear();
            executionNodeStartTimes.clear();
            executionNodePausedDurations.clear();
            executionNodePauseStartTimes.clear();
            primaryExecutionId = null;
            globalRuntimeVariables.clear();
        }
    }

    private int allocateExecutionId() {
        return nextExecutionId.getAndIncrement();
    }

    private void refreshPrimaryExecution() {
        if (activeExecutionNodes.isEmpty()) {
            primaryExecutionId = null;
            activeNode = null;
            clearActiveNodeTiming();
            return;
        }

        int replacementId = Integer.MAX_VALUE;
        Node replacementNode = null;
        for (Map.Entry<Integer, Node> entry : activeExecutionNodes.entrySet()) {
            Integer id = entry.getKey();
            Node node = entry.getValue();
            if (id == null || node == null) {
                continue;
            }
            if (id < replacementId) {
                replacementId = id;
                replacementNode = node;
            }
        }

        if (replacementNode == null) {
            primaryExecutionId = null;
            activeNode = null;
            clearActiveNodeTiming();
            return;
        }

        primaryExecutionId = replacementId;
        activeNode = replacementNode;
        resetActiveNodeTiming();
    }

    private void retainChainExecution(ChainController controller) {
        if (controller != null) {
            controller.activeExecutions.incrementAndGet();
        }
    }

    private boolean releaseChainExecution(ChainController controller) {
        if (controller == null) {
            return true;
        }
        return controller.activeExecutions.decrementAndGet() <= 0;
    }

    private boolean executeGraphSnapshot(NodeGraphData graphData, boolean markGlobalSnapshot) {
        LoadedGraph loadedGraph = buildGraphFromData(graphData);
        if (loadedGraph == null || loadedGraph.nodes.isEmpty()) {
            return false;
        }

        executeGraphInternal(loadedGraph.nodes, loadedGraph.connections, markGlobalSnapshot);
        return true;
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

    private LoadedGraph buildGraphFromData(NodeGraphData graphData) {
        if (graphData == null || graphData.getNodes() == null) {
            return null;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        List<Node> nodes = new ArrayList<>();

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            Node node = new Node(nodeData.getType(), nodeData.getX(), nodeData.getY());

            try {
                java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(node, nodeData.getId());
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Failed to set node ID during replay: {}", e.getMessage());
            }

            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            node.getParameters().clear();
            if (nodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData paramData : nodeData.getParameters()) {
                    ParameterType paramType = ParameterType.valueOf(paramData.getType());
                    NodeParameter param = new NodeParameter(paramData.getName(), paramType, paramData.getValue());
                    if (paramData.getUserEdited() != null) {
                        param.setUserEdited(paramData.getUserEdited());
                    }
                    node.getParameters().add(param);
                }
            }
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedSensorId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node sensor = nodeMap.get(nodeData.getAttachedSensorId());
                if (control != null && sensor != null) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentControlId() != null) {
                Node sensor = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentControlId());
                if (sensor != null && control != null && sensor.isSensorNode()) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedActionId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node child = nodeMap.get(nodeData.getAttachedActionId());
                if (control != null && child != null) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentActionControlId() != null) {
                Node child = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentActionControlId());
                if (child != null && control != null && control.canAcceptActionNode(child)) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                Node host = nodeMap.get(nodeData.getId());
                if (host != null) {
                    attachments.sort(java.util.Comparator.comparingInt(NodeGraphData.ParameterAttachmentData::getSlotIndex));
                    for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
                        Node parameter = nodeMap.get(attachment.getParameterNodeId());
                        if (parameter != null) {
                            host.attachParameter(parameter, attachment.getSlotIndex());
                        }
                    }
                }
            } else if (nodeData.getAttachedParameterId() != null) {
                Node host = nodeMap.get(nodeData.getId());
                Node parameter = nodeMap.get(nodeData.getAttachedParameterId());
                if (host != null && parameter != null && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                continue;
            }
            if (nodeData.getParentParameterHostId() != null) {
                Node parameter = nodeMap.get(nodeData.getId());
                Node host = nodeMap.get(nodeData.getParentParameterHostId());
                if (parameter != null && host != null && parameter.isParameterNode()
                    && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        List<NodeConnection> connections = new ArrayList<>();
        if (graphData.getConnections() != null) {
            for (NodeGraphData.ConnectionData connData : graphData.getConnections()) {
                Node outputNode = nodeMap.get(connData.getOutputNodeId());
                Node inputNode = nodeMap.get(connData.getInputNodeId());
                if (outputNode != null && inputNode != null) {
                    if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                        continue;
                    }
                    connections.add(new NodeConnection(outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket()));
                }
            }
        }

        return new LoadedGraph(nodes, connections, nodeMap);
    }

    private boolean playLastStartNodeGraphFromWorkspace() {
        if (lastStartNodeNumber == null || lastStartPreset == null) {
            return false;
        }

        if (workspaceNodes != null && !workspaceNodes.isEmpty() && workspaceConnections != null) {
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

    private static final class LoadedGraph {
        final List<Node> nodes;
        final List<NodeConnection> connections;
        final Map<String, Node> nodeLookup;

        LoadedGraph(List<Node> nodes, List<NodeConnection> connections, Map<String, Node> nodeLookup) {
            this.nodes = nodes;
            this.connections = connections;
            this.nodeLookup = nodeLookup;
        }
    }

    private static final class BranchData {
        final List<Node> nodes;
        final List<NodeConnection> connections;

        BranchData(List<Node> nodes, List<NodeConnection> connections) {
            this.nodes = nodes;
            this.connections = connections;
        }
    }

    public boolean shouldAnimateConnection(NodeConnection connection) {
        if (connection == null || !isExecuting) {
            return false;
        }

        ConnectionKey key = toKey(connection);
        if (key == null) {
            return false;
        }
        if (!activeConnectionLookup.contains(key)) {
            return false;
        }

        Node currentNode = activeNode;
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

    private Node getNextConnectedNode(Node currentNode, List<NodeConnection> connections, int outputSocket) {
        // Use branch-local object identity when traversing to avoid cross-graph collisions when
        // multiple loaded presets contain the same persisted node IDs.
        for (NodeConnection connection : connections) {
            if (connection.getOutputNode() == currentNode) {
                if (connection.getOutputSocket() == outputSocket) {
                    return connection.getInputNode();
                }
            }
        }
        return null;
    }

    private List<Node> findStartNodes(List<Node> nodes) {
        List<Node> startNodes = new ArrayList<>();
        if (nodes == null) {
            return startNodes;
        }

        for (Node node : nodes) {
            if (node.getType() == NodeType.START) {
                startNodes.add(node);
            }
        }

        return startNodes;
    }

    private Set<Node> collectBranchNodes(Node startNode, List<NodeConnection> connections) {
        LinkedHashSet<Node> visited = new LinkedHashSet<>();
        if (startNode == null) {
            return visited;
        }

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(startNode);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }

            Node attachedSensor = current.getAttachedSensor();
            if (attachedSensor != null) {
                stack.push(attachedSensor);
            }

            Node attachedAction = current.getAttachedActionNode();
            if (attachedAction != null) {
                stack.push(attachedAction);
            }

            Map<Integer, Node> attachedParameters = current.getAttachedParameters();
            if (attachedParameters != null && !attachedParameters.isEmpty()) {
                for (Node parameter : attachedParameters.values()) {
                    if (parameter != null) {
                        stack.push(parameter);
                    }
                }
            }

            for (NodeConnection connection : connections) {
                if (connection.getOutputNode() == current) {
                    stack.push(connection.getInputNode());
                }
            }
        }

        return visited;
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

    private Node findStartNodeByNumber(List<Node> nodes, int startNodeNumber) {
        if (nodes == null || startNodeNumber <= 0) {
            return null;
        }
        for (Node startNode : nodes) {
            if (startNode != null && startNode.getType() == NodeType.START
                && startNode.getStartNodeNumber() == startNodeNumber) {
                return startNode;
            }
        }
        return null;
    }

    private BranchData buildBranchData(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        if (startNode == null || nodes == null || connections == null) {
            return null;
        }
        Set<Node> branchNodeSet = collectBranchNodes(startNode, connections);

        for (Node node : nodes) {
            if (node != null && node.getType() == NodeType.EVENT_FUNCTION) {
                branchNodeSet.addAll(collectBranchNodes(node, connections));
            }
        }

        List<Node> branchNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (branchNodeSet.contains(node)) {
                branchNodes.add(node);
            }
        }

        List<NodeConnection> branchConnections = new ArrayList<>();
        for (NodeConnection connection : connections) {
            if (branchNodeSet.contains(connection.getOutputNode()) && branchNodeSet.contains(connection.getInputNode())) {
                branchConnections.add(connection);
            }
        }

        return new BranchData(branchNodes, branchConnections);
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

    private BranchLaunchData createBranchLaunchData(BranchData branchData, int startNodeNumber) {
        BranchData isolatedBranchData = cloneBranchData(branchData);
        if (isolatedBranchData == null || isolatedBranchData.nodes.isEmpty()) {
            return null;
        }

        Node isolatedStartNode = findStartNodeByNumber(isolatedBranchData.nodes, startNodeNumber);
        if (isolatedStartNode == null) {
            return null;
        }

        return new BranchLaunchData(isolatedBranchData, isolatedStartNode);
    }

    private BranchLaunchData createBranchLaunchData(BranchData branchData, Node rootNode) {
        if (rootNode == null) {
            return null;
        }

        BranchData isolatedBranchData = cloneBranchData(branchData);
        if (isolatedBranchData == null || isolatedBranchData.nodes.isEmpty()) {
            return null;
        }

        Node isolatedRootNode = findNodeById(isolatedBranchData.nodes, rootNode.getId());
        if (isolatedRootNode == null) {
            return null;
        }

        return new BranchLaunchData(isolatedBranchData, isolatedRootNode);
    }

    private BranchData cloneBranchData(BranchData branchData) {
        if (branchData == null || branchData.nodes == null || branchData.connections == null) {
            return null;
        }

        NodeGraphData snapshot = createGraphSnapshot(branchData.nodes, branchData.connections);
        List<Node> clonedNodes = NodeGraphPersistence.convertToNodes(snapshot);
        if (clonedNodes == null || clonedNodes.isEmpty()) {
            return null;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : clonedNodes) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }

        List<NodeConnection> clonedConnections = NodeGraphPersistence.convertToConnections(snapshot, nodeMap);
        return new BranchData(clonedNodes, clonedConnections);
    }

    private Node findNodeById(List<Node> nodes, String nodeId) {
        if (nodes == null || nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        for (Node node : nodes) {
            if (node != null && nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private static final class BranchLaunchData {
        final BranchData branchData;
        final Node rootNode;

        BranchLaunchData(BranchData branchData, Node rootNode) {
            this.branchData = branchData;
            this.rootNode = rootNode;
        }
    }

    private NodeGraphData createGraphSnapshot(List<Node> nodes, List<NodeConnection> connections) {
        NodeGraphData snapshot = new NodeGraphData();

        for (Node node : nodes) {
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());

            List<NodeGraphData.ParameterData> parameterDataList = new ArrayList<>();
            for (NodeParameter parameter : node.getParameters()) {
                NodeGraphData.ParameterData parameterData = new NodeGraphData.ParameterData();
                parameterData.setName(parameter.getName());
                parameterData.setValue(parameter.getStringValue());
                parameterData.setType(parameter.getType().name());
                parameterData.setUserEdited(parameter.isUserEdited());
                parameterDataList.add(parameterData);
            }
            nodeData.setParameters(parameterDataList);
            nodeData.setAttachedSensorId(node.getAttachedSensorId());
            nodeData.setParentControlId(node.getParentControlId());
            nodeData.setAttachedActionId(node.getAttachedActionId());
            nodeData.setParentActionControlId(node.getParentActionControlId());
            List<NodeGraphData.ParameterAttachmentData> attachmentData = new ArrayList<>();
            Map<Integer, Node> attachedParameters = node.getAttachedParameters();
            if (attachedParameters != null && !attachedParameters.isEmpty()) {
                List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
                Collections.sort(slotIndices);
                for (Integer slotIndex : slotIndices) {
                    Node parameterNode = attachedParameters.get(slotIndex);
                    if (parameterNode != null) {
                        attachmentData.add(new NodeGraphData.ParameterAttachmentData(slotIndex, parameterNode.getId()));
                    }
                }
            }
            nodeData.setParameterAttachments(attachmentData);
            if (!attachmentData.isEmpty()) {
                nodeData.setAttachedParameterId(attachmentData.get(0).getParameterNodeId());
            } else {
                nodeData.setAttachedParameterId(node.getAttachedParameterId());
            }
            nodeData.setParentParameterHostId(node.getParentParameterHostId());
            if (node.hasBooleanToggle()) {
                nodeData.setBooleanToggleValue(node.getBooleanToggleValue());
            }
            if (node.getType() == NodeType.START) {
                nodeData.setStartNodeNumber(node.getStartNodeNumber());
            }
            if (node.getType() == NodeType.MESSAGE) {
                nodeData.setMessageLines(node.getMessageLines());
                nodeData.setMessageClientSide(node.isMessageClientSide());
            }
            if (node.hasBookTextInput()) {
                nodeData.setBookText(node.getBookText());
            }
            if (node.getType() == NodeType.GOTO) {
                nodeData.setGotoAllowBreakWhileExecuting(node.isGotoAllowBreakWhileExecuting());
                nodeData.setGotoAllowPlaceWhileExecuting(node.isGotoAllowPlaceWhileExecuting());
            }
            if (node.getType() == NodeType.TEMPLATE || node.getType() == NodeType.CUSTOM_NODE) {
                nodeData.setTemplateName(node.getTemplateName());
                nodeData.setTemplateVersion(node.getTemplateVersion());
                nodeData.setCustomNodeInstance(node.isCustomNodeInstance());
                nodeData.setTemplateGraph(node.getTemplateGraphData());
            }

            snapshot.getNodes().add(nodeData);
        }

        for (NodeConnection connection : filterConnections(connections)) {
            NodeGraphData.ConnectionData connectionData = new NodeGraphData.ConnectionData(
                    connection.getOutputNode().getId(),
                    connection.getInputNode().getId(),
                    connection.getOutputSocket(),
                    connection.getInputSocket()
            );
            snapshot.getConnections().add(connectionData);
        }

        return snapshot;
    }

    private List<NodeConnection> filterConnections(List<NodeConnection> connections) {
        List<NodeConnection> filtered = new ArrayList<>();
        if (connections == null) {
            return filtered;
        }
        for (NodeConnection connection : connections) {
            if (connection == null) {
                continue;
            }
            Node output = connection.getOutputNode();
            Node input = connection.getInputNode();
            if (output == null || input == null) {
                continue;
            }
            if (output.isSensorNode() || input.isSensorNode()) {
                continue;
            }
            if (connection.getOutputSocket() < 0 || connection.getOutputSocket() >= output.getOutputSocketCount()) {
                continue;
            }
            filtered.add(connection);
        }
        return filtered;
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
