package st.orm.kotlin.plugin

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the Storm Template compiler plugin.
 *
 * Each test compiles a Kotlin source snippet with the plugin applied and verifies
 * that string interpolations inside TemplateBuilder lambdas are auto-wrapped in t() calls.
 */
@OptIn(ExperimentalCompilerApi::class)
class StormTemplatePluginTest {

    private val templateContextStub = SourceFile.kotlin(
        "TemplateContext.kt",
        """
        package st.orm.template

        interface TemplateContext {
            fun t(o: Any?): String = insert(o)
            fun insert(o: Any?): String
            fun autoInterpolation() {}
        }

        typealias TemplateBuilder = TemplateContext.() -> String

        data class TemplateString(
            val fragments: List<String>,
            val values: List<Any?>,
            val autoInterpolation: Boolean = false,
        )

        fun TemplateBuilder.build(): TemplateString {
            var autoInterpolation = false
            val values = mutableListOf<Any?>()
            val raw = this(object : TemplateContext {
                override fun insert(o: Any?): String {
                    values.add(o)
                    return "\u0000"
                }
                override fun autoInterpolation() {
                    autoInterpolation = true
                }
            })
            val fragments = raw.split("\u0000")
            return TemplateString(fragments, values, autoInterpolation)
        }
        """,
    )

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = listOf(templateContextStub) + sources.toList()
            compilerPluginRegistrars = listOf(StormTemplatePluginRegistrar())
            inheritClassPath = true
            languageVersion = "2.0"
        }.compile()
    }

    private fun JvmCompilationResult.runMain(): String {
        val output = StringBuilder()
        val mainClass = classLoader.loadClass("TestKt")
        val oldOut = System.out
        val capture = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(capture))
        try {
            mainClass.getMethod("main").invoke(null)
        } finally {
            System.setOut(oldOut)
        }
        return capture.toString().trim()
    }

    @Test
    fun `plain string literal is unchanged`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val builder: TemplateBuilder = { "SELECT COUNT(*) FROM users" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.size)
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT COUNT(*) FROM users", lines[0])
        assertEquals("0", lines[1])
    }

    @Test
    fun `single interpolation is auto-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}id" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = |", lines[0])
        assertEquals("42", lines[1])
    }

    @Test
    fun `multiple interpolations are auto-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val status = "active"
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}id AND status = ${'$'}status" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = | AND status = |", lines[0])
        assertEquals("42,active", lines[1])
    }

    @Test
    fun `explicit t() is not double-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}{t(id)}" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = |", lines[0])
        assertEquals("42", lines[1])
    }

    @Test
    fun `mixed explicit and auto-wrapped interpolations`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val name = "Alice"
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}{t(id)} AND name = ${'$'}name" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = | AND name = |", lines[0])
        assertEquals("42,Alice", lines[1])
    }

    @Test
    fun `multiline template is auto-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val name = "Alice"
                val builder: TemplateBuilder = {
                    ${"\"\"\""}
                    SELECT *
                    FROM users
                    WHERE id = ${'$'}id
                      AND name = ${'$'}name
                    ${"\"\"\""}.trimIndent()
                }
                val result = builder.build()
                println(result.values.joinToString(","))
                println(result.fragments.size)
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("42,Alice", lines[0])
        assertEquals("3", lines[1])
    }

    @Test
    fun `function parameter typed as TemplateBuilder is rewritten`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun query(template: TemplateBuilder): TemplateString = template.build()

            fun main() {
                val id = 42
                val result = query { "SELECT * FROM users WHERE id = ${'$'}id" }
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = |", lines[0])
        assertEquals("42", lines[1])
    }

    @Test
    fun `insert() is not double-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}{insert(id)}" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(","))
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id = |", lines[0])
        assertEquals("42", lines[1])
    }

    @Test
    fun `string outside TemplateBuilder is not rewritten`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val regular = "id is ${'$'}id"
                println(regular)
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        assertEquals("id is 42", output)
    }

    @Test
    fun `null value interpolation is auto-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val name: String? = null
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE name = ${'$'}name" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values.joinToString(",") { it?.toString() ?: "NULL" })
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE name = |", lines[0])
        assertEquals("NULL", lines[1])
    }

    @Test
    fun `autoInterpolation is called for template with interpolations`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val id = 42
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id = ${'$'}id" }
                val result = builder.build()
                println(result.autoInterpolation)
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        assertEquals("true", output)
    }

    @Test
    fun `autoInterpolation is called for plain literal template`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val builder: TemplateBuilder = { "SELECT COUNT(*) FROM users" }
                val result = builder.build()
                println(result.autoInterpolation)
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        assertEquals("true", output)
    }

    @Test
    fun `expression interpolation is auto-wrapped`() {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            import st.orm.template.*

            fun main() {
                val ids = listOf(1, 2, 3)
                val builder: TemplateBuilder = { "SELECT * FROM users WHERE id IN ${'$'}{ids}" }
                val result = builder.build()
                println(result.fragments.joinToString("|"))
                println(result.values[0])
            }
            """,
        )
        val result = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val output = result.runMain()
        val lines = output.lines()
        assertEquals("SELECT * FROM users WHERE id IN |", lines[0])
        assertEquals("[1, 2, 3]", lines[1])
    }
}
