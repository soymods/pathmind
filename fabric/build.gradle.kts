plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val requestedMinecraftVersion = rootProject.extra["requestedMinecraftVersion"] as String
val yarnMappings = rootProject.extra["yarnMappings"] as String
val fabricApiVersion = rootProject.extra["fabricApiVersion"] as String
val architecturyApiVersion = providers.gradleProperty("architectury_api_version")
    .orElse(provider { rootProject.extra["architecturyApiVersion"] as String })
    .get()

// Compat source set selection mirrors the MC-version-specific compat dirs.
val legacyInputVersions = setOf(
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"
)
val usesLegacyInputApis = requestedMinecraftVersion in legacyInputVersions
val typedUseItemCallbackVersions = setOf("1.21", "1.21.1")
val usesTypedUseItemCallback = requestedMinecraftVersion in typedUseItemCallbackVersions
val oldLegacyRenderVersions = setOf("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4")
val usesOldLegacyRenderApis = requestedMinecraftVersion in oldLegacyRenderVersions
val preAllocatorLegacyRenderVersions = setOf("1.21", "1.21.1")
val allocatorLightmapLegacyRenderVersions = setOf("1.21.2", "1.21.3")
val allocatorNoLightmapLegacyRenderVersions = setOf("1.21.4")
val transitionalLegacyRenderVersions = setOf("1.21.5")
val usesTransitionalLegacyRenderApis = requestedMinecraftVersion in transitionalLegacyRenderVersions
val midInputVersions = setOf("1.21.9", "1.21.10")
val usesMidInputApis = requestedMinecraftVersion in midInputVersions

base {
    archivesName.set("${rootProject.property("archives_base_name") as String}-fabric")
}

loom {
    runs {
        named("client") {
            vmArgs("-Xms1G", "-Xmx3G")
        }
    }
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    named("developmentFabric").get().extendsFrom(common)
}

dependencies {
    minecraft("com.mojang:minecraft:$requestedMinecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")

    modApi("dev.architectury:architectury-fabric:$architecturyApiVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    common(project(":common", "namedElements")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionFabric")) { isTransitive = false }

    val baritoneApiJar: File? = run {
        val candidates = listOfNotNull(
            System.getenv("BARITONE_API_JAR"),
            project.findProperty("baritoneApiPath") as? String,
            "libs/baritone-api-fabric-1.15.0.jar",
            "run/mods/baritone-api-fabric-1.15.0.jar"
        ).map { file(it) }
        candidates.firstOrNull { it.exists() }
    }

    val baritoneRuntimeTargets = setOf("1.21.6", "1.21.7", "1.21.8")
    val enableBaritoneRuntime = (project.findProperty("withBaritoneRuntime") as? String)
        ?.toBooleanStrictOrNull() ?: false

    baritoneApiJar?.let { jar ->
        val baritoneApi = files(jar)
        modCompileOnly(baritoneApi)
        if (enableBaritoneRuntime && requestedMinecraftVersion in baritoneRuntimeTargets) {
            modLocalRuntime(baritoneApi)
        }
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
            exclude("com/pathmind/screen/PathmindMarketplaceScreen.java")
            exclude("com/pathmind/screen/PathmindVisualEditorScreen.java")
            if (usesLegacyInputApis) {
                srcDir("src/compat/legacy/base/java")
                if (usesTypedUseItemCallback) {
                    srcDir("src/compat/legacy/useitem/typed/java")
                } else {
                    srcDir("src/compat/legacy/useitem/action/java")
                }
                if (usesOldLegacyRenderApis) {
                    when (requestedMinecraftVersion) {
                        in preAllocatorLegacyRenderVersions -> srcDir("src/compat/legacy/render/old/java")
                        in allocatorLightmapLegacyRenderVersions -> srcDir("src/compat/legacy/render/old-allocator/java")
                        in allocatorNoLightmapLegacyRenderVersions -> srcDir("src/compat/legacy/render/old-allocator-nolightmap/java")
                        else -> throw GradleException("No legacy old render source set configured for Minecraft $requestedMinecraftVersion")
                    }
                } else if (usesTransitionalLegacyRenderApis) {
                    srcDir("src/compat/legacy/render/transitional/java")
                } else {
                    srcDir("src/compat/legacy/render/late/java")
                }
            } else if (usesMidInputApis) {
                srcDir("src/compat/mid/java")
            } else {
                srcDir("src/compat/modern/java")
            }
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to version,
        "minecraft_version" to requestedMinecraftVersion,
        "loader_version" to rootProject.property("loader_version"),
        "fabric_api_version" to fabricApiVersion,
        "architectury_api_version" to architecturyApiVersion
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    configurations = listOf(project.configurations["shadowCommon"])
    archiveClassifier.set("dev-shadow")
    from(rootProject.file("LICENSE.txt")) { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set(null as String?)
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("dev")
    from(rootProject.file("LICENSE.txt")) { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.archiveFile.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
