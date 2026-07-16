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
val neoforgeVersion = rootProject.extra["neoforgeVersion"] as? String
    ?: throw GradleException(
        "NeoForge version not configured for Minecraft $requestedMinecraftVersion. " +
            "Check https://maven.neoforged.net/releases/net/neoforged/neoforge/ and " +
            "update the target in gradle/minecraft-versions.properties."
    )
val neoForgeUiFamily = rootProject.extra["neoForgeUiFamily"] as String
val packagingGeneration = rootProject.extra["packagingGeneration"] as String
val neoForgeReleaseTask = rootProject.extra["neoForgeReleaseTask"] as String
val canonicalMojangVersion = rootProject.extra["canonicalMojangVersion"] as String
val canonicalMappingsRevision = rootProject.extra["canonicalMappingsRevision"] as String

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

    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")

    common(project(":common", "namedElements")) { isTransitive = false }
    runtimeCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }
    shadowCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }
}

sourceSets {
    main {
        java {
            if (neoForgeUiFamily == "mc-1.21.0-1.21.10") {
                srcDir("src/compat/mc-1.21.0-1.21.10/java")
            } else if (neoForgeUiFamily == "mc-1.21.11") {
                srcDir("src/compat/mc-1.21.11/java")
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
        "neoforge_version" to neoforgeVersion
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(properties) }
}

tasks.shadowJar {
    exclude("architectury.common.json")
    configurations = listOf(project.configurations["shadowCommon"])
    if (neoForgeReleaseTask == "shadowJar") {
        archiveClassifier.set(null as String?)
    } else {
        archiveClassifier.set("dev-shadow")
    }
    from(rootProject.file("LICENSE.txt")) { rename { "${it}_${base.archivesName.get()}" } }
}

tasks.remapJar {
    enabled = neoForgeReleaseTask == "remapJar"
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
