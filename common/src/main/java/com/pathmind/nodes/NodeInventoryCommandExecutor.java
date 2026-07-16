package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.InventorySlotModeHelper;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.util.BlockSelection;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class NodeInventoryCommandExecutor {
    private final Node owner;
    private final NodeType type;
    private final NodeRuntimeState runtimeState;

    NodeInventoryCommandExecutor(Node owner) {
        this.owner = owner;
        this.type = owner.getType();
        this.runtimeState = owner.runtimeState();
    }

    void executeHotbarCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.player.connection == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Inventory inventory = client.player.getInventory();
        String itemId = getStringParameter("Item", "").trim();
        int slot;

        if (!itemId.isEmpty()) {
            List<String> itemIds = splitMultiValueList(itemId);
            int foundSlot = -1;
            for (String candidateId : itemIds) {
                String sanitized = sanitizeResourceId(candidateId);
                String normalized = sanitized != null && !sanitized.isEmpty()
                    ? normalizeResourceId(sanitized, "minecraft")
                    : candidateId;
                Identifier identifier = Identifier.tryParse(normalized);
                if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                    continue;
                }
                Item targetItem = BuiltInRegistries.ITEM.getValue(identifier);
                foundSlot = findHotbarSlotWithItem(inventory, targetItem);
                if (foundSlot != -1) {
                    break;
                }
            }
            if (foundSlot == -1) {
                sendNodeErrorMessage(client, tr("pathmind.error.noMatchingHotbarItem"));
                future.complete(null);
                return;
            }
            slot = foundSlot;
        } else {
            RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
            int resolvedSlot = parameterData != null && parameterData.slotIndex != null
                ? parameterData.slotIndex
                : getIntParameter("Slot", 0);
            slot = Mth.clamp(resolvedSlot, 0, Inventory.getSelectionSize() - 1);
        }

        final int targetSlot = slot;
        try {
            runOnClientThread(client, () -> HotbarSlotSynchronizer.selectHotbarSlot(client, targetSlot));
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
    }
    
    void executeDropItemCommand(CompletableFuture<Void> future) {
        executeDropCommand(future);
    }

    void executeDropSlotCommand(CompletableFuture<Void> future) {
        executeDropCommand(future);
    }

    private void executeDropCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        RuntimeParameterData parameterData = runtimeState.runtimeParameterData;
        boolean hasResolvedTarget = parameterData != null && parameterData.slotIndex != null;
        if (type == NodeType.DROP_SLOT || hasResolvedTarget) {
            executeResolvedDropTarget(client, parameterData, future);
            return;
        }

        boolean dropAll = getBooleanParameter("All", false);
        int count = shouldUseDropItemAmount() ? Math.max(1, getIntParameter("Count", 1)) : 1;
        double interval = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));
        Inventory inventory = client.player.getInventory();
        AbstractContainerMenu handler = client.player.containerMenu;
        MultiPlayerGameMode interactionManager = client.gameMode;
        SlotResolution selectedSlot = resolveInventorySlot(
            handler,
            inventory,
            inventory != null ? PlayerInventoryBridge.getSelectedSlot(inventory) : 0,
            SlotSelectionType.PLAYER_INVENTORY
        );
        if (interactionManager == null || handler == null || inventory == null || selectedSlot == null || selectedSlot.slot == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }
        final boolean dropEntireStack = dropAll
            || (selectedSlot.slot.getItem() != null && count >= selectedSlot.slot.getItem().getCount());
        final int requestedCount = count;

        new Thread(() -> {
            try {
                if (interval <= 0.0) {
                    runOnClientThread(client, () -> {
                        dropSlotCount(client, interactionManager, handler, inventory, selectedSlot.handlerSlotIndex, requestedCount, dropEntireStack);
                    });
                } else {
                    int dropIterations = dropEntireStack ? 1 : requestedCount;
                    for (int i = 0; i < dropIterations; i++) {
                        final boolean entireStackThisClick = dropEntireStack;
                        runOnClientThread(client, () -> {
                            dropSlotCount(client, interactionManager, handler, inventory, selectedSlot.handlerSlotIndex, 1, entireStackThisClick);
                        });
                        if (i < dropIterations - 1) {
                            Thread.sleep((long) (interval * 1000));
                        }
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-DropItem").start();
    }

    void executeClickSlotCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        MultiPlayerGameMode interactionManager = client.gameMode;
        AbstractContainerMenu handler = client.player.containerMenu;
        if (interactionManager == null || handler == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        Inventory inventory = client.player.getInventory();
        int slotValue = getIntParameter("Slot", 0);
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(0);
        SlotResolution resolution = resolveInventorySlot(handler, inventory, slotValue, selectionType);
        if (resolution == null || resolution.slot == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clickSlotRequiresValidSelection"));
            future.complete(null);
            return;
        }

        interactionManager.handleInventoryMouseClick(
            handler.containerId,
            resolution.handlerSlotIndex,
            0,
            ClickType.PICKUP,
            client.player
        );

        inventory.setChanged();
        client.player.inventoryMenu.broadcastChanges();
        future.complete(null);
    }

    void executeClickScreenCommand(CompletableFuture<Void> future) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        if (client.screen == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clickScreenRequiresOpenGui"));
            future.complete(null);
            return;
        }

        int guiX = getIntParameter("X", 0);
        int guiY = getIntParameter("Y", 0);
        com.mojang.blaze3d.platform.Window window = client.getWindow();
        int scaledWidth = Math.max(1, window.getGuiScaledWidth());
        int scaledHeight = Math.max(1, window.getGuiScaledHeight());
        guiX = Mth.clamp(guiX, 0, Math.max(0, scaledWidth - 1));
        guiY = Mth.clamp(guiY, 0, Math.max(0, scaledHeight - 1));

        double windowScaleX = window.getScreenWidth() / (double) scaledWidth;
        double windowScaleY = window.getScreenHeight() / (double) scaledHeight;
        double windowX = guiX * windowScaleX;
        double windowY = guiY * windowScaleY;
        final int targetGuiX = guiX;
        final int targetGuiY = guiY;

        client.execute(() -> {
            boolean moved = InputCompatibilityBridge.dispatchCursorPos(client, windowX, windowY);
            boolean pressed = InputCompatibilityBridge.dispatchScreenMouseClicked(
                client.screen,
                targetGuiX,
                targetGuiY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
            );
            if (!pressed) {
                pressed = InputCompatibilityBridge.dispatchMouseButton(
                    client,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT,
                    GLFW.GLFW_PRESS,
                    0
                );
            }
            if (!moved || !pressed) {
                sendNodeErrorMessage(client, tr("pathmind.error.screenClickDispatchFailed"));
                future.complete(null);
                return;
            }

            Node.MESSAGE_SCHEDULER.schedule(() -> {
                net.minecraft.client.Minecraft releaseClient = net.minecraft.client.Minecraft.getInstance();
                if (releaseClient == null) {
                    future.complete(null);
                    return;
                }
                releaseClient.execute(() -> {
                    boolean released = InputCompatibilityBridge.dispatchScreenMouseReleased(
                        releaseClient.screen,
                        targetGuiX,
                        targetGuiY,
                        GLFW.GLFW_MOUSE_BUTTON_LEFT
                    );
                    if (!released) {
                        InputCompatibilityBridge.dispatchMouseButton(
                            releaseClient,
                            GLFW.GLFW_MOUSE_BUTTON_LEFT,
                            GLFW.GLFW_RELEASE,
                            0
                        );
                    }
                    future.complete(null);
                });
            }, 75L, TimeUnit.MILLISECONDS);
        });
    }
    
    void executeMoveItemCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        MultiPlayerGameMode interactionManager = client.gameMode;
        AbstractContainerMenu handler = client.player.containerMenu;
        if (interactionManager == null || handler == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        if (!handler.getCarried().isEmpty()) {
            sendNodeErrorMessage(client, tr("pathmind.error.cursorHoldingStack"));
            future.complete(null);
            return;
        }

        Inventory inventory = client.player.getInventory();
        int requestedSourceSlot = getIntParameter("SourceSlot", 0);
        int requestedTargetSlot = getIntParameter("TargetSlot", 0);

        boolean shiftClickTarget = false;
        boolean moveAllMatchingStacks = false;
        Node sourceParameterNode = getAttachedParameter(0);
        Node targetParameterNode = getAttachedParameter(1);
        SlotSelectionType sourceSelection = resolveMoveItemSlotSelectionType(sourceParameterNode, 0);
        SlotSelectionType targetSelection = resolveMoveItemSlotSelectionType(targetParameterNode, 1);
        GuiSelectionMode targetGuiMode = null;
        if (targetParameterNode != null && targetParameterNode.getType() == NodeType.PARAM_GUI) {
            shiftClickTarget = true;
            targetGuiMode = GuiSelectionMode.fromId(getParameterString(targetParameterNode, "GUI"));
        }

        if (shiftClickTarget && targetGuiMode != null) {
            SlotSelectionType desiredSourceSelection = targetGuiMode == GuiSelectionMode.PLAYER_INVENTORY
                ? SlotSelectionType.GUI_CONTAINER
                : SlotSelectionType.PLAYER_INVENTORY;
            if (sourceParameterNode != null && sourceParameterNode.getType() == NodeType.PARAM_ITEM) {
                moveAllMatchingStacks = shouldMoveAllMatchingStacks(sourceParameterNode);
                if (!resolveMoveItemSlotFromItemParameter(sourceParameterNode, 0, desiredSourceSelection, future)) {
                    return;
                }
                requestedSourceSlot = getIntParameter("SourceSlot", 0);
            }
            sourceSelection = desiredSourceSelection;
        }

        SlotResolution sourceResolution = resolveInventorySlot(handler, inventory, requestedSourceSlot, sourceSelection);
        SlotResolution targetResolution = shiftClickTarget
            ? null
            : resolveInventorySlot(handler, inventory, requestedTargetSlot, targetSelection);

        if (sourceResolution == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.moveItemSourceUnresolved"));
            future.complete(null);
            return;
        }
        if (!shiftClickTarget && targetResolution == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.moveItemTargetUnresolved"));
            future.complete(null);
            return;
        }

        if (!shiftClickTarget && sourceResolution.handlerSlotIndex == targetResolution.handlerSlotIndex) {
            sendNodeErrorMessage(client, tr("pathmind.error.moveItemSameSlot"));
            future.complete(null);
            return;
        }

        ItemStack sourceStack = sourceResolution.slot.getItem();
        if (sourceStack.isEmpty()) {
            sendNodeErrorMessage(client, tr("pathmind.error.moveItemSourceEmpty"));
            future.complete(null);
            return;
        }

        int requestedCount = getIntParameter("Count", 0);
        int available = sourceStack.getCount();
        int moveCount = requestedCount <= 0 ? available : Math.min(requestedCount, available);
        if (moveCount <= 0) {
            future.complete(null);
            return;
        }

        boolean moveEntireStack = moveCount >= available;

        if (shiftClickTarget) {
            if (moveAllMatchingStacks) {
                quickMoveAllMatchingStacks(client, interactionManager, handler, inventory, sourceParameterNode, sourceSelection);
            } else if (requestedCount > 0) {
                moveRequestedCountToGuiTarget(
                    client,
                    interactionManager,
                    handler,
                    inventory,
                    sourceParameterNode,
                    sourceResolution,
                    sourceSelection,
                    moveCount
                );
            } else {
                interactionManager.handleInventoryMouseClick(
                    handler.containerId,
                    sourceResolution.handlerSlotIndex,
                    0,
                    ClickType.QUICK_MOVE,
                    client.player
                );
            }
        } else {
            performInventoryTransfer(
                interactionManager,
                handler,
                client.player,
                sourceResolution.handlerSlotIndex,
                targetResolution.handlerSlotIndex,
                moveCount,
                moveEntireStack
            );
        }

        inventory.setChanged();
        client.player.inventoryMenu.broadcastChanges();
        future.complete(null);
    }

    private boolean shouldMoveAllMatchingStacks(Node sourceParameterNode) {
        if (type != NodeType.MOVE_ITEM || sourceParameterNode == null || sourceParameterNode.getType() != NodeType.PARAM_ITEM) {
            return false;
        }
        Node targetParameterNode = getAttachedParameter(1);
        if (targetParameterNode == null || targetParameterNode.getType() != NodeType.PARAM_GUI) {
            return false;
        }
        String countValue = getParameterString(owner, "Count");
        return countValue == null
            || countValue.trim().isEmpty()
            || "0".equals(countValue.trim())
            || "all".equalsIgnoreCase(countValue.trim())
            || "any".equalsIgnoreCase(countValue.trim());
    }

    private void quickMoveAllMatchingStacks(net.minecraft.client.Minecraft client,
                                            MultiPlayerGameMode interactionManager,
                                            AbstractContainerMenu handler,
                                            Inventory inventory,
                                            Node sourceParameterNode,
                                            SlotSelectionType sourceSelection) {
        if (client == null || client.player == null || interactionManager == null || handler == null || inventory == null || sourceParameterNode == null) {
            return;
        }

        List<String> itemIds = resolveItemIdsFromParameter(sourceParameterNode);
        boolean anySelection = itemIds.isEmpty()
            && (isAnySelectionValue(getParameterString(sourceParameterNode, "Item"))
                || isAnySelectionValue(getParameterString(sourceParameterNode, "Items")));
        int movedStacks = 0;
        int stalledAttempts = 0;

        while (stalledAttempts < 2) {
            SlotResolution nextResolution = findNextMoveItemSourceResolution(client, handler, inventory, itemIds, anySelection, sourceSelection);
            if (nextResolution == null || nextResolution.slot == null) {
                break;
            }

            ItemStack beforeStack = nextResolution.slot.getItem().copy();
            if (beforeStack.isEmpty()) {
                break;
            }

            interactionManager.handleInventoryMouseClick(
                handler.containerId,
                nextResolution.handlerSlotIndex,
                0,
                ClickType.QUICK_MOVE,
                client.player
            );

            ItemStack afterStack = nextResolution.slot.getItem();
            boolean moved = afterStack.isEmpty()
                || afterStack.getCount() < beforeStack.getCount()
                || !ItemStack.isSameItemSameComponents(afterStack, beforeStack);
            if (moved) {
                movedStacks++;
                stalledAttempts = 0;
            } else {
                stalledAttempts++;
                break;
            }
        }

        if (movedStacks == 0) {
            sendNodeErrorMessage(client, tr("pathmind.error.noMatchingStacksQuickMoved", type.getDisplayName()));
        }
    }

    private void moveRequestedCountToGuiTarget(net.minecraft.client.Minecraft client,
                                               MultiPlayerGameMode interactionManager,
                                               AbstractContainerMenu handler,
                                               Inventory inventory,
                                               Node sourceParameterNode,
                                               SlotResolution initialSourceResolution,
                                               SlotSelectionType sourceSelection,
                                               int requestedCount) {
        if (client == null || client.player == null || interactionManager == null || handler == null || requestedCount <= 0) {
            return;
        }

        SlotSelectionType destinationSelection = sourceSelection == SlotSelectionType.GUI_CONTAINER
            ? SlotSelectionType.PLAYER_INVENTORY
            : SlotSelectionType.GUI_CONTAINER;
        int remaining = requestedCount;
        SlotResolution currentResolution = initialSourceResolution;

        List<String> itemIds = List.of();
        boolean anySelection = false;
        boolean iterateMatchingSources = sourceParameterNode != null && sourceParameterNode.getType() == NodeType.PARAM_ITEM;
        if (iterateMatchingSources) {
            itemIds = resolveItemIdsFromParameter(sourceParameterNode);
            anySelection = itemIds.isEmpty()
                && (isAnySelectionValue(getParameterString(sourceParameterNode, "Item"))
                    || isAnySelectionValue(getParameterString(sourceParameterNode, "Items")));
        }

        while (remaining > 0 && currentResolution != null && currentResolution.slot != null) {
            ItemStack source = currentResolution.slot.getItem();
            if (source.isEmpty()) {
                if (!iterateMatchingSources) {
                    break;
                }
                currentResolution = findNextMoveItemSourceResolution(client, handler, inventory, itemIds, anySelection, sourceSelection);
                continue;
            }

            int transferAmount = Math.min(remaining, source.getCount());
            SlotResolution destinationResolution = findTransferDestinationResolution(
                handler,
                destinationSelection,
                source,
                transferAmount
            );
            if (destinationResolution == null) {
                break;
            }

            performInventoryTransfer(
                interactionManager,
                handler,
                client.player,
                currentResolution.handlerSlotIndex,
                destinationResolution.handlerSlotIndex,
                transferAmount,
                transferAmount >= source.getCount()
            );
            remaining -= transferAmount;

            if (!iterateMatchingSources) {
                break;
            }
            currentResolution = findNextMoveItemSourceResolution(client, handler, inventory, itemIds, anySelection, sourceSelection);
        }
    }

    private SlotResolution findNextMoveItemSourceResolution(net.minecraft.client.Minecraft client,
                                                            AbstractContainerMenu handler,
                                                            Inventory inventory,
                                                            List<String> itemIds,
                                                            boolean anySelection,
                                                            SlotSelectionType selectionType) {
        if (client == null || client.player == null || handler == null) {
            return null;
        }

        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot == null || slot.getItem().isEmpty() || !isSlotInSelectionType(slot, selectionType)) {
                    continue;
                }
                if (matchesMoveItemSource(slot.getItem(), itemIds, anySelection)) {
                    return new SlotResolution(slot, i);
                }
            }
            return null;
        }

        if (inventory == null) {
            return null;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !matchesMoveItemSource(stack, itemIds, anySelection)) {
                continue;
            }
            int handlerSlot = mapPlayerInventorySlot(handler, clampInventorySlot(inventory, i));
            if (handlerSlot < 0 || handlerSlot >= handler.slots.size()) {
                continue;
            }
            Slot slot = handler.getSlot(handlerSlot);
            if (slot != null && !slot.getItem().isEmpty() && isSlotInSelectionType(slot, selectionType)) {
                return new SlotResolution(slot, handlerSlot);
            }
        }
        return null;
    }

    private SlotResolution findTransferDestinationResolution(AbstractContainerMenu handler,
                                                             SlotSelectionType selectionType,
                                                             ItemStack sourceStack,
                                                             int transferAmount) {
        if (handler == null || selectionType == null || sourceStack == null || sourceStack.isEmpty() || transferAmount <= 0) {
            return null;
        }

        SlotResolution emptySlot = null;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot == null || !isSlotInSelectionType(slot, selectionType)) {
                continue;
            }
            if (!slot.mayPlace(sourceStack)) {
                continue;
            }

            ItemStack destinationStack = slot.getItem();
            if (!destinationStack.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(destinationStack, sourceStack)) {
                    continue;
                }
                int maxCount = Math.min(slot.getMaxStackSize(sourceStack), sourceStack.getMaxStackSize());
                int space = Math.max(0, maxCount - destinationStack.getCount());
                if (space >= transferAmount) {
                    return new SlotResolution(slot, i);
                }
                continue;
            }

            if (emptySlot == null) {
                emptySlot = new SlotResolution(slot, i);
            }
        }
        return emptySlot;
    }

    private boolean matchesMoveItemSource(ItemStack stack, List<String> itemIds, boolean anySelection) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (anySelection) {
            return true;
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item candidateItem = BuiltInRegistries.ITEM.getValue(identifier);
            if (stack.is(candidateItem)) {
                return true;
            }
        }
        return false;
    }

    private void performInventoryTransfer(MultiPlayerGameMode interactionManager, AbstractContainerMenu handler,
                                          Player player, int sourceSlot, int targetSlot, int moveCount, boolean moveEntireStack) {
        if (moveEntireStack) {
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
            interactionManager.handleInventoryMouseClick(handler.containerId, targetSlot, 0, ClickType.PICKUP, player);
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
            return;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
        int moved = 0;
        while (moved < moveCount) {
            int beforeCursor = handler.getCarried().getCount();
            interactionManager.handleInventoryMouseClick(handler.containerId, targetSlot, 1, ClickType.PICKUP, player);
            int afterCursor = handler.getCarried().getCount();
            if (afterCursor >= beforeCursor) {
                break;
            }
            moved++;
        }
        interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
    }

    private SlotSelectionType resolveInventorySlotSelectionType(int parameterSlotIndex) {
        Node parameterNode = getAttachedParameter(parameterSlotIndex);
        return resolveInventorySlotSelectionType(parameterNode);
    }

    private SlotSelectionType resolveMoveItemSlotSelectionType(Node parameterNode, int parameterSlotIndex) {
        if (type != NodeType.MOVE_ITEM || parameterNode == null || parameterNode.getType() != NodeType.PARAM_ITEM) {
            return resolveInventorySlotSelectionType(parameterNode);
        }

        String modeValue = getParameterString(parameterNode, "Mode");
        if (modeValue != null && !modeValue.trim().isEmpty()) {
            return resolveInventorySlotSelectionType(parameterNode);
        }

        Node counterpart = getAttachedParameter(parameterSlotIndex == 0 ? 1 : 0);
        if (counterpart != null && counterpart.getType() != NodeType.PARAM_ITEM) {
            if (counterpart.getType() == NodeType.PARAM_GUI) {
                GuiSelectionMode guiMode = GuiSelectionMode.fromId(getParameterString(counterpart, "GUI"));
                if (guiMode == GuiSelectionMode.PLAYER_INVENTORY) {
                    return SlotSelectionType.GUI_CONTAINER;
                }
                return SlotSelectionType.PLAYER_INVENTORY;
            }
            SlotSelectionType counterpartSelection = resolveInventorySlotSelectionType(counterpart);
            if (counterpartSelection == SlotSelectionType.GUI_CONTAINER) {
                return SlotSelectionType.PLAYER_INVENTORY;
            }
            return hasOpenGuiContainer()
                ? SlotSelectionType.GUI_CONTAINER
                : SlotSelectionType.PLAYER_INVENTORY;
        }

        return parameterSlotIndex == 0
            ? SlotSelectionType.PLAYER_INVENTORY
            : resolveInventorySlotSelectionType(parameterNode);
    }

    SlotSelectionType resolveMoveItemSlotSelectionTypeForTests(Node parameterNode, int parameterSlotIndex) {
        return resolveMoveItemSlotSelectionType(parameterNode, parameterSlotIndex);
    }

    private boolean hasOpenGuiContainer() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        AbstractContainerMenu handler = client.player.containerMenu;
        if (handler == null || handler instanceof InventoryMenu) {
            return false;
        }
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot != null && isSlotInSelectionType(slot, SlotSelectionType.GUI_CONTAINER)) {
                return true;
            }
        }
        return false;
    }

    SlotSelectionType resolveInventorySlotSelectionType(Node parameterNode) {
        parameterNode = resolveVariableSelectionParameterNode(parameterNode);
        if (parameterNode == null) {
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        if (parameterNode.getType() == NodeType.LIST_ITEM) {
            ListSlotEntry listSlotEntry = resolveListItemSlotEntry(parameterNode, false, null);
            if (listSlotEntry != null) {
                return listSlotEntry.selectionType;
            }
        }

        // For item parameters, check if a container GUI is open
        if (parameterNode.getType() == NodeType.PARAM_ITEM) {
            // Check if there's a mode specified on the item parameter
            String modeValue = getParameterString(parameterNode, "Mode");
            if (modeValue != null && !modeValue.isEmpty()) {
                Boolean isPlayer = InventorySlotModeHelper.extractPlayerSelectionFlag(modeValue);
                if (isPlayer != null) {
                    return isPlayer ? SlotSelectionType.PLAYER_INVENTORY : SlotSelectionType.GUI_CONTAINER;
                }
                String modeId = InventorySlotModeHelper.extractModeId(modeValue);
                if (modeId != null && !modeId.isEmpty() && !"player_inventory".equals(modeId)) {
                    return SlotSelectionType.GUI_CONTAINER;
                }
            }

            // If no mode specified, detect based on open screen
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.player != null) {
                AbstractContainerMenu handler = client.player.containerMenu;
                // If a container is open (not just the player inventory screen)
                if (handler != null && !(handler instanceof InventoryMenu)) {
                    return SlotSelectionType.GUI_CONTAINER;
                }
            }
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        if (parameterNode.getType() == NodeType.PARAM_GUI) {
            return SlotSelectionType.GUI_CONTAINER;
        }

        if (parameterNode.getType() != NodeType.PARAM_INVENTORY_SLOT) {
            return SlotSelectionType.PLAYER_INVENTORY;
        }

        String modeValue = getParameterString(parameterNode, "Mode");
        Boolean isPlayer = InventorySlotModeHelper.extractPlayerSelectionFlag(modeValue);
        if (isPlayer != null) {
            return isPlayer ? SlotSelectionType.PLAYER_INVENTORY : SlotSelectionType.GUI_CONTAINER;
        }
        String modeId = InventorySlotModeHelper.extractModeId(modeValue);
        if (modeId != null && !modeId.isEmpty() && !"player_inventory".equals(modeId)) {
            return SlotSelectionType.GUI_CONTAINER;
        }
        return SlotSelectionType.PLAYER_INVENTORY;
    }

    private Node resolveVariableSelectionParameterNode(Node parameterNode) {
        if (parameterNode == null || parameterNode.getType() != NodeType.VARIABLE) {
            return parameterNode;
        }
        int slotIndex = parameterNode.getParentParameterSlotIndex();
        if (slotIndex < 0) {
            slotIndex = 0;
        }
        Node resolved = owner.resolveVariableValueNode(parameterNode, slotIndex, null);
        return resolved != null ? resolved : parameterNode;
    }

    SlotResolution resolveInventorySlot(AbstractContainerMenu handler, Inventory inventory, int slotValue, SlotSelectionType selectionType) {
        if (handler == null) {
            return null;
        }
        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            if (slotValue < 0 || slotValue >= handler.slots.size()) {
                return null;
            }
            Slot slot = handler.getSlot(slotValue);
            if (slot == null || !isSlotInSelectionType(slot, selectionType)) {
                return null;
            }
            return new SlotResolution(slot, slotValue);
        }
        if (inventory == null) {
            return null;
        }
        int clamped = clampInventorySlot(inventory, slotValue);
        int handlerSlot = mapPlayerInventorySlot(handler, clamped);
        if (handlerSlot < 0 || handlerSlot >= handler.slots.size()) {
            return null;
        }
        Slot slot = handler.getSlot(handlerSlot);
        if (slot == null) {
            return null;
        }
        return new SlotResolution(slot, handlerSlot);
    }

    private boolean isSlotInSelectionType(Slot slot, SlotSelectionType selectionType) {
        if (slot == null || selectionType == null) {
            return false;
        }
        boolean playerInventorySlot = slot.container instanceof Inventory;
        return selectionType == SlotSelectionType.GUI_CONTAINER ? !playerInventorySlot : playerInventorySlot;
    }

    boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex, CompletableFuture<Void> future) {
        SlotSelectionType selectionType = resolveMoveItemSlotSelectionType(parameterNode, slotIndex);
        return resolveMoveItemSlotFromItemParameter(parameterNode, slotIndex, selectionType, future);
    }

    private boolean isDropNodeType() {
        return type == NodeType.DROP_ITEM || type == NodeType.DROP_SLOT;
    }

    private boolean shouldUseDropItemAmount() {
        if (type != NodeType.DROP_ITEM) {
            return false;
        }
        NodeParameter useAmount = getParameter("UseAmount");
        if (useAmount != null) {
            return useAmount.getBoolValue();
        }
        return getIntParameter("Count", 1) > 1;
    }

    boolean resolveDropParameterSelection(Node parameterNode, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return false;
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }
        if (providesTrait(parameterNode, NodeValueTrait.INVENTORY_SLOT)) {
            runtimeState.runtimeParameterData.slotIndex = parseNodeInt(parameterNode, "Slot", 0);
            runtimeState.runtimeParameterData.slotSelectionType = resolveInventorySlotSelectionType(parameterNode);
            return true;
        }
        if (providesTrait(parameterNode, NodeValueTrait.ITEM)) {
            return resolveDropSlotFromItemParameter(parameterNode, resolveInventorySlotSelectionType(parameterNode), future);
        }
        return false;
    }

    private boolean resolveDropSlotFromItemParameter(Node parameterNode,
                                                     SlotSelectionType selectionType,
                                                     CompletableFuture<Void> future) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }

        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        boolean anySelection = itemIds.isEmpty()
            && (isAnySelectionValue(getParameterString(parameterNode, "Item"))
                || isAnySelectionValue(getParameterString(parameterNode, "Items")));
        if (itemIds.isEmpty() && !anySelection) {
            sendParameterSearchFailure(tr("pathmind.error.noItemSelectedOnParameter", type.getDisplayName()), future);
            return false;
        }

        AbstractContainerMenu handler = client.player.containerMenu;
        int foundSlot = -1;
        String matchedItemId = null;

        if (anySelection) {
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getItem().isEmpty()) {
                        foundSlot = i;
                        break;
                    }
                }
            } else {
                foundSlot = findFirstNonEmptySlot(client.player.getInventory());
            }
        }

        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item candidateItem = BuiltInRegistries.ITEM.getValue(identifier);
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getItem().isEmpty() && slot.getItem().is(candidateItem)) {
                        foundSlot = i;
                        matchedItemId = candidateId;
                        break;
                    }
                }
            } else {
                int slot = findFirstSlotWithItem(client.player.getInventory(), candidateItem);
                if (slot >= 0) {
                    foundSlot = slot;
                    matchedItemId = candidateId;
                }
            }
            if (foundSlot >= 0) {
                break;
            }
        }

        if (foundSlot < 0) {
            String reference = anySelection ? tr("pathmind.error.itemReference") : String.join(", ", itemIds);
            String locationDesc = selectionType == SlotSelectionType.GUI_CONTAINER
                ? tr("pathmind.error.locationContainer")
                : tr("pathmind.error.locationInventory");
            sendParameterSearchFailure(tr("pathmind.error.noItemFoundInLocation", reference, locationDesc, type.getDisplayName()), future);
            return false;
        }

        runtimeState.runtimeParameterData.slotIndex = foundSlot;
        runtimeState.runtimeParameterData.slotSelectionType = selectionType;
        runtimeState.runtimeParameterData.targetItemId = matchedItemId;
        return true;
    }

    private void executeResolvedDropTarget(net.minecraft.client.Minecraft client,
                                           RuntimeParameterData parameterData,
                                           CompletableFuture<Void> future) {
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        MultiPlayerGameMode interactionManager = client.gameMode;
        AbstractContainerMenu handler = client.player.containerMenu;
        Inventory inventory = client.player.getInventory();
        if (interactionManager == null || handler == null || inventory == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        SlotSelectionType selectionType = parameterData != null && parameterData.slotSelectionType != null
            ? parameterData.slotSelectionType
            : SlotSelectionType.PLAYER_INVENTORY;
        int configuredSlot = parameterData != null && parameterData.slotIndex != null
            ? parameterData.slotIndex
            : getIntParameter("Slot", 0);
        SlotResolution resolution = resolveInventorySlot(handler, inventory, configuredSlot, selectionType);
        if (resolution == null || resolution.slot == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.requiresValidSlotSelection", type.getDisplayName()));
            future.complete(null);
            return;
        }
        if (resolution.slot.getItem() == null || resolution.slot.getItem().isEmpty()) {
            future.complete(null);
            return;
        }

        boolean dropEntireStack = getBooleanParameter("All", false)
            || getBooleanParameter("EntireStack", false)
            || (type == NodeType.DROP_SLOT && getIntParameter("Count", 0) <= 0);
        int requestedCount = type == NodeType.DROP_ITEM && !shouldUseDropItemAmount()
            ? 1
            : Math.max(1, getIntParameter("Count", 1));
        double interval = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));
        int dropIterations = dropEntireStack ? 1 : requestedCount;

        new Thread(() -> {
            try {
                if (interval <= 0.0) {
                    runOnClientThread(client, () -> {
                        dropSlotCount(client, interactionManager, handler, inventory, resolution.handlerSlotIndex, requestedCount, dropEntireStack);
                    });
                } else {
                    for (int i = 0; i < dropIterations; i++) {
                        final boolean entireStackThisClick = dropEntireStack;
                        runOnClientThread(client, () -> {
                            dropSlotCount(client, interactionManager, handler, inventory, resolution.handlerSlotIndex, 1, entireStackThisClick);
                        });
                        if (i < dropIterations - 1) {
                            Thread.sleep((long) (interval * 1000));
                        }
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Drop").start();
    }

    private void dropSlotCount(net.minecraft.client.Minecraft client,
                               MultiPlayerGameMode interactionManager,
                               AbstractContainerMenu handler,
                               Inventory inventory,
                               int handlerSlotIndex,
                               int requestedCount,
                               boolean dropEntireStack) {
        if (client == null || client.player == null || interactionManager == null || handler == null || inventory == null) {
            return;
        }
        if (handlerSlotIndex < 0 || handlerSlotIndex >= handler.slots.size()) {
            return;
        }

        Slot slot = handler.getSlot(handlerSlotIndex);
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        int available = stack.getCount();
        int count = Mth.clamp(requestedCount, 1, available);
        if (dropEntireStack || count >= available) {
            interactionManager.handleInventoryMouseClick(handler.containerId, handlerSlotIndex, 1, ClickType.THROW, client.player);
            finishInventoryClickUpdates(client, inventory);
            return;
        }

        if (count == 1) {
            interactionManager.handleInventoryMouseClick(handler.containerId, handlerSlotIndex, 0, ClickType.THROW, client.player);
            finishInventoryClickUpdates(client, inventory);
            return;
        }

        if (!dropPartialStackAsSingleCursorClick(client, interactionManager, handler, inventory, handlerSlotIndex, count)) {
            for (int i = 0; i < count; i++) {
                interactionManager.handleInventoryMouseClick(handler.containerId, handlerSlotIndex, 0, ClickType.THROW, client.player);
            }
            finishInventoryClickUpdates(client, inventory);
        }
    }

    private boolean dropPartialStackAsSingleCursorClick(net.minecraft.client.Minecraft client,
                                                        MultiPlayerGameMode interactionManager,
                                                        AbstractContainerMenu handler,
                                                        Inventory inventory,
                                                        int sourceSlot,
                                                        int requestedCount) {
        if (handler.getCarried() != null && !handler.getCarried().isEmpty()) {
            return false;
        }

        Slot source = handler.getSlot(sourceSlot);
        ItemStack sourceStack = source == null ? ItemStack.EMPTY : source.getItem();
        if (sourceStack == null || sourceStack.isEmpty() || requestedCount <= 1 || requestedCount >= sourceStack.getCount()) {
            return false;
        }

        int scratchSlot = findEmptyPlayerHandlerSlot(handler, sourceSlot);
        if (scratchSlot < 0) {
            return false;
        }

        int sourceCount = sourceStack.getCount();
        if (!canSplitByHalving(sourceCount, requestedCount)) {
            return false;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 1, ClickType.PICKUP, client.player);
        if (handler.getCarried() != null
            && !handler.getCarried().isEmpty()
            && handler.getCarried().getCount() == requestedCount) {
            interactionManager.handleInventoryMouseClick(handler.containerId, -999, 0, ClickType.PICKUP, client.player);
            finishInventoryClickUpdates(client, inventory);
            return true;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, scratchSlot, 0, ClickType.PICKUP, client.player);
        interactionManager.handleInventoryMouseClick(handler.containerId, scratchSlot, 1, ClickType.PICKUP, client.player);
        while (handler.getCarried() != null
            && !handler.getCarried().isEmpty()
            && handler.getCarried().getCount() > requestedCount) {
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, client.player);
            interactionManager.handleInventoryMouseClick(handler.containerId, scratchSlot, 1, ClickType.PICKUP, client.player);
        }

        ItemStack cursorStack = handler.getCarried();
        if (cursorStack == null || cursorStack.isEmpty() || cursorStack.getCount() != requestedCount) {
            restoreCursorToSource(interactionManager, handler, client.player, sourceSlot);
            restoreScratchToSource(interactionManager, handler, client.player, scratchSlot, sourceSlot);
            finishInventoryClickUpdates(client, inventory);
            return false;
        }

        interactionManager.handleInventoryMouseClick(handler.containerId, -999, 0, ClickType.PICKUP, client.player);
        restoreScratchToSource(interactionManager, handler, client.player, scratchSlot, sourceSlot);
        finishInventoryClickUpdates(client, inventory);
        return true;
    }

    private boolean canSplitByHalving(int sourceCount, int requestedCount) {
        int cursorCount = (sourceCount + 1) / 2;
        while (cursorCount > requestedCount) {
            cursorCount = (cursorCount + 1) / 2;
        }
        return cursorCount == requestedCount;
    }

    private int findEmptyPlayerHandlerSlot(AbstractContainerMenu handler, int excludedSlot) {
        if (handler == null) {
            return -1;
        }
        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == excludedSlot) {
                continue;
            }
            Slot slot = handler.getSlot(i);
            if (slot != null && slot.container instanceof Inventory && !slot.hasItem()) {
                return i;
            }
        }
        return -1;
    }

    private void restoreCursorToSource(MultiPlayerGameMode interactionManager,
                                       AbstractContainerMenu handler,
                                       Player player,
                                       int sourceSlot) {
        if (handler.getCarried() != null && !handler.getCarried().isEmpty()) {
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
        }
    }

    private void restoreScratchToSource(MultiPlayerGameMode interactionManager,
                                        AbstractContainerMenu handler,
                                        Player player,
                                        int scratchSlot,
                                        int sourceSlot) {
        if (scratchSlot < 0 || scratchSlot >= handler.slots.size()) {
            return;
        }
        Slot scratch = handler.getSlot(scratchSlot);
        if (scratch != null && scratch.hasItem()) {
            interactionManager.handleInventoryMouseClick(handler.containerId, scratchSlot, 0, ClickType.PICKUP, player);
            interactionManager.handleInventoryMouseClick(handler.containerId, sourceSlot, 0, ClickType.PICKUP, player);
        }
    }

    private void finishInventoryClickUpdates(net.minecraft.client.Minecraft client, Inventory inventory) {
        if (client == null || client.player == null || inventory == null) {
            return;
        }
        inventory.setChanged();
        client.player.inventoryMenu.broadcastChanges();
    }

    boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex,
                                                         SlotSelectionType selectionType, CompletableFuture<Void> future) {
        if (slotIndex < 0 || slotIndex > 1) {
            return false;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }

        List<String> itemIds = resolveItemIdsFromParameter(parameterNode);
        boolean anySelection = itemIds.isEmpty()
            && (isAnySelectionValue(getParameterString(parameterNode, "Item"))
                || isAnySelectionValue(getParameterString(parameterNode, "Items")));
        if (itemIds.isEmpty() && !anySelection) {
            sendParameterSearchFailure(tr("pathmind.error.noItemSelectedOnParameter", type.getDisplayName()), future);
            return false;
        }

        AbstractContainerMenu handler = client.player.containerMenu;

        int foundSlot = -1;
        if (anySelection) {
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getItem().isEmpty() && isSlotInSelectionType(slot, selectionType)) {
                        foundSlot = i;
                        break;
                    }
                }
            } else if (client.player != null) {
                foundSlot = findFirstNonEmptySlot(client.player.getInventory());
            }
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item candidateItem = BuiltInRegistries.ITEM.getValue(identifier);

            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                // Search through all handler slots
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null
                        && !slot.getItem().isEmpty()
                        && isSlotInSelectionType(slot, selectionType)
                        && slot.getItem().is(candidateItem)) {
                        foundSlot = i;
                        break;
                    }
                }
            } else {
                // Search through player inventory
                int slot = findFirstSlotWithItem(client.player.getInventory(), candidateItem);
                if (slot >= 0) {
                    foundSlot = slot;
                }
            }

            if (foundSlot >= 0) {
                break;
            }
        }

        if (foundSlot < 0) {
            String reference = anySelection ? tr("pathmind.error.itemReference") : String.join(", ", itemIds);
            String locationDesc = (selectionType == SlotSelectionType.GUI_CONTAINER)
                ? tr("pathmind.error.locationContainer")
                : tr("pathmind.error.locationInventory");
            sendParameterSearchFailure(tr("pathmind.error.noItemFoundInLocation", reference, locationDesc, type.getDisplayName()), future);
            return false;
        }

        String targetParameter = slotIndex == 0 ? "SourceSlot" : "TargetSlot";
        setParameterValueAndPropagate(targetParameter, Integer.toString(foundSlot));
        if (slotIndex == 0) {
            if (runtimeState.runtimeParameterData == null) {
                runtimeState.runtimeParameterData = new RuntimeParameterData();
            }
            runtimeState.runtimeParameterData.slotIndex = foundSlot;
        }
        return true;
    }

    boolean resolveUseParameterSelection(Node parameterNode, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return false;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }

        Inventory inventory = client.player.getInventory();
        if (parameterNode.getType() == NodeType.OPERATOR_BOOLEAN_OR) {
            UseSelectionResult orResult = resolveUseSelectionFromOr(parameterNode, inventory);
            if (orResult != null) {
                applyUseSelectionResult(orResult);
                return true;
            }
            sendParameterSearchFailure(tr("pathmind.error.noOrOptionsInInventory", type.getDisplayName()), future);
            return false;
        }

        UseSelectionResult result = resolveUseSelectionResult(parameterNode, inventory, client, future, true);
        if (result == null) {
            return false;
        }
        applyUseSelectionResult(result);
        return true;
    }

    private UseSelectionResult resolveUseSelectionFromOr(Node orNode, Inventory inventory) {
        if (orNode == null || inventory == null) {
            return null;
        }
        java.util.List<Integer> slotIndices = new java.util.ArrayList<>(orNode.getAttachedParameterSlotIndices());
        java.util.Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node child = orNode.getAttachedParameter(slotIndex);
            if (child == null) {
                continue;
            }
            child = resolveVariableSelectionParameterNode(child);
            UseSelectionResult result = resolveUseSelectionResult(
                child,
                inventory,
                net.minecraft.client.Minecraft.getInstance(),
                null,
                false
            );
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private UseSelectionResult resolveUseSelectionResult(Node parameterNode,
                                                         Inventory inventory,
                                                         net.minecraft.client.Minecraft client,
                                                         CompletableFuture<Void> future,
                                                         boolean reportErrors) {
        if (parameterNode == null || inventory == null) {
            return null;
        }
        EnumSet<NodeValueTrait> traits = parameterNode.getProvidedTraits();
        boolean isListItem = parameterNode.getType() == NodeType.LIST_ITEM;
        boolean treatAsItem = traits.contains(NodeValueTrait.ITEM)
            || (isListItem && runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetItemId != null);
        if (traits.contains(NodeValueTrait.BLOCK)) {
            String rawBlock = getParameterString(parameterNode, "Block");
            boolean anySelection = isAnySelectionValue(rawBlock);
            List<BlockSelection> selections = resolveBlocksFromParameter(parameterNode);
            if (selections.isEmpty() && !anySelection) {
                if (reportErrors) {
                    sendParameterSearchFailure(tr("pathmind.error.noBlockSelectedOnParameter", type.getDisplayName()), future);
                }
                return null;
            }

            ItemSearchResult result = anySelection || selections.isEmpty()
                ? findFirstBlockItemSlot(inventory)
                : findUseBlockSlot(inventory, selections);

            if (result == null) {
                if (reportErrors) {
                    String reference = anySelection ? tr("pathmind.error.blockReference") : selections.stream()
                        .map(BlockSelection::getBlockIdString)
                        .filter(id -> id != null && !id.isEmpty())
                        .findFirst()
                        .orElse(tr("pathmind.error.blockReference"));
                    sendParameterSearchFailure(tr("pathmind.error.noItemFoundInInventory", reference, type.getDisplayName()), future);
                }
                return null;
            }
            return new UseSelectionResult(result.slotIndex(), SlotSelectionType.PLAYER_INVENTORY, result.item(), result.itemId());
        }
        if (treatAsItem) {
            List<String> itemIds;
            if (isListItem && runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetItemId != null) {
                itemIds = java.util.Collections.singletonList(runtimeState.runtimeParameterData.targetItemId);
            } else {
                itemIds = resolveItemIdsFromParameter(parameterNode);
            }
            if (itemIds.isEmpty()) {
                if (reportErrors) {
                    sendParameterSearchFailure(tr("pathmind.error.noItemSelectedOnParameter", type.getDisplayName()), future);
                }
                return null;
            }
            ItemSearchResult result = findUseItemSlot(inventory, itemIds);
            if (result == null) {
                if (reportErrors) {
                    String reference = String.join(", ", itemIds);
                    sendParameterSearchFailure(tr("pathmind.error.noItemFoundInInventory", reference, type.getDisplayName()), future);
                }
                return null;
            }
            return new UseSelectionResult(result.slotIndex(), SlotSelectionType.PLAYER_INVENTORY, result.item(), result.itemId());
        }
        if (traits.contains(NodeValueTrait.INVENTORY_SLOT)) {
            SlotSelectionType selectionType = resolveInventorySlotSelectionType(parameterNode);
            if (selectionType == SlotSelectionType.GUI_CONTAINER) {
                if (reportErrors) {
                    sendNodeErrorMessage(client, tr("pathmind.error.useOnlyPlayerInventorySlots"));
                    if (future != null && !future.isDone()) {
                        future.complete(null);
                    }
                }
                return null;
            }
            int slotValue = clampInventorySlot(inventory, parseNodeInt(parameterNode, "Slot", 0));
            return new UseSelectionResult(slotValue, SlotSelectionType.PLAYER_INVENTORY, null, null);
        }
        if (reportErrors) {
            sendIncompatibleParameterMessage(parameterNode);
        }
        return null;
    }

    private record ItemSearchResult(int slotIndex, Item item, String itemId) {
    }

    private record UseSelectionResult(int slotIndex, SlotSelectionType selectionType, Item item, String itemId) {
    }

    private void applyUseSelectionResult(UseSelectionResult result) {
        if (result == null) {
            return;
        }
        runtimeState.runtimeParameterData.slotIndex = result.slotIndex();
        runtimeState.runtimeParameterData.slotSelectionType = result.selectionType();
        runtimeState.runtimeParameterData.targetItem = result.item();
        runtimeState.runtimeParameterData.targetItemId = result.itemId();
    }

    private ItemSearchResult findUseItemSlot(Inventory inventory, List<String> itemIds) {
        if (inventory == null || itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
                continue;
            }
            Item candidateItem = BuiltInRegistries.ITEM.getValue(identifier);
            int slot = findAccessibleSlotWithItem(inventory, candidateItem);
            if (slot >= 0) {
                return new ItemSearchResult(slot, candidateItem, candidateId);
            }
        }
        return null;
    }

    private ItemSearchResult findUseBlockSlot(Inventory inventory, List<BlockSelection> selections) {
        if (inventory == null || selections == null || selections.isEmpty()) {
            return null;
        }
        for (BlockSelection selection : selections) {
            if (selection == null || selection.getBlock() == null) {
                continue;
            }
            Item candidateItem = selection.getBlock().asItem();
            if (candidateItem == null || candidateItem == Items.AIR) {
                continue;
            }
            int slot = findAccessibleSlotWithItem(inventory, candidateItem);
            if (slot >= 0) {
                Identifier id = BuiltInRegistries.ITEM.getKey(candidateItem);
                String itemId = id != null ? id.toString() : selection.getBlockIdString();
                return new ItemSearchResult(slot, candidateItem, itemId);
            }
        }
        return null;
    }

    private ItemSearchResult findFirstBlockItemSlot(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        int limit = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (item instanceof BlockItem) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                String itemId = id != null ? id.toString() : "";
                return new ItemSearchResult(slot, item, itemId);
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.getContainerSize()) {
            ItemStack offhandStack = inventory.getItem(offhandIndex);
            if (!offhandStack.isEmpty()) {
                Item item = offhandStack.getItem();
                if (item instanceof BlockItem) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(item);
                    String itemId = id != null ? id.toString() : "";
                    return new ItemSearchResult(offhandIndex, item, itemId);
                }
            }
        }
        return null;
    }

    private int findAccessibleSlotWithItem(Inventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.getContainerSize()) {
            ItemStack offhandStack = inventory.getItem(offhandIndex);
            if (!offhandStack.isEmpty() && offhandStack.is(item)) {
                return offhandIndex;
            }
        }
        return -1;
    }

    private int findFirstSlotWithItem(Inventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstNonEmptySlot(Inventory inventory) {
        if (inventory == null) {
            return -1;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private Node.ParameterHandlingResult preprocessAttachedParameter(EnumSet<Node.ParameterUsage> usages, CompletableFuture<Void> future) {
        return owner.preprocessAttachedParameter(usages, future);
    }

    private String getStringParameter(String name, String defaultValue) {
        return owner.getStringParameter(name, defaultValue);
    }

    private int getIntParameter(String name, int defaultValue) {
        return owner.getIntParameter(name, defaultValue);
    }

    private double getDoubleParameter(String name, double defaultValue) {
        return owner.getDoubleParameter(name, defaultValue);
    }

    private boolean getBooleanParameter(String name, boolean defaultValue) {
        return owner.getBooleanParameter(name, defaultValue);
    }

    private NodeParameter getParameter(String name) {
        return owner.getParameter(name);
    }

    private Node getAttachedParameter(int slotIndex) {
        return owner.getAttachedParameter(slotIndex);
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

    private int findHotbarSlotWithItem(Inventory inventory, Item targetItem) {
        return owner.findHotbarSlotWithItem(inventory, targetItem);
    }

    private void sendNodeErrorMessage(Minecraft client, String message) {
        owner.sendNodeErrorMessage(client, message);
    }

    private void runOnClientThread(Minecraft client, Runnable task) throws InterruptedException {
        owner.runOnClientThread(client, task);
    }

    private String getParameterString(Node node, String name) {
        return Node.getParameterString(node, name);
    }

    private List<String> resolveItemIdsFromParameter(Node parameterNode) {
        return owner.resolveItemIdsFromParameter(parameterNode);
    }

    private List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
        return owner.resolveBlocksFromParameter(parameterNode);
    }

    private boolean isAnySelectionValue(String value) {
        return Node.isAnySelectionValue(value);
    }

    private int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
        return owner.mapPlayerInventorySlot(handler, inventorySlot);
    }

    private int clampInventorySlot(Inventory inventory, int slot) {
        return owner.clampInventorySlot(inventory, slot);
    }

    private int parseNodeInt(Node node, String name, int defaultValue) {
        return Node.parseNodeInt(node, name, defaultValue);
    }

    private ListSlotEntry resolveListItemSlotEntry(Node listNode, boolean reportErrors, CompletableFuture<Void> future) {
        return owner.resolveListItemSlotEntry(listNode, reportErrors, future);
    }

    private boolean providesTrait(Node node, NodeValueTrait trait) {
        return owner.providesTrait(node, trait);
    }

    private void setParameterValueAndPropagate(String name, String value) {
        owner.setParameterValueAndPropagate(name, value);
    }

    private void sendParameterSearchFailure(String message, CompletableFuture<Void> future) {
        owner.sendParameterSearchFailure(message, future);
    }

    private void sendIncompatibleParameterMessage(Node parameterNode) {
        owner.sendIncompatibleParameterMessage(parameterNode);
    }

    private int getOffhandInventoryIndex(Inventory inventory) {
        return owner.getOffhandInventoryIndex(inventory);
    }
}
