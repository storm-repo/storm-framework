package st.orm.kt.validator

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class NoDirectInterpolationRuleTest(private val env: KotlinCoreEnvironment) {

    private val rule = NoDirectInterpolationRule()

    @Test
    fun `reports direct interpolation in TemplateContext`() {
        val code = """
            fun foo() {
                val name = "World"
                st.orm.kt.template.TemplateString.raw {
                    "Hello ${"$"}{name}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isNotEmpty()) { "Expected findings but got none." }
    }

    @Test
    fun `ignores direct interpolation when no TemplateContext`() {
        val code = """
            fun foo() {
                val name = "World"
                raw {
                    "Hello ${"$"}{name}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings." }
    }

    @Test
    fun `ignores direct interpolation when no interpolation`() {
        val code = """
            fun foo() {
                val name = "World"
                raw {
                    "Hello " + name
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings." }
    }

    @Test
    fun `ignores direct interpolation when wrapped with t()`() {
        val code = """
            fun foo() {
                val name = "World"
                st.orm.kt.template.TemplateString.raw {
                    "Hello ${"$"}{t(name)}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings." }
    }

    @Test
    fun `ignores direct interpolation when wrapped with insert()`() {
        val code = """
            fun foo() {
                val name = "World"
                st.orm.kt.template.TemplateString.raw {
                    "Hello ${"$"}{insert(name)}"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLintWithContext(env, code)
        assert(findings.isEmpty()) { "Expected no findings." }
    }
}