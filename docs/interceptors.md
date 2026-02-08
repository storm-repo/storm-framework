# SQL Interceptors

When debugging performance issues or tracing application behavior, you often need visibility into the SQL statements your ORM generates. Standard JDBC logging shows raw statements but lacks application context: which code path triggered a query, how many queries an operation generated, or which request a slow query belongs to.

Storm provides `SqlInterceptor` for observing and modifying SQL statements before they are sent to the database. Interceptors sit between Storm's query generator and the JDBC driver, giving you a hook to log, measure, tag, or transform every SQL statement.

> **Note:** SQL interceptors are intended for observability and diagnostics, not for implementing business logic.

There are two types of interceptors:
- **Observers** -- read-only access to SQL statements (for logging, metrics). Observers receive the SQL but cannot change it.
- **Interceptors** -- can modify SQL statements before execution (for tagging, commenting). Interceptors receive the SQL, transform it, and return the modified version.

Both types can be **scoped** (active only within a specific block) or **global** (active for all operations). Scoped interceptors are preferred for targeted debugging because they limit overhead to the code path you are investigating. Global interceptors are appropriate for application-wide concerns like query logging or APM integration.

---

## Kotlin

### Observing SQL

The simplest use of interceptors is observing SQL without modifying it. The `observe` function takes two arguments: a lambda that receives each SQL statement, and a block of code to observe. Every SQL statement generated within the block is passed to the observer lambda.

```kotlin
SqlInterceptor.observe({ sql -> log.debug("SQL: {}", sql.statement()) }) {
    val users = orm.findAll<User>()
}
```

The observer receives each SQL statement generated within the block. The return value of the block is passed through:

```kotlin
val users = SqlInterceptor.observe({ sql -> log.debug("SQL: {}", sql.statement()) }) {
    orm.findAll<User>()
}
```

### Modifying SQL

When you need to transform SQL before it reaches the database, use `intercept` instead of `observe`. A common use case is prepending SQL comments that identify the code path that generated a query. Database monitoring tools (such as `pg_stat_statements` or MySQL's Performance Schema) can then group and attribute queries to specific application operations.

```kotlin
SqlInterceptor.intercept({ sql ->
    val caller = Thread.currentThread().stackTrace[3]
    sql.statement("/* ${caller.className}.${caller.methodName} */ ${sql.statement()}")
}) {
    // Database operations here
}
```

### Global Interceptors

Scoped interceptors are active only within their block. When you need an interceptor that applies to every database operation for the lifetime of the application (or a portion of it), register a global interceptor. Global observers and interceptors are useful for application-wide logging, APM integration, or audit trails.

```kotlin
// Global observer
val observer = SqlInterceptor.Observer { sql ->
    log.debug("SQL: {}", sql.statement())
}
SqlInterceptor.registerGlobalObserver(observer)

// Unregister when done
SqlInterceptor.unregisterGlobalObserver(observer)
```

---

## Java

### Observing SQL

The Java API provides the same observer and interceptor capabilities using functional interfaces. The `observe` method takes a consumer for the SQL statement and a `Supplier` or `Runnable` for the code block to observe.

```java
SqlInterceptor.observe(sql -> log.debug("SQL: {}", sql.statement()), () -> {
    var entities = repository.findAll();
});
```

The observer receives each SQL statement generated within the block. The return value of the block is passed through:

```java
var users = SqlInterceptor.observe(sql -> log.debug("SQL: {}", sql.statement()), () -> {
    return orm.entity(User.class).select().getResultList();
});
```

### Modifying SQL

The `intercept` method works the same way as in Kotlin. The interceptor function receives the SQL object, modifies it, and returns the updated version. This example prepends a comment with the caller's class and method name.

```java
SqlInterceptor.intercept(sql -> {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
    String modifiedSql = "/* " + caller.getClassName() + "." + caller.getMethodName() + " */ " + sql.statement();
    return sql.statement(modifiedSql);
}, () -> {
    // Database operations here
});
```

### Global Interceptors

Global interceptors in Java work the same as in Kotlin. Register them once, and they apply to every SQL statement until unregistered.

```java
// Global observer -- logs every SQL statement
SqlInterceptor.registerGlobalObserver(sql ->
    log.debug("SQL: {}", sql.statement())
);

// Global interceptor -- modifies every SQL statement
SqlInterceptor.registerGlobalInterceptor(sql -> {
    String tagged = "/* app=myapp */ " + sql.statement();
    return sql.statement(tagged);
});
```

### Unregistering Global Interceptors

Global interceptors persist until explicitly removed. Always store the reference returned by the registration method so you can unregister the interceptor when it is no longer needed. Failing to unregister creates a memory leak and continues processing overhead on every query.

```java
var observer = SqlInterceptor.registerGlobalObserver(sql ->
    log.debug("SQL: {}", sql.statement())
);

// Later, when no longer needed
SqlInterceptor.unregisterGlobalObserver(observer);

// Similarly for interceptors
SqlInterceptor.unregisterGlobalInterceptor(interceptor);
```

---

## Use Cases

The following examples illustrate practical scenarios where interceptors provide value beyond basic logging.

### Query Logging

A global observer can log every SQL statement along with its bound parameters. This is useful during development to understand exactly what SQL Storm generates, and in production for audit or debugging purposes. Keep the logging level at DEBUG or TRACE to avoid performance overhead in normal operation.

```java
SqlInterceptor.registerGlobalObserver(sql -> {
    log.info("Executing SQL: {}", sql.statement());
    log.debug("Parameters: {}", sql.parameters());
});
```

### Query Tagging

Database monitoring tools aggregate queries by their SQL text. When all queries from different operations share the same SQL shape, it becomes difficult to attribute slow queries to specific application features. Query tagging solves this by prepending a SQL comment with application metadata (service name, request ID, feature flag). The comment does not affect query execution but appears in monitoring dashboards and slow query logs.

```java
SqlInterceptor.intercept(sql -> {
    String tagged = "/* app=myapp, request=" + requestId + " */ " + sql.statement();
    return sql.statement(tagged);
}, () -> {
    // All queries in this block will have the tag prepended
    orderService.processOrder(orderId);
});
```

### Debugging a Specific Operation

When you need to investigate the SQL generated by a specific code path without enabling global logging, use a scoped observer. This captures only the SQL statements produced within the block, leaving the rest of the application unaffected. This is particularly useful in integration tests where you want to verify that a specific operation generates the expected queries.

```kotlin
val captured = mutableListOf<String>()

SqlInterceptor.observe({ sql -> captured.add(sql.statement()) }) {
    // Only SQL from this block is captured
    orm.findAll { User_.city eq city }
}

captured.forEach { println(it) }
```

### Counting Queries

One of the most common ORM pitfalls is the N+1 query problem, where loading a list of entities triggers an additional query per entity for related data. Interceptors make it straightforward to write tests that assert the exact number of SQL statements an operation produces. If an operation that should require one query suddenly produces 101, the test fails immediately.

```kotlin
var queryCount = 0

SqlInterceptor.observe({ queryCount++ }) {
    val user = orm.find { User_.id eq userId }
}

assertEquals(1, queryCount)  // Verify single query, no N+1
```

## Tips

1. **Use for observability** -- logging, metrics, debugging, query counting
2. **Avoid business logic** -- interceptors should not implement application behavior
3. **Consider performance** -- global interceptors run for every query; keep them lightweight
4. **Prefer scoped interceptors** -- use scoped `observe()` / `intercept()` for targeted debugging over global registration
5. **Unregister global interceptors** -- always store a reference and unregister when no longer needed to avoid memory leaks
