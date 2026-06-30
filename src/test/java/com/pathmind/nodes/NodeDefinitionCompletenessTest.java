package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class NodeDefinitionCompletenessTest {
    private static final EnumSet<NodeType> ENTRY_ONLY_TYPES = EnumSet.of(
        NodeType.START,
        NodeType.EVENT_FUNCTION
    );

    private static final EnumSet<NodeType> EXECUTION_MANAGER_ROUTED_TYPES = EnumSet.of(
        NodeType.EVENT_CALL
    );

    private static final EnumSet<NodeType> EDITOR_ONLY_TYPES = EnumSet.of(
        NodeType.STICKY_NOTE
    );

    private static final EnumSet<NodeType> VALUE_ONLY_TYPES = EnumSet.of(
        NodeType.VARIABLE,
        NodeType.LIST_ITEM,
        NodeType.LIST_LENGTH,
        NodeType.OPERATOR_RANDOM,
        NodeType.OPERATOR_MOD,
        NodeType.OPERATOR_BOOLEAN_NOT,
        NodeType.OPERATOR_BOOLEAN_OR,
        NodeType.OPERATOR_BOOLEAN_AND,
        NodeType.OPERATOR_BOOLEAN_XOR,
        NodeType.OPERATOR_EQUALS,
        NodeType.OPERATOR_NOT,
        NodeType.OPERATOR_GREATER,
        NodeType.OPERATOR_LESS,
        NodeType.SENSOR_POSITION_OF,
        NodeType.SENSOR_DISTANCE_BETWEEN,
        NodeType.SENSOR_SLOT_ITEM_COUNT,
        NodeType.SENSOR_TARGETED_BLOCK,
        NodeType.SENSOR_TARGETED_ENTITY,
        NodeType.SENSOR_TARGETED_BLOCK_FACE,
        NodeType.SENSOR_LOOK_DIRECTION,
        NodeType.SENSOR_CURRENT_HAND,
        NodeType.SENSOR_IS_ON_GROUND
    );

    @Test
    void everyNodeTypeHasDeclarativeCategoryMetadata() {
        for (NodeType type : NodeType.values()) {
            assertTrue(NodeTypeDefinition.hasExplicitCategory(type),
                () -> "Missing category metadata for " + type);
        }
    }

    @Test
    void parameterizedNodeTypesHaveRegisteredDefaultsOrModeDefaults() {
        Set<NodeType> missingDefinitions = new LinkedHashSet<>();
        Set<NodeType> emptyInlineParameters = new LinkedHashSet<>();
        for (NodeType type : NodeType.values()) {
            if (!type.hasParameters()) {
                continue;
            }
            NodeMode defaultMode = NodeMode.getDefaultModeForNodeType(type);
            boolean hasTypeDefinitions = NodeParameterDefinitionRegistry.hasDefinitions(type);
            boolean hasModeDefinitions = defaultMode != null && NodeParameterDefinitionRegistry.hasDefinitions(defaultMode);
            boolean hasSocketDefinitions = NodeTraitRegistry.canHostParameter(type);
            boolean hasModeSelection = NodeMode.getModesForNodeType(type).length > 0;
            boolean hasStructuralSlotDefinitions = hasSocketDefinitions
                || NodeCompatibility.canHostSlot(type, NodeSlotType.SENSOR)
                || NodeCompatibility.canHostSlot(type, NodeSlotType.ACTION)
                || hasModeSelection;

            if (!hasTypeDefinitions && !hasModeDefinitions && !hasStructuralSlotDefinitions) {
                missingDefinitions.add(type);
            }
            if (!hasStructuralSlotDefinitions && new Node(type, 0, 0).getParameters().isEmpty()) {
                emptyInlineParameters.add(type);
            }
        }

        assertTrue(missingDefinitions.isEmpty(),
            () -> "NodeTypes have parameter metadata but no defaults or structural slots: " + missingDefinitions);
        assertTrue(emptyInlineParameters.isEmpty(),
            () -> "Inline-parameter node types initialize with no parameters: " + emptyInlineParameters);
    }

    @Test
    void parameterNodesAndValueNodesDeclareProvidedTraits() {
        for (NodeType type : NodeType.values()) {
            if (!NodeTraitRegistry.isParameterNode(type)) {
                continue;
            }
            assertFalse(NodeTraitRegistry.getProvidedTraits(type).isEmpty(),
                () -> "Parameter/value node does not declare provided traits: " + type);
        }
    }

    @Test
    void parameterHostsDeclareSlotCountLabelsAndAcceptedTraits() {
        for (NodeType type : NodeType.values()) {
            if (!NodeTraitRegistry.canHostParameter(type)) {
                continue;
            }
            int slotCount = NodeTraitRegistry.getParameterSlotCount(type);
            assertTrue(slotCount > 0, () -> "Parameter host has no slots: " + type);
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                int currentSlot = slotIndex;
                assertFalse(NodeTraitRegistry.getAcceptedTraits(type, currentSlot).isEmpty(),
                    () -> "Parameter host slot has no accepted traits: " + type + "[" + currentSlot + "]");
                assertFalse(NodeTraitRegistry.getParameterSlotLabel(type, currentSlot).isBlank(),
                    () -> "Parameter host slot has no label: " + type + "[" + currentSlot + "]");
            }
        }
    }

    @Test
    void executableNodeTypesHaveDispatcherRoutesOrExplicitNonExecutableClassification() {
        Set<NodeType> missingRoutes = EnumSet.allOf(NodeType.class).stream()
            .filter(type -> !NodeCommandDispatcher.hasExplicitRoute(type))
            .filter(type -> !EXECUTION_MANAGER_ROUTED_TYPES.contains(type))
            .filter(type -> !NodeTraitRegistry.isParameterNode(type))
            .filter(type -> !ENTRY_ONLY_TYPES.contains(type))
            .filter(type -> !EDITOR_ONLY_TYPES.contains(type))
            .filter(type -> !VALUE_ONLY_TYPES.contains(type))
            .collect(Collectors.toSet());

        assertTrue(missingRoutes.isEmpty(),
            () -> "Node types need dispatcher routes or an explicit non-executable classification: " + missingRoutes);
    }

    @Test
    void dependencyGatedNodesAreRoutedOrExplicitlyValueOnly() {
        for (NodeType type : NodeType.values()) {
            if (!type.requiresBaritone() && !type.requiresUiUtils()) {
                continue;
            }
            assertTrue(NodeCommandDispatcher.hasExplicitRoute(type) || VALUE_ONLY_TYPES.contains(type) || NodeTraitRegistry.isParameterNode(type),
                () -> "Dependency-gated node is neither routed nor explicitly value-only: " + type);
        }
    }
}
