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

import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.spi.TransactionRunner;
import st.orm.core.spi.TransactionScope;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies the default per-test rollback: every test starts from the state the scripts created, no matter what other
 * tests of the class write. The insert-then-count tests are intentionally identical; each passes only when the
 * other's insert has been rolled back, in any execution order.
 */
@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"})
class StormExtensionRollbackTest {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    private static final TransactionScope.Options DEFAULT_OPTIONS =
            new TransactionScope.Options(null, null, null, null, false);

    @Test
    void insertShouldBeRolledBackAfterTheTest(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
        orm.entity(Item.class).insert(new Item(0, "Delta"));
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

    @Test
    void insertOfAnotherTestShouldNotBeVisible(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
        orm.entity(Item.class).insert(new Item(0, "Delta"));
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

    @Test
    void closingAnInjectedConnectionShouldNotEndTheTestTransaction(ORMTemplate orm, DataSource dataSource)
            throws Exception {
        orm.entity(Item.class).insert(new Item(0, "Delta"));
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM item")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1));
        }
        // The try-with-resources closed a connection; the test transaction and its writes must survive that.
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

    @Test
    void transactionBlockShouldCommitWithinTheTestTransaction(ORMTemplate orm) {
        TransactionRunner.execute(DEFAULT_OPTIONS, transaction -> {
            orm.entity(Item.class).insert(new Item(0, "Epsilon"));
            return null;
        });
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

    @Test
    void transactionBlockShouldRollBackWithinTheTestTransaction(ORMTemplate orm) {
        assertThrows(IllegalStateException.class, () -> TransactionRunner.execute(DEFAULT_OPTIONS, transaction -> {
            orm.entity(Item.class).insert(new Item(0, "Zeta"));
            throw new IllegalStateException("Trigger rollback.");
        }));
        assertEquals(3, orm.entity(Item.class).findAll().size());
    }

    @Test
    void committedTransactionBlockShouldStillRollBackAfterTheTest(ORMTemplate orm) {
        // Identical to transactionBlockShouldCommitWithinTheTestTransaction: whichever runs second proves that a
        // transaction block's commit does not escape the test transaction.
        TransactionRunner.execute(DEFAULT_OPTIONS, transaction -> {
            orm.entity(Item.class).insert(new Item(0, "Epsilon"));
            return null;
        });
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }

    @Nested
    class NestedCases {

        @Test
        void nestedTestsShouldRollBackAsWell(ORMTemplate orm) {
            assertEquals(3, orm.entity(Item.class).findAll().size());
            orm.entity(Item.class).insert(new Item(0, "Delta"));
            assertEquals(4, orm.entity(Item.class).findAll().size());
        }

        @Test
        void nestedTestsShouldNotSeeOtherTestsWrites(ORMTemplate orm) {
            assertEquals(3, orm.entity(Item.class).findAll().size());
        }
    }
}
