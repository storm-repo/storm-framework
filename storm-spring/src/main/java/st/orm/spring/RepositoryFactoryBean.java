/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.spring;

import org.jspecify.annotations.Nullable;
import st.orm.repository.Repository;
import st.orm.template.ORMTemplate;

/**
 * FactoryBean that produces a Storm repository through the Java API's template.
 *
 * @since 1.13
 */
public class RepositoryFactoryBean<R extends Repository> extends AbstractRepositoryFactoryBean<R> {

    public RepositoryFactoryBean(Class<R> repositoryType, @Nullable String ormTemplateBeanName) {
        super(repositoryType, ormTemplateBeanName);
    }

    @Override
    protected R createRepository(Class<R> repositoryType) {
        return getOrmTemplate(ORMTemplate.class).repository(repositoryType);
    }
}
