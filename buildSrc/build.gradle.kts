plugins {
    `kotlin-dsl`
}

val gradleNexusVersion = "1.3.0"
val kotlinVersion = "1.8.21"
val ktLintVersion = "11.3.2"
val dokkaVersion = "1.8.10"

repositories {
    gradlePluginPortal()
}

ext {
    set("kotlinVersion", kotlinVersion)
    set("ktLintVersion", ktLintVersion)
    set("dokkaVersion", dokkaVersion)
}

dependencies {
    implementation("io.github.gradle-nexus", "publish-plugin", gradleNexusVersion)
    implementation("org.jetbrains.kotlin", "kotlin-gradle-plugin", kotlinVersion)
    implementation("org.jlleitschuh.gradle", "ktlint-gradle", ktLintVersion)
    implementation("org.jetbrains.dokka", "dokka-gradle-plugin", dokkaVersion)
}
