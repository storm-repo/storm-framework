# Spring Integration

Storm integrates seamlessly with Spring Framework and Spring Boot for dependency injection, transaction management, and repository auto-wiring.

## Installation

### Kotlin

```groovy
implementation 'st.orm:storm-kotlin-spring:1.8.2'
```

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-kotlin-spring</artifactId>
    <version>1.8.2</version>
</dependency>
```

### Java

```groovy
implementation 'st.orm:storm-spring:1.8.2'
```

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-spring</artifactId>
    <version>1.8.2</version>
</dependency>
```

---

## Kotlin

### Configuration

```kotlin
@Configuration
@EnableTransactionManagement
class ORMConfiguration(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate(): ORMTemplate = ORMTemplate.of(dataSource)
}
```

### Transaction Integration

Enable integration between Storm's programmatic transactions and Spring:

```kotlin
@EnableTransactionIntegration
@Configuration
class ORMConfiguration(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate(): ORMTemplate = ORMTemplate.of(dataSource)
}
```

This allows combining Spring's `@Transactional` with Storm's programmatic `transaction` blocks:

```kotlin
@Transactional
fun processUsers() {
    // Spring manages outer transaction

    transactionBlocking {
        // Participates in Spring transaction
        orm.deleteAll<Visit>()
    }
}
```

### Repository Injection

Configure repository auto-discovery:

```kotlin
@Configuration
class AcmeRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {

    override val repositoryBasePackages: Array<String>
        get() = arrayOf("com.acme.repository")
}
```

Define repositories:

```kotlin
interface UserRepository : EntityRepository<User, Int> {

    fun findByEmail(email: String): User? =
        find { User_.email eq email }
}
```

Inject into services:

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun findUser(email: String): User? =
        userRepository.findByEmail(email)
}
```

### Using @Transactional

```kotlin
@Service
class UserService(
    private val orm: ORMTemplate
) {

    @Transactional
    fun createUser(email: String, name: String): User {
        return orm insert User(email = email, name = name)
    }

    @Transactional(readOnly = true)
    fun findUsers(): List<User> {
        return orm.findAll<User>()
    }
}
```

---

## Java

### Configuration

```java
@Configuration
public class ORMConfiguration {

    private final DataSource dataSource;

    public ORMConfiguration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public ORMTemplate ormTemplate() {
        return ORMTemplate.of(dataSource);
    }
}
```

### Repository Injection

Configure repository auto-discovery:

```java
@Configuration
public class AcmeRepositoryBeanFactoryPostProcessor extends RepositoryBeanFactoryPostProcessor {

    @Override
    public String[] getRepositoryBasePackages() {
        return new String[] { "com.acme.repository" };
    }
}
```

Define repositories:

```java
public interface UserRepository extends EntityRepository<User, Integer> {

    default Optional<User> findByEmail(String email) {
        return select()
            .where(User_.email, EQUALS, email)
            .getOptionalResult();
    }
}
```

Inject into services:

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

### Using @Transactional

```java
@Service
public class UserService {

    private final ORMTemplate orm;

    public UserService(ORMTemplate orm) {
        this.orm = orm;
    }

    @Transactional
    public User createUser(String email, String name) {
        return orm.entity(User.class)
            .insertAndFetch(new User(null, email, name, null, null));
    }

    @Transactional(readOnly = true)
    public List<User> findUsers() {
        return orm.entity(User.class)
            .select()
            .getResultList();
    }
}
```

---

## Spring Boot Auto-Configuration

Storm works with Spring Boot's auto-configured DataSource. Simply inject the DataSource and create the ORMTemplate bean:

```kotlin
@Configuration
class StormConfig(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate() = ORMTemplate.of(dataSource)
}
```

## JPA Entity Manager

Storm can also work with JPA's EntityManager:

```java
@PersistenceContext
private EntityManager entityManager;

@Transactional
public void doWork() {
    var orm = ORMTemplate.of(entityManager);
    // Use orm...
}
```

## Transaction Propagation

When `@EnableTransactionIntegration` is active, Storm's programmatic transactions participate in Spring's transaction propagation:

### Joining Existing Transactions

```kotlin
@Transactional
fun outerMethod() {
    // Spring starts a transaction

    transactionBlocking {
        // This block joins the Spring transaction
        orm.insert(user1)
    }

    transactionBlocking {
        // This block also joins the same transaction
        orm.insert(user2)
    }

    // Both inserts commit or rollback together
}
```

### Starting New Transactions

Without an outer `@Transactional`, each `transactionBlocking` starts its own transaction:

```kotlin
fun methodWithoutTransactional() {
    transactionBlocking {
        // Starts new transaction
        orm.insert(user1)
    }  // Commits here

    transactionBlocking {
        // Starts another new transaction
        orm.insert(user2)
    }  // Commits here
}
```

### Key Benefits of Programmatic Transactions

1. **Explicit boundaries** — See exactly where transactions start and end
2. **Compile-time safety** — No risk of forgetting `@Transactional` on a method
3. **Flexible composition** — Easily combine with Spring's declarative model
4. **Reduced proxy overhead** — No need for Spring's transaction proxies in pure Storm code

### Mixing Approaches

You can use both styles in the same application:

```kotlin
@Service
class OrderService(
    private val orm: ORMTemplate,
    private val paymentService: PaymentService  // Uses @Transactional
) {

    @Transactional
    fun processOrder(order: Order) {
        // Spring transaction

        transactionBlocking {
            // Participates in Spring transaction
            orm.insert(order)
        }

        // Other @Transactional services also participate
        paymentService.processPayment(order)
    }
}
```

## Tips

1. **Use `@Transactional` for declarative transactions** — Simple and familiar
2. **Use programmatic transactions for complex flows** — Nested transactions, explicit propagation
3. **Enable transaction integration for Kotlin** — Allows mixing declarative and programmatic styles
4. **Configure repository packages** — Auto-wire custom repositories like any Spring bean
