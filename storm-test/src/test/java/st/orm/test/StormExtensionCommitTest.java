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

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies the {@link StormTest#rollback()} opt-out: with rollback disabled, writes commit and stay visible to later
 * tests of the class. The explicit method order makes that visibility assertable.
 */
@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"}, rollback = false)
@TestMethodOrder(OrderAnnotation.class)
class StormExtensionCommitTest {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    @Test
    @Order(1)
    void insertShouldCommit(ORMTemplate orm) {
        assertEquals(3, orm.entity(Item.class).findAll().size());
        orm.entity(Item.class).insert(new Item(0, "Delta"));
    }

    @Test
    @Order(2)
    void committedInsertShouldBeVisibleToLaterTests(ORMTemplate orm) {
        assertEquals(4, orm.entity(Item.class).findAll().size());
    }
}
