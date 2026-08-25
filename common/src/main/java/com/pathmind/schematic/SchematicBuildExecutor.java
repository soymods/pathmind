package com.pathmind.schematic;

import com.pathmind.data.SettingsManager;
import com.pathmind.execution.PathmindNavigator;
import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.PlayerInventoryBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native survival schematic execution. This owns high-level build state only:
 * movement remains exclusively in {@link PathmindNavigator} and each completed
 * world mutation is reconciled through a fresh {@link SchematicPlacementPlanner} plan.
 */
public final class SchematicBuildExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pathmind/SchematicBuild");
    private static final SchematicBuildExecutor INSTANCE = new SchematicBuildExecutor();
    private static final int MIN_SCHEMATIC_PLACEMENT_SPEED = 1;
    private static final int MAX_SCHEMATIC_PLACEMENT_SPEED = 20;
    // A placement packet can take more than one client tick to be reflected in
    // the local world, especially on a remote server.  Do not issue another
    // click while the previous one is still awaiting authoritative state.
    private static final long PLACE_CONFIRM_TIMEOUT_MS = 1_500L;
    private static final long BREAK_TIMEOUT_MS = 12_000L;
    private static final long FLIGHT_TIMEOUT_MS = 30_000L;
    private static final long FLIGHT_STALL_REPLAN_MS = 1_250L;
    private static final double FLIGHT_PROGRESS_DISTANCE_SQ = 0.09D;
    private static final long MINED_DROP_PICKUP_WAIT_MS = 1_200L;
    private static final int MAX_PLACE_ATTEMPTS = 4;

    private SchematicBuildPlan schematic;
    private BlockPos origin;
    private SchematicPlacementPlanner.ConstructionPlan constructionPlan;
    private SchematicPlacementPlanner.ConstructionStep activeStep;
    private SchematicPlacementPlanner.PlacementApproach activeApproach;
    private CompletableFuture<Void> completion;
    private CompletableFuture<Void> navigationFuture;
    private List<BlockPos> flightPath = List.of();
    private int flightPathIndex;
    private boolean creativeFlight;
    private boolean activeScaffold;
    private boolean activeScaffoldCleanup;
    /** True when a creative cleanup should return to construction immediately. */
    private boolean cleanupDuringBuild;
    /** Only blocks confirmed as placed by this build are eligible for cleanup. */
    private final Map<BlockPos, BlockState> temporaryScaffolds = new LinkedHashMap<>();
    /** Approaches which received a click but never produced the desired state. */
    private final Map<BlockPos, Set<ApproachKey>> rejectedApproaches = new LinkedHashMap<>();
    /** Targets this build has already clicked, used for safe state repair. */
    private final Set<BlockPos> attemptedPlacementTargets = new HashSet<>();
    private State state = State.IDLE;
    private long actionStartedAtMs;
    private long nextPlaceAttemptAtMs;
    private long waitUntilMs;
    private int placementAttempts;
    private long lastLiveFlightLogAtMs;
    private Vec3 lastFlightProgressPosition = Vec3.ZERO;
    private long lastFlightProgressAtMs;
    private int flightStallReplans;
    private String lastPlacementOutcome = "no interaction sent";
    private String status = "idle";

    private SchematicBuildExecutor() {
    }

    public static SchematicBuildExecutor getInstance() {
        return INSTANCE;
    }

    /** Shared by command previews so their counts match the live executor. */
    public static SchematicPlacementPlanner.ConflictPolicy configuredConflictPolicy() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        if (Boolean.TRUE.equals(settings.schematicAllowDestructiveRebuild)) {
            return SchematicPlacementPlanner.ConflictPolicy.DESTRUCTIVE_REBUILD;
        }
        if (Boolean.TRUE.equals(settings.schematicReplaceMatchingBlocks)) {
            return SchematicPlacementPlanner.ConflictPolicy.REPLACE_MATCHING_BLOCK;
        }
        return SchematicPlacementPlanner.ConflictPolicy.KEEP_EXISTING;
    }

    public synchronized boolean start(Minecraft client, SchematicBuildPlan plan, BlockPos buildOrigin, CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            status = "Minecraft must be in a loaded world before starting a schematic build.";
            return false;
        }
        if (plan == null || buildOrigin == null || future == null) {
            status = "A schematic and build origin are required.";
            return false;
        }
        stopInternal(false, "replaced by a new build");
        schematic = plan;
        origin = buildOrigin.immutable();
        completion = future;
        constructionPlan = SchematicPlacementPlanner.plan(client.level, plan, origin, client.player.blockPosition(), conflictPolicy());
        SchematicPreview.showBuild(plan, origin);
        creativeFlight = client.player.getAbilities().mayfly && client.player.getAbilities().instabuild;
        if (creativeFlight && !client.player.getAbilities().flying) {
            client.player.getAbilities().flying = true;
            client.player.onUpdateAbilities();
        }
        activeStep = null;
        activeApproach = null;
        navigationFuture = null;
        placementAttempts = 0;
        lastPlacementOutcome = "no interaction sent";
        rejectedApproaches.clear();
        attemptedPlacementTargets.clear();
        temporaryScaffolds.clear();
        activeScaffoldCleanup = false;
        actionStartedAtMs = System.currentTimeMillis();
        status = "planning";
        state = State.PLANNING;
        LOGGER.info("build started source={} origin={} creative={}", plan.source(), format(origin), creativeFlight);
        liveLog("start source=" + plan.source().getFileName() + " origin=" + format(origin)
            + " creative=" + creativeFlight + " steps=" + constructionPlan.steps().size());
        return true;
    }

    public synchronized boolean isActive() {
        return state != State.IDLE && state != State.COMPLETED && state != State.FAILED && state != State.PAUSED;
    }

    public synchronized String status() {
        return status;
    }

    /** A compact, current inventory report suitable for !build status and logs. */
    public synchronized String materialStatus(Minecraft client) {
        if (schematic == null || constructionPlan == null || client == null || client.player == null) {
            return "Materials: no active build.";
        }
        if (creativeFlight) {
            return "Materials: creative mode supplies required blocks.";
        }
        Map<Item, Integer> required = new LinkedHashMap<>();
        for (SchematicPlacementPlanner.ConstructionStep step : constructionPlan.steps()) {
            if (step.action() == SchematicPlacementPlanner.StepAction.SKIP
                || step.action() == SchematicPlacementPlanner.StepAction.CONFLICT
                || isAutomaticMultipartHalf(step.desired().state())) {
                continue;
            }
            required.merge(step.desired().state().getBlock().asItem(), 1, Integer::sum);
        }
        Map<Item, Integer> available = inventoryCounts(client.player.getInventory());
        List<String> missing = new ArrayList<>();
        int missingTotal = 0;
        int missingKinds = 0;
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int deficit = Math.max(0, entry.getValue() - available.getOrDefault(entry.getKey(), 0));
            if (deficit > 0) {
                missingTotal += deficit;
                missingKinds++;
                if (missing.size() < 4) {
                    missing.add(BuiltInRegistries.ITEM.getKey(entry.getKey()) + " x" + deficit);
                }
            }
        }
        if (missingTotal == 0) {
            return "Materials: all remaining block items are available.";
        }
        return "Materials: missing " + missingTotal + " block" + (missingTotal == 1 ? "" : "s")
            + " (" + String.join(", ", missing) + (missingKinds > missing.size() ? ", ..." : "") + ").";
    }

    /** A render-safe summary for the build HUD and chat controls. */
    public synchronized Snapshot snapshot() {
        if (schematic == null || origin == null) {
            return null;
        }
        int total = schematic.placements().size();
        int remaining = constructionPlan == null ? total
            : constructionPlan.actionableCount() + constructionPlan.blockedCount() + constructionPlan.conflictCount();
        int complete = Math.max(0, total - remaining);
        return new Snapshot(
            state.name(), origin.immutable(), total, complete, remaining,
            constructionPlan == null ? 0 : constructionPlan.blockedCount(), activeStep == null ? null : activeStep.worldPosition(),
            creativeFlight, status
        );
    }

    public synchronized void stop(String reason) {
        stopInternal(true, reason == null ? "stopped" : reason);
    }

    public synchronized boolean pauseByUser() {
        if (schematic == null || state == State.IDLE || state == State.PAUSED) {
            return false;
        }
        pause("Build paused by user.");
        return true;
    }

    public synchronized boolean resume(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null || schematic == null || state != State.PAUSED) {
            return false;
        }
        creativeFlight = client.player.getAbilities().mayfly && client.player.getAbilities().instabuild;
        if (creativeFlight && !client.player.getAbilities().flying) {
            client.player.getAbilities().flying = true;
            client.player.onUpdateAbilities();
        }
        activeStep = null;
        activeApproach = null;
        navigationFuture = null;
        flightPath = List.of();
        flightPathIndex = 0;
        status = "replanning after resume";
        state = State.PLANNING;
        return true;
    }

    public void tick(Minecraft client) {
        synchronized (this) {
            if (state == State.IDLE || state == State.COMPLETED || state == State.FAILED || state == State.PAUSED) {
                return;
            }
            if (client == null || client.player == null || client.level == null || client.gameMode == null) {
                fail("The world became unavailable while building.");
                return;
            }
            switch (state) {
                case PLANNING -> selectNextStep(client);
                case TRAVELING -> tickTravel(client);
                case FLYING -> tickFlight(client);
                case BREAKING -> tickBreaking(client);
                case WAITING_FOR_DROPS -> tickDropPickupWait(client);
                case PLACING -> tickPlacement(client);
                case WAITING_FOR_PLACE -> tickPlacementConfirmation(client);
                case CLEANUP_PLANNING -> selectScaffoldCleanup(client);
                default -> {
                }
            }
        }
    }

    private void selectNextStep(Minecraft client) {
        constructionPlan = SchematicPlacementPlanner.plan(client.level, schematic, origin, client.player.blockPosition(), conflictPolicy());
        if (constructionPlan.actionableCount() == 0 && constructionPlan.blockedCount() == 0) {
            if (!temporaryScaffolds.isEmpty()) {
                state = State.CLEANUP_PLANNING;
                status = "cleaning up " + temporaryScaffolds.size() + " temporary support block"
                    + (temporaryScaffolds.size() == 1 ? "" : "s");
            } else {
                complete();
            }
            return;
        }
        SchematicPlacementPlanner.ConstructionStep next = constructionPlan.steps().stream()
            .filter(step -> (step.action() == SchematicPlacementPlanner.StepAction.PLACE
                || step.action() == SchematicPlacementPlanner.StepAction.REPLACE
                || isRepairableOwnPlacement(client.level, step))
                && dependenciesAreComplete(client.level, step))
            .map(this::asRepairStepIfNeeded)
            .min(Comparator
                .comparingDouble((SchematicPlacementPlanner.ConstructionStep step) -> step.worldPosition().distSqr(client.player.blockPosition()))
                .thenComparingInt(step -> step.worldPosition().getY()))
            .orElse(null);
        if (next == null) {
            SchematicPlacementPlanner.ConstructionStep blocked = constructionPlan.steps().stream()
                .filter(step -> step.action() == SchematicPlacementPlanner.StepAction.BLOCKED)
                .findFirst().orElse(null);
            if (blocked != null && !creativeFlight
                && !Boolean.TRUE.equals(SettingsManager.getCurrent().schematicAllowScaffolding)) {
                pause("Build paused at " + format(blocked.worldPosition()) + ": no support face is available. "
                    + "Enable Allow scaffolding while building to let survival construction create temporary supports.");
                return;
            }
            if (blocked != null && startScaffoldStep(client, blocked.worldPosition())) {
                liveLog("scaffold requested blocked=" + format(blocked.worldPosition()));
                return;
            }
            SchematicPlacementPlanner.ConstructionStep conflict = constructionPlan.steps().stream()
                .filter(step -> step.action() == SchematicPlacementPlanner.StepAction.CONFLICT)
                .findFirst().orElse(null);
            if (conflict != null) {
                pause("Build paused at " + format(conflict.worldPosition()) + ": " + conflict.blockedReason()
                    + " Enable the appropriate schematic replacement setting to proceed.");
                return;
            }
            pause(blocked == null
                ? "No build step has its required support yet."
                : "Build paused at " + format(blocked.worldPosition()) + ": " + blocked.blockedReason());
            return;
        }
        int slot = ensureDesiredBlockInHotbar(client, next.desired().state().getBlock().asItem());
        if (slot < 0) {
            pause(missingMaterialMessage(client.player, next.desired().state().getBlock().asItem()));
            return;
        }
        activeStep = next;
        activeScaffold = false;
        activeScaffoldCleanup = false;
        activeApproach = selectApproach(client, next);
        if (activeApproach == null) {
            int rejected = rejectedApproaches.getOrDefault(next.worldPosition(), Set.of()).size();
            pause(rejected == 0
                ? "Build paused at " + format(next.worldPosition()) + ": no safe placement approach is currently available."
                : "Build paused at " + format(next.worldPosition()) + ": " + rejected
                    + " placement approach" + (rejected == 1 ? " was" : "es were")
                    + " rejected by the server; no alternative approach remains.");
            return;
        }
        liveLog("schematic step=" + next.action() + " target=" + format(next.worldPosition())
            + " support=" + format(activeApproach.supportPosition()));
        if (creativeFlight) {
            if (!beginCreativeFlight(client, "flying to place " + next.desired().stateId() + " at " + format(next.worldPosition()))) {
                pause("Creative flight could not find an aerial route to " + format(activeApproach.standingPosition()) + ".");
                return;
            }
            LOGGER.info("build step={} action={} mode=creative routeNodes={}", format(next.worldPosition()), next.action(), flightPath.size());
            return;
        }
        navigationFuture = new CompletableFuture<>();
        boolean started = PathmindNavigator.getInstance().startGoto(
            activeApproach.standingPosition(), "Build " + format(next.worldPosition()), navigationFuture);
        if (!started) {
            pause("Native pathfinding could not start for " + format(activeApproach.standingPosition()) + ".");
            return;
        }
        actionStartedAtMs = System.currentTimeMillis();
        status = "traveling to place " + next.desired().stateId() + " at " + format(next.worldPosition());
        state = State.TRAVELING;
        LOGGER.info("build step={} action={} mode=survival approach={}", format(next.worldPosition()), next.action(), format(activeApproach.standingPosition()));
    }

    private void tickTravel(Minecraft client) {
        if (navigationFuture == null || !navigationFuture.isDone()) {
            return;
        }
        if (navigationFuture.isCompletedExceptionally()) {
            if (!activeScaffold && !activeScaffoldCleanup) {
                retryFromAlternateApproach("native pathfinding could not reach the placement position");
            } else {
                pause("Native pathfinding could not reach the temporary support position for " + format(activeStep.worldPosition()) + ".");
            }
            return;
        }
        if (!isNear(client.player.blockPosition(), activeApproach.standingPosition())) {
            state = State.PLANNING;
            status = "replanning placement approach";
            return;
        }
        if (activeScaffoldCleanup) {
            beginScaffoldCleanupBreak();
            return;
        }
        actionStartedAtMs = System.currentTimeMillis();
        if (activeStep.action() == SchematicPlacementPlanner.StepAction.REPLACE
            && !client.level.getBlockState(activeStep.worldPosition()).isAir()) {
            if (!PathmindNavigator.getInstance().isBlockBreakingAllowed()) {
                pause("Build paused: block breaking is disabled.");
                return;
            }
            status = "breaking replacement at " + format(activeStep.worldPosition());
            state = State.BREAKING;
            return;
        }
        placementAttempts = 0;
        nextPlaceAttemptAtMs = 0L;
        lastPlacementOutcome = "no interaction sent";
        state = State.PLACING;
    }

    private void tickFlight(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - actionStartedAtMs > FLIGHT_TIMEOUT_MS) {
            PathmindNavigator.getInstance().pauseExternalNavigation(client);
            if (!activeScaffold && !activeScaffoldCleanup) {
                retryFromAlternateApproach("creative flight timed out before reaching the placement position");
            } else {
                pause("Creative flight timed out while reaching temporary support at " + format(activeStep.worldPosition()) + ".");
            }
            return;
        }
        if (flightPath.isEmpty() || flightPathIndex >= flightPath.size()) {
            PathmindNavigator.getInstance().pauseExternalNavigation(client);
            beginWorldAction(client);
            return;
        }
        BlockPos waypoint = flightPath.get(flightPathIndex);
        Vec3 target = new Vec3(waypoint.getX() + 0.5D, waypoint.getY() + 0.15D, waypoint.getZ() + 0.5D);
        Vec3 position = client.player.position();
        if (position.distanceToSqr(target) <= 0.72D) {
            flightPathIndex++;
            lastFlightProgressPosition = position;
            lastFlightProgressAtMs = now;
            return;
        }
        if (position.distanceToSqr(lastFlightProgressPosition) > FLIGHT_PROGRESS_DISTANCE_SQ) {
            lastFlightProgressPosition = position;
            lastFlightProgressAtMs = now;
            flightStallReplans = 0;
        } else if (now - lastFlightProgressAtMs >= FLIGHT_STALL_REPLAN_MS) {
            List<BlockPos> replanned = SchematicFlightPlanner.findPath(
                client.level, client.player.blockPosition(), activeApproach.standingPosition());
            if (!replanned.isEmpty() && flightStallReplans++ < 2) {
                flightPath = replanned;
                flightPathIndex = flightPath.size() > 1 ? 1 : 0;
                lastFlightProgressPosition = position;
                lastFlightProgressAtMs = now;
                liveLog("flight reroute reason=stalled target=" + format(activeApproach.standingPosition())
                    + " route=" + flightPath.size() + " attempt=" + flightStallReplans);
                return;
            }
            if (!activeScaffold && !activeScaffoldCleanup) {
                retryFromAlternateApproach("creative flight stalled at " + format(client.player.blockPosition())
                    + " while approaching " + format(waypoint));
            } else {
                pause("Creative flight stalled while reaching temporary support at " + format(activeStep.worldPosition()) + ".");
            }
            return;
        }
        PathmindNavigator.getInstance().updateExternalNavigation(client, flightPath, flightPathIndex);
        if (now - lastLiveFlightLogAtMs >= 1000L) {
            lastLiveFlightLogAtMs = now;
            liveLog("flight pos=" + format(client.player.blockPosition()) + " waypoint=" + format(waypoint)
                + " index=" + flightPathIndex + "/" + flightPath.size() + " flying=" + client.player.getAbilities().flying);
        }
    }

    /**
     * Every creative flight leg goes through this single entry point. This
     * keeps ordinary placements, temporary supports, and cleanup on the same
     * navigator-owned camera/input/route lifecycle.
     */
    private boolean beginCreativeFlight(Minecraft client, String flightStatus) {
        if (client == null || client.level == null || client.player == null || activeApproach == null) {
            return false;
        }
        // The nearest valid click face is not necessarily reachable once the
        // build has enclosed part of its own volume. Evaluate the available
        // air approaches by their actual 3D route rather than committing to
        // the first geometric candidate and discovering the problem mid-flight.
        List<SchematicPlacementPlanner.PlacementApproach> candidates = new ArrayList<>();
        candidates.add(activeApproach);
        if (activeStep != null) {
            for (SchematicPlacementPlanner.PlacementApproach candidate
                : SchematicPlacementPlanner.findCreativeFlightApproaches(client.level, activeStep, client.player.blockPosition())) {
                if (!candidate.equals(activeApproach) && !isRejectedApproach(activeStep.worldPosition(), candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        List<BlockPos> bestRoute = List.of();
        SchematicPlacementPlanner.PlacementApproach bestApproach = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (SchematicPlacementPlanner.PlacementApproach candidate : candidates) {
            List<BlockPos> route = SchematicFlightPlanner.findPath(
                client.level, client.player.blockPosition(), candidate.standingPosition());
            if (route.isEmpty()) {
                continue;
            }
            double score = placementApproachPriority(activeStep == null ? null : activeStep.desired().state(), candidate) * 10_000.0D
                + route.size() * 10.0D + candidate.interactionDistanceSq();
            if (score < bestScore) {
                bestScore = score;
                bestApproach = candidate;
                bestRoute = route;
            }
        }
        if (bestApproach == null) {
            return false;
        }
        activeApproach = bestApproach;
        flightPath = bestRoute;
        flightPathIndex = flightPath.size() > 1 ? 1 : 0;
        if (!PathmindNavigator.getInstance().beginExternalNavigation(client, activeApproach.standingPosition(), "Build Creative Flight")) {
            flightPath = List.of();
            flightPathIndex = 0;
            return false;
        }
        actionStartedAtMs = System.currentTimeMillis();
        lastFlightProgressPosition = client.player.position();
        lastFlightProgressAtMs = actionStartedAtMs;
        flightStallReplans = 0;
        status = flightStatus;
        state = State.FLYING;
        liveLog("flight start target=" + format(activeApproach.standingPosition()) + " route=" + flightPath.size()
            + " support=" + format(activeApproach.supportPosition()));
        return true;
    }

    private void beginWorldAction(Minecraft client) {
        actionStartedAtMs = System.currentTimeMillis();
        if (activeScaffoldCleanup) {
            beginScaffoldCleanupBreak();
            return;
        }
        if (activeStep.action() == SchematicPlacementPlanner.StepAction.REPLACE
            && !client.level.getBlockState(activeStep.worldPosition()).isAir()) {
            if (!PathmindNavigator.getInstance().isBlockBreakingAllowed()) {
                pause("Build paused: block breaking is disabled.");
                return;
            }
            status = "breaking replacement at " + format(activeStep.worldPosition());
            state = State.BREAKING;
            return;
        }
        placementAttempts = 0;
        nextPlaceAttemptAtMs = 0L;
        lastPlacementOutcome = "no interaction sent";
        state = State.PLACING;
    }

    private void tickBreaking(Minecraft client) {
        BlockPos target = activeStep.worldPosition();
        BlockState current = client.level.getBlockState(target);
        if (activeScaffoldCleanup) {
            BlockState expectedScaffold = temporaryScaffolds.get(target);
            if (expectedScaffold == null || !current.equals(expectedScaffold)) {
                // Never destroy a block that changed after we placed the support.
                temporaryScaffolds.remove(target);
                activeScaffoldCleanup = false;
                status = "left changed temporary support at " + format(target);
                state = State.CLEANUP_PLANNING;
                return;
            }
        }
        if (current.isAir()) {
            if (activeScaffoldCleanup) {
                temporaryScaffolds.remove(target);
                activeScaffoldCleanup = false;
                status = "removed temporary support at " + format(target);
                if (cleanupDuringBuild) {
                    cleanupDuringBuild = false;
                    state = State.PLANNING;
                } else {
                    state = State.CLEANUP_PLANNING;
                }
                return;
            }
            waitUntilMs = System.currentTimeMillis() + MINED_DROP_PICKUP_WAIT_MS;
            status = "waiting for mined drops";
            state = State.WAITING_FOR_DROPS;
            return;
        }
        if (!PathmindNavigator.getInstance().isBlockBreakingAllowed()) {
            pause(activeScaffoldCleanup
                ? "Scaffold cleanup paused: block breaking is disabled."
                : "Build paused: block breaking is disabled.");
            return;
        }
        if (System.currentTimeMillis() - actionStartedAtMs > BREAK_TIMEOUT_MS) {
            pause("Could not break the existing block at " + format(target) + ".");
            return;
        }
        Direction face = faceToward(client.player.position(), target);
        client.gameMode.startDestroyBlock(target, face);
        client.gameMode.continueDestroyBlock(target, face);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private void tickDropPickupWait(Minecraft client) {
        if (System.currentTimeMillis() < waitUntilMs) {
            return;
        }
        placementAttempts = 0;
        nextPlaceAttemptAtMs = 0L;
        lastPlacementOutcome = "no interaction sent";
        state = State.PLACING;
    }

    private void tickPlacement(Minecraft client) {
        if (activeStep == null || activeApproach == null) {
            state = State.PLANNING;
            return;
        }
        if (client.level.getBlockState(activeStep.worldPosition()).equals(activeStep.desired().state())) {
            state = State.PLANNING;
            return;
        }
        long now = System.currentTimeMillis();
        if (!PathmindNavigator.getInstance().isBlockPlacingAllowed()) {
            pause("Build paused: block placing is disabled.");
            return;
        }
        if (now < nextPlaceAttemptAtMs) {
            return;
        }
        if (!isSolid(client.level.getBlockState(activeApproach.supportPosition()))) {
            state = State.PLANNING;
            status = "support changed; replanning";
            return;
        }
        int hotbarSlot = ensureDesiredBlockInHotbar(client, activeStep.desired().state().getBlock().asItem());
        if (hotbarSlot < 0) {
            pause(missingMaterialMessage(client.player, activeStep.desired().state().getBlock().asItem()));
            return;
        }
        // The face centre is not enough for stateful blocks.  In particular,
        // stairs use the vertical point on a horizontal face to choose their
        // half.  Clicking the centre always asks vanilla for the bottom half,
        // then the reconciler tears it down and tries again forever when the
        // schematic requests a top stair.  Preserve the chosen support face,
        // but aim at the correct half of it before sending the actual use
        // packet.
        Vec3 placementHit = placementHitPosition(activeStep, activeApproach);
        orientForPlacement(client.player, activeStep.desired().state(), placementHit);
        int previousSlot = selectedSlot(client.player.getInventory());
        if (!HotbarSlotSynchronizer.selectHotbarSlot(client, hotbarSlot)) {
            pause("Could not select the required block in the hotbar.");
            return;
        }
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(placementHit, activeApproach.face(), activeApproach.supportPosition(), false));
        attemptedPlacementTargets.add(activeStep.worldPosition().immutable());
        client.player.swing(InteractionHand.MAIN_HAND);
        if (previousSlot >= 0) {
            HotbarSlotSynchronizer.selectHotbarSlot(client, previousSlot);
        }
        placementAttempts++;
        lastPlacementOutcome = result == null ? "interaction returned no result" : String.valueOf(result).toLowerCase(java.util.Locale.ROOT);
        actionStartedAtMs = now;
        nextPlaceAttemptAtMs = now + placementRetryDelayMs();
        status = "placing " + activeStep.desired().stateId() + " at " + format(activeStep.worldPosition())
            + " (attempt " + placementAttempts + ")";
        if (activeStep.desired().stateId().contains("_stairs")) {
            liveLog("stair placement target=" + format(activeStep.worldPosition())
                + " desired=" + activeStep.desired().state()
                + " face=" + activeApproach.face().getSerializedName()
                + " hit=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", placementHit.x, placementHit.y, placementHit.z)
                + " result=" + lastPlacementOutcome);
        }
        if (result != null && result.consumesAction()) {
            state = State.WAITING_FOR_PLACE;
        } else if (placementAttempts >= MAX_PLACE_ATTEMPTS) {
            retryFromAlternateApproach("the interaction was not accepted (" + lastPlacementOutcome + ")");
        }
    }

    private void tickPlacementConfirmation(Minecraft client) {
        if (client.level.getBlockState(activeStep.worldPosition()).equals(activeStep.desired().state())) {
            status = activeScaffold ? "placed temporary support at " + format(activeStep.worldPosition())
                : "placed " + activeStep.desired().stateId() + " at " + format(activeStep.worldPosition());
            liveLog((activeScaffold ? "scaffold placed=" : "schematic placed=") + format(activeStep.worldPosition()));
            if (activeScaffold) {
                temporaryScaffolds.put(activeStep.worldPosition().immutable(), activeStep.desired().state());
            }
            rejectedApproaches.remove(activeStep.worldPosition());
            activeScaffold = false;
            if (creativeFlight && beginImmediateCreativeScaffoldCleanup(client)) {
                return;
            }
            state = State.PLANNING;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - actionStartedAtMs < PLACE_CONFIRM_TIMEOUT_MS) {
            return;
        }
        if (placementAttempts >= MAX_PLACE_ATTEMPTS) {
            retryFromAlternateApproach("the server did not confirm " + activeStep.desired().stateId()
                + " after " + placementAttempts + " attempts (last result: " + lastPlacementOutcome + ")");
            return;
        }
        state = State.PLACING;
    }

    private boolean dependenciesAreComplete(Level world, SchematicPlacementPlanner.ConstructionStep step) {
        if (world == null || step == null || step.dependencies().isEmpty()) {
            return true;
        }
        if (step.approaches().stream().anyMatch(approach -> isSolid(world.getBlockState(approach.supportPosition())))) {
            return true;
        }
        for (BlockPos dependency : step.dependencies()) {
            SchematicBuildPlan.Placement expected = expectedAt(dependency);
            if (expected != null && !world.getBlockState(dependency).equals(expected.state())) {
                return false;
            }
        }
        return true;
    }

    /**
     * A valid placement click can produce the right block with the wrong
     * directional state (notably stairs) before the server reconciles it. If
     * that happened during this build, repair the same block type automatically
     * while retaining the default promise never to overwrite user blocks.
     */
    private boolean isRepairableOwnPlacement(Level world, SchematicPlacementPlanner.ConstructionStep step) {
        if (world == null || step == null || step.action() != SchematicPlacementPlanner.StepAction.CONFLICT
            || !attemptedPlacementTargets.contains(step.worldPosition())) {
            return false;
        }
        BlockState current = world.getBlockState(step.worldPosition());
        BlockState desired = step.desired().state();
        return current.getBlock() == desired.getBlock()
            && !current.equals(desired)
            && world.getBlockEntity(step.worldPosition()) == null
            && current.getDestroySpeed(world, step.worldPosition()) >= 0.0F;
    }

    private SchematicPlacementPlanner.ConstructionStep asRepairStepIfNeeded(SchematicPlacementPlanner.ConstructionStep step) {
        if (step == null || step.action() != SchematicPlacementPlanner.StepAction.CONFLICT) {
            return step;
        }
        return new SchematicPlacementPlanner.ConstructionStep(
            step.desired(), step.worldPosition(), SchematicPlacementPlanner.StepAction.REPLACE,
            step.dependencies(), step.approaches(), null
        );
    }

    private SchematicBuildPlan.Placement expectedAt(BlockPos worldPosition) {
        if (schematic == null || origin == null || worldPosition == null) {
            return null;
        }
        BlockPos base = origin.subtract(schematic.placementAnchor());
        BlockPos relative = worldPosition.subtract(base);
        for (SchematicBuildPlan.Placement placement : schematic.placements()) {
            if (placement.relativePosition().equals(relative)) {
                return placement;
            }
        }
        return null;
    }

    private SchematicPlacementPlanner.PlacementApproach selectLiveApproach(
        Level world, SchematicPlacementPlanner.ConstructionStep step
    ) {
        if (world == null || step == null) {
            return null;
        }
        return step.approaches().stream()
            .filter(approach -> isSolid(world.getBlockState(approach.supportPosition())))
            .filter(approach -> !isRejectedApproach(step.worldPosition(), approach))
            .findFirst().orElse(null);
    }

    private SchematicPlacementPlanner.ConflictPolicy conflictPolicy() {
        return configuredConflictPolicy();
    }

    private SchematicPlacementPlanner.PlacementApproach selectApproach(
        Minecraft client, SchematicPlacementPlanner.ConstructionStep step
    ) {
        if (creativeFlight) {
            return SchematicPlacementPlanner.findCreativeFlightApproaches(client.level, step, client.player.blockPosition()).stream()
                .filter(approach -> !isRejectedApproach(step.worldPosition(), approach))
                .sorted(Comparator.comparingInt(approach -> placementApproachPriority(step.desired().state(), approach)))
                .findFirst().orElse(null);
        }
        return step.approaches().stream()
            .filter(approach -> isSolid(client.level.getBlockState(approach.supportPosition())))
            .filter(approach -> !isRejectedApproach(step.worldPosition(), approach))
            .sorted(Comparator.comparingInt(approach -> placementApproachPriority(step.desired().state(), approach)))
            .findFirst().orElse(null);
    }

    /** Prefer a click face that produces the target block state's axis/half. */
    private static int placementApproachPriority(BlockState desired, SchematicPlacementPlanner.PlacementApproach approach) {
        if (desired == null || approach == null) return 1;
        String axis = propertyValue(desired, "axis");
        if (axis != null && axis.equals(approach.face().getAxis().getName())) return 0;
        String half = propertyValue(desired, "half");
        String slabType = propertyValue(desired, "type");
        if (("top".equals(half) || "top".equals(slabType)) && approach.face().getAxis().isHorizontal()) return 0;
        if (("bottom".equals(half) || "bottom".equals(slabType)) && approach.face() == Direction.UP) return 0;
        return 1;
    }

    private boolean isRejectedApproach(BlockPos target, SchematicPlacementPlanner.PlacementApproach approach) {
        return approach != null && rejectedApproaches.getOrDefault(target, Set.of()).contains(ApproachKey.from(approach));
    }

    /**
     * A failed click is not conclusive: it can be an obstructed ray, a brief
     * desync, or a server-side placement rule.  Retry the same face a few
     * times, then blacklist that exact standing/support/face combination and
     * ask the planner for another real route instead of looping in place.
     */
    private void retryFromAlternateApproach(String reason) {
        if (activeStep == null || activeApproach == null) {
            pause("Build paused: placement state was lost while retrying.");
            return;
        }
        BlockPos target = activeStep.worldPosition().immutable();
        rejectedApproaches.computeIfAbsent(target, ignored -> new HashSet<>()).add(ApproachKey.from(activeApproach));
        LOGGER.warn("placement approach rejected target={} standing={} support={} face={} reason={}",
            format(target), format(activeApproach.standingPosition()), format(activeApproach.supportPosition()), activeApproach.face(), reason);
        activeApproach = null;
        navigationFuture = null;
        flightPath = List.of();
        flightPathIndex = 0;
        placementAttempts = 0;
        nextPlaceAttemptAtMs = 0L;
        status = "replanning " + format(target) + " after " + reason;
        state = State.PLANNING;
    }

    private boolean startScaffoldStep(Minecraft client, BlockPos blockedTarget) {
        // Creative mode has no material cost and already creates the configured
        // support block in the hotbar.  It should be able to establish the
        // first legal placement face even when the survival scaffolding toggle
        // is off; that toggle remains an explicit survival-world permission.
        if ((!creativeFlight && !Boolean.TRUE.equals(SettingsManager.getCurrent().schematicAllowScaffolding))
            || client == null || client.level == null || blockedTarget == null) {
            return false;
        }
        Block scaffold = resolveScaffoldBlock();
        if (scaffold == null) {
            pause("Build paused: no allowed scaffolding block is available.");
            return true;
        }
        BlockPos support = null;
        for (int y = blockedTarget.getY() - 1; y >= client.level.getMinY(); y--) {
            BlockPos candidate = new BlockPos(blockedTarget.getX(), y, blockedTarget.getZ());
            if (isSolid(client.level.getBlockState(candidate))) {
                support = candidate;
                break;
            }
        }
        if (support == null) return false;
        BlockPos scaffoldPos = support.above();
        if (!client.level.getBlockState(scaffoldPos).isAir()) return false;
        String id = BuiltInRegistries.BLOCK.getKey(scaffold).toString();
        SchematicBuildPlan.Placement placement = new SchematicBuildPlan.Placement(scaffoldPos, scaffold.defaultBlockState(), id);
        activeStep = new SchematicPlacementPlanner.ConstructionStep(placement, scaffoldPos,
            SchematicPlacementPlanner.StepAction.PLACE, List.of(), List.of(), null);
        activeApproach = creativeFlight
            ? SchematicPlacementPlanner.findCreativeFlightApproaches(client.level, activeStep, client.player.blockPosition()).stream().findFirst().orElse(null)
            : SchematicPlacementPlanner.findLiveApproaches(client.level, scaffoldPos, client.player.blockPosition()).stream().findFirst().orElse(null);
        if (activeApproach == null) return false;
        activeScaffold = true;
        activeScaffoldCleanup = false;
        if (creativeFlight) {
            if (!beginCreativeFlight(client, "flying to place temporary support at " + format(scaffoldPos))) return false;
        } else {
            navigationFuture = new CompletableFuture<>();
            if (!PathmindNavigator.getInstance().startGoto(activeApproach.standingPosition(), "Build scaffold", navigationFuture)) return false;
            status = "traveling to place temporary support at " + format(scaffoldPos);
            state = State.TRAVELING;
        }
        return true;
    }

    private void selectScaffoldCleanup(Minecraft client) {
        discardChangedScaffoldRecords(client.level);
        Map.Entry<BlockPos, BlockState> next = temporaryScaffolds.entrySet().stream()
            .filter(entry -> canSafelyRemoveScaffold(client.level, entry.getKey()))
            .max(Comparator
                .comparingInt((Map.Entry<BlockPos, BlockState> entry) -> entry.getKey().getY())
                .thenComparingDouble(entry -> -entry.getKey().distSqr(client.player.blockPosition())))
            .orElse(null);
        if (next == null) {
            complete();
            return;
        }

        BlockPos target = next.getKey().immutable();
        String id = BuiltInRegistries.BLOCK.getKey(next.getValue().getBlock()).toString();
        SchematicBuildPlan.Placement placement = new SchematicBuildPlan.Placement(target, next.getValue(), id);
        activeStep = new SchematicPlacementPlanner.ConstructionStep(placement, target,
            SchematicPlacementPlanner.StepAction.REPLACE, List.of(), List.of(), null);
        activeScaffold = false;
        activeScaffoldCleanup = true;
        activeApproach = creativeFlight
            ? SchematicPlacementPlanner.findCreativeFlightApproaches(client.level, activeStep, client.player.blockPosition()).stream().findFirst().orElse(null)
            : SchematicPlacementPlanner.findLiveApproaches(client.level, target, client.player.blockPosition()).stream().findFirst().orElse(null);
        if (activeApproach == null) {
            // The support remains tracked but untouched.  Another scaffold or
            // world change may make it reachable on a later cleanup pass.
            temporaryScaffolds.remove(target);
            status = "retained unreachable temporary support at " + format(target);
            return;
        }
        if (creativeFlight) {
            if (!beginCreativeFlight(client, "flying to remove temporary support at " + format(target))) {
                temporaryScaffolds.remove(target);
                status = "retained unreachable temporary support at " + format(target);
                return;
            }
            return;
        }
        navigationFuture = new CompletableFuture<>();
        if (!PathmindNavigator.getInstance().startGoto(activeApproach.standingPosition(), "Remove scaffold", navigationFuture)) {
            temporaryScaffolds.remove(target);
            status = "retained unreachable temporary support at " + format(target);
            return;
        }
        status = "traveling to remove temporary support at " + format(target);
        state = State.TRAVELING;
    }

    private void beginScaffoldCleanupBreak() {
        if (!PathmindNavigator.getInstance().isBlockBreakingAllowed()) {
            pause("Scaffold cleanup paused: block breaking is disabled.");
            return;
        }
        actionStartedAtMs = System.currentTimeMillis();
        status = "removing temporary support at " + format(activeStep.worldPosition());
        state = State.BREAKING;
    }

    /**
     * Creative mode does not need to preserve reusable support material.  When
     * the just-confirmed build block made its temporary support redundant, mine
     * that exact block before selecting the next build step.
     */
    private boolean beginImmediateCreativeScaffoldCleanup(Minecraft client) {
        if (client == null || client.player == null || activeApproach == null) {
            return false;
        }
        BlockPos scaffold = activeApproach.supportPosition();
        if (!temporaryScaffolds.containsKey(scaffold) || !canSafelyRemoveScaffold(client.level, scaffold)) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(scaffold);
        if (client.player.getEyePosition().distanceToSqr(center) > 4.50D * 4.50D) {
            return false;
        }
        BlockState scaffoldState = temporaryScaffolds.get(scaffold);
        String id = BuiltInRegistries.BLOCK.getKey(scaffoldState.getBlock()).toString();
        activeStep = new SchematicPlacementPlanner.ConstructionStep(
            new SchematicBuildPlan.Placement(scaffold, scaffoldState, id), scaffold,
            SchematicPlacementPlanner.StepAction.REPLACE, List.of(), List.of(), null);
        activeScaffoldCleanup = true;
        cleanupDuringBuild = true;
        beginScaffoldCleanupBreak();
        return true;
    }

    /**
     * Cleanup is deliberately conservative.  A temporary block is removed
     * only after all scaffold blocks above it are gone and it is not touching a
     * non-solid schematic block or a falling block that could depend on it.
     */
    private boolean canSafelyRemoveScaffold(Level world, BlockPos position) {
        if (world == null || position == null || !temporaryScaffolds.containsKey(position)) {
            return false;
        }
        if (temporaryScaffolds.containsKey(position.above())) {
            return false;
        }
        if (hasPendingScaffoldDependency(world, position)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = position.relative(direction);
            if (temporaryScaffolds.containsKey(neighbor)) {
                continue;
            }
            BlockState state = world.getBlockState(neighbor);
            if (state.isAir()) {
                continue;
            }
            SchematicBuildPlan.Placement desired = expectedAt(neighbor);
            if (desired == null) {
                return false;
            }
            if (!isSolid(desired.state()) || desired.state().getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                return false;
            }
        }
        return true;
    }

    /**
     * A scaffold must survive until the final unfinished placement that can
     * click it as a support face has been confirmed. Removing it earlier
     * turns a deterministic support chain into repeated, apparently random
     * scaffold placement.
     */
    private boolean hasPendingScaffoldDependency(Level world, BlockPos scaffold) {
        if (world == null || scaffold == null || constructionPlan == null) {
            return false;
        }
        for (SchematicPlacementPlanner.ConstructionStep step : constructionPlan.steps()) {
            if (step.action() != SchematicPlacementPlanner.StepAction.PLACE
                && step.action() != SchematicPlacementPlanner.StepAction.REPLACE) {
                continue;
            }
            if (world.getBlockState(step.worldPosition()).equals(step.desired().state())) {
                continue;
            }
            boolean usesScaffold = step.approaches().stream()
                .anyMatch(approach -> scaffold.equals(approach.supportPosition()));
            if (usesScaffold) {
                return true;
            }
        }
        return false;
    }

    private void discardChangedScaffoldRecords(Level world) {
        if (world == null) {
            return;
        }
        temporaryScaffolds.entrySet().removeIf(entry -> !world.getBlockState(entry.getKey()).equals(entry.getValue()));
    }

    private Block resolveScaffoldBlock() {
        String allowed = SettingsManager.getCurrent().schematicScaffoldingBlocks;
        if (allowed == null) return null;
        for (String rawId : allowed.split(",")) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(rawId.trim());
            if (id == null) continue;
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block != null && block.asItem() instanceof BlockItem) return block;
        }
        return null;
    }

    private int ensureDesiredBlockInHotbar(Minecraft client, Item item) {
        if (client == null || client.player == null || item == null) {
            return -1;
        }
        Inventory inventory = client.player.getInventory();
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        int sourceSlot = -1;
        for (int slot = hotbarSize; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0 && creativeFlight && client.gameMode != null) {
            int destination = findHotbarDestination(inventory);
            int menuSlot = client.player.containerMenu == null ? -1
                : mapPlayerInventorySlot(client.player.containerMenu, destination);
            if (menuSlot < 0) {
                return -1;
            }
            // handleCreativeModeItemAdd sends the authoritative creative
            // inventory packet, but the local Inventory is not guaranteed to
            // reflect it until the next server update.  The executor needs the
            // item immediately for the interaction below, so mirror the same
            // legal creative edit locally before sending it to the server.
            ItemStack supplied = new ItemStack(item);
            inventory.setItem(destination, supplied.copy());
            // The creative packet uses the player's *menu* slot id, not its
            // hotbar index. Sending 0..8 targets unrelated menu slots, which
            // leaves the server holding the old item and makes its correction
            // erase the client's predicted placement a tick later.
            client.gameMode.handleCreativeModeItemAdd(supplied, menuSlot);
            if (inventory.getItem(destination).is(item)) {
                liveLog("creative supplied=" + BuiltInRegistries.ITEM.getKey(item)
                    + " hotbar=" + destination + " menuSlot=" + menuSlot);
                return destination;
            }
            return -1;
        }
        if (sourceSlot < 0 || client.gameMode == null || client.player.containerMenu == null) {
            return -1;
        }
        int destination = findHotbarDestination(inventory);
        int menuSlot = mapPlayerInventorySlot(client.player.containerMenu, sourceSlot);
        if (menuSlot < 0) {
            return -1;
        }
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, menuSlot, destination, ClickType.SWAP, client.player);
        return inventory.getItem(destination).is(item) ? destination : -1;
    }

    private int findHotbarDestination(Inventory inventory) {
        int selected = selectedSlot(inventory);
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return selected >= 0 ? selected : 0;
    }

    private int mapPlayerInventorySlot(AbstractContainerMenu menu, int inventorySlot) {
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == inventorySlot) {
                return index;
            }
        }
        return -1;
    }

    private String missingMaterialMessage(LocalPlayer player, Item needed) {
        String itemId = BuiltInRegistries.ITEM.getKey(needed).toString();
        int available = 0;
        if (player != null) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(needed)) {
                    available += stack.getCount();
                }
            }
        }
        return "Build paused: missing " + itemId + " (available: " + available + ").";
    }

    private static Map<Item, Integer> inventoryCounts(Inventory inventory) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        if (inventory == null) return counts;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static boolean isAutomaticMultipartHalf(BlockState state) {
        String half = propertyValue(state, "half");
        if ("upper".equals(half)) return true;
        return "head".equals(propertyValue(state, "part"));
    }

    private static boolean isSolid(BlockState state) {
        return state != null && !state.isAir() && !state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static boolean isNear(BlockPos current, BlockPos target) {
        return current != null && target != null && current.distSqr(target) <= 1.0D;
    }

    private static Direction faceToward(Vec3 from, BlockPos target) {
        if (from == null || target == null) {
            return Direction.UP;
        }
        double dx = target.getX() + 0.5D - from.x;
        double dy = target.getY() + 0.5D - from.y;
        double dz = target.getZ() + 0.5D - from.z;
        if (Math.abs(dy) > Math.max(Math.abs(dx), Math.abs(dz))) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    /**
     * Produces the exact point sent in the block-use packet.  Horizontal faces
     * accept any vertical point inside the block face.  Vanilla uses that
     * point to determine the half for stairs and slabs, so a centre click is
     * only correct for a bottom-half state.
     */
    private static Vec3 placementHitPosition(
        SchematicPlacementPlanner.ConstructionStep step,
        SchematicPlacementPlanner.PlacementApproach approach
    ) {
        Vec3 hit = approach.hitPosition();
        String half = propertyValue(step.desired().state(), "half");
        String slabType = propertyValue(step.desired().state(), "type");
        boolean top = "top".equals(half) || "top".equals(slabType);
        boolean bottom = "bottom".equals(half) || "bottom".equals(slabType);
        if ((top || bottom) && approach.face().getAxis().isHorizontal()) {
            return new Vec3(hit.x, step.worldPosition().getY() + (top ? 0.75D : 0.25D), hit.z);
        }
        return hit;
    }

    private static void orientForPlacement(LocalPlayer player, BlockState desired, Vec3 target) {
        orientToward(player, target);
        if (player == null || desired == null) return;
        Direction facing = directionProperty(desired, "facing");
        if (facing == null) facing = directionProperty(desired, "horizontal_facing");
        if (facing == null) facing = railDirection(desired);
        if (facing == null) return;
        if (facing.getAxis().isHorizontal()) {
            float yaw = (float) (Math.atan2(facing.getStepZ(), facing.getStepX()) * 180.0D / Math.PI) - 90.0F;
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
        } else {
            player.setXRot(facing == Direction.UP ? -90.0F : 90.0F);
        }
    }

    private static void orientToward(LocalPlayer player, Vec3 target) {
        if (player == null || target == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F);
        player.setXRot((float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(BlockState state, String name) {
        if (state == null || name == null) return null;
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return null;
        Comparable value = state.getValue(property);
        return value == null ? null : value.toString();
    }

    private static Direction directionProperty(BlockState state, String name) {
        String value = propertyValue(state, name);
        return value == null ? null : Direction.byName(value);
    }

    private static Direction railDirection(BlockState state) {
        String shape = propertyValue(state, "shape");
        if (shape == null) return null;
        if (shape.contains("east_west")) return Direction.EAST;
        if (shape.contains("north_south")) return Direction.NORTH;
        if (shape.contains("north_east") || shape.contains("south_east")) return Direction.EAST;
        if (shape.contains("north_west") || shape.contains("south_west")) return Direction.WEST;
        return null;
    }

    private static int selectedSlot(Inventory inventory) {
        try {
            return PlayerInventoryBridge.getSelectedSlot(inventory);
        } catch (IllegalStateException ignored) {
            return -1;
        }
    }

    private static String format(BlockPos pos) {
        return pos == null ? "?" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void pause(String message) {
        status = message;
        state = State.PAUSED;
        LOGGER.warn("build paused: {}", message);
        liveLog("paused reason=" + message);
        PathmindNavigator.getInstance().stop("schematic build paused");
        PathmindNavigator.getInstance().stopExternalNavigation(Minecraft.getInstance());
        if (completion != null && !completion.isDone()) {
            completion.completeExceptionally(new IllegalStateException(message));
        }
        completion = null;
    }

    private void complete() {
        int retainedScaffolds = temporaryScaffolds.size();
        status = retainedScaffolds == 0 ? "complete"
            : "complete; retained " + retainedScaffolds + " temporary support block"
                + (retainedScaffolds == 1 ? "" : "s") + " still needed for safe support";
        state = State.COMPLETED;
        LOGGER.info("build complete source={} origin={}", schematic == null ? "--" : schematic.source(), format(origin));
        PathmindNavigator.getInstance().stopExternalNavigation(Minecraft.getInstance());
        SchematicPreview.clearBuild();
        liveLog("complete");
        if (completion != null && !completion.isDone()) {
            completion.complete(null);
        }
        clearTerminalState();
    }

    private void fail(String message) {
        status = message;
        state = State.FAILED;
        LOGGER.error("build failed: {}", message);
        PathmindNavigator.getInstance().stopExternalNavigation(Minecraft.getInstance());
        SchematicPreview.clearBuild();
        liveLog("failed reason=" + message);
        if (completion != null && !completion.isDone()) {
            completion.completeExceptionally(new IllegalStateException(message));
        }
        clearTerminalState();
    }

    private void stopInternal(boolean completeFuture, String reason) {
        if (navigationFuture != null && !navigationFuture.isDone()) {
            PathmindNavigator.getInstance().stop("schematic build " + reason);
        }
        if (completion != null && !completion.isDone()) {
            if (completeFuture) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(new java.util.concurrent.CancellationException("Schematic build " + reason));
            }
        }
        status = reason;
        state = State.IDLE;
        LOGGER.info("build stopped: {}", reason);
        PathmindNavigator.getInstance().stopExternalNavigation(Minecraft.getInstance());
        SchematicPreview.clearBuild();
        liveLog("stopped reason=" + reason);
        clearTerminalState();
    }

    private void clearTerminalState() {
        schematic = null;
        origin = null;
        constructionPlan = null;
        activeStep = null;
        activeApproach = null;
        navigationFuture = null;
        flightPath = List.of();
        flightPathIndex = 0;
        creativeFlight = false;
        activeScaffold = false;
        activeScaffoldCleanup = false;
        cleanupDuringBuild = false;
        rejectedApproaches.clear();
        temporaryScaffolds.clear();
        completion = null;
        placementAttempts = 0;
        waitUntilMs = 0L;
    }

    private void liveLog(String event) {
        LOGGER.info("build live: {}", event);
        PathmindNavigator.getInstance().recordExternalEvent(event);
    }

    private static long placementRetryDelayMs() {
        Integer configured = SettingsManager.getCurrent().schematicPlacementSpeed;
        int placementsPerSecond = configured == null ? 5
            : Math.max(MIN_SCHEMATIC_PLACEMENT_SPEED, Math.min(MAX_SCHEMATIC_PLACEMENT_SPEED, configured));
        return Math.max(50L, 1_000L / placementsPerSecond);
    }

    private enum State {
        IDLE,
        PLANNING,
        TRAVELING,
        FLYING,
        BREAKING,
        WAITING_FOR_DROPS,
        PLACING,
        WAITING_FOR_PLACE,
        CLEANUP_PLANNING,
        PAUSED,
        COMPLETED,
        FAILED
    }

    private record ApproachKey(BlockPos standingPosition, BlockPos supportPosition, Direction face) {
        private static ApproachKey from(SchematicPlacementPlanner.PlacementApproach approach) {
            return new ApproachKey(approach.standingPosition().immutable(), approach.supportPosition().immutable(), approach.face());
        }
    }

    public record Snapshot(
        String state,
        BlockPos origin,
        int totalBlocks,
        int completedBlocks,
        int remainingBlocks,
        int blockedBlocks,
        BlockPos activeTarget,
        boolean creativeFlight,
        String status
    ) {
        public double progress() {
            return totalBlocks <= 0 ? 1.0D : Math.min(1.0D, Math.max(0.0D, (double) completedBlocks / totalBlocks));
        }
    }
}
