package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnboardingPresetManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void examplePresetResourcesRoundTripThroughGraphPersistence() throws Exception {
        List<OnboardingPresetManager.ExamplePreset> presets = OnboardingPresetManager.createExamplePresets();
        int[] expectedNodeCounts = {12, 26, 32};
        int[] expectedConnectionCounts = {6, 14, 19};

        assertEquals(3, presets.size());
        for (int index = 0; index < presets.size(); index++) {
            OnboardingPresetManager.ExamplePreset preset = presets.get(index);
            try (InputStream input = OnboardingPresetManager.class.getClassLoader().getResourceAsStream(preset.resourcePath())) {
                assertNotNull(input, preset.resourcePath());
                Path sourcePath = tempDir.resolve(preset.name() + "-source.json");
                Files.copy(input, sourcePath);

                Path savePath = tempDir.resolve(preset.name() + ".json");
                assertTrue(NodeGraphPersistence.normalizeNodeGraphToPath(sourcePath, savePath), preset.name());

                NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
                assertNotNull(loaded, preset.name());
                assertEquals(expectedNodeCounts[index], loaded.getNodes().size(), preset.name());
                assertEquals(expectedConnectionCounts[index], loaded.getConnections().size(), preset.name());

                List<Node> nodes = NodeGraphPersistence.convertToNodes(loaded);
                Map<String, Node> nodeById = new HashMap<>();
                for (Node node : nodes) {
                    assertNotNull(node.getId(), preset.name());
                    assertFalse(node.getId().isBlank(), preset.name());
                    assertFalse(node.getType().requiresBaritone(), node.getType().name());
                    assertFalse(node.getType().requiresUiUtils(), node.getType().name());
                    if (node.getType() == NodeType.START) {
                        assertEquals(StartLaunchMode.MANUAL, node.getStartLaunchMode(), preset.name());
                    }
                    if (node.getType() == NodeType.MESSAGE) {
                        assertTrue(node.isMessageClientSide(), node.getId());
                    }
                    assertFalse(nodeById.containsKey(node.getId()), "Duplicate node id: " + node.getId());
                    nodeById.put(node.getId(), node);
                }
                assertEquals(nodes.size(), nodeById.size(), preset.name());
                if (index < 2) {
                    assertNoTopLevelNodeOverlaps(preset.name(), nodes);
                }
                if (index == 0) {
                    assertContainsNodeTypes(preset.name(), nodes,
                        NodeType.LOOK, NodeType.WALK, NodeType.JUMP,
                        NodeType.PARAM_DIRECTION, NodeType.PARAM_DURATION);
                } else if (index == 1) {
                    assertContainsNodeTypes(preset.name(), nodes,
                        NodeType.SET_VARIABLE, NodeType.CHANGE_VARIABLE,
                        NodeType.OPERATOR_BOOLEAN_AND, NodeType.SENSOR_IS_ON_GROUND,
                        NodeType.CONTROL_IF_ELSE, NodeType.CONTROL_JOIN_ANY,
                        NodeType.WALK, NodeType.JUMP, NodeType.CROUCH);
                }

                List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(loaded, nodeById);
                assertEquals(expectedConnectionCounts[index], connections.size(), preset.name());
                for (NodeConnection connection : connections) {
                    assertTrue(connection.getOutputSocket() >= 0
                        && connection.getOutputSocket() < connection.getOutputNode().getOutputSocketCount(), preset.name());
                    assertTrue(connection.getInputSocket() >= 0
                        && connection.getInputSocket() < connection.getInputNode().getInputSocketCount(), preset.name());
                }

                GraphValidationResult validation = GraphValidator.validate(
                    nodes,
                    connections,
                    preset.name(),
                    false,
                    false
                );
                assertTrue(validation.getIssues().isEmpty(), () -> preset.name() + ": "
                    + validation.getIssues().stream().map(issue -> issue.getNodeId() + ":" + issue.getCode()
                        + "=" + issue.getMessage()).toList());
            }
        }
    }

    private static void assertNoTopLevelNodeOverlaps(String presetName, List<Node> nodes) {
        List<Node> topLevelNodes = nodes.stream()
            .filter(node -> node.getParentParameterHostId() == null)
            .filter(node -> node.getParentControlId() == null)
            .filter(node -> node.getParentActionControlId() == null)
            .toList();
        for (int firstIndex = 0; firstIndex < topLevelNodes.size(); firstIndex++) {
            Node first = topLevelNodes.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < topLevelNodes.size(); secondIndex++) {
                Node second = topLevelNodes.get(secondIndex);
                boolean overlaps = first.getX() < second.getX() + second.getWidth()
                    && first.getX() + first.getWidth() > second.getX()
                    && first.getY() < second.getY() + second.getHeight()
                    && first.getY() + first.getHeight() > second.getY();
                assertFalse(overlaps, () -> presetName + " overlaps " + first.getId() + " and " + second.getId());
            }
        }
    }

    private static void assertContainsNodeTypes(String presetName, List<Node> nodes, NodeType... expectedTypes) {
        for (NodeType expectedType : expectedTypes) {
            assertTrue(nodes.stream().anyMatch(node -> node.getType() == expectedType),
                () -> presetName + " is missing " + expectedType);
        }
    }

    @Test
    void examplePresetManifestPointsToBundledTutorialResources() {
        List<OnboardingPresetManager.ExamplePreset> presets = OnboardingPresetManager.createExamplePresets();

        assertEquals(3, presets.size());
        assertEquals("Example 1", presets.get(0).name());
        assertEquals("Example 2", presets.get(1).name());
        assertEquals("Example 3", presets.get(2).name());
        for (OnboardingPresetManager.ExamplePreset preset : presets) {
            assertTrue(preset.resourcePath().startsWith("assets/pathmind/onboarding_presets/"), preset.name());
            assertTrue(preset.resourcePath().endsWith(".json"), preset.name());
        }
    }

    @Test
    void restoreExamplesCanSkipOrOverwriteExistingFiles() throws Exception {
        Path baseDirectory = tempDir.resolve("pathmind");
        Path presetsDirectory = baseDirectory.resolve("presets");
        Files.createDirectories(presetsDirectory);
        Path existingPreset = presetsDirectory.resolve("Example 1.json");
        Files.writeString(existingPreset, "custom", StandardCharsets.UTF_8);

        OnboardingPresetManager.RestoreResult skipped = OnboardingPresetManager.restoreExamplePresets(baseDirectory, false);
        assertTrue(skipped.success());
        assertEquals(2, skipped.restoredCount());
        assertEquals(1, skipped.skippedCount());
        assertTrue(skipped.failedNames().isEmpty());
        assertEquals("custom", Files.readString(existingPreset, StandardCharsets.UTF_8));

        OnboardingPresetManager.RestoreResult restored = OnboardingPresetManager.restoreExamplePresets(baseDirectory, true);
        assertTrue(restored.success());
        assertEquals(3, restored.restoredCount());
        assertEquals(0, restored.skippedCount());
        assertTrue(restored.failedNames().isEmpty());
        assertTrue(Files.readString(existingPreset, StandardCharsets.UTF_8).contains("\"nodes\""));
    }

    @Test
    void autoInstallOnlyRunsForFirstRunWorkspace() throws Exception {
        Path baseDirectory = tempDir.resolve("existing-pathmind");
        Path presetsDirectory = baseDirectory.resolve("presets");
        Files.createDirectories(presetsDirectory);

        OnboardingPresetManager.ensureExamplePresetsInstalled(baseDirectory, false);
        assertEquals(0, countJsonFiles(presetsDirectory));

        OnboardingPresetManager.ensureExamplePresetsInstalled(baseDirectory, true);
        assertEquals(3, countJsonFiles(presetsDirectory));

        Files.delete(presetsDirectory.resolve("Example 1.json"));
        OnboardingPresetManager.ensureExamplePresetsInstalled(baseDirectory, true);
        assertEquals(2, countJsonFiles(presetsDirectory));
    }

    @Test
    void tutorialReplayRepairCreatesOnlyMissingExampleOne() throws Exception {
        Path baseDirectory = tempDir.resolve("tutorial-replay");

        assertTrue(OnboardingPresetManager.ensureTutorialPresetInstalled(baseDirectory));

        Path tutorialPath = baseDirectory.resolve("presets").resolve("Example 1.json");
        assertTrue(Files.isRegularFile(tutorialPath));
        assertEquals(1, countJsonFiles(tutorialPath.getParent()));
        NodeGraphData tutorialGraph = NodeGraphPersistence.loadNodeGraphFromPath(tutorialPath);
        assertNotNull(tutorialGraph);
        assertEquals(12, tutorialGraph.getNodes().size());
    }

    @Test
    void legacyLowercaseExampleNamesAreCapitalizedWithoutChangingContents() throws Exception {
        Path baseDirectory = tempDir.resolve("legacy-casing");
        Path presetsDirectory = baseDirectory.resolve("presets");
        Files.createDirectories(presetsDirectory);
        Files.writeString(presetsDirectory.resolve("example 1.json"), "custom tutorial", StandardCharsets.UTF_8);
        Files.writeString(presetsDirectory.resolve("example 2.json"), "custom second", StandardCharsets.UTF_8);
        Files.writeString(baseDirectory.resolve("active_preset.txt"), "example 1", StandardCharsets.UTF_8);

        OnboardingPresetManager.migrateLegacyExamplePresetNames(baseDirectory);

        assertTrue(hasExactFileName(presetsDirectory, "Example 1.json"));
        assertTrue(hasExactFileName(presetsDirectory, "Example 2.json"));
        assertFalse(hasExactFileName(presetsDirectory, "example 1.json"));
        assertFalse(hasExactFileName(presetsDirectory, "example 2.json"));
        assertEquals("custom tutorial", Files.readString(presetsDirectory.resolve("Example 1.json"), StandardCharsets.UTF_8));
        assertEquals("custom second", Files.readString(presetsDirectory.resolve("Example 2.json"), StandardCharsets.UTF_8));
        assertEquals("Example 1", Files.readString(baseDirectory.resolve("active_preset.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void restoreFailureDoesNotReportSuccess() {
        OnboardingPresetManager.RestoreResult result = OnboardingPresetManager.restoreExamplePresets(null, true);

        assertFalse(result.success());
        assertEquals(0, result.restoredCount());
        assertEquals(0, result.skippedCount());
    }

    private static long countJsonFiles(Path directory) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            return paths
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .count();
        }
    }

    private static boolean hasExactFileName(Path directory, String fileName) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            return paths.anyMatch(path -> fileName.equals(path.getFileName().toString()));
        }
    }
}
