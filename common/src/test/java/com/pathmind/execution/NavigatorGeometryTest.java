package com.pathmind.execution;

import java.util.List;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorGeometryTest {
    @Test
    void partialBlockTopIsAcceptedAsAStandingSurface() {
        AABB bottomSlab = new AABB(0.0D, 64.0D, 0.0D, 1.0D, 64.5D, 1.0D);

        double surface = NavigatorGeometry.highestSupportingSurface(
            0.20D, 0.80D, 0.20D, 0.80D, 64.0D, 64.999D, List.of(bottomSlab)
        ).orElseThrow();

        assertEquals(64.5D, surface);
    }

    @Test
    void narrowShapeOutsidePlayerFootprintIsNotSupport() {
        AABB edgePost = new AABB(0.0D, 64.0D, 0.0D, 0.10D, 65.0D, 0.10D);

        assertTrue(NavigatorGeometry.highestSupportingSurface(
            0.20D, 0.80D, 0.20D, 0.80D, 64.0D, 64.999D, List.of(edgePost)
        ).isEmpty());
    }

    @Test
    void bodyTouchingSupportAtItsTopIsNotAFalseCollision() {
        AABB support = new AABB(0.0D, 63.0D, 0.0D, 1.0D, 64.0D, 1.0D);
        AABB player = new AABB(0.20D, 64.0D, 0.20D, 0.80D, 65.80D, 0.80D);

        assertFalse(NavigatorGeometry.intersectsStrictly(player, support, 1.0E-5D));
    }

    @Test
    void arrivalToleranceAcceptsSafeBlockInteriorButRejectsNeighboringBlock() {
        assertTrue(NavigatorGeometry.isWithinArrivalTolerance(10.88D, 64.5D, 20.15D, 10.5D, 64.5D, 20.5D));
        assertFalse(NavigatorGeometry.isWithinArrivalTolerance(11.01D, 64.5D, 20.5D, 10.5D, 64.5D, 20.5D));
    }

    @Test
    void sharpTurnsPauseForwardMovementUntilAligned() {
        assertTrue(NavigatorGeometry.shouldTurnInPlace(true, false, false, 1.0D, 80.0F, 52.0F));
        assertFalse(NavigatorGeometry.shouldTurnInPlace(true, false, false, 1.0D, 20.0F, 52.0F));
        assertFalse(NavigatorGeometry.shouldTurnInPlace(true, true, false, 1.0D, 80.0F, 52.0F));
    }

    @Test
    void steeringLookaheadStrengthIncreasesNearTheCorner() {
        double far = NavigatorGeometry.steeringLookaheadBlend(1.30D, 1.35D);
        double near = NavigatorGeometry.steeringLookaheadBlend(0.20D, 1.35D);

        assertTrue(near > far);
        assertTrue(far >= 0.30D);
        assertTrue(near <= 0.75D);
    }
}
