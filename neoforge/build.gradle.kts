plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val requestedMinecraftVersion = rootProject.extra["requestedMinecraftVersion"] as String
val architecturyApiVersion = providers.gradleProperty("architectury_api_version")
    .orElse(provider { rootProject.extra["architecturyApiVersion"] as String })
    .get()
val neoforgeVersion = rootProject.extra["neoforgeVersion"] as? String
    ?: throw GradleException(
        "NeoForge version not configured for Minecraft $requestedMinecraftVersion. " +
            "Check https://maven.neoforged.net/releases/net/neoforged/neoforge/ and " +
            "update neoforgeVersion in the root build.gradle.kts supportedMinecraftVersions map."
    )
val renderWidgetButtonVersions = setOf(
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"
)
val usesRenderWidgetButton = requestedMinecraftVersion in renderWidgetButtonVersions

base {
    archivesName.set("${rootProject.property("archives_base_name") as String}-neoforge")
}

loom {
    accessWidenerPath = project(":common").file("src/main/resources/pathmind.accesswidener")
    runs {
        named("client") {
            vmArgs("-Xms1G", "-Xmx3G")
        }
    }
}

val common: Configuration by configurations.creating
val runtimeCommon: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(runtimeCommon)
    named("developmentNeoForge").get().extendsFrom(runtimeCommon)
}

dependencies {
    minecraft("com.mojang:minecraft:$requestedMinecraftVersion")
    mappings(loom.officialMojangMappings())

    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")

    modApi("dev.architectury:architectury-neoforge:$architecturyApiVersion")

    common(project(":common", "namedElements")) { isTransitive = false }
    runtimeCommon(project(":common", "transformProductionNeoForgeMojangElements")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionNeoForgeMojangElements")) { isTransitive = false }
}

sourceSets {
    main {
        java {
            if (usesRenderWidgetButton) {
                srcDir("src/compat/legacy/base/java")
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
        "neoforge_version" to neoforgeVersion,
        "architectury_api_version" to architecturyApiVersion
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(properties) }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    configurations = listOf(project.configurations["shadowCommon"])
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set(null as String?)
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("dev")
    from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.archiveFile.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
