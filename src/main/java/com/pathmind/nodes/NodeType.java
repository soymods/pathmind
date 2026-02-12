package com.pathmind.nodes;

import net.minecraft.text.Text;

/**
 * Enum representing different types of nodes in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each type has specific properties and behaviors.
 */
public enum NodeType {
    // Event nodes
    START("pathmind.node.type.start", 0xFF4CAF50, "pathmind.node.type.start.desc"),
    START_CHAIN("pathmind.node.type.startChain", 0xFF4CAF50, "pathmind.node.type.startChain.desc"),
    EVENT_FUNCTION("pathmind.node.type.eventFunction", 0xFFE91E63, "pathmind.node.type.eventFunction.desc"),
    EVENT_CALL("pathmind.node.type.eventCall", 0xFFE91E63, "pathmind.node.type.eventCall.desc"),

    // Variable nodes
    VARIABLE("pathmind.node.type.variable", 0xFFFF9800, "pathmind.node.type.variable.desc"),
    SET_VARIABLE("pathmind.node.type.setVariable", 0xFFFF9800, "pathmind.node.type.setVariable.desc"),
    CHANGE_VARIABLE("pathmind.node.type.changeVariable", 0xFFFF9800, "pathmind.node.type.changeVariable.desc"),
    CREATE_LIST("pathmind.node.type.createList", 0xFFFF9800, "pathmind.node.type.createList.desc"),
    LIST_ITEM("pathmind.node.type.listItem", 0xFFFF9800, "pathmind.node.type.listItem.desc"),

    // Operator nodes
    OPERATOR_EQUALS("pathmind.node.type.operatorEquals", 0xFF00C853, "pathmind.node.type.operatorEquals.desc"),
    OPERATOR_NOT("pathmind.node.type.operatorNot", 0xFF00C853, "pathmind.node.type.operatorNot.desc"),
    OPERATOR_BOOLEAN_NOT("pathmind.node.type.operatorBooleanNot", 0xFF00C853, "pathmind.node.type.operatorBooleanNot.desc"),
    OPERATOR_GREATER("pathmind.node.type.operatorGreater", 0xFF00C853, "pathmind.node.type.operatorGreater.desc"),
    OPERATOR_LESS("pathmind.node.type.operatorLess", 0xFF00C853, "pathmind.node.type.operatorLess.desc"),
    OPERATOR_MOD("pathmind.node.type.operatorMod", 0xFF00C853, "pathmind.node.type.operatorMod.desc"),
    OPERATOR_RANDOM("pathmind.node.type.operatorRandom", 0xFF00C853, "pathmind.node.type.operatorRandom.desc"),

    // Navigation Commands
    GOTO("pathmind.node.type.goto", 0xFF00BCD4, "pathmind.node.type.goto.desc"),
    GOAL("pathmind.node.type.goal", 0xFF2196F3, "pathmind.node.type.goal.desc"),
    PATH("pathmind.node.type.path", 0xFF03DAC6, "pathmind.node.type.path.desc"),
    INVERT("pathmind.node.type.invert", 0xFFFF5722, "pathmind.node.type.invert.desc"),
    COME("pathmind.node.type.come", 0xFF9C27B0, "pathmind.node.type.come.desc"),
    SURFACE("pathmind.node.type.surface", 0xFF4CAF50, "pathmind.node.type.surface.desc"),

    // Resource collection and Building Commands
    COLLECT("pathmind.node.type.collect", 0xFF2196F3, "pathmind.node.type.collect.desc"),
    BUILD("pathmind.node.type.build", 0xFFFF9800, "pathmind.node.type.build.desc"),
    TUNNEL("pathmind.node.type.tunnel", 0xFF795548, "pathmind.node.type.tunnel.desc"),
    FARM("pathmind.node.type.farm", 0xFF4CAF50, "pathmind.node.type.farm.desc"),
    PLACE("pathmind.node.type.place", 0xFF9C27B0, "pathmind.node.type.place.desc"),
    CRAFT("pathmind.node.type.craft", 0xFFFF9800, "pathmind.node.type.craft.desc"),

    // Exploration Commands
    EXPLORE("pathmind.node.type.explore", 0xFF673AB7, "pathmind.node.type.explore.desc"),
    FOLLOW("pathmind.node.type.follow", 0xFF3F51B5, "pathmind.node.type.follow.desc"),

    // Control flow Commands
    CONTROL_REPEAT("pathmind.node.type.controlRepeat", 0xFFFFC107, "pathmind.node.type.controlRepeat.desc"),
    CONTROL_REPEAT_UNTIL("pathmind.node.type.controlRepeatUntil", 0xFFFFC107, "pathmind.node.type.controlRepeatUntil.desc"),
    CONTROL_FOREVER("pathmind.node.type.controlForever", 0xFFFFC107, "pathmind.node.type.controlForever.desc"),
    CONTROL_IF("pathmind.node.type.controlIf", 0xFFFFC107, "pathmind.node.type.controlIf.desc"),
    CONTROL_IF_ELSE("pathmind.node.type.controlIfElse", 0xFFFFC107, "pathmind.node.type.controlIfElse.desc"),

    // Player movement commands
    LOOK("pathmind.node.type.look", 0xFF03A9F4, "pathmind.node.type.look.desc"),
    WALK("pathmind.node.type.walk", 0xFF26C6DA, "pathmind.node.type.walk.desc"),
    JUMP("pathmind.node.type.jump", 0xFF009688, "pathmind.node.type.jump.desc"),
    CRAWL("pathmind.node.type.crawl", 0xFF455A64, "pathmind.node.type.crawl.desc"),
    CROUCH("pathmind.node.type.crouch", 0xFF607D8B, "pathmind.node.type.crouch.desc"),
    SPRINT("pathmind.node.type.sprint", 0xFFFFEB3B, "pathmind.node.type.sprint.desc"),
    FLY("pathmind.node.type.fly", 0xFF29B6F6, "pathmind.node.type.fly.desc"),
    STOP("pathmind.node.type.stop", 0xFFF44336, "pathmind.node.type.stop.desc"),

    // Player combat commands
    SWING("pathmind.node.type.swing", 0xFFFF7043, "pathmind.node.type.swing.desc"),

    // Player interaction commands
    USE("pathmind.node.type.use", 0xFF8BC34A, "pathmind.node.type.use.desc"),
    INTERACT("pathmind.node.type.interact", 0xFF4DB6AC, "pathmind.node.type.interact.desc"),
    BREAK("pathmind.node.type.break", 0xFF4DB6AC, "pathmind.node.type.break.desc"),
    PLACE_HAND("pathmind.node.type.placeHand", 0xFFBA68C8, "pathmind.node.type.placeHand.desc"),
    TRADE("pathmind.node.type.trade", 0xFF7E57C2, "pathmind.node.type.trade.desc"),

    // GUI Commands
    HOTBAR("pathmind.node.type.hotbar", 0xFFCDDC39, "pathmind.node.type.hotbar.desc"),
    DROP_ITEM("pathmind.node.type.dropItem", 0xFFFFAB91, "pathmind.node.type.dropItem.desc"),
    DROP_SLOT("pathmind.node.type.dropSlot", 0xFFFF7043, "pathmind.node.type.dropSlot.desc"),
    CLICK_SLOT("pathmind.node.type.clickSlot", 0xFFFF7043, "pathmind.node.type.clickSlot.desc"),
    MOVE_ITEM("pathmind.node.type.moveItem", 0xFFFFB74D, "pathmind.node.type.moveItem.desc"),
    OPEN_INVENTORY("pathmind.node.type.openInventory", 0xFFB0BEC5, "pathmind.node.type.openInventory.desc"),
    CLOSE_GUI("pathmind.node.type.closeGui", 0xFFB0BEC5, "pathmind.node.type.closeGui.desc"),
    WRITE_BOOK("pathmind.node.type.writeBook", 0xFFB0BEC5, "pathmind.node.type.writeBook.desc"),
    UI_UTILS("pathmind.node.type.uiUtils", 0xFFB0BEC5, "pathmind.node.type.uiUtils.desc"),

    // Equipment Commands
    EQUIP_ARMOR("pathmind.node.type.equipArmor", 0xFF7E57C2, "pathmind.node.type.equipArmor.desc"),
    EQUIP_HAND("pathmind.node.type.equipHand", 0xFF5C6BC0, "pathmind.node.type.equipHand.desc"),

    // Sensor commands
    SENSOR_TOUCHING_BLOCK("pathmind.node.type.sensorTouchingBlock", 0xFF64B5F6, "pathmind.node.type.sensorTouchingBlock.desc"),
    SENSOR_TOUCHING_ENTITY("pathmind.node.type.sensorTouchingEntity", 0xFF64B5F6, "pathmind.node.type.sensorTouchingEntity.desc"),
    SENSOR_AT_COORDINATES("pathmind.node.type.sensorAtCoordinates", 0xFF64B5F6, "pathmind.node.type.sensorAtCoordinates.desc"),
    SENSOR_POSITION_OF("pathmind.node.type.sensorPositionOf", 0xFF64B5F6, "pathmind.node.type.sensorPositionOf.desc"),
    SENSOR_DISTANCE_BETWEEN("pathmind.node.type.sensorDistanceBetween", 0xFF64B5F6, "pathmind.node.type.sensorDistanceBetween.desc"),
    SENSOR_TARGETED_BLOCK("pathmind.node.type.sensorTargetedBlock", 0xFF64B5F6, "pathmind.node.type.sensorTargetedBlock.desc"),
    SENSOR_LOOK_DIRECTION("pathmind.node.type.sensorLookDirection", 0xFF64B5F6, "pathmind.node.type.sensorLookDirection.desc"),
    SENSOR_TARGETED_BLOCK_FACE("pathmind.node.type.sensorTargetedBlockFace", 0xFF64B5F6, "pathmind.node.type.sensorTargetedBlockFace.desc"),
    SENSOR_GUI_FILLED("pathmind.node.type.sensorGuiFilled", 0xFF64B5F6, "pathmind.node.type.sensorGuiFilled.desc"),
    SENSOR_IS_DAYTIME("pathmind.node.type.sensorIsDaytime", 0xFF64B5F6, "pathmind.node.type.sensorIsDaytime.desc"),
    SENSOR_IS_RAINING("pathmind.node.type.sensorIsRaining", 0xFF64B5F6, "pathmind.node.type.sensorIsRaining.desc"),
    SENSOR_HEALTH_BELOW("pathmind.node.type.sensorHealthBelow", 0xFF64B5F6, "pathmind.node.type.sensorHealthBelow.desc"),
    SENSOR_HUNGER_BELOW("pathmind.node.type.sensorHungerBelow", 0xFF64B5F6, "pathmind.node.type.sensorHungerBelow.desc"),
    SENSOR_ITEM_IN_INVENTORY("pathmind.node.type.sensorItemInInventory", 0xFF64B5F6, "pathmind.node.type.sensorItemInInventory.desc"),
    SENSOR_ITEM_IN_SLOT("pathmind.node.type.sensorItemInSlot", 0xFF64B5F6, "pathmind.node.type.sensorItemInSlot.desc"),
    SENSOR_VILLAGER_TRADE("pathmind.node.type.sensorVillagerTrade", 0xFF64B5F6, "pathmind.node.type.sensorVillagerTrade.desc"),
    SENSOR_IS_SWIMMING("pathmind.node.type.sensorIsSwimming", 0xFF64B5F6, "pathmind.node.type.sensorIsSwimming.desc"),
    SENSOR_IS_IN_LAVA("pathmind.node.type.sensorIsInLava", 0xFF64B5F6, "pathmind.node.type.sensorIsInLava.desc"),
    SENSOR_IS_UNDERWATER("pathmind.node.type.sensorIsUnderwater", 0xFF64B5F6, "pathmind.node.type.sensorIsUnderwater.desc"),
    SENSOR_IS_ON_GROUND("pathmind.node.type.sensorIsOnGround", 0xFF64B5F6, "pathmind.node.type.sensorIsOnGround.desc"),
    SENSOR_IS_FALLING("pathmind.node.type.sensorIsFalling", 0xFF64B5F6, "pathmind.node.type.sensorIsFalling.desc"),
    SENSOR_IS_RENDERED("pathmind.node.type.sensorIsRendered", 0xFF64B5F6, "pathmind.node.type.sensorIsRendered.desc"),
    SENSOR_IS_VISIBLE("pathmind.node.type.sensorIsVisible", 0xFF64B5F6, "pathmind.node.type.sensorIsVisible.desc"),
    SENSOR_KEY_PRESSED("pathmind.node.type.sensorKeyPressed", 0xFF64B5F6, "pathmind.node.type.sensorKeyPressed.desc"),
    SENSOR_CHAT_MESSAGE("pathmind.node.type.sensorChatMessage", 0xFF64B5F6, "pathmind.node.type.sensorChatMessage.desc"),

    // Utility Commands
    SCREEN_CONTROL("pathmind.node.type.screenControl", 0xFF9E9E9E, "pathmind.node.type.screenControl.desc"),
    WAIT("pathmind.node.type.wait", 0xFF607D8B, "pathmind.node.type.wait.desc"),
    MESSAGE("pathmind.node.type.message", 0xFF9E9E9E, "pathmind.node.type.message.desc"),
    STOP_CHAIN("pathmind.node.type.stopChain", 0xFFE53935, "pathmind.node.type.stopChain.desc"),
    STOP_ALL("pathmind.node.type.stopAll", 0xFFE53935, "pathmind.node.type.stopAll.desc"),

    // Parameter nodes
    PARAM_COORDINATE("pathmind.node.type.paramCoordinate", 0xFF8BC34A, "pathmind.node.type.paramCoordinate.desc"),
    PARAM_BLOCK("pathmind.node.type.paramBlock", 0xFF8BC34A, "pathmind.node.type.paramBlock.desc"),
    PARAM_ITEM("pathmind.node.type.paramItem", 0xFF8BC34A, "pathmind.node.type.paramItem.desc"),
    PARAM_VILLAGER_TRADE("pathmind.node.type.paramVillagerTrade", 0xFF8BC34A, "pathmind.node.type.paramVillagerTrade.desc"),
    PARAM_ENTITY("pathmind.node.type.paramEntity", 0xFF8BC34A, "pathmind.node.type.paramEntity.desc"),
    PARAM_PLAYER("pathmind.node.type.paramPlayer", 0xFF8BC34A, "pathmind.node.type.paramPlayer.desc"),
    PARAM_WAYPOINT("pathmind.node.type.paramWaypoint", 0xFF8BC34A, "pathmind.node.type.paramWaypoint.desc"),
    PARAM_SCHEMATIC("pathmind.node.type.paramSchematic", 0xFF8BC34A, "pathmind.node.type.paramSchematic.desc"),
    PARAM_INVENTORY_SLOT("pathmind.node.type.paramInventorySlot", 0xFF8BC34A, "pathmind.node.type.paramInventorySlot.desc"),
    PARAM_MESSAGE("pathmind.node.type.paramMessage", 0xFF8BC34A, "pathmind.node.type.paramMessage.desc"),
    PARAM_DURATION("pathmind.node.type.paramDuration", 0xFF8BC34A, "pathmind.node.type.paramDuration.desc"),
    PARAM_AMOUNT("pathmind.node.type.paramAmount", 0xFF8BC34A, "pathmind.node.type.paramAmount.desc"),
    PARAM_BOOLEAN("pathmind.node.type.paramBoolean", 0xFF8BC34A, "pathmind.node.type.paramBoolean.desc"),
    PARAM_HAND("pathmind.node.type.paramHand", 0xFF8BC34A, "pathmind.node.type.paramHand.desc"),
    PARAM_GUI("pathmind.node.type.paramGui", 0xFF8BC34A, "pathmind.node.type.paramGui.desc"),
    PARAM_KEY("pathmind.node.type.paramKey", 0xFF8BC34A, "pathmind.node.type.paramKey.desc"),
    PARAM_RANGE("pathmind.node.type.paramRange", 0xFF8BC34A, "pathmind.node.type.paramRange.desc"),
    PARAM_DISTANCE("pathmind.node.type.paramDistance", 0xFF8BC34A, "pathmind.node.type.paramDistance.desc"),
    PARAM_DIRECTION("pathmind.node.type.paramDirection", 0xFF8BC34A, "pathmind.node.type.paramDirection.desc"),
    PARAM_ROTATION("pathmind.node.type.paramRotation", 0xFF8BC34A, "pathmind.node.type.paramRotation.desc"),
    PARAM_PLACE_TARGET("pathmind.node.type.paramPlaceTarget", 0xFF8BC34A, "pathmind.node.type.paramPlaceTarget.desc"),
    PARAM_CLOSEST("pathmind.node.type.paramClosest", 0xFF8BC34A, "pathmind.node.type.paramClosest.desc");

    private final String translationKey;
    private final int baseColor;
    private final String descriptionKey;

    NodeType(String translationKey, int color, String descriptionKey) {
        this.translationKey = translationKey;
        this.baseColor = color;
        this.descriptionKey = descriptionKey;
    }

    public String getDisplayName() {
        return Text.translatable(translationKey).getString();
    }

    public int getColor() {
        // Special nodes keep their original colors
        if (this == START || this == START_CHAIN) {
            return baseColor; // Green
        }
        if (this == STOP_CHAIN || this == STOP_ALL) {
            return baseColor; // Bright red for stop controls
        }
        return getCategory().getColor();
    }

    public String getDescription() {
        return Text.translatable(descriptionKey).getString();
    }

    public boolean isInputNode() {
        return this == START;
    }

    public boolean isOutputNode() {
        return false;
    }

    public boolean isDraggableFromSidebar() {
        if (this == STOP || this == PLACE_HAND) {
            return false;
        }
        return true;
    }
    
    /**
     * Get the category this node belongs to for sidebar organization
     */
    public NodeCategory getCategory() {
        switch (this) {
            case START:
            case EVENT_FUNCTION:
            case EVENT_CALL:
            case START_CHAIN:
                return NodeCategory.EVENTS;
            case VARIABLE:
            case SET_VARIABLE:
            case CHANGE_VARIABLE:
            case CREATE_LIST:
            case LIST_ITEM:
                return NodeCategory.VARIABLES;
            case OPERATOR_EQUALS:
            case OPERATOR_NOT:
            case OPERATOR_BOOLEAN_NOT:
            case OPERATOR_GREATER:
            case OPERATOR_LESS:
            case OPERATOR_MOD:
            case OPERATOR_RANDOM:
                return NodeCategory.OPERATORS;
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_FOREVER:
            case CONTROL_IF:
            case CONTROL_IF_ELSE:
                return NodeCategory.LOGIC;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_POSITION_OF:
            case SENSOR_DISTANCE_BETWEEN:
            case SENSOR_TARGETED_BLOCK:
            case SENSOR_LOOK_DIRECTION:
            case SENSOR_TARGETED_BLOCK_FACE:
            case SENSOR_GUI_FILLED:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_ON_GROUND:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case SENSOR_IS_VISIBLE:
            case SENSOR_KEY_PRESSED:
            case SENSOR_CHAT_MESSAGE:
                return NodeCategory.SENSORS;
            case GOTO:
            case GOAL:
            case PATH:
            case INVERT:
            case COME:
            case SURFACE:
            case EXPLORE:
            case FOLLOW:
            case WALK:
            case JUMP:
            case CRAWL:
            case CROUCH:
            case SPRINT:
            case FLY:
                return NodeCategory.MOVEMENT;
            case COLLECT:
            case BUILD:
            case TUNNEL:
            case FARM:
            case PLACE:
            case CRAFT:
                return NodeCategory.INTERACTION;
            case LOOK:
            case SWING:
            case USE:
            case INTERACT:
            case BREAK:
            case PLACE_HAND:
            case TRADE:
                return NodeCategory.INTERACTION;
            case HOTBAR:
            case DROP_ITEM:
            case DROP_SLOT:
            case CLICK_SLOT:
            case MOVE_ITEM:
            case OPEN_INVENTORY:
            case CLOSE_GUI:
            case WRITE_BOOK:
            case EQUIP_ARMOR:
            case EQUIP_HAND:
                return NodeCategory.GUI;
            case SCREEN_CONTROL:
            case WAIT:
            case MESSAGE:
            case UI_UTILS:
                return NodeCategory.UTILITY;
            case STOP_CHAIN:
            case STOP_ALL:
                return NodeCategory.EVENTS;
            case PARAM_COORDINATE:
            case PARAM_BLOCK:
            case PARAM_ITEM:
            case PARAM_VILLAGER_TRADE:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_WAYPOINT:
            case PARAM_SCHEMATIC:
            case PARAM_INVENTORY_SLOT:
            case PARAM_DURATION:
            case PARAM_AMOUNT:
            case PARAM_BOOLEAN:
            case PARAM_HAND:
            case PARAM_GUI:
            case PARAM_KEY:
            case PARAM_RANGE:
            case PARAM_DISTANCE:
            case PARAM_DIRECTION:
            case PARAM_ROTATION:
            case PARAM_PLACE_TARGET:
            case PARAM_CLOSEST:
            case PARAM_MESSAGE:
                return NodeCategory.PARAMETERS;
            default:
                return NodeCategory.UTILITY;
        }
    }
    
    /**
     * Check if this node type requires parameters
     */
    public boolean hasParameters() {
        switch (this) {
            case EVENT_FUNCTION:
            case EVENT_CALL:
            case GOTO:
            case GOAL:
            case COLLECT:
            case PLACE:
            case CRAFT:
            case BUILD:
            case EXPLORE:
            case FOLLOW:
            case WAIT:
            case HOTBAR:
            case DROP_ITEM:
            case USE:
            case LOOK:
            case WALK:
            case CRAWL:
            case CROUCH:
            case SPRINT:
            case FLY:
            case INTERACT:
            case PLACE_HAND:
            case TRADE:
            case DROP_SLOT:
            case CLICK_SLOT:
            case MOVE_ITEM:
            case EQUIP_ARMOR:
            case EQUIP_HAND:
            case CLOSE_GUI:
            case WRITE_BOOK:
            case UI_UTILS:
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_IF_ELSE:
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_ITEM_IN_SLOT:
            case SENSOR_VILLAGER_TRADE:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case SENSOR_IS_VISIBLE:
            case SENSOR_CHAT_MESSAGE:
            case PARAM_COORDINATE:
            case PARAM_BLOCK:
            case PARAM_ITEM:
            case PARAM_VILLAGER_TRADE:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_WAYPOINT:
            case PARAM_SCHEMATIC:
            case PARAM_INVENTORY_SLOT:
            case PARAM_DURATION:
            case PARAM_AMOUNT:
            case PARAM_BOOLEAN:
            case PARAM_HAND:
            case PARAM_GUI:
            case PARAM_KEY:
            case PARAM_RANGE:
            case PARAM_DISTANCE:
            case PARAM_DIRECTION:
            case PARAM_ROTATION:
            case PARAM_PLACE_TARGET:
            case PARAM_CLOSEST:
            case PARAM_MESSAGE:
            case VARIABLE:
            case OPERATOR_RANDOM:
            case OPERATOR_MOD:
            case OPERATOR_BOOLEAN_NOT:
            case CREATE_LIST:
            case LIST_ITEM:
                return true;
            default:
                return false;
        }
    }

    public boolean requiresBaritone() {
        switch (this) {
            case GOTO:
            case GOAL:
            case PATH:
            case INVERT:
            case COME:
            case SURFACE:
            case COLLECT:
            case BUILD:
            case TUNNEL:
            case FARM:
            case EXPLORE:
            case FOLLOW:
            case STOP:
            case PARAM_WAYPOINT:
            case PARAM_SCHEMATIC:
                return true;
            default:
                return false;
        }
    }

    public boolean requiresUiUtils() {
        switch (this) {
            case UI_UTILS:
                return true;
            default:
                return false;
        }
    }
}
