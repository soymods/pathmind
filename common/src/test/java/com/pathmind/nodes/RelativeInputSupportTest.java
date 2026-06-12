package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeInputSupportTest {

    @Test
    void detectsRelativeExpressions() {
        assertTrue(RelativeInputSupport.isRelativeExpression("~"));
        assertTrue(RelativeInputSupport.isRelativeExpression(" ~+4"));
        assertFalse(RelativeInputSupport.isRelativeExpression("4"));
        assertFalse(RelativeInputSupport.isRelativeExpression("$offset"));
    }

    @Test
    void resolvesRelativeExpressionsAgainstBaseValue() {
        assertEquals(64.0, RelativeInputSupport.resolveRelativeExpression("~", 64.0));
        assertEquals(66.0, RelativeInputSupport.resolveRelativeExpression("~+2", 64.0));
        assertEquals(61.0, RelativeInputSupport.resolveRelativeExpression("~-3", 64.0));
        assertEquals(67.0, RelativeInputSupport.resolveRelativeExpression("~1+2", 64.0));
    }

    @Test
    void exposesSupportedRelativeParameterFamilies() {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        Node rotation = new Node(NodeType.PARAM_ROTATION, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertTrue(RelativeInputSupport.supportsRelativeCoordinate(coordinate, "X"));
        assertTrue(RelativeInputSupport.supportsRelativeCoordinate(coordinate, "Y"));
        assertFalse(RelativeInputSupport.supportsRelativeCoordinate(amount, "Amount"));

        assertTrue(RelativeInputSupport.supportsRelativeLook(rotation, "Yaw"));
        assertTrue(RelativeInputSupport.supportsRelativeLook(rotation, "Pitch"));
        assertFalse(RelativeInputSupport.supportsRelativeLook(rotation, "YawOffset"));
    }
}
