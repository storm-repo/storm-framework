# Database Dialects

Storm works with any JDBC-compatible database. Dialect packages provide optimized support for database-specific features like upserts, pagination, and native data types.

## Supported Databases

| Database | Dialect Package | Features |
|----------|-----------------|----------|
| PostgreSQL | `storm-postgresql` | Upsert, JSONB, arrays |
| MySQL | `storm-mysql` | Upsert, JSON |
| MariaDB | `storm-mariadb` | Upsert, JSON |
| Oracle | `storm-oracle` | Merge, sequences |
| MS SQL Server | `storm-mssqlserver` | Merge, identity columns |
| H2 | Built-in | Testing, development |

## Installation

Add the dialect dependency for your database:

### Maven

```xml
<!-- PostgreSQL -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-postgresql</artifactId>
    <version>1.8.2</version>
    <scope>runtime</scope>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mysql</artifactId>
    <version>1.8.2</version>
    <scope>runtime</scope>
</dependency>

<!-- MariaDB -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mariadb</artifactId>
    <version>1.8.2</version>
    <scope>runtime</scope>
</dependency>

<!-- Oracle -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-oracle</artifactId>
    <version>1.8.2</version>
    <scope>runtime</scope>
</dependency>

<!-- MS SQL Server -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-mssqlserver</artifactId>
    <version>1.8.2</version>
    <scope>runtime</scope>
</dependency>
```

### Gradle

```groovy
// PostgreSQL
runtimeOnly 'st.orm:storm-postgresql:1.8.2'

// MySQL
runtimeOnly 'st.orm:storm-mysql:1.8.2'

// MariaDB
runtimeOnly 'st.orm:storm-mariadb:1.8.2'

// Oracle
runtimeOnly 'st.orm:storm-oracle:1.8.2'

// MS SQL Server
runtimeOnly 'st.orm:storm-mssqlserver:1.8.2'
```

## Automatic Detection

Storm automatically detects the appropriate dialect based on the JDBC connection URL. No additional configuration is required.

## Database-Specific Features

### Upsert Support

Upsert operations require a dialect. Each database uses its native syntax:

- **PostgreSQL:** `INSERT ... ON CONFLICT DO UPDATE`
- **MySQL/MariaDB:** `INSERT ... ON DUPLICATE KEY UPDATE`
- **Oracle/MS SQL Server:** `MERGE INTO ...`

### JSON Support

PostgreSQL's JSONB and MySQL's JSON types are fully supported when using the corresponding dialect with a JSON serialization library.

## Without a Dialect

Storm works without a specific dialect package using standard SQL. However, some features may be unavailable:

- Upsert operations
- Database-specific optimizations

## Tips

1. **Always include the dialect** for production databases
2. **Use H2** for unit tests—no additional dialect needed
3. **Dialect is runtime-only** — it doesn't affect your compile-time code
