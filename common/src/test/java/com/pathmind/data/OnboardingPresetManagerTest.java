package com.pathmind.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        assertEquals(3, presets.size());
        for (OnboardingPresetManager.ExamplePreset preset : presets) {
            try (InputStream input = OnboardingPresetManager.class.getClassLoader().getResourceAsStream(preset.resourcePath())) {
                assertNotNull(input, preset.resourcePath());
                Path sourcePath = tempDir.resolve(preset.name() + "-source.json");
                Files.copy(input, sourcePath);

                Path savePath = tempDir.resolve(preset.name() + ".json");
                assertTrue(NodeGraphPersistence.normalizeNodeGraphToPath(sourcePath, savePath), preset.name());

                NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
                assertNotNull(loaded, preset.name());
                assertEquals(0, loaded.getNodes().size(), preset.name());
                assertEquals(0, loaded.getConnections().size(), preset.name());
            }
        }
    }

    @Test
    void examplePresetManifestPointsToTemporaryBlankResources() {
        List<OnboardingPresetManager.ExamplePreset> presets = OnboardingPresetManager.createExamplePresets();

        assertEquals(3, presets.size());
        assertEquals("example 1", presets.get(0).name());
        assertEquals("example 2", presets.get(1).name());
        assertEquals("example 3", presets.get(2).name());
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
        Path existingPreset = presetsDirectory.resolve("example 1.json");
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

        Files.delete(presetsDirectory.resolve("example 1.json"));
        OnboardingPresetManager.ensureExamplePresetsInstalled(baseDirectory, true);
        assertEquals(2, countJsonFiles(presetsDirectory));
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
}
