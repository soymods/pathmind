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

    @Test
    void lookAcceptsAmountParameter() {
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        amount.getParameter("Amount").setStringValue("-45");

        assertTrue(look.canAcceptParameterNode(amount, 0));
        assertTrue(look.attachParameter(amount, 0));
    }

    @Test
    void orOperatorAcceptsCoordinateParameters() {
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);

        assertTrue(or.canAcceptParameterNode(coordinate, 0));
        assertTrue(or.attachParameter(coordinate, 0));
    }

    @Test
    void equalsSupportsCoordinateOrGroupComparisons() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node first = coordinateNode(0, 64, 0);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(or.attachParameter(first, 0));
        assertTrue(or.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(or, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void notSupportsCoordinateOrGroupComparisons() {
        Node not = new Node(NodeType.OPERATOR_NOT, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node first = coordinateNode(0, 64, 0);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(or.attachParameter(first, 0));
        assertTrue(or.attachParameter(second, 1));
        assertTrue(not.attachParameter(position, 0));
        assertTrue(not.attachParameter(or, 1));
        org.junit.jupiter.api.Assertions.assertFalse(not.evaluateSensor());
    }

    @Test
    void booleanOrStillEvaluatesBooleanInputs() {
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node left = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        Node right = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        left.getParameter("Toggle").setStringValue("false");
        right.getParameter("Toggle").setStringValue("true");

        assertTrue(or.attachParameter(left, 0));
        assertTrue(or.attachParameter(right, 1));
        assertTrue(or.evaluateSensor());
    }

    @Test
    void andOperatorAcceptsCoordinateParameters() {
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);

        assertTrue(and.canAcceptParameterNode(coordinate, 0));
        assertTrue(and.attachParameter(coordinate, 0));
    }

    @Test
    void equalsSupportsCoordinateAndGroupComparisons() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node first = coordinateNode(10, 64, 10);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(and.attachParameter(first, 0));
        assertTrue(and.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(and, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsFailsWhenCoordinateAndGroupContainsMismatch() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node first = coordinateNode(10, 64, 10);
        Node second = coordinateNode(0, 64, 0);

        assertTrue(and.attachParameter(first, 0));
        assertTrue(and.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(and, 1));
        org.junit.jupiter.api.Assertions.assertFalse(equals.evaluateSensor());
    }

    @Test
    void booleanAndStillEvaluatesBooleanInputs() {
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node left = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        Node right = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        left.getParameter("Toggle").setStringValue("true");
        right.getParameter("Toggle").setStringValue("true");

        assertTrue(and.attachParameter(left, 0));
        assertTrue(and.attachParameter(right, 1));
        assertTrue(and.evaluateSensor());
    }

    private Node coordinateNode(int x, int y, int z) {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue(Integer.toString(x));
        coordinate.getParameter("Y").setStringValue(Integer.toString(y));
        coordinate.getParameter("Z").setStringValue(Integer.toString(z));
        return coordinate;
    }
}
