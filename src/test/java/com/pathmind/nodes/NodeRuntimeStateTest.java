package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRuntimeStateTest {

    @Test
    void owningStartNodeIsStoredInRuntimeState() {
        Node start = new Node(NodeType.START, 0, 0);
        Node action = new Node(NodeType.LOOK, 0, 0);

        action.setOwningStartNode(start);

        assertSame(start, action.getOwningStartNode());
    }

    @Test
    void startNodeNumberIsStoredInRuntimeState() {
        Node start = new Node(NodeType.START, 0, 0);

        start.setStartNodeNumber(7);

        assertEquals(7, start.getStartNodeNumber());
    }

    @Test
    void resetControlStateClearsRuntimeFlags() {
        Node repeat = new Node(NodeType.CONTROL_REPEAT, 0, 0);
        NodeRuntimeState runtimeState = repeat.getRuntimeState();

        runtimeState.repeatRemainingIterations = 3;
        runtimeState.repeatActive = true;
        runtimeState.repeatExecuteAttachedAction = true;
        runtimeState.lastSensorResult = true;
        runtimeState.nextOutputSocket = 1;
        runtimeState.fallingPeakY = 42.0;
        runtimeState.fallingPeakInitialized = true;
        runtimeState.lastFallingDetectedAtMs = 123L;

        runtimeState.resetControlState();

        assertEquals(0, runtimeState.repeatRemainingIterations);
        assertFalse(runtimeState.repeatActive);
        assertFalse(runtimeState.repeatExecuteAttachedAction);
        assertFalse(runtimeState.lastSensorResult);
        assertEquals(0, runtimeState.nextOutputSocket);
        assertTrue(Double.isNaN(runtimeState.fallingPeakY));
        assertFalse(runtimeState.fallingPeakInitialized);
        assertEquals(Long.MIN_VALUE, runtimeState.lastFallingDetectedAtMs);
    }
}
