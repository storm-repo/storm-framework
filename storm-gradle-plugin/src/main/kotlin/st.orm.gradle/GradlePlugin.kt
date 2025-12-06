package st.orm.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import st.orm.record.RecordProcessor
import java.io.File

/**
 * Gradle plugin that transforms Kotlin data classes implementing Storm interfaces
 * (st.orm.Data, st.orm.Entity, st.orm.Projection) into Java records by adding
 * @JvmRecord annotation and modifying bytecode after compilation.
 */
class GradlePlugin : Plugin<Project> {

    private val logger = Logging.getLogger(GradlePlugin::class.java)

    override fun apply(project: Project) {
        logger.info("Applying Storm Gradle Plugin")
        // Create configuration extension
        val extension = project.extensions.create(
            "storm", StormExtension::class.java
        )
        // Configure after project evaluation.
        project.afterEvaluate {
            configureStormProcessing(it, extension)
        }
    }

    private fun configureStormProcessing(project: Project, extension: StormExtension) {
        if (!extension.enabled.get()) {
            logger.info("Storm processing is disabled")
            return
        }
        // Transform classes after Kotlin compilation.
        project.tasks.matching {
            it.name.startsWith("compile") && it.name.endsWith("Kotlin")
        }.configureEach { compileTask ->
            compileTask.doLast {
                processClasses(compileTask, extension)
            }
        }
    }

    private fun processClasses(task: Task, extension: StormExtension) {
        val outputDir = getOutputDirectory(task)
        if (outputDir == null) {
            logger.warn("Storm: Could not determine output directory for ${task.name}")
            return
        }
        if (!outputDir.exists()) {
            logger.info("Storm: Output directory does not exist: $outputDir")
            return
        }
        logger.lifecycle("Storm: Processing ${outputDir.name}.")
        try {
            // Create processor with logger.
            val processor = RecordProcessor { message ->
                logger.lifecycle(message)
            }
            // Process all classes in output directory.
            val result = processor.processDirectory(outputDir, extension.verbose.get(), extension.debug.get())
            if (extension.verbose.get()) {
                logger.lifecycle("Storm: $result")
                if (result.transformedClasses.isNotEmpty()) {
                    logger.lifecycle("Storm: Transformed classes:")
                    result.transformedClasses.forEach { className ->
                        logger.lifecycle("  ✓ $className")
                    }
                }
            } else {
                if (result.transformed > 0) {
                    logger.lifecycle("Storm: Added @JvmRecord to ${result.transformed} classes")
                }
            }
            // Optionally fail if no transformations occurred.
            if (extension.failIfNoTransformations.get() &&
                result.transformed == 0 &&
                result.totalScanned > 0) {
                throw IllegalStateException(
                    "Storm: No classes were transformed. " +
                            "Set storm.failIfNoTransformations = false to disable this check."
                )
            }
        } catch (e: Exception) {
            if (extension.failOnError.get()) {
                throw IllegalStateException("Storm: Failed to process classes", e)
            } else {
                logger.error("Storm: Failed to process classes: ${e.message}", e)
            }
        }
    }

    /**
     * Get the output directory from a Kotlin compile task using reflection.
     * This avoids direct dependency on Kotlin Gradle Plugin types.
     */
    private fun getOutputDirectory(task: Task): File? {
        return try {
            val method = task.javaClass.getMethod("getDestinationDirectory")
            val property = method.invoke(task)
            if (property != null) {
                val getMethod = property.javaClass.getMethod("get")
                val value = getMethod.invoke(property)
                when (value) {
                    is File -> value
                    else -> {
                        // Try to get asFile property from Directory
                        try {
                            val asFileMethod = value.javaClass.getMethod("getAsFile")
                            asFileMethod.invoke(value) as? File
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            } else {
                null
            }
        } catch (_: Exception) {
            try {
                // Fallback to legacy API (Kotlin <1.7).
                val method = task.javaClass.getMethod("getDestinationDir")
                method.invoke(task) as? File
            } catch (e2: Exception) {
                logger.debug("Storm: Could not access output directory: ${e2.message}")
                null
            }
        }
    }
}