rootProject.name = "provenance-event-stream"

// Adapted from https://stackoverflow.com/a/60456440
gradle.rootProject {
    /**
     * This library and all subprojects are set to the same shared version.
     * This is the only place the version should be updated:
     */
    val libraryVersion =
        rootProject.property("libraryVersion") ?: error("Missing libraryVersion - check gradle.properties")
    allprojects {
        group = "io.provenance.eventstream"
        version = libraryVersion
        description =
            "A library for receiving real-time and historical block, block event, and transaction event data " +
                    "from the Provenance block chain."
    }
}

// See to get around the restriction of not allowing variables in the plugin section of build.gradle.kts
// https://github.com/gradle/gradle/issues/1697#issuecomment-810913564

include("api")
include("api-model")
include("cli")
include("lib")
include("lib-kafka")