# Relationships

Automatic relationship loading is a core part of Storm's design. Your data model is fully captured by immutable entity classes. When you define a foreign key, Storm automatically joins the related entity and returns complete, fully populated records in a single query.

This design enables:

- **Single-query loading.** No N+1 problems. One query returns the complete entity graph.
- **Type-safe path expressions.** Filter on joined fields with full IDE support, including auto-completion across relationships: `User_.city.name eq "Sunnyvale"`
- **Concise syntax.** No manual joins, no fetch configuration, no lazy loading surprises.
- **Predictable behavior.** What you define is what you get. The entity structure *is* the query structure.

```kotlin
// Define the relationships once
data class Country(
    @PK val code: String,
    val name: String
) : Entity<String>

data class City(
    @PK val id: Int = 0,
    val name: String,
    @FK val country: Country
) : Entity<Int>

data class User(
    @PK val id: Int = 0,
    val name: String,
    @FK val city: City      // Auto-joins City, Country, and all nested relationships
) : Entity<Int>

// Query with type-safe access to nested fields throughout the entire entity graph
val users = orm.findAll { User_.city.country.code eq "US" }

// Result: fully populated User with City and Country included
users.forEach { println("${it.name} lives in ${it.city.name}, ${it.city.country.name}") }
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
    @FK @Persist(insertable = false, updatable = false) val user: User,
    @FK @Persist(insertable = false, updatable = false) val role: Role
) : Entity<UserRolePk>
```

The `@Persist(insertable = false, updatable = false)` annotation indicates that the FK columns overlap with the composite PK columns. The FK fields are used to load the related entities, but the column values come from the PK during insert/update operations.

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

### Composite Foreign Keys

When referencing an entity with a composite primary key, Storm automatically generates multi-column join conditions:

```kotlin
// Entity with composite PK
data class UserRolePk(
    val userId: Int,
    val roleId: Int
)

data class UserRole(
    @PK val pk: UserRolePk,
    @FK val user: User,
    @FK val role: Role,
    val grantedAt: Instant
) : Entity<UserRolePk>

// Entity referencing the composite PK entity
data class AuditLog(
    @PK val id: Int = 0,
    val action: String,
    @FK val userRole: UserRole?  // References entity with composite PK
) : Entity<Int>
```

Storm generates a multi-column join condition:

```sql
LEFT JOIN user_role ur
  ON al.user_id = ur.user_id
  AND al.role_id = ur.role_id
```

**Custom column names:** Use `@DbColumn` annotations to specify custom FK column names:

```kotlin
data class AuditLog(
    @PK val id: Int = 0,
    val action: String,
    @FK @DbColumn("audit_user_id") @DbColumn("audit_role_id") val userRole: UserRole?
) : Entity<Int>
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

### Primary Key as Foreign Key

Sometimes a table's primary key is also a foreign key to another entity. This is common for:

- **Dependent one-to-one relationships** where a child entity cannot exist without its parent
- **Extension tables** that add optional data to an existing entity
- **Specialized subtypes** in a table-per-subtype inheritance strategy

Use both `@PK` and `@FK` annotations on the same field, with `generation = NONE` since the key value comes from the related entity rather than being auto-generated:

```kotlin
data class UserProfile(
    @PK(generation = NONE) @FK val user: User,  // PK is also FK to User
    val bio: String?,
    val avatarUrl: String?,
    val theme: Theme?
) : Entity<User>
```

The `generation = NONE` tells Storm that the primary key is not auto-generated—the value must be provided when inserting. This is necessary because the key comes from the related `User` entity.

**Column name resolution:** When both `@PK` and `@FK` are present, Storm resolves the column name in this order:

1. Explicit name in `@PK` (e.g., `@PK("user_profile_id")`)
2. Explicit name in `@DbColumn`
3. Foreign key naming convention (default)

For a field named `user`, the FK convention produces `user_id`. To override this, specify the name explicitly:

```kotlin
@PK("user_profile_id", generation = NONE) @FK val user: User  // Uses "user_profile_id"
```

The entity's type parameter is the related entity type (`User`), not a primitive key type. This reflects that the `UserProfile` is uniquely identified by its associated `User`.

When inserting, provide the related entity:

```kotlin
val profile = UserProfile(
    user = existingUser,
    bio = "Software developer",
    avatarUrl = null,
    theme = Theme.DARK
)
orm.insert(profile)
```

Storm extracts the primary key from the `User` entity and uses it as the value for the `user_id` column.

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
                @Nonnull @FK @Persist(insertable = false, updatable = false) User user,
                @Nonnull @FK @Persist(insertable = false, updatable = false) Role role
) implements Entity<UserRolePk> {}
```

The `@Persist(insertable = false, updatable = false)` annotation indicates that the FK columns overlap with the composite PK columns. The FK fields are used to load the related entities, but the column values come from the PK during insert/update operations.

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

### Composite Foreign Keys

When referencing an entity with a composite primary key, Storm automatically generates multi-column join conditions:

```java
// Entity with composite PK
record UserRolePk(int userId, int roleId) {}

record UserRole(@PK UserRolePk pk,
                @Nonnull @FK User user,
                @Nonnull @FK Role role,
                Instant grantedAt
) implements Entity<UserRolePk> {}

// Entity referencing the composite PK entity
record AuditLog(@PK Integer id,
                String action,
                @Nullable @FK UserRole userRole  // References entity with composite PK
) implements Entity<Integer> {}
```

Storm generates a multi-column join condition:

```sql
LEFT JOIN user_role ur
  ON al.user_id = ur.user_id
  AND al.role_id = ur.role_id
```

**Custom column names:** Use `@DbColumn` annotations to specify custom FK column names:

```java
record AuditLog(@PK Integer id,
                String action,
                @Nullable @FK @DbColumn("audit_user_id") @DbColumn("audit_role_id")
                UserRole userRole
) implements Entity<Integer> {}
```

### Self-Referential Relationships

Use `Ref` to prevent circular loading:

```java
record Employee(@PK Integer id,
                String name,
                @Nullable @FK Ref<Employee> manager  // Self-reference with Ref
) implements Entity<Integer> {}
```

### Primary Key as Foreign Key

Sometimes a table's primary key is also a foreign key to another entity. This is common for:

- **Dependent one-to-one relationships** where a child entity cannot exist without its parent
- **Extension tables** that add optional data to an existing entity
- **Specialized subtypes** in a table-per-subtype inheritance strategy

Use both `@PK` and `@FK` annotations on the same field, with `generation = NONE` since the key value comes from the related entity rather than being auto-generated:

```java
record UserProfile(@PK(generation = NONE) @FK User user,  // PK is also FK to User
                   @Nullable String bio,
                   @Nullable String avatarUrl,
                   @Nullable Theme theme
) implements Entity<User> {}
```

The `generation = NONE` tells Storm that the primary key is not auto-generated—the value must be provided when inserting. This is necessary because the key comes from the related `User` entity.

**Column name resolution:** When both `@PK` and `@FK` are present, Storm resolves the column name in this order:

1. Explicit name in `@PK` (e.g., `@PK("user_profile_id")`)
2. Explicit name in `@DbColumn`
3. Foreign key naming convention (default)

For a field named `user`, the FK convention produces `user_id`. To override this, specify the name explicitly:

```java
@PK(value = "user_profile_id", generation = NONE) @FK User user  // Uses "user_profile_id"
```

The entity's type parameter is the related entity type (`User`), not a primitive key type. This reflects that the `UserProfile` is uniquely identified by its associated `User`.

When inserting, provide the related entity:

```java
var profile = new UserProfile(existingUser, "Software developer", null, Theme.DARK);
orm.entity(UserProfile.class).insert(profile);
```

Storm extracts the primary key from the `User` entity and uses it as the value for the `user_id` column.

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
