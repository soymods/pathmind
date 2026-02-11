package com.pathmind.nodes;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class NodeTraitRegistry {
    private static final EnumSet<NodeType> BOOLEAN_SENSORS = EnumSet.of(
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
        NodeType.SENSOR_IS_SWIMMING,
        NodeType.SENSOR_IS_IN_LAVA,
        NodeType.SENSOR_IS_UNDERWATER,
        NodeType.SENSOR_IS_ON_GROUND,
        NodeType.SENSOR_IS_FALLING,
        NodeType.SENSOR_IS_RENDERED,
        NodeType.SENSOR_KEY_PRESSED,
        NodeType.SENSOR_CHAT_MESSAGE,
        NodeType.SENSOR_GUI_FILLED,
        NodeType.OPERATOR_EQUALS,
        NodeType.OPERATOR_NOT,
        NodeType.OPERATOR_BOOLEAN_NOT,
        NodeType.OPERATOR_GREATER,
        NodeType.OPERATOR_LESS
    );
    private static final EnumSet<NodeType> SENSORS_WITHOUT_PARAMETER_SLOT = EnumSet.of(
        NodeType.SENSOR_IS_DAYTIME,
        NodeType.SENSOR_IS_RAINING,
        NodeType.SENSOR_IS_SWIMMING,
        NodeType.SENSOR_IS_IN_LAVA,
        NodeType.SENSOR_IS_UNDERWATER,
        NodeType.SENSOR_IS_ON_GROUND,
        NodeType.SENSOR_IS_FALLING,
        NodeType.SENSOR_HEALTH_BELOW,
        NodeType.SENSOR_HUNGER_BELOW,
        NodeType.SENSOR_GUI_FILLED,
        NodeType.SENSOR_TARGETED_BLOCK,
        NodeType.SENSOR_TARGETED_BLOCK_FACE,
        NodeType.SENSOR_LOOK_DIRECTION
    );
    private static final EnumSet<NodeType> SENSORS_REQUIRING_PARAMETER = EnumSet.of(
        NodeType.SENSOR_TOUCHING_BLOCK,
        NodeType.SENSOR_TOUCHING_ENTITY,
        NodeType.SENSOR_AT_COORDINATES,
        NodeType.SENSOR_DISTANCE_BETWEEN,
        NodeType.SENSOR_ITEM_IN_INVENTORY,
        NodeType.SENSOR_ITEM_IN_SLOT,
        NodeType.SENSOR_VILLAGER_TRADE,
        NodeType.SENSOR_CHAT_MESSAGE
    );

    private static final EnumMap<NodeType, EnumSet<NodeValueTrait>> PROVIDED_TRAITS;
    private static final EnumMap<NodeType, EnumSet<NodeValueTrait>> PARAMETER_ACCEPTED_TRAITS;
    private static final EnumMap<NodeType, Integer> PARAMETER_SLOT_COUNTS;
    private static final EnumMap<NodeType, String[]> PARAMETER_SLOT_LABELS;

    static {
        EnumMap<NodeType, EnumSet<NodeValueTrait>> traits = new EnumMap<>(NodeType.class);
        traits.put(NodeType.PARAM_COORDINATE, EnumSet.of(NodeValueTrait.COORDINATE));
        traits.put(NodeType.PARAM_ROTATION, EnumSet.of(NodeValueTrait.ROTATION));
        traits.put(NodeType.PARAM_DIRECTION, EnumSet.of(NodeValueTrait.DIRECTION));
        traits.put(NodeType.PARAM_RANGE, EnumSet.of(NodeValueTrait.RANGE));
        traits.put(NodeType.PARAM_DISTANCE, EnumSet.of(NodeValueTrait.DISTANCE));
        traits.put(NodeType.PARAM_DURATION, EnumSet.of(NodeValueTrait.DURATION));
        traits.put(NodeType.PARAM_AMOUNT, EnumSet.of(NodeValueTrait.NUMBER));
        traits.put(NodeType.PARAM_BOOLEAN, EnumSet.of(NodeValueTrait.BOOLEAN));
        traits.put(NodeType.PARAM_HAND, EnumSet.of(NodeValueTrait.HAND));
        traits.put(NodeType.PARAM_GUI, EnumSet.of(NodeValueTrait.GUI));
        traits.put(NodeType.PARAM_KEY, EnumSet.of(NodeValueTrait.KEY));
        traits.put(NodeType.PARAM_MESSAGE, EnumSet.of(NodeValueTrait.MESSAGE));
        traits.put(NodeType.PARAM_BLOCK, EnumSet.of(NodeValueTrait.BLOCK));
        traits.put(NodeType.PARAM_ITEM, EnumSet.of(NodeValueTrait.ITEM));
        traits.put(NodeType.PARAM_ENTITY, EnumSet.of(NodeValueTrait.ENTITY));
        traits.put(NodeType.PARAM_PLAYER, EnumSet.of(NodeValueTrait.PLAYER));
        traits.put(NodeType.PARAM_INVENTORY_SLOT, EnumSet.of(NodeValueTrait.INVENTORY_SLOT));
        traits.put(NodeType.PARAM_VILLAGER_TRADE, EnumSet.of(NodeValueTrait.VILLAGER_TRADE));
        traits.put(NodeType.PARAM_WAYPOINT, EnumSet.of(NodeValueTrait.WAYPOINT, NodeValueTrait.COORDINATE));
        traits.put(NodeType.PARAM_SCHEMATIC, EnumSet.of(NodeValueTrait.SCHEMATIC, NodeValueTrait.COORDINATE));
        traits.put(NodeType.PARAM_PLACE_TARGET, EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.COORDINATE));
        traits.put(NodeType.PARAM_CLOSEST, EnumSet.of(NodeValueTrait.RANGE, NodeValueTrait.COORDINATE));

        traits.put(NodeType.SENSOR_POSITION_OF, EnumSet.of(NodeValueTrait.COORDINATE));
        traits.put(NodeType.SENSOR_DISTANCE_BETWEEN, EnumSet.of(NodeValueTrait.DISTANCE));
        traits.put(NodeType.SENSOR_TARGETED_BLOCK, EnumSet.of(NodeValueTrait.BLOCK));
        traits.put(NodeType.SENSOR_TARGETED_BLOCK_FACE, EnumSet.of(NodeValueTrait.DIRECTION));
        traits.put(NodeType.SENSOR_LOOK_DIRECTION, EnumSet.of(NodeValueTrait.ROTATION));

        traits.put(NodeType.OPERATOR_RANDOM, EnumSet.of(NodeValueTrait.NUMBER));
        traits.put(NodeType.OPERATOR_MOD, EnumSet.of(NodeValueTrait.NUMBER));
        traits.put(NodeType.LIST_ITEM, EnumSet.of(NodeValueTrait.LIST_ITEM, NodeValueTrait.COORDINATE));
        traits.put(NodeType.VARIABLE, EnumSet.of(NodeValueTrait.VARIABLE, NodeValueTrait.ANY));

        PROVIDED_TRAITS = traits;

        EnumMap<NodeType, EnumSet<NodeValueTrait>> accepted = new EnumMap<>(NodeType.class);
        accepted.put(NodeType.WALK, EnumSet.of(
            NodeValueTrait.DIRECTION,
            NodeValueTrait.ROTATION,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.LOOK, EnumSet.of(
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.BREAK, EnumSet.of(
            NodeValueTrait.BLOCK,
            NodeValueTrait.COORDINATE
        ));

        accepted.put(NodeType.OPERATOR_EQUALS, EnumSet.of(NodeValueTrait.ANY));
        accepted.put(NodeType.OPERATOR_NOT, EnumSet.of(NodeValueTrait.ANY));
        accepted.put(NodeType.OPERATOR_MOD, EnumSet.of(NodeValueTrait.NUMBER));
        accepted.put(NodeType.OPERATOR_GREATER, EnumSet.of(NodeValueTrait.NUMBER));
        accepted.put(NodeType.OPERATOR_LESS, EnumSet.of(NodeValueTrait.NUMBER));
        accepted.put(NodeType.OPERATOR_BOOLEAN_NOT, EnumSet.of(NodeValueTrait.BOOLEAN));

        accepted.put(NodeType.SENSOR_POSITION_OF, EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.ITEM, NodeValueTrait.ENTITY));
        accepted.put(NodeType.SENSOR_DISTANCE_BETWEEN, EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.ITEM, NodeValueTrait.ENTITY));
        accepted.put(NodeType.SENSOR_TOUCHING_BLOCK, EnumSet.of(NodeValueTrait.BLOCK));
        accepted.put(NodeType.SENSOR_TOUCHING_ENTITY, EnumSet.of(NodeValueTrait.ENTITY));
        accepted.put(NodeType.SENSOR_AT_COORDINATES, EnumSet.of(NodeValueTrait.COORDINATE));
        accepted.put(NodeType.SENSOR_ITEM_IN_INVENTORY, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.NUMBER));
        accepted.put(NodeType.SENSOR_ITEM_IN_SLOT, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT));
        accepted.put(NodeType.SENSOR_VILLAGER_TRADE, EnumSet.of(NodeValueTrait.VILLAGER_TRADE));
        accepted.put(NodeType.SENSOR_CHAT_MESSAGE, EnumSet.of(NodeValueTrait.PLAYER, NodeValueTrait.MESSAGE, NodeValueTrait.NUMBER));
        accepted.put(NodeType.SENSOR_KEY_PRESSED, EnumSet.of(NodeValueTrait.KEY));
        accepted.put(NodeType.SENSOR_IS_RENDERED, EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.ITEM, NodeValueTrait.ENTITY, NodeValueTrait.PLAYER));

        accepted.put(NodeType.USE, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.BLOCK));
        accepted.put(NodeType.INTERACT, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.MOVE_ITEM, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.GUI));
        accepted.put(NodeType.PLACE, EnumSet.of(
            NodeValueTrait.BLOCK,
            NodeValueTrait.INVENTORY_SLOT,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.PLACE_HAND, EnumSet.of(
            NodeValueTrait.BLOCK,
            NodeValueTrait.INVENTORY_SLOT,
            NodeValueTrait.COORDINATE,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.LIST_ITEM
        ));

        accepted.put(NodeType.GOTO, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.GOAL, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.PATH, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.EXPLORE, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.FOLLOW, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));

        accepted.put(NodeType.COLLECT, EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.NUMBER));
        accepted.put(NodeType.CRAFT, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.NUMBER));
        accepted.put(NodeType.BUILD, EnumSet.of(
            NodeValueTrait.COORDINATE,
            NodeValueTrait.SCHEMATIC,
            NodeValueTrait.BLOCK,
            NodeValueTrait.ITEM,
            NodeValueTrait.ENTITY,
            NodeValueTrait.PLAYER,
            NodeValueTrait.ROTATION,
            NodeValueTrait.DIRECTION,
            NodeValueTrait.LIST_ITEM
        ));
        accepted.put(NodeType.FARM, EnumSet.of(NodeValueTrait.WAYPOINT, NodeValueTrait.RANGE));

        accepted.put(NodeType.HOTBAR, EnumSet.of(NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.ITEM));
        accepted.put(NodeType.DROP_ITEM, EnumSet.of(NodeValueTrait.NUMBER));
        accepted.put(NodeType.DROP_SLOT, EnumSet.of(NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.NUMBER));
        accepted.put(NodeType.CLICK_SLOT, EnumSet.of(NodeValueTrait.INVENTORY_SLOT));
        accepted.put(NodeType.EQUIP_ARMOR, EnumSet.of(NodeValueTrait.INVENTORY_SLOT));
        accepted.put(NodeType.EQUIP_HAND, EnumSet.of(NodeValueTrait.INVENTORY_SLOT));

        accepted.put(NodeType.SET_VARIABLE, EnumSet.of(NodeValueTrait.VARIABLE, NodeValueTrait.ANY));
        accepted.put(NodeType.CHANGE_VARIABLE, EnumSet.of(NodeValueTrait.VARIABLE, NodeValueTrait.NUMBER));
        accepted.put(NodeType.CREATE_LIST, EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.ENTITY, NodeValueTrait.PLAYER));
        accepted.put(NodeType.TRADE, EnumSet.of(NodeValueTrait.VILLAGER_TRADE, NodeValueTrait.NUMBER));
        accepted.put(NodeType.CONTROL_REPEAT, EnumSet.of(NodeValueTrait.NUMBER));

        PARAMETER_ACCEPTED_TRAITS = accepted;

        EnumMap<NodeType, Integer> slotCounts = new EnumMap<>(NodeType.class);
        slotCounts.put(NodeType.SET_VARIABLE, 2);
        slotCounts.put(NodeType.CHANGE_VARIABLE, 2);
        slotCounts.put(NodeType.OPERATOR_MOD, 2);
        slotCounts.put(NodeType.OPERATOR_EQUALS, 2);
        slotCounts.put(NodeType.OPERATOR_NOT, 2);
        slotCounts.put(NodeType.OPERATOR_GREATER, 2);
        slotCounts.put(NodeType.OPERATOR_LESS, 2);
        slotCounts.put(NodeType.PLACE, 2);
        slotCounts.put(NodeType.PLACE_HAND, 2);
        slotCounts.put(NodeType.MOVE_ITEM, 2);
        slotCounts.put(NodeType.WALK, 2);
        slotCounts.put(NodeType.BREAK, 1);
        slotCounts.put(NodeType.SENSOR_CHAT_MESSAGE, 2);
        slotCounts.put(NodeType.SENSOR_ITEM_IN_SLOT, 2);
        slotCounts.put(NodeType.SENSOR_DISTANCE_BETWEEN, 2);
        PARAMETER_SLOT_COUNTS = slotCounts;

        EnumMap<NodeType, String[]> slotLabels = new EnumMap<>(NodeType.class);
        slotLabels.put(NodeType.SET_VARIABLE, new String[]{"Variable", "Value"});
        slotLabels.put(NodeType.CHANGE_VARIABLE, new String[]{"Variable", "Amount"});
        slotLabels.put(NodeType.OPERATOR_BOOLEAN_NOT, new String[]{"Value"});
        slotLabels.put(NodeType.OPERATOR_MOD, new String[]{"Value", "Modulo"});
        slotLabels.put(NodeType.BUILD, new String[]{"Position"});
        slotLabels.put(NodeType.PLACE, new String[]{"Source", "Position"});
        slotLabels.put(NodeType.PLACE_HAND, new String[]{"Source", "Position"});
        slotLabels.put(NodeType.MOVE_ITEM, new String[]{"Source Slot", "Target Slot"});
        slotLabels.put(NodeType.CLICK_SLOT, new String[]{"Inventory Slot"});
        slotLabels.put(NodeType.WALK, new String[]{"Direction", "Duration/Distance"});
        slotLabels.put(NodeType.BREAK, new String[]{"Target"});
        slotLabels.put(NodeType.SENSOR_CHAT_MESSAGE, new String[]{"User", "Message"});
        slotLabels.put(NodeType.SENSOR_ITEM_IN_SLOT, new String[]{"Item", "Slot"});
        slotLabels.put(NodeType.SENSOR_POSITION_OF, new String[]{"Target"});
        slotLabels.put(NodeType.SENSOR_DISTANCE_BETWEEN, new String[]{"Target A", "Target B"});
        slotLabels.put(NodeType.SENSOR_VILLAGER_TRADE, new String[]{"Villager Trade"});
        slotLabels.put(NodeType.CREATE_LIST, new String[]{"Target"});
        slotLabels.put(NodeType.TRADE, new String[]{"Villager Trade"});
        slotLabels.put(NodeType.CONTROL_REPEAT, new String[]{"Count"});
        PARAMETER_SLOT_LABELS = slotLabels;
    }

    private NodeTraitRegistry() {
    }

    public static boolean isBooleanSensor(NodeType type) {
        return type != null && BOOLEAN_SENSORS.contains(type);
    }

    public static boolean isSensorWithoutParameterSlot(NodeType type) {
        return type != null && SENSORS_WITHOUT_PARAMETER_SLOT.contains(type);
    }

    public static boolean isSensorParameterRequired(NodeType type) {
        return type != null && SENSORS_REQUIRING_PARAMETER.contains(type);
    }

    public static boolean isParameterNode(NodeType type) {
        if (type == null) {
            return false;
        }
        if (type.getCategory() == NodeCategory.PARAMETERS) {
            return true;
        }
        if (type == NodeType.SENSOR_POSITION_OF
            || type == NodeType.SENSOR_DISTANCE_BETWEEN
            || type == NodeType.SENSOR_TARGETED_BLOCK
            || type == NodeType.SENSOR_TARGETED_BLOCK_FACE
            || type == NodeType.SENSOR_LOOK_DIRECTION) {
            return true;
        }
        if (type == NodeType.VARIABLE || type == NodeType.OPERATOR_RANDOM || type == NodeType.OPERATOR_MOD || type == NodeType.LIST_ITEM) {
            return true;
        }
        return false;
    }

    public static EnumSet<NodeValueTrait> getProvidedTraits(NodeType type) {
        EnumSet<NodeValueTrait> traits = type == null ? null : PROVIDED_TRAITS.get(type);
        return traits == null ? EnumSet.noneOf(NodeValueTrait.class) : EnumSet.copyOf(traits);
    }

    public static EnumSet<NodeValueTrait> getAcceptedTraits(NodeType hostType, int slotIndex) {
        EnumSet<NodeValueTrait> traits = hostType == null ? null : PARAMETER_ACCEPTED_TRAITS.get(hostType);
        if (traits == null) {
            return EnumSet.noneOf(NodeValueTrait.class);
        }
        if (hostType == NodeType.CONTROL_REPEAT) {
            return EnumSet.of(NodeValueTrait.NUMBER, NodeValueTrait.DURATION);
        }
        if (hostType == NodeType.WALK && slotIndex == 1) {
            return EnumSet.of(NodeValueTrait.DURATION, NodeValueTrait.DISTANCE);
        }
        if (hostType == NodeType.MOVE_ITEM && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.ITEM, NodeValueTrait.INVENTORY_SLOT);
        }
        if ((hostType == NodeType.SENSOR_ITEM_IN_SLOT || hostType == NodeType.MOVE_ITEM) && slotIndex == 1) {
            return hostType == NodeType.SENSOR_ITEM_IN_SLOT
                ? EnumSet.of(NodeValueTrait.INVENTORY_SLOT)
                : EnumSet.of(NodeValueTrait.INVENTORY_SLOT, NodeValueTrait.GUI);
        }
        if (hostType == NodeType.SENSOR_ITEM_IN_SLOT && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.ITEM);
        }
        if (hostType == NodeType.SENSOR_CHAT_MESSAGE && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.PLAYER);
        }
        if (hostType == NodeType.SENSOR_CHAT_MESSAGE && slotIndex == 1) {
            return EnumSet.of(NodeValueTrait.MESSAGE);
        }
        if (hostType == NodeType.PLACE || hostType == NodeType.PLACE_HAND) {
            if (slotIndex == 0) {
                return EnumSet.of(NodeValueTrait.BLOCK, NodeValueTrait.INVENTORY_SLOT);
            }
            return EnumSet.of(
                NodeValueTrait.COORDINATE,
                NodeValueTrait.ROTATION,
                NodeValueTrait.DIRECTION,
                NodeValueTrait.BLOCK,
                NodeValueTrait.ITEM,
                NodeValueTrait.ENTITY,
                NodeValueTrait.PLAYER,
                NodeValueTrait.LIST_ITEM
            );
        }
        if (hostType == NodeType.SET_VARIABLE && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.VARIABLE);
        }
        if (hostType == NodeType.CHANGE_VARIABLE && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.VARIABLE);
        }
        if (hostType == NodeType.CONTROL_REPEAT && slotIndex == 0) {
            return EnumSet.of(NodeValueTrait.NUMBER, NodeValueTrait.DURATION);
        }
        return EnumSet.copyOf(traits);
    }

    public static Map<NodeType, EnumSet<NodeValueTrait>> getProvidedTraitsSnapshot() {
        return Collections.unmodifiableMap(PROVIDED_TRAITS);
    }

    public static boolean canHostParameter(NodeType type) {
        return type != null && PARAMETER_ACCEPTED_TRAITS.containsKey(type);
    }

    public static int getParameterSlotCount(NodeType hostType) {
        if (!canHostParameter(hostType)) {
            return 0;
        }
        Integer count = PARAMETER_SLOT_COUNTS.get(hostType);
        return count == null ? 1 : count;
    }

    public static String getParameterSlotLabel(NodeType hostType, int slotIndex) {
        if (hostType == null) {
            return "Parameter";
        }
        String[] labels = PARAMETER_SLOT_LABELS.get(hostType);
        if (labels == null || labels.length == 0) {
            return "Parameter";
        }
        if (slotIndex < 0 || slotIndex >= labels.length) {
            return labels[0];
        }
        String label = labels[slotIndex];
        return label == null || label.isEmpty() ? "Parameter" : label;
    }

    public static boolean isParameterSlotAlwaysRequired(NodeType hostType, int slotIndex) {
        if (hostType == null) {
            return false;
        }
        if (hostType == NodeType.SET_VARIABLE) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (hostType == NodeType.CHANGE_VARIABLE) {
            return slotIndex == 0;
        }
        if (hostType == NodeType.OPERATOR_MOD) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (hostType == NodeType.OPERATOR_EQUALS
            || hostType == NodeType.OPERATOR_NOT
            || hostType == NodeType.OPERATOR_GREATER
            || hostType == NodeType.OPERATOR_LESS) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (hostType == NodeType.SENSOR_CHAT_MESSAGE) {
            return slotIndex == 0 || slotIndex == 1;
        }
        if (hostType == NodeType.SENSOR_DISTANCE_BETWEEN) {
            return slotIndex == 0 || slotIndex == 1;
        }
        return slotIndex == 0;
    }
}
