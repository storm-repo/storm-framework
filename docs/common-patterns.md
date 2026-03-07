import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Common Patterns

This page collects practical patterns for recurring requirements that are not covered by a dedicated Storm API but are straightforward to implement using the framework's building blocks. Each pattern includes a complete example with the entity definition, supporting code, and usage.

---

## Loading One-to-Many Relationships

Storm does not support collection fields on entities. This is by design: embedding collections inside entities leads to lazy loading, N+1 queries, and unpredictable fetch behavior. Instead, you load the "many" side with an explicit query and assemble the result in your application code.

Unlike JPA's `@OneToMany` collection, Storm loads relationships via explicit queries. This gives you full control over when and how children are loaded, preventing N+1 problems and making the data flow visible in the source code.

### Entity Definitions

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
@DbTable("purchase_order")
data class Order(
    @PK val id: Long = 0,
    val customerId: Long,
    val status: String,
    val createdAt: Instant?
) : Entity<Long>

data class LineItem(
    @PK val id: Long = 0,
    @FK val order: Order,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) : Entity<Long>
```

</TabItem>
<TabItem value="java" label="Java">

```java
@DbTable("purchase_order")
public record Order(
    @PK Long id,
    long customerId,
    String status,
    @Nullable Instant createdAt
) implements Entity<Long> {}

public record LineItem(
    @PK Long id,
    @FK Order order,
    String productName,
    int quantity,
    BigDecimal unitPrice
) implements Entity<Long> {}
```

</TabItem>
</Tabs>

### Fetching and Assembling

Fetch the parent entity, then query its children using the foreign key. Assemble the result into a response object that your service or controller returns.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
data class OrderWithItems(
    val order: Order,
    val lineItems: List<LineItem>
)

fun findOrderWithItems(orderId: Long): OrderWithItems? {
    val order = orm.entity(Order::class).findById(orderId) ?: return null
    val lineItems = orm.entity(LineItem::class).findAll { LineItem_.order eq order }
    return OrderWithItems(order, lineItems)
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
public record OrderWithItems(Order order, List<LineItem> lineItems) {}

public Optional<OrderWithItems> findOrderWithItems(long orderId) {
    return orm.entity(Order.class).findById(orderId)
        .map(order -> {
            List<LineItem> lineItems = orm.entity(LineItem.class)
                .select()
                .where(LineItem_.order, EQUALS, order)
                .getResultList();
            return new OrderWithItems(order, lineItems);
        });
}
```

</TabItem>
</Tabs>

This pattern generalizes to any one-to-many relationship. Both queries are explicit and visible in the source code, so you can easily add filtering, sorting, or pagination to the child query without affecting the parent fetch.

---

## Auditing

Most applications need to track when records were created and last modified. Storm's `EntityCallback` interface provides the hooks for this without requiring special annotations or framework-specific column types.

### Entity Definition

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
@DbTable("article")
data class Article(
    @PK val id: Int = 0,
    val title: String,
    val content: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
) : Entity<Int>
```

</TabItem>
<TabItem value="java" label="Java">

```java
@DbTable("article")
public record Article(
    @PK Integer id,
    String title,
    String content,
    Instant createdAt,
    Instant updatedAt
) implements Entity<Integer> {}
```

</TabItem>
</Tabs>

### Callback Implementation

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
class AuditTimestampCallback : EntityCallback<Article> {

    override fun beforeInsert(entity: Article): Article {
        val now = Instant.now()
        return entity.copy(createdAt = now, updatedAt = now)
    }

    override fun beforeUpdate(entity: Article): Article =
        entity.copy(updatedAt = Instant.now())
}
```

Register it with the ORM template:

```kotlin
val orm = ORMTemplate.of(dataSource)
    .withEntityCallback(AuditTimestampCallback())
```

Or declare it as a Spring bean for automatic registration:

```kotlin
@Bean
fun auditTimestampCallback(): EntityCallback<*> = AuditTimestampCallback()
```

</TabItem>
<TabItem value="java" label="Java">

```java
public class AuditTimestampCallback implements EntityCallback<Article> {

    @Override
    public Article beforeInsert(Article entity) {
        var now = Instant.now();
        return new Article(entity.id(), entity.title(), entity.content(), now, now);
    }

    @Override
    public Article beforeUpdate(Article entity) {
        return new Article(entity.id(), entity.title(), entity.content(), entity.createdAt(), Instant.now());
    }
}
```

Register it with the ORM template:

```java
ORMTemplate orm = ORMTemplate.of(dataSource)
    .withEntityCallback(new AuditTimestampCallback());
```

Or declare it as a Spring bean for automatic registration:

```java
@Bean
public EntityCallback<?> auditTimestampCallback() {
    return new AuditTimestampCallback();
}
```

</TabItem>
</Tabs>

To apply auditing to all entities (not just `Article`), parameterize the callback with `Entity<?>` and use pattern matching to handle each entity type. See the [Entity Lifecycle](entity-lifecycle.md) page for details.

---

## Soft Deletes

Soft deletes mark records as deleted without physically removing them from the database. This preserves data for audit trails, undo operations, or compliance requirements. The pattern uses a boolean or timestamp column to indicate deletion status.

### Entity Definition

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
@DbTable("customer")
data class Customer(
    @PK val id: Int,
    val name: String,
    val email: String,
    val deletedAt: Instant?     // null means not deleted
) : Entity<Int>
```

</TabItem>
<TabItem value="java" label="Java">

```java
@DbTable("customer")
public record Customer(
    @PK int id,
    String name,
    String email,
    Instant deletedAt     // null means not deleted
) implements Entity<Integer> {}
```

</TabItem>
</Tabs>

### Repository with Soft Delete Methods

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
interface CustomerRepository : EntityRepository<Customer, Int> {

    /** Find only non-deleted customers. */
    fun findActive(): List<Customer> =
        findAll { Customer_.deletedAt.isNull() }

    /** Find a non-deleted customer by ID. */
    fun findActiveOrNull(customerId: Int): Customer? =
        find { (Customer_.id eq customerId) and Customer_.deletedAt.isNull() }

    /** Soft-delete a customer by setting the deletedAt timestamp. */
    fun softDelete(customer: Customer): Customer {
        val softDeleted = customer.copy(deletedAt = Instant.now())
        update(softDeleted)
        return softDeleted
    }

    /** Restore a soft-deleted customer. */
    fun restore(customer: Customer): Customer {
        val restored = customer.copy(deletedAt = null)
        update(restored)
        return restored
    }
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
public interface CustomerRepository extends EntityRepository<Customer, Integer> {

    /** Find only non-deleted customers. */
    default List<Customer> findActive() {
        return findAll(Customer_.deletedAt.isNull());
    }

    /** Find a non-deleted customer by ID. */
    default Optional<Customer> findActiveById(int customerId) {
        return select()
            .where(Customer_.id.eq(customerId).and(Customer_.deletedAt.isNull()))
            .getOptionalResult();
    }

    /** Soft-delete a customer by setting the deletedAt timestamp. */
    default Customer softDelete(Customer customer) {
        var softDeleted = new Customer(customer.id(), customer.name(), customer.email(), Instant.now());
        update(softDeleted);
        return softDeleted;
    }

    /** Restore a soft-deleted customer. */
    default Customer restore(Customer customer) {
        var restored = new Customer(customer.id(), customer.name(), customer.email(), null);
        update(restored);
        return restored;
    }
}
```

</TabItem>
</Tabs>

### Enforcing Soft Deletes via Callback

To prevent accidental hard deletes, use an entity callback that converts `delete()` calls into soft deletes:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
class SoftDeleteGuard : EntityCallback<Customer> {

    override fun beforeDelete(entity: Customer) {
        throw PersistenceException(
            "Hard deletes are not allowed for Customer. Use softDelete() instead."
        )
    }
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
public class SoftDeleteGuard implements EntityCallback<Customer> {

    @Override
    public void beforeDelete(Customer entity) {
        throw new PersistenceException(
            "Hard deletes are not allowed for Customer. Use softDelete() instead.");
    }
}
```

</TabItem>
</Tabs>

---

## Pagination

Storm provides built-in types for both offset-based and keyset-based pagination, so you do not need to define your own page wrappers or write raw `LIMIT`/`OFFSET` queries.

### Offset-Based Pagination

Use the `page()` method on any entity or projection repository. Storm executes the data query and count query automatically, returning a `Page` that includes the result list and total count.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page of 20 users (page numbers are zero-based)
val page: Page<User> = userRepository.page(0, 20)

// With sort order using Pageable
val pageable = Pageable.ofSize(20).sortBy(User_.name)
val sortedPage: Page<User> = userRepository.page(pageable)

// Navigate forward
if (sortedPage.hasNext()) {
    val nextPage = userRepository.page(sortedPage.nextPageable())
}

// Navigate backward
if (sortedPage.hasPrevious()) {
    val previousPage = userRepository.page(sortedPage.previousPageable())
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page of 20 users (page numbers are zero-based)
Page<User> page = userRepository.page(0, 20);

// With sort order using Pageable
Pageable pageable = Pageable.ofSize(20).sortBy(User_.name);
Page<User> sortedPage = userRepository.page(pageable);

// Navigate forward
if (sortedPage.hasNext()) {
    Page<User> nextPage = userRepository.page(sortedPage.nextPageable());
}

// Navigate backward
if (sortedPage.hasPrevious()) {
    Page<User> previousPage = userRepository.page(sortedPage.previousPageable());
}
```

</TabItem>
</Tabs>

The `Page` record carries all the metadata you need for building pagination controls:

| Field / Method | Description |
|---|---|
| `content` | List of results for the current page |
| `totalCount` | Total matching rows across all pages |
| `pageNumber()` | Zero-based index of the current page |
| `pageSize()` | Maximum elements per page |
| `totalPages()` | Computed total number of pages |
| `hasNext()` / `hasPrevious()` | Navigation checks |
| `nextPageable()` / `previousPageable()` | Returns a `Pageable` for the adjacent page |

To load only primary keys instead of full entities, use `pageRef()`:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val refPage: Page<Ref<User>> = userRepository.pageRef(0, 20)
```

</TabItem>
<TabItem value="java" label="Java">

```java
Page<Ref<User>> refPage = userRepository.pageRef(0, 20);
```

</TabItem>
</Tabs>

### Keyset-Based Pagination

Use the `slice()`, `sliceAfter()`, and `sliceBefore()` methods on any entity repository. These use a unique column value (typically the primary key) as a cursor, which lets the database seek directly to the correct position using an index.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page of 20 users ordered by ID
val page1: Slice<User> = userRepository.slice(User_.id, 20)

// Next page using the last ID as cursor
val page2: Slice<User> = userRepository.sliceAfter(User_.id, page1.content.last().id, 20)

// Previous page before a known cursor
val previous: Slice<User> = userRepository.sliceBefore(User_.id, someId, 20)
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page of 20 users ordered by ID
Slice<User> page1 = userRepository.slice(User_.id, 20);

// Next page using the last ID as cursor
User last = page1.content().getLast();
Slice<User> page2 = userRepository.sliceAfter(User_.id, last.id(), 20);

// Previous page before a known cursor
Slice<User> previous = userRepository.sliceBefore(User_.id, someId, 20);
```

</TabItem>
</Tabs>

Each method returns a `Slice` containing the page content and a `hasNext` flag. See [Repositories: Keyset Pagination](repositories.md#keyset-pagination) for the full API, including sort overloads, filtering, and ref variants.

### Choosing Between the Two

| Factor | Offset-Based (`page`) | Keyset-Based (`slice`) |
|---|---|---|
| Implementation complexity | Simple | Moderate |
| Jump to arbitrary page | Yes | No (sequential only) |
| Performance at page 1 | Good | Good |
| Performance at page 1,000 | Degrades (database must skip rows) | Consistent (index seek) |
| Handles concurrent inserts | Rows may shift between pages | Stable cursor |
| Total element count | Included in `Page` | Not available in `Slice` |

Use offset-based pagination when you need random page access or a total count (for example, displaying "Page 3 of 12" in a UI). Use keyset pagination when you need consistent performance over deep result sets or when the data changes frequently between requests.

---

## Bulk Import

For large-scale data imports, use Storm's streaming batch methods. These process entities from a `Stream` in configurable batch sizes, keeping memory usage constant regardless of the total number of entities.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// Read from a CSV file and insert in batches of 500.
val entityStream = csvReader.lines()
    .map { line -> parseUser(line) }

orm.entity(User::class).insert(entityStream, batchSize = 500)
```

For imports where auto-generated primary keys should be ignored (e.g., migrating data with existing IDs):

```kotlin
orm.entity(User::class).insert(entityStream, batchSize = 500, ignoreAutoGenerate = true)
```

</TabItem>
<TabItem value="java" label="Java">

```java
// Read from a CSV file and insert in batches of 500.
Stream<User> entityStream = csvReader.lines()
    .map(line -> parseUser(line));

orm.entity(User.class).insert(entityStream, 500);
```

For imports where auto-generated primary keys should be ignored (e.g., migrating data with existing IDs):

```java
orm.entity(User.class).insert(entityStream, 500, true);
```

</TabItem>
</Tabs>

The streaming API processes entities lazily: only one batch is held in memory at a time. This makes it suitable for importing millions of rows without running out of memory.

---

## Row-Level Security

Row-level security restricts which rows a user can access based on their identity or role. Storm does not provide built-in row-level security, but you can implement it using entity callbacks and the SQL interceptor.

### Via Entity Callbacks

Use a callback to enforce read-level security by filtering or rejecting unauthorized access:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
class TenantIsolationCallback : EntityCallback<TenantEntity<*>> {

    override fun beforeInsert(entity: TenantEntity<*>): TenantEntity<*> {
        val currentTenant = TenantContext.current()
        if (entity.tenantId != currentTenant) {
            throw PersistenceException("Cannot insert entity for tenant ${entity.tenantId}")
        }
        return entity
    }

    override fun beforeUpdate(entity: TenantEntity<*>): TenantEntity<*> {
        val currentTenant = TenantContext.current()
        if (entity.tenantId != currentTenant) {
            throw PersistenceException("Cannot update entity belonging to tenant ${entity.tenantId}")
        }
        return entity
    }

    override fun beforeDelete(entity: TenantEntity<*>) {
        val currentTenant = TenantContext.current()
        if (entity.tenantId != currentTenant) {
            throw PersistenceException("Cannot delete entity belonging to tenant ${entity.tenantId}")
        }
    }
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
public class TenantIsolationCallback implements EntityCallback<TenantEntity<?>> {

    @Override
    public TenantEntity<?> beforeInsert(TenantEntity<?> entity) {
        String currentTenant = TenantContext.current();
        if (!entity.tenantId().equals(currentTenant)) {
            throw new PersistenceException("Cannot insert entity for tenant " + entity.tenantId());
        }
        return entity;
    }

    @Override
    public TenantEntity<?> beforeUpdate(TenantEntity<?> entity) {
        String currentTenant = TenantContext.current();
        if (!entity.tenantId().equals(currentTenant)) {
            throw new PersistenceException("Cannot update entity belonging to tenant " + entity.tenantId());
        }
        return entity;
    }

    @Override
    public void beforeDelete(TenantEntity<?> entity) {
        String currentTenant = TenantContext.current();
        if (!entity.tenantId().equals(currentTenant)) {
            throw new PersistenceException("Cannot delete entity belonging to tenant " + entity.tenantId());
        }
    }
}
```

</TabItem>
</Tabs>

