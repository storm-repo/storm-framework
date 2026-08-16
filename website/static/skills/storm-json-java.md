---
name: storm-json-java
description: Map JSON database columns with @Json and write JSON aggregation queries in Java. Use for JSON or JSONB columns, not for serializing REST responses.
---

Help the user work with JSON columns in Storm entities using Java.
Ask: what data they want to store as JSON and whether they need JSON aggregation.

## JSON Columns

Annotate a field with `@Json` to store it as a JSON column. Storm auto-detects Jackson at runtime.

```java
record User(@PK Integer id,
            String email,
            @Json Map<String, String> preferences
) implements Entity<Integer> {}
```

## Complex Types

JSON columns can store structured domain objects, not just maps and primitives. Jackson handles records automatically without additional annotations.

```java
record Address(String street, String city, String postalCode) {}

record User(@PK Integer id,
            @Json Address address
) implements Entity<Integer> {}
```

## JSON Aggregation

Use JSON aggregation functions to load one-to-many relationships in a single query:

```java
/**
 * Query result shape: a user with their roles aggregated from JSON. Not
 * backed by a database table or view, so it is a plain record —
 * deliberately not a Data type.
 */
record RolesByUser(User user, @Json List<Role> roles) {}

var results = orm.entity(User.class)
    .select(RolesByUser.class, RAW."\{User.class}, JSON_OBJECTAGG(\{Role.class})")
    .innerJoin(UserRole.class).on(User.class)
    .groupBy(User_.id)
    .getResultList();
```

## Dependencies

Storm supports two Jackson modules for Java (add one):
- `storm-jackson2` - Jackson 2.17+ (Spring Boot 3.x)
- `storm-jackson3` - Jackson 3.0+ (Spring Boot 4+)

Storm auto-detects Jackson at runtime. Just add the dependency.

## Database Support

### Column types

When writing migrations, use the correct JSON column type for the target database:

| Database | Column Type | Notes |
|----------|-------------|-------|
| PostgreSQL | `JSONB` | Binary format, indexable |
| MySQL | `JSON` | Native JSON type |
| MariaDB | `JSON` | Alias for LONGTEXT with validation |
| Oracle | `JSON` | Native JSON (21c+) |
| MS SQL Server | `NVARCHAR(MAX)` | Stored as text |
| H2 | `CLOB` | Stored as text (test databases) |

### JSON aggregation functions

In Storm templates, `JSON_OBJECTAGG(\{Role.class})` with the entity class as single argument is valid — Storm expands `\{Role.class}` to the entity's projected columns, producing the dialect-appropriate key/value arguments. The table below shows the underlying raw SQL forms per database (relevant when writing the SQL by hand or debugging generated SQL). Always ask or detect which dialect the user is targeting:

| Database | Object aggregation | Array aggregation |
|----------|-------------------|-------------------|
| PostgreSQL | `JSON_OBJECT_AGG(key, value)` | `JSON_AGG(value)` |
| MySQL | `JSON_OBJECTAGG(key, value)` | `JSON_ARRAYAGG(value)` |
| MariaDB | `JSON_OBJECTAGG(key, value)` | `JSON_ARRAYAGG(value)` |
| Oracle | `JSON_OBJECTAGG(KEY key VALUE value)` | `JSON_ARRAYAGG(value)` |
| MS SQL Server | Manual via `FOR JSON` | Manual via `FOR JSON` |
| H2 | Not supported | Not supported |

H2 does not support JSON aggregation functions. Run tests that use JSON aggregation on the target database with `@StormTest(database = POSTGRESQL, ...)` (or `MYSQL`, `MARIADB`, `MSSQL_SERVER`, `ORACLE`; a Testcontainers-managed container, see /storm-setup), or verify only the generated SQL using `SqlCapture` without executing the query.

## Rules

- Use JSON for truly dynamic or denormalized data, not to avoid proper schema design.
- JSON aggregation is suitable for moderate-size collections (< 100 items, < 1MB). For large or unbounded collections, use separate queries.
- `@Json` fields are harder to filter and index than normalized columns. Consider query patterns before choosing JSON.
- Always check the target database dialect before writing JSON aggregation queries. The function names and syntax vary.
