import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Transactions

Transaction management is fundamental to database programming. Storm takes a practical approach: rather than inventing new abstractions, it provides first-class support for standard transaction semantics while integrating seamlessly with your existing infrastructure.

Storm works directly with JDBC transactions and supports both programmatic and declarative transaction management. For Kotlin, Storm provides a coroutine-friendly API inspired by Exposed. For Java, Storm integrates with Spring's transaction management or works directly with JDBC connections.

---

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Storm for Kotlin provides a fully programmatic transaction solution (following the style popularized by [Exposed](https://github.com/JetBrains/Exposed)) that is **completely coroutine-friendly**. It supports **all isolation levels and propagation modes** found in traditional transaction management systems. You can freely switch coroutine dispatchers within a transaction (offload CPU-bound work to `Dispatchers.Default` or IO work to `Dispatchers.IO`) and still remain in the **same active transaction**.

While Storm's `transaction { }` blocks look similar to Exposed's, Storm goes further by supporting seven Spring-style propagation modes (`REQUIRED`, `REQUIRES_NEW`, `NESTED`, `MANDATORY`, `SUPPORTS`, `NOT_SUPPORTED`, `NEVER`). Exposed's native transaction API only supports basic nesting (shared transaction) and savepoint-based nesting (`useNestedTransactions = true`), without the ability to suspend an outer transaction, enforce transactional context, or run non-transactionally. See [Storm vs Exposed](comparison.md#storm-vs-exposed) for a detailed comparison.

The API is designed around Kotlin's type system and coroutine model. Import the transaction functions from `st.orm.template` and the option enums from `st.orm` (shared with the Java API):

```kotlin
import st.orm.template.transaction
import st.orm.template.transactionBlocking
import st.orm.TransactionPropagation.*
import st.orm.TransactionIsolation.*
```

### Suspend Transactions

Use `transaction` for coroutine code:

```kotlin
transaction {
    orm.removeAll<Visit>()
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
    orm.removeAll<Visit>()
    orm insert User(email = "alice@example.com", name = "Alice")
    // Commits automatically on success, rolls back on exception
}
```

### Transaction Propagation

Propagation modes are one of the most powerful features of enterprise transaction management, yet they're often misunderstood. They control how transactions interact when code calls another transactional method. This is essential for building composable services where each method can define its transactional requirements independently.

Storm supports seven Spring-style propagation modes. Understanding when to use each mode helps you build robust, maintainable applications where components work correctly both standalone and when composed together.

#### REQUIRED (Default)

Joins an existing transaction if one is active, otherwise creates a new one. This is the most common mode: it allows methods to participate in a larger transactional context while still working standalone.

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

The following diagram shows the outer transaction being suspended while the inner transaction runs. Notice that the inner transaction commits before the outer transaction fails, so the audit log persists even though the outer transaction rolls back:

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

Creates a savepoint within the current transaction. If the nested block fails, only changes since the savepoint are rolled back, and the outer transaction can continue. Unlike `REQUIRES_NEW`, nested transactions share the same database connection and only fully commit when the outer transaction commits. If no transaction exists, behaves like `REQUIRED`.

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
        val promo = promoRepository.findByCode(promoCode) ?: return@transaction
        discountRepository.insert(Discount(order.id, promo.amount))

        if (promo.expired) {
            setRollbackOnly()  // Rolls back the discount insert
        }
    }

    // Continues regardless of whether discount was applied
    paymentRepository.insert(Payment(order.id, calculateTotal(order)))
}
```

**Use cases:** Optional features that shouldn't abort the main flow, retry logic within a transaction, or "best effort" operations.

#### MANDATORY

Requires an active transaction; throws `PersistenceException` if none exists. Use this to enforce that a method is never called outside a transactional context. This is a defensive programming technique to catch integration errors early.

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
        // Guaranteed to be in a transaction. Fails fast if not.
        accountRepository.debit(from, amount)
        accountRepository.credit(to, amount)
    }
}
```

**Use cases:** Critical operations that must be part of a larger transaction, enforcing transactional boundaries in service layers.

#### SUPPORTS

Uses an existing transaction if available, otherwise runs without one. The code adapts to its calling context: transactional when called from a transaction, non-transactional otherwise.

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

Fails with `PersistenceException` if a transaction is active. Use this to enforce that code runs outside any transactional context. This is the opposite of `MANDATORY`, serving as a defensive check to prevent accidental transactional execution.

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

Isolation levels are the database's answer to concurrency. When multiple transactions run simultaneously, they can interfere with each other in various ways. The SQL standard defines four isolation levels, each preventing different types of concurrency anomalies.

Storm exposes all four standard isolation levels through its API, giving you full control over the consistency-performance trade-off. Most applications work fine with the database's default isolation level (typically `READ_COMMITTED`), but understanding when to use higher levels is crucial for building correct applications.

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

> **Note:** At `READ_UNCOMMITTED` and `READ_COMMITTED` isolation levels, Storm returns fresh data from the database on every read rather than cached instances. This ensures repeated reads see the latest database state. Dirty checking remains available at all isolation levels. Storm stores observed state for detecting changes even when not returning cached instances. See [dirty checking](dirty-checking.md) for details.

#### READ_COMMITTED

Transactions only see data that has been committed. This prevents dirty reads: you will never see data that might be rolled back. However, if you read the same row twice, you might get different values if another transaction modified and committed it in between (non-repeatable read).

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

Guarantees that if you read a row once, subsequent reads return the same data, even if other transactions modify and commit changes to that row. The transaction works with a consistent snapshot taken at the start. However, phantom reads may still occur: new rows inserted by other transactions can appear in range queries.

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
        // until we commit, which prevents double-booking
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

**Simple CRUD operations:** Use `READ_COMMITTED`. Seeing the latest committed data is usually what you want:

```kotlin
transaction(isolation = READ_COMMITTED) {
    userRepository.update(user)
}
```

**Reports and calculations:** Use `REPEATABLE_READ` when you need multiple queries to see a consistent snapshot. This ensures totals, counts, and details all reflect the same point in time:

```kotlin
transaction(isolation = REPEATABLE_READ) {
    val total = orderRepository.sumByUser(userId)
    val count = orderRepository.countByUser(userId)
    val average = total / count  // Safe: total and count are consistent
}
```

**Critical operations with race conditions:** Use `SERIALIZABLE` when concurrent transactions could cause problems like double-booking or overselling. The performance cost is worth the correctness guarantee:

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

Long-running transactions hold database locks and consume connection pool resources. Setting a timeout ensures that a stuck or unexpectedly slow transaction is automatically rolled back rather than blocking indefinitely. The timeout is measured from the start of the transaction block.

```kotlin
transaction(timeoutSeconds = 30) {
    orm.removeAll<Visit>()
    delay(35_000)  // Will cause timeout
}
```

### Read-Only Transactions

Marking a transaction as read-only allows the database to apply optimizations such as skipping write-ahead logging or acquiring lighter locks. This is a hint, not an enforcement mechanism; the database may or may not reject writes depending on the driver and database engine.

```kotlin
transaction(readOnly = true) {
    // Hints to the database that no modifications will occur
    val users = orm.findAll<User>()
}
```

### Manual Rollback

Sometimes you need to abort a transaction based on a runtime condition rather than an exception. Calling `setRollbackOnly()` marks the transaction for rollback without throwing. The block continues executing, but the transaction rolls back when it completes instead of committing.

```kotlin
transaction {
    orm.removeAll<Visit>()

    if (someCondition) {
        setRollbackOnly()  // Mark for rollback
    }
    // Transaction will roll back instead of commit
}
```

### Transaction Callbacks

Database transactions often need to trigger side effects, but only when the outcome is certain. Sending a confirmation email before the order is committed risks notifying a customer about an order that never persisted. Conversely, cleanup logic (releasing external locks, closing temporary resources) should run after a rollback, not during regular flow where it might mask the real failure.

Storm's `onCommit` and `onRollback` callbacks solve this by letting you register logic that fires **after** the physical transaction completes. Callbacks are registered inside the transaction block but execute outside it, once the outcome is final. Note that running such logic right after the block is not a substitute: with `REQUIRED` propagation the block may have joined an outer transaction, in which case the end of the block commits nothing and the outer transaction may still roll back. Callbacks bind to the physical transaction, so they remain correct however deeply the block is nested.

Work that has to happen either way — releasing a lock, closing a span — registers once with `onCompletion`, which receives whether the transaction committed. `onCommit` and `onRollback` stay the simpler form when only one outcome is of interest, and say so at the registration site.

#### Basic Usage

Register callbacks anywhere inside a `transaction` or `transactionBlocking` block:

```kotlin
transaction {
    val order = orderRepository.insert(newOrder)
    inventoryRepository.decrease(order.productId, order.quantity)

    onCommit {
        // Only runs after the transaction has successfully committed.
        // The order and inventory changes are durable at this point.
        emailService.sendOrderConfirmation(order)
        eventBus.publish(OrderCreatedEvent(order.id))
    }

    onRollback {
        // Only runs after the transaction has rolled back.
        // No changes were persisted.
        metrics.increment("orders.failed")
    }

    onCompletion { committed ->
        // Runs either way; committed says which outcome it followed.
        lockService.release(order.id)
    }
}
```

Both variants work identically with `transactionBlocking`:

```kotlin
transactionBlocking {
    cacheRepository.update(entry)

    onCommit {
        cache.invalidate(entry.key)  // Evict stale cache entry only after new data is durable
    }
}
```

#### When Callbacks Fire

Callbacks are deferred until the transaction outcome is determined. The following table summarizes the trigger conditions:

| Scenario | `onCommit` | `onRollback` | `onCompletion` receives |
|----------|------------|--------------|-------------------------|
| Block completes normally | Fires | Does not fire | `true` |
| Block throws an exception | Does not fire | Fires | `false` |
| `setRollbackOnly()` called, block completes | Does not fire | Fires | `false` |
| Transaction timeout expires | Does not fire | Fires | `false` |
| Commit itself throws (e.g., constraint violation during flush) | Does not fire | Fires | `false` |

The key guarantee is that `onCommit` callbacks only execute when data is actually durable. If the commit itself fails for any reason, `onCommit` callbacks are skipped and `onRollback` callbacks run instead. `onCompletion` fires in every scenario and receives `true` exactly when the data is durable.

This timeline shows the execution order for a successful transaction:

```
[BEGIN]
   ↓
   insert(order)
   onCommit { sendEmail() }       ← registered, not yet executed
   onRollback { logFailure() }    ← registered, not yet executed
   ↓
[COMMIT]                          ← transaction commits successfully
   ↓
   sendEmail()                    ← onCommit fires now
                                     (onRollback is discarded)
```

And for a failed transaction:

```
[BEGIN]
   ↓
   insert(order)
   onCommit { sendEmail() }       ← registered, not yet executed
   onRollback { logFailure() }    ← registered, not yet executed
   ↓
   decreaseInventory()
   ↓
   ✗ exception thrown
   ↓
[ROLLBACK]                        ← transaction rolls back
   ↓
   logFailure()                   ← onRollback fires now
                                     (onCommit is discarded)
```

#### Multiple Callbacks and Ordering

You can register any number of callbacks. All three kinds share a single registration order — each run executes them in the order they were registered, skipping the ones that do not apply to the outcome — which makes it straightforward to reason about sequencing when multiple components register their own callbacks:

```kotlin
transaction {
    val user = userRepository.insert(newUser)
    val profile = profileRepository.insert(Profile(userId = user.id))

    onCommit { searchIndex.addUser(user) }         // 1st
    onCommit { cache.warm(user.id) }                // 2nd
    onCommit { eventBus.publish(UserCreated(user)) } // 3rd
}
// After commit: searchIndex → cache → eventBus, in that order
```

#### Exception Handling in Callbacks

If a callback throws, the remaining callbacks still execute. This prevents one failing callback from silently skipping others. The failures surface as a `TransactionCallbackException` whose cause is the first one, with the rest attached to it as suppressed:

```kotlin
transaction {
    orderRepository.insert(order)

    onCommit { throw RuntimeException("email failed") }   // throws, but...
    onCommit { cache.invalidate(order.productId) }         // ...still executes
}
// Caller catches TransactionCallbackException: isCommitted() == true,
// cause is RuntimeException("email failed").
// cache.invalidate() ran successfully; the order IS persisted.
```

The distinct type is what lets a caller tell "the work was not persisted" apart from "the work was persisted and something after it failed". The two need opposite responses: the first is a candidate for a retry, the second usually is not, because retrying repeats work that already succeeded. `isCommitted()` says which completion the failure followed.

When the transaction itself fails and a rollback callback also throws, the callback failure does not replace the original exception: it is attached to it as suppressed, still wrapped in `TransactionCallbackException`:

```kotlin
try {
    transaction {
        onRollback { throw RuntimeException("cleanup failed") }
        throw IllegalStateException("business error")
    }
} catch (e: IllegalStateException) {
    // e.message == "business error"                       ← primary exception
    // e.suppressed[0] is TransactionCallbackException     ← callback failure,
    //   whose cause is RuntimeException("cleanup failed")    attached, not thrown
}
```

This design ensures that the root cause of a failure is never masked by callback errors.

#### Propagation Interaction

Callbacks are tied to the **physical** transaction, not the logical scope. This distinction matters when nesting transactions with different propagation modes.

**Joining propagations (`REQUIRED`, `NESTED`, `SUPPORTS`, `MANDATORY`):** Callbacks registered in an inner scope are deferred to the outer physical transaction. They fire when the outermost transaction commits or rolls back. This is the correct behavior, because in a joined transaction, the inner scope's changes are not durable until the outer transaction commits.

```
[BEGIN outer]
   ↓
   insert(user)
   ↓
   ┌─ transaction(REQUIRED) ──────────────────────┐
   │  insert(order)                               │
   │  onCommit { notify(order) }  ← deferred      │
   └──────────────────────────────────────────────┘
   ↓
   insert(payment)
   onCommit { sendReceipt() }     ← also deferred
   ↓
[COMMIT outer]
   ↓
   notify(order)                  ← inner callback fires now
   sendReceipt()                  ← outer callback fires now
```

A practical example: the inner service registers a callback, but it only fires when the outer transaction actually commits. If the outer transaction rolls back, the inner callback is discarded along with it:

```kotlin
// Outer transaction
transaction {
    userRepository.insert(user)

    // Inner REQUIRED: joins the outer transaction
    transaction(propagation = REQUIRED) {
        orderRepository.insert(order)
        onCommit { eventBus.publish(OrderCreated(order.id)) }
    }
    // At this point, the inner onCommit has NOT fired yet.
    // The order is not yet durable.

    paymentRepository.insert(payment)
}
// NOW the outer commits, and the inner's onCommit fires.
```

If the outer transaction rolls back (explicitly or via exception), the inner callback never fires:

```kotlin
transaction {
    transaction(propagation = REQUIRED) {
        orderRepository.insert(order)
        onCommit { eventBus.publish(OrderCreated(order.id)) }
    }

    setRollbackOnly()  // Outer rolls back everything
}
// onCommit never fires. The order was never durable.
```

**`REQUIRES_NEW`:** Creates an independent physical transaction. Callbacks registered in the inner scope fire when the **inner** transaction completes, regardless of the outer transaction's outcome:

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
      onCommit { notify() }
      ↓
   [COMMIT inner]
      ↓
      notify()                 ← fires immediately, inner is committed
   ↓
   ~~~ outer resumed ~~~
   ↓
[ROLLBACK outer]              ← does not affect inner's callbacks
```

This is especially useful for audit logging or event publishing that must survive regardless of the outer outcome:

```kotlin
transaction {
    userRepository.insert(user)

    transaction(propagation = REQUIRES_NEW) {
        auditRepository.insert(AuditLog("User creation attempted"))
        onCommit { auditMetrics.increment("audit.committed") }
    }
    // Inner onCommit has already fired here.

    setRollbackOnly()  // Outer rolls back, but audit is committed and notified
}
```

**`NESTED` (savepoint):** Shares the outer physical transaction. Even though the nested scope can roll back independently (to the savepoint), callbacks are deferred to the outer transaction. This is because savepoint changes only become durable when the outer transaction commits:

```
[BEGIN outer]
   ↓
   insert(order)
   ↓
   [SAVEPOINT]
      ↓
      insert(discount)
      onCommit { notify() }   ← deferred to outer
      ↓
   [RELEASE SAVEPOINT]
   ↓
[COMMIT outer]
   ↓
   notify()                    ← fires now
```

The following table summarizes callback behavior across propagation modes:

| Propagation | Callback scope | When callbacks fire |
|-------------|---------------|---------------------|
| `REQUIRED` | Deferred to outer | When outermost transaction commits/rolls back |
| `REQUIRES_NEW` | Own scope | When inner transaction commits/rolls back |
| `NESTED` | Deferred to outer | When outermost transaction commits/rolls back |
| `SUPPORTS` | Deferred to outer (if tx exists) | When outermost transaction commits/rolls back |
| `MANDATORY` | Deferred to outer | When outermost transaction commits/rolls back |
| `NOT_SUPPORTED` | Own scope | When inner block completes/throws |
| `NEVER` | Own scope | When inner block completes/throws |

#### Common Patterns

**Cache invalidation after write:**

```kotlin
transaction {
    val updatedProduct = productRepository.update(product)

    onCommit {
        // Only evict after the update is durable.
        // Evicting before commit risks serving stale data from the database
        // while the cache is empty and the transaction hasn't committed yet.
        productCache.evict(updatedProduct.id)
    }
}
```

**Event publishing:**

```kotlin
transaction {
    val savedOrder = orderRepository.insert(order)
    paymentRepository.insert(Payment(orderId = savedOrder.id, amount = total))

    onCommit {
        // Publish domain events only after all writes are durable.
        // Subscribers can safely query the database for the new data.
        eventBus.publish(OrderPlacedEvent(savedOrder.id, total))
    }

    onRollback {
        // Track failed order attempts for monitoring
        metrics.increment("orders.failed")
        logger.warn("Order placement rolled back for customer ${order.customerId}")
    }
}
```

**Releasing external resources:**

```kotlin
transaction {
    val lockToken = distributedLock.acquire("import-job")

    onCommit {
        distributedLock.release(lockToken)
    }

    onRollback {
        distributedLock.release(lockToken)
        cleanupPartialImport()
    }

    importService.runImport(data)
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

When you need different transaction settings for a specific section of code without changing global defaults, use scoped options. All transactions created within the scope inherit the overridden settings. This is useful for test harnesses, batch processing regions, or any bounded context that needs distinct transaction behavior.

```kotlin
withTransactionOptions(timeoutSeconds = 60) {
    transaction {
        // Uses 60 second timeout
        orm.removeAll<Visit>()
    }
}

withTransactionOptionsBlocking(isolation = SERIALIZABLE) {
    transactionBlocking {
        // Uses SERIALIZABLE isolation
        orm.removeAll<Visit>()
    }
}
```

### How Transactions Bind to Templates

Since 1.13, a `transaction` or `transactionBlocking` block binds to the first `ORMTemplate` that executes inside it. Opening the block only records the requested options (propagation, isolation, timeout, read-only); the actual transaction is opened by that first template's transaction provider. This means the block automatically uses whatever transaction system the template is configured with, whether that is Storm's own JDBC transactions or a platform bridge such as Spring's transaction management. A block that never touches a template completes as a no-op.

Templates that should share a transaction must use the same transaction provider instance. This is automatic for repositories of one application (the Spring Boot starter and the Ktor plugin configure one provider per application context or plugin installation). Mixing templates with *different* transaction providers inside one block fails fast with a descriptive error, since a single commit cannot span two transaction systems.

### Spring-Managed Transactions

While Storm's programmatic transaction API works standalone, many applications use Spring's transaction management for its declarative `@Transactional` support and integration with other Spring components. Storm integrates seamlessly with Spring's transaction management.

When a template is wired to Spring's transaction management, Storm's programmatic `transactionBlocking` blocks run through Spring's `PlatformTransactionManager` and participate in Spring-managed transactions. This gives you the best of both worlds: Spring's declarative transaction boundaries with Storm's programmatic transaction blocks. The suspending `transaction` variant is not supported with Spring-managed transactions; use `transactionBlocking` there.

#### Configuration

The Spring Boot starter wires this automatically when a `PlatformTransactionManager` is present. Without the starter, compose the template with `springOrmTemplate`:

```kotlin
@Configuration
@EnableTransactionManagement
class ORMConfiguration {
    @Bean
    fun ormTemplate(
        dataSource: DataSource,
        transactionManagers: ObjectProvider<PlatformTransactionManager>,
    ): ORMTemplate = springOrmTemplate(dataSource) { transactionManagers.orderedStream().toList() }
}
```

#### Combining Declarative and Programmatic Transactions

You can use Spring's `@Transactional` annotation alongside Storm's programmatic `transactionBlocking` blocks. Storm will join the existing Spring transaction:

```kotlin
@Service
class UserService(private val orm: ORMTemplate) {

    @Transactional
    fun createUserWithOrders(user: User, orders: List<Order>) {
        // Spring starts the transaction

        transactionBlocking {
            // Storm joins the Spring transaction (REQUIRED propagation by default)
            orm insert user
        }

        transactionBlocking {
            // Still in the same Spring transaction
            orders.forEach { orm insert it }
        }

        // Spring commits when the method returns successfully
    }
}
```

#### Propagation Interaction

Storm's propagation modes work with Spring transactions:

```kotlin
@Transactional
suspend fun processWithAudit(user: User) {
    transaction {
        orm insert user
    }

    // REQUIRES_NEW creates an independent transaction, even within Spring's transaction
    transaction(propagation = REQUIRES_NEW) {
        auditRepository.log("User created: ${user.id}")
        // Commits independently - audit survives even if outer transaction rolls back
    }
}
```

#### Suspend Functions with @Transactional

For suspend functions, use Spring's `@Transactional` with the coroutine-aware transaction manager:

```kotlin
@Configuration
@EnableTransactionManagement
class TransactionConfig {
    @Bean
    fun transactionManager(dataSource: DataSource): ReactiveTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }
}

@Service
class OrderService(private val orm: ORMTemplate) {

    @Transactional
    suspend fun placeOrder(order: Order): Order {
        val savedOrder = orm insert order

        // Can switch dispatchers while staying in the same transaction
        withContext(Dispatchers.Default) {
            calculateLoyaltyPoints(savedOrder)
        }

        return savedOrder
    }
}
```

#### Using Storm Without @Transactional

You can also use Storm's programmatic transactions without Spring's `@Transactional`. Storm manages the transaction lifecycle directly:

```kotlin
@Service
class UserService(private val orm: ORMTemplate) {

    // No @Transactional needed - Storm handles it
    suspend fun createUser(user: User): User {
        return transaction {
            orm insert user
        }
    }

    // Explicit propagation and isolation
    suspend fun transferFunds(from: Account, to: Account, amount: BigDecimal) {
        transaction(
            propagation = REQUIRED,
            isolation = SERIALIZABLE
        ) {
            accountRepository.debit(from, amount)
            accountRepository.credit(to, amount)
        }
    }
}
```

</TabItem>
<TabItem value="java" label="Java">

Storm for Java provides a fully programmatic transaction API with the same semantics as the Kotlin `transaction { }` blocks: all seven propagation modes, isolation levels, timeouts, read-only transactions, rollback-only marks, and completion callbacks. The blocking API is virtual-thread friendly: the block parks on I/O rather than pinning carrier threads.

Storm also integrates with your existing transaction infrastructure: inside Spring applications, `@Transactional` and Spring's own `TransactionTemplate` remain first-class citizens, and Storm participates correctly in the active transaction.

### Programmatic Transactions

Import the static entry points and the option enums:

```java
import static st.orm.template.Transactions.transaction;
import static st.orm.template.Transactions.withTransactionOptions;
import static st.orm.template.Transactions.setGlobalTransactionOptions;
import st.orm.TransactionOptions;
import st.orm.TransactionPropagation;
import st.orm.TransactionIsolation;
```

The transaction binds to the first ORM template that executes inside the block: opening the block only records the requested options, and the template's transaction provider opens the actual transaction on first use. The block commits when it completes normally and rolls back when it throws; checked exceptions propagate to the caller unchanged and trigger rollback.

```java
// Commit on success; the block's value is returned.
User created = transaction(tx -> users.insertAndFetch(user));

// Roll back on exception: the original exception propagates.
transaction(tx -> {
    orders.insert(order);
    inventory.update(stock);
    return null;
});

// Checked exceptions need no wrapping: the call site declares what the block throws.
void importFile(Path path) throws IOException {
    transaction(tx -> {
        var data = Files.readString(path);   // IOException propagates and rolls back.
        return imports.insertAndFetch(parse(data));
    });
}
```

### Propagation, Isolation, Timeout, Read-Only

The common case takes the propagation directly; full control goes through `TransactionOptions`, an immutable record with withers. Options left unset are inherited from the surrounding defaults.

```java
// Independent transaction: commits even if the surrounding transaction rolls back.
transaction(TransactionPropagation.REQUIRES_NEW, tx -> audit.insertAndFetch(entry));

// Full control.
transaction(TransactionOptions.defaults()
        .withIsolation(TransactionIsolation.SERIALIZABLE)
        .withTimeoutSeconds(30)
        .withReadOnly(true), tx -> reports.generate());
```

The propagation semantics are identical to the Kotlin API; see the propagation behavior matrix in the Kotlin tab. `MANDATORY` without an active transaction and `NEVER` inside one fail with a `PersistenceException`; an expired timeout raises `TransactionTimedOutException`; a joined inner scope that marks the transaction rollback-only makes the outer commit raise `UnexpectedRollbackException`.

### Rollback Control and Callbacks

The block receives a `Transaction` handle:

```java
transaction(tx -> {
    orders.insert(order);
    if (!validator.accepts(order)) {
        tx.setRollbackOnly();   // Complete normally, then roll back.
    }
    tx.onCommit(() -> notifications.orderPlaced(order));
    tx.onRollback(() -> log.warn("Order {} rolled back.", order.id()));
    tx.onCompletion(committed -> locks.release(order.id()));
    return order;
});
```

Callbacks registered in a scope that joins an outer transaction are deferred to the outermost physical transaction's completion; `REQUIRES_NEW` scopes fire their own callbacks independently. The three kinds share one registration order: callbacks run in the order they were registered after the transaction has fully completed, skipping the ones that do not apply to the outcome. `onCompletion` receives whether the transaction committed, which is the variant for work that has to happen either way. If a callback throws, the remaining callbacks still execute and the failures surface as a `TransactionCallbackException` whose cause is the first one, with the rest suppressed; `isCommitted()` tells a failed side effect apart from a failed transaction, which need opposite responses, since retrying the former repeats work that already succeeded.

### Global and Scoped Defaults

```java
// Application-wide defaults, typically set once at startup.
setGlobalTransactionOptions(TransactionOptions.defaults().withTimeoutSeconds(60));

// Thread-scoped defaults for a code region; restored afterwards.
withTransactionOptions(TransactionOptions.defaults().withReadOnly(true), () -> {
    var summary = transaction(tx -> reports.summarize());
    return summary;
});
```

Explicit options on a `transaction(...)` call always win over scoped defaults, which win over the global defaults.

### Spring-Managed Transactions

Spring's transaction management is the most common approach for Java enterprise applications. Storm integrates naturally with Spring's `@Transactional` annotation, participating in the same transaction as other Spring-managed components like JPA repositories, JDBC templates, or other data access code.

#### Configuration

Configure Storm with Spring's transaction management. The Spring Boot starter does this automatically when a `PlatformTransactionManager` is present; without the starter, compose the template with `SpringOrmTemplate.of`:

```java
@Configuration
@EnableTransactionManagement
public class ORMConfiguration {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public ORMTemplate ormTemplate(DataSource dataSource,
                                   ObjectProvider<PlatformTransactionManager> transactionManagers) {
        return SpringOrmTemplate.of(dataSource, () -> transactionManagers.orderedStream().toList());
    }
}
```

With this composition, Spring's `@Transactional` and Storm's programmatic `transaction(...)` blocks share the same transaction system: a Storm block inside a `@Transactional` method joins the Spring-managed transaction, and a standalone Storm block runs through Spring's transaction manager.

#### Declarative Transactions with @Transactional

Use Spring's `@Transactional` annotation on service methods. Storm automatically participates in the active transaction:

```java
@Service
public class UserService {

    private final ORMTemplate orm;

    public UserService(ORMTemplate orm) {
        this.orm = orm;
    }

    @Transactional
    public void createUserWithOrders(User user, List<Order> orders) {
        // Storm uses the Spring-managed transaction
        orm.entity(User.class).insert(user);

        for (Order order : orders) {
            orm.entity(Order.class).insert(order);
        }
        // Spring commits when the method returns successfully
        // Rolls back automatically on unchecked exceptions
    }

    @Transactional(readOnly = true)
    public List<User> findUsersByName(String name) {
        return orm.entity(User.class)
            .select()
            .where(User_.name, EQUALS, name)
            .getResultList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transferFunds(Account from, Account to, BigDecimal amount) {
        orm.entity(Account.class).update(from.debit(amount));
        orm.entity(Account.class).update(to.credit(amount));
    }
}
```

#### Propagation with @Transactional

Spring's propagation modes control how transactions interact:

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) {
        orm.entity(Order.class).insert(order);

        // Audit log commits independently - survives even if outer transaction rolls back
        auditService.logOrderCreated(order);

        inventoryService.decreaseStock(order.getItems());
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderCreated(Order order) {
        orm.entity(AuditLog.class).insert(new AuditLog("Order created: " + order.getId()));
        // Commits in its own transaction
    }
}
```

#### Programmatic Transactions

While `@Transactional` works well for most cases, sometimes you need finer control over transaction boundaries. For example, processing a batch where each item should be in its own transaction, or conditionally rolling back based on runtime conditions. Spring's `TransactionTemplate` provides this control while still integrating with Spring's transaction infrastructure.

```java
@Service
public class BatchService {

    private final TransactionTemplate transactionTemplate;
    private final ORMTemplate orm;

    public BatchService(PlatformTransactionManager transactionManager, ORMTemplate orm) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.orm = orm;
    }

    public void processBatch(List<Item> items) {
        for (Item item : items) {
            // Each item processed in its own transaction
            transactionTemplate.execute(status -> {
                orm.entity(Item.class).update(item.markProcessed());
                return null;
            });
        }
    }

    public User createUserOrRollback(User user, boolean shouldRollback) {
        return transactionTemplate.execute(status -> {
            User saved = orm.entity(User.class).insert(user);

            if (shouldRollback) {
                status.setRollbackOnly();  // Mark for rollback
            }

            return saved;
        });
    }
}
```

Configure `TransactionTemplate` with specific settings:

```java
TransactionTemplate template = new TransactionTemplate(transactionManager);
template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
template.setTimeout(30);  // 30 seconds
template.setReadOnly(true);

List<User> users = template.execute(status -> {
    return orm.entity(User.class).select().getResultList();
});
```

### JDBC Transactions

For applications not using Spring, or for maximum control, you can manage transactions directly through JDBC. Storm works with any JDBC connection. Create an `ORMTemplate` from the connection and use it within your transaction scope.

```java
try (Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false);

    try {
        var orm = ORMTemplate.of(connection);
        orm.entity(User.class).insert(user);
        orm.entity(Order.class).insert(order);

        connection.commit();
    } catch (Exception e) {
        connection.rollback();
        throw e;
    }
}
```

### JPA EntityManager

Storm can coexist with JPA in the same application. This is useful when migrating from JPA to Storm gradually, or when you want to use Storm for specific operations (like bulk inserts or complex queries) while keeping JPA for others. Storm can create an `ORMTemplate` directly from a JPA `EntityManager`, sharing the same underlying connection and transaction.

```java
@Service
public class HybridService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void processWithBothOrms(User user) {
        // Use Storm for efficient bulk operations
        var orm = ORMTemplate.of(entityManager);
        orm.entity(User.class).insert(user);

        // JPA and Storm share the same transaction
        entityManager.flush();
    }
}
```

</TabItem>
</Tabs>

---

## Important Notes

Understanding these nuances helps avoid common pitfalls when working with transactions.

### Concurrency

Launching concurrent work inside a transaction using `async`, `launch`, or other parallel coroutine builders is **not supported**. Database transactions are bound to the calling thread/coroutine. Use sequential operations or split work into separate transactions if parallelism is required.

### RollbackOnly Semantics

- In `NESTED` propagation: rolls back to the savepoint, preserving outer transaction's work
- In `REQUIRED` or `REQUIRES_NEW`: affects the entire transaction scope

### Context Switching (Kotlin)

Within any transactional scope, you can switch dispatchers (e.g., `withContext(Dispatchers.Default)`) and still access the **same active transaction**. This allows offloading CPU-bound work without breaking transactional context.
