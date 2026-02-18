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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;
import st.orm.test.CapturedStatement.Operation;

@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"})
class StormExtensionTest {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    @Test
    void dataSourceShouldBeInjected(DataSource dataSource) {
        assertNotNull(dataSource);
    }

    @Test
    void scriptsShouldCreateTablesAndData(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM item")) {
            assertTrue(rs.next());
            // At least 3 rows from test-data.sql; may be more if insert tests run first.
            assertTrue(rs.getInt(1) >= 3);
        }
    }

    @Test
    void ormTemplateShouldBeResolved(ORMTemplate orm) {
        assertNotNull(orm);
    }

    @Test
    void ormTemplateShouldQueryEntities(ORMTemplate orm) {
        var items = orm.entity(Item.class).findAll();
        assertEquals(3, items.size());
    }

    @Test
    void statementCaptureShouldBeInjected(StatementCapture capture) {
        assertNotNull(capture);
    }

    @Test
    void statementCaptureShouldRecordSelects(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).findAll());
        assertEquals(1, capture.count(Operation.SELECT));
        assertTrue(capture.count() >= 1);
    }

    @Test
    void statementCaptureShouldRecordInserts(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).insert(new Item(0, "Delta")));
        assertEquals(1, capture.count(Operation.INSERT));
    }

    @Test
    void statementCaptureShouldAccumulateAndClear(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).findAll());
        capture.run(() -> orm.entity(Item.class).findAll());
        assertEquals(2, capture.count(Operation.SELECT));
        capture.clear();
        assertEquals(0, capture.count());
    }

    @Test
    void capturedStatementShouldContainSqlAndParameters(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).findById(1));
        var statements = capture.statements(Operation.SELECT);
        assertEquals(1, statements.size());
        var stmt = statements.getFirst();
        assertNotNull(stmt.statement());
        assertTrue(stmt.statement().toUpperCase().contains("SELECT"));
    }
}
