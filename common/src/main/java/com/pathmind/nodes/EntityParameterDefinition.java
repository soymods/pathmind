package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class EntityParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ENTITY)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Range", "Distance"))
            .runtimeBehavior(EntityParameterDefinition::resolvePositionTarget)
            .listEntryBehavior(EntityParameterDefinition::resolveListEntry)
            .gotoFallbackTargetBehavior(EntityParameterDefinition::resolveGotoFallbackTarget)
            .build();
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        String state = owner.getEntityParameterState(parameterNode);
        double defaultRange = owner.getType() == NodeType.SENSOR_DISTANCE_BETWEEN ? 256.0 : Node.PARAMETER_SEARCH_RADIUS;
        double range = Node.parseNodeDouble(parameterNode, "Range", defaultRange);
        String rawEntity = Node.getParameterString(parameterNode, "Entity");

        if (Node.isAnySelectionValue(rawEntity)) {
            if (client.world == null) {
                return Optional.empty();
            }
            Optional<Entity> nearest = findNearestAnyEntity(client, range, state);
            if (nearest.isEmpty()) {
                owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noNearbyEntityMessage(owner), future);
                return Optional.empty();
            }
            Identifier nearestIdentifier = Registries.ENTITY_TYPE.getId(nearest.get().getType());
            return resolvedEntityPosition(nearest.get(), nearestIdentifier != null ? nearestIdentifier.toString() : null, data);
        }

        List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            owner.sendParameterSearchFailure(tr("pathmind.error.noEntitySelectedOnParameter", owner.getType().getDisplayName()), future);
            return Optional.empty();
        }
        Entity nearest = null;
        String nearestId = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> entity = owner.findNearestEntity(client, entityType, range, state);
            if (entity.isEmpty()) {
                continue;
            }
            double distance = entity.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = entity.get();
                nearestId = identifier.toString();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            Optional<Vec3d> recent = resolveRecentTransientPosition(owner, entityIds, client, range, data);
            if (recent.isPresent()) {
                return recent;
            }
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.noNearbyEntityMessage(owner), future);
            return Optional.empty();
        }
        return resolvedEntityPosition(nearest, nearestId, data);
    }

    private static Optional<Vec3d> resolveRecentTransientPosition(Node owner, List<String> entityIds, MinecraftClient client,
                                                                  double range, RuntimeParameterData data) {
        if (entityIds == null || entityIds.isEmpty() || client == null || client.player == null) {
            return Optional.empty();
        }

        TransientEntityPositionTracker.TrackedPosition nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<TransientEntityPositionTracker.TrackedPosition> recent =
                TransientEntityPositionTracker.findRecent(client, entityType, range);
            if (recent.isEmpty()) {
                continue;
            }
            Vec3d playerPos = EntityCompatibilityBridge.getPos(client.player);
            if (playerPos == null) {
                playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            }
            double distance = recent.get().position().squaredDistanceTo(playerPos);
            if (nearest == null || distance < nearestDistance) {
                nearest = recent.get();
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }
        if (data != null) {
            data.targetEntity = null;
            data.targetEntityId = nearest.entityId();
            data.targetBlockPos = nearest.blockPos();
            data.targetVector = nearest.position();
        }
        return Optional.of(nearest.position());
    }

    private static Node.ListValueEntry resolveListEntry(Node owner, Node parameterNode, MinecraftClient client) {
        String state = owner.getEntityParameterState(parameterNode);
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        String rawEntity = Node.getParameterString(parameterNode, "Entity");
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        if (Node.isAnySelectionValue(rawEntity)) {
            double searchRadius = Math.max(1.0, range);
            Box searchBox = client.player.getBoundingBox().expand(searchRadius);
            for (Entity candidate : client.world.getOtherEntities(
                client.player,
                searchBox,
                entity -> entity != null && !entity.isRemoved() && EntityStateOptions.matchesState(entity, state))) {
                double distance = candidate.squaredDistanceTo(client.player);
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
        } else {
            for (String candidateId : owner.resolveEntityIdsFromParameter(parameterNode)) {
                Identifier identifier = Identifier.tryParse(candidateId);
                if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                    continue;
                }
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                Optional<Entity> candidate = owner.findNearestEntity(client, entityType, range, state);
                if (candidate.isEmpty()) {
                    continue;
                }
                double distance = candidate.get().squaredDistanceTo(client.player);
                if (distance < nearestDistance) {
                    nearest = candidate.get();
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? new Node.ListValueEntry(NodeType.PARAM_ENTITY, nearest.getUuidAsString()) : null;
    }

    private static BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, MinecraftClient client,
                                                      CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.noEntitySelectedForNode", owner.getType().getDisplayName()));
            future.complete(null);
            return null;
        }
        String state = owner.getEntityParameterState(parameterNode);
        double range = Node.parseDoubleOrDefault(Node.getParameterString(parameterNode, "Range"), Node.PARAMETER_SEARCH_RADIUS);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String candidateId : entityIds) {
            Identifier identifier = Identifier.tryParse(candidateId);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            Optional<Entity> target = owner.findNearestEntity(client, entityType, range, state);
            if (target.isEmpty()) {
                continue;
            }
            double distance = target.get().squaredDistanceTo(client.player);
            if (distance < nearestDistance) {
                nearest = target.get();
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            owner.sendNodeErrorMessage(client, tr("pathmind.error.noMatchingEntityNearby", owner.getType().getDisplayName()));
            future.complete(null);
            return null;
        }
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (data != null) {
            data.targetBlockPos = nearest.getBlockPos();
            data.targetEntity = nearest;
        }
        return nearest.getBlockPos();
    }

    private static Optional<Entity> findNearestAnyEntity(MinecraftClient client, double range, String state) {
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.world.getOtherEntities(client.player, searchBox)) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (!EntityStateOptions.matchesState(entity, state)) {
                continue;
            }
            double distance = entity.squaredDistanceTo(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Optional<Vec3d> resolvedEntityPosition(Entity entity, String entityId, RuntimeParameterData data) {
        if (data != null) {
            data.targetEntity = entity;
            data.targetEntityId = entityId;
            data.targetBlockPos = entity.getBlockPos();
        }
        Vec3d entityPos = EntityCompatibilityBridge.getPos(entity);
        if (entityPos != null) {
            return Optional.of(entityPos);
        }
        return Optional.of(Vec3d.ofCenter(entity.getBlockPos()));
    }

    private EntityParameterDefinition() {
    }
}
