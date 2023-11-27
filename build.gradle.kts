plugins {
    id("with-publish-maven-central")
    jacoco
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    dependsOn(tasks.test)
}

task<JacocoReport>("jacocoAggregateReport") {
    dependsOn(subprojects.map { it.tasks.withType<Test>() })
    dependsOn(subprojects.map { it.tasks.withType<JacocoReport>() })
    additionalSourceDirs.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    sourceDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    classDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].output })
    executionData.setFrom(project.fileTree(".") {
        include("es-core/build/jacoco/test.exec", "es-kafka/build/jacoco/test.exec")
    })
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it).exclude(
                "tech/figure/eventstream/stream/models/*",
                "tech/figure/eventstream/stream/apis/*",
            )})
        )
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(File("${buildDir}/reports/jacoco"))
        csv.required.set(false)
    }
}

tasks.test {
    finalizedBy("jacocoTestReport")
}

val javaVersion = JavaVersion.VERSION_17
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn", "-opt-in=kotlin.time.ExperimentalTime")
            jvmTarget = "17"
        }
    }
    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }

    // Set the java version
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.esCore)
    implementation(projects.esKafka)
}
