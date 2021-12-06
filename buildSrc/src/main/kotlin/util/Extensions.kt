package util.extensions

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

fun Project.javadocJar(): Jar = util.projectJavadocJar(this)

fun Project.testFixturesJavadocJar(): Jar = util.testFixturesJavadocJar(this)
