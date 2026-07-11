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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import st.orm.spring.repository.VisitRepository;
import st.orm.template.ORMTemplate;

/**
 * Verifies that {@link EnableStormRepositories} scans and registers repository beans, mirroring
 * annotations like {@code @EnableJpaRepositories}.
 */
class EnableStormRepositoriesTest {

    @Configuration
    static class DatabaseConfiguration {
        @Bean
        public DataSource dataSource() {
            DataSource dataSource = DataSourceBuilder.create()
                    .url("jdbc:h2:mem:enablerepositoriestest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                    .username("sa")
                    .password("")
                    .driverClassName("org.h2.Driver")
                    .build();
            new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
            return dataSource;
        }

        @Bean
        public ORMTemplate ormTemplate(DataSource dataSource) {
            return ORMTemplate.of(dataSource);
        }
    }

    @Configuration
    @EnableStormRepositories(basePackages = "st.orm.spring.repository", ormTemplateBeanName = "ormTemplate")
    static class ExplicitPackagesConfiguration {
    }

    @Configuration
    @EnableStormRepositories(basePackageClasses = VisitRepository.class, ormTemplateBeanName = "ormTemplate")
    static class PackageClassesConfiguration {
    }

    @Configuration
    @EnableStormRepositories(
            basePackages = "st.orm.spring.repository",
            ormTemplateBeanName = "ormTemplate",
            repositoryPrefix = "acme")
    static class PrefixedConfiguration {
    }

    @Test
    void explicitBasePackagesRegisterRepositories() {
        try (var context = new AnnotationConfigApplicationContext(
                DatabaseConfiguration.class, ExplicitPackagesConfiguration.class)) {
            VisitRepository visitRepository = context.getBean(VisitRepository.class);
            assertEquals(14, visitRepository.count());
            assertInstanceOf(RepositoryBeanFactoryPostProcessor.class,
                    context.getBean("stormRepositoriesPostProcessor"));
        }
    }

    @Test
    void basePackageClassesRegisterRepositories() {
        try (var context = new AnnotationConfigApplicationContext(
                DatabaseConfiguration.class, PackageClassesConfiguration.class)) {
            assertTrue(context.containsBean("VisitRepository"));
        }
    }

    @Test
    void repositoryPrefixNamesTheBeans() {
        try (var context = new AnnotationConfigApplicationContext(
                DatabaseConfiguration.class, PrefixedConfiguration.class)) {
            assertTrue(context.containsBean("acmeVisitRepository"));
            VisitRepository visitRepository = context.getBean(VisitRepository.class);
            assertEquals(14, visitRepository.count());
        }
    }

    @Test
    void withoutPackagesTheAnnotatedClassPackageIsScanned() {
        // The test configuration lives in st.orm.spring, so the scan covers st.orm.spring.repository too.
        try (var context = new AnnotationConfigApplicationContext(
                DatabaseConfiguration.class, DefaultPackageConfiguration.class)) {
            assertTrue(context.containsBean("VisitRepository"));
        }
    }

    @Configuration
    @EnableStormRepositories(ormTemplateBeanName = "ormTemplate")
    static class DefaultPackageConfiguration {
    }
}
