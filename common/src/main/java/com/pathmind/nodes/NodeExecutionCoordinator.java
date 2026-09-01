package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NodeExecutionCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeExecutionCoordinator.class);

    private final Node owner;

    NodeExecutionCoordinator(Node owner) {
        this.owner = owner;
    }

    CompletableFuture<Void> execute(int executionId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Minecraft client = Minecraft.getInstance();

        if (owner.hasParameterSlot()) {
            int requiredSlotCount = owner.getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (owner.isParameterSlotRequired(i) && owner.getAttachedParameter(i) == null) {
                    String label = owner.getParameterSlotLabel(i);
                    NodeExecutionCompletion.fail(owner, client, future,
                        owner.getType().getDisplayName() + " requires a " + label.toLowerCase(Locale.ROOT) + " parameter before it can run.");
                    return future;
                }
            }
        }

        if (!owner.reportEmptyParametersForNode(owner, future)) {
            return future;
        }

        if (requiresInGameRuntime(owner.getType()) && (client == null || client.player == null || client.level == null)) {
            NodeExecutionCompletion.fail(owner, client, future,
                owner.getType().getDisplayName() + " requires an in-game world before it can run.");
            return future;
        }

        if (!requiresClientThreadExecution()) {
            try {
                ExecutionManager.getInstance().runWithExecutionContext(executionId,
                    () -> NodeCommandDispatcher.execute(owner, future));
            } catch (Exception e) {
                LOGGER.warn("Error executing node {}: {}", owner.getType(), e.getMessage(), e);
                NodeExecutionCompletion.completeExceptionally(future, e);
            }
            return future;
        }

        if (client != null) {
            client.execute(() -> {
                try {
                    ExecutionManager.getInstance().runWithExecutionContext(executionId,
                        () -> NodeCommandDispatcher.execute(owner, future));
                } catch (Exception e) {
                    LOGGER.warn("Error executing node {}: {}", owner.getType(), e.getMessage(), e);
                    NodeExecutionCompletion.completeExceptionally(future, e);
                }
            });
        } else {
            NodeExecutionCompletion.completeExceptionally(future, new RuntimeException("Minecraft client not available"));
        }

        return future;
    }

    static boolean requiresInGameRuntime(NodeType type) {
        if (type == null) {
            return false;
        }
        if (type.requiresBaritone()) {
            return true;
        }
        NodeCategory category = type.getCategory();
        if (category == NodeCategory.NAVIGATION || category == NodeCategory.WORLD || category == NodeCategory.PLAYER) {
            return true;
        }
        if (category == NodeCategory.SENSORS) {
            return type != NodeType.SENSOR_CHAT_MESSAGE
                && type != NodeType.SENSOR_JOINED_SERVER
                && type != NodeType.SENSOR_FABRIC_EVENT
                && type != NodeType.SENSOR_KEY_PRESSED;
        }
        if (category == NodeCategory.INTERFACE) {
            return type != NodeType.MESSAGE && type != NodeType.STICKY_NOTE;
        }
        if (category == NodeCategory.PARAMETERS) {
            return type == NodeType.PARAM_PLAYER
                || type == NodeType.PARAM_ENTITY
                || type == NodeType.PARAM_GUI
                || type == NodeType.PARAM_INVENTORY_SLOT
                || type == NodeType.PARAM_HAND
                || type == NodeType.PARAM_PLACE_TARGET
                || type == NodeType.PARAM_CLOSEST;
        }
        return false;
    }

    private boolean requiresClientThreadExecution() {
        return switch (owner.getType()) {
            case EVENT_CALL,
                EVENT_FUNCTION,
                START,
                ROUTINE_ENTRY,
                ROUTINE_CALL,
                ROUTINE_INPUT,
                SET_VARIABLE,
                CALCULATE,
                CONTROL_REPEAT,
                CONTROL_FOREVER,
                START_CHAIN,
                RUN_PRESET,
                TEMPLATE,
                STOP_CHAIN,
                STOP_ALL -> false;
            default -> true;
        };
    }
}
