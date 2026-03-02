package st.orm.spi.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import st.orm.StormConfig;

/**
 * Unit tests for {@link OracleSqlDialect} verifying Oracle-specific SQL generation behavior.
 */
class OracleSqlDialectTest {

    private final OracleSqlDialect dialect = new OracleSqlDialect();

    // -- Escape: Oracle uses double-quote escaping --

    @Test
    void escapeShouldWrapInDoubleQuotes() {
        assertEquals("\"myColumn\"", dialect.escape("myColumn"));
    }

    @Test
    void escapeShouldDoubleEmbeddedDoubleQuotes() {
        assertEquals("\"my\"\"Column\"", dialect.escape("my\"Column"));
    }

    @Test
    void escapeShouldHandleMultipleEmbeddedDoubleQuotes() {
        assertEquals("\"a\"\"b\"\"c\"", dialect.escape("a\"b\"c"));
    }

    // -- getSafeIdentifier: keyword + escape integration --

    @Test
    void getSafeIdentifierShouldEscapeOracleSpecificKeywords() {
        // ROWNUM, ROWID, SYNONYM, VARCHAR2 are Oracle-specific keywords.
        assertEquals("\"ROWNUM\"", dialect.getSafeIdentifier("ROWNUM"));
        assertEquals("\"ROWID\"", dialect.getSafeIdentifier("ROWID"));
        assertEquals("\"VARCHAR2\"", dialect.getSafeIdentifier("VARCHAR2"));
    }

    @Test
    void getSafeIdentifierShouldNotEscapeNormalIdentifiers() {
        assertEquals("myTable", dialect.getSafeIdentifier("myTable"));
    }

    @Test
    void getSafeIdentifierShouldEscapeIdentifiersWithInvalidCharacters() {
        assertEquals("\"my table\"", dialect.getSafeIdentifier("my table"));
        assertEquals("\"my-col\"", dialect.getSafeIdentifier("my-col"));
    }

    @Test
    void isKeywordShouldBeCaseInsensitive() {
        assertTrue(dialect.isKeyword("rownum"));
        assertTrue(dialect.isKeyword("ROWNUM"));
        assertFalse(dialect.isKeyword("myColumn"));
    }

    // -- Identifier pattern --

    @Test
    void identifierPatternShouldExtractDoubleQuotedIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT \"my col\" FROM t");
        assertTrue(matcher.find());
        assertEquals("\"my col\"", matcher.group());
    }

    // -- Quote literal pattern --

    @Test
    void quoteLiteralPatternShouldMatchStringWithEscapedQuotes() {
        var matcher = dialect.getQuoteLiteralPattern().matcher("'it''s a test'");
        assertTrue(matcher.find());
        assertEquals("'it''s a test'", matcher.group());
    }

    // -- Oracle-specific limit/offset: FETCH FIRST / OFFSET ROWS syntax --

    @Test
    void limitShouldUseFetchFirstSyntax() {
        // Oracle uses FETCH FIRST N ROWS ONLY, not LIMIT N.
        assertEquals("FETCH FIRST 10 ROWS ONLY", dialect.limit(10));
        assertEquals("FETCH FIRST 1 ROWS ONLY", dialect.limit(1));
        assertEquals("FETCH FIRST 0 ROWS ONLY", dialect.limit(0));
    }

    @Test
    void offsetShouldUseOffsetRowsSyntax() {
        assertEquals("OFFSET 5 ROWS", dialect.offset(5));
        assertEquals("OFFSET 0 ROWS", dialect.offset(0));
    }

    @Test
    void limitWithOffsetShouldUseFetchNextSyntax() {
        assertEquals("OFFSET 10 ROWS FETCH NEXT 20 ROWS ONLY", dialect.limit(10, 20));
        assertEquals("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY", dialect.limit(0, 1));
    }

    // -- Oracle-specific lock hints: no shared lock, FOR UPDATE --

    @Test
    void forShareLockShouldReturnEmptyBecauseOracleDoesNotSupportSharedLocks() {
        // Oracle does not support a shared lock hint, so it returns empty string.
        assertEquals("", dialect.forShareLockHint());
    }

    // -- Sequence SQL: Oracle-specific .NEXTVAL syntax --

    @Test
    void sequenceNextValShouldGenerateDotNextvalSyntax() {
        assertEquals("my_seq.NEXTVAL", dialect.sequenceNextVal("my_seq"));
    }

    @Test
    void sequenceNextValShouldEscapeKeywordSequenceName() {
        var result = dialect.sequenceNextVal("SELECT");
        assertEquals("\"SELECT\".NEXTVAL", result);
    }

    // -- Provider filter --

    @Test
    void providerFilterShouldAcceptNonSqlDialectProviders() {
        assertTrue(OracleProviderFilter.INSTANCE.test(new st.orm.core.spi.Provider() {}));
    }

    @Test
    void providerFilterShouldRejectForeignSqlDialectProviders() {
        assertFalse(OracleProviderFilter.INSTANCE.test(new st.orm.core.spi.SqlDialectProvider() {
            @Override public st.orm.core.template.SqlDialect getSqlDialect(StormConfig config) { return null; }
        }));
    }

    @Test
    void providerFilterShouldAcceptOracleEntityRepositoryProvider() {
        assertTrue(OracleProviderFilter.INSTANCE.test(new OracleEntityRepositoryProviderImpl()));
    }

    // -- SqlDialectProvider --

    @Test
    void sqlDialectProviderShouldReturnDialectWithOracleSpecificBehavior() {
        var provider = new OracleSqlDialectProviderImpl();
        var sqlDialect = provider.getSqlDialect(StormConfig.of(Map.of()));
        // Oracle-specific: double-quote escaping and FETCH FIRST syntax.
        assertEquals("\"col\"", sqlDialect.escape("col"));
        assertEquals("FETCH FIRST 5 ROWS ONLY", sqlDialect.limit(5));
    }
}
