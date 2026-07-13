package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
            return new RestoreResult(false, 0, List.of());
        }
        Path presetsDirectory = baseDirectory.resolve(PRESETS_DIRECTORY_NAME);
        try {
            Files.createDirectories(presetsDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create example preset directory: " + e.getMessage());
            return new RestoreResult(false, 0, List.of());
        }

        int restoredCount = 0;
        List<String> restoredNames = new ArrayList<>();
        for (ExamplePreset preset : createExamplePresets()) {
            Path presetPath = presetsDirectory.resolve(preset.name() + ".json");
            if (!overwriteExisting && Files.exists(presetPath)) {
                continue;
            }
            if (NodeGraphPersistence.saveNodeGraphToPath(preset.nodes(), preset.connections(), presetPath)) {
                restoredCount++;
                restoredNames.add(preset.name());
            }
        }
        return new RestoreResult(true, restoredCount, restoredNames);
    }

    static List<ExamplePreset> createExamplePresets() {
        return List.of(
            emptyPreset("Basics"),
            emptyPreset("Variables and If Else"),
            emptyPreset("Relative Variables and Lists")
        );
    }

    private static ExamplePreset emptyPreset(String name) {
        return new ExamplePreset(name, Collections.emptyList(), Collections.emptyList());
    }

    public record RestoreResult(boolean success, int restoredCount, List<String> restoredNames) {
    }

    record ExamplePreset(String name, List<Node> nodes, List<NodeConnection> connections) {
    }
}
