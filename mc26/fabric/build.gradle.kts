plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.14"
}

val repositoryRoot = rootProject.extra["repositoryRoot"] as File
val minecraftVersion = rootProject.extra["minecraftVersion"] as String
val fabricLoaderVersion = rootProject.extra["fabricLoaderVersion"] as String
val fabricLoaderMinimumVersion = rootProject.extra["fabricLoaderMinimumVersion"] as String
val fabricApiVersion = rootProject.extra["fabricApiVersion"] as String
val targetJavaVersion = rootProject.extra["targetJavaVersion"] as Int
val commonSourceFamily = rootProject.extra["commonSourceFamily"] as String
val fabricBaseFamily = rootProject.extra["fabricBaseFamily"] as String
@Suppress("UNCHECKED_CAST")
val mc26SourceTransforms = rootProject.extra["mc26SourceTransforms"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val mc26SharedSourceTransforms = rootProject.extra["mc26SharedSourceTransforms"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val mc26VersionSourceTransforms = rootProject.extra["mc26VersionSourceTransforms"] as Map<String, String>
val mc26SourceTransformRevision = rootProject.extra["mc26SourceTransformRevision"] as Int

// Minecraft 26.x ships unobfuscated names directly. Keep the authored shared
// sources on the 1.21.x baseline for now and adapt mechanical API deltas at the
// mc26 build boundary. Version-specific transforms are declared once in the
// mc26 root build. The generated tree lives under build/ and is never an
// authored or published source of truth.
val prepareMc26Sources by tasks.registering(Sync::class) {
    inputs.property(
        "pathmindMc26SymbolTransforms",
        mc26SourceTransforms.entries.joinToString(";") { "${it.key}->${it.value}" }
    )
    inputs.property("pathmindMc26SourceTransformRevision", mc26SourceTransformRevision)
    from(repositoryRoot.resolve("common/src/main/java"))
    from(repositoryRoot.resolve("common/src/compat/$commonSourceFamily/java"))
    from(repositoryRoot.resolve("fabric/src/compat/$fabricBaseFamily/java"))
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
    archivesName = "pathmind-fabric"
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

sourceSets.main {
    java.setSrcDirs(emptyList<Any>())
    java.srcDir(prepareMc26Sources)
    resources.setSrcDirs(listOf(
        repositoryRoot.resolve("mc26/shared/src/main/resources"),
        repositoryRoot.resolve("common/src/main/resources"),
        repositoryRoot.resolve("fabric/src/main/resources")
    ))
}

loom {
    runs {
        named("client") {
            vmArgs("-Xms1G", "-Xmx3G")
        }
    }
}

tasks.processResources {
    val replacements = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "loader_version" to fabricLoaderMinimumVersion,
        "java_version" to targetJavaVersion,
        "fabric_api_version" to fabricApiVersion
    )
    inputs.properties(replacements)
    filesMatching("fabric.mod.json") { expand(replacements) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    exclude("architectury.common.json")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(repositoryRoot.resolve("LICENSE.txt")) {
        rename { "${it}_pathmind-fabric" }
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
