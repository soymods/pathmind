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
include("common", "fabric", "neoforge")
