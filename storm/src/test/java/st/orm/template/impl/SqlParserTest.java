package st.orm.template.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlParserTest {

    @Test
    public void testQuotedIdentifiers() {
        assertEquals("SELECT * FROM \"\" WHERE column = 'value'", SqlParser.clearQuotedIdentifiers("SELECT * FROM \"table\" WHERE column = 'value'"));
        assertEquals("SELECT * FROM \"\" WHERE column = '''value'''", SqlParser.clearQuotedIdentifiers("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''"));
    }

    @Test
    public void testStringLiterals() {
        assertEquals("SELECT * FROM \"table\" WHERE column = ''", SqlParser.clearStringLiterals("SELECT * FROM \"table\" WHERE column = 'value'"));
        assertEquals("SELECT * FROM \"\"\"table\"\"\" WHERE column = ''", SqlParser.clearStringLiterals("SELECT * FROM \"\"\"table\"\"\" WHERE column = '''value'''"));
    }
}
