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

import java.util.List;
import java.util.Objects;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Registers the {@link DataStormTypeExcludeFilter} for {@link DataStormTest @DataStormTest} classes.
 *
 * <p>Registration goes through a context customizer rather than {@code @TypeExcludeFilters} so that one
 * artifact serves both Spring Boot 3 and 4, which host that annotation in different locations. The filter
 * is registered as a singleton named {@code dataStormTypeExcludeFilter} and picked up by the component
 * scan's {@code TypeExcludeFilter} delegation.</p>
 *
 * @since 1.13
 */
public class DataStormTestContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        DataStormTest annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, DataStormTest.class);
        return annotation != null ? new DataStormTestContextCustomizer(annotation) : null;
    }

    private record DataStormTestContextCustomizer(DataStormTest annotation) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context,
                                     MergedContextConfiguration mergedConfig) {
            context.getBeanFactory().registerSingleton(
                    "dataStormTypeExcludeFilter", new DataStormTypeExcludeFilter(annotation));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DataStormTestContextCustomizer customizer
                    && Objects.equals(new DataStormTypeExcludeFilter(annotation),
                                      new DataStormTypeExcludeFilter(customizer.annotation));
        }

        @Override
        public int hashCode() {
            return new DataStormTypeExcludeFilter(annotation).hashCode();
        }
    }
}
