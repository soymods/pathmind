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
    mappings(
        if (requestedMinecraftVersion == "1.21.11") {
            loom.officialMojangMappings()
        } else {
            rootProject.files("gradle/mappings/$requestedMinecraftVersion-canonical-1.21.11.jar")
        }
    )
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
            if (fabricBaseFamily == "legacy") {
                srcDir("src/compat/legacy/base/java")
                when (fabricUseItemFamily) {
                    "typed" -> srcDir("src/compat/legacy/useitem/typed/java")
                    "action" -> srcDir("src/compat/legacy/useitem/action/java")
                    else -> throw GradleException("Unknown Fabric use-item family '$fabricUseItemFamily' for Minecraft $requestedMinecraftVersion")
                }
                when (fabricRenderFamily) {
                    "old" -> srcDir("src/compat/legacy/render/old/java")
                    "old-allocator" -> srcDir("src/compat/legacy/render/old-allocator/java")
                    "old-allocator-nolightmap" -> srcDir("src/compat/legacy/render/old-allocator-nolightmap/java")
                    "transitional" -> srcDir("src/compat/legacy/render/transitional/java")
                    "late" -> srcDir("src/compat/legacy/render/late/java")
                    else -> throw GradleException("Unknown Fabric render family '$fabricRenderFamily' for Minecraft $requestedMinecraftVersion")
                }
            } else if (fabricBaseFamily == "mid") {
                srcDir("src/compat/mid/java")
            } else if (fabricBaseFamily == "modern") {
                srcDir("src/compat/modern/java")
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
