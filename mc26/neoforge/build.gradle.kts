plugins {
    java
    id("net.neoforged.moddev") version "2.0.141"
}

val repositoryRoot = rootProject.extra["repositoryRoot"] as File
val minecraftVersion = rootProject.extra["minecraftVersion"] as String
val neoForgeVersion = rootProject.extra["neoForgeVersion"] as String
val targetJavaVersion = rootProject.extra["targetJavaVersion"] as Int
val commonSourceFamily = rootProject.extra["commonSourceFamily"] as String
val neoForgeUiFamily = rootProject.extra["neoForgeUiFamily"] as String
@Suppress("UNCHECKED_CAST")
val mc26SourceTransforms = rootProject.extra["mc26SourceTransforms"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val mc26SharedSourceTransforms = rootProject.extra["mc26SharedSourceTransforms"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val mc26VersionSourceTransforms = rootProject.extra["mc26VersionSourceTransforms"] as Map<String, String>
val mc26SourceTransformRevision = rootProject.extra["mc26SourceTransformRevision"] as Int

// See the Fabric mc26 build for the policy behind this generated compatibility
// source tree. Shared and version-specific mechanical API deltas are declared
// once by the mc26 root build so Fabric and NeoForge cannot drift apart.
val prepareMc26Sources by tasks.registering(Sync::class) {
    inputs.property(
        "pathmindMc26SymbolTransforms",
        mc26SourceTransforms.entries.joinToString(";") { "${it.key}->${it.value}" }
    )
    inputs.property("pathmindMc26SourceTransformRevision", mc26SourceTransformRevision)
    from(repositoryRoot.resolve("common/src/main/java"))
    from(repositoryRoot.resolve("common/src/compat/$commonSourceFamily/java"))
    from(repositoryRoot.resolve("neoforge/src/compat/$neoForgeUiFamily/java"))
    into(layout.buildDirectory.dir("generated/sources/pathmindMc26/main/java"))
    filteringCharset = "UTF-8"
    filter { line: String ->
        var transformed = line
        mc26SharedSourceTransforms.forEach { (source, target) ->
            transformed = transformed.replace(source, target)
        }
        if (!line.trimStart().startsWith("import ") && !line.trimStart().startsWith("package ")) {
            mc26VersionSourceTransforms.forEach { (source, target) ->
                transformed = transformed.replace(source, target)
            }
        }
        transformed = Regex("""\.displayClientMessage\((.*), false\);""").replace(transformed) {
            ".sendSystemMessage(${it.groupValues[1]});"
        }
        Regex("""\.displayClientMessage\((.*), true\);""").replace(transformed) {
            ".sendOverlayMessage(${it.groupValues[1]});"
        }
    }
}

base {
    archivesName = "pathmind-neoforge"
}

sourceSets.main {
    java.setSrcDirs(emptyList<Any>())
    java.srcDir(prepareMc26Sources)
    resources.setSrcDirs(listOf(
        repositoryRoot.resolve("mc26/neoforge/src/main/resources"),
        repositoryRoot.resolve("mc26/shared/src/main/resources"),
        repositoryRoot.resolve("common/src/main/resources")
    ))
}

neoForge {
    version = neoForgeVersion
    runs {
        create("client") { client() }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }
    mods {
        create("pathmind") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.processResources {
    val replacements = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to neoForgeVersion
    )
    inputs.properties(replacements)
    filesMatching("META-INF/neoforge.mods.toml") { expand(replacements) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    exclude("architectury.common.json")
    exclude("pathmind-fabric.mixins.json")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(repositoryRoot.resolve("LICENSE.txt")) {
        rename { "${it}_pathmind-neoforge" }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = targetJavaVersion
    options.encoding = "UTF-8"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}
