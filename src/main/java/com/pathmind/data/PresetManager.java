package com.pathmind.data;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class that manages Pathmind workspace presets.
 */
public final class PresetManager {
    private static final String BASE_DIRECTORY_NAME = "pathmind";
    private static final String PRESETS_DIRECTORY_NAME = "presets";
    private static final String ACTIVE_PRESET_FILE_NAME = "active_preset.txt";
    private static final String MARKETPLACE_SAVED_PRESETS_FILE_NAME = "marketplace_saved_presets.txt";
    private static final String MARKETPLACE_LINKS_FILE_NAME = "marketplace_preset_links.txt";
    private static final String DEFAULT_PRESET_NAME = "Default";
    private static final String IMPORTED_PRESET_FALLBACK_NAME = "Imported Workspace";

    private PresetManager() {
    }

    /**
     * Ensure the base directories exist and that there is always an active preset defined.
     */
    public static void initialize() {
        try {
            ensureDirectoryExists(getBaseDirectory());
            ensureDirectoryExists(getPresetsDirectory());
            ensureActivePresetFile();
        } catch (IOException e) {
            System.err.println("Failed to initialize preset directories: " + e.getMessage());
        }
    }

    /**
     * Get the currently active preset name.
     */
    public static String getActivePreset() {
        initialize();
        Path activePresetFile = getBaseDirectory().resolve(ACTIVE_PRESET_FILE_NAME);
        if (Files.exists(activePresetFile)) {
            try {
                String value = Files.readString(activePresetFile, StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            } catch (IOException e) {
                System.err.println("Failed to read active preset: " + e.getMessage());
            }
        }
        return DEFAULT_PRESET_NAME;
    }

    /**
     * Set the active preset name.
     */
    public static void setActivePreset(String presetName) {
        String sanitized = sanitizePresetName(presetName);
        if (sanitized.isEmpty()) {
            return;
        }
        initialize();
        try {
            Files.writeString(getBaseDirectory().resolve(ACTIVE_PRESET_FILE_NAME), sanitized, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write active preset: " + e.getMessage());
        }
    }

    /**
     * List all available presets.
     */
    public static List<String> getAvailablePresets() {
        initialize();
        List<String> presets = new ArrayList<>();
        Path presetsDirectory = getPresetsDirectory();
        try (Stream<Path> pathStream = Files.list(presetsDirectory)) {
            presets = pathStream
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                .map(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.substring(0, fileName.length() - 5);
                })
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            System.err.println("Failed to list presets: " + e.getMessage());
        }

        if (presets.stream().noneMatch(name -> name.equalsIgnoreCase(DEFAULT_PRESET_NAME))) {
            presets.add(DEFAULT_PRESET_NAME);
        }

        String activePreset = getActivePreset();
        if (presets.stream().noneMatch(name -> name.equalsIgnoreCase(activePreset))) {
            presets.add(activePreset);
        }

        presets.sort(Comparator.comparing(String::toLowerCase));
        return presets;
    }

    /**
     * Create a new preset file and return the sanitized preset name.
     */
    public static Optional<String> createPreset(String presetName) {
        String sanitized = sanitizePresetName(presetName);
        if (sanitized.isEmpty()) {
            return Optional.empty();
        }

        List<String> existing = getAvailablePresets();
        for (String preset : existing) {
            if (preset.equalsIgnoreCase(sanitized)) {
                return Optional.empty();
            }
        }

        Path presetPath = getPresetPath(sanitized);
        if (Files.exists(presetPath)) {
            return Optional.empty();
        }

        try {
            Files.writeString(presetPath, "{}", StandardCharsets.UTF_8);
            return Optional.of(sanitized);
        } catch (IOException e) {
            System.err.println("Failed to create preset: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Copy an external workspace JSON file into the presets directory, creating a preset that
     * matches the file name (sanitized and deduplicated when necessary).
     */
    public static Optional<String> importPresetFromFile(Path sourcePath) {
        String fileName = Optional.ofNullable(sourcePath)
                .map(Path::getFileName)
                .map(Path::toString)
                .orElse("");
        String requestedName = fileName;
        if (requestedName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            requestedName = requestedName.substring(0, requestedName.length() - 5);
        }
        return importPresetFromFile(sourcePath, requestedName);
    }

    public static Optional<String> importPresetFromFile(Path sourcePath, String requestedPresetName) {
        if (sourcePath == null || !Files.exists(sourcePath)) {
            return Optional.empty();
        }

        String baseName = requestedPresetName;
        if (baseName == null || baseName.isBlank()) {
            baseName = Optional.ofNullable(sourcePath.getFileName())
                    .map(Path::toString)
                    .orElse("");
            if (baseName.isEmpty()) {
                baseName = IMPORTED_PRESET_FALLBACK_NAME;
            }
            if (baseName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                baseName = baseName.substring(0, baseName.length() - 5);
            }
        }

        String sanitizedBase = sanitizePresetName(baseName);
        if (sanitizedBase.isEmpty()) {
            sanitizedBase = IMPORTED_PRESET_FALLBACK_NAME;
        }

        String candidateName = sanitizedBase;
        Path targetPath = getPresetPath(candidateName);
        int suffix = 1;
        while (Files.exists(targetPath)) {
            candidateName = sanitizedBase + "-" + suffix;
            targetPath = getPresetPath(candidateName);
            suffix++;
        }

        try {
            ensureDirectoryExists(getPresetsDirectory());
            Files.copy(sourcePath, targetPath);
            return Optional.of(candidateName);
        } catch (IOException e) {
            System.err.println("Failed to import preset from file: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delete the preset file with the provided name.
     *
     * @param presetName name of the preset to delete
     * @return {@code true} if the preset file was removed
     */
    public static boolean deletePreset(String presetName) {
        String sanitized = sanitizePresetName(presetName);
        if (sanitized.isEmpty() || sanitized.equalsIgnoreCase(DEFAULT_PRESET_NAME)) {
            return false;
        }

        initialize();
        Path presetPath = getPresetsDirectory().resolve(sanitized + ".json");
        if (!Files.exists(presetPath)) {
            return false;
        }

        try {
            Files.delete(presetPath);
            clearMarketplaceLinkedPreset(sanitized);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete preset: " + e.getMessage());
            return false;
        }
    }

    public static Set<String> getSavedMarketplacePresetIds() {
        initialize();
        Path savedPresetsFile = getBaseDirectory().resolve(MARKETPLACE_SAVED_PRESETS_FILE_NAME);
        if (!Files.exists(savedPresetsFile)) {
            return Set.of();
        }
        try {
            return Files.readAllLines(savedPresetsFile, StandardCharsets.UTF_8).stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            System.err.println("Failed to read marketplace saved presets: " + e.getMessage());
            return Set.of();
        }
    }

    public static void setMarketplacePresetSaved(String presetId, boolean saved) {
        String normalizedId = presetId == null ? "" : presetId.trim();
        if (normalizedId.isEmpty()) {
            return;
        }
        initialize();
        Set<String> savedPresetIds = new HashSet<>(getSavedMarketplacePresetIds());
        if (saved) {
            savedPresetIds.add(normalizedId);
        } else {
            savedPresetIds.remove(normalizedId);
        }
        Path savedPresetsFile = getBaseDirectory().resolve(MARKETPLACE_SAVED_PRESETS_FILE_NAME);
        try {
            if (savedPresetIds.isEmpty()) {
                Files.deleteIfExists(savedPresetsFile);
                return;
            }
            List<String> sortedIds = new ArrayList<>(savedPresetIds);
            sortedIds.sort(String::compareToIgnoreCase);
            Files.write(savedPresetsFile, sortedIds, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write marketplace saved presets: " + e.getMessage());
        }
    }

    public static Optional<String> getMarketplaceLinkedPresetId(String presetName) {
        return getMarketplaceLinkRecord(presetName).map(MarketplaceLinkRecord::presetId);
    }

    public static Optional<String> getMarketplaceLinkedPresetName(String presetId) {
        String normalizedId = presetId == null ? "" : presetId.trim();
        if (normalizedId.isEmpty()) {
            return Optional.empty();
        }
        return readMarketplaceLinkRecords().values().stream()
            .filter(record -> normalizedId.equalsIgnoreCase(record.presetId()))
            .map(MarketplaceLinkRecord::presetName)
            .findFirst();
    }

    public static void setMarketplaceLinkedPreset(String presetName, String presetId) {
        setMarketplaceLinkedPreset(presetName, presetId, computePresetContentHash(presetName).orElse(""));
    }

    public static void setMarketplaceLinkedPreset(String presetName, String presetId, String syncedHash) {
        String sanitizedName = sanitizePresetName(presetName);
        String normalizedId = presetId == null ? "" : presetId.trim();
        if (sanitizedName.isEmpty() || normalizedId.isEmpty()) {
            return;
        }
        Map<String, MarketplaceLinkRecord> records = new LinkedHashMap<>(readMarketplaceLinkRecords());
        records.entrySet().removeIf(entry -> normalizedId.equalsIgnoreCase(entry.getValue().presetId()) || sanitizedName.equalsIgnoreCase(entry.getKey()));
        records.put(sanitizedName.toLowerCase(Locale.ROOT), new MarketplaceLinkRecord(sanitizedName, normalizedId, syncedHash == null ? "" : syncedHash.trim()));
        writeMarketplaceLinkRecords(records.values());
    }

    public static void clearMarketplaceLinkedPreset(String presetName) {
        String sanitizedName = sanitizePresetName(presetName);
        if (sanitizedName.isEmpty()) {
            return;
        }
        Map<String, MarketplaceLinkRecord> records = new LinkedHashMap<>(readMarketplaceLinkRecords());
        if (records.remove(sanitizedName.toLowerCase(Locale.ROOT)) != null) {
            writeMarketplaceLinkRecords(records.values());
        }
    }

    public static void clearMarketplaceLinkedPresetById(String presetId) {
        String normalizedId = presetId == null ? "" : presetId.trim();
        if (normalizedId.isEmpty()) {
            return;
        }
        Map<String, MarketplaceLinkRecord> records = new LinkedHashMap<>(readMarketplaceLinkRecords());
        boolean removed = records.entrySet().removeIf(entry -> normalizedId.equalsIgnoreCase(entry.getValue().presetId()));
        if (removed) {
            writeMarketplaceLinkRecords(records.values());
        }
    }

    public static boolean hasMarketplaceLinkedPresetChanges(String presetName) {
        Optional<MarketplaceLinkRecord> record = getMarketplaceLinkRecord(presetName);
        if (record.isEmpty()) {
            return false;
        }
        String storedHash = record.get().syncedHash();
        Optional<String> currentHash = computePresetContentHash(presetName);
        if (currentHash.isEmpty()) {
            return false;
        }
        return !currentHash.get().equalsIgnoreCase(storedHash == null ? "" : storedHash);
    }

    public static Optional<String> computePresetContentHash(String presetName) {
        Path presetPath = getPresetPath(presetName);
        if (!Files.exists(presetPath)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(presetPath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return Optional.of(builder.toString());
        } catch (Exception e) {
            System.err.println("Failed to hash preset: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Rename an existing preset file.
     *
     * @param currentName the preset to rename
     * @param desiredName the requested new name
     * @return an {@link Optional} containing the sanitized new name when successful
     */
    public static Optional<String> renamePreset(String currentName, String desiredName) {
        String sanitizedCurrent = sanitizePresetName(currentName);
        String sanitizedDesired = sanitizePresetName(desiredName);
        if (sanitizedCurrent.isEmpty()
                || sanitizedDesired.isEmpty()
                || sanitizedCurrent.equalsIgnoreCase(DEFAULT_PRESET_NAME)
                || sanitizedDesired.equalsIgnoreCase(DEFAULT_PRESET_NAME)) {
            return Optional.empty();
        }

        initialize();
        Path currentPath = getPresetsDirectory().resolve(sanitizedCurrent + ".json");
        if (!Files.exists(currentPath)) {
            return Optional.empty();
        }

        boolean needsMove = !sanitizedCurrent.equals(sanitizedDesired);
        Path desiredPath = getPresetsDirectory().resolve(sanitizedDesired + ".json");
        if (needsMove && Files.exists(desiredPath)) {
            return Optional.empty();
        }

        try {
            if (needsMove) {
                Files.move(currentPath, desiredPath);
            }

            if (getActivePreset().equalsIgnoreCase(sanitizedCurrent)) {
                setActivePreset(sanitizedDesired);
            }
            renameMarketplaceLinkedPreset(sanitizedCurrent, sanitizedDesired);
            return Optional.of(sanitizedDesired);
        } catch (IOException e) {
            System.err.println("Failed to rename preset: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Expose the display name of the default preset.
     */
    public static String getDefaultPresetName() {
        return DEFAULT_PRESET_NAME;
    }

    /**
     * Resolve the save path for a preset.
     */
    public static Path getPresetPath(String presetName) {
        initialize();
        String sanitized = sanitizePresetName(presetName);
        if (sanitized.isEmpty()) {
            sanitized = DEFAULT_PRESET_NAME;
        }
        return getPresetsDirectory().resolve(sanitized + ".json");
    }

    /**
     * Ensure the Pathmind workspace directory exists inside the Minecraft directory.
     */
    public static Path getBaseDirectory() {
        Path minecraftDirectory = getMinecraftDirectory();
        return minecraftDirectory.resolve(BASE_DIRECTORY_NAME);
    }

    private static Path getPresetsDirectory() {
        return getBaseDirectory().resolve(PRESETS_DIRECTORY_NAME);
    }

    private static Path getMinecraftDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.runDirectory != null) {
            return client.runDirectory.toPath();
        }
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader != null) {
                return loader.getGameDir();
            }
        } catch (IllegalStateException ignored) {
            // Unit tests can exercise preset logic before Fabric has finalized its game directory.
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    private static void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private static void ensureActivePresetFile() throws IOException {
        Path activePresetFile = getBaseDirectory().resolve(ACTIVE_PRESET_FILE_NAME);
        if (!Files.exists(activePresetFile)) {
            Files.writeString(activePresetFile, DEFAULT_PRESET_NAME, StandardCharsets.UTF_8);
        }
    }

    private static String sanitizePresetName(String presetName) {
        if (presetName == null) {
            return "";
        }
        String trimmed = presetName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // Replace characters that are invalid for file names on most systems
        String sanitized = trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
        // Collapse multiple spaces to a single space
        sanitized = sanitized.replaceAll("\\s+", " ");
        return sanitized.trim();
    }

    private static Optional<MarketplaceLinkRecord> getMarketplaceLinkRecord(String presetName) {
        String sanitizedName = sanitizePresetName(presetName);
        if (sanitizedName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(readMarketplaceLinkRecords().get(sanitizedName.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, MarketplaceLinkRecord> readMarketplaceLinkRecords() {
        initialize();
        Path linksFile = getBaseDirectory().resolve(MARKETPLACE_LINKS_FILE_NAME);
        if (!Files.exists(linksFile)) {
            return Map.of();
        }
        Map<String, MarketplaceLinkRecord> records = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(linksFile, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", 3);
                if (parts.length < 2) {
                    continue;
                }
                String presetName = sanitizePresetName(parts[0]);
                String presetId = parts[1].trim();
                String syncedHash = parts.length >= 3 ? parts[2].trim() : "";
                if (!presetName.isEmpty() && !presetId.isEmpty()) {
                    records.put(presetName.toLowerCase(Locale.ROOT), new MarketplaceLinkRecord(presetName, presetId, syncedHash));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read marketplace preset links: " + e.getMessage());
        }
        return records;
    }

    private static void writeMarketplaceLinkRecords(Iterable<MarketplaceLinkRecord> records) {
        Path linksFile = getBaseDirectory().resolve(MARKETPLACE_LINKS_FILE_NAME);
        List<String> lines = new ArrayList<>();
        for (MarketplaceLinkRecord record : records) {
            if (record == null || record.presetName() == null || record.presetId() == null) {
                continue;
            }
            lines.add(record.presetName() + "\t" + record.presetId() + "\t" + (record.syncedHash() == null ? "" : record.syncedHash()));
        }
        try {
            if (lines.isEmpty()) {
                Files.deleteIfExists(linksFile);
            } else {
                Files.write(linksFile, lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("Failed to write marketplace preset links: " + e.getMessage());
        }
    }

    private static void renameMarketplaceLinkedPreset(String currentName, String desiredName) {
        String sanitizedCurrent = sanitizePresetName(currentName);
        String sanitizedDesired = sanitizePresetName(desiredName);
        if (sanitizedCurrent.isEmpty() || sanitizedDesired.isEmpty() || sanitizedCurrent.equalsIgnoreCase(sanitizedDesired)) {
            return;
        }
        Map<String, MarketplaceLinkRecord> records = new LinkedHashMap<>(readMarketplaceLinkRecords());
        MarketplaceLinkRecord record = records.remove(sanitizedCurrent.toLowerCase(Locale.ROOT));
        if (record == null) {
            return;
        }
        records.put(sanitizedDesired.toLowerCase(Locale.ROOT), new MarketplaceLinkRecord(sanitizedDesired, record.presetId(), record.syncedHash()));
        writeMarketplaceLinkRecords(records.values());
    }

    private record MarketplaceLinkRecord(String presetName, String presetId, String syncedHash) {
    }
}
