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

Storm stands on the shoulders of giants and draws inspiration from established ORMs, such as Hibernate. Built from scratch with a clear design philosophy—*what is the minimal amount of code required to express intent?*—Storm takes a fresh approach optimized for modern Kotlin and Java.

**Storm's mission:** Make database development productive and enjoyable while keeping developers in full control. Storm simplifies database interactions without hiding what's happening underneath. It is engineered for both rapid development and high-performance execution, scaling seamlessly from prototypes to enterprise systems. Abstracting away from SQL is explicitly a non-goal; strong database and SQL expertise is fundamental to building robust applications.

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

// Repository CRUD operations and custom queries
val user = userRepository.findByEmail("alice@example.com")

// Query Builder for more complex operations
val users = orm.entity(User::class)
    .select()
    .where(User_.city.name eq "Sunnyvale")
    .orderBy(User_.name)
    .resultList

// SQL Template for full control
val users = orm.query { "SELECT ${t(User::class)} FROM ${t(User::class)} WHERE ${User_.city.name} = $cityName" }
    .resultList<User>()
```

Full coroutine support with `Flow` for streaming and programmatic transactions:

```kotlin
transaction {
    val city = orm insert City(name = "New York", population = 8_300_000)
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

// Repository CRUD operations and custom queries
Optional<User> user = userRepository.findByEmail("alice@example.com");

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

| Topic | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Installation and first steps |
| [Entities](docs/entities.md) | Defining entities, annotations, naming |
| [Projections](docs/projections.md) | Read-only database views |
| [Relationships](docs/relationships.md) | One-to-one, many-to-one, many-to-many |
| [Queries](docs/queries.md) | Select, filter, aggregate, order |
| [Metamodel](docs/metamodel.md) | Compile-time type safety |
| [Repositories](docs/repositories.md) | Repository pattern and custom methods |
| [Refs](docs/refs.md) | Lazy loading and optimized references |
| [Batch & Streaming](docs/batch-streaming.md) | Bulk operations and Flow/Stream |
| [Upserts](docs/upserts.md) | Insert-or-update operations |
| [JSON Support](docs/json.md) | JSON columns and aggregation |
| [Transactions](docs/transactions.md) | Transaction management and propagation |
| [Dirty Checking](docs/dirty-checking.md) | Update modes and change detection |
| [Entity Cache](docs/entity-cache.md) | Transaction-scoped caching and identity |
| [SQL Templates](docs/sql-templates.md) | Template parameters and result mapping |
| [SQL Interceptors](docs/interceptors.md) | Query logging and modification |
| [Spring Integration](docs/spring-integration.md) | Spring Boot configuration |
| [Database Dialects](docs/dialects.md) | Database-specific support |
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
