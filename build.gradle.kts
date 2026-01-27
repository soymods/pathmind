import org.gradle.api.GradleException
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider

plugins {
    id("fabric-loom") version "1.14.10"
    id("maven-publish")
}

data class MinecraftVersionSpec(
    val yarnMappings: String,
    val fabricApiVersion: String
)

val supportedMinecraftVersions = linkedMapOf(
    "1.21" to MinecraftVersionSpec("1.21+build.9", "0.102.0+1.21"),
    "1.21.1" to MinecraftVersionSpec("1.21.1+build.3", "0.116.7+1.21.1"),
    "1.21.2" to MinecraftVersionSpec("1.21.2+build.1", "0.106.1+1.21.2"),
    "1.21.3" to MinecraftVersionSpec("1.21.3+build.2", "0.114.1+1.21.3"),
    "1.21.4" to MinecraftVersionSpec("1.21.4+build.8", "0.119.4+1.21.4"),
    "1.21.5" to MinecraftVersionSpec("1.21.5+build.1", "0.128.2+1.21.5"),
    "1.21.6" to MinecraftVersionSpec("1.21.6+build.1", "0.128.2+1.21.6"),
    "1.21.7" to MinecraftVersionSpec("1.21.7+build.8", "0.129.0+1.21.7"),
    "1.21.8" to MinecraftVersionSpec("1.21.8+build.1", "0.133.4+1.21.8"),
    "1.21.9" to MinecraftVersionSpec("1.21.9+build.1", "0.134.1+1.21.9"),
    "1.21.10" to MinecraftVersionSpec("1.21.10+build.3", "0.138.4+1.21.10"),
    "1.21.11" to MinecraftVersionSpec("1.21.11+build.3", "0.140.2+1.21.11")
)

fun String.toTaskSuffix(): String = replace(".", "_")

val requestedMinecraftVersion = providers.gradleProperty("mc_version")
    .orElse(providers.gradleProperty("minecraft_version"))
    .get()

val requestedSpec = supportedMinecraftVersions[requestedMinecraftVersion]

val yarnMappings = providers.gradleProperty("yarn_mappings").orElse(
    requestedSpec?.let { provider { it.yarnMappings } }
        ?: throw GradleException(
            "No Yarn mappings configured for Minecraft $requestedMinecraftVersion. " +
                "Either add it to supportedMinecraftVersions or pass -Pyarn_mappings=<mapping>"
        )
).get()

val fabricApiVersion = providers.gradleProperty("fabric_api_version").orElse(
    requestedSpec?.let { provider { it.fabricApiVersion } }
        ?: throw GradleException(
            "No Fabric API version configured for Minecraft $requestedMinecraftVersion. " +
                "Either add it to supportedMinecraftVersions or pass -Pfabric_api_version=<version>"
        )
).get()

version = "${project.property("mod_version") as String}+mc$requestedMinecraftVersion"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
val legacyInputVersions = setOf(
    "1.21",
    "1.21.1",
    "1.21.2",
    "1.21.3",
    "1.21.4",
    "1.21.5",
    "1.21.6",
    "1.21.7",
    "1.21.8"
)
val usesLegacyInputApis = requestedMinecraftVersion in legacyInputVersions
val midInputVersions = setOf("1.21.9", "1.21.10")
val usesMidInputApis = requestedMinecraftVersion in midInputVersions
loom {
    accessWidenerPath = file("src/main/resources/pathmind.accesswidener")
}

repositories {
}

val baritoneApiJar: File? by extra {
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

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$requestedMinecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Optional Baritone API (compile-time only; users may provide the mod at runtime)
    baritoneApiJar?.let { jar ->
        val baritoneApi = files(jar)
        modCompileOnly(baritoneApi)
        if (enableBaritoneRuntime && requestedMinecraftVersion in baritoneRuntimeTargets) {
            modLocalRuntime(baritoneApi)
        }
    } ?: logger.warn("Baritone API jar not found; skipping optional Baritone compile/runtime classpath")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.processResources {
    val properties = mapOf(
        "version" to version,
        "minecraft_version" to requestedMinecraftVersion,
        "loader_version" to project.property("loader_version"),
        "fabric_api_version" to fabricApiVersion
    )
    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

val targetJavaVersionInt = JavaVersion.toVersion(targetJavaVersion)
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (targetJavaVersionInt >= JavaVersion.VERSION_1_9) {
        options.release.set(targetJavaVersion)
    }
}

java {
    withSourcesJar()

    if (JavaVersion.current() < JavaVersion.toVersion(targetJavaVersion)) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
            if (usesLegacyInputApis) {
                srcDir("src/legacy/java")
            } else if (usesMidInputApis) {
                srcDir("src/mid/java")
            } else {
                srcDir("src/modern/java")
            }
        }
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

val cleanTask = tasks.named("clean")
val multiVersionBuildTasks = mutableListOf<TaskProvider<out Task>>()
supportedMinecraftVersions.keys.forEach { version ->
    val taskName = "buildMc${version.toTaskSuffix()}"
    val provider = tasks.register<org.gradle.api.tasks.Exec>(taskName) {
        group = "build"
        description = "Build Pathmind for Minecraft $version"
        val versionOutputDir = layout.buildDirectory.dir("multiVersion/$version")
        outputs.dir(versionOutputDir)
        workingDir = projectDir
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            commandLine("cmd", "/c", "gradlew.bat", "build", "-Pmc_version=$version")
        } else {
            commandLine("./gradlew", "build", "-Pmc_version=$version")
        }
        doFirst {
            project.delete(versionOutputDir)
        }
        doLast {
            project.copy {
                from(layout.buildDirectory.dir("libs")) {
                    include("*mc$version.jar")
                    include("*mc$version-sources.jar")
                }
                into(versionOutputDir)
            }
        }
    }
    provider.configure {
        mustRunAfter(cleanTask)
    }
    multiVersionBuildTasks.add(provider)
}

tasks.register("buildAllTargets") {
    group = "build"
    description = "Build Pathmind for every configured Minecraft target"
    val cleanTask = tasks.named("clean")
    dependsOn(cleanTask)
    multiVersionBuildTasks.forEach { dependsOn(it) }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        // Add repositories to publish to here.
    }
}
