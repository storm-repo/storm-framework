# Configuration

Storm can be configured through `StormConfig`, system properties, or Spring Boot's `application.yml`. These properties control runtime behavior for features like dirty checking and entity caching. All properties have sensible defaults, so **configuration is optional**. Storm works out of the box without any configuration.

---

## Properties

| Property | Default | Description |
|----------|---------|-------------|
| `storm.update.default_mode` | `ENTITY` | Default update mode for entities without `@DynamicUpdate` |
| `storm.update.dirty_check` | `INSTANCE` | Default dirty check strategy (`INSTANCE` or `VALUE`) |
| `storm.update.max_shapes` | `5` | Maximum UPDATE shapes before fallback to full-row |
| `storm.entity_cache.retention` | `default` | Cache retention mode: `default` or `light` |
| `storm.template_cache.size` | `2048` | Maximum number of compiled templates to cache |

### Setting Properties

**Via JVM arguments:**

```bash
java -Dstorm.update.default_mode=FIELD \
     -Dstorm.update.dirty_check=VALUE \
     -Dstorm.update.max_shapes=10 \
     -Dstorm.entity_cache.retention=light \
     -Dstorm.template_cache.size=4096 \
     -jar myapp.jar
```

**Programmatically via `StormConfig`:**

`StormConfig` holds an immutable set of `String` key-value properties. Pass a `StormConfig` to `ORMTemplate.of()` to apply the configuration. Any property not explicitly set falls back to the system property, then to the built-in default.

```kotlin
val config = StormConfig.of(mapOf(
    "storm.update.default_mode" to "FIELD",
    "storm.entity_cache.retention" to "light",
    "storm.template_cache.size" to "4096"
))

val orm = ORMTemplate.of(dataSource, config)

// Or using the extension function
val orm = dataSource.orm(config)
```

```java
var config = StormConfig.of(Map.of(
    "storm.update.default_mode", "FIELD",
    "storm.entity_cache.retention", "light",
    "storm.template_cache.size", "4096"
));

var orm = ORMTemplate.of(dataSource, config);
```

When `StormConfig` is omitted, `ORMTemplate.of(dataSource)` reads system properties and built-in defaults automatically.

**In Spring Boot's `application.yml`** (requires `storm-spring-boot-starter` or `storm-kotlin-spring-boot-starter`):

```yaml
storm:
  ansi-escaping: false
  update:
    default-mode: ENTITY
    dirty-check: INSTANCE
    max-shapes: 5
  entity-cache:
    retention: default
  template-cache:
    size: 2048
  validation:
    skip: false
    warnings-only: false
```

The Spring Boot Starter binds these properties and builds a `StormConfig` that is passed to the `ORMTemplate` factory. Values not set in YAML fall back to system properties and then to built-in defaults. See [Spring Integration](spring-integration.md#configuration-via-applicationyml) for details.

---

## Dirty Checking Properties

These properties control how Storm detects changes to entities during update operations. Dirty checking determines whether an UPDATE statement is sent to the database and, if so, which columns it includes. Choosing the right mode depends on your entity size, update frequency, and whether you use immutable records or mutable objects. See [Dirty Checking](dirty-checking.md) for a detailed explanation of each strategy.

### storm.update.default_mode

Controls the default update mode for entities that don't have an explicit `@DynamicUpdate` annotation. This setting applies globally and can be overridden per entity with the `@DynamicUpdate` annotation.

| Value | Behavior |
|-------|----------|
| `OFF` | No dirty checking. Always update all columns. |
| `ENTITY` | Skip UPDATE if entity unchanged; full-row update if any field changed. |
| `FIELD` | Update only the columns that actually changed. |

### storm.update.dirty_check

Controls how Storm compares field values to detect changes. The choice between `INSTANCE` and `VALUE` depends on whether your entities are truly immutable. Immutable records (the recommended pattern) work correctly with `INSTANCE` because unchanged fields share the same object reference. If your entities contain mutable objects that could change without creating a new reference, use `VALUE` to compare by `equals()` instead.

| Value | Behavior |
|-------|----------|
| `INSTANCE` | Compare by reference identity. Fast, works well with immutable records. |
| `VALUE` | Compare using `equals()`. More accurate for mutable objects. |

### storm.update.max_shapes

In `FIELD` mode, each unique combination of changed columns produces a distinct UPDATE statement shape (e.g., updating only `email` is a different shape than updating `email` and `name`). Each shape occupies a slot in the database's prepared statement cache. This property caps the number of shapes per entity type. Once the limit is reached, Storm falls back to full-row updates to prevent statement cache bloat.

Lower values (3-5) are better for applications with many entity types, where the total number of cached statements across all entities matters. Higher values (10-20) allow more granular updates but increase statement cache pressure.

---

## Entity Cache Properties

Storm maintains a transaction-scoped entity cache that ensures the same database row maps to the same object instance within a single transaction. This property controls the cache's memory behavior. See [Entity Cache](entity-cache.md) for details on how the cache interacts with identity guarantees and garbage collection.

### storm.entity_cache.retention

Controls how long entities are retained in the cache during a transaction. The choice is a trade-off between memory consumption and dirty-checking reliability. With `default`, entities are retained for the duration of the transaction, which provides reliable dirty checking while still allowing the JVM to reclaim entries under memory pressure. With `light`, the JVM can reclaim cached entities as soon as your code no longer holds a reference, which reduces memory usage but may cause dirty-check cache misses.

| Value | Behavior |
|-------|----------|
| `default` | Entities retained for the transaction duration. Reliable dirty checking. The JVM may still reclaim entries under memory pressure. |
| `light` | Entities can be garbage collected when no longer referenced by your code. Memory-efficient but may cause full-row updates due to cache misses. |

---

## Template Cache Properties

Storm compiles SQL templates into reusable prepared statement shapes. This compilation step resolves aliases, derives joins, and expands column lists. Caching the compiled result avoids repeating this work for the same query pattern with different parameter values. See [SQL Templates](sql-templates.md#compilation-caching) for details on how compilation and caching work.

### storm.template_cache.size

Sets the maximum number of compiled templates to keep in the cache. When the cache is full, the least recently used templates are evicted.

The default of 2048 is sufficient for most applications. A typical application uses a few hundred distinct query patterns. Increase this value if you have many distinct query patterns (for example, from dynamically constructed queries) and observe cache eviction in your metrics. Each cached entry is small (the compiled SQL structure and metadata), so increasing the limit has minimal memory impact.

---

## Template Metrics (JMX)

Storm exposes template cache metrics through JMX under the MBean name `st.orm:type=TemplateMetrics`. This is a singleton that aggregates metrics across all `ORMTemplate` instances in the JVM.

### Available Attributes

| Attribute | Description |
|-----------|-------------|
| `Requests` | Total number of template requests |
| `Hits` | Number of cache hits |
| `Misses` | Number of cache misses |
| `HitRatioPercent` | Hit ratio as a percentage (0-100) |
| `AvgRequestMicros` | Average request duration in microseconds |
| `MaxRequestMicros` | Maximum request duration in microseconds |
| `AvgHitMicros` | Average cache hit duration in microseconds |
| `MaxHitMicros` | Maximum cache hit duration in microseconds |
| `AvgMissMicros` | Average cache miss duration in microseconds |
| `MaxMissMicros` | Maximum cache miss duration in microseconds |

### Operations

| Operation | Description |
|-----------|-------------|
| `reset()` | Resets all counters to zero |

### Viewing Metrics

Connect to the JVM with any JMX client (JConsole, VisualVM, or your monitoring platform) and navigate to the `st.orm` domain. The `TemplateMetrics` MBean is registered automatically when Storm initializes.

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

Entity-level annotations take the highest precedence, followed by explicit `StormConfig` values, then system properties, and finally built-in defaults:

```
1. @DynamicUpdate annotation on entity class
   ↓ (if not present)
2. StormConfig (explicit value passed to factory)
   ↓ (if not set)
3. System property (-Dstorm.*)
   ↓ (if not set)
4. Built-in default
```

When using the Spring Boot Starter, `StormConfig` is built from `application.yml` properties. Properties not set in YAML fall through to system properties and then to built-in defaults.

---

## Recommended Configurations

The following profiles cover common scenarios. Start with the defaults and adjust only when profiling reveals a specific bottleneck.

### Default (Most Applications)

The built-in defaults work well for most applications. No configuration needed:
- `ENTITY` mode skips UPDATE when nothing changed, but sends all columns when any field changes
- `INSTANCE` comparison is fast and correct with immutable records/data classes
- `default` cache retention provides reliable dirty checking

### High-Write Applications

For applications with frequent updates to large entities, field-level updates reduce the amount of data sent to the database on each UPDATE. This matters most when entities have large text or binary columns where sending unchanged data wastes network bandwidth and database I/O.

```bash
java -Dstorm.update.default_mode=FIELD \
     -Dstorm.update.max_shapes=10 \
     -jar myapp.jar
```

This reduces network bandwidth by only sending changed columns.

### Memory-Constrained Bulk Operations

For transactions that load a very large number of entities (bulk migrations, large reports), light cache retention allows the JVM to reclaim cached entities sooner. The trade-off is that dirty checking may encounter cache misses, resulting in full-row updates.

```bash
java -Dstorm.entity_cache.retention=light \
     -jar myapp.jar
```

This reduces memory usage at the cost of less efficient dirty checking.
