package com.pathmind.nodes;

import com.pathmind.util.EntityCompatibilityBridge;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !isFallbackEnabled(id.toString())) {
            return;
        }
        Vec3 pos = EntityCompatibilityBridge.getPos(entity);
        if (pos == null) {
            pos = Vec3.atCenterOf(entity.blockPosition());
        }
        RECENT_POSITIONS.put(id.toString(), new TrackedPosition(id.toString(), pos, entity.blockPosition(), System.currentTimeMillis()));
    }

    static void rememberNearby(Minecraft client, String entityId, double range) {
        if (client == null || client.player == null || client.level == null || entityId == null || !isFallbackEnabled(entityId)) {
            return;
        }
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        double searchRadius = Math.max(1.0D, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        for (Entity entity : client.level.getEntities(client.player, searchBox)) {
            if (entity != null && entity.getType() == type) {
                remember(entity);
            }
        }
    }

    static Optional<TrackedPosition> findRecent(Minecraft client, EntityType<?> type, double range) {
        if (client == null || client.player == null || type == null) {
            return Optional.empty();
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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
        Vec3 playerPos = EntityCompatibilityBridge.getPos(client.player);
        if (playerPos == null) {
            playerPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
        }
        if (recent.position().distanceToSqr(playerPos) > allowedRange * allowedRange) {
            return Optional.empty();
        }
        return Optional.of(recent);
    }

    static boolean isFallbackEnabled(String entityId) {
        return EYE_OF_ENDER_ID.equals(entityId);
    }

    record TrackedPosition(String entityId, Vec3 position, BlockPos blockPos, long timestampMillis) {
    }
}
