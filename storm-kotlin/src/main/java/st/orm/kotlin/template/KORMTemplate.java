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
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.KTemplates;
import st.orm.kotlin.repository.KEntityModel;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.repository.Entity;
import st.orm.template.ORMTemplate;
import st.orm.template.TemplateFunction;

public interface KORMTemplate extends KTemplates, StringTemplate.Processor<KQuery, PersistenceException> {

    static KORMTemplate from(ORMTemplate ormTemplate) {
        return new KORMTemplateImpl(ormTemplate);
    }

    KQuery template(@Nonnull TemplateFunction function);

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see st.orm.PreparedQuery#addBatch(Record)
     */
    BindVars createBindVars();

    <T extends Entity<ID>, ID> KEntityModel<T, ID> model(@Nonnull KClass<T> type);

    <T extends Record> KQueryBuilder<T, T, Object> query(@Nonnull KClass<T> recordType);
}
