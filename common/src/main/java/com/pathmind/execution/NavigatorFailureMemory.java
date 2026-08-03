package com.pathmind.execution;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

final class NavigatorFailureMemory {
    enum Action {
        BREAK,
        JUMP,
        DROP,
        PLACE,
        PILLAR
    }

    private final Map<Edge, Long> failedEdges = new HashMap<>();
    private final Map<BlockPos, Long> failedNodes = new HashMap<>();
    private final Map<Action, Map<Edge, Long>> failedActions = new HashMap<>();

    NavigatorFailureMemory() {
        for (Action action : Action.values()) {
            failedActions.put(action, new HashMap<>());
        }
    }

    void clear() {
        failedEdges.clear();
        failedNodes.clear();
        for (Map<Edge, Long> failures : failedActions.values()) {
            failures.clear();
        }
    }

    void rememberMove(BlockPos from, BlockPos to, long now, long duration, boolean protectedGoal) {
        if (to != null && !protectedGoal) {
            failedNodes.put(to.immutable(), now + duration);
        }
        if (from != null && to != null && !protectedGoal) {
            failedEdges.put(new Edge(from.immutable(), to.immutable()), now + duration);
        }
    }

    void rememberAction(Action action, BlockPos from, BlockPos to, long now, long duration) {
        if (action != null && from != null && to != null) {
            failedActions.get(action).put(new Edge(from.immutable(), to.immutable()), now + duration);
        }
    }

    void prune(long now) {
        failedNodes.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedEdges.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (Map<Edge, Long> failures : failedActions.values()) {
            failures.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
    }

    boolean isFailedNode(BlockPos pos, long now) {
        return pos != null && isActive(failedNodes.get(pos), now);
    }

    boolean isFailedEdge(BlockPos from, BlockPos to, long now) {
        return from != null && to != null && isActive(failedEdges.get(new Edge(from, to)), now);
    }

    boolean isFailedAction(Action action, BlockPos from, BlockPos to, long now) {
        return action != null
            && from != null
            && to != null
            && isActive(failedActions.get(action).get(new Edge(from, to)), now);
    }

    private boolean isActive(Long expiresAt, long now) {
        return expiresAt != null && expiresAt > now;
    }

    private record Edge(BlockPos from, BlockPos to) {
    }
}
