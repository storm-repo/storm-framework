# Static Metamodel

The static metamodel is a code generation feature that creates companion classes for your entities at compile time. These generated classes provide type-safe references to entity fields, enabling the compiler to catch errors that would otherwise surface only at runtime.

Using the metamodel is optional. Storm works without it using SQL Templates or string-based field references. However, for projects that want to leverage Storm's full capabilities, the metamodel provides significant benefits in terms of type safety, IDE support, and maintainability.

## Why Use a Metamodel?

Storm uses Java records and Kotlin data classes as entities. While this stateless approach simplifies the programming model, it presents a challenge: how do you reference entity fields in a type-safe way without using reflection at runtime?

The metamodel solves this by generating accessor classes during compilation. These classes provide direct access to record components without reflection, which offers two advantages:

- **Performance.** No reflection overhead when accessing field metadata or values.
- **Type safety.** The compiler verifies field references, catching typos and type mismatches before your code runs.

## What is the Metamodel?

For each entity class, Storm generates a corresponding metamodel class with a `_` suffix (following the JPA naming convention):

```
┌─────────────────┐                      ┌─────────────────┐
│     Entity      │    KSP / Annotation  │    Metamodel    │
│                 │      Processor       │                 │
│  User.kt        │  ─────────────────►  │  User_.java     │
│  City.kt        │                      │  City_.java     │
│  Country.kt     │                      │  Country_.java  │
└─────────────────┘                      └─────────────────┘
```

The metamodel contains typed references to each field that can be used in queries.

## Installation

The metamodel is **optional**. Storm works without it using SQL Templates or string-based field references. However, if you want compile-time type safety for your queries, you need to configure a code generator that creates the metamodel classes during compilation.

- **Kotlin projects** use KSP (Kotlin Symbol Processing)
- **Java projects** use an annotation processor

The generator scans your entity classes and creates corresponding metamodel classes (e.g., `User_` for `User`) in the same package.

### Gradle (Kotlin with KSP)

```groovy
plugins {
    id 'com.google.devtools.ksp' version '2.0.0-1.0.21'
}

dependencies {
    ksp 'st.orm:storm-metamodel-processor:1.9.0'
}
```

### Gradle (Java)

```groovy
annotationProcessor 'st.orm:storm-metamodel-processor:1.9.0'
```

### Maven (Java)

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-metamodel-processor</artifactId>
    <version>1.9.0</version>
    <scope>provided</scope>
</dependency>
```

> **Important:** Metamodel classes are generated at compile time. When you create or modify an entity, you must rebuild your project (or run the KSP/annotation processor task) before the corresponding metamodel class becomes available. Until then, your IDE will show errors for references like `User_`.

## Usage

Once the metamodel is generated, you use the `_` suffixed classes in place of string-based field references throughout your queries. The metamodel provides type-safe field accessors that the compiler can verify, so a renamed or removed field produces a compile error rather than a runtime exception. The following examples demonstrate the metamodel in queries for both Kotlin and Java.

### Kotlin

```kotlin
// Type-safe field reference
val users = orm.findAll { User_.email eq email }

// Type-safe access to nested fields throughout the entire entity graph
val users = orm.findAll { User_.city.country.code eq "US" }

// Multiple conditions
val users = orm.entity(User::class)
    .select()
    .where(
        (User_.city eq city) and (User_.birthDate less LocalDate.of(2000, 1, 1))
    )
    .resultList
```

### Java

```java
// Type-safe field reference
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.email, EQUALS, email)
    .getResultList();

// Type-safe access to nested fields throughout the entire entity graph
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.city.country.code, EQUALS, "US")
    .getResultList();
```

### SQL Templates (Java)

```java
Optional<User> user = orm.query(RAW."""
        SELECT \{User.class}
        FROM \{User.class}
        WHERE \{User_.email} = \{email}""")
    .getOptionalResult(User.class);
```

## Path Resolution

Storm supports two forms of metamodel references: **nested paths** and **short form**. Understanding when to use each is important for writing correct queries.

Consider an entity graph where `User` has a `city` field pointing to `City`, which has a `country` field pointing to `Country`:

```
┌────────┐       ┌────────┐       ┌──────────┐
│  User  │──────►│  City  │──────►│ Country  │
└────────┘ city  └────────┘country└──────────┘
```

### Nested Paths (Fully Qualified)

Nested paths traverse from the root entity through relationships by chaining field accessors:

```kotlin
// Start from User, traverse city → country → name
User_.city.country.name eq "United States"
```

Each step in the path corresponds to a foreign key relationship in your entity model:

```
User_.city          → User has FK to City
      .country      → City has FK to Country
             .name  → Country.name column
```

**Why nested paths are always unambiguous:**

When you write `User_.city.country.name`, Storm knows exactly which tables to join and in what order. Even if `Country` appears multiple times in your entity graph (e.g., via different relationships), the nested path explicitly identifies which occurrence you mean.

Storm automatically generates the necessary JOINs based on the path. For the example above:

```sql
SELECT ...
FROM user u
INNER JOIN city c ON u.city_id = c.id
INNER JOIN country co ON c.country_id = co.id
WHERE co.name = 'United States'
```

Each segment of the path gets its own table alias, and Storm tracks the mapping between paths and aliases internally.

### Short Form

Short form uses the target table's metamodel directly:

```kotlin
// Reference Country directly
Country_.name eq "United States"
```

Short form works **only when the table appears exactly once** in the entity graph. If `Country` is referenced in multiple places, Storm cannot determine which one you mean.

**Example where short form works:**

```kotlin
data class User(
    @PK val id: Int = 0,
    val name: String,
    @FK val city: City       // City → Country (only path to Country)
) : Entity<Int>

// Short form works - Country appears only once in User's entity graph
val users = orm.entity(User::class)
    .select()
    .whereAny(Country_.name eq "United States")  // Resolves to User → City → Country
    .resultList
```

The short form `Country_.name` works here because Storm first establishes `User` as the root entity, then looks up `Country` in User's entity graph. Since there's only one path to `Country` (via `city.country`), it's unambiguous.

Note the use of `whereAny` instead of `where`. The `where` method requires predicates typed to the root entity (`User`), while `whereAny` accepts predicates for any table in the entity graph. Since `Country_.name` produces a `Country`-typed predicate, `whereAny` is required.

**Type safety considerations:**

- **`where`** is fully type-safe. The predicate must be rooted at the query's entity type, so column lookup is guaranteed to succeed at runtime.
- **`whereAny`** is type-safe for the values you pass (e.g., comparing a `String` field to a `String` value), but the column lookup may fail at runtime if the referenced table doesn't exist in the entity graph or appears multiple times (ambiguity). Use nested paths or ensure uniqueness to avoid runtime exceptions.

**Example where short form fails:**

When `Country` appears multiple times in the entity graph, Storm cannot determine which one you mean:

```
                    ┌────────┐       ┌──────────┐
              ┌────►│  City  │──────►│ Country  │  (path 1: city.country)
┌────────┐    │     └────────┘country└──────────┘
│  User  │────┤
└────────┘    │                      ┌──────────┐
              └─────────────────────►│ Country  │  (path 2: birthCountry)
                    birthCountry     └──────────┘
```

```kotlin
data class User(
    @PK val id: Int = 0,
    val name: String,
    @FK val city: City,           // City → Country (path 1)
    @FK val birthCountry: Country  // Direct reference (path 2)
) : Entity<Int>

// ERROR: Multiple paths to Country in User's entity graph
val users = orm.entity(User::class)
    .select()
    .whereAny(Country_.name eq "United States")
    .resultList

// OK: Nested paths are unambiguous (and can use where since they're rooted at User_)
val users = orm.entity(User::class)
    .select()
    .where(User_.city.country.name eq "United States")
    .resultList

val users = orm.entity(User::class)
    .select()
    .where(User_.birthCountry.name eq "United States")
    .resultList
```

When Storm detects ambiguity, it throws an exception with a message indicating which paths are available.

### Custom Joins

Sometimes you need to join a table that has no `@FK` relationship defined in your entity model. For example, you might query users and filter by their orders without adding an `orders` field to the `User` entity. Custom joins add these tables to the query at runtime, making them available for filtering and projection.

Custom joins add tables that are not part of the entity graph:

```
Entity Graph                          Custom Join
─────────────                         ───────────
┌────────┐       ┌────────┐
│  User  │──────►│  City  │           ┌─────────┐
└────────┘       └────────┘      ┌───►│  Order  │  (added via innerJoin)
     │                           │    └─────────┘
     └───────────────────────────┘
         (manual join)
```

When you add custom joins to a query, those joined tables can **only** be referenced using short form:

```kotlin
val users = orm.entity(User::class)
    .select()
    .innerJoin(Order::class).on(User::class)  // Custom join
    .whereAny(Order_.total greater BigDecimal(100))  // Short form required, use whereAny
    .resultList
```

Custom joins are not part of the entity graph traversal, so nested paths cannot reach them. The short form works here because Storm registers the custom join's alias. Use `whereAny` since the predicate references `Order`, not the root entity `User`.

**Uniqueness still applies:** If you join the same table multiple times, you must use the `join` method with explicit aliases to disambiguate:

```kotlin
val users = orm.entity(User::class)
    .select()
    .join(JoinType.inner(), Order::class, "recent").on(User::class)
    .join(JoinType.inner(), Order::class, "first").on(User::class)
    .where(/* use SQL template with explicit aliases */)
    .resultList
```

### Resolution Order

When resolving a metamodel reference, Storm follows this order:

1. **Nested path.** If a path is specified (e.g., `User_.city.country`), use the alias for that specific traversal.
2. **Unique table lookup.** If short form (e.g., `Country_`), check if the table appears exactly once in the entity graph or registered joins.
3. **Error.** If multiple paths exist, throw an exception indicating the ambiguity.

### Best Practices

1. **Prefer nested paths** for clarity and to avoid ambiguity issues
2. **Use short form** for custom joins (required) or when you're certain the table is unique
3. **Check error messages.** Storm tells you which paths are available when ambiguity is detected.

## Generated Code

Understanding the generated code helps when debugging or reading compiler errors. The metamodel mirrors your entity structure, creating a static field for each entity field. Each field carries generic type parameters that encode both the root entity type and the field's value type, which is how the compiler enforces type safety in queries.

```
Entity                              Metamodel
──────                              ─────────
User                                User_
 ├── id: Int                         ├── id      → Metamodel<User, Int>
 ├── email: String                   ├── email   → Metamodel<User, String>
 ├── name: String                    ├── name    → Metamodel<User, String>
 └── city: City (FK)                 └── city    → CityMetamodel<User>
                                                      ├── id
                                                      ├── name
                                                      └── country → CountryMetamodel<User>
```

For an entity like:

```kotlin
data class User(
    @PK val id: Int = 0,
    val email: String,
    val name: String,
    @FK val city: City
) : Entity<Int>
```

The metamodel generates an interface with typed field accessors:

```java
@Generated("st.orm.metamodel.MetamodelProcessor")
public interface User_ extends Metamodel<User, User> {
    /** Represents the {@link User#id} field. */
    AbstractMetamodel<User, Integer, Integer> id = ...;
    /** Represents the {@link User#email} field. */
    AbstractMetamodel<User, String, String> email = ...;
    /** Represents the {@link User#name} field. */
    AbstractMetamodel<User, String, String> name = ...;
    /** Represents the {@link User#city} foreign key. */
    CityMetamodel<User> city = ...;
}
```

Foreign key fields like `city` generate their own metamodel classes, enabling navigation through relationships with full type safety.

## Benefits

1. **Compile-time safety.** Typos caught at compile time, not runtime.
2. **IDE support.** Auto-completion for field names.
3. **Refactoring.** Rename fields safely; the compiler catches all usages.
4. **Type checking.** Can't compare a String field to an Integer.

## Without the Metamodel

The metamodel is not required. You can use Storm with SQL Templates (Java) or raw query methods and string-based field references. This approach works well for prototyping, small projects, or queries that are too dynamic to express through the DSL. The trade-off is that field references become strings, which the compiler cannot verify. Typos and type mismatches will surface as runtime exceptions rather than compile errors.

## Tips

1. **Rebuild after changes.** Run `./gradlew build` or `mvn compile` after adding or modifying entity fields.
2. **Check your IDE setup.** Ensure annotation processing (Java) or KSP (Kotlin) is enabled in your IDE settings.
3. **Use for all queries.** Consistent use of metamodel prevents runtime errors.
