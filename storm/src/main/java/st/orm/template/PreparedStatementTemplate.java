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
import st.orm.BindVars;
import st.orm.Templates;
import st.orm.spi.Provider;
import st.orm.template.SqlTemplate.AliasResolveStrategy;
import st.orm.template.impl.PreparedStatementTemplateImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Predicate;

public interface PreparedStatementTemplate extends Templates, StringTemplate.Processor<PreparedStatement, SQLException> {

    static PreparedStatementTemplate of(@Nonnull DataSource dataSource) {
        return new PreparedStatementTemplateImpl(dataSource);
    }

    static PreparedStatementTemplate of(@Nonnull Connection connection) {
        return new PreparedStatementTemplateImpl(connection);
    }

    static ORMRepositoryTemplate ORM(@Nonnull DataSource dataSource) {
        return new PreparedStatementTemplateImpl(dataSource).toORM();
    }

    static ORMRepositoryTemplate ORM(@Nonnull Connection connection) {
        return new PreparedStatementTemplateImpl(connection).toORM();
    }

    PreparedStatementTemplate withTableNameResolver(@Nonnull TableNameResolver tableNameResolver);

    PreparedStatementTemplate withColumnNameResolver(@Nonnull ColumnNameResolver columnNameResolver);

    PreparedStatementTemplate withForeignKeyResolver(@Nonnull ForeignKeyResolver foreignKeyResolver);

    PreparedStatementTemplate withAliasResolveStrategy(@Nonnull AliasResolveStrategy aliasResolveStrategy);

    PreparedStatementTemplate withProviderFilter(@Nonnull Predicate<Provider> providerFilter);

    ORMRepositoryTemplate toORM();

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     */
    BindVars createBindVars();

}
