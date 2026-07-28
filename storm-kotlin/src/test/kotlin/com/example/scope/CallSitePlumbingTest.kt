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
import st.orm.template.IntegrationConfig
import st.orm.template.ORMTemplate
import st.orm.template.ignoreSqlScopeCallSites
import st.orm.template.model.PetOwnerRef
import st.orm.template.sqlScope

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

    @Test
    fun `a plumbing file entry hides the frames of its inline functions`(): Unit = runBlocking {
        ignoreSqlScopeCallSites("Plumbing.kt")
        val (_, summary) = sqlScope("plumbed", callSites = true) {
            throughDbLayer { orm.entity(PetOwnerRef::class).select().resultList }
        }
        val callSite = summary.byStatement.first().callSite
        callSite.shouldNotBeNull()
        // The plumbing frames step aside; the row names this test, the caller beyond the layer.
        callSite shouldStartWith "CallSitePlumbingTest.kt:"
    }

    @Test
    fun `work launched onto another dispatcher names the frame that launched it`(): Unit = runBlocking {
        ignoreSqlScopeCallSites("Plumbing.kt")
        val (_, summary) = sqlScope("launched", callSites = true) {
            fetchAllOnDispatcher(orm)
        }
        val callSite = summary.byStatement.first().callSite
        callSite.shouldNotBeNull()
        // The dispatcher thread's stack is plumbing end to end; without the carried launch site this row would
        // name the plumbing's innermost frame.
        callSite shouldStartWith "CallSitePlumbingTest.kt:"
    }
}
