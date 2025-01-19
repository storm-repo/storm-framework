/*
 * Copyright 2024 the original author or authors.
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
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.template.impl.BindVarsHandle;
import st.orm.template.impl.SqlTemplateImpl;

import java.util.List;

/**
 * A template processor for generating SQL queries.
 */
public interface SqlTemplate {

    sealed interface Parameter {
        Object dbValue();
    }
    record NamedParameter(@Nonnull String name, @Nullable Object dbValue) implements Parameter {}
    record PositionalParameter(int position, @Nullable Object dbValue) implements Parameter {}

    interface BindVariables {
        BindVarsHandle getHandle();

        void setBatchListener(@Nonnull BatchListener listener);
    }

    interface BatchListener {
        void onBatch(@Nonnull List<PositionalParameter> parameters);
    }

    /**
     * The default SQL template that is optimized for prepared statements.
     */
    SqlTemplate PS = new SqlTemplateImpl(
            true,
            true,
            true);

    /**
     * The default SQL template that is optimized for JPA.
     */
    SqlTemplate JPA = new SqlTemplateImpl(
            false,
            false, // False, as JPA does not support collection expansion for positional parameters.
            true);  // Note that JPA does not support nested records.

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     */
    BindVars createBindVars();

    /**
     * Returns {@code true} if the template only support positional parameters, {@code false} otherwise.
     *
     * @return {@code true} if the template only support positional parameters, {@code false} otherwise.
     */
    boolean positionalOnly();

    /**
     * Returns {@code true} if collection parameters must be expanded as multiple (positional) parameters,
     * {@code false} otherwise.
     *
     * @return {@code true} if the template expands collection parameters, {@code false} otherwise.
     */
    boolean expandCollection();

    /**
     * Returns a new SQL template with the specified table name resolver.
     *
     * @param resolver the table name resolver.
     * @return a new SQL template.
     */
    SqlTemplate withTableNameResolver(TableNameResolver resolver);

    /**
     * Returns the table name resolver that is used by this template.
     *
     * @return the table name resolver that is used by this template.
     */
    TableNameResolver tableNameResolver();

    /**
     * Returns a new SQL template with the specified table alias resolver.
     *
     * @param resolver the table alias resolver.
     * @return a new SQL template.
     */
    SqlTemplate withTableAliasResolver(TableAliasResolver resolver);

    /**
     * Returns the table alias resolver that is used by this template.
     *
     * @return the table alias resolver that is used by this template.
     */
    TableAliasResolver tableAliasResolver();

    /**
     * Returns a new SQL template with the specified column name resolver.
     *
     * @param resolver the column name resolver.
     * @return a new SQL template.
     */
    SqlTemplate withColumnNameResolver(ColumnNameResolver resolver);

    /**
     * Returns the column name resolver that is used by this template.
     *
     * @return the column name resolver that is used by this template.
     */
    ColumnNameResolver columnNameResolver();

    /**
     * Returns a new SQL template with the specified foreign key resolver.
     *
     * @param resolver the foreign key resolver.
     * @return a new SQL template.
     */
    SqlTemplate withForeignKeyResolver(ForeignKeyResolver resolver);

    /**
     * Returns the foreign key resolver that is used by this template.
     *
     * @return the foreign key resolver that is used by this template.
     */
    ForeignKeyResolver foreignKeyResolver();

    /**
     * Returns a new SQL template with support for records enabled or disabled.
     *
     * @param supportRecords {@code true} if the template should support records, {@code false} otherwise.
     * @return a new SQL template.
     */
    SqlTemplate withSupportRecords(boolean supportRecords);

    /**
     * Returns {@code true} if the template supports tables represented as records, {@code false} otherwise.
     *
     * @return {@code true} if the template supports records, {@code false} otherwise.
     */
    boolean supportRecords();

    /**
     * Processes the specified {@code template} and returns the resulting SQL and parameters.
     *
     * @param template the string template to process.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    Sql process(@Nonnull StringTemplate template) throws SqlTemplateException;
}
