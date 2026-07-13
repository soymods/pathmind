package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.util.ChatMessageTracker;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.ServerJoinTracker;
import net.minecraft.client.MinecraftClient;

final class NodeEventSensorEvaluator {
    private final Node owner;

    NodeEventSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateChatMessage() {
        MinecraftClient client = MinecraftClient.getInstance();
        Node playerNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        Node messageNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(1), 1);
        if (playerNode == null || messageNode == null) {
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresUserAndMessageParameter", owner.getType().getDisplayName()));
            }
            return false;
        }
        if (!owner.providesTrait(playerNode, NodeValueTrait.PLAYER)) {
            owner.sendIncompatibleParameterMessage(playerNode);
            return false;
        }
        if (!owner.providesTrait(messageNode, NodeValueTrait.MESSAGE)) {
            owner.sendIncompatibleParameterMessage(messageNode);
            return false;
        }
        String playerName = Node.getParameterString(playerNode, "Player");
        String messageText = Node.getParameterString(messageNode, "Text");
        if (messageText == null || messageText.isEmpty()) {
            messageText = Node.getParameterString(messageNode, "Message");
        }
        boolean anyPlayer = Node.isAnyPlayerValue(playerName);
        if (!anyPlayer && Node.isSelfPlayerValue(playerName) && client != null && client.player != null) {
            playerName = GameProfileCompatibilityBridge.getName(client.player.getGameProfile());
        }
        boolean anyMessage = Node.isAnyMessageValue(messageText);
        boolean useAmount = owner.isAmountInputEnabled();
        double seconds = useAmount
            ? Math.max(0.0, owner.getDoubleParameter("Amount", 10.0))
            : ChatMessageTracker.getMaxRetentionSeconds();
        return ChatMessageTracker.hasRecentMessage(playerName, messageText, seconds, anyPlayer, anyMessage);
    }

    boolean evaluateJoinedServer() {
        MinecraftClient client = MinecraftClient.getInstance();
        Node playerNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(0), 0);
        if (playerNode == null) {
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.requiresUserParameter", owner.getType().getDisplayName()));
            }
            return false;
        }
        if (!owner.providesTrait(playerNode, NodeValueTrait.PLAYER)) {
            owner.sendIncompatibleParameterMessage(playerNode);
            return false;
        }
        String playerName = Node.getParameterString(playerNode, "Player");
        boolean anyPlayer = Node.isAnyPlayerValue(playerName);
        if (!anyPlayer && Node.isSelfPlayerValue(playerName) && client != null && client.player != null) {
            playerName = GameProfileCompatibilityBridge.getName(client.player.getGameProfile());
        }
        return ServerJoinTracker.hasRecentJoin(playerName, ServerJoinTracker.getRetentionSeconds(), anyPlayer);
    }

    boolean evaluateFabricEvent() {
        String eventName = Node.getParameterString(owner, "Event");
        if (eventName == null || eventName.trim().isEmpty()) {
            return false;
        }
        double seconds = FabricEventTracker.getMaxRetentionSeconds();
        String trimmed = eventName.trim();
        if ("Any".equalsIgnoreCase(trimmed)) {
            return FabricEventTracker.hasAnyRecentEvent(seconds);
        }
        return FabricEventTracker.hasRecentEvent(trimmed, seconds);
    }
}
