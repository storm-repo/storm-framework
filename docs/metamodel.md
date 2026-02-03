# Static Metamodel

The static metamodel is a code generation feature that creates companion classes for your entities at compile time. These generated classes provide type-safe references to entity fields, enabling the compiler to catch errors that would otherwise surface only at runtime.

Using the metamodel is optional—Storm works without it using SQL Templates or string-based field references. However, for projects that want to leverage Storm's full capabilities, the metamodel provides significant benefits in terms of type safety, IDE support, and maintainability.

## Why Use a Metamodel?

Storm uses Java records and Kotlin data classes as entities. While this stateless approach simplifies the programming model, it presents a challenge: how do you reference entity fields in a type-safe way without using reflection at runtime?

The metamodel solves this by generating accessor classes during compilation. These classes provide direct access to record components without reflection, which offers two advantages:

- **Performance** — No reflection overhead when accessing field metadata or values
- **Type safety** — The compiler verifies field references, catching typos and type mismatches before your code runs

## What is the Metamodel?

For each entity class, Storm generates a corresponding metamodel class with a `_` suffix:

- `User` → `User_`
- `City` → `City_`
- `UserRole` → `UserRole_`

The metamodel contains typed references to each field that can be used in queries.

## Installation

### Maven

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-metamodel-processor</artifactId>
    <version>1.8.2</version>
    <scope>provided</scope>
</dependency>
```

### Gradle (Java)

```groovy
annotationProcessor 'st.orm:storm-metamodel-processor:1.8.2'
```

### Gradle (Kotlin with KSP)

```groovy
plugins {
    id 'com.google.devtools.ksp' version '2.0.0-1.0.21'
}

dependencies {
    ksp 'st.orm:storm-metamodel-processor:1.8.2'
}
```

## Usage

### Kotlin

```kotlin
// Type-safe field reference
val users = orm.findAll { User_.email eq email }

// Navigate relationships
val users = orm.findAll { User_.city.name eq "San Francisco" }

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

// Navigate relationships
List<User> users = orm.entity(User.class)
    .select()
    .where(User_.city.name, EQUALS, "San Francisco")
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

## Generated Code

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

1. **Compile-time safety** — Typos caught at compile time, not runtime
2. **IDE support** — Auto-completion for field names
3. **Refactoring** — Rename fields safely; the compiler catches all usages
4. **Type checking** — Can't compare a String field to an Integer

## Without the Metamodel

You can still use Storm without the metamodel using SQL Templates or string-based field references, but you lose compile-time type safety.

## Tips

1. **Regenerate after changes** — Rebuild after adding/modifying entity fields
2. **Check your IDE setup** — Ensure annotation processing is enabled
3. **Use for all queries** — Consistent use of metamodel prevents runtime errors
