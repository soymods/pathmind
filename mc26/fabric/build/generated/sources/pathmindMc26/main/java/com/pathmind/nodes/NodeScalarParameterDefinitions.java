package com.pathmind.nodes;

import java.util.Optional;

final class NodeScalarParameterDefinitions {
    static Optional<NodeBehaviorDefinition> definition(NodeType type) {
        return switch (type) {
            case CHANGE_VARIABLE -> Optional.of(MathNodeDefinition.create());
            case PARAM_AMOUNT -> Optional.of(AmountParameterDefinition.create());
            case PARAM_DURATION -> Optional.of(DurationParameterDefinition.create());
            case PARAM_RANGE -> Optional.of(RangeParameterDefinition.create());
            case PARAM_DISTANCE -> Optional.of(DistanceParameterDefinition.create());
            case PARAM_BOOLEAN -> Optional.of(BooleanParameterDefinition.create());
            case PARAM_HAND -> Optional.of(HandParameterDefinition.create());
            case PARAM_MESSAGE -> Optional.of(MessageParameterDefinition.create());
            case PARAM_INVENTORY_SLOT -> Optional.of(InventorySlotParameterDefinition.create());
            case PARAM_VILLAGER_TRADE -> Optional.of(VillagerTradeParameterDefinition.create());
            case PARAM_WAYPOINT -> Optional.of(WaypointParameterDefinition.create());
            case PARAM_GUI -> Optional.of(GuiParameterDefinition.create());
            case PARAM_KEY -> Optional.of(KeyParameterDefinition.create());
            case PARAM_MOUSE_BUTTON -> Optional.of(MouseButtonParameterDefinition.create());
            default -> Optional.empty();
        };
    }

    private NodeScalarParameterDefinitions() {
    }
}
