package com.pathmind.util;

import net.minecraft.entity.player.PlayerInventory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Compatibility helpers for PlayerInventory slot APIs that changed across 1.21.x.
 */
public final class PlayerInventoryBridge {
    private static final Method GET_SELECTED_SLOT_METHOD = resolveGetter();
    private static final Method SET_SELECTED_SLOT_METHOD = resolveSetter();
    private static final Field SELECTED_SLOT_FIELD = resolveField();

    private PlayerInventoryBridge() {
    }

    public static int getSelectedSlot(PlayerInventory inventory) {
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
        throw new IllegalStateException("No compatible way to read PlayerInventory selected slot");
    }

    public static void setSelectedSlot(PlayerInventory inventory, int slot) {
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
        throw new IllegalStateException("No compatible way to update PlayerInventory selected slot");
    }

    private static Method resolveGetter() {
        try {
            return PlayerInventory.class.getMethod("getSelectedSlot");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolveField() {
        try {
            Field field = PlayerInventory.class.getDeclaredField("selectedSlot");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method resolveSetter() {
        try {
            return PlayerInventory.class.getMethod("setSelectedSlot", int.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
