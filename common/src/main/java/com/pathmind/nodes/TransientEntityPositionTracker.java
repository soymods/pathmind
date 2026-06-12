package com.pathmind.nodes;

import com.pathmind.util.EntityCompatibilityBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TransientEntityPositionTracker {
    static final String EYE_OF_ENDER_ID = "minecraft:eye_of_ender";

    private static final long DEFAULT_TTL_MS = 6_000L;
    private static final double RANGE_GRACE_BLOCKS = 24.0D;
    private static final ConcurrentMap<String, TrackedPosition> RECENT_POSITIONS = new ConcurrentHashMap<>();

    private TransientEntityPositionTracker() {
    }

    static void remember(Entity entity) {
        if (entity == null) {
            return;
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null || !isFallbackEnabled(id.toString())) {
            return;
        }
        Vec3d pos = EntityCompatibilityBridge.getPos(entity);
        if (pos == null) {
            pos = Vec3d.ofCenter(entity.getBlockPos());
        }
        RECENT_POSITIONS.put(id.toString(), new TrackedPosition(id.toString(), pos, entity.getBlockPos(), System.currentTimeMillis()));
    }

    static void rememberNearby(MinecraftClient client, String entityId, double range) {
        if (client == null || client.player == null || client.world == null || entityId == null || !isFallbackEnabled(entityId)) {
            return;
        }
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
            return;
        }
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        double searchRadius = Math.max(1.0D, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        for (Entity entity : client.world.getOtherEntities(client.player, searchBox)) {
            if (entity != null && entity.getType() == type) {
                remember(entity);
            }
        }
    }

    static Optional<TrackedPosition> findRecent(MinecraftClient client, EntityType<?> type, double range) {
        if (client == null || client.player == null || type == null) {
            return Optional.empty();
        }
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        if (id == null || !isFallbackEnabled(id.toString())) {
            return Optional.empty();
        }
        TrackedPosition recent = RECENT_POSITIONS.get(id.toString());
        if (recent == null) {
            return Optional.empty();
        }
        long age = System.currentTimeMillis() - recent.timestampMillis();
        if (age < 0L || age > DEFAULT_TTL_MS) {
            RECENT_POSITIONS.remove(id.toString(), recent);
            return Optional.empty();
        }
        double allowedRange = Math.max(1.0D, range) + RANGE_GRACE_BLOCKS;
        Vec3d playerPos = EntityCompatibilityBridge.getPos(client.player);
        if (playerPos == null) {
            playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        }
        if (recent.position().squaredDistanceTo(playerPos) > allowedRange * allowedRange) {
            return Optional.empty();
        }
        return Optional.of(recent);
    }

    static boolean isFallbackEnabled(String entityId) {
        return EYE_OF_ENDER_ID.equals(entityId);
    }

    record TrackedPosition(String entityId, Vec3d position, BlockPos blockPos, long timestampMillis) {
    }
}
