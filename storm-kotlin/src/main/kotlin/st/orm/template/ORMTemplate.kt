/*
 * Copyright 2024 - 2025 the original author or authors.
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
import st.orm.config.TemplateDecorator
import st.orm.core.template.Templates
import st.orm.repository.RepositoryLookup
import st.orm.template.impl.ORMTemplateImpl
import java.sql.Connection
import javax.sql.DataSource

/**
 * The `ORMTemplate` is the primary interface that extends the `QueryTemplate` and
 * `RepositoryLooking` interfaces, providing access to both the SQL Template engine and ORM logic.
 *
 * @see Templates
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
        fun of(
            entityManager: EntityManager,
            decorator: (TemplateDecorator) -> TemplateDecorator
        ): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(entityManager, decorator))
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
        fun of(
            dataSource: DataSource,
            decorator: (TemplateDecorator) -> TemplateDecorator
        ): ORMTemplate {
            return ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, decorator))
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
