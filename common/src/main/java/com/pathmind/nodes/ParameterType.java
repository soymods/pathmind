package com.pathmind.nodes;

/**
 * Enum representing different types of parameters for nodes.
 */
public enum ParameterType {
    STRING("String"),
    INTEGER("Integer"),
    DOUBLE("Double"),
    BOOLEAN("Boolean"),
    COORDINATE("Coordinate"),
    BLOCK_TYPE("Block Type"),
    PLAYER_NAME("Player Name"),
    ENTITY_TYPE("Entity Type"),
    SCHEMATIC("Schematic"),
    WAYPOINT_TAG("Waypoint Tag"),
    WAYPOINT_NAME("Waypoint Name");

    private final String displayName;

    ParameterType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
