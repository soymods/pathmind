package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class NodePlayerStateSensorEvaluator {
    private static final long FALLING_SENSOR_RETENTION_MS = 1000L;
    private static final double FALLING_SENSOR_MIN_CLEARANCE = 0.6D;
    private static final double GROUNDED_CLEARANCE_EPSILON = 1.0E-3;
    private static final Map<UUID, FallingTracker> FALLING_TRACKERS = new ConcurrentHashMap<>();

    private final Node owner;

    NodePlayerStateSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateFalling() {
        double distance = Math.max(0.0, owner.getDoubleParameter("Distance", 2.0));
        return isFalling(distance);
    }

    boolean isSwimming() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.player.isSwimming();
    }

    boolean isInLava() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.player.isInLava();
    }

    boolean isUnderwater() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.player.isUnderWater();
    }

    Optional<Double> getDistanceFromGround() {
        return computeDistanceFromGround(true);
    }

    private Optional<Double> computeDistanceFromGround(boolean treatOnGroundAsZero) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }
        if (treatOnGroundAsZero && client.player.onGround()) {
            return Optional.of(0.0);
        }

        AABB box = client.player.getBoundingBox();
        double bottomY = box.minY;
        double bottomLimit = client.level.getMinY() - 1.0;
        double inset = 1.0E-3;
        Vec3[] samplePoints = new Vec3[] {
            new Vec3((box.minX + box.maxX) * 0.5, bottomY + 0.01, (box.minZ + box.maxZ) * 0.5),
            new Vec3(box.minX + inset, bottomY + 0.01, box.minZ + inset),
            new Vec3(box.minX + inset, bottomY + 0.01, box.maxZ - inset),
            new Vec3(box.maxX - inset, bottomY + 0.01, box.minZ + inset),
            new Vec3(box.maxX - inset, bottomY + 0.01, box.maxZ - inset)
        };

        Double nearestDistance = null;
        for (Vec3 start : samplePoints) {
            HitResult hit = client.level.clip(new ClipContext(
                start,
                new Vec3(start.x, bottomLimit, start.z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
            ));
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            double distance = Math.max(0.0, bottomY - blockHit.getLocation().y);
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
        double accumulatedDrop = Math.max(fallDistance, peakY - currentY);
        if (accumulatedDrop >= requiredDistance) {
            return true;
        }
        if (groundClearance >= FALLING_SENSOR_MIN_CLEARANCE
            && accumulatedDrop > 1.0E-3) {
            return true;
        }
        return downwardVelocity < -1.0E-3
            && groundClearance >= FALLING_SENSOR_MIN_CLEARANCE
            && accumulatedDrop > 1.0E-3;
    }

    boolean isFalling(double distance) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        LocalPlayer player = client.player;
        FallingTracker tracker = FALLING_TRACKERS.computeIfAbsent(player.getUUID(), ignored -> new FallingTracker());
        long now = System.currentTimeMillis();
        double currentY = player.getY();
        double groundClearance = computeDistanceFromGround(false).orElse(Double.POSITIVE_INFINITY);
        double downwardVelocity = player.getDeltaMovement().y;
        boolean descending = downwardVelocity < -1.0E-3;
        boolean groundedByFlag = player.onGround();
        boolean groundedByClearance = groundClearance <= GROUNDED_CLEARANCE_EPSILON;
        if (groundedByFlag || groundedByClearance) {
            tracker.reset(currentY);
            return false;
        }
        if (player.isSwimming()
            || player.isUnderWater()
            || player.onClimbable()
            || player.getAbilities().flying) {
            tracker.clear(currentY);
            return false;
        }

        if (!tracker.peakInitialized) {
            tracker.peakY = currentY;
            tracker.peakInitialized = true;
        } else if (currentY > tracker.peakY) {
            tracker.peakY = currentY;
        }

        double downwardMotion = downwardVelocity;
        if (tracker.previousYInitialized) {
            downwardMotion = Math.min(downwardMotion, currentY - tracker.previousY);
        }
        tracker.previousY = currentY;
        tracker.previousYInitialized = true;

        boolean falling = isFallingState(
            false,
            false,
            false,
            false,
            false,
            downwardMotion,
            player.fallDistance,
            tracker.peakY,
            currentY,
            groundClearance,
            distance,
            now,
            tracker.lastDetectedAtMs
        );
        if (falling) {
            tracker.lastDetectedAtMs = now;
        }
        return falling;
    }

    private static final class FallingTracker {
        double peakY = Double.NaN;
        boolean peakInitialized;
        double previousY = Double.NaN;
        boolean previousYInitialized;
        long lastDetectedAtMs = Long.MIN_VALUE;

        void reset(double currentY) {
            peakY = currentY;
            peakInitialized = true;
            previousY = currentY;
            previousYInitialized = true;
            lastDetectedAtMs = Long.MIN_VALUE;
        }

        void clear(double currentY) {
            peakY = currentY;
            peakInitialized = false;
            previousY = currentY;
            previousYInitialized = false;
            lastDetectedAtMs = Long.MIN_VALUE;
        }
    }
}
