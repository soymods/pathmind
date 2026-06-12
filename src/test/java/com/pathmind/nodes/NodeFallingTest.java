package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeFallingTest {
    private static final long NOW = 10_000L;

    @Test
    void fallingStateReturnsFalseWhenGrounded() {
        assertFalse(Node.isFallingState(
            true,
            false,
            false,
            false,
            false,
            -0.2,
            4.0,
            70.0,
            66.0,
            1.2,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateReturnsFalseWhenNotMovingDownward() {
        assertFalse(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            0.0,
            4.0,
            70.0,
            66.0,
            1.2,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateReturnsTrueWhenFallDistanceMeetsThreshold() {
        assertTrue(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            -0.2,
            2.5,
            70.0,
            69.5,
            1.2,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateReturnsTrueAsSoonAsAirborneDescentStarts() {
        assertTrue(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            -0.08,
            0.0,
            72.0,
            71.99,
            1.1,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateIgnoresShortAirborneStepDownsLikeStairs() {
        assertFalse(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            -0.08,
            0.0,
            72.0,
            71.99,
            0.25,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateReturnsFalseForSwimmingTraversal() {
        assertFalse(Node.isFallingState(
            false,
            true,
            false,
            false,
            false,
            -0.2,
            4.0,
            70.0,
            66.0,
            1.2,
            2.0,
            NOW,
            Long.MIN_VALUE
        ));
    }

    @Test
    void fallingStateStaysTrueBrieflyAfterRecentDetection() {
        assertTrue(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            0.0,
            0.0,
            64.0,
            64.0,
            0.0,
            0.25,
            NOW,
            NOW - 250L
        ));
    }

    @Test
    void fallingStateExpiresAfterRetentionWindow() {
        assertFalse(Node.isFallingState(
            false,
            false,
            false,
            false,
            false,
            0.0,
            0.0,
            64.0,
            64.0,
            0.0,
            0.25,
            NOW,
            NOW - 1_500L
        ));
    }
}
