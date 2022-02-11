plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(projects.api)
    implementation(projects.apiModel)

    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.scarlet)

    implementation(libs.arrow)
    implementation(libs.json)
    implementation(libs.moshi.kotlin.codegen)

    testFixturesImplementation(projects.apiModel)
}
