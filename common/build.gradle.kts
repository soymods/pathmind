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
dependencies {
    minecraft("com.mojang:minecraft:$requestedMinecraftVersion")
    mappings(
        if (requestedMinecraftVersion == "1.21.11") {
            loom.officialMojangMappings()
        } else {
            rootProject.files("gradle/mappings/$requestedMinecraftVersion-canonical-1.21.11.jar")
        }
    )
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
            when {
                commonSourceFamily == "legacy" -> srcDir("src/compat/legacy/base/java")
                commonSourceFamily == "mid" -> srcDir("src/compat/mid/java")
                commonSourceFamily == "modern" -> srcDir("src/compat/modern/java")
                else -> throw GradleException("Unknown common source family '$commonSourceFamily' for Minecraft $requestedMinecraftVersion")
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
