package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.spi.Providers.getSqlDialect;
import static st.orm.core.template.impl.SqlParser.clearQuotedIdentifiers;
import static st.orm.core.template.impl.SqlParser.clearStringLiterals;
import static st.orm.core.template.impl.SqlParser.endsWithKeyword;
import static st.orm.core.template.impl.SqlParser.hasWhereClause;
import static st.orm.core.template.impl.SqlParser.startsWithKeyword;

import org.junit.jupiter.api.Test;

public class SqlParserTest {

    @Test
    public void testQuotedIdentifiers() {
        // clearQuotedIdentifiers should remove content inside double-quoted identifiers but leave
        // single-quoted string literals untouched (including escaped quotes like '''value''').
        assertEquals("SELECT * FROM \"\" WHERE column = 'value'", clearQuotedIdentifiers("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\" WHERE column = '''value'''", clearQuotedIdentifiers("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }

    @Test
    public void testStringLiterals() {
        // clearStringLiterals should remove content inside single-quoted string literals but leave
        // double-quoted identifiers untouched (including escaped identifiers like """table""").
        assertEquals("SELECT * FROM \"table\" WHERE column = ''", clearStringLiterals("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\"\"table\"\"\" WHERE column = ''", clearStringLiterals("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }

    // endsWithKeyword

    @Test
    public void testEndsWithKeywordMatchesKeywordAfterBoundary() {
        assertTrue(endsWithKeyword("SELECT * FROM T WHERE", "WHERE"));
        assertTrue(endsWithKeyword("SELECT * FROM T\nWHERE", "WHERE"));
        assertTrue(endsWithKeyword("(WHERE", "WHERE"));
        assertTrue(endsWithKeyword("A SET", "SET"));
        assertTrue(endsWithKeyword("WHERE", "WHERE"));
    }

    @Test
    public void testEndsWithKeywordIsCaseInsensitive() {
        assertTrue(endsWithKeyword("select * from t where", "WHERE"));
    }

    @Test
    public void testEndsWithKeywordRejectsIdentifierSuffix() {
        assertFalse(endsWithKeyword("SELECT * FROM nowhere", "WHERE"));
        assertFalse(endsWithKeyword("SELECT dataset", "SET"));
        assertFalse(endsWithKeyword("LIMIT 1 OFFSET", "SET"));
        assertFalse(endsWithKeyword("INSERT INTO key_values", "VALUES"));
        assertFalse(endsWithKeyword("SELECT copied_from", "FROM"));
        assertFalse(endsWithKeyword("SELECT 7SET", "SET"));
    }

    @Test
    public void testEndsWithKeywordRejectsShorterString() {
        assertFalse(endsWithKeyword("SET", "WHERE"));
    }

    // startsWithKeyword

    @Test
    public void testStartsWithKeywordMatchesKeywordBeforeBoundary() {
        assertTrue(startsWithKeyword("FROM t", "FROM"));
        assertTrue(startsWithKeyword("FROM", "FROM"));
        assertTrue(startsWithKeyword("from t", "FROM"));
        assertTrue(startsWithKeyword("FROM(", "FROM"));
    }

    @Test
    public void testStartsWithKeywordRejectsIdentifierPrefix() {
        assertFalse(startsWithKeyword("FROMAGE", "FROM"));
        assertFalse(startsWithKeyword("FROM_TABLE t", "FROM"));
        assertFalse(startsWithKeyword("FROM2 t", "FROM"));
    }

    // hasWhereClause

    @Test
    public void testHasWhereClauseDetectsTopLevelWhere() {
        assertTrue(hasWhereClause("DELETE FROM t WHERE id = 1", getSqlDialect()));
        assertTrue(hasWhereClause("update t set x = 1 where id = 1", getSqlDialect()));
        assertTrue(hasWhereClause("DELETE FROM t WHERE EXISTS (SELECT 1 FROM b WHERE b.id = t.id)", getSqlDialect()));
        assertTrue(hasWhereClause("UPDATE t SET x = (SELECT a FROM b WHERE c = 1) WHERE t.id = 1", getSqlDialect()));
    }

    @Test
    public void testHasWhereClauseIgnoresSubqueryWhere() {
        assertFalse(hasWhereClause("UPDATE t SET x = (SELECT a FROM b WHERE c = 1)", getSqlDialect()));
        assertFalse(hasWhereClause("DELETE FROM t USING (SELECT id FROM b WHERE b.x = 1) s", getSqlDialect()));
    }

    @Test
    public void testHasWhereClauseIgnoresMissingWhere() {
        assertFalse(hasWhereClause("DELETE FROM t", getSqlDialect()));
        assertFalse(hasWhereClause("UPDATE t SET x = 1", getSqlDialect()));
    }

    @Test
    public void testHasWhereClauseIgnoresIdentifiersAndLiterals() {
        assertFalse(hasWhereClause("UPDATE nowhere SET x = 1", getSqlDialect()));
        assertFalse(hasWhereClause("UPDATE t SET x = 'WHERE'", getSqlDialect()));
        assertFalse(hasWhereClause("UPDATE t SET \"where\" = 1", getSqlDialect()));
        assertFalse(hasWhereClause("UPDATE t SET x = 1 -- WHERE id = 1", getSqlDialect()));
    }
}
