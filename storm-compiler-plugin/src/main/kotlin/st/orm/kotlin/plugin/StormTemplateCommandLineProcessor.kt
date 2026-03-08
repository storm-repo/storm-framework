package st.orm.kotlin.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Command-line processor for the Storm Template compiler plugin.
 *
 * This processor enables the Kotlin compiler to discover and activate the plugin when it is added to the compiler
 * plugin classpath. No command-line options are required.
 */
@OptIn(ExperimentalCompilerApi::class)
class StormTemplateCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String get() = "st.orm.kotlin.storm-template-plugin"

    override val pluginOptions: Collection<AbstractCliOption> get() = emptyList()
}
