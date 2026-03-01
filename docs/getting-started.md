# Getting Started

Storm is a modern SQL Template and ORM framework for Kotlin 2.0+ and Java 21+. It uses immutable data classes and records instead of proxied entities, giving you predictable behavior, type-safe queries, and high performance.

## Design Philosophy

Storm is built around a simple idea: your data model should be a plain value, not a framework-managed object. In Storm, entities are Kotlin data classes or Java records. They carry no hidden state, no change-tracking proxies, and no lazy-loading hooks. You can create them, pass them across layers, serialize them, compare them by value, and store them in collections without worrying about session scope, detachment, or side effects. What you see in the source code is exactly what exists at runtime.

This stateless design is a deliberate trade-off. Traditional ORMs like JPA/Hibernate give you automatic dirty checking and transparent lazy loading, but at the cost of complexity: you must reason about managed vs. detached state, proxy initialization, persistence context boundaries, and cascading rules that interact in subtle ways. Storm avoids all of this. When you call `update`, you pass the full entity. When you query a relationship, you get the result in the same query. There are no surprises.

Storm is also SQL-first. Rather than abstracting SQL away behind a query language (like JPQL) or a verbose criteria builder, Storm embraces SQL directly. Its SQL Template API lets you write real SQL with type-safe parameter interpolation and automatic result mapping. For common CRUD patterns, the type-safe DSL and repository interfaces provide concise, compiler-checked alternatives, but the full power of SQL is always available when you need it.

The framework is organized around three core abstractions:

- **Entity** is your data model. A Kotlin data class or Java record with a few annotations (`@PK`, `@FK`) that describe its mapping to the database. Storm derives table and column names automatically, so annotations are only needed for primary keys, foreign keys, and cases where the naming convention does not match.
- **Repository** provides CRUD operations and type-safe queries for a specific entity. You define an interface, write query methods with explicit bodies using the DSL, and Storm handles the rest. No magic method-name parsing, no hidden query generation.
- **SQL Template** gives you direct access to SQL with type-safe parameter binding and result mapping. You write real SQL, embed parameters and entity types directly in the query string, and get back typed results. This is the escape hatch when the DSL is not enough, and it is a first-class citizen in Storm, not an afterthought.

These abstractions share a common principle: explicit behavior over implicit magic. Every query is visible in the source code. Every relationship is loaded when you ask for it. Every transaction boundary is declared, not inferred. This makes Storm applications straightforward to debug, profile, and reason about.

## How Storm Differs from JPA

If you are coming from JPA/Hibernate, the biggest shift is moving from mutable, proxy-backed entities with a managed lifecycle to stateless, immutable values. Storm has no persistence context, no first-level cache, no `EntityManager`, and no automatic change detection. This eliminates entire categories of bugs (LazyInitializationException, detached entity errors, unexpected flush ordering) while making performance predictable. For a detailed comparison, see the [Migration from JPA](migration-from-jpa.md) guide and the [Storm vs Other Frameworks](comparison.md) feature comparison.

## Step-by-Step Setup

This guide is split into three steps. Follow them in order for the fastest path from zero to a working application.

## 1. Installation

Set up your project with the right dependencies, build flags, and optional modules.

**[Go to Installation](installation.md)**

## 2. First Entity

Define your first entity, create an ORM template, and perform insert, read, update, and delete operations.

**[Go to First Entity](first-entity.md)**

## 3. First Query

Write custom queries, build repositories, stream results, and use the type-safe metamodel.

**[Go to First Query](first-query.md)**

---

## What's Next

Once you have completed the getting-started guides, explore the features that match your needs:

**Core Concepts:**
- [Entities](entities.md) -- annotations, nullability, naming conventions
- [Queries](queries.md) -- query DSL, filtering, joins, aggregation
- [Relationships](relationships.md) -- one-to-one, many-to-one, many-to-many
- [Repositories](repositories.md) -- custom repository pattern

**Operations:**
- [Transactions](transactions.md) -- transaction management and propagation
- [Upserts](upserts.md) -- insert-or-update operations
- [Batch Processing & Streaming](batch-streaming.md) -- bulk operations and large datasets
- [Dirty Checking](dirty-checking.md) -- automatic change detection on update

**Integration:**
- [Spring Integration](spring-integration.md) -- Spring Boot Starter, auto-configuration, and DI
- [Testing](testing.md) -- JUnit 5 integration and statement capture
- [Database Dialects](dialects.md) -- database-specific features

**Advanced:**
- [Refs](refs.md) -- lightweight entity references for deferred loading
- [Projections](projections.md) -- read-only views of entities
- [SQL Templates](sql-templates.md) -- raw SQL with type safety
- [Metamodel](metamodel.md) -- compile-time type-safe field references
- [JSON Support](json.md) -- JSON columns and aggregation
- [Entity Serialization](serialization.md) -- JSON serialization with Ref support

**Migration:**
- [Migration from JPA](migration-from-jpa.md) -- step-by-step guide
- [Storm vs Other Frameworks](comparison.md) -- feature comparison
