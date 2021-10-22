rootProject.name = "provenance-event-stream"

// See to get around the restriction of not allowing variables in the plugin section of build.gradle.kts
// https://github.com/gradle/gradle/issues/1697#issuecomment-810913564

pluginManagement {
    val kotlinVersion: String by settings  // defined in gradle.properties
    plugins {
        id("org.jetbrains.kotlin.jvm").version(kotlinVersion)
        id("org.jetbrains.kotlin.kapt").version(kotlinVersion)
    }
}

include("augment")
