package st.orm.template

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Metamodel
import st.orm.core.template.SqlLog.Summary
import st.orm.core.template.StatementOrigin.FETCH
import st.orm.template.impl.recordSqlLog
import st.orm.template.model.Owner
import st.orm.template.model.PetOwnerRef
import st.orm.core.template.SqlLog as CoreSqlLog

/**
 * Verifies that a scope follows the coroutine rather than the thread: it keeps recording across a suspension that
 * resumes on another thread, which is the case a thread-bound scope loses.
 *
 * The recording machinery is exercised directly, since the summary it produces is internal wiring on its way to
 * the `st.orm.sql.summary` logger rather than something [sqlLog] hands to the application.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class SqlLogTest(
    @Autowired val orm: ORMTemplate,
) {

    private val ownerPath: Metamodel<PetOwnerRef, Owner> = Metamodel.of(PetOwnerRef::class.java, "owner")

    private suspend fun <T> record(
        name: String,
        limit: Int = DEFAULT_SQL_LOG_LIMIT,
        callSites: Boolean = false,
        block: suspend () -> T,
    ): Pair<T, Summary> {
        var summary: Summary? = null
        val result = recordSqlLog(name, limit, callSites, block) { summary = it }
        return result to summary!!
    }

    @Test
    fun `sqlLog returns the block's result`(): Unit = runBlocking {
        val pets = sqlLog("load") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        pets.shouldNotBeEmpty()
    }

    @Test
    fun `a scope records the statements of the block`(): Unit = runBlocking {
        val (pets, summary) = record("load") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        pets.shouldNotBeEmpty()
        summary.name() shouldBe "load"
        summary.statementCount() shouldBe 1
    }

    @Test
    fun `a scope survives a suspension that resumes on another thread`(): Unit = runBlocking {
        val threads = mutableSetOf<String>()
        val (_, summary) = record("across-threads") {
            threads += Thread.currentThread().name
            orm.entity(PetOwnerRef::class).select().resultList
            // Hop dispatchers: a thread-bound scope stops recording from here on.
            withContext(Dispatchers.IO) {
                threads += Thread.currentThread().name
                orm.entity(PetOwnerRef::class).select().resultList
            }
            yield()
            threads += Thread.currentThread().name
            orm.entity(PetOwnerRef::class).select().resultList
        }
        threads.size shouldBeGreaterThan 1
        summary.statementCount() shouldBe 3
    }

    @Test
    fun `a scope counts the references the block resolved`(): Unit = runBlocking {
        val (_, summary) = record("n-plus-one") {
            val pets = orm.entity(PetOwnerRef::class).select().resultList
            withContext(Dispatchers.IO) {
                pets.mapNotNull { it.owner }.forEach { it.fetch() }
            }
        }
        (summary.statementCount() - summary.count(FETCH)) shouldBe 1
        summary.count(FETCH) shouldBeGreaterThan 0
    }

    @Test
    fun `naming the reference leaves the scope at one statement`(): Unit = runBlocking {
        val (_, summary) = record("fetched") {
            val pets = orm.entity(PetOwnerRef::class).select().fetch(ownerPath).resultList
            pets.mapNotNull { it.owner }.forEach { it.fetch() }
        }
        summary.statementCount() shouldBe 1
        summary.count(FETCH) shouldBe 0
    }

    @Test
    fun `a nested scope records alongside its parent`(): Unit = runBlocking {
        var inner: Summary? = null
        val (_, outer) = record("outer") {
            orm.entity(PetOwnerRef::class).select().resultList
            recordSqlLog("inner", DEFAULT_SQL_LOG_LIMIT, false, {
                orm.entity(PetOwnerRef::class).select().resultList
            }) { inner = it }
        }
        // The inner scope sees only its own statement; the outer sees both.
        inner!!.statementCount() shouldBe 1
        outer.statementCount() shouldBe 2
    }

    @Test
    fun `a scope does not record statements executed after it closed`(): Unit = runBlocking {
        val (_, summary) = record("scoped") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        orm.entity(PetOwnerRef::class).select().resultList
        summary.statementCount() shouldBe 1
    }

    @Test
    fun `a concurrent coroutine does not observe another scope`(): Unit = runBlocking {
        val (_, summary) = record("mine") {
            // Work started outside this scope must not land in it.
            withContext(Dispatchers.IO) {
                orm.entity(PetOwnerRef::class).select().resultList
            }
        }
        summary.statementCount() shouldBe 1
        val (_, other) = record("theirs") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        other.statementCount() shouldBe 1
    }

    @Test
    fun `a scope opened in blocking code reaches an app-built coroutine through sqlLogContext`(): Unit = runBlocking {
        // The shape a blocking service takes: a thread-bound scope, then a coroutine the app builds itself.
        val summaries = mutableListOf<Summary>()
        st.orm.core.template.SqlLog.record<Any?>("blocking", {
            runBlocking(sqlLogContext()) {
                withContext(Dispatchers.IO) {
                    orm.entity(PetOwnerRef::class).select().resultList
                }
            }
        }, summaries::add)
        summaries.first().statementCount() shouldBe 1
    }

    @Test
    fun `a scope opened in blocking code does not reach a coroutine that was not given the context`(): Unit = runBlocking {
        // Without the context, the coroutine inherits no binding and the statement falls outside the scope.
        val summaries = mutableListOf<Summary>()
        st.orm.core.template.SqlLog.record<Any?>("blocking", {
            runBlocking {
                withContext(Dispatchers.IO) {
                    orm.entity(PetOwnerRef::class).select().resultList
                }
            }
        }, summaries::add)
        summaries.first().statementCount() shouldBe 0
    }

    @Test
    fun `a transaction carries a blocking scope into the coroutines below it`(): Unit = runBlocking {
        // Storm builds the context here, so the fan-out below observes the scope without the caller arranging it.
        val summaries = mutableListOf<Summary>()
        st.orm.core.template.SqlLog.record<Any?>("blocking", {
            runBlocking {
                withTransactionOptions(timeoutSeconds = 30) {
                    withContext(Dispatchers.IO) {
                        orm.entity(PetOwnerRef::class).select().resultList
                    }
                }
            }
        }, summaries::add)
        summaries.first().statementCount() shouldBe 1
    }

    @Test
    fun `a scope counts every statement when coroutines record concurrently`(): Unit = runBlocking {
        // The fan-out shape: many coroutines recording into one scope at once. A recorder that is not thread
        // safe loses increments here, or corrupts the list it appends to.
        val executions = 200
        val (_, summary) = record("fan-out") {
            coroutineScope {
                (1..executions).map {
                    async(Dispatchers.IO) { orm.entity(PetOwnerRef::class).select().resultList }
                }.awaitAll()
            }
        }
        summary.statementCount() shouldBe executions
        summary.statements().size shouldBe executions
    }

    @Test
    fun `the recording limit holds under concurrency`(): Unit = runBlocking {
        val executions = 100
        val limit = 10
        val (_, summary) = record("fan-out", limit) {
            coroutineScope {
                (1..executions).map {
                    async(Dispatchers.IO) { orm.entity(PetOwnerRef::class).select().resultList }
                }.awaitAll()
            }
        }
        summary.statementCount() shouldBe executions
        summary.statements().size shouldBe limit
        summary.truncated() shouldBe true
    }

    @Test
    fun `a hydration shape renders for a data class entity`(): Unit = runBlocking {
        // The shape derives through the reflection provider, which recognizes Kotlin data classes; a JVM-record
        // check would leave these rows bare.
        CoreSqlLog.hydrationShapes(CoreSqlLog.HydrationShapes.FULL)
        try {
            val (_, summary) = record("shape") {
                orm.entity(PetOwnerRef::class).select().resultList
            }
            val rendered = summary.toString()
            rendered shouldContain "joins="
            rendered shouldContain "graph=PetOwnerRef"
        } finally {
            CoreSqlLog.hydrationShapes(CoreSqlLog.HydrationShapes.OFF)
        }
    }
}
