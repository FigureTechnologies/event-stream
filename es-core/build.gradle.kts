plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(projects.esApi)
    implementation(projects.esApiModel)

    implementation(libs.bundles.hoplite)
//    implementation(libs.bundles.scarlet)
    implementation(libs.bundles.apache.commons)

    implementation(libs.json)
    implementation(libs.moshi.kotlin.codegen)

    implementation(libs.provenance.protos)
    implementation(libs.bundles.grpc)
    implementation("io.provenance.client:pb-grpc-client-kotlin:1.0.5")
    testFixturesImplementation(projects.esApiModel)
}
