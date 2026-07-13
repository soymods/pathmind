package com.pathmind.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs the bundled learning presets for new Pathmind workspaces.
 */
public final class OnboardingPresetManager {
    private static final String INSTALL_MARKER_FILE_NAME = "example_presets_installed.txt";
    private static final String PRESETS_DIRECTORY_NAME = "presets";

    private OnboardingPresetManager() {
    }

    static void ensureExamplePresetsInstalled(Path baseDirectory, boolean firstRunWorkspace) {
        if (!firstRunWorkspace || baseDirectory == null) {
            return;
        }
        Path markerPath = baseDirectory.resolve(INSTALL_MARKER_FILE_NAME);
        if (Files.exists(markerPath)) {
            return;
        }
        RestoreResult result = restoreExamplePresets(baseDirectory, false);
        if (!result.success()) {
            return;
        }
        try {
            Files.writeString(markerPath, "1", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to mark example presets as installed: " + e.getMessage());
        }
    }

    public static RestoreResult restoreExamplePresets() {
        return restoreExamplePresets(PresetManager.getBaseDirectory(), true);
    }

    static RestoreResult restoreExamplePresets(Path baseDirectory, boolean overwriteExisting) {
        if (baseDirectory == null) {
            return RestoreResult.failed(List.of());
        }
        Path presetsDirectory = baseDirectory.resolve(PRESETS_DIRECTORY_NAME);
        try {
            Files.createDirectories(presetsDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create example preset directory: " + e.getMessage());
            return RestoreResult.failed(List.of());
        }

        int restoredCount = 0;
        int skippedCount = 0;
        List<String> restoredNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        for (ExamplePreset preset : createExamplePresets()) {
            Path presetPath = presetsDirectory.resolve(preset.name() + ".json");
            if (!overwriteExisting && Files.exists(presetPath)) {
                skippedCount++;
                continue;
            }
            if (installExamplePreset(preset, presetsDirectory, presetPath)) {
                restoredCount++;
                restoredNames.add(preset.name());
            } else {
                failedNames.add(preset.name());
            }
        }
        return new RestoreResult(failedNames.isEmpty(), restoredCount, restoredNames, skippedCount, failedNames);
    }

    static List<ExamplePreset> createExamplePresets() {
        return List.of(
            resourcePreset("example 1", "example_1.json"),
            resourcePreset("example 2", "example_2.json"),
            resourcePreset("example 3", "example_3.json")
        );
    }

    private static ExamplePreset resourcePreset(String name, String fileName) {
        return new ExamplePreset(name, "assets/pathmind/onboarding_presets/" + fileName);
    }

    private static boolean installExamplePreset(ExamplePreset preset, Path presetsDirectory, Path targetPath) {
        Path tempPath = null;
        try (InputStream input = OnboardingPresetManager.class.getClassLoader().getResourceAsStream(preset.resourcePath())) {
            if (input == null) {
                System.err.println("Missing bundled example preset resource: " + preset.resourcePath());
                return false;
            }
            tempPath = Files.createTempFile(presetsDirectory, ".pathmind-onboarding-", ".json");
            Files.copy(input, tempPath, StandardCopyOption.REPLACE_EXISTING);
            return NodeGraphPersistence.normalizeNodeGraphToPath(tempPath, targetPath);
        } catch (IOException e) {
            System.err.println("Failed to install example preset '" + preset.name() + "': " + e.getMessage());
            return false;
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    System.err.println("Failed to clean up temporary example preset: " + e.getMessage());
                }
            }
        }
    }

    public record RestoreResult(boolean success, int restoredCount, List<String> restoredNames,
                                int skippedCount, List<String> failedNames) {
        private static RestoreResult failed(List<String> failedNames) {
            return new RestoreResult(false, 0, List.of(), 0, failedNames);
        }
    }

    record ExamplePreset(String name, String resourcePath) {
    }
}
