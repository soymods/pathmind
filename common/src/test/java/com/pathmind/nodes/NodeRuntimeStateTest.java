package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        runtimeState.hasSensorResult = true;
        runtimeState.lastSensorUpdatedAt = 123L;
        runtimeState.nextOutputSocket = 1;

        runtimeState.resetControlState();

        assertEquals(0, runtimeState.repeatRemainingIterations);
        assertFalse(runtimeState.repeatActive);
        assertFalse(runtimeState.repeatExecuteAttachedAction);
        assertFalse(runtimeState.lastSensorResult);
        assertFalse(runtimeState.hasSensorResult);
        assertEquals(0L, runtimeState.lastSensorUpdatedAt);
        assertEquals(0, runtimeState.nextOutputSocket);
    }

    @Test
    void runtimeReadingFollowsRepeatUntilGuardSensor() {
        Node repeatUntil = new Node(NodeType.CONTROL_REPEAT_UNTIL, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_IS_RAINING, 0, 0);
        Node action = new Node(NodeType.WALK, 0, 0);
        assertTrue(repeatUntil.attachSensor(sensor));

        sensor.runtimeState().lastSensorResult = true;
        sensor.runtimeState().hasSensorResult = true;
        sensor.runtimeState().lastSensorUpdatedAt = 456L;
        action.setActiveRepeatUntilGuard(repeatUntil);

        Node.SensorRuntimeReading reading = action.getRuntimeSensorReading();
        assertNotNull(reading);
        assertEquals(sensor.getType().getDisplayName(), reading.sensorName());
        assertTrue(reading.result());
        assertEquals(456L, reading.updatedAt());
    }

    @Test
    void runtimeReadingIsAbsentBeforeSensorEvaluation() {
        Node sensor = new Node(NodeType.SENSOR_IS_RAINING, 0, 0);

        assertNull(sensor.getRuntimeSensorReading());
    }
}
