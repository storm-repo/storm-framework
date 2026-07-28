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
import st.orm.template.model.Owner
import st.orm.template.model.PetOwnerRef

/**
 * Verifies that a scope follows the coroutine rather than the thread: it keeps recording across a suspension that
 * resumes on another thread, which is the case a thread-bound scope loses.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class SqlScopeTest(
    @Autowired val orm: ORMTemplate,
) {

    private val ownerPath: Metamodel<PetOwnerRef, Owner> = Metamodel.of(PetOwnerRef::class.java, "owner")

    @Test
    fun `a scope records the statements of the block`(): Unit = runBlocking {
        val (pets, summary) = sqlScope("load") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        pets.shouldNotBeEmpty()
        summary.name shouldBe "load"
        summary.statementCount shouldBe 1
    }

    @Test
    fun `a scope survives a suspension that resumes on another thread`(): Unit = runBlocking {
        val threads = mutableSetOf<String>()
        val (_, summary) = sqlScope("across-threads") {
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
        summary.statementCount shouldBe 3
    }

    @Test
    fun `a scope counts the references the block resolved`(): Unit = runBlocking {
        val (_, summary) = sqlScope("n-plus-one") {
            val pets = orm.entity(PetOwnerRef::class).select().resultList
            withContext(Dispatchers.IO) {
                pets.mapNotNull { it.owner }.forEach { it.fetch() }
            }
        }
        (summary.statementCount - summary.fetchCount) shouldBe 1
        summary.fetchCount shouldBeGreaterThan 0
    }

    @Test
    fun `naming the reference leaves the scope at one statement`(): Unit = runBlocking {
        val (_, summary) = sqlScope("fetched") {
            val pets = orm.entity(PetOwnerRef::class).select().fetch(ownerPath).resultList
            pets.mapNotNull { it.owner }.forEach { it.fetch() }
        }
        summary.statementCount shouldBe 1
        summary.fetchCount shouldBe 0
    }

    @Test
    fun `a nested scope records alongside its parent`(): Unit = runBlocking {
        val (inner, outer) = sqlScope("outer") {
            orm.entity(PetOwnerRef::class).select().resultList
            sqlScope("inner") {
                orm.entity(PetOwnerRef::class).select().resultList
            }
        }
        // The inner scope sees only its own statement; the outer sees both.
        inner.summary.statementCount shouldBe 1
        outer.statementCount shouldBe 2
    }

    @Test
    fun `a scope does not record statements executed after it closed`(): Unit = runBlocking {
        val (_, summary) = sqlScope("scoped") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        orm.entity(PetOwnerRef::class).select().resultList
        summary.statementCount shouldBe 1
    }

    @Test
    fun `a concurrent coroutine does not observe another scope`(): Unit = runBlocking {
        val (_, summary) = sqlScope("mine") {
            // Work started outside this scope must not land in it.
            withContext(Dispatchers.IO) {
                orm.entity(PetOwnerRef::class).select().resultList
            }
        }
        summary.statementCount shouldBe 1
        val (_, other) = sqlScope("theirs") {
            orm.entity(PetOwnerRef::class).select().resultList
        }
        other.statementCount shouldBe 1
    }

    @Test
    fun `a scope opened in blocking code reaches an app-built coroutine through sqlScopeContext`(): Unit = runBlocking {
        // The shape a blocking service takes: a thread-bound scope, then a coroutine the app builds itself.
        val summaries = mutableListOf<st.orm.core.template.SqlScope.Summary>()
        st.orm.core.template.SqlScope.record<Any?>("blocking", {
            runBlocking(sqlScopeContext()) {
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
        val summaries = mutableListOf<st.orm.core.template.SqlScope.Summary>()
        st.orm.core.template.SqlScope.record<Any?>("blocking", {
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
        val summaries = mutableListOf<st.orm.core.template.SqlScope.Summary>()
        st.orm.core.template.SqlScope.record<Any?>("blocking", {
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
        val (_, summary) = sqlScope("fan-out") {
            coroutineScope {
                (1..executions).map {
                    async(Dispatchers.IO) { orm.entity(PetOwnerRef::class).select().resultList }
                }.awaitAll()
            }
        }
        summary.statementCount shouldBe executions
        summary.statements.size shouldBe executions
    }

    @Test
    fun `the recording limit holds under concurrency`(): Unit = runBlocking {
        val executions = 100
        val limit = 10
        val (_, summary) = sqlScope("fan-out", limit) {
            coroutineScope {
                (1..executions).map {
                    async(Dispatchers.IO) { orm.entity(PetOwnerRef::class).select().resultList }
                }.awaitAll()
            }
        }
        summary.statementCount shouldBe executions
        summary.statements.size shouldBe limit
        summary.truncated shouldBe true
    }

    @Test
    fun `a hydration shape renders for a data class entity`(): Unit = runBlocking {
        // The shape derives through the reflection provider, which recognizes Kotlin data classes; a JVM-record
        // check would leave these rows bare.
        sqlScopeHydrationShapes(HydrationShapes.FULL)
        try {
            val (_, summary) = sqlScope("shape") {
                orm.entity(PetOwnerRef::class).select().resultList
            }
            val rendered = summary.toString()
            rendered shouldContain "joins="
            rendered shouldContain "graph=PetOwnerRef"
        } finally {
            sqlScopeHydrationShapes(HydrationShapes.OFF)
        }
    }
}
