plugins {
    kotlin("jvm")
    id("core-config")
    id("with-linter")
    id("generate-docs")
}

group = rootProject.group
version = rootProject.version

dependencies {
    api(project(":api-model"))

    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
}
