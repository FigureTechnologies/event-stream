plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(projects.esApi)
    implementation(projects.esApiModel)

    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.scarlet)
    implementation(libs.bundles.apache.commons)

    implementation(libs.json)
    implementation(libs.moshi.kotlin.codegen)

    testFixturesImplementation(projects.esApiModel)
}
