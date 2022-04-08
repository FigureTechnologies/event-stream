plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(projects.esApi)
    implementation(projects.esApiModel)
    implementation(libs.kafka.coroutines)
    implementation(libs.kafka)
    implementation(libs.moshi.core)

    implementation(libs.bundles.grpc)
    implementation(libs.provenance.protos)

    testImplementation(testFixtures(projects.esCore))
}
