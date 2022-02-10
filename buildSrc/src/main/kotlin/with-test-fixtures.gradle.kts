plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

repositories {
    mavenCentral()
}

dependencies {
    testFixturesImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    testFixturesImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Versions.Kotlinx.Core)
    testFixturesImplementation("org.bouncycastle", "bcpkix-jdk15on", Versions.BouncyCastle)
    testFixturesImplementation("org.junit.jupiter", "junit-jupiter-api", Versions.JUnit.Core)
    testFixturesRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", Versions.JUnit.Core)
    testFixturesImplementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)
    testFixturesImplementation("org.junit-pioneer", "junit-pioneer", Versions.JUnit.Pioneer)
    testFixturesImplementation("org.apache.commons", "commons-text", Versions.ApacheCommons.Text)
    testFixturesImplementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    testFixturesImplementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
    testFixturesImplementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
    testFixturesImplementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
    testFixturesImplementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)
}

/**
 * Export the text javaDoc jar so it is accessible from the Maven publish task:
 */
val testFixturesJavadocJar by tasks.creating(Jar::class) {
    from(tasks.get("testFixturesJavadoc"))
    // classifier = "javadoc" (deprecated)
    archiveClassifier.convention("test-fixtures-javadoc")
    archiveClassifier.set("test-fixtures-javadoc")
}

project.extra["testFixturesJavadocJar"] = testFixturesJavadocJar
