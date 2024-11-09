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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.Lazy;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.repository.KModel;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.template.ORMTemplate;
import st.orm.template.TemplateFunction;

import static st.orm.template.TemplateFunction.template;

public interface KORMTemplate {

    static KORMTemplate from(ORMTemplate ormTemplate) {
        return new KORMTemplateImpl(ormTemplate);
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see st.orm.PreparedQuery#addBatch(Record)
     */
    BindVars createBindVars();

    <T extends Record, ID> KModel<T, ID> model(@Nonnull KClass<T> type);

    <T extends Record, ID> Lazy<T, ID> lazy(@Nonnull Class<T> type, @Nullable ID pk);

    <T extends Record> KQueryBuilder<T, T, Object> selectFrom(@Nonnull KClass<T> fromType);

    <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType, KClass<R> selectType);

    <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType, KClass<R> selectType, @Nonnull StringTemplate template);

    default <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType, KClass<R> selectType, @Nonnull TemplateFunction templateFunction) {
        return selectFrom(fromType, selectType, template(templateFunction));
    }

    KQuery query(@Nonnull StringTemplate template);

    default KQuery query(@Nonnull TemplateFunction function) {
        return query(template(function));
    }
}
