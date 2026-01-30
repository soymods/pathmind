package com.pathmind.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class that manages Pathmind user settings.
 * Settings are persisted to ~/.minecraft/pathmind/settings.json
 */
public final class SettingsManager {
    private static final String BASE_DIRECTORY_NAME = "pathmind";
    private static final String SETTINGS_FILE_NAME = "settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SettingsManager() {
    }

    /**
     * Settings data class containing all user preferences.
     */
    public static class Settings {
        public String language = "en_us";
        public String accentColor = "sky";
        public boolean showGrid = true;
        public boolean showTooltips = true;

        public Settings() {
        }

        public Settings(String language, String accentColor, boolean showGrid, boolean showTooltips) {
            this.language = language;
            this.accentColor = accentColor;
            this.showGrid = showGrid;
            this.showTooltips = showTooltips;
        }
    }

    /**
     * Ensure the base directory exists.
     */
    public static void initialize() {
        try {
            ensureDirectoryExists(getBaseDirectory());
        } catch (IOException e) {
            System.err.println("Failed to initialize settings directory: " + e.getMessage());
        }
    }

    /**
     * Load settings from disk. If the file doesn't exist, returns default settings.
     */
    public static Settings load() {
        initialize();
        Path settingsFile = getSettingsPath();

        if (Files.exists(settingsFile)) {
            try {
                String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
                Settings settings = GSON.fromJson(json, Settings.class);
                if (settings != null) {
                    return settings;
                }
            } catch (IOException e) {
                System.err.println("Failed to read settings file: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Failed to parse settings file: " + e.getMessage());
            }
        }

        // Return default settings if file doesn't exist or failed to load
        return new Settings();
    }

    /**
     * Save settings to disk.
     */
    public static void save(Settings settings) {
        if (settings == null) {
            return;
        }

        initialize();
        try {
            String json = GSON.toJson(settings);
            Files.writeString(getSettingsPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    /**
     * Get the path to the settings file.
     */
    private static Path getSettingsPath() {
        return getBaseDirectory().resolve(SETTINGS_FILE_NAME);
    }

    /**
     * Ensure the Pathmind directory exists inside the Minecraft directory.
     */
    private static Path getBaseDirectory() {
        Path minecraftDirectory = getMinecraftDirectory();
        return minecraftDirectory.resolve(BASE_DIRECTORY_NAME);
    }

    private static Path getMinecraftDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.runDirectory != null) {
            return client.runDirectory.toPath();
        }
        FabricLoader loader = FabricLoader.getInstance();
        if (loader != null) {
            return loader.getGameDir();
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    private static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }
}
