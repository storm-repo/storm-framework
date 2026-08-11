package st.orm.template

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import st.orm.PersistenceException
import java.sql.Connection
import javax.sql.DataSource

/**
 * Tests for [ORMTemplate.Builder.manualCommitConnections]: the declaration resolves against the built-in JDBC
 * connection handling and makes Storm-managed transactions work on a pool that hands out manual-commit
 * connections, with the misdeclared combinations failing fast in both directions.
 */
internal class ManualCommitConnectionsTest {

    private val h2 = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:manualCommitConnections;DB_CLOSE_DELAY=-1")
        user = "sa"
    }

    @BeforeEach
    fun resetTable() {
        h2.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS manual_city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))")
                statement.execute("DELETE FROM manual_city")
            }
        }
    }

    /**
     * A pool that hands out connections with auto-commit disabled.
     */
    private class ManualCommitDataSource(private val target: DataSource) : DataSource by target {
        override fun getConnection(): Connection = target.connection.apply { autoCommit = false }
    }

    private fun countRows(): Int = h2.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM manual_city").use { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }
    }

    @Test
    fun `declared template commits transactional work on a manual-commit pool`() {
        val orm = ORMTemplate.builder(ManualCommitDataSource(h2)).manualCommitConnections().build()
        transactionBlocking {
            orm.query("INSERT INTO manual_city (name) VALUES ('Amsterdam')").executeUpdate()
        }
        countRows() shouldBe 1
    }

    @Test
    fun `declared template rolls back transactional work on a manual-commit pool`() {
        val orm = ORMTemplate.builder(ManualCommitDataSource(h2)).manualCommitConnections().build()
        assertThrows<IllegalStateException> {
            transactionBlocking {
                orm.query("INSERT INTO manual_city (name) VALUES ('Utrecht')").executeUpdate()
                error("fail the block")
            }
        }
        countRows() shouldBe 0
    }

    @Test
    fun `declared template fails fast on an auto-commit pool`() {
        val orm = ORMTemplate.builder(h2).manualCommitConnections().build()
        val thrown = assertThrows<PersistenceException> {
            transactionBlocking {
                orm.query("SELECT id FROM manual_city").resultList
            }
        }
        thrown.message shouldContain "declares manual-commit connections"
    }

    @Test
    fun `undeclared template fails fast on a manual-commit pool`() {
        val orm = ORMTemplate.builder(ManualCommitDataSource(h2)).build()
        val thrown = assertThrows<PersistenceException> {
            transactionBlocking {
                orm.query("SELECT id FROM manual_city").resultList
            }
        }
        thrown.message shouldContain "manualCommitConnections()"
    }
}
