/*
 * Copyright 2024 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.template

import jakarta.persistence.EntityManager
import st.orm.mapping.TemplateDecorator
import st.orm.core.template.Templates
import st.orm.repository.RepositoryLookup
import st.orm.template.impl.ORMTemplateImpl
import java.sql.Connection
import javax.sql.DataSource

/**
 * The primary entry point for Storm's ORM functionality in Kotlin, combining SQL template query construction with
 * repository access.
 *
 * `ORMTemplate` extends both [QueryTemplate] (for constructing and executing SQL queries) and
 * [RepositoryLookup] (for obtaining type-safe [st.orm.repository.EntityRepository] and
 * [st.orm.repository.ProjectionRepository] instances). It is the central interface from which all database
 * operations originate.
 *
 * Instances can be created using the companion object factory methods [of], or via the Kotlin extension
 * properties [EntityManager.orm], [DataSource.orm], and [Connection.orm].
 *
 * ## Example
 * ```kotlin
 * val orm = dataSource.orm
 *
 * // Repository-based access
 * val users = orm.entity(User::class)
 * val user = users.findById(42)
 *
 * // Template-based query
 * val result = orm.query("SELECT ${User::class} FROM ${User::class} WHERE ${User_.name} = ${"Alice"}")
 *     .getResultList(User::class)
 * ```
 *
 * @see st.orm.repository.EntityRepository
 * @see st.orm.repository.ProjectionRepository
 */
interface ORMTemplate : QueryTemplate, RepositoryLookup {

    companion object {
        /**
         * Returns an [ORMTemplate] for use with JPA.
         *
         * This method creates an ORM repository template using the provided [EntityManager].
         * It allows you to perform database operations using JPA in a type-safe manner.
         *
         * Example usage:
         * ```
         * EntityManager entityManager = ...;
         * ORMTemplate orm = ORMTemplate.of(entityManager);
         * List<MyTable> otherTables = orm.query(RAW."""
         * SELECT \{MyTable.class}
         * FROM \{MyTable.class}
         * WHERE \{MyTable_.name} = \{"ABC"}""")
         * .getResultList(MyTable.class);
         * ```
         *
         * @param entityManager the [EntityManager] to use for database operations; must not be `null`.
         * @return an [ORMTemplate] configured for use with JPA.
         */
        fun of(entityManager: EntityManager): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(entityManager))
        }

        /**
         * Returns an [ORMTemplate] for use with JDBC.
         *
         * This method creates an ORM repository template using the provided [DataSource].
         * It allows you to perform database operations using JDBC in a type-safe manner.
         *
         * Example usage:
         * ```
         * DataSource dataSource = ...;
         * ORMTemplate orm = ORMTemplate.of(dataSource);
         * List<MyTable> otherTables = orm.query(RAW."""
         * SELECT \{MyTable.class}
         * FROM \{MyTable.class}
         * WHERE \{MyTable_.name} = \{"ABC"}""")
         * .getResultList(MyTable.class);
         * ```
         *
         * @param dataSource the [DataSource] to use for database operations; must not be `null`.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        fun of(dataSource: DataSource): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource))
        }

        /**
         * Returns an [ORMTemplate] for use with JDBC.
         *
         * This method creates an ORM repository template using the provided [Connection].
         * It allows you to perform database operations using JDBC in a type-safe manner.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * Example usage:
         * ```
         * try (Connection connection = ...) {
         * ORMTemplate orm = ORMTemplate.of(connection);
         * List<MyTable> otherTables = orm.query(RAW."""
         * SELECT \{MyTable.class}
         * FROM \{MyTable.class}
         * WHERE \{MyTable_.name} = \{"ABC"}""")
         * .getResultList(MyTable.class)
         * }
         * ```
         *
         * @param connection the [Connection] to use for database operations; must not be `null`.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        fun of(connection: Connection): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection))
        }

        /**
         * Returns an [ORMTemplate] for use with JPA, with a custom template decorator.
         *
         * This method creates an ORM repository template using the provided [EntityManager] and applies
         * the specified decorator to customize template processing behavior.
         *
         * @param entityManager the [EntityManager] to use for database operations.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JPA.
         */
        fun of(
            entityManager: EntityManager,
            decorator: (TemplateDecorator) -> TemplateDecorator
        ): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(entityManager, decorator))
        }

        /**
         * Returns an [ORMTemplate] for use with JDBC, with a custom template decorator.
         *
         * This method creates an ORM repository template using the provided [DataSource] and applies
         * the specified decorator to customize template processing behavior.
         *
         * @param dataSource the [DataSource] to use for database operations.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        fun of(
            dataSource: DataSource,
            decorator: (TemplateDecorator) -> TemplateDecorator
        ): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, decorator))
        }

        /**
         * Returns an [ORMTemplate] for use with JDBC, with a custom template decorator.
         *
         * This method creates an ORM repository template using the provided [Connection] and applies
         * the specified decorator to customize template processing behavior.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * @param connection the [Connection] to use for database operations.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        fun of(
            connection: Connection,
            decorator: (TemplateDecorator) -> TemplateDecorator
        ): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection, decorator))
        }
    }    
}

val EntityManager.orm: ORMTemplate
    get() = ORMTemplate.of(this)

val DataSource.orm: ORMTemplate
    get() = ORMTemplate.of(this)

val Connection.orm: ORMTemplate
    get() = ORMTemplate.of(this)

fun EntityManager.orm(decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate =
    ORMTemplate.of(this, decorator)

fun DataSource.orm(decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate =
    ORMTemplate.of(this, decorator)

fun Connection.orm(decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate =
    ORMTemplate.of(this, decorator)
