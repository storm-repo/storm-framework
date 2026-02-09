# Configuration

Storm can be configured through system properties. These properties control runtime behavior for features like dirty checking and entity caching. All properties have sensible defaults, so **configuration is optional**. Storm works out of the box without any configuration.

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

**In Spring Boot's `application.yml`** (requires `storm-spring-boot-starter` or `storm-kotlin-spring-boot-starter`):

```yaml
storm:
  ansi-escaping: false
  update:
    default-mode: ENTITY
    dirty-check: INSTANCE
    max-shapes: 5
  entity-cache:
    retention: minimal
  template-cache:
    size: 2048
  metrics:
    level: DEBUG
  validation:
    skip: false
    warnings-only: false
```

The Spring Boot Starter includes an `EnvironmentPostProcessor` that reads these properties and sets the corresponding JVM system properties before Storm initializes. Explicit JVM flags (`-Dstorm.*`) always take precedence over Spring properties. See [Spring Integration](spring-integration.md#configuration-via-applicationyml) for details.

**Without the starter** (using JVM args via `JAVA_OPTS`):

```properties
# These are JVM system properties, not Spring properties.
# Set them in your run configuration or startup script:
# JAVA_OPTS="-Dstorm.update.defaultMode=FIELD -Dstorm.entityCache.retention=aggressive"
```

---

## Dirty Checking Properties

These properties control how Storm detects changes to entities during update operations. Dirty checking determines whether an UPDATE statement is sent to the database and, if so, which columns it includes. Choosing the right mode depends on your entity size, update frequency, and whether you use immutable records or mutable objects. See [Dirty Checking](dirty-checking.md) for a detailed explanation of each strategy.

### storm.update.defaultMode

Controls the default update mode for entities that don't have an explicit `@DynamicUpdate` annotation. This setting applies globally and can be overridden per entity with the `@DynamicUpdate` annotation.

| Value | Behavior |
|-------|----------|
| `OFF` | No dirty checking. Always update all columns. |
| `ENTITY` | Skip UPDATE if entity unchanged; full-row update if any field changed. |
| `FIELD` | Update only the columns that actually changed. |

### storm.update.dirtyCheck

Controls how Storm compares field values to detect changes. The choice between `INSTANCE` and `VALUE` depends on whether your entities are truly immutable. Immutable records (the recommended pattern) work correctly with `INSTANCE` because unchanged fields share the same object reference. If your entities contain mutable objects that could change without creating a new reference, use `VALUE` to compare by `equals()` instead.

| Value | Behavior |
|-------|----------|
| `INSTANCE` | Compare by reference identity. Fast, works well with immutable records. |
| `VALUE` | Compare using `equals()`. More accurate for mutable objects. |

### storm.update.maxShapes

In `FIELD` mode, each unique combination of changed columns produces a distinct UPDATE statement shape (e.g., updating only `email` is a different shape than updating `email` and `name`). Each shape occupies a slot in the database's prepared statement cache. This property caps the number of shapes per entity type. Once the limit is reached, Storm falls back to full-row updates to prevent statement cache bloat.

Lower values (3-5) are better for applications with many entity types, where the total number of cached statements across all entities matters. Higher values (10-20) allow more granular updates but increase statement cache pressure.

---

## Entity Cache Properties

Storm maintains a transaction-scoped entity cache that ensures the same database row maps to the same object instance within a single transaction. This property controls the cache's memory behavior. See [Entity Cache](entity-cache.md) for details on how the cache interacts with identity guarantees and garbage collection.

### storm.entityCache.retention

Controls how aggressively entities are retained in the cache during a transaction. The choice is a trade-off between memory consumption and identity consistency. With `minimal`, the JVM can reclaim cached entities when your code no longer holds a reference, which is important for transactions that read large result sets. With `aggressive`, every entity loaded during the transaction is guaranteed to remain the same object instance if loaded again, which simplifies code that relies on reference equality.

| Value | Behavior |
|-------|----------|
| `minimal` | Entities can be garbage collected when no longer referenced by your code. Memory-efficient for large result sets. |
| `aggressive` | Entities are strongly retained for the duration of the transaction. Guarantees identity but uses more memory. |

---

## Template Cache Properties

Storm compiles SQL templates into reusable prepared statement shapes. This compilation step resolves aliases, derives joins, and expands column lists. Caching the compiled result avoids repeating this work for the same query pattern with different parameter values. See [SQL Templates](sql-templates.md#compilation-caching) for details on how compilation and caching work.

### storm.templateCache.size

Sets the maximum number of compiled templates to keep in the cache. When the cache is full, the least recently used templates are evicted.

The default of 2048 is sufficient for most applications. A typical application uses a few hundred distinct query patterns. Increase this value if you have many distinct query patterns (for example, from dynamically constructed queries) and observe cache eviction in your metrics. Each cached entry is small (the compiled SQL structure and metadata), so increasing the limit has minimal memory impact.

---

## Metrics Properties

Storm logs template compilation metrics that help you understand cache behavior, identify frequently recompiled templates, and diagnose performance bottlenecks. During development, you typically want these at DEBUG level. In production, increase to INFO or WARN to surface only significant events, or disable entirely with OFF.

### storm.metrics.level

Controls the log level for template compilation metrics.

| Value | Behavior |
|-------|----------|
| `DEBUG` | Log metrics at DEBUG level (default) |
| `INFO` | Log metrics at INFO level |
| `WARN` | Log metrics at WARN level |
| `OFF` | Disable metrics logging |

---

## Per-Entity Configuration

System properties set global defaults, but individual entities often have different update characteristics. An entity with a large text column benefits from field-level updates, while a small entity with three columns does not. Per-entity annotations let you tune update behavior where it matters most, without changing the global default.

### @DynamicUpdate

Override the update mode for a specific entity. This is most valuable for entities with large or variable-size columns where sending unchanged data wastes bandwidth.

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

You can also override the dirty check strategy on a per-entity basis. This is useful when a specific entity contains mutable objects that require value-based comparison, while the rest of your application uses the default instance-based comparison.

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

---

## Recommended Configurations

The following profiles cover common scenarios. Start with the defaults and adjust only when profiling reveals a specific bottleneck.

### Default (Most Applications)

The built-in defaults work well for most applications. No configuration needed:
- `ENTITY` mode skips UPDATE when nothing changed, but sends all columns when any field changes
- `INSTANCE` comparison is fast and correct with immutable records/data classes
- `minimal` cache retention keeps memory usage low

### High-Write Applications

For applications with frequent updates to large entities, field-level updates reduce the amount of data sent to the database on each UPDATE. This matters most when entities have large text or binary columns where sending unchanged data wastes network bandwidth and database I/O.

```bash
java -Dstorm.update.defaultMode=FIELD \
     -Dstorm.update.maxShapes=10 \
     -jar myapp.jar
```

This reduces network bandwidth by only sending changed columns.

### Long-Running Transactions

For applications with long transactions that read many entities, aggressive cache retention guarantees that loading the same row twice within a transaction returns the same object instance. This is important for code that relies on reference equality or builds in-memory graphs from query results. The trade-off is increased memory usage, since every loaded entity remains in the cache until the transaction completes.

```bash
java -Dstorm.entityCache.retention=aggressive \
     -jar myapp.jar
```

This guarantees entity identity within a transaction at the cost of higher memory usage.
