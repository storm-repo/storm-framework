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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;
import st.orm.test.DatabaseContainer;
import st.orm.test.TestDatabase;

/**
 * Points a {@link DataStormTest @DataStormTest} slice that names a {@link DataStormTest#database() database} at a
 * database provisioned inside the shared Testcontainers-managed container of that database.
 *
 * <p>The customizer is part of the context cache key, so test classes that name the same database and image share a
 * context, and with it one provisioned database, while classes that name different ones get contexts of their own.
 * The database is created when the context is created and dropped when it closes.</p>
 *
 * @since 1.14
 */
public class DataStormTestDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        DataStormTest annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, DataStormTest.class);
        if (annotation == null) {
            return null;
        }
        if (!annotation.database().isContainer()) {
            if (!annotation.image().isEmpty()) {
                throw new IllegalStateException("@DataStormTest on " + testClass.getName() + " names image "
                        + annotation.image() + " but no container database; set database to the database the image "
                        + "runs.");
            }
            return null;
        }
        String image = annotation.image().isEmpty() ? annotation.database().defaultImage() : annotation.image();
        return new DataStormTestDatabaseContextCustomizer(annotation.database(), image);
    }

    private record DataStormTestDatabaseContextCustomizer(TestDatabase database, String image)
            implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context,
                                     MergedContextConfiguration mergedConfig) {
            DatabaseContainer.Database provisioned = database.container(image).createDatabase();
            context.addApplicationListener(new DatabaseDrop(provisioned));
            ConfigurableEnvironment environment = context.getEnvironment();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spring.datasource.url", provisioned.url());
            properties.put("spring.datasource.username", provisioned.username());
            properties.put("spring.datasource.password", provisioned.password());
            // Keeps Boot's embedded replacement, and the slice's own Boot 4 fallback, from swapping the container
            // database out again.
            properties.put("spring.test.database.replace", "none");
            // Boot runs schema.sql and data.sql for embedded databases only; the container database is as
            // disposable as the embedded one, so they run there too, unless the application decided otherwise.
            if (!environment.containsProperty("spring.sql.init.mode")) {
                properties.put("spring.sql.init.mode", "always");
            }
            environment.getPropertySources().addFirst(new MapPropertySource("dataStormTestDatabase", properties));
        }
    }

    /**
     * Drops the database provisioned for a context when the context closes.
     */
    private record DatabaseDrop(DatabaseContainer.Database database) implements ApplicationListener<ContextClosedEvent> {

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            database.close();
        }
    }
}
