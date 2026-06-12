package com.pathmind.nodes;

import com.pathmind.util.PlayerInventoryBridge;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

final class NodeTargetSensorEvaluator {
    private final Node owner;

    NodeTargetSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    Optional<BlockState> getTargetedBlockState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return Optional.empty();
        }
        Optional<BlockHitResult> hit = getCurrentBlockHitResult();
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = hit.get().getBlockPos();
        if (pos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.world.getBlockState(pos));
    }

    Optional<BlockPos> getTargetedBlockPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        return getCurrentBlockHitResult().map(BlockHitResult::getBlockPos);
    }

    Optional<Entity> getTargetedEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit) || hit.getType() != HitResult.Type.ENTITY) {
            return Optional.empty();
        }
        Entity entity = entityHit.getEntity();
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    Optional<Direction> getLookDirection() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Vec3d look = client.player.getRotationVec(1.0F);
        return Optional.of(Direction.getFacing(look.x, look.y, look.z));
    }

    Optional<Integer> getCurrentHotbarSlot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlayerInventoryBridge.getSelectedSlot(client.player.getInventory()));
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    Optional<Direction> getTargetedBlockFace() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        Optional<BlockHitResult> hit = getCurrentBlockHitResult();
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        Direction face = hit.get().getSide();
        return face == null ? Optional.empty() : Optional.of(face);
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        BlockHitResult freshHit = owner.raycastBlockFromOrientation(
            client,
            client.player.getYaw(),
            client.player.getPitch(),
            Node.getBlockInteractionReach(client)
        );
        if (freshHit != null) {
            return Optional.of(freshHit);
        }

        HitResult cachedHit = client.crosshairTarget;
        if (cachedHit instanceof BlockHitResult blockHit && cachedHit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(blockHit);
        }
        return Optional.empty();
    }
}
