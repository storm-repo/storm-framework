package st.orm.template

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import st.orm.Data
import st.orm.Entity
import st.orm.NoResultException
import st.orm.NonUniqueResultException
import st.orm.PK
import st.orm.PersistenceException
import st.orm.Ref
import st.orm.template.model.City
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * Verifies the contract the [Query] interface provides on top of its abstract stream members: the single, optional,
 * count, list and ref-list operations derive from the streams, close them, and report a missing, ambiguous or SQL
 * NULL single row through the typed exceptions. The module's own [Query] implementation overrides these members, so
 * an implementation that supplies streams only, as this test's does, is what exercises the defaults.
 */
internal class QueryDefaultsTest {

    private data class Town(@PK val id: Int, val name: String) : Entity<Int>

    /**
     * A [Query] over in-memory rows: the first column doubles as the typed result and as the ref key. Every stream
     * records its close, so the test can hold the defaults to closing what they open.
     */
    private class RowsQuery(private val rows: List<Array<Any?>>) : Query {
        val closedStreams = mutableListOf<String>()

        override fun prepare(): PreparedQuery = throw UnsupportedOperationException()

        override fun unsafe(): Query = this

        @Suppress("UNCHECKED_CAST")
        override val resultStream: Stream<Array<Any>>
            get() = (rows.stream() as Stream<Array<Any>>).onClose { closedStreams += "rows" }

        override fun <T : Any> getResultStream(type: KClass<T>): Stream<T> = rows.stream().map { row -> row[0]?.let { type.java.cast(it) } }.onClose { closedStreams += "typed" }

        override fun <T : Data> getRefStream(type: KClass<T>, pkType: KClass<*>): Stream<Ref<T>> = rows.stream().map { row -> Ref.of(type.java, row[0]!!) }.onClose { closedStreams += "refs" }

        override val versionAware: Boolean = false

        override fun executeUpdate(): Int = 0

        override fun executeBatch(): IntArray = IntArray(0)
    }

    private fun rows(vararg cells: Any?): List<Array<Any?>> = cells.map { arrayOf(it) }

    @Test
    fun `singleResult returns the only row and closes the stream`() {
        val query = RowsQuery(rows("Madison"))
        query.singleResult[0] shouldBe "Madison"
        query.getSingleResult(String::class) shouldBe "Madison"
        query.singleResult<String>() shouldBe "Madison"
        query.closedStreams shouldBe listOf("rows", "typed", "typed")
    }

    @Test
    fun `singleResult rejects no row and more than one row`() {
        assertThrows<NoResultException> { RowsQuery(emptyList()).singleResult }
        assertThrows<NoResultException> { RowsQuery(emptyList()).getSingleResult(String::class) }
        assertThrows<NonUniqueResultException> { RowsQuery(rows("Madison", "Monona")).singleResult }
        assertThrows<NonUniqueResultException> { RowsQuery(rows("Madison", "Monona")).getSingleResult(String::class) }
    }

    @Test
    fun `singleResult reports a SQL NULL value with the COALESCE hint`() {
        val exception = assertThrows<PersistenceException> { RowsQuery(rows(null)).getSingleResult(String::class) }
        exception.message!! shouldContain "COALESCE"
    }

    @Test
    fun `optionalResult returns the only row or null and closes the stream`() {
        RowsQuery(rows("Madison")).optionalResult!![0] shouldBe "Madison"
        RowsQuery(rows("Madison")).getOptionalResult(String::class) shouldBe "Madison"
        RowsQuery(rows("Madison")).optionalResult<String>() shouldBe "Madison"
        val empty = RowsQuery(emptyList())
        empty.optionalResult shouldBe null
        empty.getOptionalResult(String::class) shouldBe null
        empty.closedStreams shouldBe listOf("rows", "typed")
    }

    @Test
    fun `optionalResult rejects more than one row and a SQL NULL value`() {
        assertThrows<NonUniqueResultException> { RowsQuery(rows("Madison", "Monona")).optionalResult }
        assertThrows<NonUniqueResultException> { RowsQuery(rows("Madison", "Monona")).getOptionalResult(String::class) }
        val exception = assertThrows<PersistenceException> { RowsQuery(rows(null)).getOptionalResult(String::class) }
        exception.message!! shouldContain "COALESCE"
    }

    @Test
    fun `resultCount counts the rows and closes the stream`() {
        val query = RowsQuery(rows("Madison", "Monona", "Windsor"))
        query.resultCount shouldBe 3L
        query.closedStreams shouldBe listOf("rows")
    }

    @Test
    fun `resultList collects the rows in both forms and closes the streams`() {
        val query = RowsQuery(rows("Madison", "Monona"))
        query.resultList.map { it[0] } shouldBe listOf("Madison", "Monona")
        query.getResultList(String::class) shouldBe listOf("Madison", "Monona")
        query.resultList<String>() shouldBe listOf("Madison", "Monona")
        query.closedStreams shouldBe listOf("rows", "typed", "typed")
    }

    @Test
    fun `refList collects the refs and closes the stream`() {
        val query = RowsQuery(rows(1, 2))
        val refs = query.getRefList(Town::class, Int::class)
        refs shouldBe listOf(Ref.of(Town::class.java, 1), Ref.of(Town::class.java, 2))
        refs.all { !it.isLoaded }.shouldBeTrue()
        query.closedStreams shouldBe listOf("refs")
    }

    @Test
    fun `typed streams and flows derive from the abstract stream member`(): Unit = runBlocking {
        val query = RowsQuery(rows(City(id = 1, name = "Sun Prairie")))
        query.resultStream<City>().use { stream -> stream.toList().single().id shouldBe 1 }
        query.resultFlow<City>().toList().single().name shouldBe "Sun Prairie"
        query.getResultFlow(City::class).toList().single().name shouldBe "Sun Prairie"
        query.resultFlow.toList().single()[0] shouldBe City(id = 1, name = "Sun Prairie")
        // The ref flow maps the key column: a query over towns 1 and 2 yields their refs.
        RowsQuery(rows(1, 2)).getRefFlow(Town::class, Int::class).toList() shouldBe
            listOf(Ref.of(Town::class.java, 1), Ref.of(Town::class.java, 2))
    }
}
