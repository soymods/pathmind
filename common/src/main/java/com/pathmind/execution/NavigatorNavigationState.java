package com.pathmind.execution;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class NavigatorNavigationState {
    long lastProgressAtMs;
    long lastPlanAtMs;
    double bestDistanceSq = Double.MAX_VALUE;
    GoalMode goalMode = GoalMode.EXACT;
    BlockPos resolvedGoalPos;
    BlockPos committedPathGoalPos;
    BlockPos committedPathStartPos;
    List<BlockPos> currentPath = List.of();
    List<PlannedPrimitive> currentPlan = List.of();
    List<List<BlockPos>> candidatePaths = List.of();
    long candidatePathsVisibleUntilMs;
    long lastWaypointAdvanceAtMs;
    int pathIndex;
    int furthestVisitedPathIndex;
    BlockPos activeWaypoint;
    long routeCommitUntilMs;
    long lastLocalRecoveryAtMs;
    int localRecoveryAttempts;
    int bestRouteProgressScore = Integer.MIN_VALUE;
    int consecutivePlanningBudgetExhaustions;
    Vec3 lastMovementSamplePos = Vec3.ZERO;
    long lastMovementAtMs;
    long lastDistanceCheckpointAtMs;
    String lastReplanReason = "none";
    String lastStuckReason = "none";
    String lastAdvanceDecision = "none";
    String lastReplanDecision = "none";
    String lastReplaceDecision = "none";
}
