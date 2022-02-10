plugins {
    kotlin("jvm")
    id("core-config")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":api"))
    api(project(":api-model"))
    api("org.apache.kafka:kafka-clients:3.0.0")

    testApi(project(":lib"))
    testApi(testFixtures(project(":lib")))
}
