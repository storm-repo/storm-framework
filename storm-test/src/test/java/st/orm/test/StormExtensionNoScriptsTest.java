package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.core.template.ORMTemplate;

/**
 * Tests the path where {@link StormTest} is used with no scripts (default empty array).
 * This exercises the beforeAll path where scripts.length == 0 and no SQL execution occurs,
 * verifying that the extension still provides a functional DataSource and ORMTemplate.
 */
@StormTest
class StormExtensionNoScriptsTest {

    @Test
    void shouldProvideWorkingDataSourceWithNoScripts(DataSource dataSource) throws SQLException {
        // Verify the DataSource is functional by executing a simple query.
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
        }
    }

    @Test
    void shouldProvideWorkingOrmTemplateWithNoScripts(ORMTemplate orm) {
        // Verify ORMTemplate can execute queries on the empty database.
        var result = orm.query("SELECT 42 AS result").getSingleResult(int.class);
        assertEquals(42, result);
    }
}
