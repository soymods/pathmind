package com.pathmind.execution;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.util.BaritoneApiProxy;

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
    private final List<String> executingEvents;
    private volatile boolean cancelRequested;
    private final Map<Node, ChainController> activeChains;
    private final Map<ConnectionKey, Node> eventConnectionOwners;
    private final Set<Node> activeEventFunctionNodes;
    private boolean globalExecutionActive;
    private boolean lastSnapshotWasGlobal;
    private long activeNodeStartTime;
    private long activeNodePausedDuration;
    private long activeNodePauseStartTime;
    private long activeNodeEndTime;
    private boolean singleplayerPaused;
    private Integer lastStartNodeNumber;
    private String lastStartPreset;

    private static final long NODE_EXECUTION_DELAY_MS = 150L;

    private static class ChainController {
        final Node startNode;
        volatile boolean cancelRequested;
        final Map<String, RuntimeVariable> runtimeVariables;

        ChainController(Node startNode) {
            this.startNode = startNode;
            this.cancelRequested = false;
            this.runtimeVariables = new ConcurrentHashMap<>();
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
        this.executingEvents = new ArrayList<>();
        this.cancelRequested = false;
        this.activeChains = new ConcurrentHashMap<>();
        this.globalExecutionActive = false;
        this.lastSnapshotWasGlobal = false;
        this.activeConnectionLookup = ConcurrentHashMap.newKeySet();
        this.outputNodeLookup = new ConcurrentHashMap<>();
        this.eventConnectionOwners = new ConcurrentHashMap<>();
        this.activeEventFunctionNodes = ConcurrentHashMap.newKeySet();
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
            return false;
        }
        controller.runtimeVariables.put(name.trim(), value);
        return true;
    }

    public RuntimeVariable getRuntimeVariable(Node startNode, String name) {
        if (startNode == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            return null;
        }
        return controller.runtimeVariables.get(name.trim());
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
        this.activeNodes = new ArrayList<>(nodes);
        this.activeConnections = new ArrayList<>(filteredConnections);
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        startExecution(startNodes, markGlobalSnapshot);
        activeChains.clear();

        for (Node startNode : startNodes) {
            ChainController controller = new ChainController(startNode);
            activeChains.put(startNode, controller);
            CompletableFuture<Void> chainFuture = runChain(startNode, controller);
            chainFuture.whenComplete((ignored, throwable) -> handleChainCompletion(controller, throwable));
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
        if (!playLastStartNodeGraphFromWorkspace()) {
            LOGGER.debug("No last START chain available to play");
            PreciseCompletionTracker.notifyPlayerMessage("No last START chain available to play.");
        }
    }

    public boolean executeBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        return executeBranch(startNode, nodes, connections, PresetManager.getActivePreset());
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
        this.activeNodes = branchNodes;
        this.activeConnections = branchConnections;
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        if (activeChains.isEmpty()) {
            startExecution(Collections.singletonList(startNode), false);
        } else {
            this.isExecuting = true;
        }

        ChainController controller = new ChainController(startNode);
        activeChains.put(startNode, controller);
        CompletableFuture<Void> chainFuture = runChain(startNode, controller);
        chainFuture.whenComplete((ignored, throwable) -> handleChainCompletion(controller, throwable));
        updateLastStartContext(startNode, presetName);
        return true;
    }
    
    /**
     * Start execution with the given start node
     */
    private void startExecution(List<Node> startNodes, boolean markGlobal) {
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
        this.activeNode = node;
        if (node != null) {
            resetActiveNodeTiming();
        } else {
            clearActiveNodeTiming();
        }
        LOGGER.trace("Set active node to {}", node != null ? node.getType() : "null");
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
        this.activeChains.clear();
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

    public boolean requestStopForStart(Node startNode) {
        if (startNode == null) {
            return false;
        }

        ChainController controller = activeChains.get(startNode);
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

        Node match = findWorkspaceStartNode(startNodeNumber);

        if (match == null) {
            LOGGER.debug("No START node found for number {}", startNodeNumber);
            return false;
        }
        if (isChainActive(match)) {
            LOGGER.debug("START node already executing, ignoring start request");
            return false;
        }

        List<NodeConnection> filteredConnections = filterConnections(workspaceConnections);
        BranchData branchData = buildBranchData(match, workspaceNodes, filteredConnections);
        if (branchData == null || branchData.nodes.isEmpty()) {
            LOGGER.debug("START node {} has no executable branch", startNodeNumber);
            return false;
        }

        mergeActiveGraph(branchData.nodes, branchData.connections);

        if (!isExecuting && activeChains.isEmpty()) {
            startExecution(Collections.singletonList(match), globalExecutionActive);
        } else {
            this.isExecuting = true;
        }

        ChainController controller = new ChainController(match);
        activeChains.put(match, controller);
        CompletableFuture<Void> chainFuture = runChain(match, controller);
        chainFuture.whenComplete((ignored, throwable) -> handleChainCompletion(controller, throwable));
        return true;
    }

    public boolean isChainActive(Node startNode) {
        if (startNode == null) {
            return false;
        }
        ChainController controller = activeChains.get(startNode);
        return controller != null && !controller.cancelRequested;
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
        if (paused) {
            if (activeNode != null) {
                this.activeNodePauseStartTime = System.currentTimeMillis();
            }
        } else if (activeNodePauseStartTime > 0) {
            this.activeNodePausedDuration += System.currentTimeMillis() - this.activeNodePauseStartTime;
            this.activeNodePauseStartTime = 0;
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

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        currentNode.setOwningStartNode(controller.startNode);

        return waitForExecutionResume()
            .thenCompose(ignored -> scheduleNodeStartDelay())
            .thenCompose(ignored -> {
                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }

                setActiveNode(currentNode);

                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }

                return waitForExecutionResume()
                    .thenCompose(pausedIgnored -> currentNode.execute())
                    .thenCompose(ignoredFuture -> {
                        if (cancelRequested || controller.cancelRequested) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return handleEventCallIfNeeded(currentNode, controller);
                    })
                    .thenCompose(ignoredFuture -> waitForExecutionResume())
                    .thenCompose(ignoredFuture -> continueFromNode(currentNode, controller));
            });
    }

    private CompletableFuture<Void> scheduleNodeStartDelay() {
        if (NODE_EXECUTION_DELAY_MS <= 0L) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> { },
            CompletableFuture.delayedExecutor(NODE_EXECUTION_DELAY_MS, TimeUnit.MILLISECONDS));
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

    private CompletableFuture<Void> handleEventCallIfNeeded(Node node, ChainController controller) {
        if (cancelRequested || controller.cancelRequested || node.getType() != NodeType.EVENT_CALL) {
            return CompletableFuture.completedFuture(null);
        }

        NodeParameter nameParam = node.getParameter("Name");
        String eventName = normalizeEventName(nameParam != null ? nameParam.getStringValue() : null);
        if (eventName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (executingEvents.contains(eventName)) {
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
            return CompletableFuture.completedFuture(null);
        }

        executingEvents.add(eventName);
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Node handler : handlers) {
            if (cancelRequested || controller.cancelRequested) {
                break;
            }
            chain = chain.thenCompose(ignored -> runEventHandler(handler, controller));
        }
        return chain.whenComplete((ignored, throwable) -> executingEvents.remove(eventName));
    }

    private CompletableFuture<Void> continueFromNode(Node currentNode, ChainController controller) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        int nextSocket = currentNode.consumeNextOutputSocket();

        if (currentNode.hasAttachedActionNode()) {
            Node attachedAction = currentNode.getAttachedActionNode();
            NodeType type = currentNode.getType();

            if (attachedAction != null) {
                if (type == NodeType.CONTROL_FOREVER && nextSocket != Node.NO_OUTPUT) {
                    return runChain(attachedAction, controller)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller);
                        });
                }

                if ((type == NodeType.CONTROL_REPEAT || type == NodeType.CONTROL_REPEAT_UNTIL) && nextSocket == 0) {
                    return runChain(attachedAction, controller)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller);
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
            return runChain(nextNode, controller);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> runEventHandler(Node handler, ChainController controller) {
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }

        setEventFunctionActive(handler, true);
        return runChain(handler, controller).whenComplete((ignored, throwable) -> setEventFunctionActive(handler, false));
    }

    private void handleChainCompletion(ChainController controller, Throwable throwable) {
        if (controller == null) {
            return;
        }

        if (throwable != null && !cancelRequested && !controller.cancelRequested) {
            LOGGER.error("Error during execution", throwable);
        }

        activeChains.remove(controller.startNode);

        if (activeChains.isEmpty() && isExecuting) {
            stopExecution();
            activeNodes.clear();
            activeConnections.clear();
            activeConnectionLookup.clear();
            outputNodeLookup.clear();
            executingEvents.clear();
            eventConnectionOwners.clear();
            activeEventFunctionNodes.clear();
        }
    }

    private boolean executeGraphSnapshot(NodeGraphData graphData, boolean markGlobalSnapshot) {
        LoadedGraph loadedGraph = buildGraphFromData(graphData);
        if (loadedGraph == null || loadedGraph.nodes.isEmpty()) {
            return false;
        }

        executeGraphInternal(loadedGraph.nodes, loadedGraph.connections, markGlobalSnapshot);
        return true;
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
        // Use O(1) lookup map when available
        if (currentNode != null && currentNode.getId() != null) {
            String lookupKey = currentNode.getId() + ":" + outputSocket;
            Node result = outputNodeLookup.get(lookupKey);
            if (result != null) {
                return result;
            }
        }
        // Fallback to linear search if lookup map not populated
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
            Map<ConnectionKey, NodeConnection> mergedConnections = new HashMap<>();
            for (NodeConnection connection : activeConnections) {
                ConnectionKey key = toKey(connection);
                if (key != null) {
                    mergedConnections.put(key, connection);
                }
            }
            for (NodeConnection connection : branchConnections) {
                ConnectionKey key = toKey(connection);
                if (key != null && !mergedConnections.containsKey(key)) {
                    mergedConnections.put(key, connection);
                }
            }
            this.activeConnections = new ArrayList<>(mergedConnections.values());
        }

        rebuildConnectionState(this.activeNodes, this.activeConnections);
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
