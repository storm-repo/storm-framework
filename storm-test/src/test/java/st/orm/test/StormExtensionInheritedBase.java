package st.orm.test;

import javax.sql.DataSource;

/**
 * Abstract base for {@link StormExtensionInheritedTest}: carries the {@link StormTest} annotation and the
 * {@code dataSource()} factory that the concrete subclass must pick up.
 */
@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"})
abstract class StormExtensionInheritedBase {

    static final String INHERITED_DB_NAME = "inherited_base_test";

    static DataSource dataSource() {
        return new SimpleTestDataSource("jdbc:h2:mem:" + INHERITED_DB_NAME + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
