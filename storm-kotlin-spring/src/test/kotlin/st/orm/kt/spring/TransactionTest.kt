package st.orm.kt.spring

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import st.orm.PersistenceException
import st.orm.kt.repository.countAll
import st.orm.kt.repository.deleteAll
import st.orm.kt.repository.exists
import st.orm.kt.spring.model.Visit
import st.orm.kt.template.*
import st.orm.kt.template.TransactionIsolation.*
import st.orm.kt.template.TransactionPropagation.*
import kotlin.test.assertFalse

@ContextConfiguration(classes = [IntegrationConfig::class])
@SpringBootTest
@Sql("/data.sql")
open class TransactionTest(
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
    fun `dirty read allowed with READ_UNCOMMITTED isolation`(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val sawDirty = CompletableDeferred<Boolean>()
        // Transaction 1: delete then mark rollback-only after a delay
        launch {
            suspendTransaction {
                orm.deleteAll<Visit>()
                started.complete(Unit)
                delay(500)
                setRollbackOnly()
            }
        }
        // Transaction 2: read while Transaction 1 is still active
        launch {
            started.await()
            suspendTransaction(isolation = READ_UNCOMMITTED) {
                sawDirty.complete(orm.exists<Visit>())
            }
        }
        sawDirty.await().shouldBeFalse()
    }

    @Test
    fun `non repeatable read allowed with READ_COMMITTED isolation`(): Unit = runBlocking {
        val first = CompletableDeferred<Long>()
        val latch1 = CompletableDeferred<Unit>()
        val latch2 = CompletableDeferred<Unit>()
        // Transaction 1: read, wait, read again.
        launch {
            suspendTransaction(isolation = READ_COMMITTED) {
                first.complete(orm.countAll<Visit>())
                latch1.complete(Unit)
                latch2.await()
                val second = orm.countAll<Visit>()
                first.await().shouldNotBe(second)
            }
        }
        // Transaction 2: wait, then delete all and commit.
        launch {
            latch1.await()
            suspendTransaction {
                orm.deleteAll<Visit>()
            }
            latch2.complete(Unit)
        }
    }

    @Test
    fun `dirty read with READ_COMMITTED isolation`(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val sawDirty = CompletableDeferred<Boolean>()
        // Transaction 1: delete then mark rollback-only after a delay.
        launch {
            suspendTransaction {
                orm.deleteAll<Visit>()
                started.complete(Unit)
                delay(500)
            }
        }
        // Transaction 2: read while Transaction 1 is still active.
        launch {
            started.await()
            suspendTransaction(isolation = READ_COMMITTED) {
                sawDirty.complete(orm.exists<Visit>())
            }
        }
        sawDirty.await().shouldBeTrue()
    }

    @Test
    fun `dirty read deleteAll with READ_UNCOMMITTED`(): Unit = runBlocking {
        val started  = CompletableDeferred<Unit>()
        val sawEmpty = CompletableDeferred<Boolean>()
        // Transaction1: deleteAll but delay before commit.
        launch {
            suspendTransaction {
                orm.deleteAll<Visit>()
                started.complete(Unit)
                delay(500)
            }
        }
        // Transaction2: under READ_UNCOMMITTED we should see the uncommitted delete.
        launch {
            started.await()
            suspendTransaction(isolation = READ_UNCOMMITTED) {
                sawEmpty.complete(!orm.exists<Visit>())
            }
        }
        sawEmpty.await().shouldBeTrue()
    }

    @Test
    fun `no dirty read deleteAll with READ_COMMITTED`(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val sawFull = CompletableDeferred<Boolean>()
        // Transaction1: deleteAll but delay before commit.
        launch {
            suspendTransaction {
                orm.deleteAll<Visit>()
                started.complete(Unit)
                delay(500)
            }
        }
        // Transaction2: under READ_COMMITTED we should NOT see the uncommitted delete.
        launch {
            started.await()
            suspendTransaction(isolation = READ_COMMITTED) {
                sawFull.complete(orm.exists<Visit>())
            }
        }
        sawFull.await().shouldBeTrue()
    }

    @Test
    fun `non-repeatable deleteAll with READ_COMMITTED`(): Unit = runBlocking {
        val deleted      = CompletableDeferred<Unit>()
        val firstExists  = CompletableDeferred<Boolean>()
        val secondExists = CompletableDeferred<Boolean>()
        // Transaction: after a short delay, deleteAll and commit.
        launch {
            delay(500)
            suspendTransaction {
                orm.deleteAll<Visit>()
            }
            deleted.complete(Unit)
        }
        // Transaction2: under READ_COMMITTED we expect a non-repeatable read of existence.
        launch {
            suspendTransaction(isolation = READ_COMMITTED) {
                firstExists.complete(orm.exists<Visit>())   // should be true
                deleted.await()
                secondExists.complete(orm.exists<Visit>())  // should be false
            }
        }
        firstExists.await().shouldBeTrue()
        secondExists.await().shouldBeFalse()
    }

    @Test
    fun `no non-repeatable deleteAll with REPEATABLE_READ`(): Unit = runBlocking {
        val deleted      = CompletableDeferred<Unit>()
        val firstExists  = CompletableDeferred<Boolean>()
        val secondExists = CompletableDeferred<Boolean>()
        // Transaction1: after a short delay, deleteAll and commit.
        launch {
            delay(500)
            suspendTransaction {
                orm.deleteAll<Visit>()
            }
            deleted.complete(Unit)
        }
        // Transaction2: under REPEATABLE_READ we expect repeatable existence
        launch {
            suspendTransaction(isolation = REPEATABLE_READ) {
                firstExists.complete(orm.exists<Visit>())   // should be true
                deleted.await()
                secondExists.complete(orm.exists<Visit>())  // should still be true
            }
        }
        firstExists.await().shouldBeTrue()
        secondExists.await().shouldBeTrue()
    }

    @Transactional
    @Test
    open fun `programmatic transactions not allowed with spring managed transactions`(): Unit = runBlocking {
        assertThrows<PersistenceException> {
            transaction {
                orm.deleteAll<Visit>()
            }
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
                Thread.sleep(1500)
            }
        }
        // Timeout should cause rollback: data still exists.
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    open fun `suspendTransaction times out and rolls back when execution exceeds delay timeout`(): Unit = runBlocking {
        assertThrows<TransactionTimedOutException> {
            suspendTransaction(timeoutSeconds = 1) {
                orm.deleteAll<Visit>()
                delay(1500)
            }
        }
        // Timeout should cause rollback: data still exists.
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    open fun `transaction times out and rolls back when execution exceeds query timeout`(): Unit = runBlocking {
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

    /**
     * Context switching scenarios.
     */

    @Test
    fun `context switch sees uncommitted data`(): Unit = runBlocking {
        val afterSwitch = CompletableDeferred<Boolean>()
        suspendTransaction {
            orm.deleteAll<Visit>()
            withContext(Dispatchers.IO) {
                afterSwitch.complete(orm.exists<Visit>())
            }
            delay(500) // Ensure the launch happens after the deleteAll
        }
        afterSwitch.await().shouldBeFalse()
    }

    @Test
    fun `context switch with transaction sees uncommitted data`(): Unit = runBlocking {
        val afterSwitch = CompletableDeferred<Boolean>()
        suspendTransaction {
            orm.deleteAll<Visit>()
            withContext(Dispatchers.IO) {
                transaction {
                    afterSwitch.complete(orm.exists<Visit>())
                }
            }
            delay(500) // Ensure the launch happens after the deleteAll
        }
        afterSwitch.await().shouldBeFalse()
    }

    @Test
    fun `context switch with suspend transaction sees uncommitted data`(): Unit = runBlocking {
        val afterSwitch = CompletableDeferred<Boolean>()
        suspendTransaction {
            orm.deleteAll<Visit>()
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    afterSwitch.complete(orm.exists<Visit>())
                }
            }
            delay(500) // Ensure the launch happens after the deleteAll
        }
        afterSwitch.await().shouldBeFalse()
    }

    /**
     * Fail-fast concurrency scenarios.
     */

    @Test
    fun `concurrent DB access in same transaction throws exception`(): Unit = runBlocking {
        val e = assertThrows<PersistenceException> {
            suspendTransaction(timeoutSeconds = 1) {
                coroutineScope {
                    // Two concurrent operations.
                    val job1 = launch(Dispatchers.IO) {
                        orm.query(
                            """
                                SELECT COUNT(*) 
                                FROM SYSTEM_RANGE(1, 1_000_000) AS A
                                CROSS JOIN SYSTEM_RANGE(1, 1_000_000) AS B
                            """.trimIndent()
                        ).singleResult
                    }
                    val job2 = launch(Dispatchers.IO) {
                        orm.query(
                            """
                            SELECT COUNT(*) 
                            FROM SYSTEM_RANGE(1, 1_000_000) AS A
                            CROSS JOIN SYSTEM_RANGE(1, 1_000_000) AS B
                        """.trimIndent()
                        ).singleResult
                    }
                    joinAll(job1, job2)
                }
            }
        }
        assertFalse(e.cause is TransactionTimedOutException)
    }


    /**
     * Unexpected rollback scenarios.
     */

    @Test
    fun `outer commit fails with UnexpectedRollback when inner REQUIRED marks rollback-only`(): Unit = runBlocking {
        assertThrows<UnexpectedRollbackException> {
            transaction {
                orm.deleteAll<Visit>()
                // Inner REQUIRED participates in the same physical tx and marks rollback-only.
                transaction(REQUIRED) {
                    setRollbackOnly()
                }
                // Outer tries to commit -> should throw UnexpectedRollbackException.
            }
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `nested rollback does not throw on outer commit`(): Unit = runBlocking {
        transaction {
            transaction(NESTED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()
            }
            // Outer should still commit fine; data remains.
        }
        orm.exists<Visit>().shouldBeTrue()
    }

    @Test
    fun `outer rollback does not throw when inner REQUIRED marked rollback-only too`(): Unit = runBlocking {
        transaction {
            transaction(REQUIRED) {
                orm.deleteAll<Visit>()
                setRollbackOnly()               // Inner marks global tx rollback-only.
            }
            orm.exists<Visit>().shouldBeFalse() // Mid-scope still sees uncommitted change.
            setRollbackOnly()                   // Outer makes rollback explicit/expected.
            // exit -> should roll back WITHOUT throwing UnexpectedRollbackException.
        }
        orm.exists<Visit>().shouldBeTrue()
    }
}