package com.pathmind.nodes;

import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.PlayerInventoryBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class NodeWorldActionCommandExecutor {
    private final Node owner;

    NodeWorldActionCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeUseCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);
        int configuredCount = Math.max(0, owner.getIntParameter("RepeatCount", 1));
        boolean useUntilEmpty = owner.getBooleanParameter("UseUntilEmpty", false);
        boolean stopIfUnavailable = owner.getBooleanParameter("StopIfUnavailable", true);
        boolean holdDurationEnabled = owner.isAmountInputEnabled();
        double durationSeconds = holdDurationEnabled
            ? Math.max(0.0, owner.getDoubleParameter("UseDurationSeconds", 0.0))
            : 0.0;
        double intervalSeconds = Math.max(0.0, owner.getDoubleParameter("UseIntervalSeconds", 0.0));
        boolean allowBlock = owner.getBooleanParameter("AllowBlockInteraction", true);
        boolean allowEntity = owner.getBooleanParameter("AllowEntityInteraction", true);
        boolean swingAfterUse = owner.getBooleanParameter("SwingAfterUse", true);
        boolean sneakWhileUsing = owner.getBooleanParameter("SneakWhileUsing", false);
        boolean restoreSneak = owner.getBooleanParameter("RestoreSneakState", true);

        if (!useUntilEmpty && configuredCount == 0) {
            future.complete(null);
            return;
        }

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        if (parameterData != null && parameterData.slotIndex != null) {
            if (!prepareSelectedItemForUse(client, parameterData, hand, future)) {
                return;
            }
        }

        final int maxIterations = configuredCount == 0 ? Integer.MAX_VALUE : configuredCount;

        new Thread(() -> {
            try {
                boolean previousSneak = false;

                if (sneakWhileUsing) {
                    previousSneak = owner.supplyFromClient(client, () -> client.player.isSneaking());
                }

                int iteration = 0;
                while (iteration < maxIterations) {
                    ItemStack stack = owner.supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                    if ((stack == null || stack.isEmpty()) && stopIfUnavailable) {
                        break;
                    }
                    if (sneakWhileUsing) {
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, true));
                        owner.waitForSneakSync(client, previousSneak, true);
                    }

                    owner.runOnClientThread(client, () -> {
                        boolean performed = false;
                        HitResult target = client.crosshairTarget;
                        if (allowEntity && target instanceof EntityHitResult entityHit) {
                            ActionResult entityResult = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
                            performed = entityResult.isAccepted();
                        }
                        if (!performed && allowBlock && target instanceof BlockHitResult blockHit) {
                            ActionResult blockResult = client.interactionManager.interactBlock(client.player, hand, blockHit);
                            performed = blockResult.isAccepted();
                        }
                        if (!performed) {
                            client.interactionManager.interactItem(client.player, hand);
                        }

                        if (durationSeconds > 0.0 && client.options != null && client.options.useKey != null) {
                            client.options.useKey.setPressed(true);
                        }

                        if (swingAfterUse) {
                            client.player.swingHand(hand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                            }
                        }
                    });

                    if (durationSeconds > 0.0) {
                        Thread.sleep((long) (durationSeconds * 1000));
                        owner.runOnClientThread(client, () -> {
                            if (client.options != null && client.options.useKey != null) {
                                client.options.useKey.setPressed(false);
                            }
                        });
                    }

                    if (sneakWhileUsing && restoreSneak) {
                        boolean sneakState = previousSneak;
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, sneakState));
                    }

                    if (useUntilEmpty) {
                        ItemStack afterUse = owner.supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                        if (afterUse == null || afterUse.isEmpty()) {
                            break;
                        }
                    }

                    iteration++;
                    if (iteration >= maxIterations) {
                        break;
                    }

                    if (intervalSeconds > 0.0) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Use").start();
    }

    private boolean prepareSelectedItemForUse(net.minecraft.client.MinecraftClient client,
                                              RuntimeParameterData parameterData,
                                              Hand hand,
                                              CompletableFuture<Void> future) {
        if (client == null || client.player == null || parameterData == null || parameterData.slotIndex == null) {
            return true;
        }
        if (parameterData.slotSelectionType == SlotSelectionType.GUI_CONTAINER) {
            owner.sendNodeErrorMessage(client, "Use node cannot use items from GUI/container slots.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }
        PlayerInventory inventory = client.player.getInventory();
        int clampedSlot = owner.clampInventorySlot(inventory, parameterData.slotIndex);
        boolean armorSlot = clampedSlot >= PlayerInventory.MAIN_SIZE
            && clampedSlot < PlayerInventory.MAIN_SIZE + Node.PLAYER_ARMOR_SLOT_COUNT;
        if (armorSlot) {
            owner.sendNodeErrorMessage(client, "Use node cannot activate armor slots.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        ItemStack stack = inventory.getStack(clampedSlot);
        if (stack.isEmpty()) {
            owner.sendNodeErrorMessage(client, "Selected slot for " + owner.getType().getDisplayName() + " is empty.");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        boolean prepared;
        if (hand == Hand.OFF_HAND) {
            prepared = ensureStackEquippedInOffhand(client, inventory, clampedSlot, stack);
        } else {
            prepared = ensureStackSelectedInMainHand(client, inventory, clampedSlot, stack);
        }

        if (!prepared) {
            owner.sendNodeErrorMessage(client, "Failed to prepare selected item for " + owner.getType().getDisplayName() + ".");
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
        }
        return prepared;
    }

    boolean ensureStackSelectedInMainHand(net.minecraft.client.MinecraftClient client,
                                                  PlayerInventory inventory,
                                                  int slotIndex,
                                                  ItemStack stack) {
        if (client == null || client.player == null || inventory == null || stack == null) {
            return false;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        int targetSlot = slotIndex;
        if (slotIndex >= hotbarSize) {
            targetSlot = moveInventoryStackToHotbar(client, inventory, slotIndex, stack.getItem());
            if (targetSlot == -1) {
                return false;
            }
        }
        try {
            PlayerInventoryBridge.setSelectedSlot(inventory, targetSlot);
        } catch (IllegalStateException ignored) {
            // Fall back to the packet-only update when inventory accessors are unavailable.
        }
        Node.syncSelectedHotbarSlot(client);
        return true;
    }

    private boolean ensureStackEquippedInOffhand(net.minecraft.client.MinecraftClient client,
                                                 PlayerInventory inventory,
                                                 int slotIndex,
                                                 ItemStack stack) {
        if (client == null || client.player == null || inventory == null || stack == null) {
            return false;
        }
        int offhandIndex = owner.getOffhandInventoryIndex(inventory);
        if (offhandIndex < 0) {
            return false;
        }
        if (slotIndex == offhandIndex) {
            return true;
        }
        if (slotIndex >= PlayerInventory.MAIN_SIZE) {
            return false;
        }
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.playerScreenHandler;
        if (interactionManager == null || handler == null) {
            return false;
        }
        int sourceHandlerSlot = owner.mapPlayerInventorySlot(handler, slotIndex);
        int offhandHandlerSlot = owner.mapPlayerInventorySlot(handler, offhandIndex);
        if (sourceHandlerSlot < 0 || offhandHandlerSlot < 0) {
            return false;
        }

        interactionManager.clickSlot(handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player);
        interactionManager.clickSlot(handler.syncId, offhandHandlerSlot, 0, SlotActionType.PICKUP, client.player);
        interactionManager.clickSlot(handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player);

        ItemStack offhandStack = client.player.getOffHandStack();
        return !offhandStack.isEmpty() && offhandStack.isOf(stack.getItem());
    }

    void executePlaceHandCommand(CompletableFuture<Void> future) {
        Node blockParameterNode = owner.getAttachedParameter(0);
        Node coordinateParameterNode = owner.getAttachedParameter(1);
        boolean blockProvidesCoordinates = blockParameterProvidesPlacementCoordinates(blockParameterNode);
        boolean coordinateProvidesCoordinates = parameterProvidesCoordinates(coordinateParameterNode);
        boolean coordinateHandledByBlockParam = coordinateParameterNode == null && blockProvidesCoordinates;

        if (blockParameterNode != null) {
            EnumSet<Node.ParameterUsage> blockUsages = coordinateHandledByBlockParam
                ? EnumSet.of(Node.ParameterUsage.POSITION)
                : EnumSet.noneOf(Node.ParameterUsage.class);
            if (owner.preprocessParameterSlot(0, blockUsages, future, true) == Node.ParameterHandlingResult.COMPLETE) {
                return;
            }
        } else {
            owner.runtimeState().runtimeParameterData = null;
        }

        if (coordinateParameterNode != null) {
            EnumSet<Node.ParameterUsage> coordinateUsages = coordinateProvidesCoordinates
                ? EnumSet.of(Node.ParameterUsage.POSITION)
                : EnumSet.noneOf(Node.ParameterUsage.class);
            if (owner.preprocessParameterSlot(1, coordinateUsages, future, blockParameterNode == null) == Node.ParameterHandlingResult.COMPLETE) {
                return;
            }
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        String inventorySlotBlockId = null;
        if (blockParameterNode != null && blockParameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            inventorySlotBlockId = resolveBlockIdFromInventorySlotParameter(client, blockParameterNode);
            if (inventorySlotBlockId == null || inventorySlotBlockId.isEmpty()) {
                future.complete(null);
                return;
            }
        }

        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);
        boolean sneakWhilePlacing = owner.getBooleanParameter("SneakWhilePlacing", false);
        boolean restoreSneak = owner.getBooleanParameter("RestoreSneakState", true);
        boolean swingOnPlace = owner.getBooleanParameter("SwingOnPlace", true);
        boolean requireBlockHit = owner.getBooleanParameter("RequireBlockHit", true);

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        BlockPos directedPlacementPos = null;
        if (parameterData != null && (coordinateProvidesCoordinates || coordinateHandledByBlockParam)) {
            directedPlacementPos = parameterData.targetBlockPos;
        }

        String parameterBlockId = resolveBlockIdFromParameterNode(blockParameterNode);
        if ((parameterBlockId == null || parameterBlockId.isEmpty()) && inventorySlotBlockId != null) {
            parameterBlockId = inventorySlotBlockId;
        }
        if ((parameterBlockId == null || parameterBlockId.isEmpty()) && parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                parameterBlockId = parameterData.targetBlockId;
            } else if (parameterData.targetBlockIds != null && !parameterData.targetBlockIds.isEmpty()) {
                parameterBlockId = parameterData.targetBlockIds.get(0);
            }
        }
        parameterBlockId = normalizePlacementBlockId(parameterBlockId);
        if (parameterBlockId != null && parameterBlockId.isEmpty()) {
            parameterBlockId = null;
        }

        if (directedPlacementPos != null) {
            handleDirectedPlaceHandPlacement(client, hand, parameterBlockId, directedPlacementPos, sneakWhilePlacing, restoreSneak, swingOnPlace, future);
            return;
        }

        if (parameterBlockId != null && !parameterBlockId.isEmpty()) {
            try {
                ensureBlockInHand(client, parameterBlockId, hand);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
                return;
            }
        }

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhilePlacing) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        boolean placed = false;
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit) {
            ActionResult result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            placed = result.isAccepted();
            if (!placed && !requireBlockHit) {
                ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
                placed = fallback.isAccepted();
            }
        } else if (!requireBlockHit) {
            ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
            placed = fallback.isAccepted();
        }

        if (swingOnPlace && placed) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        if (sneakWhilePlacing && restoreSneak) {
            client.player.setSneaking(previousSneak);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(previousSneak);
            }
        }

        future.complete(null);
    }

    private void handleDirectedPlaceHandPlacement(
        net.minecraft.client.MinecraftClient client,
        Hand hand,
        String parameterBlockId,
        BlockPos targetPos,
        boolean sneakWhilePlacing,
        boolean restoreSneak,
        boolean swingOnPlace,
        CompletableFuture<Void> future
    ) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        String blockIdToUse = parameterBlockId;
        if (blockIdToUse == null || blockIdToUse.isEmpty() || Node.isAnySelectionValue(blockIdToUse)) {
            String anyBlock = resolveAnyBlockId(client, hand);
            if (anyBlock != null && !anyBlock.isEmpty()) {
                blockIdToUse = anyBlock;
            }
        }
        if (blockIdToUse == null || blockIdToUse.isEmpty() || Node.isAnySelectionValue(blockIdToUse)) {
            blockIdToUse = getBlockIdFromHand(client, hand);
        }
        if (blockIdToUse == null || blockIdToUse.isEmpty() || Node.isAnySelectionValue(blockIdToUse)) {
            owner.sendNodeErrorMessage(client, "Cannot place block: no block selected.");
            future.complete(null);
            return;
        }

        Block desiredBlock = owner.resolveBlockForPlacement(blockIdToUse);
        if (desiredBlock == null) {
            owner.sendNodeErrorMessage(client, "Cannot place block: unknown block \"" + blockIdToUse + "\".");
            future.complete(null);
            return;
        }

        double reachSquared = owner.getPlacementReachSquared(client);
        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = blockIdToUse;
        final Hand resolvedHand = hand;
        final boolean shouldSwing = swingOnPlace;
        final boolean shouldSneak = sneakWhilePlacing;
        final boolean shouldRestoreSneak = restoreSneak;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = owner.supplyFromClient(client, () ->
                    owner.preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, reachSquared)
                );
                boolean initialSneak = owner.supplyFromClient(client, () -> client.player.isSneaking());
                if (shouldSneak) {
                    owner.runOnClientThread(client, () -> owner.applySneakState(client, true));
                    owner.waitForSneakSync(client, initialSneak, true);
                }
                try {
                    owner.runOnClientThread(client, () -> {
                        if (client.world.getBlockState(placementPos).isOf(resolvedBlock)) {
                            return;
                        }
                        ActionResult result = client.interactionManager.interactBlock(client.player, resolvedHand, placementHitResult);
                        if (!result.isAccepted()) {
                            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(placementPos) + ": placement rejected (" + result + ").");
                        }
                        if (shouldSwing) {
                            client.player.swingHand(resolvedHand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(resolvedHand));
                            }
                        }
                    });
                } finally {
                    if (shouldSneak && shouldRestoreSneak) {
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, initialSneak));
                    }
                }
                boolean placed = owner.waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    owner.sendNodeErrorMessage(client, "Attempted to place block \"" + resolvedBlockId + "\" at " + formatBlockPos(placementPos) + " but it did not appear. Make sure the space is clear and within reach.");
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                owner.sendNodeErrorMessage(client, "Failed to place block \"" + resolvedBlockId + "\": " + e.getMessage());
                future.complete(null);
            }
        }, "Pathmind-PlaceHand").start();
    }

    void ensureBlockInHand(net.minecraft.client.MinecraftClient client, String blockId, Hand hand) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": unknown block item.");
        }

        Item targetItem = Registries.ITEM.get(identifier);
        ItemStack current = client.player.getStackInHand(hand);
        if (!current.isEmpty() && current.isOf(targetItem)) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int slot = findHotbarSlotWithItem(inventory, targetItem);
        if (slot == -1) {
            int inventorySlot = findMainInventorySlotWithItem(inventory, targetItem);
            if (inventorySlot == -1) {
                boolean elsewhere = inventory.contains(new ItemStack(targetItem));
                if (elsewhere) {
                    throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": the only available items are equipped or otherwise unavailable.");
                }
                throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": none available in your inventory.");
            }

            slot = moveInventoryStackToHotbar(client, inventory, inventorySlot, targetItem);
            if (slot == -1) {
                throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": failed to move it into your hotbar.");
            }
        }

        if (hand == Hand.MAIN_HAND) {
            try {
                PlayerInventoryBridge.setSelectedSlot(inventory, slot);
            } catch (IllegalStateException ignored) {
                // Fall back to the packet-only update when inventory accessors are unavailable.
            }
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            return;
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        if (!offhandStack.isEmpty() && offhandStack.isOf(targetItem)) {
            return;
        }

        PlayerInventoryBridge.setSelectedSlot(inventory, slot);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    boolean waitForBlockPlacement(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Block desiredBlock) throws InterruptedException {
        if (client == null || targetPos == null || desiredBlock == null) {
            return false;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean matches = owner.supplyFromClient(client, () -> {
                if (client.world == null) {
                    return false;
                }
                return client.world.getBlockState(targetPos).isOf(desiredBlock);
            });
            if (matches) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private boolean waitForUseBlockPlacement(net.minecraft.client.MinecraftClient client,
                                             BlockHitResult blockHit,
                                             Block desiredBlock) throws InterruptedException {
        if (client == null || blockHit == null || desiredBlock == null) {
            return false;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        Direction side = blockHit.getSide();
        if (hitPos == null || side == null) {
            return false;
        }

        BlockPos offsetPos = hitPos.offset(side);
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean placed = owner.supplyFromClient(client, () -> {
                if (client.world == null) {
                    return false;
                }
                BlockState hitState = client.world.getBlockState(hitPos);
                if (hitState.isOf(desiredBlock)) {
                    return true;
                }
                BlockState offsetState = client.world.getBlockState(offsetPos);
                return offsetState.isOf(desiredBlock);
            });
            if (placed) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    int findHotbarSlotWithItem(PlayerInventory inventory, Item targetItem) {
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findMainInventorySlotWithItem(PlayerInventory inventory, Item targetItem) {
        if (inventory == null || targetItem == null) {
            return -1;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = hotbarSize; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot(PlayerInventory inventory) {
        if (inventory == null) {
            return -1;
        }
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int moveInventoryStackToHotbar(net.minecraft.client.MinecraftClient client, PlayerInventory inventory, int inventorySlot, Item targetItem) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return -1;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
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

        int handlerSlot = owner.mapPlayerInventorySlot(handler, inventorySlot);
        if (handlerSlot < 0) {
            return -1;
        }

        client.interactionManager.clickSlot(handler.syncId, handlerSlot, targetHotbarSlot, SlotActionType.SWAP, client.player);

        ItemStack hotbarStack = inventory.getStack(targetHotbarSlot);
        if (hotbarStack.isEmpty() || !hotbarStack.isOf(targetItem)) {
            return -1;
        }
        return targetHotbarSlot;
    }

    BlockHitResult preparePlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, String blockId, Hand hand, double reachSquared) {
        if (client.player == null || client.world == null) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": client world is unavailable.");
        }

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(targetPos);
        if (eyePos.squaredDistanceTo(targetCenter) > reachSquared) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": target is out of reach.");
        }

        if (!isBlockReplaceable(client.world, targetPos)) {
            BlockState occupied = client.world.getBlockState(targetPos);
            throw new Node.PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": target space contains " + describeBlockState(occupied) + "."
            );
        }

        Vec3d preferredLook = null;
        if (owner.runtimeState().runtimeParameterData != null
            && owner.runtimeState().runtimeParameterData.resolvedYaw != null
            && owner.runtimeState().runtimeParameterData.resolvedPitch != null) {
            double yawRad = Math.toRadians(owner.runtimeState().runtimeParameterData.resolvedYaw);
            double pitchRad = Math.toRadians(owner.runtimeState().runtimeParameterData.resolvedPitch);
            double xDir = -Math.sin(yawRad) * Math.cos(pitchRad);
            double yDir = -Math.sin(pitchRad);
            double zDir = Math.cos(yawRad) * Math.cos(pitchRad);
            preferredLook = new Vec3d(xDir, yDir, zDir).normalize();
        }

        BlockHitResult surface = createPlacementHitResult(client, targetPos, eyePos, reachSquared, preferredLook);
        if (surface == null) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": no nearby surface to place against.");
        }

        ensureBlockInHand(client, blockId, hand);

        ItemStack stack = client.player.getStackInHand(hand);
        if (stack.isEmpty()) {
            throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": the selected hand is empty.");
        }

        Item heldItem = stack.getItem();
        if (!(heldItem instanceof BlockItem blockItem)) {
            throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": the selected item cannot be placed as a block.");
        }

        if (!canPlaceBlockAt(client, hand, stack, blockItem, surface)) {
            throw new Node.PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": the location is obstructed or lacks support."
            );
        }

        return surface;
    }

    private BlockHitResult createPlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Vec3d eyePos, double reachSquared, Vec3d preferredLook) {
        if (client.player == null || client.world == null) {
            return null;
        }

        BlockHitResult bestResult = null;
        double bestDistance = Double.MAX_VALUE;
        double bestAlignment = -Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.offset(direction);
            BlockState supportState = client.world.getBlockState(supportPos);
            if (supportState.isAir()) {
                continue;
            }
            if (supportState.getCollisionShape(client.world, supportPos).isEmpty()) {
                continue;
            }

            Direction placementSide = direction.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(supportPos).add(
                placementSide.getOffsetX() * 0.5D,
                placementSide.getOffsetY() * 0.5D,
                placementSide.getOffsetZ() * 0.5D
            );

            Vec3d faceAxisA;
            Vec3d faceAxisB;
            switch (placementSide.getAxis()) {
                case X -> {
                    faceAxisA = FACE_AXIS_Y;
                    faceAxisB = FACE_AXIS_Z;
                }
                case Y -> {
                    faceAxisA = FACE_AXIS_X;
                    faceAxisB = FACE_AXIS_Z;
                }
                default -> {
                    faceAxisA = FACE_AXIS_X;
                    faceAxisB = FACE_AXIS_Y;
                }
            }

            Vec3d placementNormal = Vec3d.of(placementSide.getVector());
            double faceAlignment = preferredLook != null ? preferredLook.dotProduct(placementNormal) : 0.0D;

            for (double offsetA : FACE_OFFSET_SAMPLES) {
                for (double offsetB : FACE_OFFSET_SAMPLES) {
                    Vec3d samplePoint = faceCenter
                        .add(faceAxisA.multiply(offsetA))
                        .add(faceAxisB.multiply(offsetB));
                    double distance = eyePos.squaredDistanceTo(samplePoint);
                    if (distance > reachSquared) {
                        continue;
                    }

                    boolean better;
                    if (preferredLook != null) {
                        if (faceAlignment > bestAlignment + 1e-6) {
                            better = true;
                        } else if (Math.abs(faceAlignment - bestAlignment) <= 1e-6) {
                            better = distance < bestDistance;
                        } else {
                            better = false;
                        }
                    } else {
                        better = distance < bestDistance;
                    }

                    if (better) {
                        bestDistance = distance;
                        bestAlignment = faceAlignment;
                        bestResult = new BlockHitResult(
                            samplePoint.subtract(placementNormal.multiply(0.001D)),
                            placementSide,
                            supportPos,
                            false
                        );
                    }
                }
            }
        }
        return bestResult;
    }

    private static final long SNEAK_SYNC_DELAY_MS = 75L;
    private static final double[] FACE_OFFSET_SAMPLES = {0.0D, 0.32D, -0.32D, 0.48D, -0.48D};
    private static final Vec3d FACE_AXIS_X = new Vec3d(1.0D, 0.0D, 0.0D);
    private static final Vec3d FACE_AXIS_Y = new Vec3d(0.0D, 1.0D, 0.0D);
    private static final Vec3d FACE_AXIS_Z = new Vec3d(0.0D, 0.0D, 1.0D);

    private boolean canPlaceBlockAt(net.minecraft.client.MinecraftClient client, Hand hand, ItemStack stack, BlockItem blockItem, BlockHitResult hitResult) {
        if (client.player == null || client.world == null) {
            return false;
        }

        ItemPlacementContext placementContext = new ItemPlacementContext(client.player, hand, stack.copy(), hitResult);
        if (!placementContext.canPlace()) {
            return false;
        }

        Block block = blockItem.getBlock();
        BlockState placementState = block.getPlacementState(placementContext);
        if (placementState == null) {
            return false;
        }

        return placementState.canPlaceAt(client.world, placementContext.getBlockPos());
    }

    static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(unknown)";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    Block resolveBlockForPlacement(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }

        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }

        return Registries.BLOCK.get(identifier);
    }

    double getPlacementReachSquared(net.minecraft.client.MinecraftClient client) {
        return Node.DEFAULT_REACH_DISTANCE_SQUARED;
    }

    private String describeBlockState(BlockState state) {
        if (state == null) {
            return "an unknown block";
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id != null ? id.toString() : "an unknown block";
    }

    boolean isBlockReplaceable(World world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        return state.getCollisionShape(world, targetPos).isEmpty();
    }

    boolean hasPlacementSupport(World world, BlockPos targetPos) {
        if (world == null || targetPos == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.offset(direction);
            BlockState supportState = world.getBlockState(supportPos);
            if (!supportState.isAir() && !supportState.getCollisionShape(world, supportPos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
    void executePlaceCommand(CompletableFuture<Void> future) {
        Node blockParameterNode = owner.getAttachedParameter(0);
        Node coordinateParameterNode = owner.getAttachedParameter(1);
        boolean coordinateHandledByBlockParam = coordinateParameterNode == null && parameterProvidesCoordinates(blockParameterNode);

        if (blockParameterNode != null) {
            EnumSet<Node.ParameterUsage> blockUsages = coordinateHandledByBlockParam
                ? EnumSet.of(Node.ParameterUsage.POSITION)
                : EnumSet.noneOf(Node.ParameterUsage.class);
            if (owner.preprocessParameterSlot(0, blockUsages, future, true) == Node.ParameterHandlingResult.COMPLETE) {
                return;
            }
        } else {
            owner.runtimeState().runtimeParameterData = null;
        }

        if (coordinateParameterNode != null) {
            NodeType coordType = coordinateParameterNode.getType();
            EnumSet<Node.ParameterUsage> coordinateUsages;
            if (coordType == NodeType.PARAM_ROTATION
                || coordType == NodeType.PARAM_DIRECTION
                || coordType == NodeType.PARAM_BLOCK_FACE) {
                coordinateUsages = EnumSet.of(Node.ParameterUsage.LOOK_ORIENTATION);
            } else if (parameterProvidesCoordinates(coordinateParameterNode)) {
                coordinateUsages = EnumSet.of(Node.ParameterUsage.POSITION);
            } else {
                coordinateUsages = EnumSet.noneOf(Node.ParameterUsage.class);
            }
            if (owner.preprocessParameterSlot(1, coordinateUsages, future, blockParameterNode == null) == Node.ParameterHandlingResult.COMPLETE) {
                return;
            }
        }

        boolean inheritPlacementCoordinates = coordinateHandledByBlockParam
            || (coordinateParameterNode != null
                && parameterProvidesCoordinates(coordinateParameterNode)
                && coordinateParameterNode.getType() != NodeType.PARAM_ROTATION
                && coordinateParameterNode.getType() != NodeType.PARAM_DIRECTION
                && coordinateParameterNode.getType() != NodeType.PARAM_BLOCK_FACE);
        String block = "stone";
        int x = 0, y = 0, z = 0;

        NodeParameter blockParam = owner.getParameter("Block");
        NodeParameter xParam = owner.getParameter("X");
        NodeParameter yParam = owner.getParameter("Y");
        NodeParameter zParam = owner.getParameter("Z");
        Hand hand = owner.resolveHand(owner.getParameter("Hand"), Hand.MAIN_HAND);

        if (blockParam != null) block = blockParam.getStringValue();
        if (xParam != null) x = xParam.getIntValue();
        if (yParam != null) y = yParam.getIntValue();
        if (zParam != null) z = zParam.getIntValue();

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                block = parameterData.targetBlockId;
                owner.setParameterValueAndPropagate("Block", block);
            }
            if (inheritPlacementCoordinates && parameterData.targetBlockPos != null) {
                BlockPos resolved = parameterData.targetBlockPos;
                x = resolved.getX();
                y = resolved.getY();
                z = resolved.getZ();
            }
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (blockParameterNode != null && blockParameterNode.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            String resolvedBlockId = resolveBlockIdFromInventorySlotParameter(client, blockParameterNode);
            if (resolvedBlockId == null || resolvedBlockId.isEmpty()) {
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return;
            }
            block = resolvedBlockId;
            owner.setParameterValueAndPropagate("Block", block);
        }

        String originalBlockId = block;
        if (Node.isAnySelectionValue(originalBlockId)) {
            String anyBlock = resolveAnyBlockId(client, hand);
            if (anyBlock != null && !anyBlock.isEmpty()) {
                block = anyBlock;
                originalBlockId = block;
                owner.setParameterValueAndPropagate("Block", block);
            }
        }
        block = owner.normalizeResourceId(block, "minecraft");
        if (!Objects.equals(originalBlockId, block)) {
            owner.setParameterValueAndPropagate("Block", block);
        }

        if (block == null || block.isEmpty() || Node.isAnySelectionValue(block)) {
            owner.sendNodeErrorMessage(client, "Cannot place block: no block selected.");
            future.complete(null);
            return;
        }

        if (blockParameterNode != null && isBlockPlacementParameter(blockParameterNode)) {
            try {
                ensureBlockInHand(client, block, Hand.MAIN_HAND);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
                return;
            }
        }

        if (!inheritPlacementCoordinates
            && coordinateParameterNode != null
            && (coordinateParameterNode.getType() == NodeType.PARAM_ROTATION
                || coordinateParameterNode.getType() == NodeType.PARAM_DIRECTION
                || coordinateParameterNode.getType() == NodeType.PARAM_BLOCK_FACE)) {
            try {
                float yaw = parameterData != null && parameterData.resolvedYaw != null
                    ? parameterData.resolvedYaw
                    : client.player.getYaw();
                float pitch = parameterData != null && parameterData.resolvedPitch != null
                    ? parameterData.resolvedPitch
                    : client.player.getPitch();
                double reachDistance = Math.sqrt(getPlacementReachSquared(client));
                double lookDistance = parameterData != null && parameterData.resolvedLookDistance != null
                    ? parameterData.resolvedLookDistance
                    : reachDistance;
                BlockHitResult hit = owner.supplyFromClient(client, () ->
                    owner.raycastBlockFromOrientation(client, yaw, pitch, lookDistance)
                );
                if (hit != null) {
                    owner.runOnClientThread(client, () -> {
                        client.player.setYaw(yaw);
                        client.player.setPitch(pitch);
                        client.player.setHeadYaw(yaw);
                        ActionResult result = client.interactionManager.interactBlock(client.player, hand, hit);
                        if (result.isAccepted()) {
                            client.player.swingHand(hand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                            }
                        }
                    });
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
            return;
        }


        BlockPos targetPos = new BlockPos(x, y, z);
        if (parameterData != null) {
            parameterData.targetBlockPos = targetPos;
            if (parameterData.targetBlockId == null || parameterData.targetBlockId.isEmpty()) {
                parameterData.targetBlockId = block;
            }
        }
        double reachSquared = getPlacementReachSquared(client);

        Block desiredBlock = resolveBlockForPlacement(block);
        if (desiredBlock == null) {
            owner.sendNodeErrorMessage(client, "Cannot place block: unknown block \"" + block + "\".");
            future.complete(null);
            return;
        }

        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = block;
        final Hand resolvedHand = hand;
        final double resolvedReachSquared = reachSquared;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = owner.supplyFromClient(client, () ->
                    preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, resolvedReachSquared)
                );
                owner.runOnClientThread(client, () -> {
                    if (client.world.getBlockState(placementPos).isOf(resolvedBlock)) {
                        return;
                    }

                    ActionResult result = client.interactionManager.interactBlock(client.player, resolvedHand, placementHitResult);
                    if (!result.isAccepted()) {
                        throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(placementPos) + ": placement rejected (" + result + ").");
                    }
                    if (client.player != null) {
                        client.player.swingHand(resolvedHand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(resolvedHand));
                        }
                    }
                });
                boolean placed = waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    owner.sendNodeErrorMessage(client, "Attempted to place block \"" + resolvedBlockId + "\" at " + formatBlockPos(placementPos) + " but it did not appear. Make sure the space is clear and within reach.");
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                owner.sendNodeErrorMessage(client, "Failed to place block \"" + resolvedBlockId + "\": " + e.getMessage());
                future.complete(null);
            }
        }, "Pathmind-Place").start();
    }

    boolean parameterProvidesCoordinates(Node parameterNode) {
        if (parameterNode == null) {
            return false;
        }
        EnumSet<NodeValueTrait> traits = parameterNode.getProvidedTraits();
        if (traits.isEmpty()) {
            return false;
        }
        return traits.contains(NodeValueTrait.COORDINATE)
            || traits.contains(NodeValueTrait.DIRECTION)
            || traits.contains(NodeValueTrait.ROTATION)
            || traits.contains(NodeValueTrait.BLOCK)
            || traits.contains(NodeValueTrait.ITEM)
            || traits.contains(NodeValueTrait.ENTITY)
            || traits.contains(NodeValueTrait.PLAYER)
            || traits.contains(NodeValueTrait.WAYPOINT)
            || traits.contains(NodeValueTrait.SCHEMATIC)
            || traits.contains(NodeValueTrait.LIST_ITEM);
    }

    boolean parameterProvidesCoordinates(NodeType parameterType) {
        if (parameterType == null) {
            return false;
        }
        EnumSet<NodeValueTrait> traits = NodeTraitRegistry.getProvidedTraits(parameterType);
        if (traits.isEmpty()) {
            return false;
        }
        return traits.contains(NodeValueTrait.COORDINATE)
            || traits.contains(NodeValueTrait.DIRECTION)
            || traits.contains(NodeValueTrait.ROTATION)
            || traits.contains(NodeValueTrait.BLOCK)
            || traits.contains(NodeValueTrait.ITEM)
            || traits.contains(NodeValueTrait.ENTITY)
            || traits.contains(NodeValueTrait.PLAYER)
            || traits.contains(NodeValueTrait.WAYPOINT)
            || traits.contains(NodeValueTrait.SCHEMATIC)
            || traits.contains(NodeValueTrait.LIST_ITEM);
    }

    private boolean isBlockPlacementParameter(Node parameterNode) {
        if (parameterNode == null) {
            return false;
        }
        NodeType parameterType = parameterNode.getType();
        return parameterType == NodeType.PARAM_BLOCK
            || parameterType == NodeType.PARAM_PLACE_TARGET;
    }

    boolean blockParameterProvidesPlacementCoordinates(Node parameterNode) {
        return parameterNode != null && parameterNode.getType() == NodeType.PARAM_PLACE_TARGET;
    }

    String resolveBlockIdFromInventorySlotParameter(net.minecraft.client.MinecraftClient client,
                                                           Node parameterNode) {
        if (client == null || client.player == null || parameterNode == null) {
            return null;
        }
        SlotSelectionType selectionType = owner.resolveInventorySlotSelectionType(parameterNode);
        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            owner.sendNodeErrorMessage(client, owner.getType().getDisplayName() + " can only use player inventory slots.");
            return null;
        }
        PlayerInventory inventory = client.player.getInventory();
        int slotValue = owner.clampInventorySlot(inventory, Node.parseNodeInt(parameterNode, "Slot", 0));
        ItemStack stack = inventory.getStack(slotValue);
        if (stack.isEmpty()) {
            owner.sendNodeErrorMessage(client, "Selected slot for " + owner.getType().getDisplayName() + " is empty.");
            return null;
        }
        if (!(stack.getItem() instanceof BlockItem)) {
            owner.sendNodeErrorMessage(client, "Selected slot for " + owner.getType().getDisplayName() + " does not contain a block.");
            return null;
        }
        if (owner.runtimeState().runtimeParameterData == null) {
            owner.runtimeState().runtimeParameterData = new RuntimeParameterData();
        }
        owner.runtimeState().runtimeParameterData.slotIndex = slotValue;
        owner.runtimeState().runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
        if (!ensureStackSelectedInMainHand(client, inventory, slotValue, stack)) {
            owner.sendNodeErrorMessage(client, "Failed to prepare selected block for " + owner.getType().getDisplayName() + ".");
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null ? id.toString() : null;
    }

    String resolveBlockIdFromParameterNode(Node parameterNode) {
        if (parameterNode == null) {
            return null;
        }
        NodeType parameterType = parameterNode.getType();
        switch (parameterType) {
            case PARAM_BLOCK:
                for (String entry : owner.splitMultiValueList(owner.getBlockParameterValue(parameterNode))) {
                    return entry;
                }
                return null;
            case PARAM_PLACE_TARGET:
                return Node.getParameterString(parameterNode, "Block");
            default:
                return null;
        }
    }

    String normalizePlacementBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }
        String sanitized = owner.sanitizeResourceId(blockId);
        if (sanitized == null || sanitized.isEmpty()) {
            return "";
        }
        return owner.normalizeResourceId(sanitized, "minecraft");
    }

    String getBlockIdFromHand(net.minecraft.client.MinecraftClient client, Hand hand) {
        if (client == null || client.player == null) {
            return null;
        }
        ItemStack stack = client.player.getStackInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null ? id.toString() : null;
    }

    String resolveAnyBlockId(net.minecraft.client.MinecraftClient client, Hand preferredHand) {
        if (client == null || client.player == null) {
            return null;
        }
        String fromHand = getBlockIdFromHand(client, preferredHand);
        if (fromHand != null && !fromHand.isEmpty()) {
            return fromHand;
        }
        PlayerInventory inventory = client.player.getInventory();
        if (inventory == null) {
            return null;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id != null) {
                return id.toString();
            }
        }
        return null;
    }
    
    void executeBuildCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (owner.getMode() == null) {
            future.completeExceptionally(new RuntimeException("No mode set for BUILD node"));
            return;
        }
        
        String schematic = "house.schematic";
        NodeParameter schematicParam = owner.getParameter("Schematic");
        if (schematicParam != null) {
            schematic = schematicParam.getStringValue();
        }
        BlockPos buildOrigin = resolveBuildOrigin();
        if (buildOrigin == null) {
            future.completeExceptionally(new RuntimeException("Unable to resolve build origin"));
            return;
        }

        boolean usePlayerOriginCommand = owner.runtimeState().runtimeParameterData != null
            ? owner.runtimeState().runtimeParameterData.targetVector == null && owner.getMode() == NodeMode.BUILD_PLAYER
            : owner.getMode() == NodeMode.BUILD_PLAYER;
        String command = usePlayerOriginCommand
            ? String.format("#build %s", schematic)
            : String.format("#build %s %d %d %d", schematic, buildOrigin.getX(), buildOrigin.getY(), buildOrigin.getZ());

        if (!owner.isBaritoneApiAvailable() && owner.isBaritoneModAvailable()) {
            owner.executeCommand(command);
            future.complete(null);
            return;
        }

        Object baritone = owner.getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }

        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_BUILD, future);
        Object builderProcess = BaritoneApiProxy.getBuilderProcess(baritone);
        if (builderProcess != null && BaritoneApiProxy.build(builderProcess, schematic, buildOrigin)) {
            return;
        }
        owner.executeCommand(command);
    }

    private BlockPos resolveBuildOrigin() {
        Vec3d targetVector = owner.runtimeState().runtimeParameterData != null ? owner.runtimeState().runtimeParameterData.targetVector : null;
        if (targetVector != null) {
            return BlockPos.ofFloored(targetVector);
        }

        switch (owner.getMode()) {
            case BUILD_PLAYER:
                return getPlayerBuildOrigin();
            case BUILD_XYZ:
                int x = 0;
                int y = 0;
                int z = 0;
                NodeParameter xParam = owner.getParameter("X");
                NodeParameter yParam = owner.getParameter("Y");
                NodeParameter zParam = owner.getParameter("Z");

                if (xParam != null) {
                    x = xParam.getIntValue();
                }
                if (yParam != null) {
                    y = yParam.getIntValue();
                }
                if (zParam != null) {
                    z = zParam.getIntValue();
                }
                return new BlockPos(x, y, z);
            default:
                return null;
        }
    }

    private BlockPos getPlayerBuildOrigin() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        return client.player.getBlockPos();
    }
    
    void executeExploreCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (owner.getMode() == null) {
            future.completeExceptionally(new RuntimeException("No mode set for EXPLORE node"));
            return;
        }

        if (!owner.isBaritoneApiAvailable() && owner.isBaritoneModAvailable()) {
            switch (owner.getMode()) {
                case EXPLORE_CURRENT: {
                    String command = "#explore";
                    owner.executeCommand(command);
                    future.complete(null);
                    return;
                }
                case EXPLORE_XYZ: {
                    int x = 0, z = 0;
                    NodeParameter xParam = owner.getParameter("X");
                    NodeParameter zParam = owner.getParameter("Z");

                    if (xParam != null) x = xParam.getIntValue();
                    if (zParam != null) z = zParam.getIntValue();

                    String command = String.format("#explore %d %d", x, z);
                    owner.executeCommand(command);
                    future.complete(null);
                    return;
                }
                case EXPLORE_FILTER: {
                    String filter = "explore.txt";
                    NodeParameter filterParam = owner.getParameter("Filter");
                    if (filterParam != null) {
                        filter = filterParam.getStringValue();
                    }
                    owner.executeCommand("#explore " + filter);
                    future.complete(null);
                    return;
                }
                default:
                    future.completeExceptionally(new RuntimeException("Unknown EXPLORE mode: " + owner.getMode()));
                    return;
            }
        }
        
        Object baritone = owner.getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        owner.resetBaritonePathing(baritone);
        Object exploreProcess = BaritoneApiProxy.getExploreProcess(baritone);
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_EXPLORE, future);
        
        switch (owner.getMode()) {
            case EXPLORE_CURRENT:
                BaritoneApiProxy.explore(exploreProcess, 0, 0); // 0,0 means from current position
                break;
                
            case EXPLORE_XYZ:
                int x = 0, z = 0;
                NodeParameter xParam = owner.getParameter("X");
                NodeParameter zParam = owner.getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                BaritoneApiProxy.explore(exploreProcess, x, z);
                break;
                
            case EXPLORE_FILTER:
                String filter = "explore.txt";
                NodeParameter filterParam = owner.getParameter("Filter");
                if (filterParam != null) {
                    filter = filterParam.getStringValue();
                }
                
                // For filter-based exploration, we need to use a different approach
                owner.executeCommand("#explore " + filter);
                future.complete(null); // Command-based exploration completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown EXPLORE mode: " + owner.getMode()));
                return;
        }
    }
    
    void executeFollowCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (owner.getMode() == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FOLLOW node"));
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        
        String command;
        switch (owner.getMode()) {
            case FOLLOW_PLAYER:
                String player = "Self";
                NodeParameter playerParam = owner.getParameter("Player");
                if (playerParam != null) {
                    player = playerParam.getStringValue();
                }

                if (Node.isAnyPlayerValue(player)) {
                    command = "#follow players";
                } else {
                    if (Node.isSelfPlayerValue(player)) {
                        player = client != null && client.player != null
                            ? GameProfileCompatibilityBridge.getName(client.player.getGameProfile())
                            : "Self";
                    }
                    command = "#follow player " + player;
                }
                break;
                
            case FOLLOW_PLAYERS:
                command = "#follow players";
                break;
                
            case FOLLOW_ENTITIES:
                command = "#follow entities";
                break;
                
            case FOLLOW_ENTITY_TYPE:
                String entity = "cow";
                NodeParameter entityParam = owner.getParameter("Entity");
                if (entityParam != null) {
                    entity = entityParam.getStringValue();
                }

                command = "#follow entity " + entity;
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FOLLOW mode: " + owner.getMode()));
                return;
        }
        
        owner.executeCommand(command);
        future.complete(null); // Follow commands complete immediately
    }

}
