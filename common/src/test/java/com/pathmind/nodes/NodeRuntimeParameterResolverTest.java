package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeRuntimeParameterResolverTest {

    @Test
    void relativeCoordinateExpressionsReachTheDoubleResolver() {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.setParameterValueAndPropagate("X", "~+2.5");

        // Unit tests have no player, so the current coordinate base is zero.
        // This protects the runtime path used by Build Schematic from treating
        // a bare/relative coordinate as an invalid numeric expression.
        assertEquals(2.5D, NodeRuntimeParameterResolver.parseNodeDouble(coordinate, "X", -1.0D));
    }

    @Test
    void relativeSyntaxIsNotAcceptedForOrdinaryNumericParameters() {
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        amount.setParameterValueAndPropagate("Amount", "~+2");

        assertEquals(17.0D, NodeRuntimeParameterResolver.parseNodeDouble(amount, "Amount", 17.0D));
    }

    @Test
    void ordinaryArithmeticStillUsesTheNumericExpressionResolver() {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.setParameterValueAndPropagate("Y", "3 * 2 + 1");

        assertEquals(7.0D, NodeRuntimeParameterResolver.parseNodeDouble(coordinate, "Y", -1.0D));
    }
}
