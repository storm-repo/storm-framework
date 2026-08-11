package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Verifies the lifecycle of the extension-created default H2 database: every execution of a test class gets a
 * database of its own, so equally named test classes in different packages never share one, and the database is shut
 * down once the test class completes.
 */
class StormExtensionDatabaseLifecycleTest {

    static final List<String> capturedUrls = new ArrayList<>();

    @Test
    void eachClassExecutionShouldGetItsOwnDatabase() {
        capturedUrls.clear();
        runCapturingCase();
        runCapturingCase();
        assertEquals(2, capturedUrls.size());
        assertTrue(capturedUrls.getFirst().contains("CapturingCase"),
                "Expected the database name to start with the test class name but got: " + capturedUrls.getFirst());
        assertNotEquals(capturedUrls.get(0), capturedUrls.get(1),
                "Two executions of an equally named test class must not share a database.");
    }

    @Test
    void defaultDatabaseShouldBeShutDownAfterClassCompletes() {
        capturedUrls.clear();
        runCapturingCase();
        String url = capturedUrls.getFirst();
        String databaseName = url.substring("jdbc:h2:mem:".length()).split(";")[0];
        assertThrows(SQLException.class,
                () -> DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";IFEXISTS=TRUE", "sa", ""),
                "Expected the database to be shut down after the test class completed.");
    }

    private static void runCapturingCase() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CapturingCase.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(1));
    }

    @StormTest
    static class CapturingCase {

        @Test
        void captureUrl(DataSource dataSource) throws Exception {
            try (var conn = dataSource.getConnection()) {
                capturedUrls.add(conn.getMetaData().getURL());
            }
        }
    }
}
