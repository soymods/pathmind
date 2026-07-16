package com.pathmind.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Compatibility helpers for PlayerInventory slot APIs that changed across 1.21.x.
 */
public final class PlayerInventoryBridge {
    private static final Method GET_SELECTED_SLOT_METHOD = resolveGetter();
    private static final Method SET_SELECTED_SLOT_METHOD = resolveSetter();
    private static final Field SELECTED_SLOT_FIELD = resolveField();
    private static final Field PLAYER_FIELD = resolvePlayerField();

    private PlayerInventoryBridge() {
    }

    public static int getSelectedSlot(Inventory inventory) {
        if (inventory == null) {
            return -1;
        }
        if (GET_SELECTED_SLOT_METHOD != null) {
            try {
                return (int) GET_SELECTED_SLOT_METHOD.invoke(inventory);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to invoke PlayerInventory#getSelectedSlot", e);
            }
        }
        if (SELECTED_SLOT_FIELD != null) {
            try {
                return SELECTED_SLOT_FIELD.getInt(inventory);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read PlayerInventory.selectedSlot", e);
            }
        }
        int inferred = inferSelectedSlot(inventory);
        if (inferred >= 0) {
            return inferred;
        }
        throw new IllegalStateException("No compatible way to read PlayerInventory selected slot");
    }

    public static void setSelectedSlot(Inventory inventory, int slot) {
        if (inventory == null) {
            return;
        }
        if (SET_SELECTED_SLOT_METHOD != null) {
            try {
                SET_SELECTED_SLOT_METHOD.invoke(inventory, slot);
                return;
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to invoke PlayerInventory#setSelectedSlot", e);
            }
        }
        if (SELECTED_SLOT_FIELD != null) {
            try {
                SELECTED_SLOT_FIELD.setInt(inventory, slot);
                return;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set PlayerInventory.selectedSlot", e);
            }
        }
        if (trySetInferredSelectedSlot(inventory, slot)) {
            return;
        }
        throw new IllegalStateException("No compatible way to update PlayerInventory selected slot");
    }

    private static Method resolveGetter() {
        try {
            return Inventory.class.getMethod("getSelectedSlot");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolveField() {
        try {
            Field field = Inventory.class.getDeclaredField("selectedSlot");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method resolveSetter() {
        try {
            return Inventory.class.getMethod("setSelectedSlot", int.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolvePlayerField() {
        for (Field field : Inventory.class.getDeclaredFields()) {
            if (Player.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static int inferSelectedSlot(Inventory inventory) {
        Player player = resolvePlayer(inventory);
        if (player == null) {
            return -1;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand == null) {
            return -1;
        }
        int hotbarSize = Inventory.getSelectionSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == mainHand) {
                return slot;
            }
        }
        if (!mainHand.isEmpty()) {
            for (int slot = 0; slot < hotbarSize; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && ItemStack.isSameItem(stack, mainHand)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private static boolean trySetInferredSelectedSlot(Inventory inventory, int slot) {
        int current = inferSelectedSlot(inventory);
        if (current < 0) {
            return false;
        }
        for (Field field : Inventory.class.getDeclaredFields()) {
            if (field.getType() != int.class) {
                continue;
            }
            field.setAccessible(true);
            try {
                int value = field.getInt(inventory);
                if (value == current) {
                    field.setInt(inventory, slot);
                    return true;
                }
            } catch (IllegalAccessException ignored) {
                // Keep looking for a compatible field.
            }
        }
        return false;
    }

    private static Player resolvePlayer(Inventory inventory) {
        if (PLAYER_FIELD == null) {
            return null;
        }
        try {
            Object value = PLAYER_FIELD.get(inventory);
            return value instanceof Player player ? player : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }
}
