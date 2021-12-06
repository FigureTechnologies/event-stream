plugins {
    `kotlin-dsl`
}

val kotlinVersion = "1.5.31"
val ktLintVersion = "10.2.0"
val dokkaVersion = kotlinVersion

repositories {
    gradlePluginPortal()
}

ext {
    set("kotlinVersion", kotlinVersion)
    set("ktLintVersion", ktLintVersion)
    set("dokkaVersion", dokkaVersion)
}

dependencies {
    implementation("org.jetbrains.kotlin", "kotlin-gradle-plugin", kotlinVersion)
    implementation("org.jlleitschuh.gradle", "ktlint-gradle", ktLintVersion)
    implementation("org.jetbrains.dokka", "dokka-gradle-plugin", dokkaVersion)
}
