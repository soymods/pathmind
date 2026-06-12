package com.pathmind.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.lang.reflect.Method;

/**
 * Keeps client hotbar selection and the server-held slot in sync without
 * emitting duplicate held-item-change packets.
 */
public final class HotbarSlotSynchronizer {
    private static final Method SYNC_SELECTED_SLOT_METHOD = resolveSyncSelectedSlotMethod();
    private static int lastManuallySyncedSlot = -1;

    private HotbarSlotSynchronizer() {
    }

    public static boolean selectHotbarSlot(MinecraftClient client, int slot) {
        if (client == null || client.player == null) {
            return false;
        }
        PlayerInventory inventory = client.player.getInventory();
        int hotbarSize = PlayerInventory.getHotbarSize();
        if (slot < 0 || slot >= hotbarSize) {
            return false;
        }

        try {
            if (PlayerInventoryBridge.getSelectedSlot(inventory) == slot) {
                return true;
            }
        } catch (IllegalStateException ignored) {
            // Continue with a best-effort set and sync below.
        }

        try {
            PlayerInventoryBridge.setSelectedSlot(inventory, slot);
        } catch (IllegalStateException ignored) {
            return false;
        }
        syncSelectedHotbarSlot(client);
        return true;
    }

    public static void syncSelectedHotbarSlot(MinecraftClient client) {
        if (client == null) {
            return;
        }
        if (client.interactionManager != null && SYNC_SELECTED_SLOT_METHOD != null) {
            try {
                SYNC_SELECTED_SLOT_METHOD.invoke(client.interactionManager);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to a single manual packet below.
            }
        }
        if (client.player == null || client.player.networkHandler == null) {
            lastManuallySyncedSlot = -1;
            return;
        }
        try {
            int selectedSlot = PlayerInventoryBridge.getSelectedSlot(client.player.getInventory());
            if (selectedSlot >= 0 && selectedSlot != lastManuallySyncedSlot) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
                lastManuallySyncedSlot = selectedSlot;
            }
        } catch (IllegalStateException ignored) {
            // No compatible way to read the selected slot.
        }
    }

    private static Method resolveSyncSelectedSlotMethod() {
        try {
            return net.minecraft.client.network.ClientPlayerInteractionManager.class.getMethod("syncSelectedSlot");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
