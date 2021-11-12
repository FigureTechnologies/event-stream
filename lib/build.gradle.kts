plugins {
    kotlin("jvm")
    id("java-library")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.protobuf") version Plugins.Protobuf
    id("org.openapi.generator") version Plugins.OpenAPI
    idea
}

group = "io.provenance.eventstream"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val TENDERMINT_OPENAPI_YAML = "$rootDir/lib/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // All dependencies in the `org.jetbrains.kotlin` package will use the version of kotlin defined in
    // `gradle.properties`: used to pin the org.jetbrains.kotlin.{jvm,kapt} plugin versions in `settings.gradle.kts`.
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect")

    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Versions.Kotlinx.CLI)
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Versions.Kotlinx.DateTime)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.Kotlinx.Core)

    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Versions.Kotlinx.Core)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Versions.JUnit.Core)
    testImplementation("org.apache.commons", "commons-text", Versions.ApacheCommons.Text)
    testImplementation("org.junit-pioneer", "junit-pioneer", Versions.JUnit.Pioneer)

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

    implementation("io.provenance.protobuf", "pb-proto-java", Versions.Provenance)

    implementation("ch.qos.logback.contrib", "logback-json-core", Versions.Logback)
    implementation("ch.qos.logback.contrib", "logback-json-classic", Versions.Logback)

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
                "$projectDir/lib/src/main/kotlin",
                "$buildDir/generated/src/main/kotlin"
            )
        }
    }
    test {
        java {
            srcDir("$projectDir/lib/src/test/kotlin")
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

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
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
            "moshiCodeGen" to true.toString(),
            "modelMutable" to false.toString(),
            "serializableModel" to true.toString(),
            "serializationLibrary" to "moshi",
            "useCoroutines" to true.toString()
        )
    )
//    globalProperties.set(
//        mapOf(
//            "apis" to "false",
//            "models" to "",
//            "modelDocs" to ""
//        )
//    )
}

//tasks.withType<Jar> {
//    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//
//    manifest {
//        attributes["Main-Class"] = "io.provenance.eventstream.MainKt"
//    }
//
//    from(sourceSets.main.get().output)
//    dependsOn(configurations.runtimeClasspath)
//    from({
//        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
//    })
//
//
//    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
//}
