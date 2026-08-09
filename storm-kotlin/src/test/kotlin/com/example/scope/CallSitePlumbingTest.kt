package com.example.scope

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.core.template.SqlLog.Summary
import st.orm.core.template.impl.CallSiteCapture
import st.orm.template.DEFAULT_SQL_LOG_LIMIT
import st.orm.template.IntegrationConfig
import st.orm.template.ORMTemplate
import st.orm.template.impl.recordSqlLog
import st.orm.template.model.PetOwnerRef

/**
 * Verifies that a declared plumbing file hides the frames of its inline functions: their lambdas compile into
 * the caller's class, where a package prefix cannot see them, while the frame keeps the declaring file's name.
 * This class deliberately lives outside the framework packages, since frames inside them are infrastructure to
 * the call-site walker.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class CallSitePlumbingTest(
    @Autowired val orm: ORMTemplate,
) {

    private suspend fun <T> record(name: String, block: suspend () -> T): Summary {
        var summary: Summary? = null
        recordSqlLog(name, DEFAULT_SQL_LOG_LIMIT, true, block) { summary = it }
        return summary!!
    }

    @Test
    fun `a plumbing file entry hides the frames of its inline functions`(): Unit = runBlocking {
        CallSiteCapture.ignoreCallSites("Plumbing.kt")
        val summary = record("plumbed") {
            throughDbLayer { orm.entity(PetOwnerRef::class).select().resultList }
        }
        val callSite = summary.byStatement().first().callSite()
        callSite.shouldNotBeNull()
        // The plumbing frames step aside; the row names this test, the caller beyond the layer.
        callSite shouldStartWith "CallSitePlumbingTest.kt:"
    }

    @Test
    fun `work launched onto another dispatcher names the frame that launched it`(): Unit = runBlocking {
        CallSiteCapture.ignoreCallSites("Plumbing.kt")
        val summary = record("launched") {
            fetchAllOnDispatcher(orm)
        }
        val callSite = summary.byStatement().first().callSite()
        callSite.shouldNotBeNull()
        // The dispatcher thread's stack is plumbing end to end; without the carried launch site this row would
        // name the plumbing's innermost frame.
        callSite shouldStartWith "CallSitePlumbingTest.kt:"
    }
}
