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
package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.nullmarked.MarkedComment;
import st.orm.core.model.nullmarked.MarkedNote;
import st.orm.core.model.nullmarked.UnmarkedNote;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;

/**
 * Verifies JSpecify {@code @NullMarked} scope semantics: inside a null-marked package, unannotated components are
 * non-null by default, {@code @Nullable} opts out per type use, and {@code @NullUnmarked} cancels the scope per
 * class.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class JSpecifyNullMarkedIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testNullMarkedScopeMakesUnannotatedComponentNonNull() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var query = ORMTemplate.of(dataSource).query("SELECT 1 AS id, CAST(NULL AS VARCHAR) AS label");
            query.getResultList(MarkedNote.class);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("non-nullable"),
                "Expected a non-nullable violation, got: " + e.getCause().getMessage());
    }

    @Test
    public void testNullMarkedScopeMapsNonNullValues() {
        var query = ORMTemplate.of(dataSource).query("SELECT 1 AS id, 'note' AS label");
        var notes = query.getResultList(MarkedNote.class);
        assertEquals(new MarkedNote(1, "note"), notes.getFirst());
    }

    @Test
    public void testNullableOptOutAllowsNullWithinNullMarkedScope() {
        var query = ORMTemplate.of(dataSource).query("SELECT 1 AS id, CAST(NULL AS VARCHAR) AS remark");
        var comments = query.getResultList(MarkedComment.class);
        assertNull(comments.getFirst().remark());
    }

    @Test
    public void testNullUnmarkedClassCancelsPackageScope() {
        var query = ORMTemplate.of(dataSource).query("SELECT 1 AS id, CAST(NULL AS VARCHAR) AS label");
        var notes = query.getResultList(UnmarkedNote.class);
        assertNull(notes.getFirst().label());
    }
}
