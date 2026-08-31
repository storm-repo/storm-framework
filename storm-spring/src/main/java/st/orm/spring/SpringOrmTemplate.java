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

import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import st.orm.EntityCallback;
import st.orm.StormConfig;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.template.ORMTemplate;
import st.orm.template.impl.BuilderImpl;

/**
 * The canonical plain-Spring (non-Boot) composition of an {@link ORMTemplate} for Java applications: the
 * template participates in Spring-managed transactions, and Storm's programmatic transaction API runs through
 * Spring's transaction managers.
 *
 * {@snippet lang = java:
 * ORMTemplate orm = SpringOrmTemplate.of(dataSource, () -> List.of(transactionManager));
 * }
 *
 * <p>Templates that should share transactions must use the same provider instances, so compose one template
 * per application context (typically as a bean).</p>
 *
 * @since 1.13
 */
public final class SpringOrmTemplate {

    private SpringOrmTemplate() {
    }

    /**
     * Creates a Spring-integrated template with default configuration.
     *
     * @param dataSource the data source backing the template.
     * @param transactionManagers supplies the transaction managers of the owning application context.
     */
    public static ORMTemplate of(DataSource dataSource,
                                 Supplier<List<PlatformTransactionManager>> transactionManagers) {
        return of(dataSource, StormConfig.defaults(), List.of(), transactionManagers);
    }

    /**
     * Creates a Spring-integrated template.
     *
     * @param dataSource the data source backing the template.
     * @param config the Storm configuration.
     * @param entityCallbacks the entity callbacks to apply.
     * @param transactionManagers supplies the transaction managers of the owning application context.
     */
    public static ORMTemplate of(DataSource dataSource,
                                 StormConfig config,
                                 List<EntityCallback<?>> entityCallbacks,
                                 Supplier<List<PlatformTransactionManager>> transactionManagers) {
        var engine = st.orm.core.template.ORMTemplate.builder(dataSource)
                .connectionProvider(new SpringConnectionProvider())
                .transactionTemplateProvider(new SpringTransactionTemplateProvider(transactionManagers));
        return new BuilderImpl(engine)
                .config(config)
                .exceptionMapper(new SpringExceptionMapper(dataSource))
                .build()
                .withEntityCallbacks(entityCallbacks);
    }

    /**
     * Returns a facade builder over an engine builder that carries the Spring-aware transaction integration of the
     * owning application context: the {@code ConnectionProvider} and {@code TransactionTemplateProvider} beans, when
     * the context defines them. Application-facing options and customization apply on the returned builder.
     *
     * @param dataSource the data source backing the template.
     * @param beanFactory the bean factory of the owning application context.
     * @return a facade builder composed with the context's transaction integration.
     * @since 1.14
     */
    public static ORMTemplate.Builder builder(DataSource dataSource, BeanFactory beanFactory) {
        var engine = st.orm.core.template.ORMTemplate.builder(dataSource);
        beanFactory.getBeanProvider(ConnectionProvider.class).ifAvailable(engine::connectionProvider);
        beanFactory.getBeanProvider(TransactionTemplateProvider.class).ifAvailable(engine::transactionTemplateProvider);
        return new BuilderImpl(engine);
    }
}
