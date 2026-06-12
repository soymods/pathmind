package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeHotbarTest {

    @Test
    void inventorySlotAttachmentControlsHotbarBySlotNotItem() {
        Node hotbar = new Node(NodeType.HOTBAR, 0, 0);
        hotbar.setParameterValueAndPropagate("Item", "minecraft:stone");
        Node inventorySlot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0);
        inventorySlot.setParameterValueAndPropagate("Slot", "3");

        assertTrue(hotbar.attachParameter(inventorySlot, 0));

        assertEquals("3", hotbar.getParameter("Slot").getStringValue());
        assertEquals("", hotbar.getParameter("Item").getStringValue());
    }
}
