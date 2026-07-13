import org.gradle.api.GradleException

plugins {
    id("architectury-plugin") version "3.4.161"
    id("dev.architectury.loom") version "1.14.473" apply false
    id("com.gradleup.shadow") version "9.5.1" apply false
}

// ------------------------------------------------------------
// Per-version configuration
// architecturyApiVersion: check at https://api.modrinth.com/v2/project/lhGA9TYQ/version?game_versions=["1.21.x"]
//   or run: ./gradlew checkArchitecturyVersions
// neoforgeVersion: check at https://maven.neoforged.net/releases/net/neoforged/neoforge/
// Set neoforgeVersion to null to disable NeoForge for that MC version.
// ------------------------------------------------------------
data class MinecraftVersionSpec(
    val yarnMappings: String,
    val fabricApiVersion: String,
    val architecturyApiVersion: String,
    val neoforgeVersion: String?
)

val supportedMinecraftVersions = linkedMapOf(
    "1.21" to MinecraftVersionSpec(
        yarnMappings = "1.21+build.9",
        fabricApiVersion = "0.102.0+1.21",
        architecturyApiVersion = "13.0.8",
        neoforgeVersion = "21.0.166"
    ),
    "1.21.1" to MinecraftVersionSpec(
        yarnMappings = "1.21.1+build.3",
        fabricApiVersion = "0.116.7+1.21.1",
        architecturyApiVersion = "13.0.8",
        neoforgeVersion = "21.1.230"
    ),
    "1.21.2" to MinecraftVersionSpec(
        yarnMappings = "1.21.2+build.1",
        fabricApiVersion = "0.106.1+1.21.2",
        architecturyApiVersion = "14.0.4",
        neoforgeVersion = "21.2.1-beta"
    ),
    "1.21.3" to MinecraftVersionSpec(
        yarnMappings = "1.21.3+build.2",
        fabricApiVersion = "0.114.1+1.21.3",
        architecturyApiVersion = "14.0.4",
        neoforgeVersion = "21.3.96"
    ),
    "1.21.4" to MinecraftVersionSpec(
        yarnMappings = "1.21.4+build.8",
        fabricApiVersion = "0.119.4+1.21.4",
        architecturyApiVersion = "15.0.3",
        neoforgeVersion = "21.4.157"
    ),
    "1.21.5" to MinecraftVersionSpec(
        yarnMappings = "1.21.5+build.1",
        fabricApiVersion = "0.128.2+1.21.5",
        architecturyApiVersion = "16.1.4",
        neoforgeVersion = "21.5.97"
    ),
    "1.21.6" to MinecraftVersionSpec(
        yarnMappings = "1.21.6+build.1",
        fabricApiVersion = "0.128.2+1.21.6",
        architecturyApiVersion = "17.0.6",
        neoforgeVersion = "21.6.20-beta"
    ),
    "1.21.7" to MinecraftVersionSpec(
        yarnMappings = "1.21.7+build.8",
        fabricApiVersion = "0.129.0+1.21.7",
        architecturyApiVersion = "17.0.8",
        neoforgeVersion = "21.7.25-beta"
    ),
    "1.21.8" to MinecraftVersionSpec(
        yarnMappings = "1.21.8+build.1",
        fabricApiVersion = "0.133.4+1.21.8",
        architecturyApiVersion = "17.0.8",
        neoforgeVersion = "21.8.53"
    ),
    "1.21.9" to MinecraftVersionSpec(
        yarnMappings = "1.21.9+build.1",
        fabricApiVersion = "0.134.1+1.21.9",
        architecturyApiVersion = "18.0.5",
        neoforgeVersion = "21.9.16-beta"
    ),
    "1.21.10" to MinecraftVersionSpec(
        yarnMappings = "1.21.10+build.3",
        fabricApiVersion = "0.138.4+1.21.10",
        architecturyApiVersion = "18.0.8",
        neoforgeVersion = "21.10.64"
    ),
    "1.21.11" to MinecraftVersionSpec(
        yarnMappings = "1.21.11+build.3",
        fabricApiVersion = "0.140.2+1.21.11",
        architecturyApiVersion = "19.0.1",
        neoforgeVersion = "21.11.42"
    )
)

fun String.toTaskSuffix(): String = replace(".", "_")

val requestedMinecraftVersion = providers.gradleProperty("mc_version")
    .orElse(providers.gradleProperty("minecraft_version"))
    .get()

val requestedSpec = supportedMinecraftVersions[requestedMinecraftVersion]
    ?: throw GradleException(
        "No version spec configured for Minecraft $requestedMinecraftVersion. " +
            "Either add it to supportedMinecraftVersions or pass explicit properties."
    )

extra["requestedMinecraftVersion"] = requestedMinecraftVersion
extra["yarnMappings"] = requestedSpec.yarnMappings
extra["fabricApiVersion"] = requestedSpec.fabricApiVersion
extra["architecturyApiVersion"] = requestedSpec.architecturyApiVersion
extra["neoforgeVersion"] = requestedSpec.neoforgeVersion

val defaultRunTaskAliases = mapOf(
    "runclient" to ":fabric:runClient",
    "runserver" to ":fabric:runServer",
    "runclientrenderdoc" to ":fabric:runClientRenderDoc"
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

apply(plugin = "base")

tasks.register("runFabricClient") {
    group = "loom"
    description = "Run the Fabric development client for Minecraft $requestedMinecraftVersion"
    dependsOn(":fabric:runClient")
}

tasks.register("runNeoForgeClient") {
    group = "loom"
    description = "Run the NeoForge development client for Minecraft $requestedMinecraftVersion"
    dependsOn(":neoforge:runClient")
    onlyIf {
        requestedSpec.neoforgeVersion != null
    }
}

tasks.register("runFabricServer") {
    group = "loom"
    description = "Run the Fabric development server for Minecraft $requestedMinecraftVersion"
    dependsOn(":fabric:runServer")
}

tasks.register("runNeoForgeServer") {
    group = "loom"
    description = "Run the NeoForge development server for Minecraft $requestedMinecraftVersion"
    dependsOn(":neoforge:runServer")
    onlyIf {
        requestedSpec.neoforgeVersion != null
    }
}

subprojects {
    apply(plugin = "java")

    version = "${rootProject.property("mod_version") as String}+mc$requestedMinecraftVersion"
    group = rootProject.property("maven_group") as String

    repositories {
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

    val targetJavaVersion = 21
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        if (JavaVersion.current() < JavaVersion.toVersion(targetJavaVersion)) {
            toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
        }
    }
}

// ------------------------------------------------------------
// Check latest Architectury API versions from Modrinth
// Usage: ./gradlew checkArchitecturyVersions
// ------------------------------------------------------------
tasks.register("checkArchitecturyVersions") {
    group = "help"
    description = "Queries Modrinth for the latest Architectury API version for each configured MC version"
    doLast {
        println("\nArchitectury API version check (source: Modrinth)")
        println("-".repeat(70))
        supportedMinecraftVersions.forEach { (mcVersion, spec) ->
            try {
                val encoded = java.net.URLEncoder.encode("[\"$mcVersion\"]", "UTF-8")
                val url = java.net.URI("https://api.modrinth.com/v2/project/lhGA9TYQ/version?game_versions=$encoded&featured=true").toURL()
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "pathmind-buildscript/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().readText()
                    val latest = Regex(""""version_number"\s*:\s*"([^+]+)""").find(json)
                        ?.groupValues?.get(1) ?: "unknown"
                    val current = spec.architecturyApiVersion
                    val status = if (current == latest) "up to date" else "update to $latest"
                    println("MC $mcVersion: $current  $status")
                } else {
                    println("MC $mcVersion: HTTP ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                println("MC $mcVersion: error - ${e.message}")
            }
        }
        println("-".repeat(70))
        println("Update architecturyApiVersion in the supportedMinecraftVersions map above.\n")
    }
}

// ------------------------------------------------------------
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

        val neoforgeSupported = supportedMinecraftVersions[version]?.neoforgeVersion != null
        val targets = if (neoforgeSupported) {
            ":fabric:remapJar :neoforge:remapJar"
        } else {
            ":fabric:remapJar"
        }

        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            commandLine("cmd", "/c", "gradlew.bat", *targets.split(" ").toTypedArray(), "-Pmc_version=$version")
        } else {
            commandLine("./gradlew", *targets.split(" ").toTypedArray(), "-Pmc_version=$version")
        }

        doFirst {
            project.delete(versionOutputDir)
        }
        doLast {
            project.copy {
                from(project(":fabric").layout.buildDirectory.dir("libs")) {
                    include("*mc$version.jar")
                    include("*mc$version-sources.jar")
                }
                if (neoforgeSupported) {
                    from(project(":neoforge").layout.buildDirectory.dir("libs")) {
                        include("*mc$version.jar")
                        include("*mc$version-sources.jar")
                    }
                }
                into(versionOutputDir)
            }
        }
    }
    provider.configure {
        mustRunAfter(cleanTask)
        previousTask?.let { mustRunAfter(it) }
    }
    multiVersionBuildTasks.add(provider)
    previousMultiVersionBuildTask = provider
}

tasks.register("buildAllTargets") {
    group = "build"
    description = "Build Pathmind for every configured Minecraft target (fabric + neoforge)"
    dependsOn(cleanTask)
    multiVersionBuildTasks.forEach { dependsOn(it) }
}
