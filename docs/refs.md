# Refs

Refs are lightweight identifiers for entities that defer fetching until explicitly required. They optimize performance by avoiding unnecessary data retrieval and are useful for managing large object graphs.

---

## Kotlin

### Using Refs in Entities

Use `Ref<T>` instead of the entity type:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: Ref<City>  // Lightweight reference
) : Entity<Int>
```

The `city` field contains only the foreign key ID, not the full `City` entity.

### Fetching

Call `fetch()` to load the referenced entity:

```kotlin
val user = orm.get { User_.id eq userId }
val city: City = user.city.fetch()  // Loads from database
```

### Preventing Circular Dependencies

Refs are essential for self-referential entities:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: City,
    @FK val invitedBy: Ref<User>?  // Self-reference
) : Entity<Int>
```

### Selecting Refs

Select only the IDs for better performance:

```kotlin
val role: Role = ...
val userRefs: Flow<Ref<User>> = orm.entity(UserRole::class)
    .selectRef(User::class)
    .where(UserRole_.role eq role)
    .resultFlow
```

### Using Refs in Queries

Refs can be used directly in where clauses:

```kotlin
val userRefs: List<Ref<User>> = ...
val roles: List<Role> = orm.entity(UserRole::class)
    .select(Role::class)
    .distinct()
    .where(UserRole_.user inRefs userRefs)
    .resultList
```

### Creating Refs

```kotlin
// From type and ID
val userRef: Ref<User> = Ref.of(User::class.java, 42)

// From existing entity
val user: User = ...
val ref: Ref<User> = Ref.of(user)
```

### Aggregation with Refs

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

Use `Ref<T>` instead of the entity type:

```java
record User(@PK Integer id,
            String email,
            @FK Ref<City> city  // Lightweight reference
) implements Entity<Integer> {}
```

The `city` field contains only the foreign key ID, not the full `City` entity.

### Fetching

Call `fetch()` to load the referenced entity:

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

Select only the IDs for better performance:

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

### Optimizing Memory

Refs store only the entity type and primary key until fetched:

```kotlin
// Instead of loading full User objects
val users: List<User> = ...  // Each User has all fields loaded

// Load only IDs
val userRefs: List<Ref<User>> = ...  // Only IDs in memory
```

### Efficient Collections

Refs work well as keys in collections:

```kotlin
val userScores: Map<Ref<User>, Int> = ...

// Access by ref without loading full entity
val score = userScores[Ref.of(User::class.java, userId)]
```

### Deferred Loading

Load data only when needed:

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

- `fetch()` checks the [entity cache](entity-cache.md) before querying the database
- Multiple Refs pointing to the same entity share the cached instance within a transaction
- Calling `fetch()` on a detached Ref created with `Ref.of(type, id)` will fail

## Tips

1. **Use Refs for optional relationships** — Avoid loading data you might not need
2. **Use Refs for self-references** — Prevent circular loading in hierarchical data
3. **Use Refs in aggregations** — Get counts by FK without loading full entities
4. **Refs are great as map keys** — Lightweight identity-based comparison
