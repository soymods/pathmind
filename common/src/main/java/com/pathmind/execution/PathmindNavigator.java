package com.pathmind.execution;

import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.PlayerInventoryBridge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bounded Pathmind-owned movement backend used when Baritone is unavailable.
 * This currently implements local walkable-space pathfinding for goto-style movement and stop/cancel.
 */
public final class PathmindNavigator {
    private static final PathmindNavigator INSTANCE = new PathmindNavigator();
    private static final double WAYPOINT_REACHED_DISTANCE_SQ = 0.64D;
    private static final double WAYPOINT_NEAR_DISTANCE_SQ = 0.90D;
    private static final double WAYPOINT_SAFE_EDGE_INSET = 0.24D;
    private static final float TURN_IN_PLACE_YAW_DEGREES = 52.0F;
    private static final float SPRINT_ALIGNMENT_DEGREES = 12.0F;
    private static final float MAX_YAW_STEP = 14.0F;
    private static final float NEOFORGE_MAX_YAW_STEP = 8.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final double DEFAULT_BLOCK_INTERACTION_REACH = 4.5D;
    private static final double BREAK_AIM_EPSILON = 0.001D;
    private static final int BLOCK_INTERACTION_APPROACH_RADIUS = 4;
    private static final int BLOCK_INTERACTION_APPROACH_UP = 2;
    private static final int BLOCK_INTERACTION_APPROACH_DOWN = 4;
    private static final float JUMP_YAW_ALIGNMENT_DEGREES = 18.0F;
    private static final long STUCK_TIMEOUT_MS = 1500L;
    private static final long REPLAN_COOLDOWN_MS = 450L;
    private static final long JUMP_RETRY_COOLDOWN_MS = 250L;
    private static final long JUMP_COMMIT_WINDOW_MS = 1250L;
    private static final long JUMP_RECOVERY_GRACE_MS = 700L;
    private static final long BREAK_COMMIT_WINDOW_MS = 1800L;
    private static final long DROP_COMMIT_WINDOW_MS = 1500L;
    private static final long TRAPPED_RECOVERY_COMMIT_MS = 10000L;
    private static final long NO_MOVEMENT_REPLAN_MS = 900L;
    private static final long STANDSTILL_REDIRECT_MS = 1600L;
    private static final long WALL_PUSH_REDIRECT_MS = 700L;
    private static final long DISTANCE_STALL_REDIRECT_MS = 2500L;
    private static final double DISTANCE_STALL_THRESHOLD = 2.0D;
    private static final long ROUTE_COMMIT_MS = 8000L;
    private static final long ROUTE_STABILIZATION_MS = 1800L;
    private static final long LOCAL_RECOVERY_COOLDOWN_MS = 550L;
    private static final int MAX_LOCAL_RECOVERY_ATTEMPTS = 2;
    private static final long PATH_DECISION_VISIBILITY_MS = 1400L;
    private static final long WAYPOINT_ACQUIRE_SETTLE_MS = 300L;
    private static final double PROGRESS_EPSILON_SQ = 0.01D;
    private static final double MOVEMENT_EPSILON_SQ = 0.0025D;
    private static final double COUNTERMOVEMENT_DISTANCE = 0.9D;
    private static final double COUNTERMOVEMENT_SPEED = 0.16D;
    private static final double COUNTERMOVEMENT_LATERAL_SPEED = 0.08D;
    private static final double COUNTERMOVEMENT_PREDICTION_TICKS = 4.0D;
    private static final double AIR_COUNTERMOVEMENT_DISTANCE = 1.2D;
    private static final float COUNTERMOVEMENT_MAX_YAW_ERROR_DEGREES = 18.0F;
    private static final double COUNTERMOVEMENT_MIN_DISTANCE = 0.22D;
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
    private long lastProgressAtMs;
    private long lastPlanAtMs;
    private long lastJumpAtMs;
    private long lastMiningJumpGateLogAtMs;
    private long lastMiningResumeLogAtMs;
    private double bestDistanceSq = Double.MAX_VALUE;
    private GoalMode goalMode = GoalMode.EXACT;
    private WaterMode waterMode = WaterMode.NORMAL;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacing = true;
    private boolean eventLoggingEnabled = !LoaderMetadata.isNeoForge();
    private BlockPos resolvedGoalPos;
    private BlockPos committedPathGoalPos;
    private BlockPos committedPathStartPos;
    private List<BlockPos> currentPath = List.of();
    private List<PlannedPrimitive> currentPlan = List.of();
    private List<List<BlockPos>> candidatePaths = List.of();
    private long candidatePathsVisibleUntilMs;
    private long lastWaypointAdvanceAtMs;
    private int pathIndex;
    private int furthestVisitedPathIndex;
    private BlockPos activeWaypoint;
    private BlockPos committedJumpWaypoint;
    private long committedJumpUntilMs;
    private BlockPos lastJumpAttemptWaypoint;
    private int repeatedJumpAttempts;
    private long lastInteractAtMs;
    private BlockPos activeBreakTarget;
    private MiningAscentPhase activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
    private PillarPhase activePillarPhase = PillarPhase.CENTER;
    private List<BlockPos> plannedBreakTargets = List.of();
    private BlockPos committedEscapeTarget;
    private long committedEscapeUntilMs;
    private EscapePlan committedEscape = EscapePlan.empty();
    private int committedEscapePrimitiveIndex;
    private ControllerMode controllerMode = ControllerMode.FOLLOW_PATH;
    private BlockPos controllerTarget;
    private long controllerUntilMs;
    private long controllerEnteredAtMs;
    private long controllerProgressAtMs;
    private double controllerBestDistanceSq = Double.POSITIVE_INFINITY;
    private BlockPos lastPlaceTarget;
    private String lastPlaceResult = "none";
    private long routeCommitUntilMs;
    private long lastLocalRecoveryAtMs;
    private int localRecoveryAttempts;
    private int bestRouteProgressScore = Integer.MIN_VALUE;
    private int consecutivePlanningBudgetExhaustions;
    private FollowSegmentType activeFollowSegment = FollowSegmentType.GROUND;
    private BlockPos activeFollowSegmentTarget;
    private PlannedPrimitive activePlannedPrimitive;
    private long activeFollowSegmentEnteredAtMs;
    private long activeFollowSegmentProgressAtMs;
    private double activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
    private Vec3 lastMovementSamplePos = Vec3.ZERO;
    private long lastMovementAtMs;
    private double lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
    private long lastDistanceCheckpointAtMs;
    private volatile Snapshot renderSnapshot;
    private final PathmindPathPlanner pathPlanner = new PathmindPathPlanner(new PlannerHost());
    private String lastReplanReason = "none";
    private String lastStuckReason = "none";
    private String previousControllerMode = "none";
    private String previousPrimitiveLabel = "none";
    private String previousMiningAscentPhase = MiningAscentPhase.CLEARANCE.name();
    private String previousPillarPhase = PillarPhase.CENTER.name();
    private BlockPos previousActiveWaypoint;
    private String previousReplanReason = "none";
    private String previousStuckReason = "none";
    private String lastReplanDecision = "none";
    private String lastAdvanceDecision = "none";
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

    private enum ControllerMode {
        FOLLOW_PATH,
        RECOVER_JUMP,
        RECOVER_BREAK,
        RECOVER_PILLAR,
        RECOVER_ESCAPE,
        BREAK_BLOCK,
        PILLAR,
        COMMIT_JUMP,
        DROP,
        ESCAPE_HOLE
    }

    private enum FollowSegmentType {
        GROUND,
        CLIMB,
        DROP
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
            return PathmindNavigator.this.requiresBreakingForWaypoint(world, waypoint);
        }

        @Override
        public PlannedPrimitive createPlannedPrimitive(Level world, BlockPos from, BlockPos to,
                                                       List<BlockPos> breakTargets, BlockPos placeTarget) {
            return PathmindNavigator.this.createPlannedPrimitive(world, from, to, breakTargets, placeTarget);
        }

        @Override
        public Direction preferredEscapeDirection(Level world, BlockPos current, BlockPos goal, long now) {
            synchronized (PathmindNavigator.this) {
                if (!committedEscape.isEmpty()) {
                    return committedEscape.direction();
                }
            }
            return chooseEscapeDirection(world, current, goal, now);
        }

        @Override
        public boolean isDirectGoalCompletionCandidate(BlockPos candidate, BlockPos target) {
            return PathmindNavigator.this.isDirectGoalCompletionCandidate(candidate, target);
        }

        @Override
        public boolean isJumpPrimitive(PlannedPrimitive primitive) {
            return PathmindNavigator.this.isJumpPrimitive(primitive);
        }

        @Override
        public PathmindPathPlanner.SteeringLookahead steeringLookahead(BlockPos activeWaypoint) {
            synchronized (PathmindNavigator.this) {
                if (currentPath.isEmpty() || pathIndex < 0 || pathIndex + 1 >= currentPath.size()) {
                    return null;
                }
                BlockPos activePathWaypoint = currentPath.get(pathIndex);
                if (activePathWaypoint == null || !activePathWaypoint.equals(activeWaypoint)) {
                    return null;
                }
                return new PathmindPathPlanner.SteeringLookahead(
                    currentPath.get(pathIndex + 1),
                    getPlannedPrimitiveAtIndexLocked(pathIndex + 1)
                );
            }
        }
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
        this.lastProgressAtMs = this.startedAtMs;
        this.lastPlanAtMs = 0L;
        this.lastJumpAtMs = 0L;
        this.lastMiningJumpGateLogAtMs = 0L;
        this.lastMiningResumeLogAtMs = 0L;
        this.bestDistanceSq = Double.MAX_VALUE;
        this.goalMode = GoalMode.EXACT;
        this.resolvedGoalPos = targetPos.immutable();
        this.committedPathGoalPos = this.resolvedGoalPos;
        this.currentPath = List.of();
        this.currentPlan = List.of();
        this.candidatePaths = List.of();
        this.candidatePathsVisibleUntilMs = 0L;
        this.lastWaypointAdvanceAtMs = this.startedAtMs;
        this.pathIndex = 0;
        this.furthestVisitedPathIndex = 0;
        this.activeWaypoint = null;
        this.committedJumpWaypoint = null;
        this.committedJumpUntilMs = 0L;
        this.lastJumpAttemptWaypoint = null;
        this.repeatedJumpAttempts = 0;
        this.lastInteractAtMs = 0L;
        this.activeBreakTarget = null;
        this.plannedBreakTargets = List.of();
        this.committedEscapeTarget = null;
        this.committedEscapeUntilMs = 0L;
        this.committedEscape = EscapePlan.empty();
        this.committedEscapePrimitiveIndex = 0;
        this.controllerMode = ControllerMode.FOLLOW_PATH;
        this.controllerTarget = null;
        this.controllerUntilMs = 0L;
        this.controllerEnteredAtMs = this.startedAtMs;
        this.controllerProgressAtMs = this.startedAtMs;
        this.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        this.lastPlaceTarget = null;
        this.lastPlaceResult = "none";
        this.routeCommitUntilMs = this.startedAtMs + ROUTE_COMMIT_MS;
        this.lastLocalRecoveryAtMs = 0L;
        this.localRecoveryAttempts = 0;
        this.bestRouteProgressScore = Integer.MIN_VALUE;
        this.consecutivePlanningBudgetExhaustions = 0;
        this.activeFollowSegment = FollowSegmentType.GROUND;
        this.activeFollowSegmentTarget = null;
        this.activePlannedPrimitive = null;
        this.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        this.activeFollowSegmentProgressAtMs = this.startedAtMs;
        this.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        LocalPlayer player = Minecraft.getInstance() != null ? Minecraft.getInstance().player : null;
        Vec3 startingPosition = player != null ? player.position() : Vec3.atCenterOf(this.targetPos);
        this.lastMovementSamplePos = startingPosition;
        this.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = startingPosition.distanceTo(Vec3.atCenterOf(this.targetPos));
        this.lastDistanceCheckpointAtMs = this.startedAtMs;
        this.pathPlanner.clearFailureMemory();
        this.lastReplanReason = "start goto";
        this.lastStuckReason = "none";
        this.previousControllerMode = this.controllerMode.name();
        this.previousPrimitiveLabel = "none";
        this.previousMiningAscentPhase = this.activeMiningAscentPhase.name();
        this.previousPillarPhase = this.activePillarPhase.name();
        this.previousActiveWaypoint = null;
        this.previousReplanReason = this.lastReplanReason;
        this.previousStuckReason = this.lastStuckReason;
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

        double reachSq = blockInteractionReachSquared(client.player);
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

        double reachSq = blockInteractionReachSquared(client.player);
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
                        if (!isBlockShapeWithinReachFromFoot(world, player, candidate, targetBlockPos, reachSq)) {
                            continue;
                        }
                        if (requireLineOfSight && !canInteractWithBlockFromFoot(world, player, candidate, targetBlockPos, reachSq)) {
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
        int snapshotStart = currentPath.isEmpty()
            ? 0
            : Math.max(0, Math.min(furthestVisitedPathIndex, currentPath.size() - 1));
        List<BlockPos> pathCopy = currentPath.isEmpty()
            ? List.of()
            : copyPathWindow(currentPath, snapshotStart, MAX_SNAPSHOT_PATH_POINTS);
        boolean showCandidatePaths = state == State.PREVIEW || System.currentTimeMillis() <= candidatePathsVisibleUntilMs;
        List<List<BlockPos>> candidateCopies = !showCandidatePaths || candidatePaths.isEmpty()
            ? List.of()
            : candidatePaths.stream()
                .map(path -> copyPathWindow(path, 0, MAX_SNAPSHOT_CANDIDATE_POINTS))
                .toList();
        List<BlockPos> breakTargets = List.of();
        List<BlockPos> placeTargets = List.of();
        if (client != null && client.level != null) {
            if (!committedEscape.breakTargets().isEmpty()) {
                breakTargets = committedEscape.breakTargets().stream()
                    .filter(pos -> pos != null && !pathPlanner.canOccupy(client.level, pos))
                    .toList();
            } else if (!plannedBreakTargets.isEmpty()) {
                breakTargets = plannedBreakTargets.stream()
                    .filter(pos -> pos != null && pathPlanner.isBreakableForNavigator(client.level, pos))
                    .toList();
            } else if (activeWaypoint != null) {
                List<BlockPos> requiredBreakTargets = pathPlanner.getRequiredBreakTargets(client.level, activeWaypoint);
                if (requiredBreakTargets != null && !requiredBreakTargets.isEmpty()) {
                    breakTargets = List.copyOf(requiredBreakTargets);
                } else if (activeBreakTarget != null) {
                    breakTargets = List.of(activeBreakTarget);
                }
            }
            if (controllerMode == ControllerMode.PILLAR && controllerTarget != null) {
                placeTargets = List.of(controllerTarget.below().immutable());
            } else if (activePlannedPrimitive != null && activePlannedPrimitive.placeTarget() != null) {
                placeTargets = List.of(activePlannedPrimitive.placeTarget().immutable());
            }
        }
        BlockPos resolvedGoal = resolvedGoalPos != null ? resolvedGoalPos : (pathCopy.isEmpty() ? targetPos : pathCopy.get(pathCopy.size() - 1));
        return new Snapshot(
            isActive(),
            state,
            targetPos,
            resolvedGoal,
            activeWaypoint,
            Math.max(0, pathIndex - snapshotStart),
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
            controllerMode.name(),
            previousControllerMode,
            formatPlannedPrimitive(activePlannedPrimitive),
            activeMiningAscentPhase.name(),
            activePillarPhase.name(),
            goalMode.name(),
            waterMode.name(),
            allowBlockBreaking,
            allowBlockPlacing,
            eventLoggingEnabled,
            targetPos,
            resolvedGoalPos,
            activeWaypoint,
            previousActiveWaypoint,
            controllerTarget,
            lastPlaceTarget,
            pathIndex,
            currentPath.size(),
            lastPlaceResult,
            lastReplanReason,
            previousReplanReason,
            lastReplanDecision,
            lastAdvanceDecision,
            lastReplaceDecision,
            lastStuckReason,
            previousStuckReason,
            List.copyOf(debugEvents)
        );
    }

    private void recordDebugTransitions(long now) {
        synchronized (this) {
            boolean changed = false;
            String controller = controllerMode != null ? controllerMode.name() : "none";
            String primitive = formatPlannedPrimitive(activePlannedPrimitive);
            String miningPhase = activeMiningAscentPhase != null ? activeMiningAscentPhase.name() : "none";
            String pillarPhase = activePillarPhase != null ? activePillarPhase.name() : "none";
            String replan = lastReplanReason == null ? "none" : lastReplanReason;
            String stuck = lastStuckReason == null ? "none" : lastStuckReason;
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
            if (!java.util.Objects.equals(activeWaypoint, previousActiveWaypoint)) {
                appendDebugEventLocked("waypoint " + formatDebugPos(previousActiveWaypoint) + " -> " + formatDebugPos(activeWaypoint));
                previousActiveWaypoint = activeWaypoint != null ? activeWaypoint.immutable() : null;
                appendDebugEventLocked("primitive=" + formatPlannedPrimitive(activePlannedPrimitive));
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
            if (lastPlaceResult != null && !"none".equals(lastPlaceResult) && (debugEvents.isEmpty() || !debugEvents.peekLast().contains("placeResult=" + lastPlaceResult))) {
                appendDebugEventLocked("placeResult=" + lastPlaceResult + " target=" + formatDebugPos(lastPlaceTarget));
                changed = true;
            }
            if (!changed && eventLoggingEnabled && now - lastDebugHeartbeatAtMs >= DEBUG_HEARTBEAT_INTERVAL_MS) {
                appendDebugEventLocked(
                    "heartbeat controller=" + controller
                        + " waypoint=" + formatDebugPos(activeWaypoint)
                        + " primitive=" + primitive
                        + " miningPhase=" + miningPhase
                        + " pillarPhase=" + pillarPhase
                        + " target=" + formatDebugPos(targetPos)
                        + " replan=" + replan
                        + " replanDecision=" + lastReplanDecision
                        + " advanceDecision=" + lastAdvanceDecision
                        + " replaceDecision=" + lastReplaceDecision
                        + " stuck=" + stuck
                        + " placeResult=" + (lastPlaceResult == null ? "none" : lastPlaceResult)
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
            lastAdvanceDecision = decision == null || decision.isBlank() ? "none" : decision;
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
        this.lastProgressAtMs = this.startedAtMs;
        this.lastPlanAtMs = this.startedAtMs;
        this.lastJumpAtMs = 0L;
        this.goalMode = GoalMode.EXACT;
        this.resolvedGoalPos = this.targetPos;
        this.committedPathGoalPos = this.resolvedGoalPos;
        this.currentPath = List.of();
        this.currentPlan = List.of();
        this.candidatePaths = List.of();
        this.candidatePathsVisibleUntilMs = 0L;
        this.pathIndex = 0;
        this.furthestVisitedPathIndex = 0;
        this.activeWaypoint = null;
        this.committedJumpWaypoint = null;
        this.committedJumpUntilMs = 0L;
        this.lastInteractAtMs = 0L;
        this.activeBreakTarget = null;
        this.plannedBreakTargets = List.of();
        this.committedEscapeTarget = null;
        this.committedEscapeUntilMs = 0L;
        this.committedEscape = EscapePlan.empty();
        this.committedEscapePrimitiveIndex = 0;
        this.controllerMode = ControllerMode.FOLLOW_PATH;
        this.controllerTarget = null;
        this.controllerUntilMs = 0L;
        this.controllerEnteredAtMs = this.startedAtMs;
        this.controllerProgressAtMs = this.startedAtMs;
        this.controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        this.lastPlaceTarget = null;
        this.lastPlaceResult = "none";
        this.routeCommitUntilMs = this.startedAtMs + ROUTE_COMMIT_MS;
        this.lastLocalRecoveryAtMs = 0L;
        this.localRecoveryAttempts = 0;
        this.bestRouteProgressScore = Integer.MIN_VALUE;
        this.consecutivePlanningBudgetExhaustions = 0;
        this.activeFollowSegment = FollowSegmentType.GROUND;
        this.activeFollowSegmentTarget = null;
        this.activePlannedPrimitive = null;
        this.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        this.activeFollowSegmentProgressAtMs = this.startedAtMs;
        this.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        this.lastMovementSamplePos = Vec3.atCenterOf(this.targetPos);
        this.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        this.lastDistanceCheckpointAtMs = this.startedAtMs;
        this.lastReplanReason = "preview";
        this.lastStuckReason = "none";
        this.previousPrimitiveLabel = "none";
        this.previousMiningAscentPhase = this.activeMiningAscentPhase.name();
        this.previousPillarPhase = this.activePillarPhase.name();
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

        this.currentPath = computation.path();
        this.candidatePaths = computation.candidatePaths();
        this.candidatePathsVisibleUntilMs = Long.MAX_VALUE;
        this.goalMode = computation.goalMode();
        this.resolvedGoalPos = computation.resolvedGoalPos();
        this.committedPathGoalPos = this.resolvedGoalPos != null ? this.resolvedGoalPos.immutable() : this.targetPos;
        this.committedPathStartPos = start != null ? start.immutable() : null;
        this.pathIndex = chooseInitialPathIndex(this.currentPath, start, this.targetPos);
        this.lastWaypointAdvanceAtMs = System.currentTimeMillis();
        this.furthestVisitedPathIndex = Math.max(-1, this.pathIndex - 1);
        this.activeWaypoint = this.currentPath.get(this.pathIndex);
        this.plannedBreakTargets = buildPathBreakPlan(client.level, this.currentPath, this.pathIndex);
        this.currentPlan = computation.plannedPrimitives();
        this.activePlannedPrimitive = this.pathIndex < this.currentPlan.size() ? this.currentPlan.get(this.pathIndex) : null;
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
            if (currentPos.distanceToSqr(lastMovementSamplePos) > MOVEMENT_EPSILON_SQ) {
                lastMovementSamplePos = currentPos;
                lastMovementAtMs = now;
            }
            double currentDistance = Math.sqrt(distanceSq);
            if (!Double.isFinite(lastDistanceCheckpoint) || Math.abs(currentDistance - lastDistanceCheckpoint) > DISTANCE_STALL_THRESHOLD) {
                lastDistanceCheckpoint = currentDistance;
                lastDistanceCheckpointAtMs = now;
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
                currentPath = newPath;
                candidatePaths = computation.candidatePaths();
                candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                goalMode = shouldTrackResolvedPlanningGoal(target, computation.resolvedGoalPos(), computation.goalMode())
                    ? computation.goalMode()
                    : GoalMode.EXACT;
                resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? computation.resolvedGoalPos() : target.immutable();
                committedPathGoalPos = computation.resolvedGoalPos() != null ? computation.resolvedGoalPos().immutable() : resolvedGoalPos;
                committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                lastWaypointAdvanceAtMs = now;
                furthestVisitedPathIndex = Math.max(-1, pathIndex - 1);
                activeWaypoint = currentPath.get(pathIndex);
                plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                currentPlan = computation.plannedPrimitives();
                activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
                appendDebugEventLocked("pathDetailed=" + formatIndexedPath(currentPath, 24));
                appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(currentPlan, 24));
                lastPlanAtMs = now;
                bestDistanceSq = distanceSq;
                lastProgressAtMs = now;
                routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                lastLocalRecoveryAtMs = 0L;
                localRecoveryAttempts = 0;
                bestRouteProgressScore = routeProgressScoreLocked();
                lastReplanReason = "planner replan";
            }
            }
        }

        BlockPos waypoint = chooseActiveWaypoint(world, player, playerFootPos);
        if (waypoint == null) {
            releaseMovementKeys(client);
            synchronized (this) {
                if (!currentPath.isEmpty() && now - lastWaypointAdvanceAtMs <= WAYPOINT_ACQUIRE_SETTLE_MS) {
                    lastAdvanceDecision = "hold:settle_for_next_waypoint";
                    return;
                }
            }
            if (pathPlanner.hasReachedExactGoal(playerFootPos, target)) {
                releaseMovementKeys(client);
                complete(State.ARRIVED);
                return;
            }
            synchronized (this) {
                if (now - lastPlanAtMs < REPLAN_COOLDOWN_MS) {
                    lastAdvanceDecision = "hold:recovery_replan_cooldown";
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
                        currentPath = recovery.path();
                        candidatePaths = recovery.candidatePaths();
                        candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                        goalMode = shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                            ? recovery.goalMode()
                            : GoalMode.EXACT;
                        resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                        committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : resolvedGoalPos;
                        committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                        pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                        lastWaypointAdvanceAtMs = now;
                        furthestVisitedPathIndex = Math.max(-1, pathIndex - 1);
                        activeWaypoint = currentPath.get(pathIndex);
                        plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                        currentPlan = recovery.plannedPrimitives();
                        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                        appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
                        appendDebugEventLocked("pathDetailed=" + formatIndexedPath(currentPath, 24));
                        appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(currentPlan, 24));
                        lastPlanAtMs = now;
                        lastProgressAtMs = now;
                        routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                        lastLocalRecoveryAtMs = 0L;
                        localRecoveryAttempts = 0;
                        bestRouteProgressScore = routeProgressScoreLocked();
                        lastReplanReason = "waypoint recovery";
                    }
                }
                waypoint = chooseActiveWaypoint(world, player, playerFootPos);
            }
        }

        if (waypoint == null) {
            releaseMovementKeys(client);
            synchronized (this) {
                lastReplanReason = "waypoint exhausted";
                lastStuckReason = "no active waypoint";
            }
            fail(FailureReason.NO_ROUTE, "No active waypoint remained after replanning.");
            return;
        }

        if (shouldForceFinalApproach(world, playerFootPos, target)) {
            waypoint = target.immutable();
            synchronized (this) {
                activeWaypoint = waypoint;
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
                activePlannedPrimitive = createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
            }
        }

        if (handleDirectFinalApproach(client, world, player, playerFootPos, target, now)) {
            recordDebugTransitions(now);
            return;
        }

        noteRouteProgress(now);

        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }
        clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, plannedPrimitive, now);
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }

        double controllerDistanceSq = distanceToControllerTargetSq(world, player, waypoint);
        ControllerMode activeController = updateControllerMode(world, player, playerFootPos, waypoint, plannedPrimitive, now, controllerDistanceSq);
        recordDebugTransitions(now);
        boolean handledController = false;
        switch (activeController) {
            case RECOVER_JUMP -> handledController = handleJumpRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_BREAK -> handledController = handleBreakRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_PILLAR -> handledController = handlePillarRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case RECOVER_ESCAPE -> handledController = handleEscapeRecoveryMovement(client, world, player, playerFootPos, waypoint, now);
            case ESCAPE_HOLE -> handledController = handleTrappedSpaceRecovery(client, world, player, playerFootPos, waypoint, now);
            case BREAK_BLOCK -> handledController = handleCommittedMiningMovement(client, world, player, playerFootPos, waypoint, target, currentPos, now);
            case PILLAR -> handledController = handlePillaring(client, world, player, playerFootPos, waypoint, now);
            case COMMIT_JUMP -> handledController = handleCommittedJumpMovement(client, world, player, playerFootPos, now);
            case DROP -> handledController = handleCommittedDropMovement(client, world, player, playerFootPos, waypoint, target, currentPos, now);
            case FOLLOW_PATH -> {
            }
        }
        if (handledController) {
            return;
        }
        if ((activeController == ControllerMode.PILLAR || activeController == ControllerMode.ESCAPE_HOLE)
            && isCommittedLocalEscapeChain(now)) {
            synchronized (this) {
                if (activeController == ControllerMode.PILLAR) {
                    controllerMode = ControllerMode.ESCAPE_HOLE;
                    controllerTarget = committedEscapeTarget != null ? committedEscapeTarget : waypoint;
                }
                controllerEnteredAtMs = now;
                controllerProgressAtMs = now;
                controllerBestDistanceSq = distanceSq;
                controllerUntilMs = Math.max(controllerUntilMs, committedEscapeUntilMs);
            }
            return;
        }
        if (activeController == ControllerMode.PILLAR) {
            synchronized (this) {
                controllerMode = ControllerMode.FOLLOW_PATH;
                controllerTarget = null;
                controllerUntilMs = 0L;
                controllerEnteredAtMs = now;
                controllerProgressAtMs = now;
                controllerBestDistanceSq = distanceSq;
            }
            waypoint = chooseActiveWaypoint(world, player, playerFootPos);
            if (waypoint == null) {
                releaseMovementKeys(client);
                synchronized (this) {
                    lastReplanReason = "waypoint exhausted";
                    lastStuckReason = "no active waypoint";
                }
                fail(FailureReason.NO_ROUTE, "Recovery replanning did not produce a usable route.");
                return;
            }
            if (shouldForceFinalApproach(world, playerFootPos, target)) {
                waypoint = target.immutable();
                synchronized (this) {
                    activeWaypoint = waypoint;
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
                    activePlannedPrimitive = createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
                }
            }
        }

        controllerDistanceSq = distanceToControllerTargetSq(world, player, waypoint);
        if (shouldRedirectController(now, controllerDistanceSq)) {
            releaseMovementKeys(client);
            recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "controller redirect", activeController.name().toLowerCase());
            return;
        }
        if (handleFollowPathSegment(client, world, player, playerFootPos, waypoint, plannedPrimitive, target, currentPos, distanceSq, now)) {
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
                + " resolved=" + formatDebugPos(resolvedGoalPos)
                + " goal=" + goalMode.name()
        );
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        currentPath = List.of();
        currentPlan = List.of();
        candidatePaths = List.of();
        candidatePathsVisibleUntilMs = 0L;
        pathIndex = 0;
        furthestVisitedPathIndex = 0;
        activeWaypoint = null;
        plannedBreakTargets = List.of();
        resolvedGoalPos = null;
        committedPathGoalPos = null;
        committedPathStartPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastJumpAttemptWaypoint = null;
        repeatedJumpAttempts = 0;
        lastInteractAtMs = 0L;
        committedEscapeTarget = null;
        committedEscapeUntilMs = 0L;
        committedEscape = EscapePlan.empty();
        committedEscapePrimitiveIndex = 0;
        controllerMode = ControllerMode.FOLLOW_PATH;
        controllerTarget = null;
        controllerUntilMs = 0L;
        controllerEnteredAtMs = 0L;
        controllerProgressAtMs = 0L;
        controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        lastPlaceTarget = null;
        lastPlaceResult = "none";
        routeCommitUntilMs = 0L;
        lastLocalRecoveryAtMs = 0L;
        localRecoveryAttempts = 0;
        bestRouteProgressScore = Integer.MIN_VALUE;
        consecutivePlanningBudgetExhaustions = 0;
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3.ZERO;
        lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        lastDistanceCheckpointAtMs = 0L;
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
                + " resolved=" + formatDebugPos(resolvedGoalPos)
                + " goal=" + goalMode.name()
        );
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        currentPath = List.of();
        currentPlan = List.of();
        candidatePaths = List.of();
        candidatePathsVisibleUntilMs = 0L;
        pathIndex = 0;
        furthestVisitedPathIndex = 0;
        activeWaypoint = null;
        plannedBreakTargets = List.of();
        resolvedGoalPos = null;
        committedPathGoalPos = null;
        committedPathStartPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastJumpAttemptWaypoint = null;
        repeatedJumpAttempts = 0;
        lastInteractAtMs = 0L;
        committedEscapeTarget = null;
        committedEscapeUntilMs = 0L;
        committedEscape = EscapePlan.empty();
        committedEscapePrimitiveIndex = 0;
        controllerMode = ControllerMode.FOLLOW_PATH;
        controllerTarget = null;
        controllerUntilMs = 0L;
        controllerEnteredAtMs = 0L;
        controllerProgressAtMs = 0L;
        controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        lastPlaceTarget = null;
        lastPlaceResult = "none";
        routeCommitUntilMs = 0L;
        lastLocalRecoveryAtMs = 0L;
        localRecoveryAttempts = 0;
        bestRouteProgressScore = Integer.MIN_VALUE;
        consecutivePlanningBudgetExhaustions = 0;
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3.ZERO;
        lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        lastDistanceCheckpointAtMs = 0L;
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
        bestDistanceSq = Double.MAX_VALUE;
        currentPath = List.of();
        currentPlan = List.of();
        candidatePaths = List.of();
        candidatePathsVisibleUntilMs = 0L;
        pathIndex = 0;
        furthestVisitedPathIndex = 0;
        activeWaypoint = null;
        plannedBreakTargets = List.of();
        resolvedGoalPos = null;
        committedPathGoalPos = null;
        committedPathStartPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastJumpAttemptWaypoint = null;
        repeatedJumpAttempts = 0;
        lastInteractAtMs = 0L;
        committedEscapeTarget = null;
        committedEscapeUntilMs = 0L;
        committedEscape = EscapePlan.empty();
        committedEscapePrimitiveIndex = 0;
        controllerMode = ControllerMode.FOLLOW_PATH;
        controllerTarget = null;
        controllerUntilMs = 0L;
        controllerEnteredAtMs = 0L;
        controllerProgressAtMs = 0L;
        controllerBestDistanceSq = Double.POSITIVE_INFINITY;
        lastPlaceTarget = null;
        lastPlaceResult = "none";
        routeCommitUntilMs = 0L;
        lastLocalRecoveryAtMs = 0L;
        localRecoveryAttempts = 0;
        bestRouteProgressScore = Integer.MIN_VALUE;
        consecutivePlanningBudgetExhaustions = 0;
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3.ZERO;
        lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        lastDistanceCheckpointAtMs = 0L;
        long now = System.currentTimeMillis();
        startedAtMs = now;
        lastProgressAtMs = now;
        lastPlanAtMs = 0L;
        lastJumpAtMs = 0L;
        goalMode = GoalMode.EXACT;
        state = completeFuture ? State.STOPPED : State.IDLE;
        if (state == State.STOPPED) {
            state = State.IDLE;
        }
        renderSnapshot = null;
    }

    private boolean shouldReplan(ClientLevel world, BlockPos start, BlockPos target, long now) {
        synchronized (this) {
            if (currentPath.isEmpty() || activeWaypoint == null) {
                if (lastPlanAtMs > 0L && now - lastPlanAtMs < REPLAN_COOLDOWN_MS) {
                    lastReplanDecision = "keep:planning_retry_cooldown";
                    return false;
                }
                lastReplanDecision = "replan:no_active_path";
                return true;
            }
            if (isCommittedLocalEscapeChain(now)) {
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
            if (isJumpExecutionLocked(now, activePlannedPrimitive)) {
                lastReplanDecision = "keep:jump_locked";
                return false;
            }
            boolean committedGoalValid = isPathGoalStillValid(currentPath, committedPathGoalLocked(target));
            boolean routeReachesRequestedTarget = isPathGoalStillValid(currentPath, target);
            boolean nearCommittedRoute = isPlayerNearPath(start) || isPlayerNearCommittedPathStart(start);
            if (!routeReachesRequestedTarget
                && nearCommittedRoute
                && shouldProactivelyRefreshRouteLocked(target, now)) {
                lastReplanDecision = "replan:refresh_partial_route";
                return true;
            }
            if (committedGoalValid && nearCommittedRoute && isWaypointActionable(world, activeWaypoint)) {
                lastReplanDecision = "keep:committed_route_valid";
                return false;
            }
            if (now < routeCommitUntilMs) {
                lastReplanDecision = "keep:commit_window";
                return false;
            }
            if (now - lastProgressAtMs < 2000L) {
                lastReplanDecision = "keep:recent_progress";
                return false;
            }
            if (!isWaypointActionable(world, activeWaypoint)) {
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
        lastPlanAtMs = now;
        lastReplanReason = "planning budget retry " + consecutivePlanningBudgetExhaustions;
        lastStuckReason = "planner time budget";
        appendDebugEventLocked(
            "planner deferred retry=" + consecutivePlanningBudgetExhaustions
                + " detail=" + (detail == null || detail.isBlank() ? "none" : detail)
        );
        return consecutivePlanningBudgetExhaustions <= MAX_PLANNING_BUDGET_RETRIES;
    }

    private boolean shouldProactivelyRefreshRouteLocked(BlockPos target, long now) {
        if (target == null || currentPath.isEmpty() || pathIndex < 0) {
            return false;
        }
        if (now - lastPlanAtMs < REPLAN_COOLDOWN_MS) {
            return false;
        }
        if (pathIndex <= 0 && furthestVisitedPathIndex <= 0) {
            return false;
        }
        int remaining = Math.max(0, currentPath.size() - pathIndex - 1);
        if (remaining > PROACTIVE_REPLAN_LOOKAHEAD_STEPS) {
            return false;
        }
        BlockPos pathEnd = currentPath.get(currentPath.size() - 1);
        return pathEnd == null
            || pathPlanner.horizontalDistanceSq(pathEnd, target) > 4.0D
            || Math.abs(pathEnd.getY() - target.getY()) > MAX_DROP_DOWN;
    }

    private BlockPos committedPathGoalLocked(BlockPos fallbackTarget) {
        return committedPathGoalPos != null ? committedPathGoalPos : fallbackTarget;
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
        if (playerFootPos == null || currentPath.isEmpty()) {
            return false;
        }
        int start = Math.max(0, pathIndex - 2);
        int end = Math.min(currentPath.size() - 1, pathIndex + 6);
        for (int i = start; i <= end; i++) {
            BlockPos step = currentPath.get(i);
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
            boolean activeEscapeController = controllerMode == ControllerMode.ESCAPE_HOLE
                || controllerMode == ControllerMode.RECOVER_ESCAPE
                || controllerMode == ControllerMode.PILLAR
                || controllerMode == ControllerMode.RECOVER_PILLAR;
            return activeEscapeController
                && (hasCommittedEscapeWorkLocked(now) || isActiveEscapeBreakTargetLocked());
        }
    }

    private boolean hasCommittedEscapeWorkLocked(long now) {
        return !committedEscape.isEmpty()
            && committedEscapePrimitiveIndex < committedEscape.primitives().size()
            && committedEscapeUntilMs > now;
    }

    private boolean isActiveEscapeBreakTargetLocked() {
        return activeBreakTarget != null
            && !committedEscape.isEmpty()
            && committedEscape.breakTargets().contains(activeBreakTarget);
    }

    private boolean isJumpExecutionLocked(long now, PlannedPrimitive plannedPrimitive) {
        synchronized (this) {
            if (committedJumpWaypoint != null && committedJumpUntilMs > now) {
                return true;
            }
            return isJumpPrimitive(plannedPrimitive) && now - lastJumpAtMs <= JUMP_RECOVERY_GRACE_MS;
        }
    }

    private boolean canRepairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target) {
        if (world == null || playerFootPos == null || target == null) {
            return false;
        }
        synchronized (this) {
            BlockPos committedGoal = committedPathGoalLocked(target);
            return !currentPath.isEmpty()
                && pathIndex >= 0
                && pathIndex < currentPath.size()
                && isPathGoalStillValid(currentPath, committedGoal)
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
            if (currentPath.isEmpty() || activeWaypoint == null) {
                lastReplaceDecision = "replace:no_committed_path";
                return false;
            }
            boolean nearCommittedRoute = isPlayerNearPath(playerFootPos) || isPlayerNearCommittedPathStart(playerFootPos);
            if (!isPathGoalStillValid(currentPath, committedGoal) || !nearCommittedRoute) {
                lastReplaceDecision = !isPathGoalStillValid(currentPath, committedGoal)
                    ? "replace:committed_goal_invalid"
                    : "replace:not_near_committed_route";
                return false;
            }
            if (!isWaypointActionable(world, activeWaypoint)) {
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
            if (hasEquivalentOpeningPrefix(currentPath, pathIndex, candidatePath, playerFootPos, 4)) {
                lastReplaceDecision = "keep:equivalent_opening_prefix";
                return true;
            }
            BlockPos currentEnd = currentPath.get(currentPath.size() - 1);
            BlockPos candidateEnd = candidatePath.get(candidatePath.size() - 1);
            double currentGoalDistance = goalDistanceScore(currentEnd, committedGoal);
            double candidateGoalDistance = goalDistanceScore(candidateEnd, committedGoal);
            boolean extendingPartialRoute = !isPathGoalStillValid(currentPath, target)
                && isMeaningfulPartialRouteExtension(currentEnd, candidateEnd, target, candidatePath.size());
            if (hasEquivalentActiveOpening(activeWaypoint, candidatePath)
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
            if (isJumpExecutionLocked(now, activePlannedPrimitive)) {
                lastReplaceDecision = "keep:jump_locked";
                return true;
            }
            if (isRouteStabilizingLocked(playerFootPos, now)) {
                lastReplaceDecision = "keep:route_stabilizing";
                return true;
            }
            if (now < routeCommitUntilMs) {
                lastReplaceDecision = "keep:commit_window";
                return true;
            }
            if (hasCriticalPrimitiveAheadLocked(currentPlan, pathIndex, 6)
                && !hasCriticalPrimitive(candidatePlan, 0, 6)) {
                lastReplaceDecision = "keep:critical_primitive_ahead";
                return true;
            }
            double currentPenalty = pathPlanner.pathStructurePenalty(currentPath, currentPlan) + pathPlanner.pathModificationPenalty(currentPlan);
            double candidatePenalty = pathPlanner.pathStructurePenalty(candidatePath, candidatePlan) + pathPlanner.pathModificationPenalty(candidatePlan);
            if (candidatePenalty >= currentPenalty - 8.0D
                && candidatePath.size() >= currentPath.size() - 2) {
                lastReplaceDecision = "keep:candidate_not_materially_better";
                return true;
            }
            if (candidatePenalty > currentPenalty + 12.0D) {
                lastReplaceDecision = "keep:candidate_penalty_worse";
                return true;
            }
            boolean keep = candidatePath.size() >= currentPath.size() + 4 && candidatePenalty >= currentPenalty;
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
            || (candidateGoalDistance < currentGoalDistance && candidatePathSize >= currentPath.size() + 3);
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
        BlockPos verticalEscapeTarget = selectVerticalEscapeTarget(world, playerFootPos, waypoint);
        synchronized (this) {
            BlockPos nextTarget = switch (mode) {
                case RECOVER_JUMP, RECOVER_BREAK, RECOVER_PILLAR, RECOVER_ESCAPE -> controllerTarget != null ? controllerTarget : waypoint;
                case BREAK_BLOCK -> {
                    BlockPos miningTarget = selectMiningControllerTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
                    yield miningTarget != null ? miningTarget : waypoint;
                }
                case PILLAR -> {
                    if (controllerMode == ControllerMode.PILLAR
                        && controllerTarget != null
                        && now <= controllerUntilMs
                        && controllerTarget.getX() == playerFootPos.getX()
                        && controllerTarget.getZ() == playerFootPos.getZ()
                        && controllerTarget.getY() >= playerFootPos.getY()
                        && controllerTarget.getY() <= playerFootPos.getY() + 1) {
                        yield controllerTarget;
                    }
                    yield verticalEscapeTarget != null ? verticalEscapeTarget : waypoint;
                }
                case COMMIT_JUMP, DROP, FOLLOW_PATH -> waypoint;
                case ESCAPE_HOLE -> committedEscapeTarget != null ? committedEscapeTarget : waypoint;
            };
            if (mode != controllerMode || !java.util.Objects.equals(nextTarget, controllerTarget)) {
                controllerMode = mode;
                controllerTarget = nextTarget;
                controllerEnteredAtMs = now;
                controllerProgressAtMs = now;
                controllerBestDistanceSq = distanceSq;
            }
            controllerUntilMs = switch (mode) {
                case COMMIT_JUMP -> committedJumpUntilMs;
                case ESCAPE_HOLE -> committedEscapeUntilMs;
                case BREAK_BLOCK -> plannedPrimitive != null && plannedPrimitive.requiresBreak() ? now + BREAK_COMMIT_WINDOW_MS : now + 250L;
                case PILLAR -> now + 1800L;
                case DROP -> now + DROP_COMMIT_WINDOW_MS;
                default -> now + 250L;
            };
            return controllerMode;
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
            MiningAscentPhase phase = resolveMiningAscentPhase(world, playerFootPos, waypoint, plannedPrimitive);
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
        PlacementTargetState placementTargetState = resolveCommittedPlacementTargetState(world, waypoint, plannedPrimitive);
        if (placementTargetState.target() != null) {
            return placementTargetState.target().immutable();
        }
        synchronized (this) {
            if (activeBreakTarget != null
                && pathPlanner.isBreakableForNavigator(world, activeBreakTarget)
                && canBreakTargetNow(world, player, activeBreakTarget)) {
                return activeBreakTarget.immutable();
            }
        }
        BlockPos breakTarget = selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
        return breakTarget != null ? breakTarget.immutable() : waypoint.immutable();
    }

    private void noteControllerProgress(long now, double distanceSq) {
        synchronized (this) {
            if (distanceSq < controllerBestDistanceSq) {
                controllerBestDistanceSq = distanceSq;
                controllerProgressAtMs = now;
            }
        }
    }

    private double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget) {
        if (player == null) {
            return Double.POSITIVE_INFINITY;
        }
        BlockPos target;
        synchronized (this) {
            target = controllerTarget != null ? controllerTarget : fallbackTarget;
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
            controllerProgressAtMs = now;
        }
    }

    private void noteRouteProgress(long now) {
        synchronized (this) {
            int routeProgress = routeProgressScoreLocked();
            if (routeProgress > bestRouteProgressScore) {
                bestRouteProgressScore = routeProgress;
                lastProgressAtMs = now;
                controllerProgressAtMs = now;
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
        if (now - lastPlanAtMs > ROUTE_STABILIZATION_MS) {
            return false;
        }
        if (currentPath.isEmpty() || activeWaypoint == null) {
            return false;
        }
        if (pathIndex > Math.min(2, currentPath.size() - 1)) {
            return false;
        }
        return playerFootPos == null || isPlayerNearCommittedPathStart(playerFootPos);
    }

    private void updateFollowSegment(FollowSegmentType type, BlockPos target, double segmentDistanceSq, long now) {
        synchronized (this) {
            if (type != activeFollowSegment || !java.util.Objects.equals(activeFollowSegmentTarget, target)) {
                activeFollowSegment = type;
                activeFollowSegmentTarget = target != null ? target.immutable() : null;
                activeFollowSegmentEnteredAtMs = now;
                activeFollowSegmentProgressAtMs = now;
                activeFollowSegmentBestDistanceSq = segmentDistanceSq;
                return;
            }
            if (segmentDistanceSq + 0.01D < activeFollowSegmentBestDistanceSq) {
                activeFollowSegmentBestDistanceSq = segmentDistanceSq;
                activeFollowSegmentProgressAtMs = now;
            }
        }
    }

    private long followSegmentIdleMs(long now) {
        synchronized (this) {
            return now - activeFollowSegmentProgressAtMs;
        }
    }

    private int routeProgressScoreLocked() {
        int waypointProgress = Math.max(0, pathIndex) * 100;
        int breakPenalty = plannedBreakTargets == null ? 0 : plannedBreakTargets.size() * 7;
        int escapePenalty = committedEscape.breakTargets().size() * 5
            + committedEscape.route().size() * 3;
        return waypointProgress - breakPenalty - escapePenalty;
    }

    private boolean shouldRedirectController(long now, double distanceSq) {
        synchronized (this) {
            if (!committedEscape.isEmpty()
                && committedEscapeUntilMs > now
                && (controllerMode == ControllerMode.PILLAR || controllerMode == ControllerMode.ESCAPE_HOLE)) {
                return false;
            }
            long idleMs = now - controllerProgressAtMs;
            boolean distanceImproved = distanceSq + 4.0D < controllerBestDistanceSq;
            if (distanceImproved) {
                controllerBestDistanceSq = distanceSq;
                controllerProgressAtMs = now;
                return false;
            }
            return switch (controllerMode) {
                case FOLLOW_PATH -> idleMs > 2200L;
                case RECOVER_JUMP -> idleMs > 900L || now > controllerUntilMs;
                case RECOVER_BREAK -> idleMs > 1500L || now > controllerUntilMs;
                case RECOVER_PILLAR -> idleMs > 2200L || now > controllerUntilMs;
                case RECOVER_ESCAPE -> idleMs > 1800L || now > controllerUntilMs;
                case BREAK_BLOCK -> idleMs > 1500L;
                case PILLAR -> idleMs > 2600L || now > controllerUntilMs;
                case COMMIT_JUMP -> idleMs > 900L;
                case DROP -> idleMs > 1100L || now > controllerUntilMs;
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
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            if (committedJumpWaypoint != null && committedJumpUntilMs > now) {
                return ControllerMode.COMMIT_JUMP;
            }
            BlockPos breakTarget = selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
            if (breakTarget != null) {
                return ControllerMode.BREAK_BLOCK;
            }
            return ControllerMode.FOLLOW_PATH;
        }
        boolean committedEscape = isCommittedEscapeState(now);
        if (isRecoveryState(world, playerFootPos, now)) {
            return controllerMode;
        }
        if (isCommittedPillarState(world, playerFootPos, now) && (isPillarPrimitive(plannedPrimitive) || committedEscape)) {
            return ControllerMode.PILLAR;
        }
        if (isPillarPrimitive(plannedPrimitive)
            || shouldUsePillarStep(world, playerFootPos, waypoint, plannedPrimitive, now)) {
            return ControllerMode.PILLAR;
        }
        if (plannedPrimitive != null && plannedPrimitive.shouldCommitDrop(waypoint, playerFootPos)) {
            return ControllerMode.DROP;
        }
        if (committedJumpWaypoint != null && committedJumpUntilMs > now) {
            return ControllerMode.COMMIT_JUMP;
        }
        BlockPos breakTarget = selectBreakTarget(world, player, playerFootPos, waypoint, plannedPrimitive);
        boolean miningAscentStep = plannedPrimitive != null && plannedPrimitive.isMineAscent();
        if (breakTarget != null
            || miningAscentStep
            || (allowBlockPlacing && primitiveStillRequiresPlace(world, plannedPrimitive))) {
            return ControllerMode.BREAK_BLOCK;
        }
        return ControllerMode.FOLLOW_PATH;
    }

    private boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        return world != null && playerFootPos != null && waypoint != null && now >= 0L && isPillarPrimitive(plannedPrimitive);
    }

    private ControllerMode recoveryModeForPrimitive(PlannedPrimitive plannedPrimitive, Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (isCommittedEscapeState(now)) {
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
            if (controllerMode != ControllerMode.PILLAR || controllerTarget == null || now > controllerUntilMs) {
                return false;
            }
            BlockPos pillarTarget = controllerTarget;
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
            if ((controllerMode != ControllerMode.RECOVER_JUMP
                && controllerMode != ControllerMode.RECOVER_BREAK
                && controllerMode != ControllerMode.RECOVER_PILLAR
                && controllerMode != ControllerMode.RECOVER_ESCAPE)
                || controllerTarget == null
                || now > controllerUntilMs) {
                return false;
            }
            if (currentPath.isEmpty() || activeWaypoint == null) {
                return false;
            }
            if (isTrappedInCrampedSpace(world, playerFootPos, activeWaypoint)
                || selectVerticalEscapeTarget(world, playerFootPos, activeWaypoint) != null) {
                return false;
            }
            if (!isPlayerNearPath(playerFootPos)) {
                return false;
            }
            if (!isWaypointActionable(world, controllerTarget)) {
                return false;
            }
            if (requiresBreakingForWaypoint(world, controllerTarget) || pathPlanner.needsPlacedSupport(world, controllerTarget)) {
                return false;
            }
            return true;
        }
    }

    private boolean shouldEnterEscapeRecovery(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            return false;
        }
        int physicalWalkNeighbors = countPhysicalWalkNeighbors(world, playerFootPos);
        if (physicalWalkNeighbors > 1) {
            return false;
        }
        if (isCommittedEscapeState(now)) {
            return !canExitTrappedRecovery(world, playerFootPos, waypoint, now);
        }
        if (!isTrappedInCrampedSpace(world, playerFootPos, waypoint)) {
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
                activeBreakTarget = null;
            }
        }
        clearExcavationPlan(now, "escape cleared", "resume route");
    }

    private void repairCurrentPath(Level world, BlockPos playerFootPos, BlockPos target, long now, String replanReason, String stuckReason) {
        synchronized (this) {
            activeBreakTarget = null;
            committedJumpWaypoint = null;
            committedJumpUntilMs = 0L;
            if (pathIndex < 0) {
                pathIndex = 0;
                furthestVisitedPathIndex = 0;
            }
            if (!currentPath.isEmpty()) {
                if (pathIndex >= currentPath.size()) {
                    pathIndex = currentPath.size() - 1;
                }
                chooseRecoveryPathIndexLocked(world, playerFootPos, target);
                activeWaypoint = currentPath.get(pathIndex);
                plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
            } else {
                activeWaypoint = null;
                plannedBreakTargets = List.of();
            }
            lastPlanAtMs = now;
            lastProgressAtMs = now;
            routeCommitUntilMs = Math.max(routeCommitUntilMs, now + 650L);
            bestRouteProgressScore = routeProgressScoreLocked();
            lastReplanReason = replanReason;
            lastStuckReason = stuckReason;
            if (playerFootPos != null) {
                lastMovementSamplePos = Vec3.atCenterOf(playerFootPos);
            }
            lastMovementAtMs = now;
            lastDistanceCheckpointAtMs = now;
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
        if (isInteractablePrimitive(plannedPrimitive)
            && (pathPlanner.requiresInteractableTraversal(world, playerFootPos, waypoint)
            || pathPlanner.hasPathOpenableAhead(world, playerFootPos, waypoint))) {
            return false;
        }
        synchronized (this) {
            return now - lastProgressAtMs >= 900L;
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
            alreadyRecovering = controllerMode == ControllerMode.RECOVER_JUMP
                || controllerMode == ControllerMode.RECOVER_BREAK
                || controllerMode == ControllerMode.RECOVER_PILLAR
                || controllerMode == ControllerMode.RECOVER_ESCAPE;
            activePrimitive = activePlannedPrimitive;
        }
        if (world != null
            && playerFootPos != null
            && waypoint != null
            && shouldEnterEscapeRecovery(world, playerFootPos, waypoint, activePrimitive, now)) {
            clearExcavationPlan(now, replanReason, stuckReason);
            ensureExcavationPlan(world, playerFootPos, waypoint, now);
            synchronized (this) {
                controllerMode = ControllerMode.RECOVER_ESCAPE;
                controllerTarget = committedEscapeTarget != null ? committedEscapeTarget : waypoint.immutable();
                controllerEnteredAtMs = now;
                controllerUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                controllerProgressAtMs = now;
                controllerBestDistanceSq = Double.POSITIVE_INFINITY;
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
                controllerMode = recoveryModeForPrimitive(activePrimitive, world, playerFootPos, waypoint, now);
                controllerTarget = activeWaypoint != null ? activeWaypoint.immutable() : (waypoint != null ? waypoint.immutable() : null);
                controllerEnteredAtMs = now;
                controllerUntilMs = now + 1800L;
                lastLocalRecoveryAtMs = now;
                localRecoveryAttempts++;
                lastReplanReason = replanReason;
                controllerProgressAtMs = now;
                controllerBestDistanceSq = Double.POSITIVE_INFINITY;
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
                    currentPath = recovery.path();
                    candidatePaths = recovery.candidatePaths();
                    candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                    goalMode = shouldTrackResolvedPlanningGoal(target, recovery.resolvedGoalPos(), recovery.goalMode())
                        ? recovery.goalMode()
                        : GoalMode.EXACT;
                    resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.immutable();
                    committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().immutable() : resolvedGoalPos;
                    committedPathStartPos = playerFootPos != null ? playerFootPos.immutable() : null;
                    pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                    lastWaypointAdvanceAtMs = now;
                    furthestVisitedPathIndex = Math.max(-1, pathIndex - 1);
                    activeWaypoint = currentPath.get(pathIndex);
                    plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                    currentPlan = recovery.plannedPrimitives();
                    activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                    appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
                    activeBreakTarget = null;
                    committedJumpWaypoint = null;
                    committedJumpUntilMs = 0L;
                    committedEscapeTarget = null;
                    committedEscapeUntilMs = 0L;
                    committedEscape = EscapePlan.empty();
                    committedEscapePrimitiveIndex = 0;
                    lastPlanAtMs = now;
                    lastProgressAtMs = now;
                    routeCommitUntilMs = now + ROUTE_COMMIT_MS;
                    lastLocalRecoveryAtMs = 0L;
                    localRecoveryAttempts = 0;
                    bestRouteProgressScore = routeProgressScoreLocked();
                    lastReplanReason = replanReason;
                    lastStuckReason = stuckReason;
                    lastMovementAtMs = now;
                    lastMovementSamplePos = currentPos != null ? currentPos : Vec3.atCenterOf(playerFootPos);
                    lastDistanceCheckpointAtMs = now;
                    controllerProgressAtMs = now;
                    controllerBestDistanceSq = Double.POSITIVE_INFINITY;
                }
                return;
            }
            if (canRepairCurrentPath(world, playerFootPos, target)) {
                repairCurrentPath(world, playerFootPos, target, now, "recovery deferred", stuckReason);
                synchronized (this) {
                    controllerMode = recoveryModeForPrimitive(activePrimitive, world, playerFootPos, waypoint, now);
                    controllerTarget = activeWaypoint != null ? activeWaypoint.immutable() : (waypoint != null ? waypoint.immutable() : null);
                    controllerEnteredAtMs = now;
                    controllerUntilMs = now + 1800L;
                    controllerProgressAtMs = now;
                    controllerBestDistanceSq = Double.POSITIVE_INFINITY;
                }
                return;
            }
        }

        redirectCurrentPath(playerFootPos, waypoint, currentPos, now, replanReason, stuckReason);
    }

    private boolean shouldAttemptLocalRecovery(BlockPos playerFootPos, BlockPos target, long now) {
        synchronized (this) {
            if (currentPath.isEmpty() || activeWaypoint == null) {
                return false;
            }
            if (isJumpExecutionLocked(now, activePlannedPrimitive)) {
                return false;
            }
            if (controllerMode == ControllerMode.RECOVER_JUMP
                || controllerMode == ControllerMode.RECOVER_BREAK
                || controllerMode == ControllerMode.RECOVER_PILLAR
                || controllerMode == ControllerMode.RECOVER_ESCAPE) {
                return false;
            }
            if (localRecoveryAttempts >= MAX_LOCAL_RECOVERY_ATTEMPTS) {
                return false;
            }
            if (now - lastLocalRecoveryAtMs < LOCAL_RECOVERY_COOLDOWN_MS) {
                return false;
            }
            if (isExcavatingState(now)) {
                return false;
            }
            if (activeWaypointRequiresCommittedAction()) {
                return false;
            }
            if (localRecoveryAttempts > 0 && routeProgressScoreLocked() <= bestRouteProgressScore) {
                return false;
            }
            return isPlayerNearPath(playerFootPos) && isPathGoalStillValid(currentPath, target);
        }
    }

    private boolean activeWaypointRequiresCommittedAction() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || activeWaypoint == null) {
            return false;
        }
        Level world = client.level;
        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }
        if (plannedPrimitive != null) {
            return plannedPrimitive.requiresCommittedAction();
        }
        if (requiresBreakingForWaypoint(world, activeWaypoint) || pathPlanner.needsPlacedSupport(world, activeWaypoint)) {
            return true;
        }
        BlockPos previous = pathIndex > 0 && pathIndex - 1 < currentPath.size() ? currentPath.get(pathIndex - 1) : null;
        if (previous != null && !requiresBreakingForWaypoint(world, activeWaypoint) && !pathPlanner.requiresInteractableTraversal(world, previous, activeWaypoint)) {
            int dy = activeWaypoint.getY() - previous.getY();
            if (dy > 0 || pathPlanner.shouldStepJump(world, previous, activeWaypoint)) {
                return true;
            }
        }
        return false;
    }

    private void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint) {
        synchronized (this) {
            if (currentPath.isEmpty()) {
                pathIndex = 0;
                furthestVisitedPathIndex = 0;
                activeWaypoint = null;
                return;
            }
            int bestIndex = Math.max(furthestVisitedPathIndex, Math.min(pathIndex, currentPath.size() - 1));
            if (preferredWaypoint != null) {
                int preferredIndex = currentPath.indexOf(preferredWaypoint);
                if (preferredIndex >= furthestVisitedPathIndex) {
                    bestIndex = preferredIndex;
                }
            }
            if (playerFootPos != null) {
                int forwardIndex = -1;
                double bestForwardScore = Double.POSITIVE_INFINITY;
                for (int i = bestIndex; i < Math.min(currentPath.size(), bestIndex + 3); i++) {
                    BlockPos step = currentPath.get(i);
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
                    for (int i = bestIndex; i >= furthestVisitedPathIndex; i--) {
                        BlockPos step = currentPath.get(i);
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
            pathIndex = Math.max(furthestVisitedPathIndex, Math.min(bestIndex, currentPath.size() - 1));
            activeWaypoint = currentPath.get(pathIndex);
        }
    }

    private void chooseRecoveryPathIndexLocked(Level world, BlockPos playerFootPos, BlockPos target) {
        if (currentPath.isEmpty()) {
            pathIndex = 0;
            furthestVisitedPathIndex = 0;
            return;
        }
        int boundedIndex = Math.max(furthestVisitedPathIndex, Math.min(pathIndex, currentPath.size() - 1));
        if (world == null || playerFootPos == null || target == null) {
            pathIndex = boundedIndex;
            return;
        }

        double playerGoalDistance = pathPlanner.horizontalDistanceSq(playerFootPos, target);
        for (int i = boundedIndex; i < currentPath.size() && i <= boundedIndex + 2; i++) {
            BlockPos step = currentPath.get(i);
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
                pathIndex = i;
                return;
            }
        }

        for (int i = boundedIndex; i >= furthestVisitedPathIndex && i >= boundedIndex - 2; i--) {
            BlockPos step = currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (pathPlanner.horizontalDistanceSq(playerFootPos, step) > 9.0D || Math.abs(step.getY() - playerFootPos.getY()) > 2) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            pathIndex = i;
            return;
        }

        pathIndex = boundedIndex;
    }

    private void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now, String replanReason, String stuckReason) {
        rememberFailedRedirectWindow(playerFootPos, waypoint, now);
        synchronized (this) {
            currentPath = List.of();
            currentPlan = List.of();
            candidatePaths = List.of();
            candidatePathsVisibleUntilMs = 0L;
            activeWaypoint = null;
            committedPathStartPos = null;
            committedPathGoalPos = null;
            committedPathStartPos = null;
            pathIndex = 0;
            furthestVisitedPathIndex = 0;
            plannedBreakTargets = List.of();
            activeBreakTarget = null;
            committedJumpWaypoint = null;
            committedJumpUntilMs = 0L;
            lastPlanAtMs = 0L;
            routeCommitUntilMs = 0L;
            lastLocalRecoveryAtMs = 0L;
            localRecoveryAttempts = 0;
            bestRouteProgressScore = Integer.MIN_VALUE;
            lastReplanReason = replanReason;
            lastStuckReason = stuckReason;
            lastMovementAtMs = now;
            lastMovementSamplePos = currentPos != null ? currentPos : Vec3.ZERO;
            lastDistanceCheckpointAtMs = now;
        }
    }

    private void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) {
        pathPlanner.rememberFailedMove(playerFootPos, waypoint, now);
        synchronized (this) {
            if (currentPath.isEmpty()) {
                return;
            }
            int startIndex = pathIndex;
            if (waypoint != null) {
                int waypointIndex = currentPath.indexOf(waypoint);
                if (waypointIndex >= 0) {
                    startIndex = waypointIndex;
                }
            }
            startIndex = Math.max(0, Math.min(startIndex, currentPath.size() - 1));
            BlockPos previous = playerFootPos;
            for (int i = startIndex; i < Math.min(currentPath.size(), startIndex + 7); i++) {
                BlockPos step = currentPath.get(i);
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
            if (committedJumpWaypoint != null && committedJumpUntilMs > System.currentTimeMillis() && activeWaypoint != null) {
                if (plannedBreakTargets.isEmpty()) {
                    plannedBreakTargets = buildPathBreakPlan(world, currentPath, Math.max(0, pathIndex));
                }
                return activeWaypoint;
            }
            if (controllerMode == ControllerMode.PILLAR
                && controllerTarget != null
                && (isPillarPrimitive(activePlannedPrimitive) || !committedEscape.isEmpty())) {
                activeWaypoint = controllerTarget.immutable();
                if (plannedBreakTargets.isEmpty()) {
                    plannedBreakTargets = buildPathBreakPlan(world, currentPath, Math.max(0, pathIndex));
                }
                if (!isPillarPrimitive(activePlannedPrimitive)) {
                    activePlannedPrimitive = createPrimitiveSnapshot(world, playerFootPos, activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), activeWaypoint.below());
                }
                return activeWaypoint;
            }
        }
        BlockPos current = advanceWaypointIfNeeded(player, playerFootPos);
        if (current == null) {
            return null;
        }
        synchronized (this) {
            activeWaypoint = currentPath.get(pathIndex);
            if (plannedBreakTargets.isEmpty()) {
                plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
            }
            activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
            chooseForwardResyncIndexLocked(world, playerFootPos);
            activeWaypoint = currentPath.get(pathIndex);
            plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
            activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
            BlockPos committedGoal = committedPathGoalPos != null ? committedPathGoalPos : targetPos;
            if (world != null
                && playerFootPos != null
                && activeWaypoint != null
                && committedGoal != null
                && activeWaypoint.getY() > playerFootPos.getY()
                && activePlannedPrimitive != null
                && activePlannedPrimitive.allowsForwardResync()) {
                int previousIndex = pathIndex;
                chooseRecoveryPathIndexLocked(world, playerFootPos, committedGoal);
                if (pathIndex != previousIndex && pathIndex >= 0 && pathIndex < currentPath.size()) {
                    activeWaypoint = currentPath.get(pathIndex);
                    plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                    activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                    lastAdvanceDecision = "resync:lower_actionable_step pathIndex=" + pathIndex + " waypoint=" + formatDebugPos(activeWaypoint);
                }
            }
            activePlannedPrimitive = normalizeActivePrimitiveLocked(world, playerFootPos, activeWaypoint, activePlannedPrimitive);
            return activeWaypoint;
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
                if (reachedIndex > pathIndex) {
                    lastProgressAtMs = now;
                    routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                }
                pathIndex = Math.max(pathIndex, reachedIndex);
                lastWaypointAdvanceAtMs = now;
                furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, reachedIndex);
                lastAdvanceDecision = "advance:reached_index=" + reachedIndex;
            }
            while (!currentPath.isEmpty() && pathIndex < currentPath.size()) {
                BlockPos waypoint = currentPath.get(pathIndex);
                if (waypoint == null) {
                    pathIndex++;
                    lastWaypointAdvanceAtMs = now;
                    furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, pathIndex);
                    lastProgressAtMs = now;
                    routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                    lastAdvanceDecision = "advance:null_waypoint";
                    continue;
                }
                if (waypoint.getY() > playerFootPos.getY() && pathIndex + 1 < currentPath.size()) {
                    BlockPos next = currentPath.get(pathIndex + 1);
                    if (next != null
                        && playerFootPos.getY() >= next.getY()
                        && pathPlanner.horizontalDistanceSq(playerFootPos, next) <= WAYPOINT_REACHED_DISTANCE_SQ
                        && Math.abs(next.getY() - playerFootPos.getY()) <= 1) {
                        pathIndex++;
                        lastWaypointAdvanceAtMs = now;
                        furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, pathIndex);
                        lastProgressAtMs = now;
                        routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                        lastAdvanceDecision = "advance:skip_overshot_upward";
                        continue;
                    }
                }
                Vec3 waypointCenter = new Vec3(waypoint.getX() + 0.5D, playerPos.y, waypoint.getZ() + 0.5D);
                if (!shouldAdvancePastWaypoint(playerPos, playerFootPos, waypoint, waypointCenter)) {
                    activeWaypoint = waypoint;
                    lastAdvanceDecision = "hold:pathIndex=" + pathIndex + " waypoint=" + formatDebugPos(waypoint);
                    return waypoint;
                }
                pathIndex++;
                lastWaypointAdvanceAtMs = now;
                furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, pathIndex);
                lastProgressAtMs = now;
                routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
                lastAdvanceDecision = "advance:past_waypoint";
            }
            activeWaypoint = null;
            lastAdvanceDecision = "hold:no_active_waypoint";
            return null;
        }
    }

    private int findReachedPathIndexLocked(BlockPos playerFootPos, Vec3 playerPos) {
        if (playerFootPos == null || playerPos == null || currentPath.isEmpty()) {
            return -1;
        }
        int start = Math.max(0, pathIndex);
        int end = Math.min(currentPath.size() - 1, start + 6);
        int best = -1;
        for (int i = start; i <= end; i++) {
            BlockPos step = currentPath.get(i);
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
        if (world == null || playerFootPos == null || currentPath.isEmpty()) {
            return;
        }
        int boundedIndex = Math.max(furthestVisitedPathIndex, Math.min(pathIndex, currentPath.size() - 1));
        BlockPos currentStep = currentPath.get(boundedIndex);
        if (currentStep == null) {
            return;
        }
        double currentDistance = pathPlanner.horizontalDistanceSq(playerFootPos, currentStep);
        int bestIndex = boundedIndex;
        double bestScore = currentDistance;
        int end = Math.min(currentPath.size() - 1, boundedIndex + 4);
        for (int i = boundedIndex + 1; i <= end; i++) {
            BlockPos step = currentPath.get(i);
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
            int previousIndex = pathIndex;
            pathIndex = bestIndex;
            lastWaypointAdvanceAtMs = System.currentTimeMillis();
            furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, bestIndex - 1);
            lastAdvanceDecision = "resync:forward_index=" + bestIndex + " waypoint=" + formatDebugPos(currentPath.get(bestIndex));
            appendDebugEventLocked(
                "pathIndex " + previousIndex + " -> " + pathIndex
                    + " reason=forward_resync oldWaypoint=" + formatDebugPos(previousIndex >= 0 && previousIndex < currentPath.size() ? currentPath.get(previousIndex) : null)
                    + " newWaypoint=" + formatDebugPos(currentPath.get(pathIndex))
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
                && pathIndex + 1 < currentPath.size()) {
                BlockPos next = currentPath.get(pathIndex + 1);
                if (next != null
                    && pathPlanner.horizontalDistanceSq(playerFootPos, next) + 0.20D < pathPlanner.horizontalDistanceSq(playerFootPos, waypoint)
                    && hasStableFootingOnWaypoint(playerPos, next)
                    && Math.abs(next.getY() - playerFootPos.getY()) <= 1) {
                    return true;
                }
            }
            if (pathIndex > 0
                && pathIndex + 1 < currentPath.size()
                && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
                BlockPos previous = currentPath.get(pathIndex - 1);
                PlannedPrimitive primitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
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
        if (playerFootPos == null || currentPath.isEmpty()) {
            return false;
        }
        int start = Math.max(0, Math.min(pathIndex, currentPath.size() - 1) - 1);
        int end = Math.min(currentPath.size() - 1, Math.max(pathIndex, 0) + 3);
        for (int i = start; i <= end; i++) {
            BlockPos step = currentPath.get(i);
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
        if (index < 0 || index >= currentPlan.size()) {
            return null;
        }
        return currentPlan.get(index);
    }

    private void rebuildCurrentPlanLocked(Level world) {
        currentPlan = buildPlannedPrimitives(world, currentPath, committedPathStartPos);
        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
        if (!currentPlan.isEmpty()) {
            appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
            appendDebugEventLocked("planDetailed=" + formatIndexedPrimitiveSequence(currentPlan, 24));
        }
        if (!currentPath.isEmpty()) {
            appendDebugEventLocked("pathDetailed=" + formatIndexedPath(currentPath, 24));
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

    private boolean primitiveRequiresBreak(PlannedPrimitive primitive) {
        return primitive != null && primitive.requiresBreak();
    }

    private boolean primitiveRequiresPlace(PlannedPrimitive primitive) {
        return primitive != null && primitive.requiresPlace();
    }

    private boolean primitiveStillRequiresBreak(Level world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.breakTargets() == null || primitive.breakTargets().isEmpty()) {
            return false;
        }
        return world == null || firstPendingBreakTarget(world, primitive.breakTargets()) != null;
    }

    private boolean primitiveStillRequiresPlace(Level world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.placeTarget() == null) {
            return false;
        }
        return world == null || !pathPlanner.hasCollision(world, primitive.placeTarget());
    }

    private void clearStalePlaceStateIfNeeded(Level world, PlannedPrimitive primitive) {
        if (primitiveStillRequiresPlace(world, primitive)) {
            return;
        }
        synchronized (this) {
            if ("placed".equals(lastPlaceResult)
                || "accepted no block".equals(lastPlaceResult)
                || "ready".equals(lastPlaceResult)
                || "centering".equals(lastPlaceResult)
                || "waiting apex".equals(lastPlaceResult)) {
                lastPlaceTarget = null;
                lastPlaceResult = "none";
            }
        }
    }

    private boolean isPillarPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isPillar();
    }

    private boolean isClimbPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isClimb();
    }

    private boolean isDescendPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isDescend();
    }

    private boolean isJumpPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isJump();
    }

    private boolean isInteractablePrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isInteractable();
    }

    private boolean isSwimPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isSwim();
    }

    private boolean isWaypointPrimitiveAligned(BlockPos waypoint, PlannedPrimitive primitive) {
        if (waypoint == null || primitive == null || primitive.target() == null) {
            return true;
        }
        BlockPos target = primitive.target();
        int dx = Math.abs(target.getX() - waypoint.getX());
        int dy = Math.abs(target.getY() - waypoint.getY());
        int dz = Math.abs(target.getZ() - waypoint.getZ());
        return dx <= 1 && dz <= 1 && dy <= 1;
    }

    private boolean isActiveEscapePillarPrimitiveLocked() {
        if (committedEscape.isEmpty()
            || committedEscapePrimitiveIndex < 0
            || committedEscapePrimitiveIndex >= committedEscape.primitives().size()) {
            return false;
        }
        EscapePrimitive primitive = committedEscape.primitives().get(committedEscapePrimitiveIndex);
        return primitive != null && primitive.type() == EscapePrimitiveType.PILLAR;
    }

    private PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target) {
        return pathPlanner.findPath(world, start, target);
    }

    private void tryUseInteractables(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return;
        }
        if (now - lastInteractAtMs < 250L || client.gameMode == null) {
            return;
        }

        int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
        int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
        List<BlockPos> candidates = new ArrayList<>(6);
        candidates.add(playerFootPos);
        candidates.add(playerFootPos.above());
        if (stepX != 0 || stepZ != 0) {
            BlockPos front = new BlockPos(playerFootPos.getX() + stepX, playerFootPos.getY(), playerFootPos.getZ() + stepZ);
            candidates.add(front);
            candidates.add(front.above());
        }
        candidates.add(waypoint);
        candidates.add(waypoint.above());

        for (BlockPos candidate : candidates) {
            if (!pathPlanner.isBlockingInteractableForTraversal(world, candidate, playerFootPos, waypoint)) {
                continue;
            }
            client.gameMode.useItemOn(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(candidate), Direction.UP, candidate, false)
            );
            player.swing(InteractionHand.MAIN_HAND);
            synchronized (this) {
                lastInteractAtMs = now;
            }
            return;
        }
    }

    private boolean handleWaypointBlockInteraction(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }
        clearStalePlaceStateIfNeeded(world, plannedPrimitive);
        clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, plannedPrimitive, now);
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }
        if (!isWaypointPrimitiveAligned(waypoint, plannedPrimitive)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        if (shouldSuppressMiningNearGoal(world, player, playerFootPos, waypoint)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }

        synchronized (this) {
            if (activeBreakTarget != null && pathPlanner.canOccupy(world, activeBreakTarget)) {
                activeBreakTarget = null;
                lastProgressAtMs = now;
                lastReplanReason = "obstruction cleared";
            }
        }

        if (handleCommittedMiningInteraction(client, world, player, playerFootPos, waypoint, plannedPrimitive, now)) {
            return true;
        }

        BlockPos placeTarget;
        synchronized (this) {
            placeTarget = null;
        }
        if (primitiveStillRequiresPlace(world, plannedPrimitive)) {
            placeTarget = plannedPrimitive.placeTarget();
        }
        boolean committedWaterPlace = placeTarget != null
            && isCommittedWaterPlaceState(world, player, playerFootPos, waypoint, placeTarget);
        if (allowBlockPlacing
            && placeTarget != null
            && (committedWaterPlace
                || primitiveStillRequiresPlace(world, plannedPrimitive))) {
            boolean placed = tryPlaceSupportBlock(client, world, player, placeTarget, now, committedWaterPlace);
            if (placed) {
                noteControllerActivity(now);
            }
            return placed;
        }

        synchronized (this) {
            activeBreakTarget = null;
        }
        return false;
    }

    private boolean handleCommittedMiningMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3 currentPos,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        PlannedPrimitive plannedPrimitive;
        BlockPos miningTarget;
        long miningUntilMs;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
            miningTarget = controllerTarget != null ? controllerTarget : activeBreakTarget;
            miningUntilMs = controllerUntilMs;
        }

        boolean requiresCommittedMining = plannedPrimitive != null
            && (plannedPrimitive.requiresBreak() || plannedPrimitive.isMineAscent());
        boolean requiresCommittedPlacement = plannedPrimitive != null && plannedPrimitive.requiresPlace();
        MiningProgress miningProgress = resolveCommittedMiningProgress(world, playerFootPos, waypoint, plannedPrimitive);
        if (miningProgress.completed()) {
            synchronized (this) {
                if (commitPathIndexLocked(world, miningProgress.resumeIndex(), false, now, "advance:mining_complete")) {
                    lastReplanReason = miningProgress.minedAscent()
                        ? "mined ascent cleared"
                        : "break route step cleared";
                    lastStuckReason = miningProgress.minedAscent()
                        ? "advance into mined ascent"
                        : "advance after mining";
                    controllerUntilMs = Math.max(controllerUntilMs, now + 350L);
                }
                activeBreakTarget = null;
            }
            return false;
        }
        PlacementProgress placementProgress = resolveCommittedPlacementProgress(world, playerFootPos, waypoint, plannedPrimitive);
        if (placementProgress.completed()) {
            synchronized (this) {
                if (commitPathIndexLocked(world, placementProgress.resumeIndex(), false, now, "advance:placement_complete")) {
                    lastReplanReason = "support ready";
                    lastStuckReason = "advance after placement";
                    controllerUntilMs = Math.max(controllerUntilMs, now + 250L);
                }
            }
            return false;
        }
        if (handleWaypointBlockInteraction(client, world, player, playerFootPos, waypoint, now)) {
            boolean stalledMinedAscent = plannedPrimitive != null
                && plannedPrimitive.isMineAscent()
                && shouldRedirectController(now, distanceToControllerTargetSq(world, player, waypoint));
            if (stalledMinedAscent) {
                releaseMovementKeys(client);
                pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                recoverFromStuck(
                    client,
                    world,
                    playerFootPos,
                    waypoint,
                    target,
                    currentPos,
                    now,
                    "mined ascent redirect",
                    "mined ascent stalled"
                );
                return true;
            }
            synchronized (this) {
                if (requiresCommittedMining || requiresCommittedPlacement) {
                    lastReplanReason = "committed mine";
                    lastStuckReason = requiresCommittedMining ? "mining route step" : "placement route step";
                }
            }
            return true;
        }
        if (!requiresCommittedMining && !requiresCommittedPlacement) {
            return false;
        }

        BlockPos fallbackTarget = miningTarget != null ? miningTarget : waypoint;
        if (fallbackTarget == null) {
            return false;
        }
        boolean timedOut = now > miningUntilMs;
        boolean targetGone = requiresCommittedPlacement
            ? isPlacementTargetSatisfied(world, plannedPrimitive, fallbackTarget)
            : plannedPrimitive != null && plannedPrimitive.isMineAscent()
            ? isMiningAscentPhaseSatisfied(world, playerFootPos, waypoint, plannedPrimitive, fallbackTarget)
            : pathPlanner.canOccupy(world, fallbackTarget);
        if (!timedOut && !targetGone) {
            noteControllerActivity(now);
            return false;
        }

        if (requiresCommittedPlacement && !requiresCommittedMining) {
            pathPlanner.rememberFailedPlace(playerFootPos, fallbackTarget, now);
        } else {
            pathPlanner.rememberFailedBreak(playerFootPos, fallbackTarget, now);
        }
        recoverFromStuck(
            client,
            world,
            playerFootPos,
            waypoint,
            target,
            currentPos,
            now,
            timedOut ? (requiresCommittedPlacement && !requiresCommittedMining ? "place redirect" : "mine redirect")
                : (requiresCommittedPlacement && !requiresCommittedMining ? "place target invalidated" : "mine target invalidated"),
            timedOut ? (requiresCommittedPlacement && !requiresCommittedMining ? "place timeout" : "mine timeout")
                : (requiresCommittedPlacement && !requiresCommittedMining ? "place target invalid" : "mine target invalid")
        );
        return true;
    }

    private boolean handleCommittedMiningInteraction(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (allowBlockBreaking) {
            MiningTargetState targetState = resolveCommittedMiningTargetState(world, playerFootPos, waypoint, plannedPrimitive);
            BlockPos breakTarget = targetState.target();
            if (breakTarget != null
                && shouldBreakForWaypoint(playerFootPos, waypoint, breakTarget)
                && continueBreakingRequiredTarget(client, player, breakTarget, targetState.requiredTargets(), now)) {
                synchronized (this) {
                    lastReplanReason = targetState.currentlyActive()
                        ? "continue committed mine"
                        : "advance mining target";
                    lastStuckReason = "mining target " + formatDebugPos(breakTarget);
                }
                return true;
            }
        }

        MiningAscentPhase miningPhase = resolveMiningAscentPhase(world, playerFootPos, waypoint, plannedPrimitive);
        if (plannedPrimitive != null
            && plannedPrimitive.isMineAscent()
            && miningPhase == MiningAscentPhase.JUMP
            && waypoint.getY() > playerFootPos.getY()
            && player.onGround()
            && pathPlanner.canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)) {
            Vec3 currentVelocity = player.getDeltaMovement();
            double dx = waypoint.getX() + 0.5D - player.getX();
            double dz = waypoint.getZ() + 0.5D - player.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 0.0001D) {
                player.setDeltaMovement(
                    currentVelocity.x + (dx / horizontalDistance) * 0.14D,
                    currentVelocity.y,
                    currentVelocity.z + (dz / horizontalDistance) * 0.14D
                );
            }
            if (client.options != null) {
                if (client.options.keyUp != null) {
                    client.options.keyUp.setDown(true);
                }
                if (client.options.keyJump != null) {
                    client.options.keyJump.setDown(true);
                }
            }
            player.jumpFromGround();
            synchronized (this) {
                activeBreakTarget = null;
                activeMiningAscentPhase = MiningAscentPhase.JUMP;
                lastJumpAtMs = now;
                committedJumpWaypoint = waypoint.immutable();
                committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                lastReplanReason = "mined ascent jump";
                lastStuckReason = "airborne";
            }
            noteControllerActivity(now);
            return true;
        }

        if (plannedPrimitive != null
            && plannedPrimitive.isMineAscent()
            && miningPhase == MiningAscentPhase.ADVANCE
            && pathPlanner.horizontalDistanceSq(playerFootPos, waypoint) > WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
            BlockPos advanceBlock = pathPlanner.resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
            if (advanceBlock == null) {
                return false;
            }
            boolean jumpOpportunity = waypoint.getY() > playerFootPos.getY()
                && pathPlanner.canAttemptMiningAdvanceJump(world, playerFootPos, waypoint);
            if (waypoint.getY() > playerFootPos.getY() && !jumpOpportunity) {
                releaseMovementKeys(client);
                synchronized (this) {
                    activeBreakTarget = null;
                    activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
                    lastReplanReason = "mined ascent jump blocked";
                    lastStuckReason = "waiting for clear jump arc";
                }
                return true;
            }
            BlockPos moveTarget = jumpOpportunity
                ? pathPlanner.resolveJumpUpApproachTarget(world, playerFootPos, waypoint)
                : advanceBlock;
            if (moveTarget == null) {
                moveTarget = advanceBlock;
            }
            Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
            Vec3 advanceAim = Vec3.upFromBottomCenterOf(moveTarget, player.getY() - moveTarget.getY());
            double dx = advanceAim.x - currentPos.x;
            double dz = advanceAim.z - currentPos.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float jumpYawError = 180.0F;
            if (horizontalDistance > 0.0001D) {
                float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
                float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
                jumpYawError = Math.abs(Mth.wrapDegrees(targetYaw - nextYaw));
                player.setYRot(nextYaw);
                player.setYHeadRot(nextYaw);
                player.setYBodyRot(nextYaw);
                Vec3 velocity = player.getDeltaMovement();
                player.setDeltaMovement(
                    velocity.x * 0.20D + (dx / horizontalDistance) * 0.15D,
                    velocity.y,
                    velocity.z * 0.20D + (dz / horizontalDistance) * 0.15D
                );
            }
            if (client.options != null) {
            if (client.options.keyUp != null) {
                client.options.keyUp.setDown(true);
            }
            if (client.options.keyJump != null) {
                boolean canHop = player.onGround()
                    && jumpOpportunity
                    && horizontalDistance <= 1.6D
                    && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES;
                client.options.keyJump.setDown(canHop);
                if (jumpOpportunity && !canHop) {
                    synchronized (this) {
                        if (now - lastMiningJumpGateLogAtMs >= 250L) {
                            appendDebugEventLocked(
                                "miningAdvanceJumpGate player=" + formatDebugPos(playerFootPos)
                                    + " waypoint=" + formatDebugPos(waypoint)
                                    + " moveTarget=" + formatDebugPos(moveTarget)
                                    + " advanceBlock=" + formatDebugPos(advanceBlock)
                                    + " onGround=" + player.onGround()
                                    + " horizontalDistance=" + (((double) Math.round(horizontalDistance * 100.0D)) / 100.0D)
                                    + " jumpYawError=" + (((double) Math.round(jumpYawError * 100.0D)) / 100.0D)
                                    + " maxJumpYawError=" + JUMP_YAW_ALIGNMENT_DEGREES
                                    + " canAttempt=" + pathPlanner.canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)
                            );
                            lastMiningJumpGateLogAtMs = now;
                        }
                    }
                }
                if (canHop) {
                    player.jumpFromGround();
                    synchronized (this) {
                        activeBreakTarget = null;
                        activeMiningAscentPhase = MiningAscentPhase.JUMP;
                            lastJumpAtMs = now;
                            committedJumpWaypoint = moveTarget.immutable();
                            committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                            lastReplanReason = "mined ascent advance jump";
                            lastStuckReason = "jumping onto mined step";
                        }
                        noteControllerActivity(now);
                        return true;
                    }
                }
            }
            synchronized (this) {
                activeBreakTarget = null;
                activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
                lastReplanReason = jumpOpportunity ? "stage mined ascent jump" : "mined ascent advance";
                lastStuckReason = jumpOpportunity ? "staging mined step jump" : "advancing into mined step";
            }
            noteControllerProgress(now, horizontalDistance * horizontalDistance);
            return true;
        }
        return false;
    }

    private PlacementTargetState resolveCommittedPlacementTargetState(
        Level world,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || plannedPrimitive == null || plannedPrimitive.placeTarget() == null) {
            return PlacementTargetState.incomplete(null);
        }
        if (!primitiveStillRequiresPlace(world, plannedPrimitive)) {
            return PlacementTargetState.complete(plannedPrimitive.placeTarget());
        }
        return PlacementTargetState.incomplete(plannedPrimitive.placeTarget());
    }

    private PlacementProgress resolveCommittedPlacementProgress(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || playerFootPos == null || waypoint == null || plannedPrimitive == null || plannedPrimitive.placeTarget() == null) {
            return PlacementProgress.incomplete();
        }
        PlacementTargetState targetState = resolveCommittedPlacementTargetState(world, waypoint, plannedPrimitive);
        if (!targetState.completed()) {
            return PlacementProgress.incomplete();
        }
        int resumeIndex = resolveCommittedPlacementResumeIndexLocked(playerFootPos, waypoint);
        if (resumeIndex < 0) {
            return PlacementProgress.incomplete();
        }
        return new PlacementProgress(true, resumeIndex);
    }

    private int resolveCommittedPlacementResumeIndexLocked(BlockPos playerFootPos, BlockPos waypoint) {
        if (currentPath.isEmpty()) {
            return -1;
        }
        int boundedIndex = Math.max(0, Math.min(pathIndex, currentPath.size() - 1));
        int currentIndex = waypoint != null ? currentPath.indexOf(waypoint) : -1;
        if (currentIndex >= 0) {
            boundedIndex = currentIndex;
        }
        int nextIndex = Math.min(currentPath.size() - 1, boundedIndex + 1);
        return currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
    }

    private boolean isPlacementTargetSatisfied(Level world, PlannedPrimitive plannedPrimitive, BlockPos controllerTarget) {
        if (plannedPrimitive != null && plannedPrimitive.placeTarget() != null) {
            return !primitiveStillRequiresPlace(world, plannedPrimitive);
        }
        return controllerTarget != null && pathPlanner.hasCollision(world, controllerTarget);
    }

    private MiningProgress resolveCommittedMiningProgress(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || playerFootPos == null || waypoint == null || plannedPrimitive == null) {
            return MiningProgress.incomplete();
        }
        MiningTargetState targetState = resolveCommittedMiningTargetState(world, playerFootPos, waypoint, plannedPrimitive);
        if (!targetState.completed()) {
            return MiningProgress.incomplete();
        }

        int resumeIndex = resolveCommittedMiningResumeIndexLocked(playerFootPos, waypoint, plannedPrimitive);
        if (resumeIndex < 0) {
            return MiningProgress.incomplete();
        }
        if (plannedPrimitive.isMineAscent()) {
            int currentIndex = currentPath.isEmpty() ? -1 : Math.max(0, Math.min(pathIndex, currentPath.size() - 1));
            if (waypoint != null) {
                int waypointIndex = currentPath.indexOf(waypoint);
                if (waypointIndex >= 0) {
                    currentIndex = waypointIndex;
                }
            }
            if (resumeIndex <= currentIndex) {
                return MiningProgress.incomplete();
            }
        }
        return new MiningProgress(true, resumeIndex, plannedPrimitive.isMineAscent());
    }

    private MiningTargetState resolveCommittedMiningTargetState(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || waypoint == null) {
            return MiningTargetState.incomplete(List.of());
        }
        List<BlockPos> requiredTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : pathPlanner.getRequiredBreakTargets(world, waypoint);
        if (requiredTargets == null || requiredTargets.isEmpty()) {
            return MiningTargetState.complete(List.of());
        }

        BlockPos liveTarget;
        synchronized (this) {
            liveTarget = activeBreakTarget;
        }
        if (liveTarget != null
            && (!requiredTargets.contains(liveTarget) || !pathPlanner.isBreakableForNavigator(world, liveTarget))) {
            synchronized (this) {
                if (liveTarget.equals(activeBreakTarget)) {
                    activeBreakTarget = null;
                }
            }
            liveTarget = null;
        }

        BlockPos pendingTarget = firstPendingBreakTarget(world, requiredTargets);
        if (pendingTarget == null) {
            return MiningTargetState.complete(requiredTargets);
        }
        if (liveTarget != null && isPlannedBreakTargetReachable(playerFootPos, liveTarget)) {
            return new MiningTargetState(requiredTargets, liveTarget, true, false);
        }
        if (isPlannedBreakTargetReachable(playerFootPos, pendingTarget)) {
            return new MiningTargetState(requiredTargets, pendingTarget, false, false);
        }
        for (BlockPos candidate : requiredTargets) {
            if (candidate == null || !pathPlanner.isBreakableForNavigator(world, candidate)) {
                continue;
            }
            if (!isPlannedBreakTargetReachable(playerFootPos, candidate)) {
                continue;
            }
            return new MiningTargetState(requiredTargets, candidate, false, false);
        }
        return MiningTargetState.incomplete(requiredTargets);
    }

    private MiningAscentPhase resolveMiningAscentPhase(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (plannedPrimitive == null || !plannedPrimitive.isMineAscent()) {
            return MiningAscentPhase.CLEARANCE;
        }
        if (world == null || playerFootPos == null || waypoint == null) {
            return MiningAscentPhase.CLEARANCE;
        }
        if (primitiveStillRequiresBreak(world, plannedPrimitive)) {
            synchronized (this) {
                activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
            }
            return MiningAscentPhase.CLEARANCE;
        }
        BlockPos advanceBlock = pathPlanner.resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
        if (advanceBlock != null
            && pathPlanner.horizontalDistanceSq(playerFootPos, advanceBlock) > WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(playerFootPos.getY() - advanceBlock.getY()) <= 1) {
            synchronized (this) {
                activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
            }
            return MiningAscentPhase.ADVANCE;
        }
        synchronized (this) {
            activeMiningAscentPhase = MiningAscentPhase.JUMP;
        }
        return MiningAscentPhase.JUMP;
    }

    private boolean isMiningAscentPhaseSatisfied(
        Level world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        BlockPos controllerTarget
    ) {
        if (plannedPrimitive == null || !plannedPrimitive.isMineAscent()) {
            return controllerTarget != null && pathPlanner.canOccupy(world, controllerTarget);
        }
        MiningAscentPhase phase = resolveMiningAscentPhase(world, playerFootPos, waypoint, plannedPrimitive);
        return switch (phase) {
            case CLEARANCE -> primitiveStillRequiresBreak(world, plannedPrimitive);
            case ADVANCE -> controllerTarget != null
                && playerFootPos != null
                && pathPlanner.horizontalDistanceSq(playerFootPos, controllerTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
                && Math.abs(playerFootPos.getY() - controllerTarget.getY()) <= 1;
            case JUMP -> false;
        };
    }

    private int resolveCommittedMiningResumeIndexLocked(
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (currentPath.isEmpty()) {
            return -1;
        }
        int boundedIndex = Math.max(0, Math.min(pathIndex, currentPath.size() - 1));
        int currentIndex = waypoint != null ? currentPath.indexOf(waypoint) : -1;
        if (currentIndex >= 0) {
            boundedIndex = currentIndex;
        }
        if (plannedPrimitive.isMineAscent()) {
            double stairDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, waypoint);
            boolean reachedByHeight = playerFootPos.getY() >= waypoint.getY() - 1;
            boolean reachedCurrentStep = reachedByHeight && stairDistanceSq <= WAYPOINT_REACHED_DISTANCE_SQ;
            if (reachedCurrentStep) {
                int nextIndex = Math.min(currentPath.size() - 1, boundedIndex + 1);
                appendDebugEventLocked(
                    "miningResume currentIndex=" + boundedIndex
                        + " nextIndex=" + nextIndex
                        + " player=" + formatDebugPos(playerFootPos)
                        + " waypoint=" + formatDebugPos(waypoint)
                        + " nextWaypoint=" + formatDebugPos(currentPath.get(nextIndex))
                        + " reachedByHeight=" + reachedByHeight
                        + " stairDistanceSq=" + (((double) Math.round(stairDistanceSq * 100.0D)) / 100.0D)
                        + " reachedCurrentStep=true"
                );
                return currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
            }
            long now = System.currentTimeMillis();
            if (now - lastMiningResumeLogAtMs >= 250L) {
                appendDebugEventLocked(
                    "miningResume currentIndex=" + boundedIndex
                        + " nextIndex=hold"
                        + " player=" + formatDebugPos(playerFootPos)
                        + " waypoint=" + formatDebugPos(waypoint)
                        + " reachedByHeight=" + reachedByHeight
                        + " stairDistanceSq=" + (((double) Math.round(stairDistanceSq * 100.0D)) / 100.0D)
                        + " reachedCurrentStep=false"
                );
                lastMiningResumeLogAtMs = now;
            }
            return boundedIndex;
        }
        int nextIndex = Math.min(currentPath.size() - 1, boundedIndex + 1);
        return currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
    }

    private boolean handleJumpRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        PlannedPrimitive plannedPrimitive;
        BlockPos recoveryTarget;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
            recoveryTarget = controllerTarget != null ? controllerTarget : waypoint;
        }
        if (world == null || player == null || playerFootPos == null || recoveryTarget == null) {
            return false;
        }
        if (!isJumpPrimitive(plannedPrimitive) || recoveryTarget.getY() <= playerFootPos.getY()) {
            return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_JUMP, "recovery jump", "recovery move");
        }
        if (player.onGround() && pathPlanner.canAttemptJump(world, playerFootPos, recoveryTarget)) {
            return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_JUMP, "recovery jump", "recovery move");
        }
        releaseMovementKeys(client);
        invalidateJumpRecovery(playerFootPos, recoveryTarget, now, "jump primitive invalidated", "blocked jump recovery");
        return true;
    }

    private void invalidateJumpRecovery(BlockPos playerFootPos, BlockPos recoveryTarget, long now, String replanReason, String stuckReason) {
        rememberFailedRedirectWindow(playerFootPos, recoveryTarget, now);
        synchronized (this) {
            controllerMode = ControllerMode.FOLLOW_PATH;
            controllerTarget = null;
            controllerUntilMs = 0L;
            committedJumpWaypoint = null;
            committedJumpUntilMs = 0L;
            activeBreakTarget = null;
            currentPath = List.of();
            currentPlan = List.of();
            candidatePaths = List.of();
            candidatePathsVisibleUntilMs = 0L;
            activeWaypoint = null;
            committedPathGoalPos = null;
            pathIndex = 0;
            furthestVisitedPathIndex = 0;
            plannedBreakTargets = List.of();
            lastPlanAtMs = 0L;
            routeCommitUntilMs = 0L;
            lastLocalRecoveryAtMs = 0L;
            localRecoveryAttempts = 0;
            bestRouteProgressScore = Integer.MIN_VALUE;
            lastReplanReason = replanReason;
            lastStuckReason = stuckReason;
            lastMovementAtMs = now;
            lastMovementSamplePos = playerFootPos != null ? Vec3.atCenterOf(playerFootPos) : Vec3.ZERO;
            lastDistanceCheckpointAtMs = now;
        }
    }

    private boolean handleBreakRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (handleWaypointBlockInteraction(client, world, player, playerFootPos, waypoint, now)) {
            synchronized (this) {
                lastReplanReason = "recover break";
                lastStuckReason = "recovering break step";
            }
            return true;
        }
        return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_BREAK, "recover break jump", "recover break move");
    }

    private boolean handlePillarRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        boolean allowPillarRecovery;
        synchronized (this) {
            allowPillarRecovery = isPillarPrimitive(activePlannedPrimitive) || !committedEscape.isEmpty();
        }
        if (allowPillarRecovery && handlePillaring(client, world, player, playerFootPos, waypoint, now)) {
            synchronized (this) {
                lastReplanReason = "recover pillar";
                lastStuckReason = "recovering pillar step";
            }
            return true;
        }
        return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_PILLAR, "recover pillar jump", "recover pillar move");
    }

    private boolean handleEscapeRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        return handleTrappedSpaceRecovery(client, world, player, playerFootPos, waypoint, now);
    }

    private boolean handleRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now,
        ControllerMode recoveryMode,
        String jumpReplanReason,
        String moveReplanReason
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || client.options == null) {
            return false;
        }

        BlockPos recoveryTarget;
        synchronized (this) {
            recoveryTarget = controllerTarget != null ? controllerTarget : waypoint;
        }
        if (recoveryTarget == null) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, recoveryTarget) <= 0.64D && Math.abs(playerFootPos.getY() - recoveryTarget.getY()) <= 1) {
            synchronized (this) {
                if (controllerMode == recoveryMode) {
                    controllerMode = ControllerMode.FOLLOW_PATH;
                    controllerTarget = null;
                    controllerUntilMs = 0L;
                }
            }
            return false;
        }
        if (!isWaypointActionable(world, recoveryTarget) || !isPlayerNearPath(playerFootPos)) {
            synchronized (this) {
                if (controllerMode == recoveryMode) {
                    controllerMode = ControllerMode.FOLLOW_PATH;
                    controllerTarget = null;
                    controllerUntilMs = 0L;
                }
            }
            return false;
        }

        boolean jumpOpportunity = pathPlanner.hasJumpUpOpportunity(world, playerFootPos, recoveryTarget);
        BlockPos jumpTarget = jumpOpportunity ? pathPlanner.resolveJumpUpApproachTarget(world, playerFootPos, recoveryTarget) : recoveryTarget;
        Vec3 targetCenter = new Vec3(jumpTarget.getX() + 0.5D, player.getY(), jumpTarget.getZ() + 0.5D);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
        float jumpYawError = Math.abs(Mth.wrapDegrees(targetYaw - nextYaw));
        player.setYRot(nextYaw);
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());

        boolean blocked = pathPlanner.isBlockedTowardWaypoint(world, playerFootPos, recoveryTarget) && !jumpOpportunity;
        releaseMovementKeys(client);
        if (client.options.keyUp != null) {
            client.options.keyUp.setDown((!blocked || jumpOpportunity) && horizontalDistance > 0.2D);
        }
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyJump != null) {
            boolean canHop = player.onGround()
                && jumpOpportunity
                && horizontalDistance <= 1.6D
                && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES
                && pathPlanner.canAttemptJump(world, playerFootPos, recoveryTarget);
            client.options.keyJump.setDown(false);
            if (canHop) {
                player.jumpFromGround();
                synchronized (this) {
                    lastJumpAtMs = now;
                    committedJumpWaypoint = jumpTarget.immutable();
                    committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    controllerMode = ControllerMode.COMMIT_JUMP;
                    controllerTarget = jumpTarget.immutable();
                    controllerUntilMs = committedJumpUntilMs;
                    lastReplanReason = jumpReplanReason;
                    lastStuckReason = "recovering to path";
                }
                noteControllerActivity(now);
                return true;
            }
        }

        synchronized (this) {
            lastReplanReason = moveReplanReason;
            lastStuckReason = blocked ? "recover blocked" : "recovering to path";
        }
        if (blocked) {
            long blockedRecoveryMs;
            synchronized (this) {
                blockedRecoveryMs = now - controllerEnteredAtMs;
            }
            if (blockedRecoveryMs > 900L) {
                return false;
            }
        }
        if (!blocked) {
            noteControllerActivity(now);
        }
        return true;
    }

    private boolean handleDirectFinalApproach(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos target,
        long now
    ) {
        if (client == null
            || client.options == null
            || world == null
            || player == null
            || playerFootPos == null
            || target == null
            || playerFootPos.getY() != target.getY()
            || pathPlanner.horizontalDistanceSq(playerFootPos, target) > 2.0D
            || !pathPlanner.isStandable(world, target)) {
            return false;
        }
        List<BlockPos> pendingBreaks = pathPlanner.getRequiredBreakTargets(world, playerFootPos, target);
        if (pendingBreaks == null
            || !pendingBreaks.isEmpty()
            || pathPlanner.needsPlacedSupport(world, target)
            || pathPlanner.requiresInteractableTraversal(world, playerFootPos, target)
            || pathPlanner.hasPathOpenableAhead(world, playerFootPos, target)
            || pathPlanner.isBlockedTowardWaypoint(world, playerFootPos, target)) {
            return false;
        }

        double targetX = target.getX() + 0.5D;
        double targetZ = target.getZ() + 0.5D;
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance <= 0.0001D) {
            return false;
        }

        double directDistanceSq = horizontalDistance * horizontalDistance;
        synchronized (this) {
            boolean enteringDirectApproach = !target.equals(controllerTarget)
                || !"direct final approach".equals(lastReplanReason);
            if (enteringDirectApproach) {
                controllerTarget = target.immutable();
                controllerProgressAtMs = now;
                controllerBestDistanceSq = directDistanceSq;
            } else if (directDistanceSq + 0.01D < controllerBestDistanceSq) {
                controllerProgressAtMs = now;
                controllerBestDistanceSq = directDistanceSq;
            } else if (now - controllerProgressAtMs > 1800L) {
                lastStuckReason = "direct final approach stalled";
                return false;
            }
        }

        float targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
        player.setYRot(nextYaw);
        player.setYHeadRot(nextYaw);
        player.setYBodyRot(nextYaw);

        Vec3 velocity = player.getDeltaMovement();
        double correctionScale = horizontalDistance <= 0.35D ? 0.08D : 0.14D;
        double correctionLimit = horizontalDistance <= 0.35D ? 0.035D : 0.075D;
        player.setDeltaMovement(
            velocity.x * 0.35D + Mth.clamp(dx * correctionScale, -correctionLimit, correctionLimit),
            velocity.y,
            velocity.z * 0.35D + Mth.clamp(dz * correctionScale, -correctionLimit, correctionLimit)
        );

        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(horizontalDistance > 0.22D);
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
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyJump != null) {
            client.options.keyJump.setDown(false);
        }
        if (client.options.keyShift != null) {
            client.options.keyShift.setDown(false);
        }

        synchronized (this) {
            activeWaypoint = target.immutable();
            controllerMode = ControllerMode.FOLLOW_PATH;
            controllerTarget = target.immutable();
            lastReplanReason = "direct final approach";
            lastStuckReason = "centering on goal";
        }
        return true;
    }

    private boolean handleFollowPathSegment(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        BlockPos target,
        Vec3 currentPos,
        double distanceSq,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (isCommittedEscapeState(now) && !isTrappedInCrampedSpace(world, playerFootPos, waypoint)) {
            clearExcavationPlan(now, "escape cleared", "resume route");
        }
        clearStalePlaceStateIfNeeded(world, plannedPrimitive);
        if (!isWaypointPrimitiveAligned(waypoint, plannedPrimitive)) {
            releaseMovementKeys(client);
            recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "primitive waypoint mismatch", "desynced step");
            return true;
        }
        boolean plannedClimb = isClimbPrimitive(plannedPrimitive);
        boolean plannedDrop = isDescendPrimitive(plannedPrimitive);
        boolean sameColumnDescent = playerFootPos.getX() == waypoint.getX() && playerFootPos.getZ() == waypoint.getZ();
        BlockPos climbAnchor = pathPlanner.resolveClimbAnchor(world, playerFootPos, waypoint);
        boolean climbNode = plannedClimb || (plannedPrimitive == null && climbAnchor != null);
        boolean verticalDropStep = (plannedDrop && sameColumnDescent)
            || (plannedPrimitive == null && playerFootPos.getX() == waypoint.getX()
            && playerFootPos.getZ() == waypoint.getZ()
            && waypoint.getY() < playerFootPos.getY()
            && pathPlanner.canSafelyDropTo(world, playerFootPos, waypoint));
        FollowSegmentType segmentType = climbNode ? FollowSegmentType.CLIMB : (verticalDropStep ? FollowSegmentType.DROP : FollowSegmentType.GROUND);
        BlockPos segmentTarget = climbNode ? (climbAnchor != null ? climbAnchor : waypoint) : waypoint;

        Vec3 waypointCenter = pathPlanner.resolveWaypointAimPoint(
            world,
            playerFootPos,
            waypoint,
            climbNode ? climbAnchor : null,
            plannedPrimitive,
            player.getY()
        );
        waypointCenter = pathPlanner.resolveSmoothedSteeringAimPoint(
            world,
            playerFootPos,
            waypoint,
            plannedPrimitive,
            currentPos,
            waypointCenter
        );
        double waypointDx = waypointCenter.x - currentPos.x;
        double waypointDz = waypointCenter.z - currentPos.z;
        double waypointHorizontalDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
        double waypointHorizontalDistanceSq = waypointDx * waypointDx + waypointDz * waypointDz;
        double waypointVerticalDelta = (waypoint.getY() + 0.1D) - currentPos.y;
        updateFollowSegment(segmentType, segmentTarget, waypointHorizontalDistanceSq, now);

        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(waypointDz, waypointDx)) - 90.0D));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(waypointVerticalDelta, Math.max(0.0001D, waypointHorizontalDistance)));
        float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
        float jumpYawError = Math.abs(Mth.wrapDegrees(targetYaw - nextYaw));
        float nextPitch = stepAngle(player.getXRot(), Mth.clamp(desiredPitch, -35.0F, 35.0F), MAX_PITCH_STEP);
        player.setYRot(nextYaw);
        player.setYHeadRot(nextYaw);
        player.setYBodyRot(nextYaw);
        player.setXRot(nextPitch);

        Vec3 desiredDirection = waypointHorizontalDistance <= 0.0001D
            ? Vec3.ZERO
            : new Vec3(waypointDx / waypointHorizontalDistance, 0.0D, waypointDz / waypointHorizontalDistance);
        Vec3 horizontalVelocity = new Vec3(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
        double forwardVelocity = desiredDirection.equals(Vec3.ZERO) ? 0.0D : horizontalVelocity.dot(desiredDirection);
        Vec3 rightDirection = new Vec3(desiredDirection.z, 0.0D, -desiredDirection.x);
        double lateralVelocity = desiredDirection.equals(Vec3.ZERO) ? 0.0D : horizontalVelocity.dot(rightDirection);
        double projectedForwardTravel = Math.max(0.0D, forwardVelocity) * COUNTERMOVEMENT_PREDICTION_TICKS;
        boolean overshootRisk = waypointHorizontalDistance <= COUNTERMOVEMENT_DISTANCE
            && projectedForwardTravel > waypointHorizontalDistance + 0.1D
            && forwardVelocity > COUNTERMOVEMENT_SPEED;
        boolean airborneDriftRisk = !player.onGround()
            && waypointHorizontalDistance <= AIR_COUNTERMOVEMENT_DISTANCE
            && (projectedForwardTravel > waypointHorizontalDistance + 0.05D
            || Math.abs(lateralVelocity) > COUNTERMOVEMENT_LATERAL_SPEED);
        boolean pillarStep = shouldUsePillarStep(world, playerFootPos, waypoint, plannedPrimitive, now);
        boolean interactableStep = isInteractablePrimitive(plannedPrimitive)
            || (plannedPrimitive == null
                && (pathPlanner.requiresInteractableTraversal(world, playerFootPos, waypoint)
                || pathPlanner.hasPathOpenableAhead(world, playerFootPos, waypoint)
                || pathPlanner.isPathOpenable(pathPlanner.cachedBlockState(world, playerFootPos.below()))));
        BlockPos rawPendingBreakTarget = selectBreakTarget(world, playerFootPos, waypoint, plannedPrimitive);
        BlockPos pendingBreakTarget = rawPendingBreakTarget != null && canBreakTargetNow(world, player, rawPendingBreakTarget)
            ? rawPendingBreakTarget
            : null;
        BlockPos liveBreakTarget;
        boolean nearFinalGoal;
        synchronized (this) {
            liveBreakTarget = activeBreakTarget;
            nearFinalGoal = targetPos != null
                && pathPlanner.horizontalDistanceSq(playerFootPos, targetPos) <= 4.0D
                && Math.abs(playerFootPos.getY() - targetPos.getY()) <= 1;
        }
        boolean liveBreaking = liveBreakTarget != null
            && pathPlanner.isBreakableForNavigator(world, liveBreakTarget)
            && canBreakTargetNow(world, player, liveBreakTarget);
        boolean hasBlockedBreakTarget = rawPendingBreakTarget != null && pendingBreakTarget == null;
        boolean breakRequiredStep = liveBreaking
            || pendingBreakTarget != null
            || (plannedPrimitive == null && requiresBreakingForWaypoint(world, waypoint));
        boolean miningAdvanceStep = primitiveRequiresBreak(plannedPrimitive)
            && !liveBreaking
            && pendingBreakTarget == null
            && !hasBlockedBreakTarget;
        boolean miningAdvanceJumpStep = miningAdvanceStep
            && plannedPrimitive != null
            && plannedPrimitive.isMineAscent()
            && waypoint.getY() > playerFootPos.getY();
        boolean placeRequiredStep = primitiveRequiresPlace(plannedPrimitive)
            || (plannedPrimitive == null && pathPlanner.needsPlacedSupport(world, waypoint) && shouldPlaceForWaypoint(world, playerFootPos, waypoint));
        boolean ascentCommitStep = plannedPrimitive != null
            && plannedPrimitive.shouldCommitAscent(waypoint, playerFootPos)
            && !breakRequiredStep
            && !placeRequiredStep
            && !pillarStep
            && !verticalDropStep;
        boolean jumpUpOpportunity = pathPlanner.hasJumpUpOpportunity(world, playerFootPos, waypoint);
        boolean blockedTowardWaypoint = pathPlanner.isBlockedTowardWaypoint(world, playerFootPos, waypoint)
            && !miningAdvanceStep
            && !jumpUpOpportunity;
        boolean simpleMovementStep = plannedPrimitive != null && plannedPrimitive.isSimpleMovementStep();
        boolean counterMovementEligible = segmentType == FollowSegmentType.GROUND
            && simpleMovementStep
            && jumpYawError <= COUNTERMOVEMENT_MAX_YAW_ERROR_DEGREES
            && waypointHorizontalDistance >= COUNTERMOVEMENT_MIN_DISTANCE;
        boolean applyCountermovement = counterMovementEligible
            && !nearFinalGoal
            && !pillarStep
            && !climbNode
            && committedJumpWaypoint == null
            && (overshootRisk || airborneDriftRisk);
        boolean turnInPlace = NavigatorGeometry.shouldTurnInPlace(
            segmentType == FollowSegmentType.GROUND,
            nearFinalGoal,
            pillarStep || breakRequiredStep || placeRequiredStep,
            waypointHorizontalDistance,
            jumpYawError,
            TURN_IN_PLACE_YAW_DEGREES
        );
        if (turnInPlace) {
            applyCountermovement = false;
            Vec3 velocity = player.getDeltaMovement();
            if (player.onGround()) {
                player.setDeltaMovement(velocity.x * 0.55D, velocity.y, velocity.z * 0.55D);
            }
            noteControllerActivity(now);
        }
        boolean jumpExecutionLocked = isJumpExecutionLocked(now, plannedPrimitive);
        boolean routeStabilizing;
        boolean routeCommitActive;
        synchronized (this) {
            routeStabilizing = isRouteStabilizingLocked(playerFootPos, now);
            routeCommitActive = now < routeCommitUntilMs;
        }

        tryUseInteractables(client, world, player, playerFootPos, waypoint, now);
        boolean climbUp = climbNode && waypoint.getY() > playerFootPos.getY();
        boolean climbDown = climbNode && waypoint.getY() < playerFootPos.getY();

        if (climbNode) {
            double correctionX = Mth.clamp(waypointDx * 0.18D, -0.08D, 0.08D);
            double correctionZ = Mth.clamp(waypointDz * 0.18D, -0.08D, 0.08D);
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x * 0.35D + correctionX, velocity.y, velocity.z * 0.35D + correctionZ);
        } else if (verticalDropStep) {
            double correctionX = Mth.clamp(waypointDx * 0.22D, -0.10D, 0.10D);
            double correctionZ = Mth.clamp(waypointDz * 0.22D, -0.10D, 0.10D);
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x * 0.15D + correctionX, velocity.y, velocity.z * 0.15D + correctionZ);
        } else if (miningAdvanceStep || ascentCommitStep) {
            double correctionScale = ascentCommitStep ? 0.22D : 0.16D;
            double correctionLimit = ascentCommitStep ? 0.11D : 0.07D;
            double velocityBlend = ascentCommitStep ? 0.30D : 0.45D;
            double correctionX = Mth.clamp(waypointDx * correctionScale, -correctionLimit, correctionLimit);
            double correctionZ = Mth.clamp(waypointDz * correctionScale, -correctionLimit, correctionLimit);
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x * velocityBlend + correctionX, velocity.y, velocity.z * velocityBlend + correctionZ);
            noteControllerActivity(now);
        }

        if (client.options != null) {
            if (client.options.keyUp != null) {
                client.options.keyUp.setDown(((miningAdvanceStep || ascentCommitStep) && waypointHorizontalDistance > 0.01D)
                    || (!verticalDropStep
                    && !pillarStep
                    && !blockedTowardWaypoint
                    && !breakRequiredStep
                    && !turnInPlace
                    && (climbNode || !applyCountermovement)));
            }
            if (client.options.keySprint != null) {
                client.options.keySprint.setDown(segmentType == FollowSegmentType.GROUND
                    && !pillarStep
                    && !blockedTowardWaypoint
                    && !breakRequiredStep
                    && !placeRequiredStep
                    && !interactableStep
                    && !nearFinalGoal
                    && !applyCountermovement
                    && jumpYawError <= SPRINT_ALIGNMENT_DEGREES
                    && player.onGround()
                    && waypointHorizontalDistance > 1.75D);
            }
            if (client.options.keyDown != null) {
                client.options.keyDown.setDown(segmentType == FollowSegmentType.GROUND && applyCountermovement && forwardVelocity > COUNTERMOVEMENT_SPEED);
            }
            if (client.options.keyLeft != null) {
                client.options.keyLeft.setDown(segmentType == FollowSegmentType.GROUND && applyCountermovement && lateralVelocity < -COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.keyRight != null) {
                client.options.keyRight.setDown(segmentType == FollowSegmentType.GROUND && applyCountermovement && lateralVelocity > COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.keyJump != null) {
                boolean swimUp = isSwimPrimitive(plannedPrimitive)
                    || player.isUnderWater()
                    || pathPlanner.isWaterNode(world, playerFootPos)
                    || pathPlanner.isWaterNode(world, waypoint);
                client.options.keyJump.setDown(!verticalDropStep
                    && !pillarStep
                    && ((swimUp && waypoint.getY() >= playerFootPos.getY())
                    || climbUp
                    || miningAdvanceJumpStep));
            }
            if (client.options.keyShift != null) {
                client.options.keyShift.setDown(!verticalDropStep && climbDown);
            }
        }

        synchronized (this) {
            if (distanceSq + PROGRESS_EPSILON_SQ < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                lastProgressAtMs = now;
            }
        }
        noteControllerProgress(now, waypointHorizontalDistanceSq);

        long millisSinceProgress;
        long millisSinceMovement;
        long millisSinceDistanceChange;
        boolean busyExcavating;
        synchronized (this) {
            millisSinceProgress = now - lastProgressAtMs;
            millisSinceMovement = now - lastMovementAtMs;
            millisSinceDistanceChange = now - lastDistanceCheckpointAtMs;
            boolean activeEscapeController = controllerMode == ControllerMode.ESCAPE_HOLE
                || controllerMode == ControllerMode.RECOVER_ESCAPE
                || controllerMode == ControllerMode.PILLAR
                || controllerMode == ControllerMode.RECOVER_PILLAR;
            busyExcavating = activeEscapeController
                && (hasCommittedEscapeWorkLocked(now) || isActiveEscapeBreakTargetLocked());
        }

        long millisSinceJump;
        boolean hasCommittedJump;
        synchronized (this) {
            millisSinceJump = now - lastJumpAtMs;
            hasCommittedJump = committedJumpWaypoint != null;
        }
        boolean wantsJump = segmentType == FollowSegmentType.GROUND
            && player.onGround()
            && !hasCommittedJump
            && (miningAdvanceJumpStep || millisSinceJump >= JUMP_RETRY_COOLDOWN_MS)
            && !breakRequiredStep
            && !placeRequiredStep
            && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES
            && (isJumpPrimitive(plannedPrimitive)
            || miningAdvanceJumpStep
            || (plannedPrimitive == null && (!interactableStep && waypoint.getY() > playerFootPos.getY()))
            || (plannedPrimitive == null && jumpUpOpportunity && !interactableStep));
        if (wantsJump) {
            int jumpAttemptsAtWaypoint;
            synchronized (this) {
                if (waypoint.equals(lastJumpAttemptWaypoint)) {
                    jumpAttemptsAtWaypoint = repeatedJumpAttempts;
                } else {
                    lastJumpAttemptWaypoint = waypoint.immutable();
                    repeatedJumpAttempts = 0;
                    jumpAttemptsAtWaypoint = 0;
                }
            }
            if (jumpAttemptsAtWaypoint >= 3) {
                releaseMovementKeys(client);
                pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "jump retry limit", "repeated jump failure");
                synchronized (this) {
                    lastJumpAtMs = now;
                    repeatedJumpAttempts = 0;
                    lastJumpAttemptWaypoint = null;
                }
                return true;
            }
            boolean canJump = miningAdvanceJumpStep
                ? pathPlanner.canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)
                : pathPlanner.canAttemptJump(world, playerFootPos, waypoint);
            if (canJump) {
                if (!desiredDirection.equals(Vec3.ZERO)) {
                    Vec3 velocity = player.getDeltaMovement();
                    player.setDeltaMovement(
                        velocity.x + desiredDirection.x * 0.12D,
                        velocity.y,
                        velocity.z + desiredDirection.z * 0.12D
                    );
                }
                if (client.options != null && client.options.keyUp != null) {
                    client.options.keyUp.setDown(true);
                }
                player.jumpFromGround();
                synchronized (this) {
                    lastJumpAtMs = now;
                    committedJumpWaypoint = waypoint.immutable();
                    committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    lastJumpAttemptWaypoint = waypoint.immutable();
                    repeatedJumpAttempts++;
                }
            } else {
                releaseMovementKeys(client);
                if (miningAdvanceJumpStep) {
                    pathPlanner.rememberFailedBreak(playerFootPos, waypoint, now);
                    pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                } else {
                    pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                }
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "blocked jump", "ceiling blocked");
                synchronized (this) {
                    lastJumpAtMs = now;
                    lastJumpAttemptWaypoint = waypoint.immutable();
                    repeatedJumpAttempts++;
                }
                return true;
            }
        }

        long segmentIdleMs = followSegmentIdleMs(now);
        if (segmentType == FollowSegmentType.GROUND) {
            boolean wallPushStall = !busyExcavating
                && !jumpExecutionLocked
                && !routeStabilizing
                && player.onGround()
                && !breakRequiredStep
                && !placeRequiredStep
                && !interactableStep
                && committedJumpWaypoint == null
                && !miningAdvanceJumpStep
                && (forwardVelocity > 0.02D || blockedTowardWaypoint)
                && waypointHorizontalDistance > 0.6D
                && blockedTowardWaypoint
                && (segmentIdleMs > WALL_PUSH_REDIRECT_MS || millisSinceMovement > WALL_PUSH_REDIRECT_MS);
            if (wallPushStall) {
                if (simpleMovementStep && routeCommitActive) {
                    noteControllerActivity(now);
                    return false;
                }
                releaseMovementKeys(client);
                if (simpleMovementStep) {
                    redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment wall redirect", "front blocked");
                } else {
                    rewindCurrentPathIndex(playerFootPos, waypoint);
                    recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment wall redirect", "front blocked");
                }
                return true;
            }
        }

        if (!jumpExecutionLocked && !routeStabilizing && !miningAdvanceJumpStep && millisSinceDistanceChange > DISTANCE_STALL_REDIRECT_MS) {
            if (simpleMovementStep && routeCommitActive) {
                noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (simpleMovementStep) {
                redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "distance stall redirect", "goal distance stalled");
            } else {
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "distance stall redirect", "goal distance stalled");
            }
            return true;
        }

        long segmentStallWindow = switch (segmentType) {
            case GROUND -> STANDSTILL_REDIRECT_MS;
            case CLIMB -> 1200L;
            case DROP -> 1100L;
        };
        if (!busyExcavating
            && !jumpExecutionLocked
            && !routeStabilizing
            && !miningAdvanceJumpStep
            && (segmentIdleMs > segmentStallWindow || millisSinceMovement > segmentStallWindow + 250L)) {
            if (simpleMovementStep && routeCommitActive && segmentType == FollowSegmentType.GROUND) {
                noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (simpleMovementStep && segmentType == FollowSegmentType.GROUND) {
                redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment redirect", segmentType.name().toLowerCase());
            } else {
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment redirect", segmentType.name().toLowerCase());
            }
            return true;
        }

        if (((!busyExcavating && millisSinceProgress > STUCK_TIMEOUT_MS) || (busyExcavating && millisSinceProgress > STUCK_TIMEOUT_MS * 2L))
            && !routeStabilizing
            && !miningAdvanceJumpStep) {
            if (simpleMovementStep && routeCommitActive && segmentType == FollowSegmentType.GROUND) {
                noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (breakRequiredStep || miningAdvanceStep) {
                pathPlanner.rememberFailedBreak(playerFootPos, waypoint, now);
            } else {
                pathPlanner.rememberFailedMove(playerFootPos, waypoint, now);
            }
            if (simpleMovementStep && segmentType == FollowSegmentType.GROUND) {
                redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment timeout", "no progress");
            } else {
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment timeout", "no progress");
            }
            return true;
        }

        return false;
    }

    private boolean acceptCommittedJumpLandingLocked(Level world, BlockPos playerFootPos, BlockPos jumpTarget) {
        if (playerFootPos == null || jumpTarget == null) {
            return false;
        }
        if (playerFootPos.equals(jumpTarget)) {
            return true;
        }
        if (playerFootPos.getY() < jumpTarget.getY() - 1) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, jumpTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(playerFootPos.getY() - jumpTarget.getY()) <= 1) {
            return true;
        }
        if (currentPath.isEmpty()) {
            return false;
        }
        int startIndex = Math.max(furthestVisitedPathIndex, Math.max(0, pathIndex - 1));
        int endIndex = Math.min(currentPath.size() - 1, Math.max(pathIndex, startIndex) + 5);
        for (int i = startIndex; i <= endIndex; i++) {
            BlockPos step = currentPath.get(i);
            if (step == null) {
                continue;
            }
            double stepDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, step);
            int verticalDelta = Math.abs(playerFootPos.getY() - step.getY());
            boolean exactEnough = stepDistanceSq <= WAYPOINT_REACHED_DISTANCE_SQ && verticalDelta <= 1;
            boolean nearEnough = stepDistanceSq <= WAYPOINT_NEAR_DISTANCE_SQ && verticalDelta <= 1;
            if (!exactEnough && !nearEnough) {
                continue;
            }
            return commitPathIndexLocked(world, i, !exactEnough, System.currentTimeMillis(), "advance:jump_landing=" + i);
        }
        return false;
    }

    private boolean acceptCommittedDropLandingLocked(Level world, BlockPos playerFootPos, BlockPos dropTarget) {
        if (playerFootPos == null || dropTarget == null) {
            return false;
        }
        if (playerFootPos.equals(dropTarget)) {
            return true;
        }
        if (playerFootPos.getY() > dropTarget.getY()) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, dropTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(playerFootPos.getY() - dropTarget.getY()) <= 1) {
            return true;
        }
        if (currentPath.isEmpty()) {
            return false;
        }
        int startIndex = Math.max(furthestVisitedPathIndex, Math.max(0, pathIndex - 1));
        int endIndex = Math.min(currentPath.size() - 1, Math.max(pathIndex, startIndex) + 4);
        for (int i = startIndex; i <= endIndex; i++) {
            BlockPos step = currentPath.get(i);
            if (step == null || step.getY() > playerFootPos.getY()) {
                continue;
            }
            double stepDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, step);
            int verticalDelta = Math.abs(playerFootPos.getY() - step.getY());
            if (stepDistanceSq > WAYPOINT_NEAR_DISTANCE_SQ || verticalDelta > 1) {
                continue;
            }
            return commitPathIndexLocked(world, i, true, System.currentTimeMillis(), "advance:drop_landing=" + i);
        }
        return false;
    }

    private boolean commitPathIndexLocked(Level world, int newIndex, boolean nearAdvance, long now, String advanceDecision) {
        if (world == null || currentPath.isEmpty()) {
            return false;
        }
        if (newIndex < 0 || newIndex >= currentPath.size()) {
            return false;
        }
        BlockPos newWaypoint = currentPath.get(newIndex);
        if (newWaypoint == null) {
            return false;
        }
        int previousIndex = pathIndex;
        BlockPos previousWaypoint = previousIndex >= 0 && previousIndex < currentPath.size() ? currentPath.get(previousIndex) : null;
        pathIndex = newIndex;
        furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, pathIndex - (nearAdvance ? 1 : 0));
        activeWaypoint = newWaypoint;
        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
        plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
        lastWaypointAdvanceAtMs = now;
        lastProgressAtMs = now;
        routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
        lastAdvanceDecision = advanceDecision;
        appendDebugEventLocked(
            "pathIndex " + previousIndex + " -> " + pathIndex
                + " reason=" + advanceDecision
                + " oldWaypoint=" + formatDebugPos(previousWaypoint)
                + " newWaypoint=" + formatDebugPos(newWaypoint)
                + " nearAdvance=" + nearAdvance
        );
        return true;
    }

    private boolean handlePillaring(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        PlannedPrimitive plannedPrimitive;
        boolean escapePillar;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
            escapePillar = !committedEscape.isEmpty();
        }
        if (!isPillarPrimitive(plannedPrimitive) && !escapePillar) {
            return false;
        }
        BlockPos pillarTarget;
        synchronized (this) {
            pillarTarget = controllerMode == ControllerMode.PILLAR && controllerTarget != null
                ? controllerTarget.immutable()
                : waypoint.immutable();
            if (!committedEscape.isEmpty()) {
                committedEscapeTarget = pillarTarget.immutable();
                committedEscapeUntilMs = Math.max(committedEscapeUntilMs, now + TRAPPED_RECOVERY_COMMIT_MS);
            }
        }
        BlockPos pillarBase = pillarTarget.below();
        if (pillarBase.getX() != playerFootPos.getX()
            || pillarBase.getZ() != playerFootPos.getZ()
            || pillarBase.getY() < playerFootPos.getY() - 1
            || pillarBase.getY() > playerFootPos.getY()) {
            pathPlanner.rememberFailedPillar(playerFootPos, pillarTarget, now);
            return false;
        }
        if (!pathPlanner.canContinuePillarTo(world, pillarBase, pillarTarget)) {
            pathPlanner.rememberFailedPillar(playerFootPos, pillarTarget, now);
            return false;
        }
        syncPathToPillarTarget(world, pillarTarget, now);
        releaseMovementKeys(client);
        Vec3 columnCenter = Vec3.atCenterOf(pillarBase);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        double dx = columnCenter.x - currentPos.x;
        double dz = columnCenter.z - currentPos.z;
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYRot(stepAngle(player.getYRot(), targetYaw, movementYawStep()));
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());
        player.setXRot(stepAngle(player.getXRot(), 89.5F, MAX_PITCH_STEP));
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(
            velocity.x * 0.25D + Mth.clamp(dx * 0.18D, -0.08D, 0.08D),
            velocity.y,
            velocity.z * 0.25D + Mth.clamp(dz * 0.18D, -0.08D, 0.08D)
        );

        PillarPhase pillarPhase = resolvePillarPhase(world, player, pillarBase, pillarTarget, dx, dz);
        synchronized (this) {
            activePillarPhase = pillarPhase;
        }

        if (client.options != null) {
            if (client.options.keySprint != null) {
                client.options.keySprint.setDown(false);
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
            if (client.options.keyShift != null) {
                client.options.keyShift.setDown(true);
            }
            if (client.options.keyJump != null) {
                client.options.keyJump.setDown(pillarPhase == PillarPhase.ASCEND);
            }
        }

        if (pillarPhase == PillarPhase.SUPPORT_READY) {
            synchronized (this) {
                controllerUntilMs = 0L;
                lastReplanReason = "pillar support ready";
                lastStuckReason = "advance on support";
                lastPlaceTarget = pillarBase.immutable();
                lastPlaceResult = "placed";
            }
            return false;
        }
        synchronized (this) {
            lastPlaceTarget = pillarBase.immutable();
            lastPlaceResult = switch (pillarPhase) {
                case PLACE -> "ready";
                case ASCEND -> "waiting apex";
                case CENTER -> "centering";
                case SUPPORT_READY -> "placed";
            };
        }
        if (pillarPhase == PillarPhase.PLACE) {
            if (client.options != null) {
                if (client.options.keyJump != null) {
                    client.options.keyJump.setDown(false);
                }
                if (client.options.keyShift != null) {
                    client.options.keyShift.setDown(true);
                }
            }
            boolean placed = tryPlacePillarBlock(client, world, player, pillarBase, now);
            if (placed) {
                synchronized (this) {
                    lastReplanReason = "pillar place";
                    lastStuckReason = "pillaring";
                    lastJumpAtMs = now;
                }
                noteControllerActivity(now);
                return true;
            }
            pathPlanner.rememberFailedPillar(playerFootPos, pillarTarget, now);
        }
        if (pillarPhase == PillarPhase.ASCEND && player.onGround()) {
            synchronized (this) {
                lastJumpAtMs = now;
                committedJumpWaypoint = null;
                committedJumpUntilMs = 0L;
                lastReplanReason = "pillar jump";
                lastStuckReason = "pillaring";
            }
        }
        noteControllerActivity(now);
        return true;
    }

    private PillarPhase resolvePillarPhase(
        Level world,
        LocalPlayer player,
        BlockPos pillarBase,
        BlockPos pillarTarget,
        double dx,
        double dz
    ) {
        if (world == null || player == null || pillarBase == null || pillarTarget == null) {
            return PillarPhase.CENTER;
        }
        if (pathPlanner.hasCollision(world, pillarBase)) {
            return PillarPhase.SUPPORT_READY;
        }
        boolean centered = Math.abs(dx) <= 0.22D && Math.abs(dz) <= 0.22D;
        boolean airbornePlacementWindow = !player.onGround() && player.getDeltaMovement().y <= 0.45D;
        if (centered && airbornePlacementWindow) {
            return PillarPhase.PLACE;
        }
        if (player.getY() < pillarTarget.getY()) {
            return PillarPhase.ASCEND;
        }
        return PillarPhase.CENTER;
    }

    private boolean tryPlacePillarBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now
    ) {
        if (client == null || world == null || player == null || placePos == null || client.gameMode == null) {
            synchronized (this) {
                lastPlaceTarget = placePos != null ? placePos.immutable() : null;
                lastPlaceResult = "client unavailable";
            }
            return false;
        }
        if (now - lastInteractAtMs < 250L) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "cooldown";
            }
            return false;
        }
        BlockPos supportPos = placePos.below();
        if (!pathPlanner.hasCollision(world, supportPos)) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "no placeable block";
            }
            return false;
        }

        int previousSlot = PlayerInventoryBridge.getSelectedSlot(player.getInventory());
        HotbarSlotSynchronizer.selectHotbarSlot(client, hotbarSlot);

        if (client.options != null) {
            if (client.options.keyJump != null) {
                client.options.keyJump.setDown(false);
            }
        }
        applySneakState(client, true);

        BlockHitResult hit = raycastBlockFromOrientation(client, player.getYRot(), player.getXRot(), 4.5D);
        if (hit == null || !supportPos.equals(hit.getBlockPos())) {
            Vec3 hitPos = new Vec3(
                supportPos.getX() + 0.5D,
                supportPos.getY() + 0.999D,
                supportPos.getZ() + 0.5D
            );
            hit = new BlockHitResult(hitPos, Direction.UP, supportPos, false);
        }
        InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        boolean accepted = result != null && result.consumesAction();
        if (!accepted) {
            InteractionResult fallback = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            accepted = fallback != null && fallback.consumesAction();
        }
        if (accepted) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        HotbarSlotSynchronizer.selectHotbarSlot(client, previousSlot);
        applySneakState(client, true);

        boolean placedNow = pathPlanner.hasCollision(world, placePos);
        synchronized (this) {
            lastPlaceTarget = placePos.immutable();
            if (!accepted) {
                lastPlaceResult = "rejected";
            } else if (placedNow) {
                lastPlaceResult = "placed";
            } else {
                lastPlaceResult = "accepted no block";
            }
        }
        if (!accepted || !placedNow) {
            return false;
        }
        synchronized (this) {
            lastInteractAtMs = now;
        }
        return true;
    }

    private boolean handleCommittedJumpMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || client.options == null) {
            return false;
        }
        BlockPos jumpTarget;
        long jumpUntilMs;
        synchronized (this) {
            jumpTarget = committedJumpWaypoint;
            jumpUntilMs = committedJumpUntilMs;
        }
        if (jumpTarget == null) {
            return false;
        }
        if (player.onGround()) {
            synchronized (this) {
                if (acceptCommittedJumpLandingLocked(world, playerFootPos, jumpTarget)) {
                    committedJumpWaypoint = null;
                    committedJumpUntilMs = 0L;
                    lastJumpAttemptWaypoint = null;
                    repeatedJumpAttempts = 0;
                    controllerMode = ControllerMode.FOLLOW_PATH;
                    controllerTarget = null;
                    controllerUntilMs = 0L;
                    lastReplanReason = "jump landed";
                    lastStuckReason = "jump complete";
                    lastProgressAtMs = now;
                    return false;
                }
            }
            if (now > jumpUntilMs) {
                pathPlanner.rememberFailedJump(playerFootPos, jumpTarget, now);
                rewindCurrentPathIndex(playerFootPos, jumpTarget);
                recoverFromStuck(client, world, playerFootPos, jumpTarget, targetPos, Vec3.atCenterOf(playerFootPos), now, "jump redirect", "missed jump");
                return true;
            }
        }
        Vec3 targetCenter = new Vec3(jumpTarget.getX() + 0.5D, player.getY(), jumpTarget.getZ() + 0.5D);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYRot(stepAngle(player.getYRot(), targetYaw, movementYawStep()));
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());

        if (!player.onGround() && horizontalDistance > 0.0001D) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(
                velocity.x * 0.40D + (dx / horizontalDistance) * 0.11D,
                velocity.y,
                velocity.z * 0.40D + (dz / horizontalDistance) * 0.11D
            );
        }

        releaseMovementKeys(client);
        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(horizontalDistance > 0.05D);
        }
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyJump != null) {
            client.options.keyJump.setDown(false);
        }
        synchronized (this) {
            lastReplanReason = "committed jump";
            lastStuckReason = player.onGround() ? "landing jump" : "airborne";
            lastProgressAtMs = now;
        }
        noteControllerActivity(now);
        return true;
    }

    private boolean handleCommittedDropMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3 currentPos,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || client.options == null) {
            return false;
        }
        BlockPos dropTarget;
        long dropUntilMs;
        synchronized (this) {
            dropTarget = controllerTarget != null ? controllerTarget : waypoint;
            dropUntilMs = controllerUntilMs;
        }
        if (dropTarget == null) {
            return false;
        }
        if (player.onGround()) {
            synchronized (this) {
                if (acceptCommittedDropLandingLocked(world, playerFootPos, dropTarget)) {
                    controllerMode = ControllerMode.FOLLOW_PATH;
                    controllerTarget = null;
                    controllerUntilMs = 0L;
                    lastReplanReason = "drop landed";
                    lastStuckReason = "drop complete";
                    lastProgressAtMs = now;
                    return false;
                }
            }
        }

        Vec3 targetCenter = new Vec3(dropTarget.getX() + 0.5D, player.getY(), dropTarget.getZ() + 0.5D);
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
        player.setYRot(nextYaw);
        player.setYHeadRot(nextYaw);
        player.setYBodyRot(nextYaw);

        boolean blocked = player.onGround()
            && horizontalDistance > 0.2D
            && pathPlanner.isBlockedTowardWaypoint(world, playerFootPos, dropTarget);
        releaseMovementKeys(client);
        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(horizontalDistance > 0.15D && !blocked);
        }
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyJump != null) {
            client.options.keyJump.setDown(false);
        }
        if (client.options.keyShift != null) {
            client.options.keyShift.setDown(false);
        }

        synchronized (this) {
            lastReplanReason = "committed drop";
            lastStuckReason = player.onGround() ? "stepping off ledge" : "airborne descent";
            lastProgressAtMs = now;
        }
        noteControllerActivity(now);

        if (player.onGround() && blocked) {
            pathPlanner.rememberFailedDrop(playerFootPos, dropTarget, now);
            recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop blocked", "drop blocked");
            return true;
        }
        if (player.onGround() && now > dropUntilMs) {
            pathPlanner.rememberFailedDrop(playerFootPos, dropTarget, now);
            recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop redirect", "missed drop");
            return true;
        }
        return true;
    }

    private boolean handleTrappedSpaceRecovery(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (client == null
            || world == null
            || player == null
            || playerFootPos == null
            || waypoint == null
            || (!allowBlockBreaking && !allowBlockPlacing)) {
            return false;
        }
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            synchronized (this) {
                committedEscapeTarget = null;
                committedEscapeUntilMs = 0L;
                committedEscape = EscapePlan.empty();
                committedEscapePrimitiveIndex = 0;
            }
            return false;
        }
        boolean trapped = isTrappedInCrampedSpace(world, playerFootPos, waypoint);
        boolean committed = isCommittedEscapeState(now);
        if (!trapped && committed && canExitTrappedRecovery(world, playerFootPos, waypoint, now)) {
            synchronized (this) {
                committedEscapeTarget = null;
                committedEscapeUntilMs = 0L;
                committedEscape = EscapePlan.empty();
                committedEscapePrimitiveIndex = 0;
            }
            return false;
        }
        if (!trapped && !committed) {
            synchronized (this) {
                committedEscapeTarget = null;
                committedEscapeUntilMs = 0L;
                committedEscape = EscapePlan.empty();
                committedEscapePrimitiveIndex = 0;
            }
            return false;
        }

        BlockPos verticalEscapeTarget = selectVerticalEscapeTarget(world, playerFootPos, waypoint);
        if (verticalEscapeTarget != null) {
            syncPathToPillarTarget(world, verticalEscapeTarget, now);
            return handlePillaring(client, world, player, playerFootPos, verticalEscapeTarget, now);
        }

        ensureExcavationPlan(world, playerFootPos, waypoint, now);

        BlockPos breakTarget = selectTrappedSpaceBreakTarget(world, playerFootPos, waypoint, now);
        if (breakTarget == null) {
            long millisSinceMovement;
            BlockPos routeTarget;
            synchronized (this) {
                millisSinceMovement = now - lastMovementAtMs;
                routeTarget = selectCommittedEscapeRouteTarget(world, playerFootPos, now);
            }
            if (trapped && millisSinceMovement > NO_MOVEMENT_REPLAN_MS) {
                synchronized (this) {
                    if (!committedEscape.isEmpty()) {
                        pathPlanner.rememberFailedMove(playerFootPos, playerFootPos.relative(committedEscape.direction()), now);
                    }
                }
                clearExcavationPlan(now, "trapped redirect", "trapped stationary");
                ensureExcavationPlan(world, playerFootPos, waypoint, now);
                breakTarget = selectTrappedSpaceBreakTarget(world, playerFootPos, waypoint, now);
                if (breakTarget != null) {
                    return continueBreakingEscapeBlock(client, world, player, breakTarget, now);
                }
                synchronized (this) {
                    routeTarget = selectCommittedEscapeRouteTarget(world, playerFootPos, now);
                }
            }
            if (routeTarget != null) {
                continueCommittedEscapeMovement(client, world, player, playerFootPos, routeTarget, now);
                return true;
            }
            releaseMovementKeys(client);
            clearExcavationPlan(now, "trapped recovery reset", "escape reevaluation");
            return false;
        }
        return continueBreakingEscapeBlock(client, world, player, breakTarget, now);
    }

    private BlockPos selectVerticalEscapeTarget(Level world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null || !allowBlockPlacing) {
            return null;
        }
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            return null;
        }
        boolean trappedContext;
        boolean allowEscapePillar;
        synchronized (this) {
            trappedContext = isCommittedEscapeState(System.currentTimeMillis())
                || isTrappedInCrampedSpace(world, playerFootPos, waypoint);
            allowEscapePillar = isActiveEscapePillarPrimitiveLocked();
        }
        if (!trappedContext || !allowEscapePillar) {
            return null;
        }
        BlockPos immediateUp = playerFootPos.above();
        long now = System.currentTimeMillis();
        return pathPlanner.canPillarTo(world, playerFootPos, immediateUp) && !pathPlanner.isFailedPillar(playerFootPos, immediateUp, now)
            ? immediateUp.immutable()
            : null;
    }

    private boolean shouldPreferFinalApproachController(Level world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (this) {
            activeTarget = targetPos;
        }
        if (activeTarget == null || !pathPlanner.isStandable(world, activeTarget)) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(playerFootPos, activeTarget) <= 4.0D
            && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 1;
    }

    private void continueCommittedEscapeMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos routeTarget,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || routeTarget == null || client.options == null) {
            return;
        }
        boolean jumpOpportunity = pathPlanner.hasJumpUpOpportunity(world, playerFootPos, routeTarget);
        BlockPos jumpTarget = jumpOpportunity ? pathPlanner.resolveJumpUpApproachTarget(world, playerFootPos, routeTarget) : routeTarget;
        Vec3 frontCenter = new Vec3(jumpTarget.getX() + 0.5D, player.getY(), jumpTarget.getZ() + 0.5D);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        double dx = frontCenter.x - currentPos.x;
        double dz = frontCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float nextYaw = stepAngle(player.getYRot(), targetYaw, movementYawStep());
        float jumpYawError = Math.abs(Mth.wrapDegrees(targetYaw - nextYaw));
        player.setYRot(nextYaw);
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());

        boolean blocked = pathPlanner.isBlockedTowardWaypoint(world, playerFootPos, routeTarget) && !jumpOpportunity;
        releaseMovementKeys(client);
        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(!blocked && horizontalDistance > 0.2D || jumpOpportunity);
        }
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyJump != null) {
            boolean canHop = player.onGround()
                && jumpOpportunity
                && horizontalDistance <= 1.6D
                && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES;
            client.options.keyJump.setDown(false);
            if (canHop) {
                player.jumpFromGround();
                synchronized (this) {
                    lastJumpAtMs = now;
                    committedJumpWaypoint = jumpTarget.immutable();
                    committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    lastReplanReason = "escape primitive jump";
                    lastStuckReason = "jumping out";
                }
                noteControllerActivity(now);
                return;
            }
        }

        synchronized (this) {
            lastProgressAtMs = now;
            lastReplanReason = "escape primitive move";
            lastStuckReason = blocked ? "escape step blocked" : "following excavation route";
        }
        noteControllerActivity(now);
    }

    private void ensureExcavationPlan(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        synchronized (this) {
            boolean rebuild = committedEscape.isEmpty() || committedEscapePrimitiveIndex >= committedEscape.primitives().size();
            if (rebuild) {
                ExcavationPlan plan = buildExcavationPlan(world, playerFootPos, waypoint, now);
                if (plan != null) {
                    committedEscape = plan.escapePlan();
                    committedEscapePrimitiveIndex = 0;
                    committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                    lastReplanReason = "escape plan";
                    lastStuckReason = "committed excavation";
                }
            } else if (!committedEscape.isEmpty()) {
                committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
            }
        }
    }

    private void clearExcavationPlan(long now, String replanReason, String stuckReason) {
        synchronized (this) {
            committedEscapeTarget = null;
            committedEscapeUntilMs = 0L;
            committedEscape = EscapePlan.empty();
            committedEscapePrimitiveIndex = 0;
            lastReplanReason = replanReason;
            lastStuckReason = stuckReason;
            controllerProgressAtMs = now;
        }
    }

    private BlockPos selectCommittedEscapeRouteTarget(Level world, BlockPos playerFootPos, long now) {
        synchronized (this) {
            if (committedEscape.isEmpty()) {
                return null;
            }
            while (committedEscapePrimitiveIndex < committedEscape.primitives().size()) {
                EscapePrimitive primitive = committedEscape.primitives().get(committedEscapePrimitiveIndex);
                if (primitive == null || primitive.target() == null) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                BlockPos step = primitive.target();
                if (primitive.type() != EscapePrimitiveType.MOVE) {
                    return null;
                }
                if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= 0.25D && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                if (pathPlanner.isFailedNode(step, now) || requiresBreakingForWaypoint(world, step) || pathPlanner.needsPlacedSupport(world, step)) {
                    return null;
                }
                if (isWaypointActionable(world, step)) {
                    committedEscapeTarget = step.immutable();
                    return committedEscapeTarget;
                }
                return null;
            }
            return null;
        }
    }

    private boolean isCommittedEscapeState(long now) {
        synchronized (this) {
            return !committedEscape.isEmpty() && committedEscapeUntilMs > now;
        }
    }

    private boolean isCommittedLocalEscapeChain(long now) {
        synchronized (this) {
            return !committedEscape.isEmpty()
                && committedEscapeUntilMs > now
                && committedEscapePrimitiveIndex < committedEscape.primitives().size();
        }
    }

    private boolean canExitTrappedRecovery(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (!pathPlanner.canOccupy(world, playerFootPos.above())) {
            return false;
        }
        return countPhysicalWalkNeighbors(world, playerFootPos) >= 2;
    }

    private BlockPos selectBreakTarget(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive) {
        if (world == null || waypoint == null) {
            return null;
        }
        BlockPos currentBreakTarget = null;
        synchronized (this) {
            currentBreakTarget = activeBreakTarget;
        }
        List<BlockPos> breakTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : List.of();
        if (breakTargets == null || breakTargets.isEmpty()) {
            return null;
        }
        synchronized (this) {
            if (activeBreakTarget != null && breakTargets.contains(activeBreakTarget) && pathPlanner.isBreakableForNavigator(world, activeBreakTarget)) {
                return activeBreakTarget;
            }
        }
        BlockPos pendingTarget = firstPendingBreakTarget(world, breakTargets);
        if (pendingTarget != null && isPlannedBreakTargetReachable(playerFootPos, pendingTarget)) {
            return pendingTarget;
        }
        for (BlockPos candidate : breakTargets) {
            if (!pathPlanner.isBreakableForNavigator(world, candidate)) {
                continue;
            }
            if (!isPlannedBreakTargetReachable(playerFootPos, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private BlockPos selectBreakTarget(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        BlockPos target = selectBreakTarget((Level) world, playerFootPos, waypoint, plannedPrimitive);
        if (target == null) {
            return null;
        }
        if (!canBreakTargetNow(world, player, target)) {
            synchronized (this) {
                if (target.equals(activeBreakTarget)) {
                    activeBreakTarget = null;
                }
            }
            return null;
        }
        return target;
    }

    private BlockPos firstPendingBreakTarget(Level world, List<BlockPos> breakTargets) {
        if (world == null || breakTargets == null || breakTargets.isEmpty()) {
            return null;
        }
        for (BlockPos candidate : breakTargets) {
            if (candidate != null && pathPlanner.isBreakableForNavigator(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPlannedBreakTargetReachable(BlockPos playerFootPos, BlockPos target) {
        if (playerFootPos == null || target == null) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(playerFootPos, target) <= 9.0D
            && Math.abs(playerFootPos.getY() - target.getY()) <= 3;
    }

    private boolean isTrappedInCrampedSpace(Level world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        int physicalWalkNeighbors = countPhysicalWalkNeighbors(world, playerFootPos);
        boolean boxedIn = physicalWalkNeighbors <= 1;
        if (boxedIn) {
            return true;
        }
        boolean lowPlannerMobility = pathPlanner.countDirectWalkNeighbors(world, playerFootPos, playerFootPos, waypoint, System.currentTimeMillis()) <= 1;
        return lowPlannerMobility && physicalWalkNeighbors <= 2;
    }

    private int countPhysicalWalkNeighbors(Level world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null) {
            return 0;
        }
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = playerFootPos.relative(direction);
            if (!isPhysicalWalkNeighbor(world, candidate)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean isPhysicalWalkNeighbor(Level world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        if (!pathPlanner.canOccupy(world, footPos) || !pathPlanner.canOccupy(world, footPos.above())) {
            return false;
        }
        if (pathPlanner.resolveSupportSurfaceY(world, footPos).isEmpty() && !pathPlanner.isWaterNode(world, footPos)) {
            return false;
        }
        return !pathPlanner.isHardDanger(world, footPos);
    }

    private BlockPos selectTrappedSpaceBreakTarget(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return null;
        }

        synchronized (this) {
            while (committedEscapePrimitiveIndex < committedEscape.primitives().size()) {
                EscapePrimitive primitive = committedEscape.primitives().get(committedEscapePrimitiveIndex);
                if (primitive == null || primitive.target() == null) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                if (primitive.type() != EscapePrimitiveType.MINE) {
                    return null;
                }
                BlockPos planned = primitive.target();
                if (!isReachableTrappedBreakTarget(playerFootPos, committedEscape.direction(), planned)) {
                    return null;
                }
                if (pathPlanner.canOccupy(world, planned)) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                if (pathPlanner.isBreakableForNavigator(world, planned)) {
                    committedEscapeTarget = planned.immutable();
                    return committedEscapeTarget;
                }
                return null;
            }
            if (committedEscapeTarget != null && committedEscapeUntilMs <= now) {
                committedEscapeTarget = null;
                committedEscapeUntilMs = 0L;
            }
        }
        return null;
    }

    private boolean isReachableTrappedBreakTarget(BlockPos playerFootPos, Direction direction, BlockPos target) {
        if (playerFootPos == null || target == null) {
            return false;
        }
        int dx = target.getX() - playerFootPos.getX();
        int dy = target.getY() - playerFootPos.getY();
        int dz = target.getZ() - playerFootPos.getZ();
        if (Math.abs(dx) + Math.abs(dz) == 0) {
            return dy >= 1 && dy <= 2;
        }
        if (direction == null || direction.getAxis().isVertical()) {
            return false;
        }
        if (dx != direction.getStepX() || dz != direction.getStepZ()) {
            return false;
        }
        return dy >= 0 && dy <= 2;
    }

    private Direction chooseEscapeDirection(Level world, BlockPos current, BlockPos goal, long now) {
        if (world == null || current == null || goal == null) {
            return null;
        }
        Direction bestDirection = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Double score = scoreEscapeDirection(world, current, goal, direction, now);
            if (score == null || score >= bestScore) {
                continue;
            }
            bestScore = score;
            bestDirection = direction;
        }
        return bestDirection;
    }

    private ExcavationPlan buildExcavationPlan(Level world, BlockPos current, BlockPos goal, long now) {
        if (world == null || current == null || goal == null) {
            return null;
        }
        Direction direction = chooseEscapeDirection(world, current, goal, now);
        if (direction == null) {
            return null;
        }
        StairEscapePlan stairPlan = buildStairEscapePlan(world, current, goal, direction, now);
        if (stairPlan == null || stairPlan.route().isEmpty()) {
            return null;
        }
        return new ExcavationPlan(stairPlan.escapePlan());
    }

    private StairEscapePlan buildStairEscapePlan(Level world, BlockPos current, BlockPos goal, Direction direction, long now) {
        if (world == null || current == null || goal == null || direction == null || direction.getAxis().isVertical()) {
            return null;
        }
        List<BlockPos> route = new ArrayList<>();
        List<EscapePrimitive> primitives = new ArrayList<>();
        int stepX = direction.getStepX();
        int stepZ = direction.getStepZ();
        BlockPos cursor = current;

        addThreeHighExcavationBreaks(primitives, world, cursor);

        for (int distance = 1; distance <= 8; distance++) {
            BlockPos flat = cursor.offset(stepX, 0, stepZ);
            BlockPos up = cursor.offset(stepX, 1, stepZ);
            BlockPos chosen = null;

            boolean canFlat = isValidEscapeStepCandidate(world, cursor, flat, now) && hasExcavatableThreeHighClearance(world, flat);
            boolean canUp = isValidEscapeStepCandidate(world, cursor, up, now) && canExcavateEscapeJumpCorridor(world, cursor, up);

            boolean preferUp = goal.getY() > cursor.getY() || !canFlat;
            if (preferUp && canUp) {
                chosen = up;
            } else if (canFlat) {
                chosen = flat;
            } else if (canUp) {
                chosen = up;
            }

            if (chosen == null) {
                return null;
            }

            route.add(chosen.immutable());
            if (chosen.getY() > cursor.getY()) {
                addAscendingExcavationBreaks(primitives, world, cursor, chosen);
            } else {
                addThreeHighExcavationBreaks(primitives, world, chosen);
            }
            addEscapePrimitive(primitives, EscapePrimitiveType.MOVE, chosen);

            cursor = chosen;
            if (isEscapeLipReached(world, current, cursor, goal, now)) {
                break;
            }
        }

        return route.isEmpty() ? null : new StairEscapePlan(new EscapePlan(direction, List.copyOf(route), List.copyOf(primitives)));
    }

    private boolean isEscapeLipReached(Level world, BlockPos start, BlockPos cursor, BlockPos goal, long now) {
        if (world == null || start == null || cursor == null || goal == null) {
            return false;
        }
        if (!hasThreeHighExcavationClearance(world, cursor)) {
            return false;
        }
        if (pathPlanner.countDirectWalkNeighbors(world, cursor, cursor, goal, now) < 2) {
            return false;
        }
        int targetLipY = Math.max(start.getY() + 1, goal.getY() - 1);
        return cursor.getY() >= targetLipY;
    }

    private boolean isValidEscapeStepCandidate(Level world, BlockPos from, BlockPos candidate, long now) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        return pathPlanner.isChunkLoaded(world, candidate)
            && !pathPlanner.isFailedNode(candidate, now)
            && !pathPlanner.isFailedEdge(from, candidate, now)
            && !pathPlanner.isHardDanger(world, candidate)
            && !pathPlanner.needsPlacedSupport(world, candidate);
    }

    private boolean hasExcavatableThreeHighClearance(Level world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return pathPlanner.isExcavationClearable(world, foot)
            && pathPlanner.isExcavationClearable(world, foot.above())
            && pathPlanner.isExcavationClearable(world, foot.above(2));
    }

    private void addThreeHighExcavationBreaks(List<EscapePrimitive> plan, Level world, BlockPos foot) {
        if (plan == null || world == null || foot == null) {
            return;
        }
        addOrderedExcavationBreaks(plan, world, List.of(foot, foot.above(), foot.above(2)));
    }

    private void addAscendingExcavationBreaks(List<EscapePrimitive> plan, Level world, BlockPos from, BlockPos to) {
        if (plan == null || world == null || from == null || to == null) {
            return;
        }
        addOrderedExcavationBreaks(plan, world, List.of(
            from.above(),
            from.above(2),
            to,
            to.above(),
            to.above(2)
        ));
    }

    private boolean canExcavateEscapeJumpCorridor(Level world, BlockPos from, BlockPos to) {
        return pathPlanner.canExcavateJumpCorridor(world, from, to);
    }

    private void addOrderedExcavationBreaks(List<EscapePrimitive> plan, Level world, List<BlockPos> candidates) {
        if (plan == null || world == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (BlockPos candidate : candidates) {
            if (candidate == null || !pathPlanner.isBreakableForNavigator(world, candidate)) {
                continue;
            }
            addPlannedBreak(plan, world, candidate.immutable());
        }
    }

    private void addPlannedBreak(List<EscapePrimitive> plan, Level world, BlockPos pos) {
        if (plan == null || world == null || pos == null) {
            return;
        }
        if (!pathPlanner.isBreakableForNavigator(world, pos)) {
            return;
        }
        addEscapePrimitive(plan, EscapePrimitiveType.MINE, pos);
    }

    private void addEscapePrimitive(List<EscapePrimitive> plan, EscapePrimitiveType type, BlockPos pos) {
        if (plan == null || type == null || pos == null) {
            return;
        }
        EscapePrimitive primitive = new EscapePrimitive(type, pos.immutable());
        if (!plan.contains(primitive)) {
            plan.add(primitive);
        }
    }

    private boolean hasThreeHighExcavationClearance(Level world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return pathPlanner.canOccupy(world, foot)
            && pathPlanner.canOccupy(world, foot.above())
            && pathPlanner.canOccupy(world, foot.above(2));
    }

    private Double scoreEscapeDirection(Level world, BlockPos current, BlockPos goal, Direction direction, long now) {
        if (world == null || current == null || goal == null || direction == null || direction.getAxis().isVertical()) {
            return null;
        }
        int stepX = direction.getStepX();
        int stepZ = direction.getStepZ();

        double score = 0.0D;
        int consecutiveOpen = 0;
        boolean foundExit = false;

        StairEscapePlan plan = buildStairEscapePlan(world, current, goal, direction, now);
        if (plan == null || plan.route().isEmpty()) {
            return null;
        }
        BlockPos cursor = current;
        for (int i = 0; i < plan.route().size(); i++) {
            BlockPos step = plan.route().get(i);
            if (step == null) {
                return null;
            }
            double segmentScore = 0.0D;
            int requiredBreaks = 0;
            if (step.getY() > cursor.getY()) {
                for (BlockPos candidate : List.of(cursor.above(), cursor.above(2), step, step.above(), step.above(2))) {
                    if (!pathPlanner.canOccupy(world, candidate)) {
                        if (!pathPlanner.isBreakableForNavigator(world, candidate)) {
                            return null;
                        }
                        segmentScore += pathPlanner.breakPenalty(world, candidate);
                        requiredBreaks++;
                    }
                }
            } else {
                for (BlockPos candidate : List.of(step, step.above(), step.above(2))) {
                    if (!pathPlanner.canOccupy(world, candidate)) {
                        if (!pathPlanner.isBreakableForNavigator(world, candidate)) {
                            return null;
                        }
                        segmentScore += pathPlanner.breakPenalty(world, candidate);
                        requiredBreaks++;
                    }
                }
            }

            if (!pathPlanner.hasCollision(world, step.below()) && !pathPlanner.isWaterNode(world, step)) {
                if (!allowBlockPlacing || !pathPlanner.canPlaceSupportAt(world, step.below())) {
                    return null;
                }
                segmentScore += PLACE_MOVE_PENALTY * 3.5D;
            }

            if (requiredBreaks == 0 && hasThreeHighExcavationClearance(world, step)) {
                score -= 4.0D + ((i + 1) * 1.5D);
                consecutiveOpen++;
            } else {
                score += segmentScore + ((i + 1) * 0.65D);
                consecutiveOpen = 0;
            }

            if (consecutiveOpen >= 2
                && hasThreeHighExcavationClearance(world, step)
                && pathPlanner.countDirectWalkNeighbors(world, step, step, goal, now) >= 2) {
                score -= 14.0D + ((i + 1) * 2.0D);
                foundExit = true;
                break;
            }
            cursor = step;
        }
        if (!foundExit) {
            score += 18.0D;
        }
        int targetDistance = Math.abs(goal.getX() - (current.getX() + stepX))
            + Math.abs(goal.getZ() - (current.getZ() + stepZ));
        score += targetDistance * 0.03D;
        return score;
    }

    private void addBreakCandidate(List<BlockPos> candidates, BlockPos candidate) {
        if (candidates != null && candidate != null) {
            candidates.add(candidate);
        }
    }

    private boolean continueBreakingBlock(Minecraft client, LocalPlayer player, BlockPos target, long now) {
        if (client == null || client.gameMode == null || client.level == null || player == null || target == null) {
            return false;
        }
        BlockState targetState = client.level.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            return false;
        }
        BlockPos waypoint;
        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            waypoint = activeWaypoint;
            plannedPrimitive = activePlannedPrimitive;
        }
        if (waypoint == null) {
            return false;
        }
        List<BlockPos> requiredTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : pathPlanner.getRequiredBreakTargets(client.level, waypoint);
        return continueBreakingRequiredTarget(client, player, target, requiredTargets, now);
    }

    private boolean continueBreakingRequiredTarget(
        Minecraft client,
        LocalPlayer player,
        BlockPos target,
        List<BlockPos> requiredTargets,
        long now
    ) {
        if (client == null || client.gameMode == null || client.level == null || player == null || target == null) {
            return false;
        }
        if (requiredTargets == null || !requiredTargets.contains(target)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        BlockPos pendingTarget = firstPendingBreakTarget(client.level, requiredTargets);
        if (pendingTarget == null || !target.equals(pendingTarget)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        BlockState targetState = client.level.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        BreakTargeting targeting = resolveBreakTargeting(client.level, player, target);
        if (targeting == null) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        equipBestMiningTool(player, targetState);
        releaseMovementKeys(client);
        applyWaterInteractionStance(client, client.level, player, target);
        lookAtPosition(player, targeting.hitPos());
        Direction face = targeting.face();
        boolean startingNewTarget;
        synchronized (this) {
            startingNewTarget = activeBreakTarget == null || !activeBreakTarget.equals(target);
            activeBreakTarget = target.immutable();
            if (startingNewTarget) {
                lastInteractAtMs = now;
            }
        }
        if (startingNewTarget) {
            client.gameMode.startDestroyBlock(target, face);
        }
        client.gameMode.continueDestroyBlock(target, face);
        player.swing(InteractionHand.MAIN_HAND);
        noteControllerActivity(now);
        return true;
    }

    private void syncPathToPillarTarget(ClientLevel world, BlockPos pillarTarget, long now) {
        if (world == null || pillarTarget == null) {
            return;
        }

        BlockPos navTarget;
        synchronized (this) {
            navTarget = targetPos;
            if (controllerMode == ControllerMode.PILLAR
                && controllerTarget != null
                && pillarTarget.equals(controllerTarget)
                && !currentPath.isEmpty()
                && pathIndex >= 0
                && pathIndex < currentPath.size()
                && pillarTarget.equals(currentPath.get(pathIndex))
                && routeCommitUntilMs > now) {
                activeWaypoint = pillarTarget.immutable();
                return;
            }
        }

        List<BlockPos> syncedPath = List.of(pillarTarget.immutable());
        PathComputation continuation = null;
        if (navTarget != null) {
            continuation = findPath(world, pillarTarget, navTarget);
            if (continuation != null && !continuation.path().isEmpty()) {
                List<BlockPos> continuationPath = continuation.path();
                if (pillarTarget.equals(continuationPath.get(0))) {
                    syncedPath = List.copyOf(continuationPath);
                } else {
                    List<BlockPos> combined = new ArrayList<>(continuationPath.size() + 1);
                    combined.add(pillarTarget.immutable());
                    combined.addAll(continuationPath);
                    syncedPath = List.copyOf(combined);
                }
            }
        }

        synchronized (this) {
            currentPath = syncedPath;
            pathIndex = 0;
            furthestVisitedPathIndex = 0;
            activeWaypoint = pillarTarget.immutable();
            committedPathGoalPos = pillarTarget.immutable();
            plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
            rebuildCurrentPlanLocked(world);
            if (!isPillarPrimitive(activePlannedPrimitive)) {
                activePlannedPrimitive = createPrimitiveSnapshot(world, activeWaypoint.below(), activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), activeWaypoint.below());
            }
            lastPlanAtMs = now;
            routeCommitUntilMs = Math.max(routeCommitUntilMs, now + 1400L);
            lastReplanReason = "pillar sync";
            if (continuation != null && !continuation.path().isEmpty()) {
                candidatePaths = continuation.candidatePaths();
                candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                goalMode = shouldTrackResolvedPlanningGoal(navTarget, continuation.resolvedGoalPos(), continuation.goalMode())
                    ? continuation.goalMode()
                    : GoalMode.EXACT;
                resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? continuation.resolvedGoalPos() : navTarget.immutable();
                committedPathGoalPos = continuation.resolvedGoalPos() != null ? continuation.resolvedGoalPos().immutable() : resolvedGoalPos;
            }
        }
    }

    private boolean continueBreakingEscapeBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos target,
        long now
    ) {
        if (client == null || world == null || client.gameMode == null || player == null || target == null) {
            return false;
        }
        BlockState targetState = world.getBlockState(target);
        if (targetState == null || targetState.isAir() || !pathPlanner.isBreakableForNavigator(world, target)) {
            return false;
        }
        BreakTargeting targeting = resolveBreakTargeting(world, player, target);
        if (targeting == null) {
            return false;
        }
        equipBestMiningTool(player, targetState);
        releaseMovementKeys(client);
        applyWaterInteractionStance(client, world, player, target);
        lookAtPosition(player, targeting.hitPos());
        Direction face = targeting.face();
        boolean startingNewTarget;
        synchronized (this) {
            startingNewTarget = activeBreakTarget == null || !activeBreakTarget.equals(target);
            activeBreakTarget = target.immutable();
            if (startingNewTarget) {
                committedEscapeTarget = target.immutable();
                committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                lastInteractAtMs = now;
                lastReplanReason = "escape primitive mine";
                lastStuckReason = "excavating escape";
            }
        }
        if (startingNewTarget) {
            client.gameMode.startDestroyBlock(target, face);
        }
        client.gameMode.continueDestroyBlock(target, face);
        player.swing(InteractionHand.MAIN_HAND);
        noteControllerActivity(now);
        return true;
    }

    private boolean shouldSuppressMiningNearGoal(Level world, LocalPlayer player, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (this) {
            activeTarget = targetPos;
        }
        if (activeTarget == null) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, activeTarget) > 2.25D || Math.abs(playerFootPos.getY() - activeTarget.getY()) > 1) {
            return false;
        }
        if (!activeTarget.equals(waypoint) && !activeTarget.above().equals(waypoint)) {
            return false;
        }
        return pathPlanner.hasReachedExactGoal(playerFootPos, activeTarget);
    }

    private boolean shouldForceFinalApproach(Level world, BlockPos playerFootPos, BlockPos target) {
        if (world == null || playerFootPos == null || target == null) {
            return false;
        }
        return pathPlanner.isStandable(world, target)
            && pathPlanner.horizontalDistanceSq(playerFootPos, target) <= 4.0D
            && Math.abs(playerFootPos.getY() - target.getY()) <= 1;
    }

    private boolean shouldBreakForWaypoint(BlockPos playerFootPos, BlockPos waypoint, BlockPos breakTarget) {
        if (playerFootPos == null || waypoint == null || breakTarget == null) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, waypoint) > 4.0D || Math.abs(waypoint.getY() - playerFootPos.getY()) > 1) {
            return isPlannedBreakTargetReachable(playerFootPos, breakTarget);
        }
        return breakTarget.equals(waypoint)
            || breakTarget.equals(waypoint.above())
            || isPlannedBreakTargetReachable(playerFootPos, breakTarget);
    }

    private boolean requiresBreakingForWaypoint(Level world, BlockPos waypoint) {
        if (world == null || waypoint == null) {
            return false;
        }
        List<BlockPos> breakTargets = pathPlanner.getRequiredBreakTargets(world, waypoint);
        return breakTargets != null && !breakTargets.isEmpty();
    }

    private boolean shouldPlaceForWaypoint(Level world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (pathPlanner.isWaterNode(world, waypoint) || pathPlanner.isWaterNode(world, playerFootPos)) {
            double horizontalDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, waypoint);
            int deltaY = waypoint.getY() - playerFootPos.getY();
            if (!pathPlanner.canOccupy(world, waypoint) || !pathPlanner.canOccupy(world, waypoint.above())) {
                return false;
            }
            return deltaY >= -1
                && deltaY <= 1
                && horizontalDistanceSq >= 0.01D
                && horizontalDistanceSq <= 2.25D;
        }
        if (pathPlanner.canPillarTo(world, playerFootPos, waypoint)) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (this) {
            activeTarget = targetPos;
        }
        if (activeTarget != null) {
            if (waypoint.equals(activeTarget) || waypoint.below().equals(activeTarget)) {
                return false;
            }
            if (pathPlanner.isStandable(world, activeTarget)
                && pathPlanner.horizontalDistanceSq(playerFootPos, activeTarget) <= 9.0D
                && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 2) {
                return false;
            }
            if (pathPlanner.isStandable(world, activeTarget)
                && pathPlanner.horizontalDistanceSq(playerFootPos, activeTarget) <= 4.0D
                && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 1) {
                return false;
            }
        }
        if (requiresBreakingForWaypoint(world, waypoint)) {
            return false;
        }
        if (pathPlanner.isTreeCanopyNode(world, waypoint)) {
            return false;
        }
        if (!pathPlanner.canOccupy(world, waypoint) || !pathPlanner.canOccupy(world, waypoint.above())) {
            return false;
        }
        if (waypoint.getY() < playerFootPos.getY()) {
            return false;
        }
        double horizontalDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, waypoint);
        if (waypoint.getY() == playerFootPos.getY() && horizontalDistanceSq < 0.01D) {
            return false;
        }
        if (horizontalDistanceSq < 0.64D || horizontalDistanceSq > 1.05D) {
            return false;
        }
        int deltaY = waypoint.getY() - playerFootPos.getY();
        if (deltaY < 0 || deltaY > 1) {
            return false;
        }
        return deltaY == 0;
    }

    private boolean isCommittedWaterPlaceState(
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos placeTarget
    ) {
        if (world == null || player == null || playerFootPos == null || waypoint == null || placeTarget == null) {
            return false;
        }
        boolean inWater = player.isInWater()
            || player.isUnderWater()
            || pathPlanner.isWaterNode(world, playerFootPos)
            || pathPlanner.isWaterNode(world, waypoint);
        if (!inWater) {
            return false;
        }
        if (pathPlanner.hasCollision(world, placeTarget) || !pathPlanner.canPlaceSupportAt(world, placeTarget)) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(playerFootPos, placeTarget.above()) <= 4.0D
            && Math.abs(playerFootPos.getY() - placeTarget.getY()) <= 2;
    }

    private void equipBestMiningTool(LocalPlayer player, BlockState targetState) {
        if (player == null || player.getInventory() == null || targetState == null) {
            return;
        }
        int bestSlot = findBestMiningHotbarSlot(player, targetState);
        if (bestSlot < 0) {
            return;
        }
        if (PlayerInventoryBridge.getSelectedSlot(player.getInventory()) != bestSlot) {
            HotbarSlotSynchronizer.selectHotbarSlot(Minecraft.getInstance(), bestSlot);
        }
    }

    private int findBestMiningHotbarSlot(LocalPlayer player, BlockState targetState) {
        if (player == null || player.getInventory() == null || targetState == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            double score = miningToolScore(stack, targetState);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private double miningToolScore(ItemStack stack, BlockState targetState) {
        if (stack == null || stack.isEmpty() || targetState == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double speed = stack.getDestroySpeed(targetState);
        double score = speed;
        if (stack.isCorrectToolForDrops(targetState)) {
            score += 100.0D;
        }
        if (stack.getItem() instanceof BlockItem) {
            score -= 8.0D;
        }
        return score;
    }

    private void lookAtPosition(LocalPlayer player, Vec3 targetPos) {
        if (player == null || targetPos == null) {
            return;
        }
        Vec3 delta = targetPos.subtract(player.getEyePosition());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(0.0001D, horizontalDistance)));
        float clampedPitch = Mth.clamp(targetPitch, -60.0F, 60.0F);
        player.setYRot(targetYaw);
        player.setYHeadRot(targetYaw);
        player.setYBodyRot(targetYaw);
        player.setXRot(clampedPitch);
    }

    private BreakTargeting resolveBreakTargeting(ClientLevel world, LocalPlayer player, BlockPos target) {
        if (world == null || player == null || target == null) {
            return null;
        }
        return resolveBlockTargeting(world, player, player.getEyePosition(), target, blockInteractionReachSquared(player));
    }

    private BreakTargeting resolveBlockTargeting(
        ClientLevel world,
        LocalPlayer player,
        Vec3 eyePos,
        BlockPos target,
        double reachSq
    ) {
        if (world == null || player == null || eyePos == null || target == null) {
            return null;
        }
        BlockState targetState = world.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            return null;
        }
        for (Vec3 hitPos : getBreakAimPoints(world, targetState, target, preferredBreakFaces(player, target))) {
            BlockHitResult hit = raycastToBreakTarget(world, player, eyePos, target, hitPos, reachSq);
            if (hit != null) {
                return new BreakTargeting(target.immutable(), hit.getDirection(), hit.getLocation());
            }
        }
        return null;
    }

    private boolean canBreakTargetNow(ClientLevel world, LocalPlayer player, BlockPos target) {
        return resolveBreakTargeting(world, player, target) != null;
    }

    private boolean canInteractWithBlockFromFoot(
        ClientLevel world,
        LocalPlayer player,
        BlockPos footPos,
        BlockPos target,
        double reachSq
    ) {
        if (world == null || player == null || footPos == null || target == null) {
            return false;
        }
        double eyeOffset = Mth.clamp(player.getEyePosition().y - player.getY(), 1.27D, 1.62D);
        Vec3 eyePos = new Vec3(footPos.getX() + 0.5D, footPos.getY() + eyeOffset, footPos.getZ() + 0.5D);
        return resolveBlockTargeting(world, player, eyePos, target, reachSq) != null;
    }

    private boolean isBlockShapeWithinReachFromFoot(
        ClientLevel world,
        LocalPlayer player,
        BlockPos footPos,
        BlockPos target,
        double reachSq
    ) {
        if (world == null || player == null || footPos == null || target == null) {
            return false;
        }
        BlockState targetState = world.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            return false;
        }
        double eyeOffset = Mth.clamp(player.getEyePosition().y - player.getY(), 1.27D, 1.62D);
        Vec3 eyePos = new Vec3(footPos.getX() + 0.5D, footPos.getY() + eyeOffset, footPos.getZ() + 0.5D);
        for (Vec3 hitPos : getBreakAimPoints(world, targetState, target, preferredBreakFaces(player, target))) {
            if (eyePos.distanceToSqr(hitPos) <= reachSq) {
                return true;
            }
        }
        return false;
    }

    private double blockInteractionReachSquared(LocalPlayer player) {
        double reach = DEFAULT_BLOCK_INTERACTION_REACH;
        if (player != null) {
            reach = Math.max(0.0D, player.blockInteractionRange());
        }
        return reach * reach;
    }

    private List<Direction> preferredBreakFaces(LocalPlayer player, BlockPos target) {
        if (player == null || target == null) {
            return List.of(Direction.UP);
        }
        Vec3 eyePos = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        Vec3 delta = center.subtract(eyePos);
        Direction primary = Direction.getApproximateNearest(delta.x, delta.y, delta.z).getOpposite();
        List<Direction> faces = new ArrayList<>(6);
        faces.add(primary);
        for (Direction face : Direction.values()) {
            if (!faces.contains(face)) {
                faces.add(face);
            }
        }
        return faces;
    }

    private List<Vec3> getBreakAimPoints(ClientLevel world, BlockState targetState, BlockPos target, List<Direction> preferredFaces) {
        if (world == null || targetState == null || target == null) {
            return List.of();
        }
        VoxelShape shape = targetState.getShape(world, target);
        if (shape == null || shape.isEmpty()) {
            shape = targetState.getCollisionShape(world, target);
        }
        List<AABB> boxes = shape == null || shape.isEmpty()
            ? List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D))
            : shape.toAabbs();
        if (boxes.isEmpty()) {
            boxes = List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }
        boxes = boxes.stream()
            .sorted(Comparator.comparingDouble(AABB::getSize).reversed())
            .toList();

        List<Vec3> points = new ArrayList<>(boxes.size() * Math.max(1, preferredFaces.size()) + boxes.size() + 1);
        for (Direction face : preferredFaces) {
            for (AABB box : boxes) {
                points.add(getBreakFaceAimPoint(target, box, face));
            }
        }
        for (AABB box : boxes) {
            points.add(worldBoxCenter(target, box));
        }
        points.add(Vec3.atCenterOf(target));
        return points;
    }

    private Vec3 getBreakFaceAimPoint(BlockPos target, AABB localBox, Direction face) {
        Vec3 center = worldBoxCenter(target, localBox);
        if (target == null || localBox == null || face == null) {
            return center;
        }
        double minX = target.getX() + localBox.minX;
        double minY = target.getY() + localBox.minY;
        double minZ = target.getZ() + localBox.minZ;
        double maxX = target.getX() + localBox.maxX;
        double maxY = target.getY() + localBox.maxY;
        double maxZ = target.getZ() + localBox.maxZ;
        double x = center.x;
        double y = center.y;
        double z = center.z;
        double epsilon;
        switch (face) {
            case EAST -> {
                epsilon = inwardEpsilon(minX, maxX);
                x = maxX - epsilon;
            }
            case WEST -> {
                epsilon = inwardEpsilon(minX, maxX);
                x = minX + epsilon;
            }
            case UP -> {
                epsilon = inwardEpsilon(minY, maxY);
                y = maxY - epsilon;
            }
            case DOWN -> {
                epsilon = inwardEpsilon(minY, maxY);
                y = minY + epsilon;
            }
            case SOUTH -> {
                epsilon = inwardEpsilon(minZ, maxZ);
                z = maxZ - epsilon;
            }
            case NORTH -> {
                epsilon = inwardEpsilon(minZ, maxZ);
                z = minZ + epsilon;
            }
        }
        return new Vec3(x, y, z);
    }

    private Vec3 worldBoxCenter(BlockPos target, AABB localBox) {
        if (target == null || localBox == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
            target.getX() + (localBox.minX + localBox.maxX) * 0.5D,
            target.getY() + (localBox.minY + localBox.maxY) * 0.5D,
            target.getZ() + (localBox.minZ + localBox.maxZ) * 0.5D
        );
    }

    private double inwardEpsilon(double min, double max) {
        return Math.min(BREAK_AIM_EPSILON, Math.max(0.0D, (max - min) * 0.25D));
    }

    private BlockHitResult raycastToBreakTarget(
        ClientLevel world,
        LocalPlayer player,
        Vec3 eyePos,
        BlockPos target,
        Vec3 hitPos,
        double reachSq
    ) {
        if (world == null || player == null || eyePos == null || target == null || hitPos == null) {
            return null;
        }
        if (eyePos.distanceToSqr(hitPos) > reachSq) {
            return null;
        }
        BlockHitResult outlineHit = world.clip(new ClipContext(
            eyePos,
            hitPos,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));
        if (outlineHit == null || outlineHit.getType() != HitResult.Type.BLOCK || !target.equals(outlineHit.getBlockPos())) {
            return null;
        }
        Vec3 outlineHitPos = outlineHit.getLocation();
        if (outlineHitPos == null || eyePos.distanceToSqr(outlineHitPos) > reachSq) {
            return null;
        }
        BlockHitResult collisionHit = world.clip(new ClipContext(
            eyePos,
            outlineHitPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        if (collisionHit != null && collisionHit.getType() == HitResult.Type.BLOCK && !target.equals(collisionHit.getBlockPos())) {
            return null;
        }
        return outlineHit;
    }

    private boolean tryPlaceSupportBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now
    ) {
        return tryPlaceSupportBlock(client, world, player, placePos, now, false);
    }

    private boolean tryPlaceSupportBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now,
        boolean preserveMovementState
    ) {
        if (client == null || world == null || player == null || placePos == null || client.gameMode == null) {
            synchronized (this) {
                lastPlaceTarget = placePos != null ? placePos.immutable() : null;
                lastPlaceResult = "client unavailable";
            }
            return false;
        }
        if (now - lastInteractAtMs < 250L) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "cooldown";
            }
            return false;
        }
        PlacementTarget placementTarget = findPlacementTarget(world, placePos);
        if (placementTarget == null) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (this) {
                lastPlaceTarget = placePos.immutable();
                lastPlaceResult = "no placeable block";
            }
            return false;
        }
        int previousSlot = PlayerInventoryBridge.getSelectedSlot(player.getInventory());
        HotbarSlotSynchronizer.selectHotbarSlot(client, hotbarSlot);
        if (!preserveMovementState) {
            releaseMovementKeys(client);
        }
        applyWaterInteractionStance(client, world, player, placePos);
        InteractionResult result = client.gameMode.useItemOn(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(placementTarget.hitPos(), placementTarget.face(), placementTarget.supportPos(), false)
        );
        boolean accepted = result != null && result.consumesAction();
        if (!accepted) {
            InteractionResult fallback = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            accepted = fallback != null && fallback.consumesAction();
        }
        if (accepted) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        HotbarSlotSynchronizer.selectHotbarSlot(client, previousSlot);
        boolean placedNow = pathPlanner.hasCollision(world, placePos);
        synchronized (this) {
            lastPlaceTarget = placePos.immutable();
            if (!accepted) {
                lastPlaceResult = "rejected";
            } else if (placedNow) {
                lastPlaceResult = "placed";
            } else {
                lastPlaceResult = "accepted no block";
            }
        }
        if (!accepted || !placedNow) {
            return false;
        }
        synchronized (this) {
            lastInteractAtMs = now;
        }
        return true;
    }

    private void applyWaterInteractionStance(Minecraft client, ClientLevel world, LocalPlayer player, BlockPos anchor) {
        if (client == null || world == null || player == null || anchor == null || client.options == null) {
            return;
        }
        boolean inWater = player.isInWater()
            || player.isUnderWater()
            || pathPlanner.isWaterNode(world, resolvePlayerFootPos(player))
            || pathPlanner.isWaterNode(world, anchor);
        if (!inWater) {
            return;
        }

        Vec3 anchorCenter = Vec3.atCenterOf(anchor);
        Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
        double dx = anchorCenter.x - currentPos.x;
        double dz = anchorCenter.z - currentPos.z;
        float targetYaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYRot(stepAngle(player.getYRot(), targetYaw, movementYawStep()));
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());

        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(
            velocity.x * 0.55D + Mth.clamp(dx * 0.14D, -0.06D, 0.06D),
            velocity.y,
            velocity.z * 0.55D + Mth.clamp(dz * 0.14D, -0.06D, 0.06D)
        );

        double bobTargetY = anchor.getY() + 0.55D;
        boolean bobUp = player.getY() < bobTargetY || player.getDeltaMovement().y < -0.02D;

        if (client.options.keyUp != null) {
            client.options.keyUp.setDown(Math.abs(dx) > 0.18D || Math.abs(dz) > 0.18D);
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
        if (client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
        if (client.options.keyShift != null) {
            client.options.keyShift.setDown(false);
        }
        if (client.options.keyJump != null) {
            client.options.keyJump.setDown(bobUp);
        }
    }

    private int findPlaceableHotbarSlot(LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findPlaceableMainInventorySlot(LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        for (int slot = hotbarSize; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot(net.minecraft.world.entity.player.Inventory inventory) {
        if (inventory == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int ensurePlaceableHotbarSlot(Minecraft client, LocalPlayer player) {
        int hotbarSlot = findPlaceableHotbarSlot(player);
        if (hotbarSlot >= 0) {
            return hotbarSlot;
        }
        if (client == null || player == null || player.getInventory() == null || client.gameMode == null) {
            return -1;
        }
        int inventorySlot = findPlaceableMainInventorySlot(player);
        if (inventorySlot < 0) {
            return -1;
        }
        return moveInventoryStackToHotbar(client, player, inventorySlot);
    }

    private int moveInventoryStackToHotbar(Minecraft client, LocalPlayer player, int inventorySlot) {
        if (client == null || player == null || player.getInventory() == null || client.gameMode == null) {
            return -1;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        AbstractContainerMenu handler = player.containerMenu;
        if (handler == null) {
            return -1;
        }
        int targetHotbarSlot = findEmptyHotbarSlot(inventory);
        if (targetHotbarSlot == -1) {
            try {
                targetHotbarSlot = PlayerInventoryBridge.getSelectedSlot(inventory);
            } catch (IllegalStateException ignored) {
                targetHotbarSlot = 0;
            }
        }
        int handlerSlot = mapPlayerInventorySlot(handler, inventorySlot);
        if (handlerSlot < 0) {
            return -1;
        }
        client.gameMode.handleInventoryMouseClick(handler.containerId, handlerSlot, targetHotbarSlot, ClickType.SWAP, player);
        ItemStack hotbarStack = inventory.getItem(targetHotbarSlot);
        return !hotbarStack.isEmpty() && hotbarStack.getItem() instanceof BlockItem ? targetHotbarSlot : -1;
    }

    private int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
        if (handler == null) {
            return -1;
        }
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() == inventorySlot) {
                return slotIdx;
            }
        }
        return -1;
    }

    private PlacementTarget findPlacementTarget(Level world, BlockPos placePos) {
        if (world == null || placePos == null) {
            return null;
        }
        Direction[] preferredOrder = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP
        };
        for (Direction direction : preferredOrder) {
            BlockPos support = placePos.relative(direction);
            if (!pathPlanner.hasCollision(world, support)) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3 hitPos = Vec3.atCenterOf(support).add(
                face.getStepX() * 0.5D,
                face.getStepY() * 0.5D,
                face.getStepZ() * 0.5D
            );
            return new PlacementTarget(support, face, hitPos);
        }
        return null;
    }

    private BlockPos resolvePlayerFootPos(LocalPlayer player) {
        return player == null ? null : player.blockPosition().immutable();
    }

    private static void releaseMovementKeys(Minecraft client) {
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

    private static void applySneakState(Minecraft client, boolean active) {
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

    private static BlockHitResult raycastBlockFromOrientation(Minecraft client, float yaw, float pitch, double distance) {
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

    private static float movementYawStep() {
        return LoaderMetadata.isNeoForge() ? NEOFORGE_MAX_YAW_STEP : MAX_YAW_STEP;
    }

    private static float stepAngle(float current, float target, float maxStep) {
        float delta = Mth.wrapDegrees(target - current);
        return current + Mth.clamp(delta, -maxStep, maxStep);
    }

    private record MiningProgress(boolean completed, int resumeIndex, boolean minedAscent) {
        private static MiningProgress incomplete() {
            return new MiningProgress(false, -1, false);
        }
    }

    private record MiningTargetState(List<BlockPos> requiredTargets, BlockPos target, boolean currentlyActive, boolean completed) {
        private static MiningTargetState incomplete(List<BlockPos> requiredTargets) {
            return new MiningTargetState(requiredTargets != null ? List.copyOf(requiredTargets) : List.of(), null, false, false);
        }

        private static MiningTargetState complete(List<BlockPos> requiredTargets) {
            return new MiningTargetState(requiredTargets != null ? List.copyOf(requiredTargets) : List.of(), null, false, true);
        }
    }

    private record PlacementTargetState(BlockPos target, boolean completed) {
        private static PlacementTargetState incomplete(BlockPos target) {
            return new PlacementTargetState(target != null ? target.immutable() : null, false);
        }

        private static PlacementTargetState complete(BlockPos target) {
            return new PlacementTargetState(target != null ? target.immutable() : null, true);
        }
    }

    private record PlacementProgress(boolean completed, int resumeIndex) {
        private static PlacementProgress incomplete() {
            return new PlacementProgress(false, -1);
        }
    }

    private enum MiningAscentPhase {
        CLEARANCE,
        ADVANCE,
        JUMP
    }

    private enum PillarPhase {
        CENTER,
        ASCEND,
        PLACE,
        SUPPORT_READY
    }

    private record BreakTargeting(BlockPos target, Direction face, Vec3 hitPos) {
    }

    private enum EscapePrimitiveType {
        MOVE,
        MINE,
        PILLAR
    }

    private record EscapePrimitive(EscapePrimitiveType type, BlockPos target) {
    }

    private record EscapePlan(Direction direction, List<BlockPos> route, List<EscapePrimitive> primitives) {
        private static EscapePlan empty() {
            return new EscapePlan(Direction.NORTH, List.of(), List.of());
        }

        private boolean isEmpty() {
            return primitives == null || primitives.isEmpty();
        }

        private List<BlockPos> breakTargets() {
            if (primitives == null || primitives.isEmpty()) {
                return List.of();
            }
            List<BlockPos> targets = new ArrayList<>();
            for (EscapePrimitive primitive : primitives) {
                if (primitive != null
                    && primitive.type() == EscapePrimitiveType.MINE
                    && primitive.target() != null
                    && !targets.contains(primitive.target())) {
                    targets.add(primitive.target());
                }
            }
            return List.copyOf(targets);
        }
    }

    private record ExcavationPlan(EscapePlan escapePlan) {
    }

    private record StairEscapePlan(EscapePlan escapePlan) {
        private List<BlockPos> route() {
            return escapePlan.route();
        }
    }

    private record PlacementTarget(BlockPos supportPos, Direction face, Vec3 hitPos) {
    }

}
