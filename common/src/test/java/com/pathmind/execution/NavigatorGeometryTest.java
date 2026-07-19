package com.pathmind.execution;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorGeometryTest {
    @Test
    void vanillaGrassAndFernsHaveNoTraversalCollision() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertTrue(Blocks.SHORT_GRASS.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertTrue(Blocks.TALL_GRASS.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertTrue(Blocks.FERN.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertTrue(Blocks.LARGE_FERN.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
    }

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
    void exactGoalCompletionRejectsEveryNeighboringBlock() {
        assertTrue(NavigatorGeometry.isExactGoalBlock(10, 64, 20, 10, 64, 20));
        assertFalse(NavigatorGeometry.isExactGoalBlock(11, 64, 20, 10, 64, 20));
        assertFalse(NavigatorGeometry.isExactGoalBlock(10, 65, 20, 10, 64, 20));
        assertFalse(NavigatorGeometry.isExactGoalBlock(10, 64, 19, 10, 64, 20));
    }

    @Test
    void independentCameraUsesVanillaMouseScalingAndPitchLimits() {
        assertEquals(13.0F, NavigatorGeometry.applyMouseYaw(10.0F, 20.0D));
        assertEquals(90.0F, NavigatorGeometry.applyMousePitch(89.0F, 20.0D));
        assertEquals(-90.0F, NavigatorGeometry.applyMousePitch(-89.0F, -20.0D));
    }

    @Test
    void frontAndBackThirdPersonViewsOrbitFromTheFreeCameraAngle() {
        assertEquals(30.0F, NavigatorGeometry.cameraYawForMode(30.0F, false));
        assertEquals(12.0F, NavigatorGeometry.cameraPitchForMode(12.0F, false));
        assertEquals(210.0F, NavigatorGeometry.cameraYawForMode(30.0F, true));
        assertEquals(-12.0F, NavigatorGeometry.cameraPitchForMode(12.0F, true));
    }

    @Test
    void sharpTurnsPauseForwardMovementUntilAligned() {
        assertTrue(NavigatorGeometry.shouldTurnInPlace(true, false, false, 1.0D, 80.0F, 52.0F));
        assertFalse(NavigatorGeometry.shouldTurnInPlace(true, false, false, 1.0D, 20.0F, 52.0F));
        assertFalse(NavigatorGeometry.shouldTurnInPlace(true, true, false, 1.0D, 80.0F, 52.0F));
    }

    @Test
    void minedAscentRequiresOnlyTheJumpArcAndNotOpenSideWalls() {
        assertTrue(NavigatorGeometry.hasMinedAscentJumpClearance(true, true, true, true));
        assertFalse(NavigatorGeometry.hasMinedAscentJumpClearance(false, true, true, true));
        assertFalse(NavigatorGeometry.hasMinedAscentJumpClearance(true, false, true, true));
        assertFalse(NavigatorGeometry.hasMinedAscentJumpClearance(true, true, false, true));
        assertFalse(NavigatorGeometry.hasMinedAscentJumpClearance(true, true, true, false));
    }

    @Test
    void passedWaypointDetectionPreventsOrbitingBackToSimpleStep() {
        assertTrue(NavigatorGeometry.hasPassedWaypoint(1.65D, 0.65D, 0.5D, 0.5D, 1.5D, 0.5D, 1.15D));
        assertFalse(NavigatorGeometry.hasPassedWaypoint(1.25D, 0.65D, 0.5D, 0.5D, 1.5D, 0.5D, 1.15D));
        assertFalse(NavigatorGeometry.hasPassedWaypoint(1.65D, 2.0D, 0.5D, 0.5D, 1.5D, 0.5D, 1.15D));
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
