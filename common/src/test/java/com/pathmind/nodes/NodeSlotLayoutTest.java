package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSlotLayoutTest {

    @Test
    void nodeDelegatesSensorSlotGeometryToSlotLayout() {
        Node control = new Node(NodeType.CONTROL_IF, 20, 30);

        assertEquals(NodeSlotLayout.sensorSlotLeft(control), control.getSensorSlotLeft());
        assertEquals(NodeSlotLayout.sensorSlotTop(control), control.getSensorSlotTop());
        assertEquals(NodeSlotLayout.sensorSlotWidth(control), control.getSensorSlotWidth());
        assertEquals(NodeSlotLayout.sensorSlotHeight(control), control.getSensorSlotHeight());
        assertTrue(control.isPointInsideSensorSlot(control.getSensorSlotLeft() + 1, control.getSensorSlotTop() + 1));
        assertFalse(control.isPointInsideSensorSlot(control.getSensorSlotLeft() - 1, control.getSensorSlotTop() - 1));
    }

    @Test
    void nodeDelegatesParameterSlotGeometryToSlotLayout() {
        Node operator = new Node(NodeType.OPERATOR_EQUALS, 100, 50);

        assertEquals(NodeSlotLayout.parameterSlotLeft(operator, 0), operator.getParameterSlotLeft(0));
        assertEquals(NodeSlotLayout.parameterSlotLeft(operator, 1), operator.getParameterSlotLeft(1));
        assertEquals(NodeSlotLayout.parameterSlotTop(operator, 0), operator.getParameterSlotTop(0));
        assertEquals(NodeSlotLayout.parameterSlotWidth(operator, 0), operator.getParameterSlotWidth(0));
        assertEquals(NodeSlotLayout.parameterSlotHeight(operator, 0), operator.getParameterSlotHeight(0));
        assertEquals(0, operator.getParameterSlotIndexAt(operator.getParameterSlotLeft(0) + 1, operator.getParameterSlotTop(0) + 1));
        assertEquals(1, operator.getParameterSlotIndexAt(operator.getParameterSlotLeft(1) + 1, operator.getParameterSlotTop(1) + 1));
    }

    @Test
    void nodeDelegatesActionSlotGeometryToSlotLayout() {
        Node repeat = new Node(NodeType.CONTROL_REPEAT, 40, 60);

        assertEquals(NodeSlotLayout.actionSlotLeft(repeat), repeat.getActionSlotLeft());
        assertEquals(NodeSlotLayout.actionSlotTop(repeat), repeat.getActionSlotTop());
        assertEquals(NodeSlotLayout.actionSlotWidth(repeat), repeat.getActionSlotWidth());
        assertEquals(NodeSlotLayout.actionSlotHeight(repeat), repeat.getActionSlotHeight());
        assertTrue(repeat.isPointInsideActionSlot(repeat.getActionSlotLeft() + 1, repeat.getActionSlotTop() + 1));
        assertFalse(repeat.isPointInsideActionSlot(repeat.getActionSlotLeft() - 1, repeat.getActionSlotTop() - 1));
    }
}
