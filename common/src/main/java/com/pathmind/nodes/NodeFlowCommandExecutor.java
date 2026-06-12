package com.pathmind.nodes;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class NodeFlowCommandExecutor {
    private final Node owner;
    private final NodeType type;

    NodeFlowCommandExecutor(Node owner) {
        this.owner = owner;
        this.type = owner.getType();
    }

    void executeStopChainNode(CompletableFuture<Void> future) {
        Node owningStart = owner.getOwningStartNode();
        ExecutionManager manager = ExecutionManager.getInstance();
        int targetNumber = owner.getIntParameter("StartNumber", 0);

        if (targetNumber > 0) {
            boolean stopped = manager.requestStopForStartNumber(targetNumber);
            if (!stopped) {
                manager.requestStopAll();
            }
            future.complete(null);
            return;
        }

        if (owningStart == null) {
            manager.requestStopAll();
        } else {
            boolean stopped = manager.requestStopForStart(owningStart);
            if (!stopped) {
                manager.requestStopAll();
            }
        }

        future.complete(null);
    }

    void executeStartChainNode(CompletableFuture<Void> future) {
        int targetNumber = owner.getIntParameter("StartNumber", 0);
        if (targetNumber <= 0) {
            future.complete(null);
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        manager.requestStartForStartNumber(targetNumber);
        future.complete(null);
    }

    void executeRunPresetNode(CompletableFuture<Void> future) {
        String requestedPreset = owner.getStringParameter("Preset", "");
        String presetName = requestedPreset != null ? requestedPreset.trim() : "";
        if (presetName.isEmpty()) {
            presetName = PresetManager.getActivePreset();
        }

        List<String> availablePresets = PresetManager.getAvailablePresets();
        for (String available : availablePresets) {
            if (available != null && available.equalsIgnoreCase(presetName)) {
                presetName = available;
                break;
            }
        }

        NodeGraphData graphData = NodeGraphPersistence.loadNodeGraphForPreset(presetName);
        if (graphData == null || graphData.getNodes() == null || graphData.getNodes().isEmpty()) {
            future.complete(null);
            return;
        }

        List<Node> nodes = NodeGraphPersistence.convertToNodes(graphData);
        if (nodes == null || nodes.isEmpty()) {
            future.complete(null);
            return;
        }
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            if (node != null && node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
        }
        List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(graphData, nodeMap);

        List<Node> presetStarts = new ArrayList<>();
        for (Node candidate : nodes) {
            if (candidate != null && candidate.getType() == NodeType.START) {
                presetStarts.add(candidate);
            }
        }
        if (presetStarts.isEmpty()) {
            future.complete(null);
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        int started = 0;
        List<CompletableFuture<Void>> nestedFutures = new ArrayList<>();
        for (Node startNode : presetStarts) {
            if (type == NodeType.CUSTOM_NODE || type == NodeType.TEMPLATE) {
                CompletableFuture<Void> nestedFuture = manager.executeExternalBranchAndWait(startNode, nodes, connections, presetName);
                if (nestedFuture != null) {
                    started++;
                    nestedFutures.add(nestedFuture);
                }
            } else if (manager.executeExternalBranch(startNode, nodes, connections, presetName)) {
                started++;
            }
        }

        if (started == 0) {
            future.complete(null);
        } else if (type == NodeType.CUSTOM_NODE || type == NodeType.TEMPLATE) {
            manager.deferCompletion(CompletableFuture.allOf(nestedFutures.toArray(new CompletableFuture[0])))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(null);
                    }
                });
        } else {
            future.complete(null);
        }
    }

    void executeStopAllNode(CompletableFuture<Void> future) {
        ExecutionManager.getInstance().requestStopAll();
        future.complete(null);
    }
        void executeWaitCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        double baseDuration = Math.max(0.0, owner.getDoubleParameter("Duration", 1.0));
        Double attachedDurationSeconds = owner.runtimeState().runtimeParameterData != null ? owner.runtimeState().runtimeParameterData.durationSeconds : null;

        final double waitSeconds;
        NodeMode waitMode = owner.getMode() != null ? owner.getMode() : NodeMode.WAIT_SECONDS;
        if (attachedDurationSeconds != null) {
            waitSeconds = attachedDurationSeconds;
        } else {
            double unitSeconds;
            switch (waitMode) {
                case WAIT_TICKS:
                    unitSeconds = 0.05;
                    break;
                case WAIT_MINUTES:
                    unitSeconds = 60.0;
                    break;
                case WAIT_HOURS:
                    unitSeconds = 3600.0;
                    break;
                case WAIT_SECONDS:
                default:
                    unitSeconds = 1.0;
                    break;
            }
            waitSeconds = baseDuration * unitSeconds;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Integer executionId = manager.getCurrentExecutionId();

        new Thread(() -> {
            try {
                String nodeId = owner.getId();
                long waitMs = (long) (waitSeconds * 1000);

                while (true) {
                    if (owner.shouldAbortForRepeatUntilGuard()) {
                        future.complete(null);
                        return;
                    }
                    if (!manager.isExecutionActiveOnNode(executionId, nodeId)) {
                        future.complete(null);
                        return;
                    }
                    if (manager.isExecutionPaused()) {
                        Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
                        continue;
                    }
                    if (manager.getExecutionNodeDuration(executionId) >= waitMs) {
                        future.complete(null);
                        return;
                    }
                    Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait").start();
    }
    
    void executeControlRepeat(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        int count = Math.max(0, owner.getIntParameter("Count", 1));
        if (!owner.runtimeState().repeatActive) {
            owner.runtimeState().repeatRemainingIterations = count;
            owner.runtimeState().repeatActive = true;
        }
        if (owner.runtimeState().repeatRemainingIterations > 0) {
            owner.runtimeState().repeatRemainingIterations--;
            owner.runtimeState().repeatExecuteAttachedAction = true;
            owner.setNextOutputSocket(0);
        } else {
            owner.runtimeState().repeatRemainingIterations = 0;
            owner.runtimeState().repeatActive = false;
            owner.runtimeState().repeatExecuteAttachedAction = false;
            owner.setNextOutputSocket(0);
        }
        future.complete(null);
    }
    
    void executeControlRepeatUntil(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean conditionMet = owner.evaluateConditionFromParameters();
        if (conditionMet) {
            owner.runtimeState().repeatRemainingIterations = 0;
            owner.runtimeState().repeatActive = false;
            owner.setNextOutputSocket(1);
        } else {
            owner.runtimeState().repeatActive = true;
            owner.setNextOutputSocket(0);
        }
        future.complete(null);
    }

    void executeControlWaitUntil(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (owner.evaluateConditionFromParameters()) {
            owner.setNextOutputSocket(0);
            future.complete(null);
            return;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Integer executionId = manager.getCurrentExecutionId();

        new Thread(() -> {
            try {
                String nodeId = owner.getId();
                while (true) {
                    if (owner.shouldAbortForRepeatUntilGuard()) {
                        future.complete(null);
                        return;
                    }
                    if (!manager.isExecutionActiveOnNode(executionId, nodeId)) {
                        future.complete(null);
                        return;
                    }
                    if (manager.isExecutionPaused()) {
                        Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
                        continue;
                    }
                    if (owner.evaluateConditionFromParameters()) {
                        owner.setNextOutputSocket(0);
                        future.complete(null);
                        return;
                    }
                    Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait-Until").start();
    }

    void executeControlForever(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        owner.runtimeState().repeatActive = true;
        owner.setNextOutputSocket(0);
        future.complete(null);
    }

    void executeControlIf(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = owner.evaluateConditionFromParameters();
        owner.setNextOutputSocket(condition ? 0 : Node.NO_OUTPUT);
        future.complete(null);
    }

    void executeControlIfElse(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = owner.evaluateConditionFromParameters();
        owner.setNextOutputSocket(condition ? 0 : 1);
        future.complete(null);
    }

    void executeControlFork(CompletableFuture<Void> future) {
        future.complete(null);
    }

    void executeControlJoinAny(CompletableFuture<Void> future) {
        future.complete(null);
    }

    void executeControlJoinAll(CompletableFuture<Void> future) {
        future.complete(null);
    }
}
