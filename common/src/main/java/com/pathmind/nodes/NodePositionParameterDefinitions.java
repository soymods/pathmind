package com.pathmind.nodes;

import java.util.Optional;

final class NodePositionParameterDefinitions {
    static Optional<NodeBehaviorDefinition> definition(NodeType type) {
        return switch (type) {
            case PARAM_COORDINATE -> Optional.of(CoordinateParameterDefinition.create());
            case PARAM_SCHEMATIC -> Optional.of(SchematicParameterDefinition.create());
            case PARAM_PLACE_TARGET -> Optional.of(PlaceTargetParameterDefinition.create());
            case PARAM_CLOSEST -> Optional.of(ClosestParameterDefinition.create());
            default -> OrientationParameterDefinition.definition(type);
        };
    }

    static NodeBehaviorDefinitionSupport.Orientation applyDirection(String direction, float currentYaw, float currentPitch) {
        return OrientationParameterDefinition.applyDirection(direction, currentYaw, currentPitch);
    }

    private NodePositionParameterDefinitions() {
    }
}
