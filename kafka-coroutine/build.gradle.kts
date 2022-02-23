plugins {
    kotlin("jvm")

    id("core-config")
    id("with-test-fixtures")
}

dependencies {
    implementation(libs.kafka)
}