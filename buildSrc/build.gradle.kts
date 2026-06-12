plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
}

dependencies {
    implementation("net.fabricmc:tiny-remapper:0.12.2") {
        isTransitive = false
    }
    implementation("net.fabricmc:mapping-io:0.8.0")
    implementation("org.ow2.asm:asm:9.9")
    implementation("org.ow2.asm:asm-commons:9.9")
    implementation("org.ow2.asm:asm-tree:9.9")
    implementation("org.ow2.asm:asm-util:9.9")
}
