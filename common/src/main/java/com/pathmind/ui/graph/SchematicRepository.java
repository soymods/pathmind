package com.pathmind.ui.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;

final class SchematicRepository {

    private SchematicRepository() {
    }

    static List<String> loadSchematicOptions() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameDirectory == null) {
            return List.of();
        }

        Path runDir = client.gameDirectory.toPath();
        List<Path> roots = new ArrayList<>();
        roots.add(runDir.resolve("schematics"));
        roots.add(runDir.resolve("baritone").resolve("schematics"));
        roots.add(runDir.resolve("litematica").resolve("schematics"));
        roots.addAll(resolveMinecraftSchematicRoots());

        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root, 12)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".schem") || name.endsWith(".schematic") || name.endsWith(".nbt")
                            || name.endsWith(".litematic");
                    })
                    .forEach(path -> {
                        Path relative = root.relativize(path);
                        String normalized = relative.toString().replace(java.io.File.separatorChar, '/');
                        results.add(normalized);
                    });
            } catch (Exception ignored) {
            }
        }

        List<String> options = new ArrayList<>(results);
        Collections.sort(options);
        return options;
    }

    private static List<Path> resolveMinecraftSchematicRoots() {
        List<Path> roots = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home == null) {
            return roots;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            Path macRoot = Path.of(home, "Library", "Application Support", "minecraft");
            roots.add(macRoot.resolve("schematics"));
            roots.add(macRoot.resolve("baritone").resolve("schematics"));
            roots.add(macRoot.resolve("litematica").resolve("schematics"));
            Path dotRoot = Path.of(home, ".minecraft");
            roots.add(dotRoot.resolve("schematics"));
            roots.add(dotRoot.resolve("baritone").resolve("schematics"));
            roots.add(dotRoot.resolve("litematica").resolve("schematics"));
        } else if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                Path winRoot = Path.of(appData, ".minecraft");
                roots.add(winRoot.resolve("schematics"));
                roots.add(winRoot.resolve("baritone").resolve("schematics"));
                roots.add(winRoot.resolve("litematica").resolve("schematics"));
            }
        } else {
            Path linuxRoot = Path.of(home, ".minecraft");
            roots.add(linuxRoot.resolve("schematics"));
            roots.add(linuxRoot.resolve("baritone").resolve("schematics"));
            roots.add(linuxRoot.resolve("litematica").resolve("schematics"));
        }
        return roots;
    }

    static boolean schematicExistsInRoots(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameDirectory == null) {
            return false;
        }
        Path runDir = client.gameDirectory.toPath();
        List<Path> roots = new ArrayList<>();
        roots.add(runDir.resolve("schematics"));
        roots.add(runDir.resolve("baritone").resolve("schematics"));
        roots.add(runDir.resolve("litematica").resolve("schematics"));
        roots.addAll(resolveMinecraftSchematicRoots());
        for (Path root : roots) {
            if (Files.isRegularFile(root.resolve(value))) {
                return true;
            }
        }
        return false;
    }
}
