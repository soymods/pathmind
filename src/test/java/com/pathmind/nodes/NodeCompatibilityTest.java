package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCompatibilityTest {

    @Test
    void greaterOperatorAcceptsPositionOfSensor() {
        Node operator = new Node(NodeType.OPERATOR_GREATER, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);

        assertTrue(operator.canAcceptParameterNode(sensor, 0));
        assertTrue(operator.attachParameter(sensor, 0));
    }

    @Test
    void lessOperatorAcceptsDistanceBetweenSensor() {
        Node operator = new Node(NodeType.OPERATOR_LESS, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);

        assertTrue(operator.canAcceptParameterNode(sensor, 0));
        assertTrue(operator.attachParameter(sensor, 0));
    }
}
