package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGeometryTest {

    @Test
    void socketYCentersCompactEntryNodes() {
        assertEquals(
            70,
            NodeGeometry.socketY(50, 40, 3, 6, 12, true, false, 14, 6));
    }

    @Test
    void socketYDistributesMinimalPresentationSocketsAroundCenter() {
        assertEquals(
            100,
            NodeGeometry.socketY(50, 100, 1, 3, 12, false, true, 14, 6));
    }

    @Test
    void containsPointUsesInclusiveNodeBounds() {
        assertTrue(NodeGeometry.containsPoint(10, 20, 30, 40, 40, 60));
        assertFalse(NodeGeometry.containsPoint(10, 20, 30, 40, 41, 60));
    }

    @Test
    void centeredChildAccountsForPaddingAndVisualAdjustment() {
        assertEquals(41, NodeGeometry.centeredChildX(10, 4, 100, 32, 6));
        assertEquals(40, NodeGeometry.centeredChildY(20, 4, 60, 20));
    }
}
