# Get Started

Storm is a modern SQL Template and ORM framework for Kotlin 2.0+ and Java 21. It uses immutable data classes and records instead of proxied entities, giving you predictable behavior, type-safe queries, and high performance.

## Choose Your Path

> **Fastest start:** each example app is a GitHub template. Click **Use this template** to generate a runnable project, then replace the sample entities with your own: [Kotlin + Ktor](https://github.com/storm-orm/storm-example-kotlin-ktor/generate) · [Kotlin + Spring Boot](https://github.com/storm-orm/storm-example-kotlin-spring-boot-4/generate) · [Java + Spring Boot](https://github.com/storm-orm/storm-example-java-spring-boot-4/generate).

Two ways to get started, and both reach the same working setup: follow the guides by hand, or let your AI coding tool do it. Pick whichever fits your workflow.

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
<TabItem value="manual" label="Manual" default>

### Manual Setup

Follow these three steps in order for the fastest path from zero to a working application.

**1. Installation**

Set up your project with the right dependencies, build flags, and optional modules.

**[Go to Installation](installation.md)**

**2. First Entity**

Define your first entity, create an ORM template, and perform insert, read, update, and remove operations.

**[Go to First Entity](first-entity.md)**

**3. First Query**

Write custom queries, build repositories, stream results, and use the type-safe metamodel.

**[Go to First Query](first-query.md)**

</TabItem>
<TabItem value="ai" label="AI-Assisted">

### AI-Assisted Setup

If you use an AI coding tool (Claude Code, Cursor, GitHub Copilot, Windsurf, or Codex), Storm provides rules, skills, and an optional database-aware MCP server that give the AI deep knowledge of Storm's conventions. The AI can generate entities from your schema, write queries, and verify its own work against a real database.

**1. Install the Storm CLI and run it in your project:**

```bash
npx @storm-orm/cli init
```

The interactive setup configures your AI tool with Storm's rules and skills, and optionally connects it to your development database for schema-aware code generation.

**2. Ask your AI tool to set up Storm:**

Once `storm init` has configured your tool, you can ask it to add the right dependencies, create entities from your database tables, and write queries. The AI has access to Storm's full documentation and your database schema.

For example:
- "Add Storm to this project with Spring Boot and PostgreSQL"
- "Set up Storm with Ktor and PostgreSQL"
- "Create entities for the users and orders tables"
- "Write a repository method that finds orders by status with pagination"

**3. Verify:**

Storm's AI workflow includes built-in verification. The AI can run `ORMTemplate.validateSchema()` to prove entities match the database and `SqlCapture` to inspect generated SQL, all in an isolated H2 test database before anything touches production.

See [AI-Assisted Development](ai.md) for the full setup guide, available skills, and MCP server configuration.

</TabItem>
</Tabs>

---

## What's Next

Once you have completed the steps above, explore the features that match your needs:

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
