package st.orm.spi.mssqlserver;

import javax.sql.DataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import st.orm.tck.AbstractPolymorphicConformanceTest;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@StormTest(scripts = "/data.sql")
public class MSSQLServerPolymorphicConformanceTest extends AbstractPolymorphicConformanceTest {
    private static MSSQLServerContainer<?> container;

    public static synchronized DataSource dataSource() {
        if (container == null) {
            container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-latest")
                .acceptLicense();
            container.start();
        }
        return ContainerDataSource.of(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
