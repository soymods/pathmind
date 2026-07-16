package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class NodeProximitySensorEvaluator {
    private final Node owner;

    NodeProximitySensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateTouchingBlock() {
        String blockId = owner.getStringParameter("Block", "stone");
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null) {
            if (!owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
                owner.sendIncompatibleParameterMessage(parameterNode);
                return false;
            }
            List<BlockSelection> selections = owner.resolveBlocksFromParameter(parameterNode);
            if (!selections.isEmpty()) {
                return isTouchingBlock(selections);
            }
        }
        return evaluateSensorCondition(Node.SensorConditionType.TOUCHING_BLOCK, blockId, null, 0, 0, 0);
    }

    boolean evaluateTouchingEntity() {
        String entityId = owner.getStringParameter("Entity", "zombie");
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null) {
            if (!owner.providesTrait(parameterNode, NodeValueTrait.ENTITY)) {
                owner.sendIncompatibleParameterMessage(parameterNode);
                return false;
            }
            String nodeEntity = Node.getParameterString(parameterNode, "Entity");
            if (nodeEntity != null && !nodeEntity.isEmpty()) {
                entityId = nodeEntity;
            }
            String state = owner.getEntityParameterState(parameterNode);
            return isTouchingEntity(entityId, state);
        }
        return evaluateSensorCondition(Node.SensorConditionType.TOUCHING_ENTITY, null, entityId, 0, 0, 0);
    }

    boolean evaluateAtCoordinates() {
        int x = owner.getIntParameter("X", 0);
        int y = owner.getIntParameter("Y", 64);
        int z = owner.getIntParameter("Z", 0);
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null) {
            if (!owner.providesTrait(parameterNode, NodeValueTrait.COORDINATE)) {
                owner.sendIncompatibleParameterMessage(parameterNode);
                return false;
            }
            Optional<Vec3> resolved = owner.resolvePositionTarget(parameterNode, null, null);
            if (resolved.isPresent()) {
                Vec3 vec = resolved.get();
                x = Mth.floor(vec.x);
                y = Mth.floor(vec.y);
                z = Mth.floor(vec.z);
            } else {
                x = Node.parseNodeInt(parameterNode, "X", x);
                y = Node.parseNodeInt(parameterNode, "Y", y);
                z = Node.parseNodeInt(parameterNode, "Z", z);
            }
        }
        return evaluateSensorCondition(Node.SensorConditionType.AT_COORDINATES, null, null, x, y, z);
    }

    boolean evaluateSensorCondition(Node.SensorConditionType type, String blockId, String entityId, int x, int y, int z) {
        if (type == null) {
            type = Node.SensorConditionType.TOUCHING_BLOCK;
        }
        return switch (type) {
            case TOUCHING_BLOCK -> isTouchingBlock(blockId);
            case TOUCHING_ENTITY -> isTouchingEntity(entityId);
            case AT_COORDINATES -> isAtCoordinates(x, y, z);
            default -> false;
        };
    }

    boolean isTouchingBlock(String blockId) {
        return isTouchingBlock(parseBlockSelectionList(blockId));
    }

    boolean isTouchingBlock(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.level.Level world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        AABB box = client.player.getBoundingBox().inflate(0.05);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    mutable.set(bx, by, bz);
                    BlockState state = world.getBlockState(mutable);
                    if (matchesAnyBlock(selections, state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean isTouchingEntity(String entityId) {
        return isTouchingEntity(entityId, "");
    }

    boolean isTouchingEntity(String entityId, String state) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        net.minecraft.world.level.Level world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        for (String candidateId : owner.splitMultiValueList(entityId)) {
            String sanitized = owner.sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? owner.normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
                continue;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
            List<Entity> entities = world.getEntities(
                client.player,
                client.player.getBoundingBox().inflate(0.15),
                entity -> entity.getType() == entityType && EntityStateOptions.matchesState(entity, state)
            );
            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean isAtCoordinates(int x, int y, int z) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.blockPosition();
        return playerPos.getX() == x && playerPos.getY() == y && playerPos.getZ() == z;
    }

    boolean isBlockAhead(String blockId) {
        return isBlockAhead(parseBlockSelectionList(blockId));
    }

    boolean isBlockAhead(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.level.Level world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        Direction facing = client.player.getDirection();
        BlockPos targetPos = client.player.blockPosition().relative(facing);
        BlockState state = world.getBlockState(targetPos);
        return matchesAnyBlock(selections, state);
    }

    boolean isBlockBelow(String blockId) {
        return isBlockBelow(parseBlockSelectionList(blockId));
    }

    boolean isBlockBelow(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.level.Level world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        BlockPos below = client.player.blockPosition().below();
        BlockState state = world.getBlockState(below);
        return matchesAnyBlock(selections, state);
    }

    List<BlockSelection> parseBlockSelectionList(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockSelection> selections = new ArrayList<>();
        for (String entry : owner.splitMultiValueList(blockId)) {
            BlockSelection.parse(entry).ifPresent(selections::add);
        }
        return selections;
    }

    boolean matchesAnyBlock(List<BlockSelection> selections, BlockState state) {
        if (selections == null || selections.isEmpty() || state == null) {
            return false;
        }
        for (BlockSelection selection : selections) {
            if (selection != null && selection.matches(state)) {
                return true;
            }
        }
        return false;
    }
}
