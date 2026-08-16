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
package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.container;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;
import static st.orm.test.TestDatabase.H2;
import static st.orm.test.TestDatabase.POSTGRESQL;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Verifies that a container database that conflicts with the other ways of pointing {@code @StormTest} at a
 * database, and a container database whose dependencies are missing, fail with a message that names the problem
 * rather than with the consequences of ignoring it.
 */
class StormExtensionContainerConfigurationTest {

    @Test
    void databaseWithUrlShouldFail() {
        assertFailsWith(DatabaseAndUrlCase.class, "sets both database POSTGRESQL and url");
    }

    @Test
    void databaseWithDataSourceFactoryShouldFail() {
        assertFailsWith(DatabaseAndFactoryCase.class, "static dataSource() factory method");
    }

    @Test
    void imageWithoutContainerDatabaseShouldFail() {
        assertFailsWith(ImageWithoutContainerCase.class, "names image postgres:17 but no container database");
    }

    @Test
    void h2ShouldHaveNoContainer() {
        assertTrue(POSTGRESQL.isContainer());
        var exception = assertThrows(IllegalStateException.class, H2::container);
        assertTrue(exception.getMessage().contains("H2 runs in memory"), exception.getMessage());
        assertThrows(IllegalStateException.class, H2::defaultImage);
        assertThrows(IllegalArgumentException.class, () -> POSTGRESQL.container(" "));
    }

    @Test
    void missingTestcontainersModuleShouldFailNamingTheDependency() throws Exception {
        // Loads the module's classes in a class loader that sees the JDK and nothing else, the situation of a test
        // classpath without Testcontainers.
        URL classes = new File(TestDatabase.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toURI().toURL();
        try (var loader = new URLClassLoader(new URL[] {classes}, ClassLoader.getPlatformClassLoader())) {
            Class<?> testDatabase = loader.loadClass(TestDatabase.class.getName());
            assertTrue(testDatabase.getClassLoader() == loader);
            Object postgresql = testDatabase.getMethod("valueOf", String.class).invoke(null, "POSTGRESQL");
            var exception = assertThrows(InvocationTargetException.class,
                    () -> testDatabase.getMethod("container").invoke(postgresql));
            assertEquals(IllegalStateException.class, exception.getCause().getClass());
            String message = exception.getCause().getMessage();
            assertTrue(message.contains("org.testcontainers:testcontainers-postgresql"), message);
            assertTrue(message.contains("Testcontainers module for POSTGRESQL"), message);
        }
    }

    private static void assertFailsWith(Class<?> testClass, String messagePart) {
        Predicate<String> containsPart = message -> message.contains(messagePart);
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(testClass))
                .execute()
                .containerEvents()
                .assertThatEvents()
                .haveExactly(1, event(container(testClass), finishedWithFailure(
                        instanceOf(ExtensionConfigurationException.class), message(containsPart))));
    }

    @StormTest(database = POSTGRESQL, url = "jdbc:h2:mem:conflict")
    static class DatabaseAndUrlCase {

        @Test
        void neverRuns() {
        }
    }

    @StormTest(database = POSTGRESQL)
    static class DatabaseAndFactoryCase {

        static DataSource dataSource() {
            return new SimpleTestDataSource("jdbc:h2:mem:conflict", "sa", "");
        }

        @Test
        void neverRuns() {
        }
    }

    @StormTest(image = "postgres:17")
    static class ImageWithoutContainerCase {

        @Test
        void neverRuns() {
        }
    }
}
