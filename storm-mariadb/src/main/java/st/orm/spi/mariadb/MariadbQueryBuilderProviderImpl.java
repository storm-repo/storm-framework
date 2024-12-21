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
package st.orm.spi.mariadb;

import jakarta.annotation.Nonnull;
import st.orm.spi.EntityRepositoryProvider;
import st.orm.spi.Orderable.Before;
import st.orm.spi.QueryBuilderProvider;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.impl.DeleteBuilderImpl;
import st.orm.template.impl.SelectBuilderImpl;

/**
 * Implementation of {@link EntityRepositoryProvider} for MariaDB.
 */
@Before(MariadbQueryBuilderProviderImpl.class)
public class MariadbQueryBuilderProviderImpl implements QueryBuilderProvider {

    @Override
    public <T extends Record, R, ID> QueryBuilder<T, R, ID> selectFrom(@Nonnull QueryTemplate queryTemplate,
                                                                       @Nonnull Class<T> fromType,
                                                                       @Nonnull Class<R> selectType,
                                                                       @Nonnull StringTemplate template,
                                                                       boolean subquery) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, template, subquery);
    }

    @Override
    public <T extends Record, ID> QueryBuilder<T, ?, ID> deleteFrom(@Nonnull QueryTemplate queryTemplate,
                                                                    @Nonnull Class<T> fromType) {
        return new DeleteBuilderImpl<>(queryTemplate, fromType) {
            @Override
            protected boolean supportsJoin() {
                return true;
            }
        };
    }
}
