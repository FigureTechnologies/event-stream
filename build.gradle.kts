import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent


plugins {
    id("with-publish-maven-central")
    id("jacoco-report-aggregation")
    jacoco
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
    dependsOn(tasks.test)
}

tasks.test {
    finalizedBy("jacocoTestReport")
}

dependencies {
    implementation(projects.esCore)
    implementation(projects.esKafka)
}
