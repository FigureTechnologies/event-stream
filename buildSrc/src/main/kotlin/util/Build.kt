package util

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.component.AdhocComponentWithVariants

/**
 * This removes `<library>-$VERSION-test-fixtures.jar` from being published.
 *
 * @see https://docs.gradle.org/current/userguide/publishing_customization.html
 */
fun filterTestFixturesFromComponents(
    configurations: ConfigurationContainer,
    component: AdhocComponentWithVariants
): AdhocComponentWithVariants {
    for ((configName: String, config: Configuration) in configurations.asMap) {
        if ("testFixtures" in configName) {
            try {
                component.withVariantsFromConfiguration(config) {
                    skip()
                }
            } catch (e: Exception) {
            }
        }
    }
    return component
}
