package com.pathmind.nodes;

import com.mojang.blaze3d.platform.InputConstants;
import com.pathmind.util.InputCompatibilityBridge;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class NodeBasicSensorEvaluator {
    private final Node owner;

    NodeBasicSensorEvaluator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateHealthBelow() {
        double amount = Mth.clamp(owner.getDoubleParameter("Amount", 10.0), 0.0, 40.0);
        Node amountParameter = owner.getAttachedParameterOfType(
            NodeType.PARAM_AMOUNT,
            NodeType.OPERATOR_RANDOM,
            NodeType.OPERATOR_MOD
        );
        if (amountParameter != null) {
            amount = Mth.clamp(Node.parseNodeDouble(amountParameter, "Amount", amount), 0.0, 40.0);
        }
        return isHealthBelow(amount);
    }

    boolean evaluateHungerBelow() {
        int amount = Mth.clamp(owner.getIntParameter("Amount", 10), 0, 20);
        Node amountParameter = owner.getAttachedParameterOfType(
            NodeType.PARAM_AMOUNT,
            NodeType.OPERATOR_RANDOM,
            NodeType.OPERATOR_MOD
        );
        if (amountParameter != null) {
            double parsed = Node.parseNodeDouble(amountParameter, "Amount", amount);
            amount = Mth.clamp((int) Math.round(parsed), 0, 20);
        }
        return isHungerBelow(amount);
    }

    boolean evaluateKeyPressed() {
        String key = owner.getStringParameter("Key", "space");
        Node parameterNode = owner.resolveSensorParameterNode(owner.getAttachedParameter(), 0);
        if (parameterNode != null) {
            if (!owner.providesTrait(parameterNode, NodeValueTrait.KEY)) {
                owner.sendIncompatibleParameterMessage(parameterNode);
                return false;
            }
            String parameterKey = Node.getParameterString(parameterNode, "Key");
            if (parameterKey != null && !parameterKey.isEmpty()) {
                key = parameterKey;
            }
        }
        return isKeyPressed(key);
    }

    boolean isDaytime() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return false;
        }
        long time = client.level.getOverworldClockTime() % 24000L;
        return time < 12000L;
    }

    boolean isRaining() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            return false;
        }
        return client.level.isRaining() || client.level.isRainingAt(client.player.blockPosition());
    }

    boolean isKeyPressed(String keyName) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        if (!owner.isKeyPressedActivatesInGuis() && client.gui.screen() != null) {
            return false;
        }
        Integer keyCode = resolveKeyCode(keyName);
        if (keyCode == null) {
            return false;
        }
        return InputCompatibilityBridge.isKeyPressed(client, keyCode);
    }

    Integer resolveKeyCode(String keyName) {
        if (keyName == null) {
            return null;
        }
        String trimmed = keyName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
        }
        Integer glfwCode = resolveGlfwKeyCode(trimmed);
        if (glfwCode != null) {
            return glfwCode;
        }
        InputConstants.Key key = InputConstants.getKey(trimmed);
        int code = key.getValue();
        if (code != GLFW.GLFW_KEY_UNKNOWN) {
            return code;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT).replace(" ", "_");
        InputConstants.Key normalizedKey = InputConstants.getKey("key.keyboard." + normalized);
        int normalizedCode = normalizedKey.getValue();
        if (normalizedCode != GLFW.GLFW_KEY_UNKNOWN) {
            return normalizedCode;
        }
        return null;
    }

    Integer resolveMouseButtonCode(String buttonName) {
        if (buttonName == null) {
            return null;
        }
        String trimmed = buttonName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
        }
        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "GLFW_MOUSE_BUTTON_LEFT", "MOUSE_LEFT", "LEFT" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "GLFW_MOUSE_BUTTON_RIGHT", "MOUSE_RIGHT", "RIGHT" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "GLFW_MOUSE_BUTTON_MIDDLE", "MOUSE_MIDDLE", "MIDDLE" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            case "GLFW_MOUSE_BUTTON_4", "MOUSE_4", "BUTTON_4" -> GLFW.GLFW_MOUSE_BUTTON_4;
            case "GLFW_MOUSE_BUTTON_5", "MOUSE_5", "BUTTON_5" -> GLFW.GLFW_MOUSE_BUTTON_5;
            case "GLFW_MOUSE_BUTTON_6", "MOUSE_6", "BUTTON_6" -> GLFW.GLFW_MOUSE_BUTTON_6;
            case "GLFW_MOUSE_BUTTON_7", "MOUSE_7", "BUTTON_7" -> GLFW.GLFW_MOUSE_BUTTON_7;
            case "GLFW_MOUSE_BUTTON_8", "MOUSE_8", "BUTTON_8" -> GLFW.GLFW_MOUSE_BUTTON_8;
            default -> null;
        };
    }

    private Integer resolveGlfwKeyCode(String keyName) {
        String normalized = keyName.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
        if (!normalized.startsWith("GLFW_KEY_")) {
            normalized = "GLFW_KEY_" + normalized;
        }
        try {
            Field field = GLFW.class.getField(normalized);
            if (field.getType() == int.class) {
                return field.getInt(null);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    boolean isHealthBelow(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHealth() < amount;
    }

    boolean isHungerBelow(int amount) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getFoodData().getFoodLevel() < amount;
    }
}
