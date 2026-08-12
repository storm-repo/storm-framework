package st.orm.ktor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import st.orm.core.template.SqlLog.HydrationShapes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies the per-option resolution of the SQL log settings: an explicit plugin setting wins, otherwise the
 * HOCON configuration under `storm.sqlLog` (or `storm.sql_log`) applies, otherwise the option's default.
 */
internal class SqlLogConfigTest {

    @Test
    fun `defaults apply when neither the plugin nor the configuration sets an option`() {
        val settings = resolve()
        settings.enabled shouldBe false
        settings.limit shouldBe 200
        settings.statementThreshold shouldBe null
        settings.durationThreshold shouldBe null
        settings.callSites shouldBe false
        settings.callSiteSkip shouldBe emptyList()
        settings.lineWidth shouldBe null
        settings.hydration shouldBe null
    }

    @Test
    fun `every option reads from camelCase configuration keys`() {
        val settings = resolve(
            "storm.sqlLog.enabled" to "true",
            "storm.sqlLog.limit" to "50",
            "storm.sqlLog.threshold.statements" to "10",
            "storm.sqlLog.threshold.duration" to "250ms",
            "storm.sqlLog.callSites" to "true",
            "storm.sqlLog.callSiteSkip" to "com.myapp.data, com.myapp.plumbing",
            "storm.sqlLog.lineWidth" to "120",
            "storm.sqlLog.hydration" to "short",
        )
        settings.enabled shouldBe true
        settings.limit shouldBe 50
        settings.statementThreshold shouldBe 10
        settings.durationThreshold shouldBe 250.milliseconds
        settings.callSites shouldBe true
        settings.callSiteSkip shouldBe listOf("com.myapp.data", "com.myapp.plumbing")
        settings.lineWidth shouldBe 120
        settings.hydration shouldBe HydrationShapes.SHORT
    }

    @Test
    fun `every option reads from snake_case configuration keys`() {
        val settings = resolve(
            "storm.sql_log.enabled" to "true",
            "storm.sql_log.limit" to "25",
            "storm.sql_log.threshold.statements" to "5",
            "storm.sql_log.threshold.duration" to "2s",
            "storm.sql_log.call_sites" to "true",
            "storm.sql_log.call_site_skip" to "com.myapp.data",
            "storm.sql_log.line_width" to "240",
            "storm.sql_log.hydration" to "FULL",
        )
        settings.enabled shouldBe true
        settings.limit shouldBe 25
        settings.statementThreshold shouldBe 5
        settings.durationThreshold shouldBe 2.seconds
        settings.callSites shouldBe true
        settings.callSiteSkip shouldBe listOf("com.myapp.data")
        settings.lineWidth shouldBe 240
        settings.hydration shouldBe HydrationShapes.FULL
    }

    @Test
    fun `callSiteSkip reads a configuration list`() {
        val applicationConfig = MapApplicationConfig()
        applicationConfig.put("storm.sqlLog.callSiteSkip", listOf("com.myapp.data", "com.myapp.web"))
        val settings = resolve(applicationConfig)
        settings.callSiteSkip shouldBe listOf("com.myapp.data", "com.myapp.web")
    }

    @Test
    fun `a plugin setting overrides the configuration file`() {
        val pluginConfig = StormPluginConfig().apply {
            sqlLog = false
            sqlLogLimit = 5
        }
        val settings = resolve(
            "storm.sqlLog.enabled" to "true",
            "storm.sqlLog.limit" to "99",
            pluginConfig = pluginConfig,
        )
        settings.enabled shouldBe false
        settings.limit shouldBe 5
    }

    @Test
    fun `camelCase takes precedence over snake_case`() {
        val settings = resolve(
            "storm.sqlLog.limit" to "10",
            "storm.sql_log.limit" to "20",
        )
        settings.limit shouldBe 10
    }

    @Test
    fun `an invalid boolean aborts installation naming the key`() {
        val exception = shouldThrow<IllegalStateException> {
            resolve("storm.sqlLog.enabled" to "yes")
        }
        exception.message!! shouldContain "storm.sqlLog.enabled"
        exception.message!! shouldContain "'yes'"
    }

    @Test
    fun `an invalid integer aborts installation naming the key`() {
        val exception = shouldThrow<IllegalStateException> {
            resolve("storm.sql_log.limit" to "many")
        }
        exception.message!! shouldContain "storm.sql_log.limit"
    }

    @Test
    fun `an invalid duration aborts installation naming the key`() {
        val exception = shouldThrow<IllegalStateException> {
            resolve("storm.sqlLog.threshold.duration" to "fast")
        }
        exception.message!! shouldContain "storm.sqlLog.threshold.duration"
    }

    @Test
    fun `an invalid hydration mode aborts installation naming the valid values`() {
        val exception = shouldThrow<IllegalStateException> {
            resolve("storm.sqlLog.hydration" to "verbose")
        }
        exception.message!! shouldContain "storm.sqlLog.hydration"
        exception.message!! shouldContain "off, short, full"
    }

    private fun resolve(
        vararg pairs: Pair<String, String>,
        pluginConfig: StormPluginConfig = StormPluginConfig(),
    ): SqlLogSettings = resolve(MapApplicationConfig(*pairs), pluginConfig)

    private fun resolve(
        applicationConfig: MapApplicationConfig,
        pluginConfig: StormPluginConfig = StormPluginConfig(),
    ): SqlLogSettings {
        var result: SqlLogSettings? = null
        var failure: Throwable? = null
        runBlocking {
            testApplication {
                environment {
                    config = applicationConfig
                }
                application {
                    // Resolution failures are rethrown outside the application block: testApplication wraps
                    // an exception thrown during a module into its own startup failure.
                    try {
                        result = resolveSqlLogSettings(this, pluginConfig)
                    } catch (e: Throwable) {
                        failure = e
                    }
                }
            }
        }
        failure?.let { throw it }
        return result!!
    }
}
