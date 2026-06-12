package com.pathmind.nodes;

import com.pathmind.data.SettingsManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeParameterDefinitionRegistryTest {

    @Test
    void initializesTypeBackedParametersWithStableIdsAndDefaults() {
        Node hotbar = new Node(NodeType.HOTBAR, 0, 0);

        assertParameter(hotbar.getParameters(), 0, "hotbarslot", "Slot", ParameterType.INTEGER, "0");
        assertParameter(hotbar.getParameters(), 1, "item", "Item", ParameterType.STRING, "");
    }

    @Test
    void initializesModeBackedParametersFromDefaultMode() {
        Node gotoNode = new Node(NodeType.GOTO, 0, 0);

        assertEquals(NodeMode.GOTO_XYZ, gotoNode.getMode());
        assertParameter(gotoNode.getParameters(), 0, "x", "X", ParameterType.INTEGER, "0");
        assertParameter(gotoNode.getParameters(), 1, "y", "Y", ParameterType.INTEGER, "0");
        assertParameter(gotoNode.getParameters(), 2, "z", "Z", ParameterType.INTEGER, "0");
    }

    @Test
    void reinitializesParametersWhenModeChanges() {
        Node collect = new Node(NodeType.COLLECT, 0, 0);

        assertEquals(NodeMode.COLLECT_SINGLE, collect.getMode());
        assertParameter(collect.getParameters(), 0, "block", "Block", ParameterType.BLOCK_TYPE, "stone");
        assertParameter(collect.getParameters(), 1, "amount", "Amount", ParameterType.INTEGER, "1");

        collect.setMode(NodeMode.COLLECT_MULTIPLE);

        assertParameter(collect.getParameters(), 0, "blocks", "Blocks", ParameterType.STRING, "stone,dirt");
        assertEquals(1, collect.getParameters().size());
    }

    @Test
    void initializesDynamicCreateListDefaults() {
        Node createList = new Node(NodeType.CREATE_LIST, 0, 0);
        SettingsManager.Settings settings = SettingsManager.getCurrent();

        assertParameter(createList.getParameters(), 0, "list", "List", ParameterType.STRING, "list");
        assertParameter(createList.getParameters(), 1, "createlistuseradius", "UseRadius", ParameterType.BOOLEAN,
            Boolean.toString(Boolean.TRUE.equals(settings.createListUseCustomRadius)));
        assertEquals("Radius", createList.getParameters().get(2).getName());
        assertTrue(Double.parseDouble(createList.getParameters().get(2).getDefaultValue()) > 0.0);
    }

    private static void assertParameter(List<NodeParameter> parameters, int index, String id, String name,
                                        ParameterType type, String defaultValue) {
        NodeParameter parameter = parameters.get(index);
        assertEquals(id, parameter.getId());
        assertEquals(name, parameter.getName());
        assertEquals(type, parameter.getType());
        assertEquals(defaultValue, parameter.getDefaultValue());
    }
}
