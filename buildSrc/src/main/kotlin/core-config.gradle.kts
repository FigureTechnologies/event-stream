import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * "Convention plugin" for common plugins, dependencies, etc.
 *
 * Ongoing use of the `subprojects` DSL construct is discouraged.
 *
 * See https://docs.gradle.org/current/samples/sample_building_kotlin_applications_multi_project.html
 */
plugins {
    // Gradle plugins.
    kotlin("jvm")
    id("java-library")

    // Internal plugins.
    id("with-docs")
    id("with-linter")
}

group = rootProject.group
version = rootProject.version

val nexusUser = findProperty("nexusUser")?.toString() ?: System.getenv("NEXUS_USER")
val nexusPass = findProperty("nexusPass")?.toString() ?: System.getenv("NEXUS_PASS")

repositories {
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components.
    // See https://medium.com/@gabrielshanahan/a-deep-dive-into-an-initial-kotlin-build-gradle-kts-8950b81b214
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Versions.Kotlinx.DateTime)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.Kotlinx.Core)

    implementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)

    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Versions.Kotlinx.Core)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", Versions.JUnit.Core)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Versions.JUnit.Core)
    testImplementation("org.junit-pioneer", "junit-pioneer", Versions.JUnit.Pioneer)

    implementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)

    implementation("com.sksamuel.hoplite", "hoplite-core", Versions.Hoplite)
    implementation("com.sksamuel.hoplite", "hoplite-yaml", Versions.Hoplite)
}

// Testing:
tasks.test {
    useJUnitPlatform()
}
