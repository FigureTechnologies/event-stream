plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(projects.api)
    implementation(projects.apiModel)
    implementation(libs.kafka)
    implementation(libs.moshi.core)

    testImplementation(testFixtures(projects.lib))
}
