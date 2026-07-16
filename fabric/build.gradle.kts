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
val fabricApiVersion = rootProject.extra["fabricApiVersion"] as String
val fabricLoaderVersion = rootProject.extra["fabricLoaderVersion"] as String
val fabricLoaderMinimumVersion = rootProject.extra["fabricLoaderMinimumVersion"] as String
val targetJavaVersion = rootProject.extra["targetJavaVersion"] as Int
val packagingGeneration = rootProject.extra["packagingGeneration"] as String
val fabricReleaseTask = rootProject.extra["fabricReleaseTask"] as String
val canonicalMojangVersion = rootProject.extra["canonicalMojangVersion"] as String
val canonicalMappingsRevision = rootProject.extra["canonicalMappingsRevision"] as String
val fabricBaseFamily = rootProject.extra["fabricBaseFamily"] as String
val fabricUseItemFamily = rootProject.extra["fabricUseItemFamily"] as String
val fabricRenderFamily = rootProject.extra["fabricRenderFamily"] as String
val baritoneRuntimeSupported = rootProject.extra["baritoneRuntime"] as Boolean

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
    when (packagingGeneration) {
        "pre26-remapped" -> mappings(
            if (requestedMinecraftVersion == canonicalMojangVersion) {
                loom.officialMojangMappings()
            } else {
                "com.pathmind.mappings:$requestedMinecraftVersion-canonical-$canonicalMojangVersion:" +
                    canonicalMappingsRevision
            }
        )
        "mc26-unobfuscated" -> Unit
        else -> throw GradleException("Unknown packaging generation '$packagingGeneration'")
    }
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

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

    val enableBaritoneRuntime = (project.findProperty("withBaritoneRuntime") as? String)
        ?.toBooleanStrictOrNull() ?: false

    baritoneApiJar?.let { jar ->
        val baritoneApi = files(jar)
        modCompileOnly(baritoneApi)
        if (enableBaritoneRuntime && baritoneRuntimeSupported) {
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
            if (fabricBaseFamily == "mc-1.21.0-1.21.8") {
                srcDir("src/compat/mc-1.21.0-1.21.8/java")
                when (fabricUseItemFamily) {
                    "mc-1.21.0-1.21.1" -> srcDir("src/compat/api/use-item/mc-1.21.0-1.21.1/java")
                    "mc-1.21.2-1.21.8" -> srcDir("src/compat/api/use-item/mc-1.21.2-1.21.8/java")
                    else -> throw GradleException("Unknown Fabric use-item family '$fabricUseItemFamily' for Minecraft $requestedMinecraftVersion")
                }
                when (fabricRenderFamily) {
                    "mc-1.21.0-1.21.1" -> srcDir("src/compat/api/world-render/mc-1.21.0-1.21.1/java")
                    "mc-1.21.2-1.21.3" -> srcDir("src/compat/api/world-render/mc-1.21.2-1.21.3/java")
                    "mc-1.21.4" -> srcDir("src/compat/api/world-render/mc-1.21.4/java")
                    "mc-1.21.5" -> srcDir("src/compat/api/world-render/mc-1.21.5/java")
                    "mc-1.21.6-1.21.8" -> srcDir("src/compat/api/world-render/mc-1.21.6-1.21.8/java")
                    else -> throw GradleException("Unknown Fabric render family '$fabricRenderFamily' for Minecraft $requestedMinecraftVersion")
                }
            } else if (fabricBaseFamily == "mc-1.21.9-1.21.10") {
                srcDir("src/compat/mc-1.21.9-1.21.10/java")
            } else if (fabricBaseFamily == "mc-1.21.11") {
                srcDir("src/compat/mc-1.21.11/java")
            } else {
                throw GradleException("Unknown Fabric base family '$fabricBaseFamily' for Minecraft $requestedMinecraftVersion")
            }
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to version,
        "minecraft_version" to requestedMinecraftVersion,
        "loader_version" to fabricLoaderMinimumVersion,
        "java_version" to targetJavaVersion,
        "fabric_api_version" to fabricApiVersion
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    configurations = listOf(project.configurations["shadowCommon"])
    if (fabricReleaseTask == "shadowJar") {
        archiveClassifier.set(null as String?)
    } else {
        archiveClassifier.set("dev-shadow")
    }
    from(rootProject.file("LICENSE.txt")) { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.remapJar {
    enabled = fabricReleaseTask == "remapJar"
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
