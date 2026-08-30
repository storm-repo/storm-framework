# Changelog

All notable changes to Storm are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and Storm follows
[Semantic Versioning](https://semver.org/).

Releases are tag-driven and published to
[Maven Central](https://central.sonatype.com/namespace/st.orm) (`st.orm`) and,
for the CLI, to [npm](https://www.npmjs.com/package/@storm-orm/cli)
(`@storm-orm/cli`). Full release notes for every version are on the
[GitHub Releases](https://github.com/storm-orm/storm-framework/releases) page.

## [1.14.0] - Unreleased

A quality release: a smaller, more coherent API, and SQL that is correct on every dialect Storm supports rather than on the permissive ones.

### A join widens the query

From the join onward every clause accepts paths from any entity in the query, so referencing a joined table needs no separate method:

```kotlin
orm.entity<Role>().select()
    .innerJoin<UserRole>().on<Role>()
    .where(UserRole_.user eq user)
    .resultList
```

- Removed `whereAny`, `whereAnyRef`, `whereAnyBuilder`, `havingAny`, `groupByAny`, `orderByAny`, `orderByDescendingAny`, `andAny` and `orAny`; `and`/`or` inherit the query root. A path on an entity the query does not carry fails when the query is built, naming the entity, the root, and the paths that pin the table where it appears more than once.
- Added `widen()` and `narrow(rootType)`, the two directions made explicit: `widen()` admits short-form references on a query that joins nothing, `narrow` restores the root for the operations defined relative to it, `resultGroupedBy` and `scroll`.
- Renamed `typed(pkType)` to `typedId(pkType)`: it types the erased primary-key parameter, where `narrow` types the root.
- `fetch(...)` comes right after `select()`, before any join, enforced at compile time.
- Kotlin's `select { }` and `delete { }` blocks are widened from the start, return the widened builder, and carry the chained builder's clause vocabulary.

### A grouping states an identity

A grouping says what one row stands for, and the generator emits the columns that express it. Naming the entity is enough:

```kotlin
orm.entity<User>()
    .select<CityCount, _, _> { "${City::class}, COUNT(*)" }
    .groupBy(User_.city)
```

```sql
-- PostgreSQL, MySQL, SQLite, H2
GROUP BY c.id
-- SQL Server, Oracle, which do not resolve functional dependency from a key
GROUP BY c.id, c.name, c.population
```

- The relationship (`User_.city`), the key beyond it (`User_.city.id`), the root's own key (`User_.id`) and a reference all state the same identity. Any other path groups by its value.
- Selecting an entity selects the tables its foreign keys reach, so a grouping covers those keys too. Every foreign key is to-one, so this cannot move a row into a different group.
- A grouping that determines nothing, such as selecting `Pet` while grouping by `Pet_.owner`, is refused when the query is built, on every dialect rather than only the ones that would notice.

### The engine leaves the compile classpath

Applications program against the facade API; the engine that executes it is a runtime concern. `storm-core` reaches the application at runtime only, so completion and imports offer exactly one `EntityRepository`, one `ORMTemplate`, one `QueryBuilder`: the facade's.

- The integration contract lives in `storm-foundation` under `st.orm.spi`: exception translation (`ExceptionMapper`, `ExceptionContext`, `SqlOperation`), query observation (`QueryObserver`, `QueryContext`, `StatementOrigin`), SQL commenting (`SqlCommenter`) and cursor codecs. `SqlTemplateException` joins `PersistenceException` in `st.orm`. Transaction bridging, `RefFactory` and the dialect surface are engine SPI and stay in `storm-core`.
- The Spring Boot starters carry `storm-core` at runtime scope; the library modules declare it `provided`.
- The facade builders keep the options an application can name: `config`, `decorator`, `manualCommitConnections`, `exceptionMapper`, `queryObserver` and `sqlCommenter`. Transaction bridging is framework-level composition, available through `SpringOrmTemplate.builder(dataSource, beanFactory)` and `springOrmTemplateBuilder(dataSource, beanFactory)`.
- On the module path the facades require `storm-foundation` transitively; `storm.core` stays non-transitive, and its repository package is exported to Storm's own modules only.
- `QueryObserver.onTransaction` receives the `st.orm.TransactionOptions` an application passes to `transaction(options) { }`.
- Creating a template without the engine on the runtime classpath names the missing `st.orm:storm-core` dependency, rather than surfacing as a `NoClassDefFoundError` at the first statement.
- `@StormTest` injects `SchemaValidation`, a facade over the engine's schema validator that reports mismatches as rendered messages.

### Highlights

- The public API is JSpecify null-marked, and the jakarta annotations are gone from Storm's signatures, so `jakarta.annotation-api` is no longer a dependency. JSpecify itself is optional: compilers and analysis tools read the annotations from bytecode. Kotlin callers get real `T`/`T?` types where the Java surface used to be platform types, and the generated nullable metamodel chain compiles on Kotlin 2.1+.
- `@StormTest` runs each test inside a database transaction that is rolled back afterwards, so tests no longer observe each other's writes. Transaction blocks demarcate with savepoints inside it; `rollback = false` opts a class out.
- `@StormTest` and `@DataStormTest` run on the database the application deploys on: `database = POSTGRESQL` (or `MYSQL`, `MARIADB`, `MSSQL_SERVER`, `ORACLE`) starts a Testcontainers-managed container once per JVM and gives each test class a freshly created database inside it. The database's Testcontainers 2 module and JDBC driver stay out of `storm-test`'s dependencies, and a test that names a container database fails naming both when one is missing. `TestDatabase.POSTGRESQL.container()` exposes the shared container, and `createDatabase()` a database of your own.
- `Projection<ID>`'s type argument is a checked contract instead of a phantom parameter: `ID` is the projection's row identity type, which for a foreign-key-typed primary key is the referenced table's key. Record validation rejects a declaration the record contradicts, and the rule that a foreign key must not be an auto-generated primary key applies to entities only.
- The slow statement log reports each execution whose database time exceeds a threshold, under `st.orm.sql.slow` at `WARN`: `storm.sql-log.slow.threshold=200ms` in Spring, `sqlLogSlowThreshold` in Ktor, `storm.sql_log.slow.threshold` on a plain JVM. The line carries the statement, the rows, and how the execution compares to what its shape typically costs (`typically 6.0 ms, 306x`) and binds (`parameters 32 (typically 3)`), which says whether to look at the parameters or at the query; values render at `TRACE` only and lines are rate-limited per shape. Database time is measured to the statement's return everywhere, so the summary, `SqlCapture.duration` and the slow line agree.
- The SQL log is configured per log and named after the two loggers it writes to: `storm.sql-log.performance.*` for the performance log (`st.orm.sql.perf`), which says what a unit of work cost the database, and `storm.sql-log.slow.*` for the slow statement log (`st.orm.sql.slow`), which names the execution that cost too much. The Ktor DSL, the HOCON keys, the system properties and the Spring types follow: `StormPerformanceLogFilter` and `StormPerformanceLogEntryPointPostProcessor`.
- The SQL log is retunable while the application runs: `SlowStatementLog.threshold(Duration)` and `limit(int)` land on the next execution, and the performance log reads its settings per unit of work. Where the actuator is on the class path, the `storm` endpoint reads and sets both halves (`GET`/`POST /actuator/storm`), Storm's control surface rather than the SQL log's.
- The slow statement log takes its threshold from the performance log when it has none of its own, so the derived default names the statement behind a warning instead of adding warnings of its own.
- The implementation is sealed: `st.orm.core.template.impl` and `st.orm.core.repository.impl` are exported to Storm's own modules only, and storm-kotlin's `impl` packages are `internal` throughout, including the `Flow` operators that collided with their kotlinx.coroutines namesakes. All five Kotlin modules compile in explicit API mode; the coroutine-aware SQL log recording the Ktor plugin shares is the one exception, behind `@InternalStormApi`.

### Other changes

- The slow statement log's per-shape rate limit covers the executions it has no shape for, which used to skip it entirely. The shape identity uses the full 64 bits it is declared with, derived from the shape key's content, and renders as sixteen hex digits in the log and in the `storm.shape` observation tag.
- The Spring Boot starters expose `OrmTemplateFactory`: one bean that composes a fully integrated `ORMTemplate` wherever the application defines its own template beans. Failure translation follows `storm.exception-translation.enabled` per data source, observations resolve their conventions from each data source's JDBC URL and report the template's name as `storm.database`, and a customize block applies application-specific composition without touching the integration SPI.
- The Ktor plugin closes its composition gaps against the starters: `storm.observations.semanticConventions = otel` selects the OpenTelemetry conventions per database, and a `customize` slot applies application-specific composition after the integration is wired. Observer composition is shared with the starters through `QueryObservers` in storm-micrometer.
- Both metamodel processors reject a cycle of non-Ref foreign keys at compile time, naming the cycle and the fix. They converge on one contract: the Java processor generates the `<Type>NullableMetamodel` chain variant, and KSP sources components from the primary constructor, contributes abstract properties only for sealed interfaces, and escapes keyword-named properties.
- The Java annotation processor registers with Gradle as an aggregating incremental processor, so attaching it no longer forces full recompilation. The Gradle plugin wires it into every source set's processor configuration, so entities in test sources get a metamodel.
- The Jackson converters key their mapper cache on the field's type, so a serializer shared by fields of different types is registered per field type. A record component's field metadata includes the annotations Java propagates to the backing field, accessor or constructor parameter, and `@Json` fields serialize with the declared field type, so a polymorphic value writes the discriminator that reading the column expects.
- Storm-initiated transaction blocks resolve the Spring manager that owns their data source, covering `JdbcTransactionManager`, a `JpaTransactionManager` backed by it, and a `JtaTransactionManager` where no resource-bound manager claims it. An option a manager refuses is reported against the option passed to the block, and a connection that arrives with auto-commit disabled fails naming the two possible causes and the fix.
- What a transaction block joins, and which blocks share a connection, follows from the block structure rather than from what happens to be bound at the time. A `REQUIRED` block inside a `NOT_SUPPORTED` or `NEVER` block opens a transaction of its own, `MANDATORY` and `NEVER` are checked against the block the enclosing code declares, and data source consistency is checked per physical transaction, so an audit write can go to a second database from inside a transaction on the first.
- The `@DataStormTest` slice imports every auto-configuration the starters register in production, verified by a parity test, and provides `JdbcTemplate`, `JdbcClient` and Testcontainers service connection support.
- Transaction observations go through an observation convention, `StormTransactionObservationConvention`, the way query observations always did, and the Ktor plugin forwards them. Query observations carry the shape identity as the low-cardinality `storm.shape` key value, cached per compiled template.
- `StormConfig.sqlShapingKeys()` exposes the configuration keys whose values affect the generated SQL, and the template cache key includes exactly these.
- `SqlLog` carries the diagnostics API only: summary rendering lives in `SqlLogRenderer` and call-site capture in `CallSiteCapture`, both internal. The hydration shape is gone from summary rows; the per-type report belongs to `storm analyze` (#503).
- The deliberately paired artifacts, `storm-java21`/`storm-kotlin`, the two Spring Boot starters, `storm-jackson2`/`storm-jackson3` and the two metamodel processors, ship the same fully qualified names, one half per class path. Each half bans its twin through a maven-enforcer rule.
- The Java and Kotlin facades close their remaining parity gaps. Kotlin's path-based `where` and `whereRef` clauses accept `Navigable` paths, so navigation-only nodes reached through a `Ref` compile in both languages; Java's `where(path, record)` bounds the record by `Data`. Kotlin's `findRefBy` drops two unused type parameters, `findAllRefBy(field, values)` accepts any value type, and `Templates` gains the named `param(name, Calendar, TemporalType)` variant.
- The `select { }` and `delete { }` blocks add the remaining clause forms: `where(records)`, `where(path, records)`, `where(path, operator, values)`, `whereRef(path, refs)` and the descending order template. `whereRef` works inside `whereBuilder { }` too.
- The query builder's `hasOrderBy()` probe is no longer public API. Combining explicit `orderBy` with `Pageable` or `Scrollable` sorting still fails with a descriptive error.
- The two-argument `selectFrom(fromType, selectType)` renders the select list from the FROM table, so `selectType` may be any record shape over those columns, such as a wrapper record nesting the from-entity.
- Unloading a ref that wraps an unsaved record fails with an error naming the cause instead of a `NullPointerException`.

## [1.13.1] - 2026-08-07

- Fixed the Java API and the `Any` variants rejecting navigation-only paths (beyond a `Ref`): every clause parameter now accepts `Navigable`, and the new `Navigable.asMetamodel()` resolves a node to its column.
- Fixed template fragments degrading a beyond-`Ref` path to a bind parameter instead of resolving the column.
- Fixed schema validation resolving the SQL dialect by classpath order instead of from the database product.
- Fixed the Kotlin compiler plugin interpolating the wrong operands when a template lambda uses string concatenation.
- Fixed row mapping for reference-resolving statements (`fetch(...)`) read through a custom query executor: the public mapper overload accepts the resolved reference paths.
- Column resolution errors name the cause and the candidates: an ambiguous short-form reference lists the paths that pin it, and a reference to a table outside the query says so, naming the query root.

## [1.13.0] - 2026-08-01

Feature and performance release: write sets, `Ref` navigation and resolution in queries, compiled query plans, GraalVM native images, the Storm Gradle plugin, Java transaction parity, SQL logging, and Micrometer observability, with the read, write and transaction hot paths leaner throughout.

- `EntityCallback.beforeDelete` / `afterDelete` are renamed `beforeRemove` / `afterRemove`. A Java override without `@Override` compiles and stops being called, so search for the old names.
- "After" callbacks observe what the calling method reports: `*AndFetchId(s)` the entity carrying the assigned key, `*AndFetch` the row as read back, the void methods the entity as sent.
- Integration points are instance-scoped: `ORMTemplate.builder(...)` composes `connectionProvider`, `transactionTemplateProvider`, `exceptionMapper` and `queryObserver`, `transaction { }` binds to the first template used inside the block, and templates no longer enlist in Spring transactions based on classpath presence. `@EnableTransactionIntegration` and the Spring `ServiceLoader` providers are removed; use `springOrmTemplate(...)` or the starter.
- Models are null-marked by default, aligning Java with Kotlin and JSpecify. A bare `@FK` joins INNER, `@Nullable @FK` joins LEFT.
- Spring Boot: SQL failures translate to Spring's `DataAccessException` hierarchy. Update catch blocks and rollback rules, or set `storm.exception-translation.enabled=false`.
- Ktor is upgraded to 3.4.3, moving storm-ktor to the Kotlin 2.3 toolchain; the other modules stay on Kotlin 2.0. `storm-ktor-koin` is removed in favor of Ktor's built-in dependency injection.
- Added write sets (`orm.writeSet()`, Kotlin also `orm.writeSet { }`): one write action over a mixed-type entity collection, ordered by foreign key dependencies and batched per type. `insert` and `upsert` extend to the transitively reachable unsaved entities; `update` and `remove` write exactly what is passed.
- Added `getResultGroupedBy` / `getResultGroupedByRef`: the one-to-many read grouped during hydration in a single query, typed through the new `TypedMetamodel`.
- Added navigation through `Ref` foreign keys in queries (`User_.city.country.name`), joining the referenced table on demand; nodes beyond a reference are navigation-only (the new `Navigable`).
- Added `select().fetch(User_.city)`: the query resolves the named references in place of their foreign key columns, so `Ref.fetch()` returns without querying. `Ref.getOrThrow()` reads one without querying and fails where the plan does not cover it.
- Added compiled query plans (`QueryTemplate.plan(...)`, `QueryBuilder.plan()`): a template is processed once into a reusable `QueryPlan` and bound per execution. Repositories reuse cached plans for their fixed-shape operations.
- Added SQL logging: statements on the `st.orm.sql` logger (DEBUG, values at TRACE) and per-call summaries on `st.orm.sql.perf` through `sqlLog { }`, `SqlLog.open(...)`, `storm.sql-log.*` and the Ktor slots. Replaces the removed `@SqlLog` annotation.
- Added GraalVM native image support: reachability metadata, Spring AOT hints and AOT-participating repository scanning for Spring Boot, and a storm-core GraalVM feature for Ktor and plain JVM applications.
- Added the Storm Gradle plugin (`id("st.orm")`, Gradle 8.5+): imports the BOM, adds the language-path dependencies, wires the metamodel processor and matching compiler-plugin variant, and sets the preview flags; configuration-cache compatible.
- Added programmatic transactions for Java (`Transactions.transaction(...)`) with the semantics of Kotlin's `transaction { }`, a shared transaction vocabulary in storm-foundation, a Spring bridge that joins `@Transactional` transactions, and `Transaction.onCompletion(committed)`.
- Added `storm.query` and `storm.transaction` Micrometer Observations (new storm-micrometer module), auto-bound by the Spring Boot starters and the Ktor plugin, with opt-in OpenTelemetry semantic conventions and trace-context SQL comments.
- Added `@EnableStormRepositories`, the `@DataStormTest` test slice (new storm-spring-boot-test-autoconfigure module), and starter auto-configurations shared by both stacks.
- Added to the Ktor plugin: repositories and the template through Ktor's built-in dependency injection, multiple databases via `database("name") { }`, and the `transactional { }` route DSL.
- Performance: batch inserts emit a single multi-row `INSERT ... VALUES`, retrieving generated keys in one round trip on every capable dialect; single-row reads skip the stream pipeline, cached entities skip column decoding, eager reads skip the fetch-size hint, and the dirty-check, statement-build, expression-binding and result-mapping paths allocate less. Transactions restore connection isolation and read-only settings only when a block changed them, saving two round trips on PostgreSQL.
- Changed: records are constructed through generated instantiators instead of reflection, removing the last reflective call from row mapping.
- Changed: `groupBy` and `orderBy` resolve a path to the columns a predicate on it uses. A foreign key contributes its own column(s) without joining the referenced table, an inline record its component columns, and `Visit_.pet.id` names `visit.pet_id`.
- Changed: Spring repository scanning is single-sourced in storm-spring with the Kotlin bindings in `st.orm.spring.kotlin`; one starter per application.
- Fixed transaction callbacks inside an externally managed transaction firing when the block returned rather than when the transaction completed, so `onCommit` could report a commit that Spring then rolled back.
- Fixed multi-column key comparisons rendering as row-value tuples, which MariaDB does not index in an UPDATE or a DELETE: batches of keyed updates were quadratic in the table and prone to deadlock.
- Fixed entity callbacks being skipped on the dialect-specific key-returning insert paths (PostgreSQL, MariaDB, SQL Server), and `upsertAndFetchIds` failing on SQL Server with an auto-generated primary key.
- Fixed entity-typed primary keys: row identity in the entity cache, refs and write sets no longer depends on non-key columns round-tripping exactly, and `findAllById` / `whereId` resolve when the key entity's table appears twice in the join graph.
- Fixed the metamodel in both generators: a reference metamodel's value is nullable in Java, matching KSP; `@Json` columns are addressed by their stored type; field types resolve from the canonical constructor.
- Fixed the dynamic proxy interface order of monitored resources, keeping GraalVM proxy registrations valid; hardened schema metadata queries and `Ref` deserialization.

## [1.12.1] - 2026-07-12

- Fixed metamodel references to components of compound primary keys resolving to the underlying columns.
- Fixed repository lookups by a `Ref` primary key (`Entity<Ref<T>>`): the reference was bound instead of the key it carries.
- Website and documentation updates.

## [1.12.0] - 2026-07-06

Feature release centered on the reified Kotlin query API. Breaking changes are accepted with no deprecation shims (1.12 policy).

- `QueryBuilder`, `JoinBuilder`, and `TypedJoinBuilder` are now abstract classes (were interfaces) so they can host reified members; recompile against 1.12.
- Schema validation now defaults to `fail` in the Spring Boot starters and the Ktor plugin; set `storm.validation.schema_mode` / `schemaMode` to `warn` or `none` to relax.
- Added a reified Kotlin query API: reified joins (`innerJoin<Rating>().on<Movie>()`, `innerJoin<Owner, Pet>()`), selects (`select<R, _, _>`, `selectFrom<T, R>`), repository lookup (`entity<T, ID>()`), and `Query` result terminals (`resultList<T>()`, `resultFlow<T>()`, and friends).
- Added `findBy` / `getBy` / `findAllBy` repository shortcuts (and `Ref` variants) for Java 21.
- Added the `storm-ktor-koin` module: `Application.stormModule()` bridges the ORM template and auto-registered repositories into Koin.
- Ktor: repositories auto-register when the `Storm` plugin is installed (`stormRepositories { }` is now optional); added a `migration { }` hook that runs before schema validation.
- Foreign keys now follow key chains, resolving to the referenced key's columns for dependent one-to-one relationships; FK columns are schema-validated through the chain.
- Dependency-aware join ordering fixes forward alias references that PostgreSQL rejected when outer joins are present; JSpecify `@NonNull` is now recognized for record-component nullability.
- Typed joins onto projections resolve by table match; ambiguous foreign-key joins now fail fast with a descriptive error instead of a silent first match.
- Fixed `@Json` binding to PostgreSQL `jsonb`, H2 natural-key upserts, and `@StormTest` script splitting on semicolons inside comments and literals.
- Spring Boot 4 auto-configuration compatibility.

## [1.11.6] - 2026-07-01
- Kotlin 2.4 support: added the `storm-compiler-plugin-2.4` variant built against the Kotlin 2.4.0 compiler API.
- Fixed `@Convert` on an embedded (`@Inline`) component field failing on save.

## [1.11.5] - 2026-06-27
- Documentation and tooling release.
- Corrected the quick-start install command to `npx @storm-orm/cli` (the previously documented `@storm/cli` package does not exist on npm).

## [1.11.4] - 2026-06-27
- Improved handling of `NULL` for optional results in `Query` and `QueryBuilder` (Java and Kotlin) so optional single-result lookups behave consistently.
- Optimized object mapping for value types, reducing per-row mapping overhead.

## [1.11.3] - 2026-05-30
- Added `validateSchema(Predicate<...>)` / `validateSchemaOrThrow(...)` overloads on `ORMTemplate`.
- Added `validateAndReport(Predicate, strict)` / `validateReportAndThrow(...)` on `SchemaValidator` for filter-based validation.

## [1.11.2] - 2026-04-09
- Added the `storm db` command group for managing a global database connection library.
- Added multi-database MCP support (`storm mcp add/list/remove`).
- Added optional read-only data access via the `select_data` MCP tool.

## [1.11.1] - 2026-04-01
- Added `Slice<R>` as the common base for `Window` (cursor-based scrolling) and `Page` (offset pagination).
- Added `findAllRef()` on `EntityRepository` / `ProjectionRepository`.
- Added `select(predicate)` / `selectRef(predicate)` convenience methods.

## [1.11.0] - 2026-03-27
- Added the cursor-based scroll API (`Scrollable`, `Window`, `MappedWindow`).
- Added opaque cursor serialization (`CursorCodec`, `CursorFactory` SPI).
- Added the SQLite dialect module (`storm-sqlite`).

## [1.10.0] - 2026-03-14
- Added the Kotlin compiler plugin (`storm-compiler-plugin`) for the SQL Template DSL, replacing the `storm-kotlin-validator` lint rules with compile-time wrapping.
- Added multi-dollar string support in SQL templates.
- Added built-in offset-based pagination (`Page`, `Pageable`).

## [1.9.1] - 2026-03-07
- Fixed the repository scanner registering non-`Repository` interfaces in scanned packages.

## [1.9.0] - 2026-03-05
- Added the `storm-bom` module for centralized dependency version management.
- Added the `storm-test` module with the `@StormTest` JUnit 5 extension and `StatementCapture`.
- Added Spring Boot starter auto-configuration.

## [1.8.2] - 2026-02-13
- Added transaction-scoped, cache-first lookups; `Ref.fetch()` now checks the cache before querying.
- Raw update queries now invalidate the cache for the affected entity type.

## [1.8.1] - 2026-01-29
- Entity cache enabled in read-only transactions and disabled for `READ_UNCOMMITTED`.
- Primary-key-based cache lookups instead of full entity equality checks.

## [1.8.0] - 2026-01-28
- Added SQL template caching and template-generation metric logging.
- Separated SQL template compilation and binding stages.

## [1.7.2] - 2026-01-09
- Clean entity cache after nested transaction rollback.
- Cleaned up the extension-functions API.

## [1.7.1] - 2025-12-30
- Support for custom field types and kotlinx.serialization (Jackson or kotlinx).
- Serializability for entities and projections; entity-level dirty checks for updates.

## [1.6.2] - 2025-11-02
- Allow a primary key to be a `Ref`.

## [1.6.1] - 2025-10-04
- Fixed `WhereProcessor` for PK/FK combinations; improved join inclusion and JDBC time-type handling.

## [1.6.0] - 2025-09-13
- Added sequence-based ID generation strategy and entity-to-ref conversion.

## [1.5.0] - 2025-08-19
- Added Kotlin coroutine support, programmatic transactions, and `Flow` support.

## [1.4.0] - 2025-08-11
- Made Kotlin a first-class citizen; set the Kotlin baseline to 2.0.21.
- Clients now explicitly include the `storm-core` module.

## [1.3.8] - 2025-07-06
- Reflection performance improvements; scope-limited alias resolution.

## [1.3.7] - 2025-06-29
- Added parameter inlining for `SqlTemplate` and a `SqlTemplate` customizer.

## [1.3.6] - 2025-05-31
- Added Kotlin extension functions for convenient repository access.

## [1.3.5] - 2025-05-26
- Eliminated annotations from the metamodel; added a nullable reference method.

## [1.3.4] - 2025-05-18
- Full support for compound primary keys; improved nullability checks and ordering.

## [1.3.3] - 2025-05-05
- Refactored `SqlInterceptor` to use `ScopedValue`.

## [1.3.2] - 2025-05-03
- Aligned entity and projection repositories with existing persistence APIs; auto-register Jackson modules.

---

For releases prior to 1.3.2, see the
[GitHub Releases](https://github.com/storm-orm/storm-framework/releases) page.

[1.13.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.13.0
[1.12.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.12.1
[1.12.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.12.0
[1.11.6]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.6
[1.11.5]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.5
[1.11.4]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.4
[1.11.3]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.3
[1.11.2]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.2
[1.11.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.1
[1.11.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.11.0
[1.10.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.10.0
[1.9.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.9.1
[1.9.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.9.0
[1.8.2]: https://github.com/storm-orm/storm-framework/releases/tag/v1.8.2
[1.8.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.8.1
[1.8.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.8.0
[1.7.2]: https://github.com/storm-orm/storm-framework/releases/tag/v1.7.2
[1.7.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.7.1
[1.6.2]: https://github.com/storm-orm/storm-framework/releases/tag/v1.6.2
[1.6.1]: https://github.com/storm-orm/storm-framework/releases/tag/v1.6.1
[1.6.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.6.0
[1.5.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.5.0
[1.4.0]: https://github.com/storm-orm/storm-framework/releases/tag/v1.4.0
[1.3.8]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.8
[1.3.7]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.7
[1.3.6]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.6
[1.3.5]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.5
[1.3.4]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.4
[1.3.3]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.3
[1.3.2]: https://github.com/storm-orm/storm-framework/releases/tag/v1.3.2
