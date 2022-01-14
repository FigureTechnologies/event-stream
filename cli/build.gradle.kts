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

    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Versions.Kotlinx.CLI)

    runtimeOnly("ch.qos.logback", "logback-classic", Versions.LogBack)

    implementation("com.squareup.okhttp3", "okhttp", Versions.OkHttp)

    implementation("com.squareup.moshi", "moshi", Versions.Moshi)
    implementation("com.squareup.moshi", "moshi-kotlin", Versions.Moshi)
    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)

    implementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)

    implementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)
}

application {
    applicationName = rootProject.name
    mainClass.set("io.provenance.eventstream.NuMainKt")
}
