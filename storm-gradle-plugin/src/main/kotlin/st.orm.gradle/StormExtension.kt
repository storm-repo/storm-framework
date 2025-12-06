// storm-gradle-plugin/src/main/kotlin/st/orm/gradle/StormExtension.kt
package st.orm.gradle

import org.gradle.api.provider.Property

/**
 * Configuration extension for the Storm Gradle plugin.
 *
 * Usage in build.gradle.kts:
 * ```
 * storm {
 *     enabled.set(true)
 *     verbose.set(false)
 *     failOnError.set(true)
 * }
 * ```
 */
abstract class StormExtension {

    /**
     * Enable or disable Storm record processing.
     * Default: true
     */
    abstract val enabled: Property<Boolean>

    /**
     * Enable verbose logging showing all transformed classes.
     * Default: false
     */
    abstract val verbose: Property<Boolean>

    /**
     * Enable debug logging showing all transformed classes.
     * Default: false
     */
    abstract val debug: Property<Boolean>

    /**
     * Fail the build if record processing encounters an error.
     * Default: true
     */
    abstract val failOnError: Property<Boolean>

    /**
     * Fail the build if no classes were transformed.
     * Useful to catch configuration issues.
     * Default: false
     */
    abstract val failIfNoTransformations: Property<Boolean>

    init {
        enabled.convention(true)
        verbose.convention(false)
        debug.convention(false)
        failOnError.convention(true)
        failIfNoTransformations.convention(false)
    }
}