package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAttachmentsTest {

    @Test
    void parameterAttachmentRelationshipsAreStoredInNodeAttachments() {
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertTrue(look.attachParameter(amount, 0));

        assertSame(amount, look.getAttachedParameter(0));
        assertSame(look, amount.getParentParameterHost());
        assertEquals(0, amount.getParentParameterSlotIndex());
        assertTrue(look.getAttachedParameters().containsKey(0));
    }

    @Test
    void sensorAttachmentRelationshipsAreStoredInNodeAttachments() {
        Node control = new Node(NodeType.CONTROL_IF, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_IS_DAYTIME, 0, 0);

        assertTrue(control.attachSensor(sensor));

        assertSame(sensor, control.getAttachedSensor());
        assertSame(control, sensor.getParentControl());
    }

    @Test
    void replacingParameterClearsPreviousParentRelationship() {
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node firstAmount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        Node secondAmount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertTrue(look.attachParameter(firstAmount, 0));
        assertTrue(look.attachParameter(secondAmount, 0));

        assertSame(secondAmount, look.getAttachedParameter(0));
        assertSame(look, secondAmount.getParentParameterHost());
        assertEquals(0, secondAmount.getParentParameterSlotIndex());
        assertNull(firstAmount.getParentParameterHost());
        assertEquals(-1, firstAmount.getParentParameterSlotIndex());
    }

    @Test
    void moveItemKeepsConfiguredAmountWhenSourceParameterExportsCount() {
        Node moveItem = new Node(NodeType.MOVE_ITEM, 0, 0);
        moveItem.setParameterValueAndPropagate("Count", "7");

        Node item = new Node(NodeType.PARAM_ITEM, 0, 0);
        item.setParameterValueAndPropagate("Item", "minecraft:stone");
        item.setParameterValueAndPropagate("Amount", "32");

        assertTrue(moveItem.attachParameter(item, 0));

        assertEquals("7", moveItem.getParameter("Count").getStringValue());
    }

    @Test
    void detachingSensorClearsBothSidesOfRelationship() {
        Node control = new Node(NodeType.CONTROL_IF, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_IS_DAYTIME, 0, 0);

        assertTrue(control.attachSensor(sensor));
        control.detachSensor();

        assertNull(control.getAttachedSensor());
        assertNull(sensor.getParentControl());
    }
}
