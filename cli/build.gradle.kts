plugins {
    kotlin("jvm")

    application
    id("core-config")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.api)
    implementation(projects.apiModel)
    implementation(projects.lib)
    implementation(projects.libKafka)

    implementation(libs.bundles.logback)
    implementation(libs.bundles.moshi)

    implementation(libs.kafka)
    implementation(libs.kotlinx.cli)
    implementation(libs.okhttp.client)
}

application {
    applicationName = "event-stream"
    mainClass.set("io.provenance.eventstream.NuMainKt")
}
