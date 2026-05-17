package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.PlayerInventoryBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Method;

import org.lwjgl.glfw.GLFW;

final class NodeEntityActionCommandExecutor {
    private static final Method DO_ATTACK_METHOD = resolveDoAttackMethod();
    private static final Method SYNC_SELECTED_SLOT_METHOD = resolveSyncSelectedSlotMethod();

    private final Node owner;

    NodeEntityActionCommandExecutor(Node owner) {
        this.owner = owner;
    }
    void executeInteractCommand(java.util.concurrent.CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);
        boolean preferEntity = owner.getBooleanParameter("PreferEntity", true);
        boolean preferBlock = owner.getBooleanParameter("PreferBlock", true);
        boolean fallbackToItem = owner.getBooleanParameter("FallbackToItemUse", true);
        boolean swingOnSuccess = owner.getBooleanParameter("SwingOnSuccess", true);
        boolean sneakWhileInteracting = owner.getBooleanParameter("SneakWhileInteracting", false);
        boolean restoreSneak = owner.getBooleanParameter("RestoreSneakState", true);

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhileInteracting) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        Runnable restoreSneakState = () -> {
            if (sneakWhileInteracting && restoreSneak) {
                client.player.setSneaking(previousSneak);
                if (client.options != null && client.options.sneakKey != null) {
                    client.options.sneakKey.setPressed(previousSneak);
                }
            }
        };

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        BlockPos parameterTargetPos = parameterData != null ? parameterData.targetBlockPos : null;

        NodeParameter blockParameter = owner.getParameter("Block");
        String configuredBlockId = null;
        String requestedBlockLabel = null;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                configuredBlockId = parameterData.targetBlockId;
            } else if (parameterData.targetBlockIds != null && !parameterData.targetBlockIds.isEmpty()) {
                configuredBlockId = parameterData.targetBlockIds.get(0);
            }
        }
        if (blockParameter != null) {
            String value = blockParameter.getStringValue();
            if (value != null && !value.trim().isEmpty()) {
                configuredBlockId = value.trim();
                requestedBlockLabel = value.trim();
            }
        }
        if (requestedBlockLabel == null) {
            requestedBlockLabel = configuredBlockId;
        }

        String configuredBlockSelection = configuredBlockId;

        Block targetBlock = null;
        if (configuredBlockId != null && !configuredBlockId.isEmpty()) {
            String sanitized = owner.sanitizeResourceId(configuredBlockId);
            String normalized = owner.normalizeResourceId(sanitized, "minecraft");
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                restoreSneakState.run();
                String label = requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : configuredBlockId;
                owner.sendNodeErrorMessage(client, "Cannot interact with \"" + label + "\": unknown block identifier.");
                future.complete(null);
                return;
            }
            targetBlock = Registries.BLOCK.get(identifier);
            configuredBlockId = identifier.toString();
            owner.setParameterValueAndPropagate("Block", configuredBlockId);
        }

        // Check for entity parameter
        String configuredEntityId = null;
        Entity targetEntity = null;
        if (parameterData != null) {
            if (parameterData.targetEntity != null) {
                targetEntity = parameterData.targetEntity;
            }
            if (parameterData.targetEntityId != null && !parameterData.targetEntityId.isEmpty()) {
                configuredEntityId = parameterData.targetEntityId;
            }
        }

        if (targetEntity == null && configuredEntityId != null && !configuredEntityId.isEmpty()) {
            String sanitizedEntity = owner.sanitizeResourceId(configuredEntityId);
            String normalizedEntity = owner.normalizeResourceId(sanitizedEntity, "minecraft");
            Identifier entityIdentifier = Identifier.tryParse(normalizedEntity);

            if (entityIdentifier == null || !Registries.ENTITY_TYPE.containsId(entityIdentifier)) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, "Cannot interact with \"" + configuredEntityId + "\": unknown entity identifier.");
                future.complete(null);
                return;
            }

            EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityIdentifier);
            Optional<Entity> nearestEntity = owner.findNearestEntity(client, entityType, Node.PARAMETER_SEARCH_RADIUS);

            if (!nearestEntity.isPresent()) {
                restoreSneakState.run();
                String entityName = configuredEntityId.replace("minecraft:", "").replace("_", " ");
                owner.sendNodeErrorMessage(client, "No " + entityName + " nearby to interact with.");
                future.complete(null);
                return;
            }

            targetEntity = nearestEntity.get();
        }

        if (targetEntity != null) {
            // Check distance
            if (targetEntity.squaredDistanceTo(client.player.getEyePos()) > Node.DEFAULT_REACH_DISTANCE_SQUARED) {
                restoreSneakState.run();
                String entityName = configuredEntityId != null
                    ? configuredEntityId.replace("minecraft:", "").replace("_", " ")
                    : String.valueOf(Registries.ENTITY_TYPE.getId(targetEntity.getType()))
                        .replace("minecraft:", "")
                        .replace("_", " ");
                owner.sendNodeErrorMessage(client, entityName + " is too far away to interact with.");
                future.complete(null);
                return;
            }
        }

        HitResult target = client.crosshairTarget;
        ActionResult result = ActionResult.PASS;
        boolean attemptedInteraction = false;

        // If an entity parameter is specified, interact with it first
        if (targetEntity != null) {
            result = client.interactionManager.interactEntity(client.player, targetEntity, hand);
            attemptedInteraction = true;
        }

        if (!attemptedInteraction && (targetBlock != null || parameterTargetPos != null)) {
            BlockPos targetPos = parameterTargetPos;
            if (targetPos == null && targetBlock != null) {
                String selectionSource = configuredBlockSelection != null && !configuredBlockSelection.isEmpty()
                    ? configuredBlockSelection
                    : configuredBlockId;
                List<BlockSelection> selections = new ArrayList<>();
                if (selectionSource != null && !selectionSource.isEmpty()) {
                    BlockSelection.parse(selectionSource).ifPresent(selections::add);
                }
                Optional<BlockPos> nearest = owner.findNearestBlock(client, selections, Node.PARAMETER_SEARCH_RADIUS);
                if (nearest.isPresent()) {
                    targetPos = nearest.get();
                }
            }
            if (targetPos == null) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, name + " is not nearby for " + owner.getType().getDisplayName() + ".");
                future.complete(null);
                return;
            }

            BlockState state = client.world.getBlockState(targetPos);
            if (state.isAir()) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, name + " is missing for " + owner.getType().getDisplayName() + ".");
                future.complete(null);
                return;
            }

            if (targetBlock == null) {
                targetBlock = state.getBlock();
                Identifier stateId = Registries.BLOCK.getId(targetBlock);
                if (stateId != null) {
                    owner.setParameterValueAndPropagate("Block", stateId.toString());
                }
            }

            if (targetBlock != null && !state.isOf(targetBlock)) {
                String name = targetBlock.getName().getString();
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, name + " is not nearby for " + owner.getType().getDisplayName() + ".");
                future.complete(null);
                return;
            }

            String blockDisplayName = targetBlock.getName().getString();

            Vec3d eyePos = client.player.getEyePos();
            Vec3d hitVec = Vec3d.ofCenter(targetPos);
            if (eyePos.squaredDistanceTo(hitVec) > Node.DEFAULT_REACH_DISTANCE_SQUARED) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, blockDisplayName + " is too far away to interact with.");
                future.complete(null);
                return;
            }

            Direction facing = Direction.getFacing(hitVec.x - eyePos.x, hitVec.y - eyePos.y, hitVec.z - eyePos.z);
            BlockHitResult manualHit = new BlockHitResult(hitVec, facing == null ? Direction.UP : facing, targetPos, false);
            target = manualHit;
            result = client.interactionManager.interactBlock(client.player, hand, manualHit);
            attemptedInteraction = true;
        }

        if (!attemptedInteraction && preferEntity && target instanceof EntityHitResult entityHit) {
            result = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || !result.isAccepted()) && preferBlock && target instanceof BlockHitResult blockHit) {
            result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || (!result.isAccepted() && result != ActionResult.PASS)) && fallbackToItem) {
            result = client.interactionManager.interactItem(client.player, hand);
        }

        if (swingOnSuccess && (result.isAccepted() || result == ActionResult.PASS)) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        restoreSneakState.run();
        future.complete(null);
    }
    void executeBreakCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        Node parameterNode = owner.getAttachedParameter(0);
        if (parameterNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, "Break requires a block or coordinate parameter.");
            return;
        }

        BlockPos targetPos = null;
        Direction breakFace = null;
        if (owner.runtimeState().runtimeParameterData != null) {
            if (owner.runtimeState().runtimeParameterData.targetBlockPos != null) {
                targetPos = owner.runtimeState().runtimeParameterData.targetBlockPos;
            } else if (owner.runtimeState().runtimeParameterData.targetVector != null) {
                Vec3d vec = owner.runtimeState().runtimeParameterData.targetVector;
                targetPos = new BlockPos(MathHelper.floor(vec.x), MathHelper.floor(vec.y), MathHelper.floor(vec.z));
            }
        }

        if (targetPos == null && owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
            List<BlockSelection> selections = owner.resolveBlocksFromParameter(parameterNode);
            if (selections.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future, "No block selected for Break.");
                return;
            }
            Optional<BlockHitResult> currentHit = owner.getCurrentBlockHitResult();
            if (currentHit.isPresent()) {
                BlockHitResult blockHit = currentHit.get();
                BlockPos hitPos = blockHit.getBlockPos();
                if (hitPos != null) {
                    BlockState hitState = client.world.getBlockState(hitPos);
                    boolean matches = false;
                    for (BlockSelection selection : selections) {
                        if (selection.matches(hitState)) {
                            matches = true;
                            break;
                        }
                    }
                    if (matches) {
                        targetPos = hitPos;
                        breakFace = blockHit.getSide();
                    }
                }
            }
            if (targetPos == null) {
                Optional<BlockPos> nearest = owner.findNearestBlock(client, selections, Math.sqrt(Node.DEFAULT_REACH_DISTANCE_SQUARED));
                if (nearest.isPresent()) {
                    targetPos = nearest.get();
                }
            }
        }

        if (targetPos == null) {
            NodeExecutionCompletion.fail(owner, client, future, "No matching block found in reach for Break.");
            return;
        }

        if (owner.runtimeState().runtimeParameterData == null) {
            owner.runtimeState().runtimeParameterData = new RuntimeParameterData();
        }
        owner.runtimeState().runtimeParameterData.targetBlockPos = targetPos;

        Vec3d eyePos = client.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(targetPos);
        if (eyePos.squaredDistanceTo(center) > Node.DEFAULT_REACH_DISTANCE_SQUARED) {
            NodeExecutionCompletion.fail(owner, client, future, "Target block is out of reach.");
            return;
        }

        if (breakFace == null) {
            Vec3d delta = center.subtract(eyePos);
            breakFace = Direction.getFacing(delta.x, delta.y, delta.z);
            if (breakFace == null) {
                breakFace = Direction.UP;
            }
        }

        BlockState state = client.world.getBlockState(targetPos);
        if (state.isAir()) {
            NodeExecutionCompletion.complete(future);
            return;
        }
        float delta = state.calcBlockBreakingDelta(client.player, client.world, targetPos);
        if (delta <= 0.0F) {
            NodeExecutionCompletion.fail(owner, client, future, "Block cannot be broken.");
            return;
        }
        int ticksToBreak = Math.max(1, (int) Math.ceil(1.0F / delta));

        Direction finalBreakFace = breakFace;
        BlockPos finalTargetPos = targetPos;
        new Thread(() -> {
            try {
                owner.runOnClientThread(client, () -> {
                    owner.orientPlayerTowardsRuntimeTarget(client, owner.runtimeState().runtimeParameterData);
                    if (client.interactionManager != null) {
                        client.interactionManager.attackBlock(finalTargetPos, finalBreakFace);
                    }
                    client.player.swingHand(Hand.MAIN_HAND);
                });

                for (int i = 0; i < ticksToBreak; i++) {
                    Thread.sleep(50L);
                    Boolean isAir = owner.supplyFromClient(client,
                        () -> client.world == null || client.world.getBlockState(finalTargetPos).isAir());
                    if (Boolean.TRUE.equals(isAir)) {
                        break;
                    }
                    owner.runOnClientThread(client, () -> {
                        if (client.interactionManager != null) {
                            client.interactionManager.updateBlockBreakingProgress(finalTargetPos, finalBreakFace);
                        }
                    });
                }

                owner.runOnClientThread(client, () -> {
                    if (client.player != null && client.player.networkHandler != null) {
                        client.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                            finalTargetPos,
                            finalBreakFace
                        ));
                    }
                });
                NodeExecutionCompletion.complete(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
        }, "Pathmind-Break").start();
    }
    void executeTradeCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        owner.ensureVillagerTradeNumberParameter();

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        // Check if a merchant screen is open
        net.minecraft.client.gui.screen.Screen currentScreen = client.currentScreen;
        if (!(currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen)) {
            NodeExecutionCompletion.fail(owner, client, future, "No villager trading screen is open.");
            return;
        }

        net.minecraft.client.gui.screen.ingame.MerchantScreen merchantScreen =
            (net.minecraft.client.gui.screen.ingame.MerchantScreen) currentScreen;

        // Get the screen handler from merchant screen
        net.minecraft.screen.MerchantScreenHandler screenHandler = merchantScreen.getScreenHandler();
        if (screenHandler == null) {
            NodeExecutionCompletion.fail(owner, client, future, "Cannot access merchant screen handler.");
            return;
        }

        // Get the trade offers
        net.minecraft.village.TradeOfferList tradeOffers = screenHandler.getRecipes();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, "No trades available from this villager.");
            return;
        }
        int selectedTradeNumber = owner.getConfiguredVillagerTradeNumber();
        int tradeIndex = selectedTradeNumber - 1;
        if (tradeIndex < 0 || tradeIndex >= tradeOffers.size() || tradeOffers.get(tradeIndex) == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Trade #" + selectedTradeNumber + " is not available.");
            return;
        }
        net.minecraft.village.TradeOffer selectedOffer = tradeOffers.get(tradeIndex);
        if (selectedOffer.isDisabled()) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Trade #" + selectedTradeNumber + " is out of stock.");
            return;
        }
        if (!canAffordTrade(client.player, screenHandler, selectedOffer)) {
            NodeExecutionCompletion.fail(owner, client, future,
                "Not enough items for trade #" + selectedTradeNumber + ".");
            return;
        }
        List<Integer> preferredTradeIndexes = Collections.singletonList(tradeIndex);

        int tradesToExecute = owner.getConfiguredVillagerTradeCount();

        new Thread(() -> {
            try {
                int remainingTrades = tradesToExecute;
                while (remainingTrades > 0) {
                    boolean tradedThisPass = false;
                    boolean anyMatchStillAvailable = false;
                    for (Integer preferredTradeIndex : preferredTradeIndexes) {
                        if (preferredTradeIndex == null || preferredTradeIndex < 0 || preferredTradeIndex >= tradeOffers.size()) {
                            continue;
                        }
                        net.minecraft.village.TradeOffer offer = tradeOffers.get(preferredTradeIndex);
                        if (offer == null) {
                            continue;
                        }
                        if (!offer.isDisabled()) {
                            anyMatchStillAvailable = true;
                        }
                        int executableTrades = getMaxExecutableTradeCount(client.player, screenHandler, offer);
                        if (executableTrades <= 0) {
                            continue;
                        }

                        int batchSize = Math.min(remainingTrades, executableTrades);
                        selectMerchantTrade(client, screenHandler, preferredTradeIndex);
                        Thread.sleep(60);

                        int completedInBatch = 0;
                        for (int i = 0; i < batchSize; i++) {
                            if (offer.isDisabled() || !canAffordTrade(client.player, screenHandler, offer)) {
                                break;
                            }
                            if (!quickMoveMerchantTradeResult(client, screenHandler)) {
                                break;
                            }
                            completedInBatch++;
                            remainingTrades--;
                            tradedThisPass = true;
                            if (remainingTrades <= 0) {
                                break;
                            }
                            Thread.sleep(70);
                        }

                        if (completedInBatch > 0 && remainingTrades > 0) {
                            Thread.sleep(120);
                        }
                        if (remainingTrades <= 0) {
                            break;
                        }
                    }

                    if (!tradedThisPass) {
                        if (!anyMatchStillAvailable) {
                            owner.sendNodeErrorMessage(client, "All matching trades are out of stock.");
                        } else {
                            owner.sendNodeErrorMessage(client, "Not enough items to complete the requested trades.");
                        }
                        break;
                    }
                }

                NodeExecutionCompletion.complete(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
        }, "Pathmind-Trade").start();
    }

    private void selectMerchantTrade(net.minecraft.client.MinecraftClient client,
                                     net.minecraft.screen.MerchantScreenHandler screenHandler,
                                     int tradeIndex) throws InterruptedException {
        owner.runOnClientThread(client, () -> {
            screenHandler.setRecipeIndex(tradeIndex);
            screenHandler.switchTo(tradeIndex);
            if (client.player != null && client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket(tradeIndex)
                );
            }
        });
    }

    private boolean quickMoveMerchantTradeResult(net.minecraft.client.MinecraftClient client,
                                                 net.minecraft.screen.MerchantScreenHandler screenHandler) throws InterruptedException {
        if (client == null || client.player == null || client.interactionManager == null || screenHandler == null) {
            return false;
        }
        final boolean[] moved = {false};
        owner.runOnClientThread(client, () -> {
            final int outputSlot = 2;
            net.minecraft.screen.slot.Slot output = screenHandler.getSlot(outputSlot);
            if (output == null) {
                return;
            }
            net.minecraft.item.ItemStack outputStack = output.getStack();
            if (outputStack == null || outputStack.isEmpty()) {
                return;
            }
            client.interactionManager.clickSlot(
                screenHandler.syncId,
                outputSlot,
                0,
                net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,
                client.player
            );
            moved[0] = true;
        });
        return moved[0];
    }

    private int getMaxExecutableTradeCount(net.minecraft.entity.player.PlayerEntity player,
                                           net.minecraft.screen.MerchantScreenHandler screenHandler,
                                           net.minecraft.village.TradeOffer offer) {
        if (player == null || screenHandler == null || offer == null || offer.isDisabled()) {
            return 0;
        }

        int maxTrades = Integer.MAX_VALUE;
        net.minecraft.item.ItemStack firstBuyItem = getRequiredFirstBuyItem(offer);
        if (!firstBuyItem.isEmpty()) {
            int required = Math.max(1, firstBuyItem.getCount());
            int available = countAvailableForTrade(player.getInventory(), screenHandler, firstBuyItem);
            maxTrades = Math.min(maxTrades, available / required);
        }

        net.minecraft.item.ItemStack secondBuyItem = getRequiredSecondBuyItem(offer);
        if (!secondBuyItem.isEmpty()) {
            int required = Math.max(1, secondBuyItem.getCount());
            int available = countAvailableForTrade(player.getInventory(), screenHandler, secondBuyItem);
            maxTrades = Math.min(maxTrades, available / required);
        }

        maxTrades = Math.min(maxTrades, Math.max(0, offer.getMaxUses() - offer.getUses()));
        return maxTrades == Integer.MAX_VALUE ? 0 : Math.max(0, maxTrades);
    }

    boolean canAffordTrade(net.minecraft.entity.player.PlayerEntity player,
                           net.minecraft.screen.MerchantScreenHandler screenHandler,
                           net.minecraft.village.TradeOffer offer) {
        if (player == null || offer == null || screenHandler == null) {
            return false;
        }

        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        net.minecraft.item.ItemStack firstBuyItem = getRequiredFirstBuyItem(offer);
        if (!firstBuyItem.isEmpty()) {
            int required = firstBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, firstBuyItem);
            if (available < required) {
                return false;
            }
        }

        net.minecraft.item.ItemStack secondBuyItem = getRequiredSecondBuyItem(offer);
        if (!secondBuyItem.isEmpty()) {
            int required = secondBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, secondBuyItem);
            if (available < required) {
                return false;
            }
        }

        return true;
    }

    private static net.minecraft.item.ItemStack getRequiredFirstBuyItem(net.minecraft.village.TradeOffer offer) {
        return offer == null ? net.minecraft.item.ItemStack.EMPTY : offer.getDisplayedFirstBuyItem();
    }

    private static net.minecraft.item.ItemStack getRequiredSecondBuyItem(net.minecraft.village.TradeOffer offer) {
        return offer == null ? net.minecraft.item.ItemStack.EMPTY : offer.getDisplayedSecondBuyItem();
    }

    static int getRequiredFirstBuyCountForTests(net.minecraft.village.TradeOffer offer) {
        if (offer == null) {
            return 0;
        }
        return resolveRequiredTradeCount(
            getRequiredFirstBuyItem(offer).getCount(),
            offer.getFirstBuyItem().itemStack().getCount()
        );
    }

    static int getRequiredSecondBuyCountForTests(net.minecraft.village.TradeOffer offer) {
        if (offer == null) {
            return 0;
        }
        java.util.Optional<net.minecraft.village.TradedItem> secondBuyItem = offer.getSecondBuyItem();
        int originalCount = secondBuyItem.map(item -> item.itemStack().getCount()).orElse(0);
        return resolveRequiredTradeCount(getRequiredSecondBuyItem(offer).getCount(), originalCount);
    }

    static int resolveRequiredTradeCountForTests(int displayedCount, int originalCount) {
        return resolveRequiredTradeCount(displayedCount, originalCount);
    }

    private static int resolveRequiredTradeCount(int displayedCount, int originalCount) {
        return displayedCount > 0 ? displayedCount : Math.max(0, originalCount);
    }

    private int countAvailableForTrade(net.minecraft.entity.player.PlayerInventory inventory,
                                       net.minecraft.screen.MerchantScreenHandler screenHandler,
                                       net.minecraft.item.ItemStack requiredStack) {
        int available = 0;
        for (int i = 0; i < inventory.size(); i++) {
            net.minecraft.item.ItemStack stack = inventory.getStack(i);
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        // Include items already moved into merchant input slots (0 and 1).
        for (int slotIndex = 0; slotIndex <= 1; slotIndex++) {
            net.minecraft.item.ItemStack stack = screenHandler.getSlot(slotIndex).getStack();
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        net.minecraft.item.ItemStack cursorStack = screenHandler.getCursorStack();
        if (net.minecraft.item.ItemStack.areItemsEqual(cursorStack, requiredStack)) {
            available += cursorStack.getCount();
        }

        return available;
    }
    void executeSwingCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);
        boolean holdDurationEnabled = owner.isAmountInputEnabled();
        double durationSeconds = holdDurationEnabled
            ? Math.max(0.0, owner.getDoubleParameter("Duration", 0.0))
            : 0.0;
        int legacyCount = Math.max(1, owner.getIntParameter("Count", 1));
        double legacyIntervalSeconds = Math.max(0.0, owner.getDoubleParameter("IntervalSeconds", 0.0));

        new Thread(() -> {
            boolean releaseAttackKey = false;
            try {
                if (holdDurationEnabled && durationSeconds > 0.0) {
                    if (hand == Hand.MAIN_HAND) {
                        long durationMs = (long) Math.ceil(durationSeconds * 1000.0);
                        long deadline = System.currentTimeMillis() + durationMs;
                        owner.runOnClientThread(client, () -> {
                            syncSelectedHotbarSlot(client);
                            performMainHandAttack(client);
                            if (client.options != null && client.options.attackKey != null) {
                                client.options.attackKey.setPressed(true);
                            }
                        });
                        releaseAttackKey = true;
                        while (System.currentTimeMillis() < deadline) {
                            if (owner.shouldAbortForRepeatUntilGuard()) {
                                break;
                            }
                            long remainingMs = deadline - System.currentTimeMillis();
                            Thread.sleep(Math.min(Node.CONTROL_POLL_INTERVAL_MS, Math.max(1L, remainingMs)));
                        }
                    } else {
                        long durationMs = (long) Math.ceil(durationSeconds * 1000.0);
                        long deadline = System.currentTimeMillis() + durationMs;
                        boolean swung = false;
                        while (!swung || System.currentTimeMillis() < deadline) {
                            owner.runOnClientThread(client, () -> {
                                client.player.swingHand(hand);
                                if (client.player.networkHandler != null) {
                                    client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                                }
                            });
                            swung = true;
                            if (owner.shouldAbortForRepeatUntilGuard()) {
                                break;
                            }
                            long remainingMs = deadline - System.currentTimeMillis();
                            if (remainingMs <= 0L) {
                                break;
                            }
                            Thread.sleep(Math.min(50L, remainingMs));
                        }
                    }
                } else {
                    for (int i = 0; i < legacyCount; i++) {
                        owner.runOnClientThread(client, () -> {
                            if (hand == Hand.MAIN_HAND) {
                                syncSelectedHotbarSlot(client);
                                performMainHandAttack(client);
                            } else {
                                client.player.swingHand(hand);
                                if (client.player.networkHandler != null) {
                                    client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                                }
                            }
                        });

                        if (legacyIntervalSeconds > 0.0 && i < legacyCount - 1) {
                            Thread.sleep((long) (legacyIntervalSeconds * 1000));
                        }
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } finally {
                if (releaseAttackKey) {
                    try {
                        owner.runOnClientThread(client, () -> {
                            if (client.options != null && client.options.attackKey != null) {
                                client.options.attackKey.setPressed(false);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "Pathmind-Swing").start();
    }

    static Method resolveDoAttackMethod() {
        try {
            return net.minecraft.client.MinecraftClient.class.getMethod("doAttack");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static Method resolveSyncSelectedSlotMethod() {
        try {
            return net.minecraft.client.network.ClientPlayerInteractionManager.class.getMethod("syncSelectedSlot");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static void syncSelectedHotbarSlot(MinecraftClient client) {
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

    static void performMainHandAttack(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        InputUtil.Key attackKey = InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        KeyBinding.onKeyPressed(attackKey);
        try {
            if (DO_ATTACK_METHOD != null) {
                DO_ATTACK_METHOD.invoke(client);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the direct attack logic below.
        }
        if (client.interactionManager != null) {
            HitResult target = client.crosshairTarget;
            if (target instanceof EntityHitResult entityHit) {
                client.interactionManager.attackEntity(client.player, entityHit.getEntity());
                return;
            }
        }
        client.player.swingHand(Hand.MAIN_HAND);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }
    void executeEquipArmorCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = owner.clampInventorySlot(inventory, owner.getIntParameter("SourceSlot", 0));
        EquipmentSlot equipmentSlot = parseEquipmentSlot(owner.getParameter("ArmorSlot"), EquipmentSlot.HEAD);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack current = client.player.getEquippedStack(equipmentSlot);
        inventory.setStack(sourceSlot, current);
        client.player.equipStack(equipmentSlot, sourceStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    void executeEquipHandCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = owner.clampInventorySlot(inventory, owner.getIntParameter("SourceSlot", 0));
        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack handStack = client.player.getStackInHand(hand);
        client.player.setStackInHand(hand, sourceStack);
        inventory.setStack(sourceSlot, handStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }

    private EquipmentSlot parseEquipmentSlot(NodeParameter parameter, EquipmentSlot defaultSlot) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter.getStringValue().trim().toLowerCase(java.util.Locale.ROOT);
        switch (value) {
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            default:
                return defaultSlot;
        }
    }
}
