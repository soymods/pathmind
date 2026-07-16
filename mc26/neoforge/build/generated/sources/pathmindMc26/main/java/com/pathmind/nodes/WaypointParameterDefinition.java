package com.pathmind.nodes;

final class WaypointParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_WAYPOINT)
            .parameterBehavior((node, values) -> NodeBehaviorDefinitionSupport.copyIfPresent(values, "Waypoint", "Name"))
            .build();
    }

    private WaypointParameterDefinition() {
    }
}
