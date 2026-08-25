package com.pathmind.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.pathmind.execution.PathmindNavigator.WAYPOINT_REACHED_DISTANCE_SQ;
import static com.pathmind.execution.PathmindNavigator.WAYPOINT_NEAR_DISTANCE_SQ;
import static com.pathmind.execution.PathmindNavigator.REPLAN_COOLDOWN_MS;
import static com.pathmind.execution.PathmindNavigator.TRAPPED_RECOVERY_COMMIT_MS;
import static com.pathmind.execution.PathmindNavigator.ROUTE_COMMIT_MS;
import static com.pathmind.execution.PathmindNavigator.PATH_DECISION_VISIBILITY_MS;
import static com.pathmind.execution.PathmindNavigator.MAX_DROP_DOWN;

final class NavigatorRouteCoordinator {
    private static final double WAYPOINT_SAFE_EDGE_INSET = 0.24D;
    private static final long JUMP_RECOVERY_GRACE_MS = 700L;
    private static final long BREAK_COMMIT_WINDOW_MS = 1800L;
    private static final long DROP_COMMIT_WINDOW_MS = 1500L;
    private static final long ROUTE_STABILIZATION_MS = 1800L;
    private static final long LOCAL_RECOVERY_COOLDOWN_MS = 550L;
    private static final int MAX_LOCAL_RECOVERY_ATTEMPTS = 2;
    private static final int MAX_PATH_BREAK_LOOKAHEAD = 8;
    private static final int MAX_PLANNING_BUDGET_RETRIES = 6;
    private static final int PROACTIVE_REPLAN_LOOKAHEAD_STEPS = 6;
    private static final double MIN_PROGRESS_FOR_REPLAN_SQ = 9.0D;

    interface Host {
        Object lock();
        boolean allowBlockBreaking();
        boolean allowBlockPlacing();
        BlockPos targetPos();
        void appendDebugEventLocked(String event);
        void setAdvanceDecision(String decision);
        void setReplaceDecision(String decision);
        String formatDebugPos(BlockPos pos);
        String formatPlannedPrimitiveSequence(List<PlannedPrimitive> plan, int limit);
        String formatIndexedPrimitiveSequence(List<PlannedPrimitive> plan, int limit);
        String formatIndexedPath(List<BlockPos> path, int limit);
    }

    private final Host host;
    private final NavigatorExecutionState executionState;
    private final NavigatorNavigationState navigationState;
    private final PathmindPathPlanner pathPlanner;
    private final NavigatorPrimitiveExecutor primitiveExecutor;

    NavigatorRouteCoordinator(Host host, NavigatorExecutionState executionState,
                              NavigatorNavigationState navigationState, PathmindPathPlanner pathPlanner,
                              NavigatorPrimitiveExecutor primitiveExecutor) {
        this.host = host;
        this.executionState = executionState;
        this.navigationState = navigationState;
        this.pathPlanner = pathPlanner;
        this.primitiveExecutor = primitiveExecutor;
    }

    boolean shouldReplan(ClientLevel world, BlockPos start, BlockPos target, long now) {
        synchronized (host.lock()) {
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                if (navigationState.lastPlanAtMs > 0L && now - navigationState.lastPlanAtMs < REPLAN_COOLDOWN_MS) {
                    navigationState.lastReplanDecision = "keep:planning_retry_cooldown";
                    return false;
                }
                navigationState.lastReplanDecision = "replan:no_active_path";
                return true;
            }
            if (primitiveExecutor.isCommittedLocalEscapeChain(now)) {
                navigationState.lastReplanDecision = "keep:escape_chain";
                return false;
            }
            if (isCommittedPillarState(world, start, now)) {
                navigationState.lastReplanDecision = "keep:pillar_state";
                return false;
            }
            if (isRecoveryState(world, start, now)) {
                navigationState.lastReplanDecision = "keep:recovery_state";
                return false;
            }
            if (isExcavatingState(now)) {
                navigationState.lastReplanDecision = "keep:excavating";
                return false;
            }
            if (isJumpExecutionLocked(now, executionState.activePlannedPrimitive)) {
                navigationState.lastReplanDecision = "keep:jump_locked";
                return false;
            }
            boolean committedGoalValid = isPathGoalStillValid(navigationState.currentPath, committedPathGoalLocked(target));
            boolean routeReachesRequestedTarget = isPathGoalStillValid(navigationState.currentPath, target);
            boolean nearCommittedRoute = isPlayerNearPath(start) || isPlayerNearCommittedPathStart(start);
            if (!routeReachesRequestedTarget
                && nearCommittedRoute
                && shouldProactivelyRefreshRouteLocked(target, now)) {
                navigationState.lastReplanDecision = "replan:refresh_partial_route";
                return true;
            }
            if (committedGoalValid && nearCommittedRoute && isWaypointActionable(world, navigationState.activeWaypoint)) {
                navigationState.lastReplanDecision = "keep:committed_route_valid";
                return false;
            }
            if (now < navigationState.routeCommitUntilMs) {
                navigationState.lastReplanDecision = "keep:commit_window";
                return false;
            }
            if (now - navigationState.lastProgressAtMs < 2000L) {
                navigationState.lastReplanDecision = "keep:recent_progress";
                return false;
            }
            if (!isWaypointActionable(world, navigationState.activeWaypoint)) {
                navigationState.lastReplanDecision = "replan:waypoint_not_actionable";
                return true;
            }
            if (!isPlayerNearPath(start)) {
                navigationState.lastReplanDecision = "replan:player_not_near_path";
                return true;
            }
            navigationState.lastReplanDecision = "keep:default";
            return false;
        }
    }
    
    boolean deferPlanningAfterBudgetExhaustion(long now, String detail) {
        synchronized (host.lock()) {
            navigationState.consecutivePlanningBudgetExhaustions++;
            navigationState.lastPlanAtMs = now;
            navigationState.lastReplanReason = "planning budget retry " + navigationState.consecutivePlanningBudgetExhaustions;
            navigationState.lastStuckReason = "planner time budget";
            host.appendDebugEventLocked(
                "planner deferred retry=" + navigationState.consecutivePlanningBudgetExhaustions
                    + " detail=" + (detail == null || detail.isBlank() ? "none" : detail)
            );
            return navigationState.consecutivePlanningBudgetExhaustions <= MAX_PLANNING_BUDGET_RETRIES;
        }
    }
    
    boolean shouldProactivelyRefreshRouteLocked(BlockPos target, long now) {
        if (target == null || navigationState.currentPath.isEmpty() || navigationState.pathIndex < 0) {
            return false;
        }
        if (now - navigationState.lastPlanAtMs < REPLAN_COOLDOWN_MS) {
            return false;
        }
        if (navigationState.pathIndex <= 0 && navigationState.furthestVisitedPathIndex <= 0) {
            return false;
        }
        int remaining = Math.max(0, navigationState.currentPath.size() - navigationState.pathIndex - 1);
        if (remaining > PROACTIVE_REPLAN_LOOKAHEAD_STEPS) {
            return false;
        }
        BlockPos pathEnd = navigationState.currentPath.get(navigationState.currentPath.size() - 1);
        return pathEnd == null
            || pathPlanner.horizontalDistanceSq(pathEnd, target) > 4.0D
            || Math.abs(pathEnd.getY() - target.getY()) > MAX_DROP_DOWN;
    }
    
    BlockPos committedPathGoalLocked(BlockPos fallbackTarget) {
        return navigationState.committedPathGoalPos != null ? navigationState.committedPathGoalPos : fallbackTarget;
    }
    
    boolean isWaypointActionable(Level world, BlockPos waypoint) {
        if (world == null || waypoint == null) {
            return false;
        }
        if (pathPlanner.isNavigableNode(world, waypoint)) {
            return true;
        }
        List<BlockPos> breakTargets = pathPlanner.getRequiredBreakTargets(world, waypoint);
        if (breakTargets == null) {
            return false;
        }
        if (!breakTargets.isEmpty()) {
            return host.allowBlockBreaking();
        }
        if (pathPlanner.needsPlacedSupport(world, waypoint)) {
            return host.allowBlockPlacing() && pathPlanner.canPlaceSupportAt(world, waypoint.below());
        }
        return pathPlanner.resolveSupportSurfaceY(world, waypoint).isPresent() || pathPlanner.isWaterNode(world, waypoint);
    }
    
    boolean isPathGoalStillValid(List<BlockPos> path, BlockPos target) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return pathPlanner.horizontalDistanceSq(last, target) <= 4.0D && Math.abs(last.getY() - target.getY()) <= MAX_DROP_DOWN;
    }
    
    boolean shouldTrackResolvedPlanningGoal(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode) {
        if (goalMode != GoalMode.NEAREST_STANDABLE || target == null || resolvedGoal == null) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(target, resolvedGoal) <= 4.0D
            && Math.abs(target.getY() - resolvedGoal.getY()) <= MAX_DROP_DOWN;
    }
    
    boolean isDirectGoalCompletionCandidate(BlockPos candidate, BlockPos target) {
        if (candidate == null || target == null) {
            return false;
        }
        if (candidate.below().equals(target)) {
            return true;
        }
        if (candidate.getY() != target.getY()) {
            return false;
        }
        int dx = Math.abs(candidate.getX() - target.getX());
        int dz = Math.abs(candidate.getZ() - target.getZ());
        return dx + dz == 1;
    }
    
    boolean isPlayerNearPath(BlockPos playerFootPos) {
        if (playerFootPos == null || navigationState.currentPath.isEmpty()) {
            return false;
        }
        int start = Math.max(0, navigationState.pathIndex - 2);
        int end = Math.min(navigationState.currentPath.size() - 1, navigationState.pathIndex + 6);
        for (int i = start; i <= end; i++) {
            BlockPos step = navigationState.currentPath.get(i);
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= 4.0D && Math.abs(step.getY() - playerFootPos.getY()) <= 2) {
                return true;
            }
        }
        if (host.targetPos() != null && pathPlanner.horizontalDistanceSq(playerFootPos, host.targetPos()) < MIN_PROGRESS_FOR_REPLAN_SQ) {
            return true;
        }
        return false;
    }
    
    boolean isExcavatingState(long now) {
        synchronized (host.lock()) {
            boolean activeRouteMining = executionState.controllerMode == ControllerMode.BREAK_BLOCK
                && executionState.activeBreakTarget != null
                && executionState.controllerUntilMs > now;
            boolean activeEscapeController = executionState.controllerMode == ControllerMode.ESCAPE_HOLE
                || executionState.controllerMode == ControllerMode.RECOVER_ESCAPE
                || executionState.controllerMode == ControllerMode.PILLAR
                || executionState.controllerMode == ControllerMode.RECOVER_PILLAR;
            return activeRouteMining
                || (activeEscapeController
                && (hasCommittedEscapeWorkLocked(now) || isActiveEscapeBreakTargetLocked()));
        }
    }
    
    boolean hasCommittedEscapeWorkLocked(long now) {
        return !executionState.committedEscape.isEmpty()
            && executionState.committedEscapePrimitiveIndex < executionState.committedEscape.primitives().size()
            && executionState.committedEscapeUntilMs > now;
    }
    
    boolean isActiveEscapeBreakTargetLocked() {
        return executionState.activeBreakTarget != null
            && !executionState.committedEscape.isEmpty()
            && executionState.committedEscape.breakTargets().contains(executionState.activeBreakTarget);
    }
    
    boolean isJumpExecutionLocked(long now, PlannedPrimitive plannedPrimitive) {
        synchronized (host.lock()) {
            if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > now) {
                return true;
            }
            return primitiveExecutor.isJumpPrimitive(plannedPrimitive) && now - executionState.lastJumpAtMs <= JUMP_RECOVERY_GRACE_MS;
        }
    }
    
    boolean canRepairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target) {
        if (world == null || playerFootPos == null || target == null) {
            return false;
        }
        synchronized (host.lock()) {
            BlockPos committedGoal = committedPathGoalLocked(target);
            return !navigationState.currentPath.isEmpty()
                && navigationState.pathIndex >= 0
                && navigationState.pathIndex < navigationState.currentPath.size()
                && isPathGoalStillValid(navigationState.currentPath, committedGoal)
                && (isPlayerNearPath(playerFootPos) || isPlayerNearCommittedPathStart(playerFootPos));
        }
    }
    
    boolean shouldKeepCommittedPath(
        Level world,
        BlockPos playerFootPos,
        BlockPos target,
        List<BlockPos> candidatePath,
        List<PlannedPrimitive> candidatePlan,
        long now
    ) {
        if (world == null || playerFootPos == null || target == null) {
            host.setReplaceDecision("replace:invalid_context");
            return false;
        }
        synchronized (host.lock()) {
            BlockPos committedGoal = committedPathGoalLocked(target);
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                navigationState.lastReplaceDecision = "replace:no_committed_path";
                return false;
            }
            boolean nearCommittedRoute = isPlayerNearPath(playerFootPos) || isPlayerNearCommittedPathStart(playerFootPos);
            if (!isPathGoalStillValid(navigationState.currentPath, committedGoal) || !nearCommittedRoute) {
                navigationState.lastReplaceDecision = !isPathGoalStillValid(navigationState.currentPath, committedGoal)
                    ? "replace:committed_goal_invalid"
                    : "replace:not_near_committed_route";
                return false;
            }
            if (!isWaypointActionable(world, navigationState.activeWaypoint)) {
                navigationState.lastReplaceDecision = "replace:active_waypoint_not_actionable";
                return false;
            }
            if (candidatePath == null || candidatePath.isEmpty() || candidatePlan == null || candidatePlan.isEmpty()) {
                navigationState.lastReplaceDecision = "keep:no_candidate";
                return true;
            }
            if (!pathPlanner.isViablePlannedPath(world, candidatePath, candidatePlan)) {
                navigationState.lastReplaceDecision = "keep:candidate_not_viable";
                return true;
            }
            if (hasEquivalentOpeningPrefix(navigationState.currentPath, navigationState.pathIndex, candidatePath, playerFootPos, 4)) {
                navigationState.lastReplaceDecision = "keep:equivalent_opening_prefix";
                return true;
            }
            BlockPos currentEnd = navigationState.currentPath.get(navigationState.currentPath.size() - 1);
            BlockPos candidateEnd = candidatePath.get(candidatePath.size() - 1);
            double currentGoalDistance = goalDistanceScore(currentEnd, committedGoal);
            double candidateGoalDistance = goalDistanceScore(candidateEnd, committedGoal);
            boolean extendingPartialRoute = !isPathGoalStillValid(navigationState.currentPath, target)
                && isMeaningfulPartialRouteExtension(currentEnd, candidateEnd, target, candidatePath.size());
            if (hasEquivalentActiveOpening(navigationState.activeWaypoint, candidatePath)
                && candidateGoalDistance >= currentGoalDistance - 1.0D
                && !extendingPartialRoute) {
                navigationState.lastReplaceDecision = "keep:equivalent_active_opening";
                return true;
            }
            if (extendingPartialRoute) {
                navigationState.lastReplaceDecision = "replace:extend_partial_route";
                return false;
            }
            if (candidateGoalDistance >= currentGoalDistance + 0.75D) {
                navigationState.lastReplaceDecision = "keep:candidate_farther_goal";
                return true;
            }
            if (isJumpExecutionLocked(now, executionState.activePlannedPrimitive)) {
                navigationState.lastReplaceDecision = "keep:jump_locked";
                return true;
            }
            if (isRouteStabilizingLocked(playerFootPos, now)) {
                navigationState.lastReplaceDecision = "keep:route_stabilizing";
                return true;
            }
            if (now < navigationState.routeCommitUntilMs) {
                navigationState.lastReplaceDecision = "keep:commit_window";
                return true;
            }
            if (hasCriticalPrimitiveAheadLocked(navigationState.currentPlan, navigationState.pathIndex, 6)
                && !hasCriticalPrimitive(candidatePlan, 0, 6)) {
                navigationState.lastReplaceDecision = "keep:critical_primitive_ahead";
                return true;
            }
            double currentPenalty = pathPlanner.pathStructurePenalty(navigationState.currentPath, navigationState.currentPlan) + pathPlanner.pathModificationPenalty(navigationState.currentPlan);
            double candidatePenalty = pathPlanner.pathStructurePenalty(candidatePath, candidatePlan) + pathPlanner.pathModificationPenalty(candidatePlan);
            if (candidatePenalty >= currentPenalty - 8.0D
                && candidatePath.size() >= navigationState.currentPath.size() - 2) {
                navigationState.lastReplaceDecision = "keep:candidate_not_materially_better";
                return true;
            }
            if (candidatePenalty > currentPenalty + 12.0D) {
                navigationState.lastReplaceDecision = "keep:candidate_penalty_worse";
                return true;
            }
            boolean keep = candidatePath.size() >= navigationState.currentPath.size() + 4 && candidatePenalty >= currentPenalty;
            navigationState.lastReplaceDecision = keep ? "keep:candidate_longer_without_better_penalty" : "replace:candidate_better";
            return keep;
        }
    }
    
    boolean hasEquivalentOpeningPrefix(
        List<BlockPos> currentPath,
        int currentIndex,
        List<BlockPos> candidatePath,
        BlockPos playerFootPos,
        int lookahead
    ) {
        if (currentPath == null || currentPath.isEmpty() || candidatePath == null || candidatePath.isEmpty() || lookahead <= 0) {
            return false;
        }
        int currentStart = Math.max(0, Math.min(currentIndex, currentPath.size() - 1));
        int matched = 0;
        int candidateIndex = 0;
        for (int currentCursor = currentStart;
             currentCursor < currentPath.size() && candidateIndex < candidatePath.size() && matched < lookahead;
             currentCursor++) {
            BlockPos currentStep = currentPath.get(currentCursor);
            BlockPos candidateStep = candidatePath.get(candidateIndex);
            if (currentStep == null || candidateStep == null) {
                break;
            }
            if (playerFootPos != null) {
                double currentPlayerDistance = pathPlanner.horizontalDistanceSq(playerFootPos, currentStep);
                double candidatePlayerDistance = pathPlanner.horizontalDistanceSq(playerFootPos, candidateStep);
                if (currentPlayerDistance > 25.0D && candidatePlayerDistance > 25.0D) {
                    break;
                }
            }
            if (pathPlanner.horizontalDistanceSq(currentStep, candidateStep) > 2.25D
                || Math.abs(currentStep.getY() - candidateStep.getY()) > 1) {
                return false;
            }
            matched++;
            candidateIndex++;
        }
        return matched >= Math.min(lookahead, Math.min(currentPath.size() - currentStart, candidatePath.size()));
    }
    
    boolean hasEquivalentActiveOpening(BlockPos activeWaypoint, List<BlockPos> candidatePath) {
        if (activeWaypoint == null || candidatePath == null || candidatePath.isEmpty()) {
            return false;
        }
        int end = Math.min(candidatePath.size(), 6);
        for (int i = 0; i < end; i++) {
            BlockPos step = candidatePath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(activeWaypoint, step) <= 4.0D
                && Math.abs(activeWaypoint.getY() - step.getY()) <= 1) {
                return true;
            }
        }
        return false;
    }
    
    boolean isMeaningfulPartialRouteExtension(
        BlockPos currentEnd,
        BlockPos candidateEnd,
        BlockPos committedGoal,
        int candidatePathSize
    ) {
        if (currentEnd == null || candidateEnd == null || committedGoal == null) {
            return false;
        }
        double currentGoalDistance = goalDistanceScore(currentEnd, committedGoal);
        double candidateGoalDistance = goalDistanceScore(candidateEnd, committedGoal);
        return candidateGoalDistance <= currentGoalDistance - 2.0D
            || (candidateGoalDistance < currentGoalDistance && candidatePathSize >= navigationState.currentPath.size() + 3);
    }
    
    double goalDistanceScore(BlockPos pos, BlockPos goal) {
        if (pos == null || goal == null) {
            return Double.POSITIVE_INFINITY;
        }
        return pathPlanner.horizontalDistanceSq(pos, goal) + Math.abs(pos.getY() - goal.getY()) * 1.5D;
    }
    
    ControllerMode updateControllerMode(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        long now,
        double distanceSq
    ) {
        ControllerMode mode = selectControllerMode(world, player, playerFootPos, waypoint, plannedPrimitive, now);
        BlockPos verticalEscapeTarget = primitiveExecutor.selectVerticalEscapeTarget(world, playerFootPos, waypoint);
        synchronized (host.lock()) {
            BlockPos nextTarget = switch (mode) {
                case RECOVER_JUMP, RECOVER_BREAK, RECOVER_PILLAR, RECOVER_ESCAPE -> executionState.controllerTarget != null ? executionState.controllerTarget : waypoint;
                case BREAK_BLOCK -> {
                    BlockPos miningTarget = selectMiningControllerTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
                    yield miningTarget != null ? miningTarget : waypoint;
                }
                case PILLAR -> {
                    if (executionState.controllerMode == ControllerMode.PILLAR
                        && executionState.controllerTarget != null
                        && now <= executionState.controllerUntilMs
                        && executionState.controllerTarget.getX() == playerFootPos.getX()
                        && executionState.controllerTarget.getZ() == playerFootPos.getZ()
                        && executionState.controllerTarget.getY() >= playerFootPos.getY()
                        && executionState.controllerTarget.getY() <= playerFootPos.getY() + 1) {
                        yield executionState.controllerTarget;
                    }
                    yield verticalEscapeTarget != null ? verticalEscapeTarget : waypoint;
                }
                case COMMIT_JUMP, DROP, FOLLOW_PATH -> waypoint;
                case ESCAPE_HOLE -> executionState.committedEscapeTarget != null ? executionState.committedEscapeTarget : waypoint;
            };
            if (mode != executionState.controllerMode || !java.util.Objects.equals(nextTarget, executionState.controllerTarget)) {
                executionState.controllerMode = mode;
                executionState.controllerTarget = nextTarget;
                executionState.controllerEnteredAtMs = now;
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = distanceSq;
            }
            executionState.controllerUntilMs = switch (mode) {
                case COMMIT_JUMP -> executionState.committedJumpUntilMs;
                case ESCAPE_HOLE -> executionState.committedEscapeUntilMs;
                case BREAK_BLOCK -> plannedPrimitive != null && plannedPrimitive.requiresBreak() ? now + BREAK_COMMIT_WINDOW_MS : now + 250L;
                case PILLAR -> now + 1800L;
                case DROP -> now + DROP_COMMIT_WINDOW_MS;
                default -> now + 250L;
            };
            return executionState.controllerMode;
        }
    }
    
    BlockPos selectMiningControllerTarget(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || waypoint == null) {
            return null;
        }
        if (plannedPrimitive != null && plannedPrimitive.isMineAscent()) {
            MiningAscentPhase phase = primitiveExecutor.resolveMiningAscentPhase(world, playerFootPos, waypoint, plannedPrimitive);
            if (phase == MiningAscentPhase.ADVANCE) {
                BlockPos advanceBlock = pathPlanner.resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
                if (advanceBlock != null) {
                    return advanceBlock.immutable();
                }
            }
            if (phase == MiningAscentPhase.JUMP) {
                return waypoint.immutable();
            }
        }
        PlacementTargetState placementTargetState = primitiveExecutor.resolveCommittedPlacementTargetState(world, waypoint, plannedPrimitive);
        if (placementTargetState.target() != null) {
            return placementTargetState.target().immutable();
        }
        synchronized (host.lock()) {
            if (executionState.activeBreakTarget != null
                && pathPlanner.isBreakableForNavigator(world, executionState.activeBreakTarget)
                && primitiveExecutor.canBreakTargetNow(world, player, executionState.activeBreakTarget)) {
                return executionState.activeBreakTarget.immutable();
            }
        }
        BlockPos breakTarget = primitiveExecutor.selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
        return breakTarget != null ? breakTarget.immutable() : waypoint.immutable();
    }
    
    void noteControllerProgress(long now, double distanceSq) {
        synchronized (host.lock()) {
            if (distanceSq < executionState.controllerBestDistanceSq) {
                executionState.controllerBestDistanceSq = distanceSq;
                executionState.controllerProgressAtMs = now;
            }
        }
    }
    
    double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget) {
        if (player == null) {
            return Double.POSITIVE_INFINITY;
        }
        BlockPos target;
        synchronized (host.lock()) {
            target = executionState.controllerTarget != null ? executionState.controllerTarget : fallbackTarget;
        }
        if (target == null) {
            return Double.POSITIVE_INFINITY;
        }
        double targetY = pathPlanner.resolveSupportSurfaceY(world, target).orElse(target.getY());
        double dx = player.getX() - (target.getX() + 0.5D);
        double dy = player.getY() - targetY;
        double dz = player.getZ() - (target.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }
    
    void noteControllerActivity(long now) {
        synchronized (host.lock()) {
            executionState.controllerProgressAtMs = now;
        }
    }
    
    void noteRouteProgress(long now) {
        synchronized (host.lock()) {
            int routeProgress = routeProgressScoreLocked();
            if (routeProgress > navigationState.bestRouteProgressScore) {
                navigationState.bestRouteProgressScore = routeProgress;
                navigationState.lastProgressAtMs = now;
                executionState.controllerProgressAtMs = now;
            }
        }
    }
    
    boolean hasCriticalPrimitiveAheadLocked(List<PlannedPrimitive> plan, int startIndex, int lookahead) {
        return hasCriticalPrimitive(plan, startIndex, lookahead);
    }
    
    boolean hasCriticalPrimitive(List<PlannedPrimitive> plan, int startIndex, int lookahead) {
        if (plan == null || plan.isEmpty() || lookahead <= 0) {
            return false;
        }
        int boundedStart = Math.max(0, startIndex);
        int end = Math.min(plan.size(), boundedStart + lookahead);
        for (int i = boundedStart; i < end; i++) {
            PlannedPrimitive primitive = plan.get(i);
            if (primitive == null || primitive.type() == null) {
                continue;
            }
            if (!primitive.isPassiveTraversal()) {
                return true;
            }
        }
        return false;
    }
    
    boolean isRouteStabilizingLocked(BlockPos playerFootPos, long now) {
        if (now - navigationState.lastPlanAtMs > ROUTE_STABILIZATION_MS) {
            return false;
        }
        if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
            return false;
        }
        if (navigationState.pathIndex > Math.min(2, navigationState.currentPath.size() - 1)) {
            return false;
        }
        return playerFootPos == null || isPlayerNearCommittedPathStart(playerFootPos);
    }
    
    void updateFollowSegment(FollowSegmentType type, BlockPos target, double segmentDistanceSq, long now) {
        synchronized (host.lock()) {
            if (type != executionState.activeFollowSegment || !java.util.Objects.equals(executionState.activeFollowSegmentTarget, target)) {
                executionState.activeFollowSegment = type;
                executionState.activeFollowSegmentTarget = target != null ? target.immutable() : null;
                executionState.activeFollowSegmentEnteredAtMs = now;
                executionState.activeFollowSegmentProgressAtMs = now;
                executionState.activeFollowSegmentBestDistanceSq = segmentDistanceSq;
                return;
            }
            if (segmentDistanceSq + 0.01D < executionState.activeFollowSegmentBestDistanceSq) {
                executionState.activeFollowSegmentBestDistanceSq = segmentDistanceSq;
                executionState.activeFollowSegmentProgressAtMs = now;
            }
        }
    }
    
    long followSegmentIdleMs(long now) {
        synchronized (host.lock()) {
            return now - executionState.activeFollowSegmentProgressAtMs;
        }
    }
    
    int routeProgressScoreLocked() {
        int waypointProgress = Math.max(0, navigationState.pathIndex) * 100;
        int breakPenalty = executionState.plannedBreakTargets == null ? 0 : executionState.plannedBreakTargets.size() * 7;
        int escapePenalty = executionState.committedEscape.breakTargets().size() * 5
            + executionState.committedEscape.route().size() * 3;
        return waypointProgress - breakPenalty - escapePenalty;
    }
    
    boolean shouldRedirectController(long now, double distanceSq) {
        synchronized (host.lock()) {
            if (!executionState.committedEscape.isEmpty()
                && executionState.committedEscapeUntilMs > now
                && (executionState.controllerMode == ControllerMode.PILLAR || executionState.controllerMode == ControllerMode.ESCAPE_HOLE)) {
                return false;
            }
            long idleMs = now - executionState.controllerProgressAtMs;
            boolean distanceImproved = distanceSq + 4.0D < executionState.controllerBestDistanceSq;
            if (distanceImproved) {
                executionState.controllerBestDistanceSq = distanceSq;
                executionState.controllerProgressAtMs = now;
                return false;
            }
            return switch (executionState.controllerMode) {
                case FOLLOW_PATH -> idleMs > 2200L;
                case RECOVER_JUMP -> idleMs > 900L || now > executionState.controllerUntilMs;
                case RECOVER_BREAK -> idleMs > 1500L || now > executionState.controllerUntilMs;
                case RECOVER_PILLAR -> idleMs > 2200L || now > executionState.controllerUntilMs;
                case RECOVER_ESCAPE -> idleMs > 1800L || now > executionState.controllerUntilMs;
                case BREAK_BLOCK -> idleMs > 1500L;
                case PILLAR -> idleMs > 2600L || now > executionState.controllerUntilMs;
                case COMMIT_JUMP -> idleMs > 900L;
                case DROP -> idleMs > 1100L || now > executionState.controllerUntilMs;
                case ESCAPE_HOLE -> idleMs > 1800L;
            };
        }
    }
    
    ControllerMode selectControllerMode(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        long now
    ) {
        if (world == null || player == null || playerFootPos == null || waypoint == null) {
            return ControllerMode.FOLLOW_PATH;
        }
        boolean committedEscape = primitiveExecutor.isCommittedEscapeState(now);
        if (isRecoveryState(world, playerFootPos, now)) {
            return executionState.controllerMode;
        }
        if (isCommittedPillarState(world, playerFootPos, now) && (primitiveExecutor.isPillarPrimitive(plannedPrimitive) || committedEscape)) {
            return ControllerMode.PILLAR;
        }
        if (primitiveExecutor.shouldPreferFinalApproachController(world, playerFootPos)) {
            if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > now) {
                return ControllerMode.COMMIT_JUMP;
            }
            BlockPos breakTarget = primitiveExecutor.selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
            if (breakTarget != null) {
                return ControllerMode.BREAK_BLOCK;
            }
            return ControllerMode.FOLLOW_PATH;
        }
        if (primitiveExecutor.isPillarPrimitive(plannedPrimitive)
            || shouldUsePillarStep(world, playerFootPos, waypoint, plannedPrimitive, now)) {
            return ControllerMode.PILLAR;
        }
        if (plannedPrimitive != null && plannedPrimitive.shouldCommitDrop(waypoint, playerFootPos)) {
            return ControllerMode.DROP;
        }
        if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > now) {
            return ControllerMode.COMMIT_JUMP;
        }
        BlockPos breakTarget = primitiveExecutor.selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
        boolean miningAscentStep = plannedPrimitive != null && plannedPrimitive.isMineAscent();
        if (breakTarget != null
            || miningAscentStep
            || (host.allowBlockPlacing() && primitiveExecutor.primitiveStillRequiresPlace(world, plannedPrimitive))) {
            return ControllerMode.BREAK_BLOCK;
        }
        return ControllerMode.FOLLOW_PATH;
    }
    
    boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        return world != null && playerFootPos != null && waypoint != null && now >= 0L && primitiveExecutor.isPillarPrimitive(plannedPrimitive);
    }
    
    ControllerMode recoveryModeForPrimitive(PlannedPrimitive plannedPrimitive, Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (primitiveExecutor.isCommittedEscapeState(now)) {
            return ControllerMode.RECOVER_ESCAPE;
        }
        if (plannedPrimitive != null) {
            if (plannedPrimitive.isPillar()) {
                return ControllerMode.RECOVER_PILLAR;
            }
            if (plannedPrimitive.requiresBreak()) {
                return ControllerMode.RECOVER_BREAK;
            }
            if (plannedPrimitive.isJump()) {
                return ControllerMode.RECOVER_JUMP;
            }
        }
        return ControllerMode.RECOVER_BREAK;
    }
    
    boolean isCommittedPillarState(Level world, BlockPos playerFootPos, long now) {
        synchronized (host.lock()) {
            if (executionState.controllerMode != ControllerMode.PILLAR || executionState.controllerTarget == null || now > executionState.controllerUntilMs) {
                return false;
            }
            BlockPos pillarTarget = executionState.controllerTarget;
            BlockPos pillarBase = pillarTarget.below();
            if (executionState.pendingPlaceTarget != null && executionState.pendingPlaceTarget.equals(pillarBase)) {
                return true;
            }
            if (playerFootPos == null
                || pillarBase.getX() != playerFootPos.getX()
                || pillarBase.getZ() != playerFootPos.getZ()
                || pillarBase.getY() < playerFootPos.getY() - 1
                || pillarBase.getY() > playerFootPos.getY()) {
                return false;
            }
            return pathPlanner.canContinuePillarTo(world, pillarBase, pillarTarget);
        }
    }
    
    boolean isRecoveryState(Level world, BlockPos playerFootPos, long now) {
        synchronized (host.lock()) {
            if ((executionState.controllerMode != ControllerMode.RECOVER_JUMP
                && executionState.controllerMode != ControllerMode.RECOVER_BREAK
                && executionState.controllerMode != ControllerMode.RECOVER_PILLAR
                && executionState.controllerMode != ControllerMode.RECOVER_ESCAPE)
                || executionState.controllerTarget == null
                || now > executionState.controllerUntilMs) {
                return false;
            }
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                return false;
            }
            if (primitiveExecutor.isTrappedInCrampedSpace(world, playerFootPos, navigationState.activeWaypoint)
                || primitiveExecutor.selectVerticalEscapeTarget(world, playerFootPos, navigationState.activeWaypoint) != null) {
                return false;
            }
            if (!isPlayerNearPath(playerFootPos)) {
                return false;
            }
            if (!isWaypointActionable(world, executionState.controllerTarget)) {
                return false;
            }
            if (primitiveExecutor.requiresBreakingForWaypoint(world, executionState.controllerTarget) || pathPlanner.needsPlacedSupport(world, executionState.controllerTarget)) {
                return false;
            }
            return true;
        }
    }
    
    boolean shouldEnterEscapeRecovery(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (primitiveExecutor.shouldPreferFinalApproachController(world, playerFootPos)) {
            return false;
        }
        int physicalWalkNeighbors = primitiveExecutor.countPhysicalWalkNeighbors(world, playerFootPos);
        if (physicalWalkNeighbors > 1) {
            return false;
        }
        if (primitiveExecutor.isCommittedEscapeState(now)) {
            return !primitiveExecutor.canExitTrappedRecovery(world, playerFootPos, waypoint, now);
        }
        if (!primitiveExecutor.isTrappedInCrampedSpace(world, playerFootPos, waypoint)) {
            return false;
        }
        if (plannedPrimitive == null) {
            return !isWaypointActionable(world, waypoint);
        }
        if (!plannedPrimitive.requiresWorldModification() && !plannedPrimitive.isPillar()) {
            return false;
        }
        return plannedPrimitive.requiresWorldModification()
            || plannedPrimitive.isPillar()
            || !isWaypointActionable(world, waypoint);
    }
    
    void clearStaleEscapeRecoveryIfNeeded(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return;
        }
        synchronized (host.lock()) {
            if (!hasCommittedEscapeWorkLocked(now)) {
                return;
            }
        }
        if (shouldEnterEscapeRecovery(world, playerFootPos, waypoint, plannedPrimitive, now)) {
            return;
        }
        synchronized (host.lock()) {
            if (isActiveEscapeBreakTargetLocked()) {
                executionState.activeBreakTarget = null;
            }
        }
        primitiveExecutor.clearExcavationPlan(now, "escape cleared", "resume route");
    }
    
    void repairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target, long now, String replanReason, String stuckReason) {
        synchronized (host.lock()) {
            executionState.activeBreakTarget = null;
            executionState.committedJumpWaypoint = null;
            executionState.committedJumpUntilMs = 0L;
            if (navigationState.pathIndex < 0) {
                navigationState.pathIndex = 0;
                navigationState.furthestVisitedPathIndex = 0;
            }
            if (!navigationState.currentPath.isEmpty()) {
                if (navigationState.pathIndex >= navigationState.currentPath.size()) {
                    navigationState.pathIndex = navigationState.currentPath.size() - 1;
                }
                chooseRecoveryPathIndexLocked(world, playerFootPos, target);
                navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            } else {
                navigationState.activeWaypoint = null;
                executionState.plannedBreakTargets = List.of();
            }
            navigationState.lastPlanAtMs = now;
            navigationState.lastProgressAtMs = now;
            navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + 650L);
            navigationState.bestRouteProgressScore = routeProgressScoreLocked();
            navigationState.lastReplanReason = replanReason;
            navigationState.lastStuckReason = stuckReason;
            if (playerFootPos != null) {
                navigationState.lastMovementSamplePos = Vec3.atCenterOf(playerFootPos);
            }
            navigationState.lastMovementAtMs = now;
            navigationState.lastDistanceCheckpointAtMs = now;
        }
    }
    
    boolean shouldInvalidateCommittedPrimitive(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        long now,
        String stuckReason
    ) {
        if (world == null || playerFootPos == null || waypoint == null || plannedPrimitive == null || stuckReason == null) {
            return false;
        }
        if (plannedPrimitive.requiresCommittedAction()) {
            return false;
        }
        if (!"front blocked".equals(stuckReason)
            && !"ground".equals(stuckReason)
            && !"no progress".equals(stuckReason)) {
            return false;
        }
        if (primitiveExecutor.isInteractablePrimitive(plannedPrimitive)
            && (pathPlanner.requiresInteractableTraversal(world, playerFootPos, waypoint)
            || pathPlanner.hasPathOpenableAhead(world, playerFootPos, waypoint))) {
            return false;
        }
        synchronized (host.lock()) {
            return now - navigationState.lastProgressAtMs >= 900L;
        }
    }
    
    void recoverFromStuck(
        Minecraft client,
        ClientLevel world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3 currentPos,
        long now,
        String replanReason,
        String stuckReason
    ) {
        boolean alreadyRecovering;
        PlannedPrimitive activePrimitive;
        synchronized (host.lock()) {
            alreadyRecovering = executionState.controllerMode == ControllerMode.RECOVER_JUMP
                || executionState.controllerMode == ControllerMode.RECOVER_BREAK
                || executionState.controllerMode == ControllerMode.RECOVER_PILLAR
                || executionState.controllerMode == ControllerMode.RECOVER_ESCAPE;
            activePrimitive = executionState.activePlannedPrimitive;
        }
        if (world != null
            && playerFootPos != null
            && waypoint != null
            && shouldEnterEscapeRecovery(world, playerFootPos, waypoint, activePrimitive, now)) {
            primitiveExecutor.clearExcavationPlan(now, replanReason, stuckReason);
            primitiveExecutor.ensureExcavationPlan(world, playerFootPos, waypoint, now);
            synchronized (host.lock()) {
                executionState.controllerMode = ControllerMode.RECOVER_ESCAPE;
                executionState.controllerTarget = executionState.committedEscapeTarget != null ? executionState.committedEscapeTarget : waypoint.immutable();
                executionState.controllerEnteredAtMs = now;
                executionState.controllerUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
            }
            return;
        }
    
        if (world != null
            && playerFootPos != null
            && waypoint != null
            && shouldInvalidateCommittedPrimitive(world, playerFootPos, waypoint, activePrimitive, now, stuckReason)) {
            redirectCurrentPath(playerFootPos, waypoint, currentPos, now, replanReason, stuckReason);
            return;
        }
    
        if (world != null
            && playerFootPos != null
            && target != null
            && !alreadyRecovering
            && shouldAttemptLocalRecovery(playerFootPos, target, now)) {
            rememberFailedRedirectWindow(playerFootPos, waypoint, now);
            rewindCurrentPathIndex(playerFootPos, waypoint);
            repairCurrentPath(world, playerFootPos, target, now, "local recovery", stuckReason);
            synchronized (host.lock()) {
                executionState.controllerMode = recoveryModeForPrimitive(activePrimitive, world, playerFootPos, waypoint, now);
                executionState.controllerTarget = navigationState.activeWaypoint != null ? navigationState.activeWaypoint.immutable() : (waypoint != null ? waypoint.immutable() : null);
                executionState.controllerEnteredAtMs = now;
                executionState.controllerUntilMs = now + 1800L;
                navigationState.lastLocalRecoveryAtMs = now;
                navigationState.localRecoveryAttempts++;
                navigationState.lastReplanReason = replanReason;
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
            }
            return;
        }
    
        if (world != null && playerFootPos != null && target != null) {
            rememberFailedRedirectWindow(playerFootPos, waypoint, now);
            PathComputation recovery = pathPlanner.findPath(world, playerFootPos, target);
            if (!recovery.path().isEmpty()
                && pathPlanner.isViablePlannedPath(world, recovery.path(), recovery.plannedPrimitives())
                && !shouldKeepCommittedPath(world, playerFootPos, target, recovery.path(), recovery.plannedPrimitives(), now)) {
                synchronized (host.lock()) {
                    navigationState.currentPath = recovery.path();
                    navigationState.candidatePaths = recovery.candidatePaths();
                    navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                    navigationState.goalMode = shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                        ? recovery.goalMode()
                        : GoalMode.EXACT;
                    navigationState.resolvedGoalPos = navigationState.goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                    navigationState.committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                    navigationState.committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                    navigationState.pathIndex = chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                    navigationState.lastWaypointAdvanceAtMs = now;
                    navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                    navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                    navigationState.currentPlan = recovery.plannedPrimitives();
                    executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                    host.appendDebugEventLocked("plan=" + host.formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
                    executionState.activeBreakTarget = null;
                    executionState.committedJumpWaypoint = null;
                    executionState.committedJumpUntilMs = 0L;
                    executionState.committedEscapeTarget = null;
                    executionState.committedEscapeUntilMs = 0L;
                    executionState.committedEscape = EscapePlan.empty();
                    executionState.committedEscapePrimitiveIndex = 0;
                    navigationState.lastPlanAtMs = now;
                    navigationState.lastProgressAtMs = now;
                    navigationState.routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                    navigationState.lastLocalRecoveryAtMs = 0L;
                    navigationState.localRecoveryAttempts = 0;
                    navigationState.bestRouteProgressScore = routeProgressScoreLocked();
                    navigationState.lastReplanReason = replanReason;
                    navigationState.lastStuckReason = stuckReason;
                    navigationState.lastMovementAtMs = now;
                    navigationState.lastMovementSamplePos = currentPos != null ? currentPos : Vec3.atCenterOf(playerFootPos);
                    navigationState.lastDistanceCheckpointAtMs = now;
                    executionState.controllerProgressAtMs = now;
                    executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
                }
                return;
            }
            if (canRepairCurrentPath(world, playerFootPos, target)) {
                repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", stuckReason);
                synchronized (host.lock()) {
                    executionState.controllerMode = recoveryModeForPrimitive(activePrimitive, world, playerFootPos, waypoint, now);
                    executionState.controllerTarget = navigationState.activeWaypoint != null ? navigationState.activeWaypoint.immutable() : (waypoint != null ? waypoint.immutable() : null);
                    executionState.controllerEnteredAtMs = now;
                    executionState.controllerUntilMs = now + 1800L;
                    executionState.controllerProgressAtMs = now;
                    executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
                }
                return;
            }
        }
    
        redirectCurrentPath(playerFootPos, waypoint, currentPos, now, replanReason, stuckReason);
    }
    
    boolean shouldAttemptLocalRecovery(BlockPos playerFootPos, BlockPos target, long now) {
        synchronized (host.lock()) {
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                return false;
            }
            if (isJumpExecutionLocked(now, executionState.activePlannedPrimitive)) {
                return false;
            }
            if (executionState.controllerMode == ControllerMode.RECOVER_JUMP
                || executionState.controllerMode == ControllerMode.RECOVER_BREAK
                || executionState.controllerMode == ControllerMode.RECOVER_PILLAR
                || executionState.controllerMode == ControllerMode.RECOVER_ESCAPE) {
                return false;
            }
            if (navigationState.localRecoveryAttempts >= MAX_LOCAL_RECOVERY_ATTEMPTS) {
                return false;
            }
            if (now - navigationState.lastLocalRecoveryAtMs < LOCAL_RECOVERY_COOLDOWN_MS) {
                return false;
            }
            if (isExcavatingState(now)) {
                return false;
            }
            if (activeWaypointRequiresCommittedAction()) {
                return false;
            }
            if (navigationState.localRecoveryAttempts > 0 && routeProgressScoreLocked() <= navigationState.bestRouteProgressScore) {
                return false;
            }
            return isPlayerNearPath(playerFootPos) && isPathGoalStillValid(navigationState.currentPath, target);
        }
    }
    
    boolean activeWaypointRequiresCommittedAction() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || navigationState.activeWaypoint == null) {
            return false;
        }
        Level world = client.level;
        PlannedPrimitive plannedPrimitive;
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        if (plannedPrimitive != null) {
            return plannedPrimitive.requiresCommittedAction();
        }
        if (primitiveExecutor.requiresBreakingForWaypoint(world, navigationState.activeWaypoint) || pathPlanner.needsPlacedSupport(world, navigationState.activeWaypoint)) {
            return true;
        }
        BlockPos previous = navigationState.pathIndex > 0 && navigationState.pathIndex - 1 < navigationState.currentPath.size() ? navigationState.currentPath.get(navigationState.pathIndex - 1) : null;
        if (previous != null && !primitiveExecutor.requiresBreakingForWaypoint(world, navigationState.activeWaypoint) && !pathPlanner.requiresInteractableTraversal(world, previous, navigationState.activeWaypoint)) {
            int dy = navigationState.activeWaypoint.getY() - previous.getY();
            if (dy > 0 || pathPlanner.shouldStepJump(world, previous, navigationState.activeWaypoint)) {
                return true;
            }
        }
        return false;
    }
    
    void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint) {
        synchronized (host.lock()) {
            if (navigationState.currentPath.isEmpty()) {
                navigationState.pathIndex = 0;
                navigationState.furthestVisitedPathIndex = 0;
                navigationState.activeWaypoint = null;
                return;
            }
            int bestIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
            if (preferredWaypoint != null) {
                int preferredIndex = navigationState.currentPath.indexOf(preferredWaypoint);
                if (preferredIndex >= navigationState.furthestVisitedPathIndex) {
                    bestIndex = preferredIndex;
                }
            }
            if (playerFootPos != null) {
                int forwardIndex = -1;
                double bestForwardScore = Double.POSITIVE_INFINITY;
                for (int i = bestIndex; i < Math.min(navigationState.currentPath.size(), bestIndex + 3); i++) {
                    BlockPos step = navigationState.currentPath.get(i);
                    if (step == null) {
                        continue;
                    }
                    if (pathPlanner.horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 3) {
                        continue;
                    }
                    double score = pathPlanner.horizontalDistanceSq(playerFootPos, step);
                    if (score < bestForwardScore) {
                        bestForwardScore = score;
                        forwardIndex = i;
                    }
                }
                if (forwardIndex >= 0) {
                    bestIndex = forwardIndex;
                } else {
                    for (int i = bestIndex; i >= navigationState.furthestVisitedPathIndex; i--) {
                        BlockPos step = navigationState.currentPath.get(i);
                        if (step == null) {
                            continue;
                        }
                        if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= 6.25D && Math.abs(step.getY() - playerFootPos.getY()) <= 2) {
                            bestIndex = i;
                        } else if (i < bestIndex) {
                            break;
                        }
                    }
                }
            }
            navigationState.pathIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.min(bestIndex, navigationState.currentPath.size() - 1));
            navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
        }
    }
    
    void chooseRecoveryPathIndexLocked(Level world, BlockPos playerFootPos, BlockPos target) {
        if (navigationState.currentPath.isEmpty()) {
            navigationState.pathIndex = 0;
            navigationState.furthestVisitedPathIndex = 0;
            return;
        }
        int boundedIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
        if (world == null || playerFootPos == null || target == null) {
            navigationState.pathIndex = boundedIndex;
            return;
        }
    
        double playerGoalDistance = pathPlanner.horizontalDistanceSq(playerFootPos, target);
        for (int i = boundedIndex; i < navigationState.currentPath.size() && i <= boundedIndex + 2; i++) {
            BlockPos step = navigationState.currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 3) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(step, target) <= playerGoalDistance + 1.0D) {
                navigationState.pathIndex = i;
                return;
            }
        }
    
        for (int i = boundedIndex; i >= navigationState.furthestVisitedPathIndex && i >= boundedIndex - 2; i--) {
            BlockPos step = navigationState.currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) > 9.0D || Math.abs(step.getY() - playerFootPos.getY()) > 2) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            navigationState.pathIndex = i;
            return;
        }
    
        navigationState.pathIndex = boundedIndex;
    }
    
    void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now, String replanReason, String stuckReason) {
        rememberFailedRedirectWindow(playerFootPos, waypoint, now);
        synchronized (host.lock()) {
            navigationState.currentPath = List.of();
            navigationState.currentPlan = List.of();
            navigationState.candidatePaths = List.of();
            navigationState.candidatePathsVisibleUntilMs = 0L;
            navigationState.activeWaypoint = null;
            navigationState.committedPathStartPos = null;
            navigationState.committedPathGoalPos = null;
            navigationState.committedPathStartPos = null;
            navigationState.pathIndex = 0;
            navigationState.furthestVisitedPathIndex = 0;
            executionState.plannedBreakTargets = List.of();
            executionState.activeBreakTarget = null;
            executionState.committedJumpWaypoint = null;
            executionState.committedJumpUntilMs = 0L;
            navigationState.lastPlanAtMs = 0L;
            navigationState.routeCommitUntilMs = 0L;
            navigationState.lastLocalRecoveryAtMs = 0L;
            navigationState.localRecoveryAttempts = 0;
            navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
            navigationState.lastReplanReason = replanReason;
            navigationState.lastStuckReason = stuckReason;
            navigationState.lastMovementAtMs = now;
            navigationState.lastMovementSamplePos = currentPos != null ? currentPos : Vec3.ZERO;
            navigationState.lastDistanceCheckpointAtMs = now;
        }
    }
    
    void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) {
        pathPlanner.rememberFailedMove(playerFootPos, waypoint, now);
        synchronized (host.lock()) {
            if (navigationState.currentPath.isEmpty()) {
                return;
            }
            int startIndex = navigationState.pathIndex;
            if (waypoint != null) {
                int waypointIndex = navigationState.currentPath.indexOf(waypoint);
                if (waypointIndex >= 0) {
                    startIndex = waypointIndex;
                }
            }
            startIndex = Math.max(0, Math.min(startIndex, navigationState.currentPath.size() - 1));
            BlockPos previous = playerFootPos;
            for (int i = startIndex; i < Math.min(navigationState.currentPath.size(), startIndex + 7); i++) {
                BlockPos step = navigationState.currentPath.get(i);
                pathPlanner.rememberFailedMove(previous, step, now);
                previous = step;
            }
        }
    }
    
    BlockPos chooseActiveWaypoint(ClientLevel world, LocalPlayer player, BlockPos playerFootPos) {
        if (player == null) {
            return null;
        }
        synchronized (host.lock()) {
            if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > System.currentTimeMillis() && navigationState.activeWaypoint != null) {
                if (executionState.plannedBreakTargets.isEmpty()) {
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, Math.max(0, navigationState.pathIndex));
                }
                return navigationState.activeWaypoint;
            }
            if (executionState.controllerMode == ControllerMode.PILLAR
                && executionState.controllerTarget != null
                && (primitiveExecutor.isPillarPrimitive(executionState.activePlannedPrimitive)
                || !executionState.committedEscape.isEmpty())) {
                navigationState.activeWaypoint = executionState.controllerTarget.immutable();
                if (executionState.plannedBreakTargets.isEmpty()) {
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, Math.max(0, navigationState.pathIndex));
                }
                if (!primitiveExecutor.isPillarPrimitive(executionState.activePlannedPrimitive)) {
                    executionState.activePlannedPrimitive = createPrimitiveSnapshot(world, playerFootPos, navigationState.activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), navigationState.activeWaypoint.below());
                }
                return navigationState.activeWaypoint;
            }
        }
        BlockPos current = advanceWaypointIfNeeded(world, player, playerFootPos);
        if (current == null) {
            return null;
        }
        synchronized (host.lock()) {
            navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
            if (executionState.plannedBreakTargets.isEmpty()) {
                executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            }
            executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
            chooseForwardResyncIndexLocked(world, playerFootPos);
            navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
            executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
            BlockPos committedGoal = navigationState.committedPathGoalPos != null ? navigationState.committedPathGoalPos : host.targetPos();
            if (world != null
                && playerFootPos != null
                && navigationState.activeWaypoint != null
                && committedGoal != null
                && navigationState.activeWaypoint.getY() > playerFootPos.getY()
                && executionState.activePlannedPrimitive != null
                && executionState.activePlannedPrimitive.allowsForwardResync()) {
                int previousIndex = navigationState.pathIndex;
                chooseRecoveryPathIndexLocked(world, playerFootPos, committedGoal);
                if (navigationState.pathIndex != previousIndex && navigationState.pathIndex >= 0 && navigationState.pathIndex < navigationState.currentPath.size()) {
                    navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                    executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                    navigationState.lastAdvanceDecision = "resync:lower_actionable_step pathIndex=" + navigationState.pathIndex + " waypoint=" + host.formatDebugPos(navigationState.activeWaypoint);
                }
            }
            executionState.activePlannedPrimitive = normalizeActivePrimitiveLocked(world, playerFootPos, navigationState.activeWaypoint, executionState.activePlannedPrimitive);
            return navigationState.activeWaypoint;
        }
    }
    
    PlannedPrimitive normalizeActivePrimitiveLocked(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive primitive
    ) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return primitive;
        }
        if (primitive != null && !primitive.allowsForwardResync()) {
            return primitive;
        }
        if (waypoint.getY() <= playerFootPos.getY()) {
            return primitive;
        }
        List<BlockPos> breakTargets = pathPlanner.getRequiredBreakTargets(world, playerFootPos, waypoint);
        if (breakTargets == null) {
            breakTargets = List.of();
        } else {
            breakTargets = breakTargets.stream()
                .filter(pos -> pos != null && pathPlanner.isBreakableForNavigator(world, pos))
                .map(BlockPos::immutable)
                .toList();
        }
        BlockPos placeTarget = pathPlanner.needsPlacedSupport(world, waypoint) && pathPlanner.canPlaceSupportAt(world, waypoint.below())
            ? waypoint.below().immutable()
            : null;
        return createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
    }
    
    BlockPos advanceWaypointIfNeeded(Level world, LocalPlayer player, BlockPos playerFootPos) {
        if (world == null || player == null || playerFootPos == null) {
            host.setAdvanceDecision("hold:missing_player");
            return null;
        }
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        synchronized (host.lock()) {
            long now = System.currentTimeMillis();
            int reachedIndex = findReachedPathIndexLocked(playerFootPos, playerPos);
            reachedIndex = capReachedIndexAtUnfinishedPlacementLocked(world, playerFootPos, reachedIndex);
            if (reachedIndex >= 0) {
                if (reachedIndex > navigationState.pathIndex) {
                    navigationState.lastProgressAtMs = now;
                    navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                }
                navigationState.pathIndex = Math.max(navigationState.pathIndex, reachedIndex);
                navigationState.lastWaypointAdvanceAtMs = now;
                navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, reachedIndex);
                navigationState.lastAdvanceDecision = "advance:reached_index=" + reachedIndex;
            }
            while (!navigationState.currentPath.isEmpty() && navigationState.pathIndex < navigationState.currentPath.size()) {
                BlockPos waypoint = navigationState.currentPath.get(navigationState.pathIndex);
                if (waypoint == null) {
                    navigationState.pathIndex++;
                    navigationState.lastWaypointAdvanceAtMs = now;
                    navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, navigationState.pathIndex);
                    navigationState.lastProgressAtMs = now;
                    navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                    navigationState.lastAdvanceDecision = "advance:null_waypoint";
                    continue;
                }
                PlannedPrimitive primitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                if (primitive != null
                    && primitive.requiresPlace()
                    && (primitive.placeTarget() == null || !pathPlanner.hasCollision(world, primitive.placeTarget()))) {
                    navigationState.activeWaypoint = waypoint;
                    navigationState.lastAdvanceDecision = "hold:await_placement=" + host.formatDebugPos(primitive.placeTarget());
                    return waypoint;
                }
                if (waypoint.getY() > playerFootPos.getY() && navigationState.pathIndex + 1 < navigationState.currentPath.size()) {
                    BlockPos next = navigationState.currentPath.get(navigationState.pathIndex + 1);
                    if (next != null
                        && playerFootPos.getY() >= next.getY()
                        && pathPlanner.horizontalDistanceSq(playerFootPos, next) <= WAYPOINT_REACHED_DISTANCE_SQ
                        && Math.abs(next.getY() - playerFootPos.getY()) <= 1) {
                        navigationState.pathIndex++;
                        navigationState.lastWaypointAdvanceAtMs = now;
                        navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, navigationState.pathIndex);
                        navigationState.lastProgressAtMs = now;
                        navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                        navigationState.lastAdvanceDecision = "advance:skip_overshot_upward";
                        continue;
                    }
                }
                Vec3 waypointCenter = new Vec3(waypoint.getX() + 0.5D, playerPos.y, waypoint.getZ() + 0.5D);
                if (!shouldAdvancePastWaypoint(playerPos, playerFootPos, waypoint, waypointCenter)) {
                    navigationState.activeWaypoint = waypoint;
                    navigationState.lastAdvanceDecision = "hold:pathIndex=" + navigationState.pathIndex + " waypoint=" + host.formatDebugPos(waypoint);
                    return waypoint;
                }
                navigationState.pathIndex++;
                navigationState.lastWaypointAdvanceAtMs = now;
                navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, navigationState.pathIndex);
                navigationState.lastProgressAtMs = now;
                navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                navigationState.lastAdvanceDecision = "advance:past_waypoint";
            }
            navigationState.activeWaypoint = null;
            navigationState.lastAdvanceDecision = "hold:no_active_waypoint";
            return null;
        }
    }

    int capReachedIndexAtUnfinishedPlacementLocked(Level world, BlockPos playerFootPos, int reachedIndex) {
        if (world == null || reachedIndex < navigationState.pathIndex) {
            return reachedIndex;
        }
        int start = Math.max(0, navigationState.pathIndex);
        int end = Math.min(reachedIndex, navigationState.currentPath.size() - 1);
        for (int i = start; i <= end; i++) {
            PlannedPrimitive primitive = getPlannedPrimitiveAtIndexLocked(i);
            // A mined ascent is an ordered staircase: clearing its headroom is not
            // enough to have reached the step.  The generic one-block vertical
            // tolerance is useful for ordinary walking, but it allowed the route
            // coordinator to skip directly to a later mined step while the player
            // was still below the first one.
            if (primitive != null
                && primitive.isMineAscent()
                && !playerFootPos.equals(navigationState.currentPath.get(i))) {
                return i - 1;
            }
            if (primitive != null
                && primitive.requiresPlace()
                && (primitive.placeTarget() == null || !pathPlanner.hasCollision(world, primitive.placeTarget()))) {
                return i - 1;
            }
        }
        return reachedIndex;
    }
    
    int findReachedPathIndexLocked(BlockPos playerFootPos, Vec3 playerPos) {
        if (playerFootPos == null || playerPos == null || navigationState.currentPath.isEmpty()) {
            return -1;
        }
        int start = Math.max(0, navigationState.pathIndex);
        int end = Math.min(navigationState.currentPath.size() - 1, start + 6);
        int best = -1;
        for (int i = start; i <= end; i++) {
            BlockPos step = navigationState.currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= WAYPOINT_REACHED_DISTANCE_SQ
                && hasStableFootingOnWaypoint(playerPos, step)
                && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                best = i;
            }
        }
        return best;
    }
    
    void chooseForwardResyncIndexLocked(Level world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null || navigationState.currentPath.isEmpty()) {
            return;
        }
        int boundedIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
        BlockPos currentStep = navigationState.currentPath.get(boundedIndex);
        if (currentStep == null) {
            return;
        }
        double currentDistance = pathPlanner.horizontalDistanceSq(playerFootPos, currentStep);
        int bestIndex = boundedIndex;
        double bestScore = currentDistance;
        int end = Math.min(navigationState.currentPath.size() - 1, boundedIndex + 4);
        for (int i = boundedIndex + 1; i <= end; i++) {
            BlockPos step = navigationState.currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 2) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            double score = pathPlanner.horizontalDistanceSq(playerFootPos, step) + ((i - boundedIndex) * 0.15D);
            if (score + 0.75D < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex > boundedIndex
            && (currentDistance > 4.0D
            || currentStep.getY() > playerFootPos.getY()
            || !isWaypointActionable(world, currentStep))) {
            int previousIndex = navigationState.pathIndex;
            navigationState.pathIndex = bestIndex;
            navigationState.lastWaypointAdvanceAtMs = System.currentTimeMillis();
            navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, bestIndex - 1);
            navigationState.lastAdvanceDecision = "resync:forward_index=" + bestIndex + " waypoint=" + host.formatDebugPos(navigationState.currentPath.get(bestIndex));
            host.appendDebugEventLocked(
                "pathIndex " + previousIndex + " -> " + navigationState.pathIndex
                    + " reason=forward_resync oldWaypoint=" + host.formatDebugPos(previousIndex >= 0 && previousIndex < navigationState.currentPath.size() ? navigationState.currentPath.get(previousIndex) : null)
                    + " newWaypoint=" + host.formatDebugPos(navigationState.currentPath.get(navigationState.pathIndex))
                    + " player=" + host.formatDebugPos(playerFootPos)
                    + " currentDistanceSq=" + String.format(java.util.Locale.ROOT, "%.2f", currentDistance)
                    + " bestScore=" + String.format(java.util.Locale.ROOT, "%.2f", bestScore)
            );
        }
    }
    
    boolean shouldAdvancePastWaypoint(Vec3 playerPos, BlockPos playerFootPos, BlockPos waypoint, Vec3 waypointCenter) {
        if (waypoint == null || waypointCenter == null || playerFootPos == null || playerPos == null) {
            return true;
        }
        if (playerFootPos.equals(waypoint) && hasStableFootingOnWaypoint(playerPos, waypoint)) {
            return true;
        }
        if (waypoint.getY() > playerFootPos.getY()) {
            return false;
        }
        double distanceSq = playerPos.distanceToSqr(waypointCenter);
        if (distanceSq <= WAYPOINT_REACHED_DISTANCE_SQ
            && hasStableFootingOnWaypoint(playerPos, waypoint)
            && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
            return true;
        }
        synchronized (host.lock()) {
            if (distanceSq <= WAYPOINT_NEAR_DISTANCE_SQ
                && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1
                && navigationState.pathIndex + 1 < navigationState.currentPath.size()) {
                BlockPos next = navigationState.currentPath.get(navigationState.pathIndex + 1);
                if (next != null
                    && pathPlanner.horizontalDistanceSq(playerFootPos, next) + 0.20D < pathPlanner.horizontalDistanceSq(playerFootPos, waypoint)
                    && hasStableFootingOnWaypoint(playerPos, next)
                    && Math.abs(next.getY() - playerFootPos.getY()) <= 1) {
                    return true;
                }
            }
            if (navigationState.pathIndex > 0
                && navigationState.pathIndex + 1 < navigationState.currentPath.size()
                && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
                BlockPos previous = navigationState.currentPath.get(navigationState.pathIndex - 1);
                PlannedPrimitive primitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                if (previous != null
                    && primitive != null
                    && primitive.isSimpleMovementStep()
                    && NavigatorGeometry.hasPassedWaypoint(
                        playerPos.x,
                        playerPos.z,
                        previous.getX() + 0.5D,
                        previous.getZ() + 0.5D,
                        waypoint.getX() + 0.5D,
                        waypoint.getZ() + 0.5D,
                        1.15D
                    )) {
                    return true;
                }
            }
        }
        return false;
    }
    
    boolean hasStableFootingOnWaypoint(Vec3 playerPos, BlockPos waypoint) {
        if (playerPos == null || waypoint == null) {
            return false;
        }
        double localX = playerPos.x - waypoint.getX();
        double localZ = playerPos.z - waypoint.getZ();
        return localX >= WAYPOINT_SAFE_EDGE_INSET
            && localX <= 1.0D - WAYPOINT_SAFE_EDGE_INSET
            && localZ >= WAYPOINT_SAFE_EDGE_INSET
            && localZ <= 1.0D - WAYPOINT_SAFE_EDGE_INSET;
    }
    
    int chooseInitialPathIndex(List<BlockPos> path, BlockPos playerFootPos, BlockPos target) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos step = path.get(i);
            if (step != null) {
                if (playerFootPos != null && step.equals(playerFootPos) && i + 1 < path.size()) {
                    return i + 1;
                }
                return i;
            }
        }
        return 0;
    }
    
    boolean isPlayerNearCommittedPathStart(BlockPos playerFootPos) {
        if (playerFootPos == null || navigationState.currentPath.isEmpty()) {
            return false;
        }
        int start = Math.max(0, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1) - 1);
        int end = Math.min(navigationState.currentPath.size() - 1, Math.max(navigationState.pathIndex, 0) + 3);
        for (int i = start; i <= end; i++) {
            BlockPos step = navigationState.currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= 16.0D
                && Math.abs(step.getY() - playerFootPos.getY()) <= 3) {
                return true;
            }
        }
        return false;
    }
    
    List<BlockPos> buildPathBreakPlan(Level world, List<BlockPos> path, int startIndex) {
        if (world == null || path == null || path.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<BlockPos> plan = new LinkedHashSet<>();
        int endIndex = Math.min(path.size(), Math.max(0, startIndex) + MAX_PATH_BREAK_LOOKAHEAD);
        for (int i = Math.max(0, startIndex); i < endIndex; i++) {
            BlockPos waypoint = path.get(i);
            if (waypoint == null) {
                continue;
            }
            BlockPos previous = i > 0 ? path.get(i - 1) : waypoint;
            List<BlockPos> requiredBreakTargets = pathPlanner.getRequiredBreakTargets(world, previous, waypoint);
            if (requiredBreakTargets == null || requiredBreakTargets.isEmpty()) {
                continue;
            }
            for (BlockPos breakTarget : requiredBreakTargets) {
                if (breakTarget != null && pathPlanner.isBreakableForNavigator(world, breakTarget)) {
                    plan.add(breakTarget.immutable());
                }
            }
        }
        return List.copyOf(plan);
    }
    
    List<PlannedPrimitive> buildPlannedPrimitives(Level world, List<BlockPos> path, BlockPos startPos) {
        if (world == null || path == null || path.isEmpty()) {
            return List.of();
        }
        List<PlannedPrimitive> plan = new ArrayList<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            BlockPos target = path.get(i);
            if (target == null) {
                continue;
            }
            BlockPos previous = i > 0 ? path.get(i - 1) : (startPos != null ? startPos : target);
            List<BlockPos> breakTargets = pathPlanner.getRequiredBreakTargets(world, previous, target);
            if (breakTargets == null) {
                breakTargets = List.of();
            } else {
                breakTargets = breakTargets.stream()
                    .filter(pos -> pos != null && pathPlanner.isBreakableForNavigator(world, pos))
                    .map(BlockPos::immutable)
                    .toList();
            }
            boolean pillarTransition = host.allowBlockPlacing()
                && previous.getX() == target.getX()
                && previous.getZ() == target.getZ()
                && target.getY() == previous.getY() + 1
                && !pathPlanner.isClimbTransition(world, previous, target)
                && pathPlanner.needsPlacedSupport(world, target);
            BlockPos placeTarget = pillarTransition
                ? target.below().immutable()
                : pathPlanner.needsPlacedSupport(world, target) && pathPlanner.canPlaceSupportAt(world, target.below())
                    ? target.below().immutable()
                    : null;
            plan.add(createPlannedPrimitive(world, previous, target, breakTargets, placeTarget));
        }
        return List.copyOf(plan);
    }
    
    PlannedPrimitive createPlannedPrimitive(
        Level world,
        BlockPos from,
        BlockPos to,
        List<BlockPos> breakTargets,
        BlockPos placeTarget
    ) {
        SearchPrimitiveType searchType = classifySearchPrimitiveType(world, from, to, breakTargets, placeTarget);
        PlannedPrimitiveType type = classifyExecutionPrimitiveType(world, from, to, breakTargets, placeTarget, searchType);
        return createPrimitiveSnapshot(world, from, to, searchType, type, breakTargets, placeTarget);
    }
    
    PlannedPrimitive createPrimitiveSnapshot(
        Level world,
        BlockPos from,
        BlockPos to,
        SearchPrimitiveType searchType,
        PlannedPrimitiveType type,
        List<BlockPos> breakTargets,
        BlockPos placeTarget
    ) {
        List<BlockPos> normalizedBreakTargets = breakTargets == null ? List.of() : List.copyOf(breakTargets);
        BlockPos normalizedTarget = to == null ? null : to.immutable();
        BlockPos normalizedPlaceTarget = placeTarget == null ? null : placeTarget.immutable();
        int deltaY = from == null || to == null ? 0 : to.getY() - from.getY();
        int horizontalStepCount = from == null || to == null
            ? 0
            : Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ());
        boolean sameColumn = from != null && to != null && from.getX() == to.getX() && from.getZ() == to.getZ();
        PrimitiveTraversal traversal = classifyPrimitiveTraversal(world, from, to, type);
        PrimitiveExecution execution = classifyPrimitiveExecution(type, normalizedBreakTargets, normalizedPlaceTarget, traversal);
        return new PlannedPrimitive(
            normalizedTarget,
            searchType,
            type,
            traversal,
            execution,
            deltaY,
            horizontalStepCount,
            sameColumn,
            normalizedBreakTargets,
            normalizedPlaceTarget
        );
    }
    
    SearchPrimitiveType classifySearchPrimitiveType(
        Level world,
        BlockPos from,
        BlockPos to,
        List<BlockPos> breakTargets,
        BlockPos placeTarget
    ) {
        if (to == null) {
            return SearchPrimitiveType.WALK;
        }
        boolean hasBreaks = breakTargets != null && !breakTargets.isEmpty();
        if (placeTarget != null
            && from != null
            && to.getX() == from.getX()
            && to.getZ() == from.getZ()
            && to.getY() > from.getY()) {
            return SearchPrimitiveType.PILLAR;
        }
        if (from != null && (pathPlanner.isWaterNode(world, from) || pathPlanner.isWaterNode(world, to))) {
            return SearchPrimitiveType.SWIM;
        }
        if (from != null && (pathPlanner.isClimbTransition(world, from, to) || pathPlanner.isClimbNode(world, to) || pathPlanner.isClimbNode(world, from))) {
            return SearchPrimitiveType.CLIMB;
        }
        if (from != null && to.getY() < from.getY()) {
            return SearchPrimitiveType.DESCEND;
        }
        if (from != null && (pathPlanner.requiresInteractableTraversal(world, from, to) || pathPlanner.hasPathOpenableAhead(world, from, to))) {
            return SearchPrimitiveType.INTERACT;
        }
        if (from != null && (to.getY() > from.getY() || pathPlanner.shouldStepJump(world, from, to))) {
            return hasBreaks ? SearchPrimitiveType.MINE_ASCEND : SearchPrimitiveType.JUMP_ASCEND;
        }
        if (hasBreaks) {
            return SearchPrimitiveType.BREAK_FORWARD;
        }
        if (placeTarget != null) {
            return SearchPrimitiveType.PLACE_FORWARD;
        }
        return SearchPrimitiveType.WALK;
    }
    
    PlannedPrimitiveType classifyExecutionPrimitiveType(
        Level world,
        BlockPos from,
        BlockPos to,
        List<BlockPos> breakTargets,
        BlockPos placeTarget,
        SearchPrimitiveType searchType
    ) {
        if (searchType == null) {
            return PlannedPrimitiveType.WALK;
        }
        return switch (searchType) {
            case WALK, PLACE_FORWARD -> PlannedPrimitiveType.WALK;
            case INTERACT -> PlannedPrimitiveType.INTERACTABLE;
            case BREAK_FORWARD -> PlannedPrimitiveType.MINE_FORWARD;
            case JUMP_ASCEND -> PlannedPrimitiveType.JUMP_ASCEND;
            case MINE_ASCEND -> PlannedPrimitiveType.MINE_ASCEND;
            case DESCEND -> PlannedPrimitiveType.DESCEND;
            case CLIMB -> PlannedPrimitiveType.CLIMB;
            case SWIM -> PlannedPrimitiveType.SWIM;
            case PILLAR -> PlannedPrimitiveType.PILLAR;
        };
    }
    
    PrimitiveTraversal classifyPrimitiveTraversal(
        Level world,
        BlockPos from,
        BlockPos to,
        PlannedPrimitiveType type
    ) {
        if (type == null) {
            return PrimitiveTraversal.GROUND;
        }
        return switch (type) {
            case CLIMB -> PrimitiveTraversal.CLIMB;
            case DESCEND -> PrimitiveTraversal.DESCENT;
            case SWIM -> PrimitiveTraversal.SWIM;
            case INTERACTABLE -> PrimitiveTraversal.INTERACTABLE;
            case PILLAR -> PrimitiveTraversal.VERTICAL_ASCENT;
            case JUMP_ASCEND, MINE_ASCEND -> PrimitiveTraversal.ASCENT;
            case MINE_FORWARD, WALK -> {
                if (from != null && to != null && to.getY() > from.getY()) {
                    yield PrimitiveTraversal.ASCENT;
                }
                if (from != null && to != null && to.getY() < from.getY()) {
                    yield PrimitiveTraversal.DESCENT;
                }
                yield PrimitiveTraversal.GROUND;
            }
        };
    }
    
    PrimitiveExecution classifyPrimitiveExecution(
        PlannedPrimitiveType type,
        List<BlockPos> breakTargets,
        BlockPos placeTarget,
        PrimitiveTraversal traversal
    ) {
        if (type == PlannedPrimitiveType.PILLAR || placeTarget != null) {
            return PrimitiveExecution.PLACE_THEN_MOVE;
        }
        if (breakTargets != null && !breakTargets.isEmpty()) {
            return PrimitiveExecution.BREAK_THEN_MOVE;
        }
        if (type == PlannedPrimitiveType.JUMP_ASCEND || type == PlannedPrimitiveType.MINE_ASCEND) {
            return PrimitiveExecution.COMMITTED_MOVEMENT;
        }
        if (traversal == PrimitiveTraversal.DESCENT || traversal == PrimitiveTraversal.CLIMB || traversal == PrimitiveTraversal.SWIM) {
            return PrimitiveExecution.COMMITTED_MOVEMENT;
        }
        if (type == PlannedPrimitiveType.INTERACTABLE) {
            return PrimitiveExecution.INTERACT_THEN_MOVE;
        }
        return PrimitiveExecution.CONTINUOUS_MOVEMENT;
    }
    
    PlannedPrimitive getPlannedPrimitiveAtIndexLocked(int index) {
        if (index < 0 || index >= navigationState.currentPlan.size()) {
            return null;
        }
        return navigationState.currentPlan.get(index);
    }
    
    void rebuildCurrentPlanLocked(Level world) {
        navigationState.currentPlan = buildPlannedPrimitives(world, navigationState.currentPath, navigationState.committedPathStartPos);
        executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
        if (!navigationState.currentPlan.isEmpty()) {
            host.appendDebugEventLocked("plan=" + host.formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
            host.appendDebugEventLocked("planDetailed=" + host.formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
        }
        if (!navigationState.currentPath.isEmpty()) {
            host.appendDebugEventLocked("pathDetailed=" + host.formatIndexedPath(navigationState.currentPath, 24));
        }
    }
    
}
