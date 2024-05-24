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
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.Templates;
import st.orm.repository.Entity;
import st.orm.repository.EntityModel;

public interface ORMTemplate extends Templates, StringTemplate.Processor<Query, PersistenceException> {

    Query template(@Nonnull TemplateFunction function);

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery#addBatch(Record)
     */
    BindVars createBindVars();

    <T extends Entity<ID>, ID> EntityModel<T, ID> model(@Nonnull Class<T> type);

    <T extends Record> QueryBuilder<T, T, Object> query(@Nonnull Class<T> recordType);
}
