package com.pathmind.nodes;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central catalog for node metadata and sidebar placement.
 *
 * <p>NodeType remains the stable persisted id. This catalog owns the mutable
 * organization metadata so category, dependency, and sidebar layout changes are
 * made in one place instead of across UI renderers.
 */
public final class NodeCatalog {
    private static final Map<NodeType, NodeDefinition> DEFINITIONS = new EnumMap<>(NodeType.class);
    private static final Map<NodeType, EnumSet<NodeValueTrait>> PROVIDED_TRAITS = new EnumMap<>(NodeType.class);
    private static final Map<NodeType, ParameterSchema> PARAMETER_SCHEMAS = new EnumMap<>(NodeType.class);
    private static final List<SidebarGroupDefinition> SIDEBAR_GROUPS = new ArrayList<>();

    static {
        define(NodeCategory.FLOW,
            NodeType.START,
            NodeType.START_CHAIN,
            NodeType.EVENT_FUNCTION,
            NodeType.EVENT_CALL,
            NodeType.RUN_PRESET,
            NodeType.WAIT,
            NodeType.TEMPLATE,
            NodeType.STOP_CHAIN,
            NodeType.STOP_ALL);

        define(NodeCategory.CONTROL,
            NodeType.CONTROL_REPEAT,
            NodeType.CONTROL_REPEAT_UNTIL,
            NodeType.CONTROL_WAIT_UNTIL,
            NodeType.CONTROL_FOREVER,
            NodeType.CONTROL_IF,
            NodeType.CONTROL_IF_ELSE,
            NodeType.CONTROL_FORK,
            NodeType.CONTROL_JOIN_ANY,
            NodeType.CONTROL_JOIN_ALL);

        define(NodeCategory.SENSORS,
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
            NodeType.SENSOR_FIND_TRADE,
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

        define(NodeCategory.NAVIGATION,
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

        define(NodeCategory.WORLD,
            NodeType.COLLECT,
            NodeType.BUILD,
            NodeType.TUNNEL,
            NodeType.FARM,
            NodeType.PLACE,
            NodeType.CRAFT);

        define(NodeCategory.PLAYER,
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

        define(NodeCategory.INTERFACE,
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

        define(NodeCategory.DATA,
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

        define(NodeCategory.CUSTOM, NodeType.CUSTOM_NODE);

        define(NodeCategory.PARAMETERS,
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

        tag(NodeFlag.HAS_PARAMETERS,
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
            NodeType.CRAWL,
            NodeType.CROUCH,
            NodeType.SPRINT,
            NodeType.FLY,
            NodeType.INTERACT,
            NodeType.PLACE_HAND,
            NodeType.TRADE,
            NodeType.DROP_SLOT,
            NodeType.CLICK_SLOT,
            NodeType.MOVE_ITEM,
            NodeType.EQUIP_ARMOR,
            NodeType.EQUIP_HAND,
            NodeType.CLOSE_GUI,
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
            NodeType.SENSOR_FIND_TRADE,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.SENSOR_CURRENT_HAND,
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

        tag(NodeFlag.HIDDEN_FROM_SIDEBAR,
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

        tag(NodeFlag.REQUIRES_BARITONE,
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

        tag(NodeFlag.REQUIRES_UI_UTILS, NodeType.UI_UTILS);

        tag(NodeFlag.BOOLEAN_SENSOR,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK,
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE,
            NodeType.SENSOR_KEY_PRESSED,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_GUI_FILLED,
            NodeType.OPERATOR_EQUALS,
            NodeType.OPERATOR_NOT,
            NodeType.OPERATOR_BOOLEAN_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.OPERATOR_GREATER,
            NodeType.OPERATOR_LESS);

        tag(NodeFlag.PARAMETER_NODE,
            NodeType.SENSOR_POSITION_OF,
            NodeType.SENSOR_DISTANCE_BETWEEN,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_CURRENT_HAND,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_FIND_TRADE,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.VARIABLE,
            NodeType.OPERATOR_RANDOM,
            NodeType.OPERATOR_MOD,
            NodeType.LIST_ITEM,
            NodeType.LIST_LENGTH);

        tag(NodeFlag.SENSOR_WITHOUT_PARAMETER_SLOT,
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_CURRENT_HAND);

        tag(NodeFlag.SENSOR_REQUIRES_PARAMETER,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_DISTANCE_BETWEEN,
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_FIND_TRADE,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_GUI_FILLED);

        tag(NodeFlag.MINIMAL_PRESENTATION,
            NodeType.STOP_CHAIN,
            NodeType.STOP_ALL,
            NodeType.START_CHAIN,
            NodeType.RUN_PRESET,
            NodeType.CRAWL,
            NodeType.CROUCH,
            NodeType.SPRINT,
            NodeType.FLY,
            NodeType.JUMP,
            NodeType.CONTROL_FORK,
            NodeType.CONTROL_JOIN_ANY,
            NodeType.CONTROL_JOIN_ALL,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_CURRENT_HAND,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.OPERATOR_EQUALS,
            NodeType.OPERATOR_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.OPERATOR_GREATER,
            NodeType.OPERATOR_LESS,
            NodeType.OPEN_INVENTORY,
            NodeType.CLOSE_GUI);

        tag(NodeFlag.RENDER_INLINE_PARAMETERS,
            NodeType.UI_UTILS,
            NodeType.SENSOR_FABRIC_EVENT,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.TRADE,
            NodeType.REMOVE_LIST_ITEM);

        tag(NodeFlag.BOOLEAN_TOGGLE,
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_GUI_FILLED);

        tag(NodeFlag.POPUP_EDIT_BUTTON,
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_KEY,
            NodeType.PARAM_VILLAGER_TRADE);

        sidebar(NodeCategory.FLOW, "pathmind.sidebar.group.entryPoints",
            NodeType.START,
            NodeType.START_CHAIN,
            NodeType.EVENT_FUNCTION,
            NodeType.EVENT_CALL);
        sidebar(NodeCategory.FLOW, "pathmind.sidebar.group.timingStops",
            NodeType.WAIT,
            NodeType.STOP_CHAIN,
            NodeType.STOP_ALL);
        sidebar(NodeCategory.FLOW, "pathmind.sidebar.group.presets",
            NodeType.RUN_PRESET,
            NodeType.TEMPLATE);

        sidebar(NodeCategory.CONTROL, "pathmind.sidebar.group.branchingLoops",
            NodeType.CONTROL_IF,
            NodeType.CONTROL_IF_ELSE,
            NodeType.CONTROL_REPEAT,
            NodeType.CONTROL_REPEAT_UNTIL,
            NodeType.CONTROL_FOREVER);
        sidebar(NodeCategory.CONTROL, "pathmind.sidebar.group.parallel",
            NodeType.CONTROL_FORK,
            NodeType.CONTROL_JOIN_ANY,
            NodeType.CONTROL_JOIN_ALL);
        sidebar(NodeCategory.CONTROL, "pathmind.sidebar.group.conditionsWaiting",
            NodeType.CONTROL_WAIT_UNTIL);

        sidebar(NodeCategory.NAVIGATION, "pathmind.sidebar.group.navigation",
            NodeType.GOTO,
            NodeType.TRAVEL,
            NodeType.GOAL,
            NodeType.PATH,
            NodeType.INVERT,
            NodeType.COME,
            NodeType.SURFACE,
            NodeType.STOP);
        sidebar(NodeCategory.NAVIGATION, "pathmind.sidebar.group.exploration",
            NodeType.EXPLORE,
            NodeType.FOLLOW);
        fallbackSidebar(NodeCategory.PLAYER, NodeCategory.NAVIGATION, "pathmind.sidebar.group.navigation",
            NodeType.GOTO,
            NodeType.TRAVEL,
            NodeType.GOAL,
            NodeType.PATH,
            NodeType.INVERT,
            NodeType.COME,
            NodeType.SURFACE,
            NodeType.STOP);
        fallbackSidebar(NodeCategory.PLAYER, NodeCategory.NAVIGATION, "pathmind.sidebar.group.exploration",
            NodeType.EXPLORE,
            NodeType.FOLLOW);

        sidebar(NodeCategory.WORLD, "pathmind.sidebar.group.gathering",
            NodeType.COLLECT,
            NodeType.FARM,
            NodeType.TUNNEL);
        sidebar(NodeCategory.WORLD, "pathmind.sidebar.group.buildingCrafting",
            NodeType.BUILD,
            NodeType.PLACE,
            NodeType.CRAFT);

        sidebar(NodeCategory.PLAYER, "pathmind.sidebar.group.movement",
            NodeType.WALK,
            NodeType.JUMP,
            NodeType.CRAWL,
            NodeType.CROUCH,
            NodeType.SPRINT,
            NodeType.FLY);
        sidebar(NodeCategory.PLAYER, "pathmind.sidebar.group.viewInput",
            NodeType.LOOK,
            NodeType.PRESS_KEY);
        sidebar(NodeCategory.PLAYER, "pathmind.sidebar.group.interaction",
            NodeType.USE,
            NodeType.INTERACT,
            NodeType.BREAK,
            NodeType.PLACE_HAND);
        sidebar(NodeCategory.PLAYER, "pathmind.sidebar.group.combatTrading",
            NodeType.SWING,
            NodeType.TRADE);

        sidebar(NodeCategory.INTERFACE, "pathmind.sidebar.group.inventory",
            NodeType.HOTBAR,
            NodeType.MOVE_ITEM,
            NodeType.DROP_ITEM,
            NodeType.CLICK_SLOT,
            NodeType.OPEN_INVENTORY,
            NodeType.EQUIP_HAND,
            NodeType.EQUIP_ARMOR);
        sidebar(NodeCategory.INTERFACE, "pathmind.sidebar.group.screensUi",
            NodeType.CLICK_SCREEN,
            NodeType.CLOSE_GUI,
            NodeType.UI_UTILS);
        sidebar(NodeCategory.INTERFACE, "pathmind.sidebar.group.writingOutput",
            NodeType.MESSAGE,
            NodeType.WRITE_BOOK,
            NodeType.WRITE_SIGN);

        sidebar(NodeCategory.DATA, "pathmind.sidebar.group.variables",
            NodeType.VARIABLE,
            NodeType.SET_VARIABLE,
            NodeType.CHANGE_VARIABLE);
        sidebar(NodeCategory.DATA, "pathmind.sidebar.group.lists",
            NodeType.CREATE_LIST,
            NodeType.ADD_TO_LIST,
            NodeType.REMOVE_FIRST_FROM_LIST,
            NodeType.REMOVE_LAST_FROM_LIST,
            NodeType.REMOVE_LIST_ITEM,
            NodeType.REMOVE_FROM_LIST,
            NodeType.LIST_ITEM,
            NodeType.LIST_LENGTH);
        sidebar(NodeCategory.DATA, "pathmind.sidebar.group.comparisonBoolean",
            NodeType.OPERATOR_EQUALS,
            NodeType.OPERATOR_NOT,
            NodeType.OPERATOR_BOOLEAN_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.OPERATOR_GREATER,
            NodeType.OPERATOR_LESS);
        sidebar(NodeCategory.DATA, "pathmind.sidebar.group.mathRandom",
            NodeType.OPERATOR_MOD,
            NodeType.OPERATOR_RANDOM);

        sidebar(NodeCategory.PARAMETERS, "pathmind.sidebar.group.spatialData",
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_DIRECTION,
            NodeType.PARAM_BLOCK_FACE,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_DISTANCE,
            NodeType.PARAM_CLOSEST);
        sidebar(NodeCategory.PARAMETERS, "pathmind.sidebar.group.targetsObjects",
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC);
        sidebar(NodeCategory.PARAMETERS, "pathmind.sidebar.group.inventoryGui",
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_HAND,
            NodeType.PARAM_GUI);
        sidebar(NodeCategory.PARAMETERS, "pathmind.sidebar.group.inputText",
            NodeType.PARAM_KEY,
            NodeType.PARAM_MOUSE_BUTTON,
            NodeType.PARAM_MESSAGE);
        sidebar(NodeCategory.PARAMETERS, "pathmind.sidebar.group.utilityValues",
            NodeType.PARAM_DURATION,
            NodeType.PARAM_AMOUNT,
            NodeType.PARAM_BOOLEAN);

        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.playerState",
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_CURRENT_HAND);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.eventsInput",
            NodeType.SENSOR_KEY_PRESSED,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_FABRIC_EVENT);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.spatialTargeting",
            NodeType.SENSOR_POSITION_OF,
            NodeType.SENSOR_DISTANCE_BETWEEN,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_TOUCHING_BLOCK);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.blocksFacesVisibility",
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.inventoryItems",
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.SENSOR_GUI_FILLED);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.trading",
            NodeType.SENSOR_FIND_TRADE,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK);
        sidebar(NodeCategory.SENSORS, "pathmind.sidebar.group.worldWeather",
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING);

        provided(NodeType.PARAM_COORDINATE, NodeValueTrait.COORDINATE);
        provided(NodeType.PARAM_ROTATION, NodeValueTrait.ROTATION);
        provided(NodeType.PARAM_DIRECTION, NodeValueTrait.DIRECTION, NodeValueTrait.ROTATION);
        provided(NodeType.PARAM_BLOCK_FACE, NodeValueTrait.DIRECTION);
        provided(NodeType.PARAM_RANGE, NodeValueTrait.RANGE);
        provided(NodeType.PARAM_DISTANCE, NodeValueTrait.DISTANCE);
        provided(NodeType.PARAM_DURATION, NodeValueTrait.DURATION);
        provided(NodeType.PARAM_AMOUNT, NodeValueTrait.NUMBER);
        provided(NodeType.PARAM_BOOLEAN, NodeValueTrait.BOOLEAN);
        provided(NodeType.PARAM_HAND, NodeValueTrait.HAND);
        provided(NodeType.PARAM_GUI, NodeValueTrait.GUI);
        provided(NodeType.PARAM_KEY, NodeValueTrait.KEY);
        provided(NodeType.PARAM_MOUSE_BUTTON, NodeValueTrait.MOUSE_BUTTON);
        provided(NodeType.PARAM_MESSAGE, NodeValueTrait.MESSAGE);
        provided(NodeType.PARAM_BLOCK, NodeValueTrait.BLOCK);
        provided(NodeType.PARAM_ITEM, NodeValueTrait.ITEM);
        provided(NodeType.PARAM_ENTITY, NodeValueTrait.ENTITY);
        provided(NodeType.PARAM_PLAYER, NodeValueTrait.PLAYER);
        provided(NodeType.PARAM_INVENTORY_SLOT, NodeValueTrait.INVENTORY_SLOT);
        provided(NodeType.PARAM_VILLAGER_TRADE, NodeValueTrait.VILLAGER_TRADE);
        provided(NodeType.PARAM_WAYPOINT, NodeValueTrait.WAYPOINT, NodeValueTrait.COORDINATE);
        provided(NodeType.PARAM_SCHEMATIC, NodeValueTrait.SCHEMATIC, NodeValueTrait.COORDINATE);
        provided(NodeType.PARAM_PLACE_TARGET, NodeValueTrait.BLOCK, NodeValueTrait.COORDINATE);
        provided(NodeType.PARAM_CLOSEST, NodeValueTrait.RANGE, NodeValueTrait.COORDINATE);
        provided(NodeType.SENSOR_POSITION_OF, NodeValueTrait.COORDINATE);
        provided(NodeType.SENSOR_DISTANCE_BETWEEN, NodeValueTrait.DISTANCE);
        provided(NodeType.SENSOR_TARGETED_BLOCK, NodeValueTrait.BLOCK);
        provided(NodeType.SENSOR_TARGETED_ENTITY, NodeValueTrait.ENTITY);
        provided(NodeType.SENSOR_TARGETED_BLOCK_FACE, NodeValueTrait.DIRECTION);
        provided(NodeType.SENSOR_LOOK_DIRECTION, NodeValueTrait.ROTATION);
        provided(NodeType.SENSOR_CURRENT_HAND, NodeValueTrait.INVENTORY_SLOT);
        provided(NodeType.SENSOR_IS_ON_GROUND, NodeValueTrait.DISTANCE);
        provided(NodeType.OPERATOR_RANDOM, NodeValueTrait.NUMBER);
        provided(NodeType.OPERATOR_MOD, NodeValueTrait.NUMBER);
        provided(NodeType.CHANGE_VARIABLE, NodeValueTrait.NUMBER);
        provided(NodeType.OPERATOR_BOOLEAN_OR, NodeValueTrait.BOOLEAN);
        provided(NodeType.OPERATOR_BOOLEAN_AND, NodeValueTrait.BOOLEAN);
        provided(NodeType.OPERATOR_BOOLEAN_XOR, NodeValueTrait.BOOLEAN);
        provided(NodeType.SENSOR_SLOT_ITEM_COUNT, NodeValueTrait.NUMBER);
        provided(NodeType.SENSOR_FIND_TRADE, NodeValueTrait.NUMBER);
        provided(NodeType.LIST_ITEM,
            NodeValueTrait.LIST_ITEM,
            NodeValueTrait.ANY,
            NodeValueTrait.BOOLEAN,
            NodeValueTrait.NUMBER,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.DURATION,
            NodeValueTrait.RANGE,
            NodeValueTrait.DISTANCE,
            NodeValueTrait.INVENTORY_SLOT,
            NodeValueTrait.MESSAGE,
            NodeValueTrait.GUI,
            NodeValueTrait.KEY,
            NodeValueTrait.MOUSE_BUTTON,
            NodeValueTrait.HAND,
            NodeValueTrait.VILLAGER_TRADE,
            NodeValueTrait.WAYPOINT,
            NodeValueTrait.SCHEMATIC);
        provided(NodeType.LIST_LENGTH, NodeValueTrait.NUMBER);
        provided(NodeType.VARIABLE, NodeValueTrait.VARIABLE, NodeValueTrait.ANY);

        parameterHost(NodeType.WALK,
            slot("Direction", true,
                NodeValueTrait.DIRECTION,
                NodeValueTrait.ROTATION,
                NodeValueTrait.COORDINATE,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER,
                NodeValueTrait.LIST_ITEM),
            slot("Duration/Distance", true, NodeValueTrait.DURATION, NodeValueTrait.DISTANCE));
        parameterHost(NodeType.LOOK,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.NUMBER,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.LIST_ITEM);
        parameterHost(NodeType.BREAK, "Target", NodeValueTrait.BLOCK, NodeValueTrait.COORDINATE);
        parameterHost(NodeType.OPERATOR_EQUALS, slot("Left", true, NodeValueTrait.ANY), slot("Right", true, NodeValueTrait.ANY));
        parameterHost(NodeType.OPERATOR_NOT, slot("Left", true, NodeValueTrait.ANY), slot("Right", true, NodeValueTrait.ANY));
        parameterHost(NodeType.OPERATOR_MOD, slot("Value", true, NodeValueTrait.NUMBER), slot("Modulo", true, NodeValueTrait.NUMBER));
        parameterHost(NodeType.OPERATOR_GREATER, slot("Left", true, NodeValueTrait.NUMBER), slot("Right", true, NodeValueTrait.NUMBER));
        parameterHost(NodeType.OPERATOR_LESS, slot("Left", true, NodeValueTrait.NUMBER), slot("Right", true, NodeValueTrait.NUMBER));
        parameterHost(NodeType.OPERATOR_BOOLEAN_NOT, "Value", NodeValueTrait.BOOLEAN);
        parameterHost(NodeType.OPERATOR_BOOLEAN_OR, slot("Left", true, NodeValueTrait.ANY), slot("Right", true, NodeValueTrait.ANY));
        parameterHost(NodeType.OPERATOR_BOOLEAN_AND, slot("Left", true, NodeValueTrait.ANY), slot("Right", true, NodeValueTrait.ANY));
        parameterHost(NodeType.OPERATOR_BOOLEAN_XOR, slot("Left", true, NodeValueTrait.BOOLEAN), slot("Right", true, NodeValueTrait.BOOLEAN));
        parameterHost(NodeType.SENSOR_POSITION_OF, "Target",
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER);
        parameterHost(NodeType.SENSOR_DISTANCE_BETWEEN,
            slot("Target A", true,
                NodeValueTrait.COORDINATE,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER),
            slot("Target B", true,
                NodeValueTrait.COORDINATE,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER));
        parameterHost(NodeType.SENSOR_TOUCHING_BLOCK, NodeValueTrait.BLOCK);
        parameterHost(NodeType.SENSOR_TOUCHING_ENTITY, NodeValueTrait.ENTITY);
        parameterHost(NodeType.SENSOR_AT_COORDINATES, NodeValueTrait.COORDINATE);
        parameterHost(NodeType.SENSOR_ITEM_IN_INVENTORY, NodeValueTrait.ITEM, NodeValueTrait.NUMBER);
        parameterHost(NodeType.SENSOR_ITEM_IN_SLOT, slot("Item", true, NodeValueTrait.ITEM), slot("Selection", true, NodeValueTrait.INVENTORY_SLOT));
        parameterHost(NodeType.SENSOR_SLOT_ITEM_COUNT, "Slot", NodeValueTrait.INVENTORY_SLOT);
        parameterHost(NodeType.SENSOR_FIND_TRADE, "Item", NodeValueTrait.ITEM);
        parameterHost(NodeType.SENSOR_ATTRIBUTE_DETECTION, "Target", NodeValueTrait.ENTITY, NodeValueTrait.PLAYER, NodeValueTrait.ITEM);
        parameterHost(NodeType.SENSOR_VILLAGER_TRADE, "Villager Trade", NodeValueTrait.VILLAGER_TRADE);
        parameterHost(NodeType.SENSOR_IN_STOCK, "Villager Trade", NodeValueTrait.VILLAGER_TRADE);
        parameterHost(NodeType.SENSOR_CHAT_MESSAGE, slot("User", true, NodeValueTrait.PLAYER), slot("Message", true, NodeValueTrait.MESSAGE));
        parameterHost(NodeType.SENSOR_JOINED_SERVER, "User", NodeValueTrait.PLAYER);
        parameterHost(NodeType.SENSOR_GUI_FILLED, "GUI", NodeValueTrait.GUI);
        parameterHost(NodeType.SENSOR_KEY_PRESSED, NodeValueTrait.KEY);
        parameterHost(NodeType.SENSOR_IS_RENDERED, NodeValueTrait.BLOCK, NodeValueTrait.ITEM, NodeValueTrait.ENTITY, NodeValueTrait.PLAYER);
        parameterHost(NodeType.SENSOR_IS_VISIBLE, NodeValueTrait.BLOCK, NodeValueTrait.ITEM, NodeValueTrait.ENTITY, NodeValueTrait.PLAYER);
        parameterHost(NodeType.USE, NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.BLOCK);
        parameterHost(NodeType.INTERACT,
            NodeValueTrait.HAND,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM);
        parameterHost(NodeType.MOVE_ITEM,
            slot("Source", true, NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT),
            slot("Destination", true, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.GUI));
        parameterHost(NodeType.PLACE,
            slot("Source", false, NodeValueTrait.BLOCK, NodeValueTrait.INVENTORY_SLOT),
            slot("Position", true,
                NodeValueTrait.COORDINATE,
                NodeValueTrait.ROTATION,
                NodeValueTrait.DIRECTION,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER,
                NodeValueTrait.LIST_ITEM));
        parameterHost(NodeType.PLACE_HAND,
            slot("Source", true, NodeValueTrait.BLOCK, NodeValueTrait.INVENTORY_SLOT),
            slot("Position", true,
                NodeValueTrait.COORDINATE,
                NodeValueTrait.ROTATION,
                NodeValueTrait.DIRECTION,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER,
                NodeValueTrait.LIST_ITEM));
        parameterHost(NodeType.PRESS_KEY, "Button", NodeValueTrait.KEY, NodeValueTrait.MOUSE_BUTTON);
        parameterHost(NodeType.GOTO, movementTargetTraits());
        parameterHost(NodeType.TRAVEL, movementTargetTraits());
        parameterHost(NodeType.GOAL, movementTargetTraits());
        parameterHost(NodeType.PATH, movementTargetTraits());
        parameterHost(NodeType.EXPLORE, movementTargetTraits());
        parameterHost(NodeType.FOLLOW, movementTargetTraits());
        parameterHost(NodeType.COLLECT, NodeValueTrait.BLOCK, NodeValueTrait.NUMBER);
        parameterHost(NodeType.CRAFT, NodeValueTrait.ITEM, NodeValueTrait.NUMBER);
        parameterHost(NodeType.BUILD, "Position",
            NodeValueTrait.COORDINATE,
            NodeValueTrait.SCHEMATIC,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM);
        parameterHost(NodeType.FARM, NodeValueTrait.WAYPOINT, NodeValueTrait.RANGE);
        parameterHost(NodeType.HOTBAR, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.ITEM);
        parameterHost(NodeType.DROP_ITEM, "Target", NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.NUMBER);
        parameterHost(NodeType.DROP_SLOT, "Target", NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.NUMBER);
        parameterHost(NodeType.CLICK_SLOT, "Selection", NodeValueTrait.INVENTORY_SLOT);
        parameterHost(NodeType.EQUIP_ARMOR, NodeValueTrait.INVENTORY_SLOT);
        parameterHost(NodeType.EQUIP_HAND, NodeValueTrait.INVENTORY_SLOT);
        parameterHost(NodeType.SET_VARIABLE, slot("Variable", true, NodeValueTrait.VARIABLE), slot("Value", true, NodeValueTrait.ANY));
        parameterHost(NodeType.CHANGE_VARIABLE, "Variable", NodeValueTrait.VARIABLE);
        parameterHost(NodeType.ADD_TO_LIST, "Target", NodeValueTrait.ANY);
        parameterHost(NodeType.REMOVE_FROM_LIST, "Target", NodeValueTrait.ANY);
        parameterHost(NodeType.CREATE_LIST, "Target", NodeValueTrait.ANY);
        parameterHost(NodeType.TRADE, "Villager Trade", NodeValueTrait.NUMBER);
        parameterHost(NodeType.PARAM_BLOCK_FACE, "Target", NodeValueTrait.COORDINATE, NodeValueTrait.BLOCK);
    }

    private NodeCatalog() {
    }

    public static NodeDefinition definition(NodeType type) {
        return DEFINITIONS.get(type);
    }

    public static boolean hasDefinition(NodeType type) {
        return DEFINITIONS.containsKey(type);
    }

    public static String displayName(NodeType type) {
        NodeDefinition definition = definition(type);
        return Text.translatable(definition != null ? definition.nameKey() : nameKey(type)).getString();
    }

    public static String description(NodeType type) {
        NodeDefinition definition = definition(type);
        return Text.translatable(definition != null ? definition.descriptionKey() : descriptionKey(type)).getString();
    }

    public static int graphColor(NodeType type) {
        NodeDefinition definition = definition(type);
        if (definition == null) {
            return NodeCategory.INTERFACE.getColor();
        }
        if (usesExplicitGraphColor(type)) {
            return definition.baseColor();
        }
        return definition.category().getColor();
    }

    public static int graphColor(NodeType type, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        NodeDefinition definition = definition(type);
        if (definition == null) {
            return NodeCategory.INTERFACE.getColor();
        }
        if (usesExplicitGraphColor(type)) {
            return definition.baseColor();
        }
        NodePlacement placement = sidebarPlacement(type, baritoneAvailable, uiUtilsAvailable);
        return placement != null ? placement.displayCategory().getColor() : graphColor(type);
    }

    public static NodeCategory category(NodeType type) {
        NodeDefinition definition = definition(type);
        return definition != null ? definition.category() : NodeCategory.INTERFACE;
    }

    public static boolean hasParameters(NodeType type) {
        return hasFlag(type, NodeFlag.HAS_PARAMETERS);
    }

    public static boolean isDraggableFromSidebar(NodeType type) {
        return type != null && !hasFlag(type, NodeFlag.HIDDEN_FROM_SIDEBAR);
    }

    public static boolean requiresBaritone(NodeType type) {
        return hasFlag(type, NodeFlag.REQUIRES_BARITONE);
    }

    public static boolean requiresUiUtils(NodeType type) {
        return hasFlag(type, NodeFlag.REQUIRES_UI_UTILS);
    }

    public static boolean isBooleanSensor(NodeType type) {
        return hasFlag(type, NodeFlag.BOOLEAN_SENSOR);
    }

    public static boolean isParameterNode(NodeType type) {
        return category(type) == NodeCategory.PARAMETERS || hasFlag(type, NodeFlag.PARAMETER_NODE);
    }

    public static boolean isSensorWithoutParameterSlot(NodeType type) {
        return hasFlag(type, NodeFlag.SENSOR_WITHOUT_PARAMETER_SLOT);
    }

    public static boolean isSensorParameterRequired(NodeType type) {
        return hasFlag(type, NodeFlag.SENSOR_REQUIRES_PARAMETER);
    }

    public static boolean usesMinimalNodePresentation(NodeType type) {
        return hasFlag(type, NodeFlag.MINIMAL_PRESENTATION);
    }

    public static boolean shouldRenderInlineParameters(NodeType type) {
        return hasFlag(type, NodeFlag.RENDER_INLINE_PARAMETERS);
    }

    public static boolean isInlineParameterNode(NodeType type) {
        return isParameterNode(type)
            && type != NodeType.OPERATOR_MOD
            && type != NodeType.PARAM_DURATION
            && type != NodeType.SENSOR_POSITION_OF
            && type != NodeType.SENSOR_DISTANCE_BETWEEN
            && type != NodeType.SENSOR_LOOK_DIRECTION
            && type != NodeType.SENSOR_FIND_TRADE
            && type != NodeType.SENSOR_SLOT_ITEM_COUNT;
    }

    public static boolean hasBooleanToggle(NodeType type) {
        return hasFlag(type, NodeFlag.BOOLEAN_TOGGLE);
    }

    public static boolean hasPopupEditButton(NodeType type) {
        return isParameterNode(type) && hasFlag(type, NodeFlag.POPUP_EDIT_BUTTON);
    }

    public static EnumSet<NodeValueTrait> providedTraits(NodeType type) {
        EnumSet<NodeValueTrait> traits = type == null ? null : PROVIDED_TRAITS.get(type);
        return traits == null ? EnumSet.noneOf(NodeValueTrait.class) : EnumSet.copyOf(traits);
    }

    public static Map<NodeType, EnumSet<NodeValueTrait>> providedTraitsSnapshot() {
        return Collections.unmodifiableMap(PROVIDED_TRAITS);
    }

    public static boolean canHostParameter(NodeType type) {
        return type != null && PARAMETER_SCHEMAS.containsKey(type);
    }

    public static int parameterSlotCount(NodeType hostType) {
        ParameterSchema schema = parameterSchema(hostType);
        return schema == null ? 0 : schema.slots().size();
    }

    public static String parameterSlotLabel(NodeType hostType, int slotIndex) {
        ParameterSlot slot = parameterSlot(hostType, slotIndex);
        return slot == null ? "Parameter" : slot.label();
    }

    public static EnumSet<NodeValueTrait> acceptedTraits(NodeType hostType, int slotIndex) {
        ParameterSlot slot = parameterSlot(hostType, slotIndex);
        return slot == null ? EnumSet.noneOf(NodeValueTrait.class) : EnumSet.copyOf(slot.acceptedTraits());
    }

    public static boolean isParameterSlotAlwaysRequired(NodeType hostType, int slotIndex) {
        ParameterSlot slot = parameterSlot(hostType, slotIndex);
        return slot != null && slot.required();
    }

    public static boolean shouldDisplayInSidebar(NodeType type, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        if (!isDraggableFromSidebar(type)) {
            return false;
        }
        if (!baritoneAvailable && requiresBaritone(type)) {
            return false;
        }
        if (!uiUtilsAvailable && requiresUiUtils(type)) {
            return false;
        }
        return true;
    }

    public static List<SidebarGroup> sidebarGroups(NodeCategory category, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        if (category == null || category == NodeCategory.CUSTOM) {
            return Collections.emptyList();
        }
        List<SidebarGroup> groups = new ArrayList<>();
        for (SidebarGroupDefinition group : SIDEBAR_GROUPS) {
            if (!group.appliesTo(category, baritoneAvailable)) {
                continue;
            }
            List<NodeType> nodes = new ArrayList<>();
            for (NodeType type : group.types()) {
                if (shouldDisplayInSidebar(type, baritoneAvailable, uiUtilsAvailable)) {
                    nodes.add(type);
                }
            }
            groups.add(new SidebarGroup(group.displayCategory(), group.titleKey(), nodes));
        }
        return groups;
    }

    public static NodePlacement sidebarPlacement(NodeType type, boolean baritoneAvailable, boolean uiUtilsAvailable) {
        if (type == null || !shouldDisplayInSidebar(type, baritoneAvailable, uiUtilsAvailable)) {
            return null;
        }
        for (SidebarGroupDefinition group : SIDEBAR_GROUPS) {
            if (!group.appliesTo(group.displayCategory(), baritoneAvailable)) {
                continue;
            }
            if (group.types().contains(type)) {
                return new NodePlacement(type, group.displayCategory(), group.titleKey());
            }
        }
        return null;
    }

    public static List<SidebarGroup> sidebarGroupsForAllCategories(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        List<SidebarGroup> groups = new ArrayList<>();
        for (NodeCategory category : NodeCategory.values()) {
            groups.addAll(sidebarGroups(category, baritoneAvailable, uiUtilsAvailable));
        }
        return groups;
    }

    private static String nameKey(NodeType type) {
        return switch (type) {
            case START -> "pathmind.node.type.start";
            case START_CHAIN -> "pathmind.node.type.startChain";
            case EVENT_FUNCTION -> "pathmind.node.type.eventFunction";
            case EVENT_CALL -> "pathmind.node.type.eventCall";
            case VARIABLE -> "pathmind.node.type.variable";
            case SET_VARIABLE -> "pathmind.node.type.setVariable";
            case CHANGE_VARIABLE -> "pathmind.node.type.changeVariable";
            case CREATE_LIST -> "pathmind.node.type.createList";
            case ADD_TO_LIST -> "pathmind.node.type.addToList";
            case REMOVE_FIRST_FROM_LIST -> "pathmind.node.type.removeFirstFromList";
            case REMOVE_LAST_FROM_LIST -> "pathmind.node.type.removeLastFromList";
            case REMOVE_LIST_ITEM -> "pathmind.node.type.removeListItem";
            case REMOVE_FROM_LIST -> "pathmind.node.type.removeFromList";
            case LIST_ITEM -> "pathmind.node.type.listItem";
            case LIST_LENGTH -> "pathmind.node.type.listLength";
            case OPERATOR_EQUALS -> "pathmind.node.type.operatorEquals";
            case OPERATOR_NOT -> "pathmind.node.type.operatorNot";
            case OPERATOR_BOOLEAN_NOT -> "pathmind.node.type.operatorBooleanNot";
            case OPERATOR_BOOLEAN_OR -> "pathmind.node.type.operatorBooleanOr";
            case OPERATOR_BOOLEAN_AND -> "pathmind.node.type.operatorBooleanAnd";
            case OPERATOR_BOOLEAN_XOR -> "pathmind.node.type.operatorBooleanXor";
            case OPERATOR_GREATER -> "pathmind.node.type.operatorGreater";
            case OPERATOR_LESS -> "pathmind.node.type.operatorLess";
            case OPERATOR_MOD -> "pathmind.node.type.operatorMod";
            case OPERATOR_RANDOM -> "pathmind.node.type.operatorRandom";
            case GOTO -> "pathmind.node.type.goto";
            case TRAVEL -> "pathmind.node.type.travel";
            case GOAL -> "pathmind.node.type.goal";
            case PATH -> "pathmind.node.type.path";
            case INVERT -> "pathmind.node.type.invert";
            case COME -> "pathmind.node.type.come";
            case SURFACE -> "pathmind.node.type.surface";
            case COLLECT -> "pathmind.node.type.collect";
            case BUILD -> "pathmind.node.type.build";
            case TUNNEL -> "pathmind.node.type.tunnel";
            case FARM -> "pathmind.node.type.farm";
            case PLACE -> "pathmind.node.type.place";
            case CRAFT -> "pathmind.node.type.craft";
            case EXPLORE -> "pathmind.node.type.explore";
            case FOLLOW -> "pathmind.node.type.follow";
            case CONTROL_REPEAT -> "pathmind.node.type.controlRepeat";
            case CONTROL_REPEAT_UNTIL -> "pathmind.node.type.controlRepeatUntil";
            case CONTROL_WAIT_UNTIL -> "pathmind.node.type.controlWaitUntil";
            case CONTROL_FOREVER -> "pathmind.node.type.controlForever";
            case CONTROL_IF -> "pathmind.node.type.controlIf";
            case CONTROL_IF_ELSE -> "pathmind.node.type.controlIfElse";
            case CONTROL_FORK -> "pathmind.node.type.controlFork";
            case CONTROL_JOIN_ANY -> "pathmind.node.type.controlJoinAny";
            case CONTROL_JOIN_ALL -> "pathmind.node.type.controlJoinAll";
            case LOOK -> "pathmind.node.type.look";
            case WALK -> "pathmind.node.type.walk";
            case JUMP -> "pathmind.node.type.jump";
            case PRESS_KEY -> "pathmind.node.type.pressKey";
            case CRAWL -> "pathmind.node.type.crawl";
            case CROUCH -> "pathmind.node.type.crouch";
            case SPRINT -> "pathmind.node.type.sprint";
            case FLY -> "pathmind.node.type.fly";
            case STOP -> "pathmind.node.type.stop";
            case SWING -> "pathmind.node.type.swing";
            case USE -> "pathmind.node.type.use";
            case INTERACT -> "pathmind.node.type.interact";
            case BREAK -> "pathmind.node.type.break";
            case PLACE_HAND -> "pathmind.node.type.placeHand";
            case TRADE -> "pathmind.node.type.trade";
            case HOTBAR -> "pathmind.node.type.hotbar";
            case DROP_ITEM -> "pathmind.node.type.dropItem";
            case DROP_SLOT -> "pathmind.node.type.dropItem";
            case CLICK_SLOT -> "pathmind.node.type.clickSlot";
            case CLICK_SCREEN -> "pathmind.node.type.clickScreen";
            case MOVE_ITEM -> "pathmind.node.type.moveItem";
            case OPEN_INVENTORY -> "pathmind.node.type.openInventory";
            case CLOSE_GUI -> "pathmind.node.type.closeGui";
            case WRITE_BOOK -> "pathmind.node.type.writeBook";
            case WRITE_SIGN -> "pathmind.node.type.writeSign";
            case UI_UTILS -> "pathmind.node.type.uiUtils";
            case EQUIP_ARMOR -> "pathmind.node.type.equipArmor";
            case EQUIP_HAND -> "pathmind.node.type.equipHand";
            case SENSOR_TOUCHING_BLOCK -> "pathmind.node.type.sensorTouchingBlock";
            case SENSOR_TOUCHING_ENTITY -> "pathmind.node.type.sensorTouchingEntity";
            case SENSOR_AT_COORDINATES -> "pathmind.node.type.sensorAtCoordinates";
            case SENSOR_POSITION_OF -> "pathmind.node.type.sensorPositionOf";
            case SENSOR_DISTANCE_BETWEEN -> "pathmind.node.type.sensorDistanceBetween";
            case SENSOR_TARGETED_BLOCK -> "pathmind.node.type.sensorTargetedBlock";
            case SENSOR_TARGETED_ENTITY -> "pathmind.node.type.sensorTargetedEntity";
            case SENSOR_LOOK_DIRECTION -> "pathmind.node.type.sensorLookDirection";
            case SENSOR_CURRENT_HAND -> "pathmind.node.type.sensorCurrentHand";
            case SENSOR_TARGETED_BLOCK_FACE -> "pathmind.node.type.sensorTargetedBlockFace";
            case SENSOR_GUI_FILLED -> "pathmind.node.type.sensorGuiFilled";
            case SENSOR_IS_DAYTIME -> "pathmind.node.type.sensorIsDaytime";
            case SENSOR_IS_RAINING -> "pathmind.node.type.sensorIsRaining";
            case SENSOR_HEALTH_BELOW -> "pathmind.node.type.sensorHealthBelow";
            case SENSOR_HUNGER_BELOW -> "pathmind.node.type.sensorHungerBelow";
            case SENSOR_ITEM_IN_INVENTORY -> "pathmind.node.type.sensorItemInInventory";
            case SENSOR_ITEM_IN_SLOT -> "pathmind.node.type.sensorItemInSlot";
            case SENSOR_SLOT_ITEM_COUNT -> "pathmind.node.type.sensorSlotItemCount";
            case SENSOR_FIND_TRADE -> "pathmind.node.type.sensorFindTrade";
            case SENSOR_VILLAGER_TRADE -> "pathmind.node.type.sensorVillagerTrade";
            case SENSOR_IN_STOCK -> "pathmind.node.type.sensorInStock";
            case SENSOR_IS_SWIMMING -> "pathmind.node.type.sensorIsSwimming";
            case SENSOR_IS_IN_LAVA -> "pathmind.node.type.sensorIsInLava";
            case SENSOR_IS_UNDERWATER -> "pathmind.node.type.sensorIsUnderwater";
            case SENSOR_IS_ON_GROUND -> "pathmind.node.type.sensorIsOnGround";
            case SENSOR_IS_FALLING -> "pathmind.node.type.sensorIsFalling";
            case SENSOR_IS_RENDERED -> "pathmind.node.type.sensorIsRendered";
            case SENSOR_IS_VISIBLE -> "pathmind.node.type.sensorIsVisible";
            case SENSOR_KEY_PRESSED -> "pathmind.node.type.sensorKeyPressed";
            case SENSOR_CHAT_MESSAGE -> "pathmind.node.type.sensorChatMessage";
            case SENSOR_JOINED_SERVER -> "pathmind.node.type.sensorJoinedServer";
            case SENSOR_FABRIC_EVENT -> "pathmind.node.type.sensorFabricEvent";
            case SENSOR_ATTRIBUTE_DETECTION -> "pathmind.node.type.sensorAttributeDetection";
            case RUN_PRESET -> "pathmind.node.type.runPreset";
            case CUSTOM_NODE -> "pathmind.node.type.customNode";
            case WAIT -> "pathmind.node.type.wait";
            case STICKY_NOTE -> "pathmind.node.type.stickyNote";
            case MESSAGE -> "pathmind.node.type.message";
            case TEMPLATE -> "pathmind.node.type.template";
            case STOP_CHAIN -> "pathmind.node.type.stopChain";
            case STOP_ALL -> "pathmind.node.type.stopAll";
            case PARAM_COORDINATE -> "pathmind.node.type.paramCoordinate";
            case PARAM_BLOCK -> "pathmind.node.type.paramBlock";
            case PARAM_ITEM -> "pathmind.node.type.paramItem";
            case PARAM_VILLAGER_TRADE -> "pathmind.node.type.paramVillagerTrade";
            case PARAM_ENTITY -> "pathmind.node.type.paramEntity";
            case PARAM_PLAYER -> "pathmind.node.type.paramPlayer";
            case PARAM_WAYPOINT -> "pathmind.node.type.paramWaypoint";
            case PARAM_SCHEMATIC -> "pathmind.node.type.paramSchematic";
            case PARAM_INVENTORY_SLOT -> "pathmind.node.type.paramInventorySlot";
            case PARAM_MESSAGE -> "pathmind.node.type.paramMessage";
            case PARAM_DURATION -> "pathmind.node.type.paramDuration";
            case PARAM_AMOUNT -> "pathmind.node.type.paramAmount";
            case PARAM_BOOLEAN -> "pathmind.node.type.paramBoolean";
            case PARAM_HAND -> "pathmind.node.type.paramHand";
            case PARAM_GUI -> "pathmind.node.type.paramGui";
            case PARAM_KEY -> "pathmind.node.type.paramKey";
            case PARAM_MOUSE_BUTTON -> "pathmind.node.type.paramMouseButton";
            case PARAM_RANGE -> "pathmind.node.type.paramRange";
            case PARAM_DISTANCE -> "pathmind.node.type.paramDistance";
            case PARAM_DIRECTION -> "pathmind.node.type.paramDirection";
            case PARAM_BLOCK_FACE -> "pathmind.node.type.paramBlockFace";
            case PARAM_ROTATION -> "pathmind.node.type.paramRotation";
            case PARAM_PLACE_TARGET -> "pathmind.node.type.paramPlaceTarget";
            case PARAM_CLOSEST -> "pathmind.node.type.paramClosest";
        };
    }

    private static String descriptionKey(NodeType type) {
        return switch (type) {
            case START -> "pathmind.node.type.start.desc";
            case START_CHAIN -> "pathmind.node.type.startChain.desc";
            case EVENT_FUNCTION -> "pathmind.node.type.eventFunction.desc";
            case EVENT_CALL -> "pathmind.node.type.eventCall.desc";
            case VARIABLE -> "pathmind.node.type.variable.desc";
            case SET_VARIABLE -> "pathmind.node.type.setVariable.desc";
            case CHANGE_VARIABLE -> "pathmind.node.type.changeVariable.desc";
            case CREATE_LIST -> "pathmind.node.type.createList.desc";
            case ADD_TO_LIST -> "pathmind.node.type.addToList.desc";
            case REMOVE_FIRST_FROM_LIST -> "pathmind.node.type.removeFirstFromList.desc";
            case REMOVE_LAST_FROM_LIST -> "pathmind.node.type.removeLastFromList.desc";
            case REMOVE_LIST_ITEM -> "pathmind.node.type.removeListItem.desc";
            case REMOVE_FROM_LIST -> "pathmind.node.type.removeFromList.desc";
            case LIST_ITEM -> "pathmind.node.type.listItem.desc";
            case LIST_LENGTH -> "pathmind.node.type.listLength.desc";
            case OPERATOR_EQUALS -> "pathmind.node.type.operatorEquals.desc";
            case OPERATOR_NOT -> "pathmind.node.type.operatorNot.desc";
            case OPERATOR_BOOLEAN_NOT -> "pathmind.node.type.operatorBooleanNot.desc";
            case OPERATOR_BOOLEAN_OR -> "pathmind.node.type.operatorBooleanOr.desc";
            case OPERATOR_BOOLEAN_AND -> "pathmind.node.type.operatorBooleanAnd.desc";
            case OPERATOR_BOOLEAN_XOR -> "pathmind.node.type.operatorBooleanXor.desc";
            case OPERATOR_GREATER -> "pathmind.node.type.operatorGreater.desc";
            case OPERATOR_LESS -> "pathmind.node.type.operatorLess.desc";
            case OPERATOR_MOD -> "pathmind.node.type.operatorMod.desc";
            case OPERATOR_RANDOM -> "pathmind.node.type.operatorRandom.desc";
            case GOTO -> "pathmind.node.type.goto.desc";
            case TRAVEL -> "pathmind.node.type.travel.desc";
            case GOAL -> "pathmind.node.type.goal.desc";
            case PATH -> "pathmind.node.type.path.desc";
            case INVERT -> "pathmind.node.type.invert.desc";
            case COME -> "pathmind.node.type.come.desc";
            case SURFACE -> "pathmind.node.type.surface.desc";
            case COLLECT -> "pathmind.node.type.collect.desc";
            case BUILD -> "pathmind.node.type.build.desc";
            case TUNNEL -> "pathmind.node.type.tunnel.desc";
            case FARM -> "pathmind.node.type.farm.desc";
            case PLACE -> "pathmind.node.type.place.desc";
            case CRAFT -> "pathmind.node.type.craft.desc";
            case EXPLORE -> "pathmind.node.type.explore.desc";
            case FOLLOW -> "pathmind.node.type.follow.desc";
            case CONTROL_REPEAT -> "pathmind.node.type.controlRepeat.desc";
            case CONTROL_REPEAT_UNTIL -> "pathmind.node.type.controlRepeatUntil.desc";
            case CONTROL_WAIT_UNTIL -> "pathmind.node.type.controlWaitUntil.desc";
            case CONTROL_FOREVER -> "pathmind.node.type.controlForever.desc";
            case CONTROL_IF -> "pathmind.node.type.controlIf.desc";
            case CONTROL_IF_ELSE -> "pathmind.node.type.controlIfElse.desc";
            case CONTROL_FORK -> "pathmind.node.type.controlFork.desc";
            case CONTROL_JOIN_ANY -> "pathmind.node.type.controlJoinAny.desc";
            case CONTROL_JOIN_ALL -> "pathmind.node.type.controlJoinAll.desc";
            case LOOK -> "pathmind.node.type.look.desc";
            case WALK -> "pathmind.node.type.walk.desc";
            case JUMP -> "pathmind.node.type.jump.desc";
            case PRESS_KEY -> "pathmind.node.type.pressKey.desc";
            case CRAWL -> "pathmind.node.type.crawl.desc";
            case CROUCH -> "pathmind.node.type.crouch.desc";
            case SPRINT -> "pathmind.node.type.sprint.desc";
            case FLY -> "pathmind.node.type.fly.desc";
            case STOP -> "pathmind.node.type.stop.desc";
            case SWING -> "pathmind.node.type.swing.desc";
            case USE -> "pathmind.node.type.use.desc";
            case INTERACT -> "pathmind.node.type.interact.desc";
            case BREAK -> "pathmind.node.type.break.desc";
            case PLACE_HAND -> "pathmind.node.type.placeHand.desc";
            case TRADE -> "pathmind.node.type.trade.desc";
            case HOTBAR -> "pathmind.node.type.hotbar.desc";
            case DROP_ITEM -> "pathmind.node.type.dropItem.desc";
            case DROP_SLOT -> "pathmind.node.type.dropItem.desc";
            case CLICK_SLOT -> "pathmind.node.type.clickSlot.desc";
            case CLICK_SCREEN -> "pathmind.node.type.clickScreen.desc";
            case MOVE_ITEM -> "pathmind.node.type.moveItem.desc";
            case OPEN_INVENTORY -> "pathmind.node.type.openInventory.desc";
            case CLOSE_GUI -> "pathmind.node.type.closeGui.desc";
            case WRITE_BOOK -> "pathmind.node.type.writeBook.desc";
            case WRITE_SIGN -> "pathmind.node.type.writeSign.desc";
            case UI_UTILS -> "pathmind.node.type.uiUtils.desc";
            case EQUIP_ARMOR -> "pathmind.node.type.equipArmor.desc";
            case EQUIP_HAND -> "pathmind.node.type.equipHand.desc";
            case SENSOR_TOUCHING_BLOCK -> "pathmind.node.type.sensorTouchingBlock.desc";
            case SENSOR_TOUCHING_ENTITY -> "pathmind.node.type.sensorTouchingEntity.desc";
            case SENSOR_AT_COORDINATES -> "pathmind.node.type.sensorAtCoordinates.desc";
            case SENSOR_POSITION_OF -> "pathmind.node.type.sensorPositionOf.desc";
            case SENSOR_DISTANCE_BETWEEN -> "pathmind.node.type.sensorDistanceBetween.desc";
            case SENSOR_TARGETED_BLOCK -> "pathmind.node.type.sensorTargetedBlock.desc";
            case SENSOR_TARGETED_ENTITY -> "pathmind.node.type.sensorTargetedEntity.desc";
            case SENSOR_LOOK_DIRECTION -> "pathmind.node.type.sensorLookDirection.desc";
            case SENSOR_CURRENT_HAND -> "pathmind.node.type.sensorCurrentHand.desc";
            case SENSOR_TARGETED_BLOCK_FACE -> "pathmind.node.type.sensorTargetedBlockFace.desc";
            case SENSOR_GUI_FILLED -> "pathmind.node.type.sensorGuiFilled.desc";
            case SENSOR_IS_DAYTIME -> "pathmind.node.type.sensorIsDaytime.desc";
            case SENSOR_IS_RAINING -> "pathmind.node.type.sensorIsRaining.desc";
            case SENSOR_HEALTH_BELOW -> "pathmind.node.type.sensorHealthBelow.desc";
            case SENSOR_HUNGER_BELOW -> "pathmind.node.type.sensorHungerBelow.desc";
            case SENSOR_ITEM_IN_INVENTORY -> "pathmind.node.type.sensorItemInInventory.desc";
            case SENSOR_ITEM_IN_SLOT -> "pathmind.node.type.sensorItemInSlot.desc";
            case SENSOR_SLOT_ITEM_COUNT -> "pathmind.node.type.sensorSlotItemCount.desc";
            case SENSOR_FIND_TRADE -> "pathmind.node.type.sensorFindTrade.desc";
            case SENSOR_VILLAGER_TRADE -> "pathmind.node.type.sensorVillagerTrade.desc";
            case SENSOR_IN_STOCK -> "pathmind.node.type.sensorInStock.desc";
            case SENSOR_IS_SWIMMING -> "pathmind.node.type.sensorIsSwimming.desc";
            case SENSOR_IS_IN_LAVA -> "pathmind.node.type.sensorIsInLava.desc";
            case SENSOR_IS_UNDERWATER -> "pathmind.node.type.sensorIsUnderwater.desc";
            case SENSOR_IS_ON_GROUND -> "pathmind.node.type.sensorIsOnGround.desc";
            case SENSOR_IS_FALLING -> "pathmind.node.type.sensorIsFalling.desc";
            case SENSOR_IS_RENDERED -> "pathmind.node.type.sensorIsRendered.desc";
            case SENSOR_IS_VISIBLE -> "pathmind.node.type.sensorIsVisible.desc";
            case SENSOR_KEY_PRESSED -> "pathmind.node.type.sensorKeyPressed.desc";
            case SENSOR_CHAT_MESSAGE -> "pathmind.node.type.sensorChatMessage.desc";
            case SENSOR_JOINED_SERVER -> "pathmind.node.type.sensorJoinedServer.desc";
            case SENSOR_FABRIC_EVENT -> "pathmind.node.type.sensorFabricEvent.desc";
            case SENSOR_ATTRIBUTE_DETECTION -> "pathmind.node.type.sensorAttributeDetection.desc";
            case RUN_PRESET -> "pathmind.node.type.runPreset.desc";
            case CUSTOM_NODE -> "pathmind.node.type.customNode.desc";
            case WAIT -> "pathmind.node.type.wait.desc";
            case STICKY_NOTE -> "pathmind.node.type.stickyNote.desc";
            case MESSAGE -> "pathmind.node.type.message.desc";
            case TEMPLATE -> "pathmind.node.type.template.desc";
            case STOP_CHAIN -> "pathmind.node.type.stopChain.desc";
            case STOP_ALL -> "pathmind.node.type.stopAll.desc";
            case PARAM_COORDINATE -> "pathmind.node.type.paramCoordinate.desc";
            case PARAM_BLOCK -> "pathmind.node.type.paramBlock.desc";
            case PARAM_ITEM -> "pathmind.node.type.paramItem.desc";
            case PARAM_VILLAGER_TRADE -> "pathmind.node.type.paramVillagerTrade.desc";
            case PARAM_ENTITY -> "pathmind.node.type.paramEntity.desc";
            case PARAM_PLAYER -> "pathmind.node.type.paramPlayer.desc";
            case PARAM_WAYPOINT -> "pathmind.node.type.paramWaypoint.desc";
            case PARAM_SCHEMATIC -> "pathmind.node.type.paramSchematic.desc";
            case PARAM_INVENTORY_SLOT -> "pathmind.node.type.paramInventorySlot.desc";
            case PARAM_MESSAGE -> "pathmind.node.type.paramMessage.desc";
            case PARAM_DURATION -> "pathmind.node.type.paramDuration.desc";
            case PARAM_AMOUNT -> "pathmind.node.type.paramAmount.desc";
            case PARAM_BOOLEAN -> "pathmind.node.type.paramBoolean.desc";
            case PARAM_HAND -> "pathmind.node.type.paramHand.desc";
            case PARAM_GUI -> "pathmind.node.type.paramGui.desc";
            case PARAM_KEY -> "pathmind.node.type.paramKey.desc";
            case PARAM_MOUSE_BUTTON -> "pathmind.node.type.paramMouseButton.desc";
            case PARAM_RANGE -> "pathmind.node.type.paramRange.desc";
            case PARAM_DISTANCE -> "pathmind.node.type.paramDistance.desc";
            case PARAM_DIRECTION -> "pathmind.node.type.paramDirection.desc";
            case PARAM_BLOCK_FACE -> "pathmind.node.type.paramBlockFace.desc";
            case PARAM_ROTATION -> "pathmind.node.type.paramRotation.desc";
            case PARAM_PLACE_TARGET -> "pathmind.node.type.paramPlaceTarget.desc";
            case PARAM_CLOSEST -> "pathmind.node.type.paramClosest.desc";
        };
    }

    private static int baseColor(NodeType type) {
        return switch (type) {
            case START -> 0xFF4CAF50;
            case START_CHAIN -> 0xFF4CAF50;
            case EVENT_FUNCTION -> 0xFFE91E63;
            case EVENT_CALL -> 0xFFE91E63;
            case VARIABLE -> 0xFFFF9800;
            case SET_VARIABLE -> 0xFFFF9800;
            case CHANGE_VARIABLE -> 0xFFFF9800;
            case CREATE_LIST -> 0xFFFF9800;
            case ADD_TO_LIST -> 0xFFFF9800;
            case REMOVE_FIRST_FROM_LIST -> 0xFFFF9800;
            case REMOVE_LAST_FROM_LIST -> 0xFFFF9800;
            case REMOVE_LIST_ITEM -> 0xFFFF9800;
            case REMOVE_FROM_LIST -> 0xFFFF9800;
            case LIST_ITEM -> 0xFFFF9800;
            case LIST_LENGTH -> 0xFFFF9800;
            case OPERATOR_EQUALS -> 0xFF00C853;
            case OPERATOR_NOT -> 0xFF00C853;
            case OPERATOR_BOOLEAN_NOT -> 0xFF00C853;
            case OPERATOR_BOOLEAN_OR -> 0xFF00C853;
            case OPERATOR_BOOLEAN_AND -> 0xFF00C853;
            case OPERATOR_BOOLEAN_XOR -> 0xFF00C853;
            case OPERATOR_GREATER -> 0xFF00C853;
            case OPERATOR_LESS -> 0xFF00C853;
            case OPERATOR_MOD -> 0xFF00C853;
            case OPERATOR_RANDOM -> 0xFF00C853;
            case GOTO -> 0xFF00BCD4;
            case TRAVEL -> 0xFF26C6DA;
            case GOAL -> 0xFF2196F3;
            case PATH -> 0xFF03DAC6;
            case INVERT -> 0xFFFF5722;
            case COME -> 0xFF9C27B0;
            case SURFACE -> 0xFF4CAF50;
            case COLLECT -> 0xFF2196F3;
            case BUILD -> 0xFFFF9800;
            case TUNNEL -> 0xFF795548;
            case FARM -> 0xFF4CAF50;
            case PLACE -> 0xFF9C27B0;
            case CRAFT -> 0xFFFF9800;
            case EXPLORE -> 0xFF673AB7;
            case FOLLOW -> 0xFF3F51B5;
            case CONTROL_REPEAT -> 0xFFFFC107;
            case CONTROL_REPEAT_UNTIL -> 0xFFFFC107;
            case CONTROL_WAIT_UNTIL -> 0xFFFFC107;
            case CONTROL_FOREVER -> 0xFFFFC107;
            case CONTROL_IF -> 0xFFFFC107;
            case CONTROL_IF_ELSE -> 0xFFFFC107;
            case CONTROL_FORK -> 0xFFFFC107;
            case CONTROL_JOIN_ANY -> 0xFFFFC107;
            case CONTROL_JOIN_ALL -> 0xFFFFC107;
            case LOOK -> 0xFF03A9F4;
            case WALK -> 0xFF26C6DA;
            case JUMP -> 0xFF009688;
            case PRESS_KEY -> 0xFF26A69A;
            case CRAWL -> 0xFF455A64;
            case CROUCH -> 0xFF607D8B;
            case SPRINT -> 0xFFFFEB3B;
            case FLY -> 0xFF29B6F6;
            case STOP -> 0xFFF44336;
            case SWING -> 0xFFFF7043;
            case USE -> 0xFF8BC34A;
            case INTERACT -> 0xFF4DB6AC;
            case BREAK -> 0xFF4DB6AC;
            case PLACE_HAND -> 0xFFBA68C8;
            case TRADE -> 0xFF7E57C2;
            case HOTBAR -> 0xFFCDDC39;
            case DROP_ITEM -> 0xFFFFAB91;
            case DROP_SLOT -> 0xFFFF7043;
            case CLICK_SLOT -> 0xFFFF7043;
            case CLICK_SCREEN -> 0xFFFF7043;
            case MOVE_ITEM -> 0xFFFFB74D;
            case OPEN_INVENTORY -> 0xFFB0BEC5;
            case CLOSE_GUI -> 0xFFB0BEC5;
            case WRITE_BOOK -> 0xFFB0BEC5;
            case WRITE_SIGN -> 0xFFB0BEC5;
            case UI_UTILS -> 0xFFB0BEC5;
            case EQUIP_ARMOR -> 0xFF7E57C2;
            case EQUIP_HAND -> 0xFF5C6BC0;
            case SENSOR_TOUCHING_BLOCK -> 0xFF64B5F6;
            case SENSOR_TOUCHING_ENTITY -> 0xFF64B5F6;
            case SENSOR_AT_COORDINATES -> 0xFF64B5F6;
            case SENSOR_POSITION_OF -> 0xFF64B5F6;
            case SENSOR_DISTANCE_BETWEEN -> 0xFF64B5F6;
            case SENSOR_TARGETED_BLOCK -> 0xFF64B5F6;
            case SENSOR_TARGETED_ENTITY -> 0xFF64B5F6;
            case SENSOR_LOOK_DIRECTION -> 0xFF64B5F6;
            case SENSOR_CURRENT_HAND -> 0xFF64B5F6;
            case SENSOR_TARGETED_BLOCK_FACE -> 0xFF64B5F6;
            case SENSOR_GUI_FILLED -> 0xFF64B5F6;
            case SENSOR_IS_DAYTIME -> 0xFF64B5F6;
            case SENSOR_IS_RAINING -> 0xFF64B5F6;
            case SENSOR_HEALTH_BELOW -> 0xFF64B5F6;
            case SENSOR_HUNGER_BELOW -> 0xFF64B5F6;
            case SENSOR_ITEM_IN_INVENTORY -> 0xFF64B5F6;
            case SENSOR_ITEM_IN_SLOT -> 0xFF64B5F6;
            case SENSOR_SLOT_ITEM_COUNT -> 0xFF64B5F6;
            case SENSOR_FIND_TRADE -> 0xFF64B5F6;
            case SENSOR_VILLAGER_TRADE -> 0xFF64B5F6;
            case SENSOR_IN_STOCK -> 0xFF64B5F6;
            case SENSOR_IS_SWIMMING -> 0xFF64B5F6;
            case SENSOR_IS_IN_LAVA -> 0xFF64B5F6;
            case SENSOR_IS_UNDERWATER -> 0xFF64B5F6;
            case SENSOR_IS_ON_GROUND -> 0xFF64B5F6;
            case SENSOR_IS_FALLING -> 0xFF64B5F6;
            case SENSOR_IS_RENDERED -> 0xFF64B5F6;
            case SENSOR_IS_VISIBLE -> 0xFF64B5F6;
            case SENSOR_KEY_PRESSED -> 0xFF64B5F6;
            case SENSOR_CHAT_MESSAGE -> 0xFF64B5F6;
            case SENSOR_JOINED_SERVER -> 0xFF64B5F6;
            case SENSOR_FABRIC_EVENT -> 0xFF64B5F6;
            case SENSOR_ATTRIBUTE_DETECTION -> 0xFF64B5F6;
            case RUN_PRESET -> 0xFF607D8B;
            case CUSTOM_NODE -> 0xFF26A69A;
            case WAIT -> 0xFF607D8B;
            case STICKY_NOTE -> 0xFFEBCB5B;
            case MESSAGE -> 0xFF9E9E9E;
            case TEMPLATE -> 0xFF26A69A;
            case STOP_CHAIN -> 0xFFE53935;
            case STOP_ALL -> 0xFFE53935;
            case PARAM_COORDINATE -> 0xFF8BC34A;
            case PARAM_BLOCK -> 0xFF8BC34A;
            case PARAM_ITEM -> 0xFF8BC34A;
            case PARAM_VILLAGER_TRADE -> 0xFF8BC34A;
            case PARAM_ENTITY -> 0xFF8BC34A;
            case PARAM_PLAYER -> 0xFF8BC34A;
            case PARAM_WAYPOINT -> 0xFF8BC34A;
            case PARAM_SCHEMATIC -> 0xFF8BC34A;
            case PARAM_INVENTORY_SLOT -> 0xFF8BC34A;
            case PARAM_MESSAGE -> 0xFF8BC34A;
            case PARAM_DURATION -> 0xFF8BC34A;
            case PARAM_AMOUNT -> 0xFF8BC34A;
            case PARAM_BOOLEAN -> 0xFF8BC34A;
            case PARAM_HAND -> 0xFF8BC34A;
            case PARAM_GUI -> 0xFF8BC34A;
            case PARAM_KEY -> 0xFF8BC34A;
            case PARAM_MOUSE_BUTTON -> 0xFF8BC34A;
            case PARAM_RANGE -> 0xFF8BC34A;
            case PARAM_DISTANCE -> 0xFF8BC34A;
            case PARAM_DIRECTION -> 0xFF8BC34A;
            case PARAM_BLOCK_FACE -> 0xFF8BC34A;
            case PARAM_ROTATION -> 0xFF8BC34A;
            case PARAM_PLACE_TARGET -> 0xFF8BC34A;
            case PARAM_CLOSEST -> 0xFF8BC34A;
        };
    }

    private static boolean hasFlag(NodeType type, NodeFlag flag) {
        NodeDefinition definition = definition(type);
        return definition != null && definition.flags().contains(flag);
    }

    private static void define(NodeCategory category, NodeType... types) {
        for (NodeType type : types) {
            DEFINITIONS.put(type, new NodeDefinition(type, nameKey(type), descriptionKey(type), baseColor(type), category, EnumSet.noneOf(NodeFlag.class)));
        }
    }

    private static void tag(NodeFlag flag, NodeType... types) {
        for (NodeType type : types) {
            NodeDefinition definition = DEFINITIONS.get(type);
            if (definition == null) {
                throw new IllegalStateException("Missing node definition for " + type);
            }
            definition.flags().add(flag);
        }
    }

    private static void sidebar(NodeCategory displayCategory, String titleKey, NodeType... types) {
        SIDEBAR_GROUPS.add(new SidebarGroupDefinition(displayCategory, displayCategory, titleKey, true, types));
    }

    private static void fallbackSidebar(NodeCategory displayCategory, NodeCategory hiddenWhenCategoryVisible, String titleKey, NodeType... types) {
        SIDEBAR_GROUPS.add(new SidebarGroupDefinition(displayCategory, hiddenWhenCategoryVisible, titleKey, false, types));
    }

    private static void provided(NodeType type, NodeValueTrait firstTrait, NodeValueTrait... additionalTraits) {
        PROVIDED_TRAITS.put(type, EnumSet.of(firstTrait, additionalTraits));
    }

    private static void parameterHost(NodeType type, NodeValueTrait... acceptedTraits) {
        parameterHost(type, "Parameter", acceptedTraits);
    }

    private static void parameterHost(NodeType type, String label, NodeValueTrait... acceptedTraits) {
        parameterHost(type, slot(label, true, acceptedTraits));
    }

    private static void parameterHost(NodeType type, ParameterSlot... slots) {
        PARAMETER_SCHEMAS.put(type, new ParameterSchema(List.of(slots)));
    }

    private static ParameterSlot slot(String label, boolean required, NodeValueTrait... acceptedTraits) {
        String slotLabel = label == null || label.isBlank() ? "Parameter" : label;
        EnumSet<NodeValueTrait> traits = acceptedTraits.length == 0
            ? EnumSet.noneOf(NodeValueTrait.class)
            : EnumSet.copyOf(List.of(acceptedTraits));
        return new ParameterSlot(slotLabel, required, traits);
    }

    private static NodeValueTrait[] movementTargetTraits() {
        return new NodeValueTrait[] {
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        };
    }

    private static ParameterSchema parameterSchema(NodeType type) {
        return type == null ? null : PARAMETER_SCHEMAS.get(type);
    }

    private static ParameterSlot parameterSlot(NodeType type, int slotIndex) {
        ParameterSchema schema = parameterSchema(type);
        if (schema == null || slotIndex < 0 || slotIndex >= schema.slots().size()) {
            return null;
        }
        return schema.slots().get(slotIndex);
    }

    private static boolean usesExplicitGraphColor(NodeType type) {
        return type == NodeType.START || type == NodeType.START_CHAIN
            || type == NodeType.TEMPLATE || type == NodeType.CUSTOM_NODE
            || type == NodeType.STOP_CHAIN || type == NodeType.STOP_ALL;
    }

    public record NodeDefinition(NodeType type, String nameKey, String descriptionKey, int baseColor, NodeCategory category, Set<NodeFlag> flags) {}

    public record SidebarGroup(NodeCategory displayCategory, String titleKey, List<NodeType> nodes) {
        public SidebarGroup {
            nodes = List.copyOf(nodes);
        }
    }

    public record NodePlacement(NodeType type, NodeCategory displayCategory, String titleKey) {}

    public record ParameterSchema(List<ParameterSlot> slots) {
        public ParameterSchema {
            slots = List.copyOf(slots);
        }
    }

    public record ParameterSlot(String label, boolean required, EnumSet<NodeValueTrait> acceptedTraits) {
        public ParameterSlot {
            label = label == null || label.isBlank() ? "Parameter" : label;
            acceptedTraits = acceptedTraits == null || acceptedTraits.isEmpty()
                ? EnumSet.noneOf(NodeValueTrait.class)
                : EnumSet.copyOf(acceptedTraits);
        }
    }

    private record SidebarGroupDefinition(
        NodeCategory displayCategory,
        NodeCategory hiddenWhenCategoryVisible,
        String titleKey,
        boolean showWhenCategoryVisible,
        List<NodeType> types
    ) {
        private SidebarGroupDefinition(NodeCategory displayCategory, NodeCategory hiddenWhenCategoryVisible, String titleKey, boolean showWhenCategoryVisible, NodeType... types) {
            this(displayCategory, hiddenWhenCategoryVisible, titleKey, showWhenCategoryVisible, List.of(types));
        }

        private boolean appliesTo(NodeCategory category, boolean baritoneAvailable) {
            if (category != displayCategory) {
                return false;
            }
            if (displayCategory == NodeCategory.NAVIGATION && !baritoneAvailable) {
                return false;
            }
            boolean hiddenCategoryVisible = hiddenWhenCategoryVisible == NodeCategory.NAVIGATION && baritoneAvailable;
            return showWhenCategoryVisible || !hiddenCategoryVisible;
        }
    }

    public enum NodeFlag {
        HAS_PARAMETERS,
        HIDDEN_FROM_SIDEBAR,
        REQUIRES_BARITONE,
        REQUIRES_UI_UTILS,
        BOOLEAN_SENSOR,
        PARAMETER_NODE,
        SENSOR_WITHOUT_PARAMETER_SLOT,
        SENSOR_REQUIRES_PARAMETER,
        MINIMAL_PRESENTATION,
        RENDER_INLINE_PARAMETERS,
        BOOLEAN_TOGGLE,
        POPUP_EDIT_BUTTON
    }
}
