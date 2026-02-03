# Relationships

Automatic relationship loading is a core part of Storm's design. Your data model is fully captured by immutable entity classes. When you define a foreign key, Storm automatically joins the related entity and returns complete, fully populated records in a single query.

This design enables:

- **Single-query loading.** No N+1 problems. One query returns the complete entity graph.
- **Type-safe path expressions.** Filter on joined fields with full IDE support, including auto-completion across relationships: `User_.city.name eq "Amsterdam"`
- **Concise syntax.** No manual joins, no fetch configuration, no lazy loading surprises.
- **Predictable behavior.** What you define is what you get. The entity structure *is* the query structure.

```kotlin
// Define the relationship once
data class User(
    @PK val id: Int = 0,
    val name: String,
    @FK val city: City      // Auto-joins City and its foreign relationships
) : Entity<Int>

// Query with type-safe access to nested fields
val users = orm.findAll { User_.city.name eq "Amsterdam" }

// Result: fully populated User with City included
users.forEach { println("${it.name} lives in ${it.city.name}") }
```

All relationship types are supported through the `@FK` annotation.

---

## Kotlin

### One-to-One / Many-to-One

Use `@FK` to reference another entity:

```kotlin
data class City(
    @PK val id: Int = 0,
    val name: String,
    val population: Long
) : Entity<Int>

data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: City  // Many users belong to one city
) : Entity<Int>
```

When you query a `User`, the related `City` is automatically loaded:

```kotlin
val user = orm.find { User_.id eq userId }
println(user?.city.name)  // City is already loaded
```

### Nullable Relationships

Nullable foreign keys result in LEFT JOIN:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    @FK val city: City?  // Nullable = LEFT JOIN
) : Entity<Int>
```

### One-to-Many

Query the "many" side to get related entities:

```kotlin
// Find all users in a city
val usersInCity: List<User> = orm.findAll { User_.city eq city }
```

### Many-to-Many

Use a join entity with composite primary key:

```kotlin
data class UserRolePk(
    val userId: Int,
    val roleId: Int
)

data class UserRole(
    @PK val userRolePk: UserRolePk,
    @FK val user: User,
    @FK val role: Role
) : Entity<UserRolePk>
```

Query through the join entity:

```kotlin
// Find all roles for a user
val userRoles: List<UserRole> = orm.findAll { UserRole_.user eq user }
val roles: List<Role> = userRoles.map { it.role }

// Find all users with a specific role
val userRoles: List<UserRole> = orm.findAll { UserRole_.role eq role }
val users: List<User> = userRoles.map { it.user }
```

For more control, use explicit join queries:

```kotlin
val roles: List<Role> = orm.entity(Role::class)
    .select()
    .innerJoin(UserRole::class).on(Role::class)
    .whereAny(UserRole_.user eq user)
    .resultList
```

### Self-Referential Relationships

Use `Ref` to prevent circular loading:

```kotlin
data class Employee(
    @PK val id: Int = 0,
    val name: String,
    @FK val manager: Ref<Employee>?  // Self-reference with Ref
) : Entity<Int>
```

---

## Java

### One-to-One / Many-to-One

Use `@FK` to reference another entity:

```java
record City(@PK Integer id,
            String name,
            long population
) implements Entity<Integer> {}

record User(@PK Integer id,
            String email,
            @FK City city  // Many users belong to one city
) implements Entity<Integer> {}
```

When you query a `User`, the related `City` is automatically loaded:

```java
Optional<User> user = orm.entity(User.class)
    .select()
    .where(User_.id, EQUALS, userId)
    .getOptionalResult();

user.ifPresent(u -> System.out.println(u.city().name()));  // City is already loaded
```

### Nullable Relationships

Nullable foreign keys result in LEFT JOIN:

```java
record User(@PK Integer id,
            String email,
            @Nullable @FK City city  // Nullable = LEFT JOIN
) implements Entity<Integer> {}
```

### One-to-Many

Query the "many" side to get related entities:

```java
// Find all users in a city
List<User> usersInCity = orm.entity(User.class)
    .select()
    .where(User_.city, EQUALS, city)
    .getResultList();
```

### Many-to-Many

Use a join entity with composite primary key:

```java
record UserRolePk(int userId, int roleId) {}

record UserRole(@PK UserRolePk userRolePk,
                @Nonnull @FK User user,
                @Nonnull @FK Role role
) implements Entity<UserRolePk> {}
```

Query through the join entity:

```java
// Find all roles for a user
List<UserRole> userRoles = orm.entity(UserRole.class)
    .select()
    .where(UserRole_.user, EQUALS, user)
    .getResultList();

List<Role> roles = userRoles.stream()
    .map(UserRole::role)
    .toList();
```

For more control, use explicit join queries:

```java
List<Role> roles = orm.entity(Role.class)
    .select()
    .innerJoin(UserRole.class).on(Role.class)
    .where(UserRole_.user, EQUALS, user)
    .getResultList();
```

### Self-Referential Relationships

Use `Ref` to prevent circular loading:

```java
record Employee(@PK Integer id,
                String name,
                @Nullable @FK Ref<Employee> manager  // Self-reference with Ref
) implements Entity<Integer> {}
```

---

## Relationship Loading Behavior

Storm loads the complete reachable entity graph in a single query using JOINs, unless a relationship is explicitly broken with `Ref`:

```kotlin
data class Order(
    @PK val id: Int = 0,
    @FK val customer: Customer,
    @FK val shippingAddress: Address
) : Entity<Int>

data class Customer(
    @PK val id: Int = 0,
    val name: String,
    @FK val defaultAddress: Address
) : Entity<Int>
```

When you query `Order`:
1. `Order` is loaded
2. `Customer` is loaded (via JOIN)
3. `Address` for shipping is loaded (via JOIN)
4. `Address` for customer default is loaded (via JOIN)

All in **one SQL query**. No lazy loading surprises, no N+1 problems.

### How It Works

Storm generates a single SELECT with all necessary JOINs:

```
┌─────────────────────────────────────────────────────────────────────┐
│  SELECT o.id, o.customer_id, o.shipping_address_id,                 │
│         c.id, c.name, c.default_address_id,                         │
│         a1.id, a1.street, a1.city,                                  │
│         a2.id, a2.street, a2.city                                   │
│  FROM order o                                                       │
│  INNER JOIN customer c ON o.customer_id = c.id                      │
│  INNER JOIN address a1 ON o.shipping_address_id = a1.id             │
│  INNER JOIN address a2 ON c.default_address_id = a2.id              │
│  WHERE o.id = ?                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Result: Single row with all columns from all joined tables         │
│                                                                     │
│  Storm automatically:                                               │
│  1. Parses columns back into their respective entity types          │
│  2. Constructs the complete object graph                            │
│  3. Returns a fully populated Order with nested entities            │
└─────────────────────────────────────────────────────────────────────┘
```

Storm always uses explicit column names (never `SELECT *`), ensuring predictable results even when table schemas change.

### Entity Graph to JOIN Mapping

Storm traverses the entity graph and generates JOINs based on FK nullability:

```
Entity Graph                              Generated JOINs
─────────────                             ───────────────

┌─────────┐                               FROM order o
│  Order  │
└────┬────┘
     │
     ├──── @FK customer ──────────────►   INNER JOIN customer c
     │         (non-null)                     ON o.customer_id = c.id
     │              │
     │              └─ @FK defaultAddress ►       INNER JOIN address a2
     │                     (non-null)                 ON c.default_address_id = a2.id
     │
     └──── @FK shippingAddress? ──────►   LEFT JOIN address a1
               (nullable)                     ON o.shipping_address_id = a1.id
```

**Join type is determined by nullability:**
- Non-nullable FK → INNER JOIN (referenced entity must exist)
- Nullable FK → LEFT JOIN (referenced entity may be null)

**Nested FKs are joined transitively.** Storm follows the entire entity graph, joining each FK it encounters.

### Why Eager Loading?

Traditional ORMs use lazy loading, which causes:

| Problem | Description |
|---------|-------------|
| **N+1 queries** | Accessing a collection triggers N additional queries |
| **LazyInitializationException** | Accessing data outside transaction scope fails |
| **Unpredictable performance** | Same code has different DB load depending on access patterns |
| **Hidden complexity** | Proxied entities mask when database access occurs |

Storm's approach:

| Benefit | Description |
|---------|-------------|
| **Predictable queries** | One query per `find`/`select` operation |
| **No session required** | Entities work anywhere, no transaction scope needed |
| **Transparent behavior** | What you query is what you get |
| **Simple debugging** | Easy to trace and optimize SQL |

### Managing Graph Depth

For deep or circular relationships, use `Ref` to break the loading chain:

```kotlin
data class Category(
    @PK val id: Int = 0,
    val name: String,
    @FK val parent: Ref<Category>?  // Stops here, loads only the ID
) : Entity<Int>
```

See [Refs](refs.md) for details on lightweight references.

## Tips

1. **Keep entity graphs shallow.** Deep graphs mean large JOINs. Use `Ref` for optional or deep relationships.
2. **Query the "many" side.** For one-to-many, query the child entity with a filter on the parent.
3. **Use join entities for many-to-many.** Explicit join tables give you control over the relationship.
4. **Match nullability to your schema.** Use nullable FKs only when the database column allows NULL.
5. **Use Ref for circular references.** Prevents infinite recursion in self-referential entities.
