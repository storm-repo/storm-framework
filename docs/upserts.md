# Upserts

Storm provides first-class support for upsert (insert-or-update) operations across all major databases. By delegating conflict resolution to the database, upserts behave predictably and handle race conditions atomically, without application-level locking or retry logic.

Use upsert to insert a record when it does not exist or update it when it does, in a single atomic database operation.

---

## Kotlin

### Upsert

```kotlin
val user = orm upsert User(
    email = "alice@example.com",
    name = "Alice",
    birthDate = LocalDate.of(1990, 5, 15),
    city = city
)
// user.id is now populated with the database-generated ID
```

If a user with matching unique constraints exists, it will be updated. Otherwise, a new user is inserted. The returned entity includes any database-generated values.

### Batch Upsert

```kotlin
val users = listOf(
    User(email = "alice@example.com", name = "Alice"),
    User(email = "bob@example.com", name = "Bob")
)

orm.upsertAll(users)
```

---

## Java

### Basic Upsert

```java
orm.entity(User.class).upsert(new User(
    "alice@example.com",
    "Alice",
    LocalDate.of(1990, 5, 15),
    city
));
```

If a user with matching unique constraints exists, it will be updated. Otherwise, a new user is inserted.

### Upsert and Fetch

Get the resulting entity with database-generated values:

```java
User user = orm.entity(User.class).upsertAndFetch(new User(
    "alice@example.com",
    "Alice",
    LocalDate.of(1990, 5, 15),
    city
));
// user.id() is now populated with the database-generated ID
```

### With Lombok Builder

```java
User user = orm.entity(User.class).upsertAndFetch(User.builder()
    .email("alice@example.com")
    .name("Alice")
    .birthDate(LocalDate.of(1990, 5, 15))
    .city(city)
    .build()
);
```

### Batch Upsert

```java
List<User> users = List.of(
    new User("alice@example.com", "Alice", null, null),
    new User("bob@example.com", "Bob", null, null)
);

orm.entity(User.class).upsert(users);
```

---

## How Upsert Works

Upsert logic uses the underlying database platform's native capabilities:

| Database | Implementation |
|----------|----------------|
| PostgreSQL | `INSERT ... ON CONFLICT DO UPDATE` |
| MySQL/MariaDB | `INSERT ... ON DUPLICATE KEY UPDATE` |
| Oracle | `MERGE INTO ...` |
| MS SQL Server | `MERGE INTO ...` |

### Notes

- **PostgreSQL** upserts target a specific conflict source (a unique constraint or index), making conflict resolution explicit and predictable.
- **MySQL/MariaDB** upserts trigger the update branch when an insert would violate the primary key **or any unique constraint**. When multiple unique constraints exist, the database decides which conflict applies.
- **Oracle** and **MS SQL Server** define upsert behavior through explicit match conditions in the `MERGE` statement.

## Requirements

1. **Database dialect** — Include the appropriate dialect dependency for your database
2. **Unique constraints** — The table must have a primary key or unique constraint for conflict detection
3. **No ID for insert** — Pass `null` (Java) or default `0` (Kotlin) for the primary key to trigger insert logic

## Tips

1. **Use upsert for idempotent operations** — Safe to retry without creating duplicates
2. **Check your constraints** — Upsert relies on unique constraints to detect conflicts
3. **Use upsertAndFetch for generated IDs** — Get the actual ID assigned by the database
4. **Include the dialect dependency** — Upsert requires database-specific SQL syntax

---

> **Java note:** The examples above assume either a secondary constructor that omits the primary key, or Lombok's `@Builder` annotation. For records, you can add a compact constructor:
> ```java
> record User(@PK Integer id, String email, String name, LocalDate birthDate, @FK City city)
>         implements Entity<Integer> {
>     public User(String email, String name, LocalDate birthDate, City city) {
>         this(null, email, name, birthDate, city);
>     }
> }
> ```
