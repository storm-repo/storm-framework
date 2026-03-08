@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package st.orm.kotlin.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * IR transformer that auto-wraps string interpolations in `t()` calls inside [TemplateBuilder] lambdas.
 *
 * The transformer detects lambda functions whose extension receiver type is `st.orm.template.TemplateContext`, then
 * finds `IrStringConcatenation` nodes (Kotlin string templates) inside those lambdas. For each non-constant
 * interpolated expression, it wraps the expression in a call to `TemplateContext.t(Any?): String`.
 *
 * Expressions already wrapped in `t()` or `insert()` are left unchanged, so explicit usage remains valid.
 *
 * Example transformation:
 *
 * Source:
 * ```kotlin
 * orm.query { "SELECT ${User::class} FROM ${User::class} WHERE id = $id" }
 * ```
 *
 * Rewritten IR (equivalent to):
 * ```kotlin
 * orm.query { "SELECT ${t(User::class)} FROM ${t(User::class)} WHERE id = ${t(id)}" }
 * ```
 */
class StormTemplateIrTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    companion object {
        private val TEMPLATE_CONTEXT_FQN = FqName("st.orm.template.TemplateContext")
        private val TEMPLATE_CONTEXT_CLASS_ID = ClassId(FqName("st.orm.template"), Name.identifier("TemplateContext"))
    }

    /**
     * Tracks the `TemplateContext` receiver parameter when we're inside a TemplateBuilder lambda, so we can generate
     * `receiver.t(expr)` calls. Null when outside such a lambda.
     */
    private var templateContextReceiver: IrValueParameter? = null

    /** Cached symbol for `TemplateContext.t(Any?): String`. */
    private var tFunctionSymbol: IrSimpleFunction? = null

    /** Cached symbol for `TemplateContext.autoInterpolation()`. */
    private var autoInterpolationSymbol: IrSimpleFunction? = null

    override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
        val function = expression.function
        // Check if this lambda has TemplateContext as its extension receiver type.
        val extensionReceiver = function.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
            ?: return super.visitFunctionExpression(expression)
        val receiverFqName = extensionReceiver.type.classOrNull?.owner?.fqNameWhenAvailable
            ?: return super.visitFunctionExpression(expression)
        if (receiverFqName != TEMPLATE_CONTEXT_FQN) {
            return super.visitFunctionExpression(expression)
        }
        // We're inside a TemplateBuilder lambda. Set the receiver so nested visits can use it.
        val previousReceiver = templateContextReceiver
        templateContextReceiver = extensionReceiver
        // Resolve function symbols if not already cached.
        if (tFunctionSymbol == null) {
            tFunctionSymbol = resolveTFunction()
        }
        if (autoInterpolationSymbol == null) {
            autoInterpolationSymbol = resolveAutoInterpolationFunction()
        }
        val result = super.visitFunctionExpression(expression)
        // Inject autoInterpolation() call at the start of the lambda body to signal that the plugin is active.
        val autoInterpolation = autoInterpolationSymbol
        if (autoInterpolation != null) {
            injectAutoInterpolationCall(function, extensionReceiver, autoInterpolation)
        }
        templateContextReceiver = previousReceiver
        return result
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val receiver = templateContextReceiver ?: return super.visitStringConcatenation(expression)
        val tFunction = tFunctionSymbol ?: return super.visitStringConcatenation(expression)
        // We're inside a TemplateBuilder lambda and found a string template. Wrap each non-constant argument in t().
        // First, recursively transform each argument so that nested TemplateBuilder lambdas (e.g., inside subquery
        // calls) are processed before we wrap the argument in t().
        val newArguments = expression.arguments.map { argument ->
            val transformed = argument.transform(this, null)
            if (transformed is IrConst) {
                transformed
            } else if (isAlreadyWrappedInT(transformed)) {
                transformed
            } else {
                wrapInT(transformed, receiver, tFunction)
            }
        }
        expression.arguments.clear()
        expression.arguments.addAll(newArguments)
        return expression
    }

    /**
     * Checks whether an expression is already a call to `TemplateContext.t()` or `TemplateContext.interpolate()`, so we
     * don't double-wrap.
     */
    private fun isAlreadyWrappedInT(expression: IrExpression): Boolean {
        if (expression !is IrCall) return false
        val callee = expression.symbol.owner
        val calleeName = callee.name.asString()
        if (calleeName != "t" && calleeName != "interpolate") return false
        val dispatchReceiver = expression.dispatchReceiver ?: return false
        val dispatchFqName = dispatchReceiver.type.classOrNull?.owner?.fqNameWhenAvailable ?: return false
        return dispatchFqName == TEMPLATE_CONTEXT_FQN
    }

    /** Wraps an expression in a call to `TemplateContext.t(expr)`, generating IR equivalent to `receiver.t(expr)`. */
    private fun wrapInT(
        expression: IrExpression,
        receiver: IrValueParameter,
        tFunction: IrSimpleFunction,
    ): IrExpression {
        val builder = DeclarationIrBuilder(pluginContext, tFunction.symbol, expression.startOffset, expression.endOffset)
        val valueArgIndex = tFunction.parameters.indexOfFirst { it.kind == IrParameterKind.Regular }
        return builder.irCall(tFunction).apply {
            dispatchReceiver = builder.irGet(receiver)
            arguments[valueArgIndex] = expression
        }
    }

    /**
     * Injects a call to `receiver.autoInterpolation()` at the start of the lambda body, before any existing
     * statements. This signals to the runtime that the compiler plugin has processed this lambda.
     */
    private fun injectAutoInterpolationCall(
        function: org.jetbrains.kotlin.ir.declarations.IrFunction,
        receiver: IrValueParameter,
        autoInterpolation: IrSimpleFunction,
    ) {
        val body = function.body as? IrBlockBody ?: return
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.startOffset)
        val call = builder.irCall(autoInterpolation).apply {
            dispatchReceiver = builder.irGet(receiver)
        }
        body.statements.add(0, call)
    }

    /** Resolves the `TemplateContext.t(Any?): String` function symbol. */
    private fun resolveTFunction(): IrSimpleFunction? {
        val templateContextClass = pluginContext.referenceClass(TEMPLATE_CONTEXT_CLASS_ID) ?: return null
        return templateContextClass.owner.functions
            .firstOrNull { it.name.asString() == "t" && it.parameters.count { p -> p.kind == IrParameterKind.Regular } == 1 }
    }

    /** Resolves the `TemplateContext.autoInterpolation()` function symbol. */
    private fun resolveAutoInterpolationFunction(): IrSimpleFunction? {
        val templateContextClass = pluginContext.referenceClass(TEMPLATE_CONTEXT_CLASS_ID) ?: return null
        return templateContextClass.owner.functions
            .firstOrNull { it.name.asString() == "autoInterpolation" && it.parameters.none { p -> p.kind == IrParameterKind.Regular } }
    }
}
