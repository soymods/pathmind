package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeLayoutStateTest {

    @Test
    void nodeDelegatesPositionAndBoundsToLayoutState() {
        Node node = new Node(NodeType.START, 10, 20);

        assertEquals(10, node.getX());
        assertEquals(20, node.getY());
        assertEquals(36, node.getWidth());
        assertEquals(36, node.getHeight());
        assertTrue(node.containsPoint(12, 24));
        assertFalse(node.containsPoint(100, 100));

        node.setPosition(30, 40);

        assertEquals(30, node.getX());
        assertEquals(40, node.getY());
        assertTrue(node.containsPoint(34, 44));
        assertFalse(node.containsPoint(12, 24));
    }

    @Test
    void stickyNoteSizeOverridesAreStoredInLayoutState() {
        Node note = new Node(NodeType.STICKY_NOTE, 0, 0);

        assertEquals(152, note.getStickyNoteWidthOverride());
        assertEquals(104, note.getStickyNoteHeightOverride());

        note.setStickyNoteSize(320, 180);

        assertEquals(320, note.getStickyNoteWidthOverride());
        assertEquals(180, note.getStickyNoteHeightOverride());
        assertEquals(320, note.getWidth());
        assertEquals(180, note.getHeight());
    }

    @Test
    void fieldWidthOverridesAreStoredInLayoutState() {
        Node coordinateNode = new Node(NodeType.CLICK_SCREEN, 0, 0);
        int initialCoordinateWidth = coordinateNode.getCoordinateFieldWidth();

        coordinateNode.setCoordinateFieldTextWidth(160);

        assertTrue(coordinateNode.getCoordinateFieldWidth() > initialCoordinateWidth);

        Node messageNode = new Node(NodeType.MESSAGE, 0, 0);
        int initialMessageWidth = messageNode.getWidth();

        messageNode.setMessageFieldTextWidth(240);
        messageNode.recalculateDimensions();

        assertTrue(messageNode.getWidth() > initialMessageWidth);
    }
}
