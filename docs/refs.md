# Refs

Refs are lightweight identifiers for entities that defer fetching until explicitly required. They optimize performance by avoiding unnecessary data retrieval and are useful for managing large object graphs.

---

## Kotlin

### Using Refs in Entities

To declare a relationship as a Ref, replace the entity type with `Ref<T>` in the field declaration. Storm stores only the foreign key column value and does not generate a JOIN for the referenced table. This reduces the width of SELECT queries and avoids loading data you may never access.

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: Ref<City>  // Lightweight reference
) : Entity<Int>
```

The `city` field contains only the foreign key ID, not the full `City` entity. Compare this with declaring `@FK val city: City`, which would load the full `City` (and its transitive `@FK` relationships) via auto-generated JOINs on every query.

### Fetching

When you need the full referenced entity, call `fetch()`. This triggers a database lookup (or cache hit) on demand, loading only the data you actually need at the point you need it.

```kotlin
val user = orm.get { User_.id eq userId }
val city: City = user.city.fetch()  // Loads from database
```

### Preventing Circular Dependencies

Without Refs, an entity that references its own type would cause infinite recursion during auto-join generation: `User` joins `User`, which joins `User`, and so on. Declaring the self-referential field as `Ref<User>` breaks the cycle. Storm stores only the foreign key and does not attempt to join the table to itself.

This pattern applies to any recursive or hierarchical data model, such as organizational trees, threaded comments, or referral chains.

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: City,
    @FK val invitedBy: Ref<User>?  // Self-reference
) : Entity<Int>
```

### Selecting Refs

When you need to collect entity identifiers without loading full rows, select refs directly. This is useful for building ID lists to pass into subsequent queries (e.g., batch lookups or IN clauses) without the memory overhead of full entity hydration.

```kotlin
val role: Role = ...
val userRefs: Flow<Ref<User>> = orm.entity(UserRole::class)
    .selectRef(User::class)
    .where(UserRole_.role eq role)
    .resultFlow
```

### Using Refs in Queries

Refs integrate directly into query filter expressions. You can pass a collection of Refs to an `inRefs` clause, which generates an `IN (...)` SQL expression using only the primary key values. This lets you chain queries efficiently: select refs from one query, then use them as filters in the next.

```kotlin
val userRefs: List<Ref<User>> = ...
val roles: List<Role> = orm.entity(UserRole::class)
    .select(Role::class)
    .distinct()
    .where(UserRole_.user inRefs userRefs)
    .resultList
```

### Creating Refs

You can create Refs programmatically from a type and ID, or extract one from an existing entity. Refs created with `Ref.of(type, id)` are detached: they carry the ID but have no connection to a database session, so calling `fetch()` on them will fail unless an active transaction with an entity cache is available.

```kotlin
// From type and ID
val userRef: Ref<User> = Ref.of(User::class.java, 42)

// From existing entity
val user: User = ...
val ref: Ref<User> = Ref.of(user)
```

### Aggregation with Refs

Refs are particularly useful in aggregation queries where you group by a foreign key. Instead of loading the full related entity for each group, you can select only the primary key as a Ref. This keeps the query lightweight while still giving you a typed identifier to use in subsequent lookups if needed.

```kotlin
data class GroupedByCity(
    val city: Ref<City>,
    val count: Long
)

val counts: Map<Ref<City>, Long> = orm.entity(User::class)
    .select(GroupedByCity::class) { "${t(select(City::class, SelectMode.PK))}, COUNT(*)" }
    .groupBy(User_.city)
    .resultList
    .associate { it.city to it.count }
```

---

## Java

### Using Refs in Entities

The Java API uses `Ref<T>` in the same way as Kotlin. Declare the record component with `Ref<City>` instead of `City` to store only the foreign key.

```java
record User(@PK Integer id,
            String email,
            @FK Ref<City> city  // Lightweight reference
) implements Entity<Integer> {}
```

The `city` field contains only the foreign key ID, not the full `City` entity.

### Fetching

Call `fetch()` to load the referenced entity on demand.

```java
Optional<User> user = orm.entity(User.class)
    .select()
    .where(User_.id, EQUALS, userId)
    .getOptionalResult();

City city = user.map(u -> u.city().fetch()).orElse(null);  // Loads from database
```

### Preventing Circular Dependencies

Refs are essential for self-referential entities:

```java
record User(@PK Integer id,
            String email,
            @FK City city,
            @Nullable @FK Ref<User> invitedBy  // Self-reference
) implements Entity<Integer> {}
```

### Selecting Refs

Selecting refs in Java returns a `List` of `Ref<T>` objects. You can also use SQL templates to achieve the same result with more control over the query structure.

```java
Role role = ...;
List<Ref<User>> users = orm.entity(UserRole.class)
    .selectRef(User.class)
    .where(UserRole_.role, EQUALS, role)
    .getResultList();
```

Using SQL Templates:

```java
List<Ref<User>> users = orm.query(RAW."""
        SELECT \{select(User.class, SelectMode.PK)}
        FROM \{UserRole.class}
        WHERE \{role}""")
    .getRefList(User.class, Integer.class);
```

### Using Refs in Queries

Refs can be used directly in where clauses:

```java
List<Ref<User>> users = ...;
List<Role> roles = orm.entity(UserRole.class)
    .select(Role.class)
    .distinct()
    .whereRef(UserRole_.user, users)
    .getResultList();
```

Using SQL Templates:

```java
List<Ref<User>> users = ...;
List<Role> roles = orm.query(RAW."""
        SELECT DISTINCT \{Role.class}
        FROM \{UserRole.class}
        WHERE \{users}""")
    .getResultList(Role.class);
```

### Creating Refs

```java
// From type and ID
Ref<User> userRef = Ref.of(User.class, 42);

// From existing entity
User user = ...;
Ref<User> ref = Ref.of(user);
```

### Aggregation with Refs

```java
record GroupedByCity(Ref<City> city, long count) {}

Map<Ref<City>, Long> counts = orm.entity(User.class)
    .select(GroupedByCity.class, RAW."\{select(City.class, SelectMode.PK)}, COUNT(*)")
    .groupBy(User_.city)
    .getResultList().stream()
    .collect(toMap(GroupedByCity::city, GroupedByCity::count));
```

Using SQL Templates:

```java
Map<Ref<City>, Long> counts = orm.query(RAW."""
        SELECT \{select(City.class, SelectMode.PK)}, COUNT(*)
        FROM \{User.class}
        GROUP BY \{User_.city}""")
    .getResultList(GroupedByCity.class).stream()
    .collect(toMap(GroupedByCity::city, GroupedByCity::count));
```

---

## Use Cases

The following patterns illustrate the main scenarios where Refs provide concrete benefits over loading full entities. The common thread is reducing the amount of data loaded from the database until the moment it is actually needed.

### Optimizing Memory

When processing large collections of entities, loading full object graphs for each row can exhaust available memory. Refs store only the entity type and primary key (typically 16-32 bytes per reference, versus hundreds of bytes or more for a fully hydrated entity with nested relationships).

```kotlin
// Instead of loading full User objects
val users: List<User> = ...  // Each User has all fields loaded

// Load only IDs
val userRefs: List<Ref<User>> = ...  // Only IDs in memory
```

### Efficient Collections

Refs implement `equals()` and `hashCode()` based on their entity type and primary key, making them reliable keys in maps and sets. This lets you build lookup structures keyed by entity identity without loading the full entity data.

```kotlin
val userScores: Map<Ref<User>, Int> = ...

// Access by ref without loading full entity
val score = userScores[Ref.of(User::class.java, userId)]
```

### Deferred Loading

Refs enable a controlled form of lazy loading without proxies or bytecode manipulation. The entity field is declared as a Ref, and the calling code decides if and when to call `fetch()`. This makes the loading decision explicit in the code rather than hidden behind an ORM proxy.

```kotlin
data class Report(
    @PK val id: Int = 0,
    @FK val author: Ref<User>,  // Don't load user automatically
    val content: String
) : Entity<Int>

// Later, when you need the author
val report = orm.find { Report_.id eq reportId }
if (needsAuthorInfo) {
    val author = report?.author?.fetch()
}
```

## Fetching Behavior

Understanding how `fetch()` resolves its target helps you predict performance and avoid runtime errors.

- `fetch()` checks the [entity cache](entity-cache.md) before querying the database. If the entity was already loaded in the current transaction, no additional query is issued.
- Multiple Refs pointing to the same entity share the cached instance within a transaction, preserving object identity.
- Calling `fetch()` on a detached Ref created with `Ref.of(type, id)` will fail unless an active transaction context is available.

## Tips

1. **Use Refs for optional relationships.** Avoid loading data you might not need.
2. **Use Refs for self-references.** Prevent circular loading in hierarchical data.
3. **Use Refs in aggregations.** Get counts by FK without loading full entities.
4. **Refs are reliable map keys.** They provide lightweight, identity-based comparison.
