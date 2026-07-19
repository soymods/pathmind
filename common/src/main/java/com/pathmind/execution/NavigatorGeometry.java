package com.pathmind.execution;

import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.world.phys.AABB;

final class NavigatorGeometry {
    private NavigatorGeometry() {
    }

    static OptionalDouble highestSupportingSurface(
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        double minimumSurface,
        double maximumSurface,
        List<AABB> collisionBoxes
    ) {
        if (collisionBoxes == null || collisionBoxes.isEmpty()) {
            return OptionalDouble.empty();
        }
        double best = Double.NEGATIVE_INFINITY;
        for (AABB box : collisionBoxes) {
            if (box == null || box.maxY < minimumSurface || box.maxY > maximumSurface) {
                continue;
            }
            if (box.maxX <= minX || box.minX >= maxX || box.maxZ <= minZ || box.minZ >= maxZ) {
                continue;
            }
            best = Math.max(best, box.maxY);
        }
        return Double.isFinite(best) ? OptionalDouble.of(best) : OptionalDouble.empty();
    }

    static boolean intersectsStrictly(AABB first, AABB second, double epsilon) {
        if (first == null || second == null) {
            return false;
        }
        return first.maxX > second.minX + epsilon
            && first.minX < second.maxX - epsilon
            && first.maxY > second.minY + epsilon
            && first.minY < second.maxY - epsilon
            && first.maxZ > second.minZ + epsilon
            && first.minZ < second.maxZ - epsilon;
    }

    static boolean isWithinArrivalTolerance(
        double playerX,
        double playerY,
        double playerZ,
        double targetCenterX,
        double targetFeetY,
        double targetCenterZ
    ) {
        return Math.abs(playerX - targetCenterX) <= 0.42D
            && Math.abs(playerZ - targetCenterZ) <= 0.42D
            && Math.abs(playerY - targetFeetY) <= 0.70D;
    }

    static boolean shouldTurnInPlace(
        boolean groundSegment,
        boolean nearFinalGoal,
        boolean committedAction,
        double waypointDistance,
        float yawError,
        float turnThreshold
    ) {
        return groundSegment
            && !nearFinalGoal
            && !committedAction
            && waypointDistance > 0.35D
            && yawError > turnThreshold;
    }

    static double steeringLookaheadBlend(double waypointDistance, double lookaheadDistance) {
        if (!(lookaheadDistance > 0.0D)) {
            return 0.0D;
        }
        double proximity = Math.max(0.0D, Math.min(1.0D, 1.0D - waypointDistance / lookaheadDistance));
        return 0.30D + proximity * 0.45D;
    }
}
