package com.pathmind.build;

import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
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
import java.util.Objects;
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
        IMappingProvider mappings = createYarnNamedToMojangMappingProvider();

        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);

        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(mappings)
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

    private IMappingProvider createYarnNamedToMojangMappingProvider() throws IOException {
        Path yarnMappings = findExactYarnMappingsFile();
        Path mojangMappings = findNeoForgeMojangMappingsFile();

        MemoryMappingTree yarnTree = readMappings(yarnMappings);
        MemoryMappingTree mojangTree = readMappings(mojangMappings);

        int yarnIntermediary = namespaceId(yarnTree, "intermediary", yarnMappings);
        int yarnNamed = namespaceId(yarnTree, "named", yarnMappings);
        int mojangIntermediary = namespaceId(mojangTree, "intermediary", mojangMappings);
        int mojangNamespace = namespaceId(mojangTree, "mojang", mojangMappings);

        return acceptor -> {
            for (MappingTreeView.ClassMappingView yarnClass : yarnTree.getClasses()) {
                String sourceClassName = yarnClass.getName(yarnNamed);
                String intermediaryClassName = yarnClass.getName(yarnIntermediary);
                MappingTreeView.ClassMappingView mojangClass = mojangTree.getClass(intermediaryClassName, mojangIntermediary);
                if (sourceClassName == null || intermediaryClassName == null || mojangClass == null) {
                    continue;
                }

                String targetClassName = mojangClass.getName(mojangNamespace);
                if (targetClassName != null && !Objects.equals(sourceClassName, targetClassName)) {
                    acceptor.acceptClass(sourceClassName, targetClassName);
                }

                for (MappingTreeView.MethodMappingView yarnMethod : yarnClass.getMethods()) {
                    String sourceName = yarnMethod.getName(yarnNamed);
                    String sourceDesc = yarnMethod.getDesc(yarnNamed);
                    String intermediaryName = yarnMethod.getName(yarnIntermediary);
                    String intermediaryDesc = yarnMethod.getDesc(yarnIntermediary);
                    if (sourceName == null || sourceDesc == null || intermediaryName == null || intermediaryDesc == null) {
                        continue;
                    }
                    MappingTreeView.MethodMappingView mojangMethod =
                        mojangClass.getMethod(intermediaryName, intermediaryDesc, mojangIntermediary);
                    if (mojangMethod == null) {
                        continue;
                    }
                    String targetName = mojangMethod.getName(mojangNamespace);
                    if (targetName != null && !Objects.equals(sourceName, targetName)) {
                        acceptor.acceptMethod(new IMappingProvider.Member(sourceClassName, sourceName, sourceDesc), targetName);
                    }
                }

                for (MappingTreeView.FieldMappingView yarnField : yarnClass.getFields()) {
                    String sourceName = yarnField.getName(yarnNamed);
                    String sourceDesc = yarnField.getDesc(yarnNamed);
                    String intermediaryName = yarnField.getName(yarnIntermediary);
                    String intermediaryDesc = yarnField.getDesc(yarnIntermediary);
                    if (sourceName == null || sourceDesc == null || intermediaryName == null || intermediaryDesc == null) {
                        continue;
                    }
                    MappingTreeView.FieldMappingView mojangField =
                        mojangClass.getField(intermediaryName, intermediaryDesc, mojangIntermediary);
                    if (mojangField == null) {
                        continue;
                    }
                    String targetName = mojangField.getName(mojangNamespace);
                    if (targetName != null && !Objects.equals(sourceName, targetName)) {
                        acceptor.acceptField(new IMappingProvider.Member(sourceClassName, sourceName, sourceDesc), targetName);
                    }
                }
            }
        };
    }

    private static MemoryMappingTree readMappings(Path mappings) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(mappings, tree);
        return tree;
    }

    private static int namespaceId(MappingTreeView tree, String namespace, Path mappings) {
        int namespaceId = tree.getNamespaceId(namespace);
        if (namespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw new GradleException("Mapping namespace '" + namespace + "' was not found in " + mappings);
        }
        return namespaceId;
    }

    private Path findExactYarnMappingsFile() throws IOException {
        Path loomVersionCache = loomVersionCache();
        String yarnMappings = getYarnMappings().get();
        String yarnToken = ("net.fabricmc.yarn." + getMinecraftVersion().get().replace(".", "_") + "." + yarnMappings + "-v2")
            .toLowerCase(Locale.ROOT);

        try (Stream<Path> paths = Files.walk(loomVersionCache, 4)) {
            return paths
                .filter(path -> path.getFileName().toString().equals("mappings.tiny"))
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains(yarnToken))
                .findFirst()
                .orElseThrow(() -> new GradleException(
                    "Could not find exact Yarn mappings " + yarnMappings + " under " + loomVersionCache +
                        ". Run :common:compileJava once so Architectury Loom can generate mappings."
                ));
        }
    }

    private Path findNeoForgeMojangMappingsFile() throws IOException {
        Path loomVersionCache = loomVersionCache();
        String minecraftVersion = getMinecraftVersion().get();
        String yarnMappings = getYarnMappings().get();
        String neoForgeVersion = getNeoForgeVersion().get();
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

    private Path loomVersionCache() {
        String minecraftVersion = getMinecraftVersion().get();
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
        return loomVersionCache;
    }
}
