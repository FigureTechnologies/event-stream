import org.jetbrains.kotlin.kapt3.base.Kapt.kapt

plugins {
    kotlin("jvm")
    kotlin("kapt")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    kapt(libs.moshi.kotlin.codegen)
    implementation(projects.esApi)
    implementation(projects.esApiModel)
    implementation("io.provenance.client:pb-grpc-client-kotlin:1.1.1")

    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.scarlet)
    implementation(libs.bundles.apache.commons)

    implementation(libs.json)
    implementation(libs.moshi.kotlin.codegen)

    implementation(libs.bundles.grpc)
    implementation(libs.provenance.protos)

    testFixturesImplementation(projects.esApiModel)
    testFixturesImplementation(libs.bundles.grpc)
    testFixturesImplementation(libs.provenance.protos)
}

kapt {
    correctErrorTypes = true
}
