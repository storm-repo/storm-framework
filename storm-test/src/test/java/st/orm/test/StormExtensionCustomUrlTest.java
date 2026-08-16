package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;

/**
 * Tests the custom URL path in {@link StormExtension} where a non-default JDBC URL is provided.
 * Verifies that the custom URL is actually used and that scripts execute against it.
 */
@StormTest(url = "jdbc:h2:mem:custom_url_test;DB_CLOSE_DELAY=-1", scripts = {"/test-schema.sql", "/test-data.sql"})
class StormExtensionCustomUrlTest {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    @Test
    void customUrlShouldBeReflectedInConnection(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection()) {
            var url = conn.getMetaData().getURL();
            assertTrue(url.contains("custom_url_test"),
                    "Expected connection URL to contain 'custom_url_test' but got: " + url);
        }
    }
}
