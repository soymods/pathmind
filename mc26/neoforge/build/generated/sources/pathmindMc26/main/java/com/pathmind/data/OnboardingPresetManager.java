package com.pathmind.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Installs the bundled learning presets for new Pathmind workspaces.
 */
public final class OnboardingPresetManager {
    private static final String INSTALL_MARKER_FILE_NAME = "example_presets_installed.txt";
    private static final String PRESETS_DIRECTORY_NAME = "presets";
    private static final String ACTIVE_PRESET_FILE_NAME = "active_preset.txt";
    public static final String TUTORIAL_PRESET_NAME = "Example 1";

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

    public static boolean ensureTutorialPresetInstalled() {
        return ensureTutorialPresetInstalled(PresetManager.getBaseDirectory());
    }

    static boolean ensureTutorialPresetInstalled(Path baseDirectory) {
        if (baseDirectory == null) {
            return false;
        }
        migrateLegacyExamplePresetNames(baseDirectory);
        Path presetsDirectory = baseDirectory.resolve(PRESETS_DIRECTORY_NAME);
        try {
            Files.createDirectories(presetsDirectory);
            if (findPresetWithExactFileName(presetsDirectory, TUTORIAL_PRESET_NAME + ".json") != null) {
                return true;
            }
        } catch (IOException e) {
            System.err.println("Failed to prepare the tutorial preset directory: " + e.getMessage());
            return false;
        }
        ExamplePreset tutorialPreset = createExamplePresets().getFirst();
        return installExamplePreset(
            tutorialPreset,
            presetsDirectory,
            presetsDirectory.resolve(tutorialPreset.name() + ".json")
        );
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
            resourcePreset(TUTORIAL_PRESET_NAME, "example_1.json"),
            resourcePreset("Example 2", "example_2.json"),
            resourcePreset("Example 3", "example_3.json")
        );
    }

    static void migrateLegacyExamplePresetNames(Path baseDirectory) {
        if (baseDirectory == null) {
            return;
        }
        Path presetsDirectory = baseDirectory.resolve(PRESETS_DIRECTORY_NAME);
        if (!Files.isDirectory(presetsDirectory)) {
            return;
        }
        for (ExamplePreset preset : createExamplePresets()) {
            String canonicalFileName = preset.name() + ".json";
            String legacyFileName = preset.name().toLowerCase(Locale.ROOT) + ".json";
            try {
                Path canonicalPath = findPresetWithExactFileName(presetsDirectory, canonicalFileName);
                Path legacyPath = findPresetWithExactFileName(presetsDirectory, legacyFileName);
                if (canonicalPath == null && legacyPath != null) {
                    moveWithCaseChange(legacyPath, presetsDirectory.resolve(canonicalFileName));
                }
            } catch (IOException e) {
                System.err.println("Failed to capitalize bundled preset '" + preset.name() + "': " + e.getMessage());
            }
        }
        migrateActivePresetName(baseDirectory);
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

    private static Path findPresetWithExactFileName(Path presetsDirectory, String fileName) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.list(presetsDirectory)) {
            return paths
                .filter(path -> fileName.equals(path.getFileName().toString()))
                .findFirst()
                .orElse(null);
        }
    }

    private static void moveWithCaseChange(Path sourcePath, Path targetPath) throws IOException {
        Path temporaryPath = Files.createTempFile(sourcePath.getParent(), ".pathmind-preset-case-", ".json");
        Files.deleteIfExists(temporaryPath);
        try {
            Files.move(sourcePath, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (Files.exists(temporaryPath)) {
                try {
                    Files.move(temporaryPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            throw e;
        }
    }

    private static void migrateActivePresetName(Path baseDirectory) {
        Path activePresetPath = baseDirectory.resolve(ACTIVE_PRESET_FILE_NAME);
        if (!Files.isRegularFile(activePresetPath)) {
            return;
        }
        try {
            String activePreset = Files.readString(activePresetPath, StandardCharsets.UTF_8).trim();
            for (ExamplePreset preset : createExamplePresets()) {
                String legacyName = preset.name().toLowerCase(Locale.ROOT);
                if (legacyName.equals(activePreset)) {
                    Files.writeString(activePresetPath, preset.name(), StandardCharsets.UTF_8);
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to capitalize the active example preset name: " + e.getMessage());
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
