package com.pathmind.nodes;

import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.GameProfileCompatibilityBridge;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class PlayerParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_PLAYER)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Player", "Name"))
            .runtimeBehavior(PlayerParameterDefinition::resolvePositionTarget)
            .listEntryBehavior(PlayerParameterDefinition::resolveListEntry)
            .gotoFallbackTargetBehavior(PlayerParameterDefinition::resolveGotoFallbackTarget)
            .build();
    }

    static NodeBehaviorDefinition listItemDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.LIST_ITEM)
            .gotoFallbackTargetBehavior(PlayerParameterDefinition::resolveListItemGotoFallbackTarget)
            .build();
    }

    private static Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }
        String playerName = Node.getParameterString(parameterNode, "Player");
        Optional<AbstractClientPlayer> player = findPlayer(client, playerName);
        if (player.isEmpty()) {
            owner.sendParameterSearchFailure(NodeBehaviorDefinitionSupport.playerSearchFailureMessage(owner, playerName), future);
            return Optional.empty();
        }
        String resolvedName = GameProfileCompatibilityBridge.getName(player.get().getGameProfile());
        if (data != null) {
            data.targetPlayerName = resolvedName != null ? resolvedName : playerName;
            data.targetEntity = player.get();
            data.targetBlockPos = player.get().blockPosition();
        }
        Vec3 playerPos = EntityCompatibilityBridge.getPos(player.get());
        if (playerPos == null) {
            playerPos = Vec3.atCenterOf(player.get().blockPosition());
        }
        return Optional.of(playerPos);
    }

    private static Node.ListValueEntry resolveListEntry(Node owner, Node parameterNode, Minecraft client) {
        String playerName = Node.getParameterString(parameterNode, "Player");
        if (Node.isSelfPlayerValue(playerName)) {
            return new Node.ListValueEntry(NodeType.PARAM_PLAYER, client.player.getStringUUID());
        }
        Optional<AbstractClientPlayer> player = findPlayer(client, playerName);
        return player.map(match -> new Node.ListValueEntry(NodeType.PARAM_PLAYER, match.getStringUUID())).orElse(null);
    }

    private static BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, Minecraft client,
                                                      CompletableFuture<Void> future) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        String playerName = Node.getParameterString(parameterNode, "Player");
        if (Node.isSelfPlayerValue(playerName)) {
            future.complete(null);
            return null;
        }
        Optional<AbstractClientPlayer> match = findPlayer(client, playerName);
        if (match.isEmpty()) {
            owner.sendNodeErrorMessage(client, NodeBehaviorDefinitionSupport.playerSearchFailureMessage(owner, playerName));
            future.complete(null);
            return null;
        }

        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        if (data != null) {
            data.targetBlockPos = match.get().blockPosition();
            data.targetEntity = match.get();
        }
        return match.get().blockPosition();
    }

    private static BlockPos resolveListItemGotoFallbackTarget(Node owner, Node parameterNode, Minecraft client,
                                                              CompletableFuture<Void> future) {
        RuntimeParameterData data = owner.getRuntimeState().runtimeParameterData;
        Entity target = owner.resolveListItemEntity(parameterNode, data, future);
        if (target == null) {
            return null;
        }
        if (data != null) {
            data.targetBlockPos = target.blockPosition();
            data.targetEntity = target;
        }
        return target.blockPosition();
    }

    private static Optional<AbstractClientPlayer> findPlayer(Minecraft client, String playerName) {
        if (Node.isAnyPlayerValue(playerName)) {
            return Node.findNearestPlayer(client, client.player);
        }
        if (Node.isSelfPlayerValue(playerName)) {
            return Optional.of(client.player);
        }
        return client.level.players().stream()
            .filter(player -> playerName.equalsIgnoreCase(GameProfileCompatibilityBridge.getName(player.getGameProfile())))
            .findFirst();
    }

    private PlayerParameterDefinition() {
    }
}
