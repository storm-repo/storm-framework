# SQL Interceptors

Storm provides `SqlInterceptor` for observing and modifying SQL statements before they're sent to the database. This is useful for logging, debugging, and diagnostics.

> **Note:** SQL interceptors are intended for logging and debugging, not for implementing business logic.

## Observing SQL

Log SQL statements without modifying them:

```java
SqlInterceptor.observe(sql -> log.debug("SQL: {}", sql.statement()), () -> {
    var entities = repository.findAll();
});
```

## Modifying SQL

Add comments to identify query origins:

```java
SqlInterceptor.intercept(sql -> {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
    String modifiedSql = "/* " + caller.getClassName() + "." + caller.getMethodName() + " */ " + sql.statement();
    return sql.statement(modifiedSql);
}, () -> {
    // Database operations here
});
```

## Global Interceptors

Register interceptors that apply to all database operations:

### Global Observer

```java
SqlInterceptor.registerGlobalObserver(sql ->
    log.debug("SQL: {}", sql.statement())
);
```

### Global Interceptor

```java
SqlInterceptor.registerGlobalInterceptor(sql -> {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
    String modifiedSql = "/* " + caller.getClassName() + "." + caller.getMethodName() + " */ " + sql.statement();
    return sql.statement(modifiedSql);
});
```

### Unregistering

```java
SqlInterceptor.unregisterGlobalObserver(observer);
SqlInterceptor.unregisterGlobalInterceptor(interceptor);
```

## Use Cases

### Query Logging

```java
SqlInterceptor.registerGlobalObserver(sql -> {
    log.info("Executing SQL: {}", sql.statement());
    log.debug("Parameters: {}", sql.parameters());
});
```

### Performance Monitoring

```java
SqlInterceptor.observe(sql -> {
    long start = System.currentTimeMillis();
    // Actual execution happens after observe returns
}, () -> {
    repository.findAll();
});
```

### Query Tagging

Add metadata for database monitoring tools:

```java
SqlInterceptor.intercept(sql -> {
    String tagged = "/* app=myapp, request=" + requestId + " */ " + sql.statement();
    return sql.statement(tagged);
}, () -> {
    // Operations
});
```

## Module Dependency

`SqlInterceptor` is part of `storm-core`. Add it as a compile-time dependency if you need to use interceptors:

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-core</artifactId>
    <version>1.8.2</version>
    <scope>compile</scope>
</dependency>
```

## Tips

1. **Use for observability** — Logging, metrics, debugging
2. **Avoid business logic** — Interceptors should not implement application behavior
3. **Consider performance** — Global interceptors run for every query
4. **Use scoped interceptors** — Prefer scoped `observe()` over global for targeted debugging
