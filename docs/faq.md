# Frequently Asked Questions

## General

### What databases does Storm support?

Storm works with any JDBC-compatible database. Dialect packages provide optimized support for PostgreSQL, MySQL, MariaDB, Oracle, and MS SQL Server. See [Database Dialects](dialects.md).

### Does Storm require preview features?

- **Kotlin:** No. The Kotlin API has no preview dependencies.
- **Java:** Yes. The Java API is built on String Templates, a preview feature that is still evolving in the JDK. String Templates are the best way to write SQL that is both readable and injection-safe by design, and Storm ships with support today rather than waiting for the feature to stabilize. If you prefer a stable API right now, the Kotlin API requires no preview features. Only `storm-java21` depends on this preview feature. The Java API is production-ready from a quality perspective, but its API surface will adapt as String Templates move toward a stable release.

### Can I use Storm with Spring Boot?

Yes. Storm integrates seamlessly with Spring Boot. See [Spring Integration](spring-integration.md).

### Is Storm production-ready?

Yes. Storm is used in production environments and follows semantic versioning for stable releases.

---

## Entities

### Why use records/data classes instead of regular classes?

Storm entities are pure data carriers. They never need to intercept method calls, track dirty fields, or manage lifecycle state. Records (Java) and data classes (Kotlin) are the natural fit because the language enforces immutability and generates `equals`, `hashCode`, and `toString` for free. This eliminates an entire category of bugs related to mutable shared state, identity confusion, and missing boilerplate.

- **Immutability:** Prevents accidental state changes.
- **Simplicity:** No boilerplate getters/setters.
- **Equality:** Value-based equals/hashCode by default.
- **Transparency:** No hidden proxy magic.

### Can I use inheritance with Storm entities?

Storm focuses on composition over inheritance. Java records and Kotlin data classes cannot extend other classes (they can implement interfaces, but they cannot inherit fields). This is a language constraint, not a Storm limitation. To share fields across entities, extract them into an embedded record or data class and include it as a field.

### How do I handle auto-generated IDs?

Storm detects auto-generated IDs by checking whether the primary key is `null` (Java) or set to its default value (Kotlin). When inserting an entity with a null or default-valued primary key, Storm omits the ID from the INSERT statement and lets the database assign it. The generated ID is returned and available on the inserted instance.

```kotlin
data class User(@PK val id: Int = 0, val name: String) : Entity<Int>

val user = orm insert User(name = "Alice")  // id will be populated
```

### Can I use UUID primary keys?

Yes. Storm supports any type as a primary key, including `UUID`. When using UUIDs, you typically generate the ID on the client side rather than relying on database auto-increment. This works well for distributed systems where coordination-free ID generation is important.

```kotlin
data class User(@PK val id: UUID = UUID.randomUUID(), val name: String) : Entity<UUID>
```

---

## Data Classes

### When should I use Data vs Entity vs Projection vs plain records?

Storm provides multiple data class types to match different use cases. `Entity<ID>` is the primary type for tables you read from and write to. `Projection<ID>` maps to the same table but exposes a subset of columns, useful for read-heavy queries where you do not need the full row. `Data` is a marker interface for ad-hoc query results that span multiple tables or include computed columns; Storm can still generate SQL fragments for `Data` types. Plain records (with no Storm interface) work when you write the entire SQL yourself and just need result mapping.

| Use Case | Type | Example |
|----------|------|---------|
| Reusable types for CRUD operations | `Entity<ID>` | `User`, `Order` |
| Reusable read-only views | `Projection<ID>` | `UserSummary`, `OrderView` |
| Single-use query with SQL template support | `Data` | Ad-hoc joins with SQL generation |
| Single-use query with complete manual SQL | Plain record | Complex aggregations, CTEs |

See [SQL Templates](sql-templates.md) for details on using `Data` and plain records.

---

## Queries

### How do I prevent N+1 queries?

You do not need to take any special action. Storm prevents N+1 queries by design. When you define a relationship with `@FK`, Storm generates a single SQL query that joins the related tables and hydrates the entire entity graph from one result set. There is no lazy loading that triggers additional queries behind the scenes. If you need a reference to a related entity without loading its full graph, use `Ref<T>` to defer fetching until you explicitly call `fetch()`.

### Can I write raw SQL?

Yes. Use SQL Templates (Java) or raw query methods:

```java
orm.query(RAW."SELECT * FROM user WHERE email = \{email}")
    .getResultList(User.class);
```

### How do I handle pagination?

Storm supports offset-based pagination through `offset()` and `limit()` methods on the query builder. These translate directly to the SQL `OFFSET` and `LIMIT` clauses (or equivalent for your database dialect). For large datasets, consider keyset pagination (filtering by a cursor value) for better performance, as offset-based pagination becomes slower with increasing page numbers.

```kotlin
val page = orm.entity(User::class)
    .select()
    .orderByDescending(User_.createdAt)
    .offset(20)
    .limit(10)
    .resultList
```

### Can I use database-specific functions?

Yes. Use SQL Templates for database-specific SQL:

```java
orm.query(RAW."SELECT * FROM user WHERE LOWER(email) = LOWER(\{email})")
    .getResultList(User.class);
```

---

## Relationships

### How do I model one-to-many relationships?

Storm does not store collections on entities. This is intentional: collection fields on entities are the root cause of lazy loading, N+1 queries, and unpredictable fetch behavior in JPA. Instead, query the "many" side explicitly. This makes the database access visible in your code and gives you full control over filtering, ordering, and pagination of the related records.

```kotlin
// Instead of user.orders (not supported)
val orders = orm.findAll { Order_.user eq user }
```

### Why doesn't Storm support lazy loading?

Lazy loading requires runtime proxies that intercept method calls on entity fields. This introduces hidden database access, makes entity behavior depend on session state, and is the primary source of `LazyInitializationException` in JPA applications. Storm avoids this entirely by loading the full entity graph in one query. When you genuinely need to defer loading of a relationship (for example, a rarely-accessed large sub-graph), use `Ref<T>`. A `Ref` holds only the foreign key ID until you explicitly call `fetch()`, making the database access visible and intentional.

```kotlin
data class User(@PK val id: Int = 0, @FK val department: Ref<Department>) : Entity<Int>
```

### How do I handle circular references?

Circular references (such as an employee who references a manager, who is also an employee) would cause infinite recursion during eager loading. Use `Ref<T>` to break the cycle. The `Ref` stores only the foreign key ID, preventing Storm from recursively loading the full graph. You can fetch the referenced entity on demand when needed.

```kotlin
data class Employee(@PK val id: Int = 0, @FK val manager: Ref<Employee>?) : Entity<Int>
```

---

## Transactions

### How do transactions work in Kotlin?

Storm provides a `transaction {}` block that wraps its body in a JDBC transaction. The block commits automatically on successful completion and rolls back on any exception. You can nest transactions with propagation modes (such as `NESTED` for savepoints or `REQUIRES_NEW` for independent transactions). Inside the block, all Storm operations share the same connection and participate in the same transaction.

```kotlin
transaction {
    orm insert User(name = "Alice")
    // Commits on success, rolls back on exception
}
```

### Can I use Spring's @Transactional?

Yes. Storm participates in Spring-managed transactions automatically. Enable transaction integration for Kotlin to mix declarative and programmatic styles.

### How do I do nested transactions?

Use propagation modes:

```kotlin
transaction(propagation = REQUIRED) {
    transaction(propagation = NESTED) {
        // Creates savepoint; can rollback independently
    }
}
```

---

## Performance

### Is Storm fast?

Yes. Storm adds minimal overhead on top of JDBC. There are no runtime proxies, no bytecode enhancement, and no reflection on the hot path (when using the generated metamodel). The framework generates SQL at query build time and executes it directly through JDBC prepared statements. Key performance features:
- Single-query entity graph loading
- Batch insert/update/delete
- Streaming for large result sets
- Connection pooling support

### How do I optimize large result sets?

Loading millions of rows into a `List` consumes proportional memory and delays processing until the entire result set is fetched. Streaming processes rows one at a time as the database returns them, keeping memory usage constant regardless of result set size. In Kotlin, Storm exposes streams as `Flow`, which integrates naturally with coroutines.

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()
users.collect { processUser(it) }
```

### How does dirty checking work?

When you read an entity within a transaction, Storm stores the original field values in the entity cache. When you later call `update()` with a modified copy, Storm compares the new values against the cached original to determine which fields actually changed. In `FIELD` mode, only the changed columns appear in the UPDATE statement. In `ENTITY` mode, Storm issues a full-row update but can skip the statement entirely if nothing changed. See [Dirty Checking](dirty-checking.md) for configuration details.

---

## Troubleshooting

### My entity isn't mapping correctly

Storm maps entity fields to database columns by converting field names from camelCase to snake_case. If your schema uses a different convention, explicit column annotations are required. The most common mapping issues stem from missing annotations or name mismatches.

1. Check that `@PK` is present on the primary key field.
2. Verify field names match database columns (or use `@DbColumn`).
3. Ensure the entity implements `Entity<T>` for repository operations.

### I'm getting "column not found" errors

Storm uses snake_case by default. `birthDate` maps to `birth_date`. Use `@DbColumn` for custom mappings:

```kotlin
@DbColumn("dateOfBirth") val birthDate: LocalDate
```

### Upsert isn't working

Upsert (INSERT ... ON CONFLICT) is a database-specific feature. Storm delegates to the dialect module for your database to generate the correct SQL. Without the dialect dependency, Storm cannot produce the upsert syntax.

1. Ensure you have included the dialect dependency for your database.
2. Verify your table has a primary key or unique constraint.
3. Pass `null` (Java) or default `0` (Kotlin) for the primary key.

### Refs won't fetch

A `Ref` created manually with `Ref.of(Type.class, id)` holds only the foreign key value. It is not connected to a database session and cannot fetch the referenced entity. Only `Ref` instances loaded from the database within an active transaction have the context needed to execute the fetch query. If you need to resolve a reference by ID, use the repository's `findById()` or `getById()` method instead.

### Streams are empty or already closed

Storm's Java streams are backed by a JDBC `ResultSet`, which is tied to the database connection. The stream must be consumed within the scope that opened it. Returning an unconsumed stream from a try-with-resources block closes the underlying `ResultSet` before the caller can read any rows. Either consume the stream inside the block or ensure the caller is responsible for closing it.

```java
// Wrong -- stream closed before consumption
Stream<User> getUsers() {
    try (var users = orm.entity(User.class).selectAll()) {
        return users;  // Stream is closed when method returns
    }
}

// Right -- consume within the block
List<User> getUsers() {
    try (var users = orm.entity(User.class).selectAll()) {
        return users.toList();
    }
}
```

### How do I see the SQL Storm generates?

Use SQL interceptors to log all generated SQL:

```java
SqlInterceptor.registerGlobalObserver(sql ->
    log.debug("SQL: {}", sql.statement())
);
```

See [SQL Interceptors](interceptors.md) for scoped and targeted logging.

### Can I use Storm without Spring?

Yes. Storm has no dependency on Spring. Create an `ORMTemplate` from any JDBC `DataSource`:

```kotlin
val orm = ORMTemplate.of(dataSource)
```

Spring integration is optional via the `storm-spring` or `storm-kotlin-spring` modules.
