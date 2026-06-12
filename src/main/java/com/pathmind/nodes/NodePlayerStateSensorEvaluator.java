package com.pathmind.nodes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

final class NodePlayerStateSensorEvaluator {
    private static final long FALLING_SENSOR_RETENTION_MS = 1000L;
    private static final double FALLING_SENSOR_MIN_CLEARANCE = 0.6D;

    private final Node owner;

    NodePlayerStateSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateFalling() {
        double distance = Math.max(0.0, owner.getDoubleParameter("Distance", 2.0));
        return isFalling(distance);
    }

    boolean isSwimming() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSwimming();
    }

    boolean isInLava() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isInLava();
    }

    boolean isUnderwater() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSubmergedInWater();
    }

    Optional<Double> getDistanceFromGround() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        Box box = client.player.getBoundingBox();
        double bottomY = box.minY;
        double bottomLimit = client.world.getBottomY() - 1.0;
        double inset = 1.0E-3;
        Vec3d[] samplePoints = new Vec3d[] {
            new Vec3d((box.minX + box.maxX) * 0.5, bottomY + 0.01, (box.minZ + box.maxZ) * 0.5),
            new Vec3d(box.minX + inset, bottomY + 0.01, box.minZ + inset),
            new Vec3d(box.minX + inset, bottomY + 0.01, box.maxZ - inset),
            new Vec3d(box.maxX - inset, bottomY + 0.01, box.minZ + inset),
            new Vec3d(box.maxX - inset, bottomY + 0.01, box.maxZ - inset)
        };

        Double nearestDistance = null;
        for (Vec3d start : samplePoints) {
            HitResult hit = client.world.raycast(new RaycastContext(
                start,
                new Vec3d(start.x, bottomLimit, start.z),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
            ));
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            double distance = Math.max(0.0, bottomY - blockHit.getPos().y);
            if (nearestDistance == null || distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        if (nearestDistance == null) {
            return Optional.empty();
        }
        if (nearestDistance < 1.0E-3) {
            nearestDistance = 0.0;
        }
        return Optional.of(nearestDistance);
    }

    static boolean isFallingState(
        boolean onGround,
        boolean swimming,
        boolean submergedInWater,
        boolean climbing,
        boolean flying,
        double downwardVelocity,
        double fallDistance,
        double peakY,
        double currentY,
        double groundClearance,
        double requiredDistance,
        long nowMs,
        long lastDetectedAtMs
    ) {
        if (onGround || swimming || submergedInWater || climbing || flying) {
            return false;
        }
        if (lastDetectedAtMs != Long.MIN_VALUE && nowMs - lastDetectedAtMs <= FALLING_SENSOR_RETENTION_MS) {
            return true;
        }
        if (downwardVelocity >= -1.0E-3) {
            return false;
        }
        if (groundClearance >= FALLING_SENSOR_MIN_CLEARANCE
            && (fallDistance > 1.0E-3 || peakY - currentY > 1.0E-3)) {
            return true;
        }
        if (fallDistance >= requiredDistance) {
            return true;
        }
        return peakY - currentY >= requiredDistance;
    }

    boolean isFalling(double distance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        NodeRuntimeState runtimeState = owner.runtimeState();
        long now = System.currentTimeMillis();
        double currentY = client.player.getY();
        if (client.player.isOnGround()) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = true;
            runtimeState.lastFallingDetectedAtMs = Long.MIN_VALUE;
            return false;
        }
        if (client.player.isSwimming()
            || client.player.isSubmergedInWater()
            || client.player.isClimbing()
            || client.player.getAbilities().flying) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = false;
            runtimeState.lastFallingDetectedAtMs = Long.MIN_VALUE;
            return false;
        }

        if (!runtimeState.fallingPeakInitialized) {
            runtimeState.fallingPeakY = currentY;
            runtimeState.fallingPeakInitialized = true;
        } else if (currentY > runtimeState.fallingPeakY) {
            runtimeState.fallingPeakY = currentY;
        }
        double groundClearance = getDistanceFromGround().orElse(Double.POSITIVE_INFINITY);

        boolean falling = isFallingState(
            false,
            false,
            false,
            false,
            false,
            client.player.getVelocity().y,
            client.player.fallDistance,
            runtimeState.fallingPeakY,
            currentY,
            groundClearance,
            distance,
            now,
            runtimeState.lastFallingDetectedAtMs
        );
        if (falling) {
            runtimeState.lastFallingDetectedAtMs = now;
        }
        return falling;
    }
}
