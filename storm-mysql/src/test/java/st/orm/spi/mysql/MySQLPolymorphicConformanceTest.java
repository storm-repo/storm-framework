package st.orm.spi.mysql;

import javax.sql.DataSource;
import org.testcontainers.containers.MySQLContainer;
import st.orm.tck.AbstractPolymorphicConformanceTest;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@StormTest(scripts = "/data.sql")
public class MySQLPolymorphicConformanceTest extends AbstractPolymorphicConformanceTest {
    private static MySQLContainer<?> container;

    public static synchronized DataSource dataSource() {
        if (container == null) {
            container = new MySQLContainer<>("mysql:9.2");
            container.start();
        }
        return ContainerDataSource.of(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
