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
import jakarta.persistence.Query;
import st.orm.spi.Provider;
import st.orm.template.impl.JpaTemplateImpl;

import java.util.function.Predicate;

/**
 * A template backed by JPA.
 */
public interface JpaTemplate {

    /**
     * Creates a new JPA template for the given entity manager.
     *
     * @param entityManager the entity manager.
     * @return the JPA template.
     */
    static JpaTemplate of(@Nonnull EntityManager entityManager) {
        return new JpaTemplateImpl(entityManager);
    }

    /**
     * Creates a new ORM template for the given entity manager.
     *
     * @param entityManager the entity manager.
     * @return the ORM template.
     */
    static QueryTemplate ORM(@Nonnull EntityManager entityManager) {
        return new JpaTemplateImpl(entityManager).toORM();
    }

    /**
     * Returns an ORM template for this JPA template.
     */
    ORMTemplate toORM();

    /**
     * Returns a new JPA template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new JPA template.
     */
    JpaTemplate withTableNameResolver(@Nonnull TableNameResolver tableNameResolver);

    /**
     * Returns a new JPA template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new JPA template.
     */
    JpaTemplate withColumnNameResolver(@Nonnull ColumnNameResolver columnNameResolver);

    /**
     * Returns a new JPA template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new JPA template.
     */
    JpaTemplate withForeignKeyResolver(@Nonnull ForeignKeyResolver foreignKeyResolver);

    /**
     * Returns a new JPA template with the specified table alias resolver.
     *
     * @param tableAliasResolver the table alias resolver.
     * @return a new JPA template.
     */
    JpaTemplate withTableAliasResolver(@Nonnull TableAliasResolver tableAliasResolver);

    /**
     * Returns a new JPA template with the specified provider filter.
     *
     * @param providerFilter the provider filter.
     * @return a new JPA template.
     */
    JpaTemplate withProviderFilter(@Nonnull Predicate<Provider> providerFilter);

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    Query query(@Nonnull StringTemplate template);
}
