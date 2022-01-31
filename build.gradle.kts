import util.extensions.javadocJar

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
}

allprojects {
    repositories {
        // Use Maven Central for resolving dependencies.
        mavenCentral()
    }
}

subprojects {
    apply {
        plugin("java")
        plugin("java-library")
        plugin("kotlin")
        plugin("idea")
        plugin("maven-publish")
        plugin("core-config")
        plugin("with-linter")
        plugin("with-test-fixtures")
        plugin("generate-docs")
    }

    group = rootProject.group
    version = rootProject.version

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependencies {
        implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
        implementation("org.jetbrains.kotlin", "kotlin-stdlib")
        implementation("org.jetbrains.kotlin", "kotlin-reflect")
        implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Versions.Kotlinx.DateTime)
        implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
        implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.Kotlinx.Core)
        implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.Kotlinx.Core)
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = rootProject.group.toString()
                artifactId = rootProject.name
                version = rootProject.version.toString()
                /* Skip outputting the test-fixtures jar:
                from(
                    util.filterTestFixturesFromComponents(
                        configurations,
                        components["java"] as AdhocComponentWithVariants
                    )
                )
                */
                from(components["java"])
                artifact(project.javadocJar())
            }
        }
    }
}