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
    implementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)
}

application {
    applicationName = rootProject.name
    mainClass.set("io.provenance.eventstream.MainKt")
}
