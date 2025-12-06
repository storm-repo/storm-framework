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
package st.orm.template;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import st.orm.mapping.TemplateDecorator;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.RepositoryLookup;
import st.orm.template.impl.ORMTemplateImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.UnaryOperator;

/**
 * <p>The {@code ORMTemplate} is the primary interface that extends the {@code QueryTemplate} and
 * {@code RepositoryLooking} interfaces, providing access to both the SQL Template engine and ORM logic.
 *
 * @see Templates
 * @see EntityRepository
 * @see ProjectionRepository
 */
public interface ORMTemplate extends QueryTemplate, RepositoryLookup {

    /**
     * Returns an {@link ORMTemplate} for use with JPA.
     *
     * <p>This method creates an ORM repository template using the provided {@link EntityManager}.
     * It allows you to perform database operations using JPA in a type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * EntityManager entityManager = ...;
     * ORMTemplate orm = ORMTemplate.of(entityManager);
     * List<MyTable> otherTables = orm.query(RAW."""
     *         SELECT \{MyTable.class}
     *         FROM \{MyTable.class}
     *         WHERE \{MyTable_.name} = \{"ABC"}""")
     *     .getResultList(MyTable.class);
     * }</pre>
     *
     * @param entityManager the {@link EntityManager} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JPA.
     */
    static ORMTemplate of(@Nonnull EntityManager entityManager) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(entityManager));
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link DataSource}.
     * It allows you to perform database operations using JDBC in a type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * DataSource dataSource = ...;
     * ORMTemplate orm = ORMTemplate.of(dataSource);
     * List<MyTable> otherTables = orm.query(RAW."""
     *         SELECT \{MyTable.class}
     *         FROM \{MyTable.class}
     *         WHERE \{MyTable_.name} = \{"ABC"}""")
     *     .getResultList(MyTable.class);
     * }</pre>
     *
     * @param dataSource the {@link DataSource} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull DataSource dataSource) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource));
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link Connection}.
     * It allows you to perform database operations using JDBC in a type-safe manner.</p>
     *
     * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * try (Connection connection = ...) {
     *     ORMTemplate orm = ORMTemplate.of(connection);
     *     List<MyTable> otherTables = orm.query(RAW."""
     *             SELECT \{MyTable.class}
     *             FROM \{MyTable.class}
     *             WHERE \{MyTable_.name} = \{"ABC"}""")
     *         .getResultList(MyTable.class)
     * }
     * }</pre>
     *
     * @param connection the {@link Connection} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull Connection connection) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection));
    }

    /**
     * Returns an {@link ORMTemplate} for use with JPA.
     *
     * <p>This method creates an ORM repository template using the provided {@link EntityManager}.
     * It allows you to perform database operations using JPA in a type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * EntityManager entityManager = ...;
     * ORMTemplate orm = ORMTemplate.of(entityManager);
     * List<MyTable> otherTables = orm.query(RAW."""
     *         SELECT \{MyTable.class}
     *         FROM \{MyTable.class}
     *         WHERE \{MyTable_.name} = \{"ABC"}""")
     *     .getResultList(MyTable.class);
     * }</pre>
     *
     * @param entityManager the {@link EntityManager} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JPA.
     */
    static ORMTemplate of(@Nonnull EntityManager entityManager, @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(entityManager, decorator));

    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link DataSource}.
     * It allows you to perform database operations using JDBC in a type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * DataSource dataSource = ...;
     * ORMTemplate orm = ORMTemplate.of(dataSource);
     * List<MyTable> otherTables = orm.query(RAW."""
     *         SELECT \{MyTable.class}
     *         FROM \{MyTable.class}
     *         WHERE \{MyTable_.name} = \{"ABC"}""")
     *     .getResultList(MyTable.class);
     * }</pre>
     *
     * @param dataSource the {@link DataSource} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull DataSource dataSource, @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, decorator));
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link Connection}.
     * It allows you to perform database operations using JDBC in a type-safe manner.</p>
     *
     * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.</p>
     *
     * <p>Example usage:
     * <pre>{@code
     * try (Connection connection = ...) {
     *     ORMTemplate orm = ORMTemplate.of(connection);
     *     List<MyTable> otherTables = orm.query(RAW."""
     *             SELECT \{MyTable.class}
     *             FROM \{MyTable.class}
     *             WHERE \{MyTable_.name} = \{"ABC"}""")
     *         .getResultList(MyTable.class)
     * }
     * }</pre>
     *
     * @param connection the {@link Connection} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull Connection connection, @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        return new ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection, decorator));
    }
}
