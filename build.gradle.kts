import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent


plugins {
    id("with-publish-maven-central")
    id("jacoco")
}


jacoco {
    toolVersion = "0.8.8"
}

// Use the resolution strategy along with the version in the app.gradle. It will fix the issue.
configurations.all {
    resolutionStrategy {
        eachDependency {
            if ("org.jacoco" == requested.group) {
                useVersion("0.8.8")
            }
        }
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED, org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED, org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED, org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy("jacocoTestReport")
}

task<JacocoReport>("jacocoRootReport") {
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

tasks.check {
    dependsOn("jacocoRootReport")
}
