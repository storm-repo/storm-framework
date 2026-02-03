# Storm vs Other Frameworks

There is no universally “best” database framework. Each has strengths suited to different situations, team preferences, and project requirements. Teams approach data access differently, including using frameworks at various abstraction levels or even plain SQL. This page provides an honest comparison to help you evaluate whether Storm fits your needs, particularly if you value explicit and predictable behavior and fast development. We encourage you to explore the linked documentation for each framework and form your own conclusions.

## Storm vs JPA/Hibernate

| Aspect | Storm | JPA/Hibernate                            |
|--------|-------|------------------------------------------|
| **Entities** | Immutable records/data classes | Mutable classes with getters/setters     |
| **State** | Stateless; no persistence context | Managed entities                         |
| **Loading** | Loading in single query | Lazy loading common              |
| **N+1 Problem** | Prevented by design; requires explicit opt-in | Common pitfall                           |
| **Queries** | Type-safe DSL, SQL Templates | JPQL, Criteria API                       |
| **Caching** | Transaction-scoped observation | First/second level cache                 |
| **Transactions** | Programmatic + `@Transactional` (Spring) | `@Transactional`, JTA, container-managed |
| **Learning Curve** | Gentle; SQL-like | Steep; many concepts                     |
| **Magic** | What you see is what you get | Proxies, bytecode enhancement            |

### When to Choose Storm

- You want predictable, explicit database behavior
- N+1 queries have been a recurring problem
- You prefer immutable data structures
- You value simplicity over complexity
- You're using Kotlin and want idiomatic APIs

### When to Choose JPA/Hibernate

- You rely on second-level caching
- You have complex inheritance hierarchies
- You have an existing JPA codebase to maintain
- You need JPA compliance for vendor reasons
- You want access to a large community and extensive resources

## Storm vs Spring Data JPA

| Aspect | Storm | Spring Data JPA |
|--------|-------|-----------------|
| **Foundation** | Custom ORM | JPA/Hibernate |
| **Repositories** | Interface with default methods | Interface with method naming, `@Query` |
| **Query Methods** | Explicit DSL in method body | Derived from method names, `@Query` |
| **Entities** | Records/data classes | JPA entities |
| **State** | Stateless | Managed |
| **Transactions** | Programmatic + `@Transactional` (Spring) | `@Transactional` (Spring-managed) |

### When to Choose Storm

- You want stateless, immutable entities
- You prefer explicit query logic over naming conventions
- You want to avoid JPA's complexity

### When to Choose Spring Data JPA

- You need JPA features (lazy loading, caching)
- You like query derivation from method names
- You're already invested in the JPA ecosystem

## Storm vs MyBatis

| Aspect | Storm | MyBatis |
|--------|-------|---------|
| **Approach** | Stateless ORM | SQL mapper |
| **SQL Definition** | Inferred from entities, SQL Templates (optional) | XML files or annotations |
| **Result Mapping** | Automatic from entity definitions | Manual XML/annotation mapping |
| **Entities** | Records/data classes with annotations | POJOs, manual mapping |
| **Relationships** | Automatic via `@FK` | Manual nested queries/joins |
| **Type Safety** | Compile-time checked | String SQL, typed result mapping |
| **N+1 Problem** | Prevented by design; requires explicit opt-in | Manual optimization |
| **Transactions** | Programmatic + `@Transactional` (Spring) | Manual or Spring `@Transactional` |
| **Dynamic SQL** | Kotlin/Java code | XML tags (`<if>`, `<foreach>`) |
| **Learning Curve** | Gentle; annotation-based | Moderate; XML knowledge helpful |

### When to Choose Storm

- You want automatic entity mapping without XML
- You prefer type-safe queries over string SQL
- You want relationships handled automatically
- You value compile-time safety
- You're starting a new project without legacy SQL

### When to Choose MyBatis

- You have complex SQL that doesn't fit ORM patterns
- You need fine-grained control over every query
- You're working with legacy databases or stored procedures
- You need XML-based SQL externalization

## Storm vs jOOQ

| Aspect | Storm | jOOQ |
|--------|-------|------|
| **Approach** | Entity-first, convention | SQL-first, code generation |
| **Type Safety** | Metamodel from entities | Generated from schema |
| **Setup** | Define entities → code generation | Schema → code generation |
| **Entities** | Records/data classes with `Entity` | Records or POJOs |
| **Query Style** | Repository + ORM DSL + SQL Templates | SQL-like DSL |
| **Relationships** | Automatic from `@FK` | Manual joins |
| **Transactions** | Programmatic + `@Transactional` (Spring) | DSL context, Spring integration |
| **License** | Apache 2.0 | Commercial for some DBs |

### When to Choose Storm

- You prefer defining entities in code, not generating from schema
- You want automatic relationship handling
- You value convention over configuration
- You need a fully open-source solution

### When to Choose jOOQ

- You prefer pure SQL control
- You want native DSL support for advanced SQL features (window functions, CTEs)

## Storm vs JDBI

| Aspect | Storm | JDBI |
|--------|-------|------|
| **Level** | Stateless ORM | Low-level SQL mapping |
| **Entities** | Automatic from annotations | Manual mapping |
| **Relationships** | Automatic via `@FK` | Manual |
| **Type Safety** | Metamodel DSL | String SQL |
| **Transactions** | Programmatic + `@Transactional` (Spring) | Manual, `@Transaction` annotation |

### When to Choose Storm

- You want automatic entity mapping
- You need relationship handling
- You prefer type-safe queries over raw SQL

### When to Choose JDBI

- You want full SQL control
- You prefer minimal abstraction
- You have complex queries that don't fit ORM patterns

---

## Kotlin-Only Frameworks

The following frameworks are Kotlin-only. Storm supports both Kotlin and Java.

## Storm vs Exposed

| Aspect | Exposed | Storm                                |
|--------|---------|--------------------------------------|
| **Language** | Kotlin only | Kotlin + Java                        |
| **APIs** | DSL (SQL) + DAO (ORM) | Unified ORM + SQL Templates          |
| **Table Definition** | DSL objects (`object Users : Table()`) | Annotations on data classes          |
| **Entities (DAO)** | Mutable, extend `Entity` class | Immutable data classes/records       |
| **Relationships** | Lazy references, manual loading | Loading in single query              |
| **N+1 Problem** | Possible with DAO | Prevented by design; requires explicit opt-in                 |
| **Coroutines** | Supported (added later) | First-class from the start           |
| **Type Safety** | Column references | Metamodel DSL                        |
| **Transactions** | Required `transaction {}` block | Optional, programmatic + declarative |

### When to Choose Storm

- You need Java support alongside Kotlin
- You want immutable entities without base class inheritance
- You prefer annotation-based entity definitions
- N+1 queries are a concern
- You want relationships loaded automatically
- You need full support for transaction propagation modes

### When to Choose Exposed

- You're building a Kotlin-only project
- You prefer DSL-based table definitions
- You want to switch between SQL DSL and DAO styles
- You like the JetBrains ecosystem integration
- You need fine-grained control over lazy loading
- You need R2DBC support for reactive database access*

*Storm uses JDBC and relies on JVM virtual threads for non-blocking I/O instead of R2DBC.

## Storm vs Ktorm

| Aspect | Ktorm | Storm |
|--------|-------|-------|
| **Language** | Kotlin only | Kotlin + Java |
| **Entities** | Interfaces extending `Entity` | Data classes with annotations |
| **Table Definition** | DSL objects (`object Users : Table<User>`) | Annotations on data classes |
| **Query Style** | Sequence API, DSL | ORM DSL + SQL Templates |
| **Relationships** | References, manual loading | Automatic loading |
| **N+1 Problem** | Possible | Prevented by design; requires explicit opt-in |
| **Code Generation** | None required | Optional metamodel |
| **Immutability** | Mutable entity interfaces | Immutable data classes |
| **Coroutines** | Limited | First-class support |
| **Transactions** | `useTransaction {}` block | Programmatic + `@Transactional` (Spring) |

### When to Choose Storm

- You need Java support alongside Kotlin
- You want immutable data classes, not interfaces
- You prefer annotation-based definitions
- N+1 prevention is important
- You want automatic relationship loading

### When to Choose Ktorm

- You're building a Kotlin-only project
- You prefer no code generation
- You like the Sequence API style
- You want a lightweight, minimal dependency footprint
- You prefer DSL-based table definitions

---

## Feature Comparison

| Feature | Storm | JPA | Spring Data | MyBatis | jOOQ | JDBI | Exposed | Ktorm |
|---------|-------|-----|-------------|---------|------|------|---------|-------|
| Lines per entity | ~5 | ~30* | ~30* | ~20+ | Generated | ~15 | ~12 | ~15 |
| Immutable entities | Yes | No | No | Yes | Yes | Yes | DSL only | No |
| Type-safe queries | Yes | Criteria | No | No | Yes | No | Yes | Yes |
| Automatic relationships | Yes | Yes** | Via JPA | No | No | No | DAO only | No |
| Cascade persist | No | Yes | Yes | No | No | No | No | No |
| N+1 prevention | Yes | No | No | No | Manual | Manual | No | No |
| Lazy loading | Refs | Yes | Yes | No | No | No | Yes | Yes |
| SQL Templates | Yes | No | No | XML/Ann | Yes | Yes | No | No |
| Transactions | Both | Both | Declarative | Both | Programmatic | Both | Required | Required |
| Kotlin support | First-class | Good | Good | Good | Good | Good | Native | Native |
| Coroutines | Yes | No | No | No | No | No | Yes | Limited |
| Runtime mechanism | Codegen* | Bytecode | Bytecode | Reflection | Codegen | Reflection | Reflection | Reflection |
| Spring integration | Yes | Yes | Native | Yes | Yes | Yes | Yes | Yes |
| Java support | Yes | Yes | Yes | Yes | Yes | Yes | No | No |
| Community | New | Huge | Huge | Large | Medium | Medium | Medium | Small |

*\* JPA/Spring Data lines without Lombok; ~10 lines with Lombok. Storm uses codegen with reflection fallback.*

*\*\* JPA relationships are runtime-managed via proxies.*

## Summary

Storm is a newer framework, so community resources and third-party tutorials are still growing. However, the API is designed to be intuitive for developers familiar with SQL and modern Kotlin/Java.

Choose Storm if you value:
- **Simplicity** over complexity
- **Predictability** over magic
- **Immutability** over managed state
- **Explicit** over implicit behavior
- **Kotlin-first** development (or modern Java with records)

## Framework Links

- [Hibernate ORM](https://hibernate.org/orm/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [MyBatis](https://mybatis.org/mybatis-3/)
- [jOOQ](https://www.jooq.org/)
- [JDBI](https://jdbi.org/)
- [Exposed](https://github.com/JetBrains/Exposed)
- [Ktorm](https://www.ktorm.org/)