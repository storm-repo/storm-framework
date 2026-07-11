# Changelog

All notable changes to Storm are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/), and Storm follows
[Semantic Versioning](https://semver.org/).

Releases are tag-driven and published to
[Maven Central](https://central.sonatype.com/namespace/st.orm) (`st.orm`) and,
for the CLI, to [npm](https://www.npmjs.com/package/@storm-orm/cli)
(`@storm-orm/cli`). Full release notes for every version are on the
[GitHub Releases](https://github.com/storm-orm/storm-framework/releases) page.

## [Unreleased]

Framework integration points are now instance-scoped (#198), and the Ktor integration standardizes on Ktor's built-in dependency injection (#206). Breaking changes are accepted with no deprecation shims.

### Added

- `ORMTemplate.builder(dataSource)` / `builder(connection)` (core, Java 21 and Kotlin APIs) with instance-scoped integration strategies: `connectionProvider`, `transactionTemplateProvider`, `exceptionMapper` and `queryObserver`. `ServiceLoader` discovery remains the fallback for templates built without explicit strategies.
- `ExceptionMapper` SPI: maps failures raised during query execution to the exception thrown to the caller, enabling platform hierarchies such as Spring's `DataAccessException` (the translation itself lands with #199).
- `QueryObserver` SPI: observes query executions (operation, entity/projection type, execution kind, timing, outcome) for metrics and tracing bindings (the Spring Boot auto-configuration lands with #203).
- `springOrmTemplate(dataSource) { transactionManagers }` (storm-kotlin-spring): the canonical plain-Spring composition, replacing `@EnableTransactionIntegration`.
- `st.orm.spring.SpringTransactionTemplateProvider` (storm-spring): gives Java applications transaction-scoped entity caching under Spring-managed transactions without reflective probes.
- The Ktor plugin gains `connectionProvider`, `transactionTemplateProvider`, `exceptionMapper` and `queryObserver` slots on `install(Storm) { }`.
- The Ktor plugin exposes the `ORMTemplate` and every registered repository through Ktor's built-in dependency injection (`ktor-server-di`), each repository under its own interface type: `val visits: VisitRepository by dependencies`. Disable with `registerDependencies = false`.
- The Ktor plugin supports multiple databases: `database("name") { }` blocks declare additional databases with their own template, repositories, schema validation, migration hook and lifecycle, configured in code or under `storm.databases.<name>.*` in HOCON. The packages declared per database partition repositories and schema validation; access goes through `orm("name")`, `repository<T>("name")` and named dependency injection.
- New `storm-micrometer` module: `MicrometerQueryObserver` reports query executions as Micrometer Observations named `storm.query`, with low-cardinality key values for the operation, execution kind and data type, and the SQL statement as a high-cardinality value for trace handlers. Naming and key values are overridable via a custom `ObservationConvention`.
- The Ktor plugin binds query observations automatically: register an `ObservationRegistry` in the dependency container and every query is observed, tagged `storm.database=<name>` (`primary` for the primary database). An explicit `queryObserver` takes precedence; without a registry, queries run unobserved.
- `transactional { }` route DSL (storm-ktor): every route declared inside the block runs in its own transaction, opened before the handler, committed on completion and rolled back on exception, with the same options as `transaction { }`. The transaction binds to the first template the handler touches, so named databases work unchanged.
- Storm Gradle plugin (`id("st.orm")`, published to the Gradle Plugin Portal, version-aligned with the BOM): one plugin application imports the BOM, adds the core dependencies for the Kotlin or Java path, wires the metamodel processor (KSP or annotation processor), selects the Kotlin compiler-plugin variant matching the project's Kotlin version, and sets the Java preview flags. A `storm { }` extension covers opt-outs and the variant override.
- Programmatic transactions for Java: `Transactions.transaction(...)` in storm-java21 with the same semantics as Kotlin's `transaction { }` — all seven propagation modes, isolation, timeout, read-only, rollback-only, and commit/rollback callbacks — blocking and virtual-thread friendly, with checked exceptions propagating to the caller unchanged. Options via the `st.orm.TransactionOptions` record; global and thread-scoped defaults via `setGlobalTransactionOptions` / `withTransactionOptions`.
- The transaction vocabulary is now shared by both language APIs from storm-foundation: `st.orm.TransactionPropagation`, `st.orm.TransactionIsolation`, `st.orm.TransactionTimedOutException`, `st.orm.UnexpectedRollbackException`, and the language-neutral `st.orm.Transaction` handle (the Kotlin `Transaction` extends it with suspend callback overloads).
- The Ktor integration docs gain an Error Handling section with StatusPages recipes mapping Storm exceptions to HTTP responses (missing row to 404, constraint violation and optimistic lock to 409), including portable SQL-state-based constraint detection.
- `Sql.dataType()`: the primary entity or projection type of a statement, now also derived for SELECT statements from the selected or queried table, and reported to query observers as the statement's data type.
- Java Spring applications gain the full programmatic-transaction bridge: `SpringTransactionTemplateProvider` constructed with the application's transaction managers runs Storm's `Transactions.transaction(...)` blocks through Spring's `PlatformTransactionManager`, joins active `@Transactional` transactions, and picks the manager matching each template's `DataSource` in multi-data-source applications. `SpringOrmTemplate.of(dataSource, transactionManagers)` is the canonical plain-Spring composition for Java.
- Shared Spring Boot auto-configurations in storm-spring (`st.orm.spring.boot`): `StormTransactionAutoConfiguration` (Spring-aware provider beans), `StormValidationAutoConfiguration` (startup schema validation), and the `storm.*` configuration properties (`StormProperties`), used by both starters. Configuration keys are unchanged.
- SQL failures raised by Storm translate to Spring's `DataAccessException` hierarchy in Spring applications: `SpringExceptionMapper` (storm-spring) translates on vendor error codes with `SQLException` subclass and SQL state fallback, auto-configured by both starters (`storm.exception-translation.enabled=false` to disable) and applied by the `SpringOrmTemplate.of` / `springOrmTemplate` compositions. Failures without a `SQLException` cause keep Storm's own exceptions.
- The Spring Boot starters bind query observations automatically: with an `ObservationRegistry` bean present (Actuator provides one), every query executed by the auto-configured template reports as a `storm.query` Micrometer Observation via the shared `StormObservationAutoConfiguration`. The starters ship storm-micrometer; override the convention with an `ObservationConvention` bean, replace the binding with a `QueryObserver` bean, or disable via `management.observations.enable.storm.query=false`.

### Changed

- `transaction { }` / `transactionBlocking { }` now bind to the first template that executes inside the block: the block records the requested options, and the template's transaction provider opens the actual transaction on first use. Signatures and semantics are unchanged for single-integration applications; a block that never touches a template completes as a no-op, and mixing templates with different transaction providers in one block fails fast.
- The `TransactionTemplate` SPI is reshaped from callback-wrapping `execute()` to `open()`/`complete()` handles to support the lazy binding.
- Ambiguous `ServiceLoader` resolution of connection or transaction template providers (two enabled candidates without a defined order) now throws a descriptive error naming the candidates instead of silently picking one; provider enablement is re-evaluated per resolution instead of being frozen at first use.
- The Spring Boot starters contribute Spring-aware `ConnectionProvider`/`TransactionTemplateProvider` beans (backing off to user-defined beans) and consume optional `ExceptionMapper`/`QueryObserver` beans when creating the template.
- Ktor is upgraded from 3.1.2 to 3.2.3, the newest Ktor consumable with the project's Kotlin 2.0 toolchain (Ktor 3.3+ requires Kotlin 2.2). The `ktor.version` property now lives in the parent pom.
- The Ktor plugin's configuration class is renamed from `StormConfiguration` to `StormPluginConfig`, removing the confusion with core's `StormConfig`. Application code is unaffected unless it named the type explicitly; the `install(Storm) { }` receiver is inferred.
- The default JDBC transaction machinery moved from storm-kotlin to storm-core as Java (`st.orm.core.spi.JdbcTransactionContext`, `JdbcTransactionTemplateProviderImpl`, `JdbcConnectionProviderImpl`), replacing the core stubs that rejected transactions: core-only Java applications now get real JDBC transactions. The blocking transaction orchestration lives once in core (`TransactionRunner`), driven by both the Java API and Kotlin's `transactionBlocking { }`; the Kotlin transaction enums and exceptions moved from `st.orm.template` to `st.orm` (imports change; behavior identical). The `TransactionScope`/`TransactionTemplate` SPI now carries the typed enums instead of string/int values.
- The Spring repository scanning, autowire-candidate resolution, and AOP proxying engine is single-sourced in storm-spring (`AbstractRepositoryBeanFactoryPostProcessor`); storm-kotlin-spring now depends on storm-spring and contributes only the Kotlin bindings. The Kotlin `RepositoryBeanFactoryPostProcessor` and `springOrmTemplate` moved to `st.orm.spring.kotlin`, and Kotlin subclasses override the engine's methods instead of properties: `override val repositoryBasePackages` becomes `override fun getRepositoryBasePackages()`, likewise `getOrmTemplateBeanName()` and `getRepositoryPrefix()`. Java subclasses are unaffected.
- One starter per application: the Java and Kotlin stacks share class names (`st.orm.template.ORMTemplate`, `st.orm.repository.Repository`), so storm-spring-boot-starter and storm-kotlin-spring-boot-starter cannot be mixed in one application. The starters now share their transaction, validation, and properties auto-configurations from storm-spring.
- The auto-configured `ORMTemplate` and startup schema validation condition on a single `DataSource` candidate: applications exposing several `DataSource` beans (one pool per domain) boot cleanly with the auto-configured template backing off, or binding to the `@Primary` pool when one is marked.
- The repository AOP post-processor bean is renamed from `javaRepositoryProxyingPostProcessor`/`kotlinRepositoryProxyingPostProcessor` to `stormRepositoryProxyingPostProcessor`.
- Java applications without a `PlatformTransactionManager` no longer get a Spring-bound `ConnectionProvider`: the template falls back to Storm's own JDBC transactions (previously connections were bound through `DataSourceUtils` unconditionally).
- Spring Boot applications: SQL failures from Storm repositories and templates now surface as Spring `DataAccessException` subtypes instead of `PersistenceException` (translation is auto-configured; see Added). Catch blocks and rollback rules that reference `PersistenceException` for SQL failures need updating, or set `storm.exception-translation.enabled=false` to keep the previous behavior.

### Removed

- `@EnableTransactionIntegration` and `SpringTransactionConfiguration` (storm-kotlin-spring): the static, JVM-global transaction manager list is gone. Use `springOrmTemplate(...)` (plain Spring) or the starter (Spring Boot); multiple application contexts in one JVM no longer interfere.
- The Spring modules no longer register `ServiceLoader` providers, and the reflective Spring probes are removed from storm-core and storm-kotlin: templates never silently enlist in Spring transactions based on classpath presence. Plain templates created with `ORMTemplate.of(dataSource)` inside a Spring application now run independently of Spring transactions; compose with `springOrmTemplate` or the builder to integrate. This also removes the "programmatic and Spring managed transactions cannot be mixed" guard, superseded by the per-block provider check.
- `Providers.getConnection` / `Providers.releaseConnection`: connections are acquired through the template's own connection provider.
- `st.orm.spring.impl.SpringConnectionProviderImpl`, `SpringTransactionTemplateProviderImpl` and `TransactionAwareConnectionProviderImpl` are replaced by the public `st.orm.spring.SpringConnectionProvider` and `SpringTransactionTemplateProvider`.
- `st.orm.spring.impl.ResolverRegistration` (both language stacks): the autowire-candidate resolver is installed by the scanning engine itself.
- `storm-ktor-koin`: Ktor's built-in dependency injection is the supported DI path. Koin users keep full capability with a few lines of application code; the Ktor integration docs include the recipe.

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
