package com.pathmind.nodes;

import net.minecraft.screen.slot.Slot;

final class SlotResolution {
    final Slot slot;
    final int handlerSlotIndex;

    SlotResolution(Slot slot, int handlerSlotIndex) {
        this.slot = slot;
        this.handlerSlotIndex = handlerSlotIndex;
    }
}
