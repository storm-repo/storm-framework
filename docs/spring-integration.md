# Spring Integration

Storm integrates seamlessly with Spring Framework and Spring Boot for dependency injection, transaction management, and repository auto-wiring. This guide covers setup for both Kotlin and Java.

## Installation

### Kotlin

```kotlin
// Gradle (Kotlin DSL)
implementation("st.orm:storm-kotlin-spring:1.8.2")
```

```xml
<!-- Maven -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-kotlin-spring</artifactId>
    <version>1.8.2</version>
</dependency>
```

### Java

```kotlin
// Gradle (Kotlin DSL)
implementation("st.orm:storm-spring:1.8.2")
```

```xml
<!-- Maven -->
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-spring</artifactId>
    <version>1.8.2</version>
</dependency>
```

The Spring integration modules provide transaction integration and repository auto-discovery. They are in addition to the base `storm-kotlin` or `storm-java21` dependency.

---

## Kotlin

### Configuration

The minimum setup requires a single `ORMTemplate` bean. This bean is the entry point for all Storm operations and takes a standard `DataSource` as its only dependency. Spring Boot applications typically have a `DataSource` already configured through `application.properties`, so the `ORMTemplate` bean is the only Storm-specific configuration you need to add.

```kotlin
@Configuration
@EnableTransactionManagement
class ORMConfiguration(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate(): ORMTemplate = ORMTemplate.of(dataSource)
}
```

### Transaction Integration

By default, Storm manages its own transactions independently of Spring's transaction context. The `@EnableTransactionIntegration` annotation bridges the two systems so that Storm's programmatic `transaction` and `transactionBlocking` blocks participate in Spring-managed transactions. Without this annotation, a transaction block inside a `@Transactional` method would open a separate database connection and transaction.

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

Storm repositories are interfaces with default method implementations. Spring cannot discover them automatically because they are not annotated with `@Component` or `@Repository`. The `RepositoryBeanFactoryPostProcessor` scans specified packages for interfaces that extend `EntityRepository` or `ProjectionRepository` and registers them as Spring beans. This makes them available for constructor injection like any other Spring-managed dependency.

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

The Java configuration mirrors the Kotlin setup. Define a single `ORMTemplate` bean that wraps the Spring-managed `DataSource`.

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

Register a `RepositoryBeanFactoryPostProcessor` that scans your repository packages. This works identically to the Kotlin version: Storm discovers interfaces extending `EntityRepository` or `ProjectionRepository` and registers them as Spring beans.

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

Storm works with Spring Boot's auto-configured DataSource. No special configuration is needed beyond defining the ORM bean:

```kotlin
@Configuration
class StormConfig(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate() = ORMTemplate.of(dataSource)
}
```

Spring Boot automatically configures the DataSource from your `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypassword
```

Storm picks up this DataSource and auto-detects the dialect from the JDBC URL.

## Minimal Spring Boot Setup

Here is a complete minimal setup for a Spring Boot application with Storm:

```kotlin
@SpringBootApplication
@EnableTransactionManagement
class Application

@Configuration
@EnableTransactionIntegration
class StormConfig(private val dataSource: DataSource) {

    @Bean
    fun ormTemplate() = ORMTemplate.of(dataSource)
}

@Configuration
class MyRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {

    override val repositoryBasePackages: Array<String>
        get() = arrayOf("com.myapp.repository")
}
```

This gives you:
- Automatic DataSource from Spring Boot
- Transaction integration between Spring and Storm
- Repository auto-discovery and injection

## JPA Entity Manager

Storm can create an `ORMTemplate` from a JPA `EntityManager`, which lets you use Storm queries within existing JPA transactions and services. This is particularly useful during incremental [migration from JPA](migration-from-jpa.md), where you can convert one repository or query at a time without changing your transaction management strategy.

```java
@PersistenceContext
private EntityManager entityManager;

@Transactional
public void doWork() {
    var orm = ORMTemplate.of(entityManager);
    // Use orm alongside existing JPA code
}
```

## Transaction Propagation

When `@EnableTransactionIntegration` is active, Storm's programmatic transactions participate in Spring's transaction propagation. This means a `transaction` or `transactionBlocking` block checks for an existing Spring-managed transaction before starting a new one. If a transaction already exists, the block joins it. If not, it creates a new independent transaction.

Understanding this behavior is important for controlling atomicity. When multiple operations must commit or roll back as a unit, they need to share the same transaction. When operations should be independent (for example, logging that should persist even if the main operation fails), they need separate transactions.

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

Without an outer `@Transactional`, each `transactionBlocking` block starts and commits its own transaction independently. A failure in one block does not affect previously committed blocks.

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

1. **Explicit boundaries.** See exactly where transactions start and end.
2. **Compile-time safety.** No risk of forgetting `@Transactional` on a method.
3. **Flexible composition.** Easily combine with Spring's declarative model.
4. **Reduced proxy overhead.** No need for Spring's transaction proxies in pure Storm code.

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

1. **Use `@Transactional` for declarative transactions.** Simple and familiar for Spring developers.
2. **Use programmatic transactions for complex flows.** Nested transactions, savepoints, and explicit propagation are easier to express in code.
3. **Enable `@EnableTransactionIntegration` for Kotlin.** This allows mixing `@Transactional` with programmatic `transaction {}` blocks.
4. **Configure repository packages.** Auto-wire custom repositories like any Spring bean via `RepositoryBeanFactoryPostProcessor`.
5. **One `ORMTemplate` bean is enough.** Inject it into services or let repositories use it automatically.
6. **Works with any DataSource.** HikariCP, Tomcat pool, or any other connection pool that Spring Boot configures.
