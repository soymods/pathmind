package com.pathmind.nodes;

import net.minecraft.text.Text;

/**
 * Enum representing different categories of nodes for sidebar organization.
 */
public enum NodeCategory {
    EVENTS("pathmind.node.category.events", 0xFFE91E63, "pathmind.node.category.events.desc", "★"),
    LOGIC("pathmind.node.category.logic", 0xFFFFC107, "pathmind.node.category.logic.desc", "🧠"),
    MOVEMENT("pathmind.node.category.movement", 0xFF00BCD4, "pathmind.node.category.movement.desc", "⇄"),
    INTERACTION("pathmind.node.category.interaction", 0xFF7E57C2, "pathmind.node.category.interaction.desc", "✋"),
    GUI("pathmind.node.category.gui", 0xFF8D6E63, "pathmind.node.category.gui.desc", "🖥"),
    SENSORS("pathmind.node.category.sensors", 0xFF64B5F6, "pathmind.node.category.sensors.desc", "📡"),
    OPERATORS("pathmind.node.category.operators", 0xFF00C853, "pathmind.node.category.operators.desc", "="),
    VARIABLES("pathmind.node.category.variables", 0xFFFF9800, "pathmind.node.category.variables.desc", "V"),
    PARAMETERS("pathmind.node.category.parameters", 0xFF8BC34A, "pathmind.node.category.parameters.desc", "🧩"),
    UTILITY("pathmind.node.category.utility", 0xFF9E9E9E, "pathmind.node.category.utility.desc", "⚙");

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
