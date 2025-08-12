package st.orm.validator

import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

class NoDirectInterpolationRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoDirectInterpolation",
        severity = Severity.CodeSmell,
        description = "Avoid direct `\${…}` inside TemplateContext scope; wrap value with t()/insert() instead.",
        debt = Debt.FIVE_MINS
    )

    override fun visitStringTemplateExpression(expr: KtStringTemplateExpression) {
        // Collect only the **unsafe** embedded entries.
        val unsafeEntries = expr.entries.filter { entry ->
            when (entry) {
                is KtSimpleNameStringTemplateEntry -> true // always unsafe
                is KtBlockStringTemplateEntry -> {
                    val innerExpr = entry.expression
                    // directly check if the inner expression is a call to t() or insert()
                    if (innerExpr is KtCallExpression) {
                        val called = (innerExpr.calleeExpression as? KtNameReferenceExpression)
                            ?.getReferencedName()
                        called !in setOf("t", "insert")
                    } else true // if not a call, it's unsafe
                }
                else -> false
            }
        }
        // If there are no unsafe entries, bail out.
        if (unsafeEntries.isEmpty()) return
        val lambda = expr.getStrictParentOfType<KtLambdaExpression>() ?: return
        // Find the call expression that this lambda is an argument to.
        val call = lambda.getStrictParentOfType<KtCallExpression>() ?: return
        // Resolve that call. Note that we need type resolution context for this.
        // This can be enabled by adding the following to the `detekt` task in `build.gradle.kts`:
        //     classpath.setFrom(
        //        sourceSets.main.get().compileClasspath,
        //        sourceSets.main.get().runtimeClasspath
        //    )
        val resolvedCall = call.getResolvedCall(bindingContext) ?: return
        // Get the type of the first value parameter.
        val paramType = resolvedCall.resultingDescriptor.valueParameters
            .firstOrNull()
            ?.type
            ?: return
        // Check it’s an extension function on TemplateContext.
        if (paramType.isExtensionFunctionType) {
            val receiverFqn = paramType
                .getReceiverTypeFromFunctionType()
                ?.constructor
                ?.declarationDescriptor
                ?.fqNameOrNull()
                ?.asString()
            if (receiverFqn == "st.orm.template.TemplateContext") {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(expr),
                        "Avoid direct `\${…}` in TemplateContext scope; wrap with t()/insert()."
                    )
                )
            }
        }
        super.visitStringTemplateExpression(expr)
    }
}