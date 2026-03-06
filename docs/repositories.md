# Repositories

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Entity repositories provide a high-level abstraction for managing entities in the database. They offer methods for creating, reading, updating, and deleting entities, as well as querying and filtering based on specific criteria.

---

## Getting a Repository

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Storm provides two ways to obtain a repository. The generic `entity()` method returns a built-in repository with standard CRUD operations. For custom query methods, define your own interface extending `EntityRepository` and retrieve it with `repository()` (covered below in Custom Repositories).

```kotlin
val orm = ORMTemplate.of(dataSource)

// Generic entity repository
val userRepository = orm.entity(User::class)

// Or using extension function
val userRepository = orm.entity<User>()
```

</TabItem>
<TabItem value="java" label="Java">

The Java API follows the same pattern as Kotlin. The generic `entity()` method provides standard CRUD operations; custom interfaces use `repository()`.

```java
var orm = ORMTemplate.of(dataSource);

// Generic entity repository
EntityRepository<User, Integer> userRepository = orm.entity(User.class);
```

</TabItem>
</Tabs>

---

## Basic CRUD Operations

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

All CRUD operations use the entity's primary key (marked with `@PK`) for identity. Insert returns the entity with any database-generated fields populated (such as auto-increment IDs). Update and delete match by primary key. Query methods accept metamodel-based filter expressions that compile to parameterized WHERE clauses.

```kotlin
// Create
val user = orm insert User(
    email = "alice@example.com",
    name = "Alice",
    birthDate = LocalDate.of(1990, 5, 15)
)

// Read
val found: User? = orm.find { User_.id eq user.id }
val all: List<User> = orm.findAll { User_.city eq city }

// Update
orm update user.copy(name = "Alice Johnson")

// Delete
orm delete user

// Delete by condition
orm.delete<User> { User_.city eq city }

// Delete all
orm.deleteAll<User>()

// Delete all (builder approach, requires unsafe() to confirm intent)
orm.entity(User::class).delete().unsafe().executeUpdate()
```

</TabItem>
<TabItem value="java" label="Java">

Java CRUD operations use the fluent builder pattern. Since Java records are immutable, updates require constructing a new record instance with the changed field values.

```java
// Insert
User user = userRepository.insertAndFetch(new User(
    null, "alice@example.com", "Alice", LocalDate.of(1990, 5, 15), city
));

// Read
Optional<User> found = userRepository.select()
    .where(User_.id, EQUALS, user.id())
    .getOptionalResult();

List<User> all = userRepository.select()
    .where(User_.city, EQUALS, city)
    .getResultList();

// Update
userRepository.update(new User(
    user.id(), "alice@example.com", "Alice Johnson", user.birthDate(), user.city()
));

// Delete
userRepository.delete(user);

// Delete all
userRepository.deleteAll();

// Delete all (builder approach, requires unsafe() to confirm intent)
userRepository.delete().unsafe().executeUpdate();
```

</TabItem>
</Tabs>

:::warning Safety Check
Storm rejects DELETE and UPDATE queries that have no WHERE clause, throwing a `PersistenceException`. This prevents accidental bulk deletions, which is especially important because `QueryBuilder` is immutable and a lost `where()` return value would silently drop the filter. Call `unsafe()` to opt out of this check when you intentionally want to affect all rows. The `deleteAll()` convenience method calls `unsafe()` internally.
:::

Storm uses dirty checking to determine which columns to include in the UPDATE statement. See [Dirty Checking](dirty-checking.md) for configuration details.

---

## Streaming

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

For result sets that may be large, streaming avoids loading all rows into memory at once. Kotlin's `Flow` provides automatic resource management through structured concurrency: the underlying database cursor and connection are released when the flow completes or is cancelled, without requiring explicit cleanup.

```kotlin
val users: Flow<User> = userRepository.selectAll()
val count = users.count()

// Collect to list
val userList: List<User> = users.toList()
```

</TabItem>
<TabItem value="java" label="Java">

Java streams over database results hold open a database cursor and connection. You must close the stream explicitly, either with try-with-resources or by calling `close()`. Failing to close the stream leaks database connections.

```java
try (Stream<User> users = userRepository.selectAll()) {
    List<Integer> userIds = users.map(User::id).toList();
}
```

</TabItem>
</Tabs>

---

## Unique Key Lookups

When a field is annotated with `@UK`, the metamodel generates a `Metamodel.Key` instance that enables type-safe single-result lookups:

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val user: User? = userRepository.findBy(User_.email, "alice@example.com")
val user: User = userRepository.getBy(User_.email, "alice@example.com")  // throws if not found
```

</TabItem>
<TabItem value="java" label="Java">

```java
Optional<User> user = userRepository.findBy(User_.email, "alice@example.com");
User user = userRepository.getBy(User_.email, "alice@example.com");  // throws if not found
```

</TabItem>
</Tabs>

Since `@PK` implies `@UK`, primary key fields also work with `findBy` and `getBy`.

Entities loaded within a transaction are cached. See [Entity Cache](entity-cache.md) for details.

---

## Offset-Based Pagination

Storm provides built-in `Page` and `Pageable` types for offset-based pagination. These eliminate the need to write manual `LIMIT`/`OFFSET` queries or define your own page wrapper. The repository handles the count query and result slicing automatically.

### Page and Pageable

A `Pageable` describes a pagination request: which page to fetch, how many results per page, and an optional sort order. A `Page` holds the results along with metadata such as the total number of matching results, the total number of pages, and navigation helpers.

| `Page` field / method | Description |
|---|---|
| `content` | The list of results for this page |
| `totalCount` | Total number of matching rows across all pages |
| `pageNumber()` | Zero-based index of the current page |
| `pageSize()` | Maximum number of elements per page |
| `totalPages()` | Total number of pages |
| `hasNext()` | Whether a next page exists |
| `hasPrevious()` | Whether a previous page exists |
| `nextPageable()` | Returns a `Pageable` for the next page (preserves sort orders) |
| `previousPageable()` | Returns a `Pageable` for the previous page (preserves sort orders) |

Create a `Pageable` using one of the factory methods:

- `Pageable.ofSize(pageSize)` creates a request for the first page (page 0) with the given size.
- `Pageable.of(pageNumber, pageSize)` creates a request for a specific page.
- Chain `.sortBy(field)` or `.sortByDescending(field)` to add sort orders.

### Basic Usage

The simplest way to paginate is to call `page(pageNumber, pageSize)` on a repository. For more control over sorting, construct a `Pageable` and pass it to `page(pageable)`.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page of 20 users
val page1: Page<User> = userRepository.page(0, 20)

// Using Pageable with sort order
val pageable = Pageable.ofSize(20).sortBy(User_.name)
val page: Page<User> = userRepository.page(pageable)

// Navigate to next page
if (page.hasNext()) {
    val nextPage = userRepository.page(page.nextPageable())
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page of 20 users
Page<User> page1 = userRepository.page(0, 20);

// Using Pageable with sort order
Pageable pageable = Pageable.ofSize(20).sortBy(User_.name);
Page<User> page = userRepository.page(pageable);

// Navigate to next page
if (page.hasNext()) {
    Page<User> nextPage = userRepository.page(page.nextPageable());
}
```

</TabItem>
</Tabs>

### Ref Variants

Use `pageRef` to load only primary keys instead of full entities, returning a `Page<Ref<E>>`. This is useful when you need identifiers for a subsequent batch operation without the overhead of fetching full entity data.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
val refPage: Page<Ref<User>> = userRepository.pageRef(0, 20)
```

</TabItem>
<TabItem value="java" label="Java">

```java
Page<Ref<User>> refPage = userRepository.pageRef(0, 20);
```

</TabItem>
</Tabs>

---

## Keyset Pagination

Repositories provide convenience methods for keyset-based pagination, where a unique column value (typically the primary key) acts as a cursor. This approach avoids the performance issues of `OFFSET` on large tables, because the database can seek directly to the cursor position using an index rather than scanning and discarding skipped rows.

The key parameter must be a `Metamodel.Key`, which is generated for fields annotated with `@UK` or `@PK`. See [Metamodel](metamodel.md#unique-keys-uk-and-metamodelkey) for details.

The three methods map to the three paging operations you need:

- `slice(key, size)` fetches the **first page**, ordered by the key in ascending order.
- `sliceAfter(key, cursor, size)` fetches the **next page** after a cursor value.
- `sliceBefore(key, cursor, size)` fetches the **previous page** before a cursor value.

Each returns a `Slice<E>` containing the page content and a `hasNext` flag that tells you whether more results exist beyond the current page.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page of 20 users ordered by ID
val page1: Slice<User> = userRepository.slice(User_.id, 20)

// Next page, using the last ID as cursor
val page2: Slice<User> = userRepository.sliceAfter(User_.id, page1.content.last().id, 20)

// Previous page before a known cursor
val prev: Slice<User> = userRepository.sliceBefore(User_.id, someId, 20)
```

All three methods accept an optional trailing-lambda predicate for filtering, following the same pattern as `findAll`. The filter is combined with the keyset condition using AND, so you can paginate over a subset of rows without dropping down to the query builder.

```kotlin
val activePage = userRepository.slice(User_.id, 20) { User_.active eq true }
val nextActive = userRepository.sliceAfter(User_.id, lastId, 20) { User_.active eq true }
```

Ref variants (`sliceRef`, `sliceAfterRef`, `sliceBeforeRef`) load only primary keys, returning a `Slice<Ref<E>>`. This is useful when you need identifiers for a subsequent batch operation without the overhead of fetching full entities.

```kotlin
val refPage: Slice<Ref<User>> = userRepository.sliceRef(User_.id, 20)
```

Note that the slice methods handle ordering internally based on the key you provide, so you should not combine them with an explicit `orderBy()` call on the query builder. Also note that `sliceBefore` returns results in descending key order; reverse the list if you need ascending order for display.

</TabItem>
<TabItem value="java" label="Java">

The same keyset pagination methods described in the Kotlin section are available on Java repositories. The `slice`, `sliceAfter`, and `sliceBefore` default methods each return a `Slice<E>` containing the page `content()` and a `hasNext()` flag.

```java
// First page of 20 users ordered by ID
Slice<User> page1 = userRepository.slice(User_.id, 20);

// Next page, using the last ID as cursor
User last = page1.content().getLast();
Slice<User> page2 = userRepository.sliceAfter(User_.id, last.id(), 20);

// Previous page before a known cursor
Slice<User> prev = userRepository.sliceBefore(User_.id, someId, 20);
```

For filtered results, use the query builder and call `slice` as a terminal operation. The filter and keyset conditions are combined with AND.

```java
Slice<User> activePage = userRepository.select()
    .where(User_.active, EQUALS, true)
    .slice(User_.id, 20);
```

As with Kotlin, do not add an explicit `orderBy()` call when using the slice methods; they handle ordering internally via the key. `sliceBefore` returns results in descending key order; reverse the list if you need ascending order for display.

</TabItem>
</Tabs>

### Keyset Pagination with Sort

When you need to sort by a non-unique column (for example, a date or status), use the overloads that accept a separate sort column. These accept a `sort` column for the primary sort order and a `key` column (typically the primary key) as a unique tiebreaker to guarantee deterministic paging even when `sort` values repeat.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// First page sorted by creation date, with ID as tiebreaker
val page1: Slice<Post> = postRepository.slice(Post_.createdAt, Post_.id, 20)

// Next page: pass both cursor values from the last item
val last = page1.content.last()
val page2: Slice<Post> = postRepository.sliceAfter(
    Post_.createdAt, last.createdAt,
    Post_.id, last.id,
    20
)

// With filter
val activePage: Slice<Post> = postRepository.slice(Post_.createdAt, Post_.id, 20) {
    Post_.active eq true
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
// First page sorted by creation date, with ID as tiebreaker
Slice<Post> page1 = postRepository.slice(Post_.createdAt, Post_.id, 20);

// Next page: pass both cursor values from the last item
Post last = page1.content().getLast();
Slice<Post> page2 = postRepository.sliceAfter(
    Post_.createdAt, last.createdAt(),
    Post_.id, last.id(),
    20);
```

</TabItem>
</Tabs>

The client is responsible for extracting both cursor values from the last (or first) item of the current page and passing them to the next request.

For queries that need joins, projections, or more complex filtering, use the query builder and call `slice` as a terminal operation. See [Queries](queries.md#keyset-pagination-with-slice) for the full details on how keyset pagination composes with WHERE and ORDER BY clauses, including indexing recommendations.

### Offset vs. Keyset Pagination

Storm supports both offset-based and keyset-based pagination. The table below summarizes the trade-offs to help you choose.

| Factor | Offset-Based (`page`) | Keyset-Based (`slice`) |
|---|---|---|
| Implementation complexity | Simple | Moderate |
| Jump to arbitrary page | Yes | No (sequential only) |
| Performance at page 1 | Good | Good |
| Performance at page 1,000 | Degrades (database must skip rows) | Consistent (index seek) |
| Handles concurrent inserts | Rows may shift between pages | Stable cursor |
| Total element count | Included in `Page` | Not available in `Slice` |

Use offset-based pagination when you need random page access or a total count (for example, displaying "Page 3 of 12" in a UI). Use keyset pagination when you need consistent performance over deep result sets or when the data changes frequently between requests.

---

## Refs

Refs are lightweight identifiers that carry only the record type and primary key. Selecting refs instead of full entities reduces memory usage and network bandwidth when you only need IDs for subsequent operations, such as batch lookups or filtering. See [Refs](refs.md) for a detailed discussion.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
// Select refs (lightweight identifiers)
val refs: Flow<Ref<User>> = userRepository.selectAllRef()

// Select by refs
val users: Flow<User> = userRepository.selectByRef(refs)
```

</TabItem>
<TabItem value="java" label="Java">

Ref operations in Java return `Stream` objects that must be closed. Refs carry only the primary key and record type, making them suitable for batch operations where loading full records would be wasteful.

```java
// Select refs (lightweight identifiers)
try (Stream<Ref<User>> refs = userRepository.selectAllRef()) {
    // Process refs
}

// Select by refs
List<Ref<User>> refList = ...;
try (Stream<User> users = userRepository.selectByRef(refList.stream())) {
    // Process users
}
```

</TabItem>
</Tabs>

---

## Custom Repositories

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Custom repositories let you encapsulate domain-specific queries behind a typed interface. Define an interface that extends `EntityRepository`, add methods with default implementations that use the inherited query API, and retrieve it from `orm.repository()`. This keeps query logic in a single place and makes it testable through interface substitution.

The advantage over using the generic `entity()` repository is that custom methods express domain intent (e.g., `findByEmail`) rather than exposing raw query construction to callers.

```kotlin
interface UserRepository : EntityRepository<User, Int> {

    // Custom query method
    fun findByEmail(email: String): User? =
        find { User_.email eq email }

    // Custom query with multiple conditions
    fun findByNameInCity(name: String, city: City): List<User> =
        findAll((User_.city eq city) and (User_.name eq name))
}
```

Get the repository:

```kotlin
val userRepository: UserRepository = orm.repository<UserRepository>()
```

</TabItem>
<TabItem value="java" label="Java">

Java custom repositories follow the same pattern as Kotlin, using `default` methods to provide implementations. The fluent builder API chains `where`, `and`, and `or` calls to construct type-safe filter expressions.

```java
interface UserRepository extends EntityRepository<User, Integer> {

    // Custom query method
    default Optional<User> findByEmail(String email) {
        return select()
            .where(User_.email, EQUALS, email)
            .getOptionalResult();
    }

    // Custom query with multiple conditions
    default List<User> findByNameInCity(String name, City city) {
        return select()
            .where(it -> it.where(User_.city, EQUALS, city)
                    .and(it.where(User_.name, EQUALS, name)))
            .getResultList();
    }
}
```

Get the repository:

```java
UserRepository userRepository = orm.repository(UserRepository.class);
```

</TabItem>
</Tabs>

---

## Repository with Spring

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Repositories can be injected using Spring's dependency injection:

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun findUser(email: String): User? =
        userRepository.findByEmail(email)
}
```

</TabItem>
<TabItem value="java" label="Java">

Repositories can be injected using Spring's dependency injection:

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findUser(String email) {
        return userRepository.findByEmail(email);
    }
}
```

</TabItem>
</Tabs>

---

## Spring Configuration

Storm repositories are plain interfaces, so Spring cannot discover them through component scanning. The `RepositoryBeanFactoryPostProcessor` bridges this gap by scanning specified packages for interfaces that extend `EntityRepository` or `ProjectionRepository` and registering proxy implementations as Spring beans. Once registered, you can inject repositories through standard constructor injection. See [Spring Integration](spring-integration.md) for full configuration details.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
@Configuration
class AcmeRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {

    override val repositoryBasePackages: Array<String>
        get() = arrayOf("com.acme.repository")
}
```

</TabItem>
<TabItem value="java" label="Java">

```java
@Configuration
public class AcmeRepositoryBeanFactoryPostProcessor extends RepositoryBeanFactoryPostProcessor {

    @Override
    public String[] getRepositoryBasePackages() {
        return new String[] { "com.acme.repository" };
    }
}
```

</TabItem>
</Tabs>

## Tips

1. **Use custom repositories.** Encapsulate domain-specific queries in repository interfaces.
2. **Close streams.** Always close `Stream` results to release database resources.
3. **Prefer Kotlin Flow.** Kotlin's Flow automatically handles resource cleanup.
4. **Use Spring injection.** Let Spring manage repository lifecycle for cleaner code.
