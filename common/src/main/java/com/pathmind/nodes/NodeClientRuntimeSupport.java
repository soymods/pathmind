package com.pathmind.nodes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class NodeClientRuntimeSupport {
    private static final double DEFAULT_REACH_DISTANCE_SQUARED = 25.0D;
    private static final double DEFAULT_REACH_DISTANCE = Math.sqrt(DEFAULT_REACH_DISTANCE_SQUARED);
    static final int PLAYER_ARMOR_SLOT_COUNT = 4;
    private static final int PLAYER_OFFHAND_INVENTORY_INDEX = Inventory.INVENTORY_SIZE + PLAYER_ARMOR_SLOT_COUNT;
    private static final long SNEAK_SYNC_DELAY_MS = 75L;

    private NodeClientRuntimeSupport() {
    }

    static double getBlockInteractionReach(Minecraft client) {
        if (client == null || client.player == null) {
            return DEFAULT_REACH_DISTANCE;
        }
        return Math.max(0.0D, client.player.blockInteractionRange());
    }

    static double getBlockInteractionReachSquared(Minecraft client) {
        double reach = getBlockInteractionReach(client);
        return reach * reach;
    }

    static double getEntityInteractionReachSquared(Minecraft client) {
        double reach = DEFAULT_REACH_DISTANCE;
        if (client != null && client.player != null) {
            reach = Math.max(0.0D, client.player.entityInteractionRange());
        }
        return reach * reach;
    }

    static void applySneakState(Minecraft client, boolean active) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.setShiftKeyDown(active);
        if (client.options != null && client.options.keyShift != null) {
            client.options.keyShift.setDown(active);
        }
    }

    static void waitForSneakSync(Minecraft client, boolean previousState, boolean desiredState) throws InterruptedException {
        if (client == null || client.isSameThread() || previousState == desiredState) {
            return;
        }
        Thread.sleep(SNEAK_SYNC_DELAY_MS);
    }

    static BlockHitResult raycastBlockFromOrientation(Minecraft client, float yaw, float pitch, double distance) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        Vec3 eyePos = client.player.getEyePosition();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 direction = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        double reachDistance = getBlockInteractionReach(client);
        double rayDistance = distance > 0.0 ? Math.min(distance, reachDistance) : reachDistance;
        Vec3 end = eyePos.add(direction.scale(rayDistance));
        HitResult hit = client.level.clip(new ClipContext(
            eyePos,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            client.player
        ));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }

    static void runOnClientThread(Minecraft client, Runnable task) throws InterruptedException {
        if (client == null || client.isSameThread()) {
            task.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
    }

    static <T> T supplyFromClient(Minecraft client, Supplier<T> supplier) throws InterruptedException {
        if (client == null || client.isSameThread()) {
            return supplier.get();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                result.set(supplier.get());
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    static int clampInventorySlot(Inventory inventory, int slot) {
        return net.minecraft.util.Mth.clamp(slot, 0, inventory.getContainerSize() - 1);
    }

    static int getOffhandInventoryIndex(Inventory inventory) {
        if (inventory == null || inventory.getContainerSize() <= 0) {
            return -1;
        }
        int index = PLAYER_OFFHAND_INVENTORY_INDEX;
        if (index >= inventory.getContainerSize()) {
            return inventory.getContainerSize() - 1;
        }
        return index;
    }
}
