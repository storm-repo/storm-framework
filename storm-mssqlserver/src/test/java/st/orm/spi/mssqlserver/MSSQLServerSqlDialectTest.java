package st.orm.spi.mssqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import st.orm.StormConfig;

/**
 * Unit tests for {@link MSSQLServerSqlDialect} verifying MSSQL-specific SQL generation behavior.
 */
class MSSQLServerSqlDialectTest {

    private final MSSQLServerSqlDialect dialect = new MSSQLServerSqlDialect();

    // -- Identifier validation: MSSQL allows _, #, and letters as first char --

    @Test
    void identifierPatternShouldAcceptHashPrefixedTempTables() {
        // MSSQL temp tables start with #, which is not valid in standard SQL.
        var pattern = dialect.getValidIdentifierPattern();
        assertTrue(pattern.matcher("#tempTable").matches());
        assertTrue(pattern.matcher("#globalTemp").matches());
    }

    @Test
    void identifierPatternShouldAcceptUnderscorePrefixedNames() {
        assertTrue(dialect.getValidIdentifierPattern().matcher("_internal").matches());
    }

    @Test
    void identifierPatternShouldRejectNumericLeadingCharacters() {
        assertFalse(dialect.getValidIdentifierPattern().matcher("123table").matches());
    }

    @Test
    void identifierPatternShouldRejectEmptyString() {
        assertFalse(dialect.getValidIdentifierPattern().matcher("").matches());
    }

    // -- Escape: MSSQL uses square bracket escaping [name] --

    @Test
    void escapeShouldWrapSimpleNameInSquareBrackets() {
        assertEquals("[myColumn]", dialect.escape("myColumn"));
    }

    @Test
    void escapeShouldDoubleClosingBracketsInsideName() {
        // A single ] in the name becomes ]], wrapped in [...]
        assertEquals("[my]]Column]", dialect.escape("my]Column"));
    }

    @Test
    void escapeShouldHandleMultipleClosingBrackets() {
        // Two consecutive ] each get doubled: ]] -> ]]]]
        assertEquals("[a]]]]b]", dialect.escape("a]]b"));
    }

    @Test
    void escapeShouldHandleNameThatIsEntirelyClosingBracket() {
        // Input "]]" → each ] doubled → "]]]]" → wrapped → "[]]]]]"
        assertEquals("[]]]]]", dialect.escape("]]"));
    }

    // -- getSafeIdentifier: integration of isKeyword + escape --

    @Test
    void getSafeIdentifierShouldEscapeMSSQLKeywords() {
        // MERGE is an MSSQL-specific keyword; should be escaped with brackets.
        assertEquals("[MERGE]", dialect.getSafeIdentifier("MERGE"));
        assertEquals("[TOP]", dialect.getSafeIdentifier("TOP"));
        assertEquals("[PIVOT]", dialect.getSafeIdentifier("PIVOT"));
    }

    @Test
    void getSafeIdentifierShouldNotEscapeNormalIdentifiers() {
        assertEquals("myTable", dialect.getSafeIdentifier("myTable"));
        assertEquals("col1", dialect.getSafeIdentifier("col1"));
    }

    @Test
    void getSafeIdentifierShouldEscapeIdentifiersWithInvalidCharacters() {
        // Identifiers with spaces or special chars should be escaped.
        assertEquals("[my table]", dialect.getSafeIdentifier("my table"));
        assertEquals("[my-column]", dialect.getSafeIdentifier("my-column"));
    }

    @Test
    void isKeywordShouldBeCaseInsensitive() {
        assertTrue(dialect.isKeyword("merge"));
        assertTrue(dialect.isKeyword("MERGE"));
        assertTrue(dialect.isKeyword("Merge"));
    }

    @Test
    void isKeywordShouldRejectNonKeywords() {
        assertFalse(dialect.isKeyword("myColumn"));
        assertFalse(dialect.isKeyword("customer_name"));
    }

    // -- Identifier pattern: recognizes [bracket] and "double-quoted" identifiers --

    @Test
    void identifierPatternShouldExtractSquareBracketIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT [my col] FROM t");
        assertTrue(matcher.find());
        assertEquals("[my col]", matcher.group());
    }

    @Test
    void identifierPatternShouldExtractDoubleQuotedIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT \"my col\" FROM t");
        assertTrue(matcher.find());
        assertEquals("\"my col\"", matcher.group());
    }

    @Test
    void identifierPatternShouldNotMatchUnquotedIdentifiers() {
        var matcher = dialect.getIdentifierPattern().matcher("SELECT myCol FROM t");
        assertFalse(matcher.find());
    }

    // -- Quote literal pattern --

    @Test
    void quoteLiteralPatternShouldMatchStringWithEscapedQuotes() {
        var matcher = dialect.getQuoteLiteralPattern().matcher("'it''s a test'");
        assertTrue(matcher.find());
        assertEquals("'it''s a test'", matcher.group());
    }

    // -- MSSQL-specific limit/offset: TOP clause and OFFSET-FETCH --

    @Test
    void limitShouldGenerateTopClause() {
        // MSSQL uses TOP N instead of LIMIT N.
        assertEquals("TOP 10", dialect.limit(10));
        assertEquals("TOP 1", dialect.limit(1));
        assertEquals("TOP 0", dialect.limit(0));
    }

    @Test
    void offsetShouldGenerateOffsetRows() {
        assertEquals("OFFSET 5 ROWS", dialect.offset(5));
        assertEquals("OFFSET 0 ROWS", dialect.offset(0));
    }

    @Test
    void limitWithOffsetShouldGenerateOffsetFetchNext() {
        // MSSQL 2012+ uses OFFSET-FETCH, not LIMIT-OFFSET.
        assertEquals("OFFSET 10 ROWS FETCH NEXT 20 ROWS ONLY", dialect.limit(10, 20));
        assertEquals("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY", dialect.limit(0, 1));
    }

    // -- MSSQL-specific lock hints: WITH (HOLDLOCK), WITH (UPDLOCK) --

    @Test
    void lockHintsShouldUseWithSyntax() {
        // MSSQL uses WITH (...) hints instead of FOR UPDATE / FOR SHARE.
        assertEquals("WITH (HOLDLOCK)", dialect.forShareLockHint());
        assertEquals("WITH (UPDLOCK)", dialect.forUpdateLockHint());
    }

    // -- Sequence SQL generation --

    @Test
    void sequenceNextValShouldGenerateCorrectSqlForSimpleName() {
        assertEquals("NEXT VALUE FOR my_seq", dialect.sequenceNextVal("my_seq"));
    }

    @Test
    void sequenceNextValShouldEscapeKeywordSequenceName() {
        // If the sequence name is a keyword, getSafeIdentifier should escape it.
        var result = dialect.sequenceNextVal("SELECT");
        assertEquals("NEXT VALUE FOR [SELECT]", result);
    }

    // -- Provider filter: accept/reject logic --

    @Test
    void providerFilterShouldAcceptNonSqlDialectProviders() {
        assertTrue(MSSQLServerProviderFilter.INSTANCE.test(new st.orm.core.spi.Provider() {}));
    }

    @Test
    void providerFilterShouldRejectForeignSqlDialectProviders() {
        assertFalse(MSSQLServerProviderFilter.INSTANCE.test(new st.orm.core.spi.SqlDialectProvider() {
            @Override public st.orm.core.template.SqlDialect getSqlDialect(StormConfig config) { return null; }
        }));
    }

    @Test
    void providerFilterShouldAcceptMSSQLServerEntityRepositoryProvider() {
        assertTrue(MSSQLServerProviderFilter.INSTANCE.test(new MSSQLServerEntityRepositoryProviderImpl()));
    }

    // -- SqlDialectProvider --

    @Test
    void sqlDialectProviderShouldReturnDialectWithMSSQLSpecificBehavior() {
        var provider = new MSSQLServerSqlDialectProviderImpl();
        var sqlDialect = provider.getSqlDialect(StormConfig.of(Map.of()));
        // Verify MSSQL-specific: bracket escaping and TOP syntax.
        assertEquals("[col]", sqlDialect.escape("col"));
        assertEquals("TOP 5", sqlDialect.limit(5));
    }
}
