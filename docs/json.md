# JSON Support

Storm provides first-class support for JSON columns, allowing you to store and query JSON data directly in your entities.

## Installation

### Jackson (Java & Kotlin)

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-jackson</artifactId>
    <version>1.8.2</version>
</dependency>
```

```groovy
implementation 'st.orm:storm-jackson:1.8.2'
```

### Kotlinx Serialization (Kotlin)

```groovy
implementation 'st.orm:storm-kotlinx-serialization:1.8.2'
```

---

## Kotlin

### JSON Columns

Use `@Json` to map a field to a JSON column:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @Json val preferences: Map<String, String>
) : Entity<Int>
```

The `preferences` field is automatically serialized to JSON when writing and deserialized when reading.

### Complex Types

```kotlin
@Serializable  // For kotlinx.serialization
data class Address(
    val street: String,
    val city: String,
    val postalCode: String
)

data class User(
    @PK val id: Int = 0,
    val email: String,
    @Json val address: Address
) : Entity<Int>
```

### JSON Aggregation

Aggregate related entities into JSON:

```kotlin
data class RolesByUser(
    val user: User,
    @Json val roles: List<Role>
)

interface UserRepository : EntityRepository<User, Int> {

    fun getUserRoles(): List<RolesByUser> =
        select(RolesByUser::class) { "${t(User::class)}, JSON_OBJECTAGG(${t(Role::class)})" }
            .innerJoin(UserRole::class).on(User::class)
            .groupBy(User_.id)
            .resultList
}
```

---

## Java

### JSON Columns

Use `@Json` to map a field to a JSON column:

```java
record User(@PK Integer id,
            String email,
            @Json Map<String, String> preferences
) implements Entity<Integer> {}
```

The `preferences` field is automatically serialized to JSON when writing and deserialized when reading.

### Complex Types

```java
record Address(String street,
               String city,
               String postalCode) {}

record User(@PK Integer id,
            String email,
            @Json Address address
) implements Entity<Integer> {}
```

### JSON Aggregation

Aggregate related entities into JSON:

```java
record RolesByUser(User user, @Json List<Role> roles) {}

interface UserRepository extends EntityRepository<User, Integer> {

    default List<RolesByUser> getUserRoles() {
        return select(RolesByUser.class, RAW."\{User.class}, JSON_OBJECTAGG(\{Role.class})")
            .innerJoin(UserRole.class).on(User.class)
            .groupBy(User_.id)
            .getResultList();
    }
}
```

---

## Database Support

JSON storage works differently across databases:

| Database | JSON Type | Notes |
|----------|-----------|-------|
| PostgreSQL | `JSONB` | Binary format, indexable |
| MySQL | `JSON` | Native JSON type |
| MariaDB | `JSON` | Alias for LONGTEXT with validation |
| Oracle | `JSON` | Native JSON (21c+) |
| MS SQL Server | `NVARCHAR(MAX)` | Stored as text |
| H2 | `CLOB` | Stored as text |

## Use Cases

### Flexible Schema

Store dynamic attributes without schema changes:

```kotlin
data class Product(
    @PK val id: Int = 0,
    val name: String,
    @Json val attributes: Map<String, Any>  // Size, color, weight, etc.
) : Entity<Int>
```

### Denormalized Data

Avoid joins for frequently accessed related data:

```kotlin
data class Order(
    @PK val id: Int = 0,
    val orderDate: LocalDate,
    @Json val shippingAddress: Address,  // Snapshot at order time
    @Json val items: List<OrderItem>     // Denormalized for fast access
) : Entity<Int>
```

### Aggregation Results

Fetch one-to-many or many-to-many relationships in a single query using JSON aggregation.

## Tips

1. **Use for truly dynamic data** — Don't use JSON to avoid proper schema design
2. **Consider query patterns** — JSON columns are harder to filter/index
3. **Size limits** — Be aware of column size limits for large JSON documents

## JSON Aggregation Performance

JSON aggregation (`JSON_OBJECTAGG`, `JSON_ARRAYAGG`) is suitable for mappings with a **moderate size**. For larger datasets or extensive mappings, split queries into separate parts to avoid:

- Memory pressure from large JSON documents
- Slow serialization/deserialization

### When to Split Queries

**Use JSON aggregation when:**
- The aggregated collection typically has < 100 items
- The JSON payload is under ~1MB
- The data is read-heavy and benefits from single-query loading

**Split into separate queries when:**
- Collections can grow unbounded (e.g., all orders for a customer)
- You need pagination on the related data
- The aggregated data is rarely accessed

### Example: Splitting Large Relationships

Instead of aggregating all roles per user:

```kotlin
// Might be slow for users with many roles
data class RolesByUser(val user: User, @Json val roles: List<Role>)
```

Query separately:

```kotlin
// Fetch users
val users = orm.findAll<User>()

// Batch fetch roles and group by user
val rolesByUser = orm.findAll { UserRole_.user inList users }
    .groupBy({ it.user }, { it.role })
```

This approach gives you control over pagination, caching, and memory usage.
