package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventorySlotValueResolverTest {

    @Test
    void resolvesSlotIndexFromAliasKeys() {
        assertEquals(4, InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("Slot", "4")));
        assertEquals(5, InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("SourceSlot", "5")));
        assertEquals(6, InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("target_slot", "6")));
        assertEquals(7, InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("firstslot", "7")));
        assertEquals(8, InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("SecondSlot", "8")));
    }

    @Test
    void defaultsComparableSelectionToPlayerInventory() {
        assertEquals(SlotSelectionType.PLAYER_INVENTORY, InventorySlotValueResolver.resolveComparableSlotSelectionType(Map.of()));
        assertEquals(SlotSelectionType.PLAYER_INVENTORY, InventorySlotValueResolver.resolveComparableSlotSelectionType(Map.of("Mode", "player_inventory")));
    }

    @Test
    void resolvesComparableSelectionFromStoredGuiMode() {
        assertEquals(SlotSelectionType.GUI_CONTAINER, InventorySlotValueResolver.resolveComparableSlotSelectionType(Map.of("Mode", "gui_container|container")));
    }

    @Test
    void invalidSlotValuesReturnNull() {
        assertNull(InventorySlotValueResolver.resolveComparableSlotIndex(Map.of("Slot", "abc")));
    }

    @Test
    void moveItemInfersContainerSourceWhenGuiTargetIsPlayerInventory() {
        Node moveItem = new Node(NodeType.MOVE_ITEM, 0, 0);
        Node sourceItem = new Node(NodeType.PARAM_ITEM, 0, 0);
        sourceItem.getParameter("Item").setStringValue("minecraft:iron_ingot");
        Node targetGui = new Node(NodeType.PARAM_GUI, 0, 0);
        targetGui.getParameter("GUI").setStringValue("player_inventory");

        moveItem.attachParameter(sourceItem, 0);
        moveItem.attachParameter(targetGui, 1);

        NodeInventoryCommandExecutor executor = new NodeInventoryCommandExecutor(moveItem);
        assertEquals(SlotSelectionType.GUI_CONTAINER,
            executor.resolveMoveItemSlotSelectionTypeForTests(sourceItem, 0));
    }
}
