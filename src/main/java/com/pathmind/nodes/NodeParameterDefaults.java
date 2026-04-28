package com.pathmind.nodes;

import com.pathmind.data.SettingsManager;
import java.util.List;

final class NodeParameterDefaults {
    private static final String BOOLEAN_MODE_LITERAL = "literal";
    private static final String DIRECTION_MODE_EXACT = "exact";
    private static final double DEFAULT_DIRECTION_DISTANCE = 16.0;

    private NodeParameterDefaults() {
    }

    static void initialize(List<NodeParameter> parameters, NodeType type, NodeMode mode) {
        if (mode != null) {
            initializeForMode(parameters, mode);
            return;
        }
        initializeForType(parameters, type);
    }

    private static void initializeForMode(List<NodeParameter> parameters, NodeMode mode) {
        switch (mode) {
            case GOTO_XYZ:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case GOTO_XZ:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case GOTO_Y:
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                break;
            case GOTO_BLOCK:
                parameters.add(new NodeParameter("Block", ParameterType.STRING, "stone"));
                break;
            case GOAL_XYZ:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case GOAL_XZ:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case GOAL_Y:
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                break;
            case GOAL_CURRENT:
            case GOAL_CLEAR:
                break;
            case COLLECT_SINGLE:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                break;
            case COLLECT_MULTIPLE:
                parameters.add(new NodeParameter("Blocks", ParameterType.STRING, "stone,dirt"));
                break;
            case BUILD_PLAYER:
                parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                break;
            case BUILD_XYZ:
                parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case EXPLORE_CURRENT:
                break;
            case EXPLORE_XYZ:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case EXPLORE_FILTER:
                parameters.add(new NodeParameter("Filter", ParameterType.STRING, "explore.txt"));
                break;
            case FOLLOW_PLAYER:
                parameters.add(new NodeParameter("Player", ParameterType.STRING, "Self"));
                break;
            case FOLLOW_PLAYERS:
            case FOLLOW_ENTITIES:
                break;
            case FOLLOW_ENTITY_TYPE:
                parameters.add(new NodeParameter("Entity", ParameterType.STRING, "cow"));
                break;
            case CRAFT_PLAYER_GUI:
            case CRAFT_CRAFTING_TABLE:
                parameters.add(new NodeParameter("Item", ParameterType.STRING, "stick"));
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                break;
            case FARM_RANGE:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                break;
            case FARM_WAYPOINT:
                parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "farm"));
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                break;
            case STOP_NORMAL:
            case STOP_CANCEL:
            case STOP_FORCE:
                break;
            case WAIT_SECONDS:
            case WAIT_TICKS:
            case WAIT_MINUTES:
            case WAIT_HOURS:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, ""));
                break;
            case UI_UTILS_SET_SEND_PACKETS:
            case UI_UTILS_SET_DELAY_PACKETS:
            case UI_UTILS_SET_ENABLED:
            case UI_UTILS_SET_BYPASS_RESOURCE_PACK:
            case UI_UTILS_SET_FORCE_DENY_RESOURCE_PACK:
                parameters.add(new NodeParameter("Enabled", ParameterType.BOOLEAN, "true"));
                break;
            case UI_UTILS_ENABLE_DELAY_PACKETS:
            case UI_UTILS_DISABLE_DELAY_PACKETS:
                break;
            case UI_UTILS_FABRICATE_CLICK_SLOT:
                parameters.add(parameter("ui_click_sync_id", "SyncId", ParameterType.INTEGER, "-1"));
                parameters.add(parameter("ui_click_revision", "Revision", ParameterType.INTEGER, "-1"));
                parameters.add(parameter("ui_click_slot", "Slot", ParameterType.INTEGER, "0"));
                parameters.add(parameter("ui_click_button", "Button", ParameterType.INTEGER, "0"));
                parameters.add(parameter("ui_click_action", "Action", ParameterType.STRING, "PICKUP"));
                parameters.add(parameter("ui_click_times", "TimesToSend", ParameterType.INTEGER, "1"));
                parameters.add(parameter("ui_click_delay", "Delay", ParameterType.BOOLEAN, "false"));
                break;
            case UI_UTILS_FABRICATE_BUTTON_CLICK:
                parameters.add(parameter("ui_button_sync_id", "SyncId", ParameterType.INTEGER, "-1"));
                parameters.add(parameter("ui_button_id", "ButtonId", ParameterType.INTEGER, "0"));
                parameters.add(parameter("ui_button_times", "TimesToSend", ParameterType.INTEGER, "1"));
                parameters.add(parameter("ui_button_delay", "Delay", ParameterType.BOOLEAN, "false"));
                break;
            default:
                break;
        }
    }

    private static void initializeForType(List<NodeParameter> parameters, NodeType type) {
        switch (type) {
            case PLACE:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case WAIT:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "0.0"));
                break;
            case START_CHAIN:
                parameters.add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
                break;
            case RUN_PRESET:
            case CUSTOM_NODE:
            case TEMPLATE:
                parameters.add(new NodeParameter("Preset", ParameterType.STRING, ""));
                break;
            case STOP_CHAIN:
                parameters.add(new NodeParameter("StartNumber", ParameterType.INTEGER, ""));
                break;
            case HOTBAR:
                parameters.add(parameter("hotbar_slot", "Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Item", ParameterType.STRING, ""));
                break;
            case DROP_ITEM:
                parameters.add(new NodeParameter("All", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("IntervalSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case DROP_SLOT:
                parameters.add(parameter("drop_slot_index", "Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("EntireStack", ParameterType.BOOLEAN, "true"));
                break;
            case CLICK_SLOT:
                parameters.add(parameter("click_slot_index", "Slot", ParameterType.INTEGER, "0"));
                break;
            case CLICK_SCREEN:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                break;
            case MOVE_ITEM:
                parameters.add(parameter("move_item_source_slot", "SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(parameter("move_item_target_slot", "TargetSlot", ParameterType.INTEGER, "9"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                break;
            case EQUIP_ARMOR:
                parameters.add(parameter("equip_armor_source_slot", "SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(parameter("equip_armor_slot", "ArmorSlot", ParameterType.STRING, "head"));
                break;
            case EQUIP_HAND:
                parameters.add(parameter("equip_hand_source_slot", "SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(parameter("equip_hand_hand", "Hand", ParameterType.STRING, "main"));
                break;
            case WRITE_BOOK:
                parameters.add(new NodeParameter("Page", ParameterType.INTEGER, "1"));
                break;
            case WRITE_SIGN:
                break;
            case USE:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("UseDurationSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RepeatCount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseIntervalSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("StopIfUnavailable", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("UseUntilEmpty", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("AllowBlockInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AllowEntityInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingAfterUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileUsing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case SWING:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                break;
            case INTERACT:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, ""));
                parameters.add(new NodeParameter("PreferEntity", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("PreferBlock", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("FallbackToItemUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingOnSuccess", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileInteracting", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case PLACE_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("SneakWhilePlacing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("SwingOnPlace", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RequireBlockHit", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case TRADE:
                parameters.add(parameter("trade_number", "Number", ParameterType.INTEGER, "1"));
                parameters.add(parameter("trade_count", "Count", ParameterType.INTEGER, "1"));
                break;
            case LOOK:
                parameters.add(parameter("look_yaw", "Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("look_pitch", "Pitch", ParameterType.DOUBLE, "0.0"));
                break;
            case WALK:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "0.0"));
                break;
            case PRESS_KEY:
                parameters.add(new NodeParameter("Key", ParameterType.STRING, "GLFW_KEY_SPACE"));
                break;
            case CRAWL:
            case CROUCH:
            case SPRINT:
            case FLY:
                break;
            case CONTROL_REPEAT:
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "10"));
                break;
            case CONTROL_WAIT_UNTIL:
            case CONTROL_IF:
            case CONTROL_IF_ELSE:
            case CONTROL_FORK:
            case CONTROL_JOIN_ANY:
            case CONTROL_JOIN_ALL:
                break;
            case EVENT_FUNCTION:
            case EVENT_CALL:
                parameters.add(new NodeParameter("Name", ParameterType.STRING, "function"));
                break;
            case VARIABLE:
                parameters.add(new NodeParameter("Variable", ParameterType.STRING, "variable"));
                break;
            case CREATE_LIST:
                SettingsManager.Settings createListSettings = SettingsManager.getCurrent();
                parameters.add(new NodeParameter("List", ParameterType.STRING, "list"));
                parameters.add(parameter("create_list_use_radius", "UseRadius", ParameterType.BOOLEAN,
                    Boolean.toString(Boolean.TRUE.equals(createListSettings.createListUseCustomRadius))));
                parameters.add(parameter("create_list_radius", "Radius", ParameterType.DOUBLE,
                    Double.toString(createListSettings.createListRadius == null ? 64 : createListSettings.createListRadius)));
                parameters.add(parameter("create_list_use_block_cap", "UseBlockCap", ParameterType.BOOLEAN, "false"));
                parameters.add(parameter("create_list_max_blocks", "MaxBlocks", ParameterType.INTEGER, "256"));
                break;
            case ADD_TO_LIST:
            case REMOVE_FIRST_FROM_LIST:
            case REMOVE_LAST_FROM_LIST:
            case REMOVE_FROM_LIST:
            case LIST_LENGTH:
                parameters.add(new NodeParameter("List", ParameterType.STRING, "list"));
                break;
            case REMOVE_LIST_ITEM:
            case LIST_ITEM:
                parameters.add(new NodeParameter("List", ParameterType.STRING, "list"));
                parameters.add(new NodeParameter("Index", ParameterType.INTEGER, "1"));
                break;
            case OPERATOR_RANDOM:
                parameters.add(new NodeParameter("Min", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Max", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("Seed", ParameterType.STRING, "Any"));
                parameters.add(parameter("random_rounding_mode", "Rounding", ParameterType.STRING, "round"));
                parameters.add(parameter("random_use_rounding", "UseRounding", ParameterType.BOOLEAN, "false"));
                break;
            case OPERATOR_MOD:
            case OPERATOR_BOOLEAN_NOT:
            case OPERATOR_BOOLEAN_OR:
            case OPERATOR_BOOLEAN_AND:
            case OPERATOR_BOOLEAN_XOR:
                break;
            case OPERATOR_GREATER:
            case OPERATOR_LESS:
                parameters.add(new NodeParameter("Inclusive", ParameterType.BOOLEAN, "false"));
                break;
            case CHANGE_VARIABLE:
                parameters.add(parameter("change_variable_amount", "Amount", ParameterType.INTEGER, "1"));
                parameters.add(parameter("change_variable_operation", "Operation", ParameterType.STRING, "+"));
                break;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "false"));
                break;
            case SENSOR_SLOT_ITEM_COUNT:
                break;
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IN_STOCK:
                parameters.add(parameter("trade_number", "Number", ParameterType.INTEGER, "1"));
                break;
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_KEY_PRESSED:
                break;
            case SENSOR_HEALTH_BELOW:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "10.0"));
                break;
            case SENSOR_HUNGER_BELOW:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "10"));
                break;
            case SENSOR_IS_FALLING:
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "0.25"));
                break;
            case SENSOR_IS_RENDERED:
            case SENSOR_IS_VISIBLE:
                parameters.add(new NodeParameter("Resource", ParameterType.STRING, "stone"));
                break;
            case SENSOR_CHAT_MESSAGE:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "10.0"));
                parameters.add(new NodeParameter("UseAmount", ParameterType.BOOLEAN, "true"));
                break;
            case SENSOR_JOINED_SERVER:
                break;
            case SENSOR_FABRIC_EVENT:
                parameters.add(new NodeParameter("Event", ParameterType.STRING, "Any"));
                break;
            case SENSOR_ATTRIBUTE_DETECTION:
                parameters.add(new NodeParameter("Attribute", ParameterType.STRING, AttributeDetectionConfig.AttributeOption.NAME.id()));
                parameters.add(new NodeParameter("Value", ParameterType.STRING, ""));
                break;
            case PARAM_COORDINATE:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_BLOCK:
                parameters.add(new NodeParameter("Block", ParameterType.STRING, ""));
                parameters.add(new NodeParameter("State", ParameterType.STRING, ""));
                break;
            case PARAM_ITEM:
                parameters.add(new NodeParameter("Item", ParameterType.STRING, ""));
                break;
            case PARAM_VILLAGER_TRADE:
                parameters.add(new NodeParameter("Profession", ParameterType.STRING, "librarian"));
                parameters.add(new NodeParameter("Item", ParameterType.STRING, "book"));
                break;
            case PARAM_ENTITY:
                parameters.add(new NodeParameter("Entity", ParameterType.STRING, ""));
                parameters.add(new NodeParameter("State", ParameterType.STRING, ""));
                break;
            case PARAM_PLAYER:
                parameters.add(new NodeParameter("Player", ParameterType.STRING, "Self"));
                break;
            case PARAM_MESSAGE:
                parameters.add(new NodeParameter("Text", ParameterType.STRING, ""));
                break;
            case PARAM_WAYPOINT:
                parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "home"));
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                break;
            case PARAM_SCHEMATIC:
                parameters.add(new NodeParameter("Schematic", ParameterType.STRING, ""));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_INVENTORY_SLOT:
                parameters.add(parameter("inventory_slot_index", "Slot", ParameterType.INTEGER, "0"));
                parameters.add(parameter("inventory_slot_mode", "Mode", ParameterType.STRING, "player_inventory"));
                break;
            case PARAM_DURATION:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, ""));
                break;
            case PARAM_AMOUNT:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "1.0"));
                break;
            case PARAM_BOOLEAN:
                parameters.add(parameter("boolean_mode", "Mode", ParameterType.STRING, BOOLEAN_MODE_LITERAL));
                parameters.add(parameter("boolean_toggle", "Toggle", ParameterType.BOOLEAN, "true"));
                parameters.add(parameter("boolean_variable", "Variable", ParameterType.STRING, ""));
                break;
            case PARAM_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                break;
            case PARAM_GUI:
                parameters.add(new NodeParameter("GUI", ParameterType.STRING, "Any"));
                break;
            case PARAM_KEY:
                parameters.add(new NodeParameter("Key", ParameterType.STRING, "GLFW_KEY_SPACE"));
                break;
            case PARAM_MOUSE_BUTTON:
                parameters.add(new NodeParameter("MouseButton", ParameterType.STRING, "Left"));
                break;
            case PARAM_RANGE:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "6"));
                break;
            case PARAM_DISTANCE:
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "2.0"));
                break;
            case PARAM_DIRECTION:
                parameters.add(parameter("direction_mode", "Mode", ParameterType.STRING, DIRECTION_MODE_EXACT));
                parameters.add(parameter("direction_cardinal", "Direction", ParameterType.STRING, ""));
                parameters.add(parameter("direction_yaw", "Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("direction_pitch", "Pitch", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("direction_yaw_offset", "YawOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("direction_pitch_offset", "PitchOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("direction_distance", "Distance", ParameterType.DOUBLE, Double.toString(DEFAULT_DIRECTION_DISTANCE)));
                break;
            case PARAM_BLOCK_FACE:
                parameters.add(new NodeParameter("Face", ParameterType.STRING, "north"));
                break;
            case PARAM_ROTATION:
                parameters.add(parameter("rotation_yaw", "Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("rotation_pitch", "Pitch", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("rotation_yaw_offset", "YawOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("rotation_pitch_offset", "PitchOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(parameter("rotation_distance", "Distance", ParameterType.DOUBLE, Double.toString(DEFAULT_DIRECTION_DISTANCE)));
                break;
            case PARAM_PLACE_TARGET:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_CLOSEST:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "5"));
                break;
            default:
                break;
        }
    }

    private static NodeParameter parameter(String id, String name, ParameterType type, String defaultValue) {
        return new NodeParameter(id, name, type, defaultValue);
    }
}
