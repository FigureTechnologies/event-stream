plugins {
    kotlin("jvm")

    application
    id("core-config")
}

dependencies {
    implementation(projects.esApi)
    implementation(projects.esApiModel)
    implementation(projects.esCore)
    implementation(projects.esKafka)

    implementation(libs.bundles.logback)
    implementation(libs.bundles.moshi)

    implementation(libs.kafka)
    implementation(libs.kotlinx.cli)
    implementation(libs.okhttp.client)
}

application {
    applicationName = "event-stream"
    mainClass.set("MainKt")
}
