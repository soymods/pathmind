package com.pathmind.execution;

import com.pathmind.nodes.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ExecutionSessionState {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long MINIMUM_DISPLAY_DURATION = 3000;
    private static final ThreadLocal<Integer> CURRENT_EXECUTION_ID = new ThreadLocal<>();

    private Node activeNode;
    private boolean executing;
    private long executionStartTime;
    private long executionEndTime;
    final Map<Integer, Node> activeExecutionNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> executionNodeStartTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> executionNodePausedDurations = new ConcurrentHashMap<>();
    private final Map<Integer, Long> executionNodePauseStartTimes = new ConcurrentHashMap<>();
    private final AtomicInteger nextExecutionId = new AtomicInteger(1);
    private Integer primaryExecutionId;
    private boolean globalExecutionActive;
    private long activeNodeStartTime;
    private long activeNodePausedDuration;
    private long activeNodePauseStartTime;
    private long activeNodeEndTime;
    private boolean singleplayerPaused;

    void start(List<Node> startNodes, boolean markGlobal) {
        clearExecutionNodes();
        this.activeNode = startNodes.isEmpty() ? null : startNodes.get(0);
        if (this.activeNode != null) {
            resetActiveNodeTiming();
        } else {
            clearActiveNodeTiming();
        }
        this.executing = true;
        this.globalExecutionActive = markGlobal;
        this.executionStartTime = System.currentTimeMillis();
        this.executionEndTime = 0;
    }

    void setActiveNode(Node node) {
        setActiveNode(node, primaryExecutionId != null ? primaryExecutionId : -1);
    }

    void setActiveNode(Node node, int executionId) {
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

    void stop(boolean cancelled) {
        this.executing = false;
        this.globalExecutionActive = false;
        if (cancelled) {
            this.executionEndTime = 0;
            this.executionStartTime = 0;
            this.activeNode = null;
            clearActiveNodeTiming();
        } else {
            this.executionEndTime = System.currentTimeMillis();
            this.activeNodeEndTime = this.executionEndTime;
        }
    }

    void reset() {
        this.executing = false;
        this.globalExecutionActive = false;
        this.activeNode = null;
        clearActiveNodeTiming();
        this.executionStartTime = 0;
        this.executionEndTime = 0;
        clearExecutionNodes();
    }

    Node getActiveNode() {
        return activeNode;
    }

    boolean isExecutionActiveOnNode(Integer executionId, String nodeId) {
        if (executionId == null || executionId < 0 || nodeId == null || nodeId.isEmpty()) {
            return false;
        }
        Node active = activeExecutionNodes.get(executionId);
        return active != null && nodeId.equals(active.getId());
    }

    long getExecutionNodeDuration(Integer executionId) {
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

    Integer getCurrentExecutionId() {
        return CURRENT_EXECUTION_ID.get();
    }

    Node getActiveExecutionNode(Integer executionId) {
        return executionId != null ? activeExecutionNodes.get(executionId) : null;
    }

    void runWithExecutionContext(int executionId, Runnable runnable) {
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

    List<Node> getActiveNodeChainSnapshot() {
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

    boolean isExecuting() {
        if (executing) {
            return true;
        }
        return isCompletionDisplayActive();
    }

    boolean isActivelyExecuting() {
        return executing;
    }

    void markExecuting() {
        this.executing = true;
    }

    boolean isDisplayingCompletion() {
        if (executing) {
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

    boolean isGlobalExecutionActive() {
        return globalExecutionActive;
    }

    long getExecutionStartTime() {
        return executionStartTime;
    }

    long getExecutionDuration() {
        if (executionStartTime == 0) {
            return 0;
        }

        if (executing) {
            return System.currentTimeMillis() - executionStartTime;
        } else if (executionEndTime > 0) {
            return executionEndTime - executionStartTime;
        }

        return 0;
    }

    long getActiveNodeDuration() {
        if (activeNodeStartTime == 0) {
            return 0;
        }

        long referenceTime;
        if (executing) {
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

    void setSingleplayerPaused(boolean paused) {
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

    boolean isExecutionPaused() {
        return singleplayerPaused;
    }

    void resetActiveNodeTiming() {
        this.activeNodeStartTime = System.currentTimeMillis();
        this.activeNodePausedDuration = 0;
        this.activeNodeEndTime = 0;
        this.activeNodePauseStartTime = singleplayerPaused ? activeNodeStartTime : 0;
    }

    void clearActiveNodeTiming() {
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

    void clearExecutionTiming(int executionId) {
        if (executionId < 0) {
            return;
        }
        executionNodeStartTimes.remove(executionId);
        executionNodePausedDurations.remove(executionId);
        executionNodePauseStartTimes.remove(executionId);
    }

    void removeExecution(int executionId) {
        activeExecutionNodes.remove(executionId);
        clearExecutionTiming(executionId);
        if (primaryExecutionId != null && primaryExecutionId == executionId) {
            refreshPrimaryExecution();
        }
    }

    int allocateExecutionId() {
        return nextExecutionId.getAndIncrement();
    }

    void refreshPrimaryExecution() {
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

    void clearExecutionNodes() {
        activeExecutionNodes.clear();
        executionNodeStartTimes.clear();
        executionNodePausedDurations.clear();
        executionNodePauseStartTimes.clear();
        primaryExecutionId = null;
    }
}
