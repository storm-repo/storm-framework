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
package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.core.template.SqlTemplate.NamedParameter;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;

/**
 * Tests rendering database values as literals and inlining them into a statement, which is what makes a logged
 * statement runnable in a database console.
 */
public class SqlLiteralsTest {

    private static Parameter positional(int position, Object value) {
        return new PositionalParameter(position, value);
    }

    @Test
    public void nullRendersAsSqlNull() {
        assertEquals("NULL", SqlLiterals.toLiteral(null));
    }

    @Test
    public void numbersRenderUnquoted() {
        assertEquals("42", SqlLiterals.toLiteral(42));
        assertEquals("42", SqlLiterals.toLiteral(42L));
    }

    @Test
    public void stringsAreQuotedAndEscaped() {
        assertEquals("'O''Hara'", SqlLiterals.toLiteral("O'Hara"));
    }

    @Test
    public void positionalPlaceholdersAreReplacedInOrder() {
        assertEquals("SELECT * FROM city WHERE name = 'Rome' AND id = 7",
                SqlLiterals.inline("SELECT * FROM city WHERE name = ? AND id = ?",
                        List.of(positional(1, "Rome"), positional(2, 7))));
    }

    @Test
    public void namedPlaceholdersAreReplacedByName() {
        assertEquals("SELECT * FROM city WHERE name = 'Rome' AND id = 7",
                SqlLiterals.inline("SELECT * FROM city WHERE name = :name AND id = :id",
                        List.of(new NamedParameter("name", "Rome"), new NamedParameter("id", 7))));
    }

    @Test
    public void placeholdersInsideStringLiteralsAreLeftAlone() {
        // A quoted value carrying a placeholder character is part of the statement, not a placeholder.
        assertEquals("SELECT '?' , 'a:b' FROM city WHERE id = 7",
                SqlLiterals.inline("SELECT '?' , 'a:b' FROM city WHERE id = ?", List.of(positional(1, 7))));
    }

    @Test
    public void quotedValuesDoNotConsumeParameters() {
        // The first ? is inside quotes, so the parameter belongs to the second.
        assertEquals("SELECT '?' FROM city WHERE id = 7",
                SqlLiterals.inline("SELECT '?' FROM city WHERE id = ?", List.of(positional(1, 7))));
    }

    @Test
    public void castOperatorIsNotMistakenForANamedPlaceholder() {
        assertEquals("SELECT id::text FROM city WHERE name = 'Rome'",
                SqlLiterals.inline("SELECT id::text FROM city WHERE name = :name",
                        List.of(new NamedParameter("name", "Rome"))));
    }

    @Test
    public void unmatchedPlaceholdersAreLeftAsTheyAre() {
        // Reporting the wrong value would be worse than reporting an unresolved placeholder.
        assertEquals("SELECT * FROM city WHERE name = 'Rome' AND id = ?",
                SqlLiterals.inline("SELECT * FROM city WHERE name = ? AND id = ?",
                        List.of(positional(1, "Rome"))));
        assertEquals("SELECT * FROM city WHERE id = :missing",
                SqlLiterals.inline("SELECT * FROM city WHERE id = :missing",
                        List.of(new NamedParameter("other", 1))));
    }

    @Test
    public void anEscapedQuoteInsideAValueDoesNotUnbalanceTheScan() {
        assertEquals("SELECT 'it''s' FROM city WHERE id = 7",
                SqlLiterals.inline("SELECT 'it''s' FROM city WHERE id = ?", List.of(positional(1, 7))));
    }
}
