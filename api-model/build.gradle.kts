plugins {
    kotlin("kapt")
    id("org.openapi.generator") version Plugins.OpenAPI
}

val TENDERMINT_OPENAPI_YAML = "$rootDir/api-model/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Versions.Kotlinx.DateTime)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.Kotlinx.Core)
    implementation("io.arrow-kt", "arrow-core", Versions.Arrow)
    implementation("org.apache.commons", "commons-lang3", Versions.ApacheCommons.Lang3)
    implementation("com.tinder.scarlet", "scarlet", Versions.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Versions.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Versions.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Versions.Scarlet)
    implementation("io.grpc", "grpc-alts", Versions.GRPC)
    implementation("io.grpc", "grpc-netty", Versions.GRPC)
    implementation("io.grpc", "grpc-protobuf", Versions.GRPC)
    implementation("io.grpc", "grpc-stub", Versions.GRPC)
    runtimeOnly("ch.qos.logback", "logback-classic", Versions.LogBack)
    implementation("io.github.microutils", "kotlin-logging-jvm", Versions.KotlinLogging)
    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Versions.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi}")
    implementation("com.sksamuel.hoplite", "hoplite-core", Versions.Hoplite)
    implementation("com.sksamuel.hoplite", "hoplite-yaml", Versions.Hoplite)
    implementation("org.json", "json", Versions.JSON)
}

sourceSets {
    main {
        java {
            srcDirs(
                "$projectDir/api-model/src/main/kotlin",
                "$buildDir/generated/src/main/kotlin"
            )
        }
    }
    test {
        java {
            srcDir("$projectDir/api-model/src/test/kotlin")
        }
    }
}

kapt {
    correctErrorTypes = true
}

project.afterEvaluate {
    // Force generation of the API and models based on the
    tasks.get("kaptGenerateStubsKotlin").dependsOn("generateTendermintAPI")
}

/**
 * See the following links for information about generating models from an OpenAPI spec:
 * - https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-gradle-plugin
 * - https://github.com/OpenAPITools/openapi-generator/blob/master/docs/global-properties.md
 * - https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/kotlin.md
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateTendermintAPI") {
    generatorName.set("kotlin")
    verbose.set(false)
    validateSpec.set(true)
    inputSpec.set(TENDERMINT_OPENAPI_YAML)
    outputDir.set("$buildDir/generated")
    packageName.set("io.provenance.eventstream.stream")
    modelPackage.set("io.provenance.eventstream.stream.models")
    library.set("jvm-okhttp4")
    configOptions.set(
        mapOf(
            "artifactId" to "tendermint-api",
            "dateLibrary" to "java8",
            "moshiCodeGen" to "true",
            "modelMutable" to "false",
            "serializableModel" to "true",
            "useCoroutines" to "true"
        )
    )
}
