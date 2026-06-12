package com.pathmind.nodes;

import com.pathmind.data.SettingsManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.pathmind.nodes.NodeParameterDefinition.dynamic;
import static com.pathmind.nodes.NodeParameterDefinition.of;

final class NodeParameterDefinitionRegistry {
    private static final String BOOLEAN_MODE_LITERAL = "literal";
    private static final String DIRECTION_MODE_EXACT = "exact";
    private static final double DEFAULT_DIRECTION_DISTANCE = 16.0;
    private static final Map<NodeType, List<NodeParameterDefinition>> TYPE_DEFINITIONS = new EnumMap<>(NodeType.class);
    private static final Map<NodeMode, List<NodeParameterDefinition>> MODE_DEFINITIONS = new EnumMap<>(NodeMode.class);

    static {
        registerMode(NodeMode.GOTO_XYZ,
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.GOTO_XZ,
            of("X", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.GOTO_Y,
            of("Y", ParameterType.INTEGER, "64"));
        registerMode(NodeMode.GOTO_BLOCK,
            of("Block", ParameterType.STRING, "stone"));
        registerMode(NodeMode.GOAL_XYZ,
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.GOAL_XZ,
            of("X", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.GOAL_Y,
            of("Y", ParameterType.INTEGER, "64"));
        registerMode(NodeMode.COLLECT_SINGLE,
            of("Block", ParameterType.BLOCK_TYPE, "stone"),
            of("Amount", ParameterType.INTEGER, "1"));
        registerMode(NodeMode.COLLECT_MULTIPLE,
            of("Blocks", ParameterType.STRING, "stone,dirt"));
        registerMode(NodeMode.BUILD_PLAYER,
            of("Schematic", ParameterType.STRING, ""));
        registerMode(NodeMode.BUILD_XYZ,
            of("Schematic", ParameterType.STRING, ""),
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.EXPLORE_XYZ,
            of("X", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerMode(NodeMode.EXPLORE_FILTER,
            of("Filter", ParameterType.STRING, "explore.txt"));
        registerMode(NodeMode.FOLLOW_PLAYER,
            of("Player", ParameterType.STRING, "Self"));
        registerMode(NodeMode.FOLLOW_ENTITY_TYPE,
            of("Entity", ParameterType.STRING, "cow"));
        registerMode(NodeMode.CRAFT_PLAYER_GUI,
            of("Item", ParameterType.STRING, "stick"),
            of("Amount", ParameterType.INTEGER, "1"));
        registerMode(NodeMode.CRAFT_CRAFTING_TABLE,
            of("Item", ParameterType.STRING, "stick"),
            of("Amount", ParameterType.INTEGER, "1"));
        registerMode(NodeMode.FARM_RANGE,
            of("Range", ParameterType.INTEGER, "10"));
        registerMode(NodeMode.FARM_WAYPOINT,
            of("Waypoint", ParameterType.STRING, "farm"),
            of("Range", ParameterType.INTEGER, "10"));
        registerMode(NodeMode.WAIT_SECONDS,
            of("Duration", ParameterType.DOUBLE, ""));
        registerMode(NodeMode.WAIT_TICKS,
            of("Duration", ParameterType.DOUBLE, ""));
        registerMode(NodeMode.WAIT_MINUTES,
            of("Duration", ParameterType.DOUBLE, ""));
        registerMode(NodeMode.WAIT_HOURS,
            of("Duration", ParameterType.DOUBLE, ""));
        registerMode(NodeMode.UI_UTILS_SET_SEND_PACKETS,
            of("Enabled", ParameterType.BOOLEAN, "true"));
        registerMode(NodeMode.UI_UTILS_SET_DELAY_PACKETS,
            of("Enabled", ParameterType.BOOLEAN, "true"));
        registerMode(NodeMode.UI_UTILS_SET_ENABLED,
            of("Enabled", ParameterType.BOOLEAN, "true"));
        registerMode(NodeMode.UI_UTILS_SET_BYPASS_RESOURCE_PACK,
            of("Enabled", ParameterType.BOOLEAN, "true"));
        registerMode(NodeMode.UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK,
            of("Enabled", ParameterType.BOOLEAN, "true"));
        registerMode(NodeMode.UI_UTILS_FABRICATE_CLICK_SLOT,
            of("ui_click_sync_id", "SyncId", ParameterType.INTEGER, "-1"),
            of("ui_click_revision", "Revision", ParameterType.INTEGER, "-1"),
            of("ui_click_slot", "Slot", ParameterType.INTEGER, "0"),
            of("ui_click_button", "Button", ParameterType.INTEGER, "0"),
            of("ui_click_action", "Action", ParameterType.STRING, "PICKUP"),
            of("ui_click_times", "TimesToSend", ParameterType.INTEGER, "1"),
            of("ui_click_delay", "Delay", ParameterType.BOOLEAN, "false"));
        registerMode(NodeMode.UI_UTILS_FABRICATE_BUTTON_CLICK,
            of("ui_button_sync_id", "SyncId", ParameterType.INTEGER, "-1"),
            of("ui_button_id", "ButtonId", ParameterType.INTEGER, "0"),
            of("ui_button_times", "TimesToSend", ParameterType.INTEGER, "1"),
            of("ui_button_delay", "Delay", ParameterType.BOOLEAN, "false"));

        registerType(NodeType.PLACE,
            of("Block", ParameterType.BLOCK_TYPE, "stone"),
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerType(NodeType.WAIT, of("Duration", ParameterType.DOUBLE, "0.0"));
        registerType(NodeType.START_CHAIN, of("StartNumber", ParameterType.INTEGER, ""));
        registerType(NodeType.RUN_PRESET, of("Preset", ParameterType.STRING, ""));
        registerType(NodeType.CUSTOM_NODE, of("Preset", ParameterType.STRING, ""));
        registerType(NodeType.TEMPLATE, of("Preset", ParameterType.STRING, ""));
        registerType(NodeType.STOP_CHAIN, of("StartNumber", ParameterType.INTEGER, ""));
        registerType(NodeType.HOTBAR,
            of("hotbar_slot", "Slot", ParameterType.INTEGER, "0"),
            of("Item", ParameterType.STRING, ""));
        registerType(NodeType.DROP_ITEM,
            of("All", ParameterType.BOOLEAN, "false"),
            of("Count", ParameterType.INTEGER, "1"),
            of("UseAmount", ParameterType.BOOLEAN, "false"),
            of("IntervalSeconds", ParameterType.DOUBLE, "0.0"));
        registerType(NodeType.DROP_SLOT,
            of("drop_slot_index", "Slot", ParameterType.INTEGER, "0"),
            of("Count", ParameterType.INTEGER, "0"),
            of("EntireStack", ParameterType.BOOLEAN, "true"));
        registerType(NodeType.CLICK_SLOT, of("click_slot_index", "Slot", ParameterType.INTEGER, "0"));
        registerType(NodeType.CLICK_SCREEN,
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"));
        registerType(NodeType.MOVE_ITEM,
            of("move_item_source_slot", "SourceSlot", ParameterType.INTEGER, "0"),
            of("move_item_target_slot", "TargetSlot", ParameterType.INTEGER, "9"),
            of("Count", ParameterType.INTEGER, "0"));
        registerType(NodeType.EQUIP_ARMOR,
            of("equip_armor_source_slot", "SourceSlot", ParameterType.INTEGER, "0"),
            of("equip_armor_slot", "ArmorSlot", ParameterType.STRING, "head"));
        registerType(NodeType.EQUIP_HAND,
            of("equip_hand_source_slot", "SourceSlot", ParameterType.INTEGER, "0"),
            of("equip_hand_hand", "Hand", ParameterType.STRING, "main"));
        registerType(NodeType.WRITE_BOOK, of("Page", ParameterType.INTEGER, "1"));
        registerType(NodeType.USE,
            of("Hand", ParameterType.STRING, "main"),
            of("UseDurationSeconds", ParameterType.DOUBLE, "0.0"),
            of("UseAmount", ParameterType.BOOLEAN, "false"),
            of("RepeatCount", ParameterType.INTEGER, "1"),
            of("UseIntervalSeconds", ParameterType.DOUBLE, "0.0"),
            of("StopIfUnavailable", ParameterType.BOOLEAN, "true"),
            of("UseUntilEmpty", ParameterType.BOOLEAN, "false"),
            of("AllowBlockInteraction", ParameterType.BOOLEAN, "true"),
            of("AllowEntityInteraction", ParameterType.BOOLEAN, "true"),
            of("SwingAfterUse", ParameterType.BOOLEAN, "true"),
            of("SneakWhileUsing", ParameterType.BOOLEAN, "false"),
            of("RestoreSneakState", ParameterType.BOOLEAN, "true"));
        registerType(NodeType.SWING,
            of("Duration", ParameterType.DOUBLE, "0.0"),
            of("UseAmount", ParameterType.BOOLEAN, "false"));
        registerType(NodeType.INTERACT,
            of("Hand", ParameterType.STRING, "main"),
            of("Block", ParameterType.BLOCK_TYPE, ""),
            of("PreferEntity", ParameterType.BOOLEAN, "true"),
            of("PreferBlock", ParameterType.BOOLEAN, "true"),
            of("FallbackToItemUse", ParameterType.BOOLEAN, "true"),
            of("SwingOnSuccess", ParameterType.BOOLEAN, "true"),
            of("SneakWhileInteracting", ParameterType.BOOLEAN, "false"),
            of("RestoreSneakState", ParameterType.BOOLEAN, "true"));
        registerType(NodeType.PLACE_HAND,
            of("Hand", ParameterType.STRING, "main"),
            of("SneakWhilePlacing", ParameterType.BOOLEAN, "false"),
            of("SwingOnPlace", ParameterType.BOOLEAN, "true"),
            of("RequireBlockHit", ParameterType.BOOLEAN, "true"),
            of("RestoreSneakState", ParameterType.BOOLEAN, "true"));
        registerType(NodeType.TRADE,
            of("trade_number", "Number", ParameterType.INTEGER, "1"),
            of("trade_count", "Count", ParameterType.INTEGER, "1"));
        registerType(NodeType.LOOK,
            of("look_yaw", "Yaw", ParameterType.DOUBLE, "0.0"),
            of("look_pitch", "Pitch", ParameterType.DOUBLE, "0.0"));
        registerType(NodeType.WALK,
            of("Duration", ParameterType.DOUBLE, "1.0"),
            of("Distance", ParameterType.DOUBLE, "0.0"));
        registerType(NodeType.PRESS_KEY,
            of("Key", ParameterType.STRING, "GLFW_KEY_SPACE"),
            of("Duration", ParameterType.DOUBLE, "0.0"),
            of("UseAmount", ParameterType.BOOLEAN, "false"));
        registerType(NodeType.CONTROL_REPEAT, of("Count", ParameterType.INTEGER, "10"));
        registerType(NodeType.EVENT_FUNCTION, of("Name", ParameterType.STRING, "function"));
        registerType(NodeType.EVENT_CALL, of("Name", ParameterType.STRING, "function"));
        registerType(NodeType.VARIABLE, of("Variable", ParameterType.STRING, "variable"));
        registerType(NodeType.CREATE_LIST,
            of("List", ParameterType.STRING, "list"),
            dynamic("create_list_use_radius", "UseRadius", ParameterType.BOOLEAN, () -> Boolean.toString(Boolean.TRUE.equals(SettingsManager.getCurrent().createListUseCustomRadius))),
            dynamic("create_list_radius", "Radius", ParameterType.DOUBLE, () -> Double.toString(SettingsManager.getCurrent().createListRadius == null ? 64 : SettingsManager.getCurrent().createListRadius)),
            of("create_list_use_block_cap", "UseBlockCap", ParameterType.BOOLEAN, "false"),
            of("create_list_max_blocks", "MaxBlocks", ParameterType.INTEGER, "256"));
        registerTypes(
            List.of(NodeType.ADD_TO_LIST, NodeType.REMOVE_FIRST_FROM_LIST, NodeType.REMOVE_LAST_FROM_LIST, NodeType.REMOVE_FROM_LIST, NodeType.LIST_LENGTH),
            of("List", ParameterType.STRING, "list"));
        registerTypes(List.of(NodeType.REMOVE_LIST_ITEM, NodeType.LIST_ITEM),
            of("List", ParameterType.STRING, "list"),
            of("Index", ParameterType.INTEGER, "1"));
        registerType(NodeType.OPERATOR_RANDOM,
            of("Min", ParameterType.DOUBLE, "0.0"),
            of("Max", ParameterType.DOUBLE, "1.0"),
            of("Seed", ParameterType.STRING, "Any"),
            of("random_rounding_mode", "Rounding", ParameterType.STRING, "round"),
            of("random_use_rounding", "UseRounding", ParameterType.BOOLEAN, "false"));
        registerTypes(List.of(NodeType.OPERATOR_GREATER, NodeType.OPERATOR_LESS),
            of("Inclusive", ParameterType.BOOLEAN, "false"));
        registerTypes(
            List.of(NodeType.SENSOR_TOUCHING_BLOCK, NodeType.SENSOR_TOUCHING_ENTITY, NodeType.SENSOR_AT_COORDINATES,
                NodeType.SENSOR_IS_DAYTIME, NodeType.SENSOR_IS_RAINING, NodeType.SENSOR_ITEM_IN_INVENTORY,
                NodeType.SENSOR_ITEM_IN_SLOT),
            of("Amount", ParameterType.INTEGER, "1"),
            of("UseAmount", ParameterType.BOOLEAN, "false"));
        registerTypes(List.of(NodeType.SENSOR_VILLAGER_TRADE, NodeType.SENSOR_IN_STOCK),
            of("trade_number", "Number", ParameterType.INTEGER, "1"));
        registerType(NodeType.SENSOR_HEALTH_BELOW, of("Amount", ParameterType.DOUBLE, "10.0"));
        registerType(NodeType.SENSOR_HUNGER_BELOW, of("Amount", ParameterType.INTEGER, "10"));
        registerType(NodeType.SENSOR_IS_FALLING, of("Distance", ParameterType.DOUBLE, "0.25"));
        registerTypes(List.of(NodeType.SENSOR_IS_RENDERED, NodeType.SENSOR_IS_VISIBLE),
            of("Resource", ParameterType.STRING, "stone"));
        registerType(NodeType.SENSOR_CHAT_MESSAGE,
            of("Amount", ParameterType.DOUBLE, "10.0"),
            of("UseAmount", ParameterType.BOOLEAN, "true"));
        registerType(NodeType.SENSOR_FABRIC_EVENT, of("Event", ParameterType.STRING, "Any"));
        registerType(NodeType.SENSOR_ATTRIBUTE_DETECTION,
            of("Attribute", ParameterType.STRING, AttributeDetectionConfig.AttributeOption.NAME.id()),
            of("Value", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_COORDINATE,
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "64"),
            of("Z", ParameterType.INTEGER, "0"));
        registerType(NodeType.PARAM_BLOCK,
            of("Block", ParameterType.STRING, ""),
            of("State", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_ITEM, of("Item", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_VILLAGER_TRADE,
            of("Profession", ParameterType.STRING, "librarian"),
            of("Item", ParameterType.STRING, "book"));
        registerType(NodeType.PARAM_ENTITY,
            of("Entity", ParameterType.STRING, ""),
            of("State", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_PLAYER, of("Player", ParameterType.STRING, "Self"));
        registerType(NodeType.PARAM_MESSAGE, of("Text", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_WAYPOINT,
            of("Waypoint", ParameterType.STRING, "home"),
            of("Range", ParameterType.INTEGER, "10"));
        registerType(NodeType.PARAM_SCHEMATIC,
            of("Schematic", ParameterType.STRING, ""),
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerType(NodeType.PARAM_INVENTORY_SLOT,
            of("inventory_slot_index", "Slot", ParameterType.INTEGER, "0"),
            of("inventory_slot_mode", "Mode", ParameterType.STRING, "player_inventory"));
        registerType(NodeType.PARAM_DURATION, of("Duration", ParameterType.DOUBLE, ""));
        registerType(NodeType.PARAM_AMOUNT, of("Amount", ParameterType.DOUBLE, "1.0"));
        registerType(NodeType.PARAM_BOOLEAN,
            of("boolean_mode", "Mode", ParameterType.STRING, BOOLEAN_MODE_LITERAL),
            of("boolean_toggle", "Toggle", ParameterType.BOOLEAN, "true"),
            of("boolean_variable", "Variable", ParameterType.STRING, ""));
        registerType(NodeType.PARAM_HAND, of("Hand", ParameterType.STRING, "main"));
        registerType(NodeType.PARAM_GUI, of("GUI", ParameterType.STRING, "Any"));
        registerType(NodeType.PARAM_KEY, of("Key", ParameterType.STRING, "GLFW_KEY_SPACE"));
        registerType(NodeType.PARAM_MOUSE_BUTTON, of("MouseButton", ParameterType.STRING, "Left"));
        registerType(NodeType.PARAM_RANGE, of("Range", ParameterType.INTEGER, "6"));
        registerType(NodeType.PARAM_DISTANCE, of("Distance", ParameterType.DOUBLE, "2.0"));
        registerType(NodeType.PARAM_DIRECTION,
            of("direction_mode", "Mode", ParameterType.STRING, DIRECTION_MODE_EXACT),
            of("direction_cardinal", "Direction", ParameterType.STRING, ""),
            of("direction_yaw", "Yaw", ParameterType.DOUBLE, "0.0"),
            of("direction_pitch", "Pitch", ParameterType.DOUBLE, "0.0"),
            of("direction_distance", "Distance", ParameterType.DOUBLE, Double.toString(DEFAULT_DIRECTION_DISTANCE)));
        registerType(NodeType.PARAM_BLOCK_FACE, of("Face", ParameterType.STRING, "north"));
        registerType(NodeType.PARAM_ROTATION,
            of("rotation_yaw", "Yaw", ParameterType.DOUBLE, "0.0"),
            of("rotation_pitch", "Pitch", ParameterType.DOUBLE, "0.0"),
            of("rotation_yaw_offset", "YawOffset", ParameterType.DOUBLE, "0.0"),
            of("rotation_pitch_offset", "PitchOffset", ParameterType.DOUBLE, "0.0"),
            of("rotation_distance", "Distance", ParameterType.DOUBLE, Double.toString(DEFAULT_DIRECTION_DISTANCE)));
        registerType(NodeType.PARAM_PLACE_TARGET,
            of("Block", ParameterType.BLOCK_TYPE, "stone"),
            of("X", ParameterType.INTEGER, "0"),
            of("Y", ParameterType.INTEGER, "0"),
            of("Z", ParameterType.INTEGER, "0"));
        registerType(NodeType.PARAM_CLOSEST, of("Range", ParameterType.INTEGER, "5"));
    }

    private NodeParameterDefinitionRegistry() {
    }

    static void initialize(List<NodeParameter> parameters, NodeType type, NodeMode mode) {
        List<NodeParameterDefinition> definitions = mode != null ? MODE_DEFINITIONS.get(mode) : TYPE_DEFINITIONS.get(type);
        if (definitions == null) {
            return;
        }
        for (NodeParameterDefinition definition : definitions) {
            parameters.add(definition.createParameter());
        }
    }

    static boolean hasDefinitions(NodeType type) {
        return TYPE_DEFINITIONS.containsKey(type);
    }

    static boolean hasDefinitions(NodeMode mode) {
        return MODE_DEFINITIONS.containsKey(mode);
    }

    private static void registerType(NodeType type, NodeParameterDefinition... definitions) {
        TYPE_DEFINITIONS.put(type, List.of(definitions));
    }

    private static void registerTypes(List<NodeType> types, NodeParameterDefinition... definitions) {
        for (NodeType type : types) {
            registerType(type, definitions);
        }
    }

    private static void registerMode(NodeMode mode, NodeParameterDefinition... definitions) {
        MODE_DEFINITIONS.put(mode, List.of(definitions));
    }
}
