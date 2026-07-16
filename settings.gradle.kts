pluginManagement {
    repositories {
        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev/")
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "NeoForge"
            url = uri("https://maven.neoforged.net/releases/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
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
}

rootProject.name = "pathmind"

val requestedMinecraftVersion = providers.gradleProperty("mc_version")
    .orElse(providers.gradleProperty("minecraft_version"))
    .get()
val compatibilityManifest = java.util.Properties().apply {
    file("gradle/minecraft-versions.properties").inputStream().use(::load)
}
val packagingGeneration = compatibilityManifest
    .getProperty("version.$requestedMinecraftVersion.packaging_generation")

// Minecraft 26+ uses unobfuscated Fabric Loom and ModDevGradle in the isolated
// mc26 build. Loading the pre-26 Architectury projects would configure an
// incompatible remapping toolchain before root delegation can occur.
if (packagingGeneration != "mc26-unobfuscated") {
    include("common", "fabric", "neoforge")
}
