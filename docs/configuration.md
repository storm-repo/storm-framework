import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

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
| `storm.validation.record_mode` | `fail` | Record validation mode: `fail`, `warn`, or `none` |
| `storm.validation.schema_mode` | `none` | Schema validation mode: `none`, `warn`, or `fail` (Spring Boot only) |
| `storm.validation.strict` | `false` | Treat schema validation warnings as errors |

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

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

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

</TabItem>
<TabItem value="java" label="Java">

```java
var config = StormConfig.of(Map.of(
    "storm.update.default_mode", "FIELD",
    "storm.entity_cache.retention", "light",
    "storm.template_cache.size", "4096"
));

var orm = ORMTemplate.of(dataSource, config);
```

</TabItem>
</Tabs>

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
    record-mode: fail
    schema-mode: none
    strict: false
```

The Spring Boot Starter binds these properties and builds a `StormConfig` that is passed to the `ORMTemplate` factory. Values not set in YAML fall back to system properties and then to built-in defaults. See [Spring Integration](spring-integration.md#configuration-via-applicationyml) for details.

---

## ORMTemplate Factory Overloads

The `ORMTemplate.of()` factory method accepts optional parameters for configuration and template decoration. These overloads let you combine `StormConfig` (for runtime properties) with a `TemplateDecorator` (for name resolution customization) at creation time.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// Minimal: defaults only
val orm = dataSource.orm

// With configuration
val orm = dataSource.orm(config)

// With decorator (custom name resolution)
val orm = dataSource.orm { decorator ->
    decorator.withTableNameResolver(TableNameResolver.toUpperCase(TableNameResolver.DEFAULT))
}

// With both configuration and decorator
val orm = dataSource.orm(config) { decorator ->
    decorator.withColumnNameResolver(ColumnNameResolver.toUpperCase(ColumnNameResolver.DEFAULT))
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
// Minimal: defaults only
var orm = ORMTemplate.of(dataSource);

// With configuration
var orm = ORMTemplate.of(dataSource, config);

// With decorator (custom name resolution)
var orm = ORMTemplate.of(dataSource, decorator -> decorator
    .withTableNameResolver(TableNameResolver.toUpperCase(TableNameResolver.DEFAULT)));

// With both configuration and decorator
var orm = ORMTemplate.of(dataSource, config, decorator -> decorator
    .withColumnNameResolver(ColumnNameResolver.toUpperCase(ColumnNameResolver.DEFAULT)));
```

</TabItem>
</Tabs>

The decorator parameter is a `UnaryOperator<TemplateDecorator>` that receives the default decorator and returns a modified version. See [Spring Integration: Template Decorator](spring-integration.md#template-decorator) for details on the available resolvers.

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

## Validation Properties

Storm provides two independent validation subsystems, each controlled by a mode property. Record validation checks that your entity and projection definitions are structurally correct (valid primary key types, proper annotation usage, no circular dependencies). Schema validation compares your definitions against the actual database schema to catch mismatches before they surface as runtime errors.

### storm.validation.record_mode

Controls whether record (structural) validation runs when Storm first encounters an entity or projection type.

| Value | Behavior |
|-------|----------|
| `fail` | Validation errors cause startup to fail with a `PersistenceException` (default). |
| `warn` | Errors are logged as warnings; startup continues. |
| `none` | Record validation is skipped entirely. |

### storm.validation.schema_mode

Controls whether schema validation runs at startup (Spring Boot only; for programmatic use, see [Validation](validation.md#programmatic-api)).

| Value | Behavior |
|-------|----------|
| `none` | Schema validation is skipped (default). |
| `warn` | Mismatches are logged at WARN level; startup continues. |
| `fail` | Mismatches cause startup to fail with a `PersistenceException`. |

### storm.validation.strict

When `true`, schema validation warnings (type narrowing, nullability mismatches, missing unique/foreign key constraints) are promoted to errors. This is useful in CI environments where any schema drift should be caught.

See [Validation](validation.md) for a complete list of what each validation level checks.

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

---

## Dirty Check Metrics (JMX)

Storm exposes dirty checking metrics through JMX under the MBean name `st.orm:type=DirtyCheckMetrics`. These metrics aggregate across all dirty checks performed by entity repositories, giving you visibility into how often updates are skipped, which resolution paths are taken, and how your `max_shapes` budget is being used.

### Entity-Level Counters

| Attribute | Description |
|-----------|-------------|
| `Checks` | Total number of dirty checks performed |
| `Clean` | Number of checks that found the entity unchanged (update skipped) |
| `Dirty` | Number of checks that found the entity changed (update triggered) |
| `CleanRatioPercent` | Percentage of checks where the update was skipped (0-100) |

A high `CleanRatioPercent` indicates that many updates are avoided because the entity has not changed since it was read. If this ratio is low and your application frequently calls `update()` on unmodified entities, consider reviewing your update logic.

### Resolution Path Counters

| Attribute | Description |
|-----------|-------------|
| `IdentityMatches` | Checks resolved by identity comparison (`cached == entity`), the cheapest path |
| `CacheMisses` | Checks where no cached baseline was available, causing a fallback to full-entity update |

High `CacheMisses` may indicate that the entity cache is being cleared prematurely. Consider switching from `light` to `default` cache retention if cache misses are frequent. See [Entity Cache](entity-cache.md) for details.

### Mode and Strategy Breakdown

| Attribute | Description |
|-----------|-------------|
| `EntityModeChecks` | Checks that used `ENTITY` update mode (full-row UPDATE on any change) |
| `FieldModeChecks` | Checks that used `FIELD` update mode (column-level UPDATE) |
| `InstanceStrategyChecks` | Checks that used `INSTANCE` strategy (identity-based field comparison) |
| `ValueStrategyChecks` | Checks that used `VALUE` strategy (equality-based field comparison) |

### Field-Level Counters

| Attribute | Description |
|-----------|-------------|
| `FieldComparisons` | Total number of individual field comparisons across all dirty checks |
| `FieldClean` | Number of field comparisons where the field was unchanged |
| `FieldDirty` | Number of field comparisons where the field was different |

### Shape Counters

| Attribute | Description |
|-----------|-------------|
| `EntityTypes` | Number of distinct entity types that have generated UPDATE shapes |
| `Shapes` | Total number of distinct UPDATE statement shapes across all entity types |
| `ShapesPerEntity` | Map of entity type name to the number of shapes for that type |

Compare `ShapesPerEntity` values against the configured `storm.update.max_shapes` to determine if any entity type is exhausting its shape budget. When the limit is reached, Storm falls back to full-row updates for that entity type.

### Per-Entity Configuration

| Attribute | Description |
|-----------|-------------|
| `UpdateModePerEntity` | Map of entity type name to effective update mode (`FIELD`, `ENTITY`, `OFF`) |
| `DirtyCheckPerEntity` | Map of entity type name to effective dirty check strategy (`INSTANCE`, `VALUE`) |
| `MaxShapesPerEntity` | Map of entity type name to configured max shapes limit |

### Operations

| Operation | Description |
|-----------|-------------|
| `reset()` | Resets all counters to zero |

---

## Entity Cache Metrics (JMX)

Storm exposes entity cache metrics through JMX under the MBean name `st.orm:type=EntityCacheMetrics`. These metrics aggregate across all transaction-scoped entity caches, providing visibility into cache hit rates, eviction patterns, and retention behavior.

### Lookup Counters

| Attribute | Description |
|-----------|-------------|
| `Gets` | Total number of `get()` calls (cache lookups) |
| `GetHits` | Number of lookups that returned a cached entity |
| `GetMisses` | Number of lookups where no cached entity was available |
| `GetHitRatioPercent` | Get hit ratio as a percentage (0-100) |

A low `GetHitRatioPercent` in combination with frequent `update()` calls suggests that entities are being evicted before they can serve as dirty-check baselines. Consider switching to `default` cache retention.

### Intern Counters

| Attribute | Description |
|-----------|-------------|
| `Interns` | Total number of `intern()` calls (cache insertions) |
| `InternHits` | Number of intern calls that reused an existing canonical instance |
| `InternMisses` | Number of intern calls that stored a new or updated instance |
| `InternHitRatioPercent` | Intern hit ratio as a percentage (0-100) |

### Lifecycle Counters

| Attribute | Description |
|-----------|-------------|
| `Removals` | Number of cache entries removed due to entity mutations (insert, update, delete) |
| `Clears` | Number of full cache clears |
| `Evictions` | Number of cache entries cleaned up after garbage collection |

High `Evictions` values indicate that entities are being garbage collected while still in the cache. This is expected with `light` retention but unusual with `default` retention unless the JVM is under memory pressure.

### Per-Entity Configuration

| Attribute | Description |
|-----------|-------------|
| `RetentionPerEntity` | Map of entity type name to effective retention mode (`DEFAULT`, `LIGHT`) |

### Operations

| Operation | Description |
|-----------|-------------|
| `reset()` | Resets all counters to zero |

### Viewing Metrics

Connect to the JVM with any JMX client (JConsole, VisualVM, or your monitoring platform) and navigate to the `st.orm` domain. All three MBeans (`TemplateMetrics`, `DirtyCheckMetrics`, `EntityCacheMetrics`) are registered automatically when Storm initializes. You can also expose them via Spring Boot Actuator's JMX endpoint if your application uses Actuator.

---

## Per-Entity Configuration

System properties set global defaults, but individual entities often have different update characteristics. An entity with a large text column benefits from field-level updates, while a small entity with three columns does not. Per-entity annotations let you tune update behavior where it matters most, without changing the global default.

### @DynamicUpdate

Override the update mode for a specific entity. This is most valuable for entities with large or variable-size columns where sending unchanged data wastes bandwidth.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
@DynamicUpdate(FIELD)
data class Article(
    @PK val id: Int = 0,
    val title: String,
    val content: String  // Large column - benefits from field-level updates
) : Entity<Int>
```

</TabItem>
<TabItem value="java" label="Java">

```java
@DynamicUpdate(FIELD)
record Article(
    @PK Integer id,
    @Nonnull String title,
    @Nonnull String content
) implements Entity<Integer> {}
```

</TabItem>
</Tabs>

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
