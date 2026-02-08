# Repositories

Entity repositories provide a high-level abstraction for managing entities in the database. They offer methods for creating, reading, updating, and deleting entities, as well as querying and filtering based on specific criteria.

---

## Kotlin

### Getting a Repository

Storm provides two ways to obtain a repository. The generic `entity()` method returns a built-in repository with standard CRUD operations. For custom query methods, define your own interface extending `EntityRepository` and retrieve it with `repository()` (covered below in Custom Repositories).

```kotlin
val orm = ORMTemplate.of(dataSource)

// Generic entity repository
val userRepository = orm.entity(User::class)

// Or using extension function
val userRepository = orm.entity<User>()
```

### Basic CRUD Operations

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
```

### Streaming with Flow

For result sets that may be large, streaming avoids loading all rows into memory at once. Kotlin's `Flow` provides automatic resource management through structured concurrency: the underlying database cursor and connection are released when the flow completes or is cancelled, without requiring explicit cleanup.

```kotlin
val users: Flow<User> = userRepository.selectAll()
val count = users.count()

// Collect to list
val userList: List<User> = users.toList()
```

### Refs

Refs are lightweight identifiers that carry only the entity type and primary key. Selecting refs instead of full entities reduces memory usage and network bandwidth when you only need IDs for subsequent operations, such as batch lookups or filtering. See [Refs](refs.md) for a detailed discussion.

```kotlin
// Select refs (lightweight identifiers)
val refs: Flow<Ref<User>> = userRepository.selectAllRef()

// Select by refs
val users: Flow<User> = userRepository.selectByRef(refs)
```

### Custom Repositories

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

### Repository with Spring

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

---

## Java

### Getting a Repository

The Java API follows the same pattern as Kotlin. The generic `entity()` method provides standard CRUD operations; custom interfaces use `repository()`.

```java
var orm = ORMTemplate.of(dataSource);

// Generic entity repository
EntityRepository<User, Integer> userRepository = orm.entity(User.class);
```

### Basic CRUD Operations

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
```

### Streaming

Java streams over database results hold open a database cursor and connection. You must close the stream explicitly, either with try-with-resources or by calling `close()`. Failing to close the stream leaks database connections.

```java
try (Stream<User> users = userRepository.selectAll()) {
    List<Integer> userIds = users.map(User::id).toList();
}
```

### Refs

Ref operations in Java return `Stream` objects that must be closed. Refs carry only the primary key and entity type, making them suitable for batch operations where loading full entities would be wasteful.

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

### Custom Repositories

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

### Repository with Spring

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

---

## Spring Configuration

Storm repositories are plain interfaces, so Spring cannot discover them through component scanning. The `RepositoryBeanFactoryPostProcessor` bridges this gap by scanning specified packages for interfaces that extend `EntityRepository` or `ProjectionRepository` and registering proxy implementations as Spring beans. Once registered, you can inject repositories through standard constructor injection. See [Spring Integration](spring-integration.md) for full configuration details.

### Kotlin

```kotlin
@Configuration
class AcmeRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {

    override val repositoryBasePackages: Array<String>
        get() = arrayOf("com.acme.repository")
}
```

### Java

```java
@Configuration
public class AcmeRepositoryBeanFactoryPostProcessor extends RepositoryBeanFactoryPostProcessor {

    @Override
    public String[] getRepositoryBasePackages() {
        return new String[] { "com.acme.repository" };
    }
}
```

## Tips

1. **Use custom repositories.** Encapsulate domain-specific queries in repository interfaces.
2. **Close streams.** Always close `Stream` results to release database resources.
3. **Prefer Kotlin Flow.** Kotlin's Flow automatically handles resource cleanup.
4. **Use Spring injection.** Let Spring manage repository lifecycle for cleaner code.
