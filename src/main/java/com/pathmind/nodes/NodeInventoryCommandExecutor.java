package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.HotbarSlotSynchronizer;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.InventorySlotModeHelper;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.util.PlayerInventoryBridge;
import com.pathmind.util.BlockSelection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
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
                if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                    continue;
                }
                Item targetItem = Registries.ITEM.get(identifier);
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
            slot = MathHelper.clamp(resolvedSlot, 0, PlayerInventory.getHotbarSize() - 1);
        }

        HotbarSlotSynchronizer.selectHotbarSlot(client, slot);
        future.complete(null);
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
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
        final int dropIterations = count;
        final boolean dropEntireStack = dropAll;

        new Thread(() -> {
            try {
                for (int i = 0; i < dropIterations; i++) {
                    runOnClientThread(client, () -> {
                        client.player.dropSelectedItem(dropEntireStack);
                        client.player.getInventory().markDirty();
                        client.player.playerScreenHandler.sendContentUpdates();
                    });

                    if (interval > 0.0 && i < dropIterations - 1) {
                        Thread.sleep((long) (interval * 1000));
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (interactionManager == null || handler == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int slotValue = getIntParameter("Slot", 0);
        SlotSelectionType selectionType = resolveInventorySlotSelectionType(0);
        SlotResolution resolution = resolveInventorySlot(handler, inventory, slotValue, selectionType);
        if (resolution == null || resolution.slot == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clickSlotRequiresValidSelection"));
            future.complete(null);
            return;
        }

        interactionManager.clickSlot(
            handler.syncId,
            resolution.handlerSlotIndex,
            0,
            SlotActionType.PICKUP,
            client.player
        );

        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }

    void executeClickScreenCommand(CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        if (client.currentScreen == null) {
            sendNodeErrorMessage(client, tr("pathmind.error.clickScreenRequiresOpenGui"));
            future.complete(null);
            return;
        }

        int guiX = getIntParameter("X", 0);
        int guiY = getIntParameter("Y", 0);
        net.minecraft.client.util.Window window = client.getWindow();
        int scaledWidth = Math.max(1, window.getScaledWidth());
        int scaledHeight = Math.max(1, window.getScaledHeight());
        guiX = MathHelper.clamp(guiX, 0, Math.max(0, scaledWidth - 1));
        guiY = MathHelper.clamp(guiY, 0, Math.max(0, scaledHeight - 1));

        double windowScaleX = window.getWidth() / (double) scaledWidth;
        double windowScaleY = window.getHeight() / (double) scaledHeight;
        double windowX = guiX * windowScaleX;
        double windowY = guiY * windowScaleY;
        final int targetGuiX = guiX;
        final int targetGuiY = guiY;

        client.execute(() -> {
            boolean moved = InputCompatibilityBridge.dispatchCursorPos(client, windowX, windowY);
            boolean pressed = InputCompatibilityBridge.dispatchScreenMouseClicked(
                client.currentScreen,
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
                net.minecraft.client.MinecraftClient releaseClient = net.minecraft.client.MinecraftClient.getInstance();
                if (releaseClient == null) {
                    future.complete(null);
                    return;
                }
                releaseClient.execute(() -> {
                    boolean released = InputCompatibilityBridge.dispatchScreenMouseReleased(
                        releaseClient.currentScreen,
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (interactionManager == null || handler == null) {
            future.completeExceptionally(new RuntimeException("Interaction manager unavailable"));
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            sendNodeErrorMessage(client, tr("pathmind.error.cursorHoldingStack"));
            future.complete(null);
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
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

        ItemStack sourceStack = sourceResolution.slot.getStack();
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
                interactionManager.clickSlot(
                    handler.syncId,
                    sourceResolution.handlerSlotIndex,
                    0,
                    SlotActionType.QUICK_MOVE,
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

        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
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

    private void quickMoveAllMatchingStacks(net.minecraft.client.MinecraftClient client,
                                            ClientPlayerInteractionManager interactionManager,
                                            ScreenHandler handler,
                                            PlayerInventory inventory,
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

            ItemStack beforeStack = nextResolution.slot.getStack().copy();
            if (beforeStack.isEmpty()) {
                break;
            }

            interactionManager.clickSlot(
                handler.syncId,
                nextResolution.handlerSlotIndex,
                0,
                SlotActionType.QUICK_MOVE,
                client.player
            );

            ItemStack afterStack = nextResolution.slot.getStack();
            boolean moved = afterStack.isEmpty()
                || afterStack.getCount() < beforeStack.getCount()
                || !ItemStack.areItemsAndComponentsEqual(afterStack, beforeStack);
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

    private void moveRequestedCountToGuiTarget(net.minecraft.client.MinecraftClient client,
                                               ClientPlayerInteractionManager interactionManager,
                                               ScreenHandler handler,
                                               PlayerInventory inventory,
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
            ItemStack source = currentResolution.slot.getStack();
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

    private SlotResolution findNextMoveItemSourceResolution(net.minecraft.client.MinecraftClient client,
                                                            ScreenHandler handler,
                                                            PlayerInventory inventory,
                                                            List<String> itemIds,
                                                            boolean anySelection,
                                                            SlotSelectionType selectionType) {
        if (client == null || client.player == null || handler == null) {
            return null;
        }

        if (selectionType == SlotSelectionType.GUI_CONTAINER) {
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot == null || slot.getStack().isEmpty() || !isSlotInSelectionType(slot, selectionType)) {
                    continue;
                }
                if (matchesMoveItemSource(slot.getStack(), itemIds, anySelection)) {
                    return new SlotResolution(slot, i);
                }
            }
            return null;
        }

        if (inventory == null) {
            return null;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || !matchesMoveItemSource(stack, itemIds, anySelection)) {
                continue;
            }
            int handlerSlot = mapPlayerInventorySlot(handler, clampInventorySlot(inventory, i));
            if (handlerSlot < 0 || handlerSlot >= handler.slots.size()) {
                continue;
            }
            Slot slot = handler.getSlot(handlerSlot);
            if (slot != null && !slot.getStack().isEmpty() && isSlotInSelectionType(slot, selectionType)) {
                return new SlotResolution(slot, handlerSlot);
            }
        }
        return null;
    }

    private SlotResolution findTransferDestinationResolution(ScreenHandler handler,
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
            if (!slot.canInsert(sourceStack)) {
                continue;
            }

            ItemStack destinationStack = slot.getStack();
            if (!destinationStack.isEmpty()) {
                if (!ItemStack.areItemsAndComponentsEqual(destinationStack, sourceStack)) {
                    continue;
                }
                int maxCount = Math.min(slot.getMaxItemCount(sourceStack), sourceStack.getMaxCount());
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
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            if (stack.isOf(candidateItem)) {
                return true;
            }
        }
        return false;
    }

    private void performInventoryTransfer(ClientPlayerInteractionManager interactionManager, ScreenHandler handler,
                                          PlayerEntity player, int sourceSlot, int targetSlot, int moveCount, boolean moveEntireStack) {
        if (moveEntireStack) {
            interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
            interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, player);
            interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
            return;
        }

        interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
        int moved = 0;
        while (moved < moveCount) {
            int beforeCursor = handler.getCursorStack().getCount();
            interactionManager.clickSlot(handler.syncId, targetSlot, 1, SlotActionType.PICKUP, player);
            int afterCursor = handler.getCursorStack().getCount();
            if (afterCursor >= beforeCursor) {
                break;
            }
            moved++;
        }
        interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
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

    private boolean hasOpenGuiContainer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler instanceof PlayerScreenHandler) {
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
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                ScreenHandler handler = client.player.currentScreenHandler;
                // If a container is open (not just the player inventory screen)
                if (handler != null && !(handler instanceof PlayerScreenHandler)) {
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

    SlotResolution resolveInventorySlot(ScreenHandler handler, PlayerInventory inventory, int slotValue, SlotSelectionType selectionType) {
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
        boolean playerInventorySlot = slot.inventory instanceof PlayerInventory;
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
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

        ScreenHandler handler = client.player.currentScreenHandler;
        int foundSlot = -1;
        String matchedItemId = null;

        if (anySelection) {
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getStack().isEmpty()) {
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
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getStack().isEmpty() && slot.getStack().isOf(candidateItem)) {
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

    private void executeResolvedDropTarget(net.minecraft.client.MinecraftClient client,
                                           RuntimeParameterData parameterData,
                                           CompletableFuture<Void> future) {
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        ScreenHandler handler = client.player.currentScreenHandler;
        PlayerInventory inventory = client.player.getInventory();
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
        if (resolution.slot.getStack() == null || resolution.slot.getStack().isEmpty()) {
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
                for (int i = 0; i < dropIterations; i++) {
                    final boolean entireStackThisClick = dropEntireStack;
                    runOnClientThread(client, () -> {
                        interactionManager.clickSlot(
                            handler.syncId,
                            resolution.handlerSlotIndex,
                            entireStackThisClick ? 1 : 0,
                            SlotActionType.THROW,
                            client.player
                        );
                        inventory.markDirty();
                        client.player.playerScreenHandler.sendContentUpdates();
                    });
                    if (interval > 0.0 && i < dropIterations - 1) {
                        Thread.sleep((long) (interval * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Drop").start();
    }

    boolean resolveMoveItemSlotFromItemParameter(Node parameterNode, int slotIndex,
                                                         SlotSelectionType selectionType, CompletableFuture<Void> future) {
        if (slotIndex < 0 || slotIndex > 1) {
            return false;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
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

        ScreenHandler handler = client.player.currentScreenHandler;

        int foundSlot = -1;
        if (anySelection) {
            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null && !slot.getStack().isEmpty() && isSlotInSelectionType(slot, selectionType)) {
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
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);

            if (selectionType == SlotSelectionType.GUI_CONTAINER && handler != null) {
                // Search through all handler slots
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    if (slot != null
                        && !slot.getStack().isEmpty()
                        && isSlotInSelectionType(slot, selectionType)
                        && slot.getStack().isOf(candidateItem)) {
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
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            }
            return false;
        }
        if (runtimeState.runtimeParameterData == null) {
            runtimeState.runtimeParameterData = new RuntimeParameterData();
        }

        PlayerInventory inventory = client.player.getInventory();
        EnumSet<NodeValueTrait> traits = parameterNode.getProvidedTraits();
        boolean isListItem = parameterNode.getType() == NodeType.LIST_ITEM;
        boolean treatAsItem = traits.contains(NodeValueTrait.ITEM)
            || (isListItem && runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetItemId != null);
        if (traits.contains(NodeValueTrait.BLOCK)) {
            {
                String rawBlock = getParameterString(parameterNode, "Block");
                boolean anySelection = isAnySelectionValue(rawBlock);
                List<BlockSelection> selections = resolveBlocksFromParameter(parameterNode);
                if (selections.isEmpty() && !anySelection) {
                    sendParameterSearchFailure(tr("pathmind.error.noBlockSelectedOnParameter", type.getDisplayName()), future);
                    return false;
                }

                ItemSearchResult result = null;
                if (anySelection || selections.isEmpty()) {
                    result = findFirstBlockItemSlot(inventory);
                } else {
                    result = findUseBlockSlot(inventory, selections);
                }

                if (result == null) {
                    String reference = anySelection ? tr("pathmind.error.blockReference") : selections.stream()
                        .map(BlockSelection::getBlockIdString)
                        .filter(id -> id != null && !id.isEmpty())
                        .findFirst()
                        .orElse(tr("pathmind.error.blockReference"));
                    sendParameterSearchFailure(tr("pathmind.error.noItemFoundInInventory", reference, type.getDisplayName()), future);
                    return false;
                }
                runtimeState.runtimeParameterData.slotIndex = result.slotIndex();
                runtimeState.runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
                runtimeState.runtimeParameterData.targetItem = result.item();
                runtimeState.runtimeParameterData.targetItemId = result.itemId();
                return true;
            }
        }
        if (treatAsItem) {
            List<String> itemIds;
            if (isListItem && runtimeState.runtimeParameterData != null && runtimeState.runtimeParameterData.targetItemId != null) {
                itemIds = java.util.Collections.singletonList(runtimeState.runtimeParameterData.targetItemId);
            } else {
                itemIds = resolveItemIdsFromParameter(parameterNode);
            }
            if (itemIds.isEmpty()) {
                sendParameterSearchFailure(tr("pathmind.error.noItemSelectedOnParameter", type.getDisplayName()), future);
                return false;
            }
                ItemSearchResult result = findUseItemSlot(inventory, itemIds);
                if (result == null) {
                    String reference = String.join(", ", itemIds);
                    sendParameterSearchFailure(tr("pathmind.error.noItemFoundInInventory", reference, type.getDisplayName()), future);
                    return false;
                }
                runtimeState.runtimeParameterData.slotIndex = result.slotIndex();
                runtimeState.runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
                runtimeState.runtimeParameterData.targetItem = result.item();
                runtimeState.runtimeParameterData.targetItemId = result.itemId();
                return true;
        }
        if (traits.contains(NodeValueTrait.INVENTORY_SLOT)) {
                SlotSelectionType selectionType = resolveInventorySlotSelectionType(parameterNode);
                if (selectionType == SlotSelectionType.GUI_CONTAINER) {
                    sendNodeErrorMessage(client, tr("pathmind.error.useOnlyPlayerInventorySlots"));
                    if (future != null && !future.isDone()) {
                        future.complete(null);
                    }
                    return false;
                }
                int slotValue = clampInventorySlot(inventory, parseNodeInt(parameterNode, "Slot", 0));
                runtimeState.runtimeParameterData.slotIndex = slotValue;
                runtimeState.runtimeParameterData.slotSelectionType = SlotSelectionType.PLAYER_INVENTORY;
                return true;
        }
        sendIncompatibleParameterMessage(parameterNode);
        return false;
    }

    private record ItemSearchResult(int slotIndex, Item item, String itemId) {
    }

    private ItemSearchResult findUseItemSlot(PlayerInventory inventory, List<String> itemIds) {
        if (inventory == null || itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        for (String candidateId : itemIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item candidateItem = Registries.ITEM.get(identifier);
            int slot = findAccessibleSlotWithItem(inventory, candidateItem);
            if (slot >= 0) {
                return new ItemSearchResult(slot, candidateItem, candidateId);
            }
        }
        return null;
    }

    private ItemSearchResult findUseBlockSlot(PlayerInventory inventory, List<BlockSelection> selections) {
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
                Identifier id = Registries.ITEM.getId(candidateItem);
                String itemId = id != null ? id.toString() : selection.getBlockIdString();
                return new ItemSearchResult(slot, candidateItem, itemId);
            }
        }
        return null;
    }

    private ItemSearchResult findFirstBlockItemSlot(PlayerInventory inventory) {
        if (inventory == null) {
            return null;
        }
        int limit = Math.min(PlayerInventory.MAIN_SIZE, inventory.size());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (item instanceof BlockItem) {
                Identifier id = Registries.ITEM.getId(item);
                String itemId = id != null ? id.toString() : "";
                return new ItemSearchResult(slot, item, itemId);
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.size()) {
            ItemStack offhandStack = inventory.getStack(offhandIndex);
            if (!offhandStack.isEmpty()) {
                Item item = offhandStack.getItem();
                if (item instanceof BlockItem) {
                    Identifier id = Registries.ITEM.getId(item);
                    String itemId = id != null ? id.toString() : "";
                    return new ItemSearchResult(offhandIndex, item, itemId);
                }
            }
        }
        return null;
    }

    private int findAccessibleSlotWithItem(PlayerInventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE && slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return slot;
            }
        }
        int offhandIndex = getOffhandInventoryIndex(inventory);
        if (offhandIndex >= 0 && offhandIndex < inventory.size()) {
            ItemStack offhandStack = inventory.getStack(offhandIndex);
            if (!offhandStack.isEmpty() && offhandStack.isOf(item)) {
                return offhandIndex;
            }
        }
        return -1;
    }

    private int findFirstSlotWithItem(PlayerInventory inventory, Item item) {
        if (inventory == null || item == null) {
            return -1;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstNonEmptySlot(PlayerInventory inventory) {
        if (inventory == null) {
            return -1;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
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

    private int findHotbarSlotWithItem(PlayerInventory inventory, Item targetItem) {
        return owner.findHotbarSlotWithItem(inventory, targetItem);
    }

    private void sendNodeErrorMessage(MinecraftClient client, String message) {
        owner.sendNodeErrorMessage(client, message);
    }

    private void runOnClientThread(MinecraftClient client, Runnable task) throws InterruptedException {
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

    private int mapPlayerInventorySlot(ScreenHandler handler, int inventorySlot) {
        return owner.mapPlayerInventorySlot(handler, inventorySlot);
    }

    private int clampInventorySlot(PlayerInventory inventory, int slot) {
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

    private int getOffhandInventoryIndex(PlayerInventory inventory) {
        return owner.getOffhandInventoryIndex(inventory);
    }
}
