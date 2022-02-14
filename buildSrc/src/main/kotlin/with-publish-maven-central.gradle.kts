import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.gradle.plugins.signing.SigningExtension

plugins {
    id("signing")
    id("maven-publish")
    id("io.github.gradle-nexus.publish-plugin")
}

repositories {
    mavenCentral()
}

val artifactName = if (name.startsWith("event-stream")) name else "event-stream-${name.replace("lib-", "")}"
val projectVersion = version.toString()

//nexusPublishing {
//    repositories {
//        sonatype {
//            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
//            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
//            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
//            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
//            stagingProfileId.set("3180ca260b82a7") // prevents querying for the staging profile id, performance optimization
//        }
//    }
//}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = artifactName
            version = projectVersion

            from(components["java"])

            pom {
                name.set("Provenance EventStream Implementation")
                description.set("A collection of libraries to connect and stream blocks from a node")
                url.set("https://provenance.io")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("mtps")
                        name.set("Phil Story")
                        email.set("phil@figure.com")
                    }

                    developer {
                        id.set("wbaker-figure")
                        name.set("Wyatt Baker")
                        email.set("wbaker@figure.com")
                    }

                    developer {
                        id.set("mwoods-figure")
                        name.set("Mike Woods")
                        email.set("mwoods@figure.com")
                    }

                    developer {
                        id.set("rchaing-figure")
                        name.set("Robert Chaing")
                        email.set("rchaing@figure.com")
                    }
                }

                scm {
                    developerConnection.set("git@github.com:provenance.io/event-stream.git")
                    connection.set("https://github.com/provenance-io/event-stream.git")
                    url.set("https://github.com/provenance-io/event-stream")
                }
            }
        }
    }

    configure<SigningExtension> {
        sign(publishing.publications["maven"])
    }
}