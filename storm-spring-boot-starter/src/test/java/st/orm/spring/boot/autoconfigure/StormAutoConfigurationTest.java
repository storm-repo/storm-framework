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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import st.orm.spring.RepositoryBeanFactoryPostProcessor;
import st.orm.template.ORMTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class StormAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    StormAutoConfiguration.class,
                    StormRepositoryAutoConfiguration.class
            ));

    @Test
    void ormTemplateBeanCreatedWhenDataSourcePresent() {
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
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StormAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ORMTemplate.class);
                });
    }

    @Test
    void userDefinedOrmTemplateTakesPrecedence() {
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
    void userDefinedRepositoryBeanFactoryPostProcessorTakesPrecedence() {
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
