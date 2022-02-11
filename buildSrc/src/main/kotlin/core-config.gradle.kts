import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * "Convention plugin" for common plugins, dependencies, etc.
 *
 * Ongoing use of the `subprojects` DSL construct is discouraged.
 *
 * See https://docs.gradle.org/current/samples/sample_building_kotlin_applications_multi_project.html
 */
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")

    id("generate-docs")
    id("java-library")
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
}

// Compilation:
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = "11"
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

// Set the java version
configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}



// Testing:
tasks.test {
    useJUnitPlatform()
}
