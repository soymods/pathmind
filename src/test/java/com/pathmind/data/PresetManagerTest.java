package com.pathmind.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetManagerTest {
    @TempDir
    Path tempHome;

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void initializeCreatesDefaultWorkspaceFiles() {
        PresetManager.initialize();

        Path baseDirectory = tempHome.resolve(".minecraft").resolve("pathmind");
        assertTrue(Files.isDirectory(baseDirectory));
        assertTrue(Files.isDirectory(baseDirectory.resolve("presets")));
        assertEquals("Default", PresetManager.getActivePreset());
        assertTrue(PresetManager.getAvailablePresets().contains("Default"));
    }

    @Test
    void createPresetSanitizesNamesAndRejectsDuplicates() {
        Optional<String> created = PresetManager.createPreset("  Bad/Name: One  ");

        assertEquals(Optional.of("Bad_Name_ One"), created);
        assertTrue(Files.exists(PresetManager.getPresetPath("Bad_Name_ One")));
        assertFalse(PresetManager.createPreset("bad_name_ one").isPresent());
    }

    @Test
    void importPresetDeduplicatesSanitizedTargetNames() throws IOException {
        Path source = tempHome.resolve("Example.json");
        Files.writeString(source, "{}", StandardCharsets.UTF_8);

        Optional<String> first = PresetManager.importPresetFromFile(source, "Shared/Preset");
        Optional<String> second = PresetManager.importPresetFromFile(source, "Shared/Preset");

        assertEquals(Optional.of("Shared_Preset"), first);
        assertEquals(Optional.of("Shared_Preset-1"), second);
        assertTrue(Files.exists(PresetManager.getPresetPath("Shared_Preset")));
        assertTrue(Files.exists(PresetManager.getPresetPath("Shared_Preset-1")));
    }

    @Test
    void renamePresetUpdatesActivePresetAndMarketplaceLink() {
        assertEquals(Optional.of("Old Name"), PresetManager.createPreset("Old Name"));
        PresetManager.setActivePreset("Old Name");
        PresetManager.setMarketplaceLinkedPreset("Old Name", "preset-123", "synced-hash");

        Optional<String> renamed = PresetManager.renamePreset("Old Name", "New/Name");

        assertEquals(Optional.of("New_Name"), renamed);
        assertEquals("New_Name", PresetManager.getActivePreset());
        assertFalse(Files.exists(PresetManager.getPresetPath("Old Name")));
        assertTrue(Files.exists(PresetManager.getPresetPath("New_Name")));
        assertEquals(Optional.of("preset-123"), PresetManager.getMarketplaceLinkedPresetId("New_Name"));
    }
}
