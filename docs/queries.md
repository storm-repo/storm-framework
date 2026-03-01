# Queries

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Storm provides a powerful and flexible query API. All queries are type-safe; the generated metamodel (`User_`, `City_`, etc.) catches errors at compile time rather than at runtime.

Key features:
- **Compile-time checked** -- field references are validated by the metamodel
- **No string-based queries** -- no risk of typos in column names
- **Single-query loading** -- related entities load in JOINs, not N+1 queries
- **Two styles** -- quick methods for simple cases, fluent builder for complex queries

---

## Choosing a Query Approach

Storm offers three ways to query data, each suited to different complexity levels:

| Approach | Best for | Type safety | Flexibility |
|----------|----------|-------------|-------------|
| **Repository `findBy`** | Simple key lookups by primary key or unique key | Full compile-time | Low (single-field equality only) |
| **Query DSL** | Filtering, ordering, pagination with type-safe conditions | Full compile-time | Medium (AND/OR predicates, joins, ordering) |
| **SQL Templates** | Complex joins, subqueries, CTEs, window functions, database-specific SQL | Column references checked at compile time, SQL structure at runtime | High (full SQL control) |

Start with the simplest approach that meets your needs. Use `findBy` or `findAll` for straightforward lookups. Move to the query builder when you need compound filters or pagination. Use SQL templates when you need SQL features the DSL does not cover.

---

## Quick Queries

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Storm for Kotlin offers two complementary query styles; use whichever fits best.

For simple queries, use methods directly on the ORM template:

```kotlin
// Find single entity with predicate
val user: User? = orm.find { User_.email eq email }

// Find all matching
val users: List<User> = orm.findAll { User_.city eq city }

// Find by field value
val user: User? = orm.findBy(User_.email, email)

// Check existence
val exists: Boolean = orm.existsBy(User_.email, email)
```

</TabItem>
<TabItem value="java" label="Java">

The Java DSL uses the same `EntityRepository` interface as Kotlin. Obtain a repository with `orm.entity(Class)` and use its fluent query builder. Return types use `Optional` for single results and `List` for collections.

```java
var users = orm.entity(User.class);

// Find by ID
Optional<User> user = users.findById(userId);

// Find all matching
List<User> usersInCity = users.select()
    .where(User_.city, EQUALS, city)
    .getResultList();

// Find first matching
Optional<User> user = users.select()
    .where(User_.email, EQUALS, email)
    .getOptionalResult();

// Count
long count = users.count();
```

</TabItem>
</Tabs>

---

## Repository Queries

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

For more complex operations, use the repository:

```kotlin
val users = orm.entity(User::class)

// Find by ID
val user: User? = users.findById(userId)

// Find with predicate
val user: User? = users.find { User_.email eq email }

// Find all matching
val usersInCity: List<User> = users.findAll { User_.city eq city }

// Count
val count: Long = users.count()

// Exists
val exists: Boolean = users.existsById(userId)
```

</TabItem>
<TabItem value="java" label="Java">

For more complex operations, use the repository:

```java
var users = orm.entity(User.class);

// Find by ID
Optional<User> user = users.findById(userId);

// Find with predicate
Optional<User> user = users.select()
    .where(User_.email, EQUALS, email)
    .getOptionalResult();

// Find all matching
List<User> usersInCity = users.select()
    .where(User_.city, EQUALS, city)
    .getResultList();

// Count
long count = users.count();

// Exists
boolean exists = users.existsById(userId);
```

</TabItem>
</Tabs>

---

## Filtering with Predicates

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Combine conditions with `and` and `or`:

```kotlin
// AND condition
val users = orm.findAll {
    (User_.city eq city) and (User_.birthDate less LocalDate.of(2000, 1, 1))
}

// OR condition
val users = orm.findAll {
    (User_.role eq adminRole) or (User_.role eq superUserRole)
}

// Complex conditions
val users = orm.entity(User::class)
    .select()
    .where(
        (User_.city eq city) and (
            (User_.role eq adminRole) or (User_.birthDate greaterOrEquals LocalDate.of(1990, 1, 1))
        )
    )
    .resultList
```

### Operators

| Operator | Description |
|----------|-------------|
| `eq` | Equals |
| `notEq` | Not equals |
| `less` | Less than |
| `lessOrEquals` | Less than or equals |
| `greater` | Greater than |
| `greaterOrEquals` | Greater than or equals |
| `like` | LIKE pattern match |
| `notLike` | NOT LIKE |
| `isNull` | IS NULL |
| `isNotNull` | IS NOT NULL |
| `inList` | IN (list) |
| `notInList` | NOT IN (list) |

```kotlin
val users = orm.findAll { User_.email like "%@example.com" }
val users = orm.findAll { User_.deletedAt.isNull() }
val users = orm.findAll { User_.role inList listOf(adminRole, userRole) }
```

</TabItem>
<TabItem value="java" label="Java">

Combine conditions using the lambda-based `where` builder. The `it` parameter provides access to the condition factory, which you chain with `.and()` or `.or()` calls to compose compound predicates.

```java
// AND condition
List<User> users = orm.entity(User.class)
    .select()
    .where(it -> it.where(User_.city, EQUALS, city)
            .and(it.where(User_.birthDate, LESS_THAN, LocalDate.of(2000, 1, 1))))
    .getResultList();

// OR condition
List<User> users = orm.entity(User.class)
    .select()
    .where(it -> it.where(User_.role, EQUALS, adminRole)
            .or(it.where(User_.role, EQUALS, superUserRole)))
    .getResultList();
```

### Filtering (SQL Templates)

SQL Templates let you write SQL directly while retaining type safety. Entity references and metamodel fields are interpolated into the template, and parameter values are bound safely. This approach is well suited for queries that use database-specific syntax, CTEs, or window functions that the DSL does not cover.

```java
List<User> users = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{city}
          AND \{User_.birthDate} < \{LocalDate.of(2000, 1, 1)}""")
    .getResultList(User.class);
```

### Operators

| Operator | Description |
|----------|-------------|
| `EQUALS` | Equals |
| `NOT_EQUALS` | Not equals |
| `LESS_THAN` | Less than |
| `LESS_THAN_OR_EQUAL` | Less than or equals |
| `GREATER_THAN` | Greater than |
| `GREATER_THAN_OR_EQUAL` | Greater than or equals |
| `LIKE` | LIKE pattern match |
| `NOT_LIKE` | NOT LIKE |
| `IS_NULL` | IS NULL |
| `IS_NOT_NULL` | IS NOT NULL |
| `IN` | IN (list) |
| `NOT_IN` | NOT IN (list) |

```java
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.email, LIKE, "%@example.com")
    .getResultList();
```

</TabItem>
</Tabs>

---

## Ordering

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Use `orderBy` to control result ordering. Pass multiple fields as arguments to sort by more than one column. Use `orderByDescending` for descending order on a single field.

```kotlin
val users = orm.entity(User::class)
    .select()
    .orderBy(User_.name)
    .resultList

// Descending
val users = orm.entity(User::class)
    .select()
    .orderByDescending(User_.createdAt)
    .resultList

// Multiple fields (all ascending)
val users = orm.entity(User::class)
    .select()
    .orderBy(User_.lastName, User_.firstName)
    .resultList
```

Multiple `orderBy` and `orderByDescending` calls can be chained to build multi-column sort clauses with mixed directions. Each call appends to the existing ORDER BY clause rather than replacing it, so you can mix ascending and descending columns freely.

```kotlin
// Mixed sort directions: last name ascending, first name descending
val users = orm.entity(User::class)
    .select()
    .orderBy(User_.lastName)
    .orderByDescending(User_.firstName)
    .resultList
```

When an inline record (embedded component) is passed to `orderBy` or `orderByDescending`, Storm automatically expands it into its individual leaf columns using `flatten()`. For example, if `User_.fullName` is an inline record with `lastName` and `firstName` fields, `orderBy(User_.fullName)` produces `ORDER BY last_name, first_name`. The same expansion applies to `groupBy`.

For full control over the ORDER BY clause (for example, to use SQL expressions or database-specific syntax), use the template overload. The `t()` function resolves a metamodel field to its column name.

```kotlin
// Mixed sort directions (template)
val users = orm.entity(User::class)
    .select()
    .orderBy { "${t(User_.lastName)}, ${t(User_.firstName)} DESC" }
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

Use `orderBy` to sort results by one or more columns. Pass multiple fields as arguments for multi-column sorting. Use `orderByDescending` for descending order on a single field.

```java
// Ascending (default)
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(User_.name)
    .getResultList();

// Descending
List<User> users = orm.entity(User.class)
    .select()
    .orderByDescending(User_.createdAt)
    .getResultList();

// Multiple fields (all ascending)
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(User_.lastName, User_.firstName)
    .getResultList();
```

Chain `orderBy` and `orderByDescending` calls to mix ascending and descending columns. Each call appends to the ORDER BY clause.

```java
// Mixed sort directions: last name ascending, first name descending
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(User_.lastName)
    .orderByDescending(User_.firstName)
    .getResultList();
```

When an inline record (embedded component) is passed to `orderBy` or `orderByDescending`, Storm automatically expands it into its individual leaf columns using `flatten()`. The same expansion applies to `groupBy`.

For full control over the ORDER BY clause, use the template overload:

```java
// Mixed sort directions (template)
List<User> users = orm.entity(User.class)
    .select()
    .orderBy(RAW."\{User_.lastName}, \{User_.firstName} DESC")
    .getResultList();
```

</TabItem>
</Tabs>

---

## Compound Fields in Queries

When an inline record (embedded component) is used in a query clause, Storm automatically expands it into its constituent columns. This applies to WHERE, ORDER BY, and GROUP BY clauses.

### WHERE Clauses

Inline records expand differently depending on the operator:

**EQUALS / NOT_EQUALS** generate per-column AND conditions:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val owner = orm.entity(Owner::class)
    .select()
    .where(Owner_.address, EQUALS, address)
    .singleResult
```

</TabItem>
<TabItem value="java" label="Java">

```java
Owner owner = orm.entity(Owner.class)
    .select()
    .where(Owner_.address, EQUALS, address)
    .getSingleResult();
```

</TabItem>
</Tabs>

```sql
WHERE o.address = ? AND o.city_id = ?
```

For NOT_EQUALS, the condition is wrapped in NOT:

```sql
WHERE NOT (o.address = ? AND o.city_id = ?)
```

**Comparison operators** (GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL) generate lexicographic comparisons using nested OR/AND. This preserves the natural multi-column ordering:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val owners = orm.entity(Owner::class)
    .select()
    .where(Owner_.address, GREATER_THAN, address)
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

```java
List<Owner> owners = orm.entity(Owner.class)
    .select()
    .where(Owner_.address, GREATER_THAN, address)
    .getResultList();
```

</TabItem>
</Tabs>

```sql
WHERE (o.address > ? OR (o.address = ? AND o.city_id > ?))
```

For GREATER_THAN_OR_EQUAL, only the last column uses the inclusive operator:

```sql
WHERE (o.address > ? OR (o.address = ? AND o.city_id >= ?))
```

Some databases (PostgreSQL, MySQL, MariaDB, Oracle) support native tuple comparison syntax, which Storm uses automatically when available:

```sql
WHERE (o.address, o.city_id) > (?, ?)
```

**Unsupported operators.** LIKE, NOT_LIKE, IN, and NOT_IN do not have a meaningful multi-column interpretation and throw a `PersistenceException` when used with inline records. To filter on a sub-field, reference it directly:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val owners = orm.entity(Owner::class)
    .select()
    .where(Owner_.address.address, LIKE, "%Main%")
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

```java
List<Owner> owners = orm.entity(Owner.class)
    .select()
    .where(Owner_.address.address, LIKE, "%Main%")
    .getResultList();
```

</TabItem>
</Tabs>

### ORDER BY

Passing an inline record to `orderBy` or `orderByDescending` expands it into its leaf columns. For example, if `Owner_.address` is an inline record with `address` and `city` fields:

```kotlin
val owners = orm.entity(Owner::class)
    .select()
    .orderBy(Owner_.address)
    .resultList
```

```sql
ORDER BY o.address, o.city_id
```

Using `orderByDescending` applies DESC to each expanded column:

```sql
ORDER BY o.address DESC, o.city_id DESC
```

### GROUP BY

Inline records expand in GROUP BY the same way. This is particularly useful in combination with keyset pagination, where grouping by a column makes it unique in the result set. Wrap the metamodel with `.key()` to indicate it can serve as a cursor:

```kotlin
data class CityOrderCount(val city: City, val count: Long)

val page = orm.query(Order::class)
    .select(Order_.city, "COUNT(*)")
    .groupBy(Order_.city)
    .slice(Order_.city.key(), 20)
```

See [Keyset Pagination with GROUP BY](#keyset-pagination-with-group-by) for details.

---

## Aggregation

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

To perform GROUP BY queries with aggregate functions like COUNT, SUM, or AVG, define a result data class with the desired columns and pass a custom SELECT expression. The `t()` function generates the column list for an entity or projection type, so you do not have to enumerate columns manually.

```kotlin
data class CityCount(val city: City, val count: Long)

val counts: List<CityCount> = orm.entity(User::class)
    .select(CityCount::class) { "${t(City::class)}, COUNT(*)" }
    .groupBy(User_.city)
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

Define a result record with the desired columns and pass a custom SELECT expression. The DSL approach uses `select(Class, template)` with `groupBy` to build the query.

```java
record CityCount(City city, long count) {}

List<CityCount> counts = orm.entity(User.class)
    .select(CityCount.class, RAW."\{City.class}, COUNT(*)")
    .groupBy(User_.city)
    .getResultList();
```

### Aggregation (SQL Templates)

For aggregation queries that involve multiple tables, CTEs, or HAVING clauses, SQL Templates give you full control over the query structure while still mapping results to typed records.

```java
List<CityCount> counts = orm.query(RAW."""
        SELECT \{City.class}, COUNT(*)
        FROM \{User.class}
        GROUP BY \{User_.city}""")
    .getResultList(CityCount.class);
```

</TabItem>
</Tabs>

---

## Joins

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Storm automatically joins entities referenced by `@FK` fields. When you need to join entities that are not directly referenced in the result type (for example, filtering through a many-to-many join table), use explicit `innerJoin` or `leftJoin` calls. The `on` clause specifies which existing entity in the query the joined table relates to.

```kotlin
val roles = orm.entity(Role::class)
    .select()
    .innerJoin(UserRole::class).on(Role::class)
    .whereAny(UserRole_.user eq user)
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

Storm automatically joins entities referenced by `@FK` fields. For entities not directly referenced in the result type, such as join tables in many-to-many relationships, use explicit `innerJoin` or `leftJoin` calls. The `on` clause specifies which existing entity in the query the joined table relates to.

```java
List<Role> roles = orm.entity(Role.class)
    .select()
    .innerJoin(UserRole.class).on(Role.class)
    .where(UserRole_.user, EQUALS, user)
    .getResultList();
```

### Joins (SQL Templates)

SQL Templates let you write JOIN clauses directly, which is useful when the join condition is not a simple foreign key match or when you need to join on computed expressions.

```java
List<Role> roles = orm.query(RAW."""
        SELECT \{Role.class}
        FROM \{Role.class}
        INNER JOIN \{UserRole.class} ON \{UserRole_.role} = \{Role_.id}
        WHERE \{UserRole_.user} = \{user.id()}""")
    .getResultList(Role.class);
```

</TabItem>
</Tabs>

---

## Pagination

Storm supports two pagination strategies: offset-based and keyset-based. Offset-based pagination is simple but degrades on large tables because the database must scan and discard all skipped rows. Keyset pagination uses a column value as a cursor, so every page is fetched with the same cost regardless of how deep into the result set you are.

### Offset-based Pagination

Use `offset` and `limit` on the query builder for traditional page-number style pagination. Always combine these with `orderBy` to ensure deterministic ordering across pages.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val page = orm.entity(User::class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

```java
List<User> page = orm.entity(User.class)
    .select()
    .orderBy(User_.createdAt)
    .offset(20)
    .limit(10)
    .getResultList();
```

</TabItem>
</Tabs>

Offset-based pagination works well for small to medium tables or when users need to jump to arbitrary page numbers. For large tables where users scroll through results sequentially, prefer keyset pagination.

### Keyset Pagination with Slice

Keyset pagination works by remembering the last value seen on the current page and asking the database for rows after (or before) that value. This avoids the performance cliff of `OFFSET` on large tables, because the database can seek directly to the cursor position using an index.

:::warning Sort Stability Required
Keyset pagination (`sliceAfter`/`sliceBefore`) requires a stable sort order. The final sort column must be unique (typically the primary key). Using a non-unique sort column like `createdAt` without a tiebreaker will produce duplicate or missing rows at page boundaries. Use the [two-column overloads](#keyset-pagination-with-sort) when sorting by a non-unique column.
:::

The `slice`, `sliceAfter`, and `sliceBefore` methods are available directly on repositories and on the query builder. Each returns a `Slice<R>`, which is a simple record containing:

| Field | Description |
|-------|-------------|
| `content` | The list of results for this page. |
| `hasNext` | `true` if more results exist beyond this slice. |

The four methods correspond to four paging operations:

| Method | Purpose | SQL effect |
|--------|---------|------------|
| `slice(key, size)` | Fetch the first page (ascending). | `ORDER BY key ASC LIMIT size+1` |
| `sliceAfter(key, cursor, size)` | Fetch the next page after a cursor value. | `WHERE key > cursor ORDER BY key ASC LIMIT size+1` |
| `sliceBefore(key, size)` | Fetch the first page (descending). | `ORDER BY key DESC LIMIT size+1` |
| `sliceBefore(key, cursor, size)` | Fetch the previous page before a cursor value. | `WHERE key < cursor ORDER BY key DESC LIMIT size+1` |

The extra row (`size+1`) is used internally to determine the value of `hasNext`, then discarded from the returned content.

**Result ordering.** `slice` and `sliceAfter` return results in ascending key order. `sliceBefore` returns results in **descending** key order, both when used with a cursor (to find the nearest rows before it) and without a cursor (to start from the most recent entries). If you need ascending order for display after navigating backward, reverse the list.

**No total count.** Unlike offset-based pagination, keyset pagination does not include a total element count. A separate `COUNT(*)` query must execute the same joins, filters, and conditions as the main query, which can be expensive on large or complex result sets. Total counts are also inherently unstable: rows may be inserted or deleted while a user navigates through pages, so the count can become stale between requests. Keyset pagination is designed for sequential "load more" or infinite-scroll patterns where a total is rarely needed. If you do need a total count (for example, for a UI label like "showing 10 of 4,827 results"), call the `count` (Kotlin) or `getCount()` (Java) method on the query builder separately, keeping in mind that the value is a snapshot that may drift as the underlying data changes.

**Basic usage.** Pass a `Metamodel.Key` that identifies a unique, indexed column (typically the primary key) and the desired page size. The key determines both ordering and the cursor column. Fields annotated with `@UK` or `@PK` automatically generate `Metamodel.Key` instances in the metamodel. See [Metamodel](metamodel.md#unique-keys-uk-and-metamodelkey) for details.

> **Nullable keys.** If a `@UK` field is nullable and the default `nullsDistinct = true` applies, `slice` methods throw a `PersistenceException` at runtime. Either use a non-nullable type, or set `@UK(nullsDistinct = false)` if the database constraint prevents duplicate NULLs. See [Nullable Unique Keys](metamodel.md#nullable-unique-keys) for details.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page of 10 users ordered by ID ascending
val firstPage: Slice<User> = userRepository.slice(User_.id, 10)

// Next page, using the last seen ID as cursor
val nextPage: Slice<User> = userRepository.sliceAfter(User_.id, firstPage.content.last().id, 10)

// First page of 10 users ordered by ID descending (most recent first)
val latestPage: Slice<User> = userRepository.sliceBefore(User_.id, 10)

// Previous page before a known ID
val prevPage: Slice<User> = userRepository.sliceBefore(User_.id, someId, 10)
```

**Filtered slices.** The repository convenience methods accept a trailing-lambda predicate, following the same pattern as `findAll`. The filter is combined with the keyset condition using AND.

```kotlin
val activePage = userRepository.slice(User_.id, 10) { User_.active eq true }
val nextActive = userRepository.sliceAfter(User_.id, lastId, 10) { User_.active eq true }
```

**Query builder.** For more complex filtering, joins, or projections, build the query explicitly and call `slice` as a terminal operation. This gives you full control over the query while still getting keyset pagination.

```kotlin
val page = userRepository.select()
    .where(User_.active, EQUALS, true)
    .slice(User_.id, 10)
```

**Ordering is built in.** A common mistake is to add an `orderBy()` call before `slice(key, size)`, `sliceAfter`, or `sliceBefore`. These methods already generate the correct ORDER BY clause from the key you provide: ascending for `slice` and `sliceAfter`, descending for `sliceBefore`. Adding your own `orderBy()` would conflict with the ordering that keyset pagination depends on, so Storm rejects the combination at runtime with a `PersistenceException`.

```kotlin
// Wrong: orderBy conflicts with slice(key, size)
userRepository.select()
    .orderBy(User_.name)          // PersistenceException at runtime
    .slice(User_.id, 10)

// Correct: slice handles ordering via the key
userRepository.select()
    .slice(User_.id, 10)
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page of 10 users ordered by ID
Slice<User> firstPage = userRepository.slice(User_.id, 10);

// Next page, using the last seen ID as cursor
var last = firstPage.content().getLast();
Slice<User> nextPage = userRepository.sliceAfter(User_.id, last.id(), 10);

// Previous page before a known ID
Slice<User> prevPage = userRepository.sliceBefore(User_.id, someId, 10);
```

**Filtered slices.** In Java, filtered pagination uses the query builder. Add `where` clauses before calling the `slice` terminal operation. The filter and keyset conditions are combined with AND.

```java
Slice<User> activePage = userRepository.select()
    .where(User_.active, EQUALS, true)
    .slice(User_.id, 10);
```

**Ordering is built in.** The `slice(key, size)`, `sliceAfter`, and `sliceBefore` methods generate the ORDER BY clause from the key you provide, so you must not add your own `orderBy()` call. Doing so throws a `PersistenceException` at runtime.

</TabItem>
</Tabs>

### Keyset Pagination with Sort

The single-key `slice` methods require the cursor column to also be the sort column, which means the column must contain unique values. When you want to sort by a non-unique column (for example, a timestamp or status), use the overloads that accept a separate sort column. These accept two metamodel fields: a unique `key` column (typically the primary key) as a tiebreaker for deterministic paging, and a `sort` column for the primary sort order.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page sorted by creation date ascending, with ID as tiebreaker
val page1: Slice<Post> = postRepository.select()
    .slice(Post_.id, Post_.createdAt, 20)

// Next page: pass both cursor values from the last item
val last = page1.content.last()
val page2: Slice<Post> = postRepository.select()
    .sliceAfter(Post_.id, last.id, Post_.createdAt, last.createdAt, 20)

// First page sorted by creation date descending (most recent first)
val latest: Slice<Post> = postRepository.select()
    .sliceBefore(Post_.id, Post_.createdAt, 20)

// Previous page
val prev: Slice<Post> = postRepository.select()
    .sliceBefore(Post_.id, last.id, Post_.createdAt, last.createdAt, 20)
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page sorted by creation date, with ID as tiebreaker
Slice<Post> page1 = postRepository.select()
    .slice(Post_.id, Post_.createdAt, 20);

// Next page: pass both cursor values from the last item
Post last = page1.content().getLast();
Slice<Post> page2 = postRepository.select()
    .sliceAfter(Post_.id, last.id(), Post_.createdAt, last.createdAt(), 20);

// Previous page
Slice<Post> prev = postRepository.select()
    .sliceBefore(Post_.id, last.id(), Post_.createdAt, last.createdAt(), 20);
```

</TabItem>
</Tabs>

The generated SQL uses a composite WHERE condition that maintains correct ordering even when `sort` values repeat:

```sql
WHERE (created_at > ? OR (created_at = ? AND id > ?))
ORDER BY created_at ASC, id ASC
LIMIT 21
```

As with the single-key variants, these methods manage ORDER BY internally and reject any explicit `orderBy()` call. The client is responsible for extracting both cursor values from the last (or first) item of the current page and passing them to the next request.

**Indexing.** For keyset pagination with sort to perform well, create a composite index that covers both columns in the correct order:

```sql
CREATE INDEX idx_post_created_id ON post (created_at, id);
```

This allows the database to seek directly to the cursor position and scan forward, giving consistent performance regardless of page depth.

### Keyset Pagination with GROUP BY

When a query uses GROUP BY, the grouped column produces unique values in the result set even if the column itself is not annotated with `@UK`. In this case, wrap the metamodel with `.key()` (Kotlin) or `Metamodel.key()` (Java) to indicate it can serve as a keyset pagination cursor:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val page = orm.query(Order::class)
    .select(Order_.city, "COUNT(*)")
    .groupBy(Order_.city)
    .slice(Order_.city.key(), 20)
```

</TabItem>
<TabItem value="java" label="Java">

```java
var page = orm.query(Order.class)
    .select(Order_.city, "COUNT(*)")
    .groupBy(Order_.city)
    .slice(Metamodel.key(Order_.city), 20);
```

</TabItem>
</Tabs>

See [Manual Key Wrapping](metamodel.md#manual-key-wrapping) for more details.

### Composable WHERE and ORDER BY

Multiple `where()` calls are combined with AND, and multiple `orderBy()` calls append columns to a single ORDER BY clause:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val results = orm.entity(User::class)
    .select()
    .where(User_.active, EQUALS, true)
    .where(User_.city eq city)           // AND-combined with previous where
    .orderBy(User_.lastName)
    .orderBy(User_.firstName)            // appended: ORDER BY last_name, first_name
    .resultList
```

Builder-style `where()` calls (with `and`/`or` predicates) compose with other `where()` calls in the same way:

```kotlin
val results = orm.entity(User::class)
    .select()
    .where(User_.active, EQUALS, true)
    .where(                              // AND-combined with the active filter above
        (User_.role eq adminRole) or (User_.role eq superUserRole)
    )
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

```java
List<User> results = orm.entity(User.class)
    .select()
    .where(User_.active, EQUALS, true)
    .where(User_.city, EQUALS, city)     // AND-combined with previous where
    .orderBy(User_.lastName)
    .orderBy(User_.firstName)            // appended: ORDER BY last_name, first_name
    .getResultList();
```

Builder-style `where()` calls (with `and`/`or` predicates) compose with other `where()` calls in the same way:

```java
List<User> results = orm.entity(User.class)
    .select()
    .where(User_.active, EQUALS, true)
    .where(it -> it.where(User_.role, EQUALS, adminRole)  // AND-combined with active filter
            .or(it.where(User_.role, EQUALS, superUserRole)))
    .getResultList();
```

</TabItem>
</Tabs>

---

## Distinct Results

Add `.distinct()` to eliminate duplicate rows from the result set. This is useful when selecting a related entity type from a query that could produce duplicates due to one-to-many relationships.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val cities = orm.entity(User::class)
    .select(City::class)
    .distinct()
    .resultList
```

</TabItem>
<TabItem value="java" label="Java">

```java
List<City> cities = orm.entity(User.class)
    .select(City.class)
    .distinct()
    .getResultList();
```

</TabItem>
</Tabs>

---

## Streaming

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

For large result sets, use `selectAll()` or `select()` which return a Kotlin `Flow<T>`. Rows are fetched lazily from the database as you collect, so memory usage stays constant regardless of result set size. Flow also handles resource cleanup automatically when collection completes or is cancelled.

```kotlin
val users: Flow<User> = orm.entity(User::class).selectAll()

// Process each
users.collect { user -> process(user) }

// Transform and collect
val emails: List<String> = users.map { it.email }.toList()

// Count
val count: Int = users.count()
```

</TabItem>
<TabItem value="java" label="Java">

Java streams hold an open database cursor and JDBC resources. Unlike Kotlin's `Flow` (which handles cleanup automatically), Java `Stream` results must be explicitly closed. Always wrap them in a try-with-resources block to prevent connection leaks.

```java
try (Stream<User> users = orm.entity(User.class).selectAll()) {
    List<String> emails = users.map(User::email).toList();
}
```

</TabItem>
</Tabs>

---

## Result Classes

Query result classes can be:
- **Plain records** -- Storm maps columns to fields (you write all SQL)
- **`Data` implementations** -- enable SQL template helpers like `${t(Class::class)}`
- **`Entity`/`Projection`** -- full repository support with CRUD operations

Choose the simplest option that meets your needs. See [SQL Templates](sql-templates.md) for details.

---

## Common Patterns

### Checking Existence

Use `existsBy` (Kotlin) or `.exists()` on the query builder (Java) to check whether a matching row exists without loading the full entity.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val exists: Boolean = orm.existsBy(User_.email, email)
```

</TabItem>
<TabItem value="java" label="Java">

```java
boolean exists = orm.entity(User.class)
    .select()
    .where(User_.email, EQUALS, email)
    .exists();
```

</TabItem>
</Tabs>

### Count with Filter

Combine `where` with `count` to count rows matching a condition without loading the entities themselves. Storm translates this to a `SELECT COUNT(*)` query.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val count: Long = orm.entity(User::class)
    .select()
    .where(User_.city eq city)
    .count
```

</TabItem>
<TabItem value="java" label="Java">

```java
long count = orm.entity(User.class)
    .select()
    .where(User_.city, EQUALS, city)
    .getCount();
```

</TabItem>
</Tabs>

### Finding a Single Result

When you expect at most one matching row, use `find` (Kotlin, returns `null` if not found) or `getOptionalResult` (Java, returns `Optional`). These methods throw if more than one row matches.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val user: User? = orm.find { User_.email eq email }
```

</TabItem>
<TabItem value="java" label="Java">

```java
Optional<User> user = orm.entity(User.class)
    .select()
    .where(User_.email, EQUALS, email)
    .getOptionalResult();
```

</TabItem>
</Tabs>

---

## Tips

1. **Use the metamodel** -- `User_.email` catches typos at compile time; see [Metamodel](metamodel.md)
2. **Kotlin: choose your style** -- quick queries (`orm.find`, `orm.findAll`) for simple cases, repository builder for complex operations
3. **Java: DSL or Templates** -- DSL for type-safe conditions, SQL Templates for complex SQL like CTEs, window functions, or database-specific features
4. **Entity graphs load in one query** -- related entities marked with `@FK` are JOINed automatically, no N+1 problems
5. **Close Java streams** -- always use try-with-resources with `Stream` results
6. **Combine conditions freely** -- use `and` / `or` in Kotlin, `it.where().and()` / `.or()` in Java to build complex predicates
7. **Always use the returned builder** -- `QueryBuilder` is immutable; methods like `where()`, `orderBy()`, and `limit()` return a new instance. Ignoring the return value silently loses the change. Chain calls or reassign the variable.
