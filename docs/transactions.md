# Transactions

Storm works directly with the underlying database platform and integrates seamlessly with existing transaction management mechanisms.

---

## Kotlin

Storm for Kotlin provides a fully programmatic transaction solution that is **completely coroutine-friendly**. It supports **all isolation levels and propagation modes** found in traditional transaction management systems. You can freely switch coroutine dispatchers within a transaction—offload CPU-bound work to `Dispatchers.Default` or IO work to `Dispatchers.IO`—and still remain in the **same active transaction**.

### Suspend Transactions

Use `transaction` for coroutine code:

```kotlin
transaction {
    orm.deleteAll<Visit>()
    orm insert User(email = "alice@example.com", name = "Alice")
    // Commits automatically on success, rolls back on exception
}
```

Suspend transactions allow **context switching** without losing the active transaction:

```kotlin
transaction {
    val orders = orderRepository.findPendingOrders()

    withContext(Dispatchers.Default) {
        // CPU-bound work on another dispatcher
        heavyComputation(orders)
    }

    // Still in the same transaction
    orderRepository.update(order.copy(pending = false))
}
```

### Blocking Transactions

Use `transactionBlocking` for synchronous code:

```kotlin
transactionBlocking {
    orm.deleteAll<Visit>()
    orm insert User(email = "alice@example.com", name = "Alice")
    // Commits automatically on success, rolls back on exception
}
```

### Transaction Propagation

Propagation modes control how transactions interact when code calls another transactional method. This is essential for building composable services where each method can define its transactional requirements independently.

#### REQUIRED (Default)

Joins an existing transaction if one is active, otherwise creates a new one. This is the most common mode—it allows methods to participate in a larger transactional context while still working standalone.

When called without an existing transaction, a new transaction is started:

```
[BEGIN] → insert(user) → insert(order) → [COMMIT]
```

When called within an existing transaction, the operations join that transaction. All operations commit or rollback together:

```
[BEGIN]
   ↓
   insert(user)
   ↓
   ┌─ transaction(REQUIRED) ─┐
   │  insert(order)          │  ← joins outer transaction
   └─────────────────────────┘
   ↓
   insert(payment)
   ↓
[COMMIT]  ← all three inserts committed together
```

In this example, `orderService.createOrder()` participates in the same transaction. If either operation fails, both are rolled back:

```kotlin
transaction(propagation = REQUIRED) {
    userRepository.insert(user)
    orderService.createOrder(order)  // Joins this transaction
}
```

**Use cases:** The default for most operations. Use when operations should be atomic with their caller.

#### REQUIRES_NEW

Always creates a new, independent transaction. If an outer transaction exists, it is suspended until the inner transaction completes. The inner transaction commits or rolls back independently of the outer one.

The following diagram shows the outer transaction being suspended while the inner transaction runs. Notice that the inner transaction commits before the outer transaction fails—the audit log persists even though the outer transaction rolls back:

```
[BEGIN outer]
   ↓
   insert(user)
   ↓
   ~~~ outer suspended ~~~
   ↓
   [BEGIN inner]
      ↓
      insert(audit_log)
      ↓
   [COMMIT inner]  ← committed independently
   ↓
   ~~~ outer resumed ~~~
   ↓
   insert(order)
   ↓
[ROLLBACK outer]  ← audit_log survives!
```

This pattern is useful for audit logging. The audit record is preserved regardless of whether the business operation succeeds:

```kotlin
transaction {
    userRepository.insert(user)

    // Audit log commits even if outer transaction fails
    transaction(propagation = REQUIRES_NEW) {
        auditRepository.insert(AuditLog("User creation attempted"))
    }

    orderRepository.insert(order)  // If this fails, audit log is preserved
}
```

**Use cases:** Audit logging, error tracking, metrics recording, or any operation that must persist regardless of the outer transaction's outcome.

#### NESTED

Creates a savepoint within the current transaction. If the nested block fails, only changes since the savepoint are rolled back—the outer transaction can continue. Unlike `REQUIRES_NEW`, nested transactions share the same database connection and only fully commit when the outer transaction commits. If no transaction exists, behaves like `REQUIRED`.

When the nested block succeeds, the savepoint is released and all changes commit together with the outer transaction:

```
[BEGIN]
   ↓
   insert(order)
   ↓
   [SAVEPOINT]
      ↓
      insert(discount)
      ↓
   [RELEASE SAVEPOINT]
   ↓
   insert(payment)
   ↓
[COMMIT]  ← all three inserts committed
```

When the nested block fails or calls `setRollbackOnly()`, only changes within the savepoint are discarded. The outer transaction continues with its prior work intact:

```
[BEGIN]
   ↓
   insert(order)           ✓ kept
   ↓
   [SAVEPOINT]
      ↓
      insert(discount)     ✗ discarded
      insert(bonus)        ✗ discarded
      ↓
   [ROLLBACK TO SAVEPOINT]
   ↓
   insert(payment)         ✓ kept
   ↓
[COMMIT]  ← order + payment committed, discount + bonus discarded
```

This pattern is useful for optional operations that shouldn't abort the main flow. Here, the discount is applied if a valid promo code exists, but the order proceeds either way:

```kotlin
transaction {
    val order = orderRepository.insert(newOrder)

    transaction(propagation = NESTED) {
        val promo = promoRepository.findByCode(promoCode)
        if (promo == null || promo.expired) {
            setRollbackOnly()  // Only rolls back the promo attempt
            return@transaction
        }
        discountRepository.insert(Discount(order.id, promo.amount))
    }

    // Continues regardless of whether discount was applied
    paymentRepository.insert(Payment(order.id, calculateTotal(order)))
}
```

**Use cases:** Optional features that shouldn't abort the main flow, retry logic within a transaction, or "best effort" operations.

#### MANDATORY

Requires an active transaction—throws `PersistenceException` if none exists. Use this to enforce that a method is never called outside a transactional context. This is a defensive programming technique to catch integration errors early.

```
No transaction active:
   transaction(MANDATORY) → ✗ PersistenceException

Transaction active:
   [BEGIN]
      ↓
      transaction(MANDATORY) → ✓ joins outer
      ↓
   [COMMIT]
```

This pattern is useful for operations that must never run standalone. A fund transfer should always be part of a larger transactional context:

```kotlin
// In a repository or service that must run within a transaction
fun transferFunds(from: Account, to: Account, amount: BigDecimal) {
    transaction(propagation = MANDATORY) {
        // Guaranteed to be in a transaction—fails fast if not
        accountRepository.debit(from, amount)
        accountRepository.credit(to, amount)
    }
}
```

**Use cases:** Critical operations that must be part of a larger transaction, enforcing transactional boundaries in service layers.

#### SUPPORTS

Uses an existing transaction if available, otherwise runs without one. The code adapts to its calling context—transactional when called from a transaction, non-transactional otherwise.

```
No transaction active:
   transaction(SUPPORTS) → runs without transaction

Transaction active:
   [BEGIN]
      ↓
      transaction(SUPPORTS) → joins outer transaction
      ↓
   [COMMIT]
```

This pattern is useful for read operations that don't require transactional guarantees but benefit from them when available:

```kotlin
fun findUserById(id: Long): User? {
    return transaction(propagation = SUPPORTS) {
        // Benefits from transactional consistency if caller has a transaction,
        // but works fine standalone for simple lookups
        userRepository.findById(id)
    }
}
```

**Use cases:** Read-only operations, caching layers, or queries that benefit from transactional consistency when available but don't require it.

#### NOT_SUPPORTED

Suspends any active transaction and runs non-transactionally. The outer transaction resumes after the block completes. The suspended transaction's locks are retained, but this block won't see uncommitted changes from it.

```
[BEGIN outer]
   ↓
   insert(order)
   ↓
   ~~~ outer suspended ~~~
   ↓
   callExternalApi()  ← runs without transaction
   ↓
   ~~~ outer resumed ~~~
   ↓
   insert(confirmation)
   ↓
[COMMIT outer]
```

This pattern is useful for operations that shouldn't hold database resources or need to see committed data:

```kotlin
transaction {
    orderRepository.insert(order)

    // External API call shouldn't hold database locks
    transaction(propagation = NOT_SUPPORTED) {
        paymentGateway.processPayment(order.total)  // May take time
    }

    orderRepository.markAsPaid(order.id)
}
```

**Use cases:** External API calls, long-running computations, operations that must see committed data from other transactions, or reducing lock contention.

#### NEVER

Fails with `PersistenceException` if a transaction is active. Use this to enforce that code runs outside any transactional context. This is the opposite of `MANDATORY`—a defensive check to prevent accidental transactional execution.

```
No transaction active:
   transaction(NEVER) → ✓ runs without transaction

Transaction active:
   [BEGIN]
      ↓
      transaction(NEVER) → ✗ PersistenceException
```

This pattern is useful for operations that should never participate in a transaction, such as batch jobs that manage their own transaction boundaries:

```kotlin
fun runBatchJob() {
    transaction(propagation = NEVER) {
        // Ensures this is never accidentally called within another transaction
        // Each batch item will manage its own transaction
        items.forEach { item ->
            transaction {
                processItem(item)
            }
        }
    }
}
```

**Use cases:** Batch operations with custom transaction boundaries, operations that must see real-time committed data, or enforcing architectural boundaries.

#### Propagation Summary

| Mode | No Active Tx | Active Tx Exists |
|------|--------------|------------------|
| `REQUIRED` | Create new | Join existing |
| `REQUIRES_NEW` | Create new | Suspend outer, create new |
| `NESTED` | Create new | Create savepoint |
| `MANDATORY` | **Error** | Join existing |
| `SUPPORTS` | Run without tx | Join existing |
| `NOT_SUPPORTED` | Run without tx | Suspend outer, run without tx |
| `NEVER` | Run without tx | **Error** |

### Isolation Levels

Isolation levels control what data a transaction can see from other concurrent transactions. When multiple transactions run simultaneously, they can interfere with each other in various ways. Isolation levels let you choose the trade-off between data consistency and performance—higher isolation means fewer anomalies but typically lower throughput.

#### Concurrency Phenomena

Before diving into isolation levels, it's important to understand the three phenomena they prevent. Each represents a different way concurrent transactions can produce unexpected results:

| Phenomenon | Description |
|------------|-------------|
| **Dirty Read** | Reading uncommitted changes from another transaction that might roll back |
| **Non-Repeatable Read** | Reading the same row twice yields different values because another transaction modified it |
| **Phantom Read** | Re-executing a query returns new rows that another transaction inserted |

#### READ_UNCOMMITTED

The lowest isolation level. Transactions can see uncommitted changes from other transactions, which means you might read data that will never actually be committed (dirty reads). This offers the highest concurrency but the weakest consistency guarantees.

The following timeline shows two concurrent transactions. Transaction A reads a user that Transaction B inserted but hasn't committed yet. When Transaction B rolls back, the data Transaction A read effectively never existed:

```
Time    Transaction A                   Transaction B
─────────────────────────────────────────────────────────────────────
 t1     [BEGIN]
 t2                                     [BEGIN]
 t3                                     INSERT user ('Alice')
 t4     SELECT → sees 'Alice'           (not committed yet)
        ↑ dirty read!
 t5                                     [ROLLBACK]
 t6     SELECT → empty
        ↑ data disappeared!
 t7     [COMMIT]
```

This level is rarely used in practice, but can be useful when you need approximate results and maximum performance:

```kotlin
transaction(isolation = READ_UNCOMMITTED) {
    // Can see uncommitted changes - use with caution
    val count = userRepository.count()  // May include uncommitted rows
}
```

**Use cases:** Approximate counts for dashboards, monitoring queries, or any scenario where "close enough" is acceptable and performance matters more than accuracy.

> **Note:** At `READ_UNCOMMITTED` and `READ_COMMITTED` isolation levels, Storm returns fresh data from the database on every read rather than cached instances. This ensures repeated reads see the latest database state. Dirty checking remains available at all isolation levels—Storm stores observed state for detecting changes even when not returning cached instances. See [dirty checking](dirty-checking.md) for details.

#### READ_COMMITTED

Transactions only see data that has been committed. This prevents dirty reads—you'll never see data that might be rolled back. However, if you read the same row twice, you might get different values if another transaction modified and committed it in between (non-repeatable read).

In this timeline, Transaction A reads a balance of 1000. While it's still running, Transaction B updates and commits a new balance. When Transaction A reads again, it sees the new value:

```
Time    Transaction A                   Transaction B
─────────────────────────────────────────────────────────────────────
 t1     [BEGIN]
 t2     SELECT balance → 1000
 t3                                     [BEGIN]
 t4                                     UPDATE balance = 500
 t5                                     [COMMIT]
 t6     SELECT balance → 500
        ↑ non-repeatable read!
 t7     [COMMIT]
```

This is the default isolation level for most databases and applications. It provides a good balance between consistency and concurrency:

```kotlin
transaction(isolation = READ_COMMITTED) {
    val user = userRepository.findById(id)

    // Another transaction might modify the user here

    val sameUser = userRepository.findById(id)
    // sameUser might have different values than user
}
```

**Use cases:** The default choice for most applications. Suitable for operations where seeing the latest committed data is more important than having a consistent snapshot throughout the transaction.

> **Note:** Storm's [entity cache](entity-cache.md) behavior varies by isolation level. At `READ_COMMITTED`, fresh data is fetched on each read. At `REPEATABLE_READ` and above, cached instances are returned for consistent entity identity.

#### REPEATABLE_READ

Guarantees that if you read a row once, subsequent reads return the same data—even if other transactions modify and commit changes to that row. The transaction works with a consistent snapshot taken at the start. However, phantom reads may still occur: new rows inserted by other transactions can appear in range queries.

This timeline shows Transaction A getting consistent results for the same row, even though Transaction B modified it. The snapshot isolation ensures Transaction A sees the value as of when it started:

```
Time    Transaction A                   Transaction B
─────────────────────────────────────────────────────────────────────
 t1     [BEGIN]
 t2     SELECT balance → 1000
 t3                                     [BEGIN]
 t4                                     UPDATE balance = 500
 t5                                     [COMMIT]
 t6     SELECT balance → 1000
        ↑ same value (snapshot)
 t7     [COMMIT]
```

However, phantom reads can still occur with range queries. New rows that match the query criteria can appear between executions:

```
Time    Transaction A                   Transaction B
─────────────────────────────────────────────────────────────────────
 t1     [BEGIN]
 t2     SELECT pending orders → 3 rows
 t3                                     [BEGIN]
 t4                                     INSERT new pending order
 t5                                     [COMMIT]
 t6     SELECT pending orders → 4 rows
        ↑ phantom row!
 t7     [COMMIT]
```

This level is useful when you need consistent reads throughout a transaction, such as generating reports or performing calculations that must be internally consistent:

```kotlin
transaction(isolation = REPEATABLE_READ) {
    val user = userRepository.findById(id)

    // Even if another transaction modifies this user and commits,
    // we'll keep seeing the original values

    processUser(user)

    val sameUser = userRepository.findById(id)
    // Guaranteed: user == sameUser
}
```

**Use cases:** Financial calculations, generating reports, audit trails, or any scenario where you need a stable view of the data throughout the transaction.

#### SERIALIZABLE

The highest isolation level. Transactions execute as if they were run one after another (serially), even though they may actually run concurrently. This prevents all concurrency phenomena, including phantom reads. The database achieves this through locking or optimistic concurrency control, which may cause transactions to block or fail and retry.

In this timeline, Transaction B's insert is blocked (or will fail on commit) because Transaction A has read the range of pending orders. This ensures Transaction A sees a consistent set of rows throughout:

```
Time    Transaction A                   Transaction B
─────────────────────────────────────────────────────────────────────
 t1     [BEGIN]
 t2     SELECT pending orders → 3 rows
 t3                                     [BEGIN]
 t4                                     INSERT new pending order
                                        ↑ BLOCKED (or fails on commit)
 t5     SELECT pending orders → 3 rows
        ↑ no phantoms
 t6     [COMMIT]
 t7                                     ↑ now proceeds (or retries)
 t8                                     [COMMIT]
```

Use this level when correctness is critical and you cannot tolerate any anomalies. Be prepared for lower throughput and potential retry logic for failed transactions:

```kotlin
transaction(isolation = SERIALIZABLE) {
    // Check seat availability and book atomically
    val availableSeats = seatRepository.findAvailable(flightId)

    if (availableSeats.isNotEmpty()) {
        // No other transaction can insert/modify seats for this flight
        // until we commit—prevents double-booking
        seatRepository.book(availableSeats.first(), passengerId)
    }
}
```

**Use cases:** Booking systems, inventory management, financial transfers, or any operation where race conditions could cause serious problems like double-booking or overselling.

#### Isolation Level Summary

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance |
|-------|------------|---------------------|--------------|-------------|
| `READ_UNCOMMITTED` | Possible | Possible | Possible | Highest |
| `READ_COMMITTED` | Prevented | Possible | Possible | High |
| `REPEATABLE_READ` | Prevented | Prevented | Possible* | Medium |
| `SERIALIZABLE` | Prevented | Prevented | Prevented | Lowest |

*Some databases (e.g., PostgreSQL, MySQL/InnoDB) also prevent phantom reads at `REPEATABLE_READ` using snapshot isolation.

#### Choosing an Isolation Level

Start with `READ_COMMITTED` (often the database default) and only increase isolation when you have a specific consistency requirement. Here's a guide for common scenarios:

**Simple CRUD operations** — Use `READ_COMMITTED`. Seeing the latest committed data is usually what you want:

```kotlin
transaction(isolation = READ_COMMITTED) {
    userRepository.update(user)
}
```

**Reports and calculations** — Use `REPEATABLE_READ` when you need multiple queries to see a consistent snapshot. This ensures totals, counts, and details all reflect the same point in time:

```kotlin
transaction(isolation = REPEATABLE_READ) {
    val total = orderRepository.sumByUser(userId)
    val count = orderRepository.countByUser(userId)
    val average = total / count  // Safe: total and count are consistent
}
```

**Critical operations with race conditions** — Use `SERIALIZABLE` when concurrent transactions could cause problems like double-booking or overselling. The performance cost is worth the correctness guarantee:

```kotlin
transaction(isolation = SERIALIZABLE) {
    val inventory = inventoryRepository.findByProduct(productId)
    if (inventory.quantity >= requestedQuantity) {
        // Without SERIALIZABLE, two concurrent transactions could both
        // pass this check and oversell
        inventoryRepository.decrease(productId, requestedQuantity)
        orderRepository.create(order)
    }
}
```

### Transaction Timeout

```kotlin
transaction(timeoutSeconds = 30) {
    orm.deleteAll<Visit>()
    delay(35_000)  // Will cause timeout
}
```

### Read-Only Transactions

```kotlin
transaction(readOnly = true) {
    // Hints to the database that no modifications will occur
    val users = orm.findAll<User>()
}
```

### Manual Rollback

```kotlin
transaction {
    orm.deleteAll<Visit>()

    if (someCondition) {
        setRollbackOnly()  // Mark for rollback
    }
    // Transaction will roll back instead of commit
}
```

### Global Transaction Options

Set defaults for all transactions:

```kotlin
setGlobalTransactionOptions(
    propagation = REQUIRED,
    isolation = null,  // Use database default
    timeoutSeconds = null,
    readOnly = false
)
```

### Scoped Transaction Options

Override options for a specific scope:

```kotlin
withTransactionOptions(timeoutSeconds = 60) {
    transaction {
        // Uses 60 second timeout
        orm.deleteAll<Visit>()
    }
}

withTransactionOptionsBlocking(isolation = SERIALIZABLE) {
    transactionBlocking {
        // Uses SERIALIZABLE isolation
        orm.deleteAll<Visit>()
    }
}
```

### Spring-Managed Transactions

When using Spring, you can combine declarative `@Transactional` with programmatic transactions:

```kotlin
@Transactional
suspend fun processUsers() {
    // Spring manages outer transaction

    transaction {
        // Participates in Spring transaction
        orm.deleteAll<Visit>()
    }
}
```

Enable integration in your configuration:

```kotlin
@EnableTransactionIntegration
@Configuration
class ORMConfiguration(private val dataSource: DataSource) {
    @Bean
    fun ormTemplate() = ORMTemplate.of(dataSource)
}
```

---

## Java

Storm for Java integrates with existing transaction management mechanisms. Use Spring's transaction support or direct JDBC transaction control.

### Spring-Managed Transactions

Use Spring's `@Transactional` annotation:

```java
@Transactional
public void processUser(User user) {
    userRepository.insert(user);
    // Commits on success, rolls back on exception
}
```

### Programmatic Transactions with Spring

```java
@Autowired
private TransactionTemplate transactionTemplate;

public void processUsers() {
    transactionTemplate.execute(status -> {
        userRepository.insert(user);
        return null;
    });
}
```

### JDBC Transactions

For direct JDBC control:

```java
try (Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);

    var orm = ORMTemplate.of(connection);
    orm.entity(User.class).insert(user);

    connection.commit();
} catch (Exception e) {
    connection.rollback();
    throw e;
}
```

### JPA Entity Manager

Storm also works with JPA EntityManager:

```java
@PersistenceContext
private EntityManager entityManager;

@Transactional
public void processUser(User user) {
    var orm = ORMTemplate.of(entityManager);
    orm.entity(User.class).insert(user);
}
```

---

## Important Notes

### Concurrency

Launching concurrent work inside a transaction using `async`, `launch`, or other parallel coroutine builders is **not supported**. Database transactions are bound to the calling thread/coroutine. Use sequential operations or split work into separate transactions if parallelism is required.

### RollbackOnly Semantics

- In `NESTED` propagation: rolls back to the savepoint, preserving outer transaction's work
- In `REQUIRED` or `REQUIRES_NEW`: affects the entire transaction scope

### Context Switching (Kotlin)

Within any transactional scope, you can switch dispatchers (e.g., `withContext(Dispatchers.Default)`) and still access the **same active transaction**. This allows offloading CPU-bound work without breaking transactional context.
