package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;

import java.util.List;

final class NodeInventorySensorEvaluator {
    private final Node owner;

    NodeInventorySensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateItemInInventory() {
        String itemId = owner.getStringParameter("Item", "stone");
        boolean useAmount = owner.isAmountInputEnabled();
        int requiredAmount = Math.max(1, owner.getIntParameter("Amount", 1));
        Node amountNode = null;
        Node parameterNode = null;
        Node attached = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (attached != null) {
            if (owner.providesTrait(attached, NodeValueTrait.NUMBER)) {
                amountNode = attached;
            } else if (owner.providesTrait(attached, NodeValueTrait.ITEM)) {
                parameterNode = attached;
            } else {
                owner.sendIncompatibleParameterMessage(attached);
            }
        }
        if (amountNode != null) {
            double parsed = Node.parseNodeDouble(amountNode, "Amount", requiredAmount);
            requiredAmount = Math.max(1, (int) Math.round(parsed));
        }
        if (parameterNode != null) {
            List<String> nodeItems = owner.resolveItemIdsFromParameter(parameterNode);
            if (!nodeItems.isEmpty()) {
                for (String candidate : nodeItems) {
                    if (useAmount ? hasItemAmountInInventory(candidate, requiredAmount) : hasItemInInventory(candidate)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return useAmount ? hasItemAmountInInventory(itemId, requiredAmount) : hasItemInInventory(itemId);
    }

    boolean evaluateItemInSlot() {
        Node itemNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        Node slotNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(1), 1);
        if (itemNode == null || slotNode == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresItemAndSlotParameter", owner.getType().getDisplayName()));
            }
            return false;
        }
        if (!owner.providesTrait(itemNode, NodeValueTrait.ITEM)) {
            owner.sendIncompatibleParameterMessage(itemNode);
            return false;
        }
        if (!owner.providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
            owner.sendIncompatibleParameterMessage(slotNode);
            return false;
        }
        List<String> itemIds = owner.resolveItemIdsFromParameter(itemNode);
        if (itemIds.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.noItemSpecifiedForNode", owner.getType().getDisplayName()));
            }
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        PlayerInventory inventory = client.player.getInventory();
        ScreenHandler handler = client.player.currentScreenHandler;
        int slotValue = Node.parseNodeInt(slotNode, "Slot", 0);
        SlotSelectionType selectionType = owner.resolveInventorySlotSelectionType(slotNode);
        SlotResolution resolved = owner.resolveInventorySlot(handler, inventory, slotValue, selectionType);
        if (resolved == null || resolved.slot == null) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresValidSlotSelection", owner.getType().getDisplayName()));
            return false;
        }
        ItemStack stack = resolved.slot.getStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        boolean useAmount = owner.isAmountInputEnabled();
        int requiredAmount = Math.max(1, owner.getIntParameter("Amount", 1));
        boolean matchesItem = stackMatchesAnyItem(stack, itemIds);
        return matchesItem && (!useAmount || stack.getCount() >= requiredAmount);
    }

    boolean evaluateSlotItemCount() {
        Node slotNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        if (slotNode == null || !owner.providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresInventorySlotParameter", owner.getType().getDisplayName()));
            }
            return false;
        }
        return owner.resolveInventorySlotCount(slotNode).isPresent();
    }

    boolean hasItemInInventory(String itemId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        for (String candidateId : owner.splitMultiValueList(itemId)) {
            String sanitized = owner.sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? owner.normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            if (client.player.getInventory().count(item) > 0) {
                return true;
            }
        }
        return false;
    }

    boolean hasItemAmountInInventory(String itemId, int requiredAmount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        int needed = Math.max(1, requiredAmount);
        for (String candidateId : owner.splitMultiValueList(itemId)) {
            String sanitized = owner.sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? owner.normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            if (client.player.getInventory().count(item) >= needed) {
                return true;
            }
        }
        return false;
    }

    boolean stackMatchesAnyItem(ItemStack stack, List<String> itemIds) {
        if (stack == null || stack.isEmpty() || itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        for (String candidateId : itemIds) {
            String sanitized = owner.sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? owner.normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                continue;
            }
            Item item = Registries.ITEM.get(identifier);
            if (stack.isOf(item)) {
                return true;
            }
        }
        return false;
    }
}
