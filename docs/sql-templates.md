# SQL Templates

SQL templates are the foundation of Storm. The `EntityRepository` and `ProjectionRepository` APIs are built entirely on top of SQL templates. Everything those repositories do, such as generating SELECT columns, deriving joins from `@FK` relationships, and resolving table aliases, uses the same template engine available to you directly.

Most users will interact with Storm through repositories and only use templates when they need custom queries. This page covers the template features you're most likely to use: referencing tables and columns with automatic alias resolution, and understanding how joins are derived.

For details on how query results are mapped to records, see [Hydration](hydration.md).

---

## Template Syntax

Storm uses string interpolation to inject template elements into SQL. Rather than concatenating strings or using positional placeholders, you embed type references, metamodel fields, and parameter values directly in the SQL text. Storm resolves these at compilation time into proper column lists, table aliases, and parameterized placeholders.

The syntax differs between Kotlin and Java due to language-level string interpolation support.

### Kotlin

Kotlin uses `${}` interpolation inside a lambda that provides template functions. The lambda receiver exposes the `t()` function, which is the single entry point for all template elements.

```kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${t(User_.email)} = ${t(email)}
""" }
```

The `t()` function wraps all injected elements: types, metamodel references, and parameter values.

### Java

Java uses string templates with `\{}` syntax:

```java
orm.query(RAW."""
    SELECT \{User.class}
    FROM \{User.class}
    WHERE \{User_.email} = \{email}""")
```

> **Note:** Java string templates are a preview feature. Storm for Java requires Java 21+ with preview mode enabled (`--enable-preview`). Storm will adapt to the final string template specification once it's released.

---

## Data Interface

The `Data` interface marks a record or data class as eligible for Storm's SQL generation. Without this marker, Storm treats the type as a plain container and expects you to write all SQL manually. With it, template expressions like `t(MyType::class)` in a SELECT clause expand into the full column list, and the same expression in a FROM clause generates the table name with appropriate joins for `@FK` fields.

Use `Data` for query-specific result types that do not need full repository support (insert, update, delete). If you need CRUD operations, use `Entity` or `Projection` instead, which extend `Data`.

```kotlin
data class PetWithOwner(
    val name: String,
    val birthDate: LocalDate?,
    @FK val owner: Owner
) : Data

// SQL template generates SELECT columns and joins
val pets = orm.query { """
    SELECT ${t(PetWithOwner::class)}
    FROM ${t(PetWithOwner::class)}
    WHERE ${t(Owner_.city)} = ${t(city)}
""" }.getResultList(PetWithOwner::class)
```

```java
record PetWithOwner(
    @Nonnull String name,
    @Nullable LocalDate birthDate,
    @FK Owner owner
) implements Data {}

// SQL template generates SELECT columns and joins
List<PetWithOwner> pets = orm.query(RAW."""
        SELECT \{PetWithOwner.class}
        FROM \{PetWithOwner.class}
        WHERE \{Owner_.city} = \{city}""")
    .getResultList(PetWithOwner.class);
```

**When to use:** Single-use queries where you want Storm's SQL generation, automatic joins via `@FK`, and type-safe column references.

---

## Entity and Projection

For reusable types with repository support (`findById`, `insert`, `update`, etc.), use `Entity` or `Projection`. These extend `Data` and provide full repository operations.

See [Entities](entities.md) and [Projections](projections.md) for details.

| Type | Template Support | Repository Support |
|------|------------------|-------------------|
| Plain record | No | No |
| `Data` | Yes | No |
| `Entity`/`Projection` | Yes | Yes |

For plain records with manual SQL, see [Hydration](hydration.md).

---

## Auto-Join Generation

When you use a type in both SELECT and FROM expressions, Storm automatically generates joins for `@FK` relationships. This eliminates the need to write join clauses manually.

### How Auto-Joins Work

Given these entities:

```kotlin
data class Country(
    @PK val id: Int,
    val name: String,
    val code: String
) : Entity<Int>

data class City(
    @PK val id: Int,
    val name: String,
    @FK val country: Country
) : Entity<Int>

data class User(
    @PK val id: Int,
    val email: String,
    @FK val city: City
) : Entity<Int>
```

This query:

```kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
""" }
```

Generates:

```sql
SELECT u.id, u.email, c.id, c.name, co.id, co.name, co.code
FROM user u
INNER JOIN city c ON u.city_id = c.id
INNER JOIN country co ON c.country_id = co.id
```

Storm traverses the record type graph, following `@FK` annotations to generate the necessary joins. The ON clauses are derived automatically from the foreign key relationships.

### Nullable FKs Become LEFT JOINs

When an `@FK` field is nullable, Storm generates a LEFT JOIN instead of an INNER JOIN:

```kotlin
data class User(
    @PK val id: Int,
    val email: String,
    @FK val city: City?  // Nullable FK
) : Entity<Int>
```

Generates:

```sql
SELECT u.id, u.email, c.id, c.name, co.id, co.name, co.code
FROM user u
LEFT JOIN city c ON u.city_id = c.id
LEFT JOIN country co ON c.country_id = co.id
```

Nullability propagates through the relationship chain. If `city` is nullable, all joins that depend on it (like `country` through `city`) also become LEFT JOINs.

### Join Ordering

Storm automatically orders joins so that LEFT JOINs appear after INNER JOINs. This prevents unintended filtering effects that can occur when outer joins precede inner joins.

```
FROM user u
INNER JOIN department d ON u.department_id = d.id    -- INNER joins first
INNER JOIN company co ON d.company_id = co.id
LEFT JOIN city c ON u.city_id = c.id                 -- LEFT joins last
LEFT JOIN country cn ON c.country_id = cn.id
```

### Disabling Auto-Joins

Use `from(Class, autoJoin = false)` to disable automatic join generation:

```kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(from(User::class, autoJoin = false))}
    JOIN ${t(table(City::class))} ON ${t(User_.city)} = ${t(City_.id)}
""" }
```

---

## Column References with Metamodel

Hardcoding column names as strings in SQL is error-prone: a renamed field silently breaks at runtime. Storm's compile-time metamodel eliminates this risk. For each entity or data class, the annotation processor (KSP for Kotlin, APT for Java) generates a companion class (e.g., `User_`) with a static field for every column. These fields resolve to the correct column name and table alias at template compilation time, so a renamed field causes a compile error instead of a runtime failure.

### Basic Column Reference

For an entity `User`, Storm generates `User_` with fields for each column. Use these fields anywhere you would write a column name in SQL.

```kotlin
// Reference a column in WHERE clause
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${t(User_.email)} = ${t(email)}
""" }
```

### Nested Column References

Metamodel fields support path navigation for `@FK` relationships. This lets you reference columns on joined tables without writing the join alias yourself. Storm resolves the path to the correct alias based on the auto-generated joins.

```kotlin
// Reference a column through a relationship
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${t(User_.city.country.code)} = ${t("US")}
""" }
```

This generates:

```sql
WHERE co.code = ?
```

The alias (`co`) is resolved from the auto-generated joins.

### Column in Different Contexts

Use `column()` to explicitly reference a column with alias resolution:

```kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    ORDER BY ${t(column(User_.email))}
""" }
```

---

## Common Template Elements

Most queries only need a few template elements. Here are the ones you'll use most often:

| Element | Description |
|---------|-------------|
| `t(Class)` | Type reference for SELECT columns or FROM clause |
| `t(Metamodel_)` (e.g., `t(User_.email)`) | Column reference with automatic alias resolution |
| `t(column(Metamodel))` | Explicit column reference |
| `t(table(Class))` | Table name without alias |
| `t(from(Class, autoJoin))` | FROM clause with auto-join control |
| `t(unsafe(String))` | Raw SQL (use with caution) |

For advanced use cases like batch operations, subqueries, or custom insert/update statements, Storm provides additional elements. See the `Templates` class for the full API.

---

## Examples

The following examples demonstrate common query patterns using SQL templates. Each combines multiple template features (type references, metamodel columns, parameter binding) into a complete query.

### Filtering with Metamodel

```kotlin
val users = orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${t(User_.city.country.code)} = ${t("US")}
      AND ${t(User_.email)} LIKE ${t("%@example.com")}
""" }.getResultList(User::class)
```

### Custom Joins

When auto-join does not produce the join type or condition you need, disable it with `from(Class, autoJoin = false)` and write explicit join clauses. This is common for LEFT JOINs with aggregation or joins on non-FK conditions.

```kotlin
orm.query { """
    SELECT ${t(User::class)}, COUNT(${t(Order_.id)})
    FROM ${t(from(User::class, autoJoin = false))}
    LEFT JOIN ${t(table(Order::class))} ON ${t(Order_.userId)} = ${t(User_.id)}
    GROUP BY ${t(User_.id)}
""" }
```

### Subquery

Subqueries use `column()` and `table()` to reference columns and tables without triggering auto-join generation. This keeps the subquery self-contained, with its own FROM clause and alias scope.

```kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${t(User_.id)} IN (
        SELECT ${t(column(Order_.userId))}
        FROM ${t(table(Order::class))}
        WHERE ${t(Order_.total)} > ${t(1000)}
    )
""" }
```

---

## Template Processing

Since all Storm operations are built on the SQL template engine, understanding how templates are processed helps explain Storm's performance characteristics. Whether you use repository methods like `findById()` or write custom queries, the same template engine powers every database interaction.

Storm processes templates in two distinct steps:

1. **Compilation.** The template is parsed and analyzed. Storm resolves table aliases, traverses record type graphs to determine `@FK` relationships, generates the appropriate joins, and produces a reusable SQL shape with parameter placeholders. This step involves type introspection, alias management, and SQL construction.

2. **Binding.** Parameter values are substituted into the compiled template. This step is lightweight: it simply fills in the placeholders with actual values and prepares the statement for execution.

The compilation step does the heavy lifting. It analyzes your record types, walks through nested relationships, determines which joins are needed and in what order, and assembles the final SQL structure. The binding step, by contrast, is a straightforward value substitution.

Because the template model closely mirrors SQL structure, compilation is already fast. Storm doesn't need to translate between paradigms or build complex query plans. The template essentially describes the SQL you want, and Storm fills in the details like column lists, aliases, and join conditions. This direct mapping keeps compilation overhead low even without caching.

### Compilation Caching

Storm caches compiled templates to eliminate even this small overhead on repeated queries. The cache key is based on the template structure, not the parameter values. When you execute the same query pattern with different parameter values, Storm retrieves the compiled template from the cache and only performs the binding step.

```kotlin
// First execution: full compilation + binding
userRepository.find { User_.email eq "alice@example.com" }

// Subsequent executions: cache hit, binding only
userRepository.find { User_.email eq "bob@example.com" }
userRepository.find { User_.email eq "charlie@example.com" }
```

This applies to all Storm operations. Repository methods like `findAll()`, `insert()`, and `update()` benefit from the same caching mechanism. Once a query pattern has been compiled, repeated use across your application reuses the cached compilation.

The performance improvement from caching is significant, typically 10-20x faster for cached queries compared to full compilation. For most applications, templates are compiled once during the initial requests and then served from cache for the lifetime of the application.

### Why This Matters

Traditional database latency from network round-trips and query execution is handled efficiently by modern runtimes through non-blocking IO and asynchronous operations. This means IO-bound work scales well without consuming threads or CPU cycles while waiting.

At high scale, CPU time becomes the limiting factor. A server handling thousands of requests per second needs to minimize per-request overhead. Compilation caching ensures that Storm contributes minimal CPU overhead after the initial warmup period, leaving cycles available for your application logic and allowing better utilization of your hardware.
