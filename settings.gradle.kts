rootProject.name = "event-stream"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// Adapted from https://stackoverflow.com/a/60456440
gradle.rootProject {
    /**
     * This library and all subprojects are set to the same shared version.
     * This is the only place the version should be updated:
     */
    val libraryVersion =
        rootProject.property("libraryVersion") ?: error("Missing libraryVersion - check gradle.properties")
    allprojects {
        group = "tech.figure.eventstream"
        version = libraryVersion
        description =
            "A library for receiving real-time and historical block, block event, and transaction event data from the Provenance block chain."
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// See to get around the restriction of not allowing variables in the plugin section of build.gradle.kts
// https://github.com/gradle/gradle/issues/1697#issuecomment-810913564
include("es-api")
include("es-api-model")
include("es-cli")
include("es-common")
include("es-core")
include("es-grpc")
include("es-kafka")

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.18"
}

gitHooks {
    preCommit {
        from {
            """
                echo "Running pre-commit ktlint check"
                ./gradlew ktlintCheck
            """.trimIndent()
        }
    }
    createHooks()
}
