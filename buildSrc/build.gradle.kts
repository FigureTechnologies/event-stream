plugins {
    `kotlin-dsl`
}

val kotlinVersion = "1.5.31"
val ktLintVersion = "10.2.0"
val dokkaVersion = "1.5.31"

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin", "kotlin-gradle-plugin", kotlinVersion)
    implementation("org.jlleitschuh.gradle", "ktlint-gradle", ktLintVersion)
    implementation("org.jetbrains.kotlin", "kotlin-gradle-plugin", kotlinVersion)
    implementation("org.jetbrains.dokka", "dokka-gradle-plugin", dokkaVersion)
}
