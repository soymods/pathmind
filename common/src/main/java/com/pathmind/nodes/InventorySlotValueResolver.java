package com.pathmind.nodes;

import com.pathmind.util.InventorySlotModeHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class InventorySlotValueResolver {

    static Integer resolveComparableSlotIndex(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Integer slot = parseIntOrNull(getRuntimeValue(values, "slot"));
        if (slot != null) {
            return slot;
        }
        slot = parseIntOrNull(getRuntimeValue(values, "sourceslot"));
        if (slot != null) {
            return slot;
        }
        slot = parseIntOrNull(getRuntimeValue(values, "targetslot"));
        if (slot != null) {
            return slot;
        }
        slot = parseIntOrNull(getRuntimeValue(values, "firstslot"));
        if (slot != null) {
            return slot;
        }
        return parseIntOrNull(getRuntimeValue(values, "secondslot"));
    }

    static SlotSelectionType resolveComparableSlotSelectionType(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return SlotSelectionType.PLAYER_INVENTORY;
        }
        Boolean isPlayer = InventorySlotModeHelper.extractPlayerSelectionFlag(getRuntimeValue(values, "mode"));
        return Boolean.FALSE.equals(isPlayer) ? SlotSelectionType.GUI_CONTAINER : SlotSelectionType.PLAYER_INVENTORY;
    }

    static ItemStack resolveComparableInventorySlotStack(Map<String, String> values) {
        Integer slotValue = resolveComparableSlotIndex(values);
        if (slotValue == null) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return null;
        }
        Slot slot = resolveInventorySlot(
            client.player.containerMenu,
            client.player.getInventory(),
            slotValue.intValue(),
            resolveComparableSlotSelectionType(values)
        );
        return slot != null ? slot.getItem() : null;
    }

    private static Slot resolveInventorySlot(AbstractContainerMenu handler, Inventory inventory, int slotValue, SlotSelectionType selectionType) {
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
            return slot;
        }
        if (inventory == null) {
            return null;
        }
        int clamped = clampInventorySlot(inventory, slotValue);
        int handlerSlot = mapPlayerInventorySlot(handler, clamped);
        if (handlerSlot < 0 || handlerSlot >= handler.slots.size()) {
            return null;
        }
        return handler.getSlot(handlerSlot);
    }

    private static int mapPlayerInventorySlot(AbstractContainerMenu handler, int inventorySlot) {
        if (handler == null) {
            return -1;
        }
        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == inventorySlot) {
                return slotIdx;
            }
        }
        return -1;
    }

    private static boolean isSlotInSelectionType(Slot slot, SlotSelectionType selectionType) {
        if (slot == null) {
            return false;
        }
        boolean playerInventorySlot = slot.container instanceof Inventory;
        return selectionType == SlotSelectionType.GUI_CONTAINER ? !playerInventorySlot : playerInventorySlot;
    }

    private static int clampInventorySlot(Inventory inventory, int slot) {
        return Mth.clamp(slot, 0, inventory.getContainerSize() - 1);
    }

    private static String getRuntimeValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return "";
        }
        String direct = values.get(key);
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (!lowerKey.equals(key)) {
            String lower = values.get(lowerKey);
            if (lower != null && !lower.trim().isEmpty()) {
                return lower.trim();
            }
        }
        String normalizedKey = Node.normalizeParameterKey(key);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (!Node.normalizeParameterKey(entry.getKey()).equals(normalizedKey)) {
                continue;
            }
            String candidate = entry.getValue();
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private InventorySlotValueResolver() {
    }
}
