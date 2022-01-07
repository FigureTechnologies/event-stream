plugins {
    kotlin("jvm")
    id("core-config")
    id("with-linter")
    application
}

group = rootProject.group
version = rootProject.version

dependencies {
    api(project(":lib"))

    // Logging
    implementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)

    // Coroutines
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Versions.Kotlinx.CLI)

    // Logging
    runtimeOnly("ch.qos.logback", "logback-classic", Versions.LogBack)

    // Scarlet
    implementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
//    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
//    implementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
//    implementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)

    // OkHttp
    implementation("com.squareup.okhttp3", "okhttp", Versions.OkHttp)
}

application {
    applicationName = rootProject.name
    mainClass.set("io.provenance.eventstream.MainKt")
}
