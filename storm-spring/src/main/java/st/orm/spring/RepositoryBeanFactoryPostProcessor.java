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

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.Repository;

/**
 * BeanFactoryPostProcessor that scans base packages for Repository interfaces and registers them as beans.
 *
 * <p>The scanning and registration engine lives in {@link AbstractRepositoryBeanFactoryPostProcessor}; this
 * class binds it to the Java API's repository types.</p>
 */
@Component
public class RepositoryBeanFactoryPostProcessor extends AbstractRepositoryBeanFactoryPostProcessor {

    private final String[] basePackages;
    private final String ormTemplateBeanName;
    private final String repositoryPrefix;

    /**
     * Creates a post-processor configured through overrides; subclasses override
     * {@link #getRepositoryBasePackages()} and friends.
     */
    public RepositoryBeanFactoryPostProcessor() {
        this(new String[0], null, "");
    }

    /**
     * Creates a fully configured post-processor, without subclassing.
     *
     * @param basePackages the base packages to scan for repository interfaces.
     * @param ormTemplateBeanName the ORMTemplate bean repositories bind to, or {@code null} for the primary.
     * @param repositoryPrefix prefix for the registered repository bean names; empty for none.
     * @since 1.13
     */
    public RepositoryBeanFactoryPostProcessor(String[] basePackages,
                                              @Nullable String ormTemplateBeanName,
                                              String repositoryPrefix) {
        this.basePackages = basePackages.clone();
        this.ormTemplateBeanName = ormTemplateBeanName;
        this.repositoryPrefix = repositoryPrefix;
    }

    @Override
    public String[] getRepositoryBasePackages() {
        return basePackages.length > 0 ? basePackages.clone() : super.getRepositoryBasePackages();
    }

    @Override
    public String getOrmTemplateBeanName() {
        return ormTemplateBeanName != null ? ormTemplateBeanName : super.getOrmTemplateBeanName();
    }

    @Override
    protected String getRepositoryPrefix() {
        return !repositoryPrefix.isEmpty() ? repositoryPrefix : super.getRepositoryPrefix();
    }

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
    protected Class<? extends AbstractRepositoryFactoryBean<?>> getRepositoryFactoryBeanClass() {
        return (Class<? extends AbstractRepositoryFactoryBean<?>>) (Class<?>) RepositoryFactoryBean.class;
    }
}
