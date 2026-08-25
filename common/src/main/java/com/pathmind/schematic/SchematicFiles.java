package com.pathmind.schematic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Resolves schematic names relative to the active Minecraft profile. */
public final class SchematicFiles {
    private SchematicFiles() {
    }

    public static Optional<Path> resolve(Path gameDirectory, String schematicName) {
        if (gameDirectory == null || schematicName == null || schematicName.isBlank()) {
            return Optional.empty();
        }
        Path relative;
        try {
            relative = Path.of(schematicName.trim()).normalize();
        } catch (Exception ignored) {
            return Optional.empty();
        }
        if (relative.isAbsolute() || relative.startsWith("..")) {
            return Optional.empty();
        }
        Path root = gameDirectory.resolve("schematics").normalize();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    /** Lists supported schematic names relative to the active profile's schematics directory. */
    public static List<String> list(Path gameDirectory) {
        if (gameDirectory == null) {
            return List.of();
        }
        Path root = gameDirectory.resolve("schematics").normalize();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(root::relativize)
                .map(Path::toString)
                .map(name -> name.replace('\\', '/'))
                .filter(SchematicFiles::isSupported)
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean isSupported(String name) {
        return name.toLowerCase().endsWith(".schem");
    }
}
