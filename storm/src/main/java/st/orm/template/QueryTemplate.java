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
import st.orm.Lazy;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.repository.Model;

import static java.lang.StringTemplate.RAW;

public interface QueryTemplate extends SubqueryTemplate {

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery#addBatch(Record)
     */
    BindVars createBindVars();

    <T extends Record, ID> Lazy<T, ID> lazy(@Nonnull Class<T> type, @Nullable ID pk);

    <T extends Record, ID> Model<T, ID> model(@Nonnull Class<T> type);

    default <T extends Record> QueryBuilder<T, T, ?> selectFrom(@Nonnull Class<T> fromType) {
        return selectFrom(fromType, fromType);
    }

    default <T extends Record, R extends Record> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                                                  @Nonnull Class<R> selectType) {
        return selectFrom(fromType, selectType, RAW."\{selectType}");
    }

    <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                           @Nonnull Class<R> selectType,
                                                           @Nonnull StringTemplate template);

    Query query(@Nonnull StringTemplate template);
}
