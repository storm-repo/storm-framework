package st.orm.spi.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.StormConfig;

/**
 * Unit tests for {@link MySQLSqlDialect} verifying MySQL-specific SQL generation behavior.
 */
class MySQLSqlDialectTest {

    private final MySQLSqlDialect dialect = new MySQLSqlDialect();

    // -- Identifier validation: MySQL allows underscore prefix --

    @Test
    void identifierPatternShouldAcceptUnderscorePrefixedNames() {
        // MySQL (unlike ANSI/Oracle/PostgreSQL) allows _ as first character.
        assertTrue(dialect.getValidIdentifierPattern().matcher("_temp").matches());
    }

    @Test
    void identifierPatternShouldRejectNumericLeadingCharacters() {
        assertFalse(dialect.getValidIdentifierPattern().matcher("123start").matches());
    }

    @Test
    void identifierPatternShouldRejectEmptyString() {
        assertFalse(dialect.getValidIdentifierPattern().matcher("").matches());
    }

    // -- Escape: MySQL uses backtick escaping --

    @Test
    void escapeShouldWrapInBackticks() {
        assertEquals("`myColumn`", dialect.escape("myColumn"));
    }

    @Test
    void escapeShouldDoubleEmbeddedBackticks() {
        assertEquals("`my``Column`", dialect.escape("my`Column"));
    }

    @Test
    void escapeShouldHandleMultipleEmbeddedBackticks() {
        assertEquals("`a``b``c`", dialect.escape("a`b`c"));
    }

    // -- getSafeIdentifier: keyword + escape integration --

    @Test
    void getSafeIdentifierShouldEscapeMySQLSpecificKeywords() {
        // FULLTEXT, UNSIGNED, ZEROFILL are MySQL-specific keywords.
        assertEquals("`FULLTEXT`", dialect.getSafeIdentifier("FULLTEXT"));
        assertEquals("`UNSIGNED`", dialect.getSafeIdentifier("UNSIGNED"));
        assertEquals("`ZEROFILL`", dialect.getSafeIdentifier("ZEROFILL"));
    }

    @Test
    void getSafeIdentifierShouldNotEscapeNormalIdentifiers() {
        assertEquals("myTable", dialect.getSafeIdentifier("myTable"));
        assertEquals("_temp", dialect.getSafeIdentifier("_temp"));
    }

    @Test
    void getSafeIdentifierShouldEscapeIdentifiersWithSpaces() {
        assertEquals("`my table`", dialect.getSafeIdentifier("my table"));
    }

    @Test
    void isKeywordShouldBeCaseInsensitive() {
        assertTrue(dialect.isKeyword("fulltext"));
        assertTrue(dialect.isKeyword("FULLTEXT"));
        assertFalse(dialect.isKeyword("myColumn"));
    }

    // -- Identifier pattern: matches backtick and double-quote identifiers --

    @Test
    void identifierPatternShouldExtractBacktickIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT `my col` FROM t");
        assertTrue(matcher.find());
        assertEquals("`my col`", matcher.group());
    }

    @Test
    void identifierPatternShouldExtractDoubleQuotedIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT \"my col\" FROM t");
        assertTrue(matcher.find());
        assertEquals("\"my col\"", matcher.group());
    }

    @Test
    void identifierPatternShouldNotMatchUnquotedText() {
        assertFalse(dialect.getIdentifierPattern().matcher("SELECT myCol FROM t").find());
    }

    // -- Quote literal pattern --

    @Test
    void quoteLiteralPatternShouldMatchStringWithEscapedQuotes() {
        var matcher = dialect.getQuoteLiteralPattern().matcher("'it''s a test'");
        assertTrue(matcher.find());
        assertEquals("'it''s a test'", matcher.group());
    }

    // -- MySQL-specific limit/offset with unusual offset workaround --

    @Test
    void limitShouldGenerateLimitClause() {
        assertEquals("LIMIT 10", dialect.limit(10));
        assertEquals("LIMIT 0", dialect.limit(0));
    }

    @Test
    void offsetShouldUseLimitMaxBigintWorkaround() {
        // MySQL does not support standalone OFFSET; uses LIMIT MAX_BIGINT OFFSET N.
        assertEquals("LIMIT 18446744073709551615 OFFSET 5", dialect.offset(5));
        assertEquals("LIMIT 18446744073709551615 OFFSET 0", dialect.offset(0));
    }

    @Test
    void limitWithOffsetShouldCombineLimitAndOffset() {
        assertEquals("LIMIT 20 OFFSET 10", dialect.limit(10, 20));
    }

    // -- MySQL sequence: not supported, should throw --

    @Test
    void sequenceNextValShouldThrowPersistenceException() {
        assertThrows(PersistenceException.class, () -> dialect.sequenceNextVal("my_seq"));
    }

    @Test
    void sequenceNextValExceptionShouldContainMeaningfulMessage() {
        var exception = assertThrows(PersistenceException.class, () -> dialect.sequenceNextVal("my_seq"));
        assertTrue(exception.getMessage().contains("MySQL"));
        assertTrue(exception.getMessage().contains("sequence"));
    }

    // -- Provider filter --

    @Test
    void providerFilterShouldAcceptNonSqlDialectProviders() {
        assertTrue(MySQLProviderFilter.INSTANCE.test(new st.orm.core.spi.Provider() {}));
    }

    @Test
    void providerFilterShouldRejectForeignSqlDialectProviders() {
        assertFalse(MySQLProviderFilter.INSTANCE.test(new st.orm.core.spi.SqlDialectProvider() {
            @Override public st.orm.core.template.SqlDialect getSqlDialect(StormConfig config) { return null; }
        }));
    }

    @Test
    void providerFilterShouldAcceptMySQLEntityRepositoryProvider() {
        assertTrue(MySQLProviderFilter.INSTANCE.test(new MySQLEntityRepositoryProviderImpl()));
    }

    // -- SqlDialectProvider --

    @Test
    void sqlDialectProviderShouldReturnDialectWithMySQLSpecificBehavior() {
        var provider = new MySQLSqlDialectProviderImpl();
        var sqlDialect = provider.getSqlDialect(StormConfig.of(Map.of()));
        // MySQL-specific: backtick escaping and LIMIT syntax.
        assertEquals("`col`", sqlDialect.escape("col"));
        assertEquals("LIMIT 5", sqlDialect.limit(5));
    }
}
