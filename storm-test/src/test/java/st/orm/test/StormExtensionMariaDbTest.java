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
import static st.orm.GenerationStrategy.NONE;
import static st.orm.test.TestDatabase.MARIADB;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * Runs the extension against MariaDB in a Testcontainers-managed container: the scripts execute in a database
 * provisioned for the class, and per-test rollback works as it does on H2.
 */
@StormTest(database = MARIADB, scripts = {"/container-schema.sql", "/container-data.sql"})
class StormExtensionMariaDbTest {

    record Item(@PK(generation = NONE) Integer id, String name) implements Entity<Integer> {}

    @Test
    void scriptsShouldExecuteAgainstTheContainerDatabase(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
    }

    @Test
    void connectionShouldBeMariaDb(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("MariaDB", connection.getMetaData().getDatabaseProductName());
            assertEquals(11, connection.getMetaData().getDatabaseMajorVersion());
        }
    }

    @Test
    void insertShouldBeRolledBackAfterTheTest(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
        orm.entity(Item.class).insert(new Item(4, "Delta"));
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

}
