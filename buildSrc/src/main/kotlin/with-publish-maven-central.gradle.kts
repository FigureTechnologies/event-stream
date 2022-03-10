import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


repositories {
    mavenCentral()
}

plugins {
    `maven-publish`
    `java-library`
    signing
    id("io.github.gradle-nexus.publish-plugin")
}

val esGroup = rootProject.group
val esVersion = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

val nexusUser = findProperty("nexusUser")?.toString() ?: System.getenv("NEXUS_USER")
val nexusPass = findProperty("nexusPass")?.toString() ?: System.getenv("NEXUS_PASS")


configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set("3180ca260b82a7") // prevents querying for the staging profile id, performance optimization
        }
    }
}

subprojects {
    apply {
        plugin("maven-publish")
        plugin("kotlin")
        plugin("java-library")
        plugin("signing")
        plugin("core-config")
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
            jvmTarget = "11"
        }
    }


    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    val artifactName = if (name.startsWith("es")) name else "es-$name"
    val projectVersion = esVersion.toString()

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
            sign(publications["maven"])
        }
    }
}

