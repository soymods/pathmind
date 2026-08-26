package com.pathmind.execution;

import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.PlayerInventoryBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.pathmind.execution.PathmindNavigator.WAYPOINT_REACHED_DISTANCE_SQ;
import static com.pathmind.execution.PathmindNavigator.WAYPOINT_NEAR_DISTANCE_SQ;
import static com.pathmind.execution.PathmindNavigator.TRAPPED_RECOVERY_COMMIT_MS;
import static com.pathmind.execution.PathmindNavigator.ROUTE_COMMIT_MS;
import static com.pathmind.execution.PathmindNavigator.PATH_DECISION_VISIBILITY_MS;
import static com.pathmind.execution.PathmindNavigator.PLACE_MOVE_PENALTY;
import static com.pathmind.execution.PathmindNavigator.applySneakState;
import static com.pathmind.execution.PathmindNavigator.movementYawStep;
import static com.pathmind.execution.PathmindNavigator.raycastBlockFromOrientation;
import static com.pathmind.execution.PathmindNavigator.releaseMovementKeys;
import static com.pathmind.execution.PathmindNavigator.stepAngle;

final class NavigatorPrimitiveExecutor {
    private static final float TURN_IN_PLACE_YAW_DEGREES = 52.0F;
    private static final float SPRINT_ALIGNMENT_DEGREES = 12.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final double DEFAULT_BLOCK_INTERACTION_REACH = 4.5D;
    private static final double BREAK_AIM_EPSILON = 0.001D;
    private static final float JUMP_YAW_ALIGNMENT_DEGREES = 18.0F;
    private static final long STUCK_TIMEOUT_MS = 1500L;
    private static final long JUMP_RETRY_COOLDOWN_MS = 250L;
    private static final long JUMP_COMMIT_WINDOW_MS = 1250L;
    private static final long NO_MOVEMENT_REPLAN_MS = 900L;
    private static final long STANDSTILL_REDIRECT_MS = 1600L;
    private static final long WALL_PUSH_REDIRECT_MS = 700L;
    private static final long DISTANCE_STALL_REDIRECT_MS = 2500L;
    private static final double PROGRESS_EPSILON_SQ = 0.01D;
    private static final double COUNTERMOVEMENT_DISTANCE = 0.9D;
    private static final double COUNTERMOVEMENT_SPEED = 0.16D;
    private static final double COUNTERMOVEMENT_LATERAL_SPEED = 0.08D;
    private static final double COUNTERMOVEMENT_PREDICTION_TICKS = 4.0D;
    private static final double AIR_COUNTERMOVEMENT_DISTANCE = 1.2D;
    private static final float COUNTERMOVEMENT_MAX_YAW_ERROR_DEGREES = 18.0F;
    private static final double COUNTERMOVEMENT_MIN_DISTANCE = 0.22D;

    interface Host {
        Object lock();
        boolean allowBlockBreaking();
        boolean allowBlockPlacing();
        BlockPos targetPos();
        GoalMode goalMode();
        void goalMode(GoalMode goalMode);
        void appendDebugEventLocked(String event);
        String formatDebugPos(BlockPos pos);
        boolean isWaypointActionable(Level world, BlockPos waypoint);
        boolean shouldTrackResolvedPlanningGoal(BlockPos target, BlockPos resolvedGoal, GoalMode goalMode);
        boolean isPlayerNearPath(BlockPos playerFootPos);
        boolean hasCommittedEscapeWorkLocked(long now);
        boolean isActiveEscapeBreakTargetLocked();
        boolean isJumpExecutionLocked(long now, PlannedPrimitive plannedPrimitive);
        void noteControllerProgress(long now, double distanceSq);
        double distanceToControllerTargetSq(Level world, LocalPlayer player, BlockPos fallbackTarget);
        void noteControllerActivity(long now);
        boolean isRouteStabilizingLocked(BlockPos playerFootPos, long now);
        void updateFollowSegment(FollowSegmentType type, BlockPos target, double segmentDistanceSq, long now);
        long followSegmentIdleMs(long now);
        boolean shouldRedirectController(long now, double distanceSq);
        boolean shouldUsePillarStep(Level world, BlockPos playerFootPos, BlockPos waypoint,
                                    PlannedPrimitive plannedPrimitive, long now);
        void clearStaleEscapeRecoveryIfNeeded(Level world, BlockPos playerFootPos, BlockPos waypoint,
                                              PlannedPrimitive plannedPrimitive, long now);
        void recoverFromStuck(Minecraft client, ClientLevel world, BlockPos playerFootPos, BlockPos waypoint,
                              BlockPos target, Vec3 currentPos, long now, String replanReason, String stuckReason);
        void rewindCurrentPathIndex(BlockPos playerFootPos, BlockPos preferredWaypoint);
        void redirectCurrentPath(BlockPos playerFootPos, BlockPos waypoint, Vec3 currentPos, long now,
                                 String replanReason, String stuckReason);
        void rememberFailedRedirectWindow(BlockPos playerFootPos, BlockPos waypoint, long now);
        List<BlockPos> buildPathBreakPlan(Level world, List<BlockPos> path, int startIndex);
        PlannedPrimitive createPrimitiveSnapshot(Level world, BlockPos from, BlockPos to,
                                                 SearchPrimitiveType searchType, PlannedPrimitiveType type,
                                                 List<BlockPos> breakTargets, BlockPos placeTarget);
        PlannedPrimitive getPlannedPrimitiveAtIndexLocked(int index);
        void rebuildCurrentPlanLocked(Level world);
        PathComputation findPath(ClientLevel world, BlockPos start, BlockPos target);
        BlockPos resolvePlayerFootPos(LocalPlayer player);
    }

    private final Host host;
    private final NavigatorExecutionState executionState;
    private final NavigatorNavigationState navigationState;
    private final PathmindPathPlanner pathPlanner;

    NavigatorPrimitiveExecutor(Host host, NavigatorExecutionState executionState,
                               NavigatorNavigationState navigationState, PathmindPathPlanner pathPlanner) {
        this.host = host;
        this.executionState = executionState;
        this.navigationState = navigationState;
        this.pathPlanner = pathPlanner;
    }

    void tryUseInteractables(
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
        if (now - executionState.lastInteractAtMs < 250L || client.gameMode == null) {
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
            synchronized (host.lock()) {
                executionState.lastInteractAtMs = now;
            }
            return;
        }
    }
    
    boolean handleWaypointBlockInteraction(
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
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        clearStalePlaceStateIfNeeded(world, plannedPrimitive);
        host.clearStaleEscapeRecoveryIfNeeded(world, playerFootPos, waypoint, plannedPrimitive, now);
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        if (!isWaypointPrimitiveAligned(waypoint, plannedPrimitive)) {
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        if (shouldSuppressMiningNearGoal(world, player, playerFootPos, waypoint)) {
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
    
        synchronized (host.lock()) {
            if (executionState.activeBreakTarget != null && pathPlanner.canOccupy(world, executionState.activeBreakTarget)) {
                executionState.activeBreakTarget = null;
                executionState.lastMinedBlockAtMs = now;
                navigationState.lastProgressAtMs = now;
                navigationState.lastReplanReason = "obstruction cleared";
            }
        }
    
        if (handleCommittedMiningInteraction(client, world, player, playerFootPos, waypoint, plannedPrimitive, now)) {
            return true;
        }
    
        BlockPos placeTarget;
        synchronized (host.lock()) {
            placeTarget = null;
        }
        if (primitiveStillRequiresPlace(world, plannedPrimitive)) {
            placeTarget = plannedPrimitive.placeTarget();
        }
        boolean committedWaterPlace = placeTarget != null
            && isCommittedWaterPlaceState(world, player, playerFootPos, waypoint, placeTarget);
        if (host.allowBlockPlacing()
            && placeTarget != null
            && (committedWaterPlace
                || primitiveStillRequiresPlace(world, plannedPrimitive))) {
            boolean placed = tryPlaceSupportBlock(client, world, player, placeTarget, now, committedWaterPlace);
            if (placed) {
                host.noteControllerActivity(now);
            } else if (hasTerminalPlacementFailure(placeTarget)) {
                pathPlanner.rememberFailedPlace(playerFootPos, placeTarget, now);
                synchronized (host.lock()) {
                    navigationState.lastReplanReason = "place action failed";
                    navigationState.lastStuckReason = executionState.lastPlaceResult + " at " + host.formatDebugPos(placeTarget);
                }
            }
            return placed;
        }
    
        synchronized (host.lock()) {
            executionState.activeBreakTarget = null;
        }
        return false;
    }
    
    boolean handleCommittedMiningMovement(
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
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
            miningTarget = executionState.controllerTarget != null ? executionState.controllerTarget : executionState.activeBreakTarget;
            miningUntilMs = executionState.controllerUntilMs;
        }
    
        boolean requiresCommittedMining = plannedPrimitive != null
            && (plannedPrimitive.requiresBreak() || plannedPrimitive.isMineAscent());
        boolean requiresCommittedPlacement = plannedPrimitive != null && plannedPrimitive.requiresPlace();
        // A mined-ascent edge is only executable from the immediately lower
        // step. If the player falls away from it, holding the old committed
        // route turns the controller into an endless jump attempt toward an
        // unreachable waypoint. Invalidate that edge and let the planner find
        // a fresh route, including a normal pillar sequence when needed.
        if (plannedPrimitive != null
            && plannedPrimitive.isMineAscent()
            && !primitiveStillRequiresBreak(world, plannedPrimitive)
            && playerFootPos.getY() < waypoint.getY() - 1) {
            pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
            // This is not a recoverable detour: the route's vertical state is
            // invalid. Do not hand it to generic recovery, which may retain
            // an equivalent prefix and thereby resurrect this ascent.
            host.redirectCurrentPath(
                playerFootPos,
                waypoint,
                currentPos,
                now,
                "mined ascent lost footing",
                "discarded stale ascent after fall"
            );
            return true;
        }
        MiningProgress miningProgress = resolveCommittedMiningProgress(world, playerFootPos, waypoint, plannedPrimitive);
        // A combined break-and-place primitive must not advance/return after
        // its excavation half completes.  Its support target is still air at
        // that point, so recommitting the same path index forever starves the
        // placement interaction and leaves the player beneath the goal.
        // Fall through to the placement phase for those primitives instead.
        if (miningProgress.completed() && !requiresCommittedPlacement) {
            synchronized (host.lock()) {
                boolean terminalMinedAscent = miningProgress.minedAscent()
                    // Only the absence of a next step is terminal.  Advancing to
                    // the final index still has to execute that final primitive.
                    && miningProgress.resumeIndex() == navigationState.pathIndex;
                if (terminalMinedAscent) {
                    // A partial excavation route can legitimately end at the last
                    // newly opened stair.  Do not leave its completed MINE_ASCEND
                    // primitive active forever just because there is no next path
                    // index; the next tick must plan from the player's real position.
                    navigationState.currentPath = List.of();
                    navigationState.currentPlan = List.of();
                    navigationState.candidatePaths = List.of();
                    navigationState.candidatePathsVisibleUntilMs = 0L;
                    navigationState.activeWaypoint = null;
                    navigationState.routeCommitUntilMs = 0L;
                    navigationState.lastReplanReason = "terminal mined ascent cleared";
                    navigationState.lastStuckReason = "replanning from opened stair";
                    navigationState.lastAdvanceDecision = "replan:terminal_mined_ascent";
                    executionState.activePlannedPrimitive = null;
                    executionState.controllerTarget = null;
                    executionState.controllerUntilMs = 0L;
                    host.appendDebugEventLocked("terminal mined ascent cleared at " + host.formatDebugPos(playerFootPos));
                } else if (commitPathIndexLocked(world, miningProgress.resumeIndex(), false, now, "advance:mining_complete")) {
                    navigationState.lastReplanReason = miningProgress.minedAscent()
                        ? "mined ascent cleared"
                        : "break route step cleared";
                    navigationState.lastStuckReason = miningProgress.minedAscent()
                        ? "advance into mined ascent"
                        : "advance after mining";
                    executionState.controllerUntilMs = Math.max(executionState.controllerUntilMs, now + 350L);
                }
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        PlacementProgress placementProgress = resolveCommittedPlacementProgress(world, playerFootPos, waypoint, plannedPrimitive);
        if (placementProgress.completed()) {
            synchronized (host.lock()) {
                if (commitPathIndexLocked(world, placementProgress.resumeIndex(), false, now, "advance:placement_complete")) {
                    navigationState.lastReplanReason = "support ready";
                    navigationState.lastStuckReason = "advance after placement";
                    executionState.controllerUntilMs = Math.max(executionState.controllerUntilMs, now + 250L);
                }
            }
            return false;
        }
        if (handleWaypointBlockInteraction(client, world, player, playerFootPos, waypoint, now)) {
            boolean stalledMinedAscent = plannedPrimitive != null
                && plannedPrimitive.isMineAscent()
                && host.shouldRedirectController(now, host.distanceToControllerTargetSq(world, player, waypoint));
            if (stalledMinedAscent) {
                releaseMovementKeys(client);
                pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                host.recoverFromStuck(
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
            synchronized (host.lock()) {
                if (requiresCommittedMining || requiresCommittedPlacement) {
                    navigationState.lastReplanReason = "committed mine";
                    navigationState.lastStuckReason = requiresCommittedMining ? "mining route step" : "placement route step";
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
        boolean placementFailed = requiresCommittedPlacement && hasTerminalPlacementFailure(fallbackTarget);
        if (placementFailed) {
            pathPlanner.rememberFailedPlace(playerFootPos, fallbackTarget, now);
            host.recoverFromStuck(
                client,
                world,
                playerFootPos,
                waypoint,
                target,
                currentPos,
                now,
                "place verification failed",
                "placement " + executionState.lastPlaceResult
            );
            return true;
        }
        if (!timedOut && !targetGone) {
            host.noteControllerActivity(now);
            return false;
        }
    
        if (requiresCommittedPlacement && !requiresCommittedMining) {
            pathPlanner.rememberFailedPlace(playerFootPos, fallbackTarget, now);
        } else {
            pathPlanner.rememberFailedBreak(playerFootPos, fallbackTarget, now);
        }
        host.recoverFromStuck(
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
    
    boolean handleCommittedMiningInteraction(
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
        if (host.allowBlockBreaking()) {
            MiningTargetState targetState = resolveCommittedMiningTargetState(world, playerFootPos, waypoint, plannedPrimitive);
            BlockPos breakTarget = targetState.target();
            if (breakTarget != null
                && shouldBreakForWaypoint(playerFootPos, waypoint, breakTarget)
                && continueBreakingRequiredTarget(client, player, breakTarget, targetState.requiredTargets(), now)) {
                synchronized (host.lock()) {
                    navigationState.lastReplanReason = targetState.currentlyActive()
                        ? "continue committed mine"
                        : "advance mining target";
                    navigationState.lastStuckReason = "mining target " + host.formatDebugPos(breakTarget);
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
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
                executionState.activeMiningAscentPhase = MiningAscentPhase.JUMP;
                executionState.lastJumpAtMs = now;
                executionState.committedJumpWaypoint = waypoint.immutable();
                executionState.committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                navigationState.lastReplanReason = "mined ascent jump";
                navigationState.lastStuckReason = "airborne";
            }
            host.noteControllerActivity(now);
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
                synchronized (host.lock()) {
                    executionState.activeBreakTarget = null;
                    executionState.activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
                    navigationState.lastReplanReason = "mined ascent jump blocked";
                    navigationState.lastStuckReason = "waiting for clear jump arc";
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
                    synchronized (host.lock()) {
                        if (now - executionState.lastMiningJumpGateLogAtMs >= 250L) {
                            host.appendDebugEventLocked(
                                "miningAdvanceJumpGate player=" + host.formatDebugPos(playerFootPos)
                                    + " waypoint=" + host.formatDebugPos(waypoint)
                                    + " moveTarget=" + host.formatDebugPos(moveTarget)
                                    + " advanceBlock=" + host.formatDebugPos(advanceBlock)
                                    + " onGround=" + player.onGround()
                                    + " horizontalDistance=" + (((double) Math.round(horizontalDistance * 100.0D)) / 100.0D)
                                    + " jumpYawError=" + (((double) Math.round(jumpYawError * 100.0D)) / 100.0D)
                                    + " maxJumpYawError=" + JUMP_YAW_ALIGNMENT_DEGREES
                                    + " canAttempt=" + pathPlanner.canAttemptMiningAdvanceJump(world, playerFootPos, waypoint)
                            );
                            executionState.lastMiningJumpGateLogAtMs = now;
                        }
                    }
                }
                if (canHop) {
                    player.jumpFromGround();
                    synchronized (host.lock()) {
                        executionState.activeBreakTarget = null;
                        executionState.activeMiningAscentPhase = MiningAscentPhase.JUMP;
                            executionState.lastJumpAtMs = now;
                            executionState.committedJumpWaypoint = moveTarget.immutable();
                            executionState.committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                            navigationState.lastReplanReason = "mined ascent advance jump";
                            navigationState.lastStuckReason = "jumping onto mined step";
                        }
                        host.noteControllerActivity(now);
                        return true;
                    }
                }
            }
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
                executionState.activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
                navigationState.lastReplanReason = jumpOpportunity ? "stage mined ascent jump" : "mined ascent advance";
                navigationState.lastStuckReason = jumpOpportunity ? "staging mined step jump" : "advancing into mined step";
            }
            host.noteControllerProgress(now, horizontalDistance * horizontalDistance);
            return true;
        }
        return false;
    }
    
    PlacementTargetState resolveCommittedPlacementTargetState(
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
    
    PlacementProgress resolveCommittedPlacementProgress(
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
    
    int resolveCommittedPlacementResumeIndexLocked(BlockPos playerFootPos, BlockPos waypoint) {
        if (navigationState.currentPath.isEmpty()) {
            return -1;
        }
        int boundedIndex = Math.max(0, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
        int currentIndex = waypoint != null ? navigationState.currentPath.indexOf(waypoint) : -1;
        if (currentIndex >= 0) {
            boundedIndex = currentIndex;
        }
        int nextIndex = Math.min(navigationState.currentPath.size() - 1, boundedIndex + 1);
        return navigationState.currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
    }
    
    boolean isPlacementTargetSatisfied(Level world, PlannedPrimitive plannedPrimitive, BlockPos controllerTarget) {
        if (plannedPrimitive != null && plannedPrimitive.placeTarget() != null) {
            return !primitiveStillRequiresPlace(world, plannedPrimitive);
        }
        return controllerTarget != null && pathPlanner.hasCollision(world, controllerTarget);
    }
    
    MiningProgress resolveCommittedMiningProgress(
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
            int currentIndex = navigationState.currentPath.isEmpty() ? -1 : Math.max(0, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
            if (waypoint != null) {
                int waypointIndex = navigationState.currentPath.indexOf(waypoint);
                if (waypointIndex >= 0) {
                    currentIndex = waypointIndex;
                }
            }
            // Reaching the final mined step has no next index.  It is still a
            // completed primitive when the player is physically on that step so
            // the caller can discard this partial route and plan the continuation.
            boolean standingOnTerminalMinedStep = resumeIndex == currentIndex
                && playerFootPos.equals(waypoint);
            if (resumeIndex <= currentIndex && !standingOnTerminalMinedStep) {
                return MiningProgress.incomplete();
            }
        }
        return new MiningProgress(true, resumeIndex, plannedPrimitive.isMineAscent());
    }
    
    MiningTargetState resolveCommittedMiningTargetState(
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
        synchronized (host.lock()) {
            liveTarget = executionState.activeBreakTarget;
        }
        if (liveTarget != null
            && (!requiredTargets.contains(liveTarget) || !pathPlanner.isBreakableForNavigator(world, liveTarget))) {
            synchronized (host.lock()) {
                if (liveTarget.equals(executionState.activeBreakTarget)) {
                    executionState.activeBreakTarget = null;
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
    
    MiningAscentPhase resolveMiningAscentPhase(
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
            synchronized (host.lock()) {
                executionState.activeMiningAscentPhase = MiningAscentPhase.CLEARANCE;
            }
            return MiningAscentPhase.CLEARANCE;
        }
        BlockPos advanceBlock = pathPlanner.resolveMinedAscentAdvanceBlock(playerFootPos, waypoint);
        if (advanceBlock != null
            && pathPlanner.horizontalDistanceSq(playerFootPos, advanceBlock) > WAYPOINT_REACHED_DISTANCE_SQ
            && Math.abs(playerFootPos.getY() - advanceBlock.getY()) <= 1) {
            synchronized (host.lock()) {
                executionState.activeMiningAscentPhase = MiningAscentPhase.ADVANCE;
            }
            return MiningAscentPhase.ADVANCE;
        }
        synchronized (host.lock()) {
            executionState.activeMiningAscentPhase = MiningAscentPhase.JUMP;
        }
        return MiningAscentPhase.JUMP;
    }
    
    boolean isMiningAscentPhaseSatisfied(
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
    
    int resolveCommittedMiningResumeIndexLocked(
        BlockPos playerFootPos,
        BlockPos waypoint,
        PlannedPrimitive plannedPrimitive
    ) {
        if (navigationState.currentPath.isEmpty()) {
            return -1;
        }
        int boundedIndex = Math.max(0, Math.min(navigationState.pathIndex, navigationState.currentPath.size() - 1));
        int currentIndex = waypoint != null ? navigationState.currentPath.indexOf(waypoint) : -1;
        if (currentIndex >= 0) {
            boundedIndex = currentIndex;
        }
        if (plannedPrimitive.isMineAscent()) {
            double stairDistanceSq = pathPlanner.horizontalDistanceSq(playerFootPos, waypoint);
            boolean reachedByHeight = playerFootPos.getY() >= waypoint.getY() - 1;
            boolean reachedCurrentStep = reachedByHeight && stairDistanceSq <= WAYPOINT_REACHED_DISTANCE_SQ;
            if (reachedCurrentStep) {
                int nextIndex = Math.min(navigationState.currentPath.size() - 1, boundedIndex + 1);
                host.appendDebugEventLocked(
                    "miningResume currentIndex=" + boundedIndex
                        + " nextIndex=" + nextIndex
                        + " player=" + host.formatDebugPos(playerFootPos)
                        + " waypoint=" + host.formatDebugPos(waypoint)
                        + " nextWaypoint=" + host.formatDebugPos(navigationState.currentPath.get(nextIndex))
                        + " reachedByHeight=" + reachedByHeight
                        + " stairDistanceSq=" + (((double) Math.round(stairDistanceSq * 100.0D)) / 100.0D)
                        + " reachedCurrentStep=true"
                );
                return navigationState.currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
            }
            long now = System.currentTimeMillis();
            if (now - executionState.lastMiningResumeLogAtMs >= 250L) {
                host.appendDebugEventLocked(
                    "miningResume currentIndex=" + boundedIndex
                        + " nextIndex=hold"
                        + " player=" + host.formatDebugPos(playerFootPos)
                        + " waypoint=" + host.formatDebugPos(waypoint)
                        + " reachedByHeight=" + reachedByHeight
                        + " stairDistanceSq=" + (((double) Math.round(stairDistanceSq * 100.0D)) / 100.0D)
                        + " reachedCurrentStep=false"
                );
                executionState.lastMiningResumeLogAtMs = now;
            }
            return boundedIndex;
        }
        int nextIndex = Math.min(navigationState.currentPath.size() - 1, boundedIndex + 1);
        return navigationState.currentPath.get(nextIndex) != null ? nextIndex : boundedIndex;
    }
    
    boolean handleJumpRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        PlannedPrimitive plannedPrimitive;
        BlockPos recoveryTarget;
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
            recoveryTarget = executionState.controllerTarget != null ? executionState.controllerTarget : waypoint;
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
    
    void invalidateJumpRecovery(BlockPos playerFootPos, BlockPos recoveryTarget, long now, String replanReason, String stuckReason) {
        host.rememberFailedRedirectWindow(playerFootPos, recoveryTarget, now);
        synchronized (host.lock()) {
            executionState.controllerMode = ControllerMode.FOLLOW_PATH;
            executionState.controllerTarget = null;
            executionState.controllerUntilMs = 0L;
            executionState.committedJumpWaypoint = null;
            executionState.committedJumpUntilMs = 0L;
            executionState.activeBreakTarget = null;
            navigationState.currentPath = List.of();
            navigationState.currentPlan = List.of();
            navigationState.candidatePaths = List.of();
            navigationState.candidatePathsVisibleUntilMs = 0L;
            navigationState.activeWaypoint = null;
            navigationState.committedPathGoalPos = null;
            navigationState.pathIndex = 0;
            navigationState.furthestVisitedPathIndex = 0;
            executionState.plannedBreakTargets = List.of();
            navigationState.lastPlanAtMs = 0L;
            navigationState.routeCommitUntilMs = 0L;
            navigationState.lastLocalRecoveryAtMs = 0L;
            navigationState.localRecoveryAttempts = 0;
            navigationState.bestRouteProgressScore = Integer.MIN_VALUE;
            navigationState.lastReplanReason = replanReason;
            navigationState.lastStuckReason = stuckReason;
            navigationState.lastMovementAtMs = now;
            navigationState.lastMovementSamplePos = playerFootPos != null ? Vec3.atCenterOf(playerFootPos) : Vec3.ZERO;
            navigationState.lastDistanceCheckpointAtMs = now;
        }
    }
    
    boolean handleBreakRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        if (handleWaypointBlockInteraction(client, world, player, playerFootPos, waypoint, now)) {
            synchronized (host.lock()) {
                navigationState.lastReplanReason = "recover break";
                navigationState.lastStuckReason = "recovering break step";
            }
            return true;
        }
        return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_BREAK, "recover break jump", "recover break move");
    }
    
    boolean handlePillarRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        boolean allowPillarRecovery;
        synchronized (host.lock()) {
            allowPillarRecovery = isPillarPrimitive(executionState.activePlannedPrimitive) || !executionState.committedEscape.isEmpty();
        }
        if (allowPillarRecovery && handlePillaring(client, world, player, playerFootPos, waypoint, now)) {
            synchronized (host.lock()) {
                navigationState.lastReplanReason = "recover pillar";
                navigationState.lastStuckReason = "recovering pillar step";
            }
            return true;
        }
        return handleRecoveryMovement(client, world, player, playerFootPos, waypoint, now, ControllerMode.RECOVER_PILLAR, "recover pillar jump", "recover pillar move");
    }
    
    boolean handleEscapeRecoveryMovement(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos playerFootPos,
        BlockPos waypoint,
        long now
    ) {
        return handleTrappedSpaceRecovery(client, world, player, playerFootPos, waypoint, now);
    }
    
    boolean handleRecoveryMovement(
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
        synchronized (host.lock()) {
            recoveryTarget = executionState.controllerTarget != null ? executionState.controllerTarget : waypoint;
        }
        if (recoveryTarget == null) {
            return false;
        }
        if (pathPlanner.horizontalDistanceSq(playerFootPos, recoveryTarget) <= 0.64D && Math.abs(playerFootPos.getY() - recoveryTarget.getY()) <= 1) {
            synchronized (host.lock()) {
                if (executionState.controllerMode == recoveryMode) {
                    executionState.controllerMode = ControllerMode.FOLLOW_PATH;
                    executionState.controllerTarget = null;
                    executionState.controllerUntilMs = 0L;
                }
            }
            return false;
        }
        if (!host.isWaypointActionable(world, recoveryTarget) || !host.isPlayerNearPath(playerFootPos)) {
            synchronized (host.lock()) {
                if (executionState.controllerMode == recoveryMode) {
                    executionState.controllerMode = ControllerMode.FOLLOW_PATH;
                    executionState.controllerTarget = null;
                    executionState.controllerUntilMs = 0L;
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
                synchronized (host.lock()) {
                    executionState.lastJumpAtMs = now;
                    executionState.committedJumpWaypoint = jumpTarget.immutable();
                    executionState.committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    executionState.controllerMode = ControllerMode.COMMIT_JUMP;
                    executionState.controllerTarget = jumpTarget.immutable();
                    executionState.controllerUntilMs = executionState.committedJumpUntilMs;
                    navigationState.lastReplanReason = jumpReplanReason;
                    navigationState.lastStuckReason = "recovering to path";
                }
                host.noteControllerActivity(now);
                return true;
            }
        }
    
        synchronized (host.lock()) {
            navigationState.lastReplanReason = moveReplanReason;
            navigationState.lastStuckReason = blocked ? "recover blocked" : "recovering to path";
        }
        if (blocked) {
            long blockedRecoveryMs;
            synchronized (host.lock()) {
                blockedRecoveryMs = now - executionState.controllerEnteredAtMs;
            }
            if (blockedRecoveryMs > 900L) {
                return false;
            }
        }
        if (!blocked) {
            host.noteControllerActivity(now);
        }
        return true;
    }
    
    boolean handleDirectFinalApproach(
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
        synchronized (host.lock()) {
            boolean enteringDirectApproach = !target.equals(executionState.controllerTarget)
                || !"direct final approach".equals(navigationState.lastReplanReason);
            if (enteringDirectApproach) {
                executionState.controllerTarget = target.immutable();
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = directDistanceSq;
            } else if (directDistanceSq + 0.01D < executionState.controllerBestDistanceSq) {
                executionState.controllerProgressAtMs = now;
                executionState.controllerBestDistanceSq = directDistanceSq;
            } else if (now - executionState.controllerProgressAtMs > 1800L) {
                navigationState.lastStuckReason = "direct final approach stalled";
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
    
        synchronized (host.lock()) {
            navigationState.activeWaypoint = target.immutable();
            executionState.controllerMode = ControllerMode.FOLLOW_PATH;
            executionState.controllerTarget = target.immutable();
            navigationState.lastReplanReason = "direct final approach";
            navigationState.lastStuckReason = "centering on goal";
        }
        return true;
    }
    
    boolean handleFollowPathSegment(
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
            host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "primitive waypoint mismatch", "desynced step");
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
        host.updateFollowSegment(segmentType, segmentTarget, waypointHorizontalDistanceSq, now);
    
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
        boolean pillarStep = host.shouldUsePillarStep(world, playerFootPos, waypoint, plannedPrimitive, now);
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
        synchronized (host.lock()) {
            liveBreakTarget = executionState.activeBreakTarget;
            nearFinalGoal = host.targetPos() != null
                && pathPlanner.horizontalDistanceSq(playerFootPos, host.targetPos()) <= 4.0D
                && Math.abs(playerFootPos.getY() - host.targetPos().getY()) <= 1;
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
            && executionState.committedJumpWaypoint == null
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
            host.noteControllerActivity(now);
        }
        boolean jumpExecutionLocked = host.isJumpExecutionLocked(now, plannedPrimitive);
        boolean routeStabilizing;
        boolean routeCommitActive;
        synchronized (host.lock()) {
            routeStabilizing = host.isRouteStabilizingLocked(playerFootPos, now);
            routeCommitActive = now < navigationState.routeCommitUntilMs;
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
            host.noteControllerActivity(now);
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
    
        synchronized (host.lock()) {
            if (distanceSq + PROGRESS_EPSILON_SQ < navigationState.bestDistanceSq) {
                navigationState.bestDistanceSq = distanceSq;
                navigationState.lastProgressAtMs = now;
            }
        }
        host.noteControllerProgress(now, waypointHorizontalDistanceSq);
    
        long millisSinceProgress;
        long millisSinceMovement;
        long millisSinceDistanceChange;
        boolean busyExcavating;
        synchronized (host.lock()) {
            millisSinceProgress = now - navigationState.lastProgressAtMs;
            millisSinceMovement = now - navigationState.lastMovementAtMs;
            millisSinceDistanceChange = now - navigationState.lastDistanceCheckpointAtMs;
            boolean activeEscapeController = executionState.controllerMode == ControllerMode.ESCAPE_HOLE
                || executionState.controllerMode == ControllerMode.RECOVER_ESCAPE
                || executionState.controllerMode == ControllerMode.PILLAR
                || executionState.controllerMode == ControllerMode.RECOVER_PILLAR;
            busyExcavating = activeEscapeController
                && (host.hasCommittedEscapeWorkLocked(now) || host.isActiveEscapeBreakTargetLocked());
        }
    
        long millisSinceJump;
        boolean hasCommittedJump;
        synchronized (host.lock()) {
            millisSinceJump = now - executionState.lastJumpAtMs;
            hasCommittedJump = executionState.committedJumpWaypoint != null;
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
            synchronized (host.lock()) {
                if (waypoint.equals(executionState.lastJumpAttemptWaypoint)) {
                    jumpAttemptsAtWaypoint = executionState.repeatedJumpAttempts;
                } else {
                    executionState.lastJumpAttemptWaypoint = waypoint.immutable();
                    executionState.repeatedJumpAttempts = 0;
                    jumpAttemptsAtWaypoint = 0;
                }
            }
            if (jumpAttemptsAtWaypoint >= 3) {
                releaseMovementKeys(client);
                pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "jump retry limit", "repeated jump failure");
                synchronized (host.lock()) {
                    executionState.lastJumpAtMs = now;
                    executionState.repeatedJumpAttempts = 0;
                    executionState.lastJumpAttemptWaypoint = null;
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
                synchronized (host.lock()) {
                    executionState.lastJumpAtMs = now;
                    executionState.committedJumpWaypoint = waypoint.immutable();
                    executionState.committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    executionState.lastJumpAttemptWaypoint = waypoint.immutable();
                    executionState.repeatedJumpAttempts++;
                }
            } else {
                releaseMovementKeys(client);
                if (miningAdvanceJumpStep) {
                    pathPlanner.rememberFailedBreak(playerFootPos, waypoint, now);
                    pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                } else {
                    pathPlanner.rememberFailedJump(playerFootPos, waypoint, now);
                }
                host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "blocked jump", "ceiling blocked");
                synchronized (host.lock()) {
                    executionState.lastJumpAtMs = now;
                    executionState.lastJumpAttemptWaypoint = waypoint.immutable();
                    executionState.repeatedJumpAttempts++;
                }
                return true;
            }
        }
    
        long segmentIdleMs = host.followSegmentIdleMs(now);
        if (segmentType == FollowSegmentType.GROUND) {
            boolean wallPushStall = !busyExcavating
                && !jumpExecutionLocked
                && !routeStabilizing
                && player.onGround()
                && !breakRequiredStep
                && !placeRequiredStep
                && !interactableStep
                && executionState.committedJumpWaypoint == null
                && !miningAdvanceJumpStep
                && (forwardVelocity > 0.02D || blockedTowardWaypoint)
                && waypointHorizontalDistance > 0.6D
                && blockedTowardWaypoint
                && (segmentIdleMs > WALL_PUSH_REDIRECT_MS || millisSinceMovement > WALL_PUSH_REDIRECT_MS);
            if (wallPushStall) {
                if (simpleMovementStep && routeCommitActive) {
                    host.noteControllerActivity(now);
                    return false;
                }
                releaseMovementKeys(client);
                if (simpleMovementStep) {
                    host.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment wall redirect", "front blocked");
                } else {
                    host.rewindCurrentPathIndex(playerFootPos, waypoint);
                    host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment wall redirect", "front blocked");
                }
                return true;
            }
        }
    
        if (!jumpExecutionLocked && !routeStabilizing && !miningAdvanceJumpStep && millisSinceDistanceChange > DISTANCE_STALL_REDIRECT_MS) {
            if (simpleMovementStep && routeCommitActive) {
                host.noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (simpleMovementStep) {
                host.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "distance stall redirect", "goal distance stalled");
            } else {
                host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "distance stall redirect", "goal distance stalled");
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
                host.noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (simpleMovementStep && segmentType == FollowSegmentType.GROUND) {
                host.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment redirect", segmentType.name().toLowerCase());
            } else {
                host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment redirect", segmentType.name().toLowerCase());
            }
            return true;
        }
    
        if (((!busyExcavating && millisSinceProgress > STUCK_TIMEOUT_MS) || (busyExcavating && millisSinceProgress > STUCK_TIMEOUT_MS * 2L))
            && !routeStabilizing
            && !miningAdvanceJumpStep) {
            if (simpleMovementStep && routeCommitActive && segmentType == FollowSegmentType.GROUND) {
                host.noteControllerActivity(now);
                return false;
            }
            releaseMovementKeys(client);
            if (breakRequiredStep || miningAdvanceStep) {
                pathPlanner.rememberFailedBreak(playerFootPos, waypoint, now);
            } else {
                pathPlanner.rememberFailedMove(playerFootPos, waypoint, now);
            }
            if (simpleMovementStep && segmentType == FollowSegmentType.GROUND) {
                host.redirectCurrentPath(playerFootPos, waypoint, currentPos, now, "segment timeout", "no progress");
            } else {
                host.recoverFromStuck(client, world, playerFootPos, waypoint, target, currentPos, now, "segment timeout", "no progress");
            }
            return true;
        }
    
        return false;
    }
    
    boolean acceptCommittedJumpLandingLocked(Level world, BlockPos playerFootPos, BlockPos jumpTarget) {
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
        if (navigationState.currentPath.isEmpty()) {
            return false;
        }
        int startIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.max(0, navigationState.pathIndex - 1));
        int endIndex = Math.min(navigationState.currentPath.size() - 1, Math.max(navigationState.pathIndex, startIndex) + 5);
        for (int i = startIndex; i <= endIndex; i++) {
            BlockPos step = navigationState.currentPath.get(i);
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
    
    boolean acceptCommittedDropLandingLocked(Level world, BlockPos playerFootPos, BlockPos dropTarget) {
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
        if (navigationState.currentPath.isEmpty()) {
            return false;
        }
        int startIndex = Math.max(navigationState.furthestVisitedPathIndex, Math.max(0, navigationState.pathIndex - 1));
        int endIndex = Math.min(navigationState.currentPath.size() - 1, Math.max(navigationState.pathIndex, startIndex) + 4);
        for (int i = startIndex; i <= endIndex; i++) {
            BlockPos step = navigationState.currentPath.get(i);
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
    
    boolean commitPathIndexLocked(Level world, int newIndex, boolean nearAdvance, long now, String advanceDecision) {
        if (world == null || navigationState.currentPath.isEmpty()) {
            return false;
        }
        if (newIndex < 0 || newIndex >= navigationState.currentPath.size()) {
            return false;
        }
        BlockPos newWaypoint = navigationState.currentPath.get(newIndex);
        if (newWaypoint == null) {
            return false;
        }
        int previousIndex = navigationState.pathIndex;
        BlockPos previousWaypoint = previousIndex >= 0 && previousIndex < navigationState.currentPath.size() ? navigationState.currentPath.get(previousIndex) : null;
        navigationState.pathIndex = newIndex;
        navigationState.furthestVisitedPathIndex = Math.max(navigationState.furthestVisitedPathIndex, navigationState.pathIndex - (nearAdvance ? 1 : 0));
        navigationState.activeWaypoint = newWaypoint;
        executionState.activePlannedPrimitive = host.getPlannedPrimitiveAtIndexLocked(navigationState.pathIndex);
        executionState.plannedBreakTargets = host.buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
        navigationState.lastWaypointAdvanceAtMs = now;
        navigationState.lastProgressAtMs = now;
        navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + ROUTE_COMMIT_MS / 2L);
        navigationState.lastAdvanceDecision = advanceDecision;
        host.appendDebugEventLocked(
            "navigationState.pathIndex " + previousIndex + " -> " + navigationState.pathIndex
                + " reason=" + advanceDecision
                + " oldWaypoint=" + host.formatDebugPos(previousWaypoint)
                + " newWaypoint=" + host.formatDebugPos(newWaypoint)
                + " nearAdvance=" + nearAdvance
        );
        return true;
    }
    
    boolean handlePillaring(
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
        synchronized (host.lock()) {
            plannedPrimitive = executionState.activePlannedPrimitive;
            escapePillar = !executionState.committedEscape.isEmpty();
        }
        if (!isPillarPrimitive(plannedPrimitive) && !escapePillar) {
            return false;
        }
        BlockPos pillarTarget;
        synchronized (host.lock()) {
            pillarTarget = executionState.controllerMode == ControllerMode.PILLAR && executionState.controllerTarget != null
                ? executionState.controllerTarget.immutable()
                : waypoint.immutable();
            if (!executionState.committedEscape.isEmpty()) {
                executionState.committedEscapeTarget = pillarTarget.immutable();
                executionState.committedEscapeUntilMs = Math.max(executionState.committedEscapeUntilMs, now + TRAPPED_RECOVERY_COMMIT_MS);
            }
        }
        BlockPos pillarBase = pillarTarget.below();
        NavigatorPlacementPolicy.Verification pendingPlacement = verifyPendingPlacement(world, pillarBase, now);
        if (pendingPlacement == NavigatorPlacementPolicy.Verification.WAITING) {
            releaseMovementKeys(client);
            applySneakState(client, true);
            synchronized (host.lock()) {
                executionState.activePillarPhase = PillarPhase.PLACE;
                navigationState.lastReplanReason = "pillar placement verifying";
                navigationState.lastStuckReason = "waiting for placed support";
            }
            host.noteControllerActivity(now);
            return true;
        }
        if (pendingPlacement == NavigatorPlacementPolicy.Verification.CONFIRMED) {
            synchronized (host.lock()) {
                executionState.activePillarPhase = PillarPhase.SUPPORT_READY;
                navigationState.lastReplanReason = "pillar support confirmed";
                navigationState.lastStuckReason = "advance on placed support";
            }
            return false;
        }
        if (pendingPlacement == NavigatorPlacementPolicy.Verification.EXHAUSTED) {
            pathPlanner.rememberFailedPillar(playerFootPos, pillarTarget, now);
            synchronized (host.lock()) {
                navigationState.lastReplanReason = "pillar placement failed";
                navigationState.lastStuckReason = "confirmation timeout at " + host.formatDebugPos(pillarBase);
            }
            return false;
        }
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
        synchronized (host.lock()) {
            executionState.activePillarPhase = pillarPhase;
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
            synchronized (host.lock()) {
                executionState.controllerUntilMs = 0L;
                navigationState.lastReplanReason = "pillar support ready";
                navigationState.lastStuckReason = "advance on support";
                executionState.lastPlaceTarget = pillarBase.immutable();
                executionState.lastPlaceResult = "placed";
            }
            return false;
        }
        synchronized (host.lock()) {
            executionState.lastPlaceTarget = pillarBase.immutable();
            executionState.lastPlaceResult = switch (pillarPhase) {
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
                synchronized (host.lock()) {
                    navigationState.lastReplanReason = "pillar place";
                    navigationState.lastStuckReason = "pillaring";
                    executionState.lastJumpAtMs = now;
                }
                host.noteControllerActivity(now);
                return true;
            }
            if (hasTerminalPlacementFailure(pillarBase)) {
                pathPlanner.rememberFailedPillar(playerFootPos, pillarTarget, now);
                synchronized (host.lock()) {
                    navigationState.lastReplanReason = "pillar placement failed";
                    navigationState.lastStuckReason = executionState.lastPlaceResult + " at " + host.formatDebugPos(pillarBase);
                }
                return false;
            }
        }
        if (pillarPhase == PillarPhase.ASCEND && player.onGround()) {
            synchronized (host.lock()) {
                executionState.lastJumpAtMs = now;
                executionState.committedJumpWaypoint = null;
                executionState.committedJumpUntilMs = 0L;
                navigationState.lastReplanReason = "pillar jump";
                navigationState.lastStuckReason = "pillaring";
            }
        }
        host.noteControllerActivity(now);
        return true;
    }
    
    PillarPhase resolvePillarPhase(
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

    NavigatorPlacementPolicy.Verification verifyPendingPlacement(ClientLevel world, BlockPos placePos, long now) {
        if (world == null || placePos == null) {
            return NavigatorPlacementPolicy.Verification.EXHAUSTED;
        }
        synchronized (host.lock()) {
            NavigatorPlacementPolicy.Verification verification = NavigatorPlacementPolicy.verify(
                executionState.pendingPlaceTarget,
                placePos,
                pathPlanner.hasCollision(world, placePos),
                executionState.pendingPlaceAttempts,
                now,
                executionState.pendingPlaceUntilMs
            );
            if (verification == NavigatorPlacementPolicy.Verification.CONFIRMED) {
                executionState.pendingPlaceTarget = null;
                executionState.pendingPlaceUntilMs = 0L;
                executionState.pendingPlaceAttempts = 0;
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "placed";
                return verification;
            }
            if (verification == NavigatorPlacementPolicy.Verification.WAITING) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "verifying";
                return verification;
            }
            if (verification == NavigatorPlacementPolicy.Verification.EXHAUSTED) {
                executionState.pendingPlaceTarget = null;
                executionState.pendingPlaceUntilMs = 0L;
                executionState.pendingPlaceAttempts = 0;
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "confirmation timeout";
                return verification;
            }
            return verification;
        }
    }

    boolean recordPlacementAttempt(ClientLevel world, BlockPos placePos, boolean accepted, long now) {
        boolean placedNow = pathPlanner.hasCollision(world, placePos);
        synchronized (host.lock()) {
            executionState.lastPlaceTarget = placePos.immutable();
            if (placedNow) {
                executionState.pendingPlaceTarget = null;
                executionState.pendingPlaceUntilMs = 0L;
                executionState.pendingPlaceAttempts = 0;
                executionState.lastPlaceResult = "placed";
                return true;
            }
            if (executionState.pendingPlaceTarget == null || !executionState.pendingPlaceTarget.equals(placePos)) {
                executionState.pendingPlaceTarget = placePos.immutable();
                executionState.pendingPlaceAttempts = 0;
            }
            executionState.pendingPlaceAttempts = NavigatorPlacementPolicy.nextAttemptCount(executionState.pendingPlaceAttempts);
            executionState.lastInteractAtMs = now;
            if (!accepted) {
                executionState.pendingPlaceUntilMs = now + 250L;
                executionState.lastPlaceResult = executionState.pendingPlaceAttempts >= NavigatorPlacementPolicy.MAX_ATTEMPTS
                    ? "rejected"
                    : "retrying alternate face";
                return executionState.pendingPlaceAttempts < NavigatorPlacementPolicy.MAX_ATTEMPTS;
            }
            executionState.pendingPlaceUntilMs = now + NavigatorPlacementPolicy.CONFIRM_WINDOW_MS;
            executionState.lastPlaceResult = "verifying";
            return true;
        }
    }

    int placementAttemptIndex(BlockPos placePos) {
        synchronized (host.lock()) {
            return placePos != null && placePos.equals(executionState.pendingPlaceTarget)
                ? executionState.pendingPlaceAttempts
                : 0;
        }
    }

    boolean hasTerminalPlacementFailure(BlockPos placePos) {
        synchronized (host.lock()) {
            return placePos != null
                && placePos.equals(executionState.lastPlaceTarget)
                && ("confirmation timeout".equals(executionState.lastPlaceResult)
                || "rejected".equals(executionState.lastPlaceResult)
                || "no support face".equals(executionState.lastPlaceResult)
                || "no placeable block".equals(executionState.lastPlaceResult));
        }
    }
    
    boolean tryPlacePillarBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now
    ) {
        if (client == null || world == null || player == null || placePos == null || client.gameMode == null) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos != null ? placePos.immutable() : null;
                executionState.lastPlaceResult = "client unavailable";
            }
            return false;
        }
        NavigatorPlacementPolicy.Verification verification = verifyPendingPlacement(world, placePos, now);
        if (verification == NavigatorPlacementPolicy.Verification.CONFIRMED || verification == NavigatorPlacementPolicy.Verification.WAITING) {
            return true;
        }
        if (verification == NavigatorPlacementPolicy.Verification.EXHAUSTED) {
            return false;
        }
        if (clearReplaceablePlacementTarget(client, world, player, placePos, now, false)) {
            return true;
        }
        if (now - executionState.lastInteractAtMs < 250L) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "cooldown";
            }
            return false;
        }
        BlockPos supportPos = placePos.below();
        if (!pathPlanner.hasCollision(world, supportPos)) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "no placeable block";
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
    
        // Pillaring always places onto the top face of the block below the player.
        // Using the incidental camera ray could select a side face of that same block,
        // which asks Minecraft to place beside the column instead and is rejected.
        Vec3 hitPos = new Vec3(
            supportPos.getX() + 0.5D,
            supportPos.getY() + 0.999D,
            supportPos.getZ() + 0.5D
        );
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, supportPos, false);
        InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        boolean accepted = result != null && result.consumesAction();
        if (!accepted) {
            InteractionResult fallback = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            accepted = fallback != null && fallback.consumesAction();
        }
        if (accepted) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        synchronized (host.lock()) {
            ItemStack selectedStack = player.getInventory().getItem(hotbarSlot);
            host.appendDebugEventLocked(
                "pillarPlace item=" + selectedStack.getItem().getDescriptionId()
                    + " slot=" + hotbarSlot
                    + " support=" + host.formatDebugPos(supportPos)
                    + " result=" + (result == null ? "null" : result)
            );
        }

        HotbarSlotSynchronizer.selectHotbarSlot(client, previousSlot);
        applySneakState(client, true);
    
        return recordPlacementAttempt(world, placePos, accepted, now);
    }
    
    boolean handleCommittedJumpMovement(
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
        synchronized (host.lock()) {
            jumpTarget = executionState.committedJumpWaypoint;
            jumpUntilMs = executionState.committedJumpUntilMs;
        }
        if (jumpTarget == null) {
            return false;
        }
        if (player.onGround()) {
            synchronized (host.lock()) {
                if (acceptCommittedJumpLandingLocked(world, playerFootPos, jumpTarget)) {
                    executionState.committedJumpWaypoint = null;
                    executionState.committedJumpUntilMs = 0L;
                    executionState.lastJumpAttemptWaypoint = null;
                    executionState.repeatedJumpAttempts = 0;
                    executionState.controllerMode = ControllerMode.FOLLOW_PATH;
                    executionState.controllerTarget = null;
                    executionState.controllerUntilMs = 0L;
                    navigationState.lastReplanReason = "jump landed";
                    navigationState.lastStuckReason = "jump complete";
                    navigationState.lastProgressAtMs = now;
                    return false;
                }
            }
            if (now > jumpUntilMs) {
                pathPlanner.rememberFailedJump(playerFootPos, jumpTarget, now);
                host.rewindCurrentPathIndex(playerFootPos, jumpTarget);
                host.recoverFromStuck(client, world, playerFootPos, jumpTarget, host.targetPos(), Vec3.atCenterOf(playerFootPos), now, "jump redirect", "missed jump");
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
        synchronized (host.lock()) {
            navigationState.lastReplanReason = "committed jump";
            navigationState.lastStuckReason = player.onGround() ? "landing jump" : "airborne";
            navigationState.lastProgressAtMs = now;
        }
        host.noteControllerActivity(now);
        return true;
    }
    
    boolean handleCommittedDropMovement(
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
        synchronized (host.lock()) {
            dropTarget = executionState.controllerTarget != null ? executionState.controllerTarget : waypoint;
            dropUntilMs = executionState.controllerUntilMs;
        }
        if (dropTarget == null) {
            return false;
        }
        if (player.onGround()) {
            synchronized (host.lock()) {
                if (acceptCommittedDropLandingLocked(world, playerFootPos, dropTarget)) {
                    executionState.controllerMode = ControllerMode.FOLLOW_PATH;
                    executionState.controllerTarget = null;
                    executionState.controllerUntilMs = 0L;
                    navigationState.lastReplanReason = "drop landed";
                    navigationState.lastStuckReason = "drop complete";
                    navigationState.lastProgressAtMs = now;
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
    
        synchronized (host.lock()) {
            navigationState.lastReplanReason = "committed drop";
            navigationState.lastStuckReason = player.onGround() ? "stepping off ledge" : "airborne descent";
            navigationState.lastProgressAtMs = now;
        }
        host.noteControllerActivity(now);
    
        if (player.onGround() && blocked) {
            pathPlanner.rememberFailedDrop(playerFootPos, dropTarget, now);
            host.recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop blocked", "drop blocked");
            return true;
        }
        if (player.onGround() && now > dropUntilMs) {
            pathPlanner.rememberFailedDrop(playerFootPos, dropTarget, now);
            host.recoverFromStuck(client, world, playerFootPos, dropTarget, target, currentPos, now, "drop redirect", "missed drop");
            return true;
        }
        return true;
    }
    
    boolean handleTrappedSpaceRecovery(
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
            || (!host.allowBlockBreaking() && !host.allowBlockPlacing())) {
            return false;
        }
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            synchronized (host.lock()) {
                executionState.committedEscapeTarget = null;
                executionState.committedEscapeUntilMs = 0L;
                executionState.committedEscape = EscapePlan.empty();
                executionState.committedEscapePrimitiveIndex = 0;
            }
            return false;
        }
        boolean trapped = isTrappedInCrampedSpace(world, playerFootPos, waypoint);
        boolean committed = isCommittedEscapeState(now);
        if (!trapped && committed && canExitTrappedRecovery(world, playerFootPos, waypoint, now)) {
            synchronized (host.lock()) {
                executionState.committedEscapeTarget = null;
                executionState.committedEscapeUntilMs = 0L;
                executionState.committedEscape = EscapePlan.empty();
                executionState.committedEscapePrimitiveIndex = 0;
            }
            return false;
        }
        if (!trapped && !committed) {
            synchronized (host.lock()) {
                executionState.committedEscapeTarget = null;
                executionState.committedEscapeUntilMs = 0L;
                executionState.committedEscape = EscapePlan.empty();
                executionState.committedEscapePrimitiveIndex = 0;
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
            synchronized (host.lock()) {
                millisSinceMovement = now - navigationState.lastMovementAtMs;
                routeTarget = selectCommittedEscapeRouteTarget(world, playerFootPos, now);
            }
            if (trapped && millisSinceMovement > NO_MOVEMENT_REPLAN_MS) {
                synchronized (host.lock()) {
                    if (!executionState.committedEscape.isEmpty()) {
                        pathPlanner.rememberFailedMove(playerFootPos, playerFootPos.relative(executionState.committedEscape.direction()), now);
                    }
                }
                clearExcavationPlan(now, "trapped redirect", "trapped stationary");
                ensureExcavationPlan(world, playerFootPos, waypoint, now);
                breakTarget = selectTrappedSpaceBreakTarget(world, playerFootPos, waypoint, now);
                if (breakTarget != null) {
                    return continueBreakingEscapeBlock(client, world, player, breakTarget, now);
                }
                synchronized (host.lock()) {
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
    
    BlockPos selectVerticalEscapeTarget(Level world, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || playerFootPos == null || waypoint == null || !host.allowBlockPlacing()) {
            return null;
        }
        if (shouldPreferFinalApproachController(world, playerFootPos)) {
            return null;
        }
        boolean trappedContext;
        boolean allowEscapePillar;
        synchronized (host.lock()) {
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
    
    boolean shouldPreferFinalApproachController(Level world, BlockPos playerFootPos) {
        if (world == null || playerFootPos == null) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (host.lock()) {
            activeTarget = host.targetPos();
        }
        if (activeTarget == null || !pathPlanner.isStandable(world, activeTarget)) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(playerFootPos, activeTarget) <= 4.0D
            && Math.abs(playerFootPos.getY() - activeTarget.getY()) <= 1;
    }
    
    void continueCommittedEscapeMovement(
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
                synchronized (host.lock()) {
                    executionState.lastJumpAtMs = now;
                    executionState.committedJumpWaypoint = jumpTarget.immutable();
                    executionState.committedJumpUntilMs = now + JUMP_COMMIT_WINDOW_MS;
                    navigationState.lastReplanReason = "escape primitive jump";
                    navigationState.lastStuckReason = "jumping out";
                }
                host.noteControllerActivity(now);
                return;
            }
        }
    
        synchronized (host.lock()) {
            navigationState.lastProgressAtMs = now;
            navigationState.lastReplanReason = "escape primitive move";
            navigationState.lastStuckReason = blocked ? "escape step blocked" : "following excavation route";
        }
        host.noteControllerActivity(now);
    }
    
    void ensureExcavationPlan(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        synchronized (host.lock()) {
            boolean rebuild = executionState.committedEscape.isEmpty() || executionState.committedEscapePrimitiveIndex >= executionState.committedEscape.primitives().size();
            if (rebuild) {
                ExcavationPlan plan = buildExcavationPlan(world, playerFootPos, waypoint, now);
                if (plan != null) {
                    executionState.committedEscape = plan.escapePlan();
                    executionState.committedEscapePrimitiveIndex = 0;
                    executionState.committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                    navigationState.lastReplanReason = "escape plan";
                    navigationState.lastStuckReason = "committed excavation";
                }
            } else if (!executionState.committedEscape.isEmpty()) {
                executionState.committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
            }
        }
    }
    
    void clearExcavationPlan(long now, String replanReason, String stuckReason) {
        synchronized (host.lock()) {
            executionState.committedEscapeTarget = null;
            executionState.committedEscapeUntilMs = 0L;
            executionState.committedEscape = EscapePlan.empty();
            executionState.committedEscapePrimitiveIndex = 0;
            navigationState.lastReplanReason = replanReason;
            navigationState.lastStuckReason = stuckReason;
            executionState.controllerProgressAtMs = now;
        }
    }
    
    BlockPos selectCommittedEscapeRouteTarget(Level world, BlockPos playerFootPos, long now) {
        synchronized (host.lock()) {
            if (executionState.committedEscape.isEmpty()) {
                return null;
            }
            while (executionState.committedEscapePrimitiveIndex < executionState.committedEscape.primitives().size()) {
                EscapePrimitive primitive = executionState.committedEscape.primitives().get(executionState.committedEscapePrimitiveIndex);
                if (primitive == null || primitive.target() == null) {
                    executionState.committedEscapePrimitiveIndex++;
                    continue;
                }
                BlockPos step = primitive.target();
                if (primitive.type() != EscapePrimitiveType.MOVE) {
                    return null;
                }
                if (pathPlanner.horizontalDistanceSq(playerFootPos, step) <= 0.25D && Math.abs(step.getY() - playerFootPos.getY()) <= 1) {
                    executionState.committedEscapePrimitiveIndex++;
                    continue;
                }
                if (pathPlanner.isFailedNode(step, now) || requiresBreakingForWaypoint(world, step) || pathPlanner.needsPlacedSupport(world, step)) {
                    return null;
                }
                if (host.isWaypointActionable(world, step)) {
                    executionState.committedEscapeTarget = step.immutable();
                    return executionState.committedEscapeTarget;
                }
                return null;
            }
            return null;
        }
    }
    
    boolean isCommittedEscapeState(long now) {
        synchronized (host.lock()) {
            return !executionState.committedEscape.isEmpty() && executionState.committedEscapeUntilMs > now;
        }
    }
    
    boolean isCommittedLocalEscapeChain(long now) {
        synchronized (host.lock()) {
            return !executionState.committedEscape.isEmpty()
                && executionState.committedEscapeUntilMs > now
                && executionState.committedEscapePrimitiveIndex < executionState.committedEscape.primitives().size();
        }
    }
    
    boolean canExitTrappedRecovery(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        if (!pathPlanner.canOccupy(world, playerFootPos.above())) {
            return false;
        }
        return countPhysicalWalkNeighbors(world, playerFootPos) >= 2;
    }
    
    BlockPos selectBreakTarget(Level world, BlockPos playerFootPos, BlockPos waypoint, PlannedPrimitive plannedPrimitive) {
        if (world == null || waypoint == null) {
            return null;
        }
        BlockPos currentBreakTarget = null;
        synchronized (host.lock()) {
            currentBreakTarget = executionState.activeBreakTarget;
        }
        List<BlockPos> breakTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : List.of();
        if (breakTargets == null || breakTargets.isEmpty()) {
            return null;
        }
        synchronized (host.lock()) {
            if (executionState.activeBreakTarget != null && breakTargets.contains(executionState.activeBreakTarget) && pathPlanner.isBreakableForNavigator(world, executionState.activeBreakTarget)) {
                return executionState.activeBreakTarget;
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
    
    BlockPos selectBreakTarget(
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
            synchronized (host.lock()) {
                if (target.equals(executionState.activeBreakTarget)) {
                    executionState.activeBreakTarget = null;
                }
            }
            return null;
        }
        return target;
    }
    
    BlockPos firstPendingBreakTarget(Level world, List<BlockPos> breakTargets) {
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
    
    boolean isPlannedBreakTargetReachable(BlockPos playerFootPos, BlockPos target) {
        if (playerFootPos == null || target == null) {
            return false;
        }
        return pathPlanner.horizontalDistanceSq(playerFootPos, target) <= 9.0D
            && Math.abs(playerFootPos.getY() - target.getY()) <= 3;
    }
    
    boolean isTrappedInCrampedSpace(Level world, BlockPos playerFootPos, BlockPos waypoint) {
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
    
    int countPhysicalWalkNeighbors(Level world, BlockPos playerFootPos) {
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
    
    boolean isPhysicalWalkNeighbor(Level world, BlockPos footPos) {
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
    
    BlockPos selectTrappedSpaceBreakTarget(Level world, BlockPos playerFootPos, BlockPos waypoint, long now) {
        if (world == null || playerFootPos == null || waypoint == null) {
            return null;
        }
    
        synchronized (host.lock()) {
            while (executionState.committedEscapePrimitiveIndex < executionState.committedEscape.primitives().size()) {
                EscapePrimitive primitive = executionState.committedEscape.primitives().get(executionState.committedEscapePrimitiveIndex);
                if (primitive == null || primitive.target() == null) {
                    executionState.committedEscapePrimitiveIndex++;
                    continue;
                }
                if (primitive.type() != EscapePrimitiveType.MINE) {
                    return null;
                }
                BlockPos planned = primitive.target();
                if (!isReachableTrappedBreakTarget(playerFootPos, executionState.committedEscape.direction(), planned)) {
                    return null;
                }
                if (pathPlanner.canOccupy(world, planned)) {
                    executionState.committedEscapePrimitiveIndex++;
                    continue;
                }
                if (pathPlanner.isBreakableForNavigator(world, planned)) {
                    executionState.committedEscapeTarget = planned.immutable();
                    return executionState.committedEscapeTarget;
                }
                return null;
            }
            if (executionState.committedEscapeTarget != null && executionState.committedEscapeUntilMs <= now) {
                executionState.committedEscapeTarget = null;
                executionState.committedEscapeUntilMs = 0L;
            }
        }
        return null;
    }
    
    boolean isReachableTrappedBreakTarget(BlockPos playerFootPos, Direction direction, BlockPos target) {
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
    
    Direction chooseEscapeDirection(Level world, BlockPos current, BlockPos goal, long now) {
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
    
    ExcavationPlan buildExcavationPlan(Level world, BlockPos current, BlockPos goal, long now) {
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
    
    StairEscapePlan buildStairEscapePlan(Level world, BlockPos current, BlockPos goal, Direction direction, long now) {
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
    
    boolean isEscapeLipReached(Level world, BlockPos start, BlockPos cursor, BlockPos goal, long now) {
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
    
    boolean isValidEscapeStepCandidate(Level world, BlockPos from, BlockPos candidate, long now) {
        if (world == null || from == null || candidate == null) {
            return false;
        }
        return pathPlanner.isChunkLoaded(world, candidate)
            && !pathPlanner.isFailedNode(candidate, now)
            && !pathPlanner.isFailedEdge(from, candidate, now)
            && !pathPlanner.isHardDanger(world, candidate)
            && !pathPlanner.needsPlacedSupport(world, candidate);
    }
    
    boolean hasExcavatableThreeHighClearance(Level world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return pathPlanner.isExcavationClearable(world, foot)
            && pathPlanner.isExcavationClearable(world, foot.above())
            && pathPlanner.isExcavationClearable(world, foot.above(2));
    }
    
    void addThreeHighExcavationBreaks(List<EscapePrimitive> plan, Level world, BlockPos foot) {
        if (plan == null || world == null || foot == null) {
            return;
        }
        addOrderedExcavationBreaks(plan, world, List.of(foot, foot.above(), foot.above(2)));
    }
    
    void addAscendingExcavationBreaks(List<EscapePrimitive> plan, Level world, BlockPos from, BlockPos to) {
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
    
    boolean canExcavateEscapeJumpCorridor(Level world, BlockPos from, BlockPos to) {
        return pathPlanner.canExcavateJumpCorridor(world, from, to);
    }
    
    void addOrderedExcavationBreaks(List<EscapePrimitive> plan, Level world, List<BlockPos> candidates) {
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
    
    void addPlannedBreak(List<EscapePrimitive> plan, Level world, BlockPos pos) {
        if (plan == null || world == null || pos == null) {
            return;
        }
        if (!pathPlanner.isBreakableForNavigator(world, pos)) {
            return;
        }
        addEscapePrimitive(plan, EscapePrimitiveType.MINE, pos);
    }
    
    void addEscapePrimitive(List<EscapePrimitive> plan, EscapePrimitiveType type, BlockPos pos) {
        if (plan == null || type == null || pos == null) {
            return;
        }
        EscapePrimitive primitive = new EscapePrimitive(type, pos.immutable());
        if (!plan.contains(primitive)) {
            plan.add(primitive);
        }
    }
    
    boolean hasThreeHighExcavationClearance(Level world, BlockPos foot) {
        if (world == null || foot == null) {
            return false;
        }
        return pathPlanner.canOccupy(world, foot)
            && pathPlanner.canOccupy(world, foot.above())
            && pathPlanner.canOccupy(world, foot.above(2));
    }
    
    Double scoreEscapeDirection(Level world, BlockPos current, BlockPos goal, Direction direction, long now) {
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
                if (!host.allowBlockPlacing() || !pathPlanner.canPlaceSupportAt(world, step.below())) {
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
    
    void addBreakCandidate(List<BlockPos> candidates, BlockPos candidate) {
        if (candidates != null && candidate != null) {
            candidates.add(candidate);
        }
    }
    
    boolean continueBreakingBlock(Minecraft client, LocalPlayer player, BlockPos target, long now) {
        if (client == null || client.gameMode == null || client.level == null || player == null || target == null) {
            return false;
        }
        BlockState targetState = client.level.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            return false;
        }
        BlockPos waypoint;
        PlannedPrimitive plannedPrimitive;
        synchronized (host.lock()) {
            waypoint = navigationState.activeWaypoint;
            plannedPrimitive = executionState.activePlannedPrimitive;
        }
        if (waypoint == null) {
            return false;
        }
        List<BlockPos> requiredTargets = primitiveRequiresBreak(plannedPrimitive)
            ? plannedPrimitive.breakTargets()
            : pathPlanner.getRequiredBreakTargets(client.level, waypoint);
        return continueBreakingRequiredTarget(client, player, target, requiredTargets, now);
    }
    
    boolean continueBreakingRequiredTarget(
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
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        BlockPos pendingTarget = firstPendingBreakTarget(client.level, requiredTargets);
        if (pendingTarget == null || !target.equals(pendingTarget)) {
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        BlockState targetState = client.level.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        BreakTargeting targeting = resolveBreakTargeting(client.level, player, target);
        if (targeting == null) {
            synchronized (host.lock()) {
                executionState.activeBreakTarget = null;
            }
            return false;
        }
        equipBestMiningTool(player, targetState);
        releaseMovementKeys(client);
        applyWaterInteractionStance(client, client.level, player, target);
        lookAtPosition(player, targeting.hitPos());
        Direction face = targeting.face();
        boolean startingNewTarget;
        synchronized (host.lock()) {
            startingNewTarget = executionState.activeBreakTarget == null || !executionState.activeBreakTarget.equals(target);
            executionState.activeBreakTarget = target.immutable();
            if (startingNewTarget) {
                executionState.lastInteractAtMs = now;
            }
        }
        if (startingNewTarget) {
            client.gameMode.startDestroyBlock(target, face);
        }
        client.gameMode.continueDestroyBlock(target, face);
        player.swing(InteractionHand.MAIN_HAND);
        host.noteControllerActivity(now);
        return true;
    }
    
    void syncPathToPillarTarget(ClientLevel world, BlockPos pillarTarget, long now) {
        if (world == null || pillarTarget == null) {
            return;
        }
    
        BlockPos navTarget;
        synchronized (host.lock()) {
            navTarget = host.targetPos();
            if (executionState.controllerMode == ControllerMode.PILLAR
                && executionState.controllerTarget != null
                && pillarTarget.equals(executionState.controllerTarget)
                && !navigationState.currentPath.isEmpty()
                && navigationState.pathIndex >= 0
                && navigationState.pathIndex < navigationState.currentPath.size()
                && pillarTarget.equals(navigationState.currentPath.get(navigationState.pathIndex))
                && navigationState.routeCommitUntilMs > now) {
                navigationState.activeWaypoint = pillarTarget.immutable();
                return;
            }
        }
    
        // A pillar target is a virtual position until the client has actually observed the
        // placed support.  Planning a continuation from that virtual position lets the
        // normal search legally "drop" to the real block below and pillar back up, which
        // produced the stacked green markers and the PILLAR -> DESCEND -> PILLAR loop.
        // Keep the committed route to this single atomic action until the support exists.
        BlockPos pillarBase = pillarTarget.below();
        boolean supportConfirmed = pathPlanner.hasCollision(world, pillarBase);
        List<BlockPos> syncedPath = List.of(pillarTarget.immutable());
        PathComputation continuation = null;
        if (supportConfirmed && navTarget != null) {
            continuation = host.findPath(world, pillarTarget, navTarget);
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
    
        synchronized (host.lock()) {
            navigationState.currentPath = syncedPath;
            navigationState.pathIndex = 0;
            navigationState.furthestVisitedPathIndex = 0;
            navigationState.activeWaypoint = pillarTarget.immutable();
            navigationState.committedPathGoalPos = pillarTarget.immutable();
            executionState.plannedBreakTargets = host.buildPathBreakPlan(world, navigationState.currentPath, navigationState.pathIndex);
            host.rebuildCurrentPlanLocked(world);
            if (!isPillarPrimitive(executionState.activePlannedPrimitive)) {
                executionState.activePlannedPrimitive = host.createPrimitiveSnapshot(world, navigationState.activeWaypoint.below(), navigationState.activeWaypoint, SearchPrimitiveType.PILLAR, PlannedPrimitiveType.PILLAR, List.of(), navigationState.activeWaypoint.below());
            }
            navigationState.lastPlanAtMs = now;
            navigationState.routeCommitUntilMs = Math.max(navigationState.routeCommitUntilMs, now + 1400L);
            navigationState.lastReplanReason = supportConfirmed ? "pillar continuation sync" : "pillar atomic sync";
            host.appendDebugEventLocked(
                "pillarSync target=" + host.formatDebugPos(pillarTarget)
                    + " support=" + supportConfirmed
                    + " continuation=" + (continuation != null && !continuation.path().isEmpty())
            );
            if (continuation != null && !continuation.path().isEmpty()) {
                navigationState.candidatePaths = continuation.candidatePaths();
                navigationState.candidatePathsVisibleUntilMs = now + PATH_DECISION_VISIBILITY_MS;
                host.goalMode(host.shouldTrackResolvedPlanningGoal(navTarget, continuation.resolvedGoalPos(), continuation.goalMode())
                        ? continuation.goalMode()
                        : GoalMode.EXACT);
                navigationState.resolvedGoalPos = host.goalMode() == GoalMode.NEAREST_STANDABLE ? continuation.resolvedGoalPos() : navTarget.immutable();
                navigationState.committedPathGoalPos = continuation.resolvedGoalPos() != null ? continuation.resolvedGoalPos().immutable() : navigationState.resolvedGoalPos;
            }
        }
    }
    
    boolean continueBreakingEscapeBlock(
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
        synchronized (host.lock()) {
            startingNewTarget = executionState.activeBreakTarget == null || !executionState.activeBreakTarget.equals(target);
            executionState.activeBreakTarget = target.immutable();
            if (startingNewTarget) {
                executionState.committedEscapeTarget = target.immutable();
                executionState.committedEscapeUntilMs = now + TRAPPED_RECOVERY_COMMIT_MS;
                executionState.lastInteractAtMs = now;
                navigationState.lastReplanReason = "escape primitive mine";
                navigationState.lastStuckReason = "excavating escape";
            }
        }
        if (startingNewTarget) {
            client.gameMode.startDestroyBlock(target, face);
        }
        client.gameMode.continueDestroyBlock(target, face);
        player.swing(InteractionHand.MAIN_HAND);
        host.noteControllerActivity(now);
        return true;
    }
    
    boolean shouldSuppressMiningNearGoal(Level world, LocalPlayer player, BlockPos playerFootPos, BlockPos waypoint) {
        if (world == null || player == null || playerFootPos == null || waypoint == null) {
            return false;
        }
        BlockPos activeTarget;
        synchronized (host.lock()) {
            activeTarget = host.targetPos();
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
    
    boolean shouldBreakForWaypoint(BlockPos playerFootPos, BlockPos waypoint, BlockPos breakTarget) {
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
    
    boolean requiresBreakingForWaypoint(Level world, BlockPos waypoint) {
        if (world == null || waypoint == null) {
            return false;
        }
        List<BlockPos> breakTargets = pathPlanner.getRequiredBreakTargets(world, waypoint);
        return breakTargets != null && !breakTargets.isEmpty();
    }
    
    boolean shouldPlaceForWaypoint(Level world, BlockPos playerFootPos, BlockPos waypoint) {
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
        synchronized (host.lock()) {
            activeTarget = host.targetPos();
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
    
    boolean isCommittedWaterPlaceState(
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
    
    void equipBestMiningTool(LocalPlayer player, BlockState targetState) {
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
    
    int findBestMiningHotbarSlot(LocalPlayer player, BlockState targetState) {
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
    
    double miningToolScore(ItemStack stack, BlockState targetState) {
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
    
    void lookAtPosition(LocalPlayer player, Vec3 targetPos) {
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
    
    BreakTargeting resolveBreakTargeting(ClientLevel world, LocalPlayer player, BlockPos target) {
        if (world == null || player == null || target == null) {
            return null;
        }
        return resolveBlockTargeting(world, player, player.getEyePosition(), target, blockInteractionReachSquared(player));
    }
    
    BreakTargeting resolveBlockTargeting(
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
    
    boolean canBreakTargetNow(ClientLevel world, LocalPlayer player, BlockPos target) {
        return resolveBreakTargeting(world, player, target) != null;
    }
    
    boolean canInteractWithBlockFromFoot(
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
    
    boolean isBlockShapeWithinReachFromFoot(
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
    
    double blockInteractionReachSquared(LocalPlayer player) {
        double reach = DEFAULT_BLOCK_INTERACTION_REACH;
        if (player != null) {
            reach = Math.max(0.0D, player.blockInteractionRange());
        }
        return reach * reach;
    }
    
    List<Direction> preferredBreakFaces(LocalPlayer player, BlockPos target) {
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
    
    List<Vec3> getBreakAimPoints(ClientLevel world, BlockState targetState, BlockPos target, List<Direction> preferredFaces) {
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
    
    Vec3 getBreakFaceAimPoint(BlockPos target, AABB localBox, Direction face) {
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
    
    Vec3 worldBoxCenter(BlockPos target, AABB localBox) {
        if (target == null || localBox == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
            target.getX() + (localBox.minX + localBox.maxX) * 0.5D,
            target.getY() + (localBox.minY + localBox.maxY) * 0.5D,
            target.getZ() + (localBox.minZ + localBox.maxZ) * 0.5D
        );
    }
    
    double inwardEpsilon(double min, double max) {
        return Math.min(BREAK_AIM_EPSILON, Math.max(0.0D, (max - min) * 0.25D));
    }
    
    BlockHitResult raycastToBreakTarget(
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
    
    boolean tryPlaceSupportBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now
    ) {
        return tryPlaceSupportBlock(client, world, player, placePos, now, false);
    }
    
    boolean tryPlaceSupportBlock(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now,
        boolean preserveMovementState
    ) {
        if (client == null || world == null || player == null || placePos == null || client.gameMode == null) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos != null ? placePos.immutable() : null;
                executionState.lastPlaceResult = "client unavailable";
            }
            return false;
        }
        NavigatorPlacementPolicy.Verification verification = verifyPendingPlacement(world, placePos, now);
        if (verification == NavigatorPlacementPolicy.Verification.CONFIRMED || verification == NavigatorPlacementPolicy.Verification.WAITING) {
            return true;
        }
        if (verification == NavigatorPlacementPolicy.Verification.EXHAUSTED) {
            return false;
        }
        if (clearReplaceablePlacementTarget(client, world, player, placePos, now, preserveMovementState)) {
            return true;
        }
        if (now - executionState.lastInteractAtMs < 250L) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "cooldown";
            }
            return false;
        }
        PlacementTarget placementTarget = findPlacementTarget(world, placePos, placementAttemptIndex(placePos));
        if (placementTarget == null) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "no support face";
            }
            return false;
        }
        int hotbarSlot = ensurePlaceableHotbarSlot(client, player);
        if (hotbarSlot < 0) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "no placeable block";
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
        return recordPlacementAttempt(world, placePos, accepted, now);
    }

    /**
     * Plants do not obstruct a player's collision path, but a client-side place
     * interaction is not guaranteed to replace every such block.  Clear only the
     * exact planned support cell before attempting the placement; do not make
     * plants into ordinary route-mining targets.
     */
    boolean clearReplaceablePlacementTarget(
        Minecraft client,
        ClientLevel world,
        LocalPlayer player,
        BlockPos placePos,
        long now,
        boolean preserveMovementState
    ) {
        BlockState state = world.getBlockState(placePos);
        if (state == null || state.isAir() || !state.canBeReplaced() || !world.getFluidState(placePos).isEmpty()) {
            return false;
        }
        if (!host.allowBlockBreaking()) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "replaceable target requires breaking";
            }
            return false;
        }
        BreakTargeting targeting = resolveBreakTargeting(world, player, placePos);
        if (targeting == null) {
            synchronized (host.lock()) {
                executionState.lastPlaceTarget = placePos.immutable();
                executionState.lastPlaceResult = "cannot target replaceable block";
            }
            return false;
        }
        if (!preserveMovementState) {
            releaseMovementKeys(client);
        }
        lookAtPosition(player, targeting.hitPos());
        boolean startingNewTarget;
        synchronized (host.lock()) {
            startingNewTarget = executionState.activeBreakTarget == null || !executionState.activeBreakTarget.equals(placePos);
            executionState.activeBreakTarget = placePos.immutable();
            executionState.lastPlaceTarget = placePos.immutable();
            executionState.lastPlaceResult = "clearing replaceable placement target";
            if (startingNewTarget) {
                executionState.lastInteractAtMs = now;
            }
        }
        if (startingNewTarget) {
            client.gameMode.startDestroyBlock(placePos, targeting.face());
        }
        client.gameMode.continueDestroyBlock(placePos, targeting.face());
        player.swing(InteractionHand.MAIN_HAND);
        host.noteControllerActivity(now);
        return true;
    }
    
    void applyWaterInteractionStance(Minecraft client, ClientLevel world, LocalPlayer player, BlockPos anchor) {
        if (client == null || world == null || player == null || anchor == null || client.options == null) {
            return;
        }
        boolean inWater = player.isInWater()
            || player.isUnderWater()
            || pathPlanner.isWaterNode(world, host.resolvePlayerFootPos(player))
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
    
    int findPlaceableHotbarSlot(LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (NavigatorPlacementPolicy.isSolidSupportBlock(stack)) {
                return slot;
            }
        }
        return -1;
    }
    
    int findPlaceableMainInventorySlot(LocalPlayer player) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }
        int hotbarSize = net.minecraft.world.entity.player.Inventory.getSelectionSize();
        for (int slot = hotbarSize; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (NavigatorPlacementPolicy.isSolidSupportBlock(stack)) {
                return slot;
            }
        }
        return -1;
    }
    
    int findEmptyHotbarSlot(net.minecraft.world.entity.player.Inventory inventory) {
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
    
    int ensurePlaceableHotbarSlot(Minecraft client, LocalPlayer player) {
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
    
    int moveInventoryStackToHotbar(Minecraft client, LocalPlayer player, int inventorySlot) {
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
        return NavigatorPlacementPolicy.isSolidSupportBlock(hotbarStack) ? targetHotbarSlot : -1;
    }
    
    int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
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
    
    PlacementTarget findPlacementTarget(Level world, BlockPos placePos) {
        return findPlacementTarget(world, placePos, 0);
    }

    PlacementTarget findPlacementTarget(Level world, BlockPos placePos, int attemptIndex) {
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
        List<PlacementTarget> candidates = new ArrayList<>(preferredOrder.length);
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
            candidates.add(new PlacementTarget(support, face, hitPos));
        }
        return candidates.isEmpty() ? null : candidates.get(Math.floorMod(attemptIndex, candidates.size()));
    }
    
    boolean primitiveRequiresBreak(PlannedPrimitive primitive) {
        return primitive != null && primitive.requiresBreak();
    }
    
    boolean primitiveRequiresPlace(PlannedPrimitive primitive) {
        return primitive != null && primitive.requiresPlace();
    }
    
    boolean primitiveStillRequiresBreak(Level world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.breakTargets() == null || primitive.breakTargets().isEmpty()) {
            return false;
        }
        return world == null || firstPendingBreakTarget(world, primitive.breakTargets()) != null;
    }
    
    boolean primitiveStillRequiresPlace(Level world, PlannedPrimitive primitive) {
        if (primitive == null || primitive.placeTarget() == null) {
            return false;
        }
        return world == null || !pathPlanner.hasCollision(world, primitive.placeTarget());
    }
    
    void clearStalePlaceStateIfNeeded(Level world, PlannedPrimitive primitive) {
        if (primitiveStillRequiresPlace(world, primitive)) {
            return;
        }
        synchronized (host.lock()) {
            if ("placed".equals(executionState.lastPlaceResult)
                || "ready".equals(executionState.lastPlaceResult)
                || "centering".equals(executionState.lastPlaceResult)
                || "waiting apex".equals(executionState.lastPlaceResult)) {
                executionState.lastPlaceTarget = null;
                executionState.lastPlaceResult = "none";
            }
            if (executionState.pendingPlaceTarget != null
                && (primitive == null
                || primitive.placeTarget() == null
                || pathPlanner.hasCollision(world, executionState.pendingPlaceTarget))) {
                executionState.pendingPlaceTarget = null;
                executionState.pendingPlaceUntilMs = 0L;
                executionState.pendingPlaceAttempts = 0;
            }
        }
    }
    
    boolean isPillarPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isPillar();
    }
    
    boolean isClimbPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isClimb();
    }
    
    boolean isDescendPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isDescend();
    }
    
    boolean isJumpPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isJump();
    }
    
    boolean isInteractablePrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isInteractable();
    }
    
    boolean isSwimPrimitive(PlannedPrimitive primitive) {
        return primitive != null && primitive.isSwim();
    }
    
    boolean isWaypointPrimitiveAligned(BlockPos waypoint, PlannedPrimitive primitive) {
        if (waypoint == null || primitive == null || primitive.target() == null) {
            return true;
        }
        BlockPos target = primitive.target();
        int dx = Math.abs(target.getX() - waypoint.getX());
        int dy = Math.abs(target.getY() - waypoint.getY());
        int dz = Math.abs(target.getZ() - waypoint.getZ());
        return dx <= 1 && dz <= 1 && dy <= 1;
    }
    
    boolean isActiveEscapePillarPrimitiveLocked() {
        if (executionState.committedEscape.isEmpty()
            || executionState.committedEscapePrimitiveIndex < 0
            || executionState.committedEscapePrimitiveIndex >= executionState.committedEscape.primitives().size()) {
            return false;
        }
        EscapePrimitive primitive = executionState.committedEscape.primitives().get(executionState.committedEscapePrimitiveIndex);
        return primitive != null && primitive.type() == EscapePrimitiveType.PILLAR;
    }
    
}
