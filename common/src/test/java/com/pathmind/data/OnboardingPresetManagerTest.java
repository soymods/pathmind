package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnboardingPresetManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void examplePresetsRoundTripThroughGraphPersistence() {
        List<OnboardingPresetManager.ExamplePreset> presets = OnboardingPresetManager.createExamplePresets();

        assertEquals(3, presets.size());
        for (OnboardingPresetManager.ExamplePreset preset : presets) {
            Path savePath = tempDir.resolve(preset.name() + ".json");
            assertTrue(NodeGraphPersistence.saveNodeGraphToPath(preset.nodes(), preset.connections(), savePath));

            NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
            assertNotNull(loaded, preset.name());
            List<Node> nodes = NodeGraphPersistence.convertToNodes(loaded);
            Map<String, Node> byId = nodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));
            List<NodeConnection> connections = NodeGraphPersistence.convertToConnections(loaded, byId);

            assertFalse(nodes.isEmpty(), preset.name());
            assertTrue(nodes.stream().anyMatch(node -> node.getType() == NodeType.START), preset.name());
            assertTrue(nodes.stream().anyMatch(node -> node.getType() == NodeType.STICKY_NOTE), preset.name());
            assertEquals(preset.connections().size(), connections.size(), preset.name());
        }
    }

    @Test
    void logicExampleContainsVariableBranchAndAttachedSensor() {
        OnboardingPresetManager.ExamplePreset preset = OnboardingPresetManager.createExamplePresets().get(1);
        Path savePath = tempDir.resolve("logic.json");
        assertTrue(NodeGraphPersistence.saveNodeGraphToPath(preset.nodes(), preset.connections(), savePath));

        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        List<Node> nodes = NodeGraphPersistence.convertToNodes(loaded);

        Node branch = nodes.stream()
            .filter(node -> node.getType() == NodeType.CONTROL_IF_ELSE)
            .findFirst()
            .orElseThrow();

        assertNotNull(branch.getAttachedSensor());
        assertEquals(NodeType.OPERATOR_GREATER, branch.getAttachedSensor().getType());
        assertEquals(2, branch.getOutputSocketCount());
    }

    @Test
    void restoreExamplesCanSkipOrOverwriteExistingFiles() throws Exception {
        Path baseDirectory = tempDir.resolve("pathmind");
        Path presetsDirectory = baseDirectory.resolve("presets");
        Files.createDirectories(presetsDirectory);
        Path existingPreset = presetsDirectory.resolve("Example 1 - Basics.json");
        Files.writeString(existingPreset, "custom", StandardCharsets.UTF_8);

        OnboardingPresetManager.RestoreResult skipped = OnboardingPresetManager.restoreExamplePresets(baseDirectory, false);
        assertTrue(skipped.success());
        assertEquals(2, skipped.restoredCount());
        assertEquals("custom", Files.readString(existingPreset, StandardCharsets.UTF_8));

        OnboardingPresetManager.RestoreResult restored = OnboardingPresetManager.restoreExamplePresets(baseDirectory, true);
        assertTrue(restored.success());
        assertEquals(3, restored.restoredCount());
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

        Files.delete(presetsDirectory.resolve("Example 1 - Basics.json"));
        OnboardingPresetManager.ensureExamplePresetsInstalled(baseDirectory, true);
        assertEquals(2, countJsonFiles(presetsDirectory));
    }

    private static long countJsonFiles(Path directory) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            return paths
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .count();
        }
    }
}
