plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))

    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Versions.Kotlinx.CLI)

    implementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)

    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi}")

    implementation("ch.qos.logback.contrib", "logback-json-core", Versions.Logback)
    implementation("ch.qos.logback.contrib", "logback-json-classic", Versions.Logback)

    implementation("com.sksamuel.hoplite", "hoplite-core", Versions.Hoplite)
    implementation("com.sksamuel.hoplite", "hoplite-yaml", Versions.Hoplite)
}

application {
    mainClass.set("io.provenance.eventstream.MainKt")
}