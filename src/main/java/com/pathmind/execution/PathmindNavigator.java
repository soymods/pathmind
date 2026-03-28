package com.pathmind.execution;

import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Bounded Pathmind-owned movement backend used when Baritone is unavailable.
 * This currently implements local walkable-space pathfinding for goto-style movement and stop/cancel.
 */
public final class PathmindNavigator {
    private static final PathmindNavigator INSTANCE = new PathmindNavigator();
    private static final double WAYPOINT_REACHED_DISTANCE_SQ = 0.64D;
    private static final float MAX_YAW_STEP = 14.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final long STUCK_TIMEOUT_MS = 1500L;
    private static final long REPLAN_COOLDOWN_MS = 450L;
    private static final long JUMP_RETRY_COOLDOWN_MS = 250L;
    private static final long JUMP_COMMIT_WINDOW_MS = 900L;
    private static final long FAILED_MOVE_MEMORY_MS = 4000L;
    private static final long NO_MOVEMENT_REPLAN_MS = 900L;
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
    private static final int MAX_EXPANSIONS = 18000;
    private static final int MAX_GOAL_CANDIDATES = 18;
    private static final int MAX_VISIBLE_CANDIDATE_PATHS = 3;
    private static final int MAX_GOAL_PATH_ATTEMPTS = 10;
    private static final double WATER_PENALTY = 3.5D;
    private static final double EDGE_PENALTY = 1.25D;
    private static final double DANGER_PENALTY = 12.0D;
    private static final double MIN_PROGRESS_FOR_REPLAN_SQ = 9.0D;
    private static final Move[] MOVES = {
        new Move(0, -1, 1.0D),
        new Move(0, 1, 1.0D),
        new Move(-1, 0, 1.0D),
        new Move(1, 0, 1.0D),
        new Move(-1, -1, Math.sqrt(2.0D)),
        new Move(-1, 1, Math.sqrt(2.0D)),
        new Move(1, -1, Math.sqrt(2.0D)),
        new Move(1, 1, Math.sqrt(2.0D))
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
    private BlockPos resolvedGoalPos;
    private List<BlockPos> currentPath = List.of();
    private List<List<BlockPos>> candidatePaths = List.of();
    private int pathIndex;
    private BlockPos activeWaypoint;
    private BlockPos committedJumpWaypoint;
    private long committedJumpUntilMs;
    private long lastInteractAtMs;
    private Vec3d lastMovementSamplePos = Vec3d.ZERO;
    private long lastMovementAtMs;
    private final Map<EdgeKey, Long> failedEdges = new HashMap<>();
    private final Map<BlockPos, Long> failedNodes = new HashMap<>();
    private String lastReplanReason = "none";
    private String lastStuckReason = "none";

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

    public record Snapshot(
        boolean active,
        State state,
        BlockPos targetPos,
        BlockPos resolvedGoalPos,
        BlockPos activeWaypoint,
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
        String goalMode,
        BlockPos targetPos,
        BlockPos resolvedGoalPos,
        BlockPos activeWaypoint,
        int pathIndex,
        int nodeCount,
        String lastReplanReason,
        String lastStuckReason
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
        this.currentPath = List.of();
        this.candidatePaths = List.of();
        this.pathIndex = 0;
        this.activeWaypoint = null;
        this.committedJumpWaypoint = null;
        this.committedJumpUntilMs = 0L;
        this.lastInteractAtMs = 0L;
        this.lastMovementSamplePos = Vec3d.ofCenter(this.targetPos);
        this.lastMovementAtMs = this.startedAtMs;
        this.failedEdges.clear();
        this.failedNodes.clear();
        this.lastReplanReason = "start goto";
        this.lastStuckReason = "none";
        return true;
    }

    public synchronized boolean isActive() {
        return state == State.PATHING && activeFuture != null && targetPos != null;
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
        List<BlockPos> pathCopy = currentPath.isEmpty() ? List.of() : List.copyOf(currentPath);
        List<List<BlockPos>> candidateCopies = candidatePaths.isEmpty()
            ? List.of()
            : candidatePaths.stream().map(List::copyOf).toList();
        BlockPos resolvedGoal = resolvedGoalPos != null ? resolvedGoalPos : (pathCopy.isEmpty() ? targetPos : pathCopy.get(pathCopy.size() - 1));
        return new Snapshot(
            isActive(),
            state,
            targetPos,
            resolvedGoal,
            activeWaypoint,
            commandLabel,
            distance,
            pathCopy.size(),
            Math.max(0L, System.currentTimeMillis() - startedAtMs),
            pathCopy,
            candidateCopies
        );
    }

    public synchronized DebugInfo getDebugInfo() {
        if (targetPos == null && state == State.IDLE) {
            return null;
        }
        return new DebugInfo(
            state,
            goalMode.name(),
            targetPos,
            resolvedGoalPos,
            activeWaypoint,
            pathIndex,
            currentPath.size(),
            lastReplanReason,
            lastStuckReason
        );
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
        this.currentPath = List.of();
        this.candidatePaths = List.of();
        this.pathIndex = 0;
        this.activeWaypoint = null;
        this.committedJumpWaypoint = null;
        this.committedJumpUntilMs = 0L;
        this.lastInteractAtMs = 0L;
        this.lastMovementSamplePos = Vec3d.ofCenter(this.targetPos);
        this.lastMovementAtMs = this.startedAtMs;
        this.lastReplanReason = "preview";
        this.lastStuckReason = "none";
        this.failedEdges.clear();
        this.failedNodes.clear();

        BlockPos start = resolvePlayerFootPos(client.player);
        PathComputation computation = findPath(client.world, start, this.targetPos);
        if (computation.path().isEmpty()) {
            stopInternal(false, "preview failed");
            return new PreviewResult(false, computation.failureReason() != null ? computation.failureReason().message : FailureReason.NO_ROUTE.message);
        }

        this.currentPath = computation.path();
        this.candidatePaths = computation.candidatePaths();
        this.goalMode = computation.goalMode();
        this.resolvedGoalPos = computation.resolvedGoalPos();
        this.pathIndex = chooseInitialPathIndex(this.currentPath, start, this.targetPos);
        this.activeWaypoint = this.currentPath.get(this.pathIndex);
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
        BlockPos completionTarget = goalMode == GoalMode.NEAREST_STANDABLE && resolvedGoal != null ? resolvedGoal : target;
        long now = System.currentTimeMillis();

        synchronized (this) {
            if (currentPos.squaredDistanceTo(lastMovementSamplePos) > MOVEMENT_EPSILON_SQ) {
                lastMovementSamplePos = currentPos;
                lastMovementAtMs = now;
            }
        }

        if (isWithinGoalArrivalRange(player, completionTarget)) {
            releaseMovementKeys(client);
            complete(State.ARRIVED);
            return;
        }

        ClientWorld world = client.world;
        pruneFailureMemory(now);
        if (shouldReplan(world, playerFootPos, target, now)) {
            PathComputation computation = findPath(world, playerFootPos, target);
            if (computation.path().isEmpty()) {
                fail(computation.failureReason());
                return;
            }
            List<BlockPos> newPath = computation.path();
            synchronized (this) {
                currentPath = newPath;
                candidatePaths = computation.candidatePaths();
                goalMode = computation.goalMode();
                resolvedGoalPos = computation.resolvedGoalPos();
                pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                activeWaypoint = currentPath.get(pathIndex);
                lastPlanAtMs = now;
                bestDistanceSq = distanceSq;
                lastProgressAtMs = now;
                lastReplanReason = "planner replan";
            }
        }

        BlockPos waypoint = chooseActiveWaypoint(world, player, playerFootPos);
        if (waypoint == null) {
            PathComputation recovery = findPath(world, playerFootPos, target);
            if (!recovery.path().isEmpty()) {
                synchronized (this) {
                    currentPath = recovery.path();
                    candidatePaths = recovery.candidatePaths();
                    goalMode = recovery.goalMode();
                    resolvedGoalPos = recovery.resolvedGoalPos();
                    pathIndex = chooseInitialPathIndex(currentPath, playerFootPos, target);
                    activeWaypoint = currentPath.get(pathIndex);
                    lastPlanAtMs = now;
                    lastProgressAtMs = now;
                    lastReplanReason = "waypoint recovery";
                }
                waypoint = chooseActiveWaypoint(world, player, playerFootPos);
            }
        }

        if (waypoint == null) {
            releaseMovementKeys(client);
            synchronized (this) {
                currentPath = List.of();
                candidatePaths = List.of();
                activeWaypoint = null;
                pathIndex = 0;
                resolvedGoalPos = target;
                lastPlanAtMs = 0L;
                lastProgressAtMs = now;
            }
            return;
        }

        BlockPos climbAnchor = resolveClimbAnchor(world, playerFootPos, waypoint);
        boolean climbNode = climbAnchor != null;
        Vec3d waypointCenter = new Vec3d(
            (climbNode ? climbAnchor.getX() : waypoint.getX()) + 0.5D,
            player.getY(),
            (climbNode ? climbAnchor.getZ() : waypoint.getZ()) + 0.5D
        );
        double waypointDx = waypointCenter.x - currentPos.x;
        double waypointDz = waypointCenter.z - currentPos.z;
        double waypointHorizontalDistance = Math.sqrt(waypointDx * waypointDx + waypointDz * waypointDz);
        double waypointVerticalDelta = (waypoint.getY() + 0.1D) - currentPos.y;

        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(waypointDz, waypointDx)) - 90.0D));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(waypointVerticalDelta, Math.max(0.0001D, waypointHorizontalDistance)));
        float nextYaw = stepAngle(player.getYaw(), targetYaw, MAX_YAW_STEP);
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
        boolean interactableStep = requiresInteractableTraversal(world, playerFootPos, waypoint)
            || hasPathOpenableAhead(world, playerFootPos, waypoint)
            || isPathOpenable(world.getBlockState(playerFootPos.down()));
        boolean verticalDropStep = playerFootPos.getX() == waypoint.getX()
            && playerFootPos.getZ() == waypoint.getZ()
            && waypoint.getY() < playerFootPos.getY()
            && canSafelyDropTo(world, playerFootPos, waypoint);
        boolean applyCountermovement = !climbNode && committedJumpWaypoint == null && (overshootRisk || airborneDriftRisk);
        long progressNow = System.currentTimeMillis();

        tryUseInteractables(client, world, player, playerFootPos, waypoint, progressNow);
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
        }

        if (client.options != null) {
            if (client.options.forwardKey != null) {
                client.options.forwardKey.setPressed(!verticalDropStep && (climbNode || !applyCountermovement));
            }
            if (client.options.sprintKey != null) {
                client.options.sprintKey.setPressed(!verticalDropStep && !climbNode && !interactableStep && !applyCountermovement && player.isOnGround() && waypointHorizontalDistance > 1.75D);
            }
            if (client.options.backKey != null) {
                client.options.backKey.setPressed(!verticalDropStep && !climbNode && applyCountermovement && forwardVelocity > COUNTERMOVEMENT_SPEED);
            }
            if (client.options.leftKey != null) {
                client.options.leftKey.setPressed(!verticalDropStep && !climbNode && applyCountermovement && lateralVelocity < -COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.rightKey != null) {
                client.options.rightKey.setPressed(!verticalDropStep && !climbNode && applyCountermovement && lateralVelocity > COUNTERMOVEMENT_LATERAL_SPEED);
            }
            if (client.options.jumpKey != null) {
                boolean swimUp = player.isSubmergedInWater() || isWaterNode(world, playerFootPos) || isWaterNode(world, waypoint);
                client.options.jumpKey.setPressed(!verticalDropStep && ((swimUp && waypoint.getY() >= playerFootPos.getY()) || climbUp));
            }
            if (client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(!verticalDropStep && climbDown);
            }
        }
        synchronized (this) {
            if (distanceSq + PROGRESS_EPSILON_SQ < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                lastProgressAtMs = progressNow;
            }
        }

        long millisSinceProgress;
        long millisSinceMovement;
        synchronized (this) {
            millisSinceProgress = progressNow - lastProgressAtMs;
            millisSinceMovement = progressNow - lastMovementAtMs;
        }

        if (millisSinceMovement > NO_MOVEMENT_REPLAN_MS) {
            releaseMovementKeys(client);
            synchronized (this) {
                rememberFailedMove(playerFootPos, waypoint, progressNow);
                currentPath = List.of();
                candidatePaths = List.of();
                activeWaypoint = null;
                pathIndex = 0;
                committedJumpWaypoint = null;
                committedJumpUntilMs = 0L;
                lastPlanAtMs = 0L;
                lastReplanReason = "no movement";
                lastStuckReason = "player stationary";
                lastMovementAtMs = progressNow;
                lastMovementSamplePos = currentPos;
            }
            return;
        }

        if (millisSinceProgress > STUCK_TIMEOUT_MS) {
            releaseMovementKeys(client);
            synchronized (this) {
                rememberFailedMove(playerFootPos, waypoint, progressNow);
                currentPath = List.of();
                candidatePaths = List.of();
                activeWaypoint = null;
                pathIndex = 0;
                committedJumpWaypoint = null;
                committedJumpUntilMs = 0L;
                lastPlanAtMs = 0L;
                lastReplanReason = "stuck timeout";
                lastStuckReason = "no progress";
            }
            return;
        }

        long millisSinceJump;
        synchronized (this) {
            millisSinceJump = progressNow - lastJumpAtMs;
        }

        if (player.isOnGround()
            && millisSinceJump >= JUMP_RETRY_COOLDOWN_MS
            && ((!interactableStep && waypoint.getY() > playerFootPos.getY())
            || (shouldStepJump(world, playerFootPos, waypoint) && !climbNode && !interactableStep))) {
            player.jump();
            synchronized (this) {
                lastJumpAtMs = progressNow;
                committedJumpWaypoint = waypoint.toImmutable();
                committedJumpUntilMs = progressNow + JUMP_COMMIT_WINDOW_MS;
            }
        }

        if (player.isOnGround() && committedJumpWaypoint != null) {
            synchronized (this) {
                if (playerFootPos.equals(committedJumpWaypoint)) {
                    committedJumpWaypoint = null;
                    committedJumpUntilMs = 0L;
                } else if (progressNow > committedJumpUntilMs) {
                    rememberFailedMove(playerFootPos, committedJumpWaypoint, progressNow);
                    committedJumpWaypoint = null;
                    committedJumpUntilMs = 0L;
                    currentPath = List.of();
                    candidatePaths = List.of();
                    activeWaypoint = null;
                    pathIndex = 0;
                    lastPlanAtMs = 0L;
                    lastReplanReason = "jump recovery";
                    lastStuckReason = "missed jump";
                }
            }
        }
    }

    public synchronized void stop(String reason) {
        stopInternal(true, reason);
    }

    public synchronized void reset() {
        stopInternal(false, "reset");
    }

    private synchronized void fail(FailureReason failureReason) {
        releaseMovementKeys(MinecraftClient.getInstance());
        state = State.FAILED;
        CompletableFuture<Void> future = activeFuture;
        String message = failureReason != null ? failureReason.message : FailureReason.NO_ROUTE.message;
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        currentPath = List.of();
        candidatePaths = List.of();
        pathIndex = 0;
        activeWaypoint = null;
        resolvedGoalPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastInteractAtMs = 0L;
        lastMovementSamplePos = Vec3d.ZERO;
        lastMovementAtMs = 0L;
        NodeErrorNotificationOverlay.getInstance().show(message, UITheme.STATE_ERROR);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new RuntimeException(message));
        }
        state = State.IDLE;
    }

    private synchronized void complete(State terminalState) {
        state = terminalState;
        CompletableFuture<Void> future = activeFuture;
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        currentPath = List.of();
        candidatePaths = List.of();
        pathIndex = 0;
        activeWaypoint = null;
        resolvedGoalPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastInteractAtMs = 0L;
        lastMovementSamplePos = Vec3d.ZERO;
        lastMovementAtMs = 0L;
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
        state = State.IDLE;
    }

    private void stopInternal(boolean completeFuture, String reason) {
        releaseMovementKeys(MinecraftClient.getInstance());
        if (activeFuture != null && completeFuture && !activeFuture.isDone()) {
            activeFuture.complete(null);
        }
        activeFuture = null;
        targetPos = null;
        commandLabel = null;
        bestDistanceSq = Double.MAX_VALUE;
        currentPath = List.of();
        candidatePaths = List.of();
        pathIndex = 0;
        activeWaypoint = null;
        resolvedGoalPos = null;
        committedJumpWaypoint = null;
        committedJumpUntilMs = 0L;
        lastInteractAtMs = 0L;
        lastMovementSamplePos = Vec3d.ZERO;
        lastMovementAtMs = 0L;
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
                return true;
            }
            if (now - lastPlanAtMs >= REPLAN_COOLDOWN_MS && now - lastProgressAtMs >= STUCK_TIMEOUT_MS) {
                return true;
            }
            if (!isStandable(world, activeWaypoint)) {
                return true;
            }
            if (!isPlayerNearPath(start)) {
                return true;
            }
            return !isPathGoalStillValid(currentPath, target);
        }
    }

    private boolean isPathGoalStillValid(List<BlockPos> path, BlockPos target) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        BlockPos last = path.get(path.size() - 1);
        return horizontalDistanceSq(last, target) <= 4.0D && Math.abs(last.getY() - target.getY()) <= MAX_DROP_DOWN;
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

    private BlockPos chooseActiveWaypoint(ClientWorld world, ClientPlayerEntity player, BlockPos playerFootPos) {
        if (player == null) {
            return null;
        }
        BlockPos current = advanceWaypointIfNeeded(player, playerFootPos);
        if (current == null) {
            return null;
        }
        synchronized (this) {
            activeWaypoint = currentPath.get(pathIndex);
            return activeWaypoint;
        }
    }

    private BlockPos advanceWaypointIfNeeded(ClientPlayerEntity player, BlockPos playerFootPos) {
        if (player == null || playerFootPos == null) {
            return null;
        }
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        synchronized (this) {
            while (!currentPath.isEmpty() && pathIndex < currentPath.size()) {
                BlockPos waypoint = currentPath.get(pathIndex);
                Vec3d waypointCenter = new Vec3d(waypoint.getX() + 0.5D, playerPos.y, waypoint.getZ() + 0.5D);
                if (!shouldAdvancePastWaypoint(playerPos, playerFootPos, waypoint, waypointCenter)) {
                    activeWaypoint = waypoint;
                    return waypoint;
                }
                pathIndex++;
            }
            activeWaypoint = null;
            return null;
        }
    }

    private boolean shouldAdvancePastWaypoint(Vec3d playerPos, BlockPos playerFootPos, BlockPos waypoint, Vec3d waypointCenter) {
        if (waypoint == null || waypointCenter == null || playerFootPos == null || playerPos == null) {
            return true;
        }
        return playerPos.squaredDistanceTo(waypointCenter) <= WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(waypoint.getY() - playerFootPos.getY()) <= 1;
    }

    private int chooseInitialPathIndex(List<BlockPos> path, BlockPos playerFootPos, BlockPos target) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        int fallback = Math.min(1, Math.max(0, path.size() - 1));
        if (playerFootPos == null || target == null) {
            return fallback;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos step = path.get(i);
            if (step == null) {
                continue;
            }
            if (horizontalDistanceSq(playerFootPos, step) <= WAYPOINT_REACHED_DISTANCE_SQ
                && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                continue;
            }
            return i;
        }
        return Math.max(0, path.size() - 1);
    }

    private PathComputation findPath(ClientWorld world, BlockPos start, BlockPos target) {
        if (world == null || start == null || target == null) {
            return new PathComputation(List.of(), List.of(), null, GoalMode.EXACT, FailureReason.CLIENT_UNAVAILABLE);
        }

        BlockPos normalizedStart = isNavigableNode(world, start) ? start.toImmutable() : findNearbyStandable(world, start, 2);
        if (normalizedStart == null) {
            return new PathComputation(List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_START_SPACE);
        }

        BlockPos planningTarget = resolvePlanningTarget(world, normalizedStart, target);
        if (planningTarget == null) {
            return new PathComputation(List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_LOADED_FRONTIER);
        }

        List<BlockPos> goalCandidates = collectGoalCandidates(world, normalizedStart, planningTarget);
        if (goalCandidates.isEmpty()) {
            BlockPos nearby = findNearbyStandable(world, planningTarget, 4);
            if (nearby != null) {
                goalCandidates = List.of(nearby);
            }
        }
        if (goalCandidates.isEmpty()) {
            return new PathComputation(List.of(), List.of(), null, GoalMode.EXACT, FailureReason.NO_GOAL_SPACE);
        }

        List<ScoredPath> scoredPaths = new ArrayList<>();
        FailureReason lastFailure = FailureReason.NO_ROUTE;
        int candidateCount = Math.min(MAX_GOAL_PATH_ATTEMPTS, goalCandidates.size());
        for (int i = 0; i < candidateCount; i++) {
            PathSearchResult result = findPathToGoal(world, normalizedStart, goalCandidates.get(i));
            if (!result.path().isEmpty()) {
                scoredPaths.add(new ScoredPath(result.path(), result.cost()));
            } else if (result.failureReason() != null) {
                lastFailure = result.failureReason();
            }
        }

        if (scoredPaths.isEmpty()) {
            return new PathComputation(List.of(), List.of(), null, GoalMode.EXACT, lastFailure);
        }

        scoredPaths.sort(Comparator.comparingDouble(ScoredPath::cost));
        List<List<BlockPos>> visibleCandidates = scoredPaths.stream()
            .map(ScoredPath::path)
            .limit(MAX_VISIBLE_CANDIDATE_PATHS)
            .toList();
        BlockPos resolvedGoal = scoredPaths.get(0).path().isEmpty() ? null : scoredPaths.get(0).path().get(scoredPaths.get(0).path().size() - 1);
        GoalMode goalMode = resolvedGoal != null && resolvedGoal.equals(target) ? GoalMode.EXACT : GoalMode.NEAREST_STANDABLE;
        return new PathComputation(scoredPaths.get(0).path(), visibleCandidates, resolvedGoal, goalMode, null);
    }

    private PathSearchResult findPathToGoal(ClientWorld world, BlockPos start, BlockPos goal) {
        Set<BlockPos> goalSet = Set.of(goal);
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScore.put(start, 0.0D);
        openSet.add(new SearchNode(start, heuristic(start, List.of(goal)), 0.0D));

        int expansions = 0;
        while (!openSet.isEmpty() && expansions < MAX_EXPANSIONS) {
            SearchNode current = openSet.poll();
            if (closed.contains(current.pos)) {
                continue;
            }
            if (isGoal(current.pos, goal, goalSet)) {
                List<BlockPos> rawPath = reconstructPath(cameFrom, current.pos);
                return new PathSearchResult(rawPath, current.gScore, null);
            }
            closed.add(current.pos);
            expansions++;

            for (Neighbor neighbor : getNeighbors(world, current.pos, start, goal)) {
                if (closed.contains(neighbor.pos)) {
                    continue;
                }
                double tentativeG = current.gScore
                    + neighbor.cost
                    + elevationPenalty(current.pos, neighbor.pos)
                    + terrainPenalty(world, current.pos, neighbor.pos);
                double knownG = gScore.getOrDefault(neighbor.pos, Double.POSITIVE_INFINITY);
                if (tentativeG >= knownG) {
                    continue;
                }
                cameFrom.put(neighbor.pos, current.pos);
                gScore.put(neighbor.pos, tentativeG);
                openSet.add(new SearchNode(neighbor.pos, tentativeG + heuristic(neighbor.pos, List.of(goal)), tentativeG));
            }
        }

        return new PathSearchResult(
            List.of(),
            Double.POSITIVE_INFINITY,
            expansions >= MAX_EXPANSIONS ? FailureReason.SEARCH_LIMIT : FailureReason.NO_ROUTE
        );
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cursor = end;
        while (cursor != null) {
            path.add(cursor);
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        return path;
    }

    private List<Neighbor> getNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal) {
        List<Neighbor> neighbors = new ArrayList<>(MOVES.length);
        long now = System.currentTimeMillis();
        for (Move move : MOVES) {
            BlockPos candidate = findNeighbor(world, current, move.dx, move.dz, start, goal, false);
            if (candidate == null) {
                continue;
            }
            if (isFailedNode(candidate, now) || isFailedEdge(current, candidate, now)) {
                continue;
            }
            neighbors.add(new Neighbor(candidate, move.cost + moveTypePenalty(world, current, candidate)));
        }
        addClimbNeighbors(world, current, start, goal, neighbors, now);
        addSafeDropNeighbors(world, current, start, goal, neighbors, now);
        return neighbors;
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
            neighbors.add(new Neighbor(candidate.toImmutable(), 1.0D + moveTypePenalty(world, current, candidate)));
        }
    }

    private void addSafeDropNeighbors(World world, BlockPos current, BlockPos start, BlockPos goal, List<Neighbor> neighbors, long now) {
        if (world == null || current == null) {
            return;
        }
        for (int drop = 1; drop <= MAX_SAFE_FALL_DISTANCE; drop++) {
            BlockPos candidate = current.add(0, -drop, 0);
            if (!isWithinSearchBounds(start, candidate, goal)
                || !isChunkLoaded(world, candidate)
                || !isNavigableNode(world, candidate)
                || isHardDanger(world, candidate)
                || isFailedNode(candidate, now)
                || isFailedEdge(current, candidate, now)
                || !canSafelyDropTo(world, current, candidate)) {
                continue;
            }
            neighbors.add(new Neighbor(candidate.toImmutable(), drop + moveTypePenalty(world, current, candidate) + 0.18D));
        }
    }

    private BlockPos findNeighbor(World world, BlockPos current, int dx, int dz, BlockPos start, BlockPos goal, boolean allowRelaxedBounds) {
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
            if (!isNavigableNode(world, candidate)) {
                continue;
            }
            if (Math.abs(candidate.getY() - current.getY()) > MAX_STEP_UP && candidate.getY() > current.getY()) {
                continue;
            }
            if (isHardDanger(world, candidate)) {
                continue;
            }
            return candidate.toImmutable();
        }
        return null;
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
            && isNavigableNode(world, target)
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
                            || !isNavigableNode(world, candidate)
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
        double exactTargetBias = candidate.equals(target) ? -0.75D : 0.0D;
        double failedPenalty = isFailedNode(candidate, System.currentTimeMillis()) ? FAILED_MOVE_PENALTY : 0.0D;
        return horizontal + verticalPenalty + opennessBonus + exactTargetBias + failedPenalty + terrainPenalty(world, candidate, candidate);
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
            case DIAGONAL -> 0.08D;
            case STEP_UP -> 0.32D;
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

    private void pruneFailureMemory(long now) {
        failedNodes.entrySet().removeIf(entry -> entry.getValue() <= now);
        failedEdges.entrySet().removeIf(entry -> entry.getValue() <= now);
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
        return best == Double.POSITIVE_INFINITY ? 0.0D : best;
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
        if (!hasCollision(world, below)) {
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

    private boolean canOccupy(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return true;
        }
        if (isClimbableBlock(state) || isPathOpenable(state)) {
            return true;
        }
        return state.getCollisionShape(world, pos).isEmpty();
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

    private double terrainPenalty(World world, BlockPos from, BlockPos to) {
        double penalty = 0.0D;
        if (isWaterNode(world, to)) {
            penalty += WATER_PENALTY;
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
        return penalty;
    }

    private boolean isWaterNode(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isWater(world.getFluidState(pos)) || isWater(world.getFluidState(pos.up()));
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

    private static float stepAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(delta, -maxStep, maxStep);
    }

    private record SearchNode(BlockPos pos, double fScore, double gScore) {
    }

    private record Neighbor(BlockPos pos, double cost) {
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

    private record ScoredPos(BlockPos pos, double score) {
    }

    private record PathComputation(
        List<BlockPos> path,
        List<List<BlockPos>> candidatePaths,
        BlockPos resolvedGoalPos,
        GoalMode goalMode,
        FailureReason failureReason
    ) {
    }

    private record PathSearchResult(List<BlockPos> path, double cost, FailureReason failureReason) {
    }

    private record ScoredPath(List<BlockPos> path, double cost) {
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
