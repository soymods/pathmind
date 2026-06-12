package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeDimensionCalculatorTest {

    @Test
    void startNodeUsesFixedCompactSize() {
        Node start = new Node(NodeType.START, 0, 0);

        start.recalculateDimensions();

        assertEquals(36, start.getWidth());
        assertEquals(36, start.getHeight());
    }

    @Test
    void stickyNoteUsesStoredLayoutOverrideSize() {
        Node note = new Node(NodeType.STICKY_NOTE, 0, 0);

        note.setStickyNoteSize(280, 160);
        note.recalculateDimensions();

        assertEquals(280, note.getWidth());
        assertEquals(160, note.getHeight());
    }

    @Test
    void attachedParameterContributesToHostSize() {
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        int initialWidth = look.getWidth();
        look.attachParameter(amount);
        look.recalculateDimensions();

        assertTrue(look.getWidth() >= initialWidth);
        assertTrue(look.getHeight() >= look.getParameterSlotHeight(0) + Node.HEADER_HEIGHT);
    }
}
