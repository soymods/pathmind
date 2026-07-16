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
            "update the target in gradle/minecraft-versions.properties."
    )
val neoForgeUiFamily = rootProject.extra["neoForgeUiFamily"] as String

base {
    archivesName.set("${rootProject.property("archives_base_name") as String}-neoforge")
}

loom {
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
    mappings(
        if (requestedMinecraftVersion == "1.21.11") {
            loom.officialMojangMappings()
        } else {
            rootProject.files("gradle/mappings/$requestedMinecraftVersion-canonical-1.21.11.jar")
        }
    )

    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")

    modApi("dev.architectury:architectury-neoforge:$architecturyApiVersion")

    common(project(":common", "namedElements")) { isTransitive = false }
    runtimeCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }
}

sourceSets {
    main {
        java {
            if (neoForgeUiFamily == "legacy") {
                srcDir("src/compat/legacy/base/java")
            } else if (neoForgeUiFamily == "modern") {
                srcDir("src/compat/modern/java")
            } else {
                throw GradleException("Unknown NeoForge UI family '$neoForgeUiFamily' for Minecraft $requestedMinecraftVersion")
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
    from(rootProject.file("LICENSE.txt")) { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.remapJar {
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
