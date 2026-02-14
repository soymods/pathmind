package com.pathmind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventorySlotModeHelperTest {

    @Test
    void buildStoredModeValueHandlesEmptyAndNullInputs() {
        assertEquals("", InventorySlotModeHelper.buildStoredModeValue(null, Boolean.TRUE));
        assertEquals("", InventorySlotModeHelper.buildStoredModeValue("", Boolean.FALSE));
        assertEquals("slot", InventorySlotModeHelper.buildStoredModeValue("slot", null));
    }

    @Test
    void buildStoredModeValueEncodesContext() {
        assertEquals("slot|player", InventorySlotModeHelper.buildStoredModeValue("slot", Boolean.TRUE));
        assertEquals("slot|container", InventorySlotModeHelper.buildStoredModeValue("slot", Boolean.FALSE));
    }

    @Test
    void extractModeIdReturnsBaseMode() {
        assertNull(InventorySlotModeHelper.extractModeId(null));
        assertEquals("slot", InventorySlotModeHelper.extractModeId("slot|player"));
        assertEquals("slot", InventorySlotModeHelper.extractModeId("slot"));
        assertEquals("", InventorySlotModeHelper.extractModeId("|player"));
    }

    @Test
    void extractPlayerSelectionFlagDecodesKnownValues() {
        assertNull(InventorySlotModeHelper.extractPlayerSelectionFlag(null));
        assertNull(InventorySlotModeHelper.extractPlayerSelectionFlag("slot"));
        assertNull(InventorySlotModeHelper.extractPlayerSelectionFlag("slot|"));
        assertEquals(Boolean.TRUE, InventorySlotModeHelper.extractPlayerSelectionFlag("slot|player"));
        assertEquals(Boolean.FALSE, InventorySlotModeHelper.extractPlayerSelectionFlag("slot|container"));
        assertEquals(Boolean.TRUE, InventorySlotModeHelper.extractPlayerSelectionFlag("slot|PLAYER"));
        assertNull(InventorySlotModeHelper.extractPlayerSelectionFlag("slot|unknown"));
    }
}
