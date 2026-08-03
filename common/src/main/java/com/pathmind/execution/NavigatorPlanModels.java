package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;

enum GoalMode {
    EXACT,
    NEAREST_STANDABLE
}

record SearchNode(BlockPos pos, double fScore, double gScore) {
}

record CoarseSearchNode(BlockPos pos, double fScore, double gScore) {
}

record Neighbor(BlockPos pos, double cost, PlannedPrimitive primitive) {
}

record CoarseNeighbor(BlockPos pos, double cost, PlannedPrimitive primitive) {
}

record Move(int dx, int dz, double cost) {
}

enum SearchPrimitiveType {
    WALK,
    INTERACT,
    BREAK_FORWARD,
    PLACE_FORWARD,
    JUMP_ASCEND,
    MINE_ASCEND,
    DESCEND,
    CLIMB,
    SWIM,
    PILLAR
}

enum PlannedPrimitiveType {
    WALK,
    CLIMB,
    DESCEND,
    JUMP_ASCEND,
    MINE_FORWARD,
    MINE_ASCEND,
    PILLAR,
    SWIM,
    INTERACTABLE
}

enum PrimitiveTraversal {
    GROUND,
    ASCENT,
    VERTICAL_ASCENT,
    DESCENT,
    CLIMB,
    SWIM,
    INTERACTABLE
}

enum PrimitiveExecution {
    CONTINUOUS_MOVEMENT,
    COMMITTED_MOVEMENT,
    BREAK_THEN_MOVE,
    PLACE_THEN_MOVE,
    INTERACT_THEN_MOVE
}

record PlannedPrimitive(
    BlockPos target,
    SearchPrimitiveType searchType,
    PlannedPrimitiveType type,
    PrimitiveTraversal traversal,
    PrimitiveExecution execution,
    int deltaY,
    int horizontalStepCount,
    boolean sameColumn,
    List<BlockPos> breakTargets,
    BlockPos placeTarget
) {
    boolean requiresBreak() {
        return breakTargets != null && !breakTargets.isEmpty();
    }

    boolean requiresPlace() {
        return placeTarget != null;
    }

    boolean requiresWorldModification() {
        return requiresBreak() || requiresPlace();
    }

    boolean isPillar() {
        return type == PlannedPrimitiveType.PILLAR;
    }

    boolean isClimb() {
        return traversal == PrimitiveTraversal.CLIMB;
    }

    boolean isDescend() {
        return traversal == PrimitiveTraversal.DESCENT;
    }

    boolean isSwim() {
        return traversal == PrimitiveTraversal.SWIM;
    }

    boolean isInteractable() {
        return traversal == PrimitiveTraversal.INTERACTABLE;
    }

    boolean isJump() {
        return type == PlannedPrimitiveType.JUMP_ASCEND || type == PlannedPrimitiveType.MINE_ASCEND;
    }

    boolean isMineAscent() {
        return searchType == SearchPrimitiveType.MINE_ASCEND;
    }

    boolean isSimpleMovementStep() {
        if (searchType == null) {
            return false;
        }
        return switch (searchType) {
            case WALK -> !requiresWorldModification();
            case DESCEND -> !requiresWorldModification();
            case BREAK_FORWARD, PLACE_FORWARD, INTERACT, JUMP_ASCEND, MINE_ASCEND, CLIMB, SWIM, PILLAR -> false;
        };
    }

    boolean shouldCommitAscent(BlockPos waypoint, BlockPos playerFootPos) {
        return isJump()
            && waypoint != null
            && playerFootPos != null
            && waypoint.getY() > playerFootPos.getY();
    }

    boolean shouldCommitDrop(BlockPos waypoint, BlockPos playerFootPos) {
        return isDescend()
            && waypoint != null
            && playerFootPos != null
            && waypoint.getY() < playerFootPos.getY();
    }

    boolean isCommittedTraversal() {
        return execution == PrimitiveExecution.COMMITTED_MOVEMENT
            || execution == PrimitiveExecution.BREAK_THEN_MOVE
            || execution == PrimitiveExecution.PLACE_THEN_MOVE
            || execution == PrimitiveExecution.INTERACT_THEN_MOVE;
    }

    boolean isPassiveTraversal() {
        return !requiresWorldModification()
            && (traversal == PrimitiveTraversal.GROUND
            || traversal == PrimitiveTraversal.DESCENT
            || traversal == PrimitiveTraversal.CLIMB
            || traversal == PrimitiveTraversal.SWIM);
    }

    boolean requiresCommittedAction() {
        return isCommittedTraversal() || isPillar();
    }

    boolean allowsForwardResync() {
        if (searchType == null) {
            return true;
        }
        return switch (searchType) {
            case WALK, BREAK_FORWARD, INTERACT, DESCEND -> true;
            case PLACE_FORWARD, JUMP_ASCEND, MINE_ASCEND, CLIMB, SWIM, PILLAR -> false;
        };
    }
}

record ScoredPos(BlockPos pos, double score) {
}

record PathComputation(
    List<BlockPos> path,
    List<PlannedPrimitive> plannedPrimitives,
    List<List<BlockPos>> candidatePaths,
    BlockPos resolvedGoalPos,
    GoalMode goalMode,
    FailureReason failureReason,
    String failureDetail
) {
}

record GoalSearchOutcome(List<ScoredPath> scoredPaths, FailureReason failureReason, String failureDetail) {
}

record PathSearchResult(
    List<BlockPos> path,
    List<PlannedPrimitive> plannedPrimitives,
    double cost,
    boolean timedOut,
    FailureReason failureReason,
    String failureDetail
) {
}

record ScoredPath(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives, double cost) {
}

record ReconstructedPath(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
}

enum FailureReason {
    CLIENT_UNAVAILABLE("Pathmind Nav failed: client or world unavailable."),
    NO_START_SPACE("Pathmind Nav failed: no valid space to start pathfinding from your position."),
    NO_GOAL_SPACE("Pathmind Nav failed: no standable block near the target."),
    NO_LOADED_FRONTIER("Pathmind Nav failed: no reachable loaded terrain toward the target yet."),
    SEARCH_LIMIT("Pathmind Nav failed: search complexity limit reached before finding a route."),
    NO_ROUTE("Pathmind Nav failed: no walking route to the target.");

    final String message;

    FailureReason(String message) {
        this.message = message;
    }
}
