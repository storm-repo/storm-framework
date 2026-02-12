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
package st.orm.core.template;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import st.orm.EntityCallback;
import st.orm.PersistenceException;
import st.orm.StormConfig;
import st.orm.mapping.TemplateDecorator;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.ProjectionRepository;
import st.orm.core.repository.RepositoryLookup;
import st.orm.core.template.impl.JpaTemplateImpl;
import st.orm.core.template.impl.PreparedStatementTemplateImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
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
     * Returns the configuration associated with this template.
     *
     * @return the Storm configuration; never {@code null}.
     * @since 1.9
     */
    StormConfig config();

    /**
     * Returns the entity callbacks associated with this template.
     *
     * @return an unmodifiable list of entity callbacks; never {@code null}.
     * @since 1.9
     */
    default List<EntityCallback<?>> entityCallbacks() {
        return List.of();
    }

    /**
     * Returns a new {@code ORMTemplate} with the specified entity callback added.
     *
     * <p>The returned template shares the same underlying connection and configuration, but applies the given
     * callback to entity lifecycle operations (insert, update, delete) performed through its repositories. The
     * callback is only invoked for entities matching its type parameter. Multiple callbacks can be registered by
     * chaining calls to this method.</p>
     *
     * @param callback the entity callback to add; must not be {@code null}.
     * @return a new {@code ORMTemplate} with the callback added.
     * @since 1.9
     */
    default ORMTemplate withEntityCallback(@Nonnull EntityCallback<?> callback) {
        return withEntityCallbacks(List.of(callback));
    }

    /**
     * Returns a new {@code ORMTemplate} with the specified entity callbacks added.
     *
     * <p>The returned template shares the same underlying connection and configuration, but applies the given
     * callbacks to entity lifecycle operations (insert, update, delete) performed through its repositories. Each
     * callback is only invoked for entities matching its type parameter.</p>
     *
     * @param callbacks the entity callbacks to add; must not be {@code null}.
     * @return a new {@code ORMTemplate} with the callbacks added.
     * @since 1.9
     */
    ORMTemplate withEntityCallbacks(@Nonnull List<EntityCallback<?>> callbacks);

    /**
     * Returns an {@link ORMTemplate} for use with JPA.
     *
     * <p>This method creates an ORM repository template using the provided {@link EntityManager}.
     * It allows you to perform database operations using JPA in a type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * EntityManager entityManager = ...;
     * ORMTemplate orm = Templates.ORM(entityManager);
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
        return new JpaTemplateImpl(entityManager).toORM();
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
     * ORMTemplate orm = Templates.ORM(entityManager);
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
        var template = new JpaTemplateImpl(entityManager);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof JpaTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((JpaTemplateImpl) decorated).toORM();
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
     * ORMTemplate orm = Templates.ORM(dataSource);
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
        return new PreparedStatementTemplateImpl(dataSource).toORM();
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
     * ORMTemplate orm = Templates.ORM(dataSource);
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
        var template = new PreparedStatementTemplateImpl(dataSource);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof PreparedStatementTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((PreparedStatementTemplateImpl) decorated).toORM();
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
     *     ORMTemplate orm = Templates.ORM(connection);
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
        return new PreparedStatementTemplateImpl(connection).toORM();
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
     *     ORMTemplate orm = Templates.ORM(connection);
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
    static ORMTemplate of(@Nonnull Connection connection,
                          @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        var template = new PreparedStatementTemplateImpl(connection);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof PreparedStatementTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((PreparedStatementTemplateImpl) decorated).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JPA, configured with the provided {@link StormConfig}.
     *
     * @param entityManager the {@link EntityManager} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JPA.
     */
    static ORMTemplate of(@Nonnull EntityManager entityManager, @Nonnull StormConfig config) {
        return new JpaTemplateImpl(entityManager, config).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JPA, configured with the provided {@link StormConfig} and a custom
     * template decorator.
     *
     * @param entityManager the {@link EntityManager} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @param decorator a function that transforms the {@link TemplateDecorator} to customize template processing.
     * @return an {@link ORMTemplate} configured for use with JPA.
     */
    static ORMTemplate of(@Nonnull EntityManager entityManager, @Nonnull StormConfig config,
                          @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        var template = new JpaTemplateImpl(entityManager, config);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof JpaTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((JpaTemplateImpl) decorated).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC, configured with the provided {@link StormConfig}.
     *
     * <p>The provided configuration is applied to the template instance, not as a process-wide default.</p>
     *
     * @param dataSource the {@link DataSource} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull DataSource dataSource, @Nonnull StormConfig config) {
        return new PreparedStatementTemplateImpl(dataSource, config).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC, configured with the provided {@link StormConfig} and a custom
     * template decorator.
     *
     * @param dataSource the {@link DataSource} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @param decorator a function that transforms the {@link TemplateDecorator} to customize template processing.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull DataSource dataSource, @Nonnull StormConfig config,
                          @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        var template = new PreparedStatementTemplateImpl(dataSource, config);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof PreparedStatementTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((PreparedStatementTemplateImpl) decorated).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC, configured with the provided {@link StormConfig}.
     *
     * <p>The provided configuration is applied to the template instance, not as a process-wide default.</p>
     *
     * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.</p>
     *
     * @param connection the {@link Connection} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull Connection connection, @Nonnull StormConfig config) {
        return new PreparedStatementTemplateImpl(connection, config).toORM();
    }

    /**
     * Returns an {@link ORMTemplate} for use with JDBC, configured with the provided {@link StormConfig} and a custom
     * template decorator.
     *
     * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.</p>
     *
     * @param connection the {@link Connection} to use for database operations; must not be {@code null}.
     * @param config the Storm configuration to apply; must not be {@code null}.
     * @param decorator a function that transforms the {@link TemplateDecorator} to customize template processing.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static ORMTemplate of(@Nonnull Connection connection, @Nonnull StormConfig config,
                          @Nonnull UnaryOperator<TemplateDecorator> decorator) {
        var template = new PreparedStatementTemplateImpl(connection, config);
        var decorated = decorator.apply(template);
        if (!(decorated instanceof PreparedStatementTemplateImpl)) {
            throw new PersistenceException("Decorator must return the same template type.");
        }
        return ((PreparedStatementTemplateImpl) decorated).toORM();
    }
}
