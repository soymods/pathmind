package com.pathmind.nodes;

import com.pathmind.util.PlayerInventoryBridge;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class NodeTargetSensorEvaluator {
    private final Node owner;

    NodeTargetSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    Optional<BlockState> getTargetedBlockState() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
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
        return Optional.ofNullable(client.level.getBlockState(pos));
    }

    Optional<BlockPos> getTargetedBlockPos() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        return getCurrentBlockHitResult().map(BlockHitResult::getBlockPos);
    }

    Optional<Entity> getTargetedEntity() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        HitResult hit = client.hitResult;
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Vec3 look = client.player.getViewVector(1.0F);
        return Optional.of(Direction.getApproximateNearest(look.x, look.y, look.z));
    }

    Optional<Integer> getCurrentHotbarSlot() {
        Minecraft client = Minecraft.getInstance();
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
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        Optional<BlockHitResult> hit = getCurrentBlockHitResult();
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        Direction face = hit.get().getDirection();
        return face == null ? Optional.empty() : Optional.of(face);
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }

        BlockHitResult freshHit = owner.raycastBlockFromOrientation(
            client,
            client.player.getYRot(),
            client.player.getXRot(),
            Node.getBlockInteractionReach(client)
        );
        if (freshHit != null) {
            return Optional.of(freshHit);
        }

        HitResult cachedHit = client.hitResult;
        if (cachedHit instanceof BlockHitResult blockHit && cachedHit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(blockHit);
        }
        return Optional.empty();
    }
}
