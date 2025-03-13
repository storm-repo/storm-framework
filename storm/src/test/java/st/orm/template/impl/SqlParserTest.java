package st.orm.template.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.spi.Providers.getSqlDialect;

public class SqlParserTest {

    @Test
    public void testQuotedIdentifiers() {
        assertEquals("SELECT * FROM \"\" WHERE column = 'value'", SqlParser.clearQuotedIdentifiers("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\" WHERE column = '''value'''", SqlParser.clearQuotedIdentifiers("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }

    @Test
    public void testStringLiterals() {
        assertEquals("SELECT * FROM \"table\" WHERE column = ''", SqlParser.clearStringLiterals("SELECT * FROM \"table\" WHERE column = 'value'", getSqlDialect()));
        assertEquals("SELECT * FROM \"\"\"table\"\"\" WHERE column = ''", SqlParser.clearStringLiterals("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''", getSqlDialect()));
    }
}
