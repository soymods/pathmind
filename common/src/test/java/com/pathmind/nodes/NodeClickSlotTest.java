package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.inventory.ClickType;
import org.junit.jupiter.api.Test;

class NodeClickSlotTest {

    @Test
    void eachModeSendsTheClickVanillaSendsForTheSameInput() {
        assertEquals(ClickType.PICKUP,
            NodeInventoryCommandExecutor.clickSlotClickType(NodeMode.CLICK_SLOT_LEFT));
        assertEquals(ClickType.QUICK_MOVE,
            NodeInventoryCommandExecutor.clickSlotClickType(NodeMode.CLICK_SLOT_SHIFT));
        assertEquals(ClickType.SWAP,
            NodeInventoryCommandExecutor.clickSlotClickType(NodeMode.CLICK_SLOT_SWAP_OFFHAND));

        // Only the offhand swap carries a button; SWAP with any other button hits a hotbar slot.
        assertEquals(0, NodeInventoryCommandExecutor.clickSlotButton(NodeMode.CLICK_SLOT_LEFT));
        assertEquals(0, NodeInventoryCommandExecutor.clickSlotButton(NodeMode.CLICK_SLOT_SHIFT));
        assertEquals(40, NodeInventoryCommandExecutor.clickSlotButton(NodeMode.CLICK_SLOT_SWAP_OFFHAND));
    }

    @Test
    void aNodeSavedBeforeTheModesExistedStillSendsAPlainLeftClick() {
        assertEquals(ClickType.PICKUP, NodeInventoryCommandExecutor.clickSlotClickType(null));
        assertEquals(0, NodeInventoryCommandExecutor.clickSlotButton(null));
    }

    @Test
    void clickSlotOffersItsThreeModesWithLeftClickAsTheDefault() {
        assertEquals(NodeMode.CLICK_SLOT_LEFT, NodeMode.getDefaultModeForNodeType(NodeType.CLICK_SLOT));
        assertEquals(3, NodeMode.getModesForNodeType(NodeType.CLICK_SLOT).length);
    }

    @Test
    void noModeDeclaresAWriteInSlotParameter() {
        // The attached Slot node is the only source for the index. A mode parameter here would
        // draw a second, redundant field on the node, and the attachment overwrote whatever it
        // held on every run anyway.
        for (NodeMode mode : NodeMode.getModesForNodeType(NodeType.CLICK_SLOT)) {
            java.util.List<NodeParameter> parameters = new java.util.ArrayList<>();
            NodeCatalog.initializeParameters(parameters, NodeType.CLICK_SLOT, mode);
            assertTrue(parameters.isEmpty(), mode.name());
        }
    }

    @Test
    void theSlotComesFromARequiredAttachedNode() {
        // Required is what makes dropping the write-in safe: a Click Slot with nothing attached
        // fails in NodeExecutionCoordinator before the executor runs, rather than clicking slot 0.
        assertEquals(1, NodeCatalog.parameterSlotCount(NodeType.CLICK_SLOT));
        assertEquals("Slot", NodeCatalog.parameterSlotLabel(NodeType.CLICK_SLOT, 0));
        assertTrue(NodeCatalog.isParameterSlotAlwaysRequired(NodeType.CLICK_SLOT, 0));
    }
}
