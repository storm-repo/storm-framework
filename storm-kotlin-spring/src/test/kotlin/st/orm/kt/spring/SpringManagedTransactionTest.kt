package st.orm.kt.spring

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import st.orm.PersistenceException
import st.orm.kt.repository.deleteAll
import st.orm.kt.repository.exists
import st.orm.kt.spring.model.Visit
import st.orm.kt.template.ORMTemplate
import st.orm.kt.template.TransactionIsolation.*
import st.orm.kt.template.TransactionPropagation.*
import st.orm.kt.template.TransactionTimedOutException
import st.orm.kt.template.suspendTransaction
import st.orm.kt.template.transaction

@ContextConfiguration(classes = [IntegrationConfig::class])
@EnableTransactionIntegration
@SpringBootTest
@Sql("/data.sql")
open class SpringManagedTransactionTest(
    @Autowired val orm: ORMTemplate
) {
    /**
     * Single-layer scenarios (default REQUIRED propagation)
     */

    @Test
    fun `modification visible after required commit`(): Unit = runBlocking {
        transaction {
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `modification rolled back after required rollback`(): Unit = runBlocking {
        transaction {
            orm.deleteAll<Visit>()
            setRollbackOnly()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back after required rollback at start`(): Unit = runBlocking {
        transaction {
            setRollbackOnly()
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    /**
     * Two-layer scenarios: INNER uses REQUIRED (default), outer is default
     */

    @Test
    fun `modification visible for required inner commit and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRED) {
                orm.deleteAll<Visit>()
            }
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `modification rolled back for required inner commit and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRED) {
                orm.deleteAll<Visit>()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back for required inner rollback and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back for required inner rollback and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    /**
     * Two-layer scenarios: INNER uses REQUIRES_NEW, outer is default
     */

    @Test
    fun `modification visible for requires_new inner commit and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRES_NEW) {
                orm.deleteAll<Visit>()
            }
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `modification visible for requires_new inner commit and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRES_NEW) {
                orm.deleteAll<Visit>()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `modification rolled back for requires_new inner rollback and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRES_NEW) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            orm.exists<Visit>().shouldBeTrue()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back for requires_new inner rollback and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRES_NEW) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeTrue()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    /**
     * Two-layer scenarios: INNER uses NESTED (JDBC savepoint), outer is default
     */

    @Test
    fun `modification visible for nested inner commit and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(NESTED) {
                orm.deleteAll<Visit>()
            }
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `modification rolled back for nested inner commit and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(NESTED) {
                orm.deleteAll<Visit>()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeFalse()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back for nested inner rollback and outer commit`(): Unit = runBlocking {
        transaction {
            transaction(NESTED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            orm.exists<Visit>().shouldBeTrue()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `modification rolled back for nested inner rollback and outer rollback`(): Unit = runBlocking {
        transaction {
            transaction(NESTED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            setRollbackOnly()
            orm.exists<Visit>().shouldBeTrue()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    // Single-layer tests with various isolation levels

    @Test
    fun `required commit with READ_UNCOMMITTED isolation`(): Unit = runBlocking {
        transaction(isolation = READ_UNCOMMITTED) {
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `required rollback with READ_UNCOMMITTED isolation`(): Unit = runBlocking {
        transaction(isolation = READ_UNCOMMITTED) {
            orm.deleteAll<Visit>()
            setRollbackOnly()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `required commit with READ_COMMITTED isolation`(): Unit = runBlocking {
        transaction(isolation = READ_COMMITTED) {
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `required rollback with READ_COMMITTED isolation`(): Unit = runBlocking {
        transaction(isolation = READ_COMMITTED) {
            orm.deleteAll<Visit>()
            setRollbackOnly()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `required commit with REPEATABLE_READ isolation`(): Unit = runBlocking {
        transaction(isolation = REPEATABLE_READ) {
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `required rollback with REPEATABLE_READ isolation`(): Unit = runBlocking {
        transaction(isolation = REPEATABLE_READ) {
            orm.deleteAll<Visit>()
            setRollbackOnly()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `required commit with SERIALIZABLE isolation`(): Unit = runBlocking {
        transaction(isolation = SERIALIZABLE) {
            orm.deleteAll<Visit>()
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `required rollback with SERIALIZABLE isolation`(): Unit = runBlocking {
        transaction(isolation = SERIALIZABLE) {
            orm.deleteAll<Visit>()
            setRollbackOnly()
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    /**
     * Concurrent isolation tests
     */

    @Test
    fun `suspendTransaction not allowed with spring managed transactions`(): Unit = runBlocking {
        assertThrows<PersistenceException> {
            suspendTransaction {
                orm.deleteAll<Visit>()
            }
        }
    }

    @Transactional
    @Test
    open fun `programmatic transactions allowed with spring managed transactions`(): Unit = runBlocking {
        transaction {
            orm.deleteAll<Visit>()
        }
    }

    /**
     * Transaction timeouts.
     */

    @Test
    open fun `transaction times out and rolls back when execution exceeds timeout`(): Unit = runBlocking {
        assertThrows<TransactionTimedOutException> {
            transaction(timeoutSeconds = 1) {
                orm.deleteAll<Visit>()
                orm.query(
                    """
                        SELECT COUNT(*) 
                        FROM SYSTEM_RANGE(1, 1_000_000) AS A
                        CROSS JOIN SYSTEM_RANGE(1, 1_000_000) AS B
                    """.trimIndent()
                ).singleResult
            }
        }
        orm.exists<Visit>().shouldBeTrue()
    }
}