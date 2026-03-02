package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SimpleDataSource inner class of {@link StormExtension},
 * verifying that it provides working connections and correctly implements
 * the DataSource interface contract, including error paths.
 */
@StormTest
class SimpleDataSourceTest {

    @Test
    void connectionShouldBeUsableForSqlExecution(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1 + 1")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void connectionWithCredentialsShouldBeUsableForSqlExecution(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection("sa", "");
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1 + 1")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void multipleConnectionsShouldShareSameDatabase(DataSource dataSource) throws Exception {
        // Create a table via one connection, verify via another.
        try (var conn1 = dataSource.getConnection()) {
            conn1.createStatement().execute("CREATE TABLE IF NOT EXISTS ds_test (id INT)");
            conn1.createStatement().execute("INSERT INTO ds_test VALUES (42)");
        }
        try (var conn2 = dataSource.getConnection();
             var stmt = conn2.createStatement();
             var rs = stmt.executeQuery("SELECT id FROM ds_test")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt(1));
        }
        // Cleanup.
        try (var conn = dataSource.getConnection()) {
            conn.createStatement().execute("DROP TABLE ds_test");
        }
    }

    @Test
    void getParentLoggerShouldThrowBecauseJulNotSupported(DataSource dataSource) {
        // SimpleDataSource does not support java.util.logging.
        assertThrows(SQLFeatureNotSupportedException.class, dataSource::getParentLogger);
    }

    @Test
    void unwrapShouldThrowBecauseNotAWrapper(DataSource dataSource) {
        assertThrows(SQLException.class, () -> dataSource.unwrap(DataSource.class));
    }

    @Test
    void isWrapperForShouldReturnFalse(DataSource dataSource) throws Exception {
        assertFalse(dataSource.isWrapperFor(DataSource.class));
    }
}
