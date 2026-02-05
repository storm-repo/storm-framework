# ST/ORM

[![Maven Central](https://img.shields.io/maven-central/v/st.orm/storm-core.svg)](https://central.sonatype.com/namespace/st.orm)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin 2.0+](https://img.shields.io/badge/Kotlin-2.0%2B-purple)](https://kotlinlang.org/)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/projects/jdk/21/)

**Storm** is a SQL Template and ORM framework designed for Kotlin 2.0+ and Java 21+, focusing on modernizing and simplifying database programming. By leveraging the latest features of Kotlin and Java, it enables developers to define entities and queries in a concise and readable manner, enhancing both productivity and code clarity.

**Key benefits:**

- **Easy to learn**: With a programming model similar to the Java Persistence API (JPA), developers familiar with JPA can quickly adapt to using Storm.
- **Minimal code**: Define entities with simple records/data classes and query with concise, readable syntax—no boilerplate.
- **Close to SQL**: Storm embraces SQL rather than abstracting it away, keeping you in control of your database operations.
- **Type-safe**: Storm's DSL mirrors SQL, providing a type-safe, intuitive experience that makes queries easy to write and read while reducing the risk of runtime errors.
- **Direct Database Interaction**: Storm translates method calls directly into database operations, offering a transparent and straightforward experience. It eliminates inefficiencies like the N+1 query problem for predictable and efficient interactions.
- **Stateless**: Avoids hidden complexities and "magic" with stateless, record-based entities, ensuring simplicity and eliminating lazy initialization and transaction issues downstream.
- **Performance**: Template caching, transaction-scoped entity caching, and zero-overhead dirty checking (thanks to immutability) ensure efficient database interactions. Batch processing, lazy streams, and upserts are built in.
- **Universal Database Compatibility**: Fully compatible with all SQL databases, it offers flexibility and broad applicability across various database systems.

## Why Storm?

Storm draws inspiration from established ORMs such as Hibernate, but is built from scratch around a clear design philosophy: capturing exactly what you want to do using the minimum amount of code, optimized for modern Kotlin and Java.

**Storm’s mission:** Make database development productive and enjoyable, with full developer control and high performance.

Storm embraces SQL rather than abstracting it away. It simplifies database interactions while remaining transparent, and scales from prototypes to enterprise systems.

| Traditional ORM Pain | Storm Solution |
|----------------------|----------------|
| N+1 queries from lazy loading | Entity graphs load in a single query |
| Hidden magic (proxies, implicit flush, cascades) | Stateless records—explicit, predictable behavior |
| Entity state confusion (managed/detached/transient) | Immutable records—no state to manage |
| Entities tied to session/context | Stateless records easily cached and shared across layers |
| Dirty checking via bytecode manipulation | Lightning-fast dirty checking thanks to immutability |
| Complex mapping configuration | Convention over configuration |
| Runtime query errors | Compile-time type-safe DSL |
| SQL hidden behind abstraction layers | SQL-first design—stay close to the database |

**Storm is ideal for** developers who understand that the best solutions emerge when object model and database model work in harmony. If you value a database-first approach where records naturally mirror your schema, Storm is built for you. Custom mappings are supported when needed, but the real elegance comes from alignment, not abstraction.

## Choose Your Language

Both Kotlin and Java support SQL Templates for powerful query composition. Kotlin additionally provides a type-safe DSL with infix operators for a more idiomatic experience.

### Kotlin

```kotlin
// Define an entity
data class User(
    @PK val id: Int = 0,
    val email: String,
    val name: String,
    @FK val city: City
) : Entity<Int>

// DSL—query nested properties like city.name in one go
val users = orm.findAll { User_.city.name eq "Sunnyvale" }

// Custom repository—inherits all CRUD operations, add your own queries
interface UserRepository : EntityRepository<User, Int> {
    fun findByCityName(name: String) = findAll { User_.city.name eq name }
}
val users = userRepository.findByCityName("Sunnyvale")

// Query Builder for more complex operations
val users = orm.entity(User::class)
    .select()
    .where(User_.city.name eq "Sunnyvale")
    .orderBy(User_.name)
    .resultList

// SQL Template for full control
val users = orm.query { "SELECT ${t(User::class)} FROM ${t(User::class)} WHERE ${t(User_.city.name)} = ${t(cityName)}" }
    .resultList<User>()
```

Full coroutine support with `Flow` for streaming and programmatic transactions:

```kotlin
// Streaming with Flow
val users: Flow<User> = orm.entity(User::class).selectAll()
users.collect { user -> println(user.name) }

// Programmatic transactions
transaction {
    val city = orm insert City(name = "Sunnyvale", population = 155_000)
    val user = orm insert User(email = "bob@example.com", name = "Bob", city = city)
}
```

### Java

```java
// Define an entity
record User(@PK Integer id,
            String email,
            String name,
            @FK City city
) implements Entity<Integer> {}

// Custom repository—inherits all CRUD operations, add your own queries
interface UserRepository extends EntityRepository<User, Integer> {
    default List<User> findByCityName(String name) {
        return select().where(User_.city.name, EQUALS, name).getResultList();
    }
}
List<User> users = userRepository.findByCityName("Sunnyvale");

// Query Builder for more complex operations
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.city.name, EQUALS, "Sunnyvale")
    .orderBy(User_.name)
    .getResultList();

// SQL Template for full control
List<User> users = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{User_.city.name} = \{cityName}""")
    .getResultList(User.class);
```

> **Note:** The Java API uses String Templates (JEP 430), a preview feature. The core library has no preview dependencies—only the Java API module requires preview features.

## Quick Start

### Kotlin

```kotlin
dependencies {
    implementation("st.orm:storm-kotlin:1.8.2")
    runtimeOnly("st.orm:storm-core:1.8.2")
}
```

### Java

```xml
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
```

## Documentation

### Core Concepts

Everything you need to build applications with Storm. Start with Getting Started and work through the topics as needed.

| Topic | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Installation and first steps (5 min) |
| [Entities](docs/entities.md) | Defining entities, annotations, naming (10 min) |
| [Projections](docs/projections.md) | Read-only database views (8 min) |
| [Relationships](docs/relationships.md) | One-to-one, many-to-one, many-to-many (12 min) |
| [Repositories](docs/repositories.md) | Repository pattern and custom methods (3 min) |
| [Queries](docs/queries.md) | Select, filter, aggregate, order (5 min) |
| [Metamodel](docs/metamodel.md) | Compile-time type safety (9 min) |
| [Refs](docs/refs.md) | Lazy loading and optimized references (4 min) |
| [Batch & Streaming](docs/batch-streaming.md) | Bulk operations and Flow/Stream (2 min) |
| [Upserts](docs/upserts.md) | Insert-or-update operations (3 min) |
| [JSON Support](docs/json.md) | JSON columns and aggregation (4 min) |
| [Transactions](docs/transactions.md) | Transaction management and propagation (19 min) |
| [Spring Integration](docs/spring-integration.md) | Spring Boot configuration (4 min) |
| [Database Dialects](docs/dialects.md) | Database-specific support (2 min) |

### Advanced Topics

Deep dives into Storm's internals. You don't need these to be productive, but they help you understand what happens under the hood and optimize performance.

| Topic | Description |
|-------|-------------|
| [SQL Templates](docs/sql-templates.md) | Template parameters and query generation (10 min) |
| [Hydration](docs/hydration.md) | Result mapping to records (20 min) |
| [Dirty Checking](docs/dirty-checking.md) | Update modes and change detection (25 min) |
| [Entity Cache](docs/entity-cache.md) | Transaction-scoped caching and identity (12 min) |
| [SQL Interceptors](docs/interceptors.md) | Query logging and modification (2 min) |
| [Configuration](docs/configuration.md) | System properties reference (5 min) |

### Resources

Guides for evaluating Storm and transitioning from other frameworks.

| Topic | Description |
|-------|-------------|
| [Comparison](docs/comparison.md) | Storm vs other frameworks |
| [FAQ](docs/faq.md) | Frequently asked questions |
| [Migration from JPA](docs/migration-from-jpa.md) | Transitioning from JPA/Hibernate |

## Database Support

Storm works with any JDBC-compatible database. Dialect packages provide optimized support for:

[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white)](https://mariadb.org/)
[![Oracle](https://img.shields.io/badge/Oracle-F80000?style=for-the-badge&logo=oracle&logoColor=white)](https://www.oracle.com/database/)
[![SQL Server](https://img.shields.io/badge/SQL%20Server-CC2927?style=for-the-badge&logo=microsoftsqlserver&logoColor=white)](https://www.microsoft.com/sql-server)

## Requirements

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)

Storm targets Kotlin 2.0+ and Java 21+ as minimum supported versions. These baselines will be maintained for the foreseeable future.

> **Note:** Java String Templates (JEP 430) are a preview feature. The core library has no preview dependencies.

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Storm is released under the [Apache 2.0 License](LICENSE).
