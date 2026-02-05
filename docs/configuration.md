# Configuration

Storm can be configured through system properties. These properties control runtime behavior for features like dirty checking and entity caching. All properties have sensible defaults, so configuration is optional.

---

## System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `storm.update.defaultMode` | `ENTITY` | Default update mode for entities without `@DynamicUpdate` |
| `storm.update.dirtyCheck` | `INSTANCE` | Default dirty check strategy (`INSTANCE` or `VALUE`) |
| `storm.update.maxShapes` | `5` | Maximum UPDATE shapes before fallback to full-row |
| `storm.entityCache.retention` | `minimal` | Cache retention mode: `minimal` or `aggressive` |
| `storm.templateCache.size` | `2048` | Maximum number of compiled templates to cache |
| `storm.metrics.level` | `DEBUG` | Log level for template metrics |

### Setting Properties

**Via JVM arguments:**

```bash
java -Dstorm.update.defaultMode=FIELD \
     -Dstorm.update.dirtyCheck=VALUE \
     -Dstorm.update.maxShapes=10 \
     -Dstorm.entityCache.retention=aggressive \
     -Dstorm.templateCache.size=4096 \
     -jar myapp.jar
```

**Programmatically (before ORM initialization):**

```kotlin
System.setProperty("storm.update.defaultMode", "FIELD")
System.setProperty("storm.entityCache.retention", "aggressive")
System.setProperty("storm.templateCache.size", "4096")
```

---

## Dirty Checking Properties

These properties control how Storm detects changes to entities. See [Dirty Checking](dirty-checking.md) for details.

### storm.update.defaultMode

Controls the default update mode for entities that don't have an explicit `@DynamicUpdate` annotation.

| Value | Behavior |
|-------|----------|
| `OFF` | No dirty checking. Always update all columns. |
| `ENTITY` | Skip UPDATE if entity unchanged; full-row update if any field changed. |
| `FIELD` | Update only the columns that actually changed. |

### storm.update.dirtyCheck

Controls how Storm compares field values to detect changes.

| Value | Behavior |
|-------|----------|
| `INSTANCE` | Compare by reference identity. Fast, works well with immutable records. |
| `VALUE` | Compare using `equals()`. More accurate for mutable objects. |

### storm.update.maxShapes

Limits the number of distinct UPDATE statement shapes per entity type when using `FIELD` mode. Once the limit is reached, Storm falls back to full-row updates to prevent statement cache bloat.

Lower values (3-5) are better for applications with many entity types. Higher values (10-20) allow more granular updates but increase statement cache pressure.

---

## Entity Cache Properties

These properties control the transaction-scoped entity cache. See [Entity Cache](entity-cache.md) for details.

### storm.entityCache.retention

Controls how aggressively entities are retained in the cache during a transaction.

| Value | Behavior |
|-------|----------|
| `minimal` | Entities can be garbage collected when no longer referenced by your code. Memory-efficient for large result sets. |
| `aggressive` | Entities are strongly retained for the duration of the transaction. Guarantees identity but uses more memory. |

---

## Template Cache Properties

These properties control the caching of compiled SQL templates. See [SQL Templates](sql-templates.md#compilation-caching) for details.

### storm.templateCache.size

Sets the maximum number of compiled templates to keep in the cache. When the cache is full, the least recently used templates are evicted.

The default of 2048 is sufficient for most applications. Increase this value if you have many distinct query patterns and see cache evictions in your metrics.

---

## Metrics Properties

### storm.metrics.level

Controls the log level for template compilation metrics. Useful for monitoring cache hit rates and compilation times during development or performance tuning.

| Value | Behavior |
|-------|----------|
| `DEBUG` | Log metrics at DEBUG level (default) |
| `INFO` | Log metrics at INFO level |
| `WARN` | Log metrics at WARN level |
| `OFF` | Disable metrics logging |

---

## Per-Entity Configuration

Some settings can be overridden per entity using annotations:

### @DynamicUpdate

Override the update mode for a specific entity:

```kotlin
@DynamicUpdate(FIELD)
data class Article(
    @PK val id: Int = 0,
    val title: String,
    val content: String  // Large column - benefits from field-level updates
) : Entity<Int>
```

```java
@DynamicUpdate(FIELD)
record Article(
    @PK Integer id,
    @Nonnull String title,
    @Nonnull String content
) implements Entity<Integer> {}
```

### Dirty Check Strategy Per Entity

```kotlin
@DynamicUpdate(value = FIELD, dirtyCheck = VALUE)
data class User(
    @PK val id: Int = 0,
    val email: String
) : Entity<Int>
```

---

## Configuration Precedence

Entity-level annotations always take precedence over system properties:

```
1. @DynamicUpdate annotation on entity class
   ↓ (if not present)
2. System property
   ↓ (if not set)
3. Built-in default
```
