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

val requestedMinecraftVersion = rootProject.extra["requestedMinecraftVersion"] as String
val commonSourceFamily = rootProject.extra["commonSourceFamily"] as String
val packagingGeneration = rootProject.extra["packagingGeneration"] as String
val canonicalMojangVersion = rootProject.extra["canonicalMojangVersion"] as String
val canonicalMappingsRevision = rootProject.extra["canonicalMappingsRevision"] as String
val stonecutterCompatibilityNode = when (commonSourceFamily) {
    "mc-1.21.0-1.21.8" -> "1.21"
    "mc-1.21.9-1.21.10" -> "1.21.10"
    else -> null
}
val prepareStonecutterCompatibilitySources = stonecutterCompatibilityNode?.let { node ->
    val generatedStonecutterDirectory =
        layout.projectDirectory.dir("versions/$node/build/generated/stonecutter/stonecutter/java")
    val generateStonecutterSources = tasks.register<Exec>("generateStonecutterCompatibilitySources") {
        inputs.dir(layout.projectDirectory.dir("src/stonecutter/java"))
        inputs.files(
            layout.projectDirectory.file("settings.gradle.kts"),
            layout.projectDirectory.file("stonecutter.gradle.kts"),
            layout.projectDirectory.file("stonecutter-node.gradle.kts")
        )
        outputs.dir(generatedStonecutterDirectory)
        workingDir(rootProject.projectDir)
        val wrapperName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "gradlew.bat" else "gradlew"
        commandLine(
            rootProject.file(wrapperName).absolutePath,
            "-p",
            projectDir.absolutePath,
            ":$node:stonecutterGenerateStonecutter",
            "--no-daemon"
        )
    }
    tasks.register<Sync>("prepareStonecutterCompatibilitySources") {
        dependsOn(generateStonecutterSources)
        from(generatedStonecutterDirectory)
        include(
            "com/pathmind/screen/PathmindMarketplaceGraphPreviewRenderer.java",
            "com/pathmind/screen/PathmindMarketplacePopupController.java",
            "com/pathmind/screen/PathmindMarketplacePreviewLoader.java",
            "com/pathmind/screen/PathmindMarketplaceScreen.java",
            "com/pathmind/screen/PathmindPresetPopupController.java",
            "com/pathmind/screen/PathmindVisualEditorScreen.java",
            "com/pathmind/screen/PathmindSettingsPopupController.java"
        )
        into(layout.buildDirectory.dir("generated/sources/stonecutterCompatibility/main/java"))
    }
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
    modImplementation("net.fabricmc:fabric-loader:${rootProject.extra["fabricLoaderVersion"] as String}")

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
            srcDir("src/compat/api/pre26/java")
            when {
                commonSourceFamily == "mc-1.21.0-1.21.8" -> {
                    srcDir("src/compat/mc-1.21.0-1.21.8/java")
                    srcDir(requireNotNull(prepareStonecutterCompatibilitySources))
                }
                commonSourceFamily == "mc-1.21.9-1.21.10" -> {
                    srcDir("src/compat/mc-1.21.9-1.21.10/java")
                    srcDir(requireNotNull(prepareStonecutterCompatibilitySources))
                }
                commonSourceFamily == "mc-1.21.11" -> {
                    srcDir("src/stonecutter/java")
                    srcDir("src/compat/mc-1.21.11/java")
                }
                else -> throw GradleException("Unknown common source family '$commonSourceFamily' for Minecraft $requestedMinecraftVersion")
            }
            if (commonSourceFamily == "mc-1.21.0-1.21.8" || commonSourceFamily == "mc-1.21.9-1.21.10") {
                srcDir("src/compat/api/mc-1.21.0-1.21.10/java")
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
