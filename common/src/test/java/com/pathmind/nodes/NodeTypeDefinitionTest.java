package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTypeDefinitionTest {

    @Test
    void everyNodeTypeHasCatalogMetadata() {
        for (NodeType type : NodeType.values()) {
            assertTrue(
                NodeCatalog.hasDefinition(type),
                () -> "Missing catalog metadata for " + type
            );
        }
    }

    @Test
    void categoryMetadataMatchesKeyNodeFamilies() {
        assertEquals(NodeCategory.FLOW, NodeType.START.getCategory());
        assertEquals(NodeCategory.CONTROL, NodeType.CONTROL_IF.getCategory());
        assertEquals(NodeCategory.SENSORS, NodeType.SENSOR_TOUCHING_BLOCK.getCategory());
        assertEquals(NodeCategory.NAVIGATION, NodeType.GOTO.getCategory());
        assertEquals(NodeCategory.PLAYER, NodeType.LOOK.getCategory());
        assertEquals(NodeCategory.INTERFACE, NodeType.CLICK_SLOT.getCategory());
        assertEquals(NodeCategory.DATA, NodeType.OPERATOR_EQUALS.getCategory());
        assertEquals(NodeCategory.CUSTOM, NodeType.CUSTOM_NODE.getCategory());
        assertEquals(NodeCategory.PARAMETERS, NodeType.PARAM_BLOCK.getCategory());
    }

    @Test
    void sidebarAndDependencyMetadataMatchesExistingBehavior() {
        assertFalse(NodeType.STOP.isDraggableFromSidebar());
        assertFalse(NodeType.TEMPLATE.isDraggableFromSidebar());
        assertTrue(NodeType.GOTO.isDraggableFromSidebar());

        assertTrue(NodeType.GOTO.requiresBaritone());
        assertFalse(NodeType.TRAVEL.requiresBaritone());
        assertTrue(NodeType.UI_UTILS.requiresUiUtils());

        assertTrue(NodeType.EVENT_FUNCTION.hasParameters());
        assertTrue(NodeType.PARAM_BLOCK.hasParameters());
        assertFalse(NodeType.START.hasParameters());
    }
}
