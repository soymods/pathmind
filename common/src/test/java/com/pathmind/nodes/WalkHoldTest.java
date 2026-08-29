package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The hold count is what stops one Walk finishing from cancelling another, so it is the part
 * worth pinning down. A null client makes the key write a no-op and leaves the counter visible.
 */
class WalkHoldTest {

    @BeforeEach
    void reset() {
        WalkHold.releaseAll(null);
    }

    @Test
    void keyStaysHeldWhileAnyWalkStillWantsIt() {
        WalkHold.acquire(null);
        WalkHold.acquire(null);
        WalkHold.release(null);
        assertTrue(WalkHold.isHeld(), "a second walk finishing must not release the first");
        WalkHold.release(null);
        assertFalse(WalkHold.isHeld());
    }

    @Test
    void timedWalkDoesNotCancelASustainedOne() {
        WalkHold.startSustained(null);
        WalkHold.acquire(null);
        WalkHold.release(null);
        assertTrue(WalkHold.isHeld(), "Start Walking must survive a timed walk running alongside it");
        WalkHold.stopSustained(null);
        assertFalse(WalkHold.isHeld());
    }

    @Test
    void sustainedHoldIsIdempotentInBothDirections() {
        WalkHold.startSustained(null);
        WalkHold.startSustained(null);
        WalkHold.stopSustained(null);
        assertFalse(WalkHold.isHeld(), "a second Start Walking must not need a second Stop Walking");
        WalkHold.stopSustained(null);
        assertFalse(WalkHold.isHeld());
    }

    @Test
    void unbalancedReleaseCannotDriveTheCountNegative() {
        // Stop-all zeroes the count while timed walks are still in their finally blocks; those
        // late releases must not leave a debt that swallows the next acquire.
        WalkHold.acquire(null);
        WalkHold.releaseAll(null);
        WalkHold.release(null);
        WalkHold.release(null);
        WalkHold.acquire(null);
        assertTrue(WalkHold.isHeld());
    }

    @Test
    void walkModesAreRegisteredWithTimedAsTheDefault() {
        assertEquals(NodeMode.WALK_FOR, NodeMode.getDefaultModeForNodeType(NodeType.WALK));
        assertEquals(3, NodeMode.getModesForNodeType(NodeType.WALK).length);
        // One slot, and it is optional: Look already aims the player, and Start Walking has
        // no duration. A required slot fails the node outright before it runs.
        assertEquals(1, NodeTraitRegistry.getParameterSlotCount(NodeType.WALK));
        assertFalse(NodeCatalog.isParameterSlotAlwaysRequired(NodeType.WALK, 0));
    }
}
