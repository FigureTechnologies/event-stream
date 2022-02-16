dependencies {
    api(project(":api"))
    api(project(":api-model"))
    api(project(":kafka-coroutine"))

    // https://mvnrepository.com/artifact/org.apache.kafka/kafka-clients
    api("org.apache.kafka:kafka-clients:3.0.0")
    implementation("com.squareup.moshi", "moshi", Versions.Moshi)
    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi}")

    testApi(project(":lib"))
    testApi(testFixtures(project(":lib")))
    testImplementation("org.apache.kafka", "kafka-streams-test-utils", "2.8.1")
    testImplementation("junit", "junit", "4.13.2")
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", "1.4.2")
}
