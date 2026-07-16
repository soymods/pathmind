package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.mojang.blaze3d.platform.InputConstants;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.HotbarSlotSynchronizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.lang.reflect.Method;

import org.lwjgl.glfw.GLFW;

final class NodeEntityActionCommandExecutor {
    private static final Method DO_ATTACK_METHOD = resolveDoAttackMethod();
    private static final double BREAK_AIM_EPSILON = 0.001D;

    private final Node owner;

    NodeEntityActionCommandExecutor(Node owner) {
        this.owner = owner;
    }
    void executeInteractCommand(java.util.concurrent.CompletableFuture<Void> future) {
        Node preprocessParameter = owner.getAttachedParameter(0);
        if (preprocessParameter != null && preprocessParameter.getType() == NodeType.VARIABLE) {
            Node resolved = owner.resolveVariableValueNode(preprocessParameter, 0, future);
            if (resolved == null) {
                if (!future.isDone()) {
                    future.complete(null);
                }
                return;
            }
            preprocessParameter = resolved;
        }
        boolean attachedBlockSelection = preprocessParameter != null && preprocessParameter.getType() == NodeType.PARAM_BLOCK;
        if (!attachedBlockSelection
            && owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null || client.level == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);
        boolean swingOnSuccess = owner.getBooleanParameter("SwingOnSuccess", true);
        boolean sneakWhileInteracting = owner.getBooleanParameter("SneakWhileInteracting", false);
        boolean restoreSneak = owner.getBooleanParameter("RestoreSneakState", true);

        boolean previousSneak = client.player.isShiftKeyDown();
        if (sneakWhileInteracting) {
            client.player.setShiftKeyDown(true);
            if (client.options != null && client.options.keyShift != null) {
                client.options.keyShift.setDown(true);
            }
        }

        Runnable restoreSneakState = () -> {
            if (sneakWhileInteracting && restoreSneak) {
                client.player.setShiftKeyDown(previousSneak);
                if (client.options != null && client.options.keyShift != null) {
                    client.options.keyShift.setDown(previousSneak);
                }
            }
        };

        RuntimeParameterData parameterData = owner.runtimeState().runtimeParameterData;
        Node attachedParameter = preprocessParameter != null ? preprocessParameter : owner.getAttachedParameter(0);
        InteractionResult result;

        if (attachedParameter != null && attachedParameter.getType() == NodeType.PARAM_BLOCK) {
            String blockSelectionRaw = owner.getBlockParameterValue(attachedParameter);
            if (blockSelectionRaw == null || blockSelectionRaw.isBlank()) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, tr("pathmind.error.interactUnknownBlock", "block"));
                future.complete(null);
                return;
            }
            Optional<BlockSelection> blockSelection = BlockSelection.parse(blockSelectionRaw);
            if (blockSelection.isEmpty()) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, tr("pathmind.error.interactUnknownBlock", blockSelectionRaw));
                future.complete(null);
                return;
            }

            Optional<BlockPos> targetPos = findNearestReachableBlock(client, List.of(blockSelection.get()));
            if (targetPos.isEmpty()) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, tr("pathmind.error.blockTooFarToInteract", blockSelectionRaw));
                future.complete(null);
                return;
            }

            List<BlockHitResult> hitCandidates = buildInteractionHitCandidates(client, targetPos.get());
            if (hitCandidates.isEmpty()) {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, tr("pathmind.error.blockTooFarToInteract", blockSelectionRaw));
                future.complete(null);
                return;
            }

            BlockHitResult hit = hitCandidates.getFirst();
            orientPlayerTowards(client, hit.getLocation());
            result = client.gameMode.useItemOn(client.player, hand, hit);
        } else {
            Entity targetEntity = parameterData != null ? parameterData.targetEntity : null;
            if (targetEntity != null) {
                if (targetEntity.isRemoved() || targetEntity.distanceToSqr(client.player.getEyePosition()) > Node.getEntityInteractionReachSquared(client)) {
                    restoreSneakState.run();
                    String entityName = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(targetEntity.getType()))
                        .replace("minecraft:", "")
                        .replace("_", " ");
                    owner.sendNodeErrorMessage(client, tr("pathmind.error.entityTooFarToInteract", entityName));
                    future.complete(null);
                    return;
                }
                orientPlayerTowards(client, targetEntity.getBoundingBox().getCenter());
                result = client.gameMode.interact(client.player, targetEntity, new EntityHitResult(targetEntity), hand);
            } else if (client.hitResult instanceof EntityHitResult entityHit) {
                Entity entity = entityHit.getEntity();
                if (entity == null || entity.distanceToSqr(client.player.getEyePosition()) > Node.getEntityInteractionReachSquared(client)) {
                    restoreSneakState.run();
                    owner.sendNodeErrorMessage(client, tr("pathmind.error.targetTooFarToInteract"));
                    future.complete(null);
                    return;
                }
                orientPlayerTowards(client, entity.getBoundingBox().getCenter());
                result = client.gameMode.interact(client.player, entity, entityHit, hand);
            } else if (client.hitResult instanceof BlockHitResult blockHit) {
                if (!isHitWithinReach(client, blockHit.getLocation())) {
                    restoreSneakState.run();
                    owner.sendNodeErrorMessage(client, tr("pathmind.error.targetTooFarToInteract"));
                    future.complete(null);
                    return;
                }
                orientPlayerTowards(client, blockHit.getLocation());
                result = client.gameMode.useItemOn(client.player, hand, blockHit);
            } else {
                restoreSneakState.run();
                owner.sendNodeErrorMessage(client, tr("pathmind.error.noTargetInRangeForNode", owner.getType().getDisplayName()));
                future.complete(null);
                return;
            }
        }

        if (swingOnSuccess && result != null && (result.consumesAction() || result == InteractionResult.PASS)) {
            client.player.swing(hand);
        }

        restoreSneakState.run();
        future.complete(null);
    }

    private Optional<BlockPos> findNearestReachableBlock(Minecraft client, List<BlockSelection> selections) {
        if (client == null || client.player == null || selections == null || selections.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> localReachable = findNearestReachableBlockInInteractRange(client, selections);
        if (localReachable.isPresent()) {
            return localReachable;
        }
        List<BlockPos> candidates = owner.findBlocksWithinRange(client, selections, Node.PARAMETER_SEARCH_RADIUS);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Vec3 eyePos = client.player.getEyePosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            List<BlockHitResult> hits = buildInteractionHitCandidates(client, candidate);
            if (hits.isEmpty()) {
                continue;
            }
            double distance = hits.stream()
                .mapToDouble(hit -> eyePos.distanceToSqr(hit.getLocation()))
                .min()
                .orElse(Double.MAX_VALUE);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findNearestReachableBlockInInteractRange(Minecraft client, List<BlockSelection> selections) {
        if (client == null || client.player == null || client.level == null || selections == null || selections.isEmpty()) {
            return Optional.empty();
        }
        double reachSquared = Node.getBlockInteractionReachSquared(client);
        int radius = (int) Math.ceil(Math.sqrt(reachSquared));
        BlockPos playerPos = client.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.level.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    boolean matches = false;
                    for (BlockSelection selection : selections) {
                        if (selection != null && selection.matches(state)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                    BlockPos candidate = mutable.immutable();
                    List<BlockHitResult> hits = buildInteractionHitCandidates(client, candidate);
                    if (hits.isEmpty()) {
                        continue;
                    }
                    double distance = hits.stream()
                        .mapToDouble(hit -> client.player.getEyePosition().distanceToSqr(hit.getLocation()))
                        .min()
                        .orElse(Double.MAX_VALUE);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private boolean hasReachableInteractionCandidate(Minecraft client, BlockPos targetPos) {
        if (client == null || targetPos == null) {
            return false;
        }
        return !buildInteractionHitCandidates(client, targetPos).isEmpty();
    }

    private void orientPlayerTowards(Minecraft client, Vec3 target) {
        if (client == null || client.player == null || target == null) {
            return;
        }
        Vec3 eyes = client.player.getEyePosition();
        Vec3 delta = target.subtract(eyes);
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }

        float yaw = (float) (Mth.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        float clampedPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        client.player.setYRot(yaw);
        client.player.setXRot(clampedPitch);
        client.player.setYHeadRot(yaw);
        client.player.setYBodyRot(yaw);
    }

    private List<BlockHitResult> buildInteractionHitCandidates(Minecraft client, BlockPos targetPos) {
        if (client == null || client.player == null || client.level == null || targetPos == null) {
            return Collections.emptyList();
        }

        List<BlockHitResult> results = new ArrayList<>();
        Optional<BlockHitResult> currentHit = owner.getCurrentBlockHitResult();
        currentHit
            .filter(hit -> targetPos.equals(hit.getBlockPos()))
            .filter(hit -> isHitWithinReach(client, hit.getLocation()))
            .ifPresent(results::add);

        BlockState state = client.level.getBlockState(targetPos);
        VoxelShape shape = state.getShape(client.level, targetPos, CollisionContext.of(client.player));
        double minX = 0.0D;
        double minY = 0.0D;
        double minZ = 0.0D;
        double maxX = 1.0D;
        double maxY = 1.0D;
        double maxZ = 1.0D;
        if (!shape.isEmpty()) {
            net.minecraft.world.phys.AABB box = shape.bounds();
            minX = box.minX;
            minY = box.minY;
            minZ = box.minZ;
            maxX = box.maxX;
            maxY = box.maxY;
            maxZ = box.maxZ;
        }
        final double shapeMinX = minX;
        final double shapeMinY = minY;
        final double shapeMinZ = minZ;
        final double shapeMaxX = maxX;
        final double shapeMaxY = maxY;
        final double shapeMaxZ = maxZ;

        Vec3 eyePos = client.player.getEyePosition();
        double reachSquared = Node.getBlockInteractionReachSquared(client);
        List<Direction> orderedFaces = new ArrayList<>(List.of(Direction.values()));
        orderedFaces.sort(Comparator.comparingDouble(face -> {
            Vec3 center = faceCenter(targetPos, shapeMinX, shapeMinY, shapeMinZ, shapeMaxX, shapeMaxY, shapeMaxZ, face);
            return eyePos.distanceToSqr(center);
        }));

        for (Direction face : orderedFaces) {
            for (Vec3 hitPos : faceSamplePoints(targetPos, shapeMinX, shapeMinY, shapeMinZ, shapeMaxX, shapeMaxY, shapeMaxZ, face)) {
                if (eyePos.distanceToSqr(hitPos) > reachSquared) {
                    continue;
                }
                addUniqueHitResult(results, new BlockHitResult(hitPos, face, targetPos, false));
            }
        }

        return results;
    }

    private boolean isHitWithinReach(Minecraft client, Vec3 hitPos) {
        if (client == null || client.player == null || hitPos == null) {
            return false;
        }
        return client.player.getEyePosition().distanceToSqr(hitPos) <= Node.getBlockInteractionReachSquared(client);
    }

    private void addUniqueHitResult(List<BlockHitResult> results, BlockHitResult candidate) {
        if (results == null || candidate == null) {
            return;
        }
        for (BlockHitResult existing : results) {
            if (existing == null) {
                continue;
            }
            if (existing.getBlockPos().equals(candidate.getBlockPos())
                && existing.getDirection() == candidate.getDirection()
                && existing.getLocation().distanceToSqr(candidate.getLocation()) < 1.0E-6D) {
                return;
            }
        }
        results.add(candidate);
    }

    private Vec3 faceCenter(BlockPos pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Direction face) {
        return switch (face) {
            case DOWN -> new Vec3(pos.getX() + 0.5D * (minX + maxX), pos.getY() + minY, pos.getZ() + 0.5D * (minZ + maxZ));
            case UP -> new Vec3(pos.getX() + 0.5D * (minX + maxX), pos.getY() + maxY, pos.getZ() + 0.5D * (minZ + maxZ));
            case NORTH -> new Vec3(pos.getX() + 0.5D * (minX + maxX), pos.getY() + 0.5D * (minY + maxY), pos.getZ() + minZ);
            case SOUTH -> new Vec3(pos.getX() + 0.5D * (minX + maxX), pos.getY() + 0.5D * (minY + maxY), pos.getZ() + maxZ);
            case WEST -> new Vec3(pos.getX() + minX, pos.getY() + 0.5D * (minY + maxY), pos.getZ() + 0.5D * (minZ + maxZ));
            case EAST -> new Vec3(pos.getX() + maxX, pos.getY() + 0.5D * (minY + maxY), pos.getZ() + 0.5D * (minZ + maxZ));
        };
    }

    private List<Vec3> faceSamplePoints(BlockPos pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Direction face) {
        double inset = 0.001D;
        double centerX = 0.5D * (minX + maxX);
        double centerY = 0.5D * (minY + maxY);
        double centerZ = 0.5D * (minZ + maxZ);
        double quarterX = minX + (maxX - minX) * 0.25D;
        double quarterY = minY + (maxY - minY) * 0.25D;
        double quarterZ = minZ + (maxZ - minZ) * 0.25D;
        double threeQuarterX = minX + (maxX - minX) * 0.75D;
        double threeQuarterY = minY + (maxY - minY) * 0.75D;
        double threeQuarterZ = minZ + (maxZ - minZ) * 0.75D;

        List<Vec3> points = new ArrayList<>();
        switch (face) {
            case UP -> {
                points.add(new Vec3(pos.getX() + centerX, pos.getY() + maxY - inset, pos.getZ() + centerZ));
                points.add(new Vec3(pos.getX() + quarterX, pos.getY() + maxY - inset, pos.getZ() + quarterZ));
                points.add(new Vec3(pos.getX() + threeQuarterX, pos.getY() + maxY - inset, pos.getZ() + threeQuarterZ));
                points.add(new Vec3(pos.getX() + quarterX, pos.getY() + maxY - inset, pos.getZ() + threeQuarterZ));
                points.add(new Vec3(pos.getX() + threeQuarterX, pos.getY() + maxY - inset, pos.getZ() + quarterZ));
            }
            case DOWN -> points.add(new Vec3(pos.getX() + centerX, pos.getY() + minY + inset, pos.getZ() + centerZ));
            case NORTH -> {
                points.add(new Vec3(pos.getX() + centerX, pos.getY() + centerY, pos.getZ() + minZ + inset));
                points.add(new Vec3(pos.getX() + quarterX, pos.getY() + quarterY, pos.getZ() + minZ + inset));
                points.add(new Vec3(pos.getX() + threeQuarterX, pos.getY() + threeQuarterY, pos.getZ() + minZ + inset));
            }
            case SOUTH -> {
                points.add(new Vec3(pos.getX() + centerX, pos.getY() + centerY, pos.getZ() + maxZ - inset));
                points.add(new Vec3(pos.getX() + quarterX, pos.getY() + quarterY, pos.getZ() + maxZ - inset));
                points.add(new Vec3(pos.getX() + threeQuarterX, pos.getY() + threeQuarterY, pos.getZ() + maxZ - inset));
            }
            case WEST -> {
                points.add(new Vec3(pos.getX() + minX + inset, pos.getY() + centerY, pos.getZ() + centerZ));
                points.add(new Vec3(pos.getX() + minX + inset, pos.getY() + quarterY, pos.getZ() + quarterZ));
                points.add(new Vec3(pos.getX() + minX + inset, pos.getY() + threeQuarterY, pos.getZ() + threeQuarterZ));
            }
            case EAST -> {
                points.add(new Vec3(pos.getX() + maxX - inset, pos.getY() + centerY, pos.getZ() + centerZ));
                points.add(new Vec3(pos.getX() + maxX - inset, pos.getY() + quarterY, pos.getZ() + quarterZ));
                points.add(new Vec3(pos.getX() + maxX - inset, pos.getY() + threeQuarterY, pos.getZ() + threeQuarterZ));
            }
        }
        return points;
    }
    void executeBreakCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.of(Node.ParameterUsage.POSITION), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        Node parameterNode = owner.getAttachedParameter(0);
        if (parameterNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.breakRequiresBlockOrCoordinate"));
            return;
        }

        BlockPos targetPos = null;
        Direction breakFace = null;
        if (owner.runtimeState().runtimeParameterData != null) {
            if (owner.runtimeState().runtimeParameterData.targetBlockPos != null) {
                targetPos = owner.runtimeState().runtimeParameterData.targetBlockPos;
            } else if (owner.runtimeState().runtimeParameterData.targetVector != null) {
                Vec3 vec = owner.runtimeState().runtimeParameterData.targetVector;
                targetPos = new BlockPos(Mth.floor(vec.x), Mth.floor(vec.y), Mth.floor(vec.z));
            }
        }

        if (targetPos == null && owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
            List<BlockSelection> selections = owner.resolveBlocksFromParameter(parameterNode);
            if (selections.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noBlockSelectedForBreak"));
                return;
            }
            Optional<BlockHitResult> currentHit = owner.getCurrentBlockHitResult();
            if (currentHit.isPresent()) {
                BlockHitResult blockHit = currentHit.get();
                BlockPos hitPos = blockHit.getBlockPos();
                if (hitPos != null) {
                    BlockState hitState = client.level.getBlockState(hitPos);
                    boolean matches = false;
                    for (BlockSelection selection : selections) {
                        if (selection.matches(hitState)) {
                            matches = true;
                            break;
                        }
                    }
                    if (matches) {
                        targetPos = hitPos;
                        breakFace = blockHit.getDirection();
                    }
                }
            }
            if (targetPos == null) {
                Optional<BlockPos> nearest = owner.findNearestBlock(client, selections, Node.getBlockInteractionReach(client));
                if (nearest.isPresent()) {
                    targetPos = nearest.get();
                }
            }
        }

        if (targetPos == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noMatchingBlockInReachForBreak"));
            return;
        }

        if (owner.runtimeState().runtimeParameterData == null) {
            owner.runtimeState().runtimeParameterData = new RuntimeParameterData();
        }
        owner.runtimeState().runtimeParameterData.targetBlockPos = targetPos;

        BlockState state = client.level.getBlockState(targetPos);
        if (state.isAir()) {
            NodeExecutionCompletion.complete(future);
            return;
        }
        BreakTargeting targeting = resolveBreakTargeting(client, targetPos);
        if (targeting == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.targetBlockOutOfReach"));
            return;
        }
        breakFace = targeting.face();
        owner.runtimeState().runtimeParameterData.targetVector = targeting.hitPos();

        float delta = state.getDestroyProgress(client.player, client.level, targetPos);
        if (delta <= 0.0F) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.blockCannotBeBroken"));
            return;
        }
        int ticksToBreak = Math.max(1, (int) Math.ceil(1.0F / delta));

        Direction finalBreakFace = breakFace;
        BlockPos finalTargetPos = targetPos;
        new Thread(() -> {
            try {
                owner.runOnClientThread(client, () -> {
                    owner.orientPlayerTowardsRuntimeTarget(client, owner.runtimeState().runtimeParameterData);
                    if (client.gameMode != null) {
                        client.gameMode.startDestroyBlock(finalTargetPos, finalBreakFace);
                    }
                    client.player.swing(InteractionHand.MAIN_HAND);
                });

                for (int i = 0; i < ticksToBreak; i++) {
                    Thread.sleep(50L);
                    Boolean isAir = owner.supplyFromClient(client,
                        () -> client.level == null || client.level.getBlockState(finalTargetPos).isAir());
                    if (Boolean.TRUE.equals(isAir)) {
                        break;
                    }
                    owner.runOnClientThread(client, () -> {
                        if (client.gameMode != null) {
                            client.gameMode.continueDestroyBlock(finalTargetPos, finalBreakFace);
                        }
                    });
                }

                owner.runOnClientThread(client, () -> {
                    if (client.player != null && client.player.connection != null) {
                        client.player.connection.send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            finalTargetPos,
                            finalBreakFace
                        ));
                    }
                });
                NodeExecutionCompletion.complete(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
        }, "Pathmind-Break").start();
    }

    private BreakTargeting resolveBreakTargeting(Minecraft client, BlockPos target) {
        if (client == null || client.player == null || client.level == null || target == null) {
            return null;
        }
        BlockState targetState = client.level.getBlockState(target);
        if (targetState == null || targetState.isAir()) {
            return null;
        }
        double reachSq = getBlockInteractionReachSquared(client);
        for (Vec3 hitPos : getBreakAimPoints(client.level, targetState, target, preferredBreakFaces(client.player.getEyePosition(), target))) {
            BlockHitResult hit = raycastToBreakTarget(client.level, client.player, target, hitPos, reachSq);
            if (hit != null) {
                return new BreakTargeting(hit.getDirection(), hit.getLocation());
            }
        }
        return null;
    }

    private double getBlockInteractionReachSquared(Minecraft client) {
        return Node.getBlockInteractionReachSquared(client);
    }

    private List<Direction> preferredBreakFaces(Vec3 eyePos, BlockPos target) {
        if (eyePos == null || target == null) {
            return List.of(Direction.UP);
        }
        Vec3 center = Vec3.atCenterOf(target);
        Vec3 delta = center.subtract(eyePos);
        Direction primary = Direction.getApproximateNearest(delta.x, delta.y, delta.z).getOpposite();
        List<Direction> faces = new ArrayList<>(6);
        faces.add(primary);
        for (Direction face : Direction.values()) {
            if (!faces.contains(face)) {
                faces.add(face);
            }
        }
        return faces;
    }

    private List<Vec3> getBreakAimPoints(ClientLevel world, BlockState targetState, BlockPos target, List<Direction> preferredFaces) {
        if (world == null || targetState == null || target == null) {
            return List.of();
        }
        VoxelShape shape = targetState.getShape(world, target);
        if (shape == null || shape.isEmpty()) {
            shape = targetState.getCollisionShape(world, target);
        }
        List<AABB> boxes = shape == null || shape.isEmpty()
            ? List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D))
            : shape.toAabbs();
        if (boxes.isEmpty()) {
            boxes = List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }
        boxes = boxes.stream()
            .sorted(Comparator.comparingDouble(AABB::getSize).reversed())
            .toList();

        List<Vec3> points = new ArrayList<>(boxes.size() * Math.max(1, preferredFaces.size()) + boxes.size() + 1);
        for (Direction face : preferredFaces) {
            for (AABB box : boxes) {
                points.add(getBreakFaceAimPoint(target, box, face));
            }
        }
        for (AABB box : boxes) {
            points.add(worldBoxCenter(target, box));
        }
        points.add(Vec3.atCenterOf(target));
        return points;
    }

    private Vec3 getBreakFaceAimPoint(BlockPos target, AABB localBox, Direction face) {
        Vec3 center = worldBoxCenter(target, localBox);
        if (target == null || localBox == null || face == null) {
            return center;
        }
        double minX = target.getX() + localBox.minX;
        double minY = target.getY() + localBox.minY;
        double minZ = target.getZ() + localBox.minZ;
        double maxX = target.getX() + localBox.maxX;
        double maxY = target.getY() + localBox.maxY;
        double maxZ = target.getZ() + localBox.maxZ;
        double x = center.x;
        double y = center.y;
        double z = center.z;
        double epsilon;
        switch (face) {
            case EAST -> {
                epsilon = inwardEpsilon(minX, maxX);
                x = maxX - epsilon;
            }
            case WEST -> {
                epsilon = inwardEpsilon(minX, maxX);
                x = minX + epsilon;
            }
            case UP -> {
                epsilon = inwardEpsilon(minY, maxY);
                y = maxY - epsilon;
            }
            case DOWN -> {
                epsilon = inwardEpsilon(minY, maxY);
                y = minY + epsilon;
            }
            case SOUTH -> {
                epsilon = inwardEpsilon(minZ, maxZ);
                z = maxZ - epsilon;
            }
            case NORTH -> {
                epsilon = inwardEpsilon(minZ, maxZ);
                z = minZ + epsilon;
            }
        }
        return new Vec3(x, y, z);
    }

    private Vec3 worldBoxCenter(BlockPos target, AABB localBox) {
        if (target == null || localBox == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
            target.getX() + (localBox.minX + localBox.maxX) * 0.5D,
            target.getY() + (localBox.minY + localBox.maxY) * 0.5D,
            target.getZ() + (localBox.minZ + localBox.maxZ) * 0.5D
        );
    }

    private double inwardEpsilon(double min, double max) {
        return Math.min(BREAK_AIM_EPSILON, Math.max(0.0D, (max - min) * 0.25D));
    }

    private BlockHitResult raycastToBreakTarget(
        ClientLevel world,
        Entity player,
        BlockPos target,
        Vec3 hitPos,
        double reachSq
    ) {
        if (world == null || player == null || target == null || hitPos == null) {
            return null;
        }
        Vec3 eyePos = player.getEyePosition();
        if (eyePos.distanceToSqr(hitPos) > reachSq) {
            return null;
        }
        BlockHitResult outlineHit = world.clip(new ClipContext(
            eyePos,
            hitPos,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));
        if (outlineHit == null || outlineHit.getType() != HitResult.Type.BLOCK || !target.equals(outlineHit.getBlockPos())) {
            return null;
        }
        Vec3 outlineHitPos = outlineHit.getLocation();
        if (outlineHitPos == null || eyePos.distanceToSqr(outlineHitPos) > reachSq) {
            return null;
        }
        BlockHitResult collisionHit = world.clip(new ClipContext(
            eyePos,
            outlineHitPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        if (collisionHit != null && collisionHit.getType() == HitResult.Type.BLOCK && !target.equals(collisionHit.getBlockPos())) {
            return null;
        }
        return outlineHit;
    }

    private record BreakTargeting(Direction face, Vec3 hitPos) {
    }
    void executeTradeCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        owner.ensureVillagerTradeNumberParameter();

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null) {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
            return;
        }

        // Check if a merchant screen is open
        net.minecraft.client.gui.screens.Screen currentScreen = client.screen;
        if (!(currentScreen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen)) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noVillagerTradingScreen"));
            return;
        }

        net.minecraft.client.gui.screens.inventory.MerchantScreen merchantScreen =
            (net.minecraft.client.gui.screens.inventory.MerchantScreen) currentScreen;

        // Get the screen handler from merchant screen
        net.minecraft.world.inventory.MerchantMenu screenHandler = merchantScreen.getMenu();
        if (screenHandler == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.merchantScreenHandlerUnavailable"));
            return;
        }

        // Get the trade offers
        net.minecraft.world.item.trading.MerchantOffers tradeOffers = screenHandler.getOffers();
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noVillagerTrades"));
            return;
        }
        int selectedTradeNumber = owner.getConfiguredVillagerTradeNumber();
        int tradeIndex = selectedTradeNumber - 1;
        if (tradeIndex < 0 || tradeIndex >= tradeOffers.size() || tradeOffers.get(tradeIndex) == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.tradeUnavailable", selectedTradeNumber));
            return;
        }
        net.minecraft.world.item.trading.MerchantOffer selectedOffer = tradeOffers.get(tradeIndex);
        if (selectedOffer.isOutOfStock()) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.tradeOutOfStock", selectedTradeNumber));
            return;
        }
        if (!canAffordTrade(client.player, screenHandler, selectedOffer)) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.tradeNotEnoughItems", selectedTradeNumber));
            return;
        }
        List<Integer> preferredTradeIndexes = Collections.singletonList(tradeIndex);

        int tradesToExecute = owner.getConfiguredVillagerTradeCount();

        new Thread(() -> {
            try {
                int remainingTrades = tradesToExecute;
                while (remainingTrades > 0) {
                    boolean tradedThisPass = false;
                    boolean anyMatchStillAvailable = false;
                    for (Integer preferredTradeIndex : preferredTradeIndexes) {
                        if (preferredTradeIndex == null || preferredTradeIndex < 0 || preferredTradeIndex >= tradeOffers.size()) {
                            continue;
                        }
                        net.minecraft.world.item.trading.MerchantOffer offer = tradeOffers.get(preferredTradeIndex);
                        if (offer == null) {
                            continue;
                        }
                        if (!offer.isOutOfStock()) {
                            anyMatchStillAvailable = true;
                        }
                        int executableTrades = getMaxExecutableTradeCount(client.player, screenHandler, offer);
                        if (executableTrades <= 0) {
                            continue;
                        }

                        int batchSize = Math.min(remainingTrades, executableTrades);
                        selectMerchantTrade(client, screenHandler, preferredTradeIndex);
                        Thread.sleep(60);

                        int completedInBatch = 0;
                        for (int i = 0; i < batchSize; i++) {
                            if (offer.isOutOfStock() || !canAffordTrade(client.player, screenHandler, offer)) {
                                break;
                            }
                            if (!quickMoveMerchantTradeResult(client, screenHandler)) {
                                break;
                            }
                            completedInBatch++;
                            remainingTrades--;
                            tradedThisPass = true;
                            if (remainingTrades <= 0) {
                                break;
                            }
                            Thread.sleep(70);
                        }

                        if (completedInBatch > 0 && remainingTrades > 0) {
                            Thread.sleep(120);
                        }
                        if (remainingTrades <= 0) {
                            break;
                        }
                    }

                    if (!tradedThisPass) {
                        if (!anyMatchStillAvailable) {
                            owner.sendNodeErrorMessage(client, tr("pathmind.error.tradesOutOfStock"));
                        } else {
                            owner.sendNodeErrorMessage(client, tr("pathmind.error.tradesNotEnoughItems"));
                        }
                        break;
                    }
                }

                NodeExecutionCompletion.complete(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
        }, "Pathmind-Trade").start();
    }

    private void selectMerchantTrade(net.minecraft.client.Minecraft client,
                                     net.minecraft.world.inventory.MerchantMenu screenHandler,
                                     int tradeIndex) throws InterruptedException {
        owner.runOnClientThread(client, () -> {
            screenHandler.setSelectionHint(tradeIndex);
            screenHandler.tryMoveItems(tradeIndex);
            if (client.player != null && client.player.connection != null) {
                client.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(tradeIndex)
                );
            }
        });
    }

    private boolean quickMoveMerchantTradeResult(net.minecraft.client.Minecraft client,
                                                 net.minecraft.world.inventory.MerchantMenu screenHandler) throws InterruptedException {
        if (client == null || client.player == null || client.gameMode == null || screenHandler == null) {
            return false;
        }
        final boolean[] moved = {false};
        owner.runOnClientThread(client, () -> {
            final int outputSlot = 2;
            net.minecraft.world.inventory.Slot output = screenHandler.getSlot(outputSlot);
            if (output == null) {
                return;
            }
            net.minecraft.world.item.ItemStack outputStack = output.getItem();
            if (outputStack == null || outputStack.isEmpty()) {
                return;
            }
            client.gameMode.handleContainerInput(
                screenHandler.containerId,
                outputSlot,
                0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,
                client.player
            );
            moved[0] = true;
        });
        return moved[0];
    }

    private int getMaxExecutableTradeCount(net.minecraft.world.entity.player.Player player,
                                           net.minecraft.world.inventory.MerchantMenu screenHandler,
                                           net.minecraft.world.item.trading.MerchantOffer offer) {
        if (player == null || screenHandler == null || offer == null || offer.isOutOfStock()) {
            return 0;
        }

        int maxTrades = Integer.MAX_VALUE;
        net.minecraft.world.item.ItemStack firstBuyItem = getRequiredFirstBuyItem(offer);
        if (!firstBuyItem.isEmpty()) {
            int required = Math.max(1, firstBuyItem.getCount());
            int available = countAvailableForTrade(player.getInventory(), screenHandler, firstBuyItem);
            maxTrades = Math.min(maxTrades, available / required);
        }

        net.minecraft.world.item.ItemStack secondBuyItem = getRequiredSecondBuyItem(offer);
        if (!secondBuyItem.isEmpty()) {
            int required = Math.max(1, secondBuyItem.getCount());
            int available = countAvailableForTrade(player.getInventory(), screenHandler, secondBuyItem);
            maxTrades = Math.min(maxTrades, available / required);
        }

        maxTrades = Math.min(maxTrades, Math.max(0, offer.getMaxUses() - offer.getUses()));
        return maxTrades == Integer.MAX_VALUE ? 0 : Math.max(0, maxTrades);
    }

    boolean canAffordTrade(net.minecraft.world.entity.player.Player player,
                           net.minecraft.world.inventory.MerchantMenu screenHandler,
                           net.minecraft.world.item.trading.MerchantOffer offer) {
        if (player == null || offer == null || screenHandler == null) {
            return false;
        }

        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();

        net.minecraft.world.item.ItemStack firstBuyItem = getRequiredFirstBuyItem(offer);
        if (!firstBuyItem.isEmpty()) {
            int required = firstBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, firstBuyItem);
            if (available < required) {
                return false;
            }
        }

        net.minecraft.world.item.ItemStack secondBuyItem = getRequiredSecondBuyItem(offer);
        if (!secondBuyItem.isEmpty()) {
            int required = secondBuyItem.getCount();
            int available = countAvailableForTrade(inventory, screenHandler, secondBuyItem);
            if (available < required) {
                return false;
            }
        }

        return true;
    }

    private static net.minecraft.world.item.ItemStack getRequiredFirstBuyItem(net.minecraft.world.item.trading.MerchantOffer offer) {
        return offer == null ? net.minecraft.world.item.ItemStack.EMPTY : offer.getCostA();
    }

    private static net.minecraft.world.item.ItemStack getRequiredSecondBuyItem(net.minecraft.world.item.trading.MerchantOffer offer) {
        return offer == null ? net.minecraft.world.item.ItemStack.EMPTY : offer.getCostB();
    }

    static int getRequiredFirstBuyCountForTests(net.minecraft.world.item.trading.MerchantOffer offer) {
        if (offer == null) {
            return 0;
        }
        return resolveRequiredTradeCount(
            getRequiredFirstBuyItem(offer).getCount(),
            offer.getItemCostA().itemStack().getCount()
        );
    }

    static int getRequiredSecondBuyCountForTests(net.minecraft.world.item.trading.MerchantOffer offer) {
        if (offer == null) {
            return 0;
        }
        java.util.Optional<net.minecraft.world.item.trading.ItemCost> secondBuyItem = offer.getItemCostB();
        int originalCount = secondBuyItem.map(item -> item.itemStack().getCount()).orElse(0);
        return resolveRequiredTradeCount(getRequiredSecondBuyItem(offer).getCount(), originalCount);
    }

    static int resolveRequiredTradeCountForTests(int displayedCount, int originalCount) {
        return resolveRequiredTradeCount(displayedCount, originalCount);
    }

    private static int resolveRequiredTradeCount(int displayedCount, int originalCount) {
        return displayedCount > 0 ? displayedCount : Math.max(0, originalCount);
    }

    private int countAvailableForTrade(net.minecraft.world.entity.player.Inventory inventory,
                                       net.minecraft.world.inventory.MerchantMenu screenHandler,
                                       net.minecraft.world.item.ItemStack requiredStack) {
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
            if (net.minecraft.world.item.ItemStack.isSameItem(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        // Include items already moved into merchant input slots (0 and 1).
        for (int slotIndex = 0; slotIndex <= 1; slotIndex++) {
            net.minecraft.world.item.ItemStack stack = screenHandler.getSlot(slotIndex).getItem();
            if (net.minecraft.world.item.ItemStack.isSameItem(stack, requiredStack)) {
                available += stack.getCount();
            }
        }

        net.minecraft.world.item.ItemStack cursorStack = screenHandler.getCarried();
        if (net.minecraft.world.item.ItemStack.isSameItem(cursorStack, requiredStack)) {
            available += cursorStack.getCount();
        }

        return available;
    }
    void executeSwingCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);
        boolean holdDurationEnabled = owner.isAmountInputEnabled();
        double durationSeconds = holdDurationEnabled
            ? Math.max(0.0, owner.getDoubleParameter("Duration", 0.0))
            : 0.0;
        int legacyCount = Math.max(1, owner.getIntParameter("Count", 1));
        double legacyIntervalSeconds = Math.max(0.0, owner.getDoubleParameter("IntervalSeconds", 0.0));

        new Thread(() -> {
            boolean releaseAttackKey = false;
            try {
                if (holdDurationEnabled && durationSeconds > 0.0) {
                    if (hand == InteractionHand.MAIN_HAND) {
                        long durationMs = (long) Math.ceil(durationSeconds * 1000.0);
                        long deadline = System.currentTimeMillis() + durationMs;
                        owner.runOnClientThread(client, () -> {
                            syncSelectedHotbarSlot(client);
                            performMainHandAttack(client);
                            if (client.options != null && client.options.keyAttack != null) {
                                client.options.keyAttack.setDown(true);
                            }
                        });
                        releaseAttackKey = true;
                        while (System.currentTimeMillis() < deadline) {
                            if (owner.shouldAbortForRepeatUntilGuard()) {
                                break;
                            }
                            long remainingMs = deadline - System.currentTimeMillis();
                            Thread.sleep(Math.min(Node.CONTROL_POLL_INTERVAL_MS, Math.max(1L, remainingMs)));
                        }
                    } else {
                        long durationMs = (long) Math.ceil(durationSeconds * 1000.0);
                        long deadline = System.currentTimeMillis() + durationMs;
                        boolean swung = false;
                        while (!swung || System.currentTimeMillis() < deadline) {
                            owner.runOnClientThread(client, () -> {
                                client.player.swing(hand);
                            });
                            swung = true;
                            if (owner.shouldAbortForRepeatUntilGuard()) {
                                break;
                            }
                            long remainingMs = deadline - System.currentTimeMillis();
                            if (remainingMs <= 0L) {
                                break;
                            }
                            Thread.sleep(Math.min(50L, remainingMs));
                        }
                    }
                } else {
                    for (int i = 0; i < legacyCount; i++) {
                        owner.runOnClientThread(client, () -> {
                            if (hand == InteractionHand.MAIN_HAND) {
                                syncSelectedHotbarSlot(client);
                                performMainHandAttack(client);
                            } else {
                                client.player.swing(hand);
                            }
                        });

                        if (legacyIntervalSeconds > 0.0 && i < legacyCount - 1) {
                            Thread.sleep((long) (legacyIntervalSeconds * 1000));
                        }
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } finally {
                if (releaseAttackKey) {
                    try {
                        owner.runOnClientThread(client, () -> {
                            if (client.options != null && client.options.keyAttack != null) {
                                client.options.keyAttack.setDown(false);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "Pathmind-Swing").start();
    }

    static Method resolveDoAttackMethod() {
        try {
            return net.minecraft.client.Minecraft.class.getMethod("doAttack");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static void syncSelectedHotbarSlot(Minecraft client) {
        HotbarSlotSynchronizer.syncSelectedHotbarSlot(client);
    }

    static void performMainHandAttack(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        InputConstants.Key attackKey = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        KeyMapping.click(attackKey);
        try {
            if (DO_ATTACK_METHOD != null) {
                DO_ATTACK_METHOD.invoke(client);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the direct attack logic below.
        }
        if (client.gameMode != null) {
            HitResult target = client.hitResult;
            if (target instanceof EntityHitResult entityHit) {
                client.gameMode.attack(client.player, entityHit.getEntity());
                return;
            }
        }
        client.player.swing(InteractionHand.MAIN_HAND);
    }
    void executeEquipArmorCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        Inventory inventory = client.player.getInventory();
        int sourceSlot = owner.clampInventorySlot(inventory, owner.getIntParameter("SourceSlot", 0));
        EquipmentSlot equipmentSlot = parseEquipmentSlot(owner.getParameter("ArmorSlot"), EquipmentSlot.HEAD);
        
        ItemStack sourceStack = inventory.getItem(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack current = client.player.getItemBySlot(equipmentSlot);
        inventory.setItem(sourceSlot, current);
        client.player.setItemSlot(equipmentSlot, sourceStack);
        inventory.setChanged();
        client.player.inventoryMenu.broadcastChanges();
        future.complete(null);
    }
    
    void executeEquipHandCommand(CompletableFuture<Void> future) {
        if (owner.preprocessAttachedParameter(EnumSet.noneOf(Node.ParameterUsage.class), future) == Node.ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        Inventory inventory = client.player.getInventory();
        int sourceSlot = owner.clampInventorySlot(inventory, owner.getIntParameter("SourceSlot", 0));
        InteractionHand hand = owner.resolveHand(owner.getParameter("Hand"), InteractionHand.MAIN_HAND);
        
        ItemStack sourceStack = inventory.getItem(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack handStack = client.player.getItemInHand(hand);
        client.player.setItemInHand(hand, sourceStack);
        inventory.setItem(sourceSlot, handStack);
        inventory.setChanged();
        client.player.inventoryMenu.broadcastChanges();
        future.complete(null);
    }

    private EquipmentSlot parseEquipmentSlot(NodeParameter parameter, EquipmentSlot defaultSlot) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter.getStringValue().trim().toLowerCase(java.util.Locale.ROOT);
        switch (value) {
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            default:
                return defaultSlot;
        }
    }
}
