# SQL Templates

SQL templates provide a powerful way to write type-safe SQL queries with automatic column and table generation. This page covers how to use SQL templates with different result types.

---

## Result Class Options

When using SQL templates, you can map results to different types depending on your needs:

| Scenario | Recommended Type | Why |
|----------|------------------|-----|
| Reusable types across your application | `Entity`/`Projection` | Repository support, widely shared |
| Single-use query with SQL template support | `Data` | SQL templates, automatic joins |
| Single-use query with complete manual SQL | Plain record | No overhead, full flexibility |

---

## Plain Records (Result Mapping Only)

Use a plain record when you write complete SQL and just need to map results. No interface required—Storm maps columns to fields by position or name.

```kotlin
// No interface needed - just result mapping
data class MonthlySales(
    val month: YearMonth,
    val orderCount: Long,
    val revenue: BigDecimal
)

val sales = orm.query("""
    SELECT DATE_TRUNC('month', order_date), COUNT(*), SUM(amount)
    FROM orders
    GROUP BY DATE_TRUNC('month', order_date)
""").getResultList(MonthlySales::class)
```

```java
// No interface needed - just result mapping
record MonthlySales(
    YearMonth month,
    long orderCount,
    BigDecimal revenue
) {}

List<MonthlySales> sales = orm.query(RAW."""
        SELECT DATE_TRUNC('month', order_date), COUNT(*), SUM(amount)
        FROM orders
        GROUP BY DATE_TRUNC('month', order_date)""")
    .getResultList(MonthlySales.class);
```

**When to use:** Complete control over SQL, complex aggregations, CTEs, or database-specific features.

---

## Data Interface (SQL Template Support)

Implement `Data` when you want Storm to generate SQL from your type. This enables template parameters like `${t(Class::class)}` that automatically generate SELECT columns, FROM clauses, and joins.

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
    WHERE ${Owner_.city} = ${city}
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

---

## Template Parameters

When using `Data`, `Entity`, or `Projection` types, you can use template parameters:

| Parameter | Description |
|-----------|-------------|
| `${t(Class::class)}` / `\{Class.class}` | Generates SELECT columns and FROM clause with joins |
| `${Field_}` / `\{Field_}` | References a column using the metamodel |

```kotlin
// Kotlin
orm.query { """
    SELECT ${t(User::class)}
    FROM ${t(User::class)}
    WHERE ${User_.email} = ${email}
""" }
```

```java
// Java
orm.query(RAW."""
    SELECT \{User.class}
    FROM \{User.class}
    WHERE \{User_.email} = \{email}""")
```

---

## Summary

- **Plain records**: Full SQL control, result mapping only
- **`Data` interface**: SQL template support, no repository
- **`Entity`/`Projection`**: SQL template support + repository operations
