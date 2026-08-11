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
package st.orm.spring.boot.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annotation for a Storm test slice, the counterpart of annotations like {@code @DataJpaTest}.
 *
 * <p>Starts only the parts of the application relevant to Storm: the {@code DataSource}, Storm's
 * auto-configuration (template, repository scanning and proxying, transaction integration, schema
 * validation, exception translation), SQL initialization, and Flyway or Liquibase when present. Regular
 * {@code @Component}, {@code @Service} and {@code @Controller} beans are not loaded. Repositories carry the
 * same AOP proxy as in the running application, so {@code @Transactional} and other interface-level advice
 * behave identically under test. A {@code JdbcTemplate} and {@code JdbcClient} are available for verifying
 * database state through a channel independent of the ORM under test.</p>
 *
 * <p>On Spring Boot 3 an embedded in-memory database replaces the application's {@code DataSource} by
 * default; set {@code spring.test.database.replace=none} to run against the configured database instead,
 * such as a Testcontainers-managed one ({@code @ServiceConnection} works with the slice, on a static
 * {@code @Container} field as well as on a container declared as a {@code @Bean}). On Spring Boot 4
 * the replacement activates when the {@code spring-boot-jdbc-test-autoconfigure} artifact is present;
 * without it, point the slice at a database with the {@code properties} attribute. Each test method runs
 * in a transaction that is rolled back afterwards.</p>

 * <p>The slice is exclusion-based rather than annotation-composed where Spring Boot moved classes between
 * releases, so one artifact serves both Spring Boot 3 and Spring Boot 4 applications.</p>
 *
 * {@snippet lang = java:
 * @DataStormTest
 * class VisitRepositoryTest {
 *
 *     @Autowired
 *     private VisitRepository visitRepository;
 *
 *     @Test
 *     void findsVisits() {
 *         assertThat(visitRepository.count()).isEqualTo(14);
 *     }
 * }
 * }
 *
 * <p>Works with both Spring Boot starters: the slice pulls in the starter's auto-configuration classes by
 * name, which are identical for the Java and Kotlin stacks.</p>
 *
 * @since 1.13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BootstrapWith(DataStormTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@Transactional
@ImportAutoConfiguration
public @interface DataStormTest {

    /**
     * Properties in {@code key=value} form to add to the Spring environment before the test runs.
     */
    String[] properties() default {};

    /**
     * A set of include filters applied to component scanning in combination with the slice's exclusion of
     * regular components.
     */
    ComponentScan.Filter[] includeFilters() default {};

    /**
     * A set of exclude filters applied to component scanning in addition to the slice's exclusion of
     * regular components.
     */
    ComponentScan.Filter[] excludeFilters() default {};

    /**
     * Whether the test should use the default filtering of the slice. Set to {@code false} to bring in
     * regular {@code @Component} beans as well.
     */
    boolean useDefaultFilters() default true;
}
