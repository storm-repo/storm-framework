package st.orm.validator

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class NoDirectInterpolationRuleAdditionalTest(private val env: KotlinCoreEnvironment) {

    private val rule = NoDirectInterpolationRule()

    @Test
    fun `reports multiple unsafe interpolations in single template`() {
        val code = """
            fun foo() {
                val x = "Hello"
                val y = "World"
                st.orm.template.TemplateString.raw {
                    "${"$"}{x} and ${"$"}{y}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isNotEmpty()) { "Expected findings for multiple unsafe interpolations but got none." }
    }

    @Test
    fun `reports mixed safe and unsafe interpolations`() {
        val code = """
            fun foo() {
                val x = "Hello"
                val y = "World"
                st.orm.template.TemplateString.raw {
                    "${"$"}{t(x)} and ${"$"}{y}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isNotEmpty()) { "Expected findings for the unsafe interpolation but got none." }
    }

    @Test
    fun `ignores template when all interpolations are safe`() {
        val code = """
            fun foo() {
                val x = "Hello"
                val y = "World"
                st.orm.template.TemplateString.raw {
                    "${"$"}{t(x)} and ${"$"}{insert(y)}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings when all interpolations use t() or insert()." }
    }

    @Test
    fun `reports simple name interpolation in TemplateContext`() {
        val code = """
            fun foo() {
                val name = "World"
                st.orm.template.TemplateString.raw {
                    "Hello ${"$"}name"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isNotEmpty()) { "Expected findings for simple name interpolation but got none." }
    }

    @Test
    fun `reports call expression with non-safe function name`() {
        val code = """
            fun other(value: String): String = value
            fun foo() {
                val name = "World"
                st.orm.template.TemplateString.raw {
                    "Hello ${"$"}{other(name)}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isNotEmpty()) { "Expected findings for non-safe function call but got none." }
    }

    @Test
    fun `ignores string template outside lambda context`() {
        val code = """
            fun foo() {
                val name = "World"
                val greeting = "Hello ${"$"}{name}"
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings for interpolation outside lambda context." }
    }

    @Test
    fun `ignores simple name interpolation outside lambda context`() {
        val code = """
            fun foo() {
                val name = "World"
                val greeting = "Hello ${"$"}name"
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings for simple name interpolation outside lambda context." }
    }

    @Test
    fun `ignores interpolation in lambda whose function parameter is not extension function`() {
        // When the resolved call's parameter type is NOT an extension function type,
        // the rule should not report (exercises the isExtensionFunctionType == false path).
        val code = """
            fun bar(action: () -> String): String = action()
            fun foo() {
                val name = "World"
                bar {
                    "Hello ${"$"}{name}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings when lambda parameter is not an extension function type." }
    }

    @Test
    fun `ignores interpolation when resolved call has no value parameters`() {
        // When the call expression has no value parameters (e.g., only a lambda trailing argument),
        // paramType is null and the rule should bail out.
        val code = """
            val block: (() -> String) = { "" }
            fun foo() {
                val name = "World"
                block {
                    "Hello ${"$"}{name}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings when call has no value parameters." }
    }

    @Test
    fun `ignores interpolation in lambda with non-TemplateContext receiver`() {
        // When the function parameter IS an extension function but its receiver
        // is not TemplateContext, the rule should not report.
        val code = """
            fun baz(action: StringBuilder.() -> String): String = StringBuilder().action()
            fun foo() {
                val name = "World"
                baz {
                    "Hello ${"$"}{name}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings when receiver is not TemplateContext." }
    }
}
