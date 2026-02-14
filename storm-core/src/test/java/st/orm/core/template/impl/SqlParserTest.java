package st.orm.core.template.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.core.spi.Providers.getSqlDialect;
import static st.orm.core.template.impl.SqlParser.clearQuotedIdentifiers;
import static st.orm.core.template.impl.SqlParser.clearStringLiterals;

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
}
