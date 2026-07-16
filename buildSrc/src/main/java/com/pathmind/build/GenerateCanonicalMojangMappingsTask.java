package com.pathmind.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a complete Tiny v2 mapping set that gives an older Minecraft target
 * the canonical Mojang class names used by Pathmind's authored source while
 * canonicalizing member names that appear in authored Pathmind source, and
 * retaining conflict-safe target names for unrelated Minecraft internals.
 * Intermediary identifiers are used only as the stable join key between two
 * official mapping sets.
 */
public abstract class GenerateCanonicalMojangMappingsTask extends DefaultTask {
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getTargetMappings();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getTargetComposedMappings();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getCanonicalMappings();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAuthoredSources();

    @OutputFile
    public abstract RegularFileProperty getOutputMappings();

    @TaskAction
    public void generate() throws IOException {
        TinyMappings target = TinyMappings.read(getTargetMappings().get().getAsFile().toPath());
        TinyMappings targetComposed = TinyMappings.read(getTargetComposedMappings().get().getAsFile().toPath());
        TinyMappings canonical = TinyMappings.read(getCanonicalMappings().get().getAsFile().toPath());
        Set<String> authoredMemberNames = readAuthoredIdentifiers();
        Path output = getOutputMappings().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());

        StringBuilder mappings = new StringBuilder(6_000_000);
        mappings.append("tiny\t2\t0\tintermediary\tnamed\n");

        Map<String, String> targetNameOwners = new HashMap<>();
        for (ClassEntry targetClass : target.classes.values()) {
            String previousOwner = targetNameOwners.put(targetClass.namedName, targetClass.intermediaryName);
            if (previousOwner != null) {
                throw new GradleException(
                    "Target mappings contain duplicate class name '" + targetClass.namedName + "' for "
                        + previousOwner + " and " + targetClass.intermediaryName
                );
            }
        }

        Map<String, String> selectedClassNames = new LinkedHashMap<>();
        Map<String, String> selectedNameOwners = new HashMap<>();
        Set<String> collisionFallbacks = new HashSet<>();
        for (ClassEntry targetClass : target.classes.values()) {
            ClassEntry canonicalClass = canonical.classes.get(targetClass.intermediaryName);
            String proposedName = canonicalClass != null
                && !canonicalClass.namedName.startsWith("net/minecraft/class_")
                ? canonicalClass.namedName
                : targetClass.namedName;

            String targetOwner = targetNameOwners.get(proposedName);
            String selectedOwner = selectedNameOwners.get(proposedName);
            boolean collidesWithTarget = targetOwner != null
                && !targetOwner.equals(targetClass.intermediaryName);
            boolean collidesWithSelection = selectedOwner != null
                && !selectedOwner.equals(targetClass.intermediaryName);
            String className = collidesWithTarget || collidesWithSelection
                ? targetClass.namedName
                : proposedName;

            if (!className.equals(proposedName)) {
                collisionFallbacks.add(targetClass.intermediaryName);
            }
            String previousOwner = selectedNameOwners.putIfAbsent(className, targetClass.intermediaryName);
            if (previousOwner != null && !previousOwner.equals(targetClass.intermediaryName)) {
                throw new GradleException(
                    "Unable to make canonical class mappings unique: '" + className + "' is selected for "
                        + previousOwner + " and " + targetClass.intermediaryName
                );
            }
            selectedClassNames.put(targetClass.intermediaryName, className);
        }

        for (ClassEntry targetClass : target.classes.values()) {
            String className = selectedClassNames.get(targetClass.intermediaryName);
            mappings.append("c\t").append(targetClass.intermediaryName).append('\t').append(className).append('\n');
            for (MemberEntry targetMember : targetClass.members) {
                ClassEntry canonicalClass = canonical.classes.get(targetClass.intermediaryName);
                ClassEntry composedTargetClass = targetComposed.classes.get(targetClass.intermediaryName);
                MemberEntry canonicalMember = canonicalClass == null
                    ? null
                    : canonicalClass.canonicalMember(targetMember);
                MemberEntry composedTargetMember = composedTargetClass == null
                    ? null
                    : composedTargetClass.canonicalMember(targetMember);
                boolean targetNameWasConflictAdjusted = composedTargetMember != null
                    && !composedTargetMember.namedName.equals(targetMember.namedName);
                String memberName = !targetNameWasConflictAdjusted
                    && canonicalMember != null
                    && authoredMemberNames.contains(canonicalMember.namedName)
                    && !canonicalMember.namedName.startsWith("method_")
                    && !canonicalMember.namedName.startsWith("field_")
                    ? canonicalMember.namedName
                    : targetMember.namedName;
                mappings.append('\t').append(targetMember.kind).append('\t')
                    .append(targetMember.intermediaryDescriptor).append('\t')
                    .append(targetMember.intermediaryName).append('\t')
                    .append(memberName).append('\n');
            }
        }
        getLogger().info(
            "Retained {} target-version class names to avoid canonical-name collisions.",
            collisionFallbacks.size()
        );

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            zip.putNextEntry(new ZipEntry("mappings/mappings.tiny"));
            zip.write(mappings.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private Set<String> readAuthoredIdentifiers() throws IOException {
        Set<String> identifiers = new HashSet<>();
        for (java.io.File source : getAuthoredSources().getFiles()) {
            Matcher matcher = JAVA_IDENTIFIER.matcher(Files.readString(source.toPath(), StandardCharsets.UTF_8));
            while (matcher.find()) {
                identifiers.add(matcher.group());
            }
        }
        return identifiers;
    }

    private record MemberKey(String kind, String intermediaryName, String intermediaryDescriptor) {}

    private record MemberNameKey(String kind, String intermediaryName) {}

    private record MemberEntry(
        String kind,
        String intermediaryDescriptor,
        String intermediaryName,
        String namedName
    ) {
        MemberKey key() {
            return new MemberKey(kind, intermediaryName, intermediaryDescriptor);
        }

        MemberNameKey nameKey() {
            return new MemberNameKey(kind, intermediaryName);
        }
    }

    private static final class ClassEntry {
        private final String intermediaryName;
        private final String namedName;
        private final List<MemberEntry> members = new ArrayList<>();
        private final Map<MemberKey, MemberEntry> membersByIntermediary = new HashMap<>();
        private final Map<MemberNameKey, MemberEntry> membersByIntermediaryName = new HashMap<>();

        private ClassEntry(String intermediaryName, String namedName) {
            this.intermediaryName = intermediaryName;
            this.namedName = namedName;
        }

        private void add(MemberEntry member) {
            members.add(member);
            membersByIntermediary.put(member.key(), member);
            membersByIntermediaryName.putIfAbsent(member.nameKey(), member);
        }

        private MemberEntry canonicalMember(MemberEntry targetMember) {
            MemberEntry exact = membersByIntermediary.get(targetMember.key());
            return exact != null ? exact : membersByIntermediaryName.get(targetMember.nameKey());
        }
    }

    private static final class TinyMappings {
        private final Map<String, ClassEntry> classes = new LinkedHashMap<>();

        private static TinyMappings read(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new GradleException("Empty mappings file: " + file);
            }
            String[] header = lines.get(0).split("\\t", -1);
            if (header.length < 6 || !"tiny".equals(header[0]) || !"2".equals(header[1])) {
                throw new GradleException("Expected Tiny v2 mappings with official/intermediary/named namespaces: " + file);
            }

            int officialIndex = namespaceIndex(header, "official");
            int intermediaryIndex = namespaceIndex(header, "intermediary");
            int namedIndex = containsNamespace(header, "mojang")
                ? namespaceIndex(header, "mojang")
                : namespaceIndex(header, "named");
            Map<String, String> officialToIntermediaryClasses = new HashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.startsWith("c\t")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                officialToIntermediaryClasses.put(
                    columns[1 + officialIndex],
                    columns[1 + intermediaryIndex]
                );
            }

            TinyMappings mappings = new TinyMappings();
            ClassEntry currentClass = null;
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("c\t")) {
                    String[] columns = line.split("\\t", -1);
                    currentClass = new ClassEntry(columns[1 + intermediaryIndex], columns[1 + namedIndex]);
                    mappings.classes.put(currentClass.intermediaryName, currentClass);
                } else if (currentClass != null && (line.startsWith("\tf\t") || line.startsWith("\tm\t"))) {
                    String[] columns = line.split("\\t", -1);
                    String intermediaryDescriptor = remapDescriptor(columns[2], officialToIntermediaryClasses);
                    currentClass.add(new MemberEntry(
                        columns[1],
                        intermediaryDescriptor,
                        columns[3 + intermediaryIndex],
                        columns[3 + namedIndex]
                    ));
                }
            }
            return mappings;
        }

        private static int namespaceIndex(String[] header, String namespace) {
            for (int i = 3; i < header.length; i++) {
                if (namespace.equals(header[i])) {
                    return i - 3;
                }
            }
            throw new GradleException("Missing mapping namespace '" + namespace + "'");
        }

        private static boolean containsNamespace(String[] header, String namespace) {
            for (int i = 3; i < header.length; i++) {
                if (namespace.equals(header[i])) {
                    return true;
                }
            }
            return false;
        }

        private static String remapDescriptor(String descriptor, Map<String, String> classes) {
            StringBuilder result = new StringBuilder(descriptor.length());
            for (int i = 0; i < descriptor.length(); i++) {
                char character = descriptor.charAt(i);
                result.append(character);
                if (character != 'L') {
                    continue;
                }
                int end = descriptor.indexOf(';', i);
                if (end < 0) {
                    throw new GradleException("Invalid JVM descriptor: " + descriptor);
                }
                String className = descriptor.substring(i + 1, end);
                result.append(classes.getOrDefault(className, className));
                result.append(';');
                i = end;
            }
            return result.toString();
        }
    }
}
