package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;

/**
 * Tests that {@link StormExtension} uses a static {@code dataSource()} factory method on the test class when present,
 * instead of creating a default H2 DataSource from the {@link StormTest} annotation attributes.
 */
@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"})
class StormExtensionDataSourceFactoryTest {

    private static final String CUSTOM_DB_NAME = "custom_datasource_factory_test";

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    static DataSource dataSource() {
        String url = "jdbc:h2:mem:" + CUSTOM_DB_NAME + ";DB_CLOSE_DELAY=-1";
        return new SimpleTestDataSource(url, "sa", "");
    }

    @Test
    void shouldUseDataSourceFromFactoryMethod(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection()) {
            var url = conn.getMetaData().getURL();
            assertTrue(url.contains(CUSTOM_DB_NAME),
                    "Expected connection URL to contain '" + CUSTOM_DB_NAME + "' but got: " + url);
        }
    }

}
