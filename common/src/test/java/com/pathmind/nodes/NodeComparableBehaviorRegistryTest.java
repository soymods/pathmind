package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeComparableBehaviorRegistryTest {

    @Test
    void comparableBehaviorsAreRegisteredForInitialFamilies() {
        assertNotNull(NodeComparableBehaviorRegistry.get(NodeType.SENSOR_LOOK_DIRECTION));
        assertTrue(NodeComparableBehaviorRegistry.snapshot().containsKey(NodeType.SENSOR_POSITION_OF));
    }
}
