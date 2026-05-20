package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
            Optional<Vec3d> resolved = owner.resolvePositionTarget(parameterNode, null, null);
            if (resolved.isPresent()) {
                Vec3d vec = resolved.get();
                x = MathHelper.floor(vec.x);
                y = MathHelper.floor(vec.y);
                z = MathHelper.floor(vec.z);
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        Box box = client.player.getBoundingBox().expand(0.05);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        for (String candidateId : owner.splitMultiValueList(entityId)) {
            String sanitized = owner.sanitizeResourceId(candidateId);
            String normalized = sanitized != null && !sanitized.isEmpty()
                ? owner.normalizeResourceId(sanitized, "minecraft")
                : candidateId;
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            List<Entity> entities = world.getOtherEntities(
                client.player,
                client.player.getBoundingBox().expand(0.15),
                entity -> entity.getType() == entityType && EntityStateOptions.matchesState(entity, state)
            );
            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean isAtCoordinates(int x, int y, int z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        return playerPos.getX() == x && playerPos.getY() == y && playerPos.getZ() == z;
    }

    boolean isBlockAhead(String blockId) {
        return isBlockAhead(parseBlockSelectionList(blockId));
    }

    boolean isBlockAhead(List<BlockSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        Direction facing = client.player.getHorizontalFacing();
        BlockPos targetPos = client.player.getBlockPos().offset(facing);
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        net.minecraft.world.World world = EntityCompatibilityBridge.getWorld(client.player);
        if (world == null) {
            return false;
        }
        BlockPos below = client.player.getBlockPos().down();
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
