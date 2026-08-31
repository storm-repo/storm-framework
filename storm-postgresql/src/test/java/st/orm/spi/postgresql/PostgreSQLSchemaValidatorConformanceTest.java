package st.orm.spi.postgresql;

import javax.sql.DataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import st.orm.tck.AbstractSchemaValidatorConformanceTest;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@StormTest(scripts = "/data.sql")
public class PostgreSQLSchemaValidatorConformanceTest extends AbstractSchemaValidatorConformanceTest {
    private static PostgreSQLContainer<?> container;

    public static synchronized DataSource dataSource() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:17");
            container.start();
        }
        return ContainerDataSource.of(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
