package com.pathmind.execution;

import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.LoaderMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded Pathmind-owned movement backend used when Baritone is unavailable.
 * This currently implements local walkable-space pathfinding for goto-style movement and stop/cancel.
 */
public final class PathmindNavigator {
    private static final PathmindNavigator INSTANCE = new PathmindNavigator();
    static final double WAYPOINT_REACHED_DISTANCE_SQ = 0.64D;
    static final double WAYPOINT_NEAR_DISTANCE_SQ = 0.90D;
    private static final float MAX_YAW_STEP = 14.0F;
    private static final float NEOFORGE_MAX_YAW_STEP = 8.0F;
    private static final int BLOCK_INTERACTION_APPROACH_RADIUS = 4;
    private static final int BLOCK_INTERACTION_APPROACH_UP = 2;
    private static final int BLOCK_INTERACTION_APPROACH_DOWN = 4;
    static final long REPLAN_COOLDOWN_MS = 450L;
    static final long TRAPPED_RECOVERY_COMMIT_MS = 10000L;
    private static final double DISTANCE_STALL_THRESHOLD = 2.0D;
    static final long ROUTE_COMMIT_MS = 8000L;
    static final long PATH_DECISION_VISIBILITY_MS = 1400L;
    private static final long WAYPOINT_ACQUIRE_SETTLE_MS = 300L;
    private static final double MOVEMENT_EPSILON_SQ = 0.0025D;
    static final int MAX_DROP_DOWN = 3;
    static final int MAX_GOAL_PATH_ATTEMPTS = 8;
    private static final int MAX_SNAPSHOT_PATH_POINTS = 96;
    private static final int MAX_SNAPSHOT_CANDIDATE_POINTS = 64;
    private static final int MAX_DEBUG_EVENTS = 12;
    private static final Path NAV_DEBUG_LOG_PATH = Path.of(System.getProperty("user.dir"), ".pathmind", "logs", "navigator-debug.log");
    private static final long DEBUG_HEARTBEAT_INTERVAL_MS = 1500L;
    static final double PLACE_MOVE_PENALTY = 12.0D;
    private static final double PILLAR_MOVE_PENALTY = 2.4D;

    private CompletableFuture<Void> activeFuture;
    private BlockPos targetPos;
    private String commandLabel;
    private State state = State.IDLE;
    private long startedAtMs;
    private WaterMode waterMode = WaterMode.NORMAL;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacing = true;
    private boolean eventLoggingEnabled = !LoaderMetadata.isNeoForge();
    private final NavigatorExecutionState executionState = new NavigatorExecutionState();
    private final NavigatorNavigationState navigationState = new NavigatorNavigationState();
    private double lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
    private volatile Snapshot renderSnapshot;
    private final PathmindPathPlanner pathPlanner = new PathmindPathPlanner(new PlannerHost());
    private final NavigatorPrimitiveExecutor primitiveExecutor = new NavigatorPrimitiveExecutor(new PrimitiveHost(), executionState, navigationState, pathPlanner);
    private final NavigatorRouteCoordinator routeCoordinator = new NavigatorRouteCoordinator(new RouteHost(), executionState, navigationState, pathPlanner, primitiveExecutor);
    private String previousControllerMode = "none";
    private String previousPrimitiveLabel = "none";
    private String previousMiningAscentPhase = MiningAscentPhase.CLEARANCE.name();
    private String previousPillarPhase = PillarPhase.CENTER.name();
    private BlockPos previousActiveWaypoint;
    private String previousReplanReason = "none";
    private String previousStuckReason = "none";
    private final Deque<String> debugEvents = new LinkedList<>();
    private long lastDebugHeartbeatAtMs;

    public enum State {
        IDLE,
        PREVIEW,
        PATHING,
        ARRIVED,
        STOPPED,
        FAILED
    }


    public enum WaterMode {
        NORMAL,
        AVOID
    }

    public record Snapshot(
        boolean active,
        State state,
        BlockPos targetPos,
        BlockPos resolvedGoalPos,
        BlockPos activeWaypoint,
        int pathIndex,
        int visitedPathIndex,
        List<BlockPos> breakTargets,
        List<BlockPos> placeTargets,
        String commandLabel,
        double distance,
        int nodeCount,
        long elapsedMs,
        List<BlockPos> path,
        List<List<BlockPos>> candidatePaths
    ) {
    }

    public record DebugInfo(
        State state,
        String controllerMode,
        String previousControllerMode,
        String primitive,
        String miningAscentPhase,
        String pillarPhase,
        String goalMode,
        String waterMode,
        boolean allowBlockBreaking,
        boolean allowBlockPlacing,
        boolean eventLoggingEnabled,
        BlockPos targetPos,
        BlockPos resolvedGoalPos,
        BlockPos activeWaypoint,
        BlockPos previousActiveWaypoint,
        BlockPos controllerTarget,
        BlockPos lastPlaceTarget,
        int pathIndex,
        int nodeCount,
        String lastPlaceResult,
        String lastReplanReason,
        String previousReplanReason,
        String lastReplanDecision,
        String lastAdvanceDecision,
        String lastReplaceDecision,
        String lastStuckReason,
        String previousStuckReason,
        List<String> recentEvents
    ) {
    }

    public record PreviewResult(boolean success, String message) {
    }

    private PathmindNavigator() {
    }

    private final class PlannerHost implements PathmindPathPlanner.Host {
        @Override
        public boolean allowBlockBreaking() {
            return allowBlockBreaking;
        }

        @Override
        public boolean allowBlockPlacing() {
            return allowBlockPlacing;
        }

        @Override
        public WaterMode waterMode() {
            return waterMode;
        }

        @Override
        public BlockPos targetPos() {
            return targetPos;
        }

        @Override
        public boolean isProtectedNavigationGoal(BlockPos pos) {
            if (pos == null) {
                return false;
            }
            synchronized (PathmindNavigator.this) {
                return NavigatorSearchPolicy.isProtectedGoal(pos, targetPos);
            }
        }

        @Override
        public void recordPlanningDiagnostics(NavigatorPlanningCache cache, PathComputation result, long elapsedMs) {
            PathmindNavigator.this.recordPlanningDiagnostics(cache, result, elapsedMs);
        }

        @Override
        public String formatDebugPos(BlockPos pos) {
            return PathmindNavigator.this.formatDebugPos(pos);
        }

        @Override
        public boolean isWaypointActionable(Level world, BlockPos waypoint) {
            return routeCoordinator.isWaypointActionable(world, waypoint);
        }

        @Override
        public List<PlannedPrimitive> buildPlannedPrimitives(Level world, List<BlockPos> path, BlockPos startPos) {
            return routeCoordinator.buildPlannedPrimitives(world, path, startPos);
        }

        @Override
        public boolean requiresBreakingForWaypoint(Level world, BlockPos waypoint) {
            return primitiveExecutor.requiresBreakingForWaypoint(world, waypoint);
        }

        @Override
        public PlannedPrimitive createPlannedPrimitive(Level world, BlockPos from, BlockPos to,
                                                       List<BlockPos> breakTargets, BlockPos placeTarget) {
            return routeCoordinator.createPlannedPrimitive(world, from, to, breakTargets, placeTarget);
        }

        @Override
        public Direction preferredEscapeDirection(Level world, BlockPos current, BlockPos goal, long now) {
            synchronized (PathmindNavigator.this) {
                if (!executionState.committedEscape.isEmpty()) {
                    return executionState.committedEscape.direction();
                }
            }
            return primitiveExecutor.chooseEscapeDirection(world, current, goal, now);
        }

        @Override
        public boolean isDirectGoalCompletionCandidate(BlockPos candidate, BlockPos target) {
            return routeCoordinator.isDirectGoalCompletionCandidate(candidate, target);
        }

        @Override
        public boolean isJumpPrimitive(PlannedPrimitive primitive) {
            return primitiveExecutor.isJumpPrimitive(primitive);
        }

        @Override
        public PathmindPathPlanner.SteeringLookahead steeringLookahead(BlockPos activeWaypoint) {
            synchronized (PathmindNavigator.this) {
                if (navigationState.currentPath.isEmpty() || navigationState.pathIndex < 0 || navigationState.pathIndex + 1 >= navigationState.currentPath.size()) {
                    return null;
                }
                BlockPos activePathWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                if (activePathWaypoint == null || !activePathWaypoint.equals(activeWaypoint)) {
                    return null;
                }
                return new PathmindPathPlanner.SteeringLookahead(
                    navigationState.currentPath.get(navigationState.pathIndex + 1),
                    routeCoordinator.getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex + 1)
                );
            }
        }
    }

    private final class PrimitiveHost implements NavigatorPrimitiveExecutor.Host {
        @Override
        public Object lock() {
            return PathmindNavigator.this;
        }

        @Override public boolean allowBlockBreaking() { return allowBlockBreaking; }
        @Override public boolean allowBlockPlacing() { return allowBlockPlacing; }
        @Override public BlockPos targetPos() { return targetPos; }
        @Override public GoalMode goalMode() { return navigationState.goalMode; }
        @Override public void goalMode(GoalMode goalMode) { navigationState.goalMode = goalMode; }
        @Override public void appendDebugEventLocked(String event) { PathmindNavigator.this.appendDebugEventLocked(event); }
        @Override public String formatDebugPos(BlockPos pos) { return PathmindNavigator.this.formatDebugPos(pos); }
        @Override public boolean isWaypointActionable(Level world, BlockPos waypoint) { return routeCoordinator.isWaypointActionable(world, waypoint); }
        @Override public boolean shouldTrackResolvedPlanningGoal(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode) { return routeCoordinator.shouldTrackResolvedPlanningGoal(target, resolvedGoal, goalMode); }
        @Override public boolean isPlayerNearPath(BlockPos playerFootPos) { return routeCoordinator.isPlayerNearPath(playerFootPos); }
        @Override public boolean hasCommittedEscapeWorkLocked(long now) { return routeCoordinator.hasCommittedEscapeWorkLocked(now); }
        @Override public boolean isActiveEscapeBreakTargetLocked() { return routeCoordinator.isActiveEscapeBreakTargetLocked(); }
        @Override public boolean isJumpExecutionLocked(long now, PlannedPrimitive primitive) { return routeCoordinator.isJumpExecutionLocked(now, primitive); }
        @Override public void noteControllerProgress(long now, double distanceSq) { routeCoordinator.noteControllerProgress(now, distanceSq); }
        @Override public double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget) { return routeCoordinator.distanceToControllerTargetSq(world, player, fallbackTarget); }
        @Override public void noteControllerActivity(long now) { routeCoordinator.noteControllerActivity(now); }
        @Override public boolean isRouteStabilizingLocked(BlockPos playerFootPos, long now) { return routeCoordinator.isRouteStabilizingLocked(playerFootPos, now); }
        @Override public void updateFollowSegment(FollowSegmentType type, BlockPos target, double distanceSq, long now) { routeCoordinator.updateFollowSegment(type, target, distanceSq, now); }
        @Override public long followSegmentIdleMs(long now) { return routeCoordinator.followSegmentIdleMs(now); }
        @Override public boolean shouldRedirectController(long now, double distanceSq) { return routeCoordinator.shouldRedirectController(now, distanceSq); }
        @Override public boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive primitive, long now) { return routeCoordinator.shouldUsePillarStep(world, playerFootPos, waypoint, primitive, now); }
        @Override public void clearStaleEscapeRecoveryIfNeeded(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive primitive, long now) { routeCoordinator.clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, primitive, now); }
        @Override public void recoverFromStuck(Minecraft client, ClientLevel world, BlockPos playerFootPos, BlockPos waypoint, BlockPos target, Vec3 currentPos, long now, String replanReason, String stuckReason) { routeCoordinator.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, replanReason, stuckReason); }
        @Override public void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint) { routeCoordinator.rewindCurrentPathIndex(playerFootPos, preferredWaypoint); }
        @Override public void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now, String replanReason, String stuckReason) { routeCoordinator.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, replanReason, stuckReason); }
        @Override public void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) { routeCoordinator.rememberFailedRedirectWindow(playerFootPos, waypoint, now); }
        @Override public List<BlockPos> buildPathBreakPlan(Level world, List<BlockPos> path, int startIndex) { return routeCoordinator.buildPathBreakPlan(world, path, startIndex); }
        @Override public PlannedPrimitive createPrimitiveSnapshot(Level world, BlockPos from, BlockPos to, SearchPrimitiveType searchType, PlannedPrimitiveType type, List<BlockPos> breakTargets, BlockPos placeTarget) { return routeCoordinator.createPrimitiveSnapshot(world, from, to, searchType, type, breakTargets, placeTarget); }
        @Override public PlannedPrimitive getPlannedPrimitiveAtIndexLocked(int index) { return routeCoordinator.getPlannedPrimitiveAtIndexLocked(index); }
        @Override public void rebuildCurrentPlanLocked(Level world) { routeCoordinator.rebuildCurrentPlanLocked(world); }
        @Override public PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target) { return PathmindNavigator.this.findPath(world, start, target); }
        @Override public BlockPos resolvePlayerFootPos(LocalPlayer player) { return PathmindNavigator.this.resolvePlayerFootPos(player); }
    }

    private final class RouteHost implements NavigatorRouteCoordinator.Host {
        @Override public Object lock() { return PathmindNavigator.this; }
        @Override public boolean allowBlockBreaking() { return allowBlockBreaking; }
        @Override public boolean allowBlockPlacing() { return allowBlockPlacing; }
        @Override public BlockPos targetPos() { return targetPos; }
        @Override public void appendDebugEventLocked(String event) { PathmindNavigator.this.appendDebugEventLocked(event); }
        @Override public void setAdvanceDecision(String decision) { PathmindNavigator.this.setAdvanceDecision(decision); }
        @Override public void setReplaceDecision(String decision) { PathmindNavigator.this.setReplaceDecision(decision); }
        @Override public String formatDebugPos(BlockPos pos) { return PathmindNavigator.this.formatDebugPos(pos); }
        @Override public String formatPlannedPrimitiveSequence(List<PlannedPrimitive> plan, int limit) { return PathmindNavigator.this.formatPlannedPrimitiveSequence(plan, limit); }
        @Override public String formatIndexedPrimitiveSequence(List<PlannedPrimitive> plan, int limit) { return PathmindNavigator.this.formatIndexedPrimitiveSequence(plan, limit); }
        @Override public String formatIndexedPath(List<BlockPos> path, int limit) { return PathmindNavigator.this.formatIndexedPath(path, limit); }
    }

    public static PathmindNavigator getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startGoto(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future) {
        if (targetPos == null || future == null) {
            return false;
        }
        return startGotoInternal(targetPos, commandLabel, future);
    }

    public synchronized boolean startGotoNearBlock(BlockPos targetBlockPos, String commandLabel, CompletableFuture<Void> future) {
        if (targetBlockPos == null || future == null) {
            return false;
        }
        stopInternal(false, "replaced");
        BlockPos navigationTarget = resolveReachableAdjacentStandableTarget(targetBlockPos)
            .or(() -> resolveAdjacentStandableTarget(targetBlockPos))
            .orElse(targetBlockPos);
        return startGotoInternal(navigationTarget, commandLabel, future);
    }

    public synchronized PreviewResult previewPathNearBlock(Minecraft client, BlockPos targetBlockPos, String commandLabel) {
        if (targetBlockPos == null) {
            return new PreviewResult(false, FailureReason.CLIENT_UNAVAILABLE.message);
        }
        BlockPos navigationTarget = resolveReachableAdjacentStandableTarget(targetBlockPos)
            .or(() -> resolveAdjacentStandableTarget(targetBlockPos))
            .orElse(targetBlockPos);
        return previewPath(client, navigationTarget, commandLabel);
    }

    private boolean startGotoInternal(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future) {
        stopInternal(false, "replaced");
        Minecraft client = Minecraft.getInstance();
        NavigatorCameraController.begin(client != null ? client.player : null);
        this.targetPos = targetPos.immutable();
        this.commandLabel = commandLabel == null || commandLabel.isBlank() ? "Goto" : commandLabel.trim();
        this.activeFuture = future;
        this.state = State.PATHING;
        this.startedAtMs = System.currentTimeMillis();
        navigationState.lastProgressAtMs = this.startedAtMs;
        navigationState.lastPlanAtMs = 0L;
        executionState.lastJumpAtMs = 0L;
        executionState.lastMiningJumpGateLogAtMs = 0L;
        executionState.lastMiningResumeLogAtMs = 0L;
        navigationState.bestDistanceSq = Double.MAX_VALUE;
        navigationState.goalMode = GoalMode.EXACT;
        navigationState.resolvedGoalPos = targetPos.immutable();
        navigationState.committedPathGoalPos = navigationState.resolvedGoalPos;
        navigationState.currentPath = List.of();
        navigationState.currentPlan = List.of();
        navigationState.candidatePaths = List.of();
        navigationState.candidatePathsVisibleUntilMs = 0L;
        navigationState.lastWaypointAdvanceAtMs = this.startedAtMs;
        navigationState.pathIndex = 0;
        navigationState.furthestVisitedPathIndex = 0;
        navigationState.activeWaypoint = null;
        executionState.committedJumpWaypoint = null;
        executionState.committedJumpUntilMs = 0L;
        executionState.lastJumpAttemptWaypoint = null;
        executionState.repeatedJumpAttempts = 0;
        executionState.lastInteractAtMs = 0L;
        executionState.activeBreakTarget = null;
        executionState.plannedBreakTargets = List.of();
        executionState.committedEscapeTarget = null;
        executionState.committedEscapeUntilMs = 0L;
        executionState.committedEscape = EscapePlan.empty();
        executionState.committedEscapePrimitiveIndex = 0;
        executionState.controllerMode = ControllerMode.FOLLOW_PATH;
        executionState.controllerTarget = null;
        executionState.controllerUntilMs = 0L;
        executionState.controllerEnteredAtMs = this.startedAtMs;
        executionState.controllerProgressAtMs = this.startedAtMs;
        executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        executionState.lastPlaceTarget = null;
        executionState.lastPlaceResult = "none";
        navigationState.routeCommitUntilMs = this.startedAtMs + ROUTE_COMMIT_MS;
        navigationState.lastLocalRecoveryAtMs = 0L;
        navigationState.localRecoveryAttempts = 0;
        navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
        navigationState.consecutivePlanningBudgetExhaustions = 0;
        executionState.activeFollowSegment = FollowSegmentType.GROUND;
        executionState.activeFollowSegmentTarget = null;
        executionState.activePlannedPrimitive = null;
        executionState.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        executionState.activeFollowSegmentProgressAtMs = this.startedAtMs;
        executionState.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        LocalPlayer player = Minecraft.getInstance() != null ? Minecraft.getInstance().player : null;
        Vec3 startingPosition = player != null ? player.position() : Vec3.atCenterOf(this.targetPos);
        navigationState.lastMovementSamplePos = startingPosition;
        navigationState.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = startingPosition.distanceTo(Vec3.atCenterOf(this.targetPos));
        navigationState.lastDistanceCheckpointAtMs = this.startedAtMs;
        this.pathPlanner.clearFailureMemory();
        navigationState.lastReplanReason = "start goto";
        navigationState.lastStuckReason = "none";
        this.previousControllerMode = executionState.controllerMode.name();
        this.previousPrimitiveLabel = "none";
        this.previousMiningAscentPhase = executionState.activeMiningAscentPhase.name();
        this.previousPillarPhase = executionState.activePillarPhase.name();
        this.previousActiveWaypoint = null;
        this.previousReplanReason = navigationState.lastReplanReason;
        this.previousStuckReason = navigationState.lastStuckReason;
        this.debugEvents.clear();
        appendDebugEventLocked("goto start target=" + formatDebugPos(this.targetPos));
        return true;
    }

    private Optional<BlockPos> resolveAdjacentStandableTarget(BlockPos targetBlockPos) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || targetBlockPos == null) {
            return Optional.empty();
        }

        BlockPos playerPos = resolvePlayerFootPos(client.player);
        if (playerPos == null) {
            playerPos = client.player.blockPosition();
        }

        double reachSq = primitiveExecutor.blockInteractionReachSquared(client.player);
        List<ScoredPos> visibleCandidates = collectBlockInteractionStandTargets(
            client.level,
            client.player,
            playerPos,
            targetBlockPos,
            reachSq,
            true
        );
        if (!visibleCandidates.isEmpty()) {
            return Optional.of(visibleCandidates.get(0).pos());
        }

        List<ScoredPos> fallbackCandidates = collectBlockInteractionStandTargets(
            client.level,
            client.player,
            playerPos,
            targetBlockPos,
            reachSq,
            false
        );
        if (!fallbackCandidates.isEmpty()) {
            return Optional.of(fallbackCandidates.get(0).pos());
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 1; dy >= -1; dy--) {
                        BlockPos candidate = new BlockPos(
                            targetBlockPos.getX() + dx,
                            targetBlockPos.getY() + dy,
                            targetBlockPos.getZ() + dz
                        );
                        if (!pathPlanner.isStandable(client.level, candidate)) {
                            continue;
                        }
                        double score = candidate.distSqr(playerPos)
                            + (Math.abs(candidate.getY() - targetBlockPos.getY()) * 0.25D)
                            + (radius * 0.05D);
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }

        return Optional.empty();
    }

    private Optional<BlockPos> resolveReachableAdjacentStandableTarget(BlockPos targetBlockPos) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || targetBlockPos == null) {
            return Optional.empty();
        }

        BlockPos playerPos = resolvePlayerFootPos(client.player);
        if (playerPos == null) {
            playerPos = client.player.blockPosition();
        }

        double reachSq = primitiveExecutor.blockInteractionReachSquared(client.player);
        List<ScoredPos> visibleCandidates = collectBlockInteractionStandTargets(
            client.level,
            client.player,
            playerPos,
            targetBlockPos,
            reachSq,
            true
        );
        Optional<BlockPos> visibleTarget = selectReachableBlockInteractionStandTarget(client.level, playerPos, visibleCandidates);
        if (visibleTarget.isPresent()) {
            return visibleTarget;
        }

        List<ScoredPos> fallbackCandidates = collectBlockInteractionStandTargets(
            client.level,
            client.player,
            playerPos,
            targetBlockPos,
            reachSq,
            false
        );
        return selectReachableBlockInteractionStandTarget(client.level, playerPos, fallbackCandidates);
    }

    private Optional<BlockPos> selectReachableBlockInteractionStandTarget(
        ClientLevel world,
        BlockPos start,
        List<ScoredPos> candidates
    ) {
        if (world == null || start == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        for (ScoredPos candidate : candidates) {
            if (candidate != null && start.equals(candidate.pos())) {
                return Optional.of(candidate.pos());
            }
        }
        int attempts = Math.min(MAX_GOAL_PATH_ATTEMPTS, candidates.size());
        ScoredPos bestCandidate = null;
        double bestScore = Double.POSITIVE_INFINITY;
        long selectionDeadlineMs = System.currentTimeMillis() + 120L;
        BlockPos previousTarget = targetPos;
        try {
            for (int i = 0; i < attempts; i++) {
                if (System.currentTimeMillis() >= selectionDeadlineMs) {
                    break;
                }
                ScoredPos candidate = candidates.get(i);
                if (candidate == null || candidate.pos() == null) {
                    continue;
                }
                targetPos = candidate.pos();
                PathComputation computation = findPath(world, start, candidate.pos());
                if (computation.path().isEmpty()) {
                    continue;
                }
                double score = candidate.score()
                    + (computation.path().size() * 0.35D)
                    + (pathPlanner.pathStructurePenalty(computation.path(), computation.plannedPrimitives()) * 0.15D)
                    + (pathPlanner.pathModificationPenalty(computation.plannedPrimitives()) * 0.01D);
                if (score < bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
        } finally {
            targetPos = previousTarget;
        }
        return bestCandidate != null ? Optional.of(bestCandidate.pos()) : Optional.empty();
    }

    private List<ScoredPos> collectBlockInteractionStandTargets(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerPos,
        BlockPos targetBlockPos,
        double reachSq,
        boolean requireLineOfSight
    ) {
        if (world == null || player == null || playerPos == null || targetBlockPos == null) {
            return List.of();
        }
        List<ScoredPos> scored = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int radius = 0; radius <= BLOCK_INTERACTION_APPROACH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = BLOCK_INTERACTION_APPROACH_UP; dy >= -BLOCK_INTERACTION_APPROACH_DOWN; dy--) {
                        BlockPos candidate = new BlockPos(
                            targetBlockPos.getX() + dx,
                            targetBlockPos.getY() + dy,
                            targetBlockPos.getZ() + dz
                        );
                        if (!seen.add(candidate) || !pathPlanner.isStandable(world, candidate) || pathPlanner.isHardDanger(world, candidate)) {
                            continue;
                        }
                        if (!primitiveExecutor.isBlockShapeWithinReachFromFoot(world, player, candidate, targetBlockPos, reachSq)) {
                            continue;
                        }
                        if (requireLineOfSight && !primitiveExecutor.canInteractWithBlockFromFoot(world, player, candidate, targetBlockPos, reachSq)) {
                            continue;
                        }
                        scored.add(new ScoredPos(
                            candidate.immutable(),
                            scoreBlockInteractionStandTarget(world, playerPos, candidate, targetBlockPos, requireLineOfSight)
                        ));
                    }
                }
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredPos::score));
        return scored;
    }

    private double scoreBlockInteractionStandTarget(
        Level world,
        BlockPos playerPos,
        BlockPos candidate,
        BlockPos targetBlockPos,
        boolean lineOfSight
    ) {
        double playerDistance = Math.sqrt(pathPlanner.horizontalDistanceSq(playerPos, candidate)) * 0.72D;
        double targetDistance = Math.sqrt(pathPlanner.horizontalDistanceSq(candidate, targetBlockPos)) * 0.45D;
        double verticalFromPlayer = Math.abs(candidate.getY() - playerPos.getY()) * 0.55D;
        double verticalFromTarget = Math.abs(candidate.getY() - targetBlockPos.getY()) * 0.22D;
        double opener = pathPlanner.countOpenNeighbors(world, candidate) * -0.08D;
        double visibilityBias = lineOfSight ? -2.0D : 0.0D;
        return playerDistance + targetDistance + verticalFromPlayer + verticalFromTarget + opener + visibilityBias;
    }

    public synchronized boolean isActive() {
        return state == State.PATHING && activeFuture != null && targetPos != null;
    }

    public synchronized WaterMode getWaterMode() {
        return waterMode;
    }

    public synchronized void setWaterMode(WaterMode waterMode) {
        this.waterMode = waterMode == null ? WaterMode.NORMAL : waterMode;
    }

    public synchronized boolean isBlockBreakingAllowed() {
        return allowBlockBreaking;
    }

    public synchronized void setBlockBreakingAllowed(boolean allowBlockBreaking) {
        this.allowBlockBreaking = allowBlockBreaking;
    }

    public synchronized boolean isBlockPlacingAllowed() {
        return allowBlockPlacing;
    }

    public synchronized void setBlockPlacingAllowed(boolean allowBlockPlacing) {
        this.allowBlockPlacing = allowBlockPlacing;
    }

    public synchronized boolean isEventLoggingEnabled() {
        return eventLoggingEnabled;
    }

    public synchronized void setEventLoggingEnabled(boolean enabled) {
        eventLoggingEnabled = enabled;
        appendDebugEventLocked("logging=" + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Returns the immutable snapshot prepared by the client tick. Render callbacks must
     * never run world scans, collision checks, or planner work.
     */
    public Snapshot getSnapshot() {
        return renderSnapshot;
    }

    private synchronized Snapshot buildRenderSnapshot(Minecraft client) {
        if (state == State.IDLE || targetPos == null) {
            return null;
        }
        double distance = -1.0D;
        if (client != null && client.player != null) {
            Vec3 target = Vec3.atCenterOf(targetPos);
            Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
            distance = playerPos.distanceTo(target);
        }
        int snapshotStart = navigationState.currentPath.isEmpty()
            ? 0
            : Math.max(0, Math.min(navigationState.furthestVisitedPathIndex, navigationState.currentPath.size() - 1));
        List<BlockPos> pathCopy = navigationState.currentPath.isEmpty()
            ? List.of()
            : copyPathWindow(navigationState.currentPath, snapshotStart, MAX_SNAPSHOT_PATH_POINTS);
        boolean showCandidatePaths = state == State.PREVIEW || System.currentTimeMillis() <= navigationState.candidatePathsVisibleUntilMs;
        List<List<BlockPos>> candidateCopies = !showCandidatePaths || navigationState.candidatePaths.isEmpty()
            ? List.of()
            : navigationState.candidatePaths.stream()
                .map(path -> copyPathWindow(path, 0, MAX_SNAPSHOT_CANDIDATE_POINTS))
                .toList();
        List<BlockPos> breakTargets = List.of();
        List<BlockPos> placeTargets = List.of();
        if (client != null && client.level != null) {
            if (!executionState.committedEscape.breakTargets().isEmpty()) {
                breakTargets = executionState.committedEscape.breakTargets().stream()
                    .filter(pos -> pos != null && !pathPlanner.canOccupy(client.level, pos))
                    .toList();
            } else if (!executionState.plannedBreakTargets.isEmpty()) {
                breakTargets = executionState.plannedBreakTargets.stream()
                    .filter(pos -> pos != null && pathPlanner.isBreakableForNavigator(client.level, pos))
                    .toList();
            } else if (navigationState.activeWaypoint != null) {
                List<BlockPos> requiredBreakTargets = pathPlanner.getRequiredBreakTargets(client.level, navigationState.activeWaypoint);
                if (requiredBreakTargets != null && !requiredBreakTargets.isEmpty()) {
                    breakTargets = List.copyOf(requiredBreakTargets);
                } else if (executionState.activeBreakTarget != null) {
                    breakTargets = List.of(executionState.activeBreakTarget);
                }
            }
            if (executionState.controllerMode == ControllerMode.PILLAR && executionState.controllerTarget != null) {
                placeTargets = List.of(executionState.controllerTarget.below().immutable());
            } else if (executionState.activePlannedPrimitive != null && executionState.activePlannedPrimitive.placeTarget() != null) {
                placeTargets = List.of(executionState.activePlannedPrimitive.placeTarget().immutable());
            }
        }
        BlockPos resolvedGoal = navigationState.resolvedGoalPos != null ? navigationState.resolvedGoalPos : (pathCopy.isEmpty() ? targetPos : pathCopy.get(pathCopy.size() - 1));
        return new Snapshot(
            isActive(),
            state,
            targetPos,
            resolvedGoal,
            navigationState.activeWaypoint,
            Math.max(0, navigationState.pathIndex - snapshotStart),
            0,
            breakTargets,
            placeTargets,
            commandLabel,
            distance,
            pathCopy.size(),
            Math.max(0L, System.currentTimeMillis() - startedAtMs),
            pathCopy,
            candidateCopies
        );
    }

    private List<BlockPos> copyPathWindow(List<BlockPos> path, int startIndex, int maxPoints) {
        if (path == null || path.isEmpty() || maxPoints <= 0) {
            return List.of();
        }
        int start = Math.max(0, Math.min(startIndex, path.size()));
        int end = Math.min(path.size(), start + maxPoints);
        if (start >= end) {
            return List.of();
        }
        return List.copyOf(path.subList(start, end));
    }

    public synchronized DebugInfo getDebugInfo() {
        if (targetPos == null && state == State.IDLE) {
            return null;
        }
        return new DebugInfo(
            state,
            executionState.controllerMode.name(),
            previousControllerMode,
            formatPlannedPrimitive(executionState.activePlannedPrimitive),
            executionState.activeMiningAscentPhase.name(),
            executionState.activePillarPhase.name(),
            navigationState.goalMode.name(),
            waterMode.name(),
            allowBlockBreaking,
            allowBlockPlacing,
            eventLoggingEnabled,
            targetPos,
            navigationState.resolvedGoalPos,
            navigationState.activeWaypoint,
            previousActiveWaypoint,
            executionState.controllerTarget,
            executionState.lastPlaceTarget,
            navigationState.pathIndex,
            navigationState.currentPath.size(),
            executionState.lastPlaceResult,
            navigationState.lastReplanReason,
            previousReplanReason,
            navigationState.lastReplanDecision,
            navigationState.lastAdvanceDecision,
            navigationState.lastReplaceDecision,
            navigationState.lastStuckReason,
            previousStuckReason,
            List.copyOf(debugEvents)
        );
    }

    private void recordDebugTransitions(long now) {
        synchronized (this) {
            boolean changed = false;
            String controller = executionState.controllerMode != null ? executionState.controllerMode.name() : "none";
            String primitive = formatPlannedPrimitive(executionState.activePlannedPrimitive);
            String miningPhase = executionState.activeMiningAscentPhase != null ? executionState.activeMiningAscentPhase.name() : "none";
            String pillarPhase = executionState.activePillarPhase != null ? executionState.activePillarPhase.name() : "none";
            String replan = navigationState.lastReplanReason == null ? "none" : navigationState.lastReplanReason;
            String stuck = navigationState.lastStuckReason == null ? "none" : navigationState.lastStuckReason;
            Minecraft client = Minecraft.getInstance();
            String playerState = "player=none";
            if (client != null && client.player != null) {
                LocalPlayer player = client.player;
                BlockPos foot = resolvePlayerFootPos(player);
                Vec3 velocity = player.getDeltaMovement();
                playerState = "player=" + formatDebugPos(foot)
                    + " vel="
                    + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", velocity.x, velocity.y, velocity.z)
                    + " ground=" + player.onGround();
            }
            if (!controller.equals(previousControllerMode)) {
                appendDebugEventLocked("controller " + previousControllerMode + " -> " + controller);
                previousControllerMode = controller;
                changed = true;
            }
            if (!primitive.equals(previousPrimitiveLabel)) {
                appendDebugEventLocked("primitive " + previousPrimitiveLabel + " -> " + primitive);
                previousPrimitiveLabel = primitive;
                changed = true;
            }
            if (!miningPhase.equals(previousMiningAscentPhase)) {
                appendDebugEventLocked("miningPhase " + previousMiningAscentPhase + " -> " + miningPhase);
                previousMiningAscentPhase = miningPhase;
                changed = true;
            }
            if (!pillarPhase.equals(previousPillarPhase)) {
                appendDebugEventLocked("pillarPhase " + previousPillarPhase + " -> " + pillarPhase);
                previousPillarPhase = pillarPhase;
                changed = true;
            }
            if (!java.util.Objects.equals(navigationState.activeWaypoint, previousActiveWaypoint)) {
                appendDebugEventLocked("waypoint " + formatDebugPos(previousActiveWaypoint) + " -> " + formatDebugPos(navigationState.activeWaypoint));
                previousActiveWaypoint = navigationState.activeWaypoint != null ? navigationState.activeWaypoint.immutable() : null;
                appendDebugEventLocked("primitive=" + formatPlannedPrimitive(executionState.activePlannedPrimitive));
                changed = true;
            }
            if (!replan.equals(previousReplanReason)) {
                appendDebugEventLocked("replan " + previousReplanReason + " -> " + replan);
                previousReplanReason = replan;
                changed = true;
            }
            if (!stuck.equals(previousStuckReason)) {
                appendDebugEventLocked("stuck " + previousStuckReason + " -> " + stuck);
                previousStuckReason = stuck;
                changed = true;
            }
            if (executionState.lastPlaceResult != null && !"none".equals(executionState.lastPlaceResult) && (debugEvents.isEmpty() || !debugEvents.peekLast().contains("placeResult=" + executionState.lastPlaceResult))) {
                appendDebugEventLocked("placeResult=" + executionState.lastPlaceResult + " target=" + formatDebugPos(executionState.lastPlaceTarget));
                changed = true;
            }
            if (!changed && eventLoggingEnabled && now - lastDebugHeartbeatAtMs >= DEBUG_HEARTBEAT_INTERVAL_MS) {
                appendDebugEventLocked(
                    "heartbeat controller=" + controller
                        + " waypoint=" + formatDebugPos(navigationState.activeWaypoint)
                        + " primitive=" + primitive
                        + " miningPhase=" + miningPhase
                        + " pillarPhase=" + pillarPhase
                        + " target=" + formatDebugPos(targetPos)
                        + " replan=" + replan
                        + " replanDecision=" + navigationState.lastReplanDecision
                        + " advanceDecision=" + navigationState.lastAdvanceDecision
                        + " replaceDecision=" + navigationState.lastReplaceDecision
                        + " stuck=" + stuck
                        + " placeResult=" + (executionState.lastPlaceResult == null ? "none" : executionState.lastPlaceResult)
                        + " " + playerState
                );
                lastDebugHeartbeatAtMs = now;
            } else if (changed) {
                lastDebugHeartbeatAtMs = now;
            }
        }
    }

    private void setReplanDecision(String decision) {
        synchronized (this) {
            navigationState.lastReplanDecision = decision == null || decision.isBlank() ? "none" : decision;
        }
    }

    private void setAdvanceDecision(String decision) {
        synchronized (this) {
            navigationState.lastAdvanceDecision = decision == null || decision.isBlank() ? "none" : decision;
        }
    }

    private void setReplaceDecision(String decision) {
        synchronized (this) {
            navigationState.lastReplaceDecision = decision == null || decision.isBlank() ? "none" : decision;
        }
    }

    private void appendDebugEventLocked(String event) {
        if (event == null || event.isBlank()) {
            return;
        }
        String line = "[" + System.currentTimeMillis() + "] " + event;
        debugEvents.addLast(line);
        while (debugEvents.size() > MAX_DEBUG_EVENTS) {
            debugEvents.removeFirst();
        }
        if (!eventLoggingEnabled) {
            return;
        }
        try {
            Files.createDirectories(NAV_DEBUG_LOG_PATH.getParent());
            Files.writeString(
                NAV_DEBUG_LOG_PATH,
                line + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Logging should not break navigation.
        }
    }

    private String formatDebugPos(BlockPos pos) {
        if (pos == null) {
            return "--";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void recordPlanningDiagnostics(NavigatorPlanningCache cache, PathComputation result, long elapsedMs) {
        if (cache == null) {
            return;
        }
        String outcome;
        if (result == null) {
            outcome = "exception";
        } else if (!result.path().isEmpty()) {
            BlockPos end = result.path().get(result.path().size() - 1);
            outcome = "path:" + result.path().size() + "->" + formatDebugPos(end);
        } else if (result.failureReason() != null) {
            outcome = "failure:" + result.failureReason().name();
        } else {
            outcome = "failure:NO_ROUTE";
        }
        synchronized (this) {
            appendDebugEventLocked(
                "search " + outcome
                    + " elapsed=" + elapsedMs + "ms"
                    + " expanded=" + cache.expandedNodes
                    + " moves=" + cache.movementEvaluations
                    + " clean=" + cache.cleanSearches
                    + " modified=" + cache.modifiedSearches
                    + " states=" + cache.blockStates.size()
                    + " stateHits=" + cache.blockStateHits
                    + " shapeHits=" + cache.collisionShapeHits
            );
        }
    }

    public synchronized PreviewResult previewPath(Minecraft client, BlockPos targetPos, String commandLabel) {
        if (client == null || client.player == null || client.level == null || targetPos == null) {
            return new PreviewResult(false, FailureReason.CLIENT_UNAVAILABLE.message);
        }

        stopInternal(false, "preview");
        this.targetPos = targetPos.immutable();
        this.commandLabel = commandLabel == null || commandLabel.isBlank() ? "Path Preview" : commandLabel.trim();
        this.state = State.PREVIEW;
        this.startedAtMs = System.currentTimeMillis();
        navigationState.lastProgressAtMs = this.startedAtMs;
        navigationState.lastPlanAtMs = this.startedAtMs;
        executionState.lastJumpAtMs = 0L;
        navigationState.goalMode = GoalMode.EXACT;
        navigationState.resolvedGoalPos = this.targetPos;
        navigationState.committedPathGoalPos = navigationState.resolvedGoalPos;
        navigationState.currentPath = List.of();
        navigationState.currentPlan = List.of();
        navigationState.candidatePaths = List.of();
        navigationState.candidatePathsVisibleUntilMs = 0L;
        navigationState.pathIndex = 0;
        navigationState.furthestVisitedPathIndex = 0;
        navigationState.activeWaypoint = null;
        executionState.committedJumpWaypoint = null;
        executionState.committedJumpUntilMs = 0L;
        executionState.lastInteractAtMs = 0L;
        executionState.activeBreakTarget = null;
        executionState.plannedBreakTargets = List.of();
        executionState.committedEscapeTarget = null;
        executionState.committedEscapeUntilMs = 0L;
        executionState.committedEscape = EscapePlan.empty();
        executionState.committedEscapePrimitiveIndex = 0;
        executionState.controllerMode = ControllerMode.FOLLOW_PATH;
        executionState.controllerTarget = null;
        executionState.controllerUntilMs = 0L;
        executionState.controllerEnteredAtMs = this.startedAtMs;
        executionState.controllerProgressAtMs = this.startedAtMs;
        executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        executionState.lastPlaceTarget = null;
        executionState.lastPlaceResult = "none";
        navigationState.routeCommitUntilMs = this.startedAtMs + ROUTE_COMMIT_MS;
        navigationState.lastLocalRecoveryAtMs = 0L;
        navigationState.localRecoveryAttempts = 0;
        navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
        navigationState.consecutivePlanningBudgetExhaustions = 0;
        executionState.activeFollowSegment = FollowSegmentType.GROUND;
        executionState.activeFollowSegmentTarget = null;
        executionState.activePlannedPrimitive = null;
        executionState.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        executionState.activeFollowSegmentProgressAtMs = this.startedAtMs;
        executionState.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        navigationState.lastMovementSamplePos = Vec3.atCenterOf(this.targetPos);
        navigationState.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        navigationState.lastDistanceCheckpointAtMs = this.startedAtMs;
        navigationState.lastReplanReason = "preview";
        navigationState.lastStuckReason = "none";
        this.previousPrimitiveLabel = "none";
        this.previousMiningAscentPhase = executionState.activeMiningAscentPhase.name();
        this.previousPillarPhase = executionState.activePillarPhase.name();
        this.pathPlanner.clearFailureMemory();

        BlockPos start = resolvePlayerFootPos(client.player);
        PathComputation computation = findPath(client.level, start, this.targetPos);
        if (computation.path().isEmpty()) {
            stopInternal(false, "preview failed");
            String failureMessage = computation.failureReason() != null ? computation.failureReason().message : FailureReason.NO_ROUTE.message;
            if (computation.failureDetail() != null && !computation.failureDetail().isBlank()) {
                failureMessage = failureMessage + " " + computation.failureDetail();
            }
            return new PreviewResult(false, failureMessage);
        }

        navigationState.currentPath = computation.path();
        navigationState.candidatePaths = computation.candidatePaths();
        navigationState.candidatePathsVisibleUntilMs = Long.MAX_VALUE;
        navigationState.goalMode = computation.goalMode();
        navigationState.resolvedGoalPos = computation.resolvedGoalPos();
        navigationState.committedPathGoalPos = navigationState.resolvedGoalPos != null ? navigationState.resolvedGoalPos.immutable() : this.targetPos;
        navigationState.committedPathStartPos = start != null ? start.immutable() : null;
        navigationState.pathIndex = routeCoordinator.chooseInitialPathIndex(navigationState.currentPath, start, this.targetPos);
        navigationState.lastWaypointAdvanceAtMs = System.currentTimeMillis();
        navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
        navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
        executionState.plannedBreakTargets = routeCoordinator.buildPathBreakPlan(client.level, navigationState.currentPath, navigationState.pathIndex);
        navigationState.currentPlan = computation.plannedPrimitives();
        executionState.activePlannedPrimitive = navigationState.pathIndex < navigationState.currentPlan.size() ? navigationState.currentPlan.get(navigationState.pathIndex) : null;
        this.renderSnapshot = buildRenderSnapshot(client);
        return new PreviewResult(true, "Pathmind Nav: previewing path to " + this.targetPos.getX() + " " + this.targetPos.getY() + " " + this.targetPos.getZ());
    }

    public void tick(Minecraft client) {
        try {
            tickInternal(client);
        } finally {
            renderSnapshot = buildRenderSnapshot(client);
        }
    }

    private void tickInternal(Minecraft client) {
        CompletableFuture<Void> future;
        BlockPos target;
        synchronized (this) {
            future = activeFuture;
            target = targetPos;
            if (state != State.PATHING || future == null || target == null) {
                return;
            }
        }

        if (client == null || client.player == null || client.level == null) {
            fail(FailureReason.CLIENT_UNAVAILABLE);
            return;
        }

        LocalPlayer player = client.player;
        BlockPos playerFootPos = resolvePlayerFootPos(player);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        Vec3 targetCenter = Vec3.atCenterOf(target);
        double distanceSq = currentPos.distanceToSqr(targetCenter);
        long now = System.currentTimeMillis();

        synchronized (this) {
            if (currentPos.distanceToSqr(navigationState.lastMovementSamplePos) > MOVEMENT_EPSILON_SQ) {
                navigationState.lastMovementSamplePos = currentPos;
                navigationState.lastMovementAtMs = now;
            }
            double currentDistance = Math.sqrt(distanceSq);
            if (!Double.isFinite(lastDistanceCheckpoint) || Math.abs(currentDistance - lastDistanceCheckpoint) > DISTANCE_STALL_THRESHOLD) {
                lastDistanceCheckpoint = currentDistance;
                navigationState.lastDistanceCheckpointAtMs = now;
            }
        }

        ClientLevel world = client.level;
        if (pathPlanner.hasReachedExactGoal(playerFootPos, target)) {
            releaseMovementKeys(client);
            complete(State.ARRIVED);
            return;
        }

        pathPlanner.pruneFailureMemory(now);
        if (routeCoordinator.shouldReplan(world, playerFootPos, target, now)) {
            PathComputation computation = findPath(world, playerFootPos, target);
            if (computation.path().isEmpty()) {
                if (computation.failureReason() == FailureReason.SEARCH_LIMIT
                    && routeCoordinator.deferPlanningAfterBudgetExhaustion(now, computation.failureDetail())) {
                    releaseMovementKeys(client);
                    return;
                }
                if (routeCoordinator.canRepairCurrentPath(world, playerFootPos, target)) {
                    routeCoordinator.repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
                } else {
                    fail(computation.failureReason(), computation.failureDetail());
                }
                return;
            }
            List<BlockPos> newPath = computation.path();
            synchronized (this) {
                navigationState.consecutivePlanningBudgetExhaustions = 0;
            }
            if (routeCoordinator.shouldKeepCommittedPath(world, playerFootPos, target, newPath, computation.plannedPrimitives(), now)) {
                routeCoordinator.repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
            } else {
            synchronized (this) {
                navigationState.currentPath = newPath;
                navigationState.candidatePaths = computation.candidatePaths();
                navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                navigationState.goalMode = routeCoordinator.shouldTrackResolvedPlanningGoal(target, computation.resolvedGoalPos(), computation.goalMode())
                    ? computation.goalMode()
                    : GoalMode.EXACT;
                navigationState.resolvedGoalPos = navigationState.goalMode == GoalMode.NEAREST_STANDABLE ? computation.resolvedGoalPos() : target.immutable();
                navigationState.committedPathGoalPos = computation.resolvedGoalPos() != null ? computation.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                navigationState.committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                navigationState.pathIndex = routeCoordinator.chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                navigationState.lastWaypointAdvanceAtMs = now;
                navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                executionState.plannedBreakTargets = routeCoordinator.buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                navigationState.currentPlan = computation.plannedPrimitives();
                executionState.activePlannedPrimitive = routeCoordinator.getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
                appendDebugEventLocked("pathDetailed=" + formatIndexedPath(navigationState.currentPath, 24));
                appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
                navigationState.lastPlanAtMs = now;
                navigationState.bestDistanceSq = distanceSq;
                navigationState.lastProgressAtMs = now;
                navigationState.routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                navigationState.lastLocalRecoveryAtMs = 0L;
                navigationState.localRecoveryAttempts = 0;
                navigationState.bestRouteProgressScore = routeCoordinator.routeProgressScoreLocked();
                navigationState.lastReplanReason = "planner replan";
            }
            }
        }

        BlockPos waypoint = routeCoordinator.chooseActiveWaypoint(world, player, playerFootPos);
        if (waypoint == null) {
            releaseMovementKeys(client);
            synchronized (this) {
                if (!navigationState.currentPath.isEmpty() && now - navigationState.lastWaypointAdvanceAtMs <= WAYPOINT_ACQUIRE_SETTLE_MS) {
                    navigationState.lastAdvanceDecision = "hold:settle_for_next_waypoint";
                    return;
                }
            }
            if (pathPlanner.hasReachedExactGoal(playerFootPos, target)) {
                releaseMovementKeys(client);
                complete(State.ARRIVED);
                return;
            }
            synchronized (this) {
                if (now - navigationState.lastPlanAtMs < REPLAN_COOLDOWN_MS) {
                    navigationState.lastAdvanceDecision = "hold:recovery_replan_cooldown";
                    return;
                }
            }
            PathComputation recovery = findPath(world, playerFootPos, target);
            if (!recovery.path().isEmpty()) {
                if (!pathPlanner.isViablePlannedPath(world, recovery.path(), recovery.plannedPrimitives())) {
                    if (routeCoordinator.canRepairCurrentPath(world, playerFootPos, target)) {
                        routeCoordinator.repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", "invalid recovery path");
                    } else {
                        routeCoordinator.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "invalid recovery path", "recovery path mismatch");
                    }
                    return;
                }
                if (routeCoordinator.shouldKeepCommittedPath(world, playerFootPos, target, recovery.path(), recovery.plannedPrimitives(), now)) {
                    routeCoordinator.repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", "keep committed route");
                } else {
                    synchronized (this) {
                        navigationState.currentPath = recovery.path();
                        navigationState.candidatePaths = recovery.candidatePaths();
                        navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                        navigationState.goalMode = routeCoordinator.shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                            ? recovery.goalMode()
                            : GoalMode.EXACT;
                        navigationState.resolvedGoalPos = navigationState.goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                        navigationState.committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                        navigationState.committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                        navigationState.pathIndex = routeCoordinator.chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                        navigationState.lastWaypointAdvanceAtMs = now;
                        navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                        navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                        executionState.plannedBreakTargets = routeCoordinator.buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                        navigationState.currentPlan = recovery.plannedPrimitives();
                        executionState.activePlannedPrimitive = routeCoordinator.getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                        appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
                        appendDebugEventLocked("pathDetailed=" + formatIndexedPath(navigationState.currentPath, 24));
                        appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
                        navigationState.lastPlanAtMs = now;
                        navigationState.lastProgressAtMs = now;
                        navigationState.routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                        navigationState.lastLocalRecoveryAtMs = 0L;
                        navigationState.localRecoveryAttempts = 0;
                        navigationState.bestRouteProgressScore = routeCoordinator.routeProgressScoreLocked();
                        navigationState.lastReplanReason = "waypoint recovery";
                    }
                }
                waypoint = routeCoordinator.chooseActiveWaypoint(world, player, playerFootPos);
            }
        }

        if (waypoint == null) {
            releaseMovementKeys(client);
            synchronized (this) {
                navigationState.lastReplanReason = "waypoint exhausted";
                navigationState.lastStuckReason = "no active waypoint";
            }
            fail(FailureReason.NO_ROUTE, "No active waypoint remained after replanning.");
            return;
        }

        if (primitiveExecutor.shouldForceFinalApproach(world, playerFootPos, target)) {
            waypoint = target.immutable();
            synchronized (this) {
                navigationState.activeWaypoint = waypoint;
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
                executionState.activePlannedPrimitive = routeCoordinator.createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
            }
        }

        if (primitiveExecutor.handleDirectFinalApproach(client, world, player, playerFootPos, target, now)) {
            recordDebugTransitions(now);
            return;
        }

        routeCoordinator.noteRouteProgress(now);

        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        routeCoordinator.clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, plannedPrimitive, now);
        synchronized (this) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }

        double controllerDistanceSq = routeCoordinator.distanceToControllerTargetSq(world, player, waypoint);
        ControllerMode activeController = routeCoordinator.updateControllerMode(world, player, playerFootPos, waypoint, plannedPrimitive, now, controllerDistanceSq);
        recordDebugTransitions(now);
        boolean handledController = false;
        switch (activeController) {
            case RECOVER_JUMP -> handledController = primitiveExecutor.handleJumpRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_BREAK -> handledController = primitiveExecutor.handleBreakRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_PILLAR -> handledController = primitiveExecutor.handlePillarRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_ESCAPE -> handledController = primitiveExecutor.handleEscapeRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case ESCAPE_HOLE -> handledController = primitiveExecutor.handleTrappedSpaceRecovery(client, world, player, playerFootPos, waypoint, now);
            case BREAK_BLOCK -> handledController = primitiveExecutor.handleCommittedMiningMovement(client, world, player, playerFootPos, waypoint, target, currentPos, now);
            case PILLAR -> handledController = primitiveExecutor.handlePillaring(client, world, player, playerFootPos, waypoint, now);
            case COMMIT_JUMP -> handledController = primitiveExecutor.handleCommittedJumpMovement(client, world, player, playerFootPos, now);
            case DROP -> handledController = primitiveExecutor.handleCommittedDropMovement(client, world, player, playerFootPos, waypoint, target, currentPos, now);
            case FOLLOW_PATH -> {
            }
        }
        if (handledController) {
            return;
        }
        if ((activeController == ControllerMode.PILLAR || activeController == ControllerMode.ESCAPE_HOLE)
            && primitiveExecutor.isCommittedLocalEscapeChain(now)) {
            synchronized (this) {
                if (activeController == ControllerMode.PILLAR) {
                    executionState.controllerMode = ControllerMode.ESCAPE_HOLE;
                    executionState.controllerTarget = executionState.committedEscapeTarget != null ? executionState.committedEscapeTarget : waypoint;
                }
                executionState.controllerEnteredAtMs = now;
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = distanceSq;
                executionState.controllerUntilMs = Math.max(executionState.controllerUntilMs, executionState.committedEscapeUntilMs);
            }
            return;
        }
        if (activeController == ControllerMode.PILLAR) {
            synchronized (this) {
                executionState.controllerMode = ControllerMode.FOLLOW_PATH;
                executionState.controllerTarget = null;
                executionState.controllerUntilMs = 0L;
                executionState.controllerEnteredAtMs = now;
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = distanceSq;
            }
            waypoint = routeCoordinator.chooseActiveWaypoint(world, player, playerFootPos);
            if (waypoint == null) {
                releaseMovementKeys(client);
                synchronized (this) {
                    navigationState.lastReplanReason = "waypoint exhausted";
                    navigationState.lastStuckReason = "no active waypoint";
                }
                fail(FailureReason.NO_ROUTE, "Recovery replanning did not produce a usable route.");
                return;
            }
            if (primitiveExecutor.shouldForceFinalApproach(world, playerFootPos, target)) {
                waypoint = target.immutable();
                synchronized (this) {
                    navigationState.activeWaypoint = waypoint;
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
                    executionState.activePlannedPrimitive = routeCoordinator.createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
                }
            }
        }

        controllerDistanceSq = routeCoordinator.distanceToControllerTargetSq(world, player, waypoint);
        if (routeCoordinator.shouldRedirectController(now, controllerDistanceSq)) {
            releaseMovementKeys(client);
            routeCoordinator.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "controller redirect", activeController.name().toLowerCase());
            return;
        }
        if (primitiveExecutor.handleFollowPathSegment(client, world, player, playerFootPos, waypoint, plannedPrimitive, target, currentPos, distanceSq, now)) {
            return;
        }
    }

    public synchronized void stop(String reason) {
        stopInternal(true, reason);
    }

    public synchronized void reset() {
        stopInternal(false, "reset");
    }

    private synchronized void fail(FailureReason failureReason) {
        fail(failureReason, null);
    }

    private synchronized void fail(FailureReason failureReason, String failureDetail) {
        releaseMovementKeys(Minecraft.getInstance());
        state = State.FAILED;
        CompletableFuture<Void> future = activeFuture;
        String message = failureReason != null ? failureReason.message : FailureReason.NO_ROUTE.message;
        if (failureDetail != null && !failureDetail.isBlank()) {
            message = message + " " + failureDetail.trim();
        }
        Minecraft client = Minecraft.getInstance();
        NavigatorCameraController.end(client != null ? client.player : null);
        BlockPos playerFootPos = client != null && client.player != null ? resolvePlayerFootPos(client.player) : null;
        appendDebugEventLocked(
            "fail reason=" + message
                + " player=" + formatDebugPos(playerFootPos)
                + " target=" + formatDebugPos(targetPos)
                + " resolved=" + formatDebugPos(navigationState.resolvedGoalPos)
                + " goal=" + navigationState.goalMode.name()
        );
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        navigationState.currentPath = List.of();
        navigationState.currentPlan = List.of();
        navigationState.candidatePaths = List.of();
        navigationState.candidatePathsVisibleUntilMs = 0L;
        navigationState.pathIndex = 0;
        navigationState.furthestVisitedPathIndex = 0;
        navigationState.activeWaypoint = null;
        executionState.plannedBreakTargets = List.of();
        navigationState.resolvedGoalPos = null;
        navigationState.committedPathGoalPos = null;
        navigationState.committedPathStartPos = null;
        executionState.committedJumpWaypoint = null;
        executionState.committedJumpUntilMs = 0L;
        executionState.lastJumpAttemptWaypoint = null;
        executionState.repeatedJumpAttempts = 0;
        executionState.lastInteractAtMs = 0L;
        executionState.committedEscapeTarget = null;
        executionState.committedEscapeUntilMs = 0L;
        executionState.committedEscape = EscapePlan.empty();
        executionState.committedEscapePrimitiveIndex = 0;
        executionState.controllerMode = ControllerMode.FOLLOW_PATH;
        executionState.controllerTarget = null;
        executionState.controllerUntilMs = 0L;
        executionState.controllerEnteredAtMs = 0L;
        executionState.controllerProgressAtMs = 0L;
        executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        executionState.lastPlaceTarget = null;
        executionState.lastPlaceResult = "none";
        navigationState.routeCommitUntilMs = 0L;
        navigationState.lastLocalRecoveryAtMs = 0L;
        navigationState.localRecoveryAttempts = 0;
        navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
        navigationState.consecutivePlanningBudgetExhaustions = 0;
        executionState.activeFollowSegment = FollowSegmentType.GROUND;
        executionState.activeFollowSegmentTarget = null;
        executionState.activePlannedPrimitive = null;
        executionState.activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        executionState.activePillarPhase = PillarPhase.CENTER;
        executionState.activeFollowSegmentEnteredAtMs = 0L;
        executionState.activeFollowSegmentProgressAtMs = 0L;
        executionState.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        navigationState.lastMovementSamplePos = Vec3.ZERO;
        navigationState.lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        navigationState.lastDistanceCheckpointAtMs = 0L;
        NodeErrorNotificationOverlay.getInstance().show(message, UITheme.STATE_ERROR);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new RuntimeException(message));
        }
        state = State.IDLE;
        renderSnapshot = null;
    }

    private synchronized void complete(State terminalState) {
        state = terminalState;
        CompletableFuture<Void> future = activeFuture;
        Minecraft client = Minecraft.getInstance();
        NavigatorCameraController.end(client != null ? client.player : null);
        BlockPos playerFootPos = client != null && client.player != null ? resolvePlayerFootPos(client.player) : null;
        BlockPos completedTarget = targetPos != null ? targetPos.immutable() : null;
        appendDebugEventLocked(
            "complete state=" + terminalState.name()
                + " player=" + formatDebugPos(playerFootPos)
                + " target=" + formatDebugPos(targetPos)
                + " resolved=" + formatDebugPos(navigationState.resolvedGoalPos)
                + " goal=" + navigationState.goalMode.name()
        );
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        navigationState.currentPath = List.of();
        navigationState.currentPlan = List.of();
        navigationState.candidatePaths = List.of();
        navigationState.candidatePathsVisibleUntilMs = 0L;
        navigationState.pathIndex = 0;
        navigationState.furthestVisitedPathIndex = 0;
        navigationState.activeWaypoint = null;
        executionState.plannedBreakTargets = List.of();
        navigationState.resolvedGoalPos = null;
        navigationState.committedPathGoalPos = null;
        navigationState.committedPathStartPos = null;
        executionState.committedJumpWaypoint = null;
        executionState.committedJumpUntilMs = 0L;
        executionState.lastJumpAttemptWaypoint = null;
        executionState.repeatedJumpAttempts = 0;
        executionState.lastInteractAtMs = 0L;
        executionState.committedEscapeTarget = null;
        executionState.committedEscapeUntilMs = 0L;
        executionState.committedEscape = EscapePlan.empty();
        executionState.committedEscapePrimitiveIndex = 0;
        executionState.controllerMode = ControllerMode.FOLLOW_PATH;
        executionState.controllerTarget = null;
        executionState.controllerUntilMs = 0L;
        executionState.controllerEnteredAtMs = 0L;
        executionState.controllerProgressAtMs = 0L;
        executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        executionState.lastPlaceTarget = null;
        executionState.lastPlaceResult = "none";
        navigationState.routeCommitUntilMs = 0L;
        navigationState.lastLocalRecoveryAtMs = 0L;
        navigationState.localRecoveryAttempts = 0;
        navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
        navigationState.consecutivePlanningBudgetExhaustions = 0;
        executionState.activeFollowSegment = FollowSegmentType.GROUND;
        executionState.activeFollowSegmentTarget = null;
        executionState.activePlannedPrimitive = null;
        executionState.activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        executionState.activePillarPhase = PillarPhase.CENTER;
        executionState.activeFollowSegmentEnteredAtMs = 0L;
        executionState.activeFollowSegmentProgressAtMs = 0L;
        executionState.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        navigationState.lastMovementSamplePos = Vec3.ZERO;
        navigationState.lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        navigationState.lastDistanceCheckpointAtMs = 0L;
        if (terminalState == State.ARRIVED) {
            String message = completedTarget == null
                ? "Pathmind Nav: path complete."
                : "Pathmind Nav: arrived at " + completedTarget.getX() + " " + completedTarget.getY() + " " + completedTarget.getZ() + ".";
            NodeErrorNotificationOverlay.getInstance().show(message, UITheme.STATE_SUCCESS);
        }
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
        state = State.IDLE;
    }

    private void stopInternal(boolean completeFuture, String reason) {
        Minecraft client = Minecraft.getInstance();
        releaseMovementKeys(client);
        NavigatorCameraController.end(client != null ? client.player : null);
        appendDebugEventLocked("stop reason=" + (reason == null ? "none" : reason));
        if (activeFuture != null && !activeFuture.isDone()) {
            if (completeFuture) {
                activeFuture.complete(null);
            } else {
                String cancellationReason = reason == null || reason.isBlank() ? "cancelled" : reason.trim();
                activeFuture.completeExceptionally(new CancellationException("Pathmind Nav " + cancellationReason + "."));
            }
        }
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        navigationState.bestDistanceSq = Double.MAX_VALUE;
        navigationState.currentPath = List.of();
        navigationState.currentPlan = List.of();
        navigationState.candidatePaths = List.of();
        navigationState.candidatePathsVisibleUntilMs = 0L;
        navigationState.pathIndex = 0;
        navigationState.furthestVisitedPathIndex = 0;
        navigationState.activeWaypoint = null;
        executionState.plannedBreakTargets = List.of();
        navigationState.resolvedGoalPos = null;
        navigationState.committedPathGoalPos = null;
        navigationState.committedPathStartPos = null;
        executionState.committedJumpWaypoint = null;
        executionState.committedJumpUntilMs = 0L;
        executionState.lastJumpAttemptWaypoint = null;
        executionState.repeatedJumpAttempts = 0;
        executionState.lastInteractAtMs = 0L;
        executionState.committedEscapeTarget = null;
        executionState.committedEscapeUntilMs = 0L;
        executionState.committedEscape = EscapePlan.empty();
        executionState.committedEscapePrimitiveIndex = 0;
        executionState.controllerMode = ControllerMode.FOLLOW_PATH;
        executionState.controllerTarget = null;
        executionState.controllerUntilMs = 0L;
        executionState.controllerEnteredAtMs = 0L;
        executionState.controllerProgressAtMs = 0L;
        executionState.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        executionState.lastPlaceTarget = null;
        executionState.lastPlaceResult = "none";
        navigationState.routeCommitUntilMs = 0L;
        navigationState.lastLocalRecoveryAtMs = 0L;
        navigationState.localRecoveryAttempts = 0;
        navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
        navigationState.consecutivePlanningBudgetExhaustions = 0;
        executionState.activeFollowSegment = FollowSegmentType.GROUND;
        executionState.activeFollowSegmentTarget = null;
        executionState.activePlannedPrimitive = null;
        executionState.activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        executionState.activePillarPhase = PillarPhase.CENTER;
        executionState.activeFollowSegmentEnteredAtMs = 0L;
        executionState.activeFollowSegmentProgressAtMs = 0L;
        executionState.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        navigationState.lastMovementSamplePos = Vec3.ZERO;
        navigationState.lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        navigationState.lastDistanceCheckpointAtMs = 0L;
        long now = System.currentTimeMillis();
        startedAtMs = now;
        navigationState.lastProgressAtMs = now;
        navigationState.lastPlanAtMs = 0L;
        executionState.lastJumpAtMs = 0L;
        navigationState.goalMode = GoalMode.EXACT;
        state = completeFuture ? State.STOPPED : State.IDLE;
        if (state == State.STOPPED) {
            state = State.IDLE;
        }
        renderSnapshot = null;
    }

    private String formatPlannedPrimitiveSequence(List<PlannedPrimitive> plan, int limit) {
        if (plan == null || plan.isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        int count = Math.min(limit, plan.size());
        for (int i = 0; i < count; i++) {
            PlannedPrimitive primitive = plan.get(i);
            if (primitive == null || primitive.searchType() == null) {
                continue;
            }
            parts.add(formatPrimitiveLabel(primitive));
        }
        if (plan.size() > count) {
            parts.add("...");
        }
        return parts.toString();
    }

    private String formatIndexedPrimitiveSequence(List<PlannedPrimitive> plan, int limit) {
        if (plan == null || plan.isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        int count = Math.min(limit, plan.size());
        for (int i = 0; i < count; i++) {
            PlannedPrimitive primitive = plan.get(i);
            parts.add(i + ":" + formatPlannedPrimitive(primitive));
        }
        if (plan.size() > count) {
            parts.add("...");
        }
        return parts.toString();
    }

    private String formatIndexedPath(List<BlockPos> path, int limit) {
        if (path == null || path.isEmpty()) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        int count = Math.min(limit, path.size());
        for (int i = 0; i < count; i++) {
            parts.add(i + ":" + formatDebugPos(path.get(i)));
        }
        if (path.size() > count) {
            parts.add("...");
        }
        return parts.toString();
    }

    private String formatPlannedPrimitive(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return "none";
        }
        return formatPrimitiveLabel(primitive) + "@" + formatDebugPos(primitive.target());
    }

    private String formatPrimitiveLabel(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return "none";
        }
        if (primitive.type() == null || primitive.type().name().equals(primitive.searchType().name())) {
            return primitive.searchType().name();
        }
        return primitive.searchType().name() + "->" + primitive.type().name();
    }

    private PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target) {
        return pathPlanner.findPath(world, start, target);
    }

    private BlockPos resolvePlayerFootPos(LocalPlayer player) {
        return player == null ? null : player.blockPosition().immutable();
    }

    static void releaseMovementKeys(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }
        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(false);
        }
        if (client.options.keyDown != null) {
            client.options.keyDown.setDown(false);
        }
        if (client.options.keyLeft != null) {
            client.options.keyLeft.setDown(false);
        }
        if (client.options.keyRight != null) {
            client.options.keyRight.setDown(false);
        }
        if (client.options.keyJump != null) {
            client.options.keyJump.setDown(false);
        }
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyShift != null) {
            client.options.keyShift.setDown(false);
        }
    }

    static void applySneakState(Minecraft client, boolean active) {
        if (client == null || client.player == null) {
            return;
        }
        boolean previous = client.player.isShiftKeyDown();
        client.player.setShiftKeyDown(active);
        if (client.options != null && client.options.keyShift != null) {
            client.options.keyShift.setDown(active);
        }
        if (client.player.connection != null && previous != active) {
            ServerboundPlayerCommandPacket.Action mode = resolveSneakCommandMode(active);
            if (mode != null) {
                client.player.connection.send(new ServerboundPlayerCommandPacket(client.player, mode));
            }
        }
    }

    static BlockHitResult raycastBlockFromOrientation(Minecraft client, float yaw, float pitch, double distance) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        Vec3 eyePos = client.player.getEyePosition();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 direction = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        Vec3 end = eyePos.add(direction.scale(distance));
        HitResult hit = client.level.clip(new ClipContext(
            eyePos,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            client.player
        ));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }

    private static ServerboundPlayerCommandPacket.Action resolveSneakCommandMode(boolean active) {
        String[] candidates = active
            ? new String[]{"PRESS_SHIFT_KEY", "START_SNEAKING"}
            : new String[]{"RELEASE_SHIFT_KEY", "STOP_SNEAKING"};
        for (String candidate : candidates) {
            try {
                return ServerboundPlayerCommandPacket.Action.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try the next compatible enum name.
            }
        }
        return null;
    }

    static float movementYawStep() {
        return LoaderMetadata.isNeoForge() ? NEOFORGE_MAX_YAW_STEP : MAX_YAW_STEP;
    }

    static float stepAngle(float current, float target, float maxStep) {
        float delta = Mth.wrapDegrees(target - current);
        return current + Mth.clamp(delta, -maxStep, maxStep);
    }


}
