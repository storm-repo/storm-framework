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
package st.orm.core.spi;

import static java.lang.Thread.currentThread;
import static java.util.Collections.enumeration;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import st.orm.PersistenceException;
import st.orm.StormConfig;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplate;

/**
 * Tests for the fail-fast classpath resolution of the SQL dialect.
 */
public class SqlDialectProviderResolutionTest {

    @TempDir
    Path tempDir;

    /** Dialect provider without ordering constraints; an unordered peer of {@link UnorderedProviderB}. */
    public static class UnorderedProviderA implements SqlDialectProvider {
        @Override
        public SqlDialect getSqlDialect(StormConfig config) {
            return new DefaultSqlDialect(config);
        }
    }

    /** Dialect provider without ordering constraints; an unordered peer of {@link UnorderedProviderA}. */
    public static class UnorderedProviderB implements SqlDialectProvider {
        @Override
        public SqlDialect getSqlDialect(StormConfig config) {
            return new DefaultSqlDialect(config);
        }
    }

    /** Class loader that substitutes the {@link SqlDialectProvider} service registrations. */
    private static final class ServiceSubstitutingClassLoader extends ClassLoader {

        private static final String SERVICE_RESOURCE = "META-INF/services/" + SqlDialectProvider.class.getName();

        private final URL services;

        ServiceSubstitutingClassLoader(ClassLoader parent, URL services) {
            super(parent);
            this.services = services;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICE_RESOURCE.equals(name)) {
                return enumeration(List.of(services));
            }
            return super.getResources(name);
        }
    }

    private void withDialectProviders(List<Class<?>> providers, Runnable runnable) {
        URL services;
        try {
            Path file = tempDir.resolve("dialect-providers");
            Files.writeString(file, providers.stream().map(Class::getName).collect(joining("\n")));
            services = file.toUri().toURL();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Thread thread = currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(new ServiceSubstitutingClassLoader(getClass().getClassLoader(), services));
        try {
            runnable.run();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    public void testUnorderedPeersFailFast() {
        withDialectProviders(List.of(UnorderedProviderA.class, UnorderedProviderB.class), () -> {
            var exception = assertThrows(PersistenceException.class,
                    () -> Providers.getSqlDialect(StormConfig.defaults()));
            assertTrue(exception.getMessage().contains(UnorderedProviderA.class.getName()),
                    "Error must name the candidate providers");
            assertTrue(exception.getMessage().contains(UnorderedProviderB.class.getName()),
                    "Error must name the candidate providers");
        });
    }

    @Test
    public void testSingleProviderResolves() {
        withDialectProviders(List.of(DefaultSqlDialectProviderImpl.class), () ->
                assertEquals(new DefaultSqlDialect(StormConfig.defaults()).name(),
                        Providers.getSqlDialect(StormConfig.defaults()).name()));
    }

    @Test
    public void testOrderedProvidersResolveUnique() {
        withDialectProviders(List.of(FetchSizeSqlDialectProviderImpl.class, DefaultSqlDialectProviderImpl.class), () ->
                assertEquals("FetchSizeTest", Providers.getSqlDialect(StormConfig.defaults()).name(),
                        "The provider ordered before any other must win without an ambiguity error"));
    }

    @Test
    public void testAmbientDialectResolutionIsLazy() {
        withDialectProviders(List.of(UnorderedProviderA.class, UnorderedProviderB.class), () -> {
            // Deriving a template from the shared ambient instance must not resolve the dialect: database-bound
            // templates derive and then set the dialect resolved for their database.
            SqlTemplate template = SqlTemplate.PS.withConfig(StormConfig.of(Map.of()));
            SqlDialect dialect = new DefaultSqlDialect();
            assertSame(dialect, template.withDialect(dialect).dialect(),
                    "An explicitly set dialect must not trigger classpath resolution");
            assertThrows(PersistenceException.class, template::dialect,
                    "Using the ambient dialect on an ambiguous classpath must fail fast");
        });
    }
}
