# Frequently Asked Questions

## General

### What databases does Storm support?

Storm works with any JDBC-compatible database. Dialect packages provide optimized support for PostgreSQL, MySQL, MariaDB, Oracle, and MS SQL Server. See [Database Dialects](dialects.md).

### Does Storm require preview features?

- **Kotlin:** No. The Kotlin API has no preview dependencies.
- **Java:** Yes. The Java API uses String Templates (JEP 430), which is a preview feature in Java 21+.
- **Core:** No. The core library (`storm-core`) has no preview dependencies.

### Can I use Storm with Spring Boot?

Yes. Storm integrates seamlessly with Spring Boot. See [Spring Integration](spring-integration.md).

### Is Storm production-ready?

Yes. Storm is used in production environments and follows semantic versioning for stable releases.

---

## Entities

### Why use records/data classes instead of regular classes?

- **Immutability:** Prevents accidental state changes
- **Simplicity:** No boilerplate getters/setters
- **Equality:** Value-based equals/hashCode by default
- **Transparency:** No hidden proxy magic

### Can I use inheritance with Storm entities?

Storm focuses on composition over inheritance. Use embedded records/data classes for shared fields. For polymorphic queries, consider using a discriminator column with separate entity types.

### How do I handle auto-generated IDs?

Use `null` (Java) or default value `0` (Kotlin) for the primary key:

```kotlin
data class User(@PK val id: Int = 0, val name: String) : Entity<Int>

val user = orm insert User(name = "Alice")  // id will be populated
```

### Can I use UUID primary keys?

Yes. Use `UUID` as the primary key type:

```kotlin
data class User(@PK val id: UUID = UUID.randomUUID(), val name: String) : Entity<UUID>
```

---

## Data Classes

### When should I use Data vs Entity vs Projection vs plain records?

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

Storm prevents N+1 queries by design. Related entities marked with `@FK` are loaded in a single query using JOINs. There's no lazy loading that triggers additional queries.

### Can I write raw SQL?

Yes. Use SQL Templates (Java) or raw query methods:

```java
orm.query(RAW."SELECT * FROM user WHERE email = \{email}")
    .getResultList(User.class);
```

### How do I handle pagination?

Use `offset()` and `limit()`:

```kotlin
val page = orm.entity(User::class)
    .select()
    .orderBy(User_.createdAt, DESC)
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

Storm doesn't store collections on entities. Query the "many" side:

```kotlin
// Instead of user.orders (not supported)
val orders = orm.findAll { Order_.user eq user }
```

### Why doesn't Storm support lazy loading?

Lazy loading causes N+1 queries and unpredictable behavior. Storm loads the full entity graph in one query. For deferred loading, use `Ref<T>`:

```kotlin
data class User(@PK val id: Int = 0, @FK val department: Ref<Department>) : Entity<Int>
```

### How do I handle circular references?

Use `Ref<T>` to break the cycle:

```kotlin
data class Employee(@PK val id: Int = 0, @FK val manager: Ref<Employee>?) : Entity<Int>
```

---

## Transactions

### How do transactions work in Kotlin?

Storm provides programmatic transaction support:

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

Yes. Storm generates efficient SQL and uses JDBC directly. Key performance features:
- Single-query entity graph loading
- Batch insert/update/delete
- Streaming for large result sets
- Connection pooling support

### How do I optimize large result sets?

Use streaming to avoid loading everything into memory:

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()
users.collect { processUser(it) }
```

### How does dirty checking work?

Storm observes entities read within a transaction. On update, it compares the current state to determine if an UPDATE is needed. See [Dirty Checking](dirty-checking.md).

---

## Troubleshooting

### My entity isn't mapping correctly

1. Check that `@PK` is present on the primary key field
2. Verify field names match database columns (or use `@DbColumn`)
3. Ensure the entity implements `Entity<T>` for repository operations

### I'm getting "column not found" errors

Storm uses snake_case by default. `birthDate` maps to `birth_date`. Use `@DbColumn` for custom mappings:

```kotlin
@DbColumn("dateOfBirth") val birthDate: LocalDate
```

### Upsert isn't working

1. Ensure you've included the dialect dependency for your database
2. Verify your table has a primary key or unique constraint
3. Pass `null` (Java) or default `0` (Kotlin) for the primary key

### Refs won't fetch

`Ref.of(Type.class, id)` creates a detached ref. Only refs loaded from the database can fetch. Use repository queries to get fetchable refs.
