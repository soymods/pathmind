package com.pathmind.schematic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
}
