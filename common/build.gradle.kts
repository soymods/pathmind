import com.pathmind.build.RemapJarToMojangTask
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
}

architectury {
    common("fabric", "neoforge")
}

loom {
    accessWidenerPath = file("src/main/resources/pathmind.accesswidener")
}

val requestedMinecraftVersion = rootProject.extra["requestedMinecraftVersion"] as String
val requestedYarnMappings = rootProject.extra["yarnMappings"] as String
val neoforgeVersion = rootProject.extra["neoforgeVersion"] as? String
val architecturyApiVersion = providers.gradleProperty("architectury_api_version")
    .orElse(provider { rootProject.extra["architecturyApiVersion"] as String })
    .get()

val legacyInputVersions = setOf(
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"
)
val midInputVersions = setOf("1.21.9", "1.21.10")
val usesLegacyInputApis = requestedMinecraftVersion in legacyInputVersions
val usesMidInputApis = requestedMinecraftVersion in midInputVersions

dependencies {
    minecraft("com.mojang:minecraft:$requestedMinecraftVersion")
    mappings("net.fabricmc:yarn:$requestedYarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")

    modApi("dev.architectury:architectury:$architecturyApiVersion")

    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
    enabled = false
    description = "Disabled for the common project; use :fabric:$name, :neoforge:$name, or the root runFabric*/runNeoForge* tasks."
}

sourceSets {
    main {
        java {
            when {
                usesLegacyInputApis -> srcDir("src/compat/legacy/base/java")
                usesMidInputApis -> srcDir("src/compat/mid/java")
                else -> srcDir("src/compat/modern/java")
            }
        }
    }
}

fun Project.resolveMixinMappingsFile(sourceSet: SourceSet): File {
    val invokerClass = Class.forName("net.fabricmc.loom.build.mixin.AnnotationProcessorInvoker")
    val method = invokerClass.getMethod(
        "getMixinMappingsForSourceSet",
        Project::class.java,
        SourceSet::class.java
    )
    return method.invoke(null, this, sourceSet) as File
}

tasks.named("transformProductionFabric") {
    doFirst {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val mainMixinMappings = project.resolveMixinMappingsFile(sourceSets.named("main").get())

        if (!mainMixinMappings.isFile) {
            mainMixinMappings.parentFile.mkdirs()
            mainMixinMappings.writeText("tiny\t2\t0\tnamed\tintermediary\n")
        }
    }
}

val remapProductionNeoForgeCommonToMojang = tasks.register<RemapJarToMojangTask>("remapProductionNeoForgeCommonToMojang") {
    group = "build"
    description = "Remaps the shared Yarn-named common jar to Mojang names for NeoForge production jars"

    dependsOn(tasks.named("transformProductionNeoForge"))

    inputJar.set(layout.buildDirectory.file("libs/${project.name}-$version-transformProductionNeoForge.jar"))
    outputJar.set(layout.buildDirectory.file("libs/${project.name}-$version-transformProductionNeoForgeMojang.jar"))
    minecraftVersion.set(requestedMinecraftVersion)
    yarnMappings.set(requestedYarnMappings)
    neoForgeVersion.set(neoforgeVersion ?: "")
    remapClasspath.from(configurations.named("compileClasspath"))

    onlyIf {
        !neoforgeVersion.isNullOrBlank()
    }
}

val transformProductionNeoForgeMojangElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(transformProductionNeoForgeMojangElements.name, remapProductionNeoForgeCommonToMojang.flatMap { it.outputJar }) {
        builtBy(remapProductionNeoForgeCommonToMojang)
    }
}
