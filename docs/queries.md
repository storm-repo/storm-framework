# Queries

Storm provides a powerful and flexible query API. Kotlin users benefit from an idiomatic DSL with multiple styles, while Java users can choose between a fluent DSL and SQL Templates.

---

## Kotlin

Storm for Kotlin offers two complementary query styles—use whichever fits best.

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
    .orderBy(User_.lastName)
    .orderBy(User_.firstName)
    .resultList
```

### Aggregation

```kotlin
data class CityCount(val city: City, val count: Long)

val counts: List<CityCount> = orm.entity(User::class)
    .select(CityCount::class) { "${t(City::class)}, COUNT(*)" }
    .groupBy(User_.city)
    .resultList
```

### Joins

```kotlin
val roles = orm.entity(Role::class)
    .select()
    .innerJoin(UserRole::class).on(Role::class)
    .whereAny(UserRole_.user eq user)
    .resultList
```

### Pagination

```kotlin
val page = orm.entity(User::class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .resultList
```

### Distinct Results

```kotlin
val cities = orm.entity(User::class)
    .select(City::class)
    .distinct()
    .resultList
```

### Streaming with Flow

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
```

### Aggregation

```java
record CityCount(City city, long count) {}

List<CityCount> counts = orm.entity(User.class)
    .select(CityCount.class, RAW."\{City.class}, COUNT(*)")
    .groupBy(User_.city)
    .getResultList();
```

### Aggregation (SQL Templates)

```java
List<CityCount> counts = orm.query(RAW."""
        SELECT \{City.class}, COUNT(*)
        FROM \{User.class}
        GROUP BY \{User_.city}""")
    .getResultList(CityCount.class);
```

### Joins

```java
List<Role> roles = orm.entity(Role.class)
    .select()
    .innerJoin(UserRole.class).on(Role.class)
    .where(UserRole_.user, EQUALS, user)
    .getResultList();
```

### Joins (SQL Templates)

```java
List<Role> roles = orm.query(RAW."""
        SELECT \{Role.class}
        FROM \{Role.class}
        INNER JOIN \{UserRole.class} ON \{UserRole_.role} = \{Role_.id}
        WHERE \{UserRole_.user} = \{user.id()}""")
    .getResultList(Role.class);
```

### Pagination

```java
List<User> page = orm.entity(User.class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .getResultList();
```

### Distinct Results

```java
List<City> cities = orm.entity(User.class)
    .select(City.class)
    .distinct()
    .getResultList();
```

### Streaming

Always close streams to release resources:

```java
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    List<String> emails = users.map(User::email).toList();
}
```

---

## Result Classes

Query result classes can be:
- **Plain records** — Storm maps columns to fields (you write all SQL)
- **`Data` implementations** — Enable SQL templates like `${t(Class::class)}`
- **`Entity`/`Projection`** — Full repository support

Choose the simplest option that meets your needs. See [SQL Templates](sql-templates.md) for details.

---

## Tips

1. **Use the metamodel** — `User_.email` catches typos at compile time
2. **Kotlin: choose your style** — Quick queries for simple cases, repository for complex operations
3. **Java: DSL or Templates** — DSL for type safety, Templates for complex SQL
4. **Entity graphs load in one query** — No N+1 problems
5. **Close Java streams** — Always use try-with-resources
