import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# SQL Logging

When debugging performance issues or tracing application behavior, you often need visibility into the SQL statements your ORM generates. Standard JDBC logging shows raw statements with `?` placeholders, giving you no context about what the statement operates on or what the actual parameter values were.

Storm logs statements where they execute, under the `st.orm.sql` logger. Nothing has to be annotated and nothing about the execution changes: compiled query plans and the template cache stay in effect, so what you observe is the path that runs in production.

## Turning It On

Raise the log level; there is no other switch.

```yaml
logging:
  level:
    st.orm.sql: DEBUG    # statements as sent, with placeholders
```

Every executed statement is logged once, prefixed with what it does and what it targets:

```
SQL (SELECT City):
	SELECT c.id, c.name
	FROM city c
	WHERE c.id = ?
```

### Parameter Values

At `TRACE` the parameter values are rendered into the statement, producing SQL you can paste straight into a database console:

```yaml
logging:
  level:
    st.orm.sql: TRACE
```

```
SQL (SELECT City):
	SELECT c.id, c.name
	FROM city c
	WHERE c.id = 2
```

:::warning Parameter values are database values
They may include credentials, personal data, or anything else your entities carry. That is why they appear only at `TRACE`: the level nobody enables in production by accident. `DEBUG` is the level to leave available for on-demand diagnosis.
:::

### Scoping to One Type

Statements also log under `st.orm.sql.<Type>` for the entity or projection they target, so a single type can be turned up without the rest:

```yaml
logging:
  level:
    st.orm.sql.Owner: TRACE
```

Because these are child loggers, raising `st.orm.sql` raises every type at once. Statements that target no particular type, such as raw queries, log under `st.orm.sql` itself.

### Fetches

A statement that resolves a reference is labelled, so it is distinguishable from the primary key lookups it otherwise looks identical to:

```
SQL (SELECT Owner, fetch):
	SELECT o.id, o.first_name, ...
	FROM owner o
	WHERE o.id = ?
```

Naming the reference in the query's fetch plan brings the referenced record back in the same statement instead. See [Refs](refs.md) for the fetch plan, and [Spring Integration](spring-integration.md#observability) for the `storm.origin` metric tag that measures the same thing in production.

---
## Per-Call Summaries

Individual statements answer what ran. They do not answer what one unit of work cost, and that total is the part you act on: forty-four statements taking 678 ms tells you nothing about which of them to look at.

A scope records the statements a call executes and reports them as one summary:

```
SQL (GET /owners): 12 statements, 8 fetches, 214 ms in database over 61 ms elapsed (peak 4 concurrent), 678 ms total
	96 ms  6408 rows  7x  Visit         VisitService.kt:88  SELECT v.id, v.visit_date FROM visit v WHERE v.pet_id = ?
	28 ms     8 rows  8x  City   fetch  OwnerView.kt:31     SELECT c.id, c.name FROM city c WHERE c.id = ?
	18 ms   112 rows  4x  Pet           PetService.kt:52    SELECT p.id, p.name FROM pet p WHERE p.owner_id = ?
```

One row per distinct statement, heaviest first by **total** time, so a statement run many times cheaply ranks above one slow statement when it cost more overall. Repetition reads as the multiplier, each row carries the total rows its executions produced (or affected, for writes) followed by the execution count that accumulated them, and names the entity or projection it targets, and a statement that resolved a reference is marked `fetch`. Long statements elide from the middle, keeping the FROM and WHERE clauses that identify them, and runs of placeholders collapse to `?, …, ?` so the visible text is the part that says something. A row count a driver declined to report in full (a batch entry answered with `SUCCESS_NO_INFO`) or a stream closed before its end is a known lower bound, marked `500*`.

Two aids answer which query a row is:

- **Call sites** name the application frame that caused each execution (`VisitService.kt:88`), which is the identity a developer thinks in. An application with a database layer of its own declares it as plumbing, so rows name the caller beyond it: `storm.sql-log.call-site-skip` in Spring, `sqlLogCallSiteSkip` in Ktor, or the `storm.sql_log.call_site_skip` system property on a plain JVM. Entries ending in `.kt` or `.java` match by source file, which covers inline functions. Work resumed on another dispatcher has no caller on its stack at all; a context built with `sqlLogContext()` (and every context Storm builds, such as `transaction { }`) carries the launch site, captured while the caller is still on the stack, so such rows name the frame that launched the work. A stack that is plumbing end to end with no carried site reports its innermost plumbing frame rather than none. A stack walk per execution, so it is opt-in and suited to development: `storm.sql-log.call-sites: true` in Spring, `sqlLogCallSites = true` in Ktor, or the `callSites` parameter on a scope opened directly. A row seen from several frames shows the first plus `(+n sites)`.
- **Hydration shapes** append each read's declared shape, off by default: `storm.sql-log.hydration` in Spring, `sqlLogHydration` in Ktor, or the `storm.sql_log.hydration` system property on a plain JVM. `short` states the numbers on reads whose type hydrates beyond its own table (`j2 c12 d3`: joins, columns, and graph depth, with flat types showing none), and `full` names the joined-entity graph on every mapped read:

  ```
  12 ms  1 rows  1x  Pet  SELECT p.id, … WHERE p.id = ?  j2 c12 d3                                  # short
  12 ms  1 rows  1x  Pet  SELECT p.id, … WHERE p.id = ?  joins=2 columns=12 graph=Pet(Owner(City))  # full
  ```

  The shape is the type's declaration, derived at rendering and cached per type, so the setting costs nothing while calls run: an entity component is a join and recurses, an inline record is columns on the same table and no subgraph, and a `Ref` is its foreign key column and stops, which is exactly the width a `Ref` declaration saves. Many rows against a wide graph is the signal to consider `Ref` on the branches that read does not need, or a [projection](projections.md).

  Writes carry no shape. An insert, update or delete touches its own table, so the graph its type declares is not something the statement traverses, and its cost is the rows it affected. Shapes appear on `SELECT` rows only, which keeps the numbers on the rows where they mean something.
- **TRACE detail**: with `st.orm.sql.perf` at `TRACE`, the un-elided statement texts follow the summary, one per row in row order. `TRACE` rather than `DEBUG` because this logger sits under `st.orm.sql`, so raising that to `DEBUG` for per-statement logging would otherwise repeat every statement twice. Summaries carry no parameter values at any level, so this level is as safe to enable as the others.
- **Display width**: rows aim for 200 characters, the statement text eliding to what the other columns leave; `storm.sql-log.line-width` (Spring), `sqlLogLineWidth` (Ktor), or the `storm.sql_log.line_width` system property sets the target for narrow viewers (120) or wide ones (240).

Statements group by the template they were generated from, not by text. A collection parameter that expands to a different number of placeholders per execution (`IN (?)`, `IN (?, ?)`) therefore stays one row, marked `(n variants)`.

The headline separates three durations:

| Number | Meaning |
|--------|---------|
| `214 ms in database` | The summed duration of every statement. |
| `over 61 ms elapsed` | The time during which at least one statement was in flight. |
| `peak 4 concurrent` | The most statements in flight at once. |
| `678 ms total` | How long the call took. |

Summed database time exceeds elapsed time whenever statements run concurrently, which is why both appear. The concurrency clause is omitted when nothing overlapped. And `214 ms in database` against `678 ms total` says most of that call was spent somewhere other than the database.

The headline also counts what cost nothing: `3 from cache` reports the reads the transaction's entity cache served without a statement: a reference resolving to an entity the transaction had already read, or an identity lookup at `REPEATABLE_READ` and above. The fetch count is the cache misses; this is the other side, and it appears only when any read was served.

A scope covers whatever runs inside it, whichever repository, query builder or template issued the statement. Summaries log under `st.orm.sql.perf` at `INFO`, and statements are recorded only while that logger is enabled.

### Per Entry Point

Both integrations can wrap every way work enters the application, which needs no code change and no annotation.

<Tabs groupId="framework">
<TabItem value="spring" label="Spring Boot" default>

```yaml
storm:
  sql-log:
    enabled: true
    limit: 200      # statements recorded per unit of work; the count covers the rest
```

A servlet filter wraps each HTTP request. The same switch covers the entry points a filter cannot see: a method annotated `@Scheduled`, `@KafkaListener`, `@RabbitListener`, `@JmsListener`, or `@SqsListener` (including the `@KafkaHandler`/`@RabbitHandler` methods of a class-level listener) reports as its own summary, named after the method (`ReportJob.nightly`), so a worker without a web layer reports the same way a web application does. Matching is by annotation name, directly present on the bean method, so a listener library that is absent from the classpath costs nothing, and `storm.sql-log.entry-points` replaces the set for others, such as a Pulsar listener or an application's own dispatch annotation. A final class or method cannot be proxied; open a scope inside it instead.

For production, thresholds turn the scope into a guardrail: only units of work that exceed one are reported, at WARN.

```yaml
storm:
  sql-log:
    enabled: true
    threshold:
      statements: 50
      duration: 500ms
```

</TabItem>
<TabItem value="ktor" label="Ktor">

```kotlin
install(Storm) {
    sqlLog = true
    sqlLogLimit = 200
}
```

Every `sqlLog*` option can come from `application.conf` instead, under `storm.sqlLog` (or `storm.sql_log`), so the log can be switched on in production without a redeploy; a plugin setting overrides the configuration file per option.

For production, thresholds turn the scope into a guardrail: only calls that exceed one are reported, at WARN.

```hocon
storm.sqlLog {
    enabled = true
    threshold {
        statements = 50
        duration = 500ms
    }
}
```

The same thresholds in the plugin configuration are `sqlLogStatementThreshold = 50` and `sqlLogDurationThreshold = 500.milliseconds`.

</TabItem>
</Tabs>

The boundary names the summary: a request after its route (`GET /owners/42`), an entry-point method after itself (`ReportJob.nightly`). A unit of work that touches no database produces no line.

The invoking thread is what this covers, which is where a request handler, a scheduled task or a blocking listener does its work. An application whose work runs in coroutines opens the scope inside the coroutine instead, where every child inherits it; see below.

### A Narrower Boundary

To measure one service method rather than a whole request, open a scope directly. The summary reports the same way: under `st.orm.sql.perf`, only while that logger is enabled.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val owners = sqlLog("importOwners") {
    ownerService.importAll(batch)
}
```

The scope follows the coroutine, so it keeps recording across a suspension that resumes on another thread, and every coroutine launched inside it is covered. A scope opened by one coroutine is never observed by another.

Kotlin code that runs outside coroutines, such as a Spring MVC controller, opens the same scope with `sqlLogBlocking`, mirroring how transactions ship as the `transaction` and `transactionBlocking` pair:

```kotlin
fun loadOwners(): List<OwnerView> = sqlLogBlocking("loadOwners") {
    ownerService.loadAll()
}
```

Code that builds its own coroutine from blocking code passes the scope along explicitly:

```kotlin
fun loadOwners(ids: List<Int>): List<Owner> = runBlocking(sqlLogContext()) {
    ids.map { async { owners.getById(it) } }.awaitAll()
}
```

`transaction { }` and `withTransactionOptions { }` carry it already, so work below them needs nothing.

</TabItem>
<TabItem value="java" label="Java">

```java
try (var scope = SqlLog.open("importOwners")) {
    ownerService.importAll(batch);
}
```

The scope follows the thread that opened it. Work handed to another thread, including a subtask forked from a `StructuredTaskScope`, falls outside it.

</TabItem>
</Tabs>

### The Summary Is the Report

A scope hands nothing back: the summary reports through the `st.orm.sql.perf` logger, and the logger is the only switch. That is deliberate. Each number a summary shows already has a home for programmatic use: production metrics are the [Micrometer observations](spring-integration.md#observability), and test assertions are [`SqlCapture`](testing.md). So the summary stays a report to be read, not an API to be coupled to.

Parameter values are absent by design: they are database values, and a summary is meant to be safe to log in production. Raise `st.orm.sql` to `TRACE` to see values.

A fetch served by the transaction's entity cache issues no statement, so the fetch count counts distinct cache misses rather than `fetch()` call sites.

---

## Tips

1. **Reach for `st.orm.sql` to see statements, a scope to judge a call.** One answers what ran, the other what it cost.
2. **Keep `TRACE` out of production.** `DEBUG` gives you the statements on demand through log configuration alone; `TRACE` adds the parameter values, which are database values.
3. **Scope with the type logger** (`st.orm.sql.Owner`) rather than raising the root, so narrowing the focus needs a config change rather than a redeploy.
4. **Read the top row first.** It is the statement that cost the most in total, whether it was slow once or cheap many times. A `fetch` row is one you can often remove by naming the reference in the query's fetch plan.
5. **Compare `in database` against `total`.** A large gap says the call is slow for reasons the query layer cannot fix.
6. **Assert query counts in tests** with `SqlCapture` rather than reading logs. See [Testing](testing.md) for `count(Origin.FETCH)`.
