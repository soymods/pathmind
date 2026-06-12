package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeInteractionStateTest {

    @Test
    void nodeDelegatesSelectionAndDraggingToInteractionState() {
        Node node = new Node(NodeType.WAIT, 0, 0);

        assertFalse(node.isSelected());
        assertFalse(node.isDragging());
        assertTrue(node.shouldRenderSockets());
        assertEquals(0, node.getDragOffsetX());
        assertEquals(0, node.getDragOffsetY());

        node.setSelected(true);
        node.setDragging(true);
        node.setSocketsHidden(true);
        node.setDragOffsetX(12);
        node.setDragOffsetY(-8);

        assertTrue(node.isSelected());
        assertTrue(node.isDragging());
        assertFalse(node.shouldRenderSockets());
        assertEquals(12, node.getDragOffsetX());
        assertEquals(-8, node.getDragOffsetY());
    }
}
