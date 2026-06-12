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

rootProject.name = "pathmind"
include("common", "fabric", "neoforge")
