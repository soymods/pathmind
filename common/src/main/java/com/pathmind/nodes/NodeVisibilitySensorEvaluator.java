package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.CameraCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.GameProfileCompatibilityBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        if (Node.isAnySelectionValue(entityId)) {
            double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
            Box searchBox = client.player.getBoundingBox().expand(renderDistance);
            List<Entity> matches = client.world.getOtherEntities(
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
            if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                continue;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
            if (isEntityRendered(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    boolean isResourceVisible(String resourceId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || entityId == null || entityId.isEmpty()) {
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
            if (isEntityVisible(client, entityType, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleResourceVisible(MinecraftClient client, String resourceId) {
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
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
            if (Registries.BLOCK.containsId(identifier)) {
                Block block = Registries.BLOCK.get(identifier);
                return isBlockVisible(client, block);
            }
            if (Registries.ITEM.containsId(identifier)) {
                Item item = Registries.ITEM.get(identifier);
                return isItemVisible(client, item);
            }
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                return isEntityVisible(client, entityType, "");
            }
        }
        return isPlayerVisible(client, resourceId);
    }

    private boolean isSingleResourceRendered(MinecraftClient client, String resourceId) {
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
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
            if (Registries.BLOCK.containsId(identifier)) {
                Block block = Registries.BLOCK.get(identifier);
                return isBlockRendered(client, block);
            }
            if (Registries.ITEM.containsId(identifier)) {
                Item item = Registries.ITEM.get(identifier);
                return isItemRendered(client, item);
            }
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                return isEntityRendered(client, entityType, "");
            }
        }
        return isPlayerRendered(client, resourceId);
    }

    private boolean isBlockRendered(MinecraftClient client, Block block) {
        return isBlockRendered(client, block, null);
    }

    private boolean isBlockRendered(MinecraftClient client, BlockSelection selection) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockRendered(client, block, selection);
    }

    private boolean isBlockRendered(MinecraftClient client, Block block, BlockSelection selection) {
        if (client == null || client.player == null || client.world == null || block == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(hitPos);
            boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
            if (matches) {
                return true;
            }
        }

        BlockPos playerPos = client.player.getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue();
        int horizontalRadius = MathHelper.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = MathHelper.clamp(viewDistance * 2, 6, 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.world.getBlockState(mutable);
                    boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
                    if (matches && isBlockVisible(client, mutable, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(MinecraftClient client, BlockPos pos) {
        return isBlockVisible(client, pos, true);
    }

    private boolean isBlockVisible(MinecraftClient client, Block block) {
        return isBlockVisible(client, block, null);
    }

    private boolean isBlockVisible(MinecraftClient client, BlockSelection selection) {
        if (selection == null) {
            return false;
        }
        Block block = selection.getBlock();
        if (block == null) {
            return false;
        }
        return isBlockVisible(client, block, selection);
    }

    private boolean isBlockVisible(MinecraftClient client, Block block, BlockSelection selection) {
        if (client == null || client.player == null || client.world == null || block == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = client.world.getBlockState(hitPos);
            boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
            if (matches && isBlockVisible(client, hitPos, true)) {
                return true;
            }
        }

        BlockPos playerPos = client.player.getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue();
        int horizontalRadius = MathHelper.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = MathHelper.clamp(viewDistance * 2, 6, 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.world.getBlockState(mutable);
                    boolean matches = selection != null ? selection.matches(state) : state.isOf(block);
                    if (matches && isBlockVisible(client, mutable, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(MinecraftClient client, BlockPos pos, boolean requireInFieldOfView) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        for (Vec3d target : getBlockVisibilitySamplePoints(pos)) {
            if (isBlockSampleVisible(client, pos, target, requireInFieldOfView)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockSampleVisible(MinecraftClient client, BlockPos pos, Vec3d target, boolean requireInFieldOfView) {
        if (client == null || client.player == null || client.world == null || pos == null || target == null) {
            return false;
        }
        if (requireInFieldOfView && !isPointInPlayerFieldOfView(client, target)) {
            return false;
        }
        Vec3d cameraPos = CameraCompatibilityBridge.getPos(client.gameRenderer.getCamera());
        RaycastContext context = new RaycastContext(
            cameraPos,
            target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            client.player
        );
        BlockHitResult hit = client.world.raycast(context);
        if (hit == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private List<Vec3d> getBlockVisibilitySamplePoints(BlockPos pos) {
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
            new Vec3d(midX, midY, midZ),
            new Vec3d(midX, maxY, midZ),
            new Vec3d(midX, minY, midZ),
            new Vec3d(minX, midY, midZ),
            new Vec3d(maxX, midY, midZ),
            new Vec3d(midX, midY, minZ),
            new Vec3d(midX, midY, maxZ),
            new Vec3d(minX, maxY, minZ),
            new Vec3d(minX, maxY, maxZ),
            new Vec3d(maxX, maxY, minZ),
            new Vec3d(maxX, maxY, maxZ),
            new Vec3d(minX, minY, minZ),
            new Vec3d(minX, minY, maxZ),
            new Vec3d(maxX, minY, minZ),
            new Vec3d(maxX, minY, maxZ)
        );
    }

    private boolean isItemRendered(MinecraftClient client, Item item) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return false;
        }

        if (client.player.getMainHandStack().isOf(item) || client.player.getOffHandStack().isOf(item)) {
            return true;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (targetEntity instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty() && itemEntity.getStack().isOf(item)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item) && client.player.canSee(entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isItemVisible(MinecraftClient client, Item item) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return false;
        }
        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null
                && !entity.isRemoved()
                && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item)
                && client.player.canSee(entity)
                && isEntityInPlayerFieldOfView(client, entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isEntityRendered(MinecraftClient client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != null
            && entityHit.getEntity().getType() == entityType
            && EntityStateOptions.matchesState(entityHit.getEntity(), state)) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
        );
        return !matches.isEmpty();
    }

    private boolean isEntityVisible(MinecraftClient client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != null
            && entityHit.getEntity().getType() == entityType
            && EntityStateOptions.matchesState(entityHit.getEntity(), state)
            && client.player.canSee(entityHit.getEntity())
            && isEntityInPlayerFieldOfView(client, entityHit.getEntity())) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
                && client.player.canSee(entity)
                && isEntityInPlayerFieldOfView(client, entity)
        );
        return !matches.isEmpty();
    }

    private boolean isPlayerRendered(MinecraftClient client, String playerName) {
        if (client == null || client.player == null || client.world == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayerEntity targetPlayer) {
            if (trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(targetPlayer.getGameProfile()))) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!trimmed.equalsIgnoreCase(
                GameProfileCompatibilityBridge.getName(playerEntity.getGameProfile()))) {
                continue;
            }
            if (playerEntity.squaredDistanceTo(client.player) > renderDistance * renderDistance) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isPlayerVisible(MinecraftClient client, String playerName) {
        if (client == null || client.player == null || client.world == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayerEntity targetPlayer) {
            if (trimmed.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(targetPlayer.getGameProfile()))
                && client.player.canSee(targetPlayer)
                && isEntityInPlayerFieldOfView(client, targetPlayer)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!trimmed.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(playerEntity.getGameProfile()))) {
                continue;
            }
            if (playerEntity.squaredDistanceTo(client.player) > renderDistance * renderDistance) {
                continue;
            }
            if (!client.player.canSee(playerEntity) || !isEntityInPlayerFieldOfView(client, playerEntity)) {
                continue;
            }
            return true;
        }

        return false;
    }

    private boolean isEntityInPlayerFieldOfView(MinecraftClient client, Entity entity) {
        if (entity == null) {
            return false;
        }
        Vec3d target = entity.getBoundingBox().getCenter();
        return isPointInPlayerFieldOfView(client, target);
    }

    private boolean isPointInPlayerFieldOfView(MinecraftClient client, Vec3d target) {
        if (client == null || client.player == null || target == null) {
            return false;
        }
        Vec3d eyePos = client.player.getEyePos();
        Vec3d toTarget = target.subtract(eyePos);
        if (toTarget.lengthSquared() <= 1.0E-6D) {
            return true;
        }
        Vec3d forward = client.player.getRotationVec(1.0F);
        if (forward.lengthSquared() <= 1.0E-6D) {
            return false;
        }
        Vec3d forwardNorm = forward.normalize();
        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = forwardNorm.crossProduct(worldUp);
        if (right.lengthSquared() <= 1.0E-6D) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(forwardNorm).normalize();

        Vec3d targetNorm = toTarget.normalize();
        double z = targetNorm.dotProduct(forwardNorm);
        if (z <= 0.0) {
            return false;
        }
        double x = targetNorm.dotProduct(right);
        double y = targetNorm.dotProduct(up);

        int width = client.getWindow() != null ? client.getWindow().getFramebufferWidth() : 0;
        int height = client.getWindow() != null ? client.getWindow().getFramebufferHeight() : 0;
        double aspect = (width > 0 && height > 0) ? (double) width / (double) height : (16.0 / 9.0);

        double verticalFovDegrees = MathHelper.clamp(client.options.getFov().getValue(), 30.0, 170.0);
        double verticalHalfRadians = Math.toRadians(verticalFovDegrees / 2.0);
        double horizontalHalfRadians = Math.atan(Math.tan(verticalHalfRadians) * aspect);

        double horizontalAngle = Math.atan2(Math.abs(x), z);
        double verticalAngle = Math.atan2(Math.abs(y), z);
        return horizontalAngle <= horizontalHalfRadians && verticalAngle <= verticalHalfRadians;
    }
}
