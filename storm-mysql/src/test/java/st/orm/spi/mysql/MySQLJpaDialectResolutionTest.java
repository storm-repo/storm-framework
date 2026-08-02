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
package st.orm.spi.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.StormConfig;
import st.orm.core.spi.DefaultSqlDialect;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.impl.JpaTemplateImpl;

/**
 * A JPA template generates SQL for the database its persistence unit is configured with.
 *
 * <p>This module's dialect claims only MySQL, and the persistence unit here runs on an embedded database it does
 * not claim. Without asking the persistence unit which database it holds, the template falls back to the shared
 * {@link SqlTemplate#JPA}, whose dialect is resolved once from the classpath, and writes MySQL syntax for a
 * database that is not MySQL.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
// The module's SQL fixtures are written for MySQL, and this persistence unit deliberately runs on another
// database, so they are not applied here.
@DataJpaTest(showSql = false, properties = "spring.sql.init.mode=never")
public class MySQLJpaDialectResolutionTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void sqlIsGeneratedWithTheDialectOfThePersistenceUnitsDatabase() {
        // The dialects share a hierarchy, so only the exact class distinguishes them.
        assertEquals(MySQLSqlDialect.class, SqlTemplate.JPA.dialect().getClass(),
                "The shared template resolves this module's dialect, which is the state this test needs");

        var template = new JpaTemplateImpl(entityManager, StormConfig.defaults());

        assertEquals(DefaultSqlDialect.class, template.sqlTemplate().dialect().getClass());
    }
}
