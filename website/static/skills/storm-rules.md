## Storm ORM

This project uses the [Storm ORM framework](https://orm.st) for database access.
Storm is a modern SQL Template and ORM for Kotlin 2.0+ and Java 21+, built around
immutable data classes and records instead of proxied entities.

### Storm Annotations and API

Storm can run on top of JPA, but when generating code, always use Storm's own annotations and JDBC-based API:
- Use `@PK`, not `@Id` or `@GeneratedValue`
- Use `@FK`, not `@ManyToOne` or `@JoinColumn`
- Use `@DbTable`, not `@Table` or `@Entity`
- Use `@DbColumn`, not `@Column`
- Use `@UK`, not `@UniqueConstraint`
- Use `@Version` from `st.orm`, not from `jakarta.persistence`
- Use `DataSource.orm` or `ORMTemplate.of(dataSource)`, not `EntityManager`
- Do not add `jakarta.persistence-api`, Hibernate, or any JPA implementation unless the project already uses them

Storm works directly with JDBC `DataSource`. There is no persistence context, no session, no lazy proxy objects.

### Framework Detection

Before suggesting dependencies, patterns, or configuration, detect which framework the project uses by examining the build file and existing dependencies:

- **Spring Boot**: build file contains `storm-kotlin-spring-boot-starter`, `storm-spring-boot-starter`, `spring-boot-starter`, or `@SpringBootApplication` in the codebase.
- **Ktor**: build file contains `storm-ktor`, `ktor-server-core`, or `io.ktor` dependencies.
- **Standalone**: neither Spring Boot nor Ktor detected. The project uses Storm directly with `ORMTemplate.of(dataSource)`.

Adapt your suggestions to the detected framework:
- **Spring Boot**: use Storm's programmatic transactions — suspend `transaction { }` at the service level (never in controllers), bridged with `runBlocking` only at non-suspend entry points such as MVC handlers (declarative `@Transactional` also works), constructor injection, `application.yml` for config.
- **Ktor**: use `install(Storm)` plugin, `transaction { }` blocks, `application.conf` (HOCON) for config, `call.orm` for route access.
- **Standalone**: use `DataSource.orm` or `ORMTemplate.of(dataSource)`, programmatic `transaction { }` blocks.

### Query and Template Rules

- **Always prefer QueryBuilder and metamodel-based methods** for joins, where clauses, ordering, etc. Only fall back to SQL template lambdas when QueryBuilder cannot express the query.
- **Joins**: use `.innerJoin<Entity>().on<OtherEntity>()` unless it cannot be expressed with entity classes.
- **Reified vs `::class` style (Kotlin)**: prefer the reified forms (`orm.entity<User>()`, `.innerJoin<X>().on<Y>()`, `select<Result, _, _> { template }`, `resultList<T>()`), but keep each snippet internally consistent — never mix reified and `::class` API styles in one query or code block. When surrounding code uses the `::class` style (or a call has no reified equivalent, forcing `::class` into the snippet), match it: write `select(Result::class) { template }` rather than mixing in underscores. Template interpolations such as `${User::class}` are template syntax, not API style — they never count as mixing.
- **Template lambdas**: when you must use a template expression, write it as a lambda (`{ "..." }`) — never use `TemplateString.raw()`.
- **Compiler plugin interpolation**: with the Storm compiler plugin (which Kotlin projects should always use), standard `${}` interpolation inside template lambdas is automatically processed. Do not call `t()` manually — it exists only as a fallback for projects without the compiler plugin.
- **Metamodel in templates**: even inside template lambdas, use metamodel references (`${User_.email}`) instead of hardcoded column names wherever possible.
- **Refs stay queryable**: a `Ref<T>` foreign key can be filtered, ordered, grouped, and selected through with the metamodel (`User_.city.country.name`); Storm joins the referenced table on demand, only when a query navigates beyond the foreign key. Prefer a `Ref` for foreign keys not hydrated on most reads to avoid eager join fan-out while keeping the relationship queryable. Nodes beyond a reference are navigation-only: usable in `where`, `orderBy`, `groupBy`, `having`, and selected columns, but not in value operations; group by the reference itself with `resultGroupedByRef`. A self-referential foreign key must be a `Ref`; it is navigable (the table joins itself, one alias per occurrence), with the typed metamodel bounded to two hops and deeper cyclic paths named as strings.

If the project does not yet have Storm dependencies in its build file (pom.xml,
build.gradle.kts), use /storm-setup to help the user configure their project.
Detect the project's Kotlin or Java version from the build file to recommend the
correct dependencies and compiler plugin version.

Available Storm skills:
- /storm-setup - Help configure Maven/Gradle dependencies
- /storm-docs - Load full Storm documentation
- /storm-entity-kotlin or /storm-entity-java - Create entities
- /storm-repository-kotlin or /storm-repository-java - Write repositories
- /storm-query-kotlin or /storm-query-java - Write queries with the QueryBuilder
- /storm-sql-kotlin or /storm-sql-java - Write SQL Templates
- /storm-json-kotlin or /storm-json-java - JSON columns and JSON aggregation
- /storm-serialization-kotlin or /storm-serialization-java - Entity serialization for REST APIs
- /storm-migration - Write Flyway/Liquibase migration SQL

When the user asks about Storm topics, suggest the relevant skill if they need detailed guidance.
