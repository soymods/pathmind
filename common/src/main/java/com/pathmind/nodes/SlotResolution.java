package com.pathmind.nodes;

import net.minecraft.world.inventory.Slot;

final class SlotResolution {
    final Slot slot;
    final int handlerSlotIndex;

    SlotResolution(Slot slot, int handlerSlotIndex) {
        this.slot = slot;
        this.handlerSlotIndex = handlerSlotIndex;
    }
}
