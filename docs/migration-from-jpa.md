# Migration from JPA

This guide helps you transition from JPA/Hibernate to Storm. The two frameworks can coexist, allowing gradual migration.

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

### Lazy Loading → Eager/Ref

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

1. **Add Storm dependencies** alongside JPA
2. **Create Storm entities** for new tables
3. **Migrate simple entities first** (no complex relationships)
4. **Replace lazy loading with Ref** where needed
5. **Migrate repositories** one at a time
6. **Update service layer** to use Storm repositories
7. **Remove JPA entities and dependencies** when complete

## Coexistence

Storm and JPA can coexist in the same application:

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

### Missing Eager Fetch

JPA developers often forget that Storm doesn't have lazy loading. If you need the related entity, include `@FK`:

```kotlin
// Wrong - city not available
data class User(@PK val id: Long = 0, val cityId: Long) : Entity<Long>

// Right - city loaded with user
data class User(@PK val id: Long = 0, @FK val city: City) : Entity<Long>
```

### Mutable Habits

Storm entities are immutable. Use `copy()` instead of setters:

```kotlin
// Wrong (JPA style)
user.setName("New Name")

// Right (Storm style)
val updated = user.copy(name = "New Name")
orm update updated
```

### Collection Expectations

Storm intentionally doesn't support collection fields on entities. This is a deliberate design choice—collections on entities lead to lazy loading, N+1 queries, and unpredictable behavior. Query relationships explicitly:

```kotlin
// Wrong expectation
val orders = user.orders  // Not supported

// Right approach
val orders = orm.findAll { Order_.user eq user }
```
