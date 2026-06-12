package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.execution.PathmindNavigator;
import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import net.minecraft.block.Block;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class NodeNavigationCommandExecutor {
    private static final Object GOTO_BREAK_LOCK = new Object();
    private static final AtomicInteger ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS = new AtomicInteger(0);
    private static Boolean gotoBreakOriginalValue = null;
    private static Boolean gotoPlaceOriginalValue = null;
    private static Boolean baritoneChunkCachingOriginalValue = null;
    private static Boolean baritonePathThroughCachedOnlyOriginalValue = null;
    private static Boolean baritoneExploreForBlocksOriginalValue = null;
    private static Boolean baritoneSplicePathOriginalValue = null;
    private static Integer baritoneMaxPathHistoryLengthOriginalValue = null;
    private static Integer baritonePathHistoryCutoffAmountOriginalValue = null;
    private static Integer baritoneMaxCachedWorldScanCountOriginalValue = null;

    private final Node owner;
    private final NodeType type;
    private final NodeMode mode;
    private final NodeRuntimeState runtimeState;

    NodeNavigationCommandExecutor(Node owner) {
        this.owner = owner;
        this.type = owner.getType();
        this.mode = owner.getMode();
        this.runtimeState = owner.runtimeState();
    }

    void executeGotoCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("No mode set for GOTO node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (executeGotoCommandFallback(future)) {
                return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Baritone not available"));
            return;
        }

        resetBaritonePathing(baritone);
        Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);

        if (tryExecuteGotoUsingAttachedParameter(baritone, customGoalProcess, future)) {
            return;
        }

        switch (mode) {
            case GOTO_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, y, z)) {
                    NodeExecutionCompletion.complete(future);
                    return;
                }

                startGotoTaskWithBreakGuard(future);
                Object goal = BaritoneApiProxy.createGoalBlock(x, y, z);
                BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
                break;
                
            case GOTO_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();

                if (isPlayerAtCoordinates(x2, null, z2)) {
                    NodeExecutionCompletion.complete(future);
                    return;
                }

                startGotoTaskWithBreakGuard(future);
                Object goal2 = BaritoneApiProxy.createGoalXZ(x2, z2);
                BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal2);
                break;
                
            case GOTO_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();
                
                startGotoTaskWithBreakGuard(future);
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    if (isPlayerAtCoordinates(null, y3, null)) {
                        NodeExecutionCompletion.complete(future);
                        return;
                    }
                    Object goal3 = BaritoneApiProxy.createGoalYLevel(y3);
                    BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal3);
                }
                break;
                
            case GOTO_BLOCK:
                String block = "stone";
                NodeParameter blockParam = getParameter("Block");
                if (blockParam != null) {
                    block = blockParam.getStringValue();
                }

                BlockPos nearbyBlockTarget = resolveGotoFallbackTargetFromBlockId(block, future);
                if (future.isDone()) {
                    break;
                }
                if (nearbyBlockTarget != null) {
                    startGotoTaskWithBreakGuard(future);
                    Object nearbyGoal = BaritoneApiProxy.createGoalNear(nearbyBlockTarget, 1);
                    BaritoneApiProxy.setGoalAndPath(customGoalProcess, nearbyGoal);
                    break;
                }
                Object getToBlockProcess = BaritoneApiProxy.getGetToBlockProcess(baritone);
                if (getToBlockProcess == null) {
                    NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("GetToBlock process not available"));
                    break;
                }

                startGotoTaskWithBreakGuard(future);
                BaritoneApiProxy.getToBlock(getToBlockProcess, BaritoneApiProxy.createBlockOptionalMeta(block));
                break;
                
            default:
                NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Unknown GOTO mode: " + mode));
                break;
        }
    }

    void executeTravelCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            failTravelNode(future, "No mode set for TRAVEL node");
            return;
        }

        TravelTarget travelTarget = resolveTravelTarget(future);
        if (future.isDone()) {
            return;
        }
        if (travelTarget == null || travelTarget.pos() == null) {
            failTravelNode(future, "No target resolved for TRAVEL node");
            return;
        }
        BlockPos targetPos = travelTarget.pos();

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            failTravelNode(future, "Pathmind Nav unavailable");
            return;
        }
        if (isPlayerAtCoordinates(targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
            NodeExecutionCompletion.complete(future);
            return;
        }

        PathmindNavigator navigator = PathmindNavigator.getInstance();
        boolean previousBreakAllowed = navigator.isBlockBreakingAllowed();
        boolean previousPlaceAllowed = navigator.isBlockPlacingAllowed();
        navigator.setBlockBreakingAllowed(isGotoAllowBreakWhileExecuting());
        navigator.setBlockPlacingAllowed(isGotoAllowPlaceWhileExecuting());

        CompletableFuture<Void> navFuture = new CompletableFuture<>();
        boolean started = travelTarget.nearBlock()
            ? navigator.startGotoNearBlock(targetPos, "Node Travel", navFuture)
            : navigator.startGoto(targetPos, "Node Travel", navFuture);
        if (!started) {
            navigator.setBlockBreakingAllowed(previousBreakAllowed);
            navigator.setBlockPlacingAllowed(previousPlaceAllowed);
            failTravelNode(future, "Could not start Pathmind Nav");
            return;
        }

        navFuture.whenComplete((result, throwable) -> {
            navigator.setBlockBreakingAllowed(previousBreakAllowed);
            navigator.setBlockPlacingAllowed(previousPlaceAllowed);
            if (throwable == null) {
                NodeExecutionCompletion.complete(future);
                return;
            }
            String message = throwable.getMessage();
            if (message == null || message.isBlank()) {
                message = "Travel failed";
            }
            failTravelNode(future, message);
        });
    }

    private record TravelTarget(BlockPos pos, boolean nearBlock) {
    }

    private TravelTarget resolveTravelTarget(CompletableFuture<Void> future) {
        BlockPos attachedBlockTarget = resolveTravelTargetFromAttachedBlockParameter(future);
        if (future.isDone()) {
            return null;
        }
        if (attachedBlockTarget != null) {
            return new TravelTarget(attachedBlockTarget, true);
        }

        boolean[] handled = new boolean[1];
        BlockPos attachedTarget = resolveGotoFallbackTargetFromAttachedParameter(future, handled);
        if (handled[0]) {
            return new TravelTarget(attachedTarget, false);
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        switch (mode) {
            case GOTO_XYZ: {
                return new TravelTarget(resolveClosestCoordinateTarget("X", "Y", "Z", 0, 64, 0), false);
            }
            case GOTO_XZ: {
                if (client == null || client.player == null) {
                    failTravelNode(future, "Player unavailable for TRAVEL XZ target");
                    return null;
                }
                return new TravelTarget(resolveClosestCoordinateTarget("X", null, "Z", 0, client.player.getBlockY(), 0), false);
            }
            case GOTO_Y: {
                if (client == null || client.player == null) {
                    failTravelNode(future, "Player unavailable for TRAVEL Y target");
                    return null;
                }
                BlockPos playerPos = client.player.getBlockPos();
                return new TravelTarget(resolveClosestCoordinateTarget(null, "Y", null, playerPos.getX(), 64, playerPos.getZ()), false);
            }
            case GOTO_BLOCK: {
                BlockPos targetBlock = resolveGotoFallbackTargetFromBlockId(getStringParameter("Block", null), future);
                return new TravelTarget(targetBlock, true);
            }
            default:
                failTravelNode(future, "Unknown TRAVEL mode: " + mode);
                return null;
        }
    }

    private BlockPos resolveTravelTargetFromAttachedBlockParameter(CompletableFuture<Void> future) {
        if (!owner.hasAttachedParameter()) {
            return null;
        }

        List<Integer> slotIndices = owner.getAttachedParameterSlotIndices();
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameterNode = owner.getAttachedParameter(slotIndex);
            if (parameterNode == null) {
                continue;
            }
            if (parameterNode.getType() == NodeType.VARIABLE) {
                parameterNode = resolveVariableValueNode(parameterNode, slotIndex, future);
                if (parameterNode == null) {
                    return null;
                }
            }
            if (parameterNode.getType() != NodeType.PARAM_BLOCK) {
                continue;
            }

            String blockId = getBlockParameterValue(parameterNode);
            BlockPos targetBlock = resolveGotoFallbackTargetFromBlockId(blockId, future);
            if (future.isDone() || targetBlock == null) {
                return targetBlock;
            }
            if (runtimeState.runtimeParameterData != null) {
                runtimeState.runtimeParameterData.targetBlockPos = targetBlock;
            }
            return targetBlock;
        }

        return null;
    }

    private void startGotoTaskWithBreakGuard(CompletableFuture<Void> future) {
        applyBaritoneCacheGuardsDuringMovement(future, false);
        applyBaritoneMovementGuardsDuringGoto(future);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
    }

    void applyBaritoneMovementGuardsDuringGoto(CompletableFuture<Void> future) {
        if (future == null) {
            return;
        }
        boolean disallowBreak = !isGotoAllowBreakWhileExecuting();
        boolean disallowPlace = !isGotoAllowPlaceWhileExecuting();
        if (!disallowBreak && !disallowPlace) {
            return;
        }
        Object settings = BaritoneApiProxy.getSettings();
        if (settings == null) {
            return;
        }

        boolean appliedBreakGuard = false;
        boolean appliedPlaceGuard = false;
        synchronized (GOTO_BREAK_LOCK) {
            if (disallowBreak && ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS.getAndIncrement() == 0) {
                gotoBreakOriginalValue = BaritoneApiProxy.getAllowBreak(settings);
                BaritoneApiProxy.setAllowBreak(settings, false);
                appliedBreakGuard = true;
            } else if (disallowBreak) {
                appliedBreakGuard = true;
            }
            if (disallowPlace && ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS.getAndIncrement() == 0) {
                gotoPlaceOriginalValue = BaritoneApiProxy.getAllowPlace(settings);
                BaritoneApiProxy.setAllowPlace(settings, false);
                appliedPlaceGuard = true;
            } else if (disallowPlace) {
                appliedPlaceGuard = true;
            }
        }

        final boolean restoreBreakGuard = appliedBreakGuard;
        final boolean restorePlaceGuard = appliedPlaceGuard;
        future.whenComplete((result, throwable) -> restoreBaritoneMovementGuardsAfterGoto(restoreBreakGuard, restorePlaceGuard));
    }

    private static void restoreBaritoneMovementGuardsAfterGoto(boolean restoreBreak, boolean restorePlace) {
        if (!restoreBreak && !restorePlace) {
            return;
        }

        synchronized (GOTO_BREAK_LOCK) {
            Object settings = BaritoneApiProxy.getSettings();
            if (restoreBreak) {
                int remainingBreak = ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS.decrementAndGet();
                if (remainingBreak <= 0) {
                    ACTIVE_GOTO_BREAK_BLOCKING_REQUESTS.set(0);
                    Boolean originalBreak = gotoBreakOriginalValue;
                    gotoBreakOriginalValue = null;
                    if (settings != null) {
                        BaritoneApiProxy.setAllowBreak(settings, originalBreak != null ? originalBreak : Boolean.TRUE);
                    }
                }
            }
            if (restorePlace) {
                int remainingPlace = ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS.decrementAndGet();
                if (remainingPlace <= 0) {
                    ACTIVE_GOTO_PLACE_BLOCKING_REQUESTS.set(0);
                    Boolean originalPlace = gotoPlaceOriginalValue;
                    gotoPlaceOriginalValue = null;
                    if (settings != null) {
                        BaritoneApiProxy.setAllowPlace(settings, originalPlace != null ? originalPlace : Boolean.TRUE);
                    }
                }
            }
        }
    }

    void applyBaritoneCacheGuardsDuringMovement(CompletableFuture<Void> future, boolean disableExploreForBlocks) {
        applyBaritoneCacheGuardsDuringMovement(future, disableExploreForBlocks, false);
    }

    void applyBaritoneCacheGuardsDuringMovement(CompletableFuture<Void> future,
                                                        boolean disableExploreForBlocks,
                                                        boolean disableCachedWorldBlockScan) {
        if (future == null) {
            return;
        }
        Object settings = BaritoneApiProxy.getSettings();
        if (settings == null) {
            return;
        }

        boolean restoreCacheSettings = false;
        boolean restoreExploreSetting = false;
        boolean restorePathHistorySettings = false;
        boolean restoreCachedScanSetting = false;
        synchronized (GOTO_BREAK_LOCK) {
            if (ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS.getAndIncrement() == 0) {
                baritoneChunkCachingOriginalValue = BaritoneApiProxy.getChunkCaching(settings);
                baritonePathThroughCachedOnlyOriginalValue = BaritoneApiProxy.getPathThroughCachedOnly(settings);
                BaritoneApiProxy.setChunkCaching(settings, false);
                BaritoneApiProxy.setPathThroughCachedOnly(settings, false);
                restoreCacheSettings = true;
            } else {
                restoreCacheSettings = true;
            }

            if (disableExploreForBlocks) {
                if (ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS.getAndIncrement() == 0) {
                    baritoneExploreForBlocksOriginalValue = BaritoneApiProxy.getExploreForBlocks(settings);
                    BaritoneApiProxy.setExploreForBlocks(settings, false);
                    restoreExploreSetting = true;
                } else {
                    restoreExploreSetting = true;
                }
            }

            if (ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS.getAndIncrement() == 0) {
                baritoneSplicePathOriginalValue = BaritoneApiProxy.getSplicePath(settings);
                baritoneMaxPathHistoryLengthOriginalValue = BaritoneApiProxy.getMaxPathHistoryLength(settings);
                baritonePathHistoryCutoffAmountOriginalValue = BaritoneApiProxy.getPathHistoryCutoffAmount(settings);
                BaritoneApiProxy.setSplicePath(settings, false);
                BaritoneApiProxy.setMaxPathHistoryLength(settings, 0);
                BaritoneApiProxy.setPathHistoryCutoffAmount(settings, 0);
                restorePathHistorySettings = true;
            } else {
                restorePathHistorySettings = true;
            }

            if (disableCachedWorldBlockScan) {
                if (ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS.getAndIncrement() == 0) {
                    baritoneMaxCachedWorldScanCountOriginalValue = BaritoneApiProxy.getMaxCachedWorldScanCount(settings);
                    BaritoneApiProxy.setMaxCachedWorldScanCount(settings, 0);
                    restoreCachedScanSetting = true;
                } else {
                    restoreCachedScanSetting = true;
                }
            }
        }

        final boolean restoreCache = restoreCacheSettings;
        final boolean restoreExplore = restoreExploreSetting;
        final boolean restorePathHistory = restorePathHistorySettings;
        final boolean restoreCachedScan = restoreCachedScanSetting;
        future.whenComplete((result, throwable) -> restoreBaritoneCacheGuardsAfterMovement(restoreCache, restoreExplore, restorePathHistory, restoreCachedScan));
    }

    private static void restoreBaritoneCacheGuardsAfterMovement(boolean restoreCache,
                                                                boolean restoreExplore,
                                                                boolean restorePathHistory,
                                                                boolean restoreCachedScan) {
        if (!restoreCache && !restoreExplore && !restorePathHistory && !restoreCachedScan) {
            return;
        }

        synchronized (GOTO_BREAK_LOCK) {
            Object settings = BaritoneApiProxy.getSettings();
            if (restoreCache) {
                int remaining = ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS.decrementAndGet();
                if (remaining <= 0) {
                    ACTIVE_BARITONE_CACHE_OVERRIDE_REQUESTS.set(0);
                    Boolean originalChunkCaching = baritoneChunkCachingOriginalValue;
                    Boolean originalPathThroughCachedOnly = baritonePathThroughCachedOnlyOriginalValue;
                    baritoneChunkCachingOriginalValue = null;
                    baritonePathThroughCachedOnlyOriginalValue = null;
                    if (settings != null) {
                        if (originalChunkCaching != null) {
                            BaritoneApiProxy.setChunkCaching(settings, originalChunkCaching);
                        }
                        if (originalPathThroughCachedOnly != null) {
                            BaritoneApiProxy.setPathThroughCachedOnly(settings, originalPathThroughCachedOnly);
                        }
                    }
                }
            }

            if (restoreExplore) {
                int remaining = ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS.decrementAndGet();
                if (remaining <= 0) {
                    ACTIVE_BARITONE_EXPLORE_OVERRIDE_REQUESTS.set(0);
                    Boolean originalExploreForBlocks = baritoneExploreForBlocksOriginalValue;
                    baritoneExploreForBlocksOriginalValue = null;
                    if (settings != null && originalExploreForBlocks != null) {
                        BaritoneApiProxy.setExploreForBlocks(settings, originalExploreForBlocks);
                    }
                }
            }

            if (restorePathHistory) {
                int remaining = ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS.decrementAndGet();
                if (remaining <= 0) {
                    ACTIVE_BARITONE_PATH_HISTORY_OVERRIDE_REQUESTS.set(0);
                    Boolean originalSplicePath = baritoneSplicePathOriginalValue;
                    Integer originalMaxPathHistoryLength = baritoneMaxPathHistoryLengthOriginalValue;
                    Integer originalPathHistoryCutoffAmount = baritonePathHistoryCutoffAmountOriginalValue;
                    baritoneSplicePathOriginalValue = null;
                    baritoneMaxPathHistoryLengthOriginalValue = null;
                    baritonePathHistoryCutoffAmountOriginalValue = null;
                    if (settings != null) {
                        if (originalSplicePath != null) {
                            BaritoneApiProxy.setSplicePath(settings, originalSplicePath);
                        }
                        if (originalMaxPathHistoryLength != null) {
                            BaritoneApiProxy.setMaxPathHistoryLength(settings, originalMaxPathHistoryLength);
                        }
                        if (originalPathHistoryCutoffAmount != null) {
                            BaritoneApiProxy.setPathHistoryCutoffAmount(settings, originalPathHistoryCutoffAmount);
                        }
                    }
                }
            }

            if (restoreCachedScan) {
                int remaining = ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS.decrementAndGet();
                if (remaining <= 0) {
                    ACTIVE_BARITONE_CACHED_SCAN_OVERRIDE_REQUESTS.set(0);
                    Integer originalMaxCachedWorldScanCount = baritoneMaxCachedWorldScanCountOriginalValue;
                    baritoneMaxCachedWorldScanCountOriginalValue = null;
                    if (settings != null && originalMaxCachedWorldScanCount != null) {
                        BaritoneApiProxy.setMaxCachedWorldScanCount(settings, originalMaxCachedWorldScanCount);
                    }
                }
            }
        }
    }

    private boolean tryExecuteGotoUsingAttachedParameter(Object baritone, Object customGoalProcess, CompletableFuture<Void> future) {
        RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
        if (parameterData != null && parameterData.targetEntity != null) {
            return gotoSpecificEntity(parameterData.targetEntity, customGoalProcess, future);
        }

        Node parameterNode = getAttachedParameter();
        if (parameterNode == null) {
            return false;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, 0, future);
            if (parameterNode == null) {
                return true;
            }
        }

        switch (parameterNode.getType()) {
            case PARAM_ITEM:
                return gotoNearestDroppedItem(parameterNode, customGoalProcess, future);
            case PARAM_ENTITY:
                return gotoNearestEntity(parameterNode, customGoalProcess, future);
            case PARAM_PLAYER:
                return gotoNamedPlayer(parameterNode, customGoalProcess, future);
            case PARAM_BLOCK:
                return gotoBlockFromParameter(parameterNode, baritone, future);
            default:
                return false;
        }
    }

    private boolean executeGotoCommandFallback(CompletableFuture<Void> future) {
        if (!isBaritoneModAvailable()) {
            return false;
        }

        boolean[] handled = new boolean[1];
        BlockPos target = resolveGotoFallbackTargetFromAttachedParameter(future, handled);
        if (handled[0]) {
            if (target == null) {
                return true;
            }
            String command = String.format("#goto %d %d %d", target.getX(), target.getY(), target.getZ());
            executeCommand(command);
            NodeExecutionCompletion.complete(future);
            return true;
        }

        switch (mode) {
            case GOTO_XYZ: {
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, y, z)) {
                    NodeExecutionCompletion.complete(future);
                    return true;
                }
                String command = String.format("#goto %d %d %d", x, y, z);
                executeCommand(command);
                NodeExecutionCompletion.complete(future);
                return true;
            }
            case GOTO_XZ: {
                int x = 0, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, null, z)) {
                    NodeExecutionCompletion.complete(future);
                    return true;
                }
                String command = String.format("#goto %d %d", x, z);
                executeCommand(command);
                NodeExecutionCompletion.complete(future);
                return true;
            }
            case GOTO_Y: {
                int y = 64;
                NodeParameter yParam = getParameter("Y");
                if (yParam != null) y = yParam.getIntValue();

                if (isPlayerAtCoordinates(null, y, null)) {
                    NodeExecutionCompletion.complete(future);
                    return true;
                }
                String command = String.format("#goto %d", y);
                executeCommand(command);
                NodeExecutionCompletion.complete(future);
                return true;
            }
            case GOTO_BLOCK: {
                String blockId = getStringParameter("Block", null);
                BlockPos pos = resolveGotoFallbackTargetFromBlockId(blockId, future);
                if (pos == null) {
                    return true;
                }
                String command = String.format("#goto %d %d %d", pos.getX(), pos.getY(), pos.getZ());
                executeCommand(command);
                NodeExecutionCompletion.complete(future);
                return true;
            }
            default:
                return false;
        }
    }

    private BlockPos resolveGotoFallbackTargetFromAttachedParameter(CompletableFuture<Void> future, boolean[] handled) {
        if (handled != null && handled.length > 0) {
            handled[0] = false;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
        if (parameterData != null && parameterData.targetEntity != null) {
            if (handled != null && handled.length > 0) {
                handled[0] = true;
            }
            if (client != null && parameterData.targetEntity == client.player) {
                NodeExecutionCompletion.complete(future);
                return null;
            }
            return parameterData.targetEntity.getBlockPos();
        }
        if (parameterData != null && parameterData.targetBlockPos != null) {
            if (handled != null && handled.length > 0) {
                handled[0] = true;
            }
            return parameterData.targetBlockPos;
        }

        Node parameterNode = getAttachedParameter();
        if (parameterNode == null) {
            return null;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, 0, future);
            if (parameterNode == null) {
                if (handled != null && handled.length > 0) {
                    handled[0] = true;
                }
                NodeExecutionCompletion.complete(future);
                return null;
            }
        }

        if (handled != null && handled.length > 0) {
            handled[0] = true;
        }

        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(parameterNode.getType());
        if (behaviorDefinition != null && behaviorDefinition.hasGotoFallbackTargetBehavior()) {
            return behaviorDefinition.resolveGotoFallbackTarget(owner, parameterNode, client, future);
        }
        if (handled != null && handled.length > 0) {
            handled[0] = false;
        }
        return null;
    }

    BlockPos resolveGotoFallbackTargetFromBlockId(String blockId, CompletableFuture<Void> future) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return null;
        }

        ResolvedBlockTarget resolved = resolveNearestBlockTarget(blockId, client);
        if (resolved == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateBlockNoSelection"));
            return null;
        }
        if (resolved.invalidValue() != null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.navigateBlockUnknownIdentifier", resolved.invalidValue()));
            return null;
        }
        if (resolved.pos() == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noTargetFoundNearby", resolved.normalizedId(), type.getDisplayName()));
            return null;
        }

        setParameterValueAndPropagate("Block", resolved.normalizedId());

        if (client.player != null) {
            BlockPos playerBlockPos = client.player.getBlockPos();
            BlockPos targetPos = resolved.pos();
            if (playerBlockPos.equals(targetPos)) {
                NodeExecutionCompletion.complete(future);
                return null;
            }
            if (resolved.targetBlock() != null && client.world.getBlockState(targetPos).isOf(resolved.targetBlock())) {
                double distanceSq = client.player.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distanceSq <= 2.25D) {
                    NodeExecutionCompletion.complete(future);
                    return null;
                }
            }
        }

        return resolved.pos();
    }

    private boolean gotoSpecificEntity(Entity targetEntity, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (targetEntity == null || targetEntity.isRemoved()) {
            return false;
        }
        if (targetEntity == client.player) {
            NodeExecutionCompletion.complete(future);
            return true;
        }
        if (customGoalProcess == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateEntityGoalUnavailable"));
            return true;
        }

        BlockPos pos = targetEntity.getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNearestDroppedItem(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        if (itemIds.isEmpty()) {
            return false;
        }

        double searchRange = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Optional<BlockPos> matchedPosition = Optional.empty();
        Item matchedItem = null;
        String matchedItemId = null;

        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            Optional<BlockPos> target = findNearestDroppedItem(client, candidateItem, searchRange);
            if (target.isPresent()) {
                matchedPosition = target;
                matchedItem = candidateItem;
                matchedItemId = candidateId;
                break;
            }
        }

        if (matchedPosition.isEmpty()) {
            String reference = String.join(", ", itemIds);
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noDroppedItemNearbyYet", reference, type.getDisplayName()));
            return true;
        }

        if (customGoalProcess == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateDroppedItemGoalUnavailable"));
            return true;
        }

        BlockPos pos = matchedPosition.get();
        if (runtimeState.runtimeParameterData != null) {
            runtimeState.runtimeParameterData.targetBlockPos = pos;
            runtimeState.runtimeParameterData.targetItem = matchedItem;
            runtimeState.runtimeParameterData.targetItemId = matchedItemId;
        }

        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNearestEntity(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        List<String> entityIds = resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            return false;
        }
        String state = getEntityParameterState(parameterNode);
        double range = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> target = findNearestEntity(client, entityType, range, state);
            if (target.isEmpty()) {
                continue;
            }
            double distance = target.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = target.get();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noMatchingEntityNearby", type.getDisplayName()));
            return true;
        }

        if (customGoalProcess == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateEntityGoalUnavailable"));
            return true;
        }

        BlockPos pos = nearest.getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoNamedPlayer(Node parameterNode, Object customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String playerName = getParameterString(parameterNode, "Player");
        if (isSelfPlayerValue(playerName)) {
            NodeExecutionCompletion.complete(future);
            return true;
        }
        Optional<AbstractClientPlayerEntity> match;
        if (isAnyPlayerValue(playerName)) {
            match = findNearestPlayer(client, client.player);
        } else if (isSelfPlayerValue(playerName)) {
            match = Optional.of(client.player);
        } else {
            match = client.world.getPlayers().stream()
                .filter(p -> playerName.equalsIgnoreCase(
                    GameProfileCompatibilityBridge.getName(p.getGameProfile())))
                .findFirst();
        }

        if (match.isEmpty()) {
            String message;
            if (isAnyPlayerValue(playerName)) {
                message = tr("pathmind.error.noPlayersNearby", type.getDisplayName());
            } else if (isSelfPlayerValue(playerName)) {
                message = tr("pathmind.error.localPlayerUnavailable", type.getDisplayName());
            } else {
                message = tr("pathmind.error.playerNotNearby", playerName, type.getDisplayName());
            }
            NodeExecutionCompletion.fail(owner, client, future, message);
            return true;
        }

        if (customGoalProcess == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigatePlayerGoalUnavailable"));
            return true;
        }

        BlockPos pos = match.get().getBlockPos();
        startGotoTaskWithBreakGuard(future);
        Object goal = BaritoneApiProxy.createGoalBlock(pos.getX(), pos.getY(), pos.getZ());
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
        return true;
    }

    private boolean gotoBlockFromParameter(Node parameterNode, Object baritone, CompletableFuture<Void> future) {
        String blockId = getBlockParameterValue(parameterNode);
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
        BlockPos targetPos = parameterData != null ? parameterData.targetBlockPos : null;
        String normalized = null;
        Block targetBlock = null;

        if (client != null && client.world != null) {
            ResolvedBlockTarget resolved = resolveNearestBlockTarget(blockId, client);
            if (resolved == null) {
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateBlockNoSelection"));
                return true;
            }
            if (resolved.invalidValue() != null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.navigateBlockUnknownIdentifier", resolved.invalidValue()));
                return true;
            }
            normalized = resolved.normalizedId();
            targetBlock = resolved.targetBlock();
            if (targetPos == null) {
                if (resolved.pos() == null) {
                    NodeExecutionCompletion.fail(owner, client, future,
                        tr("pathmind.error.noTargetFoundNearby", normalized, type.getDisplayName()));
                    return true;
                }
                targetPos = resolved.pos();
            }

            setParameterValueAndPropagate("Block", normalized);

            if (client.player != null && targetPos != null && targetBlock != null
                && client.world.getBlockState(targetPos).isOf(targetBlock)) {
                BlockPos playerBlockPos = client.player.getBlockPos();
                if (playerBlockPos.equals(targetPos)) {
                    NodeExecutionCompletion.complete(future);
                    return true;
                }
                double distanceSq = client.player.squaredDistanceTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distanceSq <= 2.25D) { // already within ~1.5 blocks, treat as complete
                    NodeExecutionCompletion.complete(future);
                    return true;
                }
            }
        }

        if (targetPos != null) {
            Object customGoalProcess = baritone != null ? BaritoneApiProxy.getCustomGoalProcess(baritone) : null;
            if (customGoalProcess == null) {
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateBlockGoalUnavailable"));
                return true;
            }

            startGotoTaskWithBreakGuard(future);
            Object goal = BaritoneApiProxy.createGoalNear(targetPos, 1);
            BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);
            return true;
        }

        Object getToBlockProcess = baritone != null ? BaritoneApiProxy.getGetToBlockProcess(baritone) : null;
        if (getToBlockProcess == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.navigateBlockSearchUnavailable"));
            return true;
        }

        startGotoTaskWithBreakGuard(future);
        String targetId = (normalized != null && !normalized.isEmpty()) ? normalized : blockId;
        BaritoneApiProxy.getToBlock(getToBlockProcess, BaritoneApiProxy.createBlockOptionalMeta(targetId));
        return true;
    }

       void executeGoalCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for GOAL node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeState.runtimeParameterData.targetBlockPos;
                String command = String.format("#goal %d %d %d", target.getX(), target.getY(), target.getZ());
                executeCommand(command);
                future.complete(null);
                return;
            }
            switch (mode) {
                case GOAL_XYZ: {
                    int x = 0, y = 64, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter yParam = getParameter("Y");
                    NodeParameter zParam = getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (yParam != null) y = yParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#goal %d %d %d", x, y, z);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_XZ: {
                    int x = 0, z = 0;
                    NodeParameter xParam = getParameter("X");
                    NodeParameter zParam = getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#goal %d %d", x, z);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_Y: {
                    int y = 64;
                    NodeParameter yParam = getParameter("Y");
                    if (yParam != null) y = yParam.getIntValue();

                    String command = String.format("#goal %d", y);
                    executeCommand(command);
                    future.complete(null);
                    return;
                }
                case GOAL_CURRENT: {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null && client.player != null) {
                        int currentX = (int) client.player.getX();
                        int currentY = (int) client.player.getY();
                        int currentZ = (int) client.player.getZ();
                        String command = String.format("#goal %d %d %d", currentX, currentY, currentZ);
                        executeCommand(command);
                    }
                    future.complete(null);
                    return;
                }
                case GOAL_CLEAR: {
                    executeCommand("#goal clear");
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown GOAL mode: " + mode));
                    return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
        
        switch (mode) {
            case GOAL_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                Object goal = BaritoneApiProxy.createGoalBlock(x, y, z);
                BaritoneApiProxy.setGoal(customGoalProcess, goal);
                break;
                
            case GOAL_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();

                Object goal2 = BaritoneApiProxy.createGoalXZ(x2, z2);
                BaritoneApiProxy.setGoal(customGoalProcess, goal2);
                break;
                
            case GOAL_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();

                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    Object goal3 = BaritoneApiProxy.createGoalYLevel(y3);
                    BaritoneApiProxy.setGoal(customGoalProcess, goal3);
                }
                break;
                
            case GOAL_CURRENT:
                net.minecraft.client.MinecraftClient client2 = net.minecraft.client.MinecraftClient.getInstance();
                if (client2 != null && client2.player != null) {
                    int currentX = (int) client2.player.getX();
                    int currentY = (int) client2.player.getY();
                    int currentZ = (int) client2.player.getZ();
                    Object goal4 = BaritoneApiProxy.createGoalBlock(currentX, currentY, currentZ);
                    BaritoneApiProxy.setGoal(customGoalProcess, goal4);
                }
                break;
                
            case GOAL_CLEAR:
                BaritoneApiProxy.setGoal(customGoalProcess, null);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown GOAL mode: " + mode));
                return;
        }
        
        // Goal setting is immediate, no need to wait
        future.complete(null);
    }
    
    void executePathCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }


        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            if (runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeState.runtimeParameterData.targetBlockPos;
                String goalCommand = String.format("#goal %d %d %d", target.getX(), target.getY(), target.getZ());
                executeCommand(goalCommand);
            }
            executeCommand("#path");
            future.complete(null);
            return;
        }

        Object baritone = getBaritone();
        if (baritone != null) {
            resetBaritonePathing(baritone);
            // Start precise tracking of this task
            PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_PATH, future);

            // Start the Baritone pathing task
            Object customGoalProcess = BaritoneApiProxy.getCustomGoalProcess(baritone);
            if (runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeState.runtimeParameterData.targetBlockPos;
                BaritoneApiProxy.setGoal(customGoalProcess, BaritoneApiProxy.createGoalBlock(target.getX(), target.getY(), target.getZ()));
            }
            BaritoneApiProxy.path(customGoalProcess);

            // The future will be completed by the PreciseCompletionTracker when the path actually reaches the goal
        } else {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
        }
    }
    
    void executeStopCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for STOP node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            PreciseCompletionTracker.getInstance().cancelAllTasks();
            String command;
            switch (mode) {
                case STOP_NORMAL:
                    command = "#stop";
                    break;
                case STOP_CANCEL:
                case STOP_FORCE:
                    command = "#cancel";
                    break;
                default:
                    future.completeExceptionally(new RuntimeException("Unknown STOP mode: " + mode));
                    return;
            }
            executeCommand(command);
            future.complete(null);
            return;
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        switch (mode) {
            case STOP_NORMAL:
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            case STOP_CANCEL:
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            case STOP_FORCE:
                // Force cancel all tasks
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Force stop all Baritone processes
                BaritoneApiProxy.cancelEverything(BaritoneApiProxy.getPathingBehavior(baritone));
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown STOP mode: " + mode));
                return;
        }
        
        // Complete immediately since stop is immediate
        future.complete(null);
    }

    void executeInvertCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#invert";

        executeCommand(command);
        future.complete(null); // Invert commands complete immediately
    }

    void executeComeCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#come";

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    void executeSurfaceCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#surface";

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    void executeTunnelCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#tunnel";

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }
    
    void executeFarmCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FARM node"));
            return;
        }

        if (!isBaritoneApiAvailable() && isBaritoneModAvailable()) {
            switch (mode) {
                case FARM_RANGE: {
                    int range = 10;
                    NodeParameter rangeParam = getParameter("Range");
                    if (rangeParam != null) {
                        range = rangeParam.getIntValue();
                    }
                    executeCommand("#farm " + range);
                    future.complete(null);
                    return;
                }
                case FARM_WAYPOINT: {
                    String waypoint = "farm";
                    int waypointRange = 10;
                    NodeParameter waypointParam = getParameter("Waypoint");
                    NodeParameter waypointRangeParam = getParameter("Range");

                    if (waypointParam != null) {
                        waypoint = waypointParam.getStringValue();
                    }
                    if (waypointRangeParam != null) {
                        waypointRange = waypointRangeParam.getIntValue();
                    }
                    executeCommand("#farm " + waypoint + " " + waypointRange);
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown FARM mode: " + mode));
                    return;
            }
        }

        Object baritone = getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        Object farmProcess = BaritoneApiProxy.getFarmProcess(baritone);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_FARM, future);
        
        switch (mode) {
            case FARM_RANGE:
                int range = 10;
                NodeParameter rangeParam = getParameter("Range");
                if (rangeParam != null) {
                    range = rangeParam.getIntValue();
                }
                
                BaritoneApiProxy.farm(farmProcess, range);
                break;
                
            case FARM_WAYPOINT:
                String waypoint = "farm";
                int waypointRange = 10;
                NodeParameter waypointParam = getParameter("Waypoint");
                NodeParameter waypointRangeParam = getParameter("Range");
                
                if (waypointParam != null) {
                    waypoint = waypointParam.getStringValue();
                }
                if (waypointRangeParam != null) {
                    waypointRange = waypointRangeParam.getIntValue();
                }
                
                // For waypoint-based farming, we need to use a different approach
                executeCommand("#farm " + waypoint + " " + waypointRange);
                future.complete(null); // Command-based farming completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FARM mode: " + mode));
                return;
        }
    }

    private Node.ParameterHandlingResult preprocessAttachedParameter(EnumSet<Node.ParameterUsage> usages, CompletableFuture<Void> future) {
        return owner.preprocessAttachedParameter(usages, future);
    }

    private boolean isBaritoneApiAvailable() {
        return owner.isBaritoneApiAvailable();
    }

    private boolean isBaritoneModAvailable() {
        return owner.isBaritoneModAvailable();
    }

    private Object getBaritone() {
        return owner.getBaritone();
    }

    private void resetBaritonePathing(Object baritone) {
        owner.resetBaritonePathing(baritone);
    }

    private NodeParameter getParameter(String name) {
        return owner.getParameter(name);
    }

    private boolean isPlayerAtCoordinates(Integer x, Integer y, Integer z) {
        return owner.isPlayerAtCoordinates(x, y, z);
    }

    private boolean isGotoAllowBreakWhileExecuting() {
        return owner.isGotoAllowBreakWhileExecuting();
    }

    private boolean isGotoAllowPlaceWhileExecuting() {
        return owner.isGotoAllowPlaceWhileExecuting();
    }

    private int getIntParameter(String name, int defaultValue) {
        return owner.getIntParameter(name, defaultValue);
    }

    private String getStringParameter(String name, String defaultValue) {
        return owner.getStringParameter(name, defaultValue);
    }

    private Node resolveVariableValueNode(Node variableNode, int slotIndex, CompletableFuture<Void> future) {
        return owner.resolveVariableValueNode(variableNode, slotIndex, future);
    }

    private String getBlockParameterValue(Node node) {
        return owner.getBlockParameterValue(node);
    }

    private Node getAttachedParameter() {
        return owner.getAttachedParameter();
    }

    private void executeCommand(String command) {
        owner.executeCommand(command);
    }

    private List<String> splitMultiValueList(String rawValue) {
        return owner.splitMultiValueList(rawValue);
    }

    private String sanitizeResourceId(String value) {
        return owner.sanitizeResourceId(value);
    }

    private String normalizeResourceId(String value, String defaultNamespace) {
        return owner.normalizeResourceId(value, defaultNamespace);
    }

    private record ResolvedBlockTarget(BlockPos pos, String normalizedId, Block targetBlock, String invalidValue) {
    }

    private ResolvedBlockTarget resolveNearestBlockTarget(String rawBlockId, net.minecraft.client.MinecraftClient client) {
        List<BlockSelection> selections = new ArrayList<>();
        String firstNormalized = null;
        for (String blockId : splitMultiValueList(rawBlockId)) {
            String sanitized = sanitizeResourceId(blockId);
            String normalized = (sanitized != null && !sanitized.isEmpty())
                ? normalizeResourceId(sanitized, "minecraft")
                : null;
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }

            Optional<BlockSelection> parsedSelection = BlockSelection.parse(blockId);
            if (parsedSelection.isEmpty()) {
                return new ResolvedBlockTarget(null, null, null, blockId);
            }
            if (firstNormalized == null) {
                firstNormalized = parsedSelection.get().asString();
            }
            selections.add(parsedSelection.get());
        }

        if (selections.isEmpty()) {
            return null;
        }

        Optional<BlockPos> nearest = findNearestBlock(client, selections, Node.PARAMETER_SEARCH_RADIUS);
        if (nearest.isEmpty()) {
            return new ResolvedBlockTarget(null, firstNormalized, null, null);
        }

        BlockPos nearestPos = nearest.get();
        net.minecraft.block.BlockState state = client.world.getBlockState(nearestPos);
        for (BlockSelection selection : selections) {
            if (selection.matches(state)) {
                return new ResolvedBlockTarget(nearestPos, selection.asString(), selection.getBlock(), null);
            }
        }

        BlockSelection firstSelection = selections.get(0);
        return new ResolvedBlockTarget(nearestPos, firstSelection.asString(), firstSelection.getBlock(), null);
    }

    private BlockPos resolveClosestCoordinateTarget(String xName, String yName, String zName,
                                                    int defaultX, int defaultY, int defaultZ) {
        List<Integer> xs = resolveCoordinateCandidates(xName, defaultX);
        List<Integer> ys = resolveCoordinateCandidates(yName, defaultY);
        List<Integer> zs = resolveCoordinateCandidates(zName, defaultZ);
        int candidateCount = Math.max(xs.size(), Math.max(ys.size(), zs.size()));
        if (candidateCount <= 1) {
            return new BlockPos(xs.get(0), ys.get(0), zs.get(0));
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return new BlockPos(xs.get(0), ys.get(0), zs.get(0));
        }

        BlockPos playerPos = client.player.getBlockPos();
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int i = 0; i < candidateCount; i++) {
            BlockPos candidate = new BlockPos(valueAt(xs, i), valueAt(ys, i), valueAt(zs, i));
            double distanceSq = playerPos.getSquaredDistance(candidate);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestPos = candidate;
            }
        }
        return bestPos != null ? bestPos : new BlockPos(xs.get(0), ys.get(0), zs.get(0));
    }

    private List<Integer> resolveCoordinateCandidates(String parameterName, int defaultValue) {
        List<Integer> values = new ArrayList<>();
        if (parameterName == null) {
            values.add(defaultValue);
            return values;
        }

        String rawValue = getStringParameter(parameterName, null);
        List<String> entries = splitMultiValueList(rawValue);
        if (entries.isEmpty()) {
            values.add(getIntParameter(parameterName, defaultValue));
            return values;
        }

        for (String entry : entries) {
            Integer parsed = Node.parseIntOrNull(entry);
            if (parsed == null) {
                try {
                    parsed = (int) Math.round(Double.parseDouble(entry.trim()));
                } catch (NumberFormatException ignored) {
                    parsed = defaultValue;
                }
            }
            values.add(parsed);
        }
        if (values.isEmpty()) {
            values.add(defaultValue);
        }
        return values;
    }

    private int valueAt(List<Integer> values, int index) {
        if (values.isEmpty()) {
            return 0;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return values.get(Math.min(index, values.size() - 1));
    }

    private void failTravelNode(CompletableFuture<Void> future, String message) {
        NodeExecutionCompletion.fail(owner, net.minecraft.client.MinecraftClient.getInstance(), future, message);
    }

    private Optional<BlockPos> findNearestBlock(net.minecraft.client.MinecraftClient client, List<BlockSelection> selections, double range) {
        return owner.findNearestBlock(client, selections, range);
    }

    private void setParameterValueAndPropagate(String name, String value) {
        owner.setParameterValueAndPropagate(name, value);
    }

    private List<String> resolveItemIdsFromParameter(Node parameterNode) {
        return owner.resolveItemIdsFromParameter(parameterNode);
    }

    private String getParameterString(Node node, String name) {
        return Node.getParameterString(node, name);
    }

    private double parseDoubleOrDefault(String value, double defaultValue) {
        return Node.parseDoubleOrDefault(value, defaultValue);
    }

    private Optional<BlockPos> findNearestDroppedItem(net.minecraft.client.MinecraftClient client, Item item, double range) {
        return owner.findNearestDroppedItem(client, item, range);
    }

    private List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        return owner.resolveEntityIdsFromParameter(parameterNode);
    }

    private String getEntityParameterState(Node node) {
        return owner.getEntityParameterState(node);
    }

    private Optional<Entity> findNearestEntity(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, double range, String state) {
        return owner.findNearestEntity(client, entityType, range, state);
    }

    private boolean isSelfPlayerValue(String value) {
        return Node.isSelfPlayerValue(value);
    }

    private boolean isAnyPlayerValue(String value) {
        return Node.isAnyPlayerValue(value);
    }

    private Optional<AbstractClientPlayerEntity> findNearestPlayer(net.minecraft.client.MinecraftClient client, AbstractClientPlayerEntity reference) {
        return Node.findNearestPlayer(client, reference);
    }

    private Node getOwningStartNode() {
        return owner.getOwningStartNode();
    }
}
