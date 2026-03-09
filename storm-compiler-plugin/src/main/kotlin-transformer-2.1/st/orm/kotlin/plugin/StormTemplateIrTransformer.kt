@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package st.orm.kotlin.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
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

    /** Source text of the current file, cached for splitting merged constants. */
    private var currentSourceText: String? = null

    override fun visitFile(declaration: IrFile): IrFile {
        currentSourceText = try {
            java.io.File(declaration.fileEntry.name).readText()
        } catch (_: Exception) {
            null
        }
        val result = super.visitFile(declaration)
        currentSourceText = null
        return result
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
        val function = expression.function
        // Check if this lambda has TemplateContext as its extension receiver type.
        val extensionReceiver = function.extensionReceiverParameter ?: return super.visitFunctionExpression(expression)
        val receiverFqName = extensionReceiver.type.classFqName ?: return super.visitFunctionExpression(expression)
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
        val newArguments = expression.arguments.flatMap { argument ->
            val transformed = argument.transform(this, null)
            when {
                transformed is IrConst && isFragment(transformed) -> listOf(transformed)
                transformed is IrConst && hasMergedConstant(transformed) ->
                    splitMergedConstant(transformed, receiver, tFunction)
                isAlreadyWrappedInT(transformed) -> listOf(transformed)
                else -> listOf(wrapInT(transformed, receiver, tFunction))
            }
        }
        expression.arguments.clear()
        expression.arguments.addAll(newArguments)
        return expression
    }

    /**
     * Checks whether an [IrConst] is a string template fragment (literal SQL text) as opposed to an interpolated
     * constant expression like `${"value"}`.
     *
     * Fragment [IrConst] entries have a source offset span that matches the text length, because they represent literal
     * text from the template. Interpolated constant expressions have a larger offset span, since their source range
     * includes the surrounding syntax (e.g., quotes for string literals).
     */
    private fun isFragment(irConst: IrConst): Boolean {
        val value = irConst.value
        if (value !is String) return false
        return irConst.endOffset - irConst.startOffset == value.length
    }

    /**
     * Checks whether an [IrConst] contains a merged constant expression. This happens when the Kotlin compiler folds
     * an inline constant expression like `${"value"}` with adjacent literal template text into a single [IrConst].
     * The merged entry has a source offset span larger than the text length, because the source range includes the
     * `${"..."}` syntax in addition to the literal text.
     */
    private fun hasMergedConstant(irConst: IrConst): Boolean {
        val value = irConst.value
        if (value !is String) return false
        return irConst.endOffset - irConst.startOffset > value.length
    }

    /**
     * Splits a merged [IrConst] (containing both literal template text and folded inline constant expressions) into
     * separate fragment and wrapped expression entries.
     *
     * This method reads the source file to find `${"..."}` patterns within the [IrConst]'s source range, then splits
     * the merged text accordingly. Each inline constant expression is wrapped in a `t()` call, while literal text
     * fragments remain as plain [IrConst] entries.
     *
     * If the source file cannot be read or the parsing fails (e.g., due to escape sequences in the constant), the
     * original [IrConst] is returned unchanged to avoid incorrect transformations.
     */
    private fun splitMergedConstant(
        irConst: IrConst,
        receiver: IrValueParameter,
        tFunction: IrSimpleFunction,
    ): List<IrExpression> {
        val sourceText = currentSourceText ?: return listOf(irConst)
        val mergedText = irConst.value as String
        val sourceStart = irConst.startOffset
        val sourceEnd = irConst.endOffset
        if (sourceStart < 0 || sourceEnd > sourceText.length) return listOf(irConst)
        val source = sourceText.substring(sourceStart, sourceEnd)
        // Find all ${"..."} patterns in the source and split the merged text.
        val result = mutableListOf<IrExpression>()
        var textPosition = 0
        var sourcePosition = 0
        while (sourcePosition < source.length) {
            val expressionStart = source.indexOf("\${\"", sourcePosition)
            if (expressionStart == -1) break
            // Add the fragment before the expression.
            val fragmentSourceLength = expressionStart - sourcePosition
            if (fragmentSourceLength > 0) {
                val fragmentText = mergedText.substring(textPosition, textPosition + fragmentSourceLength)
                val fragmentSource = source.substring(sourcePosition, expressionStart)
                if (fragmentSource != fragmentText) {
                    // Fragment contains escape sequences; cannot reliably split.
                    return listOf(irConst)
                }
                result.add(createStringConst(
                    sourceStart + sourcePosition,
                    sourceStart + expressionStart,
                    irConst.type,
                    fragmentText,
                ))
                textPosition += fragmentSourceLength
            }
            // Parse the string literal inside ${"..."}.
            val contentStart = expressionStart + 3 // position after ${"
            val closingQuote = findClosingQuote(source, contentStart)
            if (closingQuote == -1) return listOf(irConst) // Malformed; leave unchanged.
            val expressionSourceContent = source.substring(contentStart, closingQuote)
            if (expressionSourceContent.contains('\\')) {
                // Expression contains escape sequences; cannot reliably determine the runtime value.
                return listOf(irConst)
            }
            val expressionValueLength = expressionSourceContent.length
            if (textPosition + expressionValueLength > mergedText.length) return listOf(irConst)
            val expressionValue = mergedText.substring(textPosition, textPosition + expressionValueLength)
            if (expressionValue != expressionSourceContent) {
                // Mismatch between source and merged text; cannot reliably split.
                return listOf(irConst)
            }
            // Wrap the expression value in t().
            val expressionConst = createStringConst(
                sourceStart + expressionStart + 2, // position of opening "
                sourceStart + closingQuote + 1,    // position after closing "
                irConst.type,
                expressionValue,
            )
            result.add(wrapInT(expressionConst, receiver, tFunction))
            textPosition += expressionValueLength
            sourcePosition = closingQuote + 2 // skip past "}
        }
        // Add remaining fragment after the last expression.
        if (textPosition < mergedText.length) {
            val remainingFragment = mergedText.substring(textPosition)
            val remainingSource = source.substring(sourcePosition)
            if (remainingSource != remainingFragment) {
                // Fragment contains escape sequences; cannot reliably split.
                return listOf(irConst)
            }
            result.add(createStringConst(
                sourceStart + sourcePosition,
                sourceEnd,
                irConst.type,
                remainingFragment,
            ))
        }
        return if (result.isEmpty()) listOf(irConst) else result
    }

    /** Finds the position of the closing quote (`"`) in a string literal, handling escaped quotes. */
    private fun findClosingQuote(source: String, fromIndex: Int): Int {
        var i = fromIndex
        while (i < source.length) {
            when (source[i]) {
                '"' -> return i
                '\\' -> i++ // Skip escaped character.
            }
            i++
        }
        return -1
    }

    /** Creates a new string [IrConst] with the given offsets and value. */
    private fun createStringConst(startOffset: Int, endOffset: Int, type: IrType, value: String): IrConst {
        return IrConstImpl.string(startOffset, endOffset, type, value)
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
        val dispatchFqName = dispatchReceiver.type.classFqName ?: return false
        return dispatchFqName == TEMPLATE_CONTEXT_FQN
    }

    /** Wraps an expression in a call to `TemplateContext.t(expr)`, generating IR equivalent to `receiver.t(expr)`. */
    private fun wrapInT(
        expression: IrExpression,
        receiver: IrValueParameter,
        tFunction: IrSimpleFunction,
    ): IrExpression {
        val builder = DeclarationIrBuilder(pluginContext, tFunction.symbol, expression.startOffset, expression.endOffset)
        return builder.irCall(tFunction).apply {
            dispatchReceiver = builder.irGet(receiver)
            putValueArgument(0, expression)
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
            .firstOrNull { it.name.asString() == "t" && it.valueParameters.size == 1 }
    }

    /** Resolves the `TemplateContext.autoInterpolation()` function symbol. */
    private fun resolveAutoInterpolationFunction(): IrSimpleFunction? {
        val templateContextClass = pluginContext.referenceClass(TEMPLATE_CONTEXT_CLASS_ID) ?: return null
        return templateContextClass.owner.functions
            .firstOrNull { it.name.asString() == "autoInterpolation" && it.valueParameters.isEmpty() }
    }
}
