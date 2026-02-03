# Getting Started

Storm offers a flexible and layered approach to database interaction. This guide will help you get up and running quickly.

Choose your language:
- **[Kotlin](#kotlin)** — Recommended. Clean DSL, coroutine support, no preview features required.
- **[Java](#java)** — Full-featured API using String Templates (preview feature).

---

## Kotlin

### Dependencies

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation("st.orm:storm-kotlin:1.8.2")
    runtimeOnly("st.orm:storm-core:1.8.2")

    // Optional: Static metamodel generation (KSP)
    ksp("st.orm:storm-metamodel-processor:1.8.2")

    // Optional: Database dialect (example: PostgreSQL)
    runtimeOnly("st.orm:storm-postgresql:1.8.2")

    // Optional: JSON support
    implementation("st.orm:storm-kotlinx-serialization:1.8.2")
}
```

### Define Entities

Entities are simple Kotlin data classes:

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

```kotlin
val orm = ORMTemplate.of(dataSource)

// Or using extension property
val orm = dataSource.orm
```

### Two Ways to Work

Storm for Kotlin offers two complementary styles. Use whichever fits your situation best—or mix them freely.

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

Process large datasets efficiently without loading everything into memory:

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
// Blocking transactions
transactionBlocking {
    val city = orm insert City(name = "New York", population = 8_300_000)
    val user = orm insert User(email = "bob@example.com", name = "Bob", city = city)
    // Commits on success, rolls back on exception
}

// Suspend transactions for coroutines
transaction {
    val city = orm insert City(name = "New York", population = 8_300_000)
    val user = orm insert User(email = "bob@example.com", name = "Bob", city = city)
}

// With options
transactionBlocking(propagation = REQUIRED, isolation = REPEATABLE_READ) {
    // ...
}

// Nested transactions with savepoints
transactionBlocking {
    orm insert User(email = "alice@example.com", name = "Alice", city = city)

    transactionBlocking(propagation = NESTED) {
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

    fun findActiveInCity(city: City): List<User> =
        findAll((User_.city eq city) and (User_.active eq true))

    fun streamByCity(city: City): Flow<User> =
        select { User_.city eq city }
}

// Get the repository
val userRepository = orm.repository<UserRepository>()
val user = userRepository.findByEmail("alice@example.com")
```

---

## Java

### Dependencies

**Maven:**

```xml
<dependencies>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-java21</artifactId>
        <version>1.8.2</version>
    </dependency>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-core</artifactId>
        <version>1.8.2</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Optional: Static metamodel generation -->
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-metamodel-processor</artifactId>
        <version>1.8.2</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
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

Entities are Java records:

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

Use Spring's `@Transactional`:

```java
@Transactional
public User createUser(String email, String name, City city) {
    return orm.entity(User.class)
        .insertAndFetch(new User(null, email, name, city));
}
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

## Next Steps

- [Entities](entities.md) — Annotations, nullability, naming conventions
- [Queries](queries.md) — Query DSL and SQL Templates
- [Relationships](relationships.md) — One-to-one, many-to-one, many-to-many
- [Repositories](repositories.md) — Repository pattern and custom methods
- [Transactions](transactions.md) — Transaction management and propagation
- [Spring Integration](spring-integration.md) — Spring Boot configuration
