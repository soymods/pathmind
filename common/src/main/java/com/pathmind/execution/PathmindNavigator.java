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
import java.util.LinkedHashSet;
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
    private static final double WAYPOINT_SAFE_EDGE_INSET = 0.24D;
    private static final float MAX_YAW_STEP = 14.0F;
    private static final float NEOFORGE_MAX_YAW_STEP = 8.0F;
    private static final int BLOCK_INTERACTION_APPROACH_RADIUS = 4;
    private static final int BLOCK_INTERACTION_APPROACH_UP = 2;
    private static final int BLOCK_INTERACTION_APPROACH_DOWN = 4;
    private static final long REPLAN_COOLDOWN_MS = 450L;
    private static final long JUMP_RECOVERY_GRACE_MS = 700L;
    private static final long BREAK_COMMIT_WINDOW_MS = 1800L;
    private static final long DROP_COMMIT_WINDOW_MS = 1500L;
    static final long TRAPPED_RECOVERY_COMMIT_MS = 10000L;
    private static final double DISTANCE_STALL_THRESHOLD = 2.0D;
    static final long ROUTE_COMMIT_MS = 8000L;
    private static final long ROUTE_STABILIZATION_MS = 1800L;
    private static final long LOCAL_RECOVERY_COOLDOWN_MS = 550L;
    private static final int MAX_LOCAL_RECOVERY_ATTEMPTS = 2;
    static final long PATH_DECISION_VISIBILITY_MS = 1400L;
    private static final long WAYPOINT_ACQUIRE_SETTLE_MS = 300L;
    private static final double MOVEMENT_EPSILON_SQ = 0.0025D;
    static final int MAX_DROP_DOWN = 3;
    private static final int MAX_PATH_BREAK_LOOKAHEAD = 8;
    static final int MAX_GOAL_PATH_ATTEMPTS = 8;
    private static final int MAX_PLANNING_BUDGET_RETRIES = 6;
    private static final int PROACTIVE_REPLAN_LOOKAHEAD_STEPS = 6;
    private static final int MAX_SNAPSHOT_PATH_POINTS = 96;
    private static final int MAX_SNAPSHOT_CANDIDATE_POINTS = 64;
    private static final int MAX_DEBUG_EVENTS = 12;
    private static final Path NAV_DEBUG_LOG_PATH = Path.of(System.getProperty("user.dir"), ".pathmind", "logs", "navigator-debug.log");
    private static final long DEBUG_HEARTBEAT_INTERVAL_MS = 1500L;
    private static final double MIN_PROGRESS_FOR_REPLAN_SQ = 9.0D;
    static final double PLACE_MOVE_PENALTY = 12.0D;
    private static final double PILLAR_MOVE_PENALTY = 2.4D;

    private CompletableFuture<Void> activeFuture;
    private BlockPos targetPos;
    private String commandLabel;
    private State state = State.IDLE;
    private long startedAtMs;
    private GoalMode goalMode = GoalMode.EXACT;
    private WaterMode waterMode = WaterMode.NORMAL;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacing = true;
    private boolean eventLoggingEnabled = !LoaderMetadata.isNeoForge();
    private BlockPos committedPathStartPos;
    private int consecutivePlanningBudgetExhaustions;
    private final NavigatorExecutionState executionState = new NavigatorExecutionState();
    private final NavigatorNavigationState navigationState = new NavigatorNavigationState();
    private double lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
    private volatile Snapshot renderSnapshot;
    private final PathmindPathPlanner pathPlanner = new PathmindPathPlanner(new PlannerHost());
    private final NavigatorPrimitiveExecutor primitiveExecutor = new NavigatorPrimitiveExecutor(new PrimitiveHost(), executionState, navigationState, pathPlanner);
    private String previousControllerMode = "none";
    private String previousPrimitiveLabel = "none";
    private String previousMiningAscentPhase = MiningAscentPhase.CLEARANCE.name();
    private String previousPillarPhase = PillarPhase.CENTER.name();
    private BlockPos previousActiveWaypoint;
    private String previousReplanReason = "none";
    private String previousStuckReason = "none";
    private String lastReplanDecision = "none";
    private String lastReplaceDecision = "none";
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
            return PathmindNavigator.this.isWaypointActionable(world, waypoint);
        }

        @Override
        public List<PlannedPrimitive> buildPlannedPrimitives(Level world, List<BlockPos> path, BlockPos startPos) {
            return PathmindNavigator.this.buildPlannedPrimitives(world, path, startPos);
        }

        @Override
        public boolean requiresBreakingForWaypoint(Level world, BlockPos waypoint) {
            return primitiveExecutor.requiresBreakingForWaypoint(world, waypoint);
        }

        @Override
        public PlannedPrimitive createPlannedPrimitive(Level world, BlockPos from, BlockPos to,
                                                       List<BlockPos> breakTargets, BlockPos placeTarget) {
            return PathmindNavigator.this.createPlannedPrimitive(world, from, to, breakTargets, placeTarget);
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
            return PathmindNavigator.this.isDirectGoalCompletionCandidate(candidate, target);
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
                    getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex + 1)
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
        @Override public GoalMode goalMode() { return goalMode; }
        @Override public void goalMode(GoalMode goalMode) { PathmindNavigator.this.goalMode = goalMode; }
        @Override public void appendDebugEventLocked(String event) { PathmindNavigator.this.appendDebugEventLocked(event); }
        @Override public String formatDebugPos(BlockPos pos) { return PathmindNavigator.this.formatDebugPos(pos); }
        @Override public boolean isWaypointActionable(Level world, BlockPos waypoint) { return PathmindNavigator.this.isWaypointActionable(world, waypoint); }
        @Override public boolean shouldTrackResolvedPlanningGoal(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode) { return PathmindNavigator.this.shouldTrackResolvedPlanningGoal(target, resolvedGoal, goalMode); }
        @Override public boolean isPlayerNearPath(BlockPos playerFootPos) { return PathmindNavigator.this.isPlayerNearPath(playerFootPos); }
        @Override public boolean hasCommittedEscapeWorkLocked(long now) { return PathmindNavigator.this.hasCommittedEscapeWorkLocked(now); }
        @Override public boolean isActiveEscapeBreakTargetLocked() { return PathmindNavigator.this.isActiveEscapeBreakTargetLocked(); }
        @Override public boolean isJumpExecutionLocked(long now, PlannedPrimitive primitive) { return PathmindNavigator.this.isJumpExecutionLocked(now, primitive); }
        @Override public void noteControllerProgress(long now, double distanceSq) { PathmindNavigator.this.noteControllerProgress(now, distanceSq); }
        @Override public double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget) { return PathmindNavigator.this.distanceToControllerTargetSq(world, player, fallbackTarget); }
        @Override public void noteControllerActivity(long now) { PathmindNavigator.this.noteControllerActivity(now); }
        @Override public boolean isRouteStabilizingLocked(BlockPos playerFootPos, long now) { return PathmindNavigator.this.isRouteStabilizingLocked(playerFootPos, now); }
        @Override public void updateFollowSegment(FollowSegmentType type, BlockPos target, double distanceSq, long now) { PathmindNavigator.this.updateFollowSegment(type, target, distanceSq, now); }
        @Override public long followSegmentIdleMs(long now) { return PathmindNavigator.this.followSegmentIdleMs(now); }
        @Override public boolean shouldRedirectController(long now, double distanceSq) { return PathmindNavigator.this.shouldRedirectController(now, distanceSq); }
        @Override public boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive primitive, long now) { return PathmindNavigator.this.shouldUsePillarStep(world, playerFootPos, waypoint, primitive, now); }
        @Override public void clearStaleEscapeRecoveryIfNeeded(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive primitive, long now) { PathmindNavigator.this.clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, primitive, now); }
        @Override public void recoverFromStuck(Minecraft client, ClientLevel world, BlockPos playerFootPos, BlockPos waypoint, BlockPos target, Vec3 currentPos, long now, String replanReason, String stuckReason) { PathmindNavigator.this.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, replanReason, stuckReason); }
        @Override public void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint) { PathmindNavigator.this.rewindCurrentPathIndex(playerFootPos, preferredWaypoint); }
        @Override public void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now, String replanReason, String stuckReason) { PathmindNavigator.this.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, replanReason, stuckReason); }
        @Override public void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) { PathmindNavigator.this.rememberFailedRedirectWindow(playerFootPos, waypoint, now); }
        @Override public List<BlockPos> buildPathBreakPlan(Level world, List<BlockPos> path, int startIndex) { return PathmindNavigator.this.buildPathBreakPlan(world, path, startIndex); }
        @Override public PlannedPrimitive createPrimitiveSnapshot(Level world, BlockPos from, BlockPos to, SearchPrimitiveType searchType, PlannedPrimitiveType type, List<BlockPos> breakTargets, BlockPos placeTarget) { return PathmindNavigator.this.createPrimitiveSnapshot(world, from, to, searchType, type, breakTargets, placeTarget); }
        @Override public PlannedPrimitive getPlannedPrimitiveAtIndexLocked(int index) { return PathmindNavigator.this.getPlannedPrimitiveAtIndexLocked(index); }
        @Override public void rebuildCurrentPlanLocked(Level world) { PathmindNavigator.this.rebuildCurrentPlanLocked(world); }
        @Override public PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target) { return PathmindNavigator.this.findPath(world, start, target); }
        @Override public BlockPos resolvePlayerFootPos(LocalPlayer player) { return PathmindNavigator.this.resolvePlayerFootPos(player); }
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
        this.goalMode = GoalMode.EXACT;
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
        this.consecutivePlanningBudgetExhaustions = 0;
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
            goalMode.name(),
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
            lastReplanDecision,
            navigationState.lastAdvanceDecision,
            lastReplaceDecision,
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
                        + " replanDecision=" + lastReplanDecision
                        + " advanceDecision=" + navigationState.lastAdvanceDecision
                        + " replaceDecision=" + lastReplaceDecision
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
            lastReplanDecision = decision == null || decision.isBlank() ? "none" : decision;
        }
    }

    private void setAdvanceDecision(String decision) {
        synchronized (this) {
            navigationState.lastAdvanceDecision = decision == null || decision.isBlank() ? "none" : decision;
        }
    }

    private void setReplaceDecision(String decision) {
        synchronized (this) {
            lastReplaceDecision = decision == null || decision.isBlank() ? "none" : decision;
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
        this.goalMode = GoalMode.EXACT;
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
        this.consecutivePlanningBudgetExhaustions = 0;
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
        this.goalMode = computation.goalMode();
        navigationState.resolvedGoalPos = computation.resolvedGoalPos();
        navigationState.committedPathGoalPos = navigationState.resolvedGoalPos != null ? navigationState.resolvedGoalPos.immutable() : this.targetPos;
        this.committedPathStartPos = start != null ? start.immutable() : null;
        navigationState.pathIndex = chooseInitialPathIndex(navigationState.currentPath, start, this.targetPos);
        navigationState.lastWaypointAdvanceAtMs = System.currentTimeMillis();
        navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
        navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
        executionState.plannedBreakTargets = buildPathBreakPlan(client.level, navigationState.currentPath, navigationState.pathIndex);
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
        if (shouldReplan(world, playerFootPos, target, now)) {
            PathComputation computation = findPath(world, playerFootPos, target);
            if (computation.path().isEmpty()) {
                if (computation.failureReason() == FailureReason.SEARCH_LIMIT
                    && deferPlanningAfterBudgetExhaustion(now, computation.failureDetail())) {
                    releaseMovementKeys(client);
                    return;
                }
                if (canRepairCurrentPath(world, playerFootPos, target)) {
                    repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
                } else {
                    fail(computation.failureReason(), computation.failureDetail());
                }
                return;
            }
            List<BlockPos> newPath = computation.path();
            synchronized (this) {
                consecutivePlanningBudgetExhaustions = 0;
            }
            if (shouldKeepCommittedPath(world, playerFootPos, target, newPath, computation.plannedPrimitives(), now)) {
                repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
            } else {
            synchronized (this) {
                navigationState.currentPath = newPath;
                navigationState.candidatePaths = computation.candidatePaths();
                navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                goalMode = shouldTrackResolvedPlanningGoal(target, computation.resolvedGoalPos(), computation.goalMode())
                    ? computation.goalMode()
                    : GoalMode.EXACT;
                navigationState.resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? computation.resolvedGoalPos() : target.immutable();
                navigationState.committedPathGoalPos = computation.resolvedGoalPos() != null ? computation.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                navigationState.pathIndex = chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                navigationState.lastWaypointAdvanceAtMs = now;
                navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                navigationState.currentPlan = computation.plannedPrimitives();
                executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
                appendDebugEventLocked("pathDetailed=" + formatIndexedPath(navigationState.currentPath, 24));
                appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
                navigationState.lastPlanAtMs = now;
                navigationState.bestDistanceSq = distanceSq;
                navigationState.lastProgressAtMs = now;
                navigationState.routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                navigationState.lastLocalRecoveryAtMs = 0L;
                navigationState.localRecoveryAttempts = 0;
                navigationState.bestRouteProgressScore = routeProgressScoreLocked();
                navigationState.lastReplanReason = "planner replan";
            }
            }
        }

        BlockPos waypoint = chooseActiveWaypoint(world, player, playerFootPos);
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
                    if (canRepairCurrentPath(world, playerFootPos, target)) {
                        repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", "invalid recovery path");
                    } else {
                        redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "invalid recovery path", "recovery path mismatch");
                    }
                    return;
                }
                if (shouldKeepCommittedPath(world, playerFootPos, target, recovery.path(), recovery.plannedPrimitives(), now)) {
                    repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", "keep committed route");
                } else {
                    synchronized (this) {
                        navigationState.currentPath = recovery.path();
                        navigationState.candidatePaths = recovery.candidatePaths();
                        navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                        goalMode = shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                            ? recovery.goalMode()
                            : GoalMode.EXACT;
                        navigationState.resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                        navigationState.committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                        committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                        navigationState.pathIndex = chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                        navigationState.lastWaypointAdvanceAtMs = now;
                        navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                        navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                        executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                        navigationState.currentPlan = recovery.plannedPrimitives();
                        executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                        appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
                        appendDebugEventLocked("pathDetailed=" + formatIndexedPath(navigationState.currentPath, 24));
                        appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
                        navigationState.lastPlanAtMs = now;
                        navigationState.lastProgressAtMs = now;
                        navigationState.routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                        navigationState.lastLocalRecoveryAtMs = 0L;
                        navigationState.localRecoveryAttempts = 0;
                        navigationState.bestRouteProgressScore = routeProgressScoreLocked();
                        navigationState.lastReplanReason = "waypoint recovery";
                    }
                }
                waypoint = chooseActiveWaypoint(world, player, playerFootPos);
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
                executionState.activePlannedPrimitive = createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
            }
        }

        if (primitiveExecutor.handleDirectFinalApproach(client, world, player, playerFootPos, target, now)) {
            recordDebugTransitions(now);
            return;
        }

        noteRouteProgress(now);

        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, plannedPrimitive, now);
        synchronized (this) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }

        double controllerDistanceSq = distanceToControllerTargetSq(world, player, waypoint);
        ControllerMode activeController = updateControllerMode(world, player, playerFootPos, waypoint, plannedPrimitive, now, controllerDistanceSq);
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
            waypoint = chooseActiveWaypoint(world, player, playerFootPos);
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
                    executionState.activePlannedPrimitive = createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
                }
            }
        }

        controllerDistanceSq = distanceToControllerTargetSq(world, player, waypoint);
        if (shouldRedirectController(now, controllerDistanceSq)) {
            releaseMovementKeys(client);
            recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "controller redirect", activeController.name().toLowerCase());
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
                + " goal=" + goalMode.name()
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
        committedPathStartPos = null;
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
        consecutivePlanningBudgetExhaustions = 0;
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
                + " goal=" + goalMode.name()
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
        committedPathStartPos = null;
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
        consecutivePlanningBudgetExhaustions = 0;
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
        committedPathStartPos = null;
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
        consecutivePlanningBudgetExhaustions = 0;
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
        goalMode = GoalMode.EXACT;
        state = completeFuture ? State.STOPPED : State.IDLE;
        if (state == State.STOPPED) {
            state = State.IDLE;
        }
        renderSnapshot = null;
    }

    private boolean shouldReplan(ClientLevel world, BlockPos start, BlockPos target, long now) {
        synchronized (this) {
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                if (navigationState.lastPlanAtMs > 0L && now - navigationState.lastPlanAtMs < REPLAN_COOLDOWN_MS) {
                    lastReplanDecision = "keep:planning_retry_cooldown";
                    return false;
                }
                lastReplanDecision = "replan:no_active_path";
                return true;
            }
            if (primitiveExecutor.isCommittedLocalEscapeChain(now)) {
                lastReplanDecision = "keep:escape_chain";
                return false;
            }
            if (isCommittedPillarState(world, start, now)) {
                lastReplanDecision = "keep:pillar_state";
                return false;
            }
            if (isRecoveryState(world, start, now)) {
                lastReplanDecision = "keep:recovery_state";
                return false;
            }
            if (isExcavatingState(now)) {
                lastReplanDecision = "keep:excavating";
                return false;
            }
            if (isJumpExecutionLocked(now, executionState.activePlannedPrimitive)) {
                lastReplanDecision = "keep:jump_locked";
                return false;
            }
            boolean committedGoalValid = isPathGoalStillValid(navigationState.currentPath, committedPathGoalLocked(target));
            boolean routeReachesRequestedTarget = isPathGoalStillValid(navigationState.currentPath, target);
            boolean nearCommittedRoute = isPlayerNearPath(start) || isPlayerNearCommittedPathStart(start);
            if (!routeReachesRequestedTarget
                && nearCommittedRoute
                && shouldProactivelyRefreshRouteLocked(target, now)) {
                lastReplanDecision = "replan:refresh_partial_route";
                return true;
            }
            if (committedGoalValid && nearCommittedRoute && isWaypointActionable(world, navigationState.activeWaypoint)) {
                lastReplanDecision = "keep:committed_route_valid";
                return false;
            }
            if (now < navigationState.routeCommitUntilMs) {
                lastReplanDecision = "keep:commit_window";
                return false;
            }
            if (now - navigationState.lastProgressAtMs < 2000L) {
                lastReplanDecision = "keep:recent_progress";
                return false;
            }
            if (!isWaypointActionable(world, navigationState.activeWaypoint)) {
                lastReplanDecision = "replan:waypoint_not_actionable";
                return true;
            }
            if (!isPlayerNearPath(start)) {
                lastReplanDecision = "replan:player_not_near_path";
                return true;
            }
            lastReplanDecision = "keep:default";
            return false;
        }
    }

    private synchronized boolean deferPlanningAfterBudgetExhaustion(long now, String detail) {
        consecutivePlanningBudgetExhaustions++;
        navigationState.lastPlanAtMs = now;
        navigationState.lastReplanReason = "planning budget retry " + consecutivePlanningBudgetExhaustions;
        navigationState.lastStuckReason = "planner time budget";
        appendDebugEventLocked(
            "planner deferred retry=" + consecutivePlanningBudgetExhaustions
                + " detail=" + (detail == null || detail.isBlank() ? "none" : detail)
        );
        return consecutivePlanningBudgetExhaustions <= MAX_PLANNING_BUDGET_RETRIES;
    }

    private boolean shouldProactivelyRefreshRouteLocked(BlockPos target, long now) {
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

    private BlockPos committedPathGoalLocked(BlockPos fallbackTarget) {
        return navigationState.committedPathGoalPos != null ? navigationState.committedPathGoalPos : fallbackTarget;
    }

    private boolean isWaypointActionable(Level world, BlockPos waypoint) {
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
            return allowBlockBreaking;
        }
        if (pathPlanner.needsPlacedSupport(world, waypoint)) {
            return allowBlockPlacing && pathPlanner.canPlaceSupportAt(world, waypoint.below());
        }
        return pathPlanner.resolveSupportSurfaceY(world, waypoint).isPresent() || pathPlanner.isWaterNode(world, waypoint);
    }

    private boolean isPathGoalStillValid(List<BlockPos> path, BlockPos target) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return pathPlanner.horizontalDistanceSq(last, target) <= 4.0D && Math.abs(last.getY() - target.getY()) <= MAX_DROP_DOWN;
    }

    private boolean shouldTrackResolvedPlanningGoal(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode) {
        if (goalMode != GoalMode.NEAREST_STANDABLE || target == null || resolvedGoal == null) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(target, resolvedGoal) <= 4.0D
            && Math.abs(target.getY() - resolvedGoal.getY()) <= MAX_DROP_DOWN;
    }

    private boolean isDirectGoalCompletionCandidate(BlockPos candidate, BlockPos target) {
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

    private boolean isPlayerNearPath(BlockPos playerFootPos) {
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
        if (targetPos != null && pathPlanner.horizontalDistanceSq(playerFootPos, targetPos) < MIN_PROGRESS_FOR_REPLAN_SQ) {
            return true;
        }
        return false;
    }

    private boolean isExcavatingState(long now) {
        synchronized (this) {
            boolean activeEscapeController = executionState.controllerMode == ControllerMode.ESCAPE_HOLE
                || executionState.controllerMode == ControllerMode.RECOVER_ESCAPE
                || executionState.controllerMode == ControllerMode.PILLAR
                || executionState.controllerMode == ControllerMode.RECOVER_PILLAR;
            return activeEscapeController
                && (hasCommittedEscapeWorkLocked(now) || isActiveEscapeBreakTargetLocked());
        }
    }

    private boolean hasCommittedEscapeWorkLocked(long now) {
        return !executionState.committedEscape.isEmpty()
            && executionState.committedEscapePrimitiveIndex < executionState.committedEscape.primitives().size()
            && executionState.committedEscapeUntilMs > now;
    }

    private boolean isActiveEscapeBreakTargetLocked() {
        return executionState.activeBreakTarget != null
            && !executionState.committedEscape.isEmpty()
            && executionState.committedEscape.breakTargets().contains(executionState.activeBreakTarget);
    }

    private boolean isJumpExecutionLocked(long now, PlannedPrimitive plannedPrimitive) {
        synchronized (this) {
            if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > now) {
                return true;
            }
            return primitiveExecutor.isJumpPrimitive(plannedPrimitive) && now - executionState.lastJumpAtMs <= JUMP_RECOVERY_GRACE_MS;
        }
    }

    private boolean canRepairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target) {
        if (world == null || playerFootPos == null || target == null) {
            return false;
        }
        synchronized (this) {
            BlockPos committedGoal = committedPathGoalLocked(target);
            return !navigationState.currentPath.isEmpty()
                && navigationState.pathIndex >= 0
                && navigationState.pathIndex < navigationState.currentPath.size()
                && isPathGoalStillValid(navigationState.currentPath, committedGoal)
                && (isPlayerNearPath(playerFootPos) || isPlayerNearCommittedPathStart(playerFootPos));
        }
    }

    private boolean shouldKeepCommittedPath(
        Level world,
        BlockPos playerFootPos,
        BlockPos target,
        List<BlockPos> candidatePath,
        List<PlannedPrimitive> candidatePlan,
        long now
    ) {
        if (world == null || playerFootPos == null || target == null) {
            setReplaceDecision("replace:invalid_context");
            return false;
        }
        synchronized (this) {
            BlockPos committedGoal = committedPathGoalLocked(target);
            if (navigationState.currentPath.isEmpty() || navigationState.activeWaypoint == null) {
                lastReplaceDecision = "replace:no_committed_path";
                return false;
            }
            boolean nearCommittedRoute = isPlayerNearPath(playerFootPos) || isPlayerNearCommittedPathStart(playerFootPos);
            if (!isPathGoalStillValid(navigationState.currentPath, committedGoal) || !nearCommittedRoute) {
                lastReplaceDecision = !isPathGoalStillValid(navigationState.currentPath, committedGoal)
                    ? "replace:committed_goal_invalid"
                    : "replace:not_near_committed_route";
                return false;
            }
            if (!isWaypointActionable(world, navigationState.activeWaypoint)) {
                lastReplaceDecision = "replace:active_waypoint_not_actionable";
                return false;
            }
            if (candidatePath == null || candidatePath.isEmpty() || candidatePlan == null || candidatePlan.isEmpty()) {
                lastReplaceDecision = "keep:no_candidate";
                return true;
            }
            if (!pathPlanner.isViablePlannedPath(world, candidatePath, candidatePlan)) {
                lastReplaceDecision = "keep:candidate_not_viable";
                return true;
            }
            if (hasEquivalentOpeningPrefix(navigationState.currentPath, navigationState.pathIndex, candidatePath, playerFootPos, 4)) {
                lastReplaceDecision = "keep:equivalent_opening_prefix";
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
                lastReplaceDecision = "keep:equivalent_active_opening";
                return true;
            }
            if (extendingPartialRoute) {
                lastReplaceDecision = "replace:extend_partial_route";
                return false;
            }
            if (candidateGoalDistance >= currentGoalDistance + 0.75D) {
                lastReplaceDecision = "keep:candidate_farther_goal";
                return true;
            }
            if (isJumpExecutionLocked(now, executionState.activePlannedPrimitive)) {
                lastReplaceDecision = "keep:jump_locked";
                return true;
            }
            if (isRouteStabilizingLocked(playerFootPos, now)) {
                lastReplaceDecision = "keep:route_stabilizing";
                return true;
            }
            if (now < navigationState.routeCommitUntilMs) {
                lastReplaceDecision = "keep:commit_window";
                return true;
            }
            if (hasCriticalPrimitiveAheadLocked(navigationState.currentPlan, navigationState.pathIndex, 6)
                && !hasCriticalPrimitive(candidatePlan, 0, 6)) {
                lastReplaceDecision = "keep:critical_primitive_ahead";
                return true;
            }
            double currentPenalty = pathPlanner.pathStructurePenalty(navigationState.currentPath, navigationState.currentPlan) + pathPlanner.pathModificationPenalty(navigationState.currentPlan);
            double candidatePenalty = pathPlanner.pathStructurePenalty(candidatePath, candidatePlan) + pathPlanner.pathModificationPenalty(candidatePlan);
            if (candidatePenalty >= currentPenalty - 8.0D
                && candidatePath.size() >= navigationState.currentPath.size() - 2) {
                lastReplaceDecision = "keep:candidate_not_materially_better";
                return true;
            }
            if (candidatePenalty > currentPenalty + 12.0D) {
                lastReplaceDecision = "keep:candidate_penalty_worse";
                return true;
            }
            boolean keep = candidatePath.size() >= navigationState.currentPath.size() + 4 && candidatePenalty >= currentPenalty;
            lastReplaceDecision = keep ? "keep:candidate_longer_without_better_penalty" : "replace:candidate_better";
            return keep;
        }
    }

    private boolean hasEquivalentOpeningPrefix(
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

    private boolean hasEquivalentActiveOpening(BlockPos activeWaypoint, List<BlockPos> candidatePath) {
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

    private boolean isMeaningfulPartialRouteExtension(
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

    private double goalDistanceScore(BlockPos pos, BlockPos goal) {
        if (pos == null || goal == null) {
            return Double.POSITIVE_INFINITY;
        }
        return pathPlanner.horizontalDistanceSq(pos, goal) + Math.abs(pos.getY() - goal.getY()) * 1.5D;
    }

    private ControllerMode updateControllerMode(
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
        synchronized (this) {
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

    private BlockPos selectMiningControllerTarget(
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
        synchronized (this) {
            if (executionState.activeBreakTarget != null
                && pathPlanner.isBreakableForNavigator(world, executionState.activeBreakTarget)
                && primitiveExecutor.canBreakTargetNow(world, player, executionState.activeBreakTarget)) {
                return executionState.activeBreakTarget.immutable();
            }
        }
        BlockPos breakTarget = primitiveExecutor.selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
        return breakTarget != null ? breakTarget.immutable() : waypoint.immutable();
    }

    private void noteControllerProgress(long now, double distanceSq) {
        synchronized (this) {
            if (distanceSq < executionState.controllerBestDistanceSq) {
                executionState.controllerBestDistanceSq = distanceSq;
                executionState.controllerProgressAtMs = now;
            }
        }
    }

    private double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget) {
        if (player == null) {
            return Double.POSITIVE_INFINITY;
        }
        BlockPos target;
        synchronized (this) {
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

    private void noteControllerActivity(long now) {
        synchronized (this) {
            executionState.controllerProgressAtMs = now;
        }
    }

    private void noteRouteProgress(long now) {
        synchronized (this) {
            int routeProgress = routeProgressScoreLocked();
            if (routeProgress > navigationState.bestRouteProgressScore) {
                navigationState.bestRouteProgressScore = routeProgress;
                navigationState.lastProgressAtMs = now;
                executionState.controllerProgressAtMs = now;
            }
        }
    }

    private boolean hasCriticalPrimitiveAheadLocked(List<PlannedPrimitive> plan, int startIndex, int lookahead) {
        return hasCriticalPrimitive(plan, startIndex, lookahead);
    }

    private boolean hasCriticalPrimitive(List<PlannedPrimitive> plan, int startIndex, int lookahead) {
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

    private boolean isRouteStabilizingLocked(BlockPos playerFootPos, long now) {
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

    private void updateFollowSegment(FollowSegmentType type, BlockPos target, double segmentDistanceSq, long now) {
        synchronized (this) {
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

    private long followSegmentIdleMs(long now) {
        synchronized (this) {
            return now - executionState.activeFollowSegmentProgressAtMs;
        }
    }

    private int routeProgressScoreLocked() {
        int waypointProgress = Math.max(0, navigationState.pathIndex) * 100;
        int breakPenalty = executionState.plannedBreakTargets == null ? 0 : executionState.plannedBreakTargets.size() * 7;
        int escapePenalty = executionState.committedEscape.breakTargets().size() * 5
            + executionState.committedEscape.route().size() * 3;
        return waypointProgress - breakPenalty - escapePenalty;
    }

    private boolean shouldRedirectController(long now, double distanceSq) {
        synchronized (this) {
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

    private ControllerMode selectControllerMode(
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
        boolean committedEscape = primitiveExecutor.isCommittedEscapeState(now);
        if (isRecoveryState(world, playerFootPos, now)) {
            return executionState.controllerMode;
        }
        if (isCommittedPillarState(world, playerFootPos, now) && (primitiveExecutor.isPillarPrimitive(plannedPrimitive) || committedEscape)) {
            return ControllerMode.PILLAR;
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
            || (allowBlockPlacing && primitiveExecutor.primitiveStillRequiresPlace(world, plannedPrimitive))) {
            return ControllerMode.BREAK_BLOCK;
        }
        return ControllerMode.FOLLOW_PATH;
    }

    private boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        return world != null && playerFootPos != null && waypoint != null && now >= 0L && primitiveExecutor.isPillarPrimitive(plannedPrimitive);
    }

    private ControllerMode recoveryModeForPrimitive(PlannedPrimitive plannedPrimitive, Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
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

    private boolean isCommittedPillarState(Level world, BlockPos playerFootPos, long now) {
        synchronized (this) {
            if (executionState.controllerMode != ControllerMode.PILLAR || executionState.controllerTarget == null || now > executionState.controllerUntilMs) {
                return false;
            }
            BlockPos pillarTarget = executionState.controllerTarget;
            BlockPos pillarBase = pillarTarget.below();
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

    private boolean isRecoveryState(Level world, BlockPos playerFootPos, long now) {
        synchronized (this) {
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

    private boolean shouldEnterEscapeRecovery(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
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

    private void clearStaleEscapeRecoveryIfNeeded(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return;
        }
        synchronized (this) {
            if (!hasCommittedEscapeWorkLocked(now)) {
                return;
            }
        }
        if (shouldEnterEscapeRecovery(world, playerFootPos, waypoint, plannedPrimitive, now)) {
            return;
        }
        synchronized (this) {
            if (isActiveEscapeBreakTargetLocked()) {
                executionState.activeBreakTarget = null;
            }
        }
        primitiveExecutor.clearExcavationPlan(now, "escape cleared", "resume route");
    }

    private void repairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target, long now, String replanReason, String stuckReason) {
        synchronized (this) {
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

    private boolean shouldInvalidateCommittedPrimitive(
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
        synchronized (this) {
            return now - navigationState.lastProgressAtMs >= 900L;
        }
    }

    private void recoverFromStuck(
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
        synchronized (this) {
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
            synchronized (this) {
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
            synchronized (this) {
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
            PathComputation recovery = findPath(world, playerFootPos, target);
            if (!recovery.path().isEmpty()
                && pathPlanner.isViablePlannedPath(world, recovery.path(), recovery.plannedPrimitives())
                && !shouldKeepCommittedPath(world, playerFootPos, target, recovery.path(), recovery.plannedPrimitives(), now)) {
                synchronized (this) {
                    navigationState.currentPath = recovery.path();
                    navigationState.candidatePaths = recovery.candidatePaths();
                    navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                    goalMode = shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                        ? recovery.goalMode()
                        : GoalMode.EXACT;
                    navigationState.resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                    navigationState.committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
                    committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                    navigationState.pathIndex = chooseInitialPathIndex(navigationState.currentPath, playerFootPos, target);
                    navigationState.lastWaypointAdvanceAtMs = now;
                    navigationState.furthestVisitedPathIndex = Math.max(-1, navigationState.pathIndex - 1);
                    navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
                    navigationState.currentPlan = recovery.plannedPrimitives();
                    executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
                    appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
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
                synchronized (this) {
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

    private boolean shouldAttemptLocalRecovery(BlockPos playerFootPos, BlockPos target, long now) {
        synchronized (this) {
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

    private boolean activeWaypointRequiresCommittedAction() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || navigationState.activeWaypoint == null) {
            return false;
        }
        Level world = client.level;
        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
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

    private void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint) {
        synchronized (this) {
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

    private void chooseRecoveryPathIndexLocked(Level world, BlockPos playerFootPos, BlockPos target) {
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

    private void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now, String replanReason, String stuckReason) {
        rememberFailedRedirectWindow(playerFootPos, waypoint, now);
        synchronized (this) {
            navigationState.currentPath = List.of();
            navigationState.currentPlan = List.of();
            navigationState.candidatePaths = List.of();
            navigationState.candidatePathsVisibleUntilMs = 0L;
            navigationState.activeWaypoint = null;
            committedPathStartPos = null;
            navigationState.committedPathGoalPos = null;
            committedPathStartPos = null;
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

    private void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) {
        pathPlanner.rememberFailedMove(playerFootPos, waypoint, now);
        synchronized (this) {
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

    private BlockPos chooseActiveWaypoint(ClientLevel world, LocalPlayer player, BlockPos playerFootPos) {
        if (player == null) {
            return null;
        }
        synchronized (this) {
            if (executionState.committedJumpWaypoint != null && executionState.committedJumpUntilMs > System.currentTimeMillis() && navigationState.activeWaypoint != null) {
                if (executionState.plannedBreakTargets.isEmpty()) {
                    executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, Math.max(0, navigationState.pathIndex));
                }
                return navigationState.activeWaypoint;
            }
            if (executionState.controllerMode == ControllerMode.PILLAR
                && executionState.controllerTarget != null
                && (primitiveExecutor.isPillarPrimitive(executionState.activePlannedPrimitive) || !executionState.committedEscape.isEmpty())) {
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
        BlockPos current = advanceWaypointIfNeeded(player, playerFootPos);
        if (current == null) {
            return null;
        }
        synchronized (this) {
            navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
            if (executionState.plannedBreakTargets.isEmpty()) {
                executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            }
            executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
            chooseForwardResyncIndexLocked(world, playerFootPos);
            navigationState.activeWaypoint = navigationState.currentPath.get(navigationState.pathIndex);
            executionState.plannedBreakTargets = buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
            BlockPos committedGoal = navigationState.committedPathGoalPos != null ? navigationState.committedPathGoalPos : targetPos;
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
                    navigationState.lastAdvanceDecision = "resync:lower_actionable_step navigationState.pathIndex=" + navigationState.pathIndex + " waypoint=" + formatDebugPos(navigationState.activeWaypoint);
                }
            }
            executionState.activePlannedPrimitive = normalizeActivePrimitiveLocked(world, playerFootPos, navigationState.activeWaypoint, executionState.activePlannedPrimitive);
            return navigationState.activeWaypoint;
        }
    }

    private PlannedPrimitive normalizeActivePrimitiveLocked(
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

    private BlockPos advanceWaypointIfNeeded(LocalPlayer player, BlockPos playerFootPos) {
        if (player == null || playerFootPos == null) {
            setAdvanceDecision("hold:missing_player");
            return null;
        }
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        synchronized (this) {
            long now = System.currentTimeMillis();
            int reachedIndex = findReachedPathIndexLocked(playerFootPos, playerPos);
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
                    navigationState.lastAdvanceDecision = "hold:navigationState.pathIndex=" + navigationState.pathIndex + " waypoint=" + formatDebugPos(waypoint);
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

    private int findReachedPathIndexLocked(BlockPos playerFootPos, Vec3 playerPos) {
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

    private void chooseForwardResyncIndexLocked(Level world, BlockPos playerFootPos) {
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
            navigationState.lastAdvanceDecision = "resync:forward_index=" + bestIndex + " waypoint=" + formatDebugPos(navigationState.currentPath.get(bestIndex));
            appendDebugEventLocked(
                "navigationState.pathIndex " + previousIndex + " -> " + navigationState.pathIndex
                    + " reason=forward_resync oldWaypoint=" + formatDebugPos(previousIndex >= 0 && previousIndex < navigationState.currentPath.size() ? navigationState.currentPath.get(previousIndex) : null)
                    + " newWaypoint=" + formatDebugPos(navigationState.currentPath.get(navigationState.pathIndex))
                    + " player=" + formatDebugPos(playerFootPos)
                    + " currentDistanceSq=" + String.format(java.util.Locale.ROOT, "%.2f", currentDistance)
                    + " bestScore=" + String.format(java.util.Locale.ROOT, "%.2f", bestScore)
            );
        }
    }

    private boolean shouldAdvancePastWaypoint(Vec3 playerPos, BlockPos playerFootPos, BlockPos waypoint, Vec3 waypointCenter) {
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
        synchronized (this) {
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

    private boolean hasStableFootingOnWaypoint(Vec3 playerPos, BlockPos waypoint) {
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

    private int chooseInitialPathIndex(List<BlockPos> path, BlockPos playerFootPos, BlockPos target) {
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

    private boolean isPlayerNearCommittedPathStart(BlockPos playerFootPos) {
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

    private List<BlockPos> buildPathBreakPlan(Level world, List<BlockPos> path, int startIndex) {
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

    private List<PlannedPrimitive> buildPlannedPrimitives(Level world, List<BlockPos> path, BlockPos startPos) {
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
            BlockPos placeTarget = pathPlanner.needsPlacedSupport(world, target) && pathPlanner.canPlaceSupportAt(world, target.below())
                ? target.below().immutable()
                : null;
            plan.add(createPlannedPrimitive(world, previous, target, breakTargets, placeTarget));
        }
        return List.copyOf(plan);
    }

    private PlannedPrimitive createPlannedPrimitive(
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

    private PlannedPrimitive createPrimitiveSnapshot(
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

    private SearchPrimitiveType classifySearchPrimitiveType(
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

    private PlannedPrimitiveType classifyExecutionPrimitiveType(
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

    private PrimitiveTraversal classifyPrimitiveTraversal(
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

    private PrimitiveExecution classifyPrimitiveExecution(
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

    private PlannedPrimitive getPlannedPrimitiveAtIndexLocked(int index) {
        if (index < 0 || index >= navigationState.currentPlan.size()) {
            return null;
        }
        return navigationState.currentPlan.get(index);
    }

    private void rebuildCurrentPlanLocked(Level world) {
        navigationState.currentPlan = buildPlannedPrimitives(world, navigationState.currentPath, committedPathStartPos);
        executionState.activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
        if (!navigationState.currentPlan.isEmpty()) {
            appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(navigationState.currentPlan, 8));
            appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(navigationState.currentPlan, 24));
        }
        if (!navigationState.currentPath.isEmpty()) {
            appendDebugEventLocked("pathDetailed=" + formatIndexedPath(navigationState.currentPath, 24));
        }
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
