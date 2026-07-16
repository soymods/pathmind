package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class NodeCollectCommandExecutor {
    private final Node owner;
    private final NodeMode mode;
    private final NodeRuntimeState runtimeState;

    NodeCollectCommandExecutor(Node owner) {
        this.owner = owner;
        this.mode = owner.getMode();
        this.runtimeState = owner.runtimeState();
    }

    void executeCollectCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("No mode set for COLLECT node"));
            return;
        }

        List<String> targets = resolveCollectTargets(future);
        if (targets.isEmpty()) {
            return;
        }

        if (!owner.isBaritoneApiAvailable() && owner.isBaritoneModAvailable()) {
            switch (mode) {
                case COLLECT_SINGLE: {
                    int amount = Math.max(1, owner.getIntParameter("Amount", 1));
                    if (hasRequiredBlockAlready(targets.get(0), amount)) {
                        NodeExecutionCompletion.complete(future);
                        return;
                    }
                    String command = "#mine " + targets.get(0);
                    if (amount > 1) {
                        command += " " + amount;
                    }
                    owner.executeCommand(command);
                    NodeExecutionCompletion.complete(future);
                    return;
                }
                case COLLECT_MULTIPLE:
                    owner.executeCommand("#mine " + String.join(" ", targets));
                    NodeExecutionCompletion.complete(future);
                    return;
                default:
                    NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Unknown COLLECT mode: " + mode));
                    return;
            }
        }

        Object baritone = owner.getBaritone();
        if (baritone == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Baritone not available"));
            return;
        }
        Object mineProcess = BaritoneApiProxy.getMineProcess(baritone);
        if (mineProcess == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Mine process not available"));
            return;
        }

        Optional<BlockPos> preferredCollectTarget = findNearestCollectTarget(targets);

        switch (mode) {
            case COLLECT_SINGLE: {
                int amount = Math.max(1, owner.getIntParameter("Amount", 1));
                if (hasRequiredBlockAlready(targets.get(0), amount)) {
                    NodeExecutionCompletion.complete(future);
                    return;
                }
                if (amount == 1 && preferredCollectTarget.isPresent()) {
                    BlockPos targetPos = preferredCollectTarget.get();
                    startCollectWithPreferredTarget(baritone, mineProcess, targetPos, future,
                        () -> breakSpecificBlockForCollect(targetPos, future));
                    break;
                }
                startCollectWithPreferredTarget(baritone, mineProcess, preferredCollectTarget.orElse(null), future, () -> {
                    owner.resetBaritonePathing(baritone, mineProcess);
                    owner.navigationCommandExecutor().applyBaritoneCacheGuardsDuringMovement(future, false, true);
                    PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_COLLECT, future);
                    CompletableFuture.runAsync(() -> {
                        try {
                            BaritoneApiProxy.mineByName(mineProcess, amount, targets.toArray(new String[0]));
                        } catch (Exception ignored) {
                        }
                    });
                });
                break;
            }
            case COLLECT_MULTIPLE:
                startCollectWithPreferredTarget(baritone, mineProcess, preferredCollectTarget.orElse(null), future, () -> {
                    owner.resetBaritonePathing(baritone, mineProcess);
                    owner.navigationCommandExecutor().applyBaritoneCacheGuardsDuringMovement(future, false, true);
                    PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_COLLECT, future);
                    CompletableFuture.runAsync(() -> {
                        try {
                            BaritoneApiProxy.mineByName(mineProcess, targets.toArray(new String[0]));
                        } catch (Exception ignored) {
                        }
                    });
                });
                break;
            default:
                NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Unknown COLLECT mode: " + mode));
                break;
        }
    }

    private List<String> resolveCollectTargets(CompletableFuture<Void> future) {
        List<String> blockIds = new ArrayList<>();

        if (runtimeState.runtimeParameterData != null) {
            if (runtimeState.runtimeParameterData.targetBlockIds != null) {
                for (String id : runtimeState.runtimeParameterData.targetBlockIds) {
                    addBlockIds(blockIds, id);
                }
            } else if (runtimeState.runtimeParameterData.targetBlockId != null) {
                addBlockIds(blockIds, runtimeState.runtimeParameterData.targetBlockId);
            }
        }

        addBlockIds(blockIds, owner.getStringParameter("Block", null));
        addBlockIds(blockIds, owner.getStringParameter("Blocks", null));

        if (blockIds.isEmpty()) {
            owner.sendParameterSearchFailure(tr("pathmind.error.noBlockTypesSpecified", owner.getType().getDisplayName()), future);
            return Collections.emptyList();
        }

        List<String> targets = new ArrayList<>();
        for (String idString : blockIds) {
            Identifier identifier = Identifier.tryParse(idString);
            if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
                owner.sendParameterSearchFailure(tr("pathmind.error.unknownBlockForNode", idString, owner.getType().getDisplayName()), future);
                return Collections.emptyList();
            }
            targets.add(identifier.toString());
        }

        return targets;
    }

    private void addBlockIds(List<String> blockIds, String rawValue) {
        if (rawValue == null) {
            return;
        }
        for (String entry : rawValue.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Identifier identifier = BlockSelection.extractBlockIdentifier(trimmed);
            if (identifier != null) {
                String normalized = identifier.toString();
                if (!blockIds.contains(normalized)) {
                    blockIds.add(normalized);
                }
            } else if (!blockIds.contains(trimmed)) {
                blockIds.add(trimmed);
            }
        }
    }

    private Optional<BlockPos> findNearestCollectTarget(List<String> targets) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || targets == null || targets.isEmpty()) {
            return Optional.empty();
        }

        List<BlockSelection> selections = new ArrayList<>();
        for (String target : targets) {
            if (target == null || target.isEmpty()) {
                continue;
            }
            BlockSelection.parse(target).ifPresent(selections::add);
        }
        if (selections.isEmpty()) {
            return Optional.empty();
        }

        return owner.findNearestBlock(client, selections, Node.PARAMETER_SEARCH_RADIUS);
    }

    private boolean isPlayerNearBlock(Minecraft client, BlockPos pos, double rangeSq) {
        if (client == null || client.player == null || pos == null) {
            return false;
        }
        double distanceSq = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return distanceSq <= rangeSq;
    }

    private void startCollectWithPreferredTarget(Object baritone,
                                                 Object mineProcess,
                                                 BlockPos preferredTarget,
                                                 CompletableFuture<Void> future,
                                                 Runnable startMiningAction) {
        Minecraft client = Minecraft.getInstance();
        Object customGoalProcess = baritone != null ? BaritoneApiProxy.getCustomGoalProcess(baritone) : null;
        if (client == null || client.player == null || preferredTarget == null || customGoalProcess == null
            || isPlayerNearBlock(client, preferredTarget, 6.25D)) {
            startMiningAction.run();
            return;
        }

        owner.resetBaritonePathing(baritone, mineProcess);

        CompletableFuture<Void> approachFuture = new CompletableFuture<>();
        owner.navigationCommandExecutor().applyBaritoneCacheGuardsDuringMovement(approachFuture, false, false);
        owner.navigationCommandExecutor().applyBaritoneMovementGuardsDuringGoto(approachFuture);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, approachFuture);
        Object goal = BaritoneApiProxy.createGoalNear(preferredTarget, 1);
        BaritoneApiProxy.setGoalAndPath(customGoalProcess, goal);

        approachFuture.whenComplete((ignored, throwable) -> {
            if (future.isDone()) {
                return;
            }
            if (throwable != null) {
                NodeExecutionCompletion.completeExceptionally(future, throwable instanceof RuntimeException
                    ? (RuntimeException) throwable
                    : new RuntimeException(throwable));
                return;
            }
            startMiningAction.run();
        });
    }

    private void breakSpecificBlockForCollect(BlockPos targetPos, CompletableFuture<Void> future) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || targetPos == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }
        runtimeState.runtimeParameterData.targetBlockPos = targetPos;

        Vec3 eyePos = client.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(targetPos);
        if (eyePos.distanceToSqr(center) > Node.getBlockInteractionReachSquared(client)) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.targetBlockOutOfReach"));
            return;
        }

        Direction breakFace = Direction.getApproximateNearest(center.x - eyePos.x, center.y - eyePos.y, center.z - eyePos.z);
        if (breakFace == null) {
            breakFace = Direction.UP;
        }

        BlockState state = client.level.getBlockState(targetPos);
        if (state.isAir()) {
            NodeExecutionCompletion.complete(future);
            return;
        }

        float delta = state.getDestroyProgress(client.player, client.level, targetPos);
        if (delta <= 0.0F) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.blockCannotBeBroken"));
            return;
        }
        int ticksToBreak = Math.max(1, (int) Math.ceil(1.0F / delta));

        Direction finalBreakFace = breakFace;
        new Thread(() -> {
            try {
                owner.runOnClientThread(client, () -> {
                    owner.orientPlayerTowardsRuntimeTarget(client, runtimeState.runtimeParameterData);
                    if (client.gameMode != null) {
                        client.gameMode.startDestroyBlock(targetPos, finalBreakFace);
                    }
                    client.player.swing(InteractionHand.MAIN_HAND);
                });

                for (int i = 0; i < ticksToBreak; i++) {
                    Thread.sleep(50L);
                    Boolean isAir = owner.supplyFromClient(client,
                        () -> client.level == null || client.level.getBlockState(targetPos).isAir());
                    if (Boolean.TRUE.equals(isAir)) {
                        break;
                    }
                    owner.runOnClientThread(client, () -> {
                        if (client.gameMode != null) {
                            client.gameMode.continueDestroyBlock(targetPos, finalBreakFace);
                        }
                    });
                }

                owner.runOnClientThread(client, () -> {
                    if (client.player != null && client.player.connection != null) {
                        client.player.connection.send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            targetPos,
                            finalBreakFace
                        ));
                    }
                });
                NodeExecutionCompletion.complete(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
        }, "Pathmind-Collect-Break").start();
    }

    private boolean hasRequiredBlockAlready(String blockId, int required) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || blockId == null) {
            return false;
        }
        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return false;
        }
        int count = client.player.getInventory().countItem(item);
        if (count >= required) {
            owner.sendNodeInfoMessage(client, "Already have " + count + " " + blockId + ", skipping mine.");
            return true;
        }
        return false;
    }
}
