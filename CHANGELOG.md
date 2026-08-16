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

From the join onward, every clause accepts paths from any entity in the query, so referencing a joined table no longer needs a separate method. The `Any` variants are removed across all APIs, and migration is a rename per call site:

```kotlin
// 1.13 — a joined entity's field needed the Any variant
orm.entity<Role>().select()
    .innerJoin<UserRole>().on<Role>()
    .whereAny(UserRole_.user eq user)
    .resultList

// 1.14 — the join widens the query; the same call serves root and joined fields
orm.entity<Role>().select()
    .innerJoin<UserRole>().on<Role>()
    .where(UserRole_.user eq user)
    .resultList
```

- Removed `whereAny`, `whereAnyRef`, `whereAnyBuilder`, `havingAny`, `groupByAny`, `orderByAny` and `orderByDescendingAny`. In Java the lambda escalation goes with them: `.where(it -> it.whereAny(UserRole_.user, EQUALS, user))` becomes the typed overload `.where(UserRole_.user, EQUALS, user)`. A path on an entity that is not part of the query fails when the query is built, with an error naming the entity, the query root, and — when the table appears more than once — the paths that pin it.
- `andAny` and `orAny` are removed too, and `and`/`or` inherit the query root. In Kotlin they live in the `whereBuilder { }` scope, where a join widens the root so the same expression combines predicates across joined entities; outside a scope, the top-level `and`/`or` extensions combine predicates that share a root.
- Added `widen()` and `narrow(rootType)`, the two directions made explicit. `widen()` admits short-form references on a query that joins nothing; `narrow` restores the root after a join for the operations defined relative to it, verified against the query's FROM table. Those are `resultGroupedBy`, which types the map key, and `scroll`, whose key has to identify one row of the root.
- Renamed `typed(pkType)` to `typedId(pkType)`: it types the erased primary-key parameter, while `narrow` types the root. Added its missing null check.
- `fetch(...)` comes right after `select()`, before any join, enforced at compile time: resolving references is defined relative to the root, and a join widens the builder past it.
- Kotlin's `select { }` block is widened from the start and returns the widened builder, so joins made inside stay queryable in chained continuations. The `select { }` and `delete { }` blocks carry the chained builder's clause vocabulary in full: `whereId`, `whereRef`, `forLock`, the aliased `join(JoinType, ..., alias)` variants, and the template, subquery and cross joins. Type transitions and terminals stay on the builder the block returns.

### A grouping states an identity

A grouping says what one row stands for, and the generator emits the columns that express it. Naming the entity is enough:

```kotlin
// 1.13 — columns enumerated to satisfy the strictest dialect
orm.entity<User>()
    .select<CityCount, _, _> { "${City::class}, COUNT(*)" }
    .widen()
    .groupBy(City_.id, City_.name, City_.population)

// 1.14 — state the identity; the dialect decides the columns
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

- The relationship (`User_.city`), the key beyond it (`User_.city.id`), the query root's own key (`User_.id`) and a reference all state the same identity. Any other path still groups by its value.
- Selecting an entity selects the tables its foreign keys reach, so a grouping covers more than the table it names: those keys are added. Every foreign key is to-one, so this cannot move a row into a different group.
- **This changes the SQL generated for existing aggregate queries.** An aggregate over an entity with a foreign key previously grouped one table while selecting several, which H2 accepts and PostgreSQL, SQL Server and Oracle reject, so such queries were valid only on the permissive databases. Groupings that already enumerate their columns keep working, with those columns now redundant.
- A grouping that determines nothing, such as selecting `Pet` while grouping by `Pet_.owner` where one owner has many pets, is refused when the query is built, on every dialect rather than only the ones that would notice.

### Highlights

- The public API is JSpecify null-marked: every Java package carries `@NullMarked`, nullable type uses carry `org.jspecify.annotations.Nullable`, and the jakarta annotations are gone from Storm's signatures, so `jakarta.annotation-api` is no longer a Storm dependency. JSpecify itself is optional: the compilers and analysis tools read the annotations from bytecode by name. Kotlin callers get real `T`/`T?` types where the Java surface used to be platform types, checked strictly by Kotlin 2.1+. Type parameters that admit nullable arguments declare it — `V extends @Nullable Object` on `TypedMetamodel`, `AbstractMetamodel` and `AbstractKeyMetamodel`, `@Nullable` elements on `Instantiator`'s argument arrays, `@Nullable` on `Metamodel.getValue`, `T extends @Nullable Object` on `SqlCapture.execute` and `executeThrowing` — so Kotlin 2.1+ consumers compile the generated nullable metamodel chain. Generated metamodel sources adapt to the class path, referencing no annotation library when JSpecify is absent.
- `@StormTest` runs each test inside a database transaction that is rolled back afterwards, so tests no longer observe each other's writes and count assertions can be exact regardless of execution order. Storm's transaction blocks demarcate with savepoints inside it. `rollback = false` opts a class out for tests that need real commits.
- `@StormTest` and `@DataStormTest` run on the database the application deploys on: `database = POSTGRESQL` (or `MYSQL`, `MARIADB`, `MSSQL_SERVER`, `ORACLE`) starts a Testcontainers-managed container of a pinned default image, or of the image named by `image`, once per JVM and shares it across the test classes of the run; each test class, or Spring context, receives a freshly created database inside the container, so scripts run against an empty database as they do on H2 and classes never observe each other's tables. Testcontainers stays out of `storm-test`'s dependencies: a test that names a container database needs the database's Testcontainers 2 module (`org.testcontainers:testcontainers-postgresql` and so on) and JDBC driver on its classpath and fails with a message naming both when one is missing, while tests on H2 pull in nothing new. `TestDatabase.POSTGRESQL.container()` exposes the shared container, and `createDatabase()` on it a database of your own, for setups outside the annotations.
- `Projection<ID>`'s type argument is a checked contract instead of a phantom parameter. `ID` is the projection's row identity type; for a foreign-key-typed primary key that is the referenced table's key rather than the component value, which is why the interface declares no id accessor and `Ref.of(projection, id)` takes the id explicitly. Record validation rejects a declaration the record contradicts. The rule that a foreign key must not be an auto-generated primary key applies to entities only now.
- The implementation is sealed. `st.orm.core.template.impl` and `st.orm.core.repository.impl` are exported to Storm's own modules only; every declaration under storm-kotlin's `st.orm.template.impl` and `st.orm.repository.impl` is `internal`, including the `Flow` operators that collided with their kotlinx.coroutines namesakes and the top-level predicate factories that polluted completion. All five Kotlin modules compile in explicit API mode. The coroutine-aware SQL log recording the Ktor plugin shares is the one deliberate exception, published as `st.orm.template.recordSqlLog` behind `@InternalStormApi`. On the class path nothing changes; an application on the module path that reached into these packages no longer compiles.

### Other changes

- Both metamodel processors reject a cycle of non-Ref foreign keys at compile time, naming the cycle and the fix. Such a cycle used to compile and fail at first metamodel use with an `ExceptionInInitializerError`. The runtime record validation reports the same message.
- The metamodel processors converge on one contract: the Java annotation processor generates the `<Type>NullableMetamodel` chain variant KSP already generates, and KSP sources components from the primary constructor, contributes abstract properties only for sealed interfaces, and escapes keyword-named properties at every emission site.
- The Java annotation processor registers with Gradle as an aggregating incremental annotation processor, so attaching it no longer forces full recompilation on every change. A generation failure reports the record it occurred on and stops.
- The Gradle plugin wires the metamodel processor into every source set's processor configuration — `testAnnotationProcessor`, `kspTest`, and custom source sets — so entities in test sources get a metamodel. Javadoc generation gets the same `--enable-preview` flag as compilation.
- The Jackson converters key their mapper cache on the field's type, so a custom `@JsonSerialize`/`@JsonDeserialize` class shared by fields of different types is registered per field type, and a sealed interface reached through a container type has its permitted subtypes registered. The kotlinx converter keys its cache on the `@Json` flags alone.
- A record component's field metadata now includes annotations Java propagates to the backing field, accessor or constructor parameter, so `@JsonSerialize`/`@JsonDeserialize` on a `@Json` component are honored; they were silently ignored before.
- `@Json` fields serialize with the declared field type instead of the value's erased runtime type, so a polymorphic value writes the discriminator that reading the column expects.
- A Storm-managed transaction that receives a connection with auto-commit disabled fails with a message naming the two possible causes and the fix, instead of stating only the precondition.
- Storm-initiated transaction blocks resolve the Spring manager that owns their data source, which covers `JdbcTransactionManager`, a `JpaTransactionManager` backed by it, and, when no resource-bound manager claims the data source, a `JtaTransactionManager`. Blocks used to fail with `No TransactionManager found` under both of the latter while `@Transactional` kept working. An isolation level, `NESTED` propagation, or a propagation that suspends the surrounding transaction that a manager refuses, as a JTA manager does unless configured for them, is reported against the option passed to the block rather than as Spring's internal exception.
- What a transaction block joins, and which blocks share a connection, follows from the block structure rather than from what happens to be bound at the time. A `REQUIRED` block inside a `NOT_SUPPORTED` or `NEVER` block used to join the non-transactional connection, so its writes committed statement by statement and a rollback was lost; it opens a transaction of its own now. `MANDATORY` inside a non-transactional block fails and `NEVER` inside one runs, and both are checked against the block the enclosing code declares, whether or not it has executed a statement yet; the Spring bridge reports both as `PersistenceException`, as Storm's own transactions do. Data source consistency is checked per physical transaction: `REQUIRES_NEW` and `NOT_SUPPORTED` blocks bind whichever data source they first touch, so an audit write can go to a second database from inside a transaction on the first, where it used to fail with `Incompatible DataSource`. A boundary no longer binds the block that encloses it, which now starts on its own first touch.
- The `@DataStormTest` slice imports every auto-configuration the starters register in production, verified by a parity test. Repository AOP proxying was missing, so interface-level advice behaved differently under test. The slice also provides `JdbcTemplate`, `JdbcClient` and Testcontainers service connection support, and every import resolved from another artifact carries the `optional:` prefix.
- Transaction observations go through an observation convention, `StormTransactionObservationConvention`, the way query observations always did. The Ktor plugin's automatic binding forwarded queries only, so Ktor applications silently emitted no `storm.transaction` observations at all; it forwards transactions too now.
- Query observations carry the statement's shape identity as the low-cardinality `storm.shape` key value, derived unconditionally and cached per compiled template, so grouping by shape charts one query template where the text would split it.
- `StormConfig.sqlShapingKeys()` exposes the configuration keys whose values affect the generated SQL, derived from markers on the key declarations, and the template cache key includes exactly these. The hand-maintained key list in the cache is gone.
- `SqlLog` carries the diagnostics API only: summary rendering and hydration-shape analysis live in `SqlLogRenderer`, call-site capture in `CallSiteCapture`, both internal. How summaries render is configured through the `storm.sql_log.*` system properties or the corresponding Spring and Ktor keys.
- The deliberately paired artifacts — `storm-java21`/`storm-kotlin`, the two Spring Boot starters, `storm-jackson2`/`storm-jackson3` and the two metamodel processors — ship the same fully qualified names, one half per class path. Each half bans its twin through a maven-enforcer rule, so a dependency tree that picks up both fails the build with a message naming the conflict and the path that introduces it. The one-per-classpath rule is documented centrally in the installation guide.

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
