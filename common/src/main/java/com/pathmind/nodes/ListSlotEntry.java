package com.pathmind.nodes;

final class ListSlotEntry {
    final int slotIndex;
    final SlotSelectionType selectionType;

    ListSlotEntry(int slotIndex, SlotSelectionType selectionType) {
        this.slotIndex = slotIndex;
        this.selectionType = selectionType;
    }
}
