package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.execution.PreciseCompletionTracker;
import com.pathmind.util.BaritoneApiProxy;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.PlayerInventoryBridge;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class NodeWorldActionCommandExecutor {
    private static final long ITEM_USE_SYNC_TIMEOUT_MS = 300L;
    private static final long TRANSIENT_ENTITY_TRACK_DURATION_MS = 4_500L;
    private static final long TRANSIENT_ENTITY_TRACK_INTERVAL_MS = 25L;
    private static final double TRANSIENT_ENTITY_TRACK_RANGE = 128.0D;

    private final Node owner;

    NodeWorldActionCommandExecutor(Node owner) {
        this.owner = owner;
    }

    void executeUseCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);
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
                    previousSneak = owner.supplyFromClient(client, () -> client.player.isShiftKeyDown());
                }

                int iteration = 0;
                while (iteration < maxIterations) {
                    ItemStack stack = owner.supplyFromClient(client, () -> client.player.getItemInHand(hand).copy());
                    if ((stack == null || stack.isEmpty()) && stopIfUnavailable) {
                        break;
                    }
                    if (sneakWhileUsing) {
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, true));
                        owner.waitForSneakSync(client, previousSneak, true);
                    }

                    owner.runOnClientThread(client, () -> {
                        boolean performed = false;
                        HitResult target = client.hitResult;
                        ItemStack currentStack = client.player.getItemInHand(hand);
                        if (allowEntity && target instanceof EntityHitResult entityHit) {
                            InteractionResult entityResult = client.gameMode.interact(client.player, entityHit.getEntity(), hand);
                            performed = entityResult.consumesAction();
                        }
                        BlockHitResult blockHit = resolveFreshBlockHit(client, target);
                        if (!performed && shouldTryBlockInteractionForUse(currentStack, allowBlock) && blockHit != null) {
                            InteractionResult blockResult = client.gameMode.useItemOn(client.player, hand, blockHit);
                            performed = blockResult.consumesAction();
                        }
                        if (!performed) {
                            client.gameMode.useItem(client.player, hand);
                        }

                        if (durationSeconds > 0.0 && client.options != null && client.options.keyUse != null) {
                            client.options.keyUse.setDown(true);
                        }

                        if (swingAfterUse) {
                            client.player.swing(hand);
                        }
                    });

                    if (isEnderEyeStack(stack)) {
                        startTransientEntityTracking(client, TransientEntityPositionTracker.EYE_OF_ENDER_ID);
                    }

                    if (durationSeconds > 0.0) {
                        Thread.sleep((long) (durationSeconds * 1000));
                        owner.runOnClientThread(client, () -> {
                            if (client.options != null && client.options.keyUse != null) {
                                client.options.keyUse.setDown(false);
                            }
                        });
                    }

                    waitForItemUseSync(client, hand, stack);

                    if (sneakWhileUsing && restoreSneak) {
                        boolean sneakState = previousSneak;
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, sneakState));
                    }

                    if (useUntilEmpty) {
                        ItemStack afterUse = owner.supplyFromClient(client, () -> client.player.getItemInHand(hand).copy());
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

    private boolean shouldTryBlockInteractionForUse(ItemStack stack, boolean allowBlock) {
        if (!allowBlock) {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        Item item = stack.getItem();
        return item instanceof BlockItem
            || item == Items.BUCKET
            || item == Items.WATER_BUCKET
            || item == Items.LAVA_BUCKET
            || item == Items.POWDER_SNOW_BUCKET
            || item == Items.FLINT_AND_STEEL
            || item == Items.BONE_MEAL
            || item == Items.SHEARS;
    }

    private BlockHitResult resolveFreshBlockHit(net.minecraft.client.Minecraft client, HitResult cachedTarget) {
        if (client != null && client.player != null) {
            BlockHitResult freshHit = owner.raycastBlockFromOrientation(
                client,
                client.player.getYRot(),
                client.player.getXRot(),
                0.0
            );
            if (freshHit != null) {
                return freshHit;
            }
        }
        return cachedTarget instanceof BlockHitResult blockHit ? blockHit : null;
    }

    private void waitForItemUseSync(net.minecraft.client.Minecraft client, InteractionHand hand, ItemStack beforeUse) throws InterruptedException {
        if (client == null || beforeUse == null || beforeUse.isEmpty() || !mayChangeAfterUse(beforeUse)) {
            return;
        }

        long deadline = System.currentTimeMillis() + ITEM_USE_SYNC_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ItemStack current = owner.supplyFromClient(client, () -> {
                if (client.player == null) {
                    return ItemStack.EMPTY;
                }
                return client.player.getItemInHand(hand).copy();
            });
            if (current == null || !ItemStack.matches(current, beforeUse)) {
                return;
            }
            Thread.sleep(Node.CONTROL_POLL_INTERVAL_MS);
        }
    }

    private boolean mayChangeAfterUse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.isDamageableItem()
            || stack.getMaxStackSize() > 1
            || hasRecipeRemainder(stack.getItem())
            || isKnownStatefulUseItem(stack.getItem());
    }

    private boolean hasRecipeRemainder(Item item) {
        if (item == null) {
            return false;
        }
        try {
            java.lang.reflect.Method method = Item.class.getMethod("getRecipeRemainder");
            Object remainder = method.invoke(item);
            return remainder instanceof Item remainderItem && remainderItem != Items.AIR;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isKnownStatefulUseItem(Item item) {
        return item == Items.BUCKET
            || item == Items.WATER_BUCKET
            || item == Items.LAVA_BUCKET
            || item == Items.POWDER_SNOW_BUCKET
            || item == Items.GLASS_BOTTLE
            || item == Items.POTION
            || item == Items.SPLASH_POTION
            || item == Items.LINGERING_POTION
            || item == Items.MILK_BUCKET
            || item == Items.ENDER_EYE;
    }

    private boolean isEnderEyeStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.ENDER_EYE);
    }

    private void startTransientEntityTracking(net.minecraft.client.Minecraft client, String entityId) {
        Thread tracker = new Thread(() -> {
            long deadline = System.currentTimeMillis() + TRANSIENT_ENTITY_TRACK_DURATION_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    owner.supplyFromClient(client, () -> {
                        TransientEntityPositionTracker.rememberNearby(client, entityId, TRANSIENT_ENTITY_TRACK_RANGE);
                        return null;
                    });
                    Thread.sleep(TRANSIENT_ENTITY_TRACK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException ignored) {
                    return;
                }
            }
        }, "Pathmind-TrackTransientEntity");
        tracker.setDaemon(true);
        tracker.start();
    }

    private boolean prepareSelectedItemForUse(net.minecraft.client.Minecraft client,
                                              RuntimeParameterData parameterData,
                                              InteractionHand hand,
                                              CompletableFuture<Void> future) {
        if (client == null || client.player == null || parameterData == null || parameterData.slotIndex == null) {
            return true;
        }
        if (parameterData.slotSelectionType == SlotSelectionType.GUI_CONTAINER) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.useCannotUseGuiSlots"));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }
        Inventory inventory = client.player.getInventory();
        int clampedSlot = owner.clampInventorySlot(inventory, parameterData.slotIndex);
        boolean armorSlot = clampedSlot >= Inventory.INVENTORY_SIZE
            && clampedSlot < Inventory.INVENTORY_SIZE + Node.PLAYER_ARMOR_SLOT_COUNT;
        if (armorSlot) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.useCannotActivateArmorSlots"));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        ItemStack stack = inventory.getItem(clampedSlot);
        if (stack.isEmpty()) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.selectedSlotEmpty", owner.getType().getDisplayName()));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return false;
        }

        java.util.concurrent.atomic.AtomicBoolean preparedRef = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            owner.runOnClientThread(client, () -> {
                if (hand == InteractionHand.OFF_HAND) {
                    preparedRef.set(ensureStackEquippedInOffhand(client, inventory, clampedSlot, stack));
                } else {
                    preparedRef.set(ensureStackSelectedInMainHand(client, inventory, clampedSlot, stack));
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (future != null && !future.isDone()) {
                future.completeExceptionally(e);
            }
            return false;
        }
        boolean prepared = preparedRef.get();

        if (!prepared) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.failedPrepareSelectedItem", owner.getType().getDisplayName()));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
        }
        return prepared;
    }

    boolean ensureStackSelectedInMainHand(net.minecraft.client.Minecraft client,
                                                  Inventory inventory,
                                                  int slotIndex,
                                                  ItemStack stack) {
        if (client == null || client.player == null || inventory == null || stack == null) {
            return false;
        }
        int hotbarSize = Inventory.getSelectionSize();
        int targetSlot = slotIndex;
        if (slotIndex >= hotbarSize) {
            targetSlot = moveInventoryStackToHotbar(client, inventory, slotIndex, stack.getItem());
            if (targetSlot == -1) {
                return false;
            }
        }
        return HotbarSlotSynchronizer.selectHotbarSlot(client, targetSlot);
    }

    private boolean ensureStackEquippedInOffhand(net.minecraft.client.Minecraft client,
                                                 Inventory inventory,
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
        if (slotIndex >= Inventory.INVENTORY_SIZE) {
            return false;
        }
        MultiPlayerGameMode interactionManager = client.gameMode;
        AbstractContainerMenu handler = client.player.inventoryMenu;
        if (interactionManager == null || handler == null) {
            return false;
        }
        int sourceHandlerSlot = owner.mapPlayerInventorySlot(handler, slotIndex);
        int offhandHandlerSlot = owner.mapPlayerInventorySlot(handler, offhandIndex);
        if (sourceHandlerSlot < 0 || offhandHandlerSlot < 0) {
            return false;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, sourceHandlerSlot, 0, ClickType.PICKUP, client.player);
        interactionManager.handleInventoryMouseClick(handler.containerId, offhandHandlerSlot, 0, ClickType.PICKUP, client.player);
        interactionManager.handleInventoryMouseClick(handler.containerId, sourceHandlerSlot, 0, ClickType.PICKUP, client.player);

        ItemStack offhandStack = client.player.getOffhandItem();
        return !offhandStack.isEmpty() && offhandStack.is(stack.getItem());
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

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null || client.level == null) {
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

        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);
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

        boolean previousSneak = client.player.isShiftKeyDown();
        if (sneakWhilePlacing) {
            client.player.setShiftKeyDown(true);
            if (client.options != null && client.options.keyShift != null) {
                client.options.keyShift.setDown(true);
            }
        }

        boolean placed = false;
        HitResult target = client.hitResult;
        if (target instanceof BlockHitResult blockHit) {
            InteractionResult result = client.gameMode.useItemOn(client.player, hand, blockHit);
            placed = result.consumesAction();
            if (!placed && !requireBlockHit) {
                InteractionResult fallback = client.gameMode.useItem(client.player, hand);
                placed = fallback.consumesAction();
            }
        } else if (!requireBlockHit) {
            InteractionResult fallback = client.gameMode.useItem(client.player, hand);
            placed = fallback.consumesAction();
        }

        if (swingOnPlace && placed) {
            client.player.swing(hand);
        }

        if (sneakWhilePlacing && restoreSneak) {
            client.player.setShiftKeyDown(previousSneak);
            if (client.options != null && client.options.keyShift != null) {
                client.options.keyShift.setDown(previousSneak);
            }
        }

        future.complete(null);
    }

    private void handleDirectedPlaceHandPlacement(
        net.minecraft.client.Minecraft client,
        InteractionHand hand,
        String parameterBlockId,
        BlockPos targetPos,
        boolean sneakWhilePlacing,
        boolean restoreSneak,
        boolean swingOnPlace,
        CompletableFuture<Void> future
    ) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
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
            owner.sendNodeErrorMessage(client, tr("pathmind.error.placeNoBlockSelected"));
            future.complete(null);
            return;
        }

        Block desiredBlock = owner.resolveBlockForPlacement(blockIdToUse);
        if (desiredBlock == null) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.placeUnknownBlock", blockIdToUse));
            future.complete(null);
            return;
        }

        double reachSquared = owner.getPlacementReachSquared(client);
        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = blockIdToUse;
        final InteractionHand resolvedHand = hand;
        final boolean shouldSwing = swingOnPlace;
        final boolean shouldSneak = sneakWhilePlacing;
        final boolean shouldRestoreSneak = restoreSneak;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = owner.supplyFromClient(client, () ->
                    owner.preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, reachSquared)
                );
                boolean initialSneak = owner.supplyFromClient(client, () -> client.player.isShiftKeyDown());
                if (shouldSneak) {
                    owner.runOnClientThread(client, () -> owner.applySneakState(client, true));
                    owner.waitForSneakSync(client, initialSneak, true);
                }
                try {
                    owner.runOnClientThread(client, () -> {
                        if (client.level.getBlockState(placementPos).is(resolvedBlock)) {
                            return;
                        }
                        InteractionResult result = client.gameMode.useItemOn(client.player, resolvedHand, placementHitResult);
                        if (!result.consumesAction()) {
                            throw new Node.PlacementFailure(tr("pathmind.error.placeRejected", formatBlockPos(placementPos), result));
                        }
                        if (shouldSwing) {
                            client.player.swing(resolvedHand);
                        }
                    });
                } finally {
                    if (shouldSneak && shouldRestoreSneak) {
                        owner.runOnClientThread(client, () -> owner.applySneakState(client, initialSneak));
                    }
                }
                boolean placed = owner.waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    owner.sendNodeErrorMessage(client, tr("pathmind.error.placeDidNotAppear", resolvedBlockId, formatBlockPos(placementPos)));
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.placeFailed", resolvedBlockId, e.getMessage()));
                future.complete(null);
            }
        }, "Pathmind-PlaceHand").start();
    }

    void ensureBlockInHand(net.minecraft.client.Minecraft client, String blockId, InteractionHand hand) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        Identifier identifier = BlockSelection.extractBlockIdentifier(blockId);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new Node.PlacementFailure("Cannot place block \"" + blockId + "\": unknown block item.");
        }

        Item targetItem = BuiltInRegistries.ITEM.getValue(identifier);
        ItemStack current = client.player.getItemInHand(hand);
        if (!current.isEmpty() && current.is(targetItem)) {
            return;
        }

        Inventory inventory = client.player.getInventory();
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

        if (hand == InteractionHand.MAIN_HAND) {
            HotbarSlotSynchronizer.selectHotbarSlot(client, slot);
            return;
        }

        ItemStack offhandStack = client.player.getOffhandItem();
        if (!offhandStack.isEmpty() && offhandStack.is(targetItem)) {
            return;
        }

        HotbarSlotSynchronizer.selectHotbarSlot(client, slot);
    }

    boolean waitForBlockPlacement(net.minecraft.client.Minecraft client, BlockPos targetPos, Block desiredBlock) throws InterruptedException {
        if (client == null || targetPos == null || desiredBlock == null) {
            return false;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean matches = owner.supplyFromClient(client, () -> {
                if (client.level == null) {
                    return false;
                }
                return client.level.getBlockState(targetPos).is(desiredBlock);
            });
            if (matches) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private boolean waitForUseBlockPlacement(net.minecraft.client.Minecraft client,
                                             BlockHitResult blockHit,
                                             Block desiredBlock) throws InterruptedException {
        if (client == null || blockHit == null || desiredBlock == null) {
            return false;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        Direction side = blockHit.getDirection();
        if (hitPos == null || side == null) {
            return false;
        }

        BlockPos offsetPos = hitPos.relative(side);
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean placed = owner.supplyFromClient(client, () -> {
                if (client.level == null) {
                    return false;
                }
                BlockState hitState = client.level.getBlockState(hitPos);
                if (hitState.is(desiredBlock)) {
                    return true;
                }
                BlockState offsetState = client.level.getBlockState(offsetPos);
                return offsetState.is(desiredBlock);
            });
            if (placed) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    int findHotbarSlotWithItem(Inventory inventory, Item targetItem) {
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findMainInventorySlotWithItem(Inventory inventory, Item targetItem) {
        if (inventory == null || targetItem == null) {
            return -1;
        }
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = hotbarSize; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot(Inventory inventory) {
        if (inventory == null) {
            return -1;
        }
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int moveInventoryStackToHotbar(net.minecraft.client.Minecraft client, Inventory inventory, int inventorySlot, Item targetItem) {
        if (client == null || client.player == null || client.gameMode == null) {
            return -1;
        }
        AbstractContainerMenu handler = client.player.containerMenu;
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

        client.gameMode.handleInventoryMouseClick(handler.containerId, handlerSlot, targetHotbarSlot, ClickType.SWAP, client.player);

        ItemStack hotbarStack = inventory.getItem(targetHotbarSlot);
        if (hotbarStack.isEmpty() || !hotbarStack.is(targetItem)) {
            return -1;
        }
        return targetHotbarSlot;
    }

    BlockHitResult preparePlacementHitResult(net.minecraft.client.Minecraft client, BlockPos targetPos, String blockId, InteractionHand hand, double reachSquared) {
        if (client.player == null || client.level == null) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": client world is unavailable.");
        }

        Vec3 eyePos = client.player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        if (eyePos.distanceToSqr(targetCenter) > reachSquared) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": target is out of reach.");
        }

        if (!isBlockReplaceable(client.level, targetPos)) {
            BlockState occupied = client.level.getBlockState(targetPos);
            throw new Node.PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": target space contains " + describeBlockState(occupied) + "."
            );
        }

        Vec3 preferredLook = null;
        if (owner.runtimeState().runtimeParameterData != null
            && owner.runtimeState().runtimeParameterData.resolvedYaw != null
            && owner.runtimeState().runtimeParameterData.resolvedPitch != null) {
            double yawRad = Math.toRadians(owner.runtimeState().runtimeParameterData.resolvedYaw);
            double pitchRad = Math.toRadians(owner.runtimeState().runtimeParameterData.resolvedPitch);
            double xDir = -Math.sin(yawRad) * Math.cos(pitchRad);
            double yDir = -Math.sin(pitchRad);
            double zDir = Math.cos(yawRad) * Math.cos(pitchRad);
            preferredLook = new Vec3(xDir, yDir, zDir).normalize();
        }

        BlockHitResult surface = createPlacementHitResult(client, targetPos, eyePos, reachSquared, preferredLook);
        if (surface == null) {
            throw new Node.PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": no nearby surface to place against.");
        }

        ensureBlockInHand(client, blockId, hand);

        ItemStack stack = client.player.getItemInHand(hand);
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

    private BlockHitResult createPlacementHitResult(net.minecraft.client.Minecraft client, BlockPos targetPos, Vec3 eyePos, double reachSquared, Vec3 preferredLook) {
        if (client.player == null || client.level == null) {
            return null;
        }

        BlockHitResult bestResult = null;
        double bestDistance = Double.MAX_VALUE;
        double bestAlignment = -Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.relative(direction);
            BlockState supportState = client.level.getBlockState(supportPos);
            if (supportState.isAir()) {
                continue;
            }
            if (supportState.getCollisionShape(client.level, supportPos).isEmpty()) {
                continue;
            }

            Direction placementSide = direction.getOpposite();
            Vec3 faceCenter = Vec3.atCenterOf(supportPos).add(
                placementSide.getStepX() * 0.5D,
                placementSide.getStepY() * 0.5D,
                placementSide.getStepZ() * 0.5D
            );

            Vec3 faceAxisA;
            Vec3 faceAxisB;
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

            Vec3 placementNormal = Vec3.atLowerCornerOf(placementSide.getUnitVec3i());
            double faceAlignment = preferredLook != null ? preferredLook.dot(placementNormal) : 0.0D;

            for (double offsetA : FACE_OFFSET_SAMPLES) {
                for (double offsetB : FACE_OFFSET_SAMPLES) {
                    Vec3 samplePoint = faceCenter
                        .add(faceAxisA.scale(offsetA))
                        .add(faceAxisB.scale(offsetB));
                    double distance = eyePos.distanceToSqr(samplePoint);
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
                            samplePoint.subtract(placementNormal.scale(0.001D)),
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
    private static final Vec3 FACE_AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 FACE_AXIS_Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 FACE_AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);

    private boolean canPlaceBlockAt(net.minecraft.client.Minecraft client, InteractionHand hand, ItemStack stack, BlockItem blockItem, BlockHitResult hitResult) {
        if (client.player == null || client.level == null) {
            return false;
        }

        BlockPlaceContext placementContext = new BlockPlaceContext(client.player, hand, stack.copy(), hitResult);
        if (!placementContext.canPlace()) {
            return false;
        }

        Block block = blockItem.getBlock();
        BlockState placementState = block.getStateForPlacement(placementContext);
        if (placementState == null) {
            return false;
        }

        return placementState.canSurvive(client.level, placementContext.getClickedPos());
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
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }

        return BuiltInRegistries.BLOCK.getValue(identifier);
    }

    double getPlacementReachSquared(net.minecraft.client.Minecraft client) {
        return Node.getBlockInteractionReachSquared(client);
    }

    private String describeBlockState(BlockState state) {
        if (state == null) {
            return "an unknown block";
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null ? id.toString() : "an unknown block";
    }

    boolean isBlockReplaceable(Level world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        return state.getCollisionShape(world, targetPos).isEmpty();
    }

    boolean hasPlacementSupport(Level world, BlockPos targetPos) {
        if (world == null || targetPos == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.relative(direction);
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
        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);

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

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.player.connection == null || client.gameMode == null || client.level == null) {
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
            owner.sendNodeErrorMessage(client, tr("pathmind.error.placeNoBlockSelected"));
            future.complete(null);
            return;
        }

        if (blockParameterNode != null && isBlockPlacementParameter(blockParameterNode)) {
            try {
                ensureBlockInHand(client, block, InteractionHand.MAIN_HAND);
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
                    : client.player.getYRot();
                float pitch = parameterData != null && parameterData.resolvedPitch != null
                    ? parameterData.resolvedPitch
                    : client.player.getXRot();
                double reachDistance = Math.sqrt(getPlacementReachSquared(client));
                double lookDistance = parameterData != null && parameterData.resolvedLookDistance != null
                    ? parameterData.resolvedLookDistance
                    : reachDistance;
                BlockHitResult hit = owner.supplyFromClient(client, () ->
                    owner.raycastBlockFromOrientation(client, yaw, pitch, lookDistance)
                );
                if (hit != null) {
                    owner.runOnClientThread(client, () -> {
                        client.player.setYRot(yaw);
                        client.player.setXRot(pitch);
                        client.player.setYHeadRot(yaw);
                        InteractionResult result = client.gameMode.useItemOn(client.player, hand, hit);
                        if (result.consumesAction()) {
                            client.player.swing(hand);
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
            owner.sendNodeErrorMessage(client, tr("pathmind.error.placeUnknownBlock", block));
            future.complete(null);
            return;
        }

        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = block;
        final InteractionHand resolvedHand = hand;
        final double resolvedReachSquared = reachSquared;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = owner.supplyFromClient(client, () ->
                    preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, resolvedReachSquared)
                );
                owner.runOnClientThread(client, () -> {
                    if (client.level.getBlockState(placementPos).is(resolvedBlock)) {
                        return;
                    }

                    InteractionResult result = client.gameMode.useItemOn(client.player, resolvedHand, placementHitResult);
                    if (!result.consumesAction()) {
                        throw new Node.PlacementFailure(tr("pathmind.error.placeRejected", formatBlockPos(placementPos), result));
                    }
                    if (client.player != null) {
                        client.player.swing(resolvedHand);
                    }
                });
                boolean placed = waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    owner.sendNodeErrorMessage(client, tr("pathmind.error.placeDidNotAppear", resolvedBlockId, formatBlockPos(placementPos)));
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (Node.PlacementFailure e) {
                owner.sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.placeFailed", resolvedBlockId, e.getMessage()));
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

    String resolveBlockIdFromInventorySlotParameter(net.minecraft.client.Minecraft client,
                                                           Node parameterNode) {
        if (client == null || client.player == null || parameterNode == null) {
            return null;
        }
        SlotSelectionType selectionType = owner.resolveInventorySlotSelectionType(parameterNode);
        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.onlyPlayerInventorySlots", owner.getType().getDisplayName()));
            return null;
        }
        Inventory inventory = client.player.getInventory();
        int slotValue = owner.clampInventorySlot(inventory, Node.parseNodeInt(parameterNode, "Slot", 0));
        ItemStack stack = inventory.getItem(slotValue);
        if (stack.isEmpty()) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.selectedSlotEmpty", owner.getType().getDisplayName()));
            return null;
        }
        if (!(stack.getItem() instanceof BlockItem)) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.selectedSlotNoBlock", owner.getType().getDisplayName()));
            return null;
        }
        if (owner.runtimeState().runtimeParameterData == null) {
            owner.runtimeState().runtimeParameterData = new RuntimeParameterData();
        }
        owner.runtimeState().runtimeParameterData.slotIndex = slotValue;
        owner.runtimeState().runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
        if (!ensureStackSelectedInMainHand(client, inventory, slotValue, stack)) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.failedPrepareSelectedBlock", owner.getType().getDisplayName()));
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    String getBlockIdFromHand(net.minecraft.client.Minecraft client, InteractionHand hand) {
        if (client == null || client.player == null) {
            return null;
        }
        ItemStack stack = client.player.getItemInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : null;
    }

    String resolveAnyBlockId(net.minecraft.client.Minecraft client, InteractionHand preferredHand) {
        if (client == null || client.player == null) {
            return null;
        }
        String fromHand = getBlockIdFromHand(client, preferredHand);
        if (fromHand != null && !fromHand.isEmpty()) {
            return fromHand;
        }
        Inventory inventory = client.player.getInventory();
        if (inventory == null) {
            return null;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        Vec3 targetVector = owner.runtimeState().runtimeParameterData != null ? owner.runtimeState().runtimeParameterData.targetVector : null;
        if (targetVector != null) {
            return BlockPos.containing(targetVector);
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
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        return client.player.blockPosition();
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
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        
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
