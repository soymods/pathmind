package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeValueTrait;
import com.pathmind.routines.RoutineDefinition;
import com.pathmind.routines.RoutineInputDefinition;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineValueKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutinePersistenceTest {

    @Test
    void invocationAttachmentFollowsStableInputIdAfterReorder() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Use");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData first = builder.addInput("first", RoutineValueKind.NUMBER);
        builder.addInput("second", RoutineValueKind.TEXT);
        Node call = Node.createRoutineCall(routine, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        assertTrue(call.attachParameter(amount, 0));
        NodeGraphData data = NodeGraphPersistence.createGraphData(List.of(call, amount), List.of());
        data.setRoutines(List.of(routine));

        builder.moveInput(first.getId(), 1);
        List<Node> restored = NodeGraphPersistence.convertToNodes(data);
        Node restoredCall = restored.stream().filter(node -> node.getType() == NodeType.ROUTINE_CALL).findFirst().orElseThrow();

        assertEquals(first.getId(), restoredCall.getRoutineInputIdForSlot(1));
        assertNotNull(restoredCall.getAttachedParameter(1));
    }
    @TempDir
    Path tempDir;

    @Test
    void emptyRoutineRoundTripsInsidePreset() {
        NodeGraphData preset = new NodeGraphData();
        RoutineDefinition routine = RoutineDefinition.create("Wave");
        NodeGraphPersistence.setRoutineDefinitions(preset, List.of(routine));

        Path path = tempDir.resolve("routine.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphDataToPath(preset, path));
        NodeGraphData restoredData = NodeGraphPersistence.loadNodeGraphFromPath(path);
        List<RoutineDefinition> restored = NodeGraphPersistence.convertToRoutineDefinitions(restoredData);

        assertEquals(1, restored.size());
        assertEquals(routine.getId(), restored.get(0).getId());
        assertEquals("Wave", restored.get(0).getName());
        assertTrue(restored.get(0).getInputs().isEmpty());
        assertTrue(restored.get(0).getNodes().isEmpty());
    }

    @Test
    void multiInputRoutinePreservesStableIdentityAndMetadata() {
        RoutineInputDefinition amount = new RoutineInputDefinition(
            UUID.randomUUID().toString(), "Steps", RoutineValueKind.NUMBER,
            Set.of(NodeValueTrait.NUMBER), true, "1", 0);
        RoutineInputDefinition target = new RoutineInputDefinition(
            UUID.randomUUID().toString(), "Target", RoutineValueKind.ENTITY,
            Set.of(NodeValueTrait.ENTITY, NodeValueTrait.PLAYER), false, "", 1);
        Node start = new Node(NodeType.START, 40, 60);
        RoutineDefinition routine = new RoutineDefinition(
            UUID.randomUUID().toString(), "Approach", 1, 1, "", "",
            List.of(amount, target), List.of(start), List.of());
        NodeGraphData preset = new NodeGraphData();
        NodeGraphPersistence.setRoutineDefinitions(preset, List.of(routine));

        NodeGraphData reparsed = NodeGraphPersistence.parseNodeGraphData(
            NodeGraphPersistence.toPrettyJson(preset));
        RoutineDefinition restored = NodeGraphPersistence.convertToRoutineDefinitions(reparsed).get(0);

        assertEquals(routine.getId(), restored.getId());
        assertEquals(List.of(amount.getId(), target.getId()),
            restored.getInputs().stream().map(RoutineInputDefinition::getId).toList());
        assertEquals(Set.of(NodeValueTrait.ENTITY, NodeValueTrait.PLAYER),
            restored.getInputs().get(1).getAcceptedTraits());
        assertEquals(NodeType.START, restored.getNodes().get(0).getType());
    }

    @Test
    void versionRulesSeparateInterfaceFromImplementationAndIgnoreLayout() {
        NodeGraphData preset = presetWithTwoInputRoutine();
        NodeGraphData.RoutineDefinitionData routine = preset.getRoutines().get(0);
        int interfaceVersion = routine.getInterfaceVersion();
        int implementationRevision = routine.getImplementationRevision();

        routine.setName("Renamed Routine");
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);
        assertEquals(interfaceVersion, routine.getInterfaceVersion());
        assertEquals(implementationRevision + 1, routine.getImplementationRevision());

        int afterRenameRevision = routine.getImplementationRevision();
        routine.getGraph().getNodes().get(0).setX(900);
        routine.getGraph().getNodes().get(0).setY(-300);
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);
        assertEquals(interfaceVersion, routine.getInterfaceVersion());
        assertEquals(afterRenameRevision, routine.getImplementationRevision());

        NodeGraphData.NodeData internalNode = new NodeGraphData.NodeData();
        internalNode.setId(UUID.randomUUID().toString());
        internalNode.setType(NodeType.WAIT);
        routine.getGraph().getNodes().add(internalNode);
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);
        assertEquals(interfaceVersion, routine.getInterfaceVersion());
        assertEquals(afterRenameRevision + 1, routine.getImplementationRevision());

        NodeGraphData.RoutineInputData added = new NodeGraphData.RoutineInputData();
        added.setId(UUID.randomUUID().toString());
        added.setLabel("Message");
        added.setValueKind(RoutineValueKind.TEXT.name());
        added.setOrder(2);
        routine.getInputs().add(added);
        int beforeInterfaceEditRevision = routine.getImplementationRevision();
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);
        assertEquals(interfaceVersion + 1, routine.getInterfaceVersion());
        assertEquals(beforeInterfaceEditRevision + 1, routine.getImplementationRevision());
    }

    @Test
    void reorderingInputsPreservesIdsAndVersionsTheInterface() {
        NodeGraphData preset = presetWithTwoInputRoutine();
        NodeGraphData.RoutineDefinitionData routine = preset.getRoutines().get(0);
        List<String> ids = routine.getInputs().stream()
            .map(NodeGraphData.RoutineInputData::getId).toList();
        int version = routine.getInterfaceVersion();

        List<NodeGraphData.RoutineInputData> reordered = new ArrayList<>(routine.getInputs());
        Collections.reverse(reordered);
        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setOrder(i);
        }
        routine.setInputs(reordered);
        NodeGraphPersistence.sanitizeRoutineDefinitions(preset);

        assertEquals(version + 1, routine.getInterfaceVersion());
        assertEquals(List.of(ids.get(1), ids.get(0)), routine.getInputs().stream()
            .map(NodeGraphData.RoutineInputData::getId).toList());
    }

    @Test
    void malformedRoutineDataIsSanitizedSafely() {
        String duplicateId = UUID.randomUUID().toString();
        String duplicateInputId = UUID.randomUUID().toString();
        String json = """
            {
              "nodes": [],
              "connections": [],
              "routines": [
                {
                  "id": "%s",
                  "name": "",
                  "interfaceVersion": -4,
                  "implementationRevision": 0,
                  "graph": null,
                  "inputs": [
                    {"id":"%s","label":"","valueKind":"mystery","acceptedTraits":["NOPE"],"order":8},
                    {"id":"%s","label":"Second","valueKind":"number","order":-2}
                  ]
                },
                {"id":"%s","name":"Other","inputs":[],"graph":{"nodes":[null],"connections":[null]}}
              ]
            }
            """.formatted(duplicateId, duplicateInputId, duplicateInputId, duplicateId);

        NodeGraphData data = NodeGraphPersistence.parseNodeGraphData(json);
        assertNotNull(data);
        assertEquals(2, data.getRoutines().size());
        assertNotEquals(data.getRoutines().get(0).getId(), data.getRoutines().get(1).getId());
        NodeGraphData.RoutineDefinitionData first = data.getRoutines().get(0);
        assertEquals("Routine", first.getName());
        assertEquals(1, first.getInterfaceVersion());
        assertEquals(1, first.getImplementationRevision());
        assertNotNull(first.getGraph());
        assertNotEquals(first.getInputs().get(0).getId(), first.getInputs().get(1).getId());
        assertEquals(RoutineValueKind.ANY.name(), first.getInputs().get(1).getValueKind());
        assertFalse(first.getInputs().get(1).getAcceptedTraits().isEmpty());
    }

    @Test
    void presetsWithoutRoutineFieldRemainValid() {
        NodeGraphData data = NodeGraphPersistence.parseNodeGraphData("{\"nodes\":[],\"connections\":[]}");
        assertNotNull(data);
        assertTrue(data.getRoutines().isEmpty());
    }

    @Test
    void normalGraphNormalizationPreservesEmbeddedRoutines() {
        NodeGraphData source = presetWithTwoInputRoutine();
        source.setNodes(List.of(new NodeGraphData.NodeData(
            UUID.randomUUID().toString(), NodeType.START, null, 0, 0, List.of())));
        Path sourcePath = tempDir.resolve("source.json");
        Path targetPath = tempDir.resolve("normalized.json");

        assertTrue(NodeGraphPersistence.saveNodeGraphDataToPath(source, sourcePath));
        assertTrue(NodeGraphPersistence.normalizeNodeGraphToPath(sourcePath, targetPath));
        NodeGraphData normalized = NodeGraphPersistence.loadNodeGraphFromPath(targetPath);

        assertNotNull(normalized);
        assertEquals(1, normalized.getRoutines().size());
        assertEquals(source.getRoutines().get(0).getId(), normalized.getRoutines().get(0).getId());
    }

    @Test
    void marketplaceFileRoundTripPreservesRoutineAndInvocation() throws Exception {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Greet");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("Message", RoutineValueKind.TEXT);
        Node reporter = builder.createInputReporter(input.getId(), 320, 180);
        routine.getGraph().getNodes().addAll(
            NodeGraphPersistence.createGraphData(List.of(reporter), List.of()).getNodes());

        Node call = Node.createRoutineCall(routine, 80, 100);
        NodeGraphData preset = NodeGraphPersistence.createGraphData(List.of(call), List.of());
        preset.setRoutines(List.of(routine));

        Path localPreset = tempDir.resolve("local-preset.json");
        Path downloadedPreset = tempDir.resolve("downloaded-preset.json");
        Path importedPreset = tempDir.resolve("imported-preset.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphDataToPath(preset, localPreset));

        // Marketplace storage uploads and downloads the preset JSON as-is.
        Files.write(downloadedPreset, Files.readAllBytes(localPreset));
        assertTrue(NodeGraphPersistence.normalizeNodeGraphToPath(downloadedPreset, importedPreset));

        NodeGraphData restored = NodeGraphPersistence.loadNodeGraphFromPath(importedPreset);
        assertNotNull(restored);
        assertEquals(1, restored.getRoutines().size());
        NodeGraphData.RoutineDefinitionData restoredRoutine = restored.getRoutines().get(0);
        assertEquals(routine.getId(), restoredRoutine.getId());
        assertEquals(input.getId(), restoredRoutine.getInputs().get(0).getId());
        assertTrue(restoredRoutine.getGraph().getNodes().stream()
            .anyMatch(node -> node.getType() == NodeType.ROUTINE_INPUT
                && input.getId().equals(node.getRoutineInputId())));
        NodeGraphData.NodeData restoredCall = restored.getNodes().stream()
            .filter(node -> node.getType() == NodeType.ROUTINE_CALL).findFirst().orElseThrow();
        assertEquals(routine.getId(), restoredCall.getRoutineId());
        assertEquals(input.getId(), restoredCall.getRoutineArguments().get(0).getInputId());
    }

    private NodeGraphData presetWithTwoInputRoutine() {
        RoutineInputDefinition first = RoutineInputDefinition.create("First", RoutineValueKind.NUMBER, 0);
        RoutineInputDefinition second = RoutineInputDefinition.create("Second", RoutineValueKind.TEXT, 1);
        RoutineDefinition routine = new RoutineDefinition(
            UUID.randomUUID().toString(), "Routine", 1, 1, "", "",
            List.of(first, second), List.of(new Node(NodeType.START, 10, 20)), List.of());
        NodeGraphData preset = new NodeGraphData();
        NodeGraphPersistence.setRoutineDefinitions(preset, List.of(routine));
        return preset;
    }
}
