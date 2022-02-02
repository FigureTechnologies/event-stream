dependencies {
    api(project(":api"))
    api(project(":api-model"))

    // https://mvnrepository.com/artifact/org.apache.kafka/kafka-clients
    api("org.apache.kafka:kafka-clients:3.0.0")
    implementation("com.squareup.moshi", "moshi", Versions.Moshi)
    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi}")
}
