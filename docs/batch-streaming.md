# Batch Processing & Streaming

Storm supports batch processing for efficient bulk operations and streaming for memory-efficient processing of large datasets.

---

## Kotlin

### Batch Insert

Insert multiple entities efficiently:

```kotlin
val users = listOf(
    User(email = "alice@example.com", name = "Alice"),
    User(email = "bob@example.com", name = "Bob"),
    User(email = "charlie@example.com", name = "Charlie")
)

orm insert users
```

### Batch Update

```kotlin
val updatedUsers = users.map { it.copy(active = true) }
orm update updatedUsers
```

### Batch Delete

```kotlin
orm delete users

// Or delete all
orm.deleteAll<User>()
```

### Streaming with Flow

Kotlin uses `Flow` for streaming, which automatically handles resource cleanup:

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()

// Process one at a time
users.collect { user ->
    processUser(user)
}

// Transform and collect
val emails: List<String> = users
    .map { it.email }
    .toList()

// Count
val count: Int = users.count()
```

### Combining with Transactions

```kotlin
transaction {
    val users: Flow<User> = orm.selectAll<User>()
    val count = users.count()
    println("Processing $count users")
}
```

---

## Java

### Batch Insert

Insert multiple entities efficiently:

```java
List<User> users = List.of(
    new User(null, "alice@example.com", "Alice", null, null),
    new User(null, "bob@example.com", "Bob", null, null),
    new User(null, "charlie@example.com", "Charlie", null, null)
);

orm.entity(User.class).insert(users);
```

### Batch Update

```java
List<User> updatedUsers = users.stream()
    .map(u -> u.toBuilder().active(true).build())
    .toList();

orm.entity(User.class).update(updatedUsers);
```

### Batch Delete

```java
orm.entity(User.class).delete(users);
```

### Streaming with Stream

Java uses `Stream` for streaming. **Always close streams to release resources:**

```java
// Using try-with-resources
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    users.forEach(user -> processUser(user));
}

// Transform and collect
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    List<String> emails = users
        .map(User::email)
        .toList();
}

// Count
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    long count = users.count();
}
```

### Memory-Efficient Processing

Process large datasets without loading everything into memory:

```java
try (Stream<User> users = userRepository.selectAll()) {
    List<Integer> userIds = users
        .map(User::id)
        .toList();
}
```

---

## Batch Size Configuration

Storm automatically batches operations for optimal performance. Batch operations have overloaded methods that accept a batch size parameter, allowing you to control how many rows are grouped together before being sent to the database.

## Tips

1. **Always close Java streams** — Use try-with-resources to prevent resource leaks
2. **Kotlin Flow is safer** — Automatic resource management with structured concurrency
3. **Use streaming for large datasets** — Avoid loading millions of rows into memory
4. **Batch operations are automatic** — Storm handles batching internally for bulk inserts/updates/deletes
5. **Process within transactions** — Wrap large batch operations in transactions for consistency
