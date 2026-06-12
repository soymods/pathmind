package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeCraftTest {

    @Test
    void craftQuantityEvaluatesArithmeticAmountExpression() {
        Node craft = new Node(NodeType.CRAFT, 0, 0);

        craft.setParameterValueAndPropagate("Amount", "4*64/3");

        assertEquals(85, craft.getRequestedCraftQuantity());
    }

    @Test
    void craftQuantityClampsInvalidOrNonPositiveAmountToOne() {
        Node craft = new Node(NodeType.CRAFT, 0, 0);

        craft.setParameterValueAndPropagate("Amount", "0");

        assertEquals(1, craft.getRequestedCraftQuantity());
    }
}
