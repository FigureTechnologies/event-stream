import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent


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
                "io/provenance/eventstream/stream/models/*",
                "io/provenance/eventstream/stream/apis/*",
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

dependencies {
    implementation(projects.esCore)
    implementation(projects.esKafka)
}
