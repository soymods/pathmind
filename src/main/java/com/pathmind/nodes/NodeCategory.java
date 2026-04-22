package com.pathmind.nodes;

import net.minecraft.text.Text;

/**
 * Enum representing different categories of nodes for sidebar organization.
 */
public enum NodeCategory {
    FLOW("pathmind.node.category.flow", 0xFFE91E63, "pathmind.node.category.flow.desc", "★"),
    CONTROL("pathmind.node.category.control", 0xFFFFC107, "pathmind.node.category.control.desc", "🧠"),
    WORLD("pathmind.node.category.world", 0xFF00BCD4, "pathmind.node.category.world.desc", "⇄"),
    PLAYER("pathmind.node.category.player", 0xFF7E57C2, "pathmind.node.category.player.desc", "✋"),
    INTERFACE("pathmind.node.category.interface", 0xFF607D8B, "pathmind.node.category.interface.desc", "🖥"),
    DATA("pathmind.node.category.data", 0xFFFF9800, "pathmind.node.category.data.desc", "Σ"),
    SENSORS("pathmind.node.category.sensors", 0xFF64B5F6, "pathmind.node.category.sensors.desc", "📡"),
    PARAMETERS("pathmind.node.category.parameters", 0xFF8BC34A, "pathmind.node.category.parameters.desc", "🧩"),
    CUSTOM("pathmind.node.category.custom", 0xFF26A69A, "pathmind.node.category.custom.desc", "◈");

    private final String translationKey;
    private final int color;
    private final String descriptionKey;
    private final String icon;

    NodeCategory(String translationKey, int color, String descriptionKey, String icon) {
        this.translationKey = translationKey;
        this.color = color;
        this.descriptionKey = descriptionKey;
        this.icon = icon;
    }

    public String getDisplayName() {
        return Text.translatable(translationKey).getString();
    }

    public int getColor() {
        return color;
    }

    public String getDescription() {
        return Text.translatable(descriptionKey).getString();
    }

    public String getIcon() {
        return icon;
    }
}
