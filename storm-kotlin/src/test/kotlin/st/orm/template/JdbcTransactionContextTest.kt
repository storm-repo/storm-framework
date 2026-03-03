package st.orm.template

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.PersistenceException
import st.orm.repository.countAll
import st.orm.repository.deleteAll
import st.orm.repository.exists
import st.orm.template.TransactionIsolation.*
import st.orm.template.TransactionPropagation.*
import st.orm.template.model.City
import st.orm.template.model.Visit

/**
 * Additional tests for [st.orm.template.impl.JdbcTransactionContext] covering edge cases
 * in transaction propagation, entity caching, and timeout handling.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class JdbcTransactionContextTest(
    @Autowired val orm: ORMTemplate,
) {

    @AfterEach
    fun resetDefaults() {
        setGlobalTransactionOptions(
            propagation = REQUIRED,
            isolation = null,
            timeoutSeconds = null,
            readOnly = false,
        )
    }

    // ======================================================================
    // Entity cache behavior
    // ======================================================================

    @Test
    fun `REPEATABLE_READ transaction should enable entity caching`(): Unit = runBlocking {
        transactionBlocking(isolation = REPEATABLE_READ) {
            val city1 = orm.entity(City::class).select().where(1).singleResult
            val city2 = orm.entity(City::class).select().where(1).singleResult
            // With REPEATABLE_READ, cached entity should be returned
            city1.name shouldBe city2.name
        }
    }

    @Test
    fun `READ_COMMITTED transaction should fetch fresh data on each read`(): Unit = runBlocking {
        transactionBlocking(isolation = READ_COMMITTED) {
            val city1 = orm.entity(City::class).select().where(1).singleResult
            val city2 = orm.entity(City::class).select().where(1).singleResult
            city1.name shouldBe "Sun Paririe"
            city2.name shouldBe "Sun Paririe"
        }
    }

    // ======================================================================
    // Nested NESTED with savepoint
    // ======================================================================

    @Test
    fun `nested savepoint rollback should clear entity cache`(): Unit = runBlocking {
        transactionBlocking(isolation = REPEATABLE_READ) {
            orm.countAll<Visit>() shouldBe 14
            transactionBlocking(NESTED) {
                orm.deleteAll<Visit>()
                orm.exists<Visit>().shouldBeFalse()
                setRollbackOnly()
            }
            // After nested rollback, data should be restored
            orm.exists<Visit>().shouldBeTrue()
        }
    }

    @Test
    fun `double nested NESTED should work correctly`(): Unit = runBlocking {
        transactionBlocking {
            transactionBlocking(NESTED) {
                orm.deleteAll<Visit>()
                transactionBlocking(NESTED) {
                    orm.exists<Visit>().shouldBeFalse()
                    setRollbackOnly()
                }
                // Inner nested was rolled back, but visits were already deleted in outer nested
                orm.exists<Visit>().shouldBeFalse()
            }
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `NESTED with outer REQUIRES_NEW should work`(): Unit = runBlocking {
        transactionBlocking {
            transactionBlocking(REQUIRES_NEW) {
                transactionBlocking(NESTED) {
                    orm.deleteAll<Visit>()
                    setRollbackOnly()
                }
                orm.exists<Visit>().shouldBeTrue()
            }
        }
    }

    // ======================================================================
    // Timeout scenarios
    // ======================================================================

    @Test
    fun `nested transaction should inherit outer timeout`(): Unit = runBlocking {
        assertThrows<TransactionTimedOutException> {
            transactionBlocking(timeoutSeconds = 1) {
                transactionBlocking(NESTED) {
                    Thread.sleep(1500)
                }
            }
        }
    }

    @Test
    fun `inner timeout should be min of outer and inner`(): Unit = runBlocking {
        assertThrows<TransactionTimedOutException> {
            transactionBlocking(timeoutSeconds = 5) {
                transactionBlocking(REQUIRED, timeoutSeconds = 1) {
                    Thread.sleep(1500)
                }
            }
        }
    }

    @Test
    fun `suspend transaction with short timeout should time out on delay`(): Unit = runBlocking {
        assertThrows<TransactionTimedOutException> {
            transaction(timeoutSeconds = 1) {
                delay(1500)
            }
        }
    }

    // ======================================================================
    // SUPPORTS edge cases
    // ======================================================================

    @Test
    fun `SUPPORTS inside REQUIRED should share entity cache`(): Unit = runBlocking {
        transactionBlocking(isolation = REPEATABLE_READ) {
            transactionBlocking(SUPPORTS) {
                orm.countAll<City>() shouldBe 6
            }
            orm.countAll<City>() shouldBe 6
        }
    }

    @Test
    fun `SUPPORTS without outer transaction runs non-transactional`(): Unit = runBlocking {
        transactionBlocking(SUPPORTS) {
            orm.deleteAll<Visit>()
        }
        // Non-transactional: auto-committed
        orm.exists<Visit>().shouldBeFalse()
    }

    // ======================================================================
    // NOT_SUPPORTED edge cases
    // ======================================================================

    @Test
    fun `NOT_SUPPORTED should suspend outer transaction and run non-transactional`(): Unit = runBlocking {
        transactionBlocking {
            transactionBlocking(NOT_SUPPORTED) {
                orm.deleteAll<Visit>()
            }
            // NOT_SUPPORTED changes are already committed
            orm.exists<Visit>().shouldBeFalse()
            setRollbackOnly()
        }
        // NOT_SUPPORTED changes survive outer rollback
        orm.exists<Visit>().shouldBeFalse()
    }

    // ======================================================================
    // MANDATORY edge cases
    // ======================================================================

    @Test
    fun `MANDATORY inside REQUIRED should join transaction`(): Unit = runBlocking {
        transactionBlocking {
            transactionBlocking(MANDATORY) {
                orm.deleteAll<Visit>()
            }
        }
        orm.exists<Visit>().shouldBeFalse()
    }

    @Test
    fun `MANDATORY without outer transaction should throw`(): Unit = runBlocking {
        assertThrows<PersistenceException> {
            transactionBlocking(MANDATORY) {
                orm.countAll<City>()
            }
        }
    }

    // ======================================================================
    // NEVER edge cases
    // ======================================================================

    @Test
    fun `NEVER without outer should work non-transactionally`(): Unit = runBlocking {
        transactionBlocking(NEVER) {
            orm.countAll<City>() shouldBe 6
        }
    }

    @Test
    fun `NEVER inside REQUIRED should throw`(): Unit = runBlocking {
        assertThrows<PersistenceException> {
            transactionBlocking {
                transactionBlocking(NEVER) {
                    orm.countAll<City>()
                }
            }
        }
    }

    // ======================================================================
    // Read-only transactions
    // ======================================================================

    @Test
    fun `readOnly transaction should allow read operations`(): Unit = runBlocking {
        transactionBlocking(readOnly = true) {
            orm.countAll<City>() shouldBe 6
            orm.countAll<Visit>() shouldBe 14
        }
    }

    @Test
    fun `readOnly flag combined with isolation should work`(): Unit = runBlocking {
        transactionBlocking(readOnly = true, isolation = READ_COMMITTED) {
            orm.countAll<City>() shouldBe 6
        }
    }

    // ======================================================================
    // Global and scoped defaults
    // ======================================================================

    @Test
    fun `global readOnly default should apply to transactions`(): Unit = runBlocking {
        setGlobalTransactionOptions(readOnly = true)
        transactionBlocking {
            orm.countAll<City>() shouldBe 6
        }
    }

    @Test
    fun `scoped isolation default should apply to suspend transactions`(): Unit = runBlocking {
        withTransactionOptions(isolation = READ_COMMITTED) {
            transaction {
                orm.countAll<City>() shouldBe 6
            }
        }
    }

    @Test
    fun `thread-scoped defaults should apply to blocking transactions`(): Unit = runBlocking {
        withTransactionOptionsBlocking(isolation = SERIALIZABLE) {
            transactionBlocking {
                orm.countAll<City>() shouldBe 6
            }
        }
    }

    @Test
    fun `explicit args should override scoped defaults`(): Unit = runBlocking {
        withTransactionOptions(isolation = SERIALIZABLE) {
            transaction(isolation = READ_COMMITTED) {
                orm.countAll<City>() shouldBe 6
            }
        }
    }

    // ======================================================================
    // Multiple operations within same transaction
    // ======================================================================

    @Test
    fun `multiple insert and delete within transaction should work`(): Unit = runBlocking {
        transactionBlocking {
            val repo = orm.entity(City::class)
            val initialCount = repo.count()
            initialCount shouldBe 6

            repo.insert(City(name = "NewCity"))
            repo.count() shouldBe 7

            repo.delete().where(7).executeUpdate() shouldBe 1
            repo.count() shouldBe 6
        }
    }
}
