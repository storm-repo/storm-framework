# Queries

Storm provides a powerful and flexible query API. All queries are type-safe -- the generated metamodel (`User_`, `City_`, etc.) catches errors at compile time rather than at runtime.

Key features:
- **Compile-time checked** -- field references are validated by the metamodel
- **No string-based queries** -- no risk of typos in column names
- **Single-query loading** -- related entities load in JOINs, not N+1 queries
- **Two styles** -- quick methods for simple cases, fluent builder for complex queries

---

## Kotlin

Storm for Kotlin offers two complementary query styles -- use whichever fits best.

### Quick Queries (Direct on ORM)

For simple queries, use methods directly on the ORM template:

```kotlin
// Find single entity with predicate
val user: User? = orm.find { User_.email eq email }

// Find all matching
val users: List<User> = orm.findAll { User_.city eq city }

// Find by field value
val user: User? = orm.findBy(User_.email, email)

// Check existence
val exists: Boolean = orm.existsBy(User_.email, email)
```

### Repository Queries

For more complex operations, use the repository:

```kotlin
val users = orm.entity(User::class)

// Find by ID
val user: User? = users.findById(userId)

// Find with predicate
val user: User? = users.find { User_.email eq email }

// Find all matching
val usersInCity: List<User> = users.findAll { User_.city eq city }

// Count
val count: Long = users.count()

// Exists
val exists: Boolean = users.existsById(userId)
```

### Filtering with Predicates

Combine conditions with `and` and `or`:

```kotlin
// AND condition
val users = orm.findAll {
    (User_.city eq city) and (User_.birthDate less LocalDate.of(2000, 1, 1))
}

// OR condition
val users = orm.findAll {
    (User_.role eq adminRole) or (User_.role eq superUserRole)
}

// Complex conditions
val users = orm.entity(User::class)
    .select()
    .where(
        (User_.city eq city) and (
            (User_.role eq adminRole) or (User_.birthDate greaterOrEquals LocalDate.of(1990, 1, 1))
        )
    )
    .resultList
```

### Operators

| Operator | Description |
|----------|-------------|
| `eq` | Equals |
| `notEq` | Not equals |
| `less` | Less than |
| `lessOrEquals` | Less than or equals |
| `greater` | Greater than |
| `greaterOrEquals` | Greater than or equals |
| `like` | LIKE pattern match |
| `notLike` | NOT LIKE |
| `isNull` | IS NULL |
| `isNotNull` | IS NOT NULL |
| `inList` | IN (list) |
| `notInList` | NOT IN (list) |

```kotlin
val users = orm.findAll { User_.email like "%@example.com" }
val users = orm.findAll { User_.deletedAt.isNull() }
val users = orm.findAll { User_.role inList listOf(adminRole, userRole) }
```

### Ordering

Use `orderBy` to control result ordering. Pass multiple fields as arguments to sort by more than one column. Use `orderByDescending` for descending order on a single field. For mixed sort directions, use the template overload.

```kotlin
val users = orm.entity(User::class)
    .select()
    .orderBy(User_.name)
    .resultList

// Descending
val users = orm.entity(User::class)
    .select()
    .orderByDescending(User_.createdAt)
    .resultList

// Multiple fields
val users = orm.entity(User::class)
    .select()
    .orderBy(User_.lastName, User_.firstName)
    .resultList

// Mixed sort directions (template)
val users = orm.entity(User::class)
    .select()
    .orderBy { "${t(User_.lastName)}, ${t(User_.firstName)} DESC" }
    .resultList
```

### Aggregation

To perform GROUP BY queries with aggregate functions like COUNT, SUM, or AVG, define a result data class with the desired columns and pass a custom SELECT expression. The `t()` function generates the column list for an entity or projection type, so you do not have to enumerate columns manually.

```kotlin
data class CityCount(val city: City, val count: Long)

val counts: List<CityCount> = orm.entity(User::class)
    .select(CityCount::class) { "${t(City::class)}, COUNT(*)" }
    .groupBy(User_.city)
    .resultList
```

### Joins

Storm automatically joins entities referenced by `@FK` fields. When you need to join entities that are not directly referenced in the result type (for example, filtering through a many-to-many join table), use explicit `innerJoin` or `leftJoin` calls. The `on` clause specifies which existing entity in the query the joined table relates to.

```kotlin
val roles = orm.entity(Role::class)
    .select()
    .innerJoin(UserRole::class).on(Role::class)
    .whereAny(UserRole_.user eq user)
    .resultList
```

### Pagination

Use `offset` and `limit` for cursor-based or offset-based pagination. Always combine pagination with `orderBy` to ensure deterministic ordering across pages.

```kotlin
val page = orm.entity(User::class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .resultList
```

### Distinct Results

Add `.distinct()` to eliminate duplicate rows from the result set. This is useful when selecting a related entity type from a query that could produce duplicates due to one-to-many relationships.

```kotlin
val cities = orm.entity(User::class)
    .select(City::class)
    .distinct()
    .resultList
```

### Streaming with Flow

For large result sets, use `selectAll()` or `select()` which return a Kotlin `Flow<T>`. Rows are fetched lazily from the database as you collect, so memory usage stays constant regardless of result set size. Flow also handles resource cleanup automatically when collection completes or is cancelled.

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()

// Process each
users.collect { user -> process(user) }

// Transform and collect
val emails: List<String> = users.map { it.email }.toList()

// Count
val count: Int = users.count()
```

---

## Java

### Basic Queries (DSL)

The Java DSL uses the same `EntityRepository` interface as Kotlin. Obtain a repository with `orm.entity(Class)` and use its fluent query builder. Return types use `Optional` for single results and `List` for collections.

```java
var users = orm.entity(User.class);

// Find by ID
Optional<User> user = users.findById(userId);

// Find all matching
List<User> usersInCity = users.select()
    .where(User_.city, EQUALS, city)
    .getResultList();

// Find first matching
Optional<User> user = users.select()
    .where(User_.email, EQUALS, email)
    .getOptionalResult();

// Count
long count = users.count();
```

### SQL Templates

SQL Templates provide a SQL-like syntax with full type safety:

```java
Optional<User> user = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{User_.email} = \{email}""")
    .getOptionalResult(User.class);
```

### Filtering (DSL)

Combine conditions using the lambda-based `where` builder. The `it` parameter provides access to the condition factory, which you chain with `.and()` or `.or()` calls to compose compound predicates.

```java
// AND condition
List<User> users = orm.entity(User.class)
    .select()
    .where(it -> it.where(User_.city, EQUALS, city)
            .and(it.where(User_.birthDate, LESS_THAN, LocalDate.of(2000, 1, 1))))
    .getResultList();

// OR condition
List<User> users = orm.entity(User.class)
    .select()
    .where(it -> it.where(User_.role, EQUALS, adminRole)
            .or(it.where(User_.role, EQUALS, superUserRole)))
    .getResultList();
```

### Filtering (SQL Templates)

SQL Templates let you write SQL directly while retaining type safety. Entity references and metamodel fields are interpolated into the template, and parameter values are bound safely. This approach is well suited for queries that use database-specific syntax, CTEs, or window functions that the DSL does not cover.

```java
List<User> users = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{city}
          AND \{User_.birthDate} < \{LocalDate.of(2000, 1, 1)}""")
    .getResultList(User.class);
```

### Operators

| Operator | Description |
|----------|-------------|
| `EQUALS` | Equals |
| `NOT_EQUALS` | Not equals |
| `LESS_THAN` | Less than |
| `LESS_THAN_OR_EQUAL` | Less than or equals |
| `GREATER_THAN` | Greater than |
| `GREATER_THAN_OR_EQUAL` | Greater than or equals |
| `LIKE` | LIKE pattern match |
| `NOT_LIKE` | NOT LIKE |
| `IS_NULL` | IS NULL |
| `IS_NOT_NULL` | IS NOT NULL |
| `IN` | IN (list) |
| `NOT_IN` | NOT IN (list) |

```java
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.email, LIKE, "%@example.com")
    .getResultList();
```

### Ordering

Use `orderBy` to sort results by one or more columns. Pass multiple fields as arguments for multi-column sorting. Use `orderByDescending` for descending order on a single field, or the template overload for mixed sort directions.

```java
// Ascending (default)
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(User_.name)
    .getResultList();

// Descending
List<User> users = orm.entity(User.class)
    .select()
    .orderByDescending(User_.createdAt)
    .getResultList();

// Multiple fields
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(User_.lastName, User_.firstName)
    .getResultList();

// Mixed sort directions (template)
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(RAW."\{User_.lastName}, \{User_.firstName} DESC")
    .getResultList();
```

### Aggregation

Define a result record with the desired columns and pass a custom SELECT expression. The DSL approach uses `select(Class, template)` with `groupBy` to build the query.

```java
record CityCount(City city, long count) {}

List<CityCount> counts = orm.entity(User.class)
    .select(CityCount.class, RAW."\{City.class}, COUNT(*)")
    .groupBy(User_.city)
    .getResultList();
```

### Aggregation (SQL Templates)

For aggregation queries that involve multiple tables, CTEs, or HAVING clauses, SQL Templates give you full control over the query structure while still mapping results to typed records.

```java
List<CityCount> counts = orm.query(RAW."""
        SELECT \{City.class}, COUNT(*)
        FROM \{User.class}
        GROUP BY \{User_.city}""")
    .getResultList(CityCount.class);
```

### Joins

Storm automatically joins entities referenced by `@FK` fields. For entities not directly referenced in the result type, such as join tables in many-to-many relationships, use explicit `innerJoin` or `leftJoin` calls. The `on` clause specifies which existing entity in the query the joined table relates to.

```java
List<Role> roles = orm.entity(Role.class)
    .select()
    .innerJoin(UserRole.class).on(Role.class)
    .where(UserRole_.user, EQUALS, user)
    .getResultList();
```

### Joins (SQL Templates)

SQL Templates let you write JOIN clauses directly, which is useful when the join condition is not a simple foreign key match or when you need to join on computed expressions.

```java
List<Role> roles = orm.query(RAW."""
        SELECT \{Role.class}
        FROM \{Role.class}
        INNER JOIN \{UserRole.class} ON \{UserRole_.role} = \{Role_.id}
        WHERE \{UserRole_.user} = \{user.id()}""")
    .getResultList(Role.class);
```

### Pagination

Use `offset` and `limit` to paginate results. Always combine pagination with `orderBy` to ensure deterministic ordering across pages.

```java
List<User> page = orm.entity(User.class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .getResultList();
```

### Distinct Results

Add `.distinct()` to eliminate duplicate rows. This is useful when selecting a related entity type from a query that could produce duplicates due to one-to-many relationships.

```java
List<City> cities = orm.entity(User.class)
    .select(City.class)
    .distinct()
    .getResultList();
```

### Streaming

Java streams hold an open database cursor and JDBC resources. Unlike Kotlin's `Flow` (which handles cleanup automatically), Java `Stream` results must be explicitly closed. Always wrap them in a try-with-resources block to prevent connection leaks.

```java
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    List<String> emails = users.map(User::email).toList();
}
```

---

## Result Classes

Query result classes can be:
- **Plain records** -- Storm maps columns to fields (you write all SQL)
- **`Data` implementations** -- enable SQL template helpers like `${t(Class::class)}`
- **`Entity`/`Projection`** -- full repository support with CRUD operations

Choose the simplest option that meets your needs. See [SQL Templates](sql-templates.md) for details.

---

## Common Patterns

### Checking Existence

Use `existsBy` (Kotlin) or `.exists()` on the query builder (Java) to check whether a matching row exists without loading the full entity.

```kotlin
// Kotlin
val exists: Boolean = orm.existsBy(User_.email, email)
```

```java
// Java
boolean exists = orm.entity(User.class)
    .select()
    .where(User_.email, EQUALS, email)
    .exists();
```

### Count with Filter

Combine `where` with `count` to count rows matching a condition without loading the entities themselves. Storm translates this to a `SELECT COUNT(*)` query.

```kotlin
// Kotlin
val count: Long = orm.entity(User::class)
    .select()
    .where(User_.city eq city)
    .count
```

```java
// Java
long count = orm.entity(User.class)
    .select()
    .where(User_.city, EQUALS, city)
    .getCount();
```

### Finding a Single Result

When you expect at most one matching row, use `find` (Kotlin, returns `null` if not found) or `getOptionalResult` (Java, returns `Optional`). These methods throw if more than one row matches.

```kotlin
// Kotlin -- returns null if not found
val user: User? = orm.find { User_.email eq email }
```

```java
// Java -- returns Optional
Optional<User> user = orm.entity(User.class)
    .select()
    .where(User_.email, EQUALS, email)
    .getOptionalResult();
```

---

## Tips

1. **Use the metamodel** -- `User_.email` catches typos at compile time; see [Metamodel](metamodel.md)
2. **Kotlin: choose your style** -- quick queries (`orm.find`, `orm.findAll`) for simple cases, repository builder for complex operations
3. **Java: DSL or Templates** -- DSL for type-safe conditions, SQL Templates for complex SQL like CTEs, window functions, or database-specific features
4. **Entity graphs load in one query** -- related entities marked with `@FK` are JOINed automatically, no N+1 problems
5. **Close Java streams** -- always use try-with-resources with `Stream` results
6. **Combine conditions freely** -- use `and` / `or` in Kotlin, `it.where().and()` / `.or()` in Java to build complex predicates
