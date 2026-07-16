import com.pathmind.build.GenerateCanonicalMojangMappingsTask
import org.gradle.api.GradleException
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("architectury-plugin") version "3.4.161"
    id("dev.architectury.loom") version "1.14.473" apply false
    id("com.gradleup.shadow") version "9.5.1" apply false
}

// ------------------------------------------------------------
// Per-version configuration is loaded from gradle/minecraft-versions.properties.
// NeoForge releases: https://maven.neoforged.net/releases/net/neoforged/neoforge/
// ------------------------------------------------------------
data class MinecraftVersionSpec(
    val compatibilityFamily: String,
    val javaVersion: Int,
    val packagingGeneration: String,
    val releaseLoaders: Set<String>,
    val fabricLoaderVersion: String,
    val fabricLoaderMinimumVersion: String,
    val fabricApiVersion: String,
    val neoforgeVersion: String?,
    val commonSourceFamily: String,
    val fabricBaseFamily: String,
    val fabricUseItemFamily: String,
    val fabricRenderFamily: String,
    val neoForgeUiFamily: String,
    val baritoneRuntime: Boolean
)

data class BuildGenerationSpec(
    val javaVersion: Int,
    val mappingsMode: String,
    val releaseTasks: Map<String, String>
) {
    fun releaseTask(loader: String): String = releaseTasks[loader]
        ?: throw GradleException("Build generation does not define a release task for loader '$loader'")
}

val buildGenerations = mapOf(
    "pre26-remapped" to BuildGenerationSpec(
        javaVersion = 21,
        mappingsMode = "canonical-mojang-remapped",
        releaseTasks = mapOf("fabric" to "remapJar", "neoforge" to "remapJar")
    ),
    "mc26-unobfuscated" to BuildGenerationSpec(
        javaVersion = 25,
        mappingsMode = "unobfuscated-no-mappings",
        releaseTasks = mapOf("fabric" to "jar", "neoforge" to "jar")
    )
)

val canonicalMojangVersion = "1.21.11"
val canonicalMappingsRevision = "v6"

val compatibilityManifestFile = layout.projectDirectory.file("gradle/minecraft-versions.properties").asFile
val compatibilityManifest = Properties().apply {
    if (!compatibilityManifestFile.isFile) {
        throw GradleException("Missing Minecraft compatibility manifest: ${compatibilityManifestFile.path}")
    }
    compatibilityManifestFile.inputStream().use(::load)
}

fun manifestValue(key: String): String = compatibilityManifest.getProperty(key)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: throw GradleException("Missing required compatibility manifest property '$key'")

val defaultMinecraftVersion = manifestValue("default_version")
val supportedMinecraftVersionIds = manifestValue("supported_versions")
    .split(',')
    .map(String::trim)
val fastVerificationVersionIds = manifestValue("fast_verification_versions")
    .split(',')
    .map(String::trim)

if (supportedMinecraftVersionIds.size != supportedMinecraftVersionIds.toSet().size) {
    throw GradleException("Duplicate versions found in supported_versions")
}
if (fastVerificationVersionIds.size != fastVerificationVersionIds.toSet().size) {
    throw GradleException("Duplicate versions found in fast_verification_versions")
}
if (!supportedMinecraftVersionIds.containsAll(fastVerificationVersionIds)) {
    throw GradleException("fast_verification_versions contains unsupported targets")
}

val supportedMinecraftVersions = linkedMapOf<String, MinecraftVersionSpec>().apply {
    supportedMinecraftVersionIds.forEach { version ->
        val prefix = "version.$version."
        put(version, MinecraftVersionSpec(
            compatibilityFamily = manifestValue(prefix + "compatibility_family"),
            javaVersion = manifestValue(prefix + "java_version").toIntOrNull()
                ?: throw GradleException("Invalid Java version for Minecraft $version"),
            packagingGeneration = manifestValue(prefix + "packaging_generation"),
            releaseLoaders = manifestValue(prefix + "release_loaders")
                .split(',')
                .map(String::trim)
                .toSet(),
            fabricLoaderVersion = manifestValue(prefix + "fabric_loader"),
            fabricLoaderMinimumVersion = manifestValue(prefix + "fabric_loader_min"),
            fabricApiVersion = manifestValue(prefix + "fabric_api"),
            neoforgeVersion = manifestValue(prefix + "neoforge").takeUnless { it == "unsupported" },
            commonSourceFamily = manifestValue(prefix + "common_source_family"),
            fabricBaseFamily = manifestValue(prefix + "fabric_base_family"),
            fabricUseItemFamily = manifestValue(prefix + "fabric_use_item_family"),
            fabricRenderFamily = manifestValue(prefix + "fabric_render_family"),
            neoForgeUiFamily = manifestValue(prefix + "neoforge_ui_family"),
            baritoneRuntime = manifestValue(prefix + "baritone_runtime").toBooleanStrictOrNull()
                ?: throw GradleException("Invalid Baritone runtime flag for Minecraft $version")
        ))
    }
}

if (defaultMinecraftVersion !in supportedMinecraftVersions) {
    throw GradleException("default_version '$defaultMinecraftVersion' is not listed in supported_versions")
}

fun String.toTaskSuffix(): String = replace(".", "_")

val requestedMinecraftVersion = providers.gradleProperty("mc_version")
    .orElse(providers.gradleProperty("minecraft_version"))
    .orElse(defaultMinecraftVersion)
    .get()

val requestedSpec = supportedMinecraftVersions[requestedMinecraftVersion]
    ?: throw GradleException(
        "No version spec configured for Minecraft $requestedMinecraftVersion. " +
            "Add it to gradle/minecraft-versions.properties before selecting it."
    )

val requestedBuildGeneration = buildGenerations[requestedSpec.packagingGeneration]
    ?: throw GradleException(
        "Unknown build generation '${requestedSpec.packagingGeneration}' for Minecraft $requestedMinecraftVersion"
    )
val usesIsolatedMc26Build = requestedSpec.packagingGeneration == "mc26-unobfuscated"

extra["requestedMinecraftVersion"] = requestedMinecraftVersion
extra["fabricLoaderVersion"] = requestedSpec.fabricLoaderVersion
extra["fabricLoaderMinimumVersion"] = requestedSpec.fabricLoaderMinimumVersion
extra["fabricApiVersion"] = requestedSpec.fabricApiVersion
extra["neoforgeVersion"] = requestedSpec.neoforgeVersion
extra["compatibilityFamily"] = requestedSpec.compatibilityFamily
extra["packagingGeneration"] = requestedSpec.packagingGeneration
extra["mappingsMode"] = requestedBuildGeneration.mappingsMode
extra["fabricReleaseTask"] = requestedBuildGeneration.releaseTask("fabric")
extra["neoForgeReleaseTask"] = requestedBuildGeneration.releaseTask("neoforge")
extra["targetJavaVersion"] = requestedSpec.javaVersion
extra["commonSourceFamily"] = requestedSpec.commonSourceFamily
extra["fabricBaseFamily"] = requestedSpec.fabricBaseFamily
extra["fabricUseItemFamily"] = requestedSpec.fabricUseItemFamily
extra["fabricRenderFamily"] = requestedSpec.fabricRenderFamily
extra["neoForgeUiFamily"] = requestedSpec.neoForgeUiFamily
extra["baritoneRuntime"] = requestedSpec.baritoneRuntime
extra["canonicalMojangVersion"] = canonicalMojangVersion
extra["canonicalMappingsRevision"] = canonicalMappingsRevision

val defaultRunTaskAliases = mapOf(
    "runclient" to "runFabricClient",
    "runserver" to "runFabricServer",
    "runclientrenderdoc" to "runFabricClient"
)
val requestedTaskNames = gradle.startParameter.taskNames
val aliasedTaskNames = requestedTaskNames.map { requested ->
    defaultRunTaskAliases[requested.lowercase()] ?: requested
}
if (aliasedTaskNames != requestedTaskNames) {
    gradle.startParameter.setTaskNames(aliasedTaskNames)
}

architectury {
    minecraft = requestedMinecraftVersion
}

apply(plugin = "java")

fun org.gradle.api.tasks.Exec.configureMc26Invocation(vararg targetTasks: String) {
    workingDir = projectDir
    val arguments = arrayOf("-p", "mc26", *targetTasks, "-Pmc_version=$requestedMinecraftVersion")
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        commandLine("cmd", "/c", "gradlew.bat", *arguments)
    } else {
        commandLine("./gradlew", *arguments)
    }
}

fun registerRunTask(name: String, legacyTask: String, mc26Task: String, loader: String) {
    if (usesIsolatedMc26Build) {
        tasks.register<org.gradle.api.tasks.Exec>(name) {
            group = "run"
            description = "Run the $loader development target for Minecraft $requestedMinecraftVersion"
            configureMc26Invocation(mc26Task)
        }
    } else {
        tasks.register(name) {
            group = "loom"
            description = "Run the $loader development target for Minecraft $requestedMinecraftVersion"
            dependsOn(legacyTask)
        }
    }
}

registerRunTask("runFabricClient", ":fabric:runClient", ":fabric:runClient", "Fabric client")
registerRunTask("runNeoForgeClient", ":neoforge:runClient", ":neoforge:runClient", "NeoForge client")
registerRunTask("runFabricServer", ":fabric:runServer", ":fabric:runServer", "Fabric server")
registerRunTask("runNeoForgeServer", ":neoforge:runServer", ":neoforge:runServer", "NeoForge server")

if (usesIsolatedMc26Build) {
    tasks.register<org.gradle.api.tasks.Exec>("buildSelectedTarget") {
        group = "build"
        description = "Build Pathmind for the selected Minecraft target using its isolated unobfuscated toolchain"
        configureMc26Invocation(*requestedSpec.releaseLoaders.map { ":$it:jar" }.toTypedArray())
    }
} else {
    tasks.register("buildSelectedTarget") {
        group = "build"
        description = "Build Pathmind for the selected Minecraft target using its declared packaging generation"
        requestedSpec.releaseLoaders.forEach { loader ->
            dependsOn(":$loader:${requestedBuildGeneration.releaseTask(loader)}")
        }
    }
}

fun registerSelectedLoaderBuild(loader: String, displayName: String): TaskProvider<out Task> {
    if (usesIsolatedMc26Build) {
        return tasks.register<org.gradle.api.tasks.Exec>("buildSelected$displayName") {
            group = "build"
            description = "Build the selected Minecraft target for $displayName using its isolated unobfuscated toolchain"
            configureMc26Invocation(":$loader:jar")
        }
    }
    return tasks.register("buildSelected$displayName") {
        group = "build"
        description = "Build the selected Minecraft target for $displayName using its declared packaging generation"
        if (loader in requestedSpec.releaseLoaders) {
            dependsOn(":$loader:${requestedBuildGeneration.releaseTask(loader)}")
        }
        doFirst {
            if (loader !in requestedSpec.releaseLoaders) {
                throw GradleException("$displayName is not a release loader for Minecraft $requestedMinecraftVersion")
            }
        }
    }
}

registerSelectedLoaderBuild("fabric", "Fabric")
registerSelectedLoaderBuild("neoforge", "NeoForge")

subprojects {
    apply(plugin = "java")

    version = "${rootProject.property("mod_version") as String}+mc$requestedMinecraftVersion"
    group = rootProject.property("maven_group") as String

    repositories {
        flatDir {
            name = "PathmindCanonicalMappings"
            dirs(rootProject.layout.projectDirectory.dir("gradle/mappings"))
        }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        val mojang = maven {
            name = "Mojang"
            url = uri("https://libraries.minecraft.net/")
            metadataSources {
                mavenPom()
                artifact()
                ignoreGradleMetadataRedirection()
            }
            artifactUrls("https://repo.maven.apache.org/maven2/")
        }
        exclusiveContent {
            forRepositories(mojang)
            filter {
                includeModule("org.lwjgl", "lwjgl-freetype")
            }
        }
        mavenCentral()
    }

    val targetJavaVersion = requestedSpec.javaVersion
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        // Compile, test, and Loom development runs must use the target runtime.
        // Older NeoForge/Mixin stacks cannot safely inherit a newer host JVM.
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

val compatibilityManifestFields = setOf(
    "compatibility_family",
    "java_version",
    "packaging_generation",
    "release_loaders",
    "fabric_loader",
    "fabric_loader_min",
    "fabric_api",
    "neoforge",
    "common_source_family",
    "fabric_base_family",
    "fabric_use_item_family",
    "fabric_render_family",
    "neoforge_ui_family",
    "baritone_runtime"
)

val verifyCompatibilityManifest = tasks.register("verifyCompatibilityManifest") {
    group = "verification"
    description = "Checks the Minecraft compatibility manifest against source, docs, metadata, and CI"

    inputs.file(compatibilityManifestFile)
    inputs.files(
        layout.projectDirectory.file("gradle.properties"),
        layout.projectDirectory.file("common/src/main/java/com/pathmind/util/VersionSupport.java"),
        layout.projectDirectory.file("fabric/src/main/resources/fabric.mod.json"),
        layout.projectDirectory.file("neoforge/src/main/resources/META-INF/neoforge.mods.toml"),
        layout.projectDirectory.file("README.md"),
        layout.projectDirectory.file("docs/build-generations.md"),
        layout.projectDirectory.file("docs/compatibility-maintenance.md"),
        layout.projectDirectory.file("gradle/minecraft-version-templates/26.x.properties"),
        layout.projectDirectory.file(".github/workflows/build.yml")
    )
    inputs.files(
        supportedMinecraftVersions
            .filter { (version, spec) -> version != canonicalMojangVersion && spec.packagingGeneration == "pre26-remapped" }
            .keys
            .map { version -> layout.projectDirectory.file("gradle/mappings/$version-canonical-$canonicalMojangVersion-$canonicalMappingsRevision.jar") }
    )

    doLast {
        fun fail(message: String): Nothing = throw GradleException("Compatibility manifest drift: $message")

        val expectedKeys = mutableSetOf("default_version", "supported_versions", "fast_verification_versions")
        supportedMinecraftVersionIds.forEach { version ->
            compatibilityManifestFields.forEach { field -> expectedKeys.add("version.$version.$field") }
        }
        val actualKeys = compatibilityManifest.stringPropertyNames()
        val missingKeys = expectedKeys - actualKeys
        val unknownKeys = actualKeys - expectedKeys
        if (missingKeys.isNotEmpty()) fail("missing properties ${missingKeys.sorted()}")
        if (unknownKeys.isNotEmpty()) fail("unknown properties ${unknownKeys.sorted()}")

        val allowedPackagingGenerations = setOf("pre26-remapped", "mc26-unobfuscated")
        val allowedLoaders = setOf("fabric", "neoforge")
        val allowedCommonFamilies = setOf("mc-1.21.0-1.21.8", "mc-1.21.9-1.21.10", "mc-1.21.11", "mc-26.1-26.2")
        val allowedFabricBaseFamilies = allowedCommonFamilies
        val allowedUseItemFamilies = setOf("mc-1.21.0-1.21.1", "mc-1.21.2-1.21.8", "none")
        val allowedRenderFamilies = setOf(
            "mc-1.21.0-1.21.1",
            "mc-1.21.2-1.21.3",
            "mc-1.21.4",
            "mc-1.21.5",
            "mc-1.21.6-1.21.8",
            "none"
        )
        val allowedNeoForgeUiFamilies = setOf("mc-1.21.0-1.21.10", "mc-1.21.11", "mc-26.1-26.2")

        val numericallySortedVersions = supportedMinecraftVersionIds.sortedWith { left, right ->
            val leftParts = left.split('.').map(String::toInt)
            val rightParts = right.split('.').map(String::toInt)
            (0 until maxOf(leftParts.size, rightParts.size))
                .asSequence()
                .map { index -> (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 }) }
                .firstOrNull { it != 0 }
                ?: 0
        }
        if (supportedMinecraftVersionIds != numericallySortedVersions) {
            fail("supported_versions must be ordered oldest to newest")
        }
        if (fastVerificationVersionIds != supportedMinecraftVersionIds.filter(fastVerificationVersionIds::contains)) {
            fail("fast_verification_versions must follow supported_versions order")
        }
        val requiredFastFamilies = supportedMinecraftVersions.values.map { it.compatibilityFamily }.toSet()
        val representedFastFamilies = fastVerificationVersionIds
            .map { supportedMinecraftVersions.getValue(it).compatibilityFamily }
            .toSet()
        if (!representedFastFamilies.containsAll(requiredFastFamilies)) {
            fail("fast_verification_versions misses compatibility families ${requiredFastFamilies - representedFastFamilies}")
        }

        supportedMinecraftVersions.forEach { (version, spec) ->
            val generation = buildGenerations[spec.packagingGeneration]
                ?: fail("Minecraft $version selects unknown build generation '${spec.packagingGeneration}'")
            if (spec.javaVersion <= 0) fail("Minecraft $version has invalid Java ${spec.javaVersion}")
            if (spec.packagingGeneration !in allowedPackagingGenerations) fail("Minecraft $version has unknown packaging generation '${spec.packagingGeneration}'")
            if (spec.javaVersion != generation.javaVersion) {
                fail("Minecraft $version declares Java ${spec.javaVersion}, but ${spec.packagingGeneration} requires Java ${generation.javaVersion}")
            }
            if (spec.packagingGeneration == "pre26-remapped" && version != canonicalMojangVersion) {
                val canonicalMappings = layout.projectDirectory
                    .file("gradle/mappings/$version-canonical-$canonicalMojangVersion-$canonicalMappingsRevision.jar")
                    .asFile
                if (!canonicalMappings.isFile) {
                    fail("Minecraft $version is missing canonical mapping jar ${canonicalMappings.relativeTo(projectDir)}")
                }
            }
            if (spec.releaseLoaders.isEmpty() || !allowedLoaders.containsAll(spec.releaseLoaders)) fail("Minecraft $version has invalid release loaders ${spec.releaseLoaders}")
            if ("neoforge" in spec.releaseLoaders && spec.neoforgeVersion == null) fail("Minecraft $version releases NeoForge but has no NeoForge version")
            if (spec.commonSourceFamily !in allowedCommonFamilies) fail("Minecraft $version has unknown common source family '${spec.commonSourceFamily}'")
            if (spec.fabricBaseFamily !in allowedFabricBaseFamilies) fail("Minecraft $version has unknown Fabric base family '${spec.fabricBaseFamily}'")
            if (spec.fabricUseItemFamily !in allowedUseItemFamilies) fail("Minecraft $version has unknown Fabric use-item family '${spec.fabricUseItemFamily}'")
            if (spec.fabricRenderFamily !in allowedRenderFamilies) fail("Minecraft $version has unknown Fabric render family '${spec.fabricRenderFamily}'")
            if (spec.neoForgeUiFamily !in allowedNeoForgeUiFamilies) fail("Minecraft $version has unknown NeoForge UI family '${spec.neoForgeUiFamily}'")

            val commonDirectory = layout.projectDirectory
                .dir("common/src/compat/${spec.commonSourceFamily}/java")
                .asFile
            if (!commonDirectory.isDirectory) fail("Minecraft $version selects missing common source directory ${commonDirectory.relativeTo(projectDir)}")

            val fabricBaseDirectory = layout.projectDirectory
                .dir("fabric/src/compat/${spec.fabricBaseFamily}/java")
                .asFile
            if (!fabricBaseDirectory.isDirectory) fail("Minecraft $version selects missing Fabric source directory ${fabricBaseDirectory.relativeTo(projectDir)}")
            if (spec.fabricUseItemFamily != "none") {
                val useItemDirectory = layout.projectDirectory.dir("fabric/src/compat/api/use-item/${spec.fabricUseItemFamily}/java").asFile
                if (!useItemDirectory.isDirectory) fail("Minecraft $version selects missing Fabric use-item directory ${useItemDirectory.relativeTo(projectDir)}")
            }
            if (spec.fabricRenderFamily != "none") {
                val renderDirectory = layout.projectDirectory.dir("fabric/src/compat/api/world-render/${spec.fabricRenderFamily}/java").asFile
                if (!renderDirectory.isDirectory) fail("Minecraft $version selects missing Fabric render directory ${renderDirectory.relativeTo(projectDir)}")
            }

            val neoForgeDirectory = layout.projectDirectory
                .dir("neoforge/src/compat/${spec.neoForgeUiFamily}/java")
                .asFile
            if (!neoForgeDirectory.isDirectory) fail("Minecraft $version selects missing NeoForge UI directory ${neoForgeDirectory.relativeTo(projectDir)}")
        }

        val gradleProperties = Properties().apply {
            layout.projectDirectory.file("gradle.properties").asFile.inputStream().use(::load)
        }
        if (gradleProperties.getProperty("minecraft_version") != defaultMinecraftVersion) {
            fail("gradle.properties minecraft_version must equal default_version '$defaultMinecraftVersion'")
        }
        if (gradleProperties.containsKey("loader_version")) {
            fail("gradle.properties duplicates per-target fabric_loader from the manifest")
        }

        val versionSupportText = layout.projectDirectory
            .file("common/src/main/java/com/pathmind/util/VersionSupport.java").asFile.readText()
        val supportedBody = Regex(
            """SUPPORTED_VERSIONS\s*=\s*List\.of\((.*?)\);""",
            RegexOption.DOT_MATCHES_ALL
        ).find(versionSupportText)?.groupValues?.get(1)
            ?: fail("could not read VersionSupport.SUPPORTED_VERSIONS")
        val runtimeVersions = Regex(""""([0-9]+\.[0-9]+(?:\.[0-9]+)?)"""")
            .findAll(supportedBody)
            .map { it.groupValues[1] }
            .toList()
        if (runtimeVersions != supportedMinecraftVersionIds) {
            fail("VersionSupport versions $runtimeVersions do not match $supportedMinecraftVersionIds")
        }
        fun versionConstant(name: String): String = Regex("""$name\s*=\s*"([^"]+)"""")
            .find(versionSupportText)?.groupValues?.get(1)
            ?: fail("could not read VersionSupport.$name")
        if (versionConstant("MIN_VERSION") != supportedMinecraftVersionIds.first()) fail("VersionSupport.MIN_VERSION is stale")
        if (versionConstant("MAX_VERSION") != supportedMinecraftVersionIds.last()) fail("VersionSupport.MAX_VERSION is stale")

        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        val supportedTargetSection = readme.substringAfter("### Supported Build Targets", "")
            .substringBefore("## Version Information", "")
        if (supportedTargetSection.isBlank()) fail("README Supported Build Targets section is missing")
        val documentedVersions = Regex("""`([0-9]+\.[0-9]+(?:\.[0-9]+)?)`""")
            .findAll(supportedTargetSection)
            .map { it.groupValues[1] }
            .toList()
        if (documentedVersions != supportedMinecraftVersionIds) {
            fail("README supported targets $documentedVersions do not match $supportedMinecraftVersionIds")
        }
        if (!readme.contains("Minecraft-1.21.x%20%7C%2026.1--26.2")) fail("README Minecraft support badge is stale")
        if (!readme.contains("`1.21 - 1.21.11`, `26.1 - 26.2`")) fail("README version summary is stale")

        val fabricMetadata = layout.projectDirectory.file("fabric/src/main/resources/fabric.mod.json").asFile.readText()
        if (!fabricMetadata.contains("\${minecraft_version}")) fail("Fabric metadata must expand minecraft_version")
        if (!fabricMetadata.contains("\${java_version}")) fail("Fabric metadata must expand java_version")
        val neoForgeMetadata = layout.projectDirectory.file("neoforge/src/main/resources/META-INF/neoforge.mods.toml").asFile.readText()
        if (!neoForgeMetadata.contains("\${minecraft_version}")) fail("NeoForge metadata must expand minecraft_version")
        if (!neoForgeMetadata.contains("\${neoforge_version}")) fail("NeoForge metadata must expand neoforge_version")

        val workflow = layout.projectDirectory.file(".github/workflows/build.yml").asFile.readText()
        if (!workflow.contains("gradle/minecraft-versions.properties")) fail("CI target discovery does not read the compatibility manifest")
        if (workflow.contains("MinecraftVersionSpec\\(")) fail("CI still scrapes MinecraftVersionSpec from build.gradle.kts")
        if (!workflow.contains("matrix.java_version")) fail("CI does not select Java from the manifest-derived matrix")
        if (!workflow.contains("matrix.packaging_generation")) fail("CI caches are not separated by build generation")
        if (!workflow.contains("buildSelectedTarget")) fail("CI bypasses generation-aware release task selection")
        if (!workflow.contains("fast_verification_versions")) fail("CI fast tier is not discovered from the manifest")
        if (!workflow.contains("verifySelectedCompatibilityArtifacts")) fail("CI bypasses release artifact verification")

        val generationDocs = layout.projectDirectory.file("docs/build-generations.md").asFile.readText()
        buildGenerations.keys.forEach { generation ->
            if (!generationDocs.contains("`$generation`")) fail("build generation '$generation' is undocumented")
        }
        val maintenanceDocs = layout.projectDirectory.file("docs/compatibility-maintenance.md").asFile.readText()
        if (!maintenanceDocs.contains("## Adding a Minecraft target")) fail("new-version playbook is undocumented")
        if (!maintenanceDocs.contains("## Deprecation policy")) fail("compatibility deprecation policy is undocumented")
        if (!maintenanceDocs.contains("## Minecraft 26.2 graphics smoke test")) fail("26.2 graphics smoke procedure is undocumented")

        println("Compatibility manifest verified for ${supportedMinecraftVersionIds.size} Minecraft targets.")
    }
}

tasks.register("printSupportedMinecraftVersions") {
    group = "help"
    description = "Prints the supported Minecraft versions as a JSON array"
    doLast {
        println(supportedMinecraftVersionIds.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\""))
    }
}

tasks.register("printReleaseMatrix") {
    group = "help"
    description = "Prints the release matrix with Java and build-generation routing as JSON"
    doLast {
        val entries = supportedMinecraftVersions.map { (version, spec) ->
            "{\"minecraft_version\":\"$version\",\"java_version\":${spec.javaVersion}," +
                "\"packaging_generation\":\"${spec.packagingGeneration}\"}"
        }
        println(entries.joinToString(prefix = "{\"include\":[", postfix = "]}", separator = ","))
    }
}

tasks.register("compatibilityReport") {
    group = "help"
    description = "Prints the configured Minecraft compatibility matrix"
    doLast {
        println("Minecraft | Fast | Compatibility | Common/Fabric | NeoForge UI | Java | Packaging | Loaders | Fabric Loader (build/min) | Fabric API | NeoForge")
        println("-".repeat(188))
        supportedMinecraftVersions.forEach { (version, spec) ->
            println(listOf(
                version,
                if (version in fastVerificationVersionIds) "yes" else "no",
                spec.compatibilityFamily,
                spec.commonSourceFamily,
                spec.neoForgeUiFamily,
                spec.javaVersion,
                spec.packagingGeneration,
                spec.releaseLoaders.joinToString(","),
                "${spec.fabricLoaderVersion}/${spec.fabricLoaderMinimumVersion}",
                spec.fabricApiVersion,
                spec.neoforgeVersion ?: "unsupported"
            ).joinToString(" | "))
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCompatibilityManifest)
}

val verifyCompatibilityStructure = tasks.register("verifyCompatibilityStructure") {
    group = "verification"
    description = "Enforces explicit compatibility families and prevents loader-level product mirrors"
    doLast {
        val forbiddenFamilyDirectories = listOf(
            "common/src/compat/legacy",
            "common/src/compat/mid",
            "common/src/compat/modern",
            "fabric/src/compat/legacy",
            "fabric/src/compat/mid",
            "fabric/src/compat/modern",
            "neoforge/src/compat/legacy",
            "neoforge/src/compat/modern"
        ).map { layout.projectDirectory.dir(it).asFile }.filter(File::exists)
        if (forbiddenFamilyDirectories.isNotEmpty()) {
            throw GradleException(
                "Ambiguous compatibility directories are not allowed: " +
                    forbiddenFamilyDirectories.map { it.relativeTo(projectDir) }
            )
        }

        val canonicalMarketplaceFiles = setOf(
            "PathmindMarketplaceActions.java",
            "PathmindMarketplaceAsyncController.java",
            "PathmindMarketplaceAvatarLoader.java",
            "PathmindMarketplaceFlowController.java",
            "PathmindMarketplaceLayout.java"
        )
        val canonicalScreenDirectory = layout.projectDirectory
            .dir("common/src/main/java/com/pathmind/screen")
            .asFile
        val missingCanonicalFiles = canonicalMarketplaceFiles.filterNot {
            canonicalScreenDirectory.resolve(it).isFile
        }
        if (missingCanonicalFiles.isNotEmpty()) {
            throw GradleException("Missing canonical marketplace implementations: $missingCanonicalFiles")
        }

        val compatibilityCopies = layout.projectDirectory
            .dir("common/src/compat")
            .asFile
            .walkTopDown()
            .filter { it.isFile && it.name in canonicalMarketplaceFiles }
            .map { it.relativeTo(projectDir) }
            .toList()
        if (compatibilityCopies.isNotEmpty()) {
            throw GradleException("Family copies of canonical marketplace behavior are not allowed: $compatibilityCopies")
        }

        val forbiddenLoaderProductNames = setOf(
            "PathmindMarketplaceScreen.java",
            "PathmindVisualEditorScreen.java",
            "PathmindSettingsPopupController.java",
            "PathmindPresetPopupController.java",
            "PathmindMarketplacePopupController.java",
            "PathmindMarketplaceGraphPreviewRenderer.java",
            "PathmindMarketplacePreviewLoader.java"
        ) + canonicalMarketplaceFiles
        val loaderProductCopies = layout.projectDirectory
            .dir("fabric/src/compat")
            .asFile
            .walkTopDown()
            .filter { it.isFile && it.name in forbiddenLoaderProductNames }
            .map { it.relativeTo(projectDir) }
            .toList()
        if (loaderProductCopies.isNotEmpty()) {
            throw GradleException("Fabric compatibility code must not mirror product screens: $loaderProductCopies")
        }

        val backendNeutralRoots = listOf(
            "common/src/main/java",
            "common/src/compat/mc-26.1-26.2/java",
            "fabric/src/compat/mc-26.1-26.2/java",
            "neoforge/src/compat/mc-26.1-26.2/java"
        ).map { layout.projectDirectory.dir(it).asFile }
        val rawGraphicsPatterns = listOf(
            "org.lwjgl.opengl",
            "com.mojang.blaze3d.opengl",
            "GlStateManager",
            "GL11.",
            "GL20.",
            "GL30."
        )
        val backendLeaks = backendNeutralRoots
            .asSequence()
            .filter(File::isDirectory)
            .flatMap { it.walkTopDown().asSequence() }
            .filter { it.isFile && it.extension == "java" }
            .filter { file -> rawGraphicsPatterns.any(file.readText()::contains) }
            .map { it.relativeTo(projectDir) }
            .toList()
        if (backendLeaks.isNotEmpty()) {
            throw GradleException(
                "Shared and 26.x code must be render-backend neutral; raw OpenGL references found in $backendLeaks"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCompatibilityStructure)
}

val verifyBuildGenerationRouting = tasks.register("verifyBuildGenerationRouting") {
    group = "verification"
    description = "Checks remapped and unobfuscated generation contracts"
    doLast {
        val pre26 = buildGenerations.getValue("pre26-remapped")
        check(pre26.javaVersion == 21)
        check(pre26.mappingsMode == "canonical-mojang-remapped")
        check(pre26.releaseTask("fabric") == "remapJar")
        check(pre26.releaseTask("neoforge") == "remapJar")

        val mc26 = buildGenerations.getValue("mc26-unobfuscated")
        check(mc26.javaVersion == 25)
        check(mc26.mappingsMode == "unobfuscated-no-mappings")
        check(mc26.releaseTask("fabric") == "jar")
        check(mc26.releaseTask("neoforge") == "jar")
    }
}

tasks.named("check") {
    dependsOn(verifyBuildGenerationRouting)
}

tasks.register("configureMc26BuildGeneration") {
    group = "build setup"
    description = "Resolves the Java 25 and unobfuscated packaging contract used by 26.x targets"
    val generation = buildGenerations.getValue("mc26-unobfuscated")
    val generationTemplate = layout.projectDirectory.file("gradle/minecraft-version-templates/26.x.properties")
    inputs.file(generationTemplate)
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(generation.javaVersion))
    }
    doLast {
        val templateProperties = Properties().apply {
            generationTemplate.asFile.inputStream().use(::load)
        }
        check(templateProperties.getProperty("java_version") == generation.javaVersion.toString())
        check(templateProperties.getProperty("packaging_generation") == "mc26-unobfuscated")
        check(templateProperties.getProperty("mappings") == "none")
        val selectedLauncher = launcher.get()
        println("generation=mc26-unobfuscated")
        println("java=${selectedLauncher.metadata.languageVersion}")
        println("mappings=${generation.mappingsMode}")
        println("fabricTask=${generation.releaseTask("fabric")}")
        println("neoforgeTask=${generation.releaseTask("neoforge")}")
    }
}

fun cachedOfficialMappings(version: String, fileName: String): File {
    val versionCache = File(System.getProperty("user.home"), ".gradle/caches/fabric-loom/$version")
    val direct = versionCache.walkTopDown()
        .maxDepth(2)
        .firstOrNull { file ->
            file.name == fileName &&
                file.parentFile.name.startsWith("loom.mappings.") &&
                file.parentFile.name.contains(".layered+hash.40545-v2")
        }
    if (direct != null) return direct

    throw GradleException(
        "Official $fileName for Minecraft $version is not cached yet. Configure that target once before " +
            "regenerating mapping overlays."
    )
}

val canonicalMappingsFile = provider { cachedOfficialMappings(canonicalMojangVersion, "mappings-base.tiny") }
val generateCanonicalMojangMappings = tasks.register("generateCanonicalMojangMappings") {
    group = "build setup"
    description = "Regenerates the checked-in cross-version Mojang-name overlays"
}

supportedMinecraftVersions
    .filter { (version, spec) -> version != canonicalMojangVersion && spec.packagingGeneration == "pre26-remapped" }
    .keys
    .forEach { version ->
        val task = tasks.register<GenerateCanonicalMojangMappingsTask>("generateCanonicalMojangMappings${version.toTaskSuffix()}") {
            group = "build setup"
            description = "Generates canonical Mojang-name mappings for Minecraft $version"
            targetMappings.set(layout.file(provider { cachedOfficialMappings(version, "mappings-base.tiny") }))
            targetComposedMappings.set(layout.file(provider { cachedOfficialMappings(version, "mappings.tiny") }))
            canonicalMappings.set(layout.file(canonicalMappingsFile))
            authoredSources.from(
                fileTree("common/src") { include("**/*.java") },
                fileTree("fabric/src") { include("**/*.java") },
                fileTree("neoforge/src") { include("**/*.java") }
            )
            outputMappings.set(layout.projectDirectory.file(
                "gradle/mappings/$version-canonical-$canonicalMojangVersion-$canonicalMappingsRevision.jar"
            ))
        }
        generateCanonicalMojangMappings.configure { dependsOn(task) }
    }

// Multi-version build tasks
// ------------------------------------------------------------
val cleanTask = tasks.named("clean")
val multiVersionBuildTasks = mutableListOf<TaskProvider<out Task>>()
var previousMultiVersionBuildTask: TaskProvider<out Task>? = null

supportedMinecraftVersions.keys.forEach { version ->
    val taskName = "buildMc${version.toTaskSuffix()}"
    val previousTask = previousMultiVersionBuildTask
    val provider = tasks.register<org.gradle.api.tasks.Exec>(taskName) {
        group = "build"
        description = "Build Pathmind for Minecraft $version (fabric + neoforge)"
        val versionOutputDir = layout.buildDirectory.dir("multiVersion/$version")
        outputs.dir(versionOutputDir)
        workingDir = projectDir

        val spec = supportedMinecraftVersions.getValue(version)
        val fabricSupported = "fabric" in spec.releaseLoaders
        val neoforgeSupported = "neoforge" in spec.releaseLoaders
        val targets = if (spec.packagingGeneration == "mc26-unobfuscated") {
            listOf("buildSelectedTarget")
        } else {
            buildList {
                val generation = buildGenerations.getValue(spec.packagingGeneration)
                if (fabricSupported) add(":fabric:${generation.releaseTask("fabric")}")
                if (neoforgeSupported) add(":neoforge:${generation.releaseTask("neoforge")}")
            }
        }

        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            commandLine("cmd", "/c", "gradlew.bat", *targets.toTypedArray(), "-Pmc_version=$version")
        } else {
            commandLine("./gradlew", *targets.toTypedArray(), "-Pmc_version=$version")
        }

        doFirst {
            project.delete(versionOutputDir)
        }
        doLast {
            val generationRoot = if (spec.packagingGeneration == "mc26-unobfuscated") {
                layout.projectDirectory.dir("mc26").asFile
            } else {
                projectDir
            }
            project.copy {
                if (fabricSupported) {
                    from(generationRoot.resolve("fabric/build/libs")) {
                        include("*mc$version.jar")
                    }
                }
                if (neoforgeSupported) {
                    from(generationRoot.resolve("neoforge/build/libs")) {
                        include("*mc$version.jar")
                    }
                }
                into(versionOutputDir)
            }
        }
    }
    provider.configure {
        dependsOn(verifyCompatibilityManifest)
        mustRunAfter(cleanTask)
        previousTask?.let { mustRunAfter(it) }
    }
    multiVersionBuildTasks.add(provider)
    previousMultiVersionBuildTask = provider
}

val buildAllTargets = tasks.register("buildAllTargets") {
    group = "build"
    description = "Build Pathmind for every configured Minecraft target (fabric + neoforge)"
    dependsOn(cleanTask, verifyCompatibilityManifest)
    multiVersionBuildTasks.forEach { dependsOn(it) }
}

fun verifyCompatibilityJar(jar: File, version: String, spec: MinecraftVersionSpec, loader: String) {
    if (!jar.isFile) {
        throw GradleException("Missing compatibility artifact: ${jar.relativeTo(projectDir)}")
    }
    if (jar.name.contains("-dev") || jar.name.contains("-sources") || jar.name.contains("-shadow") || jar.name.contains("-all")) {
        throw GradleException("Development classifier is not a release artifact: ${jar.name}")
    }

    ZipFile(jar).use { zip ->
        val entries = zip.entries().asSequence().toList()
        val duplicateEntries = entries.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        if (duplicateEntries.isNotEmpty()) {
            throw GradleException("${jar.name} contains duplicate entries: ${duplicateEntries.sorted()}")
        }
        val entryNames = entries.map { it.name }.toSet()
        val metadataPath = if (loader == "fabric") "fabric.mod.json" else "META-INF/neoforge.mods.toml"
        val oppositeMetadataPath = if (loader == "fabric") "META-INF/neoforge.mods.toml" else "fabric.mod.json"
        val metadataEntry = zip.getEntry(metadataPath)
            ?: throw GradleException("${jar.name} is missing $metadataPath")
        if (oppositeMetadataPath in entryNames) {
            throw GradleException("${jar.name} accidentally contains $oppositeMetadataPath")
        }
        if ("architectury.common.json" in entryNames) {
            throw GradleException("${jar.name} contains the development-only Architectury common marker")
        }
        val nestedJars = entryNames.filter { it.endsWith(".jar") }
        if (nestedJars.isNotEmpty()) {
            throw GradleException("${jar.name} unexpectedly embeds dependency jars: ${nestedJars.sorted()}")
        }

        val requiredEntries = buildSet {
            add("com/pathmind/PathmindCommon.class")
            add("pathmind.mixins.json")
            if (loader == "fabric") {
                add("com/pathmind/PathmindMod.class")
                add("pathmind-fabric.mixins.json")
            } else {
                add("com/pathmind/neoforge/PathmindNeoForge.class")
            }
        }
        val missingEntries = requiredEntries - entryNames
        if (missingEntries.isNotEmpty()) {
            throw GradleException("${jar.name} is missing required release entries: ${missingEntries.sorted()}")
        }
        if (loader == "neoforge" && "pathmind-fabric.mixins.json" in entryNames) {
            throw GradleException("${jar.name} contains the Fabric-only Mixin configuration")
        }

        val metadata = zip.getInputStream(metadataEntry).bufferedReader().use { it.readText() }
        if (!metadata.contains(version)) throw GradleException("${jar.name} metadata does not mention Minecraft $version")
        if (loader == "fabric") {
            if (!metadata.contains("\"minecraft\": \"$version\"")) throw GradleException("${jar.name} does not require exact Minecraft $version")
            if (!metadata.contains("\"java\": \">=${spec.javaVersion}\"")) throw GradleException("${jar.name} has stale Java metadata")
            if (!metadata.contains("\"fabricloader\": \">=${spec.fabricLoaderMinimumVersion}\"")) throw GradleException("${jar.name} has stale Fabric Loader metadata")
            if (!metadata.contains("pathmind.mixins.json") || !metadata.contains("pathmind-fabric.mixins.json")) {
                throw GradleException("${jar.name} metadata has stale Mixin configuration")
            }
        } else {
            if (!metadata.contains("versionRange = \"[$version]\"")) throw GradleException("${jar.name} does not require exact Minecraft $version")
            if (!metadata.contains(spec.neoforgeVersion ?: "unsupported")) throw GradleException("${jar.name} has stale NeoForge metadata")
            if (!metadata.contains("pathmind.mixins.json")) throw GradleException("${jar.name} metadata has stale Mixin configuration")
        }
    }
}

val verifySelectedCompatibilityArtifacts = tasks.register("verifySelectedCompatibilityArtifacts") {
    group = "verification"
    description = "Checks the selected target's release jars and embedded metadata"
    mustRunAfter("buildSelectedTarget")
    doLast {
        val modVersion = rootProject.property("mod_version") as String
        val generationRoot = if (requestedSpec.packagingGeneration == "mc26-unobfuscated") {
            layout.projectDirectory.dir("mc26").asFile
        } else {
            projectDir
        }
        requestedSpec.releaseLoaders.forEach { loader ->
            val jar = generationRoot.resolve("$loader/build/libs/pathmind-$loader-$modVersion+mc$requestedMinecraftVersion.jar")
            verifyCompatibilityJar(jar, requestedMinecraftVersion, requestedSpec, loader)
        }
        println("Verified ${requestedSpec.releaseLoaders.size} release artifacts for Minecraft $requestedMinecraftVersion.")
    }
}

val verifyBuiltCompatibilityArtifacts = tasks.register("verifyBuiltCompatibilityArtifacts") {
    group = "verification"
    description = "Checks staged multi-version jars against the compatibility manifest and metadata contract"
    val multiVersionDirectory = layout.buildDirectory.dir("multiVersion")
    inputs.dir(multiVersionDirectory)

    doLast {
        val modVersion = rootProject.property("mod_version") as String
        val expectedFiles = supportedMinecraftVersions.flatMap { (version, spec) ->
            spec.releaseLoaders.map { loader ->
                layout.buildDirectory.file("multiVersion/$version/pathmind-$loader-$modVersion+mc$version.jar").get().asFile
            }
        }.toSet()
        val actualFiles = multiVersionDirectory.get().asFile
            .walkTopDown()
            .filter { it.isFile && it.extension == "jar" }
            .toSet()

        val missingFiles = expectedFiles - actualFiles
        val unexpectedFiles = actualFiles - expectedFiles
        if (missingFiles.isNotEmpty()) {
            throw GradleException("Missing compatibility artifacts: ${missingFiles.map { it.relativeTo(projectDir) }.sortedBy { it.path }}")
        }
        if (unexpectedFiles.isNotEmpty()) {
            throw GradleException("Unexpected compatibility artifacts: ${unexpectedFiles.map { it.relativeTo(projectDir) }.sortedBy { it.path }}")
        }

        supportedMinecraftVersions.forEach { (version, spec) ->
            spec.releaseLoaders.forEach { loader ->
                val jar = layout.buildDirectory
                    .file("multiVersion/$version/pathmind-$loader-$modVersion+mc$version.jar")
                    .get().asFile
                verifyCompatibilityJar(jar, version, spec, loader)
            }
        }
        println("Verified ${expectedFiles.size} staged compatibility artifacts.")
    }
}

verifyBuiltCompatibilityArtifacts.configure {
    mustRunAfter(multiVersionBuildTasks)
}

buildAllTargets.configure {
    dependsOn(verifyBuiltCompatibilityArtifacts)
}
