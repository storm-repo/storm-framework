# Installation

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page covers everything you need to add Storm to your project: prerequisites, dependency setup, and optional modules.

## Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK | 21 or later |
| Kotlin (if using Kotlin) | 2.0 or later |
| Build tool | Maven 3.9+ or Gradle 8+ |
| Database | Any JDBC-compatible database |

Kotlin users do not need any preview flags. Java users must enable `--enable-preview` in their compiler configuration because the Java API uses String Templates (JEP 430).

## Add the BOM

Storm provides a Bill of Materials (BOM) for centralized version management. Import the BOM once, then omit version numbers from individual Storm dependencies. This prevents version mismatches between modules.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.9.1"))
}
```

**Maven:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>st.orm</groupId>
            <artifactId>storm-bom</artifactId>
            <version>1.9.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

</TabItem>
<TabItem value="java" label="Java">

**Maven:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>st.orm</groupId>
            <artifactId>storm-bom</artifactId>
            <version>1.9.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.9.1"))
}
```

</TabItem>
</Tabs>

## Add the Core Dependency

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.9.1"))

    implementation("st.orm:storm-kotlin")
}
```

**Maven:**

```xml
<dependencies>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-kotlin</artifactId>
    </dependency>
</dependencies>
```

</TabItem>
<TabItem value="java" label="Java">

**Maven:**

```xml
<dependencies>
    <dependency>
        <groupId>st.orm</groupId>
        <artifactId>storm-java21</artifactId>
    </dependency>
</dependencies>
```

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("st.orm:storm-bom:1.9.1"))

    implementation("st.orm:storm-java21")
}
```

</TabItem>
</Tabs>

### Enable Preview Features (Java Only)

The Java API uses String Templates (JEP 430), a preview feature in JDK 21+. You must add `--enable-preview` to the compiler configuration:

**Maven:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>21</release>
        <compilerArgs>
            <arg>--enable-preview</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

**Gradle (Kotlin DSL):**

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
```

## Optional Modules

Storm is modular. Add only what you need.

### Static Metamodel

The metamodel generates companion classes (e.g., `User_`, `City_`) at compile time, enabling type-safe field references in queries. While optional, it is strongly recommended for projects that use Storm's query builder or repository predicates. See [Metamodel](metamodel.md) for details.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

Kotlin projects can generate the metamodel using either KSP (for Gradle) or kapt (for Maven). Both produce the same metamodel classes.

**Gradle (Kotlin DSL) with KSP:**

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

dependencies {
    ksp("st.orm:storm-metamodel-ksp:1.9.1")
}
```

**Maven with kapt:**

```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>kapt</id>
            <goals><goal>kapt</goal></goals>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>st.orm</groupId>
                        <artifactId>storm-metamodel-processor</artifactId>
                        <version>1.9.1</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </execution>
    </executions>
</plugin>
```

</TabItem>
<TabItem value="java" label="Java">

**Maven (annotation processor):**

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-metamodel-processor</artifactId>
    <scope>provided</scope>
</dependency>
```

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    annotationProcessor("st.orm:storm-metamodel-processor:1.9.1")
}
```

</TabItem>
</Tabs>

### Database Dialects

Storm works with any JDBC-compatible database out of the box. Dialect modules provide database-specific optimizations (e.g., native upsert syntax, tuple comparisons). Add the one that matches your database as a runtime dependency:

| Module | Database |
|--------|----------|
| `storm-postgresql` | PostgreSQL |
| `storm-mysql` | MySQL |
| `storm-mariadb` | MariaDB |
| `storm-oracle` | Oracle |
| `storm-mssqlserver` | SQL Server |

```kotlin
runtimeOnly("st.orm:storm-postgresql")
```

See [Database Dialects](dialects.md) for what each dialect provides.

### Spring Boot Integration

For Spring Boot applications, use the starter modules instead of the base modules. The starters auto-configure the `ORMTemplate` bean, enable repository scanning, and integrate with Spring's transaction management. See [Spring Integration](spring-integration.md) for full setup details.

<Tabs groupId="language">
<TabItem value="kotlin" label="Kotlin" default>

```kotlin
implementation("st.orm:storm-kotlin-spring-boot-starter")
```

</TabItem>
<TabItem value="java" label="Java">

```xml
<dependency>
    <groupId>st.orm</groupId>
    <artifactId>storm-spring-boot-starter</artifactId>
</dependency>
```

</TabItem>
</Tabs>

### JSON Support

Storm supports storing and reading JSON-typed columns. Pick the module that matches your serialization library:

| Module | Library |
|--------|---------|
| `storm-jackson2` | Jackson 2.17+ (Spring Boot 3.x) |
| `storm-jackson3` | Jackson 3.0+ (Spring Boot 4+) |
| `storm-kotlinx-serialization` | Kotlinx Serialization |

See [JSON Support](json.md) for usage details.

## Module Overview

The following diagram shows how Storm's modules relate to each other. You only need the modules relevant to your language and integration choices.

```
storm-foundation (base interfaces)
└── storm-kotlin / storm-java21 (your primary dependency)
    ├── storm-kotlin-spring / storm-spring (Spring Framework)
    │   └── storm-kotlin-spring-boot-starter / storm-spring-boot-starter
    ├── dialect modules (postgresql, mysql, mariadb, oracle, mssqlserver)
    └── JSON modules (jackson2, jackson3, kotlinx-serialization)
```

## Next Steps

With Storm installed, you are ready to define your first entity and run your first query:

- [First Entity](first-entity.md) -- define an entity, create an ORM template, insert and fetch a record
- [First Query](first-query.md) -- custom queries, repositories, and type-safe filtering
