package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.CameraCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.GameProfileCompatibilityBridge;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class NodeVisibilitySensorEvaluator {
    private static final double BLOCK_SAMPLE_INSET = 0.08D;
    private final Node owner;

    NodeVisibilitySensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateRendered() {
        return evaluateResourceVisibility(true);
    }

    boolean evaluateVisible() {
        return evaluateResourceVisibility(false);
    }

    private boolean evaluateResourceVisibility(boolean rendered) {
        String resourceId = owner.getStringParameter("Resource", "stone");
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null) {
            if (owner.providesTrait(parameterNode, NodeValueTrait.ITEM)) {
                List<String> nodeItems = owner.resolveItemIdsFromParameter(parameterNode);
                if (!nodeItems.isEmpty()) {
                    resourceId = String.join(",", nodeItems);
                }
            } else if (owner.providesTrait(parameterNode, NodeValueTrait.ENTITY)) {
                String nodeEntity = Node.getParameterString(parameterNode, "Entity");
                if (nodeEntity != null && !nodeEntity.isEmpty()) {
                    String state = owner.getEntityParameterState(parameterNode);
                    return rendered ? isEntityRendered(nodeEntity, state) : isEntityVisible(nodeEntity, state);
                }
            } else if (owner.providesTrait(parameterNode, NodeValueTrait.PLAYER)) {
                String nodePlayer = Node.getParameterString(parameterNode, "Player");
                if (nodePlayer != null && !nodePlayer.isEmpty()) {
                    resourceId = nodePlayer;
                }
            } else if (owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
                String nodeBlock = owner.getBlockParameterValue(parameterNode);
                if (nodeBlock != null && !nodeBlock.isEmpty()) {
                    resourceId = nodeBlock;
                }
            } else {
                owner.sendIncompatibleParameterMessage(parameterNode);
            }
        }
        return rendered ? isResourceRendered(resourceId) : isResourceVisible(resourceId);
    }

    boolean isResourceRendered(String resourceId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty() && isResourceRendered(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceRendered(client, trimmed);
    }

    boolean isEntityRendered(String entityId, String state) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        if (Node.isAnySelectionValue(entityId)) {
            double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
            AABB searchBox = client.player.getBoundingBox().inflate(renderDistance);
            List<Entity> matches = client.level.getEntities(
                client.player,
                searchBox,
                entity -> entity != null
                    && entity.isAlive()
                    && EntityStateOptions.matchesState(entity, state)
            );
            return !matches.isEmpty();
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
            if (isEntityRendered(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    boolean isResourceVisible(String resourceId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty() && isResourceVisible(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceVisible(client, trimmed);
    }

    boolean isEntityVisible(String entityId, String state) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || entityId == null || entityId.isEmpty()) {
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
            if (isEntityVisible(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleResourceVisible(Minecraft client, String resourceId) {
        if (client == null || client.player == null || client.level == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        Optional<BlockSelection> selectionOptional = BlockSelection.parse(resourceId);
        if (selectionOptional.isPresent()) {
            BlockSelection selection = selectionOptional.get();
            Block block = selection.getBlock();
            return block != null && isBlockVisible(client, block, selection);
        }
        String normalized = resourceId.contains(":")
            ? resourceId.toLowerCase(Locale.ROOT)
            : resourceId;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier != null) {
            if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
                Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
                return isBlockVisible(client, block);
            }
            if (BuiltInRegistries.ITEM.containsKey(identifier)) {
                Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
                return isItemVisible(client, item);
            }
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
                return isEntityVisible(client, entityType, "");
            }
        }
        return isPlayerVisible(client, resourceId);
    }

    private boolean isSingleResourceRendered(Minecraft client, String resourceId) {
        if (client == null || client.player == null || client.level == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        Optional<BlockSelection> selectionOptional = BlockSelection.parse(resourceId);
        if (selectionOptional.isPresent()) {
            return isBlockRendered(client, selectionOptional.get());
        }
        String normalized = resourceId.contains(":")
            ? resourceId.toLowerCase(Locale.ROOT)
            : resourceId;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier != null) {
            if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
                Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
                return isBlockRendered(client, block);
            }
            if (BuiltInRegistries.ITEM.containsKey(identifier)) {
                Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
                return isItemRendered(client, item);
            }
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
                return isEntityRendered(client, entityType, "");
            }
        }
        return isPlayerRendered(client, resourceId);
    }

    private boolean isBlockRendered(Minecraft client, Block block) {
        return isBlockRendered(client, block, null);
    }

    private boolean isBlockRendered(Minecraft client, BlockSelection selection) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockRendered(client, block, selection);
    }

    private boolean isBlockRendered(Minecraft client, Block block, BlockSelection selection) {
        if (client == null || client.player == null || client.level == null || block == null) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(hitPos);
            boolean matches = selection != null ? selection.matches(state) : state.is(block);
            if (matches) {
                return true;
            }
        }

        BlockPos playerPos = client.player.blockPosition();
        int viewDistance = client.options.renderDistance().get();
        int horizontalRadius = Mth.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = Mth.clamp(viewDistance * 2, 6, 32);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.level.getBlockState(mutable);
                    boolean matches = selection != null ? selection.matches(state) : state.is(block);
                    if (matches && isBlockVisible(client, mutable, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(Minecraft client, BlockPos pos) {
        return isBlockVisible(client, pos, true);
    }

    private boolean isBlockVisible(Minecraft client, Block block) {
        return isBlockVisible(client, block, null);
    }

    private boolean isBlockVisible(Minecraft client, BlockSelection selection) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockVisible(client, block, selection);
    }

    private boolean isBlockVisible(Minecraft client, Block block, BlockSelection selection) {
        if (client == null || client.player == null || client.level == null || block == null) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(hitPos);
            boolean matches = selection != null ? selection.matches(state) : state.is(block);
            if (matches && isBlockVisible(client, hitPos, true)) {
                return true;
            }
        }

        BlockPos playerPos = client.player.blockPosition();
        int viewDistance = client.options.renderDistance().get();
        int horizontalRadius = Mth.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = Mth.clamp(viewDistance * 2, 6, 32);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.level.getBlockState(mutable);
                    boolean matches = selection != null ? selection.matches(state) : state.is(block);
                    if (matches && isBlockVisible(client, mutable, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(Minecraft client, BlockPos pos, boolean requireInFieldOfView) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        for (Vec3 target : getBlockVisibilitySamplePoints(pos)) {
            if (isBlockSampleVisible(client, pos, target, requireInFieldOfView)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockSampleVisible(Minecraft client, BlockPos pos, Vec3 target, boolean requireInFieldOfView) {
        if (client == null || client.player == null || client.level == null || pos == null || target == null) {
            return false;
        }
        if (requireInFieldOfView && !isPointInPlayerFieldOfView(client, target)) {
            return false;
        }
        Vec3 cameraPos = CameraCompatibilityBridge.getPos(client.gameRenderer.mainCamera());
        ClipContext context = new ClipContext(
            cameraPos,
            target,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            client.player
        );
        BlockHitResult hit = client.level.clip(context);
        if (hit == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private List<Vec3> getBlockVisibilitySamplePoints(BlockPos pos) {
        double minX = pos.getX() + BLOCK_SAMPLE_INSET;
        double minY = pos.getY() + BLOCK_SAMPLE_INSET;
        double minZ = pos.getZ() + BLOCK_SAMPLE_INSET;
        double maxX = pos.getX() + 1.0D - BLOCK_SAMPLE_INSET;
        double maxY = pos.getY() + 1.0D - BLOCK_SAMPLE_INSET;
        double maxZ = pos.getZ() + 1.0D - BLOCK_SAMPLE_INSET;
        double midX = (minX + maxX) * 0.5D;
        double midY = (minY + maxY) * 0.5D;
        double midZ = (minZ + maxZ) * 0.5D;

        return List.of(
            new Vec3(midX, midY, midZ),
            new Vec3(midX, maxY, midZ),
            new Vec3(midX, minY, midZ),
            new Vec3(minX, midY, midZ),
            new Vec3(maxX, midY, midZ),
            new Vec3(midX, midY, minZ),
            new Vec3(midX, midY, maxZ),
            new Vec3(minX, maxY, minZ),
            new Vec3(minX, maxY, maxZ),
            new Vec3(maxX, maxY, minZ),
            new Vec3(maxX, maxY, maxZ),
            new Vec3(minX, minY, minZ),
            new Vec3(minX, minY, maxZ),
            new Vec3(maxX, minY, minZ),
            new Vec3(maxX, minY, maxZ)
        );
    }

    private boolean isItemRendered(Minecraft client, Item item) {
        if (client == null || client.player == null || client.level == null || item == null) {
            return false;
        }

        if (client.player.getMainHandItem().is(item) || client.player.getOffhandItem().is(item)) {
            return true;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (targetEntity instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty() && itemEntity.getItem().is(item)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        AABB searchBox = client.player.getBoundingBox().inflate(renderDistance);
        List<ItemEntity> candidates = client.level.getEntitiesOfClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getItem().isEmpty()
                && entity.getItem().is(item) && client.player.hasLineOfSight(entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isItemVisible(Minecraft client, Item item) {
        if (client == null || client.player == null || client.level == null || item == null) {
            return false;
        }
        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        AABB searchBox = client.player.getBoundingBox().inflate(renderDistance);
        List<ItemEntity> candidates = client.level.getEntitiesOfClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null
                && !entity.isRemoved()
                && !entity.getItem().isEmpty()
                && entity.getItem().is(item)
                && client.player.hasLineOfSight(entity)
                && isEntityInPlayerFieldOfView(client, entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isEntityRendered(Minecraft client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.level == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != null
            && entityHit.getEntity().getType() == entityType
            && EntityStateOptions.matchesState(entityHit.getEntity(), state)) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        AABB searchBox = client.player.getBoundingBox().inflate(renderDistance);
        List<Entity> matches = client.level.getEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
        );
        return !matches.isEmpty();
    }

    private boolean isEntityVisible(Minecraft client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.level == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != null
            && entityHit.getEntity().getType() == entityType
            && EntityStateOptions.matchesState(entityHit.getEntity(), state)
            && client.player.hasLineOfSight(entityHit.getEntity())
            && isEntityInPlayerFieldOfView(client, entityHit.getEntity())) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        AABB searchBox = client.player.getBoundingBox().inflate(renderDistance);
        List<Entity> matches = client.level.getEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
                && client.player.hasLineOfSight(entity)
                && isEntityInPlayerFieldOfView(client, entity)
        );
        return !matches.isEmpty();
    }

    private boolean isPlayerRendered(Minecraft client, String playerName) {
        if (client == null || client.player == null || client.level == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayer targetPlayer) {
            if (trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(targetPlayer.getGameProfile()))) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        for (AbstractClientPlayer playerEntity : client.level.players()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(playerEntity.getGameProfile()))) {
                continue;
            }
            if (playerEntity.distanceToSqr(client.player) > renderDistance * renderDistance) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isPlayerVisible(Minecraft client, String playerName) {
        if (client == null || client.player == null || client.level == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.hitResult;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayer targetPlayer) {
            if (trimmed.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(targetPlayer.getGameProfile()))
                && client.player.hasLineOfSight(targetPlayer)
                && isEntityInPlayerFieldOfView(client, targetPlayer)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.renderDistance().get() * 4.0);
        for (AbstractClientPlayer playerEntity : client.level.players()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!trimmed.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(playerEntity.getGameProfile()))) {
                continue;
            }
            if (playerEntity.distanceToSqr(client.player) > renderDistance * renderDistance) {
                continue;
            }
            if (!client.player.hasLineOfSight(playerEntity) || !isEntityInPlayerFieldOfView(client, playerEntity)) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isEntityInPlayerFieldOfView(Minecraft client, Entity entity) {
        if (entity == null) {
            return false;
        }
        Vec3 target = entity.getBoundingBox().getCenter();
        return isPointInPlayerFieldOfView(client, target);
    }

    private boolean isPointInPlayerFieldOfView(Minecraft client, Vec3 target) {
        if (client == null || client.player == null || target == null) {
            return false;
        }
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 toTarget = target.subtract(eyePos);
        if (toTarget.lengthSqr() <= 1.0E-6D) {
            return true;
        }
        Vec3 forward = client.player.getViewVector(1.0F);
        if (forward.lengthSqr() <= 1.0E-6D) {
            return false;
        }
        Vec3 forwardNorm = forward.normalize();
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = forwardNorm.cross(worldUp);
        if (right.lengthSqr() <= 1.0E-6D) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forwardNorm).normalize();

        Vec3 targetNorm = toTarget.normalize();
        double z = targetNorm.dot(forwardNorm);
        if (z <= 0.0) {
            return false;
        }
        double x = targetNorm.dot(right);
        double y = targetNorm.dot(up);

        int width = client.getWindow() != null ? client.getWindow().getWidth() : 0;
        int height = client.getWindow() != null ? client.getWindow().getHeight() : 0;
        double aspect = (width > 0 && height > 0) ? (double) width / (double) height : (16.0 / 9.0);

        double verticalFovDegrees = Mth.clamp(client.options.fov().get(), 30.0, 170.0);
        double verticalHalfRadians = Math.toRadians(verticalFovDegrees / 2.0);
        double horizontalHalfRadians = Math.atan(Math.tan(verticalHalfRadians) * aspect);

        double horizontalAngle = Math.atan2(Math.abs(x), z);
        double verticalAngle = Math.atan2(Math.abs(y), z);
        return horizontalAngle <= horizontalHalfRadians && verticalAngle <= verticalHalfRadians;
    }
}
