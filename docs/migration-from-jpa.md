# Migration from JPA

This guide helps you transition from JPA/Hibernate to Storm. The two frameworks can coexist in the same application, allowing you to migrate gradually, one entity or repository at a time.

## Key Differences

| JPA/Hibernate | Storm |
|---------------|-------|
| Mutable entities with proxies | Immutable records/data classes |
| Managed persistence context | Stateless operations |
| Lazy loading by default | Eager loading in single query |
| `@Entity`, `@Id`, `@Column` | `@PK`, `@FK`, `@DbColumn` |
| JPQL / Criteria API | Type-safe DSL / SQL Templates |
| EntityManager | ORMTemplate |
| `@OneToMany`, `@ManyToOne` | `@FK` annotation |

## Entity Migration

The biggest conceptual shift from JPA to Storm is the move from mutable, proxy-backed classes to immutable records and data classes. In JPA, entities carry hidden state: change-tracking proxies, managed lifecycle, and lazy-loading hooks injected via bytecode. Storm eliminates all of this. An entity is a plain value object with annotations that describe its mapping. The database interaction happens in repositories and templates, not inside the entity itself.

This separation makes entities safe to pass across layers, serialize, and store in collections without worrying about detachment or session scope.

### JPA Entity

```java
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    // Getters and setters...
}
```

### Storm Entity (Kotlin)

```kotlin
data class User(
    @PK val id: Long = 0,
    val email: String,
    val name: String?,
    @FK val city: City?
) : Entity<Long>
```

### Storm Entity (Java)

```java
record User(@PK Long id,
            @Nonnull String email,
            @Nullable String name,
            @Nullable @FK City city
) implements Entity<Long> {}
```

## Annotation Mapping

Storm uses fewer annotations than JPA because it derives most mapping information from the entity structure itself. Table names follow from the class name (converted to snake_case), and column names follow from field names. You only need annotations for primary keys, foreign keys, and cases where the default naming does not match your schema.

| JPA | Storm |
|-----|-------|
| `@Entity` | Implement `Entity<T>` interface |
| `@Table(name = "...")` | `@DbTable("...")` |
| `@Id` | `@PK` |
| `@Column(name = "...")` | `@DbColumn("...")` |
| `@ManyToOne` | `@FK` |
| `@JoinColumn` | Column name in `@FK("...")` |
| `@Version` | `@Version` |

## Repository Migration

JPA repositories (particularly Spring Data JPA) rely on method name conventions or `@Query` annotations to define queries. Storm repositories use explicit method bodies with a type-safe DSL. This means slightly more code per method, but every query is visible in the source, IDE-navigable, and compiler-checked. There are no hidden query derivation rules to memorize.

### JPA Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByCity(City city);
}
```

### Storm Repository (Kotlin)

```kotlin
interface UserRepository : EntityRepository<User, Long> {

    fun findByEmail(email: String): User? =
        find { User_.email eq email }

    fun findByCity(city: City): List<User> =
        findAll { User_.city eq city }
}
```

### Storm Repository (Java)

```java
interface UserRepository extends EntityRepository<User, Long> {

    default Optional<User> findByEmail(String email) {
        return select()
            .where(User_.email, EQUALS, email)
            .getOptionalResult();
    }

    default List<User> findByCity(City city) {
        return select()
            .where(User_.city, EQUALS, city)
            .getResultList();
    }
}
```

## Query Migration

Storm offers two query approaches: the type-safe DSL (using the generated metamodel) and SQL Templates (for raw SQL with type interpolation). The DSL covers common CRUD patterns concisely, while SQL Templates let you write arbitrary SQL without losing type safety on parameters and result mapping. The examples below show how each JPA query style maps to Storm equivalents.

### JPQL

```java
// JPA
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// Storm (Kotlin)
fun findByEmail(email: String): User? =
    find { User_.email eq email }

// Storm (Java)
default Optional<User> findByEmail(String email) {
    return select()
        .where(User_.email, EQUALS, email)
        .getOptionalResult();
}
```

### Criteria API

```java
// JPA Criteria
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> cq = cb.createQuery(User.class);
Root<User> root = cq.from(User.class);
cq.where(cb.equal(root.get("city"), city));
return em.createQuery(cq).getResultList();

// Storm (Kotlin)
orm.findAll { User_.city eq city }

// Storm (Java)
orm.entity(User.class)
    .select()
    .where(User_.city, EQUALS, city)
    .getResultList();
```

### Native Queries

```java
// JPA
@Query(value = "SELECT * FROM users WHERE email LIKE %:pattern%", nativeQuery = true)
List<User> searchByEmail(@Param("pattern") String pattern);

// Storm (Java)
orm.query(RAW."SELECT \{User.class} FROM \{User.class} WHERE email LIKE \{pattern}")
    .getResultList(User.class);
```

## Relationship Changes

JPA models relationships bidirectionally with annotations like `@OneToMany` and `@ManyToOne`, relying on lazy-loading proxies to defer fetching. Storm takes a different approach: relationships are unidirectional, defined by `@FK` on the owning side, and loaded eagerly by default in the same query. When you need deferred loading (for example, to avoid loading a large sub-graph), wrap the field type in `Ref<T>` to make fetching explicit.

### Lazy Loading to Eager/Ref

JPA default: lazy loading with proxy

```java
// JPA - fetches city on access (N+1 risk)
user.getCity().getName();
```

Storm options:

1. **Eager loading** (default with `@FK`):
```kotlin
data class User(@PK val id: Long = 0, @FK val city: City) : Entity<Long>
// City loaded in same query as User
```

2. **Deferred loading** (with `Ref`):
```kotlin
data class User(@PK val id: Long = 0, @FK val city: Ref<City>) : Entity<Long>
// City loaded explicitly when needed
val cityName = user.city.fetch().name
```

### OneToMany Collections

JPA approach:
```java
@OneToMany(mappedBy = "user")
private List<Order> orders;
```

Storm approach (query the "many" side):
```kotlin
val orders = orm.findAll { Order_.user eq user }
```

## Transaction Migration

Storm supports both Spring's `@Transactional` annotation and its own programmatic `transaction {}` block. If you are migrating a Spring application, your existing `@Transactional` annotations continue to work unchanged. Storm participates in the same Spring-managed transaction. The programmatic API is useful when you want explicit control over isolation levels, propagation, or when working outside of Spring entirely.

### JPA @Transactional

```java
@Transactional
public void createUser(String email) {
    userRepository.save(new User(email));
}
```

### Storm (works with Spring @Transactional)

```kotlin
@Transactional
fun createUser(email: String) {
    orm insert User(email = email)
}
```

### Storm Programmatic (Kotlin)

```kotlin
transaction {
    orm insert User(email = email)
}
```

## Gradual Migration Strategy

A full rewrite is rarely practical. Storm and JPA can share the same DataSource, so you can migrate incrementally without a flag day. Start with leaf entities (those with no inbound foreign keys from other JPA entities) and work inward. Each migrated entity reduces your JPA surface area without breaking existing code.

1. **Add Storm dependencies** alongside JPA.
2. **Create Storm entities** for new tables.
3. **Migrate simple entities first** (no complex relationships).
4. **Replace lazy loading with Ref** where needed.
5. **Migrate repositories** one at a time.
6. **Update service layer** to use Storm repositories.
7. **Remove JPA entities and dependencies** when complete.

## Coexistence

Storm and JPA can coexist in the same application. Both frameworks use JDBC under the hood, so they share connection pools and participate in the same transactions when managed by Spring. The key requirement is that you configure Storm's `ORMTemplate` with the same `DataSource` that JPA uses.

```kotlin
@Configuration
class PersistenceConfig(
    private val dataSource: DataSource,
    private val entityManager: EntityManager
) {
    @Bean
    fun ormTemplate() = ORMTemplate.of(dataSource)

    // JPA EntityManager still available for legacy code
}
```

## Common Pitfalls

The most frequent issues arise from habits carried over from JPA. The following patterns cover the mistakes that developers encounter most often during migration.

### Missing Eager Fetch

In JPA, relationships are lazy-loaded by default, so you can define a foreign key column as a raw ID and still access the related entity through the proxy. Storm has no proxies. If you declare a field as a raw ID (e.g., `val cityId: Long`), Storm treats it as a plain column value with no relationship. To load the related entity, use `@FK` with the entity type.

```kotlin
// Wrong - city not available
data class User(@PK val id: Long = 0, val cityId: Long) : Entity<Long>

// Right - city loaded with user
data class User(@PK val id: Long = 0, @FK val city: City) : Entity<Long>
```

### Mutable Habits

JPA entities are mutable: you call setters, and the persistence context tracks changes automatically. Storm entities are immutable values. To modify an entity, create a new instance with the changed fields using Kotlin's `copy()` method or Java's record `with` pattern. The original instance remains unchanged, which makes reasoning about state straightforward.

```kotlin
// Wrong (JPA style)
user.setName("New Name")

// Right (Storm style)
val updated = user.copy(name = "New Name")
orm update updated
```

### Collection Expectations

Storm intentionally does not support collection fields on entities. This is a deliberate design choice. Collections on entities lead to lazy loading, N+1 queries, and unpredictable behavior. Query relationships explicitly:

```kotlin
// Wrong expectation
val orders = user.orders  // Not supported

// Right approach
val orders = orm.findAll { Order_.user eq user }
```

## What You Gain

After migrating from JPA to Storm, you can expect:

- **No more N+1 queries.** Entity graphs load in a single query by default.
- **No more LazyInitializationException.** No proxies, no surprise database hits.
- **No more detached entity errors.** Entities are stateless and always safe to use.
- **Simpler entities.** Records and data classes with a few annotations replace complex JPA mappings.
- **Predictable SQL.** What you see is what gets executed, no hidden query generation.
- **Fewer lines of code.** Typically ~5 lines per entity vs. ~30 for JPA.
