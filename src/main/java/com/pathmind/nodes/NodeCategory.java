package com.pathmind.nodes;

/**
 * Enum representing different categories of nodes for sidebar organization.
 */
public enum NodeCategory {
    EVENTS("Events", 0xFFE91E63, "Event entry points and triggers", "★"),
    LOGIC("Logic", 0xFFFFC107, "Flow control and condition checks", "🧠"),
    MOVEMENT("Movement", 0xFF00BCD4, "Pathfinding and player movement actions", "⇄"),
    INTERACTION("Interaction", 0xFF7E57C2, "Combat and interaction commands", "✋"),
    GUI("GUI", 0xFF8D6E63, "Screen and interface management", "🖥"),
    SENSORS("Sensors", 0xFF64B5F6, "Environment and state checks", "📡"),
    OPERATORS("Operators", 0xFF00C853, "Logical operators and comparisons", "="),
    VARIABLES("Variables", 0xFFFF9800, "Runtime variable storage and assignment", "V"),
    PARAMETERS("Parameters", 0xFF8BC34A, "Reusable parameter data nodes", "🧩"),
    UTILITY("Utility", 0xFF9E9E9E, "Utility and messaging tools", "⚙");

    private final String displayName;
    private final int color;
    private final String description;
    private final String icon;

    NodeCategory(String displayName, int color, String description, String icon) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}
