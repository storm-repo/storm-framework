# Database Dialects

Storm works with any JDBC-compatible database using standard SQL. However, databases diverge on features like upserts, pagination, JSON handling, and native data types. Dialect packages let Storm take advantage of these database-specific capabilities while keeping your application code portable. Your entities, repositories, and queries stay the same regardless of which database you use; only the dialect dependency changes.

## Supported Databases

| Database | Dialect Package | Key Features |
|----------|-----------------|--------------|
| PostgreSQL | `storm-postgresql` | Upsert (`ON CONFLICT`), JSONB, arrays |
| MySQL | `storm-mysql` | Upsert (`ON DUPLICATE KEY`), JSON |
| MariaDB | `storm-mariadb` | Upsert (`ON DUPLICATE KEY`), JSON |
| Oracle | `storm-oracle` | Merge (`MERGE INTO`), sequences |
| MS SQL Server | `storm-mssqlserver` | Merge (`MERGE INTO`), identity columns |
| H2 | Built-in | Testing and development (no extra dependency) |

## Installation

Add the dialect dependency for your database. Dialects are runtime-only dependencies: they do not affect your compile-time code or entity definitions. Your entity classes, repositories, and queries are written against Storm's core API, not against any specific dialect. This means you can switch databases by changing a single dependency without modifying application code.

### Maven

```xml
<!-- PostgreSQL -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-postgresql</artifactId>
    <version>1.9.0</version>
    <scope>runtime</scope>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mysql</artifactId>
    <version>1.9.0</version>
    <scope>runtime</scope>
</dependency>

<!-- MariaDB -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mariadb</artifactId>
    <version>1.9.0</version>
    <scope>runtime</scope>
</dependency>

<!-- Oracle -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-oracle</artifactId>
    <version>1.9.0</version>
    <scope>runtime</scope>
</dependency>

<!-- MS SQL Server -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mssqlserver</artifactId>
    <version>1.9.0</version>
    <scope>runtime</scope>
</dependency>
```

### Gradle (Groovy DSL)

```groovy
// PostgreSQL
runtimeOnly 'st.orm:storm-postgresql:1.9.0'

// MySQL
runtimeOnly 'st.orm:storm-mysql:1.9.0'

// MariaDB
runtimeOnly 'st.orm:storm-mariadb:1.9.0'

// Oracle
runtimeOnly 'st.orm:storm-oracle:1.9.0'

// MS SQL Server
runtimeOnly 'st.orm:storm-mssqlserver:1.9.0'
```

### Gradle (Kotlin DSL)

```kotlin
// PostgreSQL
runtimeOnly("st.orm:storm-postgresql:1.9.0")

// MySQL
runtimeOnly("st.orm:storm-mysql:1.9.0")

// MariaDB
runtimeOnly("st.orm:storm-mariadb:1.9.0")

// Oracle
runtimeOnly("st.orm:storm-oracle:1.9.0")

// MS SQL Server
runtimeOnly("st.orm:storm-mssqlserver:1.9.0")
```

## Automatic Detection

Storm automatically detects the appropriate dialect based on the JDBC connection URL. No additional configuration is required. When your application starts, Storm queries the `ServiceLoader` for available dialect implementations, inspects the JDBC URL, and selects the matching dialect. This means adding or switching a dialect is purely a dependency change with no code or configuration modifications.

For example, with the connection URL `jdbc:postgresql://localhost:5432/mydb`, Storm will automatically use the PostgreSQL dialect.

## Database-Specific Features

### Upsert Support

Upsert operations are the primary reason most applications need a dialect. Without a dialect, Storm cannot generate the database-specific INSERT ... ON CONFLICT or MERGE syntax required for atomic upsert operations. Each database uses its own native syntax:

| Database | SQL Strategy | Conflict Detection |
|----------|--------------|--------------------|
| PostgreSQL | `INSERT ... ON CONFLICT DO UPDATE` | Targets a specific unique constraint or index |
| MySQL | `INSERT ... ON DUPLICATE KEY UPDATE` | Primary key or any unique constraint |
| MariaDB | `INSERT ... ON DUPLICATE KEY UPDATE` | Primary key or any unique constraint |
| Oracle | `MERGE INTO ...` | Explicit match conditions |
| MS SQL Server | `MERGE INTO ...` | Explicit match conditions |

See [Upserts](upserts.md) for usage examples.

### JSON Support

PostgreSQL's JSONB and MySQL/MariaDB's JSON types are fully supported when using the corresponding dialect with a JSON serialization library (`storm-jackson` or `storm-kotlinx-serialization`). See [JSON Support](json.md) for details.

### Database-Specific Data Types

Beyond SQL syntax differences, databases support different native data types. Dialects handle the mapping between Java/Kotlin types and database-specific types automatically, so you can use idiomatic types in your entities without worrying about the underlying storage format.

- **PostgreSQL:** JSONB, UUID, arrays, INET, CIDR
- **MySQL/MariaDB:** JSON, TINYINT for booleans, ENUM
- **Oracle:** NUMBER, CLOB, sequences for ID generation
- **MS SQL Server:** NVARCHAR, UNIQUEIDENTIFIER, IDENTITY

## Without a Dialect

Storm works without a specific dialect package by generating standard SQL. This is the typical setup during development and testing when using H2 as an in-memory database. The core framework handles entity mapping, queries, joins, transactions, streaming, dirty checking, and caching using only standard SQL. However, some features require database-specific syntax and will be unavailable without a dialect:

- **Upsert operations** -- require database-specific syntax
- **Database-specific optimizations** -- e.g., native pagination strategies

All other features -- entity mapping, queries, joins, transactions, streaming, dirty checking, and caching -- work identically regardless of dialect.

## Testing with H2

H2 is an in-memory Java SQL database that starts instantly and requires no external processes. Storm includes built-in support for H2, making it the default choice for unit tests. Because H2 runs in-process, tests start in milliseconds and do not require Docker, network access, or database installation.

```kotlin
// Kotlin
val dataSource = JdbcDataSource().apply {
    setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
}
val orm = ORMTemplate.of(dataSource)
```

```java
// Java
var dataSource = new JdbcDataSource();
dataSource.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
var orm = ORMTemplate.of(dataSource);
```

No additional dialect dependency is needed for H2. This makes it easy to write fast tests that run without Docker or external databases.

## Integration Testing with Real Databases

While H2 is excellent for fast unit tests, it does not support all database-specific features (JSONB, arrays, database-specific functions). For thorough testing, you should also run integration tests against your production database. Each dialect module includes a `docker-compose.yml` file that starts the corresponding database in a container, making integration testing straightforward. For example, to test with PostgreSQL:

```bash
cd storm-postgresql
docker-compose up -d
mvn test -pl storm-postgresql
```

## Tips

1. **Always include the dialect** for production databases to unlock all features
2. **Use H2** for unit tests -- no additional dialect needed, fast startup
3. **Dialect is runtime-only** -- it doesn't affect your compile-time code or entity definitions
4. **One dialect per application** -- Storm auto-detects the right dialect from your connection URL
5. **Test with both** -- Use H2 for fast unit tests and the production dialect for integration tests
