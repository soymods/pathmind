package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkModeTest {

    @Test
    void walkDefaultsToDurationOrDistance() {
        Node walk = new Node(NodeType.WALK, 0, 0);

        assertEquals(NodeMode.WALK_FOR, walk.getMode());
        assertEquals("Duration / Distance", walk.getParameterSlotLabel(1));
        assertEquals(2, walk.getParameters().size());
    }

    @Test
    void untilUsesTheExistingSecondSlotAsABooleanSensorCondition() {
        Node walk = new Node(NodeType.WALK, 0, 0);
        walk.setMode(NodeMode.WALK_UNTIL);
        Node condition = new Node(NodeType.SENSOR_IS_ON_GROUND, 0, 0);

        assertEquals("Until", walk.getParameterSlotLabel(1));
        assertTrue(walk.attachParameter(condition, 1));
        assertTrue(walk.isWalkUntilMode());
    }
}
