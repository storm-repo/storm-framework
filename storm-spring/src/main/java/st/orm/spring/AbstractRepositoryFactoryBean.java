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

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

/**
 * FactoryBean that produces a Storm repository for a given repository interface.
 *
 * <p>The scanning engine registers one bean definition of this type per discovered repository interface,
 * carrying the interface and the optional ORMTemplate bean name as constructor arguments. Unlike an
 * instance-supplier definition, such a definition can be processed by Spring AOT into generated code,
 * which makes scanned repositories work in GraalVM native images.</p>
 *
 * <p>The Java and Kotlin Spring integrations each provide a concrete subclass binding their stack's
 * repository creation call, mirroring the {@link AbstractRepositoryBeanFactoryPostProcessor} adapters.</p>
 *
 * <p>The class is generic in the repository type it produces so the bean definition's target type can
 * bind {@code FactoryBean<R>} to the repository interface. AOT-generated registrations preserve the
 * target type but not definition attributes, so without the generic the produced type would be unknown
 * in a native image and the repository could not be autowired by type.</p>
 *
 * @param <R> the repository interface this factory produces.
 * @since 1.13
 */
public abstract class AbstractRepositoryFactoryBean<R> implements FactoryBean<R>, BeanFactoryAware {

    private final Class<R> repositoryType;
    private final @Nullable String ormTemplateBeanName;
    private BeanFactory beanFactory;

    /**
     * @param repositoryType the repository interface to produce.
     * @param ormTemplateBeanName the ORMTemplate bean the repository binds to, or {@code null} for the
     *                            primary template.
     */
    protected AbstractRepositoryFactoryBean(Class<R> repositoryType,
                                            @Nullable String ormTemplateBeanName) {
        this.repositoryType = requireNonNull(repositoryType, "repositoryType");
        this.ormTemplateBeanName = ormTemplateBeanName;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryType;
    }

    @Override
    public R getObject() {
        return createRepository(repositoryType);
    }

    /**
     * Creates the repository proxy for the given interface through the stack's template API.
     */
    protected abstract R createRepository(Class<R> repositoryType);

    /**
     * Resolves the ORMTemplate the repository binds to: the configured bean name if one was given, the
     * primary template otherwise.
     */
    protected final <T> T getOrmTemplate(Class<T> templateType) {
        return ormTemplateBeanName != null
                ? beanFactory.getBean(ormTemplateBeanName, templateType)
                : beanFactory.getBean(templateType);
    }
}
