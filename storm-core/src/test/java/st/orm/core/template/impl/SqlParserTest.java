package st.orm.core.template.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.core.spi.Providers.getSqlDialect;
import static st.orm.core.template.impl.SqlParser.clearQuotedIdentifiers;
import static st.orm.core.template.impl.SqlParser.clearStringLiterals;

public class SqlParserTest {

    @Test
    public void testQuotedIdentifiers() {
        assertEquals("SELECT * FROM \"\" WHERE column = 'value'", clearQuotedIdentifiers("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\" WHERE column = '''value'''", clearQuotedIdentifiers("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }

    @Test
    public void testStringLiterals() {
        assertEquals("SELECT * FROM \"table\" WHERE column = ''", clearStringLiterals("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\"\"table\"\"\" WHERE column = ''", clearStringLiterals("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }
}
