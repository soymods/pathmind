package com.pathmind.nodes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

final class NodeGuiSensorEvaluator {
    @SuppressWarnings("unused")
    private final Node owner;

    NodeGuiSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean isOpenGuiFilled() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }
        boolean hasContainerSlots = false;
        for (Slot slot : handler.slots) {
            if (slot == null) {
                continue;
            }
            if (slot.inventory instanceof PlayerInventory) {
                continue;
            }
            hasContainerSlots = true;
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
        }
        return hasContainerSlots;
    }
}
