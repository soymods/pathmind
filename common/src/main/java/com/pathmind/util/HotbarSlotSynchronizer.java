package com.pathmind.util;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * Keeps client hotbar selection and the server-held slot in sync without
 * emitting duplicate held-item-change packets.
 */
public final class HotbarSlotSynchronizer {
    private static final Method SYNC_SELECTED_SLOT_METHOD = resolveSyncSelectedSlotMethod();
    private static int lastManuallySyncedSlot = -1;

    private HotbarSlotSynchronizer() {
    }

    public static boolean selectHotbarSlot(Minecraft client, int slot) {
        if (client == null || client.player == null) {
            return false;
        }
        Inventory inventory = client.player.getInventory();
        int hotbarSize = Inventory.getSelectionSize();
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

    public static void syncSelectedHotbarSlot(Minecraft client) {
        if (client == null) {
            return;
        }
        if (client.gameMode != null && SYNC_SELECTED_SLOT_METHOD != null) {
            try {
                SYNC_SELECTED_SLOT_METHOD.invoke(client.gameMode);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to a single manual packet below.
            }
        }
        if (client.player == null || client.player.connection == null) {
            lastManuallySyncedSlot = -1;
            return;
        }
        try {
            int selectedSlot = PlayerInventoryBridge.getSelectedSlot(client.player.getInventory());
            if (selectedSlot >= 0 && selectedSlot != lastManuallySyncedSlot) {
                client.player.connection.send(new ServerboundSetCarriedItemPacket(selectedSlot));
                lastManuallySyncedSlot = selectedSlot;
            }
        } catch (IllegalStateException ignored) {
            // No compatible way to read the selected slot.
        }
    }

    private static Method resolveSyncSelectedSlotMethod() {
        try {
            return net.minecraft.client.multiplayer.MultiPlayerGameMode.class.getMethod("syncSelectedSlot");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
