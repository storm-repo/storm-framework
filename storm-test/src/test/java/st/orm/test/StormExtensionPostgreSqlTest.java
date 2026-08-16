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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.test.TestDatabase.POSTGRESQL;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * Runs the extension against PostgreSQL in a Testcontainers-managed container: the scripts execute in a database of
 * the class's own inside the shared container, parameter injection and per-test rollback work as they do on H2, and
 * the container is the one {@link TestDatabase#container()} hands out.
 */
@StormTest(database = POSTGRESQL, scripts = {"/container-schema.sql", "/container-data.sql"})
class StormExtensionPostgreSqlTest {

    record Item(@PK(generation = NONE) Integer id, String name) implements Entity<Integer> {}

    @Test
    void scriptsShouldExecuteAgainstTheContainerDatabase(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
    }

    @Test
    void connectionShouldBePostgreSql(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertEquals(17, connection.getMetaData().getDatabaseMajorVersion());
            assertTrue(connection.getCatalog().startsWith("storm_"),
                    "Expected a database provisioned for the class but got: " + connection.getCatalog());
        }
    }

    @Test
    void connectionShouldGoToTheSharedContainer(DataSource dataSource) throws Exception {
        DatabaseContainer container = POSTGRESQL.container();
        assertSame(container, POSTGRESQL.container(POSTGRESQL.defaultImage()));
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String server = container.jdbcUrl().substring(0, container.jdbcUrl().lastIndexOf('/'));
            assertTrue(url.startsWith(server), "Expected " + url + " to point at " + server);
        }
    }

}
