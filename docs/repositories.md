# Repositories

Entity repositories provide a high-level abstraction for managing entities in the database. They offer methods for creating, reading, updating, and deleting entities, as well as querying and filtering based on specific criteria.

---

## Kotlin

### Getting a Repository

```kotlin
val orm = ORMTemplate.of(dataSource)

// Generic entity repository
val userRepository = orm.entity(User::class)

// Or using extension function
val userRepository = orm.entity<User>()
```

### Basic CRUD Operations

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

```kotlin
val users: Flow<User> = userRepository.selectAll()
val count = users.count()

// Collect to list
val userList: List<User> = users.toList()
```

### Refs

```kotlin
// Select refs (lightweight identifiers)
val refs: Flow<Ref<User>> = userRepository.selectAllRef()

// Select by refs
val users: Flow<User> = userRepository.selectByRef(refs)
```

### Custom Repositories

Define specialized repositories with custom methods:

```kotlin
interface UserRepository : EntityRepository<User, Int> {

    // Custom query method
    fun findByEmail(email: String): User? =
        find { User_.email eq email }

    // Custom query with multiple conditions
    fun findActiveUsersInCity(city: City): List<User> =
        findAll((User_.city eq city) and (User_.active eq true))
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

```java
var orm = ORMTemplate.of(dataSource);

// Generic entity repository
EntityRepository<User, Integer> userRepository = orm.entity(User.class);
```

### Basic CRUD Operations

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

```java
try (Stream<User> users = userRepository.selectAll()) {
    List<Integer> userIds = users.map(User::id).toList();
}
```

### Refs

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

Define specialized repositories with custom methods:

```java
interface UserRepository extends EntityRepository<User, Integer> {

    // Custom query method
    default Optional<User> findByEmail(String email) {
        return select()
            .where(User_.email, EQUALS, email)
            .getOptionalResult();
    }

    // Custom query with multiple conditions
    default List<User> findActiveUsersInCity(City city) {
        return select()
            .where(it -> it.where(User_.city, EQUALS, city)
                    .and(it.where(User_.active, EQUALS, true)))
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

To enable repository injection in Spring, extend `RepositoryBeanFactoryPostProcessor`:

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

1. **Use custom repositories** — Encapsulate domain-specific queries in repository interfaces
2. **Close streams** — Always close `Stream` results to release database resources
3. **Prefer Kotlin Flow** — Kotlin's Flow automatically handles resource cleanup
4. **Use Spring injection** — Let Spring manage repository lifecycle for cleaner code
