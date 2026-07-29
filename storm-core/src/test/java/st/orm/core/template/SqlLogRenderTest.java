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
package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.core.template.SqlLog.StatementLine;

/**
 * Tests the summary a log reader sees: the heaviest statement leads, repetition reads as a multiplier, and the
 * headline separates database time from the time the call took.
 */
public class SqlLogRenderTest {

    private static final String NL = System.lineSeparator();

    private static StatementLine line(String sql, String type, int executions, long millis, long rows) {
        return new StatementLine(sql, type, false, executions, 1, millis * 1_000_000L, null, 0, rows, true, null);
    }

    @Test
    public void theHeaviestStatementLeadsAndRepetitionReadsAsAMultiplier() {
        String rendered = SqlLog.render("GET /owners/42", 12, 0, 0, 214, 214, 1, 678,
                List.of(line("SELECT a FROM city", "City", 1, 18, 1), line("SELECT b FROM owner", "Owner", 7, 96, 700)), 0);
        // The heaviest statement leads, whatever order it arrived in, and each row names its target type.
        assertTrue(rendered.startsWith("SQL (GET /owners/42): 12 statements, 214 ms in database, 678 ms total" + NL),
                rendered);
        assertTrue(rendered.indexOf("SELECT b FROM owner") < rendered.indexOf("SELECT a FROM city"), rendered);
        assertTrue(rendered.contains("96 ms  700 rows  7x  Owner  SELECT b FROM owner"), rendered);
        assertTrue(rendered.contains("18 ms    1 rows  1x  City   SELECT a FROM city"), rendered);
    }

    @Test
    public void concurrencyIsReportedOnlyWhenTheWorkOverlapped() {
        String serial = SqlLog.render("call", 2, 0, 0, 40, 40, 1, 90, List.of(line("SELECT 1", "-", 2, 40, 2)), 0);
        assertFalse(serial.contains("concurrent"), serial);
        String parallel = SqlLog.render("call", 8, 0, 0, 214, 61, 4, 678, List.of(line("SELECT 1", "-", 8, 214, 8)), 0);
        assertTrue(parallel.contains("214 ms in database over 61 ms elapsed (peak 4 concurrent), 678 ms total"),
                parallel);
    }

    @Test
    public void aFetchIsNamedOnItsOwnLine() {
        String rendered = SqlLog.render("call", 9, 8, 0, 30, 30, 1, 40,
                List.of(new StatementLine("SELECT c FROM city WHERE id = ?", "City", true, 8, 1, 28_000_000L, null, 0, 8, true, null)), 0);
        assertTrue(rendered.contains("8 fetches"), rendered);
        assertTrue(rendered.contains("28 ms  8 rows  8x  City  fetch  SELECT c FROM city WHERE id = ?"), rendered);
    }

    @Test
    public void aLongStatementIsElidedOntoOneLine() {
        String rendered = SqlLog.render("call", 1, 0, 0, 5, 5, 1, 9,
                List.of(line("SELECT " + "x".repeat(200) + "\n  FROM city", "City", 1, 5, 1)), 0);
        assertFalse(rendered.contains("\n  FROM"), rendered);
        assertTrue(rendered.contains("\u2026"), rendered);
        // The tail survives the elision: the FROM clause is what identifies the statement.
        assertTrue(rendered.contains("FROM city"), rendered);
    }

    @Test
    public void aNestedSubqueryClosesUpRatherThanCarryingItsLineBreaks() {
        // A subquery is rendered on lines of its own, which leaves its parentheses opening and closing lines.
        String rendered = SqlLog.render("call", 1, 0, 0, 7, 7, 1, 9,
                List.of(line("""
                        SELECT SUM(c)
                        FROM ds
                        INNER JOIN (
                          SELECT id
                          FROM df
                          HAVING n = (
                            SELECT MAX(1)
                            FROM df1
                            WHERE id = ?
                          )
                        ) x ON ds.id = x.id""", "DemographicSet", 1, 7, 1)), 0);
        // Both subqueries close where they end, and the breaks that separate words still read as spaces.
        assertTrue(rendered.contains(
                "SELECT SUM(c) FROM ds INNER JOIN (SELECT id FROM df HAVING n = "
                        + "(SELECT MAX(1) FROM df1 WHERE id = ?)) x ON ds.id = x.id"),
                rendered);
    }

    @Test
    public void aGroupCoveringSeveralTextsSaysSo() {
        // A collection parameter that expands per execution yields one group with several texts.
        String rendered = SqlLog.render("call", 3, 0, 0, 12, 12, 1, 20,
                List.of(new StatementLine("SELECT c FROM city WHERE id IN (?, ?)", "City", false, 3, 3, 12_000_000L, null, 0, 6, true, null)), 0);
        assertTrue(rendered.contains("IN (?, ?) (3 variants)"), rendered);
    }

    @Test
    public void statementsBeyondTheLimitAreReportedRatherThanDropped() {
        String rendered = SqlLog.render("call", 500, 0, 0, 90, 90, 1, 120, List.of(line("SELECT 1", "-", 200, 90, 200)), 300);
        assertTrue(rendered.endsWith("(300 statements not recorded)"), rendered);
    }

    @Test
    public void truncatedDatabaseTimesReadAsLowerBounds() {
        // The unrecorded statements contributed no duration, so the summary must not present its own total as
        // the whole cost of the call.
        String truncated = SqlLog.render("call", 500, 0, 0, 90, 60, 4, 120,
                List.of(line("SELECT 1", "-", 200, 90, 200)), 300);
        assertTrue(truncated.contains("500 statements, 90+ ms in database over 60+ ms elapsed (peak 4 concurrent), 120 ms total"),
                truncated);
        // Nothing was dropped here, so the same numbers are exact and carry no marker.
        String complete = SqlLog.render("call", 200, 0, 0, 90, 60, 4, 120,
                List.of(line("SELECT 1", "-", 200, 90, 200)), 0);
        assertTrue(complete.contains("200 statements, 90 ms in database over 60 ms elapsed (peak 4 concurrent), 120 ms total"),
                complete);
    }

    @Test
    public void aCallSiteIsRenderedWhenRecorded() {
        String rendered = SqlLog.render("call", 9, 0, 0, 30, 30, 1, 40,
                List.of(new StatementLine("SELECT s FROM user_session", "UserSession", false, 8, 1, 28_000_000L,
                        "MeController.kt:41", 3, 8, true, null)), 0);
        assertTrue(rendered.contains("MeController.kt:41 (+2 sites)  SELECT s FROM user_session"), rendered);
    }

    @Test
    public void aPlaceholderRunCollapsesInTheDisplay() {
        String rendered = SqlLog.render("call", 1, 0, 0, 5, 5, 1, 9,
                List.of(line("INSERT INTO t (a, b) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", "-", 1, 5, 4)), 0);
        assertTrue(rendered.contains("VALUES (?, \u2026, ?)"), rendered);
        // Short runs stay as they are.
        String untouched = SqlLog.render("call", 1, 0, 0, 5, 5, 1, 9,
                List.of(line("SELECT c FROM city WHERE id IN (?, ?)", "-", 1, 5, 2)), 0);
        assertTrue(untouched.contains("IN (?, ?)"), untouched);
    }

    @Test
    public void cacheServedReadsAppearInTheHeadlineOnlyWhenAnyWere() {
        String rendered = SqlLog.render("call", 3, 2, 4, 30, 30, 1, 40, List.of(line("SELECT 1", "-", 3, 30, 3)), 0);
        assertTrue(rendered.contains("3 statements, 2 fetches, 4 from cache, 30 ms in database"), rendered);
        String without = SqlLog.render("call", 3, 0, 0, 30, 30, 1, 40, List.of(line("SELECT 1", "-", 3, 30, 3)), 0);
        assertFalse(without.contains("from cache"), without);
    }

    @Test
    public void anInexactRowCountIsMarkedAsALowerBound() {
        // A batch entry whose count the driver declined to report, or a stream closed before its end, leaves the
        // count a known lower bound; the star says so, and the exact count in the other row stays unmarked.
        String rendered = SqlLog.render("call", 2, 0, 0, 45, 45, 1, 60, List.of(
                new StatementLine("INSERT INTO city (name) VALUES (?)", "City", false, 1, 1, 40_000_000L, null, 0,
                        500, false, null),
                line("SELECT c FROM city", "City", 1, 5, 3)), 0);
        assertTrue(rendered.contains("40 ms  500* rows  1x  City  INSERT INTO city (name) VALUES (?)"), rendered);
        // The star participates in the column width, so the exact count aligns under it.
        assertTrue(rendered.contains(" 5 ms     3 rows  1x  City  SELECT c FROM city"), rendered);
    }

    @Test
    public void aHydrationShapeRendersAtTheEndOfItsRow() {
        String rendered = SqlLog.render("call", 1, 0, 0, 5, 5, 1, 9,
                List.of(new StatementLine("SELECT p FROM pet", "Pet", false, 1, 1, 5_000_000L, null, 0, 1, true,
                        "joins=2 columns=10 graph=Pet(Owner(City))")), 0);
        assertTrue(rendered.contains("SELECT p FROM pet  joins=2 columns=10 graph=Pet(Owner(City))"), rendered);
    }
}
