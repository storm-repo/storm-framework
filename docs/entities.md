# Entities

Storm entities are simple data classes that map to database tables. By default, Storm applies sensible naming conventions to map entity fields to database columns automatically.

---

## Kotlin

### Defining Entities

Use Kotlin data classes with the `Entity` interface:

```kotlin
data class City(
    @PK val id: Int = 0,
    val name: String,
    val population: Long
) : Entity<Int>

data class User(
    @PK val id: Int = 0,
    val email: String,
    val birthDate: LocalDate,
    val street: String,
    val postalCode: String?,
    @FK val city: City
) : Entity<Int>
```

### Nullability

Kotlin's type system maps directly to Storm's null handling. A non-nullable field produces an `INNER JOIN` for foreign keys and a `NOT NULL` expectation for columns. A nullable field produces a `LEFT JOIN` for foreign keys and allows `NULL` values from the database. This means your entity definition fully describes the expected schema constraints.

Use nullable types (`?`) to indicate nullable fields:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,           // Non-nullable
    val birthDate: LocalDate,    // Non-nullable
    val postalCode: String?,     // Nullable
    @FK val city: City?          // Nullable (results in LEFT JOIN)
) : Entity<Int>
```

### Enumerations

Storm persists enum values as their `name()` string by default, which is readable and resilient to reordering. If storage efficiency is a priority or your schema uses integer columns for enums, you can switch to ordinal storage with `@DbEnum(ORDINAL)`. Be aware that ordinal storage is sensitive to the order of enum constants: adding or reordering values will break existing data.

Enums are stored by their name by default:

```kotlin
enum class RoleType {
    USER,
    ADMIN
}

data class Role(
    @PK val id: Int = 0,
    val name: String,
    val type: RoleType  // Stored as "USER" or "ADMIN"
) : Entity<Int>
```

To store by ordinal:

```kotlin
data class Role(
    @PK val id: Int = 0,
    val name: String,
    @DbEnum(ORDINAL) val type: RoleType  // Stored as 0 or 1
) : Entity<Int>
```

### Versioning (Optimistic Locking)

Optimistic locking prevents lost updates when multiple users or threads modify the same record concurrently. Storm checks the version value during updates: if another transaction has already changed the row, the update fails with an exception rather than silently overwriting the other change. You can use either an integer counter or a timestamp.

Use `@Version` for optimistic locking:

```kotlin
data class Owner(
    @PK val id: Int = 0,
    val firstName: String,
    val lastName: String,
    @Version val version: Int
) : Entity<Int>
```

Timestamps are also supported:

```kotlin
data class Visit(
    @PK val id: Int = 0,
    val visitDate: LocalDate,
    val description: String? = null,
    @FK val pet: Pet,
    @Version val timestamp: Instant?
) : Entity<Int>
```

### Non-Updatable Fields

Some fields should be set once at creation and never changed by the application, such as creation timestamps, entity types, or references that define an object's identity. Marking a field with `@Persist(updatable = false)` tells Storm to include it in INSERT statements but exclude it from UPDATE statements.

Use `@Persist(updatable = false)` for fields that should only be set on insert:

```kotlin
data class Pet(
    @PK val id: Int = 0,
    val name: String,
    @Persist(updatable = false) val birthDate: LocalDate,
    @FK @Persist(updatable = false) val type: PetType,
    @FK val owner: Owner? = null
) : Entity<Int>
```

### Embedded Components

Embedded components group related fields into a reusable data class without creating a separate database table. The component's fields are stored as columns in the parent entity's table. This is useful for value objects like addresses, coordinates, or monetary amounts that appear in multiple entities.

Use data classes for embedded components:

```kotlin
data class Address(
    val street: String? = null,
    @FK val city: City? = null
)

data class Owner(
    @PK val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val address: Address,
    val telephone: String?
) : Entity<Int>
```

### Custom Table and Column Names

```kotlin
@DbTable("app_users")
data class User(
    @PK("user_id") val id: Int = 0,
    @DbColumn("email_address") val email: String,
    @FK("home_city_id") val city: City
) : Entity<Int>
```

### Composite Primary Keys

For join tables or entities whose identity is defined by a combination of columns, wrap the key fields in a separate data class and annotate it with `@PK`. Storm treats all fields in the composite key class as part of the primary key.

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

### Primary Key Generation

The `@PK` annotation supports a `generation` parameter that controls how primary key values are generated:

| Strategy | Description |
|----------|-------------|
| `IDENTITY` | Database generates the key using an identity/auto-increment column (default) |
| `SEQUENCE` | Database generates the key using a named sequence |
| `NONE` | No generation; the caller must provide the key value |

**IDENTITY (default):**

```kotlin
data class User(
    @PK val id: Int = 0,  // Database generates via auto-increment
    val name: String
) : Entity<Int>
```

When inserting, Storm omits the PK column and retrieves the generated value:

```kotlin
val user = User(name = "Alice")
val inserted = orm.insert(user)  // Returns User with generated id
```

**SEQUENCE:**

```kotlin
data class Order(
    @PK(generation = SEQUENCE, sequence = "order_seq") val id: Long = 0,
    val total: BigDecimal
) : Entity<Long>
```

Storm fetches the next value from the sequence before inserting.

**NONE:**

```kotlin
data class Country(
    @PK(generation = NONE) val code: String,  // Caller provides the value
    val name: String
) : Entity<String>
```

Use `NONE` when:
- The key is a natural key (like country codes or UUIDs)
- The key comes from an external source
- The primary key is also a foreign key (see [Primary Key as Foreign Key](relationships.md#primary-key-as-foreign-key))

---

## Java

### Defining Entities

Use Java records with the `Entity` interface:

```java
record City(@PK Integer id,
            String name,
            long population
) implements Entity<Integer> {}

record User(@PK Integer id,
            String email,
            LocalDate birthDate,
            String street,
            String postalCode,
            @FK City city
) implements Entity<Integer> {}
```

### Nullability

In Java, record components are nullable by default. Use `@Nonnull` to mark fields that must always have a value. Primitive types (`int`, `long`, etc.) are inherently non-nullable. As with Kotlin, nullability determines JOIN behavior: a non-nullable `@FK` field produces an `INNER JOIN`, while a `@Nullable` one produces a `LEFT JOIN`.

```java
record User(@PK Integer id,
            @Nonnull String email,        // Non-nullable
            @Nonnull LocalDate birthDate, // Non-nullable
            String postalCode,            // Nullable (default)
            @Nullable @FK City city       // Nullable (results in LEFT JOIN)
) implements Entity<Integer> {}
```

### Enumerations

Enums are stored by their name by default:

```java
enum RoleType {
    USER,
    ADMIN
}

record Role(@PK Integer id,
            @Nonnull String name,
            @Nonnull RoleType type  // Stored as "USER" or "ADMIN"
) implements Entity<Integer> {}
```

To store by ordinal:

```java
record Role(@PK Integer id,
            @Nonnull String name,
            @Nonnull @DbEnum(ORDINAL) RoleType type  // Stored as 0 or 1
) implements Entity<Integer> {}
```

### Versioning (Optimistic Locking)

Use `@Version` for optimistic locking:

```java
record Owner(@PK Integer id,
             @Nonnull String firstName,
             @Nonnull String lastName,
             @Version int version
) implements Entity<Integer> {}
```

Timestamps are also supported:

```java
record Visit(@PK Integer id,
             @Nonnull LocalDate visitDate,
             @Nullable String description,
             @Nonnull @FK Pet pet,
             @Version Instant timestamp
) implements Entity<Integer> {}
```

### Non-Updatable Fields

Use `@Persist(updatable = false)` for fields that should only be set on insert:

```java
record Pet(@PK Integer id,
           @Nonnull String name,
           @Nonnull @Persist(updatable = false) LocalDate birthDate,
           @Nonnull @FK @Persist(updatable = false) PetType type,
           @Nullable @FK Owner owner
) implements Entity<Integer> {}
```

### Embedded Components

Use records for embedded components:

```java
record Address(String street,
               @FK City city) {}

record Owner(@PK Integer id,
             @Nonnull String firstName,
             @Nonnull String lastName,
             @Nonnull Address address,
             @Nullable String telephone
) implements Entity<Integer> {}
```

### Custom Table and Column Names

When the database schema does not follow Storm's default camelCase-to-snake_case convention, use annotations to specify the exact names. `@DbTable` overrides the table name, `@DbColumn` overrides a column name, and the string parameter on `@PK` or `@FK` overrides their respective column names. These annotations take precedence over any configured name resolver.

```java
@DbTable("app_users")
record User(@PK("user_id") Integer id,
            @DbColumn("email_address") String email,
            @FK("home_city_id") City city
) implements Entity<Integer> {}
```

### Composite Primary Keys

Composite keys work the same as in Kotlin. Define a plain record with the key fields and use it as the `@PK` type.

```java
record UserRolePk(int userId, int roleId) {}

record UserRole(@PK UserRolePk userRolePk,
                @Nonnull @FK User user,
                @Nonnull @FK Role role
) implements Entity<UserRolePk> {}
```

### Primary Key Generation

The `@PK` annotation supports a `generation` parameter that controls how primary key values are generated:

| Strategy | Description |
|----------|-------------|
| `IDENTITY` | Database generates the key using an identity/auto-increment column (default) |
| `SEQUENCE` | Database generates the key using a named sequence |
| `NONE` | No generation; the caller must provide the key value |

**IDENTITY (default):**

```java
record User(@PK Integer id,  // Database generates via auto-increment
            @Nonnull String name
) implements Entity<Integer> {}
```

When inserting, Storm omits the PK column and retrieves the generated value:

```java
var user = new User(null, "Alice");
var inserted = orm.entity(User.class).insert(user);  // Returns User with generated id
```

**SEQUENCE:**

```java
record Order(@PK(generation = SEQUENCE, sequence = "order_seq") Long id,
             @Nonnull BigDecimal total
) implements Entity<Long> {}
```

Storm fetches the next value from the sequence before inserting.

**NONE:**

```java
record Country(@PK(generation = NONE) String code,  // Caller provides the value
               @Nonnull String name
) implements Entity<String> {}
```

Use `NONE` when:
- The key is a natural key (like country codes or UUIDs)
- The key comes from an external source
- The primary key is also a foreign key (see [Primary Key as Foreign Key](relationships.md#primary-key-as-foreign-key))

### Builder Pattern (with Lombok)

```java
@Builder(toBuilder = true)
record Pet(@PK Integer id,
           @Nonnull String name,
           @Nonnull LocalDate birthDate,
           @Nonnull @FK PetType type,
           @Nullable @FK Owner owner
) implements Entity<Integer> {}
```

---

## Column Mapping

Storm automatically maps fields to columns using these conventions:

| Entity Field | Database Column |
|--------------|-----------------|
| `id` | `id` |
| `email` | `email` |
| `birthDate` | `birth_date` |
| `postalCode` | `postal_code` |
| `city` (FK) | `city_id` |

CamelCase field names are converted to snake_case column names. Foreign keys automatically append `_id` and reference the primary key of the related entity.

---

## Naming Conventions

Storm uses pluggable name resolvers to convert Kotlin/Java names to database identifiers. By default, camelCase names are converted to snake_case.

### Name Resolvers

Storm provides three resolver types:

| Resolver | Purpose | Default Behavior |
|----------|---------|------------------|
| `TableNameResolver` | Entity/projection class → table name | `User` → `user`, `OrderItem` → `order_item` |
| `ColumnNameResolver` | Field name → column name | `birthDate` → `birth_date` |
| `ForeignKeyResolver` | FK field → column name | `city` → `city_id` |

### Default Conversion: CamelCase to Snake_Case

The default resolver converts camelCase to snake_case:

1. Convert the first character to lowercase
2. Insert an underscore before each uppercase letter and convert it to lowercase

| Field/Class | Resolved Name |
|-------------|---------------|
| `id` | `id` |
| `email` | `email` |
| `birthDate` | `birth_date` |
| `postalCode` | `postal_code` |
| `firstName` | `first_name` |
| `UserRole` | `user_role` |

For foreign keys, `_id` is appended after the conversion:

| FK Field | Resolved Column |
|----------|-----------------|
| `city` | `city_id` |
| `petType` | `pet_type_id` |
| `homeAddress` | `home_address_id` |

### Configuring Name Resolvers

Configure resolvers when creating the ORM template:

```kotlin
val orm = PreparedStatementTemplate.of(dataSource)
    .withTableNameResolver(TableNameResolver.camelCaseToSnakeCase())
    .withColumnNameResolver(ColumnNameResolver.camelCaseToSnakeCase())
    .withForeignKeyResolver(ForeignKeyResolver.camelCaseToSnakeCase())
```

### Uppercase Conversion

For databases that prefer uppercase identifiers (e.g., Oracle), wrap resolvers with `toUpperCase()`:

```kotlin
val orm = PreparedStatementTemplate.of(dataSource)
    .withTableNameResolver(TableNameResolver.toUpperCase(TableNameResolver.camelCaseToSnakeCase()))
    .withColumnNameResolver(ColumnNameResolver.toUpperCase(ColumnNameResolver.camelCaseToSnakeCase()))
    .withForeignKeyResolver(ForeignKeyResolver.toUpperCase(ForeignKeyResolver.camelCaseToSnakeCase()))
```

This produces:

| Field/Class | Resolved Name |
|-------------|---------------|
| `birthDate` | `BIRTH_DATE` |
| `User` | `USER` |
| `city` (FK) | `CITY_ID` |

### Custom Resolvers

Implement custom naming strategies using lambda expressions or by implementing the resolver interfaces.

#### Lambda-Based Configuration

```kotlin
// Identity resolver (no conversion)
val orm = PreparedStatementTemplate.of(dataSource)
    .withColumnNameResolver { field -> field.name() }

// Custom prefix for foreign keys
val orm = PreparedStatementTemplate.of(dataSource)
    .withForeignKeyResolver { field, type ->
        "fk_${ForeignKeyResolver.camelCaseToSnakeCase().resolveColumnName(field, type)}"
    }
```

#### Interface-Based Implementation

For more complex or reusable naming strategies, implement the resolver interfaces directly:

```java
public class CustomTableNameResolver implements TableNameResolver {
    @Override
    public String resolveTableName(Class<?> type) {
        // Add schema prefix based on package
        String pkg = type.getPackageName();
        String schema = pkg.contains(".admin") ? "admin" : "public";
        String tableName = TableNameResolver.camelCaseToSnakeCase()
            .resolveTableName(type);
        return schema + "." + tableName;
    }
}

public class CustomColumnNameResolver implements ColumnNameResolver {
    @Override
    public String resolveColumnName(Field field) {
        // Custom logic for specific fields
        if (field.isAnnotationPresent(Encrypted.class)) {
            return "enc_" + ColumnNameResolver.camelCaseToSnakeCase()
                .resolveColumnName(field);
        }
        return ColumnNameResolver.camelCaseToSnakeCase()
            .resolveColumnName(field);
    }
}

public class CustomForeignKeyResolver implements ForeignKeyResolver {
    @Override
    public String resolveColumnName(Field field, Class<?> targetType) {
        // Use target table name in FK column
        String targetTable = TableNameResolver.camelCaseToSnakeCase()
            .resolveTableName(targetType);
        return targetTable + "_fk";
    }
}
```

Register custom implementations:

```kotlin
val orm = PreparedStatementTemplate.of(dataSource)
    .withTableNameResolver(CustomTableNameResolver())
    .withColumnNameResolver(CustomColumnNameResolver())
    .withForeignKeyResolver(CustomForeignKeyResolver())
```

#### Global Registration

Register resolvers globally to apply across all ORM instances:

```java
// Set as default for all new ORM instances
TableNameResolver.setDefault(new CustomTableNameResolver());
ColumnNameResolver.setDefault(new CustomColumnNameResolver());
ForeignKeyResolver.setDefault(new CustomForeignKeyResolver());
```

### Per-Entity and Per-Field Overrides

Use annotations to override naming for specific entities or fields:

```kotlin
@DbTable("app_users")  // Override table name
data class User(
    @PK("user_id") val id: Int = 0,                    // Override PK column
    @DbColumn("email_address") val email: String,      // Override column
    @FK("home_city_id") val city: City                 // Override FK column
) : Entity<Int>
```

Annotations take precedence over configured resolvers.

### Identifier Escaping

Storm automatically escapes identifiers that are SQL reserved words or contain special characters. Force escaping with the `escape` parameter:

```kotlin
@DbTable("order", escape = true)  // "order" is a reserved word
data class Order(
    @PK val id: Int = 0,
    @DbColumn("select", escape = true) val select: String  // "select" is reserved
) : Entity<Int>
```

---

## Join Behavior

Nullability affects how relationships are loaded:

- **Non-nullable FK:** INNER JOIN (referenced entity must exist)
- **Nullable FK:** LEFT JOIN (referenced entity may be null)

## Entity Interface

Implementing the `Entity<ID>` interface is optional but required for using `EntityRepository` with built-in CRUD operations. The type parameter specifies the primary key type. Without this interface, you can still use Storm's SQL template features and query builder, but you lose the convenience methods like `findById`, `insert`, `update`, and `delete`. If you only need read access, consider using `Projection<ID>` instead (see [Projections](projections.md)).
