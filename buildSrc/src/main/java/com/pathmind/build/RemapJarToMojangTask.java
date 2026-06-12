package com.pathmind.build;

import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public abstract class RemapJarToMojangTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getYarnMappings();

    @Input
    public abstract Property<String> getNeoForgeVersion();

    @Classpath
    public abstract ConfigurableFileCollection getRemapClasspath();

    @TaskAction
    public void remapJar() throws IOException {
        Path input = getInputJar().get().getAsFile().toPath();
        Path output = getOutputJar().get().getAsFile().toPath();
        Path mappings = findMappingsFile();

        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);

        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createTinyMappingProvider(mappings, "named", "mojang"))
            .ignoreConflicts(true)
            .extension(new MixinExtension())
            .build();

        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            List<Path> classpath = getRemapClasspath().getFiles().stream()
                .map(File::toPath)
                .toList();
            if (!classpath.isEmpty()) {
                remapper.readClassPath(classpath.toArray(Path[]::new));
            }

            remapper.readInputs(input);
            consumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
    }

    private Path findMappingsFile() throws IOException {
        String minecraftVersion = getMinecraftVersion().get();
        String yarnMappings = getYarnMappings().get();
        String neoForgeVersion = getNeoForgeVersion().get();
        Path loomVersionCache = Path.of(
            System.getProperty("user.home"),
            ".gradle",
            "caches",
            "fabric-loom",
            minecraftVersion
        );

        if (!Files.isDirectory(loomVersionCache)) {
            throw new GradleException("Fabric Loom cache was not found for Minecraft " + minecraftVersion + ": " + loomVersionCache);
        }

        String neoForgeToken = ("neoforge-" + neoForgeVersion).toLowerCase(Locale.ROOT);

        try (Stream<Path> paths = Files.walk(loomVersionCache, 4)) {
            List<Path> candidates = paths
                .filter(path -> path.getFileName().toString().equals("mappings-mojang.tiny"))
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains(neoForgeToken))
                .sorted(Comparator.comparing(Path::toString))
                .toList();

            return candidates.stream()
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains("net.fabricmc.yarn"))
                .filter(path -> path.toString().contains(yarnMappings))
                .findFirst()
                .or(() -> candidates.stream().findFirst())
                .orElseThrow(() -> new GradleException(
                    "Could not find Yarn to Mojang mappings for Minecraft " + minecraftVersion +
                        " / Yarn " + yarnMappings + " / NeoForge " + neoForgeVersion +
                        " under " + loomVersionCache +
                        ". Run :common:transformProductionNeoForge once so Architectury Loom can generate mappings."
                ));
        }
    }
}
