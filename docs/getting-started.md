# Getting Started

Storm is a modern SQL Template and ORM framework for Kotlin 2.0+ and Java 21+. It uses immutable records and data classes instead of proxied entities, giving you predictable behavior and type-safe queries.

This guide walks you through setup, entity definition, and basic operations.

Choose your language:
- **[Kotlin](#kotlin)** -- clean DSL, coroutine support, no preview features required.
- **[Java](#java)** -- full-featured API using String Templates (preview feature in Java 21+).

> **Note on Java String Templates:** The Java API is built on String Templates, a preview feature that is still evolving in the JDK. Storm is a forward-looking framework, and String Templates are the best way to write SQL that is both readable and injection-safe by design. Rather than wait for the feature to stabilize, Storm ships with String Template support today. If you prefer a stable API right now, the Kotlin API is fully stable and requires no preview features. Only the `storm-java21` module depends on this preview feature; the core framework and the Kotlin API are unaffected. The Java API is production-ready from a quality perspective, but its API surface will adapt as String Templates move toward a stable release.

---

## Kotlin

### Prerequisites

- JDK 21+
- Kotlin 2.0+
- A JDBC DataSource (any JDBC-compatible database)

### Dependencies

Storm provides a BOM (Bill of Materials) for centralized version management. Import the BOM once and omit version numbers from individual Storm dependencies. This prevents version mismatches between modules.

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.8.2"))

    implementation("st.orm:storm-kotlin")

    // Optional: Static metamodel generation (KSP) -- enables User_, City_, etc.
    ksp("st.orm:storm-metamodel-processor:1.8.2")

    // Optional: Database dialect (example: PostgreSQL)
    runtimeOnly("st.orm:storm-postgresql")

    // Optional: JSON support
    implementation("st.orm:storm-kotlinx-serialization")
}
```

**Maven:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>st.orm</groupId>
            <artifactId>storm-bom</artifactId>
            <version>1.8.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-kotlin</artifactId>
    </dependency>
</dependencies>
```

### Define Entities

Storm entities are plain Kotlin data classes that implement the `Entity<ID>` interface. Mark the primary key with `@PK` and foreign keys with `@FK`. Storm derives table and column names automatically (e.g., `birthDate` becomes `birth_date`), so no XML mapping or additional configuration is needed. See [Entities](entities.md) for the full set of annotations and conventions.

```kotlin
data class City(
    @PK val id: Int = 0,
    val name: String,
    val population: Long
) : Entity<Int>

data class User(
    @PK val id: Int = 0,
    val email: String,
    val name: String,
    @FK val city: City
) : Entity<Int>
```

### Create the ORM Template

The `ORMTemplate` is the central entry point for all database operations. Create one from any JDBC `DataSource`. It is thread-safe and typically created once at application startup or provided as a Spring bean.

```kotlin
val orm = ORMTemplate.of(dataSource)

// Or using extension property
val orm = dataSource.orm
```

### Two Ways to Work

Storm for Kotlin offers two complementary styles. Use whichever fits your situation best, or mix them freely.

#### Style 1: Concise Infix Operations

For quick, single-entity operations, use the infix DSL directly on the ORM template:

```kotlin
// Insert and get the entity with generated ID
val user = orm insert User(
    email = "alice@example.com",
    name = "Alice",
    city = city
)

// Update returns the updated entity
val updated = orm update user.copy(name = "Alice Johnson")

// Delete
orm delete user

// Upsert (insert or update)
val user = orm upsert User(email = "alice@example.com", name = "Alice", city = city)
```

#### Style 2: Repository Operations

For queries, filtering, and bulk operations, use the repository:

```kotlin
val users = orm.entity(User::class)

// Find by ID
val user: User? = users.findById(userId)

// Find with predicate
val user: User? = users.find { User_.email eq "alice@example.com" }

// Find all matching
val usersInCity: List<User> = users.findAll { User_.city eq city }

// Streaming with Flow
val allUsers: Flow<User> = users.selectAll()

// Count and existence checks
val count: Long = users.count()
val exists: Boolean = users.existsById(userId)

// Bulk delete
users.deleteAll()
```

### Type-Safe Queries

Build complex queries with the fluent API:

```kotlin
val users = orm.entity(User::class)

// Multiple conditions
val results = users.select()
    .where((User_.city eq city) and (User_.name like "A%"))
    .orderBy(User_.name)
    .limit(10)
    .resultList

// Joins
val results = users.select()
    .innerJoin(Order::class).on(User::class)
    .where(Order_.total greater 100.0)
    .resultList

// Aggregation
data class CityCount(val city: City, val count: Long)

val counts = users.select(CityCount::class) { "${t(City::class)}, COUNT(*)" }
    .groupBy(User_.city)
    .resultList
```

### Streaming with Flow

Kotlin's `Flow` provides lazy, backpressure-aware streaming. Storm's `selectAll()` and `select()` methods return `Flow<T>`, which means rows are fetched from the database only as you consume them. This is the preferred approach for processing large datasets without loading everything into memory:

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()

users.collect { user ->
    // Process each user
}

// Or transform
val emails: List<String> = users.map { it.email }.toList()
```

### Transactions

Storm provides full programmatic transaction control:

```kotlin
transaction {
    val city = orm insert City(name = "Sunnyvale", population = 155_000)
    val user = orm insert User(email = "bob@example.com", name = "Bob", city = city)
    // Commits on success, rolls back on exception
}

// With options
transaction(propagation = REQUIRED, isolation = REPEATABLE_READ) {
    // ...
}

// Nested transactions with savepoints
transaction {
    orm insert User(email = "alice@example.com", name = "Alice", city = city)

    transaction(propagation = NESTED) {
        orm insert User(email = "bob@example.com", name = "Bob", city = city)
        if (someCondition) setRollbackOnly()  // Only rolls back this nested block
    }

    // Alice is still inserted even if Bob's insert was rolled back
}
```

### Custom Repositories

Encapsulate domain-specific queries:

```kotlin
interface UserRepository : EntityRepository<User, Int> {

    fun findByEmail(email: String): User? =
        find { User_.email eq email }

    fun findByNameInCity(name: String, city: City): List<User> =
        findAll((User_.city eq city) and (User_.name eq name))

    fun streamByCity(city: City): Flow<User> =
        select { User_.city eq city }
}

// Get the repository
val userRepository = orm.repository<UserRepository>()
val user = userRepository.findByEmail("alice@example.com")
```

---

## Java

### Prerequisites

- JDK 21+
- Maven 3.9+ or Gradle 8+
- `--enable-preview` compiler flag (required for String Templates)

### Dependencies

Import the BOM for centralized version management (see [Kotlin section](#dependencies) above for details), then add the Java module:

**Maven:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>st.orm</groupId>
            <artifactId>storm-bom</artifactId>
            <version>1.8.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-java21</artifactId>
    </dependency>

    <!-- Optional: Static metamodel generation -- enables User_, City_, etc. -->
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-metamodel-processor</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.8.2"))
    implementation("st.orm:storm-java21")

    // Optional: Static metamodel generation
    annotationProcessor("st.orm:storm-metamodel-processor:1.8.2")
}
```

### Enable Preview Features

The Java API uses String Templates (JEP 430), a preview feature:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>21</release>
        <compilerArgs>
            <arg>--enable-preview</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### Define Entities

Storm entities are Java records that implement the `Entity<ID>` interface. Mark the primary key with `@PK` and foreign keys with `@FK`. Storm converts camelCase field names to snake_case column names automatically. See [Entities](entities.md) for the complete annotation reference.

```java
record City(@PK Integer id,
            String name,
            long population
) implements Entity<Integer> {}

record User(@PK Integer id,
            String email,
            String name,
            @FK City city
) implements Entity<Integer> {}
```

### Create the ORM Template

Create an `ORMTemplate` from any JDBC `DataSource`. This is the central entry point for all Storm operations and is thread-safe.

```java
var orm = ORMTemplate.of(dataSource);
```

### CRUD Operations

```java
var users = orm.entity(User.class);

// Create
var user = users.insertAndFetch(new User(null, "alice@example.com", "Alice", city));

// Read
Optional<User> found = users.findById(user.id());

// Read with filter
List<User> usersInCity = users.select()
    .where(User_.city, EQUALS, city)
    .getResultList();

// Update
users.update(new User(user.id(), "alice@example.com", "Alice Johnson", user.city()));

// Delete
users.delete(user);
```

### Type-Safe Queries

```java
var users = orm.entity(User.class);

// Multiple conditions
List<User> results = users.select()
    .where(it -> it.where(User_.city, EQUALS, city)
            .and(it.where(User_.name, LIKE, "A%")))
    .orderBy(User_.name)
    .limit(10)
    .getResultList();
```

### SQL Templates

For complex queries, use String Templates:

```java
List<User> users = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{User_.city} = \{city}
        ORDER BY \{User_.name}""")
    .getResultList(User.class);
```

### Streaming

Always close streams to release resources:

```java
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    List<String> emails = users.map(User::email).toList();
}
```

### Transactions

With Spring's `@Transactional`:

```java
@Transactional
public User createUser(String email, String name, City city) {
    return orm.entity(User.class)
        .insertAndFetch(new User(null, email, name, city));
}
```

Or programmatically (without Spring):

```java
orm.transaction(() -> {
    var city = orm.entity(City.class).insertAndFetch(
        new City(null, "Sunnyvale", 155_000));
    var user = orm.entity(User.class).insertAndFetch(
        new User(null, "bob@example.com", "Bob", city));
    // Commits on success, rolls back on exception
});
```

### Custom Repositories

```java
interface UserRepository extends EntityRepository<User, Integer> {

    default Optional<User> findByEmail(String email) {
        return select()
            .where(User_.email, EQUALS, email)
            .getOptionalResult();
    }
}

// Get the repository
UserRepository userRepository = orm.repository(UserRepository.class);
```

---

## What's Next

Now that you have the basics, explore the features that match your needs:

**Core Concepts:**
- [Entities](entities.md) -- annotations, nullability, naming conventions
- [Queries](queries.md) -- query DSL, filtering, joins, aggregation
- [Relationships](relationships.md) -- one-to-one, many-to-one, many-to-many
- [Repositories](repositories.md) -- custom repository pattern

**Operations:**
- [Transactions](transactions.md) -- transaction management and propagation
- [Upserts](upserts.md) -- insert-or-update operations
- [Batch Processing & Streaming](batch-streaming.md) -- bulk operations and large datasets
- [Dirty Checking](dirty-checking.md) -- automatic change detection on update

**Integration:**
- [Spring Integration](spring-integration.md) -- Spring Boot configuration and DI
- [JSON Support](json.md) -- JSON columns and aggregation
- [Database Dialects](dialects.md) -- database-specific features

**Advanced:**
- [Refs](refs.md) -- lightweight entity references for deferred loading
- [Projections](projections.md) -- read-only views of entities
- [SQL Templates](sql-templates.md) -- raw SQL with type safety
- [Metamodel](metamodel.md) -- compile-time type-safe field references

**Migration:**
- [Migration from JPA](migration-from-jpa.md) -- step-by-step guide
- [Storm vs Other Frameworks](comparison.md) -- feature comparison
