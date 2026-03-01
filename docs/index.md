---
title: Introduction
slug: /
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# ST/ORM

**Storm** is a modern, high-performance ORM for Kotlin 2.0+ and Java 21+, built around a powerful SQL template engine. It focuses on simplicity, type safety, and predictable performance through immutable models and compile-time metadata.

**Key benefits:**

- **Easy to learn**: With a programming model similar to the Java Persistence API (JPA), developers familiar with JPA can quickly adapt to using Storm.
- **Minimal code**: Define entities with simple records/data classes and query with concise, readable syntax, no boilerplate.
- **Close to SQL**: Storm embraces SQL rather than abstracting it away, keeping you in control of your database operations.
- **Type-safe**: Storm's DSL mirrors SQL, providing a type-safe, intuitive experience that makes queries easy to write and read while reducing the risk of runtime errors.
- **Direct Database Interaction**: Storm translates method calls directly into database operations, offering a transparent and straightforward experience. It eliminates inefficiencies like the N+1 query problem for predictable and efficient interactions.
- **Stateless**: Avoids hidden complexities and "magic" with stateless, record-based entities, ensuring simplicity and eliminating lazy initialization and transaction issues downstream.
- **Performance**: Template caching, transaction-scoped entity caching, and zero-overhead dirty checking (thanks to immutability) ensure efficient database interactions. Batch processing, lazy streams, and upserts are built in.
- **Universal Database Compatibility**: Fully compatible with all SQL databases, it offers flexibility and broad applicability across various database systems.

## Why Storm?

Storm draws inspiration from established ORMs such as Hibernate, but is built from scratch around a clear design philosophy: capturing exactly what you want to do using the minimum amount of code, optimized for modern Kotlin and Java

**Storm's mission:** Make database development productive and enjoyable, with full developer control and high performance.

Storm embraces SQL rather than abstracting it away. It simplifies database interactions while remaining transparent, and scales from prototypes to enterprise systems.

| Traditional ORM Pain | Storm Solution |
|----------------------|----------------|
| N+1 queries from lazy loading | Entity graphs load in a single query |
| Hidden magic (proxies, implicit flush, cascades) | Stateless records; explicit, predictable behavior |
| Entity state confusion (managed/detached/transient) | Immutable records; no state to manage |
| Entities tied to session/context | Stateless records easily cached and shared across layers |
| Dirty checking via bytecode manipulation | Lightning-fast dirty checking thanks to immutability |
| Complex mapping configuration | Convention over configuration |
| Runtime query errors | Compile-time type-safe DSL |
| SQL hidden behind abstraction layers | SQL-first design; stay close to the database |

**Storm is ideal for** developers who understand that the best solutions emerge when object model and database model work in harmony. If you value a database-first approach where records naturally mirror your schema, Storm is built for you. Custom mappings are supported when needed, but the real elegance comes from alignment, not abstraction.

## Choose Your Language

Both Kotlin and Java support SQL Templates for powerful query composition. Kotlin additionally provides a type-safe DSL with infix operators for a more idiomatic experience.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

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

</TabItem>
<TabItem value="java" label="Java">

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

</TabItem>
</Tabs>

## Quick Start

Storm provides a Bill of Materials (BOM) for centralized version management. Import the BOM once and omit version numbers from individual Storm dependencies.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin (Gradle)" default>

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.9.0"))
    implementation("st.orm:storm-kotlin")
    runtimeOnly("st.orm:storm-core")
}
```

</TabItem>
<TabItem value="java" label="Java (Maven)">

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>st.orm</groupId>
            <artifactId>storm-bom</artifactId>
            <version>1.9.0</version>
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
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-core</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

</TabItem>
</Tabs>

Ready to get started? Head to the [Getting Started](getting-started.md) guide.

## Database Support

Storm works with any JDBC-compatible database. Dialect packages provide optimized support for:

- PostgreSQL
- MySQL
- MariaDB
- Oracle
- SQL Server

## Requirements

- Kotlin 2.0+ or Java 21+
- Maven 3.9+ or Gradle 8+

## License

Storm is released under the [Apache 2.0 License](https://github.com/storm-repo/storm-framework/blob/main/LICENSE).
