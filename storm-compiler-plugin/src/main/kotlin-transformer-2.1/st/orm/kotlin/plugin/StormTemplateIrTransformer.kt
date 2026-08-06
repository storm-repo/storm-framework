@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package st.orm.kotlin.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irConcat
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
 * The `+` operator on strings follows the same rules, so `"SELECT $col, " + "COUNT(*)"` yields the same template as
 * `"SELECT $col, COUNT(*)"`: literal operands are SQL text and every other operand is interpolated. An expression
 * inside an interpolation yields a value rather than SQL, so its own interpolations and concatenations are left to
 * Kotlin: `"... LIKE ${"%" + name + "%"}"` interpolates a single string.
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
        private val STRING_FQN = FqName("kotlin.String")
    }

    /**
     * Tracks whether the expression being visited contributes SQL text. Text position covers the statements of a
     * TemplateBuilder lambda; the arguments of a string template (`${...}`) are value position, because they yield
     * bind values rather than SQL.
     */
    private var textPosition: Boolean = false

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
        val previousTextPosition = textPosition
        templateContextReceiver = extensionReceiver
        textPosition = true
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
        textPosition = previousTextPosition
        return result
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        if (templateContextReceiver == null || tFunctionSymbol == null) {
            return super.visitStringConcatenation(expression)
        }
        if (!textPosition) {
            // Value position: the string is computed as an ordinary Kotlin expression and interpolated as a single
            // bind value, so its own interpolations are left alone. Nested TemplateBuilder lambdas are still visited.
            return super.visitStringConcatenation(expression)
        }
        return processConcatenation(expression, isOperatorConcatenation(expression))
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val tFunction = tFunctionSymbol
        if (templateContextReceiver == null || tFunction == null || !textPosition || !isStringPlus(expression)) {
            return super.visitCall(expression)
        }
        // `a + b` in text position means the same as "$a$b", so rewrite it into a concatenation and apply the
        // template rules to its operands. Nested `+` calls become concatenations in turn and are flattened below.
        val operands = listOfNotNull(expression.dispatchReceiver, expression.getValueArgument(0))
        if (operands.size != 2) {
            return super.visitCall(expression)
        }
        val builder = DeclarationIrBuilder(pluginContext, tFunction.symbol, expression.startOffset, expression.endOffset)
        val concatenation = builder.irConcat()
        concatenation.arguments.addAll(operands)
        return processConcatenation(concatenation, operatorConcatenation = true)
    }

    /**
     * Applies the template rules to the arguments of [expression]: literal text stays a fragment and every other
     * argument is wrapped in a `t()` call.
     *
     * When [operatorConcatenation] is true, the node represents a `+` chain rather than a single string literal. Its
     * arguments are operands that contribute SQL text, so they are visited in text position and nested concatenations
     * are spliced in rather than interpolated as a value. The arguments of a string literal are `${...}` expressions,
     * which are visited in value position.
     */
    private fun processConcatenation(
        expression: IrStringConcatenation,
        operatorConcatenation: Boolean,
    ): IrExpression {
        val receiver = templateContextReceiver ?: return expression
        val tFunction = tFunctionSymbol ?: return expression
        // Recursively transform each argument first, so that nested TemplateBuilder lambdas (e.g., inside subquery
        // calls) are processed before we wrap the argument in t().
        val newArguments = expression.arguments.flatMap { argument ->
            val transformed = transformInPosition(argument, operatorConcatenation)
            when {
                transformed is IrConst && isFragment(transformed) -> listOf(transformed)
                transformed is IrConst && hasMergedConstant(transformed) ->
                    splitMergedConstant(transformed, receiver, tFunction)
                // A string literal operand of a `+` chain is SQL text, like the literal part of a string template.
                // Any other constant is interpolated, so that `+ 42` and `${42}` produce the same bind value.
                transformed is IrConst && operatorConcatenation && transformed.value is String -> listOf(transformed)
                transformed is IrStringConcatenation && operatorConcatenation -> transformed.arguments.toList()
                isAlreadyWrappedInT(transformed) -> listOf(transformed)
                else -> listOf(wrapInT(transformed, receiver, tFunction))
            }
        }
        expression.arguments.clear()
        expression.arguments.addAll(newArguments)
        return expression
    }

    /** Transforms [expression] with [textPosition] set for the duration of the visit. */
    private fun transformInPosition(expression: IrExpression, textPosition: Boolean): IrExpression {
        val previousTextPosition = this.textPosition
        this.textPosition = textPosition
        val result = expression.transform(this, null)
        this.textPosition = previousTextPosition
        return result
    }

    /**
     * Checks whether an [IrStringConcatenation] represents a `+` chain rather than a single string literal.
     *
     * The compiler folds `"a${b}" + "c"` into one concatenation node whose arguments are the operands, which is
     * indistinguishable in shape from the arguments of a string literal. The source range tells them apart: the
     * arguments of a string literal all start after its opening quote, while the first operand of a `+` chain starts
     * where the chain itself starts.
     */
    private fun isOperatorConcatenation(expression: IrStringConcatenation): Boolean {
        val first = expression.arguments.firstOrNull() ?: return false
        if (expression.startOffset < 0 || first.startOffset < 0) return false
        return first.startOffset <= expression.startOffset
    }

    /** Checks whether a call is `String.plus`, the desugared form of the `+` operator on strings. */
    private fun isStringPlus(expression: IrCall): Boolean {
        if (expression.symbol.owner.name.asString() != "plus") return false
        val receiverType = expression.dispatchReceiver?.type ?: return false
        return receiverType.classFqName == STRING_FQN
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
