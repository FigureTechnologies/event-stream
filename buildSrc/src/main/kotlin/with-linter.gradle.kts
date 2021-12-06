import org.gradle.kotlin.dsl.configure

/**
 * A plugin for adding ktlinter support for code linting.
 */

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

// Linting:
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    verbose.set(true)
    disabledRules.set(setOf("filename", "import-ordering"))
    filter {
        // This should work, but doesn't:
        // exclude("$buildDir/generated/**")
        // A workaround for the fact that ignoring the generated dir above does nothing:
        // See https://github.com/JLLeitschuh/ktlint-gradle/issues/222#issuecomment-480755054
        exclude { it.file.absolutePath.contains("/generated/") }
    }
}
