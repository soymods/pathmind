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
    private static final long PLACE_RETRY_DELAY_MS = 180L;
    // A placement packet can take more than one client tick to be reflected in
    // the local world, especially on a remote server.  Do not issue another
    // click while the previous one is still awaiting authoritative state.
    private static final long PLACE_CONFIRM_TIMEOUT_MS = 1_500L;
    private static final long BREAK_TIMEOUT_MS = 12_000L;
    private static final long FLIGHT_TIMEOUT_MS = 30_000L;
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
    /** Only blocks confirmed as placed by this build are eligible for cleanup. */
    private final Map<BlockPos, BlockState> temporaryScaffolds = new LinkedHashMap<>();
    /** Approaches which received a click but never produced the desired state. */
    private final Map<BlockPos, Set<ApproachKey>> rejectedApproaches = new LinkedHashMap<>();
    private State state = State.IDLE;
    private long actionStartedAtMs;
    private long nextPlaceAttemptAtMs;
    private long waitUntilMs;
    private int placementAttempts;
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
        temporaryScaffolds.clear();
        activeScaffoldCleanup = false;
        actionStartedAtMs = System.currentTimeMillis();
        status = "planning";
        state = State.PLANNING;
        LOGGER.info("build started source={} origin={} creative={}", plan.source(), format(origin), creativeFlight);
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
                || step.action() == SchematicPlacementPlanner.StepAction.REPLACE)
                && dependenciesAreComplete(client.level, step))
            .min(Comparator
                .comparingDouble((SchematicPlacementPlanner.ConstructionStep step) -> step.worldPosition().distSqr(client.player.blockPosition()))
                .thenComparingInt(step -> step.worldPosition().getY()))
            .orElse(null);
        if (next == null) {
            SchematicPlacementPlanner.ConstructionStep blocked = constructionPlan.steps().stream()
                .filter(step -> step.action() == SchematicPlacementPlanner.StepAction.BLOCKED)
                .findFirst().orElse(null);
            if (blocked != null && startScaffoldStep(client, blocked.worldPosition())) {
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
        if (creativeFlight) {
            flightPath = SchematicFlightPlanner.findPath(client.level, client.player.blockPosition(), activeApproach.standingPosition());
            flightPathIndex = flightPath.size() > 1 ? 1 : 0;
            if (flightPath.isEmpty()) {
                pause("Creative flight could not find an aerial route to " + format(activeApproach.standingPosition()) + ".");
                return;
            }
            actionStartedAtMs = System.currentTimeMillis();
            status = "flying to place " + next.desired().stateId() + " at " + format(next.worldPosition());
            state = State.FLYING;
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
        if (System.currentTimeMillis() - actionStartedAtMs > FLIGHT_TIMEOUT_MS) {
            clearFlightKeys(client);
            if (!activeScaffold && !activeScaffoldCleanup) {
                retryFromAlternateApproach("creative flight timed out before reaching the placement position");
            } else {
                pause("Creative flight timed out while reaching temporary support at " + format(activeStep.worldPosition()) + ".");
            }
            return;
        }
        if (flightPath.isEmpty() || flightPathIndex >= flightPath.size()) {
            clearFlightKeys(client);
            beginWorldAction(client);
            return;
        }
        BlockPos waypoint = flightPath.get(flightPathIndex);
        Vec3 target = new Vec3(waypoint.getX() + 0.5D, waypoint.getY() + 0.15D, waypoint.getZ() + 0.5D);
        Vec3 position = client.player.position();
        if (position.distanceToSqr(target) <= 0.20D) {
            flightPathIndex++;
            return;
        }
        steerFlight(client, target);
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
                state = State.CLEANUP_PLANNING;
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

    private void steerFlight(Minecraft client, Vec3 target) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }
        Vec3 position = client.player.position();
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 0.02D) {
            client.player.setYRot((float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F);
            client.player.setXRot(0.0F);
        }
        if (client.options.keyUp != null) client.options.keyUp.setDown(horizontal > 0.14D);
        if (client.options.keyJump != null) client.options.keyJump.setDown(target.y - position.y > 0.18D);
        if (client.options.keyShift != null) client.options.keyShift.setDown(position.y - target.y > 0.18D);
    }

    private void clearFlightKeys(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }
        if (client.options.keyUp != null) client.options.keyUp.setDown(false);
        if (client.options.keyJump != null) client.options.keyJump.setDown(false);
        if (client.options.keyShift != null) client.options.keyShift.setDown(false);
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
        orientForPlacement(client.player, activeStep.desired().state(), activeApproach.hitPosition());
        int previousSlot = selectedSlot(client.player.getInventory());
        if (!HotbarSlotSynchronizer.selectHotbarSlot(client, hotbarSlot)) {
            pause("Could not select the required block in the hotbar.");
            return;
        }
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(activeApproach.hitPosition(), activeApproach.face(), activeApproach.supportPosition(), false));
        client.player.swing(InteractionHand.MAIN_HAND);
        if (previousSlot >= 0) {
            HotbarSlotSynchronizer.selectHotbarSlot(client, previousSlot);
        }
        placementAttempts++;
        lastPlacementOutcome = result == null ? "interaction returned no result" : String.valueOf(result).toLowerCase(java.util.Locale.ROOT);
        actionStartedAtMs = now;
        nextPlaceAttemptAtMs = now + PLACE_RETRY_DELAY_MS;
        status = "placing " + activeStep.desired().stateId() + " at " + format(activeStep.worldPosition())
            + " (attempt " + placementAttempts + ")";
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
            if (activeScaffold) {
                temporaryScaffolds.put(activeStep.worldPosition().immutable(), activeStep.desired().state());
            }
            rejectedApproaches.remove(activeStep.worldPosition());
            activeScaffold = false;
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
        for (BlockPos dependency : step.dependencies()) {
            SchematicBuildPlan.Placement expected = expectedAt(dependency);
            if (expected != null && !world.getBlockState(dependency).equals(expected.state())) {
                return false;
            }
        }
        return true;
    }

    private SchematicBuildPlan.Placement expectedAt(BlockPos worldPosition) {
        if (schematic == null || origin == null || worldPosition == null) {
            return null;
        }
        BlockPos base = origin.offset(schematic.schematicOffset());
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
        if (!Boolean.TRUE.equals(SettingsManager.getCurrent().schematicAllowScaffolding)
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
            flightPath = SchematicFlightPlanner.findPath(client.level, client.player.blockPosition(), activeApproach.standingPosition());
            flightPathIndex = flightPath.size() > 1 ? 1 : 0;
            if (flightPath.isEmpty()) return false;
            actionStartedAtMs = System.currentTimeMillis();
            status = "flying to place temporary support at " + format(scaffoldPos);
            state = State.FLYING;
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
            flightPath = SchematicFlightPlanner.findPath(client.level, client.player.blockPosition(), activeApproach.standingPosition());
            flightPathIndex = flightPath.size() > 1 ? 1 : 0;
            if (flightPath.isEmpty()) {
                temporaryScaffolds.remove(target);
                status = "retained unreachable temporary support at " + format(target);
                return;
            }
            actionStartedAtMs = System.currentTimeMillis();
            status = "flying to remove temporary support at " + format(target);
            state = State.FLYING;
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
            client.gameMode.handleCreativeModeItemAdd(new ItemStack(item), destination);
            return inventory.getItem(destination).is(item) ? destination : -1;
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

    private static void orientForPlacement(LocalPlayer player, BlockState desired, Vec3 target) {
        orientToward(player, target);
        if (player == null || desired == null) return;
        Direction facing = directionProperty(desired, "facing");
        if (facing == null) facing = directionProperty(desired, "horizontal_facing");
        if (facing == null) facing = railDirection(desired);
        if (facing == null) return;
        if (facing.getAxis().isHorizontal()) {
            player.setYRot((float) (Math.atan2(facing.getStepZ(), facing.getStepX()) * 180.0D / Math.PI) - 90.0F);
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
        PathmindNavigator.getInstance().stop("schematic build paused");
        clearFlightKeys(Minecraft.getInstance());
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
        clearFlightKeys(Minecraft.getInstance());
        if (completion != null && !completion.isDone()) {
            completion.complete(null);
        }
        clearTerminalState();
    }

    private void fail(String message) {
        status = message;
        state = State.FAILED;
        LOGGER.error("build failed: {}", message);
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
        clearFlightKeys(Minecraft.getInstance());
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
        rejectedApproaches.clear();
        temporaryScaffolds.clear();
        completion = null;
        placementAttempts = 0;
        waitUntilMs = 0L;
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
