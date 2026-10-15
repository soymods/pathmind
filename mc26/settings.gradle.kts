pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "pathmind-mc26"

val requestedMinecraftVersion = providers.gradleProperty("mc_version").orElse("26.1").get()
val compatibilityManifest = java.util.Properties().apply {
    file("../gradle/minecraft-versions.properties").inputStream().use(::load)
}
val releaseLoaders = compatibilityManifest
    .getProperty("version.$requestedMinecraftVersion.release_loaders")
    ?.split(',')
    ?.map(String::trim)
    ?.toSet()
    ?: emptySet()

// Mirrors the root settings: targets without a NeoForge release (manifest
// neoforge=unsupported) must not configure ModDevGradle with that placeholder.
include("fabric")
if ("neoforge" in releaseLoaders) {
    include("neoforge")
}
