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

    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.scarlet)
    implementation(libs.bundles.apache.commons)

    implementation(libs.json)
    implementation(libs.moshi.kotlin.codegen)

    testFixturesImplementation("io.reactivex.rxjava2:rxjava:2.1.16")
    testFixturesImplementation("org.reactivestreams:reactive-streams:1.0.3")
    testFixturesImplementation("com.tinder.scarlet:scarlet-core-internal:0.1.12")
    testFixturesImplementation(libs.bundles.logback)

    testFixturesImplementation(projects.esApiModel)
}

kapt {
    correctErrorTypes = true
}
