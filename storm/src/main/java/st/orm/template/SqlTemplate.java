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

    SqlTemplate PS = new SqlTemplateImpl(
            true,
            true,
            true);
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

    boolean positionalOnly();

    boolean expandCollection();

    SqlTemplate withTableNameResolver(TableNameResolver resolver);

    TableNameResolver tableNameResolver();

    SqlTemplate withTableAliasResolver(TableAliasResolver resolver);

    TableAliasResolver tableAliasResolver();

    SqlTemplate withColumnNameResolver(ColumnNameResolver resolver);

    ColumnNameResolver columnNameResolver();

    SqlTemplate withForeignKeyResolver(ForeignKeyResolver resolver);

    ForeignKeyResolver foreignKeyResolver();

    SqlTemplate withSupportRecords(boolean supportRecords);

    boolean supportRecords();

    Sql process(@Nonnull StringTemplate template) throws SqlTemplateException;
}
