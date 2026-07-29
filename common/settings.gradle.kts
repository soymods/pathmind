pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "pathmind-stonecutter"

stonecutter {
    centralScript.set("stonecutter-node.gradle.kts")
    kotlinController.set(true)
    create(rootProject) {
        vcsVersion.set("1.21.11")
        version("1.21.11")
        version("26.1")
    }
}
