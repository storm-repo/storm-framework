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

import jakarta.annotation.Nonnull;
import java.util.Set;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.Repository;
import st.orm.template.ORMTemplate;

/**
 * BeanFactoryPostProcessor that scans base packages for Repository interfaces and registers them as beans.
 *
 * <p>The scanning and registration engine lives in {@link AbstractRepositoryBeanFactoryPostProcessor}; this
 * class binds it to the Java API's repository types.</p>
 */
@Component
public class RepositoryBeanFactoryPostProcessor extends AbstractRepositoryBeanFactoryPostProcessor {

    @Override
    protected Class<?> getRepositoryType() {
        return Repository.class;
    }

    @Override
    protected Set<Class<?>> getExcludedRepositoryTypes() {
        return Set.of(Repository.class, EntityRepository.class, ProjectionRepository.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object createRepository(@Nonnull ConfigurableListableBeanFactory beanFactory,
                                      @Nonnull Class<?> repositoryType) {
        return getBeanORMTemplate(beanFactory).repository((Class<Repository>) repositoryType);
    }

    private ORMTemplate getBeanORMTemplate(ConfigurableListableBeanFactory beanFactory) {
        String beanName = getOrmTemplateBeanName();
        return beanName != null
                ? beanFactory.getBean(beanName, ORMTemplate.class)
                : beanFactory.getBean(ORMTemplate.class);
    }
}
