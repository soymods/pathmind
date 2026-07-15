package com.pathmind.ui.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiHitTestTest {
    @Test
    void inclusiveBoundsIncludeEveryEdge() {
        assertTrue(UiHitTest.contains(10, 20, 10, 20, 30, 40));
        assertTrue(UiHitTest.contains(40, 60, 10, 20, 30, 40));
        assertFalse(UiHitTest.contains(41, 60, 10, 20, 30, 40));
        assertFalse(UiHitTest.contains(9.9, 20.0, 10, 20, 30, 40));
    }

    @Test
    void halfOpenBoundsExcludeRightAndBottomEdges() {
        assertTrue(UiHitTest.containsHalfOpen(39, 59, 10, 20, 30, 40));
        assertFalse(UiHitTest.containsHalfOpen(40, 59, 10, 20, 30, 40));
        assertFalse(UiHitTest.containsHalfOpen(39, 60, 10, 20, 30, 40));
    }

    @Test
    void primaryClickRequiresLeftMouseButtonAndValidBounds() {
        assertTrue(UiHitTest.primaryClick(15, 25, 0, 10, 20, 30, 40));
        assertFalse(UiHitTest.primaryClick(15, 25, 1, 10, 20, 30, 40));
        assertFalse(UiHitTest.contains(10, 20, 10, 20, -1, 5));
    }
}
