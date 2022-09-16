plugins {
    kotlin("jvm")

    application
    id("core-config")
}

repositories {
    mavenCentral()
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn", "-Xopt-in=kotlin.time.ExperimentalTime")
        jvmTarget = "11"
    }
}
