plugins {
    kotlin("jvm")
    id("java-library")
    idea
    id("maven-publish")
    id("core-config")
    id("with-linter")
    id("generate-docs")
}

group = rootProject.group
version = rootProject.version

repositories {
    maven {
        url = uri("http://packages.confluent.io/maven/")
        isAllowInsecureProtocol = true
    }
}

dependencies {
    api(project(":api"))
    api(project(":api-model"))
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Versions.Kotlinx.DateTime)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.Kotlinx.Core)

    implementation("org.apache.kafka", "kafka-clients", "3.0.0")
    implementation("org.apache.kafka", "kafka-streams", "3.0.0")

    runtimeOnly("ch.qos.logback", "logback-classic", Versions.LogBack)

    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi}")


    testImplementation("org.apache.kafka", "kafka-streams-test-utils", "2.8.1")
    testImplementation("junit", "junit", "4.13.2")
    testImplementation("io.confluent", "kafka-json-serializer", "7.0.1")
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", "1.4.2")
    testApi(project(":lib"))
    testApi(testFixtures(project(":lib")))
}
