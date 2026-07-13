package com.pathmind.nodes;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static metadata for a node type.
 *
 * <p>NodeType remains the stable persisted id, while this class owns metadata
 * that does not need to live directly on the enum.
 */
final class NodeTypeDefinition {
    private static final Map<NodeType, NodeCategory> CATEGORIES = new EnumMap<>(NodeType.class);
    private static final Set<NodeType> TYPES_WITH_PARAMETERS = EnumSet.noneOf(NodeType.class);
    private static final Set<NodeType> NON_DRAGGABLE_FROM_SIDEBAR = EnumSet.noneOf(NodeType.class);
    private static final Set<NodeType> BARITONE_TYPES = EnumSet.noneOf(NodeType.class);
    private static final Set<NodeType> UI_UTILS_TYPES = EnumSet.noneOf(NodeType.class);

    static {
        register(NodeCategory.FLOW,
            NodeType.START,
            NodeType.START_CHAIN,
            NodeType.EVENT_FUNCTION,
            NodeType.EVENT_CALL,
            NodeType.RUN_PRESET,
            NodeType.WAIT,
            NodeType.TEMPLATE,
            NodeType.STOP_CHAIN,
            NodeType.STOP_ALL);

        register(NodeCategory.CONTROL,
            NodeType.CONTROL_REPEAT,
            NodeType.CONTROL_REPEAT_UNTIL,
            NodeType.CONTROL_WAIT_UNTIL,
            NodeType.CONTROL_FOREVER,
            NodeType.CONTROL_IF,
            NodeType.CONTROL_IF_ELSE,
            NodeType.CONTROL_FORK,
            NodeType.CONTROL_JOIN_ANY,
            NodeType.CONTROL_JOIN_ALL);

        register(NodeCategory.SENSORS,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_POSITION_OF,
            NodeType.SENSOR_DISTANCE_BETWEEN,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_CURRENT_HAND,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_GUI_FILLED,
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK,
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE,
            NodeType.SENSOR_KEY_PRESSED,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_ATTRIBUTE_DETECTION);

        register(NodeCategory.NAVIGATION,
            NodeType.GOTO,
            NodeType.TRAVEL,
            NodeType.GOAL,
            NodeType.PATH,
            NodeType.INVERT,
            NodeType.COME,
            NodeType.SURFACE,
            NodeType.EXPLORE,
            NodeType.FOLLOW,
            NodeType.STOP);

        register(NodeCategory.WORLD,
            NodeType.COLLECT,
            NodeType.BUILD,
            NodeType.TUNNEL,
            NodeType.FARM,
            NodeType.PLACE,
            NodeType.CRAFT);

        register(NodeCategory.PLAYER,
            NodeType.LOOK,
            NodeType.WALK,
            NodeType.JUMP,
            NodeType.CRAWL,
            NodeType.CROUCH,
            NodeType.SPRINT,
            NodeType.FLY,
            NodeType.SWING,
            NodeType.USE,
            NodeType.INTERACT,
            NodeType.BREAK,
            NodeType.PLACE_HAND,
            NodeType.TRADE,
            NodeType.PRESS_KEY);

        register(NodeCategory.INTERFACE,
            NodeType.HOTBAR,
            NodeType.DROP_ITEM,
            NodeType.DROP_SLOT,
            NodeType.CLICK_SLOT,
            NodeType.CLICK_SCREEN,
            NodeType.MOVE_ITEM,
            NodeType.OPEN_INVENTORY,
            NodeType.CLOSE_GUI,
            NodeType.WRITE_BOOK,
            NodeType.WRITE_SIGN,
            NodeType.EQUIP_ARMOR,
            NodeType.EQUIP_HAND,
            NodeType.UI_UTILS,
            NodeType.MESSAGE,
            NodeType.STICKY_NOTE);

        register(NodeCategory.DATA,
            NodeType.VARIABLE,
            NodeType.SET_VARIABLE,
            NodeType.CHANGE_VARIABLE,
            NodeType.CREATE_LIST,
            NodeType.ADD_TO_LIST,
            NodeType.REMOVE_FIRST_FROM_LIST,
            NodeType.REMOVE_LAST_FROM_LIST,
            NodeType.REMOVE_LIST_ITEM,
            NodeType.REMOVE_FROM_LIST,
            NodeType.LIST_ITEM,
            NodeType.LIST_LENGTH,
            NodeType.OPERATOR_EQUALS,
            NodeType.OPERATOR_NOT,
            NodeType.OPERATOR_BOOLEAN_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.OPERATOR_GREATER,
            NodeType.OPERATOR_LESS,
            NodeType.OPERATOR_MOD,
            NodeType.OPERATOR_RANDOM);

        register(NodeCategory.CUSTOM,
            NodeType.CUSTOM_NODE);

        register(NodeCategory.PARAMETERS,
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_VILLAGER_TRADE,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC,
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_DURATION,
            NodeType.PARAM_AMOUNT,
            NodeType.PARAM_BOOLEAN,
            NodeType.PARAM_HAND,
            NodeType.PARAM_GUI,
            NodeType.PARAM_KEY,
            NodeType.PARAM_MOUSE_BUTTON,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_DISTANCE,
            NodeType.PARAM_DIRECTION,
            NodeType.PARAM_BLOCK_FACE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_PLACE_TARGET,
            NodeType.PARAM_CLOSEST,
            NodeType.PARAM_MESSAGE);

        add(TYPES_WITH_PARAMETERS,
            NodeType.EVENT_FUNCTION,
            NodeType.EVENT_CALL,
            NodeType.GOTO,
            NodeType.TRAVEL,
            NodeType.GOAL,
            NodeType.COLLECT,
            NodeType.PLACE,
            NodeType.CRAFT,
            NodeType.BUILD,
            NodeType.EXPLORE,
            NodeType.FOLLOW,
            NodeType.WAIT,
            NodeType.HOTBAR,
            NodeType.DROP_ITEM,
            NodeType.USE,
            NodeType.LOOK,
            NodeType.PRESS_KEY,
            NodeType.WALK,
            NodeType.INTERACT,
            NodeType.PLACE_HAND,
            NodeType.TRADE,
            NodeType.DROP_SLOT,
            NodeType.CLICK_SLOT,
            NodeType.MOVE_ITEM,
            NodeType.EQUIP_ARMOR,
            NodeType.EQUIP_HAND,
            NodeType.WRITE_BOOK,
            NodeType.UI_UTILS,
            NodeType.RUN_PRESET,
            NodeType.CONTROL_REPEAT,
            NodeType.CONTROL_REPEAT_UNTIL,
            NodeType.CONTROL_WAIT_UNTIL,
            NodeType.CONTROL_IF_ELSE,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_VILLAGER_TRADE,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC,
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_DURATION,
            NodeType.PARAM_AMOUNT,
            NodeType.PARAM_BOOLEAN,
            NodeType.PARAM_HAND,
            NodeType.PARAM_GUI,
            NodeType.PARAM_KEY,
            NodeType.PARAM_MOUSE_BUTTON,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_DISTANCE,
            NodeType.PARAM_DIRECTION,
            NodeType.PARAM_BLOCK_FACE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_PLACE_TARGET,
            NodeType.PARAM_CLOSEST,
            NodeType.PARAM_MESSAGE,
            NodeType.VARIABLE,
            NodeType.OPERATOR_RANDOM,
            NodeType.OPERATOR_MOD,
            NodeType.OPERATOR_BOOLEAN_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.CREATE_LIST,
            NodeType.ADD_TO_LIST,
            NodeType.REMOVE_FIRST_FROM_LIST,
            NodeType.REMOVE_LAST_FROM_LIST,
            NodeType.REMOVE_LIST_ITEM,
            NodeType.REMOVE_FROM_LIST,
            NodeType.LIST_ITEM,
            NodeType.LIST_LENGTH);

        add(NON_DRAGGABLE_FROM_SIDEBAR,
            NodeType.STOP,
            NodeType.PLACE_HAND,
            NodeType.RUN_PRESET,
            NodeType.TEMPLATE,
            NodeType.CUSTOM_NODE,
            NodeType.STICKY_NOTE,
            NodeType.PARAM_VILLAGER_TRADE,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.DROP_SLOT,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_PLACE_TARGET);

        add(BARITONE_TYPES,
            NodeType.GOTO,
            NodeType.GOAL,
            NodeType.PATH,
            NodeType.INVERT,
            NodeType.COME,
            NodeType.SURFACE,
            NodeType.COLLECT,
            NodeType.BUILD,
            NodeType.TUNNEL,
            NodeType.FARM,
            NodeType.EXPLORE,
            NodeType.FOLLOW,
            NodeType.STOP,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC);

        add(UI_UTILS_TYPES,
            NodeType.UI_UTILS);
    }

    private NodeTypeDefinition() {
    }

    static NodeCategory getCategory(NodeType type) {
        return CATEGORIES.getOrDefault(type, NodeCategory.INTERFACE);
    }

    static boolean hasExplicitCategory(NodeType type) {
        return CATEGORIES.containsKey(type);
    }

    static boolean hasParameters(NodeType type) {
        return TYPES_WITH_PARAMETERS.contains(type);
    }

    static boolean isDraggableFromSidebar(NodeType type) {
        return !NON_DRAGGABLE_FROM_SIDEBAR.contains(type);
    }

    static boolean requiresBaritone(NodeType type) {
        return BARITONE_TYPES.contains(type);
    }

    static boolean requiresUiUtils(NodeType type) {
        return UI_UTILS_TYPES.contains(type);
    }

    private static void register(NodeCategory category, NodeType... types) {
        for (NodeType type : types) {
            CATEGORIES.put(type, category);
        }
    }

    private static void add(Set<NodeType> set, NodeType... types) {
        for (NodeType type : types) {
            set.add(type);
        }
    }
}
