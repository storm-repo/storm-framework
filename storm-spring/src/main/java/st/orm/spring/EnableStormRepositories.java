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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import st.orm.spring.impl.StormRepositoriesRegistrar;

/**
 * Enables Storm repository beans, mirroring annotations like {@code @EnableJpaRepositories}.
 *
 * <p>Scans the configured base packages for repository interfaces and registers each as a Spring bean,
 * available for constructor injection. Without explicit packages, the package of the annotated class is
 * scanned. Works with both language stacks: the repositories are bound through the Kotlin API when
 * storm-kotlin-spring is on the classpath, and through the Java API otherwise.</p>
 *
 * {@snippet lang = java:
 * @Configuration
 * @EnableStormRepositories(basePackages = "com.acme.repository")
 * public class AcmeConfiguration {
 * }
 * }
 *
 * <p>In Spring Boot, repositories are discovered automatically under the application's base package; this
 * annotation doubles as the explicit override there — when present, the starter's auto-configured scanning
 * backs off. For multiple repository sets bound to different templates, define one
 * {@link RepositoryBeanFactoryPostProcessor} bean per set using its configuring constructor instead.</p>
 *
 * @since 1.13
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(StormRepositoriesRegistrar.class)
public @interface EnableStormRepositories {

    /**
     * Base packages to scan for repository interfaces. Empty means the package of the annotated class.
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()}: the packages of the given classes are scanned.
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * The {@code ORMTemplate} bean the repositories bind to. Empty means the primary template.
     */
    String ormTemplateBeanName() default "";

    /**
     * Prefix for the registered repository bean names. Empty for none.
     */
    String repositoryPrefix() default "";
}
