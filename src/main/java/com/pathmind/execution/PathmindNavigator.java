package com.pathmind.execution;

import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.PlayerInventoryBridge;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.nio.channels.FileChannel;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

/**
 * Bounded Pathmind-owned movement backend used when Baritone is unavailable.
 * This currently implements local walkable-space pathfinding for goto-style movement and stop/cancel.
 */
public final class PathmindNavigator {
    private static final PathmindNavigator INSTANCE = new PathmindNavigator();
    private static final Method SYNC_SELECTED_SLOT_METHOD = resolveSyncSelectedSlotMethod();
    private static final double WAYPOINT_REACHED_DISTANCE_SQ = 0.64D;
    private static final double WAYPOINT_NEAR_DISTANCE_SQ = 0.90D;
    private static final float MAX_YAW_STEP = 14.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final float JUMP_YAW_ALIGNMENT_DEGREES = 18.0F;
    private static final long STUCK_TIMEOUT_MS = 1500L;
    private static final long REPLAN_COOLDOWN_MS = 450L;
    private static final long JUMP_RETRY_COOLDOWN_MS = 250L;
    private static final long JUMP_COMMIT_WINDOW_MS = 1250L;
    private static final long JUMP_RECOVERY_GRACE_MS = 700L;
    private static final long FAILED_JUMP_MEMORY_MS = 9000L;
    private static final long BREAK_COMMIT_WINDOW_MS = 1800L;
    private static final long FAILED_BREAK_MEMORY_MS = 9000L;
    private static final long DROP_COMMIT_WINDOW_MS = 1500L;
    private static final long FAILED_DROP_MEMORY_MS = 9000L;
    private static final long TRAPPED_RECOVERY_COMMIT_MS = 10000L;
    private static final long FAILED_MOVE_MEMORY_MS = 7000L;
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
    private static final double FAILED_MOVE_PENALTY = 8.0D;
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_DROP_DOWN = 3;
    private static final int MAX_SAFE_FALL_DISTANCE = 3;
    private static final int SEARCH_RADIUS = 56;
    private static final int MAX_SEARCH_RADIUS = 72;
    private static final int SEARCH_HEIGHT = 18;
    private static final int MAX_SEARCH_HEIGHT = 48;
    private static final int GOAL_SEARCH_RADIUS = 5;
    private static final int MAX_EXPANSIONS = 64000;
    private static final int MAX_PATH_BREAK_LOOKAHEAD = 8;
    private static final int MAX_GOAL_CANDIDATES = 10;
    private static final int MAX_VISIBLE_CANDIDATE_PATHS = 3;
    private static final int MAX_GOAL_PATH_ATTEMPTS = 6;
    private static final int PROACTIVE_REPLAN_LOOKAHEAD_STEPS = 6;
    private static final int MAX_SNAPSHOT_PATH_POINTS = 96;
    private static final int MAX_SNAPSHOT_CANDIDATE_POINTS = 64;
    private static final long PATHFIND_TIME_BUDGET_MS = 220L;
    private static final long COARSE_PATHFIND_TIME_BUDGET_MS = 180L;
    private static final int COARSE_MAX_EXPANSIONS = 90000;
    private static final int COARSE_LOOKAHEAD_STEPS = 18;
    private static final double COARSE_PLANNING_DISTANCE_SQ = 18.0D * 18.0D;
    private static final int MAX_DEBUG_EVENTS = 12;
    private static final Path NAV_DEBUG_LOG_PATH = Path.of(System.getProperty("user.dir"), "logs", "navigator-debug.log");
    private static final long DEBUG_HEARTBEAT_INTERVAL_MS = 1500L;
    private static final double WATER_PENALTY = 3.5D;
    private static final double WATER_AVOIDANCE_PENALTY = 12.0D;
    private static final double FLOWING_WATER_PENALTY = 2.5D;
    private static final double DEEP_WATER_PENALTY = 2.0D;
    private static final double WATER_DANGER_PENALTY = 10.0D;
    private static final double WATER_NO_EXIT_PENALTY = 4.0D;
    private static final double EDGE_PENALTY = 1.25D;
    private static final double DANGER_PENALTY = 12.0D;
    private static final double MIN_PROGRESS_FOR_REPLAN_SQ = 9.0D;
    private static final double BREAK_MOVE_PENALTY = 4.5D;
    private static final double PLACE_MOVE_PENALTY = 12.0D;
    private static final double BREAK_ASSIST_SURCHARGE = 14.0D;
    private static final double PLACE_ASSIST_SURCHARGE = 32.0D;
    private static final double PATH_BREAK_ROUTE_PENALTY = 240.0D;
    private static final double PATH_PLACE_ROUTE_PENALTY = 420.0D;
    private static final double SEARCH_JUMP_PENALTY = 0.65D;
    private static final double SEARCH_DESCEND_PENALTY = 0.08D;
    private static final double SEARCH_CLIMB_PENALTY = 0.45D;
    private static final double SEARCH_SWIM_PENALTY = 0.95D;
    private static final double SEARCH_INTERACT_PENALTY = 0.16D;
    private static final double SEARCH_BREAK_PENALTY = 4.5D;
    private static final double SEARCH_PLACE_PENALTY = 7.5D;
    private static final double SEARCH_PILLAR_PENALTY = 12.0D;
    private static final double LOCAL_TARGET_PROGRESS_WEIGHT = 1.6D;
    private static final double LOCAL_TARGET_STEP_WEIGHT = 0.45D;
    private static final double LOCAL_TARGET_MODIFICATION_WEIGHT = 2.2D;
    private static final double LOCAL_TARGET_COMMITTED_WEIGHT = 0.9D;
    private static final int LOCAL_TARGET_TAIL_WINDOW = 6;
    private static final double GOAL_MODIFICATION_AVOID_DISTANCE_SQ = 6.25D;
    private static final double TREE_CANOPY_PENALTY = 26.0D;
    private static final double TREE_CANOPY_MODIFICATION_PENALTY = 18.0D;
    private static final double TURN_PENALTY_DIAGONAL = 0.08D;
    private static final double TURN_PENALTY_CORNER = 0.28D;
    private static final double TURN_PENALTY_REVERSE = 0.9D;
    private static final double HEURISTIC_WEIGHT = 1.18D;
    private static final int MIN_PARTIAL_PATH_LENGTH = 6;
    private static final double PILLAR_MOVE_PENALTY = 2.4D;
    private static final double DIG_ESCAPE_MOVE_PENALTY = 1.35D;
    private static final double DIG_BREAKOUT_MOVE_PENALTY = 1.1D;
    private static final Move[] MOVES = {
        new Move(0, -1, 1.0D),
        new Move(0, 1, 1.0D),
        new Move(-1, 0, 1.0D),
        new Move(1, 0, 1.0D)
    };

    private CompletableFuture<Void> activeFuture;
    private BlockPos targetPos;
    private String commandLabel;
    private State state = State.IDLE;
    private long startedAtMs;
    private long lastProgressAtMs;
    private long lastPlanAtMs;
    private long lastJumpAtMs;
    private double bestDistanceSq = Double.MAX_VALUE;
    private GoalMode goalMode = GoalMode.EXACT;
    private WaterMode waterMode = WaterMode.NORMAL;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacing = true;
    private boolean eventLoggingEnabled = true;
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
    private FollowSegmentType activeFollowSegment = FollowSegmentType.GROUND;
    private BlockPos activeFollowSegmentTarget;
    private PlannedPrimitive activePlannedPrimitive;
    private long activeFollowSegmentEnteredAtMs;
    private long activeFollowSegmentProgressAtMs;
    private double activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
    private Vec3d lastMovementSamplePos = Vec3d.ZERO;
    private long lastMovementAtMs;
    private double lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
    private long lastDistanceCheckpointAtMs;
    private final Map<EdgeKey, Long> failedEdges = new HashMap<>();
    private final Map<BlockPos, Long> failedNodes = new HashMap<>();
    private final Map<EdgeKey, Long> failedBreaks = new HashMap<>();
    private final Map<EdgeKey, Long> failedJumps = new HashMap<>();
    private final Map<EdgeKey, Long> failedDrops = new HashMap<>();
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

    private enum GoalMode {
        EXACT,
        NEAREST_STANDABLE
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

    public static PathmindNavigator getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startGoto(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future) {
        if (targetPos == null || future == null) {
            return false;
        }
        stopInternal(false, "replaced");
        this.targetPos = targetPos.toImmutable();
        this.commandLabel = commandLabel == null || commandLabel.isBlank() ? "Goto" : commandLabel.trim();
        this.activeFuture = future;
        this.state = State.PATHING;
        this.startedAtMs = System.currentTimeMillis();
        this.lastProgressAtMs = this.startedAtMs;
        this.lastPlanAtMs = 0L;
        this.lastJumpAtMs = 0L;
        this.bestDistanceSq = Double.MAX_VALUE;
        this.goalMode = GoalMode.EXACT;
        this.resolvedGoalPos = targetPos.toImmutable();
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
        this.activeFollowSegment = FollowSegmentType.GROUND;
        this.activeFollowSegmentTarget = null;
        this.activePlannedPrimitive = null;
        this.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        this.activeFollowSegmentProgressAtMs = this.startedAtMs;
        this.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        this.lastMovementSamplePos = Vec3d.ofCenter(this.targetPos);
        this.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        this.lastDistanceCheckpointAtMs = this.startedAtMs;
        this.failedEdges.clear();
        this.failedNodes.clear();
        this.failedBreaks.clear();
        this.failedJumps.clear();
        this.failedDrops.clear();
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

    public synchronized Snapshot getSnapshot() {
        if (state == State.IDLE || targetPos == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double distance = -1.0D;
        if (client != null && client.player != null) {
            Vec3d target = Vec3d.ofCenter(targetPos);
            Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
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
        if (client != null && client.world != null) {
            if (!committedEscape.breakTargets().isEmpty()) {
                breakTargets = committedEscape.breakTargets().stream()
                    .filter(pos -> pos != null && !canOccupy(client.world, pos))
                    .toList();
            } else if (!plannedBreakTargets.isEmpty()) {
                breakTargets = plannedBreakTargets.stream()
                    .filter(pos -> pos != null && isBreakableForNavigator(client.world, pos))
                    .toList();
            } else if (activeWaypoint != null) {
                List<BlockPos> requiredBreakTargets = getRequiredBreakTargets(client.world, activeWaypoint);
                if (requiredBreakTargets != null && !requiredBreakTargets.isEmpty()) {
                    breakTargets = List.copyOf(requiredBreakTargets);
                } else if (activeBreakTarget != null) {
                    breakTargets = List.of(activeBreakTarget);
                }
            }
            if (controllerMode == ControllerMode.PILLAR && controllerTarget != null) {
                placeTargets = List.of(controllerTarget.down().toImmutable());
            } else if (activePlannedPrimitive != null && activePlannedPrimitive.placeTarget() != null) {
                placeTargets = List.of(activePlannedPrimitive.placeTarget().toImmutable());
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
            MinecraftClient client = MinecraftClient.getInstance();
            String playerState = "player=none";
            if (client != null && client.player != null) {
                ClientPlayerEntity player = client.player;
                BlockPos foot = resolvePlayerFootPos(player);
                Vec3d velocity = player.getVelocity();
                playerState = "player=" + formatDebugPos(foot)
                    + " vel="
                    + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", velocity.x, velocity.y, velocity.z)
                    + " ground=" + player.isOnGround();
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
                previousActiveWaypoint = activeWaypoint != null ? activeWaypoint.toImmutable() : null;
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
            try (FileChannel channel = FileChannel.open(NAV_DEBUG_LOG_PATH, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
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

    public synchronized PreviewResult previewPath(MinecraftClient client, BlockPos targetPos, String commandLabel) {
        if (client == null || client.player == null || client.world == null || targetPos == null) {
            return new PreviewResult(false, FailureReason.CLIENT_UNAVAILABLE.message);
        }

        stopInternal(false, "preview");
        this.targetPos = targetPos.toImmutable();
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
        this.activeFollowSegment = FollowSegmentType.GROUND;
        this.activeFollowSegmentTarget = null;
        this.activePlannedPrimitive = null;
        this.activeFollowSegmentEnteredAtMs = this.startedAtMs;
        this.activeFollowSegmentProgressAtMs = this.startedAtMs;
        this.activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        this.lastMovementSamplePos = Vec3d.ofCenter(this.targetPos);
        this.lastMovementAtMs = this.startedAtMs;
        this.lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        this.lastDistanceCheckpointAtMs = this.startedAtMs;
        this.lastReplanReason = "preview";
        this.lastStuckReason = "none";
        this.previousPrimitiveLabel = "none";
        this.previousMiningAscentPhase = this.activeMiningAscentPhase.name();
        this.previousPillarPhase = this.activePillarPhase.name();
        this.failedEdges.clear();
        this.failedNodes.clear();
        this.failedBreaks.clear();
        this.failedJumps.clear();
        this.failedDrops.clear();

        BlockPos start = resolvePlayerFootPos(client.player);
        PathComputation computation = findPath(client.world, start, this.targetPos);
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
        this.committedPathGoalPos = this.resolvedGoalPos != null ? this.resolvedGoalPos.toImmutable() : this.targetPos;
        this.committedPathStartPos = start != null ? start.toImmutable() : null;
        this.pathIndex = chooseInitialPathIndex(this.currentPath, start, this.targetPos);
        this.lastWaypointAdvanceAtMs = System.currentTimeMillis();
        this.furthestVisitedPathIndex = Math.max(-1, this.pathIndex - 1);
        this.activeWaypoint = this.currentPath.get(this.pathIndex);
        this.plannedBreakTargets = buildPathBreakPlan(client.world, this.currentPath, this.pathIndex);
        this.currentPlan = computation.plannedPrimitives();
        this.activePlannedPrimitive = this.pathIndex < this.currentPlan.size() ? this.currentPlan.get(this.pathIndex) : null;
        return new PreviewResult(true, "Pathmind Nav: previewing path to " + this.targetPos.getX() + " " + this.targetPos.getY() + " " + this.targetPos.getZ());
    }

    public void tick(MinecraftClient client) {
        CompletableFuture<Void> future;
        BlockPos target;
        GoalMode goalMode;
        BlockPos resolvedGoal;
        synchronized (this) {
            future = activeFuture;
            target = targetPos;
            goalMode = this.goalMode;
            resolvedGoal = resolvedGoalPos;
            if (state != State.PATHING || future == null || target == null) {
                return;
            }
        }

        if (client == null || client.player == null || client.world == null) {
            fail(FailureReason.CLIENT_UNAVAILABLE);
            return;
        }

        ClientPlayerEntity player = client.player;
        BlockPos playerFootPos = resolvePlayerFootPos(player);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double distanceSq = currentPos.squaredDistanceTo(targetCenter);
        BlockPos completionTarget = shouldUseResolvedGoalForCompletion(target, resolvedGoal, goalMode) ? resolvedGoal : target;
        long now = System.currentTimeMillis();

        synchronized (this) {
            if (currentPos.squaredDistanceTo(lastMovementSamplePos) > MOVEMENT_EPSILON_SQ) {
                lastMovementSamplePos = currentPos;
                lastMovementAtMs = now;
            }
            double currentDistance = Math.sqrt(distanceSq);
            if (!Double.isFinite(lastDistanceCheckpoint) || Math.abs(currentDistance - lastDistanceCheckpoint) > DISTANCE_STALL_THRESHOLD) {
                lastDistanceCheckpoint = currentDistance;
                lastDistanceCheckpointAtMs = now;
            }
        }

        ClientWorld world = client.world;
        if (hasReachedGoal(world, player, playerFootPos, completionTarget, target)) {
            releaseMovementKeys(client);
            complete(State.ARRIVED);
            return;
        }

        pruneFailureMemory(now);
        if (shouldReplan(world, playerFootPos, target, now)) {
            PathComputation computation = findPath(world, playerFootPos, target);
            if (computation.path().isEmpty()) {
                if (canRepairCurrentPath(world, playerFootPos, target)) {
                    repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
                } else {
                    fail(computation.failureReason(), computation.failureDetail());
                }
                return;
            }
            List<BlockPos> newPath = computation.path();
            if (shouldKeepCommittedPath(world, playerFootPos, target, newPath, computation.plannedPrimitives(), now)) {
                repairCurrentPath(world, playerFootPos, target, now, "planner deferred", "keep committed route");
            } else {
            synchronized (this) {
                currentPath = newPath;
                candidatePaths = computation.candidatePaths();
                candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                goalMode = shouldUseResolvedGoalForCompletion(target, computation.resolvedGoalPos(), computation.goalMode())
                    ? computation.goalMode()
                    : GoalMode.EXACT;
                resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? computation.resolvedGoalPos() : target.toImmutable();
                committedPathGoalPos = computation.resolvedGoalPos() != null ? computation.resolvedGoalPos().toImmutable() : resolvedGoalPos;
                committedPathStartPos = playerFootPos != null ? playerFootPos.toImmutable() : null;
                pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                lastWaypointAdvanceAtMs = now;
                furthestVisitedPathIndex = Math.max(-1, pathIndex - 1);
                activeWaypoint = currentPath.get(pathIndex);
                plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                currentPlan = computation.plannedPrimitives();
                activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
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
            BlockPos fallbackResolvedGoal;
            synchronized (this) {
                fallbackResolvedGoal = resolvedGoalPos != null ? resolvedGoalPos.toImmutable() : null;
            }
            if (fallbackResolvedGoal != null
                && hasReachedGoal(world, player, playerFootPos, fallbackResolvedGoal, target)
                && horizontalDistanceSq(fallbackResolvedGoal, target) <= 4.0D
                && Math.abs(fallbackResolvedGoal.getY() - target.getY()) <= MAX_DROP_DOWN) {
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
                if (!isViablePlannedPath(world, recovery.path(), recovery.plannedPrimitives())) {
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
                        goalMode = shouldUseResolvedGoalForCompletion(target, recovery.resolvedGoalPos(), recovery.goalMode())
                            ? recovery.goalMode()
                            : GoalMode.EXACT;
                        resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.toImmutable();
                        committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().toImmutable() : resolvedGoalPos;
                        committedPathStartPos = playerFootPos != null ? playerFootPos.toImmutable() : null;
                        pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                        lastWaypointAdvanceAtMs = now;
                        furthestVisitedPathIndex = Math.max(-1, pathIndex - 1);
                        activeWaypoint = currentPath.get(pathIndex);
                        plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
                        currentPlan = recovery.plannedPrimitives();
                        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
                        appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
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
            waypoint = target.toImmutable();
            synchronized (this) {
                activeWaypoint = waypoint;
                activePlannedPrimitive = createPrimitiveSnapshot(null, null, waypoint, SearchPrimitiveType.WALK, PlannedPrimitiveType.WALK, List.of(), null);
            }
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

        ControllerMode activeController = updateControllerMode(world, player, playerFootPos, waypoint, plannedPrimitive, now, distanceSq);
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
                waypoint = target.toImmutable();
                synchronized (this) {
                    activeWaypoint = waypoint;
                    activePlannedPrimitive = createPrimitiveSnapshot(null, null, waypoint, SearchPrimitiveType.WALK, PlannedPrimitiveType.WALK, List.of(), null);
                }
            }
        }

        if (shouldRedirectController(now, distanceSq)) {
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
        releaseMovementKeys(MinecraftClient.getInstance());
        state = State.FAILED;
        CompletableFuture<Void> future = activeFuture;
        String message = failureReason != null ? failureReason.message : FailureReason.NO_ROUTE.message;
        if (failureDetail != null && !failureDetail.isBlank()) {
            message = message + " " + failureDetail.trim();
        }
        MinecraftClient client = MinecraftClient.getInstance();
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
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3d.ZERO;
        lastMovementAtMs = 0L;
        lastDistanceCheckpoint = Double.POSITIVE_INFINITY;
        lastDistanceCheckpointAtMs = 0L;
        NodeErrorNotificationOverlay.getInstance().show(message, UITheme.STATE_ERROR);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new RuntimeException(message));
        }
        state = State.IDLE;
    }

    private synchronized void complete(State terminalState) {
        state = terminalState;
        CompletableFuture<Void> future = activeFuture;
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos playerFootPos = client != null && client.player != null ? resolvePlayerFootPos(client.player) : null;
        BlockPos completedTarget = targetPos != null ? targetPos.toImmutable() : null;
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
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3d.ZERO;
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
        releaseMovementKeys(MinecraftClient.getInstance());
        appendDebugEventLocked("stop reason=" + (reason == null ? "none" : reason));
        if (activeFuture != null && completeFuture && !activeFuture.isDone()) {
            activeFuture.complete(null);
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
        activeFollowSegment = FollowSegmentType.GROUND;
        activeFollowSegmentTarget = null;
        activePlannedPrimitive = null;
        activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
        activePillarPhase = PillarPhase.CENTER;
        activeFollowSegmentEnteredAtMs = 0L;
        activeFollowSegmentProgressAtMs = 0L;
        activeFollowSegmentBestDistanceSq = Double.POSITIVE_INFINITY;
        lastMovementSamplePos = Vec3d.ZERO;
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
    }

    private boolean shouldReplan(ClientWorld world, BlockPos start, BlockPos target, long now) {
        synchronized (this) {
            if (currentPath.isEmpty() || activeWaypoint == null) {
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
            || horizontalDistanceSq(pathEnd, target) > 4.0D
            || Math.abs(pathEnd.getY() - target.getY()) > MAX_DROP_DOWN;
    }

    private BlockPos committedPathGoalLocked(BlockPos fallbackTarget) {
        return committedPathGoalPos != null ? committedPathGoalPos : fallbackTarget;
    }

    private boolean isWaypointActionable(World world, BlockPos waypoint) {
        if (world == null || waypoint == null) {
            return false;
        }
        if (isNavigableNode(world, waypoint)) {
            return true;
        }
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, waypoint);
        if (breakTargets == null) {
            return false;
        }
        if (!breakTargets.isEmpty()) {
            return allowBlockBreaking;
        }
        if (needsPlacedSupport(world, waypoint)) {
            return allowBlockPlacing && canPlaceSupportAt(world, waypoint.down());
        }
        return hasCollision(world, waypoint.down()) || isWaterNode(world, waypoint);
    }

    private boolean isPathGoalStillValid(List<BlockPos> path, BlockPos target) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return horizontalDistanceSq(last, target) <= 4.0D && Math.abs(last.getY() - target.getY()) <= MAX_DROP_DOWN;
    }

    private boolean shouldUseResolvedGoalForCompletion(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode) {
        if (goalMode != GoalMode.NEAREST_STANDABLE || target == null || resolvedGoal == null) {
            return false;
        }
        return horizontalDistanceSq(target, resolvedGoal) <= 4.0D
            && Math.abs(target.getY() - resolvedGoal.getY()) <= MAX_DROP_DOWN;
    }

    private boolean isPlayerNearPath(BlockPos playerFootPos) {
        if (playerFootPos == null || currentPath.isEmpty()) {
            return false;
        }
        int start = Math.max(0, pathIndex - 2);
        int end = Math.min(currentPath.size() - 1, pathIndex + 6);
        for (int i = start; i <= end; i++) {
            BlockPos step = currentPath.get(i);
            if (horizontalDistanceSq(playerFootPos, step) <= 4.0D && Math.abs(step.getY() - playerFootPos.getY()) <= 2) {
                return true;
            }
        }
        if (targetPos != null && horizontalDistanceSq(playerFootPos, targetPos) < MIN_PROGRESS_FOR_REPLAN_SQ) {
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

    private boolean canRepairCurrentPath(World world, BlockPos playerFootPos, BlockPos target) {
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
        World world,
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
            if (!isViablePlannedPath(world, candidatePath, candidatePlan)) {
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
            double currentPenalty = pathStructurePenalty(currentPath, currentPlan) + pathModificationPenalty(currentPlan);
            double candidatePenalty = pathStructurePenalty(candidatePath, candidatePlan) + pathModificationPenalty(candidatePlan);
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
                double currentPlayerDistance = horizontalDistanceSq(playerFootPos, currentStep);
                double candidatePlayerDistance = horizontalDistanceSq(playerFootPos, candidateStep);
                if (currentPlayerDistance > 25.0D && candidatePlayerDistance > 25.0D) {
                    break;
                }
            }
            if (horizontalDistanceSq(currentStep, candidateStep) > 2.25D
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
            if (horizontalDistanceSq(activeWaypoint, step) <= 4.0D
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
        return horizontalDistanceSq(pos, goal) + Math.abs(pos.getY() - goal.getY()) * 1.5D;
    }

    private ControllerMode updateControllerMode(
        ClientWorld world,
        ClientPlayerEntity player,
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
                    BlockPos miningTarget = selectMiningControllerTarget(world, playerFootPos, waypoint, plannedPrimitive);
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
        World world,
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
                BlockPos advanceBlock = resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
                if (advanceBlock != null) {
                    return advanceBlock.toImmutable();
                }
            }
            if (phase == MiningAscentPhase.JUMP) {
                return waypoint.toImmutable();
            }
        }
        PlacementTargetState placementTargetState = resolveCommittedPlacementTargetState(world, waypoint, plannedPrimitive);
        if (placementTargetState.target() != null) {
            return placementTargetState.target().toImmutable();
        }
        synchronized (this) {
            if (activeBreakTarget != null && isBreakableForNavigator(world, activeBreakTarget)) {
                return activeBreakTarget.toImmutable();
            }
        }
        BlockPos breakTarget = selectBreakTarget(world, playerFootPos, waypoint, plannedPrimitive);
        return breakTarget != null ? breakTarget.toImmutable() : waypoint.toImmutable();
    }

    private void noteControllerProgress(long now, double distanceSq) {
        synchronized (this) {
            if (distanceSq < controllerBestDistanceSq) {
                controllerBestDistanceSq = distanceSq;
                controllerProgressAtMs = now;
            }
        }
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
                activeFollowSegmentTarget = target != null ? target.toImmutable() : null;
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
        ClientWorld world,
        ClientPlayerEntity player,
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
            BlockPos breakTarget = selectBreakTarget(world, playerFootPos, waypoint, plannedPrimitive);
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
        BlockPos breakTarget = selectBreakTarget(world, playerFootPos, waypoint, plannedPrimitive);
        boolean miningAscentStep = plannedPrimitive != null && plannedPrimitive.isMineAscent();
        if (breakTarget != null
            || miningAscentStep
            || (allowBlockPlacing && primitiveStillRequiresPlace(world, plannedPrimitive))) {
            return ControllerMode.BREAK_BLOCK;
        }
        return ControllerMode.FOLLOW_PATH;
    }

    private boolean shouldUsePillarStep(World world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
        return world != null && playerFootPos != null && waypoint != null && now >= 0L && isPillarPrimitive(plannedPrimitive);
    }

    private ControllerMode recoveryModeForPrimitive(PlannedPrimitive plannedPrimitive, World world, BlockPos playerFootPos, BlockPos waypoint, long now) {
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

    private boolean isCommittedPillarState(World world, BlockPos playerFootPos, long now) {
        synchronized (this) {
            if (controllerMode != ControllerMode.PILLAR || controllerTarget == null || now > controllerUntilMs) {
                return false;
            }
            BlockPos pillarTarget = controllerTarget;
            BlockPos pillarBase = pillarTarget.down();
            if (playerFootPos == null
                || pillarBase.getX() != playerFootPos.getX()
                || pillarBase.getZ() != playerFootPos.getZ()
                || pillarBase.getY() < playerFootPos.getY() - 1
                || pillarBase.getY() > playerFootPos.getY()) {
                return false;
            }
            return canContinuePillarTo(world, pillarBase, pillarTarget);
        }
    }

    private boolean isRecoveryState(World world, BlockPos playerFootPos, long now) {
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
            if (requiresBreakingForWaypoint(world, controllerTarget) || needsPlacedSupport(world, controllerTarget)) {
                return false;
            }
            return true;
        }
    }

    private boolean shouldEnterEscapeRecovery(World world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
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

    private void clearStaleEscapeRecoveryIfNeeded(World world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive, long now) {
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

    private void repairCurrentPath(World world, BlockPos playerFootPos, BlockPos target, long now, String replanReason, String stuckReason) {
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
                lastMovementSamplePos = Vec3d.ofCenter(playerFootPos);
            }
            lastMovementAtMs = now;
            lastDistanceCheckpointAtMs = now;
        }
    }

    private boolean shouldInvalidateCommittedPrimitive(
        World world,
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
            && (requiresInteractableTraversal(world, playerFootPos, waypoint)
            || hasPathOpenableAhead(world, playerFootPos, waypoint))) {
            return false;
        }
        synchronized (this) {
            return now - lastProgressAtMs >= 900L;
        }
    }

    private void recoverFromStuck(
        MinecraftClient client,
        ClientWorld world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3d currentPos,
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
                controllerTarget = committedEscapeTarget != null ? committedEscapeTarget : waypoint.toImmutable();
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
                controllerTarget = activeWaypoint != null ? activeWaypoint.toImmutable() : (waypoint != null ? waypoint.toImmutable() : null);
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
                && isViablePlannedPath(world, recovery.path(), recovery.plannedPrimitives())
                && !shouldKeepCommittedPath(world, playerFootPos, target, recovery.path(), recovery.plannedPrimitives(), now)) {
                synchronized (this) {
                    currentPath = recovery.path();
                    candidatePaths = recovery.candidatePaths();
                    candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                    goalMode = shouldUseResolvedGoalForCompletion(target, recovery.resolvedGoalPos(), recovery.goalMode())
                        ? recovery.goalMode()
                        : GoalMode.EXACT;
                    resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? recovery.resolvedGoalPos() : target.toImmutable();
                    committedPathGoalPos = recovery.resolvedGoalPos() != null ? recovery.resolvedGoalPos().toImmutable() : resolvedGoalPos;
                    committedPathStartPos = playerFootPos != null ? playerFootPos.toImmutable() : null;
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
                    lastMovementSamplePos = currentPos != null ? currentPos : Vec3d.ofCenter(playerFootPos);
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
                    controllerTarget = activeWaypoint != null ? activeWaypoint.toImmutable() : (waypoint != null ? waypoint.toImmutable() : null);
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || activeWaypoint == null) {
            return false;
        }
        World world = client.world;
        PlannedPrimitive plannedPrimitive;
        synchronized (this) {
            plannedPrimitive = activePlannedPrimitive;
        }
        if (plannedPrimitive != null) {
            return plannedPrimitive.requiresCommittedAction();
        }
        if (requiresBreakingForWaypoint(world, activeWaypoint) || needsPlacedSupport(world, activeWaypoint)) {
            return true;
        }
        BlockPos previous = pathIndex > 0 && pathIndex - 1 < currentPath.size() ? currentPath.get(pathIndex - 1) : null;
        if (previous != null && !requiresBreakingForWaypoint(world, activeWaypoint) && !requiresInteractableTraversal(world, previous, activeWaypoint)) {
            int dy = activeWaypoint.getY() - previous.getY();
            if (dy > 0 || shouldStepJump(world, previous, activeWaypoint)) {
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
                    if (horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 3) {
                        continue;
                    }
                    double score = horizontalDistanceSq(playerFootPos, step);
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
                        if (horizontalDistanceSq(playerFootPos, step) <= 6.25D && Math.abs(step.getY() - playerFootPos.getY()) <= 2) {
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

    private void chooseRecoveryPathIndexLocked(World world, BlockPos playerFootPos, BlockPos target) {
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

        double playerGoalDistance = horizontalDistanceSq(playerFootPos, target);
        for (int i = boundedIndex; i < currentPath.size() && i <= boundedIndex + 2; i++) {
            BlockPos step = currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 3) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            if (horizontalDistanceSq(step, target) <= playerGoalDistance + 1.0D) {
                pathIndex = i;
                return;
            }
        }

        for (int i = boundedIndex; i >= furthestVisitedPathIndex && i >= boundedIndex - 2; i--) {
            BlockPos step = currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (horizontalDistanceSq(playerFootPos, step) > 9.0D || Math.abs(step.getY() - playerFootPos.getY()) > 2) {
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

    private void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3d currentPos, long now, String replanReason, String stuckReason) {
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
            lastMovementSamplePos = currentPos != null ? currentPos : Vec3d.ZERO;
            lastDistanceCheckpointAtMs = now;
        }
    }

    private void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now) {
        rememberFailedMove(playerFootPos, waypoint, now);
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
                rememberFailedMove(previous, step, now);
                previous = step;
            }
        }
    }

    private BlockPos chooseActiveWaypoint(ClientWorld world, ClientPlayerEntity player, BlockPos playerFootPos) {
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
                activeWaypoint = controllerTarget.toImmutable();
                if (plannedBreakTargets.isEmpty()) {
                    plannedBreakTargets = buildPathBreakPlan(world, currentPath, Math.max(0, pathIndex));
                }
                if (!isPillarPrimitive(activePlannedPrimitive)) {
                    activePlannedPrimitive = createPrimitiveSnapshot(world, playerFootPos, activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), activeWaypoint.down());
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
        World world,
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
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, playerFootPos, waypoint);
        if (breakTargets == null) {
            breakTargets = List.of();
        } else {
            breakTargets = breakTargets.stream()
                .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
                .map(BlockPos::toImmutable)
                .toList();
        }
        BlockPos placeTarget = needsPlacedSupport(world, waypoint) && canPlaceSupportAt(world, waypoint.down())
            ? waypoint.down().toImmutable()
            : null;
        return createPlannedPrimitive(world, playerFootPos, waypoint, breakTargets, placeTarget);
    }

    private BlockPos advanceWaypointIfNeeded(ClientPlayerEntity player, BlockPos playerFootPos) {
        if (player == null || playerFootPos == null) {
            setAdvanceDecision("hold:missing_player");
            return null;
        }
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        synchronized (this) {
            long now = System.currentTimeMillis();
            int reachedIndex = findReachedPathIndexLocked(playerFootPos);
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
                        && horizontalDistanceSq(playerFootPos, next) <= WAYPOINT_REACHED_DISTANCE_SQ
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
                Vec3d waypointCenter = new Vec3d(waypoint.getX() + 0.5D, playerPos.y, waypoint.getZ() + 0.5D);
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

    private int findReachedPathIndexLocked(BlockPos playerFootPos) {
        if (playerFootPos == null || currentPath.isEmpty()) {
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
            if (horizontalDistanceSq(playerFootPos, step) <= WAYPOINT_REACHED_DISTANCE_SQ
                && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                best = i;
            }
        }
        return best;
    }

    private void chooseForwardResyncIndexLocked(World world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null || currentPath.isEmpty()) {
            return;
        }
        int boundedIndex = Math.max(furthestVisitedPathIndex, Math.min(pathIndex, currentPath.size() - 1));
        BlockPos currentStep = currentPath.get(boundedIndex);
        if (currentStep == null) {
            return;
        }
        double currentDistance = horizontalDistanceSq(playerFootPos, currentStep);
        int bestIndex = boundedIndex;
        double bestScore = currentDistance;
        int end = Math.min(currentPath.size() - 1, boundedIndex + 4);
        for (int i = boundedIndex + 1; i <= end; i++) {
            BlockPos step = currentPath.get(i);
            if (step == null) {
                continue;
            }
            if (horizontalDistanceSq(playerFootPos, step) > 16.0D || Math.abs(step.getY() - playerFootPos.getY()) > 2) {
                continue;
            }
            if (!isWaypointActionable(world, step)) {
                continue;
            }
            double score = horizontalDistanceSq(playerFootPos, step) + ((i - boundedIndex) * 0.15D);
            if (score + 0.75D < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex > boundedIndex
            && (currentDistance > 4.0D
            || currentStep.getY() > playerFootPos.getY()
            || !isWaypointActionable(world, currentStep))) {
            pathIndex = bestIndex;
            lastWaypointAdvanceAtMs = System.currentTimeMillis();
            furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, bestIndex - 1);
            lastAdvanceDecision = "resync:forward_index=" + bestIndex + " waypoint=" + formatDebugPos(currentPath.get(bestIndex));
        }
    }

    private boolean shouldAdvancePastWaypoint(Vec3d playerPos, BlockPos playerFootPos, BlockPos waypoint, Vec3d waypointCenter) {
        if (waypoint == null || waypointCenter == null || playerFootPos == null || playerPos == null) {
            return true;
        }
        if (playerFootPos.equals(waypoint)) {
            return true;
        }
        if (waypoint.getY() > playerFootPos.getY()) {
            return false;
        }
        double distanceSq = playerPos.squaredDistanceTo(waypointCenter);
        if (distanceSq <= WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
            return true;
        }
        synchronized (this) {
            if (distanceSq <= WAYPOINT_NEAR_DISTANCE_SQ
                && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1
                && pathIndex + 1 < currentPath.size()) {
                BlockPos next = currentPath.get(pathIndex + 1);
                if (next != null
                    && horizontalDistanceSq(playerFootPos, next) + 0.20D < horizontalDistanceSq(playerFootPos, waypoint)
                    && Math.abs(next.getY() - playerFootPos.getY()) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private int chooseInitialPathIndex(List<BlockPos> path, BlockPos playerFootPos, BlockPos target) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos step = path.get(i);
            if (step != null) {
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
            if (horizontalDistanceSq(playerFootPos, step) <= 16.0D
                && Math.abs(step.getY() - playerFootPos.getY()) <= 3) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> buildPathBreakPlan(World world, List<BlockPos> path, int startIndex) {
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
            List<BlockPos> requiredBreakTargets = getRequiredBreakTargets(world, previous, waypoint);
            if (requiredBreakTargets == null || requiredBreakTargets.isEmpty()) {
                continue;
            }
            for (BlockPos breakTarget : requiredBreakTargets) {
                if (breakTarget != null && isBreakableForNavigator(world, breakTarget)) {
                    plan.add(breakTarget.toImmutable());
                }
            }
        }
        return List.copyOf(plan);
    }

    private List<PlannedPrimitive> buildPlannedPrimitives(World world, List<BlockPos> path, BlockPos startPos) {
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
            List<BlockPos> breakTargets = getRequiredBreakTargets(world, previous, target);
            if (breakTargets == null) {
                breakTargets = List.of();
            } else {
                breakTargets = breakTargets.stream()
                    .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
                    .map(BlockPos::toImmutable)
                    .toList();
            }
            BlockPos placeTarget = needsPlacedSupport(world, target) && canPlaceSupportAt(world, target.down())
                ? target.down().toImmutable()
                : null;
            plan.add(createPlannedPrimitive(world, previous, target, breakTargets, placeTarget));
        }
        return List.copyOf(plan);
    }

    private PlannedPrimitive createPlannedPrimitive(
        World world,
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
        World world,
        BlockPos from,
        BlockPos to,
        SearchPrimitiveType searchType,
        PlannedPrimitiveType type,
        List<BlockPos> breakTargets,
        BlockPos placeTarget
    ) {
        List<BlockPos> normalizedBreakTargets = breakTargets == null ? List.of() : List.copyOf(breakTargets);
        BlockPos normalizedTarget = to == null ? null : to.toImmutable();
        BlockPos normalizedPlaceTarget = placeTarget == null ? null : placeTarget.toImmutable();
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
        World world,
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
        if (from != null && (isWaterNode(world, from) || isWaterNode(world, to))) {
            return SearchPrimitiveType.SWIM;
        }
        if (from != null && (isClimbTransition(world, from, to) || isClimbNode(world, to) || isClimbNode(world, from))) {
            return SearchPrimitiveType.CLIMB;
        }
        if (from != null && to.getY() < from.getY()) {
            return SearchPrimitiveType.DESCEND;
        }
        if (from != null && (requiresInteractableTraversal(world, from, to) || hasPathOpenableAhead(world, from, to))) {
            return SearchPrimitiveType.INTERACT;
        }
        if (from != null && to.getY() > from.getY()) {
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
        World world,
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
        World world,
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

    private void rebuildCurrentPlanLocked(World world) {
        currentPlan = buildPlannedPrimitives(world, currentPath, committedPathStartPos);
        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
        if (!currentPlan.isEmpty()) {
            appendDebugEventLocked("plan=" + formatPlannedPrimitiveSequence(currentPlan, 8));
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

    private boolean primitiveStillRequiresBreak(World world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.breakTargets() == null || primitive.breakTargets().isEmpty()) {
            return false;
        }
        return world == null || firstPendingBreakTarget(world, primitive.breakTargets()) != null;
    }

    private boolean primitiveStillRequiresPlace(World world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.placeTarget() == null) {
            return false;
        }
        return world == null || !hasCollision(world, primitive.placeTarget());
    }

    private void clearStalePlaceStateIfNeeded(World world, PlannedPrimitive primitive) {
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

    private PathComputation findPath(ClientWorld world, BlockPos start, BlockPos target) {
        if (world == null || start == null || target == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.CLIENT_UNAVAILABLE, null);
        }

        BlockPos normalizedStart = isNavigableNode(world, start) ? start.toImmutable() : findNearbyStandable(world, start, 2);
        if (normalizedStart == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_START_SPACE, "Move to a standable block before retrying.");
        }

        BlockPos planningTarget = resolvePlanningTarget(world, normalizedStart, target);
        if (planningTarget == null) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_LOADED_FRONTIER, "The planner could not project a loaded route corridor toward " + formatDebugPos(target) + ".");
        }

        BlockPos exactPlanningTarget = planningTarget;
        if (shouldUseHierarchicalPlanning(normalizedStart, planningTarget)) {
            List<BlockPos> coarsePath = findCoarsePath(world, normalizedStart, planningTarget);
            BlockPos localPlanningTarget = selectLocalPlanningTarget(world, normalizedStart, coarsePath, planningTarget);
            if (localPlanningTarget != null) {
                planningTarget = localPlanningTarget;
            }
        }

        GoalSearchOutcome searchOutcome = searchPlanningTarget(world, normalizedStart, planningTarget, target);
        List<ScoredPath> scoredPaths = searchOutcome.scoredPaths();
        FailureReason lastFailure = searchOutcome.failureReason();
        String lastFailureDetail = searchOutcome.failureDetail();

        if (scoredPaths.isEmpty() && !planningTarget.equals(exactPlanningTarget)) {
            searchOutcome = searchPlanningTarget(world, normalizedStart, exactPlanningTarget, target);
            scoredPaths = searchOutcome.scoredPaths();
            lastFailure = searchOutcome.failureReason();
            lastFailureDetail = searchOutcome.failureDetail();
            planningTarget = exactPlanningTarget;
        }

        if (scoredPaths.isEmpty()) {
            return new PathComputation(List.of(), List.of(), List.of(), null, GoalMode.EXACT, lastFailure, lastFailureDetail);
        }

        List<ScoredPath> cleanScoredPaths = scoredPaths.stream()
            .filter(path -> !pathRequiresModification(path.plannedPrimitives()))
            .collect(Collectors.toCollection(ArrayList::new));
        if (!cleanScoredPaths.isEmpty()) {
            scoredPaths = cleanScoredPaths;
        }
        scoredPaths.sort(Comparator.comparingDouble(ScoredPath::cost));
        List<List<BlockPos>> visibleCandidates = scoredPaths.stream()
            .map(ScoredPath::path)
            .limit(MAX_VISIBLE_CANDIDATE_PATHS)
            .toList();
        BlockPos resolvedGoal = scoredPaths.get(0).path().isEmpty() ? null : scoredPaths.get(0).path().get(scoredPaths.get(0).path().size() - 1);
        GoalMode goalMode = resolvedGoal != null && resolvedGoal.equals(exactPlanningTarget) ? GoalMode.EXACT : GoalMode.NEAREST_STANDABLE;
        return new PathComputation(scoredPaths.get(0).path(), scoredPaths.get(0).plannedPrimitives(), visibleCandidates, resolvedGoal, goalMode, null, null);
    }

    private GoalSearchOutcome searchPlanningTarget(ClientWorld world, BlockPos start, BlockPos planningTarget, BlockPos exactTarget) {
        List<BlockPos> goalCandidates = collectGoalCandidates(world, start, planningTarget);
        if (goalCandidates.isEmpty()) {
            BlockPos nearby = findNearbyStandable(world, planningTarget, 4);
            if (nearby != null) {
                goalCandidates = List.of(nearby);
            }
        }
        if (goalCandidates.isEmpty()) {
            return new GoalSearchOutcome(List.of(), FailureReason.NO_GOAL_SPACE, "No walkable endpoint was found near " + formatDebugPos(planningTarget) + ".");
        }

        List<ScoredPath> scoredPaths = new ArrayList<>();
        FailureReason lastFailure = FailureReason.NO_ROUTE;
        String lastFailureDetail = "The planner did not find a viable route toward " + formatDebugPos(planningTarget) + ".";
        int candidateCount = Math.min(MAX_GOAL_PATH_ATTEMPTS, goalCandidates.size());
        for (int i = 0; i < candidateCount; i++) {
            BlockPos candidateGoal = goalCandidates.get(i);
            long candidateDeadlineMs = System.currentTimeMillis() + PATHFIND_TIME_BUDGET_MS;
            PathSearchResult result = findPathToGoal(world, start, candidateGoal, candidateDeadlineMs);
            if (!result.path().isEmpty()) {
                List<BlockPos> candidatePath = result.path();
                List<PlannedPrimitive> candidatePlan = result.plannedPrimitives();
                boolean exactPath = endsAtGoal(candidatePath, candidateGoal);
                if (!exactPath) {
                    if (candidateGoal.equals(exactTarget)) {
                        BlockPos partialEnd = candidatePath.isEmpty() ? null : candidatePath.get(candidatePath.size() - 1);
                        boolean acceptableNearGoal = partialEnd != null
                            && horizontalDistanceSq(partialEnd, exactTarget) <= 4.0D
                            && Math.abs(partialEnd.getY() - exactTarget.getY()) <= MAX_DROP_DOWN
                            && isWaypointActionable(world, partialEnd);
                        if (!acceptableNearGoal) {
                            lastFailure = FailureReason.NO_ROUTE;
                            lastFailureDetail = "The exact target " + formatDebugPos(candidateGoal) + " could not be reached exactly.";
                            continue;
                        }
                    }
                    if (candidatePath.size() < MIN_PARTIAL_PATH_LENGTH + 2) {
                        lastFailure = FailureReason.NO_ROUTE;
                        lastFailureDetail = "Only a very short partial path was found toward " + formatDebugPos(candidateGoal) + ".";
                        continue;
                    }
                }
                if (!isViablePlannedPath(world, candidatePath, candidatePlan)) {
                    lastFailure = FailureReason.NO_ROUTE;
                    lastFailureDetail = "The planner produced an invalid movement sequence toward " + formatDebugPos(candidateGoal) + ".";
                    continue;
                }
                double scoredCost = result.cost()
                    + pathStructurePenalty(candidatePath, candidatePlan)
                    + pathModificationPenalty(candidatePlan);
                scoredPaths.add(new ScoredPath(candidatePath, candidatePlan, scoredCost));
                if (candidateGoal.equals(exactTarget)) {
                    break;
                }
            } else if (result.failureReason() != null) {
                lastFailure = result.failureReason();
                lastFailureDetail = result.failureDetail();
            }
        }
        return new GoalSearchOutcome(scoredPaths, lastFailure, lastFailureDetail);
    }

    private boolean shouldUseHierarchicalPlanning(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return false;
        }
        return horizontalDistanceSq(start, target) >= COARSE_PLANNING_DISTANCE_SQ;
    }

    private BlockPos selectLocalPlanningTarget(World world, BlockPos start, List<BlockPos> coarsePath, BlockPos fallbackTarget) {
        if (fallbackTarget == null) {
            return null;
        }
        if (coarsePath == null || coarsePath.isEmpty()) {
            return fallbackTarget;
        }
        if (coarsePath.size() <= COARSE_LOOKAHEAD_STEPS + 1) {
            return coarsePath.get(coarsePath.size() - 1);
        }
        int upperBound = Math.min(coarsePath.size() - 1, COARSE_LOOKAHEAD_STEPS);
        BlockPos selected = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= upperBound; i++) {
            BlockPos candidate = coarsePath.get(i);
            if (candidate == null || !isWaypointActionable(world, candidate)) {
                continue;
            }
            double score = scoreLocalPlanningCandidate(world, start, coarsePath, i, fallbackTarget);
            if (selected == null || score > bestScore) {
                selected = candidate.toImmutable();
                bestScore = score;
            }
        }
        return selected != null ? selected : fallbackTarget;
    }

    private double scoreLocalPlanningCandidate(
        World world,
        BlockPos start,
        List<BlockPos> coarsePath,
        int candidateIndex,
        BlockPos fallbackTarget
    ) {
        if (world == null || start == null || coarsePath == null || coarsePath.isEmpty() || candidateIndex <= 0 || candidateIndex >= coarsePath.size()) {
            return Double.NEGATIVE_INFINITY;
        }
        BlockPos candidate = coarsePath.get(candidateIndex);
        if (candidate == null) {
            return Double.NEGATIVE_INFINITY;
        }

        double startDistance = Math.sqrt(horizontalDistanceSq(start, fallbackTarget));
        double candidateDistance = Math.sqrt(horizontalDistanceSq(candidate, fallbackTarget));
        double progressScore = Math.max(0.0D, startDistance - candidateDistance) * LOCAL_TARGET_PROGRESS_WEIGHT;
        double stepScore = candidateIndex * LOCAL_TARGET_STEP_WEIGHT;

        List<BlockPos> prefix = List.copyOf(coarsePath.subList(0, candidateIndex + 1));
        List<PlannedPrimitive> primitives = buildPlannedPrimitives(world, prefix, start);
        if (primitives.isEmpty()) {
            return progressScore + stepScore;
        }

        int tailStart = Math.max(0, primitives.size() - LOCAL_TARGET_TAIL_WINDOW);
        double tailPenalty = 0.0D;
        for (int i = tailStart; i < primitives.size(); i++) {
            PlannedPrimitive primitive = primitives.get(i);
            if (primitive == null) {
                continue;
            }
            if (primitive.requiresWorldModification()) {
                tailPenalty += LOCAL_TARGET_MODIFICATION_WEIGHT;
            }
            if (primitive.isCommittedTraversal()) {
                tailPenalty += LOCAL_TARGET_COMMITTED_WEIGHT;
            }
            if (primitive.isPillar()) {
                tailPenalty += LOCAL_TARGET_MODIFICATION_WEIGHT * 1.5D;
            }
        }

        double totalModificationPenalty = pathModificationPenalty(primitives) * 0.01D;
        double totalStructurePenalty = pathStructurePenalty(prefix, primitives) * 0.20D;
        return progressScore + stepScore - tailPenalty - totalModificationPenalty - totalStructurePenalty;
    }

    private List<BlockPos> findCoarsePath(ClientWorld world, BlockPos start, BlockPos goal) {
        if (world == null || start == null || goal == null) {
            return List.of();
        }
        long deadlineMs = System.currentTimeMillis() + COARSE_PATHFIND_TIME_BUDGET_MS;
        PriorityQueue<CoarseSearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(CoarseSearchNode::fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos bestPartial = start;
        double bestPartialHeuristic = heuristic(start, List.of(goal));

        gScore.put(start, 0.0D);
        openSet.add(new CoarseSearchNode(start, heuristic(start, List.of(goal)), 0.0D));

        int expansions = 0;
        while (!openSet.isEmpty() && expansions < COARSE_MAX_EXPANSIONS) {
            if (System.currentTimeMillis() >= deadlineMs) {
                break;
            }
            CoarseSearchNode current = openSet.poll();
            BlockPos currentPos = current.pos();
            if (!closed.add(currentPos)) {
                continue;
            }
            if (currentPos.equals(goal)) {
                return reconstructCoarsePath(cameFrom, currentPos);
            }
            double currentHeuristic = heuristic(currentPos, List.of(goal));
            if (currentHeuristic < bestPartialHeuristic) {
                bestPartial = currentPos;
                bestPartialHeuristic = currentHeuristic;
            }
            expansions++;

            for (CoarseNeighbor neighbor : getCoarseNeighbors(world, currentPos, start, goal)) {
                if (closed.contains(neighbor.pos())) {
                    continue;
                }
                BlockPos previous = cameFrom.get(currentPos);
                double tentativeG = current.gScore()
                    + neighbor.cost()
                    + elevationPenalty(currentPos, neighbor.pos())
                    + turnPenalty(previous, currentPos, neighbor.pos())
                    + terrainPenalty(world, currentPos, neighbor.pos());
                double knownG = gScore.getOrDefault(neighbor.pos(), Double.POSITIVE_INFINITY);
                if (tentativeG >= knownG) {
                    continue;
                }
                cameFrom.put(neighbor.pos(), currentPos);
                gScore.put(neighbor.pos(), tentativeG);
                openSet.add(new CoarseSearchNode(neighbor.pos(), tentativeG + heuristic(neighbor.pos(), List.of(goal)), tentativeG));
            }
        }

        if (bestPartial != null && !bestPartial.equals(start)) {
            return reconstructCoarsePath(cameFrom, bestPartial);
        }
        return List.of();
    }

    private List<BlockPos> reconstructCoarsePath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cursor = end;
        while (cursor != null) {
            path.add(cursor.toImmutable());
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private boolean endsAtGoal(List<BlockPos> path, BlockPos goal) {
        if (path == null || path.isEmpty() || goal == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return goal.equals(last);
    }

    private PathSearchResult findPathToGoal(ClientWorld world, BlockPos start, BlockPos goal, long deadlineMs) {
        Set<BlockPos> goalSet = Set.of(goal);
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fScore));
        SearchVertex startVertex = new SearchVertex(start, SearchPrimitiveType.WALK);
        Map<SearchVertex, SearchVertex> cameFrom = new HashMap<>();
        Map<SearchVertex, PlannedPrimitive> cameByPrimitive = new HashMap<>();
        Map<SearchVertex, Double> gScore = new HashMap<>();
        Set<SearchVertex> closed = new HashSet<>();
        SearchVertex bestPartial = startVertex;
        double bestPartialScore = heuristic(startVertex.pos(), List.of(goal));
        double bestPartialDistanceSq = horizontalDistanceSq(startVertex.pos(), goal);

        gScore.put(startVertex, 0.0D);
        openSet.add(new SearchNode(startVertex, heuristic(startVertex.pos(), List.of(goal)), 0.0D));

        int expansions = 0;
        while (!openSet.isEmpty() && expansions < MAX_EXPANSIONS) {
            if (System.currentTimeMillis() >= deadlineMs) {
                break;
            }
            SearchNode current = openSet.poll();
            if (closed.contains(current.vertex())) {
                continue;
            }
            if (isGoal(current.pos(), goal, goalSet)) {
                ReconstructedPath reconstructed = reconstructPath(world, cameFrom, cameByPrimitive, current.vertex(), start);
                return new PathSearchResult(reconstructed.path(), reconstructed.plannedPrimitives(), current.gScore, null, null);
            }
            double currentHeuristic = heuristic(current.pos(), List.of(goal));
            double currentDistanceSq = horizontalDistanceSq(current.pos(), goal) + Math.abs(current.pos().getY() - goal.getY());
            if (currentHeuristic < bestPartialScore
                || (Math.abs(currentHeuristic - bestPartialScore) < 0.001D && currentDistanceSq < bestPartialDistanceSq)) {
                bestPartial = current.vertex();
                bestPartialScore = currentHeuristic;
                bestPartialDistanceSq = currentDistanceSq;
            }
            closed.add(current.vertex());
            expansions++;

            for (Neighbor neighbor : getNeighbors(world, current.pos(), start, goal)) {
                if (closed.contains(neighbor.vertex())) {
                    continue;
                }
                SearchVertex previousState = cameFrom.get(current.vertex());
                BlockPos previous = previousState != null ? previousState.pos() : null;
                double tentativeG = current.gScore
                    + neighbor.cost
                    + elevationPenalty(current.pos(), neighbor.pos())
                    + turnPenalty(previous, current.pos(), neighbor.pos())
                    + terrainPenalty(world, current.pos(), neighbor.pos());
                double knownG = gScore.getOrDefault(neighbor.vertex(), Double.POSITIVE_INFINITY);
                if (tentativeG >= knownG) {
                    continue;
                }
                cameFrom.put(neighbor.vertex(), current.vertex());
                cameByPrimitive.put(neighbor.vertex(), neighbor.primitive());
                gScore.put(neighbor.vertex(), tentativeG);
                openSet.add(new SearchNode(neighbor.vertex(), tentativeG + heuristic(neighbor.pos(), List.of(goal)), tentativeG));
            }
        }

        if (bestPartial != null && !bestPartial.pos().equals(start)) {
            ReconstructedPath reconstructed = reconstructPath(world, cameFrom, cameByPrimitive, bestPartial, start);
            List<BlockPos> partialPath = reconstructed.path();
            if (partialPath.size() >= MIN_PARTIAL_PATH_LENGTH || horizontalDistanceSq(bestPartial.pos(), goal) <= 36.0D) {
                return new PathSearchResult(partialPath, reconstructed.plannedPrimitives(), gScore.getOrDefault(bestPartial, Double.POSITIVE_INFINITY), null, null);
            }
        }

        return new PathSearchResult(
            List.of(),
            List.of(),
            Double.POSITIVE_INFINITY,
            expansions >= MAX_EXPANSIONS ? FailureReason.SEARCH_LIMIT : FailureReason.NO_ROUTE,
            expansions >= MAX_EXPANSIONS
                ? "Search exhausted " + MAX_EXPANSIONS + " expansions while routing toward " + formatDebugPos(goal) + "."
                : "No traversable primitive sequence was found toward " + formatDebugPos(goal) + "."
        );
    }

    private ReconstructedPath reconstructPath(
        World world,
        Map<SearchVertex, SearchVertex> cameFrom,
        Map<SearchVertex, PlannedPrimitive> cameByPrimitive,
        SearchVertex end,
        BlockPos start
    ) {
        List<BlockPos> path = new ArrayList<>();
        List<PlannedPrimitive> primitives = new ArrayList<>();
        SearchVertex cursor = end;
        while (cursor != null) {
            path.add(cursor.pos());
            PlannedPrimitive primitive = cameByPrimitive.get(cursor);
            if (primitive != null) {
                primitives.add(primitive);
            }
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        Collections.reverse(primitives);
        List<BlockPos> cleanedPath = postProcessPath(world, path);
        List<PlannedPrimitive> cleanedPrimitives = buildPlannedPrimitives(world, cleanedPath, start);
        return new ReconstructedPath(cleanedPath, cleanedPrimitives);
    }

    private List<BlockPos> postProcessPath(World world, List<BlockPos> rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return List.of();
        }
        List<BlockPos> cleaned = new ArrayList<>(rawPath.size());
        for (BlockPos step : rawPath) {
            if (step == null) {
                continue;
            }
            BlockPos immutableStep = step.toImmutable();
            if (!cleaned.isEmpty() && immutableStep.equals(cleaned.get(cleaned.size() - 1))) {
                continue;
            }
            if (cleaned.size() >= 2 && immutableStep.equals(cleaned.get(cleaned.size() - 2))) {
                cleaned.remove(cleaned.size() - 1);
                continue;
            }
            while (cleaned.size() >= 2) {
                BlockPos previous = cleaned.get(cleaned.size() - 2);
                BlockPos middle = cleaned.get(cleaned.size() - 1);
                if (!canSkipMiddleWaypoint(world, previous, middle, immutableStep)) {
                    break;
                }
                cleaned.remove(cleaned.size() - 1);
            }
            cleaned.add(immutableStep);
        }
        return List.copyOf(cleaned);
    }

    private boolean canSkipMiddleWaypoint(World world, BlockPos previous, BlockPos middle, BlockPos next) {
        if (world == null || previous == null || middle == null || next == null) {
            return false;
        }
        if (previous.equals(next)) {
            return true;
        }
        int dx = Math.abs(next.getX() - previous.getX());
        int dz = Math.abs(next.getZ() - previous.getZ());
        int dy = Math.abs(next.getY() - previous.getY());
        if (dx > 1 || dz > 1 || dy > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        Neighbor directNeighbor = resolveNeighborAccess(world, previous, next);
        if (directNeighbor == null) {
            return false;
        }
        if (!isPlannerTraversableMove(world, previous, next)) {
            return false;
        }
        if ((requiresBreakingForWaypoint(world, middle) || needsPlacedSupport(world, middle))
            && !requiresBreakingForWaypoint(world, next)
            && !needsPlacedSupport(world, next)
            && horizontalDistanceSq(previous, next) <= 1.0D
            && Math.abs(next.getY() - previous.getY()) <= 1) {
            return true;
        }
        return true;
    }

    private double pathStructurePenalty(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
        if (path == null || path.size() < 3) {
            return 0.0D;
        }
        double penalty = 0.0D;
        int lastDx = 0;
        int lastDz = 0;
        int lastDy = 0;
        for (int i = 1; i < path.size(); i++) {
            BlockPos previous = path.get(i - 1);
            BlockPos current = path.get(i);
            if (previous == null || current == null) {
                continue;
            }
            int dx = Integer.compare(current.getX() - previous.getX(), 0);
            int dz = Integer.compare(current.getZ() - previous.getZ(), 0);
            int dy = Integer.compare(current.getY() - previous.getY(), 0);
            if (i > 1) {
                if (dx == -lastDx && dz == -lastDz && dy == -lastDy) {
                    penalty += 4.5D;
                } else {
                    if (dx != lastDx || dz != lastDz) {
                        penalty += 0.35D;
                    }
                    if (dy != 0 && lastDy != 0 && dy != lastDy) {
                        penalty += 1.0D;
                    }
                    if (dy != 0 && (dx != lastDx || dz != lastDz)) {
                        penalty += 0.7D;
                    }
                }
            }
            if (plannedPrimitives != null && i < plannedPrimitives.size()) {
                PlannedPrimitive primitive = plannedPrimitives.get(i);
                if (primitive != null) {
                    penalty += pathSearchTypePenalty(primitive);
                }
            }
            lastDx = dx;
            lastDz = dz;
            lastDy = dy;
        }
        return penalty;
    }

    private double pathModificationPenalty(List<PlannedPrimitive> plannedPrimitives) {
        if (plannedPrimitives == null || plannedPrimitives.isEmpty()) {
            return 0.0D;
        }
        double penalty = 0.0D;
        for (PlannedPrimitive primitive : plannedPrimitives) {
            if (primitive == null) {
                continue;
            }
            penalty += pathModificationPenaltyForPrimitive(primitive);
        }
        return penalty;
    }

    private double pathSearchTypePenalty(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return 0.0D;
        }
        return switch (primitive.searchType()) {
            case BREAK_FORWARD, MINE_ASCEND -> 0.45D;
            case PLACE_FORWARD, PILLAR -> 0.35D;
            case JUMP_ASCEND, DESCEND, CLIMB, SWIM, INTERACT -> 0.20D;
            case WALK -> 0.0D;
        };
    }

    private double pathModificationPenaltyForPrimitive(PlannedPrimitive primitive) {
        if (primitive == null || primitive.searchType() == null) {
            return 0.0D;
        }
        return switch (primitive.searchType()) {
            case BREAK_FORWARD, MINE_ASCEND -> PATH_BREAK_ROUTE_PENALTY;
            case PLACE_FORWARD, PILLAR -> PATH_PLACE_ROUTE_PENALTY;
            case WALK, INTERACT, JUMP_ASCEND, DESCEND, CLIMB, SWIM -> 0.0D;
        };
    }

    private boolean pathRequiresModification(List<PlannedPrimitive> plannedPrimitives) {
        if (plannedPrimitives == null || plannedPrimitives.isEmpty()) {
            return false;
        }
        for (PlannedPrimitive primitive : plannedPrimitives) {
            if (primitive == null) {
                continue;
            }
            if (primitive.requiresWorldModification()) {
                return true;
            }
        }
        return false;
    }

    private boolean isViablePlannedPath(World world, List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
        if (world == null || path == null || path.isEmpty()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos step = path.get(i);
            if (step == null || !isWaypointActionable(world, step)) {
                return false;
            }
            PlannedPrimitive primitive = plannedPrimitives != null && i < plannedPrimitives.size() ? plannedPrimitives.get(i) : null;
            if (primitive != null && primitive.target() != null && !primitive.target().equals(step)) {
                return false;
            }
            if (primitive != null && primitive.requiresBreak() && primitive.requiresPlace()) {
                return false;
            }
            if (i == 0) {
                continue;
            }
            BlockPos previous = path.get(i - 1);
            if (!isViablePlannedStep(world, previous, step, primitive)) {
                return false;
            }
        }
        return true;
    }

    private boolean isViablePlannedStep(World world, BlockPos from, BlockPos to, PlannedPrimitive primitive) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dx > 1 || dz > 1 || Math.abs(dy) > MAX_STEP_UP + MAX_SAFE_FALL_DISTANCE) {
            return false;
        }
        if (dx == 0
            && dz == 0
            && dy > 0
            && (primitive == null
                || (!primitive.isClimb() && !primitive.isPillar()))) {
            return false;
        }
        if (dx == 0
            && dz == 0
            && dy < 0
            && (primitive == null
                || (!primitive.isClimb() && !primitive.isSwim()))) {
            return false;
        }
        if (dx == 1 && dz == 1 && dy != 0) {
            return false;
        }
        if (!isPlannerTraversableMove(world, from, to)) {
            return false;
        }
        if (dy > 0 && !requiresInteractableTraversal(world, from, to) && !isClimbTransition(world, from, to)) {
            if (!canTraverseAscendingStep(world, from, to)) {
                return false;
            }
        }
        if (dy < 0 && !canSafelyDropTo(world, from, to)) {
            return false;
        }
        if (primitive != null && primitive.isPillar() && !primitive.requiresPlace()) {
            return false;
        }
        return true;
    }

    private double turnPenalty(BlockPos previous, BlockPos current, BlockPos next) {
        if (previous == null || current == null || next == null) {
            return 0.0D;
        }
        int prevDx = Integer.compare(current.getX() - previous.getX(), 0);
        int prevDz = Integer.compare(current.getZ() - previous.getZ(), 0);
        int nextDx = Integer.compare(next.getX() - current.getX(), 0);
        int nextDz = Integer.compare(next.getZ() - current.getZ(), 0);
        if (prevDx == nextDx && prevDz == nextDz) {
            return 0.0D;
        }
        if (prevDx == -nextDx && prevDz == -nextDz) {
            return TURN_PENALTY_REVERSE;
        }
        boolean diagonalTurn = Math.abs(prevDx + prevDz) == 1 && Math.abs(nextDx + nextDz) == 2
            || Math.abs(prevDx + prevDz) == 2 && Math.abs(nextDx + nextDz) == 1;
        return diagonalTurn ? TURN_PENALTY_DIAGONAL : TURN_PENALTY_CORNER;
    }

    private List<Neighbor> getNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal) {
        List<Neighbor> neighbors = new ArrayList<>(MOVES.length + 8);
        long now = System.currentTimeMillis();
        boolean trappedExcavation = isTrappedExcavationState(world, current, goal, now);
        Direction escapeDirection = getPreferredEscapeDirection(world, current, goal, now);
        for (Move move : MOVES) {
            if (trappedExcavation) {
                if (Math.abs(move.dx) + Math.abs(move.dz) == 2) {
                    continue;
                }
                if (!matchesEscapeDirection(move, escapeDirection)) {
                    continue;
                }
            }
            addDirectedPrimitiveNeighbors(world, current, move.dx, move.dz, start, goal, neighbors, now);
        }
        addDigEscapeNeighbors(world, current, start, goal, neighbors, now);
        addClimbNeighbors(world, current, start, goal, neighbors, now);
        addSafeDropNeighbors(world, current, start, goal, neighbors, now);
        addPillarNeighbors(world, current, start, goal, neighbors, now);
        return neighbors;
    }

    private void addDirectedPrimitiveNeighbors(
        World world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return;
        }
        if (Math.abs(dx) + Math.abs(dz) == 2
            && (!hasDirectedPrimitiveAccess(world, current, dx, 0, start, goal, now)
            || !hasDirectedPrimitiveAccess(world, current, 0, dz, start, goal, now))) {
            return;
        }

        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        addPrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, neighbors, now);
        addPrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, neighbors, now);
        addPrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.BREAK_FORWARD, start, goal, neighbors, now);
        addPrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.PLACE_FORWARD, start, goal, neighbors, now);

        BlockPos ascendCandidate = flatCandidate.up();
        addPrimitiveNeighborIfPresent(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, neighbors, now);
        addPrimitiveNeighborIfPresent(world, current, ascendCandidate, SearchPrimitiveType.MINE_ASCEND, start, goal, neighbors, now);

        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos descendCandidate = new BlockPos(current.getX() + dx, current.getY() - drop, current.getZ() + dz);
            addPrimitiveNeighborIfPresent(world, current, descendCandidate, SearchPrimitiveType.DESCEND, start, goal, neighbors, now);
        }
    }

    private boolean hasDirectedPrimitiveAccess(
        World world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return false;
        }
        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        if (buildPrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, now) != null
            || buildPrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, now) != null
            || buildPrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.BREAK_FORWARD, start, goal, now) != null
            || buildPrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.PLACE_FORWARD, start, goal, now) != null) {
            return true;
        }
        BlockPos ascendCandidate = flatCandidate.up();
        return buildPrimitiveNeighbor(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, now) != null
            || buildPrimitiveNeighbor(world, current, ascendCandidate, SearchPrimitiveType.MINE_ASCEND, start, goal, now) != null;
    }

    private void addPrimitiveNeighborIfPresent(
        World world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now
    ) {
        Neighbor neighbor = buildPrimitiveNeighbor(world, from, candidate, family, start, goal, now);
        if (neighbor != null) {
            neighbors.add(neighbor);
        }
    }

    private Neighbor buildPrimitiveNeighbor(
        World world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || from == null || candidate == null || family == null) {
            return null;
        }
        if (!isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(from, candidate, now)) {
            return null;
        }

        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        int dy = candidate.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0) || (dx == 1 && dz == 1 && dy != 0)) {
            return null;
        }

        if (family == SearchPrimitiveType.INTERACT || family == SearchPrimitiveType.WALK
            || family == SearchPrimitiveType.BREAK_FORWARD || family == SearchPrimitiveType.PLACE_FORWARD) {
            if (dy != 0) {
                return null;
            }
        } else if (family == SearchPrimitiveType.JUMP_ASCEND || family == SearchPrimitiveType.MINE_ASCEND) {
            if (dy != 1 || isFailedJump(from, candidate, now)) {
                return null;
            }
        } else if (family == SearchPrimitiveType.DESCEND) {
            if (dy >= 0 || !canSafelyDropTo(world, from, candidate)) {
                return null;
            }
        }

        boolean interactable = requiresInteractableTraversal(world, from, candidate) || hasPathOpenableAhead(world, from, candidate);
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, from, candidate);
        if (breakTargets == null) {
            return null;
        }
        boolean hasBreaks = !breakTargets.isEmpty();
        boolean requiresSupport = allowBlockPlacing && needsPlacedSupport(world, candidate);

        switch (family) {
            case INTERACT -> {
                if (!interactable || hasBreaks || requiresSupport || !isNavigableNode(world, candidate)) {
                    return null;
                }
            }
            case WALK -> {
                if (interactable || hasBreaks || requiresSupport || !isNavigableNode(world, candidate)) {
                    return null;
                }
            }
            case BREAK_FORWARD -> {
                if (interactable || !hasBreaks || requiresSupport || !allowBlockBreaking || isFailedBreak(from, candidate, now)) {
                    return null;
                }
            }
            case PLACE_FORWARD -> {
                if (interactable || hasBreaks || !requiresSupport) {
                    return null;
                }
            }
            case JUMP_ASCEND -> {
                if (interactable || hasBreaks || requiresSupport || !canAttemptJump(world, from, candidate)) {
                    return null;
                }
            }
            case MINE_ASCEND -> {
                if (interactable || !hasBreaks || requiresSupport || !allowBlockBreaking || isFailedBreak(from, candidate, now) || !canTraverseAscendingStep(world, from, candidate)) {
                    return null;
                }
            }
            case DESCEND -> {
                if (hasBreaks || requiresSupport) {
                    return null;
                }
            }
        }

        if (hasBreaks) {
            if (shouldAvoidGoalModification(world, candidate) || hasInteractableAlternative(world, from, candidate, targetPos)) {
                return null;
            }
        }
        if (requiresSupport) {
            BlockPos activeTarget = targetPos;
            if ((activeTarget != null && candidate.down().equals(activeTarget))
                || shouldAvoidGoalModification(world, candidate)
                || !canUsePlacedSupportMove(world, from, candidate)
                || hasNaturalGroundAlternative(world, from, candidate, activeTarget)
                || !canPlaceSupportAt(world, candidate.down())) {
                return null;
            }
        } else if (!hasBreaks && !isWaterNode(world, candidate) && !hasCollision(world, candidate.down())) {
            return null;
        }

        List<BlockPos> normalizedBreakTargets = breakTargets.stream()
            .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
            .map(BlockPos::toImmutable)
            .toList();
        BlockPos placeTarget = requiresSupport ? candidate.down().toImmutable() : null;
        PlannedPrimitive primitive = createPlannedPrimitive(world, from, candidate, normalizedBreakTargets, placeTarget);
        if (!matchesPrimitiveFamily(primitive, family)) {
            return null;
        }
        return new Neighbor(
            new SearchVertex(candidate.toImmutable(), primitive.searchType()),
            primitiveStepBaseCost(from, candidate) + primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    private boolean matchesPrimitiveFamily(PlannedPrimitive primitive, SearchPrimitiveType family) {
        if (primitive == null || family == null) {
            return false;
        }
        return primitive.searchType() == family;
    }

    private List<CoarseNeighbor> getCoarseNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal) {
        List<CoarseNeighbor> neighbors = new ArrayList<>(MOVES.length + 8);
        long now = System.currentTimeMillis();
        for (Move move : MOVES) {
            addDirectedCoarsePrimitiveNeighbors(world, current, move.dx, move.dz, start, goal, neighbors, now);
        }
        addCoarseClimbNeighbors(world, current, start, goal, neighbors, now);
        addCoarseDropNeighbors(world, current, start, goal, neighbors, now);
        return neighbors;
    }

    private void addDirectedCoarsePrimitiveNeighbors(
        World world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        List<CoarseNeighbor> neighbors,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return;
        }
        if (Math.abs(dx) + Math.abs(dz) == 2
            && (!hasDirectedCoarsePrimitiveAccess(world, current, dx, 0, start, goal, now)
            || !hasDirectedCoarsePrimitiveAccess(world, current, 0, dz, start, goal, now))) {
            return;
        }

        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        addCoarsePrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, neighbors, now);
        addCoarsePrimitiveNeighborIfPresent(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, neighbors, now);

        BlockPos ascendCandidate = flatCandidate.up();
        addCoarsePrimitiveNeighborIfPresent(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, neighbors, now);

        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos descendCandidate = new BlockPos(current.getX() + dx, current.getY() - drop, current.getZ() + dz);
            addCoarsePrimitiveNeighborIfPresent(world, current, descendCandidate, SearchPrimitiveType.DESCEND, start, goal, neighbors, now);
        }
    }

    private boolean hasDirectedCoarsePrimitiveAccess(
        World world,
        BlockPos current,
        int dx,
        int dz,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || current == null || (dx == 0 && dz == 0)) {
            return false;
        }
        BlockPos flatCandidate = new BlockPos(current.getX() + dx, current.getY(), current.getZ() + dz);
        if (buildCoarsePrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.INTERACT, start, goal, now) != null
            || buildCoarsePrimitiveNeighbor(world, current, flatCandidate, SearchPrimitiveType.WALK, start, goal, now) != null) {
            return true;
        }
        BlockPos ascendCandidate = flatCandidate.up();
        return buildCoarsePrimitiveNeighbor(world, current, ascendCandidate, SearchPrimitiveType.JUMP_ASCEND, start, goal, now) != null;
    }

    private void addCoarsePrimitiveNeighborIfPresent(
        World world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        List<CoarseNeighbor> neighbors,
        long now
    ) {
        CoarseNeighbor neighbor = buildCoarsePrimitiveNeighbor(world, from, candidate, family, start, goal, now);
        if (neighbor != null) {
            neighbors.add(neighbor);
        }
    }

    private CoarseNeighbor buildCoarsePrimitiveNeighbor(
        World world,
        BlockPos from,
        BlockPos candidate,
        SearchPrimitiveType family,
        BlockPos start,
        BlockPos goal,
        long now
    ) {
        if (world == null || from == null || candidate == null || family == null) {
            return null;
        }
        if (!isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(from, candidate, now)
            || !isCoarseNavigableNode(world, candidate)) {
            return null;
        }

        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        int dy = candidate.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0) || (dx == 1 && dz == 1)) {
            return null;
        }

        boolean interactable = requiresInteractableTraversal(world, from, candidate) || hasPathOpenableAhead(world, from, candidate);
        switch (family) {
            case INTERACT -> {
                if (dy != 0 || !interactable) {
                    return null;
                }
            }
            case WALK -> {
                if (dy != 0 || interactable || !isCoarsePlannerTraversableMove(world, from, candidate)) {
                    return null;
                }
            }
            case JUMP_ASCEND -> {
                if (dy != 1 || isFailedJump(from, candidate, now) || interactable || !canAttemptJump(world, from, candidate)) {
                    return null;
                }
            }
            case DESCEND -> {
                if (dy >= 0 || !canSafelyDropTo(world, from, candidate)) {
                    return null;
                }
            }
            case BREAK_FORWARD, PLACE_FORWARD, MINE_ASCEND -> {
                return null;
            }
        }

        PlannedPrimitive primitive = createPlannedPrimitive(world, from, candidate, List.of(), null);
        if (!matchesPrimitiveFamily(primitive, family)) {
            return null;
        }
        return new CoarseNeighbor(
            candidate.toImmutable(),
            primitiveStepBaseCost(from, candidate) + primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    private void addCoarseClimbNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<CoarseNeighbor> neighbors, long now) {
        if (world == null || current == null || !isClimbableNode(world, current)) {
            return;
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.add(0, dy, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || !isCoarseNavigableNode(world, candidate)
                || !isClimbTransition(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new CoarseNeighbor(
                candidate.toImmutable(),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    private void addCoarseDropNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<CoarseNeighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }
        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos candidate = current.add(0, -drop, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || isFailedDrop(current, candidate, now)
                || !isCoarseNavigableNode(world, candidate)
                || !canSafelyDropTo(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new CoarseNeighbor(
                candidate.toImmutable(),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    private boolean isCoarseNavigableNode(World world, BlockPos footPos) {
        return isNavigableNode(world, footPos) && !requiresBreakingForWaypoint(world, footPos) && !needsPlacedSupport(world, footPos);
    }

    private boolean isCoarsePlannerTraversableMove(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dx > 1 || dz > 1 || (dx == 0 && dz == 0)) {
            return false;
        }
        if (dx == 1 && dz == 1) {
            return false;
        }
        if (dy > 0) {
            if (isClimbTransition(world, from, to)) {
                return true;
            }
            if (requiresInteractableTraversal(world, from, to)) {
                return false;
            }
            return canAttemptJump(world, from, to);
        }
        if (dy < 0 && dx == 0 && dz == 0) {
            return canSafelyDropTo(world, from, to);
        }
        if (requiresInteractableTraversal(world, from, to)) {
            return false;
        }
        return true;
    }

    private boolean isTrappedExcavationState(World world, BlockPos current, BlockPos goal, long now) {
        if (world == null || current == null || goal == null) {
            return false;
        }
        if (goal.getY() < current.getY()) {
            return false;
        }
        return countDirectWalkNeighbors(world, current, current, goal, now) <= 1;
    }

    private boolean matchesEscapeDirection(Move move, Direction direction) {
        if (move == null || direction == null) {
            return true;
        }
        return move.dx == direction.getOffsetX() && move.dz == direction.getOffsetZ();
    }

    private Direction getPreferredEscapeDirection(World world, BlockPos current, BlockPos goal, long now) {
        synchronized (this) {
            if (!committedEscape.isEmpty()) {
                return committedEscape.direction();
            }
        }
        return chooseEscapeDirection(world, current, goal, now);
    }

    private void addDigEscapeNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }

        boolean cramped = countDirectWalkNeighbors(world, current, start, goal, now) <= 1;

        if (!cramped) {
            return;
        }

        if (!canOccupy(world, current.up())) {
            addDigEscapeNeighbor(world, current, current.up(), start, goal, neighbors, now, DIG_ESCAPE_MOVE_PENALTY);
        }
        Direction escapeDirection = getPreferredEscapeDirection(world, current, goal, now);
        if (escapeDirection != null) {
            addDirectedDigEscapeNeighbors(world, current, start, goal, neighbors, now, escapeDirection.getOffsetX(), escapeDirection.getOffsetZ());
        } else {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos breakout = current.offset(direction).up();
                addDigEscapeNeighbor(world, current, breakout, start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY + 0.35D);
            }
        }
    }

    private void addDirectedDigEscapeNeighbors(
        World world,
        BlockPos current,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now,
        int primaryDx,
        int primaryDz
    ) {
        if (primaryDx != 0 || primaryDz != 0) {
            addDigEscapeNeighbor(world, current, current.add(primaryDx, 1, primaryDz), start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY);
            addDigEscapeNeighbor(world, current, current.add(primaryDx, 0, primaryDz), start, goal, neighbors, now, DIG_BREAKOUT_MOVE_PENALTY + 0.2D);
        }
    }

    private void addDigEscapeNeighbor(
        World world,
        BlockPos current,
        BlockPos candidate,
        BlockPos start,
        BlockPos goal,
        List<Neighbor> neighbors,
        long now,
        double extraPenalty
    ) {
        if (candidate == null
            || !isWithinSearchBounds(start, candidate, goal)
            || !isChunkLoaded(world, candidate)
            || isHardDanger(world, candidate)
            || isFailedNode(candidate, now)
            || isFailedEdge(current, candidate, now)) {
            return;
        }

        Neighbor assisted = resolveNeighborAccess(world, current, candidate);
        if (assisted == null) {
            return;
        }
        if (!requiresBreakingForWaypoint(world, candidate) && !needsPlacedSupport(world, candidate)) {
            return;
        }

        neighbors.add(new Neighbor(assisted.vertex(), assisted.cost() + extraPenalty, assisted.primitive()));
    }

    private int countDirectWalkNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, long now) {
        int count = 0;
        for (Move move : MOVES) {
            Neighbor neighbor = findNeighbor(world, current, move.dx, move.dz, start, goal, true);
            if (neighbor == null) {
                continue;
            }
            if (isFailedNode(neighbor.pos(), now) || isFailedEdge(current, neighbor.pos(), now)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private void addClimbNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null || !isClimbableNode(world, current)) {
            return;
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.add(0, dy, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || !isClimbTransition(world, current, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)) {
                continue;
            }
            PlannedPrimitive primitive = createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new Neighbor(
                new SearchVertex(candidate.toImmutable(), primitive.searchType()),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    private void addSafeDropNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }
        for (int drop = 1; drop <= MAX_DROP_DOWN; drop++) {
            BlockPos candidate = current.add(0, -drop, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || isFailedDrop(current, candidate, now)
                || !canSafelyDropTo(world, current, candidate)) {
                continue;
            }
            PlannedPrimitive primitive = createPlannedPrimitive(world, current, candidate, List.of(), null);
            neighbors.add(new Neighbor(
                new SearchVertex(candidate.toImmutable(), primitive.searchType()),
                primitiveStepBaseCost(current, candidate) + primitiveSearchPenalty(world, current, candidate, primitive),
                primitive
            ));
        }
    }

    private void addPillarNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        // Generic A* pillar moves are disabled.
        // Pillaring is handled by the dedicated local escape / committed pillar controller instead,
        // which prevents the planner from scattering micro-pillars into ordinary walking routes.
    }

    private Neighbor findNeighbor(World world, BlockPos current, int dx, int dz, BlockPos start, BlockPos goal, boolean allowRelaxedBounds) {
        if (dx == 0 && dz == 0) {
            return null;
        }

        if (Math.abs(dx) + Math.abs(dz) == 2) {
            if (findNeighbor(world, current, dx, 0, start, goal, true) == null
                || findNeighbor(world, current, 0, dz, start, goal, true) == null) {
                return null;
            }
        }

        int baseX = current.getX() + dx;
        int baseZ = current.getZ() + dz;
        for (int dy = MAX_STEP_UP; dy >= -MAX_DROP_DOWN; dy--) {
            BlockPos candidate = new BlockPos(baseX, current.getY() + dy, baseZ);
            if (!allowRelaxedBounds && !isWithinSearchBounds(start, candidate, goal)) {
                continue;
            }
            if (!isChunkLoaded(world, candidate)) {
                continue;
            }
            Neighbor assistedNeighbor = resolveNeighborAccess(world, current, candidate);
            if (assistedNeighbor == null) {
                continue;
            }
            if (Math.abs(candidate.getY() - current.getY()) > MAX_STEP_UP && candidate.getY() > current.getY()) {
                continue;
            }
            if (isHardDanger(world, candidate)) {
                continue;
            }
            if (!isPlannerTraversableMove(world, current, candidate)) {
                continue;
            }
            return assistedNeighbor;
        }
        return null;
    }

    private boolean isPlannerTraversableMove(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();

        if (dx == 1 && dz == 1 && dy != 0) {
            return false;
        }
        if ((dy > 0 || shouldStepJump(world, from, to)) && !requiresInteractableTraversal(world, from, to)) {
            return canTraverseAscendingStep(world, from, to);
        }
        return true;
    }

    private boolean canTraverseAscendingStep(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        if (canAttemptJump(world, from, to)) {
            return true;
        }
        return canExcavateJumpCorridor(world, from, to);
    }

    private boolean canExcavateJumpCorridor(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dy = to.getY() - from.getY();
        if (dy <= 0 || dx > 1 || dz > 1 || (dx == 1 && dz == 1)) {
            return false;
        }
        BlockPos[] requiredClearance = new BlockPos[] {
            from.up(),
            from.up(2),
            to,
            to.up(),
            to.up(2)
        };
        for (BlockPos pos : requiredClearance) {
            if (!isExcavationClearable(world, pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean isExcavationClearable(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return canOccupy(world, pos) || isBreakableForNavigator(world, pos);
    }

    private boolean isWithinSearchBounds(BlockPos start, BlockPos candidate, BlockPos target) {
        int radius = getSearchRadius(start, target);
        int height = getSearchHeight(start, target);
        return Math.abs(candidate.getX() - start.getX()) <= radius
            && Math.abs(candidate.getZ() - start.getZ()) <= radius
            && Math.abs(candidate.getY() - start.getY()) <= height;
    }

    private List<BlockPos> collectGoalCandidates(World world, BlockPos start, BlockPos target) {
        if (target != null
            && isWithinSearchBounds(start, target, target)
            && isChunkLoaded(world, target)
            && isGoalNodeReachable(world, target)
            && !isHardDanger(world, target)) {
            return List.of(target.toImmutable());
        }

        List<ScoredPos> scored = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int radius = 0; radius <= GOAL_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = MAX_STEP_UP + 1; dy >= -MAX_DROP_DOWN; dy--) {
                        BlockPos candidate = new BlockPos(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                        if (!isWithinSearchBounds(start, candidate, target)
                            || !isChunkLoaded(world, candidate)
                            || !isGoalNodeReachable(world, candidate)
                            || isHardDanger(world, candidate)
                            || !seen.add(candidate)) {
                            continue;
                        }
                        scored.add(new ScoredPos(candidate.toImmutable(), scoreGoalCandidate(world, candidate, target)));
                    }
                }
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredPos::score));
        List<BlockPos> result = new ArrayList<>(Math.min(MAX_GOAL_CANDIDATES, scored.size()));
        for (int i = 0; i < scored.size() && i < MAX_GOAL_CANDIDATES; i++) {
            result.add(scored.get(i).pos());
        }
        return result;
    }

    private double scoreGoalCandidate(World world, BlockPos candidate, BlockPos target) {
        double horizontal = Math.sqrt(horizontalDistanceSq(candidate, target));
        double verticalPenalty = Math.abs(candidate.getY() - target.getY()) * 1.35D;
        double opennessBonus = countOpenNeighbors(world, candidate) * -0.12D;
        double exactTargetBias = candidate.equals(target) ? -2.5D : 0.0D;
        double failedPenalty = isFailedNode(candidate, System.currentTimeMillis()) ? FAILED_MOVE_PENALTY : 0.0D;
        double modificationPenalty = 0.0D;
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, candidate);
        if (breakTargets != null && !breakTargets.isEmpty()) {
            modificationPenalty += PATH_BREAK_ROUTE_PENALTY + BREAK_ASSIST_SURCHARGE + (breakTargets.size() * 3.0D);
        }
        if (needsPlacedSupport(world, candidate)) {
            modificationPenalty += PATH_PLACE_ROUTE_PENALTY + PLACE_ASSIST_SURCHARGE;
        }
        return horizontal + verticalPenalty + opennessBonus + exactTargetBias + failedPenalty + modificationPenalty + terrainPenalty(world, candidate, candidate);
    }

    private int countOpenNeighbors(World world, BlockPos pos) {
        int count = 0;
        for (Move move : MOVES) {
            if (findNeighbor(world, pos, move.dx, move.dz, pos, pos, true) != null) {
                count++;
            }
        }
        return count;
    }

    private boolean isGoalNodeReachable(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (isNavigableNode(world, pos)) {
            return true;
        }
        if (getRequiredBreakTargets(world, pos) == null) {
            return false;
        }
        if (needsPlacedSupport(world, pos)) {
            return false;
        }
        return hasCollision(world, pos.down()) || isWaterNode(world, pos);
    }

    private Neighbor resolveNeighborAccess(World world, BlockPos from, BlockPos candidate) {
        if (world == null || from == null || candidate == null) {
            return null;
        }
        boolean sameColumnUp = candidate.getX() == from.getX()
            && candidate.getZ() == from.getZ()
            && candidate.getY() > from.getY();
        boolean climbTransition = sameColumnUp && isClimbTransition(world, from, candidate);
        if (sameColumnUp && !climbTransition && !canPillarTo(world, from, candidate)) {
            return null;
        }
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, from, candidate);
        if (breakTargets == null) {
            return null;
        }
        if (sameColumnUp && !climbTransition && !breakTargets.isEmpty()) {
            return null;
        }
        boolean navigableCandidate = isNavigableNode(world, candidate);
        boolean requiresSupport = allowBlockPlacing && needsPlacedSupport(world, candidate);
        if (navigableCandidate && breakTargets.isEmpty() && !requiresSupport) {
            PlannedPrimitive primitive = createPlannedPrimitive(world, from, candidate, List.of(), null);
            return new Neighbor(
                new SearchVertex(candidate.toImmutable(), primitive.searchType()),
                primitiveSearchPenalty(world, from, candidate, primitive),
                primitive
            );
        }
        if (!breakTargets.isEmpty()) {
            if (!allowBlockBreaking) {
                return null;
            }
            if (shouldAvoidGoalModification(world, candidate)) {
                return null;
            }
            if (hasInteractableAlternative(world, from, candidate, targetPos)) {
                return null;
            }
        }

        if (requiresSupport) {
            BlockPos activeTarget = targetPos;
            if (activeTarget != null && candidate.down().equals(activeTarget)) {
                return null;
            }
            if (shouldAvoidGoalModification(world, candidate)) {
                return null;
            }
            if (!canUsePlacedSupportMove(world, from, candidate)) {
                return null;
            }
            if (hasNaturalGroundAlternative(world, from, candidate, activeTarget)) {
                return null;
            }
            if (!canPlaceSupportAt(world, candidate.down())) {
                return null;
            }
        } else if (!hasCollision(world, candidate.down()) && !isWaterNode(world, candidate)) {
            return null;
        }

        BlockPos placeTarget = requiresSupport ? candidate.down().toImmutable() : null;
        List<BlockPos> normalizedBreakTargets = breakTargets == null ? List.of() : breakTargets.stream()
            .filter(pos -> pos != null && isBreakableForNavigator(world, pos))
            .map(BlockPos::toImmutable)
            .toList();
        PlannedPrimitive primitive = createPlannedPrimitive(world, from, candidate, normalizedBreakTargets, placeTarget);
        return new Neighbor(
            new SearchVertex(candidate.toImmutable(), primitive.searchType()),
            primitiveSearchPenalty(world, from, candidate, primitive),
            primitive
        );
    }

    private double primitiveStepBaseCost(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return 1.0D;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        if (dx == 1 && dz == 1) {
            return Math.sqrt(2.0D);
        }
        return 1.0D;
    }

    private double primitiveSearchPenalty(World world, BlockPos from, BlockPos to, PlannedPrimitive primitive) {
        if (primitive == null) {
            return moveTypePenalty(world, from, to);
        }
        double penalty = moveTypePenalty(world, from, to);
        if (primitive.isPillar()) {
            penalty += SEARCH_PILLAR_PENALTY;
        } else if (primitive.isJump()) {
            penalty += SEARCH_JUMP_PENALTY;
        } else if (primitive.isDescend()) {
            penalty += SEARCH_DESCEND_PENALTY;
        } else if (primitive.isClimb()) {
            penalty += SEARCH_CLIMB_PENALTY;
        } else if (primitive.isSwim()) {
            penalty += SEARCH_SWIM_PENALTY;
        } else if (primitive.isInteractable()) {
            penalty += SEARCH_INTERACT_PENALTY;
        }
        if (primitive.requiresBreak()) {
            penalty += SEARCH_BREAK_PENALTY + BREAK_ASSIST_SURCHARGE;
            for (BlockPos breakTarget : primitive.breakTargets()) {
                penalty += breakPenalty(world, breakTarget) * 1.25D;
            }
        }
        if (primitive.requiresPlace()) {
            penalty += SEARCH_PLACE_PENALTY + PLACE_MOVE_PENALTY + PLACE_ASSIST_SURCHARGE;
        }
        if (primitive.requiresWorldModification() && isTreeCanopyNode(world, to)) {
            penalty += TREE_CANOPY_MODIFICATION_PENALTY;
        }
        return penalty;
    }

    private boolean canPillarTo(World world, BlockPos from, BlockPos candidate) {
        if (!allowBlockPlacing || world == null || from == null || candidate == null) {
            return false;
        }
        if (candidate.getX() != from.getX() || candidate.getZ() != from.getZ() || candidate.getY() != from.getY() + 1) {
            return false;
        }
        if (!canOccupy(world, candidate) || !canOccupy(world, candidate.up())) {
            return false;
        }
        if (isHardDanger(world, candidate) || isWaterNode(world, candidate)) {
            return false;
        }
        return canPlaceSupportAt(world, candidate.down(), true);
    }

    private boolean canContinuePillarTo(World world, BlockPos pillarBase, BlockPos pillarTarget) {
        if (!allowBlockPlacing || world == null || pillarBase == null || pillarTarget == null) {
            return false;
        }
        if (!pillarTarget.equals(pillarBase.up())) {
            return false;
        }
        if (!canOccupy(world, pillarTarget) || !canOccupy(world, pillarTarget.up())) {
            return false;
        }
        if (isHardDanger(world, pillarTarget) || isWaterNode(world, pillarTarget)) {
            return false;
        }
        return canPlaceSupportAt(world, pillarBase, true);
    }

    private boolean canUsePlacedSupportMove(World world, BlockPos from, BlockPos candidate) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        if (candidate.getY() != from.getY()) {
            return false;
        }
        int dx = Math.abs(candidate.getX() - from.getX());
        int dz = Math.abs(candidate.getZ() - from.getZ());
        if (dx + dz != 1) {
            return false;
        }
        if (!canOccupy(world, candidate) || !canOccupy(world, candidate.up())) {
            return false;
        }
        if (requiresBreakingForWaypoint(world, candidate)) {
            return false;
        }
        return true;
    }

    private boolean hasNaturalGroundAlternative(World world, BlockPos from, BlockPos candidate, BlockPos activeTarget) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        double candidateTargetDistance = activeTarget == null
            ? horizontalDistanceSq(from, candidate)
            : horizontalDistanceSq(candidate, activeTarget);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos alternative = from.offset(direction);
            if (alternative.equals(candidate) || alternative.equals(from)) {
                continue;
            }
            if (!isNavigableNode(world, alternative)) {
                continue;
            }
            if (requiresBreakingForWaypoint(world, alternative) || needsPlacedSupport(world, alternative)) {
                continue;
            }
            if (isHardDanger(world, alternative)) {
                continue;
            }
            double alternativeTargetDistance = activeTarget == null
                ? horizontalDistanceSq(from, alternative)
                : horizontalDistanceSq(alternative, activeTarget);
            if (alternativeTargetDistance <= candidateTargetDistance + 1.0D) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInteractableAlternative(World world, BlockPos from, BlockPos candidate, BlockPos activeTarget) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        double candidateTargetDistance = activeTarget == null
            ? horizontalDistanceSq(from, candidate)
            : horizontalDistanceSq(candidate, activeTarget);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos alternative = from.offset(direction);
            if (alternative.equals(candidate) || alternative.equals(from)) {
                continue;
            }
            if (isHardDanger(world, alternative)) {
                continue;
            }
            if (!(requiresInteractableTraversal(world, from, alternative) || hasPathOpenableAhead(world, from, alternative))) {
                continue;
            }
            if (!isWaypointActionable(world, alternative)) {
                continue;
            }
            double alternativeTargetDistance = activeTarget == null
                ? horizontalDistanceSq(from, alternative)
                : horizontalDistanceSq(alternative, activeTarget);
            if (alternativeTargetDistance <= candidateTargetDistance + 2.0D) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldAvoidGoalModification(World world, BlockPos candidate) {
        if (world == null || candidate == null) {
            return false;
        }
        BlockPos activeTarget = targetPos;
        if (activeTarget == null || !isStandable(world, activeTarget)) {
            return false;
        }
        if (candidate.equals(activeTarget)) {
            return false;
        }
        return horizontalDistanceSq(candidate, activeTarget) <= GOAL_MODIFICATION_AVOID_DISTANCE_SQ
            && Math.abs(candidate.getY() - activeTarget.getY()) <= 1;
    }

    private BlockPos findNearbyStandable(World world, BlockPos around, int maxRadius) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = MAX_STEP_UP + 1; dy >= -MAX_DROP_DOWN; dy--) {
                        BlockPos candidate = new BlockPos(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                        if (isChunkLoaded(world, candidate) && isNavigableNode(world, candidate) && !isHardDanger(world, candidate)) {
                            return candidate.toImmutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private double moveTypePenalty(World world, BlockPos from, BlockPos to) {
        MoveType moveType = classifyMoveType(world, from, to);
        return switch (moveType) {
            case STRAIGHT -> 0.0D;
            case DIAGONAL -> 0.22D;
            case STEP_UP -> 0.7D;
            case DROP -> 0.12D;
            case WATER_ENTER -> 0.8D;
            case WATER_SWIM -> 1.1D;
            case WATER_EXIT -> 0.55D;
            case CLIMB_UP -> 0.55D;
            case CLIMB_DOWN -> 0.2D;
            case INTERACTABLE -> 0.18D;
        };
    }

    private MoveType classifyMoveType(World world, BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return MoveType.STRAIGHT;
        }
        boolean fromWater = isWaterNode(world, from);
        boolean toWater = isWaterNode(world, to);
        if (!fromWater && toWater) {
            return MoveType.WATER_ENTER;
        }
        if (fromWater && toWater) {
            return MoveType.WATER_SWIM;
        }
        if (fromWater) {
            return MoveType.WATER_EXIT;
        }
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            return to.getY() > from.getY() ? MoveType.CLIMB_UP : MoveType.CLIMB_DOWN;
        }
        if (requiresInteractableTraversal(world, from, to)) {
            return MoveType.INTERACTABLE;
        }

        int deltaY = to.getY() - from.getY();
        if (deltaY > 0) {
            return MoveType.STEP_UP;
        }
        if (deltaY < 0) {
            return MoveType.DROP;
        }
        return (from.getX() != to.getX() && from.getZ() != to.getZ()) ? MoveType.DIAGONAL : MoveType.STRAIGHT;
    }

    private void rememberFailedMove(BlockPos from, BlockPos to, long now) {
        if (to != null) {
            failedNodes.put(to.toImmutable(), now + FAILED_MOVE_MEMORY_MS);
        }
        if (from != null && to != null) {
            failedEdges.put(new EdgeKey(from.toImmutable(), to.toImmutable()), now + FAILED_MOVE_MEMORY_MS);
        }
    }

    private void rememberFailedBreak(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        if (from != null && to != null) {
            failedBreaks.put(new EdgeKey(from.toImmutable(), to.toImmutable()), now + FAILED_BREAK_MEMORY_MS);
        }
    }

    private void rememberFailedJump(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        if (from != null && to != null) {
            failedJumps.put(new EdgeKey(from.toImmutable(), to.toImmutable()), now + FAILED_JUMP_MEMORY_MS);
        }
    }

    private void rememberFailedDrop(BlockPos from, BlockPos to, long now) {
        rememberFailedMove(from, to, now);
        if (from != null && to != null) {
            failedDrops.put(new EdgeKey(from.toImmutable(), to.toImmutable()), now + FAILED_DROP_MEMORY_MS);
        }
    }

    private void pruneFailureMemory(long now) {
        failedNodes.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedEdges.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedBreaks.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedJumps.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedDrops.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private boolean isFailedNode(BlockPos pos, long now) {
        if (pos == null) {
            return false;
        }
        Long expiresAt = failedNodes.get(pos);
        return expiresAt != null && expiresAt > now;
    }

    private boolean isFailedEdge(BlockPos from, BlockPos to, long now) {
        if (from == null || to == null) {
            return false;
        }
        Long expiresAt = failedEdges.get(new EdgeKey(from, to));
        return expiresAt != null && expiresAt > now;
    }

    private boolean isFailedBreak(BlockPos from, BlockPos to, long now) {
        if (from == null || to == null) {
            return false;
        }
        Long expiresAt = failedBreaks.get(new EdgeKey(from, to));
        return expiresAt != null && expiresAt > now;
    }

    private boolean isFailedJump(BlockPos from, BlockPos to, long now) {
        if (from == null || to == null) {
            return false;
        }
        Long expiresAt = failedJumps.get(new EdgeKey(from, to));
        return expiresAt != null && expiresAt > now;
    }

    private boolean isFailedDrop(BlockPos from, BlockPos to, long now) {
        if (from == null || to == null) {
            return false;
        }
        Long expiresAt = failedDrops.get(new EdgeKey(from, to));
        return expiresAt != null && expiresAt > now;
    }

    private boolean isGoal(BlockPos pos, BlockPos target, Set<BlockPos> goalSet) {
        return goalSet.contains(pos);
    }

    private double heuristic(BlockPos pos, List<BlockPos> goals) {
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos goal : goals) {
            double dx = Math.abs(pos.getX() - goal.getX());
            double dz = Math.abs(pos.getZ() - goal.getZ());
            double min = Math.min(dx, dz);
            double max = Math.max(dx, dz);
            double octile = min * Math.sqrt(2.0D) + (max - min);
            double verticalPenalty = Math.abs(pos.getY() - goal.getY()) * 1.15D;
            best = Math.min(best, octile + verticalPenalty);
        }
        return best == Double.POSITIVE_INFINITY ? 0.0D : best * HEURISTIC_WEIGHT;
    }

    private double elevationPenalty(BlockPos from, BlockPos to) {
        int delta = to.getY() - from.getY();
        if (delta > 0) {
            return delta * 0.35D;
        }
        if (delta < 0) {
            return Math.abs(delta) * 0.12D;
        }
        return 0.0D;
    }

    private double horizontalDistanceSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isWithinGoalArrivalRange(ClientPlayerEntity player, BlockPos target) {
        if (player == null || target == null) {
            return false;
        }
        Box targetBox = new Box(
            target.getX(),
            target.getY(),
            target.getZ(),
            target.getX() + 1.0D,
            target.getY() + 1.0D,
            target.getZ() + 1.0D
        );
        return player.getBoundingBox().intersects(targetBox);
    }

    private boolean hasReachedGoal(
        World world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos completionTarget,
        BlockPos requestedTarget
    ) {
        if (isWithinGoalArrivalRange(player, completionTarget)) {
            return true;
        }
        if (world == null || playerFootPos == null || requestedTarget == null) {
            return false;
        }
        if (isNavigableNode(world, requestedTarget)) {
            return false;
        }
        return horizontalDistanceSq(playerFootPos, requestedTarget) <= 2.25D
            && Math.abs(playerFootPos.getY() - requestedTarget.getY()) <= 1;
    }

    private boolean isNavigableNode(World world, BlockPos footPos) {
        return isStandable(world, footPos) || isClimbNode(world, footPos);
    }

    private boolean isStandable(World world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        if (!world.isInBuildLimit(footPos) || !isChunkLoaded(world, footPos)) {
            return false;
        }
        if (isLava(world.getFluidState(footPos)) || isLava(world.getFluidState(footPos.up()))) {
            return false;
        }
        if (isWaterNode(world, footPos)) {
            return canOccupy(world, footPos) && canOccupy(world, footPos.up());
        }
        BlockPos below = footPos.down();
        if (!hasWalkSupport(world, below)) {
            return false;
        }
        if (!canOccupy(world, footPos)) {
            return false;
        }
        return canOccupy(world, footPos.up());
    }

    private boolean isClimbNode(World world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        if (!world.isInBuildLimit(footPos) || !isChunkLoaded(world, footPos)) {
            return false;
        }
        if (isLava(world.getFluidState(footPos)) || isLava(world.getFluidState(footPos.up()))) {
            return false;
        }
        if (!canOccupy(world, footPos) || !canOccupy(world, footPos.up())) {
            return false;
        }
        return isClimbableNode(world, footPos)
            || isClimbableNode(world, footPos.up())
            || isClimbableNode(world, footPos.down());
    }

    private boolean hasCollision(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    private boolean needsPlacedSupport(World world, BlockPos footPos) {
        return world != null
            && footPos != null
            && !hasWalkSupport(world, footPos.down())
            && !isWaterNode(world, footPos);
    }

    private boolean hasWalkSupport(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (hasCollision(world, pos)) {
            return true;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return false;
        }
        if (!canOccupy(world, pos)) {
            return false;
        }
        BlockPos below = pos.down();
        return hasCollision(world, below);
    }

    private boolean canOccupy(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return true;
        }
        if (state.isReplaceable()) {
            return true;
        }
        if (isClimbableBlock(state) || isPathOpenable(state)) {
            return true;
        }
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private boolean isUnstableSupportBlock(BlockState state) {
        return state != null && state.isIn(BlockTags.LEAVES);
    }

    private boolean isTreeCanopyNode(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (isUnstableSupportBlock(world.getBlockState(pos.down()))) {
            return true;
        }
        int leafCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos sample = pos.add(dx, dy, dz);
                    if (isUnstableSupportBlock(world.getBlockState(sample))) {
                        leafCount++;
                        if (leafCount >= 2) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<BlockPos> getRequiredBreakTargets(World world, BlockPos footPos) {
        return getRequiredBreakTargets(world, footPos, footPos);
    }

    private List<BlockPos> getRequiredBreakTargets(World world, BlockPos from, BlockPos footPos) {
        if (world == null || footPos == null) {
            return null;
        }
        boolean ascending = from != null
            && !from.equals(footPos)
            && footPos.getY() > from.getY();
        List<BlockPos> targets = new ArrayList<>(ascending ? 5 : 3);
        if (ascending && from != null) {
            if (!collectBreakTarget(world, from.up(), targets)) {
                return null;
            }
            if (!collectBreakTarget(world, from.up(2), targets)) {
                return null;
            }
        }
        if (!collectBreakTarget(world, footPos, targets)) {
            return null;
        }
        if (!collectBreakTarget(world, footPos.up(), targets)) {
            return null;
        }
        if (ascending && !collectBreakTarget(world, footPos.up(2), targets)) {
            return null;
        }
        return targets;
    }

    private boolean collectBreakTarget(World world, BlockPos pos, List<BlockPos> targets) {
        if (world == null || pos == null) {
            return false;
        }
        if (canOccupy(world, pos)) {
            return true;
        }
        if (!isBreakableForNavigator(world, pos)) {
            return false;
        }
        targets.add(pos.toImmutable());
        return true;
    }

    private BlockPos resolvePlanningTarget(ClientWorld world, BlockPos start, BlockPos target) {
        if (isWithinSearchBounds(start, target, target) && isChunkLoaded(world, target)) {
            return target.toImmutable();
        }

        Vec3d startCenter = Vec3d.ofCenter(start);
        Vec3d targetCenter = Vec3d.ofCenter(target);
        Vec3d direction = targetCenter.subtract(startCenter);
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDistance < 0.001D) {
            return findNearbyStandable(world, target, 4);
        }

        Vec3d normalized = direction.normalize();
        int searchRadius = getSearchRadius(start, target);
        int searchHeight = getSearchHeight(start, target);
        double maxDistance = Math.min(horizontalDistance, searchRadius - 2.0D);
        BlockPos best = null;
        for (double distance = Math.max(4.0D, maxDistance); distance >= 4.0D; distance -= 2.0D) {
            Vec3d sample = startCenter.add(normalized.multiply(distance));
            BlockPos projected = BlockPos.ofFloored(
                sample.x,
                MathHelper.clamp((int) Math.round(targetCenter.y), start.getY() - searchHeight, start.getY() + searchHeight),
                sample.z
            );
            BlockPos candidate = findNearbyStandable(world, projected, 4);
            if (candidate != null && isChunkLoaded(world, candidate)) {
                best = candidate;
                break;
            }
        }

        if (best != null) {
            return best;
        }

        return findLoadedFrontierNear(world, start, target);
    }

    private BlockPos findLoadedFrontierNear(ClientWorld world, BlockPos start, BlockPos target) {
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int searchRadius = getSearchRadius(start, target);
        int searchHeight = getSearchHeight(start, target);
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < searchRadius - 2) {
                    continue;
                }
                for (int dy = -searchHeight; dy <= searchHeight; dy++) {
                    BlockPos candidate = new BlockPos(start.getX() + dx, start.getY() + dy, start.getZ() + dz);
                    if (!isChunkLoaded(world, candidate) || !isNavigableNode(world, candidate) || isHardDanger(world, candidate)) {
                        continue;
                    }
                    double score = horizontalDistanceSq(candidate, target) + terrainPenalty(world, candidate, candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private int getSearchRadius(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return SEARCH_RADIUS;
        }
        int horizontal = (int) Math.ceil(Math.sqrt(horizontalDistanceSq(start, target)));
        return Math.max(SEARCH_RADIUS, Math.min(MAX_SEARCH_RADIUS, horizontal + 8));
    }

    private int getSearchHeight(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return SEARCH_HEIGHT;
        }
        int vertical = Math.abs(target.getY() - start.getY());
        return Math.max(SEARCH_HEIGHT, Math.min(MAX_SEARCH_HEIGHT, vertical + 8));
    }

    private boolean isChunkLoaded(World world, BlockPos pos) {
        if (!(world instanceof ClientWorld clientWorld) || pos == null) {
            return false;
        }
        return clientWorld.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private boolean shouldStepJump(World world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        if (requiresInteractableTraversal(world, from, waypoint)) {
            return false;
        }
        if (waypoint.getY() > from.getY()) {
            return true;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return hasCollision(world, front) && !canOccupy(world, front) && canOccupy(world, front.up());
    }

    private boolean isBlockedTowardWaypoint(World world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        if (stepX != 0 && stepZ != 0) {
            BlockPos xFront = new BlockPos(from.getX() + stepX, from.getY(), from.getZ());
            BlockPos zFront = new BlockPos(from.getX(), from.getY(), from.getZ() + stepZ);
            boolean xBlocked = isMovementObstructed(world, xFront);
            boolean zBlocked = isMovementObstructed(world, zFront);
            return xBlocked
                && zBlocked
                && !requiresBreakingForWaypoint(world, waypoint)
                && !requiresInteractableTraversal(world, from, waypoint)
                && !hasPathOpenableAhead(world, from, waypoint);
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return isMovementObstructed(world, front)
            && !requiresBreakingForWaypoint(world, waypoint)
            && !requiresInteractableTraversal(world, from, waypoint)
            && !hasPathOpenableAhead(world, from, waypoint);
    }

    private boolean isMovementObstructed(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos head = pos.up();
        return (hasCollision(world, pos) || hasCollision(world, head))
            && (!canOccupy(world, pos) || !canOccupy(world, head));
    }

    private boolean canAttemptJump(World world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        if (!canOccupy(world, from.up(2))) {
            return false;
        }

        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);

        if (stepX != 0 || stepZ != 0) {
            if (!canOccupy(world, front.up())) {
                return false;
            }
            if (waypoint.getY() > from.getY() && !canOccupy(world, front.up(2))) {
                return false;
            }
            if (waypoint.getY() > from.getY() && isCorneredJump(world, from, stepX, stepZ)) {
                return false;
            }
        }

        if (waypoint.getY() > from.getY() && !canOccupy(world, waypoint.up())) {
            return false;
        }

        return true;
    }

    private Vec3d resolveWaypointAimPoint(
        World world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos climbAnchor,
        PlannedPrimitive plannedPrimitive,
        double playerY
    ) {
        BlockPos horizontalAim = climbAnchor != null ? climbAnchor : waypoint;
        if (horizontalAim == null) {
            return Vec3d.ZERO;
        }
        double aimX = horizontalAim.getX() + 0.5D;
        double aimZ = horizontalAim.getZ() + 0.5D;
        Vec3d cornerAim = resolveCornerApproachAimPoint(world, playerFootPos, waypoint, climbAnchor, plannedPrimitive, playerY);
        if (cornerAim != null) {
            return cornerAim;
        }
        if (playerFootPos != null
            && waypoint != null
            && plannedPrimitive != null
            && isJumpPrimitive(plannedPrimitive)
            && waypoint.getY() > playerFootPos.getY()) {
            int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
            int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
            if (stepX != 0 || stepZ != 0) {
                aimX -= stepX * 0.32D;
                aimZ -= stepZ * 0.32D;
            }
        }
        return new Vec3d(aimX, playerY, aimZ);
    }

    private Vec3d resolveCornerApproachAimPoint(
        World world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos climbAnchor,
        PlannedPrimitive plannedPrimitive,
        double playerY
    ) {
        if (world == null || playerFootPos == null || waypoint == null || climbAnchor != null || plannedPrimitive == null) {
            return null;
        }
        if (plannedPrimitive.requiresCommittedAction()) {
            return null;
        }
        int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
        int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
        if (stepX == 0 || stepZ == 0) {
            return null;
        }
        if (Math.abs(waypoint.getY() - playerFootPos.getY()) > 1) {
            return null;
        }
        BlockPos xFront = new BlockPos(playerFootPos.getX() + stepX, playerFootPos.getY(), playerFootPos.getZ());
        BlockPos zFront = new BlockPos(playerFootPos.getX(), playerFootPos.getY(), playerFootPos.getZ() + stepZ);
        boolean xBlocked = isMovementObstructed(world, xFront);
        boolean zBlocked = isMovementObstructed(world, zFront);
        if (xBlocked == zBlocked) {
            return null;
        }
        BlockPos approach = xBlocked ? zFront : xFront;
        if (!canOccupy(world, approach) || !canOccupy(world, approach.up())) {
            return null;
        }
        return new Vec3d(approach.getX() + 0.5D, playerY, approach.getZ() + 0.5D);
    }

    private BlockPos resolveMinedAscentAdvanceBlock(BlockPos from, BlockPos waypoint) {
        if (from == null || waypoint == null) {
            return null;
        }
        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return null;
        }
        return new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
    }

    private boolean canAttemptMiningAdvanceJump(World world, BlockPos from, BlockPos waypoint) {
        if (world == null || from == null || waypoint == null) {
            return false;
        }
        if (!isExcavationClearable(world, from.up(2))) {
            return false;
        }

        int stepX = Integer.compare(waypoint.getX(), from.getX());
        int stepZ = Integer.compare(waypoint.getZ(), from.getZ());
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);

        if (stepX != 0 || stepZ != 0) {
            if (!isExcavationClearable(world, front.up())) {
                return false;
            }
            if (waypoint.getY() > from.getY() && !isExcavationClearable(world, front.up(2))) {
                return false;
            }
            if (waypoint.getY() > from.getY() && isCorneredJump(world, from, stepX, stepZ)) {
                return false;
            }
        }

        if (waypoint.getY() > from.getY() && !isExcavationClearable(world, waypoint.up())) {
            return false;
        }

        return true;
    }

    private boolean isCorneredJump(World world, BlockPos from, int stepX, int stepZ) {
        if (world == null || from == null || (stepX == 0 && stepZ == 0)) {
            return false;
        }
        BlockPos left = new BlockPos(from.getX() - stepZ, from.getY(), from.getZ() + stepX);
        BlockPos right = new BlockPos(from.getX() + stepZ, from.getY(), from.getZ() - stepX);
        boolean leftBlocked = hasCollision(world, left) && hasCollision(world, left.up());
        boolean rightBlocked = hasCollision(world, right) && hasCollision(world, right.up());
        return leftBlocked && rightBlocked;
    }

    private double terrainPenalty(World world, BlockPos from, BlockPos to) {
        double penalty = 0.0D;
        if (isWaterNode(world, to)) {
            penalty += WATER_PENALTY;
            if (waterMode == WaterMode.AVOID) {
                penalty += WATER_AVOIDANCE_PENALTY;
            }
            if (!isStillWater(world, to)) {
                penalty += FLOWING_WATER_PENALTY;
            }
            if (waterDepth(world, to) >= 2) {
                penalty += DEEP_WATER_PENALTY;
            }
            if (isDangerousWater(world, to)) {
                penalty += WATER_DANGER_PENALTY;
            }
            if (!hasSafeWaterExit(world, to)) {
                penalty += WATER_NO_EXIT_PENALTY;
            }
        }
        if (!hasCollision(world, to.down()) && !isWaterNode(world, to)) {
            penalty += EDGE_PENALTY;
        }
        if (isNearDanger(world, to)) {
            penalty += DANGER_PENALTY;
        }
        if (from != null && isWaterNode(world, from) && !isWaterNode(world, to)) {
            penalty += 0.5D;
        }
        if (isTreeCanopyNode(world, to)) {
            penalty += TREE_CANOPY_PENALTY;
        }
        return penalty;
    }

    private boolean isWaterNode(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isWater(world.getFluidState(pos)) || isWater(world.getFluidState(pos.up()));
    }

    private boolean isStillWater(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        FluidState fluid = world.getFluidState(pos);
        FluidState fluidAbove = world.getFluidState(pos.up());
        return (isWater(fluid) && fluid.isStill()) || (isWater(fluidAbove) && fluidAbove.isStill());
    }

    private int waterDepth(World world, BlockPos pos) {
        if (world == null || pos == null || !isWaterNode(world, pos)) {
            return 0;
        }
        int depth = 0;
        BlockPos cursor = pos;
        while (depth < 4 && isWaterNode(world, cursor)) {
            depth++;
            cursor = cursor.down();
        }
        return depth;
    }

    private boolean hasSafeWaterExit(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        for (Move move : MOVES) {
            BlockPos candidate = new BlockPos(pos.getX() + move.dx, pos.getY(), pos.getZ() + move.dz);
            if (isChunkLoaded(world, candidate) && !isWaterNode(world, candidate) && isStandable(world, candidate) && !isHardDanger(world, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDangerousWater(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        BlockState below = world.getBlockState(pos.down());
        if (state.isOf(Blocks.BUBBLE_COLUMN) || below.isOf(Blocks.BUBBLE_COLUMN)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos adjacent = pos.add(dx, 0, dz);
                if (isLava(world.getFluidState(adjacent)) || isLava(world.getFluidState(adjacent.up()))) {
                    return true;
                }
                if (isDangerousBlock(world.getBlockState(adjacent)) || isDangerousBlock(world.getBlockState(adjacent.down()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWater(FluidState fluidState) {
        return fluidState != null && fluidState.isOf(Fluids.WATER);
    }

    private boolean isLava(FluidState fluidState) {
        return fluidState != null && fluidState.isOf(Fluids.LAVA);
    }

    private boolean isHardDanger(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isDangerousBlock(world.getBlockState(pos))
            || isDangerousBlock(world.getBlockState(pos.down()))
            || isLava(world.getFluidState(pos))
            || isLava(world.getFluidState(pos.up()));
    }

    private boolean isNearDanger(World world, BlockPos pos) {
        if (isHardDanger(world, pos)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos adjacent = pos.add(dx, 0, dz);
                if (isHardDanger(world, adjacent)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDangerousBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.LAVA)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE)
            || state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.WITHER_ROSE)
            || state.isOf(Blocks.POWDER_SNOW);
    }

    private boolean isBreakableForNavigator(World world, BlockPos pos) {
        if (!allowBlockBreaking || world == null || pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir() || isPathOpenable(state) || isClimbableBlock(state)) {
            return false;
        }
        if (state.isOf(Blocks.BEDROCK)
            || state.isOf(Blocks.BARRIER)
            || state.isOf(Blocks.COMMAND_BLOCK)
            || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
            || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
            || state.isOf(Blocks.STRUCTURE_BLOCK)
            || state.isOf(Blocks.STRUCTURE_VOID)
            || state.isOf(Blocks.JIGSAW)
            || state.isOf(Blocks.END_PORTAL_FRAME)
            || state.isOf(Blocks.END_PORTAL)
            || state.isOf(Blocks.NETHER_PORTAL)) {
            return false;
        }
        return state.getHardness(world, pos) >= 0.0F;
    }

    private double breakPenalty(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return BREAK_MOVE_PENALTY;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return 0.0D;
        }
        float hardness = state.getHardness(world, pos);
        if (hardness < 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        return BREAK_MOVE_PENALTY + Math.max(0.0D, hardness * 1.2D);
    }

    private boolean canPlaceSupportAt(World world, BlockPos pos) {
        return canPlaceSupportAt(world, pos, false);
    }

    private boolean canPlaceSupportAt(World world, BlockPos pos, boolean allowOccupied) {
        if (!allowBlockPlacing || world == null || pos == null) {
            return false;
        }
        if (!allowOccupied && !canOccupy(world, pos)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.offset(direction);
            if (adjacent.equals(pos.up())) {
                continue;
            }
            if (hasCollision(world, adjacent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClimbableNode(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isClimbableBlock(world.getBlockState(pos));
    }

    private BlockPos resolveClimbAnchor(World world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null) {
            return null;
        }
        if (isClimbableNode(world, playerFootPos)) {
            return playerFootPos;
        }
        if (playerFootPos != null && isClimbableNode(world, playerFootPos.up())) {
            return playerFootPos.up();
        }
        if (waypoint != null && isClimbableNode(world, waypoint)) {
            return waypoint;
        }
        if (waypoint != null && isClimbableNode(world, waypoint.down())) {
            return waypoint.down();
        }
        return null;
    }

    private boolean isClimbableBlock(BlockState state) {
        return state != null && state.isIn(BlockTags.CLIMBABLE);
    }

    private boolean isPathOpenable(BlockState state) {
        return state != null
            && (state.isIn(BlockTags.DOORS)
            || state.isIn(BlockTags.TRAPDOORS)
            || state.isIn(BlockTags.FENCE_GATES));
    }

    private boolean isClimbTransition(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        return canOccupy(world, to)
            && canOccupy(world, to.up())
            && (isClimbableNode(world, from)
            || isClimbableNode(world, to)
            || isClimbableNode(world, from.up())
            || isClimbableNode(world, to.up())
            || isClimbableNode(world, to.down()));
    }

    private boolean canSafelyDropTo(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null || to.getY() >= from.getY()) {
            return false;
        }
        if (from.getY() - to.getY() > MAX_SAFE_FALL_DISTANCE) {
            return false;
        }
        int horizontalDx = Math.abs(to.getX() - from.getX());
        int horizontalDz = Math.abs(to.getZ() - from.getZ());
        if (horizontalDx > 1 || horizontalDz > 1 || horizontalDx + horizontalDz > 1) {
            return false;
        }
        if (horizontalDx != 0 || horizontalDz != 0) {
            BlockPos entry = new BlockPos(to.getX(), from.getY(), to.getZ());
            if (!canOccupy(world, entry) || !canOccupy(world, entry.up())) {
                return false;
            }
        }
        for (int offset = 1; offset <= from.getY() - to.getY(); offset++) {
            BlockPos passThrough = new BlockPos(to.getX(), from.getY() - offset, to.getZ());
            if (!canOccupy(world, passThrough) || !canOccupy(world, passThrough.up())) {
                return false;
            }
        }
        return true;
    }

    private boolean requiresInteractableTraversal(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int stepX = Integer.compare(to.getX(), from.getX());
        int stepZ = Integer.compare(to.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        return isBlockingInteractable(world, new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ))
            || isBlockingInteractable(world, new BlockPos(from.getX() + stepX, from.getY() + 1, from.getZ() + stepZ))
            || isBlockingInteractable(world, to)
            || isBlockingInteractable(world, to.up());
    }

    private boolean hasPathOpenableAhead(World world, BlockPos from, BlockPos to) {
        if (world == null || from == null || to == null) {
            return false;
        }
        int stepX = Integer.compare(to.getX(), from.getX());
        int stepZ = Integer.compare(to.getZ(), from.getZ());
        if (stepX == 0 && stepZ == 0) {
            return false;
        }
        BlockPos front = new BlockPos(from.getX() + stepX, from.getY(), from.getZ() + stepZ);
        return isPathOpenable(world.getBlockState(front)) || isPathOpenable(world.getBlockState(front.up()));
    }

    private boolean isBlockingInteractable(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!isPathOpenable(state)) {
            return false;
        }
        return state.contains(Properties.OPEN) && !state.get(Properties.OPEN);
    }

    private void tryUseInteractables(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || waypoint == null) {
            return;
        }
        if (now - lastInteractAtMs < 250L || client.interactionManager == null) {
            return;
        }

        int stepX = Integer.compare(waypoint.getX(), playerFootPos.getX());
        int stepZ = Integer.compare(waypoint.getZ(), playerFootPos.getZ());
        List<BlockPos> candidates = new ArrayList<>(8);
        candidates.add(playerFootPos.down());
        candidates.add(playerFootPos);
        candidates.add(playerFootPos.up());
        if (stepX != 0 || stepZ != 0) {
            BlockPos front = new BlockPos(playerFootPos.getX() + stepX, playerFootPos.getY(), playerFootPos.getZ() + stepZ);
            candidates.add(front.down());
            candidates.add(front);
            candidates.add(front.up());
        }
        candidates.add(waypoint.down());
        candidates.add(waypoint);
        candidates.add(waypoint.up());

        for (BlockPos candidate : candidates) {
            if (!isBlockingInteractable(world, candidate)) {
                continue;
            }
            client.interactionManager.interactBlock(
                player,
                Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(candidate), Direction.UP, candidate, false)
            );
            player.swingHand(Hand.MAIN_HAND);
            synchronized (this) {
                lastInteractAtMs = now;
            }
            return;
        }
    }

    private boolean handleWaypointBlockInteraction(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
            if (activeBreakTarget != null && canOccupy(world, activeBreakTarget)) {
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
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3d currentPos,
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
            : canOccupy(world, fallbackTarget);
        if (!timedOut && !targetGone) {
            noteControllerActivity(now);
            return false;
        }

        rememberFailedBreak(playerFootPos, fallbackTarget, now);
        recoverFromStuck(
            client,
            world,
            playerFootPos,
            waypoint,
            target,
            currentPos,
            now,
            timedOut ? "mine redirect" : "mine target invalidated",
            timedOut ? "mine timeout" : "mine target invalid"
        );
        return true;
    }

    private boolean handleCommittedMiningInteraction(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
            && player.isOnGround()
            && canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)) {
            Vec3d currentVelocity = player.getVelocity();
            double dx = waypoint.getX() + 0.5D - player.getX();
            double dz = waypoint.getZ() + 0.5D - player.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 0.0001D) {
                player.setVelocity(
                    currentVelocity.x + (dx / horizontalDistance) * 0.14D,
                    currentVelocity.y,
                    currentVelocity.z + (dz / horizontalDistance) * 0.14D
                );
            }
            if (client.options != null) {
                if (client.options.forwardKey != null) {
                    client.options.forwardKey.setPressed(true);
                }
                if (client.options.jumpKey != null) {
                    client.options.jumpKey.setPressed(true);
                }
            }
            player.jump();
            synchronized (this) {
                activeBreakTarget = null;
                activeMiningAscentPhase = MiningAscentPhase.JUMP;
                lastJumpAtMs = now;
                committedJumpWaypoint = waypoint.toImmutable();
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
            && horizontalDistanceSq(playerFootPos, waypoint) > WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1) {
            BlockPos advanceBlock = resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
            if (advanceBlock == null) {
                return false;
            }
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d advanceAim = Vec3d.ofCenter(advanceBlock, player.getY() - advanceBlock.getY());
            double dx = advanceAim.x - currentPos.x;
            double dz = advanceAim.z - currentPos.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 0.0001D) {
                float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
                float nextYaw = stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP);
                player.setYaw(nextYaw);
                player.setHeadYaw(nextYaw);
                player.setBodyYaw(nextYaw);
                Vec3d velocity = player.getVelocity();
                player.setVelocity(
                    velocity.x * 0.20D + (dx / horizontalDistance) * 0.15D,
                    velocity.y,
                    velocity.z * 0.20D + (dz / horizontalDistance) * 0.15D
                );
            }
            if (client.options != null) {
                if (client.options.forwardKey != null) {
                    client.options.forwardKey.setPressed(true);
                }
                if (client.options.jumpKey != null) {
                    client.options.jumpKey.setPressed(false);
                }
            }
            synchronized (this) {
                activeBreakTarget = null;
                activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
                lastReplanReason = "mined ascent advance";
                lastStuckReason = "advancing into mined step";
            }
            noteControllerActivity(now);
            return true;
        }
        return false;
    }

    private PlacementTargetState resolveCommittedPlacementTargetState(
        World world,
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
        World world,
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

    private boolean isPlacementTargetSatisfied(World world, PlannedPrimitive plannedPrimitive, BlockPos controllerTarget) {
        if (plannedPrimitive != null && plannedPrimitive.placeTarget() != null) {
            return !primitiveStillRequiresPlace(world, plannedPrimitive);
        }
        return controllerTarget != null && hasCollision(world, controllerTarget);
    }

    private MiningProgress resolveCommittedMiningProgress(
        World world,
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
        return new MiningProgress(true, resumeIndex, plannedPrimitive.isMineAscent());
    }

    private MiningTargetState resolveCommittedMiningTargetState(
        World world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (world == null || waypoint == null) {
            return MiningTargetState.incomplete(List.of());
        }
        List<BlockPos> requiredTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : getRequiredBreakTargets(world, waypoint);
        if (requiredTargets == null || requiredTargets.isEmpty()) {
            return MiningTargetState.complete(List.of());
        }

        BlockPos liveTarget;
        synchronized (this) {
            liveTarget = activeBreakTarget;
        }
        if (liveTarget != null
            && (!requiredTargets.contains(liveTarget) || !isBreakableForNavigator(world, liveTarget))) {
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
            if (candidate == null || !isBreakableForNavigator(world, candidate)) {
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
        World world,
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
        BlockPos advanceBlock = resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
        if (advanceBlock != null
            && horizontalDistanceSq(playerFootPos, advanceBlock) > WAYPOINT_REACHED_DISTANCE_SQ
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
        World world,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        BlockPos controllerTarget
    ) {
        if (plannedPrimitive == null || !plannedPrimitive.isMineAscent()) {
            return controllerTarget != null && canOccupy(world, controllerTarget);
        }
        MiningAscentPhase phase = resolveMiningAscentPhase(world, playerFootPos, waypoint, plannedPrimitive);
        return switch (phase) {
            case CLEARANCE -> primitiveStillRequiresBreak(world, plannedPrimitive);
            case ADVANCE -> controllerTarget != null
                && playerFootPos != null
                && horizontalDistanceSq(playerFootPos, controllerTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
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
            if (playerFootPos.getY() >= waypoint.getY() - 1) {
                int nextIndex = Math.min(currentPath.size() - 1, boundedIndex + 1);
                return currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
            }
            return boundedIndex;
        }
        int nextIndex = Math.min(currentPath.size() - 1, boundedIndex + 1);
        return currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
    }

    private boolean handleJumpRecoveryMovement(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
        if (player.isOnGround() && canAttemptJump(world, playerFootPos, recoveryTarget)) {
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
            lastMovementSamplePos = playerFootPos != null ? Vec3d.ofCenter(playerFootPos) : Vec3d.ZERO;
            lastDistanceCheckpointAtMs = now;
        }
    }

    private boolean handleBreakRecoveryMovement(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        return handleTrappedSpaceRecovery(client, world, player, playerFootPos, waypoint, now);
    }

    private boolean handleRecoveryMovement(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
        if (horizontalDistanceSq(playerFootPos, recoveryTarget) <= 0.64D && Math.abs(playerFootPos.getY() - recoveryTarget.getY()) <= 1) {
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

        Vec3d targetCenter = new Vec3d(recoveryTarget.getX() + 0.5D, player.getY(), recoveryTarget.getZ() + 0.5D);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float nextYaw = stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP);
        float jumpYawError = Math.abs(MathHelper.wrapDegrees(targetYaw - nextYaw));
        player.setYaw(nextYaw);
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());

        boolean blocked = isBlockedTowardWaypoint(world, playerFootPos, recoveryTarget);
        releaseMovementKeys(client);
        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(!blocked && horizontalDistance > 0.2D);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            boolean canHop = player.isOnGround()
                && recoveryTarget.getY() > playerFootPos.getY()
                && horizontalDistance <= 1.6D
                && !blocked
                && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES
                && canAttemptJump(world, playerFootPos, recoveryTarget);
            client.options.jumpKey.setPressed(false);
            if (canHop) {
                player.jump();
                synchronized (this) {
                    lastJumpAtMs = now;
                    committedJumpWaypoint = recoveryTarget.toImmutable();
                    committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    controllerMode = ControllerMode.COMMIT_JUMP;
                    controllerTarget = recoveryTarget.toImmutable();
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

    private boolean handleFollowPathSegment(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive,
        BlockPos target,
        Vec3d currentPos,
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
        BlockPos climbAnchor = resolveClimbAnchor(world, playerFootPos, waypoint);
        boolean climbNode = plannedClimb || (plannedPrimitive == null && climbAnchor != null);
        boolean verticalDropStep = plannedDrop || (plannedPrimitive == null && playerFootPos.getX() == waypoint.getX()
            && playerFootPos.getZ() == waypoint.getZ()
            && waypoint.getY() < playerFootPos.getY()
            && canSafelyDropTo(world, playerFootPos, waypoint));
        FollowSegmentType segmentType = climbNode ? FollowSegmentType.CLIMB : (verticalDropStep ? FollowSegmentType.DROP : FollowSegmentType.GROUND);
        BlockPos segmentTarget = climbNode ? (climbAnchor != null ? climbAnchor : waypoint) : waypoint;

        Vec3d waypointCenter = resolveWaypointAimPoint(
            world,
            playerFootPos,
            waypoint,
            climbNode ? climbAnchor : null,
            plannedPrimitive,
            player.getY()
        );
        double waypointDx = waypointCenter.x - currentPos.x;
        double waypointDz = waypointCenter.z - currentPos.z;
        double waypointHorizontalDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
        double waypointHorizontalDistanceSq = waypointDx * waypointDx + waypointDz * waypointDz;
        double waypointVerticalDelta = (waypoint.getY() + 0.1D) - currentPos.y;
        updateFollowSegment(segmentType, segmentTarget, waypointHorizontalDistanceSq, now);

        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(waypointDz, waypointDx)) - 90.0D));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(waypointVerticalDelta, Math.max(0.0001D, waypointHorizontalDistance)));
        float nextYaw = stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP);
        float jumpYawError = Math.abs(MathHelper.wrapDegrees(targetYaw - nextYaw));
        float nextPitch = stepAngle(player.getPitch(), MathHelper.clamp(desiredPitch, -35.0F, 35.0F), MAX_PITCH_STEP);
        player.setYaw(nextYaw);
        player.setHeadYaw(nextYaw);
        player.setBodyYaw(nextYaw);
        player.setPitch(nextPitch);

        Vec3d desiredDirection = waypointHorizontalDistance <= 0.0001D
            ? Vec3d.ZERO
            : new Vec3d(waypointDx / waypointHorizontalDistance, 0.0D, waypointDz / waypointHorizontalDistance);
        Vec3d horizontalVelocity = new Vec3d(player.getVelocity().x, 0.0D, player.getVelocity().z);
        double forwardVelocity = desiredDirection.equals(Vec3d.ZERO) ? 0.0D : horizontalVelocity.dotProduct(desiredDirection);
        Vec3d rightDirection = new Vec3d(desiredDirection.z, 0.0D, -desiredDirection.x);
        double lateralVelocity = desiredDirection.equals(Vec3d.ZERO) ? 0.0D : horizontalVelocity.dotProduct(rightDirection);
        double projectedForwardTravel = Math.max(0.0D, forwardVelocity) * COUNTERMOVEMENT_PREDICTION_TICKS;
        boolean overshootRisk = waypointHorizontalDistance <= COUNTERMOVEMENT_DISTANCE
            && projectedForwardTravel > waypointHorizontalDistance + 0.1D
            && forwardVelocity > COUNTERMOVEMENT_SPEED;
        boolean airborneDriftRisk = !player.isOnGround()
            && waypointHorizontalDistance <= AIR_COUNTERMOVEMENT_DISTANCE
            && (projectedForwardTravel > waypointHorizontalDistance + 0.05D
            || Math.abs(lateralVelocity) > COUNTERMOVEMENT_LATERAL_SPEED);
        boolean pillarStep = shouldUsePillarStep(world, playerFootPos, waypoint, plannedPrimitive, now);
        boolean interactableStep = isInteractablePrimitive(plannedPrimitive)
            || (plannedPrimitive == null
                && (requiresInteractableTraversal(world, playerFootPos, waypoint)
                || hasPathOpenableAhead(world, playerFootPos, waypoint)
                || isPathOpenable(world.getBlockState(playerFootPos.down()))));
        BlockPos pendingBreakTarget = selectBreakTarget(world, playerFootPos, waypoint, plannedPrimitive);
        BlockPos liveBreakTarget;
        boolean nearFinalGoal;
        synchronized (this) {
            liveBreakTarget = activeBreakTarget;
            nearFinalGoal = targetPos != null
                && horizontalDistanceSq(playerFootPos, targetPos) <= 4.0D
                && Math.abs(playerFootPos.getY() - targetPos.getY()) <= 1;
        }
        boolean liveBreaking = liveBreakTarget != null && isBreakableForNavigator(world, liveBreakTarget);
        boolean breakRequiredStep = liveBreaking
            || pendingBreakTarget != null
            || (plannedPrimitive == null && requiresBreakingForWaypoint(world, waypoint));
        boolean miningAdvanceStep = primitiveRequiresBreak(plannedPrimitive)
            && !liveBreaking
            && pendingBreakTarget == null;
        boolean miningAdvanceJumpStep = miningAdvanceStep
            && plannedPrimitive != null
            && plannedPrimitive.isMineAscent()
            && waypoint.getY() > playerFootPos.getY();
        boolean placeRequiredStep = primitiveRequiresPlace(plannedPrimitive)
            || (plannedPrimitive == null && needsPlacedSupport(world, waypoint) && shouldPlaceForWaypoint(world, playerFootPos, waypoint));
        boolean ascentCommitStep = plannedPrimitive != null
            && plannedPrimitive.shouldCommitAscent(waypoint, playerFootPos)
            && !breakRequiredStep
            && !placeRequiredStep
            && !pillarStep
            && !verticalDropStep;
        boolean blockedTowardWaypoint = isBlockedTowardWaypoint(world, playerFootPos, waypoint) && !miningAdvanceStep;
        boolean simpleMovementStep = plannedPrimitive != null && plannedPrimitive.isSimpleMovementStep();
        boolean applyCountermovement = !nearFinalGoal
            && !pillarStep
            && !climbNode
            && committedJumpWaypoint == null
            && (overshootRisk || airborneDriftRisk);
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
            double correctionX = MathHelper.clamp(waypointDx * 0.18D, -0.08D, 0.08D);
            double correctionZ = MathHelper.clamp(waypointDz * 0.18D, -0.08D, 0.08D);
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.x * 0.35D + correctionX, velocity.y, velocity.z * 0.35D + correctionZ);
        } else if (verticalDropStep) {
            double correctionX = MathHelper.clamp(waypointDx * 0.22D, -0.10D, 0.10D);
            double correctionZ = MathHelper.clamp(waypointDz * 0.22D, -0.10D, 0.10D);
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.x * 0.15D + correctionX, velocity.y, velocity.z * 0.15D + correctionZ);
        } else if (miningAdvanceStep || ascentCommitStep) {
            double correctionScale = ascentCommitStep ? 0.22D : 0.16D;
            double correctionLimit = ascentCommitStep ? 0.11D : 0.07D;
            double velocityBlend = ascentCommitStep ? 0.30D : 0.45D;
            double correctionX = MathHelper.clamp(waypointDx * correctionScale, -correctionLimit, correctionLimit);
            double correctionZ = MathHelper.clamp(waypointDz * correctionScale, -correctionLimit, correctionLimit);
            Vec3d velocity = player.getVelocity();
            player.setVelocity(velocity.x * velocityBlend + correctionX, velocity.y, velocity.z * velocityBlend + correctionZ);
            noteControllerActivity(now);
        }

        if (client.options != null) {
            if (client.options.forwardKey != null) {
                client.options.forwardKey.setPressed(((miningAdvanceStep || ascentCommitStep) && waypointHorizontalDistance > 0.01D)
                    || (!verticalDropStep && !pillarStep && !blockedTowardWaypoint && !breakRequiredStep && (climbNode || !applyCountermovement)));
            }
            if (client.options.sprintKey != null) {
                client.options.sprintKey.setPressed(segmentType == FollowSegmentType.GROUND
                    && !pillarStep
                    && !blockedTowardWaypoint
                    && !breakRequiredStep
                    && !placeRequiredStep
                    && !interactableStep
                    && !nearFinalGoal
                    && !applyCountermovement
                    && player.isOnGround()
                    && waypointHorizontalDistance > 1.75D);
            }
            if (client.options.backKey != null) {
                client.options.backKey.setPressed(segmentType == FollowSegmentType.GROUND && applyCountermovement && forwardVelocity > COUNTERMOVEMENT_SPEED);
            }
            if (client.options.leftKey != null) {
                client.options.leftKey.setPressed(segmentType == FollowSegmentType.GROUND && applyCountermovement && lateralVelocity < -COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.rightKey != null) {
                client.options.rightKey.setPressed(segmentType == FollowSegmentType.GROUND && applyCountermovement && lateralVelocity > COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.jumpKey != null) {
                boolean swimUp = isSwimPrimitive(plannedPrimitive)
                    || player.isSubmergedInWater()
                    || isWaterNode(world, playerFootPos)
                    || isWaterNode(world, waypoint);
                client.options.jumpKey.setPressed(!verticalDropStep
                    && !pillarStep
                    && ((swimUp && waypoint.getY() >= playerFootPos.getY())
                    || climbUp
                    || miningAdvanceJumpStep));
            }
            if (client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(!verticalDropStep && climbDown);
            }
        }

        synchronized (this) {
            if (distanceSq + PROGRESS_EPSILON_SQ < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                lastProgressAtMs = now;
            }
        }
        noteControllerProgress(now, distanceSq);

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
            && player.isOnGround()
            && !hasCommittedJump
            && (miningAdvanceJumpStep || millisSinceJump >= JUMP_RETRY_COOLDOWN_MS)
            && !breakRequiredStep
            && !placeRequiredStep
            && jumpYawError <= JUMP_YAW_ALIGNMENT_DEGREES
            && (isJumpPrimitive(plannedPrimitive)
            || miningAdvanceJumpStep
            || (plannedPrimitive == null && (!interactableStep && waypoint.getY() > playerFootPos.getY()))
            || (plannedPrimitive == null && shouldStepJump(world, playerFootPos, waypoint) && !interactableStep));
        if (wantsJump) {
            int jumpAttemptsAtWaypoint;
            synchronized (this) {
                if (waypoint.equals(lastJumpAttemptWaypoint)) {
                    jumpAttemptsAtWaypoint = repeatedJumpAttempts;
                } else {
                    lastJumpAttemptWaypoint = waypoint.toImmutable();
                    repeatedJumpAttempts = 0;
                    jumpAttemptsAtWaypoint = 0;
                }
            }
            if (jumpAttemptsAtWaypoint >= 3) {
                releaseMovementKeys(client);
                rememberFailedJump(playerFootPos, waypoint, now);
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "jump retry limit", "repeated jump failure");
                synchronized (this) {
                    lastJumpAtMs = now;
                    repeatedJumpAttempts = 0;
                    lastJumpAttemptWaypoint = null;
                }
                return true;
            }
            boolean canJump = miningAdvanceJumpStep
                ? canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)
                : canAttemptJump(world, playerFootPos, waypoint);
            if (canJump) {
                if (!desiredDirection.equals(Vec3d.ZERO)) {
                    Vec3d velocity = player.getVelocity();
                    player.setVelocity(
                        velocity.x + desiredDirection.x * 0.12D,
                        velocity.y,
                        velocity.z + desiredDirection.z * 0.12D
                    );
                }
                if (client.options != null && client.options.forwardKey != null) {
                    client.options.forwardKey.setPressed(true);
                }
                player.jump();
                synchronized (this) {
                    lastJumpAtMs = now;
                    committedJumpWaypoint = waypoint.toImmutable();
                    committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    lastJumpAttemptWaypoint = waypoint.toImmutable();
                    repeatedJumpAttempts++;
                }
            } else {
                releaseMovementKeys(client);
                if (miningAdvanceJumpStep) {
                    rememberFailedBreak(playerFootPos, waypoint, now);
                    rememberFailedJump(playerFootPos, waypoint, now);
                } else {
                    rememberFailedJump(playerFootPos, waypoint, now);
                }
                recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "blocked jump", "ceiling blocked");
                synchronized (this) {
                    lastJumpAtMs = now;
                    lastJumpAttemptWaypoint = waypoint.toImmutable();
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
                && player.isOnGround()
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
                rememberFailedBreak(playerFootPos, waypoint, now);
            } else {
                rememberFailedMove(playerFootPos, waypoint, now);
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

    private boolean acceptCommittedJumpLandingLocked(World world, BlockPos playerFootPos, BlockPos jumpTarget) {
        if (playerFootPos == null || jumpTarget == null) {
            return false;
        }
        if (playerFootPos.equals(jumpTarget)) {
            return true;
        }
        if (playerFootPos.getY() < jumpTarget.getY() - 1) {
            return false;
        }
        if (horizontalDistanceSq(playerFootPos, jumpTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
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
            double stepDistanceSq = horizontalDistanceSq(playerFootPos, step);
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

    private boolean acceptCommittedDropLandingLocked(World world, BlockPos playerFootPos, BlockPos dropTarget) {
        if (playerFootPos == null || dropTarget == null) {
            return false;
        }
        if (playerFootPos.equals(dropTarget)) {
            return true;
        }
        if (playerFootPos.getY() > dropTarget.getY()) {
            return false;
        }
        if (horizontalDistanceSq(playerFootPos, dropTarget) <= WAYPOINT_REACHED_DISTANCE_SQ
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
            double stepDistanceSq = horizontalDistanceSq(playerFootPos, step);
            int verticalDelta = Math.abs(playerFootPos.getY() - step.getY());
            if (stepDistanceSq > WAYPOINT_NEAR_DISTANCE_SQ || verticalDelta > 1) {
                continue;
            }
            return commitPathIndexLocked(world, i, true, System.currentTimeMillis(), "advance:drop_landing=" + i);
        }
        return false;
    }

    private boolean commitPathIndexLocked(World world, int newIndex, boolean nearAdvance, long now, String advanceDecision) {
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
        pathIndex = newIndex;
        furthestVisitedPathIndex = Math.max(furthestVisitedPathIndex, pathIndex - (nearAdvance ? 1 : 0));
        activeWaypoint = newWaypoint;
        activePlannedPrimitive = getPlannedPrimitiveAtIndexLocked(pathIndex);
        plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
        lastWaypointAdvanceAtMs = now;
        lastProgressAtMs = now;
        routeCommitUntilMs = Math.max(routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
        lastAdvanceDecision = advanceDecision;
        return true;
    }

    private boolean handlePillaring(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
                ? controllerTarget.toImmutable()
                : waypoint.toImmutable();
            if (!committedEscape.isEmpty()) {
                committedEscapeTarget = pillarTarget.toImmutable();
                committedEscapeUntilMs = Math.max(committedEscapeUntilMs, now + TRAPPED_RECOVERY_COMMIT_MS);
            }
        }
        BlockPos pillarBase = pillarTarget.down();
        if (pillarBase.getX() != playerFootPos.getX()
            || pillarBase.getZ() != playerFootPos.getZ()
            || pillarBase.getY() < playerFootPos.getY() - 1
            || pillarBase.getY() > playerFootPos.getY()) {
            return false;
        }
        if (!canContinuePillarTo(world, pillarBase, pillarTarget)) {
            return false;
        }
        syncPathToPillarTarget(world, pillarTarget, now);
        releaseMovementKeys(client);
        Vec3d columnCenter = Vec3d.ofCenter(pillarBase);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = columnCenter.x - currentPos.x;
        double dz = columnCenter.z - currentPos.z;
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYaw(stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP));
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());
        player.setPitch(stepAngle(player.getPitch(), 89.5F, MAX_PITCH_STEP));
        Vec3d velocity = player.getVelocity();
        player.setVelocity(
            velocity.x * 0.25D + MathHelper.clamp(dx * 0.18D, -0.08D, 0.08D),
            velocity.y,
            velocity.z * 0.25D + MathHelper.clamp(dz * 0.18D, -0.08D, 0.08D)
        );

        PillarPhase pillarPhase = resolvePillarPhase(world, player, pillarBase, pillarTarget, dx, dz);
        synchronized (this) {
            activePillarPhase = pillarPhase;
        }

        if (client.options != null) {
            if (client.options.sprintKey != null) {
                client.options.sprintKey.setPressed(false);
            }
            if (client.options.forwardKey != null) {
                client.options.forwardKey.setPressed(false);
            }
            if (client.options.backKey != null) {
                client.options.backKey.setPressed(false);
            }
            if (client.options.leftKey != null) {
                client.options.leftKey.setPressed(false);
            }
            if (client.options.rightKey != null) {
                client.options.rightKey.setPressed(false);
            }
            if (client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
            if (client.options.jumpKey != null) {
                client.options.jumpKey.setPressed(pillarPhase == PillarPhase.ASCEND);
            }
        }

        if (pillarPhase == PillarPhase.SUPPORT_READY) {
            synchronized (this) {
                controllerUntilMs = 0L;
                lastReplanReason = "pillar support ready";
                lastStuckReason = "advance on support";
                lastPlaceTarget = pillarBase.toImmutable();
                lastPlaceResult = "placed";
            }
            return false;
        }
        synchronized (this) {
            lastPlaceTarget = pillarBase.toImmutable();
            lastPlaceResult = switch (pillarPhase) {
                case PLACE -> "ready";
                case ASCEND -> "waiting apex";
                case CENTER -> "centering";
                case SUPPORT_READY -> "placed";
            };
        }
        if (pillarPhase == PillarPhase.PLACE) {
            if (client.options != null) {
                if (client.options.jumpKey != null) {
                    client.options.jumpKey.setPressed(false);
                }
                if (client.options.sneakKey != null) {
                    client.options.sneakKey.setPressed(true);
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
        }
        if (pillarPhase == PillarPhase.ASCEND && player.isOnGround()) {
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
        World world,
        ClientPlayerEntity player,
        BlockPos pillarBase,
        BlockPos pillarTarget,
        double dx,
        double dz
    ) {
        if (world == null || player == null || pillarBase == null || pillarTarget == null) {
            return PillarPhase.CENTER;
        }
        if (hasCollision(world, pillarBase)) {
            return PillarPhase.SUPPORT_READY;
        }
        boolean centered = Math.abs(dx) <= 0.22D && Math.abs(dz) <= 0.22D;
        boolean airbornePlacementWindow = !player.isOnGround() && player.getVelocity().y <= 0.45D;
        if (centered && airbornePlacementWindow) {
            return PillarPhase.PLACE;
        }
        if (player.getY() < pillarTarget.getY()) {
            return PillarPhase.ASCEND;
        }
        return PillarPhase.CENTER;
    }

    private boolean tryPlacePillarBlock(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos placePos,
        long now
    ) {
        if (client == null || world == null || player == null || placePos == null || client.interactionManager == null) {
            synchronized (this) {
                lastPlaceTarget = placePos != null ? placePos.toImmutable() : null;
                lastPlaceResult = "client unavailable";
            }
            return false;
        }
        if (now - lastInteractAtMs < 250L) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "cooldown";
            }
            return false;
        }
        BlockPos supportPos = placePos.down();
        if (!hasCollision(world, supportPos)) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "no placeable block";
            }
            return false;
        }

        int previousSlot = PlayerInventoryBridge.getSelectedSlot(player.getInventory());
        PlayerInventoryBridge.setSelectedSlot(player.getInventory(), hotbarSlot);
        syncSelectedHotbarSlot(client);

        if (client.options != null) {
            if (client.options.jumpKey != null) {
                client.options.jumpKey.setPressed(false);
            }
        }
        applySneakState(client, true);

        BlockHitResult hit = raycastBlockFromOrientation(client, player.getYaw(), player.getPitch(), 4.5D);
        if (hit == null || !supportPos.equals(hit.getBlockPos())) {
            Vec3d hitPos = new Vec3d(
                supportPos.getX() + 0.5D,
                supportPos.getY() + 0.999D,
                supportPos.getZ() + 0.5D
            );
            hit = new BlockHitResult(hitPos, Direction.UP, supportPos, false);
        }
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        boolean accepted = result != null && result.isAccepted();
        if (!accepted) {
            ActionResult fallback = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            accepted = fallback != null && fallback.isAccepted();
        }
        if (accepted) {
            player.swingHand(Hand.MAIN_HAND);
        }

        PlayerInventoryBridge.setSelectedSlot(player.getInventory(), previousSlot);
        syncSelectedHotbarSlot(client);
        applySneakState(client, true);

        boolean placedNow = hasCollision(world, placePos);
        synchronized (this) {
            lastPlaceTarget = placePos.toImmutable();
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
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
        if (player.isOnGround()) {
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
                rememberFailedJump(playerFootPos, jumpTarget, now);
                rewindCurrentPathIndex(playerFootPos, jumpTarget);
                recoverFromStuck(client, world, playerFootPos, jumpTarget, targetPos, Vec3d.ofCenter(playerFootPos), now, "jump redirect", "missed jump");
                return true;
            }
        }
        Vec3d targetCenter = new Vec3d(jumpTarget.getX() + 0.5D, player.getY(), jumpTarget.getZ() + 0.5D);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYaw(stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP));
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());

        if (!player.isOnGround() && horizontalDistance > 0.0001D) {
            Vec3d velocity = player.getVelocity();
            player.setVelocity(
                velocity.x * 0.40D + (dx / horizontalDistance) * 0.11D,
                velocity.y,
                velocity.z * 0.40D + (dz / horizontalDistance) * 0.11D
            );
        }

        releaseMovementKeys(client);
        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(horizontalDistance > 0.05D);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            client.options.jumpKey.setPressed(false);
        }
        synchronized (this) {
            lastReplanReason = "committed jump";
            lastStuckReason = player.isOnGround() ? "landing jump" : "airborne";
            lastProgressAtMs = now;
        }
        noteControllerActivity(now);
        return true;
    }

    private boolean handleCommittedDropMovement(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos target,
        Vec3d currentPos,
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
        if (player.isOnGround()) {
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

        Vec3d targetCenter = new Vec3d(dropTarget.getX() + 0.5D, player.getY(), dropTarget.getZ() + 0.5D);
        double dx = targetCenter.x - currentPos.x;
        double dz = targetCenter.z - currentPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        float nextYaw = stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP);
        player.setYaw(nextYaw);
        player.setHeadYaw(nextYaw);
        player.setBodyYaw(nextYaw);

        boolean blocked = player.isOnGround()
            && horizontalDistance > 0.2D
            && isBlockedTowardWaypoint(world, playerFootPos, dropTarget);
        releaseMovementKeys(client);
        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(horizontalDistance > 0.15D && !blocked);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            client.options.jumpKey.setPressed(false);
        }
        if (client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(false);
        }

        synchronized (this) {
            lastReplanReason = "committed drop";
            lastStuckReason = player.isOnGround() ? "stepping off ledge" : "airborne descent";
            lastProgressAtMs = now;
        }
        noteControllerActivity(now);

        if (player.isOnGround() && blocked) {
            rememberFailedDrop(playerFootPos, dropTarget, now);
            recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop blocked", "drop blocked");
            return true;
        }
        if (player.isOnGround() && now > dropUntilMs) {
            rememberFailedDrop(playerFootPos, dropTarget, now);
            recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop redirect", "missed drop");
            return true;
        }
        return true;
    }

    private boolean handleTrappedSpaceRecovery(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
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
                        rememberFailedMove(playerFootPos, playerFootPos.offset(committedEscape.direction()), now);
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

    private BlockPos selectVerticalEscapeTarget(World world, BlockPos playerFootPos, BlockPos waypoint) {
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
        BlockPos immediateUp = playerFootPos.up();
        return canPillarTo(world, playerFootPos, immediateUp) ? immediateUp.toImmutable() : null;
    }

    private boolean shouldPreferFinalApproachController(World world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (this) {
            activeTarget = targetPos;
        }
        if (activeTarget == null || !isStandable(world, activeTarget)) {
            return false;
        }
        return horizontalDistanceSq(playerFootPos, activeTarget) <= 4.0D
            && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 1;
    }

    private void continueCommittedEscapeMovement(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos routeTarget,
        long now
    ) {
        if (client == null || world == null || player == null || playerFootPos == null || routeTarget == null || client.options == null) {
            return;
        }
        Vec3d frontCenter = new Vec3d(routeTarget.getX() + 0.5D, player.getY(), routeTarget.getZ() + 0.5D);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = frontCenter.x - currentPos.x;
        double dz = frontCenter.z - currentPos.z;
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYaw(stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP));
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());

        releaseMovementKeys(client);
        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(true);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            client.options.jumpKey.setPressed(false);
        }

        synchronized (this) {
            lastProgressAtMs = now;
            lastReplanReason = "escape primitive move";
            lastStuckReason = "following excavation route";
        }
        noteControllerActivity(now);
    }

    private void ensureExcavationPlan(World world, BlockPos playerFootPos, BlockPos waypoint, long now) {
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

    private BlockPos selectCommittedEscapeRouteTarget(World world, BlockPos playerFootPos, long now) {
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
                if (horizontalDistanceSq(playerFootPos, step) <= 0.25D && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                if (isFailedNode(step, now) || requiresBreakingForWaypoint(world, step) || needsPlacedSupport(world, step)) {
                    return null;
                }
                if (isWaypointActionable(world, step)) {
                    committedEscapeTarget = step.toImmutable();
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

    private boolean canExitTrappedRecovery(World world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (!canOccupy(world, playerFootPos.up())) {
            return false;
        }
        return countPhysicalWalkNeighbors(world, playerFootPos) >= 2;
    }

    private BlockPos selectBreakTarget(World world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive) {
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
            if (activeBreakTarget != null && breakTargets.contains(activeBreakTarget) && isBreakableForNavigator(world, activeBreakTarget)) {
                return activeBreakTarget;
            }
        }
        BlockPos pendingTarget = firstPendingBreakTarget(world, breakTargets);
        if (pendingTarget != null && isPlannedBreakTargetReachable(playerFootPos, pendingTarget)) {
            return pendingTarget;
        }
        for (BlockPos candidate : breakTargets) {
            if (!isBreakableForNavigator(world, candidate)) {
                continue;
            }
            if (!isPlannedBreakTargetReachable(playerFootPos, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private BlockPos firstPendingBreakTarget(World world, List<BlockPos> breakTargets) {
        if (world == null || breakTargets == null || breakTargets.isEmpty()) {
            return null;
        }
        for (BlockPos candidate : breakTargets) {
            if (candidate != null && isBreakableForNavigator(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPlannedBreakTargetReachable(BlockPos playerFootPos, BlockPos target) {
        if (playerFootPos == null || target == null) {
            return false;
        }
        return horizontalDistanceSq(playerFootPos, target) <= 9.0D
            && Math.abs(playerFootPos.getY() - target.getY()) <= 3;
    }

    private boolean isTrappedInCrampedSpace(World world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        int physicalWalkNeighbors = countPhysicalWalkNeighbors(world, playerFootPos);
        boolean boxedIn = physicalWalkNeighbors <= 1;
        if (boxedIn) {
            return true;
        }
        boolean lowPlannerMobility = countDirectWalkNeighbors(world, playerFootPos, playerFootPos, waypoint, System.currentTimeMillis()) <= 1;
        return lowPlannerMobility && physicalWalkNeighbors <= 2;
    }

    private int countPhysicalWalkNeighbors(World world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null) {
            return 0;
        }
        int count = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos candidate = playerFootPos.offset(direction);
            if (!isPhysicalWalkNeighbor(world, candidate)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean isPhysicalWalkNeighbor(World world, BlockPos footPos) {
        if (world == null || footPos == null) {
            return false;
        }
        if (!canOccupy(world, footPos) || !canOccupy(world, footPos.up())) {
            return false;
        }
        if (!hasCollision(world, footPos.down()) && !isWaterNode(world, footPos)) {
            return false;
        }
        return !isHardDanger(world, footPos);
    }

    private BlockPos selectTrappedSpaceBreakTarget(World world, BlockPos playerFootPos, BlockPos waypoint, long now) {
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
                if (canOccupy(world, planned)) {
                    committedEscapePrimitiveIndex++;
                    continue;
                }
                if (isBreakableForNavigator(world, planned)) {
                    committedEscapeTarget = planned.toImmutable();
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
        if (dx != direction.getOffsetX() || dz != direction.getOffsetZ()) {
            return false;
        }
        return dy >= 0 && dy <= 2;
    }

    private Direction chooseEscapeDirection(World world, BlockPos current, BlockPos goal, long now) {
        if (world == null || current == null || goal == null) {
            return null;
        }
        Direction bestDirection = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            Double score = scoreEscapeDirection(world, current, goal, direction, now);
            if (score == null || score >= bestScore) {
                continue;
            }
            bestScore = score;
            bestDirection = direction;
        }
        return bestDirection;
    }

    private ExcavationPlan buildExcavationPlan(World world, BlockPos current, BlockPos goal, long now) {
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

    private StairEscapePlan buildStairEscapePlan(World world, BlockPos current, BlockPos goal, Direction direction, long now) {
        if (world == null || current == null || goal == null || direction == null || direction.getAxis().isVertical()) {
            return null;
        }
        List<BlockPos> route = new ArrayList<>();
        List<EscapePrimitive> primitives = new ArrayList<>();
        int stepX = direction.getOffsetX();
        int stepZ = direction.getOffsetZ();
        BlockPos cursor = current;

        addThreeHighExcavationBreaks(primitives, world, cursor);

        for (int distance = 1; distance <= 8; distance++) {
            BlockPos flat = cursor.add(stepX, 0, stepZ);
            BlockPos up = cursor.add(stepX, 1, stepZ);
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

            route.add(chosen.toImmutable());
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

    private boolean isEscapeLipReached(World world, BlockPos start, BlockPos cursor, BlockPos goal, long now) {
        if (world == null || start == null || cursor == null || goal == null) {
            return false;
        }
        if (!hasThreeHighExcavationClearance(world, cursor)) {
            return false;
        }
        if (countDirectWalkNeighbors(world, cursor, cursor, goal, now) < 2) {
            return false;
        }
        int targetLipY = Math.max(start.getY() + 1, goal.getY() - 1);
        return cursor.getY() >= targetLipY;
    }

    private boolean isValidEscapeStepCandidate(World world, BlockPos from, BlockPos candidate, long now) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        return isChunkLoaded(world, candidate)
            && !isFailedNode(candidate, now)
            && !isFailedEdge(from, candidate, now)
            && !isHardDanger(world, candidate)
            && !needsPlacedSupport(world, candidate);
    }

    private boolean hasExcavatableThreeHighClearance(World world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return isExcavationClearable(world, foot)
            && isExcavationClearable(world, foot.up())
            && isExcavationClearable(world, foot.up(2));
    }

    private void addThreeHighExcavationBreaks(List<EscapePrimitive> plan, World world, BlockPos foot) {
        if (plan == null || world == null || foot == null) {
            return;
        }
        addOrderedExcavationBreaks(plan, world, List.of(foot, foot.up(), foot.up(2)));
    }

    private void addAscendingExcavationBreaks(List<EscapePrimitive> plan, World world, BlockPos from, BlockPos to) {
        if (plan == null || world == null || from == null || to == null) {
            return;
        }
        addOrderedExcavationBreaks(plan, world, List.of(
            from.up(),
            from.up(2),
            to,
            to.up(),
            to.up(2)
        ));
    }

    private boolean canExcavateEscapeJumpCorridor(World world, BlockPos from, BlockPos to) {
        return canExcavateJumpCorridor(world, from, to);
    }

    private void addOrderedExcavationBreaks(List<EscapePrimitive> plan, World world, List<BlockPos> candidates) {
        if (plan == null || world == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (BlockPos candidate : candidates) {
            if (candidate == null || !isBreakableForNavigator(world, candidate)) {
                continue;
            }
            addPlannedBreak(plan, world, candidate.toImmutable());
        }
    }

    private void addPlannedBreak(List<EscapePrimitive> plan, World world, BlockPos pos) {
        if (plan == null || world == null || pos == null) {
            return;
        }
        if (!isBreakableForNavigator(world, pos)) {
            return;
        }
        addEscapePrimitive(plan, EscapePrimitiveType.MINE, pos);
    }

    private void addEscapePrimitive(List<EscapePrimitive> plan, EscapePrimitiveType type, BlockPos pos) {
        if (plan == null || type == null || pos == null) {
            return;
        }
        EscapePrimitive primitive = new EscapePrimitive(type, pos.toImmutable());
        if (!plan.contains(primitive)) {
            plan.add(primitive);
        }
    }

    private boolean hasThreeHighExcavationClearance(World world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return canOccupy(world, foot)
            && canOccupy(world, foot.up())
            && canOccupy(world, foot.up(2));
    }

    private Double scoreEscapeDirection(World world, BlockPos current, BlockPos goal, Direction direction, long now) {
        if (world == null || current == null || goal == null || direction == null || direction.getAxis().isVertical()) {
            return null;
        }
        int stepX = direction.getOffsetX();
        int stepZ = direction.getOffsetZ();

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
                for (BlockPos candidate : List.of(cursor.up(), cursor.up(2), step, step.up(), step.up(2))) {
                    if (!canOccupy(world, candidate)) {
                        if (!isBreakableForNavigator(world, candidate)) {
                            return null;
                        }
                        segmentScore += breakPenalty(world, candidate);
                        requiredBreaks++;
                    }
                }
            } else {
                for (BlockPos candidate : List.of(step, step.up(), step.up(2))) {
                    if (!canOccupy(world, candidate)) {
                        if (!isBreakableForNavigator(world, candidate)) {
                            return null;
                        }
                        segmentScore += breakPenalty(world, candidate);
                        requiredBreaks++;
                    }
                }
            }

            if (!hasCollision(world, step.down()) && !isWaterNode(world, step)) {
                if (!allowBlockPlacing || !canPlaceSupportAt(world, step.down())) {
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
                && countDirectWalkNeighbors(world, step, step, goal, now) >= 2) {
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

    private boolean continueBreakingBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos target, long now) {
        if (client == null || client.interactionManager == null || client.world == null || player == null || target == null) {
            return false;
        }
        BlockState targetState = client.world.getBlockState(target);
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
            : getRequiredBreakTargets(client.world, waypoint);
        return continueBreakingRequiredTarget(client, player, target, requiredTargets, now);
    }

    private boolean continueBreakingRequiredTarget(
        MinecraftClient client,
        ClientPlayerEntity player,
        BlockPos target,
        List<BlockPos> requiredTargets,
        long now
    ) {
        if (client == null || client.interactionManager == null || client.world == null || player == null || target == null) {
            return false;
        }
        if (requiredTargets == null || !requiredTargets.contains(target)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        BlockPos pendingTarget = firstPendingBreakTarget(client.world, requiredTargets);
        if (pendingTarget == null || !target.equals(pendingTarget)) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        BlockState targetState = client.world.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(target)) > 25.0D) {
            return false;
        }
        BreakTargeting targeting = resolveBreakTargeting(client.world, player, target);
        if (targeting == null) {
            synchronized (this) {
                activeBreakTarget = null;
            }
            return false;
        }
        equipBestMiningTool(player, targetState);
        releaseMovementKeys(client);
        applyWaterInteractionStance(client, client.world, player, target);
        lookAtPosition(player, targeting.hitPos());
        Direction face = targeting.face();
        boolean startingNewTarget;
        synchronized (this) {
            startingNewTarget = activeBreakTarget == null || !activeBreakTarget.equals(target);
            activeBreakTarget = target.toImmutable();
            if (startingNewTarget) {
                lastInteractAtMs = now;
            }
        }
        if (startingNewTarget) {
            client.interactionManager.attackBlock(target, face);
        }
        client.interactionManager.updateBlockBreakingProgress(target, face);
        player.swingHand(Hand.MAIN_HAND);
        noteControllerActivity(now);
        return true;
    }

    private void syncPathToPillarTarget(ClientWorld world, BlockPos pillarTarget, long now) {
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
                activeWaypoint = pillarTarget.toImmutable();
                return;
            }
        }

        List<BlockPos> syncedPath = List.of(pillarTarget.toImmutable());
        PathComputation continuation = null;
        if (navTarget != null) {
            continuation = findPath(world, pillarTarget, navTarget);
            if (continuation != null && !continuation.path().isEmpty()) {
                List<BlockPos> continuationPath = continuation.path();
                if (pillarTarget.equals(continuationPath.get(0))) {
                    syncedPath = List.copyOf(continuationPath);
                } else {
                    List<BlockPos> combined = new ArrayList<>(continuationPath.size() + 1);
                    combined.add(pillarTarget.toImmutable());
                    combined.addAll(continuationPath);
                    syncedPath = List.copyOf(combined);
                }
            }
        }

        synchronized (this) {
            currentPath = syncedPath;
            pathIndex = 0;
            furthestVisitedPathIndex = 0;
            activeWaypoint = pillarTarget.toImmutable();
            committedPathGoalPos = pillarTarget.toImmutable();
            plannedBreakTargets = buildPathBreakPlan(world, currentPath, pathIndex);
            rebuildCurrentPlanLocked(world);
            if (!isPillarPrimitive(activePlannedPrimitive)) {
                activePlannedPrimitive = createPrimitiveSnapshot(world, activeWaypoint.down(), activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), activeWaypoint.down());
            }
            lastPlanAtMs = now;
            routeCommitUntilMs = Math.max(routeCommitUntilMs, now + 1400L);
            lastReplanReason = "pillar sync";
            if (continuation != null && !continuation.path().isEmpty()) {
                candidatePaths = continuation.candidatePaths();
                candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                goalMode = shouldUseResolvedGoalForCompletion(navTarget, continuation.resolvedGoalPos(), continuation.goalMode())
                    ? continuation.goalMode()
                    : GoalMode.EXACT;
                resolvedGoalPos = goalMode == GoalMode.NEAREST_STANDABLE ? continuation.resolvedGoalPos() : navTarget.toImmutable();
                committedPathGoalPos = continuation.resolvedGoalPos() != null ? continuation.resolvedGoalPos().toImmutable() : resolvedGoalPos;
            }
        }
    }

    private boolean continueBreakingEscapeBlock(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos target,
        long now
    ) {
        if (client == null || world == null || client.interactionManager == null || player == null || target == null) {
            return false;
        }
        BlockState targetState = world.getBlockState(target);
        if (targetState == null || targetState.isAir() || !isBreakableForNavigator(world, target)) {
            return false;
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(target)) > 25.0D) {
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
            activeBreakTarget = target.toImmutable();
            if (startingNewTarget) {
                committedEscapeTarget = target.toImmutable();
                committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                lastInteractAtMs = now;
                lastReplanReason = "escape primitive mine";
                lastStuckReason = "excavating escape";
            }
        }
        if (startingNewTarget) {
            client.interactionManager.attackBlock(target, face);
        }
        client.interactionManager.updateBlockBreakingProgress(target, face);
        player.swingHand(Hand.MAIN_HAND);
        noteControllerActivity(now);
        return true;
    }

    private boolean shouldSuppressMiningNearGoal(World world, ClientPlayerEntity player, BlockPos playerFootPos, BlockPos waypoint) {
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
        if (horizontalDistanceSq(playerFootPos, activeTarget) > 2.25D || Math.abs(playerFootPos.getY() - activeTarget.getY()) > 1) {
            return false;
        }
        if (!activeTarget.equals(waypoint) && !activeTarget.up().equals(waypoint)) {
            return false;
        }
        return isWithinGoalArrivalRange(player, activeTarget)
            || isStandable(world, activeTarget)
            || isNavigableNode(world, activeTarget);
    }

    private boolean shouldForceFinalApproach(World world, BlockPos playerFootPos, BlockPos target) {
        if (world == null || playerFootPos == null || target == null) {
            return false;
        }
        return isStandable(world, target)
            && horizontalDistanceSq(playerFootPos, target) <= 4.0D
            && Math.abs(playerFootPos.getY() - target.getY()) <= 1;
    }

    private boolean shouldBreakForWaypoint(BlockPos playerFootPos, BlockPos waypoint, BlockPos breakTarget) {
        if (playerFootPos == null || waypoint == null || breakTarget == null) {
            return false;
        }
        if (horizontalDistanceSq(playerFootPos, waypoint) > 4.0D || Math.abs(waypoint.getY() - playerFootPos.getY()) > 1) {
            return isPlannedBreakTargetReachable(playerFootPos, breakTarget);
        }
        return breakTarget.equals(waypoint)
            || breakTarget.equals(waypoint.up())
            || isPlannedBreakTargetReachable(playerFootPos, breakTarget);
    }

    private boolean requiresBreakingForWaypoint(World world, BlockPos waypoint) {
        if (world == null || waypoint == null) {
            return false;
        }
        List<BlockPos> breakTargets = getRequiredBreakTargets(world, waypoint);
        return breakTargets != null && !breakTargets.isEmpty();
    }

    private boolean shouldPlaceForWaypoint(World world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (isWaterNode(world, waypoint) || isWaterNode(world, playerFootPos)) {
            double horizontalDistanceSq = horizontalDistanceSq(playerFootPos, waypoint);
            int deltaY = waypoint.getY() - playerFootPos.getY();
            if (!canOccupy(world, waypoint) || !canOccupy(world, waypoint.up())) {
                return false;
            }
            return deltaY >= -1
                && deltaY <= 1
                && horizontalDistanceSq >= 0.01D
                && horizontalDistanceSq <= 2.25D;
        }
        if (canPillarTo(world, playerFootPos, waypoint)) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (this) {
            activeTarget = targetPos;
        }
        if (activeTarget != null) {
            if (waypoint.equals(activeTarget) || waypoint.down().equals(activeTarget)) {
                return false;
            }
            if (isStandable(world, activeTarget)
                && horizontalDistanceSq(playerFootPos, activeTarget) <= 9.0D
                && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 2) {
                return false;
            }
            if (isStandable(world, activeTarget)
                && horizontalDistanceSq(playerFootPos, activeTarget) <= 4.0D
                && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 1) {
                return false;
            }
        }
        if (requiresBreakingForWaypoint(world, waypoint)) {
            return false;
        }
        if (isTreeCanopyNode(world, waypoint)) {
            return false;
        }
        if (!canOccupy(world, waypoint) || !canOccupy(world, waypoint.up())) {
            return false;
        }
        if (waypoint.getY() < playerFootPos.getY()) {
            return false;
        }
        double horizontalDistanceSq = horizontalDistanceSq(playerFootPos, waypoint);
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
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        BlockPos placeTarget
    ) {
        if (world == null || player == null || playerFootPos == null || waypoint == null || placeTarget == null) {
            return false;
        }
        boolean inWater = player.isTouchingWater()
            || player.isSubmergedInWater()
            || isWaterNode(world, playerFootPos)
            || isWaterNode(world, waypoint);
        if (!inWater) {
            return false;
        }
        if (hasCollision(world, placeTarget) || !canPlaceSupportAt(world, placeTarget)) {
            return false;
        }
        return horizontalDistanceSq(playerFootPos, placeTarget.up()) <= 4.0D
            && Math.abs(playerFootPos.getY() - placeTarget.getY()) <= 2;
    }

    private void equipBestMiningTool(ClientPlayerEntity player, BlockState targetState) {
        if (player == null || player.getInventory() == null || targetState == null) {
            return;
        }
        int bestSlot = findBestMiningHotbarSlot(player, targetState);
        if (bestSlot < 0) {
            return;
        }
        if (PlayerInventoryBridge.getSelectedSlot(player.getInventory()) != bestSlot) {
            PlayerInventoryBridge.setSelectedSlot(player.getInventory(), bestSlot);
        }
    }

    private int findBestMiningHotbarSlot(ClientPlayerEntity player, BlockState targetState) {
        if (player == null || player.getInventory() == null || targetState == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.entity.player.PlayerInventory.getHotbarSize();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
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
        double speed = stack.getMiningSpeedMultiplier(targetState);
        double score = speed;
        if (stack.isSuitableFor(targetState)) {
            score += 100.0D;
        }
        if (stack.getItem() instanceof BlockItem) {
            score -= 8.0D;
        }
        return score;
    }

    private void lookAtPosition(ClientPlayerEntity player, Vec3d targetPos) {
        if (player == null || targetPos == null) {
            return;
        }
        Vec3d delta = targetPos.subtract(player.getEyePos());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(0.0001D, horizontalDistance)));
        float clampedPitch = MathHelper.clamp(targetPitch, -60.0F, 60.0F);
        player.setYaw(targetYaw);
        player.setHeadYaw(targetYaw);
        player.setBodyYaw(targetYaw);
        player.setPitch(clampedPitch);
    }

    private BreakTargeting resolveBreakTargeting(ClientWorld world, ClientPlayerEntity player, BlockPos target) {
        if (world == null || player == null || target == null) {
            return null;
        }
        Vec3d eyePos = player.getEyePos();
        for (Direction face : preferredBreakFaces(player, target)) {
            Vec3d hitPos = getBreakFaceAimPoint(target, face);
            if (hasLineOfSightTo(world, player, target, hitPos)) {
                return new BreakTargeting(target.toImmutable(), face, hitPos);
            }
        }
        Vec3d center = Vec3d.ofCenter(target);
        if (hasLineOfSightTo(world, player, target, center)) {
            Direction fallbackFace = Direction.getFacing(center.x - eyePos.x, center.y - eyePos.y, center.z - eyePos.z).getOpposite();
            return new BreakTargeting(target.toImmutable(), fallbackFace, center);
        }
        return null;
    }

    private List<Direction> preferredBreakFaces(ClientPlayerEntity player, BlockPos target) {
        if (player == null || target == null) {
            return List.of(Direction.UP);
        }
        Vec3d eyePos = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);
        Vec3d delta = center.subtract(eyePos);
        Direction primary = Direction.getFacing(delta.x, delta.y, delta.z).getOpposite();
        List<Direction> faces = new ArrayList<>(6);
        faces.add(primary);
        for (Direction face : Direction.values()) {
            if (!faces.contains(face)) {
                faces.add(face);
            }
        }
        return faces;
    }

    private Vec3d getBreakFaceAimPoint(BlockPos target, Direction face) {
        Vec3d center = Vec3d.ofCenter(target);
        if (face == null) {
            return center;
        }
        return center.add(
            face.getOffsetX() * 0.48D,
            face.getOffsetY() * 0.48D,
            face.getOffsetZ() * 0.48D
        );
    }

    private boolean hasLineOfSightTo(ClientWorld world, ClientPlayerEntity player, BlockPos target, Vec3d hitPos) {
        if (world == null || player == null || target == null || hitPos == null) {
            return false;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(
            player.getEyePos(),
            hitPos,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            player
        ));
        return hit != null && hit.getType() == HitResult.Type.BLOCK && target.equals(hit.getBlockPos());
    }

    private boolean tryPlaceSupportBlock(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos placePos,
        long now
    ) {
        return tryPlaceSupportBlock(client, world, player, placePos, now, false);
    }

    private boolean tryPlaceSupportBlock(
        MinecraftClient client,
        ClientWorld world,
        ClientPlayerEntity player,
        BlockPos placePos,
        long now,
        boolean preserveMovementState
    ) {
        if (client == null || world == null || player == null || placePos == null || client.interactionManager == null) {
            synchronized (this) {
                lastPlaceTarget = placePos != null ? placePos.toImmutable() : null;
                lastPlaceResult = "client unavailable";
            }
            return false;
        }
        if (now - lastInteractAtMs < 250L) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "cooldown";
            }
            return false;
        }
        PlacementTarget placementTarget = findPlacementTarget(world, placePos);
        if (placementTarget == null) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (this) {
                lastPlaceTarget = placePos.toImmutable();
                lastPlaceResult = "no placeable block";
            }
            return false;
        }
        int previousSlot = PlayerInventoryBridge.getSelectedSlot(player.getInventory());
        PlayerInventoryBridge.setSelectedSlot(player.getInventory(), hotbarSlot);
        syncSelectedHotbarSlot(client);
        if (!preserveMovementState) {
            releaseMovementKeys(client);
        }
        applyWaterInteractionStance(client, world, player, placePos);
        ActionResult result = client.interactionManager.interactBlock(
            player,
            Hand.MAIN_HAND,
            new BlockHitResult(placementTarget.hitPos(), placementTarget.face(), placementTarget.supportPos(), false)
        );
        boolean accepted = result != null && result.isAccepted();
        if (!accepted) {
            ActionResult fallback = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            accepted = fallback != null && fallback.isAccepted();
        }
        if (accepted) {
            player.swingHand(Hand.MAIN_HAND);
        }
        PlayerInventoryBridge.setSelectedSlot(player.getInventory(), previousSlot);
        syncSelectedHotbarSlot(client);
        boolean placedNow = hasCollision(world, placePos);
        synchronized (this) {
            lastPlaceTarget = placePos.toImmutable();
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

    private void applyWaterInteractionStance(MinecraftClient client, ClientWorld world, ClientPlayerEntity player, BlockPos anchor) {
        if (client == null || world == null || player == null || anchor == null || client.options == null) {
            return;
        }
        boolean inWater = player.isTouchingWater()
            || player.isSubmergedInWater()
            || isWaterNode(world, resolvePlayerFootPos(player))
            || isWaterNode(world, anchor);
        if (!inWater) {
            return;
        }

        Vec3d anchorCenter = Vec3d.ofCenter(anchor);
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dx = anchorCenter.x - currentPos.x;
        double dz = anchorCenter.z - currentPos.z;
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
        player.setYaw(stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP));
        player.setHeadYaw(player.getYaw());
        player.setBodyYaw(player.getYaw());

        Vec3d velocity = player.getVelocity();
        player.setVelocity(
            velocity.x * 0.55D + MathHelper.clamp(dx * 0.14D, -0.06D, 0.06D),
            velocity.y,
            velocity.z * 0.55D + MathHelper.clamp(dz * 0.14D, -0.06D, 0.06D)
        );

        double bobTargetY = anchor.getY() + 0.55D;
        boolean bobUp = player.getY() < bobTargetY || player.getVelocity().y < -0.02D;

        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(Math.abs(dx) > 0.18D || Math.abs(dz) > 0.18D);
        }
        if (client.options.backKey != null) {
            client.options.backKey.setPressed(false);
        }
        if (client.options.leftKey != null) {
            client.options.leftKey.setPressed(false);
        }
        if (client.options.rightKey != null) {
            client.options.rightKey.setPressed(false);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            client.options.jumpKey.setPressed(bobUp);
        }
    }

    private int findPlaceableHotbarSlot(ClientPlayerEntity player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.entity.player.PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findPlaceableMainInventorySlot(ClientPlayerEntity player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.entity.player.PlayerInventory.getHotbarSize();
        for (int slot = hotbarSize; slot < net.minecraft.entity.player.PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot(net.minecraft.entity.player.PlayerInventory inventory) {
        if (inventory == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.entity.player.PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int ensurePlaceableHotbarSlot(MinecraftClient client, ClientPlayerEntity player) {
        int hotbarSlot = findPlaceableHotbarSlot(player);
        if (hotbarSlot >= 0) {
            return hotbarSlot;
        }
        if (client == null || player == null || player.getInventory() == null || client.interactionManager == null) {
            return -1;
        }
        int inventorySlot = findPlaceableMainInventorySlot(player);
        if (inventorySlot < 0) {
            return -1;
        }
        return moveInventoryStackToHotbar(client, player, inventorySlot);
    }

    private int moveInventoryStackToHotbar(MinecraftClient client, ClientPlayerEntity player, int inventorySlot) {
        if (client == null || player == null || player.getInventory() == null || client.interactionManager == null) {
            return -1;
        }
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
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
        client.interactionManager.clickSlot(handler.syncId, handlerSlot, targetHotbarSlot, SlotActionType.SWAP, player);
        ItemStack hotbarStack = inventory.getStack(targetHotbarSlot);
        return !hotbarStack.isEmpty() && hotbarStack.getItem() instanceof BlockItem ? targetHotbarSlot : -1;
    }

    private int mapPlayerInventorySlot(ScreenHandler handler, int inventorySlot) {
        if (handler == null) {
            return -1;
        }
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (slot.inventory instanceof net.minecraft.entity.player.PlayerInventory && slot.getIndex() == inventorySlot) {
                return slotIdx;
            }
        }
        return -1;
    }

    private PlacementTarget findPlacementTarget(World world, BlockPos placePos) {
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
            BlockPos support = placePos.offset(direction);
            if (!hasCollision(world, support)) {
                continue;
            }
            Direction face = direction.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(support).add(
                face.getOffsetX() * 0.5D,
                face.getOffsetY() * 0.5D,
                face.getOffsetZ() * 0.5D
            );
            return new PlacementTarget(support, face, hitPos);
        }
        return null;
    }

    private BlockPos resolvePlayerFootPos(ClientPlayerEntity player) {
        return player == null ? null : player.getBlockPos().toImmutable();
    }

    private static void releaseMovementKeys(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }
        if (client.options.forwardKey != null) {
            client.options.forwardKey.setPressed(false);
        }
        if (client.options.backKey != null) {
            client.options.backKey.setPressed(false);
        }
        if (client.options.leftKey != null) {
            client.options.leftKey.setPressed(false);
        }
        if (client.options.rightKey != null) {
            client.options.rightKey.setPressed(false);
        }
        if (client.options.jumpKey != null) {
            client.options.jumpKey.setPressed(false);
        }
        if (client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(false);
        }
    }

    private static void applySneakState(MinecraftClient client, boolean active) {
        if (client == null || client.player == null) {
            return;
        }
        boolean previous = client.player.isSneaking();
        client.player.setSneaking(active);
        if (client.options != null && client.options.sneakKey != null) {
            client.options.sneakKey.setPressed(active);
        }
        if (client.player.networkHandler != null && previous != active) {
            ClientCommandC2SPacket.Mode mode = resolveSneakCommandMode(active);
            if (mode != null) {
                client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, mode));
            }
        }
    }

    private static BlockHitResult raycastBlockFromOrientation(MinecraftClient client, float yaw, float pitch, double distance) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        Vec3d eyePos = client.player.getEyePos();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3d direction = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        Vec3d end = eyePos.add(direction.multiply(distance));
        HitResult hit = client.world.raycast(new RaycastContext(
            eyePos,
            end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            client.player
        ));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }

    private static ClientCommandC2SPacket.Mode resolveSneakCommandMode(boolean active) {
        String[] candidates = active
            ? new String[]{"PRESS_SHIFT_KEY", "START_SNEAKING"}
            : new String[]{"RELEASE_SHIFT_KEY", "STOP_SNEAKING"};
        for (String candidate : candidates) {
            try {
                return ClientCommandC2SPacket.Mode.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try the next compatible enum name.
            }
        }
        return null;
    }

    private static Method resolveSyncSelectedSlotMethod() {
        try {
            return net.minecraft.client.network.ClientPlayerInteractionManager.class.getMethod("syncSelectedSlot");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void syncSelectedHotbarSlot(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (client.player != null && client.player.networkHandler != null) {
            try {
                int selectedSlot = PlayerInventoryBridge.getSelectedSlot(client.player.getInventory());
                if (selectedSlot >= 0) {
                    client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
                }
            } catch (IllegalStateException ignored) {
                // Fall back to interaction-manager sync below.
            }
        }
        if (client.interactionManager == null || SYNC_SELECTED_SLOT_METHOD == null) {
            return;
        }
        try {
            SYNC_SELECTED_SLOT_METHOD.invoke(client.interactionManager);
        } catch (ReflectiveOperationException ignored) {
            // Older mappings may not expose slot sync by name.
        }
    }

    private static float stepAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(delta, -maxStep, maxStep);
    }

    private record SearchVertex(BlockPos pos, SearchPrimitiveType arrivalType) {
    }

    private record SearchNode(SearchVertex vertex, double fScore, double gScore) {
        private BlockPos pos() {
            return vertex.pos();
        }
    }

    private record CoarseSearchNode(BlockPos pos, double fScore, double gScore) {
    }

    private record Neighbor(SearchVertex vertex, double cost, PlannedPrimitive primitive) {
        private BlockPos pos() {
            return vertex.pos();
        }
    }

    private record CoarseNeighbor(BlockPos pos, double cost, PlannedPrimitive primitive) {
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
            return new PlacementTargetState(target != null ? target.toImmutable() : null, false);
        }

        private static PlacementTargetState complete(BlockPos target) {
            return new PlacementTargetState(target != null ? target.toImmutable() : null, true);
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

    private enum SearchPrimitiveType {
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

    private record BreakTargeting(BlockPos target, Direction face, Vec3d hitPos) {
    }

    private record Move(int dx, int dz, double cost) {
    }

    private enum MoveType {
        STRAIGHT,
        DIAGONAL,
        STEP_UP,
        DROP,
        WATER_ENTER,
        WATER_SWIM,
        WATER_EXIT,
        CLIMB_UP,
        CLIMB_DOWN,
        INTERACTABLE
    }

    private enum PlannedPrimitiveType {
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

    private enum PrimitiveTraversal {
        GROUND,
        ASCENT,
        VERTICAL_ASCENT,
        DESCENT,
        CLIMB,
        SWIM,
        INTERACTABLE
    }

    private enum PrimitiveExecution {
        CONTINUOUS_MOVEMENT,
        COMMITTED_MOVEMENT,
        BREAK_THEN_MOVE,
        PLACE_THEN_MOVE,
        INTERACT_THEN_MOVE
    }

    private record PlannedPrimitive(
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
        private boolean requiresBreak() {
            return breakTargets != null && !breakTargets.isEmpty();
        }

        private boolean requiresPlace() {
            return placeTarget != null;
        }

        private boolean requiresWorldModification() {
            return requiresBreak() || requiresPlace();
        }

        private boolean isPillar() {
            return type == PlannedPrimitiveType.PILLAR;
        }

        private boolean isClimb() {
            return traversal == PrimitiveTraversal.CLIMB;
        }

        private boolean isDescend() {
            return traversal == PrimitiveTraversal.DESCENT;
        }

        private boolean isSwim() {
            return traversal == PrimitiveTraversal.SWIM;
        }

        private boolean isInteractable() {
            return traversal == PrimitiveTraversal.INTERACTABLE;
        }

        private boolean isJump() {
            return type == PlannedPrimitiveType.JUMP_ASCEND || type == PlannedPrimitiveType.MINE_ASCEND;
        }

        private boolean isMineAscent() {
            return searchType == SearchPrimitiveType.MINE_ASCEND;
        }

        private boolean isSimpleMovementStep() {
            if (searchType == null) {
                return false;
            }
            return switch (searchType) {
                case WALK -> !requiresWorldModification();
                case DESCEND -> !requiresWorldModification();
                case BREAK_FORWARD, PLACE_FORWARD, INTERACT, JUMP_ASCEND, MINE_ASCEND, CLIMB, SWIM, PILLAR -> false;
            };
        }

        private boolean shouldCommitAscent(BlockPos waypoint, BlockPos playerFootPos) {
            return isJump()
                && waypoint != null
                && playerFootPos != null
                && waypoint.getY() > playerFootPos.getY();
        }

        private boolean shouldCommitDrop(BlockPos waypoint, BlockPos playerFootPos) {
            return isDescend()
                && waypoint != null
                && playerFootPos != null
                && waypoint.getY() < playerFootPos.getY();
        }

        private boolean isCommittedTraversal() {
            return execution == PrimitiveExecution.COMMITTED_MOVEMENT
                || execution == PrimitiveExecution.BREAK_THEN_MOVE
                || execution == PrimitiveExecution.PLACE_THEN_MOVE
                || execution == PrimitiveExecution.INTERACT_THEN_MOVE;
        }

        private boolean isPassiveTraversal() {
            return !requiresWorldModification()
                && (traversal == PrimitiveTraversal.GROUND
                || traversal == PrimitiveTraversal.DESCENT
                || traversal == PrimitiveTraversal.CLIMB
                || traversal == PrimitiveTraversal.SWIM);
        }

        private boolean requiresCommittedAction() {
            return isCommittedTraversal() || isPillar();
        }

        private boolean allowsForwardResync() {
            if (searchType == null) {
                return true;
            }
            return switch (searchType) {
                case WALK, BREAK_FORWARD, INTERACT, DESCEND -> true;
                case PLACE_FORWARD, JUMP_ASCEND, MINE_ASCEND, CLIMB, SWIM, PILLAR -> false;
            };
        }
    }

    private record ScoredPos(BlockPos pos, double score) {
    }

    private record PathComputation(
        List<BlockPos> path,
        List<PlannedPrimitive> plannedPrimitives,
        List<List<BlockPos>> candidatePaths,
        BlockPos resolvedGoalPos,
        GoalMode goalMode,
        FailureReason failureReason,
        String failureDetail
    ) {
    }

    private record GoalSearchOutcome(List<ScoredPath> scoredPaths, FailureReason failureReason, String failureDetail) {
    }

    private record PathSearchResult(
        List<BlockPos> path,
        List<PlannedPrimitive> plannedPrimitives,
        double cost,
        FailureReason failureReason,
        String failureDetail
    ) {
    }

    private record ScoredPath(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives, double cost) {
    }

    private record ReconstructedPath(List<BlockPos> path, List<PlannedPrimitive> plannedPrimitives) {
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

    private record PlacementTarget(BlockPos supportPos, Direction face, Vec3d hitPos) {
    }

    private record EdgeKey(BlockPos from, BlockPos to) {
    }

    private enum FailureReason {
        CLIENT_UNAVAILABLE("Pathmind Nav failed: client or world unavailable."),
        NO_START_SPACE("Pathmind Nav failed: no valid space to start pathfinding from your position."),
        NO_GOAL_SPACE("Pathmind Nav failed: no standable block near the target."),
        NO_LOADED_FRONTIER("Pathmind Nav failed: no reachable loaded terrain toward the target yet."),
        SEARCH_LIMIT("Pathmind Nav failed: search complexity limit reached before finding a route."),
        NO_ROUTE("Pathmind Nav failed: no walking route to the target.");

        private final String message;

        FailureReason(String message) {
            this.message = message;
        }
    }
}
