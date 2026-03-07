package st.orm.kotlin.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * IR generation extension that rewrites string template interpolations inside [TemplateBuilder] lambdas. Delegates to
 * [StormTemplateIrTransformer] for the actual transformation.
 */
class StormTemplateIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(StormTemplateIrTransformer(pluginContext))
    }
}
