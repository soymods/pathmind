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
    void everyModeKeepsTheSlotParameterSoOldPresetsRestoreIt() {
        // initializeParameters reads mode parameters instead of type parameters once a node has
        // modes, so a mode missing the Slot definition would silently drop the saved value.
        for (NodeMode mode : NodeMode.getModesForNodeType(NodeType.CLICK_SLOT)) {
            java.util.List<NodeParameter> parameters = new java.util.ArrayList<>();
            NodeCatalog.initializeParameters(parameters, NodeType.CLICK_SLOT, mode);
            assertEquals(1, parameters.size(), mode.name());
            assertEquals("Slot", parameters.get(0).getName(), mode.name());
            assertEquals("click_slot_index", parameters.get(0).getId(), mode.name());
            assertEquals("0", parameters.get(0).getStringValue(), mode.name());
        }
    }
}
