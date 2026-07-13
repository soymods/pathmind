package com.pathmind.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertEquals(preset.nodes().size(), loaded.getNodes().size(), preset.name());
            assertEquals(preset.connections().size(), loaded.getConnections().size(), preset.name());
        }
    }

    @Test
    void examplePresetsAreTemporarilyEmpty() {
        List<OnboardingPresetManager.ExamplePreset> presets = OnboardingPresetManager.createExamplePresets();

        assertEquals(3, presets.size());
        assertEquals("Basics", presets.get(0).name());
        assertEquals("Variables and If Else", presets.get(1).name());
        assertEquals("Relative Variables and Lists", presets.get(2).name());
        for (OnboardingPresetManager.ExamplePreset preset : presets) {
            assertTrue(preset.nodes().isEmpty(), preset.name());
            assertTrue(preset.connections().isEmpty(), preset.name());
        }
    }

    @Test
    void restoreExamplesCanSkipOrOverwriteExistingFiles() throws Exception {
        Path baseDirectory = tempDir.resolve("pathmind");
        Path presetsDirectory = baseDirectory.resolve("presets");
        Files.createDirectories(presetsDirectory);
        Path existingPreset = presetsDirectory.resolve("Basics.json");
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

        Files.delete(presetsDirectory.resolve("Basics.json"));
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
