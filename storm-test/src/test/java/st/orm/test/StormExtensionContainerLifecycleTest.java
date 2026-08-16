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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static st.orm.test.TestDatabase.POSTGRESQL;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Verifies the lifecycle of container databases: test classes that ask for the same database share one container,
 * each execution of a test class receives a database of its own inside it, so the scripts (which create a table
 * without a drop guard) run cleanly every time, and the database is dropped once the class completes.
 */
class StormExtensionContainerLifecycleTest {

    static final List<String> capturedCatalogs = new ArrayList<>();
    static final List<String> capturedUrls = new ArrayList<>();

    @Test
    void eachClassExecutionShouldGetItsOwnDatabaseInTheSharedContainer() {
        capturedCatalogs.clear();
        capturedUrls.clear();
        runCapturingCase();
        runCapturingCase();
        assertEquals(2, capturedCatalogs.size());
        assertNotEquals(capturedCatalogs.get(0), capturedCatalogs.get(1),
                "Two executions of a test class must not share a database.");
        String firstServer = capturedUrls.get(0).substring(0, capturedUrls.get(0).lastIndexOf('/'));
        String secondServer = capturedUrls.get(1).substring(0, capturedUrls.get(1).lastIndexOf('/'));
        assertEquals(firstServer, secondServer, "Two executions of a test class must share the container.");
    }

    @Test
    void databaseShouldBeDroppedAfterClassCompletes() throws Exception {
        capturedCatalogs.clear();
        capturedUrls.clear();
        runCapturingCase();
        String catalog = capturedCatalogs.getFirst();
        DatabaseContainer container = POSTGRESQL.container();
        try (var connection = DriverManager.getConnection(container.jdbcUrl(), container.username(),
                container.password());
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT count(*) FROM pg_database WHERE datname = '" + catalog + "'")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1), "Expected database " + catalog + " to be dropped.");
        }
    }

    @Test
    void closingADatabaseTwiceShouldDropItOnce() {
        DatabaseContainer.Database database = POSTGRESQL.container().createDatabase();
        assertTrue(database.url().endsWith("/" + database.name()));
        database.close();
        database.close();
    }

    @Test
    void containerAccessorsShouldDescribeTheSharedContainer() {
        DatabaseContainer container = POSTGRESQL.container();
        assertEquals(POSTGRESQL, container.database());
        assertEquals(POSTGRESQL.defaultImage(), container.image());
        assertTrue(container.jdbcUrl().startsWith("jdbc:postgresql://"));
        assertFalse(container.username().isEmpty());
    }

    private static void runCapturingCase() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CapturingCase.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(1));
    }

    @StormTest(database = POSTGRESQL, scripts = "/container-schema.sql")
    static class CapturingCase {

        @Test
        void captureDatabase(DataSource dataSource) throws Exception {
            try (var connection = dataSource.getConnection()) {
                capturedCatalogs.add(connection.getCatalog());
                capturedUrls.add(connection.getMetaData().getURL());
            }
        }
    }
}
