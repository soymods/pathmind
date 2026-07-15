package com.pathmind.nodes;

/**
 * Enum representing different types of nodes in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each type has specific properties and behaviors.
 */
public enum NodeType {
    // Event nodes
    START,
    START_CHAIN,
    EVENT_FUNCTION,
    EVENT_CALL,
    ROUTINE_ENTRY,
    ROUTINE_CALL,

    // Variable nodes
    VARIABLE,
    SET_VARIABLE,
    CHANGE_VARIABLE,
    CREATE_LIST,
    ADD_TO_LIST,
    REMOVE_FIRST_FROM_LIST,
    REMOVE_LAST_FROM_LIST,
    REMOVE_LIST_ITEM,
    REMOVE_FROM_LIST,
    LIST_ITEM,
    LIST_LENGTH,

    // Operator nodes
    OPERATOR_EQUALS,
    OPERATOR_NOT,
    OPERATOR_BOOLEAN_NOT,
    OPERATOR_BOOLEAN_OR,
    OPERATOR_BOOLEAN_AND,
    OPERATOR_BOOLEAN_XOR,
    OPERATOR_GREATER,
    OPERATOR_LESS,
    OPERATOR_MOD,
    OPERATOR_RANDOM,

    // Navigation Commands
    GOTO,
    TRAVEL,
    GOAL,
    PATH,
    INVERT,
    COME,
    SURFACE,

    // Resource collection and Building Commands
    COLLECT,
    BUILD,
    TUNNEL,
    FARM,
    PLACE,
    CRAFT,

    // Exploration Commands
    EXPLORE,
    FOLLOW,

    // Control flow Commands
    CONTROL_REPEAT,
    CONTROL_REPEAT_UNTIL,
    CONTROL_WAIT_UNTIL,
    CONTROL_FOREVER,
    CONTROL_IF,
    CONTROL_IF_ELSE,
    CONTROL_FORK,
    CONTROL_JOIN_ANY,
    CONTROL_JOIN_ALL,

    // Player movement commands
    LOOK,
    WALK,
    JUMP,
    PRESS_KEY,
    CRAWL,
    CROUCH,
    SPRINT,
    FLY,
    STOP,

    // Player combat commands
    SWING,

    // Player interaction commands
    USE,
    INTERACT,
    BREAK,
    PLACE_HAND,
    TRADE,

    // GUI Commands
    HOTBAR,
    DROP_ITEM,
    DROP_SLOT,
    CLICK_SLOT,
    CLICK_SCREEN,
    MOVE_ITEM,
    OPEN_INVENTORY,
    CLOSE_GUI,
    WRITE_BOOK,
    WRITE_SIGN,
    UI_UTILS,

    // Equipment Commands
    EQUIP_ARMOR,
    EQUIP_HAND,

    // Sensor commands
    SENSOR_TOUCHING_BLOCK,
    SENSOR_TOUCHING_ENTITY,
    SENSOR_AT_COORDINATES,
    SENSOR_POSITION_OF,
    SENSOR_DISTANCE_BETWEEN,
    SENSOR_TARGETED_BLOCK,
    SENSOR_TARGETED_ENTITY,
    SENSOR_LOOK_DIRECTION,
    SENSOR_CURRENT_HAND,
    SENSOR_TARGETED_BLOCK_FACE,
    SENSOR_GUI_FILLED,
    SENSOR_CURRENT_GUI,
    SENSOR_IS_DAYTIME,
    SENSOR_IS_RAINING,
    SENSOR_HEALTH_BELOW,
    SENSOR_HUNGER_BELOW,
    SENSOR_ITEM_IN_INVENTORY,
    SENSOR_ITEM_IN_SLOT,
    SENSOR_SLOT_ITEM_COUNT,
    SENSOR_FIND_TRADE,
    SENSOR_VILLAGER_TRADE,
    SENSOR_IN_STOCK,
    SENSOR_IS_SWIMMING,
    SENSOR_IS_IN_LAVA,
    SENSOR_IS_UNDERWATER,
    SENSOR_IS_ON_GROUND,
    SENSOR_IS_FALLING,
    SENSOR_IS_RENDERED,
    SENSOR_IS_VISIBLE,
    SENSOR_KEY_PRESSED,
    SENSOR_CHAT_MESSAGE,
    SENSOR_JOINED_SERVER,
    SENSOR_FABRIC_EVENT,
    SENSOR_ATTRIBUTE_DETECTION,

    // Utility Commands
    RUN_PRESET,
    CUSTOM_NODE,
    WAIT,
    STICKY_NOTE,
    MESSAGE,
    TEMPLATE,
    STOP_CHAIN,
    STOP_ALL,

    // Parameter nodes
    PARAM_COORDINATE,
    PARAM_BLOCK,
    PARAM_ITEM,
    PARAM_VILLAGER_TRADE,
    PARAM_ENTITY,
    PARAM_PLAYER,
    PARAM_WAYPOINT,
    PARAM_SCHEMATIC,
    PARAM_INVENTORY_SLOT,
    PARAM_MESSAGE,
    PARAM_DURATION,
    PARAM_AMOUNT,
    PARAM_BOOLEAN,
    PARAM_HAND,
    PARAM_GUI,
    PARAM_KEY,
    PARAM_MOUSE_BUTTON,
    PARAM_RANGE,
    PARAM_DISTANCE,
    PARAM_DIRECTION,
    PARAM_BLOCK_FACE,
    PARAM_ROTATION,
    PARAM_PLACE_TARGET,
    PARAM_CLOSEST,
    ROUTINE_INPUT;


    public String getDisplayName() {
        return NodeCatalog.displayName(this);
    }

    public int getColor() {
        return NodeCatalog.graphColor(this);
    }

    public String getDescription() {
        return NodeCatalog.description(this);
    }

    public boolean isInputNode() {
        return this == START;
    }

    public boolean isOutputNode() {
        return false;
    }

    public boolean isDraggableFromSidebar() {
        return NodeCatalog.isDraggableFromSidebar(this);
    }
    
    /**
     * Get the category this node belongs to for sidebar organization
     */
    public NodeCategory getCategory() {
        return NodeCatalog.category(this);
    }
    
    /**
     * Check if this node type requires parameters
     */
    public boolean hasParameters() {
        return NodeCatalog.hasParameters(this);
    }

    public boolean requiresBaritone() {
        return NodeCatalog.requiresBaritone(this);
    }

    public boolean requiresUiUtils() {
        return NodeCatalog.requiresUiUtils(this);
    }
}
