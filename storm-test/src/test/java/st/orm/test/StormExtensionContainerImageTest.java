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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.test.TestDatabase.POSTGRESQL;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies the {@code image} attribute: a class that names an image runs on a container of that image, kept apart
 * from the container of the default image.
 */
@StormTest(database = POSTGRESQL, image = "postgres:16", scripts = {"/container-schema.sql", "/container-data.sql"})
class StormExtensionContainerImageTest {

    record Item(@PK(generation = NONE) Integer id, String name) implements Entity<Integer> {}

    @Test
    void connectionShouldGoToTheNamedImage(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertEquals(16, connection.getMetaData().getDatabaseMajorVersion());
        }
    }

    @Test
    void namedImageShouldHaveItsOwnContainer() {
        DatabaseContainer container = POSTGRESQL.container("postgres:16");
        assertEquals("postgres:16", container.image());
        assertNotSame(container, POSTGRESQL.container());
    }

    @Test
    void scriptsShouldExecuteAgainstTheContainerDatabase(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
    }
}
