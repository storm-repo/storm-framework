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
package st.orm.kotlin.template;

import st.orm.kotlin.KTemplates;
import st.orm.kotlin.repository.KRepositoryLookup;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.template.ORMTemplate;

/**
 * <p>The {@code KORMTemplate} is the primary interface that extends the {@code KQueryTemplate} and
 * {@code KRepositoryLooking} interfaces, providing access to both the SQL Template engine and ORM logic.
 *
 * @see KTemplates
 * @see EntityRepository
 * @see ProjectionRepository
 */
public interface KORMTemplate extends KQueryTemplate, KRepositoryLookup {

    static KORMTemplate from(ORMTemplate orm) {
        return new KORMTemplateImpl(orm);
    }

    /**
     * Exposes the underlying ORMTemplate delegate.
     *
     * @return the wrapped ORMTemplate.
     */
    ORMTemplate unwrap();
}
