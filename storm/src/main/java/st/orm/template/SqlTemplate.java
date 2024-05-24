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
import st.orm.Templates;
import st.orm.template.impl.BindVarsHandle;
import st.orm.template.impl.SqlTemplateImpl;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static st.orm.template.impl.SqlTemplateImpl.CONSUMERS;

/**
 * A template processor for generating SQL queries.
 */
public interface SqlTemplate extends Templates, StringTemplate.Processor<Sql, SqlTemplateException> {

    /**
     * The strategy to use when resolving table aliases.
     */
    enum AliasResolveStrategy {
        /**
         * Resolve all aliases for a table, if multiple exist.
         *
         * <p>This is the safest option to prevent undesired results, but may fail in some cases. If the same table is
         * present multiple types in an entity hierarchy, all the relations will be included in case of a WHERE clause.
         * However, if a single alias is requested, the request will fail because of the ambiguity.</p>
         */
        ALL,

        /**
         * Resolve the first alias for a table, if multiple exist.
         *
         * <p>This option never fails, but may have undesired side effects. If the same table is present multiple times
         * in an entity hierarchy, only the first relation will be included in case of a WHERE clause. This may lead to
         * unexpected results.</p>
         */
        FIRST,

        /**
         * Fail if multiple aliases exist for a table.
         */
        FAIL
    }

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

    SqlTemplate withAliasResolveStrategy(AliasResolveStrategy strategy);

    AliasResolveStrategy aliasResolveStrategy();

    /**
     * Wrap the given action with a consumer that will be called with the generated SQL. This method is primarily
     * intended for debugging purposes.
     */
    static void aroundInvoke(@Nonnull Runnable action, @Nonnull Consumer<Sql> consumer) {
        CONSUMERS.get().add(consumer);
        try {
            action.run();
        } finally {
            CONSUMERS.get().remove(consumer);
        }
    }

    /**
     * Wrap the given action with a consumer that will be called with the generated SQL. This method is primarily
     * intended for debugging purposes.
     */
    static <T> T aroundInvoke(@Nonnull Callable<T> action, @Nonnull Consumer<Sql> consumer) throws Exception {
        CONSUMERS.get().add(consumer);
        try {
            return action.call();
        } finally {
            CONSUMERS.get().remove(consumer);
        }
    }
}
