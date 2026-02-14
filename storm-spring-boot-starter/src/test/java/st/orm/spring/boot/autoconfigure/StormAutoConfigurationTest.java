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
package st.orm.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import st.orm.EntityCallback;
import st.orm.spring.RepositoryBeanFactoryPostProcessor;
import st.orm.template.ORMTemplate;

class StormAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    StormAutoConfiguration.class,
                    StormRepositoryAutoConfiguration.class
            ));

    @Test
    void ormTemplateBeanCreatedWhenDataSourcePresent() {
        // StormAutoConfiguration is conditional on a DataSource bean. When a DataSource is available,
        // it should auto-configure exactly one ORMTemplate bean.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ORMTemplate.class);
                });
    }

    @Test
    void ormTemplateBeanNotCreatedWithoutDataSource() {
        // Without a DataSource in the context, StormAutoConfiguration should not create
        // an ORMTemplate bean (the @ConditionalOnBean(DataSource.class) condition should fail).
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StormAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ORMTemplate.class);
                });
    }

    @Test
    void userDefinedOrmTemplateTakesPrecedence() {
        // StormAutoConfiguration uses @ConditionalOnMissingBean(ORMTemplate.class), so a user-defined
        // ORMTemplate bean should take precedence over the auto-configured one.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .withUserConfiguration(CustomOrmTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ORMTemplate.class);
                    assertThat(context).getBean(ORMTemplate.class)
                            .isSameAs(context.getBean("customOrmTemplate"));
                });
    }

    @Test
    void repositoryBeanFactoryPostProcessorAutoConfigured() {
        // StormRepositoryAutoConfiguration should register an AutoConfiguredRepositoryBeanFactoryPostProcessor
        // that scans for repository interfaces and registers them as Spring beans.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RepositoryBeanFactoryPostProcessor.class);
                    assertThat(context).getBean(RepositoryBeanFactoryPostProcessor.class)
                            .isInstanceOf(AutoConfiguredRepositoryBeanFactoryPostProcessor.class);
                });
    }

    @Test
    void entityCallbackBeanAutoDetected() {
        // When a user defines an EntityCallback bean, StormAutoConfiguration should detect it
        // and wire it into the ORMTemplate for entity lifecycle callbacks.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .withUserConfiguration(EntityCallbackConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ORMTemplate.class);
                    assertThat(context).hasSingleBean(EntityCallback.class);
                });
    }

    @Test
    void noEntityCallbackByDefault() {
        // Without user-defined EntityCallback beans, none should be present in the context.
        // The auto-configuration does not register a default EntityCallback.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ORMTemplate.class);
                    assertThat(context).doesNotHaveBean(EntityCallback.class);
                });
    }

    @Test
    void stormPropertiesAppliedToOrmTemplate() {
        // Storm properties under the "storm.*" prefix should be bound to StormProperties and applied
        // to the ORMTemplate. Each property value set here should be reflected in the bound bean.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "storm.ansi-escaping=false",
                        "storm.update.default-mode=ENTITY",
                        "storm.update.dirty-check=INSTANCE",
                        "storm.update.max-shapes=5",
                        "storm.entity-cache.retention=light",
                        "storm.template-cache.size=100",
                        "storm.validation.skip=false",
                        "storm.validation.warnings-only=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ORMTemplate.class);
                    assertThat(context).hasSingleBean(StormProperties.class);
                    StormProperties props = context.getBean(StormProperties.class);
                    assertThat(props.getAnsiEscaping()).isFalse();
                    assertThat(props.getUpdate().getDefaultMode()).isEqualTo("ENTITY");
                    assertThat(props.getUpdate().getDirtyCheck()).isEqualTo("INSTANCE");
                    assertThat(props.getUpdate().getMaxShapes()).isEqualTo(5);
                    assertThat(props.getEntityCache().getRetention()).isEqualTo("light");
                    assertThat(props.getTemplateCache().getSize()).isEqualTo(100);
                    assertThat(props.getValidation().getSkip()).isFalse();
                    assertThat(props.getValidation().getWarningsOnly()).isTrue();
                });
    }

    @Test
    void userDefinedRepositoryBeanFactoryPostProcessorTakesPrecedence() {
        // StormRepositoryAutoConfiguration uses @ConditionalOnMissingBean, so a user-defined
        // RepositoryBeanFactoryPostProcessor should prevent the auto-configured one from being created.
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver"
                )
                .withUserConfiguration(CustomRepositoryPostProcessorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RepositoryBeanFactoryPostProcessor.class);
                    assertThat(context).doesNotHaveBean(AutoConfiguredRepositoryBeanFactoryPostProcessor.class);
                });
    }

    @Configuration
    static class EntityCallbackConfig {
        @Bean
        public EntityCallback<?> entityCallback() {
            return new EntityCallback<>() {};
        }
    }

    @Configuration
    static class CustomOrmTemplateConfig {
        @Bean
        public ORMTemplate customOrmTemplate(DataSource dataSource) {
            return ORMTemplate.of(dataSource);
        }
    }

    @Configuration
    static class CustomRepositoryPostProcessorConfig {
        @Bean
        public static RepositoryBeanFactoryPostProcessor repositoryBeanFactoryPostProcessor() {
            return new RepositoryBeanFactoryPostProcessor() {
                @Override
                public String[] getRepositoryBasePackages() {
                    return new String[] { "com.example" };
                }
            };
        }
    }
}
